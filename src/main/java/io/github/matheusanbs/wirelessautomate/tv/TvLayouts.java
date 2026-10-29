package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

/**
 * Os layouts prontos da Tela inteira: Energia, Armazenamento, Rede e Relógio. Cada um é montado em proporção à área
 * (pixels de desenho, divisões inteiras) com as cores por papel do tema, para valer nos quatro temas e nos do
 * jogador. Lógica pura: o dado entra por parâmetro (a rede e o armazenamento ligado que o editor achou, ou
 * {@code null}, e as peças ficam sem ligação para o jogador ligar na aba Dado) e o texto vem de quem chama.
 */
public final class TvLayouts {
    /** Os layouts da lista do editor, na ordem do seletor. */
    public enum Kind {
        ENERGY("energy"),
        STORAGE("storage"),
        NETWORK("network"),
        CLOCK("clock");

        public final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    /** Os textos escritos nas peças, no idioma de quem aplica: {@code title} e as chaves de tipo de recurso. */
    public interface Text {
        String of(String key);
    }

    private TvLayouts() {
    }

    /** O armazenamento a mostrar: onde fica e o tipo de recurso dele. */
    public record Storage(GlobalPos pos, ResourceType type) {
    }

    public static List<TvPiece> build(Kind kind, TvArea area, Text text, @Nullable UUID network,
            @Nullable Storage storage, List<ResourceType> types) {
        List<TvPiece> pieces = switch (kind) {
            case ENERGY -> energy(area, text, network);
            case STORAGE -> storage(area, text, storage);
            case NETWORK -> network(area, text, network, types);
            case CLOCK -> clock(area, text);
        };
        return fitted(pieces, area);
    }

    /** Em Telas muito pequenas os mínimos legíveis passam da área: encolhe o que sobra e larga o que ficou fora. */
    private static List<TvPiece> fitted(List<TvPiece> pieces, TvArea area) {
        int w = area.pixelWidth();
        int h = area.pixelHeight();
        List<TvPiece> out = new ArrayList<>(pieces.size());
        for (TvPiece p : pieces) {
            if (p.x() >= w || p.y() >= h) {
                continue;
            }
            int pw = Math.min(p.width(), w - p.x());
            int ph = Math.min(p.height(), h - p.y());
            out.add(pw == p.width() && ph == p.height() ? p : p.withBox(p.x(), p.y(), pw, ph));
        }
        return out;
    }

    // ------------------------------------------------------------------ Energia

    /**
     * O medidor do que está guardado nas máquinas em Armazém da rede, com o percentual no meio, e ao lado o que entra,
     * o que sai e o gráfico do que entra.
     */
    static List<TvPiece> energy(TvArea area, Text text, @Nullable UUID network) {
        int w = area.pixelWidth();
        int h = area.pixelHeight();
        int m = margin(w, h);
        int th = Math.max(7, h / 9);
        int top = m + th + m;
        int gs = Math.max(8, Math.min(h - top - m, (w - 3 * m) * 45 / 100));
        int gx = m;
        int gy = top + (h - top - m - gs) / 2;
        int x0 = gx + gs + m;
        int rw = Math.max(8, w - x0 - m);
        int rh = Math.max(6, gs * 27 / 100);
        List<TvPiece> out = new ArrayList<>();
        out.add(card(w, h, m));
        out.add(title(m, m, w - 2 * m, th, text.of("title.energy")));
        out.add(piece(TvPieceType.GAUGE, gx, gy, gs, gs, bind(network, ResourceType.ENERGY, TvMetric.HELD_FILL),
                TvFormat.DEFAULT, base(TvColor.HIGH, 0, 8, TvAlign.CENTER)));
        out.add(piece(TvPieceType.VALUE, gx + gs / 6, gy + gs * 2 / 5, gs * 2 / 3, Math.max(6, gs / 4),
                bind(network, ResourceType.ENERGY, TvMetric.HELD_FILL), new TvFormat(0, false, false, "", "",
                        TvUnit.NONE), base(TvColor.TEXT, 0, Math.max(6, gs / 4), TvAlign.CENTER)));
        int y = gy;
        out.add(flowRow(x0, y, rw, rh, network, ResourceType.ENERGY, TvMetric.IN, TvColor.HIGH, false));
        out.add(arrowOf(x0, y, rh, network, ResourceType.ENERGY, TvMetric.IN, false));
        y += rh;
        out.add(flowRow(x0, y, rw, rh, network, ResourceType.ENERGY, TvMetric.OUT, TvColor.LOW, true));
        out.add(arrowOf(x0, y, rh, network, ResourceType.ENERGY, TvMetric.OUT, true));
        y += rh;
        out.add(piece(TvPieceType.GRAPH, x0, y + 1, rw, Math.max(6, gy + gs - y - 1),
                bind(network, ResourceType.ENERGY, TvMetric.IN), TvFormat.DEFAULT,
                base(TvColor.HIGH, 0, 8, TvAlign.LEFT).withPart(TvPartStyle.DEFAULT.withArea(true))));
        return out;
    }

