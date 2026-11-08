package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import java.util.ArrayList;
import java.util.List;

/** Lê o cabeçalho {@code ---} no topo: um YAML simples, à mão (título, ícone, pai, posição, grupo, item_ids). */
final class HeaderParser {
    private String title = "";
    private String icon = "";
    private String parent = "";
    private int position = GuideHeader.NO_POSITION;
    private String group = "";
    private final List<String> itemIds = new ArrayList<>();
    private final Source src;

    private HeaderParser(Source src) {
        this.src = src;
    }

    /** Sem o bloco no topo, devolve {@link GuideHeader#EMPTY} (com um aviso) e não consome nada. */
    static GuideHeader parse(Source src) {
        String first = src.peek();
        if (first == null || !first.strip().equals("---")) {
            src.warn("a página não começa com o cabeçalho ---");
            return GuideHeader.EMPTY;
        }
        src.next();
        HeaderParser p = new HeaderParser(src);
        p.readLines();
        return new GuideHeader(p.title, p.icon, p.parent, p.position, p.group, p.itemIds);
    }

    private void readLines() {
        String section = "";
        while (src.hasNext()) {
            String line = src.next();
            String t = line.strip();
            if (t.equals("---")) {
                return;
            }
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("- ")) {
                if (section.equals("item_ids")) {
                    itemIds.add(unquote(t.substring(2)));
                } else {
                    src.warn("item de lista fora de item_ids no cabeçalho");
                }
                continue;
            }
            int colon = t.indexOf(':');
            if (colon <= 0) {
                src.warn("linha ilegível no cabeçalho: " + t);
                continue;
            }
            String key = t.substring(0, colon);
            String value = unquote(t.substring(colon + 1).strip());
            boolean nested = line.startsWith(" ") || line.startsWith("\t");
            if (nested) {
                set(section + "." + key, value);
            } else if (value.isEmpty()) {
                section = key;
            } else {
                section = "";
                set(key, value);
            }
        }
        src.warn("o cabeçalho ---  não fecha");
    }

    private void set(String path, String value) {
        switch (path) {
            case "navigation.title" -> title = value;
            case "navigation.icon" -> icon = value;
            case "navigation.parent" -> parent = value;
            case "navigation.position" -> position = number(value);
            case "group" -> group = value;
            default -> src.warn("chave desconhecida no cabeçalho: " + path);
        }
    }

    private int number(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            src.warn("position não é um número: " + value);
            return GuideHeader.NO_POSITION;
        }
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && (v.charAt(0) == '"' || v.charAt(0) == '\'') && v.charAt(v.length() - 1) == v.charAt(0)) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
