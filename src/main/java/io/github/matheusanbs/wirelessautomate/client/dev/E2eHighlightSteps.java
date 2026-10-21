package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRangeSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.client.highlight.HighlightRenderer;
import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.menu.EditTargetRef;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * O destaque no mundo, no e2e do roteador de alcance (só em inglês): o Olho da fornalha na lista acende a máquina
 * ({@code range-8b-olho}, a lista com o Olho aceso e a linha das acesas), a tela fecha e a câmera olha a fornalha
 * atrás de uma parede de pedra ({@code range-8-no-mundo}, e nos gráficos Fabulous
 * {@code range-8c-no-mundo-fabulous}); de volta à tela, o Apagar todas apaga. No Tablet, o
 * "In world" do painel acende a fornalha ({@code 7k-tablet-no-mundo}) e o "Turn all off (1)" da Lista apaga. No
 * fim, a tela do roteador volta como estava: no mínimo, com a lista e a fornalha como alvo.
 */
final class E2eHighlightSteps {
    private E2eHighlightSteps() {
    }

    private static final int WIDE_W = 560;

    static void highlightSteps(List<Step> list, String label, UUID playerId) {
        String l = label + "destaque: ";
        EditTarget[] furnace = new EditTarget[1];
        list.add(new Step(l + "o Olho da fornalha acende", STEP_TIMEOUT_MS, () -> {
            HighlightRenderer.clear();
            furnace[0] = EditTarget.machine(rangeMachine(rangeFurnace));
            int[] eye = routerScreen().rangeEyeCenter(furnace[0]);
            if (eye == null) {
                throw new StepFailure("Olho da fornalha fora da vista");
            }
            click(routerScreen(), eye[0], eye[1]);
        }, () -> HighlightRenderer.isLit(Level.OVERWORLD, rangeFurnace) && HighlightRenderer.count() == 1
                && routerScreen().litClearCenter() != null,
                () -> "acesas " + HighlightRenderer.count()));
        list.add(new Step(l + "a lista larga com o Olho aceso", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(Math.min(WIDE_W, routerScreen().width - 8), 280),
                () -> routerScreen().rangeListShown() && routerScreen().rangeViewShown()
                        && routerScreen().rangeEyeCenter(furnace[0]) != null && stepTicks >= 4,
                () -> "lista " + routerScreen().rangeListShown()));
        list.add(rangeClipCheck(l + "lista com o Olho"));
        list.add(capture("range-8b-olho"));
        list.add(close(l + "fechar o roteador"));
        worldSteps(list, l, playerId);

        list.add(open(l + "abrir o roteador", () -> rangeRouter));
        list.add(new Step(l + "a lista no mínimo, com a linha das acesas", STEP_TIMEOUT_MS, () -> {
            routerScreen().previewResize(300, 240);
            if (!routerScreen().rangeListShown()) {
                click(widget(byMessageKey("gui.wirelessautomate.router.range.machines"), "botão Máquinas"));
            }
        }, () -> routerScreen().rangeListShown() && routerScreen().litClearCenter() != null && stepTicks >= 4,
                () -> "lista " + routerScreen().rangeListShown() + ", acesas " + HighlightRenderer.count()));
        list.add(new Step(l + "o Apagar todas apaga", STEP_TIMEOUT_MS, () -> {
            int[] c = routerScreen().litClearCenter();
            click(routerScreen(), c[0], c[1]);
        }, () -> HighlightRenderer.count() == 0 && routerScreen().litClearCenter() == null,
                () -> "acesas " + HighlightRenderer.count()));
        list.add(close(l + "fechar o roteador de novo"));

        tabletSteps(list, l, playerId);
        list.add(open(l + "abrir o roteador no fim", () -> rangeRouter));
        list.add(E2eRangeLabelSteps.backToMinimum(l, playerId));
    }

