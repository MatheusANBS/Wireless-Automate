package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.tuner.TunerProposal;
import io.github.matheusanbs.wirelessautomate.item.tuner.TunerPending;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorProposalPayload;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;

/**
 * O Afinador do Configurador no e2e: uma fornalha no ar (frente para o Norte), vinculada a um roteador de alcance, e a
 * câmera embaixo dela, a sudeste, de onde se veem Baixo, Trás e Leste. O clique mostra a pintura
 * ({@code cfg-afinador-proposta}, também {@code -escuro} e {@code -pt}); depois do intervalo mínimo, o segundo clique
 * aplica (Baixo Extrai, Cima e Trás Insere) e a pintura some ({@code cfg-afinador-aplicado}). Com uma proposta nova,
 * a tecla de uso segurada não confirma (o segundo {@code InteractionKeyMappingTriggered} vem cancelado e o jogo, que
 * repete o uso com a tecla descida, não muda a fornalha), e o clique no ar a apaga nos dois lados. Mirar outro bloco
 * por 2 s também a apaga, no cliente e no servidor. No fim, uma proposta montada no cliente com dois tipos por face
 * mostra as letras ({@code cfg-afinador-letras}), com a action bar limpa.
 */
final class E2eTunerSteps {
    /** O segundo clique precisa de 6 ticks (servidor) e do fim do descanso do item (10 ticks). */
    private static final int SECOND_CLICK_TICKS = 14;
    /** O clique no ar só apaga uma pausa depois do fim do descanso (servidor: 10 + 5 ticks depois da análise). */
    private static final int AIR_CLICK_TICKS = 18;
    /** Quanto tempo a tecla de uso fica descida no passo da trava: o jogo repete o uso a cada 4 ticks. */
    private static final int HOLD_TICKS = 30;
    private static final List<PortMode> UNTOUCHED = List.of(PortMode.NONE, PortMode.NONE, PortMode.NONE);

    private static BlockPos furnace = BlockPos.ZERO;
    private static BlockPos router = BlockPos.ZERO;

    private E2eTunerSteps() {
    }

    static void tunerSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        Vec3[] back = new Vec3[1];
        setupSteps(list, playerId, back);
        list.add(camera("afinador: câmera embaixo da fornalha", 1.6, -2.4, 2.4));
        list.add(click("afinador: clicar mostra a proposta", () -> proposalShown()));
        list.add(capture("cfg-afinador-proposta"));
        E2eVariants.escuro(list, () -> {
            list.add(new Step("afinador: tema escuro", STEP_TIMEOUT_MS, () -> GuiThemes.set(GuiTheme.DARK),
                    () -> GuiThemes.current() == GuiTheme.DARK && proposalShown(), () -> "tema " + GuiThemes.current()));
            list.add(capture("cfg-afinador-proposta-escuro"));
            list.add(new Step("afinador: tema claro", STEP_TIMEOUT_MS, () -> GuiThemes.set(GuiTheme.LIGHT),
                    () -> GuiThemes.current() == GuiTheme.LIGHT, () -> "tema " + GuiThemes.current()));
        });
        list.add(DevEndToEnd.wait("afinador: intervalo mínimo", SECOND_CLICK_TICKS));
        list.add(click("afinador: o segundo clique aplica", () -> TunerProposal.current() == null && applied()));
        list.add(capture("cfg-afinador-aplicado"));

        list.add(revert("afinador: modos de volta"));
        list.add(DevEndToEnd.wait("afinador: descanso antes da trava", SECOND_CLICK_TICKS));
        list.add(click("afinador: proposta para a trava", () -> proposalShown()));
        list.add(holdLock(playerId));
        list.add(DevEndToEnd.wait("afinador: pausa antes do clique no ar", AIR_CLICK_TICKS));
        list.add(new Step("afinador: clique no ar apaga", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> TunerProposal.current() == null && !pendingOnServer(playerId),
                () -> "proposta " + TunerProposal.current() + ", no servidor " + pendingOnServer(playerId)));

        E2eVariants.pt(list, () -> {
            list.add(language("pt_br"));
            list.add(DevEndToEnd.wait("pt: afinador: descanso", SECOND_CLICK_TICKS));
            list.add(click("pt: afinador: clicar mostra a proposta", () -> proposalShown()));
            list.add(capture("cfg-afinador-proposta-pt"));
            list.add(language("en_us"));
        });

