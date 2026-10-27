# Tela de Sinal, etapa 3 (mais peças e dados) · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Estado:** concluído em 27/10/2026 (Tasks 1, 1b, 2, layouts prontos e salvos, e 3); a etapa 4 (pixel art) espera o dono.

**Objetivo:** as peças Ícone de item, Barra, Medidor, Gráfico e Seta; os Baús, Baterias e Tanques do mod como dado, ligados pelo Vinculador, com entra e sai por item no Baú (sem "um item pela rede", removido); o relógio do jogo; e os componentes prontos com as peças novas.

**Arquitetura:** a ligação (`TvBinding`) ganha a fonte (`network`, `storage`, `clock`) com o NBT antigo lido como `network`; a Tela guarda as posições ligadas; o `TvManager` lê os armazenamentos carregados na volta da Tela; o Baú conta entra e sai só dos itens vigiados, na entrada do `KeyedStorage`; as peças novas são pintadas pelo `TvPainter` no redesenho, e o histórico do Gráfico fica no cliente.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), JUnit 5, GameTests, e2e do cliente.

**Spec:** [`docs/superpowers/specs/2026-10-27-tela-de-sinal-etapa-3-design.md`](../specs/2026-10-27-tela-de-sinal-etapa-3-design.md).

**Modo de execução (decisão do dono em 27/10, para poupar cota):** poucas tarefas grandes, um agente por tarefa, sem revisão por tarefa, uma revisão única no fim da etapa; e2e só no fim de cada tarefa, sem as variantes (`--variants pt,escuro` só na revisão final).

## Decisões deste plano (o dono pode trocar antes da tarefa em que entram)

1. **Escolher a Tela no Vinculador esquece o roteador, e vice-versa** (spec, decisão 1): o clique seguinte nunca é ambíguo.
2. **Sem limite de distância e entre dimensões** (pedido do dono em 27/10); chunk descarregado mostra o último número em Apagado, sem nunca carregar o chunk.
3. **Leitura direta do block entity do mod** (não por capability) para os armazenamentos ligados: são sempre do mod, e o caminho direto não aloca nem cacheia.

## Restrições globais

