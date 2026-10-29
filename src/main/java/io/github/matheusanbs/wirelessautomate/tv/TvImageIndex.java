package io.github.matheusanbs.wirelessautomate.tv;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * O índice do armazém de imagens (puro, com JUnit): cada imagem pelo resumo, com o que o cabeçalho disse, o tamanho,
 * quem enviou e quando, e quantas Telas e bibliotecas a usam. O uso não vem de varrer o mundo: as Telas que apontam
 * imagens ficam anotadas aqui, pela posição ({@code S}), com os resumos que usam, e as bibliotecas pelo jogador; cada
 * mudança de layout, Tela quebrada ou biblioteca troca só a anotação dela ({@link #setScreen}, {@link #removeScreen},
 * {@link #setLibrary}) e acerta as contas pela diferença. A anotação das Telas é guardada no disco com o índice; a das
 * bibliotecas é refeita da própria biblioteca ao carregar.
 * <p>
 * Uma imagem sem uso guarda desde quando ({@code unusedSince}); a limpeza ({@link #expired}) devolve as que passaram
 * da folga. Uma imagem {@code recovered} (achada no disco sem índice) nunca sai pela limpeza: o uso dela é
 * desconhecido até as Telas carregarem.
 */
public final class TvImageIndex<S> {
    /** Uma imagem do armazém. */
    public static final class Entry {
        public final String hash;
        public final TvImageFormat format;
        public final int width;
        public final int height;
        public final int frames;
        public final long bytes;
        public final @Nullable UUID author;
        public final long created;
        boolean recovered;
        int screens;
        int libraries;
        /** Desde quando ninguém a usa ({@code -1}: em uso). */
        long unusedSince;

        public Entry(String hash, TvImageFormat format, int width, int height, int frames, long bytes,
                @Nullable UUID author, long created) {
            this.hash = hash;
            this.format = format;
            this.width = width;
            this.height = height;
            this.frames = frames;
            this.bytes = bytes;
            this.author = author;
            this.created = created;
            this.unusedSince = created;
        }

        public int screens() {
            return screens;
        }

        public int libraries() {
            return libraries;
        }

        public int uses() {
            return screens + libraries;
        }

        public boolean recovered() {
            return recovered;
        }

        public long unusedSince() {
            return unusedSince;
        }
    }

    private static final String[] NONE = new String[0];

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<S, String[]> screens = new HashMap<>();
    private final Map<UUID, String[]> libraries = new HashMap<>();
    private final Map<UUID, Long> bytesByAuthor = new HashMap<>();
    private long totalBytes;

    public @Nullable Entry get(String hash) {
        return entries.get(hash);
    }

    public boolean contains(String hash) {
        return entries.containsKey(hash);
    }

    public int size() {
        return entries.size();
    }

    /** As imagens na ordem de chegada; só leitura. */
    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /** As Telas anotadas e os resumos de cada uma; só leitura (para gravar). */
    public Map<S, String[]> screens() {
        return Collections.unmodifiableMap(screens);
    }

    public long totalBytes() {
        return totalBytes;
    }

    public long bytesOf(UUID author) {
        return bytesByAuthor.getOrDefault(author, 0L);
    }

    /**
     * Guarda uma imagem nova (a que já existe fica como está), com as Telas e bibliotecas já anotadas que a apontam
     * contadas. Sem uso, conta a folga a partir de {@code now}.
     */
    public void add(Entry entry, boolean recovered, long now) {
        if (entries.containsKey(entry.hash)) {
            return;
        }
        entry.recovered = recovered;
        entries.put(entry.hash, entry);
        totalBytes += entry.bytes;
        if (entry.author != null) {
            bytesByAuthor.merge(entry.author, entry.bytes, Long::sum);
        }
        for (String[] hashes : screens.values()) {
            if (contains(hashes, entry.hash)) {
                entry.screens++;
            }
        }
        for (String[] hashes : libraries.values()) {
            if (contains(hashes, entry.hash)) {
                entry.libraries++;
            }
        }
        entry.unusedSince = entry.uses() > 0 ? -1 : now;
    }

    /**
     * Na carga, depois das anotações: a imagem ainda sem uso volta a contar a folga do instante gravado (o reinício do
     * servidor não renova a folga de quem já estava sem uso).
     */
    public void restoreUnusedSince(String hash, long since) {
        Entry entry = entries.get(hash);
        if (entry != null && entry.uses() == 0 && since >= 0) {
            entry.unusedSince = since;
        }
    }

    /** Tira a imagem do índice e das anotações (o comando de remover); devolve a que saiu, ou {@code null}. */
    public @Nullable Entry remove(String hash) {
        Entry entry = entries.remove(hash);
        if (entry == null) {
            return null;
        }
        totalBytes -= entry.bytes;
        if (entry.author != null) {
            long left = bytesByAuthor.getOrDefault(entry.author, 0L) - entry.bytes;
            if (left <= 0) {
                bytesByAuthor.remove(entry.author);
            } else {
                bytesByAuthor.put(entry.author, left);
            }
        }
        screens.replaceAll((key, hashes) -> without(hashes, hash));
        screens.values().removeIf(hashes -> hashes.length == 0);
        libraries.replaceAll((key, hashes) -> without(hashes, hash));
        libraries.values().removeIf(hashes -> hashes.length == 0);
        return entry;
    }

    /**
     * Anota os resumos que a Tela em {@code key} usa (vazio tira a anotação). Resumos que o índice não tem não são
     * anotados. Verdadeiro se a anotação mudou.
     */
    public boolean setScreen(S key, Collection<String> hashes, long now) {
        String[] next = known(hashes);
        String[] old = next.length == 0 ? screens.remove(key) : screens.put(key, next);
        if (old == null) {
            old = NONE;
        }
        if (same(old, next)) {
            return false;
        }
        count(old, next, now, true);
        return true;
    }

    /** A Tela em {@code key} saiu do mundo (quebrada): os usos dela saem. Verdadeiro se ela estava anotada. */
    public boolean removeScreen(S key, long now) {
        String[] old = screens.remove(key);
        if (old == null) {
            return false;
        }
        count(old, NONE, now, true);
        return true;
    }

    /** Anota os resumos da biblioteca do jogador (vazio tira a anotação). Verdadeiro se mudou. */
    public boolean setLibrary(UUID player, Collection<String> hashes, long now) {
        String[] next = known(hashes);
        String[] old = next.length == 0 ? libraries.remove(player) : libraries.put(player, next);
        if (old == null) {
            old = NONE;
        }
        if (same(old, next)) {
            return false;
        }
        count(old, next, now, false);
        return true;
    }

    /**
     * Os resumos sem uso há {@code graceMs} ou mais em {@code now}, até {@code limit}, na ordem de chegada; as
     * recuperadas ficam de fora.
     */
    public List<String> expired(long now, long graceMs, int limit) {
        List<String> out = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (out.size() >= limit) {
                break;
            }
            if (!entry.recovered && entry.uses() == 0 && entry.unusedSince >= 0 && now - entry.unusedSince >= graceMs) {
                out.add(entry.hash);
            }
        }
        return out;
    }

    /** Os resumos que a anotação de uma Tela teria: só os que o índice tem, sem repetir. */
    private String[] known(Collection<String> hashes) {
        if (hashes.isEmpty()) {
            return NONE;
        }
        List<String> out = new ArrayList<>(hashes.size());
        for (String hash : hashes) {
            if (entries.containsKey(hash) && !out.contains(hash)) {
                out.add(hash);
            }
        }
        return out.isEmpty() ? NONE : out.toArray(NONE);
    }

    /** Tira um uso de cada resumo de {@code old} e põe um em cada de {@code next}. */
    private void count(String[] old, String[] next, long now, boolean screen) {
        for (String hash : old) {
            Entry entry = entries.get(hash);
            if (entry != null) {
                if (screen) {
                    entry.screens--;
                } else {
                    entry.libraries--;
                }
                if (entry.uses() == 0) {
                    entry.unusedSince = now;
                }
            }
        }
        for (String hash : next) {
            Entry entry = entries.get(hash);
            if (entry != null) {
                if (screen) {
                    entry.screens++;
                } else {
                    entry.libraries++;
                }
                // Usada de novo: a imagem que a recuperação achou volta à limpeza normal quando perder o uso.
                entry.unusedSince = -1;
                entry.recovered = false;
            }
        }
    }

    private static boolean contains(String[] hashes, String hash) {
        for (String h : hashes) {
            if (h.equals(hash)) {
                return true;
            }
        }
        return false;
    }

    private static boolean same(String[] a, String[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (String hash : a) {
            if (!contains(b, hash)) {
                return false;
            }
        }
        return true;
    }

    private static String[] without(String[] hashes, String hash) {
        if (!contains(hashes, hash)) {
            return hashes;
        }
        List<String> out = new ArrayList<>(hashes.length);
        for (String h : hashes) {
            if (!h.equals(hash)) {
                out.add(h);
            }
        }
        return out.toArray(NONE);
    }
}
