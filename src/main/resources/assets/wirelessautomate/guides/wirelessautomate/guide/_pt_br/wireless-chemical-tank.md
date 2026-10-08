---
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

O [Tanque Wireless](wireless-tank.md) para os **químicos do Mekanism** (gases, líquidos de
infusão, pigmentos, slurries): vários ao mesmo tempo, na capacidade do tier. Só existe com o
Mekanism instalado. Preso a um roteador na aba **Químicos**, um tipo inteiro passa de uma vez.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_chemical_tank" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_chemical_tank" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    Químico de um Tanque para outro pela aba Químicos
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## Capacidade

A mesma do Tanque: 1.000.000, 64.000.000 e 4.000.000.000 mB, e sem limite no Ultimate.

## Recipientes do Mekanism

Na tela, com um tanque ou cilindro de químico do Mekanism no cursor: clique num químico enche o
recipiente com ele, ou o esvazia se ele já estiver cheio; o botão direito esvazia um. Shift +
clique num recipiente do inventário esvazia ele no Tanque.

## Filtro de entrada

O botão **Filtro** abre a tela de filtros de químicos (por químico ou por mod); o **estoque** vira
"guardar até N mB".

## Em comum com o Baú

- **Tiers:** sobe com os mesmos [Cartões de Upgrade](upgrade-cards.md), no mundo ou na bancada, sem perder o conteúdo.
- **Quebrar:** o item leva o conteúdo e o filtro, e o tooltip mostra o total. Cheio, ele sempre vira item (no criativo, sem picareta ou numa explosão).
- **Comparador:** o sinal sobe com a ocupação.
- **Outros mods** o veem como um tanque de químicos comum.

## Receita

Um Tanque Wireless com frascos de vidro e ferro (só com o Mekanism).

<RecipeFor id="wirelessautomate:storage_chemical_tank" />
