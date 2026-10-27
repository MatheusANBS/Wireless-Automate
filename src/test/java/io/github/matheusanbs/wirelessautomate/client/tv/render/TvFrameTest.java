package io.github.matheusanbs.wirelessautomate.client.tv.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TvFrameTest {
    private static final Path ATLAS =
            Path.of("src/main/resources/assets/wirelessautomate/textures/block/signal_screen/signal_screen.png");

    @Test
    void asFatiasSaoOpacasNoAtlas() throws IOException {
        assertTrue(Files.exists(ATLAS), "atlas não encontrado: " + ATLAS.toAbsolutePath());
        BufferedImage image = ImageIO.read(ATLAS.toFile());
        assertEquals(TvFrame.ATLAS, image.getWidth());
        assertEquals(TvFrame.ATLAS, image.getHeight());
        for (TvFrame.Slice slice : TvFrame.ALL) {
            assertTrue(slice.x() >= 0 && slice.y() >= 0 && slice.x() + slice.width() <= TvFrame.ATLAS
                    && slice.y() + slice.height() <= TvFrame.ATLAS, slice.name() + " fora do atlas");
            for (int x = slice.x(); x < slice.x() + slice.width(); x++) {
                for (int y = slice.y(); y < slice.y() + slice.height(); y++) {
                    int alpha = image.getRGB(x, y) >>> 24;
                    assertEquals(255, alpha, slice.name() + " tem um texel transparente em " + x + "," + y);
                }
            }
        }
    }

    @Test
    void asFaixasBatemComOsCantos() {
        assertEquals(TvFrame.BAND, TvFrame.CORNER_TL.width() / TvFrame.PIXELS_PER_UNIT);
        assertEquals(TvFrame.BAND, TvFrame.CORNER_TL.height() / TvFrame.PIXELS_PER_UNIT);
        assertEquals(TvFrame.BAND_BOTTOM, TvFrame.CORNER_BL.height() / TvFrame.PIXELS_PER_UNIT);
        assertEquals(TvFrame.BAND_BOTTOM, TvFrame.CHIN.height() / TvFrame.PIXELS_PER_UNIT);
        assertEquals(TvFrame.UNITS_PER_BLOCK - 2 * TvFrame.BAND, TvFrame.CHIN.unitsWide());
    }
}
