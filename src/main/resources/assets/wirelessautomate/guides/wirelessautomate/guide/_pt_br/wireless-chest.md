---
navigation:
  title: Baú Wireless
  icon: wirelessautomate:storage_chest
  parent: index.md
  position: 11
item_ids:
  - wirelessautomate:storage_chest
---


# Baú Wireless

<ItemImage id="wirelessautomate:storage_chest" scale="2" float="left" />

Um baú sem slots: guarda **milhões** de itens, por tipo, quantos tipos você quiser. O limite é só o
total de itens do tier. Preso a um roteador, ele é o parceiro ideal da rede: entre dois Baús
Wireless, um tipo inteiro passa **de uma vez**, sem o teto de uma pilha por vez dos baús comuns.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_chest" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_chest" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    Um tipo inteiro por vez: milhões de itens num instante
  </LineAnnotation>
  <BlockAnnotation x="0" y="0" z="0" color="#45d6cc">
    Baú Elite · roteador com a face de cima em **Extrai**
  </BlockAnnotation>
  <BlockAnnotation x="4" y="0" z="0" color="#a46cff">
    Baú Ultimate · roteador com a face de cima em **Insere**
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## Capacidade

Total de itens, todos os tipos somados. O tooltip do item mostra o valor do seu servidor.

| Tier | Itens |
| --- | --- |
| **Básico** | 32.768 |
| **Avançado** | 262.144 |
| **Elite** | 2.097.152 |
| **Esmeralda** | 16.777.216 |
| **Allthemodium¹** | 134.217.728 |
| **Vibranium¹** | 1.073.741.824 |
| **Unobtainium¹** | 8.589.934.592 |
| **Ultimate** | Sem limite |

¹ Só com o mod Allthemodium.

Sobe de tier com os mesmos [Cartões de Upgrade](upgrade-cards.md) do roteador, no mundo ou na
bancada, **sem perder o conteúdo**.

## A tela

Clique com a mão vazia no Baú. A lista mostra cada tipo com a quantidade; passe o mouse para ver o
número exato.

| Quero... | Como |
| --- | --- |
| Achar um item | Digite na busca. **@mod** procura pelo mod (ex.: **@mekanism**) e **#** pelas tags e pelo texto do tooltip (ex.: **#ingots**, **#not yet eaten**). A busca começa vazia cada vez que abre. |
| Mudar a ordem | O botão ao lado da busca alterna: quantidade, nome e mod. |
| Pegar uma pilha | Clique no item. Botão direito: meia pilha (com menos de uma pilha guardada, a metade do que tem). |
| Mandar direto para o inventário | **Shift** + clique no item. Segure **Shift** e arraste pela lista: uma pilha de cada item por onde passar. Enquanto o **Shift** estiver apertado, os itens não trocam de lugar. |
| Encher o inventário com um item | Com qualquer item no cursor, **Shift** + clique duas vezes no item. |
| Mover um item por vez | Rodinha sobre o item: para baixo tira um, para cima guarda um do inventário. Sobre um item do inventário: para baixo guarda um, para cima puxa um do baú. |
| Guardar o que está no cursor | Clique em qualquer lugar da lista. Botão direito: um só. |
| Guardar do inventário | **Shift** + clique no item do inventário. |

A barra embaixo da lista mostra quanto do tier já está ocupado.

## Filtro de entrada

O botão **Filtro**, no alto da tela, abre a mesma tela de [filtros](filters.md) do roteador. Ele
decide o que pode **entrar** no Baú, por qualquer caminho: roteador, funil, outro mod ou a tela.

| Na entrada do filtro | No Baú |
| --- | --- |
| Lista branca | Só entra o que está na lista. |
| Lista negra | Entra tudo, menos o que está na lista. |
| **Estoque** N | Guarda **até N** daquele item; o resto fica onde estava. |

A bolinha do botão acende na cor do tier quando há um filtro. Cartões de Filtro também servem:
importe e exporte pela tela do filtro.

