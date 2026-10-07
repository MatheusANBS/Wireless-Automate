---
navigation:
  title: Primeiros passos
  icon: minecraft:chest
  parent: index.md
  position: 1
---


# Primeiros passos

Vamos levar itens de um baú para outro, sem canos.

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

1. Faça dois roteadores (receita abaixo).
2. Clique com o roteador numa face de um baú: ele fica **preso** àquela máquina e já entra na sua
   rede ativa, em todas as abas.
3. Clique no roteador A para abrir a tela. Na aba **Itens**, escolha a face **Cima** e o modo
   **Extrai**.
4. No roteador B, mesma face, modo **Insere**. Pronto: o que entrar no baú A vai para o baú B.

<RecipeFor id="wirelessautomate:router" />

## O que mais dá para fazer

- Cada face da máquina, para cada tipo (itens, fluidos, energia, químicos), tem modo, prioridade,
  redstone e [filtro](filters.md) próprios. Veja [Roteador](router.md).
- Roteadores longe uns dos outros funcionam: o alcance depende do [tier](upgrade-cards.md).
- Para separar fábricas, crie [redes](networks.md) diferentes.
