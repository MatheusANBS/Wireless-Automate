---
name: wa-feature
description: Fluxo completo para uma feature, tela, item ou bloco novo do Wireless Automate (mod NeoForge), da ideia ao commit - mockup em artifact para o dono aprovar, spec, plano em tarefas, um subagente por tarefa com revisão, GameTests nas três runs, e2e e progresso.md. Use sempre que o dono pedir algo novo ou uma mudança de comportamento no mod ("cria", "adiciona", "quero uma tela", "nova aba", "novo item", "etapa N", "faz o plano", "executa o plano", "subagente por tarefa"), mesmo que ele não fale em plano ou em subagentes. Não use para só rodar testes, só gerar imagens do CurseForge (wa-curseforge-imagens) ou só fechar versão (wa-release).
---

# Feature nova no Wireless Automate

O dono quer ver e aprovar a ideia antes de qualquer código, e depois quer que o trabalho ande sozinho, com qualidade, sem ser interrompido a cada tarefa. Este fluxo concilia as duas coisas. Antes de começar, leia `CLAUDE.md` e `docs/progresso.md` (o estado e o próximo passo) e, se a feature tocar no design, `docs/especificacao.md`.

A conversa é em português, e também o código, os comentários, os commits e as docs.

**Performance máxima em toda etapa (skill `wa-performance`, indispensável):** carregue-a junto com esta. O custo da ideia é a primeira pergunta, antes do visual; o plano diz o custo de cada tarefa; todo brief de implementador manda ler a skill e rodar `python -I .claude/skills/wa-performance/scripts/varrer_perf.py`; toda revisão confere as leis dela; e etapa que mexe em motor, menus ou render fecha com o bench antes e depois.

## 1. Ideia em artifact, antes de tudo

Depois das perguntas de escopo (poucas, uma de cada vez, com opção recomendada), publique um artifact com a ideia: mockups das telas no estilo das telas do mod, o fluxo, as decisões e o que fica de fora. Para texturas e modelos, gere um protótipo de verdade (PIL, a partir de `scripts/textures/gerar_texturas.py`) e mostre a imagem, porque o dono julga pelo visual ("não gostei do design, quero algo mais slim" só aparece vendo).

Espere o ok explícito. Se ele pedir mudanças, refaça o artifact. A memória `mockup-antes-de-executar` registra essa regra.

## 2. Spec e plano

- Spec em `docs/superpowers/specs/AAAA-MM-DD-<tema>-design.md`, com as decisões aprovadas (cores ARGB, capacidades, nomes de chave, o que muda no protocolo).
- Plano em `docs/superpowers/plans/AAAA-MM-DD-<tema>.md` (skill `superpowers:writing-plans`). Ele começa com uma seção **Restrições globais**, que cada tarefa herda. Use a de `docs/superpowers/plans/2026-10-08-aba-source.md` como modelo: idioma e traduções nos dois `lang`, mod opcional só no seu pacote, chaves de NBT, componentes e config que não mudam, `ModPayloads.VERSION`, performance, comando de verificação, linha de co-autoria.
- Feature grande vai em etapas (etapa 1, etapa 2...), cada uma com plano próprio que termina testável.
- Commite a spec e o plano antes de executar ("Plano da etapa N: ...").

## 3. Execução: um subagente por tarefa

Use `superpowers:subagent-driven-development`. O dono já escolheu "subagente por tarefa" como padrão. Detalhes de como despachar e revisar estão em `references/conducao-de-agentes.md`. Leia antes do primeiro despacho.

Em resumo:
- Um implementador por vez (nunca dois em paralelo nos mesmos arquivos), com o texto da tarefa num arquivo e a resposta num arquivo de relatório.
- Revisão de cada tarefa (aderência ao plano e qualidade) antes de seguir. Correções voltam para o mesmo implementador.
- Revisão final do ramo inteiro, no modelo mais capaz.
- Não pare para perguntar "posso continuar?" entre tarefas. Pare só para bloqueio real, para conflito com o plano ou no fim.

## 4. Verificação antes de cada commit

```bash
./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource
```

