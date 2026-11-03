package io.github.matheusanbs.wirelessautomate.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** O que o motor sabe de uma máquina num instante: o alcance dela e o estado de cada face em uso (um por tipo e face). */
public record VerdictFacts(Reach reach, List<FaceFact> faces) {
    /** Se a máquina está ao alcance do roteador e carregada; o resto diz por que não. */
    public enum Reach { OK, UNLOADED, NO_UPGRADE, OUT_OF_RANGE, OVER_LIMIT, REPLACED, MISSING }

    /**
     * Uma face em uso ({@code source} ou {@code destination}).
     *
     * @param network          a rede da face, {@code null} = sem rede
     * @param sourceAsleep     a origem dorme (nada a mover agora)
     * @param waitsDestination o sono é por destinos que recusam ({@code Port.waitsDestination})
     * @param fullDestinations quantos destinos dela estão cheios ({@code NodeProbe.isFull}); {@code fullPositions} são as
     *                         posições das máquinas deles, até 8
     * @param selfFull         a própria face, como destino, está cheia
     * @param filtered         a face tem filtro (o embutido ou cartões)
     */
    public record FaceFact(ResourceType type, FaceSide face, boolean source, boolean destination, @Nullable UUID network,
            boolean groupPaused, boolean stopped, boolean moving, boolean sourceAsleep, boolean waitsDestination,
            int fullDestinations, List<Long> fullPositions, boolean selfFull, boolean filtered) {
        public boolean inUse() {
            return source || destination;
        }

        public static Builder builder(ResourceType type, FaceSide face) {
            return new Builder(type, face);
        }

        /** Monta um fato (usado nos testes e pelo {@code VerdictProbe}). */
        public static final class Builder {
            private final ResourceType type;
            private final FaceSide face;
            private boolean source;
            private boolean destination;
            private UUID network;
            private boolean paused;
            private boolean stopped;
            private boolean moving;
            private boolean asleep;
            private boolean waits;
            private int full;
            private List<Long> positions = List.of();
            private boolean selfFull;
            private boolean filtered;

            private Builder(ResourceType type, FaceSide face) {
                this.type = type;
                this.face = face;
            }

            public Builder source() { source = true; return this; }
            public Builder destination() { destination = true; return this; }
            public Builder network(UUID id) { network = id; return this; }
            public Builder paused() { paused = true; return this; }
            public Builder stopped() { stopped = true; return this; }
            public Builder moving() { moving = true; return this; }
            public Builder selfFull() { selfFull = true; return this; }
            public Builder filtered() { filtered = true; return this; }

            /** Dorme porque {@code n} destinos recusam. */
            public Builder asleepFull(int n) {
                asleep = true;
                waits = true;
                full = n;
                List<Long> list = new ArrayList<>(n);
                for (int i = 0; i < Math.min(n, 8); i++) {
                    list.add((long) i);
                }
                positions = list;
                return this;
            }

            /** Dorme sem ter o que dar. */
            public Builder asleepEmpty() {
                asleep = true;
                waits = false;
                return this;
            }

            public FaceFact build() {
                return new FaceFact(type, face, source, destination, network, paused, stopped, moving, asleep, waits,
                        full, List.copyOf(positions), selfFull, filtered);
            }
        }
    }
}
