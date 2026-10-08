---
navigation:
  title: Bateria Wireless
  icon: wirelessautomate:storage_battery
  parent: index.md
  position: 13
item_ids:
  - wirelessautomate:storage_battery
---


# Bateria Wireless

<ItemImage id="wirelessautomate:storage_battery" scale="2" float="left" />

Guarda energia (FE) muito além de um `int`: no Ultimate, sem limite. Preso a um roteador na aba
**Energia**, ela carrega e descarrega bilhões de FE por tick.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_battery" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_battery" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    Energia de uma Bateria para outra pela aba Energia
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## Capacidade

| Tier | FE |
| --- | --- |
| **Básico** | 16.000.000 |
| **Avançado** | 1.000.000.000 |
| **Elite** | 64.000.000.000 |
| **Ultimate** | Sem limite |

## A tela

Clique na Bateria: a barra mostra a carga e a porcentagem, e embaixo a variação por tick
(**carregando**, **descarregando** ou **estável**). Passe o mouse na barra para o valor exato.
A Bateria não tem filtro: energia é uma só.

## Em comum com o Baú

- **Tiers:** sobe com os mesmos [Cartões de Upgrade](upgrade-cards.md), no mundo ou na bancada, sem perder o conteúdo.
- **Quebrar:** o item leva o conteúdo, e o tooltip mostra o total. Cheio, ele sempre vira item (no criativo, sem picareta ou numa explosão).
- **Comparador:** o sinal sobe com a ocupação.
- **Outros mods** o veem como uma bateria comum.
- **Faces:** as seis são iguais. Ele não empurra nem puxa sozinho: quem move é o roteador, ou um cabo ou cano de outro mod. Uma máquina só encostada nele não recebe nada.

## Receita

<RecipeFor id="wirelessautomate:storage_battery" />
