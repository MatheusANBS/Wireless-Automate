# Armazenamentos do mod

Baú, Tanques e Bateria, as telas e as buscas. Parte do mapa do código (pacote base `src/main/java/io/github/matheusanbs/wirelessautomate/`). Índice em [CLAUDE.md](../../CLAUDE.md).

## `storage/`

Armazenamentos do mod (0.2): Baú, Tanque, Bateria, Tanque Químico e Tanque de Source (`StorageKind`; o de Source é sempre registrado e some da aba criativa, do JEI e da receita sem o Ars), um bloco (`StorageBlock`), um item (`StorageBlockItem`) e um block entity por tipo sobre a base `StorageBlockEntity` (tier, capacidade pela config, filtro de entrada, quebrar e colocar pelo `StorageSavedData` com o tipo, o tier e a data: só guarda quando há drop e, com prazo no `storage.stashExpiryDays` (padrão sem prazo), vence, comparador; cada mudança do conteúdo marca o chunk e entra uma vez na fila do `StorageNeighbors`, que avisa os vizinhos no fim do tick). Conteúdo por tipo em `KeyedStorage<K>` (`ItemStorage`, `FluidStorage`, `ChemicalStorage` por id, sem tipos do Mekanism) ou `ScalarStore` (energia e Source, em `long`; a base dos dois block entities é `ScalarStorageBlockEntity`); visões comuns `ItemStorageHandler`, `FluidStorageHandler`, `EnergyStoreHandler` (a de químico em `compat/mekanism/MekanismStorage`); `BulkItems`, `BulkFluids`, `BulkEnergy` e `BulkSource` (capability `wirelessautomate:bulk_source`) para o roteador (as APIs do NeoForge tiram uma pilha, ou até 2^31 mB/FE, por chamada; a de químico já é `long`); `StorageMath` com JUnit (inclusive o nível `fill` 0 a 10 que o Tanque de Source mostra na coluna de vidro; o nível se corrige no tick seguinte à carga e, com o jogo rodando, quando a config muda, pelo `SourceTankLevels`, o conjunto dos tanques carregados no servidor; o tick é agendado no `onLoad`)

## `menu/StorageList*`, `ListKind`, `StorageScalarMenu`, `StorageFilterTarget`

Telas dos armazenamentos: a lista genérica do Baú e dos Tanques (`StorageListMenu<K>` com a visão `StorageListView<K>`; só o inventário do jogador como slots, só as diferenças por tipo a cada 5 ticks e com a tela aberta, cliques validados no servidor; `ListKind` diz como cada chave viaja e se compara), a da Bateria e do Tanque de Source (`StorageScalarMenu`, estado pelo `ScalarStatePayload`) e o filtro de entrada na tela de filtro

## `client/storage/`

As telas dos armazenamentos. `StorageListScreen` (pública, anfitriã da lista do Baú e dos Tanques com busca e ordenação, redimensionável em torno do centro: tamanho, cabeçalho, busca, inventário, dicas e os ganchos do e2e e do JEI) com as partes `StorageListLayout` (as medidas), `ListSort` (a ordem), `ShownList` (os tipos mostrados: a busca começa vazia, `#` procura em tags e tooltip pela divisão do `StorageSearch`, puro com JUnit, e o Shift trava a ordem como no AE2), `StorageKeys` (nome, mod, texto da busca e desenho de cada tipo: item, fluido ou químico), `StorageGrid` (a grade: células, rolagem, barra, cliques, Shift + arrasto e a rodinha) e `UsageLine` (a ocupação); e `StorageScalarScreen` (Bateria e Tanque de Source)

