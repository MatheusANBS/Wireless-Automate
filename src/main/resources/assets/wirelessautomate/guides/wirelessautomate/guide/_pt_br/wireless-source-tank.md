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

Guarda muito mais **Source** que uma Source Jar. Só existe com o Ars Nouveau.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_source_tank" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_source_tank" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    De um Tanque para outro pela aba Source
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## Como usar

- **Sourcelinks** num raio de 5 blocos depositam nele.
- **Máquinas do Ars** por perto (Enchanting Apparatus, Imbuement Chamber, rituais, Spell Turrets e Relays de
  depósito) tiram dele como de uma Source Jar, sem encostar.
- **Relays** e outros blocos do Ars ligam nele direto.
- **Roteador:** na aba **Source**, funciona como qualquer face. Entre dois tanques, tudo passa de uma vez.

Clique no Tanque para ver quanto ele tem e a vazão por segundo; a coluna de vidro do bloco também mostra o nível.
Não tem filtro: Source é uma só.

## Capacidade

| Tier | Source |
| --- | --- |
| **Básico** | 10.000 |
| **Avançado** | 80.000 |
| **Elite** | 640.000 |
| **Esmeralda** | 5.120.000 |
| **Allthemodium¹** | 40.960.000 |
| **Vibranium¹** | 327.680.000 |
| **Unobtainium¹** | 2.621.440.000 |
| **Ultimate** | Sem limite |

¹ Só com o mod Allthemodium.

## Em comum com o Baú

- **Tiers:** sobe com os [Cartões de Upgrade](upgrade-cards.md), no mundo ou na bancada, sem perder o conteúdo.
- **Quebrar:** o item leva o conteúdo. Cheio, sempre vira item (no criativo, sem picareta ou numa explosão).
- **Comparador:** o sinal sobe com a ocupação.
- **Faces:** as seis são iguais.

## Receita

Um Tanque Wireless com gemas de Source e ferro (só com o Ars Nouveau).

<RecipeFor id="wirelessautomate:storage_source_tank" />
