---
group: integracoes
navigation:
  title: Doca de Padrões
  icon: wirelessautomate:pattern_dock
  parent: index.md
  position: 17
item_ids:
  - wirelessautomate:pattern_dock
---


# Doca de Padrões

<ItemImage id="wirelessautomate:pattern_dock" scale="2" float="left" />

Leva os pedidos de um **Pattern Provider** do AE2 ou de um **Autocrafter** do Refined Storage 2 até as máquinas que
fazem a receita. Cada **lado** da Doca cuida de **um tipo de máquina**. Precisa do **AE2** ou do **RS2** para receber
pedidos.

<br clear="all" />

## Como usar

1. Coloque a Doca e encoste um **Pattern Provider** do AE2 (ou um **Autocrafter** do RS2, virado para ela) num lado
   dela.
2. Com o [Vinculador](linker.md), clique **nesse lado** da Doca. Depois dê **Ctrl + clique** numa máquina: as iguais
   que estão por perto entram naquele lado. Um clique simples vincula só uma máquina; numa já vinculada, desvincula.
3. Ponha no provedor **os padrões dessas máquinas**. Pronto: a CPU de crafting do AE2 (ou a rede do RS2) já pode
   pedir.

Quando você vincula a primeira máquina de um lado, a Doca descobre sozinha em que face entram os ingredientes e de qual
sai o resultado. A action bar mostra o que ela achou.

## O que a Doca faz sozinha

- Entrega **cada pedido inteiro** a uma máquina livre do lado.
- As máquinas **se revezam**: o próximo pedido vai para a seguinte.
- O resultado vai **direto para a rede** do provedor (a ME do AE2 ou a do RS2), sem passar pelo inventário dele, e o
  pedido o conta ali.
- Quando **todas as máquinas estão cheias**, o pedido espera. **Nada se perde**: se você quebrar a Doca com um resto
  de pedido esperando, ele vai junto no item ("Pedido pendente") e volta ao mesmo lado quando você a recoloca.

## Quem faz o quê

A Doca **não gasta energia nem combustível**. Ela só leva os pedidos.

- **Energia:** vincule as máquinas também a um [roteador](router.md). É ele que leva a energia até elas.
- **Combustível:** o que a receita gasta (carvão numa fornalha, por exemplo) vai **dentro do padrão**, como um
  ingrediente.

## Dicas

- **Multiplique os padrões até um lote que a máquina faz num tempo bom** (×8, ×64). Cada empurrão da CPU vai inteiro
  para uma máquina e a enche: com o padrão de 1 minério, cada máquina recebe um de cada vez; com o de 64, recebe um
  lote que a mantém ocupada. **Mais coprocessadores** na CPU continuam ajudando: são mais empurrões por vez, e mais
  máquinas trabalhando juntas. Um pedido pequeno ocupa poucas máquinas.
- **Não use a trava "até o resultado"** do Pattern Provider: o resultado não passa por ele, então a trava nunca solta.
  A linha do lado avisa ("Tire a trava do provedor").
- **Mais padrões no RS2:** encadeie **Autocrafters** (um virado para o outro, o último virado para a Doca). Os padrões
  de todos chegam ao mesmo lado.
- **As travas do Autocrafter:** "até receber todos os resultados" funciona com a Doca. "Até a máquina esvaziar" conta
  o lado inteiro como uma máquina, que só fica vazia 15 s depois do último movimento: um lote por vez no lado, com
  esse intervalo entre eles. Para usar várias máquinas ao mesmo tempo, deixe sem trava.
- **O produto final no RS2 precisa de lugar no disco.** O RS2 só conta o resultado final do pedido depois de guardá-lo:
  sem lugar nos discos, ele fica na máquina, o lado mostra "Provedor cheio" e espera até abrir espaço.
- **Padrões de bancada** (bancada, cortador de pedra, forja) são do Molecular Assembler. A Doca os recusa.
- **Outro tipo de máquina?** Use **outro lado**. Um lado só cuida de um tipo, e a Doca tem seis lados.
- **Se a Doca errar as faces** de uma máquina, use o [Configurador](configurator.md) numa máquina do lado, com o gesto
  de sempre. O resultado vale para o lado inteiro.

## Alcance

O raio e o máximo de máquinas dependem do tier da Doca. As máquinas ficam dentro do raio, medido em cada eixo, e o
máximo conta a **Doca inteira**, somando os **seis lados**. O Cartão de Upgrade sobe o tier.

| Tier | Raio | Máquinas |
| --- | --- | --- |
| Básico | 6 | 8 |
| Avançado | 12 | 16 |
| Elite | 16 | 32 |
| Esmeralda | 24 | 48 |
| Allthemodium | 32 | 64 |
| Vibranium | 48 | 96 |
| Unobtainium | 64 | 128 |
| Ultimate | 96 | 256 |

Os números são os padrões; o JEI mostra os do seu pack. Se a config baixar, as máquinas de fora continuam vinculadas,
mas não recebem pedidos. Máquina em área descarregada também espera.

## O que a tela mostra

Clique direito na Doca. A tela tem uma linha por lado, com o nome da máquina, quantas são e quantas estão trabalhando,
e o estado:

| Estado | O que quer dizer |
| --- | --- |
| Sem provedor neste lado | Há máquinas, mas nada encostado. Encoste um provedor |
| Nenhuma máquina vinculada | Há provedor, mas nenhuma máquina. Vincule com o Vinculador |
| Tire a trava do provedor | O provedor está em "travar até o resultado", que nunca solta com a Doca |
| Padrão de bancada recusado | O último pedido era de bancada, cortador ou forja |
| Pedido com item e fluido recusado | O último pedido misturava itens e fluidos |
| Provedor cheio | O resultado espera lugar: a rede cheia, sem energia ou sem canal |
| Todas cheias | Nenhuma máquina aceitou o último pedido; ele espera |
| Pedido pendente | A máquina ainda não recebeu o resto do último pedido; o lado espera por ele |
| Trabalhando | Uma máquina recebeu um pedido ou devolveu um resultado há pouco |
| Pronto | Tudo certo, esperando pedidos |
| livre | Nada encostado e nenhuma máquina |

Ao lado do estado a tela avisa quantas máquinas estão **fora do alcance** ou **em área descarregada**.

## Limites

- **Um tipo de máquina por lado.** Um lado de Fornalha não aceita Alto-forno.
- **Pedidos só de itens ou só de fluidos**, nesta versão. Um pedido com os dois é recusado.
- **Mesma dimensão** e dentro do raio do tier.
- Uma máquina pertence a **um lado de uma Doca só**.
- A Doca precisa do **AE2** ou do **RS2** para receber pedidos.
