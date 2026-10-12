package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
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
}
