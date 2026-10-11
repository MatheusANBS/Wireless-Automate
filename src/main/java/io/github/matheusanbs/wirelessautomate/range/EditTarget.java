package io.github.matheusanbs.wirelessautomate.range;

/**
 * O que a tela do roteador está editando: a máquina presa ({@link Kind#ATTACHED}), o perfil de um
 * grupo ({@link Kind#GROUP}, {@code id} = {@link MachineGroup#id()}) ou uma máquina remota
 * ({@link Kind#MACHINE}, {@code id} = índice na lista de máquinas do roteador).
 */
public record EditTarget(Kind kind, int id) {
    public enum Kind { ATTACHED, GROUP, MACHINE }

    public static final EditTarget ATTACHED_TARGET = new EditTarget(Kind.ATTACHED, 0);

    public static EditTarget group(int id) {
        return new EditTarget(Kind.GROUP, id);
    }

    public static EditTarget machine(int index) {
        return new EditTarget(Kind.MACHINE, index);
    }

    public boolean attached() {
        return kind == Kind.ATTACHED;
    }
}
