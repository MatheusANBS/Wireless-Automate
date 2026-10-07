# Progresso

Estado real do código em relação ao roadmap da [especificação](especificacao.md). Atualize este arquivo ao fim de cada sessão de trabalho: marque o que ficou pronto, mova o "Próximo passo" e acrescente uma linha no histórico.

Legenda: ✅ pronto e testado · 🟡 parcial · ⬜ não começado

## Resumo

**Etapa atual (7/10/2026): na máquina local do dono; Configurador refeito simples e benchmark medido.** O trabalho está no branch `configurador-simples` (a partir do `main`, que já tem o PR #2), ainda sem PR. Build com 77 testes JUnit e 88 GameTests passando, e o e2e OK nos 78 passos.

O motor move itens, fluidos e energia por redes, com prioridade, round-robin, redstone, vazão e alcance por tier, destinos dormindo e orçamento de tempo por tick, já otimizado pelo benchmark. Cada aba do roteador escolhe a sua rede. Há tela do roteador (visor 3D, faces, filtro, cartões, upgrade), filtros com tela e Cartão de Filtro, JEI opcional, receitas vanilla, upgrade de chunk loading, Tablet de rede (lista, mapa, estatísticas, redes, grupos com pausar), Vinculador com modo Área e Configurador sem tela (pincel e colar em área na mesma máquina). Falta, do v1/v2: o teste manual do JEI, químicos do Mekanism, AE2/RS2, texturas finais.

## Roadmap v1, motor e essencial

| Item | Estado | Onde está / o que falta |
| --- | --- | --- |
| Projeto NeoForge 1.21.1 | ✅ | `build.gradle`, `gradle.properties`, `scripts/setup.sh` e CI em `.github/workflows/build.yml` |
| Gerenciador central com orçamento de tempo | ✅ | `network/NetworkManager.java`: rotas montadas por rede e por tipo (`NetworkRoutes`), refeitas só quando a rede suja; laço com cursor salvo entre ticks; origens e destinos dormindo (`Backoff`, teto de 100 ticks) e acordados por `onNeighborChange`. Transferência em `ItemTransfer`, `FluidTransfer` e `EnergyTransfer` (energia num passe, `EnergySplit`). GameTests em `TransferGameTests`. |
| Roteador direcional | ✅ | `block/RouterBlock.java` e `RouterBlockEntity.java`: acessa a máquina por qualquer face, com `BlockCapabilityCache` por face e por tipo, refeitos se o bloco girar. |
| Configuração por face da máquina (itens, fluidos, energia) | ✅ | `network/FaceConfig.java` (modo, prioridade, redstone) por tipo e por `RelativeSide`, salva em relação ao `facing`. Sem tela ainda: use `/wa face <pos> <tipo> <face> <modo> [prioridade]`. O filtro entra no `FaceConfig`. |
| Redes, rede ativa, prioridade, round-robin, redstone | ✅ | `network/NetworkSavedData.java` (redes e rede ativa por jogador, no overworld), `/wa network list/create/use/remove`. **Rede por aba:** o roteador guarda uma rede por tipo (`RouterBlockEntity.networkId(type)`), o motor monta cada tipo só com os nós cujo tipo está na rede. Roteador colocado entra na rede ativa em todas as abas. Prioridade e round-robin em `RoundRobinOrder`; redstone por face e tipo em `RedstoneMode`. Faltam os grupos de redes (v1 só pede redes). |
| Filtros sem limite (inventário, JEI, tags, mod, estoque) e cartões | ✅ | `filter/`: `Filter`/`FilterEntry` (imutáveis, com codecs; teto de 4.096), correspondência compilada em mapas de hash com cache por chave e invalidação na recarga de tags (`FilterTags`), estoque em `StockLimit`. Aplicados em `ItemTransfer`/`FluidTransfer` (recusa do filtro não faz o destino dormir). Tela `client/FilterScreen.java` + `menu/FilterMenu.java` (Shift + clique no inventário adiciona), Cartão de Filtro (`item/FilterCardItem.java`, importar/exportar pela tela). Slots de cartão por face e por tipo (2 cada) na tela do roteador, com a regra do conjunto em `filter/FilterSet.java`. JEI opcional (`compat/jei/`): arrastar e Shift + clique na lista do JEI acrescentam ao filtro. |
| Tiers e núcleos de upgrade | ✅ | `block/RouterTier.java`, `item/TierCoreItem.java` e `Config.java`. Vazão e alcance/dimensão aplicados. Receitas vanilla em `data/wirelessautomate/recipe/` (Avançado: ouro + diamante; Elite: netherita + estrela do Nether; Ultimate: ovo do dragão + blocos de netherita + fragmentos de eco). Não há núcleo Básico (o roteador já nasce Básico). Upgrade também na bancada: roteador + núcleo do tier seguinte, sem forma (`recipe/RouterUpgradeRecipe`, mostrado no JEI); o clique do meio no criativo pega o roteador no tier do bloco. |
| Configurador como pincel e Vinculador modo Único | ✅ | `item/ConfiguratorItem.java`, sem tela: Shift + clique copia (o preset em `network/RouterPreset.java`, componente `wirelessautomate:preset`, e o bloco da máquina em `configurator_machine`), clique cola, relativo ao `facing`; a rede só é colada se o jogador puder usá-la (`preset/PresetApplier`). Modo Área (Shift + clique no ar): cliques em dois blocos marcam a área e clique no ar cola nos roteadores dela presos à mesma máquina; Shift + clique num bloco sem roteador limpa a varinha (cópia e área), nos dois modos (`preset/ConfiguratorArea`, com a área e os limites do Vinculador). `item/LinkerItem.java`: clique no roteador põe na rede ativa, em todas as abas ou só no tipo escolhido (Shift + roda do mouse). O preset leva a rede de cada aba. |
| Profiler embutido | ✅ | `/wa profile`: linha geral e uma linha por rede com nós, ms/tick, operações por segundo e origens/destinos acordados e dormindo. |
| Benchmark com Sophisticated Storage | ✅ | `bench/`, `/wa bench`, `scripts/bench.sh` (servidor dedicado com Sophisticated Storage e Spark, só no run `benchServer`), resultados em `docs/benchmark.md`. Com 500 nós ativos o mod fica no orçamento (o trabalho que sobra segue no tick seguinte); rede ociosa ~0,02 ms/tick. Correções aplicadas e medidas (remontagem com 500 nós de 7–8,6 ms para 0,5–1 ms, todas as origens movem com orçamento esgotado, Ultimate de 41 mil para 69 mil itens/s). Rodada completa na máquina local, antes e depois, com todos os critérios passando (`docs/benchmark.md`, "Máquina local: antes e depois"): remontagem de 3,3–4,0 ms para 0,29–0,56 ms, 68,7 mil itens/s na vazão bruta vanilla e 66,5 mil no Sophisticated. |
| Químicos do Mekanism | ✅ | Opcional e só pela API do Mekanism 10.7 (`compileOnly`): `compat/mekanism/MekanismChemicals` (capability `mekanism:chemical_handler` criada pelo mesmo nome), `network/ChemicalTransfer` (protocolo dos fluidos, vazão do limite de fluido do tier) e a ponte `network/Chemicals` (`LOADED`). Aba Químicos no roteador com modo, prioridade, redstone, rede por aba e filtro embutido: químico exato (`FilterEntry.ChemicalEntry`, pelo id) ou `@mod`, com estoque; entra por Shift + clique num tanque com químico, pelo JEI ou digitando o id. Sem cartões de filtro de químico e sem tags de químico. GameTests em `ChemicalGameTests` (run `gameTestServerChemicals`, com tanques de teste porque os do Mekanism vêm com as faces desligadas). |
| Texturas | ✅ | Geradas por `scripts/textures/gerar_texturas.py` (folha em `docs/preview/folha-de-sprites.png`): roteador nos 4 tiers, portas da tela e itens; os upgrades de tier viraram cartões (Cartão de Upgrade Avançado/Elite/Ultimate), na mesma família do Cartão de Filtro e do upgrade de chunk loading. |
| Telas (GUI) | ✅ | Roteador (300×240, com inventário, slots de cartão e o seletor de rede na linha das abas): `client/RouterScreen.java` com o visor 3D (`client/MachineView3D.java`: arrastar gira, clique escolhe a face por raio, roda dá zoom) e os botões por face abaixo; `menu/RouterMenu.java`/`RouterSnapshot.java` e `packet/`. O servidor manda o snapshot só quando algo muda e só com a tela aberta. Filtro: `client/FilterScreen.java`. Tablet: `client/TabletScreen.java`. Vinculador: `client/LinkerScreen.java`. O Configurador não tem tela; o contorno da área dele e do Vinculador fica em `client/AreaRenderer.java`. Upgrade de chunk loading no cabeçalho da tela do roteador. |

## Roadmap v2, escala e integrações

Feitos: Tablet de rede (`network/NodeIndex.java`, `menu/Tablet*`, `client/TabletScreen.java`, tecla de atalho sem padrão), Vinculador por área (`linker/`), Configurador colando em área na mesma máquina (a biblioteca e o código `WA1:` saíram a pedido do dono), upgrade de chunk loading (`chunk/`, config `chunkLoading.enabled` e `maxChunksPerPlayer`). Químicos do Mekanism feitos (ver tabela abaixo) e texturas polidas por script. Não começados: AE2 e RS2 e balanceamento das receitas. As dependências opcionais do `mekanism` e do `jei` já estão declaradas no `neoforge.mods.toml`.

## Feedback do dono (resolvido)

- **Configurador complexo demais (resolvido em 7/10/2026).** O dono achou a varinha com biblioteca, código `WA1:` e importar/exportar complexa demais. Desenho aprovado e feito: sem tela, uma cópia só no item, modos Pincel e Área como no Vinculador, e colar em área **só nos roteadores presos ao mesmo tipo de máquina** (automático, escolha do dono). Saíram a biblioteca, o código de texto, a cópia de área com âncora e a tela.

## Próximo passo

1. Testar no jogo os químicos com máquinas de verdade do Mekanism (lembrar de ligar as faces delas com a ferramenta de configuração do Mekanism) e ver as texturas novas.
2. Depois: balanceamento das receitas, atalhos de AE2/RS2, e o teste num ATM10 real com o jar de `build/libs/`.

**No Windows:** o `scripts/bench.sh` roda pelo Git Bash com `JAVA_HOME` apontando para o JDK 21 (o padrão da máquina é o 17). O `e2e.sh` e o modo de captura dependem do `xvfb-run`; sem ele, rode o cliente direto (`WA_E2E="$PWD/run/e2e" ./gradlew runClient`): a janela aparece e o roteiro roda sozinho. Num `run/` novo, a primeira execução para na tela de acessibilidade e o roteiro não começa; o `options.txt` fica com `onboardAccessibility:false` e a segunda execução passa.

Pendências pequenas anotadas pelos agentes: a tela de filtro aberta de longe (pelo Tablet) fecha sozinha porque `RouterFaceFilterTarget.stillValid` exige 8 blocos; o callback que limpa tickets de chunk ao recarregar o mundo não tem teste automático; a vazão na tela conta só o que o roteador moveu como origem.

Para conferir telas sem monitor: `WA_SCREENSHOT=<dir> xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew runClient` desenha a tela do roteador com um snapshot de exemplo, salva PNGs e fecha o jogo (`client/DevScreenshot.java`; ponha `lang:pt_br` em `run/options.txt` para as capturas em português). O mesmo modo desenha a tela de filtro e o visor 3D (passos em `DevScreenshot.SEQUENCE`).

Teste de ponta a ponta: `./scripts/e2e.sh` (precisa de Xvfb; ~2 min, 78 passos). Os GameTests rodam em `run/gametest`, com o mundo apagado antes de cada rodada (um mundo velho reaproveitado fazia testes falharem). Benchmark: `./scripts/bench.sh <cenários>`.

Limites conhecidos do motor, para depois: um fluido por tanque por visita, inserção em inventário grande sem índice de slots com espaço, sem custo medido por vizinho. A vazão na tela conta só o que o roteador moveu como origem (um roteador que só recebe mostra 0/s).

## Decisões tomadas no código

- Pacote `io.github.matheusanbs.wirelessautomate` e mod id `wirelessautomate`.
- Um único bloco `router` com as propriedades `facing` e `tier`, igual ao blockstate do pacote de design. O item carrega o tier no componente `block_state`.
- `canSurvive` exige apenas que o bloco de trás não seja ar, sem checar se é uma máquina com capability.
- Na config, `0` significa sem limite, e alcance `0` significa a dimensão inteira. Sair da dimensão depende de `crossDimension`, que fica ligado só no Ultimate.
- O orçamento cai de 100% (MSPT ≤ 40) a 25% (MSPT ≥ 50), de forma linear.
- Rede inexistente no `NetworkSavedData` (removida) = roteador sem rede, parado. Remover uma rede não varre o mundo.
- Alcance, dimensão e vazão valem pelo tier da **origem**. Entre dimensões (só com `crossDimension`) não há limite de distância.
- Nunca se entrega na mesma porta (máquina e face) de onde se extraiu. Itens: extrai só o que o destino aceitou na simulação; sobra que o destino recusar volta à origem e, em último caso, cai no mundo (com log). Energia: extrai antes de entregar, então nunca se cria energia.
- Lados relativos (`RelativeSide`): `FRONT` é a face onde o roteador está preso, `BACK` a oposta, `TOP` para onde apontam os LEDs, `LEFT`/`RIGHT` vistos de fora da face `FRONT`.
- `/wa network` é de jogador comum; `/wa face` e `/wa profile` pedem permissão 2. A rede padrão de um jogador leva o nome dele.
- Filtro sem entradas passa tudo, em qualquer modo. Estoque vale o da primeira entrada que casa, só em lista branca. Recusa por filtro ou estoque atingido pula o destino sem fazê-lo dormir; só falta de espaço faz dormir.
- O Cartão de Filtro guarda tipo (itens ou fluidos) e filtro; troca de tipo só vazio. Duplicar: exportar do roteador para uma pilha de cartões grava em todos (ainda sem receita).
- Persistência do `FaceConfig` usa `HolderLookup.Provider` (o filtro guarda `ItemStack`); entradas que não leem mais (mod removido) são descartadas com aviso (`FilterCodecs.LENIENT`).
- Tela do roteador: o servidor valida tudo (mesmo `containerId`, até 8 blocos, prioridade −999..999, rede só do dono ou de op). A tela não muda nada sozinha: espera o snapshot do servidor. Com Vinculador, Configurador ou núcleo de tier na mão, o clique vai para o item, não para a tela.
- A receita do roteador é ferro + redstone + olho de ender (`data/wirelessautomate/recipe/router.json`). Os núcleos ainda não têm receita.

## Decisões do dono (7 de outubro de 2026)

Respondidas e registradas também na especificação ("Decisões tomadas"):

- Canais: só redes + filtros no v1.
- Receitas dos núcleos: só vanilla (Avançado: ouro + diamante; Elite: netherita + estrela do Nether; Ultimate: ovo do dragão).
- Orçamento padrão: 1 ms/tick (era 0,5 ms; trocado pelo dono depois do benchmark, por mais vazão).
- Cartões: slots por face e por tipo; passa se o filtro embutido ou algum cartão aceitar.
- Duplicar cartão: receita cartão configurado + cartão vazio = dois iguais.
- JEI: integração opcional agora (compileOnly + plugin).
- Modo Ambos (agora Armazém): face Armazém não entrega para outra face Armazém.
- Visor 3D: fica como está (nome da face no canto, sem a dica flutuante nem a linha da máquina).
- Redes (depois de testar): **cada aba do roteador escolhe a sua rede**; o Vinculador tem seletor de tipo; grupos ficam só para organizar no Tablet. Substitui a ideia de vincular redes.
- "Ambos" passa a se chamar **Armazém** na tela (recebe de quem extrai, entrega para quem insere, não troca com outro armazém).

## Histórico

| Data | O que foi feito |
| --- | --- |
| 2026-10-07 | Químicos do Mekanism (motor, filtro, telas, JEI; GameTests numa run com o Mekanism), Mekanism no `runClient`, texturas polidas por script (subagente) com os upgrades virando cartões, e o build passando a falhar se o servidor de GameTests não chega ao fim (antes, um mod que não carregava passava). 89 + 3 GameTests. |
| 2026-10-07 | Orçamento padrão do mod de 0,5 para 1 ms/tick (pedido do dono, por mais vazão; o benchmark de `docs/benchmark.md` foi medido com 0,5 ms). |
| 2026-10-07 | Configurador mais fácil: Shift + clique num bloco sem roteador limpa a varinha toda (cópia e área) nos dois modos, e o tooltip mostra o estado e só os comandos do modo atual. |
| 2026-10-07 | Upgrade do roteador na bancada (roteador + núcleo do tier seguinte, também no JEI), sem o item núcleo Básico, clique do meio no criativo pegando o tier certo e Sophisticated Storage carregado no `runClient` junto com o JEI. 89 GameTests. |
| 2026-10-07 | Configurador: Shift + clique num bloco sem roteador, no modo Área, limpa a área marcada (o dono não achou como limpar). |
| 2026-10-07 | Máquina local: Configurador refeito simples (sem tela, pincel e colar em área na mesma máquina; saíram biblioteca, código `WA1:` e cópia de área) e benchmark completo antes e depois, com todos os critérios passando. 77 JUnit, 88 GameTests, e2e com 78 passos OK. |
| 2026-10-07 | Esqueleto: projeto ModDevGradle, assets do pacote de design, roteador com tiers, itens registrados, gerenciador com orçamento, `/wa profile`, config, traduções, scripts de setup, CI, 6 testes JUnit e 3 GameTests passando. |
| 2026-10-07 | Itens e telas do v2 (subagentes) e passagem para a máquina local: upgrade de chunk loading, Tablet de rede, Vinculador modo Área, Configurador com biblioteca/código/área (a simplificar, pedido do dono), correções de performance medidas, GameTests num mundo novo a cada rodada. 83 JUnit, 92 GameTests, e2e com 98 passos OK. |
| 2026-10-07 | Fechamento do v1 (subagentes): receitas vanilla e duplicação de cartão, Armazém, slots de cartão por face, JEI opcional, teste de ponta a ponta num mundo real (achou e corrigiu dois bugs de foco), rede por aba com Vinculador por tipo, e o benchmark com Sophisticated Storage. 57 testes JUnit e 64 GameTests passando, e2e OK. |
| 2026-10-07 | Filtros e visor 3D (quatro subagentes sobre contratos): correspondência compilada com cache e estoque no transporte, persistência com registries, tela de filtro, Cartão de Filtro, botão Editar e o visor 3D com picking por raio. 53 testes JUnit e 41 GameTests passando. |
| 2026-10-07 | Tela do roteador (subagentes servidor e cliente sobre contratos): abrir pelo clique, snapshot só com a tela aberta, ações validadas, vazão atual por tipo, nome do nó, `RouterScreen` com cabeçalho, abas, faces, modos, prioridade e redstone, e modo de captura sob Xvfb. 49 testes JUnit e 27 GameTests passando. |
| 2026-10-07 | Motor v1 (feito com subagentes em paralelo sobre contratos): redes e rede ativa salvas, configuração por face relativa, caches de capability, laço de itens, fluidos e energia com prioridade, round-robin, redstone, vazão, alcance e destinos dormindo; `/wa network`, `/wa face`, profiler por rede, Vinculador modo Único e Configurador pincel. 45 testes JUnit e 22 GameTests passando. |
