package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.FrameClock;
import io.github.matheusanbs.wirelessautomate.client.resonator.ResonatorProjection;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.ResonatorRenderer;
import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.resonator.screen.ResonatorScreen;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.resonator.TuneSignal;
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
import net.minecraft.world.phys.Vec3;

/**
 * A projeção do Ressonador no e2e: uma Vitrine pela metade (o Núcleo virado para o leste, as duas camadas de baixo
 * das carcaças, a janela sem dois vidros e uma terra numa aresta), o clique de mão vazia no Núcleo, a dica que fica
 * na action bar mesmo com a linha do problema que o servidor manda no clique, a contagem e a
 * captura dos fantasmas; depois a estrutura completa, que monta e apaga a projeção, e a segunda captura.
 */
final class E2eResonatorSteps {
    private static final Part[][][] VITRINE = ResonatorBlueprint.expected(Face.EAST, ResonatorBlueprint.Layout.VITRINE);
    /** A terra fica na aresta vertical entre as faces leste e sul, de frente para a câmera. */
    private static final int[] DIRT = {4, 1, 4};
    /** O vsync e o limite de FPS de antes da medida do tempo de quadro. */
    private static boolean measuring;
    private static boolean savedVsync;
    private static int savedLimit;
    private static final String HINT = "block.wirelessautomate.resonance_core.projection.hint";
    /** Os dois vidros da janela que ficam faltando. */
    private static final int[][] MISSING_GLASS = {{2, 3, 4}, {3, 2, 4}};
    /** Os pés do jogador nas fotos de fora: a leste e ao sul da caixa, a uns 7 blocos do Núcleo. */
    private static final double[] SIDE_VIEW = {8.5, 3.2, 7.5};
    /** Os pés do jogador nas fotos da câmara: a 2,5 blocos da janela, de frente para ela (yaw 180°). */
    private static final double[] WINDOW_VIEW = {2.5, 2.3, 7.5};
    /**
     * Os pés do jogador na foto de perto dos cristais: dentro da câmara, junto da janela e com a cabeça perto do teto,
     * olhando o anel de cima. A Vitrine montada não tem vão no vidro, e de fora o vidro lavaria a foto.
     */
    static final double[] CLOSE_VIEW = {2.5, 2.18, 3.65};

    private E2eResonatorSteps() {
    }

