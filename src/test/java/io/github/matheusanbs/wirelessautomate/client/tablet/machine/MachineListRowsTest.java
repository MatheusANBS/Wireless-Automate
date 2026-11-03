package io.github.matheusanbs.wirelessautomate.client.tablet.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.matheusanbs.wirelessautomate.client.tablet.machine.MachineState.State;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineStatus;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.Verdict.Tone;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * O que a lista da aba Máquina escreve de cada linha: o estado e a cor, a vazão e o nome ({@link MachineState}). O achatar
 * da página ({@link MachineListRows}) usa o {@code TabletMachineRows}, com Minecraft, que o JUnit não tem: o e2e da aba o cobre.
 */
class MachineListRowsTest {
    private static final long[] NONE = new long[0];

    private static void assertWord(MachineStatus status, String key, Tone tone) {
        State state = MachineState.of(status, NONE);
        assertEquals(key, state.key());
        assertEquals(tone, state.tone());
        assertNull(state.rateType());
    }

    @Test
    void fullIsWarnAndNoNetworkIsBad() {
        assertWord(MachineStatus.FULL, "full", Tone.WARN);
        assertWord(MachineStatus.NO_NETWORK, "no_network", Tone.BAD);
    }

    @Test
    void unloadedPausedAndSleepingAreMuted() {
        assertWord(MachineStatus.UNLOADED, "unloaded", Tone.MUTED);
        assertWord(MachineStatus.PAUSED, "paused", Tone.MUTED);
        assertWord(MachineStatus.SLEEPING, "sleeping", Tone.MUTED);
    }

    @Test
    void theOtherProblemsAreBad() {
        for (MachineStatus status : List.of(MachineStatus.NO_UPGRADE, MachineStatus.OUT_OF_RANGE,
                MachineStatus.OVER_LIMIT, MachineStatus.REPLACED, MachineStatus.MISSING)) {
            assertWord(status, status.name().toLowerCase(Locale.ROOT), Tone.BAD);
        }
    }

    @Test
    void movingShowsTheRateOfTheTypeWithTheMostFlow() {
        long[] flow = new long[ResourceType.values().length];
        flow[ResourceType.ITEM.ordinal()] = 96;
        flow[ResourceType.ENERGY.ordinal()] = 20;
        State state = MachineState.of(MachineStatus.MOVING, flow);
        assertEquals(ResourceType.ITEM, state.rateType());
        assertEquals(96, state.rate());
        assertNull(state.key());
        assertEquals(Tone.MUTED, state.tone());
        flow[ResourceType.ENERGY.ordinal()] = 60;
        flow[ResourceType.ITEM.ordinal()] = 8;
        assertEquals(ResourceType.ENERGY, MachineState.of(MachineStatus.MOVING, flow).rateType());
    }

    @Test
    void movingWithoutRateFallsBackToTheWord() {
        State state = MachineState.of(MachineStatus.MOVING, NONE);
        assertEquals("moving", state.key());
        assertNull(state.rateType());
    }

    @Test
    void nameUsesTheLabelThenTheBlockName() {
        assertEquals("Fornalha 3", MachineState.name("Fornalha 3", false, 1, "Fornalha"));
        assertEquals("Fornalha 3", MachineState.name("Fornalha 3", true, 0, "Fornalha"));
        assertEquals("Fornalha", MachineState.name("", true, 0, "Fornalha"));
        assertEquals("Fornalha #2", MachineState.name("", false, 2, "Fornalha"));
    }
}
