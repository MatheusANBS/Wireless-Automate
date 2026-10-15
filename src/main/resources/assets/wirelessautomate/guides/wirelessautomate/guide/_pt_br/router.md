---
navigation:
  title: Roteador Wireless
  icon: wirelessautomate:router
  parent: index.md
  position: 2
item_ids:
  - wirelessautomate:router
---


# Roteador Wireless

<BlockImage id="wirelessautomate:router" p:facing="up" p:tier="basic" scale="3" float="left" />

O roteador se prende a uma face de qualquer bloco (a máquina) e dá à rede acesso a ela, como um
cano encostado faria, só que nas **seis faces de uma vez**: você escolhe na tela o que cada face da
máquina faz.

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Prende em** | Qualquer face de qualquer bloco (precisa de um bloco atrás). |
| **Abre com** | Clique direito no roteador, de mãos livres ou com um item comum. |
| **Gira com** | Shift + clique direito com a mão vazia: gira o roteador 90°. A configuração das faces não muda. |
| **Tipos** | Itens, Fluidos, Energia e, com o Mekanism, Químicos; com o Ars Nouveau, Source. |
| **Faces configuráveis** | As seis faces da máquina, cada uma com modo, prioridade, redstone, filtro e rede por tipo. |
| **Upgrades** | [Cartões de Upgrade](upgrade-cards.md) (Shift + clique no roteador) e o [Upgrade de Chunk Loading](chunk-loading.md) (no slot do topo da tela). |
| **Tier inicial** | Básico. Sobe com os [Cartões de Upgrade](upgrade-cards.md). |

## Receita

<RecipeFor id="wirelessautomate:router" />

## Onde prender

<GameScene zoom="4" interactive={true}>
  <Block id="minecraft:furnace" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="wirelessautomate:router" x="0" y="0" z="1" p:facing="south" p:tier="basic" />
  <BlockAnnotation x="0" y="1" z="0" color="#4a8cff">
    Preso em cima da fornalha
  </BlockAnnotation>
  <BlockAnnotation x="0" y="0" z="1" color="#ff9a3c">
    Preso na lateral: funciona igual
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

Tanto faz em qual face ele fica: pela tela, ele acessa todas as seis. Quebrar o bloco de trás solta
o roteador.

## Configurar uma face

| Passo | Como |
| --- | --- |
| **1. Escolha o tipo** | Clique na aba: Itens, Fluidos, Energia, Químicos ou Source. |
| **2. Escolha a face** | Clique na face da máquina no modelo 3D (arraste para girar) ou no **cubo desdobrado** do painel: as duas escolhas andam juntas, e a face sob o mouse se acende nos dois. |
| **3. Escolha o modo** | Extrai, Insere, Armazém ou Nenhum (tabela abaixo). Escolher um modo numa face sem rede, com uma [rede](networks.md) ativa, põe a face nela; o aviso na base do painel traz o botão **Desfazer**. |
| **4. Ajustes (opcional)** | Prioridade e redstone ficam sempre à vista no painel. **Editar** abre o [filtro](filters.md) da face. |
| **5. Rede** | Na linha **Rede**, escolha a [rede](networks.md) da face (ou **Sem rede**). |

Arraste a borda direita, a de baixo ou o canto para aumentar a tela; o visor 3D cresce. Com pouco espaço, as abas mostram só o ícone (o nome aparece ao passar o mouse).

## O painel e o cubo

O painel à direita é o mesmo que o [Tablet de Rede](network-tablet.md) mostra para um nó. No alto,
o **cubo desdobrado** mostra as seis faces da máquina de uma vez: cada uma com a cor do modo e uma
bolinha na cor da rede. Uma face sem modo tem a borda tracejada, e uma com modo e sem rede ganha um
contorno coral e o convite **Escolha a rede desta face**. O olho marca a face onde o roteador está
preso.

Os nomes são os lados da **máquina**, vistos de frente para ela: Frente, Trás, Esquerda, Direita,
Cima e Baixo. Máquina sem frente, ou com a frente para cima ou para baixo, como um barril:
**Frente = Norte**.

