# Ressonador de Sinal: rebalanceamento justo (cascata, Coração e escada revisada)

Aprovado pelo dono em 21/10/2026 ("todo o plano proposto"), a partir da proposta revisada `https://claude.ai/artifact/PHwob7zZjJrWZDL9sVzo8W`. **Esta spec revisa a [spec de 20/10](2026-10-20-ressonador-rebalanceamento-design.md)**: onde divergem, vale esta; o resto da de 20/10 (Afinados, fadiga, Pó de Ressonância, interferência, Eco, tela, JEI e guia, migração 3, custo de performance) continua valendo.

## Princípio

Em cada tier, montar e manter o Ressonador dá trabalho parecido com o melhor gerador do pack naquele tier, e ele gera cerca de 20% a mais que esse rival. A Carcaça (os 98 blocos) não muda; o custo vai para o Coração do Ressonador, os cristais e a manutenção. Nada explode.

## 1. Escada revisada (no lugar da tabela 1 da spec de 20/10)

FE/t máximo = câmaras × FE por cristal × 2 (Afinado do tier em todas as câmaras, eficiência 100%).

| Tier | Câmaras | FE por cristal | FE/t máximo | Rival |
| --- | --- | --- | --- | --- |
| Básico | 1 | 216 | 432 | Alternator, 360 |
| Avançado | 2 | 6.912 | 27.648 | painel Eclipse, 23.040 |
| Elite | 3 | 10.000 | 60.000 | painel Nitro / Rainbow Furnace, 50.000 |
| Esmeralda | 4 | 160.000 | 1.280.000 | painel Chaotic, 1.048.576 |
| Allthemodium | 5 | 6.400.000 | 64.000.000 | Turbina Industrial + fissão, 53,5M |
| Vibranium | 6 | 6.700.000 | 80.400.000 | 25% acima do Allthemodium |
| Unobtainium | 7 | 184.000.000 | 2.576.000.000 | painel Infinity, 2,15B |
| Ultimate | 8 | 268.435.456 | 4.294.967.296 + Eco | 2× o painel Infinity; a Pandora cai para o Eco |

Energia do roteador por face (a seção 6 da spec de 20/10 muda): 1.000; 32.768; 64.000; 2.097.152; 67.108.864; 134.217.728; 4.294.967.296; sem limite (o Básico e o Elite já cobrem a geração e ficam). A migração 3 troca só o que ainda está no padrão anterior.

## 2. Vida do cristal por tier

Config nova `resonator.crystalLifeMinutes` (lista de 8, uma por tier do Núcleo; faixa 1 a 240): **20, 20, 15, 10, 5, 5, 4, 3**. É o tempo, no Núcleo daquele tier a 100% de geração, que a carga dura até o cristal ficar Opaco. Substitui, para o desgaste, o `crystalLifeTicks` e o `cutCrystalLifeTicks` atuais (que continuam lidos como a base do Básico e do Avançado, para não mudar mundos que já têm Ressonador). Cada fim de vida é uma recarga que gasta 1 catalisador e 1 de integridade.

## 3. Catalisador em três níveis (no lugar da regra da spec de 20/10)

- Núcleo Básico e Avançado: redstone ou Pó de Ressonância.
- Núcleo Elite e Esmeralda: só Pó de Ressonância.
- Núcleo Allthemodium, Vibranium, Unobtainium e Ultimate: só o **Pó Carregado do tier** (`charged_dust_allthemodium`, `_vibranium`, `_unobtainium`, `_ultimate`). Receita sem forma: **8 Pós de Ressonância + 1 lingote do tier, rende 8** (lingote: `c:ingots/allthemodium`, `c:ingots/vibranium`, `c:ingots/unobtainium` e, no Ultimate, `c:ingots/unobtainium_vibranium_alloy`; sem o Allthemodium, o Ultimate usa 1 lingote de netherite). Só com o Allthemodium, exceto o Ultimate. O Núcleo guarda o item do catalisador (NBT `catalyst`, como no plano da etapa 1) e a regra vale no menu, na Porta e no clique.
- Manutenção por hora, Núcleo no máximo (câmaras × recargas por hora): Básico 3 redstone; Avançado 6; Elite 12 Pós; Esmeralda 24 Pós; Allthemodium 60 Pós Carregados; Vibranium 72; Unobtainium 105; Ultimate 160.

## 4. A cascata de 11 itens (próprios do mod)

Itens: `signal_alloy_raw` (Liga de Sinal crua), `signal_alloy_ingot` (Lingote de Liga de Sinal), `resonance_plate` (Placa de Ressonância), `resonance_wire` (Fio), `resonance_coil` (Bobina), `harmonic_lens` (Lente Harmônica), `phase_circuit` (Circuito de Fase), `phase_matrix` (Matriz de Fase), `field_stabilizer` (Estabilizador de Campo), `condensed_core` (Núcleo Condensado) e `resonator_heart_<tier>` (Coração do Ressonador, um por tier). Receitas (todas editáveis no `/wa recipes`, que só abre `crafting_shaped`/`shapeless`; o passo do alto-forno é `minecraft:blasting`):

