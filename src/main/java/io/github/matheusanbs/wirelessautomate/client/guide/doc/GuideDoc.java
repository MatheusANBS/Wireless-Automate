package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import java.util.List;

/** Uma página lida: o cabeçalho, o corpo e os avisos do que o parser pulou ou não entendeu. */
public record GuideDoc(GuideHeader header, List<Block> blocks, List<String> warnings) {
    public GuideDoc {
        blocks = List.copyOf(blocks);
        warnings = List.copyOf(warnings);
    }

    public String title() {
        return header.title();
    }

    public String group() {
        return header.group();
    }
}
