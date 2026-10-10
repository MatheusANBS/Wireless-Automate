package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.compat.mekanism.MekanismChemicals;
import io.github.matheusanbs.wirelessautomate.compat.mekanism.MekanismChemicals.Subtype;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.ChemicalTankBuilder;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

/**
 * A parte dos testes de químico que usa a API do Mekanism. Não é {@code @GameTestHolder} nem
 * {@code @EventBusSubscriber}: o Forge inspeciona essas classes por reflexão (as assinaturas dos
 * métodos), e um tipo do Mekanism numa assinatura impede o mod de carregar sem ele. Só é chamada
 * por {@link ChemicalTestTanks} e {@link ChemicalGameTests} com o Mekanism presente.
 *
 * <p>Porte 1.20.1 (D4): o Mekanism 10.4 tem quatro tipos de químico, cada um com a sua capability. No {@code main}
 * o tanque de teste era um tanque só, de qualquer químico; aqui cada posição tem um tanque por tipo (gás, infusão,
 * pigmento, slurry), cada um exposto pela capability do seu tipo. Os métodos recebem o id do químico e acham o tipo
 * pelo {@link MekanismChemicals#typeOf}.
 */
final class ChemicalTestSupport {
    private static final Map<BlockPos, List<Tanks<?, ?>>> TANKS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, List<Tanks<?, ?>>> MANY = new ConcurrentHashMap<>();
    /**
     * Subtipos que o tanque de teste da posição expõe (máscara de bits pelo índice de {@link MekanismChemicals#SUBTYPES});
     * sem entrada, os quatro. Os tanques dos tipos escondidos continuam lá (lidos por {@link #stored}).
     */
    private static final Map<BlockPos, Integer> EXPOSED = new ConcurrentHashMap<>();

    /** O handler do tanque de teste da posição para a capability, ou {@code null} se não é de químico (ou está escondido). */
    static @Nullable Object handler(Capability<?> capability, BlockPos pos) {
        int subtype = MekanismChemicals.CAPABILITIES.indexOf(capability);
        if (subtype < 0) {
            return null;
        }
        Integer mask = EXPOSED.get(pos);
        return mask != null && (mask & (1 << subtype)) == 0 ? null : tanks(pos).get(subtype);
    }

    /** O tanque de teste de {@code pos} (absoluta) passa a expor só os subtipos dados (índices de {@code SUBTYPES}). */
    static void exposeOnly(BlockPos pos, int... subtypes) {
        int mask = 0;
        for (int subtype : subtypes) {
            mask |= 1 << subtype;
        }
        EXPOSED.put(pos.immutable(), mask);
    }

    /**
     * O tanque de teste de {@code pos} passa a expor também {@code subtype}, sem trocar de block entity, e avisa os
     * vizinhos como uma máquina do Mekanism que muda o lado ({@code TileComponentConfig.sideChanged} chama o
     * {@code WorldUtils.notifyNeighborOfChange}): o lado não oferecia nada, então não há {@code LazyOptional} velho a
     * invalidar, só o aviso ({@code neighborChanged} no roteador).
     */
    static void exposeAlso(ServerLevel level, BlockPos pos, int subtype) {
        EXPOSED.computeIfPresent(pos.immutable(), (key, mask) -> mask | (1 << subtype));
        level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
    }

