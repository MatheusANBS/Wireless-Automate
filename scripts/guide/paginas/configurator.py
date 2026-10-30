"""Página configurator.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('configurator.md', front('Configurador', 'wirelessautomate:configurator', 8, item_ids=['wirelessautomate:configurator']) + '''
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
''', front('Configurator', 'wirelessautomate:configurator', 8, item_ids=['wirelessautomate:configurator']) + '''
# Configurator

<ItemImage id="wirelessautomate:configurator" scale="2" float="left" />

Copies a router's configuration and pastes it on another.

<br clear="all" />

## How to use it

1. Shift + click the router that's already set up: copies faces, filters, priorities, redstone and
   networks.
2. Click another router: pastes on it.

The item holds a single copy. The tooltip shows what's copied.

| Gesture | What it does |
| --- | --- |
| Shift + click a router | Copy |
| Click a router | Paste on it |
| Shift + click a block without a router | Clear the wand |
| Shift + mouse wheel | Change the pasted type |
| Hold Left Alt | Open the pasted type wheel |

## Pasted type

Copying always copies everything. The **pasted type** picks what goes to the router: **All**
(default), Items, Fluids, Energy, Chemicals (with Mekanism) or Source (with Ars Nouveau). On a single
type, the router's other tabs stay as they were.

Example: copy a fluids router, switch to **Fluids** and paste on the others without touching their
items or energy.

On the wheel (Left Alt; change the key in Controls), release the key over a slice to pick it.
Releasing in the center or Esc closes without changing anything.

## Good to know

- The copy is relative to the router: it works with the router on any face.
- A network is only pasted if you can use it, and a face only leaves a network you manage (your own,
  or any if you are an operator). Otherwise the face keeps its own. A face with no network in the copy leaves the target's network alone.
- The network applies to the attached machine; Range Upgrade machines stay as they are.
- To paste on many routers at once, use the [Tablet](network-tablet.md) selection.

## Recipe

<RecipeFor id="wirelessautomate:configurator" />
''')
