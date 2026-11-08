# Roteador de alcance, etapa 4: os menores

> **Para agentes:** uma tarefa, com commits pequenos por item (ou por grupo de itens próximos). Restrições globais: as da etapa 1 (`docs/superpowers/plans/2026-11-02-presa-no-grupo.md`) e da etapa 2 (`docs/superpowers/plans/2026-11-02-alcance-etapa-2.md`). `ModPayloads.VERSION` sobe para `"43"` só se um payload mudar de formato.

Achados das revisões de 02/11 (`docs/progresso/pendencias.md`, seção "Roteador de alcance", e a revisão da etapa 1 do alcance `docs/superpowers/reviews/2026-10-11-roteador-de-alcance-etapa-1.md`). Cada item com GameTest (ou JUnit, se for lógica pura) que falha antes e passa depois, quando der.

1. **Vinculador desvincula num clique e solta os cartões sem dizer** (`range/RangeLinks`, `item/LinkerItem`): clicar numa máquina já vinculada pede confirmação (a mensagem na action bar "Clique de novo para desvincular <máquina>"; o segundo clique na mesma máquina em até 3 s desvincula; o estado pendente fica num componente do item ou na memória do servidor por jogador, sem NBT novo no mundo). Os cartões de um grupo que esvazia vão para o inventário de quem clicou (como na etapa 2), e a mensagem diz quantos.
2. **Clique ignorado quando a lista mudou** (`ModPayloads.sameMachine` e os handlers por índice: Desvincular, Voltar ao perfil, Atualizar, Renomear máquina): se o índice não bate com a posição, procure a máquina pela posição (`RemoteMachines.index(pos)`) e use esse índice; só recuse se a posição não está mais vinculada.
3. **Ids de grupo reaproveitados**: salve o `nextGroupId` no NBT do roteador (chave `next_group`; ausente = maior id + 1, como hoje), para um id nunca voltar depois de recarregar; o `RenameTarget` de grupo confere também o bloco quando o payload o tiver (ou passe a mandá-lo, subindo a versão).
4. **Máquina trocada continua recebendo até o Atualizar**: uma remota cujo bloco não é mais o do grupo dela não tem portas (a montagem pula, como "trocada"), sem custo por tick: a checagem vai na montagem das rotas (que já lê o bloco para a frente, com `isLoaded`), e a troca de bloco precisa provocar a remontagem daquele nó (descubra o aviso que já chega quando o bloco remoto é trocado — o listener de capability do `MachineCaches` — e use-o para pedir a remontagem, com o limite de remontagens adiadas que já existe).
5. **"7/32" diverge** entre a tela (`RangeView.used`) e o Vinculador (`RangeLinks.used`): uma conta só.
6. **Caches não limpos quando a recarga da config baixa o raio ou o limite**: chame o `pruneCaches` (ou o equivalente) na recarga, só nos roteadores carregados com alcance.
7. **`RouterRange.unlink` sem `isClientSide`**: guarde.
8. **`RemoteMachines.load` descarta grupo de id repetido junto com os cartões**: dê um id novo ao repetido em vez de descartar.
9. **Vincular e escolher o roteador conferem só `mayInteract`**: confira também `mayBuild` na posição da máquina (o modo aventura não vincula).
10. **`GroupView.icon()` cria um `ItemStack` por quadro** (`RangeList`/`GroupRow`): guarde por grupo, refeito só quando o snapshot muda.

Fora: o destino seletivo para fluidos e químicos (pendência à parte, no motor).

Fechamento: atualize `docs/mapa/`, `docs/progresso/` (histórico, pendências: tire o que foi feito) e o guia se um comportamento visível mudou (o Vinculador pedindo confirmação). Verificação completa e e2e das suítes tocadas (`vinculador`, `alcance`).
