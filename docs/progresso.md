# Progresso

Estado real do código em relação ao roadmap da [especificação](especificacao.md). Atualize este arquivo ao fim de cada sessão de trabalho: marque o que ficou pronto, mova o "Próximo passo" e acrescente uma linha no histórico.

Legenda: ✅ pronto e testado · 🟡 parcial · ⬜ não começado

## Resumo

**Etapa atual (7/10/2026): v1 completo e boa parte do v2 feitos; mudando o desenvolvimento da nuvem para a máquina local do dono.** Tudo está commitado no branch `ccr-9c66f044-g29lli` (ainda sem PR para o `main`). Build com 83 testes JUnit e 92 GameTests passando, e o `./scripts/e2e.sh` OK nos 98 passos.

O motor move itens, fluidos e energia por redes, com prioridade, round-robin, redstone, vazão e alcance por tier, destinos dormindo e orçamento de tempo por tick, já otimizado pelo benchmark. Cada aba do roteador escolhe a sua rede. Há tela do roteador (visor 3D, faces, filtro, cartões, upgrade), filtros com tela e Cartão de Filtro, JEI opcional, receitas vanilla, upgrade de chunk loading, Tablet de rede (lista, mapa, estatísticas, redes, grupos com pausar), Vinculador com modo Área e Configurador com biblioteca (este **a simplificar**, ver "Feedback do dono"). Falta, do v1/v2: refazer o Configurador mais simples, medir o benchmark na máquina local, químicos do Mekanism, AE2/RS2, texturas finais.

## Roadmap v1, motor e essencial

| Item | Estado | Onde está / o que falta |
| --- | --- | --- |
| Projeto NeoForge 1.21.1 | ✅ | `build.gradle`, `gradle.properties`, `scripts/setup.sh` e CI em `.github/workflows/build.yml` |
| Gerenciador central com orçamento de tempo | ✅ | `network/NetworkManager.java`: rotas montadas por rede e por tipo (`NetworkRoutes`), refeitas só quando a rede suja; laço com cursor salvo entre ticks; origens e destinos dormindo (`Backoff`, teto de 100 ticks) e acordados por `onNeighborChange`. Transferência em `ItemTransfer`, `FluidTransfer` e `EnergyTransfer` (energia num passe, `EnergySplit`). GameTests em `TransferGameTests`. |
| Roteador direcional | ✅ | `block/RouterBlock.java` e `RouterBlockEntity.java`: acessa a máquina por qualquer face, com `BlockCapabilityCache` por face e por tipo, refeitos se o bloco girar. |
| Configuração por face da máquina (itens, fluidos, energia) | ✅ | `network/FaceConfig.java` (modo, prioridade, redstone) por tipo e por `RelativeSide`, salva em relação ao `facing`. Sem tela ainda: use `/wa face <pos> <tipo> <face> <modo> [prioridade]`. O filtro entra no `FaceConfig`. |
| Redes, rede ativa, prioridade, round-robin, redstone | ✅ | `network/NetworkSavedData.java` (redes e rede ativa por jogador, no overworld), `/wa network list/create/use/remove`. **Rede por aba:** o roteador guarda uma rede por tipo (`RouterBlockEntity.networkId(type)`), o motor monta cada tipo só com os nós cujo tipo está na rede. Roteador colocado entra na rede ativa em todas as abas. Prioridade e round-robin em `RoundRobinOrder`; redstone por face e tipo em `RedstoneMode`. Faltam os grupos de redes (v1 só pede redes). |
| Filtros sem limite (inventário, JEI, tags, mod, estoque) e cartões | ✅ | `filter/`: `Filter`/`FilterEntry` (imutáveis, com codecs; teto de 4.096), correspondência compilada em mapas de hash com cache por chave e invalidação na recarga de tags (`FilterTags`), estoque em `StockLimit`. Aplicados em `ItemTransfer`/`FluidTransfer` (recusa do filtro não faz o destino dormir). Tela `client/FilterScreen.java` + `menu/FilterMenu.java` (Shift + clique no inventário adiciona), Cartão de Filtro (`item/FilterCardItem.java`, importar/exportar pela tela). Slots de cartão por face e por tipo (2 cada) na tela do roteador, com a regra do conjunto em `filter/FilterSet.java`. JEI opcional (`compat/jei/`): arrastar e Shift + clique na lista do JEI acrescentam ao filtro. |
| Tiers e núcleos de upgrade | ✅ | `block/RouterTier.java`, `item/TierCoreItem.java` e `Config.java`. Vazão e alcance/dimensão aplicados. Receitas vanilla em `data/wirelessautomate/recipe/` (Avançado: ouro + diamante; Elite: netherita + estrela do Nether; Ultimate: ovo do dragão + blocos de netherita + fragmentos de eco). O núcleo Básico não tem uso nem receita (o roteador já nasce Básico). |
| Configurador como pincel e Vinculador modo Único | ✅ | `item/ConfiguratorItem.java`: Shift + clique copia, clique cola (`network/RouterPreset.java`, componente `wirelessautomate:preset`), relativo ao `facing`; a rede só é colada se o jogador puder usá-la. `item/LinkerItem.java`: clique no roteador põe na rede ativa, em todas as abas ou só no tipo escolhido (Shift + roda do mouse). O preset leva a rede de cada aba. |
| Profiler embutido | ✅ | `/wa profile`: linha geral e uma linha por rede com nós, ms/tick, operações por segundo e origens/destinos acordados e dormindo. |
| Benchmark com Sophisticated Storage | 🟡 | `bench/`, `/wa bench`, `scripts/bench.sh` (servidor dedicado com Sophisticated Storage e Spark, só no run `benchServer`), resultados em `docs/benchmark.md`. Com 500 nós ativos o mod fica no orçamento (o trabalho que sobra segue no tick seguinte); rede ociosa ~0,02 ms/tick. Correções aplicadas e medidas (remontagem com 500 nós de 7–8,6 ms para 0,5–1 ms, todas as origens movem com orçamento esgotado, Ultimate de 41 mil para 69 mil itens/s). **Falta** a rodada completa na máquina local (`docs/benchmark.md`, "A medir na máquina local"). |
| Telas (GUI) | ✅ | Roteador (300×240, com inventário, slots de cartão e o seletor de rede na linha das abas): `client/RouterScreen.java` com o visor 3D (`client/MachineView3D.java`: arrastar gira, clique escolhe a face por raio, roda dá zoom) e os botões por face abaixo; `menu/RouterMenu.java`/`RouterSnapshot.java` e `packet/`. O servidor manda o snapshot só quando algo muda e só com a tela aberta. Filtro: `client/FilterScreen.java`. Tablet: `client/TabletScreen.java`. Vinculador: `client/LinkerScreen.java`. Configurador: `client/ConfiguratorScreen.java` (a simplificar). Upgrade de chunk loading no cabeçalho da tela do roteador. |

