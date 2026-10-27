package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvFont;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * A fonte de cada {@link TvFont} no cliente: o {@code Style} que aponta para o {@code font/tv_<nome>.json} do mod
 * (um provedor {@code ttf} com o padrão do jogo como reserva dos caracteres que faltam). {@code MINECRAFT} usa a
 * fonte padrão, sem estilo, e o desenho dela não muda. Os estilos são criados uma vez.
 */
public final class TvFonts {
    private static final Style[] STYLES = new Style[TvFont.values().length];

    static {
        for (TvFont font : TvFont.values()) {
            STYLES[font.ordinal()] = font == TvFont.MINECRAFT ? Style.EMPTY
                    : Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath(WirelessAutomate.MODID,
                            "tv_" + font.getSerializedName()));
        }
    }

    private TvFonts() {
    }

    /** O texto na fonte pedida; o padrão sem estilo é o texto literal comum. */
    public static Component text(TvFont font, String text) {
        return Component.literal(text).withStyle(STYLES[font.ordinal()]);
    }
}
