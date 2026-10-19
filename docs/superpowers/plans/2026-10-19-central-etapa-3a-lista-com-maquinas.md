# Plano da etapa 3a da central da rede: a Lista com as máquinas

Spec: `docs/superpowers/specs/2026-10-19-central-etapa-3a-lista-com-maquinas-design.md`. Mockup aprovado: `docs/preview/central-etapa-3.html`, com as telas da v6 em `docs/preview/central-da-rede.html` (seções Lista e Mapa). Ramo: `central-etapa-3a`.

## Restrições globais

- **Idioma:** código, comentários, commits e docs em português; as traduções vão sempre nos dois arquivos, `en_us.json` e `pt_br.json`.
- **NBT e protocolo:**
  - as chaves existentes de NBT, componentes e config não mudam;
  - as únicas chaves novas no mundo são as das máquinas no índice: `remote_pos`, `remote_group`, `remote_exception`, `remote_labels` e `remote_groups`, gravadas só quando há máquinas, mais `remote_total` e o `count` de cada grupo, só acima do teto de 512;
  - `ModPayloads.VERSION` sobe de `"23"` para `"24"`, uma vez, na Tarefa 3.
- **Performance é requisito (skill `wa-performance`, leia o `SKILL.md`):**
  - o motor não muda: nada no laço, nada por tick além do que a amostra do Tablet já faz;
  - o índice só lê as máquinas no aviso de mudança do roteador;
  - o servidor manda ao cliente só as linhas à vista e só quando mudam; com o Tablet fechado, nada;
  - a vazão por máquina é calculada só para as linhas de máquina da página de alguém, uma vez por amostra, compartilhada pelo `TabletSample`;
  - por quadro, só desenhar: as linhas e os textos são montados quando a página muda, e o layout usa o `FrameCache`;
  - remota nunca é consultada sem `level.isLoaded(pos)`;
  - antes do commit, rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados, com cada candidato resolvido ou justificado no relatório.
- **Telas:**
  - telas só em `client/`, nunca referenciadas por código comum;
  - cores pelo `GuiPaint`;
  - texto variável pelo `GuiText` (o e2e confere `GuiText.clipCount()==0`);
  - tema claro e escuro;
  - não poluir a tela: o detalhe vai na dica.
- **Tamanho (`wa-modular`):**
  - a Tarefa 1 divide o `RouterBlockEntity` (997), a `TabletList` (612) e a `TabletScreen` (686) antes de tudo;
  - depois dela, nenhum desses três, nem o `TabletMenu` (664), o `TabletSnapshot` (723) e o `NodeIndex` (602), cresce além de um repasse: o que é novo vai em classe própria;
  - antes do commit, `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados.
- **Comentários (`wa-comentarios`):** `python -I .claude/skills/wa-comentarios/scripts/varrer.py` antes do commit.
- **GameTests:**
  - `@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)` e template `"empty"`;
  - cada teste cria a própria rede;
  - teste pertinência, não contagem global (o `NodeIndex` e o `TabletSample` são globais);
  - jogador só por `TestPlayers`;
  - espere o `onLoad` com `thenWaitUntil`;
  - redes com `Objects.equals`;
  - `timeoutTicks` de no máximo 300.
- **Verificação:**
  - no fim da tarefa: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`;
  - nas tarefas de tela, também o e2e, rodado sozinho, sem outro cliente aberto na mesma `run/`: `WA_E2E="$PWD/run/e2e" ./gradlew runClient --console=plain > run/e2e/client.log 2>&1`, com `run/e2e/result.txt` começando por `OK`. Olhe as capturas;
  - compile antes (`./gradlew compileJava`);
  - um Gradle ou jogo por vez;
  - não mexa em `run/options.txt`.
- **Commits:** um por tarefa, sem linhas de atribuição, sem push.

## Mapa de arquivos

