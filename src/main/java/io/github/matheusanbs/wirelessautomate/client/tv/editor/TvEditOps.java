package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * As operações do editor sobre a lista de peças (a ordem é a de desenho: a última fica em cima), como no mockup
 * aprovado: escolher pelo clique, inserir no primeiro lugar livre, mover, redimensionar pela alça, remover, duplicar,
 * subir e descer camada, mudar a camada pela lista e agrupar. Cada uma devolve uma lista nova e não muda a de
 * entrada. Pura, com JUnit: a peça é genérica e lida pelo {@link Access}; o cliente passa a do layout. Só em
 * eventos (clique, arrasto, tecla), nunca por quadro.
 */
public final class TvEditOps {
    private TvEditOps() {
    }

    /** Como as operações leem e trocam uma peça. */
    public interface Access<T> {
        int x(T piece);

        int y(T piece);

        int width(T piece);

        int height(T piece);

        /** O grupo da peça; 0 é solta. */
        int group(T piece);

        boolean hidden(T piece);

        /** Se a peça ocupa lugar para o {@link #freeSpot} (a Forma é fundo e não ocupa). */
        boolean occupies(T piece);

        T withBox(T piece, int x, int y, int width, int height);

        T withGroup(T piece, int group);
    }

    /** O resultado de uma operação: as peças e a escolha depois dela. */
    public record Edit<T>(List<T> pieces, TvSelection selection) {
    }

    /** A escolha depois de um clique e o grupo em que se entrou (0 nenhum). */
    public record Pick(TvSelection selection, int focusGroup) {
    }

