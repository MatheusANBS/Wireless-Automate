# Tablet do zero, etapa 2: a aba Máquina, plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** a aba Máquina do mockup aprovado: a lista de máquinas com busca e o estado de cada uma, o cartão do veredito ("por que parou"), as faces em uso editáveis ali mesmo, "Mostrar no mundo" e "Abrir roteador"/"Filtro e mais" abrindo a tela do roteador já no tipo e na face. O Tablet abre na última aba usada, e o clique com o Tablet numa máquina abre a aba Máquina com ela escolhida.

**Arquitetura:** no servidor, uma classe pura (`VerdictRules`, com JUnit) transforma fatos (`VerdictFacts`) num `Verdict` (motivo, detalhe, ação, problemas); o `VerdictProbe`, em `network/` porque o `Port` é package-private, lê os fatos das portas da máquina escolhida; o `TabletVerdict` (como o `TabletMap`) monta o veredito 1 vez por segundo, só com a aba Máquina aberta e uma máquina escolhida, e o `TabletVerdictPayload` só vai quando muda. A lista de máquinas é a página que o servidor já monta (`TabletMachineRows`), achatada no cliente (sem cabeçalho de bloco). As faces vêm do `RouterSnapshot` que o `TabletPanelPayload` já entrega e se editam pelos pacotes de face de sempre (o `TabletMenu` é um `RouterEditHost`). No cliente, a aba nova é `client/tablet/machine/`, do tamanho e da organização de `client/tablet/networks/` (anfitriã até ~335 linhas, modelo, janelas, probe do e2e). "Abrir roteador" e "Filtro e mais" levam o alvo, o tipo e a face no `TabletOpenNodePayload` e no buffer de abertura do `RouterMenu` (protocolo 48).

**Stack:** Java 21, NeoForge 21.1.248 (mínimo) / Minecraft 1.21.1, ModDevGradle, JUnit 5, GameTests, e2e (`scripts/e2e.sh`).

**Spec:** [`docs/superpowers/specs/2026-11-03-tablet-do-zero-design.md`](../specs/2026-11-03-tablet-do-zero-design.md), etapa 2 (seção "Máquina" e decisões 6, 7 e 10). **Plano anterior:** [`2026-11-03-tablet-do-zero-etapa-1.md`](2026-11-03-tablet-do-zero-etapa-1.md) (já commitado: kit, `PanelPaint`, aba Redes). **Mockup:** `docs/preview/tablet-do-zero/maquina.png` e `maquina-face.png` (e `-escuro`), gerados por `docs/preview/tablet-do-zero/gerar.py`, **a fonte das medidas** (funções `lista_maquinas`, `cartao`, `faces`, `seta_modo`, `maquina_detalhe`; resumidas em "Medidas do mockup").

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; todo texto de tela em `en_us.json` **e** `pt_br.json`, prefixo `gui.wirelessautomate.tablet.machine.`.
- **Leia antes de cada tarefa** `.claude/skills/wa-performance/SKILL.md`, `.claude/skills/wa-modular/SKILL.md` e `.claude/skills/wa-comentarios/SKILL.md`.
- **Performance (leis da `wa-performance` que esta etapa toca):** parado custa zero (tela fechada, ou outra aba aberta: o veredito não roda nem é enviado); o veredito é de **uma** máquina, 1 por segundo, e só vai quando muda; o `VerdictProbe` lê campos que o motor já mantém (`Port`, `Backoff`), nunca `level.getCapability`, e uma máquina remota só é consultada com `level.isLoaded(pos)` antes; por quadro, só desenhar: linhas, textos, larguras e `ItemStack`s montados quando chega a página, o veredito ou muda o tema, nunca no `render`; caminho quente sem alocação.
- **Tamanho (`wa-modular`):** tela, componente e widget em `client/` até 500 linhas (aviso 600, dividir 800); servidor até 600; método até 60; a anfitriã `MachinePanel` até ~335. Componente não manda payload: as ações saem pela anfitriã (`MachineActions`, sobre o `TabletActions`). **O `TabletMenu` (817 linhas, acima do aviso) não cresce**: o código novo vai em classes próprias, e a Tarefa 1 o encolhe. O `TabletSnapshot` (698) perde o codec do cabeçalho na Tarefa 1.
- **Cores só pelo `GuiPaint`** na hora de desenhar; erro `bad` = `GuiPaint.DANGER`, aviso `warn` = `GuiPaint.WARN`, "andando" `ok` = `GuiPaint.OK`; o `acc` (`GuiPaint.ACCENT`/`PRIMARY`) só na aba ativa e no botão primário (aqui: o botão de ação do cartão); a linha escolhida com a faixa de 2 px em `FG`; os modos pela cor do mockup (`GuiPaint.modeColor`, ver Tarefa 6); texto variável pelo `GuiText`; tipos pelo `LoadedTypes.LIST`; cliente só em `client/`.
- **Mekanism, Ars Nouveau e FTB Teams são opcionais:** nada de tipos deles aqui; o veredito trabalha com `ResourceType` e `UUID`.
- **Protocolo:** `ModPayloads.VERSION` sobe de `"47"` para `"48"` na Tarefa 7, e só nela (ver "Decisão sobre o protocolo").
- **GameTests:** classe com `@GameTestHolder(WirelessAutomate.MODID)` e `@PrefixGameTestTemplate(false)`, template `"empty"`, `timeoutTicks` no máximo 300; cada teste cria as próprias redes (`NetworkSavedData.get(server).create(...)`) e confere pertinência, nunca contagens totais; compare valores que podem ser nulos com `Objects.equals` (o `assertValueEqual` com nulo derruba o servidor de GameTest); o `onLoad` de um block entity recém-colocado só roda no tick seguinte (`startSequence().thenWaitUntil(...)`); jogador de teste só pelo `TestPlayers.inLevel`. Arquivo novo para os testes novos (o `TabletGameTests` tem 1280 linhas).
- **Verificação antes de cada commit** (Git Bash, na raiz; um Gradle ou jogo por vez, ~3 GB cada):
  ```bash
  ./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams
  python -I .claude/skills/wa-performance/scripts/varrer_perf.py   # candidatos resolvidos ou justificados no relatório
  python -I .claude/skills/wa-modular/scripts/medir.py             # nada acima de "Dividir"; TabletMenu não cresce
  python -I .claude/skills/wa-comentarios/scripts/varrer.py        # sem código comentado, histórico, TODO, {@link} quebrado
  ```
  Nas tarefas de tela, também o e2e: `./scripts/e2e.sh --only <suíte> --capture <prefixo>` (ver `docs/e2e.md`); `run/e2e/result.txt` precisa começar por `OK`, e as capturas novas são olhadas (texto cortado, cor errada). Compile antes (`./gradlew compileJava`): com `BUILD FAILED`, pare e conserte, nunca espere o `result.txt`.
- **Commits:** sem linhas de atribuição (`Co-Authored-By`, `Claude-Session`), autor do dono, sem push.
- Não mexa em `run/options.txt`.

## Decisão sobre o protocolo

`ModPayloads.VERSION` vai para **`"48"`** (Tarefa 7). Muda o formato de três coisas: o `TabletOpenNodePayload` (ganha o alvo, o tipo e a face, opcionais), o buffer de abertura do `RouterMenu` (ganha, depois do snapshot, o tipo e a face iniciais, opcionais) e o conjunto de canais (o `TabletVerdictPayload`, da Tarefa 3, é um canal novo). Um cliente 47 num servidor 48 leria o buffer de abertura torto; a versão nova faz o NeoForge recusar a conexão com a mensagem certa. A Tarefa 3 já registra o canal novo, mas o número sobe uma vez só, na Tarefa 7, que fecha o formato (as duas entram no mesmo ramo, sem release no meio). A linha do javadoc da `VERSION` ganha: `48: o veredito da máquina (TabletVerdictPayload) e o alvo, o tipo e a face no TabletOpenNodePayload e no buffer de abertura do RouterMenu`. O estado de aba do servidor (`State.tab`) não muda de formato, então a decisão 10 não pesa no protocolo.

## Ajustes em relação à spec (o código contraria ou não cobre)

