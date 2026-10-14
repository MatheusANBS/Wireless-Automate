package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * O NBT das partes de um {@link RouterBlockEntity} (faces, cartões, upgrades, máquinas e grupos), com a
 * migração da rede antiga por aba para as faces. O nome e o sinal de redstone ficam no roteador. As chaves
 * não mudam: mundos antigos carregam.
 */
final class RouterNbt {
    private static final ResourceType[] TYPES = ResourceType.values();

    private RouterNbt() {
    }

    static void save(CompoundTag tag, HolderLookup.Provider registries, RouterFaces faces, RouterCards cards,
            RouterUpgrades upgrades, RemoteMachines remotes) {
        CompoundTag facesTag = faces.save(registries);
        if (!facesTag.isEmpty()) {
            tag.put("faces", facesTag);
        }
        CompoundTag cardsTag = cards.save(registries);
        if (!cardsTag.isEmpty()) {
            tag.put("cards", cardsTag);
        }
        upgrades.save(tag, registries);
        if (!remotes.machines().isEmpty()) {
            tag.put("machines", remotes.saveMachines(registries));
        }
        if (!remotes.groups().isEmpty()) {
            tag.put("groups", remotes.saveGroups(registries));
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

    /** Lê as partes e põe a rede antiga ({@link #legacyNetworks}) nas faces ainda sem rede. Sem avisar ninguém. */
    static void load(CompoundTag tag, HolderLookup.Provider registries, RouterFaces faces, RouterCards cards,
            RouterUpgrades upgrades, RemoteMachines remotes, UUID[] legacy) {
        faces.load(tag.getCompound("faces"), registries);
        cards.load(tag.getCompound("cards"), registries);
        upgrades.load(tag, registries);
        // Sem as chaves (mundo de antes do alcance), as listas ficam vazias.
        remotes.load(tag.getList("machines", Tag.TAG_COMPOUND), tag.getList("groups", Tag.TAG_COMPOUND), registries);
        for (ResourceType type : TYPES) {
            UUID network = legacy[type.ordinal()];
            if (network != null) {
                faces.migrateLegacyNetwork(remotes, type, network);
            }
        }
    }
}
