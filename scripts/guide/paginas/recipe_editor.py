"""Página recipe-editor.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('recipe-editor.md', front('Editor de receitas', 'minecraft:writable_book', 22) + '''
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
''', front('Recipe editor', 'minecraft:writable_book', 22) + '''
# Recipe editor

Fit the cost of the mod's crafting recipes to your pack, without leaving the game.

## How to use it

1. Type `/wa recipes`. Operators only (permission level 2); in single player, with cheats.
2. Pick a recipe in the list. **Green** dot: default; **coral**: edited; **gray**: disabled.
3. Edit the 3 × 3 grid and the result.
4. **Save**, then **Reload now**: it applies to everyone right away.

## Editing

| To... | Do this |
| --- | --- |
| Put an item | Click the slot, then an item in your inventory (it stays there), or drag from JEI. |
| Empty | Right-click the slot, or **Clear**. |
| Accept a tag | **Tag**; the arrows cycle the tag. **Item** goes back to the exact item. |
| Amount | **−** and **+**, up to the stack size (64 at most). |
| An Upgrade Card's ring | Swap the eight slots around it. The center is locked; the new ring applies to everything that goes there. |

**Restore default** brings back the mod's recipe. **Disable** / **Enable** takes the recipe out or
brings it back. Closing the screen also applies what was saved.

## Good to know

| | |
| --- | --- |
| **Where it lives** | `config/wirelessautomate/recipes/`. Applies to every world in the instance (or the server) and survives updates. |
| **Datapacks and KubeJS** | If they change the same recipe, they win; the screen warns you. |
| **Not in the editor** | Other mods' recipes, the Filter Card copy and the router upgrade in the crafting table. It can't create new recipes. |
| **Undo everything** | Delete `data` inside `config/wirelessautomate/recipes/` and run `/reload`. |
''')
