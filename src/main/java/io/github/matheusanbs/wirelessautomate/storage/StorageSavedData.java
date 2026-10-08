package io.github.matheusanbs.wirelessautomate.storage;

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
 * Conteúdo dos Baús que viraram item, por id ({@link StorageContents}). Enquanto o bloco está
 * colocado, o conteúdo fica no próprio block entity (no chunk); só ao quebrar ele vem para cá, e
 * volta ao block entity quando o item é colocado. Colocar é "tirar daqui": uma cópia do item
 * (criativo, dupe) encontra o conteúdo já tirado e nasce vazia, sem duplicar nada.
 *
 * <p>Guardado como NBT cru (a lista do {@link ItemStorage#save}), sem reler os itens até alguém
 * colocar o bloco. Fica no data storage do overworld.
 */
public final class StorageSavedData extends SavedData {
    public static final String DATA_NAME = "wirelessautomate_storage";
    public static final SavedData.Factory<StorageSavedData> FACTORY =
            new SavedData.Factory<>(StorageSavedData::new, StorageSavedData::load);

    private final Map<UUID, ListTag> contents = new HashMap<>();

    public static StorageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Guarda o conteúdo de um Baú quebrado (substitui o que houver com o mesmo id). */
    public void put(UUID id, ListTag items) {
        contents.put(id, items);
        setDirty();
    }

    /** Tira o conteúdo do id, ou {@code null} se não houver (já colocado, ou item copiado). */
    public @Nullable ListTag take(UUID id) {
        ListTag items = contents.remove(id);
        if (items != null) {
            setDirty();
        }
        return items;
    }

    public boolean contains(UUID id) {
        return contents.containsKey(id);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        contents.forEach((id, items) -> all.put(id.toString(), items));
        tag.put("contents", all);
        return tag;
    }

    private static StorageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StorageSavedData data = new StorageSavedData();
        CompoundTag all = tag.getCompound("contents");
        for (String key : all.getAllKeys()) {
            try {
                data.contents.put(UUID.fromString(key), all.getList(key, Tag.TAG_COMPOUND));
            } catch (IllegalArgumentException ignored) {
                // Chave que não é um id: save adulterado, ignora a entrada.
            }
        }
        return data;
    }
}
