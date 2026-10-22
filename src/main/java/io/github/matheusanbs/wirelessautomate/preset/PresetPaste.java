package io.github.matheusanbs.wirelessautomate.preset;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Copiar e colar a configuração de um roteador, a lógica que o Configurador e o Tablet dividem: a cópia
 * sem redes que não existem mais e a colagem pela regra das redes ({@link PresetApplier}). Quem chama
 * cuida do item que guarda a cópia, das mensagens e de conferir se o jogador pode mexer no roteador.
 */
public final class PresetPaste {
    private PresetPaste() {
    }

    /** Id do bloco da máquina do roteador ({@code minecraft:air} sem máquina). */
    public static ResourceLocation machine(ServerLevel level, RouterBlockEntity router) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(router.machinePos()).getBlock());
    }

    /**
     * A configuração das faces do roteador, para guardar num item. Uma rede removida sai da cópia: o id
     * órfão não serve para nada colado noutro roteador.
     */
    public static RouterPreset copy(ServerPlayer player, RouterBlockEntity router) {
        RouterPreset preset = RouterPreset.copyOf(router);
        NetworkSavedData data = NetworkSavedData.get(player.server);
        for (UUID id : preset.distinctNetworks()) {
            if (data.network(id) == null) {
                preset = preset.withoutNetwork(id);
            }
        }
        return preset;
    }

    /**
     * Cola o preset no roteador: cada face com a sua rede se o jogador pode usá-la (dono ou operador
     * nível 2); a face cuja rede ele não pode usar fica com a rede que já tinha. Com {@code only}, só a
     * aba desse tipo (e só as redes das faces dela entram na regra); {@code null} = todas. Devolve o que
     * a regra decidiu, para a mensagem.
     */
    public static PresetApplier.Checked paste(ServerPlayer player, RouterBlockEntity router, RouterPreset preset,
            @Nullable ResourceType only) {
        PresetApplier.Checked checked = PresetApplier.check(player, preset, only);
        checked.preset().applyTo(router, only, canLeave(player));
        return checked;
    }

    /**
     * Quando o colar pode tirar uma face da rede em que está: só de uma rede que o jogador gerencia (dono ou
     * operador nível 2), como o Tablet no Mover; uma rede que não existe mais não prende ninguém.
     */
    public static Predicate<UUID> canLeave(ServerPlayer player) {
        NetworkSavedData data = NetworkSavedData.get(player.server);
        return from -> {
            WaNetwork current = data.network(from);
            return current == null || current.canManage(player);
        };
    }
}
