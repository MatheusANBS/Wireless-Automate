package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineState;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineView;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A linha de uma máquina na lista das máquinas ({@link RangeList}), em duas linhas com a altura da linha do
 * grupo: na primeira, a bolinha do estado, o nome ({@link #rowName}: o rótulo, ou {@code #N}) e a marca
 * "exc."; na segunda, as coordenadas e a distância ao roteador, ou o estado em palavras quando ela não está
 * carregada. À direita da primeira linha fica o lugar do Olho ({@link #EYE_W}).
 *
 * <p>Os textos e as larguras são montados quando o snapshot muda (a visão é outro objeto), não a cada quadro: o
 * quadro só compara larguras e desenha. Quando a segunda linha não cabe inteira, fica só com as coordenadas
 * (carregada) ou só com o estado (as coordenadas vão na dica).
 */
final class MachineRow {
    /** O lugar do Olho, à direita da primeira linha. */
    static final int EYE_W = 10;
    /** Topo da primeira e da segunda linha de texto, a partir do topo da linha. */
    static final int LINE1_Y = 2;
    static final int LINE2_Y = 11;
    /** Onde o texto começa, a partir da borda esquerda da lista (depois da bolinha). */
    static final int TEXT_X = 16;
    private static final Component SEPARATOR = Component.literal(" · ");

    private @Nullable RangeView cached;
    private Component[] names = new Component[0];
    private Component[] coords = new Component[0];
    private int[] coordsW = new int[0];
    private Component[] suffixes = new Component[0];
    private int[] suffixW = new int[0];
    private int separatorW;
    private Component badgeText = Component.empty();
    private int badgeW;

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
        return Component.literal("#" + RangeList.number(view, index));
    }

    /** A dica da linha: o nome, as coordenadas, a distância, se segue o perfil, o estado e o que o clique faz. */
    static Component tooltip(RangeView view, int index) {
        MachineView machine = view.machine(index);
        if (machine == null) {
            return Component.empty();
        }
        Component follows = machine.exception() ? tr("range.machine.exception") : tr("range.machine.follows");
        Component detail = tr("range.machine.detail", RangeList.distance(machine), follows);
        Component text = RangeList.machineName(view, index).copy()
                .append("\n").append(tr("range.machine.detail", coordinates(machine.pos()), detail).copy()
                        .withColor(GuiPaint.TOOLTIP_MUTED))
                .append("\n").append(RangeList.stateText(machine.state()).copy()
                        .withColor(RangeList.stateColor(machine.state())));
        if (machine.state() == MachineState.REPLACED) {
            text = text.copy().append("\n").append(RangeRowButtons.updateDetail(view, EditTarget.machine(index)).copy()
                    .withColor(GuiPaint.TOOLTIP_MUTED));
        }
        return text.copy().append("\n").append(tr("range.machine.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** O fim da segunda linha: a distância, ou o estado em palavras quando ela não está carregada. */
    static Component suffix(MachineView machine) {
        return switch (machine.state()) {
            case LOADED -> RangeList.distance(machine);
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

    /** Monta os textos e as larguras de cada máquina, só quando a visão muda. */
    private void prepare(Font font, RangeView view) {
        if (view == cached) {
            return;
        }
        cached = view;
        int n = view.machines().size();
        names = new Component[n];
        coords = new Component[n];
        coordsW = new int[n];
        suffixes = new Component[n];
        suffixW = new int[n];
        separatorW = font.width(SEPARATOR);
        badgeText = tr("range.exception.badge");
        badgeW = font.width(badgeText);
        for (int i = 0; i < n; i++) {
            MachineView machine = view.machines().get(i);
            names[i] = rowName(view, i);
            coords[i] = coordinates(machine.pos());
            coordsW[i] = font.width(coords[i]);
            suffixes[i] = suffix(machine);
            suffixW[i] = font.width(suffixes[i]);
        }
    }

    /**
     * A máquina {@code index} na linha com o topo em {@code ry}, numa lista com a borda esquerda em {@code x}.
     * {@code nameRight} é até onde vai a primeira linha (antes dos botões da linha escolhida, ou do lugar do Olho)
     * e {@code right} até onde vai a segunda; {@code badge} é a marca "exc." (na linha escolhida, o ↺ mostra a
     * exceção).
     */
    void render(GuiGraphics g, Font font, RangeView view, int index, int x, int ry, int nameRight, int right,
            boolean badge) {
        MachineView machine = view.machine(index);
        if (machine == null) {
            return;
        }
        prepare(font, view);
        GuiPaint.dot(g, x + 8, ry + 7, RangeList.stateColor(machine.state()));
        int textX = x + TEXT_X;
        int end = nameRight - 2;
        if (badge && machine.exception()) {
            end -= badgeW + 3;
            GuiPaint.text(g, font, badgeText, end + 3, ry + LINE1_Y, GuiPaint.ACCENT);
        }
        boolean counts = machine.state() != MachineState.OUT_OF_RANGE && machine.state() != MachineState.OVER_LIMIT;
        GuiText.draw(g, font, names[index], textX, ry + LINE1_Y, end - textX, counts ? GuiPaint.FG : GuiPaint.MUTED);

        int room = right - 2 - textX;
        if (coordsW[index] + separatorW + suffixW[index] <= room) {
            GuiPaint.text(g, font, coords[index], textX, ry + LINE2_Y, GuiPaint.MUTED);
            int sx = textX + coordsW[index];
            GuiPaint.text(g, font, SEPARATOR, sx, ry + LINE2_Y, GuiPaint.MUTED);
            GuiPaint.text(g, font, suffixes[index], sx + separatorW, ry + LINE2_Y, suffixColor(machine.state()));
        } else if (machine.state() == MachineState.LOADED) {
            GuiText.draw(g, font, coords[index], textX, ry + LINE2_Y, room, GuiPaint.MUTED);
        } else {
            // sem lugar para tudo, o estado diz mais que as coordenadas (que ficam na dica)
            GuiText.draw(g, font, suffixes[index], textX, ry + LINE2_Y, room, suffixColor(machine.state()));
        }
    }
}
