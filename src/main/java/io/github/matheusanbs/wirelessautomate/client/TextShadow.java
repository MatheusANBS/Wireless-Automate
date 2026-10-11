package io.github.matheusanbs.wirelessautomate.client;

import net.minecraft.client.gui.components.EditBox;

/**
 * Sombra do texto de um {@link EditBox}, como o {@code setTextShadow} do NeoForge no main. Porte 1.20.1: o
 * {@code EditBox} do Forge sempre desenha com sombra; o mixin de cliente {@code client/mixin/EditBoxMixin}
 * aplica esta interface ao {@code EditBox} e troca a sombra dos {@code drawString} do {@code renderWidget}.
 * Sem o mixin aplicado (outro mod em conflito), {@link #setTextShadow(EditBox, boolean)} não faz nada e o
 * campo fica com a sombra de sempre.
 */
public interface TextShadow {
    void wirelessautomate$setTextShadow(boolean shadow);

    boolean wirelessautomate$textShadow();

    /** O {@code box.setTextShadow(shadow)} do main. */
    static void setTextShadow(EditBox box, boolean shadow) {
        if (box instanceof TextShadow target) {
            target.wirelessautomate$setTextShadow(shadow);
        }
    }

    /** O {@code box.isTextShadow()} do main: verdadeiro sem o mixin. */
    static boolean isTextShadow(EditBox box) {
        return !(box instanceof TextShadow target) || target.wirelessautomate$textShadow();
    }
}
