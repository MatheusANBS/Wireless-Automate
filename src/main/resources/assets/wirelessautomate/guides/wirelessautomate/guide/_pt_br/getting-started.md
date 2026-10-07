---
navigation:
  title: Primeiros passos
  icon: minecraft:chest
  parent: index.md
  position: 1
---


# Primeiros passos

Vamos levar itens de um baú para outro, sem canos. Leva um minuto.

<GameScene zoom="4" interactive={true}>
  <Block id="minecraft:chest" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:chest" x="4" y="0" z="0" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="basic" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#45d6cc" thickness="0.08">
    Mesma rede: os itens vão de A para B sem fio nenhum
  </LineAnnotation>
  <BlockAnnotation x="0" y="1" z="0" color="#4a8cff">
    Roteador A · face de cima em **Extrai**
  </BlockAnnotation>
  <BlockAnnotation x="4" y="1" z="0" color="#ff9a3c">
    Roteador B · face de cima em **Insere**
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

| Passo | O que fazer |
| --- | --- |
| **1** | Faça dois roteadores e um [Vinculador](linker.md) (receitas abaixo). |
| **2** | Clique com um roteador em qualquer face do baú A. Ele fica **preso** ao baú, ainda **sem rede**. Faça o mesmo no baú B. |
| **3** | Com o Vinculador na mão, clique no roteador A e depois no B: os dois entram na sua rede ativa (o primeiro clique cria uma, se você não tiver). |
| **4** | Clique no roteador A. Na aba **Itens**, escolha a face **Cima** e o modo **Extrai**. |
| **5** | No roteador B, mesma face, modo **Insere**. |
| **6** | Coloque itens no baú A: eles aparecem no baú B. |

Depois de configurar o primeiro roteador de uma linha, use o [Configurador](configurator.md) para
copiar a configuração (e as redes) para os outros.

## Receitas

<RecipeFor id="wirelessautomate:router" />

<RecipeFor id="wirelessautomate:linker" />

## Próximos passos

| Quero... | Leia |
| --- | --- |
| Entender cada parte da tela do roteador | [Roteador Wireless](router.md) |
| Mandar só alguns itens | [Filtros](filters.md) |
| Separar fábricas diferentes | [Redes](networks.md) |
| Mais vazão ou mais alcance | [Cartões de Upgrade](upgrade-cards.md) |
| Saber por que não funcionou | [Problemas comuns](troubleshooting.md) |
