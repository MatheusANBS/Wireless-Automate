package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eShowcaseWorld.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * O GIF "chiado → sintonia" da vitrine do CurseForge: uma Vitrine Esmeralda no jardim, a leste do caminho, com a
 * janela para o sul, e a câmera parada olhando por ela. Com o tempo congelado ({@code tickRateManager}), cada quadro
 * põe o dial do roteiro, avança um tick e salva {@code gif/ressonador-NNN.png}; o
 * {@code scripts/curseforge/gerar_gif.py} monta o GIF.
 *
 * <p>Roteiro (120 quadros, 20 fps): 25 quadros com o dial a {@value #START_MHZ} MHz da estação (chiado), 45 com o
 * desvio caindo pela curva {@code 1 - u²} (parte devagar e chega à lâmpada perto do fim da varredura, o clarão por
 * volta de 3,4 s) e o resto na estação. A estação é lida no servidor e fica presa nela a cada quadro: com o tempo
 * congelado o relógio do overworld não anda e o lote do Núcleo pode rodar várias vezes no mesmo tick, derivando.
 *
 * <p>Roda no fim da vitrine inteira ou sozinho, com {@code WA_SHOWCASE_ONLY=ressonador} (só a casa e os quadros).
 */
final class E2eShowcaseResonator {
    private static final int FRAMES = 120;
    private static final int NOISE_FRAMES = 25;
    private static final int SWEEP_FRAMES = 45;
    private static final double START_MHZ = 1.9;
    /** O canto da caixa, relativo à base da vitrine: x 5 a 9, z 2 a 6, no jardim a leste do caminho. */
    private static final int[] BOX = {5, 0, 2};
    /** Ticks do cliente depois do tick do quadro antes de capturar: deixa um quadro inteiro desenhar congelado. */
    private static final int SETTLE_TICKS = 2;

    private E2eShowcaseResonator() {
    }

    static void steps(List<Step> list) {
        double[] station = new double[1];
        // o guia, a última foto da vitrine, pausa o servidor integrado: sem fechar, nada monta
        list.add(new Step("Ressonador (GIF): fechar a tela", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                minecraft.screen.onClose();
            }
        }, () -> Minecraft.getInstance().screen == null, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("Ressonador (GIF): montar a Vitrine", STEP_TIMEOUT_MS, () -> {
            Minecraft.getInstance().getToasts().clear();
            onServer(server -> {
                E2eResonatorSteps.placeVitrine(server.overworld(), origin(), RouterTier.EMERALD);
                return null;
            });
        }, () -> {
            BlockState state = Minecraft.getInstance().level.getBlockState(core());
            return state.getBlock() instanceof ResonanceCoreBlock && state.getValue(ResonatorPartBlock.FORMED);
        }, () -> "Núcleo " + Minecraft.getInstance().level.getBlockState(core())));
        list.add(new Step("Ressonador (GIF): quatro cristais e o dial no chiado", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity core = core(server);
            for (int i = 0; i < core.chambers(); i++) {
                core.insertCrystal(new ItemStack(ModItems.SIGNAL_CRYSTAL.get()));
            }
            station[0] = core.station();
            core.setStationForTest(station[0]);
            core.setDial(station[0] + START_MHZ);
            return null;
        }), () -> {
            var view = ((ResonanceCoreBlockEntity) Minecraft.getInstance().level.getBlockEntity(core())).clientView();
            return view.chambers().length == 4 && view.chambers()[3] != 0 && !view.tuned();
        }, () -> "câmaras no cliente"));
        // a câmera a 3,5 blocos da janela, um pouco acima do centro, olhando o meio da câmara
        list.add(E2eShowcase.showLook("Ressonador (GIF): câmera na janela", BOX[0] + 2, 2.3, BOX[2] + 7, BOX[0] + 2,
                2.3, BOX[2] + 2, false));
        list.add(DevEndToEnd.wait("Ressonador (GIF): chiado assentado", 60));
        list.add(new Step("Ressonador (GIF): congelar o tempo", STEP_TIMEOUT_MS, () -> {
            clearFrames();
            onServer(server -> {
                server.tickRateManager().setFrozen(true);
                return null;
            });
            WirelessAutomate.LOGGER.info("vitrine: GIF com o tempo congelado, um tick por quadro");
        }, () -> !Minecraft.getInstance().level.tickRateManager().runsNormally(), () -> "cliente congelado"));
        for (int i = 0; i < FRAMES; i++) {
            list.add(frame(i, station));
        }
        list.add(new Step("Ressonador (GIF): descongelar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.tickRateManager().setFrozen(false);
            return null;
        }), () -> Minecraft.getInstance().level.tickRateManager().runsNormally(), () -> "cliente descongelado"));
    }

    /** O desvio do dial no quadro {@code frame}, em MHz acima da estação. */
    private static double deviation(int frame) {
        if (frame < NOISE_FRAMES) {
            return START_MHZ;
        }
        if (frame >= NOISE_FRAMES + SWEEP_FRAMES) {
            return 0;
        }
        double u = (frame - NOISE_FRAMES + 1) / (double) SWEEP_FRAMES;
        return START_MHZ * (1 - u * u);
    }

    /**
     * Um quadro: o dial do roteiro (no servidor, que manda a câmara no tick seguinte), um tick depois o passo de um
     * tick, e a captura {@value #SETTLE_TICKS} ticks do cliente depois de o tempo do jogo dele andar.
     */
    private static Step frame(int index, double[] station) {
        long[] before = {-1};
        int[] advancedAt = {-1};
        String file = String.format("gif/ressonador-%03d", index);
        return new Step("Ressonador (GIF): quadro " + index, STEP_TIMEOUT_MS, () -> {
            advancedAt[0] = -1;
            before[0] = -1;
            onServer(server -> {
                ResonanceCoreBlockEntity core = core(server);
                core.setStationForTest(station[0]);
                core.setDial(station[0] + deviation(index));
                return null;
            });
        }, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (before[0] < 0) {
                if (stepTicks < 2) {
                    return false;
                }
                long now = minecraft.level.getGameTime();
                // o servidor manda o tempo antes do tick do mundo a cada 20 ticks: no tick do passo, o tempo
                // velho chegaria depois e voltaria o relógio do cliente, e a câmara andaria duas vezes
                boolean stepped = onServer(server -> (server.getTickCount() + 1) % 20 != 0
                        && server.tickRateManager().stepGameIfPaused(1));
                if (stepped) {
                    before[0] = now;
                }
                return false;
            }
            if (advancedAt[0] < 0) {
                if (minecraft.level.getGameTime() > before[0]) {
                    advancedAt[0] = stepTicks;
                }
                return false;
            }
            if (stepTicks - advancedAt[0] < SETTLE_TICKS) {
                return false;
            }
            capture(minecraft, file);
            return true;
        }, () -> file + ".png, tempo " + Minecraft.getInstance().level.getGameTime() + " desde " + before[0]);
    }

    private static BlockPos origin() {
        return showBase.offset(BOX[0], BOX[1], BOX[2]);
    }

    private static BlockPos core() {
        return E2eResonatorSteps.core(origin());
    }

    private static ResonanceCoreBlockEntity core(MinecraftServer server) {
        return (ResonanceCoreBlockEntity) server.overworld().getBlockEntity(core());
    }

    /** Apaga os quadros de uma rodada anterior, para o script não misturar. */
    private static void clearFrames() throws IOException {
        Path dir = Path.of(SHOWCASE, "gif");
        Files.createDirectories(dir);
        try (DirectoryStream<Path> old = Files.newDirectoryStream(dir, "ressonador-*.png")) {
            for (Path path : old) {
                Files.delete(path);
            }
        }
    }
}
