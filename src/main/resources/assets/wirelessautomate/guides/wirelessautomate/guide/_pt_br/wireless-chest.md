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
| **Básico** | 262.144 |
| **Avançado** | 16.777.216 |
| **Elite** | 1.073.741.824 |
| **Ultimate** | Sem limite |

Sobe de tier com os mesmos [Cartões de Upgrade](upgrade-cards.md) do roteador, no mundo ou na
bancada, **sem perder o conteúdo**.

## A tela

Clique com a mão vazia no Baú. A lista mostra cada tipo com a quantidade; passe o mouse para ver o
número exato.

| Quero... | Como |
| --- | --- |
| Achar um item | Digite na busca. **@mod** procura pelo mod (ex.: **@mekanism**). |
| Mudar a ordem | O botão ao lado da busca alterna: quantidade, nome e mod. |
| Pegar uma pilha | Clique no item. Botão direito: meia pilha. |
| Mandar direto para o inventário | **Shift** + clique no item. |
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

## Quebrar e levar

Quebre o Baú: o item leva **todo o conteúdo e o filtro**, e o tooltip mostra o total e os tipos.
Coloque de novo e está tudo lá. Cheio, ele sempre vira item: no criativo, sem picareta ou numa
explosão, nada se perde.

## Com outros blocos

- **Funis, AE2, Refined Storage e outros mods** veem o Baú como um inventário comum, um slot por tipo.
- **Comparador**: o sinal sobe com a ocupação (no Ultimate, 1 com qualquer coisa dentro).
- Entre um Baú Wireless e um baú comum o roteador também é rápido, mas o baú comum continua
  andando uma pilha por vez.

## Receita

<RecipeFor id="wirelessautomate:storage_chest" />
