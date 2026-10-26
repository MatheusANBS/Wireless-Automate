---
navigation:
  title: Reparador de Cristais
  icon: wirelessautomate:crystal_repairer
  parent: index.md
  position: 20
item_ids:
  - wirelessautomate:crystal_repairer
---


# Reparador de Cristais

<ItemImage id="wirelessautomate:crystal_repairer" scale="2" float="left" />

Um bloco que **conserta os cristais rachados** do [Ressonador de Sinal](signal-resonator.md): o cristal volta cheio, com
a carga e a integridade cheias. É a mesma coisa que refazer o cristal na bancada, só que sem ficar na bancada e com
**até oito reparos ao mesmo tempo**. Não tem tier e não precisa estar perto de um Ressonador.

<br clear="all" />

## Como usar

1. Coloque o Reparador e dê **energia** a ele. A energia entra por **qualquer face**: um cabo, um roteador no modo
   **Insere** com a Bateria ou o gerador do outro lado, ou uma Bateria Wireless. Nenhum roteador tira energia dele.
2. Ponha os **cristais rachados** nos oito slots de entrada (um por linha) e os **Cristais de Sinal cheios** no slot do
   meio. Pode ser pela tela (clique direito), por um roteador preso ao bloco ou por um funil: cada slot só aceita o
   que cabe nele.
3. Cada linha começa sozinha quando tem um rachado, **4 Cristais de Sinal cheios** do estoque e a energia do reparo.
   Os reparos levam **7 segundos** e as oito linhas andam juntas.
4. O cristal consertado vai para os slots de saída. Tire-os pela tela, por um roteador ou por um funil. Se a saída
   está cheia, a linha espera.

A tela mostra a energia do bloco, o progresso de cada linha e o que falta quando nada anda (**sem energia**,
**faltam cristais** ou **saída cheia**).

## O custo

Cada reparo gasta **4 Cristais de Sinal cheios** (o mesmo da bancada) e uma quantidade de **FE** que depende do tipo
do cristal:

| Cristal | FE por reparo |
| --- | --- |
| Cristal de Sinal | 432 |
| Cristal Lapidado | 13.824 |
| Afinado Elite | 20.000 |
| Afinado Esmeralda | 320.000 |
| Afinado Allthemodium | 12.800.000 |
| Afinado Vibranium | 13.400.000 |
| Afinado Unobtainium | 21.400.000 |
| Afinado Ultimate | 536.870.912 |

Os números são os padrões; o JEI mostra os do seu pack. Com pouca energia, o reparo só começa quando o buffer
tem o custo inteiro. Quebrar o bloco no meio de um reparo **perde os 4 cristais e a energia já gastos** nele.

## Receita

<RecipeFor id="wirelessautomate:crystal_repairer" />
