# Upgrade direto de tier: plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** o Cartão de Upgrade de tier N sobe roteador ou armazenamento de qualquer tier abaixo direto para N (clique e bancada), com tooltip, JEI, guia e docs acompanhando.

**Spec:** [`docs/superpowers/specs/2026-10-09-upgrade-direto-design.md`](../specs/2026-10-09-upgrade-direto-design.md).

**Stack:** Java 21, NeoForge 21.1.251, JUnit 5, GameTests, JEI (só `compat/jei`), Python 3 (guia).

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- Lógica pura sem classes do Minecraft no `block/TierLadder.java`, com JUnit em `src/test/java/.../block/TierLadderTest.java`.
- Chaves existentes de NBT, componentes, config e tradução **não mudam de nome** (só o texto de algumas traduções).
- Nada de tipos do JEI fora de `compat/jei`. Classes de cliente só em `client/`.
- Allthemodium continua sem código: só `RouterTier.loaded()`/`TierLadder`.
- `ModPayloads.VERSION` não muda.
- GameTests: os do mesmo lote rodam em paralelo; teste pertinência, não contagem; template `"empty"`.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` (Git Bash, na raiz). Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, conserte em vez de esperar. Nunca rode dois jogos ao mesmo tempo. Não mexa em `run/options.txt`.
- Commits terminam com a linha `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `block/TierLadder.java`, `TierLadderTest.java` | `canUpgrade(requiredMods, from, to, modLoaded)` |
| `block/RouterTier.java` | `canUpgradeTo(RouterTier target)` |
| `block/RouterBlock.java`, `storage/StorageBlock.java`, `recipe/RouterUpgradeRecipe.java` | Usam `canUpgradeTo` no lugar de `next() == alvo` |
| `gametest/RouterGameTests.java`, `StorageGameTests.java`, `AtmGameTests.java`, `RecipeGameTests.java` | Asserções de "não pula tier" viram "pula"; casos de descer e reaplicar |
| `item/TierCoreItem.java`, `lang/*.json` | Tooltip só com o destino |
| `compat/jei/WirelessAutomateJeiPlugin.java` | Receitas de exibição de todas as origens |
| `scripts/guide/gerar_guia.py` + páginas geradas, `docs/especificacao.md`, `CLAUDE.md`, `docs/progresso.md` | Documentação |

---

### Task 1: Regra do pulo, no mundo e na bancada

**Files:** `block/TierLadder.java`, `src/test/java/io/github/matheusanbs/wirelessautomate/block/TierLadderTest.java`, `block/RouterTier.java`, `block/RouterBlock.java`, `storage/StorageBlock.java`, `recipe/RouterUpgradeRecipe.java`, `gametest/RouterGameTests.java`, `gametest/StorageGameTests.java`, `gametest/AtmGameTests.java`, `gametest/RecipeGameTests.java`.

- [ ] `TierLadder.canUpgrade(List<@Nullable String> requiredMods, int from, int to, Predicate<String> modLoaded)`: `to > from && loaded(requiredMods.get(to), modLoaded)`. Javadoc: o cartão de um tier já consome os anteriores na receita, então pular degraus não barateia nada.
- [ ] JUnit: sobe um e vários degraus; não desce; não reaplica; alvo de mod ausente recusa; origem de mod ausente (bloco que ficou no Vibranium) sobe para o Ultimate.
- [ ] `RouterTier.canUpgradeTo(RouterTier target)` delega ao `TierLadder` com `REQUIRED_MODS` e `RouterTier::modLoaded`.
- [ ] `RouterBlock.tryUpgrade` (linha ~269), `StorageBlock.tryUpgrade` (~187) e `RouterUpgradeRecipe.upgraded` (~50): troque `x.next() != target` por `!x.canUpgradeTo(target)`. Atualize os javadocs que dizem "tier seguinte".
- [ ] GameTests:
  - `RouterGameTests.tierCoreUpgradesOneTierAtATime` vira `tierCoreJumpsToAnyHigherTier`: Básico → Elite sobe; Elite → Avançado e Elite → Elite recusam; Elite → Ultimate sobe.
  - `StorageGameTests.capacityFollowsTierAndUpgrade`: a asserção "não pula tier" vira `assertTrue` de Avançado → Ultimate ("pula para o Ultimate"); o resto (mesmo block entity, conteúdo mantido) continua.
  - `AtmGameTests` (~linha 63): "pulou os tiers do ATM" vira `assertTrue` num **segundo** roteador Esmeralda (outra posição do template) subindo direto para o Ultimate; o laço degrau a degrau do primeiro continua.
  - `RecipeGameTests.routerUpgradeRecipe`: o laço passa a cobrir todo par (origem carregada, destino carregado acima com cartão), conferindo tier e nome mantido. A asserção "pulou tier" (Básico + Elite) vira positiva; acrescente as negativas "desceu" (roteador Elite + cartão Avançado) e "mesmo tier" (roteador Avançado + cartão Avançado).
