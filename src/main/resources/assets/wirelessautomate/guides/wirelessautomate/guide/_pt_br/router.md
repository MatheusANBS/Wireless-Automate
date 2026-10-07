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
| **Tipos** | Itens, Fluidos, Energia e, com o Mekanism, Químicos. |
| **Faces configuráveis** | As seis faces da máquina, cada uma com modo, prioridade, redstone e filtro por tipo. |
| **Upgrades** | [Cartões de Upgrade](upgrade-cards.md) (clique no roteador) e o [Upgrade de Chunk Loading](chunk-loading.md) (no slot do topo da tela). |
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
| **1. Escolha o tipo** | Clique na aba: Itens, Fluidos, Energia ou Químicos. |
| **2. Escolha a face** | Clique na face da máquina no modelo 3D (arraste para girar), ou use os botões C N L B S O (Cima, Norte, Leste, Baixo, Sul, Oeste). |
| **3. Escolha o modo** | Extrai, Insere, Armazém ou Nenhum (tabela abaixo). |
| **4. Ajustes (opcional)** | Em **Mais**: prioridade e redstone. Em **Editar**: o [filtro](filters.md) da face. |
| **5. Rede (opcional)** | No seletor ao lado das abas, escolha a [rede](networks.md) desta aba. |

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
| **Prioridade** | −999 a 999 (padrão 0) | Destinos com prioridade maior recebem primeiro; empates se revezam. |
| **Redstone: Ignorar** | Padrão | A face funciona sempre. |
| **Redstone: Com sinal** | | A face só funciona com sinal de redstone no roteador. |
| **Redstone: Sem sinal** | | A face só funciona sem sinal. |

## Especificações por tier

<Row gap="12">
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'basic'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'advanced'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'elite'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'ultimate'}" />
</Row>

A vazão vale **por face e por tipo**, contada em quem envia. Químicos usam o limite de fluido.
O servidor pode ter valores diferentes: o tooltip do cartão de upgrade mostra os do seu.

| Tier | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Alcance |
| --- | --- | --- | --- | --- |
| **Básico** | 512 | 32.000 | 16.000 | 128 blocos |
| **Avançado** | 8.192 | 512.000 | 256.000 | 1.024 blocos |
| **Elite** | 131.072 | 8.000.000 | 4.000.000 | A dimensão inteira |
| **Ultimate** | Sem limite | Sem limite | Sem limite | Todas as dimensões |

<GameScene zoom="3" interactive={true}>
  <Block id="minecraft:chest" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:furnace" x="2" y="0" z="0" />
  <Block id="wirelessautomate:router" x="2" y="1" z="0" p:facing="up" p:tier="advanced" />
  <Block id="minecraft:barrel" x="4" y="0" z="0" p:facing="up" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="elite" />
  <Block id="minecraft:blast_furnace" x="6" y="0" z="0" />
  <Block id="wirelessautomate:router" x="6" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <BlockAnnotation x="0" y="1" z="0" color="#c8ccd2">
    Básico
  </BlockAnnotation>
  <BlockAnnotation x="2" y="1" z="0" color="#e2b347">
    Avançado
  </BlockAnnotation>
  <BlockAnnotation x="4" y="1" z="0" color="#45d6cc">
    Elite
  </BlockAnnotation>
  <BlockAnnotation x="6" y="1" z="0" color="#a06bff">
    Ultimate
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

No criativo, o clique do meio pega o roteador já no tier do bloco.
