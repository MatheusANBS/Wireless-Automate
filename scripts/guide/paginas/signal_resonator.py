"""Página signal-resonator.md do guia."""
from guia_base import *  # noqa: F401,F403


def _scene_blocks():
    with open(os.path.join(ROOT, 'scripts/guide/resonator_scene.txt'), encoding='utf-8') as fh:
        return '\n'.join('  ' + line.strip() for line in fh if line.strip())


SCENE_RESONATOR = '''<GameScene zoom="3" interactive={true}>
''' + _scene_blocks() + '''
  <BlockAnnotation x="2" y="2" z="4" color="#f0603e">
    {CORE}
  </BlockAnnotation>
  <BlockAnnotation x="2" y="2" z="0" color="#45d6cc">
    {ENERGY}
  </BlockAnnotation>
  <BlockAnnotation x="4" y="2" z="2" color="#e2b347">
    {CRYSTALS}
  </BlockAnnotation>
  <BlockAnnotation x="2" y="4" z="2" color="#a06bff">
    {TUNER}
  </BlockAnnotation>
  <IsometricCamera yaw="{YAW}" pitch="30" />
</GameScene>'''


# Tiers do Ressonador na ordem do RouterTier: câmaras (tier + 1), FE por cristal (padrões do CoreMath), minutos que a
# carga do Lapidado ou do Afinado dura a 100% (CoreMath.DEFAULT_LIFE_MINUTES; o Cristal de Sinal dura a metade) e teto
# do Sintonizador em % (constantes do Tuning.ceiling). A geração máxima é câmaras × FE por cristal × 2.


RESONATOR_FE = [216, 6_912, 10_000, 160_000, 6_400_000, 6_700_000, 10_700_000, 268_435_456]


RESONATOR_LIFE = [20, 20, 15, 10, 5, 5, 4, 3]


RESONATOR_CEILING = [85, 88, 90, 92, 94, 95, 96, 98]


# O catalisador da recarga por tier do Núcleo (CatalystRule): redstone ou Pó até o Avançado, só Pó até a Esmeralda e
# o Pó Carregado do próprio tier do Allthemodium para cima.


CATALYST = {
    'pt': ['Redstone ou Pó de Ressonância'] * 2 + ['Pó de Ressonância'] * 2
    + ['Pó Carregado de Allthemodium', 'Pó Carregado de Vibranium', 'Pó Carregado de Unobtainium',
       'Pó Carregado Ultimate'],
    'en': ['Redstone or Resonance Dust'] * 2 + ['Resonance Dust'] * 2
    + ['Charged Allthemodium Dust', 'Charged Vibranium Dust', 'Charged Unobtainium Dust', 'Charged Ultimate Dust'],
}


def resonator_table(lang):
    head = {'pt': '| Tier do Núcleo | Câmaras | Geração máxima (FE/t) | Teto do Sintonizador |',
            'en': '| Core tier | Chambers | Max generation (FE/t) | Tuner ceiling |'}[lang]
    rows = [head, '| --- | --- | --- | --- |']
    for i in range(len(TIERS)):
        top = (i + 1) * RESONATOR_FE[i] * 2
        rows.append(f'| **{tier_name(lang, i)}** | {i + 1} | {num(lang, top)} | {RESONATOR_CEILING[i]}% |')
    return '\n'.join(rows) + '\n\n' + ATM_NOTE[lang]


def resonator_upkeep_table(lang):
    head = {'pt': '| Tier do Núcleo | Cristal que rende cheio | A carga dura | Catalisador da recarga |',
            'en': '| Core tier | Crystal at full yield | A charge lasts | Recharge catalyst |'}[lang]
    rows = [head, '| --- | --- | --- | --- |']
    for i in range(len(TIERS)):
        if i < 2:
            crystal = {'pt': 'Lapidado ou Afinado', 'en': 'Cut or Tuned'}[lang]
        elif i == len(TIERS) - 1:
            crystal = {'pt': 'Afinado Ultimate', 'en': 'Tuned Ultimate'}[lang]
        else:
            crystal = {'pt': f'Afinado {TIERS[i][1]} ou acima', 'en': f'Tuned {TIERS[i][2]} or higher'}[lang]
        rows.append(f'| **{tier_name(lang, i)}** | {crystal} | {RESONATOR_LIFE[i]} min | {CATALYST[lang][i]} |')
    return '\n'.join(rows) + '\n\n' + ATM_NOTE[lang]


