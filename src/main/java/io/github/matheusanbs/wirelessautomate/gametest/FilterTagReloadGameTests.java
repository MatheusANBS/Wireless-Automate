package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterTags;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * As tags dos filtros depois de uma recarga: a expansão é compartilhada entre os filtros com as mesmas tags e nunca
 * fica velha. O teste troca o conteúdo de uma tag só dele no registro de itens (como o {@code /reload} faz, pelo
 * {@code bindTags}, com todas as outras tags iguais) e devolve no fim; fica num lote só dele.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class FilterTagReloadGameTests {
    private static final ResourceLocation TEST_TAG = ResourceLocation.fromNamespaceAndPath(WirelessAutomate.MODID,
            "gametest_reload");

    @GameTest(template = "empty", batch = "wirelessautomate_tags", timeoutTicks = 100)
    public static void filtersFollowTagReload(GameTestHelper helper) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, TEST_TAG);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        ItemStack emerald = new ItemStack(Items.EMERALD);
        ItemStack stone = new ItemStack(Items.STONE);
        Filter onlyTag = whitelist(new FilterEntry.TagEntry(TEST_TAG, 0));
        Filter itemThenTag = whitelist(new FilterEntry.ItemEntry(stone, 0), new FilterEntry.TagEntry(TEST_TAG, 7));
        try {
            bind(helper, tag, List.<Holder<Item>>of(Items.DIAMOND.builtInRegistryHolder()));
            helper.assertTrue(onlyTag.testItem(diamond) && !onlyTag.testItem(emerald), "tag com o diamante");
            helper.assertTrue(itemThenTag.testItem(diamond) && itemThenTag.testItem(stone)
                    && !itemThenTag.testItem(emerald), "item e tag com o diamante");
            helper.assertTrue(itemThenTag.itemStock(diamond) == 7, "estoque da tag no índice dela");
            Object before = onlyTag.itemTagExpansionForTests();
            helper.assertTrue(before != null && before == itemThenTag.itemTagExpansionForTests(),
                    "os dois filtros com a mesma tag não compartilham a expansão");

            // Um filtro compilado antes da recarga e consultado só depois.
            Filter compiledBefore = whitelist(new FilterEntry.TagEntry(TEST_TAG, 0));
            helper.assertTrue(compiledBefore.testItem(diamond), "tag com o diamante, no filtro novo");
            bind(helper, tag, List.<Holder<Item>>of(Items.EMERALD.builtInRegistryHolder()));
            helper.assertTrue(compiledBefore.testItem(emerald) && !compiledBefore.testItem(diamond),
                    "o filtro usou a expansão de antes da recarga");
            helper.assertTrue(onlyTag.testItem(emerald) && !onlyTag.testItem(diamond), "tag com a esmeralda");
            helper.assertTrue(itemThenTag.testItem(emerald) && itemThenTag.testItem(stone)
                    && !itemThenTag.testItem(diamond), "item e tag com a esmeralda");
            Object after = onlyTag.itemTagExpansionForTests();
            helper.assertTrue(after != before, "a expansão não foi trocada na recarga");
            helper.assertTrue(after == itemThenTag.itemTagExpansionForTests()
                    && after == compiledBefore.itemTagExpansionForTests(), "a expansão nova não é compartilhada");
        } finally {
            bind(helper, tag, List.of());
        }
        helper.assertTrue(!onlyTag.testItem(emerald) && !onlyTag.testItem(diamond), "a tag vazia ainda casou");
        helper.succeed();
    }

    private static Filter whitelist(FilterEntry... entries) {
        return new Filter(Filter.ListMode.WHITELIST, false, List.of(entries));
    }

    /** Põe {@code holders} na tag do teste, com as outras tags iguais, e avisa a recarga como o jogo faz. */
    private static void bind(GameTestHelper helper, TagKey<Item> tag, List<Holder<Item>> holders) {
        Registry<Item> registry = BuiltInRegistries.ITEM;
        Map<TagKey<Item>, List<Holder<Item>>> tags = new HashMap<>();
        registry.getTags().forEach(pair -> tags.put(pair.getFirst(), pair.getSecond().stream().toList()));
        tags.put(tag, holders);
        registry.bindTags(tags);
        FilterTags.onTagsUpdated(new TagsUpdatedEvent(helper.getLevel().registryAccess(), false, false));
    }
}
