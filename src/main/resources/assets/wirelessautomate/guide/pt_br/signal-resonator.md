---
group: energia
navigation:
  title: Ressonador de Sinal
  icon: wirelessautomate:resonance_core
  parent: index.md
  position: 19
item_ids:
  - wirelessautomate:resonator_casing
  - wirelessautomate:resonator_column
  - wirelessautomate:resonator_cradle
  - wirelessautomate:resonance_core
  - wirelessautomate:resonator_energy_port
  - wirelessautomate:resonator_crystal_port
  - wirelessautomate:resonator_tuner
---


# Ressonador de Sinal

<ItemImage id="wirelessautomate:resonance_core" scale="2" float="left" />

Gerador de vários blocos, a **Armilar**: cristais vibram no Berço do Sol e você **sintoniza** o Núcleo para tirar o máximo de FE.
Cristais, catalisadores e Corações vêm do [Quartzo de Sinal](signal-quartz.md).

<br clear="all" />

## Como usar

1. Faça o **Núcleo de Ressonância** (a receita pede um **Coração do Ressonador Básico**) e coloque-o.
2. Clique nele com a mão vazia: a estrutura aparece **projetada** no mundo.
3. Monte a estrutura seguindo a projeção.
4. Ponha um cristal: clique no Núcleo com ele na mão.
5. Clique no Núcleo montado para abrir a tela e gire o dial até acender **EM SINTONIA**.
6. Leve a energia embora: uma máquina ou bateria ao lado da **Porta de Energia** já recebe sozinha, ou prenda um [Roteador](router.md) nela, face em **Extrai** na aba **Energia**.

## Montar

<GameScene zoom="3" interactive={true}>
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="0" p:facing="south" p:position="p00" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="1" p:facing="south" p:position="p01" />
  <Block id="wirelessautomate:resonator_crystal_port" x="0" y="0" z="2" p:facing="west" p:formed="true" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="3" p:facing="south" p:position="p03" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="4" p:facing="south" p:position="p04" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="0" p:facing="south" p:position="p10" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="1" p:facing="south" p:position="p11" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="2" p:facing="south" p:position="p12" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="3" p:facing="south" p:position="p13" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="4" p:facing="south" p:position="p14" />
  <Block id="wirelessautomate:resonator_column" x="1" y="1" z="1" p:corner="nw" p:part="lower" />
  <Block id="wirelessautomate:resonator_column" x="1" y="1" z="3" p:corner="sw" p:part="lower" />
  <Block id="wirelessautomate:resonator_column" x="1" y="2" z="1" p:corner="nw" p:part="upper" />
  <Block id="wirelessautomate:resonator_column" x="1" y="2" z="3" p:corner="sw" p:part="upper" />
  <Block id="wirelessautomate:resonator_tuner" x="2" y="0" z="0" p:facing="north" p:formed="true" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="0" z="1" p:facing="south" p:position="p21" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="0" z="2" p:facing="south" p:position="p22" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="0" z="3" p:facing="south" p:position="p23" />
  <Block id="wirelessautomate:resonance_core" x="2" y="0" z="4" p:facing="south" p:formed="true" p:tier="emerald" />
  <Block id="wirelessautomate:resonator_cradle" x="2" y="1" z="2" p:part="lower" />
  <Block id="wirelessautomate:resonator_cradle" x="2" y="2" z="2" p:part="upper" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="0" p:facing="south" p:position="p30" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="1" p:facing="south" p:position="p31" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="2" p:facing="south" p:position="p32" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="3" p:facing="south" p:position="p33" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="4" p:facing="south" p:position="p34" />
  <Block id="wirelessautomate:resonator_column" x="3" y="1" z="1" p:corner="ne" p:part="lower" />
  <Block id="wirelessautomate:resonator_column" x="3" y="1" z="3" p:corner="se" p:part="lower" />
  <Block id="wirelessautomate:resonator_column" x="3" y="2" z="1" p:corner="ne" p:part="upper" />
  <Block id="wirelessautomate:resonator_column" x="3" y="2" z="3" p:corner="se" p:part="upper" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="0" p:facing="south" p:position="p40" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="1" p:facing="south" p:position="p41" />
  <Block id="wirelessautomate:resonator_energy_port" x="4" y="0" z="2" p:facing="east" p:formed="true" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="3" p:facing="south" p:position="p43" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="4" p:facing="south" p:position="p44" />
  <BlockAnnotation x="2" y="0" z="4" color="#f0603e">
    Núcleo de Ressonância: a tela e os cristais, na frente
  </BlockAnnotation>
  <BlockAnnotation x="4" y="0" z="2" color="#45d6cc">
    Porta de Energia: empurra a energia aos vizinhos, e o roteador também tira daqui
  </BlockAnnotation>
  <BlockAnnotation x="0" y="0" z="2" color="#e2b347">
    Porta de Cristais: entram cristais e catalisador, saem Opacos e rachados
  </BlockAnnotation>
  <BlockAnnotation x="1" y="1" z="3" color="#a06bff">
    Coluna do Ressonador: uma em cada diagonal, nas camadas 2 e 3
  </BlockAnnotation>
  <BlockAnnotation x="2" y="1" z="2" color="#7fb8ff">
    Berço do Sol: o centro das camadas 2 e 3
  </BlockAnnotation>
  <IsometricCamera yaw="315" pitch="30" />
</GameScene>

A cena mostra a estrutura pronta. Gire-a com o mouse. São **35 blocos** numa caixa de 5×5×5, de cinco camadas; a frente é o lado do Núcleo.

