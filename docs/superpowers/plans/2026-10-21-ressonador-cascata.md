# Ressonador: etapa 1b (vida por tier, Pó Carregado, cascata e Coração) · plano

> Executar tarefa por tarefa, um subagente por tarefa (`wa-feature`). **Depende da etapa 1** (`2026-10-20-ressonador-rebalanceamento-etapa-1.md`, já com os valores revisados): `CoreCatalyst`, `crystal_integrity`, `catalyst` no NBT, Afinados.

**Spec:** `docs/superpowers/specs/2026-10-21-ressonador-cascata-design.md`, seções 2 a 5.

## Restrições globais

As mesmas da etapa 1 (idioma, os dois `lang`, chaves existentes não mudam, performance, JUnit e GameTest, texturas pelo gerador, `wa-modular`, `wa-comentarios`, commits **sem** linhas de atribuição, sem push, verificação com as quatro runs). Em especial:
- Allthemodium opcional e sem código: receitas com `neoforge:conditions` e tags `c:`; itens sempre registrados, visíveis só com o mod (`RouterTier.loaded()`).
- Nova config: `resonator.crystalLifeMinutes` (lista de 8 inteiros, padrão `20,20,15,10,5,5,4,3`, faixa 1..240); entra no `migrateBalance3` como padrão novo (chave nova, sem migração de valor).
- `ModPayloads.VERSION` continua `"25"`.
- Texto das receitas em `data/wirelessautomate/recipe/` (singular); nomes e dicas nos dois `lang`.

### Task 1: Vida do cristal por tier
- Hoje o desgaste é proporcional ao gerado, com `crystalLifeTicks` / `cutCrystalLifeTicks` (veja `CrystalCharge`, `ResonatorBatch`). Faça a vida a 100% de geração depender do **tier do Núcleo** pela lista `crystalLifeMinutes` (1 min = 1.200 ticks): o desgaste por FE gerado fica multiplicado por `vidaBase(tipo) / vidaDoTier`, em que a base do Básico e do Avançado é a de hoje (20 min), para os mundos antigos não mudarem. Conta pura em `CoreMath.wearScale(coreTier)` (ou `CrystalCharge`) com JUnit; nada novo por tick.
- Config: getter `Config.crystalLifeTicks(int tierOrdinal)`; atualize a tela/JEI/dicas que citam a vida só se citarem número (procure `crystalLife`).
- GameTests (lote próprio, config em `try/finally`): o Núcleo Ultimate gasta a carga de um Afinado em 3 min (3.600 ticks a 100%), o Básico em 20 min; a recarga e a integridade continuam.
- Commit: `Ressonador: vida do cristal por tier`.

### Task 2: Pó Carregado e o catalisador do Allthemodium para cima
- Itens `charged_dust_allthemodium|vibranium|unobtainium|ultimate` (registro, aba criativa só quando o tier está carregado, `lang`, textura pelo gerador: o Pó de Ressonância com a cor do tier, `GuiPaint.tierColor`, e uma faísca a mais).
- Receita sem forma (spec seção 3): 8 `resonance_dust` + 1 lingote do tier → 8; condição do Allthemodium (o Ultimate tem dois arquivos: com o mod, `c:ingots/unobtainium_vibranium_alloy`; sem, `minecraft:netherite_ingot`).
- `CoreCatalyst.accepts`: Allthemodium, Vibranium, Unobtainium e Ultimate só aceitam o Pó Carregado do próprio tier (menu, Porta, clique com a mão, mensagem `needs_charged_dust` nos dois `lang`); Elite e Esmeralda só o Pó; Básico e Avançado redstone ou Pó. Núcleo que já tinha catalisador de nível mais baixo gasta até acabar, como a redstone antiga no Elite.
- JUnit da regra pura (`CoreCatalyst`), GameTests (aceita e recusa por tier, a Porta, a recarga gastando o item certo).
- Commit: `Ressonador: Pó Carregado e catalisador por tier`.

### Task 3: A cascata de itens (1 a 10) e as receitas
- Os 10 itens da spec seção 4 (registro, aba criativa, `lang`, texturas pelo gerador no estilo do mod: liga e placa em coral, fio e bobina em cobre e coral, lente com o Lapidado, circuito e matriz com o verde de placa, estabilizador e condensado maiores e mais brilhantes), e as receitas:
  - 1 (cristal cheio sem rachadura): tipo próprio, no molde do `CutCrystalRecipe` (ou ingrediente que só aceita cristal cheio); 2 é `minecraft:blasting` (e `smelting` opcional não); 3 a 10 shaped/shapeless à escolha, desde que casem com a tabela da spec (3 lingotes em linha para a placa; 8 fios em volta de 1 bloco de ferro para a bobina). Rendimentos: 1→2, 4→4, 7→2.
  - Lente (6): o Lapidado deve ser cheio (use o mesmo mecanismo da 1).
- JUnit/GameTest: cada receita casa e rende o certo; o Lapidado ou Cristal gasto não casa.
- Commit: `Ressonador: cascata de crafting`.

### Task 4: Coração do Ressonador por tier e a exigência no Núcleo
- 8 itens `resonator_heart_<tier>` (Allthemodium, Vibranium e Unobtainium só com o mod; texturas pelo gerador, com a cinta do tier) e as receitas da spec seção 4 (sem forma; Ultimate em dois arquivos, com e sem o mod).
- `resonance_core.json`: o diamante da base vira o Coração Básico.
- `ResonanceCoreBlock.tryUpgrade` (e o `TierCoreItem` no que for preciso): subir o Núcleo gasta 1 Coração do tier de destino do inventário do jogador (criativo não gasta); sem ele, nada muda e a action bar diz `block.wirelessautomate.resonance_core.needs_heart` ("Falta o Coração do Ressonador (<tier>)"); o gasto só depois de todas as outras recusas. Os GameTests de subir o Núcleo que já existem passam a dar o Coração ao jogador de teste; GameTests novos: sobe com o Coração e o consome, recusa sem ele, criativo não gasta, o Coração errado não vale.
- Tooltip do Cartão de Upgrade no Núcleo e do Coração (qual tier e o que pede).
- Commit: `Ressonador: Coração do Ressonador por tier`.

### Task 5: Fechamento da etapa 1b
- `docs/progresso.md` (estado, próximo passo = etapa 2, histórico com os números de testes), `CLAUDE.md` (itens novos, `CoreCatalyst`, config), verificação completa das quatro runs, `varrer_perf.py`, `varrer.py` e `medir.py`.
- Commit: `Ressonador: fechamento da etapa 1b`.