| Arquivo | Tarefa | Papel |
| --- | --- | --- |
| `block/RouterBlockEntity.java` e uma parte nova de pacote, `client/tablet/TabletList.java`, `client/tablet/TabletScreen.java` e partes novas, `bench/` (cenário `tablet-range`) | 1 | Divisões sem mudar o jogo; cenário de bench |
| `network/NodeIndex.java` e uma parte nova, como `network/IndexRemotes.java` (as máquinas do nó e o NBT compacto) | 2 | Máquinas no índice e filtro por tipo |
| `menu/` partes novas (`TabletTree`: o estado da árvore e a montagem das linhas; a vazão por máquina no `TabletSample` ou numa parte dele), `menu/TabletSnapshot.java` (as linhas da página e o codec), `packet/TabletTreePayload.java` (novo) | 3 | A página com as máquinas |
| `client/tablet/` partes novas para as linhas da árvore (modelo puro com JUnit, desenho, cliques e teclado) | 4 | A árvore na Lista |
| `menu/TabletMenu.State`, `menu/TabletPlayers` | 5 | Lembrar ao reabrir |
| `client/tablet/MapTab.java` e uma parte nova se precisar | 6 | O Mapa com as máquinas |
| `docs/benchmark.md`, `scripts/guide/gerar_guia.py`, `docs/especificacao.md`, `CLAUDE.md`, `docs/progresso.md` | 7 | Bench e docs |

---

### Tarefa 1: divisões e o cenário de bench

**Custo:** nenhum; o jogo não muda. **Prova:** as capturas do e2e iguais pelo `comparar.py` e os GameTests verdes.

1. **Divisões** (siga o roteiro da skill `wa-modular`: medir, planejar as partes, extrair uma por vez):
   - **`RouterBlockEntity`:** tire um pedaço coeso para uma parte de pacote em `block/`, no padrão das partes que já existem (`RouterFaces`, `RouterCards`, `RouterRange`…), até ficar com folga para as próximas etapas (alvo ≤ 900).
   - **`TabletList` e `TabletScreen`:** tire partes para elas ficarem perto de 500, pensando que a Tarefa 4 vai trocar o modelo de linhas da Lista. Deixe o modelo e o desenho das linhas numa parte que a Tarefa 4 possa substituir.
2. **Bench:** cenário `tablet-range`, como o `tablet`, mas com N roteadores de alcance (padrão 100, tier Ultimate), cada um com 16 máquinas vinculadas num grupo e um par origem/destino movendo itens. Um jogador falso tem o Tablet aberto e, quando a árvore existir (Tarefa 3), abre 4 roteadores e um grupo com "Mostrar todas". Nesta tarefa o cenário só abre o Tablet: esse é o "antes". Mede o tempo do Tablet e os KB/s, como o `tablet`. Atualize a tabela de cenários do `docs/benchmark.md`.
3. **e2e:** rode antes de mudar (`run/e2e-antes`) e depois (`run/e2e`), e compare com `python -I .claude/skills/wa-modular/scripts/comparar.py run/e2e-antes run/e2e`. Só pode mudar o que anima (o mundo e as vazões).
4. **Commit:** `Divisões antes da etapa 3a (RouterBlockEntity, TabletList, TabletScreen) e o cenário tablet-range`.

### Tarefa 2: as máquinas no índice e o filtro por tipo

**Custo:** a leitura das máquinas só no `Entry.read` (no aviso de mudança); ~12 B por máquina no save, só as entradas que mudaram regravadas. **Prova:** GameTests; o heap e o tamanho do arquivo no bench da Tarefa 7.

