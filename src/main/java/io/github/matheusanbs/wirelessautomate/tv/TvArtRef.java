package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Uma pixel art do layout como o update tag da Tela a leva, sem os pixels: o id, o nome, o tamanho e o resumo do
 * conteúdo ({@link TvPixels#hash}). Os pixels vão à parte, no {@code TvArtsPayload}, só a quem está perto; o cliente
 * os guarda pelo resumo e monta a arte de volta com o id e o nome daqui. O layout que vai ao cliente
 * ({@link #clientTag}) é o NBT do layout sem as {@code images}, com a lista destas referências em {@code art_refs}: é
 * ele que o {@code tv.maxLayoutBytes} mede, porque é ele que pesa no pacote do chunk.
 */
public record TvArtRef(UUID id, String name, int width, int height, long hash) {
    public static final String KEY = "art_refs";

    public static final Codec<TvArtRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(TvArtRef::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(TvArtRef::name),
            Codec.INT.fieldOf("w").forGetter(TvArtRef::width),
            Codec.INT.fieldOf("h").forGetter(TvArtRef::height),
            Codec.LONG.fieldOf("hash").forGetter(TvArtRef::hash)).apply(i, TvArtRef::new));

    /** A referência de uma arte; calcula o resumo (o SHA-256 dos pixels), então só quando o layout muda. */
    public static TvArtRef of(TvPixelArt art) {
        return new TvArtRef(art.id(), art.name(), art.width(), art.height(), art.contentHash());
    }

    /** As referências das artes do layout, na ordem dele. */
    public static List<TvArtRef> of(TvLayout layout) {
        List<TvPixelArt> images = layout.images();
        if (images.isEmpty()) {
            return List.of();
        }
        List<TvArtRef> refs = new ArrayList<>(images.size());
        for (TvPixelArt image : images) {
            refs.add(of(image));
        }
        return List.copyOf(refs);
    }

    /**
     * A chave do conteúdo de uma lista de referências: muda quando alguma arte muda de pixels, de paleta ou de
     * tamanho, ou entra ou sai, e não muda com o resto do layout nem com o nome. 0 só sem artes.
     */
    public static long key(List<TvArtRef> refs) {
        if (refs.isEmpty()) {
            return 0;
        }
        long key = 1;
        for (int i = 0; i < refs.size(); i++) {
            key = key * 0x9E3779B97F4A7C15L + refs.get(i).hash;
        }
        return key == 0 ? 1 : key;
    }

    /**
     * Se a arte achada pelo {@link #hash} pode ser usada como está: o mesmo id, nome e tamanho (o conteúdo é o do
     * resumo, não é calculado de novo).
     */
    public boolean matches(TvPixelArt art) {
        return art.id().equals(id) && art.name().equals(name) && art.width() == width && art.height() == height;
    }

    /** O layout como vai ao cliente: sem as artes, com as referências delas em {@link #KEY}. */
    public static CompoundTag clientTag(TvLayout layout, List<TvArtRef> refs) {
        TvLayout bare = layout.images().isEmpty() ? layout : layout.withImages(List.of());
        CompoundTag tag = (CompoundTag) TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, bare).getOrThrow();
        if (!refs.isEmpty()) {
            ListTag list = new ListTag();
            for (TvArtRef ref : refs) {
                list.add(CODEC.encodeStart(NbtOps.INSTANCE, ref).getOrThrow());
            }
            tag.put(KEY, list);
        }
        return tag;
    }

    /**
     * As referências de um layout lido do update tag, ou {@code null} se ele não as tem (o NBT do disco, com as artes
     * inteiras). Uma referência que não decodifica é largada; no máximo {@link TvLayout#MAX_IMAGES}.
     */
    public static @Nullable List<TvArtRef> read(@Nullable Tag layout) {
        if (!(layout instanceof CompoundTag tag) || !(tag.get(KEY) instanceof ListTag list)) {
            return null;
        }
        List<TvArtRef> refs = new ArrayList<>(Math.min(list.size(), TvLayout.MAX_IMAGES));
        for (int i = 0; i < list.size() && refs.size() < TvLayout.MAX_IMAGES; i++) {
            CODEC.parse(NbtOps.INSTANCE, list.get(i)).result().ifPresent(refs::add);
        }
        return List.copyOf(refs);
    }
}
