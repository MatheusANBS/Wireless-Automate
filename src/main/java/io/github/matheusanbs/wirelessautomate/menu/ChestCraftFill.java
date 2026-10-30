package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.item.ChestUpgradeItem;
import io.github.matheusanbs.wirelessautomate.packet.ChestCraftFillPayload;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestCompacting;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestUpgrades;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * O "+" do JEI na Mesa do Baú, no servidor: devolve a grade ao Baú e monta a receita pedida, pegando cada ingrediente
 * do Baú (o item exato, depois descendo uma forma compactada, com o upgrade de Compactação) e por último do
 * inventário do jogador. O cliente só manda as alternativas de cada slot; quantidades e origem são daqui. Tudo o que
 * sai do Baú ou do inventário entra na grade: nada se perde nem se cria.
 */
final class ChestCraftFill {
    private ChestCraftFill() {
    }

    /**
     * Valida e aplica o pacote. Recusa (sem mudar nada) sem o menu do Baú aberto com esse {@code containerId}, fora de
     * alcance, sem a Mesa, ou com um número de listas ou de alternativas fora do combinado.
     *
     * @return se foi aceito
     */
    static boolean handle(@Nullable ServerPlayer player, ChestCraftFillPayload payload) {
        if (player == null || !(player.containerMenu instanceof StorageListMenu<?> open)
                || open.containerId != payload.containerId() || !open.stillValid(player)
                || open.upgradeSlots() == null) {
            return false;
        }
        return open.upgradeSlots().fill(player, payload.slots(), payload.maxTransfer());
    }

    /** A montagem em si; {@code alternatives} nunca é confiada além da forma (a contagem das pilhas é ignorada). */
    static boolean fill(ServerPlayer player, StorageChestBlockEntity chest, List<List<ItemStack>> alternatives,
            boolean max) {
        if (alternatives.size() != ChestUpgrades.GRID_SIZE || !chest.upgrades().has(ChestUpgradeItem.Kind.CRAFTING)) {
            return false;
        }
        for (List<ItemStack> list : alternatives) {
            if (list.size() > ChestCraftFillPayload.MAX_ALTERNATIVES) {
                return false;
            }
        }
        chest.returnGrid(player);
        Source source = new Source(chest, player.getInventory());
        ItemStack[] keys = new ItemStack[ChestUpgrades.GRID_SIZE];
        for (int slot = 0; slot < keys.length; slot++) {
            keys[slot] = choose(source, alternatives.get(slot), keys);
        }
        // As quantidades saem antes de tirar qualquer coisa, para os slots da mesma chave ficarem iguais.
        int[] amounts = new int[keys.length];
        for (int slot = 0; slot < keys.length; slot++) {
            amounts[slot] = keys[slot] == null ? 0 : max ? perSlot(source, keys[slot], keys) : 1;
        }
        NonNullList<ItemStack> grid = chest.upgrades().grid();
        boolean placed = false;
        for (int slot = 0; slot < keys.length; slot++) {
            if (amounts[slot] > 0) {
                ItemStack taken = source.take(keys[slot], amounts[slot]);
                if (!taken.isEmpty()) {
                    grid.set(slot, taken);
                    placed = true;
                }
            }
        }
        if (placed) {
            chest.upgrades().changed();
        }
        return true;
    }

    /** A primeira alternativa que ainda tem 1 a mais do que já está reservado para os slots de antes. */
    private static @Nullable ItemStack choose(Source source, List<ItemStack> alternatives, ItemStack[] keys) {
        for (ItemStack alternative : alternatives) {
            if (alternative.isEmpty()) {
                continue;
            }
            ItemStack key = alternative.copyWithCount(1);
            int need = reserved(keys, key) + 1;
            if (source.available(key, need) >= need) {
                return key;
            }
        }
        return null;
    }

    /** Quantos slots já escolheram esta chave. */
    private static int reserved(ItemStack[] keys, ItemStack key) {
        int count = 0;
        for (ItemStack other : keys) {
            if (other != null && ItemStack.isSameItemSameComponents(other, key)) {
                count++;
            }
        }
        return count;
    }

    /** Com o Shift: o menor entre a pilha cheia e o que há dividido pelos slots da mesma chave (ao menos 1). */
    private static int perSlot(Source source, ItemStack key, ItemStack[] keys) {
        int users = reserved(keys, key);
        int stack = key.getMaxStackSize();
        long have = source.available(key, (long) users * stack);
        return (int) Math.max(1, Math.min(stack, have / users));
    }

    /** O Baú e o inventário do jogador como uma fonte só, nessa ordem. */
    private static final class Source {
        private final ItemStorage storage;
        private final ChestCompacting compacting;
        private final boolean unpack;
        private final Inventory inventory;

        Source(StorageChestBlockEntity chest, Inventory inventory) {
            this.storage = chest.storage();
            this.compacting = chest.compacting();
            this.unpack = chest.upgrades().has(ChestUpgradeItem.Kind.COMPACTING);
            this.inventory = inventory;
        }

        /** Quanto há da chave (Baú mais inventário), descendo formas do Baú enquanto faltar até {@code need}. */
        long available(ItemStack key, long need) {
            long inChest = storage.count(key);
            if (inChest < need && unpack) {
                compacting.unpackFor(key, need);
                inChest = storage.count(key);
            }
            return inChest + inInventory(key);
        }

        private int inInventory(ItemStack key) {
            int total = 0;
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                ItemStack there = inventory.getItem(i);
                if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, key)) {
                    total += there.getCount();
                }
            }
            return total;
        }

        /** Tira até {@code want} da chave: do Baú primeiro, o resto do inventário. */
        ItemStack take(ItemStack key, int want) {
            if (storage.count(key) < want && unpack) {
                compacting.unpackFor(key, want);
            }
            int got = (int) storage.extract(key, want, false);
            for (int i = 0; i < Inventory.INVENTORY_SIZE && got < want; i++) {
                ItemStack there = inventory.getItem(i);
                if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, key)) {
                    int moving = Math.min(want - got, there.getCount());
                    there.shrink(moving);
                    got += moving;
                    inventory.setChanged();
                }
            }
            return got <= 0 ? ItemStack.EMPTY : key.copyWithCount(got);
        }
    }
}
