package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.compat.mekanism.MekanismChemicals;
import java.util.List;
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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A parte dos testes de químico que usa a API do Mekanism. Não é {@code @GameTestHolder} nem
 * {@code @EventBusSubscriber}: o NeoForge inspeciona essas classes por reflexão (as assinaturas dos
 * métodos), e um tipo do Mekanism numa assinatura impede o mod de carregar sem ele. Só é chamada
 * por {@link ChemicalTestTanks}, {@link ChemicalGameTests}, {@link DockChemicalGameTests} e os testes dos químicos da
 * Doca nas redes ({@link DockChemicalAe2GameTests}, {@link DockChemicalRs2GameTests}) com o Mekanism presente.
 */
final class ChemicalTestSupport {
    private static final Map<BlockPos, IChemicalTank> TANKS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, IChemicalTank[]> MANY = new ConcurrentHashMap<>();
    private static final Map<BlockPos, IChemicalTank> TYPED = new ConcurrentHashMap<>();
    private static final Map<BlockPos, ChemicalMachine> DOCK_MACHINES = new ConcurrentHashMap<>();

    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(MekanismChemicals.BLOCK, (level, pos, state, be, side) -> handler(pos), ChemicalTestTanks.BLOCK);
        event.registerBlock(MekanismChemicals.BLOCK, (level, pos, state, be, side) -> manyHandler(pos),
                ChemicalTestTanks.MANY_TANKS_BLOCK);
        event.registerBlock(MekanismChemicals.BLOCK, (level, pos, state, be, side) -> typedHandler(pos),
                ChemicalTestTanks.TYPED_TANK_BLOCK);
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) ->
                side == Direction.UP ? dockMachine(pos).items : null, ChemicalTestTanks.DOCK_MACHINE_BLOCK);
        event.registerBlock(MekanismChemicals.BLOCK, (level, pos, state, be, side) -> side == Direction.DOWN
                ? dockMachine(pos).outputHandler : side != null && side.getAxis().isHorizontal()
                        ? dockMachine(pos).inputHandler : null, ChemicalTestTanks.DOCK_MACHINE_BLOCK);
    }

    // ------------------------------------------------------------------ a máquina dos testes da Doca

    /** Esquece a máquina da Doca de {@code pos} (o mundo dos testes é reaproveitado entre lotes). */
    static void resetDockMachine(BlockPos pos) {
        DOCK_MACHINES.remove(pos.immutable());
    }

    /** A entrada de químico da máquina da Doca aceita na simulação e nada de verdade ({@code true}), ou volta ao normal. */
    static void dockMachineLying(BlockPos pos, boolean lying) {
        dockMachine(pos).lying = lying;
    }

    static ItemStack dockMachineItem(BlockPos pos) {
        return dockMachine(pos).items.getStackInSlot(0);
    }

    /** Quanto do químico há na entrada da máquina da Doca. */
    static long dockMachineInput(BlockPos pos, ResourceLocation chemical) {
        ChemicalStack stack = dockMachine(pos).input.getStack();
        return !stack.isEmpty() && MekanismChemicals.id(stack).equals(chemical) ? stack.getAmount() : 0;
    }

    /** Põe {@code amount} mB do químico na saída da máquina da Doca, como uma receita pronta. */
    static void fillDockMachineOutput(BlockPos pos, ResourceLocation chemical, long amount) {
        dockMachine(pos).output.insert(stack(chemical, amount), Action.EXECUTE, AutomationType.INTERNAL);
    }

    static long dockMachineOutput(BlockPos pos) {
        return dockMachine(pos).output.getStack().getAmount();
    }

    /** Um pedido de um químico só, como a Doca recebe ({@code ChemicalStack} como {@code Object}). */
    static List<Object> chemicalOrder(ResourceLocation chemical, long amount) {
        return List.of(stack(chemical, amount));
    }

    /** {@code amount} mB do químico, como {@code Object} ({@code ChemicalStack}), para as chaves das redes. */
    static Object chemical(ResourceLocation chemical, long amount) {
        return stack(chemical, amount);
    }

    private static ChemicalStack stack(ResourceLocation chemical, long amount) {
        return new ChemicalStack(MekanismAPI.CHEMICAL_REGISTRY.getHolder(chemical).orElseThrow(), amount);
    }

    private static ChemicalMachine dockMachine(BlockPos pos) {
        return DOCK_MACHINES.computeIfAbsent(pos.immutable(), key -> new ChemicalMachine());
    }

    /**
     * Uma máquina de duas entradas e uma saída, como uma do Mekanism que pede item e químico: um slot de item pela face
     * de cima, a entrada de químico pelas laterais (que pode mentir, {@link #lying}) e a saída de químico por baixo (só
     * dá).
     */
    private static final class ChemicalMachine {
        final ItemStackHandler items = new ItemStackHandler(1);
        final IChemicalTank input = BasicChemicalTank.createAllValid(ChemicalTestTanks.CAPACITY, () -> {
        });
        final IChemicalTank output = BasicChemicalTank.createAllValid(ChemicalTestTanks.CAPACITY, () -> {
        });
        volatile boolean lying;
        final IChemicalHandler inputHandler = new SingleTank(input) {
            @Override
            public ChemicalStack insertChemical(int index, ChemicalStack stack, Action action) {
                return lying && action.execute() ? stack : super.insertChemical(index, stack, action);
            }
        };
        final IChemicalHandler outputHandler = new SingleTank(output) {
            @Override
            public ChemicalStack insertChemical(int index, ChemicalStack stack, Action action) {
                return stack;
            }
        };
    }

    static void reset(BlockPos pos) {
        TANKS.remove(pos.immutable());
        MANY.remove(pos.immutable());
        TYPED.remove(pos.immutable());
    }

    /** O tanque seletivo de {@code pos} passa a aceitar só {@code chemical} (chame antes da primeira consulta). */
    static void acceptOnly(BlockPos pos, ResourceLocation chemical) {
        TYPED.put(pos.immutable(), BasicChemicalTank.createModern(ChemicalTestTanks.CAPACITY,
                stack -> MekanismChemicals.id(stack).equals(chemical), () -> {
                }));
    }

    /** Quanto há no tanque seletivo de {@code pos}. */
    static long typedAmount(BlockPos pos) {
        IChemicalTank tank = TYPED.get(pos.immutable());
        return tank == null ? 0 : tank.getStack().getAmount();
    }

    /** Um tanque só de entrada que só aceita um químico ({@link #acceptOnly}), como a entrada de uma máquina. */
    private static IChemicalHandler typedHandler(BlockPos pos) {
        IChemicalTank tank = TYPED.computeIfAbsent(pos.immutable(),
                key -> BasicChemicalTank.createModern(ChemicalTestTanks.CAPACITY, stack -> false, () -> {
                }));
        return handler(tank);
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
        return handler(TANKS.computeIfAbsent(pos.immutable(),
                key -> BasicChemicalTank.createAllValid(ChemicalTestTanks.CAPACITY, () -> {
                })));
    }

    /** Um handler de um tanque só sobre {@code tank}. */
    private static IChemicalHandler handler(IChemicalTank tank) {
        return new SingleTank(tank);
    }

    /** O handler de um tanque só, pela automação externa. */
    private static class SingleTank implements IChemicalHandler {
        private final IChemicalTank tank;

        SingleTank(IChemicalTank tank) {
            this.tank = tank;
        }

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
    }

    private ChemicalTestSupport() {
    }
}
