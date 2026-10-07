package io.github.matheusanbs.wirelessautomate.preset;

import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * A regra das redes ao colar ou aplicar um preset: cada aba só leva a rede se ela existe e o
 * jogador pode usá-la (dono ou operador nível 2, {@link ModPayloads#canUse}); senão a aba fica com
 * a rede que o roteador já tinha. A mesma regra vale para o pincel, a área e os códigos importados
 * (a rede de outro mundo não existe aqui e cai).
 */
public final class PresetApplier {
    /**
     * O preset que pode ser aplicado e o que ficou de fora.
     *
     * @param applied   redes que vão junto, sem repetir
     * @param keptTypes abas que ficam com a rede de antes
     * @param kept      redes recusadas, sem repetir
     * @param denied    uma rede recusada que existe (de outro dono), para a mensagem; {@code null} se todas sumiram
     */
    public record Checked(RouterPreset preset, List<UUID> applied, List<ResourceType> keptTypes, List<UUID> kept,
            @Nullable WaNetwork denied) {
        public boolean droppedAny() {
            return !keptTypes.isEmpty();
        }
    }

    public static Checked check(ServerPlayer player, RouterPreset preset) {
        return check(player, preset, null);
    }

    /**
     * Como {@link #check(ServerPlayer, RouterPreset)}, mas olhando só a rede da aba {@code only}
     * (as outras redes saem do preset e não entram nos avisos); {@code null} = todas as abas.
     */
    public static Checked check(ServerPlayer player, RouterPreset preset, @Nullable ResourceType only) {
        preset = preset.onlyNetworkOf(only);
        NetworkSavedData data = NetworkSavedData.get(player.server);
        List<UUID> applied = new ArrayList<>();
        List<ResourceType> keptTypes = new ArrayList<>();
        List<UUID> kept = new ArrayList<>();
        WaNetwork denied = null;
        for (ResourceType type : ResourceType.values()) {
            UUID id = preset.network(type);
            if (id == null) {
                continue;
            }
            WaNetwork network = data.network(id);
            if (network != null && ModPayloads.canUse(player, network)) {
                if (!applied.contains(id)) {
                    applied.add(id);
                }
                continue;
            }
            preset = preset.withoutNetwork(type);
            keptTypes.add(type);
            if (!kept.contains(id)) {
                kept.add(id);
            }
            if (network != null) {
                denied = network;
            }
        }
        return new Checked(preset, List.copyOf(applied), List.copyOf(keptTypes), List.copyOf(kept), denied);
    }

    /** O preset só com as redes que o jogador pode usar (as outras saem). */
    public static RouterPreset usable(ServerPlayer player, RouterPreset preset) {
        return check(player, preset).preset();
    }

    private PresetApplier() {
    }
}
