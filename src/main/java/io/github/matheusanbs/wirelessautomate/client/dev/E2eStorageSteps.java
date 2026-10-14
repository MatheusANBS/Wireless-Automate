package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSourceTankSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUpgradeCardSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageListScreen;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageScalarScreen;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageTankBlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Os armazenamentos do mod no e2e: o Baú Wireless (transporte, tela, busca, alça, filtro de entrada, Cartão de
 * Upgrade, quebrar e recolocar), o Tanque e a Bateria.
 */
final class E2eStorageSteps {
    private E2eStorageSteps() {
    }

    /** Total guardado nos dois Baús do roteiro: 12 milhões de pedregulho, 64 diamantes e 1.000 de terra. */
    private static final long CHEST_TOTAL = 12_000_000L + 64 + 1_000;

    /** O Baú A é Esmeralda e sobe para o tier seguinte: o Ultimate, ou o Allthemodium com o mod. */
    private static RouterTier chestUpgrade() {
        return java.util.Objects.requireNonNull(RouterTier.EMERALD.next());
    }
    private static BlockPos storageA = BlockPos.ZERO;
    /** A Bateria do passo dos armazenamentos, que a volta no escuro abre de novo. */
    static BlockPos batteryPos = BlockPos.ZERO;
    static BlockPos storageB = BlockPos.ZERO;