| # | Item | Receita | Rende |
| --- | --- | --- | --- |
| 1 | Liga de Sinal crua | bancada: 2 Cristais de Sinal cheios + 2 lingotes de ferro + 1 redstone | 2 |
| 2 | Lingote de Liga de Sinal | alto-forno: 1 Liga crua | 1 |
| 3 | Placa de Ressonância | bancada: 3 Lingotes de Liga em linha | 1 |
| 4 | Fio de Ressonância | bancada: 1 Lingote de Liga + 2 cobre + 1 ouro | 4 |
| 5 | Bobina de Ressonância | bancada: 8 Fios em volta de 1 bloco de ferro | 1 |
| 6 | Lente Harmônica | bancada: 1 Cristal Lapidado + 2 diamantes + 4 Vidros do Ressonador | 1 |
| 7 | Circuito de Fase | bancada: 1 Placa + 2 blocos de redstone + 4 quartzo do Nether + 2 ouro | 2 |
| 8 | Matriz de Fase | bancada: 4 Bobinas + 1 Lente + 2 Circuitos + 2 Placas | 1 |
| 9 | Estabilizador de Campo | bancada: 4 Matrizes + 1 bloco de diamante + 2 obsidianas chorosas + 1 olho do ender | 1 |
| 10 | Núcleo Condensado | bancada: 4 Estabilizadores + 4 blocos de netherite + 1 Nether Star | 1 |

O Cristal de Sinal e o Lapidado das receitas 1 e 6 são só cheios e sem rachadura (tipo de receita próprio onde o ingrediente comum não basta).

**Coração do Ressonador (11, um por tier):**

| Tier | Receita (sem forma) |
| --- | --- |
| Básico | 1 Matriz + 4 blocos de redstone |
| Avançado | 2 Matrizes + 4 blocos de ouro |
| Elite | 3 Matrizes + 4 lingotes de netherite |
| Esmeralda | 1 Estabilizador + 7 blocos de esmeralda + 1 Nether Star |
| Allthemodium | 2 Estabilizadores + 4 blocos de Allthemodium + 2 Nether Stars |
| Vibranium | 1 Núcleo Condensado + 4 blocos de Vibranium + 2 Nether Stars |
| Unobtainium | 2 Núcleos Condensados + 4 blocos de Unobtainium + 3 Nether Stars |
| Ultimate | 3 Núcleos Condensados + 1 Moldura de Liga + 5 Nether Stars (os dois arquivos, com e sem o Allthemodium, usam a mesma Moldura) |

A receita sem forma aceita no máximo 9 itens, por isso o Esmeralda ficou com 7 blocos e o Unobtainium com 3 Nether Stars. O Ultimate (19 itens no desenho original) guarda o custo na **Moldura de Liga** (`alloy_frame`, item intermediário, registrado e na aba criativa sempre): `crafting_shaped` em anel, 8 blocos de liga Unobtainium-Vibranium (`c:storage_blocks/unobtainium_vibranium_alloy`) em volta de 1 Nether Star, com a condição do Allthemodium; sem o mod (`neoforge:not`), 8 blocos de netherite em volta de 1 Nether Star.

Allthemodium, Vibranium e Unobtainium só existem com o mod (condições `neoforge:mod_loaded`, tags `c:storage_blocks/...`), como o resto desses tiers.

## 5. O Núcleo pede o Coração do tier

- **Núcleo Básico (receita `resonance_core`):** o diamante da base é trocado pelo Coração Básico.
- **Subir o Núcleo pelo Cartão de Upgrade** (`ResonanceCoreBlock.tryUpgrade`, pelo `useOn` do cartão): além do cartão, gasta **1 Coração do tier de destino do inventário** do jogador (criativo não gasta). Sem o Coração, nada muda e a action bar diz "Falta o Coração do Ressonador (<tier>)". O cartão só sobe um degrau (regra de hoje), então cada degrau pede o seu Coração.
- O roteador e os armazenamentos não mudam.
- Um Coração só gasta no clique que sobe o Núcleo de verdade (nunca antes de as outras recusas, como a estrutura montada).

## 6. O que continua da spec de 20/10

Cristais Afinados (receitas, rendimento em quartos), fadiga e rachado (8 cargas, refazer com 4 Cristais de Sinal), Pó de Ressonância (1 redstone + 1 glowstone + 1 Cristal de Sinal, rende 4), cartão do Ultimate com Allthemodium mais caro, interferência (32 blocos, 15% cada, teto 60%), Eco Ressonante no Ultimate, a tela, o payload 26, JEI e guia.

## Etapas

1. **Etapa 1** (plano `2026-10-20-ressonador-rebalanceamento-etapa-1.md`, com os valores desta spec): escada, energia do roteador, migração 3, quartos, Afinados, fadiga, Pó e catalisador por tier.
2. **Etapa 1b** (plano `2026-10-21-ressonador-cascata.md`): vida do cristal por tier, Pó Carregado, a cascata, os Corações e a exigência no Núcleo.
3. **Etapa 2:** interferência, Eco, tela, payload 26 e BER do rachado.
4. **Etapa 3:** JEI, guia, `docs/especificacao.md`, `CLAUDE.md`, `docs/progresso.md`.
