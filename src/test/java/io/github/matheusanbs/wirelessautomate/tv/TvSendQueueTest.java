package io.github.matheusanbs.wirelessautomate.tv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TvSendQueueTest {
    private static TvSendQueue<Integer> queue(int... sizes) {
        TvSendQueue<Integer> queue = new TvSendQueue<>();
        for (int size : sizes) {
            queue.add(size);
        }
        return queue;
    }

    @Test
    void saiNaOrdemOQueCabeNoOrcamentoEORestoEspera() {
        TvSendQueue<Integer> queue = queue(100, 100, 100, 50);
        List<Integer> sent = new ArrayList<>();
        assertEquals(200, queue.drain(256, Integer::intValue, sent::add));
        assertEquals(List.of(100, 100), sent);
        assertEquals(2, queue.size());
        sent.clear();
        assertEquals(150, queue.drain(256, Integer::intValue, sent::add), "o tick seguinte leva o resto");
        assertEquals(List.of(100, 50), sent);
        assertTrue(queue.isEmpty());
    }

    @Test
    void oPrimeiroSempreSaiMesmoMaiorQueOOrcamento() {
        TvSendQueue<Integer> queue = queue(1000, 10);
        List<Integer> sent = new ArrayList<>();
        assertEquals(1000, queue.drain(256, Integer::intValue, sent::add));
        assertEquals(List.of(1000), sent, "o grande sai sozinho e o pequeno espera");
        assertEquals(1, queue.size());
    }

    @Test
    void itemVencidoSaiSemContar() {
        TvSendQueue<Integer> queue = queue(-1, 200, -1, 50, 30);
        List<Integer> sent = new ArrayList<>();
        assertEquals(250, queue.drain(256, Integer::intValue, sent::add));
        assertEquals(List.of(200, 50), sent, "os vencidos não vão e não pesam");
        assertEquals(List.of(30), drainAll(queue));
    }

    @Test
    void tirarOsQueCasam() {
        TvSendQueue<Integer> queue = queue(1, 2, 3, 2);
        assertTrue(queue.removeIf(i -> i == 2));
        assertEquals(List.of(1, 3), drainAll(queue));
    }

    private static List<Integer> drainAll(TvSendQueue<Integer> queue) {
        List<Integer> sent = new ArrayList<>();
        queue.drain(Integer.MAX_VALUE, Integer::intValue, sent::add);
        return sent;
    }
}
