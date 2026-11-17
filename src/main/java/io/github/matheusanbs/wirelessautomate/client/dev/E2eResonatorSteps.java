package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.FrameClock;
import io.github.matheusanbs.wirelessautomate.client.dock.render.PatternDockRenderer;
import io.github.matheusanbs.wirelessautomate.client.repairer.render.RepairerRenderer;
import io.github.matheusanbs.wirelessautomate.client.resonator.ResonatorProjection;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.ArmilarRenderer;
import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.resonator.screen.ResonatorScreen;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.resonator.ProjectionDiff;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A projeção do Ressonador no e2e: uma Armilar pela metade (o Núcleo virado para o sul, as Lajes do chão e uma
 * terra numa casa de ar), o clique de mão vazia no Núcleo, a dica que fica na action bar mesmo com a linha do
 * problema que o servidor manda no clique, a contagem e a captura dos fantasmas; depois a Armilar inteira solta (só
 * a terra no ar e uma Coluna faltando, vista de lado e de frente); por fim a Coluna e sem a terra, que monta e apaga
 * a projeção, com a Armilar formada vista de lado, de frente e de cima (o topo de cada peça de face).
 */
final class E2eResonatorSteps {
    private static final Part[][][] ARMILAR = ResonatorBlueprint.expected(Face.SOUTH);
    /** A terra fica numa casa de ar, no canto de frente da Armilar (leste e sul), à vista da câmera. */
    private static final int[] DIRT = {4, 1, 4};
    /** A Coluna que falta na segunda foto: a de cima, a sudeste do Berço. */
    private static final int[] MISSING_COLUMN = {3, 2, 3};
    /** O vsync e o limite de FPS de antes da medida do tempo de quadro. */
    private static boolean measuring;
    private static boolean savedVsync;
    private static int savedLimit;
    private static final String HINT = "block.wirelessautomate.resonance_core.projection.hint";
    /** Os pés do jogador nas fotos de lado: a sudeste da caixa, a uns 5 blocos do Núcleo (o clique no Núcleo alcança). */
    static final double[] SIDE_VIEW = {5.8, 2.8, 7.0};
    /** Os pés do jogador nas fotos de frente: ao sul, a 4 blocos do Núcleo, de frente para a Armilar (yaw 180°). */
    static final double[] FRONT_VIEW = {2.5, 2.3, 9.5};
    /**
     * Os pés do jogador na foto de perto do centro: dentro da Armilar, entre as Colunas do sul e a um bloco e meio
     * do Berço, com a cabeça acima das Lajes.
     */
    static final double[] CLOSE_VIEW = {2.5, 2.18, 3.65};
    /** O ponto que a câmera olha por padrão: o centro da Armilar, 2 blocos para trás do Núcleo. */
    private static final double[] CENTER = {2.5, 2.3, 2.5};
    /** De cima, com o norte para cima da tela: o alvo fica um décimo de bloco ao norte dos pés. */
    private static final double[] TOP_VIEW = {2.5, 6.0, 2.5};
    private static final double[] TOP_TARGET = {2.5, 0.5, 2.4};

    private E2eResonatorSteps() {
    }

