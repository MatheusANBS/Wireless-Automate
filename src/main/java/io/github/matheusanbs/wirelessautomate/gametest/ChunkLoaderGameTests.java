package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoadState;
import io.github.matheusanbs.wirelessautomate.chunk.RouterChunkLoader;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Upgrade de chunk loading: o slot da tela (Shift + clique leva e traz, dono = quem pôs), os tickets
 * do NeoForge registrados e liberados, o chunk da máquina noutro chunk, o limite por jogador (cada
 * chunk conta uma vez; quem esperava entra quando o dono libera), o upgrade de tier, o NBT e o drop ao
 * quebrar. Os tickets são conferidos no NeoForge com {@link RouterChunkLoader#hasTicket}.
 *
 * <p>Montagens fora da área do teste (para ter outro chunk) ficam 20 blocos acima dela, com o chunk
 * forçado pelo vanilla durante o teste para não descarregar, e são desmontadas no fim.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ChunkLoaderGameTests {
    private static final int CONTAINER_ID = 47;

    private static ItemStack upgrade() {
        return new ItemStack(ModItems.CHUNK_LOADER_UPGRADE.get());
    }

    /** Baú em {@code machine} e roteador preso nele pela face {@code facing}. Posições relativas. */
    private static RouterBlockEntity chestWithRouter(GameTestHelper helper, BlockPos machine, Direction facing) {
        helper.setBlock(machine, Blocks.CHEST);
        BlockPos pos = machine.relative(facing);
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, facing));
        return helper.getBlockEntity(pos);
    }

    /** O mesmo com posições absolutas, para montagens fora da área do teste. */
    private static RouterBlockEntity chestWithRouterAt(ServerLevel level, BlockPos machine, Direction facing) {
        level.setBlockAndUpdate(machine, Blocks.CHEST.defaultBlockState());
        BlockPos pos = machine.relative(facing);
        level.setBlockAndUpdate(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, facing));
        if (!(level.getBlockEntity(pos) instanceof RouterBlockEntity router)) {
            throw new GameTestAssertException("sem roteador em " + pos.toShortString());
        }
        return router;
    }

    private static long chunk(BlockPos pos) {
        return ChunkPos.asLong(pos);
    }

    private static boolean hasTicket(GameTestHelper helper, RouterBlockEntity router, BlockPos inChunk) {
        return RouterChunkLoader.hasTicket(helper.getLevel(), router.getBlockPos(), chunk(inChunk));
    }

    private static void assertState(GameTestHelper helper, RouterBlockEntity router, ChunkLoadState expected, String what) {
        helper.assertValueEqual(router.chunkLoadState(), expected, what);
    }

    /** Começo do chunk seguinte (em x) ao da origem da área, 20 blocos acima: fora da área e noutro chunk. */
    private static BlockPos nextChunkAbove(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new BlockPos(((origin.getX() >> 4) + 1) << 4, origin.getY() + 20, origin.getZ());
    }

    @SuppressWarnings("removal")
    private static ServerPlayer playerNear(GameTestHelper helper, RouterBlockEntity router) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        return player;
    }

    @GameTest(template = "empty")
    public static void slotForcesAndReleases(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, new BlockPos(1, 1, 1), Direction.UP);
        BlockPos pos = router.getBlockPos();
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    ServerPlayer player = playerNear(helper, router);
                    player.getInventory().setItem(0, upgrade());
                    RouterMenu menu = new RouterMenu(CONTAINER_ID, player.getInventory(), router,
                            RouterSnapshot.capture(router, player));
                    player.containerMenu = menu;
                    helper.assertFalse(menu.getSlot(RouterMenu.UPGRADE_SLOT).mayPlace(new ItemStack(Items.DIAMOND)),
                            "slot de upgrade aceitou diamante");
                    helper.assertFalse(menu.getSlot(RouterMenu.UPGRADE_SLOT).mayPlace(
                            new ItemStack(ModItems.FILTER_CARD.get())), "slot de upgrade aceitou cartão");
                    int hotbar0 = RouterMenu.UPGRADE_SLOT - 9;
                    helper.assertTrue(menu.getSlot(hotbar0).getItem().is(ModItems.CHUNK_LOADER_UPGRADE.get()),
                            "upgrade não está no slot 0 da barra");

                    // Shift + clique no inventário leva ao slot de upgrade.
                    menu.clicked(hotbar0, 0, ClickType.QUICK_MOVE, player);
                    helper.assertTrue(router.hasChunkUpgrade(), "o upgrade não foi para o roteador");
                    helper.assertTrue(menu.getSlot(hotbar0).getItem().isEmpty(), "o upgrade ficou no inventário");
                    helper.assertValueEqual(router.upgradeOwner(), player.getUUID(), "dono do upgrade");
                    assertState(helper, router, ChunkLoadState.ACTIVE, "estado com o upgrade");
                    helper.assertTrue(hasTicket(helper, router, router.getBlockPos()), "sem ticket no chunk do roteador");
                    helper.assertValueEqual(RouterChunkLoader.get().forcedChunks(player.getUUID()), 1, "chunks do dono");
                    helper.assertValueEqual(RouterSnapshot.capture(router, player).chunkLoad(), ChunkLoadState.ACTIVE,
                            "estado no snapshot");

                    // Shift + clique no slot de upgrade traz de volta e libera.
                    menu.clicked(RouterMenu.UPGRADE_SLOT, 0, ClickType.QUICK_MOVE, player);
                    helper.assertFalse(router.hasChunkUpgrade(), "o upgrade ficou no roteador");
                    helper.assertTrue(player.getInventory().contains(upgrade()), "o upgrade não voltou ao inventário");
                    helper.assertFalse(hasTicket(helper, router, router.getBlockPos()), "ticket ficou sem o upgrade");
                    helper.assertValueEqual(RouterChunkLoader.get().forcedChunks(player.getUUID()), 0, "chunks do dono");
                    assertState(helper, router, ChunkLoadState.NONE, "estado sem o upgrade");
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void breakDropsUpgradeAndReleases(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, new BlockPos(1, 1, 1), Direction.UP);
        BlockPos relative = new BlockPos(1, 2, 1);
        UUID owner = UUID.randomUUID();
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    router.setUpgrade(upgrade(), owner);
                    helper.assertTrue(hasTicket(helper, router, router.getBlockPos()), "sem ticket com o upgrade");
                    helper.destroyBlock(relative);
                    helper.assertBlockNotPresent(ModBlocks.ROUTER.get(), relative);
                    helper.assertItemEntityPresent(ModItems.CHUNK_LOADER_UPGRADE.get(), relative, 2.0);
                    helper.assertFalse(RouterChunkLoader.hasTicket(helper.getLevel(), helper.absolutePos(relative),
                            chunk(helper.absolutePos(relative))), "ticket ficou com o roteador quebrado");
                    helper.assertValueEqual(RouterChunkLoader.get().forcedChunks(owner), 0, "chunks do dono");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void tierUpgradeKeepsChunkUpgrade(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, new BlockPos(1, 1, 1), Direction.UP);
        UUID owner = UUID.randomUUID();
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    router.setUpgrade(upgrade(), owner);
                    helper.assertTrue(RouterBlock.tryUpgrade(helper.getLevel(), router.getBlockPos(), RouterTier.ADVANCED),
                            "upgrade de tier recusado");
                    RouterBlockEntity after = helper.getBlockEntity(new BlockPos(1, 2, 1));
                    helper.assertTrue(after == router, "o block entity foi trocado");
                    helper.assertValueEqual(after.tier(), RouterTier.ADVANCED, "tier");
                    helper.assertTrue(after.hasChunkUpgrade(), "o upgrade de chunk loading saiu no upgrade de tier");
                    assertState(helper, after, ChunkLoadState.ACTIVE, "estado depois do upgrade de tier");
                    helper.assertTrue(hasTicket(helper, after, after.getBlockPos()), "ticket saiu no upgrade de tier");
                })
                .thenSucceed();
    }

    /** Salvo e relido: o upgrade e o dono voltam; relido sem o upgrade (ex.: /data), o ticket sai. */
    @GameTest(template = "empty")
    public static void upgradeSavedAndReloaded(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, new BlockPos(1, 1, 1), Direction.UP);
        UUID owner = UUID.randomUUID();
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    router.setUpgrade(upgrade(), owner);
                    CompoundTag tag = router.saveWithoutMetadata(helper.getLevel().registryAccess());
                    helper.assertTrue(tag.contains("upgrade"), "upgrade não foi salvo");
                    helper.assertValueEqual(tag.getUUID("upgrade_owner"), owner, "dono salvo");

                    RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
                    copy.loadWithComponents(tag, helper.getLevel().registryAccess());
                    helper.assertTrue(copy.hasChunkUpgrade(), "upgrade não foi lido");
                    helper.assertValueEqual(copy.upgradeOwner(), owner, "dono lido");

                    tag.remove("upgrade");
                    router.loadWithComponents(tag, helper.getLevel().registryAccess());
                    helper.assertFalse(router.hasChunkUpgrade(), "upgrade ficou depois de relido sem ele");
                })
                // A releitura só enfileira: o ticket sai no tick seguinte.
                .thenWaitUntil(() -> helper.assertFalse(hasTicket(helper, router, router.getBlockPos()),
                        "ticket ficou depois de relido sem o upgrade"))
                .thenExecute(() -> helper.assertValueEqual(RouterChunkLoader.get().forcedChunks(owner), 0, "chunks do dono"))
                .thenSucceed();
    }

    /** Roteador num chunk e máquina no vizinho: os dois chunks são forçados e contam 2 para o dono. */
    @GameTest(template = "empty")
    public static void machineChunkForcedToo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos routerPos = nextChunkAbove(helper);
        BlockPos machine = routerPos.west();
        ChunkPos routerChunk = new ChunkPos(routerPos);
        ChunkPos machineChunk = new ChunkPos(machine);
        UUID owner = UUID.randomUUID();
        List<RouterBlockEntity> routers = new ArrayList<>();
        helper.startSequence()
                .thenExecute(() -> {
                    level.setChunkForced(routerChunk.x, routerChunk.z, true);
                    level.setChunkForced(machineChunk.x, machineChunk.z, true);
                    routers.add(chestWithRouterAt(level, machine, Direction.EAST));
                })
                .thenExecuteAfter(1, () -> {
                    RouterBlockEntity router = routers.get(0);
                    helper.assertTrue(!routerChunk.equals(machineChunk), "montagem no mesmo chunk");
                    router.setUpgrade(upgrade(), owner);
                    assertState(helper, router, ChunkLoadState.ACTIVE, "estado");
                    helper.assertTrue(hasTicket(helper, router, routerPos), "sem ticket no chunk do roteador");
                    helper.assertTrue(hasTicket(helper, router, machine), "sem ticket no chunk da máquina");
                    helper.assertValueEqual(RouterChunkLoader.get().forcedChunks(owner), 2, "chunks do dono");

                    // Sai do mundo (trocado por ar, sem drop): os dois tickets saem.
                    level.removeBlock(routerPos, false);
                    level.removeBlock(machine, false);
                    helper.assertFalse(RouterChunkLoader.hasTicket(level, routerPos, routerChunk.toLong()),
                            "ticket do chunk do roteador ficou");
                    helper.assertFalse(RouterChunkLoader.hasTicket(level, routerPos, machineChunk.toLong()),
                            "ticket do chunk da máquina ficou");
                    helper.assertValueEqual(RouterChunkLoader.get().forcedChunks(owner), 0, "chunks do dono");
                    for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(routerPos).inflate(3))) {
                        item.discard();
                    }
                    level.setChunkForced(routerChunk.x, routerChunk.z, false);
                    level.setChunkForced(machineChunk.x, machineChunk.z, false);
                })
                .thenSucceed();
    }

    /**
     * Limite 1 para o dono: dois roteadores no mesmo chunk contam um chunk e ficam ativos; um terceiro
     * noutro chunk fica no limite, sem ticket. Tirar o upgrade de um dos dois não libera o chunk (o
     * outro ainda o usa); tirar dos dois libera, e o terceiro entra no tick seguinte.
     */
    @GameTest(template = "empty")
    public static void limitPerPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        RouterChunkLoader.overrideLimit(owner, 1);
        // A em cima e B embaixo do mesmo baú: mesma coluna, mesmo chunk (o da origem, em x = 0).
        RouterBlockEntity a = chestWithRouter(helper, new BlockPos(0, 1, 1), Direction.UP);
        helper.setBlock(new BlockPos(0, 0, 1),
                ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.DOWN));
        RouterBlockEntity b = helper.getBlockEntity(new BlockPos(0, 0, 1));
        BlockPos farMachine = nextChunkAbove(helper);
        ChunkPos farChunk = new ChunkPos(farMachine);
        List<RouterBlockEntity> far = new ArrayList<>();
        helper.startSequence()
                .thenExecute(() -> {
                    level.setChunkForced(farChunk.x, farChunk.z, true);
                    far.add(chestWithRouterAt(level, farMachine, Direction.UP));
                })
                .thenExecuteAfter(1, () -> {
                    RouterBlockEntity c = far.get(0);
                    helper.assertTrue(chunk(a.getBlockPos()) == chunk(b.getBlockPos()), "A e B em chunks diferentes");
                    helper.assertTrue(chunk(a.getBlockPos()) != chunk(c.getBlockPos()), "C no chunk de A");
                    a.setUpgrade(upgrade(), owner);
                    b.setUpgrade(upgrade(), owner);
                    c.setUpgrade(upgrade(), owner);
                    assertState(helper, a, ChunkLoadState.ACTIVE, "A");
                    assertState(helper, b, ChunkLoadState.ACTIVE, "B (mesmo chunk de A)");
                    assertState(helper, c, ChunkLoadState.LIMIT, "C (outro chunk)");
                    helper.assertFalse(hasTicket(helper, c, c.getBlockPos()), "C no limite com ticket");
                    helper.assertValueEqual(RouterChunkLoader.get().forcedChunks(owner), 1, "chunks do dono");
                    a.setUpgrade(ItemStack.EMPTY, null);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    RouterBlockEntity c = far.get(0);
                    assertState(helper, c, ChunkLoadState.LIMIT, "C com B ainda no chunk");
                    helper.assertTrue(hasTicket(helper, b, b.getBlockPos()), "B perdeu o ticket");
                    b.setUpgrade(ItemStack.EMPTY, null);
                })
                .thenWaitUntil(() -> assertState(helper, far.get(0), ChunkLoadState.ACTIVE, "C depois de liberar"))
                .thenExecute(() -> {
                    RouterBlockEntity c = far.get(0);
                    helper.assertTrue(hasTicket(helper, c, c.getBlockPos()), "C ativo sem ticket");
                    helper.assertValueEqual(RouterChunkLoader.get().forcedChunks(owner), 1, "chunks do dono");
                    c.setUpgrade(ItemStack.EMPTY, null);
                    level.removeBlock(c.getBlockPos(), false);
                    level.removeBlock(farMachine, false);
                    helper.assertFalse(RouterChunkLoader.hasTicket(level, c.getBlockPos(), farChunk.toLong()),
                            "ticket de C ficou");
                    helper.assertValueEqual(RouterChunkLoader.get().forcedChunks(owner), 0, "chunks do dono no fim");
                    level.setChunkForced(farChunk.x, farChunk.z, false);
                    RouterChunkLoader.overrideLimit(owner, null);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void recipe(GameTestHelper helper) {
        ItemLike o = Items.OBSIDIAN;
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemLike item : new ItemLike[] {o, Items.ENDER_EYE, o, Items.REDSTONE, Items.DIAMOND, Items.REDSTONE, o, o, o}) {
            stacks.add(new ItemStack(item));
        }
        CraftingInput input = CraftingInput.of(3, 3, stacks);
        RecipeHolder<CraftingRecipe> holder = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new GameTestAssertException("nenhuma receita para o upgrade"));
        helper.assertValueEqual(holder.id(), WirelessAutomate.id("chunk_loader_upgrade"), "receita");
        ItemStack out = holder.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(ModItems.CHUNK_LOADER_UPGRADE.get()) && out.getCount() == 1, "resultado: " + out);
        helper.succeed();
    }
}
