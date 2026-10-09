# Upgrade direto de tier (design)

Aprovado pelo dono em 9/10/2026 (opção A do mockup "Upgrade direto de tier"). Origem: sugestão do jogador taccio3 no CurseForge, que pediu para fabricar direto um roteador de tier alto, ou ter upgrades que pulam degraus como os kits do Iron Furnaces.

## Problema

Cada Cartão de Upgrade leva o cartão anterior na receita e só sobe **um** degrau. Para ir do Básico ao Ultimate o jogador fabrica a escada de novo a cada degrau: 4 Avançados, 3 Elites, 2 Esmeraldas e 1 Ultimate sem o Allthemodium; 7, 6, 5, 4, 3, 2 e 1 com ele.

## Decisão

O Cartão de Upgrade de tier N sobe um roteador ou armazenamento de **qualquer tier abaixo de N** direto para N, no clique e na bancada. Como o cartão de N já consome os anteriores na receita, o custo total passa a ser exatamente uma escada (um cartão de cada).

- Regra: o alvo está acima do tier atual (`ordinal` maior) e o mod do alvo está carregado. Não desce, não reaplica o mesmo tier.
- Vale para o tier de origem fora da escada (bloco que ficou num tier do Allthemodium depois que o mod saiu): sobe para qualquer tier carregado acima dele.
- Com o Allthemodium, a Esmeralda passa a aceitar o Cartão Ultimate direto (antes era proibido); o Cartão Ultimate com o ATM já pede o Unobtainium na receita.
- Configuração, nome e conteúdo continuam, como hoje.
- A regra pura fica no `TierLadder` (com JUnit); `RouterTier.canUpgradeTo(target)` a expõe para `RouterBlock.tryUpgrade`, `StorageBlock.tryUpgrade` e `RouterUpgradeRecipe`.

## Tooltip do cartão

Antes: "Sobe o roteador de X para Y:" e cada linha "valor de X → valor de Y". Depois: a origem varia, então o tooltip mostra só o tier de destino.

| Chave | pt_br | en_us |
| --- | --- | --- |
| `item.wirelessautomate.tier_core.upgrades` | `Sobe para %s, de qualquer tier abaixo:` | `Raises to %s from any lower tier:` |
| `...tier_core.items` | `  Itens/s: %s` | `  Items/s: %s` |
| `...tier_core.fluids` | `  Fluidos e químicos (mB/s): %s` | (o texto em inglês atual, sem o `→ %s`) |
| `...tier_core.energy`, `source`, `range`, `storage_*` | idem, um `%s` só | idem |

O valor sai em `AQUA`, a linha em `DARK_AQUA`, como hoje. As chaves não mudam de nome.

## JEI

Uma receita de exibição na bancada para cada par (origem carregada abaixo, cartão do destino), no roteador e em cada armazenamento carregado. Ids `wirelessautomate:jei/router_upgrade_<origem>_<destino>` e `wirelessautomate:jei/<armazenamento>_upgrade_<origem>_<destino>`. Com o subtipo por tier (commit anterior), cada tier aparece separado.

## Fica de fora

Itens, sprites e receitas novas; mudar o custo dos cartões; upgrade em lote. `ModPayloads.VERSION` não muda (nenhum payload muda).
