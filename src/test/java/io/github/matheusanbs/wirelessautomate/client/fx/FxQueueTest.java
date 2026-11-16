package io.github.matheusanbs.wirelessautomate.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.renderer.MultiBufferSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FxQueueTest {
    private static final FxJob JOB = new FxJob() {
        @Override
        public void draw(FxContext ctx, MultiBufferSource buffers) {
        }

        @Override
        public double distanceSq(FxContext ctx) {
            return 0;
        }
    };

    @AfterEach
    void limpa() {
        FxQueue.clear();
        FxQueue.setShadowDetector(null);
    }

    @Test
    void recusaJobNaPassadaDeSombra() {
        FxQueue.setShadowDetector(() -> true);
        FxQueue.submit(JOB);
        assertTrue(FxQueue.isEmpty());
    }

    @Test
    void aceitaJobForaDaPassadaDeSombra() {
        FxQueue.setShadowDetector(() -> false);
        FxQueue.submit(JOB);
        FxQueue.submit(JOB);
        assertEquals(2, FxQueue.size());
        FxQueue.clear();
        assertTrue(FxQueue.isEmpty());
    }
}
