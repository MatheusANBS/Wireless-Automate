# Vinculador: Ctrl + clique vincula as máquinas do mesmo bloco no raio

Pedido e decidido pelo dono em 02/11/2026.

## O gesto

- Com o Vinculador no modo **Vincular** e um roteador já escolhido (componente `linker_router`), **Ctrl (Cmd no Mac, o `Screen.hasControlDown`) + clique direito numa máquina** (um bloco com a capability de algum tipo carregado, como no `ConfiguratorItem.actsOn`) vincula ao roteador todas as máquinas **do mesmo bloco** dela (o mesmo id de registro) que estão no raio do roteador.
- **Direto, sem confirmação.** Aviso na action bar, sem concordância de gênero: "Vinculou 7 Fornalhas" ou, com o limite, "Vinculou 32 de 47 Fornalhas · limite do tier" (o limite só quando de fato ficou alguma de fora: a varredura procura uma além das vagas); sem nenhuma nova, "Fornalha: todas as do raio já estão vinculadas" ou, parada no teto, "Fornalha: nenhuma nova nas posições lidas"; todas as achadas recusadas pelo evento de clique das proteções, a recusa de área protegida.
- **Ordem:** da mais perto do roteador para a mais longe (distância de Chebyshev, a do `RangeRules`; empate pela posição), até o limite de máquinas do tier (`RangeRules`: as que já estão vinculadas contam).
- **Quem entra:** as posições dentro do raio do tier (`RangeTiers`/`RangeRules`, a mesma regra do vincular de um clique, na mesma dimensão do roteador), com o mesmo bloco da máquina clicada, carregadas (`level.isLoaded`), que o jogador pode usar (`mayBuild` e `mayInteract`, como no vincular de um clique), que não são a presa nem já vinculadas, e que não são roteadores. A máquina clicada entra também (se ainda não estava).
- **Grupo:** entram no grupo do bloco (o primeiro grupo daquele bloco, ou um novo), seguindo o perfil, pelo `RouterBlockEntity.linkMachine` (o mesmo caminho do vincular de um clique: índice do mixin, caches e rotas).
- **Sem roteador escolhido**, roteador sem Upgrade de Alcance ou máquina fora do raio: a mesma recusa do vincular de um clique, com a mensagem de sempre.
- **Ctrl chega ao servidor por um pacote** (`LinkSameBlockPayload(pos)`, cliente → servidor), porque o servidor não sabe do Ctrl: o cliente cancela o uso normal quando o Vinculador está na mão principal, no modo Vincular, com Ctrl apertado, mirando uma máquina que não é roteador, e manda o pacote. Correndo (`isSprinting`), ou com a tecla de correr no Ctrl e apertada, o gesto não vale e o clique segue normal (decisão do dono, 03/11). O servidor põe o Vinculador num descanso de 10 ticks a cada pacote aceito e ignora o pacote que chega no descanso. O servidor refaz todas as conferências (item na mão, modo, roteador escolhido, distância do jogador ao bloco clicado como no uso normal).

## Custo

Só no clique. A varredura é o cubo do raio em volta do roteador, limitada: para raios grandes, varre só os chunks carregados que cruzam o cubo, lendo o id do bloco de cada posição (sem block entity, sem capability) e parando quando junta candidatos suficientes para o limite. O raio de máquinas vai até 64 (0 = o tier não aceita o upgrade; "dimensão inteira" é só o alcance de rede, não o de máquinas): seções da mais perto para a mais longe, pulando a seção sem o bloco na paleta, com teto de 131.072 posições lidas por clique. Diga no aviso se o teto foi atingido ("· mais longe não foi procurado").

## Desvincular volta a ser num clique

A confirmação "Clique de novo para desvincular" (etapa 4) sai: um clique numa máquina já vinculada desvincula, como antes. Os cartões do grupo que esvazia continuam indo para o inventário de quem clicou, com a mensagem de quantos.

## Fora

Ctrl + clique para desvincular em massa (não pedido).
