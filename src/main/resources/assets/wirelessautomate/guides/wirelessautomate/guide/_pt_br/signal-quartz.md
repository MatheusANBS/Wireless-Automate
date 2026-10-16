---
navigation:
  title: Quartzo de Sinal e cristais
  icon: wirelessautomate:signal_crystal
  parent: index.md
  position: 18
item_ids:
  - wirelessautomate:signal_quartz_ore
  - wirelessautomate:deepslate_signal_quartz_ore
  - wirelessautomate:signal_crystal
  - wirelessautomate:cut_signal_crystal
---


# Quartzo de Sinal e cristais

<Row gap="8">
  <ItemImage id="wirelessautomate:signal_quartz_ore" />
  <ItemImage id="wirelessautomate:deepslate_signal_quartz_ore" />
  <ItemImage id="wirelessautomate:signal_crystal" />
  <ItemImage id="wirelessautomate:cut_signal_crystal" />
</Row>

Os cristais de quartzo são o combustível do [Ressonador de Sinal](signal-resonator.md), o gerador de energia do mod.

## O minério

O **Minério de Quartzo de Sinal** aparece no Overworld, em pedra e em ardósia (a versão de ardósia
é mais dura). Fica em veios pequenos, mais comum perto de **y = −16**. Quebre com **picareta de ferro** ou melhor:

| Ferramenta | O que cai |
| --- | --- |
| Picareta de ferro ou melhor | 1 a 3 **Cristais de Sinal** e um pouco de experiência. **Fortuna** aumenta a quantidade. |
| Toque de Seda | O próprio minério. |

## Os cristais

| Cristal | Rende | Dura gerando |
| --- | --- | --- |
| <ItemImage id="wirelessautomate:signal_crystal" /> **Cristal de Sinal** | 1× | 10 minutos |
| <ItemImage id="wirelessautomate:cut_signal_crystal" /> **Cristal Lapidado** | 2× | 20 minutos |

O Cristal de Sinal só vem do minério. O **Cristal Lapidado** sai da bancada com quatro Cristais de Sinal em
cruz em volta de um Fragmento de Ametista, e só aceita cristais **cheios**: cristais gastos não valem.

<RecipeFor id="wirelessautomate:cut_signal_crystal" />

O Cristal de Sinal também entra nas receitas da Carcaça de Porcelana e do Vidro de Ressonância, e o Lapidado
na do Núcleo de Ressonância.

## Carga e cristal Opaco

A **barra do item** mostra quanto falta para o cristal acabar: ela só aparece depois que o cristal começa a
gastar. Dentro do Núcleo, a carga cai enquanto ele gera energia.

Sem carga, o cristal fica **Opaco**: é o mesmo item, só que apagado. Um Opaco não gera nada, mas dá para
**recarregar**:

1. Deixe o Opaco numa câmara do Núcleo.
2. Ponha **Pó de Redstone** no Núcleo (clique com a redstone na mão, pela tela ou pela Porta de Cristais).
3. O Núcleo recarrega **um Opaco por vez**: leva 30 segundos e gasta 1 pó de redstone, e o cristal volta cheio.

A Porta de Cristais só devolve os Opacos quando o Núcleo está sem redstone; com redstone, o Opaco fica
lá dentro esperando a vez de recarregar.

Veja como montar o gerador em [Ressonador de Sinal](signal-resonator.md).
