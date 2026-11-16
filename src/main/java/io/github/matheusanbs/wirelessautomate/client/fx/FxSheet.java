package io.github.matheusanbs.wirelessautomate.client.fx;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.resources.ResourceLocation;

/**
 * Uma folha de efeito: um PNG em {@code textures/fx/<name>.png} com {@code frames} quadros empilhados, cada um com
 * {@code width} × {@code frameHeight} texels. O quadro de cada animação sai do relógio do efeito
 * ({@link #frameAt}), nunca do {@code .mcmeta}. {@code repeatU} marca as tiras que repetem pelo comprimento (o
 * {@code u} passa de 1), que pedem a textura com a repetição ligada.
 */
public record FxSheet(String name, int width, int frameHeight, int frames, Layer layer, boolean repeatU) {
    /** Tinta: alfa, com a luz real, por baixo. Luz: aditiva pré-multiplicada, luz cheia, por cima. */
    public enum Layer { INK, LIGHT }

    /** A folga de meio décimo de texel para dentro do quadro, para o NEAREST não pegar a linha do vizinho. */
    private static final float EDGE = 0.05f;

    public FxSheet {
        if (width <= 0 || frameHeight <= 0 || frames <= 0) {
            throw new IllegalArgumentException(name);
        }
    }

    public ResourceLocation texture() {
        return WirelessAutomate.id("textures/fx/" + name + ".png");
    }

    public int height() {
        return frameHeight * frames;
    }

    public int clampFrame(int frame) {
        return Math.max(0, Math.min(frames - 1, frame));
    }

    /** O v do alto do quadro. */
    public float v0(int frame) {
        return (clampFrame(frame) * frameHeight + EDGE) / height();
    }

    /** O v de baixo do quadro. */
    public float v1(int frame) {
        return ((clampFrame(frame) + 1) * frameHeight - EDGE) / height();
    }

    /** O {@code qd} do filme: {@code floor(t × fps) mod frames}, sempre de 0 a frames − 1. */
    public int frameAt(double seconds, double fps) {
        long k = (long) Math.floor(seconds * fps);
        return (int) (((k % frames) + frames) % frames);
    }

    /** O {@code qf} do filme: {@code floor(f × frames)} limitado ao intervalo. */
    public int frameByFraction(double f) {
        return clampFrame((int) Math.floor(f * frames));
    }
}
