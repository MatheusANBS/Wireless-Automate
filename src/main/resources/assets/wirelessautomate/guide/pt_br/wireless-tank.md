---
group: armazenamentos
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

Um tanque para **vários fluidos ao mesmo tempo**, numa lista como a do [Baú Wireless](wireless-chest.md).
Preso a um roteador, troca bilhões de mB de uma vez.

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
| **Básico** | 28.800.000 |
| **Avançado** | 230.400.000 |
| **Elite** | 1.843.200.000 |
| **Esmeralda** | 14.745.600.000 |
| **Allthemodium¹** | 117.964.800.000 |
| **Vibranium¹** | 943.718.400.000 |
| **Unobtainium¹** | 7.549.747.200.000 |
| **Ultimate** | Sem limite |

¹ Só com o mod Allthemodium.

## Baldes e recipientes

| Gesto | O que faz |
| --- | --- |
| **Clique** no Tanque com balde cheio | Esvazia o balde (ou Shift + clique nele, na tela). |
| **Clique** no Tanque com balde vazio | Enche com o primeiro fluido. |
| Na tela, **clique** num fluido com recipiente no cursor | Enche o recipiente, ou o esvazia se já estiver cheio. Botão direito: esvazia um. |

Tanques e células de outros mods funcionam igual, pela tela.

## Filtro de entrada

O botão **Filtro** abre a [tela de filtros](filters.md) de fluidos: só entra o que ele aceita, e o
**estoque** vira "guardar até N mB".

## Em comum com o Baú

- **Tiers:** sobe com os mesmos [Cartões de Upgrade](upgrade-cards.md), no mundo ou na bancada, sem perder o conteúdo.
- **Quebrar:** o item leva o conteúdo e o filtro, e o tooltip mostra o total. Cheio, ele sempre vira item (no criativo, sem picareta ou numa explosão).
- **Comparador:** o sinal sobe com a ocupação.
- **Outros mods** o veem como um tanque comum.
- **Faces:** as seis são iguais. Ele não empurra nem puxa sozinho: quem move é o roteador, ou um cabo ou cano de outro mod. Uma máquina só encostada nele não recebe nada.

## Receita

<RecipeFor id="wirelessautomate:storage_tank" />
