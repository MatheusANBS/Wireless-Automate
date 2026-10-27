# Roteador de alcance e o mixin

Máquinas remotas vinculadas, grupos e o mixin que as acorda. Parte do mapa do código (pacote base `src/main/java/io/github/matheusanbs/wirelessautomate/`). Índice em [CLAUDE.md](../../CLAUDE.md).

## `range/`

Roteador de alcance: com o Upgrade de Alcance, um roteador move de e para várias máquinas no raio dele, configuradas por grupo (perfil por tipo de bloco) com exceção por máquina. `RangeTiers`, `RangeRules` (puro, com JUnit: distância de Chebyshev, recusas e a regra do limite `reach`), `RemoteSide` (puro: lados relativos à frente da máquina, Esquerda e Direita vistas de frente, e o quadro canônico das telas), `EditTarget`, `MachineConfig`, `MachineGroup`, `RemoteMachine` (com o `label` opcional, a chave de NBT `label`), `MachineNames` (valida e resolve o nome mostrado de uma máquina e de um grupo), `RemoteMachines`, `MachineCaches` (os caches de capability de uma máquina, com o `isLoaded` antes de consultar uma remota), `RemoteCapabilities` (a consulta direta, sem cache nem listener, fora do motor: na tela, as faces da remota mostrada e o teste da máquina trocada, e no servidor o `updateReplaced` do Atualizar), `RemoteIndex` (o índice do mixin e da carga de chunk, para acordar as remotas; `routersAt(pos)` dá os roteadores que têm aquela posição vinculada, para o clique do Tablet) e `RangeLinks` (o modo Vincular do Vinculador: escolher o roteador, vincular uma máquina ou a área, sempre sem rede) e `IdleNetworkFace` (uma face que o "Tirar redes" limpou, com a rede que tinha, para o Desfazer). Spec em `docs/superpowers/specs/2026-10-11-roteador-de-alcance-design.md`

## `mixin/`

`LevelMixin` em `Level.blockEntityChanged`, o primeiro mixin do mod (`wirelessautomate.mixins.json`, declarado no `neoforge.mods.toml`): o `setChanged` de uma máquina remota vinculada acorda as portas dela, como o da presa. No caminho quente, só uma consulta no `RemoteIndex`

