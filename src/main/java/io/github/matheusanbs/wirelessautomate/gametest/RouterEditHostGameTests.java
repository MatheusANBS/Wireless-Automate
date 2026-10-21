package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.EditTargetRef;
import io.github.matheusanbs.wirelessautomate.menu.RouterEditHost;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.FaceActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.SetNetworkPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Os pacotes de face passam por qualquer {@link RouterEditHost}, não só pela tela do roteador: um menu
 * de teste sem slots, que aponta um roteador, edita as faces e é recusado quando {@code canEdit} nega.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RouterEditHostGameTests {
    private static final int CONTAINER_ID = 77;

    /** Menu sem slots que só implementa o host. */
    private static final class TestHost extends AbstractContainerMenu implements RouterEditHost {
        private final RouterBlockEntity router;
        boolean allowed = true;
        int applied;

        TestHost(int id, RouterBlockEntity router) {
            super(null, id);
            this.router = router;
        }

        @Override
        public int containerId() {
            return containerId;
        }

        @Override
        public RouterBlockEntity editedRouter() {
            return router;
        }

        @Override
        public boolean canEdit(ServerPlayer player) {
            return allowed;
        }

        @Override
        public EditTargetRef targetRef() {
            return EditTargetRef.ATTACHED;
        }

        @Override
        public EditTarget actionTarget() {
            return EditTarget.ATTACHED_TARGET;
        }

        @Override
        public EditTarget validTarget() {
            return EditTarget.ATTACHED_TARGET;
        }

        @Override
        public boolean selectTarget(EditTarget target, @Nullable BlockPos pos, @Nullable ResourceLocation block) {
            return false;
        }

        @Override
        public void editApplied() {
            applied++;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    @GameTest(template = "empty")
    public static void facePayloadsWorkThroughAnyHost(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos);
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        TestHost host = new TestHost(CONTAINER_ID, router);
        player.containerMenu = host;
        try {
            helper.assertTrue(ModPayloads.handleSetFace(player, new SetFacePayload(CONTAINER_ID, ResourceType.ITEM,
                    Direction.UP, PortMode.INSERT, 7, RedstoneMode.LOW)), "recusou o SetFace pelo host");
            helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT, "modo");
            helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).priority(), 7, "prioridade");

            WaNetwork network = NetworkSavedData.get(player.server).create(player.getUUID(), "host-teste");
            helper.assertTrue(ModPayloads.handleSetNetwork(player, new SetNetworkPayload(CONTAINER_ID,
                    ResourceType.ITEM, Direction.UP, Optional.of(network.id()))), "recusou o SetNetwork pelo host");
            helper.assertTrue(Objects.equals(router.faceNetwork(ResourceType.ITEM, Direction.UP), network.id()),
                    "a rede da face não mudou");
            helper.assertTrue(host.applied >= 2, "o host não foi avisado das edições");

            host.allowed = false;
            helper.assertFalse(ModPayloads.handleSetFace(player, new SetFacePayload(CONTAINER_ID, ResourceType.ITEM,
                    Direction.UP, PortMode.EXTRACT, 0, RedstoneMode.IGNORE)), "aceitou com canEdit falso");
            helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT,
                    "a face mudou sem permissão");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    /** A edição do quadro das faces, aberta pelo host: um clique de cada vez, o modo anda na ordem do {@code ModeCycle}. */
    @GameTest(template = "empty")
    public static void faceClicksWalkTheModesThroughTheHost(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos);
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        player.containerMenu = new TestHost(CONTAINER_ID, router);
        try {
            PortMode mode = router.face(ResourceType.ITEM, Direction.DOWN).mode();
            helper.assertValueEqual(mode, PortMode.NONE, "modo inicial");
            PortMode[] walk = {PortMode.EXTRACT, PortMode.INSERT, PortMode.BOTH, PortMode.NONE, PortMode.EXTRACT};
            for (PortMode expected : walk) {
                PortMode next = io.github.matheusanbs.wirelessautomate.client.router.ModeCycle.next(mode);
                helper.assertValueEqual(next, expected, "ordem do ciclo");
                helper.assertTrue(ModPayloads.handleSetFace(player, new SetFacePayload(CONTAINER_ID, ResourceType.ITEM,
                        Direction.DOWN, next, 0, RedstoneMode.IGNORE)), "recusou o modo " + next);
                mode = router.face(ResourceType.ITEM, Direction.DOWN).mode();
                helper.assertValueEqual(mode, expected, "modo no servidor");
            }
            helper.assertValueEqual(io.github.matheusanbs.wirelessautomate.client.router.ModeCycle.previous(mode),
                    PortMode.NONE, "o clique direito volta");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    /** Limpar põe as faces do tipo em Nenhum (e, com todos os tipos, as dos outros também), numa remontagem só. */
    @GameTest(template = "empty")
    public static void faceActionClearsOneTypeOrAllTypes(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos);
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        TestHost host = new TestHost(CONTAINER_ID, router);
        player.containerMenu = host;
        try {
            router.configureFace(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 3, RedstoneMode.LOW);
            router.configureFace(ResourceType.ITEM, Direction.DOWN, PortMode.INSERT, 0, RedstoneMode.IGNORE);
            router.configureFace(ResourceType.FLUID, Direction.UP, PortMode.BOTH, 0, RedstoneMode.IGNORE);
            int version = router.changeVersion();
            helper.assertTrue(ModPayloads.handleFaceAction(player,
                    FaceActionPayload.clear(CONTAINER_ID, ResourceType.ITEM, false)), "o Limpar não mudou nada");
            helper.assertValueEqual(router.changeVersion(), version + 1, "uma mudança só");
            helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.NONE, "item em cima");
            helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.DOWN).mode(), PortMode.NONE, "item embaixo");
            helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).priority(), 3, "só o modo muda");
            helper.assertValueEqual(router.face(ResourceType.FLUID, Direction.UP).mode(), PortMode.BOTH,
                    "o outro tipo não podia mudar");
            helper.assertTrue(ModPayloads.handleFaceAction(player,
                    FaceActionPayload.clear(CONTAINER_ID, ResourceType.ITEM, true)), "o Limpar de todos não mudou nada");
            helper.assertValueEqual(router.face(ResourceType.FLUID, Direction.UP).mode(), PortMode.NONE, "fluido");
            helper.assertFalse(ModPayloads.handleFaceAction(player,
                    FaceActionPayload.clear(CONTAINER_ID, ResourceType.ITEM, true)), "limpou o que já estava limpo");

            router.configureFace(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0, RedstoneMode.IGNORE);
            host.allowed = false;
            helper.assertFalse(ModPayloads.handleFaceAction(player,
                    FaceActionPayload.clear(CONTAINER_ID, ResourceType.ITEM, true)), "aceitou com canEdit falso");
            helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.EXTRACT,
                    "limpou sem permissão");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    /** Colar copia modo, prioridade, redstone, filtro e rede de uma face para outra do mesmo tipo. */
    @GameTest(template = "empty")
    public static void faceActionPastesTheWholeFace(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos);
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        TestHost host = new TestHost(CONTAINER_ID, router);
        player.containerMenu = host;
        try {
            WaNetwork network = NetworkSavedData.get(player.server).create(player.getUUID(), "colar-teste");
            router.configureFace(ResourceType.ITEM, Direction.UP, PortMode.INSERT, 9, RedstoneMode.HIGH);
            router.setFaceNetwork(ResourceType.ITEM, Direction.UP, network.id());
            helper.assertFalse(ModPayloads.handleFaceAction(player, FaceActionPayload.paste(CONTAINER_ID,
                    ResourceType.ITEM, Direction.UP, Direction.UP)), "colou a face nela mesma");
            helper.assertTrue(ModPayloads.handleFaceAction(player, FaceActionPayload.paste(CONTAINER_ID,
                    ResourceType.ITEM, Direction.UP, Direction.NORTH)), "recusou o Colar");
            var pasted = router.face(ResourceType.ITEM, Direction.NORTH);
            helper.assertValueEqual(pasted.mode(), PortMode.INSERT, "modo");
            helper.assertValueEqual(pasted.priority(), 9, "prioridade");
            helper.assertValueEqual(pasted.redstone(), RedstoneMode.HIGH, "redstone");
            helper.assertTrue(Objects.equals(pasted.network(), network.id()), "rede");
            helper.assertValueEqual(router.face(ResourceType.FLUID, Direction.NORTH).mode(), PortMode.NONE,
                    "o outro tipo não podia mudar");
            host.allowed = false;
            helper.assertFalse(ModPayloads.handleFaceAction(player, FaceActionPayload.paste(CONTAINER_ID,
                    ResourceType.ITEM, Direction.UP, Direction.EAST)), "colou com canEdit falso");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }
}
