package io.github.matheusanbs.wirelessautomate.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.recipe.TierUpgradeRecipe;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageContents;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.AABB;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Cartão de Upgrade com origem ({@code upgrade_from}): só vale num bloco daquele tier, no clique e na bancada; o
 * cartão antigo, sem origem, continua subindo de qualquer tier abaixo. Nome e tooltip mostram a origem.
 *
 * <p>As três receitas do anel ({@link TierUpgradeRecipe}, um {@code tier_core_<tier>} por tier): Redstone no centro
 * dá o cartão de um degrau, um cartão com origem um degrau abaixo dá o cartão que pula, e um bloco um degrau abaixo
 * sobe direto, com os dados do item. O custo pelos dois caminhos é o mesmo. Os ajudantes de anel servem também ao
 * {@code AtmGameTests}.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TierUpgradeGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos ROUTER = MACHINE.above();
    private static final String KEY = "item.wirelessautomate.tier_core.";

    /** Roteador preso em cima de uma fornalha, no {@code tier}. */
    private static RouterBlockEntity router(GameTestHelper helper, RouterTier tier) {
        helper.setBlock(MACHINE, Blocks.FURNACE);
        helper.setBlock(ROUTER, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(ROUTER);
    }

    /** Jogador de sobrevivência com {@code card} na mão usa o cartão no bloco em {@code relative}. */
    private static InteractionResult use(GameTestHelper helper, ServerPlayer player, ItemStack card,
            BlockPos relative) {
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        BlockPos pos = helper.absolutePos(relative);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return card.getItem().useOn(context);
    }

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static String name(RouterTier tier) {
        return Component.translatable(tier.translationKey()).getString();
    }

    private static List<String> tooltip(ItemStack card) {
        List<Component> lines = new ArrayList<>();
        card.getItem().appendHoverText(card, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
        return lines.stream().map(Component::getString).toList();
    }

    @GameTest(template = "empty")
    public static void cardWithOriginOnClick(GameTestHelper helper) {
        RouterBlockEntity before = router(helper, RouterTier.BASIC);
        before.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
        ServerPlayer player = survivor(helper);
        ItemStack card = TierCoreItem.card(RouterTier.ELITE, RouterTier.BASIC);
        InteractionResult result = use(helper, player, card, ROUTER);
        helper.assertTrue(result.consumesAction(), "o cartão Básico → Elite não subiu o Básico: " + result);
        helper.assertBlockProperty(ROUTER, RouterBlock.TIER, RouterTier.ELITE);
        helper.assertTrue(card.isEmpty(), "o cartão não foi gasto");
        RouterBlockEntity after = helper.getBlockEntity(ROUTER);
        helper.assertTrue(before == after, "o block entity mudou");
        helper.assertTrue(after.face(ResourceType.ITEM, Direction.NORTH).mode() == PortMode.EXTRACT,
                "a face configurada mudou");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cardWithOriginRefusesOtherTier(GameTestHelper helper) {
        router(helper, RouterTier.ADVANCED);
        ServerPlayer player = survivor(helper);
        ItemStack card = TierCoreItem.card(RouterTier.ELITE, RouterTier.BASIC);
        InteractionResult result = use(helper, player, card, ROUTER);
        helper.assertTrue(result == InteractionResult.FAIL, "resultado: " + result);
        helper.assertBlockProperty(ROUTER, RouterBlock.TIER, RouterTier.ADVANCED);
        helper.assertTrue(card.getCount() == 1, "o cartão recusado foi gasto");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cardWithOriginOnStorage(GameTestHelper helper) {
        BlockPos basic = new BlockPos(0, 1, 0);
        BlockPos advanced = new BlockPos(2, 1, 2);
        helper.setBlock(basic, ModBlocks.STORAGE_CHEST.get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.BASIC));
        helper.setBlock(advanced, ModBlocks.STORAGE_CHEST.get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        StorageChestBlockEntity chest = helper.getBlockEntity(basic);
        chest.storage().insert(new ItemStack(Items.COBBLESTONE), 1_000, false);
        ServerPlayer player = survivor(helper);

        InteractionResult up = use(helper, player, TierCoreItem.card(RouterTier.ELITE, RouterTier.BASIC), basic);
        helper.assertTrue(up.consumesAction(), "não subiu o Baú Básico: " + up);
        helper.assertBlockProperty(basic, RouterBlock.TIER, RouterTier.ELITE);
        StorageChestBlockEntity upgraded = helper.getBlockEntity(basic);
        helper.assertTrue(upgraded.storage().count(new ItemStack(Items.COBBLESTONE)) == 1_000L, "conteúdo perdido");

        ItemStack card = TierCoreItem.card(RouterTier.ELITE, RouterTier.BASIC);
        InteractionResult refused = use(helper, player, card, advanced);
        helper.assertTrue(refused == InteractionResult.FAIL, "Baú Avançado: " + refused);
        helper.assertBlockProperty(advanced, RouterBlock.TIER, RouterTier.ADVANCED);
        helper.assertTrue(card.getCount() == 1, "o cartão recusado foi gasto");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyCardStillJumps(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        router(helper, RouterTier.BASIC);
        helper.assertTrue(use(helper, player, TierCoreItem.card(RouterTier.ELITE, null), ROUTER).consumesAction(),
                "o cartão antigo não subiu o Básico");
        helper.assertBlockProperty(ROUTER, RouterBlock.TIER, RouterTier.ELITE);

        router(helper, RouterTier.ADVANCED);
        helper.assertTrue(use(helper, player, TierCoreItem.card(RouterTier.ELITE, null), ROUTER).consumesAction(),
                "o cartão antigo não subiu o Avançado");
        helper.assertBlockProperty(ROUTER, RouterBlock.TIER, RouterTier.ELITE);

        router(helper, RouterTier.EMERALD);
        ItemStack down = TierCoreItem.card(RouterTier.ELITE, null);
        InteractionResult result = use(helper, player, down, ROUTER);
        helper.assertTrue(result == InteractionResult.PASS, "o cartão antigo desceu: " + result);
        helper.assertBlockProperty(ROUTER, RouterBlock.TIER, RouterTier.EMERALD);
        helper.assertTrue(down.getCount() == 1, "o cartão foi gasto");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cardNameAndTooltip(GameTestHelper helper) {
        ItemStack card = TierCoreItem.card(RouterTier.ELITE, RouterTier.BASIC);
        helper.assertTrue(Objects.equals(TierCoreItem.origin(card), RouterTier.BASIC), "origem");
        String hover = card.getHoverName().getString();
        helper.assertTrue(hover.contains(name(RouterTier.BASIC)) && hover.contains(name(RouterTier.ELITE)),
                "nome: " + hover);

        List<String> texts = tooltip(card);
        String from = Component.translatable(KEY + "upgrades_from",
                Component.translatable(RouterTier.BASIC.translationKey()),
                Component.translatable(RouterTier.ELITE.translationKey())).getString();
        String items = Component.translatable(KEY + "items", Component.translatable(KEY + "change",
                TierCoreItem.grouped(32), TierCoreItem.grouped(2_048))).getString();
        helper.assertTrue(texts.contains(from), "sem upgrades_from: " + texts);
        helper.assertTrue(texts.contains(items), "sem a linha de itens com a mudança: " + texts);

        ItemStack legacy = TierCoreItem.card(RouterTier.ELITE, null);
        helper.assertTrue(TierCoreItem.origin(legacy) == null, "cartão antigo com origem");
        List<String> old = tooltip(legacy);
        String legacyLine = Component.translatable(KEY + "legacy",
                Component.translatable(RouterTier.ELITE.translationKey())).getString();
        helper.assertTrue(old.contains(legacyLine), "sem legacy: " + old);
        helper.assertTrue(!old.contains(from), "cartão antigo com upgrades_from: " + old);
        String oldItems = Component.translatable(KEY + "items", TierCoreItem.grouped(2_048)).getString();
        helper.assertTrue(old.contains(oldItems), "cartão antigo sem o valor do destino: " + old);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void benchUsesOrigin(GameTestHelper helper) {
        ItemStack basic = RouterBlockItem.withTier(ModItems.ROUTER.get(), RouterTier.BASIC);
        basic.set(DataComponents.CUSTOM_NAME, Component.literal("Fornalha 1"));
        CraftingInput input = CraftingInput.of(2, 1,
                List.of(basic, TierCoreItem.card(RouterTier.ELITE, RouterTier.BASIC)));
        RecipeHolder<CraftingRecipe> holder = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new GameTestAssertException("roteador Básico + cartão Básico → Elite não casou"));
        ItemStack out = holder.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(RouterBlockItem.tierOf(out) == RouterTier.ELITE, "tier: " + out);
        helper.assertTrue(out.getHoverName().getString().equals("Fornalha 1"), "nome perdido");

        CraftingInput advanced = CraftingInput.of(2, 1, List.of(
                RouterBlockItem.withTier(ModItems.ROUTER.get(), RouterTier.ADVANCED),
                TierCoreItem.card(RouterTier.ELITE, RouterTier.BASIC)));
        helper.assertTrue(helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, advanced, helper.getLevel()).isEmpty(),
                "cartão Básico → Elite subiu um roteador Avançado");

        CraftingInput legacy = CraftingInput.of(2, 1, List.of(
                RouterBlockItem.withTier(ModItems.ROUTER.get(), RouterTier.BASIC),
                TierCoreItem.card(RouterTier.ELITE, null)));
        helper.assertTrue(helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, legacy, helper.getLevel()).isPresent(),
                "o cartão antigo não subiu o roteador Básico");
        helper.succeed();
    }

    // ------------------------------------------------------------------ anéis (tier_upgrade)

    /** A receita de anel carregada do {@code tier} (uma só por instância, inclusive no Ultimate). */
    static RecipeHolder<CraftingRecipe> ringHolder(GameTestHelper helper, RouterTier tier) {
        RecipeHolder<CraftingRecipe> found = null;
        for (RecipeHolder<CraftingRecipe> holder : helper.getLevel().getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)) {
            if (holder.value() instanceof TierUpgradeRecipe ring && ring.tier() == tier) {
                if (found != null) {
                    throw new GameTestAssertException("dois anéis para " + tier + ": " + found.id() + ", " + holder.id());
                }
                found = holder;
            }
        }
        if (found == null) {
            throw new GameTestAssertException("sem anel para " + tier);
        }
        return found;
    }

    /** A grade 3×3 com o anel da receita (o primeiro item de cada ingrediente) e {@code center} no meio. */
    static CraftingInput ringGrid(RecipeHolder<CraftingRecipe> holder, ItemStack center) {
        List<Ingredient> ingredients = holder.value().getIngredients();
        List<ItemStack> items = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            items.add(i == TierUpgradeRecipe.CENTER ? center : ingredients.get(i).getItems()[0].copyWithCount(1));
        }
        return CraftingInput.of(3, 3, items);
    }

    /** A grade casa com a receita {@code id}; devolve o resultado. */
    static ItemStack craft(GameTestHelper helper, CraftingInput input, ResourceLocation id) {
        RecipeHolder<CraftingRecipe> holder = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new GameTestAssertException("a grade não casou com " + id + ": " + input.items()));
        if (!holder.id().equals(id)) {
            throw new GameTestAssertException("casou com " + holder.id() + ", esperado " + id);
        }
        ItemStack out = holder.value().assemble(input, helper.getLevel().registryAccess());
        if (out.isEmpty() || out.getCount() != 1) {
            throw new GameTestAssertException(id + " deu " + out);
        }
        return out;
    }

    static boolean crafts(GameTestHelper helper, CraftingInput input) {
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .isPresent();
    }

    /** O cartão {@code out} vai de {@code from} para {@code to}. */
    static void assertCard(GameTestHelper helper, ItemStack out, RouterTier from, RouterTier to, String what) {
        helper.assertTrue(out.getItem() instanceof TierCoreItem core && core.tier() == to
                && Objects.equals(TierCoreItem.origin(out), from), what + ": " + out + " origem "
                + TierCoreItem.origin(out));
    }

    /** Anel pelos itens da tabela da spec (só os tiers sem o ATM), com {@code center} no meio. */
    private static CraftingInput vanillaRing(RouterTier tier, ItemStack center) {
        ItemLike[] ring = switch (tier) {
            case ADVANCED -> new ItemLike[] {Items.GOLD_INGOT, Items.DIAMOND, Items.GOLD_INGOT,
                    Items.DIAMOND, null, Items.DIAMOND, Items.GOLD_INGOT, Items.DIAMOND, Items.GOLD_INGOT};
            case ELITE -> new ItemLike[] {Items.NETHERITE_INGOT, Items.OBSIDIAN, Items.NETHERITE_INGOT,
                    Items.OBSIDIAN, null, Items.OBSIDIAN, Items.NETHERITE_INGOT, Items.OBSIDIAN, Items.NETHERITE_INGOT};
            case EMERALD -> new ItemLike[] {Items.EMERALD_BLOCK, Items.NETHER_STAR, Items.EMERALD_BLOCK,
                    Items.CRYING_OBSIDIAN, null, Items.CRYING_OBSIDIAN, Items.EMERALD_BLOCK, Items.ENDER_EYE,
                    Items.EMERALD_BLOCK};
            case ULTIMATE -> new ItemLike[] {Items.ECHO_SHARD, Items.DRAGON_EGG, Items.ECHO_SHARD,
                    Items.NETHERITE_BLOCK, null, Items.NETHERITE_BLOCK, Items.ECHO_SHARD, Items.NETHERITE_BLOCK,
                    Items.ECHO_SHARD};
            default -> throw new GameTestAssertException("anel do " + tier + " só na run do ATM");
        };
        List<ItemStack> items = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            items.add(i == TierUpgradeRecipe.CENTER ? center : new ItemStack(ring[i]));
        }
        return CraftingInput.of(3, 3, items);
    }

    private static ItemStack redstone() {
        return new ItemStack(Items.REDSTONE);
    }

    static ItemStack routerItem(RouterTier tier) {
        return RouterBlockItem.withTier(ModItems.ROUTER.get(), tier);
    }

    /** Anel + Redstone = cartão de um degrau, pela receita {@code tier_core_<tier>}, com a origem no degrau anterior. */
    @GameTest(template = "empty")
    public static void stepCardRecipe(GameTestHelper helper) {
        for (RouterTier tier : RouterTier.values()) {
            if (!tier.loaded() || !ModItems.TIER_CORES.containsKey(tier)) {
                continue;
            }
            ItemStack out = craft(helper, vanillaRing(tier, redstone()),
                    WirelessAutomate.id("tier_core_" + tier.getSerializedName()));
            assertCard(helper, out, tier.previous(), tier, "cartão de um degrau do " + tier);
        }
        // Sem o ATM, o Ultimate vem da Esmeralda.
        assertCard(helper, craft(helper, vanillaRing(RouterTier.ULTIMATE, redstone()),
                WirelessAutomate.id("tier_core_ultimate")), RouterTier.EMERALD, RouterTier.ULTIMATE, "Ultimate sem o ATM");
        helper.succeed();
    }

    /** Anel + cartão com origem um degrau abaixo = o cartão que pula, com a mesma origem. */
    @GameTest(template = "empty")
    public static void jumpCardRecipe(GameTestHelper helper) {
        assertCard(helper, craft(helper, vanillaRing(RouterTier.ELITE,
                TierCoreItem.card(RouterTier.ADVANCED, RouterTier.BASIC)), WirelessAutomate.id("tier_core_elite")),
                RouterTier.BASIC, RouterTier.ELITE, "Básico → Elite");
        assertCard(helper, craft(helper, vanillaRing(RouterTier.EMERALD,
                TierCoreItem.card(RouterTier.ELITE, RouterTier.BASIC)), WirelessAutomate.id("tier_core_emerald")),
                RouterTier.BASIC, RouterTier.EMERALD, "Básico → Esmeralda");
        assertCard(helper, craft(helper, vanillaRing(RouterTier.ULTIMATE,
                TierCoreItem.card(RouterTier.EMERALD, RouterTier.ADVANCED)), WirelessAutomate.id("tier_core_ultimate")),
                RouterTier.ADVANCED, RouterTier.ULTIMATE, "Avançado → Ultimate");
        helper.assertFalse(crafts(helper, vanillaRing(RouterTier.EMERALD,
                TierCoreItem.card(RouterTier.ADVANCED, RouterTier.BASIC))),
                "o anel do Esmeralda estendeu um cartão dois degraus abaixo");
        helper.assertFalse(crafts(helper, vanillaRing(RouterTier.ELITE, TierCoreItem.card(RouterTier.ADVANCED, null))),
                "o anel do Elite estendeu um cartão antigo");
        helper.succeed();
    }

    /** Anel + roteador um degrau abaixo = o mesmo item, com os outros componentes, no tier do anel. */
    @GameTest(template = "empty")
    public static void directRecipeKeepsRouterData(GameTestHelper helper) {
        ItemStack advanced = routerItem(RouterTier.ADVANCED);
        advanced.set(DataComponents.CUSTOM_NAME, Component.literal("Fornalha 1"));
        CompoundTag data = new CompoundTag();
        data.putString("id", WirelessAutomate.id("router").toString());
        data.putString("marca", "teste");
        advanced.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
        ItemStack elite = craft(helper, vanillaRing(RouterTier.ELITE, advanced), WirelessAutomate.id("tier_core_elite"));
        helper.assertTrue(elite.is(ModItems.ROUTER.get()), "resultado: " + elite);
        helper.assertTrue(RouterBlockItem.tierOf(elite) == RouterTier.ELITE, "tier: " + elite);
        assertSameComponents(helper, advanced, elite);
        helper.assertTrue(advanced.getCount() == 1 && RouterBlockItem.tierOf(advanced) == RouterTier.ADVANCED,
                "o item do centro mudou");

        helper.assertFalse(crafts(helper, vanillaRing(RouterTier.ELITE, routerItem(RouterTier.BASIC))),
                "o anel do Elite subiu um roteador Básico (dois degraus)");
        helper.assertFalse(crafts(helper, vanillaRing(RouterTier.ELITE, routerItem(RouterTier.ELITE))),
                "o anel do Elite aceitou um roteador Elite");
        helper.succeed();
    }

    /** Os componentes dos dois itens, fora o {@code block_state}, são iguais (nos dois sentidos). */
    static void assertSameComponents(GameTestHelper helper, ItemStack before, ItemStack after) {
        for (ItemStack[] pair : new ItemStack[][] {{before, after}, {after, before}}) {
            for (TypedDataComponent<?> component : pair[0].getComponents()) {
                DataComponentType<?> type = component.type();
                if (type == DataComponents.BLOCK_STATE) {
                    continue;
                }
                helper.assertTrue(Objects.equals(pair[1].get(type), component.value()),
                        "componente " + type + " mudou: " + component.value() + " × " + pair[1].get(type));
            }
        }
    }

    /**
     * Baú Avançado quebrado (com conteúdo e filtro) no anel do Elite = Baú Elite com a mesma referência e o mesmo
     * filtro; colocado, o conteúdo volta. Tanque e Bateria (e os de outros mods, com eles) pelo resultado só.
     */
    @GameTest(template = "empty")
    public static void directRecipeKeepsStorageContents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 2, 0);
        helper.setBlock(pos, ModBlocks.STORAGE_CHEST.get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        StorageChestBlockEntity chest = helper.getBlockEntity(pos);
        chest.storage().insert(new ItemStack(Items.COBBLESTONE), 4_321L, false);
        Filter filter = new Filter(Filter.ListMode.BLACKLIST, false,
                List.of(new FilterEntry.ItemEntry(new ItemStack(Items.DIRT), 0)));
        chest.setFilter(filter);
        BlockPos absolute = helper.absolutePos(pos);
        helper.getLevel().destroyBlock(absolute, true);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2),
                entity -> entity.getItem().is(ModItems.STORAGE_CHEST.get())
                        && entity.getItem().get(ModDataComponents.STORAGE_CONTENTS.get()) != null
                        && entity.getItem().get(ModDataComponents.STORAGE_CONTENTS.get()).total() == 4_321L);
        helper.assertTrue(drops.size() == 1, "um Baú no chão: " + drops.size());
        ItemStack dropped = drops.get(0).getItem().copy();
        drops.get(0).discard();

        ItemStack elite = craft(helper, vanillaRing(RouterTier.ELITE, dropped), WirelessAutomate.id("tier_core_elite"));
        helper.assertTrue(StorageBlockItem.tierOf(elite) == RouterTier.ELITE, "tier: " + elite);
        helper.assertTrue(Objects.equals(elite.get(ModDataComponents.STORAGE_CONTENTS.get()),
                dropped.get(ModDataComponents.STORAGE_CONTENTS.get())), "referência ao conteúdo mudou");
        helper.assertTrue(Objects.equals(elite.get(ModDataComponents.STORAGE_FILTER.get()), filter), "filtro mudou");

        helper.setBlock(pos.below(), Blocks.STONE);
        BlockPos floor = absolute.below();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        ((BlockItem) elite.getItem()).place(new BlockPlaceContext(helper.getLevel(), null, InteractionHand.MAIN_HAND,
                elite, hit));
        helper.assertBlockProperty(pos, RouterBlock.TIER, RouterTier.ELITE);
        StorageChestBlockEntity placed = helper.getBlockEntity(pos);
        helper.assertTrue(placed.storage().count(new ItemStack(Items.COBBLESTONE)) == 4_321L, "o conteúdo não voltou");
        helper.assertTrue(Objects.equals(placed.filter(), filter), "o filtro não voltou");

        for (StorageKind kind : StorageKind.values()) {
            if (kind != StorageKind.CHEST && kind.loaded()) {
                directStorage(helper, kind);
            }
        }
        helper.succeed();
    }

    /** O armazenamento {@code kind} Avançado, com conteúdo e filtro, no anel do Elite sobe com os mesmos componentes. */
    static void directStorage(GameTestHelper helper, StorageKind kind) {
        ItemStack item = StorageBlockItem.withTier(ModItems.STORAGE.get(kind).get(), RouterTier.ADVANCED);
        item.set(ModDataComponents.STORAGE_CONTENTS.get(), new StorageContents(UUID.randomUUID(), 1, 9_876L));
        item.set(ModDataComponents.STORAGE_FILTER.get(), Filter.EMPTY);
        ItemStack out = craft(helper, ringGrid(ringHolder(helper, RouterTier.ELITE), item),
                WirelessAutomate.id("tier_core_elite"));
        helper.assertTrue(out.is(ModItems.STORAGE.get(kind).get()) && StorageBlockItem.tierOf(out) == RouterTier.ELITE,
                kind + ": " + out);
        assertSameComponents(helper, item, out);
    }

    /** As três grades de cada tier casam com o mesmo id: é o mesmo anel. */
    @GameTest(template = "empty")
    public static void sameRingThreeRecipes(GameTestHelper helper) {
        sameRing(helper);
        helper.succeed();
    }

    /** O corpo do {@link #sameRingThreeRecipes}, para a run do ATM também. */
    static void sameRing(GameTestHelper helper) {
        for (RouterTier tier : RouterTier.values()) {
            RouterTier below = tier.previous();
            if (!tier.loaded() || !ModItems.TIER_CORES.containsKey(tier) || below == null) {
                continue;
            }
            RecipeHolder<CraftingRecipe> ring = ringHolder(helper, tier);
            assertCard(helper, craft(helper, ringGrid(ring, redstone()), ring.id()), below, tier, "Redstone no " + tier);
            if (below != RouterTier.BASIC) {
                assertCard(helper, craft(helper, ringGrid(ring, TierCoreItem.card(below, RouterTier.BASIC)), ring.id()),
                        RouterTier.BASIC, tier, "cartão que pula no " + tier);
            }
            ItemStack block = craft(helper, ringGrid(ring, routerItem(below)), ring.id());
            helper.assertTrue(RouterBlockItem.tierOf(block) == tier, "bloco no " + tier + ": " + block);
        }
    }

    /**
     * De Básico até cada tier, subir na bancada degrau a degrau e montar o cartão custam os mesmos anéis; o cartão
     * pede só uma Redstone a mais. Os dois caminhos são montados pelas receitas reais, somando o que foi à grade.
     */
    @GameTest(template = "empty")
    public static void sameCostBothWays(GameTestHelper helper) {
        sameCost(helper);
        helper.succeed();
    }

    /** O corpo do {@link #sameCostBothWays}, para a run do ATM também. */
    static void sameCost(GameTestHelper helper) {
        for (RouterTier target : RouterTier.values()) {
            if (!target.loaded() || !ModItems.TIER_CORES.containsKey(target)) {
                continue;
            }
            Map<Item, Integer> bench = new HashMap<>();
            Map<Item, Integer> cardCost = new HashMap<>();
            ItemStack block = routerItem(RouterTier.BASIC);
            ItemStack card = ItemStack.EMPTY;
            for (RouterTier tier = RouterTier.BASIC.next(); tier != null; tier = tier.next()) {
                RecipeHolder<CraftingRecipe> ring = ringHolder(helper, tier);
                CraftingInput benchGrid = ringGrid(ring, block);
                add(bench, benchGrid, false);
                block = craft(helper, benchGrid, ring.id());

                boolean first = card.isEmpty();
                CraftingInput cardGrid = ringGrid(ring, first ? redstone() : card);
                add(cardCost, cardGrid, first);
                card = craft(helper, cardGrid, ring.id());
                if (tier == target) {
                    break;
                }
            }
            helper.assertTrue(RouterBlockItem.tierOf(block) == target, "bancada até " + target + ": " + block);
            assertCard(helper, card, RouterTier.BASIC, target, "cartão até " + target);
            Map<Item, Integer> expected = new HashMap<>(bench);
            expected.merge(Items.REDSTONE, 1, Integer::sum);
            helper.assertTrue(expected.equals(cardCost), "custo até " + target + ": bancada " + bench
                    + " + Redstone × cartão " + cardCost);
            // E o cartão sobe um roteador Básico até o destino, como a bancada.
            CraftingInput use = CraftingInput.of(2, 1, List.of(routerItem(RouterTier.BASIC), card));
            RecipeHolder<CraftingRecipe> holder = helper.getLevel().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, use, helper.getLevel())
                    .orElseThrow(() -> new GameTestAssertException("o cartão Básico → " + target + " não subiu o Básico"));
            helper.assertTrue(RouterBlockItem.tierOf(holder.value().assemble(use, helper.getLevel().registryAccess()))
                    == target, "cartão até " + target + " na bancada");
        }
    }

    /** Soma os itens da grade; o centro só se {@code withCenter} (o item do centro dos outros sai do degrau anterior). */
    private static void add(Map<Item, Integer> cost, CraftingInput grid, boolean withCenter) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = grid.getItem(i);
            if (!stack.isEmpty() && (withCenter || i != TierUpgradeRecipe.CENTER)) {
                cost.merge(stack.getItem(), 1, Integer::sum);
            }
        }
    }

    /** Nenhuma receita de bancada carregada fabrica um cartão sem origem. */
    @GameTest(template = "empty")
    public static void noCardWithoutOrigin(GameTestHelper helper) {
        for (RecipeHolder<CraftingRecipe> holder : helper.getLevel().getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(helper.getLevel().registryAccess());
            helper.assertFalse(result.getItem() instanceof TierCoreItem && TierCoreItem.origin(result) == null,
                    "cartão sem origem em " + holder.id());
        }
        helper.succeed();
    }

    /**
     * O codec do anel: a receita volta a JSON pelo codec (o {@code Data} do anel), o stream codec a leva ao cliente,
     * e um anel com borda vazia, com o centro ocupado ou no tier Básico é recusado.
     */
    @GameTest(template = "empty")
    public static void ringCodec(GameTestHelper helper) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        TierUpgradeRecipe elite = (TierUpgradeRecipe) ringHolder(helper, RouterTier.ELITE).value();
        JsonObject encoded = TierUpgradeRecipe.MAP_CODEC.codec().encodeStart(ops, elite).getOrThrow().getAsJsonObject();
        helper.assertTrue(encoded.get("tier").getAsString().equals("elite") && encoded.has("pattern")
                && encoded.has("key"), "JSON reescrito: " + encoded);

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        TierUpgradeRecipe.STREAM_CODEC.encode(buf, elite);
        TierUpgradeRecipe synced = TierUpgradeRecipe.STREAM_CODEC.decode(buf);
        helper.assertTrue(synced.tier() == RouterTier.ELITE, "tier no cliente");
        assertCard(helper, synced.getResultItem(helper.getLevel().registryAccess()), RouterTier.ADVANCED,
                RouterTier.ELITE, "resultado no cliente");
        helper.assertTrue(synced.matches(vanillaRing(RouterTier.ELITE, redstone()), helper.getLevel()),
                "não casa no cliente");

        String key = "\"key\": {\"N\": {\"item\": \"minecraft:netherite_ingot\"}, "
                + "\"O\": {\"item\": \"minecraft:obsidian\"}}";
        for (String bad : List.of(
                "{\"tier\": \"elite\", \"pattern\": [\"NON\", \"O O\", \"   \"], " + key + "}",
                "{\"tier\": \"elite\", \"pattern\": [\"NON\", \"OOO\", \"NON\"], " + key + "}",
                "{\"tier\": \"basic\", \"pattern\": [\"NON\", \"O O\", \"NON\"], " + key + "}")) {
            DataResult<TierUpgradeRecipe> result = TierUpgradeRecipe.MAP_CODEC.codec()
                    .parse(ops, JsonParser.parseString(bad));
            helper.assertTrue(result.isError(), "aceitou " + bad);
        }
        DataResult<TierUpgradeRecipe> good = TierUpgradeRecipe.MAP_CODEC.codec().parse(ops, JsonParser.parseString(
                "{\"tier\": \"elite\", \"pattern\": [\"NON\", \"O O\", \"NON\"], " + key + "}"));
        helper.assertTrue(good.isSuccess() && good.getOrThrow().step().test(redstone()), "padrão do step: " + good);
        helper.succeed();
    }
}
