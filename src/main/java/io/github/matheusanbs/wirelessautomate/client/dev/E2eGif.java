package io.github.matheusanbs.wirelessautomate.client.dev;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Um gravador de GIF mínimo para os testes (o e2e do GIF animado e o JUnit do {@code TvGifReader}): cada quadro com
 * a posição, o atraso, o descarte, o índice transparente e o entrelaçado que o teste quiser, coisa que o
 * {@code ImageIO} só faz com metadados longos. O LZW sai sem compressão de verdade (só códigos literais, com um
 * {@code clear} antes de a tabela crescer), o que todo leitor de GIF aceita. Lógica pura, sem nada do Minecraft.
 */
public final class E2eGif {
    private E2eGif() {
    }

    /**
     * Um quadro: o retângulo, os índices de cor linha a linha (na ordem natural; o gravador entrelaça), o atraso em
     * centésimos, o descarte (0 a 3) e o índice transparente ({@code -1}: nenhum).
     */
    public record Frame(int x, int y, int w, int h, int[] indices, int delayCs, int disposal, int transparent,
            boolean interlaced) {
        /** Um quadro da tela inteira, sem transparência, que fica (descarte 1). */
        public static Frame full(int w, int h, int[] indices, int delayCs) {
            return new Frame(0, 0, w, h, indices, delayCs, 1, -1, false);
        }
    }

    /** O GIF de {@code w} × {@code h} com a paleta global ({@code 0xRRGGBB}) e os quadros, em laço infinito. */
    public static byte[] write(int w, int h, int[] palette, List<Frame> frames) {
        int bits = 1;
        while (1 << bits < palette.length) {
            bits++;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("GIF89a".getBytes());
        u16(out, w);
        u16(out, h);
        out.write(0x80 | 0x70 | bits - 1);
        out.write(0);
        out.write(0);
        for (int i = 0; i < 1 << bits; i++) {
            int color = i < palette.length ? palette[i] : 0;
            out.write(color >> 16 & 0xFF);
            out.write(color >> 8 & 0xFF);
            out.write(color & 0xFF);
        }
        out.writeBytes(new byte[] {0x21, (byte) 0xFF, 11});
        out.writeBytes("NETSCAPE2.0".getBytes());
        out.writeBytes(new byte[] {3, 1, 0, 0, 0});
        for (Frame frame : frames) {
            out.writeBytes(new byte[] {0x21, (byte) 0xF9, 4});
            out.write(frame.disposal << 2 | (frame.transparent >= 0 ? 1 : 0));
            u16(out, frame.delayCs);
            out.write(Math.max(0, frame.transparent));
            out.write(0);
            out.write(0x2C);
            u16(out, frame.x);
            u16(out, frame.y);
            u16(out, frame.w);
            u16(out, frame.h);
            out.write(frame.interlaced ? 0x40 : 0);
            int minSize = Math.max(2, bits);
            out.write(minSize);
            lzw(out, ordered(frame), minSize);
        }
        out.write(0x3B);
        return out.toByteArray();
    }

    /** Os índices na ordem em que vão ao arquivo: as linhas entrelaçadas em quatro passadas, se for o caso. */
    private static int[] ordered(Frame frame) {
        if (!frame.interlaced) {
            return frame.indices;
        }
        int[] out = new int[frame.indices.length];
        int at = 0;
        int[][] passes = {{0, 8}, {4, 8}, {2, 4}, {1, 2}};
        for (int[] pass : passes) {
            for (int row = pass[0]; row < frame.h; row += pass[1]) {
                System.arraycopy(frame.indices, row * frame.w, out, at, frame.w);
                at += frame.w;
            }
        }
        return out;
    }

    /** Só códigos literais, com o {@code clear} antes da tabela chegar ao ponto em que o código cresceria. */
    private static void lzw(ByteArrayOutputStream out, int[] indices, int minSize) {
        int clear = 1 << minSize;
        int size = minSize + 1;
        int run = clear - 2;
        Bits bits = new Bits();
        for (int i = 0; i < indices.length; i++) {
            if (i % run == 0) {
                bits.put(clear, size);
            }
            bits.put(indices[i], size);
        }
        bits.put(clear + 1, size);
        byte[] packed = bits.bytes();
        for (int at = 0; at < packed.length; at += 255) {
            int n = Math.min(255, packed.length - at);
            out.write(n);
            out.write(packed, at, n);
        }
        out.write(0);
    }

    private static final class Bits {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private int buffer;
        private int count;

        void put(int code, int size) {
            buffer |= code << count;
            count += size;
            while (count >= 8) {
                out.write(buffer & 0xFF);
                buffer >>>= 8;
                count -= 8;
            }
        }

        byte[] bytes() {
            if (count > 0) {
                out.write(buffer & 0xFF);
            }
            return out.toByteArray();
        }
    }

    private static void u16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write(value >> 8 & 0xFF);
    }
}
