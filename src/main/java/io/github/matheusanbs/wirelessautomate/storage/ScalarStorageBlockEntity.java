package io.github.matheusanbs.wirelessautomate.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Base dos armazenamentos de um valor só ({@link ScalarStore}): a Bateria (energia) e o Tanque de
 * Source. Sem tipos, então sem filtro. Cada mudança real do conteúdo salva o chunk e chama
 * {@link #contentsChanged()}.
 */
public abstract class ScalarStorageBlockEntity extends StorageBlockEntity {
    private final ScalarStore store = new ScalarStore(this::onStoreChanged, this::capacity);

    protected ScalarStorageBlockEntity(BlockEntityType<?> type, StorageKind kind, BlockPos pos, BlockState state) {
        super(type, kind, pos, state);
    }

    public ScalarStore store() {
        return store;
    }

    private void onStoreChanged() {
        setChanged();
        contentsChanged();
    }

    /** Gancho: o conteúdo mudou de verdade (depois do {@code setChanged}). */
    protected void contentsChanged() {
    }

    @Override
    public boolean isEmptyContents() {
        return store.isEmpty();
    }

    @Override
    public long total() {
        return store.stored();
    }

    @Override
    public int types() {
        return 0;
    }

    @Override
    public int contentsVersion() {
        return store.version();
    }

    @Override
    protected Tag saveContents() {
        return LongTag.valueOf(store.stored());
    }

    @Override
    protected void loadContents(@Nullable Tag tag) {
        store.set(tag instanceof NumericTag number ? number.getAsLong() : 0);
    }

    @Override
    protected void clearContents() {
        store.set(0);
    }
}
