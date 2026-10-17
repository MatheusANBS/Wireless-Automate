# Plano da etapa 0: correção do alcance

Spec: `docs/superpowers/specs/2026-10-17-correcao-alcance-design.md`. Ramo: `correcao-alcance` (a partir do `main`). Caminhos de código relativos a `src/main/java/io/github/matheusanbs/wirelessautomate/`.

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- Chaves existentes de NBT, componentes e config **não mudam**. Nada novo é salvo no mundo (o Desfazer vive só na memória do servidor).
- `ModPayloads.VERSION` sobe de `"16"` para `"17"` (um payload novo e um valor novo no enum `PanelNoticePayload.Undo`), e só na Tarefa 3.
- Máquinas remotas: vincular e desvincular só pelos métodos do roteador (`linkMachine`/`unlinkMachine`); nunca consultar uma remota sem `level.isLoaded(pos)` (o `MachineCaches` já faz isso).
- Performance: nada de tick por bloco; capability só por `BlockCapabilityCache`; nada de sincronizar o cliente com a tela fechada. O botão novo decide pela informação que o snapshot já traz.
- Telas só em `client/`, nunca referenciadas por código comum. Cores só pelo `GuiPaint` na hora de desenhar; texto variável pelo `GuiText` (o e2e confere `GuiText.clipCount()==0`); tema claro e escuro.
- Tamanho (`wa-modular`): nada novo em `RouterMenu` (933 linhas, já no aviso), `TabletScreen` e `RouterScreen`; o `NodePanel` (520) só delega. Antes do commit: `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados; acima de "Dividir" volta para divisão.
- Comentários (`wa-comentarios`): antes do commit, `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados, e resolver os candidatos (código comentado, comentário desatualizado como "decisão 5"/"nasce na rede ativa", histórico, redundante).
- GameTests: `@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, template `"empty"`; cada teste cria a própria rede; pertinência, não contagem global; jogador só por `TestPlayers`; `onLoad` com `thenWaitUntil`; redes comparadas com `Objects.equals`.
- Verificação: durante a tarefa, só o que a mudança toca (`./gradlew compileJava`, a run afetada). Uma vez no fim da tarefa: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`. Na Tarefa 3, também o e2e (`WA_E2E="$PWD/run/e2e" ./gradlew runClient`, `run/e2e/result.txt` = `OK`), olhando as capturas novas. Com `BUILD FAILED`, pare e conserte: nunca espere um resultado que não virá. Um Gradle/jogo por vez. Não mexa em `run/options.txt`.
- Commits: um por tarefa, sem linhas de atribuição, autor do dono; sem push.

## Mapa de arquivos

| Arquivo | Tarefa | Papel |
| --- | --- | --- |
| `network/ItemGiveBack.java` | 1 | O resgate pela máquina da porta |
| `network/NodeProbe.java`, `network/NodePorts.java` | 1 | Contar destinos cheios sem criar porta, com as remotas |
| `range/RangeLinks.java`, `range/RemoteMachines.java`, `block/RouterBlockEntity.java` (doc) | 2 | O Vinculador vincula sem rede |
| `block/RouterTargets.java` (ou parte nova de pacote) e `block/RouterBlockEntity.java` | 3 | Limpar e devolver as redes das faces sem modo de um alvo |
| `packet/ClearIdleNetworksPayload.java` (novo), `packet/ModPayloads.java`, `packet/PanelNoticePayload.java` | 3 | Payload, handler, `Undo.RESTORE_IDLE_NETWORKS`, versão 17 |
| `menu/IdleNetworkUndo.java` (novo) | 3 | O último limpar por jogador, para o Desfazer |
| `client/router/IdleNetworkLine.java` (novo), `client/router/NodePanel.java`, `client/router/PanelLayout.java`, `client/router/RouterPanelActions.java`, `client/tablet/TabletList.java`/`TabletActions.java` | 3 | O botão, a ação e o Desfazer no aviso |
| `lang/en_us.json`, `lang/pt_br.json` | 3 | Textos |
| `gametest/RangeFixGameTests.java` (novo) e os testes de alcance que mudarem | 1–3 | GameTests |
| `client/dev/E2eRangeSteps.java` | 3 | Capturas do botão e do aviso |
| `scripts/guide/gerar_guia.py` + páginas geradas, `docs/especificacao.md`, `docs/progresso.md`, `CLAUDE.md` | 4 | Documentação |

