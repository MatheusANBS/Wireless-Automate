package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eResonatorSteps.core;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.ArmilarRenderer;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A suíte opcional {@code armilar-render}: o render novo da Armilar com o relógio parado em cada fase da conjunção do
 * filme (a preparação, o estouro, o arco, o impacto na Porta e a dissolução), com a vazão 7 e com a vazão 0 (só o
 * estouro e o clarão no horizonte); o brilho dos cristais de perto; o buffer cheio; e a recarga (os grãos de redstone
 * e o clarão do fim). A Tarefa 11 funde estes passos na suíte {@code ressonador}.
 */
final class E2eArmilarRenderSteps {
    /** O {@code d} (segundos desde a conjunção) de cada fase: a preparação, o estouro, o arco, o impacto e a dissolução. */
    private static final double[] PHASE_D = {-0.3, 0.1, 0.35, 0.9, 2.0};
    private static final String[] PHASE_NAME = {"preparacao", "estouro", "arco", "impacto", "dissolucao"};
    /** O {@code TC} do filme: a conjunção de referência, em s. */
    private static final double TC = 11.0;
    /** Os ticks para a vazão suavizada chegar à faixa forçada (a faixa anda 12% por tick). */
    private static final int FLOW_SETTLE_TICKS = 100;
    /** Os ticks para a montagem acabar e o {@code u} chegar a 1 depois de sintonizar. */
    private static final int ASSEMBLY_TICKS = 160;
    /** O Opaco da recarga: a câmara 2, a última das três do Núcleo Elite. */
    private static final int RECHARGE_CHAMBER = 2;
    private static final int FLASH_DELAY_TICKS = 4;

    private E2eArmilarRenderSteps() {
    }