1. **`Port.full` não serve ao veredito:** só o `ItemTransfer` o grava (`network/ItemTransfer.java:241,256`, apagado em `NodePorts.java:453`), então valeria só para itens. O "cheio" que o Tablet já usa para todos os tipos é `NodeProbe.isFull` (`network/NodeProbe.java:103`: o destino dorme com intervalo ≥ 8, `FULL_INTERVAL`). O `VerdictProbe` usa o mesmo critério, não o `Port.full`.
2. **A face aberta pelo "Filtro e mais" depende de mais que o `EditTargetRef`.** A spec (decisão 6) diz que o `RemoteRouterMenu.open(player, router, EditTargetRef)` "já existe", e existe (`menu/RemoteRouterMenu.java:49`), mas ele abre sempre no tipo `ITEM` (`menu/RouterMenu.java:100`) e na face da presa ou `NORTH` (`RouterMenu.java:153` e `:434`), e o `EditTargetRef` não tem `STREAM_CODEC` (`menu/EditTargetRef.java:24`). O alvo vai então como o `SelectTargetPayload` o leva (tipo do alvo, índice, posição, bloco) e o tipo e a face iniciais vão no buffer de abertura, lidos pelo cliente no construtor do `RouterMenu` (`RouterMenu.java:140`, `RouterSnapshot.STREAM_CODEC.decode(buf)`) e usados pela `RouterScreen` (`client/router/RouterScreen.java:98`, que hoje só lê `menu.selectedFace()`).
3. **Decisão 10, em boa parte já existe.** O servidor já guarda a aba por jogador ao fechar (`menu/TabletMenu.java:272`, `State.tab`), restaura (`:233`) e o `TabletScreen` a lê do buffer (`client/tablet/TabletScreen.java:96-98`; só o Mapa vira Redes); o clique com o Tablet já abre com a aba `LIST_TAB` (`TabletMenu.java:332`, que é a aba Máquina) e o nó escolhido (`menu/TabletClick.java:80`). Falta: a aba Máquina escolher a máquina pelo alvo do painel (não pelo nó da Lista antiga) e uma aba lembrada só na sessão (o `TabletPlayers` já limpa ao parar o servidor). A primeira abertura continua na aba Máquina (a posição 0, como hoje); a spec não diz outra.
4. **O mockup não tem seletor de tipo.** Uma máquina pode ter faces em vários tipos (`RouterSnapshot.FaceView` é por tipo e face, `menu/RouterSnapshot.java:231,273`). A lista de faces mostra **uma linha por (tipo, face) em uso**, ordenadas por tipo (`LoadedTypes.LIST`) e depois por face; o tipo aparece em `muted` depois do lado ("Cima · Fluidos") só quando a máquina tem faces em mais de um tipo. Com um tipo só, fica igual ao mockup.
5. **O texto do filtro do mockup ("Filtro de Baixo: só ferro sai") exige um resumo do filtro que não existe.** O problema sai como "Filtro em Baixo" (com "Editar"), e o limite do motor (`Port.waitsDestination == false` vale para "vazia" e para "o filtro barrou", `network/Port.java:89`) fica dito no detalhe: "Nada para mandar".
6. **"Ver destinos" (ação do cartão) não está definida na spec.** O plano a faz acender no mundo os destinos cheios (até 8, as posições vão no veredito; reaproveita o `HighlightRenderer.toggle`). **Ponto para o dono confirmar** (a alternativa é levar à aba Fluxos, que só chega na etapa 3).
7. **O código novo da Lista antiga não tem lugar na aba Máquina.** Selecionar, colar nos marcados, árvore, Grade e o resto saem do Tablet só na etapa 4, mas a aba Máquina nova passa a ocupar o lugar da Lista. A `TabletList` fica viva (e o `TabletScreen` a mostra no lugar do painel novo) **só** por um gancho de desenvolvimento (`TabletScreen.legacyList(boolean)`, como o Mapa tem o `previewTab`) para os e2e antigos de árvore, marcas e máquinas, até a etapa 4 apagar tudo.
8. **O `TabletMenu` tem 817 linhas** (o `CLAUDE.md` diz cerca de 803), e o aviso do `wa-modular` é 800.

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `menu/TabletHeaderCodec.java` (novo), `menu/TabletSnapshot.java` (mudar) | O codec do cabeçalho (`encodeHeader`/`decodeHeader`) sai do snapshot |
| `menu/TabletWatches.java` (novo), `menu/TabletMenu.java` (mudar) | Os vigias do servidor por aba (Mapa e o veredito) numa classe só; o `TabletMenu` encolhe |
| `network/Verdict.java`, `VerdictFacts.java`, `VerdictRules.java` (novos, puros) | O veredito, os fatos e a regra que os transforma |
| `network/VerdictProbe.java` (novo) | Lê os fatos das portas da máquina (package-private do `Port`) |
| `menu/TabletVerdict.java` (novo), `packet/TabletVerdictPayload.java` (novo), `packet/ModPayloads.java`, `packet/TabletPayloads.java` (mudar) | O vigia do veredito, o canal e a entrega ao cliente |
| `client/tablet/machine/` (novo pacote) | `MachinePanel` (anfitriã), `MachineList`, `MachineListRows` (pura), `MachineHeader`, `VerdictCard`, `VerdictText` (pura), `FaceList`, `FaceLayout` (pura), `FaceRow`, `NetworkDropdown`, `MachineSelect`, `MachineActions`, `MachineProbe` |
| `client/PanelPaint.java` (mudar) | `modeArrow`, `errorMark`, a faixa do cartão |
| `client/tablet/TabletScreen.java`, `TabletBody.java`, `TabletTabs.java`, `TabletKeys.java`, `TabletTips.java`, `TabletActions.java` (mudar) | A aba LIST passa a ser a Máquina nova; `legacyList`; o clique dos Fluxos |
| `menu/TabletMenuActions.java`, `packet/TabletOpenNodePayload.java`, `menu/RemoteRouterMenu.java`, `menu/RouterMenu.java`, `client/router/RouterScreen.java` (mudar) | Abrir o roteador no alvo, tipo e face |
| `gametest/TabletVerdictGameTests.java`, `gametest/TabletOpenAtGameTests.java` (novos) | O veredito pelo caminho real; o abrir em face |
| `src/test/.../network/VerdictRulesTest.java`, `client/tablet/machine/MachineListRowsTest.java`, `FaceLayoutTest.java`, `VerdictTextTest.java` (novos) | JUnit da lógica pura |
| `client/dev/E2eTabletMachineSteps.java` (novo), `E2eTabletSteps.java`, `E2eTabletMachinesSteps.java`, `E2eTabletTreeSteps.java`, `E2eTabletMarkSteps.java`, `E2eFlowsSteps.java`, `E2eShowcase.java`, `ScreenshotTablet.java` (mudar) | e2e e capturas da aba nova; os antigos passam pelo `legacyList` |
| `lang/en_us.json`, `lang/pt_br.json` (mudar) | Textos novos |
| `scripts/guide/paginas/network_tablet.py` (mudar), `docs/progresso/*`, `docs/mapa/tablet.md`, `motor.md`, `cliente-e-pacotes.md` (mudar) | Documentação |

Pacote base de todos os caminhos Java: `src/main/java/io/github/matheusanbs/wirelessautomate/` (testes: `src/test/java/io/github/matheusanbs/wirelessautomate/`).

## Medidas do mockup (referência para as Tarefas 4 a 8)

Constantes de `gerar.py`: `W, H = 420, 280`; `X0 = 158`; `X1 = 411` (borda direita, inclusive); `BTN_FIM = 403` (onde terminam os botões e links dentro do cartão). Com a tela redimensionada: `X1 = w - 9`, `BTN_FIM = w - 17`, o rodapé em `h - 21`. Tudo em pixels de GUI relativos ao canto do painel (`layout.left`, `layout.top`); "direita em N" é `GuiPaint.textRight(..., N + 1, ...)`; negrito é `ChatFormatting.BOLD`.

| Peça (função do `gerar.py`) | Medidas |
| --- | --- |
| Coluna esquerda (`coluna_esquerda`, `lista_maquinas`) | divisória `inset` em (150, 24, 1, H − 26); campo "Buscar máquina" (`campo`) em (8, 30, 135, 16), texto `muted` em (13, 34); linhas a partir de y = 52, **passo 24**: ícone 16 em (8, y + 3), nome `fg` em (28, y + 3), estado em (28, y + 12) em `bad`, `warn` ou `muted`; a escolhida: `hi` em (2, y, 148, 22) e faixa `fg` de 2 px em (2, y, 2, 22) |
| Estados na lista (`MAQUINAS`) | texto: "destinos cheios" `bad`, "sem rede" `bad`, "cheia" `warn`, "descarregada" `muted`, a vazão ("96 itens/s", "60 FE/t") `muted` |
| Cabeçalho (`cabecalho`) | ícone 16 em (X0, 30); título negrito em (X0 + 21, 30); subtítulo `muted` em (X0 + 21, 40) ("Roteador Elite · a 14 blocos"); linha `inset` em (X0, 53, X1 − X0 + 1, 1) (o `PanelHeader` da etapa 1) |
| Cartão (`cartao`) | começa em y = 59; altura `30 + 13 × problemas`; fundo `hi` em (X0, y, X1 − X0 + 1, h); faixa de 2 px na cor do estado em (X0, y, 2, h); título negrito `fg` em (X0 + 8, y + 5); detalhe `muted` em (X0 + 8, y + 16); botão primário (largura = texto + 16, altura 16) em (BTN_FIM − w, y + 7); problemas a partir de py = y + 30, passo 13: régua `inset` em (X0 + 8, py − 3, X1 − X0 − 15, 1), marca de erro 5 × 5 em (X0 + 8, py + 1), texto `fg` em (X0 + 19, py + 1), link `fg` com sublinhado à direita, terminando em BTN_FIM, em y = py + 1 |
| Marca de erro (`marca_erro`) | um "x" 5 × 5: para i em 0..4, `fill` (x + i, y + i, 2, 1) e (x + 4 − i, y + i, 2, 1) na cor do problema |
| Faces (`faces`) | "Faces" `muted` em (X0 + 4, y + 4) com y = fim do cartão + 8; as linhas a partir de y + 14, **passo 18**: lado `fg` em (X0 + 4, y + 4), seta do modo em (X0 + 50, y + 4), nome do modo `fg` em (X0 + 60, y + 4), quadradinho da rede (7) em (X0 + 132, y + 4) e o nome `fg` em (X0 + 143, y + 4) (ou "sem rede" `bad` em (X0 + 132, y + 4)), chevron em (X1 − 9, y + 4) (aberta: y + 6); "N faces sem uso" `muted` em (X0 + 4, y + 3) |
| Seta do modo (`seta_modo`) | na cor do modo (extrai `0x2F6FD6`, insere `0xE8742B`, armazém `0x2F9D5C`): haste 1 × 7 em (px + 2, y); cabeça: para i em 0..2, 2i + 1 de largura em (px + 2 − i, yy), com yy = y + 6 − i (para baixo, entra na máquina) ou y + i (para cima, sai); Armazém: as duas setas, a de cima em px = x − 2 e a de baixo em x + 3 |
| Face aberta (`faces`, `editar`) | fundo `hi` em (X0, y − 1, X1 − X0 + 1, 18 + 44); o chevron em y + 6; abaixo da linha (y += 18): segmentos Extrai, Insere, Armazém, Nenhum em (X0 + 4, y) (`Segments` do kit, altura 18); seletor de rede (relevo `face`) em (X0 + 4, y + 21, 124, 18), com borda `bad` de 1 px por fora quando sem rede; quadradinho em (X0 + 9, y + 26) e nome em (X0 + 20, y + 26), ou "Escolher rede" `fg` em (X0 + 9, y + 26); seta do seletor (`PanelPaint.caret`) em (X0 + 116, y + 29); link "Filtro e mais" terminando em X1 − 4, em y + 26; a face aberta ocupa 18 + 44 |
| Rodapé (`rodape`, `maquina_detalhe`) | régua `inset` em (151, H − 27, W − 153, 1); "Mostrar no mundo" em (X0, H − 21), "Abrir roteador" 4 px depois; botões secundários de 16 de altura, largura = texto + 16 |

---

### Task 1: Folga nas classes grandes

**Files:**
- Create: `menu/TabletHeaderCodec.java`, `menu/TabletWatches.java`
- Modify: `menu/TabletSnapshot.java` (tira `encodeHeader`/`decodeHeader`, `:521-675`), `menu/TabletMenu.java` (o `mapWatch`, `:166,189-192,264-267,295-298,564-567`)

