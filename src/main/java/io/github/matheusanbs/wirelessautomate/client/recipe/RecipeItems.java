package io.github.matheusanbs.wirelessautomate.client.recipe;

import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorSnapshot.Row;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Itens e tags pelo id: o item de um slot, o de referência de uma tag, as tags de um item e o resultado. */
final class RecipeItems {
    private RecipeItems() {
    }

    static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
    }

    /** O primeiro item da tag (o de referência, que dá o ícone), ou ar. */
    static Item tagReference(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return Items.AIR;
        }
        Optional<HolderSet.Named<Item>> tag = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl));
        return tag.flatMap(t -> t.stream().findFirst()).map(Holder::value).orElse(Items.AIR);
    }

    /** As tags do item, em ordem alfabética do id. */
    static List<String> tagsOf(Item item) {
        if (item == Items.AIR) {
            return List.of();
        }
        return new ItemStack(item).getTags().map(t -> t.location().toString()).sorted().toList();
    }

    static Component itemName(Item item) {
        return new ItemStack(item).getHoverName();
    }

    static ItemStack resultStack(Row row) {
        return new ItemStack(BuiltInRegistries.ITEM.get(row.resultItem()));
    }
}
