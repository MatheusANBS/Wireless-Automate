package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.linker.LinkerOpenScreen;
import io.github.matheusanbs.wirelessautomate.client.linker.LinkerSameBlockInput;
import io.github.matheusanbs.wirelessautomate.client.linker.LinkerScreen;
import io.github.matheusanbs.wirelessautomate.client.router.RouterScreen;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.packet.CycleLinkerModePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * O Vinculador no e2e: um roteador Avançado com o Upgrade de Alcance escolhido com o clique, a área marcada com
 * Shift + clique, a tela com o Vincular e o Desvincular, o Ctrl + clique que vincula os funis (e não vale correndo),
 * e o modo Abrir com a lista de dois roteadores.
 */
final class E2eLinkerSteps {
    /** Onde o jogador estava antes de ir para perto do baú do Vinculador, e para onde olhava. */
    private static Vec3 beforeLinker = Vec3.ZERO;
    private static float[] beforeLinkerRot = new float[2];

    private E2eLinkerSteps() {
    }

    private static BlockPos linkRouter;
    /** O baú que o Vincular junta ao roteador, com um roteador preso nele (os dois chegam na mesma máquina). */
    static BlockPos linkChest;
    private static BlockPos linkAttached;
    /**
     * Os dois funis que o Ctrl + clique vincula juntos: um bloco que nenhuma outra suíte usa, para a contagem do aviso
     * não pegar máquinas de fora.
     */
    private static BlockPos linkHopper;
    private static BlockPos linkHopperSame;
    private static BlockPos linkCornerA;
    private static BlockPos linkCornerB;

