package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.client.fx.FxContext;
import io.github.matheusanbs.wirelessautomate.client.fx.FxJob;
import io.github.matheusanbs.wirelessautomate.client.fx.FxLights;
import io.github.matheusanbs.wirelessautomate.client.fx.FxSheets;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * O estado de uma Armilar no cliente, um por Núcleo, atualizado uma vez por tick novo no desenho: a sintonia e o giro
 * ({@link ArmilarMotion}), a vazão da Porta de Energia suavizada por tick, o clarão do fim da recarga, a luz do
 * conjunto e o referencial local-mundo. O relógio é o do mundo ({@code gameTime / 20}), igual em todos os clientes, e
 * a montagem corre do {@code formedAt} que o servidor mandou. Os arrays da {@link ChamberView} são lidos aqui, na view
 * nova, e nunca por quadro. É também o {@link FxJob} dos efeitos: o sol, os filamentos, o ponteiro e a conjunção.
 */
final class ArmilarScene implements FxJob {
    private static final double TICK_SECONDS = 0.05;
    /** O quanto a faixa de vazão anda por tick em direção ao alvo: acende e apaga em cerca de 1 s. */
    private static final float FLOW_RATE = 0.12f;
    /** O tempo desde algo que nunca aconteceu: tudo já aceso, nenhum clarão. */
    private static final double LONG_AGO = 1e9;
    /** O relógio do fluxo com o relógio parado do e2e: fixo, para as brasas do pé da Porta não mudarem a contagem entre as capturas. */
    private static final double FROZEN_FLOW_CLOCK = 1.3;
    private static final int SIZE = 5;
    /** A folga da caixa de desenho na horizontal, em blocos: o arco de saída passa de 3 blocos do centro e os decalques do chão vão a ~3. */
    private static final int BOX_MARGIN = 1;
    /** A maior faixa de eficiência: o {@code u} da sintonia é {@code band / BAND_MAX}. */
    private static final int BAND_MAX = 7;

    /** A luz some abaixo disto. */
    private static final double LIGHT_MIN = 0.001;
    /** O grão de redstone da recarga (pó coral do vanilla, tamanho 0,6). */
    private static final DustParticleOptions REDSTONE = new DustParticleOptions(new Vector3f(0.84f, 0.27f, 0.23f), 0.6f);
    /**
     * O meio lado do clarão no cristal ao fim da recarga, em px. Proposta: a spec e o filme não dão o tamanho (nem a
     * curva do alfa, {@code (1 − f)²}); ficam aqui, num lugar só, para a prancha decidir.
     */
    static final double FLASH_HALF = 4;

    private final BlockPos core;
    private final ArmilarMotion motion = new ArmilarMotion();
    private final ArmilarFrame frame = new ArmilarFrame();
    private final FlashTracker flash = new FlashTracker();
    private final double[] sunAt = new double[3];
    private final ArmilarGrains grains = new ArmilarGrains();
    /** Rascunhos dos grãos e do clarão dos cristais: só a thread de render os usa. */
    private final double[] grainFrom = new double[3];
    private final double[] grainTo = new double[3];
    private final double[] crystalAt = new double[3];
    /** As três luzes do quadro e o ponto do mundo de cada uma: rascunhos, só a thread de render os usa. */
    private final double[][] lights = new double[ArmilarLights.COUNT][8];
    private final double[] lightAt = new double[3];
    private final ArmilarPass pass = new ArmilarPass();
    private final BlockPos.MutableBlockPos sunPos = new BlockPos.MutableBlockPos();
    private AABB box;
    private ChamberView view = ChamberView.EMPTY;
    private byte[] chambers = new byte[0];
    private boolean full;
    private byte flowTarget;
    private float flowPrev;
    private float flowNow;
    /** O relógio do fluxo (s) no tick anterior e no de agora: soma o passo de cada tick vezes a velocidade da força. */
    private double flowClockPrev;
    private double flowClockNow;
    private long tunedAt = -1;
    private long gameTime;
    private int sunLight = LightTexture.FULL_BRIGHT;
    private float framePartialTick;
    private long frameGameTime;
    /** O tick em que a cena foi atualizada por último; o renderer só chama o {@link #update} se mudou. */
    long lastUsed;

