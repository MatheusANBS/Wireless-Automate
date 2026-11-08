---
group: armazenamentos
navigation:
  title: Tanque Químico Wireless
  icon: wirelessautomate:storage_chemical_tank
  parent: index.md
  position: 14
item_ids:
  - wirelessautomate:storage_chemical_tank
---


# Tanque Químico Wireless

<ItemImage id="wirelessautomate:storage_chemical_tank" scale="2" float="left" />

O [Tanque Wireless](wireless-tank.md) dos **químicos do Mekanism**, vários de uma vez. Só existe com o Mekanism.
Na aba **Químicos** do roteador, um tipo inteiro passa de uma vez.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_chemical_tank" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_chemical_tank" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    De um Tanque para outro pela aba Químicos
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## Capacidade

A do [Tanque](wireless-tank.md), em mB, somando todos os químicos.

| Tier | mB |
| --- | --- |
| **Básico** | 28.800.000 |
| **Avançado** | 230.400.000 |
| **Elite** | 1.843.200.000 |
| **Esmeralda** | 14.745.600.000 |
| **Allthemodium¹** | 117.964.800.000 |
| **Vibranium¹** | 943.718.400.000 |
| **Unobtainium¹** | 7.549.747.200.000 |
| **Ultimate** | Sem limite |

¹ Só com o mod Allthemodium.

## Recipientes do Mekanism

| Gesto | O que faz |
| --- | --- |
| **Clique** num químico, com tanque ou cilindro no cursor | Enche o recipiente (ou esvazia, se cheio). |
| **Clique direito** | Esvazia um. |
| **Shift + clique** no recipiente do inventário | Esvazia no Tanque. |

## Filtro de entrada

**Filtro** abre o filtro de químicos (por químico ou por mod). O **estoque** vira "guardar até N mB".

## Em comum com o Baú

- **Tiers:** sobe com os mesmos [Cartões de Upgrade](upgrade-cards.md), no mundo ou na bancada, sem perder o conteúdo.
- **Quebrar:** o item leva o conteúdo e o filtro, e o tooltip mostra o total. Cheio, ele sempre vira item (no criativo, sem picareta ou numa explosão).
- **Comparador:** o sinal sobe com a ocupação.
- **Outros mods** o veem como um tanque de químicos comum.
- **Faces:** as seis são iguais. Ele não empurra nem puxa sozinho: quem move é o roteador, ou um cabo ou cano de outro mod. Uma máquina só encostada nele não recebe nada.

## Receita

Um Tanque Wireless com frascos de vidro e ferro (só com o Mekanism).

<RecipeFor id="wirelessautomate:storage_chemical_tank" />
