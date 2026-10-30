---
name: wa-higiene
description: Limpeza das docs e higiene do repositório do Wireless Automate - acha e tira o que só gera ruído (planos de features já mescladas, mockups e capturas de docs/preview que nada cita, specs mortas, changelogs separados de versões antigas, itens feitos no "Próximo passo" do progresso, ramos e worktrees já absorvidos pelo main), com a varredura varrer.py e o ok do dono antes de apagar. Use quando o dono falar em "limpar o repositório", "lixo", "garbage", "higiene", "docs com ruído", "arquivo que não serve mais", "faxina", "enxugar o progresso", "apagar ramos velhos", "worktrees", e no fim de um release (a wa-release chama). Não use para limpar comentários do código (wa-comentarios) nem para dividir classes (wa-modular).
---

# Higiene do repositório

O repositório é público e é lido por agentes a cada sessão. Arquivo que ninguém usa custa duas vezes: o agente lê e se confunde (um plano velho descreve um código que mudou), e quem chega pelo GitHub não acha o que importa. **O git guarda tudo:** apagar um arquivo do main não perde nada, ele volta com `git show <commit>:<caminho>`. Por isso a regra é tirar sem dó o que não tem leitor, e não arquivar em pasta "old".

## O que fica e o que sai

| Arquivo | Fica enquanto | Depois |
| --- | --- | --- |
| `docs/especificacao.md`, `docs/progresso.md`, `docs/mapa/`, `docs/pacote-de-design.md`, `docs/identidade-visual.md`, `docs/e2e.md`, `docs/benchmark*.md`, `docs/portes*`, guias de pixel art | sempre (são a fonte da verdade) | — |
| Spec (`docs/superpowers/specs/`) | a especificação, o mapa, uma skill ou o código a citam, ou a feature ainda não terminou | o que ainda vale vai para a `especificacao.md` ou o mapa da área, e a spec sai |
| Plano (`docs/superpowers/plans/`) | a feature está em andamento (ramo aberto ou item no "Próximo passo") | sai depois do merge. A spec é o registro do desenho; o plano é andaime. Exceção: um plano usado de exemplo numa skill (`aba-source` na `wa-mod-opcional`) |
| Revisão (`docs/superpowers/reviews/`) | tem achado em aberto que o progresso cita | sai quando os achados foram feitos ou viraram itens no progresso |
| Mockup e captura (`docs/preview/`) | a feature está em andamento, ou o código, um script, o mapa ou uma skill o usam (`hitboxes.txt`, `folha-de-sprites.png`, `.bbmodel`, as capturas que o `gerar_imagens.py` lê) | sai depois do merge |
| Changelog separado (`docs/curseforge/changelog-X.md`) | é o da versão atual | sai: o unificado (`changelog-curseforge-X.md`) e as páginas do CurseForge, do Modrinth e as releases do GitHub guardam todos |
| Imagens do CurseForge (`docs/curseforge/*.png`, `*.gif`) | a `descricao.md`, o README ou um script as usam | a `wa-curseforge-imagens` decide |
| Item do "Próximo passo" do progresso | ainda falta fazer algo | sai; o histórico já registra o que foi feito |
| "Resumo" do progresso | a versão atual e o que está em andamento | as versões antigas saem (estão no histórico e nos changelogs) |
| Ramo local | tem commit fora do main (`git cherry main <ramo>` com `+`) | sai com o ok do dono, junto da worktree dele |

O histórico do `progresso.md` **não** se limpa: é o diário do projeto, e citar nele um arquivo que já saiu é normal.

## Roteiro

1. **Varredura:** `python -I .claude/skills/wa-higiene/scripts/varrer.py`. Ela lista os docs que nenhum documento vivo cita (raízes: `CLAUDE.md`, `AGENTS.md`, READMEs, skills, mapa, especificação, código, scripts, build e o "Próximo passo" do progresso, seguindo as citações em cadeia), os changelogs velhos, os ramos sem commit fora do main e as worktrees. Ela não apaga nada.
2. **Progresso primeiro:** enxugue o "Próximo passo" e o "Resumo" do `progresso.md` (tabela acima), porque item feito que continua lá segura plano, spec e mockup vivos à toa. Na dúvida se um item ainda vale, pergunte ao dono, não apague. Depois rode a varredura de novo.
3. **Confira cada candidato.** A varredura acha pelo nome do arquivo; um texto que cita o arquivo sem o nome exato escapa. Para cada um, `git grep -n "<nome sem extensão>"` fora de `docs/superpowers` e `docs/preview`. Uma spec que a especificação não cita mas que tem decisão que ainda vale: passe a decisão para a especificação ou o mapa antes de tirar.
4. **Mostre a lista ao dono**, agrupada (planos, specs, mockups, changelogs, ramos e worktrees), com o tamanho e uma linha do porquê de cada grupo. Apagar é com o ok.
5. **Apague com `git rm`**, num commit só de higiene (`Higiene: ...`), sem misturar com feature.
6. **Ramos e worktrees:** com o ok, `git worktree remove <caminho>` (só se `git -C <caminho> status --short` está vazio) e `git branch -d <ramo>` (o `-d` recusa ramo não mesclado; se recusar, confira o `git cherry` antes de pensar em `-D`). Ramo remoto e worktree fora da pasta do repositório (como `C:/wab`) só com pedido explícito. `git worktree prune` limpa as que já sumiram do disco.
7. **Pastas ignoradas** (`build/`, `run/`, `.gradle/`, `.superpowers/`, `.claude/worktrees/`) não estão no repositório; só mexa se o dono pedir espaço em disco, e nunca em `run/` sem perguntar (tem mundo e config de teste).
8. **Atualize o `progresso.md`** (linha no histórico) e, se uma regra desta skill mudou, a própria skill.

## Para não voltar a acumular

- A `wa-feature` cria spec, plano e mockup; ao mesclar a feature, o plano e o mockup saem no mesmo merge ou no release seguinte, e a spec é absorvida pela especificação ou fica citada por ela.
- A `wa-release` roda esta varredura no fechamento da versão e tira o changelog separado da versão anterior.
- Arquivo de trabalho que não é do projeto (rascunho, captura de teste, protótipo descartado) vai para a pasta de rascunho da sessão, não para `docs/`.
