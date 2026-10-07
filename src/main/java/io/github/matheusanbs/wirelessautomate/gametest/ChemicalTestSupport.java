package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.compat.mekanism.MekanismChemicals;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * A parte dos testes de químico que usa a API do Mekanism. Não é {@code @GameTestHolder} nem
 * {@code @EventBusSubscriber}: o NeoForge inspeciona essas classes por reflexão (as assinaturas dos
 * métodos), e um tipo do Mekanism numa assinatura impede o mod de carregar sem ele. Só é chamada
 * por {@link ChemicalTestTanks} e {@link ChemicalGameTests} com o Mekanism presente.
 */
final class ChemicalTestSupport {
    private static final Map<BlockPos, IChemicalTank> TANKS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, IChemicalTank[]> MANY = new ConcurrentHashMap<>();

    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(MekanismChemicals.BLOCK, (level, pos, state, be, side) -> handler(pos), ChemicalTestTanks.BLOCK);
        event.registerBlock(MekanismChemicals.BLOCK, (level, pos, state, be, side) -> manyHandler(pos),
                ChemicalTestTanks.MANY_TANKS_BLOCK);
    }

    static void reset(BlockPos pos) {
        TANKS.remove(pos.immutable());
        MANY.remove(pos.immutable());
    }

    /** Põe {@code amount} mB do químico no tanque {@code tank} do bloco de muitos tanques; devolve a sobra. */
    static long fillTank(BlockPos pos, int tank, ResourceLocation chemical, long amount) {
        ChemicalStack stack = new ChemicalStack(MekanismAPI.CHEMICAL_REGISTRY.getHolder(chemical).orElseThrow(), amount);
        return manyTanks(pos)[tank].insert(stack, Action.EXECUTE, AutomationType.INTERNAL).getAmount();
    }

    /** Quanto há no tanque {@code tank} do bloco de muitos tanques. */
    static long tankAmount(BlockPos pos, int tank) {
        return manyTanks(pos)[tank].getStack().getAmount();
    }

    private static IChemicalTank[] manyTanks(BlockPos pos) {
        return MANY.computeIfAbsent(pos.immutable(), key -> {
            IChemicalTank[] tanks = new IChemicalTank[ChemicalTestTanks.MANY_TANKS];
            for (int i = 0; i < tanks.length; i++) {
                tanks[i] = BasicChemicalTank.createAllValid(ChemicalTestTanks.CAPACITY, () -> {
                });
            }
            return tanks;
        });
    }

    /** Muitos tanques, só de saída (inserir não aceita nada). */
    private static IChemicalHandler manyHandler(BlockPos pos) {
        IChemicalTank[] tanks = manyTanks(pos);
        return new IChemicalHandler() {
            @Override
            public int getChemicalTanks() {
                return tanks.length;
            }

            @Override
            public ChemicalStack getChemicalInTank(int index) {
                return tanks[index].getStack();
            }

            @Override
            public void setChemicalInTank(int index, ChemicalStack stack) {
                tanks[index].setStack(stack);
            }

            @Override
            public long getChemicalTankCapacity(int index) {
                return tanks[index].getCapacity();
            }

            @Override
            public boolean isValid(int index, ChemicalStack stack) {
                return false;
            }

            @Override
            public ChemicalStack insertChemical(int index, ChemicalStack stack, Action action) {
                return stack;
            }

            @Override
            public ChemicalStack extractChemical(int index, long amount, Action action) {
                return tanks[index].extract(amount, action, AutomationType.EXTERNAL);
            }
        };
    }

    static boolean hasHandler(ServerLevel level, BlockPos pos) {
        return level.getCapability(MekanismChemicals.BLOCK, pos, Direction.UP) != null;
    }

    /** Põe {@code amount} mB do químico pela face de cima; devolve a sobra. */
    static long fill(ServerLevel level, BlockPos pos, ResourceLocation chemical, long amount) {
        ChemicalStack stack = new ChemicalStack(MekanismAPI.CHEMICAL_REGISTRY.getHolder(chemical).orElseThrow(), amount);
        return level.getCapability(MekanismChemicals.BLOCK, pos, Direction.UP).insertChemical(stack, Action.EXECUTE).getAmount();
    }

    /** Quanto do químico há nos tanques, pela face de cima. */
    static long amount(ServerLevel level, BlockPos pos, ResourceLocation chemical) {
        IChemicalHandler handler = level.getCapability(MekanismChemicals.BLOCK, pos, Direction.UP);
        long total = 0;
        for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
            ChemicalStack stack = handler.getChemicalInTank(tank);
            if (!stack.isEmpty() && MekanismChemicals.id(stack).equals(chemical)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static IChemicalHandler handler(BlockPos pos) {
        IChemicalTank tank = TANKS.computeIfAbsent(pos.immutable(),
                key -> BasicChemicalTank.createAllValid(ChemicalTestTanks.CAPACITY, () -> {
                }));
        return new IChemicalHandler() {
            @Override
            public int getChemicalTanks() {
                return 1;
            }

            @Override
            public ChemicalStack getChemicalInTank(int index) {
                return tank.getStack();
            }

            @Override
            public void setChemicalInTank(int index, ChemicalStack stack) {
                tank.setStack(stack);
            }

            @Override
            public long getChemicalTankCapacity(int index) {
                return tank.getCapacity();
            }

            @Override
            public boolean isValid(int index, ChemicalStack stack) {
                return tank.isValid(stack);
            }

            @Override
            public ChemicalStack insertChemical(int index, ChemicalStack stack, Action action) {
                return tank.insert(stack, action, AutomationType.EXTERNAL);
            }

            @Override
            public ChemicalStack extractChemical(int index, long amount, Action action) {
                return tank.extract(amount, action, AutomationType.EXTERNAL);
            }
        };
    }

    private ChemicalTestSupport() {
    }
}
