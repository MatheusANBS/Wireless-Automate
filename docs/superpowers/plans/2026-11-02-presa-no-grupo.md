# A presa vira máquina do grupo: plano de implementação (etapa 1)

> **Para agentes:** um subagente por tarefa, na ordem. Cada tarefa termina compilando, com os GameTests passando, e num commit próprio.

**Objetivo:** a máquina presa passa a ser membro do grupo do bloco dela (perfil, exceção e cartões do grupo); a máquina vinculada segue o perfil; o Configurador fica sem função.

**Spec:** [`docs/superpowers/specs/2026-11-02-presa-no-grupo-design.md`](../specs/2026-11-02-presa-no-grupo-design.md). Leia inteira antes da sua tarefa.

**Estratégia:** a API da presa no `RouterBlockEntity` (`face`, `setMode`, `configureFace`, `setFaceNetwork`, `setFilter`, `card`, `setCard`, `filterSet`, `setAttachedFacesNetwork`...) e o alvo `EditTarget.ATTACHED` **continuam existindo** com os mesmos nomes: mudam só por dentro, para o membro presa do `RemoteMachines`. Assim o motor, os comandos, o Vinculador, o Tablet, o bench e os ~540 usos nos GameTests seguem compilando, e só os testes cuja regra mudou de propósito (spin, frente da máquina, presa + remotas do mesmo bloco, exceção vazia do Vinculador) são ajustados.

## Restrições globais

