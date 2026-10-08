package io.github.matheusanbs.wirelessautomate.compat.mekanism;

import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.storage.ChemicalStorage;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * O Tanque Químico para o Mekanism: a capability {@code mekanism:chemical_handler} do bloco e as
 * trocas com recipientes (tanques e cilindros) pela tela. Só é carregada com o Mekanism presente;
 * o resto do mod passa por {@code network.Chemicals}.
 */
public final class MekanismStorage {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(MekanismChemicals.BLOCK, ModBlockEntities.CHEMICAL_TANK.get(),
                (tank, side) -> new Handler(tank.storage()));
    }

    private static @Nullable Chemical chemical(ResourceLocation id) {
        return MekanismChemicals.exists(id) ? MekanismAPI.CHEMICAL_REGISTRY.get(id) : null;
    }

    private static ChemicalStack stack(ResourceLocation id, long amount) {
        Chemical chemical = chemical(id);
        return chemical == null || amount <= 0 ? ChemicalStack.EMPTY : new ChemicalStack(chemical, amount);
    }

    /**
     * Enche o recipiente (pilha de 1 no cursor) com o químico {@code id} do Tanque. Devolve quanto
     * passou; o recipiente é alterado no lugar.
     */
    public static long fillContainer(ItemStack container, ChemicalStorage storage, ResourceLocation id) {
        IChemicalHandler item = container.getCapability(MekanismChemicals.ITEM);
        if (item == null) {
            return 0;
        }
        long available = storage.count(id);
        ChemicalStack offered = stack(id, available);
        if (offered.isEmpty()) {
            return 0;
        }
        long accepts = available - item.insertChemical(offered, Action.SIMULATE).getAmount();
        if (accepts <= 0) {
            return 0;
        }
        long taken = storage.extract(id, accepts, false);
        ChemicalStack rest = item.insertChemical(stack(id, taken), Action.EXECUTE);
        if (!rest.isEmpty()) {
            storage.insert(id, rest.getAmount(), false);
        }
        return taken - rest.getAmount();
    }

    /** Esvazia o recipiente no Tanque (o que o filtro e a capacidade deixarem). Devolve quanto passou. */
    public static long emptyContainer(ItemStack container, ChemicalStorage storage) {
        IChemicalHandler item = container.getCapability(MekanismChemicals.ITEM);
        if (item == null) {
            return 0;
        }
        long moved = 0;
        for (int tank = 0, n = item.getChemicalTanks(); tank < n; tank++) {
            ChemicalStack inTank = item.getChemicalInTank(tank);
            if (inTank.isEmpty()) {
                continue;
            }
            ResourceLocation id = MekanismChemicals.id(inTank);
            long accepts = storage.insert(id, inTank.getAmount(), true);
            if (accepts <= 0) {
                continue;
            }
            ChemicalStack taken = item.extractChemical(tank, accepts, Action.EXECUTE);
            long in = storage.insert(id, taken.getAmount(), false);
            if (in < taken.getAmount()) {
                item.insertChemical(tank, taken.copyWithAmount(taken.getAmount() - in), Action.EXECUTE);
            }
            moved += in;
        }
        return moved;
    }

    /**
     * O Tanque Químico como {@link IChemicalHandler}: um tanque por químico guardado, mais um vazio no
     * fim que aceita qualquer químico. A API do Mekanism é em {@code long}: um tipo inteiro passa
     * numa chamada.
     */
    static final class Handler implements IChemicalHandler {
        private final ChemicalStorage storage;

        Handler(ChemicalStorage storage) {
            this.storage = storage;
        }

        @Override
        public int getChemicalTanks() {
            return storage.types() + 1;
        }

        @Override
        public ChemicalStack getChemicalInTank(int tank) {
            return tank < 0 || tank >= storage.types() ? ChemicalStack.EMPTY
                    : stack(storage.key(tank), storage.count(tank));
        }

        /** Só o que dá para fazer sem criar nem destruir químico: nada (o Mekanism usa em tanques próprios). */
        @Override
        public void setChemicalInTank(int tank, ChemicalStack stack) {
        }

        @Override
        public long getChemicalTankCapacity(int tank) {
            long capacity = storage.capacity();
            return capacity <= 0 ? Long.MAX_VALUE : capacity;
        }

        @Override
        public boolean isValid(int tank, ChemicalStack stack) {
            if (tank < 0 || tank > storage.types()) {
                return false;
            }
            return tank == storage.types() || storage.key(tank).equals(MekanismChemicals.id(stack));
        }

        @Override
        public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
            if (stack.isEmpty() || !isValid(tank, stack)) {
                return stack;
            }
            return insertChemical(stack, action);
        }

        @Override
        public ChemicalStack insertChemical(ChemicalStack stack, Action action) {
            if (stack.isEmpty()) {
                return stack;
            }
            long in = storage.insert(MekanismChemicals.id(stack), stack.getAmount(), action.simulate());
            return in >= stack.getAmount() ? ChemicalStack.EMPTY : stack.copyWithAmount(stack.getAmount() - in);
        }

        @Override
        public ChemicalStack extractChemical(int tank, long amount, Action action) {
            if (tank < 0 || tank >= storage.types() || amount <= 0) {
                return ChemicalStack.EMPTY;
            }
            ResourceLocation id = storage.key(tank);
            return stack(id, storage.extract(id, amount, action.simulate()));
        }

        @Override
        public ChemicalStack extractChemical(long amount, Action action) {
            return storage.isEmpty() ? ChemicalStack.EMPTY : extractChemical(0, amount, action);
        }

        @Override
        public ChemicalStack extractChemical(ChemicalStack stack, Action action) {
            if (stack.isEmpty()) {
                return ChemicalStack.EMPTY;
            }
            ResourceLocation id = MekanismChemicals.id(stack);
            return stack(id, storage.extract(id, stack.getAmount(), action.simulate()));
        }
    }

    private MekanismStorage() {
    }
}
