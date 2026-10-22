# Acesso às redes privadas: convidados e times do FTB Teams

Mockup aprovado em 22/10/2026 (artifact "Acesso às Redes Privadas"). Decisões do dono: convidado **usa e edita** (só o dono e operadores gerenciam); o time do FTB entra **ao vivo** (a rede guarda o id do time); operador mantém acesso a tudo; limites configuráveis (32 convidados e 8 times por rede); **sugestões ao digitar** nos dois campos; o convidado **pode** tirar uma face da rede para outra.

## Regras

Três níveis sobre a mesma rede (`WaNetwork`):

| Regra | Quem | Onde vale |
| --- | --- | --- |
| `canManage` (não muda) | dono, operador | renomear, cor, pública/privada, remover, convidar, remover convidado, adicionar e tirar time |
| `canEdit` (novo) | quem gerencia, convidado pelo nome ou membro de um time adicionado | **tirar uma face** da rede para outra (o `canLeave` do Tablet, do Configurador e do colar de perfil) |
| `canUse` | quem edita, ou qualquer um se a rede é pública | escolher a rede nas faces, rede ativa, colar, mover nós para ela, ver a rede e os nós dela no Tablet |

- O `canLeave` hoje usa `canManage`. Passa a usar `canEdit`, e **não** `canUse`: senão qualquer jogador tiraria faces de uma rede pública alheia. Teste novo garante isso.
- As quatro verificações de visibilidade espalhadas (`TabletNodes.visible` dos nós e da rede, a lista de redes do `RouterSnapshot`, a do `TabletSnapshotBuilder`) passam a chamar a mesma regra do `WaNetwork`, em vez de repetir "pública ou dono".
- Quem perde o acesso (tirado da lista ou saiu do time): as faces dele continuam na rede e funcionando, nada é desligado. Ele só deixa de poder escolher a rede de novo, e de mexer nela se for tirar a face.
- Dono nunca entra na lista de convidados. Convidar quem já é convidado é recusado.
- Time: só times de grupo (party) do FTB Teams. A rede guarda o UUID do time, não os membros. Time apagado no FTB aparece como "removido" na lista, sem dar acesso, e o dono o tira com ✕. Sem o FTB Teams carregado, a seção some e os times guardados ficam inertes (não dão acesso, voltam a valer com o mod).
- Convidar jogador exige um UUID conhecido: o jogador está online ou está no cache de perfis do servidor. Quem nunca entrou não existe para o mod.

## Dados

- `WaNetwork` ganha `members` e `teams` (`List<UUID>`, sem repetição, em ordem de inclusão). O construtor de 4 e o de 5 argumentos continuam (listas vazias). Métodos `withMember`, `withoutMember`, `withTeam`, `withoutTeam`.
- NBT: as chaves `members` e `teams` (listas de UUID como `IntArrayTag`, `NbtUtils.createUUID`) só são gravadas quando não vazias. Mundo antigo carrega igual.
- Config, seção `networks`: `maxMembersPerNetwork` (padrão 32) e `maxTeamsPerNetwork` (padrão 8), 0 = sem limite, operadores sem limite (como `maxNetworksPerPlayer`).
- Sem migração: nenhuma chave existente muda.

## FTB Teams (opcional)

Conferido no código-fonte do ramo `1.21.1/main` do FTB Teams (versão `2101.1.11`; o ramo `main` usa nomes de outra versão do Minecraft): `FTBTeamsAPI.api().isManagerLoaded()`, `getManager()`, `TeamManager.getTeamByID(UUID)`, `getTeams()`, `Team.getMembers()` (`Set<UUID>`), `Team.getName()` (`Component`), `Team.getId()`, `Team.isPartyTeam()` e `Team.isValid()`. Não existe `isMember`: usa-se `getMembers().contains`. A tarefa de compat confirma de novo as assinaturas contra o jar antes de usá-las.

Ponte `network/TeamAccess` (como `Sources` e `Chemicals`): `LOADED`, `isMember(UUID team, UUID player)`, `info(UUID team)` e `search(String text, int max)`. A classe `compat/ftbteams/FtbTeams` é a única com tipos do FTB e só carrega com o mod presente; nenhum tipo do FTB na assinatura de classe `@EventBusSubscriber` ou `@GameTestHolder`.

## Protocolo (31)

- `TabletMenu.Action` ganha `NETWORK_MEMBER_ADD`, `NETWORK_MEMBER_REMOVE`, `NETWORK_TEAM_ADD`, `NETWORK_TEAM_REMOVE` (rede em `target`, jogador ou time em `other`), no fim do enum, validados por `managedNetwork`.
- `NetworkView` (a rede no snapshot do Tablet) ganha a lista de acesso **só quando `manageable`**: convidados (`UUID`, nome, online) e times (`UUID`, nome, nº de membros, `gone`). Quem não gerencia não recebe os nomes. Cresce a página só quando a lista muda (a página já vai só quando a estrutura muda).
- Sugestões: `NetworkSuggestPayload` (cliente → servidor: id do menu, tipo `PLAYER` ou `TEAM`, texto, rede) e `NetworkSuggestionsPayload` (servidor → cliente: tipo, o texto que respondeu e até 8 `Suggestion(UUID, nome, detalhe, jáNaRede)`). O servidor só responde a quem gerencia a rede escolhida, filtra pelo texto e ordena: começa com o texto antes de só conter, online antes de offline, depois pelo nome, e quem já está na rede por último. Limite de uma consulta por jogador a cada 100 ms.

## Tela (aba Redes do Tablet)

Na rede privada escolhida e com `manageable`, o painel direito ganha as seções **Acesso** (convidados com ✕ e campo "Convidar jogador…") e **Times do FTB Teams** (só com o FTB carregado; com "ao vivo" e o número de membros, "removido" em latão, campo "Adicionar time…"). Quem só usa vê a lista de acesso em texto, sem ✕ e sem campo, na linha de membros que já existe. Sugestões em lista suspensa embaixo do campo: setas e Enter escolhem, Tab completa, Esc fecha, clique escolhe; a consulta sai 150 ms depois da última tecla. Texto variável pelo `GuiText`; tema claro e escuro; en_us e pt_br.

## Custo (leis da `wa-performance`)

- Por tick, por visita, por entrega: **zero**. O motor nunca consulta acesso; só o jogador agindo, o snapshot do Tablet e as sugestões.
- Checagem de acesso: `contains` em duas listas pequenas (≤ 32 e ≤ 8) e, por time, `getTeamByID` mais `getMembers().contains` no FTB. Sem alocação além do que o snapshot já aloca.
- Memória: duas listas imutáveis por rede, vazias compartilhadas quando não há acesso.
- Cliente: a lista de acesso vai só com a página (mudou a estrutura) e só das redes gerenciáveis; sugestões só sob demanda, com pausa na digitação, até 8 linhas; nada com a aba fechada.
- Pico: nenhum. A busca de jogadores varre os online e o cache de perfis uma vez por consulta, a de times o `getTeams()` uma vez, com o limite de 100 ms por jogador.

## Fora desta etapa

Cargos dentro da rede; seguir o cargo do jogador no time (vale o time inteiro); convidar quem nunca entrou; outros mods de time (a ponte `TeamAccess` já isola o provedor para um segundo depois).