    // ------------------------------------------------------------------ Armazenamento

    /**
     * Um armazenamento ligado: o nome do tipo em cima, a barra de enchimento, o quanto tem e a capacidade, e embaixo o
     * que entra e o que sai.
     */
    static List<TvPiece> storage(TvArea area, Text text, @Nullable Storage storage) {
        int w = area.pixelWidth();
        int h = area.pixelHeight();
        int m = margin(w, h);
        int th = Math.max(7, h / 9);
        ResourceType type = storage == null ? ResourceType.ITEM : storage.type();
        int bh = Math.max(6, h / 7);
        int by = m + th + m;
        int vh = Math.max(8, h / 5);
        int vy = by + bh + m;
        int rh = Math.max(7, h / 8);
        int ry = h - m - rh;
        int half = (w - 2 * m) / 2;
        List<TvPiece> out = new ArrayList<>();
        out.add(card(w, h, m));
        out.add(title(m, m, w - 2 * m, th, text.of("title.storage")));
        out.add(piece(TvPieceType.BAR, m, by, w - 2 * m, bh, bound(storage, TvStorageMetric.FILL), TvFormat.DEFAULT,
                base(TvColor.HIGH, 0, 8, TvAlign.LEFT).withRadius(Math.max(1, bh / 4))));
        out.add(piece(TvPieceType.VALUE, m, vy, w - 2 * m - half / 2, vh, bound(storage, TvStorageMetric.STORED),
                compact(""), base(TvColor.TEXT, 0, vh, TvAlign.LEFT)));
        out.add(piece(TvPieceType.VALUE, m + half, vy + (vh - Math.max(6, vh * 2 / 3)), half, Math.max(6, vh * 2 / 3),
                bound(storage, TvStorageMetric.CAPACITY), compact("/ "),
                base(TvColor.MUTED, 0, Math.max(6, vh * 2 / 3), TvAlign.RIGHT)));
        out.add(storageFlow(m, ry, half - m / 2, rh, storage, type, TvStorageMetric.IN, TvColor.HIGH, false));
        out.add(arrowOf(m, ry, rh, storage, TvStorageMetric.IN, false));
        out.add(storageFlow(m + half + m / 2, ry, half - m / 2, rh, storage, type, TvStorageMetric.OUT, TvColor.LOW,
                true));
        out.add(arrowOf(m + half + m / 2, ry, rh, storage, TvStorageMetric.OUT, true));
        return out;
    }

    // ------------------------------------------------------------------ Rede

    /** Uma linha por tipo de recurso carregado (até quatro): o nome, o que entra e o que sai da rede. */
    static List<TvPiece> network(TvArea area, Text text, @Nullable UUID network, List<ResourceType> types) {
        int w = area.pixelWidth();
        int h = area.pixelHeight();
        int m = margin(w, h);
        int th = Math.max(7, h / 9);
        int count = Math.max(1, Math.min(4, types.size()));
        int top = m + th + m;
        int rh = Math.max(7, (h - top - m) / count);
        int nameW = (w - 2 * m) * 40 / 100;
        int colW = (w - 2 * m - nameW) / 2;
        List<TvPiece> out = new ArrayList<>();
        out.add(card(w, h, m));
        out.add(title(m, m, w - 2 * m, th, text.of("title.network")));
        for (int i = 0; i < Math.min(count, types.size()); i++) {
            ResourceType type = types.get(i);
            int y = top + i * rh;
            int size = Math.max(6, rh * 62 / 100);
            out.add(piece(TvPieceType.TEXT, m, y, nameW, rh, Optional.empty(), TvFormat.DEFAULT,
                    base(TvColor.MUTED, 0, size, TvAlign.LEFT).withText(text.of("type." + type.key()))));
            TvUnit unit = type.ratePerTick() ? TvUnit.TICK : TvUnit.SECOND;
            out.add(piece(TvPieceType.VALUE, m + nameW, y, colW, rh, bind(network, type, TvMetric.IN),
                    new TvFormat(1, true, false, "▲", "", unit), base(TvColor.HIGH, 0, size, TvAlign.LEFT)));
            out.add(piece(TvPieceType.VALUE, m + nameW + colW, y, colW, rh, bind(network, type, TvMetric.OUT),
                    new TvFormat(1, true, false, "▼", "", unit), base(TvColor.LOW, 0, size, TvAlign.LEFT)));
        }
        return out;
    }

    // ------------------------------------------------------------------ Relógio

