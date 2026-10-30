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

Guarda **milhões** de itens, de quantos tipos quiser. Entre dois Baús Wireless, um tipo inteiro
passa **de uma vez**.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_chest" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_chest" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    Um tipo inteiro por vez
  </LineAnnotation>
  <BlockAnnotation x="0" y="0" z="0" color="#45d6cc">
    Baú Elite · face de cima em **Extrai**
  </BlockAnnotation>
  <BlockAnnotation x="4" y="0" z="0" color="#a46cff">
    Baú Ultimate · face de cima em **Insere**
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## Como usar

1. Coloque o Baú e prenda um roteador nele.
2. Configure a face na aba Itens do roteador.
3. Clique no Baú com a mão vazia para ver o que tem dentro.

## Capacidade

Total de itens, todos os tipos somados.

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

Sobe de tier com os [Cartões de Upgrade](upgrade-cards.md), no mundo ou na bancada, **sem perder o conteúdo**.

## A tela

| Gesto | O que faz |
| --- | --- |
| Digitar na busca | **@mod** busca por mod (ex.: **@mekanism**); **#** por tag e tooltip (ex.: **#ingots**). |
| Botão ao lado da busca | Muda a ordem: quantidade, nome ou mod. |
| **Clique** no item | Pega uma pilha. Botão direito: meia. |
| **Shift + clique** no item | Manda a pilha para o inventário. Arrastando: uma de cada. |
| **Shift + clique duplo** com item no cursor | Enche o inventário com aquele item. |
| **Rodinha** sobre o item | Para baixo tira um, para cima guarda um. |
| **Clique** na lista com item no cursor | Guarda tudo. Botão direito: um só. |
| **Shift + clique** no inventário | Guarda o item. |

## Filtro de entrada

O botão **Filtro** abre um [filtro](filters.md) que vale para **tudo que entra**: roteador, funil,
outro mod ou a tela.

| Na entrada do filtro | No Baú |
| --- | --- |
| Lista branca | Só entra o que está na lista. |
| Lista negra | Entra tudo, menos o que está na lista. |
| **Estoque** N | Guarda **até N** daquele item; o resto fica onde estava. |

## Upgrades

Dois slots, à esquerda da lista. Clique no slot com o upgrade no cursor; a aba ao lado abre o painel.

| Upgrade | Receita |
| --- | --- |
| <ItemImage id="wirelessautomate:crafting_upgrade" /> **Upgrade de Mesa de Trabalho** | Mesa de trabalho + Cristal de Sinal. |
| <ItemImage id="wirelessautomate:compacting_upgrade" /> **Upgrade de Compactação** | Pistão + Cristal de Sinal + bloco de ferro (sem forma). |

### Mesa de Trabalho

Uma mesa 3×3 dentro do Baú.

- A grade **se repõe sozinha** com o que o Baú tem.
- **Shift** + clique no resultado: repete até uma pilha.
- No **JEI**, o **+** monta a receita puxando do Baú e depois do inventário.

### Compactação

Junta e separa sozinha cadeias como pepita, lingote, bloco (ou pedregulho até o 9x do
AllTheCompressed). Itens encantados ou com nome ficam de fora.

1. O upgrade começa **Desligado**.
2. No painel, escolha **Compactar** (forma mais alta) ou **Descompactar** (a base) para todas as cadeias.
3. Para uma cadeia só, use as setas na linha dela. **Global** volta ao modo geral.

Com o alvo em lingote, pepitas **e** blocos viram lingotes.

- A sobra fica na forma de baixo: 10 lingotes com alvo em bloco dão 1 bloco e 1 lingote.
- Descompactar converte **só o que cabe**; o resto fica na forma alta.
- O roteador vê só a forma guardada: para tirar lingotes, deixe o alvo em lingote.
- O **Estoque** conta a cadeia inteira (11 blocos e 1 lingote valem 100 lingotes).
- O mesmo material de mods diferentes é uma linha só: clique no nome do mod para escolher em qual juntar.
- Uma cadeia com regra própria fica na lista, apagada, mesmo vazia.
- Com a Compactação, a Mesa desce uma forma quando falta o item exato.

## Quebrar e levar

O item leva **o conteúdo, o filtro, os upgrades e a grade da Mesa**. Cheio, ele sempre vira item,
até no criativo ou numa explosão.

## Com outros blocos

- **Funis, AE2, Refined Storage e outros mods** o veem como um inventário comum, um slot por tipo.
- **Comparador**: o sinal sobe com a ocupação (no Ultimate, 1 com qualquer coisa dentro).

## Receita

<RecipeFor id="wirelessautomate:storage_chest" />
