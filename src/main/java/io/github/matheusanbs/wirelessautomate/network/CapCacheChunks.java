package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Porte 1.20.1: o índice dos {@link CapCache} por dimensão e chunk do alvo, no lugar do
 * {@code invalidateCapabilities(ChunkPos)} que o NeoForge 21.1 chama no {@code ChunkEvent.Load} e no
 * {@code ChunkEvent.Unload} ({@code CapabilityHooks.invalidateCapsOnChunkLoad/Unload}). No Forge 1.20.1 a carga de
 * um chunk não avisa ninguém, e a descarga só invalida as capabilities com block entity: um cache que viu o chunk
 * descarregado (ou um handler de bloco sem block entity, como o do caldeirão) não saberia que ele voltou ou saiu.
 * Aqui o chunk carregar ou descarregar invalida os caches dele, e quem já tinha consultado é avisado (a porta do
 * roteador acorda).
 *
 * <p>Custo zero por tick: o {@link CapCache} entra no índice na primeira consulta e sai no {@code close()}; os
 * eventos só olham um mapa. Só os do {@link ServerLevel} contam (o {@code ClientChunkCache} também posta). A parada
 * do servidor limpa tudo. Tudo na thread do servidor.
 */
@Mod.EventBusSubscriber(modid = WirelessAutomate.MODID)
public final class CapCacheChunks {
    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Set<CapCache<?>>>> INDEX = new HashMap<>();

    static void add(CapCache<?> cache) {
        INDEX.computeIfAbsent(cache.level().dimension(), key -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(ChunkPos.asLong(cache.pos()), key -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(cache);
    }

    static void remove(CapCache<?> cache) {
        Long2ObjectOpenHashMap<Set<CapCache<?>>> chunks = INDEX.get(cache.level().dimension());
        if (chunks == null) {
            return;
        }
        long chunk = ChunkPos.asLong(cache.pos());
        Set<CapCache<?>> caches = chunks.get(chunk);
        if (caches != null && caches.remove(cache) && caches.isEmpty()) {
            chunks.remove(chunk);
        }
    }

    /** Quantos caches estão no índice (para os GameTests conferirem que os removidos saem). */
    public static int size() {
        int total = 0;
        for (Long2ObjectOpenHashMap<Set<CapCache<?>>> chunks : INDEX.values()) {
            for (Set<CapCache<?>> caches : chunks.values()) {
                total += caches.size();
            }
        }
        return total;
    }

    /** Quantos caches miram o chunk {@code pos} de {@code level} (para os GameTests). */
    public static int count(ServerLevel level, ChunkPos pos) {
        Long2ObjectOpenHashMap<Set<CapCache<?>>> chunks = INDEX.get(level.dimension());
        Set<CapCache<?>> caches = chunks == null ? null : chunks.get(pos.toLong());
        return caches == null ? 0 : caches.size();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            invalidate(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            invalidate(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        INDEX.clear();
    }

    private static void invalidate(ServerLevel level, ChunkPos pos) {
        if (!level.getServer().isSameThread()) {
            // O Forge posta os dois na thread do servidor; se um mod postar de outra, fica para ela.
            level.getServer().execute(() -> invalidate(level, pos));
            return;
        }
        Long2ObjectOpenHashMap<Set<CapCache<?>>> chunks = INDEX.get(level.dimension());
        Set<CapCache<?>> caches = chunks == null ? null : chunks.get(pos.toLong());
        if (caches == null || caches.isEmpty()) {
            return;
        }
        // Cópia: o aviso do cache chega ao motor e, por ele, a código que poderia fechar caches deste chunk.
        for (CapCache<?> cache : caches.toArray(new CapCache<?>[0])) {
            cache.invalidate();
        }
    }

    private CapCacheChunks() {
    }
}
