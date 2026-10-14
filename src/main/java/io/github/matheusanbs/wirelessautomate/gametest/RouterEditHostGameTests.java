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

    @SuppressWarnings("removal")
    @GameTest(template = "empty")
    public static void facePayloadsWorkThroughAnyHost(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
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
}
