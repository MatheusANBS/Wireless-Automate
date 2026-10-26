package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRepairerSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.FrameClock;
import io.github.matheusanbs.wirelessautomate.client.repairer.render.RepairClock;
import io.github.matheusanbs.wirelessautomate.client.repairer.render.RepairerRenderer;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlock;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlockEntity;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerView;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * A animação do Reparador no mundo, só o efeito: um Reparador no ar com as 8 linhas andando (os rachados na ordem do
 * mockup), o tempo parado em cada fase da linha do tempo pelo {@link RepairClock#freezeForTests}, e de cada fase a
 * conferência de quantos cristais e quads o BER pôs ({@link #EXPECTED}) e as duas capturas, de cima e rente ao vidro.
 * Depois os estados: parado, parado com rachados esperando e sem energia. O {@code repairTicks} vai a 1.000.000 para as
 * linhas não terminarem durante a suíte e volta no fim, como a interface, o voo e os ganchos (também em qualquer saída,
 * pelo {@link E2eResonatorSteps#restoreFrameSettings()}).
 */
final class E2eRepairerAnimSteps {
    /** As fases da referência de 7 s, com o segundo parado de cada uma (o mockup tira o mesmo quadro). */
    static final String[] PHASES = {"puxa", "orbita", "raio", "solda", "fecha", "converge", "clarao", "onda"};
    static final double[] SECONDS = {0.25, 0.8, 2.0, 3.5, 5.0, 5.9, 6.45, 6.75};
    /** Quantos cristais e quads o BER põe em cada fase com as 8 linhas andando. */
    static final int[] EXPECTED = {25, 33, 41, 41, 41, 41, 33, 33};
    private static final long ANIM_REPAIR_TICKS = 1_000_000;
    private static final long ENERGY = 1_000_000;
    private static final long IDLE_ENERGY = 1_000;
    private static final int FEED = 64;
    /** O bloco fica no ar, para a câmera de cima e a rente ao vidro não entrarem no chão. */
    private static final int LIFT = 3;
    /**
     * A câmera de frente, um pouco acima do centro do vidro e a 1 bloco dele, fora do eixo do montante do meio: mais
     * longe (ou de cima), o teto e os montantes escondem a câmara.
     */
    private static final double[] HIGH_VIEW = {0.35, 0.3, -0.95};
    private static final double[] SIDE_VIEW = {GRAZE_DISTANCE, 0, 0.15};
    private static final int COUNT_FRAMES = 5;

    /** O {@code repairTicks} de antes da suíte, ou nulo quando não há nada a devolver. */
    private static Long savedRepairTicks;
    private static final boolean[] SAVED = new boolean[2];

    private E2eRepairerAnimSteps() {
    }

    static void animationSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] pos = new BlockPos[1];
        list.add(close("Reparador animado: fechar a tela de antes"));
        list.add(begin(playerId));
        list.add(place(playerId, pos));
        list.add(eightLines(pos));
        for (int phase = 0; phase < PHASES.length; phase++) {
            phaseSteps(list, playerId, pos, PHASES[phase], SECONDS[phase], EXPECTED[phase]);
        }
        list.add(new Step("Reparador animado: tempo de volta", STEP_TIMEOUT_MS,
                () -> RepairClock.freezeForTests = -1, () -> true, () -> ""));
        list.add(idle(playerId, pos));
        stateShots(list, playerId, pos, "parado", 1);
        list.add(serverStep("Reparador animado: 3 rachados sem estoque", pos, repairer -> {
            putCracked(repairer, 3);
            repairer.itemsChanged();
        }, () -> clientRepairer(pos) != null && clientRepairer(pos).clientView().kind(2) != RepairerView.NONE));
        stateShots(list, playerId, pos, "parado-rachados", 4);
        list.add(serverStep("Reparador animado: sem energia", pos,
                repairer -> repairer.energy().extract(repairer.energy().stored(), false),
                () -> !Minecraft.getInstance().level.getBlockState(pos[0]).getValue(RepairerBlock.LIT)));
        stateShots(list, playerId, pos, "sem-energia", 3);
        list.add(serverStep("Reparador animado: sem os rachados", pos, repairer -> {
            for (int line = 0; line < 3; line++) {
                repairer.items().set(line, ItemStack.EMPTY);
            }
            repairer.itemsChanged();
        }, () -> clientRepairer(pos) != null && clientRepairer(pos).clientView().empty()));
        list.add(count("sem rachados", 0));
        list.add(end(playerId, pos));
    }

    /**
     * O tempo de quadro com o Reparador (mede, não afirma): sem ele, com as 8 linhas em duas fases, com o desenho
     * antigo forçado, fora de vista e parado com 3 rachados. As linhas vão para o result.txt com o prefixo
     * {@code reparador}.
     */
    static void perfSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] pos = new BlockPos[1];
        list.add(close("Reparador desempenho: fechar a tela de antes"));
        list.add(begin(playerId));
        list.add(new Step("Reparador desempenho: posição", STEP_TIMEOUT_MS, () -> onServer(server -> {
            pos[0] = server.getPlayerList().getPlayer(playerId).blockPosition().offset(-3, LIFT, 0);
            return null;
        }), () -> true, () -> ""));
        list.add(cameraShot("reparador-perf-sem-bloco", playerId, () -> pos[0], HIGH_VIEW, GLASS_CENTER_Y));
        list.add(measure("reparador sem bloco", () -> {
        }, null));
        list.add(place(playerId, pos));
        list.add(eightLines(pos));
        list.add(freeze(3.5));
        list.add(cameraShot("reparador-perf-perto", playerId, () -> pos[0], HIGH_VIEW, GLASS_CENTER_Y));
        list.add(measure("reparador 8 linhas 3.5 s", () -> {
        }, null));
        list.add(freeze(5.9));
        list.add(measure("reparador 8 linhas 5.9 s", () -> {
        }, null));
        list.add(freeze(3.5));
        list.add(measure("reparador antigo 8 linhas 3.5 s", () -> RepairerRenderer.forceLegacyForTests = true, null));
        float[] away = {(float) Math.toDegrees(Math.atan2(HIGH_VIEW[0], -HIGH_VIEW[2])) + 180, 0};
        list.add(measure("reparador fora de vista", () -> {
        }, () -> look(away)));
        list.add(new Step("Reparador desempenho: tempo de volta", STEP_TIMEOUT_MS,
                () -> RepairClock.freezeForTests = -1, () -> true, () -> ""));
        list.add(idle(playerId, pos));
        list.add(serverStep("Reparador desempenho: 3 rachados sem estoque", pos, repairer -> {
            putCracked(repairer, 3);
            repairer.itemsChanged();
        }, () -> clientRepairer(pos) != null && clientRepairer(pos).clientView().kind(2) != RepairerView.NONE));
        list.add(cameraShot("reparador-perf-parado", playerId, () -> pos[0], HIGH_VIEW, GLASS_CENTER_Y));
        list.add(measure("reparador parado 3 rachados", () -> {
        }, null));
        list.add(end(playerId, pos));
    }

    /** Devolve o tempo, o desenho forçado e o {@code repairTicks}. Idempotente. */
    static void restoreHooks() {
        RepairClock.freezeForTests = -1;
        RepairerRenderer.forceLegacyForTests = false;
        if (savedRepairTicks != null) {
            Config.REPAIR_TICKS.set(savedRepairTicks);
            savedRepairTicks = null;
        }
    }

    private static void phaseSteps(List<Step> list, UUID playerId, BlockPos[] pos, String phase, double seconds,
            int expected) {
        list.add(freeze(seconds));
        list.add(cameraShot("reparador-anim-" + phase + "-alto", playerId, () -> pos[0], HIGH_VIEW, GLASS_CENTER_Y));
        list.add(count(phase, expected));
        list.add(cameraShot("reparador-anim-" + phase + "-lado", playerId, () -> pos[0], SIDE_VIEW, GLASS_CENTER_Y));
    }

    private static void stateShots(List<Step> list, UUID playerId, BlockPos[] pos, String state, int expected) {
        list.add(cameraShot("reparador-anim-" + state + "-alto", playerId, () -> pos[0], HIGH_VIEW, GLASS_CENTER_Y));
        list.add(count(state, expected));
        list.add(cameraShot("reparador-anim-" + state + "-lado", playerId, () -> pos[0], SIDE_VIEW, GLASS_CENTER_Y));
    }

    private static Step freeze(double seconds) {
        return new Step("Reparador animado: tempo parado em " + seconds + " s", STEP_TIMEOUT_MS,
                () -> RepairClock.freezeForTests = seconds, () -> stepTicks >= 2, () -> "");
    }

    /** Alguns quadros depois, confere quantos cristais e quads o último {@code render} do Reparador pôs. */
    private static Step count(String name, int expected) {
        long[] start = new long[1];
        return new Step("Reparador animado: " + name + " desenha " + expected, STEP_TIMEOUT_MS,
                () -> start[0] = FrameClock.frame() + COUNT_FRAMES, () -> {
                    if (FrameClock.frame() < start[0]) {
                        return false;
                    }
                    int drawn = RepairerRenderer.lastPrimitivesForTests;
                    if (drawn != expected) {
                        throw new StepFailure(name + ": o BER pôs " + drawn + ", o esperado é " + expected);
                    }
                    return true;
                }, () -> RepairerRenderer.lastPrimitivesForTests + " primitivas");
    }

    private static Step measure(String label, Runnable setup, E2eResonatorSteps.Tick perTick) {
        return E2eResonatorSteps.frameTimeStep("Reparador: " + label + " (tempo de quadro)", label, setup, perTick);
    }

    /** Sem a interface, voando e com o {@code repairTicks} longo; guarda o que havia. */
    private static Step begin(UUID playerId) {
        return new Step("Reparador animado: sem interface e reparo longo", STEP_TIMEOUT_MS, () -> {
            SAVED[0] = Minecraft.getInstance().options.hideGui;
            SAVED[1] = onServer(server -> server.getPlayerList().getPlayer(playerId).getAbilities().flying);
            Minecraft.getInstance().options.hideGui = true;
            onServer(server -> {
                if (savedRepairTicks == null) {
                    savedRepairTicks = Config.REPAIR_TICKS.get();
                }
                Config.REPAIR_TICKS.set(ANIM_REPAIR_TICKS);
                return null;
            });
        }, () -> Config.repairTicks() == ANIM_REPAIR_TICKS, () -> "repairTicks " + Config.repairTicks());
    }

    private static Step end(UUID playerId, BlockPos[] pos) {
        return new Step("Reparador animado: bloco fora e tudo de volta", STEP_TIMEOUT_MS, () -> {
            restoreHooks();
            Minecraft.getInstance().options.hideGui = SAVED[0];
            onServer(server -> {
                remove(server.overworld(), pos[0]);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = SAVED[1];
                player.onUpdateAbilities();
                return null;
            });
        }, () -> Minecraft.getInstance().level.getBlockState(pos[0]).isAir()
                && Minecraft.getInstance().player.getAbilities().flying == SAVED[1], () -> "bloco no cliente");
    }

    /** Um Reparador novo, para o norte, no ar a 3 blocos a oeste do jogador (a posição fica na primeira vez). */
    private static Step place(UUID playerId, BlockPos[] pos) {
        return new Step("Reparador animado: bloco novo", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            if (pos[0] == null) {
                pos[0] = player.blockPosition().offset(-3, LIFT, 0);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            remove(level, pos[0]);
            level.setBlockAndUpdate(pos[0], ModBlocks.CRYSTAL_REPAIRER.get().defaultBlockState()
                    .setValue(RepairerBlock.FACING, Direction.NORTH));
            return null;
        }), () -> clientRepairer(pos) != null
                && onServer(server -> server.overworld().getBlockEntity(pos[0]) instanceof RepairerBlockEntity),
                () -> "Reparador no cliente");
    }

    /** Os 8 rachados na ordem do mockup, o estoque e a energia: as 8 linhas andam no cliente. */
    private static Step eightLines(BlockPos[] pos) {
        return serverStep("Reparador animado: 8 linhas andando", pos, repairer -> {
            putCracked(repairer, RepairMath.LINES);
            repairer.items().set(RepairerSlots.FEED, new ItemStack(ModItems.SIGNAL_CRYSTAL.get(), FEED));
            repairer.itemsChanged();
            repairer.bulkEnergyView().insert(ENERGY, false);
        }, () -> {
            RepairerBlockEntity client = clientRepairer(pos);
            if (client == null) {
                return false;
            }
            for (int line = 0; line < RepairMath.LINES; line++) {
                if (!client.clientView().running(line)) {
                    return false;
                }
            }
            return true;
        });
    }

    /** O bloco trocado por um novo só com energia: aceso, sem nada andando. */
    private static Step idle(UUID playerId, BlockPos[] pos) {
        Step fresh = place(playerId, pos);
        return new Step("Reparador animado: parado", STEP_TIMEOUT_MS, () -> {
            fresh.action().run();
        }, () -> {
            if (!fresh.check().done()) {
                return false;
            }
            onServer(server -> {
                RepairerBlockEntity repairer = repairer(server, pos[0]);
                if (repairer.energy().stored() == 0) {
                    repairer.bulkEnergyView().insert(IDLE_ENERGY, false);
                }
                return null;
            });
            return Minecraft.getInstance().level.getBlockState(pos[0]).getValue(RepairerBlock.LIT)
                    && clientRepairer(pos).clientView().empty();
        }, () -> "estado " + Minecraft.getInstance().level.getBlockState(pos[0]));
    }

    /** Os rachados nas primeiras {@code count} linhas, na ordem do mockup. */
    private static void putCracked(RepairerBlockEntity repairer, int count) {
        for (int line = 0; line < count; line++) {
            repairer.items().set(line, E2eResonatorScreenSteps.cracked(new ItemStack(order(line))));
        }
    }

    private static net.minecraft.world.level.ItemLike order(int line) {
        return switch (line) {
            case 1, 5 -> ModItems.CUT_SIGNAL_CRYSTAL.get();
            case 2, 7 -> ModItems.TUNED_CRYSTALS.get(RouterTier.ELITE).get();
            case 4 -> ModItems.TUNED_CRYSTALS.get(RouterTier.EMERALD).get();
            default -> ModItems.SIGNAL_CRYSTAL.get();
        };
    }

    @FunctionalInterface
    private interface ServerAction {
        void run(RepairerBlockEntity repairer);
    }

    private static Step serverStep(String name, BlockPos[] pos, ServerAction action, Check ready) {
        return new Step(name, STEP_TIMEOUT_MS, () -> onServer(server -> {
            action.run(repairer(server, pos[0]));
            return null;
        }), ready, () -> "vista " + describeView(pos));
    }

    private static RepairerBlockEntity clientRepairer(BlockPos[] pos) {
        return pos[0] != null && Minecraft.getInstance().level.getBlockEntity(pos[0]) instanceof RepairerBlockEntity
                repairer ? repairer : null;
    }

    private static String describeView(BlockPos[] pos) {
        RepairerBlockEntity client = clientRepairer(pos);
        if (client == null) {
            return "sem o bloco no cliente";
        }
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < RepairMath.LINES; line++) {
            text.append(client.clientView().kind(line)).append(client.clientView().running(line) ? "+ " : " ");
        }
        return text.toString();
    }
}
