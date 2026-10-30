package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A Compactação de um Baú: cada inserção de fora de um item de cadeia (sem componentes) suja a cadeia, e no fim do
 * tick ({@link #convertDirty}) cada cadeia suja vai para o alvo dela, O(formas) por cadeia. A conversão é movimento
 * interno do {@link ItemStorage} ({@code insertInternal}/{@code extractInternal}): não passa pelo filtro de entrada,
 * não conta no vigia e não suja a cadeia de novo. Uma cadeia com variantes ({@link VariantGroup}) leva o grupo
 * inteiro: as outras variantes viram a preferida 1 por 1 antes da conversão, O(formas × variantes).
 *
 * <p>Sem o upgrade as regras são nulas, o {@link ItemStorage} não tem ouvinte e nada aqui roda. O conjunto das
 * cadeias sujas é criado na primeira inserção de cadeia. Os ids das cadeias só valem numa geração do
 * {@link CompactingChains}: depois de um {@code /reload}, as sujas são descartadas e todas as presentes marcadas de
 * novo, senão um id velho converteria outra cadeia. Quem percebe a troca primeiro (o painel, uma inserção, o fim do
 * tick) só descarta e põe o Baú na fila do fim do tick; a remarcação roda lá, uma vez.
 */
public final class ChestCompacting implements ItemStorage.InsertListener {
    private final ItemStorage storage;
    private final LongSupplier capacity;
    /** Põe o Baú na fila do fim do tick, onde {@link #convertDirty} roda. */
    private final Runnable enqueue;
    private @Nullable CompactingRules rules;
    private @Nullable IntOpenHashSet dirty;
    /** A geração do índice em que os ids de {@link #dirty} e de {@link #present} valem. */
    private int generation;
    /** A geração mudou e as cadeias presentes ainda não foram marcadas de novo. */
    private boolean remark;
    private int version;
    /** As cadeias presentes, montadas sob demanda; nulo até alguém pedir. */
    private @Nullable IntArrayList present;
    /** A versão do conteúdo em que {@link #present} foi montada. */
    private int presentContents;
    /** As contagens das formas do {@link #stockCount}, uma por tamanho de cadeia, reusadas entre chamadas. */
    private long[][] scratch = new long[0][];

    /**
     * @param capacity a capacidade do Baú ({@code <= 0} é sem limite)
     * @param enqueue põe o Baú na fila do fim do tick (o {@code markContentsChanged} dele)
     */
    public ChestCompacting(ItemStorage storage, LongSupplier capacity, Runnable enqueue) {
        this.storage = storage;
        this.capacity = capacity;
        this.enqueue = enqueue;
        this.generation = CompactingChains.get().generation();
    }

    public @Nullable CompactingRules rules() {
        return rules;
    }

    /**
     * Troca as regras; {@code null} é sem o upgrade (o {@link ItemStorage} fica sem ouvinte). Não suja nada: quem
     * troca chama {@link #markAll} quando o conteúdo tem de ser convertido de novo.
     */
    public void setRules(@Nullable CompactingRules next) {
        if (next == null ? rules == null : next.equals(rules)) {
            return;
        }
        rules = next;
        version++;
        storage.setInsertListener(next == null ? null : this);
        if (next == null) {
            dirty = null;
            present = null;
        }
    }

    /** Sobe quando as regras ou as cadeias presentes mudam. */
    public int version() {
        refreshPresent();
        return version;
    }

    @Override
    public void inserted(ItemStack key) {
        if (rules == null || !key.isComponentsPatchEmpty()) {
            return;
        }
        CompactingChains chains = CompactingChains.get();
        catchUp(chains);
        if (remark) {
            markAll();
        }
        CompactingChain chain = chains.chainOf(key.getItem());
        if (chain != null) {
            dirty().add(chain.id());
        }
    }

    private IntOpenHashSet dirty() {
        if (dirty == null) {
            dirty = new IntOpenHashSet();
        }
        return dirty;
    }

    /**
     * Se o índice foi remontado, descarta os ids guardados (apontariam para outras cadeias), passa à geração nova e,
     * com o upgrade, deixa a remarcação pendente e o Baú na fila do fim do tick.
     *
     * @return se a geração mudou agora
     */
    private boolean catchUp(CompactingChains chains) {
        if (chains.generation() == generation) {
            return false;
        }
        generation = chains.generation();
        if (dirty != null) {
            dirty.clear();
        }
        present = null;
        version++;
        if (rules != null) {
            remark = true;
            enqueue.run();
        }
        return true;
    }

    /** Suja todas as cadeias presentes no Baú, numa passada pelos tipos. */
    public void markAll() {
        if (rules == null) {
            return;
        }
        CompactingChains chains = CompactingChains.get();
        catchUp(chains);
        remark = false;
        for (int i = 0, n = storage.slots(); i < n; i++) {
            ItemStack key = storage.key(i);
            if (storage.count(i) > 0 && key.isComponentsPatchEmpty()) {
                CompactingChain chain = chains.chainOf(key.getItem());
                if (chain != null) {
                    dirty().add(chain.id());
                }
            }
        }
    }

    /**
     * Suja uma cadeia pelo id. Um id só vale na geração do índice em que foi lido: se o índice foi remontado desde a
     * última conta do Baú, o id é descartado (a remarcação de todas as presentes já ficou pendente) e o mesmo vale
     * para um id que nenhuma cadeia tem.
     */
    public void markChain(int chainId) {
        if (rules == null) {
            return;
        }
        CompactingChains chains = CompactingChains.get();
        if (!catchUp(chains) && chains.byId(chainId) != null) {
            dirty().add(chainId);
            enqueue.run();
        }
    }

    /** Fim do tick: leva cada cadeia suja ao alvo dela. */
    public void convertDirty() {
        if (rules == null) {
            return;
        }
        CompactingChains chains = CompactingChains.get();
        catchUp(chains);
        if (remark) {
            markAll();
        }
        IntOpenHashSet pending = dirty;
        if (pending == null || pending.isEmpty()) {
            return;
        }
        // Um iterador por Baú com cadeia suja, no fim do tick; nunca por inserção.
        for (IntIterator it = pending.iterator(); it.hasNext(); ) {
            CompactingChain chain = chains.byId(it.nextInt());
            if (chain != null) {
                convert(chains, chain);
            }
        }
        // Sem trim por tick: o conjunto volta a crescer na próxima inserção.
        pending.clear();
    }

    /**
     * Leva a cadeia ao alvo. Com variantes, a cadeia suja processa o grupo dela: primeiro as outras variantes viram a
     * preferida, nível a nível e 1 por 1, e depois a preferida é convertida.
     */
    private void convert(CompactingChains chains, CompactingChain dirtyChain) {
        VariantGroup group = chains.groupOf(dirtyChain);
        CompactingChain chain = group == null ? dirtyChain : rules.preferredOf(group);
        int target = rules.targetLevel(chain);
        if (target < 0) {
            return;
        }
        long cap = capacity.getAsLong();
        // Acima da capacidade (o tier ou a config encolheu), tirar e pôr de volta poderia não caber: espera espaço.
        if (cap > 0 && storage.total() > cap) {
            return;
        }
        if (group != null) {
            swapInto(group, chain);
        }
        long total = storage.total();
        ItemStack[] keys = chain.keys();
        long[] counts = new long[keys.length];
        for (int i = 0; i < keys.length; i++) {
            counts[i] = storage.count(keys[i]);
        }
        long room = cap <= 0 ? Long.MAX_VALUE : cap - total;
        long[] next = CompactingMath.convert(counts, chain.factors(), target, room);
        for (int i = 0; i < keys.length; i++) {
            if (next[i] < counts[i]) {
                storage.extractInternal(keys[i], counts[i] - next[i]);
            }
        }
        for (int i = 0; i < keys.length; i++) {
            if (next[i] > counts[i] && storage.insertInternal(keys[i], next[i] - counts[i]) < next[i] - counts[i]) {
                WirelessAutomate.LOGGER.warn("Compactação: a cadeia de {} não coube na conversão; o conteúdo volta como estava",
                        chain.base());
                restore(keys, counts);
                return;
            }
        }
    }

    /**
     * Troca as outras variantes do grupo pela preferida, nível a nível e 1 por 1, por dentro (sem admissão nem vigia).
     * O total não muda: tira e põe a mesma quantidade, então sempre cabe; se o pôr falhar, devolve o que tirou.
     * O(formas × variantes), sem alocar.
     */
    private void swapInto(VariantGroup group, CompactingChain preferred) {
        ItemStack[] into = preferred.keys();
        List<CompactingChain> variants = group.chains();
        for (int v = 0; v < variants.size(); v++) {
            CompactingChain variant = variants.get(v);
            if (variant == preferred) {
                continue;
            }
            ItemStack[] from = variant.keys();
            for (int level = 0; level < from.length; level++) {
                long moved = storage.extractInternal(from[level], storage.count(from[level]));
                if (moved <= 0) {
                    continue;
                }
                long put = storage.insertInternal(into[level], moved);
                if (put < moved) {
                    WirelessAutomate.LOGGER.warn("Compactação: a troca de {} por {} não coube; o resto volta como estava",
                            from[level].getItem(), into[level].getItem());
                    storage.extractInternal(into[level], put);
                    storage.insertInternal(from[level], moved);
                    return;
                }
            }
        }
    }

    /**
     * Volta cada forma à contagem de antes: tira primeiro o que sobrou e depois devolve o que saiu, então o total
     * nunca passa do de antes, que cabia.
     */
    private void restore(ItemStack[] keys, long[] counts) {
        for (int i = 0; i < keys.length; i++) {
            long now = storage.count(keys[i]);
            if (now > counts[i]) {
                storage.extractInternal(keys[i], now - counts[i]);
            }
        }
        for (int i = 0; i < keys.length; i++) {
            long now = storage.count(keys[i]);
            if (now < counts[i]) {
                storage.insertInternal(keys[i], counts[i] - now);
            }
        }
    }

    /**
     * Quanto a cadeia da chave vale na unidade dela, para o limite de estoque, com as variantes do grupo dela somadas
     * no mesmo nível; −1 se a chave não é de cadeia, tem componentes ou a cadeia não é convertida (sem alvo).
     * O(formas × variantes), sem alocar.
     */
    public long stockCount(ItemStack key) {
        if (rules == null || !key.isComponentsPatchEmpty()) {
            return -1;
        }
        CompactingChains chains = CompactingChains.get();
        CompactingChain chain = chains.chainOf(key.getItem());
        if (chain == null) {
            return -1;
        }
        VariantGroup group = chains.groupOf(chain);
        if (rules.targetLevel(group == null ? chain : rules.preferredOf(group)) < 0) {
            return -1;
        }
        ItemStack[] keys = chain.keys();
        long[] counts = scratch(keys.length);
        for (int i = 0; i < keys.length; i++) {
            counts[i] = storage.count(keys[i]);
        }
        if (group != null) {
            // As variantes têm as mesmas formas e fatores: o nível i de uma soma com o nível i da chave.
            List<CompactingChain> variants = group.chains();
            for (int v = 0; v < variants.size(); v++) {
                ItemStack[] other = variants.get(v).keys();
                if (other == keys) {
                    continue;
                }
                for (int i = 0; i < keys.length; i++) {
                    counts[i] += storage.count(other[i]);
                }
            }
        }
        return CompactingMath.equivalent(counts, chain.factors(), chain.levelOf(key.getItem()));
    }

    /** O vetor reusado do tamanho exato: o {@link CompactingMath#equivalent} lê o vetor inteiro. */
    private long[] scratch(int length) {
        if (scratch.length <= length) {
            scratch = Arrays.copyOf(scratch, length + 1);
        }
        long[] counts = scratch[length];
        if (counts == null) {
            counts = new long[length];
            scratch[length] = counts;
        }
        return counts;
    }

    /**
     * Desce por dentro a forma mais próxima acima da chave, uma por vez, até haver {@code need} da chave (a Mesa e o
     * JEI pegam dali). A sobra fica e não suja a cadeia: volta a ser compactada na próxima entrada dela.
     *
     * @return se há {@code need} da chave no fim
     */
    public boolean unpackFor(ItemStack key, long need) {
        if (storage.count(key) >= need) {
            return true;
        }
        if (rules == null || !key.isComponentsPatchEmpty()) {
            return false;
        }
        CompactingChain chain = CompactingChains.get().chainOf(key.getItem());
        if (chain == null) {
            return false;
        }
        int level = chain.levelOf(key.getItem());
        ItemStack[] keys = chain.keys();
        int[] factors = chain.factors();
        while (storage.count(key) < need) {
            int from = level + 1;
            while (from < keys.length && storage.count(keys[from]) <= 0) {
                from++;
            }
            if (from >= keys.length) {
                return false;
            }
            long cap = capacity.getAsLong();
            if (cap > 0 && cap - storage.total() < factors[from] - 1) {
                return false;
            }
            storage.extractInternal(keys[from], 1);
            storage.insertInternal(keys[from - 1], factors[from]);
        }
        return true;
    }

    /** As cadeias com algum item no Baú, pelo id (a ordem do índice); remontada só quando o conteúdo mudou. */
    public IntList presentChains() {
        refreshPresent();
        return present;
    }

    private void refreshPresent() {
        CompactingChains chains = CompactingChains.get();
        catchUp(chains);
        int contents = storage.version();
        if (present != null && presentContents == contents) {
            return;
        }
        // Só com o painel aberto e o conteúdo mudado: alocar aqui não toca a inserção.
        IntOpenHashSet seen = new IntOpenHashSet();
        for (int i = 0, n = storage.slots(); i < n; i++) {
            ItemStack key = storage.key(i);
            if (storage.count(i) > 0 && key.isComponentsPatchEmpty()) {
                CompactingChain chain = chains.chainOf(key.getItem());
                if (chain != null) {
                    seen.add(chain.id());
                }
            }
        }
        int[] ids = seen.toIntArray();
        Arrays.sort(ids);
        IntArrayList next = IntArrayList.wrap(ids);
        if (present == null || !next.equals(present)) {
            version++;
            present = next;
        }
        presentContents = contents;
    }
}
