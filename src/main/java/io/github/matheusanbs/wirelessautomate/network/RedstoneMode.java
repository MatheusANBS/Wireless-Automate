package io.github.matheusanbs.wirelessautomate.network;

/** Controle por redstone de uma face, por tipo de recurso. */
public enum RedstoneMode {
    /** Funciona com ou sem sinal. */
    IGNORE,
    /** Ativo com sinal. */
    HIGH,
    /** Ativo sem sinal. */
    LOW;

    public boolean allows(boolean powered) {
        return switch (this) {
            case IGNORE -> true;
            case HIGH -> powered;
            case LOW -> !powered;
        };
    }
}
