package io.github.matheusanbs.wirelessautomate.network;

/** Modo de uma face da máquina para um tipo de recurso. Sprites em textures/gui/port_*.png. */
public enum PortMode {
    NONE,
    EXTRACT,
    INSERT,
    BOTH;

    public boolean extracts() {
        return this == EXTRACT || this == BOTH;
    }

    public boolean inserts() {
        return this == INSERT || this == BOTH;
    }
}
