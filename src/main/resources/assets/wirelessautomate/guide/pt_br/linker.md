---
group: ferramentas
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

Liga **máquinas** a um roteador de alcance, uma a uma ou todas as do mesmo bloco. Também abre o
roteador que chega numa máquina.

<br clear="all" />

## Como usar

1. Ponha o Upgrade de Alcance num roteador e clique nele com o Vinculador. Clique nele de novo para
   soltá-lo.
2. Clique numa máquina para vinculá-la. Clique nela de novo para desvincular. Se o grupo dela fica
   vazio, os cartões dele voltam para o seu inventário.
3. Para vincular todas as máquinas iguais de uma vez, segure **Ctrl** (**Cmd** no Mac) e clique numa
   delas: entram as do mesmo bloco no raio do roteador, as mais perto primeiro, até o limite do
   tier. A action bar diz quantas, por exemplo **Vinculou 7 Fornalhas**. Correndo, o Ctrl é o da
   corrida e o clique vale como um clique normal.
4. Para desvincular todas as iguais de uma vez, faça o mesmo gesto numa máquina que já está
   vinculada.
5. Clique no ar para ver o roteador escolhido. **Shift** + clique no ar o solta.
6. Passe para o modo **Abrir** (Shift + roda do mouse) e clique numa máquina para configurar a face
   dela no roteador.

O Vinculador não mexe nas redes: escolha a rede de cada face na linha **Rede** da tela do roteador ou
pelo [Tablet](network-tablet.md).

## Gestos

| Gesto | Vincular | Abrir |
| --- | --- | --- |
| Clique num roteador | Escolhe o roteador | — |
| Clique numa máquina | Vincula ou desvincula | Abre o roteador que chega nela |
| Ctrl (Cmd no Mac) + clique numa máquina | Vincula as do mesmo bloco no raio; se ela já está vinculada, desvincula todas as do mesmo bloco | — |
| Clique numa Tela de Sinal | Escolhe a Tela | — |
| Clique num Baú, Bateria ou Tanque do mod | Com uma Tela escolhida, liga ou desliga | Abre o roteador que chega nele |
| Clique no ar | Mostra o roteador escolhido | Mostra a dica do modo |
| Shift + clique no ar | Solta o roteador e a Tela escolhidos | Mostra a dica do modo |
| Shift + roda do mouse | Passa para **Abrir** | Passa para **Vincular** |

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
