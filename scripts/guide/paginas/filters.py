"""Página filters.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('filters.md', front('Filtros', 'minecraft:hopper', 5) + '''
# Filtros

Cada face, em cada tipo, tem um **filtro embutido** sem limite de entradas e dois slots de
[Cartão de Filtro](filter-card.md).

## Como usar

1. Na tela do roteador, escolha a face e clique em **Editar**.
2. Shift + clique num item do inventário para adicioná-lo (fluidos e químicos: um balde ou tanque cheio).

## Regra de ouro

| Situação | Resultado |
| --- | --- |
| Nenhum filtro com entradas | **Passa tudo.** |
| Filtro embutido ou algum cartão aceita | Passa. |
| Lista branca | Só passa o que casa com uma entrada. |
| Lista negra | Passa tudo, menos o que casa. |

Na origem, o filtro decide o que **sai**; no destino, o que **entra**.

## Tipos de entrada

| Regra | Exemplo | Itens | Fluidos | Químicos |
| --- | --- | --- | --- | --- |
| **Exato** | Lingote de ferro, água, hidrogênio | Sim | Sim | Sim |
| **Tag** | `#c:ingots`, `#c:ores` | Sim | Sim | Não |
| **Mod** | `@mekanism` | Sim | Sim | Sim |
| **Regra** | Qualquer item encantado | Sim | Não | Não |

## Outros jeitos de adicionar

- **JEI:** arraste para a lista de entradas, ou Shift + clique. Não precisa ter o item.
- **Tags:** na aba **Tags**, Ctrl + clique num item do inventário, marque as tags e clique em
  **Adicionar**. Ou digite parte do nome (`ingots`), ou `@` para mods. Nos químicos, a aba vira
  **Adicionar**: id ou `@mod`.

## Regras por propriedade

Na aba **Regra** (só itens). Cada condição tem **- / Sim / Não**; o item precisa cumprir **todas** as
marcadas.

| Condição | Exemplo |
| --- | --- |
| **Encantado** | Inclusive livro encantado. |
| **Danificado** | Com desgaste. **Não** = intacto. |
| **Renomeado** | Com nome dado na bigorna. |
| **Com poção** | Poções, flechas com efeito. |
| **Empilhável** | **Não** = ferramentas, armaduras etc. |
| **Com conteúdo** | Caixa de shulker ou bundle com algo dentro. |
| **Encantamento** | Um encantamento com nível mínimo (1 a 255): Fortuna ≥ III. |
| **Durabilidade** | Restante ≥ ou < uma porcentagem: < 25% manda para o reparo. |
| **Só em** | Limita a uma tag ou mod: Encantado + `#c:armors` = só armadura encantada. |

## Opções

| Opção | O que faz |
| --- | --- |
| **Componentes** (itens) | Ignorar: picareta encantada = picareta. Exigir: só iguais. Para pegar só os encantados, use uma regra. |
| **Estoque** | No destino, aceita só até N. Na origem, mantém sempre N. Selecione a entrada para definir. |

''', front('Filters', 'minecraft:hopper', 5) + '''
# Filters

Each face, for each type, has a **built-in filter** with no entry limit and two
[Filter Card](filter-card.md) slots.

## How to use it

1. On the router screen, pick the face and click **Edit**.
2. Shift + click an item in your inventory to add it (fluids and chemicals: a full bucket or tank).

## Golden rule

| Situation | Result |
| --- | --- |
| No filter has entries | **Everything passes.** |
| The built-in filter or any card accepts | It passes. |
| Whitelist | Only what matches an entry passes. |
| Blacklist | Everything passes except what matches. |

At the source, the filter decides what **leaves**; at the destination, what **enters**.

## Entry types

| Rule | Example | Items | Fluids | Chemicals |
| --- | --- | --- | --- | --- |
| **Exact** | Iron ingot, water, hydrogen | Yes | Yes | Yes |
| **Tag** | `#c:ingots`, `#c:ores` | Yes | Yes | No |
| **Mod** | `@mekanism` | Yes | Yes | Yes |
| **Rule** | Any enchanted item | Yes | No | No |

## Other ways to add

- **JEI:** drag onto the entry list, or Shift + click. You don't need the item.
- **Tags:** in the **Tags** tab, Ctrl + click an item in your inventory, check the tags and click
  **Add**. Or type part of the name (`ingots`), or `@` for mods. For chemicals, the tab becomes
  **Add**: id or `@mod`.

## Property rules

In the **Rule** tab (items only). Each condition has **- / Yes / No**; the item must meet **all** the
set ones.

| Condition | Example |
| --- | --- |
| **Enchanted** | Enchanted books included. |
| **Damaged** | Has wear. **No** = undamaged. |
| **Renamed** | Named on an anvil. |
| **Has potion** | Potions, tipped arrows. |
| **Stackable** | **No** = tools, armor etc. |
| **Has contents** | A shulker box or bundle with something inside. |
| **Enchantment** | One enchantment with a minimum level (1 to 255): Fortune ≥ III. |
| **Durability** | Remaining ≥ or < a percentage: < 25% sends it to repair. |
| **Only in** | Limits it to a tag or mod: Enchanted + `#c:armors` = enchanted armor only. |

## Options

| Option | What it does |
| --- | --- |
| **Components** (items) | Ignore: enchanted pickaxe = pickaxe. Require: only identical. To match only enchanted ones, use a rule. |
| **Stock** | At a destination, accept only up to N. At a source, always keep N. Select the entry to set it. |

''')
