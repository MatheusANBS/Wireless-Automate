package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.FlowSampler;
import io.github.matheusanbs.wirelessautomate.network.FlowStats;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvFeed;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvManager;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O canal de dados das Telas de Sinal: só quem está perto recebe, só quando os números mudam, quem chega recebe os
 * atuais, rede sem acesso ou que sumiu vira {@code NaN} e uma Tela sem ligação não lê nada. Os testes montam o
 * relatório dos Fluxos à mão e chamam o mesmo caminho da visita, numa Tela fora do mundo (o tick real não a vê); o
 * último usa o tick de verdade, com fluxo de verdade, no lote dos Fluxos.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvFeedGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);
    private static final BlockPos CHEST_FROM = new BlockPos(0, 1, 3);
    private static final BlockPos CHEST_TO = new BlockPos(2, 1, 3);
    private static final String FLOWS = "wirelessautomate_flows";

    private TvFeedGameTests() {
    }

    private static BlockState screen() {
        return ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING, Direction.NORTH);
    }

    private static TvPiece value(int x, TvBinding binding) {
        return new TvPiece(TvPieceType.VALUE, x, 0, 10, 10, 0, "", Optional.of(binding), TvFormat.DEFAULT,
                TvStyle.DEFAULT);
    }

    /** Uma Tela só na memória, com o dono e uma peça Valor por ligação. */
    private static TvBlockEntity tv(GameTestHelper helper, UUID owner, TvBinding... bindings) {
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(A), screen());
        tv.setOwner(owner);
        TvPiece[] pieces = new TvPiece[bindings.length];
        for (int i = 0; i < bindings.length; i++) {
            pieces[i] = value(i * 12, bindings[i]);
        }
        tv.setLayout(TvLayout.of(List.of(pieces)));
        return tv;
    }

    private static UUID network(GameTestHelper helper, UUID owner, boolean isPublic) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork created = data.create(owner, "teste-tela");
        if (isPublic) {
            data.update(created.withPublic(true));
        }
        return created.id();
    }

    private static FlowSampler.Report report(long at, FlowStats... flows) {
        return new FlowSampler.Report(at, List.of(flows), FlowSampler.Report.EMPTY.withoutNetwork(), List.of());
    }

    private static FlowStats energy(UUID network, long produced, int seconds, FlowStats.States states) {
        return new FlowStats(network, ResourceType.ENERGY, produced, 0, 0, 0, seconds, states);
    }

    private static ServerPlayer nearPlayer(GameTestHelper helper) {
        TvManager.dropSendsForTests();
        ServerPlayer player = TestPlayers.menuOpener(helper);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(A)).add(0, 0, 10));
        return player;
    }

    private static long packets(TvBlockEntity tv) {
        return tv.feed() == null ? 0 : tv.feed().packets();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mandaSoParaQuemEstaPerto(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID net = network(helper, owner, false);
        TvBlockEntity tv = tv(helper, owner, new TvBinding(net, ResourceType.ENERGY, TvMetric.IN));
        ServerPlayer near = nearPlayer(helper);
        ServerPlayer far = nearPlayer(helper);
        far.moveTo(Vec3.atCenterOf(helper.absolutePos(A)).add(0, 0, 100));
        FlowSampler.Report report = report(100, energy(net, 2_000, 5, FlowStats.States.NONE));

        TvManager.sampleForTests(tv, report, List.of(near));
        helper.assertValueEqual(packets(tv), 1L, "pacotes para o jogador perto");
        TvManager.sampleForTests(tv, report, List.of(near));
        helper.assertValueEqual(packets(tv), 1L, "o mesmo relatório não manda de novo");
        helper.assertTrue(far.distanceToSqr(Vec3.atCenterOf(helper.absolutePos(A))) > 90 * 90, "o outro está longe");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void valorMudouMandaDeNovo(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID net = network(helper, owner, false);
        TvBlockEntity tv = tv(helper, owner, new TvBinding(net, ResourceType.ENERGY, TvMetric.IN));
        List<ServerPlayer> near = List.of(nearPlayer(helper));

        TvManager.sampleForTests(tv, report(100, energy(net, 2_000, 5, FlowStats.States.NONE)), near);
        helper.assertValueEqual(tv.feed().values()[0], 400.0, "primeiro valor");
        TvManager.sampleForTests(tv, report(120, energy(net, 4_000, 5, FlowStats.States.NONE)), near);
        helper.assertValueEqual(packets(tv), 2L, "outro número manda de novo");
        helper.assertValueEqual(tv.feed().values()[0], 800.0, "segundo valor");
        TvManager.sampleForTests(tv, report(140, energy(net, 4_000, 5, FlowStats.States.NONE)), near);
        helper.assertValueEqual(packets(tv), 2L, "amostra nova com os mesmos números não manda");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void quemChegaRecebeOsAtuais(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID net = network(helper, owner, false);
        TvBlockEntity tv = tv(helper, owner, new TvBinding(net, ResourceType.ENERGY, TvMetric.IN));
        ServerPlayer first = nearPlayer(helper);
        ServerPlayer second = nearPlayer(helper);
        FlowSampler.Report report = report(100, energy(net, 2_000, 5, FlowStats.States.NONE));

        TvManager.sampleForTests(tv, report, List.of(first));
        helper.assertValueEqual(packets(tv), 1L, "o primeiro");
        TvManager.sampleForTests(tv, report, List.of(first, second));
        helper.assertValueEqual(packets(tv), 2L, "quem chegou recebe, quem já tinha não");
        TvManager.sampleForTests(tv, report, List.of(first, second));
        helper.assertValueEqual(packets(tv), 2L, "os dois já têm");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void quemSaiERetornaRecebeDeNovo(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID net = network(helper, owner, false);
        TvBlockEntity tv = tv(helper, owner, new TvBinding(net, ResourceType.ENERGY, TvMetric.IN));
        ServerPlayer visitor = nearPlayer(helper);
        ServerPlayer stays = nearPlayer(helper);
        FlowSampler.Report report = report(100, energy(net, 2_000, 5, FlowStats.States.NONE));

        TvManager.sampleForTests(tv, report, List.of(visitor, stays));
        TvManager.sampleForTests(tv, report, List.of(stays));
        TvManager.sampleForTests(tv, report, List.of(visitor, stays));
        helper.assertValueEqual(packets(tv), 3L, "quem saiu do alcance e voltou recebe de novo");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void semAcessoViraNaN(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        UUID privateNet = network(helper, stranger, false);
        UUID publicNet = network(helper, stranger, true);
        TvBlockEntity tv = tv(helper, owner,
                new TvBinding(privateNet, ResourceType.ENERGY, TvMetric.IN),
                new TvBinding(publicNet, ResourceType.ENERGY, TvMetric.IN));
        FlowSampler.Report report = report(100, energy(privateNet, 2_000, 5, FlowStats.States.NONE),
                energy(publicNet, 2_000, 5, FlowStats.States.NONE));

        TvManager.sampleForTests(tv, report, List.of(nearPlayer(helper)));
        helper.assertTrue(Double.isNaN(tv.feed().values()[0]), "rede privada de outro vira NaN");
        helper.assertValueEqual(tv.feed().values()[1], 400.0, "rede pública de outro mostra o número");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void semDonoViraNaN(GameTestHelper helper) {
        UUID net = network(helper, UUID.randomUUID(), true);
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(A), screen());
        tv.setLayout(TvLayout.of(List.of(value(0, new TvBinding(net, ResourceType.ENERGY, TvMetric.IN)))));

        TvManager.sampleForTests(tv, report(100, energy(net, 2_000, 5, FlowStats.States.NONE)),
                List.of(nearPlayer(helper)));
        helper.assertTrue(Double.isNaN(tv.feed().values()[0]), "sem dono vira NaN");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void redeQueSumiuViraNaN(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        TvBlockEntity tv = tv(helper, owner, new TvBinding(UUID.randomUUID(), ResourceType.ENERGY, TvMetric.IN));

        TvManager.sampleForTests(tv, report(100), List.of(nearPlayer(helper)));
        helper.assertTrue(Double.isNaN(tv.feed().values()[0]), "rede que não existe vira NaN");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void semBindingNaoLeNada(GameTestHelper helper) {
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(A), screen());
        tv.setOwner(UUID.randomUUID());
        tv.setLayout(TvLayout.of(List.of(new TvPiece(TvPieceType.TEXT, 0, 0, 10, 10, 0, "t", Optional.empty(),
                TvFormat.DEFAULT, TvStyle.DEFAULT))));
        long before = TvManager.packetsForTests();

        TvManager.sampleForTests(tv, report(100), List.of(nearPlayer(helper)));
        helper.assertTrue(tv.feed() == null, "nenhum TvFeed criado");
        helper.assertValueEqual(packets(tv), 0L, "nenhum pacote");
        helper.assertTrue(TvManager.packetsForTests() >= before, "o contador global só cresce");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void taxasELeiturasDoFlowStats(GameTestHelper helper) {
        UUID net = UUID.randomUUID();
        FlowStats.States states = new FlowStats.States(2, 3, 4, 5, 6, 7);
        FlowStats stats = energy(net, 2_000, 5, states);
        helper.assertValueEqual(TvMetric.IN.read(stats), 400.0, "IN");
        helper.assertValueEqual(TvMetric.MOVING.read(stats), 7.0, "MOVING soma origens e destinos movendo");
        helper.assertValueEqual(TvMetric.EMPTY.read(stats), 3.0, "EMPTY");
        helper.assertValueEqual(TvMetric.WAITING.read(stats), 4.0, "WAITING");
        helper.assertValueEqual(TvMetric.FULL.read(stats), 6.0, "FULL");
        helper.assertValueEqual(TvMetric.REDSTONE.read(stats), 7.0, "REDSTONE");
        helper.assertValueEqual(TvMetric.IN.read(null), 0.0, "sem fluxos lê 0");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ninguemPertoNaoMontaOMapa(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID net = network(helper, owner, false);
        TvBlockEntity tv = tv(helper, owner, new TvBinding(net, ResourceType.ENERGY, TvMetric.IN));
        FlowSampler.Report report = report(100, energy(net, 2_000, 5, FlowStats.States.NONE));

        int builds = TvManager.mapBuildsForTests();
        TvManager.sampleForTests(tv, report, List.of());
        helper.assertValueEqual(TvManager.mapBuildsForTests(), builds, "o mapa não foi montado");
        helper.assertTrue(tv.feed() == null, "nada guardado");
        TvManager.sampleForTests(tv, report, List.of(nearPlayer(helper)));
        helper.assertValueEqual(TvManager.mapBuildsForTests(), builds + 1, "com alguém perto o mapa é montado");
        TvManager.sampleForTests(tv, report, List.of(nearPlayer(helper)));
        helper.assertValueEqual(TvManager.mapBuildsForTests(), builds + 1, "o mesmo relatório reusa o mapa");
        helper.succeed();
    }

    /** O tick real, com um fluxo real: a Tela ligada a IN de itens recebe um número maior que zero. */
    @GameTest(template = "empty", batch = FLOWS, timeoutTicks = 300)
    public static void telaRecebeOFluxoReal(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID net = network(helper, owner, false);
        placeChestWithRouter(helper, CHEST_FROM, net, PortMode.NONE);
        RouterBlockEntity source = helper.getBlockEntity(CHEST_FROM.above());
        placeChestWithRouter(helper, CHEST_TO, net, PortMode.INSERT);
        RouterBlockEntity target = helper.getBlockEntity(CHEST_TO.above());
        ((ChestBlockEntity) helper.getBlockEntity(CHEST_FROM)).setItem(0, new ItemStack(Items.DIAMOND, 16));
        helper.setBlock(A.relative(Direction.NORTH.getOpposite()), Blocks.STONE);
        helper.setBlock(A, screen());
        ServerPlayer player = nearPlayer(helper);
        long start = helper.getLevel().getServer().getTickCount();

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getBlockEntity(A) instanceof TvBlockEntity, "sem block entity");
                    helper.assertTrue(NetworkManager.get().contains(source) && NetworkManager.get().contains(target),
                            "roteadores não registrados");
                })
                .thenExecute(() -> {
                    TvBlockEntity tv = helper.getBlockEntity(A);
                    tv.setOwner(owner);
                    tv.setLayout(TvLayout.of(List.of(value(0, new TvBinding(net, ResourceType.ITEM, TvMetric.IN)))));
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        NetworkManager.get().flows().report().sampledAt() >= start + FlowSampler.SAMPLE_TICKS,
                        "nenhuma volta inteira do amostrador"))
                .thenExecute(() -> source.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT))
                .thenWaitUntil(() -> {
                    TvBlockEntity tv = helper.getBlockEntity(A);
                    TvFeed feed = tv.feed();
                    helper.assertTrue(feed != null && feed.values().length == 1 && feed.values()[0] > 0,
                            "a Tela ainda não recebeu o fluxo");
                    helper.assertTrue(feed.packets() >= 1, "nenhum pacote para o jogador perto");
                    helper.assertTrue(player.isAlive(), "jogador");
                })
                .thenSucceed();
    }

    private static void placeChestWithRouter(GameTestHelper helper, BlockPos machine, UUID network, PortMode mode) {
        helper.setBlock(machine, Blocks.CHEST);
        helper.setBlock(machine.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING,
                Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(machine.above());
        router.setNetworkId(ResourceType.ITEM, network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
    }
}