---

### Tarefa 1: resgate e destino cheio nas remotas

1. `ItemGiveBack.rescue`: trocar `destination.node.bulkItems(destination.face)` e `destination.node.items(destination.face)` por `destination.machine.bulkItems(destination.face)` e `destination.machine.items(destination.face)`. Ajustar o javadoc se ele falar da presa.
2. `NodeProbe.fullDestinations(node, type, now)`: não usar mais `ports.port(...)` (cria porta). Contar, entre as portas **já existentes** do nó naquele tipo, as que têm `destination == true` e `isFull(port, now)`: as da presa por `ports.peek(type, face)` e as remotas por um acesso novo de leitura no `NodePorts` que percorra as portas remotas existentes de um tipo sem criar nada (por exemplo `void forEachRemote(ResourceType type, Consumer<Port> action)`, sem alocar por chamada além do lambda). Remover a checagem `node.face(type, face).mode().inserts()`: o `destination` da porta já diz isso (vale para a presa e para as remotas, que têm configuração própria).
3. GameTests em `gametest/RangeFixGameTests.java` (novo, apoio em `RangeTestSupport` e `TestMachines`, que já tem o destino que mente na simulação):
   - `remoteRescueGoesToRemoteMachine`: origem e dois destinos remotos (um mentiroso, outro normal) num roteador de alcance; a sobra recusada termina no destino remoto normal, não na presa.
   - `fullRemoteDestinationCounts`: um destino remoto cheio aparece em `NodeProbe.fullDestinations`.
   - `fullDestinationsDoesNotCreatePorts`: chamar `fullDestinations` num nó sem portas criadas não cria nenhuma (conferir por um acesso de teste que já exista no `NetworkManager`, como `portOrNull`, ou um novo package-private de leitura).
4. Commit: `Alcance: resgate na máquina da porta e destino cheio com as remotas`.

### Tarefa 2: o Vinculador vincula sem rede

1. `RangeLinks`: no clique (`toggle`) e na área, chamar `router.linkMachine(pos, null)`; apagar `groupNetwork` e o que só ele usava.
2. `RemoteMachines.link` e `RouterBlockEntity.linkMachine`: o javadoc deixa de citar a "decisão 5" e diz que o Vinculador vincula sem rede; `network` continua aceito (testes e vitrine).
3. Conferir que a regra D1 vale no perfil do grupo: escolher um modo numa face sem rede, com rede ativa utilizável, põe só aquela face (aquele tipo) na rede ativa, com o `PanelNoticePayload` `CLEAR_FACE_NETWORK`. Se já há GameTest disso para grupo, só conferir; senão, criar em `RangeFixGameTests`.
4. GameTests: `linkerCreatesGroupWithoutNetwork` (clique com rede ativa: todas as faces de todos os tipos carregados do perfil sem rede) e `linkerAreaCreatesGroupWithoutNetwork` (área). Ajustar os testes existentes que esperavam o grupo na rede ativa pelo Vinculador (procure em `RangeLinkerGameTests`, `FaceNetworkGameTests`, `ActiveNetworkModeGameTests`); os que chamam `linkMachine(pos, rede)` direto continuam valendo.
5. Commit: `Vinculador: grupo novo nasce sem rede`.

### Tarefa 3: "Tirar a rede das faces sem modo"

