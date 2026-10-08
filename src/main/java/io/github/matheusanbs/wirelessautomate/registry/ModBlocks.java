package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WirelessAutomate.MODID);

    public static final DeferredBlock<RouterBlock> ROUTER = BLOCKS.registerBlock("router", RouterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion());

    /** Armazenamentos do mod (Baú, Tanque, Bateria, Tanque Químico), com tiers. */
    public static final Map<StorageKind, DeferredBlock<StorageBlock>> STORAGE = new EnumMap<>(StorageKind.class);

    static {
        for (StorageKind kind : StorageKind.values()) {
            STORAGE.put(kind, BLOCKS.registerBlock(kind.id, props -> new StorageBlock(kind, props),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0F, 6.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));
        }
    }

    public static final DeferredBlock<StorageBlock> STORAGE_CHEST = STORAGE.get(StorageKind.CHEST);

    private ModBlocks() {
    }
}
