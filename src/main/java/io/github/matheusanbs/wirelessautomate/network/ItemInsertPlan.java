package io.github.matheusanbs.wirelessautomate.network;

import java.util.Arrays;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Inserção num destino com uma varredura só por entrega. A simulação dá o mesmo resultado do
 * {@link ItemHandlerHelper#insertItemStacked} (primeiro as pilhas do mesmo item, depois os slots
 * vazios, na ordem dos slots), mas numa passada: anota os vazios enquanto procura as pilhas
 * iguais e lembra os slots que aceitaram. A inserção real vai direto neles; o que sobrar (destino
 * que mudou ou mentiu na simulação) cai no {@code insertItemStacked}, e o que nem ele aceitar
 * volta para quem chamou. Se o destino tem estoque, a mesma passada conta o item
 * (e então vai até o fim do inventário, ou até a contagem atingir o estoque).
 *
 * <p>Pilha igual já no limite do slot ({@link IItemHandler#getSlotLimit}): a primeira é simulada
 * na ordem (a do {@code insertItemStacked}). Se ela recusou, as outras cheias não são simuladas na hora: vão para o
 * fim, depois dos vazios, e só são tentadas se ainda sobrar. Um destino que aceita numa pilha
 * cheia (um upgrade que anula o excesso) aceita já na primeira, e a ordem não muda. Em
 * inventários de pilhas grandes, as cheias são a maior parte das chamadas, e as mais caras.
 * Cheia também é a pilha no tamanho máximo do item abaixo do limite do slot, se a primeira assim
 * recusou: é o baú vanilla (limite 99, pedregulho 64), em que o {@code InvWrapper} limita pelo
 * item. Aí as outras são reconhecidas só pela contagem, sem chamada nenhuma ao destino.
 *
 * <p>Memória da visita: a contagem de cada destino com estoque fica guardada até o fim da visita
 * da origem ({@link #recall}) e soma o que entregamos, então um destino que já atingiu o estoque
 * não é varrido de novo a cada slot da origem. Numa visita as contagens só sobem (só entregamos);
 * a memória de outro destino abaixo do estoque é esquecida a cada entrega, porque dois destinos
 * podem ver o mesmo inventário (metades de um baú duplo, interfaces da mesma rede de armazenamento).
 */
final class ItemInsertPlan {
    static final int COUNT_NONE = 0;
    /** Conta pelo item, como {@link ItemStack#isSameItem}. */
    static final int COUNT_ITEM = 1;
    /** Conta pelo item e componentes, como {@link ItemStack#isSameItemSameComponents}. */
    static final int COUNT_COMPONENTS = 2;
    /** Destinos lembrados por visita; além disso, a contagem é refeita na varredura. */
    private static final int MEMORY = 32;

    private int[] slots = new int[8];
    private int size;
    /**
     * O primeiro slot do plano que ainda pode aceitar: os de antes lotaram num {@link #execute} anterior. A rajada
     * insere várias vezes no mesmo plano, e sem isto cada passo bateria de novo em todas as pilhas já cheias.
     */
    private int open;
    private int[] empties = new int[64];
    private int[] fulls = new int[16];
    private long counted;
    /** Vazios anotados pela última {@link #simulate} (em {@link #empties}). */
    private int emptySeen;
    /** A última {@link #simulate} viu uma pilha do mesmo item no destino. */
    private boolean sameSeen;
    /** Tamanho máximo do item da simulação em curso ({@link #insert}). */
    private int itemMax;

    private final Port[] memoPorts = new Port[MEMORY];
    private final Item[] memoItems = new Item[MEMORY];
    /** Com componentes: a pilha (cópia de 1); sem: {@code null}. */
    private final ItemStack[] memoStacks = new ItemStack[MEMORY];
    private final long[] memoCounts = new long[MEMORY];
    private final long[] memoStocks = new long[MEMORY];
    private int memoSize;

    /** Começo da visita de uma origem: esquece as contagens da anterior. */
    void beginVisit() {
        if (memoSize > 0) {
            Arrays.fill(memoPorts, 0, memoSize, null);
            Arrays.fill(memoItems, 0, memoSize, null);
            Arrays.fill(memoStacks, 0, memoSize, null);
            memoSize = 0;
        }
    }

    /** Posição da contagem lembrada do item no destino, ou −1. */
    int recall(Port destination, ItemStack stack, boolean components) {
        for (int i = 0; i < memoSize; i++) {
            if (memoPorts[i] == destination && matches(i, stack, components)) {
                return i;
            }
        }
        return -1;
    }

    long recalled(int index) {
        return memoCounts[index];
    }

    /** Lembra a contagem do item no destino; devolve a posição, ou −1 sem espaço. */
    int remember(Port destination, ItemStack stack, boolean components, long count, long stock) {
        if (memoSize == MEMORY) {
            return -1;
        }
        int i = memoSize++;
        memoPorts[i] = destination;
        memoItems[i] = stack.getItem();
        memoStacks[i] = components ? stack.copyWithCount(1) : null;
        memoCounts[i] = count;
        memoStocks[i] = stock;
        return i;
    }

    /** Entregamos {@code amount} de {@code stack} no destino: soma no que foi lembrado dele. */
    void delivered(Port destination, ItemStack stack, int amount) {
        for (int i = 0; i < memoSize; i++) {
            Port port = memoPorts[i];
            if (port == destination) {
                if (matches(i, stack, memoStacks[i] != null)) {
                    memoCounts[i] += amount;
                }
            } else if (port != null && memoCounts[i] < memoStocks[i]) {
                // Pode ser o mesmo inventário por outro caminho: abaixo do estoque, conta de novo.
                memoPorts[i] = null;
            }
        }
    }

    private boolean matches(int i, ItemStack stack, boolean components) {
        ItemStack exact = memoStacks[i];
        if (components) {
            return exact != null && ItemStack.isSameItemSameComponents(exact, stack);
        }
        return exact == null && memoItems[i] == stack.getItem();
    }

    /**
     * Simula a inserção de {@code stack} e guarda o plano; com {@code count}, conta também o
     * item no destino ({@link #counted}) e para quando a contagem chega a {@code stock} (aí o
     * destino não aceita nada pelo estoque, e a resposta é 0). Devolve quanto entraria.
     */
    int simulate(IItemHandler target, ItemStack stack, int count, long stock) {
        size = 0;
        open = 0;
        itemMax = stack.getMaxStackSize();
        counted = 0;
        emptySeen = 0;
        sameSeen = false;
        int emptyCount = 0;
        int fullCount = 0;
        boolean fullRefused = false;
        // Tamanho máximo do item, se o destino limita pelo item (ver abaixo); 0 = não limita.
        int itemFull = 0;
        ItemStack rest = stack;
        for (int slot = 0, n = target.getSlots(); slot < n; slot++) {
            boolean placing = !rest.isEmpty();
            if (!placing && count == COUNT_NONE) {
                break;
            }
            ItemStack inSlot = target.getStackInSlot(slot);
            if (inSlot.isEmpty()) {
                if (placing) {
                    if (emptyCount == empties.length) {
                        empties = Arrays.copyOf(empties, emptyCount * 2);
                    }
                    empties[emptyCount++] = slot;
                }
                continue;
            }
            boolean same = ItemStack.isSameItemSameComponents(inSlot, stack);
            sameSeen |= same;
            if (count == COUNT_COMPONENTS ? same : count == COUNT_ITEM && ItemStack.isSameItem(inSlot, stack)) {
                counted += inSlot.getCount();
                if (counted >= stock) {
                    // Estoque atingido: o resto do inventário não muda a resposta.
                    size = 0;
                    return 0;
                }
            }
            if (same && placing) {
                int inCount = inSlot.getCount();
                if (fullRefused && (itemFull > 0 && inCount >= itemFull
                        || inCount >= target.getSlotLimit(slot))) {
                    if (fullCount == fulls.length) {
                        fulls = Arrays.copyOf(fulls, fullCount * 2);
                    }
                    fulls[fullCount++] = slot;
                } else {
                    int before = rest.getCount();
                    rest = offer(target, slot, rest);
                    if (rest.getCount() == before && !fullRefused) {
                        if (inCount >= target.getSlotLimit(slot)) {
                            fullRefused = true;
                        } else {
                            // Recusou no tamanho máximo do item, abaixo do limite do slot (baú
                            // vanilla: limite 99, pedregulho 64): o destino limita pelo item. As
                            // pilhas iguais têm o mesmo item e componentes, logo o mesmo máximo,
                            // lido uma vez só (o getMaxStackSize consulta os componentes e é a
                            // parte cara de cada recusa do InvWrapper).
                            int max = inSlot.getMaxStackSize();
                            if (inCount >= max) {
                                fullRefused = true;
                                itemFull = max;
                            }
                        }
                    }
                }
            }
        }
        emptySeen = emptyCount;
        for (int i = 0; i < emptyCount && !rest.isEmpty(); i++) {
            rest = offer(target, empties[i], rest);
        }
        for (int i = 0; i < fullCount && !rest.isEmpty(); i++) {
            rest = offer(target, fulls[i], rest);
        }
        return stack.getCount() - rest.getCount();
    }

    /**
     * A última {@link #simulate} não aceitou nada porque o destino recusa o item, não por falta
     * de espaço: havia slot vazio, nenhum deles o aceita ({@link IItemHandler#isItemValid}) e não
     * havia pilha dele no destino. Uma máquina cheia de verdade fica de fora: sem vazio, ou com
     * a pilha do item lotada, ou com um vazio que aceita o item e mesmo assim recusou (a saída da
     * máquina cheia). Só roda na recusa, e só nos vazios anotados.
     */
    boolean refusesItem(IItemHandler target, ItemStack stack) {
        if (sameSeen || emptySeen == 0) {
            return false;
        }
        for (int i = 0; i < emptySeen; i++) {
            if (target.isItemValid(empties[i], stack)) {
                return false;
            }
        }
        return true;
    }

    /** Quanto do item a última {@link #simulate} contou no destino. */
    long counted() {
        return counted;
    }

    private ItemStack offer(IItemHandler target, int slot, ItemStack stack) {
        ItemStack rest = insert(target, slot, stack, itemMax, true);
        if (rest.getCount() < stack.getCount()) {
            if (size == slots.length) {
                slots = Arrays.copyOf(slots, size * 2);
            }
            slots[size++] = slot;
        }
        return rest;
    }

    /**
     * Insere de verdade nos slots do plano; o que sobrar vai pelo empilhado. Devolve a sobra. Vem
     * sempre depois da {@link #simulate} do mesmo item, de quem reaproveita o tamanho máximo.
     */
    ItemStack execute(IItemHandler target, ItemStack stack) {
        ItemStack rest = stack;
        for (int i = open; i < size && !rest.isEmpty(); i++) {
            rest = insert(target, slots[i], rest, itemMax, false);
            if (!rest.isEmpty()) {
                // O slot recusou parte: lotou, e a rajada não volta nele.
                open = i + 1;
            }
        }
        return rest.isEmpty() ? rest : insertStacked(target, rest, itemMax);
    }

    /**
     * {@link IItemHandler#insertItem} sem passar, numa chamada, do que o slot aceita: até o tamanho
     * máximo do item ({@code itemMax}), ou até o limite do slot se ele passa de
     * {@link Item#ABSOLUTE_MAX_STACK_SIZE} (99, o limite comum do vanilla e do {@code ItemStackHandler}).
     * Um slot que declara mais que isso (upgrade de pilha, gaveta) recebe a pilha grande de uma vez
     * na simulação; de verdade, só depois de mostrar que guarda o item ({@link #insertInStacks}). Os
     * outros slots nunca veem uma pilha acima do tamanho do item, mesmo que não a limitem. Devolve a
     * sobra.
     */
    static ItemStack insert(IItemHandler target, int slot, ItemStack stack, int itemMax, boolean simulate) {
        int count = stack.getCount();
        if (count > itemMax) {
            int limit = target.getSlotLimit(slot);
            if (limit > Item.ABSOLUTE_MAX_STACK_SIZE && !simulate) {
                return insertInStacks(target, slot, stack, itemMax, limit);
            }
            int cap = limit > Item.ABSOLUTE_MAX_STACK_SIZE ? Math.max(itemMax, limit) : itemMax;
            if (count > cap) {
                ItemStack rest = target.insertItem(slot, stack.copyWithCount(cap), simulate);
                return stack.copyWithCount(count - cap + rest.getCount());
            }
        }
        return target.insertItem(slot, stack, simulate);
    }

    /**
     * Insere de verdade num slot de limite grande, até o limite dele. O slot que mostra o item recebe
     * o resto numa chamada só; o que continua vazio depois de receber (a lixeira do Trash Cans, que
     * diz aceitar {@code Integer.MAX_VALUE} e guarda a pilha como veio) recebe uma pilha do tamanho
     * do item por chamada: com a pilha grande, o codec do {@link ItemStack}, que não passa de 99,
     * derrubava o servidor quando ela salvava. Para quando o slot recusa algo. Devolve a sobra.
     */
    private static ItemStack insertInStacks(IItemHandler target, int slot, ItemStack stack, int itemMax, int limit) {
        int count = stack.getCount();
        int offered = Math.min(count, Math.max(itemMax, limit));
        int left = offered;
        int refused = 0;
        while (left > 0) {
            boolean keeps = ItemStack.isSameItemSameComponents(target.getStackInSlot(slot), stack);
            int chunk = keeps ? left : Math.min(left, itemMax);
            ItemStack rest = target.insertItem(slot, stack.copyWithCount(chunk), false);
            left -= chunk;
            if (!rest.isEmpty()) {
                refused = rest.getCount();
                break;
            }
        }
        int back = count - offered + left + refused;
        return back == 0 ? ItemStack.EMPTY : stack.copyWithCount(back);
    }

    /**
     * O {@link ItemHandlerHelper#insertItemStacked} (primeiro as pilhas do mesmo item, depois os
     * vazios, na ordem dos slots), com cada chamada limitada como em {@link #insert}. Devolve a sobra.
     */
    static ItemStack insertStacked(IItemHandler target, ItemStack stack, int itemMax) {
        ItemStack rest = stack;
        int slots = target.getSlots();
        if (stack.isStackable()) {
            for (int slot = 0; slot < slots && !rest.isEmpty(); slot++) {
                if (ItemStack.isSameItemSameComponents(target.getStackInSlot(slot), rest)) {
                    rest = insert(target, slot, rest, itemMax, false);
                }
            }
            for (int slot = 0; slot < slots && !rest.isEmpty(); slot++) {
                if (target.getStackInSlot(slot).isEmpty()) {
                    rest = insert(target, slot, rest, itemMax, false);
                }
            }
        } else {
            for (int slot = 0; slot < slots && !rest.isEmpty(); slot++) {
                rest = insert(target, slot, rest, itemMax, false);
            }
        }
        return rest;
    }
}
