package io.github.matheusanbs.wirelessautomate.client.tv.image;

import java.util.Arrays;

/**
 * Um leitor de GIF quadro a quadro, para o GIF animado grande demais para ter todos os quadros na memória: guarda só
 * o arquivo (os quadros comprimidos em LZW, como vieram) e a tela montada até o quadro da vez, e abre o próximo quando
 * pedido ({@link #next}). Monta cada quadro como um navegador: o quadro anterior sai pelo descarte dele (fica, volta
 * ao transparente ou volta ao que havia antes), os pixels transparentes do quadro deixam ver o de baixo, e as linhas
 * entrelaçadas vão no lugar certo. A tela começa transparente, como no {@code stbi_load_gif_from_memory}.
 * <p>
 * Lógica pura, com JUnit. Um arquivo cortado ou estragado no meio não lança: o quadro que não lê encerra a leitura
 * ({@link #next} devolve falso), e quem toca recomeça do primeiro. Não é seguro entre threads: um quadro por vez.
 */
public final class TvGifReader {
    /** Quantos códigos o LZW do GIF pode ter (12 bits). */
    private static final int MAX_CODES = 4096;

    private final byte[] data;
    private final int width;
    private final int height;
    private final int[] globalTable;
    /** Onde começa o primeiro bloco depois da tabela global (o rebobinar volta aqui). */
    private final int firstBlock;
    /** A tela montada: um pixel por {@code int}, RGBA com o R no byte de baixo (a ordem do STB lida como {@code int}). */
    private final int[] canvas;
    private int[] saved = new int[0];
    private int pos;
    private int frame = -1;
    private boolean ended;
    /** O descarte do último quadro desenhado e o retângulo dele, aplicados antes do próximo. */
    private int lastDisposal;
    private int lastX;
    private int lastY;
    private int lastW;
    private int lastH;
    private final short[] prefix = new short[MAX_CODES];
    private final byte[] suffix = new byte[MAX_CODES];
    private final byte[] stack = new byte[MAX_CODES + 1];

    /** Lê o cabeçalho; lança {@link IllegalArgumentException} se não é um GIF ou a tela não tem tamanho. */
    public TvGifReader(byte[] data) {
        if (data.length < 13 || data[0] != 'G' || data[1] != 'I' || data[2] != 'F') {
            throw new IllegalArgumentException("não é um GIF");
        }
        this.data = data;
        width = u16(data, 6);
        height = u16(data, 8);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("GIF sem tamanho");
        }
        int packed = data[10] & 0xFF;
        int at = 13;
        if ((packed & 0x80) != 0) {
            int size = 2 << (packed & 7);
            if (at + size * 3 > data.length) {
                throw new IllegalArgumentException("GIF cortado na tabela de cores");
            }
            globalTable = table(data, at, size);
            at += size * 3;
        } else {
            globalTable = null;
        }
        firstBlock = at;
        pos = at;
        canvas = new int[width * height];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** O índice do último quadro montado, ou -1 antes do primeiro. */
    public int frame() {
        return frame;
    }

    /** A tela montada até o {@link #frame} atual (não copie por quadro: é o próprio buffer do leitor). */
    public int[] canvas() {
        return canvas;
    }

    /** Volta ao começo: a tela transparente, antes do primeiro quadro. */
    public void rewind() {
        pos = firstBlock;
        frame = -1;
        ended = false;
        lastDisposal = 0;
        Arrays.fill(canvas, 0);
    }

