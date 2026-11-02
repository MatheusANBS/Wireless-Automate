package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineState;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineView;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.Locale;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A linha de uma máquina na lista das máquinas ({@link RangeList}), em duas linhas com a altura da linha do
 * grupo: na primeira, a bolinha do estado, o nome ({@link #rowName}: o rótulo, ou {@code #N}) e a marca
 * "exc."; na segunda, as coordenadas e a distância ao roteador, ou o estado em palavras quando ela não está
 * carregada. À direita da primeira linha fica o Olho ({@link #EYE_W}): ciano com a máquina acesa no mundo,
 * cinza apagada; o clique nele é da lista ({@link #eyeAt}). A presa dentro do grupo dela usa a mesma linha
 * ({@link #renderAttached}), com "presa" (ou o nome dado) no lugar do número e sem a distância.
 *
 * <p>Os textos e as larguras são montados quando o snapshot muda (a visão é outro objeto), não a cada quadro: o
 * quadro só compara larguras e desenha. Quando a segunda linha não cabe inteira, fica só com as coordenadas
 * (carregada) ou só com o estado (as coordenadas vão na dica).
 */
final class MachineRow {
    /** O lugar do Olho, à direita da primeira linha. */
    static final int EYE_W = 10;
    /** A altura da área de clique do Olho, a da primeira linha com os botões da linha escolhida. */
    private static final int EYE_H = RangeRowButtons.SIZE + 1;
    /** Topo do texto, a partir do topo da linha. */
    static final int LINE1_Y = 2;
    /** Onde o texto começa, a partir da borda esquerda da lista (depois da bolinha). */
    static final int TEXT_X = 16;

    /** A máquina na posição dada está acesa no mundo. */
    private final LongPredicate lit;
    /** A posição da presa ({@code BlockPos.asLong}), para o Olho dela. */
    private final LongSupplier attachedPos;
    private @Nullable RangeView cached;
    private Component[] names = new Component[0];
    private int[] namesW = new int[0];
    private Component[] suffixes = new Component[0];
    private int[] suffixW = new int[0];
    private Component badgeText = Component.empty();
    private int badgeW;
    /** A marca curta, "· exc.", quando a inteira não cabe ao lado do nome. */
    private Component badgeShort = Component.empty();
    private int badgeShortW;
    /** O nome dado à presa para o qual {@link #attachedName} foi montado; {@code null} antes do primeiro. */
    private @Nullable String attachedFor;
    private Component attachedName = Component.empty();
    private int attachedNameW;
    /** O "presa" à direita quando a presa tem nome (vazio sem nome: o nome já é "presa"). */
    private Component attachedSuffix = Component.empty();
    private int attachedSuffixW;


    /**
     * @param lit         se a máquina na posição dada está acesa no mundo (o Olho aceso)
     * @param attachedPos a posição da presa ({@code BlockPos.asLong})
     */
    MachineRow(LongPredicate lit, LongSupplier attachedPos) {
        this.lit = lit;
        this.attachedPos = attachedPos;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
    }

    /** As coordenadas da máquina, "−118 65 231" (com o hífen do jogo). */
    static Component coordinates(BlockPos pos) {
        return Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    /** O nome na linha da lista, embaixo do grupo: o rótulo, ou só "#3" (o grupo já diz o bloco). */
    static Component rowName(RangeView view, int index) {
        MachineView machine = view.machine(index);
        if (machine != null && !machine.label().isEmpty()) {
            return Component.literal(machine.label());
        }
        return Component.literal("#" + MachineTexts.number(view, index));
    }

    /** O nome da presa na linha dentro do grupo: o nome dado, ou "presa". */
    static Component attachedRowName(String label) {
        return label.isEmpty() ? tr("range.attached.row") : Component.literal(label);
    }

    /**
     * A dica da linha da presa: o nome (o dado ou "Máquina presa"), o bloco dela e, dentro do grupo, se segue o
     * perfil; e o que o clique faz.
     */
    static Component attachedTooltip(RangeView view, boolean inGroup, Component blockName, String label) {
        Component head = label.isEmpty() ? tr("range.attached") : Component.literal(label);
        Component detail = !inGroup ? blockName : tr("range.machine.detail", blockName,
                view.attachedException() ? tr("range.machine.exception") : tr("range.machine.follows"));
        return head.copy().append("\n").append(detail.copy().withColor(GuiPaint.TOOLTIP_MUTED))
                .append("\n").append(tr("range.attached.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** A dica da linha: o nome, as coordenadas, a distância, se segue o perfil, o estado e o que o clique faz. */
    static Component tooltip(RangeView view, int index) {
        MachineView machine = view.machine(index);
        if (machine == null) {
            return Component.empty();
        }
        Component follows = machine.exception() ? tr("range.machine.exception") : tr("range.machine.follows");
        Component detail = tr("range.machine.detail", MachineTexts.distance(machine), follows);
        Component text = MachineTexts.machineName(view, index).copy()
                .append("\n").append(tr("range.machine.detail", coordinates(machine.pos()), detail).copy()
                        .withColor(GuiPaint.TOOLTIP_MUTED))
                .append("\n").append(MachineTexts.stateText(machine.state()).copy()
                        .withColor(MachineTexts.stateColor(machine.state())));
        if (machine.state() == MachineState.REPLACED) {
            text = text.copy().append("\n").append(RangeRowButtons.updateDetail(view, EditTarget.machine(index)).copy()
                    .withColor(GuiPaint.TOOLTIP_MUTED));
        }
        return text.copy().append("\n").append(tr("range.machine.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** O fim da segunda linha: a distância, ou o estado em palavras quando ela não está carregada. */
    static Component suffix(MachineView machine) {
        return switch (machine.state()) {
            case LOADED -> tr("range.distance.short", machine.distance());
            case REPLACED -> tr("range.machine.replaced");
            case UNLOADED, MISSING, OUT_OF_RANGE, OVER_LIMIT ->
                    tr("range.machine.short." + machine.state().name().toLowerCase(Locale.ROOT));
        };
    }

    private static int suffixColor(MachineState state) {
        return switch (state) {
            case LOADED, OUT_OF_RANGE, OVER_LIMIT -> GuiPaint.MUTED;
            case REPLACED -> GuiPaint.BRASS;
            case UNLOADED -> GuiPaint.WARN;
            case MISSING -> GuiPaint.DANGER;
        };
    }

    /** Monta os textos e as larguras da presa, só quando o nome dado a ela muda. */
    private void prepareAttached(Font font, String label) {
        if (label.equals(attachedFor)) {
            return;
        }
        attachedFor = label;
        attachedName = attachedRowName(label);
        attachedNameW = font.width(attachedName);
        attachedSuffix = label.isEmpty() ? Component.empty() : tr("range.attached.row");
        attachedSuffixW = font.width(attachedSuffix);
    }

    /** Monta os textos e as larguras de cada máquina, só quando a visão muda. */
    private void prepare(Font font, RangeView view) {
        if (view == cached) {
            return;
        }
        // o nome da presa é montado de novo com a visão, como os outros textos
        attachedFor = null;
        cached = view;
        int n = view.machines().size();
        names = new Component[n];
        namesW = new int[n];
        suffixes = new Component[n];
        suffixW = new int[n];
        badgeText = tr("range.exception.badge");
        badgeW = font.width(badgeText);
        badgeShort = tr("range.exception.badge.short");
        badgeShortW = font.width(badgeShort);
        for (int i = 0; i < n; i++) {
            MachineView machine = view.machines().get(i);
            names[i] = rowName(view, i);
            namesW[i] = font.width(names[i]);
            suffixes[i] = suffix(machine);
            suffixW[i] = font.width(suffixes[i]);
        }
    }

    /**
     * A máquina {@code index} na linha com o topo em {@code ry}, numa lista com a borda esquerda em {@code x}: a bolinha
     * do estado, o nome com "· exceção" quando a máquina difere do perfil e, à direita, a distância (ou o estado em
     * palavras quando ela não está carregada) antes do Olho. {@code nameRight} é até onde vai o texto (antes dos botões
     * da linha escolhida, ou do lugar do Olho); {@code badge} é a marca "· exceção" (na linha escolhida, o ↺ mostra a
     * exceção); {@code eye} é o Olho na linha (sai com o "Desvincular?" armado).
     */
    void render(GuiGraphics g, Font font, RangeView view, int index, int x, int ry, int nameRight, int right,
            boolean badge, boolean eye) {
        MachineView machine = view.machine(index);
        if (machine == null) {
            return;
        }
        prepare(font, view);
        GuiPaint.dot(g, x + 8, ry + 3, MachineTexts.stateColor(machine.state()));
        if (eye) {
            int eyeColor = lit.test(machine.pos().asLong()) ? GuiPaint.HIGHLIGHT : GuiPaint.MUTED;
            GuiPaint.eye(g, right - EYE_W + 2, ry + LINE1_Y + 1, eyeColor);
        }
        int textX = x + TEXT_X;
        int end = nameRight - 2;
        // a distância (ou o estado) à direita, só enquanto sobra lugar para o nome; com os botões da linha, sai
        if (badge) {
            int suffixRoom = end - textX - namesW[index] - 6;
            if (suffixW[index] <= suffixRoom) {
                end -= suffixW[index] + 3;
                GuiPaint.text(g, font, suffixes[index], end + 3, ry + LINE1_Y, suffixColor(machine.state()));
            }
        }
        boolean counts = machine.state() != MachineState.OUT_OF_RANGE && machine.state() != MachineState.OVER_LIMIT;
        int nameRoom = end - textX;
        GuiText.draw(g, font, names[index], textX, ry + LINE1_Y, nameRoom, counts ? GuiPaint.FG : GuiPaint.MUTED);
        if (badge && machine.exception()) {
            badge(g, font, textX + namesW[index] + 3, ry, nameRoom - namesW[index] - 3);
        }
    }

    /**
     * A presa na linha com o topo em {@code ry}, dentro do grupo dela, como uma máquina: a bolinha (verde com o bloco
     * na frente, vermelha sem ele), "presa" ou o nome dado, a marca "· exceção" e o Olho. Com nome, o "presa" vai à
     * direita, no lugar da distância. {@code label} é o nome dado; {@code present}, há bloco na frente do roteador;
     * {@code nameRight}, {@code right} e {@code badge} como no {@link #render}.
     */
    void renderAttached(GuiGraphics g, Font font, RangeView view, String label, boolean present, int x, int ry,
            int nameRight, int right, boolean badge) {
        prepare(font, view);
        prepareAttached(font, label);
        GuiPaint.dot(g, x + 8, ry + 3, present ? GuiPaint.OK : GuiPaint.DANGER);
        int eyeColor = lit.test(attachedPos.getAsLong()) ? GuiPaint.HIGHLIGHT : GuiPaint.MUTED;
        GuiPaint.eye(g, right - EYE_W + 2, ry + LINE1_Y + 1, eyeColor);
        int textX = x + TEXT_X;
        int end = nameRight - 2;
        if (badge && attachedSuffixW > 0 && attachedSuffixW <= end - textX - attachedNameW - 6) {
            end -= attachedSuffixW + 3;
            GuiPaint.text(g, font, attachedSuffix, end + 3, ry + LINE1_Y, GuiPaint.MUTED);
        }
        int nameRoom = end - textX;
        GuiText.draw(g, font, attachedName, textX, ry + LINE1_Y, nameRoom, GuiPaint.FG);
        if (badge && view.attachedException()) {
            badge(g, font, textX + attachedNameW + 3, ry, nameRoom - attachedNameW - 3);
        }
    }

    /** A marca de exceção em {@code bx}: a inteira, a curta se só ela cabe em {@code room}, ou nada. */
    private void badge(GuiGraphics g, Font font, int bx, int ry, int room) {
        if (badgeW <= room) {
            GuiPaint.text(g, font, badgeText, bx, ry + LINE1_Y, GuiPaint.ACCENT);
        } else if (badgeShortW <= room) {
            GuiPaint.text(g, font, badgeShort, bx, ry + LINE1_Y, GuiPaint.ACCENT);
        }
    }

    /**
     * A linha da máquina (ou da presa dentro do grupo) da lista {@code list} cujo Olho está sob o mouse, ou
     * {@code null}. Na linha do alvo com o "Desvincular?" armado, o Olho não aparece.
     */
    @Nullable RangeList.Row eyeAt(RangeList list, RangeView view, EditTarget target, double mouseX, double mouseY) {
        RangeList.Row row = list.rowAt(view, mouseX, mouseY);
        if (row == null || !row.machineLike() || row.target().equals(target) && list.unlinkArmed(target)) {
            return null;
        }
        int ry = list.rowTop(row);
        int right = list.textRight(view);
        return mouseX >= right - EYE_W && mouseX < right && mouseY >= ry && mouseY < ry + EYE_H ? row : null;
    }

    /**
     * O centro do Olho da linha da máquina (ou da presa) {@code target} (gancho do e2e); {@code null} se fora da
     * vista.
     */
    int @Nullable [] eyeCenter(RangeList list, RangeView view, EditTarget target) {
        int[] center = list.rowCenter(view, target);
        return center == null ? null
                : new int[] {list.textRight(view) - EYE_W / 2, center[1] - RangeList.MACHINE_H / 2 + EYE_H / 2};
    }
}
