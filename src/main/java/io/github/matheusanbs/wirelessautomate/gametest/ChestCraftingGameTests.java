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
import io.github.matheusanbs.wirelessautomate.packet.ChestCraftFillPayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
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

        // Receita presente, mas a grade mudou por fora sem aviso: B não recebe nada, e o resultado é recalculado.
        // Terra no lugar da tábua (uma tábua sozinha seria o botão de madeira, outra receita).
        ItemStack aside = chest.upgrades().grid().set(0, new ItemStack(Items.DIRT));
        helper.assertTrue(menuB.getSlot(RESULT).getItem().is(Items.STICK), "sem aviso, o resultado velho continua");
        menuB.clicked(RESULT, 0, ClickType.PICKUP, b);
        helper.assertTrue(menuB.getCarried().isEmpty(), "o resultado velho saiu com a grade mudada");
        helper.assertTrue(menuB.getSlot(RESULT).getItem().isEmpty(), "a recusa recalculou o resultado");
        chest.upgrades().grid().set(0, aside);
        chest.upgrades().changed();
        helper.assertTrue(menuB.getSlot(RESULT).getItem().is(Items.STICK), "a grade de volta");

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

    /**
     * Shift + clique com restos: o bloco de mel (4 × 2 garrafas de mel) devolve 4 garrafas vazias ao inventário, que
     * tomam o único slot livre conferido para o bloco. O bloco não some: cai no chão, como no {@code CraftingMenu}.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void shiftComRestosNaoPerdeOResultado(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        putCrafting(chest);
        ServerPlayer player = TestPlayers.survivalMenuOpener(helper);
        player.moveTo(Vec3.atCenterOf(chest.getBlockPos().above()));
        player.getInventory().clearContent();
        for (int i = 1; i < 36; i++) {
            player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        for (int slot : new int[] {0, 1, 3, 4}) {
            menu.getSlot(GRID + slot).set(new ItemStack(Items.HONEY_BOTTLE, 2));
        }
        helper.assertTrue(menu.getSlot(RESULT).getItem().is(Items.HONEY_BLOCK), "o resultado é o bloco de mel");

        menu.clicked(RESULT, 0, ClickType.QUICK_MOVE, player);

        int bottlesUsed = 8 - inGrid(menu, Items.HONEY_BOTTLE);
        int blocks = player.getInventory().countItem(Items.HONEY_BLOCK) + onGround(helper, player, Items.HONEY_BLOCK);
        helper.assertValueEqual(blocks * 4, bottlesUsed, "blocos feitos (inventário + chão) contra garrafas usadas");
        helper.assertTrue(blocks >= 1, "nenhum craft");
        helper.assertValueEqual(player.getInventory().countItem(Items.GLASS_BOTTLE), bottlesUsed, "as garrafas vazias");
        helper.succeed();
    }

    /**
     * Tecla numérica (SWAP) e Ctrl + Q (THROW) no resultado consomem a grade como o clique; com a grade mudada por
     * fora, sem aviso, não entregam nada.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void swapEThrowNoResultadoConsomemAGrade(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        putCrafting(chest);
        ServerPlayer player = player(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        stickRecipe(menu);

        menu.clicked(RESULT, 0, ClickType.SWAP, player);
        helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 4, "SWAP: 4 gravetos na barra");
        helper.assertValueEqual(inGrid(menu, Items.OAK_PLANKS), 0, "SWAP consumiu a grade (o Baú vazio não repõe)");

        stickRecipe(menu);
        menu.clicked(RESULT, 1, ClickType.THROW, player);
        helper.assertValueEqual(onGround(helper, player, Items.STICK), 4, "THROW: 4 gravetos no chão");
        helper.assertValueEqual(inGrid(menu, Items.OAK_PLANKS), 0, "THROW consumiu a grade");

        stickRecipe(menu);
        chest.upgrades().grid().set(3, new ItemStack(Items.DIRT));
        menu.clicked(RESULT, 1, ClickType.SWAP, player);
        menu.clicked(RESULT, 1, ClickType.THROW, player);
        helper.assertValueEqual(player.getInventory().getItem(1).getCount(), 0, "SWAP com a grade mudada");
        helper.assertValueEqual(onGround(helper, player, Items.STICK), 4, "THROW com a grade mudada");
        helper.assertValueEqual(inGrid(menu, Items.OAK_PLANKS), 1, "a tábua que sobrou fica");
        helper.succeed();
    }

    /** Tirar a Mesa com o Baú cheio: a grade vai para o inventário de quem tirou, nada cai. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tirarAMesaComBauCheioVaiProInventario(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        chest.storage().insert(new ItemStack(Items.DIRT), Long.MAX_VALUE / 4, false);
        putCrafting(chest);
        ServerPlayer player = player(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        for (int i = 0; i < 9; i++) {
            menu.getSlot(GRID + i).set(new ItemStack(Items.COBBLESTONE, 5));
        }
        helper.assertValueEqual(chest.storage().insert(new ItemStack(Items.COBBLESTONE), 1, true), 0L, "o Baú cheio");

        menu.clicked(ChestUpgradeSlots.UPGRADE_FIRST, 0, ClickType.PICKUP, player);

        helper.assertTrue(chest.upgrades().gridEmpty(), "a grade vazia");
        helper.assertValueEqual(player.getInventory().countItem(Items.COBBLESTONE), 45, "a grade no inventário");
        helper.assertValueEqual(onGround(helper, player, Items.COBBLESTONE), 0, "nada caiu");
        helper.succeed();
    }

    /** Itens do tipo no chão perto do jogador. */
    private static int onGround(GameTestHelper helper, ServerPlayer player, Item item) {
        int total = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(4.0), e -> e.getItem().is(item))) {
            total += entity.getItem().getCount();
        }
        return total;
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

    // ------------------------------------------------------------------ o "+" do JEI (o handler do servidor)

    private static List<List<ItemStack>> lists(int firstSlot, int secondSlot, Item... alternatives) {
        List<List<ItemStack>> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            List<ItemStack> list = new ArrayList<>();
            if (i == firstSlot || i == secondSlot) {
                for (Item alternative : alternatives) {
                    list.add(new ItemStack(alternative, 64));
                }
            }
            slots.add(list);
        }
        return slots;
    }

    private static List<List<ItemStack>> stickLists(Item... alternatives) {
        return lists(0, 3, alternatives);
    }

    /** O item em todo canto do jogador e do Baú: Baú, grade, inventário e cursor. */
    private static long everywhere(StorageChestBlockEntity chest, ServerPlayer player, Item item) {
        long grid = 0;
        for (ItemStack stack : chest.upgrades().grid()) {
            grid += stack.is(item) ? stack.getCount() : 0;
        }
        return stored(chest, item) + grid + held(player, item);
    }

    /**
     * O "+" monta a receita pelo Baú primeiro e pelo inventário depois: o Baú tem 1 tábua de carvalho, a segunda
     * alternativa (bétula) só existe no inventário. A grade que já tinha algo volta ao Baú antes, o slot sem nenhuma
     * alternativa disponível fica vazio, e nenhum item some nem aparece.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void jeiPreencheAGradePeloBauEDepoisPeloInventario(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        putCrafting(chest);
        chest.storage().insert(new ItemStack(Items.OAK_PLANKS), 1, false);
        ServerPlayer player = player(helper, chest);
        player.getInventory().setItem(5, new ItemStack(Items.BIRCH_PLANKS, 5));
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        menu.getSlot(GRID + 8).set(new ItemStack(Items.DIRT, 3));
        long oak = everywhere(chest, player, Items.OAK_PLANKS);
        long birch = everywhere(chest, player, Items.BIRCH_PLANKS);
        long dirt = everywhere(chest, player, Items.DIRT);
        List<List<ItemStack>> slots = stickLists(Items.OAK_PLANKS, Items.BIRCH_PLANKS);
        slots.set(6, List.of(new ItemStack(Items.DIAMOND)));

        helper.assertTrue(ChestUpgradeSlots.handleFill(player, new ChestCraftFillPayload(91, slots, false)),
                "o pacote foi aceito");

        helper.assertTrue(menu.getSlot(GRID).getItem().is(Items.OAK_PLANKS), "a primeira alternativa vem do Baú");
        helper.assertValueEqual(menu.getSlot(GRID).getItem().getCount(), 1, "1 por slot");
        helper.assertTrue(menu.getSlot(GRID + 3).getItem().is(Items.BIRCH_PLANKS), "a segunda vem do inventário");
        helper.assertValueEqual(menu.getSlot(GRID + 3).getItem().getCount(), 1, "1 por slot");
        helper.assertTrue(menu.getSlot(GRID + 6).getItem().isEmpty(), "sem alternativa disponível, o slot fica vazio");
        helper.assertTrue(menu.getSlot(GRID + 8).getItem().isEmpty(), "a grade velha foi devolvida");
        helper.assertValueEqual(stored(chest, Items.DIRT), 3L, "a terra voltou ao Baú");
        helper.assertTrue(menu.getSlot(RESULT).getItem().is(Items.STICK), "o resultado já está na grade");
        helper.assertValueEqual(everywhere(chest, player, Items.OAK_PLANKS), oak, "tábuas de carvalho conservadas");
        helper.assertValueEqual(everywhere(chest, player, Items.BIRCH_PLANKS), birch, "tábuas de bétula conservadas");
        helper.assertValueEqual(everywhere(chest, player, Items.DIRT), dirt, "a terra conservada");
        helper.assertValueEqual(player.getInventory().countItem(Items.BIRCH_PLANKS), 4, "1 de bétula saiu do inventário");
        helper.succeed();
    }

    /** Com o Shift cada slot da mesma chave leva o mesmo tanto: o menor entre a pilha e o que há, dividido. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void jeiComShiftEncheOsSlotsIgualmente(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        putCrafting(chest);
        chest.storage().insert(new ItemStack(Items.OAK_PLANKS), 30, false);
        ServerPlayer player = player(helper, chest);
        player.getInventory().setItem(5, new ItemStack(Items.OAK_PLANKS, 41));
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        long oak = everywhere(chest, player, Items.OAK_PLANKS);

        helper.assertTrue(menu.upgradeSlots().fill(player, stickLists(Items.OAK_PLANKS), true), "aceito");

        // 71 no total: 35 por slot, 30 do Baú (primeiro) e 40 do inventário, sobrando 1 lá.
        helper.assertValueEqual(menu.getSlot(GRID).getItem().getCount(), 35, "slot de cima");
        helper.assertValueEqual(menu.getSlot(GRID + 3).getItem().getCount(), 35, "slot de baixo");
        helper.assertValueEqual(stored(chest, Items.OAK_PLANKS), 0L, "o Baú deu tudo");
        helper.assertValueEqual(player.getInventory().countItem(Items.OAK_PLANKS), 1, "o inventário ficou com a sobra");
        helper.assertValueEqual(everywhere(chest, player, Items.OAK_PLANKS), oak, "tábuas conservadas");

        // Sem o Shift: 1 por slot, devolvendo a grade antes (as 70 voltam ao Baú).
        helper.assertTrue(menu.upgradeSlots().fill(player, stickLists(Items.OAK_PLANKS), false), "aceito de novo");
        helper.assertValueEqual(menu.getSlot(GRID).getItem().getCount(), 1, "1 no slot de cima");
        helper.assertValueEqual(menu.getSlot(GRID + 3).getItem().getCount(), 1, "1 no slot de baixo");
        helper.assertValueEqual(everywhere(chest, player, Items.OAK_PLANKS), oak, "tábuas conservadas de novo");
        helper.succeed();
    }

    /** Com a Compactação, o Baú que só tem o bloco entrega o lingote: o bloco desce e a sobra fica no Baú. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void jeiDesceAFormaCompactadaDoBau(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        putCrafting(chest);
        ItemStack compacting = new ItemStack(ModItems.COMPACTING_UPGRADE.get());
        compacting.set(ModDataComponents.COMPACTING_RULES.get(), CompactingRules.DEFAULT.withMode(CompactingMode.COMPRESS));
        chest.upgrades().setUpgrade(1, compacting);
        chest.storage().insert(new ItemStack(Items.IRON_BLOCK), 1, false);
        ServerPlayer player = player(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 91);

        helper.assertTrue(menu.upgradeSlots().fill(player, lists(0, 4, Items.IRON_INGOT), false), "aceito");

        helper.assertTrue(menu.getSlot(GRID).getItem().is(Items.IRON_INGOT), "o lingote na grade");
        helper.assertTrue(menu.getSlot(GRID + 4).getItem().is(Items.IRON_INGOT), "o outro lingote na grade");
        helper.assertValueEqual(stored(chest, Items.IRON_BLOCK), 0L, "o bloco desceu");
        helper.assertValueEqual(stored(chest, Items.IRON_INGOT), 7L, "a sobra do bloco: 9 - 2");
        helper.succeed();
    }

    /**
     * O servidor não confia no cliente: sem a Mesa, com menos de 9 listas, com mais de 16 alternativas, com o
     * {@code containerId} errado ou sem jogador nada muda (nem a grade é devolvida); a contagem das pilhas do pacote
     * é ignorada (a de 64 leva 1); e o pacote vai e volta pelo codec.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void jeiRecusaPedidoForaDaForma(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        chest.storage().insert(new ItemStack(Items.OAK_PLANKS), 10, false);
        ServerPlayer player = player(helper, chest);
        StorageListMenu<ItemStack> menu = open(player, chest, 91);
        List<List<ItemStack>> valid = stickLists(Items.OAK_PLANKS);

        helper.assertFalse(ChestUpgradeSlots.handleFill(player, new ChestCraftFillPayload(91, valid, false)),
                "sem a Mesa");
        helper.assertTrue(chest.upgrades().gridEmpty(), "nada na grade sem a Mesa");

        putCrafting(chest);
        menu.getSlot(GRID + 8).set(new ItemStack(Items.DIRT, 2));
        List<List<ItemStack>> eight = new ArrayList<>(valid.subList(0, 8));
        List<ItemStack> many = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            many.add(new ItemStack(Items.OAK_PLANKS));
        }
        List<List<ItemStack>> tooMany = new ArrayList<>(valid);
        tooMany.set(0, many);
        helper.assertFalse(ChestUpgradeSlots.handleFill(player, new ChestCraftFillPayload(91, eight, false)), "8 listas");
        helper.assertFalse(ChestUpgradeSlots.handleFill(player, new ChestCraftFillPayload(91, tooMany, false)),
                "17 alternativas");
        helper.assertFalse(ChestUpgradeSlots.handleFill(player, new ChestCraftFillPayload(92, valid, false)),
                "containerId errado");
        helper.assertFalse(ChestUpgradeSlots.handleFill(null, new ChestCraftFillPayload(91, valid, false)), "sem jogador");
        helper.assertTrue(menu.getSlot(GRID + 8).getItem().is(Items.DIRT), "a grade não foi mexida");
        helper.assertValueEqual(stored(chest, Items.OAK_PLANKS), 10L, "o Baú intacto");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        ChestCraftFillPayload.STREAM_CODEC.encode(buf, new ChestCraftFillPayload(91, valid, true));
        ChestCraftFillPayload decoded = ChestCraftFillPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(decoded.maxTransfer() && decoded.containerId() == 91 && decoded.slots().size() == 9
                && decoded.slots().get(0).size() == 1 && decoded.slots().get(1).isEmpty(), "o codec ida e volta");

        helper.assertTrue(ChestUpgradeSlots.handleFill(player, new ChestCraftFillPayload(91, valid, false)), "válido");
        helper.assertValueEqual(menu.getSlot(GRID).getItem().getCount(), 1, "a contagem do pacote é ignorada");
        helper.assertTrue(menu.getSlot(GRID + 8).getItem().isEmpty(), "a terra voltou ao Baú");
        helper.succeed();
    }
}
