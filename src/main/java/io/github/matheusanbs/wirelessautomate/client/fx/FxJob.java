package io.github.matheusanbs.wirelessautomate.client.fx;

import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Um efeito de um quadro: o dono (o BER, por exemplo) o anota na {@link FxQueue} e o {@link FxStages} o desenha no
 * estágio certo. A passada de sombra não recebe efeito: quem confere é o {@link FxQueue#submit}, que recusa o job nela.
 */
public interface FxJob {
    /** Pede os vértices (relativos à câmera) ao {@code buffers}, que é o do {@link FxBatch}. */
    void draw(FxContext ctx, MultiBufferSource buffers);

    /** A distância ao quadrado da câmera até o centro do efeito, para o corte de 48 blocos. */
    double distanceSq(FxContext ctx);
}
