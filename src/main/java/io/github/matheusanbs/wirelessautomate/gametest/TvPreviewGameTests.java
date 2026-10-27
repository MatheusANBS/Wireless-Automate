package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.FlowSampler;
import io.github.matheusanbs.wirelessautomate.network.FlowStats;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TvPreviewPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvPreviewValuesPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvPreview;
import io.github.matheusanbs.wirelessautomate.tv.TvRule;
import io.github.matheusanbs.wirelessautomate.tv.TvRuleOp;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvUnit;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A prévia dos números do rascunho no editor da Tela de Sinal: o acesso de quem edita (rede privada alheia vira
 * {@code NaN}), o teto de ligações, o limite próprio de pedidos, parar ao fechar o menu, a ida e a volta dos dois
 * payloads, e que toda mudança de uma peça dá um layout novo (a miniatura redesenha por referência).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvPreviewGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);

    private TvPreviewGameTests() {
    }

    private static void withScreen(GameTestHelper helper, UUID owner, Consumer<TvBlockEntity> body) {
        helper.setBlock(A.south(), Blocks.STONE);
        helper.setBlock(A, ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING, Direction.NORTH));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(A) instanceof TvBlockEntity, "sem block entity"))
                .thenExecute(() -> {
                    TvBlockEntity tv = (TvBlockEntity) helper.getBlockEntity(A);
                    tv.setOwner(owner);
                    body.accept(tv);
                })
                .thenSucceed();
    }

    private static TvMenu opened(GameTestHelper helper, TestPlayers.MenuPlayer player) {
        BlockPos pos = helper.absolutePos(A);
        player.setPos(helper.absoluteVec(Vec3.atCenterOf(A)));
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        helper.assertTrue(player.containerMenu instanceof TvMenu, "o menu não abriu");
        return (TvMenu) player.containerMenu;
    }

    private static UUID network(GameTestHelper helper, UUID owner, boolean isPublic) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork created = data.create(owner, "teste-previa");
        if (isPublic) {
            data.update(created.withPublic(true));
        }
        return created.id();
    }

    private static FlowSampler.Report report(long at, FlowStats... flows) {
        return new FlowSampler.Report(at, List.of(flows), FlowSampler.Report.EMPTY.withoutNetwork(), List.of());
    }

    private static FlowStats energy(UUID network, long produced) {
        return new FlowStats(network, ResourceType.ENERGY, produced, 0, 0, 0, 5, FlowStats.States.NONE);
    }

    private static TvBinding binding(UUID network) {
        return new TvBinding(network, ResourceType.ENERGY, TvMetric.IN);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void acessoDeQuemEditaDecideOsNumeros(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        UUID stranger = UUID.randomUUID();
        UUID own = network(helper, player.getUUID(), false);
        UUID foreignPrivate = network(helper, stranger, false);
        UUID foreignPublic = network(helper, stranger, true);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            TvPreview.handle(player, new TvPreviewPayload(menu.containerId, 1,
                    List.of(binding(own), binding(foreignPrivate), binding(foreignPublic))));
            TvPreview.tickForTests(helper.getLevel().getServer(), report(100, energy(own, 2_000),
                    energy(foreignPrivate, 2_000), energy(foreignPublic, 2_000)));
            double[] sent = TvPreview.sentForTests(player.getUUID());
            helper.assertTrue(sent != null && sent.length == 3, "a prévia não respondeu as três ligações");
            helper.assertValueEqual(sent[0], 400.0, "a rede do próprio jogador");
            helper.assertTrue(Double.isNaN(sent[1]), "rede privada de outro vira NaN");
            helper.assertValueEqual(sent[2], 400.0, "rede pública de outro mostra o número");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pedidoDeOutroMenuEListaVaziaNaoGuardamEstado(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        UUID own = network(helper, player.getUUID(), false);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            TvPreview.handle(player, new TvPreviewPayload(menu.containerId + 1, 1, List.of(binding(own))));
            helper.assertFalse(TvPreview.activeForTests(player.getUUID()), "pedido de um menu que não é o dele");
            TvPreview.handle(player, new TvPreviewPayload(menu.containerId, 1, List.of()));
            helper.assertFalse(TvPreview.activeForTests(player.getUUID()), "a lista vazia não guarda estado");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tetoDeLigacoes(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            List<TvBinding> many = new ArrayList<>();
            for (int i = 0; i < TvPreviewPayload.MAX_BINDINGS; i++) {
                many.add(binding(UUID.randomUUID()));
            }
            TvPreview.handle(player, new TvPreviewPayload(menu.containerId, 1, many));
            TvPreview.tickForTests(helper.getLevel().getServer(), report(100));
            double[] sent = TvPreview.sentForTests(player.getUUID());
            helper.assertTrue(sent != null, "sem resposta");
            helper.assertValueEqual(sent.length, Math.min(Config.tvMaxBindings(), TvPreviewPayload.MAX_BINDINGS),
                    "o servidor corta no teto de ligações");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void limiteProprioDePedidos(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        UUID own = network(helper, player.getUUID(), false);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            for (int i = 0; i < 10; i++) {
                TvPreview.handle(player, new TvPreviewPayload(menu.containerId, i + 1, List.of(binding(own))));
            }
            TvPreview.handle(player, new TvPreviewPayload(menu.containerId, 11, List.of()));
            helper.assertTrue(TvPreview.activeForTests(player.getUUID()),
                    "o 11º pedido no mesmo segundo devia ser descartado (a lista vazia pararia a prévia)");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void paraAoFecharOMenu(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        UUID own = network(helper, player.getUUID(), false);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            TvPreview.handle(player, new TvPreviewPayload(menu.containerId, 1, List.of(binding(own))));
            helper.assertTrue(TvPreview.activeForTests(player.getUUID()), "a prévia devia estar ativa");
            player.closeContainer();
            TvPreview.tickForTests(helper.getLevel().getServer(), report(100, energy(own, 2_000)));
            helper.assertFalse(TvPreview.activeForTests(player.getUUID()), "fechar o menu devia parar a prévia");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void segueMandandoSoQuandoMuda(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        UUID own = network(helper, player.getUUID(), false);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            TvPreview.handle(player, new TvPreviewPayload(menu.containerId, 1, List.of(binding(own))));
            TvPreview.tickForTests(helper.getLevel().getServer(), report(100, energy(own, 2_000)));
            double[] first = TvPreview.sentForTests(player.getUUID());
            helper.assertTrue(first != null && first[0] == 400.0, "primeiro número");
            TvPreview.tickForTests(helper.getLevel().getServer(), report(120, energy(own, 4_000)));
            double[] second = TvPreview.sentForTests(player.getUUID());
            helper.assertTrue(second != first && second[0] == 800.0, "a volta nova com outro número atualiza");
            TvPreview.tickForTests(helper.getLevel().getServer(), report(140, energy(own, 4_000)));
            helper.assertTrue(TvPreview.sentForTests(player.getUUID()) == second, "os mesmos números não mandam de novo");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void codecsDosDoisPayloadsIdaEVolta(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().getServer().registryAccess());
        TvPreviewPayload ask = new TvPreviewPayload(9, 3, List.of(binding(UUID.randomUUID()),
                new TvBinding(UUID.randomUUID(), ResourceType.ITEM, TvMetric.STORED)));
        TvPreviewPayload.STREAM_CODEC.encode(buf, ask);
        helper.assertTrue(TvPreviewPayload.STREAM_CODEC.decode(buf).equals(ask), "o pedido não voltou igual");
        TvPreviewValuesPayload answer = new TvPreviewValuesPayload(9, 3, new double[] {1.5, Double.NaN, -3.0E9});
        TvPreviewValuesPayload.STREAM_CODEC.encode(buf, answer);
        helper.assertTrue(TvPreviewValuesPayload.STREAM_CODEC.decode(buf).equals(answer), "a resposta não voltou igual");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void todaMudancaDaPecaDaLayoutNovoELigacaoNovaApareceNoRascunho(GameTestHelper helper) {
        UUID net = UUID.randomUUID();
        TvBinding bound = binding(net);
        TvPiece base = new TvPiece(TvPieceType.VALUE, 0, 0, 40, 12, 0, "", Optional.of(bound), TvFormat.DEFAULT,
                TvStyle.DEFAULT);
        TvLayout saved = TvLayout.of(List.of(base));
        List<UnaryOperator<TvPiece>> changes = List.of(
                p -> p.withBinding(Optional.of(new TvBinding(net, ResourceType.FLUID, TvMetric.IN))),
                p -> p.withBinding(Optional.of(new TvBinding(UUID.randomUUID(), ResourceType.ENERGY, TvMetric.IN))),
                p -> p.withBinding(Optional.of(new TvBinding(net, ResourceType.ENERGY, TvMetric.OUT))),
                p -> p.withFormat(new TvFormat(2, true, false, "", "", TvUnit.SECOND)),
                p -> p.withFormat(new TvFormat(1, true, false, "", "", TvUnit.MINUTE)),
                p -> p.withStyle(TvStyle.DEFAULT.withSize(12)),
                p -> p.withStyle(TvStyle.DEFAULT.withColor(0xFF112233)),
                p -> p.withRules(List.of(new TvRule(TvRuleOp.LT, 5, Optional.empty(), Optional.of(0xFFFF0000),
                        Optional.empty(), false, false))));
        for (UnaryOperator<TvPiece> change : changes) {
            TvLayout draft = saved.withPieces(List.of(change.apply(base)));
            helper.assertTrue(draft != saved && !draft.equals(saved), "a mudança devia dar um rascunho novo");
        }
        TvBinding fresh = new TvBinding(net, ResourceType.ENERGY, TvMetric.STORED);
        TvLayout draft = saved.withPieces(List.of(base.withBinding(Optional.of(fresh))));
        helper.assertTrue(draft.bindings().equals(List.of(fresh)), "a ligação nova está nas ligações do rascunho");
        helper.assertValueEqual(saved.bindingIndex(fresh), -1, "a Tela salva não tem o número: só a prévia o dá");
        helper.assertTrue(draft.bindings() == draft.bindings(), "a mesma lista a cada leitura (comparação por referência)");
        helper.succeed();
    }
}
