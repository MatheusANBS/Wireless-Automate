package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.GuideBook;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
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
                        for (RouterTier tier : RouterTier.values()) {
                            output.accept(RouterBlockItem.withTier(ModItems.ROUTER.get(), tier));
                        }
                        for (RouterTier tier : RouterTier.values()) {
                            if (ModItems.TIER_CORES.containsKey(tier)) {
                                output.accept(ModItems.TIER_CORES.get(tier).get());
                            }
                        }
                        for (StorageKind kind : StorageKind.values()) {
                            // O Tanque Químico só aparece com o Mekanism (sem ele não troca nada).
                            if (kind == StorageKind.CHEMICAL_TANK && !Chemicals.LOADED) {
                                continue;
                            }
                            for (RouterTier tier : RouterTier.values()) {
                                output.accept(StorageBlockItem.withTier(ModItems.STORAGE.get(kind).get(), tier));
                            }
                        }
                        output.accept(ModItems.CONFIGURATOR.get());
                        output.accept(ModItems.NETWORK_TABLET.get());
                        output.accept(ModItems.LINKER.get());
                        output.accept(ModItems.FILTER_CARD.get());
                        output.accept(ModItems.CHUNK_LOADER_UPGRADE.get());
                        GuideBook.create().ifPresent(output::accept);
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
