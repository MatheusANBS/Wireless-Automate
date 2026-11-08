package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import java.util.List;

/** Uma anotação da cena: o texto fica ao lado de um bloco ou de uma linha. */
public sealed interface SceneNote {
    List<Span> text();

    record Vec(double x, double y, double z) {}

    record OnBlock(int x, int y, int z, String color, List<Span> text) implements SceneNote {
        public OnBlock {
            text = List.copyOf(text);
        }
    }

    record OnLine(Vec from, Vec to, String color, double thickness, List<Span> text) implements SceneNote {
        public OnLine {
            text = List.copyOf(text);
        }
    }
}
