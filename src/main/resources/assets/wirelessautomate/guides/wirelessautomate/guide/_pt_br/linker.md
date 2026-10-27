---
navigation:
  title: Vinculador
  icon: wirelessautomate:linker
  parent: index.md
  position: 7
item_ids:
  - wirelessautomate:linker
---


# Vinculador

<ItemImage id="wirelessautomate:linker" scale="2" float="left" />

Liga **máquinas** a um roteador de alcance, uma a uma ou uma área inteira, e abre o roteador que chega
numa máquina.

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Modos** | **Vincular** (padrão) e **Abrir**. Shift + roda do mouse troca de um para o outro. |
| **Vincular** | Escolha um roteador com o Upgrade de Alcance e clique nas máquinas: cada clique vincula a máquina a ele (um clique numa já vinculada a desvincula). A tela vincula uma área inteira de uma vez. |
| **Abrir** | Clique numa máquina: abre a tela do roteador que chega nela, já na máquina. Se chegam vários (o preso nela e os de alcance), uma lista curta deixa escolher. |
| **Redes** | O Vinculador não mexe nelas: escolha a rede de cada face na linha **Rede** da tela do roteador ou pelo [Tablet](network-tablet.md). |
| **Tela de Sinal** | Clique numa [Tela de Sinal](signal-screen.md) para escolhê-la e depois num Baú, Bateria ou Tanque do mod: o armazenamento fica ligado à Tela (outro clique nele desliga). Sem limite de distância nem de dimensão. |

## Comandos

| Gesto | Vincular | Abrir |
| --- | --- | --- |
| Clique num roteador | Escolhe o roteador | — |
| Clique numa Tela de Sinal | Escolhe a Tela | — |
| Clique num Baú, Bateria ou Tanque do mod | Com uma Tela escolhida, liga ou desliga o armazenamento a ela | Abre o roteador que chega nele |
| Clique numa máquina | Vincula ou desvincula a máquina | Abre o roteador que chega nela |
| Clique no ar | Abre a tela | Mostra a dica do modo |
| Shift + roda do mouse | Passa para **Abrir** | Passa para **Vincular** |
| Shift + clique em dois blocos | Marca os cantos da área | — |

## Na tela (modo Vincular)

| Parte | Como usar |
| --- | --- |
| **Roteador escolhido** | O nome, a posição e as vagas usadas (por exemplo **7/32 máquinas**). Se o roteador ainda não vincula (sem o Upgrade de Alcance), o motivo aparece aqui. |
| **Vincular / Desvincular** | No cabeçalho. Em **Desvincular**, os cliques e o botão da área tiram as máquinas do roteador em vez de pôr. |
| **Prévia** | A área marcada vista de cima, com os cantos e as máquinas do roteador dentro dela. |
| **Vincular área** | Liga ao roteador as máquinas da área que ele alcança, as mais perto primeiro, até o limite do tier. Em **Desvincular**, vira **Desvincular N** e tira as que estão dentro. O resultado aparece embaixo da prévia. |
| **Limpar** | Apaga os cantos. |

Arraste a borda ou o canto para aumentar a tela; a prévia cresce.

A área pode ter até 262.144 blocos (por exemplo 64 × 64 × 64), e você precisa estar a até 64
blocos dela.

## A lista do modo Abrir

Quando mais de um roteador chega na máquina, a lista mostra uma linha por roteador: o nome, se ele está
**preso** na máquina ou chega por **alcance** e a distância. Clique numa linha para abrir. Só entram
roteadores ao seu alcance e que você pode tocar.

## Ligar armazenamentos a uma Tela

Clique com o Vinculador numa [Tela de Sinal](signal-screen.md): ela fica escolhida e a action bar mostra quantos armazenamentos ela já tem. Depois clique num **Baú, Bateria ou Tanque** do mod para ligá-lo à Tela; clique de novo nele para desligar. Não há limite de distância nem de dimensão. Você precisa poder editar a Tela. Com o armazenamento ligado, a Tela mostra o conteúdo dele ao vivo; se o chunk estiver descarregado, ela mantém o último número, apagado.

## Exemplo: ligar as máquinas de uma sala

| Passo | O que fazer |
| --- | --- |
| **1** | Ponha o Upgrade de Alcance num roteador e clique nele com o Vinculador. |
| **2** | Com Shift, clique em dois cantos da sala. |
| **3** | Clique no ar para abrir a tela e em **Vincular área**: as máquinas dentro do alcance entram, até o limite do tier. |
| **4** | No modo **Abrir**, clique numa delas para configurar a face no roteador. |

## Receita

<RecipeFor id="wirelessautomate:linker" />
