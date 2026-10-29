# Núcleo do mod, config, comandos e bench

A classe do mod, a config, os registros, os comandos, o chunk loading e o bench. Parte do mapa do código (pacote base `src/main/java/io/github/matheusanbs/wirelessautomate/`). Índice em [CLAUDE.md](../../CLAUDE.md).

## `WirelessAutomate.java`

Classe `@Mod`: registra os DeferredRegisters e a config, e escuta os eventos de tick, parada do servidor e comandos

## `GuiTheme.java`, `ClientConfig.java`

O tema das telas (`GuiTheme`: `LIGHT`, `DARK`, `other()`) e a config do cliente (`ClientConfig`, `config/wirelessautomate-client.toml`: seção `gui`, chave `theme`, padrão `LIGHT`; seção `tv`, `pixelsPerBlock` 16, 32 ou 64 e `vramBudgetMb` 1024). Os dois ficam no pacote base e não importam nada de `client/`, porque o servidor dedicado carrega a classe da config. A config do servidor é a `Config`

## `Config.java`

Config do servidor (a do cliente é o `ClientConfig`), por seção: `performance` (`tickBudgetMs`, `adaptiveBudget`), `tiers` (vazão, alcance e dimensão por tier), `chunkLoading` (`enabled`, `maxChunksPerPlayer`), `linker` (`maxAreaVolume`, `maxDistance`, valem também para o Configurador), `tiers.<tier>.machineRadius` e `maxMachines` (raio e limite de máquinas do Upgrade de Alcance; padrões na `RangeTiers`), `networks` (`maxNetworksPerPlayer`, `maxGroupsPerPlayer`, `maxMembersPerNetwork` 32 e `maxTeamsPerNetwork` 8; 0 = sem limite e operadores sem teto), `guide.giveOnFirstJoin` e `migration.balanceVersion` (na primeira carga, `migrateBalance()` passa os valores ainda no padrão antigo para o balanceamento atual: o 1 é a escada ×8, o 2 é a energia do roteador Unobtainium ×8 e o FE por cristal do Ressonador, o 3 (`migrateBalance3`) é a escada nova do Ressonador e a energia do roteador por tier, trocando só o que ainda está no padrão do 2, o 4 (`migrateBalance4`) é o FE por cristal do Unobtainium de 184M para 10,7M, só se ainda está em 184M, e o 5 (`CURRENT_BALANCE = 5`, `migrateBalance5`) é a energia do roteador e a capacidade da Bateria ligadas ao Ressonador, trocando só o que ainda está no padrão do 4), `tv` (`maxWidth`, `maxHeight`, `maxElements` 128, `maxBindings` 64, `viewDistance` 64, `maxComponents` 64 e `maxThemes` 32), `storage.stashExpiryDays` (prazo do conteúdo de um armazenamento quebrado, padrão 0 = para sempre; com prazo, em dias de servidor ligado), `resonator` (`crystalLifeTicks` e `cutCrystalLifeTicks`, a vida de referência a 20 min; `crystalLifeMinutes`, lista de 8, a vida por tier do Núcleo, padrão 20, 20, 15, 10, 5, 5, 4, 3; `rechargeTicks`; `crystalIntegrity`, padrão 8, as cargas até rachar; `recastCrystals`, padrão 4; `interferenceRadius` 32, `interferencePenalty` 0,15 e `interferenceMax` 0,60; `ecoBonus` 0,05 e `ecoMax` 0 = sem teto; e `resonator.<tier>.fePerCrystal`, padrões no `CoreMath`)

## `chunk/`

Upgrade de chunk loading: tickets do NeoForge (`RouterChunkLoader`), eventos de tick, config e parada (`ChunkLoaderEvents`) e o estado mostrado na tela (`ChunkLoadState`)

## `registry/`

`ModBlocks`, `ModItems`, `ModBlockEntities`, `ModCreativeTabs`, `ModDataComponents`, `ModMenus`, `ModRecipes`

## `bench/`

`/wa bench` e o modo automático do `scripts/bench.sh` (os cenários no `BenchScenario`; o `tablet-range` abre o Tablet sobre roteadores de alcance, com o gancho `BenchScene.openRangeRows` para as linhas que ele abre; o `BenchTablet` é o Tablet aberto pelo jogador falso, com o custo, os bytes e a contagem dos pacotes); o benchmark comparativo com o Logistics Network (`BenchTransport`, o adaptador `LogisticsNetworkBench`, só por reflexão, e `LnChannelPlan`, puro com JUnit; ver `docs/benchmark-logistics-network.md`)

## `command/WaCommand.java`

`/wa profile` (com as linhas do Ressonador, dos Fluxos, das Telas e do envio das artes das Telas), `/wa network ...` (`list`, `create`, `use`, `remove` e, no `NetworkAccessCommand`, `invite` e `uninvite <jogador> <rede>` e `team add|remove "<time>" <rede>`, para o dono e operadores), `/wa face ...` (os tipos são os do registro, inclusive `chemical` com o Mekanism; `/wa face <pos> <tipo> <face> network <nome|none>` põe ou tira a rede da face), `/wa bench ...` e `/wa recipes` (abre o editor de receitas; permissão 2), `/wa storage list` e `recover` (`StorageCommand`: devolve como item o conteúdo de um armazenamento que sumiu, no tier gravado)