## Roadmap v2, escala e integrações

Feitos: Tablet de rede (`network/NodeIndex.java`, `menu/Tablet*`, `client/TabletScreen.java`, tecla de atalho sem padrão), presets e código `WA1:` (`preset/`, a simplificar), Vinculador por área (`linker/`), Configurador por área, upgrade de chunk loading (`chunk/`, config `chunkLoading.enabled` e `maxChunksPerPlayer`). Não começados: químicos do Mekanism, AE2 e RS2, texturas finais e balanceamento das receitas. As dependências opcionais do `mekanism` e do `jei` já estão declaradas no `neoforge.mods.toml`.

## Feedback do dono para retomar (pendente)

- **Configurador está complexo demais (refazer).** Comentário do dono depois de ver a versão com biblioteca: "Sobre a varinha e os códigos importar etc. fica muito complexo para o usuário, não gostei. Poderia refazer?". A versão atual (commit `751eefa`: tela com abas Biblioteca e Área, presets nomeados por jogador, código `WA1:` com exportar/importar/colar, modos Pincel/Área, copiar e aplicar em área com mapa e "limitar a máquina") funciona e tem testes, mas precisa ser **simplificada**. Antes de codar, propor ao dono um desenho mais simples (ex.: só o pincel copiar/colar e um "colar em área" direto, sem biblioteca nem código de texto, ou com a biblioteca escondida) e confirmar. Código: `item/ConfiguratorItem.java`, `preset/`, `menu/Configurator*.java`, `client/Configurator*.java`.

## Próximo passo

**Continuar na máquina local** (mais memória e CPU; a nuvem não aguentava benchmark nem vários jogos juntos).

