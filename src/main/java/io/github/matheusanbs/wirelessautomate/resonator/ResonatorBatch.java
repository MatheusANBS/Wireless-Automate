package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Um lote do Núcleo de Ressonância: deriva da estação, geração pela sintonia, desgaste dos cristais e recarga do
 * Opaco com redstone. O gerenciador roda um lote de 20 ticks por Núcleo a cada 20 ticks.
 */
public final class ResonatorBatch {
    /** Lotes de 20 ticks entre dois passos da deriva (400 ticks). */
    static final int BATCHES_PER_DRIFT = 20;

    private ResonatorBatch() {
    }

    public static void run(ServerLevel level, ResonanceCoreBlockEntity core, int ticks) {
        long energyBefore = core.energy();
        int redstoneBefore = core.redstone();
        int emptyBefore = count(core, false);
        int dullBefore = count(core, true);
        boolean changed = drift(level, core);
        changed |= generate(core, ticks);
        changed |= recharge(core, ticks);
        if (changed) {
            core.setChanged();
        }
        boolean energy = energyBefore == 0 && core.energy() > 0;
        boolean crystals = core.redstone() < redstoneBefore || count(core, false) > emptyBefore
                || count(core, true) > dullBefore;
        if (energy || crystals) {
            notifyPorts(level, core, energy, crystals);
        }
    }

    /**
     * Avisa as Portas só nas transições que podem acordar quem dorme nelas: energia de 0 para mais (o roteador
     * que tira da Porta de Energia), redstone que baixou, câmara que esvaziou ou cristal que ficou Opaco (o que
     * põe redstone ou cristal, ou tira o Opaco, pela Porta de Cristais). O {@code setChanged} do Núcleo avisa só
     * os vizinhos dele; os das Portas ficam sabendo por aqui.
     */
    private static void notifyPorts(ServerLevel level, ResonanceCoreBlockEntity core, boolean energy, boolean crystals) {
        for (BlockPos port : core.ports()) {
            if (!level.isLoaded(port)) {
                continue;
            }
            BlockState state = level.getBlockState(port);
            if (state.getBlock() instanceof ResonatorPartBlock part
                    && (energy && part.kind() == ResonatorPartBlock.Kind.ENERGY_PORT
                            || crystals && part.kind() == ResonatorPartBlock.Kind.CRYSTAL_PORT)) {
                level.updateNeighbourForOutputSignal(port, state.getBlock());
            }
        }
    }

    /** Quantas câmaras do tier estão vazias ({@code dull} falso) ou com um Opaco ({@code dull} verdadeiro). */
    private static int count(ResonanceCoreBlockEntity core, boolean dull) {
        int total = 0;
        for (int i = 0; i < core.chambers(); i++) {
            ItemStack stack = core.crystals().get(i);
            if (dull ? SignalCrystalItem.isDull(stack) : stack.isEmpty()) {
                total++;
            }
        }
        return total;
    }

    /**
     * Sem estação (nunca montou) não há o que derivar; a contagem dos lotes anda mesmo assim. Só o passo da
     * deriva marca o Núcleo como mudado: a contagem sozinha não vale uma gravação do chunk.
     */
    private static boolean drift(ServerLevel level, ResonanceCoreBlockEntity core) {
        if (core.countBatch(BATCHES_PER_DRIFT) && core.hasStation()) {
            core.setDrift(Tuning.step(core.station(), core.velocity(), level.random.nextGaussian()));
            return true;
        }
        return false;
    }

    /** O desgaste só vem com a geração: buffer cheio ou eficiência 0 não gastam os cristais. */
    private static boolean generate(ResonanceCoreBlockEntity core, int ticks) {
        if (!core.formed()) {
            return false;
        }
        long room = core.capacity() - core.energy();
        long generated = Math.min(CoreMath.mulSat(core.generationPerTick(), ticks), room);
        if (generated <= 0) {
            return false;
        }
        core.addEnergy(generated);
        for (int i = 0; i < core.chambers(); i++) {
            ItemStack stack = core.crystals().get(i);
            if (stack.getItem() instanceof SignalCrystalItem item && !SignalCrystalItem.isDull(stack)) {
                long life = Config.crystalLifeTicks(item.isCut());
                SignalCrystalItem.setCharge(stack, CrystalCharge.drain(SignalCrystalItem.charge(stack), ticks, life));
            }
        }
        return true;
    }

    /** O progresso só anda com um Opaco e redstone; ao completar, gasta 1 de redstone e enche o primeiro Opaco. */
    private static boolean recharge(ResonanceCoreBlockEntity core, int ticks) {
        int dull = firstDull(core);
        if (dull < 0 || core.redstone() <= 0) {
            return false;
        }
        long progress = (long) core.rechargeProgress() + ticks;
        if (progress >= Config.rechargeTicks()) {
            core.spendRedstone();
            SignalCrystalItem.setCharge(core.crystals().get(dull), CrystalCharge.FULL);
            progress = 0;
        }
        core.setRechargeProgress((int) Math.min(progress, Integer.MAX_VALUE));
        return true;
    }

    private static int firstDull(ResonanceCoreBlockEntity core) {
        for (int i = 0; i < core.chambers(); i++) {
            if (SignalCrystalItem.isDull(core.crystals().get(i))) {
                return i;
            }
        }
        return -1;
    }
}
