---
navigation:
  title: Editor de receitas
  icon: minecraft:writable_book
  parent: index.md
  position: 22
---


# Editor de receitas

Ajuste o custo das receitas de bancada do mod ao seu pack, sem sair do jogo.

## Como usar

1. Digite `/wa recipes`. Só operadores (permissão 2); num mundo de um jogador, com cheats.
2. Escolha uma receita na lista. Bolinha **verde**: padrão; **coral**: editada; **cinza**: desativada.
3. Edite a grade 3 × 3 e o resultado.
4. **Salvar**, depois **Recarregar agora**: vale para todos na hora.

## Editar

| Para... | Faça |
| --- | --- |
| Pôr um item | Clique no slot e num item do inventário (ele fica lá), ou arraste do JEI. |
| Esvaziar | Clique direito no slot, ou **Limpar**. |
| Aceitar uma tag | **Tag**; as setas trocam a tag. **Item** volta ao item exato. |
| Quantidade | **−** e **+**, até a pilha (no máximo 64). |
| Anel de um Cartão de Upgrade | Troque os oito slots em volta. O centro é travado; o anel novo vale para tudo o que vai nele. |

**Restaurar padrão** volta a receita do mod. **Desativar** / **Reativar** tira ou devolve a receita.
Fechar a tela também aplica o que foi salvo.

## Bom saber

| | |
| --- | --- |
| **Onde fica** | `config/wirelessautomate/recipes/`. Vale em todos os mundos da instância (ou no servidor) e sobrevive às atualizações. |
| **Datapacks e KubeJS** | Se mexerem na mesma receita, eles vencem; a tela avisa. |
| **Fora do editor** | Receitas de outros mods, a cópia de Cartão de Filtro e o upgrade de roteador na bancada. Não cria receitas novas. |
| **Voltar tudo** | Apague `data` dentro de `config/wirelessautomate/recipes/` e use `/reload`. |
