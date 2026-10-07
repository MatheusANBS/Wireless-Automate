# Progresso

Estado real do código em relação ao roadmap da [especificação](especificacao.md). Atualize este arquivo ao fim de cada sessão de trabalho: marque o que ficou pronto, mova o "Próximo passo" e acrescente uma linha no histórico.

Legenda: ✅ pronto e testado · 🟡 parcial · ⬜ não começado

## Resumo

A fundação está pronta: o projeto compila, o bloco e os itens existem no jogo e os testes rodam (JUnit e GameTest). O transporte de recursos, que é o centro do mod, ainda não foi escrito. Em tamanho, isso é perto de 5% do v1.

## Roadmap v1, motor e essencial

| Item | Estado | Onde está / o que falta |
| --- | --- | --- |
| Projeto NeoForge 1.21.1 | ✅ | `build.gradle`, `gradle.properties`, `scripts/setup.sh` e CI em `.github/workflows/build.yml` |
| Gerenciador central com orçamento de tempo | 🟡 | `network/NetworkManager.java` e `network/TickBudget.java`. O orçamento adaptativo ao MSPT está pronto e testado. **Falta** o laço de rotas (o `TODO(v1)` em `NetworkManager.tick`), o cursor salvo entre ticks e os destinos dormindo. |
| Roteador direcional | 🟡 | `block/RouterBlock.java`, com `facing` igual à face da máquina, forma rotacionada e quebra sem a máquina. **Falta** acessar a máquina pelas capabilities, com `BlockCapabilityCache` por face. |
| Configuração por face da máquina (itens, fluidos, energia) | ⬜ | Só existem os enums `network/PortMode.java` e `network/ResourceType.java`. Falta guardar modo, filtro e redstone por face e por tipo no `RouterBlockEntity`. |
| Redes, rede ativa, prioridade, round-robin, redstone | ⬜ | `RouterBlockEntity` já guarda um `networkId` (UUID), mas ainda não existem as redes nem um `SavedData`. |
| Filtros sem limite (inventário, JEI, tags, mod, estoque) e cartões | ⬜ | `item/FilterCardItem.java` é só um stub. |
| Tiers e núcleos de upgrade | 🟡 | `block/RouterTier.java`, `item/TierCoreItem.java` e `Config.java`. Upgrade um tier por vez, e o tier se mantém ao quebrar (loot `copy_state`). **Falta** aplicar os limites de vazão e alcance e as receitas dos núcleos. |
| Configurador como pincel e Vinculador modo Único | ⬜ | `item/ConfiguratorItem.java` e `item/LinkerItem.java` são só stubs. |
| Profiler embutido | 🟡 | `/wa profile` em `command/WaCommand.java` mostra os nós e o tempo médio, o último e o teto. Falta o detalhamento por rede, as operações e os destinos dormindo. |
| Benchmark com Sophisticated Storage | ⬜ | — |
| Telas (GUI) | ⬜ | Nenhuma tela ainda; o clique direito no roteador não abre nada. |

## Roadmap v2, escala e integrações

Nada começado: químicos do Mekanism, Tablet, presets e código `WA1:`, Configurador e Vinculador por área, AE2 e RS2, upgrade de chunk loading, texturas finais e balanceamento das receitas. As dependências opcionais do `mekanism` e do `jei` já estão declaradas no `neoforge.mods.toml`.

## Próximo passo

**O coração do mod: transferir itens de um baú para outro numa rede.**

1. Criar um `NetworkSavedData` (no overworld) com as redes: id, nome, cor e dono. Pela especificação, um roteador recém-colocado entra na rede ativa do jogador.
2. Fazer o `RouterBlockEntity` guardar a configuração por face da máquina e por `ResourceType`: `PortMode`, prioridade e redstone. Tudo deve ser salvo em relação ao `facing` (a especificação pede isso para os presets).
3. Fazer o `RouterBlockEntity` manter um `BlockCapabilityCache<IItemHandler, Direction>` por face da máquina, criado em `onLoad`.
4. No `NetworkManager`, montar as rotas (origem que extrai → destino que insere) por rede, ordenadas por prioridade, e reconstruí-las só quando a rede muda.
5. Escrever o laço em `NetworkManager.tick`: mover lotes enquanto `budget.hasTime(...)`, com round-robin entre os destinos e respeitando a vazão do tier (`Config.TIERS`).
6. Escrever um GameTest com dois baús e dois roteadores na mesma rede, um com `EXTRACT` e outro com `INSERT`, verificando que os itens chegam.

Depois disso: fluidos e energia no mesmo motor, depois destinos dormindo e lotes, e por fim a GUI do roteador.

## Decisões tomadas no código

- Pacote `io.github.matheusanbs.wirelessautomate` e mod id `wirelessautomate`.
- Um único bloco `router` com as propriedades `facing` e `tier`, igual ao blockstate do pacote de design. O item carrega o tier no componente `block_state`.
- `canSurvive` exige apenas que o bloco de trás não seja ar, sem checar se é uma máquina com capability.
- Na config, `0` significa sem limite, e alcance `0` significa a dimensão inteira. Sair da dimensão depende de `crossDimension`, que fica ligado só no Ultimate.
- O orçamento cai de 100% (MSPT ≤ 40) a 25% (MSPT ≥ 50), de forma linear.
- A receita do roteador é ferro + redstone + olho de ender (`data/wirelessautomate/recipe/router.json`). Os núcleos ainda não têm receita.

## Decisões em aberto (da especificação)

- Canais dentro de uma rede ou só redes + filtros (sugestão: só redes no v1).
- Materiais das receitas dos tiers altos.
- Orçamento padrão de 0,5 ms/tick.

## Histórico

| Data | O que foi feito |
| --- | --- |
| 2026-10-07 | Esqueleto: projeto ModDevGradle, assets do pacote de design, roteador com tiers, itens registrados, gerenciador com orçamento, `/wa profile`, config, traduções, scripts de setup, CI, 6 testes JUnit e 3 GameTests passando. |
