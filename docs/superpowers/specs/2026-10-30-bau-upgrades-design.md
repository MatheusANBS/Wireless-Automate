# Upgrades do Baú: Mesa de Trabalho e Compactação

Data: 30/10/2026. Mockup aprovado: `docs/preview/bau-upgrades.html` (versão 4 do artifact). Ramo: `bau-upgrades`. Versão alvo: 2.13.0.

## Resumo

O Baú (`StorageChestBlockEntity`) ganha **dois slots de upgrade**. Existem dois upgrades, cada um aceito uma vez só:

- **Upgrade de Mesa de Trabalho** (`crafting_upgrade`): uma mesa 3×3 dentro da tela do Baú, que se repõe com o estoque dele.
- **Upgrade de Compactação** (`compacting_upgrade`): compacta e descompacta sozinho as cadeias de itens (pedregulho → 1x → … → 9x do AllTheCompressed, pepita → lingote → bloco, gema → bloco), com um modo global e um alvo por cadeia.

Só o Baú recebe upgrades. Tanques, Bateria e Tanque de Source ficam como estão.

## Decisões aprovadas

1. **Cadeias pelas receitas, sem lista.** Um par entra quando há ida e volta pelas receitas de mesa: 1 de X sozinho na grade dá N de Y (N = 9 ou 4), e N de Y na grade cheia (3×3 para 9, 2×2 para 4) dá exatamente 1 de X. Sem a volta não entra (lã → tapete, areia → arenito, neve, quartzo). A ida é conferida com o `RecipeManager` de verdade (`getRecipeFor` com a grade montada), então tags nos ingredientes e receitas de qualquer tipo de mesa funcionam.
2. **Fundir fica de fora.** Cru → lingote não é compactação.
3. **Automática na entrada.** Um item de cadeia que entra no Baú (roteador, funil, outro mod, tela) põe a cadeia numa fila; ela é convertida no fim do tick, junto do aviso aos vizinhos (`StorageNeighbors`).
4. **Capacidade:** descompactar converte só o que cabe; o resto fica na forma alta. Nada se perde.
5. **Filtro:** a conversão interna ignora o filtro de entrada e o limite de estoque (o item já está dentro); respeita só a capacidade.
6. **Receitas dos upgrades:** Mesa = mesa de trabalho + Cristal de Sinal; Compactação = pistão + Cristal de Sinal + bloco de ferro (sem forma, `crafting_shapeless`).

## Regra da conversão

Uma cadeia é uma lista de formas `f0 … fk` com fatores `n1 … nk` (1 de `fi` = `ni` de `f(i-1)`). Com o alvo `T`, a conta é feita degrau a degrau, sem somar tudo em unidades base (8,6 bilhões de itens × 9^11 estouraria o `long`):

1. **Compactar abaixo do alvo**, de `f0` até `f(T-1)`: cada forma passa para a de cima o que fecha o fator (`c = count(fi) / n(i+1)`), e a sobra fica. Só diminui o total de itens, então nunca estoura nem a capacidade nem o `long`.
2. **Descompactar acima do alvo**, de `f(T+1)` para cima (a forma mais barata primeiro): `m` itens de `fi` viram `m × r` de `fT` (`r` = produto dos fatores entre as duas), limitado pela capacidade livre (`m ≤ livre / (r - 1)`) e pelo `long`. O que não cabe fica na forma alta. Nada se perde.
3. Sobra que não fecha um fator fica na forma de baixo: 10 pedregulhos com alvo 1x → 1 pedregulho 1x + 1 pedregulho.

A função pura (`CompactingMath`) devolve as contagens novas; o block entity aplica só as diferenças (tira primeiro, põe depois).

**Alvo de cada cadeia:** o específico, se a regra da cadeia existir; senão o global (Compactar = a forma mais alta, Descompactar = `f0`, Desligada = não mexe). Padrão ao pôr o upgrade: **Desligada** (decisão da revisão: Compactar pegaria todas as cadeias de um baú misto, como pó de redstone, trigo e carvão, e pararia as máquinas que puxam dali). Nada muda até o jogador escolher um modo ou um alvo.

