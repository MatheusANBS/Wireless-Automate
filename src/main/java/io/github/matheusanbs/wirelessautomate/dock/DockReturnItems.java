package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * A volta de itens para uma rede que simula ({@link DockReturnNetwork} sem {@code insertsFirst}: a ME do AE2) juntada
 * numa visita de um lado: o {@link #gather} anota, máquina a máquina, o que cada slot das faces Extrai oferece, sem
 * tirar nada; o {@link #commit} faz, por item, uma simulação na rede com a soma de todos os slots e máquinas, tira das
 * máquinas (na ordem das anotações) só o que a simulação aceitou e insere de verdade o que saiu, numa chamada. O custo
 * da rede é por chamada (no AE2, cada inserção passa pelas CPUs que esperam o item): com cinco Infinite Factories, 85
 * slots por tick viram duas chamadas por item, em vez de duas por slot.
 *
 * <p>Nada se perde nem duplica: a máquina só dá o que a rede aceitou na simulação, e a rede só recebe o que saiu da
 * máquina. O que a rede recusar de verdade depois de aceitar na simulação volta às máquinas de onde saiu, da última
 * anotação para a primeira, e o que não couber nelas vai ao chão, na Doca ({@link #keep}); a máquina que dá menos do que
 * ofereceu só faz a rede receber menos.
 *
 * <p>Também guarda as extrações de um slot que a volta usa nos outros destinos ({@link #take}, {@link #keep}). Só a
 * thread do servidor mexe aqui; os vetores são de uma Doca e reaproveitados (crescem até o maior número de slots de
 * uma visita).
 */
final class DockReturnItems {
    private static final RemoteSide[] FACES = RemoteSide.values();
    private static final Direction[] DIRECTIONS = Direction.values();

    /** As anotações: o inventário, o slot, quanto oferece, quanto saiu, o item ({@link #items}) e a máquina da visita. */
    private IItemHandler[] handlers = new IItemHandler[32];
    private int[] slots = new int[32];
    private int[] amounts = new int[32];
    private int[] gave = new int[32];
    private int[] groups = new int[32];
    private int[] owners = new int[32];
    private int size;
    /** Os itens anotados (a pilha da extração simulada, que só serve de modelo) e a soma de cada um. */
    private ItemStack[] items = new ItemStack[4];
    private long[] totals = new long[4];
    private int groupCount;
    /** O que ainda cabe no balde do lado nesta visita. */
    private long limit;
    /** O total devolvido pelo último {@link #commit}. */
    private long returned;

    /** Começa a visita de um lado, com {@code limit} no balde de itens. */
    void start(long limit) {
        this.limit = limit;
        size = 0;
        groupCount = 0;
    }

    /** O total que o último {@link #commit} devolveu à rede. */
    long returned() {
        return returned;
    }

    /**
     * Anota o que a máquina oferece pelas faces Extrai de itens do perfil, sem tirar nada: por slot, uma extração
     * simulada (o que a face deixa tirar, e de que item) e, se o slot guarda mais que uma extração do mesmo item, o
     * que ele guarda, até {@link DockReturn#MAX_EXTRACTIONS_PER_SLOT} extrações e o que resta do balde. Devolve
     * {@link DockReturn#HAS_MORE} se ficou algo para a próxima visita, e com {@link DockReturn#NO_RATE} se o balde
     * acabou.
     */
    int gather(MachineCaches caches, MachineConfig profile, int front, int owner) {
        int outcome = 0;
        for (int face = 0; face < FACES.length; face++) {
            if (!profile.face(ResourceType.ITEM, FACES[face]).mode().extracts()) {
                continue;
            }
            IItemHandler handler = caches.items(DIRECTIONS[FACES[face].toAbsolute(front)]);
            if (handler == null) {
                continue;
            }
            for (int slot = 0, slots = handler.getSlots(); slot < slots; slot++) {
                if (limit <= 0) {
                    return outcome | DockReturn.HAS_MORE | DockReturn.NO_RATE;
                }
                ItemStack offer = handler.extractItem(slot, (int) Math.min(limit, Integer.MAX_VALUE), true);
                if (offer.isEmpty()) {
                    continue;
                }
                long one = offer.getCount();
                ItemStack inSlot = handler.getStackInSlot(slot);
                long held;
                if (ItemStack.isSameItemSameComponents(inSlot, offer)) {
                    held = Math.max(inSlot.getCount(), one);
                } else {
                    // O slot não mostra o que a face dá: uma extração, e a pilha cheia volta na próxima visita.
                    held = one;
                    if (one >= offer.getMaxStackSize()) {
                        outcome |= DockReturn.HAS_MORE;
                    }
                }
                long want = Math.min(Math.min(held, one * DockReturn.MAX_EXTRACTIONS_PER_SLOT), limit);
                if (held > want) {
                    outcome |= DockReturn.HAS_MORE;
                }
                add(handler, slot, offer, (int) Math.min(want, Integer.MAX_VALUE), owner);
                limit -= want;
            }
        }
        return outcome;
    }

    private void add(IItemHandler handler, int slot, ItemStack item, int amount, int owner) {
        int group = 0;
        while (group < groupCount && !ItemStack.isSameItemSameComponents(items[group], item)) {
            group++;
        }
        if (group == groupCount) {
            if (groupCount == items.length) {
                items = Arrays.copyOf(items, groupCount * 2);
                totals = Arrays.copyOf(totals, groupCount * 2);
            }
            items[group] = item;
            totals[group] = 0;
            groupCount++;
        }
        totals[group] += amount;
        if (size == handlers.length) {
            int grown = size * 2;
            handlers = Arrays.copyOf(handlers, grown);
            slots = Arrays.copyOf(slots, grown);
            amounts = Arrays.copyOf(amounts, grown);
            gave = Arrays.copyOf(gave, grown);
            groups = Arrays.copyOf(groups, grown);
            owners = Arrays.copyOf(owners, grown);
        }
        handlers[size] = handler;
        slots[size] = slot;
        amounts[size] = amount;
        gave[size] = 0;
        groups[size] = group;
        owners[size] = owner;
        size++;
    }

    /**
     * Põe na rede o que foi anotado, item a item (ver a classe), e soma em {@code outcomes[máquina]}
     * {@link DockReturn#GAVE} para a que deu algo e {@link DockReturn#HAS_MORE} com {@link DockReturn#FULL} para a que
     * ficou com algo que a rede recusou. O total vai em {@link #returned}. Larga as anotações no fim.
     */
    void commit(ServerLevel level, BlockPos dockPos, DockReturnNetwork network, int[] outcomes) {
        returned = 0;
        for (int group = 0; group < groupCount; group++) {
            ItemStack item = items[group];
            long room = network.insertItems(item, totals[group], true);
            long taken = 0;
            for (int i = 0; i < size; i++) {
                if (groups[i] != group) {
                    continue;
                }
                int want = (int) Math.min(amounts[i], Math.max(0, room));
                if (want < amounts[i]) {
                    outcomes[owners[i]] |= DockReturn.HAS_MORE | DockReturn.FULL;
                }
                if (want <= 0) {
                    continue;
                }
                int got = takeCount(level, dockPos, handlers[i], slots[i], item, want);
                gave[i] = got;
                room -= got;
                taken += got;
            }
            if (taken <= 0) {
                continue;
            }
            long back = taken - Math.max(0, Math.min(taken, network.insertItems(item, taken, false)));
            // A rede recusou de verdade o que aceitou na simulação: volta às máquinas, da última para a primeira.
            for (int i = size - 1; i >= 0 && back > 0; i--) {
                if (groups[i] != group || gave[i] <= 0) {
                    continue;
                }
                int n = (int) Math.min(back, gave[i]);
                keep(level, dockPos, handlers[i], item, n);
                gave[i] -= n;
                back -= n;
                outcomes[owners[i]] |= DockReturn.HAS_MORE | DockReturn.FULL;
            }
            for (int i = 0; i < size; i++) {
                if (groups[i] == group && gave[i] > 0) {
                    outcomes[owners[i]] |= DockReturn.GAVE;
                    returned += gave[i];
                }
            }
        }
        Arrays.fill(handlers, 0, size, null);
        Arrays.fill(items, 0, groupCount, null);
        size = 0;
        groupCount = 0;
    }

    /**
     * Tira {@code amount} do item de {@code item} do slot, em quantas extrações a máquina pedir (a do Mekanism dá uma
     * pilha por chamada); devolve quanto saiu. Para no que vier vazio; o que sair de outro item volta para a máquina,
     * ou para o chão.
     */
    static int takeCount(ServerLevel level, BlockPos dockPos, IItemHandler handler, int slot, ItemStack item,
            int amount) {
        int total = 0;
        while (total < amount) {
            ItemStack more = handler.extractItem(slot, amount - total, false);
            if (more.isEmpty()) {
                break;
            }
            if (!ItemStack.isSameItemSameComponents(more, item)) {
                keep(level, dockPos, handler, more);
                break;
            }
            total += more.getCount();
        }
        return total;
    }

    /**
     * O {@link #takeCount} que devolve o que saiu numa pilha só (maior que uma pilha, se a rede é o destino): a pilha da
     * primeira extração, se ela já deu tudo, senão uma cópia de {@code offer}.
     */
    static ItemStack take(ServerLevel level, BlockPos dockPos, IItemHandler handler, int slot, ItemStack offer,
            int amount) {
        ItemStack taken = handler.extractItem(slot, amount, false);
        if (taken.isEmpty()) {
            return taken;
        }
        if (!ItemStack.isSameItemSameComponents(taken, offer)) {
            keep(level, dockPos, handler, taken);
            return ItemStack.EMPTY;
        }
        if (taken.getCount() >= amount) {
            return taken;
        }
        int total = taken.getCount() + takeCount(level, dockPos, handler, slot, offer, amount - taken.getCount());
        return offer.copyWithCount(total);
    }

    /** {@code count} do item de {@code item} que o destino recusou: de volta na máquina, e o que não voltar, no chão. */
    static void keep(ServerLevel level, BlockPos dockPos, IItemHandler machine, ItemStack item, int count) {
        // Em pilhas do tamanho do item: o que volta da rede pode ser maior que uma pilha.
        int max = item.getMaxStackSize();
        for (; count > 0; count -= max) {
            ItemStack left = ItemHandlerHelper.insertItemStacked(machine, item.copyWithCount(Math.min(count, max)),
                    false);
            if (!left.isEmpty()) {
                Containers.dropItemStack(level, dockPos.getX(), dockPos.getY(), dockPos.getZ(), left);
            }
        }
    }

    /** O {@link #keep(ServerLevel, BlockPos, IItemHandler, ItemStack, int)} de uma pilha inteira. */
    static void keep(ServerLevel level, BlockPos dockPos, IItemHandler machine, ItemStack rest) {
        keep(level, dockPos, machine, rest, rest.getCount());
    }
}
