package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.BitSet;
import net.minecraft.core.Direction;

/**
 * O resumo das faces de um roteador para o {@link NodeIndex}: os papéis ({@link NodeIndex#role}) e os modos por
 * tipo e lado ({@link NodeIndex.Entry#faceModes}), numa passada só pela presa, pelos perfis e pelas exceções.
 * Um por índice, reaproveitado a cada {@link #scan}: não aloca nada (o {@link BitSet} só cresce).
 *
 * <p>Os modos: para cada tipo na ordem do {@link ResourceType} e cada lado ({@link RemoteSide}, relativo à frente
 * de cada máquina, como o painel mostra) na ordem do enum, 2 bits com o ordinal do {@link PortMode}, a partir do
 * menos significativo (o lado {@code s} do tipo {@code t} começa no bit {@code (t * 6 + s) * 2}). Num lado vale o
 * modo mais forte entre as máquinas ({@link #rank}). Sem a frente da presa (chunk dela descarregado), vale a de
 * Norte, como no painel.
 *
 * <p>As remotas só contam com o Upgrade de Alcance valendo ({@link RouterBlockEntity#rangeActive()}), como no motor;
 * o perfil de um grupo só conta se alguma máquina dele segue o perfil (sem exceção). Pôr ou tirar o upgrade avisa o
 * índice; o {@code machineRadius} mudado na config com o jogo rodando, não: o resumo fica velho até o próximo aviso
 * do roteador.
 */
final class FaceSummary {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final RemoteSide[] SIDES = RemoteSide.values();
    private static final PortMode[] MODES = PortMode.values();

    /** Os 2 bits por lado cabem num {@code long}: um tipo ou um modo a mais falha aqui, não em silêncio. */
    static {
        if (MODES.length > 4 || TYPES.length * SIDES.length * 2 > Long.SIZE) {
            throw new IllegalStateException("Os modos das faces não cabem num long: " + TYPES.length + " tipos × "
                    + SIDES.length + " lados × " + MODES.length + " modos");
        }
    }

    int roles;
    long modes;
    /** Os ids dos grupos com alguma máquina sem exceção. */
    private final BitSet followed = new BitSet();

    void scan(RouterBlockEntity router) {
        roles = 0;
        modes = 0;
        int front = router.attachedFront();
        for (ResourceType type : TYPES) {
            for (RemoteSide side : SIDES) {
                add(type, side, router.face(type, DIRECTIONS[side.toAbsolute(front)]).mode());
            }
        }
        if (!router.rangeActive()) {
            return;
        }
        RemoteMachines remotes = router.remoteMachines();
        followed.clear();
        int machines = remotes.size();
        for (int i = 0; i < machines; i++) {
            RemoteMachine machine = remotes.machine(i);
            MachineConfig exception = machine.exception();
            if (exception != null) {
                add(exception);
            } else if (machine.group() >= 0) {
                followed.set(machine.group());
            }
        }
        int groups = remotes.groupCount();
        for (int g = 0; g < groups; g++) {
            MachineGroup group = remotes.groupAt(g);
            if (group.id() >= 0 && followed.get(group.id())) {
                add(group.profile());
            }
        }
    }

    private void add(MachineConfig config) {
        for (ResourceType type : TYPES) {
            for (RemoteSide side : SIDES) {
                add(type, side, config.face(type, side).mode());
            }
        }
    }

    private void add(ResourceType type, RemoteSide side, PortMode mode) {
        if (mode == PortMode.NONE) {
            return;
        }
        roles |= NodeIndex.roleOf(type, mode);
        int shift = shift(type, side);
        if (rank(mode) > rank(mode(modes, shift))) {
            modes = (modes & ~(3L << shift)) | ((long) mode.ordinal() << shift);
        }
    }

    static int shift(ResourceType type, RemoteSide side) {
        return (type.ordinal() * SIDES.length + side.ordinal()) * 2;
    }

    static PortMode mode(long modes, int shift) {
        return MODES[(int) (modes >>> shift) & 3];
    }

    /**
     * Extrai &gt; Insere &gt; Armazém &gt; Nenhum. Os 2 bits são um ordinal e não bandeiras: o OU de Extrai (1) e
     * Insere (2) daria Armazém (3), um modo que nenhuma máquina tem.
     */
    private static int rank(PortMode mode) {
        return switch (mode) {
            case EXTRACT -> 3;
            case INSERT -> 2;
            case BOTH -> 1;
            case NONE -> 0;
        };
    }
}
