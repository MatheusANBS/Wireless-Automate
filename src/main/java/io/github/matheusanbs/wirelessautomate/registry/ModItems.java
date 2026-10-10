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
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, WirelessAutomate.MODID);

    public static final RegistryObject<RouterBlockItem> ROUTER = ITEMS.register("router",
            () -> new RouterBlockItem(ModBlocks.ROUTER.get(), new Item.Properties()));

    /** Itens dos armazenamentos do mod, um por tipo. */
    public static final Map<StorageKind, RegistryObject<StorageBlockItem>> STORAGE = new EnumMap<>(StorageKind.class);

    static {
        for (StorageKind kind : StorageKind.values()) {
            STORAGE.put(kind, ITEMS.register(kind.id,
                    () -> new StorageBlockItem(ModBlocks.STORAGE.get(kind).get(), new Item.Properties())));
        }
    }

    public static final RegistryObject<StorageBlockItem> STORAGE_CHEST = STORAGE.get(StorageKind.CHEST);

    public static final RegistryObject<ConfiguratorItem> CONFIGURATOR = ITEMS.register("configurator",
            () -> new ConfiguratorItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<NetworkTabletItem> NETWORK_TABLET = ITEMS.register("network_tablet",
            () -> new NetworkTabletItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<LinkerItem> LINKER = ITEMS.register("linker",
            () -> new LinkerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<FilterCardItem> FILTER_CARD = ITEMS.register("filter_card",
            () -> new FilterCardItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<ChunkLoaderUpgradeItem> CHUNK_LOADER_UPGRADE = ITEMS.register(
            "chunk_loader_upgrade", () -> new ChunkLoaderUpgradeItem(new Item.Properties().stacksTo(1)));

    /** Cartões de Upgrade de Avançado a Ultimate (os do Allthemodium sempre registrados); não há Básico (o roteador já nasce Básico). */
    public static final Map<RouterTier, RegistryObject<TierCoreItem>> TIER_CORES = new EnumMap<>(RouterTier.class);

    static {
        for (RouterTier tier : RouterTier.values()) {
            if (tier == RouterTier.BASIC) {
                continue;
            }
            TIER_CORES.put(tier, ITEMS.register("tier_core_" + tier.getSerializedName(),
                    () -> new TierCoreItem(tier, new Item.Properties())));
        }
    }

    private ModItems() {
    }
}
