package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.RenameTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.RevertMachinePayload;
import io.github.matheusanbs.wirelessautomate.packet.UnlinkMachinePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Roteador de alcance, os achados das revisões (etapa 4): o pedido da tela que segue a máquina pela posição quando a
 * lista mudou, o id de grupo que nunca volta depois de recarregar, o grupo de id repetido no NBT e o Vinculador no
 * modo aventura.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeReviewGameTests {
    /** Desvincular e Voltar ao perfil com o índice velho: a máquina é achada pela posição; sem ela, recusa. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void staleIndexFollowsThePosition(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BARREL);
                    BlockPos a = helper.absolutePos(FURNACE_A);
                    BlockPos b = helper.absolutePos(FURNACE_B);
                    BlockPos barrel = helper.absolutePos(BARREL);
                    int indexB = router.remoteMachines().index(b);
                    router.configureFace(EditTarget.machine(indexB), ResourceType.ITEM, Direction.UP, PortMode.INSERT, 0,
                            RedstoneMode.IGNORE);
                    helper.assertTrue(router.remoteMachines().machine(indexB).hasException(), "a B não virou exceção");
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        // o índice 0 é o da A: o pedido vale para a máquina da posição
                        helper.assertTrue(ModPayloads.handleUnlinkMachine(player,
                                new UnlinkMachinePayload(menu.containerId, 0, barrel)), "o índice velho recusou");
                        helper.assertFalse(router.remoteMachines().contains(barrel), "o barril continua vinculado");
                        helper.assertTrue(router.remoteMachines().contains(a), "a A saiu no lugar do barril");
                        helper.assertFalse(ModPayloads.handleUnlinkMachine(player,
                                new UnlinkMachinePayload(menu.containerId, 0, barrel)), "desvinculou uma posição sem máquina");
                        helper.assertTrue(router.remoteMachines().contains(a), "a recusa tirou a A");

                        helper.assertTrue(ModPayloads.handleRevertMachine(player,
                                new RevertMachinePayload(menu.containerId, 0, b)), "o Voltar ao perfil recusou");
                        helper.assertFalse(router.remoteMachines().machine(router.remoteMachines().index(b)).hasException(),
                                "a exceção da B ficou");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** O id de um grupo que saiu não volta depois de recarregar (a chave {@code next_group}). */
    @GameTest(template = "empty")
    public static void groupIdNeverComesBack(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, BARREL);
                    int barrels = groupOf(router, helper.absolutePos(BARREL));
                    helper.assertTrue(router.unlinkMachine(helper.absolutePos(BARREL)), "não desvinculou o barril");
                    helper.assertTrue(router.remoteMachines().group(barrels) == null, "o grupo do barril ficou");

                    var registries = helper.getLevel().registryAccess();
                    RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
                    copy.loadWithComponents(router.saveWithFullMetadata(registries), registries);
                    MachineGroup next = copy.remoteMachines().newGroup(BuiltInRegistries.BLOCK.getKey(Blocks.BARREL),
                            new MachineConfig());
                    helper.assertTrue(next.id() > barrels, "o id " + barrels + " voltou: " + next.id());
                })
                .thenSucceed();
    }

    /** Um grupo de id repetido no NBT fica, com id novo e os cartões; as máquinas daquele id são do primeiro. */
    @GameTest(template = "empty")
    public static void repeatedGroupIdKeepsCards(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.UP, 0, itemCard());

                    var registries = helper.getLevel().registryAccess();
                    CompoundTag tag = router.saveWithFullMetadata(registries);
                    ListTag groups = tag.getList("groups", Tag.TAG_COMPOUND);
                    CompoundTag twin = null;
                    for (int i = 0; i < groups.size(); i++) {
                        if (groups.getCompound(i).getInt("id") == furnaces) {
                            twin = groups.getCompound(i).copy();
                        }
                    }
                    ResourceLocation barrel = BuiltInRegistries.BLOCK.getKey(Blocks.BARREL);
                    twin.putString("block", barrel.toString());
                    groups.add(twin);
                    tag.put("groups", groups);
                    RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
                    copy.loadWithComponents(tag, registries);

                    RemoteMachines remotes = copy.remoteMachines();
                    helper.assertValueEqual(remotes.groupCount(), router.remoteMachines().groupCount() + 1,
                            "grupos depois da carga");
                    MachineGroup first = remotes.group(furnaces);
                    helper.assertTrue(first != null && first.block().equals(BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE)),
                            "o grupo das fornalhas não é o primeiro do id");
                    MachineGroup other = remotes.firstGroupOf(barrel);
                    helper.assertTrue(other != null && other.id() != furnaces, "o repetido sumiu ou ficou com o mesmo id");
                    helper.assertTrue(other.hasCards(), "o repetido perdeu os cartões");
                    helper.assertValueEqual(groupOf(copy, helper.absolutePos(FURNACE_A)), furnaces, "a fornalha mudou de grupo");
                    MachineGroup next = remotes.newGroup(BuiltInRegistries.BLOCK.getKey(Blocks.CHEST), new MachineConfig());
                    helper.assertTrue(remotes.group(next.id()) == next && next.id() != other.id(),
                            "id repetido de novo: " + next.id());
                })
                .thenSucceed();
    }

    /** No modo aventura o Vinculador não escolhe o roteador nem vincula ({@code mayBuild}). */
    @GameTest(template = "empty")
    public static void adventureDoesNotLink(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        router.setRangeUpgrade(rangeUpgrade());
                        player.setGameMode(GameType.ADVENTURE);
                        click(helper, player, ROUTER, false);
                        helper.assertTrue(LinkerItem.router(linker) == null, "escolheu o roteador no modo aventura");
                        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                        RangeLinks.Result result = toggle(helper, player, FURNACE_A);
                        helper.assertTrue(result.status() == RangeLinks.Status.REFUSED
                                && result.refusal() == LinkRefusal.PROTECTED, "no modo aventura: " + result);
                        helper.assertFalse(router.remoteMachines().contains(helper.absolutePos(FURNACE_A)),
                                "vinculou no modo aventura");
                        player.setGameMode(GameType.SURVIVAL);
                        helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(), RangeLinks.Status.LINKED,
                                "no modo sobrevivência");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }
    /**
     * O Vinculador desvincula num clique: o clique na máquina vinculada a tira, e os cartões do grupo que esvaziou vão
     * para o inventário de quem clicou, com a contagem na mensagem.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void linkerUnlinksInOneClick(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ItemStack linker = machinesLinker();
        TestPlayers.MenuPlayer player = TestPlayers.survivalMenuOpener(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, linker);
        BlockPos furnace = helper.absolutePos(FURNACE_A);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                    helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(), RangeLinks.Status.LINKED, "vincular");
                    helper.assertValueEqual(toggle(helper, player, BARREL).status(), RangeLinks.Status.LINKED,
                            "vincular o barril");
                    router.setCard(EditTarget.group(groupOf(router, furnace)), ResourceType.ITEM, Direction.UP, 0,
                            itemCard());

                    helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(), RangeLinks.Status.UNLINKED,
                            "o clique na vinculada");
                    helper.assertFalse(router.remoteMachines().contains(furnace), "a fornalha continua vinculada");
                    helper.assertTrue(router.remoteMachines().contains(helper.absolutePos(BARREL)),
                            "o barril saiu junto");
                    helper.assertValueEqual(player.getInventory().countItem(ModItems.FILTER_CARD.get()), 1,
                            "o cartão não veio para o inventário");
                    helper.assertItemEntityNotPresent(ModItems.FILTER_CARD.get(), ROUTER, 2.0);
                    assertKey(helper, player.actionBar, "item.wirelessautomate.linker.range.unlinked");
                    helper.assertTrue(player.actionBar.getSiblings().stream().anyMatch(part -> part.getContents()
                            instanceof TranslatableContents c
                            && c.getKey().equals("item.wirelessautomate.linker.range.cards_returned.one")),
                            "a mensagem não diz o cartão: " + player.actionBar.getString());
                })
                .thenSucceed();
    }

    /**
     * Renomear: a máquina pelo índice velho é achada pela posição; o grupo só com o bloco visto, e o NBT guarda o
     * próximo id ({@code next_group}).
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void renameFollowsPositionAndBlock(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, BARREL);
                    BlockPos barrel = helper.absolutePos(BARREL);
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        helper.assertTrue(ModPayloads.handleRenameTarget(player, new RenameTargetPayload(menu.containerId,
                                EditTarget.Kind.MACHINE, 0, Optional.of(barrel), Optional.empty(), "Barril")),
                                "o índice velho recusou");
                        helper.assertValueEqual(router.remoteMachines().machine(router.remoteMachines().index(barrel)).label(),
                                "Barril", "o rótulo não foi para o barril");
                        Optional<ResourceLocation> furnace = Optional.of(BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE));
                        Optional<ResourceLocation> chest = Optional.of(BuiltInRegistries.BLOCK.getKey(Blocks.CHEST));
                        helper.assertFalse(ModPayloads.handleRenameTarget(player, new RenameTargetPayload(menu.containerId,
                                EditTarget.Kind.GROUP, furnaces, Optional.empty(), chest, "Errado")), "aceitou outro bloco");
                        helper.assertFalse(ModPayloads.handleRenameTarget(player, new RenameTargetPayload(menu.containerId,
                                EditTarget.Kind.GROUP, furnaces, Optional.empty(), Optional.empty(), "Sem bloco")),
                                "aceitou sem o bloco");
                        helper.assertTrue(ModPayloads.handleRenameTarget(player, new RenameTargetPayload(menu.containerId,
                                EditTarget.Kind.GROUP, furnaces, Optional.empty(), furnace, "Fornos")), "não nomeou");
                        helper.assertValueEqual(router.remoteMachines().group(furnaces).name(), "Fornos", "nome do grupo");
                        CompoundTag tag = router.saveWithFullMetadata(helper.getLevel().registryAccess());
                        helper.assertValueEqual(tag.getInt("next_group"), router.remoteMachines().nextGroupId(),
                                "next_group no NBT");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    private static void assertKey(GameTestHelper helper, @Nullable Component message, String key) {
        helper.assertTrue(message != null && message.getContents() instanceof TranslatableContents c
                && c.getKey().equals(key), "esperava " + key + ", veio " + message);
    }
}
