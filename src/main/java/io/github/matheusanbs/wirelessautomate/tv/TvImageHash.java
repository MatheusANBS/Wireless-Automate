package io.github.matheusanbs.wirelessautomate.tv;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * O resumo de uma imagem enviada (puro, com JUnit): o SHA-256 dos bytes do arquivo em 64 dígitos hexadecimais
 * minúsculos, que é o nome do arquivo no armazém e no cache do cliente e a chave em tudo. O id da imagem num layout
 * ({@link #idOf}) sai dos primeiros 16 bytes do resumo, com a versão 8 do UUID: a peça Imagem e o fundo em mosaico
 * apontam uma pixel art ou uma imagem pelo mesmo campo de id, e um id de arte (UUID aleatório, versão 4) nunca é o de
 * uma imagem.
 */
public final class TvImageHash {
    public static final int LENGTH = 64;
    public static final int BYTES = 32;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private TvImageHash() {
    }

    /** O SHA-256 dos bytes, em hexadecimal minúsculo. */
    public static String of(byte[] data) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
            out[i * 2 + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(out);
    }

    /** Os 32 bytes de um resumo válido (o pacote leva assim, metade do texto). */
    public static byte[] bytes(String hash) {
        byte[] out = new byte[BYTES];
        for (int i = 0; i < BYTES; i++) {
            out[i] = (byte) (Character.digit(hash.charAt(i * 2), 16) << 4 | Character.digit(hash.charAt(i * 2 + 1), 16));
        }
        return out;
    }

    /** Se o texto é um resumo: 64 dígitos hexadecimais minúsculos (o que vira nome de arquivo, sem caminho). */
    public static boolean valid(String hash) {
        if (hash == null || hash.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < LENGTH; i++) {
            char c = hash.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) {
                return false;
            }
        }
        return true;
    }

    /** O id da imagem num layout: os primeiros 16 bytes do resumo, como UUID da versão 8 (variante IETF). */
    public static UUID idOf(String hash) {
        long msb = Long.parseUnsignedLong(hash.substring(0, 16), 16);
        long lsb = Long.parseUnsignedLong(hash.substring(16, 32), 16);
        msb = msb & ~0xF000L | 0x8000L;
        lsb = lsb & 0x3FFFFFFFFFFFFFFFL | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

    /** As 8 primeiras letras, para listas e avisos. */
    public static String brief(String hash) {
        return hash.length() <= 8 ? hash : hash.substring(0, 8);
    }
}
