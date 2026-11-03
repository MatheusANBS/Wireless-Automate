package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.rangeUpgrade;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.FaceSide;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.NodeProbe;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.Verdict;
import io.github.matheusanbs.wirelessautomate.network.Verdict.Action;
import io.github.matheusanbs.wirelessautomate.network.Verdict.Reason;
import io.github.matheusanbs.wirelessautomate.network.VerdictFacts.Reach;
import io.github.matheusanbs.wirelessautomate.network.VerdictProbe;
import io.github.matheusanbs.wirelessautomate.network.VerdictRules;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import io.github.matheusanbs.wirelessautomate.packet.TabletVerdictPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * O veredito da aba Máquina do Tablet pelo caminho real: o {@link TabletMenu} do jogador com o nó e a máquina
 * escolhidos, a aba aberta e o {@code pollVerdict()} lendo as portas do motor. Num lote só dele; cada teste cria as
 * próprias redes.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TabletVerdictGameTests {
    private static final String BATCH = "wirelessautomate_verdict";
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 0);
    private static final BlockPos C = new BlockPos(4, 1, 0);

    private static ServerPlayer playerWithTablet(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(A).above(2)));
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        return player;
    }

    /** O Tablet sem ser o menu aberto do jogador: o servidor consumiria o veredito no {@code broadcastChanges}. */
    private static TabletMenu openTablet(ServerPlayer player, int containerId) {
        return new TabletMenu(containerId, player.getInventory(), player);
    }

    /** Uma máquina com um roteador em cima, colocado pelo jogador (ele o vê), sem modo nem rede. */
    private static RouterBlockEntity router(GameTestHelper helper, ServerPlayer player, BlockPos machine, Block block,
            RouterTier tier) {
        helper.setBlock(machine, block);
        helper.setBlock(machine.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, tier));
        RouterBlockEntity router = helper.getBlockEntity(machine.above());
        NodeIndex.placedBy(router, player.getUUID());
        return router;
    }

    private static NodeKey key(RouterBlockEntity router) {
        return NodeKey.of(router.getLevel(), router.getBlockPos());
    }

    private static UUID network(GameTestHelper helper, ServerPlayer player, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(player.getUUID(), name).id();
    }

    private static void loaded(GameTestHelper helper, RouterBlockEntity router) {
        helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad");
    }

    private static void fullChest(GameTestHelper helper, BlockPos machine) {
        Container chest = helper.getBlockEntity(machine);
        for (int i = 0; i < chest.getContainerSize(); i++) {
            chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
    }

    private static Verdict verdict(GameTestHelper helper, @Nullable TabletVerdictPayload payload) {
        helper.assertTrue(payload != null && payload.verdict().isPresent(), "o veredito não saiu");
        return payload.verdict().get();
    }

    private static void eq(GameTestHelper helper, @Nullable Object actual, @Nullable Object expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": esperado " + expected + ", veio " + actual);
    }

    /** A ordem dos lados do veredito é a do {@code Direction.get3DDataValue()}: o codec e o probe dependem dela. */
    @GameTest(template = "empty", batch = BATCH)
    public static void faceSideFollowsDirectionOrder(GameTestHelper helper) {
        for (int i = 0; i < 6; i++) {
            Direction direction = Direction.from3DDataValue(i);
            eq(helper, FaceSide.values()[i].name(), direction.name(), "lado " + i);
            eq(helper, FaceSide.valueOf(direction.name()).ordinal(), direction.get3DDataValue(), "ordinal " + i);
        }
        helper.succeed();
    }

    /** Uma face que extrai sem rede: o motivo é sem rede, na face de baixo, e a ação é escolher a rede. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void noNetworkVerdict(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        RouterBlockEntity router = router(helper, player, A, Blocks.FURNACE, RouterTier.BASIC);
        router.setMode(ResourceType.ITEM, Direction.DOWN, PortMode.EXTRACT);
        TabletMenu menu = openTablet(player, 120);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    helper.assertTrue(menu.chooseNode(key(router)), "não escolheu o nó");
                    TabletVerdictPayload payload = menu.pollVerdict();
                    Verdict verdict = verdict(helper, payload);
                    eq(helper, payload.containerId(), 120, "containerId");
                    eq(helper, payload.node(), Optional.of(key(router)), "nó");
                    eq(helper, payload.machine(), Optional.empty(), "a presa não leva posição");
                    eq(helper, verdict.reason(), Reason.NO_NETWORK, "motivo");
                    eq(helper, verdict.face(), FaceSide.DOWN, "face");
                    eq(helper, verdict.type(), ResourceType.ITEM, "tipo");
                    eq(helper, verdict.action(), Action.CHOOSE_NETWORK, "ação");
                })
                .thenSucceed();
    }

    /** Uma origem e dois destinos cheios na mesma rede: destinos cheios, com a conta e as posições deles. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 300)
    public static void fullDestinationsVerdict(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        UUID network = network(helper, player, "teste-veredito-cheios");
        RouterBlockEntity source = router(helper, player, A, Blocks.CHEST, RouterTier.BASIC);
        RouterBlockEntity first = router(helper, player, B, Blocks.CHEST, RouterTier.BASIC);
        RouterBlockEntity second = router(helper, player, C, Blocks.CHEST, RouterTier.BASIC);
        for (RouterBlockEntity router : new RouterBlockEntity[] {source, first, second}) {
            router.setNetworkId(ResourceType.ITEM, network);
        }
        source.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        first.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        second.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        ((Container) helper.getBlockEntity(A)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        fullChest(helper, B);
        fullChest(helper, C);
        TabletMenu menu = openTablet(player, 121);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(source)
                        && NetworkManager.get().contains(first) && NetworkManager.get().contains(second), "sem onLoad"))
                .thenWaitUntil(() -> {
                    long now = helper.getLevel().getServer().getTickCount();
                    helper.assertTrue(NodeProbe.fullDestinations(first, ResourceType.ITEM, now) == 1
                            && NodeProbe.fullDestinations(second, ResourceType.ITEM, now) == 1,
                            "destinos ainda não cheios");
                    // a origem já dorme esperando destino: o que o vigia vai ler
                    Verdict direct = VerdictRules.decide(VerdictProbe.read(source, null, Reach.OK, now));
                    helper.assertTrue(direct.reason() == Reason.DESTINATIONS_FULL, "ainda " + direct.reason());
                })
                .thenExecute(() -> {
                    helper.assertTrue(menu.chooseNode(key(source)), "não escolheu o nó");
                    Verdict verdict = verdict(helper, menu.pollVerdict());
                    eq(helper, verdict.reason(), Reason.DESTINATIONS_FULL, "motivo");
                    eq(helper, verdict.count(), 2, "destinos cheios");
                    eq(helper, verdict.positions().size(), 2, "posições");
                    helper.assertTrue(verdict.positions().contains(helper.absolutePos(B).asLong())
                            && verdict.positions().contains(helper.absolutePos(C).asLong()),
                            "as posições: " + verdict.positions());
                    eq(helper, verdict.action(), Action.SEE_DESTINATIONS, "ação");
                    eq(helper, verdict.network(), network, "rede");
                })
                .thenSucceed();
    }

    /** A face que o redstone desliga, com o sinal chegando: o motivo é o redstone. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void redstoneVerdict(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        UUID network = network(helper, player, "teste-veredito-redstone");
        RouterBlockEntity router = router(helper, player, A, Blocks.CHEST, RouterTier.BASIC);
        router.setNetworkId(ResourceType.ITEM, network);
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        router.setRedstone(ResourceType.ITEM, Direction.UP, RedstoneMode.LOW);
        TabletMenu menu = openTablet(player, 122);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> helper.setBlock(A.above().east(), Blocks.REDSTONE_BLOCK))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(menu.chooseNode(key(router)), "não escolheu o nó");
                    eq(helper, verdict(helper, menu.pollVerdict()).reason(), Reason.REDSTONE, "motivo");
                })
                .thenSucceed();
    }

    /** A rede da face num grupo pausado: o motivo é a pausa, com a rede certa. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void pausedGroupVerdict(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID network = network(helper, player, "teste-veredito-pausa");
        RouterBlockEntity router = router(helper, player, A, Blocks.CHEST, RouterTier.BASIC);
        router.setNetworkId(ResourceType.ITEM, network);
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        TabletMenu menu = openTablet(player, 123);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    WaGroup group = data.createGroup(player.getUUID(), "teste-veredito-grupo");
                    data.updateGroup(group.withNetwork(network).withPaused(true));
                    helper.assertTrue(menu.chooseNode(key(router)), "não escolheu o nó");
                    Verdict verdict = verdict(helper, menu.pollVerdict());
                    eq(helper, verdict.reason(), Reason.PAUSED, "motivo");
                    eq(helper, verdict.network(), network, "rede");
                    eq(helper, verdict.action(), Action.RESUME, "ação");
                })
                .thenSucceed();
    }

    /** O pacote só sai quando o veredito muda: duas leituras no mesmo tick, e a amostra seguinte, sem mudança, não repetem. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void sentOnlyWhenChanged(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        RouterBlockEntity router = router(helper, player, A, Blocks.FURNACE, RouterTier.BASIC);
        router.setMode(ResourceType.ITEM, Direction.DOWN, PortMode.EXTRACT);
        TabletMenu menu = openTablet(player, 124);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    helper.assertTrue(menu.pollVerdict() == null, "veredito sem nó escolhido");
                    helper.assertTrue(menu.chooseNode(key(router)), "não escolheu o nó");
                    helper.assertTrue(menu.pollVerdict() != null, "a primeira leitura não saiu");
                    helper.assertTrue(menu.pollVerdict() == null, "a segunda, no mesmo tick, saiu");
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(menu.pollVerdict() == null, "o mesmo veredito saiu de novo na amostra seguinte");
                    // mudar o que ele diz o faz sair na amostra seguinte
                    router.setNetworkId(ResourceType.ITEM, network(helper, player, "teste-veredito-muda"));
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    TabletVerdictPayload changed = menu.pollVerdict();
                    helper.assertTrue(changed != null && changed.verdict().get().reason() != Reason.NO_NETWORK,
                            "o veredito novo não saiu");
                })
                .thenSucceed();
    }

    /** Fora da aba Máquina nada roda; voltar a ela manda o veredito de novo. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void silentOutsideTheMachineTab(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        RouterBlockEntity router = router(helper, player, A, Blocks.FURNACE, RouterTier.BASIC);
        router.setMode(ResourceType.ITEM, Direction.DOWN, PortMode.EXTRACT);
        TabletMenu menu = openTablet(player, 125);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    helper.assertTrue(menu.chooseNode(key(router)), "não escolheu o nó");
                    helper.assertTrue(menu.pollVerdict() != null, "não saiu na aba Máquina");
                    helper.assertTrue(menu.setTab(TabletMenu.FLOWS_TAB), "a aba Fluxos recusada");
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(menu.pollVerdict() == null, "saiu na aba Fluxos");
                    helper.assertTrue(menu.setTab(0), "a aba Máquina recusada");
                    helper.assertTrue(menu.pollVerdict() != null, "não voltou a sair na aba Máquina");
                })
                .thenSucceed();
    }

    /** Uma máquina vinculada: o pacote leva a posição dela e o veredito é o dela, não o da presa. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void remoteMachineVerdict(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        RouterBlockEntity router = router(helper, player, A, Blocks.CHEST, RouterTier.ADVANCED);
        BlockPos remote = new BlockPos(4, 1, 0);
        helper.setBlock(remote, Blocks.FURNACE);
        TabletMenu menu = openTablet(player, 126);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    helper.assertTrue(router.linkMachine(helper.absolutePos(remote)), "não vinculou");
                    router.configureFace(EditTarget.group(router.remoteMachines().machine(0).group()),
                            ResourceType.ITEM, Direction.DOWN, PortMode.EXTRACT, 0, RedstoneMode.IGNORE);
                    helper.assertTrue(menu.chooseNode(key(router)), "não escolheu o nó");
                    helper.assertTrue(menu.selectTarget(EditTarget.machine(0), helper.absolutePos(remote), null),
                            "não escolheu a máquina");
                    TabletVerdictPayload payload = menu.pollVerdict();
                    Verdict verdict = verdict(helper, payload);
                    eq(helper, payload.machine(), Optional.of(helper.absolutePos(remote)), "máquina");
                    eq(helper, verdict.reason(), Reason.NO_NETWORK, "motivo");
                    eq(helper, verdict.face(), FaceSide.DOWN, "face");
                })
                .thenSucceed();
    }

    /**
     * Com o chunk da máquina vinculada descarregado, o veredito é descarregada e a máquina não é consultada. A máquina
     * é vinculada nos dados do roteador (sem o chunk, que um vínculo de verdade exigiria carregado) a 56 blocos, dentro do
     * raio do Ultimate, num chunk que ninguém carrega.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void unloadedRemoteMachineVerdict(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerWithTablet(helper);
        RouterBlockEntity router = router(helper, player, A, Blocks.CHEST, RouterTier.ULTIMATE);
        BlockPos near = helper.absolutePos(A);
        BlockPos far = new BlockPos(near.getX(), near.getY(), near.getZ() + 56);
        TabletMenu menu = openTablet(player, 127);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    helper.assertFalse(level.isLoaded(far), "o chunk distante está carregado");
                    router.setRangeUpgrade(rangeUpgrade());
                    helper.assertTrue(router.remoteMachines().link(far, BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE),
                            Direction.NORTH.ordinal(), null), "não vinculou");
                    helper.assertTrue(menu.chooseNode(key(router)), "não escolheu o nó");
                    helper.assertTrue(menu.selectTarget(EditTarget.machine(0), far, null), "não escolheu a máquina");
                    TabletVerdictPayload payload = menu.pollVerdict();
                    eq(helper, verdict(helper, payload).reason(), Reason.UNLOADED, "motivo");
                    eq(helper, payload.machine(), Optional.of(far), "máquina");
                    helper.assertFalse(level.isLoaded(far), "o veredito carregou o chunk");
                })
                .thenSucceed();
    }
}
