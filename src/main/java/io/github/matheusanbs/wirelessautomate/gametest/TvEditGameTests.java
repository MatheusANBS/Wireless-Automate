package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvEditing;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvRule;
import io.github.matheusanbs.wirelessautomate.tv.TvRuleOp;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTemplates;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    public static void salvarAcimaDeDezPorSegundoEIgnorado(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            TvLayout last = null;
            for (int i = 0; i < 12; i++) {
                TvLayout layout = TvLayout.of(List.of(text(0, 0, 30, 10, "v" + i)));
                if (i == 9) {
                    last = layout;
                }
                TvEditing.handleLayout(player, new TvLayoutPayload(menu.containerId, layout));
            }
            helper.assertValueEqual(tv.layout(), last, "só os 10 primeiros valem no mesmo segundo");
        });
    }
}
