package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlock;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, WirelessAutomate.MODID);

    public static final RegistryObject<RouterBlock> ROUTER = BLOCKS.register("router", () -> new RouterBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    /** Armazenamentos do mod (Baú, Tanque, Bateria, Tanque Químico, Tanque de Source), com tiers. */
    public static final Map<StorageKind, RegistryObject<StorageBlock>> STORAGE = new EnumMap<>(StorageKind.class);

    static {
        for (StorageKind kind : StorageKind.values()) {
            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    // Os modelos têm elementos e não preenchem o cubo: sem oclusão (só visual).
                    .noOcclusion();
            STORAGE.put(kind, BLOCKS.register(kind.id,
                    () -> kind == StorageKind.SOURCE_TANK ? new StorageSourceTankBlock(kind, properties) : new StorageBlock(kind, properties)));
        }
    }

    public static final RegistryObject<StorageBlock> STORAGE_CHEST = STORAGE.get(StorageKind.CHEST);

    private ModBlocks() {
    }
}
