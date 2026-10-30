---
navigation:
  title: Configurador
  icon: wirelessautomate:configurator
  parent: index.md
  position: 8
item_ids:
  - wirelessautomate:configurator
---


# Configurador

<ItemImage id="wirelessautomate:configurator" scale="2" float="left" />

Copia a configuração de um roteador e cola em outro.

<br clear="all" />

## Como usar

1. Shift + clique no roteador já configurado: copia faces, filtros, prioridades, redstone e redes.
2. Clique em outro roteador: cola nele.

O item guarda uma cópia só. O tooltip mostra o que está copiado.

| Gesto | O que faz |
| --- | --- |
| Shift + clique num roteador | Copia |
| Clique num roteador | Cola nele |
| Shift + clique num bloco sem roteador | Limpa a varinha |
| Shift + roda do mouse | Troca o tipo colado |
| Segurar Alt esquerdo | Abre a roda do tipo colado |

## Tipo colado

Copiar sempre copia tudo. O **tipo colado** escolhe o que vai para o roteador: **Todos** (padrão),
Itens, Fluidos, Energia, Químicos (com o Mekanism) ou Source (com o Ars Nouveau). Num tipo só, as
outras abas do roteador ficam como estavam.

Exemplo: copie um roteador de fluidos, passe para **Fluidos** e cole nos outros sem mexer nos itens
nem na energia deles.

Na roda (Alt esquerdo; a tecla muda em Controles), solte a tecla sobre a fatia para escolher. Soltar
no centro ou Esc fecha sem mudar.

## Bom saber

- A cópia é relativa ao roteador: funciona com ele preso em qualquer face.
- A rede só é colada se você puder usá-la, e a face só sai de uma rede que você gerencia (a sua, ou
  qualquer uma se for operador). Senão, a face fica com a de antes. Uma face sem rede na cópia não mexe na rede do destino.
- A rede vale para a máquina presa; as máquinas do Upgrade de Alcance ficam como estão.
- Para colar em muitos roteadores de uma vez, use a seleção do [Tablet](network-tablet.md).

## Receita

<RecipeFor id="wirelessautomate:configurator" />