    /**
     * Baú Wireless num mundo real: dois Baús (Elite e Ultimate) com roteadores Ultimate na rede
     * principal; os 12 milhões de pedregulho do teste do dono passam de um para o outro. Depois a
     * tela: lista, busca, pegar e devolver uma pilha pelo clique, o filtro de entrada e Voltar. Por
     * fim o Cartão de Upgrade no Baú, quebrar e recolocar com o conteúdo e uma foto dos blocos.
     */
    static void storageChestSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        boolean[] clicked = new boolean[3];
        chestTransferSteps(list, playerId);
        chestScreenSteps(list);
        chestResizeSteps(list, playerId, clicked);
        chestFilterSteps(list);
        chestCardSteps(list, playerId, clicked);
        chestBreakSteps(list, playerId, clicked);
        tankAndBatterySteps(list, playerId);
    }

    /** Baú: os 12 milhões de pedregulho passam de um Baú para o outro. */
    private static void chestTransferSteps(List<Step> list, UUID playerId) {
        long[] started = new long[1];
        list.add(new Step("Baú: 12 milhões de um Baú para outro", 30_000, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            BlockPos base = player.blockPosition();
            // Ao alcance do clique (o servidor recusa de longe) e longe dos baús A e B do roteiro.
            storageA = base.offset(-2, 0, -1);
            storageB = base.offset(2, 0, -1);
            level.setBlockAndUpdate(storageA, ModBlocks.STORAGE_CHEST.get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.EMERALD));
            level.setBlockAndUpdate(storageB, ModBlocks.STORAGE_CHEST.get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
            ItemStorage storage = storageChest(server, storageA).storage();
            storage.insert(new ItemStack(Items.COBBLESTONE), 12_000_000L, false);
            storage.insert(new ItemStack(Items.DIAMOND), 64, false);
            storage.insert(new ItemStack(Items.DIRT), 1_000, false);
            for (BlockPos chest : List.of(storageA, storageB)) {
                level.setBlockAndUpdate(chest.above(), ModBlocks.ROUTER.get().defaultBlockState()
                        .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
                RouterBlockEntity router = router(server, chest.above());
                router.setNetworkId(ResourceType.ITEM, mainNetwork);
                router.setMode(ResourceType.ITEM, Direction.UP, chest == storageA ? PortMode.EXTRACT : PortMode.INSERT);
            }
            started[0] = server.getTickCount();
            return null;
        }), () -> onServer(server -> storageChest(server, storageA).storage().isEmpty()
                && storageChest(server, storageB).storage().total() == CHEST_TOTAL),
                () -> onServer(server -> "A=" + storageChest(server, storageA).storage().total() + " B="
                        + storageChest(server, storageB).storage().total() + " em "
                        + (server.getTickCount() - started[0]) + " ticks")));
    }

    /** Baú: a tela, a busca, o ingrediente do JEI e pegar e devolver uma pilha pelo clique. */
    private static void chestScreenSteps(List<Step> list) {
        list.add(new Step("Baú: abrir a tela", STEP_TIMEOUT_MS, () -> useOn(storageB),
                () -> Minecraft.getInstance().screen instanceof StorageListScreen screen
                        && screen.getMenu().view().types() == 3 && screen.getMenu().view().header().total() == CHEST_TOTAL,
                () -> Minecraft.getInstance().screen instanceof StorageListScreen screen
                        ? "tipos " + screen.getMenu().view().types() + ", total " + screen.getMenu().view().header().total()
                        : "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("bau-1-tela"));
        list.add(new Step("Baú: busca", STEP_TIMEOUT_MS, () -> chestScreen().searchBox().setValue("diam"),
                () -> chestScreen().shownCount() == 1, () -> "na lista " + chestScreen().shownCount()));
        list.add(capture("bau-2-busca"));
        list.add(new Step("Baú: ingrediente do JEI sob o mouse", STEP_TIMEOUT_MS, () -> { }, () -> {
            int[] cell = chestScreen().cellCenter(0);
            StorageListScreen.Hovered hovered = chestScreen().ingredientAt(cell[0], cell[1]);
            return hovered != null && hovered.ingredient() instanceof ItemStack stack && stack.is(Items.DIAMOND);
        }, () -> {
            int[] cell = chestScreen().cellCenter(0);
            return "sob o mouse " + chestScreen().ingredientAt(cell[0], cell[1]);
        }));
        list.add(new Step("Baú: pegar uma pilha pelo clique", STEP_TIMEOUT_MS, () -> {
            int[] cell = chestScreen().cellCenter(0);
            click(chestScreen(), cell[0], cell[1]);
        }, () -> chestScreen().getMenu().getCarried().is(Items.DIAMOND) && chestScreen().getMenu().getCarried().getCount() == 64
                && onServer(server -> storageChest(server, storageB).storage().count(new ItemStack(Items.DIAMOND)) == 0),
                () -> "cursor " + chestScreen().getMenu().getCarried()));
        list.add(new Step("Baú: devolver pelo clique na lista", STEP_TIMEOUT_MS, () -> {
            int[] cell = chestScreen().cellCenter(3);
            click(chestScreen(), cell[0], cell[1]);
        }, () -> chestScreen().getMenu().getCarried().isEmpty()
                && onServer(server -> storageChest(server, storageB).storage().total() == CHEST_TOTAL),
                () -> "cursor " + chestScreen().getMenu().getCarried()));
        list.add(new Step("Baú: busca por tag", STEP_TIMEOUT_MS, () -> chestScreen().searchBox().setValue("#c:gems"),
                () -> chestScreen().shownCount() == 1, () -> "na lista " + chestScreen().shownCount()));
        list.add(new Step("Baú: limpar a busca", STEP_TIMEOUT_MS, () -> chestScreen().searchBox().setValue(""),
                () -> chestScreen().shownCount() == 3, () -> "na lista " + chestScreen().shownCount()));
    }

    /** Baú: aumentar e diminuir pela alça, com o Shift + clique no inventário na posição nova. */
    private static void chestResizeSteps(List<Step> list, UUID playerId, boolean[] clicked) {
        int[] before = new int[2];
        list.add(new Step("Baú: aumentar pela alça (+4 colunas, +2 linhas)", STEP_TIMEOUT_MS, () -> {
            StorageListScreen screen = chestScreen();
            before[0] = screen.cols();
            before[1] = screen.rows();
            // Cresce em torno do centro: cada lado anda o que o mouse andou, então 2 células de
            // mouse dão 4 colunas.
            dragGrip(screen, 2 * 18, 18);
        }, () -> chestScreen().cols() == before[0] + 4 && chestScreen().rows() == before[1] + 2
                && inventoryFollows(chestScreen()),
                () -> "grade " + chestScreen().cols() + "x" + chestScreen().rows() + ", antes " + before[0] + "x" + before[1]));
        list.add(capture("bau-6-maior"));
        list.add(new Step("Baú: Shift + clique no inventário na posição nova", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).getInventory().setItem(9, new ItemStack(Items.EMERALD, 5));
                return null;
            });
        }, () -> {
            StorageListScreen screen = chestScreen();
            Slot slot = screen.getMenu().slots.stream().filter(s -> s.getContainerSlot() == 9).findFirst().orElseThrow();
            if (!clicked[2]) {
                if (!slot.getItem().is(Items.EMERALD)) {
                    return false;
                }
                clicked[2] = true;
                // O mesmo pacote do Shift + clique da tela (que lê o teclado de verdade), pelo índice do
                // slot trocado: ele precisa apontar para o mesmo slot no servidor.
                Minecraft.getInstance().gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0,
                        ClickType.QUICK_MOVE, Minecraft.getInstance().player);
            }
            return onServer(server -> storageChest(server, storageB).storage().count(new ItemStack(Items.EMERALD)) == 5);
        }, () -> onServer(server -> "esmeraldas no Baú " + storageChest(server, storageB).storage().count(new ItemStack(Items.EMERALD)))));
        list.add(new Step("Baú: diminuir até o mínimo pela alça", STEP_TIMEOUT_MS,
                () -> dragGrip(chestScreen(), -40 * 18, -40 * 18),
                () -> chestScreen().cols() == 9 && chestScreen().rows() == 3 && inventoryFollows(chestScreen()),
                () -> "grade " + chestScreen().cols() + "x" + chestScreen().rows()));
        list.add(capture("bau-7-minimo"));
        list.add(new Step("Baú: tamanho padrão de volta", STEP_TIMEOUT_MS,
                () -> dragGrip(chestScreen(), (before[0] - 9) * 9, (before[1] - 3) * 9),
                () -> chestScreen().cols() == before[0] && chestScreen().rows() == before[1],
                () -> "grade " + chestScreen().cols() + "x" + chestScreen().rows()));
    }

    /** Baú: o filtro de entrada e Voltar, e o Shift que trava a ordem da lista. */
    private static void chestFilterSteps(List<Step> list) {
        list.add(new Step("Baú: abrir o filtro de entrada", STEP_TIMEOUT_MS, () -> {
                    chestScreen().searchBox().setValue("diam");
                    click(widget(byMessage(Component.translatable("gui.wirelessautomate.storage.filter")), "botão Filtro"));
                },
                () -> Minecraft.getInstance().screen instanceof FilterScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("bau-3-filtro"));
        list.add(new Step("Baú: voltar do filtro", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.back")), "Voltar")),
                () -> Minecraft.getInstance().screen instanceof StorageListScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("Baú: a busca volta vazia", STEP_TIMEOUT_MS, () -> { },
                () -> chestScreen().searchBox().getValue().isEmpty() && chestScreen().shownCount() == 4,
                () -> "busca '" + chestScreen().searchBox().getValue() + "', na lista " + chestScreen().shownCount()));
        int[] diamondAt = {-1};
        list.add(new Step("Baú: Shift trava a ordem (o tipo que acaba fica no lugar)", STEP_TIMEOUT_MS, () -> {
            StorageListScreen screen = chestScreen();
            for (int i = 0; i < screen.shownCount(); i++) {
                if (screen.shownKey(i) instanceof ItemStack stack && stack.is(Items.DIAMOND)) {
                    diamondAt[0] = i;
                }
            }
            StorageListScreen.e2eShift = true;
            screen.shownCount();
            onServer(server -> storageChest(server, storageB).storage().extract(new ItemStack(Items.DIAMOND), 64, false));
        }, () -> chestScreen().getMenu().view().types() == 3 && chestScreen().shownCount() == 4
                && chestScreen().shownKey(diamondAt[0]) instanceof ItemStack stack && stack.is(Items.DIAMOND)
                && chestScreen().shownAmount(diamondAt[0]) == 0,
                () -> "tipos " + chestScreen().getMenu().view().types() + ", na lista " + chestScreen().shownCount()
                        + ", diamante em " + diamondAt[0]));
        list.add(new Step("Baú: soltar o Shift reordena", STEP_TIMEOUT_MS, () -> {
            StorageListScreen.e2eShift = false;
            onServer(server -> storageChest(server, storageB).storage().insert(new ItemStack(Items.DIAMOND), 64, false));
        }, () -> chestScreen().getMenu().view().types() == 4 && chestScreen().shownCount() == 4,
                () -> "tipos " + chestScreen().getMenu().view().types() + ", na lista " + chestScreen().shownCount()));
        list.add(close("Baú: fechar"));
    }

    /** Baú: o cartão de outra origem é recusado e o Cartão de Upgrade certo sobe o bloco. */
    private static void chestCardSteps(List<Step> list, UUID playerId, boolean[] clicked) {
        boolean[] wrongClicked = new boolean[1];
        int[] wrongAt = new int[1];
        list.add(new Step("Baú: cartão de outra origem é recusado", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND,
                        TierCoreItem.card(chestUpgrade(), RouterTier.BASIC));
                return null;
            });
        }, () -> {
            ItemStack hand = Minecraft.getInstance().player.getMainHandItem();
            if (!hand.is(ModItems.TIER_CORES.get(chestUpgrade()).get())) {
                return false;
            }
            if (!wrongClicked[0]) {
                wrongClicked[0] = true;
                wrongAt[0] = stepTicks;
                useOn(storageA);
            }
            return stepTicks - wrongAt[0] >= 10 && onServer(server -> {
                ItemStack held = server.getPlayerList().getPlayer(playerId).getMainHandItem();
                return server.overworld().getBlockState(storageA).getValue(RouterBlock.TIER) == RouterTier.EMERALD
                        && held.getCount() == 1 && TierCoreItem.origin(held) == RouterTier.BASIC;
            });
        }, () -> onServer(server -> "tier " + server.overworld().getBlockState(storageA).getValue(RouterBlock.TIER)
                + ", mão " + server.getPlayerList().getPlayer(playerId).getMainHandItem())));
        list.add(new Step("Baú: Cartão de Upgrade no bloco", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND,
                        TierCoreItem.card(chestUpgrade(), RouterTier.EMERALD));
                return null;
            });
        }, () -> {
            ItemStack hand = Minecraft.getInstance().player.getMainHandItem();
            if (!hand.is(ModItems.TIER_CORES.get(chestUpgrade()).get()) || TierCoreItem.origin(hand) != RouterTier.EMERALD) {
                return false;
            }
            if (!clicked[0]) {
                clicked[0] = true;
                useOn(storageA);
            }
            return onServer(server -> server.overworld().getBlockState(storageA).getValue(RouterBlock.TIER) == chestUpgrade());
        }, () -> onServer(server -> "tier " + server.overworld().getBlockState(storageA).getValue(RouterBlock.TIER))));
    }

    /** Baú: quebrar e recolocar com o conteúdo, o Cartão de Upgrade no inventário e a foto dos blocos. */
    private static void chestBreakSteps(List<Step> list, UUID playerId, boolean[] clicked) {
        list.add(new Step("Baú: quebrar e recolocar com o conteúdo", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            level.destroyBlock(storageB.above(), false);
            level.destroyBlock(storageB, true);
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(storageB).inflate(2),
                    entity -> entity.getItem().is(ModItems.STORAGE_CHEST.get()));
            if (drops.size() != 1) {
                throw new IllegalStateException("Baús no chão: " + drops.size());
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, drops.get(0).getItem().copy());
            drops.get(0).discard();
            return null;
        }), () -> {
            if (!Minecraft.getInstance().player.getMainHandItem().is(ModItems.STORAGE_CHEST.get())) {
                return false;
            }
            if (!clicked[1]) {
                clicked[1] = true;
                BlockPos floor = storageB.below();
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
                Minecraft.getInstance().gameMode.useItemOn(Minecraft.getInstance().player, InteractionHand.MAIN_HAND, hit);
            }
            // + as 5 esmeraldas guardadas pelo Shift + clique depois de redimensionar
            return onServer(server -> server.overworld().getBlockEntity(storageB) instanceof StorageChestBlockEntity chest
                    && chest.storage().total() == CHEST_TOTAL + 5);
        }, () -> onServer(server -> server.overworld().getBlockEntity(storageB) instanceof StorageChestBlockEntity chest
                ? "total " + chest.storage().total() : "sem Baú em " + storageB.toShortString())));
        list.add(capture("bau-4-tooltip-mao"));
        upgradeCardSteps(list);

        list.add(new Step("Baú: olhar os blocos", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerLevel level = player.serverLevel();
            level.setBlockAndUpdate(storageB.above(), ModBlocks.ROUTER.get().defaultBlockState()
                    .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
            BlockPos look = storageA.offset(2, 0, 4);
            player.teleportTo(level, look.getX() + 0.5, look.getY(), look.getZ() + 0.5, 180f, 25f);
            return null;
        }), () -> stepTicks >= 20, () -> "esperando o chunk desenhar"));
        list.add(capture("bau-5-blocos"));
    }

    /** O Tanque e a Bateria: a tela de cada um, com conteúdo. */
    private static void tankAndBatterySteps(List<Step> list, UUID playerId) {
        // Tanque e Bateria: a tela de cada um, com conteúdo.
        BlockPos[] more = new BlockPos[2];
        list.add(new Step("Tanque e Bateria: preparar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            BlockPos base = player.blockPosition();
            more[0] = base.offset(-1, 0, -2);
            more[1] = base.offset(1, 0, -2);
            level.setBlockAndUpdate(more[0], ModBlocks.STORAGE.get(StorageKind.TANK).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.EMERALD));
            level.setBlockAndUpdate(more[1], ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.EMERALD));
            batteryPos = more[1];
            StorageTankBlockEntity tank = (StorageTankBlockEntity) level.getBlockEntity(more[0]);
            tank.storage().insert(new FluidStack(Fluids.WATER, 1), 80_000_000L, false);
            tank.storage().insert(new FluidStack(Fluids.LAVA, 1), 48_000_000L, false);
            StorageBatteryBlockEntity battery = (StorageBatteryBlockEntity) level.getBlockEntity(more[1]);
            battery.store().insert(320_000_000L, false);
            return null;
        }), () -> {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.level != null && minecraft.level.getBlockState(more[0]).getBlock() instanceof StorageBlock
                    && minecraft.level.getBlockState(more[1]).getBlock() instanceof StorageBlock;
        }, () -> "blocos no cliente"));
        list.add(new Step("Tanque: abrir a tela", STEP_TIMEOUT_MS, () -> useOn(more[0]),
                () -> Minecraft.getInstance().screen instanceof StorageListScreen screen && screen.getMenu().view().types() == 2,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("tanque-1-tela"));
        list.add(new Step("Tanque: ingrediente do JEI sob o mouse", STEP_TIMEOUT_MS, () -> { }, () -> {
            if (!(Minecraft.getInstance().screen instanceof StorageListScreen screen)) {
                return false;
            }
            int[] cell = screen.cellCenter(0);
            StorageListScreen.Hovered hovered = screen.ingredientAt(cell[0], cell[1]);
            return hovered != null && hovered.ingredient() instanceof FluidStack;
        }, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(close("Tanque: fechar"));
        list.add(new Step("Bateria: abrir a tela", STEP_TIMEOUT_MS, () -> useOn(more[1]),
                () -> Minecraft.getInstance().screen instanceof StorageScalarScreen screen && screen.getMenu().received()
                        && screen.getMenu().stored() == 320_000_000L,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("bateria-1-tela"));
        list.add(close("Bateria: fechar"));
        if (StorageKind.SOURCE_TANK.loaded()) {
            scalarSourceSteps(list, () -> more[1].offset(2, 0, 0));
        }
    }

    /**
     * Depois de redimensionar: o painel continua no centro da tela e o inventário do jogador
     * acompanhou, centralizado embaixo dele.
     */
    private static boolean inventoryFollows(StorageListScreen screen) {
        Slot first = screen.getMenu().slots.stream().filter(s -> s.getContainerSlot() == 9).findFirst().orElseThrow();
        int center = first.x + 9 * 18 / 2 - 1;
        boolean centered = Math.abs(screen.getGuiLeft() + screen.getXSize() / 2 - screen.width / 2) <= 1
                && Math.abs(screen.getGuiTop() + screen.getYSize() / 2 - screen.height / 2) <= 1;
        return centered && Math.abs(center - screen.getXSize() / 2) <= 1
                && first.y + 3 * 18 + 4 + 18 + 8 + 8 >= screen.getYSize() - 8;
    }

    private static StorageListScreen chestScreen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof StorageListScreen screen) {
            return screen;
        }
        throw new StepFailure("a tela do Baú não está aberta: " + describe(Minecraft.getInstance().screen));
    }

    /** Na thread do servidor. */
    private static StorageChestBlockEntity storageChest(MinecraftServer server, BlockPos pos) {
        if (!(server.overworld().getBlockEntity(pos) instanceof StorageChestBlockEntity chest)) {
            throw new IllegalStateException("sem Baú Wireless em " + pos.toShortString());
        }
        return chest;
    }
}
