package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterFaceFilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload.Op;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.RevertMachinePayload;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.UnlinkMachinePayload;
import io.github.matheusanbs.wirelessautomate.preset.PasteMode;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Roteador de alcance: o slot do Upgrade de Alcance na tela (aceita num tier com raio, recusa no
 * Básico), o NBT ({@code range_upgrade}) e o drop ao quebrar; o modelo das máquinas remotas (grupos
 * por bloco, perfil, exceções, cartões do grupo, {@code machines}/{@code groups} no NBT); o motor, o
 * acordar e o Vinculador no modo Máquinas; e a tela (alvo de edição, {@link RangeView} no snapshot e os
 * payloads por alvo, pelos handlers públicos com a {@link RouterMenu} aberta).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeGameTests {
    private static final int CONTAINER_ID = 53;

    private static ItemStack rangeUpgrade() {
        return new ItemStack(ModItems.RANGE_UPGRADE.get());
    }

    /** Baú em (1, 1, 1) e roteador do tier {@code tier} preso nele por cima. */
    private static RouterBlockEntity chestWithRouter(GameTestHelper helper, RouterTier tier) {
        BlockPos machine = new BlockPos(1, 1, 1);
        helper.setBlock(machine, Blocks.CHEST);
        helper.setBlock(machine.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(machine.above());
    }

    @SuppressWarnings("removal")
    private static RouterMenu openMenu(GameTestHelper helper, RouterBlockEntity router, ServerPlayer[] out) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        RouterMenu menu = new RouterMenu(CONTAINER_ID, player.getInventory(), router, RouterSnapshot.capture(router, player));
        player.containerMenu = menu;
        out[0] = player;
        return menu;
    }

    @GameTest(template = "empty")
    public static void slotAcceptsOnAdvanced(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    helper.assertTrue(menu.getSlot(RouterMenu.RANGE_SLOT).mayPlace(rangeUpgrade()),
                            "slot de alcance recusou o upgrade no Avançado");
                    helper.assertFalse(menu.getSlot(RouterMenu.RANGE_SLOT).mayPlace(new ItemStack(Items.DIAMOND)),
                            "slot de alcance aceitou diamante");
                    helper.assertFalse(menu.getSlot(RouterMenu.RANGE_SLOT).mayPlace(
                            new ItemStack(ModItems.CHUNK_LOADER_UPGRADE.get())), "slot de alcance aceitou o de chunk loading");
                    helper.assertFalse(menu.getSlot(RouterMenu.UPGRADE_SLOT).mayPlace(rangeUpgrade()),
                            "slot de chunk loading aceitou o de alcance");

                    // Shift + clique no inventário leva ao slot de alcance; no slot, traz de volta.
                    player.getInventory().setItem(0, rangeUpgrade());
                    int hotbar0 = RouterMenu.UPGRADE_SLOT - 9;
                    menu.clicked(hotbar0, 0, ClickType.QUICK_MOVE, player);
                    helper.assertTrue(router.rangeUpgrade().is(ModItems.RANGE_UPGRADE.get()),
                            "o upgrade de alcance não foi para o roteador");
                    helper.assertTrue(router.rangeActive(), "o upgrade de alcance não vale no Avançado");
                    helper.assertTrue(menu.getSlot(hotbar0).getItem().isEmpty(), "o upgrade ficou no inventário");
                    helper.assertFalse(router.hasChunkUpgrade(), "o upgrade de alcance foi para o slot de chunk loading");

                    menu.clicked(RouterMenu.RANGE_SLOT, 0, ClickType.QUICK_MOVE, player);
                    helper.assertTrue(router.rangeUpgrade().isEmpty(), "o upgrade de alcance ficou no roteador");
                    helper.assertFalse(router.rangeActive(), "alcance ativo sem o upgrade");
                    helper.assertTrue(player.getInventory().contains(rangeUpgrade()), "o upgrade não voltou ao inventário");
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void slotRefusesOnBasic(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.BASIC);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    helper.assertFalse(router.acceptsRangeUpgradeHere(rangeUpgrade()), "o Básico aceitou o upgrade");
                    helper.assertFalse(menu.getSlot(RouterMenu.RANGE_SLOT).mayPlace(rangeUpgrade()),
                            "slot de alcance aceitou o upgrade no Básico");

                    player.getInventory().setItem(0, rangeUpgrade());
                    int hotbar0 = RouterMenu.UPGRADE_SLOT - 9;
                    menu.clicked(hotbar0, 0, ClickType.QUICK_MOVE, player);
                    helper.assertTrue(router.rangeUpgrade().isEmpty(), "o upgrade de alcance entrou no Básico");
                    helper.assertTrue(player.getInventory().contains(rangeUpgrade()), "o upgrade sumiu do inventário");
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void rangeUpgradeSavedAndReloaded(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    CompoundTag tag = router.saveWithFullMetadata(helper.getLevel().registryAccess());
                    helper.assertTrue(tag.contains("range_upgrade"), "range_upgrade não foi salvo");
                    helper.assertFalse(tag.contains("upgrade"), "o upgrade de alcance foi salvo na chave do outro");

                    RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
                    copy.loadWithComponents(tag, helper.getLevel().registryAccess());
                    helper.assertTrue(copy.rangeUpgrade().is(ModItems.RANGE_UPGRADE.get()), "range_upgrade não foi lido");
                    helper.assertTrue(copy.upgrade().isEmpty(), "o upgrade de alcance foi lido no slot do outro");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void breakDropsRangeUpgrade(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        BlockPos relative = new BlockPos(1, 2, 1);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    helper.destroyBlock(relative);
                    helper.assertBlockNotPresent(ModBlocks.ROUTER.get(), relative);
                    helper.assertItemEntityPresent(ModItems.RANGE_UPGRADE.get(), relative, 2.0);
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ máquinas, grupos, perfis e exceções

    private static final BlockPos FURNACE_A = new BlockPos(0, 1, 0);
    private static final BlockPos FURNACE_B = new BlockPos(2, 1, 0);
    private static final BlockPos BARREL = new BlockPos(0, 1, 2);
    private static final BlockPos ROUTER = new BlockPos(1, 2, 1);

    /**
     * Roteador Avançado numa rede própria, preso no baú, com duas fornalhas (frente Leste e Sul) e um
     * barril virado para cima (sem frente horizontal) em volta.
     */
    private static RouterBlockEntity routerWithMachines(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        router.setNetworkId(NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "alcance").id());
        helper.setBlock(FURNACE_A, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
        helper.setBlock(FURNACE_B, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH));
        helper.setBlock(BARREL, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        return router;
    }

    private static void waitLoaded(GameTestHelper helper, RouterBlockEntity router) {
        helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
    }

    private static void link(GameTestHelper helper, RouterBlockEntity router, BlockPos relative) {
        helper.assertTrue(router.linkMachine(helper.absolutePos(relative)), "não vinculou " + relative);
    }

    private static ItemStack itemCard() {
        ItemStack card = new ItemStack(ModItems.FILTER_CARD.get());
        FilterCardItem.setContents(card, new FilterCardItem.Contents(ResourceType.ITEM, Filter.EMPTY));
        return card;
    }

    private static int groupOf(RouterBlockEntity router, BlockPos absolute) {
        RemoteMachines remotes = router.remoteMachines();
        return remotes.machine(remotes.index(absolute)).group();
    }

    @GameTest(template = "empty")
    public static void remoteMachinesSavedAndReloaded(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BARREL);
                    helper.assertFalse(router.linkMachine(helper.absolutePos(FURNACE_A)), "vinculou a mesma posição duas vezes");
                    BlockPos a = helper.absolutePos(FURNACE_A);
                    BlockPos b = helper.absolutePos(FURNACE_B);
                    BlockPos barrel = helper.absolutePos(BARREL);
                    int furnaces = groupOf(router, a);
                    int barrels = groupOf(router, barrel);
                    helper.assertTrue(furnaces == groupOf(router, b), "as fornalhas ficaram em grupos diferentes");
                    helper.assertTrue(furnaces != barrels, "fornalha e barril no mesmo grupo");

                    // Perfil das fornalhas, exceção na B e cartões nos dois grupos (pela máquina, no do barril).
                    router.configureFace(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, PortMode.EXTRACT, 3,
                            RedstoneMode.IGNORE);
                    int indexB = router.remoteMachines().index(b);
                    router.configureFace(EditTarget.machine(indexB), ResourceType.ITEM, Direction.UP, PortMode.INSERT, 0,
                            RedstoneMode.IGNORE);
                    router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0, itemCard());
                    int indexBarrel = router.remoteMachines().index(barrel);
                    router.setCard(EditTarget.machine(indexBarrel), ResourceType.ITEM, Direction.UP, 1, itemCard());

                    CompoundTag tag = router.saveWithFullMetadata(helper.getLevel().registryAccess());
                    helper.assertTrue(tag.contains("machines") && tag.contains("groups"), "machines/groups não foram salvos");
                    RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
                    copy.loadWithComponents(tag, helper.getLevel().registryAccess());

                    RemoteMachines remotes = copy.remoteMachines();
                    helper.assertTrue(remotes.contains(a) && remotes.contains(b) && remotes.contains(barrel),
                            "máquinas não foram lidas");
                    helper.assertTrue(remotes.index(a) == router.remoteMachines().index(a)
                            && remotes.index(barrel) == indexBarrel, "a ordem de vínculo mudou");
                    helper.assertTrue(remotes.machine(remotes.index(a)).front() == Direction.EAST.ordinal()
                            && remotes.machine(remotes.index(b)).front() == Direction.SOUTH.ordinal(),
                            "a frente das fornalhas não foi lida");
                    helper.assertTrue(remotes.machine(indexBarrel).front() == -1, "barril para cima ganhou frente");
                    helper.assertTrue(remotes.group(furnaces) != null && remotes.group(barrels) != null, "grupos não foram lidos");
                    helper.assertTrue(remotes.group(furnaces).block().equals(BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE)),
                            "id do bloco do grupo errado");

                    FaceConfig profile = copy.face(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH);
                    helper.assertTrue(profile.mode() == PortMode.EXTRACT && profile.priority() == 3, "perfil não foi lido");
                    helper.assertFalse(remotes.machine(remotes.index(a)).hasException(), "a fornalha A ganhou exceção");
                    helper.assertTrue(remotes.machine(indexB).hasException(), "a exceção da B não foi lida");
                    helper.assertTrue(copy.face(EditTarget.machine(indexB), ResourceType.ITEM, Direction.UP).mode() == PortMode.INSERT
                            && copy.face(EditTarget.machine(indexB), ResourceType.ITEM, Direction.SOUTH).mode() == PortMode.EXTRACT,
                            "a exceção da B não tem a mudança e o perfil copiado");
                    helper.assertTrue(copy.card(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0)
                            .is(ModItems.FILTER_CARD.get()), "cartão do grupo das fornalhas não foi lido");
                    helper.assertTrue(copy.card(EditTarget.machine(indexB), ResourceType.ITEM, Direction.SOUTH, 0)
                            .is(ModItems.FILTER_CARD.get()), "a máquina não lê os cartões do grupo");
                    helper.assertTrue(copy.card(EditTarget.group(barrels), ResourceType.ITEM, Direction.UP, 1)
                            .is(ModItems.FILTER_CARD.get()), "cartão posto pela máquina não foi para o grupo do barril");
                    helper.assertTrue(copy.card(EditTarget.ATTACHED_TARGET, ResourceType.ITEM, Direction.UP, 1).isEmpty(),
                            "cartão de grupo foi para a máquina presa");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void legacyNbtLoadsWithoutMachines(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    // Um NBT de antes da etapa: só a máquina presa configurada, sem machines/groups.
                    router.configureFace(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 2, RedstoneMode.IGNORE);
                    CompoundTag legacy = router.saveWithFullMetadata(helper.getLevel().registryAccess());
                    helper.assertFalse(legacy.contains("machines") || legacy.contains("groups"),
                            "um roteador sem remotas salvou as listas");

                    link(helper, router, FURNACE_A);
                    router.loadWithComponents(legacy, helper.getLevel().registryAccess());
                    helper.assertTrue(router.remoteMachines().isEmpty(), "NBT sem as chaves deixou máquinas");
                    FaceConfig attached = router.face(EditTarget.ATTACHED_TARGET, ResourceType.ITEM, Direction.UP);
                    helper.assertTrue(attached.mode() == PortMode.EXTRACT && attached.priority() == 2,
                            "a máquina presa perdeu a configuração");

                    // E funciona: vincula de novo e configura.
                    link(helper, router, FURNACE_A);
                    router.configureFace(EditTarget.group(groupOf(router, helper.absolutePos(FURNACE_A))), ResourceType.ITEM,
                            Direction.NORTH, PortMode.INSERT, 0, RedstoneMode.IGNORE);
                    helper.assertTrue(router.face(EditTarget.machine(0), ResourceType.ITEM, Direction.NORTH).mode() == PortMode.INSERT,
                            "a máquina vinculada depois da carga não segue o perfil");
                    helper.assertTrue(router.face(ResourceType.ITEM, Direction.UP).mode() == PortMode.EXTRACT,
                            "editar o grupo mexeu na máquina presa");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void profileChangeSkipsException(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    RemoteMachines remotes = router.remoteMachines();
                    int indexA = remotes.index(helper.absolutePos(FURNACE_A));
                    int indexB = remotes.index(helper.absolutePos(FURNACE_B));
                    EditTarget group = EditTarget.group(remotes.machine(indexA).group());

                    router.configureFace(EditTarget.machine(indexB), ResourceType.FLUID, Direction.UP, PortMode.INSERT, 1,
                            RedstoneMode.IGNORE);
                    helper.assertTrue(remotes.machine(indexB).hasException(), "editar a máquina não criou exceção");
                    helper.assertTrue(router.face(group, ResourceType.FLUID, Direction.UP).mode() == PortMode.NONE,
                            "editar a máquina mudou o perfil");

                    router.configureFace(group, ResourceType.ITEM, Direction.WEST, PortMode.EXTRACT, 0, RedstoneMode.IGNORE);
                    router.setFilter(group, ResourceType.ITEM, Direction.WEST, Filter.EMPTY.withListMode(Filter.ListMode.BLACKLIST));
                    FaceConfig onA = router.face(EditTarget.machine(indexA), ResourceType.ITEM, Direction.WEST);
                    helper.assertTrue(onA.mode() == PortMode.EXTRACT && onA.filter().listMode() == Filter.ListMode.BLACKLIST,
                            "o perfil não vale para a máquina sem exceção");
                    helper.assertTrue(remotes.effective(remotes.machine(indexA)) == remotes.group(group.id()).profile(),
                            "a máquina sem exceção não usa o perfil");
                    FaceConfig onB = router.face(EditTarget.machine(indexB), ResourceType.ITEM, Direction.WEST);
                    helper.assertTrue(onB.mode() == PortMode.NONE, "o perfil mudou a exceção");
                    helper.assertTrue(router.face(EditTarget.machine(indexB), ResourceType.FLUID, Direction.UP).mode()
                            == PortMode.INSERT, "a exceção perdeu a própria mudança");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void exceptionEqualToProfileDisappears(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    RemoteMachines remotes = router.remoteMachines();
                    int indexA = remotes.index(helper.absolutePos(FURNACE_A));
                    int indexB = remotes.index(helper.absolutePos(FURNACE_B));
                    EditTarget group = EditTarget.group(remotes.machine(indexA).group());

                    // Desfazer a mudança na máquina tira a exceção.
                    router.configureFace(EditTarget.machine(indexA), ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT, 0,
                            RedstoneMode.IGNORE);
                    helper.assertTrue(remotes.machine(indexA).hasException(), "editar a máquina não criou exceção");
                    router.configureFace(EditTarget.machine(indexA), ResourceType.ITEM, Direction.NORTH, PortMode.NONE, 0,
                            RedstoneMode.IGNORE);
                    helper.assertFalse(remotes.machine(indexA).hasException(), "a exceção igual ao perfil ficou");

                    // O perfil alcançar a exceção também a tira.
                    router.configureFace(EditTarget.machine(indexB), ResourceType.ITEM, Direction.NORTH, PortMode.INSERT, 0,
                            RedstoneMode.IGNORE);
                    helper.assertTrue(remotes.machine(indexB).hasException(), "editar a B não criou exceção");
                    router.configureFace(group, ResourceType.ITEM, Direction.NORTH, PortMode.INSERT, 0, RedstoneMode.IGNORE);
                    helper.assertFalse(remotes.machine(indexB).hasException(), "a exceção alcançada pelo perfil ficou");

                    // Voltar ao perfil.
                    router.configureFace(EditTarget.machine(indexB), ResourceType.ENERGY, Direction.DOWN, PortMode.EXTRACT, 0,
                            RedstoneMode.IGNORE);
                    helper.assertTrue(router.revertToProfile(indexB), "voltar ao perfil não fez nada");
                    helper.assertFalse(remotes.machine(indexB).hasException(), "voltar ao perfil deixou a exceção");
                    helper.assertTrue(router.face(EditTarget.machine(indexB), ResourceType.ENERGY, Direction.DOWN).mode()
                            == PortMode.NONE, "voltar ao perfil não voltou a configuração");

                    // Uma exceção sobrevive ao NBT só se diferente do perfil.
                    CompoundTag tag = router.saveWithFullMetadata(helper.getLevel().registryAccess());
                    RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
                    copy.loadWithComponents(tag, helper.getLevel().registryAccess());
                    helper.assertFalse(copy.remoteMachines().machine(indexB).hasException(), "exceção apagada voltou pelo NBT");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void unlinkLastMachineDropsGroupCards(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BARREL);
                    BlockPos a = helper.absolutePos(FURNACE_A);
                    int furnaces = groupOf(router, a);
                    router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.EAST, 0, itemCard());

                    // A outra fornalha ainda está no grupo: nada cai.
                    helper.assertTrue(router.unlinkMachine(a), "não desvinculou a fornalha A");
                    helper.assertFalse(router.remoteMachines().contains(a), "a fornalha A continua vinculada");
                    helper.assertTrue(router.remoteMachines().group(furnaces) != null, "o grupo saiu com uma máquina nele");
                    helper.assertItemEntityNotPresent(ModItems.FILTER_CARD.get(), ROUTER, 3.0);

                    // A última sai: o grupo some e o cartão cai no roteador.
                    helper.assertTrue(router.unlinkMachine(helper.absolutePos(FURNACE_B)), "não desvinculou a fornalha B");
                    helper.assertTrue(router.remoteMachines().group(furnaces) == null, "o grupo vazio ficou");
                    helper.assertItemEntityPresent(ModItems.FILTER_CARD.get(), ROUTER, 2.0);
                    helper.assertTrue(router.remoteMachines().contains(helper.absolutePos(BARREL)), "o barril saiu junto");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void breakDropsGroupCards(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, BARREL);
                    int barrels = groupOf(router, helper.absolutePos(BARREL));
                    router.setCard(EditTarget.group(barrels), ResourceType.ITEM, Direction.DOWN, 1, itemCard());
                    helper.destroyBlock(ROUTER);
                    helper.assertBlockNotPresent(ModBlocks.ROUTER.get(), ROUTER);
                    helper.assertItemEntityPresent(ModItems.FILTER_CARD.get(), ROUTER, 2.0);
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ o motor com as máquinas remotas

    /** Posição relativa da máquina remota a 4 blocos do roteador (em cima, fora do template). */
    private static final BlockPos REMOTE_CHEST = new BlockPos(1, 6, 1);
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);

    /** Roteador Avançado preso no baú, numa rede própria (sem o upgrade ainda: só depois do {@code onLoad}). */
    private static RouterBlockEntity rangeRouter(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        router.setNetworkId(NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "motor").id());
        return router;
    }

    private static Container container(GameTestHelper helper, BlockPos relative) {
        return helper.getBlockEntity(relative);
    }

    private static int count(GameTestHelper helper, BlockPos relative) {
        Container container = container(helper, relative);
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            total += container.getItem(i).getCount();
        }
        return total;
    }

    /** O grupo da máquina em {@code relative} recebe o modo de itens no lado canônico {@code side}. */
    private static void profile(GameTestHelper helper, RouterBlockEntity router, BlockPos relative, Direction side,
            PortMode mode) {
        int group = groupOf(router, helper.absolutePos(relative));
        router.configureFace(EditTarget.group(group), ResourceType.ITEM, side, mode, 0, RedstoneMode.IGNORE);
    }

    private static boolean remotePort(GameTestHelper helper, RouterBlockEntity router, BlockPos relative, Direction face) {
        return NetworkManager.get().hasRemotePort(router, helper.absolutePos(relative), ResourceType.ITEM, face);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void attachedToRemoteChest(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    link(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, REMOTE_CHEST) == 16, "os itens não chegaram ao baú remoto"))
                .thenExecute(() -> helper.assertTrue(count(helper, CHEST) == 0, "o baú preso não esvaziou"))
                .thenSucceed();
    }

    /**
     * A fornalha vanilla só dá o resultado por baixo ({@code WorldlyContainer}): "Baixo → Extrair" tira o
     * resultado de duas fornalhas com frentes diferentes. Os lados horizontais da fornalha são iguais
     * entre si, então quem prova que Trás segue a frente é {@link #backFollowsFront}.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void remoteFurnaceOutput(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        container(helper, FURNACE_A).setItem(2, new ItemStack(Items.IRON_INGOT, 5));
        container(helper, FURNACE_B).setItem(2, new ItemStack(Items.GOLD_INGOT, 7));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    profile(helper, router, FURNACE_A, Direction.DOWN, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, CHEST) == 12, "o resultado das fornalhas não chegou"))
                .thenExecute(() -> {
                    helper.assertTrue(container(helper, FURNACE_A).getItem(2).isEmpty()
                            && container(helper, FURNACE_B).getItem(2).isEmpty(), "sobrou resultado numa fornalha");
                    helper.assertTrue(remotePort(helper, router, FURNACE_A, Direction.DOWN)
                            && remotePort(helper, router, FURNACE_B, Direction.DOWN), "porta de baixo ausente");
                })
                .thenSucceed();
    }

    /**
     * "Trás → Extrair" numa máquina de teste ({@link TestMachines#BACK_SLOT}, abóbora esculpida) que só
     * tem inventário pela face de trás: com frente Leste, Trás é Oeste; com frente Sul, é Norte. Se o
     * lado não seguisse a frente, a consulta cairia numa face sem inventário e nada sairia.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void backFollowsFront(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        BlockPos east = new BlockPos(0, 1, 0);
        BlockPos south = new BlockPos(2, 1, 2);
        helper.setBlock(east, TestMachines.BACK_SLOT.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(south, TestMachines.BACK_SLOT.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        TestMachines.reset(helper.absolutePos(east));
        TestMachines.reset(helper.absolutePos(south));
        TestMachines.bigSlot(helper.absolutePos(east)).set(Items.COBBLESTONE, 10);
        TestMachines.bigSlot(helper.absolutePos(south)).set(Items.DIRT, 10);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    link(helper, router, east);
                    link(helper, router, south);
                    // Trás, no quadro canônico (frente = Norte), é o Sul.
                    profile(helper, router, east, Direction.SOUTH, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, CHEST) == 20, "o lado de trás não deu os itens"))
                .thenExecute(() -> {
                    helper.assertTrue(remotePort(helper, router, east, Direction.WEST), "Trás da frente Leste não é Oeste");
                    helper.assertTrue(remotePort(helper, router, south, Direction.NORTH), "Trás da frente Sul não é Norte");
                    helper.assertFalse(remotePort(helper, router, east, Direction.EAST), "porta na frente");
                    TestMachines.reset(helper.absolutePos(east));
                    TestMachines.reset(helper.absolutePos(south));
                })
                .thenSucceed();
    }

    /** Barril no raio recebe; o de fora do raio (um bloco além, em cima) não vira porta e não recebe. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void outOfRadiusDoesNotMove(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        int radius = Config.machineRadius(RouterTier.ADVANCED);
        BlockPos inside = new BlockPos(1, 2 + radius, 1);
        BlockPos outside = new BlockPos(1, 3 + radius, 1);
        helper.setBlock(inside, Blocks.BARREL);
        helper.setBlock(outside, Blocks.BARREL);
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    link(helper, router, outside);
                    link(helper, router, inside);
                    profile(helper, router, inside, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, inside) == 64, "o barril no raio não recebeu tudo"))
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, outside) == 0, "o barril fora do raio recebeu");
                    helper.assertFalse(remotePort(helper, router, outside, Direction.UP), "porta fora do raio");
                })
                .thenSucceed();
    }

    /** Com o limite do Avançado em N máquinas, a máquina N+1 (na ordem de vínculo) não vira porta. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void machineBeyondLimitDoesNotMove(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        int max = Config.maxMachines(RouterTier.ADVANCED);
        helper.assertTrue(max >= 1 && max + 1 <= 18, "teste pensado para até 17 máquinas, config com " + max);
        BlockPos[] barrels = new BlockPos[max + 1];
        for (int i = 0; i < barrels.length; i++) {
            barrels[i] = new BlockPos(i % 3, 4 + i / 9, i / 3 % 3);
            helper.setBlock(barrels[i], Blocks.BARREL);
        }
        Container chest = container(helper, CHEST);
        for (int i = 0; i < chest.getContainerSize(); i++) {
            chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    for (BlockPos barrel : barrels) {
                        link(helper, router, barrel);
                    }
                    profile(helper, router, barrels[0], Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> {
                    for (int i = 0; i < max; i++) {
                        helper.assertTrue(count(helper, barrels[i]) > 0, "a máquina " + (i + 1) + " não recebeu");
                    }
                })
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, barrels[max]) == 0, "a máquina além do limite recebeu");
                    helper.assertTrue(remotePort(helper, router, barrels[max - 1], Direction.UP), "a última no limite sem porta");
                    helper.assertFalse(remotePort(helper, router, barrels[max], Direction.UP), "porta além do limite");
                })
                .thenSucceed();
    }

    /** Sem o Upgrade de Alcance, as máquinas vinculadas ficam guardadas e não viram porta. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void withoutUpgradeRemotesDoNotMove(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    link(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(count(helper, REMOTE_CHEST) == 0, "a remota recebeu sem o upgrade");
                    helper.assertTrue(count(helper, CHEST) == 16, "o baú preso perdeu itens");
                    helper.assertFalse(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta remota sem o upgrade");
                })
                .thenSucceed();
    }

    /** Tirar o upgrade com as remotas movendo: as portas delas saem na remontagem e nada mais chega. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void removingUpgradeStopsRemotes(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 1));
        int[] before = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    link(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, REMOTE_CHEST) == 1, "a remota não recebeu com o upgrade"))
                .thenExecute(() -> {
                    helper.assertTrue(router.removeRangeUpgrade().is(ModItems.RANGE_UPGRADE.get()), "o upgrade não saiu");
                    container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
                    before[0] = count(helper, REMOTE_CHEST);
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(count(helper, REMOTE_CHEST) == before[0], "a remota recebeu depois de tirar o upgrade");
                    helper.assertTrue(count(helper, CHEST) == 16, "o baú preso perdeu itens");
                    helper.assertFalse(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta remota sem o upgrade");
                })
                .thenSucceed();
    }

    /** Uma máquina fora do raio vinculada primeiro não gasta vaga: as N de dentro do raio movem todas. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void outOfRangeDoesNotTakeASlot(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        int radius = Config.machineRadius(RouterTier.ADVANCED);
        int max = Config.maxMachines(RouterTier.ADVANCED);
        helper.assertTrue(max >= 1 && max <= 18, "teste pensado para até 18 máquinas, config com " + max);
        BlockPos outside = new BlockPos(1, 3 + radius, 1);
        helper.setBlock(outside, Blocks.BARREL);
        BlockPos[] barrels = new BlockPos[max];
        for (int i = 0; i < barrels.length; i++) {
            barrels[i] = new BlockPos(i % 3, 4 + i / 9, i / 3 % 3);
            helper.setBlock(barrels[i], Blocks.BARREL);
        }
        Container chest = container(helper, CHEST);
        for (int i = 0; i < chest.getContainerSize(); i++) {
            chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    link(helper, router, outside);
                    for (BlockPos barrel : barrels) {
                        link(helper, router, barrel);
                    }
                    profile(helper, router, outside, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> {
                    for (int i = 0; i < max; i++) {
                        helper.assertTrue(count(helper, barrels[i]) > 0, "a máquina " + (i + 1) + " no raio não recebeu");
                    }
                })
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, outside) == 0, "a máquina fora do raio recebeu");
                    helper.assertTrue(remotePort(helper, router, barrels[max - 1], Direction.UP), "a última no raio sem porta");
                    helper.assertFalse(remotePort(helper, router, outside, Direction.UP), "porta fora do raio");
                })
                .thenSucceed();
    }

    /** Desvincular tira as portas da máquina na remontagem e nada mais chega a ela. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void unlinkRemovesPorts(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 1));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    link(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, REMOTE_CHEST) == 1, "a remota não recebeu"))
                .thenExecute(() -> {
                    helper.assertTrue(router.unlinkMachine(helper.absolutePos(REMOTE_CHEST)), "não desvinculou");
                    container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(count(helper, REMOTE_CHEST) == 1, "a máquina desvinculada recebeu");
                    helper.assertTrue(count(helper, CHEST) == 16, "o baú preso perdeu itens");
                    helper.assertFalse(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta da desvinculada");
                })
                .thenSucceed();
    }

    /**
     * Baixar o raio na config (num Elite, que nenhum outro teste usa com remotas) relê as portas na
     * remontagem: a remota a 4 blocos para de mover. O raio só é lido na remontagem, então o passo que
     * confere baixa a config, roda alguns ticks do motor à mão e restaura no {@code finally}, tudo
     * síncrono (a config nunca fica baixa entre ticks do jogo). Num lote próprio: a
     * recarga da config relê (e acorda) as portas de todos os roteadores com alcance, o que atrapalharia
     * os testes de sono que rodariam em paralelo.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "wirelessautomate_config")
    public static void configRadiusRereadsPorts(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ELITE);
        router.setNetworkId(NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "raio").id());
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 1));
        var radius = Config.TIERS.get(RouterTier.ELITE).machineRadius();
        int[] original = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    link(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, REMOTE_CHEST) == 1, "a remota não recebeu"))
                .thenExecute(() -> {
                    original[0] = radius.get();
                    helper.assertTrue(original[0] >= 4, "o raio do Elite já não alcança a remota: " + original[0]);
                    container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
                    // A config baixa e volta no mesmo passo síncrono, para a restauração ser garantida pelo
                    // finally. O raio só é lido na remontagem: os ticks do motor rodam aqui, com ele baixo.
                    MinecraftServer server = helper.getLevel().getServer();
                    try {
                        radius.set(2);
                        NetworkManager.configChanged();
                        for (int tick = 0; tick < 5; tick++) {
                            NetworkManager.get().tick(server);
                        }
                        helper.assertFalse(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta fora do raio novo");
                        helper.assertTrue(count(helper, REMOTE_CHEST) == 1, "a remota recebeu fora do raio novo");
                        helper.assertTrue(count(helper, CHEST) == 16, "o baú preso perdeu itens");
                    } finally {
                        radius.set(original[0]);
                        NetworkManager.configChanged();
                    }
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ acordar as máquinas remotas

    /** Um sono bem maior que os 20 ticks em que o aviso tem de chegar. */
    private static final int LONG_SLEEP = 300;

    /** O {@code IItemHandler} da máquina em {@code relative} pela face de cima; mexer nele chama {@code setChanged}. */
    private static IItemHandler handler(GameTestHelper helper, BlockPos relative) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(relative), Direction.UP);
        helper.assertTrue(handler != null, "sem IItemHandler em " + relative);
        return handler;
    }

    private static int countOf(GameTestHelper helper, BlockPos relative, net.minecraft.world.item.Item item) {
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

    private static boolean sleep(GameTestHelper helper, RouterBlockEntity router, BlockPos relativeOrNull) {
        return NetworkManager.get().sleepForTests(router, relativeOrNull == null ? null : helper.absolutePos(relativeOrNull),
                ResourceType.ITEM, Direction.UP, helper.getLevel().getServer().getTickCount(), LONG_SLEEP);
    }

    /**
     * Uma origem que espera destino: barril em {@code relative}, com terra e um roteador Básico em
     * cima, na rede, extraindo. O destino dela fica cheio de terra ({@link #fillButOne}), então ela
     * oferece, é recusada por espaço e dorme esperando destino.
     */
    private static RouterBlockEntity waitingFeeder(GameTestHelper helper, BlockPos relative, UUID network) {
        helper.setBlock(relative, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        helper.setBlock(relative.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(relative.above());
        router.setNetworkId(network);
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        container(helper, relative).setItem(0, new ItemStack(Items.DIRT, 8));
        return router;
    }

    /** Enche o baú de terra, menos o slot 0, que fica com 1 pedregulho: só aceita mais pedregulho. */
    private static void fillButOne(GameTestHelper helper, BlockPos relative) {
        Container container = container(helper, relative);
        container.setItem(0, new ItemStack(Items.COBBLESTONE, 1));
        for (int i = 1; i < container.getContainerSize(); i++) {
            container.setItem(i, new ItemStack(Items.DIRT, 64));
        }
    }

    /**
     * Origem remota vazia e dormindo um sono longo: pôr um item no baú remoto pelo {@code IItemHandler}
     * (que chama {@code setChanged}) acorda a porta pelo mixin, e o item chega à presa em menos de 20
     * ticks.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void remoteSourceWakesOnSetChanged(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        long[] insertedAt = {0};
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    link(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta da remota"))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(RemoteIndex.get().isIndexed(router, helper.absolutePos(REMOTE_CHEST)), "remota fora do índice");
                    helper.assertTrue(sleep(helper, router, REMOTE_CHEST), "a porta da remota sumiu");
                    handler(helper, REMOTE_CHEST).insertItem(0, new ItemStack(Items.COBBLESTONE, 5), false);
                    insertedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, CHEST) == 5, "o item não seguiu para a presa"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - insertedAt[0] < 20,
                        "a remota demorou " + (helper.getTick() - insertedAt[0]) + " ticks para acordar"))
                .thenSucceed();
    }

    /**
     * Destino remoto cheio, dormindo, e a origem presa esperando por ele: tirar uma pilha do baú remoto
     * pelo {@code IItemHandler} acorda o destino e a origem que espera, e a entrega sai em menos de 20
     * ticks.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fullRemoteDestinationWakesWhenEmptied(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        Container full = container(helper, REMOTE_CHEST);
        for (int i = 0; i < full.getContainerSize(); i++) {
            full.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        long[] freedAt = {0};
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    link(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta da remota"))
                // A origem tenta, o destino cheio recusa e ela passa a dormir esperando destino.
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(countOf(helper, CHEST, Items.COBBLESTONE) == 10, "a presa entregou num destino cheio");
                    helper.assertTrue(sleep(helper, router, null), "a porta da presa sumiu");
                    helper.assertTrue(sleep(helper, router, REMOTE_CHEST), "a porta da remota sumiu");
                    helper.assertTrue(handler(helper, REMOTE_CHEST).extractItem(3, 64, false).getCount() == 64, "não tirou a pilha");
                    freedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(countOf(helper, REMOTE_CHEST, Items.COBBLESTONE) == 10,
                        "a entrega no destino remoto não saiu"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - freedAt[0] < 20,
                        "o destino remoto demorou " + (helper.getTick() - freedAt[0]) + " ticks para acordar"))
                .thenSucceed();
    }

    /**
     * Revisão I2: a origem remota de um roteador entregando na presa do mesmo roteador é uma entrega
     * (compara a máquina, não o nó). O barril, que espera a presa (cheia de terra) abrir espaço, está num
     * sono longo; a entrega de pedregulho da remota na presa não pode acordá-lo (o tick em que ele
     * acorda não muda).
     */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void remoteDeliveryIntoAttachedDoesNotWakeFeeders(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        RouterBlockEntity feeder = waitingFeeder(helper, new BlockPos(0, 1, 0), router.networkId(ResourceType.ITEM));
        fillButOne(helper, CHEST);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        long[] feederWake = {0};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, router);
                    waitLoaded(helper, feeder);
                })
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    link(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta da remota"))
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().waitsDestination(feeder, null, ResourceType.ITEM,
                        Direction.UP), "o barril não espera destino"))
                .thenExecute(() -> {
                    helper.assertTrue(sleep(helper, feeder, null), "a porta do barril sumiu");
                    feederWake[0] = NetworkManager.get().sourceWakeAt(feeder, null, ResourceType.ITEM, Direction.UP);
                    handler(helper, REMOTE_CHEST).insertItem(0, new ItemStack(Items.COBBLESTONE, 5), false);
                })
                .thenWaitUntil(() -> helper.assertTrue(countOf(helper, CHEST, Items.COBBLESTONE) == 6, "a remota não entregou na presa"))
                .thenExecute(() -> helper.assertValueEqual(NetworkManager.get().sourceWakeAt(feeder, null, ResourceType.ITEM,
                        Direction.UP), feederWake[0], "a entrega da remota na presa acordou o barril; o tick do despertar"))
                .thenSucceed();
    }

    /**
     * A mesma regra numa máquina remota ({@code wakeMachine}, pelo mixin): a presa entregando
     * pedregulho na remota não acorda o barril que espera a remota (cheia de terra) abrir espaço.
     */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void deliveryIntoRemoteDoesNotWakeFeeders(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        RouterBlockEntity feeder = waitingFeeder(helper, new BlockPos(0, 1, 0), router.networkId(ResourceType.ITEM));
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        fillButOne(helper, REMOTE_CHEST);
        long[] feederWake = {0};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    waitLoaded(helper, router);
                    waitLoaded(helper, feeder);
                })
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    link(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta da remota"))
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().waitsDestination(feeder, null, ResourceType.ITEM,
                        Direction.UP), "o barril não espera destino"))
                .thenExecute(() -> {
                    helper.assertTrue(sleep(helper, feeder, null), "a porta do barril sumiu");
                    feederWake[0] = NetworkManager.get().sourceWakeAt(feeder, null, ResourceType.ITEM, Direction.UP);
                    handler(helper, CHEST).insertItem(0, new ItemStack(Items.COBBLESTONE, 5), false);
                })
                .thenWaitUntil(() -> helper.assertTrue(countOf(helper, REMOTE_CHEST, Items.COBBLESTONE) == 6,
                        "a presa não entregou na remota"))
                .thenExecute(() -> helper.assertValueEqual(NetworkManager.get().sourceWakeAt(feeder, null, ResourceType.ITEM,
                        Direction.UP), feederWake[0], "a entrega da presa na remota acordou o barril; o tick do despertar"))
                .thenSucceed();
    }

    /**
     * Revisão M4: a carga do chunk (o método que o {@code ChunkEvent.Load} chama) acorda a porta
     * dormindo de uma remota do chunk. Aqui a porta já tinha cache de capability; o caso sem cache usa
     * o mesmo caminho (acordar a porta, e a visita seguinte cria o cache), mas não é isolado no teste.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chunkLoadWakesRemote(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    link(helper, router, REMOTE_CHEST);
                    profile(helper, router, REMOTE_CHEST, Direction.UP, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, REMOTE_CHEST, Direction.UP), "porta da remota"))
                .thenExecute(() -> {
                    BlockPos remote = helper.absolutePos(REMOTE_CHEST);
                    long now = helper.getLevel().getServer().getTickCount();
                    helper.assertTrue(sleep(helper, router, REMOTE_CHEST), "a porta da remota sumiu");
                    helper.assertFalse(NetworkManager.get().sourceAwake(router, remote, ResourceType.ITEM, Direction.UP, now),
                            "a porta não dormiu");
                    RemoteIndex.get().chunkLoaded(helper.getLevel(), helper.getLevel().getChunk(remote));
                    helper.assertTrue(NetworkManager.get().sourceAwake(router, remote, ResourceType.ITEM, Direction.UP, now),
                            "a carga do chunk não acordou a porta");
                })
                .thenSucceed();
    }

    /**
     * Revisão M5, pela carga do chunk: a máquina de teste sem block entity ({@link TestMachines#BACK_SLOT})
     * girada no mundo não avisa nada; a carga do chunk relê a frente e "Trás" passa para o lado novo.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chunkLoadRereadsFront(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        BlockPos machine = new BlockPos(0, 1, 0);
        helper.setBlock(machine, TestMachines.BACK_SLOT.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        TestMachines.reset(helper.absolutePos(machine));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    link(helper, router, machine);
                    profile(helper, router, machine, Direction.SOUTH, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, machine, Direction.WEST), "Trás da frente Leste"))
                .thenExecute(() -> {
                    helper.setBlock(machine, TestMachines.BACK_SLOT.defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
                    BlockPos absolute = helper.absolutePos(machine);
                    RemoteIndex.get().chunkLoaded(helper.getLevel(), helper.getLevel().getChunk(absolute));
                    RemoteMachines remotes = router.remoteMachines();
                    helper.assertValueEqual(remotes.machine(remotes.index(absolute)).front(), Direction.SOUTH.ordinal(), "frente relida");
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, machine, Direction.NORTH), "Trás da frente Sul"))
                .thenExecute(() -> {
                    helper.assertFalse(remotePort(helper, router, machine, Direction.WEST), "porta no lado velho");
                    TestMachines.reset(helper.absolutePos(machine));
                })
                .thenSucceed();
    }

    /**
     * Revisão final I1: a máquina de teste sem block entity ({@link TestMachines#BACK_SLOT}) girada por
     * {@code setBlock} não chama {@code setChanged} nem invalida a capability, então a frente fica a velha.
     * Uma remontagem vinda de outra coisa (o modo de outra face do roteador) relê a frente e "Trás"
     * passa para o lado novo.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void rebuildRereadsFront(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        BlockPos machine = new BlockPos(0, 1, 0);
        helper.setBlock(machine, TestMachines.BACK_SLOT.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        TestMachines.reset(helper.absolutePos(machine));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                    link(helper, router, machine);
                    profile(helper, router, machine, Direction.SOUTH, PortMode.EXTRACT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, machine, Direction.WEST), "Trás da frente Leste"))
                .thenExecute(() -> {
                    BlockPos absolute = helper.absolutePos(machine);
                    helper.setBlock(machine, TestMachines.BACK_SLOT.defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
                    RemoteMachines remotes = router.remoteMachines();
                    helper.assertValueEqual(remotes.machine(remotes.index(absolute)).front(), Direction.EAST.ordinal(),
                            "a frente mudou sem aviso nem remontagem");
                    router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, machine, Direction.NORTH), "Trás da frente Sul"))
                .thenExecute(() -> {
                    BlockPos absolute = helper.absolutePos(machine);
                    RemoteMachines remotes = router.remoteMachines();
                    helper.assertValueEqual(remotes.machine(remotes.index(absolute)).front(), Direction.SOUTH.ordinal(), "frente relida");
                    helper.assertFalse(remotePort(helper, router, machine, Direction.WEST), "porta no lado velho");
                    TestMachines.reset(helper.absolutePos(machine));
                })
                .thenSucceed();
    }

    /**
     * Revisão M5, pelo mixin: uma fornalha remota girada (o block entity fica; a capability não é
     * invalidada) relê a frente no {@code setChanged} seguinte.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void setChangedRereadsFront(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        BlockPos furnace = new BlockPos(0, 1, 0);
        helper.setBlock(furnace, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    link(helper, router, furnace);
                    profile(helper, router, furnace, Direction.UP, PortMode.INSERT);
                })
                .thenWaitUntil(() -> helper.assertTrue(remotePort(helper, router, furnace, Direction.UP), "porta da fornalha"))
                .thenExecute(() -> {
                    BlockPos absolute = helper.absolutePos(furnace);
                    RemoteMachines remotes = router.remoteMachines();
                    helper.setBlock(furnace, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH));
                    helper.assertValueEqual(remotes.machine(remotes.index(absolute)).front(), Direction.EAST.ordinal(),
                            "frente antes do setChanged");
                    handler(helper, furnace).insertItem(0, new ItemStack(Items.IRON_ORE, 1), false);
                    helper.assertValueEqual(remotes.machine(remotes.index(absolute)).front(), Direction.SOUTH.ordinal(),
                            "frente depois do setChanged");
                })
                .thenSucceed();
    }

    /** Sem o upgrade, a posição da máquina vinculada não fica no índice do mixin; com ele, fica; tirando, sai. */
    @GameTest(template = "empty")
    public static void indexFollowsUpgrade(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    BlockPos remote = helper.absolutePos(REMOTE_CHEST);
                    link(helper, router, REMOTE_CHEST);
                    helper.assertFalse(RemoteIndex.get().isIndexed(router, remote), "no índice sem o upgrade");
                    router.setRangeUpgrade(rangeUpgrade());
                    helper.assertTrue(RemoteIndex.get().isIndexed(router, remote), "fora do índice com o upgrade");
                    router.removeRangeUpgrade();
                    helper.assertFalse(RemoteIndex.get().isIndexed(router, remote), "no índice depois de tirar o upgrade");
                    router.setRangeUpgrade(rangeUpgrade());
                    router.unlinkMachine(remote);
                    helper.assertFalse(RemoteIndex.get().isIndexed(router, remote), "no índice depois de desvincular");
                })
                .thenSucceed();
    }

    /**
     * Só as máquinas ativas entram no índice: a de fora do raio não entra; com o limite do Avançado
     * cheio, a seguinte não entra até uma das primeiras ser desvinculada.
     */
    @GameTest(template = "empty")
    public static void onlyActiveMachinesIndexed(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        int radius = Config.machineRadius(RouterTier.ADVANCED);
        int max = Config.maxMachines(RouterTier.ADVANCED);
        BlockPos outside = new BlockPos(1, 3 + radius, 1);
        helper.setBlock(outside, Blocks.BARREL);
        BlockPos[] inside = new BlockPos[max + 1];
        int n = 0;
        for (int y = 3; y <= 5 && n < inside.length; y++) {
            for (int x = 0; x <= 2 && n < inside.length; x += 2) {
                for (int z = 0; z <= 2 && n < inside.length; z += 2) {
                    inside[n] = new BlockPos(x, y, z);
                    helper.setBlock(inside[n++], Blocks.BARREL);
                }
            }
        }
        int placed = n;
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    helper.assertTrue(placed == max + 1 && radius >= 3, "a config padrão mudou: limite " + max + ", raio " + radius);
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, outside);
                    for (BlockPos pos : inside) {
                        link(helper, router, pos);
                    }
                    RemoteIndex index = RemoteIndex.get();
                    helper.assertFalse(index.isIndexed(router, helper.absolutePos(outside)), "fora do raio no índice");
                    for (int i = 0; i < max; i++) {
                        helper.assertTrue(index.isIndexed(router, helper.absolutePos(inside[i])), "ativa fora do índice: " + i);
                    }
                    BlockPos extra = helper.absolutePos(inside[max]);
                    helper.assertFalse(index.isIndexed(router, extra), "além do limite no índice");
                    router.unlinkMachine(helper.absolutePos(inside[0]));
                    helper.assertTrue(index.isIndexed(router, extra), "a seguinte não entrou ao abrir a vaga");
                })
                .thenSucceed();
    }

    /** Quebrar o roteador ({@code destroyBlock}) tira as máquinas dele do índice. */
    @GameTest(template = "empty")
    public static void breakingRouterRemovesFromIndex(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        helper.setBlock(REMOTE_CHEST, Blocks.CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, REMOTE_CHEST);
                    helper.assertTrue(RemoteIndex.get().isIndexed(router, helper.absolutePos(REMOTE_CHEST)), "fora do índice");
                    helper.destroyBlock(ROUTER);
                    helper.assertBlockNotPresent(ModBlocks.ROUTER.get(), ROUTER);
                    helper.assertFalse(RemoteIndex.get().isIndexed(router, helper.absolutePos(REMOTE_CHEST)),
                            "o roteador quebrado ficou no índice");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ Vinculador no modo Máquinas

    private static final BlockPos SECOND_ROUTER_MACHINE = new BlockPos(2, 1, 2);
    private static final BlockPos STONE = new BlockPos(0, 1, 1);
    private static final BlockPos COMPOSTER = new BlockPos(2, 1, 1);

    private static ItemStack machinesLinker() {
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        LinkerItem.setMode(linker, LinkerMode.MACHINES);
        return linker;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer linkerPlayer(GameTestHelper helper, ItemStack linker) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, linker);
        return player;
    }

    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        player.containerMenu = player.inventoryMenu;
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /** Clique direito com o item da mão principal num bloco (relativo), pelo caminho do jogo. */
    private static void click(GameTestHelper helper, ServerPlayer player, BlockPos relative, boolean sneak) {
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

    private static RangeLinks.Result toggle(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        return RangeLinks.toggle(player, player.getMainHandItem(), helper.absolutePos(relative));
    }

    private static void assertRefused(GameTestHelper helper, RangeLinks.Result result, LinkRefusal refusal, String what) {
        helper.assertTrue(result.status() == RangeLinks.Status.REFUSED && result.refusal() == refusal,
                what + ": esperava " + refusal + ", veio " + result);
    }

    /**
     * Modo Máquinas pelo clique: sem roteador escolhido não vincula; o clique no roteador o escolhe;
     * sem o upgrade recusa; com ele, o clique na fornalha vincula (e entra no índice) e o segundo
     * desvincula. Shift + clique marca um canto.
     */
    @GameTest(template = "empty")
    public static void linkerClickLinksAndUnlinks(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        BlockPos furnace = helper.absolutePos(FURNACE_A);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(), RangeLinks.Status.NO_ROUTER,
                                "sem roteador escolhido");
                        click(helper, player, ROUTER, false);
                        helper.assertValueEqual(LinkerItem.router(linker),
                                GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()), "roteador escolhido");
                        assertRefused(helper, toggle(helper, player, FURNACE_A), LinkRefusal.NO_UPGRADE, "sem upgrade");
                        router.setRangeUpgrade(rangeUpgrade());
                        click(helper, player, FURNACE_A, false);
                        helper.assertTrue(router.remoteMachines().contains(furnace), "o clique não vinculou");
                        helper.assertTrue(RemoteIndex.get().isIndexed(router, furnace), "fora do índice");
                        click(helper, player, FURNACE_A, false);
                        helper.assertFalse(router.remoteMachines().contains(furnace), "o segundo clique não desvinculou");
                        helper.assertFalse(RemoteIndex.get().isIndexed(router, furnace), "ficou no índice");
                        click(helper, player, FURNACE_A, true);
                        helper.assertTrue(LinkerItem.area(linker) != null, "Shift + clique não marcou o canto");
                        helper.assertFalse(router.remoteMachines().contains(furnace), "Shift + clique vinculou");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Recusas do clique: o próprio roteador, outro roteador, a máquina presa, um bloco sem capability,
     * ar e uma máquina fora do raio. Um bloco com capability sem block entity (composteira) vincula.
     * Com o roteador escolhido quebrado, a resposta é própria e o componente fica.
     */
    @GameTest(template = "empty")
    public static void linkerClickRefusals(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.setBlock(SECOND_ROUTER_MACHINE, Blocks.CHEST);
        helper.setBlock(SECOND_ROUTER_MACHINE.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        helper.setBlock(STONE, Blocks.STONE);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        int radius = Config.machineRadius(RouterTier.ADVANCED);
        BlockPos outside = new BlockPos(1, 3 + radius, 1);
        helper.setBlock(outside, Blocks.BARREL);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        router.setRangeUpgrade(rangeUpgrade());
                        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                        assertRefused(helper, toggle(helper, player, ROUTER), LinkRefusal.SELF, "o próprio roteador");
                        assertRefused(helper, toggle(helper, player, SECOND_ROUTER_MACHINE.above()), LinkRefusal.ROUTER,
                                "outro roteador");
                        assertRefused(helper, toggle(helper, player, CHEST), LinkRefusal.ATTACHED, "a máquina presa");
                        assertRefused(helper, toggle(helper, player, STONE), LinkRefusal.NOT_A_MACHINE, "pedra");
                        assertRefused(helper, toggle(helper, player, new BlockPos(0, 2, 0)), LinkRefusal.NOT_A_MACHINE, "ar");
                        assertRefused(helper, toggle(helper, player, outside), LinkRefusal.OUT_OF_RANGE, "fora do raio");
                        helper.assertTrue(router.remoteMachines().isEmpty(), "uma recusa vinculou");
                        helper.assertValueEqual(toggle(helper, player, COMPOSTER).status(), RangeLinks.Status.LINKED,
                                "a composteira (capability sem block entity) no clique");

                        helper.destroyBlock(ROUTER);
                        helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(),
                                RangeLinks.Status.ROUTER_MISSING, "roteador quebrado");
                        helper.assertTrue(LinkerItem.router(linker) != null, "limpou o roteador escolhido");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** Com o limite do Avançado cheio, o clique seguinte é recusado com {@code LIMIT}. */
    @GameTest(template = "empty")
    public static void linkerClickRefusesOverLimit(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        int max = Config.maxMachines(RouterTier.ADVANCED);
        BlockPos[] barrels = new BlockPos[max + 1];
        int n = 0;
        for (int y = 3; y <= 5 && n < barrels.length; y++) {
            for (int x = 0; x <= 2 && n < barrels.length; x += 2) {
                for (int z = 0; z <= 2 && n < barrels.length; z += 2) {
                    barrels[n] = new BlockPos(x, y, z);
                    helper.setBlock(barrels[n++], Blocks.BARREL);
                }
            }
        }
        int placed = n;
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(placed == max + 1, "a config padrão mudou: limite " + max);
                        router.setRangeUpgrade(rangeUpgrade());
                        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                        for (int i = 0; i < max; i++) {
                            helper.assertValueEqual(toggle(helper, player, barrels[i]).status(), RangeLinks.Status.LINKED,
                                    "barril " + i);
                        }
                        assertRefused(helper, toggle(helper, player, barrels[max]), LinkRefusal.LIMIT, "além do limite");
                        helper.assertFalse(router.remoteMachines().contains(helper.absolutePos(barrels[max])),
                                "vinculou além do limite");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * O Vincular da tela no modo Máquinas: só entram block entities com capability de uma aba marcada
     * (a composteira, sem block entity, fica de fora; com a aba Itens, a bateria também), nunca a
     * máquina presa nem o roteador; de novo, as vinculadas contam como já estavam.
     */
    @GameTest(template = "empty")
    public static void linkerAreaLinksBlockEntitiesOfMarkedTabs(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        BlockPos barrel = new BlockPos(0, 3, 0);
        BlockPos furnace = new BlockPos(2, 3, 2);
        BlockPos composter = new BlockPos(2, 3, 0);
        BlockPos battery = new BlockPos(0, 3, 2);
        helper.setBlock(barrel, Blocks.BARREL);
        helper.setBlock(furnace, Blocks.FURNACE);
        helper.setBlock(composter, Blocks.COMPOSTER);
        helper.setBlock(battery, ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState());
        ItemStack linker = machinesLinker();
        LinkerItem.setTabs(linker, LinkerTabs.of(ResourceType.ITEM));
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        router.setRangeUpgrade(rangeUpgrade());
                        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                        LinkerItem.setArea(linker, LinkerArea.firstCorner(helper.getLevel().dimension(),
                                helper.absolutePos(new BlockPos(0, 1, 0))).withSecond(helper.absolutePos(new BlockPos(2, 3, 2))));
                        LinkerMenu menu = new LinkerMenu(CONTAINER_ID, player.getInventory(), InteractionHand.MAIN_HAND,
                                LinkerSnapshot.capture(player, linker, null));
                        player.containerMenu = menu;
                        helper.assertValueEqual(menu.snapshot().problem(), LinkerProblem.NONE, "problema na tela");
                        helper.assertTrue(LinkerActions.handle(player, LinkerActionPayload.of(CONTAINER_ID, Op.LINK)),
                                "Vincular pela tela recusou");
                        RemoteMachines remotes = router.remoteMachines();
                        helper.assertTrue(remotes.contains(helper.absolutePos(barrel)), "barril fora");
                        helper.assertTrue(remotes.contains(helper.absolutePos(furnace)), "fornalha fora");
                        helper.assertFalse(remotes.contains(helper.absolutePos(composter)), "composteira sem block entity entrou");
                        helper.assertFalse(remotes.contains(helper.absolutePos(battery)), "bateria entrou com a aba Itens");
                        helper.assertFalse(remotes.contains(helper.absolutePos(CHEST)), "a máquina presa entrou");
                        helper.assertFalse(remotes.contains(router.getBlockPos()), "o roteador entrou");

                        RangeLinks.Result again = RangeLinks.linkArea(player, linker);
                        helper.assertTrue(again.ok() && again.linked() == 0 && again.already() == 2,
                                "de novo: " + again);

                        LinkerItem.setTabs(linker, LinkerTabs.of(ResourceType.ENERGY));
                        RangeLinks.Result energy = RangeLinks.linkArea(player, linker);
                        helper.assertTrue(energy.ok() && energy.linked() == 1, "aba Energia: " + energy);
                        helper.assertTrue(remotes.contains(helper.absolutePos(battery)), "bateria fora com a aba Energia");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * O Configurador nunca fica no modo Máquinas: gravar Máquinas vira pincel, um componente com
     * Máquinas é lido como pincel e o ciclo dele só passa por Pincel e Área.
     */
    @GameTest(template = "empty")
    public static void configuratorNeverMachines(GameTestHelper helper) {
        ItemStack configurator = new ItemStack(ModItems.CONFIGURATOR.get());
        ConfiguratorItem.setMode(configurator, LinkerMode.MACHINES);
        helper.assertValueEqual(ConfiguratorItem.mode(configurator), LinkerMode.SINGLE, "setMode(MACHINES)");
        helper.assertFalse(configurator.has(ModDataComponents.CONFIGURATOR_MODE.get()), "gravou Máquinas");
        configurator.set(ModDataComponents.CONFIGURATOR_MODE.get(), LinkerMode.MACHINES);
        helper.assertValueEqual(ConfiguratorItem.mode(configurator), LinkerMode.SINGLE, "componente com Máquinas");
        helper.assertValueEqual(ConfiguratorItem.pasteMode(configurator), PasteMode.BRUSH, "modo de colar");
        PasteMode mode = ConfiguratorItem.pasteMode(configurator);
        for (int i = 0; i < PasteMode.values().length * 2; i++) {
            mode = mode.next();
            ConfiguratorItem.setPasteMode(configurator, mode);
            helper.assertTrue(ConfiguratorItem.mode(configurator) != LinkerMode.MACHINES, "o ciclo chegou a Máquinas");
        }
        helper.assertValueEqual(LinkerMode.MACHINES.toggled(), LinkerMode.AREA, "toggled de Máquinas");
        helper.assertValueEqual(LinkerMode.AREA.toggled(), LinkerMode.SINGLE, "toggled de Área");
        helper.succeed();
    }

    /** As chaves de tradução de uma mensagem e das partes dela, em ordem. */
    private static java.util.List<String> keys(Component text) {
        java.util.List<String> keys = new java.util.ArrayList<>();
        if (text.getContents() instanceof TranslatableContents contents) {
            keys.add(contents.getKey());
        }
        for (Component sibling : text.getSiblings()) {
            if (sibling.getContents() instanceof TranslatableContents contents) {
                keys.add(contents.getKey());
            }
        }
        return keys;
    }

    /**
     * As recusas do Vincular da área: com o limite 8 do Avançado, as 8 máquinas mais perto vinculam e a
     * mais longe dentro do raio fica além do limite; a de fora do raio é contada como fora do alcance;
     * a que um listener do {@code RightClickBlock} cancela (como um claim) é protegida e não gasta vaga.
     */
    @GameTest(template = "empty")
    public static void linkerAreaRefusals(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        BlockPos[] near = {
                new BlockPos(0, 3, 0), new BlockPos(2, 3, 0), new BlockPos(0, 3, 2), new BlockPos(2, 3, 2),
                new BlockPos(0, 4, 0), new BlockPos(2, 4, 0), new BlockPos(0, 4, 2), new BlockPos(2, 4, 2)};
        BlockPos claimed = new BlockPos(1, 3, 1);
        int radius = Config.machineRadius(RouterTier.ADVANCED);
        BlockPos farthest = new BlockPos(1, 2 + radius, 1);
        BlockPos outside = new BlockPos(1, 3 + radius, 1);
        for (BlockPos pos : near) {
            helper.setBlock(pos, Blocks.BARREL);
        }
        helper.setBlock(claimed, Blocks.BARREL);
        helper.setBlock(farthest, Blocks.BARREL);
        helper.setBlock(outside, Blocks.BARREL);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        BlockPos claimedAbsolute = helper.absolutePos(claimed);
        int[] posted = new int[1];
        Consumer<PlayerInteractEvent.RightClickBlock> claim = event -> {
            if (event.getEntity() == player) {
                posted[0]++;
                if (event.getPos().equals(claimedAbsolute)) {
                    event.setCanceled(true);
                }
            }
        };
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    NeoForge.EVENT_BUS.addListener(claim);
                    try {
                        helper.assertTrue(Config.maxMachines(RouterTier.ADVANCED) == near.length && radius >= 4,
                                "a config padrão mudou: limite " + Config.maxMachines(RouterTier.ADVANCED) + ", raio " + radius);
                        router.setRangeUpgrade(rangeUpgrade());
                        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                        LinkerItem.setArea(linker, LinkerArea.firstCorner(helper.getLevel().dimension(),
                                helper.absolutePos(new BlockPos(0, 1, 0))).withSecond(helper.absolutePos(outside.offset(1, 0, 1))));
                        RangeLinks.Result result = RangeLinks.linkArea(player, linker);
                        helper.assertTrue(result.ok() && result.linked() == near.length && result.overLimit() == 1
                                && result.outOfRange() == 1 && result.protectedCount() == 1 && result.already() == 0,
                                "contagens: " + result);
                        RemoteMachines remotes = router.remoteMachines();
                        for (BlockPos pos : near) {
                            helper.assertTrue(remotes.contains(helper.absolutePos(pos)), "a mais perto ficou de fora: " + pos);
                        }
                        helper.assertFalse(remotes.contains(claimedAbsolute), "a protegida pelo evento entrou");
                        helper.assertFalse(remotes.contains(helper.absolutePos(farthest)), "a mais longe passou do limite");
                        helper.assertFalse(remotes.contains(helper.absolutePos(outside)), "a de fora do raio entrou");
                        // o evento só sai para quem seria vinculado: as 8, a protegida e nenhuma recusada antes
                        helper.assertValueEqual(posted[0], near.length + 1, "eventos postados");
                        helper.assertValueEqual(keys(RangeLinks.areaMessage(result)), java.util.List.of(
                                "item.wirelessautomate.linker.range.area.linked",
                                "item.wirelessautomate.linker.range.area.out_of_range",
                                "item.wirelessautomate.linker.range.area.over_limit",
                                "item.wirelessautomate.linker.range.area.protected"), "mensagem");
                    } finally {
                        NeoForge.EVENT_BUS.unregister(claim);
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ tela: alvo de edição, snapshot e payloads

    /** Barril a 7 blocos do roteador (raio do Avançado é 6): fora do raio. */
    private static final BlockPos FAR_BARREL = new BlockPos(1, 9, 1);

    private static ResourceLocation furnaceId() {
        return BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);
    }

    /** Liga o upgrade (depois do {@code onLoad}) e vincula as duas fornalhas e o barril. */
    private static void linkAll(GameTestHelper helper, RouterBlockEntity router) {
        router.setRangeUpgrade(rangeUpgrade());
        link(helper, router, FURNACE_A);
        link(helper, router, FURNACE_B);
        link(helper, router, BARREL);
    }

    private static SetFacePayload setFace(RouterMenu menu, Direction face, PortMode mode) {
        return new SetFacePayload(menu.containerId, ResourceType.ITEM, face, mode, 0, RedstoneMode.IGNORE);
    }

    @GameTest(template = "empty")
    public static void screenGroupTargetEditsProfile(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        int furnaces = groupOf(router, a);
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.group(menu.containerId, furnaces, furnaceId())), "não escolheu o grupo");
                        helper.assertValueEqual(menu.target(), EditTarget.group(furnaces), "alvo no menu");

                        // Mudar uma face (canônica: SOUTH = Trás) muda o perfil, não a máquina presa.
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.SOUTH, PortMode.EXTRACT)),
                                "recusou a face do grupo");
                        helper.assertValueEqual(router.face(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH).mode(),
                                PortMode.EXTRACT, "perfil");
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.SOUTH).mode(), PortMode.NONE,
                                "a máquina presa mudou");
                        helper.assertFalse(router.remoteMachines().machine(router.remoteMachines().index(a)).hasException(),
                                "mudar o perfil criou exceção");

                        // O snapshot leva a visão das remotas, o alvo e as faces do perfil, com o visor na fornalha.
                        RouterSnapshot snapshot = menu.pollSnapshot();
                        helper.assertTrue(snapshot != null, "o snapshot não mudou com o alvo");
                        RangeView range = snapshot.range();
                        helper.assertTrue(range.present(), "sem a visão das remotas com o upgrade");
                        helper.assertValueEqual(range.radius(), Config.machineRadius(RouterTier.ADVANCED), "raio");
                        helper.assertValueEqual(range.max(), Config.maxMachines(RouterTier.ADVANCED), "limite");
                        helper.assertValueEqual(snapshot.target(), EditTarget.group(furnaces), "alvo no snapshot");
                        RangeView.GroupView group = range.group(furnaces);
                        helper.assertTrue(group != null && group.count() == 2 && group.exceptions() == 0
                                && group.block().equals(furnaceId()) && group.icon().is(Items.FURNACE), "grupo das fornalhas");
                        helper.assertValueEqual(range.machines().size(), 3, "máquinas");
                        RangeView.MachineView view = range.machine(router.remoteMachines().index(a));
                        helper.assertTrue(view.pos().equals(a) && view.group() == furnaces && view.distance() == 1
                                && view.state() == RangeView.MachineState.LOADED, "máquina A: " + view);
                        helper.assertValueEqual(snapshot.face(ResourceType.ITEM, Direction.SOUTH).mode(), PortMode.EXTRACT,
                                "face do perfil no snapshot");
                        helper.assertTrue(snapshot.face(ResourceType.ITEM, Direction.DOWN).slots() > 0,
                                "a face de baixo da fornalha sem slots");
                        helper.assertValueEqual(snapshot.face(ResourceType.FLUID, Direction.DOWN).slots(), -1, "fornalha com tanque");
                        helper.assertTrue(snapshot.machineState().is(Blocks.FURNACE)
                                && snapshot.machineState().getValue(AbstractFurnaceBlock.FACING) == Direction.NORTH,
                                "o visor não mostra a fornalha virada para Norte: " + snapshot.machineState());
                        helper.assertTrue(snapshot.machine().is(Items.FURNACE), "ícone do alvo");

                        // Ida e volta pelo codec, com a visão.
                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                                helper.getLevel().registryAccess());
                        RouterSnapshot.STREAM_CODEC.encode(buf, snapshot);
                        helper.assertTrue(RouterSnapshot.STREAM_CODEC.decode(buf).equals(snapshot), "snapshot pelo codec");

                        // Os slots de cartão mostram os do grupo, na face canônica; pôr pelo slot grava no grupo.
                        router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0, itemCard());
                        menu.select(ResourceType.ITEM, Direction.SOUTH);
                        helper.assertTrue(menu.getSlot(0).getItem().is(ModItems.FILTER_CARD.get()), "o slot não mostra o cartão do grupo");
                        menu.getSlot(1).set(itemCard());
                        helper.assertTrue(router.card(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 1)
                                .is(ModItems.FILTER_CARD.get()), "o slot não gravou no grupo");
                        helper.assertTrue(router.card(EditTarget.ATTACHED_TARGET, ResourceType.ITEM, Direction.SOUTH, 1).isEmpty(),
                                "o slot gravou na máquina presa");

                        // Voltar à presa.
                        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.attached(menu.containerId)),
                                "não voltou à presa");
                        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "o slot da presa mostra o cartão do grupo");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void screenMachineTargetCreatesException(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos b = helper.absolutePos(FURNACE_B);
                        int indexB = router.remoteMachines().index(b);
                        int furnaces = groupOf(router, b);
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.machine(menu.containerId, indexB, b)), "não escolheu a máquina");

                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.INSERT)),
                                "recusou a face da máquina");
                        helper.assertTrue(router.remoteMachines().machine(indexB).hasException(), "não criou a exceção");
                        helper.assertValueEqual(router.face(EditTarget.group(furnaces), ResourceType.ITEM, Direction.UP).mode(),
                                PortMode.NONE, "mudar a máquina mudou o perfil");
                        RouterSnapshot snapshot = menu.pollSnapshot();
                        helper.assertTrue(snapshot != null && snapshot.range().machine(indexB).exception()
                                && snapshot.range().group(furnaces).exceptions() == 1, "a exceção não aparece no snapshot");
                        helper.assertValueEqual(snapshot.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT,
                                "face da máquina no snapshot");

                        // Voltar ao perfil: com a posição errada, nada; com a certa, a exceção sai.
                        helper.assertFalse(ModPayloads.handleRevertMachine(player,
                                new RevertMachinePayload(menu.containerId, indexB, b.above())), "reverteu com a posição errada");
                        helper.assertTrue(ModPayloads.handleRevertMachine(player,
                                new RevertMachinePayload(menu.containerId, indexB, b)), "não reverteu");
                        helper.assertFalse(router.remoteMachines().machine(indexB).hasException(), "a exceção ficou");
                        helper.assertValueEqual(router.face(EditTarget.machine(indexB), ResourceType.ITEM, Direction.UP).mode(),
                                PortMode.NONE, "voltar ao perfil não voltou a face");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void screenUnlinkDropsGroupCards(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos barrel = helper.absolutePos(BARREL);
                        int index = router.remoteMachines().index(barrel);
                        int barrels = groupOf(router, barrel);
                        router.setCard(EditTarget.group(barrels), ResourceType.ITEM, Direction.DOWN, 0, itemCard());
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.machine(menu.containerId, index, barrel)), "não escolheu o barril");

                        helper.assertFalse(ModPayloads.handleUnlinkMachine(player,
                                new UnlinkMachinePayload(menu.containerId, index, helper.absolutePos(FURNACE_A))),
                                "desvinculou com a posição de outra máquina");
                        helper.assertTrue(router.remoteMachines().contains(barrel), "o barril saiu pela posição errada");
                        helper.assertTrue(ModPayloads.handleUnlinkMachine(player,
                                new UnlinkMachinePayload(menu.containerId, index, barrel)), "não desvinculou");
                        helper.assertFalse(router.remoteMachines().contains(barrel), "o barril continua vinculado");
                        helper.assertTrue(router.remoteMachines().group(barrels) == null, "o grupo vazio ficou");
                        helper.assertItemEntityPresent(ModItems.FILTER_CARD.get(), ROUTER, 2.0);
                        helper.assertTrue(menu.target().attached(), "o alvo continua na máquina desvinculada");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void screenInvalidTargetIgnored(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos a = helper.absolutePos(FURNACE_A);
                        BlockPos b = helper.absolutePos(FURNACE_B);
                        int furnaces = groupOf(router, a);
                        int cid = menu.containerId;

                        // Sem o upgrade, nenhum alvo remoto.
                        helper.assertFalse(ModPayloads.handleSelectTarget(player, SelectTargetPayload.group(cid, furnaces,
                                furnaceId())), "escolheu um grupo sem o upgrade");
                        router.setRangeUpgrade(rangeUpgrade());

                        // Bloco, posição ou índice que não batem.
                        helper.assertFalse(ModPayloads.handleSelectTarget(player, SelectTargetPayload.group(cid, furnaces,
                                BuiltInRegistries.BLOCK.getKey(Blocks.BARREL))), "escolheu o grupo com outro bloco");
                        helper.assertFalse(ModPayloads.handleSelectTarget(player, SelectTargetPayload.group(cid, furnaces + 100,
                                furnaceId())), "escolheu um grupo que não existe");
                        helper.assertFalse(ModPayloads.handleSelectTarget(player, SelectTargetPayload.machine(cid, 0, b)),
                                "escolheu a máquina 0 com a posição da 1");
                        helper.assertFalse(ModPayloads.handleSelectTarget(player, SelectTargetPayload.machine(cid, 5, a)),
                                "escolheu um índice fora da lista");
                        helper.assertTrue(menu.target().attached(), "um alvo inválido mudou o menu");
                        helper.assertFalse(ModPayloads.handleSelectTarget(player, SelectTargetPayload.attached(cid + 1)),
                                "aceitou outro containerId");

                        // A máquina escolhida muda de índice (outra antes dela sai): o alvo segue pela posição.
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.machine(cid, router.remoteMachines().index(b), b)), "não escolheu a B");
                        helper.assertTrue(router.unlinkMachine(a), "não desvinculou a A");
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.EXTRACT)),
                                "recusou a face da B depois de mudar de índice");
                        int indexB = router.remoteMachines().index(b);
                        helper.assertValueEqual(menu.target(), EditTarget.machine(indexB), "o alvo não seguiu a B");
                        helper.assertTrue(router.remoteMachines().machine(indexB).hasException(), "a B não ganhou a exceção");

                        // A máquina escolhida sai: a ação seguinte é ignorada e o alvo volta para a presa.
                        helper.assertTrue(router.unlinkMachine(b), "não desvinculou a B");
                        helper.assertFalse(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.INSERT)),
                                "aplicou a face num alvo que sumiu");
                        helper.assertTrue(menu.target().attached(), "o alvo não voltou para a presa");
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.NONE,
                                "a ação ignorada caiu na máquina presa");
                        // Daí em diante, na presa.
                        helper.assertTrue(ModPayloads.handleSetFace(player, setFace(menu, Direction.UP, PortMode.INSERT)),
                                "recusou a face da presa");
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT,
                                "a face da presa não mudou");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void snapshotWithoutUpgradeUnchanged(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, FURNACE_A);
                    router.configureFace(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 4, RedstoneMode.IGNORE);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        // Com máquinas guardadas mas sem o upgrade: sem visão, presa e faces absolutas da presa.
                        RouterSnapshot snapshot = RouterSnapshot.capture(router, player,
                                EditTarget.group(groupOf(router, helper.absolutePos(FURNACE_A))));
                        helper.assertTrue(snapshot.range() == RangeView.EMPTY, "visão das remotas sem o upgrade");
                        helper.assertTrue(snapshot.target().attached(), "alvo remoto sem o upgrade");
                        helper.assertTrue(snapshot.equals(RouterSnapshot.capture(router, player)), "o alvo mudou o snapshot");
                        helper.assertTrue(snapshot.machine().is(Items.CHEST), "o ícone não é o da presa");
                        for (ResourceType type : ResourceType.values()) {
                            for (Direction face : Direction.values()) {
                                FaceConfig config = router.face(type, face);
                                RouterSnapshot.FaceView view = snapshot.face(type, face);
                                helper.assertTrue(view.mode() == config.mode() && view.priority() == config.priority(),
                                        "face " + type + " " + face);
                            }
                        }
                        helper.assertValueEqual(snapshot.face(ResourceType.ITEM, Direction.UP).slots(), 27, "slots do baú");
                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                                helper.getLevel().registryAccess());
                        RouterSnapshot.STREAM_CODEC.encode(buf, snapshot);
                        helper.assertTrue(RouterSnapshot.STREAM_CODEC.decode(buf).equals(snapshot), "snapshot pelo codec");
                        // Sem o upgrade, o tempo não recaptura.
                        menu.pollSnapshot();
                        helper.assertTrue(menu.pollSnapshot() == null, "recapturou sem mudança e sem as remotas");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void snapshotMachineStates(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer[] holder = new ServerPlayer[1];
        RouterMenu[] menus = new RouterMenu[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    helper.setBlock(FAR_BARREL, Blocks.BARREL);
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FAR_BARREL);
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    menus[0] = openMenu(helper, router, holder);
                    RangeView range = menus[0].snapshot().range();
                    helper.assertValueEqual(range.machine(0).state(), RangeView.MachineState.OUT_OF_RANGE, "barril longe");
                    helper.assertValueEqual(range.machine(0).distance(), 7, "distância do barril longe");
                    helper.assertValueEqual(range.machine(1).state(), RangeView.MachineState.LOADED, "fornalha A");
                    helper.assertValueEqual(range.used(), 2, "vagas usadas");
                    // A fornalha B vira pedra: sem mexer na versão do roteador, a tela vê em até 20 ticks.
                    helper.setBlock(FURNACE_B, Blocks.STONE);
                })
                .thenExecuteAfter(RouterMenu.SAMPLE_TICKS + 1, () -> {
                    try {
                        menus[0].pollSnapshot();
                        RangeView range = menus[0].snapshot().range();
                        helper.assertValueEqual(range.machine(2).state(), RangeView.MachineState.MISSING, "fornalha que virou pedra");
                        helper.assertValueEqual(range.machine(1).state(), RangeView.MachineState.LOADED, "fornalha A depois");
                    } finally {
                        removePlayer(helper, holder[0]);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void snapshotOverLimit(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        router.setNetworkId(NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "limite").id());
        int max = Config.maxMachines(RouterTier.ADVANCED);
        java.util.List<BlockPos> barrels = new java.util.ArrayList<>();
        for (int i = 0; i <= max; i++) {
            BlockPos pos = new BlockPos(i % 3, 3 + i / 3, i % 3 == 1 ? 0 : 2);
            barrels.add(pos);
            helper.setBlock(pos, Blocks.BARREL);
        }
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    for (BlockPos pos : barrels) {
                        link(helper, router, pos);
                    }
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    try {
                        RangeView range = menu.snapshot().range();
                        helper.assertValueEqual(range.machine(max).state(), RangeView.MachineState.OVER_LIMIT, "a além do limite");
                        helper.assertValueEqual(range.machine(0).state(), RangeView.MachineState.LOADED, "a primeira");
                        helper.assertValueEqual(range.used(), max, "vagas usadas");
                    } finally {
                        removePlayer(helper, holder[0]);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void remoteFilterTargetFollowsTarget(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                        helper.assertTrue(menu.selectTarget(EditTarget.group(furnaces), null, furnaceId()), "não escolheu o grupo");
                        RouterFaceFilterTarget target = new RouterFaceFilterTarget(router, ResourceType.ITEM, Direction.EAST,
                                menu.targetRef());
                        Filter blacklist = Filter.EMPTY.withListMode(Filter.ListMode.BLACKLIST);
                        target.setFilter(blacklist);
                        helper.assertValueEqual(router.face(EditTarget.group(furnaces), ResourceType.ITEM, Direction.EAST)
                                .filter().listMode(), Filter.ListMode.BLACKLIST, "o filtro não foi para o perfil");
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.EAST).filter().listMode(),
                                Filter.ListMode.WHITELIST, "o filtro foi para a presa");
                        helper.assertTrue(target.stillValid(player), "o filtro do grupo fechou");

                        // O grupo some (as duas fornalhas saem): o filtro fecha e não grava.
                        router.unlinkMachine(helper.absolutePos(FURNACE_A));
                        router.unlinkMachine(helper.absolutePos(FURNACE_B));
                        helper.assertFalse(target.stillValid(player), "o filtro de um grupo que sumiu continua aberto");
                        target.setFilter(Filter.EMPTY);
                        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.EAST).filter().listMode(),
                                Filter.ListMode.WHITELIST, "o filtro de um grupo que sumiu gravou na presa");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    private static void assertAttachedCardsEmpty(GameTestHelper helper, RouterBlockEntity router, String what) {
        for (Direction face : Direction.values()) {
            for (int slot = 0; slot < RouterBlockEntity.CARD_SLOTS; slot++) {
                helper.assertTrue(router.card(EditTarget.ATTACHED_TARGET, ResourceType.ITEM, face, slot).isEmpty(),
                        what + ": cartão na presa em " + face);
            }
        }
    }

    /**
     * Tirar o upgrade com a tela num alvo remoto: o menu volta para a presa, na face onde o roteador está
     * preso (a mesma que o cliente adota, {@code snapshot.facing()}), e os cartões não ficam travados.
     */
    @GameTest(template = "empty")
    public static void screenUpgradeRemovedRestoresFace(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.group(menu.containerId, furnaces, furnaceId())), "não escolheu o grupo");
                        menu.select(ResourceType.ITEM, Direction.SOUTH);
                        router.removeRangeUpgrade();
                        RouterSnapshot snapshot = menu.pollSnapshot();
                        helper.assertTrue(menu.validTarget().attached(), "o alvo não voltou para a presa");
                        helper.assertValueEqual(menu.selectedFace(), router.facing(), "a face não voltou para a da presa");
                        helper.assertTrue(snapshot != null && !snapshot.range().present() && snapshot.target().attached(),
                                "o snapshot ainda traz o alvo remoto");
                        helper.assertFalse(menu.cardsLocked(), "os cartões ficaram travados sem o upgrade");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void screenShiftClickCardGoesToGroup(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                        helper.assertValueEqual(menu.selectedFace(), Direction.UP, "face inicial da presa");
                        helper.assertTrue(ModPayloads.handleSelectTarget(player,
                                SelectTargetPayload.group(menu.containerId, furnaces, furnaceId())), "não escolheu o grupo");
                        // A face vai para a Frente canônica e segue no snapshot.
                        helper.assertValueEqual(menu.selectedFace(), Direction.NORTH, "face padrão no remoto");
                        RouterSnapshot snapshot = menu.pollSnapshot();
                        helper.assertTrue(snapshot != null && snapshot.range().face() == Direction.NORTH,
                                "a face padrão não foi no snapshot");

                        player.getInventory().setItem(0, itemCard());
                        int hotbar0 = RouterMenu.UPGRADE_SLOT - 9;
                        menu.clicked(hotbar0, 0, ClickType.QUICK_MOVE, player);
                        helper.assertTrue(router.card(EditTarget.group(furnaces), ResourceType.ITEM, Direction.NORTH, 0)
                                .is(ModItems.FILTER_CARD.get()), "o Shift + clique não pôs o cartão no grupo");
                        helper.assertTrue(menu.getSlot(hotbar0).getItem().isEmpty(), "o cartão ficou no inventário");
                        assertAttachedCardsEmpty(helper, router, "Shift + clique com alvo de grupo");

                        // De volta à presa, a face de antes.
                        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.attached(menu.containerId)),
                                "não voltou à presa");
                        helper.assertValueEqual(menu.selectedFace(), Direction.UP, "a face da presa não voltou");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void screenLostTargetLocksCards(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    linkAll(helper, router);
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = openMenu(helper, router, holder);
                    ServerPlayer player = holder[0];
                    try {
                        BlockPos barrel = helper.absolutePos(BARREL);
                        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.machine(menu.containerId,
                                router.remoteMachines().index(barrel), barrel)), "não escolheu o barril");
                        helper.assertFalse(menu.cardsLocked(), "cartões travados com o alvo valendo");

                        // O barril sai com um cartão no cursor: o clique no slot não o põe na presa.
                        menu.setCarried(itemCard());
                        helper.assertTrue(router.unlinkMachine(barrel), "não desvinculou o barril");
                        menu.clicked(0, 0, ClickType.PICKUP, player);
                        helper.assertTrue(menu.getCarried().is(ModItems.FILTER_CARD.get()), "o cartão saiu do cursor");
                        helper.assertTrue(menu.target().attached(), "o alvo não voltou para a presa");
                        helper.assertTrue(menu.cardsLocked(), "cartões destravados com o alvo perdido");
                        assertAttachedCardsEmpty(helper, router, "clique com o alvo perdido");

                        // Shift + clique também não.
                        player.getInventory().setItem(0, itemCard());
                        int hotbar0 = RouterMenu.UPGRADE_SLOT - 9;
                        menu.clicked(hotbar0, 0, ClickType.QUICK_MOVE, player);
                        assertAttachedCardsEmpty(helper, router, "Shift + clique com o alvo perdido");

                        // Escolher a presa pela tela destrava.
                        helper.assertTrue(ModPayloads.handleSelectTarget(player, SelectTargetPayload.attached(menu.containerId)),
                                "não escolheu a presa");
                        helper.assertFalse(menu.cardsLocked(), "cartões travados depois de escolher a presa");
                        menu.setCarried(ItemStack.EMPTY);
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }
}
