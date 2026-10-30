---
navigation:
  title: Redes
  icon: wirelessautomate:linker
  parent: index.md
  position: 4
---


# Redes

Roteadores não se ligam uns aos outros: cada **face**, em cada aba, entra numa **rede**. Tudo do
mesmo tipo na mesma rede troca entre si; o modo da face diz quem envia e quem recebe.

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

## Como usar

1. Um roteador novo não está em rede nenhuma. Abra a tela dele e escolha a aba e a face.
2. Na linha **Rede**, escolha a rede.
3. Repita nas outras máquinas.

## Regras

| Regra | Explicação |
| --- | --- |
| **Rede por face** | A face de **Cima** da fornalha pode estar na rede "Mina" e a de **Baixo** na "Armazém". A energia pode ir para a "Base". |
| **Ordem de entrega** | Prioridade maior primeiro; empates se revezam. |
| **Rede ativa** | A que o [Tablet](network-tablet.md) usa como destino do **Mover**. Cada jogador tem a sua. |
| **Quem usa** | Pública: todos. Privada: o dono, os operadores, os **convidados** e, com o FTB Teams, os times adicionados. Só o dono (ou um operador) renomeia, convida e remove. |

## Como mudar a rede

| Jeito | Quantos de uma vez |
| --- | --- |
| Linha **Rede** da face, na tela do roteador | Uma face, numa aba |
| <ItemLink id="wirelessautomate:network_tablet" />, Selecionar | Todas as faces dos nós marcados, no tipo escolhido (ou em todos) |
| <ItemLink id="wirelessautomate:configurator" /> | A rede vai junto com a configuração colada |

## Alcance e chunks

| Situação | O que acontece |
| --- | --- |
| Destino longe demais | Fica de fora. O alcance depende do tier de quem envia ([Roteador](router.md)). |
| Outra dimensão | Só com origem **Esmeralda** ou acima. |
| Chunk descarregado | A rota pausa e volta sozinha quando o chunk carrega. |
| Manter trabalhando longe | Use o [Upgrade de Chunk Loading](chunk-loading.md). |
