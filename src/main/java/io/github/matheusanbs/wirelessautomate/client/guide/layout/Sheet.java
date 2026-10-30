package io.github.matheusanbs.wirelessautomate.client.guide.layout;

import java.util.List;

/** Uma folha paginada: as operações de desenho, com as posições relativas ao canto da área de conteúdo. */
public record Sheet(List<SheetOp> ops, int usedHeight) {
    public Sheet {
        ops = List.copyOf(ops);
    }
}
