package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity do Baú. Não faz tick: só guarda o conteúdo ({@link ItemStorage}) e, a cada mudança,
 * chama {@link #setChanged()}, que salva o chunk e avisa os vizinhos. É esse aviso que acorda os
 * roteadores presos nele, como num baú vanilla.
 *
 * <p>Nada do conteúdo vai para o cliente pelo bloco: a tela (quando aberta) recebe o que mostra.
 *
 * <p>Ao quebrar: o drop leva só a referência ({@link StorageContents}) e o conteúdo vai para o
 * {@link StorageSavedData}, pelo bloco ({@link StorageChestBlock#onRemove}). Ao colocar o item, o conteúdo
 * volta para cá ({@link #applyImplicitComponents}).
 */
public class StorageChestBlockEntity extends BlockEntity {
    private final ItemStorage storage = new ItemStorage(this::setChanged, this::capacity);
    private final ItemStorageHandler handler = new ItemStorageHandler(storage);
    /** Id do conteúdo quando o bloco vira item; criado na primeira vez que precisa. */
    private @Nullable UUID storageId;

    public StorageChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHEST.get(), pos, state);
    }

    public ItemStorage storage() {
        return storage;
    }

    public ItemStorageHandler handler() {
        return handler;
    }

    public RouterTier tier() {
        return getBlockState().getValue(RouterBlock.TIER);
    }

    /** Capacidade do tier atual pela config (0 = sem limite). */
    public long capacity() {
        return Config.chestCapacity(tier());
    }

    private UUID storageId() {
        if (storageId == null) {
            storageId = UUID.randomUUID();
        }
        return storageId;
    }

    /** O bloco saiu do mundo: o conteúdo vai para o {@link StorageSavedData}, pelo id do drop. */
    void stash() {
        if (storage.isEmpty() || !(level instanceof ServerLevel server)) {
            return;
        }
        StorageSavedData.get(server.getServer()).put(storageId(), storage.save(server.registryAccess()));
        storage.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", storage.save(registries));
        if (storageId != null) {
            tag.putUUID("storage_id", storageId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storage.load(tag.getList("items", Tag.TAG_COMPOUND), registries);
        storageId = tag.hasUUID("storage_id") ? tag.getUUID("storage_id") : null;
    }

    /** O drop leva a referência e o resumo; vazio, o item não leva nada (e empilha). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!storage.isEmpty()) {
            components.set(ModDataComponents.STORAGE_CONTENTS.get(),
                    new StorageContents(storageId(), storage.types(), storage.total()));
        }
    }

    /**
     * Colocado a partir de um item cheio: tira o conteúdo do {@link StorageSavedData}. Se ele já
     * foi tirado (cópia do item), nasce vazio e com id novo, para não dividir o id com o original.
     */
    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        StorageContents contents = input.get(ModDataComponents.STORAGE_CONTENTS.get());
        if (contents == null || !(level instanceof ServerLevel server)) {
            return;
        }
        ListTag items = StorageSavedData.get(server.getServer()).take(contents.id());
        if (items != null) {
            storage.load(items, server.registryAccess());
            storageId = contents.id();
            setChanged();
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("items");
        tag.remove("storage_id");
    }
}
