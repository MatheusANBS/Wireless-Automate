---
navigation:
  title: Filtros
  icon: minecraft:hopper
  parent: index.md
  position: 5
---


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
