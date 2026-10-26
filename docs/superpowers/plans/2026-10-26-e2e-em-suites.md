# E2E em suítes: rápido por padrão, capturas e variantes sob demanda · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** o e2e (`client/dev/DevEndToEnd` + `E2eScript` + 30 classes `E2e*Steps`) deixa de ser um bloco único e demorado. Passa a ser um conjunto de **suítes** que o dono escolhe (uma, várias ou todas), **rápido por padrão** (só asserções), com **capturas, variantes (pt_br, tema escuro) e medidas de desempenho só quando pedidas**, e com o tempo medido por suíte no `result.txt`.

**Pedido do dono (26/10/2026):** "no e2e ainda tem muita coisa redundante, como a comparação com o sistema antigo (o reator piscando entre shader e sem shader), e muita coisa que demora demais e é ineficiente. Dá para separar os e2e para rodar separados e rodar juntos quando quiser, e uma versão que captura só o que eu quero? Isso deve poupar muito tempo." Decisões do dono: **rápido por padrão** (asserções, sem capturas, sem comparação antigo × plasma, sem repetir em pt_br e no escuro) e **lista de suítes mais filtro de captura** como seletor.

**Arquitetura:** (1) um filtro de captura no motor (`WA_E2E_CAPTURE`) que torna a captura pulada um passo instantâneo; (2) um registro de suítes no `E2eScript` (`Suite(nome, descrição, pré-requisito, passos, padrão ou opcional)`), escolhido por `WA_E2E_ONLY=a,b,c`; (3) as variantes de idioma e de tema e o desempenho viram blocos que só entram no roteiro quando ligados (`WA_E2E_VARIANTS`) ou em suíte própria; (4) o motor mede e grava o tempo por suíte e os passos mais lentos; (5) um `scripts/e2e.sh` com flags, que roda no Windows (Git Bash) e no Linux (Xvfb).

**Stack:** Java 21 (cliente de dev, pacote `client/dev`), Bash, GameTests só para a lógica pura (filtros e seleção, JUnit).

## Variáveis de ambiente (o contrato novo)

| Variável | Valor | Efeito |
| --- | --- | --- |
| `WA_E2E` | pasta de saída | liga o e2e (como hoje) |
| `WA_E2E_ONLY` | `a,b,c` (nomes de suítes), `todas` ou vazio | quais suítes rodam; vazio = o conjunto padrão (todas menos as opcionais); `lista` só imprime as suítes no log e sai |
| `WA_E2E_SKIP` | `a,b` | tira suítes do conjunto escolhido |
| `WA_E2E_CAPTURE` | `off` (padrão), `todas`, ou prefixos separados por vírgula (`reparador-tela,ressonador-camara`) | quais capturas saem; as outras viram passos instantâneos (sem esperar os 6 ticks nem gravar PNG) |
| `WA_E2E_VARIANTS` | `off` (padrão), `todas`, ou `pt`, `escuro` separados por vírgula | se as repetições em português e no tema escuro entram no roteiro |
| `WA_E2E_PROFILE` | `rapido` (padrão) ou `completo` | `completo` = `CAPTURE=todas`, `VARIANTS=todas` e as suítes opcionais (desempenho, legado) incluídas; as outras variáveis, quando presentes, ganham do perfil |

`result.txt` ganha, no fim, uma seção "Tempo por suíte" (nome, passos, segundos, capturas) e os 10 passos mais lentos.

## Restrições globais

