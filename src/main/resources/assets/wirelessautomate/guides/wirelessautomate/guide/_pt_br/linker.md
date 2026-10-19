---
navigation:
  title: Vinculador
  icon: wirelessautomate:linker
  parent: index.md
  position: 7
item_ids:
  - wirelessautomate:linker
---


# Vinculador

<ItemImage id="wirelessautomate:linker" scale="2" float="left" />

Escolhe a sua **rede ativa** e coloca roteadores nela, um a um ou uma área inteira de uma vez.
Também tira roteadores da rede.

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Modos** | Único, Área e Máquinas. No modo **Máquinas**, escolha um roteador com o Upgrade de Alcance e clique nas máquinas (um clique numa vinculada a desvincula); a mensagem diz a máquina e o grupo ou marque uma área. Uma máquina de um tipo novo entra sem rede; a face entra na sua rede ativa quando você escolhe um modo nela. Com **Nenhuma (desvincular)** na tela, o botão da área desvincula do roteador as máquinas dentro dela. |
| **Abas** | Itens, Fluidos, Energia e, com o Mekanism, Químicos; com o Ars Nouveau, Source. Marque quantas quiser. |
| **Faces** | As seis (padrão) ou só algumas: Frente, Trás, Esquerda, Direita, Cima e Baixo, lados da máquina de cada roteador. |
| **Rede** | Uma das suas redes, ou **Nenhuma (desvincular)**. |

## Comandos

| Gesto | O que faz |
| --- | --- |
| Clique num roteador | Põe as faces marcadas do roteador, nas abas marcadas, na rede ativa (cria uma, se você não tiver). Em **Nenhuma (desvincular)**, tira essas faces da rede. |
| Clique no ar | Abre a tela: rede, abas, faces, modo e, em Área, a prévia e o botão **Vincular** (ou **Desvincular**). |
| Shift + clique no ar | Passa para o próximo modo: **Único**, **Área**, **Máquinas** e de volta ao **Único**. |
| Shift + roda do mouse | Troca as abas pelos atalhos: **Todos**, Itens, Fluidos, Energia, Químicos (com o Mekanism), Source (com o Ars Nouveau). Uma combinação marcada na tela volta para **Todos**. |
| Shift + clique em dois blocos (Área) | Marca os cantos da área. |

## Na tela

| Parte | Como usar |
| --- | --- |
| **Rede** | Clique numa rede para torná-la a ativa, ou em **+ Nova rede** para criar uma. |
| **Nenhuma (desvincular)** | A primeira linha da lista. Com ela escolhida, os mesmos gestos tiram as faces e as abas marcadas da rede em vez de pôr. Escolha uma rede para voltar a vincular. |
| **Abas** | Um botão colorido por tipo: clique para marcar ou desmarcar (marcado = borda e fundo na cor do tipo). **Todos** marca todos os tipos; com todos marcados, deixa só o primeiro. Só as abas marcadas mudam; as outras ficam como estão. Pelo menos uma fica marcada. |
| **Faces** | **Todas** e uma letra por lado da máquina (F, T, E, D, C, B), vistos de frente para ela: clique para marcar ou desmarcar. Só as faces marcadas mudam. O botão mostra quais vão, como **Vincular 3 · T, B**. Máquina sem frente: Frente = Norte. Não aparece no modo Máquinas. |
| **Mais dicas ›** | No modo Único, quando as dicas não cabem na tela: passe o mouse para ver as que ficaram de fora. |

Arraste a borda ou o canto para aumentar a tela; o mapa da área cresce.

<GameScene zoom="3" interactive={true}>
  <Block id="minecraft:furnace" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:furnace" x="2" y="0" z="0" />
  <Block id="wirelessautomate:router" x="2" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:furnace" x="4" y="0" z="0" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:chest" x="2" y="0" z="2" />
  <Block id="wirelessautomate:router" x="2" y="1" z="2" p:facing="up" p:tier="basic" />
  <BoxAnnotation min="0 0 0" max="5 2 3" color="#45d6cc" thickness="0.05">
    Área marcada: todos os roteadores carregados dentro dela entram na rede
  </BoxAnnotation>
  <BlockAnnotation x="2" y="1" z="2" color="#3fc36b">
    Também entra: para o Vinculador, a máquina não importa
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="35" />
</GameScene>

A área pode ter até 262.144 blocos (por exemplo 64 × 64 × 64), e você precisa estar a até 64
blocos dela.

## Exemplo: entrada e saída em redes diferentes

| Passo | O que fazer |
| --- | --- |
| **1** | Na tela do roteador de cada fornalha, ponha a face **Cima** em **Insere** e a **Baixo** em **Extrai**. |
| **2** | No Vinculador, escolha a rede **Mina** e, em Faces, deixe só **C** (Cima) marcada. Clique nos roteadores das fornalhas. |
| **3** | Escolha a rede **Armazém**, marque só **B** (Baixo) e clique nos roteadores de novo. |
| **4** | O minério chega pela Mina e os lingotes saem pelo Armazém. Volte a **Todas** quando terminar. |

## Exemplo: tirar uma área da rede, menos a energia

| Passo | O que fazer |
| --- | --- |
| **1** | Clique no ar para abrir a tela e escolha **Nenhuma (desvincular)**. |
| **2** | Deixe marcadas **Itens**, **Fluidos** e **Químicos** e desmarque **Energia**. |
| **3** | Shift + clique no ar para o modo **Área** e marque os dois cantos com Shift + clique. |
| **4** | Abra a tela de novo e clique em **Desvincular**: as faces de itens, fluidos e químicos saem da rede e a energia continua ligada. |

## Receita

<RecipeFor id="wirelessautomate:linker" />
