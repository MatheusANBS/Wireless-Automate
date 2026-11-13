# Cubo do alcance do roteador no mundo

Pedido do dono em 13/11/2026 ("a mesma mecânica da Doca: mostrar o range dele no mundo e as máquinas vinculadas"), sem mockup por decisão dele ("pode seguir direto"). As escolhas abaixo seguem a Doca sempre que dá; as que não têm par na Doca estão marcadas como **decisão da execução**, para o dono conferir no jogo.

## O que aparece

- Com o **Vinculador numa das mãos** e um **roteador escolhido** nele (componente `linker_router`), o cliente desenha no mundo, através dos blocos, como o cubo da Doca (`client/dock/DockReachPaint`):
  - **o cubo do raio de máquinas** do roteador (Chebyshev, lado `2r + 1`, recortado na altura do mundo), na **cor do tier**; só quando o roteador vincula (Upgrade de Alcance valendo e raio maior que zero, `RangeLinks.upgradeRefusal == NONE`); sem isso, nada de cubo, só as máquinas;
  - **cada máquina vinculada** (remota; a presa não) com a moldura e o véu leve, na **cor do grupo dela**: a paleta das seis cores dos lados da Doca (`DockColors`), na ordem `DockColors.ORDER`, pela ordem dos grupos no roteador (o sétimo grupo volta à primeira cor) (**decisão da execução**);
  - a máquina **fora do raio ou além do limite** (`RangeRules.Reach` diferente de `ACTIVE`) em **cinza** (**decisão da execução**: mostra no mundo o "fora do alcance" que a tela já mostra).
- Só o que está a menos de 128 blocos da câmera, como na Doca.
- **Quando chega:** ao escolher o roteador, depois de cada vínculo e desvínculo (clique e Ctrl + clique) e quando o cliente pede (segurando um Vinculador com o roteador escolhido sem ter o cubo: entrou no mundo de novo). Só ao jogador que agiu. Sem o cubo "de colocar" (o roteador nasce sem o upgrade).
- **Quando some:** o Vinculador sai das mãos (fica guardado e volta ao pegar de novo), o Vinculador na mão escolhe outra coisa (outro roteador, uma Doca, uma Tela, nada), o roteador some do lugar, troca de mundo e logout. As mesmas regras do cubo da Doca.
- Um cubo por vez: o Vinculador escolhe um roteador **ou** uma Doca, então o cliente guarda um só, de quem quer que seja.

## Arquitetura

- **Um pacote para os dois donos:** o `DockReachPayload` vira o `LinkerReachPayload(kind, origin, radius, tier, machines, colors, ticks)`, com `kind` (roteador ou Doca), o raio (−1: sem cubo), as posições (`BlockPos.asLong`) e um byte de cor por máquina (0 a 5: a cor da paleta `DockColors.side`; 6: cinza), no máximo `RangeTiers.MAX_MACHINES` (512). O pedido `DockReachRequestPayload` vira o `LinkerReachRequestPayload(kind, pos)`. Protocolo `ModPayloads.VERSION` 51 → 52.
- **Servidor:** `range/RouterReachSync` (o pacote do roteador, o envio e a resposta ao pedido, como o `DockReachSync`), chamado pelo `RangeLinks` (escolher, vincular, desvincular) e pelo `SameBlockLinks` (vincular e desvincular). O `DockReachSync` passa a montar o pacote genérico com `kind` Doca e a cor do lado.
- **Cliente:** o `DockReachPaint` vira o `client/linker/ReachPaint`, que guarda um cubo de qualquer dono e confere a escolha do Vinculador pelo `kind`; o `DockReachBox` vira o `client/linker/ReachBox` (lógica pura, com o JUnit junto).

## Custo

- **Por tick no servidor:** zero. O pacote sai só nos eventos acima, só para o jogador que agiu.
- **Bytes:** ~9 bytes por máquina (posição e cor), até 512 máquinas: no máximo ~4,6 KB por evento.
- **Cliente:** os vértices são montados quando o pacote chega; o quadro só soma a câmera e desenha o que está a menos de 128 blocos. Sem cubo guardado, o tick só lê os itens das mãos. Os pedidos ao servidor seguem o recuo da Doca (1 s, 2 s, 4 s, 8 s, 10 s e param).

## Fora

- O cubo ao pôr o Upgrade de Alcance ou ao abrir a tela do roteador.
- Mostrar a presa.
- O cubo para quem não está com o Vinculador (por exemplo, com o Tablet).
