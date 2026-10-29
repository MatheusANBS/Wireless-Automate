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
 *
 * <p>A arte animada (o formato dos guias de IA, seção "Pixel art animada") soma as chaves {@code quadros}/{@code
 * frames} (1 a {@link TvPixels#MAX_FRAMES}), {@code velocidade}/{@code speed} (1 a 20, padrão 6) e {@code modo}/{@code
 * mode} ({@code loop}, ou {@code vaivem}/{@code pingpong}) e escreve cada quadro num bloco que começa com
 * {@code quadro N:}/{@code frame N:}, N de 1 em diante e em ordem. Sem a chave {@code quadros}, as linhas vêm direto
 * (a arte parada); os blocos sem a chave também valem, contados.
 */
public final class TvArtText {
    public static final String HEADER = "WA-ARTE1";
    private static final Pattern SIZE = Pattern.compile("(\\d{1,4})\\s*[xX×*]\\s*(\\d{1,4})");
    private static final Pattern FRAME = Pattern.compile("(?i)(quadro|frame)\\s*(\\d{1,4})\\s*:");
    private static final int BLACK = 0xFF000000;

    private TvArtText() {
    }

    /**
     * Uma arte lida: nome, tamanho, as 16 cores ARGB (a 0 transparente), um índice por pixel de cada quadro, um
     * quadro depois do outro, e a animação.
     */
    public record Art(String name, int width, int height, int[] palette, byte[] pixels, int frames, int fps,
            TvPixels.Mode mode) {
        /** Uma arte parada. */
        public Art(String name, int width, int height, int[] palette, byte[] pixels) {
            this(name, width, height, palette, pixels, 1, TvPixels.DEFAULT_FPS, TvPixels.Mode.LOOP);
        }
    }

    /**
     * Uma linha de pixels e o número dela no texto (ou na lista), para o erro. {@code frame} é o quadro de uma linha
     * da lista de quadros do JSON, onde o número conta dentro do quadro; no texto solto é 0, porque a linha já diz
     * onde está.
     */
    public record Row(String text, int line, int frame) {
        public Row(String text, int line) {
            this(text, line, 0);
        }
    }

    /** A arte, ou o erro: a chave do texto e os argumentos. */
    public record Parsed(@Nullable Art art, @Nullable String error, Object[] args) {
        static Parsed fail(String key, Object... args) {
            return new Parsed(null, "code.art." + key, args);
        }
    }

    /** A velocidade e o modo já lidos, ou o erro de um deles. */
    public record Motion(int fps, TvPixels.Mode mode, @Nullable Parsed error) {
    }

    /** As chaves lidas antes das linhas de pixels, cada uma com a linha dela para o erro. */
    private static final class Keys {
        String name = "";
        String size;
        int sizeLine;
        List<String> palette = List.of();
        int paletteLine;
        String frames;
        int framesLine;
        String speed;
        int speedLine;
        String mode;
        int modeLine;

        /** Guarda a chave da linha {@code line}; devolve se ela é conhecida. */
        boolean read(String key, String value, int line) {
            switch (key) {
                case "nome", "name" -> name = value;
                case "tamanho", "size" -> {
                    size = value;
                    sizeLine = line;
                }
                case "paleta", "palette" -> {
                    palette = value.isEmpty() ? List.of() : List.of(value.split("\\s+"));
                    paletteLine = line;
                }
                case "quadros", "frames" -> {
                    frames = value;
                    framesLine = line;
                }
                case "velocidade", "speed" -> {
                    speed = value;
                    speedLine = line;
                }
                case "modo", "mode" -> {
                    mode = value;
                    modeLine = line;
                }
                default -> {
                    return false;
                }
            }
            return true;
        }
    }

    /** Lê o texto inteiro, com o {@link #HEADER} na primeira linha que não está em branco. */
    public static Parsed parse(String text) {
        String[] lines = text.split("\\r\\n|\\r|\\n", -1);
        Keys keys = new Keys();
        List<List<Row>> frames = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        boolean header = false;
        boolean pixels = false;
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
            Matcher block = FRAME.matcher(line);
            if (block.matches()) {
                if (frames.isEmpty() && !rows.isEmpty()) {
                    return Parsed.fail("frame_order", rows.get(0).line(), 1);
                }
                if (Integer.parseInt(block.group(2)) != frames.size() + 1) {
                    return Parsed.fail("frame_order", i + 1, frames.size() + 1);
                }
                rows = new ArrayList<>();
                frames.add(rows);
                pixels = true;
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0 || pixels) {
                rows.add(new Row(line, i + 1));
                pixels = true;
                continue;
            }
            String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            if (!keys.read(key, line.substring(colon + 1).strip(), i + 1)) {
                return Parsed.fail("key", i + 1, key);
            }
        }
        return header ? assemble(keys, frames, rows) : new Parsed(null, "code.invalid", new Object[0]);
    }

    /** A arte das chaves e dos blocos lidos, conferindo os quadros contra a chave {@code quadros}. */
    private static Parsed assemble(Keys keys, List<List<Row>> frames, List<Row> rows) {
        Motion motion = motion(keys.speed, keys.speedLine, keys.mode, keys.modeLine);
        if (motion.error != null) {
            return motion.error;
        }
        if (keys.frames != null) {
            int declared = count(keys.frames);
            if (declared < 1 || declared > TvPixels.MAX_FRAMES) {
                return Parsed.fail("frames", keys.framesLine, 1, TvPixels.MAX_FRAMES);
            }
            if (frames.isEmpty() && declared > 1) {
                return rows.isEmpty() ? Parsed.fail("frames_count", 0, declared)
                        : Parsed.fail("frame_order", rows.get(0).line(), 1);
            }
            if (!frames.isEmpty() && frames.size() != declared) {
                return Parsed.fail("frames_count", frames.size(), declared);
            }
        }
        if (frames.isEmpty()) {
            frames.add(rows);
        }
        return build(keys.name, keys.size, keys.sizeLine, keys.palette, keys.paletteLine, frames, motion.fps,
                motion.mode);
    }

    private static int count(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * A velocidade (1 a 20, padrão 6) e o modo ({@code loop}, {@code vaivem} ou {@code pingpong}, padrão loop) em
     * texto; nulos, o padrão. A linha vai no erro (no JSON, 0).
     */
    public static Motion motion(@Nullable String speed, int speedLine, @Nullable String mode, int modeLine) {
        int fps = TvPixels.DEFAULT_FPS;
        if (speed != null) {
            fps = count(speed);
            if (fps < TvPixels.MIN_FPS || fps > TvPixels.MAX_FPS) {
                return new Motion(0, TvPixels.Mode.LOOP, Parsed.fail("speed", speedLine, TvPixels.MIN_FPS,
                        TvPixels.MAX_FPS));
            }
        }
        TvPixels.Mode loop = TvPixels.Mode.LOOP;
        if (mode != null) {
            switch (mode.strip().toLowerCase(Locale.ROOT)) {
                case "loop" -> loop = TvPixels.Mode.LOOP;
                case "vaivem", "pingpong" -> loop = TvPixels.Mode.PINGPONG;
                default -> {
                    return new Motion(0, TvPixels.Mode.LOOP, Parsed.fail("mode", modeLine, mode.strip()));
                }
            }
        }
        return new Motion(fps, loop, null);
    }

    /** Monta a arte parada das partes já separadas (o objeto legível com {@code linhas} dentro do JSON de um layout). */
    public static Parsed build(String name, @Nullable String size, int sizeLine, List<String> palette, int paletteLine,
            List<Row> rows) {
        return build(name, size, sizeLine, palette, paletteLine, List.of(rows), TvPixels.DEFAULT_FPS,
                TvPixels.Mode.LOOP);
    }

    /**
     * Monta a arte das partes já separadas, com as linhas de cada quadro (o texto acima, ou o objeto legível com
     * {@code quadros} dentro do JSON de um layout).
     */
    public static Parsed build(String name, @Nullable String size, int sizeLine, List<String> palette, int paletteLine,
            List<List<Row>> frames, int fps, TvPixels.Mode mode) {
        if (size == null) {
            return Parsed.fail("size_missing");
        }
        Matcher matcher = SIZE.matcher(size.strip());
        int width = matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
        int height = matcher.matches() ? Integer.parseInt(matcher.group(2)) : 0;
        if (!TvPixels.validSize(width, height)) {
            return Parsed.fail("size", sizeLine, TvPixels.MIN_SIDE, TvPixels.MAX_SIDE);
        }
        if (frames.isEmpty() || frames.size() > TvPixels.MAX_FRAMES) {
            return Parsed.fail("frames", 0, 1, TvPixels.MAX_FRAMES);
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
        int area = width * height;
        byte[] pixels = new byte[area * frames.size()];
        for (int f = 0; f < frames.size(); f++) {
            Parsed problem = frame(frames.get(f), f, frames.size() > 1, width, height, pixels, f * area);
            if (problem != null) {
                return problem;
            }
        }
        return new Parsed(new Art(name, width, height, colors, pixels, frames.size(), fps, mode), null,
                new Object[0]);
    }

    /** Lê as linhas de um quadro para {@code pixels} a partir de {@code offset}; o erro, ou {@code null}. */
    private static @Nullable Parsed frame(List<Row> rows, int index, boolean animated, int width, int height,
            byte[] pixels, int offset) {
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            int x = 0;
            for (int c = 0; c < row.text.length(); c++) {
                char ch = row.text.charAt(c);
                if (Character.isWhitespace(ch)) {
                    continue;
                }
                int value = ch == '.' ? 0 : Character.digit(ch, 16);
                if (value < 0) {
                    return row.frame > 0 ? Parsed.fail("frame_pixel", row.frame, row.line, String.valueOf(ch))
                            : Parsed.fail("pixel", row.line, String.valueOf(ch));
                }
                if (r < height && x < width) {
                    pixels[offset + r * width + x] = (byte) value;
                }
                x++;
            }
            if (x != width) {
                return row.frame > 0 ? Parsed.fail("frame_row", row.frame, row.line, x, width)
                        : Parsed.fail("row", row.line, x, width);
            }
        }
        if (rows.size() != height) {
            return animated ? Parsed.fail("frame_rows", index + 1, rows.size(), height)
                    : Parsed.fail("rows", rows.size(), height);
        }
        return null;
    }

    private static boolean transparent(String entry) {
        return entry.equals("-") || entry.equals(".") || entry.equalsIgnoreCase("transparente")
                || entry.equalsIgnoreCase("transparent");
    }

    /**
     * O texto legível da arte, com as chaves em inglês ou em português; a animada com {@code quadros},
     * {@code velocidade}, {@code modo} e um bloco por quadro.
     */
    public static String format(Art art, boolean english) {
        StringBuilder out = new StringBuilder(art.frames * (art.height * (art.width + 1) + 12) + 200);
        out.append(HEADER).append('\n');
        out.append(english ? "name: " : "nome: ").append(oneLine(art.name)).append('\n');
        out.append(english ? "size: " : "tamanho: ").append(art.width).append('x').append(art.height).append('\n');
        out.append(english ? "palette:" : "paleta:");
        for (String entry : paletteEntries(art.palette)) {
            out.append(' ').append(entry);
        }
        out.append('\n');
        if (art.frames > 1) {
            out.append(english ? "frames: " : "quadros: ").append(art.frames).append('\n');
            out.append(english ? "speed: " : "velocidade: ").append(art.fps).append('\n');
            out.append(english ? "mode: " : "modo: ").append(modeName(art.mode, english)).append('\n');
        }
        for (int f = 0; f < art.frames; f++) {
            if (art.frames > 1) {
                out.append(english ? "frame " : "quadro ").append(f + 1).append(":\n");
            }
            for (String row : rows(art, f)) {
                out.append(row).append('\n');
            }
        }
        return out.toString();
    }

    /** O nome do modo no texto: {@code loop}, e {@code vaivem} ou {@code pingpong}. */
    public static String modeName(TvPixels.Mode mode, boolean english) {
        return mode == TvPixels.Mode.LOOP ? "loop" : english ? "pingpong" : "vaivem";
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

    /** As linhas de pixels do primeiro quadro: o índice em hexadecimal minúsculo e {@code .} no transparente. */
    public static List<String> rows(Art art) {
        return rows(art, 0);
    }

    /** As linhas de pixels do quadro {@code frame}. */
    public static List<String> rows(Art art, int frame) {
        List<String> out = new ArrayList<>(art.height);
        char[] line = new char[art.width];
        int offset = frame * art.width * art.height;
        for (int y = 0; y < art.height; y++) {
            for (int x = 0; x < art.width; x++) {
                int index = art.pixels[offset + y * art.width + x];
                line[x] = index == 0 ? '.' : Character.forDigit(index, 16);
            }
            out.add(new String(line));
        }
        return out;
    }
}
