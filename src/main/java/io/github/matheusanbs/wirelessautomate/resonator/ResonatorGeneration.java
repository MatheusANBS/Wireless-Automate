package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * A geração do Núcleo, um tick por vez. O custo por tick é o mínimo: a geração e a capacidade já estão em cache no
 * Núcleo ({@link ResonanceCoreBlockEntity#generationPerTick}), então o tick é uma soma, um corte pelo espaço do
 * buffer e a soma do desgaste ({@link CoreWear}); sem alocação e sem consulta ao mundo. O desgaste dos cristais, a
 * deriva e a recarga ficam com o {@link ResonatorBatch}, de 20 em 20 ticks.
 */
public final class ResonatorGeneration {
    private ResonatorGeneration() {
    }

    /** Se o Núcleo gera: montado e com geração por tick (em cache) maior que zero. */
    static boolean generates(ResonanceCoreBlockEntity core) {
        return core.formed() && core.generationPerTick() > 0;
    }

    /**
     * Gera um tick: soma o {@code generationPerTick} cortado no espaço que sobra no buffer e guarda o gerado e o
     * "cheio" para o desgaste. Devolve se a energia subiu de 0 com alguém esperando, o aviso que acorda quem tira da
     * Porta de Energia: o chamador avisa depois ({@link ResonanceCoreBlockEntity#notifyPorts}), fora do laço do
     * gerenciador, porque o aviso acorda vizinhos que podem mudar blocos. Quem tira todo tick esvazia o buffer a cada
     * tick e a energia sobe de 0 a cada tick; sem a marca de espera ({@link ResonanceCoreBlockEntity#takeStarved})
     * isso avisaria os vizinhos das Portas todo tick sem ninguém dormindo.
     */
    static boolean tick(ResonanceCoreBlockEntity core) {
        long perTick = core.generationPerTick();
        if (perTick <= 0 || !core.formed()) {
            return false;
        }
        long energy = core.energy();
        long generated = Math.min(perTick, core.capacity() - energy);
        if (generated <= 0) {
            core.wear().add(0, perTick);
            return false;
        }
        core.wear().add(generated, perTick);
        core.addEnergy(generated);
        return energy == 0 && core.takeStarved();
    }

    /** Um tick de geração e, se a energia subiu de 0, o aviso às Portas (para os GameTests e o apoio de teste). */
    public static void tickAndNotify(ResonanceCoreBlockEntity core) {
        if (tick(core)) {
            core.notifyPorts(true, false);
        }
    }
}
