package io.github.matheusanbs.wirelessautomate.network;

import java.util.UUID;

/**
 * Uma rede: tudo que estiver nela troca recursos entre si. Guardada no {@link NetworkSavedData}.
 *
 * @param color cor RGB (0xRRGGBB), usada nas telas e no LED de rede
 */
public record WaNetwork(UUID id, String name, int color, UUID owner) {
    // TODO(contrato): serialização em NBT (save/load) para o NetworkSavedData.
}
