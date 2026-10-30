---
navigation:
  title: Primeiros passos
  icon: minecraft:chest
  parent: index.md
  position: 1
---


# Primeiros passos

Itens de um baú para outro, sem canos, em um minuto.

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

## Como fazer

1. Faça dois roteadores (receita abaixo).
2. Clique com um roteador no baú A e com o outro no baú B. Eles ficam presos, ainda sem rede.
3. No roteador A, aba **Itens**, face **Cima**: na linha **Rede**, crie uma rede e escolha-a.
4. No roteador B, mesma face, escolha a mesma rede.
5. Ponha a face **Cima** em **Extrai** no A e em **Insere** no B.
6. Ponha itens no baú A: eles aparecem no baú B.

O [Configurador](configurator.md) copia a configuração, com as redes, para outros roteadores.

O botão de lua, no canto de cima à direita, põe as telas no modo escuro.

## Receitas

<RecipeFor id="wirelessautomate:router" />

## Próximos passos

| Quero... | Leia |
| --- | --- |
| Entender a tela | [Roteador Wireless](router.md) |
| Mandar só alguns itens | [Filtros](filters.md) |
| Separar fábricas | [Redes](networks.md) |
| Mais vazão ou alcance | [Cartões de Upgrade](upgrade-cards.md) |
| Não funcionou | [Problemas comuns](troubleshooting.md) |
