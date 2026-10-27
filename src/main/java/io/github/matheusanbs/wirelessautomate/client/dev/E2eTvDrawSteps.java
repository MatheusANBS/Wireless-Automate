package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.tv.render.TvCanvases;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvRule;
import io.github.matheusanbs.wirelessautomate.tv.TvRuleOp;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * O desenho da Tela com o modelo da etapa 2 (tema, fundo, linhas, cantos, opacidade, peças escondidas, regras e
 * piscar), montado direto no block entity porque o editor é de outra tarefa. Parte do Painel de rede que a suíte
 * {@code tela} aplicou, refeito com cores de tema: fotografa os quatro temas, o degradê, as linhas de varredura, um
 * Valor vermelho pela regra e os cantos; confere que o piscar redesenha poucas vezes por segundo; e devolve o layout
 * de antes.
 */
final class E2eTvDrawSteps {
    private static final int BLINK_WAIT_TICKS = 40;
    private static List<TvPiece> panel = List.of();
    private static TvLayout original = TvLayout.EMPTY;
    private static long paintsAtStart;

    private E2eTvDrawSteps() {
    }

    static void drawSteps(List<Step> list, UUID playerId, Supplier<BlockPos> tv) {
        list.add(new Step("Tela: guardar o Painel de rede", STEP_TIMEOUT_MS, () -> {
            original = entity(tv).layout();
            panel = original.pieces();
        }, () -> panel.size() == 5, () -> "peças do Painel: " + panel.size()));
        for (TvTheme theme : TvTheme.PRESETS) {
            String name = theme.name().substring(theme.name().lastIndexOf('.') + 1);
            list.add(set(tv, "tema " + name, () -> themed(theme)));
            list.add(E2eTvSteps.view("tela-tema-" + name, playerId, E2eTvSteps::worldEye, E2eTvSteps::worldTarget));
        }
        list.add(set(tv, "fundo em degradê", () -> TvLayout.of(text(), TvTheme.GRAPHITE,
                new TvBackground(TvBackground.Kind.GRADIENT, 0xFF1B3A6B, TvColor.theme(TvColor.BG)), false)));
        list.add(E2eTvSteps.view("tela-fundo-degrade", playerId, E2eTvSteps::worldEye, E2eTvSteps::worldTarget));
        list.add(set(tv, "linhas de varredura", () -> themed(TvTheme.GRAPHITE).withScanlines(true)));
        list.add(E2eTvSteps.view("tela-linhas", playerId, E2eTvSteps::worldEye, E2eTvSteps::worldTarget));
        list.add(set(tv, "regra: Valor vermelho", () -> themed(TvTheme.GRAPHITE).withPieces(
                ruled(rule(0xFFFF3030, false)))));
        list.add(E2eTvSteps.view("tela-regra", playerId, E2eTvSteps::worldEye, E2eTvSteps::worldTarget));
        list.add(set(tv, "cantos, opacidade e peça escondida", E2eTvDrawSteps::corners));
        list.add(E2eTvSteps.view("tela-cantos", playerId, E2eTvSteps::worldEye, E2eTvSteps::worldTarget));
        list.add(set(tv, "regra que pisca", () -> themed(TvTheme.GRAPHITE).withPieces(ruled(rule(0xFFFF3030, true)))));
        list.add(new Step("Tela: o piscar redesenha só a Tela que pisca, 2 por segundo", 30_000,
                () -> paintsAtStart = TvCanvases.paintsForTests, () -> {
                    long spent = TvCanvases.paintsForTests - paintsAtStart;
                    return TvCanvases.blinkingForTests() >= 1 && stepTicks >= BLINK_WAIT_TICKS && spent >= 3
                            && spent <= 12;
                }, () -> TvCanvases.blinkingForTests() + " piscando, " + (TvCanvases.paintsForTests - paintsAtStart)
                        + " redesenho(s) em " + stepTicks + " ticks"));
        list.add(set(tv, "layout de antes", () -> original));
        list.add(new Step("Tela: nada piscando de novo", STEP_TIMEOUT_MS, () -> {
        }, () -> TvCanvases.blinkingForTests() == 0 && stepTicks >= 5,
                () -> TvCanvases.blinkingForTests() + " piscando"));
    }

