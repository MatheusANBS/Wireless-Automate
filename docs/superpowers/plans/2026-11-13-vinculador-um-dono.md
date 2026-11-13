# Plano: Vinculador de um dono

Spec: `docs/superpowers/specs/2026-11-13-vinculador-um-dono-design.md` (mockup aprovado: `docs/preview/vinculador-um-dono.html`). Duas regras no modo Vincular do Vinculador: a máquina de outro roteador é recusada, e o Ctrl + clique segue só as máquinas do mesmo bloco que se tocam com a clicada (faces, arestas e pontas), no roteador e na Doca, para vincular e para desvincular.

Pacote base: `src/main/java/io/github/matheusanbs/wirelessautomate/` (abreviado abaixo).

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; textos novos e mudados nos dois `lang` (`pt_br.json` e `en_us.json`), sempre os dois.
- Chaves existentes de NBT, componentes, config e payloads **não mudam**. `ModPayloads.VERSION` continua o mesmo (nenhum payload muda de formato). Nenhuma chave de `lang` é apagada; as chaves `range.same.none`, `range.same.none.capped` e `range.same.capped` só mudam de texto.
- `RouterBlockEntity` está em 999 linhas: **nenhuma linha nova nele**. O que o roteador precisar fica no `RouterRange` e no `RouterPresence` (que já recebem os avisos de vínculo, carga e saída).
- `RouterBlockEntity.linkMachine` e `unlinkMachine` não mudam: a regra de um dono é do Vinculador (`range/RangeLinks`, `range/SameBlockLinks`). Testes, e2e e bench seguem vinculando direto.
- Doca e roteador continuam podendo dividir a mesma máquina (decisão do dono de 10/11): a Doca **não** consulta o índice dos roteadores, e o roteador **não** consulta o das Docas.
- **Performance** (skill `wa-performance`, leia `.claude/skills/wa-performance/SKILL.md`): parado custa zero (nada por tick, nada no motor, no `RemoteIndex` nem no mixin); trabalho proporcional à mudança (o índice de donos refeito só quando as remotas do roteador mudam, na carga e na saída); o Ctrl + clique lê só o grupo que se toca, com o teto `SameBlockScan.MAX_READS`; capability, outro dono e evento de clique das proteções só nas que de fato seriam vinculadas; nunca carregar chunk para consultar (`getChunkNow` / `level.isLoaded` antes de ler).
- **Tamanho** (skill `wa-modular`): servidor até 1000 linhas; nenhuma classe mudada pode passar de ~600 sem dividir. `python -I .claude/skills/wa-modular/scripts/medir.py <arquivos>` antes do commit.
- **Comentários** (skill `wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py` antes do commit; atualize todo javadoc que descreve o comportamento antigo ("no raio", "da mais perto do roteador", "todas as do mesmo bloco", "o cubo do raio", "não lê mundo além do bloco clicado").
- **Varredura de performance:** `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` antes do commit; resolva ou justifique cada candidato no relatório.
- **GameTests:** classe com `@GameTestHolder(WirelessAutomate.MODID)` e `@PrefixGameTestTemplate(false)`, template `"empty"`, `timeoutTicks` de no máximo 300. Os testes do mesmo lote rodam em paralelo e dividem os índices estáticos (`RemoteIndex`, o índice de donos, o `DockIndex`): teste pertinência, nunca contagem global. O `onLoad` de um block entity recém-colocado só roda no tick seguinte: `startSequence().thenWaitUntil(...)`. Jogador só por `TestPlayers` (`survivalMenuOpener`, `menuOpener`...). Teste que muda a config (`Config.*.set`) vai num lote `wirelessautomate_config_<assunto>` próprio e devolve o valor em `try/finally`. Use os ajudantes que já existem (`gametest/RangeTestSupport`, os de `SameBlockLinkGameTests` e `DockLinkGameTests`).
- **Verificação de cada tarefa:** `./gradlew build runGameTestServer` (e as runs indicadas na tarefa). Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, conserte antes de rodar jogo. **Nunca dois Gradle ou jogos ao mesmo tempo** (cada um usa ~3 GB). Não mexa em `run/options.txt`. Se uma run de GameTest passar de ~10 min, olhe `run/gametest/logs/latest.log` e ache o teste parado.
- **Commits:** um por tarefa, mensagem curta em português no estilo do `git log` (ex.: `Vinculador: a máquina de um roteador só`), autor do dono e o committer padrão do ambiente, que assina (`git commit --author="MatheusANBS <matheusanbs@hotmail.com>" ...`; nunca mude o `user.name`, o `user.email` nem o committer), **sem** linhas `Co-Authored-By` ou `Claude-Session`. Sem push.

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `range/OwnerIndex.java` (novo) | O corpo genérico do `DockIndex`: os donos carregados por posição de máquina, por nível |
| `dock/DockIndex.java` (mudar) | Vira só o dono da instância (`get()`, `reset()`) de um `OwnerIndex<PatternDockBlockEntity>` |
| `dock/DockLinks.java`, `dock/DockTuner.java`, `dock/PatternDockBlockEntity.java` (mudar) | `docksAt` → `at` |
| `range/RouterOwners.java` (novo) | O `OwnerIndex<RouterBlockEntity>` dos roteadores e o `otherRouter` (vinculada a outro ou presa de outro) |
| `block/RouterRange.java`, `block/RouterPresence.java` (mudar) | Registrar no índice quando as remotas mudam e na carga; tirar na saída |
| `WirelessAutomate.java` (mudar) | `RouterOwners.reset()` na parada |
| `range/RangeRules.java` (mudar) | `LinkRefusal.OTHER_ROUTER` |
| `range/RangeLinks.java` (mudar) | A recusa no clique simples e o campo `otherRouter` do `Result` |
| `range/SameBlockScan.java` (reescrever) | A busca em largura pelas que se tocam |
| `range/SameBlockLinks.java`, `dock/DockSameBlock.java` (mudar) | O Ctrl + clique pelo grupo, para vincular e desvincular; o outro roteador no laço |
| `lang/pt_br.json`, `lang/en_us.json` (mudar) | Textos novos e mudados |
| `gametest/RouterOwnersGameTests.java`, `gametest/OneOwnerLinkGameTests.java`, `gametest/TouchingLinkGameTests.java` (novos) | GameTests das três tarefas de código |
| `gametest/SameBlockLinkGameTests.java`, `gametest/DockLinkGameTests.java` (mudar) | Os testes do Ctrl + clique com as máquinas encostadas |
| `client/dev/E2eLinkerSteps.java` (mudar) | Os dois funis do e2e passam a se tocar |
| `scripts/guide/paginas/linker.py`, `scripts/guide/paginas/pattern_dock.py` (mudar) + páginas geradas | O guia |
| `docs/especificacao.md`, `docs/mapa/roteador.md`, `docs/mapa/alcance.md`, `docs/mapa/doca.md` (mudar) | Documentação |

---

## Tarefa 1: o índice de donos dos roteadores

**Arquivos:** `range/OwnerIndex.java` (novo), `range/RouterOwners.java` (novo), `dock/DockIndex.java`, `dock/DockLinks.java`, `dock/DockTuner.java`, `dock/PatternDockBlockEntity.java`, `block/RouterRange.java`, `block/RouterPresence.java`, `WirelessAutomate.java`, `gametest/RouterOwnersGameTests.java` (novo).

**Interfaces que produz:**

```java
// range/OwnerIndex.java: o corpo do DockIndex de hoje, genérico, público
public final class OwnerIndex<T> {
    public void update(T owner, Level level, long[] positions); // refaz o registro do dono (tira o anterior)
    public void remove(T owner);                                // tira tudo o que o dono registrou
    public List<T> at(Level level, BlockPos pos);               // os donos da posição; List.of() sem nenhum; só ler
}

// dock/DockIndex.java: só a instância
public final class DockIndex {
    public static OwnerIndex<PatternDockBlockEntity> get();
    public static void reset();
}

// range/RouterOwners.java
public final class RouterOwners {
    public static OwnerIndex<RouterBlockEntity> get();
    public static void reset();
    /**
     * Outro roteador carregado (não {@code chosen}) que tem a máquina em {@code pos}: vinculada (ativa ou não,
     * pelo índice, conferida com {@code remoteMachines().index(pos) >= 0}) ou presa (um roteador encostado
     * com {@code machinePos()} em {@code pos}, nos 6 vizinhos carregados). {@code null} se nenhum.
     */
    public static @Nullable RouterBlockEntity otherRouter(Level level, RouterBlockEntity chosen, BlockPos pos);
}
```

**Passos:**

1. Crie o `OwnerIndex<T>` a partir do `DockIndex` (mesmos campos: por nível um `Long2ObjectOpenHashMap<List<T>>`, e o registro por dono num `Reference2ObjectOpenHashMap<T, Registration>`); o javadoc descreve o índice sem dono específico (só no clique, nunca no motor; só a thread do servidor; um dono descarregado não aparece).
2. Reduza o `DockIndex` ao dono da instância (`get()`, `reset()`), com o javadoc de hoje sobre a unicidade entre Docas. Troque `docksAt` por `at` no `DockLinks` e no `DockTuner`; o `PatternDockBlockEntity` segue chamando `update`/`remove`.
3. Crie o `RouterOwners` com a instância, o `reset()` e o `otherRouter`. Primeiro o índice (laço na lista do `at`, pulando `chosen`, os removidos e os que já não têm a posição); depois os 6 vizinhos com um `BlockPos.MutableBlockPos`, cada um com `level.isLoaded` antes do `getBlockEntity`. Só no clique: pode alocar.
4. Registro dos roteadores: um método `ownersChanged()` no `RouterRange` que, num `ServerLevel` e com o roteador não removido, chama `RouterOwners.get().update(router, level, posições)` com as posições de **todas** as remotas (`remotes.machines()`, sem filtro de ativas, sem exigir o upgrade nem o motor); sem remotas, `remove`. Chame-o no fim do `remotesChanged()` e no `RouterPresence.loaded()`. Tire no `RouterPresence.leaveManager()` (quebra e descarga do chunk) com `RouterOwners.get().remove(router)`.
5. `WirelessAutomate`: `RouterOwners.reset()` ao lado do `DockIndex.reset()`.
6. `gametest/RouterOwnersGameTests` (run comum), cada um com roteadores e fornalhas próprios:
   - vinculada pelo `linkMachine` ao roteador A: `otherRouter(level, B, fornalha) == A`; `otherRouter(level, A, fornalha) == null`; desvinculada, `null`;
   - inativa: uma fornalha vinculada pelo `linkMachine` fora do raio de A (o `linkMachine` não confere raio) continua achada; e um roteador A **sem** o Upgrade de Alcance com remotas guardadas também;
   - presa: o roteador A virado para uma fornalha (sem vínculo): `otherRouter(level, B, fornalha) == A` e `otherRouter(level, A, fornalha) == null`;
   - saída: depois de quebrar A (`setBlock` de ar) e esperar um tick, `otherRouter` volta `null` e `RouterOwners.get().at(...)` não tem A.

**Custo:** nada por tick. Uma refeita O(remotas do roteador) por vínculo, desvínculo, carga do NBT e carga do roteador (o mesmo que o `RemoteIndex.update` já faz); memória de uma entrada por máquina vinculada de roteador carregado. O cenário `range` do bench mede o heap retido (fica para a verificação final).

**Verificação:** `./gradlew build runGameTestServer runGameTestServerAe2 runGameTestServerRefined` (as duas últimas cobrem a Doca com o AE2 e o RS2 depois da troca do `DockIndex`). Commit: `Vinculador: o índice de donos dos roteadores`.

---

## Tarefa 2: a máquina de um roteador só

**Arquivos:** `range/RangeRules.java`, `range/RangeLinks.java`, `range/SameBlockLinks.java`, `lang/pt_br.json`, `lang/en_us.json`, `gametest/OneOwnerLinkGameTests.java` (novo).

**Usa:** `RouterOwners.otherRouter(level, chosen, pos)` da tarefa 1.

**Passos:**

1. `RangeRules.LinkRefusal`: `OTHER_ROUTER` no **fim** do enum (chave `range.refusal.other_router`). Confira que nada usa o `ordinal` ou o `values()` dele num codec; se usar, avise no relatório.
2. `RangeLinks.Result`: um campo `int otherRouter` (as de outro roteador que ficaram de fora no Ctrl + clique), entre `protectedCount` e `unloadedChunks`; ajuste o `of`, o `refused` e todo `new Result(...)`.
3. `RangeLinks.toggle`: depois do `machineRefusal` dar `NONE` e **antes** do raio e do limite, `RouterOwners.otherRouter(level, router, pos)`; achado, mostra na action bar `Component.translatable("item.wirelessautomate.linker.range.refusal.other_router", RouterBlock.machineName(bloco), routerName(outro), LinkerActions.position(outro.getBlockPos()))` e devolve `Result.refused(LinkRefusal.OTHER_ROUTER)` (o desvincular de uma vinculada ao escolhido continua vindo antes de tudo). O `refusalText` genérico não sabe montar esse texto: deixe isso escrito no javadoc da constante e garanta que nenhum caminho o chame com `OTHER_ROUTER`. Atualize o javadoc da classe (a ordem das recusas).
4. `SameBlockLinks.link`: no laço das candidatas, depois do `isMachine` e antes do `clickAllowed`, uma de outro roteador (`otherRouter != null`) não ocupa vaga e conta num contador novo, que vai para o `Result.otherRouter`; a clicada de outro roteador serve de modelo (ela fica de fora pelo laço e as outras entram, como na Doca). Na `message`, `RangeLinks.appendCount(text, "same.other_router", result.otherRouter())` logo depois do `area.protected`. Atualize o javadoc.
5. `lang`: `item.wirelessautomate.linker.range.refusal.other_router` = `%s já é de %s em %s` / `%s already belongs to %s at %s`; `item.wirelessautomate.linker.range.same.other_router` = `%s de outro roteador ficaram de fora` / `%s of another router left out`.
6. `gametest/OneOwnerLinkGameTests` (run comum), pelo caminho do Vinculador (`RangeLinks.toggle` e `SameBlockLinks.link`, como em `SameBlockLinkGameTests` e `RangeTestSupport`):
   - o clique numa fornalha vinculada a outro roteador (ativa) é recusado com `OTHER_ROUTER`, a chave da mensagem e os três argumentos (o terceiro é a posição do outro); a fornalha não entra no escolhido;
   - o mesmo com a fornalha **inativa** no outro (vinculada direto fora do raio dele) e com a **presa** de outro roteador;
   - uma fornalha que já é de uma **Doca** entra no roteador (a regra é só entre roteadores);
   - duplicada de antes (vinculada direto em A e em B): o clique com A escolhido **desvincula** de A, e B continua com ela;
   - depois de quebrar o outro roteador, o clique vincula;
   - Ctrl + clique em três fornalhas encostadas em fila, com a do meio de outro roteador: vinculam as duas das pontas, `result.otherRouter() == 1` e a mensagem tem a parte `same.other_router`;
   - Ctrl + clique na fornalha de outro roteador: ela fica nele e as encostadas entram no escolhido.

**Custo:** só no clique: uma consulta no índice e os 6 vizinhos, depois das recusas baratas; no Ctrl + clique, o mesmo para cada candidata que passou do `isMachine`.

**Verificação:** `./gradlew build runGameTestServer`. Commit: `Vinculador: a máquina de um roteador só`.

---

## Tarefa 3: o Ctrl + clique pelas que se tocam

**Arquivos:** `range/SameBlockScan.java` (reescrever), `range/SameBlockLinks.java`, `dock/DockSameBlock.java`, `lang/pt_br.json`, `lang/en_us.json`, `gametest/SameBlockLinkGameTests.java`, `gametest/DockLinkGameTests.java`, `gametest/TouchingLinkGameTests.java` (novo).

**Interface que produz:**

```java
public final class SameBlockScan {
    public static final int MAX_READS = 32 * 4096;
    /** Sem o corte do raio (o desvincular). */
    public static final int NO_RADIUS = -1;

    /**
     * @param linkedPositions as posições do grupo que estão em {@code linked}, na ordem da busca
     * @param candidates      as que podem entrar, na ordem da busca (da mais perto da clicada)
     * @param complete        o grupo inteiro foi lido (sem parar cedo nem no teto)
     * @param capped          parou no {@link #MAX_READS}
     * @param reads           quantas leituras de vizinho fez
     */
    public record Scan(LongArrayList candidates, LongArrayList linkedPositions, int protectedCount,
            int unloadedChunks, boolean complete, boolean capped, int reads) {
        public int already() { return linkedPositions.size(); }
    }

    /**
     * O grupo de {@code block} que se toca com {@code start} (os 26 vizinhos), em largura a partir dele; só dentro
     * do cubo de Chebyshev {@code radius} em volta de {@code origin} ({@link #NO_RADIUS}: sem corte) e na altura do
     * mundo; só em chunks carregados. Cada posição do grupo: em {@code excluded}, nada; em {@code linked}, vai para
     * {@code linkedPositions}; sem {@code mayInteract}, conta em {@code protectedCount}; senão é candidata. O grupo
     * passa por todas elas. Para quando já há {@code needed} candidatas ou no {@link #MAX_READS}.
     */
    public static Scan scan(ServerPlayer player, BlockPos start, BlockPos origin, int radius, Block block,
            LongSet linked, LongSet excluded, int needed);
}
```

**Passos:**

1. Reescreva o `SameBlockScan` como busca em largura: fila `LongArrayFIFOQueue`, visitadas num `LongOpenHashSet`, os 26 deslocamentos numa constante (ordem fixa, para a ordem do resultado ser determinística); o `start` entra primeiro (classificado como os outros). Para cada vizinho: conte a leitura (no teto, `capped`); fora da altura do mundo ou do raio, pule; chunk por `level.getChunkSource().getChunkNow(cx, cz)` com um cache do último chunk (o grupo fica quase sempre no mesmo), e um descarregado conta uma vez por chunk (`LongOpenHashSet` das chaves de chunk) em `unloadedChunks` e é pulado; o estado pela seção (`chunk.getSection(chunk.getSectionIndex(y)).getBlockState(x & 15, y & 15, z & 15)`), sem block entity. Mesmo bloco e não visitada: entra na fila e é classificada. Com `candidates.size() >= needed`, para com `complete = false`. Tire o que era do cubo (`Box`, `Section`, `sections`, `neededDistance`, `sortByDistance`). Javadoc novo (o custo: até 26 leituras por posição do grupo).
2. `SameBlockLinks.link`: `SameBlockScan.scan(player, pos, router.getBlockPos(), RangeLinks.radius(router), block, linked, presa, room + 1)` (o `scan(...)` interno ganha o `start`). Javadoc: as que se tocam, da mais perto da clicada.
3. `SameBlockLinks.unlink`: o grupo pela varredura a partir da clicada, `NO_RADIUS`, `linked` = as posições das remotas do roteador, `excluded` vazio, `needed = Integer.MAX_VALUE`; desvincula as `linkedPositions()` (dentro do `batchChanges`), com a mesma mensagem e os cartões de hoje. Javadoc: só as que se tocam, sem o corte do raio; agora lê o grupo.
4. `DockSameBlock.link`: `SameBlockScan.scan(player, pos, dock.getBlockPos(), DockLinks.radius(dock), block, inSide, otherSides, room + 1)`. `DockSameBlock.unlink`: o grupo a partir da clicada, `NO_RADIUS`, `linked` = as do lado escolhido, desvincula só as `linkedPositions()`. Javadocs.
5. `lang`, só o texto: `range.same.none` = `%s: todas as que se tocam já estão vinculadas` / `%s: all the touching ones are already linked`; `range.same.none.capped` = `%s: nenhuma nova no grupo lido` / `%s: no new ones in the part of the group read`; `range.same.capped` = `o resto do grupo não foi procurado` / `the rest of the group was not searched`.
6. Testes que já existem: passe por **todos** os do Ctrl + clique em `SameBlockLinkGameTests` e `DockLinkGameTests` (e qualquer outro que chame `SameBlockLinks.link`, `DockSameBlock.link` ou `SameBlockScan.scan`; procure com `grep`) e ponha as máquinas encostadas onde o teste quer que elas entrem, mantendo o que cada um prova. O `sameBlockNearestFirstUpToTheLimit` passa a provar "da mais perto da clicada". O `sameBlockUnlinksAllOfThatBlock` passa a provar que só o grupo sai. Renomeie o que ficar com nome mentiroso.
7. `gametest/TouchingLinkGameTests` (run comum, o que for de config num lote próprio):
   - faces e quinas: a clicada, uma encostada pela face, uma pela aresta (diagonal no plano) e uma pela ponta (diagonal em 3D) entram; uma separada por um bloco de ar fica;
   - passa por quem não entra: numa fila, depois de uma já vinculada, da presa e de uma protegida (ou de outro roteador), as seguintes entram;
   - o raio corta: numa fila que sai do raio, as de fora não entram e o grupo não continua por elas (uma que volta para dentro do raio só por elas também não entra);
   - ordem com o limite: com vagas para 2, entram as duas mais perto da clicada, não as mais perto do roteador;
   - desvincular: dois grupos do mesmo bloco vinculados ao mesmo roteador; o Ctrl + clique num tira só o dele; uma vinculada encostada fora do raio sai junto;
   - Doca: o Ctrl + clique num lado vincula só o grupo, e o desvincular tira só o grupo;
   - custo: `SameBlockScan.scan` direto num grupo de N fornalhas com um raio grande e outras fornalhas soltas no raio: `reads() <= 26 * N`, e as soltas não são candidatas.

**Custo:** só no clique: até 26 leituras por posição do grupo (antes, o cubo inteiro do raio), com o mesmo teto; o desvincular passa a ler o grupo (antes, nada além do clicado).

**Verificação:** `./gradlew build runGameTestServer runGameTestServerAe2 runGameTestServerRefined`. Commit: `Vinculador: o Ctrl + clique nas que se tocam`.

---

## Tarefa 4: guia, e2e e docs

**Arquivos:** `scripts/guide/paginas/linker.py`, `scripts/guide/paginas/pattern_dock.py` e as páginas geradas, `client/dev/E2eLinkerSteps.java`, `docs/especificacao.md`, `docs/mapa/roteador.md`, `docs/mapa/alcance.md`, `docs/mapa/doca.md`.

**Passos:**

1. Guia, nos dois idiomas, só "como operar", frases curtas:
   - `linker.py`: o passo 3 ("segure Ctrl e clique numa delas: entram as do mesmo bloco **encostadas nela** (pela face ou pela quina), dentro do raio, as mais perto primeiro, até o limite do tier"); o passo 4 ("desvincula as encostadas"); um passo ou nota: "Uma máquina é de um roteador só. Se ela já é de outro (vinculada ou presa nele), a action bar diz de qual."; a linha do Ctrl na tabela de gestos.
   - `pattern_dock.py`: o passo 2 ("as iguais **encostadas nela** entram naquele lado").
   - Regere com `python -I scripts/guide/gerar_guia.py` e confira que só as páginas do Vinculador e da Doca mudaram (`git status`).
2. e2e (`E2eLinkerSteps`): os dois funis passam a se tocar (por exemplo o `linkHopperSame` na diagonal do `linkHopper`, encostado pela aresta); confira que a posição nova está livre e que nenhum outro passo depende da antiga. Rode `./scripts/e2e.sh --only vinculador` (com `E2E_TIMEOUT=1200` se precisar) e confira `run/e2e/result.txt` = `OK`.
3. Docs:
   - `docs/especificacao.md`, a linha do **Vincular** (o "quem entra", a ordem, o desvincular do Ctrl + clique, o custo e a mensagem "todas as que se tocam") e a regra de um dono (com o limite do roteador descarregado), citando a spec nova;
   - `docs/mapa/roteador.md` (a linha do `linker/`), `docs/mapa/alcance.md` (`SameBlockScan`, `SameBlockLinks`, `RangeLinks`, `OwnerIndex`, `RouterOwners`, `RouterRange` se tiver linha) e `docs/mapa/doca.md` (`DockIndex`, `DockSameBlock`): a linha de cada classe que mudou.

**Verificação:** `./gradlew build runGameTestServer` e o e2e `vinculador`. Commit: `Vinculador de um dono: guia, e2e e docs`.

---

## Fechamento (o condutor)

- Verificação completa: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams runGameTestServerAe2 runGameTestServerRefined runGameTestServerDockChemicals` e o e2e `vinculador` (e `doca`, pela página do guia, se a suíte `guia` não rodar).
- Bench `range:200:3,idle:500:3` antes (no commit de base) e depois, com a tabela e o heap retido no `docs/benchmark.md`.
- Revisão final do ramo no modelo mais capaz.
- `docs/progresso/`: histórico, pendências (o teste à mão do dono; o changelog da próxima versão), estado e próximo passo.
