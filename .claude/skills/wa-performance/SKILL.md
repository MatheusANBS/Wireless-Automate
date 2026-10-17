---
name: wa-performance
description: Performance máxima do Wireless Automate - o filtro de custo de toda mudança no mod (motor, menus e payloads, telas e render, block entities, NBT, memória), as leis de performance com exemplos do próprio mod, a varredura estática `varrer_perf.py`, o bench antes e depois e o backlog da auditoria de eficiência. Use em toda tarefa que mexe em código do mod, junto da wa-feature (no desenho, no plano, em cada brief de implementador, na revisão de tarefa e na revisão final), e sempre que o pedido falar em performance, eficiência, lag, TPS, MSPT, FPS, travada, otimizar, custo, bench, profile ou auditoria. Ao propor qualquer feature, o custo dela vem antes do visual.
---

# Performance máxima no Wireless Automate

O mod existe para ser **o transporte mais leve do ATM10**. Isso não é uma qualidade a mais, é o produto: uma base com milhares de roteadores tem de custar perto de zero quando nada se move, e o mínimo possível quando tudo se move. Toda linha nova compete por um orçamento que já é pequeno (1 ms por tick para o motor inteiro, por padrão) e por quadros do cliente.

Pense assim em toda decisão: **o que isto custa por tick, por visita, por entrega, por quadro e por byte, numa base com 2.000 roteadores, 200 deles com 64 máquinas vinculadas, e 5 jogadores com o Tablet aberto?** Se a resposta é "não sei", meça antes de seguir.

A conversa, o código e os comentários são em português.

## As leis (não negociáveis)

1. **Parado custa zero.** Rede ociosa, roteador sem nada a mover, tela fechada, jogador longe: nenhum trabalho, nenhuma alocação, nenhum pacote. O custo por tick sem nada a fazer é uma checagem de flag ou de relógio, não um laço.
2. **Nada de tick por bloco.** Um gerenciador central por sistema (`NetworkManager`, `ResonatorManager`), com o trabalho espalhado em fases (20 baldes pela posição) quando é periódico.
3. **Caminho quente sem alocação.** Visita, entrega, slot e quadro não criam objetos: nada de lambda que captura, stream, `List.of`, iterador de coleção boxed, `String.format`, `Component`, `BlockPos` novo, `ItemStack.copy` sem necessidade, `values()` de enum (clona o array: use uma constante `TYPES`). Contadores em campos `long` da porta. Reuse arrays e mapas (fastutil, com `clear()` e um `trim()` quando encolhe muito).
4. **Nenhuma busca repetida.** Capability só por `BlockCapabilityCache` (nunca `level.getCapability` no laço); `getBlockState` e registro fora do laço; o que só muda por evento fica em cache com uma versão (`changeVersion`, `NodeIndex.version()`, `NetworkSavedData.version()`), invalidado pelo evento, nunca recalculado por tick.
5. **Trabalho proporcional à mudança, não ao tamanho.** Remontagem só do tipo e da rede sujos; diferenças em vez de snapshot; contagens mantidas incrementalmente (nada de O(tipos) por entrega, O(nós) por amostra quando dá para ser O(mudanças)).
6. **Sem picos.** Nada de "tudo de uma vez num tick": carga de chunk, recarga de config, revalidação de estruturas, amostras e montagens grandes vão em lotes, fases ou com orçamento.
7. **O cliente só recebe o que a tela aberta mostra, e só quando muda.** Nada sincronizado com a tela fechada; o que muda rápido (vazão, estado) vai separado do que muda pouco (estrutura, nomes), em pacote pequeno; paleta e índices em vez de strings e UUIDs repetidos; nada que o cliente já sabe (a posição do jogador).
8. **Por quadro, só desenhar.** Layout, medida de texto (`font.width`, `split`), ordenação, filtro e montagem de linhas e textos acontecem quando o dado muda (chave suja), não a cada `render`. Desenhe só o que está à vista. No BER, agrupe por `RenderType` e não aloque.
9. **Mundo: o mínimo de efeitos colaterais.** `setChanged()` avisa os 6 vizinhos: use `level.blockEntityChanged(pos)` quando só precisa marcar o chunk, e só avise vizinhos quando um comparador ou um vizinho realmente depende. `setBlock` de mudança de estado visual com `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`. Nunca carregar chunk para consultar (`level.isLoaded(pos)` antes de qualquer remota).
10. **Memória proporcional ao uso.** Estruturas por porta e por máquina criadas sob demanda (nada de 9 caches ou 30 portas alocados para usar 2); fastutil em vez de `HashMap<Long,...>`; dados iguais compartilhados (filtros com as mesmas tags); mapas estáticos limpos no logout e na parada; nada que cresça sem limite.
11. **Medido, não suposto.** Toda mudança no motor, nos menus ou no render tem número de antes e depois. Regressão acima do ruído (o ± do relatório) não entra.

## Em toda tarefa

