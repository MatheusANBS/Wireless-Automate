package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A lista aberta de um seletor (o grupo da rede, o grupo do filtro...): janela logo abaixo do seletor de 18 px, linhas
 * de 18 px com ícone ou quadradinho de cor e o texto, a escolhida em {@link PanelPaint#chosenRow}, até 8 linhas e a
 * roda rola. Uma linha com {@code id} nulo é a de "nenhum".
 */
public final class DropdownModal implements TabletModal {
    /** O que a janela redesenha por cima do véu: o seletor que a abriu. */
    public interface Anchor {
        void draw(GuiGraphics g);
    }

    /** Uma linha: o id que {@code pick} recebe, o texto, o ícone (ou nada, e então o quadradinho em {@code color}). */
    public record Row(@Nullable UUID id, Component label, @Nullable ItemStack icon, int color, boolean muted) {
    }

    private static final int ROW_H = 18;
    private static final int MAX_ROWS = 8;

    private final int x;
    private final int y;
    private final int w;
    private final List<Row> rows;
    private final @Nullable UUID chosen;
    private final Consumer<@Nullable UUID> pick;
    private final Anchor anchor;
    private final int visible;
    private int scroll;
    private boolean done;

    /** {@code x} e {@code y} são o canto do seletor; a janela abre 1 px abaixo dele. */
    public DropdownModal(int x, int y, int w, List<Row> rows, @Nullable UUID chosen, Consumer<@Nullable UUID> pick,
            Anchor anchor) {
        this.x = x;
        this.y = y + ROW_H + 1;
        this.w = w;
        this.rows = rows;
        this.chosen = chosen;
        this.pick = pick;
        this.anchor = anchor;
        this.visible = Math.min(MAX_ROWS, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            if (Objects.equals(rows.get(i).id(), chosen)) {
                scroll = Math.max(0, Math.min(i - visible + 1, rows.size() - visible));
            }
        }
    }

    private int height() {
        return visible * ROW_H + 2;
    }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my) {
        anchor.draw(g);
        PanelPaint.window(g, x, y, w, height());
        for (int i = 0; i < visible; i++) {
            Row row = rows.get(scroll + i);
            int ry = y + 1 + i * ROW_H;
            if (Objects.equals(row.id(), chosen)) {
                PanelPaint.chosenRow(g, x + 1, ry, w - 2, ROW_H);
            } else if (mx >= x + 1 && mx < x + w - 1 && my >= ry && my < ry + ROW_H) {
                g.fill(x + 1, ry, x + w - 1, ry + ROW_H, GuiPaint.ROW_HOVER);
            }
            ItemStack icon = row.icon();
            if (icon != null && !icon.isEmpty()) {
                g.renderItem(icon, x + 3, ry + 1);
            } else {
                PanelPaint.swatch(g, x + 7, ry + 6, 7, row.color());
            }
            GuiText.draw(g, font, row.label(), x + 22, ry + 5, w - 22 - 5, row.muted() ? GuiPaint.MUTED : GuiPaint.FG);
        }
    }

    @Override
    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + height();
    }

    @Override
    public void mouseClicked(double mx, double my, int button) {
        int i = ((int) my - y - 1) / ROW_H;
        if (button == 0 && my >= y + 1 && i >= 0 && i < visible) {
            pick.accept(rows.get(scroll + i).id());
            done = true;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(delta), rows.size() - visible));
        return true;
    }

    @Override
    public boolean done() {
        return done;
    }

    /** O centro da linha de {@code id} na tela (para o e2e clicar); {@code null} se ela não está à vista. */
    public int[] rowCenter(@Nullable UUID id) {
        for (int i = 0; i < visible; i++) {
            if (Objects.equals(rows.get(scroll + i).id(), id)) {
                return new int[] {x + w / 2, y + 1 + i * ROW_H + ROW_H / 2};
            }
        }
        return null;
    }
}