    static void resonatorSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] origin = new BlockPos[1];
        Vec3[] back = new Vec3[1];
        float[] backRot = new float[2];
        list.add(new Step("Ressonador: Vitrine pela metade", STEP_TIMEOUT_MS, () -> {
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
                place(level, origin[0], true);
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
                    Part.CASING, 47, Part.GLASS, 2, Part.ENERGY_PORT, 1, Part.CRYSTAL_PORT, 1, Part.TUNER, 1));
        }, () -> {
            ProjectionDiff.Result result = ResonatorProjection.result();
            return result == null ? "sem projeção" : "faltam " + result.missing() + ", errados " + result.wrong();
        }));
        list.add(capture("ressonador-projecao"));
        list.add(new Step("Ressonador: completar monta e desliga a projeção", STEP_TIMEOUT_MS, () -> onServer(server -> {
            place(server.overworld(), origin[0], false);
            return null;
        }), () -> {
            BlockState core = Minecraft.getInstance().level.getBlockState(core(origin[0]));
            return core.getBlock() instanceof ResonanceCoreBlock && core.getValue(ResonatorPartBlock.FORMED)
                    && !ResonatorProjection.isOn();
        }, () -> "Núcleo " + Minecraft.getInstance().level.getBlockState(core(origin[0])) + ", projeção "
                + ResonatorProjection.isOn()));
        list.add(DevEndToEnd.wait("Ressonador montado", 10));
        list.add(capture("ressonador-montado"));
        screenSteps(list, origin);
        chamberSteps(list, playerId, origin);
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
        list.add(close("Ressonador: fechar a tela"));
        list.add(language("pt_br"));
        list.add(new Step("Ressonador: abrir em pt_br", STEP_TIMEOUT_MS, () -> useOn(core(origin[0])),
                () -> Minecraft.getInstance().screen instanceof ResonatorScreen screen && screen.getMenu().received(),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("ressonador-tela-pt"));
        list.add(clipCheck("ressonador-tela-pt"));
        list.add(close("Ressonador: fechar em pt_br"));
        list.add(language("en_us"));
    }

    /**
     * A câmara vista pela janela: em sintonia (a figura fechada, os cristais juntos e os feixes, o Opaco caído e a
     * placa com a figura) e depois a 72% (o rabisco, os cristais fora de ritmo e a placa com ruído).
     */
    private static void chamberSteps(List<Step> list, UUID playerId, BlockPos[] origin) {
        list.add(new Step("Ressonador: dial na estação, sem Sintonizador", STEP_TIMEOUT_MS, () -> {
            // sem a mão, a mira e o contorno do vidro na frente da câmara
            Minecraft.getInstance().options.hideGui = true;
            onServer(server -> {
                ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                        .getBlockEntity(core(origin[0]));
                core.setAuto(false);
                core.setDial(98.0);
                return null;
            });
        }, () -> ((ResonanceCoreBlockEntity) Minecraft.getInstance().level.getBlockEntity(core(origin[0])))
                .clientView().band() == TuneSignal.BANDS - 1, () -> "faixa no cliente"));
        list.add(camera("Ressonador: câmera na janela", playerId, () -> origin[0], WINDOW_VIEW));
        list.add(DevEndToEnd.wait("a câmara acender", 30));
        list.add(capture("ressonador-camara"));
        list.add(frameTime(true, "", null));
        list.add(frameTime(false, "", null));
        list.add(frameTime(true, "", null));
        list.add(frameTime(false, "", null));
        list.add(new Step("Ressonador: dial a 96,7", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ((ResonanceCoreBlockEntity) server.overworld().getBlockEntity(core(origin[0]))).setDial(96.7);
            return null;
        }), () -> ((ResonanceCoreBlockEntity) Minecraft.getInstance().level.getBlockEntity(core(origin[0])))
                .clientView().band() < TuneSignal.BANDS - 1, () -> "faixa no cliente"));
        list.add(DevEndToEnd.wait("a figura escorregar", 60));
        list.add(capture("ressonador-camara-chiado"));
        list.add(new Step("Ressonador: dial de volta a 98,0", STEP_TIMEOUT_MS, () -> {
            Minecraft.getInstance().options.hideGui = false;
            onServer(server -> {
                ((ResonanceCoreBlockEntity) server.overworld().getBlockEntity(core(origin[0]))).setDial(98.0);
                return null;
            });
        }, () -> true, () -> "dial"));
        E2eResonatorFlowSteps.flowSteps(list, playerId, origin);
    }

    /** O que a medida repete a cada tick de espera (a extração que mantém o conduto aceso). */
    @FunctionalInterface
    interface Tick {
        void run() throws Exception;
    }

    /** A única Porta de Energia da Vitrine. */
    static BlockPos energyPort(BlockPos origin) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    if (VITRINE[x][y][z] == Part.ENERGY_PORT) {
                        return origin.offset(x, y, z);
                    }
                }
            }
        }
        throw new IllegalStateException("a Vitrine não tem Porta de Energia");
    }

    /**
     * O tempo médio de quadro em 120 quadros com a câmara ocupando a tela, sem o limite de FPS e sem o vsync (o
     * que valia volta ao fim), com o desenho antigo forçado ou com o plasma; a linha vai para o result.txt.
     */
    static Step frameTime(boolean legacy, String situation, Tick perTick) {
        long[] start = new long[2];
        String label = (legacy ? "camara antiga" : "camara plasma") + situation;
        double[] result = new double[1];
        return new Step("Ressonador: " + label + " (tempo de quadro)", 60_000, () -> {
            var options = Minecraft.getInstance().options;
            if (!measuring) {
                measuring = true;
                savedVsync = options.enableVsync().get();
                savedLimit = options.framerateLimit().get();
            }
            options.enableVsync().set(false);
            options.framerateLimit().set(260);
            ResonatorRenderer.forceLegacyForTests = legacy;
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
            restoreFrameSettings();
            return true;
        }, () -> String.format(java.util.Locale.ROOT, "%s: %.3f ms/quadro", label, result[0]));
    }

    /**
     * Devolve o vsync, o limite de FPS e o desenho da câmara. Idempotente; o motor chama em qualquer saída
     * (sucesso, falha ou tempo esgotado), sem gravar o options.txt.
     */
    static void restoreFrameSettings() {
        ResonatorRenderer.forceLegacyForTests = false;
        if (!measuring) {
            return;
        }
        measuring = false;
        var options = Minecraft.getInstance().options;
        options.enableVsync().set(savedVsync);
        options.framerateLimit().set(savedLimit);
    }

    /** Põe as peças da primeira foto ({@code half}) ou as que faltam para completar a Vitrine (a terra vira Carcaça). */
    private static void place(ServerLevel level, BlockPos origin, boolean half) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    if (VITRINE[x][y][z] != Part.AIR && halfBuilt(x, y, z) == half) {
                        level.setBlockAndUpdate(origin.offset(x, y, z),
                                looseState(VITRINE[x][y][z], x, y, z, RouterTier.ELITE));
                    }
                }
            }
        }
    }

    /**
     * A Vitrine inteira, com o Núcleo virado para o leste no tier dado e a janela na face sul; monta sozinha pelo
     * tick agendado do Núcleo. A vitrine do CurseForge usa a mesma.
     */
    static void placeVitrine(ServerLevel level, BlockPos origin, RouterTier tier) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    if (VITRINE[x][y][z] != Part.AIR) {
                        level.setBlockAndUpdate(origin.offset(x, y, z), looseState(VITRINE[x][y][z], x, y, z, tier));
                    }
                }
            }
        }
    }

    /**
     * A chave do texto na action bar agora, ou o texto cru se ele não é traduzível. O campo do {@link Gui} é privado
     * e só serve a este teste (o jogo de dev roda com os nomes da Mojang).
     */
    private static String overlayKey() {
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

    static BlockPos core(BlockPos origin) {
        return origin.offset(4, 2, 2);
    }

    /** O que a primeira foto já tem: o Núcleo, as carcaças das duas camadas de baixo e a janela menos dois vidros. */
    private static boolean halfBuilt(int x, int y, int z) {
        Part part = VITRINE[x][y][z];
        if (part == Part.CORE) {
            return true;
        }
        if (part == Part.CASING) {
            return y <= 1 && !isDirt(x, y, z);
        }
        if (part == Part.GLASS) {
            for (int[] gap : MISSING_GLASS) {
                if (gap[0] == x && gap[1] == y && gap[2] == z) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean isDirt(int x, int y, int z) {
        return x == DIRT[0] && y == DIRT[1] && z == DIRT[2];
    }

    /** O bloco solto de cada peça; o Núcleo e as peças de face nascem virados para fora, como a montagem os deixa. */
    private static BlockState looseState(Part part, int x, int y, int z, RouterTier tier) {
        Direction out = x == 0 ? Direction.WEST : x == 4 ? Direction.EAST : y == 0 ? Direction.DOWN
                : y == 4 ? Direction.UP : z == 0 ? Direction.NORTH : Direction.SOUTH;
        return switch (part) {
            case CASING -> ModBlocks.RESONATOR_CASING.get().defaultBlockState();
            case GLASS -> ModBlocks.RESONATOR_GLASS.get().defaultBlockState();
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
    private static void clear(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, 0, -2), origin.offset(6, 6, 6))) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** O jogador voando com os pés em {@code origin + feet}, olhando o centro da caixa. */
    static Step camera(String name, UUID playerId, java.util.function.Supplier<BlockPos> origin,
            double[] feet) {
        double[] cam = new double[3];
        float[] ang = new float[2];
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            BlockPos o = origin.get();
            cam[0] = o.getX() + feet[0];
            cam[1] = o.getY() + feet[1];
            cam[2] = o.getZ() + feet[2];
            double ex = o.getX() + 2.5 - cam[0];
            double ey = o.getY() + 2.3 - (cam[1] + 1.62);
            double ez = o.getZ() + 2.5 - cam[2];
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