1. **`Entry`** guarda as máquinas e os grupos como na spec, em arrays (`long[]`, `int[]`, `String[]` esparso, `BitSet` ou `int[]` das exceções) e numa lista pequena de grupos, numa parte nova (`IndexRemotes`). O `Entry.read` compara e só marca mudado se algo mudou. Teto de 512 máquinas; guarde também a contagem total.
2. **NBT** com as chaves da spec, só quando há máquinas. O formato antigo lê sem máquinas. A tag guardada (o cache da R3) é refeita só quando a entrada muda.
3. **`roles` e `faceModes`** juntam a presa, os perfis dos grupos e as exceções. O lado de uma máquina remota segue a frente dela (`RemoteSide`); o lado do `faceModes` é o lado relativo, igual para todos.
4. **API de leitura** para a Tarefa 3: as máquinas e os grupos de uma `Entry`, sem cópia, só leitura.
5. **GameTests:**
   - ida e volta do NBT, inclusive o formato antigo;
   - teto de 512;
   - vincular, desvincular, renomear e mudar o grupo atualizam a entrada e sobem a `dataVersion`;
   - um roteador que só extrai das remotas aparece no filtro "Só Itens" da consulta do Tablet.
6. **Commit:** `Índice de nós: as máquinas vinculadas, compactas, e o filtro por tipo com elas`.

### Tarefa 3: a página com as máquinas

**Custo:** a montagem da árvore só para os roteadores abertos de quem olha. A vazão por máquina só para as linhas à vista, uma vez por amostra. Os bytes só das linhas à vista, e o "live" pequeno a cada amostra. **Prova:** GameTests e o `tablet-range` antes e depois.

1. **`TabletTreePayload`** e o estado da árvore numa parte nova de `menu/` (`TabletTree`), conforme a spec: ações, validação da visibilidade e limites de 64 abertos e 256 grupos. Cada ação refaz só a página daquele jogador.
2. **A página como lista de linhas**, num tipo selado ou com um campo de tipo, com o nó de hoje mais as linhas de presa, grupo, máquina e "mais N". A ordem é a da tela, montada a partir do índice (Tarefa 2) para os nós abertos.
   - O limite de 256 conta linhas.
   - O codec continua com a paleta: os blocos dos grupos entram na paleta de máquinas. As posições das máquinas vão como `long`.
   - O `sameExceptLive` cobre as linhas novas.
3. **O "live" por linha,** na mesma ordem. Para as linhas de máquina: o estado da spec e a vazão por tipo pela diferença dos totais das portas da máquina (`gaveRead + gave`, `gotRead + got`) entre amostras.
   - Feito no `TabletSample` (ou numa parte dele) só para as máquinas que estão na página de alguém. Junte o conjunto das máquinas à vista de todos os Tablets abertos, para medir uma vez só.
   - Com o roteador descarregado: "descarregada", sem vazão.
4. **`ModPayloads.VERSION = "24"`.** Atualize o `BenchScene.encodedSize`, se o payload novo entrar na soma, e o cenário `tablet-range` para abrir 4 roteadores e um grupo com "Mostrar todas".
5. **GameTests:**
   - abrir, recolher e "mostrar todas" mudam a página, e só a daquele jogador;
   - nó invisível recusado;
   - os limites;
   - a ordem das linhas;
   - o limite de 256 contando linhas;
   - o "live" de uma máquina movendo (com vazão > 0), com a entrada cheia e descarregada;
   - o `TabletLivePayload` sai sozinho quando só o estado ou a vazão mudam.
6. **Commit:** `Tablet: as linhas das máquinas na página, para os roteadores abertos, com estado e vazão`.

### Tarefa 4: a árvore na Lista

**Custo:** nada por quadro além de desenhar. As linhas e os textos são montados quando a página muda, e o layout fica no `FrameCache`. **Prova:** e2e e `varrer_perf.py`.

