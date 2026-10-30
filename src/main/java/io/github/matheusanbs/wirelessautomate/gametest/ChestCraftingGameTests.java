package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.ChestUpgradeSlots;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.packet.StorageActionPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingMode;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingRules;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A Mesa de Trabalho no menu do Baú: o resultado que repõe a grade pelo estoque, o Shift + clique até uma pilha, a
 * devolução da grade e as proteções contra duplicação (dois jogadores, Shift + clique, duplo clique, rodinha). Cada
 * teste abre o {@link StorageListMenu} no servidor com um jogador de teste e clica como o pacote de clique faria.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ChestCraftingGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);
    private static final int GRID = ChestUpgradeSlots.GRID_FIRST;
    private static final int RESULT = ChestUpgradeSlots.RESULT;

    private static StorageChestBlockEntity chest(GameTestHelper helper) {
        helper.setBlock(A, ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.ELITE));
        return helper.getBlockEntity(A);
    }

    private static void putCrafting(StorageChestBlockEntity chest) {
        chest.upgrades().setUpgrade(0, new ItemStack(ModItems.CRAFTING_UPGRADE.get()));
    }

    private static ServerPlayer player(GameTestHelper helper, StorageChestBlockEntity chest) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(chest.getBlockPos().above()));
        player.getInventory().clearContent();
        return player;
    }

    private static StorageListMenu<ItemStack> open(ServerPlayer player, StorageChestBlockEntity chest, int containerId) {
        StorageListMenu<ItemStack> menu = new StorageListMenu<>(containerId, player.getInventory(), chest);
        player.containerMenu = menu;
        return menu;
    }

    /** Duas tábuas, uma em cima da outra: a receita do graveto (4). */
    private static void stickRecipe(StorageListMenu<ItemStack> menu) {
        menu.getSlot(GRID).set(new ItemStack(Items.OAK_PLANKS));
        menu.getSlot(GRID + 3).set(new ItemStack(Items.OAK_PLANKS));
    }

    private static long stored(StorageChestBlockEntity chest, Item item) {
        return chest.storage().count(new ItemStack(item));
    }

    private static int inGrid(StorageListMenu<ItemStack> menu, Item item) {
        int total = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = menu.getSlot(GRID + i).getItem();
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** O item no jogador: inventário e cursor do menu aberto. */
    private static int held(ServerPlayer player, Item item) {
        ItemStack carried = player.containerMenu.getCarried();
        return player.getInventory().countItem(item) + (carried.is(item) ? carried.getCount() : 0);
    }

    private static boolean act(ServerPlayer player, StorageListMenu<ItemStack> menu, StorageActionPayload.Action action,
            Optional<Object> key, int slot) {
        return StorageListMenu.handle(player, new StorageActionPayload(menu.containerId, StorageKind.CHEST, action, key, slot));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void resultadoRepoeAGradePeloBau(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        chest.storage().insert(new ItemStack(Items.OAK_PLANKS), 64, false);
        putCrafting(chest);
        ServerPlayer player = player(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        stickRecipe(menu);
        helper.assertTrue(menu.getSlot(RESULT).getItem().is(Items.STICK), "o resultado mostra os gravetos");

        menu.clicked(RESULT, 0, ClickType.PICKUP, player);

        ItemStack carried = menu.getCarried();
        helper.assertTrue(carried.is(Items.STICK) && carried.getCount() == 4, "4 gravetos no cursor: " + carried);
        helper.assertValueEqual(menu.getSlot(GRID).getItem().getCount(), 1, "a tábua de cima reposta");
        helper.assertValueEqual(menu.getSlot(GRID + 3).getItem().getCount(), 1, "a tábua de baixo reposta");
        helper.assertValueEqual(stored(chest, Items.OAK_PLANKS), 62L, "2 tábuas saíram do Baú");
        helper.assertTrue(menu.getSlot(RESULT).getItem().is(Items.STICK), "o resultado volta");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void shiftCraftaAteUmaPilha(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        chest.storage().insert(new ItemStack(Items.OAK_PLANKS), 64, false);
        putCrafting(chest);
        ServerPlayer player = player(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        stickRecipe(menu);

        menu.clicked(RESULT, 0, ClickType.QUICK_MOVE, player);

        // Uma pilha de gravetos (64) são 16 crafts de 4. A grade começa com as 2 tábuas e cada craft a repõe com 2
        // do Baú: 16 × 2 = 32 tábuas saem do Baú, que fica com 64 − 32 = 32, e a grade termina com as 2 de novo.
        helper.assertValueEqual(player.getInventory().countItem(Items.STICK), 64, "uma pilha de gravetos no inventário");
        helper.assertValueEqual(inGrid(menu, Items.OAK_PLANKS), 2, "a grade reposta");
        helper.assertValueEqual(stored(chest, Items.OAK_PLANKS), 32L, "o Baú deu 32 tábuas");
        helper.assertTrue(menu.getCarried().isEmpty(), "nada no cursor");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void devolverPoeAGradeNoBau(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        putCrafting(chest);
        ServerPlayer player = player(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        menu.getSlot(GRID + 4).set(new ItemStack(Items.STONE, 3));

        helper.assertTrue(act(player, menu, StorageActionPayload.Action.RETURN_GRID, Optional.empty(), -1), "devolveu");

        helper.assertTrue(chest.upgrades().gridEmpty(), "a grade vazia");
        helper.assertValueEqual(stored(chest, Items.STONE), 3L, "as 3 pedras no Baú");
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 0, "nada foi para o inventário");
        helper.assertFalse(act(player, menu, StorageActionPayload.Action.RETURN_GRID, Optional.empty(), -1),
                "nada a devolver");
        helper.succeed();
    }

    /** Sem a Mesa no slot a grade não aceita item, e o que chega nela por fora volta ao Baú sem resultado. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void semMesaOsSlotsNaoAceitam(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        ServerPlayer player = player(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        ItemStack planks = new ItemStack(Items.OAK_PLANKS);
        helper.assertFalse(menu.getSlot(GRID).mayPlace(planks), "a grade aceitou item sem a Mesa");

        menu.setCarried(new ItemStack(Items.OAK_PLANKS, 2));
        menu.clicked(GRID, 0, ClickType.PICKUP, player);
        helper.assertValueEqual(menu.getCarried().getCount(), 2, "o clique não pôs na grade");

        chest.upgrades().grid().set(0, planks.copy());
        chest.upgrades().grid().set(3, planks.copy());
        chest.upgrades().changed();
        helper.assertTrue(menu.getSlot(RESULT).getItem().isEmpty(), "resultado sem a Mesa");
        helper.assertValueEqual(stored(chest, Items.OAK_PLANKS), 2L, "a grade sem Mesa voltou ao Baú");
        helper.succeed();
    }

    /**
     * Dois jogadores, uma grade: A monta o graveto, B vê o resultado; A tira as tábuas e o resultado de B some. Mesmo
     * com o resultado velho forçado de volta no menu de B (como se o aviso não tivesse chegado), nada sai: o slot
     * confere a receita contra a grade. As tábuas e os gravetos do mundo não mudam.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void doisJogadoresNaoDuplicam(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        chest.storage().insert(new ItemStack(Items.OAK_PLANKS), 64, false);
        putCrafting(chest);
        ServerPlayer a = player(helper, chest);
        ServerPlayer b = player(helper, chest);
        StorageListMenu<ItemStack> menuA = open(a, chest, 91);
        StorageListMenu<ItemStack> menuB = open(b, chest, 92);
        stickRecipe(menuA);
        helper.assertTrue(menuB.getSlot(RESULT).getItem().is(Items.STICK), "B vê o resultado");
        int planks = 64 + 2;

        menuA.clicked(GRID, 0, ClickType.QUICK_MOVE, a);
        menuA.clicked(GRID + 3, 0, ClickType.QUICK_MOVE, a);
        helper.assertTrue(chest.upgrades().gridEmpty(), "A tirou as tábuas");
        helper.assertTrue(menuB.getSlot(RESULT).getItem().isEmpty(), "o resultado de B foi recalculado");

        menuB.clicked(RESULT, 0, ClickType.PICKUP, b);
        menuB.getSlot(RESULT).set(new ItemStack(Items.STICK, 4));
        menuB.clicked(RESULT, 0, ClickType.PICKUP, b);
        menuB.getSlot(RESULT).set(new ItemStack(Items.STICK, 4));
        menuB.clicked(RESULT, 0, ClickType.QUICK_MOVE, b);

        long planksNow = stored(chest, Items.OAK_PLANKS) + inGrid(menuA, Items.OAK_PLANKS) + held(a, Items.OAK_PLANKS)
                + held(b, Items.OAK_PLANKS);
        long sticksNow = stored(chest, Items.STICK) + held(a, Items.STICK) + held(b, Items.STICK);
        helper.assertValueEqual(planksNow, (long) planks, "tábuas no mundo");
        helper.assertValueEqual(sticksNow, 0L, "nenhum graveto saiu");
        helper.succeed();
    }

    /**
     * {@code quickMoveStack} direto no resultado: o caminho comum do menu guardaria os gravetos no Baú sem o
     * {@code onTake}. Aqui ou a grade é consumida (2 tábuas por 4 gravetos), ou nada se move.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void shiftNoResultadoNaoGuardaSemConsumir(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        chest.storage().insert(new ItemStack(Items.OAK_PLANKS), 64, false);
        putCrafting(chest);
        ServerPlayer player = player(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        stickRecipe(menu);

        menu.quickMoveStack(player, RESULT);

        helper.assertValueEqual(stored(chest, Items.STICK), 0L, "gravetos guardados no Baú sem craft");
        long consumed = 66 - stored(chest, Items.OAK_PLANKS) - inGrid(menu, Items.OAK_PLANKS);
        helper.assertValueEqual((long) held(player, Items.STICK), consumed * 2, "gravetos sem tábuas consumidas");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void duploCliqueNaoJuntaDoResultado(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        chest.storage().insert(new ItemStack(Items.OAK_PLANKS), 64, false);
        putCrafting(chest);
        ServerPlayer player = player(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        stickRecipe(menu);
        menu.setCarried(new ItemStack(Items.STICK, 10));

        menu.clicked(0, 0, ClickType.PICKUP_ALL, player);

        helper.assertValueEqual(menu.getCarried().getCount(), 10, "o cursor juntou do resultado");
        helper.assertValueEqual(menu.getSlot(RESULT).getItem().getCount(), 4, "o resultado continua");
        helper.assertValueEqual(inGrid(menu, Items.OAK_PLANKS), 2, "a grade intacta");
        helper.assertValueEqual(stored(chest, Items.OAK_PLANKS), 64L, "o Baú intacto");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tirarAMesaDevolveAGrade(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        putCrafting(chest);
        ServerPlayer player = player(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        for (int i = 0; i < 9; i++) {
            menu.getSlot(GRID + i).set(new ItemStack(Items.COBBLESTONE, 5));
        }

        menu.clicked(ChestUpgradeSlots.UPGRADE_FIRST, 0, ClickType.PICKUP, player);

        helper.assertTrue(menu.getCarried().is(ModItems.CRAFTING_UPGRADE.get()), "a Mesa no cursor");
        helper.assertTrue(chest.upgrades().gridEmpty(), "a grade vazia");
        helper.assertValueEqual(stored(chest, Items.COBBLESTONE), 45L, "a grade no Baú");
        helper.succeed();
    }

    /**
     * Modo Compactar, só 2 blocos de ferro no Baú e a receita do balde na grade (3 lingotes em V): tirar o balde
     * repõe os 3 lingotes descendo 1 bloco por dentro (9 lingotes, 3 para a grade), e o Baú fica com 1 bloco e 6
     * lingotes.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mesaDesceOBlocoParaRepor(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        putCrafting(chest);
        ItemStack compacting = new ItemStack(ModItems.COMPACTING_UPGRADE.get());
        compacting.set(ModDataComponents.COMPACTING_RULES.get(), CompactingRules.DEFAULT.withMode(CompactingMode.COMPRESS));
        chest.upgrades().setUpgrade(1, compacting);
        chest.storage().insert(new ItemStack(Items.IRON_BLOCK), 2, false);
        ServerPlayer player = player(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        menu.getSlot(GRID).set(new ItemStack(Items.IRON_INGOT));
        menu.getSlot(GRID + 2).set(new ItemStack(Items.IRON_INGOT));
        menu.getSlot(GRID + 4).set(new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(menu.getSlot(RESULT).getItem().is(Items.BUCKET), "o resultado é o balde");

        menu.clicked(RESULT, 0, ClickType.PICKUP, player);

        helper.assertTrue(menu.getCarried().is(Items.BUCKET), "o balde no cursor");
        helper.assertValueEqual(inGrid(menu, Items.IRON_INGOT), 3, "os 3 lingotes repostos");
        helper.assertValueEqual(stored(chest, Items.IRON_BLOCK), 1L, "1 bloco desceu");
        helper.assertValueEqual(stored(chest, Items.IRON_INGOT), 6L, "a sobra dos lingotes");
        helper.succeed();
    }

    /** A rodinha sobre o slot do resultado não guarda o item feito sem o craft. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rodinhaNaoGuardaDoResultado(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        chest.storage().insert(new ItemStack(Items.OAK_PLANKS), 64, false);
        putCrafting(chest);
        ServerPlayer player = player(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        stickRecipe(menu);

        helper.assertFalse(act(player, menu, StorageActionPayload.Action.INSERT_ONE_FROM_SLOT, Optional.empty(), RESULT),
                "a rodinha guardou do resultado");
        helper.assertFalse(act(player, menu, StorageActionPayload.Action.INSERT_ONE_FROM_INVENTORY,
                Optional.of(new ItemStack(Items.STICK)), -1), "a rodinha achou o graveto no resultado");
        helper.assertValueEqual(stored(chest, Items.STICK), 0L, "nenhum graveto no Baú");
        helper.assertValueEqual(menu.getSlot(RESULT).getItem().getCount(), 4, "o resultado intacto");
        helper.succeed();
    }
}
