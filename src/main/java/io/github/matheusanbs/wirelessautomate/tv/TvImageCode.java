package io.github.matheusanbs.wirelessautomate.tv;

import java.util.Base64;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * O código de texto de uma imagem enviada, para colar no "Importar" do editor da Tela de Sinal (e o que o "Copiar
 * código" de uma imagem gera):
 * <pre>
 * WA-IMAGEM1
 * nome: Mona Lisa
 * formato: png
 * iVBORw0KGgoAAAANSUhEUgAA...
 * </pre>
 * A primeira linha é só {@link #HEADER}; as chaves valem em português ou inglês ({@code nome}/{@code name},
 * {@code formato}/{@code format}), sem diferenciar maiúscula; o resto é o arquivo em base64 (o padrão ou o URL-safe,
 * com ou sem {@code =}), numa linha ou em várias. Linhas em branco, linhas que começam com {@code #} e espaços são
 * ignorados. O {@code formato} (png, jpg, jpeg ou gif) precisa bater com a assinatura do arquivo; sem ele, vale a
 * assinatura. Sem {@code nome}, quem cola dá o padrão.
 * <p>
 * Os tetos são os do envio por arquivo, que o cliente conhece pelo menu: o texto é contado antes de tudo
 * ({@link #maxText}, coerente com o {@code tv.images.maxFileBytes}: o base64 ocupa 4/3, mais as quebras de linha), o
 * tamanho do arquivo antes de decodificar o base64, e o cabeçalho ({@link TvImageHeader}: formato, lado e quadros)
 * antes de qualquer decodificação de pixels. Lógica pura, com JUnit.
 */
public final class TvImageCode {
    public static final String HEADER = "WA-IMAGEM1";
    /** Quantos caracteres por linha o "Copiar código" põe no base64. */
    static final int LINE = 76;

    private TvImageCode() {
    }

    /** O que o código deu: o nome (vazio se não veio), os bytes do arquivo e o cabeçalho conferido. */
    public record Image(String name, byte[] data, TvImageHeader.Info info) {
    }

    /** O teto do texto colado para um arquivo de até {@code maxFileBytes}: o base64, as quebras e uma folga. */
    public static int maxText(int maxFileBytes) {
        long base64 = (maxFileBytes + 2L) / 3 * 4;
        return (int) Math.min(Integer.MAX_VALUE, base64 + base64 / LINE * 2 + 4096);
    }

    /** Se o texto é um código de imagem: a primeira linha que conta é {@link #HEADER} (só ela é olhada). */
    public static boolean is(@Nullable String text) {
        if (text == null) {
            return false;
        }
        int at = 0;
        while (at < text.length()) {
            int end = lineEnd(text, at);
            String line = text.substring(at, end).strip();
            if (!line.isEmpty() && !line.startsWith("#")) {
                return line.equalsIgnoreCase(HEADER);
            }
            at = end + 1;
        }
        return false;
    }

    /**
     * Lê o código, conferindo os tetos do servidor: {@code maxFileBytes} por arquivo, {@code maxSize} de lado e
     * {@code maxFrames} quadros num GIF. O erro é a chave do aviso ({@code code.image.*}) e os argumentos.
     */
    public static TvCodes.Read<Image> read(@Nullable String text, int maxFileBytes, int maxSize, int maxFrames) {
        if (text == null || !is(text)) {
            return TvCodes.Read.fail("code.invalid");
        }
        if (text.length() > maxText(maxFileBytes)) {
            return TvCodes.Read.fail("code.image.file_too_big", maxFileBytes / 1024);
        }
        String name = "";
        TvImageFormat format = null;
        StringBuilder base64 = new StringBuilder(Math.min(text.length(), 1 << 20));
        boolean header = false;
        int number = 0;
        int at = 0;
        while (at < text.length()) {
            int end = lineEnd(text, at);
            String line = text.substring(at, end).strip();
            at = end + 1;
            number++;
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!header) {
                header = true;
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                if (!appendBase64(base64, line)) {
                    return TvCodes.Read.fail("code.image.base64", number);
                }
                continue;
            }
            String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();
            switch (key) {
                case "nome", "name" -> name = TvText.cut(value, TvLibraryPicture.MAX_NAME);
                case "formato", "format" -> {
                    format = format(value);
                    if (format == null) {
                        return TvCodes.Read.fail("code.image.format", number, value);
                    }
                }
                default -> {
                    return TvCodes.Read.fail("code.art.key", number, key);
                }
            }
        }
        if (base64.isEmpty()) {
            return TvCodes.Read.fail("code.image.empty");
        }
        // O tamanho do arquivo sai do tamanho do base64, antes de decodificar.
        if (base64.length() / 4L * 3 > maxFileBytes + 2L) {
            return TvCodes.Read.fail("code.image.file_too_big", maxFileBytes / 1024);
        }
        byte[] data;
        try {
            data = Base64.getDecoder().decode(base64.toString());
        } catch (IllegalArgumentException e) {
            return TvCodes.Read.fail("code.image.base64", number);
        }
        if (data.length > maxFileBytes) {
            return TvCodes.Read.fail("code.image.file_too_big", maxFileBytes / 1024);
        }
        TvImageHeader.Result result = TvImageHeader.read(data, maxSize, maxFrames);
        if (!result.ok()) {
            return switch (result.problem()) {
                case TOO_LARGE -> TvCodes.Read.fail("code.image.too_large", maxSize);
                case TOO_MANY_FRAMES -> TvCodes.Read.fail("code.image.frames", maxFrames);
                default -> TvCodes.Read.fail("code.image.bad_format");
            };
        }
        if (format != null && format != result.info().format()) {
            return TvCodes.Read.fail("code.image.mismatch", format.ext(), result.info().format().ext());
        }
        return TvCodes.Read.ok(new Image(name, data, result.info()));
    }

    /**
     * O código da imagem, com as chaves em inglês ({@code english}) ou em português, e o base64 padrão com {@code =}
     * em linhas de {@link #LINE} caracteres.
     */
    public static String write(String name, byte[] data, TvImageFormat format, boolean english) {
        String encoded = Base64.getEncoder().encodeToString(data);
        StringBuilder out = new StringBuilder(encoded.length() + encoded.length() / LINE + 64);
        out.append(HEADER).append('\n');
        out.append(english ? "name: " : "nome: ").append(name.strip()).append('\n');
        out.append(english ? "format: " : "formato: ").append(format.ext()).append('\n');
        for (int i = 0; i < encoded.length(); i += LINE) {
            out.append(encoded, i, Math.min(encoded.length(), i + LINE)).append('\n');
        }
        return out.toString();
    }

    /** O formato pelo nome do código ({@code jpeg} é o JPG), ou {@code null}. */
    static @Nullable TvImageFormat format(String value) {
        String ext = value.toLowerCase(Locale.ROOT);
        return TvImageFormat.byExt(ext.equals("jpeg") ? "jpg" : ext);
    }

    /**
     * Junta uma linha de base64 sem os espaços, trocando o URL-safe pelo padrão e largando o {@code =}; falso se há um
     * caractere que não é de base64.
     */
    private static boolean appendBase64(StringBuilder out, String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '+' || c == '/') {
                out.append(c);
            } else if (c == '-') {
                out.append('+');
            } else if (c == '_') {
                out.append('/');
            } else if (c != '=' && !Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    /** Onde acaba a linha que começa em {@code from} (o {@code \n}, ou o fim); o {@code \r} sai no {@code strip}. */
    private static int lineEnd(String text, int from) {
        int n = text.indexOf('\n', from);
        return n < 0 ? text.length() : n;
    }
}
