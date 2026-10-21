---
navigation:
  title: Quartzo de Sinal e cristais
  icon: wirelessautomate:signal_crystal
  parent: index.md
  position: 18
item_ids:
  - wirelessautomate:signal_quartz_ore
  - wirelessautomate:deepslate_signal_quartz_ore
  - wirelessautomate:signal_crystal
  - wirelessautomate:cut_signal_crystal
  - wirelessautomate:tuned_signal_crystal_elite
  - wirelessautomate:tuned_signal_crystal_emerald
  - wirelessautomate:tuned_signal_crystal_allthemodium
  - wirelessautomate:tuned_signal_crystal_vibranium
  - wirelessautomate:tuned_signal_crystal_unobtainium
  - wirelessautomate:tuned_signal_crystal_ultimate
  - wirelessautomate:resonance_dust
  - wirelessautomate:charged_dust_allthemodium
  - wirelessautomate:charged_dust_vibranium
  - wirelessautomate:charged_dust_unobtainium
  - wirelessautomate:charged_dust_ultimate
  - wirelessautomate:signal_alloy_raw
  - wirelessautomate:signal_alloy_ingot
  - wirelessautomate:resonance_plate
  - wirelessautomate:resonance_wire
  - wirelessautomate:resonance_coil
  - wirelessautomate:harmonic_lens
  - wirelessautomate:phase_circuit
  - wirelessautomate:phase_matrix
  - wirelessautomate:field_stabilizer
  - wirelessautomate:condensed_core
  - wirelessautomate:alloy_frame
  - wirelessautomate:resonator_heart_basic
  - wirelessautomate:resonator_heart_advanced
  - wirelessautomate:resonator_heart_elite
  - wirelessautomate:resonator_heart_emerald
  - wirelessautomate:resonator_heart_allthemodium
  - wirelessautomate:resonator_heart_vibranium
  - wirelessautomate:resonator_heart_unobtainium
  - wirelessautomate:resonator_heart_ultimate
---


# Quartzo de Sinal e cristais

<Row gap="8">
  <ItemImage id="wirelessautomate:signal_quartz_ore" />
  <ItemImage id="wirelessautomate:deepslate_signal_quartz_ore" />
  <ItemImage id="wirelessautomate:signal_crystal" />
  <ItemImage id="wirelessautomate:cut_signal_crystal" />
  <ItemImage id="wirelessautomate:tuned_signal_crystal_elite" />
  <ItemImage id="wirelessautomate:resonance_dust" />
</Row>

Os cristais de quartzo são o combustível do [Ressonador de Sinal](signal-resonator.md), o gerador de energia do mod.
Esta página mostra de onde eles vêm, como cuidar deles e as peças que o Ressonador pede para subir de tier.

## O minério

O **Minério de Quartzo de Sinal** aparece no Overworld, em pedra e em ardósia (a versão de ardósia
é mais dura). Fica em veios pequenos, mais comum perto de **y = −16**. Quebre com **picareta de ferro** ou melhor:

| Ferramenta | O que cai |
| --- | --- |
| Picareta de ferro ou melhor | 1 a 3 **Cristais de Sinal** e um pouco de experiência. **Fortuna** aumenta a quantidade. |
| Toque de Seda | O próprio minério. |

## Os cristais

| Cristal | De onde vem |
| --- | --- |
| <ItemImage id="wirelessautomate:signal_crystal" /> **Cristal de Sinal** | Do minério. |
| <ItemImage id="wirelessautomate:cut_signal_crystal" /> **Cristal Lapidado** | Bancada: quatro Cristais de Sinal em cruz em volta de um Fragmento de Ametista. |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_elite" /> **Cristal Afinado** | Bancada: um Lapidado com o material do tier (mais abaixo). Um por tier, do Elite ao Ultimate. |

Quanto cada um rende numa câmara depende do tier do Núcleo:

| Cristal | Núcleo Básico ou Avançado | Núcleo Elite ou acima |
| --- | --- | --- |
| **Cristal de Sinal** | 1× | 1/4× |
| **Lapidado** | 2× | 1/2× |
| **Afinado** do tier do Núcleo ou acima | 2× | 2× |
| **Afinado** de tier abaixo | 2× | 1/2× |

