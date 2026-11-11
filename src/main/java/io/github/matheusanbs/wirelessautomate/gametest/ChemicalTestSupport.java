package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.compat.mekanism.MekanismChemicals;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.RelativeSide;
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
import org.jetbrains.annotations.Nullable;

/**
 * A parte dos testes de químico que usa a API do Mekanism. Não é {@code @GameTestHolder} nem
 * {@code @EventBusSubscriber}: o NeoForge inspeciona essas classes por reflexão (as assinaturas dos
 * métodos), e um tipo do Mekanism numa assinatura impede o mod de carregar sem ele. Só é chamada
 * por {@link ChemicalTestTanks}, {@link ChemicalGameTests}, {@link DockChemicalGameTests} e os testes dos químicos da
 * Doca nas redes ({@link DockChemicalAe2GameTests}, {@link DockChemicalRs2GameTests}) e {@link DockMekanismGameTests}
 * com o Mekanism presente.
 */
final class ChemicalTestSupport {
    private static final Map<BlockPos, IChemicalTank> TANKS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, IChemicalTank[]> MANY = new ConcurrentHashMap<>();
    private static final Map<BlockPos, IChemicalTank> TYPED = new ConcurrentHashMap<>();
    private static final Map<BlockPos, ChemicalMachine> DOCK_MACHINES = new ConcurrentHashMap<>();
    /** Os tanques que mentem ({@link #tankLying}), com quantas inserções de verdade recusaram. */
    private static final Map<BlockPos, AtomicInteger> LYING = new ConcurrentHashMap<>();

    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(MekanismChemicals.BLOCK, (level, pos, state, be, side) -> handler(pos), ChemicalTestTanks.BLOCK);
        event.registerBlock(MekanismChemicals.BLOCK, (level, pos, state, be, side) -> manyHandler(pos),
                ChemicalTestTanks.MANY_TANKS_BLOCK);
        event.registerBlock(MekanismChemicals.BLOCK, (level, pos, state, be, side) -> typedHandler(pos),
                ChemicalTestTanks.TYPED_TANK_BLOCK);
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) ->
                side == Direction.UP ? dockMachine(pos).items : null, ChemicalTestTanks.DOCK_MACHINE_BLOCK);
        event.registerBlock(MekanismChemicals.BLOCK, (level, pos, state, be, side) -> side == Direction.UP
                ? dockMachine(pos).top : side == Direction.DOWN ? dockMachine(pos).outputHandler
                        : side != null && side.getAxis().isHorizontal() ? dockMachine(pos).inputHandler : null,
                ChemicalTestTanks.DOCK_MACHINE_BLOCK);
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

    /**
     * A máquina da Doca ganha uma entrada de químico na face de cima, ao lado do slot de item, que só aceita
     * {@code chemical} (chame antes do vínculo).
     */
    static void dockMachineSelectiveTop(BlockPos pos, ResourceLocation chemical) {
        dockMachine(pos).top = handler(BasicChemicalTank.createModern(ChemicalTestTanks.CAPACITY,
                stack -> MekanismChemicals.id(stack).equals(chemical), () -> {
                }));
    }

    /** Quanto do químico há na entrada de cima da máquina da Doca ({@link #dockMachineSelectiveTop}). */
    static long dockMachineTop(BlockPos pos, ResourceLocation chemical) {
        IChemicalHandler top = dockMachine(pos).top;
        ChemicalStack stack = top == null ? ChemicalStack.EMPTY : top.getChemicalInTank(0);
        return !stack.isEmpty() && MekanismChemicals.id(stack).equals(chemical) ? stack.getAmount() : 0;
    }

    /** A saída da máquina da Doca aceita de volta o que saiu dela (o químico que o destino recusou). */
    static void dockMachineTakesBack(BlockPos pos) {
        dockMachine(pos).takesBack = true;
    }

    /** A saída da máquina da Doca oferece tudo na simulação e dá no máximo {@code max} mB por extração de verdade. */
    static void dockMachineStingy(BlockPos pos, long max) {
        dockMachine(pos).stingy = max;
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

    /** {@code stack} ({@code ChemicalStack}) é {@code amount} mB do químico {@code chemical}. */
    static boolean isChemical(Object stack, ResourceLocation chemical, long amount) {
        return stack instanceof ChemicalStack chemicalStack && !chemicalStack.isEmpty()
                && MekanismChemicals.id(chemicalStack).equals(chemical) && chemicalStack.getAmount() == amount;
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
     * dá, menos com {@link #takesBack}). Opcionais: uma entrada de químico seletiva em cima ({@link #top}) e a saída que
     * dá menos que ofereceu ({@link #stingy}).
     */
    private static final class ChemicalMachine {
        final ItemStackHandler items = new ItemStackHandler(1);
        final IChemicalTank input = BasicChemicalTank.createAllValid(ChemicalTestTanks.CAPACITY, () -> {
        });
        final IChemicalTank output = BasicChemicalTank.createAllValid(ChemicalTestTanks.CAPACITY, () -> {
        });
        volatile boolean lying;
        volatile @Nullable IChemicalHandler top;
        volatile boolean takesBack;
        /** O máximo por extração de verdade da saída; 0 sem teto. */
        volatile long stingy;
        final IChemicalHandler inputHandler = new SingleTank(input) {
            @Override
            public ChemicalStack insertChemical(int index, ChemicalStack stack, Action action) {
                return lying && action.execute() ? stack : super.insertChemical(index, stack, action);
            }
        };
        final IChemicalHandler outputHandler = new SingleTank(output) {
            @Override
            public ChemicalStack insertChemical(int index, ChemicalStack stack, Action action) {
                return takesBack ? super.insertChemical(index, stack, action) : stack;
            }

            @Override
            public ChemicalStack extractChemical(int index, long amount, Action action) {
                long max = stingy;
                return super.extractChemical(index, action.execute() && max > 0 ? Math.min(amount, max) : amount,
                        action);
            }
        };
    }

    static void reset(BlockPos pos) {
        LYING.remove(pos.immutable());
        TANKS.remove(pos.immutable());
        MANY.remove(pos.immutable());
        TYPED.remove(pos.immutable());
    }

    /**
     * O tanque de {@code pos} aceita na simulação e recusa toda inserção de verdade, contando as recusas (chame
     * depois do {@link #reset}).
     */
    static void tankLying(BlockPos pos) {
        LYING.put(pos.immutable(), new AtomicInteger());
    }

    /** Quantas inserções de verdade o tanque que mente de {@code pos} recusou. */
    static int tankRefusals(BlockPos pos) {
        AtomicInteger refusals = LYING.get(pos.immutable());
        return refusals == null ? 0 : refusals.get();
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
        BlockPos key = pos.immutable();
        IChemicalTank tank = TANKS.computeIfAbsent(key,
                created -> BasicChemicalTank.createAllValid(ChemicalTestTanks.CAPACITY, () -> {
                }));
        return new SingleTank(tank) {
            @Override
            public ChemicalStack insertChemical(int index, ChemicalStack stack, Action action) {
                AtomicInteger refusals = LYING.get(key);
                if (refusals != null && action.execute()) {
                    refusals.incrementAndGet();
                    return stack;
                }
                return super.insertChemical(index, stack, action);
            }
        };
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

    // ------------------------------------------------------------------ uma máquina de verdade do Mekanism

    /**
     * Liga as faces da máquina do Mekanism em {@code be} (que vêm todas desligadas): a entrada de item em Cima, a
     * saída em Baixo e a energia pelos quatro lados. A configuração de lados é do jar inteiro do Mekanism, fora da API
     * que compila aqui, então vai por reflexão ({@code ISideConfiguration.getConfig()}, {@code ConfigInfo.setDataType},
     * {@code sideChanged}).
     */
    static void mekanismSides(Object be) {
        try {
            ClassLoader loader = be.getClass().getClassLoader();
            Class<?> transmission = Class.forName("mekanism.common.lib.transmitter.TransmissionType", true, loader);
            Class<?> dataType = Class.forName("mekanism.common.tile.component.config.DataType", true, loader);
            Object item = enumConstant(transmission, "ITEM");
            Object config = be.getClass().getMethod("getConfig").invoke(be);
            Object info = config.getClass().getMethod("getConfig", transmission).invoke(config, item);
            var setDataType = info.getClass().getMethod("setDataType", dataType, RelativeSide.class);
            var sideChanged = config.getClass().getMethod("sideChanged", transmission, RelativeSide.class);
            setDataType.invoke(info, enumConstant(dataType, "INPUT"), RelativeSide.TOP);
            setDataType.invoke(info, enumConstant(dataType, "OUTPUT"), RelativeSide.BOTTOM);
            sideChanged.invoke(config, item, RelativeSide.TOP);
            sideChanged.invoke(config, item, RelativeSide.BOTTOM);
            // a energia entra pelos lados (Frente, Trás, Esquerda e Direita)
            Object energy = enumConstant(transmission, "ENERGY");
            Object energyInfo = config.getClass().getMethod("getConfig", transmission).invoke(config, energy);
            for (RelativeSide side : new RelativeSide[] {RelativeSide.FRONT, RelativeSide.BACK, RelativeSide.LEFT,
                    RelativeSide.RIGHT}) {
                setDataType.invoke(energyInfo, enumConstant(dataType, "INPUT"), side);
                sideChanged.invoke(config, energy, side);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("a configuração de lados do Mekanism mudou", e);
        }
    }

    /**
     * Instala na máquina do Mekanism em {@code be} os upgrades {@code upgrades} (o nome do {@code Upgrade} e a
     * quantidade, alternados), pelo componente de upgrades ({@code TileComponentUpgrade.addUpgrades}, que recalcula a
     * máquina como o slot de upgrade faz); o {@code CREATIVE} é do Mekanism Extras, que o põe no enum do Mekanism. Liga
     * também a ordenação automática da fábrica ({@code toggleSorting}), se ela tiver. Por reflexão, como o
     * {@link #mekanismSides}. Devolve quantos upgrades entraram no total.
     */
    static int mekanismUpgrades(Object be, Object... upgrades) {
        try {
            ClassLoader loader = be.getClass().getClassLoader();
            Class<?> upgrade = Class.forName("mekanism.api.Upgrade", true, loader);
            Object component = be.getClass().getMethod("getComponent").invoke(be);
            var add = component.getClass().getMethod("addUpgrades", upgrade, int.class);
            int added = 0;
            for (int i = 0; i < upgrades.length; i += 2) {
                added += (Integer) add.invoke(component, enumConstant(upgrade, (String) upgrades[i]),
                        (Integer) upgrades[i + 1]);
            }
            try {
                if (!(Boolean) be.getClass().getMethod("isSorting").invoke(be)) {
                    be.getClass().getMethod("toggleSorting").invoke(be);
                }
            } catch (NoSuchMethodException e) {
                // não é fábrica: nada a ordenar
            }
            return added;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("o componente de upgrades do Mekanism mudou", e);
        }
    }

    private static Object enumConstant(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new IllegalStateException(type.getName() + " sem " + name);
    }
}