1. **Camada 1, o chão:** o **Núcleo** no meio da frente, a **Porta de Energia** à direita e a **Porta de Cristais** à esquerda, vistas de frente. O **Sintonizador** (opcional) fica atrás, e a casa dele leva uma **Laje** quando ele falta. O resto do chão é **Laje**.
2. **Camadas 2 e 3:** uma **Coluna** em cada uma das quatro diagonais em volta do centro e um **Berço do Sol** no centro, em cada camada. As outras casas dessas camadas ficam vazias.
3. **Camadas 4 e 5:** vazias. A caixa que o Núcleo confere tem as cinco camadas, e qualquer bloco nas duas de cima impede a montagem.

- Dois Ressonadores **não dividem blocos**.

Se não monta, clique no Núcleo: a barra de ação diz o que impede e o bloco errado fica contornado.

| Bloco | Papel |
| --- | --- |
| <ItemImage id="wirelessautomate:resonator_casing" /> **Laje do Ressonador** | O chão da estrutura. |
| <ItemImage id="wirelessautomate:resonator_column" /> **Coluna do Ressonador** | As quatro diagonais, nas camadas 2 e 3. |
| <ItemImage id="wirelessautomate:resonator_cradle" /> **Berço do Sol** | O centro das camadas 2 e 3. |
| <ItemImage id="wirelessautomate:resonance_core" /> **Núcleo de Ressonância** | Tela, cristais e energia gerada. |
| <ItemImage id="wirelessautomate:resonator_energy_port" /> **Porta de Energia** | Empurra a energia, a cada tick, para as máquinas e baterias coladas nela (não para a própria Armilar), e deixa o roteador tirar. |
| <ItemImage id="wirelessautomate:resonator_crystal_port" /> **Porta de Cristais** | Recebe cristais e catalisador; devolve os Opacos e os rachados. |
| <ItemImage id="wirelessautomate:resonator_tuner" /> **Sintonizador** | Opcional: sintoniza sozinho. |

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

## Energia

A **Porta de Energia** empurra a cada tick o que o Núcleo gerou, até a geração máxima do tier, para quem está colado
nela e aceita energia (uma bateria do mod recebe tudo numa vez, mesmo acima de 2 bilhões de FE por tick); um roteador do
mesmo tier do Núcleo também dá conta da geração cheia. Com uma máquina colada e um roteador na mesma Porta, o
roteador tira primeiro em cada tick; como o empurrão esvazia a reserva a cada tick, nada acumula para o roteador
levar depois. O Núcleo guarda uns 10 segundos de geração; com a
reserva cheia, ele **para de gerar e não gasta cristal**. Ele só trabalha com o chunk carregado: use o
[Upgrade de Chunk Loading](chunk-loading.md) no roteador da Porta.

## Tiers

Suba o Núcleo com os [Cartões de Upgrade](upgrade-cards.md) (Shift + clique), sem desmontar. Cada degrau pede também o
**Coração do Ressonador do tier de destino** no inventário; o clique gasta os dois. Corações no
[Quartzo de Sinal](signal-quartz.md).

Geração máxima: cristal que rende cheio em todas as câmaras, **EM SINTONIA**, sem interferência.

| Tier do Núcleo | Câmaras | Geração máxima (FE/t) | Teto do Sintonizador |
| --- | --- | --- | --- |
| **Básico** | 1 | 432 | 85% |
| **Avançado** | 2 | 27.648 | 88% |
| **Elite** | 3 | 60.000 | 90% |
| **Esmeralda** | 4 | 1.280.000 | 92% |
| **Allthemodium¹** | 5 | 64.000.000 | 94% |
| **Vibranium¹** | 6 | 80.400.000 | 95% |
| **Unobtainium¹** | 7 | 149.800.000 | 96% |
| **Ultimate** | 8 | 4.294.967.296 | 98% |

¹ Só com o mod Allthemodium.

Manutenção por tier: o cristal certo, quanto a carga dura (a 100%) e o catalisador. O Cristal de Sinal dura a metade.

| Tier do Núcleo | Cristal que rende cheio | A carga dura | Catalisador da recarga |
| --- | --- | --- | --- |
| **Básico** | Lapidado ou Afinado | 20 min | Redstone ou Pó de Ressonância |
| **Avançado** | Lapidado ou Afinado | 20 min | Redstone ou Pó de Ressonância |
| **Elite** | Afinado Elite ou acima | 15 min | Pó de Ressonância |
| **Esmeralda** | Afinado Esmeralda ou acima | 10 min | Pó de Ressonância |
| **Allthemodium¹** | Afinado Allthemodium ou acima | 5 min | Pó Carregado de Allthemodium |
| **Vibranium¹** | Afinado Vibranium ou acima | 5 min | Pó Carregado de Vibranium |
| **Unobtainium¹** | Afinado Unobtainium ou acima | 4 min | Pó Carregado de Unobtainium |
| **Ultimate** | Afinado Ultimate | 3 min | Pó Carregado Ultimate |

¹ Só com o mod Allthemodium.

Os números são os padrões; o JEI mostra os do seu pack.

## Receitas

<RecipeFor id="wirelessautomate:resonator_casing" />
<RecipeFor id="wirelessautomate:resonator_column" />
<RecipeFor id="wirelessautomate:resonator_cradle" />
<RecipeFor id="wirelessautomate:resonance_core" />
<RecipeFor id="wirelessautomate:resonator_energy_port" />
<RecipeFor id="wirelessautomate:resonator_crystal_port" />
<RecipeFor id="wirelessautomate:resonator_tuner" />
