package io.github.matheusanbs.wirelessautomate.repairer;

import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import io.github.matheusanbs.wirelessautomate.storage.ScalarStore;

/** O buffer do Reparador para o roteador, em {@code long}: só recebe. */
public final class RepairerBulkEnergy implements BulkEnergy {
    private final RepairerBlockEntity repairer;
    private final ScalarStore store;

    RepairerBulkEnergy(RepairerBlockEntity repairer, ScalarStore store) {
        this.repairer = repairer;
        this.store = store;
    }

    @Override
    public long insert(long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        long taken = store.insert(amount, simulate);
        if (taken > 0 && !simulate) {
            repairer.wakeForEnergy();
        }
        return taken;
    }

    @Override
    public long extract(long amount, boolean simulate) {
        return 0;
    }
}
