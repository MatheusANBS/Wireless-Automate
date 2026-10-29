package io.github.matheusanbs.wirelessautomate.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * A forma legível da pixel art, para uma pessoa ou uma IA de chat escrever sem comprimir nada. Lógica pura, com
 * JUnit:
 *
 * <pre>
 * WA-ARTE1
 * nome: Logo
 * tamanho: 16x16
 * paleta: - 1B1920 7A7366 ECE5D6 ...
 * ......66666.....
 * </pre>
 *
 * <p>As chaves valem em português ou em inglês ({@code nome}/{@code name}, {@code tamanho}/{@code size},
 * {@code paleta}/{@code palette}), sem diferenciar maiúscula. A paleta tem até 16 entradas: a 0 é sempre transparente
 * ({@code -}, {@code .}, {@code transparente}, {@code transparent} ou uma cor, ignorada) e as outras são
 * {@code RRGGBB} ou {@code RRGGBBAA}, com {@code #} opcional; as que faltam ficam pretas. Cada pixel é o índice da
 * paleta em hexadecimal, e {@code .} vale 0; espaços na linha não contam, e linhas em branco ou começadas por
 * {@code #} são comentário. Um erro diz a chave do texto ({@code code.art.*}) e os argumentos, a linha primeiro.
 */
public final class TvArtText {
    public static final String HEADER = "WA-ARTE1";
    private static final Pattern SIZE = Pattern.compile("(\\d{1,4})\\s*[xX×*]\\s*(\\d{1,4})");
    private static final int BLACK = 0xFF000000;

    private TvArtText() {
    }

    /** Uma arte lida: nome, tamanho, as 16 cores ARGB (a 0 transparente) e um índice por pixel. */
    public record Art(String name, int width, int height, int[] palette, byte[] pixels) {
    }

    /** Uma linha de pixels e o número dela no texto (ou na lista), para o erro. */
    public record Row(String text, int line) {
    }

    /** A arte, ou o erro: a chave do texto e os argumentos. */
    public record Parsed(@Nullable Art art, @Nullable String error, Object[] args) {
        static Parsed fail(String key, Object... args) {
            return new Parsed(null, "code.art." + key, args);
        }
    }

    /** Lê o texto inteiro, com o {@link #HEADER} na primeira linha que não está em branco. */
    public static Parsed parse(String text) {
        String[] lines = text.split("\\r\\n|\\r|\\n", -1);
        String name = "";
        String size = null;
        int sizeLine = 0;
        List<String> palette = List.of();
        int paletteLine = 0;
        List<Row> rows = new ArrayList<>();
        boolean header = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || header && line.startsWith("#")) {
                continue;
            }
            if (!header) {
                if (!line.equalsIgnoreCase(HEADER)) {
                    return new Parsed(null, "code.invalid", new Object[0]);
                }
                header = true;
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0 || !rows.isEmpty()) {
                rows.add(new Row(line, i + 1));
                continue;
            }
            String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();
            switch (key) {
                case "nome", "name" -> name = value;
                case "tamanho", "size" -> {
                    size = value;
                    sizeLine = i + 1;
                }
                case "paleta", "palette" -> {
                    palette = value.isEmpty() ? List.of() : List.of(value.split("\\s+"));
                    paletteLine = i + 1;
                }
                default -> {
                    return Parsed.fail("key", i + 1, key);
                }
            }
        }
        return header ? build(name, size, sizeLine, palette, paletteLine, rows)
                : new Parsed(null, "code.invalid", new Object[0]);
    }

    /** Monta a arte das partes já separadas (o texto acima, ou o objeto legível dentro do JSON de um layout). */
    public static Parsed build(String name, @Nullable String size, int sizeLine, List<String> palette, int paletteLine,
            List<Row> rows) {
        if (size == null) {
            return Parsed.fail("size_missing");
        }
        Matcher matcher = SIZE.matcher(size.strip());
        int width = matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
        int height = matcher.matches() ? Integer.parseInt(matcher.group(2)) : 0;
        if (!TvPixels.validSize(width, height)) {
            return Parsed.fail("size", sizeLine, TvPixels.MIN_SIDE, TvPixels.MAX_SIDE);
        }
        if (palette.size() > TvPixels.COLORS) {
            return Parsed.fail("palette_long", paletteLine, TvPixels.COLORS);
        }
        int[] colors = new int[TvPixels.COLORS];
        Arrays.fill(colors, BLACK);
        colors[TvPixels.TRANSPARENT] = 0;
        for (int i = 0; i < palette.size(); i++) {
            String entry = palette.get(i).strip();
            Integer color = TvColor.fromHex(entry);
            if (i == TvPixels.TRANSPARENT) {
                if (color == null && !transparent(entry)) {
                    return Parsed.fail("color", paletteLine, entry);
                }
            } else if (color == null) {
                return Parsed.fail("color", paletteLine, entry);
            } else {
                colors[i] = color;
            }
        }
        byte[] pixels = new byte[width * height];
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            int x = 0;
            for (int c = 0; c < row.text.length(); c++) {
                char ch = row.text.charAt(c);
                if (Character.isWhitespace(ch)) {
                    continue;
                }
                int index = ch == '.' ? 0 : Character.digit(ch, 16);
                if (index < 0) {
                    return Parsed.fail("pixel", row.line, String.valueOf(ch));
                }
                if (r < height && x < width) {
                    pixels[r * width + x] = (byte) index;
                }
                x++;
            }
            if (x != width) {
                return Parsed.fail("row", row.line, x, width);
            }
        }
        if (rows.size() != height) {
            return Parsed.fail("rows", rows.size(), height);
        }
        return new Parsed(new Art(name, width, height, colors, pixels), null, new Object[0]);
    }

    private static boolean transparent(String entry) {
        return entry.equals("-") || entry.equals(".") || entry.equalsIgnoreCase("transparente")
                || entry.equalsIgnoreCase("transparent");
    }

    /** O texto legível da arte, com as chaves em inglês ou em português. */
    public static String format(Art art, boolean english) {
        StringBuilder out = new StringBuilder(art.height * (art.width + 1) + 160);
        out.append(HEADER).append('\n');
        out.append(english ? "name: " : "nome: ").append(oneLine(art.name)).append('\n');
        out.append(english ? "size: " : "tamanho: ").append(art.width).append('x').append(art.height).append('\n');
        out.append(english ? "palette:" : "paleta:");
        for (String entry : paletteEntries(art.palette)) {
            out.append(' ').append(entry);
        }
        out.append('\n');
        for (String row : rows(art)) {
            out.append(row).append('\n');
        }
        return out.toString();
    }

    /** O nome numa linha só (uma quebra de linha viraria uma linha de pixels). */
    public static String oneLine(String name) {
        return name.replaceAll("[\\r\\n]+", " ").strip();
    }

    /** As 16 entradas da paleta: {@code -} e as cores em {@code RRGGBB}, ou {@code RRGGBBAA} com transparência. */
    public static List<String> paletteEntries(int[] palette) {
        List<String> out = new ArrayList<>(palette.length);
        out.add("-");
        for (int i = 1; i < palette.length; i++) {
            int color = palette[i];
            int alpha = color >>> 24;
            String rgb = String.format(Locale.ROOT, "%06X", color & 0xFFFFFF);
            out.add(alpha == 0xFF ? rgb : rgb + String.format(Locale.ROOT, "%02X", alpha));
        }
        return out;
    }

    /** As linhas de pixels: o índice em hexadecimal minúsculo e {@code .} no transparente. */
    public static List<String> rows(Art art) {
        List<String> out = new ArrayList<>(art.height);
        char[] line = new char[art.width];
        for (int y = 0; y < art.height; y++) {
            for (int x = 0; x < art.width; x++) {
                int index = art.pixels[y * art.width + x];
                line[x] = index == 0 ? '.' : Character.forDigit(index, 16);
            }
            out.add(new String(line));
        }
        return out;
    }
}
