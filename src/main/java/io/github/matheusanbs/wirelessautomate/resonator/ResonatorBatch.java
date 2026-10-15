package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

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
        // Com o buffer cheio não há geração nem desgaste: pula as câmaras. Sem gerar, o Opaco novo e a energia
        // de 0 para mais não acontecem, então só a redstone que baixou pela recarga pode acordar as Portas.
        boolean full = energyBefore >= core.capacity();
        int dullBefore = full ? 0 : dullCount(core);
        boolean changed = drift(level, core);
        if (!full) {
            changed |= generate(core, ticks);
        }
        changed |= recharge(core, ticks);
        if (changed) {
            core.setChanged();
        }
        // Só as transições que podem acordar quem dorme nas Portas: energia de 0 para mais (quem tira da Porta de
        // Energia), redstone que baixou (quem põe redstone) e cristal que ficou Opaco (quem tira o Opaco). Câmara
        // vazia não vem do lote: quem esvazia avisa no Núcleo.
        boolean energy = energyBefore == 0 && core.energy() > 0;
        boolean crystals = core.redstone() < redstoneBefore || (!full && dullCount(core) > dullBefore);
        if (energy || crystals) {
            core.notifyPorts(energy, crystals);
        }
        // A carga que baixa não muda a câmara: só cristal Opaco, recarga, faixa ou lâmpada mandam o update tag.
        core.syncIfChanged();
    }

    private static int dullCount(ResonanceCoreBlockEntity core) {
        int total = 0;
        for (int i = 0; i < core.chambers(); i++) {
            if (SignalCrystalItem.isDull(core.crystals().get(i))) {
                total++;
            }
        }
        return total;
    }

    /**
     * Sem estação (nunca montou) não há o que derivar; a contagem dos lotes anda mesmo assim. No modo automático o
     * Sintonizador anda o dial logo depois de a estação andar. Só o passo da deriva marca o Núcleo como mudado: a
     * contagem sozinha não vale uma gravação do chunk.
     */
    private static boolean drift(ServerLevel level, ResonanceCoreBlockEntity core) {
        if (core.countBatch(BATCHES_PER_DRIFT) && core.hasStation()) {
            core.setDrift(Tuning.step(core.station(), core.velocity(), level.random.nextGaussian()));
            if (core.auto() && core.hasTuner()) {
                core.setDial(Tuning.autoStep(core.dial(), core.station()));
            }
            return true;
        }
        return false;
    }

    /**
     * O desgaste só vem com a geração, na proporção do que foi gerado: buffer cheio ou eficiência 0 não gastam os
     * cristais, e com espaço para só parte do lote cada cristal gasta a mesma parte dos ticks (arredondada para cima).
     */
    private static boolean generate(ResonanceCoreBlockEntity core, int ticks) {
        if (!core.formed()) {
            return false;
        }
        long room = core.capacity() - core.energy();
        long full = CoreMath.mulSat(core.generationPerTick(), ticks);
        long generated = Math.min(full, room);
        if (generated <= 0) {
            return false;
        }
        core.addEnergy(generated);
        long spent = generated == full ? ticks : (long) Math.ceil((double) ticks * generated / full);
        for (int i = 0; i < core.chambers(); i++) {
            ItemStack stack = core.crystals().get(i);
            if (stack.getItem() instanceof SignalCrystalItem item && !SignalCrystalItem.isDull(stack)) {
                long life = Config.crystalLifeTicks(item.isCut());
                SignalCrystalItem.setCharge(stack, CrystalCharge.drain(SignalCrystalItem.charge(stack), spent, life));
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
