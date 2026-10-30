# Wireless Automate · guia para agentes

Mod NeoForge 1.21.1 (Java 21) de transporte wireless de itens, fluidos, energia e químicos, feito para o ATM10. A conversa com o dono é em português; código, comentários e docs também.

## Leia antes de começar

1. **`docs/progresso.md`**: o que está pronto, o que falta e o **próximo passo**. Quando pedirem para "continuar de onde parou", comece por ali.
2. **`docs/especificacao.md`**: a fonte da verdade do design (componentes, tiers, telas, filtros, redes, arquitetura de performance, roadmap).
3. `docs/pacote-de-design.md`: convenções dos sprites e do modelo do roteador.

Ao terminar uma sessão, **atualize `docs/progresso.md`**: a tabela de estado, o próximo passo e uma linha no histórico.

## Skills do projeto (`.claude/skills/`)

Cada skill tem a descrição completa no próprio `SKILL.md`; aqui só quando usar:

- `wa-performance`: **em toda tarefa que mexe em código** (custo antes do visual; varredura `python -I .claude/skills/wa-performance/scripts/varrer_perf.py`; bench antes e depois em `references/medir.md`).
- `wa-feature`: feature, tela ou item novo, do mockup ao commit, com um subagente por tarefa.
- `wa-modular`: limites de tamanho por classe (tela até 500 linhas, servidor até 1000) e como dividir; `python -I .claude/skills/wa-modular/scripts/medir.py`.
- `wa-comentarios`: regras dos comentários; `python -I .claude/skills/wa-comentarios/scripts/varrer.py` antes de cada commit.
- `wa-shaders`: efeito visual ou referência a replicar (GIF, vídeo, outro mod).
- `wa-mod-opcional`: integrar outro mod opcional sem quebrar o mod quando ele não está instalado.
- `wa-release`: fechar uma versão (changelogs, tag, release com o jar).
- `wa-porte`: portar o mod para outra versão de Minecraft (estado dos ramos em `docs/portes.md`).
- `wa-curseforge-imagens`: imagens da página do CurseForge e os links da descrição.

## Mapa do código

Pacote base: `src/main/java/io/github/matheusanbs/wirelessautomate/`. O mapa detalhado (o que cada classe faz, chaves de NBT, regras) fica em `docs/mapa/`, **um arquivo por área: leia só o da área em que você vai mexer**, não todos. Ao mudar uma classe, atualize a linha dela no arquivo da área.

| Área | Arquivo | O que tem |
| --- | --- | --- |
| Núcleo, config, comandos, registros, chunk loading, bench | [nucleo](docs/mapa/nucleo.md) | `WirelessAutomate`, `Config`, `ClientConfig`, `registry/`, `command/`, `chunk/`, `bench/` |
| Roteador, Vinculador, Configurador, tela do roteador | [roteador](docs/mapa/roteador.md) | `block/`, `item/`, `linker/`, `preset/`, `menu/Router*`, `client/router/`, `client/linker/`, `client/highlight/` |
| Motor de transferência e redes | [motor](docs/mapa/motor.md) | `network/`: gerenciador, rotas, portas, transferências, Fluxos, redes salvas |
| Roteador de alcance | [alcance](docs/mapa/alcance.md) | `range/`, `mixin/` |
| Tablet (a central) | [tablet](docs/mapa/tablet.md) | `menu/Tablet*`, `client/tablet/`, `NodeIndex` |
| Filtros | [filtro](docs/mapa/filtro.md) | `filter/`, `menu/Filter*`, `client/filter/` |
| Armazenamentos (Baú, Tanques, Bateria) | [armazenamento](docs/mapa/armazenamento.md) | `storage/`, `client/storage/` |
| Ressonador de Sinal | [ressonador](docs/mapa/ressonador.md) | `resonator/`, tela do Núcleo e câmara animada |
| Reparador de Cristais | [reparador](docs/mapa/reparador.md) | `repairer/` |
| Tela de Sinal | [tela-de-sinal](docs/mapa/tela-de-sinal.md) | `tv/`, `client/tv/` (editor, peças, layouts, biblioteca, desenho) |
| Integrações opcionais | [compat](docs/mapa/compat.md) | JEI, Mekanism, FTB Teams, Ars Nouveau |
| Receitas e o editor de receitas | [receitas](docs/mapa/receitas.md) | `recipe/`, `recipe/edit/`, `client/recipe/` |
| Cliente geral, pacotes, ferramentas de dev | [cliente-e-pacotes](docs/mapa/cliente-e-pacotes.md) | `packet/`, `client/` (tema, `GuiText`, e2e, capturas) |
| Testes | [testes](docs/mapa/testes.md) | GameTests por assunto e as runs separadas |
| Lógica pura (JUnit) | [logica-pura](docs/mapa/logica-pura.md) | as classes sem Minecraft |
| Scripts e recursos | [scripts-e-recursos](docs/mapa/scripts-e-recursos.md) | texturas, guia, CurseForge, `assets/`, `data/` |

