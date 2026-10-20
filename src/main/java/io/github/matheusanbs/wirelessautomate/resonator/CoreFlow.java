package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * A vazão real de um Núcleo, por Porta de Energia: a energia que saiu de verdade (nunca a simulada) soma num
 * {@code long} por Porta, e o fim de cada lote a converte em faixa (0 a 7, {@link CoreMath#flowBand}) e zera a
 * soma. Também guarda se o buffer terminou o lote cheio. Só o servidor, e nada daqui vai para o NBT: a faixa e o
 * "cheio" seguem ao cliente pela {@link ChamberView}.
 */
final class CoreFlow {
    /** As Portas de Energia na ordem do {@link PortOrder}; o índice delas é o da soma e da faixa. */
    private List<BlockPos> ports = List.of();
    private long[] sums = new long[0];
    private byte[] bands = new byte[0];
    private boolean full;

    /**
     * Redimensiona para as Portas de Energia (já em ordem); devolve se a lista mudou. Igual não mexe em nada, para
     * a revalidação com a estrutura intacta não apagar a soma do lote.
     */
    boolean setPorts(List<BlockPos> sorted) {
        if (ports.equals(sorted)) {
            return false;
        }
        ports = List.copyOf(sorted);
        sums = new long[ports.size()];
        bands = new byte[ports.size()];
        return true;
    }

    /** O índice da Porta de Energia em {@code pos}, ou -1 se ela não está registrada neste Núcleo. */
    int indexOf(BlockPos pos) {
        return ports.indexOf(pos);
    }

    /** Soma o que saiu pela Porta; índice fora da lista (-1) não conta. */
    void add(int port, long amount) {
        if (port >= 0 && port < sums.length) {
            sums[port] = amount > Long.MAX_VALUE - sums[port] ? Long.MAX_VALUE : sums[port] + amount;
        }
    }

    /** Fecha o lote: as somas viram faixas e zeram, e guarda se o buffer terminou cheio. */
    void closeBatch(int ticks, long maxOutput, boolean full) {
        for (int i = 0; i < sums.length; i++) {
            bands[i] = (byte) CoreMath.flowBand(sums[i], ticks, maxOutput);
            sums[i] = 0;
        }
        this.full = full;
    }

    /** As faixas do último lote, uma por Porta de Energia; não altere. */
    byte[] bands() {
        return bands;
    }

    boolean full() {
        return full;
    }
}
