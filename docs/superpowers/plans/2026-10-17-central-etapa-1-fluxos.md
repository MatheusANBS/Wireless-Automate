# Plano da etapa 1 da central da rede: medição e Fluxos

Spec: `docs/superpowers/specs/2026-10-17-central-etapa-1-fluxos-design.md`. Ramo: `central-da-rede`, sobre o `origin/main` 05be9c7. Caminhos de código relativos a `src/main/java/io/github/matheusanbs/wirelessautomate/`.

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- Chaves existentes de NBT, componentes e config **não mudam**. Nada novo é salvo no mundo (contadores e janela só na memória).
- `ModPayloads.VERSION` sobe de `"19"` para `"20"`, só na Tarefa 3.
- **Performance é requisito:** no caminho quente só as duas somas por porta (sem alocação, sem mapa, sem chamada virtual nova); o amostrador nunca roda sem um Tablet na aba Fluxos; nada de tick por bloco; nada de busca de capability por tick; nada sincronizado com a tela fechada. Máquinas remotas nunca consultadas sem `level.isLoaded(pos)`.
- Mekanism e Ars opcionais: tipos deles só nos pacotes `compat/`; tipos pelo `LoadedTypes.LIST`.
- Telas só em `client/`, nunca referenciadas por código comum. Cores pelo `GuiPaint`; texto variável pelo `GuiText` (o e2e confere `GuiText.clipCount()==0`); tema claro e escuro. Regra do dono: **não poluir a tela** (texto curto só quando não é zero; o detalhe na dica).
- Tamanho (`wa-modular`): nada novo em `TabletScreen` (anfitriã) além de trocar a aba; `RouterBlockEntity` (999) e `RouterMenu` não crescem; o `NetworkManager` só chama o amostrador. Antes do commit: `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados.
- Comentários (`wa-comentarios`): antes do commit, `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados.
- GameTests: `@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, template `"empty"`; cada teste cria a própria rede; pertinência, não contagem global (os lotes dividem o `NetworkManager`); jogador só por `TestPlayers`; `onLoad` com `thenWaitUntil`; redes com `Objects.equals`. O amostrador é global: teste por rede própria, nunca pelo total do servidor.
- Verificação: durante a tarefa, só o que a mudança toca. Uma vez no fim da tarefa: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`; nas tarefas de tela, também o e2e (`WA_E2E="$PWD/run/e2e" ./gradlew runClient`, `run/e2e/result.txt` = `OK`), olhando as capturas. Com `BUILD FAILED`, pare e conserte. Um Gradle/jogo por vez. Não mexa em `run/options.txt`.
- Commits: um por tarefa, sem linhas de atribuição, sem push.

## Mapa de arquivos

| Arquivo | Tarefa | Papel |
| --- | --- | --- |
| `network/Port.java` e os cinco `*Transfer` (`ItemTransfer`, `ItemBulkTransfer`, `FluidTransfer`, `ScalarTransfer`, `ChemicalTransfer`) | 1 | Os dois contadores e as somas |
| `network/FlowWindow.java` (novo, puro) + `src/test/.../FlowWindowTest.java` | 2 | A janela de 5 baldes |
| `network/FlowSampler.java` (novo), `network/FlowStats.java` (novo, os números de uma rede × tipo), `network/NetworkManager.java` | 2 | O amostrador e o registro de quem olha |
| `packet/TabletFlowsPayload.java` (novo), `packet/ModPayloads.java` (ou `TabletPayloads`), `menu/TabletMenu.java` / parte nova de pacote `menu/TabletFlows.java`, `menu/TabletSnapshot.java` | 3 | Enviar só na aba Fluxos; tirar do cabeçalho o que só as Estatísticas usavam |
| `client/tablet/FlowsTab.java` (novo, anfitriã da aba), partes pequenas se passar de 500 linhas, `client/tablet/StatsTab.java` (sai), `client/tablet/TabletScreen.java`, lang | 3 | A aba Fluxos |
| `gametest/FlowGameTests.java` (novo) e os testes de Químicos e Source | 1–3 | GameTests |
| `client/dev/E2eTabletSteps.java` | 3 | Capturas |
| `docs/benchmark.md`, `scripts/guide/gerar_guia.py`, `docs/especificacao.md`, `CLAUDE.md`, `docs/progresso.md`, `docs/preview/central-da-rede.html` | 4 | Bench depois e documentação |

---

### Tarefa 1: os contadores da porta

1. `Port`: dois campos `long` package-private, por exemplo `gave` (o que entregou como origem) e `got` (o que recebeu como destino), com javadoc curto (quem soma, quem lê e zera: o `FlowSampler`).
2. Some nos pontos já conhecidos, sem alocar: a origem soma o total entregue no fim da visita que moveu (onde hoje é chamado `addMoved`/`endVisit`), e o destino soma o entregue em cada entrega, junto do `destinationBackoff.wake()` que já existe. Faça nos cinco transfers (itens pilha a pilha e rajada, Baú do mod, fluidos, escalares (energia e Source) e químicos). O que volta à origem pelo `ItemGiveBack` não conta como entregue; o resgate num outro destino conta para ele.
3. GameTests em `gametest/FlowGameTests.java`: itens Extrai → Insere (o `gave` da origem e o `got` do destino crescem do mesmo tanto); Extrai → Armazém e Armazém → Insere; fluidos; energia (a divisão entre dois destinos soma certo). Leia os contadores por um acesso de teste package-private (como os getters de diagnóstico do `NetworkManager`). Nas runs de Químicos e Source, um teste de cada no namespace delas.
4. Commit: `Motor: contadores de entrega e recebimento por porta`.

