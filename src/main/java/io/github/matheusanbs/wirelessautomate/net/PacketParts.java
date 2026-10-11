package io.github.matheusanbs.wirelessautomate.net;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Divide um pacote grande em partes e as junta do outro lado (lógica pura, com JUnit). Porte 1.20.1: o Forge
 * não divide pacotes como o NeoForge 1.21; o {@link PayloadRegistrar} manda um payload acima do teto do vanilla
 * em partes de {@link Part} e o outro lado o decodifica quando chega a última.
 *
 * <p>Cada parte leva o tamanho total e o deslocamento dela. As partes de um pacote chegam em ordem e seguidas (uma
 * conexão é um fluxo só, e os handlers rodam em ordem na thread principal), e quem manda nunca abandona um pacote
 * pela metade. Por isso qualquer desvio é erro de protocolo ({@link IllegalStateException}, e a montagem fica
 * vazia): uma parte com deslocamento 0 com outro pacote pela metade, um deslocamento fora de ordem (repetido ou
 * pulado), um total diferente do começado ou acima do teto do lado que recebe. Quem usa trata o erro como o vanilla
 * trata um pacote inválido: desconecta ({@link PayloadRegistrar}).
 *
 * <p><b>Memória:</b> o total declarado na primeira parte não é alocado de uma vez (um cliente hostil declararia o
 * teto com um pacote de poucos bytes). O buffer começa do tamanho da primeira parte e dobra conforme os bytes
 * chegam, até o total: a memória segue o que de fato chegou.
 */
public final class PacketParts {
    private PacketParts() {
    }

    /**
     * Uma parte: {@code bytes} vão no deslocamento {@code offset} de um pacote de {@code total} bytes.
     */
    public record Part(int total, int offset, byte[] bytes) {
        public Part {
            if (total <= 0 || offset < 0 || bytes.length == 0 || offset + bytes.length > total
                    || offset + bytes.length < 0) {
                throw new IllegalArgumentException("Parte inválida: total " + total + ", deslocamento " + offset
                        + ", " + bytes.length + " bytes");
            }
        }

        /** É a última do pacote. */
        public boolean last() {
            return offset + bytes.length == total;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Part part && total == part.total && offset == part.offset
                    && Arrays.equals(bytes, part.bytes);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * total + offset) + Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return "Part[total=" + total + ", offset=" + offset + ", bytes=" + bytes.length + "]";
        }
    }

    /** Partes de no máximo {@code partSize} bytes, em ordem. {@code data} não pode ser vazio. */
    public static List<Part> split(byte[] data, int partSize) {
        if (partSize <= 0) {
            throw new IllegalArgumentException("Tamanho de parte inválido: " + partSize);
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("Pacote vazio");
        }
        List<Part> parts = new ArrayList<>((data.length + partSize - 1) / partSize);
        for (int offset = 0; offset < data.length; offset += partSize) {
            parts.add(new Part(data.length, offset, Arrays.copyOfRange(data, offset,
                    Math.min(data.length, offset + partSize))));
        }
        return parts;
    }

    /** A montagem de um lado de uma conexão. Não é thread-safe: use numa thread só (a principal). */
    public static final class Assembly {
        private final int max;
        private byte @Nullable [] buffer;
        private int total;
        private int filled;

        /** @param max o maior pacote aceito, em bytes */
        public Assembly(int max) {
            this.max = max;
        }

        /** Um pacote está pela metade. */
        public boolean pending() {
            return buffer != null;
        }

        /** Bytes guardados agora (para os testes: segue o que chegou, não o total declarado). */
        public int allocated() {
            return buffer == null ? 0 : buffer.length;
        }

        /** Esvazia (a conexão caiu). */
        public void reset() {
            buffer = null;
            total = 0;
            filled = 0;
        }

        /**
         * Junta a parte. Devolve o pacote inteiro quando ela é a última, ou {@code null}.
         *
         * @throws IllegalStateException erro de protocolo (ver a classe); a montagem fica vazia
         */
        public byte @Nullable [] accept(Part part) {
            if (part.offset() == 0) {
                if (buffer != null) {
                    String state = "deslocamento " + filled + " de " + total;
                    reset();
                    throw new IllegalStateException("Pacote novo com outro pela metade (" + state + "): " + part);
                }
                if (part.total() > max) {
                    throw new IllegalStateException("Pacote de " + part.total() + " bytes passa do teto de " + max);
                }
                total = part.total();
                filled = 0;
                buffer = new byte[part.bytes().length];
            } else if (buffer == null || part.total() != total || part.offset() != filled) {
                String expected = buffer == null ? "nenhum pacote começado" : "deslocamento " + filled + " de " + total;
                reset();
                throw new IllegalStateException("Parte fora de ordem: " + part + " (esperava " + expected + ")");
            }
            int end = filled + part.bytes().length;
            if (end > buffer.length) {
                buffer = Arrays.copyOf(buffer, (int) Math.min(total, Math.max(end, 2L * buffer.length)));
            }
            System.arraycopy(part.bytes(), 0, buffer, filled, part.bytes().length);
            filled = end;
            if (filled < total) {
                return null;
            }
            byte[] whole = buffer.length == total ? buffer : Arrays.copyOf(buffer, total);
            reset();
            return whole;
        }
    }
}
