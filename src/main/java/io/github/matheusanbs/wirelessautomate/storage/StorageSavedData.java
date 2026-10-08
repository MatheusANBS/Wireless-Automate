package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Conteúdo dos armazenamentos do mod que viraram item, por id ({@link StorageContents}). Enquanto o bloco está
 * colocado, o conteúdo fica no próprio block entity (no chunk); só ao quebrar ele vem para cá, e
 * volta ao block entity quando o item é colocado. Colocar é "tirar daqui": uma cópia do item
 * (criativo, dupe) encontra o conteúdo já tirado e nasce vazia, sem duplicar nada.
 *
 * <p>Guardado como NBT cru (a lista do {@link KeyedStorage#save}, ou a energia), sem reler nada até alguém
 * colocar o bloco. Fica no data storage do overworld.
 */
public final class StorageSavedData extends SavedData {
    public static final String DATA_NAME = "wirelessautomate_storage";
    public static final SavedData.Factory<StorageSavedData> FACTORY =
            new SavedData.Factory<>(StorageSavedData::new, StorageSavedData::load);

    /**
     * Um conteúdo guardado: de que armazenamento é, o tier do bloco quebrado e o NBT dele. O tier é
     * {@code null} nos guardados antes de ele ser gravado.
     */
    public record Stored(StorageKind kind, @Nullable RouterTier tier, Tag data) {
    }

    private final Map<UUID, Stored> contents = new HashMap<>();

    public static StorageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Guarda o conteúdo de um armazenamento quebrado (substitui o que houver com o mesmo id). */
    public void put(UUID id, StorageKind kind, RouterTier tier, Tag data) {
        contents.put(id, new Stored(kind, tier, data));
        setDirty();
    }

    /**
     * Tira o conteúdo do id, ou {@code null} se não houver (já colocado, ou item copiado) ou se for
     * de outro tipo de armazenamento (o id de um Tanque não serve num Baú).
     */
    public @Nullable Tag take(UUID id, StorageKind kind) {
        Stored stored = contents.get(id);
        if (stored == null || stored.kind() != kind) {
            return null;
        }
        contents.remove(id);
        setDirty();
        return stored.data();
    }

    public boolean contains(UUID id) {
        return contents.containsKey(id);
    }

    /** Tudo o que está guardado, só para ler ({@code /wa storage}). */
    public Map<UUID, Stored> contents() {
        return Collections.unmodifiableMap(contents);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        contents.forEach((id, stored) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("kind", stored.kind().getSerializedName());
            if (stored.tier() != null) {
                entry.putString("tier", stored.tier().getSerializedName());
            }
            entry.put("data", stored.data());
            all.put(id.toString(), entry);
        });
        tag.put("contents", all);
        return tag;
    }

    /** Lê o formato {@code {kind, data}}; uma lista solta é do formato antigo, só de Baús. */
    private static StorageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StorageSavedData data = new StorageSavedData();
        CompoundTag all = tag.getCompound("contents");
        for (String key : all.getAllKeys()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Tag value = all.get(key);
            if (value instanceof CompoundTag entry && entry.contains("data")) {
                StorageKind kind = kindOf(entry.getString("kind"));
                if (kind != null) {
                    data.contents.put(id, new Stored(kind, tierOf(entry.getString("tier")), entry.get("data")));
                }
            } else if (value instanceof ListTag list) {
                data.contents.put(id, new Stored(StorageKind.CHEST, null, list));
            }
        }
        return data;
    }

    private static @Nullable RouterTier tierOf(String name) {
        for (RouterTier tier : RouterTier.values()) {
            if (tier.getSerializedName().equals(name)) {
                return tier;
            }
        }
        return null;
    }

    private static @Nullable StorageKind kindOf(String name) {
        for (StorageKind kind : StorageKind.values()) {
            if (kind.getSerializedName().equals(name)) {
                return kind;
            }
        }
        return null;
    }
}