### Tarefa 2: o amostrador e a janela

1. `network/FlowWindow` (puro, sem classes do Minecraft): N baldes de 1 s (N = 5), `add(...)` no balde atual, `roll()` a cada segundo, `perSecond()` com a média dos baldes cheios e `filled()` para o "medindo…". JUnit em `src/test`.
2. `network/FlowSampler` (um por `NetworkManager`): `watch(player)`/`unwatch(player)` (quem tem a aba Fluxos aberta) e `tick(now)`, chamado pelo `NetworkManager` a cada tick, que só trabalha de 20 em 20 ticks e só com alguém olhando. A cada amostra: para cada rede com rotas, cada porta: lê e zera `gave`/`got`; soma em entra (`EXTRACT`, `gave`), sai (`INSERT`, `got`), armazém recebe e armazém dá (`BOTH`); conta os estados (movendo se moveu na amostra; vazia e espera destino pelo `sourceBackoff` e `waitsDestination`; cheio pelo `NodeProbe.isFull`). Das configurações dos nós carregados, conte as faces paradas por redstone (por rede) e as sem rede (por tipo), na presa, nos perfis e nas exceções. A primeira amostra depois de ficar sem ninguém olhando só zera os contadores. Guarde por rede × tipo uma `FlowWindow` para cada número e os estados da última amostra num `FlowStats` imutável que a Tarefa 3 lê. Esqueça redes que sumiram.
3. GameTests: sem ninguém olhando, nada é amostrado (os contadores só crescem); com um jogador olhando, depois de duas amostras os totais da rede própria aparecem certos (entra, sai, saldo de um Armazém que enche); estados vazia, espera destino, cheio, redstone e sem rede, cada um numa rede própria.
4. Commit: `Motor: amostrador dos fluxos, só com a aba Fluxos aberta`.

### Tarefa 3: o payload e a aba Fluxos

1. Servidor: o `TabletMenu` (pela parte de pacote nova `menu/TabletFlows`, para o menu não crescer) registra o jogador no `FlowSampler` quando a aba Fluxos está aberta (`setTab`, abrir, fechar no `removed`) e, a cada 20 ticks, monta o `TabletFlowsPayload` com as redes que o jogador vê (a mesma visibilidade do Tablet) e manda só se mudou. Tire do `TabletSnapshot` o que só a aba Estatísticas usava (confira cada campo por grep). `ModPayloads.VERSION = "20"`.
2. Cliente: `client/tablet/FlowsTab` no lugar do `StatsTab` (apague o `StatsTab`), conforme a spec e o mockup aprovado (seção Fluxos, "Por tipo" e "Por rede"): a barra do tempo do mod, o seletor Por tipo | Por rede (lembrado na sessão), as linhas, o ▸ do tipo, as dicas, o clique que filtra a Lista (o mesmo efeito do cartão de hoje, com a rede quando a linha é de rede), "medindo…", rede pausada apagada, o vazio. Unidades e números pelo `RateFormat`/`ResourceStyle`. A aba se chama "Fluxos"/"Flows".
3. GameTests: o payload só sai na aba Fluxos e só quando muda; trocar de aba tira o jogador do amostrador; o jogador não vê redes alheias privadas.
4. e2e (`E2eTabletSteps`): substituir as capturas das Estatísticas (`7c-tablet-estatisticas`, `7d-...-grande` e as que dependem delas) por `7c-tablet-fluxos` (Por tipo, com um tipo aberto), `7c-tablet-fluxos-rede`, a versão em pt e a no escuro; o filtro do clique continua conferido. Olhe as capturas.
5. Commit: `Tablet: a aba Fluxos no lugar das Estatísticas`.

### Tarefa 4: bench depois e documentação

1. `./scripts/bench.sh many:500:3:vanilla,raw:1:3:vanilla,mixed:498:3,idle:500:3,tablet:1000:3` no fim; compare com a rodada de antes (o relatório dela fica em `run/bench/reports/`; o nome do arquivo está no registro da etapa e no relatório da Tarefa 4) e registre a tabela antes × depois em `docs/benchmark.md`. Se o custo por visita subir mais que o ruído entre repetições, pare e avise (não siga com a documentação).
2. Guia (`scripts/guide/gerar_guia.py`, nunca os `.md`), pt e en: a página do Tablet troca Estatísticas por Fluxos (entra, sai, saldo; Por tipo e Por rede; o clique). `docs/especificacao.md` (Tablet: Fluxos; Arquitetura de performance: os contadores e o amostrador), `CLAUDE.md` (classes novas, protocolo `20`, números de testes), `docs/progresso.md` (estado, próximo passo: etapa 2, identificar máquinas; linha no histórico) e o texto do mockup `docs/preview/central-da-rede.html` onde ele define Entra e Sai (Entra = Extrai, Sai = Insere, saldo = Armazéns).
3. Verificação completa uma vez (as quatro runs + e2e) e commit: `Docs: etapa 1 da central da rede (Fluxos)`.