**Interfaces:**
- Produces: `TabletHeaderCodec.CODEC` (o `StreamCodec<RegistryFriendlyByteBuf, TabletSnapshot>` de hoje, com o mesmo formato byte a byte); `TabletSnapshot.HEADER_CODEC` continua existindo e aponta para ele; `TabletWatches` com `tab(int)`, `stop()`, `poll(List<CustomPacketPayload>)` e, por ora, só o vigia do Mapa.

**Custo:** nenhum; refatoração sem mudar formato nem comportamento.

- [ ] **Step 1: O codec do cabeçalho**

Mova `encodeHeader` e `decodeHeader` (e os helpers privados que só eles usam) do `TabletSnapshot` para `TabletHeaderCodec` (`final class`, pacote `menu`, `static final StreamCodec<RegistryFriendlyByteBuf, TabletSnapshot> CODEC = StreamCodec.of(TabletHeaderCodec::encode, TabletHeaderCodec::decode)`). Os campos do record já são públicos pelos acessores; os que o codec lê por `s.operator` passam a `s.operator()`. No `TabletSnapshot`, deixe `public static final StreamCodec<...> HEADER_CODEC = TabletHeaderCodec.CODEC;` e o javadoc da classe apontando para o `TabletHeaderCodec`.

- [ ] **Step 2: Os vigias**

Crie `TabletWatches` (pacote `menu`, `final class`) que guarda o `TabletMap` e (na Tarefa 3) o `TabletVerdict`:

```java
/** Os vigias do servidor que só rodam com uma aba aberta: o Mapa e o veredito da máquina. */
final class TabletWatches {
    private final TabletMap map;

    TabletWatches(ServerPlayer viewer, int containerId) {
        this.map = new TabletMap(viewer, containerId);
    }

    /** A tela mostra a aba {@code tab}. */
    void tab(int tab) {
        map.tab(tab);
    }

    /** Fechou o Tablet. */
    void stop() {
        map.stop();
    }

    /** Os pacotes novos dos vigias, se mudaram. */
    void poll(List<CustomPacketPayload> out) {
        TabletMapPayload mapNow = map.poll();
        if (mapNow != null) {
            out.add(mapNow);
        }
    }
}
```

No `TabletMenu`, troque o campo `mapWatch` por `private final @Nullable TabletWatches watches;` (nulo no cliente, como o `mapWatch`), as chamadas `mapWatch.tab/stop/poll` pelas de `watches`, e apague o bloco do `poll` do mapa nos `:564-567`.

- [ ] **Step 3: Provar que nada mudou**

Run: `./gradlew build runGameTestServer` (o `TabletMapGameTests` e o `TabletGameTests` cobrem o codec e o Mapa pelo caminho real).
Run: `python -I .claude/skills/wa-modular/scripts/medir.py`
Expected: BUILD SUCCESSFUL; `TabletMenu` abaixo de 800 e `TabletSnapshot` abaixo de 600 linhas.

- [ ] **Step 4: Commit**

`Tablet do zero, etapa 2, 1/9: o codec do cabeçalho e os vigias saem do TabletSnapshot e do TabletMenu`

---

### Task 2: A lógica pura do veredito

**Files:**
- Create: `network/Verdict.java`, `network/VerdictFacts.java`, `network/VerdictRules.java`
- Test: `src/test/java/io/github/matheusanbs/wirelessautomate/network/VerdictRulesTest.java`

**Interfaces:**
- Produces: `VerdictFacts(Reach reach, List<FaceFact> faces)`; `FaceFact(ResourceType type, Direction face, boolean source, boolean destination, @Nullable UUID network, boolean groupPaused, boolean stopped, boolean moving, boolean sourceAsleep, boolean waitsDestination, int fullDestinations, List<Long> fullPositions, boolean selfFull, boolean filtered)`; `Verdict(Reason reason, ResourceType type, Direction face, @Nullable UUID network, int count, Action action, List<Problem> problems, List<Long> positions)` com `Verdict.Reason`, `Verdict.Tone`, `Verdict.Action`, `Verdict.Problem` e `Verdict.STREAM_CODEC`; `VerdictRules.decide(VerdictFacts)`.

**Custo:** nenhum no motor; uma conta por segundo sobre no máximo 6 faces × tipos, sem alocar além do resultado.

A regra (decisão 7 da spec, na ordem em que vale; **a primeira que bate decide o motivo**):

1. `reach` diferente de `OK`: o motivo é o do alcance (`UNLOADED` em `muted`; `NO_UPGRADE`, `OUT_OF_RANGE`, `OVER_LIMIT`, `REPLACED` e `MISSING` em `bad`, as palavras que a lista antiga já usa).
2. Nenhuma face em uso (nem fonte nem destino): `NOT_IN_USE`, `muted`.
3. Toda face em uso sem rede (`network == null`): `NO_NETWORK`, `bad`; o detalhe diz a primeira (tipo e face) e a ação é `CHOOSE_NETWORK`.
4. Toda face em uso com rede está em grupo pausado: `PAUSED`, `muted`; ação `RESUME` (o cliente acha o grupo pela rede).
5. Toda face viva (com rede, sem pausa) parada por redstone: `REDSTONE`, `warn`.
6. Alguma face viva e sem redstone moveu agora: `MOVING`, `ok`.
7. Uma fonte dormindo que espera destino e tem destinos cheios: `DESTINATIONS_FULL`, `bad`; `count` = quantos, `network`, ação `SEE_DESTINATIONS` com as posições (até 8).
8. Só destinos (ou fontes sem o caso 7) e a própria face cheia: `SELF_FULL`, `warn` ("cheia").
9. Uma fonte dormindo que não espera destino: `NOTHING_TO_SEND`, `muted` (o limite conhecido: o motor não distingue "vazia" de "o filtro barrou").
10. Senão `IDLE`, `muted`.

Os problemas (um por linha, depois do motivo): cada face em uso sem rede quando o motivo não é `NO_NETWORK` (link `CHOOSE_NETWORK`); cada face parada por redstone quando o motivo não é `REDSTONE` (sem link); cada fonte com filtro quando o motivo é `DESTINATIONS_FULL` ou `NOTHING_TO_SEND` (link `OPEN_FILTER`, na face). No máximo 4.

- [ ] **Step 1: Teste que falha**

`VerdictRulesTest`:

```java
package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.network.Verdict.Action;
import io.github.matheusanbs.wirelessautomate.network.Verdict.Reason;
import io.github.matheusanbs.wirelessautomate.network.VerdictFacts.FaceFact;
import io.github.matheusanbs.wirelessautomate.network.VerdictFacts.Reach;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class VerdictRulesTest {
    private static final UUID NET = new UUID(1, 1);

    private static FaceFact.Builder face(Direction d) {
        return FaceFact.builder(ResourceType.ITEM, d);
    }

    private static Verdict decide(Reach reach, FaceFact... faces) {
        return VerdictRules.decide(new VerdictFacts(reach, List.of(faces)));
    }

    @Test
    void unloadedBeatsEverything() {
        Verdict v = decide(Reach.UNLOADED, face(Direction.UP).source().network(NET).build());
        assertEquals(Reason.UNLOADED, v.reason());
    }

    @Test
    void noFaceInUse() {
        assertEquals(Reason.NOT_IN_USE, decide(Reach.OK, face(Direction.UP).build()).reason());
    }

    @Test
    void faceWithoutNetworkSaysNothingLeaves() {
        Verdict v = decide(Reach.OK, face(Direction.UP).source().build(),
                face(Direction.DOWN).destination().network(NET).build());
        // a outra face tem rede: o motivo só é "sem rede" quando todas as faces em uso estão sem
        assertTrue(v.reason() != Reason.NO_NETWORK);
        assertEquals(1, v.problems().size());
        assertEquals(Action.CHOOSE_NETWORK, v.problems().get(0).action());
        assertEquals(Direction.UP, v.problems().get(0).face());
    }

    @Test
    void allFacesWithoutNetwork() {
        Verdict v = decide(Reach.OK, face(Direction.UP).source().build());
        assertEquals(Reason.NO_NETWORK, v.reason());
        assertEquals(Direction.UP, v.face());
        assertEquals(Action.CHOOSE_NETWORK, v.action());
        assertTrue(v.problems().isEmpty());
    }

    @Test
    void noNetworkBeatsFull() {
        Verdict v = decide(Reach.OK, face(Direction.UP).source().asleepFull(2).build());
        assertEquals(Reason.NO_NETWORK, v.reason());
    }

    @Test
    void pausedGroup() {
        Verdict v = decide(Reach.OK, face(Direction.UP).source().network(NET).paused().build());
        assertEquals(Reason.PAUSED, v.reason());
        assertEquals(Action.RESUME, v.action());
        assertEquals(NET, v.network());
    }

    @Test
    void redstoneStopsEveryLiveFace() {
        Verdict v = decide(Reach.OK, face(Direction.UP).source().network(NET).stopped().build());
        assertEquals(Reason.REDSTONE, v.reason());
    }

    @Test
    void redstoneOnOneFaceIsAProblemNotTheReason() {
        Verdict v = decide(Reach.OK, face(Direction.UP).source().network(NET).stopped().build(),
                face(Direction.DOWN).source().network(NET).moving().build());
        assertEquals(Reason.MOVING, v.reason());
        assertEquals(1, v.problems().size());
        assertEquals(Direction.UP, v.problems().get(0).face());
    }

    @Test
    void destinationsFullCountsThemAndCarriesPositions() {
        Verdict v = decide(Reach.OK, face(Direction.UP).source().network(NET).asleepFull(2).build());
        assertEquals(Reason.DESTINATIONS_FULL, v.reason());
        assertEquals(2, v.count());
        assertEquals(NET, v.network());
        assertEquals(Action.SEE_DESTINATIONS, v.action());
        assertEquals(2, v.positions().size());
    }

    @Test
    void filterBecomesAProblemWhenFullOrNothingToSend() {
        Verdict full = decide(Reach.OK, face(Direction.DOWN).source().network(NET).asleepFull(1).filtered().build());
        assertEquals(Reason.DESTINATIONS_FULL, full.reason());
        assertEquals(Action.OPEN_FILTER, full.problems().get(0).action());
        Verdict empty = decide(Reach.OK, face(Direction.DOWN).source().network(NET).asleepEmpty().filtered().build());
        assertEquals(Reason.NOTHING_TO_SEND, empty.reason());
        assertEquals(Action.OPEN_FILTER, empty.problems().get(0).action());
    }

    @Test
    void nothingToSendWithoutFilterHasNoProblem() {
        Verdict v = decide(Reach.OK, face(Direction.DOWN).source().network(NET).asleepEmpty().build());
        assertEquals(Reason.NOTHING_TO_SEND, v.reason());
        assertTrue(v.problems().isEmpty());
    }

    @Test
    void movingWinsOverASleepingOtherFace() {
        Verdict v = decide(Reach.OK, face(Direction.UP).source().network(NET).moving().build(),
                face(Direction.DOWN).source().network(NET).asleepFull(1).build());
        assertEquals(Reason.MOVING, v.reason());
    }

    @Test
    void receiverThatIsFullIsWarnNotBad() {
        Verdict v = decide(Reach.OK, face(Direction.UP).destination().network(NET).selfFull().build());
        assertEquals(Reason.SELF_FULL, v.reason());
        assertEquals(Verdict.Tone.WARN, v.reason().tone());
    }

    @Test
    void idleWhenNothingElseApplies() {
        Verdict v = decide(Reach.OK, face(Direction.UP).destination().network(NET).build());
        assertEquals(Reason.IDLE, v.reason());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*VerdictRulesTest'`