1. **Servidor, lógica:** no roteador, pelo alvo de edição (`EditTarget`: só `GROUP` e `MACHINE` com exceção; `ATTACHED` recusa), um método que tira a rede de toda face com modo `NONE` e rede, em todos os tipos de `LoadedTypes.LIST` e lados, e devolve o que tirou (tipo, lado, rede), numa mudança só; e outro que devolve uma lista dessas, só nas faces que continuam com modo `NONE` e sem rede. Coloque na parte de pacote que já edita por alvo (`RouterTargets`) ou numa parte nova pequena, com o `RouterBlockEntity` só delegando (a API do nó). Nada de NBT novo.
2. **Desfazer:** `menu/IdleNetworkUndo.java`: o último limpar por jogador (UUID → container, dimensão e posição do roteador, alvo, lista). É apagado quando o mesmo jogador limpa de novo, quando o menu do container fecha (`removed`) e quando o servidor para (siga o padrão do `TabletPlayers.reset`/`forget`). O Desfazer só vale com o mesmo container aberto.
3. **Payload:** `packet/ClearIdleNetworksPayload(int containerId, boolean undo)`, registrado no `ModPayloads` (cliente → servidor). O handler acha o host por `ModPayloads.host(player, containerId)` e valida como os payloads de face (acesso de edição; no Tablet, `PanelAccess.EDIT`). Limpar com resultado > 0: `PanelNoticePayload` com a mensagem `"Rede tirada de %s faces sem modo"` e `Undo.RESTORE_IDLE_NETWORKS` (novo, no fim do enum); resultado 0: nada. Desfazer: devolve e manda o aviso sem Desfazer ("Redes devolvidas a %s faces"). `ModPayloads.VERSION = "17"`. `editApplied()` como as outras edições.
4. **Cliente:** `client/router/IdleNetworkLine.java` (package-private: layout, desenho, clique e dica de uma linha com o botão "Tirar a rede das faces sem modo"), usado pelo `NodePanel` logo abaixo do cubo. Aparece só se o alvo é grupo ou máquina com exceção, o painel não é só leitura e o snapshot tem ao menos uma face (qualquer tipo) com modo `NONE` e `network` presente. A ação sai pela `NodePanel.Actions` (método novo), e a tela do roteador e o Tablet mandam o payload (`RouterPanelActions`, `TabletActions`). O Desfazer do aviso com `RESTORE_IDLE_NETWORKS` manda o payload com `undo = true`, nos mesmos lugares em que hoje o `CLEAR_FACE_NETWORK` é tratado (`PanelLayout`, `TabletList`). Dica: `"Tira a rede das faces com modo Nenhum, em todas as abas. As faces que movem algo ficam como estão."`.
5. **Lang** (`wirelessautomate.` + o padrão de chaves do painel): botão, dica e os dois avisos, em en_us ("Remove network from faces with no mode", "Removes the network from faces set to None, on every tab. Faces that move something stay as they are.", "Network removed from %s faces with no mode", "Network restored to %s faces") e pt_br.
6. **GameTests** em `RangeFixGameTests`: limpar no perfil tira só as faces sem modo com rede e mantém as com modo; idem numa exceção; na presa não faz nada; o aviso leva `RESTORE_IDLE_NETWORKS`; Desfazer devolve e não sobrescreve uma face que mudou de rede ou de modo depois; pelo Tablet (`TabletMenu` como host) funciona; payload com container de outro jogador ou fechado é ignorado; Desfazer depois de fechar o menu não faz nada.
7. **e2e** (`E2eRangeSteps`): num grupo com rede em todas as abas, captura `range-6-limpar-redes` com o botão à vista, clica, captura `range-6b-limpar-aviso` com o aviso e o Desfazer, clica no Desfazer e confere que a rede voltou. Olhe as capturas (texto cortado, tema).
8. Commit: `Painel: tirar a rede das faces sem modo, com Desfazer`.

### Tarefa 4: documentação e fechamento

1. `scripts/guide/gerar_guia.py` (nunca os `.md`), nos dois idiomas: a linha do modo Máquinas do Vinculador (pt ~1124, en ~1192) passa a dizer que a máquina de um tipo novo entra sem rede e que a face entra na rede ativa ao escolher um modo; na página do roteador de alcance, o botão "Tirar a rede das faces sem modo" e o Desfazer. Rodar o gerador.
2. `docs/especificacao.md`: Vinculador (modo Máquinas) e Roteador (painel do nó, o botão). `CLAUDE.md`: mapa do código (`IdleNetworkLine`, `ClearIdleNetworksPayload`, `IdleNetworkUndo`, `RangeFixGameTests`, protocolo `17`). `docs/progresso.md`: estado, próximo passo (etapa 1 da central) e uma linha no histórico com os números de testes.
3. Verificação completa uma vez (as quatro runs + e2e) e commit: `Docs: correção do alcance`.