## Comandos

```bash
./scripts/setup.sh          # instala o JDK 21 etc. e compila (use --gametest / --no-build)
./gradlew build             # compila + JUnit; jar em build/libs/
./gradlew test              # só JUnit
./gradlew runGameTestServer # GameTests headless (sem o Mekanism); falha o build se algum teste falhar
./gradlew runGameTestServerChemicals # GameTests de químicos, num servidor com o Mekanism na pasta mods
./gradlew runGameTestServerSource # GameTests de Source, num servidor com o Ars Nouveau (e GeckoLib e Curios) na pasta mods
./gradlew runGameTestServerAllthemodium # GameTests dos tiers do ATM, com o Allthemodium, o All The Tweaks e o GeckoLib (cursemaven)
./gradlew runGameTestServerTeams # GameTests de times, num servidor com o FTB Teams, o FTB Library e o Architectury na pasta mods (maven.ftb.dev)
./gradlew runClient         # cliente de dev com JEI, Sophisticated Storage, Observable, Mekanism, Ars Nouveau (com GeckoLib e Curios), Allthemodium, All The Tweaks, Apotheosis (com as dependências dele), GuideME, Mouse Tweaks, AllTheCompressed, Occultism (com Modonomicon e SmartBrainLib) e Just Dire Things, para testar à mão
./gradlew runData           # datagen para src/generated/resources/
WA_SCREENSHOT=run/shots xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew runClient  # capturas das telas (roteador, visor 3D, filtro), sem monitor
./scripts/e2e.sh            # e2e num mundo real, rápido por padrão (sem capturas, sem pt_br e escuro, sem a comparação antigo × plasma): `--only a,b` (suítes), `--skip`, `--capture <prefixos|todas>`, `--variants pt,escuro`, `--completo` (tudo), `--lista`; no Windows abre uma janela de verdade (veja docs/e2e.md)
./scripts/bench.sh <cenários> # benchmark num servidor dedicado com Sophisticated Storage (ver docs/benchmark.md)
./scripts/bench.sh comparativo # o mesmo contra o Logistics Network (baixa o jar dele para run/bench-ln, fora do git; ver docs/benchmark-logistics-network.md)
```

Antes de commitar, rode `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams` (e o `./scripts/e2e.sh` se mexeu em tela ou payload). O CI (`.github/workflows/build.yml`) roda os GameTests.

## Convenções e armadilhas

- **Pastas de dados do 1.21.1 são no singular:** `recipe/`, `loot_table/`, `structure/`, `tags/block/`, `tags/item/`.
- **GameTests:** a classe leva `@GameTestHolder(WirelessAutomate.MODID)` e `@PrefixGameTestTemplate(false)`, e o template é `"empty"`. Os testes do mesmo lote rodam em paralelo e dividem o `NetworkManager`, então teste pertinência (`contains`) e não contagem. Também dividem o `NetworkSavedData`: cada teste cria a própria rede (`NetworkSavedData.get(server).create(...)`), porque uma rede que não existe lá deixa a face parada. Compare redes (que podem ser nulas) com `Objects.equals`: o `assertValueEqual` com valor nulo derruba o servidor de GameTest, não só o teste. O `onLoad` de um block entity recém-colocado só roda no tick seguinte: use `startSequence().thenWaitUntil(...)`. Jogador de teste no mundo só pelo `TestPlayers.inLevel` (ou `removedAtEnd` para um jogador próprio): sai da lista quando o teste termina, porque a grade reaproveita as posições entre lotes e um jogador esquecido impede colocar bloco ali. Teste que grava a config (`Config.SPEC.save()`, `migrateBalance()`) fica num lote só dele e espera a recarga do arquivo, que chega pelo relógio e remonta as redes.
- **Lógica pura sem classes do Minecraft** (como `TickBudget`) vai com teste JUnit em `src/test/java`. Lógica que depende do jogo vai com GameTest.
- **Performance é requisito**, não detalhe (ver "Arquitetura de performance" na especificação e a skill `wa-performance`, que vale em toda tarefa):
  - nada de tick por bloco;
  - nada de busca de capability por tick, use `BlockCapabilityCache`;
  - nada de sincronizar o cliente com a tela fechada.
