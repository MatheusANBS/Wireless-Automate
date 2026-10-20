package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.packet.RenameTargetPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** O rótulo da máquina vinculada e o nome do grupo (etapa 2 da central): NBT, payload, snapshot e rotas. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class MachineLabelGameTests {
    private static void linkTwo(GameTestHelper helper, RouterBlockEntity router) {
        router.setRangeUpgrade(rangeUpgrade());
        link(helper, router, FURNACE_A);
        link(helper, router, BARREL);
    }

    private static RenameTargetPayload machine(RouterMenu menu, int index, BlockPos pos, String name) {
        return new RenameTargetPayload(menu.containerId, EditTarget.Kind.MACHINE, index, Optional.of(pos), name);
    }

    private static RenameTargetPayload group(RouterMenu menu, int id, String name) {
        return new RenameTargetPayload(menu.containerId, EditTarget.Kind.GROUP, id, Optional.empty(), name);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void labelNbtRoundTrip(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkTwo(helper, router);
                    BlockPos furnace = helper.absolutePos(FURNACE_A);
                    BlockPos barrel = helper.absolutePos(BARREL);
                    int furnaceIndex = router.remoteMachines().index(furnace);
                    helper.assertTrue(router.renameTarget(EditTarget.machine(furnaceIndex), "  Forno do ferro "),
                            "rótulo não mudou");
                    helper.assertTrue(router.renameTarget(EditTarget.group(groupOf(router, furnace)), "Fornos"),
                            "nome do grupo não mudou");

                    var registries = helper.getLevel().registryAccess();
                    CompoundTag tag = router.saveWithFullMetadata(registries);
                    RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
                    copy.loadWithComponents(tag, registries);
                    RemoteMachine loaded = copy.remoteMachines().machine(copy.remoteMachines().index(furnace));
                    helper.assertValueEqual(loaded.label(), "Forno do ferro", "rótulo depois da ida e volta");
                    helper.assertValueEqual(copy.remoteMachines().group(groupOf(copy, furnace)).name(), "Fornos",
                            "nome do grupo depois da ida e volta");
                    RemoteMachine plain = copy.remoteMachines().machine(copy.remoteMachines().index(barrel));
                    helper.assertValueEqual(plain.label(), "", "a máquina sem rótulo ganhou um");

                    CompoundTag plainTag = router.remoteMachines().machine(router.remoteMachines().index(barrel))
                            .save(registries);
                    helper.assertFalse(plainTag.contains("label"), "a chave label foi gravada vazia");
                    helper.assertValueEqual(RemoteMachine.load(plainTag, registries).label(), "",
                            "sem a chave não lê vazio");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void renameByPayload(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkTwo(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        var registries = helper.getLevel().registryAccess();
                        BlockPos furnace = helper.absolutePos(FURNACE_A);
                        BlockPos barrel = helper.absolutePos(BARREL);
                        int index = router.remoteMachines().index(furnace);
                        int groupId = groupOf(router, furnace);

                        helper.assertTrue(ModPayloads.handleRenameTarget(player, machine(menu, index, furnace, "Forno 1")),
                                "não rotulou");
                        helper.assertValueEqual(router.remoteMachines().machine(index).label(), "Forno 1", "rótulo");
                        helper.assertTrue(ModPayloads.handleRenameTarget(player, group(menu, groupId, "Fornos")),
                                "não nomeou o grupo");
                        helper.assertValueEqual(router.remoteMachines().group(groupId).name(), "Fornos", "nome do grupo");

                        RouterSnapshot snapshot = RouterSnapshot.capture(router, player);
                        helper.assertValueEqual(snapshot.range().machine(index).label(), "Forno 1", "rótulo no snapshot");
                        helper.assertValueEqual(snapshot.range().group(groupId).name(), "Fornos", "grupo no snapshot");
                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
                        RouterSnapshot.STREAM_CODEC.encode(buf, snapshot);
                        RouterSnapshot decoded = RouterSnapshot.STREAM_CODEC.decode(buf);
                        helper.assertValueEqual(decoded.range().machine(index).label(), "Forno 1",
                                "rótulo depois do codec");
                        helper.assertValueEqual(decoded.range().machine(router.remoteMachines().index(barrel)).label(), "",
                                "a máquina sem rótulo ganhou um no codec");

                        helper.assertFalse(ModPayloads.handleRenameTarget(player,
                                machine(menu, index, furnace, "x".repeat(RenameRouterPayload.MAX_LENGTH + 1))),
                                "aceitou mais de 32 caracteres");
                        helper.assertFalse(ModPayloads.handleRenameTarget(player, machine(menu, index, barrel, "Errado")),
                                "aceitou a posição de outra máquina");
                        helper.assertFalse(ModPayloads.handleRenameTarget(player, new RenameTargetPayload(menu.containerId,
                                EditTarget.Kind.MACHINE, index, Optional.empty(), "Sem posição")), "aceitou sem posição");
                        helper.assertFalse(ModPayloads.handleRenameTarget(player, group(menu, 999, "Nenhum")),
                                "aceitou um grupo que não existe");
                        helper.assertFalse(ModPayloads.handleRenameTarget(player,
                                new RenameTargetPayload(menu.containerId + 1, EditTarget.Kind.GROUP, groupId,
                                        Optional.empty(), "Outro menu")), "aceitou outro menu");
                        helper.assertValueEqual(router.remoteMachines().machine(index).label(), "Forno 1",
                                "uma recusa mudou o rótulo");
                        helper.assertValueEqual(router.remoteMachines().group(groupId).name(), "Fornos",
                                "uma recusa mudou o nome do grupo");

                        router.setRangeUpgrade(net.minecraft.world.item.ItemStack.EMPTY);
                        helper.assertFalse(ModPayloads.handleRenameTarget(player, group(menu, groupId, "Sem upgrade")),
                                "renomeou o grupo sem o Upgrade de Alcance");
                        helper.assertValueEqual(router.remoteMachines().group(groupId).name(), "Fornos",
                                "o grupo mudou sem o upgrade");
                        router.setRangeUpgrade(rangeUpgrade());

                        helper.assertTrue(ModPayloads.handleRenameTarget(player, machine(menu, index, furnace, "  ")),
                                "não tirou o rótulo");
                        helper.assertValueEqual(router.remoteMachines().machine(index).label(), "", "rótulo não saiu");
                        helper.assertFalse(router.remoteMachines().machine(index).save(registries).contains("label"),
                                "a chave label ficou");

                        RenameTargetPayload payload = machine(menu, index, furnace, "Ida e volta");
                        RegistryFriendlyByteBuf out = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
                        RenameTargetPayload.STREAM_CODEC.encode(out, payload);
                        helper.assertValueEqual(RenameTargetPayload.STREAM_CODEC.decode(out), payload, "codec do payload");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    private static RenameTargetPayload attached(RouterMenu menu, String name) {
        return new RenameTargetPayload(menu.containerId, EditTarget.Kind.ATTACHED, 0, Optional.empty(), name);
    }

    /**
     * A máquina presa aceita nome pelo payload, sem o Upgrade de Alcance e sem posição: limpa o nome, apaga com
     * vazio, recusa mais de 32 letras e outro menu, vai no snapshot (também pelo codec) e continua quando o bloco
     * preso é trocado.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void attachedAcceptsNameWithoutRangeUpgrade(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    helper.assertFalse(router.rangeActive(), "o teste precisa do roteador sem o Upgrade de Alcance");
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        var registries = helper.getLevel().registryAccess();
                        helper.assertValueEqual(router.attachedLabel(), "", "nasceu com nome");
                        int version = router.changeVersion();
                        helper.assertTrue(ModPayloads.handleRenameTarget(player, attached(menu, "  Cofre do topo ")),
                                "não nomeou a presa");
                        helper.assertValueEqual(router.attachedLabel(), "Cofre do topo", "nome da presa");
                        helper.assertTrue(router.changeVersion() != version, "a versão da tela não subiu");
                        helper.assertFalse(ModPayloads.handleRenameTarget(player, attached(menu, "Cofre do topo")),
                                "o mesmo nome mudou de novo");

                        RouterSnapshot snapshot = RouterSnapshot.capture(router, player);
                        helper.assertValueEqual(snapshot.attachedLabel(), "Cofre do topo", "nome da presa no snapshot");
                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
                        RouterSnapshot.STREAM_CODEC.encode(buf, snapshot);
                        RouterSnapshot decoded = RouterSnapshot.STREAM_CODEC.decode(buf);
                        helper.assertValueEqual(decoded.attachedLabel(), "Cofre do topo", "nome da presa depois do codec");
                        helper.assertTrue(decoded.equals(snapshot), "o snapshot mudou no codec");
                        RegistryFriendlyByteBuf body = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
                        RouterSnapshot.BODY_CODEC.encode(body, snapshot);
                        helper.assertValueEqual(RouterSnapshot.BODY_CODEC.decode(body).attachedLabel(), "Cofre do topo",
                                "nome da presa no corpo");
                        RouterSnapshot nameless = new RouterSnapshot(snapshot.pos(), snapshot.name(), snapshot.tier(),
                                snapshot.facing(), snapshot.attachedFront(), snapshot.networks(), snapshot.powered(),
                                snapshot.machine(), snapshot.machineState(), snapshot.faces(), snapshot.chunkLoad(),
                                snapshot.range(), snapshot.dimension());
                        helper.assertFalse(nameless.sameBody(snapshot), "o nome da presa não conta no corpo");

                        helper.assertFalse(ModPayloads.handleRenameTarget(player,
                                attached(menu, "x".repeat(RenameRouterPayload.MAX_LENGTH + 1))), "aceitou mais de 32 letras");
                        helper.assertFalse(ModPayloads.handleRenameTarget(player, new RenameTargetPayload(
                                menu.containerId + 1, EditTarget.Kind.ATTACHED, 0, Optional.empty(), "Outro menu")),
                                "aceitou outro menu");
                        helper.assertValueEqual(router.attachedLabel(), "Cofre do topo", "uma recusa mudou o nome");

                        // o nome fica quando o bloco preso é trocado
                        helper.setBlock(new BlockPos(1, 1, 1), net.minecraft.world.level.block.Blocks.FURNACE);
                        helper.assertValueEqual(router.attachedLabel(), "Cofre do topo", "trocar o bloco apagou o nome");

                        CompoundTag tag = router.saveWithFullMetadata(registries);
                        helper.assertValueEqual(tag.getString("attached_label"), "Cofre do topo", "NBT do roteador");
                        RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
                        copy.loadWithComponents(tag, registries);
                        helper.assertValueEqual(copy.attachedLabel(), "Cofre do topo", "nome depois da ida e volta");

                        helper.assertTrue(ModPayloads.handleRenameTarget(player, attached(menu, "  ")), "não apagou");
                        helper.assertValueEqual(router.attachedLabel(), "", "o nome não saiu");
                        CompoundTag old = router.saveWithFullMetadata(registries);
                        helper.assertFalse(old.contains("attached_label"), "a chave attached_label ficou vazia");
                        RouterBlockEntity oldCopy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
                        oldCopy.loadWithComponents(old, registries);
                        helper.assertValueEqual(oldCopy.attachedLabel(), "", "o formato antigo sem a chave não lê vazio");
                        helper.assertTrue(router.renameTarget(EditTarget.ATTACHED_TARGET, "Direto"), "renameTarget da presa");
                        helper.assertValueEqual(router.attachedLabel(), "Direto", "nome por renameTarget");

                        RenameTargetPayload payload = attached(menu, "Ida e volta");
                        RegistryFriendlyByteBuf out = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
                        RenameTargetPayload.STREAM_CODEC.encode(out, payload);
                        helper.assertValueEqual(RenameTargetPayload.STREAM_CODEC.decode(out), payload, "codec do payload");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** Renomear não chama {@code changed()}: as rotas da rede continuam limpas, e um desvincular as suja. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void renameDoesNotDirtyRoutes(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        UUID network = router.networkId(ResourceType.ITEM);
        BlockPos furnace = helper.absolutePos(FURNACE_A);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> linkTwo(helper, router))
                .thenWaitUntil(() -> helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.ITEM),
                        "as rotas não assentaram"))
                .thenExecute(() -> {
                    int version = router.changeVersion();
                    int index = router.remoteMachines().index(furnace);
                    helper.assertTrue(router.renameTarget(EditTarget.machine(index), "Forno"), "rótulo não mudou");
                    helper.assertTrue(router.renameTarget(EditTarget.group(groupOf(router, furnace)), "Fornos"),
                            "nome do grupo não mudou");
                    helper.assertFalse(router.renameTarget(EditTarget.machine(index), "Forno"),
                            "o mesmo rótulo mudou de novo");
                    helper.assertTrue(router.changeVersion() != version, "a versão da tela não subiu");
                    helper.assertFalse(NetworkManager.get().isDirty(network, ResourceType.ITEM),
                            "renomear sujou as rotas");
                    helper.assertTrue(router.unlinkMachine(index), "não desvinculou");
                    helper.assertTrue(NetworkManager.get().isDirty(network, ResourceType.ITEM),
                            "desvincular não sujou as rotas (o teste não prova nada)");
                })
                .thenSucceed();
    }

    /**
     * O snapshot leva a dimensão do roteador (o destaque no mundo acende a máquina nela, também com a tela aberta à
     * distância de outra dimensão), e ela faz a ida e volta pelo codec, inclusive uma que não é a do teste.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void snapshotCarriesTheDimension(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkTwo(helper, router);
                    ServerPlayer player = TestPlayers.inLevel(helper);
                    var registries = helper.getLevel().registryAccess();
                    RouterSnapshot snapshot = RouterSnapshot.capture(router, player);
                    helper.assertValueEqual(snapshot.dimension(), helper.getLevel().dimension(), "dimensão no snapshot");
                    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
                    RouterSnapshot.STREAM_CODEC.encode(buf, snapshot);
                    RouterSnapshot decoded = RouterSnapshot.STREAM_CODEC.decode(buf);
                    helper.assertValueEqual(decoded.dimension(), snapshot.dimension(), "dimensão depois do codec");
                    helper.assertTrue(decoded.equals(snapshot), "o snapshot mudou no codec");

                    RouterSnapshot nether = new RouterSnapshot(snapshot.pos(), snapshot.name(), snapshot.tier(),
                            snapshot.facing(), snapshot.attachedFront(), snapshot.networks(), snapshot.powered(),
                            snapshot.machine(), snapshot.machineState(), snapshot.faces(), snapshot.chunkLoad(),
                            snapshot.range(), Level.NETHER);
                    RegistryFriendlyByteBuf netherBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
                    RouterSnapshot.BODY_CODEC.encode(netherBuf, nether);
                    RouterSnapshot back = RouterSnapshot.BODY_CODEC.decode(netherBuf);
                    helper.assertValueEqual(back.dimension(), Level.NETHER, "o Nether depois do codec do corpo");
                    helper.assertFalse(back.sameBody(snapshot), "outra dimensão deu o mesmo corpo");
                })
                .thenSucceed();
    }
}