O Lapidado e os Afinados duram o dobro do Cristal de Sinal. Quanto maior o tier do Núcleo, menos a carga dura: a
tabela da página do [Ressonador de Sinal](signal-resonator.md) mostra o tempo de cada tier.

As receitas dos cristais só aceitam cristais **cheios** e sem rachadura: cristais gastos não valem.

<RecipeFor id="wirelessautomate:cut_signal_crystal" />

## Afinar um cristal

<Row gap="8">
  <ItemImage id="wirelessautomate:tuned_signal_crystal_elite" />
  <ItemImage id="wirelessautomate:tuned_signal_crystal_emerald" />
  <ItemImage id="wirelessautomate:tuned_signal_crystal_allthemodium" />
  <ItemImage id="wirelessautomate:tuned_signal_crystal_vibranium" />
  <ItemImage id="wirelessautomate:tuned_signal_crystal_unobtainium" />
  <ItemImage id="wirelessautomate:tuned_signal_crystal_ultimate" />
</Row>

Do **Núcleo Elite** para cima, só o **Cristal Afinado** do tier do Núcleo (ou de um tier acima) rende cheio. Para
afinar, junte na bancada, em qualquer posição, um **Lapidado** (ou um Afinado de tier abaixo, para reafinar) e o
material do tier:

| Afinado | Material, além do cristal |
| --- | --- |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_elite" /> **Elite** | 2 lingotes de netherite |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_emerald" /> **Esmeralda** | 2 blocos de esmeralda + 1 Nether Star |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_allthemodium" /> **Allthemodium¹** | 2 lingotes de Allthemodium + 1 Nether Star |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_vibranium" /> **Vibranium¹** | 2 lingotes de Vibranium + 1 lingote de liga Vibranium-Allthemodium |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_unobtainium" /> **Unobtainium¹** | 2 lingotes de Unobtainium + 1 lingote de liga Unobtainium-Vibranium |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_ultimate" /> **Ultimate** | Com o Allthemodium: 2 lingotes de liga Unobtainium-Vibranium + 1 Nether Star. Sem ele: 1 bloco de netherite + 1 Nether Star + 1 Fragmento de Eco |

¹ Só com o mod Allthemodium.

O cristal afinado sai cheio, mas com a **integridade** que o cristal de entrada tinha: afinar não renova o cristal.

<RecipeFor id="wirelessautomate:tuned_signal_crystal_elite" />

## Carga e cristal Opaco

A **barra do item** mostra quanto falta para o cristal acabar: ela só aparece depois que o cristal começa a
gastar. Dentro do Núcleo, a carga cai enquanto ele gera energia.

Sem carga, o cristal fica **Opaco**: é o mesmo item, só que apagado. Um Opaco não gera nada, mas dá para
**recarregar**:

1. Deixe o Opaco numa câmara do Núcleo.
2. Ponha o **catalisador** do tier no Núcleo (clique com ele na mão, pela tela ou pela Porta de Cristais):
   **redstone** ou **Pó de Ressonância** no Básico e no Avançado, só **Pó de Ressonância** no Elite e na Esmeralda,
   e o **Pó Carregado** do tier do Allthemodium para cima.
3. O Núcleo recarrega **um Opaco por vez**: leva 30 segundos e gasta 1 catalisador, e o cristal volta cheio.

O slot do catalisador guarda um tipo por vez: para trocar de catalisador, espere o que está lá acabar ou tire-o
pela tela. A Porta de Cristais só devolve os Opacos quando o Núcleo está sem catalisador; com ele, o Opaco fica lá
dentro esperando a vez de recarregar.

## Integridade e cristal rachado

Cada cristal aguenta **8 cargas**: a de fábrica e mais 7 recargas. Cada recarga gasta 1 de **integridade** (a dica
do item mostra "Integridade 5 de 8", e a tela do Núcleo, uma barrinha sob cada câmara). Quando a integridade acaba,
a recarga não enche o cristal: ele **racha**.

Um **cristal rachado** não gera, não recarrega e não entra na câmara. A Porta de Cristais sempre o devolve. Para
**refazer**, junte na bancada, em qualquer posição, o rachado e **4 Cristais de Sinal cheios**: sai o mesmo cristal,
cheio e com a integridade inteira. Vale para os três tipos; num Afinado, o tier fica.

