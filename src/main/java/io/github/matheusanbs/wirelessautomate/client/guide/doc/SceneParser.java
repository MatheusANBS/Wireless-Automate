package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import java.util.ArrayList;
import java.util.List;

/** Lê o interior de um {@code <GameScene>}: blocos, anotações e a câmera, uma tag por linha. */
final class SceneParser {
    private static final String CLOSE = "</GameScene";

    private final Source src;
    private final List<SceneBlock> blocks = new ArrayList<>();
    private final List<SceneNote> notes = new ArrayList<>();
    private SceneCamera camera = SceneCamera.DEFAULT;

    private SceneParser(Source src) {
        this.src = src;
    }

    /** {@code open} é a tag {@code <GameScene>} já lida; consome as linhas até o {@code </GameScene>}. */
    static Block.Scene parse(Tag open, Source src) {
        SceneParser p = new SceneParser(src);
        if (!open.selfClosing()) {
            p.readBody();
        }
        return new Block.Scene(p.blocks, p.notes, p.camera, open.num("zoom", 1, src), open.flag("interactive"));
    }

    private void readBody() {
        while (src.hasNext()) {
            String t = src.next().strip();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith(CLOSE)) {
                return;
            }
            Tag tag = t.startsWith("<") ? Tag.read(t, 0) : null;
            if (tag == null || tag.closing()) {
                src.warn("linha inesperada na cena: " + t);
                continue;
            }
            switch (tag.name()) {
                case "Block" -> block(tag);
                case "IsometricCamera" ->
                    camera = new SceneCamera(tag.num("yaw", SceneCamera.DEFAULT.yaw(), src),
                            tag.num("pitch", SceneCamera.DEFAULT.pitch(), src));
                case "BlockAnnotation" -> blockNote(tag, text(t, tag));
                case "LineAnnotation" -> lineNote(tag, text(t, tag));
                default -> src.warn("tag desconhecida na cena: <" + tag.name() + ">");
            }
        }
        src.warn("o <GameScene> não fecha");
    }

    private void block(Tag tag) {
        String id = tag.get("id");
        if (id == null) {
            src.warn("<Block> sem id");
            return;
        }
        blocks.add(new SceneBlock(id, (int) tag.num("x", 0, src), (int) tag.num("y", 0, src),
                (int) tag.num("z", 0, src), tag.props()));
    }

    private void blockNote(Tag tag, List<Span> text) {
        notes.add(new SceneNote.OnBlock((int) tag.num("x", 0, src), (int) tag.num("y", 0, src),
                (int) tag.num("z", 0, src), color(tag), text));
    }

    private void lineNote(Tag tag, List<Span> text) {
        SceneNote.Vec from = vec(tag, "from");
        SceneNote.Vec to = vec(tag, "to");
        if (from == null || to == null) {
            src.warn("<LineAnnotation> sem from/to legíveis");
            return;
        }
        notes.add(new SceneNote.OnLine(from, to, color(tag), tag.num("thickness", 0.05, src), text));
    }

    private static String color(Tag tag) {
        String c = tag.get("color");
        return c == null ? "" : c;
    }

    /** "x y z" com três números; {@code null} se faltar ou não for número. */
    private static SceneNote.Vec vec(Tag tag, String key) {
        String v = tag.get(key);
        if (v == null) {
            return null;
        }
        String[] parts = v.strip().split(" +");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new SceneNote.Vec(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** O texto entre a tag e o fechamento dela, na mesma linha ou nas seguintes, já lido em linha. */
    private List<Span> text(String firstLine, Tag tag) {
        if (tag.selfClosing()) {
            return List.of();
        }
        String close = "</" + tag.name() + ">";
        String rest = firstLine.substring(tag.end());
        StringBuilder sb = new StringBuilder();
        boolean closed = false;
        while (true) {
            int idx = rest.indexOf(close);
            if (idx >= 0) {
                sb.append(rest.substring(0, idx).strip());
                closed = true;
                break;
            }
            sb.append(rest.strip()).append(' ');
            if (!src.hasNext()) {
                break;
            }
            rest = src.next();
        }
        if (!closed) {
            src.warn("<" + tag.name() + "> não fecha");
        }
        return InlineParser.parse(sb.toString().strip(), src);
    }
}
