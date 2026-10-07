package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.preset.PresetCode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: uma ação na tela do Configurador aberta. Os campos usados dependem de {@link #op}:
 * <ul>
 *   <li>{@code SAVE}: {@code text} = nome; salva a cópia da varinha na biblioteca</li>
 *   <li>{@code RENAME}: {@code index} e {@code text} = nome novo</li>
 *   <li>{@code DELETE}, {@code LOAD} (Aplicar: a varinha passa a colar esse preset), {@code EXPORT}: {@code index}</li>
 *   <li>{@code IMPORT}: {@code text} = código {@code WA1:}</li>
 *   <li>{@code SET_MODE}: {@code index} 0 = pincel, 1 = área</li>
 *   <li>{@code APPLY_AREA}: {@code index} −1 = a cópia da varinha, senão o preset da biblioteca;
 *       {@code text} = id do bloco da máquina, ou vazio para todas</li>
 *   <li>{@code CLEAR_WAND}, {@code CLEAR_AREA}, {@code COPY_AREA}, {@code CLEAR_CLIPBOARD}, {@code PASTE}: nenhum</li>
 * </ul>
 */
public record ConfiguratorActionPayload(int containerId, Op op, int index, String text) implements CustomPacketPayload {
    public enum Op {
        SAVE,
        RENAME,
        DELETE,
        LOAD,
        EXPORT,
        IMPORT,
        CLEAR_WAND,
        SET_MODE,
        CLEAR_AREA,
        COPY_AREA,
        CLEAR_CLIPBOARD,
        PASTE,
        APPLY_AREA
    }

    public static ConfiguratorActionPayload of(int containerId, Op op) {
        return new ConfiguratorActionPayload(containerId, op, 0, "");
    }

    public static final Type<ConfiguratorActionPayload> TYPE = new Type<>(WirelessAutomate.id("configurator_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfiguratorActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ConfiguratorActionPayload::containerId,
                    NeoForgeStreamCodecs.enumCodec(Op.class), ConfiguratorActionPayload::op,
                    ByteBufCodecs.VAR_INT, ConfiguratorActionPayload::index,
                    ByteBufCodecs.stringUtf8(PresetCode.MAX_CODE_LENGTH), ConfiguratorActionPayload::text,
                    ConfiguratorActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
