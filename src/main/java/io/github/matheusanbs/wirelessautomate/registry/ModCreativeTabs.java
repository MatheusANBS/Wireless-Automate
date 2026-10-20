package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.GuideBook;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WirelessAutomate.MODID);

    public static final Supplier<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wirelessautomate"))
                    .icon(() -> ModItems.ROUTER.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        // Os tiers do Allthemodium só aparecem com ele (o JEI lista o que está aqui).
                        for (RouterTier tier : RouterTier.values()) {
                            if (tier.loaded()) {
                                output.accept(RouterBlockItem.withTier(ModItems.ROUTER.get(), tier));
                            }
                        }
                        // O cartão de um degrau, com origem (o que a bancada fabrica); o antigo, sem origem, não
                        // se fabrica mais.
                        for (RouterTier tier : RouterTier.values()) {
                            RouterTier previous = tier.previous();
                            if (tier.loaded() && ModItems.TIER_CORES.containsKey(tier) && previous != null) {
                                output.accept(TierCoreItem.card(tier, previous));
                            }
                        }
                        for (StorageKind kind : StorageKind.values()) {
                            // Os armazenamentos de outros mods (Químico, Source) só aparecem com eles.
                            if (!kind.loaded()) {
                                continue;
                            }
                            for (RouterTier tier : RouterTier.values()) {
                                if (tier.loaded()) {
                                    output.accept(StorageBlockItem.withTier(ModItems.STORAGE.get(kind).get(), tier));
                                }
                            }
                        }
                        output.accept(ModItems.CONFIGURATOR.get());
                        output.accept(ModItems.NETWORK_TABLET.get());
                        output.accept(ModItems.LINKER.get());
                        output.accept(ModItems.FILTER_CARD.get());
                        output.accept(ModItems.CHUNK_LOADER_UPGRADE.get());
                        output.accept(ModItems.RANGE_UPGRADE.get());
                        output.accept(ModItems.SIGNAL_QUARTZ_ORE.get());
                        output.accept(ModItems.DEEPSLATE_SIGNAL_QUARTZ_ORE.get());
                        output.accept(ModItems.SIGNAL_CRYSTAL.get());
                        output.accept(ModItems.CUT_SIGNAL_CRYSTAL.get());
                        for (var tuned : ModItems.TUNED_CRYSTALS.entrySet()) {
                            if (tuned.getKey().loaded()) {
                                output.accept(tuned.getValue().get());
                            }
                        }
                        output.accept(ModItems.RESONANCE_DUST.get());
                        output.accept(ModItems.RESONATOR_CASING.get());
                        output.accept(ModItems.RESONATOR_GLASS.get());
                        for (RouterTier tier : RouterTier.values()) {
                            if (tier.loaded()) {
                                output.accept(ResonanceCoreItem.withTier(ModItems.RESONANCE_CORE.get(), tier));
                            }
                        }
                        output.accept(ModItems.RESONATOR_ENERGY_PORT.get());
                        output.accept(ModItems.RESONATOR_CRYSTAL_PORT.get());
                        output.accept(ModItems.RESONATOR_TUNER.get());
                        GuideBook.create().ifPresent(output::accept);
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
