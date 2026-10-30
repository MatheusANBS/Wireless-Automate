package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Uma cadeia de compactação: as formas do menor para o maior (pepita, lingote, bloco). {@code factors[i]} é
 * quantos de {@code forms[i - 1]} fazem 1 de {@code forms[i]} ({@code factors[0]} é 1). {@code keys[i]} é a
 * pilha de uma unidade de {@code forms[i]}, criada na montagem e nunca alterada: serve de chave de contagem
 * sem alocar a cada conversão. O {@code id} só vale dentro de uma geração de {@link CompactingChains}.
 */
public record CompactingChain(int id, Item[] forms, int[] factors, ItemStack[] keys) {
    /**
     * O teto de formas de uma cadeia do índice: o estado do painel não codifica mais que isso por linha, e o índice
     * deixa de fora a cadeia que passa dele ({@link CompactingChains#assemble}). As do vanilla têm 2 ou 3; a maior do AllTheCompressed, 10.
     */
    public static final int MAX_FORMS = 16;

    /** O nível da forma na cadeia (0 é a base), ou -1 se o item não faz parte dela. */
    public int levelOf(Item item) {
        for (int i = 0; i < forms.length; i++) {
            if (forms[i] == item) {
                return i;
            }
        }
        return -1;
    }

    public Item base() {
        return forms[0];
    }
}