    static void armilarRenderSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] origin = new BlockPos[1];
        Vec3[] back = new Vec3[1];
        float[] backRot = new float[2];
        list.add(new Step("Armilar render: montar", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            back[0] = minecraft.player.position();
            backRot[0] = minecraft.player.getYRot();
            backRot[1] = minecraft.player.getXRot();
            minecraft.options.hideGui = true;
            ArmilarRenderer.freezeSecondsForTests = Double.NaN;
            ArmilarRenderer.forceBandForTests = -1;
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ServerLevel level = player.serverLevel();
                origin[0] = player.blockPosition().offset(12, 0, -14);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                E2eResonatorSteps.clear(level, origin[0]);
                E2eResonatorSteps.placeArmilar(level, origin[0], RouterTier.ELITE);
                return null;
            });
        }, () -> {
            var state = Minecraft.getInstance().level.getBlockState(core(origin[0]));
            return state.getBlock() instanceof ResonanceCoreBlock && state.getValue(ResonatorPartBlock.FORMED);
        }, () -> "Núcleo montado no cliente"));
        list.add(new Step("Armilar render: cristais e sintonia", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                    .getBlockEntity(core(origin[0]));
            core.setAuto(false);
            core.setStationForTest(98.0);
            core.setDial(98.0);
            core.insertCrystal(new ItemStack(ModItems.SIGNAL_CRYSTAL.get()));
            core.insertCrystal(new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get()));
            ItemStack dull = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
            SignalCrystalItem.setCharge(dull, 0);
            core.insertCrystal(dull);
            return null;
        }), () -> {
            ChamberView view = view(origin);
            return view.tuned() && view.chambers().length == 3 && view.chambers()[2] != 0;
        }, () -> "view " + view(origin)));
        list.add(E2eResonatorSteps.camera("Armilar render: câmera de frente", playerId, () -> origin[0],
                E2eResonatorSteps.FRONT_VIEW));
        list.add(DevEndToEnd.wait("a montagem acabar", ASSEMBLY_TICKS));
        // o brilho dos cristais vivos, de perto, com o relógio solto
        list.add(E2eResonatorSteps.camera("Armilar render: câmera de perto", playerId, () -> origin[0],
                E2eResonatorSteps.CLOSE_VIEW));
        list.add(DevEndToEnd.wait("a câmera chegar", 10));
        list.add(capture("armilar-render-cristais-brilho"));
        list.add(E2eResonatorSteps.camera("Armilar render: câmera de frente de novo", playerId, () -> origin[0],
                E2eResonatorSteps.FRONT_VIEW));
        phases(list, 7);
        phases(list, 0);
        list.add(new Step("Armilar render: relógio e vazão soltos", STEP_TIMEOUT_MS, () -> {
            ArmilarRenderer.freezeSecondsForTests = Double.NaN;
            ArmilarRenderer.forceBandForTests = -1;
        }, () -> true, () -> "relógio do mundo"));
        fullSteps(list, origin);
        rechargeSteps(list, playerId, origin);
        list.add(new Step("Armilar render: limpar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            E2eResonatorSteps.clear(player.serverLevel(), origin[0]);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), back[0].x, back[0].y, back[0].z, backRot[0], backRot[1]);
            Minecraft.getInstance().options.hideGui = false;
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(core(origin[0])).isAir()
                && Minecraft.getInstance().player.position().distanceTo(back[0]) < 0.5,
                () -> "jogador em " + Minecraft.getInstance().player.position()));
    }

    /**
     * As cinco fases da conjunção com a vazão {@code band} forçada: o relógio parado em {@code TC + d}, uma captura por
     * fase ({@code armilar-render-v<band>-<fase>}).
     */
    private static void phases(List<Step> list, int band) {
        list.add(new Step("Armilar render: vazão " + band, STEP_TIMEOUT_MS,
                () -> ArmilarRenderer.forceBandForTests = band, () -> stepTicks >= FLOW_SETTLE_TICKS,
                () -> "faixa forçada " + band));
        for (int i = 0; i < PHASE_D.length; i++) {
            double seconds = TC + PHASE_D[i];
            list.add(new Step("Armilar render: relógio em TC + " + PHASE_D[i], STEP_TIMEOUT_MS,
                    () -> ArmilarRenderer.freezeSecondsForTests = seconds, () -> stepTicks >= 8,
                    () -> "relógio parado em " + seconds));
            list.add(capture("armilar-render-v" + band + "-" + PHASE_NAME[i]));
        }
        list.add(new Step("Armilar render: relógio solto", STEP_TIMEOUT_MS,
                () -> ArmilarRenderer.freezeSecondsForTests = Double.NaN, () -> true, () -> "relógio do mundo"));
    }

    /** O buffer cheio: a redstone no máximo e a estação no dial, sem ninguém tirar energia; o sol sob pressão. */
    private static void fullSteps(List<Step> list, BlockPos[] origin) {
        list.add(new Step("Armilar render: buffer cheio", 4 * STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                    .getBlockEntity(core(origin[0]));
            core.setRedstone(ResonanceCoreBlockEntity.MAX_REDSTONE);
            core.setStationForTest(98.0);
            core.setDial(98.0);
            return null;
        }), () -> view(origin).full() && stepTicks >= 20, () -> "cheio " + view(origin).full()));
        list.add(DevEndToEnd.wait("a pressão assentar", 40));
        list.add(capture("armilar-render-cheio"));
    }

    /**
     * A recarga: um Opaco na {@link #RECHARGE_CHAMBER} com redstone; um grão voa da Porta de Cristais até ele (a
     * captura 20 ticks depois de a recarga começar) e, no fim, o clarão no cristal.
     */
    private static void rechargeSteps(List<Step> list, UUID playerId, BlockPos[] origin) {
        long[] seen = new long[1];
        long waitMs = Config.rechargeTicks() * 50 + 30_000;
        list.add(new Step("Armilar render: Opaco em recarga", waitMs, () -> {
            seen[0] = 0;
            onServer(server -> {
                ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                        .getBlockEntity(core(origin[0]));
                ItemStack dull = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
                SignalCrystalItem.setCharge(dull, 0);
                core.setCrystalAt(RECHARGE_CHAMBER, dull);
                core.setRedstone(14);
                return null;
            });
        }, () -> {
            if (seen[0] == 0 && view(origin).rechargeStart() >= 0) {
                seen[0] = stepTicks;
            }
            return seen[0] != 0 && stepTicks >= seen[0] + 20;
        }, () -> "recarga no cliente"));
        list.add(capture("armilar-render-recarga-graos"));
        list.add(E2eResonatorSteps.camera("Armilar render: câmera de perto da recarga", playerId, () -> origin[0],
                E2eResonatorSteps.CLOSE_VIEW));
        list.add(DevEndToEnd.wait("a recarga de perto", 10));
        list.add(capture("armilar-render-recarga-perto"));
        list.add(new Step("Armilar render: o fim da recarga", waitMs, () -> seen[0] = 0, () -> {
            if (seen[0] == 0 && view(origin).rechargeStart() < 0) {
                seen[0] = stepTicks;
            }
            return seen[0] != 0 && stepTicks >= seen[0] + FLASH_DELAY_TICKS;
        }, () -> "fim da recarga no cliente"));
        list.add(capture("armilar-render-recarga-fim"));
    }

    private static ChamberView view(BlockPos[] origin) {
        return ((ResonanceCoreBlockEntity) Minecraft.getInstance().level.getBlockEntity(core(origin[0]))).clientView();
    }
}
