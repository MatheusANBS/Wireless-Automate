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
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload.Op;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
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
 * caixa no tipo escolhido, o limite de volume e de distância, rede de outro dono recusada e as
 * ações da tela. Pedras em y=1 com o roteador em cima; os cantos pegam A e B, e C fica de fora.
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

    private static ItemStack areaLinker(@org.jetbrains.annotations.Nullable ResourceType type) {
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        LinkerItem.setMode(linker, LinkerMode.AREA);
        LinkerItem.setType(linker, type);
        return linker;
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
        ItemStack linker = areaLinker(ResourceType.FLUID);
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

    /** Em Todos, todas as abas (inclusive químicos) entram na rede. */
    @GameTest(template = "empty")
    public static void linkAllTypes(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-area-todos-antes").id();
        RouterBlockEntity a = router(helper, A, before);
        ItemStack linker = areaLinker(null);
        ServerPlayer player = player(helper, linker);
        WaNetwork active = data.create(player.getUUID(), "teste-area-todos");
        data.setActiveNetwork(player.getUUID(), active.id());
        try {
            markArea(helper, player);
            LinkResult result = LinkerActions.link(player, linker);
            helper.assertTrue(result.ok() && result.linked() == 1, "vinculou " + result);
            for (ResourceType type : ResourceType.values()) {
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
        ItemStack linker = areaLinker(null);
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
        ItemStack linker = areaLinker(null);
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
        ItemStack linker = areaLinker(ResourceType.ITEM);
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
     * Ações da tela: tipo, modo, criar rede (nome vazio recusado, repetido só ativa) e limpar os
     * cantos; o menu manda um estado novo depois de cada uma. Sem o Vinculador na mão, nada vale.
     */
    @GameTest(template = "empty")
    public static void menuActions(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID before = data.create(UUID.randomUUID(), "teste-area-tela-antes").id();
        router(helper, A, before);
        router(helper, B, before);
        ItemStack linker = areaLinker(null);
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

            helper.assertTrue(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.SET_TYPE,
                    Optional.empty(), "", ResourceType.ENERGY.ordinal())), "recusou Energia");
            helper.assertValueEqual(LinkerItem.type(linker), ResourceType.ENERGY, "tipo");
            helper.assertFalse(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.SET_TYPE,
                    Optional.empty(), "", ResourceType.CHEMICAL.ordinal())), "aceitou químicos");
            helper.assertFalse(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.SET_TYPE,
                    Optional.empty(), "", 99)), "aceitou tipo inválido");
            LinkerSnapshot afterType = menu.poll();
            helper.assertTrue(afterType != null && afterType.type().equals(Optional.of(ResourceType.ENERGY)),
                    "a tela não recebeu o tipo");

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
}
