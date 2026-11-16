package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ArmilarSceneTest {
    @AfterEach
    void descongela() {
        ArmilarRenderer.freezeSecondsForTests = Double.NaN;
    }

    @Test
    void aFrenteDoNucleoViraAFaceDoMundo() {
        assertEquals(Face.NORTH, ArmilarScene.faceOf(Direction.NORTH));
        assertEquals(Face.SOUTH, ArmilarScene.faceOf(Direction.SOUTH));
        assertEquals(Face.WEST, ArmilarScene.faceOf(Direction.WEST));
        assertEquals(Face.EAST, ArmilarScene.faceOf(Direction.EAST));
    }

    @Test
    void oRelogioEODoMundoOuOParadoDoE2e() {
        ArmilarScene scene = new ArmilarScene(BlockPos.ZERO);
        assertEquals(0.025, scene.clock(0.5f), 1e-9);
        ArmilarRenderer.freezeSecondsForTests = 12.5;
        assertEquals(12.5, scene.clock(0.9f), 0);
    }

    @Test
    void semViewAMontagemEstaCompletaENadaFoiSintonizado() {
        ArmilarScene scene = new ArmilarScene(BlockPos.ZERO);
        assertEquals(ArmilarAssembly.A_MAX, scene.assembly(0), 0);
        assertEquals(1e9, scene.sinceTuned(0), 0);
        assertEquals(0f, scene.flow(0.5f), 0);
    }

    @Test
    void relogioEmMundoLongoGuardaOParcial() {
        // 2e7 ticks (cerca de 11 dias): somar o parcial em float o perderia (a ulp é 2)
        long ticks = 20_000_000L;
        double a = ArmilarScene.worldSeconds(ticks, 0f);
        double b = ArmilarScene.worldSeconds(ticks, 0.5f);
        assertEquals(0.025, b - a, 1e-9);
        assertEquals(0.025, ArmilarScene.seconds(10_000_000L, 0.5f) - ArmilarScene.seconds(10_000_000L, 0f), 1e-9);
    }
}
