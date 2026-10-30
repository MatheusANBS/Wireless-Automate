"""Página crystal-repairer.md do guia."""
from guia_base import *  # noqa: F401,F403


TABLE_REPAIR_PT = '''| Cristal | FE por reparo |
| --- | --- |
| Cristal de Sinal | 432 |
| Cristal Lapidado | 13.824 |
| Afinado Elite | 20.000 |
| Afinado Esmeralda | 320.000 |
| Afinado Allthemodium | 12.800.000 |
| Afinado Vibranium | 13.400.000 |
| Afinado Unobtainium | 21.400.000 |
| Afinado Ultimate | 536.870.912 |'''


TABLE_REPAIR_EN = '''| Crystal | FE per repair |
| --- | --- |
| Signal Crystal | 432 |
| Cut Crystal | 13,824 |
| Tuned Elite | 20,000 |
| Tuned Emerald | 320,000 |
| Tuned Allthemodium | 12,800,000 |
| Tuned Vibranium | 13,400,000 |
| Tuned Unobtainium | 21,400,000 |
| Tuned Ultimate | 536,870,912 |'''


PAGINA = page('crystal-repairer.md', front('Reparador de Cristais', 'wirelessautomate:crystal_repairer', 20, item_ids=[
    'wirelessautomate:crystal_repairer']) + '''
# Reparador de Cristais

<ItemImage id="wirelessautomate:crystal_repairer" scale="2" float="left" />

Conserta os cristais rachados do [Ressonador de Sinal](signal-resonator.md): o cristal volta com carga e integridade
cheias. Faz o mesmo que a bancada, mas com **até oito reparos ao mesmo tempo**. Não tem tier e não precisa ficar perto
do Ressonador.

<br clear="all" />

## Como usar

1. Coloque o Reparador e dê **energia** por **qualquer face** (cabo, roteador em **Insere** ou Bateria Wireless). Nenhum
   roteador tira energia dele.
2. Ponha os **cristais rachados** nos oito slots de entrada e os **Cristais de Sinal cheios** no slot do meio. Vale
   pela tela, por roteador ou por funil.
3. Cada linha começa sozinha quando tem um rachado, **4 Cristais de Sinal cheios** e a energia. O reparo leva
   **7 segundos**.
4. Tire os cristais consertados da saída. Saída cheia: a linha espera.

A tela mostra o progresso de cada linha e o que falta (**sem energia**, **faltam cristais** ou **saída cheia**).

## O custo

Cada reparo gasta **4 Cristais de Sinal cheios** e uma quantidade de **FE** que depende do cristal:

''' + TABLE_REPAIR_PT + '''

Os números são os padrões; o JEI mostra os do seu pack. O reparo só começa com o custo inteiro guardado. Quebrar o
bloco no meio de um reparo **perde os 4 cristais e a energia** já gastos nele.

## Receita

<RecipeFor id="wirelessautomate:crystal_repairer" />
''', front('Crystal Repairer', 'wirelessautomate:crystal_repairer', 20, item_ids=[
    'wirelessautomate:crystal_repairer']) + '''
# Crystal Repairer

<ItemImage id="wirelessautomate:crystal_repairer" scale="2" float="left" />

Fixes the cracked crystals of the [Signal Resonator](signal-resonator.md): the crystal comes back with full charge and
integrity. It does the same as the crafting table, but with **up to eight repairs at the same time**. It has no tier and
doesn't need to be near the Resonator.

<br clear="all" />

## How to use

1. Place the Repairer and give it **energy** through **any face** (a cable, a router on **Insert** or a Wireless
   Battery). No router pulls energy out of it.
2. Put the **cracked crystals** in the eight input slots and the **full Signal Crystals** in the middle slot. Use the
   screen, a router or a hopper.
3. Each line starts by itself when it has a cracked crystal, **4 full Signal Crystals** and the energy. A repair takes
   **7 seconds**.
4. Take the fixed crystals from the output. Output full: the line waits.

The screen shows each line's progress and what is missing (**no energy**, **missing crystals** or **output full**).

## The cost

Each repair spends **4 full Signal Crystals** and an amount of **FE** that depends on the crystal:

''' + TABLE_REPAIR_EN + '''

The numbers are the defaults; JEI shows your pack's. A repair only starts once its whole cost is stored. Breaking the
block in the middle of a repair **loses the 4 crystals and the energy** already spent on it.

## Recipe

<RecipeFor id="wirelessautomate:crystal_repairer" />
''')
