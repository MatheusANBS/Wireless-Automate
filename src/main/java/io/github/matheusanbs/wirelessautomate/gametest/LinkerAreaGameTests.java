package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload.Op;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A área e o modo do Vinculador: marcar cantos com Shift + clique, o limite de volume e de distância, o
 * Shift + clique no ar que troca de modo, as ações da tela e os itens antigos, com os componentes de quando o
 * Vinculador punha faces em redes.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class LinkerAreaGameTests {
    private static final BlockPos CORNER1 = new BlockPos(0, 1, 0);
    private static final BlockPos CORNER2 = new BlockPos(2, 2, 1);
    private static final int CONTAINER_ID = 57;

    private static ServerPlayer player(GameTestHelper helper, ItemStack linker) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 3, 1.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, linker);
        return player;
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

    private static void cleanup(ServerPlayer player, GameTestHelper helper) {
        player.containerMenu = player.inventoryMenu;
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /** Dois Shift + cliques marcam os cantos; um terceiro recomeça pelo canto 1; limpar apaga a área. */
    @GameTest(template = "empty")
    public static void marksCornersWithShiftClick(GameTestHelper helper) {
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        ServerPlayer player = player(helper, linker);
        try {
            helper.assertTrue(shiftClick(player, helper.absolutePos(CORNER1)), "canto 1 não agiu");
            LinkerArea area = LinkerItem.area(linker);
            helper.assertTrue(area != null && !area.complete(), "só o canto 1 devia estar marcado: " + area);
            helper.assertValueEqual(LinkerActions.checkArea(player, area), LinkerProblem.INCOMPLETE, "problema");

            helper.assertTrue(shiftClick(player, helper.absolutePos(CORNER2)), "canto 2 não agiu");
            area = LinkerItem.area(linker);
            helper.assertTrue(area != null && area.complete(), "área incompleta: " + area);
            helper.assertValueEqual(LinkerActions.checkArea(player, area), LinkerProblem.NONE, "área completa");

            shiftClick(player, helper.absolutePos(CORNER1));
            area = LinkerItem.area(linker);
            helper.assertTrue(area != null && !area.complete(), "o terceiro clique não recomeçou: " + area);
            helper.assertValueEqual(LinkerActions.checkArea(player, null), LinkerProblem.NO_AREA, "sem área");
        } finally {
            cleanup(player, helper);
        }
        helper.succeed();
    }

    /** O canto 2 que passaria do volume máximo é recusado; uma área acima do limite vinda de fora não vale. */
    @GameTest(template = "empty")
    public static void volumeLimit(GameTestHelper helper) {
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
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
            helper.assertValueEqual(LinkerActions.checkArea(player, LinkerItem.area(linker)), LinkerProblem.TOO_BIG,
                    "problema");
        } finally {
            cleanup(player, helper);
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
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        ServerPlayer player = player(helper, linker);
        try {
            shiftClick(player, helper.absolutePos(CORNER1));
            shiftClick(player, helper.absolutePos(CORNER2));
            player.moveTo(helper.absoluteVec(new Vec3(1.5, 3 + distance + 20, 1.5)));
            helper.assertValueEqual(LinkerActions.checkArea(player, LinkerItem.area(linker)), LinkerProblem.TOO_FAR,
                    "problema");
        } finally {
            cleanup(player, helper);
        }
        helper.succeed();
    }

    /** Shift + clique no ar passa de Vincular a Abrir e volta; o padrão, sem componente, é Vincular. */
    @GameTest(template = "empty")
    public static void shiftClickInTheAirTogglesTheMode(GameTestHelper helper) {
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        ServerPlayer player = player(helper, linker);
        try {
            helper.assertValueEqual(LinkerItem.mode(linker), LinkerMode.LINK, "o padrão");
            player.setShiftKeyDown(true);
            ModItems.LINKER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(LinkerItem.mode(linker), LinkerMode.OPEN, "depois do primeiro Shift");
            ModItems.LINKER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(LinkerItem.mode(linker), LinkerMode.LINK, "depois do segundo Shift");
            helper.assertFalse(linker.has(io.github.matheusanbs.wirelessautomate.registry.ModDataComponents.LINKER_MODE
                    .get()), "Vincular deixou o componente no item");
        } finally {
            player.setShiftKeyDown(false);
            cleanup(player, helper);
        }
        helper.succeed();
    }

    /**
     * As ações da tela: o desvincular liga e desliga e recusa valor fora de 0 e 1, Limpar apaga a área, e uma ação
     * de outro {@code containerId} é recusada.
     */
    @GameTest(template = "empty")
    public static void menuActions(GameTestHelper helper) {
        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        ServerPlayer player = player(helper, linker);
        try {
            LinkerItem.setArea(linker, LinkerArea.firstCorner(helper.getLevel().dimension(),
                    helper.absolutePos(CORNER1)).withSecond(helper.absolutePos(CORNER2)));
            player.containerMenu = new LinkerMenu(CONTAINER_ID, player.getInventory(), InteractionHand.MAIN_HAND,
                    LinkerSnapshot.capture(player, linker, null));

            helper.assertTrue(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.SET_UNLINK, 1)),
                    "SET_UNLINK 1 recusou");
            helper.assertTrue(LinkerItem.unlink(linker), "o desvincular não ligou");
            helper.assertFalse(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.SET_UNLINK, 2)),
                    "SET_UNLINK 2 aceito");
            helper.assertTrue(LinkerItem.unlink(linker), "um valor inválido mexeu no item");
            helper.assertTrue(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.SET_UNLINK, 0)),
                    "SET_UNLINK 0 recusou");
            helper.assertFalse(LinkerItem.unlink(linker), "o desvincular não desligou");

            helper.assertFalse(LinkerActions.handle(player, LinkerActionPayload.of(CONTAINER_ID + 1, Op.CLEAR_AREA)),
                    "aceitou outro containerId");
            helper.assertTrue(LinkerItem.area(linker) != null, "outro containerId limpou a área");
            helper.assertTrue(LinkerActions.handle(player, LinkerActionPayload.of(CONTAINER_ID, Op.CLEAR_AREA)),
                    "CLEAR_AREA recusou");
            helper.assertTrue(LinkerItem.area(linker) == null, "a área não foi limpa");

            // o Vinculador saiu da mão: a tela não vale mais
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertFalse(LinkerActions.handle(player, new LinkerActionPayload(CONTAINER_ID, Op.SET_UNLINK, 1)),
                    "agiu sem o Vinculador na mão");
        } finally {
            cleanup(player, helper);
        }
        helper.succeed();
    }

    /**
     * Um Vinculador de antes, com {@code linker_mode} em "area" e os componentes das abas e das faces, ainda lê: o
     * item não some e o modo antigo vale como Vincular.
     */
    @GameTest(template = "empty")
    public static void oldComponentsStillLoad(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag components = new CompoundTag();
        components.putString("wirelessautomate:linker_mode", "area");
        ListTag tabs = new ListTag();
        tabs.add(StringTag.valueOf("item"));
        components.put("wirelessautomate:linker_tabs", tabs);
        components.putInt("wirelessautomate:linker_faces", 5);
        components.putString("wirelessautomate:linker_type", "fluid");
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "wirelessautomate:linker");
        tag.putInt("count", 1);
        tag.put("components", components);
        ItemStack stack = ItemStack.parse(registries, tag).orElse(ItemStack.EMPTY);
        helper.assertFalse(stack.isEmpty(), "o item antigo não leu");
        helper.assertValueEqual(LinkerItem.mode(stack), LinkerMode.LINK, "modo antigo");

        CompoundTag oldConfigurator = new CompoundTag();
        CompoundTag oldComponents = new CompoundTag();
        oldComponents.putString("wirelessautomate:configurator_mode", "area");
        oldComponents.putBoolean("wirelessautomate:configurator_any_machine", true);
        oldConfigurator.putString("id", "wirelessautomate:configurator");
        oldConfigurator.putInt("count", 1);
        oldConfigurator.put("components", oldComponents);
        helper.assertFalse(ItemStack.parse(registries, oldConfigurator).orElse(ItemStack.EMPTY).isEmpty(),
                "o Configurador antigo não leu");
        helper.succeed();
    }
}
