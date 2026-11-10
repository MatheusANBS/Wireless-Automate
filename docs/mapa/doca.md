# Doca de Padrões

O bloco que recebe os pedidos de um Pattern Provider do AE2 encostado num lado e os entrega às máquinas vinculadas àquele lado. Parte do mapa do código (pacote base `src/main/java/io/github/matheusanbs/wirelessautomate/`). Índice em [CLAUDE.md](../../CLAUDE.md). Spec: `docs/superpowers/specs/2026-11-10-doca-de-padroes-design.md`; plano: `docs/superpowers/plans/2026-11-10-doca-de-padroes-etapa-1.md`.

## `dock/` (lógica pura, com JUnit)

- `DockReach`: o alcance um degrau acima (raio e máximo de máquinas pelo ordinal do tier seguinte, ou os valores do Ultimate); `ULTIMATE_RADIUS` 96, `MAX_ULTIMATE_RADIUS` 128, `ULTIMATE_MACHINES` 256. O `Config.dockRadius`/`dockMaxMachines` chama por aqui.
- `DockPick`: a escolha da máquina pelo cursor.
- `DockStatus`: o estado da linha de um lado.
- `DockColors`: as cores das etiquetas dos lados (índice `Direction.ordinal()`) e a ordem na tela (Norte, Sul, Leste, Oeste, Cima, Baixo).

## `dock/` (bloco, block entity e dados)

- `PatternDockBlock`: bloco cheio, sem frente e sem giro, com o `RouterBlock.TIER`; `tryUpgrade(level, pos, target, origin)` sobe o tier no lugar (como o do `StorageBlock`) e refaz o registro no índice; o `onRemove` larga o pendente de itens no chão; clique direito sem efeito.
- `PatternDockItem`: o item, com o tier no `DataComponents.BLOCK_STATE` (`StorageBlockItem.withTier`/`tierOf`) e a dica do tier e do alcance. Propriedade `wirelessautomate:tier` no `ClientSetup` para o modelo do item.
- `PatternDockBlockEntity`: sem ticker. Os seis `DockSide` (nulo = lado nunca usado), as máquinas por posição (`machineAt`), `link(side, pos, block, front)` e `unlink(side, pos)` (o lado esquece o bloco sem máquinas e some sem pendente), `side(int)`, `machineCount()`, `reachChanged()` (troca de tier), `dropPending()`. Implementa `RemoteListener` e `CacheOwner`: registra no `RemoteIndex` as máquinas no alcance (`Config.dockRadius`, Chebyshev) até o máximo (`Config.dockMaxMachines`), na ordem dos lados e de vínculo, no `onLoad`, num vínculo e numa troca de tier; sai no `setRemoved` e no `onChunkUnloaded`. Os avisos das máquinas (o `setChanged` pelo mixin com a releitura da frente, a carga do chunk e o cache invalidado) chegam ao `machineChanged`, que marca a máquina. NBT: `sides` (lista de `DockSide`). Só `level.blockEntityChanged` quando o NBT muda.
- `DockSide`: o bloco das máquinas, o perfil (`range/MachineConfig`), as máquinas, o `cursor` e o pendente (itens ou fluidos, com `pending_at`, a posição da máquina que recebeu o pedido). NBT: `side` (byte), `block`, `profile`, `machines`, `cursor`, `pending` (lista de `{item}` ou `{fluid}`; um item acima de 99 é salvo em partes) e `pending_at`.
- `DockMachine`: a posição e a frente num `RemoteMachine` (o objeto que vai ao índice), o lado, e o que não é salvo: `active` (no alcance e no máximo), `lastActive`, `changed` e os `MachineCaches` criados na primeira consulta. NBT: `pos` (long) e `front` (byte).
- `DockChoice`: o componente `linker_dock` (a Doca e o lado escolhidos no Vinculador), com `CODEC` e `STREAM_CODEC`.

## Recursos

Blockstate `pattern_dock` por `tier`, modelos provisórios em `models/block/pattern_dock/` (um `cube_all` com a textura `topo_<tier>` do roteador; fora da pasta que o `blocos.py` limpa), o modelo do item com as sobreposições por tier, a loot table (`copy_state` do `tier`), a tag de picareta, os nomes nos dois `lang` e a informação do JEI (`compat/jei/DockInfo`).

## Testes

`gametest/DockBlockGameTests` (8 testes: o tier, o NBT de um lado com duas máquinas, perfil, cursor e pendente de item e de fluido, o Cartão pelo `tryUpgrade` e pelo `useOn`, a dica do cartão, o item que guarda o tier, o pendente no chão, o alcance pela config e o registro no índice). As costuras do motor ficam em `gametest/DockGameTests`.