    ArmilarScene(BlockPos core) {
        this.core = core.immutable();
        this.box = new AABB(core);
    }

    /** A face do mundo da frente do Núcleo ({@code ResonatorPartBlock.FACING}). */
    static ResonatorLayout.Face faceOf(Direction facing) {
        return switch (facing) {
            case SOUTH -> ResonatorLayout.Face.SOUTH;
            case WEST -> ResonatorLayout.Face.WEST;
            case EAST -> ResonatorLayout.Face.EAST;
            default -> ResonatorLayout.Face.NORTH;
        };
    }

    /** Uma vez por tick novo: a view nova entra e o tempo anda. */
    void update(ChamberView view, ResonatorLayout.Face front, long tunedAt, long gameTime, ClientLevel level) {
        if (view != this.view) {
            this.view = view;
            chambers = view.chambers();
            byte[] flows = view.flows();
            flowTarget = flows.length > 0 ? flows[0] : 0;
            full = view.full();
            int[] origin = view.origin();
            frame.set(front, origin);
            box = new AABB(origin[0] - BOX_MARGIN, origin[1], origin[2] - BOX_MARGIN, origin[0] + SIZE + BOX_MARGIN,
                    origin[1] + SIZE, origin[2] + SIZE + BOX_MARGIN);
            flash.onView(view, chambers, gameTime);
        }
        this.tunedAt = tunedAt;
        // os ticks desde o último desenho: o relógio do mundo seguiu, o giro e a vazão têm de ter acompanhado
        long elapsed = motion.started() ? Math.max(1, gameTime - lastUsed) : 1;
        this.gameTime = gameTime;
        boolean frozen = !Double.isNaN(ArmilarRenderer.freezeSecondsForTests);
        motion.catchUp(view.tuned() ? 1 : view.band() / (double) BAND_MAX, elapsed, clockAtTick(gameTime),
                frozen ? 0 : TICK_SECONDS);
        float goal = ArmilarRenderer.forceBandForTests >= 0 ? ArmilarRenderer.forceBandForTests : flowTarget;
        // o tick anterior ao de agora (flowPrev) e o de agora, em forma fechada; sem pulo se só passou um tick
        flowPrev = (float) ArmilarFlow.approach(flowNow, goal, FLOW_RATE, elapsed - 1);
        flowNow = flowPrev + (goal - flowPrev) * FLOW_RATE;
        // os ticks que ficaram para trás andam na velocidade do tick anterior; o de agora, na de agora
        flowClockPrev = flowClockNow + (elapsed - 1) * TICK_SECONDS * ArmilarFlow.speed(ArmilarFlow.strength(flowPrev));
        flowClockNow = flowClockPrev + TICK_SECONDS * ArmilarFlow.speed(ArmilarFlow.strength(flowNow));
        flash.tick(view, chambers, gameTime);
        tickGrains(level);
        frame.toWorld(0, ArmilarSpec.SOL_Y, 0, sunAt);
        sunPos.set(Mth.floor(sunAt[0]), Mth.floor(sunAt[1]), Mth.floor(sunAt[2]));
        sunLight = LevelRenderer.getLightColor(level, sunPos);
        lastUsed = gameTime;
    }

    /**
     * Os grãos de redstone: por tick novo, os vivos voam da Porta de Cristais ao centro do Opaco que recarrega, com a
     * rampa suave do progresso, e viram uma partícula de poeira de redstone no ponto.
     */
    private void tickGrains(ClientLevel level) {
        grains.tick(ArmilarRecharge.firstRecharging(chambers, view), chambers.length);
        double[] door = ArmilarSpec.PORTA_CRISTAIS;
        frame.toWorld(door[0], door[1], door[2], grainFrom);
        for (int g = 0; g < ArmilarGrains.MAX; g++) {
            if (!grains.alive(g) || grains.chamber(g) >= chambers.length) {
                continue;
            }
            crystalCenter(grains.chamber(g), 0f, grainTo);
            frame.toWorld(grainTo[0], grainTo[1], grainTo[2], grainTo);
            double k = Ease.ss(0, 1, grains.progress(g));
            level.addParticle(REDSTONE, grainFrom[0] + (grainTo[0] - grainFrom[0]) * k,
                    grainFrom[1] + (grainTo[1] - grainFrom[1]) * k, grainFrom[2] + (grainTo[2] - grainFrom[2]) * k, 0,
                    0, 0);
        }
    }

