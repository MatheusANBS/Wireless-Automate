package io.github.matheusanbs.wirelessautomate.chunk;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.common.world.ForgeChunkManager.TicketHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Tickets do Upgrade de chunk loading, um gerenciador por servidor. Um roteador com o upgrade força,
 * com ticking, o chunk dele e o da máquina (se for outro), em nome da posição do roteador
 * ({@link #CONTROLLER}). Os tickets são do Forge e ficam salvos no mundo; ao carregar o mundo,
 * {@link #validateTickets} tira os de roteadores que sumiram ou perderam o upgrade.
 *
 * <p>Sem tick por bloco: o roteador avisa quando o upgrade muda ({@link #update}), quando carrega
 * ({@link #loaded}, avaliado no tick seguinte, porque forçar outro chunk durante a carga de um chunk
 * carregaria chunk dentro da carga), quando descarrega ({@link #unloaded}) e quando sai do mundo
 * ({@link #removed}). O {@link #tick} só processa a fila desses avisos.
 *
 * <p>Limite por jogador ({@code chunkLoading.maxChunksPerPlayer}): o dono é quem pôs o upgrade, e
 * cada chunk conta uma vez, mesmo com vários roteadores do mesmo dono nele. Um upgrade que passaria
 * do limite fica {@link ChunkLoadState#LIMIT} sem tickets; quando o dono libera chunks, os que
 * esperavam são reavaliados. Desligado na config, todos ficam {@link ChunkLoadState#DISABLED}.
 */
public final class RouterChunkLoader {
    /**
     * Os tickets do mod, em nome da posição do roteador. No Forge 1.20.1 não há {@code TicketController}:
     * esta fachada chama o {@link ForgeChunkManager} com o id do mod, e a validação ao carregar o mundo é
     * registrada por {@link #registerValidation()}.
     */
    public static final Controller CONTROLLER = new Controller();
    private static final long[] NO_CHUNKS = new long[0];

    private static RouterChunkLoader instance;
    private static volatile boolean configChanged;
    /** Só para GameTests: limite de um dono que substitui o da config. */
    private static final Map<UUID, Integer> LIMIT_OVERRIDES = new ConcurrentHashMap<>();

    /** Roteadores carregados com upgrade (ou que acabaram de perdê-lo) e o estado de cada um. */
    private final Map<RouterBlockEntity, Entry> routers = new Reference2ObjectOpenHashMap<>();
    /** Por dono: chunks forçados e quantos roteadores ativos do dono usam cada um. */
    private final Map<UUID, Object2IntOpenHashMap<ForcedChunk>> owners = new HashMap<>();
    /** Roteadores a avaliar no próximo tick, na ordem em que chegaram. */
    private final ReferenceLinkedOpenHashSet<RouterBlockEntity> pending = new ReferenceLinkedOpenHashSet<>();
    /** A seção chunkLoading da config com que os roteadores foram avaliados, lida no primeiro tick. */
    private boolean appliedKnown;
    private boolean appliedEnabled;
    private int appliedMax;

    private record ForcedChunk(ResourceKey<Level> dimension, long chunk) {
    }

    private static final class Entry {
        final ServerLevel level;
        final BlockPos pos;
        UUID owner;
        ChunkLoadState state = ChunkLoadState.NONE;
        /** Chunks com ticket deste roteador, contados no dono; vazio se inativo. */
        long[] forced = NO_CHUNKS;

        Entry(ServerLevel level, BlockPos pos, UUID owner) {
            this.level = level;
            this.pos = pos;
            this.owner = owner;
        }
    }

    public static RouterChunkLoader get() {
        if (instance == null) {
            instance = new RouterChunkLoader();
        }
        return instance;
    }

    /** Servidor parou: esquece o estado em memória. Os tickets ficam salvos no mundo. */
    public static void reset() {
        instance = null;
    }

    /**
     * A config foi recarregada: no próximo tick, se a seção chunkLoading mudou, tudo é reavaliado.
     * Seguro de qualquer thread.
     */
    public static void configChanged() {
        configChanged = true;
    }

    /** O upgrade está ligado na config do servidor. */
    public static boolean enabled() {
        return Config.SPEC.isLoaded() ? Config.CHUNK_LOADING_ENABLED.get() : Config.CHUNK_LOADING_ENABLED.getDefault();
    }

    /** Limite da config, sem as trocas dos GameTests; 0 = sem limite. */
    private static int configuredMax() {
        return Config.SPEC.isLoaded()
                ? Config.CHUNK_LOADING_MAX_PER_PLAYER.get()
                : Config.CHUNK_LOADING_MAX_PER_PLAYER.getDefault();
    }

    /** Limite de chunks forçados do dono; 0 = sem limite. */
    public static int limit(UUID owner) {
        Integer override = LIMIT_OVERRIDES.get(owner);
        if (override != null) {
            return override;
        }
        return configuredMax();
    }

    /** Só para GameTests: troca o limite de um dono ({@code null} volta ao da config). */
    public static void overrideLimit(UUID owner, @Nullable Integer limit) {
        if (limit == null) {
            LIMIT_OVERRIDES.remove(owner);
        } else {
            LIMIT_OVERRIDES.put(owner, limit);
        }
    }

    // ------------------------------------------------------------------ avisos do roteador

    /**
     * O upgrade do roteador mudou (slot, NBT, rotação): reavalia na hora. Não chame durante a carga
     * de um chunk; para isso há {@link #loaded}.
     */
    public void update(RouterBlockEntity router) {
        pending.remove(router);
        evaluate(router);
    }

    /** O roteador carregou (ou foi relido do NBT): com upgrade, ou se tinha, é avaliado no próximo tick. */
    public void loaded(RouterBlockEntity router) {
        if (router.hasChunkUpgrade() || routers.containsKey(router)) {
            pending.add(router);
        }
    }

    /**
     * O chunk do roteador descarregou (servidor parando, ou um ticket tirado por fora): esquece o
     * roteador sem mexer nos tickets salvos.
     */
    public void unloaded(RouterBlockEntity router) {
        pending.remove(router);
        Entry entry = routers.remove(router);
        if (entry != null) {
            uncount(entry);
        }
    }

    /** O roteador saiu do mundo (quebrado, trocado): libera os tickets dele. */
    public void removed(RouterBlockEntity router) {
        pending.remove(router);
        Entry entry = routers.remove(router);
        if (entry != null) {
            release(entry);
            wakeOwner(entry.owner);
        }
        // Tickets de outra sessão de um roteador que saiu antes de ser avaliado.
        if (router.getLevel() instanceof ServerLevel level) {
            unforce(level, router.getBlockPos(), chunksOf(router));
        }
    }

    /** Estado para a tela. Só no servidor. */
    public ChunkLoadState state(RouterBlockEntity router) {
        if (!router.hasChunkUpgrade()) {
            return ChunkLoadState.NONE;
        }
        Entry entry = routers.get(router);
        if (entry == null || entry.state == ChunkLoadState.NONE) {
            return enabled() ? ChunkLoadState.ACTIVE : ChunkLoadState.DISABLED;
        }
        return entry.state;
    }

    /** Chunks forçados pelos roteadores ativos do dono (cada chunk uma vez). Para os testes e a tela. */
    public int forcedChunks(UUID owner) {
        Object2IntOpenHashMap<ForcedChunk> chunks = owners.get(owner);
        return chunks == null ? 0 : chunks.size();
    }

    /** Processa a fila de avisos. Barato quando vazia. */
    public void tick() {
        if (!appliedKnown) {
            appliedKnown = true;
            appliedEnabled = enabled();
            appliedMax = configuredMax();
        }
        if (configChanged) {
            configChanged = false;
            boolean nowEnabled = enabled();
            int nowMax = configuredMax();
            // Só a seção chunkLoading importa: recarregar outra chave (vazão, orçamento) não mexe nos tickets.
            if (nowEnabled != appliedEnabled || nowMax != appliedMax) {
                appliedEnabled = nowEnabled;
                appliedMax = nowMax;
                // Limite ou chave mudou: solta tudo e reavalia (no mesmo tick, sem descarregar) numa
                // ordem que não depende do hash: dimensão e posição. Com limite, quem entra primeiro
                // fica com os chunks, então a ordem decide quem fica de fora.
                List<RouterBlockEntity> all = new ArrayList<>(routers.keySet());
                all.sort(Comparator.comparing((RouterBlockEntity r) -> routers.get(r).level.dimension().location())
                        .thenComparingLong(r -> routers.get(r).pos.asLong()));
                for (RouterBlockEntity router : all) {
                    release(routers.get(router));
                    pending.add(router);
                }
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        List<RouterBlockEntity> batch = new ArrayList<>(pending);
        pending.clear();
        for (RouterBlockEntity router : batch) {
            evaluate(router);
        }
    }

    // ------------------------------------------------------------------ avaliação

    private void evaluate(RouterBlockEntity router) {
        if (!(router.getLevel() instanceof ServerLevel level) || router.isRemoved()) {
            return;
        }
        BlockPos pos = router.getBlockPos();
        long[] wanted = chunksOf(router);
        Entry entry = routers.get(router);
        if (!router.hasChunkUpgrade()) {
            if (entry != null) {
                routers.remove(router);
                release(entry);
                wakeOwner(entry.owner);
                if (entry.state != ChunkLoadState.NONE) {
                    router.chunkLoadStateChanged();
                }
            }
            unforce(level, pos, wanted);
            return;
        }
        UUID owner = ownerOf(router);
        if (entry == null) {
            entry = new Entry(level, pos.immutable(), owner);
            routers.put(router, entry);
        } else if (entry.state == ChunkLoadState.ACTIVE && entry.owner.equals(owner)
                && Arrays.equals(entry.forced, wanted) && enabled()) {
            return;
        } else {
            UUID previous = entry.owner;
            boolean freed = release(entry);
            entry.owner = owner;
            if (freed) {
                wakeOwner(previous);
            }
        }

        ChunkLoadState next;
        if (!enabled()) {
            unforce(level, pos, wanted);
            next = ChunkLoadState.DISABLED;
        } else if (!fits(owner, level, wanted)) {
            unforce(level, pos, wanted);
            next = ChunkLoadState.LIMIT;
        } else {
            force(entry, wanted);
            next = ChunkLoadState.ACTIVE;
        }
        if (entry.state != next) {
            entry.state = next;
            router.chunkLoadStateChanged();
        }
    }

    /** Os chunks novos de {@code wanted} cabem no limite do dono. */
    private boolean fits(UUID owner, ServerLevel level, long[] wanted) {
        int limit = limit(owner);
        if (limit <= 0) {
            return true;
        }
        Object2IntOpenHashMap<ForcedChunk> chunks = owners.get(owner);
        int used = chunks == null ? 0 : chunks.size();
        for (long chunk : wanted) {
            if (chunks == null || !chunks.containsKey(new ForcedChunk(level.dimension(), chunk))) {
                used++;
            }
        }
        return used <= limit;
    }

    private void force(Entry entry, long[] wanted) {
        Object2IntOpenHashMap<ForcedChunk> chunks = owners.computeIfAbsent(entry.owner, o -> new Object2IntOpenHashMap<>());
        for (long chunk : wanted) {
            CONTROLLER.forceChunk(entry.level, entry.pos, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true, true);
            chunks.addTo(new ForcedChunk(entry.level.dimension(), chunk), 1);
        }
        entry.forced = wanted;
    }

    /** Tira os tickets do roteador e a contagem no dono. Devolve se havia algum. */
    private boolean release(Entry entry) {
        if (entry.forced.length == 0) {
            return false;
        }
        unforce(entry.level, entry.pos, entry.forced);
        uncount(entry);
        return true;
    }

    /** Tira só a contagem no dono (os tickets ficam). */
    private void uncount(Entry entry) {
        Object2IntOpenHashMap<ForcedChunk> chunks = owners.get(entry.owner);
        if (chunks != null) {
            for (long chunk : entry.forced) {
                ForcedChunk key = new ForcedChunk(entry.level.dimension(), chunk);
                if (chunks.addTo(key, -1) <= 1) {
                    chunks.removeInt(key);
                }
            }
            if (chunks.isEmpty()) {
                owners.remove(entry.owner);
            }
        }
        entry.forced = NO_CHUNKS;
    }

    /** O dono liberou chunks: os roteadores dele que esperavam pelo limite são reavaliados no próximo tick. */
    private void wakeOwner(UUID owner) {
        for (Map.Entry<RouterBlockEntity, Entry> e : routers.entrySet()) {
            if (e.getValue().state == ChunkLoadState.LIMIT && e.getValue().owner.equals(owner)) {
                pending.add(e.getKey());
            }
        }
    }

    private static UUID ownerOf(RouterBlockEntity router) {
        UUID owner = router.upgradeOwner();
        return owner != null ? owner : Util.NIL_UUID;
    }

    /** O chunk do roteador e, se for outro, o da máquina. */
    private static long[] chunksOf(RouterBlockEntity router) {
        long own = ChunkPos.asLong(router.getBlockPos());
        long machine = ChunkPos.asLong(router.machinePos());
        return own == machine ? new long[] {own} : new long[] {own, machine};
    }

    private static void unforce(ServerLevel level, BlockPos pos, long[] chunks) {
        for (long chunk : chunks) {
            CONTROLLER.forceChunk(level, pos, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, true);
        }
    }

    /**
     * O roteador em {@code pos} tem ticket deste controle no chunk. Para os GameTests: o Forge
     * não expõe a consulta, então tira o ticket (a remoção diz se existia) e o devolve.
     */
    public static boolean hasTicket(ServerLevel level, BlockPos pos, long chunk) {
        int x = ChunkPos.getX(chunk);
        int z = ChunkPos.getZ(chunk);
        if (CONTROLLER.forceChunk(level, pos, x, z, false, true)) {
            CONTROLLER.forceChunk(level, pos, x, z, true, true);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ carga do mundo

    /** Registra a validação dos tickets salvos ({@link #validateTickets}), no setup comum do mod. */
    public static void registerValidation() {
        ForgeChunkManager.setForcedChunkLoadingCallback(WirelessAutomate.MODID, RouterChunkLoader::validateTickets);
    }

    /** Fachada do {@code TicketController} do NeoForge sobre o {@link ForgeChunkManager}. */
    public static final class Controller {
        private Controller() {
        }

        public boolean forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
            return ForgeChunkManager.forceChunk(level, WirelessAutomate.MODID, owner, chunkX, chunkZ, add, ticking);
        }
    }

    /**
     * Ao carregar o mundo, antes de reativar os tickets salvos: tira os de roteadores que não
     * existem mais ou não têm mais o upgrade, os que sobraram de antes de girar o roteador e todos,
     * se o upgrade está desligado. Ler o block entity carrega o chunk dele, que o ticket carregaria
     * de qualquer jeito. O limite por jogador é conferido quando cada roteador carrega.
     */
    private static void validateTickets(ServerLevel level, TicketHelper helper) {
        boolean enabled = enabled();
        helper.getBlockTickets().forEach((pos, tickets) -> {
            if (!enabled) {
                helper.removeAllTickets(pos);
                return;
            }
            BlockEntity blockEntity;
            try {
                blockEntity = level.getBlockEntity(pos);
            } catch (RuntimeException e) {
                WirelessAutomate.LOGGER.warn("Não deu para conferir o roteador com chunk loading em {}", pos, e);
                return;
            }
            if (!(blockEntity instanceof RouterBlockEntity router) || !router.hasChunkUpgrade()) {
                helper.removeAllTickets(pos);
                return;
            }
            long[] wanted = chunksOf(router);
            // No Forge, o primeiro do par são os tickets sem ticking e o segundo, os com ticking.
            removeOthers(helper, pos, tickets.getSecond(), wanted, true);
            removeOthers(helper, pos, tickets.getFirst(), NO_CHUNKS, false);
        });
    }

    private static void removeOthers(TicketHelper helper, BlockPos pos, LongSet chunks, long[] wanted, boolean ticking) {
        for (long chunk : chunks.toLongArray()) {
            boolean keep = false;
            for (long w : wanted) {
                keep |= w == chunk;
            }
            if (!keep) {
                helper.removeTicket(pos, chunk, ticking);
            }
        }
    }

    private RouterChunkLoader() {
    }
}
