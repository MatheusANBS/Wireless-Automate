package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * As linhas da árvore na página do Tablet, abaixo dos nós abertos ({@link TabletTree}): a máquina presa, os grupos,
 * as máquinas dos grupos abertos e o "mais N" de um grupo cortado. Cada linha diz o nó de cima ({@code parent}, a
 * posição dele em {@link TabletSnapshot.Page#nodes()}); as linhas de um nó vêm juntas, logo depois dele na tela, e
 * os nós vêm em ordem. Os nós e estas linhas dividem o teto de {@link TabletSnapshot#MAX_NODES} da página.
 *
 * <p>O estado e a vazão das linhas de máquina ({@link MachineLive}) vão à parte, na ordem delas, para o pacote
 * pequeno ({@link #LIVE_CODEC}) levar só isso quando nada mais mudou; as outras linhas não têm o que mudar a cada
 * amostra.
 *
 * <p>No pacote, cada linha começa num byte: o tipo nos dois bits de baixo, as marcas nos de cima e, no último, se o
 * nó de cima é o mesmo da linha anterior (senão vem o índice dele). Os blocos dos grupos entram na paleta de
 * máquinas da página, e a posição de uma máquina vai como {@code long}: uma linha de máquina sem rótulo leva 10 a
 * 11 bytes, e o estado e a vazão dela, 2 bytes parada e 3 a 6 movendo um tipo.
 *
 * @param rows  as linhas, na ordem da tela
 * @param lives o estado e a vazão de cada {@link Machine}, na ordem delas
 */
public record TabletRows(List<Row> rows, List<MachineLive> lives) {
    public static final TabletRows EMPTY = new TabletRows(List.of(), List.of());
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final long[] NO_RATES = new long[2 * TYPES.length];

    private static final int KIND_ATTACHED = 0;
    private static final int KIND_GROUP = 1;
    private static final int KIND_MACHINE = 2;
    private static final int KIND_MORE = 3;
    private static final int FLAG_A = 1 << 2;
    private static final int FLAG_B = 1 << 3;
    private static final int FLAG_C = 1 << 4;
    private static final int SAME_PARENT = 1 << 7;

    /** Uma linha da árvore. */
    public sealed interface Row permits Attached, Group, Machine, More {
        /** A posição do nó de cima nos nós da página. */
        int parent();
    }

    /**
     * A máquina presa.
     *
     * @param side de que lado do roteador ela fica (o oposto da face onde ele está preso); vazio com o roteador
     *             descarregado
     */
    public record Attached(int parent, Optional<Direction> side) implements Row {
    }

    /**
     * Um grupo.
     *
     * @param id         o id do grupo no roteador (o do {@code EditTarget.group})
     * @param name       nome dado pelo jogador; vazio = o nome do bloco
     * @param total      máquinas do grupo, inclusive as que não aparecem
     * @param exceptions das máquinas guardadas no índice, quantas têm exceção
     */
    public record Group(int parent, int id, ResourceLocation block, String name, int total, int exceptions,
            boolean collapsed, boolean showAll) implements Row {
    }

    /**
     * Uma máquina, dentro do grupo da linha de grupo de cima.
     *
     * @param index o índice da máquina no roteador (o do {@code EditTarget.machine}, o "#N" menos 1)
     * @param pos   a posição ({@code BlockPos.asLong})
     * @param label rótulo dado pelo jogador; vazio = a tela usa "#N"
     */
    public record Machine(int parent, int index, long pos, String label, boolean exception) implements Row {
    }

    /** "… mais {@code hidden}" no fim do grupo de id {@code group}. */
    public record More(int parent, int group, int hidden) implements Row {
    }

    /** O estado de uma linha de máquina, na ordem de prioridade (o primeiro que valer). */
    public enum MachineStatus {
        /** O roteador ou o chunk da máquina está descarregado. */
        UNLOADED,
        /** O roteador está sem o Upgrade de Alcance: as remotas não movem. */
        NO_UPGRADE,
        OUT_OF_RANGE,
        OVER_LIMIT,
        /** No lugar há outro bloco com a capability de algum tipo carregado. */
        REPLACED,
        /** No lugar há outro bloco, sem capability, ou o do grupo sem o block entity. */
        MISSING,
        /**
         * Alguma porta da máquina entregou ou recebeu desde a amostra anterior (na primeira medida, sem amostra
         * anterior, moveu há menos de um segundo ou tem uma origem acordada).
         */
        MOVING,
        /** Algum destino da máquina dorme depois de recusas seguidas. */
        FULL,
        /** Carregada e no alcance, sem mover nada. */
        SLEEPING,
        /** A presa de um nó sem nenhuma face numa rede que exista (só na visão por máquinas). */
        NO_NETWORK,
        /** A presa de um nó com todas as redes em grupos pausados (só na visão por máquinas). */
        PAUSED
    }

    /**
     * O estado e a vazão de uma linha de máquina na última amostra.
     *
     * @param rates por tipo, na unidade do registro: em {@code [ordinal]} o que a máquina recebeu e em
     *              {@code [tipos + ordinal]} o que ela deu. Não alterar
     */
    public record MachineLive(MachineStatus status, long[] rates) {
        private static final MachineLive[] STILL = new MachineLive[MachineStatus.values().length];

        static {
            for (MachineStatus status : MachineStatus.values()) {
                STILL[status.ordinal()] = new MachineLive(status, NO_RATES);
            }
        }

        /** O estado sem vazão nenhuma, compartilhado. */
        public static MachineLive still(MachineStatus status) {
            return STILL[status.ordinal()];
        }

        /** O que a máquina recebeu do tipo, por tick ou por segundo como a vazão dos nós. */
        public long received(ResourceType type) {
            return rates[type.ordinal()];
        }

        /** O que a máquina deu do tipo. */
        public long given(ResourceType type) {
            return rates[TYPES.length + type.ordinal()];
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MachineLive live && live.status == status && Arrays.equals(live.rates, rates);
        }

        @Override
        public int hashCode() {
            return status.hashCode() * 31 + Arrays.hashCode(rates);
        }

        @Override
        public String toString() {
            return "MachineLive[" + status + ", " + Arrays.toString(rates) + "]";
        }
    }

    /** Igual a {@code other} fora o estado e a vazão das máquinas. */
    boolean sameExceptLive(TabletRows other) {
        return rows.equals(other.rows);
    }

    /** As mesmas linhas com o estado e a vazão de {@code updated}; elas mesmas se o número de máquinas não bate. */
    TabletRows withLives(List<MachineLive> updated) {
        return updated.size() == lives.size() ? new TabletRows(rows, List.copyOf(updated)) : this;
    }

    // ------------------------------------------------------------------ codec, dentro da página

    /** Os blocos dos grupos na paleta de máquinas da página. */
    static void addBlocks(TabletRows rows, Object2IntLinkedOpenHashMap<ResourceLocation> machines) {
        for (Row row : rows.rows) {
            if (row instanceof Group group) {
                machines.putIfAbsent(group.block, machines.size());
            }
        }
    }

    static void encode(RegistryFriendlyByteBuf buf, TabletRows rows, Object2IntLinkedOpenHashMap<ResourceLocation> machines) {
        buf.writeVarInt(rows.rows.size());
        int previous = -1;
        for (Row row : rows.rows) {
            int header = switch (row) {
                case Attached attached -> KIND_ATTACHED | (attached.side.isPresent() ? FLAG_A : 0);
                case Group group -> KIND_GROUP | (group.collapsed ? FLAG_A : 0) | (group.showAll ? FLAG_B : 0)
                        | (group.name.isEmpty() ? 0 : FLAG_C);
                case Machine machine -> KIND_MACHINE | (machine.exception ? FLAG_A : 0)
                        | (machine.label.isEmpty() ? 0 : FLAG_C);
                case More more -> KIND_MORE;
            };
            boolean same = row.parent() == previous;
            buf.writeByte(header | (same ? SAME_PARENT : 0));
            if (!same) {
                buf.writeVarInt(row.parent());
                previous = row.parent();
            }
            switch (row) {
                case Attached attached -> attached.side.ifPresent(side -> buf.writeByte(side.ordinal()));
                case Group group -> {
                    buf.writeVarInt(group.id);
                    buf.writeVarInt(machines.getInt(group.block));
                    if (!group.name.isEmpty()) {
                        buf.writeUtf(group.name, RouterSnapshot.NAME_LENGTH);
                    }
                    buf.writeVarInt(group.total);
                    buf.writeVarInt(group.exceptions);
                }
                case Machine machine -> {
                    buf.writeVarInt(machine.index);
                    buf.writeLong(machine.pos);
                    if (!machine.label.isEmpty()) {
                        buf.writeUtf(machine.label, RouterSnapshot.NAME_LENGTH);
                    }
                }
                case More more -> {
                    buf.writeVarInt(more.group);
                    buf.writeVarInt(more.hidden);
                }
            }
        }
        writeLives(buf, rows.lives);
    }

    /** O formato de {@link #encode}: cada nó de cima tem de estar entre os {@code nodes} da página. */
    static TabletRows decode(RegistryFriendlyByteBuf buf, List<ResourceLocation> machines, int nodes) {
        int count = readCount(buf, TabletSnapshot.MAX_NODES);
        List<Row> rows = new ArrayList<>(count);
        int parent = -1;
        int machineRows = 0;
        for (int i = 0; i < count; i++) {
            int header = buf.readUnsignedByte();
            if ((header & SAME_PARENT) == 0) {
                parent = buf.readVarInt();
            }
            if (parent < 0 || parent >= nodes) {
                throw new DecoderException("linha sob o nó " + parent + " de " + nodes);
            }
            Row row = switch (header & 3) {
                case KIND_ATTACHED -> new Attached(parent, (header & FLAG_A) == 0 ? Optional.empty()
                        : Optional.of(direction(buf.readUnsignedByte())));
                case KIND_GROUP -> {
                    int id = buf.readVarInt();
                    int block = buf.readVarInt();
                    if (block < 0 || block >= machines.size()) {
                        throw new DecoderException("bloco " + block + " de " + machines.size());
                    }
                    String name = (header & FLAG_C) == 0 ? "" : buf.readUtf(RouterSnapshot.NAME_LENGTH);
                    yield new Group(parent, id, machines.get(block), name, buf.readVarInt(), buf.readVarInt(),
                            (header & FLAG_A) != 0, (header & FLAG_B) != 0);
                }
                case KIND_MACHINE -> {
                    machineRows++;
                    int index = buf.readVarInt();
                    long pos = buf.readLong();
                    String label = (header & FLAG_C) == 0 ? "" : buf.readUtf(RouterSnapshot.NAME_LENGTH);
                    yield new Machine(parent, index, pos, label, (header & FLAG_A) != 0);
                }
                default -> new More(parent, buf.readVarInt(), buf.readVarInt());
            };
            rows.add(row);
        }
        List<MachineLive> lives = readLives(buf);
        if (lives.size() != machineRows) {
            throw new DecoderException("estados " + lives.size() + " para " + machineRows + " máquinas");
        }
        return new TabletRows(List.copyOf(rows), lives);
    }

    private static Direction direction(int ordinal) {
        if (ordinal >= DIRECTIONS.length) {
            throw new DecoderException("lado " + ordinal);
        }
        return DIRECTIONS[ordinal];
    }

    private static int readCount(RegistryFriendlyByteBuf buf, int max) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) {
            throw new DecoderException("count " + count + " > " + max);
        }
        return count;
    }

    // ------------------------------------------------------------------ o estado e a vazão

    /** O estado e a vazão das linhas de máquina, na ordem delas: o pacote pequeno e o fim da página. */
    public static final StreamCodec<RegistryFriendlyByteBuf, List<MachineLive>> LIVE_CODEC =
            StreamCodec.of(TabletRows::writeLives, TabletRows::readLives);

    /** Cada uma: o estado, uma máscara de quais vazões não são zero e só essas. */
    private static void writeLives(RegistryFriendlyByteBuf buf, List<MachineLive> lives) {
        buf.writeVarInt(lives.size());
        for (MachineLive live : lives) {
            buf.writeEnum(live.status);
            long[] rates = live.rates;
            int mask = 0;
            for (int r = 0; r < rates.length; r++) {
                if (rates[r] != 0) {
                    mask |= 1 << r;
                }
            }
            buf.writeVarInt(mask);
            for (int r = 0; r < rates.length; r++) {
                if (rates[r] != 0) {
                    buf.writeVarLong(rates[r]);
                }
            }
        }
    }

    private static List<MachineLive> readLives(RegistryFriendlyByteBuf buf) {
        int count = readCount(buf, TabletSnapshot.MAX_NODES);
        List<MachineLive> lives = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            MachineStatus status = buf.readEnum(MachineStatus.class);
            int mask = buf.readVarInt();
            if (mask == 0) {
                lives.add(MachineLive.still(status));
                continue;
            }
            if ((mask >>> NO_RATES.length) != 0) {
                throw new DecoderException("máscara de vazão " + mask);
            }
            long[] rates = new long[NO_RATES.length];
            for (int r = 0; r < rates.length; r++) {
                if ((mask & (1 << r)) != 0) {
                    rates[r] = buf.readVarLong();
                }
            }
            lives.add(new MachineLive(status, rates));
        }
        return List.copyOf(lives);
    }
}