**Limite de estoque conta a cadeia** (decisão da revisão): num Baú com o upgrade de Compactação, o limite de estoque de um item de cadeia, tanto o do filtro do Baú quanto o de estoque do roteador que entrega nele, conta a cadeia inteira na unidade do item limitado (11 blocos + 1 lingote = 100 lingotes). Sem isso, "manter 1000 lingotes" nunca seria atingido, porque os lingotes viram blocos, e o roteador mandaria para sempre. Sem o upgrade, a contagem é a de hoje.

**A Mesa desce uma forma na hora** (decisão da revisão): com o upgrade de Compactação no Baú, se falta o item exato para repor a grade (ou para o "+" do JEI) e ele é de cadeia, o Baú desce as formas de cima até ter o que precisa (1 bloco vira 9 lingotes por dentro) e a Mesa tira dali. A sobra fica no Baú e volta a ser compactada na próxima entrada daquela cadeia. O roteador continua vendo só a forma guardada.

## Casos de borda

- Item com cadeia 3×3 e 2×2 ao mesmo tempo: usa a 3×3.
- Item que forma dois blocos diferentes, ou ciclo (A → B → A): a primeira ligação achada na ordem estável (pelo id do item) vale; ciclos são cortados. O índice é determinístico.
- Itens com componentes (encantados, com nome, com dados) não compactam: só a pilha "pura" do item (`ItemStack` sem componentes além dos padrão) entra na cadeia.
- A pilha resultante de cada forma é `new ItemStack(item)` sem componentes; o Baú guarda por item + componentes, então a forma convertida casa com a que vem pelo roteador.
- O roteador e os outros mods veem só a forma guardada. Para tirar lingotes, o alvo da cadeia fica em lingote.

## Mesa de Trabalho

- Grade 3×3 com slots de verdade e o slot do resultado, no painel à direita da lista. Os 9 itens da grade ficam no block entity (NBT `craft_grid`) e viajam no drop (componente `storage_craft_grid`), como o filtro.
- **Tirar o resultado** consome a grade como a mesa vanilla (inclusive os restos, como o balde vazio) e depois repõe cada slot que diminuiu com o mesmo item (item + componentes) do estoque do Baú, até onde houver.
- **Shift + clique no resultado** repete a receita, repondo a grade a cada vez, até completar uma pilha do resultado (ou até faltar ingrediente, a receita mudar ou o inventário encher) e manda para o inventário.
- **Devolver** (botão) põe a grade no Baú; o que não couber vai para o inventário e depois para o chão.
- **JEI "+":** monta a receita puxando do Baú primeiro e do inventário depois.
- A receita da grade é procurada só quando a grade muda (cache do último resultado, como o `CraftingMenu`).
- Tirar o Upgrade de Mesa com a grade cheia: a grade volta para o Baú (e o excesso para o inventário).

## Compactação

- **Painel:** modo global (Desligada / Compactar / Descompactar) e a lista das cadeias **presentes no Baú**. Cada linha mostra o ícone da base, o nome, `‹ [forma alvo] ›`, o nome da forma e a etiqueta Global/Própria. As setas e a rodinha andam uma forma; clicar no ícone abre a cadeia inteira (10 a 12 formas) numa lista suspensa para escolher. "Global" volta a regra da cadeia para o modo global.
- **Onde fica a configuração:** no próprio item do upgrade (componente `compacting_rules`: modo global e o conjunto dos ids das formas alvo; a cadeia de cada regra é a que contém a forma). Mudar o upgrade de Baú leva as regras junto.
- Trocar o modo global ou uma regra põe na fila as cadeias afetadas que existem no Baú. Pôr o upgrade põe na fila todas as cadeias presentes (uma passada O(tipos), por evento).

## Tela

- Coluna de dois slots de upgrade à esquerda do painel do Baú (fora da largura do painel: o `hasClickedOutside` da tela considera a coluna e o painel lateral como dentro).
- O painel do upgrade escolhido abre à direita da lista; um clique no slot ocupado troca o painel; sem upgrade, sem painel. A lista continua redimensionável.
- Classes novas em `client/storage/upgrade/` (a `StorageListScreen` já passa de 500 linhas e só ganha os ganchos).

