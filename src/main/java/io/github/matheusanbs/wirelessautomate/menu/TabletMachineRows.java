package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineLive;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * As linhas da Lista do Tablet na visão por máquinas (etapa 3b): um cabeçalho por bloco, as máquinas dele na ordem
 * roteador e #N (a presa antes das vinculadas do mesmo roteador) e o "mais N" de um bloco cortado. Sem nó pai: a
 * página nessa visão não leva nós. As linhas dividem o teto de {@link TabletSnapshot#MAX_NODES} da página.
 *
 * <p>Os roteadores das linhas vão numa paleta da página ({@link RouterRef}), e cada máquina leva o índice dele; os
 * blocos entram na paleta de máquinas da página. O estado e a vazão das máquinas ({@link MachineLive}) vão à parte,
 * na ordem delas, para o pacote pequeno, como nas linhas da árvore ({@link TabletRows}).
 *
 * @param active    a Lista está na visão por máquinas ({@link #EMPTY} na visão por roteadores)
 * @param rows      as linhas, na ordem da tela
 * @param routers   a paleta dos roteadores das linhas de máquina
 * @param lives     o estado e a vazão de cada {@link Machine}, na ordem delas
 * @param totalRows quantas linhas a visão teria sem o teto, com os blocos abertos e recolhidos de agora
 * @param machines  quantas máquinas passam na consulta
 * @param blocks    em quantos blocos
 */
public record TabletMachineRows(boolean active, List<Row> rows, List<RouterRef> routers, List<MachineLive> lives,
        int totalRows, int machines, int blocks) {
    public static final TabletMachineRows EMPTY = new TabletMachineRows(false, List.of(), List.of(), List.of(), 0, 0, 0);

    private static final int KIND_BLOCK = 0;
    private static final int KIND_MACHINE = 1;
    private static final int KIND_MORE = 2;
    private static final int FLAG_A = 1 << 2;
    private static final int FLAG_B = 1 << 3;
    private static final int FLAG_C = 1 << 4;

    /** Uma linha da visão por máquinas. */
    public sealed interface Row permits Block, Machine, More {
    }

    /**
     * O cabeçalho de um bloco.
     *
     * @param machines as máquinas do bloco que passam na consulta
     * @param routers  de quantos roteadores elas são
     * @param hidden   máquinas do bloco acima do teto do índice ({@code IndexRemotes.CAP}), fora das linhas
     */
    public record Block(ResourceLocation block, int machines, int routers, boolean collapsed, boolean showAll,
            int hidden) implements Row {
    }

    /**
     * Uma máquina.
     *
     * @param router   a posição do roteador em {@link #routers()}
     * @param attached a máquina presa do roteador ({@code index} −1)
     * @param index    o índice da máquina vinculada no roteador (o do {@code EditTarget.machine}); −1 na presa
     * @param number   o "#N" da vinculada: a posição dela no grupo do roteador, a partir de 1, como na árvore e na tela
     *                 do roteador; 0 na presa
     * @param pos      a posição ({@code BlockPos.asLong}); a do roteador numa presa de roteador descarregado
     * @param label    rótulo dado pelo jogador; vazio = a tela usa "#N" (ou o nome do roteador na presa)
     */
    public record Machine(int router, boolean attached, int index, int number, long pos, String label,
            boolean exception)
            implements Row {
    }

    /** "… mais {@code hidden}" no fim do bloco {@code block}. */
    public record More(ResourceLocation block, int hidden) implements Row {
    }

    /** Um roteador das linhas: a chave e o nome dado pelo jogador (vazio = a tela usa o nome padrão). */
    public record RouterRef(NodeKey key, String name) {
    }

    /** Igual a {@code other} fora o estado e a vazão das máquinas. */
    boolean sameExceptLive(TabletMachineRows other) {
        return active == other.active && totalRows == other.totalRows && machines == other.machines
                && blocks == other.blocks && rows.equals(other.rows) && routers.equals(other.routers);
    }

    /** As mesmas linhas com o estado e a vazão de {@code updated}; elas mesmas se o número de máquinas não bate. */
    TabletMachineRows withLives(List<MachineLive> updated) {
        return updated.size() == lives.size()
                ? new TabletMachineRows(active, rows, routers, List.copyOf(updated), totalRows, machines, blocks) : this;
    }

    // ------------------------------------------------------------------ codec, dentro da página

    /** Os blocos das linhas na paleta de máquinas da página. */
    static void addBlocks(TabletMachineRows rows, Object2IntLinkedOpenHashMap<ResourceLocation> palette) {
        for (Row row : rows.rows) {
            switch (row) {
                case Block block -> palette.putIfAbsent(block.block, palette.size());
                case More more -> palette.putIfAbsent(more.block, palette.size());
                case Machine machine -> {
                }
            }
        }
    }

    /**
     * Cada linha começa num byte (o tipo nos dois bits de baixo e as marcas nos de cima); uma máquina vinculada sem
     * rótulo leva 12 a 14 bytes, e a presa, 10 a 11.
     */
    static void encode(RegistryFriendlyByteBuf buf, TabletMachineRows rows,
            Object2IntLinkedOpenHashMap<ResourceLocation> palette) {
        buf.writeBoolean(rows.active);
        if (!rows.active) {
            return;
        }
        buf.writeVarInt(rows.routers.size());
        for (RouterRef router : rows.routers) {
            NodeKey.STREAM_CODEC.encode(buf, router.key);
            buf.writeUtf(router.name, RouterSnapshot.NAME_LENGTH);
        }
        buf.writeVarInt(rows.rows.size());
        for (Row row : rows.rows) {
            switch (row) {
                case Block block -> {
                    buf.writeByte(KIND_BLOCK | (block.collapsed ? FLAG_A : 0) | (block.showAll ? FLAG_B : 0));
                    buf.writeVarInt(palette.getInt(block.block));
                    buf.writeVarInt(block.machines);
                    buf.writeVarInt(block.routers);
                    buf.writeVarInt(block.hidden);
                }
                case Machine machine -> {
                    buf.writeByte(KIND_MACHINE | (machine.attached ? FLAG_A : 0) | (machine.exception ? FLAG_B : 0)
                            | (machine.label.isEmpty() ? 0 : FLAG_C));
                    buf.writeVarInt(machine.router);
                    if (!machine.attached) {
                        buf.writeVarInt(machine.index);
                        buf.writeVarInt(machine.number);
                    }
                    buf.writeLong(machine.pos);
                    if (!machine.label.isEmpty()) {
                        buf.writeUtf(machine.label, RouterSnapshot.NAME_LENGTH);
                    }
                }
                case More more -> {
                    buf.writeByte(KIND_MORE);
                    buf.writeVarInt(palette.getInt(more.block));
                    buf.writeVarInt(more.hidden);
                }
            }
        }
        TabletRows.LIVE_CODEC.encode(buf, rows.lives);
        buf.writeVarInt(rows.totalRows);
        buf.writeVarInt(rows.machines);
        buf.writeVarInt(rows.blocks);
    }

    /** O formato de {@link #encode}, com os tetos e os índices conferidos. */
    static TabletMachineRows decode(RegistryFriendlyByteBuf buf, List<ResourceLocation> palette) {
        if (!buf.readBoolean()) {
            return EMPTY;
        }
        int routerCount = TabletPageCodec.readCount(buf, TabletSnapshot.MAX_NODES);
        List<RouterRef> routers = new ArrayList<>(routerCount);
        for (int i = 0; i < routerCount; i++) {
            routers.add(new RouterRef(NodeKey.STREAM_CODEC.decode(buf), buf.readUtf(RouterSnapshot.NAME_LENGTH)));
        }
        int count = TabletPageCodec.readCount(buf, TabletSnapshot.MAX_NODES);
        List<Row> rows = new ArrayList<>(count);
        int machineRows = 0;
        for (int i = 0; i < count; i++) {
            int header = buf.readUnsignedByte();
            Row row = switch (header & 3) {
                case KIND_BLOCK -> new Block(block(palette, buf.readVarInt()), buf.readVarInt(), buf.readVarInt(),
                        (header & FLAG_A) != 0, (header & FLAG_B) != 0, buf.readVarInt());
                case KIND_MACHINE -> {
                    machineRows++;
                    int router = buf.readVarInt();
                    if (router < 0 || router >= routerCount) {
                        throw new DecoderException("roteador " + router + " de " + routerCount);
                    }
                    boolean attached = (header & FLAG_A) != 0;
                    int index = attached ? -1 : buf.readVarInt();
                    int number = attached ? 0 : buf.readVarInt();
                    long pos = buf.readLong();
                    String label = (header & FLAG_C) == 0 ? "" : buf.readUtf(RouterSnapshot.NAME_LENGTH);
                    yield new Machine(router, attached, index, number, pos, label, (header & FLAG_B) != 0);
                }
                case KIND_MORE -> new More(block(palette, buf.readVarInt()), buf.readVarInt());
                default -> throw new DecoderException("linha de máquina " + header);
            };
            rows.add(row);
        }
        List<MachineLive> lives = TabletRows.LIVE_CODEC.decode(buf);
        if (lives.size() != machineRows) {
            throw new DecoderException("estados " + lives.size() + " para " + machineRows + " máquinas");
        }
        return new TabletMachineRows(true, List.copyOf(rows), List.copyOf(routers), lives, buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt());
    }

    private static ResourceLocation block(List<ResourceLocation> palette, int index) {
        if (index < 0 || index >= palette.size()) {
            throw new DecoderException("bloco " + index + " de " + palette.size());
        }
        return palette.get(index);
    }
}
