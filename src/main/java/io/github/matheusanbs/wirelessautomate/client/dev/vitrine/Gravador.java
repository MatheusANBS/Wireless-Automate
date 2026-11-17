package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Step;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Grava os quadros de uma cena em {@code <WA_VITRINE>/<cena>/NNNN.png} e, no fim, o {@code quadros.csv}
 * ({@code arquivo;ms;cursorX;cursorY;idadeDoClique}, o cursor em pixels do quadro ou {@code -1}). O jogo roda em tempo
 * real, com o servidor e o shaderpack de verdade; o quadro sai no fim de cada desenho ({@link RenderFrameEvent.Post}) e
 * leva o instante em que foi tirado, e o {@code gif.py} reparte os atrasos por ele: o ritmo do GIF é o do jogo, mesmo
 * que a máquina engasgue num quadro. A leitura dos pixels é na hora; os PNGs são escritos em quatro threads à parte.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
final class Gravador {
    /** O intervalo mínimo entre dois quadros (cerca de 30 por segundo). */
    private static final long INTERVALO_NS = 31_000_000L;
    /** Quadros esperando para ser escritos acima disto: pula o quadro (a escrita não acompanha). */
    private static final int FILA_MAXIMA = 90;
    private static final ExecutorService ESCRITOR = Executors.newFixedThreadPool(4, r -> {
        Thread thread = new Thread(r, "vitrine-escritor");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicInteger FILA = new AtomicInteger();
    private static final List<String> LINHAS = new ArrayList<>();
    private static boolean gravando;
    private static Path pasta;
    private static long inicio;
    private static long ultimo;
    private static int numero;

    private Gravador() {
    }

    /** Esvazia a pasta da cena e começa a gravar. */
    static Step iniciar(String cena) {
        return Passos.agir("gravar " + cena, () -> {
            pasta = Path.of(Vitrine.DIR, cena);
            if (Files.exists(pasta)) {
                try (var arquivos = Files.walk(pasta)) {
                    for (Path arquivo : arquivos.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(arquivo);
                    }
                }
            }
            Files.createDirectories(pasta);
            LINHAS.clear();
            numero = 0;
            inicio = System.nanoTime();
            ultimo = 0;
            gravando = true;
        });
    }

    /** Para, espera a escrita acabar e grava o {@code quadros.csv}. */
    static Step parar() {
        return Passos.ate("parar a gravação", () -> {
            gravando = false;
        }, () -> {
            if (FILA.get() > 0) {
                return false;
            }
            Files.write(pasta.resolve("quadros.csv"), LINHAS);
            WirelessAutomate.LOGGER.info("vitrine: {} quadros em {}", numero, pasta);
            return true;
        });
    }

    /** Uma foto do quadro de agora em {@code <WA_VITRINE>/<nome>.png} (o chão visto de cima, para conferir a praça). */
    static Step foto(String nome) {
        return Passos.agir("foto " + nome, () -> {
            try (NativeImage imagem = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                Files.createDirectories(Path.of(Vitrine.DIR));
                imagem.writeToFile(Path.of(Vitrine.DIR, nome + ".png"));
            }
        });
    }

    @SubscribeEvent
    static void aoDesenhar(RenderFrameEvent.Post evento) {
        long agora = System.nanoTime();
        if (!gravando || agora - ultimo < INTERVALO_NS || FILA.get() > FILA_MAXIMA) {
            return;
        }
        ultimo = agora;
        NativeImage imagem = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
        String arquivo = String.format(Locale.ROOT, "%04d.png", numero++);
        double[] cursor = Cursor.quadro();
        LINHAS.add(String.format(Locale.ROOT, "%s;%d;%.1f;%.1f;%.0f", arquivo, (agora - inicio) / 1_000_000,
                cursor == null ? -1 : cursor[0], cursor == null ? -1 : cursor[1], cursor == null ? 9999 : cursor[2]));
        FILA.incrementAndGet();
        Path destino = pasta.resolve(arquivo);
        ESCRITOR.execute(() -> {
            try (imagem) {
                imagem.writeToFile(destino);
            } catch (IOException e) {
                WirelessAutomate.LOGGER.error("vitrine: falha ao gravar {}", destino, e);
            } finally {
                FILA.decrementAndGet();
            }
        });
    }
}
