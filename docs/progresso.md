# Progresso

Estado real do código em relação ao roadmap da [especificação](especificacao.md). Atualize este arquivo ao fim de cada sessão de trabalho: marque o que ficou pronto, mova o "Próximo passo" e acrescente uma linha no histórico.

Legenda: ✅ pronto e testado · 🟡 parcial · ⬜ não começado

## Resumo

O motor funciona: roteadores na mesma rede movem itens, fluidos e energia entre as máquinas, com prioridade, round-robin, redstone, vazão e alcance por tier, destinos dormindo e orçamento de tempo por tick. Redes e rede ativa existem e são salvas no mundo; o Vinculador (modo Único) e o Configurador (pincel) funcionam. O clique direito no roteador abre a tela dele, com nome, rede, tier, vazão atual, um visor 3D da máquina com as faces clicáveis e a configuração por face e por tipo (modo, prioridade, redstone e filtro). Os filtros funcionam no transporte (exato, tag, mod, lista branca/negra, componentes, estoque) e têm tela própria e Cartão de Filtro. Falta o benchmark e um teste de ponta a ponta num mundo real. Em tamanho, isso é perto de 80% do v1.

## Roadmap v1, motor e essencial

| Item | Estado | Onde está / o que falta |
| --- | --- | --- |
| Projeto NeoForge 1.21.1 | ✅ | `build.gradle`, `gradle.properties`, `scripts/setup.sh` e CI em `.github/workflows/build.yml` |
| Gerenciador central com orçamento de tempo | ✅ | `network/NetworkManager.java`: rotas montadas por rede e por tipo (`NetworkRoutes`), refeitas só quando a rede suja; laço com cursor salvo entre ticks; origens e destinos dormindo (`Backoff`, teto de 100 ticks) e acordados por `onNeighborChange`. Transferência em `ItemTransfer`, `FluidTransfer` e `EnergyTransfer` (energia num passe, `EnergySplit`). GameTests em `TransferGameTests`. |
| Roteador direcional | ✅ | `block/RouterBlock.java` e `RouterBlockEntity.java`: acessa a máquina por qualquer face, com `BlockCapabilityCache` por face e por tipo, refeitos se o bloco girar. |
| Configuração por face da máquina (itens, fluidos, energia) | ✅ | `network/FaceConfig.java` (modo, prioridade, redstone) por tipo e por `RelativeSide`, salva em relação ao `facing`. Sem tela ainda: use `/wa face <pos> <tipo> <face> <modo> [prioridade]`. O filtro entra no `FaceConfig`. |
| Redes, rede ativa, prioridade, round-robin, redstone | ✅ | `network/NetworkSavedData.java` (redes e rede ativa por jogador, no overworld), `/wa network list/create/use/remove`. Roteador colocado entra na rede ativa. Prioridade e round-robin em `RoundRobinOrder`; redstone por face e tipo em `RedstoneMode`. Faltam os grupos de redes (v1 só pede redes). |
| Filtros sem limite (inventário, JEI, tags, mod, estoque) e cartões | 🟡 | `filter/`: `Filter`/`FilterEntry` (imutáveis, com codecs; teto de 4.096), correspondência compilada em mapas de hash com cache por chave e invalidação na recarga de tags (`FilterTags`), estoque em `StockLimit`. Aplicados em `ItemTransfer`/`FluidTransfer` (recusa do filtro não faz o destino dormir). Tela `client/FilterScreen.java` + `menu/FilterMenu.java` (Shift + clique no inventário adiciona), Cartão de Filtro (`item/FilterCardItem.java`, importar/exportar pela tela). **Faltam** a aba JEI (o JEI não está nas dependências) e os slots de cartões no rodapé do roteador. |
| Tiers e núcleos de upgrade | 🟡 | `block/RouterTier.java`, `item/TierCoreItem.java` e `Config.java`. Vazão (`RateLimiter`, por face e tipo) e alcance/dimensão (pelo tier da origem) aplicados; recarregar a config remonta as rotas. **Falta** a receita dos núcleos (materiais em aberto). |
| Configurador como pincel e Vinculador modo Único | ✅ | `item/ConfiguratorItem.java`: Shift + clique copia, clique cola (`network/RouterPreset.java`, componente `wirelessautomate:preset`), relativo ao `facing`; a rede só é colada se o jogador puder usá-la. `item/LinkerItem.java`: clique no roteador põe na rede ativa. |
| Profiler embutido | ✅ | `/wa profile`: linha geral e uma linha por rede com nós, ms/tick, operações por segundo e origens/destinos acordados e dormindo. |
| Benchmark com Sophisticated Storage | ⬜ | — |
| Telas (GUI) | 🟡 | Roteador: `client/RouterScreen.java` com o visor 3D (`client/MachineView3D.java`: arrastar gira, clique escolhe a face por raio, roda dá zoom) e os botões por face abaixo; `menu/RouterMenu.java`/`RouterSnapshot.java` e `packet/`. O servidor manda o snapshot só quando algo muda e só com a tela aberta. Filtro: `client/FilterScreen.java`. **Faltam** o rodapé de cartões/upgrades e as telas do Tablet, do Vinculador e do Configurador (v2/área). |

