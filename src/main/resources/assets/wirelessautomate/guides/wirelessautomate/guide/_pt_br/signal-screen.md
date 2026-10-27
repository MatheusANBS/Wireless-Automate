---
navigation:
  title: Tela de Sinal
  icon: wirelessautomate:signal_screen
  parent: index.md
  position: 20
item_ids:
  - wirelessautomate:signal_screen
---


# Tela de Sinal

<ItemImage id="wirelessautomate:signal_screen" scale="2" float="left" />

Um painel que você prende numa parede e estica pelo tamanho que quiser, de **1 × 1 até 32 × 32 blocos**. Ele mostra os
números das suas [redes](networks.md) ao vivo: o que entra, o que sai e quantas faces estão movendo.

<br clear="all" />

## Como colocar

1. Clique com a Tela na **lateral de um bloco sólido**: ela fica rente à parede, virada para você. Não dá para pôr no
   chão nem no teto, e ela cai se a parede atrás dela sair.
2. Clique nela com a **mão vazia** para abrir a tela de configuração. Só quem a colocou (e os operadores) pode
   configurar; os outros só olham.

## Escolher o tamanho

Na aba **Tamanho**:

| Parte | Como usar |
| --- | --- |
| **Largura** e **Altura** | ‹ e › mudam de 1 em 1 bloco; com **Shift**, de 4 em 4. |
| **Cresce a partir do bloco para** | ↗ cima e direita, ↖ cima e esquerda ou ✥ centro: onde o bloco que você colocou fica dentro da tela. |
| **Prévia** | A parede em volta, com a área da tela. A célula vermelha é a que impede o tamanho. |
| **Aplicar** | Estica a tela. Fica desligado enquanto algo impede. |

A tela grande **não ocupa blocos**: ela é desenhada sobre a parede. Para caber, cada bloco da área precisa de
**parede sólida atrás** e de **espaço livre na frente** (ar ou grama, por exemplo; uma tocha impede), e a área não pode
cruzar outra Tela.

## O que mostrar

Na aba **Conteúdo**, escolha uma **rede** (só aparecem as que você pode usar) e um **tipo** (Itens, Fluidos, Energia...)
e clique em **Aplicar o Painel de rede**. A Tela passa a mostrar o nome da rede, o que **entra** (▲), o que **sai** (▼) e
quantas faces estão **movendo** (⇄), com a unidade do tipo. **Limpar** (dois cliques) apaga o conteúdo.

Os números chegam uma vez por segundo enquanto há alguém perto. Uma rede privada da qual quem colocou a Tela perdeu o
acesso mostra **—**.

## Receita

<RecipeFor id="wirelessautomate:signal_screen" />