    /**
     * A câmera a oeste da fornalha, atrás de uma parede de pedra posta só nos lugares vazios (e tirada depois),
     * olhando a fornalha: o contorno e a linha até o roteador aparecem através da parede. A interface sai da
     * captura, e o jogador volta para onde estava.
     */
    private static void worldSteps(List<Step> list, String l, UUID playerId) {
        List<BlockPos> wall = new ArrayList<>();
        Vec3[] back = new Vec3[1];
        float[] backRot = new float[2];
        double[] cam = new double[3];
        float[] ang = new float[2];
        list.add(new Step(l + "parede e câmera", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            back[0] = minecraft.player.position();
            backRot[0] = minecraft.player.getYRot();
            backRot[1] = minecraft.player.getXRot();
            BlockPos f = rangeFurnace;
            cam[0] = f.getX() - 5.5;
            cam[1] = f.getY() + 0.4;
            cam[2] = f.getZ() + 1.5;
            double ex = f.getX() + 0.5 - cam[0];
            double ey = f.getY() + 0.9 - (cam[1] + 1.62);
            double ez = f.getZ() + 1.4 - cam[2];
            ang[0] = (float) Math.toDegrees(Math.atan2(-ex, ez));
            ang[1] = (float) -Math.toDegrees(Math.atan2(ey, Math.sqrt(ex * ex + ez * ez)));
            minecraft.player.setYRot(ang[0]);
            minecraft.player.setXRot(ang[1]);
            minecraft.player.yRotO = ang[0];
            minecraft.player.xRotO = ang[1];
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ServerLevel level = player.serverLevel();
                wall.clear();
                for (int y = 0; y <= 2; y++) {
                    for (int z = -1; z <= 4; z++) {
                        BlockPos pos = f.offset(-2, y, z);
                        if (level.getBlockState(pos).isAir()) {
                            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                            wall.add(pos);
                        }
                    }
                }
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(level, cam[0], cam[1], cam[2], ang[0], ang[1]);
                return null;
            });
            minecraft.options.hideGui = true;
        }, () -> Math.abs(Minecraft.getInstance().player.getXRot() - ang[1]) < 0.5f
                && Minecraft.getInstance().player.position().distanceToSqr(cam[0], cam[1], cam[2]) < 0.5
                && !wall.isEmpty() && Minecraft.getInstance().level.getBlockState(wall.getLast()).is(Blocks.STONE)
                && stepTicks >= 10,
                () -> "câmera em " + Minecraft.getInstance().player.position()));
        list.add(capture("range-8-no-mundo"));
        // o mesmo nos gráficos Fabulous, só nesta sessão (o options.txt não muda): o modo volta logo depois
        GraphicsStatus[] graphics = new GraphicsStatus[1];
        list.add(new Step(l + "gráficos Fabulous", STEP_TIMEOUT_MS, () -> {
            graphics[0] = Minecraft.getInstance().options.graphicsMode().get();
            Minecraft.getInstance().options.graphicsMode().set(GraphicsStatus.FABULOUS);
            // os alvos do Fabulous (nuvens, translúcidos) já no mesmo quadro: o céu do Ars Nouveau os usa
            Minecraft.getInstance().levelRenderer.allChanged();
        }, () -> Minecraft.getInstance().options.graphicsMode().get() == GraphicsStatus.FABULOUS && stepTicks >= 20,
                () -> "gráficos " + Minecraft.getInstance().options.graphicsMode().get()));
        list.add(capture("range-8c-no-mundo-fabulous"));
        list.add(new Step(l + "gráficos de volta", STEP_TIMEOUT_MS,
                () -> {
                    Minecraft.getInstance().options.graphicsMode().set(graphics[0]);
                    Minecraft.getInstance().levelRenderer.allChanged();
                },
                () -> Minecraft.getInstance().options.graphicsMode().get() == graphics[0] && stepTicks >= 4,
                () -> "gráficos " + Minecraft.getInstance().options.graphicsMode().get()));
        list.add(new Step(l + "tirar a parede e voltar", STEP_TIMEOUT_MS, () -> {
            Minecraft.getInstance().options.hideGui = false;
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ServerLevel level = player.serverLevel();
                for (BlockPos pos : wall) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.teleportTo(level, back[0].x, back[0].y, back[0].z, backRot[0], backRot[1]);
                return null;
            });
        }, () -> Minecraft.getInstance().player.position().distanceTo(back[0]) < 0.5
                && Minecraft.getInstance().level.getBlockState(wall.getLast()).isAir()
                && HighlightRenderer.isLit(Level.OVERWORLD, rangeFurnace),
                () -> "posição " + Minecraft.getInstance().player.position()));
    }

    /** O Tablet no nó do roteador, com a fornalha como alvo: o "In world" acende e o Apagar todas da Lista apaga. */
    private static void tabletSteps(List<Step> list, String l, UUID playerId) {
        list.add(new Step(l + "Tablet aberto no nó, a fornalha como alvo", STEP_TIMEOUT_MS,
                () -> onServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.NETWORK_TABLET.get()));
                    RouterBlockEntity router = router(server, rangeRouter);
                    NodeIndex.placedBy(router, playerId);
                    EditTarget target = EditTarget.machine(router.remoteMachines().index(rangeFurnace));
                    TabletMenu.open(player, new NodeIndex.NodeKey(Level.OVERWORLD, rangeRouter),
                            EditTargetRef.resolve(router, target, rangeFurnace, null));
                    return null;
                }), () -> {
                    if (!(Minecraft.getInstance().screen instanceof TabletScreen screen)) {
                        return false;
                    }
                    NodePanel panel = screen.nodePanel();
                    return panel != null && panel.highlightCenter() != null && stepTicks >= 4;
                }, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step(l + "Tablet: o In world acende a fornalha", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletScreen().nodePanel().highlightCenter();
            click(tabletScreen(), c[0], c[1]);
        }, () -> HighlightRenderer.isLit(Level.OVERWORLD, rangeFurnace)
                && find(byMessageKey("gui.wirelessautomate.highlight.clear_all.count")) != null && stepTicks >= 4,
                () -> "acesas " + HighlightRenderer.count()));
        // a Lista corta a segunda linha dos roteadores com "…" e a dica da linha traz o texto inteiro
        list.add(clipCheck(l + "Tablet"));
        list.add(capture("7k-tablet-no-mundo"));
        list.add(new Step(l + "Tablet: o Apagar todas apaga", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.highlight.clear_all.count"), "Apagar todas")),
                () -> HighlightRenderer.count() == 0
                        && find(byMessageKey("gui.wirelessautomate.highlight.clear_all.count")) == null,
                () -> "acesas " + HighlightRenderer.count()));
        list.add(close(l + "fechar o Tablet"));
        list.add(new Step(l + "o Tablet sai da mão", STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return null;
        }), () -> Minecraft.getInstance().player.getMainHandItem().isEmpty(), () -> "mão"));
    }
}
