package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvFont;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvSavedLayouts;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Os layouts salvos do jogador: escala para outra Tela, ida e volta pelo NBT, aplicar e a biblioteca. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvSavedLayoutsGameTests {
    private TvSavedLayoutsGameTests() {
    }

    private static TvPiece piece(int x, int y, int w, int h, int size) {
        return new TvPiece(TvPieceType.TEXT, x, y, w, h, 0, "", Optional.empty(), TvFormat.DEFAULT,
                new TvStyle(0xFFFFFFFF, 0, 0, 0, size, TvAlign.LEFT, true, "a", 2, 100, TvFont.MINECRAFT));
    }

    @GameTest(template = "empty")
    public static void escalaAsCaixasPorEixoEAFontePeloMenorFator(GameTestHelper helper) {
        TvPiece p = TvSavedLayouts.scale(List.of(piece(10, 4, 40, 8, 8)), 96, 64, 192, 96).get(0);
        if (p.x() != 20 || p.y() != 6 || p.width() != 80 || p.height() != 12 || p.style().size() != 12) {
            helper.fail("escala errada: " + p);
        }
        TvPiece tiny = TvSavedLayouts.scale(List.of(piece(0, 0, 1, 1, 1)), 96, 64, 32, 32).get(0);
        if (tiny.width() < 1 || tiny.height() < 1 || tiny.style().size() < 1) {
            helper.fail("encolheu abaixo de um pixel: " + tiny);
        }
        List<TvPiece> same = List.of(piece(10, 4, 40, 8, 8));
        if (!same.equals(TvSavedLayouts.scale(same, 96, 64, 96, 64))) {
            helper.fail("a mesma área mudou as peças");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void layoutSalvoFazIdaEVoltaEComponenteAntigoLeIgual(GameTestHelper helper) {
        TvLayout layout = TvLayout.of(List.of(piece(1, 2, 30, 8, 8)), TvTheme.DEFAULT,
                new TvBackground(TvBackground.Kind.SOLID, 0xFF112233, 0), true);
        TvComponent saved = TvSavedLayouts.of("Meu", layout, new TvArea(3, 2, 0, 0));
        Tag tag = TvComponent.CODEC.encodeStart(NbtOps.INSTANCE, saved).getOrThrow();
        TvComponent back = TvComponent.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        if (!back.equals(saved) || !back.isLayout() || back.layout().get().width() != 96
                || !back.layout().get().scanlines()) {
            helper.fail("o layout salvo não voltou igual: " + back);
        }
        TvComponent plain = new TvComponent("antigo", List.of(piece(1, 2, 3, 4, 8)));
        Tag plainTag = TvComponent.CODEC.encodeStart(NbtOps.INSTANCE, plain).getOrThrow();
        if (((CompoundTag) plainTag).contains("layout")
                || !TvComponent.CODEC.parse(NbtOps.INSTANCE, plainTag).getOrThrow().equals(plain)
                || plain.isLayout()) {
            helper.fail("o componente sem layout não leu igual");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aplicarTrocaPecasFundoELinhasMasMantemOTema(GameTestHelper helper) {
        TvLayout current = TvLayout.of(List.of(), TvTheme.AMBER, TvBackground.DEFAULT, false);
        TvLayout src = TvLayout.of(List.of(piece(0, 0, 96, 64, 8)), TvTheme.DEFAULT,
                new TvBackground(TvBackground.Kind.SOLID, 0xFF000000, 0), true);
        TvComponent saved = TvSavedLayouts.of("m", src, new TvArea(3, 2, 0, 0));
        TvLayout out = TvSavedLayouts.apply(saved, current, new TvArea(2, 2, 0, 0));
        if (!out.theme().equals(TvTheme.AMBER) || !out.scanlines() || out.pieces().get(0).width() != 64) {
            helper.fail("aplicar errado: " + out);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bibliotecaGuardaOLayoutSalvoESubstituiPeloNome(GameTestHelper helper) {
        TvLibrary library = new TvLibrary();
        UUID player = UUID.randomUUID();
        TvLayout layout = TvLayout.of(List.of(piece(1, 2, 30, 8, 8)));
        TvComponent saved = TvSavedLayouts.of("Painel", layout, new TvArea(3, 2, 0, 0));
        if (library.saveComponent(player, saved) != TvLibrary.Result.OK
                || library.saveComponent(player, saved) != TvLibrary.Result.OK) {
            helper.fail("não salvou");
        }
        List<TvComponent> all = library.components(player);
        if (all.size() != 1 || !all.get(0).isLayout()) {
            helper.fail("devia ter um só layout, substituído pelo nome: " + all);
        }
        TvLibrary reread = TvLibrary.load(library.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        if (reread.components(player).size() != 1 || !reread.components(player).get(0).isLayout()) {
            helper.fail("o layout salvo não sobreviveu à gravação");
        }
        helper.succeed();
    }
}
