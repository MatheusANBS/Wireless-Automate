# Acesso às redes privadas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O dono de uma rede privada convida jogadores pelo nome e, com o FTB Teams, adiciona times; convidados usam e editam a rede, com sugestões ao digitar.

**Architecture:** `WaNetwork` ganha `members` e `teams` e três regras (`canManage`, `canEdit`, `canUse`) que substituem as verificações "pública ou dono" espalhadas. O FTB Teams entra por uma ponte `TeamAccess` (mod opcional, tipos só em `compat/ftbteams`). O servidor valida as ações e as sugestões; a aba Redes do Tablet ganha as seções Acesso e Times.

**Tech Stack:** NeoForge 1.21.1, Java 21, JUnit, GameTests (runs comum e `gameTestServerTeams`), FTB Teams 2101.1.11 (opcional).

Spec: `docs/superpowers/specs/2026-10-22-acesso-redes-privadas-design.md`.

## Global Constraints

- **Idioma:** conversa, código, comentários, commits e docs em português; textos nos dois `lang` (`en_us` e `pt_br`).
- **Comentários** (`wa-comentarios`): rodar `python -I .claude/skills/wa-comentarios/scripts/varrer.py` antes de cada commit; sem código comentado nem histórico.
- **Performance** (`wa-performance`): ler `.claude/skills/wa-performance/SKILL.md`; rodar `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados e justificar cada candidato. Nada por tick, por visita ou por entrega; cliente só com a aba Redes aberta e só quando muda; sugestão sempre sob demanda, no máximo 8, uma consulta por jogador a cada 100 ms.
- **Tamanho** (`wa-modular`): rodar `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados; tela até 500 linhas, dividir acima de 800.
- **Mod opcional** (`wa-mod-opcional`): tipos do FTB Teams só em `compat/ftbteams` e `gametest/TeamTestSupport`; nunca na assinatura de método de classe `@EventBusSubscriber` ou `@GameTestHolder`; o resto do mod passa por `network/TeamAccess`.
- **Dados:** nenhuma chave de NBT, componente ou config existente muda. Novas: `members` e `teams` (só gravadas quando não vazias) e `networks.maxMembersPerNetwork` e `networks.maxTeamsPerNetwork`.
- **Protocolo:** `ModPayloads.VERSION` de "30" para "31".
- **`canLeave` não pode virar `canUse`:** a rede pública alheia continua sem permitir tirar faces dela.
- **GameTests:** classe com `@GameTestHolder(WirelessAutomate.MODID)` e `@PrefixGameTestTemplate(false)`, template `"empty"`; cada teste cria a própria rede; compara redes com `Objects.equals`; teste que muda a config vai no lote `wirelessautomate_config` e devolve o valor em `try/finally`; `timeoutTicks` no máximo 300; jogador falso só por `TestPlayers`.
- **Verificação:** `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams` e, ao mexer em tela ou payload, `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (um jogo por vez; o resultado em `run/e2e/result.txt` precisa dizer `OK`). Se aparecer `BUILD FAILED`, pare e conserte: nunca espere o resultado de um jogo que não abriu.
- **Commits:** sem linhas de atribuição extras além da definida pelo ambiente, autor do dono, sem push. `run/options.txt` não se mexe.

---

### Task 1: Modelo, regras de acesso e limites

**Custo:** nenhum caminho quente. A checagem roda só em ação do jogador e no snapshot do Tablet: `contains` em listas de até 32 e 8 UUIDs.

**Files:**
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/network/WaNetwork.java`
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/network/TeamAccess.java` (nesta tarefa só o contrato e o estado sem FTB; o provedor real é a Task 2)
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/Config.java` (seção `networks`)
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/network/NetworkSavedData.java` (`memberLimitReached`, `teamLimitReached`)
- Modify: `menu/TabletNodes.java`, `menu/RouterSnapshot.java`, `menu/TabletSnapshotBuilder.java` (usar `canUse`)
- Modify: `menu/TabletMenuActions.java:75-78`, `preset/PresetPaste.java:62-67` (`canLeave` com `canEdit`)
- Test: `src/main/java/io/github/matheusanbs/wirelessautomate/gametest/NetworkAccessGameTests.java` (novo)

**Interfaces:**
- Produces: `WaNetwork(UUID id, String name, int color, UUID owner, boolean isPublic, List<UUID> members, List<UUID> teams)`; `withMember(UUID)`, `withoutMember(UUID)`, `withTeam(UUID)`, `withoutTeam(UUID)`; `boolean canManage(UUID player, boolean operator)`; `boolean canEdit(UUID player, boolean operator)`; `boolean canUse(UUID player, boolean operator)`; as versões `ServerPlayer` delegam a elas. `TeamAccess.LOADED` (constante), `TeamAccess.isMember(UUID team, UUID player)` (falso sem o FTB).

- [ ] **Step 1: Escrever o teste (falha)** em `NetworkAccessGameTests`: rede privada, o convidado `canUse` e `canEdit` mas não `canManage`; o estranho não tem nenhum dos três; a rede pública dá `canUse` ao estranho mas não `canEdit`; o dono e o operador têm os três; o NBT ida e volta mantém `members` e `teams`; sem acesso, `save()` não grava as chaves; um mundo antigo (tag sem as chaves) carrega com listas vazias; `withMember` repetido não duplica; o limite de convidados pela config recusa o 33º e o operador não tem teto.
- [ ] **Step 2: Rodar** `./gradlew runGameTestServer` e ver o teste falhar por não compilar.
- [ ] **Step 3: Implementar `WaNetwork`**

```java
public record WaNetwork(UUID id, String name, int color, UUID owner, boolean isPublic, List<UUID> members,
        List<UUID> teams) {
    public WaNetwork {
        members = List.copyOf(members);
        teams = List.copyOf(teams);
    }

    public WaNetwork(UUID id, String name, int color, UUID owner, boolean isPublic) {
        this(id, name, color, owner, isPublic, List.of(), List.of());
    }

    public WaNetwork(UUID id, String name, int color, UUID owner) {
        this(id, name, color, owner, false);
    }

    public boolean canManage(UUID player, boolean operator) {
        return operator || owner.equals(player);
    }

    /** Quem gerencia, convidado pelo nome ou membro de um time adicionado. Não vale pela rede ser pública. */
    public boolean canEdit(UUID player, boolean operator) {
        if (canManage(player, operator) || members.contains(player)) {
            return true;
        }
        for (UUID team : teams) {
            if (TeamAccess.isMember(team, player)) {
                return true;
            }
        }
        return false;
    }

    public boolean canUse(UUID player, boolean operator) {
        return isPublic || canEdit(player, operator);
    }

    public boolean canManage(ServerPlayer player) {
        return canManage(player.getUUID(), player.hasPermissions(2));
    }

    public boolean canEdit(ServerPlayer player) {
        return canEdit(player.getUUID(), player.hasPermissions(2));
    }

    public boolean canUse(ServerPlayer player) {
        return canUse(player.getUUID(), player.hasPermissions(2));
    }
}
```

Os `with*` existentes (`withName`, `withColor`, `withPublic`) passam a repassar `members` e `teams`; `withMember`/`withTeam` devolvem `this` se o UUID já está na lista; `withoutMember`/`withoutTeam` fazem o inverso. `save()` grava `members` e `teams` como `ListTag` de `NbtUtils.createUUID(uuid)` só quando não vazias; `load()` lê com `NbtUtils.loadUUID` ignorando entradas que não forem `IntArrayTag` (try/catch por entrada, como o `NetworkSavedData.load`).

- [ ] **Step 4: `TeamAccess` provisório**: `LOADED = false` por ora e `isMember` devolvendo `false`; o comentário de classe diz que o provedor entra na Task 2.
- [ ] **Step 5: Config e limites:** `MAX_MEMBERS_PER_NETWORK` (`maxMembersPerNetwork`, 32, 0 a 10_000) e `MAX_TEAMS_PER_NETWORK` (`maxTeamsPerNetwork`, 8, 0 a 1_000) na seção `networks`; `NetworkSavedData.memberLimitReached(ServerPlayer, WaNetwork)` e `teamLimitReached(...)`: `max > 0 && !player.hasPermissions(2) && lista.size() >= max`.
- [ ] **Step 6: Unificar a visibilidade:** `TabletNodes.visible(entry, ...)` e `visible(network, ...)`, a lista de redes públicas do `RouterSnapshot.capture` (laço sobre `data.networks()`) e o equivalente no `TabletSnapshotBuilder` passam a usar `network.canUse(me, operator)`. Em `RouterSnapshot`, a lista passa a incluir toda rede alheia que `canUse` (não só pública), com `owned = false`.
- [ ] **Step 7: `canLeave` com `canEdit`:** em `TabletMenuActions` (linhas 75-78) e `PresetPaste.canLeave` (62-67): `current == null || current.canEdit(viewer)`. Atualizar os comentários dos dois e o do `TabletMenu` (linha ~705), que citam "dono ou operador".
- [ ] **Step 8: Rodar** `./gradlew build runGameTestServer`; todos os testes de rede existentes continuam passando (em especial os de `TabletGameTests` e `ConfiguratorGameTests` com rede alheia) e o teste novo passa. Acrescentar a `NetworkAccessGameTests` o caso "rede pública alheia: `canLeave` recusa" (face numa rede pública de outro dono segue no lugar ao colar e ao mover) e o caso "convidado tira a face de uma rede privada para outra".
- [ ] **Step 9: Comentários, performance, tamanho e commit:** `git add` dos arquivos acima e `git commit -m "Redes: convidados, times e as regras canManage, canEdit e canUse"`.

---

### Task 2: Compat com o FTB Teams

**Custo:** consulta só sob demanda (checagem de acesso e sugestões). `getTeamByID` e `getMembers().contains` por time, no máximo 8 times por rede.

**Files:**
- Modify: `build.gradle`, `gradle.properties` (dependência `compileOnly`, repositório e a run `gameTestServerTeams`, nos moldes da `gameTestServerSource`)
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/compat/ftbteams/FtbTeams.java`
- Modify: `network/TeamAccess.java` (a ponte)
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/gametest/TeamTestSupport.java`, `gametest/TeamGameTests.java` (namespace `wirelessautomate_teams`)

**Interfaces:**
- Produces: `TeamAccess.LOADED` (`ModList.get().isLoaded("ftbteams")`, **confirmar o id do mod no `neoforge.mods.toml` do jar**); `TeamAccess.isMember(UUID team, UUID player)`; `TeamAccess.info(UUID team)` → `@Nullable TeamInfo`; `TeamAccess.search(String text, int max, Set<UUID> taken)` → `List<TeamInfo>`; `record TeamInfo(UUID id, String name, int members, boolean gone)`.

- [ ] **Step 1: Confirmar o build.** Abrir o `build.gradle` e o repositório maven do FTB (a documentação oficial dá o endereço; **não adivinhar**: conferir antes) e a coordenada exata do jar NeoForge 1.21.1 da versão 2101.1.11; ver quais mods o FTB Teams exige em tempo de execução (o que a página dele lista como dependência: FTB Library e Architectury API, versões a conferir). Se alguma coordenada não resolver, parar e perguntar ao dono em vez de seguir.
- [ ] **Step 2: Dependência e run.** `compileOnly` do jar do FTB Teams (e do que a compilação exigir); config de `teamsTestMods` e a run `gameTestServerTeams` copiando a estrutura da `gameTestServerSource` (linhas ~200-205 e 298); o `runClient` ganha o FTB Teams e as dependências dele para o e2e; adicionar o nome dos jars no filtro da linha ~250 do `build.gradle`.
- [ ] **Step 2b:** `./gradlew compileJava` precisa passar antes de seguir. Se der `BUILD FAILED`, consertar.
- [ ] **Step 3: Confirmar as assinaturas contra o jar** (descompilado ou fontes do ramo `1.21.1/main`): `FTBTeamsAPI.api().isManagerLoaded()`, `getManager()`, `TeamManager.getTeamByID(UUID)` (`Optional<Team>`), `getTeams()` (`Collection<Team>`), `Team.getMembers()` (`Set<UUID>`), `getName()` (`Component`), `getId()`, `isPartyTeam()`, `isValid()`. Se alguma diferir, seguir o jar e anotar no relatório.
- [ ] **Step 4: Escrever `FtbTeams`** com os métodos estáticos: `isMember(team, player)` (`getTeamByID(team).filter(Team::isPartyTeam).map(t -> t.getMembers().contains(player)).orElse(false)`), `info(team)` (`gone = true` quando o time não existe mais ou `!isValid()`), `search(text, max, taken)` (varre `getTeams()` uma vez, só `isPartyTeam()` e `isValid()`, filtra pelo texto sem diferenciar maiúsculas e ordena com `AccessSuggestions` da Task 3; até lá, ordem por nome). Tudo com `FTBTeamsAPI.api().isManagerLoaded()` antes; sem gerenciador carregado devolve vazio.
- [ ] **Step 5: `TeamAccess`** vira a ponte: `LOADED` pelo `ModList` (com `ModList.get() != null`, como `Sources`), e cada método chama `FtbTeams` só se `LOADED`.
- [ ] **Step 6: Testes na run `gameTestServerTeams`** (`TeamGameTests`): criar um time de grupo pelo gerenciador, adicionar a rede, confirmar `canUse` e `canEdit` para um membro e não para um estranho, tirar o membro do time e ver o acesso sumir sem mexer na rede, apagar o time e ver `info(...).gone`, `search` só com times de grupo. O apoio com os tipos do FTB fica em `TeamTestSupport`, nunca na assinatura de um método da classe de teste.
- [ ] **Step 7: Provar que sem o FTB nada quebra:** `./gradlew runGameTestServer` (sem o mod) passa com os testes da Task 1; `TeamAccess.LOADED` é falso e `isMember` devolve falso.
- [ ] **Step 8: Comentários, performance, tamanho e commit:** `git commit -m "Compat com o FTB Teams: ponte TeamAccess e a run gameTestServerTeams"`.

---

### Task 3: Ações, sugestões e protocolo 31

**Custo:** nada por tick. As sugestões varrem os jogadores online, o cache de perfis e o `getTeams()` uma vez por consulta, no máximo uma por jogador a cada 100 ms, e devolvem até 8 linhas.

**Files:**
- Create: `network/AccessSuggestions.java` (puro) e `src/test/java/io/github/matheusanbs/wirelessautomate/network/AccessSuggestionsTest.java`
- Modify: `menu/TabletMenu.java` (`Action`), `menu/TabletMenuActions.java`
- Create: `packet/NetworkSuggestPayload.java`, `packet/NetworkSuggestionsPayload.java`
- Modify: `packet/ModPayloads.java` (registro, `VERSION = "31"`, handler com `canManage` e o limite de 100 ms), `menu/TabletPlayers.java` (o relógio por jogador, no estilo das fichas)
- Modify: `menu/TabletSnapshot.java` (`NetworkView` com o acesso), `menu/TabletSnapshotBuilder.java`
- Modify: `command/WaCommand.java` (`/wa network <nome> member add|remove <jogador>` e `team add|remove <nome>`, permissão 2 ou dono)
- Test: `gametest/NetworkAccessGameTests.java` (acrescentar), `gametest/TabletGameTests.java` se o `NetworkView` quebrar

**Interfaces:**
- Consumes: Task 1 (`WaNetwork.with*`, limites) e Task 2 (`TeamAccess.search`, `info`).
- Produces: `AccessSuggestions.Candidate(UUID id, String name, boolean online, boolean taken)` e `static List<Candidate> pick(Collection<Candidate> all, String text, int max)`; as ações `NETWORK_MEMBER_ADD`, `NETWORK_MEMBER_REMOVE`, `NETWORK_TEAM_ADD`, `NETWORK_TEAM_REMOVE` no fim do enum; `NetworkView.access()` com `members` (`UUID`, nome, online) e `teams` (`TeamAccess.TeamInfo`).

- [ ] **Step 1: JUnit de `AccessSuggestions.pick`:** texto vazio devolve os melhores até `max`; quem começa com o texto vem antes de quem só contém; online antes de offline; depois o nome sem diferenciar maiúsculas; `taken` por último; corta em `max`; texto com espaços nas pontas é aparado.
- [ ] **Step 2: Implementar `pick`** (comparador numa constante, sem stream no caminho comum; uma lista nova por consulta é aceitável: é sob demanda).
- [ ] **Step 3: Ações no servidor** em `TabletMenuActions.applyNetwork` (e o `switch` de `apply`): todas por `managedNetwork`; `MEMBER_ADD` recusa (`refused.permission`, `refused.owner`, `refused.known`, `refused.exists`, `refused.limit`) se o jogador for o dono, se o UUID não é de um jogador online nem do cache de perfis (`server.getProfileCache().get(uuid)`), se já está, ou se o limite estourou; `MEMBER_REMOVE` tira; `TEAM_ADD` exige `TeamAccess.LOADED` e `TeamAccess.info(id)` existente e não `gone`, e respeita `teamLimitReached`; `TEAM_REMOVE` tira mesmo `gone`. Cada ação chama `data.update(...)` (que já suja e sobe a versão) e dá o aviso.
- [ ] **Step 4: Sugestões:** `NetworkSuggestPayload(int containerId, Kind kind, String text, UUID network)` (texto até 32 caracteres, cortado no handler). O handler confere o menu do Tablet do jogador, `managedNetwork` e o relógio (`TabletPlayers.allowSuggest(uuid, gameTime)`: 2 ticks entre consultas); `PLAYER` junta os online (`server.getPlayerList().getPlayers()`) e o cache de perfis (todos os UUID que o `GameProfileCache` já conhece, sem baixar nada), marca `taken` quem já está na lista ou é o dono, e passa por `pick(..., 8)`; `TEAM` usa `TeamAccess.search(text, 8, taken)`. Responde com `NetworkSuggestionsPayload(kind, text, suggestions)`.
- [ ] **Step 5: `NetworkView`:** acrescentar `access` (vazio quando `!manageable`); `TabletSnapshotBuilder` monta os convidados com o nome do cache de perfis (UUID sem nome vira o UUID curto) e os times por `TeamAccess.info`. Atualizar o codec binário e os testes que constroem `NetworkView`.
- [ ] **Step 6: Registro dos payloads e `ModPayloads.VERSION = "31"`.** Atualizar a linha do protocolo no `CLAUDE.md` na Task 6.
- [ ] **Step 7: Comandos** `/wa network <nome> member add|remove <jogador>` e `team add|remove <nome do time>` com a mesma validação (a regra de dono ou operador do `/wa network` existente) e sugestão de nomes pela API de comandos; testes de comando seguindo os de `/wa network`.
- [ ] **Step 8: GameTests** (`NetworkAccessGameTests`): cada ação com dono, convidado e estranho (só o dono e o operador conseguem; convidado e estranho recebem `refused.permission`); jogador desconhecido recusado; limites (config no lote próprio, restaurada em `try/finally`); o `NetworkView` do dono traz o acesso e o do convidado não; as sugestões respeitam o limite de 8, o `taken` e a recusa a quem não gerencia; o limite de 100 ms. Sem o FTB, `TEAM_ADD` é recusado.
- [ ] **Step 9: Verificação:** `./gradlew build runGameTestServer runGameTestServerTeams`. Comentários, performance e tamanho; `git commit -m "Redes: ações de acesso, sugestões ao digitar e protocolo 31"`.

---

### Task 4: Tela, seções Acesso e Times

**Custo:** a tela só desenha; as linhas e os textos são montados quando a página ou as sugestões mudam (chave suja), nunca por quadro. A consulta de sugestão sai com 150 ms de pausa.

**Files:**
- Create: `client/tablet/AccessSection.java` (as seções e as linhas), `client/tablet/SuggestField.java` (campo com a lista suspensa), `client/tablet/SuggestNav.java` (puro: o item escolhido, setas, Tab, rolagem) e `src/test/java/.../client/tablet/SuggestNavTest.java`
- Modify: `client/tablet/NetworksTab.java` (a seção entra no painel direito; a linha de membros da linha ~344 mostra a lista em texto para quem só usa), `client/tablet/TabletActions.java` (envio das ações e da consulta), `client/tablet/TabletScreen.java` (receber `NetworkSuggestionsPayload` pelo handler do cliente), `assets/wirelessautomate/lang/en_us.json` e `pt_br.json`

**Interfaces:**
- Consumes: Task 3 (ações, payloads, `NetworkView.access()`).
- Produces: `SuggestNav` com `next()`, `previous()`, `selected()`, `reset(int size)`; o handler do cliente que entrega as sugestões à `SuggestField` ativa (descarta resposta de um texto que não é mais o do campo).

- [ ] **Step 1: JUnit do `SuggestNav`** (setas dão a volta, `reset` zera, lista vazia não escolhe nada, Tab completa o selecionado).
- [ ] **Step 2: `SuggestField`:** um `EditBox` com atraso de 150 ms (um contador de ticks do cliente, sem `Timer`), a lista até 8 linhas abaixo (nome, detalhe cinza, "já adicionado" apagado), clique ou Enter envia a ação `*_ADD` e limpa o campo, Esc fecha. Textos pelo `GuiText`; tema pela `GuiPaint`; `EditBox` reaplica a tinta pelo `GuiThemes.version()`.
- [ ] **Step 3: `AccessSection`:** convidados (`✕` de dois cliques pelo `ArmedClick`, como o Desvincular), o campo de convite, os times (com "ao vivo" e o número de membros, "removido" em latão) e o campo de time (só com o `access` presente e a lista de times só com o FTB carregado: o cliente sabe pelo `ModList`). Quem não gerencia vê só a linha de texto na linha de membros.
- [ ] **Step 4: Integrar na `NetworksTab`** sem passar das 500 linhas (medir; se passar, mover para `AccessSection`). Rede pública: as seções não aparecem (a lista é para privadas); ao trocar para pública, os dados de acesso ficam guardados.
- [ ] **Step 5: Lang** nos dois idiomas (todas as chaves novas, inclusive os avisos `refused.*`, "ao vivo", "removido", as dicas); rodar a conferência de chaves que o projeto já usa.
- [ ] **Step 6: e2e.** Em `E2eNetworkSectionSteps` (ou um `E2eAccessSteps` novo, ligado no `E2eScript`), passos: abrir a aba Redes, escolher uma rede privada, digitar "Br", conferir a lista de sugestões, escolher, ver o convidado na lista, adicionar um time (com o FTB no `runClient`), capturas `7s-tablet-acesso`, `7s-tablet-acesso-sugestoes`, nos dois temas e em pt; `clipCheck` zerado. Rodar `WA_E2E="$PWD/run/e2e" ./gradlew runClient` e olhar as capturas (texto cortado só aparece olhando).
- [ ] **Step 7:** `./gradlew build runGameTestServer`; comentários, performance, tamanho; `git commit -m "Aba Redes: seções Acesso e Times com sugestões ao digitar"`.

---

### Task 5: Guia, documentação e fechamento

**Files:**
- Modify: `scripts/guide/gerar_guia.py` (páginas das redes e do Tablet, nos dois idiomas; regerar os `.md`), `CLAUDE.md` (mapa: `WaNetwork`, `TeamAccess`, `compat/ftbteams`, `client/tablet/Access*`, `packet/` com o protocolo 31 e os dois payloads novos, `Config` com os dois limites, `gametest/` com a run `gameTestServerTeams` e a lista de comandos), `docs/especificacao.md` (a seção das redes e do Tablet, a tabela de permissões), `docs/progresso.md` (a tabela, o próximo passo e uma linha no histórico com os números de testes)

- [ ] **Step 1: Guia só com "como operar":** convidar um amigo, adicionar um time, o que o convidado pode fazer, o aviso de que só quem já entrou aparece nas sugestões. Sem config nem detalhes internos.
- [ ] **Step 2: Docs e `CLAUDE.md`** como acima.
- [ ] **Step 3: Verificação completa:** `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams` e o e2e completo (o item "alcance: tema escuro" que já falhava segue pendente e não deve piorar; se o e2e falhar só nele, registrar).
- [ ] **Step 4: Revisão final independente** do conjunto, conferindo: as leis de performance no diff; `canLeave` sem `canUse`; nenhum tipo do FTB fora do pacote de compat; o mundo antigo e o sem FTB carregando; permissão de toda ação e das sugestões validada no servidor (nunca no cliente).
- [ ] **Step 5:** `git commit -m "Acesso às redes privadas: guia, especificação e progresso"`.
