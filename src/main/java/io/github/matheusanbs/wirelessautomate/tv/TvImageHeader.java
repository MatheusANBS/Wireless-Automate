package io.github.matheusanbs.wirelessautomate.tv;

import org.jetbrains.annotations.Nullable;

/**
 * O cabeçalho de uma imagem enviada (puro, com JUnit): o formato pela assinatura, a largura e a altura e, no GIF, o
 * número de quadros, tudo sem decodificar um pixel. O servidor confere assim o arquivo que guarda (nunca o decodifica),
 * e o cliente confere de novo antes de entregar ao leitor STB, para nenhuma imagem que mente no tamanho virar uma bomba
 * de memória.
 * <ul>
 * <li><b>PNG:</b> a assinatura de 8 bytes e o {@code IHDR} logo depois (13 bytes).</li>
 * <li><b>JPG:</b> {@code FF D8} e os segmentos andados pelo tamanho até o primeiro {@code SOF0}, {@code SOF1} ou
 * {@code SOF2} (os que o STB abre: sequencial e progressivo com Huffman); um {@code SOS} antes dele, ou outro
 * {@code SOF}, é recusado.</li>
 * <li><b>GIF:</b> {@code GIF87a}/{@code GIF89a}, o tamanho da tela lógica e os blocos andados até o fim: cada
 * descritor de imagem é um quadro (as sub-tramas LZW são puladas pelo tamanho delas, sem descomprimir), e um quadro
 * que sai da tela lógica é recusado, como o STB faz. Passou de {@code maxFrames}, para de contar.</li>
 * </ul>
 */
public final class TvImageHeader {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private TvImageHeader() {
    }

    /** O que o cabeçalho diz: formato, tamanho e quadros (1 fora do GIF). */
    public record Info(TvImageFormat format, int width, int height, int frames) {
    }

    /** O motivo de uma recusa pelo cabeçalho. */
    public enum Problem { BAD_FORMAT, TOO_LARGE, TOO_MANY_FRAMES }

    /** A leitura: {@code info} quando vale, senão o {@code problem} (e o {@code info} que deu para ler, se deu). */
    public record Result(@Nullable Info info, @Nullable Problem problem) {
        public boolean ok() {
            return problem == null && info != null;
        }
    }

    private static final Result BAD = new Result(null, Problem.BAD_FORMAT);

    /**
     * Lê o cabeçalho de {@code data}: recusa assinatura que não é PNG, JPG nem GIF, estrutura quebrada, um lado fora
     * de 1 a {@code maxSize} e GIF com mais de {@code maxFrames} quadros.
     */
    public static Result read(byte[] data, int maxSize, int maxFrames) {
        Info info;
        if (startsWith(data, PNG_SIGNATURE)) {
            info = png(data);
        } else if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            info = jpg(data);
        } else if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8'
                && (data[4] == '7' || data[4] == '9') && data[5] == 'a') {
            info = gif(data, maxFrames);
        } else {
            return BAD;
        }
        if (info == null) {
            return BAD;
        }
        if (info.width < 1 || info.height < 1 || info.width > maxSize || info.height > maxSize) {
            return new Result(info, Problem.TOO_LARGE);
        }
        if (info.frames > maxFrames) {
            return new Result(info, Problem.TOO_MANY_FRAMES);
        }
        return new Result(info, null);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int u8(byte[] data, int at) {
        return data[at] & 0xFF;
    }

    private static int u16be(byte[] data, int at) {
        return (data[at] & 0xFF) << 8 | data[at + 1] & 0xFF;
    }

    private static int u16le(byte[] data, int at) {
        return data[at] & 0xFF | (data[at + 1] & 0xFF) << 8;
    }

    private static long u32be(byte[] data, int at) {
        return (long) u16be(data, at) << 16 | u16be(data, at + 2);
    }

    /** O {@code IHDR}, o primeiro bloco depois da assinatura, com 13 bytes. */
    private static @Nullable Info png(byte[] data) {
        if (data.length < 8 + 8 + 13 || u32be(data, 8) != 13 || data[12] != 'I' || data[13] != 'H' || data[14] != 'D'
                || data[15] != 'R') {
            return null;
        }
        long w = u32be(data, 16);
        long h = u32be(data, 20);
        return new Info(TvImageFormat.PNG, (int) Math.min(w, Integer.MAX_VALUE), (int) Math.min(h, Integer.MAX_VALUE),
                1);
    }

    /** Os segmentos pelo tamanho até o primeiro SOF que o STB abre. */
    private static @Nullable Info jpg(byte[] data) {
        int at = 2;
        while (at + 4 <= data.length) {
            if (u8(data, at) != 0xFF) {
                return null;
            }
            int marker = u8(data, at + 1);
            if (marker == 0xFF) {
                at++;
                continue;
            }
            if (marker == 0x01 || marker >= 0xD0 && marker <= 0xD8) {
                at += 2;
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) {
                return null;
            }
            int length = u16be(data, at + 2);
            if (length < 2) {
                return null;
            }
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                if (marker > 0xC2 || length < 8 || at + 9 > data.length) {
                    return null;
                }
                return new Info(TvImageFormat.JPG, u16be(data, at + 7), u16be(data, at + 5), 1);
            }
            at += 2 + length;
        }
        return null;
    }

    /** A tela lógica e os blocos até o fim (ou o fim dos dados depois de um quadro inteiro). */
    private static @Nullable Info gif(byte[] data, int maxFrames) {
        if (data.length < 13) {
            return null;
        }
        int width = u16le(data, 6);
        int height = u16le(data, 8);
        int packed = u8(data, 10);
        int at = 13;
        if ((packed & 0x80) != 0) {
            at += 3 << ((packed & 7) + 1);
        }
        int frames = 0;
        while (at < data.length) {
            int block = u8(data, at);
            if (block == 0x3B) {
                break;
            }
            if (block == 0x21) {
                if (at + 2 > data.length) {
                    return null;
                }
                at = skipSubBlocks(data, at + 2);
            } else if (block == 0x2C) {
                if (at + 10 > data.length) {
                    return null;
                }
                int left = u16le(data, at + 1);
                int top = u16le(data, at + 3);
                int w = u16le(data, at + 5);
                int h = u16le(data, at + 7);
                int local = u8(data, at + 9);
                if (left + w > width || top + h > height) {
                    return null;
                }
                at += 10;
                if ((local & 0x80) != 0) {
                    at += 3 << ((local & 7) + 1);
                }
                // O tamanho mínimo do código LZW e as sub-tramas da imagem.
                at = skipSubBlocks(data, at + 1);
                if (at < 0) {
                    return null;
                }
                frames++;
                if (frames > maxFrames) {
                    break;
                }
                continue;
            } else {
                return null;
            }
            if (at < 0) {
                return null;
            }
        }
        return frames == 0 ? null : new Info(TvImageFormat.GIF, width, height, frames);
    }

    /** Pula as sub-tramas a partir de {@code at} até a de tamanho 0; devolve a posição depois dela, ou -1. */
    private static int skipSubBlocks(byte[] data, int at) {
        while (at >= 0 && at < data.length) {
            int size = u8(data, at);
            at += 1 + size;
            if (size == 0) {
                return at <= data.length ? at : -1;
            }
        }
        return -1;
    }
}
