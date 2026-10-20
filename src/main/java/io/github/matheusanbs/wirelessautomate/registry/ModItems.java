package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.ChunkLoaderUpgradeItem;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.item.NetworkTabletItem;
import io.github.matheusanbs.wirelessautomate.item.RangeUpgradeItem;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WirelessAutomate.MODID);

    public static final DeferredItem<RouterBlockItem> ROUTER = ITEMS.register("router",
            () -> new RouterBlockItem(ModBlocks.ROUTER.get(), new Item.Properties()));

    /** Itens dos armazenamentos do mod, um por tipo. */
    public static final Map<StorageKind, DeferredItem<StorageBlockItem>> STORAGE = new EnumMap<>(StorageKind.class);

    static {
        for (StorageKind kind : StorageKind.values()) {
            STORAGE.put(kind, ITEMS.register(kind.id,
                    () -> new StorageBlockItem(ModBlocks.STORAGE.get(kind).get(), new Item.Properties())));
        }
    }

    public static final DeferredItem<StorageBlockItem> STORAGE_CHEST = STORAGE.get(StorageKind.CHEST);

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

    public static final DeferredItem<RangeUpgradeItem> RANGE_UPGRADE = ITEMS.registerItem(
            "range_upgrade", RangeUpgradeItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<BlockItem> SIGNAL_QUARTZ_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.SIGNAL_QUARTZ_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_SIGNAL_QUARTZ_ORE =
            ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_SIGNAL_QUARTZ_ORE);

    /** Cristais do Ressonador de Sinal: o de Sinal (rende 1x) e o Lapidado (rende 2x e dura o dobro). */
    public static final DeferredItem<SignalCrystalItem> SIGNAL_CRYSTAL = ITEMS.registerItem("signal_crystal",
            props -> new SignalCrystalItem(false, props), new Item.Properties());
    public static final DeferredItem<SignalCrystalItem> CUT_SIGNAL_CRYSTAL = ITEMS.registerItem("cut_signal_crystal",
            props -> new SignalCrystalItem(true, props), new Item.Properties());

    /**
     * Cristais Afinados, um por tier do Elite ao Ultimate (os do Allthemodium sempre registrados): Lapidados com o
     * tier gravado, que rendem cheio no Núcleo do tier ou abaixo.
     */
    public static final Map<RouterTier, DeferredItem<SignalCrystalItem>> TUNED_CRYSTALS = new EnumMap<>(RouterTier.class);

    static {
        for (RouterTier tier : RouterTier.values()) {
            if (tier.ordinal() < RouterTier.ELITE.ordinal()) {
                continue;
            }
            TUNED_CRYSTALS.put(tier, ITEMS.registerItem("tuned_signal_crystal_" + tier.getSerializedName(),
                    props -> new SignalCrystalItem(true, tier, props), new Item.Properties()));
        }
    }

    /** O catalisador da recarga do Núcleo do Elite para cima. */
    public static final DeferredItem<Item> RESONANCE_DUST = ITEMS.registerSimpleItem("resonance_dust");

    /** Os blocos do Ressonador; o Núcleo leva o tier no componente do estado. */
    public static final DeferredItem<BlockItem> RESONATOR_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.RESONATOR_CASING);
    public static final DeferredItem<BlockItem> RESONATOR_GLASS = ITEMS.registerSimpleBlockItem(ModBlocks.RESONATOR_GLASS);
    public static final DeferredItem<ResonanceCoreItem> RESONANCE_CORE = ITEMS.register("resonance_core",
            () -> new ResonanceCoreItem(ModBlocks.RESONANCE_CORE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> RESONATOR_ENERGY_PORT =
            ITEMS.registerSimpleBlockItem(ModBlocks.RESONATOR_ENERGY_PORT);
    public static final DeferredItem<BlockItem> RESONATOR_CRYSTAL_PORT =
            ITEMS.registerSimpleBlockItem(ModBlocks.RESONATOR_CRYSTAL_PORT);
    public static final DeferredItem<BlockItem> RESONATOR_TUNER = ITEMS.registerSimpleBlockItem(ModBlocks.RESONATOR_TUNER);

    /** Cartões de Upgrade de Avançado a Ultimate (os do Allthemodium sempre registrados); não há Básico (o roteador já nasce Básico). */
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
