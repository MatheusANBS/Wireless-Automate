package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.packet.TvArtsPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvArtRef;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvLoaded;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * As pixel arts das Telas no cliente, pelo resumo do conteúdo ({@link TvArtRef#hash}): o update tag da Tela traz só as
 * referências, os pixels chegam pelo {@link TvArtsPayload}, e uma arte igual em várias Telas é uma só. Quando o
 * update tag chega ({@link #updated}), as artes que o cliente já tem entram no layout da Tela na hora; as que faltam
 * entram quando chegam ({@link #receive}), e até lá a Imagem e o mosaico não desenham (o resto da Tela desenha). A
 * Tela fica suja nos dois casos. O salvar do editor também guarda aqui as artes que mandou ({@link #offer}), para o
 * update tag da resposta já achar as dele.
 * <p>
 * A varredura, a cada {@link #SWEEP_TICKS} ticks com alguma arte, solta as que nenhuma Tela carregada apontou em
 * duas varreduras seguidas: a folga cobre a arte que chega antes do chunk da Tela e a Tela que sai e volta logo. Sem
 * arte, o tick é a checagem do mapa vazio. Só na thread do cliente.
 */
public final class TvArtCache {
    private static final int SWEEP_TICKS = 100;
    private static final Long2ObjectOpenHashMap<Entry> ARTS = new Long2ObjectOpenHashMap<>();
    /** As Telas com referência a uma arte que ainda não chegou. */
    private static final Set<TvBlockEntity> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int generation;
    private static int ticks;
    /** Quantos {@link TvArtsPayload} chegaram (o e2e confere que a arte veio pelo pacote). */
    public static int receivedForTests;

    private TvArtCache() {
    }

    /** Uma arte guardada e a última varredura que a viu em uso. */
    private static final class Entry {
        final TvPixelArt art;
        int generation;

        Entry(TvPixelArt art) {
            this.art = art;
            this.generation = TvArtCache.generation;
        }
    }

    /** O update tag de uma Tela chegou: as artes que já estão aqui entram no layout, e a Tela fica suja. */
    public static void updated(TvBlockEntity tv) {
        resolve(tv);
        TvCanvases.markDirty(tv.getBlockPos().asLong());
    }

    /** Os pixels chegaram: guarda as artes e completa as Telas que esperavam por elas. */
    public static void receive(TvArtsPayload payload) {
        receivedForTests++;
        offer(payload.arts());
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<TvBlockEntity> it = PENDING.iterator();
        while (it.hasNext()) {
            TvBlockEntity tv = it.next();
            if (tv.isRemoved() || resolve(tv)) {
                it.remove();
            }
        }
    }

    /** Guarda as artes (as do pacote, ou as que o editor acabou de salvar); a que já está fica. */
    public static void offer(List<TvPixelArt> arts) {
        for (int i = 0; i < arts.size(); i++) {
            TvPixelArt art = arts.get(i);
            long hash = art.contentHash();
            Entry entry = ARTS.get(hash);
            if (entry == null) {
                ARTS.put(hash, new Entry(art));
            } else {
                entry.generation = generation;
            }
        }
    }

    /**
     * Põe no layout da Tela as artes das referências que estão aqui (a mesma instância quando o id e o nome batem,
     * para a textura ser uma só) e anota a Tela como pendente se falta alguma; verdadeiro se não falta nenhuma. O
     * layout só muda quando entra arte nova: entre dois update tags as artes só se somam.
     */
    private static boolean resolve(TvBlockEntity tv) {
        List<TvArtRef> refs = tv.artRefs();
        if (refs.isEmpty() || tv.layout().images().size() == refs.size()) {
            PENDING.remove(tv);
            return true;
        }
        List<TvPixelArt> found = new ArrayList<>(refs.size());
        for (int i = 0; i < refs.size(); i++) {
            TvPixelArt art = find(refs.get(i));
            if (art != null) {
                found.add(art);
            }
        }
        boolean all = found.size() == refs.size();
        if (all) {
            PENDING.remove(tv);
        } else {
            PENDING.add(tv);
        }
        if (found.size() != tv.layout().images().size()) {
            tv.resolveArts(found);
            TvCanvases.markDirty(tv.getBlockPos().asLong());
        }
        return all;
    }

    /**
     * A arte da referência, se ela está aqui: a mesma instância quando o id e o nome batem, senão uma com o id e o
     * nome da referência; {@code null} se ainda não chegou.
     */
    public static @Nullable TvPixelArt find(TvArtRef ref) {
        Entry entry = ARTS.get(ref.hash());
        if (entry == null || entry.art.width() != ref.width() || entry.art.height() != ref.height()) {
            return null;
        }
        TvPixelArt art = entry.art;
        return ref.matches(art) ? art
                : new TvPixelArt(ref.id(), ref.name(), art.width(), art.height(), art.palette(), art.pixels());
    }

    /**
     * A arte guardada com o id, ou {@code null}: o editor completa as artes das peças coladas de outra Tela. Percorre
     * o cache, só no clique.
     */
    public static @Nullable TvPixelArt byId(UUID id) {
        for (Entry entry : ARTS.values()) {
            if (entry.art.id().equals(id)) {
                return entry.art;
            }
        }
        return null;
    }

    /**
     * A cada tick do cliente: a varredura a cada {@link #SWEEP_TICKS}, só com alguma arte ou Tela esperando uma (a
     * Tela removida sai das pendentes mesmo com o cache vazio).
     */
    static void onClientTick() {
        if (ARTS.isEmpty() && PENDING.isEmpty() || ++ticks < SWEEP_TICKS) {
            return;
        }
        ticks = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        generation++;
        for (TvBlockEntity tv : TvLoaded.of(minecraft.level)) {
            List<TvArtRef> refs = tv.artRefs();
            for (int i = 0; i < refs.size(); i++) {
                Entry entry = ARTS.get(refs.get(i).hash());
                if (entry != null) {
                    entry.generation = generation;
                }
            }
        }
        ObjectIterator<Entry> it = ARTS.values().iterator();
        while (it.hasNext()) {
            if (generation - it.next().generation >= 2) {
                it.remove();
            }
        }
        PENDING.removeIf(TvBlockEntity::isRemoved);
    }

    /** Ao trocar de dimensão e ao sair do mundo. */
    public static void clear() {
        ARTS.clear();
        ARTS.trim();
        PENDING.clear();
        ticks = 0;
    }

    /** Quantas artes estão guardadas. */
    public static int sizeForTests() {
        return ARTS.size();
    }
}
