package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eConfiguratorSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eLinkerSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2ePanelSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.*;

import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.neoforged.fml.ModList;

/**
 * O registro das suítes do e2e, na ordem do roteiro completo. Cada suíte roda sozinha, depois da abertura (e do que o
 * {@link Prereq} dela pede), e junto das outras nessa ordem.
 */
final class E2eSuites {
    /** O que a suíte pressupõe além da abertura. */
    enum Prereq {
        /** Basta a abertura (o roteador B aberto). */
        NENHUM,
        /** As redes de B e o fechar B, que a suíte {@code roteador} já faz dentro dela. */
        REDES,
        /** Como {@link #REDES}, mais os baús e a Bateria que a suíte {@code armazenamento} deixa no mundo. */
        ARMAZENAMENTO
    }

    /**
     * @param disponivel se a suíte existe nesta instalação (o Ars Nouveau e o GuideME são opcionais)
     * @param opcional fora do conjunto padrão; só roda pedida pelo nome ou no perfil completo
     */
    record Suite(String nome, String descricao, Prereq prereq, Consumer<List<Step>> passos, boolean opcional,
            BooleanSupplier disponivel) {
    }

    /** A suíte que já traz as redes de B e o fechar B no corpo dela. */
    static final String ROTEADOR = "roteador";

    /** A suíte que cria o estado do {@link Prereq#ARMAZENAMENTO}. */
    static final String ARMAZENAMENTO = "armazenamento";

    static final List<Suite> ALL = List.of(
            suite("filtro", "tela de filtro", Prereq.NENHUM, E2eFilterSteps::filterSteps),
            suite(ROTEADOR, "nome, redes por face, tamanhos, painel, cartões e chunk loading", Prereq.NENHUM,
                    list -> {
                        networkSteps(list);
                        layoutSteps(list);
                        panelSteps(list);
                        attachedFrameSteps(list);
                        cardSteps(list);
                        chunkUpgradeSteps(list);
                        list.add(close("fechar B"));
                    }),
            suite("alcance", "roteador de alcance", Prereq.REDES, E2eRangeSteps::rangeSteps),
            suite("tablet", "transporte por aba e o Tablet", Prereq.REDES, list -> {
                typeNetworkTransferSteps(list);
                tabletSteps(list);
            }),
            suite("vinculador", "Configurador e Vinculador", Prereq.REDES, list -> {
                configuratorSteps(list);
                linkerSteps(list);
            }),
            optional("portugues", "telas em português (a variante pt de todas)", Prereq.REDES, E2ePortugueseSteps::portugueseSteps),
            suite(ARMAZENAMENTO, "Baú de armazenamento", Prereq.REDES, E2eStorageSteps::storageChestSteps),
            new Suite("source", "Tanque de Source (com o Ars Nouveau)", Prereq.REDES, E2eSourceTankSteps::sourceTankSteps,
                    false, StorageKind.SOURCE_TANK::loaded),
            suite("receitas", "editor de receitas", Prereq.REDES, E2eRecipeSteps::recipeEditorSteps),
            optional("escuro", "tema escuro (usa os baús e a Bateria do armazenamento)", Prereq.ARMAZENAMENTO, E2eDarkSteps::darkSteps),
            suite("ressonador", "Ressonador de Sinal", Prereq.NENHUM, E2eResonatorSteps::resonatorSteps),
            new Suite("ressonador-desempenho", "tempo de quadro da câmara: desenho antigo × plasma (mede, não afirma)",
                    Prereq.NENHUM, list -> {
                        E2eResonatorSteps.perf = true;
                        try {
                            E2eResonatorSteps.resonatorSteps(list);
                        } finally {
                            E2eResonatorSteps.perf = false;
                        }
                    }, true, () -> true),
            suite("ressonador-jei", "JEI do Ressonador", Prereq.NENHUM, E2eJeiResonatorSteps::jeiResonatorSteps),
            suite("reparador", "Reparador de Cristais", Prereq.NENHUM, E2eRepairerSteps::repairerSteps),
            suite("reparador-animacao", "a animação do Reparador no mundo e na tela, com o tempo parado em cada fase",
                    Prereq.NENHUM, E2eRepairerAnimSteps::animationSteps),
            optional("reparador-desempenho", "tempo de quadro do Reparador (mede, não afirma)", Prereq.NENHUM,
                    E2eRepairerAnimSteps::perfSteps),
            suite("tela", "Tela de Sinal: tamanho, conteúdo e o desenho no mundo", Prereq.NENHUM, E2eTvSteps::tvSteps),
            optional("tela-desempenho", "tempo de quadro e de redesenho com 8 Telas 32 x 32 (mede, não afirma)",
                    Prereq.NENHUM, E2eTvPerfSteps::perfSteps),
            new Suite("guia", "páginas do guia (com o GuideME)", Prereq.NENHUM, E2eGuideSteps::guideSteps, false,
                    () -> ModList.get().isLoaded("guideme")));

    private E2eSuites() {
    }

    private static Suite optional(String nome, String descricao, Prereq prereq, Consumer<List<Step>> passos) {
        return new Suite(nome, descricao, prereq, passos, true, () -> true);
    }

    private static Suite suite(String nome, String descricao, Prereq prereq, Consumer<List<Step>> passos) {
        return new Suite(nome, descricao, prereq, passos, false, () -> true);
    }
}
