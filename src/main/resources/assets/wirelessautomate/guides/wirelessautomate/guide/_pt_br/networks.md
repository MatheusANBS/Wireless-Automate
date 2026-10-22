---
navigation:
  title: Redes
  icon: wirelessautomate:linker
  parent: index.md
  position: 4
---


# Redes

Roteadores não se ligam uns aos outros: cada **face** de um roteador, em cada aba, entra numa
**rede** (ou em nenhuma), e tudo do mesmo tipo na mesma rede troca entre si. Quem envia e quem
recebe vem do modo das faces.

<GameScene zoom="3" interactive={true}>
  <Block id="minecraft:furnace" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="advanced" />
  <Block id="minecraft:barrel" x="3" y="0" z="0" p:facing="up" />
  <Block id="wirelessautomate:router" x="3" y="1" z="0" p:facing="up" p:tier="advanced" />
  <Block id="minecraft:barrel" x="3" y="0" z="3" p:facing="up" />
  <Block id="wirelessautomate:router" x="3" y="1" z="3" p:facing="up" p:tier="advanced" />
  <Block id="minecraft:chest" x="0" y="0" z="3" />
  <Block id="wirelessautomate:router" x="0" y="1" z="3" p:facing="up" p:tier="advanced" />
  <LineAnnotation from="0.5 1.5 0.5" to="3.5 1.5 0.5" color="#4a8cff" thickness="0.08">
    A fornalha (Extrai) entrega nos barris (Armazém)
  </LineAnnotation>
  <LineAnnotation from="0.5 1.5 0.5" to="3.5 1.5 3.5" color="#4a8cff" thickness="0.08">
    A fornalha (Extrai) entrega nos barris (Armazém)
  </LineAnnotation>
  <LineAnnotation from="3.5 1.5 0.5" to="0.5 1.5 3.5" color="#3fc36b" thickness="0.08">
    Os barris (Armazém) entregam no baú (Insere)
  </LineAnnotation>
  <LineAnnotation from="3.5 1.5 3.5" to="0.5 1.5 3.5" color="#3fc36b" thickness="0.08">
    Os barris (Armazém) entregam no baú (Insere)
  </LineAnnotation>
  <BlockAnnotation x="0" y="1" z="0" color="#4a8cff">
    Fornalha · Extrai
  </BlockAnnotation>
  <BlockAnnotation x="3" y="1" z="0" color="#3fc36b">
    Barril · Armazém
  </BlockAnnotation>
  <BlockAnnotation x="3" y="1" z="3" color="#3fc36b">
    Barril · Armazém
  </BlockAnnotation>
  <BlockAnnotation x="0" y="1" z="3" color="#ff9a3c">
    Baú · Insere
  </BlockAnnotation>
  <IsometricCamera yaw="210" pitch="35" />
</GameScene>

## Como funciona

| Regra | Explicação |
| --- | --- |
| **Sem rede ao colocar** | Um roteador novo não está em rede nenhuma: ponha as faces dele numa rede (abaixo) para ele trabalhar. |
| **Rede ativa** | Cada jogador tem uma rede ativa, a que o [Tablet](network-tablet.md) usa como destino do **Mover**. |
| **Rede por face** | Numa fornalha, a face de **Cima** (entrada) pode estar na rede "Mina" e a de **Baixo** (saída) na rede "Armazém": o minério chega pela Mina e o lingote sai pelo Armazém. Cada aba tem as suas: a energia pode ir para a rede "Base". |
| **Ordem de entrega** | Prioridade maior primeiro; empates se revezam (round-robin). |
| **Armazém** | Recebe de quem extrai e entrega para quem insere, sem ficar trocando com outro Armazém. |
| **Dono** | Só o dono da rede (ou um operador) pode pôr roteadores nela. |

## Como mudar a rede

| Jeito | Quantos de uma vez |
| --- | --- |
| Linha **Rede** da face, na tela do roteador | Uma face de um roteador, numa aba |
| <ItemLink id="wirelessautomate:network_tablet" />, Selecionar | Os nós que você marcar na lista: todas as faces deles no tipo escolhido (ou em todos) |
| <ItemLink id="wirelessautomate:configurator" /> | A rede de cada face vai junto com a configuração colada (todas as abas ou só um tipo) |

## Alcance e chunks

| Situação | O que acontece |
| --- | --- |
| Destino longe demais | Fica de fora daquela origem. O alcance depende do tier de quem envia ([Roteador](router.md)). |
| Outra dimensão | Só com origem **Esmeralda** ou acima. |
| Chunk descarregado | A rota pausa e volta sozinha quando o chunk carrega. |
| Quer manter trabalhando longe | Use o [Upgrade de Chunk Loading](chunk-loading.md). |