- Código, comentários, commits e docs em português. Nada de lógica nova no jogo (mod): só `client/dev/` (que só roda com `WA_E2E`), `scripts/e2e.sh` e docs. Nenhum payload, nenhuma chave de NBT, nenhuma config do mod muda.
- **Compatibilidade do contrato:** o `result.txt` continua começando por `OK` ou `FALHA: <passo> <motivo>`, seguido do log dos passos (as ferramentas e o `scripts/e2e.sh` leem a primeira linha); os nomes dos passos e das capturas não mudam (o que não roda simplesmente não aparece).
- A lógica pura (parser de listas, filtro de capturas, seleção de suítes, ordenação por dependência) fica em classes sem Minecraft, com JUnit (`src/test/java`): `E2eSelection` (nome de suíte → lista), `CaptureFilter`.
- **Cada suíte tem de rodar sozinha** (o pré-requisito dela é declarado) e junto das outras na ordem do registro; um passo que dependia de estado deixado por uma suíte anterior é corrigido declarando o pré-requisito, nunca copiando passos.
- Performance (skill `wa-performance`): o e2e é ferramenta de dev e não toca no caminho quente do mod; a meta é o tempo de parede do e2e: cada tarefa diz o que economiza e o número medido antes e depois (a **linha de base** vem da tarefa 7 do Reparador: `.superpowers/sdd/2026-10-26-reparador-etapa-2/task-7-report.md`, seção "Linha de base do e2e completo"; se não existir, meça você: rode o e2e completo uma vez antes de mudar qualquer coisa).
- Comentários (`wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados. Tamanho (`wa-modular`): `E2eScript` e as classes de passos continuam pequenos (o registro cabe em ~200 linhas); rode `python -I .claude/skills/wa-modular/scripts/medir.py`.
- Antes de cada commit: `./gradlew compileJava` e `./gradlew test` (JUnit); `./gradlew runGameTestServer` só na tarefa 5. Nunca dois jogos ao mesmo tempo (cada cliente usa ~3 GB e abre uma janela real nesta máquina Windows: não toque no mouse); não mexa em `run/options.txt`. Commits sem linhas de atribuição; `git add` só dos arquivos da tarefa (nunca `run/`, nunca `.superpowers/`, nunca `__pycache__`). Não faça push.

## Mapa de arquivos

Base `D/` = `src/main/java/io/github/matheusanbs/wirelessautomate/client/dev/`, `P/` = `src/main/java/io/github/matheusanbs/wirelessautomate/`, `T/` = `src/test/java/io/github/matheusanbs/wirelessautomate/`.

| Arquivo | Papel |
| --- | --- |
| `D/E2eSelection.java` (novo, puro) | Lê `WA_E2E_ONLY`, `SKIP`, `CAPTURE`, `VARIANTS`, `PROFILE` e responde: quais suítes, se uma captura sai, se uma variante entra |
| `T/client/dev/E2eSelectionTest.java` (novo) | JUnit da seleção |
| `D/E2eSuites.java` (novo) | O registro das suítes (nome, descrição, pré-requisito, passos, padrão/opcional) |
| `D/E2eScript.java` (mudar) | Monta o roteiro pelas suítes escolhidas, na ordem do registro |
| `D/DevEndToEnd.java` (mudar) | O filtro nas duas formas de `capture`, o cronômetro por suíte e o resumo no `result.txt` |
| `D/E2eVariants.java` (novo) | `pt(list, corpo)` e `escuro(list, corpo)`: o bloco só entra quando a variante está ligada |
| `D/E2e*Steps.java` (mudar, as que repetem em pt_br ou no escuro) | Usam `E2eVariants` |
| `D/E2eResonatorSteps.java`, `E2eResonatorFlowSteps.java` (mudar) | O desempenho (tempo de quadro) e o desenho antigo viram as suítes opcionais `ressonador-desempenho` e `ressonador-legado` |
| `scripts/e2e.sh`, `docs/e2e.md` (mudar/novo) | O runner com flags e o guia de uso; `CLAUDE.md` e as skills (`wa-feature`, `wa-shaders`) apontam para ele |

---

### Task 1: A seleção pura e o filtro de captura

**Files:**
- Create: `D/E2eSelection.java`, `T/client/dev/E2eSelectionTest.java`
- Modify: `D/DevEndToEnd.java` (`capture(String)` e `capture(Minecraft, String)`)

**Interfaces:**
- Produces: `E2eSelection.parseList(String raw)` → `List<String>` (separa por vírgula, tira espaços e vazios, minúsculas); `E2eSelection.captureWanted(String mode, String file)` → `boolean` (`off` nunca; `todas` sempre; senão `file.startsWith` de algum prefixo da lista); `E2eSelection.variantOn(String mode, String variant)` → `boolean` (`off` não; `todas` sim; senão a variante está na lista); `E2eSelection.profile(String name)` → registro com os padrões de `CAPTURE`, `VARIANTS` e se as opcionais entram; a seleção de suítes (Tarefa 2) usa estas.

- [ ] **Step 1: Escreva o JUnit que falha** (`E2eSelectionTest`): lista com espaços e vazios (`" a, ,B ,c"` → `[a, b, c]`); `captureWanted("off", "x")` falso; `("todas", "x")` verdadeiro; `("reparador-tela,ressonador-camara", "reparador-tela-escuro")` verdadeiro e `("reparador-tela", "ressonador-camara")` falso; `variantOn("off","pt")` falso, `("todas","escuro")` verdadeiro, `("pt","pt")` verdadeiro e `("pt","escuro")` falso; `profile("rapido")` → captura `off`, variantes `off`, opcionais fora; `profile("completo")` → captura `todas`, variantes `todas`, opcionais dentro; perfil desconhecido → `rapido`.
- [ ] **Step 2: Rode e veja falhar:** `./gradlew test --tests "*E2eSelectionTest"`.
- [ ] **Step 3: Implemente** `E2eSelection` (classe `final`, estática, sem Minecraft; o pacote `client/dev` já é carregado só no cliente de dev) e ligue o filtro no motor: em `capture(String file)`, se `!captureWanted(...)` devolva um passo instantâneo (`new Step("captura " + file + " (pulada)", ..., () -> {}, () -> true, ...)`, sem esperar `stepTicks >= 6` nem mexer o mouse); em `capture(Minecraft, String name)` (as capturas feitas por dentro dos passos), retorne sem gravar se o filtro recusar; a captura `falha` (quando um passo falha) **sempre** sai. As variáveis são lidas uma vez (campos `static final`).
- [ ] **Step 4: Rode e veja passar** (`./gradlew test --tests "*E2eSelectionTest"` e `./gradlew compileJava`).
- [ ] **Step 5: Verificação no jogo:** `WA_E2E="$PWD/run/e2e-t1" WA_E2E_ONLY=reparador ./gradlew runClient` sem `WA_E2E_CAPTURE` deve dar `OK` **sem nenhum PNG** (confira `ls run/e2e-t1/*.png`), e com `WA_E2E_CAPTURE=reparador-tela-parado` só as capturas com esse prefixo; anote o tempo de parede dos dois.
- [ ] **Step 6: Commit** (`git add` dos 3 arquivos): "e2e: seleção pura e filtro de capturas (padrão sem capturas)".

---

### Task 2: O registro de suítes

**Files:**
- Create: `D/E2eSuites.java`
- Modify: `D/E2eScript.java`, `D/E2eSelection.java` (seleção de suítes), `T/client/dev/E2eSelectionTest.java`

**Interfaces:**
- Consumes: as classes `E2e*Steps` existentes e os ramos `WA_E2E_ONLY` atuais do `E2eScript` (tablet, vinculador, alcance, ressonador, reparador, guia) e a ordem do roteiro completo.
- Produces: `E2eSuites.ALL` (lista ordenada de `Suite(String nome, String descricao, Prereq prereq, Consumer<List<Step>> passos, boolean opcional)`); `Prereq` = `NENHUM` (basta a abertura), `REDES` (a abertura + `networkSteps` + `close("fechar B")`); `E2eSelection.selectSuites(String only, String skip, boolean perfilCompleto, List<String> nomesDisponiveis, Set<String> opcionais)` → nomes escolhidos na ordem do registro, com erro claro (`IllegalArgumentException` listando os nomes válidos) para um nome desconhecido.

- [ ] **Step 1: Mapeie o roteiro atual em suítes.** Leia `E2eScript.script()` e cada `E2e*Steps` chamado: agrupe os passos do roteiro completo em suítes com nomes curtos e estáveis, na MESMA ordem de hoje: `roteador` (filterSteps, layoutSteps, panelSteps, attachedFrameSteps, cardSteps, chunkUpgradeSteps: confira o que cada um pressupõe), `alcance`, `tablet`, `configurador-vinculador`, `portugues`, `armazenamento` (storageChestSteps), `source` (só com o Ars Nouveau: `StorageKind.SOURCE_TANK.loaded()`), `receitas` (recipeEditorSteps), `escuro` (darkSteps), `ressonador`, `ressonador-jei`, `reparador`, `guia` (só com o GuideME). Mantenha os nomes que o dono já usa (`tablet`, `vinculador`, `alcance`, `ressonador`, `reparador`, `guia`) como nomes ou apelidos. Para cada suíte, descubra e declare o pré-requisito (`NENHUM` ou `REDES`) **testando-a sozinha** (Step 4).
- [ ] **Step 2: Escreva os JUnit que falham** (seleção de suítes): vazio → todas as não opcionais na ordem do registro; `"b,a"` → `[a, b]` na ordem do registro (não na da lista); `todas` → todas as não opcionais; perfil completo → inclui as opcionais; `skip` tira; nome desconhecido → `IllegalArgumentException` com os nomes válidos; `lista` → sinaliza "só listar".
- [ ] **Step 3: Implemente** `E2eSuites` e o `E2eScript.script()` novo: `openingSteps` sempre; depois, se alguma suíte escolhida tem `REDES`, `networkSteps` + `close("fechar B")` **uma vez**; depois as suítes, na ordem do registro. `WA_E2E_ONLY=lista` escreve no log uma linha por suíte (nome, descrição, pré-requisito, padrão/opcional) e fecha o jogo sem rodar (ou melhor: um `main` de linha de comando que lê `E2eSuites.ALL` sem abrir o jogo, se couber sem custo; senão o `scripts/e2e.sh --lista` da Tarefa 4 mostra a tabela estática de `docs/e2e.md`).
- [ ] **Step 4: Prove as suítes isoladas e juntas:** rode CADA suíte sozinha (`WA_E2E_ONLY=<nome>`, capturas desligadas: é rápido) e anote `OK`/`FALHA` e o tempo de cada uma; uma que falha sozinha por depender de estado de outra ganha o pré-requisito certo (ou o passo que prepara o estado entra no começo dela), até todas passarem sozinhas. Depois rode o conjunto padrão inteiro uma vez e confira `OK`. Compare o número de passos do conjunto padrão com o do e2e completo de antes (a diferença deve ser só o que a Tarefa 3 ainda não tirou: nada nesta tarefa).
- [ ] **Step 5: Commit:** "e2e: registro de suítes e seleção por WA_E2E_ONLY (uma, várias ou todas)".

---

### Task 3: As variantes e o desempenho viram opcionais

**Files:**
- Create: `D/E2eVariants.java`
- Modify: as `E2e*Steps` com repetição em pt_br ou no escuro (`E2ePortugueseSteps`, `E2eGuideSteps`, `E2eJeiResonatorSteps`, `E2eRangeSteps`, `E2eRepairerSteps`, `E2eResonatorScreenSteps`, `E2eResonatorSteps`, `E2eSourceTankSteps`, `E2eTabletMachinesSteps`, `E2eAccessSteps`, `E2eNetworkSectionSteps`, `E2eTabletMarkSteps`, `E2eTabletTreeSteps`, `E2eDarkSteps`, mais as que o `grep -n 'language("pt_br")\|GuiThemes.preview\|GuiThemes.set' D/E2e*.java` achar), `D/E2eResonatorSteps.java` e `E2eResonatorFlowSteps.java`

**Interfaces:**
- Consumes: `E2eSelection.variantOn`.
- Produces: `E2eVariants.pt(List<Step> list, Runnable corpo)` e `E2eVariants.escuro(List<Step> list, Runnable corpo)` (o corpo acrescenta passos a `list`; só roda quando a variante está ligada); as suítes opcionais `ressonador-desempenho` (os passos `frameTime(...)` e as situações medidas) e `ressonador-legado` (os passos que forçam `ResonatorRenderer.forceLegacyForTests` para capturar ou comparar o desenho antigo).

- [ ] **Step 1: Inventário (sem mudar nada):** liste, por arquivo, cada bloco que (a) troca para `pt_br` e volta, (b) liga o tema escuro e volta, (c) mede o tempo de quadro, (d) força o desenho antigo. Para cada bloco, diga quantos passos e capturas tem. Ponha a tabela no relatório. Siga a regra: o que só repete o que o idioma/tema padrão já provou fica atrás da variante; o que prova algo que só existe no idioma (texto cortado no pt_br, `GuiText.clipCount()`) continua por trás da variante `pt` (ligada no perfil completo), **mas** o `clipCheck` do idioma inglês e do tema claro continua sempre.
- [ ] **Step 2: Implemente** `E2eVariants` e envolva cada bloco: `E2eVariants.pt(list, () -> { list.add(language("pt_br")); ...; list.add(language("en_us")); })`. O bloco precisa ser autocontido (entra e volta para o estado padrão), sem passo fora dele que dependa do idioma trocado; se houver, ajuste.
- [ ] **Step 3: Desempenho e legado:** mova os `frameTime(...)` e as capturas do desenho antigo para as suítes opcionais `ressonador-desempenho` e `ressonador-legado` (registradas como `opcional = true` no `E2eSuites`), sem mudar o que fazem; o `ressonador` padrão fica só com o plasma (asserções e as capturas dele, que seguem o filtro). Confirme que nada no `ressonador` padrão fica ligado a `forceLegacyForTests`.
- [ ] **Step 4: Meça:** rode o conjunto padrão (rápido) e o completo (`WA_E2E_PROFILE=completo`) e compare com a linha de base: passos, capturas e tempo de parede. O completo deve ter os mesmos passos de antes (mais os das suítes opcionais, que antes já estavam dentro do `ressonador`).
- [ ] **Step 5: Commit:** "e2e: variantes de idioma e tema e o desempenho do Ressonador só sob demanda".

---

### Task 4: O motor mede o tempo e o runner novo

**Files:**
- Modify: `D/DevEndToEnd.java` (cronômetro por suíte e resumo), `D/E2eSuites.java` (marca o início e o fim de cada suíte no roteiro), `scripts/e2e.sh`
- Create: `docs/e2e.md`
- Modify: `CLAUDE.md` (seção "Comandos" e a linha do `client/dev/`), `.claude/skills/wa-feature/SKILL.md` e `.claude/skills/wa-shaders/SKILL.md` (a menção ao e2e: o comando rápido e o seletor)

- [ ] **Step 1: Marcadores de suíte e resumo.** Cada suíte começa e termina com um passo marcador (instantâneo, `"== suíte <nome> =="`) que o motor usa para somar os ms, passos e capturas por suíte; no `finish(...)`, o `result.txt` ganha, depois do log, as linhas `# Tempo por suíte` (nome, passos, segundos, capturas) e `# 10 passos mais lentos`. A primeira linha (`OK` / `FALHA: ...`) não muda.
- [ ] **Step 2: `scripts/e2e.sh` com flags** (mantendo o comportamento antigo sem flags e o `E2E_TIMEOUT`):

```bash
./scripts/e2e.sh                                  # conjunto padrão, rápido (sem capturas), em run/e2e
./scripts/e2e.sh --only reparador,ressonador      # só essas suítes
./scripts/e2e.sh --only tablet --capture tablet-  # com as capturas que começam por "tablet-"
./scripts/e2e.sh --completo                       # tudo: capturas, variantes e as suítes opcionais
./scripts/e2e.sh --lista                          # as suítes, com descrição e pré-requisito (lê docs/e2e.md ou E2eSuites)
./scripts/e2e.sh --skip guia,escuro --out run/e2e-x
```

Traduza as flags nas variáveis (`--only` → `WA_E2E_ONLY`, `--skip` → `WA_E2E_SKIP`, `--capture` → `WA_E2E_CAPTURE`, `--variants` → `WA_E2E_VARIANTS`, `--completo` → `WA_E2E_PROFILE=completo`, `--out` → o diretório). **Windows:** sem `xvfb-run` (Git Bash no Windows, o caso desta máquina), rode `./gradlew runClient --console=plain` direto, com a janela real, e avise na primeira linha que o jogo vai abrir uma janela e que não se deve mexer no mouse; no Linux, com `xvfb-run`, como hoje. Imprima no fim o resumo do tempo por suíte (as linhas `# Tempo por suíte` do `result.txt`) e o caminho das capturas.
- [ ] **Step 3: `docs/e2e.md`:** uma página curta: o que é, as variáveis (a tabela deste plano), as suítes (nome, descrição, pré-requisito, padrão/opcional, tempo medido), receitas (verificar uma tela nova só com captura dela; rodar tudo antes de fechar versão; investigar uma suíte que falha) e a regra "suíte nova: registre em `E2eSuites` e declare o pré-requisito". `CLAUDE.md`: troque os comandos de e2e da seção "Comandos" e o texto do `client/dev/` para o novo contrato (sem apagar o que continua valendo); nas skills, onde o e2e é citado como `WA_E2E_ONLY=<área> ./gradlew runClient`, aponte para `./scripts/e2e.sh --only <suíte> [--capture <prefixo>]`.
- [ ] **Step 4: Verificação:** rode `./scripts/e2e.sh --lista`, `./scripts/e2e.sh --only reparador`, `./scripts/e2e.sh --only reparador --capture reparador-tela-parado` e o `--completo`; anote os tempos de parede. 
- [ ] **Step 5: Commit:** "e2e: tempo por suíte no result.txt, runner com flags e o guia docs/e2e.md".

---

### Task 5: Prova final e números

**Files:**
- Modify: `docs/e2e.md` (a tabela de tempos medidos), `docs/progresso.md` (uma linha no histórico), `CLAUDE.md` (se algo mudou)

- [ ] **Step 1:** rode, um por vez (nunca dois jogos), e anote o tempo de parede e o `OK`: (a) o conjunto padrão rápido; (b) cada suíte sozinha (só as asserções); (c) o `--completo`; (d) `--only reparador --capture reparador-tela-parado`. Monte a tabela "antes (linha de base) × depois" com o total de passos, de capturas e de segundos.
- [ ] **Step 2:** `./gradlew build runGameTestServer` (JUnit novo e GameTests intactos: nada do mod mudou) e confira que `git status` só tem arquivos do e2e, do runner e das docs.
- [ ] **Step 3: Commit:** "e2e: tabela de tempos e fechamento da reforma em suítes". Relatório com a tabela e o que ficou de fora (se algo).

---

## Autorrevisão (feita ao escrever)

- **Cobertura do pedido:** separar em suítes que rodam sozinhas ou juntas (T2); versão que captura só o que se quer (T1, `WA_E2E_CAPTURE`); tirar a redundância antigo × plasma e as repetições pt_br/escuro do caminho padrão (T3); medir o tempo para saber onde está o desperdício (T4); runner e guia (T4); prova com números (T5).
- **Risco principal:** dependências escondidas entre áreas do roteiro completo (uma suíte que só passava porque outra deixou estado): a T2 manda testar cada suíte sozinha e declarar o pré-requisito.
- **Fora de escopo:** tornar o jogo do e2e mais rápido para subir (o tempo fixo de abrir o Minecraft e criar o mundo é de cada execução; juntar várias suítes numa execução só é a forma de diluí-lo), paralelizar clientes (cada um usa ~3 GB), o `DevScreenshot` (galerias de telas), a vitrine do CurseForge e os GameTests.
