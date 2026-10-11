package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Roteador de alcance: o slot do Upgrade de Alcance na tela (aceita num tier com raio, recusa no
 * Básico), o NBT ({@code range_upgrade}) e o drop ao quebrar; o modelo das máquinas remotas (grupos
 * por bloco, perfil, exceções, cartões do grupo, {@code machines}/{@code groups} no NBT).
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
     * remontagem: a remota a 4 blocos para de mover. O raio só é lido na remontagem, então a config
     * fica baixa por alguns ticks e volta no {@code finally} do passo que confere. Num lote próprio: a
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
                    radius.set(2);
                    NetworkManager.configChanged();
                    container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
                })
                .thenExecuteAfter(20, () -> {
                    try {
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
     * Revisão M4: a carga do chunk (o método que o {@code ChunkEvent.Load} chama) acorda as portas
     * das remotas do chunk, inclusive uma que nunca teve cache de capability.
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
}
