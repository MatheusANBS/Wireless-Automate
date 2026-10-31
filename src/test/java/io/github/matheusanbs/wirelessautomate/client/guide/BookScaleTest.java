package io.github.matheusanbs.wirelessautomate.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BookScaleTest {
    @Test
    void kEhOMaiorParQueCabe() {
        assertEquals(2, BookScale.pixelsPerTexel(854, 480));
        assertEquals(4, BookScale.pixelsPerTexel(1920, 1080));
        // 6 × 315 + 32 = 1922: não cabe em 1920, fica 4
        assertEquals(4, BookScale.pixelsPerTexel(1921, 1400));
        assertEquals(6, BookScale.pixelsPerTexel(2560, 1440));
    }

    @Test
    void nuncaMenosQueDois() {
        assertEquals(2, BookScale.pixelsPerTexel(320, 240));
    }

    @Test
    void mouseVoltaParaOPixelDaTextura() {
        BookScale s = BookScale.of(1920, 1080, 3);
        // k = 4: o livro (315 × 196) ocupa 1260 × 784, centrado
        assertEquals(4, s.k());
        assertEquals(0, s.texX(330 / 3.0), 1e-9);
        assertEquals(0, s.texY(148 / 3.0), 1e-9);
        assertEquals(10, s.texX(s.guiX(10)), 1e-9);
        assertEquals(4, BookScale.unit(25, BookScale.LEFT_X), 1e-9);
    }
}
