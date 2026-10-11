package io.github.matheusanbs.wirelessautomate.client.mixin;

import io.github.matheusanbs.wirelessautomate.client.TextShadow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Porte 1.20.1: o {@code EditBox} desenha o texto, a dica, a sugestão e o cursor "_" com
 * {@code drawString(font, texto, x, y, cor)}, que tem sombra fixa. Aqui cada um desses {@code drawString} do
 * {@code renderWidget} passa a usar a sombra do campo ({@link TextShadow}, ligada por padrão, como o
 * {@code setTextShadow} do NeoForge no main). Com a sombra ligada o desenho é o mesmo do jogo. Sem efeito se
 * outro mod mudar o método ({@code require = 0}; o config não é obrigatório).
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin implements TextShadow {
    /** Sem sombra? Falso por padrão (o campo do jogo tem sombra). */
    @Unique
    private boolean wirelessautomate$noShadow;

    @Override
    public void wirelessautomate$setTextShadow(boolean shadow) {
        wirelessautomate$noShadow = !shadow;
    }

    @Override
    public boolean wirelessautomate$textShadow() {
        return !wirelessautomate$noShadow;
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"),
            require = 0)
    private int wirelessautomate$drawString(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        return graphics.drawString(font, text, x, y, color, !wirelessautomate$noShadow);
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"),
            require = 0)
    private int wirelessautomate$drawSequence(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y,
            int color) {
        return graphics.drawString(font, text, x, y, color, !wirelessautomate$noShadow);
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"),
            require = 0)
    private int wirelessautomate$drawComponent(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        return graphics.drawString(font, text, x, y, color, !wirelessautomate$noShadow);
    }
}
