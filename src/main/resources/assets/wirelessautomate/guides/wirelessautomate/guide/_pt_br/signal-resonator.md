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

Um gerador de energia de vários blocos: cristais de quartzo vibram dentro de uma caixa, e você
**sintoniza** o Núcleo na frequência certa para tirar o máximo de FE. Os cristais vêm do
[Quartzo de Sinal](signal-quartz.md).

<br clear="all" />

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
    Porta de Cristais: entram cristais e redstone, saem Opacos
  </BlockAnnotation>
  <BlockAnnotation x="2" y="4" z="2" color="#a06bff">
    Sintonizador (opcional)
  </BlockAnnotation>
  <IsometricCamera yaw="35" pitch="30" />
</GameScene>

A cena mostra a montagem **Vitrine**, com a janela de vidro numa face e o Núcleo em outra. Gire a cena com o mouse.

## Montar

1. Coloque o **Núcleo de Ressonância** e clique nele com a mão vazia: a estrutura aparece **projetada** no mundo
   (as peças que faltam e o que está errado). Clique de novo para desligar. **Shift + roda do mouse** troca entre
   as montagens **Vitrine** e **Faixa**.
2. A estrutura é uma caixa **5×5×5 oca**: **Carcaça de Porcelana** nas arestas e nos cantos; **Vidro de
   Ressonância**, as portas e o Sintonizador nas posições do miolo das faces. O interior 3×3×3 fica vazio.
3. Precisa de **um Núcleo**, **pelo menos uma Porta de Energia** e **pelo menos uma Porta de Cristais**; o
   **Sintonizador** é opcional (no máximo um). O Núcleo e as portas ficam no miolo das faces, nunca nas arestas.
4. Duas montagens de exemplo valem: a **Vitrine** (janela de Vidro 3×3 na frente) e a **Faixa** (três vidros na
   linha de cima de cada lateral). Outros arranjos também montam: o Vidro pode ficar em qualquer posição do
   miolo das faces.
5. Uma **parede não pode ser dividida** com outro Ressonador: cada um tem a sua caixa.

Ao completar, a estrutura se acende com um pulso e a barra de ação diz **Ressonador montado**. Se não monta, clique
no Núcleo solto: a barra de ação diz o que impede e o bloco errado fica contornado em coral.

| Bloco | Papel |
| --- | --- |
| <ItemImage id="wirelessautomate:resonator_casing" /> **Carcaça de Porcelana** | A casca da caixa. |
| <ItemImage id="wirelessautomate:resonator_glass" /> **Vidro de Ressonância** | A janela para ver a câmara. |
| <ItemImage id="wirelessautomate:resonance_core" /> **Núcleo de Ressonância** | O cérebro: tela, cristais e a energia gerada. |
| <ItemImage id="wirelessautomate:resonator_energy_port" /> **Porta de Energia** | Só tira energia: prenda um roteador nela. |
| <ItemImage id="wirelessautomate:resonator_crystal_port" /> **Porta de Cristais** | Aceita cristais e redstone e devolve os Opacos. |
| <ItemImage id="wirelessautomate:resonator_tuner" /> **Sintonizador** | Opcional: sintoniza sozinho. |

## Cristais e redstone

Cada Núcleo tem câmaras para os cristais (1 no Básico, até 8 no Ultimate). Ponha um **Cristal de Sinal** ou
**Lapidado** de um destes jeitos:

| Como | O que fazer |
| --- | --- |
| **Pelo clique** | Clique no Núcleo com o cristal na mão: ele vai para a primeira câmara vazia. Com redstone na mão, o pó entra no Núcleo. |
| **Pela tela** | Abra a tela do Núcleo e use os slots de cristais e o de redstone. |
| **Pela Porta de Cristais** | Um roteador (ou outro transporte de itens) põe os cristais e a redstone na porta e tira os Opacos dela. |

Para tirar o último cristal, clique no Núcleo com **Shift e as duas mãos vazias**. A **redstone** só serve
para recarregar os Opacos, como na página do [Quartzo de Sinal](signal-quartz.md).