1. **O modelo das linhas no cliente,** puro e com JUnit, substitui o `ListGroups` de um nível. Ele conhece os grupos de máquina (o cabeçalho de hoje) e, dentro deles, os nós, as presas, os grupos de alcance, as máquinas e o "mais N". Também guarda a navegação ↑ ↓ ← →.
2. **O desenho** das linhas como na spec e no mockup: indentação, ▸/▾, o nome, a segunda linha, a vazão na cor do tipo, a marca "exceção" e o Olho ◉ (o `MachineHighlights` da etapa 2). Uma parte por responsabilidade, sem crescer `TabletList` nem `TabletScreen`.
3. **Cliques:**
   - ▸/▾ manda o `TabletTreePayload`;
   - a linha da máquina escolhe o nó e o alvo `MACHINE` (`TabletSelectNodePayload` mais o `SelectTargetPayload` do painel, ou um caminho só, se for mais simples e seguro: diga qual);
   - a presa escolhe o alvo da presa, e o grupo, o alvo `GROUP`;
   - "Mostrar todas" e o Olho.
4. **Teclado:** ↑ ↓ pelas linhas, ← → abrem e fecham, Enter escolhe.
5. **e2e:**
   - abrir um roteador de alcance na Lista, ver os grupos e as máquinas, escolher uma máquina e conferir o alvo do painel;
   - "Mostrar todas";
   - o Olho na linha;
   - as capturas `7l-tablet-arvore`, `7l-tablet-arvore-escuro`, `7l-tablet-arvore-pt` e `7m-tablet-mostrar-todas`;
   - conferir que nada corta.
6. **Commit:** `Tablet: a Lista em árvore, com as máquinas de cada roteador`.

### Tarefa 5: lembrar ao reabrir

**Custo:** alguns bytes por jogador na memória do servidor e uma vez na abertura. **Prova:** GameTests e e2e.

1. **Os três conjuntos da árvore** entram no `TabletMenu.State`. São gravados ao fechar e restaurados ao abrir, junto do que já é, e vão no buffer de abertura para o cliente desenhar certo no primeiro quadro.
2. **GameTests:** fechar e reabrir mantém os abertos, os recolhidos e o "mostrar todas"; o logout limpa.
3. **e2e:** fechar e reabrir o Tablet com um roteador aberto, e a árvore continua aberta.
4. **Commit:** `Tablet: lembra os roteadores abertos e os grupos recolhidos`.

### Tarefa 6: o Mapa com as máquinas

**Custo:** 0 no servidor e na rede: as posições já vêm no `RangeView` do painel. Por quadro, só desenhar, com os pontos calculados quando o painel ou a vista mudam. **Prova:** e2e.

1. **No `MapTab`,** ou numa parte nova: o roteador escolhido desenha as máquinas dele.
   - Cada máquina é um quadradinho, ligado ao roteador por uma linha.
   - As acesas pelo Olho ganham o contorno ciano.
   - Clicar numa máquina a escolhe como alvo do painel, e a faixa de info mostra o nome e as coordenadas dela.
   - Só a dimensão do roteador.
   - Os outros roteadores ficam como hoje.
2. **e2e:** o Mapa com um roteador de alcance escolhido (`7n-tablet-mapa-maquinas`) e o clique numa máquina.
3. **Commit:** `Tablet: o Mapa mostra as máquinas do roteador escolhido`.

### Tarefa 7: bench e documentação

1. **Bench:** `./scripts/bench.sh tablet:1000:3,tablet-range:100:3,range:200:3,many:500:3`, antes (na base da Tarefa 1) e depois, alternados. Anote também o tamanho do `wirelessautomate_nodes.dat` no cenário `range`. Registre a tabela no `docs/benchmark.md`. Se algo subir além do ruído, pare e avise.
2. **Guia,** pelo `gerar_guia.py`, nos dois idiomas.
3. **Docs:**
   - a especificação (Tablet e índice);
   - o `CLAUDE.md` (classes novas, protocolo `24`, contagens);
   - o `progresso.md` (estado, próximo passo, que é a 3b, e o histórico).
4. **Revisão final do ramo,** verificação completa e commit `Docs: etapa 3a da central da rede (a Lista com as máquinas)`.