## Dados e protocolo

| O quê | Chave |
| --- | --- |
| Itens | `wirelessautomate:crafting_upgrade`, `wirelessautomate:compacting_upgrade` |
| NBT do Baú | `upgrades` (2 slots), `craft_grid` (9 slots) |
| Componentes do drop do Baú | `storage_upgrades`, `storage_craft_grid` (`ItemContainerContents`) |
| Componente do upgrade | `compacting_rules` |
| Protocolo | `ModPayloads.VERSION` de `38` para `39` (payloads novos do painel da Compactação) |

Payloads novos: o servidor manda as cadeias presentes e as regras (`CompactingStatePayload`) só com o painel aberto e só quando a lista de cadeias presentes ou as regras mudam; o cliente manda `CompactingRulePayload` (modo global, ou alvo de uma cadeia, ou voltar ao global). A Devolução da grade é uma ação nova do `StorageActionPayload`.

Chaves existentes de NBT, componentes e config **não mudam**.

## Custo

- **Parado custa zero.** Sem tick no Baú. Sem upgrade de Compactação, a inserção paga um teste de campo nulo.
- **Inserção com Compactação:** um `get` num mapa item → cadeia (`Reference2ObjectOpenHashMap`) e, se for de cadeia, um `add` num conjunto de cadeias sujas do Baú, que já entra na fila do `StorageNeighbors` pela marcação do conteúdo. Nenhuma alocação no caminho quente.
- **Conversão:** O(formas da cadeia), no máximo 12, uma vez por cadeia suja por tick. A conversão não suja a cadeia de novo.
- **Índice de cadeias:** montado uma vez quando o servidor sobe e a cada `/reload`, fora do jogo; com o ATM10 (~1.800 pares só do AllTheCompressed), mede-se o tempo e loga-se. Consulta O(1).
- **Mesa:** a receita só é procurada quando a grade muda; repor é O(9).
- **Rede:** o estado da Compactação vai ao cliente só com o painel aberto, e só quando muda (uma versão por Baú: lista de cadeias presentes + regras). A grade e os slots de upgrade usam a sincronização normal de slots do menu.
- **Memória:** o conjunto de cadeias sujas é criado sob demanda; o Baú sem upgrade não guarda nada a mais além de dois campos nulos.

## Interações e proteções (revisão de 30/10)

- **Duplicação pela Mesa com dois jogadores:** a grade é do Baú e o resultado é de cada menu. Se o jogador A muda a grade, o resultado do menu de B fica velho, e tirá-lo daria o item sem os ingredientes. Proteção: toda mudança da grade recalcula o resultado de **todos** os menus abertos naquele Baú, e o slot do resultado confere de novo, no `mayPickup` e antes de consumir, que a receita casa com a grade atual (senão não entrega e recalcula).
- **Duplicação pelo Shift + clique e pelo duplo clique:** o `quickMoveStack` de hoje guarda qualquer slot no Baú sem chamar `onTake`. Com o slot do resultado, isso guardaria o item feito sem consumir a grade. Proteção: o resultado e a grade têm caminho próprio, e o resultado não entra no "juntar tudo" do duplo clique (`canTakeItemForPickAll` falso, como o `CraftingMenu`).
- **Ids de cadeia depois do `/reload`:** o índice é remontado e os ids mudam. Uma cadeia suja guardada com o id velho converteria a cadeia errada. Proteção: o índice tem uma geração; o Baú com geração diferente descarta as sujas e marca tudo de novo.
- **Tela de Sinal:** a conversão não conta como entrada nem saída no vigia (`StorageWatch`), senão a vazão mostrada inventaria itens que nunca entraram.
- **Regras e mods removidos:** as regras guardam os ids das formas alvo como texto (`ResourceLocation`), não `Item`. Um mod removido não derruba a leitura do componente (o que apagaria o item do upgrade); a regra cuja forma sumiu é ignorada.
- **Regra presa à forma alvo, não à base:** se um `/reload` acrescentar uma forma abaixo da base (um mod novo que cria pepita), a regra continua achando a cadeia pela forma alvo.

