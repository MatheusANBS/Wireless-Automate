package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvCodecs;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: uma ação na biblioteca do jogador, só com o menu da Tela de Sinal aberto. {@code OPEN} pede a
 * biblioteca; {@code SAVE_COMPONENT}, {@code SAVE_THEME} e {@code SAVE_ART} levam o conteúdo (NBT em bytes, até
 * {@link #MAX_BYTES}; a arte cria ou troca a de mesmo id); {@code RENAME} e {@code REMOVE} levam o tipo e o nome, e o
 * primeiro o nome novo; {@code RENAME_ART} e {@code REMOVE_ART} levam o id da arte no {@code name} e o primeiro o
 * nome novo; {@code RENAME_PICTURE} e {@code REMOVE_PICTURE}, o resumo da imagem enviada no {@code name} (64 letras) e
 * o primeiro o nome novo. O servidor valida tudo.
 */
public record TvLibraryActionPayload(int containerId, Action action, Kind kind, String name, String newName,
        byte[] content) implements CustomPacketPayload {
    public static final int MAX_BYTES = 64 * 1024;
    public static final int MAX_NAME = 64;
    public static final Type<TvLibraryActionPayload> TYPE = new Type<>(WirelessAutomate.id("tv_library_action"));

    public enum Action {
        OPEN, SAVE_COMPONENT, SAVE_THEME, RENAME, REMOVE, SAVE_ART, RENAME_ART, REMOVE_ART, RENAME_PICTURE,
        REMOVE_PICTURE
    }

    /** O que um {@code RENAME} ou {@code REMOVE} atinge. */
    public enum Kind { COMPONENT, THEME }

    private static final byte[] NONE = new byte[0];
    private static final StreamCodec<ByteBuf, Action> ACTION = ByteBufCodecs.idMapper(i -> Action.values()[i],
            Action::ordinal);
    private static final StreamCodec<ByteBuf, Kind> KIND = ByteBufCodecs.idMapper(i -> Kind.values()[i],
            Kind::ordinal);
    private static final StreamCodec<ByteBuf, byte[]> BYTES = ByteBufCodecs.byteArray(MAX_BYTES);

    public static final StreamCodec<RegistryFriendlyByteBuf, TvLibraryActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.containerId);
                ACTION.encode(buf, p.action);
                KIND.encode(buf, p.kind);
                buf.writeUtf(p.name, MAX_NAME);
                buf.writeUtf(p.newName, MAX_NAME);
                BYTES.encode(buf, p.content);
            },
            buf -> new TvLibraryActionPayload(ByteBufCodecs.VAR_INT.decode(buf), ACTION.decode(buf), KIND.decode(buf),
                    buf.readUtf(MAX_NAME), buf.readUtf(MAX_NAME), BYTES.decode(buf)));

    public static TvLibraryActionPayload open(int containerId) {
        return new TvLibraryActionPayload(containerId, Action.OPEN, Kind.COMPONENT, "", "", NONE);
    }

    public static TvLibraryActionPayload saveComponent(int containerId, TvComponent component) {
        return new TvLibraryActionPayload(containerId, Action.SAVE_COMPONENT, Kind.COMPONENT, "", "",
                TvNbtBytes.encode(TvComponent.CODEC, component));
    }

    public static TvLibraryActionPayload saveTheme(int containerId, TvTheme theme) {
        return new TvLibraryActionPayload(containerId, Action.SAVE_THEME, Kind.THEME, "", "",
                TvNbtBytes.encode(TvCodecs.THEME, theme));
    }

    public static TvLibraryActionPayload saveArt(int containerId, TvPixelArt art) {
        return new TvLibraryActionPayload(containerId, Action.SAVE_ART, Kind.COMPONENT, "", "",
                TvNbtBytes.encode(TvPixelArt.CODEC, art));
    }

    public static TvLibraryActionPayload renameArt(int containerId, UUID id, String newName) {
        return new TvLibraryActionPayload(containerId, Action.RENAME_ART, Kind.COMPONENT, id.toString(),
                newName.length() > MAX_NAME ? newName.substring(0, MAX_NAME) : newName, NONE);
    }

    public static TvLibraryActionPayload removeArt(int containerId, UUID id) {
        return new TvLibraryActionPayload(containerId, Action.REMOVE_ART, Kind.COMPONENT, id.toString(), "", NONE);
    }

    public static TvLibraryActionPayload renamePicture(int containerId, String hash, String newName) {
        return new TvLibraryActionPayload(containerId, Action.RENAME_PICTURE, Kind.COMPONENT, hash,
                newName.length() > MAX_NAME ? newName.substring(0, MAX_NAME) : newName, NONE);
    }

    public static TvLibraryActionPayload removePicture(int containerId, String hash) {
        return new TvLibraryActionPayload(containerId, Action.REMOVE_PICTURE, Kind.COMPONENT, hash, "", NONE);
    }

    public static TvLibraryActionPayload rename(int containerId, Kind kind, String name, String newName) {
        return new TvLibraryActionPayload(containerId, Action.RENAME, kind, name, newName, NONE);
    }

    public static TvLibraryActionPayload remove(int containerId, Kind kind, String name) {
        return new TvLibraryActionPayload(containerId, Action.REMOVE, kind, name, "", NONE);
    }

    /** O componente do conteúdo; lança {@code DecoderException} se o conteúdo não decodifica. */
    public TvComponent component() {
        return TvNbtBytes.decode(TvComponent.CODEC, content, MAX_BYTES);
    }

    /** A arte do conteúdo; lança {@code DecoderException} se o conteúdo não decodifica ou a arte não vale. */
    public TvPixelArt art() {
        return TvNbtBytes.decode(TvPixelArt.CODEC, content, MAX_BYTES);
    }

    /** O id da arte de um {@code RENAME_ART} ou {@code REMOVE_ART}; lança {@code IllegalArgumentException} se não é um. */
    public UUID artId() {
        return UUID.fromString(name);
    }

    /** O tema do conteúdo; lança {@code DecoderException} se o conteúdo não decodifica. */
    public TvTheme theme() {
        return TvNbtBytes.decode(TvCodecs.THEME, content, MAX_BYTES);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