    /** O centro do cristal do nó {@code node} em px locais, com a pose do quadro {@code partialTick}. */
    private void crystalCenter(int node, float partialTick, double[] out) {
        double t = clock(partialTick);
        double u = motion.u(partialTick);
        double since = sinceTuned(partialTick);
        int orbit = ArmilarNodes.orbit(node);
        ArmilarNodes.center(node, ArmilarAssembly.ring(assembly(partialTick), orbit), ArmilarPose.prec(orbit, t),
                ArmilarPose.tilt(orbit, t, u, since), motion.spin(orbit, partialTick), out);
    }

    /** O clarão que fecha a recarga, em cima do cristal: o quadro e o alfa pelo tempo ({@code (1 − f)²}). */
    private void drawFlashes(ArmilarPass p) {
        for (int i = 0; i < chambers.length; i++) {
            float f = flash.flash(i, frameGameTime, framePartialTick);
            if (f < 0 || f >= 1) {
                continue;
            }
            ArmilarNodes.center(i, p.ring[ArmilarNodes.orbit(i)], p.prec[ArmilarNodes.orbit(i)],
                    p.tilt[ArmilarNodes.orbit(i)], p.spin[ArmilarNodes.orbit(i)], crystalAt);
            p.spr(FxSheets.CLARAO, FxSheets.CLARAO.frameByFraction(f), crystalAt[0], crystalAt[1], crystalAt[2],
                    FLASH_HALF, ArmilarPass.CLARO, (1 - f) * (1 - f), 0, 0);
        }
    }

    /** Os segundos do relógio no quadro: o do mundo, ou o parado do e2e. */
    double clock(float partialTick) {
        return Double.isNaN(ArmilarRenderer.freezeSecondsForTests) ? worldSeconds(gameTime, partialTick)
                : ArmilarRenderer.freezeSecondsForTests;
    }

    /** Os segundos do mundo no tick {@code gameTime} mais {@code partialTick}; a soma é em {@code double} (em {@code float} perde o parcial em mundo longo). */
    static double worldSeconds(long gameTime, float partialTick) {
        return (gameTime + (double) partialTick) * TICK_SECONDS;
    }

    /** O mesmo relógio no início do tick {@code tick}, para o laço de fase do {@link ArmilarMotion}. */
    private static double clockAtTick(long tick) {
        return Double.isNaN(ArmilarRenderer.freezeSecondsForTests) ? tick * TICK_SECONDS
                : ArmilarRenderer.freezeSecondsForTests;
    }

    /** O {@code a} da montagem; {@code formedAt <= 0} (montada de antes, ou nunca) vale {@link ArmilarAssembly#A_MAX}. */
    double assembly(float partialTick) {
        long formedAt = view.formedAt();
        return ArmilarAssembly.a(formedAt > 0 ? seconds(gameTime - formedAt, partialTick) : 0, formedAt <= 0);
    }

    /** Segundos desde que a lâmpada acendeu; sem sintonia vista, muito tempo. */
    double sinceTuned(float partialTick) {
        return tunedAt < 0 ? LONG_AGO : seconds(gameTime - tunedAt, partialTick);
    }

    static double seconds(long ticks, float partialTick) {
        return Math.max(0, (ticks + (double) partialTick) * TICK_SECONDS);
    }

    ArmilarMotion motion() {
        return motion;
    }

    ArmilarFrame frame() {
        return frame;
    }

    /** O que há em cada câmara, lido da view uma vez por view nova; não altere. */
    byte[] chambers() {
        return chambers;
    }

