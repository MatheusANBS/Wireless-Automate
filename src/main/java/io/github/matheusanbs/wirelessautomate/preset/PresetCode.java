package io.github.matheusanbs.wirelessautomate.preset;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Envelope do código de texto de um preset, sem classes do Minecraft: {@value #PREFIX} seguido dos
 * bytes comprimidos (deflate) em Base64 URL-safe sem preenchimento. Quem chama decide o que vão os
 * bytes (o NBT do preset, em {@link PresetCodes}).
 *
 * <p>Limites: o código tem no máximo {@value #MAX_CODE_LENGTH} caracteres (cabe num pacote do
 * cliente para o servidor) e descomprime para no máximo {@value #MAX_RAW_BYTES} bytes, o que barra
 * códigos que explodem ao descomprimir.
 */
public final class PresetCode {
    public static final String PREFIX = "WA1:";
    public static final int MAX_CODE_LENGTH = 32_000;
    public static final int MAX_RAW_BYTES = 512 * 1024;

    /** Por que um código não foi aceito; vira a chave de tradução {@code ...code.<chave>}. */
    public enum Problem {
        EMPTY("empty"),
        PREFIX("prefix"),
        TOO_LONG("too_long"),
        BASE64("corrupt"),
        CORRUPT("corrupt"),
        TOO_LARGE("too_large");

        public final String key;

        Problem(String key) {
            this.key = key;
        }
    }

    public static final class InvalidCodeException extends Exception {
        private final Problem problem;

        public InvalidCodeException(Problem problem) {
            super(problem.name());
            this.problem = problem;
        }

        public Problem problem() {
            return problem;
        }
    }

    /** Comprime e codifica. Pode passar de {@link #MAX_CODE_LENGTH}: veja {@link #fits(String)}. */
    public static String encode(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
        } finally {
            deflater.end();
        }
    }

    public static boolean fits(String code) {
        return code.length() <= MAX_CODE_LENGTH;
    }

    /**
     * Valida e decodifica. Aceita espaços e quebras de linha no meio (o chat e alguns editores quebram
     * linhas longas) e o prefixo em qualquer caixa.
     */
    public static byte[] decode(String code) throws InvalidCodeException {
        String text = code == null ? "" : code.strip();
        if (text.isEmpty()) {
            throw new InvalidCodeException(Problem.EMPTY);
        }
        if (text.length() > MAX_CODE_LENGTH) {
            throw new InvalidCodeException(Problem.TOO_LONG);
        }
        if (!text.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            throw new InvalidCodeException(Problem.PREFIX);
        }
        String body = text.substring(PREFIX.length()).replaceAll("\\s+", "");
        if (body.isEmpty()) {
            throw new InvalidCodeException(Problem.CORRUPT);
        }
        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new InvalidCodeException(Problem.BASE64);
        }
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(compressed.length * 4);
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read == 0 && !inflater.finished() && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new InvalidCodeException(Problem.CORRUPT);
                }
                if (out.size() + read > MAX_RAW_BYTES) {
                    throw new InvalidCodeException(Problem.TOO_LARGE);
                }
                out.write(buffer, 0, read);
            }
            if (inflater.getRemaining() > 0) {
                throw new InvalidCodeException(Problem.CORRUPT);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new InvalidCodeException(Problem.CORRUPT);
        } finally {
            inflater.end();
        }
    }

    private PresetCode() {
    }
}
