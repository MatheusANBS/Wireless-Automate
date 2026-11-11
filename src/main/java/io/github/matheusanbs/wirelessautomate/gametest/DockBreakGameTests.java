package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockPendingContents;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A Doca quebrada com pendente: o item leva o {@code pattern_dock_pending} (itens, fluidos e químicos de todos os
 * lados, com o bloco de cada um; o químico sem o Mekanism, cru), a dica diz "Pedido pendente", e recolocada a Doca
 * devolve o pendente aos mesmos lados. Sem pendente, o item não leva o componente e empilha.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockBreakGameTests {
    private static final BlockPos DOCK = new BlockPos(2, 1, 2);
    private static final BlockPos PLACED = new BlockPos(5, 1, 5);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final int SOUTH = Direction.SOUTH.ordinal();
    private static final ResourceLocation FURNACE = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);

    private DockBreakGameTests() {
    }

    private static PatternDockBlockEntity dock(GameTestHelper helper) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.ELITE));
        return helper.getBlockEntity(DOCK);
    }

    /** As Docas no chão perto de {@code relative}, tiradas do mundo; o teste confere quantas são. */
    private static List<ItemStack> takeDocks(GameTestHelper helper, BlockPos relative) {
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(relative)).inflate(2),
                entity -> entity.getItem().is(ModBlocks.PATTERN_DOCK.get().asItem()));
        List<ItemStack> stacks = drops.stream().map(entity -> entity.getItem().copy()).toList();
        drops.forEach(ItemEntity::discard);
        return stacks;
    }

    /** Quantos itens de {@code item} há no chão perto de {@code relative}. */
    private static int onGround(GameTestHelper helper, BlockPos relative, Item item) {
        int count = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(relative)).inflate(2), entity -> entity.getItem().is(item))) {
            count += entity.getItem().getCount();
        }
        return count;
    }

    /** Coloca o item da Doca em {@code relative} como o {@code BlockItem} faz, clicando na pedra embaixo. */
    private static PatternDockBlockEntity place(GameTestHelper helper, ItemStack stack, BlockPos relative) {
        helper.setBlock(relative.below(), Blocks.STONE);
        BlockPos floor = helper.absolutePos(relative).below();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        ((BlockItem) stack.getItem()).place(new BlockPlaceContext(helper.getLevel(), null, InteractionHand.MAIN_HAND,
                stack, hit));
        helper.assertBlockPresent(ModBlocks.PATTERN_DOCK.get(), relative);
        return helper.getBlockEntity(relative);
    }

    private static int count(List<ItemStack> stacks, Item item) {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Quebrada com pendente de itens no Norte (150 pedregulhos, salvos em partes, e 3 carvões) e de fluido no Sul, a
     * Doca vira um item com o componente e nada cai no chão; recolocada, cada lado volta com o bloco e o pendente, sem
     * máquinas, e recusa outro bloco.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pendingGoesInItemAndComesBack(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper);
        BlockPos north = helper.absolutePos(new BlockPos(2, 1, 0));
        BlockPos south = helper.absolutePos(new BlockPos(2, 1, 4));
        dock.link(NORTH, north, FURNACE, -1);
        dock.link(SOUTH, south, FURNACE, -1);
        dock.side(NORTH).setPending(List.of(new ItemStack(Items.COBBLESTONE, 150), new ItemStack(Items.COAL, 3)),
                List.of(), north);
        dock.side(SOUTH).setPending(List.of(), List.of(new FluidStack(Fluids.WATER, 1_000)), south);
        helper.getLevel().destroyBlock(helper.absolutePos(DOCK), true);

        helper.assertValueEqual(onGround(helper, DOCK, Items.COBBLESTONE), 0, "pedregulho no chão");
        helper.assertValueEqual(onGround(helper, DOCK, Items.COAL), 0, "carvão no chão");
        List<ItemStack> docks = takeDocks(helper, DOCK);
        helper.assertValueEqual(docks.size(), 1, "Docas no chão");
        ItemStack item = docks.get(0);
        DockPendingContents contents = item.get(ModDataComponents.PATTERN_DOCK_PENDING.get());
        helper.assertTrue(contents != null && contents.sides().size() == 2, "componente: " + contents);
        for (DockPendingContents.Side saved : contents.sides()) {
            helper.assertTrue(saved.block().equals(FURNACE), "o bloco do lado: " + saved.block());
            if (saved.side() == NORTH) {
                helper.assertTrue(count(saved.items(), Items.COBBLESTONE) == 150
                        && count(saved.items(), Items.COAL) == 3 && saved.fluids().isEmpty(), "Norte: " + saved);
            } else {
                helper.assertTrue(saved.side() == SOUTH && saved.items().isEmpty() && saved.fluids().size() == 1
                        && saved.fluids().get(0).is(Fluids.WATER) && saved.fluids().get(0).getAmount() == 1_000,
                        "Sul: " + saved);
            }
        }
        List<Component> tooltip = new ArrayList<>();
        item.getItem().appendHoverText(item, Item.TooltipContext.of(helper.getLevel()), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.stream().anyMatch(line -> line.getString().equals(
                Component.translatable("block.wirelessautomate.pattern_dock.pending").getString())),
                "a dica sem o pendente: " + tooltip);

        PatternDockBlockEntity placed = place(helper, item, PLACED);
        DockSide placedNorth = placed.side(NORTH);
        DockSide placedSouth = placed.side(SOUTH);
        helper.assertTrue(placedNorth != null && FURNACE.equals(placedNorth.block())
                && placedNorth.machines().isEmpty() && count(placedNorth.pendingItems(), Items.COBBLESTONE) == 150
                && count(placedNorth.pendingItems(), Items.COAL) == 3, "Norte recolocado");
        helper.assertTrue(placedSouth != null && FURNACE.equals(placedSouth.block())
                && placedSouth.pendingFluids().size() == 1
                && placedSouth.pendingFluids().get(0).getAmount() == 1_000, "Sul recolocado");
        helper.assertFalse(placed.link(NORTH, helper.absolutePos(new BlockPos(5, 1, 3)),
                BuiltInRegistries.BLOCK.getKey(Blocks.BLAST_FURNACE), -1), "o lado com pendente aceitou outro bloco");
        helper.succeed();
    }

    /** No sobrevivência, com a picareta (a loot table roda depois de tirar o bloco), o fluido pendente vai no item. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void survivalBreakKeepsPendingFluid(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper);
        BlockPos south = helper.absolutePos(new BlockPos(2, 1, 4));
        dock.link(SOUTH, south, FURNACE, -1);
        dock.side(SOUTH).setPending(List.of(), List.of(new FluidStack(Fluids.LAVA, 250)), south);
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(DOCK)), "não quebrou a Doca");
        List<ItemStack> docks = takeDocks(helper, DOCK);
        helper.assertValueEqual(docks.size(), 1, "Docas no chão");
        DockPendingContents contents = docks.get(0).get(ModDataComponents.PATTERN_DOCK_PENDING.get());
        helper.assertTrue(contents != null && contents.sides().size() == 1
                && contents.sides().get(0).fluids().get(0).is(Fluids.LAVA)
                && contents.sides().get(0).fluids().get(0).getAmount() == 250, "componente: " + contents);
        helper.succeed();
    }

    /** Sem pendente (com máquinas), o item não leva o componente e empilha com uma Doca nova do mesmo tier. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void dockWithoutPendingStacks(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper);
        dock.link(NORTH, helper.absolutePos(new BlockPos(2, 1, 0)), FURNACE, -1);
        helper.getLevel().destroyBlock(helper.absolutePos(DOCK), true);
        List<ItemStack> docks = takeDocks(helper, DOCK);
        helper.assertValueEqual(docks.size(), 1, "Docas no chão");
        ItemStack item = docks.get(0);
        helper.assertFalse(item.has(ModDataComponents.PATTERN_DOCK_PENDING.get()), "a Doca sem pendente ganhou o componente");
        ItemStack fresh = StorageBlockItem.withTier(ModBlocks.PATTERN_DOCK.get().asItem(), RouterTier.ELITE);
        helper.assertTrue(ItemStack.isSameItemSameComponents(item, fresh), "não empilha com uma Doca nova");
        helper.succeed();
    }

    /**
     * Sem o Mekanism, um químico pendente que veio no item (a entrada {@code {chemical}}) fica cru: não segura o lado,
     * mas o lado guarda o bloco, o NBT o salva igual e o item da Doca o leva de novo, sem descartar.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rawChemicalKeptWithoutMekanism(GameTestHelper helper) {
        if (Chemicals.LOADED) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        CompoundTag chemical = new CompoundTag();
        chemical.putString("id", "mekanism:hydrogen");
        chemical.putLong("amount", 100);
        ItemStack item = StorageBlockItem.withTier(ModBlocks.PATTERN_DOCK.get().asItem(), RouterTier.ELITE);
        item.set(ModDataComponents.PATTERN_DOCK_PENDING.get(), new DockPendingContents(List.of(
                new DockPendingContents.Side(NORTH, FURNACE, List.of(), List.of(), List.of(chemical)))));
        dock.applyComponentsFromItemStack(item);
        DockSide side = dock.side(NORTH);
        helper.assertTrue(side != null && FURNACE.equals(side.block()), "o lado não guardou o bloco");
        helper.assertFalse(side.hasPending(), "a entrada crua segura o lado");
        helper.assertTrue(side.keepsPending() && dock.hasPending(), "a entrada crua sumiu");
        CompoundTag saved = dock.saveWithoutMetadata(helper.getLevel().registryAccess());
        ListTag pending = saved.getList("sides", Tag.TAG_COMPOUND).getCompound(0).getList("pending", Tag.TAG_COMPOUND);
        helper.assertTrue(pending.size() == 1 && chemical.equals(pending.getCompound(0).getCompound("chemical")),
                "NBT: " + pending);
        DockPendingContents contents = dock.collectComponents().get(ModDataComponents.PATTERN_DOCK_PENDING.get());
        helper.assertTrue(contents != null && contents.sides().size() == 1
                && contents.sides().get(0).chemicals().equals(List.of(chemical)), "componente: " + contents);
        helper.succeed();
    }
}
