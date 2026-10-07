---
navigation:
  title: Filtros
  icon: minecraft:hopper
  parent: index.md
  position: 5
---


# Filtros

Cada face da máquina, para cada tipo, tem um **filtro embutido** sem limite de entradas, mais dois
slots de [Cartão de Filtro](filter-card.md). O recurso passa se o filtro embutido **ou** algum
cartão aceitar. Filtro vazio passa tudo.

Abra pelo botão **Editar**, na tela do roteador.

## Entradas

| Regra | Exemplo | Vale para |
| --- | --- | --- |
| Exato | Lingote de ferro, água, hidrogênio | Itens, fluidos, químicos |
| Tag | `#c:ingots`, `#c:ores` | Itens, fluidos |
| Mod | `@mekanism` | Itens, fluidos, químicos |

- **Adicionar:** Shift + clique num item do inventário (num balde ou tanque, para fluidos e
  químicos), arrastar do JEI ou Shift + clique na lista do JEI (sem precisar ter o item), ou
  digitar a regra na caixa de **Mais** (`#tag`, `@mod` ou, nos químicos, o id).
- **Lista branca ou negra**, por filtro.
- **Componentes** (itens): ignorar (picareta encantada = picareta) ou exigir iguais.
- **Estoque:** num destino, aceitar só até N; numa origem, manter sempre N.

Conferir um item custa o mesmo com 9 ou com milhares de entradas: o filtro é compilado.