    /** O tier do Afinado da câmara {@code chamber} mais um, ou 0 sem Afinado. */
    int tier(int chamber) {
        return view.tierAt(chamber);
    }

    ChamberView view() {
        return view;
    }

    FlashTracker flash() {
        return flash;
    }

    /** A faixa de vazão suavizada da Porta de Energia 0, de 0 a 7, interpolada entre o tick anterior e o de agora. */
    float flow(float partialTick) {
        return Mth.lerp(partialTick, flowPrev, flowNow);
    }

    /** O relógio do fluxo, em segundos do filme, interpolado entre o tick anterior e o de agora (fixo com o relógio parado do e2e). */
    double flowClock(float partialTick) {
        if (!Double.isNaN(ArmilarRenderer.freezeSecondsForTests)) {
            return FROZEN_FLOW_CLOCK;
        }
        return flowClockPrev + (flowClockNow - flowClockPrev) * partialTick;
    }

    /** O buffer terminou o último lote cheio: a Armilar fica sob pressão. */
    boolean full() {
        return full;
    }

    /** A luz do mundo no sol, lida uma vez por tick novo: a das peças. */
    int sunLight() {
        return sunLight;
    }

    /** A caixa de desenho: a 5 × 5 × 5 da estrutura mais 1 bloco para cada lado na horizontal, ou o bloco do Núcleo antes da primeira view. */
    AABB box() {
        return box;
    }

    BlockPos core() {
        return core;
    }

    /** O tick da última atualização. */
    long gameTime() {
        return gameTime;
    }

    /** O quadro em que o job vai desenhar: o BER diz antes de anotá-lo na fila. */
    void setFrame(float partialTick, long gameTime) {
        framePartialTick = partialTick;
        frameGameTime = gameTime;
    }

    float framePartialTick() {
        return framePartialTick;
    }

    long frameGameTime() {
        return frameGameTime;
    }

    /**
     * Os efeitos do quadro: o sol (com as brasas), os filamentos, o ponteiro, o fluxo base (enquanto sai energia) e a
     * conjunção por cima (o estouro, o arco e o impacto, nessa ordem, que é a do filme) e o clarão dos cristais; a tinta de cada um sai antes da luz.
     */
    @Override
    public void draw(FxContext ctx, MultiBufferSource buffers) {
        pass.begin(this, ctx, buffers);
        if (pass.a <= 0) {
            return;
        }
        ArmilarSun.draw(pass);
        ArmilarFilaments.draw(pass);
        ArmilarPointer.draw(pass);
        ArmilarStream.draw(pass);
        ArmilarBeam.draw(pass);
        ArmilarArc.draw(pass);
        ArmilarImpact.draw(pass);
        drawFlashes(pass);
        addLights();
    }

    /** O sol, o clarão do horizonte e a Porta anotam a luz no mundo; só entra a que passa de {@link #LIGHT_MIN}. */
    private void addLights() {
        ArmilarLights.compute(pass.t, pass.a, pass.flowK, pass.phase, lights);
        for (double[] light : lights) {
            if (light[ArmilarLights.K] <= LIGHT_MIN) {
                continue;
            }
            frame.toWorld(light[ArmilarLights.X], light[ArmilarLights.Y], light[ArmilarLights.Z], lightAt);
            FxLights.add(lightAt[0], lightAt[1], lightAt[2], (float) light[ArmilarLights.R],
                    (float) light[ArmilarLights.G], (float) light[ArmilarLights.B], (float) light[ArmilarLights.K],
                    (float) (light[ArmilarLights.RADIUS] / 16));
        }
    }

    /** Os sprites e as fitas que o último quadro pediu (só o e2e lê). */
    int spriteCount() {
        return pass.sprites;
    }

    int ribbonCount() {
        return pass.ribbons;
    }

    @Override
    public double distanceSq(FxContext ctx) {
        double dx = frame.centerX() - ctx.camX;
        double dy = frame.centerY() + ArmilarSpec.SOL_Y / 16 - ctx.camY;
        double dz = frame.centerZ() - ctx.camZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
