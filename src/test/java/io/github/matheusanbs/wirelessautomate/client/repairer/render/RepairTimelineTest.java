package io.github.matheusanbs.wirelessautomate.client.repairer.render;

import org.junit.jupiter.api.Test;

import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairTimelineTest {
    private static final double EPS = 1e-9;

    @Test
    void secondsAndaComOTempoDaLinha() {
        assertEquals(0, RepairTimeline.seconds(1140, 140, 1000), EPS);
        assertEquals(3.5, RepairTimeline.seconds(1140, 140, 1070), EPS);
        assertEquals(7, RepairTimeline.seconds(1140, 140, 1140), EPS);
        assertEquals(7, RepairTimeline.seconds(1140, 140, 2000), EPS);
        assertEquals(0, RepairTimeline.seconds(1140, 140, 900), EPS);
        assertEquals(7, RepairTimeline.seconds(1140, 0, 1000), EPS);
    }

    @Test
    void bordasDasFases() {
        assertEquals(0, RepairTimeline.beam(0.99), EPS);
        assertEquals(1, RepairTimeline.beam(3), EPS);
        assertEquals(0, RepairTimeline.beam(6.45), EPS);
        assertEquals(0, RepairTimeline.cracks(1), EPS);
        assertEquals(1, RepairTimeline.cracks(5.5), EPS);
        for (int k = 0; k < 4; k++) {
            assertEquals(1, RepairTimeline.dissolve(6.3 + 0.1 * k, k), EPS);
            assertEquals(0, RepairTimeline.dissolve(5.5, k), EPS);
            assertEquals(1, RepairTimeline.orbitAppear(1.2, k), EPS);
        }
        assertEquals(0, RepairTimeline.orbitAppear(0.3, 0), EPS);
        assertEquals(0, RepairTimeline.flash(6.29), EPS);
        assertEquals(1, RepairTimeline.flash(6.3), EPS);
        assertEquals(0, RepairTimeline.flash(7), EPS);
        assertEquals(0, RepairTimeline.crystalGlow(5.5), EPS);
        assertEquals(1, RepairTimeline.crystalGlow(6.4), EPS);
        assertEquals(0, RepairTimeline.crystalGlow(7), EPS);
        assertEquals(4, RepairTimeline.beamBand(3));
        assertEquals(7, RepairTimeline.beamBand(6.1));
        assertEquals(0, RepairTimeline.vibration(6.3), EPS);
        assertEquals(0, RepairTimeline.exit(6.7), EPS);
        assertEquals(1, RepairTimeline.exit(7), EPS);
        assertEquals(0, RepairTimeline.shockAlpha(7), EPS);
        double last = -1;
        for (double s = 6.3; s <= 7; s += 0.01) {
            double r = RepairTimeline.shockRadius(s);
            assertTrue(r >= last, "a onda de choque recuou em " + s);
            last = r;
        }
    }

    @Test
    void continuoNasBordas() {
        double[] borders = {0.5, 1.0, 5.5, 6.3, 6.4, 6.45};
        double h = 1e-6;
        // O clarão, a onda de choque e a vibração começam de propósito no corte do clarão (6,3 s).
        DoubleUnaryOperator[] fns = {RepairTimeline::emitter, RepairTimeline::pedestal, RepairTimeline::beam,
            RepairTimeline::beamWidth, RepairTimeline::cracks, RepairTimeline::crystalGlow, RepairTimeline::orbitAngle,
            RepairTimeline::orbitShrink, RepairTimeline::exit, RepairTimeline::shockRadius,
            s -> RepairTimeline.orbitAppear(s, 2), s -> RepairTimeline.dissolve(s, 1),
            s -> RepairTimeline.runeWave(s, 1.0)};
        for (int f = 0; f < fns.length; f++) {
            for (double b : borders) {
                assertTrue(Math.abs(fns[f].applyAsDouble(b - h) - fns[f].applyAsDouble(b + h)) < 1e-3,
                        "descontínua: função " + f + " em " + b);
            }
        }
    }

    @Test
    void envelopesFicamNaFaixa() {
        for (double s = 0; s <= 7.0001; s += 0.01) {
            double[] unit = {RepairTimeline.pedestal(s), RepairTimeline.beam(s), RepairTimeline.cracks(s),
                RepairTimeline.crystalGlow(s), RepairTimeline.flash(s), RepairTimeline.shockAlpha(s),
                RepairTimeline.exit(s), RepairTimeline.orbitAppear(s, 1), RepairTimeline.dissolve(s, 2),
                RepairTimeline.runeWave(s, 0.8), RepairTimeline.orbitShrink(s) - 0.65};
            for (double v : unit) {
                assertTrue(v >= -1e-9 && v <= 1 + 1e-9, "fora de 0 a 1 em " + s + ": " + v);
            }
            double e = RepairTimeline.emitter(s);
            assertTrue(e >= 0.3 - 1e-9 && e <= 1.3 + 1e-9, "emissor fora da faixa em " + s);
        }
    }
}
