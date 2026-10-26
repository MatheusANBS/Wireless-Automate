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
import io.github.matheusanbs.wirelessautomate.resonator.CascadeItem;
import io.github.matheusanbs.wirelessautomate.resonator.CatalystRule;
import io.github.matheusanbs.wirelessautomate.resonator.ChargedDustItem;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorHeartItem;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.EnumMap;
import java.util.List;
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

    /** O Pó Carregado, um por tier do Allthemodium ao Ultimate (sempre registrados): o catalisador desses Núcleos. */
    public static final Map<RouterTier, DeferredItem<ChargedDustItem>> CHARGED_DUSTS = new EnumMap<>(RouterTier.class);

    static {
        for (RouterTier tier : RouterTier.values()) {
            if (CatalystRule.needsCharged(tier.ordinal())) {
                CHARGED_DUSTS.put(tier, ITEMS.registerItem("charged_dust_" + tier.getSerializedName(),
                        props -> new ChargedDustItem(tier, props), new Item.Properties()));
            }
        }
    }

    /** A cascata do Ressonador, na ordem da fabricação: cada peça entra na seguinte, até o Núcleo Condensado. */
    public static final DeferredItem<CascadeItem> SIGNAL_ALLOY_RAW = cascade("signal_alloy_raw");
    public static final DeferredItem<CascadeItem> SIGNAL_ALLOY_INGOT = cascade("signal_alloy_ingot");
    public static final DeferredItem<CascadeItem> RESONANCE_PLATE = cascade("resonance_plate");
    public static final DeferredItem<CascadeItem> RESONANCE_WIRE = cascade("resonance_wire");
    public static final DeferredItem<CascadeItem> RESONANCE_COIL = cascade("resonance_coil");
    public static final DeferredItem<CascadeItem> HARMONIC_LENS = cascade("harmonic_lens");
    public static final DeferredItem<CascadeItem> PHASE_CIRCUIT = cascade("phase_circuit");
    public static final DeferredItem<CascadeItem> PHASE_MATRIX = cascade("phase_matrix");
    public static final DeferredItem<CascadeItem> FIELD_STABILIZER = cascade("field_stabilizer");
    public static final DeferredItem<CascadeItem> CONDENSED_CORE = cascade("condensed_core");
    /** A peça do Coração Ultimate que leva os blocos de liga (ou de netherite, sem o Allthemodium) e uma Nether Star. */
    public static final DeferredItem<CascadeItem> ALLOY_FRAME = cascade("alloy_frame");

    /** A cascata na ordem da fabricação (a da aba criativa). */
    public static final List<DeferredItem<CascadeItem>> CASCADE = List.of(SIGNAL_ALLOY_RAW, SIGNAL_ALLOY_INGOT,
            RESONANCE_PLATE, RESONANCE_WIRE, RESONANCE_COIL, HARMONIC_LENS, PHASE_CIRCUIT, PHASE_MATRIX,
            FIELD_STABILIZER, CONDENSED_CORE, ALLOY_FRAME);

    private static DeferredItem<CascadeItem> cascade(String name) {
        return ITEMS.registerItem(name, CascadeItem::new, new Item.Properties());
    }

    /** O Coração do Ressonador, um por tier (sempre registrados): pedido pela receita e pelo upgrade do Núcleo. */
    public static final Map<RouterTier, DeferredItem<ResonatorHeartItem>> RESONATOR_HEARTS =
            new EnumMap<>(RouterTier.class);

    static {
        for (RouterTier tier : RouterTier.values()) {
            RESONATOR_HEARTS.put(tier, ITEMS.registerItem("resonator_heart_" + tier.getSerializedName(),
                    props -> new ResonatorHeartItem(tier, props), new Item.Properties()));
        }
    }

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
    public static final DeferredItem<BlockItem> CRYSTAL_REPAIRER =
            ITEMS.registerSimpleBlockItem(ModBlocks.CRYSTAL_REPAIRER);

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
