package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.PanelAccess;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.SetNetworkPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPayloads;
import io.github.matheusanbs.wirelessautomate.packet.TabletSelectNodePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * O nó escolhido no Tablet: o painel que o servidor manda ({@link TabletPanelPayload}) e a edição à
 * distância pelos pacotes de face, com o {@link TabletMenu} como host. Os testes do lote dividem o índice
 * e as redes, então cada um cria as suas.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TabletPanelGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos FURNACE = new BlockPos(2, 1, 0);

    private static ServerPlayer playerWithTablet(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(pos).above(2)));
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        return player;
    }

    private static TabletMenu openTablet(ServerPlayer player, int containerId) {
        TabletMenu menu = new TabletMenu(containerId, player.getInventory(), player);
        player.containerMenu = menu;
        return menu;
    }

    /** Máquina com um roteador {@code tier} em cima, nomeado, na rede e colocado pelo jogador (ele o vê). */
    private static RouterBlockEntity owned(GameTestHelper helper, ServerPlayer player, BlockPos machine, Block block,
            RouterTier tier, @Nullable UUID network, String name) {
        helper.setBlock(machine, block);
        BlockPos pos = machine.above();
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP)
                .setValue(RouterBlock.TIER, tier));
        RouterBlockEntity router = helper.getBlockEntity(pos);
        router.setNetworkId(network);
        router.setName(name);
        NodeIndex.placedBy(router, player.getUUID());
        return router;
    }

    private static NodeKey key(RouterBlockEntity router) {
        return NodeKey.of(router.getLevel(), router.getBlockPos());
    }

    private static void loaded(GameTestHelper helper, RouterBlockEntity router) {
        helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad");
    }

    private static void eq(GameTestHelper helper, @Nullable Object actual, @Nullable Object expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": esperado " + expected + ", veio " + actual);
    }

    private static boolean choose(ServerPlayer player, int containerId, NodeKey key) {
        return TabletPayloads.handleSelectNode(player, new TabletSelectNodePayload(containerId, Optional.of(key)));
    }

    private static SetFacePayload setFace(int containerId, Direction face, PortMode mode) {
        return new SetFacePayload(containerId, ResourceType.ITEM, face, mode, 3, RedstoneMode.IGNORE);
    }

    /** Escolher manda o painel, uma vez; editar por pacotes de face e de rede muda o nó e o painel seguinte. */
    @GameTest(template = "empty")
    public static void choosingSendsPanelAndFaceEditsWork(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork one = data.create(player.getUUID(), "teste-painel-um");
        WaNetwork two = data.create(player.getUUID(), "teste-painel-dois");
        RouterBlockEntity router = owned(helper, player, A, Blocks.CHEST, RouterTier.BASIC, one.id(), "Painel");
        TabletMenu menu = openTablet(player, 80);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(menu.pollPanel() == null, "painel sem nó escolhido");
                        helper.assertFalse(menu.canEdit(player), "editável sem nó escolhido");
                        helper.assertFalse(ModPayloads.handleSetFace(player, setFace(80, Direction.UP, PortMode.INSERT)),
                                "editou sem nó escolhido");
                        helper.assertFalse(TabletPayloads.handleSelectNode(player,
                                new TabletSelectNodePayload(81, Optional.of(key(router)))), "outro containerId");

                        helper.assertTrue(choose(player, 80, key(router)), "não escolheu");
                        TabletPanelPayload panel = menu.pollPanel();
                        helper.assertTrue(panel != null, "o painel não saiu ao escolher");
                        eq(helper, panel.node(), Optional.of(key(router)), "nó do painel");
                        eq(helper, panel.access(), PanelAccess.EDIT, "acesso");
                        helper.assertTrue(panel.snapshot().isPresent()
                                && panel.snapshot().get().pos().equals(router.getBlockPos()), "snapshot do nó");
                        eq(helper, panel.range(), Config.TIERS.get(RouterTier.BASIC).range().get(), "alcance");
                        helper.assertTrue(panel.distance() >= 0 && panel.distance() <= 5, "distância " + panel.distance());
                        helper.assertTrue(menu.pollPanel() == null, "reenviou o que não mudou");

                        // ida e volta pelo codec
                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                                helper.getLevel().registryAccess());
                        TabletPanelPayload.STREAM_CODEC.encode(buf, panel);
                        // No fio a distância vira só a dimensão: a tela calcula a distância pela posição do jogador.
                        helper.assertTrue(TabletPanelPayload.STREAM_CODEC.decode(buf).equals(panel.asSent()), "painel pelo codec");
                        TabletSelectNodePayload select = new TabletSelectNodePayload(80, Optional.of(key(router)));
                        TabletSelectNodePayload.STREAM_CODEC.encode(buf, select);
                        helper.assertTrue(TabletSelectNodePayload.STREAM_CODEC.decode(buf).equals(select), "escolha pelo codec");

                        // pelo Tablet como host: face, rede e nome do nó escolhido
                        helper.assertTrue(menu.canEdit(player), "não pode editar ao alcance");
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(80, Direction.UP, PortMode.INSERT)),
                                "recusou o SetFace");
                        eq(helper, router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT, "modo da face");
                        panel = menu.pollPanel();
                        helper.assertTrue(panel != null && panel.snapshot().get().face(ResourceType.ITEM, Direction.UP).mode()
                                == PortMode.INSERT, "o painel não mostra a face nova");
                        helper.assertTrue(menu.pollPanel() == null, "reenviou depois da edição");

                        helper.assertTrue(ModPayloads.handleSetNetwork(player,
                                new SetNetworkPayload(80, ResourceType.ITEM, Direction.UP, Optional.of(two.id()))),
                                "recusou o SetNetwork");
                        eq(helper, router.faceNetwork(ResourceType.ITEM, Direction.UP), two.id(), "rede da face");
                        panel = menu.pollPanel();
                        helper.assertTrue(panel != null && panel.snapshot().get().network(ResourceType.ITEM, Direction.UP)
                                .equals(Optional.of(two.id())), "o painel não mostra a rede nova");

                        helper.assertTrue(ModPayloads.handleRename(player, new RenameRouterPayload(80, "Novo nome")),
                                "recusou o Rename");
                        eq(helper, router.name(), "Novo nome", "nome");
                        panel = menu.pollPanel();
                        helper.assertTrue(panel != null && panel.snapshot().get().name().equals("Novo nome"),
                                "o painel não mostra o nome novo");

                        // largar a escolha
                        helper.assertTrue(TabletPayloads.handleSelectNode(player,
                                new TabletSelectNodePayload(80, Optional.empty())), "não largou a escolha");
                        helper.assertTrue(menu.pollPanel() == null, "painel sem nó");
                        helper.assertFalse(menu.canEdit(player), "editável sem nó escolhido");
                        helper.succeed();
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    /** Fora do alcance a edição é recusada e o painel diz só leitura, com a distância e o alcance. */
    @GameTest(template = "empty")
    public static void outOfReachIsReadOnly(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        WaNetwork network = NetworkSavedData.get(helper.getLevel().getServer()).create(player.getUUID(), "teste-painel-longe");
        RouterBlockEntity router = owned(helper, player, A, Blocks.CHEST, RouterTier.BASIC, network.id(), "Longe");
        TabletMenu menu = openTablet(player, 82);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    try {
                        Vec3 center = Vec3.atCenterOf(router.getBlockPos());
                        player.moveTo(center.add(300, 0, 0));
                        helper.assertTrue(choose(player, 82, key(router)), "não escolheu fora do alcance");
                        TabletPanelPayload panel = menu.pollPanel();
                        helper.assertTrue(panel != null, "sem painel fora do alcance");
                        eq(helper, panel.access(), PanelAccess.READ_OUT_OF_REACH, "acesso");
                        helper.assertTrue(panel.snapshot().isPresent(), "sem a configuração para ler");
                        helper.assertTrue(Math.abs(panel.distance() - 300) <= 1, "distância " + panel.distance());
                        eq(helper, panel.range(), Config.TIERS.get(RouterTier.BASIC).range().get(), "alcance");
                        helper.assertFalse(menu.canEdit(player), "editável fora do alcance");

                        helper.assertFalse(ModPayloads.handleSetFace(player, setFace(82, Direction.UP, PortMode.INSERT)),
                                "editou fora do alcance");
                        helper.assertFalse(ModPayloads.handleRename(player, new RenameRouterPayload(82, "Longe demais")),
                                "renomeou fora do alcance");
                        eq(helper, router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.NONE, "modo da face");

                        player.moveTo(center.add(3, 0, 0));
                        helper.assertTrue(menu.canEdit(player), "não editável de volta ao alcance");
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(82, Direction.UP, PortMode.INSERT)),
                                "recusou de volta ao alcance");
                        panel = menu.pollPanel();
                        helper.assertTrue(panel != null && panel.access() == PanelAccess.EDIT, "o acesso não voltou a editar");

                        // sem o Tablet nada edita
                        player.getInventory().setItem(0, ItemStack.EMPTY);
                        helper.assertFalse(menu.canEdit(player), "editável sem o Tablet");
                        helper.succeed();
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    /** Nó fora da vista do jogador não pode ser escolhido nem editado. */
    @GameTest(template = "empty")
    public static void invisibleNodeCannotBeChosen(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        WaNetwork foreign = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "teste-painel-alheia");
        RouterBlockEntity router = owned(helper, player, A, Blocks.CHEST, RouterTier.BASIC, foreign.id(), "Alheio");
        NodeIndex.placedBy(router, UUID.randomUUID());
        TabletMenu menu = openTablet(player, 83);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    try {
                        helper.assertFalse(choose(player, 83, key(router)), "escolheu um nó invisível");
                        helper.assertTrue(menu.chosenNode() == null, "o nó invisível ficou escolhido");
                        helper.assertTrue(menu.pollPanel() == null, "painel de nó invisível");
                        helper.assertFalse(ModPayloads.handleSetFace(player, setFace(83, Direction.UP, PortMode.INSERT)),
                                "editou um nó invisível");
                        NodeKey nowhere = new NodeKey(router.getLevel().dimension(), router.getBlockPos().offset(0, 5, 0));
                        helper.assertFalse(choose(player, 83, nowhere), "escolheu um nó que não existe");
                        helper.succeed();
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    /** Um nó que o jogador vê mas está descarregado: painel vazio e só leitura, sem carregar o chunk. */
    @GameTest(template = "empty")
    public static void unloadedNodeIsReadOnlyAndNeverLoadsTheChunk(GameTestHelper helper) throws Exception {
        ServerPlayer player = playerWithTablet(helper, A);
        BlockPos far = new BlockPos(3_000_000, 64, 3_000_000);
        NodeKey key = new NodeKey(helper.getLevel().dimension(), far);
        CompoundTag node = new CompoundTag();
        node.putString("dim", key.dimension().location().toString());
        node.putLong("pos", far.asLong());
        node.putString("tier", RouterTier.BASIC.getSerializedName());
        node.putString("machine", BuiltInRegistries.BLOCK.getKey(Blocks.CHEST).toString());
        node.putUUID("placedBy", player.getUUID());
        CompoundTag file = new CompoundTag();
        ListTag nodes = new ListTag();
        nodes.add(node);
        file.put("nodes", nodes);
        NodeIndex.Entry entry = NodeIndex.load(file, helper.getLevel().registryAccess()).entry(key);
        Field field = NodeIndex.class.getDeclaredField("entries");
        field.setAccessible(true);
        NodeIndex index = NodeIndex.get(helper.getLevel().getServer());
        @SuppressWarnings("unchecked")
        Map<NodeKey, NodeIndex.Entry> entries = (Map<NodeKey, NodeIndex.Entry>) field.get(index);
        entries.put(key, entry);
        TabletMenu menu = openTablet(player, 84);
        try {
            helper.assertFalse(helper.getLevel().hasChunkAt(far), "o chunk longe já estava carregado");
            helper.assertTrue(choose(player, 84, key), "não escolheu o nó descarregado");
            TabletPanelPayload panel = menu.pollPanel();
            helper.assertTrue(panel != null, "sem painel do nó descarregado");
            eq(helper, panel.access(), PanelAccess.READ_UNLOADED, "acesso");
            helper.assertTrue(panel.snapshot().isEmpty(), "snapshot de um nó nunca lido");
            helper.assertFalse(menu.canEdit(player), "editável descarregado");
            helper.assertTrue(menu.editedRouter() == null, "roteador de um nó descarregado");
            helper.assertFalse(ModPayloads.handleSetFace(player, setFace(84, Direction.UP, PortMode.INSERT)),
                    "editou um nó descarregado");
            helper.assertFalse(helper.getLevel().hasChunkAt(far), "o painel carregou o chunk");
            helper.succeed();
        } finally {
            entries.remove(key);
            player.containerMenu = player.inventoryMenu;
        }
    }

    /** Trocar o alvo (grupo do alcance) pelo Tablet edita o perfil do grupo; outro nó volta à presa. */
    @GameTest(template = "empty")
    public static void groupTargetWorksThroughTheTablet(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        WaNetwork network = NetworkSavedData.get(helper.getLevel().getServer()).create(player.getUUID(), "teste-painel-grupo");
        RouterBlockEntity router = owned(helper, player, A, Blocks.CHEST, RouterTier.ADVANCED, network.id(), "Alcance");
        helper.setBlock(FURNACE, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
        TabletMenu menu = openTablet(player, 85);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, router))
                .thenExecute(() -> {
                    try {
                        router.setRangeUpgrade(new ItemStack(ModItems.RANGE_UPGRADE.get()));
                        BlockPos furnace = helper.absolutePos(FURNACE);
                        helper.assertTrue(router.linkMachine(furnace), "não vinculou a fornalha");
                        int group = router.remoteMachines().machine(router.remoteMachines().index(furnace)).group();
                        var furnaceId = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);

                        helper.assertFalse(ModPayloads.handleSelectTarget(player, SelectTargetPayload.group(85, group, furnaceId)),
                                "escolheu o alvo sem nó escolhido");
                        helper.assertTrue(choose(player, 85, key(router)), "não escolheu o nó");
                        helper.assertTrue(menu.pollPanel() != null, "sem painel");
                        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.group(85, group, furnaceId)),
                                "não trocou para o grupo");
                        eq(helper, menu.targetRef().target(), EditTarget.group(group), "alvo no menu");
                        TabletPanelPayload panel = menu.pollPanel();
                        helper.assertTrue(panel != null, "o painel não saiu ao trocar o alvo");
                        eq(helper, panel.snapshot().get().target(), EditTarget.group(group), "alvo no painel");
                        helper.assertTrue(panel.snapshot().get().range().present(), "sem a visão do alcance");

                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(85, Direction.SOUTH, PortMode.EXTRACT)),
                                "recusou a face do grupo");
                        eq(helper, router.face(EditTarget.group(group), ResourceType.ITEM, Direction.SOUTH).mode(),
                                PortMode.EXTRACT, "perfil do grupo");
                        eq(helper, router.face(ResourceType.ITEM, Direction.SOUTH).mode(), PortMode.NONE, "a presa mudou");

                        // com o bloco errado o alvo é recusado e continua o grupo
                        helper.assertFalse(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.group(85, group, BuiltInRegistries.BLOCK.getKey(Blocks.BARREL))),
                                "aceitou o bloco errado");
                        eq(helper, menu.targetRef().target(), EditTarget.group(group), "alvo depois da recusa");

                        // escolher o nó de novo (largar e escolher) volta à presa
                        menu.chooseNode(null);
                        helper.assertTrue(choose(player, 85, key(router)), "não escolheu de novo");
                        eq(helper, menu.targetRef().target(), EditTarget.ATTACHED_TARGET, "alvo depois de escolher de novo");
                        helper.succeed();
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }
}
