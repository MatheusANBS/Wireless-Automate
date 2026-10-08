---
navigation:
  title: Tanque de Source Wireless
  icon: wirelessautomate:storage_source_tank
  parent: index.md
  position: 17
item_ids:
  - wirelessautomate:storage_source_tank
---


# Tanque de Source Wireless

<ItemImage id="wirelessautomate:storage_source_tank" scale="2" float="left" />

Guarda **Source** do Ars Nouveau em grande quantidade, muito além de uma Source Jar. Só existe com o
Ars Nouveau instalado. Preso a um roteador na aba **Source**, ele recebe e entrega Source como os
outros armazenamentos.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_source_tank" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_source_tank" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    Source de um Tanque para outro pela aba Source
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## Para o Ars

- Os **Sourcelinks** num raio de 5 blocos depositam nele.
- As máquinas do Ars por perto (Enchanting Apparatus, Imbuement Chamber, rituais, Spell Turrets e Relays de depósito) tiram dele, como de uma Source Jar.

## Com o roteador

Na aba **Source** ele funciona como qualquer outra face. Entre dois tanques, tudo passa de uma
vez.

## A tela

Clique no Tanque: a barra roxa mostra quanto ele tem, a porcentagem e a vazão em Source por
segundo. A coluna de vidro do bloco também mostra o nível, de vazio a cheio. O Tanque não tem
filtro: Source é uma só.

## Capacidade

| Tier | Source |
| --- | --- |
| **Básico** | 160.000 |
| **Avançado** | 2.560.000 |
| **Elite** | 40.960.000 |
| **Ultimate** | Sem limite |

## Em comum com o Baú

- **Tiers:** sobe com os mesmos [Cartões de Upgrade](upgrade-cards.md), no mundo ou na bancada, sem perder o conteúdo.
- **Quebrar:** o item leva o conteúdo, e o tooltip mostra o total. Cheio, ele sempre vira item (no criativo, sem picareta ou numa explosão).
- **Comparador:** o sinal sobe com a ocupação.
- **Outros mods** o veem como um tanque comum.
- **Faces:** as seis são iguais. Ele não empurra nem puxa sozinho: quem move é o roteador, ou um cabo ou cano de outro mod. Uma máquina só encostada nele não recebe nada.

## Receita

Um Tanque Wireless com gemas de Source e ferro (só com o Ars Nouveau).

<RecipeFor id="wirelessautomate:storage_source_tank" />
