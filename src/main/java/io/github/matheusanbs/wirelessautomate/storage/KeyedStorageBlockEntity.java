package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Armazenamento por tipo (Baú, Tanque, Tanque Químico): o conteúdo é um {@link KeyedStorage} e a
 * tela é a lista com busca ({@link StorageListMenu}). A admissão de cada subclasse aplica o filtro
 * de entrada.
 */
public abstract class KeyedStorageBlockEntity<K> extends StorageBlockEntity {
    protected KeyedStorageBlockEntity(BlockEntityType<?> type, StorageKind kind, BlockPos pos, BlockState state) {
        super(type, kind, pos, state);
    }

    /** O conteúdo. */
    public abstract KeyedStorage<K> keyed();

    @Override
    public boolean isEmptyContents() {
        return keyed().isEmpty();
    }

    @Override
    public long total() {
        return keyed().total();
    }

    @Override
    public int types() {
        return keyed().types();
    }

    @Override
    public int contentsVersion() {
        return keyed().version();
    }

    @Override
    protected Tag saveContents(HolderLookup.Provider registries) {
        return keyed().save(registries);
    }

    @Override
    protected void loadContents(@Nullable Tag tag, HolderLookup.Provider registries) {
        keyed().load(tag instanceof ListTag list ? list : new ListTag(), registries);
    }

    @Override
    protected void clearContents() {
        keyed().clear();
    }

    @Override
    public void open(ServerPlayer player) {
        StorageListMenu.open(player, this);
    }
}
