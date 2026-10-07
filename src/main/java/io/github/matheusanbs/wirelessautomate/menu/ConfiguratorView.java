package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.preset.PresetCode;
import io.github.matheusanbs.wirelessautomate.preset.PresetLibrary;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * O que a tela do Configurador mostra: a varinha (cópia, modo e área), a biblioteca do jogador, os
 * roteadores da área marcada, o código exportado e o último aviso do servidor. Só resumos: os
 * presets inteiros nunca vão para o cliente por aqui.
 */
public record ConfiguratorView(Wand wand, List<LibraryEntry> library, Area area, Optional<Export> export,
        Optional<Component> notice, int noticeId) {
    /** Linhas de face mostradas por preset. */
    public static final int MAX_LINES = 8;
    /** Pontos do mapa da área. */
    public static final int MAX_DOTS = 1024;
    public static final int MAX_MACHINES = 32;

    public static final ConfiguratorView EMPTY = new ConfiguratorView(Wand.EMPTY, List.of(), Area.NONE,
            Optional.empty(), Optional.empty(), 0);

    /**
     * A varinha na mão.
     *
     * @param presetFaces   faces configuradas na cópia do pincel; −1 sem cópia
     * @param corner1       cantos e origem só quando estão na dimensão do jogador
     * @param clipboardSize roteadores na cópia de área (0 = sem cópia)
     * @param anchorHits    posições da cópia, coladas na origem, que têm roteador
     */
    public record Wand(boolean areaMode, int presetFaces, int presetNetworks, Optional<BlockPos> corner1,
            Optional<BlockPos> corner2, Optional<BlockPos> anchor, int clipboardSize, int anchorHits) {
        public static final Wand EMPTY = new Wand(false, -1, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                0, 0);

        public boolean hasPreset() {
            return presetFaces >= 0;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, Wand> STREAM_CODEC = StreamCodec.of(
                (buf, w) -> {
                    buf.writeBoolean(w.areaMode);
                    buf.writeVarInt(w.presetFaces + 1);
                    buf.writeVarInt(w.presetNetworks);
                    OPTIONAL_POS.encode(buf, w.corner1);
                    OPTIONAL_POS.encode(buf, w.corner2);
                    OPTIONAL_POS.encode(buf, w.anchor);
                    buf.writeVarInt(w.clipboardSize);
                    buf.writeVarInt(w.anchorHits);
                },
                buf -> new Wand(buf.readBoolean(), buf.readVarInt() - 1, buf.readVarInt(), OPTIONAL_POS.decode(buf),
                        OPTIONAL_POS.decode(buf), OPTIONAL_POS.decode(buf), buf.readVarInt(), buf.readVarInt()));
    }

    /** Uma face ativa de um preset, para o resumo. */
    public record FaceLine(ResourceType type, RelativeSide side, PortMode mode, int filterSize) {
        public static final StreamCodec<RegistryFriendlyByteBuf, FaceLine> STREAM_CODEC = StreamCodec.composite(
                NeoForgeStreamCodecs.enumCodec(ResourceType.class), FaceLine::type,
                NeoForgeStreamCodecs.enumCodec(RelativeSide.class), FaceLine::side,
                NeoForgeStreamCodecs.enumCodec(PortMode.class), FaceLine::mode,
                ByteBufCodecs.VAR_INT, FaceLine::filterSize,
                FaceLine::new);
    }

    /** Um preset da biblioteca. */
    public record LibraryEntry(String name, int faces, int networks, List<FaceLine> lines) {
        public static final StreamCodec<RegistryFriendlyByteBuf, LibraryEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), LibraryEntry::name,
                ByteBufCodecs.VAR_INT, LibraryEntry::faces,
                ByteBufCodecs.VAR_INT, LibraryEntry::networks,
                FaceLine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), LibraryEntry::lines,
                LibraryEntry::new);

        public static LibraryEntry of(String name, RouterPreset preset) {
            return new LibraryEntry(name, preset.configuredFaces(), preset.distinctNetworks().size(),
                    ConfiguratorView.lines(preset));
        }
    }

    /** As faces com modo de um preset, na ordem tipo × lado, no máximo {@link #MAX_LINES}. */
    public static List<FaceLine> lines(RouterPreset preset) {
        List<FaceLine> lines = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            for (RelativeSide side : RelativeSide.values()) {
                FaceConfig face = preset.face(type, side);
                if (face.mode() != PortMode.NONE && lines.size() < MAX_LINES) {
                    lines.add(new FaceLine(type, side, face.mode(), face.filter().entries().size()));
                }
            }
        }
        return List.copyOf(lines);
    }

    /** Um roteador da área, relativo ao canto mínimo, e o índice da máquina em {@link Area#machines}. */
    public record Dot(int dx, int dy, int dz, boolean configured, int machine) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Dot> STREAM_CODEC = StreamCodec.of(
                (buf, d) -> {
                    buf.writeVarInt(d.dx);
                    buf.writeVarInt(d.dy);
                    buf.writeVarInt(d.dz);
                    buf.writeBoolean(d.configured);
                    buf.writeVarInt(d.machine);
                },
                buf -> new Dot(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));
    }

    /**
     * A área marcada.
     *
     * @param problem chave do problema ({@code incomplete}, {@code too_large}, {@code too_far},
     *                {@code other_dimension}); vazio se a área serve
     * @param size    tamanho em x, y e z (0 sem área)
     */
    public record Area(Optional<String> problem, BlockPos min, BlockPos size, List<Dot> routers,
            List<ResourceLocation> machines) {
        public static final Area NONE = new Area(Optional.of("incomplete"), BlockPos.ZERO, BlockPos.ZERO, List.of(),
                List.of());

        public boolean usable() {
            return problem.isEmpty();
        }

        /** Roteadores presos a uma máquina ({@code machine} −1 = todas). */
        public int count(int machine) {
            if (machine < 0) {
                return routers.size();
            }
            int count = 0;
            for (Dot dot : routers) {
                if (dot.machine == machine) {
                    count++;
                }
            }
            return count;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, Area> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(32)), Area::problem,
                BlockPos.STREAM_CODEC, Area::min,
                BlockPos.STREAM_CODEC, Area::size,
                Dot.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_DOTS)), Area::routers,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MACHINES)), Area::machines,
                Area::new);
    }

    /** O código de um preset da biblioteca, pedido com Exportar. */
    public record Export(int index, String name, String code) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Export> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Export::index,
                ByteBufCodecs.stringUtf8(256), Export::name,
                ByteBufCodecs.stringUtf8(PresetCode.MAX_CODE_LENGTH),
                Export::code,
                Export::new);
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<BlockPos>> OPTIONAL_POS =
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC).cast();

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfiguratorView> STREAM_CODEC = StreamCodec.composite(
            Wand.STREAM_CODEC, ConfiguratorView::wand,
            LibraryEntry.STREAM_CODEC.apply(ByteBufCodecs.list(PresetLibrary.MAX_PRESETS)),
            ConfiguratorView::library,
            Area.STREAM_CODEC, ConfiguratorView::area,
            ByteBufCodecs.optional(Export.STREAM_CODEC), ConfiguratorView::export,
            ComponentSerialization.OPTIONAL_STREAM_CODEC, ConfiguratorView::notice,
            ByteBufCodecs.VAR_INT, ConfiguratorView::noticeId,
            ConfiguratorView::new);
}
