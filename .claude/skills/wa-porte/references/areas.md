# Áreas do código e o que costuma mudar entre versões

Ponto de partida para a tabela do relatório de impacto. **São hipóteses de onde olhar**, escritas de
memória: cada uma precisa de fonte (primer, notas, código do alvo) antes de entrar no relatório como
"confirmado" ou "provável".

| Área | Onde fica aqui | O que verificar no alvo |
| --- | --- | --- |
| Build | `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper`, `src/main/templates/META-INF/neoforge.mods.toml` | Plugin (ModDevGradle, o modo legado do MDG para o Forge 1.20.1, ou ForgeGradle), Java (17 no 1.20.1, 21 no 1.21.1, 25 no 26.1 segundo as notas do NeoForge), Gradle mínimo, mapeamentos (Parchment no 1.21.1; o 26.1 vem sem ofuscação), nome do arquivo de metadados (`mods.toml` no Forge, `neoforge.mods.toml` no NeoForge), as runs de GameTest com mods opcionais |
| Registros | `registry/` | `DeferredRegister`, `DeferredHolder`/`DeferredBlock`/`DeferredItem` × `RegistryObject` (Forge); se o item e o bloco precisam do id na construção (`Item.Properties`) |
| Componentes de item | `registry/ModDataComponents`, `item/`, `storage/StorageBlockItem` | Só existem do 1.20.5 em diante: no 1.20.1 o estado do item (preset, modo, área, tipo, abas, conteúdo do armazenamento) volta a ser NBT |
| Rede cliente-servidor | `packet/`, `menu/*Snapshot*` | `CustomPacketPayload` e `StreamCodec` só do 1.20.5 em diante; no Forge 1.20.1, `SimpleChannel` e `FriendlyByteBuf`. Versão do protocolo (`ModPayloads.VERSION`) |
| Codecs e NBT | `filter/`, `network/RouterPreset`, `storage/`, `*SavedData` | `RegistryOps`/`HolderLookup` nos codecs de item; getters de NBT (no 1.21.5 passaram a devolver `Optional`, segundo os primers); como o block entity salva (`saveAdditional`/`loadAdditional` × a API nova de valores) |
| Capabilities e transporte | `block/RouterBlockEntity`, `network/*Transfer`, `storage/*Handler`, `storage/Bulk*` | `BlockCapabilityCache` (NeoForge 1.20.3+); no Forge 1.20.1, `LazyOptional` e um cache próprio com invalidação; os handlers de item, fluido e energia (o NeoForge da linha 1.21 ganhou uma API de transferência nova: confirmar se `IItemHandler` e companhia ainda existem no alvo) |
| Motor | `network/NetworkManager`, `TickBudget` etc. | Em geral não muda (lógica pura); confirme os eventos de tick do servidor |
| Receitas | `recipe/`, `data/wirelessautomate/recipe/` | `RecipeInput`/`CraftingInput` (1.21+), o sistema de receitas do 1.21.2 (receitas só no servidor, exibição no livro), as condições (`neoforge:conditions` × `forge:conditions`), pastas de dados no plural no 1.20.1 (`recipes/`, `loot_tables/`, `tags/blocks/`) |
| Modelos e texturas | `assets/wirelessautomate/`, `scripts/textures/` | Definições de modelo de item (pasta `items/` do 1.21.4 em diante), transformações raiz do blockstate (o giro do roteador depende delas), formato dos `.mcmeta` |
| Telas | `client/` | `GuiGraphics` existe desde o 1.20; o 1.21.6 mudou o desenho da GUI (estados de render e desenho adiado, segundo os primers): pesa no `ConfiguratorWheelScreen` (triângulos com `BufferBuilder`), no `MachineView3D` (bloco 3D na tela) e no `DevScreenshot` |
| Mundo e render | `client/AreaRenderer`, `client/MachineView3D` | Eventos de render do nível, `RenderType`, `PoseStack` |
| Chunk loading | `chunk/` | `TicketController` (NeoForge) × `ForgeChunkManager` (Forge) |
| Config | `Config.java` | `ModConfigSpec` (NeoForge) × `ForgeConfigSpec` (Forge) |
| Comandos | `command/` | Em geral estável; nível de permissão mudou de API no 26.1 segundo relatos de porte |
| GameTests | `gametest/`, `data/*/structure/empty.nbt` | `@GameTestHolder` e `@PrefixGameTestTemplate` no loader do alvo; o formato das estruturas; o GameTest do vanilla ganhou registro próprio no 1.21.5 (confirmar) |
| Mekanism | `compat/mekanism/`, `network/ChemicalTransfer` | No 1.21 os químicos são um tipo só; no Mekanism do 1.20.1 eram gás, infusão, pigmento e lama, cada um com o seu handler. A aba Químicos pode virar quatro sub-tipos ou só o gás |
| Ars Nouveau | `compat/arsnouveau/` | A capability de Source e o `SourceManager` mudam de versão para versão; confirmar se o pack do alvo traz o Ars |
| JEI | `compat/jei/` | Versão da API do JEI do alvo |
| GuideME | `assets/wirelessautomate/guideme_guides/`, `scripts/guide/` | Se existe GuideME para o alvo; senão, outro livro (Patchouli) ou nenhum |
| Allthemodium | receitas e tiers | Ids dos itens e tags no alvo; se o pack traz All The Tweaks (ATM Star) |
| Lado do cliente | tudo em `client/` | A regra de não referenciar classes de cliente vale em qualquer versão: o servidor dedicado dos GameTests prova |
