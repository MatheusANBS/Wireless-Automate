# Wireless Automate

Transporte wireless de itens, fluidos, energia e químicos para **NeoForge 1.21.1** (ATM10). A meta é ser o transporte mais rápido do pack e o mais leve em TPS. A especificação completa está em [`docs/especificacao.md`](docs/especificacao.md).

## Começando

```bash
./scripts/setup.sh              # Linux, macOS ou WSL
.\scripts\setup.ps1             # Windows (PowerShell)
```

O script instala o que faltar (JDK 21, git, curl, unzip), baixa o Gradle pelo wrapper e roda o primeiro build, que baixa e decompila o Minecraft e o NeoForge. Opções: `--gametest` (`-GameTest`) roda também os GameTests; `--no-build` (`-NoBuild`) só instala as ferramentas. Sem permissão de root, o JDK vai para `.tools/jdk-21`.

Em um ambiente na nuvem do Claude Code, `bash scripts/setup.sh --no-build` serve como script de setup do ambiente.

## Comandos

| Comando | O que faz |
| --- | --- |
| `./gradlew build` | Compila, roda os testes de unidade e gera o jar em `build/libs/` |
| `./gradlew test` | Só os testes de unidade (JUnit, lógica sem Minecraft) |
| `./gradlew runGameTestServer` | Sobe um servidor sem tela, roda os GameTests do mod e sai com erro se algum falhar |
| `./gradlew runClient` | Abre o Minecraft com o mod (`/test runall` roda os GameTests no mundo) |
| `./gradlew runServer` | Servidor dedicado de dev |
| `./gradlew runData` | Geradores de dados, saída em `src/generated/resources/` |

O CI (`.github/workflows/build.yml`) roda `build` e `runGameTestServer` a cada push.

## Estrutura

```
├── build.gradle, settings.gradle, gradle.properties   projeto ModDevGradle (versões no gradle.properties)
├── scripts/setup.sh, setup.ps1                        setup do ambiente
├── docs/
│   ├── especificacao.md                               especificação do mod
│   ├── pacote-de-design.md                            notas do pacote de design (sprites e modelos)
│   └── preview/                                       rascunho visual (HTML) e folha de sprites
└── src/
    ├── main/java/io/github/matheusanbs/wirelessautomate/
    │   ├── WirelessAutomate.java     classe @Mod, eventos
    │   ├── Config.java               config do servidor: orçamento de tempo e vazão por tier
    │   ├── block/                    RouterBlock, RouterBlockEntity, RouterTier, formas
    │   ├── item/                     núcleos de tier, configurador, tablet, vinculador, cartão, upgrade
    │   ├── network/                  NetworkManager (gerenciador central), TickBudget, tipos e modos
    │   ├── registry/                 DeferredRegisters de blocos, itens, block entities e aba criativa
    │   ├── command/                  /wa profile
    │   └── gametest/                 GameTests
    ├── main/resources/
    │   ├── assets/wirelessautomate/  blockstates, modelos, texturas e traduções (en_us, pt_br)
    │   └── data/                     loot table, receita, tags e a estrutura vazia dos GameTests
    ├── main/templates/META-INF/neoforge.mods.toml
    └── test/java/                    testes de unidade (JUnit 5)
```

## O que já existe

- Roteador direcional (`facing` = face da máquina onde foi preso, `tier` de Básico a Ultimate), com forma de colisão que acompanha a rotação, quebra quando a máquina sai e guarda o tier ao ser quebrado.
- Núcleos de tier sobem o roteador um tier por vez, sem perder o block entity.
- Os sete itens registrados, com sprites; o comportamento de configurador, tablet, vinculador, cartão e upgrade ainda é stub.
- Gerenciador central com orçamento de tempo adaptativo ao MSPT (o loop de rotas ainda é `TODO`) e `/wa profile`.
- Config do servidor com os números da tabela de tiers.

O próximo passo é o roadmap v1 da especificação.
