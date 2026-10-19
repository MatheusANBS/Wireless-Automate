package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameCacheTest {
    @Test
    void firstCallComputes() {
        assertFalse(new FrameCache().reuse(0, null));
    }

    @Test
    void sameFrameAndTokenReuses() {
        FrameCache cache = new FrameCache();
        Object token = new Object();
        cache.reuse(5, token);
        assertTrue(cache.reuse(5, token));
        assertTrue(cache.reuse(5, token));
    }

    @Test
    void newFrameComputes() {
        FrameCache cache = new FrameCache();
        cache.reuse(5, null);
        assertFalse(cache.reuse(6, null));
        assertTrue(cache.reuse(6, null));
    }

    @Test
    void otherTokenComputesEvenIfEqual() {
        FrameCache cache = new FrameCache();
        cache.reuse(5, new String("aviso"));
        assertFalse(cache.reuse(5, new String("aviso")));
    }

    @Test
    void invalidateComputesInTheSameFrame() {
        FrameCache cache = new FrameCache();
        cache.reuse(5, null);
        cache.invalidate();
        assertFalse(cache.reuse(5, null));
        assertTrue(cache.reuse(5, null));
    }
}
