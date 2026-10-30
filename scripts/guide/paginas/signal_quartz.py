"""Página signal-quartz.md do guia."""
from guia_base import *  # noqa: F401,F403


TUNED_TIERS = ['elite', 'emerald', 'allthemodium', 'vibranium', 'unobtainium', 'ultimate']


CHARGED_TIERS = ['allthemodium', 'vibranium', 'unobtainium', 'ultimate']


CASCADE = ['signal_alloy_raw', 'signal_alloy_ingot', 'resonance_plate', 'resonance_wire', 'resonance_coil',
           'harmonic_lens', 'phase_circuit', 'phase_matrix', 'field_stabilizer', 'condensed_core']


QUARTZ_ITEMS = (['signal_quartz_ore', 'deepslate_signal_quartz_ore', 'signal_crystal', 'cut_signal_crystal']
                + ['tuned_signal_crystal_' + t for t in TUNED_TIERS] + ['resonance_dust']
                + ['charged_dust_' + t for t in CHARGED_TIERS] + CASCADE + ['alloy_frame']
                + ['resonator_heart_' + t[0] for t in TIERS])


TUNED_ROW = '\n'.join('  ' + item('tuned_signal_crystal_' + t) for t in TUNED_TIERS)


HEART_ROW = '\n'.join('  ' + item('resonator_heart_' + t[0]) for t in TIERS)


