package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRangeSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.router.RangeRowButtons;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;

/**
 * Desvincular o grupo inteiro e o Atualizar que devolve cartões, no e2e do roteador de alcance, depois do grupo "×0"
 * ({@link E2eRangeAttachedSteps#emptyGroupSteps}: o grupo dos baús sem máquina e o do barril com a presa e um barril
 * vinculado). O ✕ do "×0" arma "Apagar?" ({@code range-q5-apagar-armado}) e apaga o grupo. No grupo do barril, com um
 * Cartão de Filtro, o ✕ arma "Desvincular 1?" ({@code range-q6-desvincular-armado}, também no escuro) e desvincula o
 * barril: a presa fica, o cartão vai para o inventário e o aviso diz os dois ({@code range-q7-desvinculado-aviso});
 * zerado, o grupo fica sem o ✕. Depois, um baú vinculado com um cartão no grupo dele vira barril e o ⟳ o leva ao grupo
 * do barril: o grupo dos baús esvazia, o cartão volta e o aviso conta ({@code range-q9-atualizar-cartoes}).
 */
final class E2eRangeGroupSteps {
    private E2eRangeGroupSteps() {
    }

    static void groupSteps(List<Step> list, String label) {
        String l = label + "desvincular grupo: ";
        UUID playerId = Minecraft.getInstance().player.getUUID();
        list.add(open(l + "abrir", () -> rangeRouter));
        list.add(new Step(l + "no mínimo, com a lista", STEP_TIMEOUT_MS, () -> {
            routerScreen().previewResize(300, 240);
            if (!routerScreen().rangeListShown()) {
                routerScreen().previewToggleList();
            }
        }, () -> routerScreen().rangeListShown() && stepTicks >= 4, () -> "lista " + routerScreen().rangeListShown()));

        // o ×0: "Apagar?"
        list.add(new Step(l + "escolher o grupo ×0", STEP_TIMEOUT_MS,
                () -> clickRow(EditTarget.group(rangeGroup(Blocks.CHEST))),
                () -> rangeTargetIs(EditTarget.group(rangeGroup(Blocks.CHEST)))
                        && routerScreen().rangeButtonCenter(RangeRowButtons.Action.UNLINK) != null,
                () -> "alvo " + routerScreen().getMenu().snapshot().target()));
        list.add(armStep(l + "o ✕ do ×0 arma"));
        list.add(armedCapture(l + "Apagar? armado", "range-q5-apagar-armado"));
        list.add(new Step(l + "o segundo clique apaga o grupo", STEP_TIMEOUT_MS,
                () -> clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.UNLINK), "Apagar?"),
                () -> !hasGroup(Blocks.CHEST) && E2eRangeCopySteps.noticeHas("group_removed")
                        && onServer(server -> router(server, rangeRouter).remoteMachines().groups().size() == 1),
                () -> "grupos " + rangeView().groups() + ", aviso " + PanelNotices.current()));

        // o grupo da presa: as remotas saem, a presa fica e o cartão vai para o inventário
        list.add(new Step(l + "um cartão no grupo do barril, e escolher o grupo", STEP_TIMEOUT_MS, () -> {
            int barrels = rangeGroup(Blocks.BARREL);
            onServer(server -> {
                router(server, rangeRouter).setCard(EditTarget.group(barrels), ResourceType.ITEM, Direction.NORTH, 0,
                        itemCard());
                return null;
            });
            clickRow(EditTarget.group(barrels));
        }, () -> rangeTargetIs(EditTarget.group(rangeGroup(Blocks.BARREL)))
                && routerScreen().rangeButtonCenter(RangeRowButtons.Action.UNLINK) != null,
                () -> "alvo " + routerScreen().getMenu().snapshot().target()));
        list.add(armStep(l + "o ✕ do grupo da presa arma"));
        list.add(armedCapture(l + "Desvincular 1? armado", "range-q6-desvincular-armado"));
        E2eVariants.escuro(list, () -> {
            list.add(disarmStep(l + "sair com o mouse desarma"));
            list.add(E2eDarkSteps.themeClick(l + "tema escuro", GuiTheme.DARK));
            list.add(armStep(l + "escuro: o ✕ arma de novo"));
            list.add(armedCapture(l + "escuro: armado", "range-q6-desvincular-armado-escuro"));
            list.add(disarmStep(l + "escuro: sair com o mouse desarma"));
            list.add(E2eDarkSteps.themeClick(l + "de volta ao tema claro", GuiTheme.LIGHT));
            list.add(armStep(l + "claro: o ✕ arma de novo"));
        });
        int[] cardsBefore = new int[1];
        list.add(new Step(l + "o segundo clique desvincula o barril; a presa fica", STEP_TIMEOUT_MS, () -> {
            cardsBefore[0] = onServer(server -> cards(server, playerId));
            clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.UNLINK), "Desvincular 1?");
        }, () -> rangeView().machines().isEmpty() && rangeView().attachedGroup() == rangeGroup(Blocks.BARREL)
                && routerScreen().getMenu().snapshot().target().attached()
                && E2eRangeCopySteps.noticeHas("unlinked.one") && E2eRangeCopySteps.noticeHas("cards_returned.one")
                && onServer(server -> cards(server, playerId)) == cardsBefore[0] + 1,
                () -> "alcance " + rangeView() + ", aviso " + PanelNotices.current()));
        list.add(rangeClipCheck(l + "aviso"));
        list.add(capture("range-q7-desvinculado-aviso"));
        list.add(new Step(l + "zerado, o grupo da presa fica sem o ✕", STEP_TIMEOUT_MS,
                () -> clickRow(EditTarget.group(rangeGroup(Blocks.BARREL))), () -> {
                    RangeView.GroupView barrels = rangeView().group(rangeGroup(Blocks.BARREL));
                    return rangeTargetIs(EditTarget.group(rangeGroup(Blocks.BARREL))) && barrels != null
                            && barrels.blank() && stepTicks >= 4
                            && routerScreen().rangeButtonCenter(RangeRowButtons.Action.UNLINK) == null;
                }, () -> "grupos " + rangeView().groups()));
        updateSteps(list, label + "Atualizar: ", playerId);
        list.add(new Step(l + "sem os cartões no inventário", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            player.getInventory().clearOrCountMatchingItems(stack -> stack.is(ModItems.FILTER_CARD.get()), -1,
                    player.inventoryMenu.getCraftSlots());
            return null;
        }), () -> onServer(server -> cards(server, playerId)) == 0, () -> "cartões"));
        list.add(close(l + "fechar"));
    }

    /**
     * O Atualizar que devolve cartões: o primeiro baú, vinculado de novo (num grupo dos baús, com um cartão), vira
     * barril; o ⟳ o leva ao grupo do barril, o grupo dos baús esvazia e o cartão vai para o inventário.
     */
    private static void updateSteps(List<Step> list, String l, UUID playerId) {
        int[] cardsBefore = new int[1];
        list.add(new Step(l + "um baú com cartão vira barril", STEP_TIMEOUT_MS, () -> onServer(server -> {
            RouterBlockEntity router = router(server, rangeRouter);
            router.linkMachine(rangeChestA);
            var machines = router.remoteMachines();
            int index = machines.index(rangeChestA);
            int chests = machines.groupOf(machines.machine(index)).id();
            router.setCard(EditTarget.group(chests), ResourceType.ITEM, Direction.NORTH, 0, itemCard());
            server.overworld().setBlockAndUpdate(rangeChestA, Blocks.BARREL.defaultBlockState());
            cardsBefore[0] = cards(server, playerId);
            selectOnServer(server, playerId,
                    id -> SelectTargetPayload.machine(id, index, rangeChestA));
            return null;
        }), () -> rangeView().machines().size() == 1
                && rangeView().machines().getFirst().state() == RangeView.MachineState.REPLACED
                && routerScreen().rangeButtonCenter(RangeRowButtons.Action.UPDATE) != null,
                () -> "alcance " + rangeView()));
        list.add(new Step(l + "o ⟳ leva o barril ao grupo da presa e devolve o cartão", STEP_TIMEOUT_MS,
                () -> {
                    PanelNotices.clear();
                    clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.UPDATE), "o ⟳");
                }, () -> !hasGroup(Blocks.CHEST) && rangeView().machines().size() == 1
                        && rangeView().machines().getFirst().group() == rangeGroup(Blocks.BARREL)
                        && E2eRangeCopySteps.noticeHas("updated.one") && E2eRangeCopySteps.noticeHas("cards_returned.one")
                        && onServer(server -> cards(server, playerId)) == cardsBefore[0] + 1,
                () -> "alcance " + rangeView() + ", aviso " + PanelNotices.current()));
        list.add(rangeClipCheck(l + "aviso"));
        list.add(capture("range-q9-atualizar-cartoes"));
        list.add(new Step(l + "o barril sai e o baú volta", STEP_TIMEOUT_MS, () -> onServer(server -> {
            router(server, rangeRouter).unlinkMachine(rangeChestA);
            server.overworld().setBlockAndUpdate(rangeChestA,
                    Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
            return null;
        }), () -> rangeView().machines().isEmpty(), () -> "alcance " + rangeView()));
    }

    /** O mouse fora da tela desarma o ✕ (o clique no tema não move o mouse, e o próximo clique confirmaria). */
    private static Step disarmStep(String name) {
        return new Step(name, STEP_TIMEOUT_MS, () -> moveMouse(0, 0),
                () -> stepTicks >= 2 && !routerScreen().rangeUnlinkArmed(),
                () -> "armado " + routerScreen().rangeUnlinkArmed());
    }

    /** O primeiro clique no ✕ da linha do grupo escolhido só arma. */
    private static Step armStep(String name) {
        return new Step(name, STEP_TIMEOUT_MS,
                () -> clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.UNLINK), "o ✕ do grupo"),
                () -> stepTicks >= 4 && routerScreen().rangeUnlinkArmed()
                        && routerScreen().rangeButtonCenter(RangeRowButtons.Action.RENAME) == null,
                () -> "armado " + routerScreen().rangeUnlinkArmed());
    }

    /**
     * Captura com o ✕ armado e o mouse em cima (a captura comum tiraria o mouse e desarmaria): só o nome do grupo pode
     * sair abreviado (ele fica na dica), o texto do botão nunca.
     */
    private static Step armedCapture(String name, String file) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
        }, () -> {
            boolean onlyName = clippedTexts().stream().allMatch(text -> text.contains("×"));
            if (stepTicks < 6 || !routerScreen().rangeUnlinkArmed() || !onlyName) {
                return false;
            }
            capture(Minecraft.getInstance(), file);
            return true;
        }, () -> "armado " + routerScreen().rangeUnlinkArmed() + ", " + GuiText.clipCount() + " cortado(s): "
                + clippedTexts());
    }

    private static boolean hasGroup(net.minecraft.world.level.block.Block block) {
        try {
            rangeGroup(block);
            return true;
        } catch (StepFailure missing) {
            return false;
        }
    }

    /** Cartões de Filtro no inventário do jogador. */
    private static int cards(MinecraftServer server, UUID playerId) {
        return server.getPlayerList().getPlayer(playerId).getInventory().countItem(ModItems.FILTER_CARD.get());
    }

    private static ItemStack itemCard() {
        ItemStack card = new ItemStack(ModItems.FILTER_CARD.get());
        FilterCardItem.setContents(card, new FilterCardItem.Contents(ResourceType.ITEM, Filter.EMPTY));
        return card;
    }
}
