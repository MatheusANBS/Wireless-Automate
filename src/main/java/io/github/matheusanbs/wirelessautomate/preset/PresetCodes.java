package io.github.matheusanbs.wirelessautomate.preset;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.preset.PresetCode.InvalidCodeException;
import io.github.matheusanbs.wirelessautomate.preset.PresetCode.Problem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * Preset ↔ NBT ↔ código {@code WA1:}. O NBT é o do {@link RouterPreset#CODEC} com {@code RegistryOps}
 * (o mesmo do componente do item e da biblioteca), dentro de {@code {v:1, name, preset}}.
 *
 * <p>Importar é tolerante: a face lê pelo codec tolerante, então uma entrada de filtro de um mod
 * ausente é descartada; {@link Imported#ignoredEntries()} conta quantas, para o aviso. As redes vêm
 * como estão; quem importa decide quais servem (veja {@link PresetApplier}).
 */
public final class PresetCodes {
    public static final int FORMAT = 1;
    private static final String VERSION = "v";
    private static final String NAME = "name";
    private static final String PRESET = "preset";

    /** Um preset lido de um código: o nome sugerido, o preset e quantas entradas de filtro caíram. */
    public record Imported(String name, RouterPreset preset, int ignoredEntries) {
    }

    /** NBT do preset (sem envelope), com registros. */
    public static Tag presetTag(RouterPreset preset, HolderLookup.Provider registries) {
        return RouterPreset.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), preset)
                .getOrThrow(error -> new IllegalStateException("Falha ao salvar o preset: " + error));
    }

    /** Lê o NBT de {@link #presetTag}; dado ilegível vira preset vazio, com aviso no log. */
    public static RouterPreset readPreset(Tag tag, HolderLookup.Provider registries) {
        return RouterPreset.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .resultOrPartial(error -> WirelessAutomate.LOGGER.warn("Preset com dados inválidos ({})", error))
                .orElse(RouterPreset.EMPTY);
    }

    /** Envelope {@code {v, name, preset}}. */
    public static CompoundTag toTag(String name, RouterPreset preset, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION, FORMAT);
        tag.putString(NAME, name);
        tag.put(PRESET, presetTag(preset, registries));
        return tag;
    }

    public static String export(String name, RouterPreset preset, HolderLookup.Provider registries) {
        return PresetCode.encode(bytes(toTag(name, preset, registries)));
    }

    /** Valida e lê um código. Formato mais novo que o deste mod é recusado como corrompido. */
    public static Imported importCode(String code, HolderLookup.Provider registries) throws InvalidCodeException {
        byte[] raw = PresetCode.decode(code);
        CompoundTag tag;
        try {
            tag = NbtIo.read(new DataInputStream(new ByteArrayInputStream(raw)),
                    NbtAccounter.create(PresetCode.MAX_RAW_BYTES * 4L));
        } catch (IOException | RuntimeException e) {
            throw new InvalidCodeException(Problem.CORRUPT);
        }
        return fromTag(tag, registries);
    }

    public static Imported fromTag(CompoundTag tag, HolderLookup.Provider registries) throws InvalidCodeException {
        if (tag.getInt(VERSION) != FORMAT || !tag.contains(PRESET, Tag.TAG_COMPOUND)) {
            throw new InvalidCodeException(Problem.CORRUPT);
        }
        Tag presetTag = tag.get(PRESET);
        RouterPreset preset = readPreset(presetTag, registries);
        int ignored = Math.max(0, countEntries(presetTag) - countEntries(preset));
        return new Imported(tag.getString(NAME), preset, ignored);
    }

    /** NBT sem compressão, no formato de rede. */
    public static byte[] bytes(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(out));
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Tamanho do NBT de um preset, em bytes; a biblioteca usa para os limites. */
    public static int size(Tag presetTag) {
        CompoundTag holder = new CompoundTag();
        holder.put(PRESET, presetTag);
        return bytes(holder).length;
    }

    /** Entradas de filtro escritas no NBT de um preset: {@code faces → tipo → lado → filter.entries}. */
    static int countEntries(Tag presetTag) {
        if (!(presetTag instanceof CompoundTag preset)) {
            return 0;
        }
        int count = 0;
        CompoundTag faces = preset.getCompound("faces");
        for (String type : faces.getAllKeys()) {
            CompoundTag sides = faces.getCompound(type);
            for (String side : sides.getAllKeys()) {
                CompoundTag filter = sides.getCompound(side).getCompound("filter");
                Tag entries = filter.get("entries");
                if (entries instanceof ListTag list) {
                    count += list.size();
                }
            }
        }
        return count;
    }

    static int countEntries(RouterPreset preset) {
        int count = 0;
        for (ResourceType type : ResourceType.values()) {
            for (RelativeSide side : RelativeSide.values()) {
                count += preset.face(type, side).filter().entries().size();
            }
        }
        return count;
    }

    private PresetCodes() {
    }
}
