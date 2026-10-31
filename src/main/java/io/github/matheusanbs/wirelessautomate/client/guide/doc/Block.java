package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Um bloco do corpo de uma página: o que o parser lê e a paginação distribui pelas folhas. */
public sealed interface Block {
    record Heading(int level, List<Span> spans) implements Block {
        public Heading {
            spans = List.copyOf(spans);
        }
    }

    record Paragraph(List<Span> spans) implements Block {
        public Paragraph {
            spans = List.copyOf(spans);
        }
    }

    record ListBlock(boolean numbered, List<List<Span>> items) implements Block {
        public ListBlock {
            items = List.copyOf(items);
        }
    }

    /** {@code header} tem uma lista de trechos por coluna; cada linha de {@code rows}, uma por célula. */
    record Table(List<List<Span>> header, List<List<List<Span>>> rows) implements Block {
        public Table {
            header = List.copyOf(header);
            rows = List.copyOf(rows);
        }
    }

    record ItemImage(String id, double scale, Flow flow, String components) implements Block {}

    record BlockImage(String id, Map<String, String> props, double scale, Flow flow) implements Block {
        public BlockImage {
            props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
        }
    }

    record Row(int gap, List<Block> children) implements Block {
        public Row {
            children = List.copyOf(children);
        }
    }

    /** A receita do item {@code itemId}, as que existirem. */
    record RecipeFor(String itemId) implements Block {}

    /** Uma receita pelo id dela. */
    record Recipe(String recipeId) implements Block {}

    /** Uma imagem sozinha; {@code path} é relativo a {@code assets/wirelessautomate/guide/}. */
    record Image(String path) implements Block {}

    record Scene(List<SceneBlock> blocks, List<SceneNote> notes, SceneCamera camera, double zoom, boolean interactive)
            implements Block {
        public Scene {
            blocks = List.copyOf(blocks);
            notes = List.copyOf(notes);
        }
    }

    /** A lista das subpáginas da página; a tela decide o que mostrar. */
    record SubPages(boolean icons) implements Block {}

    /** O {@code <br clear="all" />}: o texto seguinte desce até passar das imagens flutuantes. */
    record Break() implements Block {}
}
