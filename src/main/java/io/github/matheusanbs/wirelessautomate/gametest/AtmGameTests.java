package io.github.matheusanbs.wirelessautomate.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.recipe.TierUpgradeRecipe;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tiers do Allthemodium, num servidor de testes com o Allthemodium e o All The Tweaks na pasta mods
 * ({@code ./gradlew runGameTestServerAllthemodium}, namespace {@value #NAMESPACE}). Os itens dos dois mods
 * são lidos pelo registro, pelo id: nenhum tipo deles aqui.
 */
@GameTestHolder(AtmGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AtmGameTests {

    /** Com o Allthemodium, todas as receitas viajam para o cliente (o servidor dedicado codifica o update_recipes). */
    @GameTest(template = "empty")
    public static void recipesEncodeForNetwork(net.minecraft.gametest.framework.GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (var holder : server.getRecipeManager().getRecipes()) {
            var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
            try {
                net.minecraft.world.item.crafting.RecipeHolder.STREAM_CODEC.encode(buf, holder);
                net.minecraft.world.item.crafting.RecipeHolder.STREAM_CODEC.decode(buf);
            } catch (RuntimeException e) {
                io.github.matheusanbs.wirelessautomate.WirelessAutomate.LOGGER.error("Receita não codifica: {}", holder.id(), e);
                helper.fail("Receita não codifica: " + holder.id() + ": " + e);
                return;
            }
        }
        helper.succeed();
    }
    static final String NAMESPACE = "wirelessautomate_atm";

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos ROUTER = new BlockPos(1, 2, 1);
    private static final BlockPos STORAGE = new BlockPos(0, 1, 0);

    private AtmGameTests() {
    }

    /** Com o mod, a escada tem os oito degraus, e o roteador e o Baú sobem um de cada vez. */
    @GameTest(template = "empty")
    public static void fullLadderWithAllthemodium(GameTestHelper helper) {
        RouterTier[] tiers = RouterTier.values();
        for (int i = 0; i < tiers.length - 1; i++) {
            helper.assertTrue(tiers[i].loaded(), "tier não carregado: " + tiers[i]);
            helper.assertValueEqual(tiers[i].next(), tiers[i + 1], "depois de " + tiers[i]);
            helper.assertValueEqual(tiers[i + 1].previous(), tiers[i], "antes de " + tiers[i + 1]);
        }
        helper.setBlock(MACHINE, Blocks.FURNACE);
        helper.setBlock(ROUTER, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP)
                .setValue(RouterBlock.TIER, RouterTier.EMERALD));
        helper.setBlock(STORAGE, ModBlocks.STORAGE_CHEST.get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.EMERALD));
        BlockPos router = helper.absolutePos(ROUTER);
        BlockPos chest = helper.absolutePos(STORAGE);
        // Um segundo roteador Esmeralda pula os tiers do ATM direto para o Ultimate.
        BlockPos machine2 = new BlockPos(2, 1, 2);
        BlockPos router2Rel = new BlockPos(2, 2, 2);
        helper.setBlock(machine2, Blocks.FURNACE);
        helper.setBlock(router2Rel, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP)
                .setValue(RouterBlock.TIER, RouterTier.EMERALD));
        helper.assertTrue(RouterBlock.tryUpgrade(helper.getLevel(), helper.absolutePos(router2Rel), RouterTier.ULTIMATE),
                "não pulou os tiers do ATM");
        helper.assertBlockProperty(router2Rel, RouterBlock.TIER, RouterTier.ULTIMATE);
        for (RouterTier target : List.of(RouterTier.ALLTHEMODIUM, RouterTier.VIBRANIUM, RouterTier.UNOBTAINIUM,
                RouterTier.ULTIMATE)) {
            helper.assertTrue(RouterBlock.tryUpgrade(helper.getLevel(), router, target), "roteador não subiu: " + target);
            helper.assertTrue(StorageBlock.tryUpgrade(helper.getLevel(), chest, target), "Baú não subiu: " + target);
        }
        helper.assertBlockProperty(ROUTER, RouterBlock.TIER, RouterTier.ULTIMATE);
        helper.assertBlockProperty(STORAGE, RouterBlock.TIER, RouterTier.ULTIMATE);
        // Um cartão com origem Esmeralda → Ultimate só vale num bloco Esmeralda, não num Allthemodium.
        BlockPos machine3 = new BlockPos(0, 1, 2);
        BlockPos router3 = new BlockPos(0, 2, 2);
        helper.setBlock(machine3, Blocks.FURNACE);
        helper.setBlock(router3, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP)
                .setValue(RouterBlock.TIER, RouterTier.ALLTHEMODIUM));
        helper.assertFalse(RouterBlock.tryUpgrade(helper.getLevel(), helper.absolutePos(router3), RouterTier.ULTIMATE,
                RouterTier.EMERALD), "o cartão Esmeralda → Ultimate subiu um roteador Allthemodium");
        helper.assertBlockProperty(router3, RouterBlock.TIER, RouterTier.ALLTHEMODIUM);
        helper.succeed();
    }

    /**
     * Os anéis do ATM carregam e casam com os materiais do mod: com Redstone no centro, o cartão de um degrau com a
     * origem da escada de oito degraus (o Ultimate do ATM10 pelo {@code tier_core_ultimate_atm_star}, com o fragmento
     * de ATM Star); o cartão que pula e a receita direta também. As outras duas versões do Ultimate não carregam, e
     * o custo pela bancada e pelo cartão é o mesmo em toda a escada.
     */
    @GameTest(template = "empty")
    public static void allthemodiumRecipes(GameTestHelper helper) {
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        Item atmIngot = item(helper, "allthemodium:allthemodium_ingot");
        Item atmBlock = item(helper, "allthemodium:allthemodium_block");
        TierUpgradeGameTests.assertCard(helper, TierUpgradeGameTests.craft(helper,
                grid(redstone, atmIngot, atmBlock, atmIngot, atmIngot, atmIngot, atmIngot, atmBlock, atmIngot),
                WirelessAutomate.id("tier_core_allthemodium")), RouterTier.EMERALD, RouterTier.ALLTHEMODIUM,
                "Allthemodium");

        Item vib = item(helper, "allthemodium:vibranium_ingot");
        Item vibAlloy = item(helper, "allthemodium:vibranium_allthemodium_alloy_ingot");
        Item vibBlock = item(helper, "allthemodium:vibranium_block");
        TierUpgradeGameTests.assertCard(helper, TierUpgradeGameTests.craft(helper,
                grid(redstone, vib, vibAlloy, vib, vibBlock, vibBlock, vib, vibAlloy, vib),
                WirelessAutomate.id("tier_core_vibranium")), RouterTier.ALLTHEMODIUM, RouterTier.VIBRANIUM,
                "Vibranium");

        Item unob = item(helper, "allthemodium:unobtainium_ingot");
        Item unobAlloy = item(helper, "allthemodium:unobtainium_vibranium_alloy_ingot");
        Item unobBlock = item(helper, "allthemodium:unobtainium_block");
        TierUpgradeGameTests.assertCard(helper, TierUpgradeGameTests.craft(helper,
                grid(redstone, unob, unobAlloy, unob, unobBlock, unobBlock, unob, unobAlloy, unob),
                WirelessAutomate.id("tier_core_unobtainium")), RouterTier.VIBRANIUM, RouterTier.UNOBTAINIUM,
                "Unobtainium");

        Item shard = item(helper, "allthetweaks:atm_star_shard");
        Item alloyBlock = item(helper, "allthemodium:unobtainium_allthemodium_alloy_block");
        TierUpgradeGameTests.assertCard(helper, TierUpgradeGameTests.craft(helper,
                grid(redstone, shard, Items.DRAGON_EGG, shard, alloyBlock, alloyBlock, shard, alloyBlock, shard),
                WirelessAutomate.id("tier_core_ultimate_atm_star")), RouterTier.UNOBTAINIUM, RouterTier.ULTIMATE,
                "Ultimate do ATM10");

        // Cartão que pula: Esmeralda → Allthemodium no anel do Vibranium = Esmeralda → Vibranium.
        TierUpgradeGameTests.assertCard(helper, TierUpgradeGameTests.craft(helper,
                grid(TierCoreItem.card(RouterTier.ALLTHEMODIUM, RouterTier.EMERALD),
                        vib, vibAlloy, vib, vibBlock, vibBlock, vib, vibAlloy, vib),
                WirelessAutomate.id("tier_core_vibranium")), RouterTier.EMERALD, RouterTier.VIBRANIUM,
                "cartão que pula");

        // Receita direta: roteador Esmeralda no anel do Allthemodium sobe; no do Ultimate, não (três degraus no meio).
        ItemStack up = TierUpgradeGameTests.craft(helper,
                grid(TierUpgradeGameTests.routerItem(RouterTier.EMERALD),
                        atmIngot, atmBlock, atmIngot, atmIngot, atmIngot, atmIngot, atmBlock, atmIngot),
                WirelessAutomate.id("tier_core_allthemodium"));
        helper.assertTrue(RouterBlockItem.tierOf(up) == RouterTier.ALLTHEMODIUM, "roteador Esmeralda: " + up);
        helper.assertTrue(find(helper, grid(TierUpgradeGameTests.routerItem(RouterTier.EMERALD),
                shard, Items.DRAGON_EGG, shard, alloyBlock, alloyBlock, shard, alloyBlock, shard)).isEmpty(),
                "o anel do Ultimate subiu um roteador Esmeralda");

        ultimateWithoutTweaks(helper, redstone);
        for (String id : List.of("tier_core_ultimate", "tier_core_ultimate_atm")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(WirelessAutomate.id(id)).isEmpty(),
                    "outra versão do Ultimate carregou: " + id);
        }
        // Com o ATM, o anel vanilla do Ultimate não vale, nem com Redstone nem com um roteador no centro.
        Item echo = Items.ECHO_SHARD;
        Item netherite = Items.NETHERITE_BLOCK;
        for (ItemStack center : List.of(redstone, TierUpgradeGameTests.routerItem(RouterTier.UNOBTAINIUM),
                TierUpgradeGameTests.routerItem(RouterTier.EMERALD))) {
            helper.assertTrue(find(helper, grid(center, echo, Items.DRAGON_EGG, echo, netherite, netherite,
                    echo, netherite, echo)).isEmpty(), "Ultimate vanilla com o ATM: " + center);
        }
        TierUpgradeGameTests.sameRing(helper);
        TierUpgradeGameTests.sameCost(helper);
        helper.succeed();
    }

    /**
     * O Ultimate com o Allthemodium e sem o All The Tweaks ({@code tier_core_ultimate_atm}) não carrega aqui, porque
     * esta run tem o All The Tweaks: o anel é lido do recurso do jar e casado direto. Quatro Nether Stars nos cantos,
     * o ovo do dragão em cima e três blocos de liga Unobtainium-Vibranium; o anel antigo, de fragmentos de eco e
     * netherite, não casa.
     */
    private static void ultimateWithoutTweaks(GameTestHelper helper, ItemStack redstone) {
        var server = helper.getLevel().getServer();
        ResourceLocation file = WirelessAutomate.id("recipe/tier_core_ultimate_atm.json");
        Resource resource = server.getResourceManager().getResource(file)
                .orElseThrow(() -> new GameTestAssertException("o jar deve ter o recurso " + file));
        JsonObject json;
        try (Reader reader = resource.openAsReader()) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new GameTestAssertException("erro lendo o recurso: " + e);
        }
        json.remove("neoforge:conditions");
        Recipe<?> recipe = Recipe.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), json)
                .getOrThrow(msg -> new GameTestAssertException("o anel não decodifica: " + msg));
        if (!(recipe instanceof TierUpgradeRecipe ring)) {
            throw new GameTestAssertException("o anel não é tier_upgrade: " + recipe);
        }
        Item star = Items.NETHER_STAR;
        Item alloyBlock = item(helper, "allthemodium:unobtainium_vibranium_alloy_block");
        CraftingInput novo = grid(redstone, star, Items.DRAGON_EGG, star, alloyBlock, alloyBlock, star, alloyBlock, star);
        helper.assertTrue(ring.matches(novo, helper.getLevel()), "o anel novo do Ultimate não casa");
        TierUpgradeGameTests.assertCard(helper, ring.assemble(novo, server.registryAccess()), RouterTier.UNOBTAINIUM,
                RouterTier.ULTIMATE, "Ultimate com o ATM, sem o All The Tweaks");
        Item echo = Items.ECHO_SHARD;
        Item netherite = Items.NETHERITE_BLOCK;
        helper.assertFalse(ring.matches(grid(redstone, echo, Items.DRAGON_EGG, echo, netherite, netherite, echo,
                netherite, echo), helper.getLevel()), "o anel antigo do Ultimate ainda casa");
    }

    /**
     * Os Cristais Afinados do Allthemodium carregam e casam com os itens do mod; a receita do Ultimate sem o
     * Allthemodium não carrega.
     */
    @GameTest(template = "empty")
    public static void tunedCrystalRecipes(GameTestHelper helper) {
        ItemStack star = new ItemStack(Items.NETHER_STAR);
        ItemStack atm = new ItemStack(item(helper, "allthemodium:allthemodium_ingot"));
        assertTuned(helper, RouterTier.ALLTHEMODIUM, tuned(RouterTier.EMERALD), atm, atm, star);
        ItemStack vib = new ItemStack(item(helper, "allthemodium:vibranium_ingot"));
        ItemStack vibAlloy = new ItemStack(item(helper, "allthemodium:vibranium_allthemodium_alloy_ingot"));
        assertTuned(helper, RouterTier.VIBRANIUM, tuned(RouterTier.ALLTHEMODIUM), vib, vibAlloy, vib);
        ItemStack unob = new ItemStack(item(helper, "allthemodium:unobtainium_ingot"));
        ItemStack unobAlloy = new ItemStack(item(helper, "allthemodium:unobtainium_vibranium_alloy_ingot"));
        assertTuned(helper, RouterTier.UNOBTAINIUM, unob, tuned(RouterTier.VIBRANIUM), unob, unobAlloy);
        assertTuned(helper, RouterTier.ULTIMATE, unobAlloy, star, unobAlloy, tuned(RouterTier.UNOBTAINIUM));
        assertTuned(helper, RouterTier.ULTIMATE, new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get()), unobAlloy, star,
                unobAlloy);
        helper.assertTrue(helper.getLevel().getRecipeManager()
                .byKey(WirelessAutomate.id("tuned_signal_crystal_ultimate_no_atm")).isEmpty(),
                "a receita do Afinado Ultimate sem o ATM carregou com ele");
        helper.succeed();
    }

    /** O Pó Carregado do Allthemodium ao Ultimate: 8 Pós de Ressonância + 1 lingote do tier rendem 8. */
    @GameTest(template = "empty")
    public static void chargedDustRecipes(GameTestHelper helper) {
        assertChargedDust(helper, RouterTier.ALLTHEMODIUM, "allthemodium:allthemodium_ingot");
        assertChargedDust(helper, RouterTier.VIBRANIUM, "allthemodium:vibranium_ingot");
        assertChargedDust(helper, RouterTier.UNOBTAINIUM, "allthemodium:unobtainium_ingot");
        assertChargedDust(helper, RouterTier.ULTIMATE, "allthemodium:unobtainium_vibranium_alloy_ingot");
        helper.assertTrue(helper.getLevel().getRecipeManager()
                .byKey(WirelessAutomate.id("charged_dust_ultimate_no_atm")).isEmpty(),
                "a receita do Pó Carregado Ultimate sem o ATM carregou com ele");
        helper.succeed();
    }

    /** Os Corações do Allthemodium ao Ultimate e a Moldura de Liga; as receitas sem o mod não carregam. */
    @GameTest(template = "empty")
    public static void resonatorHeartRecipes(GameTestHelper helper) {
        Item stab = ModItems.FIELD_STABILIZER.get();
        Item condensed = ModItems.CONDENSED_CORE.get();
        Item star = Items.NETHER_STAR;
        assertHeart(helper, RouterTier.ALLTHEMODIUM, "allthemodium:allthemodium_block", 4, stab, stab, star, star);
        assertHeart(helper, RouterTier.VIBRANIUM, "allthemodium:vibranium_block", 4, condensed, star, star);
        assertHeart(helper, RouterTier.UNOBTAINIUM, "allthemodium:unobtainium_block", 4, condensed, condensed,
                star, star, star);
        assertAlloyFrame(helper);
        Item frame = ModItems.ALLOY_FRAME.get();
        assertHeart(helper, RouterTier.ULTIMATE, "allthemodium:unobtainium_vibranium_alloy_block", 0, condensed,
                condensed, condensed, frame, star, star, star, star, star);
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : List.of("resonator_heart_ultimate_no_atm", "alloy_frame_no_atm")) {
            helper.assertTrue(recipes.byKey(WirelessAutomate.id(id)).isEmpty(), "a receita " + id
                    + " sem o ATM carregou com ele");
        }
        helper.succeed();
    }

    private static void assertHeart(GameTestHelper helper, RouterTier tier, String block, int blocks, Item... rest) {
        List<ItemStack> grade = new ArrayList<>();
        for (int i = 0; i < blocks; i++) {
            grade.add(new ItemStack(item(helper, block)));
        }
        for (Item part : rest) {
            grade.add(new ItemStack(part));
        }
        while (grade.size() < 9) {
            grade.add(ItemStack.EMPTY);
        }
        CraftingInput input = CraftingInput.of(3, 3, grade);
        ItemStack out = find(helper, input).map(holder -> holder.value().assemble(input,
                helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(out.is(ModItems.RESONATOR_HEARTS.get(tier).get()) && out.getCount() == 1,
                "Coração " + tier + ": saiu " + out);
    }

    /** A Moldura de Liga: 8 blocos de liga Unobtainium-Vibranium em volta de 1 Nether Star. */
    private static void assertAlloyFrame(GameTestHelper helper) {
        List<ItemStack> grade = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            grade.add(new ItemStack(item(helper, "allthemodium:unobtainium_vibranium_alloy_block")));
        }
        grade.add(4, new ItemStack(Items.NETHER_STAR));
        CraftingInput input = CraftingInput.of(3, 3, grade);
        ItemStack out = find(helper, input).map(holder -> holder.value().assemble(input,
                helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(out.is(ModItems.ALLOY_FRAME.get()) && out.getCount() == 1, "Moldura de Liga: saiu " + out);
    }

    private static void assertChargedDust(GameTestHelper helper, RouterTier tier, String ingot) {
        List<ItemStack> grade = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            grade.add(new ItemStack(ModItems.RESONANCE_DUST.get()));
        }
        grade.add(4, new ItemStack(item(helper, ingot)));
        CraftingInput input = CraftingInput.of(3, 3, grade);
        ItemStack out = find(helper, input).map(holder -> holder.value().assemble(input,
                helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(out.is(ModItems.CHARGED_DUSTS.get(tier).get()) && out.getCount() == 8,
                "Pó Carregado " + tier + ": saiu " + out);
    }

    private static ItemStack tuned(RouterTier tier) {
        return new ItemStack(ModItems.TUNED_CRYSTALS.get(tier).get());
    }

    private static void assertTuned(GameTestHelper helper, RouterTier tier, ItemStack... items) {
        List<ItemStack> grade = new ArrayList<>(List.of(items));
        while (grade.size() < 9) {
            grade.add(ItemStack.EMPTY);
        }
        CraftingInput input = CraftingInput.of(3, 3, grade);
        ItemStack out = find(helper, input).map(holder -> holder.value().assemble(input,
                helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(ItemStack.isSameItemSameComponents(out, tuned(tier)), "Afinado " + tier + ": saiu " + out);
    }

    private static Item item(GameTestHelper helper, String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            throw new GameTestAssertException("item não existe: " + id);
        }
        return BuiltInRegistries.ITEM.get(key);
    }

    /** Grade 3×3: {@code center} no meio e as oito casas do anel em ordem de leitura, sem o centro. */
    private static CraftingInput grid(ItemStack center, Item... ring) {
        List<ItemStack> items = new ArrayList<>(Arrays.stream(ring).map(ItemStack::new).toList());
        items.add(TierUpgradeRecipe.CENTER, center);
        return CraftingInput.of(3, 3, items);
    }

    private static Optional<RecipeHolder<CraftingRecipe>> find(GameTestHelper helper, CraftingInput input) {
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    }
}
