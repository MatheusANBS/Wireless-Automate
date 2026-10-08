# Como conduzir os subagentes neste projeto

O que funcionou nas etapas 0, 1 e 2 (registro de tipos, aba Source e Tanque de Source), com 20+ tarefas e nenhuma refeita.

## Antes do primeiro despacho

- Trabalhe num ramo ou worktree, nunca direto no `main` sem o ok do dono.
- Mantenha um registro de progresso (o *ledger* da skill `superpowers:subagent-driven-development`) com uma linha `Task N: complete` por tarefa. Depois de uma compactação da conversa, confie no registro e no `git log`, não na memória.
- Leia o plano uma vez e procure contradições entre tarefas ou com as Restrições globais. Leve todas ao dono numa pergunta só, antes de começar.

## O despacho do implementador

Anote o `HEAD` (BASE) antes de cada despacho. A revisão usa o intervalo BASE..HEAD.

O pedido ao implementador leva só:
1. Uma linha sobre onde a tarefa se encaixa ("etapa 2, tarefa 3 de 7: o modelo do tanque").
2. O caminho do arquivo com o texto da tarefa (`task-brief` da skill), apresentado como "leia primeiro: são os seus requisitos, com os valores exatos".
3. Interfaces e decisões de tarefas anteriores que o texto não conhece (nomes de classes e métodos que já existem).
4. Como resolver as ambiguidades que você notou no texto.
5. O arquivo de relatório e o formato da resposta: status (DONE, DONE_WITH_CONCERNS, NEEDS_CONTEXT ou BLOCKED), commits, uma linha de testes e dúvidas.

E sempre estas regras do projeto:
- compile antes de rodar jogo (`./gradlew compileJava`); com `BUILD FAILED`, conserte em vez de esperar;
- nunca rode dois jogos ao mesmo tempo;
- não mexa em `run/options.txt`;
- verifique com `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource` (e o e2e se mexeu em tela);
- termine cada commit com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

Não cole o histórico das tarefas anteriores no pedido. O subagente precisa da tarefa dele, das interfaces que toca e das restrições, nada mais.

## Modelos

- O texto da tarefa já traz o código pronto (transcrever e testar): use o modelo mais barato.
- Integração entre vários arquivos, ou depuração: modelo padrão.
- Revisor de tarefa: no mínimo o modelo intermediário.
- Revisão final do ramo e correções nas rodadas 4 e 5: o modelo mais capaz.
- Sempre diga o modelo explicitamente.

## Revisão

- Cada tarefa recebe uma revisão com dois vereditos: aderência ao plano e qualidade. Entregue o diff ao revisor como arquivo (`review-package`), com as Restrições globais copiadas literalmente.
- Achados importantes voltam para o mesmo implementador (rodadas 1 a 3). Nas rodadas 4 e 5 entra um implementador novo, num modelo mais capaz.
- Achados menores vão para o registro e são triados na revisão final.
- Um achado que contradiz o plano é decisão do dono: mostre o achado e o trecho do plano e pergunte qual vale.

## Problemas que as revisões pegaram aqui

- Sourcelink parado: `canAcceptSource` dizia que cabia com a visão `int` cheia.
- Página do guia copiando bullets comuns que contradiziam o próprio bloco.
- Teste de nível que passava sem o código real (atalho com `scheduleTick`).
- Restauração de config num `TickTask` em vez de `try/finally`.

Vale pedir ao revisor final que procure exatamente esse tipo de coisa: um teste que não prova o que diz, um texto que contradiz o comportamento, um limite de `int` e `long`.

## Quando o dono interrompe

- "Verifica o agente, está demorando": olhe o processo, a saída e os arquivos que ele espera. Se está esperando algo que nunca virá, mate e reenvie com a instrução que faltou.
- Pedido novo no meio de uma tarefa: termine ou entregue a tarefa e então atenda.
