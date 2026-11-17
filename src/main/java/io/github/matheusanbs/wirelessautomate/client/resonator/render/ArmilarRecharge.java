package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.RechargeRank;

/**
 * Quem recarrega e o quanto já andou, em lógica pura.
 */
final class ArmilarRecharge {
    private static final byte DULL = 3;
    private static final byte CRACKED = 5;

    private ArmilarRecharge() {
    }

    /** Opaco que o Núcleo recarrega: sem carga e não rachado. */
    static boolean rechargeable(byte code) {
        return code >= DULL && code < CRACKED;
    }

    /** O posto da câmara {@code i} entre os Opacos recarregáveis (0 é o primeiro), ou -1 se ela não é um. */
    static int rank(int i, byte[] chambers) {
        if (!rechargeable(chambers[i])) {
            return -1;
        }
        int rank = 0;
        for (int j = 0; j < i; j++) {
            if (rechargeable(chambers[j])) {
                rank++;
            }
        }
        return rank;
    }

    /** Recarga em curso e o posto dentro do que o catalisador paga. */
    static boolean isRecharging(int i, byte[] chambers, ChamberView view) {
        return view.rechargeStart() >= 0 && RechargeRank.includes(rank(i, chambers), view.rechargers());
    }

    /** A primeira câmara que recarrega agora, ou -1. */
    static int firstRecharging(byte[] chambers, ChamberView view) {
        for (int i = 0; i < chambers.length; i++) {
            if (isRecharging(i, chambers, view)) {
                return i;
            }
        }
        return -1;
    }

    /** De 0 a 1, o quanto a recarga da câmara {@code i} já andou; 0 se ela não recarrega. */
    static double progress(int i, byte[] chambers, double ticks, ChamberView view) {
        if (!isRecharging(i, chambers, view)) {
            return 0;
        }
        return Math.clamp((ticks - view.rechargeStart()) / view.rechargeTicks(), 0.0, 1.0);
    }

    /** O quanto o Opaco ainda está caído (inclinado e solto do engaste): 1 sem recarga, 0 ao terminar. */
    static double fall(double progress) {
        double c = Math.clamp(progress, 0.0, 1.0);
        return 1 - c * c * (3 - 2 * c);
    }
}
