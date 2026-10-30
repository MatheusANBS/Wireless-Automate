package io.github.matheusanbs.wirelessautomate.client.guide;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.SceneNote;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.Span;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * As anotações de uma cena: o contorno de cada bloco anotado (as 12 arestas de uma caixa {@value #GROW} maior de cada
 * lado) e as linhas entre dois pontos, na cor da página (coral se faltar), por cima dos blocos. Montadas uma vez com a
 * cena (cores, caixas, linhas e o {@link Component} de cada texto). Os pontos são projetados para unidades da folha só
 * quando a vista muda; o desenho são faixas planas sobre essa projeção, sem profundidade (por cima de tudo, nítidas
 * em qualquer ângulo). A anotação sob o mouse é achada só quando o mouse se mexe ou a vista muda: primeiro os blocos
 * (o ponto numa das seis faces projetadas; o mais perto do olho ganha), depois as linhas (a até {@value #LINE_HIT}
 * unidades do segmento). As contas puras ficam no {@link SceneHit}.
 */
final class SceneNotes {
    /** Quanto a caixa do contorno passa do bloco de cada lado (0,02 maior no total). */
    static final float GROW = 0.01f;
    /** Distância máxima, em unidades da folha, do mouse à linha para a dica dela. */
    static final double LINE_HIT = 4;
    /** A largura do contorno, em pixels de tela; a anotação sob o mouse fica mais grossa e mais clara. */
    private static final float OUTLINE_PX = 2f;
    private static final float HOVER_PX = 3f;
    /** As linhas nunca ficam mais finas que isto, em pixels de tela. */
    private static final float MIN_LINE_PX = 1f;
    /** Rascunho da projeção; o desenho e a dica são só na linha de render. */
    private static final Vector3f P = new Vector3f();

    static final SceneNotes NONE = new SceneNotes(List.of());

    private final int boxes;
    private final int lines;
    /** O canto mínimo de cada bloco anotado (x, y, z). */
    private final int[] boxAt;
    /** As pontas de cada linha (x0, y0, z0, x1, y1, z1), em blocos. */
    private final float[] lineAt;
    /** A espessura de cada linha, em blocos. */
    private final float[] thickness;
    /** A cor e a cor realçada de cada anotação, blocos antes das linhas. */
    private final int[] color;
    private final int[] light;
    private final @Nullable Component[] text;

    /** Os 8 cantos projetados de cada caixa (x, y em unidades da folha) e a profundidade do centro. */
    private final float[] boxPts;
    private final float[] boxDepth;
    /** As pontas projetadas de cada linha (x0, y0, x1, y1). */
    private final float[] linePts;
    /** Unidades da folha por bloco, na vista projetada. */
    private float block;
    /** A versão da vista com que a projeção foi feita; -1 = nunca. */
    private int seen = -1;
    /** O ponto e a versão da vista da última procura, e o achado (blocos de 0, linhas depois; -1 = nada). */
    private double hoverX = Double.NaN;
    private double hoverY = Double.NaN;
    private int hoverSeen = -1;
    private int hovered = -1;

    private SceneNotes(List<SceneNote> notes) {
        int b = 0;
        for (SceneNote n : notes) {
            if (n instanceof SceneNote.OnBlock) {
                b++;
            }
        }
        boxes = b;
        lines = notes.size() - b;
        boxAt = new int[boxes * 3];
        lineAt = new float[lines * 6];
        thickness = new float[lines];
        color = new int[boxes + lines];
        light = new int[boxes + lines];
        text = new Component[boxes + lines];
        int bi = 0;
        int li = 0;
        for (SceneNote n : notes) {
            int slot;
            String c;
            if (n instanceof SceneNote.OnBlock o) {
                slot = bi;
                boxAt[bi * 3] = o.x();
                boxAt[bi * 3 + 1] = o.y();
                boxAt[bi * 3 + 2] = o.z();
                c = o.color();
                bi++;
            } else {
                SceneNote.OnLine o = (SceneNote.OnLine) n;
                slot = boxes + li;
                float[] v = {(float) o.from().x(), (float) o.from().y(), (float) o.from().z(), (float) o.to().x(),
                        (float) o.to().y(), (float) o.to().z()};
                System.arraycopy(v, 0, lineAt, li * 6, 6);
                thickness[li] = (float) o.thickness();
                c = o.color();
                li++;
            }
            color[slot] = SceneHit.color(c);
            light[slot] = SceneHit.lighter(color[slot]);
            text[slot] = text(n.text());
        }
        boxPts = new float[boxes * 16];
        boxDepth = new float[boxes];
        linePts = new float[lines * 4];
    }

    /** As anotações da cena, montadas uma vez. */
    static SceneNotes of(List<SceneNote> notes) {
        return notes.isEmpty() ? NONE : new SceneNotes(notes);
    }

    boolean isEmpty() {
        return boxes + lines == 0;
    }

    /** O texto da anotação numa linha só, com os espaços juntados; {@code null} se vazio (realça sem dica). */
    private static @Nullable Component text(List<Span> spans) {
        StringBuilder sb = new StringBuilder();
        for (Span s : spans) {
            switch (s) {
                case Span.Text t -> sb.append(t.text());
                case Span.Bold t -> sb.append(t.text());
                case Span.Code t -> sb.append(t.text());
                case Span.Link t -> sb.append(t.text());
                default -> {
                }
            }
        }
        String t = sb.toString().strip().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : Component.literal(t);
    }

    // ---- projeção e dica

    /**
     * Projeta caixas e linhas com a matriz da vista ({@code view}: blocos para unidades da folha), só quando a
     * {@code version} mudou; {@code blockUnits} é o tamanho de um bloco na folha.
     */
    void project(Matrix4f view, int version, float blockUnits) {
        if (seen == version) {
            return;
        }
        seen = version;
        block = blockUnits;
        for (int i = 0; i < boxes; i++) {
            float x = boxAt[i * 3] - GROW;
            float y = boxAt[i * 3 + 1] - GROW;
            float z = boxAt[i * 3 + 2] - GROW;
            float size = 1 + 2 * GROW;
            for (int c = 0; c < 8; c++) {
                view.transformPosition(x + ((c & 1) == 0 ? 0 : size), y + ((c & 2) == 0 ? 0 : size),
                        z + ((c & 4) == 0 ? 0 : size), P);
                boxPts[i * 16 + c * 2] = P.x;
                boxPts[i * 16 + c * 2 + 1] = P.y;
            }
            view.transformPosition(x + size / 2, y + size / 2, z + size / 2, P);
            boxDepth[i] = P.z;
        }
        for (int i = 0; i < lines; i++) {
            view.transformPosition(lineAt[i * 6], lineAt[i * 6 + 1], lineAt[i * 6 + 2], P);
            linePts[i * 4] = P.x;
            linePts[i * 4 + 1] = P.y;
            view.transformPosition(lineAt[i * 6 + 3], lineAt[i * 6 + 4], lineAt[i * 6 + 5], P);
            linePts[i * 4 + 2] = P.x;
            linePts[i * 4 + 3] = P.y;
        }
    }

    /** O mouse em ({@code ux}, {@code uy}), unidades da folha: acha a anotação sob ele se o ponto ou a vista mudou. */
    void hover(double ux, double uy) {
        if (ux == hoverX && uy == hoverY && hoverSeen == seen) {
            return;
        }
        hoverX = ux;
        hoverY = uy;
        hoverSeen = seen;
        hovered = -1;
        if (seen < 0) {
            return;
        }
        float best = -Float.MAX_VALUE;
        for (int i = 0; i < boxes; i++) {
            if (boxDepth[i] > best && SceneHit.inBox(boxPts, i * 16, ux, uy)) {
                best = boxDepth[i];
                hovered = i;
            }
        }
        if (hovered >= 0) {
            return;
        }
        double near = LINE_HIT;
        for (int i = 0; i < lines; i++) {
            int o = i * 4;
            double d = SceneHit.segment(ux, uy, linePts[o], linePts[o + 1], linePts[o + 2], linePts[o + 3]);
            if (d <= near) {
                near = d;
                hovered = boxes + i;
            }
        }
    }

    /** O mouse saiu da cena: nada realçado. */
    void clearHover() {
        hovered = -1;
        hoverX = Double.NaN;
    }

    /** O texto da anotação sob o mouse, ou {@code null}. */
    @Nullable Component hoveredText() {
        return hovered < 0 ? null : text[hovered];
    }

    // ---- desenho

    /**
     * Desenha as anotações projetadas como faixas planas na folha, por cima da cena; {@code unitsPerPx} converte as
     * larguras em pixels de tela. Sem alocação: só percorre os vetores da projeção.
     */
    void draw(GuiGraphics g, float unitsPerPx) {
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = g.pose().last().pose();
        for (int i = 0; i < boxes; i++) {
            boolean on = i == hovered;
            float half = (on ? HOVER_PX : OUTLINE_PX) * unitsPerPx / 2;
            int c = on ? light[i] : color[i];
            int o = i * 16;
            for (int e = 0; e < 24; e += 2) {
                int a = o + SceneHit.EDGES[e] * 2;
                int b = o + SceneHit.EDGES[e + 1] * 2;
                band(vc, m, boxPts[a], boxPts[a + 1], boxPts[b], boxPts[b + 1], half, c);
            }
        }
        for (int i = 0; i < lines; i++) {
            boolean on = boxes + i == hovered;
            float w = Math.max(thickness[i] * block, MIN_LINE_PX * unitsPerPx) + (on ? unitsPerPx : 0);
            int o = i * 4;
            band(vc, m, linePts[o], linePts[o + 1], linePts[o + 2], linePts[o + 3], w / 2,
                    on ? light[boxes + i] : color[boxes + i]);
        }
        g.flush();
    }

    /** Uma faixa de meia largura {@code half} de a até b, esticada nas pontas para os cantos fecharem. */
    private static void band(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by, float half,
            int c) {
        float dx = bx - ax;
        float dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) {
            return;
        }
        dx = dx / len * half;
        dy = dy / len * half;
        ax -= dx;
        ay -= dy;
        bx += dx;
        by += dy;
        vc.addVertex(m, ax - dy, ay + dx, 0).setColor(c);
        vc.addVertex(m, bx - dy, by + dx, 0).setColor(c);
        vc.addVertex(m, bx + dy, by - dx, 0).setColor(c);
        vc.addVertex(m, ax + dy, ay - dx, 0).setColor(c);
    }

    // ---- ganchos do e2e

    /** O centro projetado (unidades da folha) do primeiro bloco anotado com o texto; {@code null} antes da projeção. */
    float @Nullable [] boxCenter(String label) {
        if (seen < 0) {
            return null;
        }
        for (int i = 0; i < boxes; i++) {
            if (text[i] != null && text[i].getString().equals(label)) {
                float x = 0;
                float y = 0;
                for (int c = 0; c < 8; c++) {
                    x += boxPts[i * 16 + c * 2];
                    y += boxPts[i * 16 + c * 2 + 1];
                }
                return new float[] {x / 8, y / 8};
            }
        }
        return null;
    }
}
