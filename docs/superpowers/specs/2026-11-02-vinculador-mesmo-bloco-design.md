# Vinculador: Ctrl + clique vincula as máquinas do mesmo bloco no raio

Pedido e decidido pelo dono em 02/11/2026.

## O gesto

- Com o Vinculador no modo **Vincular** e um roteador já escolhido (componente `linker_router`), **Ctrl + clique direito numa máquina** vincula ao roteador todas as máquinas **do mesmo bloco** dela (o mesmo id de registro) que estão no raio do roteador.
- **Direto, sem confirmação.** Aviso na action bar: "Vinculadas 7 Fornalhas" ou, com o limite, "Vinculadas 32 de 47 Fornalhas · limite do tier"; sem nenhuma nova, "Todas as Fornalhas no raio já estão vinculadas".
- **Ordem:** da mais perto do roteador para a mais longe (distância de Chebyshev, a do `RangeRules`; empate pela posição), até o limite de máquinas do tier (`RangeRules`: as que já estão vinculadas contam).
- **Quem entra:** as posições dentro do raio do tier (`RangeTiers`/`RangeRules`, a mesma regra do vincular de um clique, na mesma dimensão do roteador), com o mesmo bloco da máquina clicada, carregadas (`level.isLoaded`), que o jogador pode usar (`mayBuild` e `mayInteract`, como no vincular de um clique), que não são a presa nem já vinculadas, e que não são roteadores. A máquina clicada entra também (se ainda não estava).
- **Grupo:** entram no grupo do bloco (o primeiro grupo daquele bloco, ou um novo), seguindo o perfil, pelo `RouterBlockEntity.linkMachine` (o mesmo caminho do vincular de um clique: índice do mixin, caches e rotas).
- **Sem roteador escolhido**, roteador sem Upgrade de Alcance ou máquina fora do raio: a mesma recusa do vincular de um clique, com a mensagem de sempre.
- **Ctrl chega ao servidor por um pacote** (`LinkSameBlockPayload(pos)`, cliente → servidor), porque o servidor não sabe do Ctrl: o cliente cancela o uso normal quando o Vinculador está na mão principal, no modo Vincular, com Ctrl apertado, mirando um bloco que não é roteador, e manda o pacote. O servidor refaz todas as conferências (item na mão, modo, roteador escolhido, distância do jogador ao bloco clicado como no uso normal).

## Custo

Só no clique. A varredura é o cubo do raio em volta do roteador, limitada: para raios grandes, varre só os chunks carregados que cruzam o cubo, lendo o id do bloco de cada posição (sem block entity, sem capability) e parando quando junta candidatos suficientes para o limite. Raio de dimensão inteira (alcance 0): só os chunks carregados num quadrado de 8 chunks em volta do jogador, com um teto de 4.096 posições lidas por clique. Diga no aviso se o teto foi atingido ("· mais longe não foi procurado").

## Desvincular volta a ser num clique

A confirmação "Clique de novo para desvincular" (etapa 4) sai: um clique numa máquina já vinculada desvincula, como antes. Os cartões do grupo que esvazia continuam indo para o inventário de quem clicou, com a mensagem de quantos.

## Fora

Ctrl + clique para desvincular em massa (não pedido).