Expected: FAIL (compilação: `Verdict`, `VerdictFacts` e `VerdictRules` não existem).

- [ ] **Step 3: Os tipos**

`VerdictFacts` (puro, nada do Minecraft além de `Direction` e `ResourceType`, que já são usados em lógica pura testada):

```java
package io.github.matheusanbs.wirelessautomate.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/** O que o motor sabe de uma máquina num instante: o alcance dela e o estado de cada face em uso (um por tipo e face). */
public record VerdictFacts(Reach reach, List<FaceFact> faces) {
    /** Se a máquina está ao alcance do roteador e carregada; o resto diz por que não. */
    public enum Reach { OK, UNLOADED, NO_UPGRADE, OUT_OF_RANGE, OVER_LIMIT, REPLACED, MISSING }

    /**
     * Uma face em uso ({@code source} ou {@code destination}).
     *
     * @param network          a rede da face, {@code null} = sem rede
     * @param sourceAsleep     a origem dorme (nada a mover agora)
     * @param waitsDestination o sono é por destinos que recusam ({@code Port.waitsDestination})
     * @param fullDestinations quantos destinos dela estão cheios ({@code NodeProbe.isFull}); {@code fullPositions} são as
     *                         posições das máquinas deles, até 8
     * @param selfFull         a própria face, como destino, está cheia
     * @param filtered         a face tem filtro (o embutido ou cartões)
     */
    public record FaceFact(ResourceType type, Direction face, boolean source, boolean destination, @Nullable UUID network,
            boolean groupPaused, boolean stopped, boolean moving, boolean sourceAsleep, boolean waitsDestination,
            int fullDestinations, List<Long> fullPositions, boolean selfFull, boolean filtered) {
        public boolean inUse() {
            return source || destination;
        }

        public static Builder builder(ResourceType type, Direction face) {
            return new Builder(type, face);
        }

        /** Monta um fato (usado nos testes e pelo {@code VerdictProbe}). */
        public static final class Builder {
            private final ResourceType type;
            private final Direction face;
            private boolean source;
            private boolean destination;
            private UUID network;
            private boolean paused;
            private boolean stopped;
            private boolean moving;
            private boolean asleep;
            private boolean waits;
            private int full;
            private List<Long> positions = List.of();
            private boolean selfFull;
            private boolean filtered;

            private Builder(ResourceType type, Direction face) {
                this.type = type;
                this.face = face;
            }

            public Builder source() { source = true; return this; }
            public Builder destination() { destination = true; return this; }
            public Builder network(UUID id) { network = id; return this; }
            public Builder paused() { paused = true; return this; }
            public Builder stopped() { stopped = true; return this; }
            public Builder moving() { moving = true; return this; }
            public Builder selfFull() { selfFull = true; return this; }
            public Builder filtered() { filtered = true; return this; }

            /** Dorme porque {@code n} destinos recusam. */
            public Builder asleepFull(int n) {
                asleep = true;
                waits = true;
                full = n;
                List<Long> list = new ArrayList<>(n);
                for (int i = 0; i < Math.min(n, 8); i++) {
                    list.add((long) i);
                }
                positions = list;
                return this;
            }

            /** Dorme sem ter o que dar. */
            public Builder asleepEmpty() {
                asleep = true;
                waits = false;
                return this;
            }

            public FaceFact build() {
                return new FaceFact(type, face, source, destination, network, paused, stopped, moving, asleep, waits,
                        full, List.copyOf(positions), selfFull, filtered);
            }
        }
    }
}
```

Nos testes acima, `source()` sem `network(...)` é a face sem rede. `Verdict` leva os enums e o codec; escreva-o assim:

```java
package io.github.matheusanbs.wirelessautomate.network;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.jetbrains.annotations.Nullable;

/**
 * Por que uma máquina está como está: o motivo principal, a face e a rede a que ele se refere, o número que o detalhe
 * cita, a ação que o resolve e os outros problemas. Sem texto: o cliente monta as frases pelas chaves de
 * {@code gui.wirelessautomate.tablet.machine.verdict.*} (o motor não conhece a língua do jogador).
 *
 * @param count     o número do detalhe (destinos cheios, faces sem rede)
 * @param positions as máquinas cheias de {@link Action#SEE_DESTINATIONS}, até {@link #MAX_POSITIONS}
 */
public record Verdict(Reason reason, ResourceType type, Direction face, @Nullable UUID network, int count, Action action,
        List<Problem> problems, List<Long> positions) {
    public static final int MAX_POSITIONS = 8;
    public static final int MAX_PROBLEMS = 4;

    /** A cor do estado na lista e no cartão. */
    public enum Tone { BAD, WARN, MUTED, OK }

    public enum Reason {
        UNLOADED(Tone.MUTED), NO_UPGRADE(Tone.BAD), OUT_OF_RANGE(Tone.BAD), OVER_LIMIT(Tone.BAD), REPLACED(Tone.BAD),
        MISSING(Tone.BAD), NOT_IN_USE(Tone.MUTED), NO_NETWORK(Tone.BAD), PAUSED(Tone.MUTED), REDSTONE(Tone.WARN),
        MOVING(Tone.OK), DESTINATIONS_FULL(Tone.BAD), SELF_FULL(Tone.WARN), NOTHING_TO_SEND(Tone.MUTED), IDLE(Tone.MUTED);

        private final Tone tone;

        Reason(Tone tone) {
            this.tone = tone;
        }

        public Tone tone() {
            return tone;
        }
    }

    /** O botão do cartão (ou o link do problema). */
    public enum Action { NONE, CHOOSE_NETWORK, SEE_DESTINATIONS, RESUME, OPEN_FILTER }

    /** Um problema além do motivo: o que é, onde (tipo e face) e o link que o resolve. */
    public enum ProblemKind { NO_NETWORK, REDSTONE, FILTER }

    public record Problem(ProblemKind kind, ResourceType type, Direction face, Action action) {
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, Problem> PROBLEM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(ProblemKind.class), Problem::kind,
            NeoForgeStreamCodecs.enumCodec(ResourceType.class), Problem::type,
            Direction.STREAM_CODEC, Problem::face,
            NeoForgeStreamCodecs.enumCodec(Action.class), Problem::action,
            Problem::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, Verdict> STREAM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(Reason.class), Verdict::reason,
            NeoForgeStreamCodecs.enumCodec(ResourceType.class), Verdict::type,
            Direction.STREAM_CODEC, Verdict::face,
            ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC), v -> java.util.Optional.ofNullable(v.network()),
            ByteBufCodecs.VAR_INT, Verdict::count,
            NeoForgeStreamCodecs.enumCodec(Action.class), Verdict::action,
            PROBLEM_CODEC.apply(ByteBufCodecs.list(MAX_PROBLEMS)), Verdict::problems,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(MAX_POSITIONS)), Verdict::positions,
            (reason, type, face, network, count, action, problems, positions) ->
                    new Verdict(reason, type, face, network.orElse(null), count, action, problems, positions));
}
```

(`StreamCodec.composite` aceita até 8 campos: são 8. Se o compilador pedir, confira o nome do codec de `UUID` em uso nos outros payloads do mod e use o mesmo.)

`VerdictRules.decide` implementa a ordem acima; esqueleto:

```java
public final class VerdictRules {
    private VerdictRules() {
    }

    public static Verdict decide(VerdictFacts facts) {
        List<FaceFact> used = facts.faces().stream().filter(FaceFact::inUse).toList();
        FaceFact first = used.isEmpty() ? null : used.get(0);
        ResourceType type = first == null ? ResourceType.ITEM : first.type();
        Direction face = first == null ? Direction.UP : first.face();
        Reason reach = reachReason(facts.reach());
        if (reach != null) {
            return plain(reach, type, face);
        }
        if (used.isEmpty()) {
            return plain(Reason.NOT_IN_USE, type, face);
        }
        List<FaceFact> noNet = used.stream().filter(f -> f.network() == null).toList();
        if (noNet.size() == used.size()) {
            return new Verdict(Reason.NO_NETWORK, first.type(), first.face(), null, noNet.size(), Action.CHOOSE_NETWORK,
                    List.of(), List.of());
        }
        // ... 4 a 10 da lista acima, e o problemas(...) no fim
    }
}
```

