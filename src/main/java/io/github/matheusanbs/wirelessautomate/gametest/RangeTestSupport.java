package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Apoio dos GameTests do roteador de alcance ({@code Range*GameTests}): o Upgrade de Alcance, o roteador num baú
 * com máquinas vinculadas, a tela aberta por um jogador de teste e as consultas às máquinas e aos grupos.
 */
final class RangeTestSupport {
    private RangeTestSupport() {
    }

    static final int CONTAINER_ID = 53;

    static ItemStack rangeUpgrade() {
        return new ItemStack(ModItems.RANGE_UPGRADE.get());
    }

    /** Baú em (1, 1, 1) e roteador do tier {@code tier} preso nele por cima. */
    static RouterBlockEntity chestWithRouter(GameTestHelper helper, RouterTier tier) {
        BlockPos machine = new BlockPos(1, 1, 1);
        helper.setBlock(machine, Blocks.CHEST);
        helper.setBlock(machine.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(machine.above());
    }

    static RouterMenu openMenu(GameTestHelper helper, RouterBlockEntity router, ServerPlayer[] out) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        RouterMenu menu = new RouterMenu(CONTAINER_ID, player.getInventory(), router, RouterSnapshot.capture(router, player));
        player.containerMenu = menu;
        out[0] = player;
        return menu;
    }

    static final BlockPos FURNACE_A = new BlockPos(0, 1, 0);
    static final BlockPos FURNACE_B = new BlockPos(2, 1, 0);
    static final BlockPos BARREL = new BlockPos(0, 1, 2);
    static final BlockPos ROUTER = new BlockPos(1, 2, 1);

    /**
     * Roteador Avançado numa rede própria, preso no baú, com duas fornalhas (frente Leste e Sul) e um
     * barril virado para cima (sem frente horizontal) em volta.
     */
    static RouterBlockEntity routerWithMachines(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        router.setNetworkId(NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "alcance").id());
        helper.setBlock(FURNACE_A, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
        helper.setBlock(FURNACE_B, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH));
        helper.setBlock(BARREL, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        return router;
    }

    static void waitLoaded(GameTestHelper helper, RouterBlockEntity router) {
        helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
    }

    static void link(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        helper.assertTrue(router.linkMachine(helper.absolutePos(relative)), "não vinculou " + relative);
    }

    /**
     * Vincula e põe todas as faces (presa, perfis e exceções) na rede da presa: a rede é da face e o
     * grupo novo nasce sem rede, então a remota não pegaria a rede do roteador sozinha.
     */
    static void linkInNetwork(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        link(helper, router, relative);
        UUID network = router.networkId(ResourceType.ITEM);
        helper.assertTrue(network != null, "o roteador não está numa rede");
        router.setNetworkId(network);
    }

    static ItemStack itemCard() {
        ItemStack card = new ItemStack(ModItems.FILTER_CARD.get());
        FilterCardItem.setContents(card, new FilterCardItem.Contents(ResourceType.ITEM, Filter.EMPTY));
        return card;
    }

    static int groupOf(RouterBlockEntity router, BlockPos absolute) {
        RemoteMachines remotes = router.remoteMachines();
        return remotes.machine(remotes.index(absolute)).group();
    }

    /** Posição relativa da máquina remota a 4 blocos do roteador (em cima, fora do template). */
    static final BlockPos REMOTE_CHEST = new BlockPos(1, 6, 1);
    static final BlockPos CHEST = new BlockPos(1, 1, 1);

    /** Roteador Avançado preso no baú, numa rede própria (sem o upgrade ainda: só depois do {@code onLoad}). */
    static RouterBlockEntity rangeRouter(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        router.setNetworkId(NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "motor").id());
        return router;
    }

    static Container container(GameTestHelper helper, BlockPos relative) {
        return helper.getBlockEntity(relative);
    }

    static int count(GameTestHelper helper, BlockPos relative) {
        Container container = container(helper, relative);
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            total += container.getItem(i).getCount();
        }
        return total;
    }

    /** O grupo da máquina em {@code relative} recebe o modo de itens no lado canônico {@code side}. */
    static void profile(GameTestHelper helper, RouterBlockEntity router, BlockPos relative, Direction side,
            PortMode mode) {
        int group = groupOf(router, helper.absolutePos(relative));
        router.configureFace(EditTarget.group(group), ResourceType.ITEM, side, mode, 0, RedstoneMode.IGNORE);
    }

    static boolean remotePort(GameTestHelper helper, RouterBlockEntity router, BlockPos relative, Direction face) {
        return NetworkManager.get().hasRemotePort(router, helper.absolutePos(relative), ResourceType.ITEM, face);
    }

    static int countOf(GameTestHelper helper, BlockPos relative, net.minecraft.world.item.Item item) {
        Container container = container(helper, relative);
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static ItemStack machinesLinker() {
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        LinkerItem.setMode(linker, LinkerMode.MACHINES);
        return linker;
    }

    static ServerPlayer linkerPlayer(GameTestHelper helper, ItemStack linker) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, linker);
        return player;
    }

    static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        player.containerMenu = player.inventoryMenu;
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /** Clique direito com o item da mão principal num bloco (relativo), pelo caminho do jogo. */
    static void click(GameTestHelper helper, ServerPlayer player, BlockPos relative, boolean sneak) {
        player.setShiftKeyDown(sneak);
        BlockPos pos = helper.absolutePos(relative);
        ItemStack stack = player.getMainHandItem();
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        if (!stack.onItemUseFirst(context).consumesAction()) {
            stack.useOn(context);
        }
        player.setShiftKeyDown(false);
    }

    static RangeLinks.Result toggle(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        return RangeLinks.toggle(player, player.getMainHandItem(), helper.absolutePos(relative));
    }

    /** Confere a rede de todas as faces de todos os tipos carregados do perfil do grupo da máquina. */
    static void assertProfileNetwork(GameTestHelper helper, RouterBlockEntity router, BlockPos relative,
            @Nullable UUID expected, String what) {
        MachineConfig profile = router.remoteMachines().group(groupOf(router, helper.absolutePos(relative))).profile();
        for (ResourceType type : LoadedTypes.LIST) {
            for (RemoteSide side : RemoteSide.values()) {
                UUID actual = profile.face(type, side).network();
                helper.assertTrue(Objects.equals(actual, expected),
                        what + " (" + type + ", " + side + "): esperava " + expected + ", veio " + actual);
            }
        }
    }

    /** Rede nova do jogador, ativa para ele. */
    static UUID activeNetworkOf(GameTestHelper helper, ServerPlayer player, String name) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID id = data.create(player.getUUID(), name).id();
        data.setActiveNetwork(player.getUUID(), id);
        return id;
    }
}