    /** Monta o próximo quadro na tela; falso no fim do arquivo ou se o quadro não lê (a tela fica como estava). */
    public boolean next() {
        if (ended) {
            return false;
        }
        try {
            if (readFrame()) {
                frame++;
                return true;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // Um arquivo que acaba no meio de um bloco: a leitura para aqui.
        }
        ended = true;
        return false;
    }

    /** Lê os blocos até o próximo quadro e o monta; falso no fim. */
    private boolean readFrame() {
        int disposal = 0;
        int transparent = -1;
        while (pos < data.length) {
            int block = data[pos++] & 0xFF;
            if (block == 0x3B) {
                return false;
            }
            if (block == 0x21) {
                int label = data[pos++] & 0xFF;
                if (label == 0xF9 && (data[pos] & 0xFF) >= 4) {
                    int packed = data[pos + 1] & 0xFF;
                    disposal = packed >> 2 & 7;
                    transparent = (packed & 1) != 0 ? data[pos + 4] & 0xFF : -1;
                }
                skipSubBlocks();
            } else if (block == 0x2C) {
                draw(disposal, transparent);
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    /** O descarte do quadro anterior, depois o quadro do descritor em {@link #pos} desenhado por cima. */
    private void draw(int disposal, int transparent) {
        int fx = u16(data, pos);
        int fy = u16(data, pos + 2);
        int fw = u16(data, pos + 4);
        int fh = u16(data, pos + 6);
        int packed = data[pos + 8] & 0xFF;
        pos += 9;
        int[] colors = globalTable;
        if ((packed & 0x80) != 0) {
            int size = 2 << (packed & 7);
            colors = table(data, pos, size);
            pos += size * 3;
        }
        boolean interlaced = (packed & 0x40) != 0;
        dispose();
        if (disposal == 3) {
            saved = Arrays.copyOf(canvas, canvas.length);
        }
        byte[] indices = lzw(fw * fh);
        paint(indices, colors, transparent, fx, fy, fw, fh, interlaced);
        lastDisposal = disposal;
        lastX = fx;
        lastY = fy;
        lastW = fw;
        lastH = fh;
    }

    /** Aplica o descarte do último quadro: 2 limpa o retângulo dele, 3 volta a tela ao que era antes dele. */
    private void dispose() {
        if (frame < 0) {
            return;
        }
        if (lastDisposal == 2) {
            int x0 = Math.min(width, lastX);
            int x1 = Math.min(width, lastX + lastW);
            int y1 = Math.min(height, lastY + lastH);
            for (int y = lastY; y < y1; y++) {
                Arrays.fill(canvas, y * width + x0, y * width + x1, 0);
            }
        } else if (lastDisposal == 3 && saved.length == canvas.length) {
            System.arraycopy(saved, 0, canvas, 0, canvas.length);
        }
    }

    private void paint(byte[] indices, int[] colors, int transparent, int fx, int fy, int fw, int fh,
            boolean interlaced) {
        for (int row = 0; row < fh; row++) {
            int y = fy + (interlaced ? interlacedRow(row, fh) : row);
            if (y >= height) {
                continue;
            }
            for (int col = 0; col < fw; col++) {
                int x = fx + col;
                if (x >= width) {
                    break;
                }
                int index = indices[row * fw + col] & 0xFF;
                if (index == transparent) {
                    continue;
                }
                canvas[y * width + x] = colors != null && index < colors.length ? colors[index] : 0xFF000000;
            }
        }
    }

    /** A linha da imagem da {@code row}-ésima linha guardada, na ordem entrelaçada (passadas de 8, 8, 4 e 2). */
    static int interlacedRow(int row, int height) {
        int pass1 = (height + 7) / 8;
        if (row < pass1) {
            return row * 8;
        }
        row -= pass1;
        int pass2 = (height + 3) / 8;
        if (row < pass2) {
            return row * 8 + 4;
        }
        row -= pass2;
        int pass3 = (height + 1) / 4;
        if (row < pass3) {
            return row * 4 + 2;
        }
        return (row - pass3) * 2 + 1;
    }

    /**
     * Os índices de cor do quadro: o LZW dos sub-blocos a partir de {@link #pos}, até {@code count} pixels (os que
     * faltam ficam 0). Deixa {@link #pos} depois do bloco final.
     */
    private byte[] lzw(int count) {
        byte[] out = new byte[count];
        int minSize = data[pos++] & 0xFF;
        if (minSize < 1 || minSize > 11) {
            skipSubBlocks();
            return out;
        }
        int clear = 1 << minSize;
        int end = clear + 1;
        int size = minSize + 1;
        int next = clear + 2;
        int old = -1;
        int first = 0;
        for (int i = 0; i < clear; i++) {
            prefix[i] = -1;
            suffix[i] = (byte) i;
        }
        int written = 0;
        int bits = 0;
        int buffer = 0;
        int blockLeft = 0;
        boolean done = false;
        while (!done) {
            while (bits < size) {
                if (blockLeft == 0) {
                    blockLeft = data[pos++] & 0xFF;
                    if (blockLeft == 0) {
                        return out;
                    }
                }
                buffer |= (data[pos++] & 0xFF) << bits;
                bits += 8;
                blockLeft--;
            }
            int code = buffer & (1 << size) - 1;
            buffer >>>= size;
            bits -= size;
            if (code == clear) {
                size = minSize + 1;
                next = clear + 2;
                old = -1;
                continue;
            }
            if (code == end) {
                done = true;
                continue;
            }
            int top = 0;
            int in = code;
            if (old == -1) {
                if (code >= clear) {
                    done = true;
                    continue;
                }
                first = code;
                if (written < count) {
                    out[written++] = (byte) code;
                }
                old = code;
                continue;
            }
            if (code >= next) {
                if (code > next) {
                    done = true;
                    continue;
                }
                stack[top++] = (byte) first;
                code = old;
            }
            while (code >= clear) {
                stack[top++] = suffix[code];
                code = prefix[code];
            }
            first = suffix[code] & 0xFF;
            stack[top++] = (byte) first;
            while (top > 0 && written < count) {
                out[written++] = stack[--top];
            }
            if (next < MAX_CODES) {
                prefix[next] = (short) old;
                suffix[next] = (byte) first;
                next++;
                if (next == 1 << size && size < 12) {
                    size++;
                }
            }
            old = in;
        }
        skipSubBlocksFrom(blockLeft);
        return out;
    }

    /** Pula o resto do sub-bloco atual ({@code left} bytes) e os seguintes, até o terminador. */
    private void skipSubBlocksFrom(int left) {
        pos += left;
        skipSubBlocks();
    }

    private void skipSubBlocks() {
        while (pos < data.length) {
            int size = data[pos++] & 0xFF;
            if (size == 0) {
                return;
            }
            pos += size;
        }
    }

    /**
     * As durações de cada quadro, em ms (o atraso do GIF em centésimos vezes 10, 0 quando o quadro não diz), sem abrir
     * os quadros: só o caminho pelos blocos. Até {@code maxFrames}; um arquivo cortado devolve os que leram.
     */
    public static int[] delays(byte[] data, int maxFrames) {
        TvGifReader reader;
        try {
            reader = new TvGifReader(data);
        } catch (IllegalArgumentException e) {
            return new int[0];
        }
        int[] out = new int[Math.max(1, Math.min(maxFrames, 64))];
        int count = 0;
        int delay = 0;
        int at = reader.firstBlock;
        try {
            while (at < data.length && count < maxFrames) {
                int block = data[at++] & 0xFF;
                if (block == 0x21) {
                    int label = data[at++] & 0xFF;
                    if (label == 0xF9 && (data[at] & 0xFF) >= 4) {
                        delay = u16(data, at + 2) * 10;
                    }
                    at = afterSubBlocks(data, at);
                } else if (block == 0x2C) {
                    int packed = data[at + 8] & 0xFF;
                    at += 9;
                    if ((packed & 0x80) != 0) {
                        at += (2 << (packed & 7)) * 3;
                    }
                    at = afterSubBlocks(data, at + 1);
                    if (count == out.length) {
                        out = Arrays.copyOf(out, Math.min(maxFrames, out.length * 2));
                    }
                    out[count++] = delay;
                    delay = 0;
                } else {
                    break;
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // O arquivo acabou no meio: valem os quadros que leram.
        }
        return Arrays.copyOf(out, count);
    }

    private static int afterSubBlocks(byte[] data, int at) {
        while (at < data.length) {
            int size = data[at++] & 0xFF;
            if (size == 0) {
                return at;
            }
            at += size;
        }
        return at;
    }

    /** A tabela de cores em RGBA opaco, com o R no byte de baixo. */
    private static int[] table(byte[] data, int at, int size) {
        int[] colors = new int[size];
        for (int i = 0; i < size; i++) {
            int r = data[at + i * 3] & 0xFF;
            int g = data[at + i * 3 + 1] & 0xFF;
            int b = data[at + i * 3 + 2] & 0xFF;
            colors[i] = 0xFF000000 | b << 16 | g << 8 | r;
        }
        return colors;
    }

    private static int u16(byte[] data, int at) {
        return data[at] & 0xFF | (data[at + 1] & 0xFF) << 8;
    }
}
