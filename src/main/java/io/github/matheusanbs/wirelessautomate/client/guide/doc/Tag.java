package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import java.util.LinkedHashMap;
import java.util.Map;

/** Uma tag no estilo JSX ({@code <Nome a="1" b={true} />}); {@code end} é o índice logo depois do {@code >}. */
record Tag(String name, Map<String, String> attrs, boolean closing, boolean selfClosing, int end) {
    /** Lê a tag que começa em {@code from} (um {@code <}); {@code null} se não for uma tag bem formada. */
    static Tag read(String s, int from) {
        int n = s.length();
        int i = from + 1;
        if (i >= n) {
            return null;
        }
        boolean closing = s.charAt(i) == '/';
        if (closing) {
            i++;
        }
        int nameStart = i;
        while (i < n && Character.isLetterOrDigit(s.charAt(i))) {
            i++;
        }
        if (i == nameStart || !Character.isLetter(s.charAt(nameStart))) {
            return null;
        }
        String name = s.substring(nameStart, i);
        Map<String, String> attrs = new LinkedHashMap<>();
        while (i < n) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t') {
                i++;
            } else if (c == '>') {
                return new Tag(name, attrs, closing, false, i + 1);
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '>') {
                return new Tag(name, attrs, closing, true, i + 2);
            } else {
                int keyStart = i;
                while (i < n && " \t=/>".indexOf(s.charAt(i)) < 0) {
                    i++;
                }
                if (i == keyStart) {
                    return null;
                }
                String key = s.substring(keyStart, i);
                String value = "";
                if (i < n && s.charAt(i) == '=') {
                    int valueEnd = valueEnd(s, i + 1);
                    if (valueEnd < 0) {
                        return null;
                    }
                    value = s.substring(i + 2, valueEnd);
                    i = valueEnd + 1;
                }
                attrs.put(key, value);
            }
        }
        return null;
    }

    /** O índice do delimitador que fecha o valor aberto em {@code open}: aspas duplas, simples ou chaves. */
    private static int valueEnd(String s, int open) {
        if (open >= s.length()) {
            return -1;
        }
        char q = s.charAt(open);
        if (q == '"' || q == '\'') {
            return s.indexOf(q, open + 1);
        }
        if (q == '{') {
            int depth = 0;
            for (int j = open; j < s.length(); j++) {
                char d = s.charAt(j);
                if (d == '{') {
                    depth++;
                } else if (d == '}' && --depth == 0) {
                    return j;
                }
            }
        }
        return -1;
    }

    String get(String key) {
        return attrs.get(key);
    }

    /** O número do atributo; ausente ou ilegível dá {@code def} (o ilegível também avisa). */
    double num(String key, double def, Source src) {
        String v = attrs.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Double.parseDouble(v.strip());
        } catch (NumberFormatException e) {
            src.warn("atributo " + key + " de <" + name + "> não é um número: " + v);
            return def;
        }
    }

    boolean flag(String key) {
        return "true".equals(attrs.get(key));
    }

    /** Os atributos {@code p:...} (propriedades de bloco), sem o prefixo e na ordem do texto. */
    Map<String, String> props() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            if (e.getKey().startsWith("p:")) {
                out.put(e.getKey().substring(2), e.getValue());
            }
        }
        return out;
    }

    Flow flow(Source src) {
        String v = attrs.get("float");
        if (v == null) {
            return Flow.NONE;
        }
        return switch (v) {
            case "left" -> Flow.LEFT;
            case "right" -> Flow.RIGHT;
            default -> {
                src.warn("float desconhecido em <" + name + ">: " + v);
                yield Flow.NONE;
            }
        };
    }
}