    /**
     * Vincular: com o Vinculador na mão, clique no roteador o escolhe; Shift + clique em dois blocos (o chão ao lado e o baú) marca a área em
     * volta do baú; a tela mostra o roteador e a área, Vincular junta o baú ao roteador, Desvincular o tira (com
     * a tela maior no meio). Abrir: Shift + clique no ar troca o modo, clique no baú (que tem o roteador preso e
     * o de alcance) abre a lista, e a escolha abre a tela do roteador.
     */
    static void linkerSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        list.add(new Step("Vinculador: preparar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            BlockPos chest = chestB.offset(3, 0, 6);
            linkRouter = chest.above();
            linkChest = chest.offset(2, 0, 0);
            linkAttached = linkChest.above();
            linkHopper = linkChest.offset(1, 0, 2);
            linkHopperSame = linkChest.offset(-1, 0, 2);
            linkCornerA = linkChest.offset(-1, -1, -1);
            linkCornerB = linkChest;
            for (BlockPos pos : List.of(chest, linkChest)) {
                level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
            }
            for (BlockPos pos : List.of(linkHopper, linkHopperSame)) {
                level.setBlockAndUpdate(pos, Blocks.HOPPER.defaultBlockState());
            }
            level.setBlockAndUpdate(linkRouter, ModBlocks.ROUTER.get().defaultBlockState()
                    .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
            level.setBlockAndUpdate(linkAttached, ModBlocks.ROUTER.get().defaultBlockState()
                    .setValue(RouterBlock.FACING, Direction.UP));
            router(server, linkRouter).setRangeUpgrade(new ItemStack(ModItems.RANGE_UPGRADE.get()));
            // perto de tudo: os cliques precisam estar ao alcance do jogador
            beforeLinker = player.position();
            beforeLinkerRot = new float[] {player.getYRot(), player.getXRot()};
            player.teleportTo(chest.getX() + 2.5, chest.getY() + 1, chest.getZ() + 2.5);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
            return null;
        }), () -> clientSees(linkRouter) && clientSees(linkAttached)
                && Minecraft.getInstance().player.getMainHandItem().is(ModItems.LINKER.get())
                && onServer(server -> router(server, linkRouter).rangeActive()),
                () -> "roteador em " + linkRouter));
        list.add(new Step("Vinculador: clique no roteador o escolhe", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(linkRouter), Direction.UP, linkRouter, false));
        }, () -> serverLinker(stack -> LinkerItem.router(stack) != null
                && LinkerItem.router(stack).pos().equals(linkRouter)),
                () -> "roteador no servidor " + serverLinker(stack -> String.valueOf(LinkerItem.router(stack)))));
        list.add(shift("Shift pressionado", true));
        list.add(new Step("Vinculador: Shift + clique nos dois cantos", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            for (BlockPos corner : List.of(linkCornerA, linkCornerB)) {
                minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(corner), Direction.UP, corner, false));
            }
        }, () -> serverLinker(stack -> {
            LinkerArea area = LinkerItem.area(stack);
            return area != null && area.first().equals(linkCornerA) && area.second().equals(Optional.of(linkCornerB));
        }) && LinkerItem.area(Minecraft.getInstance().player.getMainHandItem()) != null
                && LinkerItem.area(Minecraft.getInstance().player.getMainHandItem()).complete(),
                () -> "área no servidor " + serverLinker(stack -> String.valueOf(LinkerItem.area(stack)))));
        list.add(shift("Shift solto", false));
        list.add(capture("7b-vinculador-contorno"));
        list.add(new Step("Vinculador: abrir a tela (clique no ar)", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                throw new StepFailure("ainda há uma tela aberta: " + describe(minecraft.screen));
            }
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof LinkerScreen screen
                && screen.getMenu().snapshot().router().links()
                && screen.getMenu().snapshot().problem() == LinkerProblem.NONE,
                () -> Minecraft.getInstance().screen instanceof LinkerScreen screen
                        ? "roteador " + screen.getMenu().snapshot().router() + ", " + screen.getMenu().snapshot().problem()
                        : "tela " + describe(Minecraft.getInstance().screen)));
        list.add(clipCheck("Vinculador: tela"));
        list.add(capture("8-vinculador-area"));
        list.add(new Step("Vinculador: Vincular", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.link.label"), "Vincular")),
                () -> onServer(server -> router(server, linkRouter).remoteMachines().contains(linkChest))
                        && linkerScreen().getMenu().snapshot().outcome().map(o -> !o.unlink() && o.linked() == 1)
                                .orElse(false),
                () -> "máquinas " + onServer(server -> router(server, linkRouter).remoteMachines().machines().size())
                        + "; resultado na tela " + linkerScreen().getMenu().snapshot().outcome()));
        list.add(capture("9-vinculador-vinculado"));
        list.add(new Step("Vinculador maior", STEP_TIMEOUT_MS,
                () -> linkerScreen().previewResize(460, 300),
                () -> linkerScreen().size()[0] > 300 && linkerScreen().size()[1] > 204,
                () -> "tamanho " + java.util.Arrays.toString(linkerScreen().size())));
        list.add(capture("8d-vinculador-grande"));
        list.add(new Step("Vinculador de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> linkerScreen().previewResize(300, 204),
                () -> linkerScreen().size()[0] == 300 && linkerScreen().size()[1] == 204,
                () -> "tamanho " + java.util.Arrays.toString(linkerScreen().size())));
        list.add(new Step("Vinculador: seletor Desvincular", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.action.unlink"), "Desvincular")),
                // o botão "Desvincular N" nasce quando a tela refaz os widgets com o snapshot novo, um quadro depois
                () -> serverLinker(LinkerItem::unlink) && linkerScreen().getMenu().snapshot().unlink()
                        && find(byMessageKey("gui.wirelessautomate.linker.unlink.count")) != null,
                () -> "desvincular na tela " + linkerScreen().getMenu().snapshot().unlink()));
        list.add(new Step("Vinculador: Desvincular a área", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.unlink.count"), "Desvincular N")),
                () -> onServer(server -> !router(server, linkRouter).remoteMachines().contains(linkChest))
                        && linkerScreen().getMenu().snapshot().outcome().map(o -> o.unlink() && o.linked() == 1)
                                .orElse(false),
                () -> "resultado na tela " + linkerScreen().getMenu().snapshot().outcome()));
        list.add(clipCheck("Vinculador: desvinculado"));
        list.add(capture("9b-vinculador-desvincular"));
        list.add(close("Vinculador: fechar a tela"));
        ctrlClickSteps(list);
        list.add(capture("9c-vinculador-ctrl-clique"));
        openSteps(list);
    }

    /**
     * Ctrl + clique num funil, pelo caminho do jogo: o {@code InteractionKeyMappingTriggered} postado como o jogo posta
     * ({@code ClientHooks.onClickInput}), duas vezes com a tecla de uso descida (o jogo repete o uso com o botão
     * segurado). O Ctrl de verdade não chega ao {@code Screen.hasControlDown()} no e2e, então o passo o força pelo
     * gancho do {@link LinkerSameBlockInput}. Os dois usos são cancelados, um pedido só vai ao servidor, os dois funis
     * entram e a action bar diz 2. Depois, correndo (ou mirando o chão, que não é máquina), o gesto não vale: o uso
     * passa e nenhum pedido sai.
     */
    private static void ctrlClickSteps(List<Step> list) {
        int[] sentBefore = new int[1];
        list.add(new Step("Vinculador: Ctrl + clique segurado manda um pedido só", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            KeyMapping use = minecraft.options.keyUse;
            sentBefore[0] = LinkerSameBlockInput.sentCount();
            boolean[] canceled = new boolean[2];
            LinkerSameBlockInput.forceControl(true);
            KeyMapping.set(use.getKey(), true);
            try {
                minecraft.hitResult = new BlockHitResult(Vec3.atCenterOf(linkHopper), Direction.UP, linkHopper, false);
                canceled[0] = ClientHooks.onClickInput(1, use, InteractionHand.MAIN_HAND).isCanceled();
                canceled[1] = ClientHooks.onClickInput(1, use, InteractionHand.MAIN_HAND).isCanceled();
            } finally {
                KeyMapping.set(use.getKey(), false);
                LinkerSameBlockInput.forceControl(false);
            }
            int sent = LinkerSameBlockInput.sentCount() - sentBefore[0];
            if (!canceled[0] || !canceled[1] || sent != 1) {
                throw new StepFailure("usos cancelados " + canceled[0] + "/" + canceled[1] + ", pedidos " + sent);
            }
        }, () -> onServer(server -> router(server, linkRouter).remoteMachines().contains(linkHopper)
                && router(server, linkRouter).remoteMachines().contains(linkHopperSame))
                && "item.wirelessautomate.linker.range.same.linked".equals(E2eResonatorSteps.overlayKey())
                && E2eResonatorSteps.overlayArgs().length > 0
                && Integer.valueOf(2).equals(E2eResonatorSteps.overlayArgs()[0])
                && LinkerSameBlockInput.sentCount() - sentBefore[0] == 1,
                () -> "máquinas " + onServer(server -> router(server, linkRouter).remoteMachines().machines().size())
                        + "; action bar " + E2eResonatorSteps.overlayKey() + " "
                        + java.util.Arrays.toString(E2eResonatorSteps.overlayArgs()) + "; pedidos "
                        + (LinkerSameBlockInput.sentCount() - sentBefore[0])));
        list.add(new Step("Vinculador: correndo ou no chão, o Ctrl + clique não vale", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            KeyMapping use = minecraft.options.keyUse;
            sentBefore[0] = LinkerSameBlockInput.sentCount();
            boolean sprinting;
            boolean floor;
            LinkerSameBlockInput.forceControl(true);
            try {
                minecraft.player.setSprinting(true);
                minecraft.hitResult = new BlockHitResult(Vec3.atCenterOf(linkHopper), Direction.UP, linkHopper, false);
                sprinting = ClientHooks.onClickInput(1, use, InteractionHand.MAIN_HAND).isCanceled();
                minecraft.player.setSprinting(false);
                BlockPos ground = linkHopper.below();
                minecraft.hitResult = new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false);
                floor = ClientHooks.onClickInput(1, use, InteractionHand.MAIN_HAND).isCanceled();
            } finally {
                minecraft.player.setSprinting(false);
                LinkerSameBlockInput.forceControl(false);
            }
            if (sprinting || floor) {
                throw new StepFailure("cancelado correndo " + sprinting + ", no chão " + floor);
            }
        }, () -> stepTicks >= 2 && LinkerSameBlockInput.sentCount() == sentBefore[0]
                // o descanso do Ctrl + clique acaba antes dos cliques do Abrir (no descanso, o clique abriria o baú)
                && !Minecraft.getInstance().player.getCooldowns().isOnCooldown(ModItems.LINKER.get()),
                () -> "pedidos " + (LinkerSameBlockInput.sentCount() - sentBefore[0]) + ", descanso "
                        + Minecraft.getInstance().player.getCooldowns().isOnCooldown(ModItems.LINKER.get())));
    }

    /**
     * Abrir: o baú tem o roteador preso e o de alcance (vinculado pela API do servidor), então o clique manda a lista
     * de dois; a escolha da primeira linha abre a tela do roteador. Termina limpando o que montou.
     */
    private static void openSteps(List<Step> list) {
        list.add(shift("Shift pressionado", true));
        // o pacote que o HeldScrollHandler manda no Shift + roda (a roda de verdade não chega à janela no e2e)
        list.add(new Step("Vinculador: Shift + roda troca para Abrir", STEP_TIMEOUT_MS,
                () -> PacketDistributor.sendToServer(new CycleLinkerModePayload(1)), () -> serverLinker(stack -> LinkerItem.mode(stack) == LinkerMode.OPEN)
                && LinkerItem.mode(Minecraft.getInstance().player.getMainHandItem()) == LinkerMode.OPEN,
                () -> "modo no servidor " + serverLinker(stack -> LinkerItem.mode(stack).name())));
        list.add(shift("Shift solto", false));
        list.add(new Step("Vinculador: vincular o baú ao roteador de alcance", STEP_TIMEOUT_MS,
                () -> onServer(server -> router(server, linkRouter).linkMachine(linkChest)),
                () -> onServer(server -> router(server, linkRouter).remoteMachines().contains(linkChest)),
                () -> "baú não vinculado"));
        list.add(new Step("Vinculador: clique no baú abre a lista", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(linkChest), Direction.UP, linkChest, false));
        }, () -> Minecraft.getInstance().screen instanceof LinkerOpenScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(clipCheck("Vinculador: lista do Abrir"));
        list.add(capture("8e-vinculador-abrir-lista"));
        list.add(new Step("Vinculador: escolher a primeira linha abre o roteador", STEP_TIMEOUT_MS, () -> {
            LinkerOpenScreen screen = (LinkerOpenScreen) Minecraft.getInstance().screen;
            int[] point = screen.rowCenter(0);
            screen.mouseClicked(point[0], point[1], 0);
        }, () -> Minecraft.getInstance().screen instanceof RouterScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(close("Vinculador: fechar o roteador"));
        list.add(new Step("Vinculador: limpar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            RouterBlockEntity router = router(server, linkRouter);
            // nada cai no chão: sem o upgrade e sem máquinas antes de tirar o bloco
            router.removeRangeUpgrade();
            while (!router.remoteMachines().machines().isEmpty()) {
                router.unlinkMachine(0, null);
            }
            for (BlockPos pos : List.of(linkAttached, linkChest, linkHopper, linkHopperSame, linkRouter,
                    linkRouter.below())) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            ServerPlayer player = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            // de volta para onde estava: os passos seguintes clicam em blocos ao alcance da mão
            player.teleportTo(player.serverLevel(), beforeLinker.x, beforeLinker.y, beforeLinker.z,
                    beforeLinkerRot[0], beforeLinkerRot[1]);
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(linkRouter).isAir()
                && Minecraft.getInstance().player.getMainHandItem().isEmpty()
                && Minecraft.getInstance().player.position().distanceTo(beforeLinker) < 0.5,
                () -> "roteador ainda no cliente ou jogador longe: "
                        + Minecraft.getInstance().player.position().distanceTo(beforeLinker)));
    }

    /** Lê o Vinculador da mão principal do jogador no servidor. */
    static <T> T serverLinker(Function<ItemStack, T> read) throws Exception {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        return onServer(server -> read.apply(server.getPlayerList().getPlayer(playerId).getMainHandItem()));
    }

    static LinkerScreen linkerScreen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof LinkerScreen screen) {
            return screen;
        }
        throw new StepFailure("a tela do Vinculador não está aberta (tela: " + describe(Minecraft.getInstance().screen) + ")");
    }
}
