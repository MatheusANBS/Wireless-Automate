package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterCodecs;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
    /** Filtro de entrada: o que pode entrar, por qualquer caminho. Vazio = tudo. */
    private Filter filter = Filter.EMPTY;
    /** Sobe quando o filtro muda: a tela de filtro aberta reenvia a visão. */
    private int filterVersion;
    private final FilterSet.ItemRule rule = new FilterSet.ItemRule();

    public StorageChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHEST.get(), pos, state);
        storage.setAdmission(this::admit);
    }

    public Filter filter() {
        return filter;
    }

    public int filterVersion() {
        return filterVersion;
    }

    /**
     * Troca o filtro de entrada. Salva e avisa os vizinhos: um roteador cujo destino dormia porque
     * o filtro recusava acorda e tenta de novo.
     */
    public void setFilter(Filter filter) {
        if (!filter.equals(this.filter)) {
            this.filter = filter;
            filterVersion++;
            setChanged();
        }
    }

    /**
     * Quanto do tipo o filtro deixa entrar: nada se ele recusa; com estoque na entrada, só até o
     * Baú ter N do item (com componentes iguais se a entrada exigir).
     */
    private long admit(ItemStack key, long amount) {
        FilterSet set = filter.asSet();
        if (set.isEmpty()) {
            return amount;
        }
        if (!set.evaluateItem(key, rule)) {
            return 0;
        }
        if (rule.stock > 0) {
            long present = rule.matchComponents ? storage.count(key) : storage.countItem(key);
            return StockLimit.acceptable(present, rule.stock, amount);
        }
        return amount;
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
        if (!filter.isEmpty()) {
            FilterCodecs.LENIENT.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), filter)
                    .resultOrPartial(error -> WirelessAutomate.LOGGER.error("Falha ao salvar o filtro do Baú: {}", error))
                    .ifPresent(encoded -> tag.put("filter", encoded));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storage.load(tag.getList("items", Tag.TAG_COMPOUND), registries);
        storageId = tag.hasUUID("storage_id") ? tag.getUUID("storage_id") : null;
        filter = tag.contains("filter")
                ? FilterCodecs.LENIENT.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("filter"))
                        .resultOrPartial(error -> WirelessAutomate.LOGGER.warn("Filtro do Baú inválido: {}", error))
                        .orElse(Filter.EMPTY)
                : Filter.EMPTY;
    }

    /** O drop leva a referência, o resumo e o filtro; vazio e sem filtro, não leva nada (e empilha). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!storage.isEmpty()) {
            components.set(ModDataComponents.STORAGE_CONTENTS.get(),
                    new StorageContents(storageId(), storage.types(), storage.total()));
        }
        if (!filter.isEmpty()) {
            components.set(ModDataComponents.STORAGE_FILTER.get(), filter);
        }
    }

    /**
     * Colocado a partir de um item cheio: tira o conteúdo do {@link StorageSavedData}. Se ele já
     * foi tirado (cópia do item), nasce vazio e com id novo, para não dividir o id com o original.
     */
    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        filter = input.getOrDefault(ModDataComponents.STORAGE_FILTER.get(), Filter.EMPTY);
        StorageContents contents = input.get(ModDataComponents.STORAGE_CONTENTS.get());
        if (contents == null || !(level instanceof ServerLevel server)) {
            return;
        }
        if (StorageSavedData.get(server.getServer()).take(contents.id()) instanceof ListTag items) {
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
        tag.remove("filter");
    }
}
