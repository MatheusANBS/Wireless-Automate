package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Os campos de estilo das peças da etapa 3: ida e volta, cortes, só o que difere do padrão e o NBT antigo. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvPartGameTests {
    private TvPartGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void partStyleRoundTripsAndClamps(GameTestHelper helper) {
        TvPartStyle part = TvPartStyle.DEFAULT.withSweep(180).withWindow(5)
                .withOrientation(TvPartStyle.Orientation.VERTICAL).withRange(1, 50).withReverse(true)
                .withMode(TvPartStyle.GraphMode.COLUMNS).withItem(Optional.of(ResourceLocation.withDefaultNamespace("diamond")));
        TvPiece piece = new TvPiece(TvPieceType.GAUGE, 1, 2, 30, 30, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withPart(part));
        Tag tag = TvPiece.CODEC.encodeStart(NbtOps.INSTANCE, piece).getOrThrow();
        helper.assertTrue(TvPiece.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().equals(piece), "ida e volta");
        helper.assertValueEqual(TvPartStyle.DEFAULT.withSweep(999).sweep(), 360, "abertura cortada");
        helper.assertValueEqual(TvPartStyle.DEFAULT.withWindow(99).window(), 10, "janela cortada");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void defaultPartWritesNothingAndOldNbtReads(GameTestHelper helper) {
        CompoundTag style = (CompoundTag) TvStyle.CODEC.encodeStart(NbtOps.INSTANCE, TvStyle.DEFAULT).getOrThrow();
        helper.assertTrue(!style.contains("direction") && !style.contains("sweep") && !style.contains("item"),
                "o padrão não grava os campos novos");
        CompoundTag old = new CompoundTag();
        old.putInt("color", 5);
        helper.assertTrue(TvStyle.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow().part().equals(TvPartStyle.DEFAULT),
                "o estilo antigo lê com a parte padrão");
        TvLayout layout = TvLayout.of(List.of(new TvPiece(TvPieceType.ARROW, 0, 0, 8, 10, 0, "", Optional.empty(),
                TvFormat.DEFAULT, TvStyle.DEFAULT)));
        Tag tag = TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout).getOrThrow();
        helper.assertTrue(TvLayout.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().pieces().get(0).type()
                == TvPieceType.ARROW, "a Seta volta como Seta");
        helper.succeed();
    }
}
