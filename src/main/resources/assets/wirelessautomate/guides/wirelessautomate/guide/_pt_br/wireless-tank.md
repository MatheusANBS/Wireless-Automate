---
navigation:
  title: Tanque Wireless
  icon: wirelessautomate:storage_tank
  parent: index.md
  position: 12
item_ids:
  - wirelessautomate:storage_tank
---


# Tanque Wireless

<ItemImage id="wirelessautomate:storage_tank" scale="2" float="left" />

Um tanque para **vários fluidos ao mesmo tempo**, quantos couberem na capacidade do tier. Cada
fluido aparece numa lista, como os itens do [Baú Wireless](wireless-chest.md). Preso a um roteador,
ele troca bilhões de mB de uma vez.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_tank" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_tank" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    Fluido de um Tanque para outro pela aba Fluidos
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## Capacidade

Total em mB, todos os fluidos somados.

| Tier | mB |
| --- | --- |
| **Básico** | 1.000.000 |
| **Avançado** | 64.000.000 |
| **Elite** | 4.000.000.000 |
| **Ultimate** | Sem limite |

## Baldes e recipientes

| Quero... | Como |
| --- | --- |
| Esvaziar um balde | Clique no Tanque com o balde cheio na mão, ou Shift + clique nele no inventário da tela. |
| Encher um balde | Clique no Tanque com o balde vazio (pega o primeiro fluido), ou, na tela, clique no fluido com o balde no cursor. |
| Outros recipientes | Tanques e células de outros mods funcionam igual, pela tela. |

Na tela, com um recipiente no cursor: clique num fluido enche o recipiente com ele, ou o esvazia se
ele já estiver cheio; o botão direito esvazia um. A busca, a ordem e o redimensionar são os do Baú.

## Filtro de entrada

O botão **Filtro** da tela abre a [tela de filtros](filters.md) de fluidos: só entra o que ele
aceita, e o **estoque** de uma entrada vira "guardar até N mB".

## Em comum com o Baú

- **Tiers:** sobe com os mesmos [Cartões de Upgrade](upgrade-cards.md), no mundo ou na bancada, sem perder o conteúdo.
- **Quebrar:** o item leva o conteúdo e o filtro, e o tooltip mostra o total. Cheio, ele sempre vira item (no criativo, sem picareta ou numa explosão).
- **Comparador:** o sinal sobe com a ocupação.
- **Outros mods** o veem como um tanque comum.

## Receita

<RecipeFor id="wirelessautomate:storage_tank" />
