# Ressonador de Sinal: rebalanceamento (o melhor gerador de cada tier)

Aprovado pelo dono em 20/10/2026, junto com as recomendações das 6 decisões do mockup [`docs/preview/ressonador-rebalanceamento.html`](../../preview/ressonador-rebalanceamento.html). Os protótipos dos cristais estão em [`docs/preview/ressonador-afinados-proto.png`](../../preview/ressonador-afinados-proto.png). A base é a [spec do Ressonador](2026-10-15-ressonador-de-sinal-design.md); o que esta spec não muda continua valendo.

## Por que

A medição dos 80 geradores do modpack Infinity Legacy II (tier list em https://claude.ai/artifact/XifgBxwhHfuFQLLSvxce7Z) cruzou a geração máxima com o custo de craft e a manutenção de cada um, medidos nas receitas reais do pack. Dois problemas apareceram:

- **Caro de menos nos tiers altos.** A estrutura é de ferro, terracota e Cristais de Sinal em qualquer tier, e a manutenção é 0,05 a 0,4 redstone por minuto. O Unobtainium (1,88B FE/t) e o Ultimate (17,2B) geram de 300× a 2.600× mais do que o esforço deles justifica na curva do pack. O cartão do Ultimate com o Allthemodium sai mais barato que o do Unobtainium.
- **Fraco nos tiers baixos e médios.** O Básico perde para o Alternator, o Avançado para o painel Eclipse, o Elite empata com o painel Nitro, e o Vibranium perde para as turbinas do Mekanism e do Extreme Reactors.

O objetivo do dono: o Ressonador deve ser **o melhor gerador de cada tier**, inclusive à frente da Pandora Box do Min For Max no Ultimate, com custo e manutenção que paguem por isso, por mecânicas próprias do mod.

## Resumo

1. **Escada nova** de FE por cristal, acima do melhor rival de cada tier.
2. **Cristais Afinados:** do Elite para cima, cada câmara pede o cristal do tier, que leva o material do tier.
3. **Fadiga do cristal:** cada carga gasta integridade; sem integridade o cristal racha e é refeito com Cristais de Sinal. Do Elite para cima a recarga gasta Pó de Ressonância em vez de redstone.
4. **Interferência:** Núcleos montados perto uns dos outros perdem eficiência.
5. **Eco Ressonante (só Ultimate):** queimar Afinados Ultimate rachados dá crescimento permanente sem teto.
6. **Roteador:** a energia do roteador de cada tier sobe até a geração do Ressonador do mesmo tier.

## 1. A escada

FE/t = câmaras × FE por cristal × rendimento × eficiência × fator de interferência × fator do Eco. Câmaras: Básico 1 até Ultimate 8 (sem mudança).

| Tier | Rival a vencer | FE por cristal (antes → novo) | FE/t no máximo (antes → novo) |
| --- | --- | --- | --- |
| Básico | Alternator, 360 | 128 → **512** | 256 → 1.024 |
| Avançado | painel Eclipse, 23.040 | 1.024 → **8.192** | 4.096 → 32.768 |
| Elite | painel Nitro e Rainbow Furnace, 50.000 | 8.192 → **16.384** | 49.152 → 98.304 |
| Esmeralda | painel Chaotic, 1.048.576 | 32.768 → **262.144** | 262.144 → 2.097.152 |
| Allthemodium | Turbina do Mekanism com fissão, 53,5M | 262.144 → **8.388.608** | 2,62M → 83,9M |
| Vibranium | turbinas e Naquadah, até 53,5M | 2.097.152 → **16.777.216** | 25,2M → 201,3M |
| Unobtainium | painel Infinity, 2,15B | 134.217.728 → **268.435.456** | 1,88B → 3,76B |
| Ultimate | painel Infinity e Pandora | 1.073.741.824 → **536.870.912** | 17,2B → 8,59B + Eco |

O máximo é com o rendimento 2 (Lapidado ou Afinado do tier) em todas as câmaras e eficiência 100%. Os valores ficam no `CoreMath.DEFAULT_FE_PER_CRYSTAL` e na config `resonator.<tier>.fePerCrystal`, que já existem.

## 2. Cristais Afinados

- **Itens novos:** `tuned_signal_crystal_<tier>` para `elite`, `emerald`, `allthemodium`, `vibranium`, `unobtainium` e `ultimate`. São `SignalCrystalItem` cortados (rendimento 2), com o tier gravado no próprio item (um item por tier, para a textura e o JEI). Os do Allthemodium, Vibranium e Unobtainium só aparecem na aba criativa, no JEI e nas receitas com o Allthemodium (`RouterTier.loaded()`), como o resto desses tiers.
- **Rendimento por câmara.** O rendimento passa a ser contado em quartos, para o Lapidado comum valer 1/4 nos tiers altos:

| Cristal | Núcleo Básico ou Avançado | Núcleo Elite ou acima |
| --- | --- | --- |
| Cristal de Sinal | 1 | 1/4 |
| Lapidado | 2 | 1/2 |
| Afinado de tier igual ou maior que o do Núcleo | 2 | 2 |
| Afinado de tier menor que o do Núcleo | 2 | 1/2 |

  Com isso os mundos que já têm Ressonador continuam funcionando: nada sai das câmaras e nada quebra, só rende menos até o jogador afinar os cristais (decisão 2).
- **Receitas** (sem forma, tipo novo `wirelessautomate:tune_crystal`, que só aceita cristal cheio e sem rachadura, como o `cut_crystal`). O cristal de entrada é um Lapidado ou um Afinado de tier menor (reafinar sem perder o anterior):

| Afinado | Material, além do cristal | Condição |
| --- | --- | --- |
| Elite | 2 lingotes de netherite | sempre |
| Esmeralda | 2 blocos de esmeralda + 1 Nether Star | sempre |
| Allthemodium | 2 `c:ingots/allthemodium` + 1 Nether Star | Allthemodium |
| Vibranium | 2 `c:ingots/vibranium` + 1 `c:ingots/vibranium_allthemodium_alloy` | Allthemodium |
| Unobtainium | 2 `c:ingots/unobtainium` + 1 `c:ingots/unobtainium_vibranium_alloy` | Allthemodium |
| Ultimate | 2 `c:ingots/unobtainium_vibranium_alloy` + 1 Nether Star | Allthemodium |
| Ultimate | 1 bloco de netherite + 1 Nether Star + 1 fragmento de eco | sem Allthemodium |

- **Cartão do Ultimate com o Allthemodium e sem o All The Tweaks** (`tier_core_ultimate_atm.json`): de echo shards, ovo do dragão e blocos de netherite para **4 Nether Stars nos cantos, ovo do dragão em cima e 3 `c:storage_blocks/unobtainium_vibranium_alloy`** (padrão `"SDS","B B","SBS"`). O cartão é o mesmo para roteadores e armazenamentos, então o Ultimate deles também fica mais caro; é o que deixa o Ultimate acima do Unobtainium em custo. A regra "mesmo custo pelos dois caminhos" do `TierUpgradeRecipe` continua valendo (o caminho direto usa o mesmo anel).
- **Visual (decisão 6):** a forma do Lapidado com a cinta na cor do tier (`GuiPaint.tierColor`) e duas gemas do material, como nos protótipos. O Opaco do Afinado é o Opaco do Lapidado com a cinta do tier.

## 3. Fadiga do cristal

- **Integridade:** componente novo `crystal_integrity` (int de 0 até o máximo; sem o componente, cheio). Máximo na config `resonator.crystalIntegrity`, padrão **8** (decisão 3). Vale para os três tipos (Sinal, Lapidado, Afinados).
- **Quando gasta:** quando a recarga termina, o cristal perde 1 de integridade. Se a integridade chega a 0, ele **racha** em vez de encher: fica com carga 0 e integridade 0, não gera e não é recarregado. Um cristal novo, portanto, dá 8 cargas (a de fábrica e 7 recargas).
- **Rachado:** o mesmo item com integridade 0 (nome "… Rachado", predicado `wirelessautomate:cracked` para o modelo). A Porta de Cristais deixa extrair o rachado nas mesmas condições do Opaco. O Núcleo recarrega só cristais não rachados.
- **Refazer:** tipo novo `wirelessautomate:recast_crystal`, sem forma: 1 cristal rachado + **4 Cristais de Sinal cheios** (config `resonator.recastCrystals`, padrão 4) devolve o mesmo item, cheio e com integridade cheia. Funciona para todos os tipos; nos Afinados, o material do tier fica.
- **Catalisador da recarga:** o slot da redstone vira o slot do catalisador.
  - Núcleo Básico e Avançado: redstone (como hoje) ou Pó de Ressonância.
  - Núcleo Elite ou acima: **só Pó de Ressonância**. A redstone que já estiver num Núcleo desses quando o mundo carregar continua sendo gasta até acabar; nada novo de redstone entra.
  - **Pó de Ressonância** (`resonance_dust`): 1 redstone + 1 glowstone em pó + 1 Cristal de Sinal cheio, sem forma, rende 4.
  - O slot guarda até 64 (sem mudança) e a Porta aceita os dois pelo funil, com a mesma regra de tier.
- **Consumo no máximo:** com o Lapidado de 20 min por carga, 8 cargas por vida e 4 cristais para refazer: 1,5 Cristal de Sinal e 3 catalisadores por hora por câmara. No Ultimate (8 câmaras): 12 Cristais de Sinal e 24 Pós por hora.

## 4. Interferência

- Cada outro Núcleo **montado e carregado** na mesma dimensão, a até `resonator.interferenceRadius` blocos (padrão **32**, distância de Chebyshev entre os Núcleos; 0 desliga), tira `resonator.interferencePenalty` (padrão **0,15**) da eficiência, até `resonator.interferenceMax` (padrão **0,60**). Conta todos os tiers (decisão 4).
- O fator multiplica a eficiência depois do teto do Sintonizador. A tela mostra a perda e a distância do vizinho mais perto.
- Vizinho num chunk descarregado não conta; quando o chunk carrega, a conta é refeita.

## 5. Eco Ressonante

- Só no Núcleo **Ultimate**. Slot novo **Eco** na tela: um Afinado Ultimate rachado posto ali é consumido na hora e soma 1 de Eco. A Porta de Cristais também entrega o rachado Ultimate ao Eco quando ele chega pelo funil e a câmara não o aceita (rachado não entra em câmara). Para refazer em vez de queimar, o jogador tira o rachado pela Porta e o refaz na bancada.
- Cada Eco vale `resonator.ecoBonus` (padrão **0,05**, +5%) da geração base; **sem teto** por padrão (`resonator.ecoMax`, padrão 0 = sem teto; decisão 5). Fator do Eco = 1 + Eco × bônus, com o resultado saturando em `Long.MAX_VALUE`.
- O Eco é um `long` no NBT do Núcleo (chave `eco`) e vai para o item quando o Núcleo é quebrado (componente `resonator_eco`, pela loot table com `copy_components`), voltando ao colocar.
- O buffer acompanha: capacidade = geração máxima com o Eco × 200 ticks.
- Conta de referência: com as 8 câmaras queimando tudo o que racha, ~3 Ecos por hora, +15% por hora, ~+1,3B FE/t por hora. A Pandora com um gerador Ultimate Speed e XP no nível 1000 sobe ~277M FE/t por hora (fórmula do código do Min For Max).

## 6. Roteador (decisão 1)

A energia do roteador por tier (`ResourceType.ENERGY`, chave `[tiers.<tier>] energyPerTick`) passa a escoar a geração do Ressonador do mesmo tier por uma face:

| Tier | Antes (FE/t) | Novo |
| --- | --- | --- |
| Básico | 1.000 | 1.024 |
| Avançado | 8.000 | 32.768 |
| Elite | 64.000 | 131.072 |
| Esmeralda | 512.000 | 2.097.152 |
| Allthemodium | 4.096.000 | 83.886.080 |
| Vibranium | 32.768.000 | 268.435.456 |
| Unobtainium | 2.097.152.000 | 4.294.967.296 |
| Ultimate | sem limite | sem limite |

A conta da vazão já é `long` (`ScalarTransfer`, `BulkEnergy`); conferir que nada no caminho corta em `int`.

## Migração (balanceamento 3)

`Config.CURRENT_BALANCE` vai a **3** com o `migrateBalance3()`, no mesmo molde do 2: só troca o que ainda está no padrão do balanceamento 2 (o `fePerCrystal` de cada tier e o `energyPerTick` de cada tier); o que o dono do pack mudou fica. Os passos 1 e 2 continuam rodando para quem vem de antes. Chaves novas de config (`crystalIntegrity`, `recastCrystals`, `interference*`, `eco*`) nascem com o padrão.

Nenhuma chave antiga de NBT ou componente muda. Chaves novas: NBT `eco` no Núcleo; componentes `crystal_integrity` e `resonator_eco`.

## Tela do Núcleo (sem mudar o tamanho, 352 × 244)

- **Integridade:** uma barra fina (16 × 2 px) sob cada câmara, entre a câmara e o slot do catalisador, proporcional à integridade; vermelha no rachado. Dica do slot com "Integridade 5 de 8" e "Rachado: refaça com 4 Cristais de Sinal".
- **Catalisador:** o rótulo do slot diz "Redstone" ou "Pó de Ressonância" pelo tier; com redstone num Núcleo Elite ou acima, "redstone antiga, gasta até acabar".
- **Interferência:** linha nova no vão entre a dica da energia e a Estrutura: "Interferência: nenhuma" ou, em laranja, "−15% · Núcleo a 24 blocos".
- **Eco:** só no Ultimate, uma caixa abaixo das linhas da Estrutura com o slot Eco, "Eco 10 · +50%" e a dica "queime um Afinado Ultimate rachado".
- O `ResonatorStatePayload` ganha a perda da interferência (permille), a distância do vizinho mais perto, o Eco e o bônus do Eco; `ModPayloads.VERSION` vai de 25 para 26. A integridade vai pelo próprio ItemStack dos slots, como a carga.
- A câmara animada (BER) mostra o rachado escuro: o byte da `ChamberView` ganha o estado "rachado" para os três tipos.

## JEI e guia

- JEI: as receitas novas aparecem pelo tipo de bancada; a categoria Recarga mostra o catalisador por tier e a perda de integridade; a informação por tier ganha o Afinado exigido, a integridade, a interferência e (no Ultimate) o Eco. Uma categoria "Refazer o cristal" só se o tipo `recast_crystal` não aparecer sozinho na bancada do JEI.
- Guia (`scripts/guide/gerar_guia.py`, os dois idiomas, só "como operar"): a página `signal-quartz` ganha Afinados, integridade, rachado, refazer e o Pó; a `signal-resonator` ganha o catalisador por tier, a interferência, o Eco e a tabela nova de tiers.

## Custo de performance

| Mecânica | Por tick | Quando trabalha | Memória e rede |
| --- | --- | --- | --- |
| Escada nova | nada novo | no lote de 20 ticks que já existe | só config |
| Afinados e rendimento | nada | no lote, a mesma soma de rendimentos de hoje, agora em quartos | nenhuma chave nova no Núcleo |
| Fadiga | nada | só ao terminar uma recarga | 1 int por cristal; a câmara manda update só quando um cristal racha |
| Interferência | nada | ao montar, desmontar, carregar e descarregar um Núcleo: varredura linear dos Núcleos carregados da dimensão (o `ResonatorManager` não tem índice espacial; os Núcleos são poucos e a varredura é por evento) | 1 int e 1 distância no Núcleo, fora do NBT; fator guardado |
| Eco | nada | quando um cristal é queimado | 1 long no NBT |
| Tela | nada com a tela fechada | o pacote de estado de hoje, a cada 5 ticks e só se mudou | +4 campos pequenos |

## Etapas

1. **Motor e itens:** escada, energia do roteador, migração 3, rendimento em quartos, Afinados com receitas e texturas, fadiga (integridade, rachado, refazer), Pó e catalisador por tier, cartão do Ultimate. GameTests e JUnit.
2. **Interferência, Eco e tela:** as duas mecânicas no motor, a tela do Núcleo, o payload 26 e o BER do rachado. GameTests e e2e com as capturas.
3. **JEI, guia e fechamento:** JEI, guia nos dois idiomas, capturas do JEI no e2e, `docs/especificacao.md`, `CLAUDE.md` e `docs/progresso.md`.

## Fora desta spec

- Comparador, redstone de controle e Tablet no Ressonador (decisões anteriores).
- Mudar outros geradores ou a config do Infinity Legacy II.
- Risco de explosão: a manutenção sobe por suprimento, não por perigo.
- Índice espacial dos Núcleos: só se a varredura por evento aparecer no `/wa profile`.
