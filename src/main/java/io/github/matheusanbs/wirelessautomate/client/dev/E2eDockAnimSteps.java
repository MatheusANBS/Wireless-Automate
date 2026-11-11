package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.FrameClock;
import io.github.matheusanbs.wirelessautomate.client.dock.render.DockClock;
import io.github.matheusanbs.wirelessautomate.client.dock.render.PatternDockRenderer;
import io.github.matheusanbs.wirelessautomate.dock.DockWork;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * A animação da Doca no mundo: uma Doca Elite no ar, o evento de trabalho do Leste que chega ao cliente e faz o laço
 * de trabalho tocar, e o tempo parado ({@link DockClock}) em cada fase da linha do tempo da spec, com a conferência de
 * quantas caixas o renderer pôs e a captura na câmera dos GIFs do Blockbench ({@code doca-anim-<laço>-<segundo>}),
 * mais o pacote pelo Norte. A suíte {@code doca-gif} grava os dois laços quadro a quadro pelo {@link E2eDockGif}
 * (montados em GIF fora do jogo) e a {@code doca-desempenho} mede o tempo de quadro com 50 Docas
 * à vista, sem e com o renderer. Tudo volta no fim (e em qualquer saída, pelo
 * {@link E2eResonatorSteps#restoreFrameSettings()}).
 */
final class E2eDockAnimSteps {
    /** As fases paradas: os segundos do laço de repouso e do de trabalho, e quantas caixas cada uma desenha. */
    static final double[] REST = {0, 1.5, 3, 4.5};
    static final double[] WORK = {0.2, 0.7, 1.25, 1.6, 2.0, 2.55, 3.0, 3.85, 4.45, 4.9, 5.6};
    private static final int[] WORK_BOXES = {15, 15, 13, 17, 17, 17, 13, 17, 13, 15, 15};
    /** Os três anéis em quatro caixas e o cristal. */
    private static final int GYRO_BOXES = 13;
    /**
     * A câmera dos GIFs do Blockbench (a partir do centro do bloco, com o campo de visão {@link #REF_FOV}): pelo
     * Sudeste, de frente para o Leste (o lado por onde o pacote entra), quase na altura do centro, de perto. Tirada
     * dos cantos do bloco no primeiro quadro da referência.
     */
    static final double[] REF_VIEW = {1.75, 0.14, 1.08};
    private static final double CENTER_Y = 0.5;
    private static final int LIFT = 3;
    /** Quadros desenhados com a fase nova antes da captura. */
    private static final int SETTLE_FRAMES = 3;
    /** As 50 Docas da medida: 10 × 5, de 2 em 2 blocos, à frente do jogador. */
    private static final int GRID_X = 10;
    private static final int GRID_Z = 5;
    private static final double[] GRID_VIEW = {0.5, 6, -10};

    private static final boolean[] SAVED = new boolean[2];
    /** O campo de visão da câmera do Blockbench (vertical, em graus), e o do jogo de antes. */
    private static final int REF_FOV = 39;
    private static int savedFov = -1;
    private static double savedFovEffect;
    private static final List<BlockPos> PLACED = new ArrayList<>();

    private E2eDockAnimSteps() {
    }

    /** As fases da animação, dentro da suíte {@code doca}. */
    static void animationSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] pos = new BlockPos[1];
        list.add(begin("Doca animada", playerId));
        list.add(place(playerId, pos));
        list.add(E2eRepairerSteps.cameraShot("doca-anim-vista", playerId, () -> pos[0], REF_VIEW, CENTER_Y));
        list.add(new Step("Doca animada: o repouso desenha só o giroscópio", STEP_TIMEOUT_MS, () -> {
        }, () -> stepTicks >= 10 && PatternDockRenderer.lastBoxesForTests == GYRO_BOXES,
                () -> PatternDockRenderer.lastBoxesForTests + " caixas"));
        list.add(new Step("Doca animada: o evento de trabalho do Leste chega ao cliente", STEP_TIMEOUT_MS,
                () -> onServer(server -> {
                    dock(server.overworld(), pos[0]).signalWork(Direction.EAST.ordinal(),
                            server.overworld().getGameTime());
                    return null;
                }), () -> client(pos) != null && client(pos).work().lastEvent() != DockWork.NEVER
                        && client(pos).work().side() == Direction.EAST.ordinal(),
                () -> "último evento " + (client(pos) == null ? "-" : client(pos).work().lastEvent())));
        list.add(new Step("Doca animada: o laço seguinte é de trabalho (pacote e fragmentos)", STEP_TIMEOUT_MS,
                () -> {
                }, () -> PatternDockRenderer.lastBoxesForTests > GYRO_BOXES,
                () -> PatternDockRenderer.lastBoxesForTests + " caixas"));
        for (double s : REST) {
            phase(list, playerId, pos, false, s, GYRO_BOXES);
        }
        for (int i = 0; i < WORK.length; i++) {
            phase(list, playerId, pos, true, WORK[i], WORK_BOXES[i]);
        }
        list.add(new Step("Doca animada: evento do Norte", STEP_TIMEOUT_MS, () -> {
            // direto no cliente: o servidor só manda outro evento depois de 100 ticks
            client(pos).work().event(Minecraft.getInstance().level.getGameTime(), Direction.NORTH.ordinal());
        }, () -> true, () -> ""));
        phase(list, playerId, pos, true, 0.7, 15, "doca-anim-norte-0.7", new double[] {1.08, 0.14, -1.75});
        list.add(end("Doca animada", playerId, pos));
    }

    /** Os dois GIFs (repouso e trabalho), quadro a quadro, na câmera da referência. */
    static void gifSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] pos = new BlockPos[1];
        list.add(close("Doca GIF: fechar a tela de antes"));
        list.add(begin("Doca GIF", playerId));
        list.add(place(playerId, pos));
        list.add(new Step("Doca GIF: evento do Leste", STEP_TIMEOUT_MS, () -> onServer(server -> {
            dock(server.overworld(), pos[0]).signalWork(Direction.EAST.ordinal(), server.overworld().getGameTime());
            return null;
        }), () -> client(pos) != null && client(pos).work().side() == Direction.EAST.ordinal()
                && client(pos).work().lastEvent() != DockWork.NEVER, () -> "evento no cliente"));
        list.add(freeze(false, 0));
        list.add(E2eRepairerSteps.cameraShot("doca-gif-camera", playerId, () -> pos[0], REF_VIEW, CENTER_Y));
        list.add(new Step("Doca GIF: o renderer desenha o giroscópio", STEP_TIMEOUT_MS, () -> {
        }, () -> stepTicks >= 4 && PatternDockRenderer.lastBoxesForTests == GYRO_BOXES,
                () -> PatternDockRenderer.lastBoxesForTests + " caixas"));
        float[] ang = new float[2];
        list.add(new Step("Doca GIF: o ângulo da câmera", STEP_TIMEOUT_MS,
                () -> aim(pos[0], REF_VIEW, new double[3], ang), () -> true, () -> ""));
        list.add(E2eDockGif.record(ang));
        list.add(end("Doca GIF", playerId, pos));
    }

    /**
     * O tempo de quadro (mede, não afirma): 50 Docas Elite no ar à vista, sem o renderer (como antes dele), com o
     * repouso andando, paradas no trabalho com o pacote e os fragmentos (1,6 s) e fora de vista. As linhas vão para o
     * result.txt com o prefixo {@code doca}.
     */
    static void perfSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] origin = new BlockPos[1];
        list.add(close("Doca desempenho: fechar a tela de antes"));
        list.add(begin("Doca desempenho", playerId));
        list.add(new Step("Doca desempenho: 50 Docas", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            origin[0] = server.getPlayerList().getPlayer(playerId).blockPosition().offset(-GRID_X + 1, LIFT + 1, 8);
            for (int x = 0; x < GRID_X; x++) {
                for (int z = 0; z < GRID_Z; z++) {
                    BlockPos at = origin[0].offset(2 * x, 0, 2 * z);
                    level.setBlockAndUpdate(at, ModBlocks.PATTERN_DOCK.get().defaultBlockState()
                            .setValue(RouterBlock.TIER, RouterTier.ELITE));
                    PLACED.add(at);
                }
            }
            return null;
        }), () -> stepTicks >= 20 && PLACED.stream().allMatch(at -> Minecraft.getInstance().level
                .getBlockEntity(at) instanceof PatternDockBlockEntity), () -> PLACED.size() + " Docas"));
        list.add(E2eRepairerSteps.cameraShot("doca-desempenho", playerId,
                () -> origin[0].offset(GRID_X - 1, 0, GRID_Z - 1), GRID_VIEW, CENTER_Y));
        list.add(measure("doca 50 sem renderer", () -> PatternDockRenderer.disabledForTests = true));
        list.add(measure("doca 50 repouso", () -> {
        }));
        list.add(new Step("Doca desempenho: trabalho parado em 1,6 s", STEP_TIMEOUT_MS, () -> {
            DockClock.freezeForTests = 1.6;
            DockClock.workForTests = true;
        }, () -> stepTicks >= 2, () -> ""));
        list.add(measure("doca 50 trabalho 1.6 s", () -> {
        }));
        list.add(measure("doca 50 trabalho 1.6 s sem renderer", () -> PatternDockRenderer.disabledForTests = true));
        float[] away = {(float) Math.toDegrees(Math.atan2(GRID_VIEW[0], -GRID_VIEW[2])) + 180, 0};
        list.add(E2eResonatorSteps.frameTimeStep("Doca: doca 50 fora de vista (tempo de quadro)",
                "doca 50 fora de vista", () -> {
                }, () -> E2eRepairerSteps.look(away)));
        list.add(end("Doca desempenho", playerId, null));
    }

    /** Solta o tempo e o renderer. Idempotente. */
    static void restoreHooks() {
        E2eDockGif.release();
        DockClock.release();
        PatternDockRenderer.disabledForTests = false;
        if (savedFov >= 0) {
            Minecraft.getInstance().options.fov().set(savedFov);
            Minecraft.getInstance().options.fovEffectScale().set(savedFovEffect);
            savedFov = -1;
        }
    }

    private static Step measure(String label, Runnable setup) {
        return E2eResonatorSteps.frameTimeStep("Doca: " + label + " (tempo de quadro)", label, setup, null);
    }

    private static void phase(List<Step> list, UUID playerId, BlockPos[] pos, boolean work, double s, int boxes) {
        String name = String.format(Locale.ROOT, "doca-anim-%s-%s", work ? "trabalho" : "repouso", seconds(s));
        phase(list, playerId, pos, work, s, boxes, name, REF_VIEW);
    }

    private static void phase(List<Step> list, UUID playerId, BlockPos[] pos, boolean work, double s, int boxes,
            String name, double[] view) {
        list.add(freeze(work, s));
        list.add(E2eRepairerSteps.cameraShot(name, playerId, () -> pos[0], view, CENTER_Y));
        long[] start = new long[1];
        list.add(new Step(name + ": " + boxes + " caixas", STEP_TIMEOUT_MS,
                () -> start[0] = FrameClock.frame() + SETTLE_FRAMES, () -> {
                    if (FrameClock.frame() < start[0]) {
                        return false;
                    }
                    int drawn = PatternDockRenderer.lastBoxesForTests;
                    if (drawn != boxes) {
                        throw new StepFailure(name + ": o renderer pôs " + drawn + " caixas, o esperado é " + boxes);
                    }
                    return true;
                }, () -> PatternDockRenderer.lastBoxesForTests + " caixas"));
    }

    private static String seconds(double s) {
        return String.format(Locale.ROOT, "%.2f", s).replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }

    private static Step freeze(boolean work, double s) {
        return new Step("Doca: tempo parado em " + s + " s do " + (work ? "trabalho" : "repouso"), STEP_TIMEOUT_MS,
                () -> {
                    DockClock.freezeForTests = s;
                    DockClock.workForTests = work;
                }, () -> stepTicks >= 2, () -> "");
    }

    /** A posição dos pés e o ângulo do jogador que olha o centro do bloco de {@code offset}, como a cameraShot. */
    private static void aim(BlockPos block, double[] offset, double[] cam, float[] ang) {
        cam[0] = block.getX() + 0.5 + offset[0];
        cam[1] = block.getY() + CENTER_Y + offset[1] - E2eRepairerSteps.EYE_HEIGHT;
        cam[2] = block.getZ() + 0.5 + offset[2];
        ang[0] = (float) Math.toDegrees(Math.atan2(offset[0], -offset[2]));
        ang[1] = (float) Math.toDegrees(Math.atan2(offset[1], Math.sqrt(offset[0] * offset[0]
                + offset[2] * offset[2])));
    }

    /** Sem a interface e voando; guarda o que havia. */
    private static Step begin(String name, UUID playerId) {
        return new Step(name + ": sem interface", STEP_TIMEOUT_MS, () -> {
            SAVED[0] = Minecraft.getInstance().options.hideGui;
            SAVED[1] = onServer(server -> server.getPlayerList().getPlayer(playerId).getAbilities().flying);
            Minecraft.getInstance().options.hideGui = true;
            var options = Minecraft.getInstance().options;
            if (savedFov < 0) {
                savedFov = options.fov().get();
                savedFovEffect = options.fovEffectScale().get();
            }
            options.fov().set(REF_FOV);
            options.fovEffectScale().set(0.0);
        }, () -> true, () -> "");
    }

    private static Step end(String name, UUID playerId, BlockPos[] pos) {
        return new Step(name + ": blocos fora e tudo de volta", STEP_TIMEOUT_MS, () -> {
            restoreHooks();
            Minecraft.getInstance().options.hideGui = SAVED[0];
            onServer(server -> {
                ServerLevel level = server.overworld();
                if (pos != null && pos[0] != null) {
                    level.setBlockAndUpdate(pos[0], Blocks.AIR.defaultBlockState());
                }
                for (BlockPos at : PLACED) {
                    level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
                }
                PLACED.clear();
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = SAVED[1];
                player.onUpdateAbilities();
                return null;
            });
        }, () -> pos == null || pos[0] == null || Minecraft.getInstance().level.getBlockState(pos[0]).isAir(),
                () -> "bloco no cliente");
    }

    /** Uma Doca Elite nova no ar, a 3 blocos a oeste do jogador (a posição fica na primeira vez). */
    private static Step place(UUID playerId, BlockPos[] pos) {
        return new Step("Doca animada: bloco novo", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (pos[0] == null) {
                pos[0] = player.blockPosition().offset(-3, LIFT, 0);
            }
            player.serverLevel().setBlockAndUpdate(pos[0], ModBlocks.PATTERN_DOCK.get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.ELITE));
            return null;
        }), () -> client(pos) != null, () -> "Doca no cliente");
    }

    private static PatternDockBlockEntity dock(ServerLevel level, BlockPos pos) {
        return (PatternDockBlockEntity) level.getBlockEntity(pos);
    }

    private static PatternDockBlockEntity client(BlockPos[] pos) {
        return pos[0] != null && Minecraft.getInstance().level.getBlockEntity(pos[0]) instanceof PatternDockBlockEntity
                dock ? dock : null;
    }
}
