package io.github.matheusanbs.wirelessautomate.client.storage;

/** Ordem da lista do Baú e dos Tanques: quantidade, nome ou mod. */
enum ListSort {
    COUNT, NAME, MOD;

    ListSort next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
