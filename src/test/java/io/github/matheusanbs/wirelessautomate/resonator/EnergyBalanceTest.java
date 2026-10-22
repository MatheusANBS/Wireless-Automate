package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EnergyBalanceTest {
    @Test
    void routerTableIsTheResonatorPlusFivePercentRoundedUp() {
        assertArrayEquals(new long[] {454L, 29_100L, 63_000L, 1_350_000L, 67_200_000L, 84_500_000L, 158_000_000L, 0L},
                EnergyBalance.routerEnergyTable());
    }

    @Test
    void batteryTableIsOneHourOfTheResonatorAtFullOutput() {
        assertArrayEquals(new long[] {31_104_000L, 1_990_656_000L, 4_320_000_000L, 92_160_000_000L,
                4_608_000_000_000L, 5_788_800_000_000L, 10_785_600_000_000L, 0L}, EnergyBalance.batteryTable());
    }

    @Test
    void everyLimitedRouterCoversTheResonatorOfItsTierWithAtMostTenPercentToSpare() {
        for (int tier = 0; tier < EnergyBalance.TIERS - 1; tier++) {
            long generated = EnergyBalance.resonatorOutput(tier);
            long router = EnergyBalance.routerEnergy(tier);
            assertTrue(router >= generated, "o roteador do tier " + tier + " não dá conta do Ressonador");
            assertTrue(router * 10 <= generated * 11, "o roteador do tier " + tier + " passa de 10% de folga");
        }
    }

    @Test
    void aBatteryLastsAnHourOfItsResonatorAtFullOutput() {
        for (int tier = 0; tier < EnergyBalance.TIERS - 1; tier++) {
            assertEquals(EnergyBalance.resonatorOutput(tier) * 72_000L, EnergyBalance.batteryCapacity(tier));
        }
    }

    @Test
    void theUltimateHasNoLimit() {
        assertEquals(0L, EnergyBalance.routerEnergy(EnergyBalance.TIERS - 1));
        assertEquals(0L, EnergyBalance.batteryCapacity(EnergyBalance.TIERS - 1));
    }

    @Test
    void bothTablesGrowWithTheTier() {
        for (int tier = 1; tier < EnergyBalance.TIERS - 1; tier++) {
            assertTrue(EnergyBalance.routerEnergy(tier) > EnergyBalance.routerEnergy(tier - 1));
            assertTrue(EnergyBalance.batteryCapacity(tier) > EnergyBalance.batteryCapacity(tier - 1));
        }
    }

    @Test
    void roundingKeepsThreeSignificantDigitsAndNeverGoesDown() {
        assertEquals(454L, EnergyBalance.roundUpToThreeDigits(454));
        assertEquals(999L, EnergyBalance.roundUpToThreeDigits(999));
        assertEquals(29_100L, EnergyBalance.roundUpToThreeDigits(29_031));
        assertEquals(63_000L, EnergyBalance.roundUpToThreeDigits(63_000));
        assertEquals(1_350_000L, EnergyBalance.roundUpToThreeDigits(1_344_000));
        assertEquals(1_000_000L, EnergyBalance.roundUpToThreeDigits(1_000_000));
    }
}