## Sintonizar

Clique no Núcleo montado, com a mão vazia, para abrir a tela. O Ressonador tem uma **estação**, uma
frequência escondida, e o **dial** precisa estar em cima dela:

- Gire o dial com os botões (**±1 MHz** e **±0,05 MHz**) ou com a **roda do mouse** (±0,05 MHz).
- O ponteiro mostra se está perto; quando acende a lâmpada **EM SINTONIA**, a geração está a 100%.
- Se o ponteiro diz **sem sinal: varra o dial**, o dial está longe: varra-o até o ponteiro aparecer.
- Quanto mais longe da estação, menos energia. Muito longe, a geração vai a **zero**.

A estação **escorrega devagar com o tempo**, e a sintonia se perde aos poucos: volte de vez em quando e acerte o dial
de novo. A estação nunca aparece como número: você a acha pela lâmpada.

## Sintonizador

Com um **Sintonizador** na estrutura, o botão **Sintonizador** da tela liga o **modo automático**: o dial segue a
estação sozinho, sem você voltar. Em troca, a eficiência fica limitada por um **teto** que depende do tier do Núcleo
(da tabela mais abaixo). No modo automático o dial não responde aos botões: volte para **Manual** para girá-lo.

## A câmara

Pelo vidro dá para ler a sintonia, sem abrir a tela:

- **Em sintonia**: a figura de luz no ar **fecha**, os cristais sobem e descem **juntos**, cada um com um feixe até a
  luz, e a placa do chão fica nítida.
- **Fora de sintonia**: a figura vira um **rabisco**, cada cristal vai no seu tempo e o chão chia. Com a geração em zero,
  a figura some.
- A lente do Núcleo acende na parede dele, faíscas correm até a Porta de Energia e um fio de redstone chega ao cristal
  Opaco que está recarregando.

## Energia

A energia sai pela **Porta de Energia**: prenda um [Roteador](router.md) nela e ponha a face em **Extrai**
na aba **Energia**. O Núcleo guarda uma reserva pequena (uns 10 segundos de geração cheia); com ela cheia, ele
**para de gerar e não gasta cristal**. O Núcleo só trabalha com o chunk carregado: para ele rodar longe de você,
use o [Upgrade de Chunk Loading](chunk-loading.md) no roteador preso à Porta.

## Tiers

O Núcleo sobe de tier com os mesmos [Cartões de Upgrade](upgrade-cards.md) do roteador (Shift + clique com o
cartão no Núcleo), sem desmontar: o conteúdo continua e as câmaras novas aparecem. Cada tier tem mais câmaras e
mais FE por cristal. O Lapidado rende o dobro do que a tabela mostra para o Cristal de Sinal.

| Tier do Núcleo | Câmaras | FE/t por Cristal de Sinal | Teto do Sintonizador |
| --- | --- | --- | --- |
| **Básico** | 1 | 128 | 85% |
| **Avançado** | 2 | 1.024 | 88% |
| **Elite** | 3 | 8.192 | 90% |
| **Esmeralda** | 4 | 32.768 | 92% |
| **Allthemodium¹** | 5 | 262.144 | 94% |
| **Vibranium¹** | 6 | 2.097.152 | 95% |
| **Unobtainium¹** | 7 | 134.217.728 | 96% |
| **Ultimate** | 8 | 1.073.741.824 | 98% |

¹ Só com o mod Allthemodium.

Valores padrão: o servidor pode mudar o FE por cristal. O JEI mostra os do seu pack no Núcleo.

## Receitas

<RecipeFor id="wirelessautomate:resonator_casing" />
<RecipeFor id="wirelessautomate:resonator_glass" />
<RecipeFor id="wirelessautomate:resonance_core" />
<RecipeFor id="wirelessautomate:resonator_energy_port" />
<RecipeFor id="wirelessautomate:resonator_crystal_port" />
<RecipeFor id="wirelessautomate:resonator_tuner" />
