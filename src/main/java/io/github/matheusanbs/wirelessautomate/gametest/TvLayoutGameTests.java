package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** O modelo do layout da Tela de Sinal e o codec de NBT: ida e volta, peça inválida, cortes e ajuste à área. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvLayoutGameTests {
    private TvLayoutGameTests() {
    }

    private static TvPiece plain(TvPieceType type, int x, int w, Optional<TvBinding> binding) {
        return new TvPiece(type, x, 0, w, 10, 0, "", binding, TvFormat.DEFAULT, TvStyle.DEFAULT);
    }

    private static Tag encode(TvLayout layout) {
        return TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout).getOrThrow();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layoutRoundTripNoNbt(GameTestHelper helper) {
        TvBinding binding = new TvBinding(UUID.randomUUID(), ResourceType.ENERGY, TvMetric.OUT);
        TvFormat format = new TvFormat(2, false, true, "▲ ", "/s", TvUnit.TICK);
        TvStyle style = new TvStyle(0xFF112233, 0x80445566, 0xFF778899, 2, 12, TvAlign.CENTER, false, "Base");
        TvLayout layout = TvLayout.of(List.of(
                new TvPiece(TvPieceType.VALUE, 3, 4, 50, 20, 2, "valor", Optional.of(binding), format, style),
                new TvPiece(TvPieceType.TEXT, 5, 6, 60, 22, 1, "texto", Optional.empty(), format, style),
                new TvPiece(TvPieceType.SHAPE, 7, 8, 70, 24, 3, "forma", Optional.empty(), format, style)));
        TvLayout back = TvLayout.CODEC.parse(NbtOps.INSTANCE, encode(layout)).getOrThrow();
        helper.assertValueEqual(back, layout, "layout depois da ida e volta");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pecaInvalidaELargadaSemDerrubarOLayout(GameTestHelper helper) {
        ListTag list = new ListTag();
        for (String type : new String[] {"text", "nao_existe", "shape"}) {
            CompoundTag piece = new CompoundTag();
            piece.putString("type", type);
            list.add(piece);
        }
        CompoundTag root = new CompoundTag();
        root.put("pieces", list);
        TvLayout read = TvLayout.CODEC.parse(NbtOps.INSTANCE, root).getOrThrow();
        helper.assertValueEqual(read.pieces().size(), 2, "peças lidas");
        helper.assertValueEqual(read.pieces().get(1).type(), TvPieceType.SHAPE, "a última sobrevive");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void textosLongosSaoCortadosNaLeitura(GameTestHelper helper) {
        CompoundTag style = new CompoundTag();
        style.putString("text", "a".repeat(300));
        CompoundTag format = new CompoundTag();
        format.putInt("decimals", 9);
        CompoundTag piece = new CompoundTag();
        piece.putString("type", "text");
        piece.putString("name", "n".repeat(100));
        piece.putInt("w", 0);
        piece.put("style", style);
        piece.put("format", format);
        ListTag list = new ListTag();
        list.add(piece);
        CompoundTag root = new CompoundTag();
        root.put("pieces", list);
        TvPiece read = TvLayout.CODEC.parse(NbtOps.INSTANCE, root).getOrThrow().pieces().get(0);
        helper.assertValueEqual(read.style().text().length(), 128, "texto");
        helper.assertValueEqual(read.name().length(), 32, "nome");
        helper.assertValueEqual(read.format().decimals(), 3, "casas");
        helper.assertValueEqual(read.width(), 1, "largura");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bindingsDistintosNaOrdem(GameTestHelper helper) {
        TvBinding first = new TvBinding(UUID.randomUUID(), ResourceType.ITEM, TvMetric.IN);
        TvBinding second = new TvBinding(UUID.randomUUID(), ResourceType.FLUID, TvMetric.BALANCE);
        TvBinding absent = new TvBinding(UUID.randomUUID(), ResourceType.ENERGY, TvMetric.FULL);
        TvLayout layout = TvLayout.of(List.of(
                plain(TvPieceType.VALUE, 0, 10, Optional.of(first)),
                plain(TvPieceType.VALUE, 10, 10, Optional.of(second)),
                plain(TvPieceType.VALUE, 20, 10, Optional.of(first))));
        helper.assertValueEqual(layout.bindings().size(), 2, "ligações distintas");
        helper.assertValueEqual(layout.bindingIndex(first), 0, "índice da primeira");
        helper.assertValueEqual(layout.bindingIndex(second), 1, "índice da segunda");
        helper.assertValueEqual(layout.bindingIndex(absent), -1, "ligação ausente");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void clampedToTrazAsPecasParaDentro(GameTestHelper helper) {
        TvLayout layout = TvLayout.of(List.of(
                plain(TvPieceType.SHAPE, 180, 40, Optional.empty()),
                plain(TvPieceType.SHAPE, 0, 300, Optional.empty())));
        TvLayout clamped = layout.clampedTo(new TvArea(6, 3, 0, 0));
        helper.assertValueEqual(clamped.pieces().get(0).x(), 152, "x da primeira");
        helper.assertValueEqual(clamped.pieces().get(0).width(), 40, "largura da primeira");
        helper.assertValueEqual(clamped.pieces().get(1).x(), 0, "x da segunda");
        helper.assertValueEqual(clamped.pieces().get(1).width(), 192, "largura da segunda");
        helper.succeed();
    }
}
