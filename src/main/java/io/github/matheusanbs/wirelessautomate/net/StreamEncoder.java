package io.github.matheusanbs.wirelessautomate.net;

/**
 * Escreve um valor num buffer. Imita o {@code net.minecraft.network.codec.StreamEncoder} do 1.21 (porte
 * 1.20.1: o código do {@code main} muda só o import).
 */
@FunctionalInterface
public interface StreamEncoder<O, T> {
    void encode(O buffer, T value);
}
