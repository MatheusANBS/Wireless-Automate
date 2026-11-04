package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A cópia do perfil de uma máquina de cada jogador (o Copiar da tela do roteador): a configuração copiada, o bloco
 * e o nome de origem ("Fornalha #2", "presa", "Perfil: Fornalha"). Vale em qualquer roteador que o jogador possa
 * editar. Só na memória do servidor, nunca no item nem no NBT: some quando o jogador esquece a cópia, sai do
 * servidor ou o servidor para.
 */
public final class MachineClipboard {
    /**
     * Uma cópia. A configuração é só leitura: quem cola trabalha numa cópia dela. Uma cópia nova é um objeto novo,
     * e o menu do roteador compara a identidade para saber quando mandar o nome de novo.
     */
    public record Entry(MachineConfig config, ResourceLocation block, Component source) {
    }

    private static final Map<UUID, Entry> COPIES = new HashMap<>();

    private MachineClipboard() {
    }

    /** Guarda a cópia do jogador no lugar da anterior. */
    public static void put(UUID player, MachineConfig config, ResourceLocation block, Component source) {
        COPIES.put(player, new Entry(config, block, source));
    }

    /** A cópia do jogador, ou {@code null}. */
    public static @Nullable Entry get(UUID player) {
        return COPIES.get(player);
    }

    /** O nome de origem da cópia do jogador; vazio sem cópia. */
    public static Component source(UUID player) {
        Entry entry = COPIES.get(player);
        return entry == null ? Component.empty() : entry.source();
    }

    /** Esquece a cópia do jogador (o ✕ da tela, ou ele saiu do servidor). */
    public static void forget(UUID player) {
        COPIES.remove(player);
    }

    /** Esquece todas (o servidor parou). */
    public static void reset() {
        COPIES.clear();
    }
}
