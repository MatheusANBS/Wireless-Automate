---
navigation:
  title: Filtros
  icon: minecraft:hopper
  parent: index.md
  position: 5
---


# Filtros

Cada face da máquina, para cada tipo, tem um **filtro embutido** sem limite de entradas, mais dois
slots de [Cartão de Filtro](filter-card.md). Abra pelo botão **Editar**, na tela do roteador.

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

## A tela

À esquerda ficam as entradas, uma por linha, com a busca em cima e o seu inventário embaixo. À
direita, quatro abas. A janela cresce pelas bordas e pela alça do canto, como a do Baú.

| Aba | Para quê |
| --- | --- |
| **Entrada** | A entrada selecionada: o que ela pega, o estoque, Remover e, num item, **Ver tags**. |
| **Tags** | Achar e marcar tags e mods (ver abaixo). Nos químicos, vira **Adicionar**: id ou `@mod`. |
| **Regra** | Montar uma regra por propriedade (só itens). |
| **Mais** | Cartão de Filtro, componentes e Limpar. |

## Como adicionar

| Jeito | Como |
| --- | --- |
| **Inventário** | Shift + clique no item (para fluidos e químicos, num balde ou tanque cheio). |
| **JEI** | Arraste da lista do JEI para a lista de entradas, ou Shift + clique nele. Não precisa ter o item. |
| **Tags de um item** | Na aba **Tags**, clique no slot do inspetor com o item no cursor (ele continua com você), ou Ctrl + clique no item do inventário. Marque as tags e clique em **Adicionar**. |
| **Buscando** | Na aba **Tags**, digite parte do nome (`ingots`) para ver todas as tags do jogo, ou `@` para mods. Passe o mouse numa linha para ver os itens dela. |

## Regras por propriedade

Uma regra pega o item pelo que ele **é**, não por qual item é. Cada condição tem **- / Sim / Não**,
e a regra pega o item que cumpre **todas** as marcadas. O inventário acende no que ela pega antes
de você adicionar.

| Condição | Exemplo |
| --- | --- |
| **Encantado** | Qualquer item com encantamento, inclusive livro encantado. |
| **Danificado** | Com algum desgaste. **Não** = intacto. |
| **Renomeado** | Com nome dado na bigorna. |
| **Com poção** | Poções, flechas com efeito. |
| **Empilhável** | **Não** = ferramentas, armaduras e o que fica sozinho no slot. |
| **Com conteúdo** | Caixa de shulker ou bundle com algo dentro. |
| **Encantamento** | Um encantamento com nível mínimo: Fortuna ≥ III. Digite o nome ou o id e escolha na lista; nível de 1 a 255. |
| **Durabilidade** | Restante ≥ ou < uma porcentagem: < 25% manda para o reparo. |
| **Só em** | Limita a uma tag ou mod: Encantado + `#c:armors` = só armadura encantada. |

Para editar uma regra, selecione-a na lista e clique em **Editar**.

## Opções

| Opção | O que faz |
| --- | --- |
| **Lista branca / negra** | Inverte o filtro. |
| **Componentes** (itens) | Ignorar: picareta encantada = picareta. Exigir: só iguais. Para pegar só os encantados, use uma regra. |
| **Estoque** | No destino, aceita só até N. Na origem, mantém sempre N. Selecione a entrada para definir. |
