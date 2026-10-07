---
navigation:
  title: Redes
  icon: wirelessautomate:linker
  parent: index.md
  position: 4
---


# Redes

Roteadores não se ligam uns aos outros: cada **aba** de um roteador entra numa **rede**, e tudo do
mesmo tipo na mesma rede troca entre si. Quem envia e quem recebe vem do modo das faces.

- **Rede ativa:** cada jogador tem uma rede ativa (a primeira leva o nome dele). Roteador colocado
  entra nela, em todas as abas.
- **Rede por aba:** os itens de uma fornalha podem ir para a rede "Linha de minério" e a energia
  dela para a rede "Base". Quem não quer separar nada não vê diferença.
- **Ordem de entrega:** prioridade maior primeiro; empates se revezam (round-robin).
- **Armazém:** recebe de quem extrai e entrega para quem insere, sem ficar trocando com outro
  Armazém.

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

## Mudar a rede

- Pelo seletor de rede de cada aba, na tela do roteador.
- Com o <ItemLink id="wirelessautomate:linker" />, um roteador ou uma área de cada vez.
- Pelo <ItemLink id="wirelessautomate:network_tablet" />, vários nós de uma vez.
- Colando a configuração com o <ItemLink id="wirelessautomate:configurator" /> (leva a rede junto).

## Alcance e chunks

A distância máxima depende do tier da origem (veja [Roteador](router.md)). Origem ou destino num
chunk descarregado só pausam a rota, sem custo. Para manter um roteador trabalhando longe, use o
[Upgrade de Chunk Loading](chunk-loading.md).

Redes são de quem as criou: só o dono (ou um operador) pode pôr roteadores nelas.
