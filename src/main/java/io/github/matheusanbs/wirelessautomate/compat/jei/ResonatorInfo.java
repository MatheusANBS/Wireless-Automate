package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;
import io.github.matheusanbs.wirelessautomate.resonator.Tuning;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * A página de informação do Núcleo de um tier no JEI: o que o Ressonador faz, as câmaras, o FE por cristal e o teto
 * do Sintonizador, o cristal que rende cheio e quanto a carga dura, o Coração do tier, a integridade, a interferência
 * e, no Ultimate, o Eco. Os valores vêm da config.
 */
final class ResonatorInfo {
    private static final String KEY = "jei.wirelessautomate.resonator.info";

    private ResonatorInfo() {
    }

    static Component[] lines(RouterTier tier) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY));
        Component name = Component.translatable(tier.translationKey());
        int chambers = CoreMath.chambers(tier.ordinal());
        lines.add(Component.translatable(KEY + ".tier." + (chambers == 1 ? "one" : "many"), name,
                TierCoreItem.grouped(chambers), TierCoreItem.grouped(Config.resonatorFePerCrystal(tier)),
                Math.round(Tuning.ceiling(tier.ordinal()) * 100)));
        lines.add(Component.translatable(KEY + ".crystal", fullYield(tier), JeiText.decimal(lifeMinutes(tier))));
        lines.add(Component.translatable(KEY + (tier.previous() == null ? ".heart.craft" : ".heart.upgrade"),
                ModItems.RESONATOR_HEARTS.get(tier).get().getDescription()));
        lines.add(Component.translatable(KEY + ".integrity", Config.crystalIntegrity(), Config.recastCrystals()));
        int radius = Config.interferenceRadius();
        lines.add(radius <= 0 ? Component.translatable(KEY + ".interference.off")
                : Component.translatable(KEY + ".interference", radius,
                        JeiText.decimal(Config.interferencePenalty() * 100),
                        JeiText.decimal(Config.interferenceMax() * 100)));
        if (tier == RouterTier.ULTIMATE) {
            String bonus = JeiText.decimal(Config.ecoBonus() * 100);
            long max = Config.ecoMax();
            lines.add(max > 0 ? Component.translatable(KEY + ".eco.max", bonus, TierCoreItem.grouped(max))
                    : Component.translatable(KEY + ".eco", bonus));
        }
        return lines.toArray(Component[]::new);
    }

    /** O cristal que rende cheio no Núcleo do tier: o Lapidado até o Avançado, o Afinado do tier ou acima depois. */
    private static Component fullYield(RouterTier tier) {
        var tuned = ModItems.TUNED_CRYSTALS.get(tier);
        if (tuned == null) {
            return ModItems.CUT_SIGNAL_CRYSTAL.get().getDescription();
        }
        if (tier.next() == null) {
            return tuned.get().getDescription();
        }
        return Component.translatable(KEY + ".tuned", tuned.get().getDescription());
    }

    /** Minutos que a carga do Lapidado (e do Afinado) dura no Núcleo do tier a 100% de geração. */
    private static double lifeMinutes(RouterTier tier) {
        long life = CoreMath.crystalLife(Config.crystalLifeTicks(true), Config.crystalLifeTicks(tier.ordinal()));
        return Math.round(life * 10.0 / CoreMath.TICKS_PER_MINUTE) / 10.0;
    }
}
