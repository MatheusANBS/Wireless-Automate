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

## Ficha

| | |
| --- | --- |
| **Copia** | Faces, filtros, prioridades, redstone e a rede de cada face. |
| **Guarda** | Uma cópia só, no próprio item. O tooltip mostra o que está copiado e os comandos. |
| **Tipo colado** | Todos (padrão), Itens, Fluidos, Energia, Químicos (com o Mekanism) ou Source (com o Ars Nouveau). |

## Comandos

| Gesto | O que faz |
| --- | --- |
| Shift + clique num roteador | Copia |
| Clique num roteador | Cola nele |
| Shift + clique num bloco sem roteador | Limpa a varinha |
| Shift + roda do mouse | Troca o tipo colado |
| Segurar Alt esquerdo | Abre a roda |

## A roda

Com o Configurador na mão, **segure Alt esquerdo** (a tecla muda em Controles, na categoria Wireless
Automate): abre uma roda no meio da tela com o **tipo colado**. A opção de agora fica com a borda
laranja e o centro mostra o que faz a fatia sob o mouse.

| | |
| --- | --- |
| **Soltar a tecla** | Escolhe a fatia sob o mouse e fecha. |
| **Clique** | Escolhe sem fechar. |
| **Soltar no centro ou Esc** | Fecha sem mudar nada. |

Copiar sempre copia tudo; o **tipo colado** escolhe o que vai para o roteador. Em **Todos**, todas as
abas. Num tipo só, apenas as faces (com as redes delas) daquela aba: as outras abas do roteador ficam como
estavam. Por exemplo, copie um roteador configurado só para fluidos, passe para **Fluidos** e cole
nos outros sem mexer nos itens nem na energia deles.

## Bom saber

| | |
| --- | --- |
| **Orientação** | A cópia é relativa ao roteador: funciona com ele preso em qualquer face. |
| **Redes** | A rede de uma face só é colada se você puder usá-la, e só sai de uma rede que você gerencia (a sua, ou qualquer uma se for operador); senão, a face fica com a de antes. Uma face sem rede na cópia não mexe na rede do destino. A rede vale para as faces da máquina presa; as máquinas do Upgrade de Alcance ficam como estão. |
| **Muitos de uma vez** | Para colar em vários roteadores, use a seleção do [Tablet](network-tablet.md). |

## Receita

<RecipeFor id="wirelessautomate:configurator" />
