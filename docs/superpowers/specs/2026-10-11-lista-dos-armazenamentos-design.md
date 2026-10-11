# Lista dos armazenamentos: correções e pedidos do Lucas (1.8.0)

Origem: conversa com o Lucas (servidor Infinity Legacy II, 11/10/2026) e o log do crash dele. Decisões do dono na mesma conversa.

## Crash ao abrir o Baú

`Comparison method violates its general contract!` em `StorageListScreen.rebuild`. A ordenação lia a quantidade por busca no hash e o nome sob demanda; o nome de alguns itens de outros mods mexe nos componentes da pilha, o hash muda e a quantidade vira 0 no meio do `sort`. Agora a tela tira uma foto (quantidade pela iteração do mapa, `StorageListView.entries()`, e nome e mod por identidade) e ordena a foto. A grade e o tooltip mostram a quantidade da foto.

## Busca começa vazia

Decisão do dono: limpar sempre. A tela não guarda mais a última busca (o texto guardado aparecia rolado e confuso). Vale também ao voltar do filtro de entrada.

## Botão direito: metade do que há

Com pelo menos uma pilha guardada, continua meia pilha cheia (32). Com menos, a metade do que há, arredondada para cima como no vanilla (10 → 5, 3 → 2, 1 → 1). Regra pura em `StorageMath.halfTake`, com JUnit, e o caso no GameTest `screenActionsMoveItems`.

## Busca por `#`

`#texto` procura nas tags do tipo (`c:gems/diamond`) e nas linhas do tooltip (itens), como no JEI. O `#` vale até o próximo `@` ou `#`, então `#not yet eaten` procura a frase inteira (o exemplo do Lucas, a linha de tooltip do mod de comida). As palavras antes do primeiro `#` continuam sendo do nome e `@mod`, do mod. Fluidos: tags. Químicos: só nome e mod. A divisão da busca fica em `client/StorageSearch` (pura, com JUnit); o texto de tags e tooltip é calculado só com um `#` na busca, guardado por tipo e refeito quando a busca muda.

## Shift trava a ordem (como o AE2)

Decisão do dono: igual ao AE2. Enquanto o Shift está apertado, a lista não se reordena: as quantidades atualizam, um tipo que acaba fica no lugar como célula apagada (sem número) e, se voltar, retoma o mesmo lugar; um tipo novo entra no fim. Ao soltar, a lista se ordena de novo. Mudar a busca ou a ordem com o Shift apertado refaz a lista (digitar maiúsculas usa o Shift).

## Fora

Tags de químicos na busca; ordenar por tag.
