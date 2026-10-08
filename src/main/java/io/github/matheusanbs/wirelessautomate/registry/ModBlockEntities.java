package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WirelessAutomate.MODID);

    public static final Supplier<BlockEntityType<RouterBlockEntity>> ROUTER = BLOCK_ENTITY_TYPES.register("router",
            () -> BlockEntityType.Builder.of(RouterBlockEntity::new, ModBlocks.ROUTER.get()).build(null));

    public static final Supplier<BlockEntityType<StorageChestBlockEntity>> CHEST = BLOCK_ENTITY_TYPES.register("storage_chest",
            () -> BlockEntityType.Builder.of(StorageChestBlockEntity::new, ModBlocks.STORAGE_CHEST.get()).build(null));

    private ModBlockEntities() {
    }
}
