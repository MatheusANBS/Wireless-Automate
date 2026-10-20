package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.BARREL;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.FURNACE_A;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.FURNACE_B;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.groupOf;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.rangeUpgrade;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.routerWithMachines;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.waitLoaded;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.TabletMarks;
import io.github.matheusanbs.wirelessautomate.menu.TabletMarks.MachineKey;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletTree.GroupKey;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TabletMachinesPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMoveNodesPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPayloads;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload.Action;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSource;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Colar e mover nos marcados com máquinas (etapa 3b, {@link TabletMarks}): a exceção com os lados convertidos pela
 * frente, a exceção igual ao perfil que some, o grupo inteiro com as exceções mantidas e contadas (D2), o bloco
 * marcado reavaliado pela consulta, a origem de fora, o perfil antigo recusado em máquinas, o Mover da máquina só nas
 * faces com modo (D1) e do grupo inteiro, o roteador com máquina marcada sem contar duas vezes, uma remontagem por
 * roteador, o Desfazer e os tetos. O roteador de origem fica sobre uma fornalha virada para o norte, com a face leste
 * da presa (a Esquerda dela) em Inserir.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TabletMarkedGameTests {
    private static final BlockPos SOURCE_MACHINE = new BlockPos(2, 1, 2);
    private static final ResourceLocation FURNACE_ID = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);
    private static final int CONTAINER = 160;

    private TabletMarkedGameTests() {
    }

    /** O cenário: o roteador de origem, o de alcance com as duas fornalhas e o barril, o jogador e a rede dele. */
    private record Scene(ServerPlayer player, TabletMenu menu, RouterBlockEntity source, RouterBlockEntity target,
            WaNetwork network) {
        NodeKey sourceKey() {
            return key(source);
        }

        NodeKey targetKey() {
            return key(target);
        }
    }

    private static NodeKey key(RouterBlockEntity router) {
        return NodeKey.of(router.getLevel(), router.getBlockPos());
    }

    private static void eq(GameTestHelper helper, @Nullable Object actual, @Nullable Object expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": esperado " + expected + ", veio " + actual);
    }

    private static RouterBlockEntity sourceRouter(GameTestHelper helper) {
        helper.setBlock(SOURCE_MACHINE, Blocks.FURNACE.defaultBlockState()
                .setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
        helper.setBlock(SOURCE_MACHINE.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP));
        return helper.getBlockEntity(SOURCE_MACHINE.above());
    }

    /** Monta o cenário depois do {@code onLoad}: vincula as três máquinas e copia o perfil da origem. */
    private static Scene scene(GameTestHelper helper, RouterBlockEntity source, RouterBlockEntity target,
            ServerPlayer player) {
        WaNetwork network = NetworkSavedData.get(helper.getLevel().getServer()).create(player.getUUID(), "marcados");
        target.setRangeUpgrade(rangeUpgrade());
        helper.assertTrue(target.linkMachine(helper.absolutePos(FURNACE_A))
                && target.linkMachine(helper.absolutePos(FURNACE_B))
                && target.linkMachine(helper.absolutePos(BARREL)), "não vinculou");
        NodeIndex.placedBy(source, player.getUUID());
        NodeIndex.placedBy(target, player.getUUID());
        source.setName("Origem");
        source.setMode(ResourceType.ITEM, Direction.EAST, PortMode.INSERT);
        source.setPriority(ResourceType.ITEM, Direction.EAST, 7);
        source.setFaceNetwork(ResourceType.ITEM, Direction.EAST, network.id());
        TabletMenu menu = new TabletMenu(CONTAINER, player.getInventory(), player);
        player.containerMenu = menu;
        helper.assertTrue(profile(player, Action.COPY, key(source), List.of(), TabletMarks.EMPTY) >= 0, "não copiou");
        ProfileSource from = player.getInventory().getItem(0).get(ModDataComponents.PROFILE_SOURCE.get());
        helper.assertTrue(from != null && from.pastesOnMachines(), "o Copiar não guardou o quadro da presa");
        return new Scene(player, menu, source, target, network);
    }

    private static void run(GameTestHelper helper, java.util.function.Consumer<Scene> test) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 4, 1.5)));
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        RouterBlockEntity target = routerWithMachines(helper);
        RouterBlockEntity source = sourceRouter(helper);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, target);
                    waitLoaded(helper, source);
                })
                .thenExecute(() -> {
                    Scene scene = scene(helper, source, target, player);
                    try {
                        test.accept(scene);
                    } finally {
                        // fecha o Tablet: quem estava em Máquinas sai da ordem por bloco compartilhada
                        scene.menu().removed(player);
                        player.containerMenu = player.inventoryMenu;
                    }
                })
                .thenSucceed();
    }

    private static int profile(ServerPlayer player, Action action, @Nullable NodeKey key, List<NodeKey> keys,
            TabletMarks marks) {
        return TabletPayloads.handleProfile(player, new TabletProfilePayload(CONTAINER, action, Optional.ofNullable(key),
                Optional.empty(), keys, marks));
    }

    private static int paste(Scene scene, List<NodeKey> keys, TabletMarks marks) {
        return profile(scene.player(), Action.PASTE_SELECTED, null, keys, marks);
    }

    private static TabletMarks machines(MachineKey... keys) {
        return new TabletMarks(List.of(keys), List.of(), List.of());
    }

    private static MachineKey machine(GameTestHelper helper, Scene scene, BlockPos relative) {
        return new MachineKey(scene.targetKey(), helper.absolutePos(relative).asLong());
    }

    private static RemoteMachine remote(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        RemoteMachines remotes = router.remoteMachines();
        return remotes.machine(remotes.index(helper.absolutePos(relative)));
    }

    private static int index(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        return router.remoteMachines().index(helper.absolutePos(relative));
    }

    private static FaceConfig effective(GameTestHelper helper, RouterBlockEntity router, BlockPos relative,
            RemoteSide side) {
        RemoteMachines remotes = router.remoteMachines();
        return remotes.effective(remote(helper, router, relative)).face(ResourceType.ITEM, side);
    }

    /** As chaves de tradução do último aviso. */
    private static String text(TabletMenu menu) {
        StringBuilder keys = new StringBuilder();
        if (menu.lastNotice() != null) {
            collect(menu.lastNotice(), keys);
        }
        return keys.toString();
    }

    private static void collect(Component component, StringBuilder keys) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            keys.append(translatable.getKey()).append(' ');
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component inner) {
                    collect(inner, keys);
                }
            }
        }
        component.getSiblings().forEach(sibling -> collect(sibling, keys));
    }

    /** Uma face com modo no perfil das fornalhas (a Frente em Extrair), sem rede. */
    private static void furnaceFrontExtracts(GameTestHelper helper, RouterBlockEntity target) {
        int group = groupOf(target, helper.absolutePos(FURNACE_A));
        target.configureFace(EditTarget.group(group), ResourceType.ITEM,
                Direction.values()[RemoteSide.FRONT.canonical()], PortMode.EXTRACT, 0, RedstoneMode.IGNORE);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pasteOnAMachineConvertsTheSidesByItsFront(GameTestHelper helper) {
        run(helper, scene -> {
            RouterBlockEntity target = scene.target();
            int version = target.changeVersion();
            RouterPreset sourceBefore = RouterPreset.copyOf(scene.source());
            // a origem marcada e a fornalha A (frente Leste)
            helper.assertTrue(paste(scene, List.of(scene.sourceKey()), machines(machine(helper, scene, FURNACE_A))) == 1,
                    "colou em quantos? " + text(scene.menu()));
            eq(helper, target.changeVersion() - version, 1, "uma remontagem por roteador");
            helper.assertTrue(RouterPreset.copyOf(scene.source()).equals(sourceBefore), "a presa da origem mudou");
            RemoteMachine a = remote(helper, target, FURNACE_A);
            helper.assertTrue(a.hasException(), "a fornalha sem exceção");
            // a Esquerda da origem (o leste de uma fornalha virada para o norte) vira a Esquerda da fornalha A, que
            // virada para o leste fica ao sul
            FaceConfig left = effective(helper, target, FURNACE_A, RemoteSide.LEFT);
            eq(helper, left.mode(), PortMode.INSERT, "modo da Esquerda");
            eq(helper, left.priority(), 7, "prioridade da Esquerda");
            eq(helper, left.network(), scene.network().id(), "rede da Esquerda");
            eq(helper, RemoteSide.LEFT.toAbsolute(Direction.EAST.ordinal()), Direction.SOUTH.ordinal(),
                    "a Esquerda da fornalha virada para o leste");
            eq(helper, effective(helper, target, FURNACE_A, RemoteSide.FRONT).mode(), PortMode.NONE, "a Frente");
            helper.assertFalse(remote(helper, target, FURNACE_B).hasException(), "a fornalha B mudou");
            helper.assertTrue(text(scene.menu()).contains("pasted_on") && text(scene.menu()).contains("part.machines.one"),
                    "aviso: " + text(scene.menu()));

            // o grupo inteiro (as duas, uma a uma) recebe o perfil; a exceção de A fica igual ao perfil e some
            helper.assertTrue(paste(scene, List.of(), machines(machine(helper, scene, FURNACE_A),
                    machine(helper, scene, FURNACE_B))) == 1, "o grupo inteiro: " + text(scene.menu()));
            int group = groupOf(target, helper.absolutePos(FURNACE_A));
            eq(helper, target.remoteMachines().group(group).profile().face(ResourceType.ITEM, RemoteSide.LEFT).mode(),
                    PortMode.INSERT, "o perfil do grupo");
            helper.assertFalse(remote(helper, target, FURNACE_A).hasException(), "a exceção igual ao perfil ficou");

            // o Desfazer volta o perfil e a exceção de antes
            helper.assertTrue(profile(scene.player(), Action.UNDO, null, List.of(), TabletMarks.EMPTY) == 1,
                    "não desfez: " + text(scene.menu()));
            eq(helper, target.remoteMachines().group(group).profile().face(ResourceType.ITEM, RemoteSide.LEFT).mode(),
                    PortMode.NONE, "o perfil depois de desfazer");
            helper.assertTrue(remote(helper, target, FURNACE_A).hasException(), "a exceção não voltou");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wholeGroupKeepsAndCountsExceptionsAndUndoSkipsLaterChanges(GameTestHelper helper) {
        run(helper, scene -> {
            RouterBlockEntity target = scene.target();
            int group = groupOf(target, helper.absolutePos(FURNACE_A));
            int b = index(helper, target, FURNACE_B);
            target.configureFace(EditTarget.machine(b), ResourceType.ITEM,
                    Direction.values()[RemoteSide.TOP.canonical()], PortMode.EXTRACT, 3, RedstoneMode.IGNORE);
            helper.assertTrue(remote(helper, target, FURNACE_B).hasException(), "sem a exceção de B");
            TabletMarks marks = new TabletMarks(List.of(), List.of(new GroupKey(scene.targetKey(), group)), List.of());
            helper.assertTrue(paste(scene, List.of(), marks) == 1, "não colou no grupo: " + text(scene.menu()));
            eq(helper, target.remoteMachines().group(group).profile().face(ResourceType.ITEM, RemoteSide.LEFT).mode(),
                    PortMode.INSERT, "o perfil do grupo");
            helper.assertTrue(remote(helper, target, FURNACE_B).hasException(), "a exceção de B não ficou");
            eq(helper, effective(helper, target, FURNACE_B, RemoteSide.TOP).mode(), PortMode.EXTRACT, "a exceção de B");
            String notice = text(scene.menu());
            helper.assertTrue(notice.contains("part.groups.one") && notice.contains("exceptions_kept.one"),
                    "aviso: " + notice);

            // mudado depois do colar, o perfil não volta; o resto do roteador volta
            MachineConfig changed = target.remoteMachines().group(group).profile().copy();
            changed.face(ResourceType.ITEM, RemoteSide.BACK).setPriority(9);
            target.setGroupProfile(group, changed);
            profile(scene.player(), Action.UNDO, null, List.of(), TabletMarks.EMPTY);
            eq(helper, target.remoteMachines().group(group).profile().face(ResourceType.ITEM, RemoteSide.BACK).priority(),
                    9, "o desfazer apagou a mudança de depois");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void markedBlockFollowsTheQueryAndLeavesTheSourceOut(GameTestHelper helper) {
        run(helper, scene -> {
            RouterBlockEntity target = scene.target();
            helper.assertTrue(TabletPayloads.handleMachines(scene.player(), new TabletMachinesPayload(CONTAINER,
                    TabletMachinesPayload.Action.MACHINES, Optional.empty())), "não entrou em Máquinas");
            scene.menu().setQuery(scene.menu().query());
            scene.menu().pollSnapshot();
            RouterPreset sourceBefore = RouterPreset.copyOf(scene.source());
            int version = target.changeVersion();
            TabletMarks marks = new TabletMarks(List.of(), List.of(), List.of(FURNACE_ID));
            helper.assertTrue(paste(scene, List.of(), marks) == 1, "o bloco: " + text(scene.menu()));
            eq(helper, target.changeVersion() - version, 1, "uma remontagem por roteador");
            int group = groupOf(target, helper.absolutePos(FURNACE_A));
            eq(helper, target.remoteMachines().group(group).profile().face(ResourceType.ITEM, RemoteSide.LEFT).mode(),
                    PortMode.INSERT, "o bloco não virou o perfil do grupo");
            helper.assertTrue(RouterPreset.copyOf(scene.source()).equals(sourceBefore),
                    "a presa da origem (uma fornalha) recebeu");
            helper.assertFalse(remote(helper, target, BARREL).hasException(), "o barril recebeu");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void profileWithoutTheFrameOnlyPastesOnTheAttached(GameTestHelper helper) {
        run(helper, scene -> {
            ItemStack tablet = scene.player().getInventory().getItem(0);
            ProfileSource from = tablet.get(ModDataComponents.PROFILE_SOURCE.get());
            tablet.set(ModDataComponents.PROFILE_SOURCE.get(),
                    new ProfileSource(from.nodeName(), from.machine(), from.origin()));
            RouterBlockEntity target = scene.target();
            helper.assertTrue(paste(scene, List.of(scene.targetKey()), machines(machine(helper, scene, FURNACE_A))) == 1,
                    "a presa: " + text(scene.menu()));
            helper.assertFalse(remote(helper, target, FURNACE_A).hasException(), "colou na máquina sem o quadro");
            eq(helper, target.face(ResourceType.ITEM, Direction.EAST).mode(), PortMode.INSERT, "a presa");
            helper.assertTrue(text(scene.menu()).contains("copy_again"), "aviso: " + text(scene.menu()));
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void moveMachinesGroupsAndRoutersWithoutCountingTwice(GameTestHelper helper) {
        run(helper, scene -> {
            RouterBlockEntity target = scene.target();
            furnaceFrontExtracts(helper, target);
            UUID net = scene.network().id();
            int version = target.changeVersion();
            // D1: a fornalha A marcada muda só a Frente (a face com modo), como exceção
            int moved = TabletPayloads.handleMoveNodes(scene.player(), new TabletMoveNodesPayload(CONTAINER, List.of(),
                    machines(machine(helper, scene, FURNACE_A)), Optional.of(ResourceType.ITEM), Optional.of(net)));
            eq(helper, moved, 1, "máquinas movidas");
            eq(helper, target.changeVersion() - version, 1, "uma remontagem por roteador");
            eq(helper, effective(helper, target, FURNACE_A, RemoteSide.FRONT).network(), net, "a Frente de A");
            eq(helper, effective(helper, target, FURNACE_A, RemoteSide.LEFT).network(), null, "a face sem modo de A");
            eq(helper, effective(helper, target, FURNACE_B, RemoteSide.FRONT).network(), null, "a Frente de B");
            helper.assertTrue(remote(helper, target, FURNACE_A).hasException(), "A sem a exceção");

            // D2: o grupo das fornalhas inteiro muda no perfil, e a exceção de A fica (agora igual ao perfil, some)
            int group = groupOf(target, helper.absolutePos(FURNACE_A));
            moved = TabletPayloads.handleMoveNodes(scene.player(), new TabletMoveNodesPayload(CONTAINER, List.of(),
                    new TabletMarks(List.of(), List.of(new GroupKey(scene.targetKey(), group)), List.of()),
                    Optional.of(ResourceType.ITEM), Optional.of(net)));
            eq(helper, moved, 1, "grupos movidos");
            eq(helper, target.remoteMachines().group(group).profile().face(ResourceType.ITEM, RemoteSide.FRONT).network(),
                    net, "o perfil do grupo");

            // o roteador marcado leva tudo; a máquina dele marcada não conta de novo
            moved = TabletPayloads.handleMoveNodes(scene.player(), new TabletMoveNodesPayload(CONTAINER,
                    List.of(scene.targetKey()), machines(machine(helper, scene, BARREL)), Optional.empty(),
                    Optional.empty()));
            eq(helper, moved, 1, "o roteador e a máquina dele contados uma vez");
            eq(helper, target.remoteMachines().group(group).profile().face(ResourceType.ITEM, RemoteSide.FRONT).network(),
                    null, "o roteador inteiro saiu da rede");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void limitsOfThePacketsAndOfThePlayer(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        NodeKey node = NodeKey.of(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        List<MachineKey> many = new ArrayList<>();
        for (int i = 0; i <= TabletMenu.MAX_MOVE; i++) {
            many.add(new MachineKey(node, i));
        }
        TabletMarks.STREAM_CODEC.encode(buf, new TabletMarks(many, List.of(), List.of()));
        boolean refused = false;
        try {
            TabletMarks.STREAM_CODEC.decode(buf);
        } catch (RuntimeException e) {
            refused = true;
        }
        helper.assertTrue(refused, "o pacote com máquinas demais passou");
        buf.clear();
        TabletMarks ok = new TabletMarks(many.subList(0, 3), List.of(new GroupKey(node, 2)), List.of(FURNACE_ID));
        TabletMarks.STREAM_CODEC.encode(buf, ok);
        eq(helper, TabletMarks.STREAM_CODEC.decode(buf), ok, "os marcados pelo codec");

        run(helper, scene -> {
            TabletMarks marks = machines(machine(helper, scene, FURNACE_A));
            boolean busy = false;
            for (int i = 0; i <= TabletMenu.BULK_PER_SECOND && !busy; i++) {
                busy = paste(scene, List.of(), marks) < 0;
            }
            helper.assertTrue(busy && text(scene.menu()).contains("busy"), "sem o limite por jogador");
        });
    }
}
