package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.RangePayloads;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.UnlinkGroupPayload;
import io.github.matheusanbs.wirelessautomate.packet.UpdateMachinePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Etapa 2 do alcance, o grupo inteiro: Desvincular o grupo pela tela ({@link UnlinkGroupPayload}: sem a presa o
 * grupo sai, com ela fica zerado, o "×0" some, o id reaproveitado é recusado, os cartões vão para o jogador e a sobra
 * cai aos pés dele) e o Atualizar que devolve os cartões ao jogador, com o aviso.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeGroupUnlinkGameTests {
    /** Um baú remoto, ao lado da presa (que é um baú), sem formar baú duplo com ela. */
    private static final BlockPos REMOTE = new BlockPos(2, 1, 2);
    private static final String NOTICE = "gui.wirelessautomate.panel.notice.range.";

    private static ResourceLocation id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    private static String key(PanelNoticePayload notice) {
        return notice.message().getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    private static int cards(ServerPlayer player) {
        return player.getInventory().countItem(ModItems.FILTER_CARD.get());
    }

    private static boolean selectGroup(ServerPlayer player, RouterBlockEntity router, int group) {
        return ModPayloads.handleSelectTarget(player, SelectTargetPayload.group(CONTAINER_ID, group,
                router.remoteMachines().group(group).block()));
    }

    /** Sem a presa: as duas fornalhas saem, o grupo sai, o cartão vai para o jogador e o alvo volta para a presa. */
    @GameTest(template = "empty")
    public static void unlinkGroupWithoutAttached(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BARREL);
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0, itemCard());
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    helper.assertTrue(selectGroup(player, router, furnaces), "não escolheu o grupo");

                    helper.assertTrue(RangePayloads.handleUnlinkGroup(player,
                            new UnlinkGroupPayload(CONTAINER_ID, furnaces, id(Blocks.FURNACE))), "não desvinculou");
                    RemoteMachines remotes = router.remoteMachines();
                    helper.assertTrue(remotes.group(furnaces) == null, "o grupo ficou");
                    helper.assertFalse(remotes.contains(helper.absolutePos(FURNACE_A)), "a fornalha A ficou");
                    helper.assertFalse(remotes.contains(helper.absolutePos(FURNACE_B)), "a fornalha B ficou");
                    helper.assertTrue(remotes.contains(helper.absolutePos(BARREL)), "o barril saiu junto");
                    helper.assertValueEqual(cards(player), 1, "cartões no inventário");
                    helper.assertTrue(menu.target().attached(), "o alvo não voltou para a presa: " + menu.target());

                    PanelNoticePayload notice = RangePayloads.unlinkedGroupNotice(2, 1, false);
                    helper.assertValueEqual(key(notice), NOTICE + "unlinked", "aviso");
                    helper.assertValueEqual(notice.message().getSiblings().size(), 2, "partes do aviso");
                    helper.assertValueEqual(notice.undo(), PanelNoticePayload.Undo.NONE, "Desfazer no aviso");
                    helper.assertValueEqual(key(RangePayloads.unlinkedGroupNotice(1, 0, false)), NOTICE + "unlinked.one",
                            "singular");
                    helper.assertValueEqual(key(RangePayloads.unlinkedGroupNotice(0, 1, false)),
                            NOTICE + "cards_returned.one", "só cartões");
                })
                .thenSucceed();
    }

    /**
     * Com a presa: o baú remoto sai, o grupo fica só com a presa, sem nome, com o perfil zerado e a presa sem
     * exceção; o cartão vai para o jogador e o alvo vira a presa.
     */
    @GameTest(template = "empty")
    public static void unlinkGroupKeepsAttached(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.setBlock(REMOTE, Blocks.CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, REMOTE);
                    RemoteMachines remotes = router.remoteMachines();
                    int chests = groupOf(router, helper.absolutePos(REMOTE));
                    helper.assertTrue(remotes.groupHasAttached(chests), "a presa não está no grupo dos baús");
                    remotes.group(chests).setName("Baús");
                    router.setCard(EditTarget.group(chests), ResourceType.ITEM, Direction.NORTH, 0, itemCard());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    helper.assertTrue(remotes.attached().hasException(), "a presa sem exceção");
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    helper.assertTrue(selectGroup(player, router, chests), "não escolheu o grupo");

                    helper.assertTrue(RangePayloads.handleUnlinkGroup(player,
                            new UnlinkGroupPayload(CONTAINER_ID, chests, id(Blocks.CHEST))), "não desvinculou");
                    MachineGroup group = remotes.group(chests);
                    helper.assertTrue(group != null, "o grupo da presa saiu");
                    helper.assertTrue(remotes.groupHasAttached(chests), "a presa saiu do grupo");
                    helper.assertFalse(remotes.contains(helper.absolutePos(REMOTE)), "o baú remoto ficou");
                    helper.assertValueEqual(group.name(), "", "nome");
                    helper.assertTrue(group.profile().isDefault(), "o perfil não zerou");
                    helper.assertFalse(group.hasCards(), "o cartão ficou no grupo");
                    helper.assertFalse(remotes.attached().hasException(), "a exceção da presa ficou");
                    helper.assertValueEqual(cards(player), 1, "cartões no inventário");
                    helper.assertTrue(menu.target().attached(), "o alvo não virou a presa: " + menu.target());

                    // De novo, nada mais a zerar: recusa sem aviso.
                    helper.assertFalse(RangePayloads.handleUnlinkGroup(player,
                            new UnlinkGroupPayload(CONTAINER_ID, chests, id(Blocks.CHEST))), "zerou de novo");
                    helper.assertValueEqual(key(RangePayloads.unlinkedGroupNotice(0, 0, true)), NOTICE + "group_cleared",
                            "aviso do grupo da presa zerado");
                })
                .thenSucceed();
    }

    /** O "×0" (grupo sem máquina, só com perfil) sai; o id reaproveitado ou o bloco errado são recusados sem mudar nada. */
    @GameTest(template = "empty")
    public static void unlinkEmptyGroupAndRefusals(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    RemoteMachines remotes = router.remoteMachines();
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    MachineConfig profile = new MachineConfig();
                    profile.face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
                    int empty = remotes.newGroup(id(Blocks.SMOKER), profile).id();
                    ServerPlayer[] holder = new ServerPlayer[1];
                    openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];

                    helper.assertFalse(RangePayloads.handleUnlinkGroup(player,
                            new UnlinkGroupPayload(CONTAINER_ID, furnaces, id(Blocks.SMOKER))), "bloco errado aceito");
                    helper.assertFalse(RangePayloads.handleUnlinkGroup(player,
                            new UnlinkGroupPayload(CONTAINER_ID, 999, id(Blocks.FURNACE))), "id que não existe aceito");
                    helper.assertFalse(RangePayloads.handleUnlinkGroup(player,
                            new UnlinkGroupPayload(CONTAINER_ID + 1, furnaces, id(Blocks.FURNACE))), "outro menu aceito");
                    helper.assertTrue(remotes.contains(helper.absolutePos(FURNACE_A)), "uma recusa desvinculou");

                    helper.assertTrue(RangePayloads.handleUnlinkGroup(player,
                            new UnlinkGroupPayload(CONTAINER_ID, empty, id(Blocks.SMOKER))), "não apagou o ×0");
                    helper.assertTrue(remotes.group(empty) == null, "o ×0 ficou");
                    helper.assertTrue(remotes.group(furnaces) != null, "o outro grupo saiu");
                    helper.assertFalse(RangePayloads.handleUnlinkGroup(player,
                            new UnlinkGroupPayload(CONTAINER_ID, empty, id(Blocks.SMOKER))), "apagou o ×0 duas vezes");
                    helper.assertValueEqual(key(RangePayloads.unlinkedGroupNotice(0, 0, false)), NOTICE + "group_removed",
                            "aviso do ×0 sem nada");
                })
                .thenSucceed();
    }

    /** Inventário cheio: o cartão cai aos pés do jogador, não no roteador. */
    @GameTest(template = "empty")
    public static void unlinkGroupCardsOverflowAtFeet(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0, itemCard());
                    TestPlayers.MenuPlayer player = TestPlayers.survivalMenuOpener(helper);
                    Vec3 feet = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
                    player.moveTo(feet);
                    player.containerMenu = new RouterMenu(CONTAINER_ID, player.getInventory(), router,
                            RouterSnapshot.capture(router, player));
                    for (int i = 0; i < player.getInventory().items.size(); i++) {
                        player.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
                    }

                    helper.assertTrue(RangePayloads.handleUnlinkGroup(player,
                            new UnlinkGroupPayload(CONTAINER_ID, furnaces, id(Blocks.FURNACE))), "não desvinculou");
                    helper.assertValueEqual(cards(player), 0, "o cartão coube no inventário cheio");
                    boolean atFeet = !helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(feet, feet).inflate(1.5),
                            item -> item.getItem().is(ModItems.FILTER_CARD.get())).isEmpty();
                    helper.assertTrue(atFeet, "o cartão não caiu aos pés do jogador");
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            new AABB(router.getBlockPos()).inflate(1), item -> item.getItem().is(ModItems.FILTER_CARD.get()))
                            .isEmpty(), "o cartão caiu no roteador");
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }

    /**
     * Atualizar pelo pacote: as fornalhas trocadas entram no grupo dos altos-fornos que já existe, o grupo delas
     * esvazia e o cartão dele vai para o jogador (nada cai no roteador); o aviso conta máquinas e cartões.
     */
    @GameTest(template = "empty")
    public static void updateReturnsCardsToPlayer(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.setBlock(REMOTE, Blocks.BLAST_FURNACE);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, REMOTE);
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    int blasts = groupOf(router, helper.absolutePos(REMOTE));
                    router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0, itemCard());
                    helper.setBlock(FURNACE_A, Blocks.BLAST_FURNACE.defaultBlockState()
                            .setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
                    helper.setBlock(FURNACE_B, Blocks.BLAST_FURNACE.defaultBlockState()
                            .setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH));
                    ServerPlayer[] holder = new ServerPlayer[1];
                    openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];

                    helper.assertTrue(ModPayloads.handleUpdateMachine(player,
                            UpdateMachinePayload.group(CONTAINER_ID, furnaces, id(Blocks.FURNACE))), "não atualizou");
                    helper.assertTrue(router.remoteMachines().group(furnaces) == null, "o grupo vazio ficou");
                    helper.assertValueEqual(groupOf(router, helper.absolutePos(FURNACE_A)), blasts, "A");
                    helper.assertValueEqual(cards(player), 1, "cartão no inventário");
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            new AABB(router.getBlockPos()).inflate(2)).isEmpty(), "algo caiu no roteador");

                    PanelNoticePayload notice = RangePayloads.updatedNotice(2, 1);
                    helper.assertValueEqual(key(notice), NOTICE + "updated", "aviso");
                    helper.assertValueEqual(notice.message().getSiblings().size(), 2, "partes do aviso");
                    PanelNoticePayload one = RangePayloads.updatedNotice(1, 0);
                    helper.assertValueEqual(key(one), NOTICE + "updated.one", "singular");
                    helper.assertTrue(one.message().getSiblings().isEmpty(), "aviso sem cartões com a parte dos cartões");
                })
                .thenSucceed();
    }

    /** Atualizar a única fornalha do grupo: o grupo passa ao bloco novo e os cartões vão junto, nada ao jogador. */
    @GameTest(template = "empty")
    public static void updateKeepsCardsWithGroup(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0, itemCard());
                    router.configureFace(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, PortMode.INSERT,
                            0, RedstoneMode.IGNORE);
                    helper.setBlock(FURNACE_A, Blocks.BLAST_FURNACE.defaultBlockState()
                            .setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
                    ServerPlayer[] holder = new ServerPlayer[1];
                    openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    int index = router.remoteMachines().index(helper.absolutePos(FURNACE_A));

                    helper.assertTrue(ModPayloads.handleUpdateMachine(player,
                            UpdateMachinePayload.machine(CONTAINER_ID, index, helper.absolutePos(FURNACE_A))),
                            "não atualizou");
                    MachineGroup group = router.remoteMachines().group(furnaces);
                    helper.assertTrue(group != null && group.block().equals(id(Blocks.BLAST_FURNACE)), "o grupo não mudou");
                    helper.assertTrue(group.hasCards(), "o cartão não foi junto");
                    helper.assertValueEqual(cards(player), 0, "cartão no jogador");
                })
                .thenSucceed();
    }
}
