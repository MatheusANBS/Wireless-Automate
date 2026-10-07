package io.github.matheusanbs.wirelessautomate.network;

/** Tipos de recurso que uma face transporta. Cada um tem modo, filtro e redstone próprios. */
public enum ResourceType {
    ITEM,
    FLUID,
    ENERGY,
    /** Só existe com o Mekanism instalado. */
    CHEMICAL
}
