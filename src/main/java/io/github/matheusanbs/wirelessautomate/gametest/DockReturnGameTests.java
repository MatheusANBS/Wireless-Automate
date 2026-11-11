package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockMachine;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A volta do resultado ({@code DockReturn}): o lingote da fornalha volta ao baú encostado no lado da Doca (também o
 * que já estava na saída antes da carga da Doca), o combustível fica na fornalha, o baú cheio segura a volta (o lado marca "provedor cheio") e ela retoma poucos ticks
 * depois que ele esvazia (o aviso do vizinho), a Doca sem pedido não é visitada e a recarga da config com o raio menor tira a máquina de fora do índice.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockReturnGameTests {
    private static final BlockPos DOCK = new BlockPos(1, 1, 1);
    /** O baú no lugar do provedor, encostado no lado Norte da Doca. */
    private static final BlockPos CHEST = DOCK.north();
    private static final BlockPos FURNACE = new BlockPos(3, 1, 1);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final ResourceLocation FURNACE_ID = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);

    private DockReturnGameTests() {
    }

    /** A Doca Básica, o baú ao norte dela e uma fornalha virada para o norte. */
    private static PatternDockBlockEntity scene(GameTestHelper helper) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.BASIC));
        helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
        helper.setBlock(FURNACE, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(DOCK);
    }

    /** Vincula a fornalha ao lado Norte com o perfil do Afinador: Cima e Trás Insere, Baixo Extrai. */
    private static DockMachine link(GameTestHelper helper, PatternDockBlockEntity dock) {
        BlockPos pos = helper.absolutePos(FURNACE);
        helper.assertTrue(dock.link(NORTH, pos, FURNACE_ID, Direction.NORTH.ordinal()), "não vinculou");
        DockSide side = dock.side(NORTH);
        side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BACK).setMode(PortMode.INSERT);
        side.profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
        return dock.machineAt(pos);
    }

    /** A Doca entrou no laço do motor (o {@code onLoad} roda no tick seguinte ao da colocação). */
    private static void registered(PatternDockBlockEntity dock) {
        if (!NetworkManager.get().hasVisitor(dock.returns())) {
            throw new GameTestAssertException("a volta da Doca não está no motor");
        }
    }

    /** Passo de espera: o orçamento tem folga para uma oferta (o próximo passo roda no mesmo tick). */
    private static void room() {
        if (!NetworkManager.get().budget().outsideRoom()) {
            throw new GameTestAssertException("sem orçamento neste tick");
        }
    }

    private static AbstractFurnaceBlockEntity furnace(GameTestHelper helper) {
        return helper.getBlockEntity(FURNACE);
    }

    private static int chestCount(GameTestHelper helper, Item item) {
        ChestBlockEntity chest = helper.getBlockEntity(CHEST);
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Põe pilhas na fornalha e avisa a mudança, como ela faz ao cozinhar (o aviso chega à Doca pelo índice). */
    private static void fillFurnace(GameTestHelper helper, int coal, int ingots) {
        AbstractFurnaceBlockEntity entity = furnace(helper);
        entity.setItem(1, new ItemStack(Items.COAL, coal));
        entity.setItem(2, new ItemStack(Items.IRON_INGOT, ingots));
        entity.setChanged();
    }

    /** Um pedido de minério e carvão: a fornalha cozinha (200 ticks) e o lingote volta ao baú pela face de baixo. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void ingotReturnsToChest(GameTestHelper helper) {
        PatternDockBlockEntity dock = scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> link(helper, dock))
                .thenWaitUntil(DockReturnGameTests::room)
                .thenExecute(() -> helper.assertTrue(DockDelivery.offer(dock, NORTH,
                        List.of(new ItemStack(Items.IRON_ORE), new ItemStack(Items.COAL)), List.of(), false)
                        == DockDelivery.Result.ACCEPTED, "recusou o pedido"))
                .thenWaitUntil(() -> helper.assertTrue(chestCount(helper, Items.IRON_INGOT) == 1, "o lingote não voltou"))
                .thenExecute(() -> {
                    DockSide side = dock.side(NORTH);
                    helper.assertTrue(side.returned() == 1, "devolvidos: " + side.returned());
                    helper.assertTrue(furnace(helper).getItem(2).isEmpty(), "o lingote ficou na fornalha");
                    helper.assertTrue(dock.machineAt(helper.absolutePos(FURNACE)).lastActive() > 0, "sem atividade");
                })
                .thenSucceed();
    }

    /**
     * O lingote que já estava na saída de uma fornalha parada (que não avisa a Doca) volta ao baú depois da carga da
     * Doca: o {@code onLoad} põe as máquinas ativas na fila da volta. A descarga e a carga do chunk são simuladas no
     * block entity. Num lote só dele: um teste em paralelo que simula a carga de um chunk ({@code RangeWakeGameTests})
     * avisa as máquinas do chunk inteiro, e a fornalha acordaria antes da hora.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "wirelessautomate_dock_load")
    public static void ingotReturnsAfterDockLoad(GameTestHelper helper) {
        PatternDockBlockEntity dock = scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    link(helper, dock);
                    // o setItem da saída não chama o setChanged: a fornalha fica parada e calada
                    furnace(helper).setItem(2, new ItemStack(Items.IRON_INGOT, 2));
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(chestCount(helper, Items.IRON_INGOT) == 0, "voltou sem a carga");
                    dock.onChunkUnloaded();
                    dock.onLoad();
                })
                .thenWaitUntil(() -> helper.assertTrue(chestCount(helper, Items.IRON_INGOT) == 2,
                        "o lingote não voltou depois da carga"))
                .thenExecute(() -> helper.assertTrue(furnace(helper).getItem(2).isEmpty(), "sobrou na fornalha"))
                .thenSucceed();
    }

    /** A face de baixo da fornalha dá só o produto: o carvão do combustível fica. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fuelStaysInFurnace(GameTestHelper helper) {
        PatternDockBlockEntity dock = scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> link(helper, dock))
                .thenExecute(() -> fillFurnace(helper, 10, 3))
                .thenWaitUntil(() -> helper.assertTrue(chestCount(helper, Items.IRON_INGOT) == 3, "os lingotes não voltaram"))
                .thenIdle(5)
                .thenExecute(() -> {
                    ItemStack fuel = furnace(helper).getItem(1);
                    helper.assertTrue(fuel.is(Items.COAL) && fuel.getCount() == 10, "combustível: " + fuel);
                    helper.assertTrue(chestCount(helper, Items.COAL) == 0, "o carvão foi ao baú");
                    helper.assertTrue(dock.returns().awakeCount() == 0, "a fornalha ficou acordada");
                })
                .thenSucceed();
    }

    /**
     * O baú cheio segura a volta (o lado marca o provedor cheio, nada sai da fornalha) e ela retoma quando esvazia: o
     * {@code setChanged} do baú acorda o lado em poucos ticks, bem antes do teto de sono de 100.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void fullChestHoldsThenResumes(GameTestHelper helper) {
        PatternDockBlockEntity dock = scene(helper);
        long[] emptied = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    ChestBlockEntity chest = helper.getBlockEntity(CHEST);
                    for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                        chest.setItem(slot, new ItemStack(Items.STONE, 64));
                    }
                    link(helper, dock);
                    fillFurnace(helper, 0, 5);
                })
                .thenWaitUntil(() -> helper.assertTrue(dock.side(NORTH).providerFull(), "o lado não marcou provedor cheio"))
                .thenExecute(() -> {
                    ItemStack out = furnace(helper).getItem(2);
                    helper.assertTrue(out.is(Items.IRON_INGOT) && out.getCount() == 5, "saiu da fornalha: " + out);
                })
                // Espera alguns ticks para o lado já estar dormindo pelo teto quando o baú esvazia.
                .thenIdle(3)
                .thenExecute(() -> {
                    ChestBlockEntity chest = helper.getBlockEntity(CHEST);
                    chest.removeItemNoUpdate(0);
                    chest.setChanged();
                    emptied[0] = helper.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> helper.assertTrue(chestCount(helper, Items.IRON_INGOT) == 5, "não retomou"))
                .thenExecute(() -> {
                    long ticks = helper.getLevel().getGameTime() - emptied[0];
                    helper.assertTrue(ticks <= 20, "retomou só depois de " + ticks + " ticks");
                    helper.assertFalse(dock.side(NORTH).providerFull(), "o lado continua cheio");
                    helper.assertTrue(furnace(helper).getItem(2).isEmpty(), "sobrou na fornalha");
                })
                .thenSucceed();
    }

    /** Sem pedido e sem aviso, a volta não tem máquina acordada e o motor não a visita. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void idleDockNotVisited(GameTestHelper helper) {
        PatternDockBlockEntity dock = scene(helper);
        long[] visits = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> link(helper, dock))
                .thenIdle(5)
                .thenExecute(() -> visits[0] = dock.returns().visits())
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(dock.returns().wakeAt() == Long.MAX_VALUE, "acordada: " + dock.returns().wakeAt());
                    helper.assertTrue(dock.returns().visits() == visits[0],
                            "visitada parada: " + visits[0] + " → " + dock.returns().visits());
                })
                .thenSucceed();
    }

    /** A recarga da config com o raio menor (pelo motor) tira a fornalha do índice e a deixa inativa; e volta. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_config_dock")
    public static void configReloadRefreshesReach(GameTestHelper helper) {
        PatternDockBlockEntity dock = scene(helper);
        // a Doca Básica lê o raio do Avançado
        var radius = Config.TIERS.get(RouterTier.ADVANCED).machineRadius();
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> link(helper, dock))
                .thenExecute(() -> {
                    BlockPos pos = helper.absolutePos(FURNACE);
                    DockMachine machine = dock.machineAt(pos);
                    helper.assertTrue(machine.active() && RemoteIndex.get().isIndexed(dock, pos), "começou fora");
                    int before = radius.get();
                    try {
                        radius.set(1);
                        NetworkManager.configChanged();
                        NetworkManager.get().applyVisitorsConfig();
                        helper.assertFalse(machine.active(), "a fornalha continua ativa com o raio 1");
                        helper.assertFalse(RemoteIndex.get().isIndexed(dock, pos), "a fornalha continua no índice");
                    } finally {
                        radius.set(before);
                        NetworkManager.configChanged();
                        NetworkManager.get().applyVisitorsConfig();
                    }
                    helper.assertTrue(machine.active() && RemoteIndex.get().isIndexed(dock, pos), "não voltou");
                })
                .thenSucceed();
    }
}