    static void resonatorSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] origin = new BlockPos[1];
        Vec3[] back = new Vec3[1];
        float[] backRot = new float[2];
        list.add(new Step("Ressonador: Armilar pela metade", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            back[0] = minecraft.player.position();
            backRot[0] = minecraft.player.getYRot();
            backRot[1] = minecraft.player.getXRot();
            minecraft.options.hideGui = false;
            minecraft.options.keyShift.setDown(false);
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ServerLevel level = player.serverLevel();
                origin[0] = player.blockPosition().offset(12, 0, -14);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                clear(level, origin[0]);
                place(level, origin[0], true, false);
                level.setBlockAndUpdate(origin[0].offset(DIRT[0], DIRT[1], DIRT[2]), Blocks.DIRT.defaultBlockState());
                return null;
            });
        }, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.level.getBlockState(core(origin[0])).getBlock() instanceof ResonanceCoreBlock
                    && minecraft.level.getBlockState(origin[0].offset(DIRT[0], DIRT[1], DIRT[2])).is(Blocks.DIRT)
                    && minecraft.player.getMainHandItem().isEmpty();
        }, () -> "Núcleo e terra no cliente, mão " + Minecraft.getInstance().player.getMainHandItem()));
        list.add(camera("Ressonador: câmera", playerId, () -> origin[0], SIDE_VIEW));
        list.add(new Step("Ressonador: clique no Núcleo liga a projeção", STEP_TIMEOUT_MS, () -> useOn(core(origin[0])),
                () -> ResonatorProjection.isOn() && ResonatorProjection.result() != null,
                () -> "projeção " + ResonatorProjection.isOn()));
        list.add(new Step("Ressonador: a dica fica na action bar", STEP_TIMEOUT_MS, () -> {
        }, () -> stepTicks >= 10 && HINT.equals(overlayKey()), () -> "action bar com " + overlayKey()));
        list.add(new Step("Ressonador: contagem da projeção", STEP_TIMEOUT_MS, () -> {
        }, () -> {
            ProjectionDiff.Result result = ResonatorProjection.result();
            // a dica fica 40 ticks na action bar antes da contagem
            return stepTicks >= 50 && result != null && result.wrong() == 1 && result.missing().equals(Map.of(
                    Part.COLUMN, 8, Part.CRADLE, 2, Part.ENERGY_PORT, 1, Part.CRYSTAL_PORT, 1, Part.TUNER, 1));
        }, () -> {
            ProjectionDiff.Result result = ResonatorProjection.result();
            return result == null ? "sem projeção" : "faltam " + result.missing() + ", errados " + result.wrong();
        }));
        list.add(capture("armilar-projecao"));
        list.add(new Step("Ressonador: Armilar solta, com a terra e sem uma Coluna", STEP_TIMEOUT_MS,
                () -> onServer(server -> {
                    place(server.overworld(), origin[0], false, true);
                    return null;
                }), () -> {
            ProjectionDiff.Result result = ResonatorProjection.result();
            return stepTicks >= 12 && result != null && result.wrong() == 1
                    && result.missing().equals(Map.of(Part.COLUMN, 1));
        }, () -> {
            ProjectionDiff.Result result = ResonatorProjection.result();
            return result == null ? "sem projeção" : "faltam " + result.missing() + ", errados " + result.wrong();
        }));
        list.add(capture("armilar-solta-lado"));
        list.add(camera("Ressonador: câmera de frente", playerId, () -> origin[0], FRONT_VIEW));
        list.add(DevEndToEnd.wait("a câmera chegar", 10));
        list.add(capture("armilar-solta-frente"));
        list.add(camera("Ressonador: câmera de lado", playerId, () -> origin[0], SIDE_VIEW));
        list.add(new Step("Ressonador: completar monta e desliga a projeção", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            level.setBlockAndUpdate(origin[0].offset(DIRT[0], DIRT[1], DIRT[2]), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(origin[0].offset(MISSING_COLUMN[0], MISSING_COLUMN[1], MISSING_COLUMN[2]),
                    looseState(Part.COLUMN, MISSING_COLUMN[0], MISSING_COLUMN[1], MISSING_COLUMN[2],
                            RouterTier.ELITE));
            return null;
        }), () -> {
            BlockState core = Minecraft.getInstance().level.getBlockState(core(origin[0]));
            return core.getBlock() instanceof ResonanceCoreBlock && core.getValue(ResonatorPartBlock.FORMED)
                    && !ResonatorProjection.isOn();
        }, () -> "Núcleo " + Minecraft.getInstance().level.getBlockState(core(origin[0])) + ", projeção "
                + ResonatorProjection.isOn()));
        list.add(DevEndToEnd.wait("Ressonador montado", 10));
        list.add(capture("armilar-formada-lado"));
        list.add(camera("Ressonador: câmera de frente, montado", playerId, () -> origin[0], FRONT_VIEW));
        list.add(DevEndToEnd.wait("a câmera chegar", 10));
        list.add(capture("armilar-formada-frente"));
        selectionSteps(list, playerId, origin);
        topSteps(list, playerId, origin);
        screenSteps(list, origin);
        E2eResonatorScreenSteps.screenSteps(list, playerId, origin);
        E2eArmilarRenderSteps.chamberSteps(list, playerId, origin);
        list.add(new Step("Ressonador: limpar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            clear(player.serverLevel(), origin[0]);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), back[0].x, back[0].y, back[0].z, backRot[0], backRot[1]);
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(core(origin[0])).isAir()
                && Minecraft.getInstance().player.position().distanceTo(back[0]) < 0.5,
                () -> "jogador em " + Minecraft.getInstance().player.position()));
    }

    /**
     * O contorno de seleção da Armilar formada, que segue o desenho e não o cubo: a mira numa Laje do canto (a
     * {@code p00}, no chão a noroeste) e numa Coluna (a de noroeste, embaixo), vistas de fora, com o HUD ligado
     * (o jogo só desenha o contorno com ele). Cada passo espera a mira cair no bloco certo.
     */
    private static void selectionSteps(List<Step> list, UUID playerId, BlockPos[] origin) {
        aim(list, playerId, origin, "armilar-selecao", new double[] {-1.6, 1.3, -1.6}, new double[] {0.9, 0.5, 0.5},
                new int[] {0, 0, 0}, ModBlocks.RESONATOR_CASING.get());
        aim(list, playerId, origin, "armilar-selecao-coluna", new double[] {-1.6, 1.4, -1.6},
                new double[] {1.12, 1.5, 1.12}, new int[] {1, 1, 1}, ModBlocks.RESONATOR_COLUMN.get());
        list.add(camera("Ressonador: câmera de lado, depois da seleção", playerId, () -> origin[0], SIDE_VIEW));
    }

    private static void aim(List<Step> list, UUID playerId, BlockPos[] origin, String name, double[] feet,
            double[] target, int[] block, net.minecraft.world.level.block.Block expected) {
        list.add(camera("Ressonador: câmera, " + name, playerId, () -> origin[0], feet, target));
        list.add(new Step("Ressonador: a mira cai em " + name, STEP_TIMEOUT_MS, () -> {
        }, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            return stepTicks >= 10 && minecraft.hitResult instanceof BlockHitResult hit
                    && hit.getType() == HitResult.Type.BLOCK
                    && hit.getBlockPos().equals(origin[0].offset(block[0], block[1], block[2]))
                    && minecraft.level.getBlockState(hit.getBlockPos()).is(expected);
        }, () -> "mira em " + Minecraft.getInstance().hitResult));
        list.add(capture(name));
    }

    /**
     * A Armilar formada vista de cima, com o norte para cima da tela, e o topo da Porta de Energia, da de Cristais e
     * do Sintonizador de perto (a luz e a textura de cima, que o gerador gira por face). Sem a mão na tela.
     */
    private static void topSteps(List<Step> list, UUID playerId, BlockPos[] origin) {
        list.add(new Step("Ressonador: sem a mão para as fotos de cima", STEP_TIMEOUT_MS,
                () -> Minecraft.getInstance().options.hideGui = true, () -> true, () -> "mão escondida"));
        top(list, playerId, origin, "armilar-topo", TOP_VIEW, TOP_TARGET);
        top(list, playerId, origin, "armilar-topo-energia", new double[] {4.5, 3.6, 3.7},
                new double[] {4.5, 1.0, 2.5});
        top(list, playerId, origin, "armilar-topo-cristais", new double[] {0.5, 3.6, 3.7},
                new double[] {0.5, 1.0, 2.5});
        top(list, playerId, origin, "armilar-topo-sintonizador", new double[] {2.5, 3.6, 1.7},
                new double[] {2.5, 1.0, 0.5});
        list.add(new Step("Ressonador: a mão de volta nas fotos", STEP_TIMEOUT_MS,
                () -> Minecraft.getInstance().options.hideGui = false, () -> true, () -> "mão"));
        list.add(camera("Ressonador: câmera de lado, depois do topo", playerId, () -> origin[0], SIDE_VIEW));
    }

    private static void top(List<Step> list, UUID playerId, BlockPos[] origin, String name, double[] feet,
            double[] target) {
        list.add(camera("Ressonador: câmera, " + name, playerId, () -> origin[0], feet, target));
        list.add(DevEndToEnd.wait("a câmera chegar", 10));
        list.add(capture(name));
    }

    /**
     * A tela do Núcleo montado (Elite, três câmaras): dois cristais e um Opaco, redstone, o dial pelo botão e o
     * Sintonizador, fotografada no claro, no escuro e em pt_br.
     */
    private static void screenSteps(List<Step> list, BlockPos[] origin) {
        list.add(new Step("Ressonador: cristais e redstone no Núcleo", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld().getBlockEntity(core(origin[0]));
            core.setStationForTest(98.0);
            core.setDial(98.0);
            core.insertCrystal(new ItemStack(ModItems.SIGNAL_CRYSTAL.get()));
            ItemStack half = new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get());
            SignalCrystalItem.setCharge(half, CrystalCharge.FULL / 2);
            core.insertCrystal(half);
            ItemStack dull = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
            SignalCrystalItem.setCharge(dull, 0);
            core.insertCrystal(dull);
            core.addRedstone(14);
            return null;
        }), () -> {
            var view = ((ResonanceCoreBlockEntity) Minecraft.getInstance().level.getBlockEntity(core(origin[0]))).clientView();
            return view.chambers().length == 3 && view.chambers()[0] != 0 && view.chambers()[2] != 0;
        }, () -> "câmaras no cliente"));
        list.add(new Step("Ressonador: abrir a tela", STEP_TIMEOUT_MS, () -> useOn(core(origin[0])),
                () -> Minecraft.getInstance().screen instanceof ResonatorScreen screen && screen.getMenu().received(),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("Ressonador: +0,05 pelo botão", STEP_TIMEOUT_MS, () -> {
            ResonatorScreen screen = (ResonatorScreen) Minecraft.getInstance().screen;
            int[] at = screen.dialPlusCenter();
            click(screen, at[0], at[1]);
        }, () -> Minecraft.getInstance().screen instanceof ResonatorScreen screen && screen.getMenu().state() != null
                && Math.abs(screen.getMenu().state().dial() - 98.05) < 1e-6,
                () -> "estado " + ((ResonatorScreen) Minecraft.getInstance().screen).getMenu().state()));
        list.add(new Step("Ressonador: ligar o Sintonizador", STEP_TIMEOUT_MS, () -> {
            ResonatorScreen screen = (ResonatorScreen) Minecraft.getInstance().screen;
            int[] at = screen.modeAutoCenter();
            click(screen, at[0], at[1]);
        }, () -> Minecraft.getInstance().screen instanceof ResonatorScreen screen && screen.getMenu().state() != null
                && screen.getMenu().state().auto(),
                () -> "estado " + ((ResonatorScreen) Minecraft.getInstance().screen).getMenu().state()));
        list.add(new Step("captura ressonador-tela", STEP_TIMEOUT_MS, () -> {
            ResonatorScreen screen = (ResonatorScreen) Minecraft.getInstance().screen;
            int[] at = screen.coreSlotCenter(2);
            moveMouse(at[0], at[1]);
        }, () -> {
            if (stepTicks < 8) {
                return false;
            }
            capture(Minecraft.getInstance(), "ressonador-tela");
            return true;
        }, () -> "ressonador-tela.png"));
        list.add(clipCheck("ressonador-tela"));
        E2eVariants.escuro(list, () -> {
            list.add(E2eDarkSteps.themeClick("Ressonador: tema escuro", GuiTheme.DARK));
            list.add(new Step("captura ressonador-tela-escuro", STEP_TIMEOUT_MS, () -> moveMouse(0, 0), () -> {
                if (stepTicks < 6) {
                    return false;
                }
                capture(Minecraft.getInstance(), "ressonador-tela-escuro");
                return true;
            }, () -> "ressonador-tela-escuro.png"));
            list.add(clipCheck("ressonador-tela-escuro"));
            list.add(E2eDarkSteps.themeClick("Ressonador: tema claro", GuiTheme.LIGHT));
        });
        list.add(close("Ressonador: fechar a tela"));
        E2eVariants.pt(list, () -> {
            list.add(language("pt_br"));
            list.add(new Step("Ressonador: abrir em pt_br", STEP_TIMEOUT_MS, () -> useOn(core(origin[0])),
                    () -> Minecraft.getInstance().screen instanceof ResonatorScreen screen && screen.getMenu().received(),
                    () -> "tela " + describe(Minecraft.getInstance().screen)));
            list.add(capture("ressonador-tela-pt"));
            list.add(clipCheck("ressonador-tela-pt"));
            list.add(close("Ressonador: fechar em pt_br"));
            list.add(language("en_us"));
        });
    }

    /** O que a medida repete a cada tick de espera (nenhuma medida usa hoje, todas passam {@code null}; ficou para uma que precise manter um estado vivo). */
    @FunctionalInterface
    interface Tick {
        void run() throws Exception;
    }

    /** A única Porta de Energia da Armilar. */
    static BlockPos energyPort(BlockPos origin) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    if (ARMILAR[x][y][z] == Part.ENERGY_PORT) {
                        return origin.offset(x, y, z);
                    }
                }
            }
        }
        throw new IllegalStateException("a Armilar não tem Porta de Energia");
    }

    /**
     * O tempo médio de quadro em 120 quadros, sem o limite de FPS e sem o vsync (o que valia volta ao fim): o
     * {@code setup} liga o gancho do desenho medido e o {@code perTick} repete a cada tick de espera; a linha
     * {@code label: N ms/quadro} vai para o result.txt.
     */
    static Step frameTimeStep(String name, String label, Runnable setup, Tick perTick) {
        long[] start = new long[2];
        double[] result = new double[1];
        return new Step(name, 60_000, () -> {
            var options = Minecraft.getInstance().options;
            if (!measuring) {
                measuring = true;
                savedVsync = options.enableVsync().get();
                savedLimit = options.framerateLimit().get();
            }
            options.enableVsync().set(false);
            options.framerateLimit().set(260);
            setup.run();
            start[0] = FrameClock.frame() + 30;
        }, () -> {
            if (perTick != null) {
                perTick.run();
            }
            long frame = FrameClock.frame();
            if (frame < start[0]) {
                return false;
            }
            if (start[1] == 0) {
                // o check roda por tick: o primeiro depois do aquecimento marca o quadro e a hora de partida
                start[0] = frame;
                start[1] = System.nanoTime();
                return false;
            }
            if (frame < start[0] + 120) {
                return false;
            }
            result[0] = (System.nanoTime() - start[1]) / (double) (frame - start[0]) / 1_000_000.0;
            restoreMeasure();
            return true;
        }, () -> String.format(java.util.Locale.ROOT, "%s: %.3f ms/quadro", label, result[0]));
    }

    /**
     * Devolve o vsync, o limite de FPS, a vazão forçada e os ganchos do Reparador (o tempo parado e o
     * {@code repairTicks}). Idempotente; o motor chama em qualquer saída (sucesso, falha ou tempo esgotado), sem
     * gravar o options.txt.
     */
    static void restoreFrameSettings() {
        restoreMeasure();
        E2eRepairerAnimSteps.restoreHooks();
        E2eDockAnimSteps.restoreHooks();
        E2eFxProbeSteps.restoreHooks();
        E2eArmilarRenderSteps.restoreHooks();
    }

    /** O fim de uma medida: o vsync, o limite de FPS e os desenhos forçados; o tempo parado do Reparador fica. */
    private static void restoreMeasure() {
        ArmilarRenderer.forceBandForTests = -1;
        ArmilarRenderer.freezeSecondsForTests = Double.NaN;
        RepairerRenderer.forceLegacyForTests = false;
        PatternDockRenderer.disabledForTests = false;
        if (!measuring) {
            return;
        }
        measuring = false;
        var options = Minecraft.getInstance().options;
        options.enableVsync().set(savedVsync);
        options.framerateLimit().set(savedLimit);
    }

    /**
     * Põe as peças da primeira foto ({@code half}) ou as que faltam para a Armilar inteira, sem a Coluna de
     * {@link #MISSING_COLUMN} se {@code skipColumn}; a terra não é tocada.
     */
    private static void place(ServerLevel level, BlockPos origin, boolean half, boolean skipColumn) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    boolean skipped = skipColumn && x == MISSING_COLUMN[0] && y == MISSING_COLUMN[1]
                            && z == MISSING_COLUMN[2];
                    if (ARMILAR[x][y][z] != Part.AIR && halfBuilt(x, y, z) == half && !skipped) {
                        level.setBlockAndUpdate(origin.offset(x, y, z),
                                looseState(ARMILAR[x][y][z], x, y, z, RouterTier.ELITE));
                    }
                }
            }
        }
    }

    /**
     * A Armilar inteira, com o Núcleo virado para o sul no tier dado; monta sozinha pelo tick agendado do Núcleo. A
     * vitrine do CurseForge usa a mesma.
     */
    static void placeArmilar(ServerLevel level, BlockPos origin, RouterTier tier) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    if (ARMILAR[x][y][z] != Part.AIR) {
                        level.setBlockAndUpdate(origin.offset(x, y, z), looseState(ARMILAR[x][y][z], x, y, z, tier));
                    }
                }
            }
        }
    }

    /**
     * A chave do texto na action bar agora, ou o texto cru se ele não é traduzível. O campo do {@link Gui} é privado
     * e só serve a este teste (o jogo de dev roda com os nomes da Mojang).
     */
    static String overlayKey() {
        try {
            Field field = Gui.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            Component message = (Component) field.get(Minecraft.getInstance().gui);
            if (message == null) {
                return "nada";
            }
            return message.getContents() instanceof TranslatableContents contents ? contents.getKey()
                    : message.getString();
        } catch (ReflectiveOperationException e) {
            return "erro: " + e;
        }
    }

    /** Os argumentos do texto traduzível da action bar agora; vazio se não há ou ele não é traduzível. */
    static Object[] overlayArgs() {
        try {
            Field field = Gui.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            Component message = (Component) field.get(Minecraft.getInstance().gui);
            return message != null && message.getContents() instanceof TranslatableContents contents
                    ? contents.getArgs() : new Object[0];
        } catch (ReflectiveOperationException e) {
            return new Object[0];
        }
    }

    static BlockPos core(BlockPos origin) {
        return origin.offset(2, 0, 4);
    }

    /** O que a primeira foto já tem: o Núcleo e as Lajes (menos a casa da terra). */
    private static boolean halfBuilt(int x, int y, int z) {
        Part part = ARMILAR[x][y][z];
        if (part == Part.CORE) {
            return true;
        }
        if (part == Part.CASING) {
            return !isDirt(x, y, z);
        }
        return false;
    }

    private static boolean isDirt(int x, int y, int z) {
        return x == DIRT[0] && y == DIRT[1] && z == DIRT[2];
    }

    /** O bloco solto de cada peça; o Núcleo e as peças de face nascem virados para fora, como a montagem os deixa. */
    private static BlockState looseState(Part part, int x, int y, int z, RouterTier tier) {
        Direction out = switch (part) {
            case CORE -> Direction.SOUTH;
            case ENERGY_PORT -> Direction.EAST;
            case CRYSTAL_PORT -> Direction.WEST;
            default -> Direction.NORTH;
        };
        return switch (part) {
            case CASING -> ModBlocks.RESONATOR_CASING.get().defaultBlockState();
            case COLUMN -> ModBlocks.RESONATOR_COLUMN.get().defaultBlockState();
            case CRADLE -> ModBlocks.RESONATOR_CRADLE.get().defaultBlockState();
            case CORE -> ModBlocks.RESONANCE_CORE.get().defaultBlockState().setValue(ResonatorPartBlock.FACING, out)
                    .setValue(RouterBlock.TIER, tier);
            case ENERGY_PORT -> ModBlocks.RESONATOR_ENERGY_PORT.get().defaultBlockState().setValue(ResonatorPartBlock.FACING, out);
            case CRYSTAL_PORT -> ModBlocks.RESONATOR_CRYSTAL_PORT.get().defaultBlockState()
                    .setValue(ResonatorPartBlock.FACING, out);
            case TUNER -> ModBlocks.RESONATOR_TUNER.get().defaultBlockState().setValue(ResonatorPartBlock.FACING, out);
            case AIR, OTHER -> Blocks.AIR.defaultBlockState();
        };
    }

    /** Ar na caixa e numa volta de dois blocos em torno dela (o chão fica). */
    static void clear(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, 0, -2), origin.offset(6, 6, 6))) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        // o Núcleo solta cristais, redstone e catalisador ao sair: sem isso ficam no chão da câmara da próxima montagem
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(origin.getX() - 4,
                origin.getY() - 2, origin.getZ() - 4, origin.getX() + 9, origin.getY() + 9, origin.getZ() + 9);
        for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, box)) {
            item.discard();
        }
    }

    /** O jogador voando com os pés em {@code origin + feet}, olhando o centro da Armilar. */
    static Step camera(String name, UUID playerId, java.util.function.Supplier<BlockPos> origin,
            double[] feet) {
        return camera(name, playerId, origin, feet, CENTER);
    }

    /** O jogador voando com os pés em {@code origin + feet}, olhando {@code origin + target}. */
    static Step camera(String name, UUID playerId, java.util.function.Supplier<BlockPos> origin,
            double[] feet, double[] target) {
        double[] cam = new double[3];
        float[] ang = new float[2];
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            BlockPos o = origin.get();
            cam[0] = o.getX() + feet[0];
            cam[1] = o.getY() + feet[1];
            cam[2] = o.getZ() + feet[2];
            double ex = o.getX() + target[0] - cam[0];
            double ey = o.getY() + target[1] - (cam[1] + 1.62);
            double ez = o.getZ() + target[2] - cam[2];
            ang[0] = (float) Math.toDegrees(Math.atan2(-ex, ez));
            ang[1] = (float) -Math.toDegrees(Math.atan2(ey, Math.sqrt(ex * ex + ez * ez)));
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.player.setYRot(ang[0]);
            minecraft.player.setXRot(ang[1]);
            minecraft.player.yRotO = ang[0];
            minecraft.player.xRotO = ang[1];
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.serverLevel(), cam[0], cam[1], cam[2], ang[0], ang[1]);
                return null;
            });
        }, () -> Math.abs(Minecraft.getInstance().player.getXRot() - ang[1]) < 0.5f
                && Minecraft.getInstance().player.position().distanceToSqr(cam[0], cam[1], cam[2]) < 0.5,
                () -> "câmera em " + Minecraft.getInstance().player.position());
    }
}
