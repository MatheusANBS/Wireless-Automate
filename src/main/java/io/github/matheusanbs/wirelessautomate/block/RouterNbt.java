package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * O NBT das partes de um {@link RouterBlockEntity} (upgrades, máquinas, grupos, o membro presa e o rascunho da
 * presa de um mundo antigo), com a migração da rede antiga por aba. O nome e o sinal de redstone ficam no
 * roteador. As chaves antigas continuam lidas: {@code faces} e {@code cards} (a presa por lado do roteador) viram
 * o rascunho, gravado de volta nelas até a conversão.
 */
final class RouterNbt {
    private static final ResourceType[] TYPES = ResourceType.values();

    private RouterNbt() {
    }

    static void save(CompoundTag tag, HolderLookup.Provider registries, RouterAttached attached,
            RouterUpgrades upgrades, RemoteMachines remotes) {
        attached.save(tag, registries);
        upgrades.save(tag, registries);
        if (!remotes.machines().isEmpty()) {
            tag.put("machines", remotes.saveMachines(registries));
        }
        if (!remotes.groups().isEmpty()) {
            tag.put("groups", remotes.saveGroups(registries));
        }
        CompoundTag member = remotes.saveAttached(registries);
        if (member != null) {
            tag.put("attached", member);
        }
    }

    /**
     * Formato antigo (rede da aba em "networks", ou ainda mais antigo, uma rede só em "network"): só lido,
     * por {@link ResourceType#ordinal()}, para migrar para as faces depois de carregá-las ({@link #load}).
     */
    static UUID[] legacyNetworks(CompoundTag tag) {
        UUID single = tag.hasUUID("network") ? tag.getUUID("network") : null;
        CompoundTag networksTag = tag.getCompound("networks");
        UUID[] legacy = new UUID[TYPES.length];
        for (ResourceType type : TYPES) {
            String key = type.key();
            legacy[type.ordinal()] = networksTag.hasUUID(key) ? networksTag.getUUID(key) : single;
        }
        return legacy;
    }

    /**
     * Lê as partes e põe a rede antiga ({@link #legacyNetworks}) nas faces ainda sem rede: as do rascunho da presa,
     * dos perfis e das exceções. Sem avisar ninguém.
     */
    static void load(CompoundTag tag, HolderLookup.Provider registries, RouterAttached attached,
            RouterUpgrades upgrades, RemoteMachines remotes, UUID[] legacy) {
        upgrades.load(tag, registries);
        // Sem as chaves (mundo de antes do alcance ou da presa no grupo), as listas e o membro ficam vazios.
        remotes.load(tag.getList("machines", Tag.TAG_COMPOUND), tag.getList("groups", Tag.TAG_COMPOUND),
                tag.contains("attached", Tag.TAG_COMPOUND) ? tag.getCompound("attached") : null, registries);
        attached.load(tag, registries, legacy);
        for (ResourceType type : TYPES) {
            UUID network = legacy[type.ordinal()];
            if (network != null) {
                RouterFaces.migrateLegacyNetwork(remotes, type, network);
            }
        }
    }
}
