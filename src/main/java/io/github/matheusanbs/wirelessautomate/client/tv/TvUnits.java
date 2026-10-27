package io.github.matheusanbs.wirelessautomate.client.tv;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.tv.TvUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

/**
 * O texto da unidade de uma taxa na Tela, no idioma do jogador: a unidade do tipo ("mB", "FE", nada nos itens e na
 * Source) e a do tempo ("/s", "/t", "/min"), juntas. Montado uma vez por idioma, num array por tipo × unidade; só
 * chamado no redesenho.
 */
public final class TvUnits {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final TvUnit[] UNITS = TvUnit.values();
    private static final String[] LABELS = new String[TYPES.length * UNITS.length];
    private static String language = "";

    private TvUnits() {
    }

    /** "mB/s", "FE/t", "/s"; vazio em {@link TvUnit#NONE}. */
    public static String label(ResourceType type, TvUnit unit) {
        if (unit == TvUnit.NONE) {
            return "";
        }
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!code.equals(language)) {
            build();
            language = code;
        }
        return LABELS[type.ordinal() * UNITS.length + unit.ordinal()];
    }

    private static void build() {
        for (ResourceType type : TYPES) {
            String base = I18n.get("screen.wirelessautomate.tv.unit." + type.key());
            for (TvUnit unit : UNITS) {
                LABELS[type.ordinal() * UNITS.length + unit.ordinal()] = unit == TvUnit.NONE ? ""
                        : base + I18n.get("screen.wirelessautomate.tv.per." + unit.getSerializedName());
            }
        }
    }
}
