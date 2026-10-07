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

Escolhe a sua **rede ativa** e coloca roteadores nela, um a um ou por área.

<br clear="all" />

<RecipeFor id="wirelessautomate:linker" />

| Gesto | O que faz |
| --- | --- |
| Clique num roteador | Põe o roteador na rede ativa (cria uma, se você não tiver) |
| Clique no ar | Abre a tela: rede ativa, tipo, modo e, em Área, a prévia e o botão Vincular |
| Shift + clique no ar | Alterna entre **Único** e **Área** |
| Shift + roda do mouse | Troca o tipo: Todos, Itens, Fluidos ou Energia |
| Shift + clique em dois blocos (modo Área) | Marca os cantos da área |

Com o tipo em **Todos**, o roteador entra na rede em todas as abas; com um tipo, só naquela aba.

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
  <BlockAnnotation x="2" y="1" z="2" color="#ff6b5e">
    Também entra (no Vinculador, a máquina não importa)
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="35" />
</GameScene>

A área vale até 262.144 blocos e a até 64 blocos de você (configurável no servidor).