- Código, comentários, commits e docs em português; textos nos dois `lang` (`en_us.json` e `pt_br.json`).
- Leia `CLAUDE.md`, `docs/mapa/alcance.md`, `docs/mapa/roteador.md` e `docs/mapa/motor.md` antes de mexer; ao mudar uma classe, atualize a linha dela no mapa da área.
- Leia `.claude/skills/wa-performance/SKILL.md`. Nada novo por tick; a fachada da presa roda só na montagem das rotas, na tela e nas edições. Nenhuma busca de bloco por tick; `level.isLoaded(pos)` antes de ler o bloco da presa. Antes do commit: `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados, resolvendo ou justificando cada candidato no relatório.
- Leia `.claude/skills/wa-comentarios/SKILL.md`; antes do commit, `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados. Sem comentário de histórico ("antes era").
- Tamanho (`.claude/skills/wa-modular/SKILL.md`): o `RouterBlockEntity` está em 976 linhas, o teto é 1000. A lógica nova da presa vai numa classe nova do pacote `block` (`RouterAttached`), e o roteador só delega. `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados.
- Chaves de NBT existentes continuam lidas. Novas: no roteador, `attached` (o membro presa: `group`, `exception` opcional, `front`); no índice, `attached_group`. As chaves `faces` e `cards` do roteador só são gravadas enquanto houver rascunho não convertido (spec, "Mundos antigos").
- Componentes de item registrados não saem (inclusive `configurator_*`, `configurator_mode`, `configurator_any_machine`, `configurator_area` e `preset`).
- `ModPayloads.VERSION` sobe de `"40"` para `"41"` uma vez só, na tarefa que mudar o formato de um payload ou do snapshot (a Tarefa 2), e os payloads do Configurador saem na Tarefa 4 sem nova subida.
- Mekanism, Ars Nouveau e FTB Teams continuam opcionais (tipos só nos pacotes `compat/` e nos suportes dos GameTests).
- GameTests novos: `@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, template `"empty"`, `timeoutTicks` até 300, cada teste com a própria rede, pertinência e não contagem. Ajustar um teste existente só quando a regra mudou de propósito pela spec; diga no relatório qual teste, qual regra e por quê.
- Compile antes de rodar jogo (`./gradlew compileJava`); com `BUILD FAILED`, conserte em vez de esperar. Um jogo por vez; não mexa em `run/options.txt`.
- Verificação antes do commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams`. Tarefas de tela também `./scripts/e2e.sh --only <suítes tocadas>` com `run/e2e/result.txt` = `OK`.
- Commits sem linhas de atribuição, com o autor do repositório (`git -c user.name="$(git log -1 --format=%an)" -c user.email="$(git log -1 --format=%ae)" commit ...`). Não faça push.

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `range/RemoteMachines.java`, `range/AttachedMember.java` (novo) | O membro presa: grupo, exceção, frente; entrar e sair de grupo; NBT; o grupo da presa não sai; vincular sem exceção vazia |
| `block/RouterAttached.java` (novo) | A fachada da presa: ler a face que vale, editar com a semântica de máquina, cartões do grupo, sincronizar com o bloco da frente, converter o rascunho antigo |
| `block/RouterBlockEntity.java`, `RouterFaces.java`, `RouterCards.java`, `RouterNbt.java`, `RouterNeighbors.java`, `RouterTargets.java`, `RouterRange.java` | Delegar à fachada; tirar o armazenamento da presa; NBT; spin sem remapear; o alvo `ATTACHED` como máquina |
| `network/NetworkRoutes.java` | Portas da presa lidas pela fachada (frente lida uma vez por montagem) |
| `range/RangeLinks.java` | Vincular com `linkMachine` (segue o perfil) |
| `menu/RangeView.java`, `menu/RouterSnapshot.java`, `network/IndexRemotes.java`, `network/NodeIndex.java`, `menu/TreeOrder.java`, `menu/TabletRows.java` | A presa dentro do grupo no snapshot e no índice |
| `client/router/RangeList.java`, `MachineColumn.java`, `client/tablet/Tree*.java` | A linha da presa dentro do grupo |
| `item/ConfiguratorItem.java`, `packet/*Configurator*`, `client/ConfiguratorWheel*`, `block/RouterBlock.java`, guias, lang | Configurador sem função |

---

### Tarefa 1: modelo, fachada, motor e migração (servidor, sem tela)

Faça a spec valer no servidor, sem mudar tela nem payload:

1. **`range/AttachedMember`** (grupo, exceção opcional, frente) e o membro opcional em `RemoteMachines`: `attached()`, `attach(block, front)` (entra no primeiro grupo do bloco ou cria um com o perfil vazio; trocar de bloco descarta a exceção), `effectiveAttached()`, `groupHasAttached(id)`, `memberCount(group)` (remotas + presa), NBT (chave `attached`, gravada pelo `RouterNbt`). A regra de saída de grupo da spec: o grupo da presa não sai ao desvincular a última remota; o grupo que a presa deixa fica, a não ser que esteja vazio de verdade (perfil `sameAs(new MachineConfig())` e sem cartões). Ajuste o `unlink`, o `regroup` e o `load` (o `load` não descarta o grupo da presa).
2. **Vincular segue o perfil:** tire o `isolated` (`link(..., boolean isolated)` e `RouterBlockEntity.linkMachineIsolated`); o `RangeLinks` chama o `linkMachine` comum. Ajuste os testes que provavam a exceção vazia (`RangeFaceNetworkGameTests.groupNetworkDoesNotReachTheIsolatedMachine` e outros que achar) para provar a regra nova.
3. **`block/RouterAttached`**, a fachada (o `RouterBlockEntity` delega e fica abaixo de 1000 linhas):
   - leitura: `face(type, Direction absoluto)` e `face(type, RelativeSide)` devolvem a face que vale, pelo `RemoteSide` da frente da máquina (sem frente, Norte); sem membro, um `FaceConfig` vazio compartilhado só de leitura (não alocar por chamada);
   - escrita: um `edit(type, Direction absoluto, Predicate<FaceConfig>)` com a semântica de máquina (única do grupo muda o perfil; senão exceção, que some se igual ao perfil) e `router.changed(typeBit)`; sem membro, cria o membro (se o bloco da frente não é ar e está carregado) antes de editar; todos os setters da presa do roteador passam por ele;
   - cartões: `card`/`setCard`/`cardCount`/`filterSet` da presa vão para o grupo (`RouterCards.remoteFilterSet` e `MachineGroup.card`), com o lado convertido;
   - sincronizar com o bloco da frente (`sync()`): chamada no `machineNeighborChanged` quando o bloco mudou, quando o `facing` muda e no `onLoad`; nunca por tick; avisa o motor (`changed` de todos os tipos) só se o grupo mudou;
   - migração (spec, "Mundos antigos"): o `RouterNbt.load` lê `faces`/`cards` (e a rede antiga) para um rascunho; o `sync()` converte quando há bloco; enquanto houver rascunho, o `RouterNbt.save` o grava nas chaves antigas.
4. **Tirar o armazenamento da presa** do `RouterFaces` (o array por `RelativeSide`, `rotate`, `commonNetwork` da presa) e do `RouterCards`; as regras de rede e redstone sobre remotas (`setNetworkAll`, `networks`, `hasNetwork`, `redstoneTypes`, `migrateLegacyNetwork`, `dropExceptionsLikeProfile`) passam a incluir a exceção do membro presa. `spinTo` só muda o estado do bloco.
5. **Motor:** `NetworkRoutes.readPorts` lê pela fachada, com a frente da presa lida uma vez por montagem do tipo; as portas continuam no array da presa (`NodePorts.ports`, `attachedCaches`).
6. **`RouterTargets`:** o alvo `ATTACHED` usa a fachada (editar, `hasOwnFaces` = presa com exceção, cartões do grupo, `restoreIdleNetworks`/`clearIdleNetworks` também na presa). O "Voltar ao perfil" da presa (o mesmo método que tira a exceção de uma máquina) passa a valer para ela.
7. **GameTests novos** (`gametest/AttachedGroupGameTests.java`): a presa entra no grupo do bloco e uma remota do mesmo bloco segue o mesmo perfil; editar a presa sozinha muda o perfil, com remota no grupo vira exceção; a remota vinculada num grupo configurado move na hora; bloco trocado leva a presa para o grupo do bloco novo e o grupo antigo configurado fica; ar mantém o grupo; spin não muda a configuração absoluta; mundo antigo (NBT montado à mão com `faces`/`cards`) converte nos casos 4, 5 e 6 da spec; salvar e carregar mantém o membro.
8. Ajuste os GameTests que quebrarem só pelas regras que a spec mudou (spin, frente da máquina, presa + remotas do mesmo bloco, exceção vazia); qualquer outra falha é bug seu.

**Custo:** nada por tick; um `getBlockState` (com `isLoaded`) por montagem de tipo do nó para a frente da presa; menos 36 `FaceConfig` por roteador.

### Tarefa 2: snapshot, índice e Tablet no servidor

1. `RangeView`/`RouterSnapshot`: o grupo diz se tem a presa e conta com ela; some a linha separada da presa (o `Kind.ATTACHED` continua como alvo). O nome da presa (`attachedLabel`) segue no snapshot.
2. `IndexRemotes`/`NodeIndex.Entry`: `attachedGroup` (chave `attached_group`, −1 sem grupo; índice antigo lê −1); a contagem de máquinas da base continua uma presa por roteador.
3. `TreeOrder`/`TabletRows`: a linha da presa vai para dentro do grupo dela, antes das remotas; grupo recolhido esconde a presa também; sem grupo (−1), a linha da presa fica onde está hoje.
4. `ModPayloads.VERSION` = `"41"`.
5. GameTests: ajuste `TabletTreeGameTests`, `TabletMachinesGameTests`, `RouterMenuGameTests` e os que quebrarem pela posição nova da presa; novos para o grupo com a presa no snapshot e na árvore.

### Tarefa 3: telas (cliente)

1. `client/router/RangeList` e `MachineColumn`: sem a linha "Máquina presa" no topo; a presa como primeira linha dentro do grupo, com "presa" (ou o nome dado) e o Olho; "Fornalha ×4"; "vale para N de M" com a presa. Clicar escolhe o alvo `ATTACHED`; com exceção, o ↺ como nas remotas.
2. Tablet (`client/tablet/Tree*`, `ListTree`): a presa dentro do grupo.
3. Lang nos dois idiomas (tire as chaves que ficarem sem uso).
4. e2e: ajuste os passos de alcance e do Tablet; rode `./scripts/e2e.sh --only <suítes de alcance e Tablet> --capture <prefixos>` e olhe as capturas.

### Tarefa 4: Configurador sem função

Siga a seção "Configurador" da spec: tire `useOn`, seletor, roda (`ConfiguratorWheelKeys`, `ConfiguratorWheelScreen`), `ConfiguratorWheelPayload`, `CycleConfiguratorTypePayload` (registro e handlers), a exceção no `RouterBlock.useItemOn`, o ramo do Configurador no `HeldScrollHandler`, `ConfiguratorGameTests`, os passos de e2e e captura dele (`E2eConfiguratorSteps`, `ScreenshotWheel` e as menções), as chaves de lang sem uso; tooltip novo nos dois idiomas ("Sem função por enquanto: uma ferramenta nova chega numa próxima versão."); página do guia (`scripts/guide/paginas/`) como nota curta, e as menções nas outras páginas.

### Tarefa 5: docs e verificação final

Spec da especificação (`docs/especificacao.md`: presa, Configurador), mapas (`alcance`, `roteador`, `motor`, `tablet`, `cliente-e-pacotes`), guia do Roteador (a presa no grupo), `docs/progresso/`, bench antes e depois (`many:500:3,full:100:3,raw:2:3,rebuild:500:3,mixed:498:3`) com a tabela em `docs/benchmark.md`, e2e completo das suítes tocadas.
