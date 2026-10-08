package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterCodecs;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Base dos block entities de armazenamento do mod (Baú, Tanque, Bateria, Tanque Químico). Não faz
 * tick: só guarda o conteúdo e, a cada mudança, chama {@link #setChanged()}, que salva o chunk e
 * avisa os vizinhos; é esse aviso que acorda os roteadores presos nele.
 *
 * <p>Nada do conteúdo vai para o cliente pelo bloco: a tela, quando aberta, recebe o que mostra.
 *
 * <p>Ao quebrar: o drop leva só a referência ({@link StorageContents}) e o filtro, e o conteúdo vai
 * para o {@link StorageSavedData} ({@link #stash}, chamado pelo bloco). Ao colocar o item, o
 * conteúdo volta para cá ({@link #applyImplicitComponents}); uma cópia do item (criativo, dupe)
 * encontra o conteúdo já tirado e nasce vazia.
 *
 * <p>Filtro de entrada: só nos que guardam vários tipos ({@link StorageKind#hasTypes}); a admissão
 * de cada subclasse o aplica a qualquer caminho de entrada.
 */
public abstract class StorageBlockEntity extends BlockEntity {
    private final StorageKind kind;
    /** Id do conteúdo quando o bloco vira item; criado na primeira vez que precisa. */
    private @Nullable UUID storageId;
    /** Filtro de entrada: o que pode entrar, por qualquer caminho. Vazio = tudo. */
    private Filter filter = Filter.EMPTY;
    /** Sobe quando o filtro muda: a tela de filtro aberta reenvia a visão. */
    private int filterVersion;

    protected StorageBlockEntity(BlockEntityType<?> type, StorageKind kind, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.kind = kind;
    }

    public StorageKind kind() {
        return kind;
    }

    public RouterTier tier() {
        return getBlockState().getValue(RouterBlock.TIER);
    }

    /** Capacidade do tier atual pela config (0 = sem limite). */
    public long capacity() {
        return Config.storageCapacity(kind, tier());
    }

    // ------------------------------------------------------------------ conteúdo (subclasses)

    public abstract boolean isEmptyContents();

    /** Total guardado, na unidade do tipo (itens, mB, FE). */
    public abstract long total();

    /** Tipos guardados (0 na Bateria). */
    public abstract int types();

    /** Sobe a cada mudança do conteúdo (a tela compara). */
    public abstract int contentsVersion();

    protected abstract Tag saveContents(HolderLookup.Provider registries);

    /** Troca o conteúdo pelo salvo, sem avisar; aceita {@code null} (vazio). */
    protected abstract void loadContents(@Nullable Tag tag, HolderLookup.Provider registries);

    /** Esvazia sem avisar. */
    protected abstract void clearContents();

    /** Chave do conteúdo no NBT do bloco ({@code items} no Baú, por compatibilidade). */
    protected abstract String contentsKey();

    /** Abre a tela do bloco para o jogador. */
    public abstract void open(ServerPlayer player);

    // ------------------------------------------------------------------ filtro de entrada

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

    /** Sinal do comparador pela ocupação do tier. */
    public int signal() {
        return StorageMath.signal(total(), capacity());
    }

    // ------------------------------------------------------------------ quebrar e colocar

    private UUID storageId() {
        if (storageId == null) {
            storageId = UUID.randomUUID();
        }
        return storageId;
    }

    /** O bloco saiu do mundo: o conteúdo vai para o {@link StorageSavedData}, pelo id do drop. */
    void stash() {
        if (isEmptyContents() || !(level instanceof ServerLevel server)) {
            return;
        }
        StorageSavedData.get(server.getServer()).put(storageId(), kind, tier(), saveContents(server.registryAccess()));
        clearContents();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(contentsKey(), saveContents(registries));
        if (storageId != null) {
            tag.putUUID("storage_id", storageId);
        }
        if (!filter.isEmpty()) {
            FilterCodecs.LENIENT.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), filter)
                    .resultOrPartial(error -> WirelessAutomate.LOGGER.error("Falha ao salvar o filtro de {}: {}", kind, error))
                    .ifPresent(encoded -> tag.put("filter", encoded));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadContents(tag.get(contentsKey()), registries);
        storageId = tag.hasUUID("storage_id") ? tag.getUUID("storage_id") : null;
        filter = tag.contains("filter")
                ? FilterCodecs.LENIENT.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("filter"))
                        .resultOrPartial(error -> WirelessAutomate.LOGGER.warn("Filtro de {} inválido: {}", kind, error))
                        .orElse(Filter.EMPTY)
                : Filter.EMPTY;
    }

    /** O drop leva a referência, o resumo e o filtro; vazio e sem filtro, não leva nada (e empilha). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!isEmptyContents()) {
            components.set(ModDataComponents.STORAGE_CONTENTS.get(), new StorageContents(storageId(), types(), total()));
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
        Tag stored = StorageSavedData.get(server.getServer()).take(contents.id(), kind);
        if (stored != null) {
            loadContents(stored, server.registryAccess());
            storageId = contents.id();
            setChanged();
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(contentsKey());
        tag.remove("storage_id");
        tag.remove("filter");
    }
}
