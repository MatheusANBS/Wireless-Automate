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

O roteador se prende a uma face de qualquer bloco (a máquina) e dá à rede acesso a ela. Ele não
precisa ficar numa face específica: pela tela, você configura **todas as seis faces da máquina**, e
o roteador acessa cada uma como um cano encostado nela faria.

<br clear="all" />

<RecipeFor id="wirelessautomate:router" />

## Onde prender

<GameScene zoom="4" interactive={true}>
  <Block id="minecraft:furnace" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="wirelessautomate:router" x="0" y="0" z="1" p:facing="south" p:tier="basic" />
  <Block id="wirelessautomate:router" x="-1" y="0" z="0" p:facing="west" p:tier="basic" />
  <BlockAnnotation x="0" y="1" z="0" color="#4a8cff">
    Preso em cima da fornalha
  </BlockAnnotation>
  <BlockAnnotation x="0" y="0" z="1" color="#ff9a3c">
    Preso na lateral: funciona igual
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

Precisa de um bloco atrás (não fica no ar): quebrar esse bloco solta o roteador.

## A tela

Clique direito no roteador (sem Vinculador, Configurador ou cartão de upgrade na mão):

- **Abas:** Itens, Fluidos, Energia e Químicos (Químicos só com o Mekanism). Cada aba escolhe a sua
  [rede](networks.md) no seletor ao lado das abas.
- **Visor 3D:** arraste para girar, use a roda para dar zoom e clique numa face da máquina para
  escolhê-la. Os botões abaixo (C, N, L, B, S, O) fazem o mesmo.
- **Modo da face:**
  - <Color color="#4a8cff">Extrai</Color>: tira recursos da máquina por essa face.
  - <Color color="#ff9a3c">Insere</Color>: coloca recursos na máquina por essa face.
  - <Color color="#3fc36b">Armazém</Color>: recebe de quem extrai e entrega para quem insere, mas não
    troca com outro Armazém. Ideal para baús de buffer.
  - Nenhum: a face fica de fora.
- **Mais:** prioridade (de −999 a 999; destinos de prioridade maior recebem primeiro; empate se
  reveza) e redstone (Ignorar, Com sinal, Sem sinal).
- **Filtro:** o botão Editar abre o [filtro](filters.md) da face; ao lado ficam dois slots de
  [Cartão de Filtro](filter-card.md).
- **Cabeçalho:** nome, vazão atual, tier e o slot do [Upgrade de Chunk Loading](chunk-loading.md).

## Especificações por tier

A vazão vale **por face e por tipo**, contando a origem. Químicos usam o limite de fluido.

| Tier | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Alcance |
| --- | --- | --- | --- | --- |
| Básico | 512 | 32.000 | 16.000 | 128 blocos |
| Avançado | 8.192 | 512.000 | 256.000 | 1.024 blocos |
| Elite | 131.072 | 8.000.000 | 4.000.000 | A dimensão inteira |
| Ultimate | Sem limite | Sem limite | Sem limite | Todas as dimensões |

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

Para subir de tier, veja [Cartões de Upgrade](upgrade-cards.md). No criativo, o clique do meio pega
o roteador já no tier do bloco.
