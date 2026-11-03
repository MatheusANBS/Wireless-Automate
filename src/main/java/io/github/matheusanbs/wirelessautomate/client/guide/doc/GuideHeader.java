package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import java.util.List;

/** O cabeçalho {@code ---} de uma página. Campo ausente: texto vazio e {@link #NO_POSITION}. */
public record GuideHeader(String title, String icon, String parent, int position, String group, List<String> itemIds) {
    /** Sem {@code position}: a página fica depois das que têm. */
    public static final int NO_POSITION = Integer.MAX_VALUE;

    public static final GuideHeader EMPTY = new GuideHeader("", "", "", NO_POSITION, "", List.of());

    public GuideHeader {
        itemIds = List.copyOf(itemIds);
    }
}
