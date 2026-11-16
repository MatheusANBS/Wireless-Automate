package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.client.fx.FxContext;
import io.github.matheusanbs.wirelessautomate.client.fx.FxJob;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * O estado de uma Armilar no cliente, um por Núcleo, atualizado uma vez por tick novo no desenho: a sintonia e o giro
 * ({@link ArmilarMotion}), a vazão da Porta de Energia suavizada por tick, o clarão do fim da recarga, a luz do
 * conjunto e o referencial local-mundo. O relógio é o do mundo ({@code gameTime / 20}), igual em todos os clientes, e
 * a montagem corre do {@code formedAt} que o servidor mandou. Os arrays da {@link ChamberView} são lidos aqui, na view
 * nova, e nunca por quadro. É também o {@link FxJob} dos efeitos, que as Tarefas 8 e 9 preenchem.
 */
final class ArmilarScene implements FxJob {
    private static final double TICK_SECONDS = 0.05;
    /** O quanto a faixa de vazão anda por tick em direção ao alvo: acende e apaga em cerca de 1 s. */
    private static final float FLOW_RATE = 0.12f;
    /** O tempo desde algo que nunca aconteceu: tudo já aceso, nenhum clarão. */
    private static final double LONG_AGO = 1e9;
    private static final int SIZE = 5;
    /** A folga da caixa de desenho na horizontal, em blocos: o arco de saída passa de 3 blocos do centro e os decalques do chão vão a ~3. */
    private static final int BOX_MARGIN = 1;
    /** A maior faixa de eficiência: o {@code u} da sintonia é {@code band / BAND_MAX}. */
    private static final int BAND_MAX = 7;

    private final BlockPos core;
    private final ArmilarMotion motion = new ArmilarMotion();
    private final ArmilarFrame frame = new ArmilarFrame();
    private final FlashTracker flash = new FlashTracker();
    private final double[] sunAt = new double[3];
    private final BlockPos.MutableBlockPos sunPos = new BlockPos.MutableBlockPos();
    private AABB box;
    private ChamberView view = ChamberView.EMPTY;
    private byte[] chambers = new byte[0];
    private boolean full;
    private byte flowTarget;
    private float flowPrev;
    private float flowNow;
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
        this.gameTime = gameTime;
        motion.advance(view.tuned() ? 1 : view.band() / (double) BAND_MAX, clockAtTick(gameTime));
        float goal = ArmilarRenderer.forceBandForTests >= 0 ? ArmilarRenderer.forceBandForTests : flowTarget;
        flowPrev = flowNow;
        flowNow += (goal - flowNow) * FLOW_RATE;
        flash.tick(view, chambers, gameTime);
        frame.toWorld(0, ArmilarSpec.SOL_Y, 0, sunAt);
        sunPos.set(Mth.floor(sunAt[0]), Mth.floor(sunAt[1]), Mth.floor(sunAt[2]));
        sunLight = LevelRenderer.getLightColor(level, sunPos);
        lastUsed = gameTime;
    }

    /** Os segundos do relógio no quadro: o do mundo, ou o parado do e2e. */
    double clock(float partialTick) {
        return Double.isNaN(ArmilarRenderer.freezeSecondsForTests) ? (gameTime + partialTick) * TICK_SECONDS
                : ArmilarRenderer.freezeSecondsForTests;
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

    private static double seconds(long ticks, float partialTick) {
        return Math.max(0, (ticks + partialTick) * TICK_SECONDS);
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

    @Override
    public void draw(FxContext ctx, MultiBufferSource buffers) {
    }

    @Override
    public double distanceSq(FxContext ctx) {
        double dx = frame.centerX() - ctx.camX;
        double dy = frame.centerY() + ArmilarSpec.SOL_Y / 16 - ctx.camY;
        double dz = frame.centerZ() - ctx.camZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
