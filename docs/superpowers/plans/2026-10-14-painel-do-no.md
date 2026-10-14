# Painel do nó (etapa 1) · plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (um subagente por tarefa, revisão entre elas). Os passos usam caixas (`- [ ]`).

**Objetivo:** a aba Lista do Tablet vira um painel estilo XNet (busca única com sugestões e etiquetas, filtros rápidos, lista agrupada por máquina com as faces, o nó escolhido editável ao lado, copiar/colar perfil com desfazer) e a tela do roteador usa o mesmo painel do nó; mais as regras D1 (modo põe a face na rede ativa) e D5 (filtro à distância).

**Arquitetura:** no servidor, uma interface `RouterEditHost` deixa os payloads de face agirem pela tela do roteador ou pelo Tablet (nó escolhido); o Tablet manda o `RouterSnapshot` do nó escolhido; o índice ganha os modos por face; a consulta ganha texto, etiquetas e filtros rápidos; o perfil fica no item Tablet, com colar extraído do Configurador e desfazer no menu. No cliente, o `NodePanel` é um componente usado pelas duas telas, com as partes puras (`FaceNet`, `TabletSearch`, `ListGroups`) testadas por JUnit.

**Tecnologia:** NeoForge 1.21.1, Java 21, JUnit 5, GameTests, e2e (`DevEndToEnd`).

