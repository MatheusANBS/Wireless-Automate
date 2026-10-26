package io.github.matheusanbs.wirelessautomate.filter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * O texto da condição de componente de uma {@link ItemRule}, lido e avaliado sem classes do Minecraft (testado
 * por JUnit). A forma é {@code id}, {@code id op valor} ou {@code id->caminho op valor}:
 * <ul>
 *   <li>{@code id}: o componente de qualquer mod ({@code apotheosis:purity}); sozinho, só exige que o item o tenha;</li>
 *   <li>{@code ->caminho}: um campo dentro do componente, por pontos ({@code minecraft:custom_data->genes.productivity});
 *       um número indexa uma lista e {@code *} vale para qualquer elemento dela;</li>
 *   <li>{@code op}: {@code =} (igual; o valor pode vir sem o namespace), {@code !=}, {@code >=}, {@code <=}, {@code >},
 *       {@code <} (números) e {@code ~} (contém).</li>
 * </ul>
 * O componente chega aqui já serializado numa árvore simples: {@link Map} (objeto), {@link List}, {@link String},
 * {@link Number} e {@link Boolean}. Um campo que não existe nunca casa, exceto no {@code !=} (é diferente).
 */
public record ComponentQuery(String id, List<String> path, Op op, @Nullable String value) {
    public enum Op {
        EXISTS("", ""), EQ("=", "="), NE("!=", "≠"), GE(">=", "≥"), LE("<=", "≤"), GT(">", ">"),
        LT("<", "<"), CONTAINS("~", "~");

        private final String token;
        private final String symbol;

        Op(String token, String symbol) {
            this.token = token;
            this.symbol = symbol;
        }

        /** O que se digita ({@code >=}). */
        public String token() {
            return token;
        }

        /** O que se lê ({@code ≥}). */
        public String symbol() {
            return symbol;
        }
    }

    /** O texto separado nas três partes, sem checar nada; as sugestões da tela leem daqui o que está sendo digitado. */
    public record Parts(String id, boolean arrow, String path, String op, String value) {
    }

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");
    private static final String OP_CHARS = "=!<>~";
    private static final int MAX_NODES = 4096;

    public ComponentQuery {
        path = List.copyOf(path);
    }

    /** Separa {@code text} em id, seta, caminho, operador e valor. */
    public static Parts parts(String text) {
        String t = text.strip();
        int arrow = t.indexOf("->");
        String id;
        String rest;
        if (arrow >= 0) {
            id = t.substring(0, arrow).strip();
            rest = t.substring(arrow + 2);
        } else {
            int p = firstOp(t);
            if (p < 0) {
                return new Parts(t, false, "", "", "");
            }
            id = t.substring(0, p).strip();
            rest = t.substring(p);
            return withOp(id, false, "", rest);
        }
        int q = firstOp(rest);
        if (q < 0) {
            return new Parts(id, true, rest.strip(), "", "");
        }
        return withOp(id, true, rest.substring(0, q).strip(), rest.substring(q));
    }

    private static Parts withOp(String id, boolean arrow, String path, String fromOp) {
        String op = fromOp.startsWith(">=") || fromOp.startsWith("<=") || fromOp.startsWith("!=")
                ? fromOp.substring(0, 2) : fromOp.substring(0, 1);
        return new Parts(id, arrow, path, op, fromOp.substring(op.length()).strip());
    }

