package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * Itens por tipo e quantidade {@code long}, sem o teto de uma pilha por chamada do
 * {@link net.neoforged.neoforge.items.IItemHandler}. É a capability que o roteador procura antes da
 * de itens do NeoForge: entre dois baús do mod, mover milhões de um tipo é uma chamada só.
 *
 * <p>As chaves são pilhas de 1 (item e componentes); quem chama não pode alterá-las. As posições
 * ({@code index}) valem até a próxima mudança: um tipo que zera sai da lista.
 */
public interface BulkItems {
    BlockCapability<BulkItems, @Nullable Direction> BLOCK =
            BlockCapability.createSided(WirelessAutomate.id("bulk_items"), BulkItems.class);

    /** Quantos tipos há guardados. */
    int types();

    /** A chave do tipo na posição (pilha de 1, não alterar). */
    ItemStack key(int index);

    /** Quanto há do tipo na posição. */
    long count(int index);

    /** Quanto há do item com os mesmos componentes da chave. */
    long count(ItemStack key);

    /** Quanto há do item, com quaisquer componentes (O(tipos)). */
    long countItem(ItemStack key);

    /** Guarda até {@code amount} de {@code key}; devolve quanto coube. */
    long insert(ItemStack key, long amount, boolean simulate);

    /** Tira até {@code amount} de {@code key}; devolve quanto saiu. */
    long extract(ItemStack key, long amount, boolean simulate);
}