**Spec:** `docs/superpowers/specs/2026-10-14-painel-do-no-design.md` — é a fonte dos textos, do comportamento e das regras; cada tarefa cita as seções dela. **Mockup:** `docs/preview/tablet-painel.html` (v3). **Investigações do código** (leia a parte da sua tarefa antes de começar): `.superpowers/sdd/2026-10-14-painel-do-no/investiga-tablet.md` e `.superpowers/sdd/2026-10-14-painel-do-no/investiga-roteador.md`.

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas (o texto em português é o da spec; o inglês, a tradução natural).
- **Lado do cliente:** telas e componentes só em `client/`, nunca referenciados por código comum (o `runGameTestServer` é um servidor dedicado).
- **Cores** só pelos tokens do `GuiPaint` (lidos na hora de desenhar; nenhum campo guarda cor; `EditBox` pela `GuiPaint.ink` quando `GuiThemes.version()` muda). **Texto variável** sempre pelo `GuiText` (abrevia com dica do texto inteiro).
- **Performance:** nada de tick por bloco; capability só por `BlockCapabilityCache`; nada de sincronizar o cliente com a tela fechada; nada de carregar chunk à força (confira `level.isLoaded` antes de ler um roteador ou máquina); o painel do Tablet só é enviado com o Tablet aberto e um nó escolhido.
- **Validação no servidor:** toda ação vinda do Tablet confere, nesta ordem, o menu aberto com aquele `containerId` e `stillValid`, o nó visível para o jogador (`TabletMenu.visible`), o roteador carregado (`loadedRouter`, sem carregar chunk) e o alcance (`inReach`); e a regra das redes (`canUse`/`canManage`, `PresetApplier`). Listas de chaves vindas do cliente: no máximo 256.
- **Tipos:** sempre `LoadedTypes.LIST` (Químicos só com o Mekanism, Source só com o Ars), nunca `ResourceType.values()` para o que aparece ao jogador. Tipos do Mekanism e do Ars nunca na assinatura de métodos de classes `@EventBusSubscriber` ou `@GameTestHolder`.
- Chaves existentes de NBT, componentes e config **não mudam**; chaves novas: NBT `face_modes` no índice; componente `wirelessautomate:profile_source`. Mundos antigos têm que carregar (ausente = vazio).
- `ModPayloads.VERSION` passa de `"15"` para `"16"` na primeira tarefa que mudar um payload (a 3) e não muda de novo depois.
- Comentários pela skill `wa-comentarios` (no presente, sem histórico nem código comentado); antes de cada commit, `python -I .claude/skills/wa-comentarios/scripts/varrer.py` e resolva os candidatos nos seus arquivos.
- Commits sem linhas de atribuição (`Co-Authored-By`, `Claude-Session`), com o autor do dono (o `git config` já está certo). Sem push.
- Compile antes de rodar jogo (`./gradlew compileJava`); com `BUILD FAILED`, conserte, nunca espere. Nunca dois jogos ou servidores ao mesmo tempo (~3 GB cada). Não mexa em `run/options.txt`; `run/config/wirelessautomate-client.toml` termina em `LIGHT`.
- Verificação antes de cada commit (Git Bash, na raiz): `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`; nas tarefas que mexem em tela ou payload, também o e2e `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (minutos; em segundo plano; `run/e2e/result.txt` com `OK`). Se o e2e falhar num passo que a tarefa não toca (já houve falhas intermitentes em "filtro: aumentar a janela" e na roda do Configurador), rode de novo e anote no relatório.
- Ramo: `tema-escuro` (a remodelagem sai junta na 2.0.0).

## Mapa de arquivos

| Arquivo | Tarefa |
| --- | --- |
| `menu/RouterEditHost.java` (novo), `menu/RouterMenu.java`, `menu/RemoteRouterMenu.java`, `packet/ModPayloads.java` | 1 |
| `packet/PanelNoticePayload.java` (novo), `packet/ModPayloads.java`, `client/PanelNotices.java` (novo) | 2 |
| `network/NodeIndex.java`, `menu/TabletSnapshot.java`, `menu/TabletMenu.java`, `packet/Tablet*Payload.java`, `client/TabletScreen.java` (adaptação mínima), `gametest/TabletGameTests.java`, `client/DevScreenshot.java`, `client/DevEndToEnd.java` | 3 |
| `menu/TabletMenu.java`, `packet/TabletSelectNodePayload.java`, `packet/TabletPanelPayload.java` (novos), `packet/TabletPayloads.java` | 4 |
| `preset/PresetPaste.java`, `preset/ProfileDiff.java` (novos), `item/ConfiguratorItem.java`, `network/RouterPreset.java`, `registry/ModDataComponents.java`, `menu/TabletMenu.java`, `packet/TabletProfilePayload.java` (novo) | 5 |
| `menu/RouterFaceFilterTarget.java`, `packet/ModPayloads.java`, `menu/TabletMenu.java` | 6 |
| `client/FaceNet.java` (novo, JUnit), `client/NodePanel.java` (novo), `client/RouterScreen.java`, `client/MachineView3D.java` | 7 |
| `client/TabletSearch.java`, `client/ListGroups.java` (novos, JUnit), `client/TabletScreen.java` | 8 |
| `scripts/guide/gerar_guia.py`, `docs/especificacao.md`, `CLAUDE.md`, `docs/progresso.md` | 9 |

---

### Tarefa 1: `RouterEditHost` — os payloads de face pela tela do roteador ou por outro host

**Arquivos:** criar `menu/RouterEditHost.java`; modificar `menu/RouterMenu.java`, `menu/RemoteRouterMenu.java` (se preciso), `packet/ModPayloads.java`; testes em `gametest/` (os de `RouterMenuGameTests`, `RangeGameTests`, `FaceNetworkGameTests` continuam passando).

**Interfaces produzidas:**
- `public interface RouterEditHost` com, no mínimo: `int containerId()`, `@Nullable RouterBlockEntity editedRouter()`, o alvo de edição atual (o que os handlers hoje leem do `RouterMenu`: `EditTargetRef`/`EditTarget` e a face escolhida, se houver), `boolean canEdit(ServerPlayer player)` (na tela do roteador: o `stillValid` de hoje; o Tablet implementa na Tarefa 4), e um gancho para avisar que algo mudou (o que hoje força o reenvio do snapshot da tela do roteador).
- Um ponto único em `ModPayloads` que acha o host: `static @Nullable RouterEditHost host(ServerPlayer player, int containerId)` (o `player.containerMenu` com aquele id, se for `RouterEditHost` e `canEdit`).

**Passos:**
- [ ] Leia em `investiga-roteador.md` as seções 1.2 (payloads e handlers) e 2; liste todos os handlers que hoje exigem `player.containerMenu instanceof RouterMenu` (mode/prioridade/redstone pelo `SetFacePayload`, rede pelo `SetNetworkPayload`, escolher alvo, voltar ao perfil, desvincular, abrir filtro, renomear e o que mais houver).
- [ ] Escreva um GameTest novo (`RouterEditHostGameTests`) com um host de teste que não é `RouterMenu` (uma classe só de teste, no pacote `gametest`, que implementa `RouterEditHost` apontando um roteador) e confira que `SetFacePayload` e `SetNetworkPayload` aplicados por ele mudam a face. Rode e veja falhar (o handler recusa por não ser `RouterMenu`). Se o handler precisar de `player.containerMenu`, abra o menu de teste no jogador falso do GameTest como os testes de menu existentes fazem.
- [ ] Crie a interface, faça o `RouterMenu` implementar (sem mudar comportamento) e troque os handlers para o `host(...)`. O formato dos payloads não muda (o `VERSION` continua `"15"` nesta tarefa).
- [ ] Rode a verificação completa (sem e2e: nenhuma tela muda). Commit: `Painel do nó: RouterEditHost para os payloads de face`.

---

### Tarefa 2: D1 — escolher o modo põe a face na rede ativa, e o aviso com desfazer

**Arquivos:** `packet/ModPayloads.java` (handler do `SetFacePayload`); criar `packet/PanelNoticePayload.java` e `client/PanelNotices.java`; traduções; GameTests.

**Spec:** seção "Regra D1".

**Interfaces produzidas:**
- `PanelNoticePayload` (servidor → cliente): a mensagem (`Component`) e o que o Desfazer faz: `NONE`, `UNDO_PASTE` (Tarefa 5) ou `CLEAR_FACE_NETWORK` com o tipo e o lado da face (para o cliente mandar o `SetNetworkPayload` sem rede naquela face). Registrado no `ModPayloads`.
- `client/PanelNotices`: guarda o último aviso com o instante; `current()` devolve o aviso dos últimos 5 s ou nada; as telas (Tarefas 7 e 8) desenham. Nesta tarefa, nenhuma tela desenha ainda.

**Passos:**
- [ ] GameTests (novo arquivo `ActiveNetworkModeGameTests`): (a) presa, face sem rede, modo Nenhum → Insere com rede ativa usável: a face fica com Insere e a rede ativa, numa mudança só; (b) sem rede ativa: fica Insere e sem rede; (c) rede ativa que o jogador não pode usar (privada de outro, sem ser operador): fica sem rede; (d) face já com rede: trocar o modo não muda a rede; (e) voltar para Nenhum não tira a rede; (f) roteador de alcance editando um grupo e uma máquina: a rede entra no perfil ou na exceção sem criar duas exceções. Cada teste cria a própria rede e põe como ativa do jogador falso pelo `NetworkSavedData`. Rode e veja falhar.
- [ ] Implemente no handler do `SetFacePayload` (só quando o modo sai de Nenhum para outro e a face está sem rede), na mesma edição (`batchChanges` na presa; uma edição só no alvo do alcance). Mande o `PanelNoticePayload` com "<Face> entrou em <rede>" e `CLEAR_FACE_NETWORK`.
- [ ] Traduções do aviso e das mensagens "Escolha a rede desta face" / "Você não tem uma rede ativa. Com uma, a face entra sozinha." (usadas na Tarefa 7).
- [ ] Verificação completa, mais o e2e (payload novo). Commit: `Painel do nó: o modo numa face sem rede põe a face na rede ativa`.

---

### Tarefa 3: índice com os modos por face e a consulta nova do Tablet

**Arquivos:** `network/NodeIndex.java`, `menu/TabletSnapshot.java`, `menu/TabletMenu.java`, `packet/TabletQueryPayload.java` (e o que carregar o `Query`), `packet/ModPayloads.java` (`VERSION = "16"`), `client/TabletScreen.java` (adaptação mínima para compilar e funcionar), `client/DevScreenshot.java`, `client/DevEndToEnd.java`, `gametest/TabletGameTests.java`.

**Spec:** "Lista do Tablet" (busca, filtros rápidos, agrupamento, limite 256) e "Arquitetura › Servidor" (Índice, Consulta).

**Interfaces produzidas:**
- `NodeIndex.Entry.faceModes()` (`long`: para cada tipo na ordem do `ResourceType` e cada `RelativeSide` na ordem do enum, 2 bits com o ordinal do `PortMode`), salvo na chave `face_modes`; mundos sem a chave leem 0.
- `TabletSnapshot.NodeView` ganha `long faceModes` e um campo para a comparação com o perfil (preenchido na Tarefa 5; aqui sempre "sem perfil").
- `TabletSnapshot.Query` passa a ser: `String text` (≤ 64), `List<ResourceLocation> textMachines` (≤ 32), `List<ResourceLocation> machines` (≤ 8), `List<UUID> networks` (≤ 8), `Set<QuickFilter> quick` (enum `QuickFilter { PROBLEM, NO_NETWORK, UNLOADED }`), `Optional<ResourceType> type`. Some `RoleFilter` e `page`.
- O cabeçalho do snapshot ganha `List<Facet>` das máquinas (id do bloco, contagem) e das redes (id, contagem) entre os nós visíveis, e as contagens dos três filtros rápidos (entre os nós visíveis, independente da busca).
- A página vira o resultado: até `MAX_NODES = 256` nós, ordenados por id da máquina e depois nome (sem diferenciar maiúsculas), e o total que casou.

**Regras da busca no servidor:** um nó casa se passa em todas as etiquetas (`machines`: o id da máquina está na lista, se houver; `networks`: alguma face do nó numa delas, se houver), no tipo (se houver), nos filtros rápidos (todos os ligados; PROBLEM = descarregado, sem rede ou destino cheio) e no texto: cada palavra do texto casa com o nome, o id da máquina, as coordenadas, o nome de uma rede do nó, **ou** o id da máquina está em `textMachines`.

**Passos:**
- [ ] Reescreva os testes do Tablet que usam `RoleFilter`/página para a consulta nova e acrescente: texto que casa pelo nome, pelo id e por `textMachines`; etiquetas de máquina e de rede somadas; cada filtro rápido; ordem por máquina e nome; limite 256 com o total; facetas e contagens; `face_modes` ida e volta pelo `save`/`load` e um índice salvo sem a chave (mundo antigo); `faceModes` acompanha a mudança de modo de uma face. Rode e veja falhar.
- [ ] Implemente. O `probeAll` das contagens roda no mesmo ritmo da amostra de hoje (só com o Tablet aberto).
- [ ] `TabletScreen`: adaptação mínima para a consulta nova, sem o redesenho (Tarefa 8): a caixa de busca manda `text`, o seletor de papel e os botões de página saem, o chip de tipo continua. Ajuste `DevScreenshot.sampleTablet` e os passos do e2e do Tablet que usavam papel ou página.
- [ ] Verificação completa e e2e. Commit: `Painel do nó: modos por face no índice e a consulta nova do Tablet`.

---

### Tarefa 4: o nó escolhido no Tablet e a edição à distância pelo painel

**Arquivos:** `menu/TabletMenu.java`; criar `packet/TabletSelectNodePayload.java` (cliente → servidor) e `packet/TabletPanelPayload.java` (servidor → cliente); `packet/TabletPayloads.java`; `client/TabletScreen.java` (só guardar o painel recebido e expor um gancho de teste; o desenho é a Tarefa 8); GameTests.

**Spec:** "Painel do nó" (só leitura fora do alcance) e "Arquitetura › Servidor" (nó escolhido).

**Interfaces consumidas:** `RouterEditHost` (Tarefa 1).

**Interfaces produzidas:**
- `TabletMenu implements RouterEditHost` para o nó escolhido: `canEdit` = escolhido, visível, carregado e `inReach`. O alvo de edição (presa, grupo, máquina) do nó escolhido vive no `TabletMenu`, como no `RouterMenu`.
- `TabletSelectNodePayload(containerId, Optional<NodeKey>)`.
- `TabletPanelPayload(containerId, Optional<RouterSnapshot> snapshot, PanelAccess access)`, com `PanelAccess` = `EDIT`, `READ_UNLOADED`, `READ_OUT_OF_REACH` (e a distância/alcance para a mensagem). Enviado só com o Tablet aberto e um nó escolhido: ao escolher, quando o `changeVersion` do roteador muda e a cada 20 ticks; sem reenviar o que não mudou. Fora do alcance ou descarregado: o último snapshot conhecido com o acesso de leitura (ou vazio, se nunca houve).
- Gancho no cliente: `TabletScreen.panel()` (snapshot e acesso atuais).

**Passos:**
- [ ] GameTests: escolher um nó manda o painel; `SetFacePayload` com o `containerId` do Tablet muda a face do nó escolhido; fora do alcance a ação é recusada e o acesso diz `READ_OUT_OF_REACH`; descarregado diz `READ_UNLOADED` e não carrega o chunk; nó de outro jogador que não é visível não pode ser escolhido; trocar o alvo (grupo do alcance) pelo Tablet funciona. Rode e veja falhar.
- [ ] Implemente, reaproveitando `RouterSnapshot.capture` e o codec dele.
- [ ] Verificação completa e e2e. Commit: `Painel do nó: o nó escolhido no Tablet e a edição à distância`.

---

### Tarefa 5: perfil no Tablet — copiar, colar (um, grupo, marcados), desfazer e "igual ao perfil"

**Arquivos:** criar `preset/PresetPaste.java` e `preset/ProfileDiff.java`; modificar `item/ConfiguratorItem.java` (passa a usar o `PresetPaste`, sem mudar o comportamento do Configurador), `network/RouterPreset.java` (`restoreTo`), `registry/ModDataComponents.java` (`profile_source`), `menu/TabletMenu.java`; criar `packet/TabletProfilePayload.java`; `menu/TabletSnapshot.java` (perfil no cabeçalho e a comparação por nó); traduções; GameTests.

**Spec:** "Copiar, colar e desfazer (Tablet)" e "Arquitetura › Servidor" (Perfil, Colar, Comparação, Aviso).

**Interfaces consumidas:** Tarefas 2 (aviso), 3 (consulta), 4 (nó escolhido).

**Interfaces produzidas:**
- `PresetPaste.copy(ServerPlayer, RouterBlockEntity) -> RouterPreset` e `PresetPaste.paste(ServerPlayer, RouterBlockEntity, RouterPreset, @Nullable ResourceType only) -> PresetApplier.Checked` (a regra das redes e o aviso de faces mantidas como hoje no Configurador).
- `RouterPreset.restoreTo(RouterBlockEntity)`: deixa as faces exatamente como no preset, inclusive redes nulas (o `applyTo` não tira rede).
- `ProfileDiff.firstDifference(RouterPreset profile, RouterPreset node) -> Optional<Difference>`, `Difference(ResourceType type, RelativeSide side, What what)`, `What { MODE, NETWORK, FILTER, PRIORITY, REDSTONE }`; percorre `LoadedTypes.LIST` e os lados em ordem fixa; igual = nenhuma diferença.
- Componente `wirelessautomate:profile_source` (nome do nó, id do bloco da máquina); o perfil no `wirelessautomate:preset` do item Tablet.
- `TabletProfilePayload(containerId, Action, Optional<NodeKey> key, Optional<ResourceLocation> machine, List<NodeKey> keys ≤ 256)` com `Action { COPY, FORGET, PASTE_ONE, PASTE_GROUP, PASTE_SELECTED, UNDO }`. `PASTE_GROUP` reavalia no servidor a consulta atual e cola em todos os nós daquela máquina que casam, menos a origem (se ela estiver entre eles).
- Desfazer: o `TabletMenu` guarda, do último colar, `NodeKey → RouterPreset` de antes; `UNDO` aplica `restoreTo` em cada um que ainda está visível, carregado e ao alcance, e limpa. Some ao fechar o Tablet.
- O cabeçalho do snapshot leva o perfil guardado (nome de origem, máquina, um resumo: tipos e número de faces com modo) e cada `NodeView` carregado leva o resultado do `ProfileDiff` (igual, diferença, ou sem comparação).
- Aviso pelo `PanelNoticePayload` com `UNDO_PASTE`: "Perfil colado em <nó>" ou "Perfil colado em N nós · M ficaram de fora (descarregados ou fora do alcance)"; e "K faces ficaram com a rede que já tinham" quando o `PresetApplier` manteve redes.

**Passos:**
- [ ] GameTests (novo `TabletProfileGameTests`, e os do Configurador continuam passando): copiar grava os componentes no Tablet do jogador; colar num nó aplica; colar no grupo pula a origem e os descarregados e diz quantos; colar nos marcados com 257 chaves é recusado; rede que o jogador não pode usar fica como estava; desfazer devolve modo, filtro e redes (inclusive voltar a sem rede); desfazer depois de fechar o Tablet não faz nada; `ProfileDiff` acha a diferença de modo, de rede e de filtro, e diz igual para cópias; o `NodeView` traz a comparação só com perfil guardado. Rode e veja falhar.
- [ ] Implemente. O Configurador continua igual (os testes dele provam).
- [ ] Verificação completa e e2e. Commit: `Painel do nó: copiar e colar perfil pelo Tablet, com desfazer`.

---

### Tarefa 6: D5 — filtro à distância e o Voltar para a origem

**Arquivos:** `menu/RouterFaceFilterTarget.java`, `packet/ModPayloads.java` (abrir filtro e Voltar), `menu/TabletMenu.java` (guardar e restaurar o estado do Tablet por jogador na sessão), `client/TabletScreen.java` (reaplicar o estado restaurado ao abrir); GameTests.

**Spec:** "Regra D5". Leia `investiga-roteador.md` seção 3.

**Interfaces produzidas:**
- O alvo de filtro de face guarda a origem: `NEAR` (tela do roteador perto), `REMOTE_ROUTER` (tela do roteador aberta pelo Tablet), `TABLET` (painel do Tablet); o `stillValid` confere a distância de 8 blocos só em `NEAR`; nas outras, o `inReach` do Tablet e o Tablet no inventário.
- Voltar: `NEAR` e `REMOTE_ROUTER` reabrem a tela do roteador como hoje (a remota pelo `RemoteRouterMenu`); `TABLET` reabre o Tablet com a aba Lista, a consulta, o nó escolhido e o alvo de edição de antes.
- O `TabletMenu` guarda por jogador (mapa na memória do servidor, só na sessão) a última consulta, a aba e o nó escolhido; ao abrir o Tablet, ele começa desse estado e o manda no buffer de abertura.

**Passos:**
- [ ] GameTests: filtro aberto pelo Tablet a 40 blocos com um Elite continua válido; a 40 blocos aberto da tela do roteador perto (origem `NEAR`, jogador teleportado) fecha; Voltar com origem `TABLET` reabre o `TabletMenu` com o nó escolhido; o estado restaurado tem a consulta de antes. Rode e veja falhar.
- [ ] Implemente.
- [ ] Verificação completa e e2e (o passo do e2e que abre o filtro de longe entra na Tarefa 8; aqui basta não quebrar os que existem). Commit: `Painel do nó: filtro à distância e Voltar para a origem`.

---

### Tarefa 7: o `NodePanel` e a tela do roteador

**Arquivos:** criar `client/FaceNet.java` (+ `src/test/java/.../client/FaceNetTest.java`) e `client/NodePanel.java`; modificar `client/RouterScreen.java`, `client/MachineView3D.java` (se preciso para a sincronia), `client/DevScreenshot.java`, `client/DevEndToEnd.java`; traduções.

**Spec:** "Painel do nó" e "Tela do roteador"; mockup v3, Tela 4 e "Casos que pedem cuidado". Leia `investiga-roteador.md` seções 1 e 7.

**Interfaces consumidas:** `RouterSnapshot` (como hoje), os payloads de face (Tarefa 1), `PanelNotices` (Tarefa 2).

**Interfaces produzidas:**
- `FaceNet` (lógica pura, JUnit; sem classes do Minecraft — se o `RelativeSide` tiver alguma, use os nomes dos lados): a célula de cada lado na grade 4 × 3 (Cima (1,0); Esquerda (0,1), Frente (1,1), Direita (2,1), Trás (3,1); Baixo (1,2)), o lado de uma célula (ou nenhum), a ordem dos seis quadradinhos da Lista (Cima, Frente, Esquerda / Baixo, Trás, Direita) e a escolha entre nome inteiro e curto pela largura da célula.
- `NodePanel`: construído com o enviador de ações (o `containerId` e o `RouterEditHost` do lado do servidor são transparentes: ele manda os payloads de face de sempre) e o snapshot atual; `layout(x, y, w, h)`, `render(...)`, `mouseClicked`, `mouseScrolled`, `keyPressed`, `tooltip(mouseX, mouseY)`; a face escolhida e o tipo atual; `readOnly(motivo)` para o Tablet; ganchos para o e2e (centro de uma face no cubo, de um modo, da linha Rede, do Editar, de −/+, do Redstone, da linha Editando). Desenha o aviso do `PanelNotices` (com Desfazer) e as mensagens da D1 na linha Rede.
- `RouterScreen`: a coluna da direita vira o `NodePanel`; saem os seis botões de face, a linha "Frente: …" e o "Mais"; o visor 3D e o cubo andam juntos; os slots de cartão continuam embaixo do visor; o resumo do filtro conta os cartões pelos slots como hoje.

**Passos:**
- [ ] JUnit do `FaceNet` (as seis células, o lado de cada célula e de uma vazia, a ordem dos quadradinhos, o nome curto abaixo da largura). Rode e veja falhar; implemente; passe.
- [ ] `NodePanel` e a `RouterScreen`, mantendo o redimensionamento (mínimo 300 × 240) e os ganchos de teste que o e2e usa (troque os que apontavam para os botões de face e para o "Mais" pelos do painel).
- [ ] e2e: os passos da tela do roteador passam pelo painel (clicar numa face do cubo, mudar o modo, a rede, a prioridade sem "Mais", o redstone) e conferem o visor sincronizado; a D1 (escolher um modo numa face sem rede com rede ativa mostra o aviso e põe a rede; Desfazer tira); volta escura e pt_br com `clipCheck`. Refaça as capturas do `DevScreenshot` da tela do roteador e olhe as novas (claro e escuro).
- [ ] Verificação completa e e2e. Commit: `Painel do nó: o NodePanel na tela do roteador, com as faces desdobradas`.

---

### Tarefa 8: a Lista do Tablet redesenhada

**Arquivos:** criar `client/TabletSearch.java` e `client/ListGroups.java` (+ JUnit `TabletSearchTest`, `ListGroupsTest`); modificar `client/TabletScreen.java`, `client/DevScreenshot.java`, `client/DevEndToEnd.java`; traduções.

**Spec:** "Lista do Tablet", "Copiar, colar e desfazer", "Teclado"; mockup v3, Telas 1 a 3 e "Teclado".

**Interfaces consumidas:** consulta, facetas e contagens (Tarefa 3), painel e `TabletSelectNodePayload` (Tarefa 4), `TabletProfilePayload` e a comparação (Tarefa 5), estado restaurado (Tarefa 6), `NodePanel` e `FaceNet` (Tarefa 7), `PanelNotices` (Tarefa 2).

**Interfaces produzidas:**
- `TabletSearch` (pura, JUnit): `suggest(texto, facetas, nomes, porTipo)` devolve as sugestões por grupo (Máquinas, Redes, Nomes), sem diferenciar maiúsculas nem acentos, primeiro as que começam com o texto, depois por contagem e por nome, no máximo 3 por grupo, com o trecho casado (início e fim); `matchingIds(texto, facetas)` para o `textMachines`; vazio com texto vazio.
- `ListGroups` (pura, JUnit): de uma lista de ids de máquina por nó (na ordem do servidor) e do conjunto de grupos recolhidos, as linhas a desenhar (cabeçalho com id e contagem; nós), e a navegação do teclado (próxima e anterior linha de nó, pulando grupos recolhidos; o grupo da linha).
- `TabletScreen`, aba Lista: a busca com sugestões e etiquetas, os filtros rápidos, a contagem e Limpar, a lista agrupada com os quadradinhos e o estado em palavras, a faixa do perfil, "Colar nas N" nos cabeçalhos, o painel do nó à direita (com Copiar perfil / Colar perfil, Tela completa, só leitura), o aviso com Desfazer, o modo Selecionar com "Colar nos N marcados", os atalhos de teclado. Ganchos de teste: `nodeRowCenter` (continua), centro de uma sugestão, de uma etiqueta, de um filtro rápido, do "Colar nas N" de um grupo, do Copiar perfil, do Desfazer, e `panel()`.

**Passos:**
- [ ] JUnit do `TabletSearch` e do `ListGroups` (inclusive acentos, ordem, limite por grupo, grupo recolhido, navegação nas bordas). Rode e veja falhar; implemente; passe.
- [ ] A aba Lista. A busca é feita no servidor (a consulta da Tarefa 3, com debounce); as sugestões no cliente, pelas facetas e pelos nomes recebidos.
- [ ] e2e: digitar na busca mostra a sugestão da máquina, escolher vira etiqueta e filtra; um filtro rápido filtra e desliga; clicar numa linha mostra o painel; mudar um modo no painel muda o nó; Copiar perfil mostra a faixa; "Colar nas N" cola e mostra o aviso; Desfazer devolve; a linha diz "igual ao perfil"; Editar o filtro de um nó a mais de 8 blocos abre e Voltar retorna ao Tablet com o mesmo nó escolhido; duplo clique abre a tela completa; Ctrl+C/Ctrl+V/Ctrl+Z; volta escura e pt_br com `clipCheck`. Refaça as capturas do Tablet no `DevScreenshot` e olhe as novas (claro e escuro, e no tamanho mínimo).
- [ ] Verificação completa e e2e. Commit: `Painel do nó: a Lista do Tablet com busca, grupos e o painel`.

---

### Tarefa 9: guia e docs

**Arquivos:** `scripts/guide/gerar_guia.py` (páginas `network-tablet` e `router`, nos dois idiomas) e os `.md` gerados; `docs/especificacao.md` (telas do roteador e do Tablet); `CLAUDE.md` (mapa: as classes novas, `FaceNet`, `TabletSearch` e `ListGroups` na lista de lógica pura, o protocolo `16`, os números de testes); `docs/progresso.md` (etapa atual: etapa 1 de 3 pronta; próximo passo: etapa 2; histórico com os números).

**Passos:**
- [ ] Guia só com "como operar": buscar, etiquetas, filtros rápidos, grupos, o painel, copiar/colar/desfazer, a regra do modo que põe a face na rede ativa, o filtro à distância; a tela do roteador com o painel e o cubo. Rode `python scripts/guide/gerar_guia.py` e confira com `git diff --stat` que só as páginas certas mudaram (reverta PNGs que o script regerar sem mudança).
- [ ] Docs.
- [ ] Verificação completa e e2e. Commit: `Painel do nó: guia e docs`.
