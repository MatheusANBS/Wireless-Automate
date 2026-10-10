package io.github.matheusanbs.wirelessautomate.compat.mekanism;

import io.github.matheusanbs.wirelessautomate.compat.mekanism.MekanismChemicals.Subtype;
import io.github.matheusanbs.wirelessautomate.storage.ChemicalStorage;
import java.util.Arrays;
import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

/**
 * O Tanque Químico para o Mekanism: as capabilities de químico do bloco e as trocas com recipientes
 * (tanques e cilindros) pela tela. Só é carregada com o Mekanism presente; o resto do mod passa por
 * {@code network.Chemicals}.
 *
 * <p>Porte 1.20.1 (Mekanism 10.4, D4): o Tanque guarda químicos dos quatro tipos (a chave é o id) e expõe as
 * quatro capabilities ({@link #handler}); cada uma vê só os químicos do seu tipo. Um Tanque com hidrogênio e
 * uma slurry mostra um tanque (mais o vazio) no {@link IGasHandler} e outro no {@link ISlurryHandler}.
 */
public final class MekanismStorage {
    /**
     * O handler do Tanque para a capability, ou {@code null} se ela não é de químico. O block entity guarda o
     * resultado num {@code LazyOptional} ({@code StorageBlockEntity.getCapability}).
     */
    public static @Nullable Object handler(Capability<?> capability, ChemicalStorage storage) {
        if (capability == MekanismChemicals.GAS) {
            return new GasView(storage);
        }
        if (capability == MekanismChemicals.INFUSION) {
            return new InfusionView(storage);
        }
        if (capability == MekanismChemicals.PIGMENT) {
            return new PigmentView(storage);
        }
        if (capability == MekanismChemicals.SLURRY) {
            return new SlurryView(storage);
        }
        return null;
    }

    /**
     * Enche o recipiente (pilha de 1 no cursor) com o químico {@code id} do Tanque, pela capability do tipo
     * dele. Devolve quanto passou; o recipiente é alterado no lugar.
     */
    public static long fillContainer(ItemStack container, ChemicalStorage storage, ResourceLocation id) {
        Subtype<?, ?> type = MekanismChemicals.typeOf(id);
        return type == null ? 0 : fill(type, container, storage, id);
    }

    private static <C extends Chemical<C>, S extends ChemicalStack<C>> long fill(Subtype<C, S> type,
            ItemStack container, ChemicalStorage storage, ResourceLocation id) {
        IChemicalHandler<C, S> item = type.item(container);
        if (item == null) {
            return 0;
        }
        long available = storage.count(id);
        S offered = type.stack(id, available);
        if (offered.isEmpty()) {
            return 0;
        }
        long accepts = available - item.insertChemical(offered, Action.SIMULATE).getAmount();
        if (accepts <= 0) {
            return 0;
        }
        long taken = storage.extract(id, accepts, false);
        S rest = item.insertChemical(type.stack(id, taken), Action.EXECUTE);
        if (!rest.isEmpty()) {
            storage.insert(id, rest.getAmount(), false);
        }
        return taken - rest.getAmount();
    }

    /** Esvazia o recipiente no Tanque (o que o filtro e a capacidade deixarem), pelos quatro tipos. Devolve quanto passou. */
    public static long emptyContainer(ItemStack container, ChemicalStorage storage) {
        long moved = 0;
        for (Subtype<?, ?> type : MekanismChemicals.SUBTYPES) {
            moved += empty(type, container, storage);
        }
        return moved;
    }

    private static <C extends Chemical<C>, S extends ChemicalStack<C>> long empty(Subtype<C, S> type,
            ItemStack container, ChemicalStorage storage) {
        IChemicalHandler<C, S> item = type.item(container);
        if (item == null) {
            return 0;
        }
        long moved = 0;
        for (int tank = 0, n = item.getTanks(); tank < n; tank++) {
            S inTank = item.getChemicalInTank(tank);
            if (inTank.isEmpty()) {
                continue;
            }
            ResourceLocation id = MekanismChemicals.id(inTank);
            if (MekanismChemicals.subtype(id) != type.index) {
                // Id repetido entre registros (D4): o Tanque o guardaria como o outro tipo.
                continue;
            }
            long accepts = storage.insert(id, inTank.getAmount(), true);
            if (accepts <= 0) {
                continue;
            }
            S taken = item.extractChemical(tank, accepts, Action.EXECUTE);
            long in = storage.insert(id, taken.getAmount(), false);
            if (in < taken.getAmount()) {
                item.insertChemical(tank, type.withAmount(taken, taken.getAmount() - in), Action.EXECUTE);
            }
            moved += in;
        }
        return moved;
    }

