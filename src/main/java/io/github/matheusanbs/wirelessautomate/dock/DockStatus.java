package io.github.matheusanbs.wirelessautomate.dock;

/**
 * Estado de uma linha (lado) da Doca na tela. Os fatos entram todos e vale o mais grave, na ordem do
 * enum; {@link State#FREE} é o lado vazio, sem provedor e sem máquinas. As notas de máquina
 * descarregada ou fora do alcance ficam de fora: são avisos, não estado.
 *
 * <p>Lógica pura, sem classes do Minecraft, para ser testada com JUnit.
 */
public final class DockStatus {
    /** Da mais grave à menos grave; {@code FREE} fica por último por ser o lado sem uso. */
    public enum State {
        NO_PROVIDER, NO_MACHINES, CRAFTING_PATTERN, MIXED, PROVIDER_FULL, ALL_FULL, WORKING, READY, FREE
    }

    /** Nenhuma recusa recente. */
    public static final int NO_REFUSAL = 0;
    /** O provedor ofereceu um padrão de criação (bancada), que a Doca não entrega. */
    public static final int CRAFTING_PATTERN = 1;
    /** O pedido misturava tipos que a máquina não aceita juntos. */
    public static final int MIXED = 2;
    // Os motivos seguintes também são gravados no lado pela entrega (DockDelivery), mas não decidem o estado por
    // aqui: cada um é um fato que a tela tira do próprio lado (as máquinas ativas, o pendente, as máquinas dormindo).
    /** O lado não tinha máquina ativa (no alcance e no limite). */
    public static final int NO_MACHINES = 3;
    /** O lado tinha o resto de um pedido esperando lugar. */
    public static final int PENDING = 4;
    /** Nenhuma candidata aceitou o pedido inteiro. */
    public static final int ALL_FULL = 5;
    /** O orçamento do motor deste tick já tinha acabado. */
    public static final int BUDGET = 6;

    private DockStatus() {
    }

    public static State of(boolean provider, int machines, int lastRefusal, boolean returnBlocked,
            boolean allFull, int working) {
        if (!provider && machines == 0) {
            return State.FREE;
        }
        if (!provider) {
            return State.NO_PROVIDER;
        }
        if (machines == 0) {
            return State.NO_MACHINES;
        }
        if (lastRefusal == CRAFTING_PATTERN) {
            return State.CRAFTING_PATTERN;
        }
        if (lastRefusal == MIXED) {
            return State.MIXED;
        }
        if (returnBlocked) {
            return State.PROVIDER_FULL;
        }
        if (allFull) {
            return State.ALL_FULL;
        }
        return working > 0 ? State.WORKING : State.READY;
    }
}
