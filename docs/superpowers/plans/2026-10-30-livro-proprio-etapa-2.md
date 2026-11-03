# Livro próprio, etapa 2: as cenas interativas

Spec: `docs/superpowers/specs/2026-10-30-livro-proprio-design.md` (seção "Etapa 2: as cenas interativas"). Mapa do livro: `docs/mapa/livro.md`. Ramo: `livro-proprio` (o mesmo da etapa 1). Uma tarefa por vez, revisão de cada uma.

## Restrições globais

- As mesmas da etapa 1 (`docs/superpowers/plans/2026-10-30-livro-proprio-etapa-1.md`, "Restrições globais"): português; lado do cliente (`client/guide/`); lógica pura com JUnit; `ModPayloads.VERSION` continua `"40"`; performance (lei 8: por quadro só desenhar; nada de busca, montagem, lista ou `Component` novo no `render`); tamanho (tela até 500 linhas; a `GuideScreen` está com ~485: o estado e os gestos da cena vão para classes próprias); comentários no presente; verificação com as cinco runs e `./scripts/e2e.sh --only guia --variants pt`; commits sem atribuição; sem push.
- A conferência `problems()` do e2e continua limpa.

### Task 1: a cena gira e tem zoom

- `SceneView` (puro, com JUnit): yaw, pitch e zoom de uma cena a partir da `SceneCamera` e do `zoom` da página; `drag(dx, dy)` (graus por pixel de tela fixos, pitch preso entre 0 e 90), `scroll(delta)` (zoom entre 0,5 e 3), `reset()`; igualdade para saber se mudou.
- `GuideScenes` desenha com a `SceneView` da moldura (a matriz do quadro sai dela; os blocos continuam montados uma vez).
- Os gestos (uma classe própria, por exemplo `SceneInput`): arrastar com o esquerdo dentro da moldura gira (sem virar a folha, sem abrir link embaixo); a roda dentro da moldura dá zoom (fora dela vira a folha como hoje); clique duplo volta à vista da página. O estado de cada cena vive enquanto o livro está aberto.
- e2e: numa página com cena (o roteador), arrastar e conferir que a vista mudou, rodar e conferir o zoom, clique duplo e conferir que voltou; capturas antes e depois.
- **Custo:** girar e dar zoom só mudam números e a matriz do quadro.

### Task 2: as anotações

- O parser já lê `BlockAnnotation` (x, y, z, cor, texto) e `LineAnnotation` (de, para, cor, espessura, texto) em `SceneNote`. Desenhe por cima dos blocos da cena, na mesma vista: o contorno do bloco (as 12 arestas, um pouco maior que o bloco, sem profundidade para aparecer por cima) e a linha 3D com a espessura (um prisma ou a linha de largura fixa em pixels de tela, o que ficar nítido).
- A dica: quando o mouse se mexe sobre a cena, projete as caixas e as linhas com a vista atual e ache a anotação sob o mouse (a caixa projetada que contém o ponto, ou a linha a menos de alguns pixels); a dica mostra o texto dela (o `Component` feito uma vez por anotação). A anotação sob o mouse fica realçada.
- e2e: pôr o mouse sobre uma anotação de bloco conhecida (por exemplo a da fornalha em `networks`) e conferir o texto da dica; captura.
- **Custo:** caixas e linhas montadas uma vez por cena; a projeção para a dica só quando o mouse se mexe.

### Task 3: textos, docs e e2e

- Os textos que dizem que as cenas são paradas passam a dizer que giram: `README.md`, `README.en.md`, `docs/curseforge/descricao.md`, a página do livro (`scripts/guide/paginas/index.py`, seção do livro, com os gestos), `docs/mapa/livro.md`, `docs/especificacao.md` (se citar), e a tabela de gestos da spec.
- `docs/progresso/`: sai a pendência da etapa 2 e a da Porta de Energia escondida (agora dá para girar); estado e histórico do mês; próximo passo.
- Ângulos das cenas: as duas cenas que a etapa 1 girou para mostrar algo escondido (`router` e `signal-resonator`) podem ficar como estão.
- Verificação completa: as cinco runs e o `./scripts/e2e.sh` padrão.
