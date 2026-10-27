# Receitas e editor de receitas

As receitas de tipo próprio e o editor `/wa recipes`. Parte do mapa do código (pacote base `src/main/java/io/github/matheusanbs/wirelessautomate/`). Índice em [CLAUDE.md](../../CLAUDE.md).

## `recipe/edit/`

Editor de receitas (`/wa recipes`, só operador): `RecipeSlot` e `RecipeDraft` (o rascunho da grade 3×3 e do resultado, puro com JUnit), `RecipeJson` (lê e escreve a receita), `RecipeOverridePack` (grava em `config/wirelessautomate/recipes/`, um datapack global ligado como repositório do servidor: vale em todos os mundos e sobrevive a atualizações; outro datapack ou o KubeJS ganha) e `RecipeEditor` (carregadas × padrão × editada × desativada, salvar, restaurar, desativar e recarregar para todos). A recarga passa pelo `ReloadGate` (puro, com JUnit): uma por vez, porque duas sobrepostas quebram o KubeJS e dobram a memória; as pendências voltam se ela falhar e, depois de uma falha, fechar a tela não tenta de novo. Só as receitas de bancada do mod (`crafting_shaped`/`shapeless`); fora as de outros mods, as especiais, os anéis dos Cartões de Upgrade (tipo próprio) e criar receita nova. Override em disco sem padrão editável (um `tier_core_*` editado antes do tipo novo) só gera um aviso no log, na subida do servidor e a cada recarga. Spec em `docs/superpowers/specs/2026-10-10-editor-de-receitas-design.md`

## `recipe/`

`FilterCardCopyRecipe` (cartão configurado + vazio = dois iguais), `RouterUpgradeRecipe` (bloco + Cartão de Upgrade na bancada, pela regra com origem; `withTier`) e `TierUpgradeRecipe` (`wirelessautomate:tier_upgrade`, um anel por tier em `tier_core_<tier>.json`, estende `ShapedRecipe`: o centro decide entre o cartão de um degrau com Redstone, o cartão que pula com um cartão e a receita direta com o bloco do degrau anterior; mesmo custo pelos dois caminhos). O JEI mostra as três e os pares bloco + cartão. Spec em `docs/superpowers/specs/2026-10-12-subir-tier-design.md`

## `client/recipe/`

O editor de receitas em partes (356 × 276 de tamanho fixo: lista com busca e bolinha de estado, grade 3×3 de slots fantasmas, Item/Tag, quantidade, Salvar, Restaurar e Desativar; com o JEI, `compat/jei/RecipeEditorGhostHandler` aceita o arrasto). Anfitriã: `RecipeEditorScreen` (pública: os botões, a visibilidade pelo estado, os cliques na lista, na grade e no inventário, as dicas, o cabeçalho e o inventário, a parte que o JEI usa e os ganchos do e2e). Partes: `RecipeLayout` (as medidas), `RecipeItems` (itens e tags pelo id), `RecipeEdit` (a receita escolhida e o rascunho: slot escolhido, a origem das tags e as edições), `RecipeList` (a busca, a lista com rolagem e a recarga pendente), `RecipeView` (a receita: cabeçalho, grade, resultado, inspetor e as dicas da grade) e `RecipePaint` (os botões e as marcas da grade)