## Upgrades

O Baú tem **dois slots de upgrade**, à esquerda da lista. Cada upgrade entra uma vez só. Clique
num slot com o upgrade no cursor para pôr; clique na aba ao lado do slot ocupado para abrir o painel
dele, à direita da lista. Clique de novo na aba do painel aberto para escondê-lo; ele fica escondido,
mesmo em outros Baús, até você clicar numa aba.

| Upgrade | Receita |
| --- | --- |
| <ItemImage id="wirelessautomate:crafting_upgrade" /> **Upgrade de Mesa de Trabalho** | Mesa de trabalho + Cristal de Sinal. |
| <ItemImage id="wirelessautomate:compacting_upgrade" /> **Upgrade de Compactação** | Pistão + Cristal de Sinal + bloco de ferro (sem forma). |

### Mesa de Trabalho

Uma mesa 3×3 dentro do Baú. Monte a receita na grade e pegue o resultado.

- A grade **se repõe sozinha** com o que o Baú tem.
- **Shift** + clique no resultado: repete a receita até uma pilha e manda para o inventário.
- **Devolver**: a grade volta para o Baú.
- No **JEI**, o botão **+** monta a receita puxando do Baú e depois do inventário.

### Compactação

Junta e separa sozinha as cadeias de itens: pepita, lingote, bloco; ou pedregulho até o 9x do
AllTheCompressed. Só entra a cadeia que a mesa de trabalho também desfaz. Itens com encantamento ou
nome não compactam.

1. Pôr o upgrade não muda nada: o modo começa **Desligada**.
2. No painel, escolha o modo para todas as cadeias: **Compactar** (a forma mais alta) ou **Descompactar** (a base).
3. Para uma cadeia só, use as setas ou a rodinha na linha dela; clique no ícone para escolher a forma numa lista. **Global** volta ao modo geral.

O alvo vale para os dois lados: o que está **abaixo** dele é compactado até ele, e o que está **acima** é
descompactado até ele. Com o alvo em lingote, pepitas viram lingotes e blocos viram lingotes.

- A sobra que não fecha um fator fica na forma de baixo: 10 lingotes com alvo em bloco dão 1 bloco e 1 lingote.
- Descompactar converte **só o que cabe** no Baú; o resto fica na forma alta, sem perder nada.
- O roteador vê só a forma guardada: para tirar lingotes, deixe o alvo da cadeia em lingote.
- O limite de **Estoque** de um item da cadeia conta a cadeia inteira (11 blocos e 1 lingote valem 100 lingotes).
- O mesmo material de mods diferentes (a prata crua de dois mods) é uma linha só, com o nome do mod embaixo: clique nele para escolher em qual mod a Compactação junta tudo.
- A linha de uma cadeia com regra própria (alvo ou mod escolhido) fica na lista, apagada, mesmo sem nenhum item no Baú: a regra continua valendo. Clique na etiqueta Própria para voltar a Global: a regra sai e a linha some.
- Com a Compactação, a Mesa desce uma forma quando falta o item exato.

## Quebrar e levar

Quebre o Baú: o item leva **todo o conteúdo e o filtro**, e o tooltip mostra o total e os tipos.
Coloque de novo e está tudo lá. Cheio, ele sempre vira item: no criativo, sem picareta ou numa
explosão, nada se perde.

Os **upgrades e os itens da grade** da Mesa também vão no item, mesmo com o Baú vazio, e voltam quando
ele é colocado. Se o bloco sumir sem virar item (um comando, outro mod), eles caem no chão.

## Com outros blocos

- **Funis, AE2, Refined Storage e outros mods** veem o Baú como um inventário comum, um slot por tipo.
- **Comparador**: o sinal sobe com a ocupação (no Ultimate, 1 com qualquer coisa dentro).
- Entre um Baú Wireless e um baú comum o roteador também é rápido, mas o baú comum continua
  andando uma pilha por vez.

## Receita

<RecipeFor id="wirelessautomate:storage_chest" />