PAGINA = page('signal-quartz.md', front('Quartzo de Sinal e cristais', 'wirelessautomate:signal_crystal', 18, item_ids=[
    'wirelessautomate:' + i for i in QUARTZ_ITEMS]) + '''
# Quartzo de Sinal e cristais

<Row gap="8">
  ''' + item('signal_quartz_ore') + '''
  ''' + item('deepslate_signal_quartz_ore') + '''
  ''' + item('signal_crystal') + '''
  ''' + item('cut_signal_crystal') + '''
  ''' + item('tuned_signal_crystal_elite') + '''
  ''' + item('resonance_dust') + '''
</Row>

Os cristais são o combustível do [Ressonador de Sinal](signal-resonator.md), o gerador de energia do mod.

## Como usar

1. Minere o minério com picareta de ferro ou melhor.
2. Ponha os cristais nas câmaras do Núcleo. Do Elite para cima, use o **Afinado** do tier.
3. Cristal **Opaco** (sem carga): deixe na câmara e ponha catalisador no Núcleo.
4. Cristal **rachado**: refaça na bancada com 4 Cristais de Sinal cheios.

## O minério

No Overworld, em pedra e ardósia, em veios pequenos. Mais comum perto de **y = −16**.

| Ferramenta | O que cai |
| --- | --- |
| Picareta de ferro ou melhor | 1 a 3 **Cristais de Sinal** e um pouco de experiência. **Fortuna** aumenta a quantidade. |
| Toque de Seda | O próprio minério. |

## Os cristais

| Cristal | De onde vem |
| --- | --- |
| ''' + item('signal_crystal') + ''' **Cristal de Sinal** | Do minério. |
| ''' + item('cut_signal_crystal') + ''' **Cristal Lapidado** | Bancada: quatro Cristais de Sinal em cruz em volta de um Fragmento de Ametista. |
| ''' + item('tuned_signal_crystal_elite') + ''' **Cristal Afinado** | Bancada: um Lapidado com o material do tier (mais abaixo). Um por tier, do Elite ao Ultimate. |

Quanto cada um rende numa câmara:

| Cristal | Núcleo Básico ou Avançado | Núcleo Elite ou acima |
| --- | --- | --- |
| **Cristal de Sinal** | 1× | 1/4× |
| **Lapidado** | 2× | 1/2× |
| **Afinado** do tier do Núcleo ou acima | 2× | 2× |
| **Afinado** de tier abaixo | 2× | 1/2× |

Lapidado e Afinado duram o dobro. Quanto maior o Núcleo, menos a carga dura
([Ressonador de Sinal](signal-resonator.md)). As receitas só aceitam cristais **cheios** e sem rachadura.

<RecipeFor id="wirelessautomate:cut_signal_crystal" />

## Afinar um cristal

<Row gap="8">
''' + TUNED_ROW + '''
</Row>

Na bancada, sem forma: um **Lapidado** (ou um Afinado de tier abaixo) e o material do tier.

| Afinado | Material, além do cristal |
| --- | --- |
| ''' + item('tuned_signal_crystal_elite') + ''' **Elite** | 2 lingotes de netherite |
| ''' + item('tuned_signal_crystal_emerald') + ''' **Esmeralda** | 2 blocos de esmeralda + 1 Nether Star |
| ''' + item('tuned_signal_crystal_allthemodium') + ''' **Allthemodium¹** | 2 lingotes de Allthemodium + 1 Nether Star |
| ''' + item('tuned_signal_crystal_vibranium') + ''' **Vibranium¹** | 2 lingotes de Vibranium + 1 lingote de liga Vibranium-Allthemodium |
| ''' + item('tuned_signal_crystal_unobtainium') + ''' **Unobtainium¹** | 2 lingotes de Unobtainium + 1 lingote de liga Unobtainium-Vibranium |
| ''' + item('tuned_signal_crystal_ultimate') + ''' **Ultimate** | Com o Allthemodium: 2 lingotes de liga Unobtainium-Vibranium + 1 Nether Star. Sem ele: 1 bloco de netherite + 1 Nether Star + 1 Fragmento de Eco |

''' + ATM_NOTE['pt'] + '''

Sai cheio, mas com a mesma **integridade**: afinar não renova o cristal.

<RecipeFor id="wirelessautomate:tuned_signal_crystal_elite" />

## Carga e cristal Opaco

A barra do item mostra a carga. Sem carga, o cristal fica **Opaco** e não gera. Para recarregar, deixe-o na
câmara e ponha o catalisador no Núcleo (na mão, pela tela ou pela Porta de Cristais):

| Núcleo | Catalisador |
| --- | --- |
| Básico e Avançado | Redstone ou Pó de Ressonância |
| Elite e Esmeralda | Pó de Ressonância |
| Allthemodium para cima | Pó Carregado do tier |

Todos os Opacos recarregam juntos em 30 segundos, com 1 catalisador cada. O slot guarda um tipo de catalisador
por vez. A Porta de Cristais só devolve Opacos com o Núcleo sem catalisador.

## Integridade e cristal rachado

Cada cristal aguenta **8 cargas** (a de fábrica e 7 recargas); a dica do item mostra quantas faltam. Depois,
ele **racha**: não gera, não recarrega e não entra na câmara.

Para **refazer**: rachado + **4 Cristais de Sinal cheios** na bancada. Volta o mesmo cristal, cheio e inteiro (o
Afinado mantém o tier). No Núcleo Ultimate, um Afinado Ultimate rachado também pode ser queimado no **Eco**
([Ressonador de Sinal](signal-resonator.md)).

## Pó de Ressonância e Pó Carregado

<Row gap="8">
  ''' + item('resonance_dust') + '''
''' + '\n'.join('  ' + item('charged_dust_' + t) for t in CHARGED_TIERS) + '''
</Row>

**Pó de Ressonância**, sem forma: **1 redstone + 1 pó de glowstone + 1 Cristal de Sinal cheio** rendem 4.

<RecipeFor id="wirelessautomate:resonance_dust" />

**Pó Carregado**, sem forma: **8 Pós de Ressonância + 1 lingote do tier** rendem 8.

| Pó Carregado | Lingote |
| --- | --- |
| ''' + item('charged_dust_allthemodium') + ''' **Allthemodium¹** | 1 lingote de Allthemodium |
| ''' + item('charged_dust_vibranium') + ''' **Vibranium¹** | 1 lingote de Vibranium |
| ''' + item('charged_dust_unobtainium') + ''' **Unobtainium¹** | 1 lingote de Unobtainium |
| ''' + item('charged_dust_ultimate') + ''' **Ultimate** | Com o Allthemodium: 1 lingote de liga Unobtainium-Vibranium. Sem ele: 1 lingote de netherite |

''' + ATM_NOTE['pt'] + '''

## As peças do Coração

Os **Corações do Ressonador** saem destas peças, na ordem. O JEI mostra a posição dos ingredientes.

| Peça | Receita | Rende |
| --- | --- | --- |
| ''' + item('signal_alloy_raw') + ''' **1. Liga de Sinal crua** | Bancada, sem forma: 2 Cristais de Sinal cheios + 2 lingotes de ferro + 1 redstone | 2 |
| ''' + item('signal_alloy_ingot') + ''' **2. Lingote de Liga de Sinal** | **Alto-forno**: 1 Liga de Sinal crua | 1 |
| ''' + item('resonance_plate') + ''' **3. Placa de Ressonância** | 3 Lingotes de Liga de Sinal em linha | 1 |
| ''' + item('resonance_wire') + ''' **4. Fio de Ressonância** | 1 Lingote de Liga de Sinal entre 2 lingotes de cobre, com 1 lingote de ouro embaixo | 4 |
| ''' + item('resonance_coil') + ''' **5. Bobina de Ressonância** | 8 Fios em volta de 1 bloco de ferro | 1 |
| ''' + item('harmonic_lens') + ''' **6. Lente Harmônica** | Bancada, sem forma: 1 Cristal Lapidado cheio + 2 diamantes + 4 Vidros de Ressonância | 1 |
| ''' + item('phase_circuit') + ''' **7. Circuito de Fase** | 1 Placa no centro, 2 blocos de redstone, 2 lingotes de ouro e 4 quartzos do Nether | 2 |
| ''' + item('phase_matrix') + ''' **8. Matriz de Fase** | 1 Lente no centro, 4 Bobinas nos cantos, 2 Circuitos e 2 Placas | 1 |
| ''' + item('field_stabilizer') + ''' **9. Estabilizador de Campo** | 4 Matrizes + 1 bloco de diamante + 2 obsidianas chorosas + 1 olho do ender | 1 |
| ''' + item('condensed_core') + ''' **10. Núcleo Condensado** | 4 Estabilizadores + 4 blocos de netherite + 1 Nether Star no centro | 1 |

<RecipeFor id="wirelessautomate:signal_alloy_ingot" />
<RecipeFor id="wirelessautomate:phase_matrix" />

## O Coração do Ressonador

<Row gap="8">
''' + HEART_ROW + '''
</Row>

Um por tier, sem forma. O Básico entra na receita do Núcleo de Ressonância; os outros são gastos ao subir o
Núcleo com o Cartão de Upgrade ([Ressonador de Sinal](signal-resonator.md)).

| Coração | Receita |
| --- | --- |
| ''' + item('resonator_heart_basic') + ''' **Básico** | 1 Matriz de Fase + 4 blocos de redstone |
| ''' + item('resonator_heart_advanced') + ''' **Avançado** | 2 Matrizes de Fase + 4 blocos de ouro |
| ''' + item('resonator_heart_elite') + ''' **Elite** | 3 Matrizes de Fase + 4 lingotes de netherite |
| ''' + item('resonator_heart_emerald') + ''' **Esmeralda** | 1 Estabilizador de Campo + 7 blocos de esmeralda + 1 Nether Star; com o Modern Industrialization: 1 Estabilizador de Campo + 6 blocos de esmeralda + 1 Nether Star + 1 Processing Unit |
| ''' + item('resonator_heart_allthemodium') + ''' **Allthemodium¹** | 2 Estabilizadores de Campo + 4 blocos de Allthemodium + 2 Nether Stars; com o MEGA Cells: 1 Estabilizador de Campo + 4 blocos de Allthemodium + 2 Nether Stars + 2 Cell Components 16M |
| ''' + item('resonator_heart_vibranium') + ''' **Vibranium¹** | 1 Núcleo Condensado + 4 blocos de Vibranium + 2 Nether Stars; com o Modern Industrialization e o Draconic Evolution: 1 Núcleo Condensado + 4 blocos de Vibranium + 1 Nether Star + 1 Processing Unit + 2 Chaotic Cores |
| ''' + item('resonator_heart_unobtainium') + ''' **Unobtainium¹** | 2 Núcleos Condensados + 4 blocos de Unobtainium + 3 Nether Stars; com o MEGA Cells e o Draconic Evolution: 1 Núcleo Condensado + 4 blocos de Unobtainium + 2 Nether Stars + 1 Cell Component 16M + 1 Chaotic Core |
| ''' + item('resonator_heart_ultimate') + ''' **Ultimate** | 3 Núcleos Condensados + 1 Moldura de Liga + 5 Nether Stars; com o MEGA Cells, o Draconic Evolution e o Modern Industrialization: 3 Cell Components 256M + 1 Cell Component 64M + 1 Quantum Circuit + 1 Chaotic Core + 1 Awakened Core + 1 Moldura de Liga + 1 Nether Star |

''' + ATM_NOTE['pt'] + '''

A ''' + item('alloy_frame') + ''' **Moldura de Liga** é um anel de 8 blocos em volta de 1 Nether Star: blocos de
liga Unobtainium-Vibranium com o Allthemodium, blocos de netherite sem ele.

<RecipeFor id="wirelessautomate:resonator_heart_basic" />
<RecipeFor id="wirelessautomate:alloy_frame" />

Os números são os padrões; o JEI mostra os do seu pack. Para montar o gerador, veja o
[Ressonador de Sinal](signal-resonator.md).
''', front('Signal Quartz and crystals', 'wirelessautomate:signal_crystal', 18, item_ids=[
    'wirelessautomate:' + i for i in QUARTZ_ITEMS]) + '''
# Signal Quartz and crystals

<Row gap="8">
  ''' + item('signal_quartz_ore') + '''
  ''' + item('deepslate_signal_quartz_ore') + '''
  ''' + item('signal_crystal') + '''
  ''' + item('cut_signal_crystal') + '''
  ''' + item('tuned_signal_crystal_elite') + '''
  ''' + item('resonance_dust') + '''
</Row>

The crystals are the fuel of the [Signal Resonator](signal-resonator.md), the mod's power generator.

## How to use them

1. Mine the ore with an iron pickaxe or better.
2. Put the crystals in the Core's chambers. From Elite up, use the tier's **Tuned** crystal.
3. **Dull** crystal (no charge): leave it in the chamber and put catalyst in the Core.
4. **Cracked** crystal: recast it on a crafting table with 4 full Signal Crystals.

## The ore

In the Overworld, in stone and deepslate, in small veins. Most common near **y = −16**.

| Tool | Drop |
| --- | --- |
| Iron pickaxe or better | 1 to 3 **Signal Crystals** and a bit of experience. **Fortune** raises the amount. |
| Silk Touch | The ore itself. |

## The crystals

| Crystal | Where it comes from |
| --- | --- |
| ''' + item('signal_crystal') + ''' **Signal Crystal** | The ore. |
| ''' + item('cut_signal_crystal') + ''' **Cut Signal Crystal** | Crafting table: four Signal Crystals in a cross around an Amethyst Shard. |
| ''' + item('tuned_signal_crystal_elite') + ''' **Tuned Signal Crystal** | Crafting table: a Cut crystal with the tier's material (below). One per tier, from Elite to Ultimate. |

How much each one yields in a chamber:

| Crystal | Basic or Advanced Core | Elite Core or higher |
| --- | --- | --- |
| **Signal Crystal** | 1× | 1/4× |
| **Cut** | 2× | 1/2× |
| **Tuned** for the Core's tier or higher | 2× | 2× |
| **Tuned** for a lower tier | 2× | 1/2× |

Cut and Tuned last twice as long. The higher the Core, the shorter a charge
([Signal Resonator](signal-resonator.md)). Recipes only accept **full**, uncracked crystals.

<RecipeFor id="wirelessautomate:cut_signal_crystal" />

## Tuning a crystal

<Row gap="8">
''' + TUNED_ROW + '''
</Row>

On a crafting table, shapeless: a **Cut** crystal (or a lower-tier Tuned one) and the tier's material.

| Tuned | Material, besides the crystal |
| --- | --- |
| ''' + item('tuned_signal_crystal_elite') + ''' **Elite** | 2 netherite ingots |
| ''' + item('tuned_signal_crystal_emerald') + ''' **Emerald** | 2 emerald blocks + 1 Nether Star |
| ''' + item('tuned_signal_crystal_allthemodium') + ''' **Allthemodium¹** | 2 Allthemodium ingots + 1 Nether Star |
| ''' + item('tuned_signal_crystal_vibranium') + ''' **Vibranium¹** | 2 Vibranium ingots + 1 Vibranium-Allthemodium alloy ingot |
| ''' + item('tuned_signal_crystal_unobtainium') + ''' **Unobtainium¹** | 2 Unobtainium ingots + 1 Unobtainium-Vibranium alloy ingot |
| ''' + item('tuned_signal_crystal_ultimate') + ''' **Ultimate** | With Allthemodium: 2 Unobtainium-Vibranium alloy ingots + 1 Nether Star. Without it: 1 netherite block + 1 Nether Star + 1 Echo Shard |

''' + ATM_NOTE['en'] + '''

It comes out full, but with the same **integrity**: tuning doesn't renew it.

<RecipeFor id="wirelessautomate:tuned_signal_crystal_elite" />

## Charge and Dull crystals

The item's bar shows the charge. With none left, the crystal turns **Dull** and generates nothing. To recharge
it, leave it in the chamber and put the catalyst in the Core (by hand, through the screen or the Crystal Port):

| Core | Catalyst |
| --- | --- |
| Basic and Advanced | Redstone or Resonance Dust |
| Elite and Emerald | Resonance Dust |
| Allthemodium and up | The tier's Charged Dust |

All Dull crystals recharge together in 30 seconds, 1 catalyst each. The slot holds one kind of catalyst at a
time. The Crystal Port only hands Dull crystals back when the Core has no catalyst.

## Integrity and cracked crystals

Each crystal takes **8 charges** (the first and 7 recharges); the tooltip shows how many are left. Then it
**cracks**: it doesn't generate, doesn't recharge and doesn't go into a chamber.

To **recast** it: cracked crystal + **4 full Signal Crystals** on a crafting table. The same crystal comes back,
full and whole (a Tuned one keeps its tier). In an Ultimate Core, a cracked Tuned Ultimate can also be burned in
the **Echo** ([Signal Resonator](signal-resonator.md)).

## Resonance Dust and Charged Dust

<Row gap="8">
  ''' + item('resonance_dust') + '''
''' + '\n'.join('  ' + item('charged_dust_' + t) for t in CHARGED_TIERS) + '''
</Row>

**Resonance Dust**, shapeless: **1 redstone + 1 glowstone dust + 1 full Signal Crystal** make 4.

<RecipeFor id="wirelessautomate:resonance_dust" />

**Charged Dust**, shapeless: **8 Resonance Dust + 1 ingot of the tier** make 8.

| Charged Dust | Ingot |
| --- | --- |
| ''' + item('charged_dust_allthemodium') + ''' **Allthemodium¹** | 1 Allthemodium ingot |
| ''' + item('charged_dust_vibranium') + ''' **Vibranium¹** | 1 Vibranium ingot |
| ''' + item('charged_dust_unobtainium') + ''' **Unobtainium¹** | 1 Unobtainium ingot |
| ''' + item('charged_dust_ultimate') + ''' **Ultimate** | With Allthemodium: 1 Unobtainium-Vibranium alloy ingot. Without it: 1 netherite ingot |

''' + ATM_NOTE['en'] + '''

## The Heart's parts

**Resonator Hearts** come from these parts, in order. JEI shows where the ingredients go.

| Part | Recipe | Makes |
| --- | --- | --- |
| ''' + item('signal_alloy_raw') + ''' **1. Raw Signal Alloy** | Crafting table, shapeless: 2 full Signal Crystals + 2 iron ingots + 1 redstone | 2 |
| ''' + item('signal_alloy_ingot') + ''' **2. Signal Alloy Ingot** | **Blast furnace**: 1 Raw Signal Alloy | 1 |
| ''' + item('resonance_plate') + ''' **3. Resonance Plate** | 3 Signal Alloy Ingots in a row | 1 |
| ''' + item('resonance_wire') + ''' **4. Resonance Wire** | 1 Signal Alloy Ingot between 2 copper ingots, with 1 gold ingot below | 4 |
| ''' + item('resonance_coil') + ''' **5. Resonance Coil** | 8 Wires around 1 iron block | 1 |
| ''' + item('harmonic_lens') + ''' **6. Harmonic Lens** | Crafting table, shapeless: 1 full Cut Signal Crystal + 2 diamonds + 4 Resonance Glass | 1 |
| ''' + item('phase_circuit') + ''' **7. Phase Circuit** | 1 Plate in the center, 2 redstone blocks, 2 gold ingots and 4 Nether quartz | 2 |
| ''' + item('phase_matrix') + ''' **8. Phase Matrix** | 1 Lens in the center, 4 Coils in the corners, 2 Circuits and 2 Plates | 1 |
| ''' + item('field_stabilizer') + ''' **9. Field Stabilizer** | 4 Matrices + 1 diamond block + 2 crying obsidian + 1 eye of ender | 1 |
| ''' + item('condensed_core') + ''' **10. Condensed Core** | 4 Stabilizers + 4 netherite blocks + 1 Nether Star in the center | 1 |

<RecipeFor id="wirelessautomate:signal_alloy_ingot" />
<RecipeFor id="wirelessautomate:phase_matrix" />

## The Resonator Heart

<Row gap="8">
''' + HEART_ROW + '''
</Row>

One per tier, shapeless. The Basic one goes into the Resonance Core's recipe; the others are used up raising the
Core with an Upgrade Card ([Signal Resonator](signal-resonator.md)).

| Heart | Recipe |
| --- | --- |
| ''' + item('resonator_heart_basic') + ''' **Basic** | 1 Phase Matrix + 4 redstone blocks |
| ''' + item('resonator_heart_advanced') + ''' **Advanced** | 2 Phase Matrices + 4 gold blocks |
| ''' + item('resonator_heart_elite') + ''' **Elite** | 3 Phase Matrices + 4 netherite ingots |
| ''' + item('resonator_heart_emerald') + ''' **Emerald** | 1 Field Stabilizer + 7 emerald blocks + 1 Nether Star; with Modern Industrialization: 1 Field Stabilizer + 6 emerald blocks + 1 Nether Star + 1 Processing Unit |
| ''' + item('resonator_heart_allthemodium') + ''' **Allthemodium¹** | 2 Field Stabilizers + 4 Allthemodium blocks + 2 Nether Stars; with MEGA Cells: 1 Field Stabilizer + 4 Allthemodium blocks + 2 Nether Stars + 2 Cell Components 16M |
| ''' + item('resonator_heart_vibranium') + ''' **Vibranium¹** | 1 Condensed Core + 4 Vibranium blocks + 2 Nether Stars; with Modern Industrialization and Draconic Evolution: 1 Condensed Core + 4 Vibranium blocks + 1 Nether Star + 1 Processing Unit + 2 Chaotic Cores |
| ''' + item('resonator_heart_unobtainium') + ''' **Unobtainium¹** | 2 Condensed Cores + 4 Unobtainium blocks + 3 Nether Stars; with MEGA Cells and Draconic Evolution: 1 Condensed Core + 4 Unobtainium blocks + 2 Nether Stars + 1 Cell Component 16M + 1 Chaotic Core |
| ''' + item('resonator_heart_ultimate') + ''' **Ultimate** | 3 Condensed Cores + 1 Alloy Frame + 5 Nether Stars; with MEGA Cells, Draconic Evolution and Modern Industrialization: 3 Cell Components 256M + 1 Cell Component 64M + 1 Quantum Circuit + 1 Chaotic Core + 1 Awakened Core + 1 Alloy Frame + 1 Nether Star |

''' + ATM_NOTE['en'] + '''

The ''' + item('alloy_frame') + ''' **Alloy Frame** is a ring of 8 blocks around 1 Nether Star: Unobtainium-Vibranium
alloy blocks with Allthemodium, netherite blocks without it.

<RecipeFor id="wirelessautomate:resonator_heart_basic" />
<RecipeFor id="wirelessautomate:alloy_frame" />

The numbers are the defaults; JEI shows your pack's. To build the generator, see the
[Signal Resonator](signal-resonator.md).
''')