    private static int firstOp(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (OP_CHARS.indexOf(text.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    /** As condições de {@code text}, separadas por {@code ;} (todas precisam valer), ou {@code null} se alguma está mal escrita. */
    public static @Nullable List<ComponentQuery> parseAll(String text) {
        List<ComponentQuery> out = new ArrayList<>();
        for (String piece : text.split(";", -1)) {
            // um ";" sobrando ainda está sendo digitado: vale como as condições que já estão escritas
            if (piece.isBlank()) {
                continue;
            }
            ComponentQuery query = parse(piece);
            if (query == null) {
                return null;
            }
            out.add(query);
        }
        return out.isEmpty() ? null : out;
    }

    /** A condição de {@code text}, ou {@code null} se está mal escrita (id inválido, caminho com ponto sobrando, operador sem valor). */
    public static @Nullable ComponentQuery parse(String text) {
        if (text.isBlank()) {
            return null;
        }
        Parts parts = parts(text);
        if (!ID.matcher(parts.id()).matches()) {
            return null;
        }
        List<String> path = new ArrayList<>();
        if (!parts.path().isEmpty()) {
            for (String segment : parts.path().split("\\.", -1)) {
                if (segment.isBlank()) {
                    return null;
                }
                path.add(segment.strip());
            }
        }
        Op op = opOf(parts.op());
        if (op == null) {
            return null;
        }
        String value = parts.value().isEmpty() ? null : parts.value().toLowerCase(Locale.ROOT);
        if (op != Op.EXISTS && value == null) {
            // "id=" e "id->campo=" ainda sem valor: só exige o componente ou o campo
            if (op != Op.EQ) {
                return null;
            }
            op = Op.EXISTS;
        }
        return new ComponentQuery(parts.id(), path, op, value);
    }

    private static @Nullable Op opOf(String token) {
        for (Op op : Op.values()) {
            if (op.token.equals(token)) {
                return op;
            }
        }
        return null;
    }

    /** A condição para ler: {@code gene_group → purity ≥ 100} (o id sem o namespace, o caminho depois da seta). */
    public String summary() {
        StringBuilder out = new StringBuilder(id.substring(id.indexOf(':') + 1));
        if (!path.isEmpty()) {
            out.append(" → ").append(String.join(".", path));
        }
        if (op != Op.EXISTS) {
            out.append(' ').append(op.symbol).append(' ').append(value);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ avaliação

    /** O componente serializado em {@code tree} satisfaz a condição. */
    public boolean test(Object tree) {
        List<Object> found = new ArrayList<>(1);
        found.add(tree);
        for (String segment : path) {
            List<Object> next = new ArrayList<>(found.size());
            for (Object node : found) {
                step(node, segment, next);
            }
            found = next;
            if (found.isEmpty()) {
                break;
            }
        }
        if (found.isEmpty()) {
            return op == Op.NE;
        }
        if (op == Op.EXISTS) {
            return true;
        }
        boolean any = false;
        for (Object node : found) {
            if (satisfies(node, op == Op.NE ? Op.EQ : op)) {
                any = true;
                break;
            }
        }
        return op == Op.NE ? !any : any;
    }

    private static void step(Object node, String segment, List<Object> out) {
        if (node instanceof Map<?, ?> map) {
            Object child = map.get(segment);
            if (child != null) {
                out.add(child);
            }
        } else if (node instanceof List<?> list) {
            if (segment.equals("*")) {
                out.addAll(list);
            } else if (!segment.isEmpty() && segment.chars().allMatch(Character::isDigit) && segment.length() < 6) {
                int index = Integer.parseInt(segment);
                if (index < list.size()) {
                    out.add(list.get(index));
                }
            }
        }
    }

    private boolean satisfies(Object node, Op test) {
        String wanted = value;
        if (wanted == null) {
            return false;
        }
        switch (test) {
            case EQ -> {
                Double a = number(node);
                Double b = number(wanted);
                if (a != null && b != null) {
                    return a.doubleValue() == b.doubleValue();
                }
                String text = text(node);
                return text.equals(wanted) || text.endsWith(":" + wanted);
            }
            case CONTAINS -> {
                if (node instanceof List<?> list) {
                    for (Object element : list) {
                        if (text(element).contains(wanted)) {
                            return true;
                        }
                    }
                    return false;
                }
                return text(node).contains(wanted);
            }
            case GE, LE, GT, LT -> {
                Double a = number(node);
                Double b = number(wanted);
                if (a == null || b == null) {
                    return false;
                }
                return switch (test) {
                    case GE -> a >= b;
                    case LE -> a <= b;
                    case GT -> a > b;
                    default -> a < b;
                };
            }
            default -> {
                return false;
            }
        }
    }

    private static @Nullable Double number(Object node) {
        if (node instanceof Number n) {
            return n.doubleValue();
        }
        if (node instanceof String s) {
            return number(s);
        }
        return null;
    }

    private static @Nullable Double number(String text) {
        if (text.isEmpty()) {
            return null;
        }
        char c = text.charAt(0);
        if (!(c == '-' || c == '+' || c == '.' || Character.isDigit(c))) {
            return null;
        }
        try {
            double d = Double.parseDouble(text);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** O texto, em minúsculas, que o {@code =} e o {@code ~} comparam: o próprio valor, ou o JSON compacto de um objeto ou lista. */
    public static String text(Object node) {
        if (node instanceof String s) {
            return s.toLowerCase(Locale.ROOT);
        }
        if (node instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
        }
        if (node instanceof Boolean || node == null) {
            return String.valueOf(node);
        }
        StringBuilder out = new StringBuilder();
        json(node, out);
        return out.toString().toLowerCase(Locale.ROOT);
    }

    private static void json(Object node, StringBuilder out) {
        if (node instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(String.valueOf(entry.getKey()), out);
                out.append(':');
                json(entry.getValue(), out);
            }
            out.append('}');
        } else if (node instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                json(list.get(i), out);
            }
            out.append(']');
        } else if (node instanceof String s) {
            quote(s, out);
        } else if (node instanceof Number n) {
            out.append(text(n));
        } else {
            out.append(node);
        }
    }

    private static void quote(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\');
            }
            out.append(c);
        }
        out.append('"');
    }

    // ------------------------------------------------------------------ para as sugestões da tela

    /**
     * Os caminhos até os valores simples de {@code tree}, com {@code *} no lugar dos elementos de uma lista (os
     * primeiros dela dão as chaves), até {@code maxDepth} níveis e {@code cap} caminhos.
     */
    public static Set<String> leafPaths(Object tree, int maxDepth, int cap) {
        Set<String> out = new LinkedHashSet<>();
        walk(tree, "", maxDepth, cap, out, new int[] {MAX_NODES});
        return out;
    }

    private static void walk(Object node, String prefix, int depth, int cap, Set<String> out, int[] budget) {
        if (out.size() >= cap || budget[0]-- <= 0) {
            return;
        }
        if (node instanceof Map<?, ?> map && depth > 0) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                walk(entry.getValue(), prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey(),
                        depth - 1, cap, out, budget);
            }
        } else if (node instanceof List<?> list && depth > 0) {
            for (int i = 0; i < Math.min(3, list.size()); i++) {
                walk(list.get(i), prefix.isEmpty() ? "*" : prefix + ".*", depth - 1, cap, out, budget);
            }
        } else if (!prefix.isEmpty()) {
            out.add(prefix);
        }
    }

    /** Os textos dos valores simples que há em {@code path} dentro de {@code tree}, sem repetir, até {@code cap}. */
    public static Set<String> leafValues(Object tree, List<String> path, int cap) {
        List<Object> found = new ArrayList<>();
        found.add(tree);
        for (String segment : path) {
            List<Object> next = new ArrayList<>();
            for (Object node : found) {
                step(node, segment, next);
            }
            found = next;
        }
        Set<String> out = new LinkedHashSet<>();
        for (Object node : found) {
            if (out.size() >= cap) {
                break;
            }
            if (node instanceof List<?> list) {
                for (Object element : list) {
                    if (!(element instanceof Map<?, ?>) && !(element instanceof List<?>) && out.size() < cap) {
                        out.add(text(element));
                    }
                }
            } else if (!(node instanceof Map<?, ?>)) {
                out.add(text(node));
            }
        }
        return out;
    }
}