- [ ] Verifique com o comando das Restrições globais e commite: "Upgrade direto: o cartão sobe de qualquer tier abaixo".

### Task 2: Tooltip e JEI

**Files:** `item/TierCoreItem.java`, `src/main/resources/assets/wirelessautomate/lang/en_us.json`, `pt_br.json`, `compat/jei/WirelessAutomateJeiPlugin.java`.

- [ ] `TierCoreItem.appendHoverText`: some o `from = tier.previous()`. A primeira linha é `upgrades` com um argumento (o nome do tier do cartão), em `GRAY`. Cada linha de valor vira `line(key, value)` com um argumento só (`value` em `AQUA`, linha em `DARK_AQUA`). O resto (Source só com o Ars, armazenamentos carregados, linha `use`, aviso `requires`) continua. Para o tier Básico não existe cartão, então nada muda nesse caso.
- [ ] Traduções, mantendo as chaves: `upgrades` = `Sobe para %s, de qualquer tier abaixo:` / `Raises to %s from any lower tier:`; em `items`, `fluids`, `energy`, `source`, `range`, `storage_chest`, `storage_tank`, `storage_battery`, `storage_chemical_tank`, `storage_source_tank`, tire o ` → %s` do fim (fica um `%s`).
- [ ] JEI `registerRecipes`: para cada destino com cartão (`ModItems.TIER_CORES`) e `loaded()`, e cada origem `loaded()` com `origem.canUpgradeTo(destino)`, uma receita sem forma de exibição para o roteador e para cada armazenamento `kind.loaded()`. Ids: `jei/router_upgrade_<origem>_<destino>` e `jei/<kind.id>_upgrade_<origem>_<destino>`. Atualize o javadoc do método e da classe ("núcleo do tier seguinte").
- [ ] Verifique e commite: "Upgrade direto: tooltip e receitas no JEI".

### Task 3: Guia e documentação

**Files:** `scripts/guide/gerar_guia.py` (e as páginas que ele gera), `docs/especificacao.md`, `CLAUDE.md`.

- [ ] Guia, página `upgrade-cards` (pt e en, ~linhas 620–720): "Cada cartão sobe **um** tier" vira "cada cartão leva o roteador ao tier dele, de qualquer tier abaixo (Básico + Cartão Esmeralda = roteador Esmeralda)", mantendo a ordem da escada. Tabela "Como usar": "o cartão do tier desejado". Troque "Não dá para pular tier..." por: o cartão não desce tier, e como cada cartão leva o anterior na receita, pular não economiza nada. Mesmas ideias no inglês. Procure outras menções a "tier seguinte"/"next tier"/"skip" no script.
- [ ] Rode `python scripts/guide/gerar_guia.py` e confira que só as páginas esperadas mudaram.
- [ ] `docs/especificacao.md`: linha da tabela de componentes dos Cartões de Upgrade (~22, "sem pular tiers"), a dos armazenamentos (~201), o parágrafo de "Receitas e progressão" (~284) e a linha "Upgrade do roteador na bancada" (~296): qualquer tier abaixo, com uma frase do motivo (custo = uma escada).
- [ ] `CLAUDE.md`: na linha do `RouterTier.java, TierLadder.java`, mencione `canUpgradeTo` (o cartão sobe de qualquer tier abaixo).
- [ ] Commite: "Upgrade direto: guia e especificação".
