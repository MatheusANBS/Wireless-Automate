package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GuideParserTest {
    private static final String HEAD = """
            ---
            group: ferramentas
            navigation:
              title: Wireless Router
              icon: wirelessautomate:router
              parent: index.md
              position: 2
            item_ids:
              - wirelessautomate:router
              - wirelessautomate:linker
            ---

            """;

    private static GuideDoc parse(String body) {
        return GuideParser.parse(HEAD + body);
    }

    private static GuideDoc ok(String body) {
        GuideDoc doc = parse(body);
        assertEquals(List.of(), doc.warnings());
        return doc;
    }

    @Test
    void cabecalho() {
        GuideDoc doc = ok("# Oi\n");
        GuideHeader h = doc.header();
        assertEquals("Wireless Router", h.title());
        assertEquals("wirelessautomate:router", h.icon());
        assertEquals("index.md", h.parent());
        assertEquals(2, h.position());
        assertEquals("ferramentas", doc.group());
        assertEquals(List.of("wirelessautomate:router", "wirelessautomate:linker"), h.itemIds());
    }

    @Test
    void cabecalhoSemPaiNemPosicao() {
        GuideDoc doc = GuideParser.parse("---\ngroup: comecar\nnavigation:\n  title: Início\n---\n\n# Início\n");
        assertEquals(List.of(), doc.warnings());
        assertEquals("", doc.header().parent());
        assertEquals(GuideHeader.NO_POSITION, doc.header().position());
        assertEquals(List.of(), doc.header().itemIds());
    }

    @Test
    void semCabecalhoAvisa() {
        GuideDoc doc = GuideParser.parse("# Só o texto\n");
        assertEquals(1, doc.warnings().size());
        assertEquals(GuideHeader.EMPTY, doc.header());
        assertEquals(1, doc.blocks().size());
    }

    @Test
    void titulosEParagrafo() {
        GuideDoc doc = ok("# Um\n\n## Dois\n\nlinha a\nlinha b\n\n### Três\n");
        assertEquals(4, doc.blocks().size());
        assertEquals(1, assertInstanceOf(Block.Heading.class, doc.blocks().get(0)).level());
        assertEquals(2, assertInstanceOf(Block.Heading.class, doc.blocks().get(1)).level());
        Block.Paragraph p = assertInstanceOf(Block.Paragraph.class, doc.blocks().get(2));
        assertEquals(List.of(new Span.Text("linha a linha b")), p.spans());
        assertEquals(3, assertInstanceOf(Block.Heading.class, doc.blocks().get(3)).level());
    }

    @Test
    void trechosEmLinha() {
        GuideDoc doc = ok("Texto **forte** e `cod` e [ver](outra.md) fim.\n");
        Block.Paragraph p = assertInstanceOf(Block.Paragraph.class, doc.blocks().get(0));
        assertEquals(List.of(new Span.Text("Texto "), new Span.Bold("forte"), new Span.Text(" e "),
                new Span.Code("cod"), new Span.Text(" e "), new Span.Link("outra.md", "ver"),
                new Span.Text(" fim.")), p.spans());
    }

    @Test
    void colchetesEAsteriscosSoltosSaoTexto() {
        GuideDoc doc = ok("Use **[** e **]** para [1] e 2 * 3.\n");
        Block.Paragraph p = assertInstanceOf(Block.Paragraph.class, doc.blocks().get(0));
        assertEquals(List.of(new Span.Text("Use "), new Span.Bold("["), new Span.Text(" e "), new Span.Bold("]"),
                new Span.Text(" para [1] e 2 * 3.")), p.spans());
    }

    @Test
    void listaComMarcadorEContinuacao() {
        GuideDoc doc = ok("- um\n- dois que\n  continua\n- três\n");
        Block.ListBlock l = assertInstanceOf(Block.ListBlock.class, doc.blocks().get(0));
        assertEquals(false, l.numbered());
        assertEquals(3, l.items().size());
        assertEquals(List.of(new Span.Text("dois que continua")), l.items().get(1));
    }

    @Test
    void listaNumerada() {
        GuideDoc doc = ok("1. a\n2. b\n10. c\n");
        Block.ListBlock l = assertInstanceOf(Block.ListBlock.class, doc.blocks().get(0));
        assertEquals(true, l.numbered());
        assertEquals(3, l.items().size());
    }

    @Test
    void tabelaComIconeEImagemEmLinha() {
        GuideDoc doc = ok("""
                | Item | Para quê |
                | --- | --- |
                | <ItemImage id="a:b" /> [Rota](r.md) | ![x](images/x.png) **Y** com `a\\|b` |
                """);
        Block.Table t = assertInstanceOf(Block.Table.class, doc.blocks().get(0));
        assertEquals(2, t.header().size());
        assertEquals(1, t.rows().size());
        List<Span> c0 = t.rows().get(0).get(0);
        assertEquals(new Span.ItemIcon("a:b", 1, ""), c0.get(0));
        assertEquals(new Span.Link("r.md", "Rota"), c0.get(2));
        List<Span> c1 = t.rows().get(0).get(1);
        assertEquals(new Span.InlineImage("images/x.png", "x"), c1.get(0));
        assertEquals(new Span.Bold("Y"), c1.get(2));
    }

    @Test
    void barraDentroDeCodigoNaoDivideACelula() {
        assertEquals(List.of(" `a|b` ", " c "), TableParser.split("| `a|b` | c |"));
    }

    @Test
    void itemImageSozinhoEQuebra() {
        GuideDoc doc = ok("<ItemImage id=\"a:b\" scale=\"2\" float=\"left\" components=\"minecraft:block_state={tier:'basic'}\" />\n"
                + "<br clear=\"all\" />\n");
        assertEquals(new Block.ItemImage("a:b", 2, Flow.LEFT, "minecraft:block_state={tier:'basic'}"),
                doc.blocks().get(0));
        assertEquals(new Block.Break(), doc.blocks().get(1));
    }

    @Test
    void blockImageEReceitas() {
        GuideDoc doc = ok("<BlockImage id=\"a:r\" p:facing=\"up\" p:tier=\"basic\" scale=\"3\" float=\"left\" />\n"
                + "<RecipeFor id=\"a:r\" />\n<Recipe id=\"a:guide\" />\n<SubPages icons={true} />\n");
        Block.BlockImage b = assertInstanceOf(Block.BlockImage.class, doc.blocks().get(0));
        assertEquals(Map.of("facing", "up", "tier", "basic"), b.props());
        assertEquals(3, b.scale());
        assertEquals(Flow.LEFT, b.flow());
        assertEquals(new Block.RecipeFor("a:r"), doc.blocks().get(1));
        assertEquals(new Block.Recipe("a:guide"), doc.blocks().get(2));
        assertEquals(new Block.SubPages(true), doc.blocks().get(3));
    }

    @Test
    void imagemSozinhaViraBlocoImage() {
        GuideDoc doc = ok("![alt](images/a.png)\n");
        assertEquals(new Block.Image("images/a.png"), doc.blocks().get(0));
    }

    @Test
    void row() {
        GuideDoc doc = ok("<Row gap=\"8\">\n  <ItemImage id=\"a:x\" scale=\"2\" />\n  <ItemImage id=\"a:y\" />\n</Row>\n");
        Block.Row r = assertInstanceOf(Block.Row.class, doc.blocks().get(0));
        assertEquals(8, r.gap());
        assertEquals(2, r.children().size());
        assertEquals(new Block.ItemImage("a:x", 2, Flow.NONE, ""), r.children().get(0));
    }

    @Test
    void cenaComAnotacoesECamera() {
        GuideDoc doc = ok("""
                <GameScene zoom="4" interactive={true}>
                  <Block id="minecraft:furnace" x="0" y="0" z="0" />
                  <Block id="a:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
                  <BlockAnnotation x="0" y="1" z="0" color="#4a8cff">
                    Router A · **Extract**
                    segunda linha
                  </BlockAnnotation>
                  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
                    Um tipo por vez
                  </LineAnnotation>
                  <IsometricCamera yaw="35" pitch="30" />
                </GameScene>
                depois
                """);
        Block.Scene s = assertInstanceOf(Block.Scene.class, doc.blocks().get(0));
        assertEquals(4, s.zoom());
        assertTrue(s.interactive());
        assertEquals(2, s.blocks().size());
        assertEquals(Map.of("facing", "up", "tier", "basic"), s.blocks().get(1).props());
        assertEquals(new SceneCamera(35, 30), s.camera());
        SceneNote.OnBlock bn = assertInstanceOf(SceneNote.OnBlock.class, s.notes().get(0));
        assertEquals("#4a8cff", bn.color());
        assertEquals(List.of(new Span.Text("Router A · "), new Span.Bold("Extract"),
                new Span.Text(" segunda linha")), bn.text());
        SceneNote.OnLine ln = assertInstanceOf(SceneNote.OnLine.class, s.notes().get(1));
        assertEquals(new SceneNote.Vec(4.5, 1.5, 0.5), ln.to());
        assertEquals(0.08, ln.thickness());
        assertInstanceOf(Block.Paragraph.class, doc.blocks().get(1));
    }

    @Test
    void itemLinkEmLinha() {
        GuideDoc doc = ok("| <ItemLink id=\"a:tablet\" />, Select | Todas |\n");
        Block.Table t = assertInstanceOf(Block.Table.class, doc.blocks().get(0));
        assertEquals(new Span.ItemLink("a:tablet"), t.header().get(0).get(0));
    }

    @Test
    void tagDesconhecidaViraAvisoEEPulada() {
        GuideDoc doc = parse("antes\n\n<Mistério x=\"1\" />\n\ntexto <Outra /> fim\n\ndepois\n");
        assertEquals(2, doc.warnings().size());
        assertTrue(doc.warnings().get(0).contains("Mistério") || doc.warnings().get(0).contains("Mist"));
        assertEquals(3, doc.blocks().size());
        Block.Paragraph meio = assertInstanceOf(Block.Paragraph.class, doc.blocks().get(1));
        assertEquals(List.of(new Span.Text("texto "), new Span.Text(" fim")), meio.spans());
    }

    @Test
    void tagDeCenaForaDeCenaAvisa() {
        GuideDoc doc = parse("<Block id=\"a:b\" x=\"0\" y=\"0\" z=\"0\" />\n\nok\n");
        assertEquals(1, doc.warnings().size());
        assertEquals(1, doc.blocks().size());
    }

    @Test
    void cenaSemFecharAvisa() {
        GuideDoc doc = parse("<GameScene zoom=\"1\">\n  <Block id=\"a:b\" x=\"0\" y=\"0\" z=\"0\" />\n");
        assertTrue(doc.warnings().stream().anyMatch(w -> w.contains("não fecha")));
        assertEquals(1, doc.blocks().size());
    }

    @Test
    void crlfEBom() {
        GuideDoc doc = GuideParser.parse("﻿" + HEAD.replace("\n", "\r\n") + "# T\r\n\r\ntexto\r\n");
        assertEquals(List.of(), doc.warnings());
        assertEquals("Wireless Router", doc.title());
        assertEquals(2, doc.blocks().size());
    }

    @Test
    void textoComMenorQueNaoEhTag() {
        GuideDoc doc = ok("se a < b então c\n");
        Block.Paragraph p = assertInstanceOf(Block.Paragraph.class, doc.blocks().get(0));
        assertEquals(List.of(new Span.Text("se a < b então c")), p.spans());
    }
}