**No desenho (antes do mockup ou da spec):** escreva o custo da ideia: o que roda por tick, por visita, por entrega, por amostra, por quadro; quanto pesa em memória por porta ou por máquina; quantos bytes por segundo vão ao cliente e quando. Se o custo cresce com o tamanho da base, proponha a versão incremental, em fases ou sob demanda como a recomendada, e ponha o custo de cada opção na própria pergunta de decisão.

**No plano:** cada tarefa tem uma linha "Custo" (o que acrescenta em cada caminho e por quê está dentro do orçamento) e diz que medição prova. As Restrições globais copiam as leis acima que a tarefa toca.

**No brief de cada implementador:** "Leia `.claude/skills/wa-performance/SKILL.md` (as leis e a lista da área que você vai mexer). Antes do commit, rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados e resolva ou justifique cada candidato no relatório."

**Na revisão de tarefa e na final:** o revisor confere as leis no diff, roda o `varrer_perf.py` e procura o que o script não acha: trabalho sem nada a fazer, O(n) que poderia ser O(mudança), cache sem invalidação (bug) ou invalidação demais (custo), pico, pacote que sai sem mudança.

**Antes do merge de uma etapa que tocou motor, menus ou render:** o bench de `references/medir.md` antes e depois, com a tabela no `docs/benchmark.md`.

## Por área: o que procurar

- **Motor** (`network/`, `filter/`, `range/MachineCaches`, mixin): alocação por visita ou entrega; varredura O(slots) repetida na mesma visita; acordar em cascata (uma mudança que acorda centenas de origens e cada uma varre todos os destinos); remontagem por evento que não muda rota (redstone sem face de redstone, carga de chunk); laço que passa por portas dormindo; simulação que repete a varredura; capability ou estado lido por item.
- **Menus e payloads** (`menu/`, `packet/`, `NodeIndex`): montagem por tick sem mudança; snapshot inteiro onde caberia diferença; `equals` profundo de objeto grande a cada amostra; trabalho por jogador que é igual para todos (compartilhe); ordenação de tudo a cada montagem; campos que o cliente não usa ou já sabe; strings, ids e UUIDs repetidos por item.
- **Cliente** (`client/`): layout ou texto recalculado por quadro, várias vezes no mesmo quadro (render, overlay, hover, tooltip); listas grandes reordenadas a cada sincronização; cache limpo à toa (por exemplo a cada tecla); registro percorrido ao abrir tela; BER que troca de `RenderType` várias vezes ou aloca.
- **Block entities e NBT** (`storage/`, `resonator/`, `block/`): `setChanged` a cada operação; NBT recodificado inteiro a cada salvamento; contagem O(tipos) em toda entrega; `getStackInSlot` que aloca; SavedData que cresce sem limite; `setBlock` sem `UPDATE_KNOWN_SHAPE`.
- **Memória e carga**: bytes por porta, por máquina e por nó multiplicados por uma base grande; o que acontece no primeiro tick depois de carregar o mundo; eventos globais (tick do servidor e do cliente) que trabalham sem nada a fazer.

## A varredura estática

```bash
python -I .claude/skills/wa-performance/scripts/varrer_perf.py            # arquivos mudados contra o origin/main
python -I .claude/skills/wa-performance/scripts/varrer_perf.py --tudo     # o mod inteiro
python -I .claude/skills/wa-performance/scripts/varrer_perf.py a.java ... # arquivos escolhidos
```

Ela lista **candidatos** por área (alocação, busca repetida, efeito colateral no mundo, trabalho no render), com a lei que cada um fere. Não é veredito: uma alocação fora do laço ou uma busca única num `onLoad` está certa. Cada candidato num arquivo mudado é resolvido ou justificado no relatório da tarefa.

## Medir

O roteiro está em `references/medir.md`: os cenários do `/wa bench`, o comando de antes e depois, como ler o ruído, o JFR para achar o ponto quente, o `/wa profile` (com as linhas do Ressonador e dos Fluxos) e o que vale como regressão. Um Gradle, jogo ou bench por vez (cada um usa ~3 GB).

## O backlog

`references/auditoria.md` guarda os achados da auditoria de eficiência de 17/10/2026 (o relatório completo é `docs/preview/auditoria-eficiencia.html`), com id, onde, ganho, risco e estado. Ao terminar um item, marque-o feito com o commit e os números. Ao mexer numa área, olhe antes os itens pendentes dela: corrigir junto costuma sair mais barato. Achado novo de performance entra ali, com id novo.

## Armadilhas que já custaram

- O amostrador dos Fluxos sempre ligado ia percorrer todas as portas e a configuração de todos os nós de uma vez a cada segundo: pico de 1 a 10 ms sem ninguém olhando. A saída foi espalhar em 20 fases e guardar a configuração por nó com versão.
- A página do Tablet volta inteira a cada segundo porque a vazão de algum nó mudou (30–38 KB/s por jogador).
- O GameTest que trava gira o servidor a centenas de ticks por segundo e enche o log (100 MB em 10 min): todo teste novo com `timeoutTicks` de no máximo 300.
- Um `setChanged()` no lugar errado (o Núcleo a cada extração, o armazenamento a cada inserção) custa mais que o trabalho que ele marca.
