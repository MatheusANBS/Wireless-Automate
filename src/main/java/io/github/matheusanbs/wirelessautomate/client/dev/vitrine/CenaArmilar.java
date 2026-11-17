package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Step;
import io.github.matheusanbs.wirelessautomate.client.dev.E2eResonatorSteps;
import io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.ArmilarSpec;
import io.github.matheusanbs.wirelessautomate.client.resonator.screen.ResonatorScreen;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.lwjgl.glfw.GLFW;

/**
 * A Armilar numa praça de ardósia, em quatro filmes curtos do mesmo lugar, um por momento: {@code armilar-montagem} (a
 * última peça, o Núcleo, e a animação do fim da montagem), {@code armilar-fora} (as órbitas abertas, fora de
 * sintonia), {@code armilar-sintonia} (o dial chegando na estação e as órbitas se alinhando) e
 * {@code armilar-conjuncao} (a conjunção, o arco até a Porta de Energia e o feixe contínuo, o laço de um
 * {@link ArmilarSpec#PERIODO}). A câmera balança num seno inteiro em cada um, então o último quadro emenda no
 * primeiro, e vem de {@link Camera#mover} (a pose sai do relógio de cada quadro, sem tremer com os ticks).
 */
final class CenaArmilar {
    static final String NOME = "armilar";
    private static final RouterTier TIER = RouterTier.EMERALD;
    /** A câmera do filme da Armilar: distância (blocos), elevação e rumo central (graus), balanço e campo de visão. */
    private static final double DISTANCIA = 215.0 / 16;
    private static final double ELEVACAO = 34;
    private static final double RUMO = 38;
    private static final double BALANCO = 22;
    private static final int CAMPO = 30;
    /** O campo de visão e a distância da câmera da sintonia: perto o bastante para clicar no Núcleo. */
    private static final int CAMPO_PERTO = 68;
    private static final double PERTO_CAMERA = 5.4;
    /** A estação do Núcleo e o dial fora de sintonia, a 1,3 MHz dela. */
    private static final double ESTACAO = 98.0;
    private static final double FORA = 96.7;
    /** O dial no começo da sintonia: 1,1 MHz abaixo da estação (um » e dois ›). */
    private static final double PERTO = 96.9;

    private static BlockState nucleoEstado;

    private CenaArmilar() {
    }

