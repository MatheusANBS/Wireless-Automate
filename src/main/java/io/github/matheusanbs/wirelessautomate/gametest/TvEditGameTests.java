package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutAckPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvEditing;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvRule;
import io.github.matheusanbs.wirelessautomate.tv.TvRuleOp;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTemplates;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Salvar o layout do editor da Tela de Sinal: quem pode, os tetos, a caixa e as ligações sem acesso. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvEditGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);

    private TvEditGameTests() {
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
        player.setPos(helper.absoluteVec(Vec3.atCenterOf(A)));
        BlockPos pos = helper.absolutePos(A);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        helper.assertTrue(player.containerMenu instanceof TvMenu, "o menu não abriu");
        return (TvMenu) player.containerMenu;
    }

    private static TvPiece text(int x, int y, int w, int h, String content) {
        return new TvPiece(TvPieceType.TEXT, x, y, w, h, 0, "", Optional.empty(), TvFormat.DEFAULT,
                new TvStyle(TvStyle.DEFAULT.color(), 0, 0, 0, 8, TvStyle.DEFAULT.align(), true, content));
    }

    private static TvPiece value(UUID network, TvBinding ruleBinding) {
        List<TvRule> rules = ruleBinding == null ? List.of()
                : List.of(new TvRule(TvRuleOp.GT, 1, Optional.of(ruleBinding), Optional.of(0xFFFF0000),
                        Optional.empty(), false, false));
        return new TvPiece(TvPieceType.VALUE, 0, 0, 40, 10, 0, "",
                Optional.of(new TvBinding(network, ResourceType.ITEM, TvMetric.IN)), TvFormat.DEFAULT,
                TvStyle.DEFAULT, false, rules);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void donoSalvaERevisaoSobe(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            int before = tv.revision();
            TvLayout layout = TvLayout.of(List.of(text(2, 2, 30, 10, "Ola")));
            TvEditing.handleLayout(player, new TvLayoutPayload(menu.containerId, layout));
            helper.assertValueEqual(tv.layout(), layout, "layout");
            helper.assertTrue(tv.revision() > before, "a revisão não subiu");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void quemNaoEDonoNemOperadorERecusado(GameTestHelper helper) {
        TestPlayers.MenuPlayer owner = TestPlayers.menuOpener(helper);
        TestPlayers.MenuPlayer other = TestPlayers.menuOpener(helper);
        withScreen(helper, owner.getUUID(), tv -> {
            TvEditing.LayoutResult result = TvEditing.applyLayout(other, tv,
                    TvLayout.of(List.of(text(0, 0, 10, 10, "x"))));
            helper.assertFalse(result.ok(), "outro jogador salvou");
            helper.assertValueEqual(tv.layout(), TvLayout.EMPTY, "o layout mudou");
            helper.assertTrue(other.actionBar != null, "sem aviso da recusa");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void caixaForaDaAreaECortada(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvEditing.LayoutResult result = TvEditing.applyLayout(player, tv,
                    TvLayout.of(List.of(text(20, 20, 500, 500, "grande"))));
            helper.assertTrue(result.ok(), "recusou");
            TvPiece piece = tv.layout().pieces().get(0);
            TvArea area = tv.area();
            helper.assertTrue(piece.width() <= area.pixelWidth() && piece.height() <= area.pixelHeight(), "tamanho");
            helper.assertTrue(piece.x() + piece.width() <= area.pixelWidth()
                    && piece.y() + piece.height() <= area.pixelHeight(), "fora da área");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ligacaoSemAcessoSaiEAvisaConta(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork own = data.create(player.getUUID(), "teste-tv-edit-propria");
        WaNetwork foreign = data.create(UUID.randomUUID(), "teste-tv-edit-alheia");
        withScreen(helper, player.getUUID(), tv -> {
            try {
                TvLayout layout = TvLayout.of(List.of(value(own.id(), null),
                        value(foreign.id(), new TvBinding(foreign.id(), ResourceType.ITEM, TvMetric.OUT))));
                helper.assertValueEqual(layout.bindings().size(), 3, "ligações enviadas");
                TvEditing.LayoutResult result = TvEditing.applyLayout(player, tv, layout);
                helper.assertTrue(result.ok(), "recusou");
                helper.assertValueEqual(result.dropped(), 2, "ligações que saíram (peça e regra)");
                helper.assertValueEqual(tv.layout().pieces().size(), 2, "as peças ficam");
                helper.assertTrue(tv.layout().pieces().get(0).binding().isPresent(), "a rede própria perdeu a ligação");
                helper.assertTrue(tv.layout().pieces().get(1).binding().isEmpty(), "a alheia manteve a ligação");
                helper.assertTrue(tv.layout().pieces().get(1).rules().isEmpty(),
                        "a regra com ligação sem acesso ficou e leria o dado da peça");
                helper.assertValueEqual(tv.layout().bindings().size(), 1, "ligações salvas");
            } finally {
                data.remove(own.id());
                data.remove(foreign.id());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void acimaDoTetoERecusado(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            List<TvPiece> pieces = new ArrayList<>();
            for (int i = 0; i < 129; i++) {
                pieces.add(text(0, 0, 4, 4, "a"));
            }
            TvEditing.LayoutResult result = TvEditing.applyLayout(player, tv, TvLayout.of(pieces));
            helper.assertFalse(result.ok(), "aceitou acima do teto");
            helper.assertValueEqual(tv.layout(), TvLayout.EMPTY, "o layout mudou");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layoutDaEtapa1SalvoDeNovoFicaIgual(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork own = data.create(player.getUUID(), "teste-tv-edit-etapa1");
        withScreen(helper, player.getUUID(), tv -> {
            try {
                tv.setLayout(TvTemplates.networkPanel(own.name(), own.color(), own.id(), ResourceType.ITEM, tv.area()));
                TvLayout saved = tv.layout();
                int revision = tv.revision();
                helper.assertTrue(TvEditing.applyLayout(player, tv, saved).ok(), "recusou");
                helper.assertValueEqual(tv.layout(), saved, "o layout mudou");
                helper.assertValueEqual(tv.revision(), revision, "a revisão subiu sem mudança");
            } finally {
                data.remove(own.id());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void regraSemAcessoSaiEORestanteFica(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork own = data.create(player.getUUID(), "teste-tv-edit-regra-propria");
        WaNetwork foreign = data.create(UUID.randomUUID(), "teste-tv-edit-regra-alheia");
        withScreen(helper, player.getUUID(), tv -> {
            try {
                TvRule kept = new TvRule(TvRuleOp.LT, 5, Optional.empty(), Optional.of(0xFF00FF00), Optional.empty(),
                        false, false);
                TvRule ownBound = new TvRule(TvRuleOp.GT, 1, Optional.of(new TvBinding(own.id(), ResourceType.ITEM,
                        TvMetric.OUT)), Optional.empty(), Optional.empty(), true, false);
                TvRule foreignBound = new TvRule(TvRuleOp.GT, 1, Optional.of(new TvBinding(foreign.id(),
                        ResourceType.ITEM, TvMetric.OUT)), Optional.empty(), Optional.empty(), false, true);
                TvPiece piece = value(own.id(), null).withRules(List.of(kept, foreignBound, ownBound));
                TvEditing.LayoutResult result = TvEditing.applyLayout(player, tv, TvLayout.of(List.of(piece)));
                helper.assertTrue(result.ok(), "recusou");
                helper.assertValueEqual(result.dropped(), 1, "só a regra alheia conta");
                helper.assertValueEqual(tv.layout().pieces().get(0).rules(), List.of(kept, ownBound), "regras salvas");
            } finally {
                data.remove(own.id());
                data.remove(foreign.id());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void avisoUsaSingularEPlural(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        UUID foreign = UUID.randomUUID();
        withScreen(helper, player.getUUID(), tv -> {
            TvEditing.applyLayout(player, tv, TvLayout.of(List.of(value(foreign, null))));
            helper.assertTrue(player.actionBar != null && player.actionBar.getContents()
                    instanceof net.minecraft.network.chat.contents.TranslatableContents one
                    && one.getKey().equals("message.wirelessautomate.tv.layout_saved_dropped.one"), "singular");
            TvEditing.applyLayout(player, tv, TvLayout.of(List.of(value(foreign, null), value(foreign, null))));
            helper.assertTrue(player.actionBar != null && player.actionBar.getContents()
                    instanceof net.minecraft.network.chat.contents.TranslatableContents many
                    && many.getKey().equals("message.wirelessautomate.tv.layout_saved_dropped"), "plural");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void salvarAcimaDeDoisPorSegundoEBarradoEAvisado(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            TvLayout second = null;
            TvLayoutAckPayload third = null;
            for (int i = 0; i < 3; i++) {
                TvLayout layout = TvLayout.of(List.of(text(0, 0, 30, 10, "v" + i)));
                if (i == 1) {
                    second = layout;
                }
                TvLayoutAckPayload ack = TvEditing.handleLayout(player, new TvLayoutPayload(menu.containerId, layout));
                if (i == 2) {
                    third = ack;
                } else {
                    helper.assertValueEqual(ack.result(), TvLayoutAckPayload.Result.SAVED, "salvar " + i);
                }
            }
            helper.assertValueEqual(tv.layout(), second, "só os 2 primeiros valem no mesmo segundo");
            helper.assertValueEqual(third.result(), TvLayoutAckPayload.Result.THROTTLED, "o terceiro foi barrado, mas respondido");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void todoSalvarRecebeRespostaInclusiveSemMudanca(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            TvLayout layout = TvLayout.of(List.of(text(0, 0, 30, 10, "igual")));
            helper.assertValueEqual(TvEditing.handleLayout(player, new TvLayoutPayload(menu.containerId, layout)).result(),
                    TvLayoutAckPayload.Result.SAVED, "primeiro");
            int revision = tv.revision();
            TvLayoutAckPayload again = TvEditing.handleLayout(player, new TvLayoutPayload(menu.containerId, layout));
            helper.assertValueEqual(again.result(), TvLayoutAckPayload.Result.UNCHANGED, "igual ao salvo");
            helper.assertValueEqual(tv.revision(), revision, "a revisão não sobe quando nada muda");
            helper.assertValueEqual(again.containerId(), menu.containerId, "o menu da resposta");
            helper.assertTrue(TvEditing.handleLayout(player, new TvLayoutPayload(menu.containerId + 50, layout)) == null,
                    "menu errado não tem a quem responder");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void recusaPorPermissaoTemMotivoDeNegado(GameTestHelper helper) {
        TestPlayers.MenuPlayer owner = TestPlayers.menuOpener(helper);
        TestPlayers.MenuPlayer other = TestPlayers.menuOpener(helper);
        withScreen(helper, owner.getUUID(), tv -> helper.assertValueEqual(
                TvEditing.applyLayout(other, tv, TvLayout.of(List.of(text(0, 0, 10, 10, "x")))).reason(),
                TvLayoutAckPayload.Result.DENIED, "motivo"));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_config_tv")
    public static void layoutAcimaDoTetoDeBytesERecusado(GameTestHelper helper) {
        int max = Config.tvMaxLayoutBytes();
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            try {
                Config.TV_MAX_LAYOUT_BYTES.set(1024);
                List<TvPiece> pieces = new ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    pieces.add(text(0, 0, 30, 10, "x".repeat(100)));
                }
                TvLayout big = TvLayout.of(pieces);
                helper.assertTrue(TvLayoutPayload.encodedSize(big) > 1024, "o layout de teste ficou pequeno");
                TvEditing.LayoutResult result = TvEditing.applyLayout(player, tv, big);
                helper.assertFalse(result.ok(), "salvou acima do teto");
                helper.assertValueEqual(result.reason(), TvLayoutAckPayload.Result.TOO_BIG, "motivo");
                helper.assertValueEqual(tv.layout(), TvLayout.EMPTY, "o layout mudou");
                helper.assertTrue(TvEditing.applyLayout(player, tv, TvLayout.of(List.of(text(0, 0, 10, 10, "ok")))).ok(),
                        "um layout pequeno deve caber");
            } finally {
                Config.TV_MAX_LAYOUT_BYTES.set(max);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void valorDesconhecidoNasChavesNovasCaiNoPadraoSemPerderOLayout(GameTestHelper helper) {
        CompoundTag piece = new CompoundTag();
        piece.putString("type", "text");
        piece.putInt("w", 20);
        piece.putInt("h", 8);
        CompoundTag style = new CompoundTag();
        style.putString("font", "fonte_do_futuro");
        style.putString("radius", "muito");
        piece.put("style", style);
        piece.putString("hidden", "talvez");
        ListTag rules = new ListTag();
        CompoundTag bogus = new CompoundTag();
        bogus.putString("op", "entre");
        bogus.putDouble("value", 1);
        CompoundTag good = new CompoundTag();
        good.putString("op", "gt");
        good.putDouble("value", 5);
        rules.add(bogus);
        rules.add(good);
        piece.put("rules", rules);
        CompoundTag layout = new CompoundTag();
        ListTag pieces = new ListTag();
        pieces.add(piece);
        layout.put("pieces", pieces);
        CompoundTag background = new CompoundTag();
        background.putString("kind", "mosaico");
        layout.put("background", background);
        layout.put("theme", new CompoundTag());
        layout.putString("scanlines", "sim");
        TvLayout read = TvLayout.CODEC.parse(NbtOps.INSTANCE, layout).result().orElse(null);
        helper.assertTrue(read != null, "o layout inteiro foi perdido");
        helper.assertValueEqual(read.pieces().size(), 1, "a peça");
        TvPiece first = read.pieces().get(0);
        helper.assertValueEqual(first.style().font(), TvStyle.DEFAULT.font(), "fonte desconhecida");
        helper.assertValueEqual(first.style().radius(), TvStyle.DEFAULT.radius(), "raio inválido");
        helper.assertFalse(first.hidden(), "hidden inválido");
        helper.assertValueEqual(first.rules().size(), 1, "só a regra boa fica");
        helper.assertValueEqual(first.rules().get(0).op(), TvRuleOp.GT, "a regra boa");
        helper.assertValueEqual(read.background(), TvBackground.DEFAULT, "fundo desconhecido");
        helper.assertValueEqual(read.theme(), TvTheme.DEFAULT, "tema malformado");
        helper.assertFalse(read.scanlines(), "linhas inválidas");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void idaEVoltaDoPayloadDeLayoutEDaResposta(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        TvLayout layout = TvLayout.of(List.of(text(1, 2, 30, 10, "abc"), text(0, 0, 8, 8, "d")));
        TvLayoutPayload.STREAM_CODEC.encode(buf, new TvLayoutPayload(5, layout));
        TvLayoutPayload back = TvLayoutPayload.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(back.layout(), layout, "layout");
        helper.assertValueEqual(back.containerId(), 5, "menu");
        for (TvLayoutAckPayload.Result result : TvLayoutAckPayload.Result.values()) {
            TvLayoutAckPayload ack = new TvLayoutAckPayload(9, result, 3);
            TvLayoutAckPayload.STREAM_CODEC.encode(buf, ack);
            helper.assertValueEqual(TvLayoutAckPayload.STREAM_CODEC.decode(buf), ack, "ack " + result);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layoutAcimaDe256KbNaoCabeNoPacote(GameTestHelper helper) {
        List<TvPiece> pieces = new ArrayList<>();
        for (int i = 0; i < TvLayout.HARD_CAP; i++) {
            pieces.add(text(0, 0, 30, 10, "x".repeat(TvStyle.MAX_TEXT)));
        }
        // As peças dão uns 100 KB; 24 artes de 128 × 128 (8 KB cada em meio byte) passam dos 256 KB.
        List<TvPixelArt> arts = new ArrayList<>();
        Random random = new Random(3);
        for (int i = 0; i < 24; i++) {
            byte[] pixels = new byte[128 * 128];
            for (int p = 0; p < pixels.length; p++) {
                pixels[p] = (byte) random.nextInt(16);
            }
            arts.add(new TvPixelArt(UUID.randomUUID(), "a" + i, 128, 128, new int[16], pixels));
        }
        TvLayout huge = TvLayout.of(pieces, TvTheme.DEFAULT, TvBackground.DEFAULT, false, arts);
        helper.assertTrue(TvLayoutPayload.encodedSize(huge) > TvLayoutPayload.MAX_BYTES, "o teste deveria passar de 256 KB");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        boolean refused = false;
        try {
            TvLayoutPayload.STREAM_CODEC.encode(buf, new TvLayoutPayload(1, huge));
        } catch (RuntimeException e) {
            refused = true;
        }
        helper.assertTrue(refused, "o codec deve recusar acima de 256 KB");
        helper.succeed();
    }
}
