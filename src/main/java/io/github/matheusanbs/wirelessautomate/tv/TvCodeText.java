package io.github.matheusanbs.wirelessautomate.tv;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.jetbrains.annotations.Nullable;

/**
 * O texto dos códigos de exportar da Tela de Sinal ({@code WA-TELA1:} e {@code WA-ARTE1:}): o prefixo seguido dos
 * bytes comprimidos com deflate, em base64 URL-safe sem quebra de linha nem preenchimento. Lógica pura, com JUnit; o
 * NBT dentro dos bytes fica com o {@link TvCodes}.
 *
 * <p>A leitura aceita espaços e quebras de linha em volta (e no meio, que um chat pode ter quebrado o texto), recusa
 * mais de {@link #MAX_TEXT} caracteres antes de olhar o resto e para a descompressão assim que ela passa do teto de
 * bytes, para um código pequeno não virar uma bomba de memória.
 */
public final class TvCodeText {
    /** O teto do texto colado, em caracteres (o código de um layout no teto fica bem abaixo). */
    public static final int MAX_TEXT = 512 * 1024;
    private static final int CHUNK = 8192;

    private TvCodeText() {
    }

    /** O código: {@code prefix} e os bytes comprimidos em base64 URL-safe, numa linha só. */
    public static String encode(String prefix, byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 2));
            byte[] chunk = new byte[CHUNK];
            while (!deflater.finished()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
            return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
        } finally {
            deflater.end();
        }
    }

    /**
     * Os bytes do código, ou {@code null} se o texto é grande demais, não começa por {@code prefix}, não é base64,
     * não descomprime inteiro ou passa de {@code maxBytes} descomprimido.
     */
    public static byte @Nullable [] decode(String prefix, @Nullable String text, int maxBytes) {
        if (text == null || text.length() > MAX_TEXT) {
            return null;
        }
        String code = text.strip();
        if (!code.startsWith(prefix)) {
            return null;
        }
        byte[] packed;
        try {
            packed = Base64.getUrlDecoder().decode(withoutSpaces(code.substring(prefix.length()))
                    .getBytes(StandardCharsets.ISO_8859_1));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return inflate(packed, maxBytes);
    }

    /**
     * A maior profundidade de {@code [} e {@code {} do texto, fora das strings: o JSON de um código legível com mais
     * do que um teto é recusado antes de ler, para não estourar a pilha de quem lê.
     */
    public static int jsonDepth(String text) {
        int depth = 0;
        int max = 0;
        boolean string = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (string) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    string = false;
                }
            } else if (c == '"') {
                string = true;
            } else if (c == '[' || c == '{') {
                max = Math.max(max, ++depth);
            } else if (c == ']' || c == '}') {
                depth = Math.max(0, depth - 1);
            }
        }
        return max;
    }

    private static String withoutSpaces(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Descomprime parando no meio quando passa de {@code maxBytes}; {@code null} também se o fluxo não termina. */
    private static byte @Nullable [] inflate(byte[] packed, int maxBytes) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, Math.max(64, packed.length * 4)));
            byte[] chunk = new byte[CHUNK];
            while (!inflater.finished()) {
                int room = maxBytes - out.size();
                int read = inflater.inflate(chunk, 0, Math.min(chunk.length, room + 1));
                if (read == 0 && !inflater.finished()) {
                    return null;
                }
                out.write(chunk, 0, read);
                if (out.size() > maxBytes) {
                    return null;
                }
            }
            return inflater.getRemaining() == 0 ? out.toByteArray() : null;
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }
}