RESONATOR_ANNOTATIONS = {
    'pt': dict(CORE='Núcleo de Ressonância: a tela e os cristais',
               ENERGY='Porta de Energia: o roteador tira a energia daqui',
               CRYSTALS='Porta de Cristais: entram cristais e catalisador, saem Opacos e rachados',
               TUNER='Sintonizador (opcional)', YAW='35'),
    'en': dict(CORE='Resonance Core: the screen and the crystals',
               ENERGY='Resonator Energy Port: a router pulls the energy here',
               CRYSTALS='Resonator Crystal Port: crystals and catalyst in, Dull and cracked ones out',
               TUNER='Resonator Tuner (optional)', YAW='35'),
}


PAGINA = page('signal-resonator.md', front('Ressonador de Sinal', 'wirelessautomate:resonance_core', 19, item_ids=[
    'wirelessautomate:resonator_casing', 'wirelessautomate:resonator_glass', 'wirelessautomate:resonance_core',
    'wirelessautomate:resonator_energy_port', 'wirelessautomate:resonator_crystal_port',
    'wirelessautomate:resonator_tuner']) + '''
# Ressonador de Sinal

<ItemImage id="wirelessautomate:resonance_core" scale="2" float="left" />

Gerador de vários blocos: cristais vibram numa caixa e você **sintoniza** o Núcleo para tirar o máximo de FE.
Cristais, catalisadores e Corações vêm do [Quartzo de Sinal](signal-quartz.md).

<br clear="all" />

## Como usar

1. Faça o **Núcleo de Ressonância** (a receita pede um **Coração do Ressonador Básico**) e coloque-o.
2. Clique nele com a mão vazia: a estrutura aparece **projetada** no mundo. **Shift + roda do mouse** troca a montagem.
3. Monte a caixa em volta seguindo a projeção.
4. Ponha um cristal: clique no Núcleo com ele na mão.
5. Clique no Núcleo montado para abrir a tela e gire o dial até acender **EM SINTONIA**.
6. Prenda um [Roteador](router.md) na **Porta de Energia**, face em **Extrai** na aba **Energia**.

## Montar

''' + fill(SCENE_RESONATOR, **RESONATOR_ANNOTATIONS['pt']) + '''

A cena mostra a montagem **Vitrine**. Gire-a com o mouse.

- Caixa **5×5×5 oca**: **Carcaça de Porcelana** nas arestas e nos cantos; o miolo 3×3×3 fica vazio.
- No miolo das faces (nunca nas arestas): **um Núcleo**, **pelo menos uma Porta de Energia**, **pelo menos uma Porta
  de Cristais**, no máximo um **Sintonizador** (opcional) e Vidro no resto.
- Exemplos: **Vitrine** (janela de Vidro 3×3 numa lateral) e **Faixa** (três vidros no alto de cada lateral).
- Dois Ressonadores **não dividem parede**.

Se não monta, clique no Núcleo: a barra de ação diz o que impede e o bloco errado fica contornado.

| Bloco | Papel |
| --- | --- |
| ''' + item('resonator_casing') + ''' **Carcaça de Porcelana** | A casca da caixa. |
| ''' + item('resonator_glass') + ''' **Vidro de Ressonância** | A janela para a câmara. |
| ''' + item('resonance_core') + ''' **Núcleo de Ressonância** | Tela, cristais e energia gerada. |
| ''' + item('resonator_energy_port') + ''' **Porta de Energia** | Só tira energia. |
| ''' + item('resonator_crystal_port') + ''' **Porta de Cristais** | Recebe cristais e catalisador; devolve os Opacos e os rachados. |
| ''' + item('resonator_tuner') + ''' **Sintonizador** | Opcional: sintoniza sozinho. |

## Cristais e catalisador

O Núcleo tem câmaras para cristais: 1 no Básico, até 8 no Ultimate.

| Como | O que fazer |
| --- | --- |
| **Clique** | Cristal ou catalisador na mão, clique no Núcleo. |
| **Tela** | Slots de cristais e de catalisador. |
| **Porta de Cristais** | Um roteador põe cristais e catalisador e tira os Opacos e os rachados. |

- Tirar o último cristal: clique com **Shift e as duas mãos vazias**.
- Do Elite para cima, use o **Cristal Afinado** do tier: os outros rendem bem menos.
- O **catalisador** só recarrega os Opacos, e cada tier aceita o seu (a tela diz qual).
- Cada recarga gasta **integridade**: o cristal racha depois de 8 cargas. Mais no [Quartzo de Sinal](signal-quartz.md).

## Sintonizar

O Ressonador tem uma **estação** escondida, e o **dial** precisa estar em cima dela.

- Gire o dial com os botões (**±1 MHz** e **±0,05 MHz**) ou com a **roda do mouse** sobre ele (±0,05 MHz).
- **EM SINTONIA** aceso: geração a 100%. Quanto mais longe, menos energia, até **zero**.
- **sem sinal: varra o dial**: você está longe; varra até o ponteiro aparecer.
- A estação **escorrega com o tempo**: volte de vez em quando e acerte de novo.

Com um **Sintonizador** na estrutura, o botão **Sintonizador** liga o **modo automático**: o dial segue a estação
sozinho, mas a eficiência fica limitada por um **teto** do tier (tabela abaixo). Para girar à mão, volte para **Manual**.

## Interferência

Cada outro Núcleo montado a até **32 blocos** (pela maior das distâncias em x, y e z), na mesma dimensão e carregado, tira **15%** da
eficiência, até **60%**. Para gerar no máximo, deixe mais de 32 blocos entre eles.

## Eco Ressonante

Só no Núcleo **Ultimate**. Um **Cristal Afinado Ultimate rachado** no slot **Eco** é queimado e soma **1 de Eco**. Cada
Eco dá **+5%** de geração, sem teto.

Atenção: um Afinado Ultimate rachado que chega pela Porta de Cristais também vai para o Eco. O Núcleo **guarda o
Eco** quando é quebrado.

## A câmara

Pelo vidro: em sintonia, a figura de luz **fecha** e os cristais sobem e descem **juntos**. Fora, ela vira um
**rabisco**; com geração zero, some. Cristal rachado fica escuro.

## Energia

Um roteador do mesmo tier do Núcleo dá conta da geração cheia. O Núcleo guarda uns 10 segundos de geração; com a
reserva cheia, ele **para de gerar e não gasta cristal**. Ele só trabalha com o chunk carregado: use o
[Upgrade de Chunk Loading](chunk-loading.md) no roteador da Porta.

## Tiers

Suba o Núcleo com os [Cartões de Upgrade](upgrade-cards.md) (Shift + clique), sem desmontar. Cada degrau pede também o
**Coração do Ressonador do tier de destino** no inventário; o clique gasta os dois. Corações no
[Quartzo de Sinal](signal-quartz.md).

Geração máxima: cristal que rende cheio em todas as câmaras, **EM SINTONIA**, sem interferência.

''' + resonator_table('pt') + '''

Manutenção por tier: o cristal certo, quanto a carga dura (a 100%) e o catalisador. O Cristal de Sinal dura a metade.

''' + resonator_upkeep_table('pt') + '''

Os números são os padrões; o JEI mostra os do seu pack.

## Receitas

<RecipeFor id="wirelessautomate:resonator_casing" />
<RecipeFor id="wirelessautomate:resonator_glass" />
<RecipeFor id="wirelessautomate:resonance_core" />
<RecipeFor id="wirelessautomate:resonator_energy_port" />
<RecipeFor id="wirelessautomate:resonator_crystal_port" />
<RecipeFor id="wirelessautomate:resonator_tuner" />
''', front('Signal Resonator', 'wirelessautomate:resonance_core', 19, item_ids=[
    'wirelessautomate:resonator_casing', 'wirelessautomate:resonator_glass', 'wirelessautomate:resonance_core',
    'wirelessautomate:resonator_energy_port', 'wirelessautomate:resonator_crystal_port',
    'wirelessautomate:resonator_tuner']) + '''
# Signal Resonator

<ItemImage id="wirelessautomate:resonance_core" scale="2" float="left" />

A multiblock generator: crystals vibrate in a box and you **tune** the Core to pull the most FE out of them.
Crystals, catalysts and Hearts come from [Signal Quartz](signal-quartz.md).

<br clear="all" />

## How to use it

1. Craft the **Resonance Core** (its recipe takes a **Basic Resonator Heart**) and place it.
2. Click it with an empty hand: the structure shows up **projected** in the world. **Shift + mouse wheel** switches
   the build.
3. Build the box around it following the projection.
4. Add a crystal: click the Core with it in hand.
5. Click the formed Core to open its screen and turn the dial until **IN TUNE** lights up.
6. Attach a [Router](router.md) to the **Energy Port**, face on **Extract** in the **Energy** tab.

## Building it

''' + fill(SCENE_RESONATOR, **RESONATOR_ANNOTATIONS['en']) + '''

The scene shows the **Showcase** build. Rotate it with the mouse.

- A **hollow 5×5×5 box**: **Porcelain Casing** on the edges and corners; the 3×3×3 inside stays empty.
- On face positions (never on edges): **one Core**, **at least one Energy Port**, **at least one Crystal Port**, at most
  one **Tuner** (optional) and Glass for the rest.
- Examples: **Showcase** (a 3×3 Glass window on one side) and **Band** (three glass blocks along the top of each
  side).
- Two Resonators **can't share a wall**.

If it doesn't form, click the Core: the action bar says what's in the way and the wrong block is outlined.

| Block | Role |
| --- | --- |
| ''' + item('resonator_casing') + ''' **Porcelain Casing** | The shell of the box. |
| ''' + item('resonator_glass') + ''' **Resonance Glass** | The window onto the chamber. |
| ''' + item('resonance_core') + ''' **Resonance Core** | Screen, crystals and generated power. |
| ''' + item('resonator_energy_port') + ''' **Resonator Energy Port** | Only pulls energy out. |
| ''' + item('resonator_crystal_port') + ''' **Resonator Crystal Port** | Takes crystals and catalyst; hands back Dull and cracked ones. |
| ''' + item('resonator_tuner') + ''' **Resonator Tuner** | Optional: tunes by itself. |

## Crystals and catalyst

The Core has chambers for crystals: 1 on Basic, up to 8 on Ultimate.

| How | What to do |
| --- | --- |
| **Click** | Crystal or catalyst in hand, click the Core. |
| **Screen** | Crystal slots and catalyst slot. |
| **Crystal Port** | A router puts crystals and catalyst in and takes Dull and cracked ones out. |

- To take the last crystal out: click with **Shift and both hands empty**.
- From Elite up, use the tier's **Tuned Signal Crystal**: others yield much less.
- The **catalyst** only recharges Dull crystals, and each tier takes its own (the screen says which).
- Each recharge uses up **integrity**: the crystal cracks after 8 charges. More on [Signal Quartz](signal-quartz.md).

## Tuning

The Resonator has a hidden **station**, and the **dial** has to sit right on it.

- Turn the dial with the buttons (**±1 MHz** and **±0.05 MHz**) or the **mouse wheel** over it (±0.05 MHz).
- **IN TUNE** lit: generation at 100%. The farther off, the less power, down to **zero**.
- **no signal: sweep the dial**: you're far off; sweep until the pointer shows up.
- The station **drifts over time**: come back now and then and set it again.

With a **Tuner** in the structure, the **Tuner** button turns on **automatic mode**: the dial follows the station by
itself, but efficiency is capped by a tier **ceiling** (table below). To turn it by hand, switch back to **Manual**.

## Interference

Every other formed Core within **32 blocks** (by the largest of the x, y and z distances), in the same dimension and loaded, takes **15%** of the
efficiency, up to **60%**. To generate at full, keep them more than 32 blocks apart.

## Resonant Echo

Only on the **Ultimate** Core. A **cracked Tuned Ultimate crystal** in the **Echo** slot burns and adds **1 Echo**.
Each Echo gives **+5%** generation, with no ceiling.

Watch out: a cracked Tuned Ultimate crystal that arrives through the Crystal Port goes to the Echo too. The Core
**keeps its Echo** when broken.

## The chamber

Through the glass: in tune, the figure of light **closes** and the crystals rise and fall **together**. Out of tune,
it turns into a **scribble**; at zero generation, it disappears. A cracked crystal goes dark.

## Power

A router of the Core's tier keeps up with full generation. The Core stores about 10 seconds of generation; when the
reserve is full, it **stops generating and doesn't wear the crystals**. It only works while its chunk is loaded: use
the [Chunk Loading Upgrade](chunk-loading.md) on the Port's router.

## Tiers

Upgrade the Core with [Upgrade Cards](upgrade-cards.md) (Shift + click), without taking it apart. Each step also needs
the **Resonator Heart of the target tier** in your inventory; the click uses up both. Hearts are on
[Signal Quartz](signal-quartz.md).

Max generation: a full-yield crystal in every chamber, **IN TUNE**, no interference.

''' + resonator_table('en') + '''

Upkeep per tier: the right crystal, how long a charge lasts (at 100%) and the catalyst. A Signal Crystal lasts half
as long.

''' + resonator_upkeep_table('en') + '''

The numbers are the defaults; JEI shows your pack's.

## Recipes

<RecipeFor id="wirelessautomate:resonator_casing" />
<RecipeFor id="wirelessautomate:resonator_glass" />
<RecipeFor id="wirelessautomate:resonance_core" />
<RecipeFor id="wirelessautomate:resonator_energy_port" />
<RecipeFor id="wirelessautomate:resonator_crystal_port" />
<RecipeFor id="wirelessautomate:resonator_tuner" />
''')