        list.add(DevEndToEnd.wait("afinador: descanso", SECOND_CLICK_TICKS));
        list.add(click("afinador: clicar de novo", () -> proposalShown()));
        int[] gone = new int[1];
        list.add(new Step("afinador: mirar outro bloco por 2 s apaga", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            gone[0] = -1;
            // o chão logo abaixo da câmera
            minecraft.player.setXRot(89f);
            minecraft.player.xRotO = 89f;
        }, () -> {
            if (gone[0] < 0 && TunerProposal.current() == null) {
                gone[0] = stepTicks;
            }
            return gone[0] >= 0;
        }, () -> "proposta " + TunerProposal.current() + " em " + stepTicks + " ticks, mira "
                + Minecraft.getInstance().hitResult));
        list.add(new Step("afinador: apagou depois de uns 2 s, não antes, e no servidor também", STEP_TIMEOUT_MS, () -> {
        }, () -> gone[0] >= 38 && !pendingOnServer(playerId),
                () -> "apagou em " + gone[0] + " ticks, no servidor " + pendingOnServer(playerId)));
        list.add(camera("afinador: câmera de volta", 1.6, -2.4, 2.4));
        list.add(new Step("afinador: letras com dois tipos por face", STEP_TIMEOUT_MS, () -> {
            // a action bar ainda traz o resumo da proposta anterior, que não é a desta captura
            Minecraft.getInstance().gui.setOverlayMessage(Component.empty(), false);
            TunerProposal.receive(new ConfiguratorProposalPayload(furnace, List.of(
                    new ConfiguratorProposalPayload.Face(Direction.DOWN.ordinal(), ResourceType.ITEM, PortMode.EXTRACT),
                    new ConfiguratorProposalPayload.Face(Direction.DOWN.ordinal(), ResourceType.ENERGY, PortMode.INSERT),
                    new ConfiguratorProposalPayload.Face(Direction.SOUTH.ordinal(), ResourceType.ITEM, PortMode.INSERT),
                    new ConfiguratorProposalPayload.Face(Direction.SOUTH.ordinal(), ResourceType.FLUID, PortMode.EXTRACT),
                    new ConfiguratorProposalPayload.Face(Direction.SOUTH.ordinal(), ResourceType.ENERGY, PortMode.BOTH),
                    new ConfiguratorProposalPayload.Face(Direction.EAST.ordinal(), ResourceType.FLUID, PortMode.BOTH),
                    // fora de 0..5: ignorada
                    new ConfiguratorProposalPayload.Face(9, ResourceType.ITEM, PortMode.INSERT)), 2000));
        }, () -> TunerProposal.current() != null && stepTicks >= 2, () -> "proposta " + TunerProposal.current()));
        list.add(capture("cfg-afinador-letras"));
        list.add(new Step("afinador: o prazo é limitado a 400 ticks", STEP_TIMEOUT_MS, () -> {
        }, () -> {
            TunerProposal.Shown shown = TunerProposal.current();
            return shown != null && shown.until() - Minecraft.getInstance().level.getGameTime() <= 400;
        }, () -> "proposta " + TunerProposal.current()));
        cleanupSteps(list, playerId, back);
    }

    /**
     * A trava do botão segurado, pelo caminho do jogo: com a tecla de uso descida e a mira na fornalha, o primeiro
     * {@code InteractionKeyMappingTriggered} passa e o segundo vem cancelado; depois o jogo repete o uso sozinho por
     * {@link #HOLD_TICKS} ticks, e a fornalha continua sem mudança, com a proposta de pé.
     */
    private static Step holdLock(UUID playerId) {
        boolean[] canceled = new boolean[2];
        return new Step("afinador: o botão segurado não confirma", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            KeyMapping use = minecraft.options.keyUse;
            KeyMapping.set(use.getKey(), true);
            canceled[0] = ClientHooks.onClickInput(1, use, InteractionHand.MAIN_HAND).isCanceled();
            canceled[1] = ClientHooks.onClickInput(1, use, InteractionHand.MAIN_HAND).isCanceled();
            if (canceled[0] || !canceled[1]) {
                KeyMapping.set(use.getKey(), false);
                throw new StepFailure("o primeiro uso cancelado: " + canceled[0] + ", o segundo: " + canceled[1]);
            }
        }, () -> {
            if (stepTicks < HOLD_TICKS) {
                return false;
            }
            KeyMapping.set(Minecraft.getInstance().options.keyUse.getKey(), false);
            return modes().equals(UNTOUCHED) && proposalShown() && pendingOnServer(playerId);
        }, () -> "modos " + modes() + ", proposta " + TunerProposal.current() + ", no servidor "
                + pendingOnServer(playerId));
    }

    /** O servidor ainda guarda uma proposta do jogador. */
    private static boolean pendingOnServer(UUID playerId) throws Exception {
        return onServer(server -> TunerPending.get(playerId) != null);
    }

    /** O roteador Avançado com o Upgrade de Alcance e a fornalha no ar, vinculada; o Configurador na mão. */
    private static void setupSteps(List<Step> list, UUID playerId, Vec3[] back) {
        list.add(new Step("afinador: preparar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            back[0] = player.position();
            BlockPos origin = player.blockPosition();
            furnace = origin.offset(0, 4, 6);
            router = origin.offset(-3, 0, 6);
            level.setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState()
                    .setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(router, ModBlocks.ROUTER.get().defaultBlockState()
                    .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
            RouterBlockEntity be = router(server, router);
            be.setRangeUpgrade(new ItemStack(ModItems.RANGE_UPGRADE.get()));
            be.linkMachine(furnace);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CONFIGURATOR.get()));
            TunerProposal.clear();
            return null;
        }), () -> clientSees(router) && Minecraft.getInstance().player.getMainHandItem().is(ModItems.CONFIGURATOR.get())
                && onServer(server -> router(server, router).remoteMachines().index(furnace) >= 0),
                () -> "roteador em " + router));
    }

    private static void cleanupSteps(List<Step> list, UUID playerId, Vec3[] back) {
        list.add(new Step("afinador: limpar", STEP_TIMEOUT_MS, () -> {
            TunerProposal.clear();
            Minecraft.getInstance().options.hideGui = false;
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ServerLevel level = player.serverLevel();
                RouterBlockEntity be = router(server, router);
                be.removeRangeUpgrade();
                be.unlinkMachine(furnace);
                level.setBlockAndUpdate(router, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(furnace, Blocks.AIR.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.teleportTo(level, back[0].x, back[0].y, back[0].z, player.getYRot(), 0);
                return null;
            });
        }, () -> Minecraft.getInstance().level.getBlockState(router).isAir()
                && Minecraft.getInstance().player.position().distanceTo(back[0]) < 0.5,
                () -> "posição " + Minecraft.getInstance().player.position()));
    }

    /** O clique direito na fornalha com o Configurador, pela face de baixo, como o jogo faria. */
    private static Step click(String name, Check done) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(furnace), Direction.DOWN, furnace, false);
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        }, done, () -> "proposta " + TunerProposal.current() + ", modos " + modes());
    }

    /** A proposta da fornalha chegou: Baixo em Extrai, Cima em Insere. */
    private static boolean proposalShown() {
        TunerProposal.Shown shown = TunerProposal.current();
        return shown != null && shown.machine().equals(furnace)
                && shown.faces().contains(new ConfiguratorProposalPayload.Face(Direction.DOWN.ordinal(),
                        ResourceType.ITEM, PortMode.EXTRACT))
                && shown.faces().contains(new ConfiguratorProposalPayload.Face(Direction.UP.ordinal(),
                        ResourceType.ITEM, PortMode.INSERT));
    }

    /** No servidor, a fornalha está com Baixo Extrai e Cima e Trás Insere. */
    private static boolean applied() throws Exception {
        return modes().equals(List.of(PortMode.EXTRACT, PortMode.INSERT, PortMode.INSERT));
    }

    /** Os modos de Itens da fornalha no servidor: Baixo, Cima e Trás. */
    private static List<PortMode> modes() throws Exception {
        return onServer(server -> {
            var machines = router(server, router).remoteMachines();
            var config = machines.effective(machines.machine(machines.index(furnace)));
            return List.of(config.face(ResourceType.ITEM, RemoteSide.BOTTOM).mode(),
                    config.face(ResourceType.ITEM, RemoteSide.TOP).mode(),
                    config.face(ResourceType.ITEM, RemoteSide.BACK).mode());
        });
    }

    /** A fornalha volta ao perfil (tudo em Nenhum). */
    private static Step revert(String name) {
        return new Step(name, STEP_TIMEOUT_MS, () -> onServer(server -> {
            RouterBlockEntity be = router(server, router);
            be.revertToProfile(be.remoteMachines().index(furnace));
            return null;
        }), () -> modes().equals(UNTOUCHED), () -> "modos " + modes());
    }

    /**
     * A câmera em {@code fornalha + (dx, dy, dz)} (o olho), voando, olhando o centro da fornalha, com a interface à
     * vista (a action bar resume a proposta).
     */
    private static Step camera(String name, double dx, double dy, double dz) {
        double[] cam = new double[3];
        float[] ang = new float[2];
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            double eyeX = furnace.getX() + 0.5 + dx;
            double eyeY = furnace.getY() + 0.5 + dy;
            double eyeZ = furnace.getZ() + 0.5 + dz;
            cam[0] = eyeX;
            cam[1] = eyeY - 1.62;
            cam[2] = eyeZ;
            double ex = -dx;
            double ey = -dy;
            double ez = -dz;
            ang[0] = (float) Math.toDegrees(Math.atan2(-ex, ez));
            ang[1] = (float) -Math.toDegrees(Math.atan2(ey, Math.sqrt(ex * ex + ez * ez)));
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.options.hideGui = false;
            minecraft.player.setYRot(ang[0]);
            minecraft.player.setXRot(ang[1]);
            minecraft.player.yRotO = ang[0];
            minecraft.player.xRotO = ang[1];
            UUID playerId = minecraft.player.getUUID();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.serverLevel(), cam[0], cam[1], cam[2], ang[0], ang[1]);
                return null;
            });
        }, () -> Math.abs(Minecraft.getInstance().player.getXRot() - ang[1]) < 0.5f
                && Minecraft.getInstance().player.position().distanceToSqr(cam[0], cam[1], cam[2]) < 0.5
                && Minecraft.getInstance().hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(furnace)
                && stepTicks >= 4,
                () -> "câmera em " + Minecraft.getInstance().player.position() + ", mira " + Minecraft.getInstance().hitResult);
    }
}