    /** Quanto do químico há nos tanques internos do tanque de teste, exposto ou não. */
    static long stored(BlockPos pos, ResourceLocation chemical) {
        Subtype<?, ?> type = type(chemical);
        long total = 0;
        for (IChemicalTank<?, ?> tank : tanks(pos).get(type.index).tanks) {
            ChemicalStack<?> stack = tank.getStack();
            if (!stack.isEmpty() && MekanismChemicals.id(stack).equals(chemical)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** O handler do bloco de muitos tanques (só de saída) da posição, ou {@code null}. */
    static @Nullable Object manyHandler(Capability<?> capability, BlockPos pos) {
        int subtype = MekanismChemicals.CAPABILITIES.indexOf(capability);
        return subtype < 0 ? null : manyTanks(pos).get(subtype);
    }

    static void reset(BlockPos pos) {
        TANKS.remove(pos.immutable());
        MANY.remove(pos.immutable());
        EXPOSED.remove(pos.immutable());
    }

    /** Põe {@code amount} mB do químico no tanque {@code tank} do bloco de muitos tanques; devolve a sobra. */
    static long fillTank(BlockPos pos, int tank, ResourceLocation chemical, long amount) {
        Subtype<?, ?> type = type(chemical);
        return fillTank(type, manyTanks(pos).get(type.index), tank, chemical, amount);
    }

    private static <C extends Chemical<C>, S extends ChemicalStack<C>> long fillTank(Subtype<C, S> type, Tanks<?, ?> tanks,
            int tank, ResourceLocation chemical, long amount) {
        @SuppressWarnings("unchecked")
        Tanks<C, S> typed = (Tanks<C, S>) tanks;
        return typed.tanks.get(tank).insert(type.stack(chemical, amount), Action.EXECUTE, AutomationType.INTERNAL).getAmount();
    }

    /** Quanto há no tanque {@code tank} do bloco de muitos tanques (somando os quatro tipos). */
    static long tankAmount(BlockPos pos, int tank) {
        long total = 0;
        for (Tanks<?, ?> tanks : manyTanks(pos)) {
            total += tanks.tanks.get(tank).getStored();
        }
        return total;
    }

    /** O bloco expõe alguma capability de químico pela face de cima. */
    static boolean hasHandler(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return false;
        }
        for (Capability<?> capability : MekanismChemicals.CAPABILITIES) {
            if (be.getCapability(capability, Direction.UP).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Os químicos que o bloco mostra pela capability do subtipo {@code subtype} (face de cima), tanque a tanque, com
     * {@code null} nos tanques vazios; {@code null} se o bloco não expõe essa capability.
     */
    static @Nullable List<ResourceLocation> tanksSeenBy(ServerLevel level, BlockPos pos, int subtype) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        Object raw = be.getCapability(MekanismChemicals.CAPABILITIES.get(subtype), Direction.UP).resolve().orElse(null);
        if (raw == null) {
            return null;
        }
        IChemicalHandler<?, ?> handler = (IChemicalHandler<?, ?>) raw;
        List<ResourceLocation> ids = new ArrayList<>();
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            ChemicalStack<?> stack = handler.getChemicalInTank(tank);
            ids.add(stack.isEmpty() ? null : MekanismChemicals.id(stack));
        }
        return ids;
    }

    /** O índice do subtipo do químico em {@code SUBTYPES} (-1 se ele não existe). */
    static int subtypeOf(ResourceLocation chemical) {
        return MekanismChemicals.subtype(chemical);
    }

    // ------------------------------------------------------------------ recipientes (itens)

    /** O item expõe a capability do subtipo (um tanque do Mekanism expõe as quatro). */
    static boolean itemExposes(ItemStack stack, int subtype) {
        return MekanismChemicals.SUBTYPES.get(subtype).item(stack) != null;
    }

    /** Quanto do químico há no item, pela capability do tipo dele. */
    static long itemAmount(ItemStack stack, ResourceLocation chemical) {
        IChemicalHandler<?, ?> handler = type(chemical).item(stack);
        if (handler == null) {
            return 0;
        }
        long total = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            ChemicalStack<?> inTank = handler.getChemicalInTank(tank);
            if (!inTank.isEmpty() && MekanismChemicals.id(inTank).equals(chemical)) {
                total += inTank.getAmount();
            }
        }
        return total;
    }

    // ------------------------------------------------------------------ máquinas do Mekanism

    /** Os getters dos tanques internos de uma máquina do Mekanism por subtipo ({@code IGasTracker} etc., fora da API). */
    private static final String[] TANK_GETTERS = {"getGasTanks", "getInfusionTanks", "getPigmentTanks", "getSlurryTanks"};

    /**
     * Muda a configuração de lados de uma máquina do Mekanism como o Configurator do jogador: o tipo
     * {@code transmission} ({@code TransmissionType}: GAS, SLURRY...) passa a {@code dataType} ({@code DataType}:
     * INPUT, OUTPUT, NONE...) na face absoluta {@code side}, e o {@code TileComponentConfig.sideChanged} invalida a
     * capability daquele lado e avisa o vizinho. Por reflexão: essas classes ficam em {@code mekanism.common}, fora da
     * API que o mod compila (o nome dos métodos do próprio Mekanism não é remapeado).
     */
    static void setSideData(BlockEntity machine, String transmission, String dataType, Direction side) {
        try {
            ClassLoader loader = machine.getClass().getClassLoader();
            Class<?> transmissionClass = Class.forName("mekanism.common.lib.transmitter.TransmissionType", true, loader);
            Class<?> dataTypeClass = Class.forName("mekanism.common.tile.component.config.DataType", true, loader);
            Object type = enumConstant(transmissionClass, transmission);
            Object data = enumConstant(dataTypeClass, dataType);
            Direction facing = (Direction) machine.getClass().getMethod("getDirection").invoke(machine);
            RelativeSide relative = RelativeSide.fromDirections(facing, side);
            Object component = machine.getClass().getMethod("getConfig").invoke(machine);
            Object info = component.getClass().getMethod("getConfig", transmissionClass).invoke(component, type);
            if (info == null) {
                throw new IllegalStateException("a máquina não configura " + transmission);
            }
            info.getClass().getMethod("setDataType", dataTypeClass, RelativeSide[].class)
                    .invoke(info, data, new RelativeSide[] {relative});
            component.getClass().getMethod("sideChanged", transmissionClass, RelativeSide.class).invoke(component, type, relative);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("não deu para configurar o lado da máquina do Mekanism", e);
        }
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        for (Object constant : enumClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(enumClass.getName() + " sem " + name);
    }

    @SuppressWarnings("unchecked")
    private static List<? extends IChemicalTank<?, ?>> machineTanks(BlockEntity machine, int subtype) {
        try {
            return (List<? extends IChemicalTank<?, ?>>) machine.getClass().getMethod(TANK_GETTERS[subtype], Direction.class)
                    .invoke(machine, (Object) null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("a máquina não tem tanques de " + MekanismChemicals.SUBTYPES.get(subtype).name, e);
        }
    }

    /** Põe o químico, por dentro (como a receita da máquina), no tanque interno {@code tank} do tipo dele; devolve a sobra. */
    static long fillMachineTank(BlockEntity machine, int tank, ResourceLocation chemical, long amount) {
        Subtype<?, ?> type = type(chemical);
        return fillInternal(type, machineTanks(machine, type.index).get(tank), chemical, amount);
    }

    private static <C extends Chemical<C>, S extends ChemicalStack<C>> long fillInternal(Subtype<C, S> type,
            IChemicalTank<?, ?> tank, ResourceLocation chemical, long amount) {
        @SuppressWarnings("unchecked")
        IChemicalTank<C, S> typed = (IChemicalTank<C, S>) tank;
        return typed.insert(type.stack(chemical, amount), Action.EXECUTE, AutomationType.INTERNAL).getAmount();
    }

    /** Quanto do químico há nos tanques internos da máquina, os do tipo dele. */
    static long machineAmount(BlockEntity machine, ResourceLocation chemical) {
        long total = 0;
        for (IChemicalTank<?, ?> tank : machineTanks(machine, type(chemical).index)) {
            ChemicalStack<?> stack = tank.getStack();
            if (!stack.isEmpty() && MekanismChemicals.id(stack).equals(chemical)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Põe {@code amount} mB do químico pela face de cima; devolve a sobra. */
    static long fill(ServerLevel level, BlockPos pos, ResourceLocation chemical, long amount) {
        return fill(type(chemical), handler(level, pos, type(chemical)), chemical, amount);
    }

    private static <C extends Chemical<C>, S extends ChemicalStack<C>> long fill(Subtype<C, S> type, Object handler,
            ResourceLocation chemical, long amount) {
        return type.handler(handler).insertChemical(type.stack(chemical, amount), Action.EXECUTE).getAmount();
    }

    /** Quanto do químico há nos tanques, pela face de cima. */
    static long amount(ServerLevel level, BlockPos pos, ResourceLocation chemical) {
        Subtype<?, ?> type = type(chemical);
        IChemicalHandler<?, ?> handler = type.handler(handler(level, pos, type));
        long total = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            ChemicalStack<?> stack = handler.getChemicalInTank(tank);
            if (!stack.isEmpty() && MekanismChemicals.id(stack).equals(chemical)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static Subtype<?, ?> type(ResourceLocation chemical) {
        Subtype<?, ?> type = MekanismChemicals.typeOf(chemical);
        if (type == null) {
            throw new IllegalArgumentException("químico desconhecido: " + chemical);
        }
        return type;
    }

    private static Object handler(ServerLevel level, BlockPos pos, Subtype<?, ?> type) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            throw new IllegalStateException("sem block entity em " + pos.toShortString());
        }
        return be.getCapability(type.capability, Direction.UP).resolve()
                .orElseThrow(() -> new IllegalStateException("sem capability de " + type.name + " em " + pos.toShortString()));
    }

    private static List<Tanks<?, ?>> tanks(BlockPos pos) {
        return TANKS.computeIfAbsent(pos.immutable(), key -> create(1, false));
    }

    private static List<Tanks<?, ?>> manyTanks(BlockPos pos) {
        return MANY.computeIfAbsent(pos.immutable(), key -> create(ChemicalTestTanks.MANY_TANKS, true));
    }

    /** Um handler por tipo, na ordem de {@link MekanismChemicals#SUBTYPES}, cada um com {@code count} tanques. */
    private static List<Tanks<?, ?>> create(int count, boolean outputOnly) {
        return List.of(
                new GasTanks(make(count, () -> ChemicalTankBuilder.GAS.createAllValid(ChemicalTestTanks.CAPACITY, () -> {
                })), outputOnly),
                new InfusionTanks(make(count, () -> ChemicalTankBuilder.INFUSION.createAllValid(ChemicalTestTanks.CAPACITY, () -> {
                })), outputOnly),
                new PigmentTanks(make(count, () -> ChemicalTankBuilder.PIGMENT.createAllValid(ChemicalTestTanks.CAPACITY, () -> {
                })), outputOnly),
                new SlurryTanks(make(count, () -> ChemicalTankBuilder.SLURRY.createAllValid(ChemicalTestTanks.CAPACITY, () -> {
                })), outputOnly));
    }

    private static <C extends Chemical<C>, S extends ChemicalStack<C>> List<IChemicalTank<C, S>> make(int count,
            Supplier<? extends IChemicalTank<C, S>> factory) {
        List<IChemicalTank<C, S>> tanks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tanks.add(factory.get());
        }
        return tanks;
    }

    /**
     * Tanques de um tipo vistos de fora (todas as faces). {@code outputOnly}: só de saída (inserir não aceita nada),
     * como o bloco de muitos tanques do {@code main}.
     */
    private abstract static class Tanks<C extends Chemical<C>, S extends ChemicalStack<C>> implements IChemicalHandler<C, S> {
        final List<IChemicalTank<C, S>> tanks;
        private final boolean outputOnly;

        Tanks(List<IChemicalTank<C, S>> tanks, boolean outputOnly) {
            this.tanks = tanks;
            this.outputOnly = outputOnly;
        }

        @Override
        public int getTanks() {
            return tanks.size();
        }

        @Override
        public S getChemicalInTank(int index) {
            return tanks.get(index).getStack();
        }

        @Override
        public void setChemicalInTank(int index, S stack) {
            tanks.get(index).setStack(stack);
        }

        @Override
        public long getTankCapacity(int index) {
            return tanks.get(index).getCapacity();
        }

        @Override
        public boolean isValid(int index, S stack) {
            return !outputOnly && tanks.get(index).isValid(stack);
        }

        @Override
        public S insertChemical(int index, S stack, Action action) {
            return outputOnly ? stack : tanks.get(index).insert(stack, action, AutomationType.EXTERNAL);
        }

        @Override
        public S extractChemical(int index, long amount, Action action) {
            return tanks.get(index).extract(amount, action, AutomationType.EXTERNAL);
        }
    }

    private static final class GasTanks extends Tanks<Gas, GasStack> implements IGasHandler {
        GasTanks(List<IChemicalTank<Gas, GasStack>> tanks, boolean outputOnly) {
            super(tanks, outputOnly);
        }
    }

    private static final class InfusionTanks extends Tanks<InfuseType, InfusionStack> implements IInfusionHandler {
        InfusionTanks(List<IChemicalTank<InfuseType, InfusionStack>> tanks, boolean outputOnly) {
            super(tanks, outputOnly);
        }
    }

    private static final class PigmentTanks extends Tanks<Pigment, PigmentStack> implements IPigmentHandler {
        PigmentTanks(List<IChemicalTank<Pigment, PigmentStack>> tanks, boolean outputOnly) {
            super(tanks, outputOnly);
        }
    }

    private static final class SlurryTanks extends Tanks<Slurry, SlurryStack> implements ISlurryHandler {
        SlurryTanks(List<IChemicalTank<Slurry, SlurryStack>> tanks, boolean outputOnly) {
            super(tanks, outputOnly);
        }
    }

    private ChemicalTestSupport() {
    }
}
