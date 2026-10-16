package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint;
import io.github.matheusanbs.wirelessautomate.resonator.Tuning;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * O Ressonador de Sinal no JEI: as categorias, as receitas, os catalisadores e a página de informação dos Núcleos.
 * O plugin só delega para cá, uma etapa de registro por método.
 */
final class ResonatorJei {
    private ResonatorJei() {
    }

    static void registerCategories(IRecipeCategoryRegistration registration) {
        var helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new ResonatorStructureCategory(helper), new ResonatorRechargeCategory(helper));
    }

    static void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(ResonatorRechargeCategory.TYPE, List.of(
                ResonatorRechargeCategory.Recharge.of(ModItems.SIGNAL_CRYSTAL.get()),
                ResonatorRechargeCategory.Recharge.of(ModItems.CUT_SIGNAL_CRYSTAL.get())));
        registration.addRecipes(ResonatorStructureCategory.TYPE, List.of(
                ResonatorStructureCategory.Structure.of(ResonatorBlueprint.Layout.VITRINE),
                ResonatorStructureCategory.Structure.of(ResonatorBlueprint.Layout.FAIXA)));
        registration.addItemStackInfo(cores(), info());
    }

    static void registerCatalysts(IRecipeCatalystRegistration registration) {
        for (ItemStack core : cores()) {
            registration.addRecipeCatalysts(ResonatorRechargeCategory.TYPE, core);
            registration.addRecipeCatalysts(ResonatorStructureCategory.TYPE, core);
        }
    }

    /** Os tiers carregados, em ordem. */
    private static List<RouterTier> loadedTiers() {
        return java.util.Arrays.stream(RouterTier.values()).filter(RouterTier::loaded).toList();
    }

    /** O Núcleo de cada tier carregado. */
    static List<ItemStack> cores() {
        List<ItemStack> cores = new ArrayList<>();
        for (RouterTier tier : loadedTiers()) {
            cores.add(ResonanceCoreItem.withTier(ModItems.RESONANCE_CORE.get(), tier));
        }
        return cores;
    }

    /** O que o Ressonador faz e uma linha por tier carregado: câmaras, FE por tick de um cristal e teto do Sintonizador. */
    private static Component[] info() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("jei.wirelessautomate.resonator.info"));
        for (RouterTier tier : loadedTiers()) {
            int chambers = CoreMath.chambers(tier.ordinal());
            lines.add(Component.translatable(
                    "jei.wirelessautomate.resonator.info.tier." + (chambers == 1 ? "one" : "many"),
                    Component.translatable(tier.translationKey()), TierCoreItem.grouped(chambers),
                    TierCoreItem.grouped(Config.resonatorFePerCrystal(tier)),
                    Math.round(Tuning.ceiling(tier.ordinal()) * 100)));
        }
        return lines.toArray(Component[]::new);
    }
}
