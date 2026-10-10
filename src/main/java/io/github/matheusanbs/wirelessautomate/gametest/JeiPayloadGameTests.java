package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterFaceFilterTarget;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.AddFilterEntryPayload;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Lado do servidor da integração com o JEI: o {@link AddFilterEntryPayload}, que acrescenta um
 * ingrediente fantasma ao filtro da tela aberta. O plugin em si é só do cliente e não é carregado
 * aqui; o handler é chamado direto, com o menu no {@code containerMenu} do jogador falso.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class JeiPayloadGameTests {
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);
    private static final int CONTAINER_ID = 47;

    private static RouterBlockEntity chestWithRouter(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        BlockPos pos = CHEST.above();
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        return GameTestCompat.getBlockEntity(helper, pos);
    }

    private static ServerPlayer playerNear(GameTestHelper helper, RouterBlockEntity router) {
        @SuppressWarnings("removal")
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        return player;
    }

    private static FilterMenu openFace(ServerPlayer player, RouterBlockEntity router, ResourceType type) {
        RouterFaceFilterTarget target = new RouterFaceFilterTarget(router, type, Direction.UP);
        FilterMenu menu = new FilterMenu(CONTAINER_ID, player.getInventory(), target, target.view(player));
        player.containerMenu = menu;
        return menu;
    }

    private static boolean add(ServerPlayer player, int containerId, FilterEntry entry) {
        return ModPayloads.handleAddFilterEntry(player, new AddFilterEntryPayload(containerId, entry));
    }

    private static Filter faceFilter(RouterBlockEntity router, ResourceType type) {
        return router.face(type, Direction.UP).filter();
    }

    @GameTest(template = "empty")
    public static void ghostItemGoesIntoItemFilter(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper);
        ServerPlayer player = playerNear(helper, router);
        FilterMenu menu = openFace(player, router, ResourceType.ITEM);
        try {
            helper.assertTrue(player.getInventory().isEmpty(), "o jogador não deveria ter itens");
            // Pilha e estoque vindos do cliente são normalizados.
            helper.assertTrue(add(player, CONTAINER_ID, new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND, 64), 500)),
                    "recusou o diamante");
            ItemStack named = new ItemStack(Items.IRON_INGOT);
            named.setHoverName(Component.literal("Lingote"));
            helper.assertTrue(add(player, CONTAINER_ID, new FilterEntry.ItemEntry(named, 0)), "recusou o lingote");
            // Duplicado: aceito, mas sem mudar o filtro.
            helper.assertTrue(add(player, CONTAINER_ID, new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND), 0)),
                    "recusou o duplicado");

            Filter filter = faceFilter(router, ResourceType.ITEM);
            GameTestCompat.assertValueEqual(helper, filter.entries().size(), 2, "entradas");
            FilterEntry.ItemEntry diamond = (FilterEntry.ItemEntry) filter.entries().get(0);
            GameTestCompat.assertValueEqual(helper, diamond.stack().getCount(), 1, "quantidade normalizada");
            GameTestCompat.assertValueEqual(helper, diamond.stock(), 0L, "estoque zerado");
            helper.assertTrue(ItemStack.isSameItemSameTags(
                    ((FilterEntry.ItemEntry) filter.entries().get(1)).stack(), named), "componentes perdidos");
            helper.assertTrue(menu.pollView() != null, "a visão não acompanhou o filtro");

            // Tipo errado, entradas que não são fantasmas e vazios.
            helper.assertFalse(add(player, CONTAINER_ID, new FilterEntry.FluidEntry(new FluidStack(Fluids.WATER, 1000), 0)),
                    "aceitou fluido num filtro de itens");
            helper.assertFalse(add(player, CONTAINER_ID,
                    new FilterEntry.TagEntry(new ResourceLocation("c", "ingots"), 0)), "aceitou tag");
            helper.assertFalse(add(player, CONTAINER_ID, new FilterEntry.ModEntry("minecraft", 0)), "aceitou mod");
            helper.assertFalse(add(player, CONTAINER_ID, new FilterEntry.ItemEntry(ItemStack.EMPTY, 0)), "aceitou vazio");

            // Recusas por tela: outro id, jogador longe e sem tela de filtro.
            helper.assertFalse(add(player, CONTAINER_ID + 1, new FilterEntry.ItemEntry(new ItemStack(Items.STONE), 0)),
                    "aceitou outro containerId");
            player.moveTo(Vec3.atCenterOf(router.getBlockPos()).add(0, 20, 0));
            helper.assertFalse(add(player, CONTAINER_ID, new FilterEntry.ItemEntry(new ItemStack(Items.STONE), 0)),
                    "aceitou de longe");
            player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
            player.containerMenu = player.inventoryMenu;
            helper.assertFalse(add(player, CONTAINER_ID, new FilterEntry.ItemEntry(new ItemStack(Items.STONE), 0)),
                    "aceitou sem a tela aberta");
            helper.assertFalse(ModPayloads.handleAddFilterEntry(null,
                    new AddFilterEntryPayload(CONTAINER_ID, new FilterEntry.ItemEntry(new ItemStack(Items.STONE), 0))),
                    "aceitou sem jogador");
            GameTestCompat.assertValueEqual(helper, faceFilter(router, ResourceType.ITEM).entries().size(), 2, "entradas após recusas");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ghostFluidGoesIntoFluidFilter(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper);
        ServerPlayer player = playerNear(helper, router);
        openFace(player, router, ResourceType.FLUID);
        try {
            helper.assertTrue(add(player, CONTAINER_ID, new FilterEntry.FluidEntry(new FluidStack(Fluids.LAVA, 1000), 7)),
                    "recusou a lava");
            helper.assertFalse(add(player, CONTAINER_ID, new FilterEntry.ItemEntry(new ItemStack(Items.WATER_BUCKET), 0)),
                    "aceitou item num filtro de fluidos");
            helper.assertFalse(add(player, CONTAINER_ID, new FilterEntry.FluidEntry(FluidStack.EMPTY, 0)),
                    "aceitou fluido vazio");
            Filter filter = faceFilter(router, ResourceType.FLUID);
            GameTestCompat.assertValueEqual(helper, filter.entries(),
                    List.<FilterEntry>of(new FilterEntry.FluidEntry(new FluidStack(Fluids.LAVA, 1), 0)), "entradas");
            GameTestCompat.assertValueEqual(helper, ((FilterEntry.FluidEntry) filter.entries().get(0)).stack().getAmount(), 1,
                    "quantidade normalizada");
            helper.assertTrue(faceFilter(router, ResourceType.ITEM).isEmpty(), "mexeu no filtro de itens");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    /** O payload vai e volta pelo codec de rede com a entrada intacta. */
    @GameTest(template = "empty")
    public static void payloadStreamCodec(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer());
        try {
            ItemStack named = new ItemStack(Items.GOLD_INGOT);
            named.setHoverName(Component.literal("Ouro"));
            AddFilterEntryPayload payload = new AddFilterEntryPayload(CONTAINER_ID, new FilterEntry.ItemEntry(named, 0));
            AddFilterEntryPayload.STREAM_CODEC.encode(buf, payload);
            AddFilterEntryPayload decoded = AddFilterEntryPayload.STREAM_CODEC.decode(buf);
            GameTestCompat.assertValueEqual(helper, decoded, payload, "payload decodificado");
        } finally {
            buf.release();
        }
        helper.succeed();
    }
}
