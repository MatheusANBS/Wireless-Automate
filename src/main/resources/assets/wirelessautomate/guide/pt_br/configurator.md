---
group: ferramentas
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

O **Afinador**: descobre sozinho o que cada face de uma máquina aceita e o que ela dá, e propõe os
modos (Insere, Extrai, Armazém) para você conferir antes de aplicar.

<br clear="all" />

## Como operar

1. Vincule a máquina a um roteador (preso nela ou de alcance, pelo [Vinculador](linker.md)).
2. Clique na máquina com o Configurador. As faces com modo ganham uma moldura na cor do modo, e a
   action bar resume a proposta.
3. Confira a proposta. Se estiver boa, clique de novo na mesma máquina em até 10 segundos: os modos
   são aplicados nela.
4. Com **Shift**, o segundo clique aplica no perfil do grupo inteiro.
5. Escolha a rede das faces na tela do roteador, se ainda não tiverem.

Clicar em outra máquina refaz a análise para ela. Clicar no ar, mirar outro bloco por 2 segundos ou
esperar 10 segundos apaga a proposta, e o próximo clique na máquina faz uma análise nova. Segurar o
botão não aplica nem apaga: solte e clique de novo.

## O que muda

- Só os **modos** dos tipos que a análise achou. Prioridade, redstone, filtro e rede de cada face
  ficam como estão.
- Numa máquina de um grupo com outras, a mudança vira exceção dela; com Shift, vai para o perfil.
- Baús, barris e tanques viram **Armazém** numa face só; as outras faces ficam em Nenhum. Uma máquina
  só de energia ou de Source vira Armazém só se for a Bateria ou o Tanque de Source do mod: nas de
  outros mods ela recebe Insere, porque uma máquina que consome lê igual a uma bateria, e Insere não a
  esvazia.
- Não há Desfazer: a moldura mostra a proposta antes, e o segundo clique é a confirmação.

## Limites

- A análise olha até **27 slots por face** e até 64 itens de prova (alguns comuns, os da máquina e os
  do seu inventário). Uma máquina com mais slots, ou que só aceita um item raro, pode receber uma
  proposta incompleta.
- É uma proposta: confira antes de aplicar, principalmente em máquinas de outros mods.
- Você precisa poder mexer na máquina e editar o roteador.
- Numa máquina sem roteador aparece "Vincule a máquina a um roteador primeiro" e nada é analisado.

Num roteador, o clique com o Configurador abre a tela dele.

## Receita

<RecipeFor id="wirelessautomate:configurator" />
