package io.github.matheusanbs.wirelessautomate.client.guide;

import io.github.matheusanbs.wirelessautomate.client.guide.GuideInk.Ink;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Os gestos nas cenas das duas folhas à vista: o botão esquerdo apertado dentro de uma moldura começa o arrasto, que
 * gira a cena (e o clique não vira link nem folha); a roda dentro dela dá zoom; dois cliques na mesma cena em
 * {@value #DOUBLE_CLICK_MS} ms voltam à vista da página (um aperto que virou arrasto não conta como o primeiro). Com o
 * mouse sobre uma cena, a anotação sob ele fica realçada e o texto dela vira a dica ({@link SceneNotes}; a procura só
 * roda quando o mouse ou a vista mudam). A {@link GuideScreen} só repassa os eventos e mostra as folhas a cada mudança
 * de lugar; a vista de cada cena fica na tinta dela, que vive enquanto o livro está aberto.
 */
public final class SceneInput {
    /** O intervalo do clique duplo. */
    static final long DOUBLE_CLICK_MS = 300;
    /** Quanto o mouse anda, em pixels de tela, para o aperto virar arrasto e não contar para o clique duplo. */
    static final double DRAG_SLOP_PX = 2;

    private BookScale scale;
    private List<Ink> left = List.of();
    private List<Ink> right = List.of();
    private GuideInk.@Nullable Scene dragging;
    private double lastTexX;
    private double lastTexY;
    private double pressTexX;
    private double pressTexY;
    private GuideInk.@Nullable Scene lastClicked;
    private long lastClickAt;
    /** O ponto da última {@link #at}, em unidades da folha da cena achada. */
    private double atX;
    private double atY;
    /** A cena sob o mouse no quadro, com a anotação achada nela. */
    private GuideInk.@Nullable Scene hovering;

    /** As folhas à vista e a escala; o arrasto em curso termina (a folha virou ou a janela mudou). */
    void show(BookScale scale, List<Ink> left, List<Ink> right) {
        this.scale = scale;
        this.left = left;
        this.right = right;
        dragging = null;
        leave();
    }

    /**
     * O mouse no quadro, antes do desenho: acha a anotação sob ele na cena em que está (a conta só roda se o ponto ou
     * a vista mudou) e tira o realce da cena de antes.
     */
    void hover(double mouseX, double mouseY) {
        GuideInk.Scene s = at(mouseX, mouseY);
        if (s != hovering) {
            leave();
            hovering = s;
        }
        if (s != null) {
            GuideScenes.hover(s, atX, atY);
        }
    }

    private void leave() {
        if (hovering != null) {
            hovering.notes().clearHover();
            hovering = null;
        }
    }

    /** A dica da anotação sob o mouse (fora de um arrasto); {@code true} se desenhou. */
    boolean tooltip(GuiGraphics g, Font font, int mouseX, int mouseY) {
        Component text = hovering == null || dragging != null ? null : hovering.notes().hoveredText();
        if (text == null) {
            return false;
        }
        g.renderTooltip(font, text, mouseX, mouseY);
        return true;
    }

    /** O botão esquerdo apertado: {@code true} se caiu numa cena (começa o arrasto, ou o clique duplo a volta). */
    boolean press(double mouseX, double mouseY) {
        dragging = null;
        GuideInk.Scene s = at(mouseX, mouseY);
        if (s == null) {
            return false;
        }
        long now = Util.getMillis();
        if (s == lastClicked && now - lastClickAt <= DOUBLE_CLICK_MS) {
            s.view().reset();
            lastClicked = null;
            return true;
        }
        lastClicked = s;
        lastClickAt = now;
        dragging = s;
        lastTexX = pressTexX = scale.texX(mouseX);
        lastTexY = pressTexY = scale.texY(mouseY);
        return true;
    }

    /** O mouse arrastado: gira a cena do arrasto pelo deslocamento em pixels de tela. */
    boolean drag(double mouseX, double mouseY) {
        if (dragging == null) {
            return false;
        }
        double tx = scale.texX(mouseX);
        double ty = scale.texY(mouseY);
        dragging.view().drag((tx - lastTexX) * scale.k(), (ty - lastTexY) * scale.k());
        lastTexX = tx;
        lastTexY = ty;
        if (Math.hypot(tx - pressTexX, ty - pressTexY) * scale.k() > DRAG_SLOP_PX) {
            lastClicked = null;
        }
        return true;
    }

    void release() {
        dragging = null;
    }

    /** A roda: {@code true} se o mouse está numa cena (o zoom dela muda e a folha não vira). */
    boolean scroll(double mouseX, double mouseY, double delta) {
        GuideInk.Scene s = at(mouseX, mouseY);
        if (s == null) {
            return false;
        }
        s.view().scroll(delta);
        return true;
    }

    private GuideInk.@Nullable Scene at(double mouseX, double mouseY) {
        double tx = scale.texX(mouseX);
        boolean onLeft = tx < BookScale.RIGHT_X - 4;
        atX = BookScale.unit(tx, onLeft ? BookScale.LEFT_X : BookScale.RIGHT_X);
        atY = BookScale.unit(scale.texY(mouseY), BookScale.CONTENT_Y);
        return find(onLeft ? left : right, atX, atY);
    }

    private static GuideInk.@Nullable Scene find(List<Ink> inks, double ux, double uy) {
        for (int i = 0; i < inks.size(); i++) {
            if (inks.get(i) instanceof GuideInk.Scene s && GuidePainter.inside(ux, uy, s.x(), s.y(), s.w(), s.h())) {
                return s;
            }
        }
        return null;
    }

    // ---- ganchos do e2e

    /** A vista da primeira cena à vista (a da folha esquerda antes), ou {@code null}. */
    public @Nullable SceneView firstView() {
        GuideInk.Scene s = first();
        return s == null ? null : s.view();
    }

    /** O centro, em coordenadas de interface, da primeira cena à vista, ou {@code null}. */
    public double @Nullable [] firstCenter() {
        for (int side = 0; side < 2; side++) {
            for (Ink ink : side == 0 ? left : right) {
                if (ink instanceof GuideInk.Scene s) {
                    int originX = side == 0 ? BookScale.LEFT_X : BookScale.RIGHT_X;
                    return new double[] {scale.guiX(originX + (s.x() + s.w() / 2.0) / 2),
                            scale.guiY(BookScale.CONTENT_Y + (s.y() + s.h() / 2.0) / 2)};
                }
            }
        }
        return null;
    }

    /** O texto da anotação sob o mouse, ou {@code null}. */
    public @Nullable String hoveredNote() {
        Component text = hovering == null ? null : hovering.notes().hoveredText();
        return text == null ? null : text.getString();
    }

    /**
     * O centro projetado, em coordenadas de interface, do bloco anotado com o texto {@code label} na primeira cena à
     * vista, ou {@code null} (sem cena, sem a anotação ou antes do primeiro desenho).
     */
    public double @Nullable [] noteCenter(String label) {
        for (int side = 0; side < 2; side++) {
            for (Ink ink : side == 0 ? left : right) {
                if (ink instanceof GuideInk.Scene s) {
                    float[] c = s.notes().boxCenter(label);
                    int originX = side == 0 ? BookScale.LEFT_X : BookScale.RIGHT_X;
                    return c == null ? null : new double[] {scale.guiX(originX + c[0] / 2),
                            scale.guiY(BookScale.CONTENT_Y + c[1] / 2)};
                }
            }
        }
        return null;
    }

    private GuideInk.@Nullable Scene first() {
        for (List<Ink> inks : List.of(left, right)) {
            for (Ink ink : inks) {
                if (ink instanceof GuideInk.Scene s) {
                    return s;
                }
            }
        }
        return null;
    }
}
