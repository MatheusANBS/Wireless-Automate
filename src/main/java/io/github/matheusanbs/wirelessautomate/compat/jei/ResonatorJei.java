package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CoreCatalyst;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * O Ressonador de Sinal no JEI: as categorias, as receitas, os catalisadores e a página de informação de cada Núcleo
 * ({@link ResonatorInfo}). O plugin só delega para cá, uma etapa de registro por método. Só entram os tiers carregados:
 * os cristais, os Pós Carregados e os Núcleos dos tiers do Allthemodium só com o mod.
 */
final class ResonatorJei {
    private ResonatorJei() {
    }

    static void registerCategories(IRecipeCategoryRegistration registration) {
        var helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new ResonatorStructureCategory(helper), new ResonatorRechargeCategory(helper),
                new ResonatorRecastCategory(helper));
    }

    static void registerRecipes(IRecipeRegistration registration) {
        List<ItemStack> cores = new ArrayList<>();
        List<ItemStack> catalysts = new ArrayList<>();
        catalystPairs(cores, catalysts);
        List<ResonatorRechargeCategory.Recharge> recharges = new ArrayList<>();
        List<ResonatorRecastCategory.Recast> recasts = new ArrayList<>();
        for (Item crystal : crystals()) {
            recharges.add(ResonatorRechargeCategory.Recharge.of(crystal, List.copyOf(cores), List.copyOf(catalysts)));
            recasts.add(ResonatorRecastCategory.Recast.of(crystal));
        }
        registration.addRecipes(ResonatorRechargeCategory.TYPE, recharges);
        registration.addRecipes(ResonatorRecastCategory.TYPE, recasts);
        registration.addRecipes(ResonatorStructureCategory.TYPE, List.of(
                ResonatorStructureCategory.Structure.of(ResonatorBlueprint.Layout.VITRINE),
                ResonatorStructureCategory.Structure.of(ResonatorBlueprint.Layout.FAIXA)));
        for (RouterTier tier : loadedTiers()) {
            registration.addItemStackInfo(ResonanceCoreItem.withTier(ModItems.RESONANCE_CORE.get(), tier),
                    ResonatorInfo.lines(tier));
        }
    }

    static void registerCatalysts(IRecipeCatalystRegistration registration) {
        for (ItemStack core : cores()) {
            registration.addRecipeCatalysts(ResonatorRechargeCategory.TYPE, core);
            registration.addRecipeCatalysts(ResonatorStructureCategory.TYPE, core);
        }
        registration.addRecipeCatalysts(ResonatorRecastCategory.TYPE, new ItemStack(Items.CRAFTING_TABLE));
    }

    /** Os tiers carregados, em ordem. */
    static List<RouterTier> loadedTiers() {
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

    /** Os cristais do Núcleo: o de Sinal, o Lapidado e os Afinados dos tiers carregados. */
    private static List<Item> crystals() {
        List<Item> crystals = new ArrayList<>(List.of(ModItems.SIGNAL_CRYSTAL.get(), ModItems.CUT_SIGNAL_CRYSTAL.get()));
        for (var tuned : ModItems.TUNED_CRYSTALS.entrySet()) {
            if (tuned.getKey().loaded()) {
                crystals.add(tuned.getValue().get());
            }
        }
        return crystals;
    }

    /**
     * Os pares Núcleo × catalisador que a regra do {@link CoreCatalyst} aceita, por tier carregado: listas paralelas,
     * do mesmo tamanho, para o JEI girá-las juntas.
     */
    private static void catalystPairs(List<ItemStack> cores, List<ItemStack> catalysts) {
        List<ItemStack> candidates = new ArrayList<>(List.of(new ItemStack(Items.REDSTONE),
                new ItemStack(ModItems.RESONANCE_DUST.get())));
        for (var dust : ModItems.CHARGED_DUSTS.entrySet()) {
            if (dust.getKey().loaded()) {
                candidates.add(new ItemStack(dust.getValue().get()));
            }
        }
        for (RouterTier tier : loadedTiers()) {
            ItemStack core = ResonanceCoreItem.withTier(ModItems.RESONANCE_CORE.get(), tier);
            for (ItemStack candidate : candidates) {
                if (CoreCatalyst.accepts(tier, candidate)) {
                    cores.add(core);
                    catalysts.add(candidate);
                }
            }
        }
    }
}
