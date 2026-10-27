package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvCodecs;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: uma ação na biblioteca do jogador, só com o menu da Tela de Sinal aberto. {@code OPEN} pede a
 * biblioteca; {@code SAVE_COMPONENT} e {@code SAVE_THEME} levam o conteúdo (NBT em bytes, até {@link #MAX_BYTES});
 * {@code RENAME} e {@code REMOVE} levam o tipo e o nome, e o primeiro o nome novo. O servidor valida tudo.
 */
public record TvLibraryActionPayload(int containerId, Action action, Kind kind, String name, String newName,
        byte[] content) implements CustomPacketPayload {
    public static final int MAX_BYTES = 64 * 1024;
    public static final int MAX_NAME = 64;
    public static final Type<TvLibraryActionPayload> TYPE = new Type<>(WirelessAutomate.id("tv_library_action"));

    public enum Action { OPEN, SAVE_COMPONENT, SAVE_THEME, RENAME, REMOVE }

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

    /** O tema do conteúdo; lança {@code DecoderException} se o conteúdo não decodifica. */
    public TvTheme theme() {
        return TvNbtBytes.decode(TvCodecs.THEME, content, MAX_BYTES);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
