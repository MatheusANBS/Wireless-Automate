package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.resonator.render.ArmilarSoundTriggers.Event;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArmilarSoundTriggersTest {
    private static final long NEVER = ArmilarSoundTriggers.NEVER;

    /** Quem ouve: guarda cada evento com o tick em que saiu. */
    private static final class Log implements ArmilarSoundTriggers.Sink {
        final List<Event> events = new ArrayList<>();
        final List<Long> ticks = new ArrayList<>();
        final List<Integer> chambers = new ArrayList<>();
        long now;

        @Override
        public void play(Event event, int chamber) {
            events.add(event);
            ticks.add(now);
            chambers.add(chamber);
        }

        long count(Event event) {
            return events.stream().filter(e -> e == event).count();
        }
    }

    private static ChamberView view(byte[] chambers, boolean tuned, long formedAt, byte flow, boolean full) {
        return new ChamberView(chambers, new byte[0], tuned ? 7 : 3, tuned, -1, 60, 0, formedAt, new int[3],
                new byte[] {flow}, full);
    }

    private static ChamberView tuned(byte flow) {
        return view(new byte[] {1, 2, 1}, true, 0, flow, false);
    }

    private static void run(ArmilarSoundTriggers triggers, ChamberView view, long from, long to, Log log) {
        for (long t = from; t <= to; t++) {
            log.now = t;
            triggers.tick(view, true, t, t * 0.05, log);
        }
    }

    @Test
    void conjuncaoEmSintoniaComVazaoTocaCadaEventoUmaVezPorVolta() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        ChamberView view = tuned((byte) 7);
        run(triggers, view, 100, 199, log);
        log.events.clear();
        log.ticks.clear();
        // a volta da conjunção tem 104,7 ticks: 105 ticks seguidos cobrem uma conjunção inteira
        run(triggers, view, 200, 304, log);
        for (Event e : new Event[] {Event.CONJ_CHARGE, Event.CONJ_BURST, Event.CONJ_ARC, Event.CONJ_IMPACT}) {
            assertEquals(1, log.count(e), e.name());
        }
        // a ordem é a da animação: carga, arco (cabeça parte em 0,1), estouro (0,15), impacto (0,6)
        assertEquals(List.of(Event.CONJ_CHARGE, Event.CONJ_ARC, Event.CONJ_BURST, Event.CONJ_IMPACT), log.events);
    }

    @Test
    void saltoCurtoDeTicksNaoPerdeNemRepeteOEvento() {
        ChamberView view = tuned((byte) 7);
        for (int step = 2; step <= (int) ArmilarSoundTriggers.MAX_JUMP_TICKS; step++) {
            ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
            Log log = new Log();
            for (long t = 100; t < 100 + 4 * step; t += step) {
                log.now = t;
                triggers.tick(view, true, t, t * 0.05, log);
            }
            log.events.clear();
            // uma volta inteira andando de 2 em 2 (ou 3 em 3) ticks: cada evento toca uma vez
            for (long t = 200; t <= 200 + 105 + step; t += step) {
                log.now = t;
                triggers.tick(view, true, t, t * 0.05, log);
            }
            for (Event e : new Event[] {Event.CONJ_CHARGE, Event.CONJ_BURST, Event.CONJ_ARC, Event.CONJ_IMPACT}) {
                assertEquals(1, log.count(e), "salto " + step + " " + e.name());
            }
        }
        // um salto grande (mais de 3 ticks) recomeça o laço sem tocar o que passou
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        log.now = 100;
        triggers.tick(view, true, 100, 100 * 0.05, log);
        log.now = 230;
        triggers.tick(view, true, 230, 230 * 0.05, log);
        assertTrue(log.events.isEmpty());
    }

    @Test
    void loopDerrubadoPeloEngineSeDaPorMortoDepoisDaTolerancia() {
        long t = ArmilarLoopLife.GRACE_TICKS;
        assertTrue(ArmilarLoopLife.alive(false, true, 500, 100));
        assertTrue(ArmilarLoopLife.alive(false, false, 100 + t, 100));
        assertFalse(ArmilarLoopLife.alive(false, false, 100 + t + 1, 100));
        assertFalse(ArmilarLoopLife.alive(true, true, 100, 100));
    }

    @Test
    void semVazaoNaoHaArcoNemImpacto() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        run(triggers, tuned((byte) 0), 100, 420, log);
        assertTrue(log.count(Event.CONJ_CHARGE) >= 3);
        assertTrue(log.count(Event.CONJ_BURST) >= 3);
        assertEquals(0, log.count(Event.CONJ_ARC));
        assertEquals(0, log.count(Event.CONJ_IMPACT));
    }

    @Test
    void foraDeSintoniaNaoHaConjuncao() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        run(triggers, view(new byte[] {1, 1, 1}, false, 0, (byte) 7, false), 100, 420, log);
        assertTrue(log.events.isEmpty(), log.events.toString());
    }

    @Test
    void relogioParadoNaoCruzaNada() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        ChamberView view = tuned((byte) 7);
        for (long t = 100; t < 400; t++) {
            log.now = t;
            triggers.tick(view, true, t, 11.1, log);
        }
        assertTrue(log.events.isEmpty());
    }

    @Test
    void primeiroTickSoTomaOEstadoDePartida() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        // a cena nasce no meio da janela da conjunção, sintonizada, com um Opaco recarregando e um cristal rachado
        ChamberView view = new ChamberView(new byte[] {3, 5, 1}, new byte[0], 7, true, 0, 1000, 1, 0, new int[3],
                new byte[] {7}, false);
        run(triggers, view, 219, 219, log);
        assertTrue(log.events.isEmpty(), log.events.toString());
    }

    @Test
    void montagemRecenteTocaUmaVezENaoNaCenaRecriada() {
        ChamberView view = view(new byte[] {1, 1, 1}, false, 1000, (byte) 0, false);
        Log log = new Log();
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        run(triggers, view, 1010, 1100, log);
        assertEquals(List.of(Event.ASSEMBLE), log.events);
        // o chunk recarrega 5 s depois: a cena nova nasce com a memória da montagem e fica quieta
        Log again = new Log();
        ArmilarSoundTriggers recreated = new ArmilarSoundTriggers(triggers.assembledFor());
        run(recreated, view, 1030, 1100, again);
        assertTrue(again.events.isEmpty());
        // e uma Armilar montada há muito tempo, que o jogador só agora viu, também fica quieta
        Log late = new Log();
        run(new ArmilarSoundTriggers(NEVER), view, 1000 + ArmilarSoundTriggers.FRESH_TICKS, 1300, late);
        assertTrue(late.events.isEmpty());
    }

    @Test
    void trocaDeSintoniaTocaUmaVezCadaLado() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        ChamberView solta = view(new byte[] {1, 1, 1}, false, 0, (byte) 0, false);
        ChamberView presa = view(new byte[] {1, 1, 1}, true, 0, (byte) 0, false);
        run(triggers, solta, 100, 110, log);
        run(triggers, presa, 111, 130, log);
        run(triggers, solta, 131, 150, log);
        assertEquals(List.of(Event.TUNE_LOCK, Event.TUNE_LOST), log.events.stream()
                .filter(e -> e == Event.TUNE_LOCK || e == Event.TUNE_LOST).toList());
    }

    @Test
    void rachaduraEOpacoSaemDaTransicaoDaCamara() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        run(triggers, view(new byte[] {1, 2, 1, 0}, false, 0, (byte) 0, false), 100, 105, log);
        run(triggers, view(new byte[] {3, 6, 1, 5}, false, 0, (byte) 0, false), 106, 110, log);
        assertEquals(List.of(Event.DULL, Event.CRACK), log.events);
        assertEquals(List.of(0, 1), log.chambers);
        // um cristal já Opaco que entra numa câmara vazia não é transição de cristal vivo
        Log insert = new Log();
        ArmilarSoundTriggers other = new ArmilarSoundTriggers(NEVER);
        run(other, view(new byte[] {0, 0}, false, 0, (byte) 0, false), 100, 105, insert);
        run(other, view(new byte[] {3, 5}, false, 0, (byte) 0, false), 106, 110, insert);
        assertTrue(insert.events.isEmpty());
    }

    @Test
    void graosNascemNoMaximoTresPorSegundo() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        ChamberView recharging = new ChamberView(new byte[] {3, 1, 1}, new byte[0], 3, false, 100, 100000, 1, 0,
                new int[3], new byte[] {0}, false);
        run(triggers, recharging, 100, 400, log);
        long grains = log.count(Event.GRAIN);
        // 2 por segundo: um a cada 10 ticks, e nunca um grão no tick do primeiro
        assertTrue(grains >= 28 && grains <= 30, "grãos " + grains);
        assertMaxPerSecond(log, Event.GRAIN);
        assertTrue(log.chambers.stream().allMatch(c -> c == 0));
    }

    @Test
    void fimDaRecargaTocaNoCristalUmaVez() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        ChamberView recharging = new ChamberView(new byte[] {1, 3, 1}, new byte[0], 3, false, 100, 60, 1, 0,
                new int[3], new byte[] {0}, false);
        run(triggers, recharging, 150, 200, log);
        assertEquals(1, log.count(Event.DONE));
        assertEquals(160L, log.ticks.get(log.events.indexOf(Event.DONE)));
        assertEquals(1, log.chambers.get(log.events.indexOf(Event.DONE)));
        // com a view cheia chegando depois, o clarão já tocou e não toca de novo
        run(triggers, view(new byte[] {1, 1, 1}, false, 0, (byte) 0, false), 201, 260, log);
        assertEquals(1, log.count(Event.DONE));
    }

    @Test
    void servidorTerminaAntesTocaOFimNaView() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        run(triggers, new ChamberView(new byte[] {1, 3, 1}, new byte[0], 3, false, 100, 60, 1, 0, new int[3],
                new byte[] {0}, false), 100, 139, log);
        run(triggers, view(new byte[] {1, 1, 1}, false, 0, (byte) 0, false), 140, 170, log);
        assertEquals(1, log.count(Event.DONE));
        assertEquals(140L, log.ticks.get(log.events.indexOf(Event.DONE)));
    }

    @Test
    void nenhumEventoPassaDeTresPorSegundoNemRepeteNoMesmoTick() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        // a sintonia vira a cada tick (10 transições por segundo): só 3 por segundo podem tocar
        for (long t = 100; t < 300; t++) {
            log.now = t;
            triggers.tick(view(new byte[] {1, 1, 1}, t % 2 == 0, 0, (byte) 0, false), true, t, t * 0.05, log);
        }
        long total = log.count(Event.TUNE_LOCK) + log.count(Event.TUNE_LOST);
        assertTrue(total > 0);
        assertMaxPerSecond(log, Event.TUNE_LOCK);
        assertMaxPerSecond(log, Event.TUNE_LOST);
        assertTrue(total <= 2 * ArmilarSoundTriggers.MAX_PER_SECOND * 10, "total " + total);
        // chamar de novo no mesmo tick não faz nada
        int before = log.events.size();
        log.now = 299;
        triggers.tick(view(new byte[] {1, 1, 1}, true, 0, (byte) 0, false), true, 299, 299 * 0.05, log);
        assertEquals(before, log.events.size());
    }

    @Test
    void desmontarVoltaAoEstadoDePartida() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        ChamberView presa = tuned((byte) 7);
        run(triggers, presa, 100, 110, log);
        assertTrue(triggers.active());
        assertTrue(triggers.sunLevel() > 0);
        triggers.tick(presa, false, 111, 5.55, log);
        assertFalse(triggers.active());
        assertEquals(0, triggers.sunLevel());
        assertEquals(0, triggers.flowLevel());
        assertFalse(triggers.full());
        // montada de novo, o primeiro tick é de partida: a trava não toca outra vez
        run(triggers, presa, 112, 120, log);
        assertEquals(0, log.count(Event.TUNE_LOCK));
    }

    @Test
    void loopsSeguemEficienciaVazaoEBufferCheio() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        run(triggers, view(new byte[] {1, 1, 1}, false, 0, (byte) 0, false), 100, 120, log);
        double sunSolta = triggers.sunLevel();
        assertEquals(0, triggers.flowLevel());
        assertEquals(ArmilarSoundTriggers.PITCH_MIN, triggers.flowPitch(), 1e-9);
        run(triggers, view(new byte[] {1, 1, 1}, true, 0, (byte) 7, true), 121, 250, log);
        assertTrue(triggers.sunLevel() > sunSolta);
        assertTrue(triggers.flowLevel() > 0.9);
        assertEquals(ArmilarSoundTriggers.PITCH_MAX, triggers.flowPitch(), 0.01);
        assertTrue(triggers.full());
    }

    @Test
    void ignicaoDaMontagemSobeOLoopDoSol() {
        ArmilarSoundTriggers triggers = new ArmilarSoundTriggers(NEVER);
        Log log = new Log();
        ChamberView view = view(new byte[] {1, 1, 1}, true, 1000, (byte) 7, false);
        run(triggers, view, 1000, 1000, log);
        double inicio = triggers.sunLevel();
        run(triggers, view, 1001, 1100, log);
        assertTrue(inicio < 0.1, "início " + inicio);
        assertTrue(triggers.sunLevel() > 0.9);
    }

    /** Nenhuma janela de 20 ticks tem mais de 3 eventos do tipo. */
    private static void assertMaxPerSecond(Log log, Event event) {
        List<Long> ticks = new ArrayList<>();
        for (int i = 0; i < log.events.size(); i++) {
            if (log.events.get(i) == event) {
                ticks.add(log.ticks.get(i));
            }
        }
        for (int i = ArmilarSoundTriggers.MAX_PER_SECOND; i < ticks.size(); i++) {
            long span = ticks.get(i) - ticks.get(i - ArmilarSoundTriggers.MAX_PER_SECOND);
            assertTrue(span >= 20, event + ": " + (ArmilarSoundTriggers.MAX_PER_SECOND + 1) + " em " + span + " ticks");
        }
    }
}
