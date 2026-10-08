package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Como cada tipo de recurso aparece nas telas: cor, ícone, nome, vazão e acesso. O único lugar do
 * cliente com um {@code switch} por tipo; sem {@code default}, para um tipo novo não compilar sem estilo.
 */
public final class ResourceStyle {
    private ResourceStyle() {
    }

    public static int color(ResourceType type) {
        return switch (type) {
            case ITEM -> 0xFFD9A35B;
            case FLUID -> 0xFF3D8BFF;
            case ENERGY -> 0xFFFFB020;
            case CHEMICAL -> 0xFFB45CFF;
            case SOURCE -> 0xFFFF5CC8;
        };
    }

    public static Component name(ResourceType type) {
        return Component.translatable("gui.wirelessautomate.router.type." + type.key());
    }

    /** Vazão já na unidade da tela (itens/s, mB/s ou B/s, FE/t, Source/s). */
    public static Component rate(ResourceType type, long value) {
        String abbreviated = RateFormat.abbreviate(value);
        return switch (type) {
            case ITEM -> Component.translatable("gui.wirelessautomate.router.rate.items", abbreviated);
            case FLUID, CHEMICAL -> value >= 1000
                    ? Component.translatable("gui.wirelessautomate.router.rate.buckets", RateFormat.abbreviate(value / 1000))
                    : Component.translatable("gui.wirelessautomate.router.rate.millibuckets", abbreviated);
            case ENERGY -> Component.translatable("gui.wirelessautomate.router.rate.energy", abbreviated);
            case SOURCE -> Component.translatable("gui.wirelessautomate.router.rate.source", abbreviated);
        };
    }

    /** O que a face oferece (slots, tanques, bateria, Source). */
    public static Component access(ResourceType type, int slots) {
        String prefix = "gui.wirelessautomate.router.access.";
        return switch (type) {
            case ITEM -> slots == 1 ? Component.translatable(prefix + "slot") : Component.translatable(prefix + "slots", slots);
            case FLUID, CHEMICAL -> slots == 1 ? Component.translatable(prefix + "tank")
                    : Component.translatable(prefix + "tanks", slots);
            case ENERGY -> Component.translatable(prefix + "energy");
            case SOURCE -> Component.translatable(prefix + "source");
        };
    }

    public static ResourceLocation icon(ResourceType type) {
        return ResourceLocation.fromNamespaceAndPath(WirelessAutomate.MODID, "textures/gui/type/" + type.key() + ".png");
    }

    /** Lado do ícone em pixels de GUI. */
    public static final int ICON = 9;

    /** Ícone 9 × 9 em ({@code x}, {@code y}): o recorte do canto da textura 16 × 16. */
    public static void drawIcon(GuiGraphics g, ResourceType type, int x, int y) {
        g.blit(icon(type), x, y, 0, 0, ICON, ICON, 16, 16);
    }
}
