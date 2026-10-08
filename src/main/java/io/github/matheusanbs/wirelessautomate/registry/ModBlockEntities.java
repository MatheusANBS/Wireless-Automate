package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChemicalTankBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageTankBlockEntity;
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
    public static final Supplier<BlockEntityType<StorageTankBlockEntity>> TANK = BLOCK_ENTITY_TYPES.register("storage_tank",
            () -> BlockEntityType.Builder.of(StorageTankBlockEntity::new,
                    ModBlocks.STORAGE.get(StorageKind.TANK).get()).build(null));
    public static final Supplier<BlockEntityType<StorageBatteryBlockEntity>> BATTERY = BLOCK_ENTITY_TYPES.register("storage_battery",
            () -> BlockEntityType.Builder.of(StorageBatteryBlockEntity::new,
                    ModBlocks.STORAGE.get(StorageKind.BATTERY).get()).build(null));
    public static final Supplier<BlockEntityType<StorageChemicalTankBlockEntity>> CHEMICAL_TANK =
            BLOCK_ENTITY_TYPES.register("storage_chemical_tank", () -> BlockEntityType.Builder.of(
                    StorageChemicalTankBlockEntity::new, ModBlocks.STORAGE.get(StorageKind.CHEMICAL_TANK).get()).build(null));

    private ModBlockEntities() {
    }
}
