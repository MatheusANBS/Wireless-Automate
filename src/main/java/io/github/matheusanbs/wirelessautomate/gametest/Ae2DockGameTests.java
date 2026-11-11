package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockAe2;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.DockMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.DockStatePayload;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A Doca com o AE2 de verdade: a capability {@code ae2:crafting_machine} nas seis faces, um pedido de uma CPU por um
 * Pattern Provider numa rede montada no teste (energia criativa, drive com célula, armazenamento de crafting) com a
 * volta direto para a rede, a rede sem energia e o provedor quebrado que seguram a volta, o aviso da trava "até o
 * resultado", o padrão de bancada recusado, o padrão de item + fluido, as máquinas cheias que seguram o pedido sem perder nada, o nome do lado no
 * Pattern Access Terminal e o "tem provedor". Rodam só na run {@code runGameTestServerAe2}, que tem o AE2 na pasta mods
 * e liga o namespace {@value #NAMESPACE}.
 *
 * <p>As máquinas são Altos-fornos (100 ticks por item, metade da fornalha), para o pedido inteiro caber no limite de
 * 300 ticks do teste. Nenhum tipo do AE2 nas assinaturas: o que usa a API fica em {@link Ae2TestSupport}.
 */
@GameTestHolder(Ae2DockGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class Ae2DockGameTests {
    static final String NAMESPACE = "wirelessautomate_ae2";

    private static final BlockPos DOCK = new BlockPos(2, 1, 2);
    /** O provedor, encostado no lado Norte da Doca. */
    private static final BlockPos PROVIDER = DOCK.north();
    /**
     * A rede: o armazenamento de crafting a oeste do provedor, a energia a oeste dele e o drive ao norte dele (encostado
     * na CPU, não na energia: tirar a energia não separa o drive da grade).
     */
    private static final BlockPos CPU = PROVIDER.west();
    static final BlockPos ENERGY = CPU.west();
    private static final BlockPos DRIVE = CPU.north();
    private static final BlockPos[] FURNACES = {new BlockPos(4, 1, 1), new BlockPos(4, 1, 2), new BlockPos(4, 1, 3)};
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final ResourceLocation BLAST_ID = BuiltInRegistries.BLOCK.getKey(Blocks.BLAST_FURNACE);

    private Ae2DockGameTests() {
    }

    static boolean enabled() {
        return Boolean.getBoolean("wirelessautomate.ae2Tests") && ModList.get().isLoaded("ae2");
    }

    static Block ae2(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ae2", path));
    }

    /** A run do AE2 carrega o AE2, e a Doca expõe a capability dele. */
    @GameTest(template = "empty")
    public static void ae2IsLoaded(GameTestHelper helper) {
        if (!Boolean.getBoolean("wirelessautomate.ae2Tests")) {
            helper.succeed();
            return;
        }
        helper.assertTrue(ModList.get().isLoaded("ae2"), "o AE2 não carregou na run do AE2");
        helper.assertTrue(DockAe2.LOADED, "a ponte não vê o AE2");
        helper.assertTrue(ae2("pattern_provider") != Blocks.AIR, "sem o Pattern Provider no registro");
        dock(helper);
        helper.assertTrue(Ae2TestSupport.hasMachine(helper.getLevel(), helper.absolutePos(DOCK), Direction.NORTH),
                "a Doca sem a capability ae2:crafting_machine");
        helper.succeed();
    }

    private static PatternDockBlockEntity dock(GameTestHelper helper) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.BASIC));
        return helper.getBlockEntity(DOCK);
    }

    /** Os Altos-fornos virados para o norte, vinculados ao lado Norte com o perfil do Afinador. */
    private static void linkFurnaces(GameTestHelper helper, PatternDockBlockEntity dock, int count) {
        for (int i = 0; i < count; i++) {
            helper.setBlock(FURNACES[i], Blocks.BLAST_FURNACE.defaultBlockState()
                    .setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
            helper.assertTrue(dock.link(NORTH, helper.absolutePos(FURNACES[i]), BLAST_ID, Direction.NORTH.ordinal()),
                    "não vinculou o alto-forno " + i);
        }
        DockSide side = dock.side(NORTH);
        side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BACK).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
    }

    private static void registered(PatternDockBlockEntity dock) {
        if (!NetworkManager.get().hasVisitor(dock.returns())) {
            throw new GameTestAssertException("a Doca ainda não carregou");
        }
    }

    private static void room() {
        if (!NetworkManager.get().budget().outsideRoom()) {
            throw new GameTestAssertException("sem orçamento neste tick");
        }
    }

    private static ItemStack ironPattern() {
        return Ae2TestSupport.processingPattern(Items.IRON_INGOT, 1, Items.RAW_IRON, 1, Items.COAL, 1);
    }

    /** As seis faces têm a capability; sem máquina, nenhuma aceita pedidos. */
    @GameTest(template = "empty")
    public static void capabilityOnSixFaces(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    BlockPos pos = helper.absolutePos(DOCK);
                    for (Direction face : Direction.values()) {
                        helper.assertTrue(Ae2TestSupport.hasMachine(helper.getLevel(), pos, face), "sem a capability em " + face);
                        helper.assertFalse(Ae2TestSupport.acceptsPlans(helper.getLevel(), pos, face), "aceita sem máquina em " + face);
                    }
                })
                .thenSucceed();
    }

    /**
     * A rede de verdade: um pedido de 3 lingotes pela CPU passa pelo provedor, a Doca reparte as três execuções entre
     * os três Altos-fornos, que trabalham ao mesmo tempo (num mesmo tick, os três com minério na entrada ou acesos), e
     * os lingotes vão direto para a rede, onde a CPU os conta: o retorno do provedor fica vazio o tempo todo.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void cpuOrderUsesThreeFurnaces(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = network(helper);
        Object[] plan = new Object[1];
        helper.onEachTick(() -> returnEmpty(helper));
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> linkFurnaces(helper, dock, 3))
                .thenWaitUntil(() -> ready(helper))
                .thenExecute(() -> stock(helper, 3))
                .thenIdle(2)
                .thenExecute(() -> plan(helper, plan, 3))
                .thenWaitUntil(() -> helper.assertTrue(Ae2TestSupport.planned(plan[0]), "calculando"))
                .thenExecute(() -> submit(helper, plan[0]))
                .thenWaitUntil(() -> helper.assertTrue(allWorking(helper), "os três altos-fornos não trabalharam juntos"))
                .thenWaitUntil(() -> helper.assertTrue(stored(helper, Items.IRON_INGOT) == 3,
                        "lingotes na rede: " + stored(helper, Items.IRON_INGOT)))
                .thenExecute(() -> {
                    for (BlockPos furnace : FURNACES) {
                        helper.assertTrue(dock.machineAt(helper.absolutePos(furnace)).lastActive() > 0,
                                "o alto-forno " + furnace.toShortString() + " não trabalhou");
                    }
                    helper.assertTrue(stored(helper, Items.RAW_IRON) == 0, "sobrou minério na rede");
                })
                .thenWaitUntil(() -> helper.assertFalse(Ae2TestSupport.requesting(helper.getLevel(),
                        helper.absolutePos(PROVIDER), Items.IRON_INGOT), "o pedido não terminou"))
                .thenSucceed();
    }

    /**
     * Um pedido de {@code count} lingotes pela CPU {@code cpu}, numa rede com o provedor {@code provider} encostado no
     * lado Norte e três Altos-fornos: a Doca recebe as execuções, e os lingotes vão direto para a rede (o retorno do
     * provedor fica vazio o tempo todo), onde a CPU os conta e o pedido termina. A rede tem uma CPU só, cuja classe contém
     * {@code cpuClass}. Para os provedores e as CPUs dos addons ({@link Ae2AddonDockGameTests}).
     */
    static void orderEndsInNetwork(GameTestHelper helper, Block provider, Block cpu, int count, String cpuClass) {
        PatternDockBlockEntity dock = network(helper, provider, cpu);
        Object[] plan = new Object[1];
        helper.onEachTick(() -> returnEmpty(helper));
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> linkFurnaces(helper, dock, FURNACES.length))
                .thenWaitUntil(() -> ready(helper))
                .thenExecute(() -> {
                    String cpus = Ae2TestSupport.cpus(helper.getLevel(), helper.absolutePos(PROVIDER));
                    helper.assertTrue(!cpus.contains(",") && cpus.contains(cpuClass), "CPUs da rede: " + cpus);
                    stock(helper, count);
                })
                .thenIdle(2)
                .thenExecute(() -> plan(helper, plan, count))
                .thenWaitUntil(() -> helper.assertTrue(Ae2TestSupport.planned(plan[0]), "calculando"))
                .thenExecute(() -> submit(helper, plan[0]))
                .thenWaitUntil(() -> helper.assertTrue(stored(helper, Items.IRON_INGOT) == count,
                        "lingotes na rede: " + stored(helper, Items.IRON_INGOT)))
                .thenExecute(() -> helper.assertTrue(dock.side(NORTH).returned() >= count,
                        "a Doca devolveu " + dock.side(NORTH).returned()))
                .thenWaitUntil(() -> helper.assertFalse(Ae2TestSupport.requesting(helper.getLevel(),
                        helper.absolutePos(PROVIDER), Items.IRON_INGOT), "o pedido não terminou"))
                .thenSucceed();
    }

    /** Neste tick, os três Altos-fornos têm minério na entrada ou estão acesos. */
    private static boolean allWorking(GameTestHelper helper) {
        for (BlockPos furnace : FURNACES) {
            AbstractFurnaceBlockEntity entity = helper.getBlockEntity(furnace);
            if (entity.getItem(0).isEmpty() && !entity.getBlockState().getValue(AbstractFurnaceBlock.LIT)) {
                return false;
            }
        }
        return true;
    }

    /** O retorno do provedor está vazio neste tick (a volta não passa por ele). */
    private static void returnEmpty(GameTestHelper helper) {
        if (helper.getBlockEntity(PROVIDER) != null && !Ae2TestSupport.returnEmpty(helper.getLevel(),
                helper.absolutePos(PROVIDER), Direction.SOUTH)) {
            throw new GameTestAssertException("o resultado passou pelo retorno do provedor");
        }
    }

    /**
     * A trava "até o resultado" nunca destrava com a Doca (o resultado não passa pelo retorno do provedor): com ela, a
     * linha do lado mostra "Tire a trava do provedor"; sem ela, o lado volta a ficar pronto.
     */
    @GameTest(template = "empty")
    public static void lockUntilResultShowsOnTheLine(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = network(helper);
        DockMenu menu = new DockMenu(1, TestPlayers.inLevel(helper).getInventory(), dock);
        BlockPos provider = helper.absolutePos(PROVIDER);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> linkFurnaces(helper, dock, 1))
                .thenExecute(() -> {
                    DockStatus.State free = state(menu, 0);
                    helper.assertTrue(free == DockStatus.State.READY, "sem trava: " + free);
                    Ae2TestSupport.lockUntilResult(helper.getLevel(), provider);
                    DockStatus.State locked = state(menu, DockMenu.SYNC_INTERVAL);
                    helper.assertTrue(locked == DockStatus.State.PROVIDER_LOCK, "com a trava: " + locked);
                    Ae2TestSupport.unlock(helper.getLevel(), provider);
                    DockStatus.State unlocked = state(menu, 2L * DockMenu.SYNC_INTERVAL);
                    helper.assertTrue(unlocked == DockStatus.State.READY, "sem a trava de novo: " + unlocked);
                })
                .thenSucceed();
    }

    /** O estado da linha Norte na conferência do menu em {@code now} (um estado novo a cada mudança). */
    private static DockStatus.State state(DockMenu menu, long now) {
        DockStatePayload payload = menu.pollState(now);
        if (payload == null) {
            throw new GameTestAssertException("o estado não mudou em " + now);
        }
        return DockStatus.State.values()[payload.lines().get(NORTH).state()];
    }

    /**
     * Sem energia, o nó do provedor fica inativo e a rede não recebe: o lingote fica no Alto-forno e o lado marca
     * "provedor cheio"; com a energia de volta, o lingote vai para a rede (sem passar pelo retorno do provedor). Só a
     * energia cai: o drive encosta na CPU, e a grade continua com ele.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void unpoweredNetworkHoldsTheReturn(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = network(helper);
        helper.onEachTick(() -> returnEmpty(helper));
        BlockPos provider = helper.absolutePos(PROVIDER);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> linkFurnaces(helper, dock, 1))
                .thenWaitUntil(() -> ready(helper))
                .thenExecute(() -> helper.setBlock(ENERGY, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertFalse(Ae2TestSupport.active(helper.getLevel(), provider),
                        "o nó continua ativo sem energia"))
                .thenExecute(() -> {
                    AbstractFurnaceBlockEntity furnace = helper.getBlockEntity(FURNACES[0]);
                    furnace.setItem(2, new ItemStack(Items.IRON_INGOT));
                    furnace.setChanged();
                })
                .thenWaitUntil(() -> helper.assertTrue(dock.side(NORTH).providerFull(), "o lado não marcou cheio"))
                .thenExecute(() -> {
                    AbstractFurnaceBlockEntity furnace = helper.getBlockEntity(FURNACES[0]);
                    helper.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT), "o lingote saiu sem rede");
                    helper.setBlock(ENERGY, ae2("creative_energy_cell"));
                })
                .thenWaitUntil(() -> helper.assertTrue(stored(helper, Items.IRON_INGOT) == 1,
                        "lingotes na rede: " + stored(helper, Items.IRON_INGOT)))
                .thenExecute(() -> {
                    AbstractFurnaceBlockEntity furnace = helper.getBlockEntity(FURNACES[0]);
                    helper.assertTrue(furnace.getItem(2).isEmpty(), "o lingote ficou no Alto-forno");
                    helper.assertFalse(dock.side(NORTH).providerFull(), "o lado continuou cheio");
                })
                .thenSucceed();
    }

    /**
     * O provedor quebrado com o resultado na máquina: o lado fica sem destino e espera, com o lingote no Alto-forno;
     * com um provedor novo no lugar (o cache do lado avisa a troca), o lingote vai para a rede. Nada sai nem se perde
     * no meio.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void brokenProviderHoldsAndResumes(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = network(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> linkFurnaces(helper, dock, 1))
                .thenWaitUntil(() -> ready(helper))
                .thenExecute(() -> {
                    helper.setBlock(PROVIDER, Blocks.AIR);
                    AbstractFurnaceBlockEntity furnace = helper.getBlockEntity(FURNACES[0]);
                    furnace.setItem(2, new ItemStack(Items.IRON_INGOT));
                    furnace.setChanged();
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    AbstractFurnaceBlockEntity furnace = helper.getBlockEntity(FURNACES[0]);
                    helper.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT) && furnace.getItem(2).getCount() == 1,
                            "o lingote saiu sem provedor: " + furnace.getItem(2));
                    helper.assertTrue(dock.side(NORTH).returned() == 0, "devolvidos: " + dock.side(NORTH).returned());
                    helper.assertItemEntityNotPresent(Items.IRON_INGOT);
                    helper.setBlock(PROVIDER, ae2("pattern_provider"));
                })
                .thenWaitUntil(() -> helper.assertTrue(Ae2TestSupport.active(helper.getLevel(),
                        helper.absolutePos(PROVIDER)), "o provedor novo não subiu"))
                .thenWaitUntil(() -> helper.assertTrue(stored(helper, Items.IRON_INGOT) == 1,
                        "lingotes na rede: " + stored(helper, Items.IRON_INGOT)))
                .thenExecute(() -> {
                    AbstractFurnaceBlockEntity furnace = helper.getBlockEntity(FURNACES[0]);
                    helper.assertTrue(furnace.getItem(2).isEmpty(), "o lingote ficou no Alto-forno");
                    helper.assertTrue(dock.side(NORTH).returned() == 1, "devolvidos: " + dock.side(NORTH).returned());
                    helper.assertItemEntityNotPresent(Items.IRON_INGOT);
                })
                .thenSucceed();
    }

    /** A Doca, o provedor ao norte com o padrão do lingote e a rede ligada a ele. */
    private static PatternDockBlockEntity network(GameTestHelper helper) {
        return network(helper, ae2("pattern_provider"), ae2("1k_crafting_storage"));
    }

    /** A rede de {@link #network(GameTestHelper)} com outro provedor e outra CPU (os dos addons). */
    private static PatternDockBlockEntity network(GameTestHelper helper, Block provider, Block cpu) {
        PatternDockBlockEntity dock = dock(helper);
        helper.setBlock(PROVIDER, provider);
        helper.setBlock(CPU, cpu);
        helper.setBlock(ENERGY, ae2("creative_energy_cell"));
        helper.setBlock(DRIVE, ae2("drive"));
        helper.assertTrue(Ae2TestSupport.insertCell(helper.getLevel(), helper.absolutePos(DRIVE)), "a célula não entrou");
        helper.assertTrue(Ae2TestSupport.installPattern(helper.getLevel(), helper.absolutePos(PROVIDER), ironPattern()),
                "o padrão não entrou no provedor");
        return dock;
    }

    private static void ready(GameTestHelper helper) {
        String missing = Ae2TestSupport.notReady(helper.getLevel(), helper.absolutePos(PROVIDER), Items.IRON_INGOT);
        if (missing != null) {
            throw new GameTestAssertException("a rede ainda não subiu: " + missing);
        }
    }

    private static long stored(GameTestHelper helper, Item item) {
        return Ae2TestSupport.stored(helper.getLevel(), helper.absolutePos(PROVIDER), item);
    }

    private static void stock(GameTestHelper helper, int count) {
        BlockPos pos = helper.absolutePos(PROVIDER);
        helper.assertTrue(Ae2TestSupport.stock(helper.getLevel(), pos, Items.RAW_IRON, count) == count, "o minério não coube");
        helper.assertTrue(Ae2TestSupport.stock(helper.getLevel(), pos, Items.COAL, count) == count, "o carvão não coube");
    }

    /** O cálculo lê o estoque guardado da rede, que só se atualiza no tick seguinte ao da entrada. */
    private static void plan(GameTestHelper helper, Object[] plan, int count) {
        plan[0] = Ae2TestSupport.plan(helper.getLevel(), helper.absolutePos(PROVIDER), Items.IRON_INGOT, count);
    }

    private static void submit(GameTestHelper helper, Object plan) {
        String error = Ae2TestSupport.submit(helper.getLevel(), helper.absolutePos(PROVIDER), plan);
        if (error != null) {
            throw new GameTestAssertException(error);
        }
    }

    /** O padrão de bancada é recusado e o lado guarda o motivo; nada entra no alto-forno. */
    @GameTest(template = "empty")
    public static void craftingPatternRefused(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        long[] remaining = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> linkFurnaces(helper, dock, 1))
                .thenWaitUntil(Ae2DockGameTests::room)
                .thenExecute(() -> {
                    BlockPos pos = helper.absolutePos(DOCK);
                    helper.assertTrue(Ae2TestSupport.acceptsPlans(helper.getLevel(), pos, Direction.NORTH),
                            "o lado com máquina não aceita pedidos");
                    helper.assertFalse(Ae2TestSupport.push(helper.getLevel(), pos, Direction.NORTH,
                            Ae2TestSupport.craftingPattern(helper.getLevel()), remaining), "aceitou um padrão de bancada");
                    helper.assertTrue(dock.side(NORTH).lastRefusal() == DockStatus.CRAFTING_PATTERN,
                            "motivo: " + dock.side(NORTH).lastRefusal());
                    helper.assertTrue(remaining[0] == 1, "as entradas mudaram: " + remaining[0]);
                    AbstractFurnaceBlockEntity furnace = helper.getBlockEntity(FURNACES[0]);
                    helper.assertTrue(furnace.isEmpty(), "algo entrou no alto-forno");
                })
                .thenSucceed();
    }

    /** Com os três Altos-fornos ocupados, o pedido é recusado sem perder nada: as entradas do AE2 ficam inteiras. */
    @GameTest(template = "empty")
    public static void allFullHoldsWithoutLoss(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        long[] remaining = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    linkFurnaces(helper, dock, 3);
                    for (BlockPos furnace : FURNACES) {
                        AbstractFurnaceBlockEntity entity = helper.getBlockEntity(furnace);
                        entity.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    }
                })
                .thenWaitUntil(Ae2DockGameTests::room)
                .thenExecute(() -> {
                    helper.assertFalse(Ae2TestSupport.push(helper.getLevel(), helper.absolutePos(DOCK), Direction.NORTH,
                            ironPattern(), remaining), "aceitou com as máquinas cheias");
                    helper.assertTrue(dock.side(NORTH).lastRefusal() == DockStatus.ALL_FULL,
                            "motivo: " + dock.side(NORTH).lastRefusal());
                    helper.assertTrue(remaining[0] == 2, "as entradas mudaram: " + remaining[0]);
                    helper.assertFalse(dock.side(NORTH).hasPending(), "ficou pendente");
                    for (BlockPos furnace : FURNACES) {
                        AbstractFurnaceBlockEntity entity = helper.getBlockEntity(furnace);
                        helper.assertTrue(entity.getItem(0).is(Items.COBBLESTONE) && entity.getItem(0).getCount() == 64
                                && entity.getItem(1).isEmpty(), "o alto-forno mudou");
                    }
                })
                .thenSucceed();
    }

    /**
     * Um padrão de item + fluido (um minério e um balde de água) chega inteiro a uma máquina com uma face de item e
     * outra de fluido ({@link TestDualMachine}, ligada nesta run pela {@code wirelessautomate.gameTests}).
     */
    @GameTest(template = "empty")
    public static void itemAndFluidPatternReachesTheMachine(GameTestHelper helper) {
        if (!enabled() || !TestMachines.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        BlockPos machine = FURNACES[0];
        TestDualMachine.reset(helper.absolutePos(machine));
        helper.setBlock(machine, TestDualMachine.BLOCK);
        long[] remaining = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    helper.assertTrue(dock.link(NORTH, helper.absolutePos(machine),
                            BuiltInRegistries.BLOCK.getKey(TestDualMachine.BLOCK), Direction.NORTH.ordinal()),
                            "não vinculou a máquina");
                    TestDualMachine.profile(dock.side(NORTH));
                })
                .thenWaitUntil(Ae2DockGameTests::room)
                .thenExecute(() -> {
                    helper.assertTrue(Ae2TestSupport.push(helper.getLevel(), helper.absolutePos(DOCK), Direction.NORTH,
                            Ae2TestSupport.itemAndFluidPattern(Items.IRON_INGOT, Items.RAW_IRON, 1, Fluids.WATER,
                                    1_000), remaining), "recusou: motivo " + dock.side(NORTH).lastRefusal());
                    TestDualMachine.Dual dual = TestDualMachine.at(helper.absolutePos(machine));
                    ItemStack slot = dual.items.getStackInSlot(0);
                    helper.assertTrue(slot.is(Items.RAW_IRON) && slot.getCount() == 1, "slot: " + slot);
                    helper.assertTrue(dual.tank.getFluid().is(Fluids.WATER) && dual.tank.getFluidAmount() == 1_000,
                            "tanque: " + dual.tank.getFluid());
                    helper.assertFalse(dock.side(NORTH).hasPending(), "ficou pendente");
                })
                .thenSucceed();
    }

    /** No Pattern Access Terminal, o lado se chama pelas máquinas dele; o lado vazio, pela própria Doca. */
    @GameTest(template = "empty")
    public static void machineInfoNamesTheSide(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> linkFurnaces(helper, dock, 3))
                .thenExecute(() -> {
                    BlockPos pos = helper.absolutePos(DOCK);
                    Component name = Ae2TestSupport.groupName(helper.getLevel(), pos, Direction.NORTH);
                    helper.assertTrue(name.getContents() instanceof TranslatableContents contents
                            && contents.getKey().equals("gui.wirelessautomate.pattern_dock.ae2_group")
                            && contents.getArgs()[0].equals(RouterBlock.machineName(Blocks.BLAST_FURNACE))
                            && Integer.valueOf(3).equals(contents.getArgs()[1]), "nome: " + name);
                    helper.assertTrue(Ae2TestSupport.groupIcon(helper.getLevel(), pos, Direction.NORTH)
                            == Items.BLAST_FURNACE, "ícone errado");
                    Component empty = Ae2TestSupport.groupName(helper.getLevel(), pos, Direction.SOUTH);
                    helper.assertTrue(empty.equals(ModBlocks.PATTERN_DOCK.get().getName()), "nome do lado vazio: " + empty);
                })
                .thenSucceed();
    }

    /**
     * "Tem provedor": o Pattern Provider encostado conta; a ME Interface (que expõe o armazenamento ME) e o baú não; um
     * lado sem vizinho do AE2 que recebeu uma oferta nos últimos 60 s conta.
     */
    @GameTest(template = "empty")
    public static void providerDetection(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        helper.setBlock(DOCK.north(), ae2("pattern_provider"));
        helper.setBlock(DOCK.south(), ae2("interface"));
        helper.setBlock(DOCK.east(), Blocks.CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    helper.assertTrue(DockAe2.hasProvider(dock, NORTH), "o Pattern Provider não conta");
                    helper.assertFalse(DockAe2.hasProvider(dock, Direction.SOUTH.ordinal()), "a ME Interface conta");
                    helper.assertFalse(DockAe2.hasProvider(dock, Direction.EAST.ordinal()), "o baú conta");
                    linkFurnaces(helper, dock, 1);
                    int west = Direction.WEST.ordinal();
                    BlockPos furnace = helper.absolutePos(FURNACES[0]).above();
                    helper.setBlock(FURNACES[0].above(), Blocks.BLAST_FURNACE);
                    helper.assertTrue(dock.link(west, furnace, BLAST_ID, Direction.NORTH.ordinal()), "não vinculou a oeste");
                    helper.assertFalse(DockAe2.hasProvider(dock, west), "a oeste sem vizinho conta");
                    Ae2TestSupport.acceptsPlans(helper.getLevel(), helper.absolutePos(DOCK), Direction.WEST);
                    helper.assertTrue(DockAe2.hasProvider(dock, west), "a oferta recente não conta");
                })
                .thenSucceed();
    }
}
