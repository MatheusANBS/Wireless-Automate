package io.github.matheusanbs.wirelessautomate.net;

/**
 * Lê um valor de um buffer. Imita o {@code net.minecraft.network.codec.StreamDecoder} do 1.21 (porte
 * 1.20.1: o código do {@code main} muda só o import).
 */
@FunctionalInterface
public interface StreamDecoder<I, T> {
    T decode(I buffer);
}
