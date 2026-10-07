package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Move itens de uma origem para os destinos dela, numa visita do {@link NetworkManager}.
 *
 * <p>Para não duplicar nem perder, cada entrega segue: simula a extração, simula a inserção no
 * destino, extrai de verdade só o que o destino aceitaria e insere de verdade exatamente o que
 * saiu. Assim nunca se insere algo que não foi tirado. Se o destino mentiu na simulação e devolve
 * sobra, ela volta para a origem (mesmo slot, depois qualquer slot); se nem a origem aceitar
 * (slot só de saída, por exemplo), a sobra cai no mundo na posição do roteador e o caso vai para o
 * log. Se a extração real der menos que o simulado, só o que saiu é entregue e a visita para.
 */
final class ItemTransfer {
    /** Slots examinados por visita: inventário grande continua do cursor no tick seguinte. */
    static final int MAX_SLOTS_PER_VISIT = 128;
    /** Slots com item que tentam entregar por visita, para uma origem não comer o orçamento sozinha. */
    static final int MAX_ATTEMPTS_PER_VISIT = 32;

    static void move(Port source, long now) {
        IItemHandler handler = source.node.items(source.face);
        RoundRobinOrder<Port> order = source.order;
        if (handler == null || order == null) {
            // Máquina sumiu ou chunk descarregado: a invalidação do cache refaz as rotas e acorda.
            source.sourceBackoff.sleep(now);
            return;
        }
        long tokens = source.limiter.available(now);
        if (tokens <= 0) {
            return;
        }
        List<Port> pass = order.pass();
        if (!NetworkManager.hasAwakeDestination(pass, now)) {
            source.sourceBackoff.sleep(now);
            return;
        }
        int slots = handler.getSlots();
        if (slots <= 0) {
            source.sourceBackoff.sleep(now);
            return;
        }
        int slot = source.slotCursor < slots ? source.slotCursor : 0;
        int limit = Math.min(slots, MAX_SLOTS_PER_VISIT);
        int scanned = 0;
        int attempts = 0;
        long moved = 0;
        boolean destinationsAsleep = false;
        while (scanned < limit) {
            int current = slot;
            scanned++;
            slot = slot + 1 == slots ? 0 : slot + 1;
            if (handler.getStackInSlot(current).isEmpty()) {
                continue;
            }
            int amount = moveSlot(source, handler, current, (int) Math.min(tokens, Integer.MAX_VALUE), order, pass, now);
            if (amount > 0) {
                tokens -= amount;
                moved += amount;
                if (tokens <= 0) {
                    // Sem saldo: o slot pode ter mais, continua dele no próximo tick.
                    slot = current;
                    break;
                }
            }
            if (++attempts >= MAX_ATTEMPTS_PER_VISIT) {
                break;
            }
            if (!NetworkManager.hasAwakeDestination(pass, now)) {
                destinationsAsleep = true;
                break;
            }
        }
        source.slotCursor = slot;
        if (moved > 0) {
            source.idleSlots = 0;
            source.limiter.consume(moved);
            source.sourceBackoff.wake();
            return;
        }
        source.idleSlots += scanned;
        if (destinationsAsleep || source.idleSlots >= slots) {
            // Uma volta inteira sem mover (mesmo dividida em visitas), ou todos os destinos
            // recusaram: dorme até alguém mudar.
            source.idleSlots = 0;
            source.sourceBackoff.sleep(now);
        }
    }

    /** Entrega o que der do slot, na ordem da passada. Devolve quanto foi entregue. */
    private static int moveSlot(Port source, IItemHandler handler, int slot, int max,
            RoundRobinOrder<Port> order, List<Port> pass, long now) {
        ItemStack offered = handler.extractItem(slot, max, true);
        if (offered.isEmpty()) {
            return 0;
        }
        int remaining = offered.getCount();
        int moved = 0;
        for (int i = 0, n = pass.size(); i < n && remaining > 0; i++) {
            Port destination = pass.get(i);
            if (destination.destinationBackoff.isSleeping(now)) {
                continue;
            }
            IItemHandler target = destination.node.items(destination.face);
            if (target == null) {
                continue;
            }
            ItemStack probe = remaining == offered.getCount() ? offered : offered.copyWithCount(remaining);
            int accepts = remaining - ItemHandlerHelper.insertItemStacked(target, probe, true).getCount();
            if (accepts <= 0) {
                destination.destinationBackoff.sleep(now);
                continue;
            }
            ItemStack taken = handler.extractItem(slot, accepts, false);
            if (taken.isEmpty()) {
                break;
            }
            int takenCount = taken.getCount();
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(target, taken, false);
            int delivered = takenCount - leftover.getCount();
            if (!leftover.isEmpty()) {
                giveBack(source, handler, slot, leftover);
            }
            if (delivered > 0) {
                destination.destinationBackoff.wake();
                order.delivered(destination);
                if (source.network != null) {
                    source.network.ops++;
                }
                moved += delivered;
                remaining -= delivered;
            } else {
                destination.destinationBackoff.sleep(now);
            }
            if (takenCount < accepts) {
                break;
            }
        }
        return moved;
    }

    private static void giveBack(Port source, IItemHandler handler, int slot, ItemStack leftover) {
        ItemStack rest = handler.insertItem(slot, leftover, false);
        if (!rest.isEmpty()) {
            rest = ItemHandlerHelper.insertItemStacked(handler, rest, false);
        }
        if (!rest.isEmpty()) {
            Level level = source.node.getLevel();
            WirelessAutomate.LOGGER.warn("Destino recusou {} depois de aceitar na simulação e a origem {} não aceitou de volta; soltando no mundo",
                    rest, source);
            if (level != null) {
                Block.popResource(level, source.node.getBlockPos(), rest);
            }
        }
    }

    private ItemTransfer() {
    }
}