No Núcleo Ultimate, um Afinado Ultimate rachado também pode ser queimado no **Eco**, como na página do
[Ressonador de Sinal](signal-resonator.md).

## Pó de Ressonância e Pó Carregado

<Row gap="8">
  <ItemImage id="wirelessautomate:resonance_dust" />
  <ItemImage id="wirelessautomate:charged_dust_allthemodium" />
  <ItemImage id="wirelessautomate:charged_dust_vibranium" />
  <ItemImage id="wirelessautomate:charged_dust_unobtainium" />
  <ItemImage id="wirelessautomate:charged_dust_ultimate" />
</Row>

O **Pó de Ressonância** é o catalisador do Núcleo Elite e do Esmeralda (e vale também no Básico e no Avançado).
Na bancada, sem forma: **1 redstone + 1 pó de glowstone + 1 Cristal de Sinal cheio** rendem 4.

<RecipeFor id="wirelessautomate:resonance_dust" />

Do Allthemodium para cima, cada Núcleo pede o **Pó Carregado** do tier dele. Na bancada, sem forma:
**8 Pós de Ressonância + 1 lingote do tier** rendem 8.

| Pó Carregado | Lingote |
| --- | --- |
| <ItemImage id="wirelessautomate:charged_dust_allthemodium" /> **Allthemodium¹** | 1 lingote de Allthemodium |
| <ItemImage id="wirelessautomate:charged_dust_vibranium" /> **Vibranium¹** | 1 lingote de Vibranium |
| <ItemImage id="wirelessautomate:charged_dust_unobtainium" /> **Unobtainium¹** | 1 lingote de Unobtainium |
| <ItemImage id="wirelessautomate:charged_dust_ultimate" /> **Ultimate** | Com o Allthemodium: 1 lingote de liga Unobtainium-Vibranium. Sem ele: 1 lingote de netherite |

¹ Só com o mod Allthemodium.

## As peças do Coração

Para montar o Núcleo e para subir o tier dele, o Ressonador pede um **Coração do Ressonador** (mais abaixo). Os
Corações saem desta cadeia de peças, feitas na ordem:

| Peça | Receita | Rende |
| --- | --- | --- |
| <ItemImage id="wirelessautomate:signal_alloy_raw" /> **1. Liga de Sinal crua** | Bancada, sem forma: 2 Cristais de Sinal cheios + 2 lingotes de ferro + 1 redstone | 2 |
| <ItemImage id="wirelessautomate:signal_alloy_ingot" /> **2. Lingote de Liga de Sinal** | **Alto-forno**: 1 Liga de Sinal crua | 1 |
| <ItemImage id="wirelessautomate:resonance_plate" /> **3. Placa de Ressonância** | 3 Lingotes de Liga de Sinal em linha | 1 |
| <ItemImage id="wirelessautomate:resonance_wire" /> **4. Fio de Ressonância** | 1 Lingote de Liga de Sinal entre 2 lingotes de cobre, com 1 lingote de ouro embaixo | 4 |
| <ItemImage id="wirelessautomate:resonance_coil" /> **5. Bobina de Ressonância** | 8 Fios em volta de 1 bloco de ferro | 1 |
| <ItemImage id="wirelessautomate:harmonic_lens" /> **6. Lente Harmônica** | Bancada, sem forma: 1 Cristal Lapidado cheio + 2 diamantes + 4 Vidros de Ressonância | 1 |
| <ItemImage id="wirelessautomate:phase_circuit" /> **7. Circuito de Fase** | 1 Placa no centro, 2 blocos de redstone, 2 lingotes de ouro e 4 quartzos do Nether | 2 |
| <ItemImage id="wirelessautomate:phase_matrix" /> **8. Matriz de Fase** | 1 Lente no centro, 4 Bobinas nos cantos, 2 Circuitos e 2 Placas | 1 |
| <ItemImage id="wirelessautomate:field_stabilizer" /> **9. Estabilizador de Campo** | 4 Matrizes + 1 bloco de diamante + 2 obsidianas chorosas + 1 olho do ender | 1 |
| <ItemImage id="wirelessautomate:condensed_core" /> **10. Núcleo Condensado** | 4 Estabilizadores + 4 blocos de netherite + 1 Nether Star no centro | 1 |

