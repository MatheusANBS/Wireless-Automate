---
navigation:
  title: Ressonador de Sinal
  icon: wirelessautomate:resonance_core
  parent: index.md
  position: 19
item_ids:
  - wirelessautomate:resonator_casing
  - wirelessautomate:resonator_glass
  - wirelessautomate:resonance_core
  - wirelessautomate:resonator_energy_port
  - wirelessautomate:resonator_crystal_port
  - wirelessautomate:resonator_tuner
---


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

<GameScene zoom="3" interactive={true}>
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="0" p:position="nnn" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="1" p:position="nnc" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="2" p:position="nnc" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="3" p:position="nnc" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="4" p:position="nnp" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="1" z="0" p:position="ncn" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="1" z="1" p:face="west" p:connections="3" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="1" z="2" p:face="west" p:connections="11" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="1" z="3" p:face="west" p:connections="9" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="1" z="4" p:position="ncp" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="2" z="0" p:position="ncn" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="2" z="1" p:face="west" p:connections="7" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="2" z="2" p:face="west" p:connections="15" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="2" z="3" p:face="west" p:connections="13" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="2" z="4" p:position="ncp" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="3" z="0" p:position="ncn" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="3" z="1" p:face="west" p:connections="6" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="3" z="2" p:face="west" p:connections="14" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="3" z="3" p:face="west" p:connections="12" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="3" z="4" p:position="ncp" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="4" z="0" p:position="npn" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="4" z="1" p:position="npc" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="4" z="2" p:position="npc" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="4" z="3" p:position="npc" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="4" z="4" p:position="npp" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="0" p:position="cnn" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="1" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="2" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="3" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="4" p:position="cnp" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="1" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="1" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="2" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="2" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="3" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="3" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="4" z="0" p:position="cpn" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="4" z="1" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="4" z="2" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="4" z="3" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="4" z="4" p:position="cpp" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="0" z="0" p:position="cnn" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="0" z="1" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="0" z="2" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="0" z="3" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="0" z="4" p:position="cnp" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="1" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="1" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_energy_port" x="2" y="2" z="0" p:facing="north" p:formed="true" />
  <Block id="wirelessautomate:resonance_core" x="2" y="2" z="4" p:facing="south" p:formed="true" p:tier="emerald" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="3" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="3" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="4" z="0" p:position="cpn" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="4" z="1" p:position="cpc" />
  <Block id="wirelessautomate:resonator_tuner" x="2" y="4" z="2" p:facing="up" p:formed="true" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="4" z="3" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="4" z="4" p:position="cpp" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="0" p:position="cnn" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="1" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="2" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="3" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="4" p:position="cnp" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="1" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="1" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="2" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="2" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="3" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="3" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="4" z="0" p:position="cpn" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="4" z="1" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="4" z="2" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="4" z="3" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="4" z="4" p:position="cpp" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="0" p:position="pnn" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="1" p:position="pnc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="2" p:position="pnc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="3" p:position="pnc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="4" p:position="pnp" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="1" z="0" p:position="pcn" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="1" z="1" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="1" z="2" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="1" z="3" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="1" z="4" p:position="pcp" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="2" z="0" p:position="pcn" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="2" z="1" p:position="pcc" />
  <Block id="wirelessautomate:resonator_crystal_port" x="4" y="2" z="2" p:facing="east" p:formed="true" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="2" z="3" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="2" z="4" p:position="pcp" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="3" z="0" p:position="pcn" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="3" z="1" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="3" z="2" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="3" z="3" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="3" z="4" p:position="pcp" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="4" z="0" p:position="ppn" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="4" z="1" p:position="ppc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="4" z="2" p:position="ppc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="4" z="3" p:position="ppc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="4" z="4" p:position="ppp" />
  <BlockAnnotation x="2" y="2" z="4" color="#f0603e">
    Núcleo de Ressonância: a tela e os cristais
  </BlockAnnotation>
  <BlockAnnotation x="2" y="2" z="0" color="#45d6cc">
    Porta de Energia: o roteador tira a energia daqui
  </BlockAnnotation>
  <BlockAnnotation x="4" y="2" z="2" color="#e2b347">
    Porta de Cristais: entram cristais e catalisador, saem Opacos e rachados
  </BlockAnnotation>
  <BlockAnnotation x="2" y="4" z="2" color="#a06bff">
    Sintonizador (opcional)
  </BlockAnnotation>
  <IsometricCamera yaw="35" pitch="30" />
</GameScene>

A cena mostra a montagem **Vitrine**. Gire-a com o mouse.

- Caixa **5×5×5 oca**: **Carcaça de Porcelana** nas arestas e nos cantos; o miolo 3×3×3 fica vazio.
- No miolo das faces (nunca nas arestas): **um Núcleo**, **pelo menos uma Porta de Energia**, **pelo menos uma Porta
  de Cristais**, no máximo um **Sintonizador** (opcional) e Vidro no resto.
- Exemplos: **Vitrine** (janela de Vidro 3×3 numa lateral) e **Faixa** (três vidros no alto de cada lateral).
- Dois Ressonadores **não dividem parede**.

Se não monta, clique no Núcleo: a barra de ação diz o que impede e o bloco errado fica contornado.

| Bloco | Papel |
| --- | --- |
| <ItemImage id="wirelessautomate:resonator_casing" /> **Carcaça de Porcelana** | A casca da caixa. |
| <ItemImage id="wirelessautomate:resonator_glass" /> **Vidro de Ressonância** | A janela para a câmara. |
| <ItemImage id="wirelessautomate:resonance_core" /> **Núcleo de Ressonância** | Tela, cristais e energia gerada. |
| <ItemImage id="wirelessautomate:resonator_energy_port" /> **Porta de Energia** | Só tira energia. |
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
<RecipeFor id="wirelessautomate:resonator_glass" />
<RecipeFor id="wirelessautomate:resonance_core" />
<RecipeFor id="wirelessautomate:resonator_energy_port" />
<RecipeFor id="wirelessautomate:resonator_crystal_port" />
<RecipeFor id="wirelessautomate:resonator_tuner" />
