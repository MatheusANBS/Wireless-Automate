package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.ChunkLoaderUpgradeItem;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.item.NetworkTabletItem;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockItem;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WirelessAutomate.MODID);

    public static final DeferredItem<RouterBlockItem> ROUTER = ITEMS.register("router",
            () -> new RouterBlockItem(ModBlocks.ROUTER.get(), new Item.Properties()));

    public static final DeferredItem<StorageChestBlockItem> STORAGE_CHEST = ITEMS.register("storage_chest",
            () -> new StorageChestBlockItem(ModBlocks.STORAGE_CHEST.get(), new Item.Properties()));

    public static final DeferredItem<ConfiguratorItem> CONFIGURATOR = ITEMS.registerItem("configurator",
            ConfiguratorItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<NetworkTabletItem> NETWORK_TABLET = ITEMS.registerItem("network_tablet",
            NetworkTabletItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<LinkerItem> LINKER = ITEMS.registerItem("linker",
            LinkerItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<FilterCardItem> FILTER_CARD = ITEMS.registerItem("filter_card",
            FilterCardItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChunkLoaderUpgradeItem> CHUNK_LOADER_UPGRADE = ITEMS.registerItem(
            "chunk_loader_upgrade", ChunkLoaderUpgradeItem::new, new Item.Properties().stacksTo(1));

    /** Núcleos de Avançado a Ultimate; não há núcleo Básico (o roteador já nasce Básico). */
    public static final Map<RouterTier, DeferredItem<TierCoreItem>> TIER_CORES = new EnumMap<>(RouterTier.class);

    static {
        for (RouterTier tier : RouterTier.values()) {
            if (tier == RouterTier.BASIC) {
                continue;
            }
            TIER_CORES.put(tier, ITEMS.registerItem("tier_core_" + tier.getSerializedName(),
                    props -> new TierCoreItem(tier, props), new Item.Properties()));
        }
    }

    private ModItems() {
    }
}
