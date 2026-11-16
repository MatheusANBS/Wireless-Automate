package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eResonatorSteps.core;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.fx.FxLightPass;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.ArmilarRenderer;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.resonator.TuneSignal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * As capturas da Armilar (a câmara do Ressonador) e as suítes que dependem do render dela, todas com a câmera do
 * filme da formada ({@link #FILM_FEET}: alvo a 30 px sobre o centro do chão, yaw 38°, pitch 24°, distância 215 px,
 * FOV 30) e o relógio parado em cada fase ({@code ArmilarRenderer.freezeSecondsForTests}): a luz da conjunção em cada
 * fase com a vazão 7 e a 0, a sintonia, o buffer cheio, os cinco estados dos cristais, a luz no mundo à meia-noite, a
 * mão perto do sol, a recarga, o rachado e o instante da montagem. De cada fase o passo confere quantos sprites e
 * fitas a Armilar pediu (a suíte falha se um desenho sumir). O FOV, a hora do mundo, os gráficos e os ganchos do
 * render voltam ao que eram no fim e em qualquer saída ({@link #restoreHooks()}), sem gravar o options.txt.
 * <p>
 * Também a medida do tempo de quadro com 0, 1 e 10 Armilares ({@link #perfSteps}) e a rodada com os gráficos Fabulous
 * ({@link #fabulousSteps}).
 */
final class E2eArmilarRenderSteps {
    /** O {@code d} (segundos desde a conjunção) de cada fase: a preparação, o estouro, o arco, o impacto e a dissolução. */
    private static final double[] PHASE_D = {-0.3, 0.1, 0.35, 0.9, 2.0};
    private static final String[] PHASE_NAME = {"preparacao", "estouro", "arco", "impacto", "dissolucao"};
    private static final int ARCO = 2;
    private static final int IMPACTO = 3;
    /** O {@code TC} do filme: a conjunção de referência, em s. */
    private static final double TC = 11.0;
    /** Fora da janela da conjunção ({@code d = 2,6 s}): só o sol e os filamentos. */
    private static final double QUIET = TC + 2.6;
    /** Os ticks com o relógio parado até a captura (o {@code motion} trava na conjunção). */
    private static final int FREEZE_TICKS = 40;
    /** Os ticks para a vazão suavizada chegar à faixa forçada (a faixa anda 12% por tick). */
    private static final int FLOW_SETTLE_TICKS = 100;
    /** Os ticks para a montagem acabar e o {@code u} chegar a 1 depois de sintonizar. */
    private static final int ASSEMBLY_TICKS = 160;
    /** A câmara do Opaco da recarga: a última das três do Núcleo Elite. */
    private static final int RECHARGE_CHAMBER = 2;
    private static final int FLASH_DELAY_TICKS = 4;
    /** O {@code a ≈ 0,5} da montagem: {@code FORM_SECONDS = 2,5 s} × 0,5 = 1,25 s = 25 ticks desde o {@code formedAt}. */
    private static final int HALF_ASSEMBLY_TICKS = 25;
    /** A meia-noite do mundo, para a luz da Armilar aparecer. */
    private static final long MIDNIGHT = 18000;
    /** O {@code d} de cada captura da luz: o sol sozinho, o clarão do horizonte (cheio em 0,2) e a Porta (cheia em 0,9). */
    private static final double[] LIGHT_D = {-3.0, 0.2, 0.9};
    private static final String[] LIGHT_NAME = {"sol", "horizonte", "porta"};
    /** O segundo Ressonador (Ultimate, 8 nós) fica a oeste do primeiro. */
    private static final int ULTIMATE_APART = 24;

    /** A câmera do filme da formada: os ângulos, a distância e o FOV do filme. */
    private static final double FILM_YAW = 38;
    private static final double FILM_PITCH = 24;
    private static final double FILM_DISTANCE = 215.0 / 16;
    private static final int FILM_FOV = 30;
    private static final double EYE_HEIGHT = 1.62;
    /** O alvo, relativo ao canto mínimo da caixa: o centro do chão da Armilar, 30 px acima. */
    static final double[] FILM_TARGET = {2.5, 30.0 / 16, 2.5};
    /**
     * Os pés do jogador na câmera do filme: o olho em {@code alvo + (d cos p sin y; d sin p; d cos p cos y)} e os pés
     * 1,62 abaixo. O olhar sai do {@code camera} do e2e, com {@code yaw = atan2(−dx, dz)} e {@code pitch =
     * −atan2(dy, hypot(dx, dz))}.
     */
    static final double[] FILM_FEET = {
            FILM_TARGET[0] + FILM_DISTANCE * Math.cos(Math.toRadians(FILM_PITCH)) * Math.sin(Math.toRadians(FILM_YAW)),
            FILM_TARGET[1] + FILM_DISTANCE * Math.sin(Math.toRadians(FILM_PITCH)) - EYE_HEIGHT,
            FILM_TARGET[2] + FILM_DISTANCE * Math.cos(Math.toRadians(FILM_PITCH)) * Math.cos(Math.toRadians(FILM_YAW))};
    /** O sol, para a mão da captura {@code armilar-mao} (perto dele). */
    private static final double[] SUN_TARGET = {2.5, 47.5 / 16, 2.5};

    /** As Armilares da medida de desempenho: 10 num anel, com 14 blocos entre uma e a seguinte. */
    private static final int RING = 10;
    private static final double RING_SPACING = 14;
    /** A câmera de cima do anel: alta o bastante para as 10 entrarem no quadro, e ainda a menos de 48 blocos do sol delas. */
    private static final double[] RING_VIEW = {2.5, 40.4, 2.5};
    private static final double[] RING_TARGET = {2.5, 0.0, 2.5};

    /** O FOV, a hora e os gráficos de antes das capturas; nulos quando não há nada a devolver. */
    private static Integer savedFov;
    private static Long savedDayTime;
    private static GraphicsStatus savedGraphics;

    private E2eArmilarRenderSteps() {
    }

    /** O que se espera da contagem de uma fase; lança {@link StepFailure} se algo sumiu. */
    @FunctionalInterface
    private interface CountRule {
        void check(int sprites, int ribbons) throws StepFailure;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // a suíte ressonador: as capturas da câmara
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * As capturas da Armilar na suíte {@code ressonador}: a Armilar formada (Elite) já está em {@code origin}; o
     * Ultimate dos cristais é montado ao lado e sai no fim. Nada de relógio parado, vazão forçada ou FOV de 30 fica
     * depois do último passo.
     */
    static void chamberSteps(List<Step> list, UUID playerId, BlockPos[] origin) {
        int[] counts = new int[2];
        // [0] as fitas da sintonia; [1] e [2] as fitas e os sprites do arco com a vazão 7
        int[] ref = new int[3];
        list.add(new Step("Armilar: sem a mão, FOV do filme e o Núcleo fora de sintonia", STEP_TIMEOUT_MS, () -> {
            filmOptions();
            releaseClock();
            onServer(server -> {
                ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                        .getBlockEntity(core(origin[0]));
                // o ponto de partida das contagens: três cristais vivos e sem redstone (nenhum Opaco recarregando
                // nem terminando de recarregar no meio das fases, que mudaria quantas fitas há)
                for (int i = 0; i < core.chambers(); i++) {
                    core.setCrystalAt(i, new ItemStack(i % 2 == 0 ? ModItems.SIGNAL_CRYSTAL.get()
                            : ModItems.CUT_SIGNAL_CRYSTAL.get()));
                }
                core.setRedstone(0);
                core.setAuto(false);
                core.setStationForTest(98.0);
                core.setDial(96.7);
                return null;
            });
        }, () -> view(origin[0]).band() < TuneSignal.BANDS - 1 && view(origin[0]).rechargeStart() < 0
                && view(origin[0]).chambers().length == 3 && view(origin[0]).chambers()[2] != 0
                && view(origin[0]).chambers()[2] < 3, () -> "faixa no cliente, view " + view(origin[0])));
        list.add(film("Armilar: câmera do filme", playerId, () -> origin[0]));
        list.add(DevEndToEnd.wait("a pose assentar (fora de sintonia)", 60));
        list.add(capture("armilar-fora"));
        list.add(new Step("Armilar: dial a 98,0 e a vazão 7", STEP_TIMEOUT_MS, () -> {
            ArmilarRenderer.forceBandForTests = 7;
            onServer(server -> {
                ((ResonanceCoreBlockEntity) server.overworld().getBlockEntity(core(origin[0]))).setDial(98.0);
                return null;
            });
        }, () -> view(origin[0]).tuned() && stepTicks >= ASSEMBLY_TICKS, () -> "em sintonia no cliente"));
        list.add(shotAt("sintonia", QUIET, () -> origin[0], counts, (sprites, ribbons) -> {
            if (sprites < 1 || ribbons < 2) {
                throw new StepFailure("o sol e os filamentos sumiram: " + sprites + " sprites, " + ribbons + " fitas");
            }
            ref[0] = ribbons;
        }));
        list.add(capture("armilar-sintonia"));
        phases(list, "armilar", () -> origin[0], counts, ref, 0, 1, 2, 3, 4);
        list.add(new Step("Armilar: vazão 0", STEP_TIMEOUT_MS, () -> ArmilarRenderer.forceBandForTests = 0,
                () -> stepTicks >= FLOW_SETTLE_TICKS, () -> "faixa forçada 0"));
        list.add(shotAt("vazão 0 no arco", TC + PHASE_D[ARCO], () -> origin[0], counts, (sprites, ribbons) -> {
            if (ribbons < ref[0] || ribbons >= ref[1] || sprites >= ref[2]) {
                throw new StepFailure("com a vazão 0 o arco não sai: esperava menos de " + ref[1] + " fitas e "
                        + ref[2] + " sprites (e ao menos " + ref[0] + " fitas), veio " + ribbons + " e " + sprites);
            }
        }));
        list.add(capture("armilar-conj-vazao0"));
        list.add(new Step("Armilar: relógio e vazão soltos", STEP_TIMEOUT_MS, E2eArmilarRenderSteps::releaseClock,
                () -> true, () -> "relógio do mundo"));
        fullSteps(list, origin);
        crystalSteps(list, playerId, origin, counts);
        lightSteps(list, playerId, "armilar", () -> origin[0], counts);
        handSteps(list, playerId, "armilar", () -> origin[0]);
        rechargeSteps(list, origin);
        list.add(film("Armilar: câmera do filme, rachado", playerId, () -> origin[0]));
        E2eResonatorScreenSteps.crackedChamberSteps(list, origin);
        assemblySteps(list, origin);
        list.add(new Step("Armilar: opções e ganchos de volta", STEP_TIMEOUT_MS, () -> {
            restoreHooks();
            Minecraft.getInstance().options.hideGui = false;
        }, () -> true, () -> "FOV " + Minecraft.getInstance().options.fov().get()));
    }

    /** O FOV do filme e sem a mão e a mira na frente da câmara; guarda o FOV de antes. */
    private static void filmOptions() {
        var options = Minecraft.getInstance().options;
        if (savedFov == null) {
            savedFov = options.fov().get();
        }
        options.fov().set(FILM_FOV);
        options.hideGui = true;
    }

    private static Step film(String name, UUID playerId, Supplier<BlockPos> origin) {
        return E2eResonatorSteps.camera(name, playerId, origin, FILM_FEET, FILM_TARGET);
    }

    /**
     * O relógio parado em {@code seconds} por {@link #FREEZE_TICKS} ticks (o {@code motion} trava na conjunção) e a
     * conferência do que a Armilar do Núcleo de {@code origin} pediu no último quadro.
     */
    private static Step shotAt(String label, double seconds, Supplier<BlockPos> origin, int[] counts,
            CountRule rule) {
        return new Step("Armilar: " + label + ", relógio em " + seconds, STEP_TIMEOUT_MS,
                () -> ArmilarRenderer.freezeSecondsForTests = seconds, () -> {
                    if (stepTicks < FREEZE_TICKS) {
                        return false;
                    }
                    if (!ArmilarRenderer.countsForTests(core(origin.get()), counts)) {
                        throw new StepFailure(label + ": a Armilar não foi desenhada");
                    }
                    if (rule != null) {
                        rule.check(counts[0], counts[1]);
                    }
                    return true;
                }, () -> label + ": " + counts[0] + " sprites, " + counts[1] + " fitas");
    }

    /**
     * As fases da conjunção com a vazão 7 já forçada: o relógio parado em {@code TC + d}, a conferência e uma captura
     * por fase ({@code <prefixo>-conj-<fase>}). {@code ref} guarda as fitas da sintonia ({@code [0]}) e, no arco, as
     * fitas e os sprites ({@code [1]}, {@code [2]}): as fases do estouro à dissolução têm de ter mais fitas que a
     * sintonia (o raio do sol ao horizonte e o arco), e a preparação ao menos as dela.
     */
    private static void phases(List<Step> list, String prefix, Supplier<BlockPos> origin, int[] counts, int[] ref,
            int... which) {
        for (int i : which) {
            int phase = i;
            list.add(shotAt("conjunção, " + PHASE_NAME[phase], TC + PHASE_D[phase], origin, counts,
                    (sprites, ribbons) -> {
                        if (sprites < 1 || ribbons < ref[0]) {
                            throw new StepFailure(PHASE_NAME[phase] + ": " + sprites + " sprites e " + ribbons
                                    + " fitas, menos que a sintonia (" + ref[0] + " fitas)");
                        }
                        if (phase >= 1 && ribbons <= ref[0]) {
                            throw new StepFailure(PHASE_NAME[phase] + ": o raio e o arco sumiram (" + ribbons
                                    + " fitas, a sintonia tem " + ref[0] + ")");
                        }
                        if (phase == ARCO) {
                            ref[1] = ribbons;
                            ref[2] = sprites;
                        }
                    }));
            list.add(capture(prefix + "-conj-" + PHASE_NAME[phase]));
        }
    }

    /**
     * O buffer cheio: três cristais vivos (nenhum Opaco, para nada recarregar e misturar os estados), a redstone no
     * máximo e a estação no dial, sem ninguém tirar energia; o sol sob pressão. Relógio e vazão reais.
     */
    private static void fullSteps(List<Step> list, BlockPos[] origin) {
        list.add(new Step("Armilar: buffer cheio, com cristais vivos", 4 * STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                    .getBlockEntity(core(origin[0]));
            for (int i = 0; i < core.chambers(); i++) {
                core.setCrystalAt(i, new ItemStack(i % 2 == 0 ? ModItems.SIGNAL_CRYSTAL.get()
                        : ModItems.CUT_SIGNAL_CRYSTAL.get()));
            }
            core.setRedstone(ResonanceCoreBlockEntity.MAX_REDSTONE);
            core.setStationForTest(98.0);
            core.setDial(98.0);
            return null;
        }), () -> view(origin[0]).full() && stepTicks >= 20, () -> "cheio " + view(origin[0]).full()));
        list.add(DevEndToEnd.wait("a pressão assentar", 40));
        list.add(capture("armilar-cheio"));
    }

    /**
     * O Ultimate de 8 nós com um cristal de cada estado: Sinal, Lapidado, Afinado (de dois tiers), Opaco e Rachado
     * (dois); a 24 blocos a oeste do Elite, sem conjunção e sem recarga (a redstone é 0), e some no fim.
     */
    private static void crystalSteps(List<Step> list, UUID playerId, BlockPos[] origin, int[] counts) {
        BlockPos[] second = new BlockPos[1];
        list.add(new Step("Armilar: o Ultimate dos cristais", 3 * STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            second[0] = origin[0].offset(-ULTIMATE_APART, 0, 0);
            E2eResonatorSteps.clear(level, second[0]);
            E2eResonatorSteps.placeArmilar(level, second[0], RouterTier.ULTIMATE);
            return null;
        }), () -> formed(core(second[0])), () -> "Ultimate montado no cliente"));
        list.add(new Step("Armilar: os cinco estados nos 8 nós", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity ultimate = (ResonanceCoreBlockEntity) server.overworld()
                    .getBlockEntity(core(second[0]));
            ultimate.setAuto(false);
            ultimate.setStationForTest(98.0);
            ultimate.setDial(98.0);
            ItemStack[] states = {new ItemStack(ModItems.SIGNAL_CRYSTAL.get()),
                    new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get()),
                    new ItemStack(ModItems.TUNED_CRYSTALS.get(RouterTier.ELITE).get()),
                    new ItemStack(ModItems.TUNED_CRYSTALS.get(RouterTier.ULTIMATE).get()), dull(),
                    E2eResonatorScreenSteps.cracked(new ItemStack(ModItems.SIGNAL_CRYSTAL.get())),
                    E2eResonatorScreenSteps.cracked(new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get())),
                    new ItemStack(ModItems.SIGNAL_CRYSTAL.get())};
            for (int i = 0; i < states.length; i++) {
                ultimate.setCrystalAt(i, states[i]);
            }
            return null;
        }), () -> view(second[0]).tuned() && view(second[0]).chambers().length == 8
                && view(second[0]).chambers()[7] != 0, () -> "view " + view(second[0])));
        list.add(film("Armilar: câmera do filme no Ultimate", playerId, () -> second[0]));
        list.add(DevEndToEnd.wait("o Ultimate sintonizar", ASSEMBLY_TICKS));
        list.add(shotAt("cristais", QUIET, () -> second[0], counts, (sprites, ribbons) -> {
            if (sprites < 1 || ribbons < 2) {
                throw new StepFailure("o sol e os filamentos sumiram: " + sprites + " sprites, " + ribbons + " fitas");
            }
        }));
        list.add(capture("armilar-cristais"));
        list.add(new Step("Armilar: sem o Ultimate", STEP_TIMEOUT_MS, () -> {
            releaseClock();
            onServer(server -> {
                E2eResonatorSteps.clear(server.overworld(), second[0]);
                return null;
            });
        }, () -> Minecraft.getInstance().level.getBlockState(core(second[0])).isAir(), () -> "Ultimate fora"));
        list.add(film("Armilar: câmera do filme de volta ao Elite", playerId, () -> origin[0]));
    }

    /**
     * A luz no mundo, à meia-noite com a vazão 7: o sol sozinho, o clarão do horizonte e a Porta, uma captura de cada
     * ({@code <prefixo>-luz-<luz>}); de perto, com a passada de luz e sem ela, no mesmo quadro parado.
     */
    private static void lightSteps(List<Step> list, UUID playerId, String prefix, Supplier<BlockPos> origin,
            int[] counts) {
        list.add(new Step("Armilar: a noite e a vazão 7", STEP_TIMEOUT_MS, () -> {
            ArmilarRenderer.forceBandForTests = 7;
            onServer(server -> {
                if (savedDayTime == null) {
                    savedDayTime = server.overworld().getDayTime();
                }
                server.overworld().setDayTime(MIDNIGHT);
                return null;
            });
        }, () -> stepTicks >= FLOW_SETTLE_TICKS, () -> "hora no cliente " + Minecraft.getInstance().level.getDayTime()));
        for (int i = 0; i < LIGHT_D.length; i++) {
            list.add(shotAt("luz " + LIGHT_NAME[i], TC + LIGHT_D[i], origin, counts, null));
            list.add(capture(prefix + "-luz-" + LIGHT_NAME[i]));
        }
        list.add(E2eResonatorSteps.camera("Armilar: câmera de perto da luz", playerId, origin,
                E2eResonatorSteps.CLOSE_VIEW));
        list.add(shotAt("luz de perto", TC - 3, origin, counts, null));
        list.add(capture(prefix + "-luz-perto"));
        list.add(new Step("Armilar: sem a passada de luz", STEP_TIMEOUT_MS, () -> FxLightPass.disabledForTests = true,
                () -> stepTicks >= 4, () -> "passada desligada"));
        list.add(capture(prefix + "-luz-perto-sem"));
        list.add(new Step("Armilar: a passada de luz de volta", STEP_TIMEOUT_MS,
                () -> FxLightPass.disabledForTests = false, () -> true, () -> "passada ligada"));
    }

    /**
     * A mão perto da luz do sol, com a passada de luz e o desenho tardio: com pacote a profundidade é salva antes da
     * mão, então a luz e os efeitos podem cair por cima dela. O jogador segura um cristal, a interface volta e o FOV é o
     * de antes (o da mão); a hora do mundo volta no fim. Captura {@code <prefixo>-mao}.
     */
    private static void handSteps(List<Step> list, UUID playerId, String prefix, Supplier<BlockPos> origin) {
        list.add(new Step("Armilar: a mão com um cristal e o HUD", STEP_TIMEOUT_MS, () -> {
            var options = Minecraft.getInstance().options;
            options.hideGui = false;
            if (savedFov != null) {
                options.fov().set(savedFov);
            }
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND,
                        new ItemStack(ModItems.SIGNAL_CRYSTAL.get()));
                return null;
            });
        }, () -> !Minecraft.getInstance().player.getMainHandItem().isEmpty(), () -> "mão com o cristal"));
        list.add(E2eResonatorSteps.camera("Armilar: câmera perto do sol", playerId, origin,
                E2eResonatorSteps.CLOSE_VIEW, SUN_TARGET));
        list.add(new Step("Armilar: a mão, relógio em " + TC, STEP_TIMEOUT_MS,
                () -> ArmilarRenderer.freezeSecondsForTests = TC, () -> stepTicks >= FREEZE_TICKS, () -> "mão"));
        list.add(capture(prefix + "-mao"));
        list.add(new Step("Armilar: sem a mão, FOV do filme, o dia volta", STEP_TIMEOUT_MS, () -> {
            filmOptions();
            releaseClock();
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                return null;
            });
            restoreDay();
        }, () -> Minecraft.getInstance().player.getMainHandItem().isEmpty(), () -> "mão vazia, hora de volta"));
        list.add(film("Armilar: câmera do filme depois da mão", playerId, origin));
    }

    /**
     * A recarga: um Opaco na {@link #RECHARGE_CHAMBER} com redstone; no meio do tempo da config o cristal está
     * enchendo e, no fim, o clarão no cristal. Relógio real.
     */
    private static void rechargeSteps(List<Step> list, BlockPos[] origin) {
        long[] seen = new long[1];
        long half = Config.rechargeTicks() / 2;
        long waitMs = Config.rechargeTicks() * 50 + 30_000;
        list.add(new Step("Armilar: Opaco em recarga", waitMs, () -> {
            seen[0] = 0;
            onServer(server -> {
                ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                        .getBlockEntity(core(origin[0]));
                core.setCrystalAt(RECHARGE_CHAMBER, dull());
                core.setRedstone(14);
                return null;
            });
        }, () -> {
            if (seen[0] == 0 && view(origin[0]).rechargeStart() >= 0) {
                seen[0] = stepTicks;
            }
            return seen[0] != 0 && stepTicks >= seen[0] + half;
        }, () -> "recarga no cliente"));
        list.add(capture("armilar-recarga"));
        list.add(new Step("Armilar: o fim da recarga", waitMs, () -> seen[0] = 0, () -> {
            if (seen[0] == 0 && view(origin[0]).rechargeStart() < 0) {
                seen[0] = stepTicks;
            }
            return seen[0] != 0 && stepTicks >= seen[0] + FLASH_DELAY_TICKS;
        }, () -> "fim da recarga no cliente"));
        list.add(capture("armilar-recarga-fim"));
    }

    /**
     * O instante {@code a ≈ 0,5} da montagem: o Núcleo é posto de novo e a captura sai 25 ticks depois do
     * {@code formedAt} (o {@code FORM_SECONDS} é 2,5 s; o 0,75 s do brief daria {@code a = 0,3}), com o relógio real.
     */
    private static void assemblySteps(List<Step> list, BlockPos[] origin) {
        list.add(new Step("Armilar: montar de novo", STEP_TIMEOUT_MS, () -> {
            releaseClock();
            onServer(server -> {
                ServerLevel level = server.overworld();
                E2eResonatorSteps.clear(level, origin[0]);
                E2eResonatorSteps.placeArmilar(level, origin[0], RouterTier.ELITE);
                return null;
            });
        }, () -> formed(core(origin[0])) && view(origin[0]).formedAt() > 0, () -> "Núcleo montado no cliente"));
        list.add(new Step("captura armilar-montagem", STEP_TIMEOUT_MS, () -> {
        }, () -> {
            var level = Minecraft.getInstance().level;
            if (level.getGameTime() - view(origin[0]).formedAt() < HALF_ASSEMBLY_TICKS) {
                return false;
            }
            capture(Minecraft.getInstance(), "armilar-montagem");
            return true;
        }, () -> "armilar-montagem.png, " + (Minecraft.getInstance().level.getGameTime() - view(origin[0]).formedAt())
                + " ticks desde a montagem"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // a suíte ressonador-desempenho
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * O tempo de quadro com 0, 1 e 10 Armilares à vista, com o mesmo {@code frameTimeStep} (120 quadros, sem vsync):
     * nenhuma (a câmera de frente, sem nada montado), uma (de frente) e dez num anel de 14 blocos entre uma e a
     * seguinte, vistas de cima. Mede, não afirma; só confere que as dez foram desenhadas.
     */
    static void perfSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] base = new BlockPos[1];
        BlockPos[] ring = new BlockPos[RING];
        Vec3[] back = new Vec3[1];
        float[] backRot = new float[2];
        double radius = RING_SPACING / (2 * Math.sin(Math.PI / RING));
        list.add(new Step("Armilar desempenho: sem a mão e nenhuma Armilar", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            back[0] = minecraft.player.position();
            backRot[0] = minecraft.player.getYRot();
            backRot[1] = minecraft.player.getXRot();
            minecraft.options.hideGui = true;
            releaseClock();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                base[0] = player.blockPosition().offset(12, 0, -14);
                for (int k = 0; k < RING; k++) {
                    double angle = 2 * Math.PI * k / RING;
                    ring[k] = base[0].offset((int) Math.round(radius * Math.cos(angle)), 0,
                            (int) Math.round(radius * Math.sin(angle)));
                    E2eResonatorSteps.clear(server.overworld(), ring[k]);
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                return null;
            });
        }, () -> true, () -> "anel de " + RING));
        list.add(E2eResonatorSteps.camera("Armilar desempenho: câmera de frente, sem Armilar", playerId,
                () -> ring[0], E2eResonatorSteps.FRONT_VIEW));
        list.add(DevEndToEnd.wait("a câmera chegar", 20));
        list.add(E2eResonatorSteps.frameTimeStep("Armilar desempenho: armilar 0 (tempo de quadro)", "armilar 0",
                () -> {
                }, null));
        list.add(new Step("Armilar desempenho: uma Armilar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            E2eResonatorSteps.placeArmilar(server.overworld(), ring[0], RouterTier.ELITE);
            return null;
        }), () -> formed(core(ring[0])), () -> "Armilar montada no cliente"));
        list.add(tuneStep("Armilar desempenho: sintonizar a primeira", ring, 1));
        list.add(DevEndToEnd.wait("a montagem acabar", ASSEMBLY_TICKS));
        list.add(E2eResonatorSteps.frameTimeStep("Armilar desempenho: armilar 1 (tempo de quadro)", "armilar 1",
                () -> {
                }, null));
        list.add(new Step("Armilar desempenho: as outras nove", 3 * STEP_TIMEOUT_MS, () -> onServer(server -> {
            for (int k = 1; k < RING; k++) {
                E2eResonatorSteps.placeArmilar(server.overworld(), ring[k], RouterTier.ELITE);
            }
            return null;
        }), () -> {
            for (int k = 1; k < RING; k++) {
                if (!formed(core(ring[k]))) {
                    return false;
                }
            }
            return true;
        }, () -> "as nove montadas no cliente"));
        list.add(tuneStep("Armilar desempenho: sintonizar as dez", ring, RING));
        list.add(E2eResonatorSteps.camera("Armilar desempenho: câmera de cima do anel", playerId, () -> base[0],
                RING_VIEW, RING_TARGET));
        list.add(DevEndToEnd.wait("a montagem acabar", ASSEMBLY_TICKS));
        list.add(new Step("Armilar desempenho: as dez à vista", STEP_TIMEOUT_MS, () -> {
        }, () -> stepTicks >= 10 && allDrawn(ring, new int[2]), () -> "sprites/fitas das dez: " + drawn(ring)));
        list.add(E2eResonatorSteps.frameTimeStep("Armilar desempenho: armilar 10 (tempo de quadro)", "armilar 10",
                () -> {
                }, null));
        list.add(new Step("Armilar desempenho: limpar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            for (BlockPos pos : ring) {
                E2eResonatorSteps.clear(player.serverLevel(), pos);
            }
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), back[0].x, back[0].y, back[0].z, backRot[0], backRot[1]);
            Minecraft.getInstance().options.hideGui = false;
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(core(ring[RING - 1])).isAir()
                && Minecraft.getInstance().player.position().distanceTo(back[0]) < 0.5,
                () -> "jogador em " + Minecraft.getInstance().player.position()));
    }

    /** Os três cristais vivos e a estação no dial nas primeiras {@code count} Armilares de {@code ring}. */
    private static Step tuneStep(String name, BlockPos[] ring, int count) {
        return new Step(name, STEP_TIMEOUT_MS, () -> onServer(server -> {
            for (int k = 0; k < count; k++) {
                tune((ResonanceCoreBlockEntity) server.overworld().getBlockEntity(core(ring[k])));
            }
            return null;
        }), () -> {
            for (int k = 0; k < count; k++) {
                ChamberView view = view(ring[k]);
                if (!view.tuned() || view.chambers().length != 3 || view.chambers()[2] == 0) {
                    return false;
                }
            }
            return true;
        }, () -> "em sintonia no cliente: " + count);
    }

    private static boolean allDrawn(BlockPos[] ring, int[] counts) {
        for (BlockPos pos : ring) {
            if (!ArmilarRenderer.countsForTests(core(pos), counts) || counts[0] < 1) {
                return false;
            }
        }
        return true;
    }

    private static String drawn(BlockPos[] ring) {
        StringBuilder text = new StringBuilder();
        int[] counts = new int[2];
        for (BlockPos pos : ring) {
            text.append(ArmilarRenderer.countsForTests(core(pos), counts) ? counts[0] + "/" + counts[1] : "-")
                    .append(' ');
        }
        return text.toString().trim();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // a suíte armilar-fabulous
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * A Armilar com os gráficos Fabulous (sem pacote): com o Fabulous o alvo aberto em {@code AFTER_TRANSLUCENT_BLOCKS}
     * pode ser o translúcido e a passada de luz em {@code AFTER_LEVEL} nunca foi testada. A conjunção no arco e no
     * impacto, a sintonia, a luz no mundo e a mão, com o que o desenho tem de pôr em cada quadro conferido. Os
     * gráficos voltam ao de antes no fim e em qualquer saída.
     */
    static void fabulousSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] origin = new BlockPos[1];
        Vec3[] back = new Vec3[1];
        float[] backRot = new float[2];
        int[] counts = new int[2];
        int[] ref = new int[3];
        list.add(new Step("Armilar Fabulous: montar", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            back[0] = minecraft.player.position();
            backRot[0] = minecraft.player.getYRot();
            backRot[1] = minecraft.player.getXRot();
            filmOptions();
            releaseClock();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ServerLevel level = player.serverLevel();
                origin[0] = player.blockPosition().offset(12, 0, -14);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                E2eResonatorSteps.clear(level, origin[0]);
                E2eResonatorSteps.placeArmilar(level, origin[0], RouterTier.ELITE);
                return null;
            });
        }, () -> origin[0] != null && formed(core(origin[0])), () -> "Núcleo montado no cliente"));
        list.add(tuneStep("Armilar Fabulous: sintonizar", origin, 1));
        list.add(new Step("Armilar Fabulous: gráficos Fabulous", STEP_TIMEOUT_MS, () -> {
            var options = Minecraft.getInstance().options;
            if (savedGraphics == null) {
                savedGraphics = options.graphicsMode().get();
            }
            options.graphicsMode().set(GraphicsStatus.FABULOUS);
        }, () -> stepTicks >= 40 && Minecraft.getInstance().options.graphicsMode().get() == GraphicsStatus.FABULOUS
                && Minecraft.useShaderTransparency(), () -> "gráficos "
                + Minecraft.getInstance().options.graphicsMode().get() + ", cadeia translúcida "
                + Minecraft.useShaderTransparency()));
        list.add(film("Armilar Fabulous: câmera do filme", playerId, () -> origin[0]));
        list.add(DevEndToEnd.wait("a montagem acabar", ASSEMBLY_TICKS));
        list.add(new Step("Armilar Fabulous: vazão 7", STEP_TIMEOUT_MS, () -> ArmilarRenderer.forceBandForTests = 7,
                () -> stepTicks >= FLOW_SETTLE_TICKS, () -> "faixa forçada 7"));
        list.add(shotAt("sintonia", QUIET, () -> origin[0], counts, (sprites, ribbons) -> {
            if (sprites < 1 || ribbons < 2) {
                throw new StepFailure("o sol e os filamentos sumiram: " + sprites + " sprites, " + ribbons + " fitas");
            }
            ref[0] = ribbons;
        }));
        list.add(capture("armilar-fabulous-sintonia"));
        phases(list, "armilar-fabulous", () -> origin[0], counts, ref, ARCO, IMPACTO);
        lightSteps(list, playerId, "armilar-fabulous", () -> origin[0], counts);
        handSteps(list, playerId, "armilar-fabulous", () -> origin[0]);
        list.add(new Step("Armilar Fabulous: limpar", STEP_TIMEOUT_MS, () -> {
            restoreHooks();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                E2eResonatorSteps.clear(player.serverLevel(), origin[0]);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.teleportTo(player.serverLevel(), back[0].x, back[0].y, back[0].z, backRot[0], backRot[1]);
                Minecraft.getInstance().options.hideGui = false;
                return null;
            });
        }, () -> Minecraft.getInstance().level.getBlockState(core(origin[0])).isAir()
                && Minecraft.getInstance().player.position().distanceTo(back[0]) < 0.5
                && Minecraft.getInstance().options.graphicsMode().get() != GraphicsStatus.FABULOUS,
                () -> "jogador em " + Minecraft.getInstance().player.position()));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // o que é de todas
    // ---------------------------------------------------------------------------------------------------------------

    /** O Núcleo sintonizado na estação com os três cristais vivos (nenhum Opaco: nada recarrega). */
    private static void tune(ResonanceCoreBlockEntity core) {
        core.setAuto(false);
        core.setStationForTest(98.0);
        core.setDial(98.0);
        core.insertCrystal(new ItemStack(ModItems.SIGNAL_CRYSTAL.get()));
        core.insertCrystal(new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get()));
        core.insertCrystal(new ItemStack(ModItems.SIGNAL_CRYSTAL.get()));
    }

    /** Solta o relógio parado, a vazão forçada e a passada de luz desligada. */
    private static void releaseClock() {
        ArmilarRenderer.freezeSecondsForTests = Double.NaN;
        ArmilarRenderer.forceBandForTests = -1;
        FxLightPass.disabledForTests = false;
    }

    /** Devolve a hora do mundo, se o teste mudou. Na thread do cliente (espera o servidor). */
    private static void restoreDay() {
        Long saved = savedDayTime;
        if (saved == null) {
            return;
        }
        savedDayTime = null;
        try {
            onServer(server -> {
                server.overworld().setDayTime(saved);
                return null;
            });
        } catch (Exception e) {
            // o servidor já saiu: a hora não existe mais
        }
    }

    /**
     * Solta o relógio parado, a vazão forçada e a passada de luz, e devolve o FOV, os gráficos e a hora do mundo.
     * Idempotente; o motor chama em qualquer saída, na thread do cliente, sem gravar o options.txt.
     */
    static void restoreHooks() {
        releaseClock();
        var options = Minecraft.getInstance().options;
        if (savedFov != null) {
            options.fov().set(savedFov);
            savedFov = null;
        }
        if (savedGraphics != null) {
            options.graphicsMode().set(savedGraphics);
            savedGraphics = null;
        }
        restoreDay();
    }

    private static ItemStack dull() {
        ItemStack dull = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(dull, 0);
        return dull;
    }

    private static boolean formed(BlockPos pos) {
        BlockState state = Minecraft.getInstance().level.getBlockState(pos);
        return state.getBlock() instanceof ResonanceCoreBlock && state.hasProperty(ResonatorPartBlock.FORMED)
                && state.getValue(ResonatorPartBlock.FORMED);
    }

    private static ChamberView view(BlockPos origin) {
        return ((ResonanceCoreBlockEntity) Minecraft.getInstance().level.getBlockEntity(core(origin))).clientView();
    }
}