Se mexeu em tela, payload ou mundo, rode também o e2e: `./scripts/e2e.sh --only <suíte> --capture <prefixo>` (rápido por padrão; `--lista` mostra as suítes, `--completo` roda tudo; veja `docs/e2e.md`), cujo `run/e2e/result.txt` precisa dizer `OK`. Depois olhe as capturas novas em `run/e2e/` (folha de contato: `scripts/folha.py` da skill `wa-curseforge-imagens`). Texto cortado, gema preta e nível desatualizado só apareceram olhando as imagens.

Cada run de jogo usa uns 3 GB e o build service `oneGame` já serializa as runs. Mesmo assim, nunca dispare dois jogos ao mesmo tempo.

Comentários (skill `wa-comentarios`): antes do commit, rode `python -I .claude/skills/wa-comentarios/scripts/varrer.py` (só os arquivos mudados no ramo) e resolva os candidatos. Diga isso a todo implementador, e peça ao revisor de tarefa e ao revisor final que confiram no diff: código comentado, comentário desatualizado (nome, número ou comportamento que mudou), histórico ("antes era") e redundante.

Performance (skill `wa-performance`): antes do commit, rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` (os arquivos mudados contra o `origin/main`) e resolva ou justifique cada candidato no relatório. Diga isso a todo implementador, e peça ao revisor de tarefa e ao revisor final que confiram as leis da skill no diff (parado custa zero, caminho quente sem alocação, nada de busca repetida, trabalho proporcional à mudança, sem picos, cliente só com o que muda, nada por quadro além de desenhar, `setChanged` só quando precisa, memória sob demanda, medido e não suposto). Item do backlog (`references/auditoria.md`) feito numa tarefa é marcado lá com o commit e os números.

Tamanho e divisão (skill `wa-modular`): no plano, cada tela ou componente novo diz em quais classes vai, com a responsabilidade de cada uma; antes do commit, rode `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos que mudou e divida o que passar do limite. Diga isso a todo implementador, e peça ao revisor de tarefa e ao revisor final que confiram arquivo novo acima do alvo, componente que manda payload direto e lógica pura sem JUnit.

## 5. Fechamento da etapa

- `docs/progresso.md`: a tabela de estado, o próximo passo e uma linha no histórico (o que foi feito e os números de testes, por exemplo "157 + 7 + 15 GameTests, e2e OK").
- `docs/mapa/<área>.md` (o índice está no `CLAUDE.md`): atualize a linha da classe se surgiram arquivos ou papéis novos.
- Guia do jogador: edite `scripts/guide/gerar_guia.py` (nunca os `.md` gerados), nos dois idiomas, só com "como operar".
- Texturas: edite as paletas em `scripts/textures/gerar_texturas.py`, nunca os PNGs.
- Commit e push só quando o dono pedir. A versão nova é com a skill `wa-release`.

## Armadilhas que já custaram tempo

- **Agente travado:** um subagente ficou esperando `run/e2e/result.txt` depois de o build falhar, e o jogo nunca abriu. Diga a todo implementador: compile antes (`./gradlew compileJava`); se aparecer `BUILD FAILED`, pare e conserte, nunca espere o resultado. Se o dono disser que um agente está demorando, confira os processos (Gradle/Java) e mate os que esperam à toa.
- **`run/options.txt`:** o dono muda o `guiScale` de propósito para testar. Não mexa nesse arquivo (o e2e escolhe a escala só na sessão dele).
- **GameTests em paralelo** dividem `NetworkManager` e `NetworkSavedData`: teste pertinência, crie a própria rede em cada teste e espere o `onLoad` com `thenWaitUntil`. Um teste que "passa" com um atalho (um `scheduleTick` manual, por exemplo) não prova nada: exercite o caminho real.
- **Restaurar a config num teste:** faça de forma síncrona (`try/finally`). Um `TickTask` não é um atraso.
- **Mod opcional:** tipos dele nunca na assinatura de métodos de classes `@EventBusSubscriber` ou `@GameTestHolder`. A receita completa está em `wa-mod-opcional`.
- **Texto de tela:** todo texto variável passa por `GuiText`. O e2e confere `GuiText.clipCount()==0`. Uma dica cortada com "…" é bug.
- **UV fora da textura** (y > 16 num modelo) renderiza preto. Mantenha o modelo dentro do bloco.
- **Push não pedido:** já aconteceu de um ramo ir para o GitHub sem o dono pedir. Se acontecer, diga a ele.