O JEI mostra a posição de cada ingrediente.

<RecipeFor id="wirelessautomate:signal_alloy_ingot" />
<RecipeFor id="wirelessautomate:phase_matrix" />

## O Coração do Ressonador

<Row gap="8">
  <ItemImage id="wirelessautomate:resonator_heart_basic" />
  <ItemImage id="wirelessautomate:resonator_heart_advanced" />
  <ItemImage id="wirelessautomate:resonator_heart_elite" />
  <ItemImage id="wirelessautomate:resonator_heart_emerald" />
  <ItemImage id="wirelessautomate:resonator_heart_allthemodium" />
  <ItemImage id="wirelessautomate:resonator_heart_vibranium" />
  <ItemImage id="wirelessautomate:resonator_heart_unobtainium" />
  <ItemImage id="wirelessautomate:resonator_heart_ultimate" />
</Row>

Há um **Coração do Ressonador** por tier. O Básico vai na receita do Núcleo de Ressonância; os outros são gastos
ao subir o Núcleo com o Cartão de Upgrade (veja o [Ressonador de Sinal](signal-resonator.md)). Todos são feitos na
bancada, sem forma:

| Coração | Receita |
| --- | --- |
| <ItemImage id="wirelessautomate:resonator_heart_basic" /> **Básico** | 1 Matriz de Fase + 4 blocos de redstone |
| <ItemImage id="wirelessautomate:resonator_heart_advanced" /> **Avançado** | 2 Matrizes de Fase + 4 blocos de ouro |
| <ItemImage id="wirelessautomate:resonator_heart_elite" /> **Elite** | 3 Matrizes de Fase + 4 lingotes de netherite |
| <ItemImage id="wirelessautomate:resonator_heart_emerald" /> **Esmeralda** | 1 Estabilizador de Campo + 7 blocos de esmeralda + 1 Nether Star; com o Modern Industrialization: 1 Estabilizador de Campo + 6 blocos de esmeralda + 1 Nether Star + 1 Processing Unit |
| <ItemImage id="wirelessautomate:resonator_heart_allthemodium" /> **Allthemodium¹** | 2 Estabilizadores de Campo + 4 blocos de Allthemodium + 2 Nether Stars; com o MEGA Cells: 1 Estabilizador de Campo + 4 blocos de Allthemodium + 2 Nether Stars + 2 Cell Components 16M |
| <ItemImage id="wirelessautomate:resonator_heart_vibranium" /> **Vibranium¹** | 1 Núcleo Condensado + 4 blocos de Vibranium + 2 Nether Stars; com o Modern Industrialization e o Draconic Evolution: 1 Núcleo Condensado + 4 blocos de Vibranium + 1 Nether Star + 1 Processing Unit + 2 Chaotic Cores |
| <ItemImage id="wirelessautomate:resonator_heart_unobtainium" /> **Unobtainium¹** | 2 Núcleos Condensados + 4 blocos de Unobtainium + 3 Nether Stars; com o MEGA Cells e o Draconic Evolution: 1 Núcleo Condensado + 4 blocos de Unobtainium + 2 Nether Stars + 1 Cell Component 16M + 1 Chaotic Core |
| <ItemImage id="wirelessautomate:resonator_heart_ultimate" /> **Ultimate** | 3 Núcleos Condensados + 1 Moldura de Liga + 5 Nether Stars; com o MEGA Cells, o Draconic Evolution e o Modern Industrialization: 3 Cell Components 256M + 1 Cell Component 64M + 1 Quantum Circuit + 1 Chaotic Core + 1 Awakened Core + 1 Moldura de Liga + 1 Nether Star |

¹ Só com o mod Allthemodium.

A <ItemImage id="wirelessautomate:alloy_frame" /> **Moldura de Liga** do Coração Ultimate é um anel de 8 blocos em volta de
1 Nether Star: com o Allthemodium, blocos de liga Unobtainium-Vibranium; sem ele, blocos de netherite.

<RecipeFor id="wirelessautomate:resonator_heart_basic" />
<RecipeFor id="wirelessautomate:alloy_frame" />

Os números são os padrões; o JEI mostra os do seu pack.

Veja como montar o gerador em [Ressonador de Sinal](signal-resonator.md).
