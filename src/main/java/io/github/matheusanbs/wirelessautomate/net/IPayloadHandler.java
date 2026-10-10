package io.github.matheusanbs.wirelessautomate.net;

/**
 * Handler de um pacote recebido. Imita o {@code net.neoforged.neoforge.network.handling.IPayloadHandler} do
 * 1.21; o {@link PayloadRegistrar} o chama na thread principal do lado que recebeu.
 */
@FunctionalInterface
public interface IPayloadHandler<T extends CustomPacketPayload> {
    void handle(T payload, IPayloadContext context);
}