    /** A hora do mundo grande no meio e o dia embaixo. */
    static List<TvPiece> clock(TvArea area, Text text) {
        int w = area.pixelWidth();
        int h = area.pixelHeight();
        int m = margin(w, h);
        int th = Math.max(7, h / 9);
        int big = Math.max(8, h * 38 / 100);
        int dayH = Math.max(6, h / 8);
        int by = (h - big - dayH - m) / 2 + th / 2;
        List<TvPiece> out = new ArrayList<>();
        out.add(card(w, h, m));
        out.add(title(m, m, w - 2 * m, th, text.of("title.clock")));
        out.add(piece(TvPieceType.VALUE, m, by, w - 2 * m, big, Optional.of(TvBinding.clock(TvClockMetric.TIME)),
                TvFormat.DEFAULT, base(TvColor.TEXT, 0, big, TvAlign.CENTER)));
        out.add(piece(TvPieceType.VALUE, m, by + big + m, w - 2 * m, dayH,
                Optional.of(TvBinding.clock(TvClockMetric.DAY)),
                new TvFormat(0, false, false, text.of("day") + " ", "", TvUnit.NONE),
                base(TvColor.MUTED, 0, dayH, TvAlign.CENTER)));
        return out;
    }

    // ------------------------------------------------------------------ peças comuns

    private static int margin(int w, int h) {
        return Math.max(2, Math.min(w, h) / 14);
    }

    private static TvPiece card(int w, int h, int m) {
        return piece(TvPieceType.SHAPE, 0, 0, w, h, Optional.empty(), TvFormat.DEFAULT,
                new TvStyle(TvColor.theme(TvColor.TEXT), TvColor.theme(TvColor.CARD), TvColor.theme(TvColor.MUTED), 1,
                        8, TvAlign.LEFT, true, "", Math.max(2, m), 100, TvFont.MINECRAFT));
    }

    private static TvPiece title(int x, int y, int w, int h, String text) {
        return piece(TvPieceType.TEXT, x, y, w, h, Optional.empty(), TvFormat.DEFAULT,
                base(TvColor.MUTED, 0, h, TvAlign.LEFT).withText(text));
    }

    private static TvPiece flowRow(int x, int y, int w, int h, @Nullable UUID network, ResourceType type,
            TvMetric metric, int role, boolean reverse) {
        TvUnit unit = type.ratePerTick() ? TvUnit.TICK : TvUnit.SECOND;
        int size = Math.max(6, h * 7 / 10);
        return piece(TvPieceType.VALUE, x + h, y, w - h, h, bind(network, type, metric),
                new TvFormat(1, true, false, "", "", unit), base(role, 0, size, TvAlign.LEFT));
    }

    private static TvPiece arrowOf(int x, int y, int h, @Nullable UUID network, ResourceType type, TvMetric metric,
            boolean reverse) {
        return piece(TvPieceType.ARROW, x, y + h / 8, h * 7 / 10, h * 3 / 4, bind(network, type, metric),
                TvFormat.DEFAULT, base(TvColor.HIGH, 0, 8, TvAlign.LEFT)
                        .withPart(TvPartStyle.DEFAULT.withReverse(reverse)));
    }

    private static TvPiece storageFlow(int x, int y, int w, int h, @Nullable Storage storage, ResourceType type,
            TvStorageMetric metric, int role, boolean reverse) {
        int size = Math.max(6, h * 7 / 10);
        return piece(TvPieceType.VALUE, x + h, y, w - h, h, bound(storage, metric),
                new TvFormat(1, true, false, "", "", type.ratePerTick() ? TvUnit.TICK : TvUnit.SECOND),
                base(role, 0, size, TvAlign.LEFT));
    }

    private static TvPiece arrowOf(int x, int y, int h, @Nullable Storage storage, TvStorageMetric metric,
            boolean reverse) {
        return piece(TvPieceType.ARROW, x, y + h / 8, h * 7 / 10, h * 3 / 4, bound(storage, metric), TvFormat.DEFAULT,
                base(TvColor.HIGH, 0, 8, TvAlign.LEFT).withPart(TvPartStyle.DEFAULT.withReverse(reverse)));
    }

    private static TvFormat compact(String prefix) {
        return new TvFormat(1, true, false, prefix, "", TvUnit.NONE);
    }

    private static TvStyle base(int colorRole, int backgroundRole, int size, TvAlign align) {
        return new TvStyle(TvColor.theme(colorRole), backgroundRole == 0 ? 0 : TvColor.theme(backgroundRole), 0, 0, size,
                align, true, "");
    }

    private static TvPiece piece(TvPieceType type, int x, int y, int w, int h, Optional<TvBinding> binding,
            TvFormat format, TvStyle style) {
        return new TvPiece(type, x, y, Math.max(1, w), Math.max(1, h), 0, "", binding, format, style);
    }

    private static Optional<TvBinding> bind(@Nullable UUID network, ResourceType type, TvMetric metric) {
        return network == null ? Optional.empty() : Optional.of(new TvBinding(network, type, metric));
    }

    private static Optional<TvBinding> bound(@Nullable Storage storage, TvStorageMetric metric) {
        return storage == null ? Optional.empty()
                : Optional.of(TvBinding.storage(storage.type(), storage.pos(), metric, Optional.empty()));
    }
}
