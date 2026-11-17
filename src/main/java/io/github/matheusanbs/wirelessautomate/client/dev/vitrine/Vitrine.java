package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A vitrine dos GIFs do CurseForge: com {@code WA_VITRINE=<dir>} o jogo abre o mundo plano do e2e, põe o fim de tarde e
 * grava cada cena em {@code <dir>/<cena>/} ({@link Gravador}); {@code WA_VITRINE_ONLY=<cena>} grava só uma. O
 * {@code scripts/curseforge/gif.py} monta os GIFs. O comando completo, com o Complementary, é
 * {@code scripts/e2e.sh --vitrine}.
 */
public final class Vitrine {
    public static final String DIR = System.getenv("WA_VITRINE");
    private static final String SO = System.getenv("WA_VITRINE_ONLY");

    private Vitrine() {
    }

    /** Uma cena: o nome da pasta e quem monta os passos dela (recebe a clareira, a ordem entre as que rodam). */
    private record Cena(String nome, BiConsumer<List<Step>, Integer> passos) {
    }

    private static final List<Cena> CENAS = List.of(new Cena(CenaRoteador.NOME, CenaRoteador::passos),
            new Cena(CenaFiltro.NOME, CenaFiltro::passos), new Cena(CenaBau.NOME, CenaBau::passos),
            new Cena(CenaDoca.NOME, CenaDoca::passos),
            new Cena(CenaTela.NOME, CenaTela::passos), new Cena(CenaArmilar.NOME, CenaArmilar::passos));

    /** Os passos de todas as cenas (ou da escolhida), na ordem da lista. */
    public static List<Step> passos() {
        List<Step> lista = new ArrayList<>();
        lista.add(Palco.abrir());
        int clareira = 0;
        for (Cena cena : CENAS) {
            if (SO == null || SO.isBlank() || SO.equals(cena.nome())) {
                cena.passos().accept(lista, clareira++);
            }
        }
        return lista;
    }
}