    /** A peça visível mais de cima que contém o ponto, ou -1. */
    public static <T> int hit(Access<T> a, List<T> pieces, double x, double y) {
        for (int i = pieces.size() - 1; i >= 0; i--) {
            T p = pieces.get(i);
            if (!a.hidden(p) && x >= a.x(p) && x < a.x(p) + a.width(p) && y >= a.y(p) && y < a.y(p) + a.height(p)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * O clique do mockup: no vazio solta tudo; com Shift soma ou tira a peça; dois cliques (ou um clique dentro do
     * grupo em que se entrou) escolhem só a peça e entram no grupo dela; numa peça fora da escolha, escolhe o grupo
     * inteiro (ou só ela, se é solta); numa peça já escolhida, mantém a escolha (para arrastar todas).
     */
    public static <T> Pick click(Access<T> a, List<T> pieces, TvSelection selection, int focusGroup, int hit,
            boolean shift, boolean twice) {
        if (hit < 0) {
            return new Pick(TvSelection.EMPTY, 0);
        }
        int group = a.group(pieces.get(hit));
        if (shift) {
            return new Pick(selection.toggle(hit), focusGroup);
        }
        if (twice || focusGroup != 0 && group == focusGroup) {
            return new Pick(TvSelection.of(hit), group);
        }
        if (!selection.contains(hit)) {
            return group != 0 ? new Pick(members(a, pieces, group), 0) : new Pick(TvSelection.of(hit), 0);
        }
        return new Pick(selection, focusGroup);
    }

    /** As peças do grupo {@code group}. */
    public static <T> TvSelection members(Access<T> a, List<T> pieces, int group) {
        int count = 0;
        for (T p : pieces) {
            if (a.group(p) == group) {
                count++;
            }
        }
        int[] out = new int[count];
        int k = 0;
        for (int i = 0; i < pieces.size(); i++) {
            if (a.group(pieces.get(i)) == group) {
                out[k++] = i;
            }
        }
        return TvSelection.of(out);
    }

    /** O menor grupo livre: um a mais que o maior em uso. */
    public static <T> int nextGroup(Access<T> a, List<T> pieces) {
        int max = 0;
        for (T p : pieces) {
            max = Math.max(max, a.group(p));
        }
        return max + 1;
    }

    /** A caixa que cobre a escolha: {x0, y0, x1, y1}; vazia dá zeros. */
    public static <T> int[] bounds(Access<T> a, List<T> pieces, TvSelection selection) {
        if (selection.isEmpty()) {
            return new int[4];
        }
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        for (int k = 0; k < selection.size(); k++) {
            T p = pieces.get(selection.get(k));
            x0 = Math.min(x0, a.x(p));
            y0 = Math.min(y0, a.y(p));
            x1 = Math.max(x1, a.x(p) + a.width(p));
            y1 = Math.max(y1, a.y(p) + a.height(p));
        }
        return new int[] {x0, y0, x1, y1};
    }

    /** A alça vale para uma peça só, ou para um grupo escolhido inteiro (redimensionado em proporção). */
    public static <T> boolean resizable(Access<T> a, List<T> pieces, TvSelection selection) {
        if (selection.size() == 1) {
            return true;
        }
        if (selection.isEmpty()) {
            return false;
        }
        int group = a.group(pieces.get(selection.first()));
        return group != 0 && members(a, pieces, group).equals(selection);
    }

    /**
     * O primeiro lugar livre na grade, de cima para baixo e da esquerda para a direita, para uma caixa
     * {@code w} × {@code h} já dentro da área: livre é não cruzar nenhuma peça visível que ocupa lugar. Sem lugar,
     * o canto. Pula logo para a direita da peça que bate, para não testar casa a casa.
     */
    public static <T> int[] freeSpot(Access<T> a, List<T> pieces, int w, int h, int areaW, int areaH) {
        for (int y = 0; y <= areaH - h; y += TvSnap.GRID) {
            int x = 0;
            while (x <= areaW - w) {
                int blockedRight = -1;
                for (T o : pieces) {
                    if (!a.hidden(o) && a.occupies(o) && x < a.x(o) + a.width(o) && x + w > a.x(o)
                            && y < a.y(o) + a.height(o) && y + h > a.y(o)) {
                        blockedRight = Math.max(blockedRight, a.x(o) + a.width(o));
                    }
                }
                if (blockedRight < 0) {
                    return new int[] {x, y};
                }
                x = Math.max(x + TvSnap.GRID, TvSnap.ceil(blockedRight));
            }
        }
        return new int[] {0, 0};
    }

    /** Põe a peça nova em cima de todas, no primeiro lugar livre e no tamanho que cabe na área; escolhe só ela. */
    public static <T> Edit<T> insert(Access<T> a, List<T> pieces, T piece, int areaW, int areaH) {
        int w = Math.min(a.width(piece), areaW);
        int h = Math.min(a.height(piece), areaH);
        int[] spot = freeSpot(a, pieces, w, h, areaW, areaH);
        List<T> out = new ArrayList<>(pieces.size() + 1);
        out.addAll(pieces);
        out.add(a.withBox(piece, spot[0], spot[1], w, h));
        return new Edit<>(out, TvSelection.of(out.size() - 1));
    }

    /** As peças escolhidas de {@code original} andam {@code dx}, {@code dy}, limitados para a escolha não sair da área. */
    public static <T> List<T> move(Access<T> a, List<T> original, TvSelection selection, int dx, int dy, int areaW,
            int areaH) {
        int[] b = bounds(a, original, selection);
        int cx = TvSnap.moveDelta(dx, b[0], b[2], areaW);
        int cy = TvSnap.moveDelta(dy, b[1], b[3], areaH);
        List<T> out = new ArrayList<>(original);
        for (int k = 0; k < selection.size(); k++) {
            int i = selection.get(k);
            T p = original.get(i);
            out.set(i, a.withBox(p, a.x(p) + cx, a.y(p) + cy, a.width(p), a.height(p)));
        }
        return out;
    }

    /**
     * A alça puxada {@code dw}, {@code dh} a partir de {@code original}: numa peça só, o lado novo pelo
     * {@link TvSnap#size}; num grupo, a caixa do grupo muda assim e cada peça acompanha em proporção, com as bordas
     * arredondadas (no mínimo 1 px).
     */
    public static <T> List<T> resize(Access<T> a, List<T> original, TvSelection selection, int dw, int dh,
            int areaW, int areaH) {
        int[] b = bounds(a, original, selection);
        int bw = b[2] - b[0];
        int bh = b[3] - b[1];
        int nw = TvSnap.size(bw, dw, b[0], areaW);
        int nh = TvSnap.size(bh, dh, b[1], areaH);
        List<T> out = new ArrayList<>(original);
        for (int k = 0; k < selection.size(); k++) {
            int i = selection.get(k);
            T p = original.get(i);
            int left = scale(a.x(p) - b[0], nw, bw);
            int right = scale(a.x(p) + a.width(p) - b[0], nw, bw);
            int top = scale(a.y(p) - b[1], nh, bh);
            int bottom = scale(a.y(p) + a.height(p) - b[1], nh, bh);
            out.set(i, a.withBox(p, b[0] + left, b[1] + top, Math.max(1, right - left), Math.max(1, bottom - top)));
        }
        return out;
    }

    private static int scale(int offset, int size, int original) {
        return original == 0 ? 0 : (int) Math.round(offset * (double) size / original);
    }

    /** Tira as escolhidas; a escolha fica vazia. */
    public static <T> Edit<T> remove(List<T> pieces, TvSelection selection) {
        List<T> out = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            if (!selection.contains(i)) {
                out.add(pieces.get(i));
            }
        }
        return new Edit<>(out, TvSelection.EMPTY);
    }

    /**
     * Cópias das escolhidas em cima de todas, {@link TvSnap#GRID} px para a direita e para baixo (sem sair da área),
     * cada grupo copiado num grupo novo; escolhe as cópias.
     */
    public static <T> Edit<T> duplicate(Access<T> a, List<T> pieces, TvSelection selection, int areaW, int areaH) {
        List<T> out = new ArrayList<>(pieces.size() + selection.size());
        out.addAll(pieces);
        int next = nextGroup(a, pieces);
        int[] from = new int[selection.size()];
        int[] to = new int[selection.size()];
        int mapped = 0;
        for (int k = 0; k < selection.size(); k++) {
            T p = pieces.get(selection.get(k));
            int group = a.group(p);
            int copyGroup = 0;
            if (group != 0) {
                for (int m = 0; m < mapped && copyGroup == 0; m++) {
                    if (from[m] == group) {
                        copyGroup = to[m];
                    }
                }
                if (copyGroup == 0) {
                    from[mapped] = group;
                    to[mapped++] = next;
                    copyGroup = next++;
                }
            }
            int x = Math.max(0, Math.min(areaW - a.width(p), a.x(p) + TvSnap.GRID));
            int y = Math.max(0, Math.min(areaH - a.height(p), a.y(p) + TvSnap.GRID));
            out.add(a.withGroup(a.withBox(p, x, y, a.width(p), a.height(p)), copyGroup));
        }
        return new Edit<>(out, TvSelection.range(pieces.size(), out.size()));
    }

    /** Cada escolhida sobe uma camada, trocando com a de cima se ela não está escolhida. */
    public static <T> Edit<T> raise(List<T> pieces, TvSelection selection) {
        List<T> out = new ArrayList<>(pieces);
        int[] chosen = flags(selection, pieces.size());
        for (int i = pieces.size() - 2; i >= 0; i--) {
            if (chosen[i] == 1 && chosen[i + 1] == 0) {
                swap(out, chosen, i, i + 1);
            }
        }
        return new Edit<>(out, selectionOf(chosen));
    }

    /** Cada escolhida desce uma camada, trocando com a de baixo se ela não está escolhida. */
    public static <T> Edit<T> lower(List<T> pieces, TvSelection selection) {
        List<T> out = new ArrayList<>(pieces);
        int[] chosen = flags(selection, pieces.size());
        for (int i = 1; i < pieces.size(); i++) {
            if (chosen[i] == 1 && chosen[i - 1] == 0) {
                swap(out, chosen, i, i - 1);
            }
        }
        return new Edit<>(out, selectionOf(chosen));
    }

    /**
     * Leva as peças de {@code block} (em ordem) para a frente da posição {@code before} da lista original (0 é embaixo
     * de todas, o tamanho é em cima de todas): a lista de Camadas arrastada. Escolhe as levadas.
     */
    public static <T> Edit<T> moveTo(List<T> pieces, TvSelection block, int before) {
        List<T> rest = new ArrayList<>(pieces.size());
        List<T> moved = new ArrayList<>(block.size());
        int below = 0;
        for (int i = 0; i < pieces.size(); i++) {
            if (block.contains(i)) {
                moved.add(pieces.get(i));
                if (i < before) {
                    below++;
                }
            } else {
                rest.add(pieces.get(i));
            }
        }
        int at = Math.max(0, Math.min(rest.size(), before - below));
        rest.addAll(at, moved);
        return new Edit<>(rest, TvSelection.range(at, at + moved.size()));
    }

    /**
     * O Agrupar do mockup: as escolhidas (duas ou mais) ganham um grupo novo e passam a ficar juntas na lista, na
     * camada da mais alta. Com menos de duas, nada muda.
     */
    public static <T> Edit<T> group(Access<T> a, List<T> pieces, TvSelection selection) {
        if (selection.size() < 2) {
            return new Edit<>(pieces, selection);
        }
        int group = nextGroup(a, pieces);
        List<T> rest = new ArrayList<>(pieces.size());
        List<T> moved = new ArrayList<>(selection.size());
        for (int i = 0; i < pieces.size(); i++) {
            if (selection.contains(i)) {
                moved.add(a.withGroup(pieces.get(i), group));
            } else {
                rest.add(pieces.get(i));
            }
        }
        int at = Math.min(selection.last() - selection.size() + 1, rest.size());
        rest.addAll(at, moved);
        return new Edit<>(rest, TvSelection.range(at, at + moved.size()));
    }

    /** As escolhidas saem do grupo; a ordem não muda. */
    public static <T> Edit<T> ungroup(Access<T> a, List<T> pieces, TvSelection selection) {
        List<T> out = new ArrayList<>(pieces);
        for (int k = 0; k < selection.size(); k++) {
            int i = selection.get(k);
            out.set(i, a.withGroup(pieces.get(i), 0));
        }
        return new Edit<>(out, selection);
    }

    /** Se alguma escolhida está num grupo (o Desagrupar vale). */
    public static <T> boolean anyGrouped(Access<T> a, List<T> pieces, TvSelection selection) {
        for (int k = 0; k < selection.size(); k++) {
            if (a.group(pieces.get(selection.get(k))) != 0) {
                return true;
            }
        }
        return false;
    }

    private static int[] flags(TvSelection selection, int size) {
        int[] chosen = new int[size];
        for (int k = 0; k < selection.size(); k++) {
            if (selection.get(k) < size) {
                chosen[selection.get(k)] = 1;
            }
        }
        return chosen;
    }

    private static <T> void swap(List<T> list, int[] chosen, int i, int j) {
        T t = list.get(i);
        list.set(i, list.get(j));
        list.set(j, t);
        int c = chosen[i];
        chosen[i] = chosen[j];
        chosen[j] = c;
    }

    private static TvSelection selectionOf(int[] chosen) {
        int count = 0;
        for (int c : chosen) {
            count += c;
        }
        int[] out = new int[count];
        int k = 0;
        for (int i = 0; i < chosen.length; i++) {
            if (chosen[i] == 1) {
                out[k++] = i;
            }
        }
        return TvSelection.of(out);
    }
}
