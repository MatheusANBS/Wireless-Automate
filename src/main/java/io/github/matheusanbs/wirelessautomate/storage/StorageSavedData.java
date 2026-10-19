package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
 *
 * <p>Para o arquivo não crescer sem limite (ele é regravado inteiro a cada mudança):
 * <ul>
 *   <li>Só entra aqui o conteúdo de um bloco cujo item com a referência foi feito ou vai ser feito
 *       ({@link StorageBlockEntity#stash}): a loot table, o drop do próprio bloco no criativo, a explosão, a
 *       broca de outro mod. Um bloco que sai sem drop nenhum ({@code /setblock}, {@code /fill}) perde o
 *       conteúdo, como um baú vanilla limpo pelo comando.</li>
 *   <li>Cada conteúdo vence depois de {@link Config#STORAGE_STASH_EXPIRY_DAYS} dias de servidor ligado
 *       (game time do overworld; 0 é sem prazo), contados de quando o bloco foi quebrado e renovados uma vez
 *       por dia enquanto o item está no inventário de um jogador ({@link #touch}). Vencido, ele some na
 *       próxima varredura (a cada quebra e na subida do servidor), e o item que ainda exista nasce vazio.
 *       Os guardados sem data (de antes dela) começam a contar na primeira varredura.</li>
 * </ul>
 * O {@code /wa storage recover} vale para o que ainda está aqui: o item que sumiu depois de cair (lava,
 * despawn, vazio) dentro do prazo.
 */
public final class StorageSavedData extends SavedData {
    public static final String DATA_NAME = "wirelessautomate_storage";
    public static final SavedData.Factory<StorageSavedData> FACTORY =
            new SavedData.Factory<>(StorageSavedData::new, StorageSavedData::load);
    /** {@link Stored#storedAt} de um guardado sem data: a próxima varredura põe a hora dela. */
    public static final long UNDATED = -1;

    /**
     * Um conteúdo guardado: de que armazenamento é, o tier do bloco quebrado, o NBT dele e quando foi
     * guardado (game time do overworld, ou {@link #UNDATED}). O tier é {@code null} nos guardados antes de
     * ele ser gravado.
     */
    public record Stored(StorageKind kind, @Nullable RouterTier tier, Tag data, long storedAt) {
        public Stored(StorageKind kind, @Nullable RouterTier tier, Tag data) {
            this(kind, tier, data, UNDATED);
        }
    }

    private final Map<UUID, Stored> contents = new HashMap<>();

    public static StorageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** O relógio do prazo: o game time do overworld, que só anda com o servidor ligado. */
    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /**
     * Guarda o conteúdo de um armazenamento quebrado (substitui o que houver com o mesmo id) e varre os
     * vencidos: quebrar é raro, e a varredura é O(guardados).
     */
    public void put(UUID id, StorageKind kind, RouterTier tier, Tag data, long now) {
        contents.put(id, new Stored(kind, tier, data, now));
        expire(now, Config.STORAGE_STASH_EXPIRY_DAYS.get());
        setDirty();
    }

    /**
     * Tira os vencidos e põe a data nos guardados sem data; devolve quantos saíram. Chamado a cada quebra e
     * na subida do servidor.
     */
    public int expire(long now, int days) {
        int removed = 0;
        Iterator<Map.Entry<UUID, Stored>> it = contents.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Stored> entry = it.next();
            Stored stored = entry.getValue();
            if (stored.storedAt() == UNDATED) {
                entry.setValue(new Stored(stored.kind(), stored.tier(), stored.data(), now));
                setDirty();
            } else if (StorageMath.stashExpired(stored.storedAt(), now, days)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            WirelessAutomate.LOGGER.info("Armazenamentos: {} conteúdo(s) guardado(s) havia mais de {} dias descartado(s)",
                    removed, days);
            setDirty();
        }
        return removed;
    }

    /**
     * O item com a referência ainda existe (está no inventário de um jogador): renova o prazo, no máximo uma
     * vez por dia, para não regravar o arquivo a cada consulta.
     */
    public void touch(UUID id, long now) {
        Stored stored = contents.get(id);
        if (stored != null && stored.storedAt() != UNDATED && now - stored.storedAt() > StorageMath.TICKS_PER_DAY) {
            contents.put(id, new Stored(stored.kind(), stored.tier(), stored.data(), now));
            setDirty();
        }
    }

    /** Subida do servidor: varre os vencidos uma vez. */
    public static void onServerStarted(MinecraftServer server) {
        get(server).expire(now(server), Config.STORAGE_STASH_EXPIRY_DAYS.get());
    }

    /**
     * Tira o conteúdo do id, ou {@code null} se não houver (já colocado, item copiado ou vencido) ou se for
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
            if (stored.storedAt() != UNDATED) {
                entry.putLong("stored_at", stored.storedAt());
            }
            entry.put("data", stored.data());
            all.put(id.toString(), entry);
        });
        tag.put("contents", all);
        return tag;
    }

    /**
     * Lê o formato {@code {kind, tier, stored_at, data}}; sem {@code stored_at}, o guardado fica sem data. Uma
     * lista solta é do formato antigo, só de Baús.
     */
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
                    long storedAt = entry.contains("stored_at") ? entry.getLong("stored_at") : UNDATED;
                    data.contents.put(id, new Stored(kind, tierOf(entry.getString("tier")), entry.get("data"), storedAt));
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