    /**
     * O Tanque Químico como {@link IChemicalHandler} de um tipo: um tanque por químico desse tipo guardado,
     * mais um vazio no fim que aceita qualquer químico do tipo. A API do Mekanism é em {@code long}: um tipo
     * inteiro passa numa chamada. Os índices dos químicos do tipo são refeitos só quando o conteúdo muda
     * ({@link ChemicalStorage#version()}).
     */
    abstract static class View<C extends Chemical<C>, S extends ChemicalStack<C>> implements IChemicalHandler<C, S> {
        private final ChemicalStorage storage;
        private final Subtype<C, S> type;
        /** Posições em {@link #storage} dos químicos deste tipo, as {@link #count} primeiras. */
        private int[] slots = new int[4];
        private int count;
        private int version = -1;

        View(ChemicalStorage storage, Subtype<C, S> type) {
            this.storage = storage;
            this.type = type;
        }

        private void refresh() {
            int now = storage.version();
            if (now == version) {
                return;
            }
            version = now;
            count = 0;
            for (int i = 0, n = storage.types(); i < n; i++) {
                if (MekanismChemicals.subtype(storage.key(i)) == type.index) {
                    if (count == slots.length) {
                        slots = Arrays.copyOf(slots, count * 2);
                    }
                    slots[count++] = i;
                }
            }
        }

        /** Posição no Tanque do tanque {@code tank} deste tipo, ou -1 (fora, ou o vazio do fim). */
        private int slot(int tank) {
            refresh();
            return tank < 0 || tank >= count ? -1 : slots[tank];
        }

        private boolean ofType(ResourceLocation id) {
            return MekanismChemicals.subtype(id) == type.index;
        }

        @Override
        public S getEmptyStack() {
            return type.empty();
        }

        @Override
        public int getTanks() {
            refresh();
            return count + 1;
        }

        @Override
        public S getChemicalInTank(int tank) {
            int slot = slot(tank);
            return slot < 0 ? type.empty() : type.stack(storage.key(slot), storage.count(slot));
        }

        /** Só o que dá para fazer sem criar nem destruir químico: nada (o Mekanism usa em tanques próprios). */
        @Override
        public void setChemicalInTank(int tank, S stack) {
        }

        @Override
        public long getTankCapacity(int tank) {
            long capacity = storage.capacity();
            return capacity <= 0 ? Long.MAX_VALUE : capacity;
        }

        @Override
        public boolean isValid(int tank, S stack) {
            refresh();
            if (tank < 0 || tank > count) {
                return false;
            }
            ResourceLocation id = MekanismChemicals.id(stack);
            return tank == count ? ofType(id) : storage.key(slots[tank]).equals(id);
        }

        @Override
        public S insertChemical(int tank, S stack, Action action) {
            if (stack.isEmpty() || !isValid(tank, stack)) {
                return stack;
            }
            return insertChemical(stack, action);
        }

        @Override
        public S insertChemical(S stack, Action action) {
            if (stack.isEmpty()) {
                return stack;
            }
            ResourceLocation id = MekanismChemicals.id(stack);
            if (!ofType(id)) {
                // Id repetido entre registros (D4): guardado, ele voltaria como o outro tipo.
                return stack;
            }
            long in = storage.insert(id, stack.getAmount(), action.simulate());
            return in >= stack.getAmount() ? type.empty() : type.withAmount(stack, stack.getAmount() - in);
        }

        @Override
        public S extractChemical(int tank, long amount, Action action) {
            int slot = slot(tank);
            if (slot < 0 || amount <= 0) {
                return type.empty();
            }
            ResourceLocation id = storage.key(slot);
            return type.stack(id, storage.extract(id, amount, action.simulate()));
        }

        @Override
        public S extractChemical(long amount, Action action) {
            return extractChemical(0, amount, action);
        }

        @Override
        public S extractChemical(S stack, Action action) {
            if (stack.isEmpty()) {
                return type.empty();
            }
            ResourceLocation id = MekanismChemicals.id(stack);
            if (!ofType(id)) {
                return type.empty();
            }
            return type.stack(id, storage.extract(id, stack.getAmount(), action.simulate()));
        }
    }

    static final class GasView extends View<Gas, GasStack> implements IGasHandler {
        GasView(ChemicalStorage storage) {
            super(storage, MekanismChemicals.GASES);
        }

        @Override
        public GasStack getEmptyStack() {
            return GasStack.EMPTY;
        }
    }

    static final class InfusionView extends View<InfuseType, InfusionStack> implements IInfusionHandler {
        InfusionView(ChemicalStorage storage) {
            super(storage, MekanismChemicals.INFUSIONS);
        }

        @Override
        public InfusionStack getEmptyStack() {
            return InfusionStack.EMPTY;
        }
    }

    static final class PigmentView extends View<Pigment, PigmentStack> implements IPigmentHandler {
        PigmentView(ChemicalStorage storage) {
            super(storage, MekanismChemicals.PIGMENTS);
        }

        @Override
        public PigmentStack getEmptyStack() {
            return PigmentStack.EMPTY;
        }
    }

    static final class SlurryView extends View<Slurry, SlurryStack> implements ISlurryHandler {
        SlurryView(ChemicalStorage storage) {
            super(storage, MekanismChemicals.SLURRIES);
        }

        @Override
        public SlurryStack getEmptyStack() {
            return SlurryStack.EMPTY;
        }
    }

    private MekanismStorage() {
    }
}
