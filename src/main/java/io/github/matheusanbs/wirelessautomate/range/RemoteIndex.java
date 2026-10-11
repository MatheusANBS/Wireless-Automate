package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * As máquinas remotas vinculadas, por nível, para acordar o roteador como a máquina presa acorda
 * (decisão 8 da spec do alcance). Um por servidor, como o {@link NetworkManager}, descartado na parada.
 *
 * <ul>
 *   <li>posição da máquina → os vínculos (roteador e {@link RemoteMachine}): uma máquina pode estar
 *       em vários roteadores. O mixin em {@code Level.blockEntityChanged} consulta este mapa a cada
 *       {@code setChanged} de qualquer block entity, então ele só tem as remotas que viram porta de fato:
 *       roteador no motor, upgrade valendo e máquina ativa ({@link RouterBlockEntity#machineReach()}:
 *       dentro do raio e do limite). Uma de fora não acorda nada nem tem a frente relida;
 *   <li>chunk → posições, para acordar as máquinas de um chunk que acabou de carregar.
 * </ul>
 *
 * <p>É mantido quando um nó entra ou sai do motor, quando uma máquina é vinculada ou desvinculada,
 * quando o upgrade entra ou sai, numa carga de NBT, numa troca de tier e numa recarga da config
 * ({@link #update}, que refaz o registro do roteador). Só a thread do servidor mexe nele.
 */
public final class RemoteIndex {
    private static @Nullable RemoteIndex instance;

    /** Índice de cada nível com alguma remota; um nível sem remotas sai do mapa. */
    private final Reference2ObjectOpenHashMap<Level, LevelIndex> levels = new Reference2ObjectOpenHashMap<>();
    /** O que cada roteador registrou, para tirar sem varrer os níveis. */
    private final Reference2ObjectOpenHashMap<RouterBlockEntity, Registration> registered = new Reference2ObjectOpenHashMap<>();

    public static RemoteIndex get() {
        if (instance == null) {
            instance = new RemoteIndex();
        }
        return instance;
    }

    /** Descarta o estado ao parar o servidor. */
    public static void reset() {
        instance = null;
    }

    /**
     * Caminho quente, chamado pelo mixin a cada {@code setChanged} de um block entity no servidor: uma
     * consulta num mapa por nível e outra por posição, sem alocar. Só para uma máquina vinculada lê o
     * estado do bloco (para reler a frente) e avisa os roteadores.
     */
    public static void blockEntityChanged(Level level, BlockPos pos) {
        RemoteIndex index = instance;
        // Sem nenhuma remota no servidor (o caso comum), nem calcula o hash do nível.
        if (index == null || index.levels.isEmpty()) {
            return;
        }
        // Antes de qualquer get: os mapas não são thread-safe, e um setChanged de outra thread (raro, de
        // algum mod) os leria no meio de um rehash. Esse aviso não mexe no motor: a porta acorda pelo
        // teto do sono.
        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread()) {
            return;
        }
        LevelIndex byLevel = index.levels.get(level);
        if (byLevel == null) {
            return;
        }
        List<Link> links = byLevel.byPos.get(pos.asLong());
        if (links == null) {
            return;
        }
        // O chunk está carregado (a máquina acabou de mudar), mas o getChunkNow garante que não carrega.
        LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
        BlockState state = chunk == null ? null : chunk.getBlockState(pos);
        for (int i = 0, n = links.size(); i < n; i++) {
            Link link = links.get(i);
            link.router.remoteMachineChanged(link.machine, state);
        }
    }

    /**
     * O chunk {@code chunk} de {@code level} carregou: acorda as portas das máquinas vinculadas nele e
     * relê a frente delas. Cobre a remota que estava descarregada na primeira consulta (sem cache nem
     * listener de capability): a próxima visita cria o cache. Chamado pelo {@code ChunkEvent.Load}.
     */
    public void chunkLoaded(Level level, ChunkAccess chunk) {
        LevelIndex byLevel = levels.get(level);
        if (byLevel == null) {
            return;
        }
        LongArrayList positions = byLevel.byChunk.get(chunk.getPos().toLong());
        if (positions == null) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int p = 0, m = positions.size(); p < m; p++) {
            long key = positions.getLong(p);
            List<Link> links = byLevel.byPos.get(key);
            if (links == null) {
                continue;
            }
            BlockState state = chunk.getBlockState(pos.set(key));
            for (int i = 0, n = links.size(); i < n; i++) {
                Link link = links.get(i);
                link.router.remoteMachineChanged(link.machine, state);
            }
        }
    }

    /**
     * Refaz o registro de {@code router}: tira o anterior e, se ele está no motor e o upgrade de
     * alcance vale, registra as máquinas ativas (dentro do raio e do limite). Quem muda essa regra
     * chama de novo: vincular, desvincular, o upgrade, a carga do NBT, o tier e a recarga da config.
     */
    public void update(RouterBlockEntity router) {
        remove(router);
        Level level = router.getLevel();
        if (!(level instanceof ServerLevel) || router.isRemoved() || !router.rangeActive()
                || !NetworkManager.get().contains(router)) {
            return;
        }
        List<RemoteMachine> machines = router.remoteMachines().machines();
        RangeRules.Reach[] reach = router.machineReach();
        int active = 0;
        for (RangeRules.Reach r : reach) {
            if (r == RangeRules.Reach.ACTIVE) {
                active++;
            }
        }
        if (active == 0) {
            return;
        }
        LevelIndex byLevel = levels.get(level);
        if (byLevel == null) {
            byLevel = new LevelIndex();
            levels.put(level, byLevel);
        }
        long[] positions = new long[active];
        int next = 0;
        for (int i = 0; i < reach.length; i++) {
            if (reach[i] != RangeRules.Reach.ACTIVE) {
                continue;
            }
            RemoteMachine machine = machines.get(i);
            long key = machine.pos().asLong();
            positions[next++] = key;
            byLevel.add(key, new Link(router, machine));
        }
        registered.put(router, new Registration(level, positions));
    }

    /** Tira tudo o que {@code router} registrou (saiu do motor, ou antes de registrar de novo). */
    public void remove(RouterBlockEntity router) {
        Registration registration = registered.remove(router);
        if (registration == null) {
            return;
        }
        LevelIndex byLevel = levels.get(registration.level);
        if (byLevel == null) {
            return;
        }
        for (long key : registration.positions) {
            byLevel.remove(key, router);
        }
        if (byLevel.byPos.isEmpty()) {
            levels.remove(registration.level);
        }
    }

    /** Para GameTests e diagnóstico: {@code router} está registrado para a máquina em {@code pos}. */
    public boolean isIndexed(RouterBlockEntity router, BlockPos pos) {
        Level level = router.getLevel();
        LevelIndex byLevel = level == null ? null : levels.get(level);
        List<Link> links = byLevel == null ? null : byLevel.byPos.get(pos.asLong());
        if (links == null) {
            return false;
        }
        for (Link link : links) {
            if (link.router == router) {
                return true;
            }
        }
        return false;
    }

    private record Link(RouterBlockEntity router, RemoteMachine machine) {
    }

    private record Registration(Level level, long[] positions) {
    }

    private static final class LevelIndex {
        final Long2ObjectOpenHashMap<List<Link>> byPos = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<LongArrayList> byChunk = new Long2ObjectOpenHashMap<>();

        void add(long key, Link link) {
            List<Link> links = byPos.get(key);
            if (links == null) {
                links = new ArrayList<>(1);
                byPos.put(key, links);
                long chunk = chunkOf(key);
                LongArrayList positions = byChunk.get(chunk);
                if (positions == null) {
                    positions = new LongArrayList();
                    byChunk.put(chunk, positions);
                }
                positions.add(key);
            }
            links.add(link);
        }

        void remove(long key, RouterBlockEntity router) {
            List<Link> links = byPos.get(key);
            if (links == null) {
                return;
            }
            links.removeIf(link -> link.router == router);
            if (!links.isEmpty()) {
                return;
            }
            byPos.remove(key);
            long chunk = chunkOf(key);
            LongArrayList positions = byChunk.get(chunk);
            if (positions != null) {
                positions.rem(key);
                if (positions.isEmpty()) {
                    byChunk.remove(chunk);
                }
            }
        }

        private static long chunkOf(long key) {
            return ChunkPos.asLong(SectionPos.blockToSectionCoord(BlockPos.getX(key)),
                    SectionPos.blockToSectionCoord(BlockPos.getZ(key)));
        }
    }
}
