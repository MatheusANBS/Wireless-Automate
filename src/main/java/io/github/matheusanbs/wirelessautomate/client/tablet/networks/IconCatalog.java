package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Os itens do registro que a busca da grade de ícones percorre, com o id e o nome já dobrados ({@link IconSearch#fold}).
 * A lista é montada na primeira consulta e vive enquanto a janela está aberta: as consultas seguintes só comparam
 * textos, sem ler o registro nem traduzir nomes de novo.
 */
final class IconCatalog {
    private Item @Nullable [] items;
    private String[] ids = new String[0];
    private String[] names = new String[0];

    /** Os itens que casam com {@code query}, na ordem do {@link IconSearch#order}. */
    Item[] find(String query) {
        Item[] all = items != null ? items : build();
        int[] order = IconSearch.order(query, ids, names, IconSearch.MAX_RESULTS);
        Item[] out = new Item[order.length];
        for (int i = 0; i < order.length; i++) {
            out[i] = all[order[i]];
        }
        return out;
    }

    private Item[] build() {
        List<Item> list = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                list.add(item);
            }
        }
        Item[] all = list.toArray(new Item[0]);
        ids = new String[all.length];
        names = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            ids[i] = IconSearch.fold(BuiltInRegistries.ITEM.getKey(all[i]).toString());
            names[i] = IconSearch.fold(all[i].getDescription().getString());
        }
        items = all;
        return all;
    }
}
