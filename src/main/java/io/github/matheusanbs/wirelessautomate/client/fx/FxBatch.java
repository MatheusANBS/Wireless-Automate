package io.github.matheusanbs.wirelessautomate.client.fx;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * O buffer dos efeitos: um {@link ByteBufferBuilder} por tipo de {@link FxTypes#all()}, na ordem das folhas (a tinta
 * sai antes da luz no {@code endBatch}), e um construtor de reserva para o que não for desses tipos. Sem shaderpack é
 * esvaziado no estágio do mundo; com pacote guarda os vértices até o gancho tardio. Criado uma vez; os construtores
 * começam pequenos e crescem sozinhos.
 */
public final class FxBatch {
    private final MultiBufferSource.BufferSource buffer;
    private final Counting source = new Counting();
    private int requests;

    public FxBatch() {
        LinkedHashMap<RenderType, ByteBufferBuilder> fixed = new LinkedHashMap<>();
        for (RenderType type : FxTypes.all()) {
            fixed.put(type, new ByteBufferBuilder(4096));
        }
        buffer = MultiBufferSource.immediateWithBuffers(fixed, new ByteBufferBuilder(4096));
    }

    /** A fonte de vértices dos {@link FxJob}s; conta os pedidos para o {@link #hasData()}. */
    public MultiBufferSource source() {
        return source;
    }

    /** Desenha o que foi pedido, na ordem dos tipos, e zera a contagem. */
    public void flush() {
        requests = 0;
        buffer.endBatch();
    }

    /** Se algum job pediu vértices desde o último {@link #flush()}. */
    public boolean hasData() {
        return requests > 0;
    }

    /** Conta os pedidos sem alocar por quadro (uma instância só). */
    private final class Counting implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType type) {
            requests++;
            return buffer.getBuffer(type);
        }
    }
}
