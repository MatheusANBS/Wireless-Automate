package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import java.util.Arrays;

/**
 * O clarão do fim da recarga, em lógica pura. Porte de {@code ChamberScene.trackRecharge} e do laço do clarão no
 * {@code update}. Dura 14 ticks (0,7 s).
 */
final class FlashTracker {
    static final int FLASH_TICKS = 14;
    private static final long NEVER = Long.MIN_VALUE / 2;

    private long[] flashAt = new long[0];
    private int rechargingPrev;
    private int flashFired;

    /** Chamado quando a view muda. O clarão sai quando a câmara deixa de ser Opaco que recarrega sem ter terminado pelo progresso. */
    void onView(ChamberView view, byte[] chambers, long gameTime) {
        if (flashAt.length != chambers.length) {
            flashAt = new long[chambers.length];
            Arrays.fill(flashAt, NEVER);
            rechargingPrev = 0;
            flashFired = 0;
        }
        int now = 0;
        for (int i = 0; i < chambers.length; i++) {
            if (ArmilarRecharge.isRecharging(i, chambers, view)) {
                now |= 1 << i;
            }
        }
        if (now == rechargingPrev) {
            return;
        }
        for (int i = 0; i < chambers.length; i++) {
            int bit = 1 << i;
            if ((rechargingPrev & bit) != 0 && (now & bit) == 0 && (flashFired & bit) == 0
                    && (chambers[i] == 1 || chambers[i] == 2)) {
                flashAt[i] = gameTime;
            }
        }
        flashFired &= now;
        rechargingPrev = now;
    }

    /** Uma vez por tick novo: dispara o clarão da câmara cuja recarga acabou pelo progresso e apaga os que passaram dos 14 ticks. */
    void tick(ChamberView view, byte[] chambers, long gameTime) {
        for (int i = 0; i < chambers.length; i++) {
            int bit = 1 << i;
            if ((rechargingPrev & bit) != 0 && (flashFired & bit) == 0
                    && ArmilarRecharge.progress(i, chambers, gameTime, view) >= 1) {
                flashAt[i] = gameTime;
                flashFired |= bit;
            }
        }
        for (int i = 0; i < flashAt.length; i++) {
            if (flashAt[i] != NEVER && gameTime - flashAt[i] >= FLASH_TICKS) {
                flashAt[i] = NEVER;
            }
        }
    }

    /** De 0 a 1 em 0,7 s; -1 sem clarão. */
    float flash(int chamber, long gameTime, float partialTick) {
        return chamber < flashAt.length && flashAt[chamber] != NEVER
                ? (gameTime - flashAt[chamber] + partialTick) / FLASH_TICKS : -1f;
    }
}
