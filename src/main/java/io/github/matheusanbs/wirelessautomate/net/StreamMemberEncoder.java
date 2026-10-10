package io.github.matheusanbs.wirelessautomate.net;

/**
 * Escritor "de membro" ({@code valor.write(buf)}). Imita o
 * {@code net.minecraft.network.codec.StreamMemberEncoder} do 1.21.
 */
@FunctionalInterface
public interface StreamMemberEncoder<O, T> {
    void encode(T value, O buffer);
}
