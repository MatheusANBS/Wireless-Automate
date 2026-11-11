package io.github.matheusanbs.wirelessautomate.dock;

import static io.github.matheusanbs.wirelessautomate.dock.DockStatus.State.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DockStatusTest {
    private static final int NONE = DockStatus.NO_REFUSAL;

    @Test
    void emptySideIsFree() {
        assertEquals(FREE, DockStatus.of(false, 0, NONE, false, false, false, 0));
    }

    @Test
    void machinesWithoutProviderIsNoProvider() {
        assertEquals(NO_PROVIDER, DockStatus.of(false, 2, DockStatus.MIXED, true, true, false, 3));
    }

    @Test
    void providerWithoutMachines() {
        assertEquals(NO_MACHINES, DockStatus.of(true, 0, DockStatus.CRAFTING_PATTERN, true, true, false, 3));
    }

    @Test
    void craftingPatternBeatsTheRest() {
        assertEquals(CRAFTING_PATTERN, DockStatus.of(true, 2, DockStatus.CRAFTING_PATTERN, true, true, false, 3));
    }

    @Test
    void mixedBeatsFullAndWorking() {
        assertEquals(MIXED, DockStatus.of(true, 2, DockStatus.MIXED, true, true, false, 3));
    }

    @Test
    void providerFullBeatsAllFull() {
        assertEquals(PROVIDER_FULL, DockStatus.of(true, 2, NONE, true, true, false, 3));
    }

    @Test
    void allFullBeatsWorking() {
        assertEquals(ALL_FULL, DockStatus.of(true, 2, NONE, false, true, false, 3));
    }

    @Test
    void allFullBeatsPending() {
        assertEquals(ALL_FULL, DockStatus.of(true, 2, NONE, false, true, true, 3));
    }

    @Test
    void pendingBeatsWorking() {
        assertEquals(PENDING, DockStatus.of(true, 2, NONE, false, false, true, 3));
    }

    @Test
    void noMachinesBeatsPending() {
        assertEquals(NO_MACHINES, DockStatus.of(true, 0, NONE, false, false, true, 0));
    }

    @Test
    void workingBeatsReady() {
        assertEquals(WORKING, DockStatus.of(true, 2, NONE, false, false, false, 1));
    }

    @Test
    void readyWhenNothingIsWrong() {
        assertEquals(READY, DockStatus.of(true, 2, NONE, false, false, false, 0));
    }

    @Test
    void severityOrderIsTheEnumOrder() {
        assertEquals(0, NO_PROVIDER.ordinal());
        assertEquals(6, PENDING.ordinal());
        assertEquals(8, READY.ordinal());
        assertEquals(9, FREE.ordinal());
    }
}
