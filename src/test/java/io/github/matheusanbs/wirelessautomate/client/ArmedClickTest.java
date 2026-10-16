package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArmedClickTest {
    private final ArmedClick click = new ArmedClick();

    @Test
    void startsDisarmed() {
        assertFalse(click.armed(0));
        assertFalse(click.armed(1_000_000));
    }

    @Test
    void firstClickArmsAndSecondConfirms() {
        assertFalse(click.click(1000));
        assertTrue(click.armed(1000));
        assertTrue(click.click(2000));
        // confirmado, volta a desarmado: o próximo clique arma de novo
        assertFalse(click.armed(2000));
        assertFalse(click.click(2100));
    }

    @Test
    void armedForThreeSeconds() {
        click.arm(1000);
        assertTrue(click.armed(1000 + ArmedClick.WINDOW_MS - 1));
        assertFalse(click.armed(1000 + ArmedClick.WINDOW_MS));
    }

    @Test
    void secondClickAfterTheWindowOnlyArmsAgain() {
        assertFalse(click.click(1000));
        assertFalse(click.click(1000 + ArmedClick.WINDOW_MS));
        assertTrue(click.armed(1000 + ArmedClick.WINDOW_MS));
        assertTrue(click.click(1000 + ArmedClick.WINDOW_MS + 10));
    }

    @Test
    void disarmCancels() {
        click.arm(1000);
        click.disarm();
        assertFalse(click.armed(1001));
        assertFalse(click.click(1002));
    }
}
