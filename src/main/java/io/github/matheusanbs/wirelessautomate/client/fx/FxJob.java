package io.github.matheusanbs.wirelessautomate.client.fx;

import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Um efeito de um quadro: o dono (o BER, por exemplo) o anota na {@link FxQueue} e o {@link FxStages} o desenha no
 * estágio certo. Quem anota confere {@link ShaderPackCheck#shadowPass()} antes: nada de efeito na passada de sombra.
 */
public interface FxJob {
    /** Pede os vértices (relativos à câmera) ao {@code buffers}, que é o do {@link FxBatch}. */
    void draw(FxContext ctx, MultiBufferSource buffers);

    /** A distância ao quadrado da câmera até o centro do efeito, para o corte de 48 blocos. */
    double distanceSq(FxContext ctx);
}
