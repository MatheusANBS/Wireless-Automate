package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLayouts;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvUnit;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Os layouts prontos da Tela: dentro da área, sem passar dos tetos, ligações só com dado, ida e volta pelo NBT. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvLayoutsGameTests {
    private static final int MAX_ELEMENTS = 128;
    private static final int MAX_BINDINGS = 64;
    private static final List<ResourceType> TYPES = List.of(ResourceType.ITEM, ResourceType.FLUID,
            ResourceType.ENERGY, ResourceType.CHEMICAL);
    private static final UUID NETWORK = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final TvLayouts.Storage STORAGE = new TvLayouts.Storage(
            GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 2, 3)), ResourceType.ITEM);
    private static final TvArea[] AREAS = {TvArea.grown(1, 1, TvArea.Grow.UP_RIGHT),
        TvArea.grown(2, 2, TvArea.Grow.UP_RIGHT), TvArea.grown(3, 2, TvArea.Grow.UP_RIGHT),
        TvArea.grown(4, 3, TvArea.Grow.CENTER), TvArea.grown(32, 32, TvArea.Grow.UP_LEFT)};

    private TvLayoutsGameTests() {
    }

    private static List<TvPiece> build(TvLayouts.Kind kind, TvArea area, boolean withData) {
        return TvLayouts.build(kind, area, key -> key, withData ? NETWORK : null, withData ? STORAGE : null, TYPES);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layoutsProntosFicamDentroDaAreaEDosTetos(GameTestHelper helper) {
        for (TvLayouts.Kind kind : TvLayouts.Kind.values()) {
            for (TvArea area : AREAS) {
                List<TvPiece> pieces = build(kind, area, true);
                for (TvPiece piece : pieces) {
                    boolean inside = piece.x() >= 0 && piece.y() >= 0 && piece.width() >= 1 && piece.height() >= 1
                            && piece.x() + piece.width() <= area.pixelWidth()
                            && piece.y() + piece.height() <= area.pixelHeight();
                    if (!inside) {
                        helper.fail(kind + " " + area + " fora da área: " + piece);
                    }
                }
                TvLayout layout = TvLayout.of(pieces);
                if (layout.pieces().size() > MAX_ELEMENTS || layout.bindings().size() > MAX_BINDINGS) {
                    helper.fail(kind + " " + area + " passa dos tetos");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layoutsProntosSoLigamComDado(GameTestHelper helper) {
        for (TvLayouts.Kind kind : TvLayouts.Kind.values()) {
            for (TvPiece piece : build(kind, AREAS[2], false)) {
                boolean clock = piece.binding().map(b -> b.clock() != null).orElse(false);
                if (piece.binding().isPresent() && !clock) {
                    helper.fail(kind + " liga sem dado: " + piece);
                }
            }
        }
        if (TvLayout.of(build(TvLayouts.Kind.ENERGY, AREAS[2], true)).bindings().size() < 3
                || TvLayout.of(build(TvLayouts.Kind.STORAGE, AREAS[2], true)).bindings().size() < 3
                || TvLayout.of(build(TvLayouts.Kind.NETWORK, AREAS[2], true)).bindings().isEmpty()
                || TvLayout.of(build(TvLayouts.Kind.CLOCK, AREAS[2], false)).bindings().isEmpty()) {
            helper.fail("com dado, as peças de valor deviam ter ligação");
        }
        helper.succeed();
    }

    /**
     * A vazão de um armazenamento chega por segundo: o layout Armazenamento a mostra pela unidade (por tick na
     * energia, por segundo nos outros), nunca com o sufixo "/t" fixo sobre o número por segundo.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layoutArmazenamentoMostraAVazaoNaUnidadeCerta(GameTestHelper helper) {
        for (ResourceType type : TYPES) {
            TvLayouts.Storage storage = new TvLayouts.Storage(STORAGE.pos(), type);
            List<TvPiece> pieces = TvLayouts.build(TvLayouts.Kind.STORAGE, AREAS[3], key -> key, NETWORK, storage,
                    TYPES);
            TvUnit expected = type.ratePerTick() ? TvUnit.TICK : TvUnit.SECOND;
            for (TvPiece piece : pieces) {
                boolean rate = piece.type() == TvPieceType.VALUE && piece.binding().map(TvBinding::rate).orElse(false);
                if (rate && (piece.format().unit() != expected || !piece.format().suffix().isEmpty())) {
                    helper.fail(type + ": vazão com unidade " + piece.format().unit() + " e sufixo \""
                            + piece.format().suffix() + "\"");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layoutsProntosVoltamIguaisPeloNbtEServemA1x1(GameTestHelper helper) {
        for (TvLayouts.Kind kind : TvLayouts.Kind.values()) {
            for (TvArea area : AREAS) {
                TvLayout layout = TvLayout.of(build(kind, area, true));
                Tag tag = TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout).getOrThrow();
                TvLayout back = TvLayout.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
                if (!layout.pieces().equals(back.pieces())) {
                    helper.fail(kind + " " + area + " não volta igual pelo NBT");
                }
                if (area.cells() == 1 && layout.clampedTo(area).pieces().size() != layout.pieces().size()) {
                    helper.fail(kind + " perde peças ao caber em 1 × 1");
                }
            }
            for (TvArea area : AREAS) {
                List<TvPiece> pieces = build(kind, area, true);
                for (int a = 0; a < pieces.size(); a++) {
                    for (int b = a + 1; b < pieces.size(); b++) {
                        TvPiece p = pieces.get(a);
                        TvPiece q = pieces.get(b);
                        if (p.type() == q.type() && p.type() != TvPieceType.SHAPE && p.x() == q.x() && p.y() == q.y()
                                && p.width() == q.width() && p.height() == q.height()) {
                            helper.fail(kind + " " + area + " duas peças iguais empilhadas: " + p);
                        }
                    }
                }
            }
        }
        helper.succeed();
    }
}
