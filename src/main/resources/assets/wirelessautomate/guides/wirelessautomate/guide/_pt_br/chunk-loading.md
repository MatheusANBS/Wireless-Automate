---
navigation:
  title: Upgrade de Chunk Loading
  icon: wirelessautomate:chunk_loader_upgrade
  parent: index.md
  position: 10
item_ids:
  - wirelessautomate:chunk_loader_upgrade
---


# Upgrade de Chunk Loading

<ItemImage id="wirelessautomate:chunk_loader_upgrade" scale="2" float="left" />

Mantém o chunk do roteador (e da máquina) carregado, para ele continuar trabalhando com você longe.

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Vai em** | O slot do cabeçalho da tela do roteador. |
| **Limite** | Cada jogador mantém até 16 chunks; vários roteadores no mesmo chunk contam uma vez. |

## Estados

| Estado no cabeçalho | Significa |
| --- | --- |
| **Ativo: chunks carregados** | O roteador e a máquina continuam trabalhando longe dos jogadores. |
| **Inativo: limite de chunks do dono** | Quem pôs o upgrade já força o máximo de chunks permitido. |
| **Inativo: desligado no servidor** | O servidor desativou este upgrade. |

## Receita

<RecipeFor id="wirelessautomate:chunk_loader_upgrade" />
