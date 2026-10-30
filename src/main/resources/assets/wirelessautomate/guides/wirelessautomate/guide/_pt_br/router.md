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

Prenda o roteador numa máquina e ela entra na rede pelas **seis faces de uma vez**. Sem canos.

<br clear="all" />

## Como usar

1. Coloque o roteador em qualquer face da máquina. Tanto faz qual: ele alcança as seis.
2. Clique direito no roteador para abrir a tela.
3. Escolha a aba do tipo: Itens, Fluidos ou Energia (Químicos com o Mekanism, Source com o Ars Nouveau).
4. Clique numa face da máquina, no modelo 3D ou no cubo do painel.
5. Clique no botão da face até chegar no modo certo.
6. Na linha **Rede**, escolha a [rede](networks.md) da face.

Pronto: a face já troca recursos com as outras faces da mesma rede.

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

Quebrar o bloco de trás solta o roteador. Shift + clique direito com a mão vazia gira o roteador; as
faces continuam como estavam.

## Modos da face

| Modo | O que faz | Use em |
| --- | --- | --- |
| ![extract](images/port_extract.png) **Extrai** | Tira da máquina. | Saídas: fornalha, gerador, minerador. |
| ![insert](images/port_insert.png) **Insere** | Põe na máquina. | Entradas: fornalha, máquina, baú final. |
| ![both](images/port_both.png) **Armazém** | Recebe de quem extrai e entrega para quem insere. Não troca com outro Armazém. | Baús de estoque. |
| ![none](images/port_none.png) **Nenhum** | A face fica de fora. | O resto. |

## Na tela

| Gesto | O que faz |
| --- | --- |
| **Clique** no botão da face | Avança o modo. |
| **Clique direito** | Volta o modo. |
| **Shift + clique** | Põe em Nenhum. |
| **Arrastar** o modelo 3D | Gira a máquina. |
| **Arrastar** a borda ou o canto | Aumenta a tela. |
| **Copiar** e **Colar** | Leva tudo de uma face para outra do mesmo tipo. |
| **Limpar** (dois cliques) | Põe em Nenhum as faces da aba. Com Shift no segundo clique, de todas as abas. |

Os lados (Frente, Trás, Esquerda, Direita, Cima, Baixo) são os da máquina, olhando de frente para
ela. Máquina sem frente, como um barril: Frente = Norte.

## Ajustes da face

| Ajuste | Como funciona |
| --- | --- |
| **Prioridade** | De −999 a 999 (padrão 0). Quem tem mais recebe primeiro; empates se revezam. Com Shift, muda de 10 em 10. |
| **Redstone** | Ignorar (padrão), Com sinal ou Sem sinal no roteador. |
| **Filtro** | **Editar** abre o [filtro](filters.md) da face. Ao lado, dois slots para [Cartão de Filtro](filter-card.md). |

## Upgrades

- [Cartões de Upgrade](upgrade-cards.md): Shift + clique no roteador. Mais vazão e alcance.
- [Upgrade de Chunk Loading](chunk-loading.md): no slot do topo da tela. O roteador trabalha com você longe.
- **Upgrade de Alcance:** o roteador atende várias máquinas ao redor. A lista delas aparece embaixo
  do visor: o ✎ dá um nome e o olho ◉ acende a máquina no mundo.

## Vazão por tier

<Row gap="12">
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'basic'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'advanced'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'elite'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'emerald'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'ultimate'}" />
</Row>

A vazão vale **por face e por tipo**. Químicos usam o limite de fluido. O servidor pode mudar esses
números: o tooltip do cartão de upgrade mostra os dele.

| Tier | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Source/s | Alcance |
| --- | --- | --- | --- | --- | --- |
| **Básico** | 32 | 2.000 | 454 | 100 | 64 blocos |
| **Avançado** | 256 | 16.000 | 29.100 | 800 | 512 blocos |
| **Elite** | 2.048 | 128.000 | 63.000 | 6.400 | A dimensão inteira |
| **Esmeralda** | 16.384 | 1.024.000 | 1.350.000 | 51.200 | Todas as dimensões |
| **Allthemodium¹** | 131.072 | 8.192.000 | 67.200.000 | 409.600 | Todas as dimensões |
| **Vibranium¹** | 1.048.576 | 65.536.000 | 84.500.000 | 3.276.800 | Todas as dimensões |
| **Unobtainium¹** | 8.388.608 | 524.288.000 | 158.000.000 | 26.214.400 | Todas as dimensões |
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
