# Wireless Automate

Transporte wireless de itens, fluidos, energia, químicos e Source para **NeoForge 1.21.1** (Java 21), feito para o ATM10. A meta é ser o transporte mais rápido do pack e o mais leve em TPS: um gerenciador central, sem tick por bloco, com teto de tempo por tick.

**Estado:** versão 1.1 (1.1.0 pronta para lançar; a última publicada é a 1.0.2). Baixe o jar na [release mais recente do GitHub](https://github.com/MatheusANBS/Wireless-Automate/releases/latest), que tem também o changelog. Requer NeoForge 21.1.251 ou mais novo (roda no ATM10). Também no CurseForge (projeto 1732160). Licença All Rights Reserved.

## Recursos

- **Roteador Wireless:** preso a qualquer face de uma máquina, acessa todas as faces dela.
- **Redes por aba:** cada aba do roteador (Itens, Fluidos, Energia, Químicos e Source) entra na sua própria rede; tudo do mesmo tipo na mesma rede troca entre si.
- **Modos por face e por tipo:** Extrair, Inserir, Armazém ou Nenhum, com prioridade, round-robin no empate e controle por redstone.
- **Filtros sem limite:** item ou fluido exato, tag e mod, lista branca ou negra, limite de estoque, e Cartões de Filtro (dois slots por face e por tipo, copiáveis na bancada). A tela é redimensionável: ponha um item no inspetor para ver e marcar todas as tags dele, ou busque em todas as tags do jogo com a prévia dos itens.
- **Regras por propriedade** (1.0): pegue qualquer item encantado, danificado, renomeado, com poção, não empilhável ou com conteúdo, um encantamento com nível mínimo ou a durabilidade abaixo de X%, limitado a uma tag ou mod (só armadura encantada, ferramentas para o reparo).
- **Quatro tiers**, subidos com os Cartões de Upgrade Avançado, Elite e Ultimate (não há cartão Básico, o roteador já nasce Básico), clicando no roteador colocado ou juntando os dois na bancada.
- **Vinculador:** escolhe a rede ativa e põe roteadores nela, um a um ou por área, nas abas marcadas (Itens, Fluidos, Energia e, com o Mekanism e o Ars Nouveau, Químicos e Source); em "Nenhuma (desvincular)", tira essas abas da rede.
- **Configurador:** copia a configuração de um roteador e cola em outro, ou em todos os de uma área presos ao mesmo tipo de máquina; todas as abas ou só um tipo, Source incluída (Shift + roda do mouse).
- **Upgrade de chunk loading:** mantém carregado o chunk do roteador e o da máquina, com limite por jogador na config.
- **Tablet de Rede:** lista, mapa, estatísticas, redes e grupos à distância, e abre a tela do roteador de longe.
- **Baú Wireless** (1.0): guarda itens por tipo e quantidade, sem slots e com tipos ilimitados, até a capacidade do tier (262.144, 16.777.216, 1.073.741.824 itens e sem limite no Ultimate), subida com os mesmos Cartões de Upgrade. Entre dois Baús, o roteador move um tipo inteiro numa operação só. Tem tela em lista com busca (`@mod`) e ordenação, filtro de entrada (o estoque vira "guardar até N") e sinal de comparador. Quebrado, leva o conteúdo e o filtro no item. Para os outros mods, é um inventário comum.
- **Tanque, Bateria e Tanque Químico Wireless** (1.0): o mesmo molde do Baú para fluidos (vários por tanque, até 4 bilhões de mB no Elite), energia (até 64 bilhões de FE no Elite) e químicos do Mekanism (só com ele). Sem limite no Ultimate. O Tanque troca baldes direto no bloco e pela tela (recipientes no cursor), a Bateria mostra a carga e a variação por tick. Para os outros mods e para o roteador, são um tanque, uma bateria e um tanque de químico comuns, que já passam bilhões por chamada.
- **Tanque de Source Wireless** (1.1, só com o Ars Nouveau): jarra fina com a coluna de vidro mostrando o nível, de 160.000 a 40.960.000 de Source (sem limite no Ultimate). Os Sourcelinks num raio de 5 blocos depositam nele e as máquinas do Ars por perto tiram dele como de uma Source Jar; entre dois tanques o roteador move tudo numa operação. Mesmos Cartões de Upgrade, leva o conteúdo ao quebrar, comparador e tela própria.
- **Telas redimensionáveis** (1.1): roteador, Tablet e Vinculador mudam de tamanho pela borda e pelo canto; as abas do roteador se adaptam à largura e o texto que não cabe é abreviado, com o tooltip inteiro.
- **Livro-guia (GuideME):** na aba criativa e entregue a cada jogador no primeiro login.

| Tier | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Source/s | Alcance |
| --- | --- | --- | --- | --- | --- |
| Básico | 512 | 32.000 | 16.000 | 1.000 | 128 blocos |
| Avançado | 8.192 | 512.000 | 256.000 | 16.000 | 1.024 blocos |
| Elite | 131.072 | 8.000.000 | 4.000.000 | 256.000 | Dimensão inteira |
| Ultimate | Sem limite | Sem limite | Sem limite | Sem limite | Entre dimensões |

A vazão vale por face e por tipo (`sourcePerSecond` é a da Source). Os valores ficam na config do servidor (`config/wirelessautomate-server.toml`), assim como a capacidade dos armazenamentos por tier (`storage.chestCapacity`, `tankCapacity`, `batteryCapacity` e `chemicalTankCapacity` e `sourceTankCapacity`).

## Integrações opcionais

| Mod | O que traz |
| --- | --- |
| Mekanism | Químicos: aba Químicos no roteador, com filtro por químico ou mod, e o Tanque Químico Wireless |
| Ars Nouveau (5.2 ou mais novo) | Source: aba Source no roteador (Source Jars, Relays e Imbuement Chamber ligam direto) e o Tanque de Source Wireless |
| JEI | Arrastar e Shift + clique para os filtros (e para o inspetor de tags), e a receita de upgrade na bancada (roteador e armazenamentos) |
| GuideME | O livro-guia, em inglês e português |

Sem um deles, a parte correspondente não carrega e o resto funciona normal.

## Desenvolvimento

```bash
./scripts/setup.sh              # Linux, macOS ou WSL
.\scripts\setup.ps1             # Windows (PowerShell)
```

O script instala o que faltar (JDK 21, git, curl, unzip) e roda o primeiro build, que baixa e decompila o Minecraft (uns 4 minutos). Opções: `--gametest` (`-GameTest`) roda também os GameTests; `--no-build` (`-NoBuild`) só instala as ferramentas.

| Comando | O que faz |
| --- | --- |
| `./gradlew build` | Compila, roda os testes JUnit e gera o jar em `build/libs/` |
| `./gradlew test` | Só os testes JUnit (lógica sem Minecraft) |
| `./gradlew runGameTestServer` | GameTests num servidor sem tela (sem o Mekanism); falha se algum teste falhar |
| `./gradlew runGameTestServerChemicals` | GameTests de químicos, num servidor com o Mekanism |
| `./gradlew runGameTestServerSource` | GameTests de Source, num servidor com o Ars Nouveau (e GeckoLib e Curios) |
| `./gradlew runClient` | Cliente de dev com JEI, Sophisticated Storage, Observable, Mekanism, Ars Nouveau (com GeckoLib e Curios) e GuideME, para testar à mão |
| `./gradlew runData` | Geradores de dados, saída em `src/generated/resources/` |
| `./scripts/e2e.sh` | Teste de ponta a ponta num mundo real (precisa de Xvfb) |
| `./scripts/bench.sh <cenários>` | Benchmark num servidor dedicado com Sophisticated Storage (ver `docs/benchmark.md`) |

O CI (`.github/workflows/build.yml`) roda `build`, `runGameTestServer`, `runGameTestServerChemicals` e `runGameTestServerSource` a cada push e pull request.

## Organização

O mapa do código-fonte fica no [`CLAUDE.md`](CLAUDE.md) ("Mapa do código").

- `docs/especificacao.md`: especificação do mod (fonte da verdade do design).
- `docs/progresso.md`: o que está pronto, o que falta e o próximo passo.
- `docs/benchmark.md`: como rodar o benchmark e os resultados.
- `docs/pacote-de-design.md`: sprites e modelo do roteador.
- `docs/curseforge/`: descrição, capa, banner e imagens da página do CurseForge.
- `scripts/`: `setup.sh`/`setup.ps1`, `e2e.sh`, `bench.sh`, `textures/` (gera as texturas), `guide/` (gera as páginas do guia) e `curseforge/` (gera as imagens do CurseForge).
