package io.github.matheusanbs.wirelessautomate.client.guide.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.client.guide.doc.Block;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.Flow;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.GuideDoc;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.GuideHeader;
import io.github.matheusanbs.wirelessautomate.client.guide.doc.Span;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuideLayoutTest {
    /** 6 unidades por caractere, 9 de altura de linha. */
    static final TextMeasure FAKE = new TextMeasure() {
        @Override
        public int width(String text, TextStyle style) {
            return 6 * text.length();
        }

        @Override
        public int lineHeight() {
            return 9;
        }
    };

    static GuideDoc doc(Block... blocks) {
        return new GuideDoc(GuideHeader.EMPTY, List.of(blocks), List.of());
    }

    static Block.Paragraph para(String text) {
        return new Block.Paragraph(List.of(new Span.Text(text)));
    }

    static List<SheetOp.Text> texts(Sheet s) {
        List<SheetOp.Text> out = new ArrayList<>();
        for (SheetOp op : s.ops()) {
            if (op instanceof SheetOp.Text t) {
                out.add(t);
            }
        }
        return out;
    }

    @Test
    void quebraDeLinhaPorPalavra() {
        // largura 60 = 10 caracteres
        List<Sheet> sheets = GuideLayout.paginate(doc(para("aaaa bbbb cccc dddd")), 60, 280, FAKE);
        assertEquals(1, sheets.size());
        List<SheetOp.Text> t = texts(sheets.get(0));
        assertEquals(List.of("aaaa bbbb", "cccc dddd"), t.stream().map(SheetOp.Text::text).toList());
        assertEquals(0, t.get(0).y());
        assertEquals(9, t.get(1).y());
    }

    @Test
    void palavraMaisLargaQueALinhaECortada() {
        List<Sheet> sheets = GuideLayout.paginate(doc(para("abcdefghijklmnopqrstuvwxyz")), 60, 280, FAKE);
        List<SheetOp.Text> t = texts(sheets.get(0));
        assertEquals(3, t.size());
        assertEquals("abcdefghij", t.get(0).text());
        assertEquals("uvwxyz", t.get(2).text());
    }

    @Test
    void estilosAdjacentesNaoGanhamEspacoNoMeio() {
        Block.Paragraph p = new Block.Paragraph(
                List.of(new Span.Text("a "), new Span.Bold("bb"), new Span.Text(", c")));
        List<SheetOp.Text> t = texts(GuideLayout.paginate(doc(p), 232, 280, FAKE).get(0));
        assertEquals(List.of("a", "bb", ", c"), t.stream().map(SheetOp.Text::text).toList());
        assertEquals(TextStyle.BOLD, t.get(1).style());
        assertEquals(6 + 6, t.get(1).x() - 0);
        assertEquals(24, t.get(2).x());
    }

    @Test
    void linkTemAreaDeClique() {
        Block.Paragraph p = new Block.Paragraph(List.of(new Span.Text("ver "), new Span.Link("a.md", "aqui")));
        Sheet s = GuideLayout.paginate(doc(p), 232, 280, FAKE).get(0);
        SheetOp.LinkArea area = s.ops().stream().filter(o -> o instanceof SheetOp.LinkArea)
                .map(o -> (SheetOp.LinkArea) o).findFirst().orElseThrow();
        assertEquals("a.md", area.page());
        assertEquals(24, area.x());
        assertEquals(24, area.w());
    }

    @Test
    void paragrafoPartidoEntreFolhas() {
        // folha de 27 de altura = 3 linhas; 7 linhas de 10 caracteres
        String word = "aaaaaaaaaa";
        String text = String.join(" ", java.util.Collections.nCopies(7, word));
        List<Sheet> sheets = GuideLayout.paginate(doc(para(text)), 60, 27, FAKE);
        assertEquals(3, sheets.size());
        assertEquals(3, texts(sheets.get(0)).size());
        assertEquals(3, texts(sheets.get(1)).size());
        assertEquals(1, texts(sheets.get(2)).size());
        assertEquals(0, texts(sheets.get(1)).get(0).y());
    }

    @Test
    void tabelaRepeteOCabecalhoNaFolhaSeguinte() {
        List<List<Span>> header = List.of(List.of(new Span.Text("Nome")), List.of(new Span.Text("Valor")));
        List<List<List<Span>>> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(List.of(List.of(new Span.Text("n" + i)), List.of(new Span.Text("v" + i))));
        }
        // cada linha: 9 + 4 + 1 = 14; folha de 60 cabe cabeçalho + 3 linhas
        List<Sheet> sheets = GuideLayout.paginate(doc(new Block.Table(header, rows)), 232, 60, FAKE);
        assertTrue(sheets.size() >= 3);
        for (Sheet s : sheets) {
            assertEquals("Nome", texts(s).get(0).text());
            assertEquals("Valor", texts(s).get(1).text());
        }
        long body = sheets.stream().flatMap(s -> texts(s).stream()).filter(t -> t.text().startsWith("n")
                && t.text().length() == 2).count();
        assertEquals(10, body);
    }

    @Test
    void tabelaLargaEncolheECabeNaFolha() {
        String longo = "palavra ".repeat(20).trim();
        List<List<Span>> header = List.of(List.of(new Span.Text("A")), List.of(new Span.Text("B")));
        List<List<List<Span>>> rows = List.of(List.of(List.of(new Span.Text(longo)), List.of(new Span.Text(longo))));
        List<Sheet> sheets = GuideLayout.paginate(doc(new Block.Table(header, rows)), 232, 280, FAKE);
        assertEquals(1, sheets.size());
        for (SheetOp op : sheets.get(0).ops()) {
            assertTrue(op.x() >= 0 && op.x() + op.w() <= 232, op.toString());
        }
        assertTrue(texts(sheets.get(0)).size() > 3, "a célula quebra em linhas");
    }

    @Test
    void colunaEstreitaNaoCortaPalavraQuandoAsPalavrasCabem() {
        String longo = "texto ".repeat(40).trim();
        List<List<Span>> header = List.of(List.of(new Span.Text("Modo")), List.of(new Span.Text("O que faz")));
        List<List<List<Span>>> rows = List.of(List.of(List.of(new Span.Text("Armazena")), List.of(new Span.Text(longo))));
        List<Sheet> sheets = GuideLayout.paginate(doc(new Block.Table(header, rows)), 232, 280, FAKE);
        List<String> t = texts(sheets.get(0)).stream().map(SheetOp.Text::text).toList();
        assertTrue(t.contains("Armazena"), t.toString());
        assertTrue(t.contains("Modo"), t.toString());
        for (SheetOp op : sheets.get(0).ops()) {
            assertTrue(op.x() >= 0 && op.x() + op.w() <= 232, op.toString());
        }
    }

    @Test
    void blocoAtomicoVaiInteiroParaAFolhaSeguinte() {
        // parágrafo de 9 + espaço de 4 + receita de 64 = 77; a folha de 70 não cabe os dois
        List<Sheet> sheets = GuideLayout.paginate(doc(para("oi"), new Block.RecipeFor("x:y")), 232, 70, FAKE);
        assertEquals(2, sheets.size());
        SheetOp.RecipeFrame f = assertInstanceOf(SheetOp.RecipeFrame.class, sheets.get(1).ops().get(0));
        assertEquals(0, f.y());
        assertEquals(GuideMetrics.RECIPE_H, f.h());
        assertEquals(1, sheets.get(0).ops().size());
    }

    @Test
    void linhaDeIconesNaoParte() {
        Block.Row row = new Block.Row(8, List.of(new Block.ItemImage("a:b", 2, Flow.NONE, ""),
                new Block.ItemImage("a:c", 2, Flow.NONE, "")));
        List<Sheet> sheets = GuideLayout.paginate(doc(para("oi"), row), 232, 40, FAKE);
        assertEquals(2, sheets.size());
        assertEquals(2, sheets.get(1).ops().size());
        assertEquals(32, sheets.get(1).ops().get(0).w());
    }

    @Test
    void tituloNaoFicaOrfaoNoPeDaFolha() {
        // folha de 45: parágrafo de 3 linhas (27) + espaço 4 = 31; título (9) + 2 + uma linha (9) = 20 não cabe
        Block.Heading h = new Block.Heading(2, List.of(new Span.Text("Titulo")));
        List<Sheet> sheets = GuideLayout.paginate(doc(para("aaaaaaaaaa aaaaaaaaaa aaaaaaaaaa"), h,
                para("corpo")), 60, 45, FAKE);
        assertEquals(2, sheets.size());
        SheetOp.Text first = texts(sheets.get(1)).get(0);
        assertEquals(TextStyle.TITLE2, first.style());
        assertEquals(0, first.y());
    }

    @Test
    void tituloUmTemEscalaEMedeComEla() {
        Block.Heading h = new Block.Heading(1, List.of(new Span.Text("abc")));
        SheetOp.Text t = texts(GuideLayout.paginate(doc(h), 232, 280, FAKE).get(0)).get(0);
        assertEquals(TextStyle.TITLE1, t.style());
        assertEquals(2.0, t.scale());
        assertEquals(36, t.w());
        assertEquals(18, t.h());
    }

    @Test
    void tituloUmTemOrnamentoEmbaixo() {
        Block.Heading h = new Block.Heading(1, List.of(new Span.Text("abc")));
        Sheet s = GuideLayout.paginate(doc(h, para("corpo")), 232, 280, FAKE).get(0);
        SheetOp.Ornament o = (SheetOp.Ornament) s.ops().stream().filter(op -> op instanceof SheetOp.Ornament)
                .findFirst().orElseThrow();
        assertEquals(18, o.y());
        assertEquals(232, o.w());
        SheetOp.Text corpo = texts(s).get(1);
        assertEquals(18 + GuideMetrics.ORNAMENT_H + GuideMetrics.HEADING_GAP, corpo.y());
    }

    @Test
    void iconeFlutuanteEstreitaOTextoAteOBreak() {
        Block.ItemImage img = new Block.ItemImage("a:b", 1, Flow.LEFT, "");
        // largura 100: com o ícone (16 + 4) o texto tem 80 (13 caracteres)
        List<Sheet> sheets = GuideLayout.paginate(doc(img, para("aaaaaaaaaaaa bbbbbbbbbbbb cccccccccccc"),
                new Block.Break(), para("dddddddddddd eeeeeeeeeeee")), 100, 280, FAKE);
        List<SheetOp.Text> t = texts(sheets.get(0));
        assertEquals(20, t.get(0).x());
        assertEquals("aaaaaaaaaaaa", t.get(0).text());
        assertEquals(20, t.get(1).x());
        assertEquals("cccccccccccc", t.get(2).text());
        // o ícone tem 16: o texto de depois do Break começa abaixo dele, na esquerda
        SheetOp.Text d = t.get(3);
        assertEquals(0, d.x());
        assertTrue(d.y() >= 16);
    }

    @Test
    void iconeEmLinhaAlinhaComOTexto() {
        Block.Paragraph p = new Block.Paragraph(List.of(new Span.ItemIcon("a:b", 1, ""), new Span.Text(" nome")));
        Sheet s = GuideLayout.paginate(doc(p), 232, 280, FAKE).get(0);
        SheetOp.Icon icon = assertInstanceOf(SheetOp.Icon.class, s.ops().get(0));
        assertEquals(16, icon.w());
        assertEquals(0, icon.y());
        SheetOp.Text t = assertInstanceOf(SheetOp.Text.class, s.ops().get(1));
        assertEquals(16 + 6, t.x());
        assertEquals((16 - 9) / 2, t.y());
    }

    @Test
    void listaTemMarcadorERecuo() {
        Block.ListBlock l = new Block.ListBlock(true, List.of(List.of(new Span.Text("um")),
                List.of(new Span.Text("dois"))));
        List<SheetOp.Text> t = texts(GuideLayout.paginate(doc(l), 232, 280, FAKE).get(0));
        assertEquals("1.", t.get(0).text());
        assertEquals("um", t.get(1).text());
        assertEquals(GuideMetrics.LIST_INDENT, t.get(1).x());
        assertEquals("2.", t.get(2).text());
    }

    @Test
    void marcadoresDaIndexNaoQuebram() {
        List<Sheet> sheets = GuideLayout.paginate(doc(new Block.SubPages(true), new Block.Recipe("a:b")), 232, 280,
                FAKE);
        assertEquals(1, sheets.size());
        assertInstanceOf(SheetOp.SubPagesMarker.class, sheets.get(0).ops().get(0));
        SheetOp.RecipeFrame r = assertInstanceOf(SheetOp.RecipeFrame.class, sheets.get(0).ops().get(1));
        assertEquals("a:b", r.recipeId());
    }

    @Test
    void paginaVaziaDaUmaFolhaVazia() {
        List<Sheet> sheets = GuideLayout.paginate(doc(), 232, 280, FAKE);
        assertEquals(1, sheets.size());
        assertFalse(sheets.get(0).ops().iterator().hasNext());
    }
}
