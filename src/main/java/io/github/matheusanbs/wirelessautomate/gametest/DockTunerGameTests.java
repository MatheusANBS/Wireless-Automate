package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.tuner.ConfiguratorTuner;
import io.github.matheusanbs.wirelessautomate.item.tuner.TunerPending;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * O Configurador numa máquina de um lado da Doca de Padrões ({@code dock/DockTuner}): os dois cliques refazem o perfil
 * do lado inteiro, que vale para todas as máquinas dele pela frente de cada uma; numa máquina de um roteador e de uma
 * Doca, vale o roteador.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockTunerGameTests {
    private static final String KEY = "item.wirelessautomate.configurator.tuner.";
    private static final int NORTH = Direction.NORTH.ordinal();

    private DockTunerGameTests() {
    }

    private static PatternDockBlockEntity dock(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, ModBlocks.PATTERN_DOCK.get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.BASIC));
        return helper.getBlockEntity(relative);
    }

    /** Vincula a máquina em {@code relative} ao lado {@code side}, sem o Vinculador (o perfil fica como está). */
    private static void link(GameTestHelper helper, PatternDockBlockEntity dock, int side, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(pos);
        helper.assertTrue(dock.link(side, pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                RemoteMachines.frontOf(state)), "não vinculou " + relative);
    }

    private static String key(@Nullable Component message) {
        return message != null && message.getContents() instanceof TranslatableContents contents ? contents.getKey()
                : String.valueOf(message);
    }

    /** O modo do perfil de itens na face absoluta {@code absolute} de uma máquina de frente {@code front}. */
    private static PortMode mode(DockSide side, Direction front, Direction absolute) {
        return side.profile().face(ResourceType.ITEM,
                RemoteSide.fromAbsolute(front.ordinal(), absolute.ordinal())).mode();
    }

    /**
     * Duas fornalhas num lado, de frentes diferentes, com o perfil errado (Baixo Insere): os dois cliques do
     * Configurador na segunda refazem o perfil (Cima e Trás Insere, Baixo Extrai), e as duas usam o perfil novo, cada
     * uma pela sua frente.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void twoClicksRetuneWholeSide(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, new BlockPos(1, 1, 1));
        BlockPos first = new BlockPos(0, 1, 1);
        BlockPos second = new BlockPos(2, 1, 1);
        helper.setBlock(first, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
        helper.setBlock(second, Blocks.FURNACE.defaultBlockState()
                .setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH));
        ServerPlayer player = TestPlayers.inLevel(helper);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    link(helper, dock, NORTH, first);
                    link(helper, dock, NORTH, second);
                    dock.side(NORTH).profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.INSERT);
                    helper.assertValueEqual(key(ConfiguratorTuner.click(player, helper.absolutePos(second), false)),
                            KEY + "proposal", "aviso do primeiro clique");
                    helper.assertValueEqual(mode(dock.side(NORTH), Direction.SOUTH, Direction.DOWN), PortMode.INSERT,
                            "o primeiro clique mudou o perfil");
                })
                .thenIdle(TunerPending.CONFIRM_DELAY)
                .thenExecute(() -> {
                    Component message = ConfiguratorTuner.click(player, helper.absolutePos(second), false);
                    helper.assertValueEqual(key(message), KEY + "applied_dock", "aviso do segundo clique");
                    helper.assertTrue(TunerPending.get(player.getUUID()) == null, "a proposta ficou depois de aplicar");
                    DockSide side = dock.side(NORTH);
                    // a segunda (frente Sul): Trás é Norte
                    helper.assertValueEqual(mode(side, Direction.SOUTH, Direction.UP), PortMode.INSERT, "Cima");
                    helper.assertValueEqual(mode(side, Direction.SOUTH, Direction.NORTH), PortMode.INSERT, "Trás");
                    helper.assertValueEqual(mode(side, Direction.SOUTH, Direction.DOWN), PortMode.EXTRACT, "Baixo");
                    helper.assertValueEqual(mode(side, Direction.SOUTH, Direction.SOUTH), PortMode.NONE, "Frente");
                    // a primeira (frente Leste) usa o mesmo perfil: Trás é Oeste
                    helper.assertValueEqual(mode(side, Direction.EAST, Direction.WEST), PortMode.INSERT,
                            "Trás da primeira");
                    helper.assertValueEqual(mode(side, Direction.EAST, Direction.DOWN), PortMode.EXTRACT,
                            "Baixo da primeira");
                    helper.assertValueEqual(mode(side, Direction.EAST, Direction.EAST), PortMode.NONE,
                            "Frente da primeira");
                })
                .thenSucceed();
    }

    /**
     * Uma fornalha de um roteador e de um lado da Doca: os dois cliques aplicam na exceção dela no roteador, e o perfil
     * do lado não muda.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void routerWinsOverDock(GameTestHelper helper) {
        RouterBlockEntity router = RangeTestSupport.routerWithMachines(helper);
        PatternDockBlockEntity dock = dock(helper, new BlockPos(2, 1, 2));
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        BlockPos furnace = RangeTestSupport.FURNACE_A;
        helper.startSequence()
                .thenWaitUntil(() -> RangeTestSupport.waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(RangeTestSupport.rangeUpgrade());
                    RangeTestSupport.link(helper, router, furnace);
                    link(helper, dock, NORTH, furnace);
                    ConfiguratorTuner.click(player, helper.absolutePos(furnace), false);
                })
                .thenIdle(TunerPending.CONFIRM_DELAY)
                .thenExecute(() -> {
                    helper.assertValueEqual(key(ConfiguratorTuner.click(player, helper.absolutePos(furnace), false)),
                            KEY + "applied", "aviso do segundo clique");
                    EditTarget target = EditTarget.machine(router.remoteMachines().index(helper.absolutePos(furnace)));
                    helper.assertValueEqual(router.face(target, ResourceType.ITEM,
                            Direction.values()[RemoteSide.TOP.canonical()]).mode(), PortMode.INSERT,
                            "o roteador não recebeu");
                    helper.assertTrue(dock.side(NORTH).profile().isDefault(), "o perfil do lado mudou");
                })
                .thenSucceed();
    }
}