- **Mekanism é opcional:** tipos da API dele (`IChemicalHandler`, `ChemicalStack`...) só em `compat/mekanism`, `ChemicalTransfer` e `gametest/ChemicalTestSupport`, e **nunca na assinatura de um método** de classe `@EventBusSubscriber` ou `@GameTestHolder`: o NeoForge inspeciona essas classes por reflexão e o mod deixa de carregar sem o Mekanism. O `runGameTestServer` roda sem ele e pega isso.
- **Ars Nouveau é opcional:** tipos da API dele só em `compat/arsnouveau` e `gametest/SourceTestSupport`, e nunca na assinatura de um método de classe `@EventBusSubscriber` ou `@GameTestHolder` (mesmo motivo do Mekanism).
- **FTB Teams é opcional:** tipos da API dele só em `compat/ftbteams` e `gametest/TeamTestSupport`, e nunca na assinatura de um método de classe `@EventBusSubscriber` ou `@GameTestHolder` (mesmo motivo do Mekanism). O resto passa pela ponte `TeamAccess`. A dependência é `compileOnly` (FTB Teams e FTB Library, sem transitivas); o mod completo vai para o `runClient` e a run `gameTestServerTeams`.
- **Allthemodium é opcional e sem código:** os tiers Allthemodium, Vibranium e Unobtainium existem sempre no enum (o bloco continua valendo se o mod sair) e só entram na escada, na aba criativa e nas receitas com o mod (`RouterTier.loaded()`). Receitas por tags `c:` e `neoforge:conditions`; o Ultimate tem três arquivos e só um carrega (sem ATM, com ATM, com ATM + All The Tweaks). Nada de tipos ou `compileOnly` desses mods.
- **Tiers:** vazão e capacidade padrão vêm de arrays pela posição do `RouterTier` (`ResourceType`, `StorageKind`), ×8 por degrau, menos a energia do roteador e a capacidade da Bateria, que vêm do `EnergyBalance` (a geração máxima do Ressonador do tier mais 5% e uma hora dela, o Ultimate sem limite); um tier novo pede um valor em cada array, a cor no `GuiPaint.tierColor` e a paleta no `gerar_texturas.py`, que gera também os modelos e blockstates por tier.
- **Lado do cliente:** classes de tela só em `client/`, nunca referenciadas por código comum (o `runGameTestServer` é um servidor dedicado e quebra se carregar uma).
- **Memória:** cada build/jogo usa ~3 GB; não rode vários clientes ou servidores ao mesmo tempo (um cliente do e2e morreu assim).
- **Rotação:** o `facing` do roteador segue a convenção do para-raios (`up` sem rotação, `down` x=180, laterais x=90 + y). O `spin` (0 a 3) gira em torno do eixo do `facing`, antes da rotação do `facing`: no caso `up`, horário visto de cima (`getClockWise`), `spin` vezes. Os modelos giram pela transformação raiz (`router_<tier>_spinN`, y = −90 × spin) e a colisão pelo `RouterShapes`. Configurações por face devem ser salvas em relação ao `facing` + `spin` (`RelativeSide`).
- **Teste que muda a config** (`Config.*.set`) vai no lote `batch = "wirelessautomate_config"`, para não rodar em paralelo com os outros lotes, e devolve o valor em `try/finally`. Os testes do mesmo lote rodam em paralelo entre si: dois que mudam a mesma chave e a deixam mudada por mais de um tick vão em lotes diferentes (`wirelessautomate_config_<assunto>`, como os dois do `crossDimension` do Elite).
- **Nome de máquina de outro mod:** sempre por `RouterBlock.machineName(bloco)`, nunca `bloco.getName()`: o nome de alguns blocos tem lacunas que só o item preenche (os barris do Sophisticated Storage aparecem como `%s%sBarrel` pelo bloco).
- **Máquinas remotas:** vincule e desvincule só pelos métodos do roteador (`RouterBlockEntity.linkMachine`/`unlinkMachine`), que mantêm o índice do mixin (`RemoteIndex`) e os caches (`MachineCaches`). Uma máquina remota nunca é consultada sem `level.isLoaded(pos)` antes: consultar um chunk descarregado o carrega à força.
- **Primeiro build:** leva uns 4 minutos (baixa e decompila o Minecraft). O erro `Failed to load properties from file: server.properties` no `runGameTestServer` é normal. Os GameTests rodam em `run/gametest`, com o mundo apagado a cada rodada.
- **Wrapper:** `gradle-wrapper.properties` usa `validateDistributionUrl=false`, porque a validação falha atrás do proxy do ambiente na nuvem.
- **Commits:** sem linhas de atribuição (`Co-Authored-By`, `Claude-Session`) e com o autor do dono; o repositório é público.
- **Versões:** NeoForge, Parchment e mod ficam no `gradle.properties`. O `neo_version` vira a versão **mínima** exigida no `neoforge.mods.toml`: não suba: hoje é 21.1.248 (a de um pack do dono; o ATM10 traz a 21.1.251), senão o mod não carrega nesses packs. O plugin ModDevGradle fica no `build.gradle`.
