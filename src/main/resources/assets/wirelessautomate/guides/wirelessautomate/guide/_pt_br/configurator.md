---
navigation:
  title: Configurador
  icon: wirelessautomate:configurator
  parent: index.md
  position: 8
item_ids:
  - wirelessautomate:configurator
---


# Configurador

<ItemImage id="wirelessautomate:configurator" scale="2" float="left" />

Copia a configuração de um roteador (faces, filtros, prioridades, redstone e a rede de cada aba) e
cola em outros. Guarda uma cópia só, no próprio item; o tooltip mostra o que está copiado e os
comandos do modo atual.

<br clear="all" />

<RecipeFor id="wirelessautomate:configurator" />

| Gesto | Pincel (padrão) | Área |
| --- | --- | --- |
| Shift + clique num roteador | Copia | Copia |
| Clique num roteador | Cola nele | Marca um canto |
| Clique num bloco | — | Marca um canto (o 3º recomeça) |
| Clique no ar | — | Cola nos roteadores da área presos à **mesma máquina** da cópia |
| Shift + clique num bloco sem roteador | Limpa a varinha | Limpa a varinha |
| Shift + clique no ar | Vai para Área | Vai para Pincel |

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
    Área marcada com o Configurador
  </BoxAnnotation>
  <BlockAnnotation x="2" y="1" z="2" color="#ff6b5e">
    Fica como estava: está num baú, e a cópia veio de uma fornalha
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="35" />
</GameScene>

A cópia é relativa à orientação do roteador: funciona com ele preso em qualquer face. A rede de uma
aba só é colada se você puder usá-la.

**Dica:** para replicar uma linha de máquinas, copie o roteador de cada tipo de máquina e cole numa
área que cubra a linha inteira.
