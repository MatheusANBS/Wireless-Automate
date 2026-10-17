package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.storage.BulkItems;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * A devolução do {@link ItemTransfer}: o que um destino recusou depois de aceitar na simulação volta para a
 * origem (o mesmo slot, ou o Baú do mod); o que não couber vai para os destinos da passada cujo filtro deixe
 * (o resgate) e só o resto cai no mundo, com o caso no log. Só roda quando um destino mente, fora do caminho
 * comum de uma entrega.
 */
final class ItemGiveBack {
    /**
     * Devolve ao Baú de origem o que o destino recusou depois de aceitar na simulação. Cabe sempre
     * (acabou de sair de lá); se a capacidade da config caiu nesse meio-tempo, o resto cai no mundo.
     */
    static void giveBackBulk(Port source, BulkItems bulk, ItemStack key, long amount) {
        long back = bulk.insert(key, amount, false);
        long rest = amount - back;
        if (rest <= 0) {
            return;
        }
        Level level = source.node.getLevel();
        WirelessAutomate.LOGGER.warn("Destino recusou {} x {} e o Baú de origem {} não aceitou de volta; soltando no mundo",
                rest, key, source);
        if (level == null) {
            return;
        }
        int stackSize = Math.max(1, key.getMaxStackSize());
        while (rest > 0) {
            int drop = (int) Math.min(rest, stackSize);
            Block.popResource(level, source.node.getBlockPos(), key.copyWithCount(drop));
            rest -= drop;
        }
    }

    /**
     * Devolve ao mesmo slot da origem o que o destino recusou depois de aceitar na simulação. Outro
     * slot da origem não serve: pode ser a entrada da máquina (a pedra tirada da saída de um forno
     * voltava pela entrada e virava pedra lisa), e o filtro da face nem é consultado. O que não couber
     * vai para os destinos da passada cujo filtro aceite, e só o resto cai no mundo.
     */
    static void giveBack(Port source, IItemHandler handler, int slot, ItemStack leftover, List<Port> pass) {
        ItemStack rest = handler.insertItem(slot, leftover, false);
        for (int i = 0, n = pass.size(); i < n && !rest.isEmpty(); i++) {
            rest = rescue(pass.get(i), rest);
        }
        if (!rest.isEmpty()) {
            Level level = source.node.getLevel();
            WirelessAutomate.LOGGER.warn("Destino recusou {} depois de aceitar na simulação e nem a origem {} nem outro destino aceitaram; soltando no mundo",
                    rest, source);
            if (level != null) {
                Block.popResource(level, source.node.getBlockPos(), rest);
            }
        }
    }

    /**
     * Insere a sobra na máquina da porta de destino (a presa ou a remota), se o filtro dele deixar (sem
     * estoque: é só para não perdê-la). Uma remota descarregada não responde, e a sobra segue adiante.
     */
    private static ItemStack rescue(Port destination, ItemStack rest) {
        if (!destination.filter.testItem(rest)) {
            return rest;
        }
        BulkItems bulk = destination.machine.bulkItems(destination.face);
        if (bulk != null) {
            long in = bulk.insert(rest, rest.getCount(), false);
            return rest.copyWithCount(rest.getCount() - (int) in);
        }
        IItemHandler target = destination.machine.items(destination.face);
        return target == null ? rest : ItemHandlerHelper.insertItemStacked(target, rest, false);
    }

    private ItemGiveBack() {
    }
}
