package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvCodecs;
import io.github.matheusanbs.wirelessautomate.tv.TvFont;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvRule;
import io.github.matheusanbs.wirelessautomate.tv.TvRuleOp;
import io.github.matheusanbs.wirelessautomate.tv.TvRules;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import io.github.matheusanbs.wirelessautomate.tv.TvUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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

    private static TvRule rule(TvRuleOp op, double value, Optional<TvBinding> bind) {
        return new TvRule(op, value, bind, Optional.of(0xFF010203), Optional.empty(), true, false);
    }

    private static TvBlockEntity entity(GameTestHelper helper) {
        return new TvBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)),
                ModBlocks.SIGNAL_SCREEN.get().defaultBlockState());
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layoutDaEtapa1LeIgualEEscreveSemChavesNovas(GameTestHelper helper) {
        CompoundTag style = new CompoundTag();
        style.putInt("color", 0xFF112233);
        CompoundTag piece = new CompoundTag();
        piece.putString("type", "shape");
        piece.put("style", style);
        ListTag list = new ListTag();
        list.add(piece);
        CompoundTag root = new CompoundTag();
        root.put("pieces", list);
        TvLayout read = TvLayout.CODEC.parse(NbtOps.INSTANCE, root).getOrThrow();
        helper.assertValueEqual(read.theme(), TvTheme.DEFAULT, "tema padrão");
        helper.assertValueEqual(read.background(), TvBackground.DEFAULT, "fundo padrão");
        helper.assertFalse(read.scanlines(), "sem linhas de varredura");
        TvPiece first = read.pieces().get(0);
        helper.assertFalse(first.hidden(), "visível");
        helper.assertTrue(first.rules().isEmpty(), "sem regras");
        helper.assertValueEqual(first.style().radius(), 0, "raio");
        helper.assertValueEqual(first.style().opacity(), 100, "opacidade");
        helper.assertValueEqual(first.style().font(), TvFont.MINECRAFT, "fonte");
        CompoundTag written = (CompoundTag) encode(read);
        helper.assertFalse(written.contains("theme") || written.contains("background") || written.contains("scanlines"),
                "o layout não escreve as chaves novas no padrão");
        CompoundTag writtenPiece = written.getList("pieces", Tag.TAG_COMPOUND).getCompound(0);
        helper.assertFalse(writtenPiece.contains("hidden") || writtenPiece.contains("rules"),
                "a peça não escreve as chaves novas no padrão");
        CompoundTag writtenStyle = writtenPiece.getCompound("style");
        helper.assertFalse(writtenStyle.contains("radius") || writtenStyle.contains("opacity")
                || writtenStyle.contains("font"), "o estilo não escreve as chaves novas no padrão");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void layoutCompletoFazIdaEVoltaPeloNbtEPeloUpdateTag(GameTestHelper helper) {
        TvBinding bind = new TvBinding(UUID.randomUUID(), ResourceType.FLUID, TvMetric.OUT);
        TvStyle style = TvStyle.DEFAULT.withColor(3).withBackground(2).withRadius(5).withOpacity(40)
                .withFont(TvFont.TERMINAL);
        TvPiece piece = plain(TvPieceType.VALUE, 0, 30, Optional.empty()).withStyle(style).withHidden(true)
                .withRules(List.of(rule(TvRuleOp.GT, 3.5, Optional.of(bind)), rule(TvRuleOp.EQ, 0, Optional.empty())));
        TvLayout layout = TvLayout.of(List.of(piece), new TvTheme("Meu", 1, 2, 3, 4, 5, 6),
                new TvBackground(TvBackground.Kind.GRADIENT, 0xFF000000, 0xFF112233), true);
        TvLayout back = TvLayout.CODEC.parse(NbtOps.INSTANCE, encode(layout)).getOrThrow();
        helper.assertValueEqual(back, layout, "ida e volta pelo NBT");
        var registries = helper.getLevel().registryAccess();
        TvBlockEntity tv = entity(helper);
        tv.setLayout(layout);
        TvBlockEntity fromSave = entity(helper);
        fromSave.loadWithComponents(tv.saveWithFullMetadata(registries), registries);
        helper.assertValueEqual(fromSave.layout(), layout, "ida e volta pelo save");
        TvBlockEntity fromUpdate = entity(helper);
        fromUpdate.handleUpdateTag(tv.getUpdateTag(registries), registries);
        helper.assertValueEqual(fromUpdate.layout(), layout, "ida e volta pelo update tag");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void regrasAlemDeOitoSaoCortadas(GameTestHelper helper) {
        List<TvRule> nine = new ArrayList<>();
        for (int n = 0; n < 9; n++) {
            nine.add(rule(TvRuleOp.LT, n, Optional.empty()));
        }
        TvPiece piece = plain(TvPieceType.VALUE, 0, 10, Optional.empty()).withRules(nine);
        helper.assertValueEqual(piece.rules().size(), 8, "o construtor corta");
        ListTag rules = new ListTag();
        for (TvRule rule : nine) {
            rules.add(TvCodecs.RULE.encodeStart(NbtOps.INSTANCE, rule).getOrThrow());
        }
        CompoundTag raw = new CompoundTag();
        raw.putString("type", "value");
        raw.put("rules", rules);
        helper.assertValueEqual(TvPiece.CODEC.parse(NbtOps.INSTANCE, raw).getOrThrow().rules().size(), 8,
                "o codec corta");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bindingsIncluemAsDasRegrasDepoisDasDasPecas(GameTestHelper helper) {
        TvBinding onPiece = new TvBinding(UUID.randomUUID(), ResourceType.ITEM, TvMetric.IN);
        TvBinding onRule = new TvBinding(UUID.randomUUID(), ResourceType.FLUID, TvMetric.OUT);
        TvPiece first = plain(TvPieceType.SHAPE, 0, 10, Optional.empty())
                .withRules(List.of(rule(TvRuleOp.LT, 1, Optional.of(onRule)),
                        rule(TvRuleOp.GT, 2, Optional.of(onPiece))));
        TvPiece second = plain(TvPieceType.VALUE, 10, 10, Optional.of(onPiece));
        TvLayout layout = TvLayout.of(List.of(first, second));
        helper.assertValueEqual(layout.bindings().size(), 2, "distintas");
        helper.assertValueEqual(layout.bindingIndex(onPiece), 0, "a da peça primeiro");
        helper.assertValueEqual(layout.bindingIndex(onRule), 1, "a da regra depois");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void regraComLigacaoPropriaLeOValorDela(GameTestHelper helper) {
        TvBinding binding = new TvBinding(UUID.randomUUID(), ResourceType.ITEM, TvMetric.IN);
        TvRule own = rule(TvRuleOp.GT, 100, Optional.empty());
        TvRule bound = rule(TvRuleOp.LT, 10, Optional.of(binding));
        TvRule hit = TvRules.first(List.of(own, bound), OptionalDouble.of(1),
                b -> b.equals(binding) ? OptionalDouble.of(2) : OptionalDouble.empty()).orElseThrow();
        helper.assertTrue(hit == bound, "vale a regra com a ligação própria");
        helper.assertTrue(TvRules.first(List.of(bound), OptionalDouble.of(1), b -> OptionalDouble.empty()).isEmpty(),
                "sem valor na ligação não bate");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void soOVazioDaEtapa1NaoDesenha(GameTestHelper helper) {
        helper.assertTrue(TvLayout.EMPTY.blank(), "o vazio da etapa 1 desenha");
        TvLayout read = TvLayout.CODEC.parse(NbtOps.INSTANCE, encode(TvLayout.EMPTY)).getOrThrow();
        helper.assertTrue(read.blank(), "o vazio lido do NBT desenha");
        helper.assertFalse(TvLayout.EMPTY.withTheme(TvTheme.AMBER).blank(), "só com tema não desenha");
        helper.assertFalse(TvLayout.EMPTY.withScanlines(true).blank(), "só com as linhas não desenha");
        helper.assertFalse(TvLayout.EMPTY.withBackground(new TvBackground(TvBackground.Kind.SOLID, 0xFF203040, 0))
                .blank(), "só com o fundo fixo não desenha");
        helper.assertFalse(TvLayout.of(List.of(plain(TvPieceType.SHAPE, 0, 4, Optional.empty()))).blank(),
                "com peça não desenha");
        helper.succeed();
    }
}
