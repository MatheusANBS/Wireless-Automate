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

Copia a configuração de um roteador e cola em outros, um a um ou numa área inteira.

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Copia** | Faces, filtros, prioridades, redstone e a rede de cada aba. |
| **Guarda** | Uma cópia só, no próprio item. O tooltip mostra o que está copiado e os comandos do modo. |
| **Modos** | Pincel (padrão), Área (mesma máquina) e Área (qualquer máquina). |
| **Tipo colado** | Todos (padrão), Itens, Fluidos, Energia, Químicos (com o Mekanism) ou Source (com o Ars Nouveau). |

## Comandos

| Gesto | Pincel | Área |
| --- | --- | --- |
| Shift + clique num roteador | Copia | Copia |
| Clique num roteador | Cola nele | Marca um canto |
| Clique num bloco | — | Marca um canto (o 3º recomeça) |
| Clique no ar | — | Cola nos roteadores da área: só nos presos à **mesma máquina** da cópia, ou em **qualquer máquina** |
| Shift + clique num bloco sem roteador | Limpa a varinha | Limpa a varinha |
| Shift + clique no ar | Próximo modo | Próximo modo |
| Shift + roda do mouse | Troca o tipo colado | Troca o tipo colado |
| Segurar Alt esquerdo | Abre a roda | Abre a roda |

## A roda

Com o Configurador na mão, **segure Alt esquerdo** (a tecla muda em Controles, na categoria Wireless
Automate): abre uma roda no meio da tela, com o anel de dentro para o **modo** (Pincel, Área na mesma
máquina, Área em qualquer máquina) e o anel de fora para o **tipo colado**. A opção de agora fica com a
borda laranja e o centro mostra o que faz a fatia sob o mouse.

| | |
| --- | --- |
| **Soltar a tecla** | Escolhe a fatia sob o mouse e fecha. |
| **Clique** | Escolhe sem fechar: dá para trocar o modo e o tipo de uma vez. |
| **Soltar no centro ou Esc** | Fecha sem mudar nada. |

Copiar sempre copia tudo; o **tipo colado** escolhe o que vai para o roteador. Em **Todos**, todas as
abas. Num tipo só, apenas as faces e a rede daquela aba: as outras abas do roteador ficam como
estavam. Por exemplo, copie um roteador configurado só para fluidos, passe para **Fluidos** e cole
nos outros sem mexer nos itens nem na energia deles.

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

## Bom saber

| | |
| --- | --- |
| **Mesma ou qualquer máquina** | Na mesma máquina, a área ignora os roteadores de outras máquinas (um baú no meio de uma linha de fornalhas fica como estava). Em qualquer máquina, cola em todos. |
| **Orientação** | A cópia é relativa ao roteador: funciona com ele preso em qualquer face. |
| **Redes** | A rede de uma aba só é colada se você puder usá-la; senão, a aba fica com a de antes. |
| **Replicar uma linha** | Copie o roteador de cada tipo de máquina e cole numa área que cubra a linha inteira. |

## Receita

<RecipeFor id="wirelessautomate:configurator" />