## Roadmap v2, escala e integrações

Nada começado: químicos do Mekanism, Tablet, presets e código `WA1:`, Configurador e Vinculador por área, AE2 e RS2, upgrade de chunk loading, texturas finais e balanceamento das receitas. As dependências opcionais do `mekanism` e do `jei` já estão declaradas no `neoforge.mods.toml`.

## Próximo passo

**Fechar o v1: teste de ponta a ponta, benchmark e receitas.**

1. Teste de ponta a ponta num mundo real: um cliente sob Xvfb que cria um mundo (ou entra num servidor dedicado de teste), coloca baús e roteadores, abre a tela, clica e confere que o servidor aplicou e a tela atualizou. Até hoje as telas só foram vistas com dados locais, e os handlers só por GameTest.
2. Benchmark com Sophisticated Storage (cenários "Muitos nós" e "Rede ociosa" da especificação), com `/wa profile` e Spark.
3. Receitas só vanilla dos núcleos de tier (Avançado: ouro + diamante; Elite: netherita + estrela do Nether; Ultimate: ovo do dragão) e do Cartão de Filtro, mais a receita de duplicar cartão (configurado + vazio = dois iguais).
4. Slots de Cartão de Filtro por face e por tipo, na tela do roteador; o recurso passa se o filtro embutido ou algum cartão aceitar. O upgrade de chunk loading fica no rodapé.
5. Integração opcional com o JEI: dependência só de compilação, aba JEI no filtro e arrastar ingredientes fantasmas.
6. Modo Ambos: uma face Ambos não entrega para outra face Ambos (acaba o vai e volta).

Para conferir telas sem monitor: `WA_SCREENSHOT=<dir> xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew runClient` desenha a tela do roteador com um snapshot de exemplo, salva PNGs e fecha o jogo (`client/DevScreenshot.java`; ponha `lang:pt_br` em `run/options.txt` para as capturas em português). O mesmo modo desenha a tela de filtro e o visor 3D (passos em `DevScreenshot.SEQUENCE`).

Limites conhecidos do motor, para depois: um fluido por tanque por visita, inserção em inventário grande sem índice de slots com espaço, sem custo medido por vizinho, o round-robin recomeça a cada remontagem, e uma face Ambos em duas máquinas faz os itens irem e voltarem (decidido: corrigir, item 6 acima).

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
- Modo Ambos: face Ambos não entrega para outra face Ambos.
- Visor 3D: fica como está (nome da face no canto, sem a dica flutuante nem a linha da máquina).

## Histórico

| Data | O que foi feito |
| --- | --- |
| 2026-10-07 | Esqueleto: projeto ModDevGradle, assets do pacote de design, roteador com tiers, itens registrados, gerenciador com orçamento, `/wa profile`, config, traduções, scripts de setup, CI, 6 testes JUnit e 3 GameTests passando. |
| 2026-10-07 | Filtros e visor 3D (quatro subagentes sobre contratos): correspondência compilada com cache e estoque no transporte, persistência com registries, tela de filtro, Cartão de Filtro, botão Editar e o visor 3D com picking por raio. 53 testes JUnit e 41 GameTests passando. |
| 2026-10-07 | Tela do roteador (subagentes servidor e cliente sobre contratos): abrir pelo clique, snapshot só com a tela aberta, ações validadas, vazão atual por tipo, nome do nó, `RouterScreen` com cabeçalho, abas, faces, modos, prioridade e redstone, e modo de captura sob Xvfb. 49 testes JUnit e 27 GameTests passando. |
| 2026-10-07 | Motor v1 (feito com subagentes em paralelo sobre contratos): redes e rede ativa salvas, configuração por face relativa, caches de capability, laço de itens, fluidos e energia com prioridade, round-robin, redstone, vazão, alcance e destinos dormindo; `/wa network`, `/wa face`, profiler por rede, Vinculador modo Único e Configurador pincel. 45 testes JUnit e 22 GameTests passando. |
