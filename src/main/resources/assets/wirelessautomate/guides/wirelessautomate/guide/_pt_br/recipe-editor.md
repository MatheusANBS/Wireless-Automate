---
navigation:
  title: Editor de receitas
  icon: minecraft:writable_book
  parent: index.md
  position: 19
---


# Editor de receitas

Troque os ingredientes e o resultado das receitas de bancada do mod sem sair do jogo e sem mexer em
arquivos. Serve para ajustar o custo dos itens do Wireless Automate ao seu pack.

## Quem pode usar

Só operadores (permissão 2). Num mundo de um jogador só, é preciso ter os comandos ativados (cheats).

## Abrir

Digite `/wa recipes`. A tela mostra, à esquerda, as receitas de bancada do mod que estão carregadas,
com uma busca no topo. A bolinha de cada receita diz o estado:

| Bolinha | Estado |
| --- | --- |
| **Verde** | Receita padrão do mod. |
| **Coral** | Receita editada por você. |
| **Cinza** | Receita desativada. |

## Editar uma receita

Escolha uma receita na lista: a grade 3 × 3 e o resultado aparecem à direita.

| Para... | Faça |
| --- | --- |
| Pôr um item num slot | Clique no slot e depois num item do seu inventário (o item não sai de lá), ou clique no slot com o item no cursor. Com o JEI, arraste o item para o slot. |
| Esvaziar um slot | Clique com o botão direito nele. |
| Aceitar qualquer item de uma tag | Escolha o slot e clique em **Tag**. As setas trocam entre as tags do item. **Item** volta para o item exato. |
| Esvaziar o slot escolhido | **Limpar** (ou clique direito no slot). |
| Mudar a quantidade do resultado | **−** e **+**, até o tamanho da pilha do item (no máximo 64). |

## Salvar e recarregar

| Botão | O que faz |
| --- | --- |
| **Salvar** | Só grava a receita. Nada muda no jogo ainda. |
| **Recarregar agora** | Aplica as mudanças salvas, para todos os jogadores, na hora. |
| **Restaurar padrão** | Volta a receita ao que o mod traz. |
| **Desativar** / **Reativar** | Tira a receita do jogo, ou a devolve. |

Se você fechar a tela com mudanças salvas ainda não aplicadas, elas são recarregadas ao fechar.

## Bom saber

| | |
| --- | --- |
| **Onde fica** | As edições ficam em `config/wirelessautomate/recipes/`, uma pasta só para a instância. Valem em todos os mundos dela e, num servidor, no servidor. Sobrevivem às atualizações do mod. |
| **Outros datapacks e KubeJS** | Se outro datapack ou o KubeJS mexer na mesma receita, o dele vale, e a tela avisa que a receita é diferente do que está carregado. |
| **O que não aparece** | Receitas de outros mods, a cópia de Cartão de Filtro e o upgrade de roteador na bancada. Também não dá para criar receitas novas. |
| **Voltar tudo** | Apague a pasta `data` dentro de `config/wirelessautomate/recipes/` e recarregue (`/reload`). |
