package io.github.matheusanbs.wirelessautomate.preset;

import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
 * Biblioteca de presets de cada jogador, salva no data storage do overworld
 * ({@code data/wirelessautomate_presets.dat}). Os presets ficam em ordem de criação.
 *
 * <p>Limites, para o arquivo do mundo e os pacotes não crescerem sem fim (filtros podem ter até
 * 4.096 entradas por face): no máximo {@value #MAX_PRESETS} presets por jogador, nomes de até
 * {@value #MAX_NAME} caracteres, {@value #MAX_PRESET_BYTES} bytes de NBT por preset e
 * {@value #MAX_PLAYER_BYTES} bytes somando a biblioteca de um jogador. Um preset típico (algumas
 * faces e filtros curtos) tem menos de 1 KiB.
 */
public final class PresetLibrary extends SavedData {
    public static final String DATA_NAME = "wirelessautomate_presets";
    public static final int MAX_PRESETS = 64;
    public static final int MAX_NAME = 40;
    public static final int MAX_PRESET_BYTES = 128 * 1024;
    public static final int MAX_PLAYER_BYTES = 1024 * 1024;

    /** Um preset salvo. {@code size} é o tamanho do NBT, medido ao salvar. */
    public record Entry(String name, RouterPreset preset, int size) {
    }

    /** Resultado de salvar ou renomear. */
    public enum Result {
        OK,
        FULL,
        TOO_LARGE,
        LIBRARY_FULL,
        BAD_NAME,
        MISSING
    }

    private final Map<UUID, List<Entry>> byPlayer = new HashMap<>();
    /** Muda a cada alteração; as telas abertas comparam. Não é salvo. */
    private int version;

    public static PresetLibrary get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PresetLibrary::new, PresetLibrary::load), DATA_NAME);
    }

    public int version() {
        return version;
    }

    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty);
        if (dirty) {
            version++;
        }
    }

    /** Os presets do jogador, em ordem. Visão só de leitura. */
    public List<Entry> presets(UUID player) {
        return Collections.unmodifiableList(byPlayer.getOrDefault(player, List.of()));
    }

    public @Nullable Entry preset(UUID player, int index) {
        List<Entry> list = byPlayer.get(player);
        return list != null && index >= 0 && index < list.size() ? list.get(index) : null;
    }

    /** Nome limpo (sem espaços nas pontas nem caracteres de controle) ou {@code null} se vazio ou longo demais. */
    public static @Nullable String cleanName(String name) {
        if (name == null) {
            return null;
        }
        String clean = name.replaceAll("\\p{Cntrl}", "").strip();
        return clean.isEmpty() || clean.length() > MAX_NAME ? null : clean;
    }

    /** Acrescenta um preset no fim da lista do jogador. */
    public Result add(UUID player, String name, RouterPreset preset, HolderLookup.Provider registries) {
        String clean = cleanName(name);
        if (clean == null) {
            return Result.BAD_NAME;
        }
        List<Entry> list = byPlayer.computeIfAbsent(player, id -> new ArrayList<>());
        if (list.size() >= MAX_PRESETS) {
            return Result.FULL;
        }
        int size = PresetCodes.size(PresetCodes.presetTag(preset, registries));
        if (size > MAX_PRESET_BYTES) {
            return Result.TOO_LARGE;
        }
        if (totalSize(list) + size > MAX_PLAYER_BYTES) {
            return Result.LIBRARY_FULL;
        }
        list.add(new Entry(clean, preset, size));
        setDirty();
        return Result.OK;
    }

    public Result rename(UUID player, int index, String name) {
        String clean = cleanName(name);
        Entry entry = preset(player, index);
        if (entry == null) {
            return Result.MISSING;
        }
        if (clean == null) {
            return Result.BAD_NAME;
        }
        if (!clean.equals(entry.name())) {
            byPlayer.get(player).set(index, new Entry(clean, entry.preset(), entry.size()));
            setDirty();
        }
        return Result.OK;
    }

    public boolean remove(UUID player, int index) {
        if (preset(player, index) == null) {
            return false;
        }
        List<Entry> list = byPlayer.get(player);
        list.remove(index);
        if (list.isEmpty()) {
            byPlayer.remove(player);
        }
        setDirty();
        return true;
    }

    private static int totalSize(List<Entry> list) {
        int total = 0;
        for (Entry entry : list) {
            total += entry.size();
        }
        return total;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        byPlayer.forEach((player, list) -> {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("player", player);
            ListTag presets = new ListTag();
            for (Entry entry : list) {
                CompoundTag presetTag = new CompoundTag();
                presetTag.putString("name", entry.name());
                presetTag.put("preset", PresetCodes.presetTag(entry.preset(), registries));
                presets.add(presetTag);
            }
            playerTag.put("presets", presets);
            players.add(playerTag);
        });
        tag.put("players", players);
        return tag;
    }

    public static PresetLibrary load(CompoundTag tag, HolderLookup.Provider registries) {
        PresetLibrary library = new PresetLibrary();
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            if (!playerTag.hasUUID("player")) {
                continue;
            }
            List<Entry> list = new ArrayList<>();
            ListTag presets = playerTag.getList("presets", Tag.TAG_COMPOUND);
            for (int j = 0; j < presets.size() && list.size() < MAX_PRESETS; j++) {
                CompoundTag presetTag = presets.getCompound(j);
                Tag raw = presetTag.get("preset");
                String name = cleanName(presetTag.getString("name"));
                if (raw == null) {
                    continue;
                }
                // Lido pelo codec tolerante: entrada de filtro de mod removido cai, o resto fica.
                RouterPreset preset = PresetCodes.readPreset(raw, registries);
                list.add(new Entry(name != null ? name : "Preset " + (j + 1), preset, PresetCodes.size(raw)));
            }
            if (!list.isEmpty()) {
                library.byPlayer.put(playerTag.getUUID("player"), list);
            }
        }
        return library;
    }
}
