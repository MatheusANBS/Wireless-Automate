package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.net.ByteBufCodecs;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenCustomHashMap;
import java.util.ArrayList;
import java.util.List;

/**
 * O que a tela em lista mostra, no cliente: os tipos com a quantidade, montados a partir das
 * diferenças que o servidor manda ({@link #apply}), e o cabeçalho (total, capacidade, filtro). A
 * {@link #version()} sobe a cada mudança; a tela reordena e refiltra só quando ela muda.
 */
public final class StorageListView<K> {
    /**
     * Um tipo e a quantidade nova; 0 = o tipo saiu. Porte 1.20.1: {@code ref} &gt; 0 é a referência da chave no menu
     * do servidor, dada às chaves grandes ({@code StorageEntriesPayload#REF_BYTES}); as ações sobre ela vão pela
     * referência, sem mandar o NBT de volta.
     */
    public record Entry<K>(K key, long count, int ref) {
        public Entry(K key, long count) {
            this(key, count, 0);
        }
    }

    /** Cabeçalho: total guardado, capacidade (0 = sem limite) e se há filtro de entrada. */
    public record Header(long total, long capacity, boolean filtered) {
        public static final Header EMPTY = new Header(0, 0, false);
        public static final StreamCodec<RegistryFriendlyByteBuf, Header> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Header::total,
                ByteBufCodecs.VAR_LONG, Header::capacity,
                ByteBufCodecs.BOOL, Header::filtered,
                Header::new);
    }

    private final ListKind<K> kind;
    private final Object2LongLinkedOpenCustomHashMap<K> counts;
    private final Object2IntOpenCustomHashMap<K> refs;
    private Header header = Header.EMPTY;
    private int version;

    public StorageListView(ListKind<K> kind) {
        this.kind = kind;
        this.counts = new Object2LongLinkedOpenCustomHashMap<>(kind.strategy);
        this.refs = new Object2IntOpenCustomHashMap<>(kind.strategy);
    }

    public ListKind<K> kind() {
        return kind;
    }

    public Header header() {
        return header;
    }

    public int version() {
        return version;
    }

    public int types() {
        return counts.size();
    }

    public long count(K key) {
        return counts.getLong(key);
    }

    /** A referência da chave no menu do servidor, ou 0: a ação manda a própria chave. */
    public int ref(K key) {
        return refs.getInt(key);
    }

    /** A chave é o substituto de uma grande demais: a tela mostra uma marca (o NBT não veio). */
    public boolean truncated(K key) {
        return kind.isTruncated(key);
    }

    /** Os tipos na ordem de chegada (a tela ordena a cópia). */
    public List<K> keys() {
        return new ArrayList<>(counts.keySet());
    }

    /** Aplica um pacote do servidor; {@code reset} esvazia antes (o primeiro de uma sincronização inteira). */
    public void apply(boolean reset, Header header, List<Entry<K>> entries) {
        if (reset) {
            counts.clear();
            refs.clear();
        }
        for (Entry<K> entry : entries) {
            if (entry.count() <= 0) {
                counts.removeLong(entry.key());
                refs.removeInt(entry.key());
            } else {
                counts.put(entry.key(), entry.count());
                if (entry.ref() > 0) {
                    refs.put(entry.key(), entry.ref());
                } else {
                    refs.removeInt(entry.key());
                }
            }
        }
        this.header = header;
        version++;
    }
}
