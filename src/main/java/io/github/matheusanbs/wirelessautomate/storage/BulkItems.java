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
 * ({@code index}) são os slots: um tipo que zera fica com 0 no lugar até o fim do tick e então sai
 * da lista, e as posições seguintes andam.
 */
public interface BulkItems {
    BlockCapability<BulkItems, @Nullable Direction> BLOCK =
            BlockCapability.createSided(WirelessAutomate.id("bulk_items"), BulkItems.class);

    /** O total guardado, de todos os tipos. */
    long total();

    /** A capacidade em itens; {@code <= 0} é sem limite. */
    long capacity();

    /** Quantos slots há, contando os que zeraram neste tick (com 0). */
    int slots();

    /** A chave do tipo na posição (pilha de 1, não alterar). */
    ItemStack key(int index);

    /** Quanto há do tipo na posição. */
    long count(int index);

    /** Quanto há do item com os mesmos componentes da chave. */
    long count(ItemStack key);

    /** Quanto há do item, com quaisquer componentes (O(1) no Baú: a soma por item é mantida). */
    long countItem(ItemStack key);

    /**
     * Quanto conta para o limite de estoque da chave: o mesmo item com os mesmos componentes ou, sem
     * {@code components}, com quaisquer componentes. Num Baú com a Compactação, a cadeia inteira na unidade da chave.
     */
    default long stockCount(ItemStack key, boolean components) {
        return components ? count(key) : countItem(key);
    }

    /** Guarda até {@code amount} de {@code key}; devolve quanto coube. */
    long insert(ItemStack key, long amount, boolean simulate);

    /** Tira até {@code amount} de {@code key}; devolve quanto saiu. */
    long extract(ItemStack key, long amount, boolean simulate);
}
