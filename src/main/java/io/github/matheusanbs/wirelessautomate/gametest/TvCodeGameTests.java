package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvCodeText;
import io.github.matheusanbs.wirelessautomate.tv.TvCodes;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLayouts;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvRule;
import io.github.matheusanbs.wirelessautomate.tv.TvRuleOp;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Os códigos de texto da Tela de Sinal ({@link TvCodes}): o layout com a arte e a área de origem de ida e volta, a
 * arte de ida e volta, o lixo e o código do outro tipo recusados, e o tamanho do código de um layout típico e de um
 * no teto de pixels (no log, para o relatório).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvCodeGameTests {
    private TvCodeGameTests() {
    }

    private static TvPixelArt art(String name, int side, long seed) {
        int[] palette = new int[16];
        for (int i = 1; i < 16; i++) {
            palette[i] = 0xFF000000 | i * 0x111111;
        }
        byte[] pixels = new byte[side * side];
        Random random = new Random(seed);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) random.nextInt(16);
        }
        return new TvPixelArt(UUID.randomUUID(), name, side, side, palette, pixels);
    }

    private static TvPiece image(UUID art, int x) {
        return new TvPiece(TvPieceType.IMAGE, x, 0, 32, 32, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withPart(TvPartStyle.DEFAULT.withImage(Optional.of(art), 2,
                        TvPartStyle.VAlign.MIDDLE)));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layoutComArteEAreaVoltaIgual(GameTestHelper helper) {
        TvPixelArt logo = art("Logo", 16, 1);
        TvLayout layout = TvLayout.of(List.of(image(logo.id(), 4)), TvTheme.DEFAULT,
                TvBackground.DEFAULT, true, List.of(logo));
        String code = TvCodes.layout(layout, 128, 64, false);
        helper.assertTrue(code != null && code.startsWith(TvCodes.LAYOUT_PREFIX), "sem código");
        TvCodes.Layout read = TvCodes.readLayout("\n  " + code + "  \n").value();
        helper.assertTrue(read != null, "o código não leu");
        helper.assertTrue(read.layout().equals(layout), "o layout mudou: " + read.layout());
        helper.assertTrue(read.width() == 128 && read.height() == 64 && !read.keepTheme(), "área " + read);
        helper.assertTrue(!read.needsScale(128, 64) && read.needsScale(64, 64), "escala");
        TvCodes.Layout saved = TvCodes.readLayout(TvCodes.layout(layout, 128, 64, true)).value();
        helper.assertTrue(saved != null && saved.keepTheme(), "o layout salvo não fica com o tema de quem cola");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void arteVoltaIgualELixoNaoLe(GameTestHelper helper) {
        TvPixelArt logo = art("Logo", 128, 2);
        String code = TvCodes.art(logo);
        helper.assertTrue(code != null && code.startsWith(TvCodes.ART_PREFIX), "sem código");
        helper.assertTrue(logo.equals(TvCodes.readArt(code).value()), "a arte mudou");
        helper.assertTrue(TvCodes.readLayout(code).value() == null, "um código de arte não é de layout");
        helper.assertTrue(TvCodes.readArt(TvCodes.layout(TvLayout.EMPTY, 32, 32, false)).value() == null,
                "um código de layout não é de arte");
        helper.assertTrue(TvCodes.readLayout("lixo").value() == null && TvCodes.readArt(null).value() == null,
                "lixo leu");
        String notCompound = TvCodeText.encode(TvCodes.ART_PREFIX, new byte[] {8, 0, 0, 0, 0});
        helper.assertTrue(TvCodes.readArt(notCompound).value() == null, "NBT que não é composto leu");
        CompoundTag broken = (CompoundTag) TvPixelArt.CODEC.encodeStart(NbtOps.INSTANCE, logo).getOrThrow();
        broken.putInt("w", 200);
        helper.assertTrue(TvCodes.readArt(TvCodeText.encode(TvCodes.ART_PREFIX, nbt(broken))).value() == null,
                "arte inválida leu");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tamanhoDoCodigoTipicoENoTeto(GameTestHelper helper) {
        TvArea area = TvArea.grown(4, 2, TvArea.Grow.UP_RIGHT);
        TvLayout typical = TvLayout.of(TvLayouts.build(TvLayouts.Kind.ENERGY, area, key -> key, UUID.randomUUID(),
                null, List.of(ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY)));
        String small = TvCodes.layout(typical, area.pixelWidth(), area.pixelHeight(), false);
        List<TvPixelArt> arts = new ArrayList<>();
        List<TvPiece> pieces = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            TvPixelArt art = art("Ruído " + i, 128, 10 + i);
            arts.add(art);
            pieces.add(image(art.id(), i * 32));
        }
        TvLayout full = TvLayout.of(pieces, TvTheme.DEFAULT, TvBackground.DEFAULT, false, arts);
        helper.assertTrue(full.imagePixels() == 131_072, "pixels " + full.imagePixels());
        String big = TvCodes.layout(full, 1024, 1024, false);
        helper.assertTrue(small != null && big != null, "sem código");
        helper.assertTrue(big.length() <= TvCodeText.MAX_TEXT, "o código no teto passa do texto: " + big.length());
        helper.assertTrue(full.equals(TvCodes.readLayout(big).value().layout()), "o layout no teto mudou");
        WirelessAutomate.LOGGER.info("Tela de Sinal: código de um layout típico {} caracteres; no teto de pixels "
                + "{} caracteres ({} bytes de NBT)", small.length(), big.length(), TvLayoutPayload.encodedSize(full));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void arteLegivelVoltaIgualAComprimida(GameTestHelper helper) {
        TvPixelArt logo = art("Logo", 24, 3);
        for (boolean english : new boolean[] {false, true}) {
            String text = TvCodes.artText(logo, english);
            helper.assertTrue(text.startsWith("WA-ARTE1\n")
                    && text.contains(english ? "size: 24x24" : "tamanho: 24x24"),
                    "cabeçalho " + text.lines().limit(3).toList());
            TvPixelArt read = TvCodes.readArt(text).value();
            helper.assertTrue(read != null && sameContent(read, logo), "a arte legível mudou");
            TvPixelArt back = TvCodes.readArt(TvCodes.art(read)).value();
            helper.assertTrue(back != null && sameContent(back, logo), "a comprimida da legível mudou");
        }
        TvPixelArt example = TvCodes.readArt(TvCodeExamples.ART).value();
        helper.assertTrue(example != null && example.name().equals("Coração") && example.width() == 16
                && example.pixel(2, 4) == 5 && example.palette()[7] == 0xFFFFB08A, "o exemplo não leu");
        TvCodes.Read<TvPixelArt> bad = TvCodes.readArt(TvCodeExamples.ART.replace("..155551155551..",
                "..15555115551.."));
        helper.assertTrue("code.art.row".equals(bad.error()) && Integer.valueOf(9).equals(bad.args()[0]),
                "erro " + bad.error() + " " + List.of(bad.args()));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layoutLegivelComCoresArteELigacoes(GameTestHelper helper) {
        TvCodes.Layout read = TvCodes.readLayout(TvCodeExamples.LAYOUT).value();
        helper.assertTrue(read != null && read.width() == 96 && read.height() == 64, "o exemplo não leu");
        TvLayout layout = read.layout();
        helper.assertTrue(layout.pieces().size() == 3 && layout.images().size() == 1, "peças " + layout.pieces());
        TvPixelArt heart = layout.images().get(0);
        TvPiece text = layout.pieces().get(0);
        TvPiece value = layout.pieces().get(1);
        TvPiece image = layout.pieces().get(2);
        helper.assertTrue(text.style().text().equals("Base norte")
                && text.style().color() == TvColor.theme(TvColor.TEXT),
                "texto " + text.style());
        helper.assertTrue(value.style().color() == 0xFFFFB08A && value.binding().isPresent()
                && value.binding().get().source() == TvBinding.Source.CLOCK, "valor " + value);
        helper.assertTrue(image.style().part().image().orElseThrow().equals(heart.id())
                && heart.name().equals("Coração"),
                "a Imagem não aponta a arte");
        helper.assertTrue(layout.background().kind() == TvBackground.Kind.SOLID
                && layout.background().top() == 0xFF1B1920, "fundo " + layout.background());
        helper.assertTrue(layout.prunedImages().equals(layout), "a arte não vale para a Imagem");

        String broken = TvCodeExamples.LAYOUT.replace("\"metric\": \"time\"", "\"metric\": \"semana\"");
        TvCodes.Layout dropped = TvCodes.readLayout(broken).value();
        helper.assertTrue(dropped != null && dropped.dropped() == 1 && dropped.layout().pieces().size() == 3
                && dropped.layout().pieces().get(1).binding().isEmpty(), "a ligação que não lê não saiu");
        UUID network = UUID.randomUUID();
        TvLayout bound = layout.withPieces(List.of(text.withBinding(Optional.of(new TvBinding(network,
                ResourceType.ITEM, TvMetric.STORED))), value, image));
        TvCodes.Pruned pruned = TvCodes.withoutBindings(bound, b -> b.source() != TvBinding.Source.NETWORK);
        helper.assertTrue(pruned.removed() == 1 && pruned.layout().pieces().get(0).binding().isEmpty()
                && pruned.layout().pieces().get(1).binding().isPresent(), "a rede inexistente não saiu");

        helper.assertTrue("code.layout.color".equals(TvCodes.readLayout(TvCodeExamples.LAYOUT
                .replace("tema:texto", "tema:roxo")).error()), "cor ruim leu");
        helper.assertTrue("code.layout.piece".equals(TvCodes.readLayout(TvCodeExamples.LAYOUT
                .replace("\"type\": \"value\"", "\"type\": \"radio\"")).error()), "peça ruim leu");
        TvCodes.Read<TvCodes.Layout> json = TvCodes.readLayout(TvCodeExamples.LAYOUT.replace("\"area_h\": 64,",
                "\"area_h\": 64 \"x\""));
        helper.assertTrue("code.layout.json".equals(json.error()), "JSON ruim leu: " + json.error());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void temaIncompletoPapelNoTemaEErroDaArteNoJson(GameTestHelper helper) {
        TvCodes.Layout partial = TvCodes.readLayout("WA-TELA1\n{\"area_w\": 96, \"area_h\": 64, "
                + "\"theme\": {\"name\": \"Meu\", \"bg\": \"#101010\"}}").value();
        helper.assertTrue(partial != null && partial.layout().theme().bg() == 0xFF101010
                && partial.layout().theme().card() == TvTheme.GRAPHITE.card()
                && partial.layout().theme().low() == TvTheme.GRAPHITE.low(),
                "o tema incompleto não foi completado pelo Grafite");

        TvCodes.Read<TvCodes.Layout> role = TvCodes.readLayout("WA-TELA1\n{\"theme\": {\"name\": \"x\", "
                + "\"bg\": \"tema:texto\"}}");
        helper.assertTrue("code.layout.theme_role".equals(role.error()), "papel no tema leu: " + role.error());

        String rows = "\"........\", ".repeat(7);
        TvCodes.Read<TvCodes.Layout> color = TvCodes.readLayout("WA-TELA1\n{\"images\": [{\"nome\": \"Logo\", "
                + "\"tamanho\": \"8x8\", \"paleta\": [\"-\", \"GG1920\"], \"linhas\": [" + rows
                + "\"........\"]}]}");
        helper.assertTrue("code.layout.art.color".equals(color.error()) && "Logo".equals(color.args()[0])
                && "GG1920".equals(color.args()[1]), "o erro da cor não diz a arte: " + color.error());
        TvCodes.Read<TvCodes.Layout> row = TvCodes.readLayout("WA-TELA1\n{\"images\": [{\"tamanho\": \"8x8\", "
                + "\"linhas\": [\"........\", \"........\", \".......\", " + rows + "]}]}");
        helper.assertTrue("code.layout.art.row".equals(row.error()) && "#1".equals(row.args()[0])
                && Integer.valueOf(3).equals(row.args()[1]), "o erro da fileira não diz a arte e a fileira: "
                + row.error() + " " + Arrays.toString(row.args()));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layoutLegivelDeIdaEVolta(GameTestHelper helper) {
        TvPixelArt logo = art("Logo", 16, 4);
        TvRule rule = new TvRule(TvRuleOp.LT, 10, Optional.empty(), Optional.of(TvColor.theme(TvColor.LOW)),
                Optional.of(0x80FF0000), true, false);
        TvPiece text = new TvPiece(TvPieceType.TEXT, 2, 2, 40, 10, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withText("Olá \"#FF0000\" tema:texto")).withRules(List.of(rule));
        TvLayout layout = TvLayout.of(List.of(text, image(logo.id(), 40)),
                TvTheme.DEFAULT.withColor(TvColor.HIGH, 0xFF00FF00), TvBackground.DEFAULT, true, List.of(logo));
        for (boolean english : new boolean[] {false, true}) {
            String code = TvCodes.layoutText(layout, 96, 64, false, english);
            TvCodes.Layout read = TvCodes.readLayout(code).value();
            helper.assertTrue(read != null && read.layout().equals(layout), "o layout legível mudou:\n" + code);
            helper.assertTrue(code.contains(english ? "theme:low" : "tema:baixa") && code.contains("#FF000080"),
                    "as cores não saíram por nome");
        }
        WirelessAutomate.LOGGER.info("Tela de Sinal: layout legível de exemplo:\n{}",
                TvCodes.layoutText(TvCodes.readLayout(TvCodeExamples.LAYOUT).value().layout(), 96, 64, false, false));
        helper.succeed();
    }

    private static boolean sameContent(TvPixelArt a, TvPixelArt b) {
        return a.name().equals(b.name()) && a.width() == b.width() && a.height() == b.height()
                && Arrays.equals(a.palette(), b.palette()) && Arrays.equals(a.pixels(), b.pixels());
    }

    private static byte[] nbt(CompoundTag tag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(out)) {
            NbtIo.writeAnyTag(tag, data);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }
}
