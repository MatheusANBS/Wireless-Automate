package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions.LinkResult;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload.Op;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Vinculador em modo Área: marcar cantos com Shift + clique, contar e vincular os roteadores da
 * caixa nas abas marcadas, o limite de volume e de distância, rede de outro dono recusada e as
 * ações da tela. Também o modo desvincular ("Nenhuma") no clique e na área, as abas combinadas e o
 * componente antigo {@code linker_type}. Pedras em y=1 com o roteador em cima; os cantos pegam A e
 * B, e C fica de fora.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class LinkerAreaGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 0);
    private static final BlockPos C = new BlockPos(2, 1, 2);
    /** Cantos da área (relativos): de A até a frente de B, sem chegar a C. */
    private static final BlockPos CORNER1 = new BlockPos(0, 1, 0);
    private static final BlockPos CORNER2 = new BlockPos(2, 2, 1);
    private static final int CONTAINER_ID = 57;

    private static RouterBlockEntity router(GameTestHelper helper, BlockPos machine, UUID network) {
        helper.setBlock(machine, Blocks.STONE);
        BlockPos pos = machine.above();
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos);
        router.setNetworkId(network);
        return router;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, ItemStack linker) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, linker);
        return player;
    }

    private static ItemStack areaLinker(LinkerTabs tabs) {
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        LinkerItem.setMode(linker, LinkerMode.AREA);
        LinkerItem.setTabs(linker, tabs);
        return linker;
    }

    /** Clique direito (sem Shift) com o item da mão principal num bloco (posição absoluta). */
    private static boolean click(ServerPlayer player, BlockPos absolute) {
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        return player.getMainHandItem().getItem().useOn(context).consumesAction();
    }

    /** O jogador continua sem rede ativa e sem rede própria (o modo desvincular não cria rede). */
    private static void assertNoNetworkCreated(GameTestHelper helper, ServerPlayer player) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        helper.assertTrue(data.activeNetwork(player.getUUID()) == null, "desvincular deixou uma rede ativa");
        helper.assertTrue(data.networksOf(player.getUUID()).isEmpty(), "desvincular criou uma rede");
    }

    /** Shift + clique direito com o item da mão principal num bloco (posição absoluta). */
    private static boolean shiftClick(ServerPlayer player, BlockPos absolute) {
        player.setShiftKeyDown(true);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        boolean acted = player.getMainHandItem().getItem().useOn(context).consumesAction();
        player.setShiftKeyDown(false);
        return acted;
    }

    private static void markArea(GameTestHelper helper, ServerPlayer player) {
        shiftClick(player, helper.absolutePos(CORNER1));
        shiftClick(player, helper.absolutePos(CORNER2));
    }

    private static void cleanup(GameTestHelper helper, ServerPlayer player, UUID... networks) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        for (UUID network : networks) {
            data.remove(network);
        }
        player.containerMenu = player.inventoryMenu;
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /**
     * Dois Shift + cliques marcam os cantos; Vincular em Fluidos põe só a aba de fluidos de A e B na
     * rede ativa, sem mexer nos itens nem em C; de novo, conta os dois como já vinculados.
     */
    @GameTest(template = "empty")
    public static void marksCornersAndLinksAreaByType(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-area-antes").id();
        RouterBlockEntity a = router(helper, A, before);
        RouterBlockEntity b = router(helper, B, before);
        RouterBlockEntity c = router(helper, C, before);
        ItemStack linker = areaLinker(LinkerTabs.of(ResourceType.FLUID));
        ServerPlayer player = player(helper, linker);
        WaNetwork active = data.create(player.getUUID(), "teste-area-ativa");
        data.setActiveNetwork(player.getUUID(), active.id());
        try {
            helper.assertTrue(shiftClick(player, helper.absolutePos(CORNER1)), "canto 1 não agiu");
            LinkerArea area = LinkerItem.area(linker);
            helper.assertTrue(area != null && !area.complete(), "canto 1 não marcado: " + area);
            helper.assertValueEqual(LinkerActions.check(player, linker), LinkerProblem.INCOMPLETE, "só com o canto 1");
            helper.assertTrue(shiftClick(player, helper.absolutePos(CORNER2)), "canto 2 não agiu");
            area = LinkerItem.area(linker);
            helper.assertTrue(area != null && area.complete(), "canto 2 não marcado: " + area);
            helper.assertValueEqual(area.box().volume(), 3L * 2 * 2, "volume");
            helper.assertValueEqual(LinkerActions.check(player, linker), LinkerProblem.NONE, "área completa");

            LinkResult result = LinkerActions.link(player, linker);
            helper.assertTrue(result.ok(), "vincular recusou: " + result.problem());
            helper.assertValueEqual(result.linked(), 2, "vinculados");
            helper.assertValueEqual(result.already(), 0, "já estavam");
            for (RouterBlockEntity inside : new RouterBlockEntity[] {a, b}) {
                helper.assertValueEqual(inside.networkId(ResourceType.FLUID), active.id(), "fluidos de dentro");
                helper.assertValueEqual(inside.networkId(ResourceType.ITEM), before, "itens mudaram");
                helper.assertValueEqual(inside.networkId(ResourceType.ENERGY), before, "energia mudou");
            }
            helper.assertValueEqual(c.networkId(ResourceType.FLUID), before, "C fora da área mudou");

            LinkResult again = LinkerActions.link(player, linker);
            helper.assertValueEqual(again.linked(), 0, "vinculou de novo");
            helper.assertValueEqual(again.already(), 2, "já estavam na segunda vez");

            // um terceiro clique recomeça pelo canto 1
            shiftClick(player, helper.absolutePos(C));
            area = LinkerItem.area(linker);
            helper.assertTrue(area != null && !area.complete() && area.first().equals(helper.absolutePos(C)),
                    "terceiro clique não recomeçou: " + area);
        } finally {
            cleanup(helper, player, before, active.id());
        }
        helper.succeed();
    }

    /** Em Todos, todas as abas que existem (Químicos só com o Mekanism) entram na rede. */
    @GameTest(template = "empty")
    public static void linkAllTypes(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-area-todos-antes").id();
        RouterBlockEntity a = router(helper, A, before);
        ItemStack linker = areaLinker(LinkerTabs.ALL);
        ServerPlayer player = player(helper, linker);
        WaNetwork active = data.create(player.getUUID(), "teste-area-todos");
        data.setActiveNetwork(player.getUUID(), active.id());
        try {
            markArea(helper, player);
            LinkResult result = LinkerActions.link(player, linker);
            helper.assertTrue(result.ok() && result.linked() == 1, "vinculou " + result);
            for (ResourceType type : LinkerTabs.available(Chemicals.LOADED)) {
                helper.assertValueEqual(a.networkId(type), active.id(), "aba " + type);
            }
        } finally {
            cleanup(helper, player, before, active.id());
        }
        helper.succeed();
    }

    /** O canto 2 que passaria do volume máximo é recusado; uma área grande demais não vincula. */
    @GameTest(template = "empty")
    public static void volumeLimit(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-area-volume").id();
        RouterBlockEntity a = router(helper, A, before);
        ItemStack linker = areaLinker(LinkerTabs.ALL);
        ServerPlayer player = player(helper, linker);
        try {
            long max = LinkerActions.maxVolume();
            BlockPos first = helper.absolutePos(CORNER1);
            shiftClick(player, first);
            helper.assertTrue(shiftClick(player, first.offset((int) max, 0, 0)), "canto 2 grande não agiu");
            LinkerArea area = LinkerItem.area(linker);
            helper.assertTrue(area != null && !area.complete(), "aceitou canto 2 acima do limite: " + area);

            // no limite exato passa
            shiftClick(player, first.offset((int) max - 1, 0, 0));
            area = LinkerItem.area(linker);
            helper.assertTrue(area != null && area.complete() && area.box().volume() == max,
                    "recusou o volume máximo: " + area);

            // uma área acima do limite vinda de outro lugar (config reduzida, item antigo) é recusada
            LinkerItem.setArea(linker, area.withSecond(first.offset((int) max, 0, 0)));
            helper.assertValueEqual(LinkerActions.check(player, linker), LinkerProblem.TOO_BIG, "problema");
            LinkResult result = LinkerActions.link(player, linker);
            helper.assertValueEqual(result.problem(), LinkerProblem.TOO_BIG, "vinculou área grande");
            helper.assertValueEqual(a.networkId(ResourceType.ITEM), before, "roteador mudou");
        } finally {
            cleanup(helper, player, before);
        }
        helper.succeed();
    }

    /** Longe da área (acima do {@code maxDistance}) não vincula. */
    @GameTest(template = "empty")
    public static void tooFarIsRefused(GameTestHelper helper) {
        int distance = LinkerActions.maxDistance();
        if (distance == 0) {
            helper.succeed();
            return;
        }
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-area-longe").id();
        RouterBlockEntity a = router(helper, A, before);
        ItemStack linker = areaLinker(LinkerTabs.ALL);
        ServerPlayer player = player(helper, linker);
        try {
            markArea(helper, player);
            player.moveTo(helper.absoluteVec(new Vec3(1.5, 3 + distance + 20, 1.5)));
            helper.assertValueEqual(LinkerActions.check(player, linker), LinkerProblem.TOO_FAR, "problema");
            helper.assertValueEqual(LinkerActions.link(player, linker).problem(), LinkerProblem.TOO_FAR, "vinculou");
            helper.assertValueEqual(a.networkId(ResourceType.ITEM), before, "roteador mudou");
        } finally {
            cleanup(helper, player, before);
        }
        helper.succeed();
    }

    /**
     * Rede ativa de outro dono: vincular por área é recusado. Pela tela, escolher a rede alheia é
     * recusado; escolher a própria e Vincular funciona.
     */
    @GameTest(template = "empty")
    public static void foreignNetworkIsRefused(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-area-alheia-antes").id();
        WaNetwork foreign = data.create(UUID.randomUUID(), "teste-area-alheia");
        RouterBlockEntity a = router(helper, A, before);
        ItemStack linker = areaLinker(LinkerTabs.of(ResourceType.ITEM));
        ServerPlayer player = player(helper, linker);
        WaNetwork own = data.create(player.getUUID(), "teste-area-propria");
        data.setActiveNetwork(player.getUUID(), foreign.id());
        try {
            markArea(helper, player);
            helper.assertValueEqual(LinkerActions.check(player, linker), LinkerProblem.FOREIGN_NETWORK, "problema");
            helper.assertValueEqual(LinkerActions.link(player, linker).problem(), LinkerProblem.FOREIGN_NETWORK,
                    "vinculou à rede alheia");
            helper.assertValueEqual(a.networkId(ResourceType.ITEM), before, "roteador mudou");

            LinkerMenu menu = new LinkerMenu(CONTAINER_ID, player.getInventory(), InteractionHand.MAIN_HAND,
                    LinkerSnapshot.capture(player, linker, null));
            player.containerMenu = menu;
            helper.assertValueEqual(menu.snapshot().problem(), LinkerProblem.FOREIGN_NETWORK, "tela sem o aviso");
            helper.assertFalse(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.SET_ACTIVE,
                    Optional.of(foreign.id()), "", 0)), "aceitou escolher a rede alheia");
            helper.assertTrue(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.SET_ACTIVE,
                    Optional.of(own.id()), "", 0)), "recusou a própria rede");
            helper.assertValueEqual(data.activeNetwork(player.getUUID()), own.id(), "rede ativa");
            helper.assertFalse(LinkerActions.handle(player, LinkerActionPayload.of(CONTAINER_ID + 1, Op.LINK)),
                    "aceitou outro containerId");
            helper.assertTrue(LinkerActions.handle(player, LinkerActionPayload.of(CONTAINER_ID, Op.LINK)),
                    "Vincular pela tela recusou");
            helper.assertValueEqual(a.networkId(ResourceType.ITEM), own.id(), "itens de A");
            helper.assertValueEqual(a.networkId(ResourceType.FLUID), before, "fluidos de A mudaram");
            LinkerSnapshot snapshot = menu.poll();
            helper.assertTrue(snapshot != null && snapshot.outcome().isPresent()
                    && snapshot.outcome().get().linked() == 1, "a tela não mostra o resultado: " + snapshot);
            helper.assertValueEqual(snapshot.inside(), 1, "roteadores na área");
            helper.assertValueEqual(snapshot.already(), 1, "já na rede");
        } finally {
            cleanup(helper, player, before, foreign.id(), own.id());
        }
        helper.succeed();
    }

    /**
     * Ações da tela: abas, modo, criar rede (nome vazio recusado, repetido só ativa) e limpar os
     * cantos; o menu manda um estado novo depois de cada uma. Sem o Vinculador na mão, nada vale.
     */
    @GameTest(template = "empty")
    public static void menuActions(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-area-tela-antes").id();
        router(helper, A, before);
        router(helper, B, before);
        ItemStack linker = areaLinker(LinkerTabs.ALL);
        ServerPlayer player = player(helper, linker);
        UUID created = null;
        try {
            markArea(helper, player);
            LinkerMenu menu = new LinkerMenu(CONTAINER_ID, player.getInventory(), InteractionHand.MAIN_HAND,
                    LinkerSnapshot.capture(player, linker, null));
            player.containerMenu = menu;
            helper.assertValueEqual(menu.snapshot().inside(), 2, "roteadores na prévia");
            helper.assertValueEqual(menu.snapshot().routers().size(), 2, "pontos na prévia");
            helper.assertTrue(menu.poll() == null, "estado sem mudança foi reenviado");

            helper.assertTrue(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.TOGGLE_TAB,
                    Optional.empty(), "", ResourceType.ENERGY.ordinal())), "recusou desmarcar Energia");
            LinkerTabs expected = LinkerTabs.ALL.toggle(ResourceType.ENERGY);
            helper.assertValueEqual(LinkerItem.tabs(linker), expected, "abas");
            helper.assertValueEqual(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.TOGGLE_TAB,
                    Optional.empty(), "", ResourceType.CHEMICAL.ordinal())), Chemicals.LOADED,
                    "Químicos com o Mekanism " + Chemicals.LOADED);
            if (Chemicals.LOADED) {
                expected = expected.toggle(ResourceType.CHEMICAL);
            }
            helper.assertFalse(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.TOGGLE_TAB,
                    Optional.empty(), "", 99)), "aceitou aba inválida");
            helper.assertValueEqual(LinkerItem.tabs(linker), expected, "abas depois das recusas");
            LinkerSnapshot afterType = menu.poll();
            helper.assertTrue(afterType != null && afterType.tabs().equals(expected),
                    "a tela não recebeu as abas");

            // desmarcar até sobrar uma: a última é recusada
            for (ResourceType type : new ResourceType[] {ResourceType.ITEM, ResourceType.CHEMICAL}) {
                if (LinkerItem.tabs(linker).contains(type)
                        && (type != ResourceType.CHEMICAL || Chemicals.LOADED)) {
                    helper.assertTrue(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID,
                            Op.TOGGLE_TAB, Optional.empty(), "", type.ordinal())), "recusou desmarcar " + type);
                }
            }
            helper.assertValueEqual(LinkerItem.effectiveTabs(linker), List.of(ResourceType.FLUID), "sobrou");
            helper.assertFalse(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.TOGGLE_TAB,
                    Optional.empty(), "", ResourceType.FLUID.ordinal())), "aceitou desmarcar a última aba");

            helper.assertFalse(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.CREATE_NETWORK,
                    Optional.empty(), "   ", 0)), "aceitou nome vazio");
            helper.assertTrue(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.CREATE_NETWORK,
                    Optional.empty(), " teste-area-nova ", 0)), "recusou criar");
            WaNetwork network = data.byName(player.getUUID(), "teste-area-nova");
            helper.assertTrue(network != null, "rede não criada");
            created = network.id();
            helper.assertValueEqual(data.activeNetwork(player.getUUID()), created, "a nova não ficou ativa");
            int count = data.networksOf(player.getUUID()).size();
            LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.CREATE_NETWORK,
                    Optional.empty(), "TESTE-AREA-NOVA", 0));
            helper.assertValueEqual(data.networksOf(player.getUUID()).size(), count, "nome repetido criou outra");
            LinkerSnapshot afterCreate = menu.poll();
            helper.assertTrue(afterCreate != null && afterCreate.active().equals(Optional.of(created))
                    && afterCreate.networks().stream().anyMatch(e -> e.id().equals(network.id())),
                    "a tela não recebeu a rede nova");

            helper.assertTrue(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.SET_MODE,
                    Optional.empty(), "", LinkerMode.SINGLE.ordinal())), "recusou o modo");
            helper.assertValueEqual(LinkerItem.mode(linker), LinkerMode.SINGLE, "modo");
            helper.assertTrue(LinkerActions.handle(player, LinkerActionPayload.of(CONTAINER_ID, Op.CLEAR_AREA)),
                    "recusou limpar");
            helper.assertTrue(LinkerItem.area(linker) == null, "cantos ficaram");
            LinkerSnapshot afterClear = menu.poll();
            helper.assertTrue(afterClear != null && afterClear.first().isEmpty()
                    && afterClear.problem() == LinkerProblem.NO_AREA, "a tela não recebeu a limpeza");

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertFalse(menu.stillValid(player), "tela válida sem o Vinculador");
            helper.assertFalse(LinkerActions.handle(player, LinkerActionPayload.of(CONTAINER_ID, Op.CLEAR_AREA)),
                    "agiu sem o Vinculador na mão");
        } finally {
            if (created != null) {
                cleanup(helper, player, before, created);
            } else {
                cleanup(helper, player, before);
            }
        }
        helper.succeed();
    }

    /**
     * Shift + clique no ar alterna Único e Área. Em Único, Shift + clique num bloco que não é
     * roteador não marca canto (nem alterna o modo); num roteador, vincula como antes.
     */
    @GameTest(template = "empty")
    public static void modeToggleAndSingleClicks(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-area-modo").id();
        RouterBlockEntity a = router(helper, A, before);
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        ServerPlayer player = player(helper, linker);
        WaNetwork active = data.create(player.getUUID(), "teste-area-modo-ativa");
        data.setActiveNetwork(player.getUUID(), active.id());
        try {
            helper.assertValueEqual(LinkerItem.mode(linker), LinkerMode.SINGLE, "modo inicial");
            player.setShiftKeyDown(true);
            linker.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(LinkerItem.mode(linker), LinkerMode.AREA, "Shift + clique no ar");
            linker.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(LinkerItem.mode(linker), LinkerMode.SINGLE, "de volta a Único");
            player.setShiftKeyDown(false);

            helper.assertTrue(shiftClick(player, helper.absolutePos(A)), "Shift + clique num bloco em Único");
            helper.assertTrue(LinkerItem.area(linker) == null, "Único marcou canto");
            helper.assertValueEqual(LinkerItem.mode(linker), LinkerMode.SINGLE, "Único alternou o modo");
            helper.assertTrue(shiftClick(player, a.getBlockPos()), "Shift + clique no roteador");
            helper.assertValueEqual(a.networkId(ResourceType.ITEM), active.id(), "Único não vinculou");
        } finally {
            cleanup(helper, player, before, active.id());
        }
        helper.succeed();
    }

    /**
     * Modo desvincular no clique: tira só as abas marcadas (Itens + Fluidos) da rede, sem mexer nas
     * outras; um segundo clique não muda nada. O jogador sem rede ativa continua sem rede.
     */
    @GameTest(template = "empty")
    public static void unlinkSingleClearsOnlySelectedTabs(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-desvincular-unico").id();
        RouterBlockEntity a = router(helper, A, before);
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        LinkerItem.setTabs(linker, LinkerTabs.of(ResourceType.ITEM, ResourceType.FLUID));
        LinkerItem.setUnlink(linker, true);
        ServerPlayer player = player(helper, linker);
        try {
            helper.assertTrue(click(player, a.getBlockPos()), "clique no roteador não agiu");
            helper.assertTrue(a.networkId(ResourceType.ITEM) == null, "itens ficaram na rede");
            helper.assertTrue(a.networkId(ResourceType.FLUID) == null, "fluidos ficaram na rede");
            helper.assertValueEqual(a.networkId(ResourceType.ENERGY), before, "energia mudou");
            helper.assertValueEqual(a.networkId(ResourceType.CHEMICAL), before, "químicos mudaram");
            assertNoNetworkCreated(helper, player);

            helper.assertTrue(click(player, a.getBlockPos()), "segundo clique não agiu");
            helper.assertValueEqual(a.networkId(ResourceType.ENERGY), before, "energia mudou no segundo clique");
            assertNoNetworkCreated(helper, player);
        } finally {
            cleanup(helper, player, before);
        }
        helper.succeed();
    }

    /**
     * Modo desvincular na área: Itens + Fluidos + Químicos saem da rede em A e B (Químicos só com o
     * Mekanism), a Energia fica; C, fora da área, não muda. De novo, os dois contam como já sem rede.
     */
    @GameTest(template = "empty")
    public static void unlinkAreaClearsSelectedTabs(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-desvincular-area").id();
        RouterBlockEntity a = router(helper, A, before);
        RouterBlockEntity b = router(helper, B, before);
        RouterBlockEntity c = router(helper, C, before);
        ItemStack linker = areaLinker(LinkerTabs.of(ResourceType.ITEM, ResourceType.FLUID, ResourceType.CHEMICAL));
        LinkerItem.setUnlink(linker, true);
        ServerPlayer player = player(helper, linker);
        try {
            markArea(helper, player);
            helper.assertValueEqual(LinkerActions.check(player, linker), LinkerProblem.NONE, "problema");
            LinkResult result = LinkerActions.link(player, linker);
            helper.assertTrue(result.ok() && result.unlink(), "desvincular recusou: " + result);
            helper.assertValueEqual(result.linked(), 2, "desvinculados");
            helper.assertTrue(result.network() == null, "desvincular usou uma rede");
            for (RouterBlockEntity inside : new RouterBlockEntity[] {a, b}) {
                helper.assertTrue(inside.networkId(ResourceType.ITEM) == null, "itens de dentro");
                helper.assertTrue(inside.networkId(ResourceType.FLUID) == null, "fluidos de dentro");
                helper.assertValueEqual(inside.networkId(ResourceType.ENERGY), before, "energia de dentro mudou");
                if (Chemicals.LOADED) {
                    helper.assertTrue(inside.networkId(ResourceType.CHEMICAL) == null, "químicos de dentro");
                } else {
                    helper.assertValueEqual(inside.networkId(ResourceType.CHEMICAL), before,
                            "químicos mudaram sem o Mekanism");
                }
            }
            for (ResourceType type : ResourceType.values()) {
                helper.assertValueEqual(c.networkId(type), before, "C fora da área mudou: " + type);
            }
            assertNoNetworkCreated(helper, player);

            LinkResult again = LinkerActions.link(player, linker);
            helper.assertValueEqual(again.linked(), 0, "desvinculou de novo");
            helper.assertValueEqual(again.already(), 2, "já sem rede na segunda vez");
            assertNoNetworkCreated(helper, player);
        } finally {
            cleanup(helper, player, before);
        }
        helper.succeed();
    }

    /**
     * Abas combinadas: Itens + Químicos põe os itens na rede ativa e os químicos só com o Mekanism
     * (sem ele, a aba é ignorada, sem erro); Fluidos e Energia ficam. Vale no clique e na área.
     */
    @GameTest(template = "empty")
    public static void linkMultipleTabs(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-abas-antes").id();
        RouterBlockEntity a = router(helper, A, before);
        RouterBlockEntity b = router(helper, B, before);
        ItemStack linker = areaLinker(LinkerTabs.of(ResourceType.ITEM, ResourceType.CHEMICAL));
        ServerPlayer player = player(helper, linker);
        WaNetwork active = data.create(player.getUUID(), "teste-abas-ativa");
        data.setActiveNetwork(player.getUUID(), active.id());
        try {
            // Único: clique no roteador A
            LinkerItem.setMode(linker, LinkerMode.SINGLE);
            helper.assertTrue(click(player, a.getBlockPos()), "clique não agiu");
            // Área: A e B
            LinkerItem.setMode(linker, LinkerMode.AREA);
            markArea(helper, player);
            LinkResult result = LinkerActions.link(player, linker);
            helper.assertTrue(result.ok(), "vincular recusou: " + result.problem());
            helper.assertValueEqual(result.linked(), 1, "vinculados (B)");
            helper.assertValueEqual(result.already(), 1, "já estavam (A)");
            for (RouterBlockEntity router : new RouterBlockEntity[] {a, b}) {
                helper.assertValueEqual(router.networkId(ResourceType.ITEM), active.id(), "itens");
                helper.assertValueEqual(router.networkId(ResourceType.FLUID), before, "fluidos mudaram");
                helper.assertValueEqual(router.networkId(ResourceType.ENERGY), before, "energia mudou");
                helper.assertValueEqual(router.networkId(ResourceType.CHEMICAL),
                        Chemicals.LOADED ? active.id() : before, "químicos");
            }
        } finally {
            cleanup(helper, player, before, active.id());
        }
        helper.succeed();
    }

    /** Um Vinculador com o componente antigo {@code linker_type} vincula a área só naquela aba. */
    @GameTest(template = "empty")
    public static void legacyTypeComponentIsHonored(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-tipo-antigo").id();
        RouterBlockEntity a = router(helper, A, before);
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        LinkerItem.setMode(linker, LinkerMode.AREA);
        linker.set(ModDataComponents.LINKER_TYPE.get(), ResourceType.FLUID);
        ServerPlayer player = player(helper, linker);
        WaNetwork active = data.create(player.getUUID(), "teste-tipo-antigo-ativa");
        data.setActiveNetwork(player.getUUID(), active.id());
        try {
            helper.assertValueEqual(LinkerItem.tabs(linker), LinkerTabs.of(ResourceType.FLUID), "abas do tipo antigo");
            markArea(helper, player);
            LinkResult result = LinkerActions.link(player, linker);
            helper.assertTrue(result.ok() && result.linked() == 1, "vinculou " + result);
            helper.assertValueEqual(a.networkId(ResourceType.FLUID), active.id(), "fluidos");
            helper.assertValueEqual(a.networkId(ResourceType.ITEM), before, "itens mudaram");
            helper.assertValueEqual(a.networkId(ResourceType.ENERGY), before, "energia mudou");
        } finally {
            cleanup(helper, player, before, active.id());
        }
        helper.succeed();
    }

    /**
     * Regras ao desvincular: a área precisa valer (longe demais é recusado e nada muda); sem aba que
     * valha (só Químicos, sem o Mekanism) nada muda; a rede ativa de outro dono não importa, porque
     * desvincular não usa rede, e ela fica como estava.
     */
    @GameTest(template = "empty")
    public static void unlinkFollowsTheRules(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-desvincular-regras").id();
        WaNetwork foreign = data.create(UUID.randomUUID(), "teste-desvincular-alheia");
        RouterBlockEntity a = router(helper, A, before);
        ItemStack linker = areaLinker(LinkerTabs.of(ResourceType.ITEM));
        LinkerItem.setUnlink(linker, true);
        ServerPlayer player = player(helper, linker);
        data.setActiveNetwork(player.getUUID(), foreign.id());
        try {
            markArea(helper, player);
            int distance = LinkerActions.maxDistance();
            if (distance > 0) {
                player.moveTo(helper.absoluteVec(new Vec3(1.5, 3 + distance + 20, 1.5)));
                helper.assertValueEqual(LinkerActions.link(player, linker).problem(), LinkerProblem.TOO_FAR,
                        "desvinculou de longe");
                helper.assertValueEqual(a.networkId(ResourceType.ITEM), before, "roteador mudou de longe");
                player.moveTo(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
            }

            if (!Chemicals.LOADED) {
                LinkerItem.setTabs(linker, LinkerTabs.of(ResourceType.CHEMICAL));
                helper.assertValueEqual(LinkerActions.check(player, linker), LinkerProblem.NO_TABS, "sem abas");
                helper.assertFalse(LinkerActions.single(player, linker, a), "clique sem abas mudou o roteador");
                for (ResourceType type : ResourceType.values()) {
                    helper.assertValueEqual(a.networkId(type), before, "sem abas mudou " + type);
                }
                LinkerItem.setTabs(linker, LinkerTabs.of(ResourceType.ITEM));
            }

            helper.assertValueEqual(LinkerActions.check(player, linker), LinkerProblem.NONE,
                    "rede alheia barrou o desvincular");
            LinkResult result = LinkerActions.link(player, linker);
            helper.assertTrue(result.ok() && result.linked() == 1, "desvinculou " + result);
            helper.assertTrue(a.networkId(ResourceType.ITEM) == null, "itens ficaram");
            helper.assertValueEqual(data.activeNetwork(player.getUUID()), foreign.id(), "a rede ativa mudou");
            helper.assertTrue(data.networksOf(player.getUUID()).isEmpty(), "desvincular criou uma rede");

            // de volta a vincular, a rede alheia volta a barrar
            LinkerItem.setUnlink(linker, false);
            helper.assertValueEqual(LinkerActions.check(player, linker), LinkerProblem.FOREIGN_NETWORK,
                    "vincular à rede alheia");
        } finally {
            cleanup(helper, player, before, foreign.id());
        }
        helper.succeed();
    }

    /**
     * Pela tela: "Nenhuma (desvincular)" liga o modo, Desvincular tira da rede e a tela mostra o
     * resultado; escolher uma rede (ou criar uma) sai do modo. Valor inválido é recusado.
     */
    @GameTest(template = "empty")
    public static void menuUnlinkActions(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-desvincular-tela").id();
        RouterBlockEntity a = router(helper, A, before);
        ItemStack linker = areaLinker(LinkerTabs.of(ResourceType.ENERGY));
        ServerPlayer player = player(helper, linker);
        WaNetwork own = data.create(player.getUUID(), "teste-desvincular-tela-propria");
        try {
            markArea(helper, player);
            LinkerMenu menu = new LinkerMenu(CONTAINER_ID, player.getInventory(), InteractionHand.MAIN_HAND,
                    LinkerSnapshot.capture(player, linker, null));
            player.containerMenu = menu;
            helper.assertFalse(menu.snapshot().unlink(), "começou desvinculando");
            helper.assertFalse(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.SET_UNLINK,
                    Optional.empty(), "", 2)), "aceitou valor inválido");
            helper.assertTrue(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.SET_UNLINK,
                    Optional.empty(), "", 1)), "recusou desvincular");
            helper.assertTrue(LinkerItem.unlink(linker), "o item não ficou desvinculando");
            LinkerSnapshot afterUnlink = menu.poll();
            helper.assertTrue(afterUnlink != null && afterUnlink.unlink(), "a tela não recebeu o desvincular");

            helper.assertTrue(LinkerActions.handle(player, LinkerActionPayload.of(CONTAINER_ID, Op.LINK)),
                    "Desvincular pela tela recusou");
            helper.assertTrue(a.networkId(ResourceType.ENERGY) == null, "energia ficou");
            helper.assertValueEqual(a.networkId(ResourceType.ITEM), before, "itens mudaram");
            LinkerSnapshot afterLink = menu.poll();
            helper.assertTrue(afterLink != null && afterLink.outcome().isPresent()
                    && afterLink.outcome().get().unlink() && afterLink.outcome().get().linked() == 1,
                    "a tela não mostra o resultado: " + afterLink);
            helper.assertTrue(data.activeNetwork(player.getUUID()) == null, "desvincular deixou uma rede ativa");

            helper.assertTrue(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.SET_ACTIVE,
                    Optional.of(own.id()), "", 0)), "recusou a própria rede");
            helper.assertFalse(LinkerItem.unlink(linker), "escolher uma rede não saiu do desvincular");
        } finally {
            cleanup(helper, player, before, own.id());
        }
        helper.succeed();
    }
}