## Cadeias de mods diferentes e a escolha da variante (decisão de 30/10, depois do teste do dono)

O dono testou com o Modern Industrialization e o Occultism no runClient: o bloco de prata crua do MI não aparecia. Cada mod tem a sua prata crua e o seu bloco, e a receita "bloco → 9" do Occultism aceita qualquer bloco da tag `c:storage_blocks/raw_silver`; a mesa usa a primeira receita que casa, então o bloco do MI vira prata do Occultism, e a volta não fecha.

1. **Alguma receita, não só a do jogador.** Um par entra quando existe **alguma** receita de mesa que faz cada sentido: a receita de volta candidata (1 → N) e uma receita de ida (N na grade → 1) que casa (`matches` + `assemble`) com N cópias da forma menor. Substitui a conferência pelo `getRecipeFor` (a decisão 1 de cima). Com isso, cada mod tem a sua cadeia (bloco do MI ↔ prata crua do MI; bloco do Occultism ↔ prata crua do Occultism).
2. **Variantes.** Duas cadeias são variantes do mesmo material quando têm o mesmo número de formas, os mesmos fatores e, em cada nível, as duas formas dividem pelo menos uma tag específica do namespace `c` (com `/`, como `c:raw_materials/silver`, `c:storage_blocks/raw_silver`, `c:ingots/silver`; as tags-pai sem `/`, como `c:ingots`, não contam). Os grupos se formam por união (A ~ B e B ~ C juntam os três).
3. **A escolha.** Cada grupo com mais de uma variante tem uma preferida: a escolhida pelo jogador (guardada nas regras do upgrade pela forma base dela, ids em texto, com o mesmo teto de `MAX_TARGETS`) ou, sem escolha, a primeira pelo id da forma base em ordem alfabética (o mod em ordem alfabética).
4. **A troca 1 por 1.** Num Baú com o upgrade, quando uma cadeia do grupo é convertida (alvo ≥ 0), primeiro os itens das outras variantes viram a preferida, nível a nível e 1 por 1 (bloco do Occultism → bloco do MI, prata crua do Occultism → prata crua do MI), por dentro (sem admissão nem vigia, como a conversão); depois a compactação normal da preferida. A troca é 1 por 1, então não muda o total nem a capacidade. Com a cadeia parada (modo Desligada sem alvo próprio), nada troca.
5. **Limite de estoque** de um item de cadeia com variantes conta o grupo inteiro no nível e na unidade do item limitado.
6. **Painel.** O grupo aparece como uma linha só (a da preferida). Quando há mais de uma variante, a linha ganha um botão pequeno com o nome curto do mod da preferida; o clique passa para a próxima variante (e a regra de alvo continua a valer pela forma, na variante nova). O grupo aparece na lista quando qualquer variante está no Baú.
7. **Custo.** Os grupos são montados junto com o índice, uma vez por carga de receitas (consulta O(1) item → grupo). A troca é O(formas × variantes) por cadeia suja, só com o upgrade.

## Fora

Fundir; autocrafting e fila de pedidos; mostrar todas as formas ao mesmo tempo (como a gaveta compactadora do Functional Storage); upgrades nos Tanques e na Bateria.

## Testes

- JUnit: a conta da conversão (`CompactingMath`) e a montagem das cadeias a partir dos pares (`ChainGraph`), com ciclo, ramificação e 2×2 × 3×3.
- GameTests (vanilla, sem o AllTheCompressed): o índice acha pepita → lingote → bloco de ferro, cru ↔ bloco cru, e não acha lã, arenito, neve; o Baú compacta na entrada pelo roteador e pelo handler; o alvo específico vence o global; descompactar para na capacidade; a Mesa repõe a grade pelo estoque; o drop leva upgrades e grade.
- e2e: a suíte `armazenamento` com os dois painéis (capturas `armazenamento-upgrade-*`).
- À mão (o dono): a farm da lâmpada no runClient com o AllTheCompressed (já no `runClient`).