Complete 4 a 10 e o cálculo dos problemas (`problems(used, reason)`, no máximo `MAX_PROBLEMS`) seguindo a lista da tarefa; nos testes, o `NO_NETWORK` de uma face só com a outra viva vira problema, e o motivo vem do que sobra (aqui `IDLE`, ou `SELF_FULL`, ou o que as outras faces disserem).

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests '*VerdictRulesTest'`
Expected: PASS (13 testes).

- [ ] **Step 5: Commit**

`Tablet do zero, etapa 2, 2/9: a regra pura do veredito da máquina, com JUnit`

---

### Task 3: O veredito no servidor: leitura das portas, vigia e payload

**Files:**
- Create: `network/VerdictProbe.java`, `menu/TabletVerdict.java`, `packet/TabletVerdictPayload.java`, `gametest/TabletVerdictGameTests.java`
- Modify: `menu/TabletWatches.java`, `menu/TabletMenu.java` (só o `verdictInbox` do cliente e o `pollVerdict` público dos testes), `packet/ModPayloads.java`, `packet/TabletPayloads.java`

**Interfaces:**
- Produces: `VerdictProbe.read(RouterBlockEntity node, @Nullable BlockPos machine, long now): VerdictFacts` (`machine == null` é a presa); `TabletVerdictPayload(int containerId, Optional<NodeKey> node, Optional<BlockPos> machine, Optional<Verdict> verdict)` (`machine` vazio = a presa); `TabletMenu.pollVerdict()` (público, para os GameTests); `TabletMenu.verdict()` no cliente (o último pacote, só vale se bate com o nó e o alvo escolhidos).
- Consumes: `NodeProbe.isFull` (package-private; o `VerdictProbe` está no mesmo pacote `network`), `NodePorts.peek`/`machinePorts`, `NetworkSavedData.isPaused`, `TabletNodes.loadedRouter`, `TabletMachineSample` (o `MachineStatus` de alcance).

**Custo:** com a aba Máquina aberta e uma máquina escolhida, 1 leitura por segundo de até `tipos × 6` portas dessa máquina e a contagem dos destinos cheios dela (itera `Port.order`, até 64 destinos); nada com a tela fechada ou em outra aba; o payload só sai se o veredito mudou.

- [ ] **Step 1: O `VerdictProbe` (em `network/`)**

Para cada tipo de `LoadedTypes.LIST` e cada `Direction`, a porta da presa vem de `ports.peek(type, face)` e a de uma remota de `ports.peekRemote(machine.asLong(), type, face)`. Uma face sem porta (`null`) ou sem modo não entra em `faces`. Preencha o `FaceFact` assim:

- `source`/`destination`: `port.source`/`port.destination` (**não** `port.network != null`: uma face sem rede tem a porta com `faceNetwork == null` e a configuração de modo pela `FaceConfig`; confira onde a `NodePorts` guarda o modo de uma face sem rede e leia de lá, senão o caso "sem rede" nunca aparece);
- `network`: `port.faceNetwork`; `groupPaused`: `data.isPaused(network)` (`NetworkSavedData.java:292`);
- `stopped`: `port.stopped`; `moving`: `port.got > 0 || port.gave > 0` (o mesmo critério do `NodeProbe.activeNow`, `NodeProbe.java:93-95`);
- `sourceAsleep`: `port.source && port.sourceBackoff.isSleeping(now)`; `waitsDestination`: `port.waitsDestination`;
- `fullDestinations`/`fullPositions`: percorra `port.order` (se não for nulo) com o `pass()`... **sem criar lista por segundo além do teto**: use o `layout()` (`RoundRobinOrder.java:205`, `Layout.size()`/`destination(i)`) e conte as `Port` com `countsAsFull(dest, now)`; guarde `dest.machinePos.asLong()` das 8 primeiras;
- `selfFull`: `port.destination && isFull(port, now)`; `filtered`: `!port.filter.isEmpty()`.

O `Reach` vem de fora (o menu o sabe pelo `TabletMachineSample`): o `read` recebe um `Reach` já calculado ou, se o roteador/chunk não está carregado, o chamador devolve `UNLOADED` sem chamar o probe (`level.isLoaded(pos)` antes: **uma máquina remota nunca é consultada sem isso**).

- [ ] **Step 2: O vigia `TabletVerdict`**

Como o `TabletMap`: `tab(int tab)` liga com `tab == TabletMenu.LIST_TAB` (visibilidade de pacote, já existe) e desliga nas outras; `stop()` zera o enviado; `select(@Nullable NodeKey node, EditTargetRef target)` é chamado quando a escolha muda (pela `chooseNode`/`SelectTarget`, ver a Tarefa 4) e força a próxima leitura; `poll()` só roda se ligado, há nó e alvo e passaram `TabletMenu.SAMPLE_TICKS` desde a última; monta `VerdictRules.decide(VerdictProbe.read(...))` e devolve o `TabletVerdictPayload` só se `!equals(sent)`; se o nó sumiu (descarregado), devolve o veredito `UNLOADED`. Registre-o no `TabletWatches` (`tab`, `stop`, `poll`) e exponha `TabletMenu.pollVerdict()` para os GameTests, sem linhas novas além de uma delegação.

- [ ] **Step 3: O payload e a entrega**

`TabletVerdictPayload` no molde do `TabletMapPayload` (`TYPE = new Type<>(WirelessAutomate.id("tablet_verdict"))`, `STREAM_CODEC` com `VAR_INT`, `optional(NodeKey)`, `optional(BlockPos)`, `optional(Verdict.STREAM_CODEC)`), `registrar.playToClient(...)` no `ModPayloads` e o tratador em `TabletPayloads`, que guarda no `TabletMenu` do cliente (um campo `verdictInbox`, o mesmo padrão do `applyMap`; se faltar linha no menu, o tratador guarda numa classe `client/tablet/machine/VerdictInbox` por `containerId`). Não suba o `VERSION` ainda.

- [ ] **Step 4: GameTests pelo caminho real**

`TabletVerdictGameTests` (classe própria, `@GameTestHolder`, `@PrefixGameTestTemplate(false)`, template `"empty"`, um lote só dele): para cada caso, monte um roteador com a máquina presa (fornalha), crie a rede (`NetworkSavedData.get(server).create(...)`), abra o `TabletMenu` do `TestPlayers.inLevel`, escolha o nó (`menu.chooseNode(...)`), `menu.setTab(0)` e chame `menu.pollVerdict()`:

1. **sem rede:** face de baixo extrai sem rede → `Reason.NO_NETWORK`, face `DOWN`, ação `CHOOSE_NETWORK`;
2. **destinos cheios:** uma origem (baú com item) e dois destinos (baús cheios, `fillWithItems`) na mesma rede; `thenWaitUntil` o `NodeProbe.fullDestinations(router, ITEM, now) == 2` e então o veredito é `DESTINATIONS_FULL` com `count() == 2` e `positions().size() == 2` (cuidado com a espera: o `FULL_INTERVAL` é 8 ticks de recusas);
3. **redstone:** a face em `RedstoneMode` que a desliga com sinal, `level.setBlock` de uma tocha de redstone ao lado → `REDSTONE`;
4. **grupo pausado:** a rede da face num grupo, `data.setGroupPaused(...)` → `PAUSED` com a `network()` certa;
5. **só quando muda:** duas chamadas seguidas do `pollVerdict()` no mesmo tick: a primeira devolve o pacote, a segunda `null`; depois de `SAMPLE_TICKS` sem mudança, `null` também;
6. **fora da aba:** com `menu.setTab(1)` (Fluxos) o `pollVerdict()` devolve `null` e nada roda; voltando a 0, devolve de novo;
7. **máquina vinculada (remota):** o mesmo de (1) com o Upgrade de Alcance e uma máquina remota, a `machine()` do pacote igual à posição dela, e com o chunk descarregado (`forceload` removido, `level.isLoaded` falso) → `UNLOADED` sem consultar a máquina.

Compare com `Objects.equals` quando o valor puder ser nulo.

- [ ] **Step 5: Verificar e commitar**

Run: `./gradlew build runGameTestServer` e as outras quatro runs da lista global; o `varrer_perf.py` (o `VerdictProbe` e o vigia não podem alocar por quadro nem por tick).
Commit: `Tablet do zero, etapa 2, 3/9: o veredito da máquina no servidor (leitura das portas, vigia de 1 s e payload)`

---

### Task 4: A aba Máquina: a lista, o cabeçalho e a escolha

**Files:**
- Create (todos em `client/tablet/machine/`): `MachinePanel.java`, `MachineList.java`, `MachineListRows.java` (pura), `MachineHeader.java`, `MachineSelect.java`, `MachineActions.java`, `MachineProbe.java`
- Modify: `client/tablet/TabletScreen.java`, `TabletBody.java`, `TabletTabs.java`, `TabletKeys.java`, `TabletTips.java`, `TabletActions.java`; `lang/en_us.json`, `lang/pt_br.json`
- Test: `src/test/java/.../client/tablet/machine/MachineListRowsTest.java`

**Interfaces:**
- Produces: `MachinePanel(TabletLayout layout, TabletMenu menu, Supplier<TabletSnapshot> snapshot, TabletActions actions)` com `render(g, font, mx, my)`, `mouseClicked/mouseScrolled/keyPressed/charTyped`, `tooltip(mx, my)`, `probe()`; `MachineListRows.of(TabletMachineRows page, ...)` → `List<Entry>` achatado (`Entry(router NodeKey, boolean attached, int index, BlockPos pos, ResourceLocation block, String label, MachineStatus status, long rate, ...)`); `TabletScreen.legacyList(boolean)` (gancho de desenvolvimento).
- Consumes: o `TabletMachineRows` e o `TabletMachines` (a página de máquinas, a cada 20 ticks e só se mudou), o `TabletMachinesPayload` (a visão Máquinas e o "Mostrar todas"), o `TabletSelectNodePayload` e o `SelectTargetPayload` (`MachineNav.java:27` os usa), `PanelHeader` e o `PanelPaint` da etapa 1.

**Custo:** nenhum servidor novo (a página já existe e já só sai com a aba LIST aberta, `TabletMenu.java:193`); o cliente monta as entradas e os textos quando chega a página (não por quadro); a busca reusa a consulta de hoje (`ListSearch`, texto com atraso, sem etiquetas na UI nova).

A aba LIST (posição 0 no servidor, a que já constrói a página de máquinas) passa a mostrar o `MachinePanel`. A `TabletList` antiga continua construída (as Fluxos e o Mapa a usam) e só é mostrada pelo `legacyList(true)`.

- [ ] **Step 1: Teste que falha (achatar a página)**

`MachineListRowsTest` (puro: monte `TabletMachineRows` à mão, como o `TabletMachinesGameTests` faz, sem `Minecraft`): os cabeçalhos de bloco (`Block`) saem da lista, as linhas `Machine` ficam na ordem da página com o bloco do cabeçalho anterior, o `More(block, hidden)` vira uma entrada `More` ("… mais N", que dispara o "Mostrar todas" do bloco) e a presa (`attached`) sem rótulo usa o nome do bloco. O texto de estado de cada entrada vem do `MachineStatus` e da vazão (`MachineLive`): `FULL` → "cheia" `warn`; `NO_NETWORK` → "sem rede" `bad`; `UNLOADED` → "descarregada" `muted`; `NO_UPGRADE`, `OUT_OF_RANGE`, `OVER_LIMIT`, `REPLACED`, `MISSING` → `bad`, com a palavra que a lista antiga usa (`MachineLines`/`MachineRowTexts`); `PAUSED` → "pausada" `muted`; `MOVING` → a vazão ("96 itens/s") `muted`, do tipo com mais vazão; `SLEEPING` → "parada" `muted`.

Run: `./gradlew test --tests '*MachineListRowsTest'` → FAIL (a classe não existe).

- [ ] **Step 2: `MachineListRows`** (pura, com o código que faz o teste passar) e rodar de novo: PASS.

- [ ] **Step 3: `MachineList` (a coluna esquerda)**

Medidas da tabela "Coluna esquerda": divisória, campo "Buscar máquina" (um `EditBox` sem borda própria sobre o `PanelPaint.field`, o mesmo do `NetworkSearch`/`TitleEditor` da etapa 1; o foco desenha a borda `FG` de 1 px), linhas de 24 px a partir de y = 52 com a faixa `FG` de 2 px na escolhida (`PanelPaint.chosenRow`), ícone 16 do bloco (`ItemStack` criado quando chega a página, do `block` da linha: `BuiltInRegistries.ITEM.get(block)`; as que não têm item usam o `Items.BARRIER`... confira como a Lista antiga resolve o ícone no `MachineRows` e reuse), nome e estado. A rolagem é pela roda e por uma barra fina (como a `NetworkColumn`), a linha escolhida visível ao escolher. Clique numa linha: `MachineSelect.choose(entry)`. O estado das entradas só é refeito quando a página ou o tema muda (cache; o `LineCache` da Lista antiga serve de modelo).

- [ ] **Step 4: `MachineSelect` e a escolha vinda do servidor**

`choose(entry)` manda o `TabletSelectNodePayload` (nó) e depois o `SelectTargetPayload` (presa ou máquina), pelo `TabletActions`, como o `TreeNav` e o `MachineNav` fazem. O que está escolhido vem do servidor: o `menu.panel()` (`TabletPanelPayload`: o nó e o `RouterSnapshot.target()`): a linha escolhida da lista é a que tem o mesmo roteador e o mesmo alvo (presa, ou a posição da máquina). Assim o clique com o Tablet numa máquina (`TabletClick.open`, que já abre com o nó e o alvo e a aba LIST) abre a aba Máquina com ela escolhida sem código novo no servidor, e a lista rola até ela na primeira página que a traz.

- [ ] **Step 5: `MachineHeader` e a anfitriã `MachinePanel`**

O cabeçalho é o `PanelHeader` da etapa 1: ícone do bloco em (X0, 30), nome (rótulo, ou o nome da máquina: `RouterBlock.machineName` só no servidor; o cliente usa o nome do bloco do item) e o subtítulo "Roteador <tier> · a N blocos" (o tier de `RouterRef.tier()`, a distância da posição da máquina ao jogador, `Math.round(sqrt(distSqr))`, **sem alocar por quadro**: recalculada a cada página/amostra, e "noutra dimensão" quando a dimensão do nó difere da do jogador). Sem máquina escolhida: texto `muted` centrado "Escolha uma máquina". O `MachinePanel` só monta e repassa (a lista, o cabeçalho; o cartão e as faces entram nas Tarefas 5 e 6), até ~335 linhas.

- [ ] **Step 6: A moldura e a decisão 10**

No `TabletScreen`: `Tab.LIST` mostra o `MachinePanel` (e `legacyList(true)` mostra a `TabletList`); o `TabletBody`, o `TabletTabs`, o `TabletKeys` e o `TabletTips` repassam ao painel novo; o clique dos Fluxos (`TabletScreen.java:109-112`) só troca para a aba, sem o `list.filterFlow` (a etapa 3 refaz o clique); a aba lembrada vem do buffer como hoje, e o `list.showMachines()` do construtor passa a valer só para a `TabletList` (o servidor continua montando a página de máquinas pela visão Máquinas, então o `MachinePanel` manda o `TabletMachinesPayload` da visão Máquinas ao abrir, como o `showMachines` faz). A aba é lembrada pelo estado que o servidor já guarda ao fechar (`TabletMenu.java:272`); confira no e2e que reabrir o Tablet em Fluxos abre em Fluxos e que o clique com o Tablet numa máquina abre em Máquina com ela escolhida (`TabletClickGameTests` já cobre o servidor, `:77,99,138`).

- [ ] **Step 7: Textos, build, e2e rápido e commit**

Textos em `en_us.json` e `pt_br.json` (`gui.wirelessautomate.tablet.machine.search`, `.empty`, `.state.*`, `.more`, `.header.distance`...). Rode as verificações globais e `./scripts/e2e.sh --only tablet --capture maquina` (os passos antigos que usam a Lista passam a chamar `tabletScreen().legacyList(true)` no começo; ver a Tarefa 8). Commit: `Tablet do zero, etapa 2, 4/9: a aba Máquina com a lista, o cabeçalho e a escolha`

---

### Task 5: O cartão do veredito e "Mostrar no mundo"

**Files:**
- Create: `client/tablet/machine/VerdictCard.java`, `VerdictText.java` (pura)
- Modify: `client/PanelPaint.java` (`errorMark`, o cartão), `client/tablet/machine/MachinePanel.java`, `MachineActions.java`, `MachineProbe.java`, `lang/*.json`
- Test: `src/test/java/.../client/tablet/machine/VerdictTextTest.java`

**Interfaces:**
- Produces: `VerdictText.of(Verdict, NameLookup names): Lines` com `Lines(String titleKey, Object[] titleArgs, String detailKey, Object[] detailArgs, String actionKey, List<ProblemLine> problems)` (só chaves e argumentos, sem `Component`: JUnit puro); `VerdictCard.render(...)`, `mouseClicked(...)`.
- Consumes: o `TabletVerdictPayload` (a última que bate com o nó e o alvo escolhidos), `HighlightRenderer.toggle` (client/highlight).

**Custo:** o cartão é remontado só quando chega um veredito novo ou muda o tema (os `Component`s e as larguras em cache); "Mostrar no mundo" é um clique (`HighlightRenderer.toggle`, limite 64 do `MachineHighlights.LIMIT`); nada por quadro além de `fill` e `drawString`.

- [ ] **Step 1: Teste que falha (as frases)**

`VerdictTextTest`: para cada `Reason` o título e o detalhe pedidos: `DESTINATIONS_FULL` → título "Parada: destinos cheios" (`verdict.destinations_full`), detalhe "{count} cheios em {rede}" (`verdict.destinations_full.detail`, args `count` e o nome da rede, "uma rede" se a rede não está no cabeçalho), ação "Ver destinos"; `NO_NETWORK` → "Sem rede: nada sai", detalhe "{face} {extrai/insere}, mas não tem rede" (o modo pelo tipo da face), ação "Escolher rede"; `NOTHING_TO_SEND` → "Parada: nada para mandar" e o detalhe com o limite do motor ("Vazia ou barrada pelo filtro"); `PAUSED` → "Pausada", detalhe "O grupo {grupo} está pausado", ação "Retomar"; `REDSTONE` → "Parada pelo redstone"; `UNLOADED` → "Descarregada"; `MOVING` → "Andando" com a vazão no detalhe; os problemas: `NO_NETWORK` → "{face}: sem rede" com "Escolher rede", `REDSTONE` → "{face}: desligada pelo redstone", `FILTER` → "Filtro em {face}" com "Editar". O nome do lado vem da chave de direção que o jogo já traduz (`TabletText` já tem), e o tipo ("Cima · Fluidos") só quando `machineTypes > 1`.

- [ ] **Step 2: `VerdictText` e o cartão**

Cores pelo `Tone`: `BAD` → `GuiPaint.DANGER`, `WARN` → `GuiPaint.WARN`, `MUTED` → `GuiPaint.MUTED`, `OK` → `GuiPaint.OK`. Medidas da tabela "Cartão": o fundo `hi` (`GuiPaint.BEVEL_LIGHT`), a faixa de 2 px na cor do tom, título negrito, detalhe `muted`, o botão primário (`PanelPaint.button(..., primary = true, ...)`) quando a ação não é `NONE`, os problemas com a régua `inset`, a marca de erro (`PanelPaint.errorMark(g, x, y, color)`: o "x" 5 × 5 da tabela) e o link (`PanelPaint.link`) alinhado em `BTN_FIM`. A altura é `30 + 13 × problemas` e a posição das faces (Tarefa 6) começa em `59 + altura + 8`. Sem veredito ainda (o servidor não respondeu): o cartão não aparece e as faces sobem (nada de "carregando" piscando; o veredito chega em até 1 s).

- [ ] **Step 3: As ações**

Pela `MachineActions` (nenhum payload no componente): `CHOOSE_NETWORK` abre a linha da face sem rede na lista de faces (Tarefa 6) já com o seletor de rede aberto; `SEE_DESTINATIONS` acende as posições do veredito (`HighlightRenderer.toggle(dimension, BlockPos.of(p), routerPos)` para cada uma, na dimensão do nó); `RESUME` manda `GROUP_PAUSE` com `paused = false` para o grupo que contém a rede do veredito (o cabeçalho do snapshot dá os grupos e as redes deles: `TabletActions.groupPause`, `TabletActions.java:106`); `OPEN_FILTER` só é ligado na Tarefa 7 (`MachineActions.openFilter`): até lá o link "Editar" do problema de filtro aparece desativado (texto `muted`, sem clique). "Mostrar no mundo" (rodapé, o botão secundário de (X0, H − 21)): `HighlightRenderer.toggle` da máquina escolhida, o rótulo vira "Esconder do mundo" enquanto acesa (`HighlightRenderer.isLit`), desativado se a máquina é de outra dimensão.

- [ ] **Step 4: Verificar e commitar**

Verificações globais; `./scripts/e2e.sh --only tablet --capture maquina` e abra `run/e2e/shots/maquina*.png` ao lado de `docs/preview/tablet-do-zero/maquina.png`. Commit: `Tablet do zero, etapa 2, 5/9: o cartão do veredito e Mostrar no mundo`

---

### Task 6: As faces em uso, a seta do modo e a edição

**Files:**
- Create: `client/tablet/machine/FaceList.java`, `FaceRow.java`, `FaceLayout.java` (pura), `NetworkDropdown.java`
- Modify: `client/PanelPaint.java` (`modeArrow`), `client/tablet/machine/MachinePanel.java`, `MachineActions.java`, `MachineProbe.java`, `lang/*.json`
- Test: `src/test/java/.../client/tablet/machine/FaceLayoutTest.java`

**Interfaces:**
- Produces: `PanelPaint.modeArrow(GuiGraphics g, int x, int y, PortMode mode)` (a seta do mockup, na cor de `GuiPaint.modeColor(mode)`); `FaceLayout.of(int y0, List<Face> faces, int openIndex, int unused)` → as linhas com `y` e `height` (18 cada; a aberta 18 + 44) e o `y` do "N faces sem uso"; `FaceList` com `render`, `mouseClicked`, `rowCenter` (para o e2e).
- Consumes: o `RouterSnapshot` do `TabletPanelPayload` (`FaceView` por tipo e face: modo, rede, `filterSize`), os pacotes de face de sempre: `SetFacePayload` (modo), `SetNetworkPayload` (rede) e, para o alvo, o que o `PanelEdits` já manda (`TabletMenu` é um `RouterEditHost`), `Segments` e `DropdownModal` do kit.

**Custo:** nenhum no servidor (nenhum pacote novo; as edições usam os pacotes de face e o painel já reenvia pelo `pollPanel`); no cliente, as linhas e os textos refeitos só quando o `RouterSnapshot` ou o tema muda.

- [ ] **Step 1: Teste que falha (o desenho em y)**

`FaceLayoutTest`: com 3 faces em uso e nenhuma aberta, as linhas ficam a 18 px uma da outra começando em `y0 + 14` ("Faces" ocupa os 14 primeiros), o "N faces sem uso" fica em `+ 3` depois da última; com a do meio aberta, ela ocupa 18 + 44 e as seguintes descem 44; sem face em uso a lista vira só "N faces sem uso"; `unused == 0` não desenha a linha. A ordem é por tipo (`LoadedTypes.LIST`) e depois por face (a ordem do `Direction` que o painel antigo usa, como no mockup: Cima, Trás, Baixo; confira `RelativeSide` e `FaceCube` para o rótulo relativo ao `facing` + `spin` do roteador, **o rótulo é o lado relativo da máquina**, como o painel de hoje).

Run: `./gradlew test --tests '*FaceLayoutTest'` → FAIL; implemente `FaceLayout`; PASS.

- [ ] **Step 2: `PanelPaint.modeArrow`**

Pela tabela "Seta do modo": para baixo (Insere, entra na máquina) e para cima (Extrai, sai); `PortMode` Ambos (o Armazém) desenha as duas, a de cima em `x − 2` e a de baixo em `x + 3`. Só `fill`. Os rótulos dos modos no mockup são Extrai, Insere, Armazém e Nenhum (os do `PortMode`); a cor do Armazém do mockup é `0x2F9D5C`: se o `GuiPaint.modeColor` já devolver essas (confira `GuiPaint.java:222`), use-o; senão acrescente os três tokens ao `GuiPalette` com os valores do mockup nos dois temas (e o `GuiPaletteTest`, como na etapa 1).

- [ ] **Step 3: `FaceList` e `FaceRow`**

Medidas da tabela "Faces" e "Face aberta". Uma face é "em uso" quando o modo não é `NONE` (o `FaceView.mode()`). A linha fechada: lado, seta, nome do modo, quadradinho e nome da rede (a cor da rede está no `RouterSnapshot.NetworkEntry`) ou "sem rede" em `GuiPaint.DANGER`, chevron. Clique na linha abre e fecha (uma aberta por vez; a que o `CHOOSE_NETWORK` abre vem aberta). Aberta: os quatro segmentos (`Segments` com Extrai, Insere, Armazém, Nenhum, o do modo atual escolhido; clicar manda o `SetFacePayload` com o modo novo e **a prioridade e o redstone atuais** da face, como o `PanelEdits.sendFace` faz), o seletor de rede (`NetworkDropdown` sobre o `DropdownModal`: lista as redes de `RouterSnapshot.networks()` que o jogador pode usar, `usable()`, e "Nenhuma"; escolher manda o `SetNetworkPayload`; sem rede, a borda `bad` de 1 px por fora e "Escolher rede") e o link "Filtro e mais" (ação da Tarefa 7; até lá, desativado). As faces sem uso vão na linha "N faces sem uso" (só texto; clicar nela não faz nada, como no mockup). Uma face passa a "sem uso" quando o jogador escolhe "Nenhum" (some da lista na próxima atualização do painel; mantenha-a aberta até lá para o clique não pular).

Edição à distância: só quando `menu.panel().access() == PanelAccess.EDIT`; com `READ_UNLOADED` ou `READ_OUT_OF_REACH`, os segmentos e o seletor ficam desativados e a dica diz por quê (a `PanelAccess` já tem os textos na Lista antiga, `ChosenPanel`).

- [ ] **Step 4: Ligar na anfitriã, textos, verificar e commitar**

`MachinePanel` põe o cartão e as faces em sequência (o `y` das faces vem da altura do cartão), o rodapé ("Mostrar no mundo", "Abrir roteador" desativado até a Tarefa 7) e a régua do rodapé. Verificações globais; e2e `tablet` com as capturas `maquina` e `maquina-face` lado a lado com os mockups. Commit: `Tablet do zero, etapa 2, 6/9: as faces em uso com a seta do modo, os segmentos e o seletor de rede`

---

### Task 7: Abrir o roteador no alvo, no tipo e na face; protocolo 48

**Files:**
- Modify: `packet/TabletOpenNodePayload.java`, `packet/TabletPayloads.java`, `menu/TabletMenu.java` (só `openNode`, `:804`), `menu/TabletMenuActions.java` (`openNode`, `:123`), `menu/RemoteRouterMenu.java`, `menu/RouterMenu.java`, `client/router/RouterScreen.java`, `client/tablet/TabletActions.java` (`openNode`, `:72`), `client/tablet/machine/MachineActions.java`, `FaceList.java`, `MachinePanel.java`, `VerdictCard.java`, `packet/ModPayloads.java` (`VERSION`)
- Create: `gametest/TabletOpenAtGameTests.java`

**Interfaces:**
- Produces: `TabletOpenNodePayload(int containerId, NodeKey node, Optional<SelectTargetPayload-like target>, Optional<ResourceType> type, Optional<Direction> face)`; `RemoteRouterMenu.open(ServerPlayer, RouterBlockEntity, EditTargetRef, @Nullable ResourceType type, @Nullable Direction face)`; o buffer de abertura do `RouterMenu` com `Optional<ResourceType>` e `Optional<Direction>` depois do snapshot; `RouterMenu.initial(type, face)` no cliente.

**Custo:** um clique, um pacote; nada por tick.

O alvo (a presa, uma máquina ou um grupo) vai como o `SelectTargetPayload` o leva (`EditTarget.Kind`, `id`, `Optional<BlockPos>`, `Optional<ResourceLocation>`): o `EditTargetRef` não tem codec (`EditTargetRef.java:24`), e o servidor o resolve com o `EditTargetRef.resolve(router, target, pos, block)` (confere posição e bloco; devolve `null` se o alvo não bate, e então se abre na presa, como o `refresh` faz). A face é absoluta (a mesma que o `SelectFacePayload` usa).

- [ ] **Step 1: GameTest que falha**

`TabletOpenAtGameTests`: com o jogador de teste e um roteador com uma máquina remota, mande `TabletPayloads.handleOpenNode(player, new TabletOpenNodePayload(id, key, Optional.of(target da máquina), Optional.of(ResourceType.FLUID), Optional.of(Direction.EAST)))` e confira que o menu aberto do jogador é um `RemoteRouterMenu` com `target()` igual à máquina, `selectedType() == FLUID` e `selectedFace() == EAST`; sem o tipo e a face, abre como hoje (`ITEM`, face da presa); com um alvo que não bate (posição trocada), abre na presa; com o roteador descarregado, recusa com o aviso `open.unloaded` como hoje.

- [ ] **Step 2: O pacote e o servidor**

Acrescente os três campos opcionais ao `STREAM_CODEC` (`ByteBufCodecs.optional(...)`), o `TabletMenuActions.openNode(key, target, type, face)` chama `RemoteRouterMenu.open(viewer, router, ref, type, face)`, e o `RemoteRouterMenu` escreve no buffer de abertura, depois do `RouterSnapshot.STREAM_CODEC.encode`, o `Optional<ResourceType>` e o `Optional<Direction>`; o servidor aplica `menu.select(type, face)` (`RouterMenu.java:334`) ao criar o menu (dentro do `SimpleMenuProvider`) para os slots de cartão já mostrarem os da face. **Todo o resto que abre um `RouterMenu` (`RouterMenu.java:251`, `LinkerOpen.java:168`) escreve `Optional.empty()` nos dois.**

- [ ] **Step 3: O cliente**

O construtor do `RouterMenu` do buffer (`RouterMenu.java:140`) lê os dois opcionais e, se presentes, chama `select(type, face)`; a `RouterScreen` (`:98`) passa `menu.selectedFace()` ao `NodePanel` como já faz e acrescenta `panel.select(menu.selectedType())` (o `NodePanel.select(ResourceType)` existe, é o que o `ChosenPanel.openOn` usa). O `TabletActions.openNode(key, target, type, face)` manda o pacote; o `openNode(key)` antigo (a Lista antiga, o Mapa) manda os três vazios.

- [ ] **Step 4: Ligar os botões e o protocolo**

"Abrir roteador" (rodapé): `openNode(nó, alvo escolhido, empty, empty)`; "Filtro e mais" (na face aberta e no link "Editar" do problema de filtro do cartão): `openNode(nó, alvo, Optional.of(tipo da face), Optional.of(face))`. A tela do roteador aberta à distância já volta ao Tablet pela regra do `RemoteRouterMenu.mayStayOpen`. Suba `ModPayloads.VERSION` para `"48"` e acrescente a linha do javadoc (ver "Decisão sobre o protocolo").

- [ ] **Step 5: Verificar e commitar**

Verificações globais (as cinco runs); e2e `tablet`: clicar "Filtro e mais" numa face de fluido leva à tela do roteador na aba Fluidos e na face escolhida.
Commit: `Tablet do zero, etapa 2, 7/9: abrir o roteador no alvo, no tipo e na face; protocolo 48`

---

### Task 8: e2e e capturas da aba Máquina

**Files:**
- Create: `client/dev/E2eTabletMachineSteps.java`
- Modify: `client/dev/E2eTabletSteps.java` (registra a suíte; `legacyList` nos passos antigos), `E2eTabletMachinesSteps.java`, `E2eTabletTreeSteps.java`, `E2eTabletMarkSteps.java`, `E2eFlowsSteps.java`, `E2eShowcase.java`, `ScreenshotTablet.java`, `client/tablet/machine/MachineProbe.java`

**Interfaces:**
- Produces: `MachineProbe` (os ganchos do e2e, como o `NetworksProbe`: `listRowCenter(label)`, `chosenLabel()`, `verdictTitle()`, `verdictTone()`, `actionCenter()`, `faceRowCenter(type, face)`, `segmentCenter(mode)`, `networkSelectCenter()`, `showInWorldCenter()`, `openRouterCenter()`, `filterLinkCenter()`, `snapshotBuilds()`), `TabletScreen.machineProbe()`.

**Custo:** só desenvolvimento.

- [ ] **Step 1: Os passos novos** (`E2eTabletMachineSteps.machineSteps(list)`, na suíte `tablet`):

1. abre o Tablet com uma base de teste (o que o `E2eNetworksSteps`/`E2eTabletMachinesSteps` já montam: a fornalha com os destinos cheios, uma máquina sem rede e outra descarregada), vai à aba Máquina e confere que as linhas e os estados batem com o mockup (`destinos cheios` em `bad`, `sem rede`, `descarregada`);
2. a busca "forn" deixa só as fornalhas; o clique numa linha a escolhe e o cabeçalho mostra "Roteador <tier> · a N blocos";
3. com a fornalha de destinos cheios escolhida, o cartão mostra "Parada: destinos cheios" em `bad` e o botão "Ver destinos"; clicar acende as posições (`HighlightRenderer.count() == 2`); "Mostrar no mundo" acende a máquina; clicar de novo apaga;
4. a máquina sem rede: o cartão "Sem rede: nada sai" com "Escolher rede", que abre a face e o seletor com a borda `bad`; escolher uma rede muda "sem rede" para o nome e o cartão muda no segundo seguinte (espere o veredito, `E2eTiming`);
5. os segmentos: Extrai → Insere muda a seta e o nome do modo na linha (e no servidor: `FaceView.mode`); "Nenhum" a tira da lista e o "N faces sem uso" sobe;
6. "Filtro e mais" abre a tela do roteador no tipo e na face (a `RouterScreen` com `selectedType()`/`selectedFace()` certos) e o Esc volta ao Tablet; "Abrir roteador" abre na presa;
7. a aba lembrada: fecha o Tablet em Fluxos e o reabre em Fluxos (decisão 10); o clique direito com o Tablet numa máquina abre em Máquina com ela escolhida;
8. **custo:** com a aba Máquina aberta e parada, o contador `snapshotBuilds()` e a contagem de pacotes do veredito não crescem a cada quadro (só a cada mudança); em outra aba o veredito não chega.

- [ ] **Step 2: Capturas**

Pelo `ScreenshotTablet`/`E2eShowcase`, capturas `maquina` (a fornalha de destinos cheios, como o mockup `maquina.png`) e `maquina-face` (o Distribuidor sem rede, a face "Cima" aberta, como `maquina-face.png`), nos dois temas (`--variants escuro`) e em pt_br. A comparação é **visual**: abra `run/e2e/shots/maquina*.png` ao lado de `docs/preview/tablet-do-zero/maquina*.png` e anote no commit o que difere (as diferenças aceitas: o texto do filtro, o tipo na linha de face, a distância).

- [ ] **Step 3: Os passos antigos** (árvore, marcas, máquinas da Lista, Grade) ganham `tabletScreen().legacyList(true)` no começo e `legacyList(false)` ao fim; o `E2eFlowsSteps` que clicava numa linha e esperava a Lista filtrada passa a esperar só a troca de aba.

- [ ] **Step 4: Rodar tudo**

Run: `./scripts/e2e.sh --only tablet,tela --capture maquina` e depois `./scripts/e2e.sh --completo` uma vez. Expected: `run/e2e/result.txt` começa por `OK`.
Commit: `Tablet do zero, etapa 2, 8/9: e2e e capturas da aba Máquina`

---

### Task 9: Docs, guia e progresso

**Files:**
- Modify: `scripts/guide/paginas/network_tablet.py`, `docs/progresso/README.md`, `pendencias.md`, `estado.md`, `decisoes.md`, `historico/2026-11.md`, `docs/mapa/tablet.md`, `docs/mapa/motor.md`, `docs/mapa/cliente-e-pacotes.md`, `docs/mapa/testes.md`, `docs/mapa/logica-pura.md`

- [ ] **Step 1: Guia (pt e en)**

Na `network_tablet.py`, a linha da tabela das abas fica `| **Máquina** | Escolha uma máquina e veja por que ela parou, as faces dela e mude o modo e a rede sem sair do Tablet. |` (e o equivalente em inglês), e a seção "A Lista (aba Máquina)" vira "A aba Máquina": a lista com a busca e o estado de cada uma (a vazão de quem anda, "destinos cheios", "sem rede", "cheia", "descarregada"), o **cartão** (a cor da faixa diz a gravidade; o botão faz o que resolve: **Ver destinos** acende os cheios no mundo, **Escolher rede**, **Retomar**; os outros problemas vêm abaixo com um link), as **Faces** em uso (a seta mostra o modo; clique na linha para trocar Extrai, Insere, Armazém ou Nenhum e a rede; **Filtro e mais** abre a tela do roteador naquela face), **Mostrar no mundo** e **Abrir roteador**; e uma nota: o Tablet abre na última aba usada, e o clique direito com ele numa máquina abre na aba Máquina com ela escolhida. O que o guia dizia de Selecionar, colar nos marcados e árvore **fica** (sai na etapa 4, com o Tablet). Rode `python scripts/guide/gerar_guia.py` e, **depois**, `git checkout -- <guide>/*/linker.md` (o `linker.py` está atrasado e o gerador o regera errado); confira o diff dos `.md` gerados: só as páginas do Tablet mudam.

- [ ] **Step 2: Progresso e mapa**

- `historico/2026-11.md`, no topo: uma linha "Tablet do zero, etapa 2: a aba Máquina (lista com busca, veredito de 1 s só da máquina escolhida, faces editáveis com a seta do modo, Mostrar no mundo, Abrir roteador e Filtro e mais no tipo e na face); protocolo 48. N JUnit + N + N + N + N + N GameTests, e2e `tablet`, `tela` OK." com os números reais.
- `pendencias.md`, área Tablet: o teste à mão do dono da aba Máquina, o "Ver destinos" que acende no mundo (ajuste 6, confirmar), o resumo do filtro que falta ao motor ("só ferro sai", ajuste 5), o limite "vazia × filtro" (`Port.waitsDestination`), e as etapas 3 e 4; o que foi feito sai de lá.
- `estado.md`: a linha do Tablet (aba Máquina nova, veredito, "Abrir em face") "no ramo, sem versão".
- `decisoes.md`: "O veredito de uma máquina é montado no servidor só para a máquina escolhida, 1 vez por segundo, com a aba Máquina aberta; vai num payload próprio e só quando muda." e "O Tablet abre na última aba usada; o clique com ele numa máquina abre em Máquina."
- `README.md`: "Agora" e "Próximo passo" (a etapa 3, os Fluxos, e a limpeza).
- `docs/mapa/tablet.md`: o pacote `client/tablet/machine/` (uma frase por classe, como o da aba Redes), o `TabletVerdict`, o `TabletWatches`, o `TabletHeaderCodec` e o `TabletOpenNodePayload` com alvo, tipo e face; troque "A aba Máquina mostra por ora a Lista" pelo que é agora e diga que a `TabletList` só aparece pelo `legacyList` até a etapa 4. `docs/mapa/motor.md`: `Verdict`, `VerdictFacts`, `VerdictRules` e `VerdictProbe` em `network/`. `docs/mapa/cliente-e-pacotes.md`: o `TabletVerdictPayload`, o `TabletOpenNodePayload` novo e o buffer do `RouterMenu`. `testes.md` e `logica-pura.md`: o `TabletVerdictGameTests`, o `TabletOpenAtGameTests` e os quatro JUnit novos.

- [ ] **Step 3: Varreduras e commit**

```bash
python -I .claude/skills/wa-comentarios/scripts/varrer.py
python -I .claude/skills/wa-higiene/scripts/varrer.py
python -I .claude/skills/wa-modular/scripts/medir.py
```

Resolva o que acusarem nos arquivos do ramo (javadocs que citam "a Lista" onde agora é a Máquina; `grep -rn "visão Máquinas da Lista\|por ora, a Lista" src docs/mapa scripts/guide`). Commit: `Tablet do zero, etapa 2, 9/9: guia, mapa e progresso da etapa 2`

---

## Revisão do plano contra a spec (etapa 2)

| Item da spec | Tarefa |
| --- | --- |
| Lista à esquerda: busca, linhas de 24 px, estado `bad`/`warn`/`muted` ou a vazão | 4 |
| Cabeçalho "Roteador <tier> · a N blocos" | 4 |
| Veredito no servidor, só da máquina escolhida, com a aba aberta, 1 por segundo, payload próprio só quando muda | 3 |
| Classe em `network/` que lê o `Port`; classe pura com JUnit: motivo, detalhe, ação, problemas | 2 (pura), 3 (`VerdictProbe`) |
| Casos da decisão 7: sem rede, cheios (quantos e em qual rede), redstone, pausada, descarregada, filtro, "nada para mandar" | 2 (regra e JUnit), 3 (GameTests), 5 (frases) |
| Cartão: faixa de 2 px, título em negrito, detalhe, botão primário, problemas com link | 5 |
| Faces em uso com seta do modo e rede; aberta: Extrai/Insere/Armazém/Nenhum, seletor (borda `bad` sem rede), "Filtro e mais"; sem uso recolhidas; edição pelos pacotes de face | 6 |
| "Mostrar no mundo" com o realce de `client/highlight/` | 5 |
| "Abrir roteador" e "Filtro e mais" com alvo, tipo e face; protocolo sobe | 7 |
| Decisão 10: última aba usada; clique com o Tablet abre Máquina com a máquina escolhida | 4 (escolha pelo painel), 8 (e2e); o resto já existe (ajuste 3) |
| GameTests do veredito pelo caminho real, JUnit da lógica pura, e2e com capturas comparadas ao mockup | 2, 3, 7, 8 |
| `TabletMenu` e `TabletSnapshot` não crescem; codec do cabeçalho fora | 1 |
| Docs: progresso, mapa, guia nos dois idiomas | 9 |
