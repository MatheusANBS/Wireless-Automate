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
| **Modos** | Único e Área. |
| **Abas** | Itens, Fluidos, Energia e, com o Mekanism, Químicos. Marque quantas quiser. |
| **Rede** | Uma das suas redes, ou **Nenhuma (desvincular)**. |

## Comandos

| Gesto | O que faz |
| --- | --- |
| Clique num roteador | Põe as abas marcadas do roteador na rede ativa (cria uma, se você não tiver). Em **Nenhuma (desvincular)**, tira essas abas da rede. |
| Clique no ar | Abre a tela: rede, abas, modo e, em Área, a prévia e o botão **Vincular** (ou **Desvincular**). |
| Shift + clique no ar | Alterna entre **Único** e **Área**. |
| Shift + roda do mouse | Troca as abas pelos atalhos: **Todos**, Itens, Fluidos, Energia, Químicos (com o Mekanism). Uma combinação marcada na tela volta para **Todos**. |
| Shift + clique em dois blocos (Área) | Marca os cantos da área. |

## Na tela

| Parte | Como usar |
| --- | --- |
| **Rede** | Clique numa rede para torná-la a ativa, ou em **+ Nova rede** para criar uma. |
| **Nenhuma (desvincular)** | A primeira linha da lista. Com ela escolhida, os mesmos gestos tiram as abas marcadas da rede em vez de pôr. Escolha uma rede para voltar a vincular. |
| **Abas** | Uma caixa por aba. Só as abas marcadas mudam; as outras ficam como estão. Pelo menos uma fica marcada. |

Marque os tipos nas caixas; Todos marca ou desmarca todos.

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

## Exemplo: tirar uma área da rede, menos a energia

| Passo | O que fazer |
| --- | --- |
| **1** | Clique no ar para abrir a tela e escolha **Nenhuma (desvincular)**. |
| **2** | Deixe marcadas **Itens**, **Fluidos** e **Químicos** e desmarque **Energia**. |
| **3** | Shift + clique no ar para o modo **Área** e marque os dois cantos com Shift + clique. |
| **4** | Abra a tela de novo e clique em **Desvincular**: as abas de itens, fluidos e químicos saem da rede e a energia continua ligada. |

## Receita

<RecipeFor id="wirelessautomate:linker" />
