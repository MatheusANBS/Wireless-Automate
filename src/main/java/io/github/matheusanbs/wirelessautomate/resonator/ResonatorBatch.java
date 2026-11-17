package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Um lote do Núcleo de Ressonância: deriva da estação, desgaste dos cristais pelo que os ticks geraram e recarga dos
 * Opacos com o catalisador (todos os pagos de uma vez), mais a faixa de vazão e o update tag. A geração de energia
 * não é daqui: ela roda a cada tick ({@link ResonatorGeneration}). O gerenciador roda um lote de 20 ticks por Núcleo
 * a cada 20 ticks.
 */
public final class ResonatorBatch {
    /** Ticks de um lote: o gerenciador roda um por Núcleo a cada tanto. */
    public static final int BATCH_TICKS = 20;
    /** Lotes de 20 ticks entre dois passos da deriva (400 ticks). */
    static final int BATCHES_PER_DRIFT = 20;

    private ResonatorBatch() {
    }

    public static void run(ServerLevel level, ResonanceCoreBlockEntity core, int ticks) {
        int catalystBefore = core.redstone();
        // Sem geração desde o último lote não há desgaste: pula as câmaras. Sem gastar, o Opaco novo não acontece,
        // então só o catalisador que baixou pela recarga pode acordar as Portas de Cristais.
        boolean worn = core.wear().any();
        int dullBefore = worn ? dullCount(core) : 0;
        boolean changed = drift(level, core);
        changed |= wear(core);
        changed |= recharge(core, ticks);
        // Rede de segurança do cache da geração: os cristais gastaram, a deriva andou, a config pode ter mudado.
        core.invalidateGeneration();
        if (changed) {
            core.markUnsaved();
        }
        // Só as transições que podem acordar quem dorme nas Portas de Cristais: catalisador que baixou (quem põe
        // catalisador; a recarga que racha um cristal também gasta) e cristal que ficou Opaco (quem tira o Opaco).
        // A energia de 0 para mais avisa no tick em que sobe (ResonatorGeneration); câmara vazia não vem do lote:
        // quem esvazia avisa no Núcleo.
        if (core.redstone() < catalystBefore || (worn && dullCount(core) > dullBefore)) {
            core.notifyPorts(false, true);
        }
        // A vazão real do lote vira faixa (o "cheio" é o buffer no teto agora) e zera as somas das Portas.
        core.flow().closeBatch(ticks, core.maxOutput(), core.energy() >= core.capacity());
        // A carga que baixa não muda a câmara: só cristal Opaco ou rachado, recarga, faixa ou lâmpada mandam o
        // update tag.
        core.syncIfChanged();
    }

    /** Opacos e rachados (os dois têm carga 0 e saem pela Porta de Cristais). */
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
     * O desgaste só vem com a geração, na proporção do que os ticks desde o último lote geraram de fato
     * ({@link CoreWear}): buffer cheio ou eficiência 0 não gastam os cristais, e se o buffer cortou parte dos ticks
     * cada cristal gasta a mesma parte deles (arredondada para cima). A vida do cristal é a da config na escala da
     * vida do tier do Núcleo ({@link CoreMath#crystalLife}).
     */
    private static boolean wear(ResonanceCoreBlockEntity core) {
        long spent = core.wear().take();
        if (spent <= 0) {
            return false;
        }
        long tierLife = Config.crystalLifeTicks(core.tier().ordinal());
        for (int i = 0; i < core.chambers(); i++) {
            ItemStack stack = core.crystals().get(i);
            if (stack.getItem() instanceof SignalCrystalItem item && !SignalCrystalItem.isDull(stack)
                    && !SignalCrystalItem.isCracked(stack)) {
                long life = CoreMath.crystalLife(Config.crystalLifeTicks(item.isCut()), tierLife);
                SignalCrystalItem.setCharge(stack, CrystalCharge.drain(SignalCrystalItem.charge(stack), spent, life));
            }
        }
        return true;
    }

    /**
     * Um cronômetro só para todos: o progresso anda com pelo menos um Opaco recarregável e catalisador; ao completar,
     * recarregam os Opacos recarregáveis na ordem das câmaras enquanto houver catalisador, cada um gastando 1
     * ({@link #finishRecharge}). Os que o catalisador não pagou esperam o próximo ciclo.
     */
    private static boolean recharge(ResonanceCoreBlockEntity core, int ticks) {
        if (rechargeable(core) == 0) {
            // O último Opaco saiu no meio: o progresso era deles, o próximo que entrar começa do zero.
            if (core.rechargeProgress() != 0) {
                core.setRechargeProgress(0);
            }
            return false;
        }
        if (core.redstone() <= 0) {
            return false;
        }
        long progress = (long) core.rechargeProgress() + ticks;
        if (progress >= Config.rechargeTicks()) {
            for (int i = 0; i < core.chambers() && core.redstone() > 0; i++) {
                ItemStack stack = core.crystals().get(i);
                if (SignalCrystalItem.isRechargeable(stack)) {
                    core.spendCatalyst();
                    finishRecharge(stack);
                }
            }
            progress = 0;
        }
        core.setRechargeProgress((int) Math.min(progress, Integer.MAX_VALUE));
        return true;
    }

    /**
     * A fadiga: cada recarga gasta 1 de integridade. Com integridade sobrando o cristal enche; a que zera racha o
     * cristal em vez de enchê-lo (carga 0 e integridade 0), e a câmara muda para o {@code syncIfChanged}.
     */
    static void finishRecharge(ItemStack stack) {
        int left = CrystalIntegrity.spend(SignalCrystalItem.integrity(stack));
        SignalCrystalItem.setIntegrity(stack, left);
        if (!CrystalIntegrity.isCracked(left)) {
            SignalCrystalItem.setCharge(stack, CrystalCharge.FULL);
        }
    }

    /** Quantos Opacos o Núcleo recarrega (o rachado fica de fora). */
    static int rechargeable(ResonanceCoreBlockEntity core) {
        int total = 0;
        for (int i = 0; i < core.chambers(); i++) {
            if (SignalCrystalItem.isRechargeable(core.crystals().get(i))) {
                total++;
            }
        }
        return total;
    }
}