    private static TvBlockEntity entity(Supplier<BlockPos> tv) {
        return (TvBlockEntity) Minecraft.getInstance().level.getBlockEntity(tv.get());
    }

    /** Aplica o layout (montado só na hora, quando o Painel já foi guardado) no servidor e espera o cliente receber igual. */
    private static Step set(Supplier<BlockPos> tv, String name, Supplier<TvLayout> layout) {
        TvLayout[] applied = new TvLayout[1];
        return new Step("Tela: " + name, STEP_TIMEOUT_MS, () -> onServer(server -> {
            applied[0] = layout.get();
            ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(applied[0]);
            return null;
        }), () -> entity(tv).layout().equals(applied[0]), () -> "layout no cliente");
    }

    private static TvLayout themed(TvTheme theme) {
        return TvLayout.of(themedPieces(), theme, TvBackground.DEFAULT, false);
    }

    /**
     * O Painel com as cores do tema: um cartão arredondado com borda (o fundo da Tela aparece em volta), o título
     * apagado, o que entra em Alta, o que sai em Baixa e as portas em apagado.
     */
    private static List<TvPiece> themedPieces() {
        List<TvPiece> pieces = new ArrayList<>(panel);
        TvPiece card = panel.get(0);
        pieces.set(0, card.withBox(2, 2, card.width() - 4, card.height() - 4).withStyle(card.style()
                .withBackground(TvColor.theme(TvColor.CARD)).withBorder(TvColor.theme(TvColor.MUTED), 1)
                .withRadius(4)));
        int[] roles = {0, TvColor.MUTED, TvColor.HIGH, TvColor.LOW, TvColor.MUTED};
        for (int i = 1; i < roles.length; i++) {
            pieces.set(i, pieces.get(i).withStyle(pieces.get(i).style().withColor(TvColor.theme(roles[i]))));
        }
        return pieces;
    }

    /** Só os textos do Painel (sem o cartão), para o fundo aparecer inteiro. */
    private static List<TvPiece> text() {
        List<TvPiece> pieces = new ArrayList<>(themedPieces());
        pieces.remove(0);
        return pieces;
    }

    /** Maior que zero (sem ligação própria: vale o dado do Valor): troca a cor do texto, e opcionalmente pisca. */
    private static TvRule rule(int color, boolean blink) {
        return new TvRule(TvRuleOp.GT, 0, Optional.<TvBinding>empty(), Optional.of(color), Optional.empty(), blink,
                false);
    }

    /** O Painel com a regra na peça do que entra (a de índice 2). */
    private static List<TvPiece> ruled(TvRule rule) {
        List<TvPiece> pieces = new ArrayList<>(themedPieces());
        pieces.set(2, pieces.get(2).withRules(List.of(rule)));
        return pieces;
    }

    /** Cinco cartões: reto, de raio 3 com borda de 2, de raio 8 a 50%, de raio 8 e um escondido. */
    private static TvLayout corners() {
        List<TvPiece> pieces = List.of(
                card(4, 4, 36, 24, 0, 100, false), card(46, 4, 36, 24, 3, 100, false),
                card(88, 4, 36, 24, 8, 50, false), card(4, 34, 36, 24, 8, 100, false),
                card(46, 34, 36, 24, 3, 100, true));
        return TvLayout.of(pieces, TvTheme.PORCELAIN, TvBackground.DEFAULT, false);
    }

    private static TvPiece card(int x, int y, int w, int h, int radius, int opacity, boolean hidden) {
        TvStyle style = new TvStyle(TvColor.theme(TvColor.TEXT), TvColor.theme(TvColor.HIGH),
                TvColor.theme(TvColor.LOW), radius == 3 ? 2 : 1, 8, TvAlign.LEFT, true, "", radius, opacity,
                TvStyle.DEFAULT.font());
        return new TvPiece(TvPieceType.SHAPE, x, y, w, h, 0, "", Optional.empty(), TvFormat.DEFAULT, style, hidden,
                List.of());
    }
}
