package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.client.fx.FxContext;
import io.github.matheusanbs.wirelessautomate.client.fx.FxDraw;
import io.github.matheusanbs.wirelessautomate.client.fx.FxRibbon;
import io.github.matheusanbs.wirelessautomate.client.fx.FxSheet;
import io.github.matheusanbs.wirelessautomate.client.fx.FxTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * O contexto do desenho dos efeitos de uma cena num quadro, um por {@link ArmilarScene} e reaproveitado: o relógio, a
 * montagem, a sintonia, a fase da conjunção, a pose de cada órbita e os ajudantes {@code spr}, {@code dec} e
 * {@code ribbon} do filme ({@code shared.js}), que recebem pontos locais em px e escrevem vértices relativos à câmera,
 * em blocos. Nada é alocado por quadro: os rascunhos e os dois buffers de fita são campos.
 *
 * <p>A dissolução: o parâmetro {@code dis} de {@code spr}, {@code dec} e {@code ribbon} é inteiro, de 0 (inteiro) a
 * 1000 (some), que é o que o {@code FxDraw} manda ao shader na trama Bayer. O {@code phase.dis} do filme vai de 0 a 1,
 * então quem desenha passa {@link #disMilli()}.
 */
final class ArmilarPass {
    static final int VINHO = 0x87301F;
    static final int OURO = 0xFFD142;
    static final int CLARO = 0xFFF2D9;
    static final int WHITE = 0xFFFFFF;
    static final int PLASMA = 0xFF8A6B;
    static final int CORAL_HI = 0xFFB39E;
    static final int CORAL = 0xF0603E;
    /** O {@code [1; 0,62; 0,5]} do filme. */
    static final int PONTEIRO = 0xFF9E80;

    final ArmilarPhase phase = new ArmilarPhase();
    ArmilarScene scene;
    FxContext fx;
    MultiBufferSource buffers;
    double t;
    double a;
    double u;
    /** O que o sol multiplica: o {@code ig} do filme (entrada do sol) vezes a eficiência. */
    double ig;
    double enc;
    double prepEff;
    double eff;
    /** A força da vazão da Porta de Energia neste quadro, de 0 a 1 ({@link ArmilarFlow#strength} vezes a ignição da montagem): escala o fluxo base e o pulso do arco e do impacto. */
    double flowK;
    /** O relógio do fluxo neste quadro, em segundos do filme: anda com a velocidade da força ({@link ArmilarFlow#speed}), sem pulo quando a vazão muda. */
    double fc;
    float sunX;
    float sunY;
    float sunZ;
    /**
     * Quantos sprites (spr, dec e sprCam) e fitas esta cena pôs de fato no buffer no último quadro (os de alfa nulo
     * não contam); o e2e confere que nada sumiu.
     */
    int sprites;
    int ribbons;

    /** A pose de cada órbita neste quadro: precessão, inclinação, giro e a escala da entrada. */
    final double[] prec = new double[3];
    final double[] tilt = new double[3];
    final double[] spin = new double[3];
    final double[] ring = new double[3];

    /** Os pontos locais (px) de uma fita, preenchidos por quem desenha; {@link #ribbon} os leva à câmera. */
    final FxRibbon.Buffer local = new FxRibbon.Buffer();
    private final FxRibbon.Buffer cam = new FxRibbon.Buffer();
    private final double[] world = new double[3];
    private final double[] axisA = new double[3];
    private final double[] axisB = new double[3];
    /** Rascunhos do {@link #screenAngle}; só a thread de render os usa. */
    private final float[] ndcA = new float[2];
    private final float[] ndcB = new float[2];

    /**
     * Pega o quadro: o relógio, a montagem, a pose e a posição do sol. O {@code partialTick} é o do BER, o mesmo das
     * peças sólidas (o do contexto só serve à câmera), para os efeitos não andarem fora de fase delas.
     */
    void begin(ArmilarScene scene, FxContext fx, MultiBufferSource buffers) {
        this.scene = scene;
        this.fx = fx;
        this.buffers = buffers;
        sprites = 0;
        ribbons = 0;
        float pt = scene.framePartialTick();
        t = scene.clock(pt);
        a = scene.assembly(pt);
        u = scene.motion().u(pt);
        // a conjunção só existe em sintonia (spec)
        phase.set(t, scene.view().tuned() ? u : 0);
        eff = ArmilarFlow.sunMul(u);
        flowK = ArmilarFlow.ignited(ArmilarFlow.strength(scene.flow(pt)), ArmilarAssembly.ignition(a));
        fc = scene.flowClock(pt);
        prepEff = Math.max(phase.prep, scene.full() ? ArmilarPhase.fullPrep(t) : 0);
        ig = ArmilarAssembly.ignition(a) * eff;
        enc = 1 - 0.28 * prepEff + 0.18 * phase.raio;
        double since = scene.sinceTuned(pt);
        for (int i = 0; i < 3; i++) {
            prec[i] = ArmilarPose.prec(i, t);
            tilt[i] = ArmilarPose.tilt(i, t, u, since);
            spin[i] = scene.motion().spin(i, pt);
            ring[i] = ArmilarAssembly.ring(a, i);
        }
        scene.frame().toWorld(0, ArmilarSpec.SOL_Y, 0, world);
        sunX = (float) (world[0] - fx.camX);
        sunY = (float) (world[1] - fx.camY);
        sunZ = (float) (world[2] - fx.camZ);
    }

    /** A dissolução da conjunção em milésimos (0 a 1000), a unidade do {@code dis} dos desenhos. */
    int disMilli() {
        return (int) Math.round(phase.dis * 1000);
    }

    /**
     * O ângulo na tela do risco que vai da cauda {@code tail} à cabeça {@code head} (px locais): o
     * {@code atan2(screen(hp).y − screen(tr).y, screen(tr).x − screen(hp).x)} do filme, que mede em pixels com o y
     * para baixo. Em coordenadas normalizadas (y para cima) é {@code atan2(−ΔY × H, −ΔX × W)}, com Δ = cabeça − cauda e
     * {@code W}, {@code H} o tamanho da janela (o fator 1/2 do normalizado sai nos dois).
     */
    double screenAngle(double[] head, double[] tail) {
        ndc(head, ndcA);
        ndc(tail, ndcB);
        var window = Minecraft.getInstance().getWindow();
        return Math.atan2(-(ndcA[1] - ndcB[1]) * window.getHeight(), -(ndcA[0] - ndcB[0]) * window.getWidth());
    }

    /** O ponto local (px) em coordenadas normalizadas da tela. */
    private void ndc(double[] local, float[] out) {
        scene.frame().toWorld(local[0], local[1], local[2], world);
        fx.ndc((float) (world[0] - fx.camX), (float) (world[1] - fx.camY), (float) (world[2] - fx.camZ), out);
    }

    /** A luz da folha: cheia nas de luz, a do mundo no sol nas de tinta. */
    private int light(FxSheet sheet) {
        return sheet.layer() == FxSheet.Layer.LIGHT ? LightTexture.FULL_BRIGHT : scene.sunLight();
    }

    /**
     * O {@code spr} do filme: o sprite que olha para a câmera num ponto local (px); {@code halfPx} é o meio lado. O
     * {@code rot} (que cresce com o relógio) é reduzido a {@code (−π, π]} em {@code double} antes do {@code float}.
     */
    void spr(FxSheet sheet, int frame, double lx, double ly, double lz, double halfPx, int rgb, double alpha,
            double rot, int dis) {
        scene.frame().toWorld(lx, ly, lz, world);
        sprCam(sheet, frame, (float) (world[0] - fx.camX), (float) (world[1] - fx.camY),
                (float) (world[2] - fx.camZ), halfPx, rgb, alpha, rot, dis);
    }

    /** O mesmo sprite num ponto já relativo à câmera, em blocos (a coroa do sol anda em eixos da tela). */
    void sprCam(FxSheet sheet, int frame, float x, float y, float z, double halfPx, int rgb, double alpha,
            double rot, int dis) {
        if (FxDraw.sprite(buffers.getBuffer(FxTypes.of(sheet)), fx, sheet, frame, x, y, z, (float) (halfPx / 16),
                Ease.wrap(rot), rgb, (float) alpha, dis, light(sheet))) {
            sprites++;
        }
    }

    /** O {@code dec} do filme: o quad nos eixos locais dados (vetores unitários), colado numa superfície. */
    void dec(FxSheet sheet, int frame, double lx, double ly, double lz, double[] axis1, double[] axis2,
            double halfPx, int rgb, double alpha, double rot, int dis) {
        scene.frame().toWorld(lx, ly, lz, world);
        // o dirToWorld divide por 16 (px a blocos); o eixo é unitário, então multiplica por 16 para voltar a 1
        scene.frame().dirToWorld(axis1[0] * 16, axis1[1] * 16, axis1[2] * 16, axisA);
        scene.frame().dirToWorld(axis2[0] * 16, axis2[1] * 16, axis2[2] * 16, axisB);
        if (FxDraw.decal(buffers.getBuffer(FxTypes.of(sheet)), sheet, frame, (float) (world[0] - fx.camX),
                (float) (world[1] - fx.camY), (float) (world[2] - fx.camZ), (float) axisA[0], (float) axisA[1],
                (float) axisA[2], (float) axisB[0], (float) axisB[1], (float) axisB[2], (float) (halfPx / 16),
                Ease.wrap(rot), rgb, (float) alpha, dis, light(sheet))) {
            sprites++;
        }
    }

    /**
     * A {@code fita} do filme: {@code b} (o {@link #local}) tem os {@code n} pontos e as meias larguras em px locais e o
     * alfa por amostra; aqui eles vão à câmera, em blocos. {@code repPx} é o comprimento de uma repetição da textura
     * e {@code offset} o deslocamento dela, em repetições; o {@code offset} (que cresce com o relógio) é reduzido a
     * {@code [0, 1)} em {@code double} antes do {@code float}: com o relógio em 1e6 s o {@code float} cru não teria
     * casas para o fluxo andar.
     */
    void ribbon(FxSheet sheet, int frame, FxRibbon.Buffer b, int n, int rgb, double repPx, double offset, int dis) {
        if (n < 2) {
            return;
        }
        float[] src = b.pts;
        float[] dst = cam.pts;
        for (int i = 0; i < n; i++) {
            int i3 = i * 3;
            scene.frame().toWorld(src[i3], src[i3 + 1], src[i3 + 2], world);
            dst[i3] = (float) (world[0] - fx.camX);
            dst[i3 + 1] = (float) (world[1] - fx.camY);
            dst[i3 + 2] = (float) (world[2] - fx.camZ);
            cam.w[i] = b.w[i] / 16f;
            cam.a[i] = b.a[i];
        }
        if (FxDraw.ribbon(buffers.getBuffer(FxTypes.of(sheet)), fx, sheet, frame, cam, n, rgb, (float) (repPx / 16),
                (float) Ease.frac(offset), dis, light(sheet))) {
            ribbons++;
        }
    }
}
