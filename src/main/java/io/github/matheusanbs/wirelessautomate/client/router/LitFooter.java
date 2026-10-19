package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.highlight.HighlightRenderer;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * As máquinas acesas no mundo na coluna da tela do roteador ({@link MachineColumn}): o Olho de cada linha da lista
 * ({@link MachineRow}), que acende e apaga a máquina na dimensão do roteador ({@link HighlightRenderer}), e a linha
 * embaixo da lista com alguma acesa: "2 máquinas acesas no mundo · Apagar todas", ou "2 acesas" quando a lista é
 * estreita. O Apagar todas apaga as acesas de qualquer roteador. Os textos e as larguras são montados quando as
 * acesas ou a largura mudam. Guarda também os textos do destaque comuns à lista e ao painel ({@link #tr},
 * {@link #eyeTooltip}).
 */
final class LitFooter {
    static final int H = 12;
    private static final Component SEPARATOR = Component.literal(" · ");

    private final Supplier<RouterSnapshot> snapshot;
    private final MachineRow machineRow = new MachineRow(this::lit);
    private int x;
    private int y;
    private int w;
    private int version = Integer.MIN_VALUE;
    private int preparedW = -1;
    private Component count = Component.empty();
    private Component clear = Component.empty();
    private int clearW;
    private int countW;
    private int separatorW;

    /** @param snapshot o snapshot da tela: a dimensão e a posição do roteador, e as máquinas */
    LitFooter(Supplier<RouterSnapshot> snapshot) {
        this.snapshot = snapshot;
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.highlight." + key, args);
    }

    /** A dica do Olho da linha e do "No mundo" do painel: acender ou apagar a máquina no mundo. */
    static Component eyeTooltip(boolean lit) {
        return tr(lit ? "eye.off" : "eye.on").copy().append("\n")
                .append(tr(lit ? "eye.off.tooltip" : "eye.on.tooltip").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** O desenho da linha da máquina, com o Olho destas acesas (a lista o usa). */
    MachineRow machineRow() {
        return machineRow;
    }

    /** A máquina na posição {@code pos} da dimensão do roteador está acesa. */
    private boolean lit(long pos) {
        return HighlightRenderer.isLit(snapshot.get().dimension(), pos);
    }

    /** A linha das acesas aparece: com a lista à vista e alguma máquina acesa. */
    static boolean shown(boolean listShown) {
        return listShown && HighlightRenderer.count() > 0;
    }

    /** Põe a linha no lugar e monta os textos que mudaram; a coluna chama antes do desenho e dos cliques. */
    void place(Font font, int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
        prepare(font);
    }

    private void prepare(Font font) {
        if (version == HighlightRenderer.version() && preparedW == w) {
            return;
        }
        version = HighlightRenderer.version();
        preparedW = w;
        int n = HighlightRenderer.count();
        clear = tr("clear_all");
        clearW = font.width(clear);
        separatorW = font.width(SEPARATOR);
        Component full = n == 1 ? tr("lit.one") : tr("lit", n);
        Component brief = n == 1 ? tr("lit.short.one") : tr("lit.short", n);
        count = font.width(full) <= w - clearW - separatorW - 4 ? full : brief;
        countW = font.width(count);
    }

    /** O Apagar todas logo depois da contagem e do separador; numa lista estreita demais, preso à direita. */
    private int clearX() {
        return Math.min(x + 2 + countW + separatorW, x + w - clearW - 2);
    }

    private boolean overClear(double mx, double my) {
        return mx >= clearX() - 1 && mx < clearX() + clearW + 1 && my >= y && my < y + H;
    }

    /** A linha das acesas, se ela aparece. */
    void render(GuiGraphics g, Font font, boolean listShown, int mx, int my) {
        if (!shown(listShown)) {
            return;
        }
        int cx = clearX();
        GuiText.draw(g, font, count, x + 2, y + 2, cx - separatorW - x - 2, GuiPaint.MUTED);
        GuiPaint.text(g, font, SEPARATOR, cx - separatorW, y + 2, GuiPaint.MUTED);
        boolean over = overClear(mx, my);
        GuiPaint.text(g, font, clear, cx, y + 2, over ? GuiPaint.ACCENT : GuiPaint.FG);
        g.fill(cx, y + 11, cx + clearW, y + 12, GuiPaint.ACCENT);
    }

    /**
     * Clique esquerdo com a lista {@code list} à vista: o Apagar todas apaga todas as acesas, e o Olho de uma linha
     * acende ou apaga a máquina dela, com a linha até este roteador. Devolve se o clique foi de um dos dois.
     */
    boolean click(RangeList list, double mx, double my) {
        if (shown(true) && overClear(mx, my)) {
            HighlightRenderer.clear();
            return true;
        }
        RouterSnapshot s = snapshot.get();
        RangeList.Row row = machineRow.eyeAt(list, s.range(), s.target(), mx, my);
        RangeView.MachineView machine = row == null ? null : s.range().machine(row.machine());
        if (machine == null) {
            return false;
        }
        HighlightRenderer.toggle(s.dimension(), machine.pos(), s.pos());
        return true;
    }

    /** A dica do Apagar todas ou do Olho sob o mouse, com a lista {@code list} à vista; {@code null} fora dos dois. */
    @Nullable Component tooltip(RangeList list, double mx, double my) {
        if (shown(true) && overClear(mx, my)) {
            return tr("clear_all.tooltip");
        }
        RouterSnapshot s = snapshot.get();
        RangeList.Row row = machineRow.eyeAt(list, s.range(), s.target(), mx, my);
        RangeView.MachineView machine = row == null ? null : s.range().machine(row.machine());
        return machine == null ? null : eyeTooltip(lit(machine.pos().asLong()));
    }

    /** O centro do Olho da linha da máquina {@code target} (gancho do e2e). */
    int @Nullable [] eyeCenter(RangeList list, EditTarget target) {
        return machineRow.eyeCenter(list, snapshot.get().range(), target);
    }

    /** O centro do Apagar todas (gancho do e2e). */
    int[] clearCenter() {
        return new int[] {clearX() + clearW / 2, y + H / 2};
    }
}