| No painel | O que mostra |
| --- | --- |
| **Bolinha na face** | A cor da rede daquela face (sem bolinha: sem rede). |
| **Linha Rede** | A rede da face escolhida; clique para trocar. |
| **Resumo ao lado das abas** | Em quantas redes estão as faces da aba (**1 rede**, **2 redes**, **Sem rede**); passe o mouse para ver qual face está em qual. |

## Modos da face

| Modo | O que faz | Use para |
| --- | --- | --- |
| ![extract](images/port_extract.png) **Extrai** | Tira recursos da máquina por esta face. | Saídas: fornalha pronta, gerador, minerador. |
| ![insert](images/port_insert.png) **Insere** | Coloca recursos na máquina por esta face. | Entradas: fornalha, máquina de processar, baú final. |
| ![both](images/port_both.png) **Armazém** | Recebe de quem extrai e entrega para quem insere, mas não troca com outro Armazém. | Baús de buffer e armazenamento. |
| ![none](images/port_none.png) **Nenhum** | A face fica de fora. | Faces que não interessam. |

## Prioridade e redstone

| Ajuste | Valores | Efeito |
| --- | --- | --- |
| **Prioridade** | −999 a 999 (padrão 0) | Destinos com prioridade maior recebem primeiro; empates se revezam. Os botões − e + mudam de 1 em 1; com Shift, de 10 em 10. |
| **Redstone: Ignorar** | Padrão | A face funciona sempre. |
| **Redstone: Com sinal** | | A face só funciona com sinal de redstone no roteador. |
| **Redstone: Sem sinal** | | A face só funciona sem sinal. |

## Especificações por tier

<Row gap="12">
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'basic'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'advanced'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'elite'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'emerald'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'ultimate'}" />
</Row>

A vazão vale **por face e por tipo**, contada em quem envia. Químicos usam o limite de fluido.
O servidor pode ter valores diferentes: o tooltip do cartão de upgrade mostra os do seu.

| Tier | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Source/s | Alcance |
| --- | --- | --- | --- | --- | --- |
| **Básico** | 32 | 2.000 | 1.000 | 100 | 64 blocos |
| **Avançado** | 256 | 16.000 | 8.000 | 800 | 512 blocos |
| **Elite** | 2.048 | 128.000 | 64.000 | 6.400 | A dimensão inteira |
| **Esmeralda** | 16.384 | 1.024.000 | 512.000 | 51.200 | Todas as dimensões |
| **Allthemodium¹** | 131.072 | 8.192.000 | 4.096.000 | 409.600 | Todas as dimensões |
| **Vibranium¹** | 1.048.576 | 65.536.000 | 32.768.000 | 3.276.800 | Todas as dimensões |
| **Unobtainium¹** | 8.388.608 | 524.288.000 | 262.144.000 | 26.214.400 | Todas as dimensões |
| **Ultimate** | Sem limite | Sem limite | Sem limite | Sem limite | Todas as dimensões |

¹ Só com o mod Allthemodium. Source só com o Ars Nouveau.

<GameScene zoom="3" interactive={true}>
  <Block id="minecraft:chest" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:furnace" x="2" y="0" z="0" />
  <Block id="wirelessautomate:router" x="2" y="1" z="0" p:facing="up" p:tier="advanced" />
  <Block id="minecraft:barrel" x="4" y="0" z="0" p:facing="up" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="elite" />
  <Block id="minecraft:smoker" x="6" y="0" z="0" />
  <Block id="wirelessautomate:router" x="6" y="1" z="0" p:facing="up" p:tier="emerald" />
  <Block id="minecraft:blast_furnace" x="8" y="0" z="0" />
  <Block id="wirelessautomate:router" x="8" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <BlockAnnotation x="0" y="1" z="0" color="#c8ccd2">
    Básico
  </BlockAnnotation>
  <BlockAnnotation x="2" y="1" z="0" color="#e2b347">
    Avançado
  </BlockAnnotation>
  <BlockAnnotation x="4" y="1" z="0" color="#45d6cc">
    Elite
  </BlockAnnotation>
  <BlockAnnotation x="6" y="1" z="0" color="#2fdc62">
    Esmeralda
  </BlockAnnotation>
  <BlockAnnotation x="8" y="1" z="0" color="#a06bff">
    Ultimate
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

No criativo, o clique do meio pega o roteador já no tier do bloco.
