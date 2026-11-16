package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.fx.FxContext;
import io.github.matheusanbs.wirelessautomate.client.fx.FxDraw;
import io.github.matheusanbs.wirelessautomate.client.fx.FxJob;
import io.github.matheusanbs.wirelessautomate.client.fx.FxQueue;
import io.github.matheusanbs.wirelessautomate.client.fx.FxRibbon;
import io.github.matheusanbs.wirelessautomate.client.fx.FxSheets;
import io.github.matheusanbs.wirelessautomate.client.fx.FxShaders;
import io.github.matheusanbs.wirelessautomate.client.fx.FxTypes;
import io.github.matheusanbs.wirelessautomate.client.fx.ShaderPackCheck;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A sonda do caminho de desenho dos efeitos (suíte opcional {@code fx-base}): um brilho e uma fita de teste, no mundo
 * plano, sem a Armilar. Três capturas:
 * <ol>
 *   <li>{@code fx-base-oclusao}: o job atrás de uma parede de pedra 3 × 3 × 1, de modo que o brilho sai cortado pela
 *   borda da parede e a fita passa atrás dela (a oclusão, com e sem shaderpack);</li>
 *   <li>{@code fx-base-perto}: o job a 2 blocos do jogador, sem nada na frente (a cor, a forma e o degradê de alfa
 *   sem halo escuro);</li>
 *   <li>{@code fx-base-longe}: o jogador a 60 blocos do job, que não pode ser desenhado (o corte de 48).</li>
 * </ol>
 * O job entra na fila a cada quadro por um ouvinte do {@code RenderFrameEvent.Pre} ligado só enquanto a suíte roda
 * (e desligado em qualquer saída, pelo {@link #restoreHooks()}), e não entra na passada de sombra.
 */
final class E2eFxProbeSteps {
    /** O canto da parede, em relação ao jogador: longe das outras suítes. */
    private static final int[] WALL_AT = {-24, 0, 20};
    /** O centro do job atrás da parede (em relação ao canto da parede): a borda da parede passa pelo meio do brilho. */
    private static final double[] BEHIND = {4.5, 1.5, -4.0};
    /** Os pés do jogador na foto da oclusão: 6 blocos à frente da parede, olhando o centro do job. */
    private static final double[] OCCLUSION_FEET = {1.5, 0.0, 6.0};
    /** Os pés do jogador na foto de perto: o job fica 2 blocos adiante. */
    private static final double[] NEAR_FEET = {1.5, 0.0, 30.0};
    private static final double[] NEAR_JOB = {1.5, 1.7, 28.0};
    /** Os pés do jogador na foto de longe: 60 blocos ao sul do job. */
    private static final double[] FAR_FEET = {1.5, 0.0, 56.0};

    private static final Consumer<RenderFrameEvent.Pre> HOOK = E2eFxProbeSteps::onFrame;
    private static boolean hooked;
    private static ProbeJob job;

    private E2eFxProbeSteps() {
    }

    /** Desliga o ouvinte e solta o job; idempotente. */
    static void restoreHooks() {
        job = null;
        if (hooked) {
            hooked = false;
            NeoForge.EVENT_BUS.unregister(HOOK);
        }
    }

    private static void onFrame(RenderFrameEvent.Pre event) {
        ProbeJob current = job;
        if (current != null && !ShaderPackCheck.shadowPass()) {
            FxQueue.submit(current);
        }
    }

    static void probeSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] wall = new BlockPos[1];
        Vec3[] back = new Vec3[1];
        float[] backRot = new float[2];
        boolean[] saved = new boolean[2];
        list.add(close("fx-base: fechar a tela de antes"));
        list.add(new Step("fx-base: os shaders dos efeitos compilaram", STEP_TIMEOUT_MS, () -> {
        }, FxShaders::ready, () -> "tinta " + FxShaders.ink() + ", aditivo " + FxShaders.add() + ", luz "
                + FxShaders.light()));
        list.add(new Step("fx-base: a parede de pedra", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            back[0] = minecraft.player.position();
            backRot[0] = minecraft.player.getYRot();
            backRot[1] = minecraft.player.getXRot();
            saved[0] = minecraft.options.hideGui;
            minecraft.options.hideGui = true;
            minecraft.options.keyShift.setDown(false);
            saved[1] = onServer(server -> server.getPlayerList().getPlayer(playerId).getAbilities().flying);
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ServerLevel level = player.serverLevel();
                wall[0] = player.blockPosition().offset(WALL_AT[0], WALL_AT[1], WALL_AT[2]);
                setWall(level, wall[0], true);
                return null;
            });
            if (!hooked) {
                hooked = true;
                NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class, HOOK);
            }
        }, () -> Minecraft.getInstance().level.getBlockState(wall[0]).is(Blocks.STONE)
                && Minecraft.getInstance().level.getBlockState(wall[0].offset(2, 2, 0)).is(Blocks.STONE),
                () -> "bloco no cliente " + Minecraft.getInstance().level.getBlockState(wall[0])));

        // 1. oclusão
        list.add(E2eResonatorSteps.camera("fx-base: câmera da oclusão", playerId, () -> wall[0], OCCLUSION_FEET,
                BEHIND));
        list.add(new Step("fx-base: o job atrás da parede", STEP_TIMEOUT_MS,
                () -> job = new ProbeJob(wall[0], BEHIND), () -> stepTicks >= 40 && job.draws > 0,
                () -> "desenhos " + job.draws));
        list.add(capture("fx-base-oclusao"));

        // 2. perto
        list.add(E2eResonatorSteps.camera("fx-base: câmera de perto", playerId, () -> wall[0], NEAR_FEET, NEAR_JOB));
        list.add(new Step("fx-base: o job a 2 blocos", STEP_TIMEOUT_MS,
                () -> job = new ProbeJob(wall[0], NEAR_JOB), () -> stepTicks >= 40 && job.draws > 0,
                () -> "desenhos " + job.draws));
        list.add(capture("fx-base-perto"));

        // 3. longe
        list.add(E2eResonatorSteps.camera("fx-base: câmera de longe", playerId, () -> wall[0], FAR_FEET,
                new double[] {1.5, 1.5, 0.5}));
        list.add(new Step("fx-base: o job a 60 blocos não é desenhado", STEP_TIMEOUT_MS,
                () -> job = new ProbeJob(wall[0], BEHIND), () -> {
                    if (stepTicks < 40) {
                        return false;
                    }
                    if (job.draws > 0) {
                        throw new StepFailure("o job a 60 blocos foi desenhado " + job.draws + " vez(es)");
                    }
                    return true;
                }, () -> "desenhos " + job.draws));
        list.add(capture("fx-base-longe"));

        list.add(new Step("fx-base: limpar", STEP_TIMEOUT_MS, () -> {
            restoreHooks();
            FxQueue.clear();
            Minecraft.getInstance().options.hideGui = saved[0];
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                setWall(player.serverLevel(), wall[0], false);
                player.getAbilities().flying = saved[1];
                player.onUpdateAbilities();
                player.teleportTo(player.serverLevel(), back[0].x, back[0].y, back[0].z, backRot[0], backRot[1]);
                return null;
            });
        }, () -> Minecraft.getInstance().level.getBlockState(wall[0]).isAir()
                && Minecraft.getInstance().player.position().distanceTo(back[0]) < 0.5,
                () -> "jogador em " + Minecraft.getInstance().player.position()));
    }

    /** A parede de pedra de 3 × 3 × 1 com o canto em {@code corner} (a face larga para o sul), ou ar no lugar dela. */
    private static void setWall(ServerLevel level, BlockPos corner, boolean on) {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                level.setBlockAndUpdate(corner.offset(x, y, 0), on ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
            }
        }
    }

    /**
     * O efeito de teste: um {@code BRILHO_G} de meio lado 1,2 bloco e uma fita {@code TRILHOS} (tinta) com a
     * {@code FLUXO} (luz) por cima, de 6 blocos, ao longo de x. A fita é amostrada uma vez, em px, e só é movida para
     * a câmera a cada quadro.
     */
    private static final class ProbeJob implements FxJob {
        private static final float HALF = 1.2f;
        private static final int GOLD = 0xFFC878;
        /** Os controles da fita, em px locais ao centro (6 blocos = 96 px de ponta a ponta). */
        private static final float[] CONTROLS = {-48, -6, 0, -24, 8, 6, 0, -2, -6, 24, 8, 6, 48, -6, 0};

        private final double cx;
        private final double cy;
        private final double cz;
        private final FxRibbon.Buffer base = new FxRibbon.Buffer();
        private final FxRibbon.Buffer work = new FxRibbon.Buffer();
        private final int samples;
        private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        /** Quantas vezes o {@link #draw} rodou: o corte de 48 blocos e o estágio se conferem por aqui. */
        int draws;

        ProbeJob(BlockPos origin, double[] at) {
            cx = origin.getX() + at[0];
            cy = origin.getY() + at[1];
            cz = origin.getZ() + at[2];
            samples = FxRibbon.catmullRom(CONTROLS, 5, 1f, base);
            for (int i = 0; i < samples; i++) {
                // largura constante (a textura tem 8 px de altura = meio bloco) e as pontas somem em 8 amostras
                work.w[i] = 0.25f;
                work.a[i] = Math.min(1f, Math.min(i, samples - 1 - i) / 8f);
            }
        }

        @Override
        public double distanceSq(FxContext ctx) {
            double dx = cx - ctx.camX;
            double dy = cy - ctx.camY;
            double dz = cz - ctx.camZ;
            return dx * dx + dy * dy + dz * dz;
        }

        @Override
        public void draw(FxContext ctx, MultiBufferSource buffers) {
            draws++;
            float rx = (float) (cx - ctx.camX);
            float ry = (float) (cy - ctx.camY);
            float rz = (float) (cz - ctx.camZ);
            probe.set(cx, cy, cz);
            int inkLight = LevelRenderer.getLightColor(Minecraft.getInstance().level, probe);
            int full = LightTexture.FULL_BRIGHT;
            for (int i = 0; i < samples; i++) {
                work.pts[i * 3] = base.pts[i * 3] / 16f + rx;
                work.pts[i * 3 + 1] = base.pts[i * 3 + 1] / 16f + ry;
                work.pts[i * 3 + 2] = base.pts[i * 3 + 2] / 16f + rz;
            }
            float scroll = (ctx.gameTime + ctx.partialTick) / 20f * 0.5f;
            // 64 px de textura = 4 blocos por repetição
            FxDraw.ribbon(buffers.getBuffer(FxTypes.of(FxSheets.TRILHOS)), ctx, FxSheets.TRILHOS, 0, work, samples,
                    0xFFFFFF, 4f, 0f, 0, inkLight);
            FxDraw.ribbon(buffers.getBuffer(FxTypes.of(FxSheets.FLUXO)), ctx, FxSheets.FLUXO, 0, work, samples,
                    GOLD, 4f, scroll, 0, full);
            FxDraw.sprite(buffers.getBuffer(FxTypes.of(FxSheets.BRILHO_G)), ctx, FxSheets.BRILHO_G, 0, rx, ry, rz,
                    HALF, 0f, GOLD, 1f, 0, full);
        }
    }
}
