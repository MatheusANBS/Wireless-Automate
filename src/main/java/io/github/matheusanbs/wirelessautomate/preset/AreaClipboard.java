package io.github.matheusanbs.wirelessautomate.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Cópia de área do Configurador (componente {@code wirelessautomate:area_clipboard}): os roteadores
 * de uma área, cada um com a posição relativa ao primeiro canto e o preset dele. Presets iguais
 * (o comum numa linha de máquinas) são guardados uma vez só em {@link #presets} e citados pelo índice.
 * Imutável; no máximo {@value #MAX_ROUTERS} roteadores, para o item não ficar grande demais.
 */
public record AreaClipboard(List<RouterPreset> presets, List<Entry> entries) {
    public static final int MAX_ROUTERS = 256;

    /** Um roteador copiado: deslocamento a partir da origem e o índice do preset. */
    public record Entry(BlockPos offset, int preset) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("offset").forGetter(Entry::offset),
                Codec.INT.fieldOf("preset").forGetter(Entry::preset))
                .apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Entry::offset,
                ByteBufCodecs.VAR_INT, Entry::preset,
                Entry::new);
    }

    public AreaClipboard {
        presets = List.copyOf(presets);
        // entradas com índice inválido (dado corrompido) caem
        List<Entry> valid = new ArrayList<>(Math.min(entries.size(), MAX_ROUTERS));
        for (Entry entry : entries) {
            if (valid.size() < MAX_ROUTERS && entry.preset() >= 0 && entry.preset() < presets.size()) {
                valid.add(entry);
            }
        }
        entries = List.copyOf(valid);
    }

    public static final Codec<AreaClipboard> CODEC = RecordCodecBuilder.create(i -> i.group(
            RouterPreset.CODEC.listOf().fieldOf("presets").forGetter(AreaClipboard::presets),
            Entry.CODEC.listOf().fieldOf("routers").forGetter(AreaClipboard::entries))
            .apply(i, AreaClipboard::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AreaClipboard> STREAM_CODEC = StreamCodec.composite(
            RouterPreset.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ROUTERS)), AreaClipboard::presets,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ROUTERS)), AreaClipboard::entries,
            AreaClipboard::new);

    /** Monta a cópia juntando presets iguais. {@code offsets} e {@code routerPresets} andam juntos. */
    public static AreaClipboard of(List<BlockPos> offsets, List<RouterPreset> routerPresets) {
        List<RouterPreset> distinct = new ArrayList<>();
        List<Entry> entries = new ArrayList<>(offsets.size());
        for (int i = 0; i < offsets.size() && i < MAX_ROUTERS; i++) {
            RouterPreset preset = routerPresets.get(i);
            int index = distinct.indexOf(preset);
            if (index < 0) {
                index = distinct.size();
                distinct.add(preset);
            }
            entries.add(new Entry(offsets.get(i).immutable(), index));
        }
        return new AreaClipboard(distinct, entries);
    }

    public int size() {
        return entries.size();
    }

    public RouterPreset preset(Entry entry) {
        return presets.get(entry.preset());
    }
}
