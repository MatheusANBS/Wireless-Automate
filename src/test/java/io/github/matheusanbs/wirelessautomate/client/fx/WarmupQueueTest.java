package io.github.matheusanbs.wirelessautomate.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class WarmupQueueTest {
    private static List<ResourceLocation> five() {
        List<ResourceLocation> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(ResourceLocation.fromNamespaceAndPath("wirelessautomate", "textures/fx/t" + i + ".png"));
        }
        return list;
    }

    @Test
    void entregaAosPoucosNaOrdemDada() {
        List<ResourceLocation> all = five();
        WarmupQueue queue = new WarmupQueue(all);
        List<ResourceLocation> got = new ArrayList<>();

        List<ResourceLocation> a = queue.next(2);
        List<ResourceLocation> b = queue.next(2);
        List<ResourceLocation> c = queue.next(2);
        assertEquals(2, a.size());
        assertEquals(2, b.size());
        assertEquals(1, c.size());
        got.addAll(a);
        got.addAll(b);
        got.addAll(c);
        assertEquals(all, got);

        assertTrue(queue.next(2).isEmpty());
        assertTrue(queue.done());
    }

    @Test
    void naoTerminaAntesDaUltima() {
        WarmupQueue queue = new WarmupQueue(five());
        queue.next(4);
        assertFalse(queue.done());
        queue.next(1);
        assertTrue(queue.done());
    }
}
