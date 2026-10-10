package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterCodecs;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
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
 * conteúdo volta para cá ({@link #applyFromItem}); uma cópia do item (criativo, dupe)
 * encontra o conteúdo já tirado e nasce vazia.
 *
 * <p>Porte 1.20.1 (sem componentes de item): o NBT do bloco leva também {@value #CONTENTS_KEY} (a
 * referência e o resumo, só com conteúdo), que a loot table copia para o item ({@code copy_nbt}, com o
 * {@code filter} indo para {@code storage_filter}); o drop sem loot table usa o {@link #writeToItem}.
 * Ao colocar, o {@link StorageBlockItem} chama o {@link #applyFromItem}. As capabilities são expostas
 * pelo {@link #getCapability} (D3), cada uma num {@link LazyOptional} guardado e invalidado no
 * {@link #invalidateCaps()}; cada tipo diz o que expõe no {@link #exposed}.
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
    /** Porte 1.20.1 (D3): as capabilities já pedidas, guardadas até o {@link #invalidateCaps()}. */
    private final Map<Capability<?>, LazyOptional<?>> capabilities = new HashMap<>();

    /** Porte 1.20.1: chave da referência ao conteúdo no NBT do bloco, que a loot table copia para o item. */
    public static final String CONTENTS_KEY = "storage_contents";

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

    protected abstract Tag saveContents();

    /** Troca o conteúdo pelo salvo, sem avisar; aceita {@code null} (vazio). */
    protected abstract void loadContents(@Nullable Tag tag);

    /** Esvazia sem avisar. */
    protected abstract void clearContents();

    /** Chave do conteúdo no NBT do bloco ({@code items} no Baú, por compatibilidade). */
    protected abstract String contentsKey();

    /** Abre a tela do bloco para o jogador. */
    public abstract void open(ServerPlayer player);

    // ------------------------------------------------------------------ capabilities (porte 1.20.1, D3)

    /**
     * O objeto que este armazenamento expõe para a capability, ou {@code null} se não a expõe (vale para
     * qualquer lado). No main isso ficava no {@code StorageCapabilities}; aqui cada tipo sobrescreve.
     */
    protected @Nullable Object exposed(Capability<?> capability) {
        return null;
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (!remove) {
            LazyOptional<?> cached = capabilities.get(capability);
            if (cached == null) {
                Object value = exposed(capability);
                if (value != null) {
                    cached = LazyOptional.of(() -> value);
                    capabilities.put(capability, cached);
                }
            }
            if (cached != null) {
                return cached.cast();
            }
        }
        return super.getCapability(capability, side);
    }

    /** O bloco saiu (ou o chunk descarregou): quem guardou a capability (o {@code CapCache} do roteador) fica sabendo. */
    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        capabilities.values().forEach(LazyOptional::invalidate);
        capabilities.clear();
    }

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

    /**
     * O bloco saiu do mundo: o conteúdo é copiado para o {@link StorageSavedData}, pelo id do drop.
     *
     * <p>Não esvazia o block entity: na quebra do sobrevivência o NeoForge tira o bloco
     * ({@code removeBlock}, que chama isto) <b>antes</b> de a loot table montar o drop
     * ({@code playerDestroy}), lendo este mesmo block entity. Esvaziado, o item cairia sem a
     * referência e o conteúdo ficaria órfão. O block entity é descartado logo depois.
     */
    void stash() {
        if (isEmptyContents() || !(level instanceof ServerLevel server)) {
            return;
        }
        StorageSavedData.get(server.getServer()).put(storageId(), kind, tier(), saveContents());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(contentsKey(), saveContents());
        if (storageId != null) {
            tag.putUUID("storage_id", storageId);
        }
        if (!filter.isEmpty()) {
            FilterCodecs.LENIENT.encodeStart(NbtOps.INSTANCE, filter)
                    .resultOrPartial(error -> WirelessAutomate.LOGGER.error("Falha ao salvar o filtro de {}: {}", kind, error))
                    .ifPresent(encoded -> tag.put("filter", encoded));
        }
        // Porte 1.20.1: a referência que a loot table (copy_nbt) leva para o item; o "filter" vai como "storage_filter".
        if (!isEmptyContents()) {
            StorageContents.CODEC.encodeStart(NbtOps.INSTANCE, contentsRef())
                    .resultOrPartial(error -> WirelessAutomate.LOGGER.error("Falha ao salvar a referência de {}: {}", kind, error))
                    .ifPresent(encoded -> tag.put(CONTENTS_KEY, encoded));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        loadContents(tag.get(contentsKey()));
        storageId = tag.hasUUID("storage_id") ? tag.getUUID("storage_id") : null;
        filter = tag.contains("filter")
                ? FilterCodecs.LENIENT.parse(NbtOps.INSTANCE, tag.get("filter"))
                        .resultOrPartial(error -> WirelessAutomate.LOGGER.warn("Filtro de {} inválido: {}", kind, error))
                        .orElse(Filter.EMPTY)
                : Filter.EMPTY;
    }

    private StorageContents contentsRef() {
        return new StorageContents(storageId(), types(), total());
    }

    /**
     * O drop leva a referência, o resumo e o filtro; vazio e sem filtro, não leva nada (e empilha).
     * Porte 1.20.1: no lugar do {@code collectImplicitComponents}, grava no NBT do item ({@link ModDataComponents}).
     */
    public void writeToItem(ItemStack stack) {
        if (!isEmptyContents()) {
            ModDataComponents.STORAGE_CONTENTS.set(stack, contentsRef());
        }
        if (!filter.isEmpty()) {
            ModDataComponents.STORAGE_FILTER.set(stack, filter);
        }
    }

    /**
     * Colocado a partir de um item cheio: tira o conteúdo do {@link StorageSavedData}. Se ele já
     * foi tirado (cópia do item), nasce vazio e com id novo, para não dividir o id com o original.
     *
     * <p>Porte 1.20.1: no lugar do {@code applyImplicitComponents}; o {@link StorageBlockItem} chama ao
     * colocar. O id é sempre refeito, porque um {@code BlockEntityTag} copiado (clique do meio no
     * criativo) traz o id do original.
     */
    public void applyFromItem(ItemStack stack) {
        filter = ModDataComponents.STORAGE_FILTER.getOrDefault(stack, Filter.EMPTY);
        storageId = null;
        StorageContents contents = ModDataComponents.STORAGE_CONTENTS.get(stack);
        if (contents != null && level instanceof ServerLevel server) {
            Tag stored = StorageSavedData.get(server.getServer()).take(contents.id(), kind);
            if (stored != null) {
                loadContents(stored);
                storageId = contents.id();
            }
        }
        setChanged();
        appliedFromItem();
    }

    /** Gancho: o {@link #applyFromItem} terminou (o Tanque de Source refaz o nível). */
    protected void appliedFromItem() {
    }
}