1. Preparar: `git checkout ccr-9c66f044-g29lli` e `./scripts/setup.sh --gametest`. No Windows sem WSL, o `e2e.sh` e o modo de captura dependem do `xvfb-run`; ali, rode o cliente normal com `WA_E2E=run/e2e` ou `WA_SCREENSHOT=run/shots` definidos (a janela aparece, mas o roteiro roda sozinho).
2. **Refazer o Configurador mais simples** (feedback do dono acima): propor o desenho e confirmar antes de codar.
3. **Medir o benchmark** na máquina local com o comando de `docs/benchmark.md` ("A medir na máquina local") e atualizar os números.
4. Teste manual num mundo com o JEI: arrastar item e balde do JEI para a grade do filtro (o e2e não cobre o JEI).
5. Depois: químicos do Mekanism, texturas finais, PR do branch para o `main`.

Pendências pequenas anotadas pelos agentes: a tela de filtro aberta de longe (pelo Tablet) fecha sozinha porque `RouterFaceFilterTarget.stillValid` exige 8 blocos; o callback que limpa tickets de chunk ao recarregar o mundo não tem teste automático; os limites de área do Configurador são constantes em `preset/AreaOps` (o Vinculador já usa a config); a vazão na tela conta só o que o roteador moveu como origem.

Para conferir telas sem monitor: `WA_SCREENSHOT=<dir> xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew runClient` desenha a tela do roteador com um snapshot de exemplo, salva PNGs e fecha o jogo (`client/DevScreenshot.java`; ponha `lang:pt_br` em `run/options.txt` para as capturas em português). O mesmo modo desenha a tela de filtro e o visor 3D (passos em `DevScreenshot.SEQUENCE`).

Teste de ponta a ponta: `./scripts/e2e.sh` (precisa de Xvfb; ~2 min, 98 passos). Os GameTests rodam em `run/gametest`, com o mundo apagado antes de cada rodada (um mundo velho reaproveitado fazia testes falharem). Benchmark: `./scripts/bench.sh <cenários>`.

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
- Orçamento padrão: 0,5 ms/tick (fica como está).
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
| 2026-10-07 | Esqueleto: projeto ModDevGradle, assets do pacote de design, roteador com tiers, itens registrados, gerenciador com orçamento, `/wa profile`, config, traduções, scripts de setup, CI, 6 testes JUnit e 3 GameTests passando. |
| 2026-10-07 | Itens e telas do v2 (subagentes) e passagem para a máquina local: upgrade de chunk loading, Tablet de rede, Vinculador modo Área, Configurador com biblioteca/código/área (a simplificar, pedido do dono), correções de performance medidas, GameTests num mundo novo a cada rodada. 83 JUnit, 92 GameTests, e2e com 98 passos OK. |
| 2026-10-07 | Fechamento do v1 (subagentes): receitas vanilla e duplicação de cartão, Armazém, slots de cartão por face, JEI opcional, teste de ponta a ponta num mundo real (achou e corrigiu dois bugs de foco), rede por aba com Vinculador por tipo, e o benchmark com Sophisticated Storage. 57 testes JUnit e 64 GameTests passando, e2e OK. |
| 2026-10-07 | Filtros e visor 3D (quatro subagentes sobre contratos): correspondência compilada com cache e estoque no transporte, persistência com registries, tela de filtro, Cartão de Filtro, botão Editar e o visor 3D com picking por raio. 53 testes JUnit e 41 GameTests passando. |
| 2026-10-07 | Tela do roteador (subagentes servidor e cliente sobre contratos): abrir pelo clique, snapshot só com a tela aberta, ações validadas, vazão atual por tipo, nome do nó, `RouterScreen` com cabeçalho, abas, faces, modos, prioridade e redstone, e modo de captura sob Xvfb. 49 testes JUnit e 27 GameTests passando. |
| 2026-10-07 | Motor v1 (feito com subagentes em paralelo sobre contratos): redes e rede ativa salvas, configuração por face relativa, caches de capability, laço de itens, fluidos e energia com prioridade, round-robin, redstone, vazão, alcance e destinos dormindo; `/wa network`, `/wa face`, profiler por rede, Vinculador modo Único e Configurador pincel. 45 testes JUnit e 22 GameTests passando. |