- Código, comentários, commits e docs em português; textos nos dois `lang` (`en_us.json`, `pt_br.json`), sempre os dois, na mesma ordem.
- **Chaves existentes não mudam.** As das etapas anteriores continuam; as novas são as da spec da etapa 3 ("Modelo e chaves"), todas opcionais com padrão, para um layout salvo antes ler e desenhar igual. **`ModPayloads.VERSION` sobe de `"36"` para `"37"`** (na primeira tarefa que mexe em payload).
- **Performance (skill `wa-performance`; leia `.claude/skills/wa-performance/SKILL.md`):** nada novo por tick no servidor; salvar e a biblioteca são eventos; nada sai para o cliente com o editor fechado (a biblioteca só com ele aberto); no mundo, nada por quadro além do que a etapa 1 faz: regras, fontes, tema, cantos e opacidade só no redesenho da textura; o piscar redesenha só a Tela com regra de piscar valendo e à vista, 2 vezes por segundo; no editor, a miniatura redesenha no máximo uma vez por quadro e só com o rascunho, os números ou o tema mudados; nada de alocação por quadro no desenho da GUI do editor além do que as telas do mod já fazem (textos medidos guardados até mudarem). Rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados e resolva ou justifique cada candidato no relatório.
- Lógica pura com JUnit (`src/test/java`); o que depende do jogo, com GameTest (`@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, `template = "empty"`, `timeoutTicks` ≤ 300; jogador só por `TestPlayers.inLevel`; cada teste cria a própria rede com `NetworkSavedData.get(server).create(...)`; compare redes com `Objects.equals`; teste que muda a config vai em `batch = "wirelessautomate_config_tv"` com `try/finally`; o `onLoad` só roda no tick seguinte: `startSequence().thenWaitUntil(...)`; testes do mesmo lote rodam em paralelo, então teste pertinência e não contagem global).
- Mod opcional: nada de tipos do Mekanism, Ars, FTB Teams ou Allthemodium em `tv/` e `client/tv/`; os tipos de recurso vêm do `ResourceType` e do `LoadedTypes`.
- Lado do cliente: tudo de tela e render só em `client/` (`client/tv/`, `client/tv/editor/`, `client/tv/render/`), nunca referenciado por código comum.
- Todo texto variável da GUI do editor passa por `GuiText` (o e2e confere `GuiText.clipCount() == 0`); cores da GUI pelo `GuiPaint`, nos dois temas; o botão `ThemeToggle` no cabeçalho. O texto **desenhado na Tela** (na parede e na miniatura) não passa pelo `GuiText`: encolhe pelo `TvFit` e nunca é cortado.
- Componentes de tela não mandam payload direto: tudo sai por uma classe de ações (`TvEditorActions`), que na captura só mexe no estado local.
- Tamanho (`wa-modular`): classe de tela até 500 linhas (alvo 300), servidor até 600; rode `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados. Comentários (`wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados.
- Guia: edite `scripts/guide/gerar_guia.py`, nunca os `.md` gerados. Não rode o `gerar_texturas.py` (reescreve dezenas de PNGs).
- Antes de cada commit: `./gradlew build runGameTestServer` (Git Bash, na raiz); tarefa só de lógica pura roda `./gradlew test --tests "..."`. As runs Chemicals, Source, Allthemodium e Teams ficam para a verificação final do ramo, antes do release (pedido do dono em 27/10, para poupar tempo). Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte, nunca espere um resultado que não vem. Tarefas de tela e render rodam o e2e (`./scripts/e2e.sh --only tela --capture tela`; `run/e2e/result.txt` precisa dizer `OK`), olham as capturas e copiam as novas para `.superpowers/sdd/2026-10-27-tela-de-sinal-etapa-3/capturas/` (o e2e seguinte apaga as de `run/e2e/`). Nunca dois jogos ao mesmo tempo (~3 GB cada); não mexa em `run/options.txt`. Se o e2e de outra suíte falhar por algo fora da tarefa, pare e relate: não entre em loop.
- Commits sem linhas de atribuição (nada de `Co-Authored-By` nem `Claude-Session`), com o autor do dono; `git add` só dos arquivos da tarefa (nunca `run/`, nunca `.superpowers/`). Não faça push.

## Tarefas

### Task 1: Fontes de dado e ligações pelo Vinculador

Custo: nada por tick sem Tela com armazenamento ligado; com ele, uma leitura por armazenamento carregado na volta da Tela.

- [ ] `TvBinding` com `source` (`network` padrão, `storage`, `clock`), `pos`, `item` opcional e as métricas por fonte (`TvStorageMetric`: `stored`, `capacity`, `fill`, `in`, `out`, `balance`, com o item opcional no Baú; `TvClockMetric`: `time`, `day`); NBT antigo lê igual (GameTest de ida e volta com layouts das etapas 1 e 2).
- [ ] `TvBlockEntity.links` (lista de `GlobalPos`, até `tv.maxLinks`), NBT e update tag; config `tv.maxLinks`.
- [ ] Vinculador: componente `linker_tv`; no modo Vincular, clique na Tela a escolhe (esquece o roteador; escolher roteador esquece a Tela); clique num armazenamento do mod com Tela escolhida liga ou desliga, com as recusas (teto, sem permissão, não é armazenamento do mod; sem limite de distância nem de dimensão) na action bar; tooltip com a Tela. As regras em `tv/TvLinks` (puro onde der, com JUnit).
- [ ] `TvManager`: lê `stored`, `capacity` e `fill` dos ligados carregados em qualquer dimensão (`server.getLevel` + `isLoaded`, nunca forçando o chunk); descarregado mostra o último número marcado como antigo (o cliente pinta em Apagado); removido ou desligado, "—". GameTest com um Baú em outra dimensão e com o chunk descarregado.
- [ ] Contadores: entra e sai do total em todo armazenamento (`ScalarStore` e `KeyedStorage`, dois `long` somados só com o armazenamento vigiado) e por item no Baú (`KeyedStorage` com o conjunto de vigiados e o contador na entrada); só `insert`/`extract` reais; o `TvManager` registra e solta a vigia pelo critério de "alguém perto"; `in`/`out` por segundo numa janela de 5 s.
- [ ] Bench antes e depois do Baú com e sem vigia (`./scripts/bench.sh` num cenário de Baú; números no relatório).
- [ ] GameTests (`TvLinkGameTests`, `TvStorageFeedGameTests`): ligar e desligar, recusas, leitura, vigia ligando e soltando, contador exato com inserção e extração reais e nenhuma conta nas simuladas. Commit "Tela de Sinal: armazenamentos ligados pelo Vinculador".

### Task 1b: Guardado na rede

Custo: sem Tela pedindo, zero; com Tela, a leitura do guardado uma vez por volta do amostrador pelos caches das portas. Nada no caminho de itens.

- [ ] `held`, `held_capacity` e `held_fill` (spec, decisão 12): o `FlowSampler` soma o conteúdo e a capacidade das portas em Armazém da rede × tipo pedida, só para as redes × tipos que alguma Tela pede (o `TvManager` registra o pedido pela volta, como a vigia do Baú).
- [ ] GameTests (o guardado com uma Bateria e um Baú em Armazém; sem Tela pedindo, a amostra não lê nada a mais). Commit "Tela de Sinal: guardado na rede".

### Task 2: As peças novas no desenho e no editor

Custo: só no redesenho; Gráfico e relógio redesenham por tempo, só a Tela à vista que os tem.

- [ ] Modelo: `icon`, `bar`, `gauge`, `graph`, `arrow` e os campos de estilo da spec, opcionais.
- [ ] Pintura (`TvPainter` e partes novas, uma por peça): Ícone pelo `ItemRenderer` na textura; Barra; Medidor em segmentos; Gráfico com o histórico do cliente (`TvGraphHistory`, puro, JUnit: buffer circular de 600, janela, escala); Seta pelo sinal com zona morta; relógio no cliente.
- [ ] Redesenho por tempo: o conjunto de Telas com Gráfico ou relógio à vista, no mesmo esquema do piscar da etapa 2 (sem varrer todas por quadro).
- [ ] Editor: as peças na paleta (com os ícones novos no `scripts/textures/tela_icones.py`, mesmo estilo), as abas de cada peça, a fonte `storage` e `clock` na aba Dado (lista dos ligados com nome, coordenadas e distância; a grade de itens do Baú pelo `TvStorageContentsPayload`, só com o editor aberto; arrastar do inventário e do JEI), protocolo 37.
- [ ] Componentes prontos novos: "Item do Baú", "Carga da Bateria", "Entra e sai".
- [ ] e2e: um Baú e uma Bateria ligados pelo Vinculador de verdade, o painel do mockup montado, capturas `tela-pecas`, `tela-grafico`, `tela-relogio`, `tela-editor-armazenamento`; `tela-desempenho` com Gráfico (antes e depois). Commit "Tela de Sinal: Ícone, Barra, Medidor, Gráfico e Seta".

### Task 3: Guia, docs e verificação

- [x] Guia (pt e en): ligar armazenamentos pelo Vinculador, as peças novas, o relógio; página do Vinculador com o clique na Tela.
- [x] `CLAUDE.md`, `docs/especificacao.md`, `docs/progresso.md`.
- [x] `./gradlew build runGameTestServer` e o e2e da Tela. Commit "Tela de Sinal: guia e docs da etapa 3".
