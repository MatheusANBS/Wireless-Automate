package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.dev.E2eSuites.Prereq;
import io.github.matheusanbs.wirelessautomate.client.dev.E2eSuites.Suite;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * O roteiro do e2e ({@code WA_E2E}): a abertura e as suítes escolhidas, na ordem do {@link E2eSuites}. Escolha por
 * {@code WA_E2E_ONLY=a,b,c} (vazio ou {@code todas}: as suítes padrão; {@code lista}: só mostra as suítes no log),
 * {@code WA_E2E_SKIP=a,b} tira suítes, e {@code WA_E2E_PROFILE=completo} inclui as opcionais. Os nomes dos passos e das
 * capturas são o contrato do {@code result.txt}.
 */
final class E2eScript {
    private static final String ONLY = System.getenv("WA_E2E_ONLY");
    private static final String SKIP = System.getenv("WA_E2E_SKIP");

    private E2eScript() {
    }

    static List<Step> script() {
        List<Suite> available = new ArrayList<>();
        for (Suite suite : E2eSuites.ALL) {
            if (suite.disponivel().getAsBoolean()) {
                available.add(suite);
            }
        }
        if (E2eSelection.listOnly(ONLY)) {
            for (Suite suite : available) {
                WirelessAutomate.LOGGER.info("E2E suíte {}: {} (pré-requisito {}, {})", suite.nome(), suite.descricao(),
                        suite.prereq(), suite.opcional() ? "opcional" : "padrão");
            }
            return new ArrayList<>();
        }
        List<String> names = new ArrayList<>();
        Set<String> optional = new HashSet<>();
        for (Suite suite : available) {
            names.add(suite.nome());
            if (suite.opcional()) {
                optional.add(suite.nome());
            }
        }
        List<String> chosen = E2eSelection.selectSuites(ONLY, SKIP, DevEndToEnd.PROFILE.optional(), names, optional);
        List<Step> list = new ArrayList<>();
        openingSteps(list);
        boolean needsNetworks = false;
        boolean needsStorage = false;
        for (Suite suite : available) {
            if (chosen.contains(suite.nome())) {
                needsNetworks |= suite.prereq() != Prereq.NENHUM;
                needsStorage |= suite.prereq() == Prereq.ARMAZENAMENTO;
            }
        }
        if (needsStorage && !chosen.contains(E2eSuites.ARMAZENAMENTO)) {
            chosen = new ArrayList<>(chosen);
            chosen.add(E2eSuites.ARMAZENAMENTO);
        }
        if (needsNetworks && !chosen.contains(E2eSuites.ROTEADOR)) {
            networkSteps(list);
            list.add(close("fechar B"));
        }
        for (Suite suite : available) {
            if (chosen.contains(suite.nome())) {
                list.add(suiteMarker(suite.nome()));
                suite.passos().accept(list);
            }
        }
        return list;
    }
}
