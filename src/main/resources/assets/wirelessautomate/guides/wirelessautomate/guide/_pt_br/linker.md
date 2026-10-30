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

Liga **máquinas** a um roteador de alcance, uma a uma ou uma área inteira. Também abre o roteador que
chega numa máquina.

<br clear="all" />

## Como usar

1. Ponha o Upgrade de Alcance num roteador e clique nele com o Vinculador.
2. Clique numa máquina para vinculá-la. Clique de novo para desvincular.
3. Para uma sala inteira: com Shift, clique em dois cantos, clique no ar para abrir a tela e clique
   em **Vincular área**.
4. Passe para o modo **Abrir** (Shift + roda do mouse) e clique numa máquina para configurar a face
   dela no roteador.

O Vinculador não mexe nas redes: escolha a rede de cada face na linha **Rede** da tela do roteador ou
pelo [Tablet](network-tablet.md).

## Gestos

| Gesto | Vincular | Abrir |
| --- | --- | --- |
| Clique num roteador | Escolhe o roteador | — |
| Clique numa máquina | Vincula ou desvincula | Abre o roteador que chega nela |
| Clique numa Tela de Sinal | Escolhe a Tela | — |
| Clique num Baú, Bateria ou Tanque do mod | Com uma Tela escolhida, liga ou desliga | Abre o roteador que chega nele |
| Clique no ar | Abre a tela | Mostra a dica do modo |
| Shift + clique em dois blocos | Marca os cantos da área | — |
| Shift + roda do mouse | Passa para **Abrir** | Passa para **Vincular** |

## A tela (modo Vincular)

- Mostra o roteador escolhido e as vagas usadas (por exemplo **7/32 máquinas**). Se ele ainda não
  vincula (sem o Upgrade de Alcance), o motivo aparece ali.
- **Vincular área** liga as máquinas da área que o roteador alcança, as mais perto primeiro, até o
  limite do tier.
- **Desvincular**, no cabeçalho, faz os cliques e o botão da área tirarem máquinas em vez de pôr.
- **Limpar** apaga os cantos.

A área pode ter até 262.144 blocos (por exemplo 64 × 64 × 64), e você precisa estar a até 64
blocos dela.

## Modo Abrir

Se mais de um roteador chega na máquina (o preso nela e os de alcance), aparece uma lista com o nome,
se ele está **preso** ou chega por **alcance**, e a distância. Clique numa linha para abrir. Só entram
roteadores ao seu alcance e que você pode tocar.

## Ligar armazenamentos a uma Tela

1. Clique numa [Tela de Sinal](signal-screen.md). A action bar mostra quantos armazenamentos ela já tem.
2. Clique num **Baú, Bateria ou Tanque** do mod para ligá-lo. Clique de novo para desligar.

Sem limite de distância nem de dimensão. Você precisa poder editar a Tela. Se o chunk do
armazenamento estiver descarregado, a Tela mantém o último número, apagado.

## Receita

<RecipeFor id="wirelessautomate:linker" />
