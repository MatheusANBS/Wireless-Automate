package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.router.RangeRowButtons;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import io.github.matheusanbs.wirelessautomate.client.GuiText;

/**
 * O roteador de alcance no e2e: a lista de grupos e máquinas, o perfil do grupo, a exceção da fornalha, o visor,
 * desvincular pela tela e tirar o upgrade com a tela aberta, em inglês e em português.
 */
final class E2eRangeSteps {
    private E2eRangeSteps() {
    }

    static BlockPos rangeRouter;
    private static BlockPos rangeChestA;
    private static BlockPos rangeChestB;
    static BlockPos rangeFurnace;

    /**
     * Roteador de alcance pela tela: um roteador Avançado num baú, com o Upgrade de Alcance e dois baús e
     * uma fornalha vinculados (a até 3 blocos) pela API do servidor. Em inglês e em português: abre a
     * tela, mostra a lista ({@code range-1-lista}), escolhe o grupo dos baús e põe Trás em Extrair (o
     * perfil; {@code range-2-grupo}, com os lados relativos), escolhe a fornalha e põe Frente em Inserir
     * (vira exceção; {@code range-3-excecao}, com o ↺ na linha dela) e volta ao perfil pelo ↺. Depois, a
     * fornalha trocada no lugar e o Atualizar ({@link E2eRangeReplaceSteps}). Na volta em português,
     * desvincula o segundo baú pela tela, em dois cliques. Sem texto cortado nas capturas (no Desvincular
     * armado da lista, só o nome da linha abreviado).
     */
    static void rangeSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        rangeSetupSteps(list, playerId);
        for (String code : new String[] {"en_us", "pt_br"}) {
            boolean pt = code.equals("pt_br");
            String suffix = pt ? "-pt" : "";
            String label = pt ? "pt: alcance: " : "alcance: ";
            if (pt) {
                list.add(language("pt_br"));
            }
            rangeGroupSteps(list, label, suffix);
            if (!pt) {
                rangeNetworkAndViewSteps(list, label);
            }
            rangeExceptionSteps(list, label, suffix);
            // em inglês o Atualizar sai pelo ⟳ da lista; em português, pelo botão da linha Editando
            E2eRangeReplaceSteps.rangeReplaceSteps(list, label, suffix, !pt);
            if (pt) {
                rangeUnlinkSteps(list, label);
            }
            if (!pt) {
                rangeUpgradeOutSteps(list, label, playerId);
            }
            list.add(close(label + "fechar"));
            if (!pt) {
                list.add(new Step(label + "upgrade de volta", STEP_TIMEOUT_MS, () -> onServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    player.getInventory().clearOrCountMatchingItems(stack -> stack.is(ModItems.RANGE_UPGRADE.get()), -1,
                            player.inventoryMenu.getCraftSlots());
                    router(server, rangeRouter).setRangeUpgrade(new ItemStack(ModItems.RANGE_UPGRADE.get()));
                    return null;
                }), () -> onServer(server -> router(server, rangeRouter).rangeActive()), () -> "upgrade fora"));
            }
            if (pt) {
                list.add(language("en_us"));
            }
        }
        list.add(new Step("alcance: limpar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            RouterBlockEntity router = router(server, rangeRouter);
            // nada cai no chão: sem o upgrade e sem máquinas antes de tirar o bloco
            router.removeRangeUpgrade();
            while (!router.remoteMachines().machines().isEmpty()) {
                router.unlinkMachine(0);
            }
            for (BlockPos pos : List.of(rangeRouter, rangeRouter.below(), rangeChestA, rangeChestB, rangeFurnace)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(rangeRouter).isAir(), () -> "roteador ainda no cliente"));
    }

    /** Alcance: o roteador Avançado com o Upgrade de Alcance, dois baús e uma fornalha vinculados. */
    private static void rangeSetupSteps(List<Step> list, UUID playerId) {
        list.add(new Step("alcance: preparar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            BlockPos chest = chestB.offset(3, 0, 0);
            rangeRouter = chest.above();
            rangeChestA = chest.offset(2, 0, 0);
            rangeChestB = chest.offset(2, 0, -2);
            rangeFurnace = chest.offset(0, 0, -3);
            for (BlockPos pos : List.of(chest, rangeChestA, rangeChestB)) {
                level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
            }
            level.setBlockAndUpdate(rangeFurnace, Blocks.FURNACE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, Direction.SOUTH));
            level.setBlockAndUpdate(rangeRouter, ModBlocks.ROUTER.get().defaultBlockState()
                    .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
            RouterBlockEntity router = router(server, rangeRouter);
            router.setRangeUpgrade(new ItemStack(ModItems.RANGE_UPGRADE.get()));
            router.linkMachine(rangeChestA);
            router.linkMachine(rangeChestB);
            router.linkMachine(rangeFurnace);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return null;
        }), () -> clientSees(rangeRouter) && onServer(server -> router(server, rangeRouter).rangeActive()
                && router(server, rangeRouter).remoteMachines().machines().size() == 3),
                () -> "roteador em " + rangeRouter + ", máquinas "
                        + onServer(server -> router(server, rangeRouter).remoteMachines().machines().size())));
    }

    /** Alcance: abre a tela no mínimo com a lista e põe Trás do grupo dos baús em Extrair. */
    private static void rangeGroupSteps(List<Step> list, String label, String suffix) {
        list.add(open(label + "abrir", () -> rangeRouter));
        list.add(new Step(label + "tela no mínimo, com a lista", STEP_TIMEOUT_MS, () -> {
            routerScreen().previewResize(300, 240);
            if (!routerScreen().rangeListShown()) {
                click(widget(byMessageKey("gui.wirelessautomate.router.range.machines"), "botão Máquinas"));
            }
        }, () -> routerScreen().size()[0] == 300 && routerScreen().size()[1] == 240
                && routerScreen().rangeListShown() && rangeView().machines().size() == 3
                && rangeView().groups().size() == 2 && stepTicks >= 4,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size()) + ", lista "
                        + routerScreen().rangeListShown() + ", alcance " + rangeView()));
        list.add(rangeClipCheck(label + "lista"));
        list.add(capture("range-1-lista" + suffix));

        list.add(new Step(label + "escolher o grupo dos baús", STEP_TIMEOUT_MS,
                () -> clickRow(EditTarget.group(rangeGroup(Blocks.CHEST))),
                () -> rangeTargetIs(EditTarget.group(rangeGroup(Blocks.CHEST)))
                        && routerScreen().getMenu().selectedFace() == Direction.NORTH
                        && panel().sideCenter(RemoteSide.BACK) != null,
                () -> "alvo " + routerScreen().getMenu().snapshot().target() + ", face "
                        + routerScreen().getMenu().selectedFace()));
        list.add(new Step(label + "grupo: Trás em Extrair", STEP_TIMEOUT_MS, () -> {
            clickSide(RemoteSide.BACK);
            clickMode(PortMode.EXTRACT);
        }, () -> {
            int chests = rangeGroup(Blocks.CHEST);
            return routerScreen().getMenu().selectedFace() == Direction.SOUTH
                    && routerScreen().getMenu().snapshot().face(ResourceType.ITEM, Direction.SOUTH).mode()
                            == PortMode.EXTRACT
                    && onServer(server -> router(server, rangeRouter).remoteMachines().group(chests)
                            .profile().face(ResourceType.ITEM, RemoteSide.BACK).mode() == PortMode.EXTRACT);
        },
                () -> "face " + routerScreen().getMenu().selectedFace() + ", modo na tela "
                        + routerScreen().getMenu().snapshot().face(ResourceType.ITEM, Direction.SOUTH).mode()));
        list.add(rangeClipCheck(label + "grupo"));
        list.add(capture("range-2-grupo" + suffix));
    }

    /** Alcance, só em inglês: a rede do perfil pela linha Rede, o visor do grupo e a tela larga. */
    private static void rangeNetworkAndViewSteps(List<Step> list, String label) {
        // a linha Rede no grupo edita a rede da face do perfil; depois volta a "Sem rede"
        list.add(new Step(label + "grupo: Trás na rede principal pela linha Rede", STEP_TIMEOUT_MS,
                () -> chooseNetwork(mainNetwork, MAIN_NETWORK),
                () -> {
                    int chests = rangeGroup(Blocks.CHEST);
                    return routerScreen().getMenu().snapshot().network(ResourceType.ITEM, Direction.SOUTH)
                            .equals(Optional.of(mainNetwork))
                            && onServer(server -> mainNetwork.equals(router(server, rangeRouter).remoteMachines()
                                    .group(chests).profile().face(ResourceType.ITEM, RemoteSide.BACK).network())
                                    && router(server, rangeRouter).faceNetwork(ResourceType.ITEM, Direction.NORTH)
                                            == null);
                }, () -> "rede na tela " + routerScreen().getMenu().snapshot()
                        .network(ResourceType.ITEM, Direction.SOUTH)));
        list.add(rangeClipCheck(label + "grupo com rede"));
        list.add(capture("range-2c-grupo-rede"));
        list.add(new Step(label + "grupo: Trás sem rede", STEP_TIMEOUT_MS,
                () -> chooseNetwork(null, "Sem rede"),
                () -> {
                    int chests = rangeGroup(Blocks.CHEST);
                    return routerScreen().getMenu().snapshot().network(ResourceType.ITEM, Direction.SOUTH).isEmpty()
                            && onServer(server -> router(server, rangeRouter).remoteMachines().group(chests)
                                    .profile().face(ResourceType.ITEM, RemoteSide.BACK).network() == null);
                }, () -> "rede na tela " + routerScreen().getMenu().snapshot()
                        .network(ResourceType.ITEM, Direction.SOUTH)));
        // o visor com o alvo remoto: só o bloco do grupo, com a frente para quem olha
        list.add(new Step(label + "visor do grupo", STEP_TIMEOUT_MS, () -> {
            click(widget(byMessageKey("gui.wirelessautomate.router.range.machines"), "botão Máquinas"));
            routerScreen().previewViewDefault();
        }, () -> !routerScreen().rangeListShown() && stepTicks >= 4,
                () -> "lista " + routerScreen().rangeListShown()));
        list.add(rangeClipCheck(label + "visor"));
        list.add(capture("range-2b-visor"));
        list.add(new Step(label + "de volta à lista", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.router.range.machines"), "botão Máquinas")),
                () -> routerScreen().rangeListShown(), () -> "lista " + routerScreen().rangeListShown()));
        // tela larga: a lista e o visor lado a lado, sem alternar
        list.add(new Step(label + "tela larga, lista e visor lado a lado", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(Math.min(560, routerScreen().width - 8), 280),
                () -> routerScreen().rangeListShown() && routerScreen().rangeViewShown() && stepTicks >= 4,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size()) + ", lista "
                        + routerScreen().rangeListShown() + ", visor " + routerScreen().rangeViewShown()));
        list.add(rangeClipCheck(label + "lado a lado"));
        list.add(capture("range-5-lado-a-lado"));
        list.add(new Step(label + "de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(300, 240),
                () -> routerScreen().size()[0] == 300 && routerScreen().rangeListShown()
                        && !routerScreen().rangeViewShown(),
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size()) + ", visor "
                        + routerScreen().rangeViewShown()));
    }

    /** Alcance: Frente da fornalha em Inserir vira exceção, e Voltar ao perfil. */
    private static void rangeExceptionSteps(List<Step> list, String label, String suffix) {
        // a lista no mínimo não mostra tudo com os baús abertos: fecha os baús e abre a fornalha
        list.add(new Step(label + "fechar os baús e abrir a fornalha", STEP_TIMEOUT_MS, () -> {
            int[] chests = routerScreen().rangeArrowPoint(rangeGroup(Blocks.CHEST));
            if (chests == null) {
                throw new StepFailure("seta do grupo dos baús fora da vista");
            }
            click(routerScreen(), chests[0], chests[1]);
            int[] furnaces = routerScreen().rangeArrowPoint(rangeGroup(Blocks.FURNACE));
            if (furnaces == null) {
                throw new StepFailure("seta do grupo da fornalha fora da vista");
            }
            click(routerScreen(), furnaces[0], furnaces[1]);
        }, () -> routerScreen().rangeRowCenter(EditTarget.machine(rangeMachine(rangeFurnace))) != null
                && routerScreen().rangeRowCenter(EditTarget.machine(rangeMachine(rangeChestA))) == null,
                () -> "linha da fornalha " + java.util.Arrays.toString(
                        routerScreen().rangeRowCenter(EditTarget.machine(rangeMachine(rangeFurnace))))));
        list.add(new Step(label + "escolher a fornalha", STEP_TIMEOUT_MS,
                () -> clickRow(EditTarget.machine(rangeMachine(rangeFurnace))),
                () -> rangeTargetIs(EditTarget.machine(rangeMachine(rangeFurnace)))
                        && routerScreen().getMenu().selectedFace() == Direction.NORTH,
                () -> "alvo " + routerScreen().getMenu().snapshot().target()));
        list.add(new Step(label + "fornalha: Frente em Inserir vira exceção", STEP_TIMEOUT_MS,
                () -> clickMode(PortMode.INSERT),
                () -> {
                    RangeView.MachineView machine = rangeView().machine(rangeMachine(rangeFurnace));
                    return machine != null && machine.exception()
                            && routerScreen().rangeButtonCenter(RangeRowButtons.Action.REVERT) != null
                            && onServer(server -> serverException(server, rangeFurnace));
                }, () -> "alcance " + rangeView()));
        list.add(rangeClipCheck(label + "exceção"));
        list.add(capture("range-3-excecao" + suffix));
        if (suffix.isEmpty()) {
            // armado, o ✕ fica sozinho na linha (o ↺ sai) e sair com o mouse desarma
            armUnlinkSteps(list, label, suffix, () -> rangeFurnace);
            list.add(new Step(label + "sair com o mouse desarma", STEP_TIMEOUT_MS, () -> moveMouse(0, 0),
                    () -> stepTicks >= 2 && !routerScreen().rangeUnlinkArmed()
                            && routerScreen().rangeButtonCenter(RangeRowButtons.Action.REVERT) != null,
                    () -> "armado " + routerScreen().rangeUnlinkArmed()));
        }
        list.add(new Step(label + "Voltar ao perfil", STEP_TIMEOUT_MS,
                () -> clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.REVERT), "o ↺ da fornalha"),
                () -> {
                    RangeView.MachineView machine = rangeView().machine(rangeMachine(rangeFurnace));
                    return machine != null && !machine.exception()
                            && routerScreen().rangeButtonCenter(RangeRowButtons.Action.REVERT) == null
                            && !onServer(server -> serverException(server, rangeFurnace));
                }, () -> "alcance " + rangeView()));
    }

    /** Alcance, só em português: desvincula o segundo baú pela tela. */
    private static void rangeUnlinkSteps(List<Step> list, String label) {
        // desvincular pela tela: o segundo baú sai e a tela volta para a máquina presa
        list.add(new Step(label + "abrir os baús e escolher o segundo", STEP_TIMEOUT_MS, () -> {
            int[] chests = routerScreen().rangeArrowPoint(rangeGroup(Blocks.CHEST));
            int[] furnaces = routerScreen().rangeArrowPoint(rangeGroup(Blocks.FURNACE));
            if (chests == null || furnaces == null) {
                throw new StepFailure("setas dos grupos fora da vista");
            }
            click(routerScreen(), furnaces[0], furnaces[1]);
            click(routerScreen(), chests[0], chests[1]);
        }, () -> routerScreen().rangeRowCenter(EditTarget.machine(rangeMachine(rangeChestB))) != null,
                () -> "linha do segundo baú fora da vista"));
        list.add(new Step(label + "escolher o segundo baú", STEP_TIMEOUT_MS,
                () -> clickRow(EditTarget.machine(rangeMachine(rangeChestB))),
                () -> rangeTargetIs(EditTarget.machine(rangeMachine(rangeChestB))),
                () -> "alvo " + routerScreen().getMenu().snapshot().target()));
        armUnlinkSteps(list, label, "-pt", () -> rangeChestB);
        list.add(new Step(label + "Desvincular: o segundo confirma", STEP_TIMEOUT_MS,
                () -> clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.UNLINK), "Desvincular?"),
                () -> rangeView().machines().size() == 2 && routerScreen().getMenu().snapshot().target().attached()
                        && onServer(server -> !router(server, rangeRouter).remoteMachines().contains(rangeChestB)),
                () -> "alcance " + rangeView()));
    }

    /**
     * O ✕ da máquina escolhida pede dois cliques: o primeiro só arma, e o botão vira o "Desvincular?"
     * sozinho na linha, com o texto inteiro ({@code range-6-desvincular-armado}).
     */
    private static void armUnlinkSteps(List<Step> list, String label, String suffix, Supplier<BlockPos> pos) {
        list.add(new Step(label + "Desvincular: o primeiro clique arma", STEP_TIMEOUT_MS,
                () -> clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.UNLINK), "o ✕ da máquina"),
                () -> stepTicks >= 4 && routerScreen().rangeUnlinkArmed()
                        && routerScreen().rangeButtonCenter(RangeRowButtons.Action.REVERT) == null
                        && onServer(server -> router(server, rangeRouter).remoteMachines().contains(pos.get())),
                () -> "armado " + routerScreen().rangeUnlinkArmed() + ", alcance " + rangeView()));
        // a captura comum tira o mouse da tela, o que desarma: esta deixa o mouse no botão. O nome da linha
        // pode sair abreviado (o texto inteiro fica na dica), o "Desvincular?" nunca
        list.add(new Step(label + "armado: só o nome da linha abreviado", STEP_TIMEOUT_MS, () -> {
        }, () -> {
            boolean onlyName = clippedTexts().stream().allMatch(text -> text.startsWith("#"));
            if (stepTicks < 6 || !routerScreen().rangeUnlinkArmed() || !onlyName) {
                return false;
            }
            capture(Minecraft.getInstance(), "range-6-desvincular-armado" + suffix);
            return true;
        }, () -> "armado " + routerScreen().rangeUnlinkArmed() + ", " + GuiText.clipCount() + " texto(s) cortado(s): "
                + clippedTexts()));
    }

    /** Alcance, só em inglês: tira o upgrade pelo slot com a tela num alvo remoto. */
    private static void rangeUpgradeOutSteps(List<Step> list, String label, UUID playerId) {
        // tirar o upgrade pelo slot com a tela num alvo remoto: a tela volta a ser a de sempre, na
        // face onde o roteador está preso, igual no cliente e no servidor
        list.add(new Step(label + "tirar o upgrade com a tela aberta", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.handleInventoryMouseClick(routerScreen().getMenu().containerId,
                    RouterMenu.RANGE_SLOT, 0, ClickType.QUICK_MOVE, minecraft.player);
        }, () -> {
            RouterMenu menu = routerScreen().getMenu();
            Direction serverFace = onServer(server -> server.getPlayerList().getPlayer(playerId).containerMenu
                    instanceof RouterMenu m && m.target().attached() ? m.selectedFace() : null);
            return !menu.snapshot().range().present() && menu.snapshot().target().attached()
                    && menu.selectedFace() == Direction.UP && serverFace == Direction.UP
                    && find(byMessageKey("gui.wirelessautomate.router.range.machines")) == null
                    && !onServer(server -> router(server, rangeRouter).rangeActive()) && stepTicks >= 4;
        }, () -> "face no cliente " + routerScreen().getMenu().selectedFace() + ", alcance "
                + routerScreen().getMenu().snapshot().range().present() + ", alvo "
                + routerScreen().getMenu().snapshot().target()));
        list.add(rangeClipCheck(label + "sem upgrade"));
        list.add(capture("range-4-sem-upgrade"));
    }

    /** No servidor, a máquina remota em {@code pos} tem exceção. */
    private static boolean serverException(MinecraftServer server, BlockPos pos) {
        var machines = router(server, rangeRouter).remoteMachines();
        var machine = machines.machine(machines.index(pos));
        return machine != null && machine.hasException();
    }

    static RangeView rangeView() throws StepFailure {
        return routerScreen().getMenu().snapshot().range();
    }

    /** O id do grupo do bloco {@code block} na tela. */
    static int rangeGroup(net.minecraft.world.level.block.Block block) throws StepFailure {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        for (RangeView.GroupView group : rangeView().groups()) {
            if (group.block().equals(id)) {
                return group.id();
            }
        }
        throw new StepFailure("sem grupo de " + id + " na tela: " + rangeView().groups());
    }

    /** O índice da máquina em {@code pos} na tela. */
    static int rangeMachine(BlockPos pos) throws StepFailure {
        List<RangeView.MachineView> machines = rangeView().machines();
        for (int i = 0; i < machines.size(); i++) {
            if (machines.get(i).pos().equals(pos)) {
                return i;
            }
        }
        throw new StepFailure("sem máquina em " + pos.toShortString() + " na tela");
    }

    /** Clica na linha da lista do alvo {@code target}. */
    static void clickRow(EditTarget target) throws StepFailure {
        int[] center = routerScreen().rangeRowCenter(target);
        if (center == null) {
            throw new StepFailure("linha de " + target + " fora da vista");
        }
        click(routerScreen(), center[0], center[1]);
    }

    /** A tela e o menu do servidor estão no alvo {@code target}. */
    static boolean rangeTargetIs(EditTarget target) throws Exception {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        return routerScreen().getMenu().snapshot().target().equals(target) && onServer(server ->
                server.getPlayerList().getPlayer(playerId).containerMenu instanceof RouterMenu menu
                        && menu.target().equals(target));
    }

    /** Nenhum texto cortado na tela do roteador de alcance (mouse fora dela, alguns quadros depois). */
    static Step rangeClipCheck(String name) {
        return new Step(name + ": nenhum texto cortado", STEP_TIMEOUT_MS, () -> moveMouse(0, 0),
                () -> stepTicks >= 4 && GuiText.clipCount() == 0,
                () -> GuiText.clipCount() + " texto(s) cortado(s): " + clippedTexts());
    }

    static List<String> clippedTexts() {
        List<String> texts = new ArrayList<>();
        for (int[] c : GuiText.clipCenters()) {
            Component full = GuiText.clipAt(c[0], c[1]);
            texts.add(full == null ? "?" : full.getString());
        }
        return texts;
    }
}