    static void passos(List<Step> lista, int indice) {
        Supplier<BlockPos> origem = () -> Palco.base(indice);
        Supplier<BlockPos> nucleo = () -> E2eResonatorSteps.core(origem.get());
        Supplier<double[]> alvo = () -> new double[] {origem.get().getX() + 2.5, origem.get().getY() + 30.0 / 16,
                origem.get().getZ() + 2.5};
        lista.add(Palco.montar(NOME, nivel -> {
            BlockPos o = origem.get();
            Palco.praca(nivel, origem.get().offset(2, -1, 2), 96, 96);
        }, () -> origem.get().below()));
        Palco.chao(lista, NOME, () -> origem.get().offset(2, -1, 2), 110);
        lista.add(Passos.ate("câmera na praça", () -> {
            Camera.campo(CAMPO);
            Camera.saltar(Camera.orbita(alvo.get(), DISTANCIA, RUMO, ELEVACAO), alvo.get());
        }, () -> Camera.em(Camera.orbita(alvo.get(), DISTANCIA, RUMO, ELEVACAO))));
        lista.add(Passos.esperar(1500));
        // 1. a montagem: a Armilar sem o Núcleo já posta, e o Núcleo por último, 0,9 s depois do começo
        lista.add(Passos.ate("Armilar sem o Núcleo", () -> E2eWorld.onServer(servidor -> {
            E2eResonatorSteps.placeArmilar(servidor.overworld(), origem.get(), TIER);
            nucleoEstado = servidor.overworld().getBlockState(nucleo.get());
            servidor.overworld().setBlockAndUpdate(nucleo.get(), Blocks.AIR.defaultBlockState());
            return null;
        }), () -> !Minecraft.getInstance().level.getBlockState(E2eResonatorSteps.energyPort(origem.get())).isAir()));
        lista.add(Passos.esperar(1500));
        gravar(lista, "armilar-montagem", 3600, t -> balanco(alvo.get(), t));
        lista.add(Passos.esperar(900));
        lista.add(Passos.agir("o Núcleo, a última peça", () -> E2eWorld.onServer(servidor -> {
            servidor.overworld().setBlockAndUpdate(nucleo.get(), nucleoEstado);
            return null;
        })));
        lista.add(Passos.esperar(2700));
        lista.add(Gravador.parar());
        lista.add(Passos.ate("Armilar formada", () -> {
            var estado = Minecraft.getInstance().level.getBlockState(nucleo.get());
            return estado.getBlock() instanceof ResonanceCoreBlock && estado.getValue(ResonatorPartBlock.FORMED);
        }));
        lista.add(Passos.agir("conferir as peças da Armilar", () -> conferir(origem.get())));
        lista.add(Passos.esperar(2500));
        // 2. fora de sintonia: cristais comuns e o dial longe da estação, a câmera dando a volta
        lista.add(Passos.ate("cristais fora de sintonia", () -> E2eWorld.onServer(servidor -> {
            ResonanceCoreBlockEntity core = core(servidor, nucleo.get());
            for (int i = 0; i < core.chambers(); i++) {
                core.setCrystalAt(i, new ItemStack(i % 2 == 0 ? ModItems.SIGNAL_CRYSTAL.get()
                        : ModItems.CUT_SIGNAL_CRYSTAL.get()));
            }
            core.setStationForTest(ESTACAO);
            core.setDial(FORA);
            return null;
        }), () -> {
            var vista = ((ResonanceCoreBlockEntity) Minecraft.getInstance().level.getBlockEntity(nucleo.get()))
                    .clientView();
            return vista.chambers().length == 4 && vista.chambers()[3] != 0 && !vista.tuned();
        }));
        lista.add(Passos.esperar(3000));
        gravar(lista, "armilar-fora", 8000, t -> baixa(alvo.get(), t));
        lista.add(Passos.esperar(8000));
        lista.add(Gravador.parar());
        // 3. a sintonia: o dial um pouco fora, a tela do Núcleo e os cliques até a estação, as órbitas alinhando
        lista.add(Passos.agir("dial perto da estação", () -> dial(nucleo.get(), PERTO)));
        lista.add(Passos.esperar(1500));
        lista.add(Passos.agir("campo aberto para chegar perto", () -> Camera.campo(CAMPO_PERTO)));
        gravar(lista, "armilar-sintonia", 11_000, t -> volta(alvo.get(), t));
        lista.add(Passos.esperar(1500));
        lista.add(Palco.usar("abrir o Núcleo", nucleo, () -> Minecraft.getInstance().screen instanceof ResonatorScreen
                tela && tela.getMenu().state() != null && tela.getMenu().pos().equals(nucleo.get())));
        lista.add(Cursor.centralizar());
        Cursor.tocar(lista, () -> tela().dialBigPlusCenter(), 500, 450);
        for (int i = 0; i < 2; i++) {
            Cursor.tocar(lista, () -> tela().dialPlusCenter(), 320, 380);
        }
        lista.add(Passos.ate("em sintonia", () -> tela().getMenu().state().tuned()));
        lista.add(Passos.esperar(1100));
        lista.add(Passos.agir("fechar a tela", () -> tela().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0)));
        lista.add(Passos.esperar(3800));
        lista.add(Gravador.parar());
        lista.add(Passos.agir("campo de volta ao fechado", () -> Camera.campo(CAMPO)));
        // 4. a conjunção, com o consumidor tirando da Porta (a vazão leva uns segundos para subir)
        lista.add(Passos.durante("vazão", 9_000, t -> extrair(origem.get())));
        lista.add(Passos.ate("antes da conjunção", () -> {
            extrair(origem.get());
            double tau = ArmilarSpec.tau(Minecraft.getInstance().level.getGameTime() * 0.05);
            return tau >= -1.0 && tau < -0.9;
        }));
        long periodo = Math.round(ArmilarSpec.PERIODO * 1000);
        gravar(lista, "armilar-conjuncao", periodo, t -> baixa(alvo.get(), t));
        lista.add(Passos.durante("conjunção", periodo, t -> extrair(origem.get())));
        lista.add(Gravador.parar());
        lista.add(Passos.agir("campo de visão de volta", Camera::restaurarCampo));
    }

    /** Leva a câmera ao começo da curva e começa a gravar {@code nome}, com ela conduzida por {@code camera} por {@code ms}. */
    private static void gravar(List<Step> lista, String nome, long ms, DoubleFunction<double[][]> camera) {
        lista.add(Passos.ate("câmera no começo de " + nome, () -> Camera.saltar(camera.apply(0)[0], camera.apply(0)[1]),
                () -> Camera.em(camera.apply(0)[0])));
        lista.add(Passos.esperar(400));
        lista.add(Gravador.iniciar(nome));
        lista.add(Camera.mover(ms, camera));
    }

    /** A câmera de cima, balançando num seno inteiro (o laço emenda). */
    private static double[][] balanco(double[] alvo, double t) {
        return new double[][] {Camera.orbita(alvo, DISTANCIA, RUMO + BALANCO * Math.sin(2 * Math.PI * t), ELEVACAO), alvo};
    }

    /** A órbita baixa: o olho a pouco mais de um bloco do chão, olhando para um ponto acima da Armilar, uma volta por laço. */
    private static double[][] baixa(double[] alvo, double t) {
        double[] mira = {alvo[0], alvo[1] + 1.6, alvo[2]};
        return new double[][] {Camera.orbita(mira, DISTANCIA, RUMO + 360 * t, -10), mira};
    }

    /** Um arco lento pelo lado sul (o do Núcleo), de começo e fim suaves, a meia altura e ao alcance do clique. */
    private static double[][] volta(double[] alvo, double t) {
        double[] mira = {alvo[0], alvo[1] + 1.2, alvo[2]};
        return new double[][] {Camera.orbita(mira, PERTO_CAMERA, -40 + 80 * Passos.suave(t), 12), mira};
    }

    /** Toda célula do desenho da Armilar (25 Lajes, 8 Colunas, 2 Berços e o resto) tem bloco no cliente. */
    private static void conferir(BlockPos origem) {
        var projeto = ResonatorBlueprint.expected(ResonatorLayout.Face.SOUTH);
        int faltam = 0;
        int total = 0;
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    if (projeto[x][y][z] != ResonatorLayout.Part.AIR) {
                        total++;
                        faltam += Minecraft.getInstance().level.getBlockState(origem.offset(x, y, z)).isAir() ? 1 : 0;
                    }
                }
            }
        }
        if (faltam > 0) {
            throw new IllegalStateException("faltam " + faltam + " de " + total + " peças da Armilar");
        }
    }

    private static ResonatorScreen tela() {
        return (ResonatorScreen) Minecraft.getInstance().screen;
    }

    private static void dial(BlockPos nucleo, double mhz) {
        MinecraftServer servidor = Minecraft.getInstance().getSingleplayerServer();
        servidor.execute(() -> core(servidor, nucleo).setDial(mhz));
    }

    /** O consumidor tirando da Porta. */
    private static void extrair(BlockPos origem) {
        MinecraftServer servidor = Minecraft.getInstance().getSingleplayerServer();
        servidor.execute(() -> {
            IEnergyStorage energia = servidor.overworld().getCapability(Capabilities.EnergyStorage.BLOCK,
                    E2eResonatorSteps.energyPort(origem), null);
            if (energia != null) {
                energia.extractEnergy(Integer.MAX_VALUE, false);
            }
        });
    }

    private static ResonanceCoreBlockEntity core(MinecraftServer servidor, BlockPos nucleo) {
        return (ResonanceCoreBlockEntity) servidor.overworld().getBlockEntity(nucleo);
    }
}
