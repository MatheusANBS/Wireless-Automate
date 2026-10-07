# Progresso

Estado real do código em relação ao roadmap da [especificação](especificacao.md). Atualize este arquivo ao fim de cada sessão de trabalho: marque o que ficou pronto, mova o "Próximo passo" e acrescente uma linha no histórico.

Legenda: ✅ pronto e testado · 🟡 parcial · ⬜ não começado

## Resumo

O motor funciona: roteadores na mesma rede movem itens, fluidos e energia entre as máquinas, com prioridade, round-robin, redstone, vazão e alcance por tier, destinos dormindo e orçamento de tempo por tick. Redes e rede ativa existem e são salvas no mundo; o Vinculador (modo Único) e o Configurador (pincel) funcionam. Ainda não há telas: a configuração das faces é feita por `/wa face` ou pelo Configurador. Em tamanho, isso é perto de 45% do v1.

## Roadmap v1, motor e essencial

| Item | Estado | Onde está / o que falta |
| --- | --- | --- |
| Projeto NeoForge 1.21.1 | ✅ | `build.gradle`, `gradle.properties`, `scripts/setup.sh` e CI em `.github/workflows/build.yml` |
| Gerenciador central com orçamento de tempo | ✅ | `network/NetworkManager.java`: rotas montadas por rede e por tipo (`NetworkRoutes`), refeitas só quando a rede suja; laço com cursor salvo entre ticks; origens e destinos dormindo (`Backoff`, teto de 100 ticks) e acordados por `onNeighborChange`. Transferência em `ItemTransfer`, `FluidTransfer` e `EnergyTransfer` (energia num passe, `EnergySplit`). GameTests em `TransferGameTests`. |
| Roteador direcional | ✅ | `block/RouterBlock.java` e `RouterBlockEntity.java`: acessa a máquina por qualquer face, com `BlockCapabilityCache` por face e por tipo, refeitos se o bloco girar. |
| Configuração por face da máquina (itens, fluidos, energia) | ✅ | `network/FaceConfig.java` (modo, prioridade, redstone) por tipo e por `RelativeSide`, salva em relação ao `facing`. Sem tela ainda: use `/wa face <pos> <tipo> <face> <modo> [prioridade]`. O filtro entra no `FaceConfig`. |
| Redes, rede ativa, prioridade, round-robin, redstone | ✅ | `network/NetworkSavedData.java` (redes e rede ativa por jogador, no overworld), `/wa network list/create/use/remove`. Roteador colocado entra na rede ativa. Prioridade e round-robin em `RoundRobinOrder`; redstone por face e tipo em `RedstoneMode`. Faltam os grupos de redes (v1 só pede redes). |
| Filtros sem limite (inventário, JEI, tags, mod, estoque) e cartões | ⬜ | `item/FilterCardItem.java` é só um stub. |
| Tiers e núcleos de upgrade | 🟡 | `block/RouterTier.java`, `item/TierCoreItem.java` e `Config.java`. Vazão (`RateLimiter`, por face e tipo) e alcance/dimensão (pelo tier da origem) aplicados; recarregar a config remonta as rotas. **Falta** a receita dos núcleos (materiais em aberto). |
| Configurador como pincel e Vinculador modo Único | ✅ | `item/ConfiguratorItem.java`: Shift + clique copia, clique cola (`network/RouterPreset.java`, componente `wirelessautomate:preset`), relativo ao `facing`; a rede só é colada se o jogador puder usá-la. `item/LinkerItem.java`: clique no roteador põe na rede ativa. |
| Profiler embutido | ✅ | `/wa profile`: linha geral e uma linha por rede com nós, ms/tick, operações por segundo e origens/destinos acordados e dormindo. |
| Benchmark com Sophisticated Storage | ⬜ | — |
| Telas (GUI) | ⬜ | Nenhuma tela ainda; o clique direito no roteador não abre nada. |

## Roadmap v2, escala e integrações

Nada começado: químicos do Mekanism, Tablet, presets e código `WA1:`, Configurador e Vinculador por área, AE2 e RS2, upgrade de chunk loading, texturas finais e balanceamento das receitas. As dependências opcionais do `mekanism` e do `jei` já estão declaradas no `neoforge.mods.toml`.

## Próximo passo

**A tela do roteador e os filtros.** Hoje a configuração só é feita por comando ou pelo Configurador.

1. Sincronização sob demanda: o block entity manda ao cliente só o que a tela precisa, e só com ela aberta (pacotes `CustomPacketPayload`; nada com a tela fechada).
2. `RouterMenu` + `RouterScreen`: cabeçalho (nome, rede, tier), abas por tipo, as 6 faces da máquina com modo, prioridade e redstone (a especificação descreve a tela em "Telas da interface"; o visor 3D pode vir depois de uma versão com botões).
3. Ao abrir a tela pelo clique direito, o `RouterBlock.useItemOn` deve devolver `SKIP_DEFAULT_BLOCK_INTERACTION` quando a mão tiver o Vinculador ou o Configurador, senão o `useOn` deles deixa de rodar.
4. Filtros dentro do `FaceConfig` (exato, tag, mod; lista branca/negra; compilados em conjuntos de hash) e o Cartão de Filtro. O `RouterPreset` já leva o que o `FaceConfig.save()` salvar.
5. Benchmark com Sophisticated Storage (cenários "Muitos nós" e "Rede ociosa" da especificação).

Limites conhecidos do motor, para depois: um fluido por tanque por visita, inserção em inventário grande sem índice de slots com espaço, sem custo medido por vizinho, o round-robin recomeça a cada remontagem, e uma face Ambos em duas máquinas faz os itens irem e voltarem.

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
- A receita do roteador é ferro + redstone + olho de ender (`data/wirelessautomate/recipe/router.json`). Os núcleos ainda não têm receita.

## Decisões em aberto (da especificação)

- Canais dentro de uma rede ou só redes + filtros (sugestão: só redes no v1).
- Materiais das receitas dos tiers altos.
- Orçamento padrão de 0,5 ms/tick.

## Histórico

| Data | O que foi feito |
| --- | --- |
| 2026-10-07 | Esqueleto: projeto ModDevGradle, assets do pacote de design, roteador com tiers, itens registrados, gerenciador com orçamento, `/wa profile`, config, traduções, scripts de setup, CI, 6 testes JUnit e 3 GameTests passando. |
| 2026-10-07 | Motor v1 (feito com subagentes em paralelo sobre contratos): redes e rede ativa salvas, configuração por face relativa, caches de capability, laço de itens, fluidos e energia com prioridade, round-robin, redstone, vazão, alcance e destinos dormindo; `/wa network`, `/wa face`, profiler por rede, Vinculador modo Único e Configurador pincel. 45 testes JUnit e 22 GameTests passando. |
