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

    /** O handler do tanque de teste da posição para a capability, ou {@code null} se não é de químico. */
    static @Nullable Object handler(Capability<?> capability, BlockPos pos) {
        int subtype = MekanismChemicals.CAPABILITIES.indexOf(capability);
        return subtype < 0 ? null : tanks(pos).get(subtype);
    }

    /** O handler do bloco de muitos tanques (só de saída) da posição, ou {@code null}. */
    static @Nullable Object manyHandler(Capability<?> capability, BlockPos pos) {
        int subtype = MekanismChemicals.CAPABILITIES.indexOf(capability);
        return subtype < 0 ? null : manyTanks(pos).get(subtype);
    }

    static void reset(BlockPos pos) {
        TANKS.remove(pos.immutable());
        MANY.remove(pos.immutable());
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

    static boolean hasHandler(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.getCapability(MekanismChemicals.GAS, Direction.UP).isPresent();
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
