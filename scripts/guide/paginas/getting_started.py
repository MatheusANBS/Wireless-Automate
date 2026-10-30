"""Página getting-started.md do guia."""
from guia_base import *  # noqa: F401,F403


# ------------------------------------------------------------------ cenas 3D


SCENE_TWO_CHESTS = '''<GameScene zoom="4" interactive={true}>
  <Block id="minecraft:chest" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:chest" x="4" y="0" z="0" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="basic" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#45d6cc" thickness="0.08">
    {LINE}
  </LineAnnotation>
  <BlockAnnotation x="0" y="1" z="0" color="#4a8cff">
    {A}
  </BlockAnnotation>
  <BlockAnnotation x="4" y="1" z="0" color="#ff9a3c">
    {B}
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>'''


PAGINA = page('getting-started.md', front('Primeiros passos', 'minecraft:chest', 1) + '''
# Primeiros passos

Itens de um baú para outro, sem canos, em um minuto.

''' + fill(SCENE_TWO_CHESTS, LINE='Mesma rede: os itens vão de A para B sem fio nenhum',
           A='Roteador A · face de cima em **Extrai**', B='Roteador B · face de cima em **Insere**') + '''

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
''', front('Getting started', 'minecraft:chest', 1) + '''
# Getting started

Items from one chest to another, without pipes, in a minute.

''' + fill(SCENE_TWO_CHESTS, LINE='Same network: items go from A to B with no wire at all',
           A='Router A · top face set to **Extract**', B='Router B · top face set to **Insert**') + '''

## How to do it

1. Craft two routers (recipe below).
2. Use one router on chest A and the other on chest B. They attach, with no network yet.
3. On router A, **Items** tab, **Up** face: on the **Network** row, create a network and pick it.
4. On router B, same face, pick the same network.
5. Set the **Up** face to **Extract** on A and to **Insert** on B.
6. Put items in chest A: they show up in chest B.

The [Configurator](configurator.md) copies the setup, networks included, to other routers.

The moon button, in the top-right corner, switches the screens to dark mode.

## Recipes

<RecipeFor id="wirelessautomate:router" />

## Next steps

| I want to... | Read |
| --- | --- |
| Understand the screen | [Wireless Router](router.md) |
| Send only some items | [Filters](filters.md) |
| Keep factories apart | [Networks](networks.md) |
| More throughput or range | [Upgrade Cards](upgrade-cards.md) |
| It didn't work | [Troubleshooting](troubleshooting.md) |
''')
