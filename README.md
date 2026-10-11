# Wireless Automate

Transporte wireless de itens, fluidos, energia, químicos e Source para **NeoForge 1.21.1** (Java 21), feito para o ATM10. A meta é ser o transporte mais rápido do pack e o mais leve em TPS: um gerenciador central, sem tick por bloco, com teto de tempo por tick.

> **[Read in English](README.en.md)**

**Estado:** versão 1.7 (1.7.1: o editor faz uma recarga por vez; editor de receitas para operadores: `/wa recipes` troca, desativa e restaura as receitas do mod dentro do jogo). Antes, 1.6 (roda do Configurador no Alt esquerdo e o modo Área em qualquer máquina) e 1.5 (1.5.0: controles no estilo do Mouse Tweaks no Baú, como Shift + arrastar, rodinha e Shift + duplo clique, e NeoForge 21.1.248; 1.5.1: o Baú enche slots de pilha grande de outros mods de uma vez; 1.5.2: identidade visual nova "Porcelana e Sinal", com itens animados, blocos 3D, telas claras e hitboxes justas). Baixe no [CurseForge](https://www.curseforge.com/minecraft/mc-mods/wireless-automate) ou na [release mais recente do GitHub](https://github.com/MatheusANBS/Wireless-Automate/releases/latest), as duas com o changelog. Requer NeoForge 21.1.248 ou mais novo (roda no ATM10).

## Recursos

- **Roteador Wireless:** preso a qualquer face de uma máquina, acessa todas as faces dela; Shift + clique direito com as mãos vazias gira o roteador 90° (1.3), sem mudar a configuração das faces.
- **Redes por aba:** cada aba do roteador (Itens, Fluidos, Energia, Químicos e Source) entra na sua própria rede; tudo do mesmo tipo na mesma rede troca entre si.
- **Modos por face e por tipo:** Extrair, Inserir, Armazém ou Nenhum, com prioridade, round-robin no empate e controle por redstone.
- **Filtros sem limite:** item ou fluido exato, tag e mod, lista branca ou negra, limite de estoque, e Cartões de Filtro (dois slots por face e por tipo, copiáveis na bancada). A tela é redimensionável: ponha um item no inspetor para ver e marcar todas as tags dele, ou busque em todas as tags do jogo com a prévia dos itens.
- **Regras por propriedade** (1.0): pegue qualquer item encantado, danificado, renomeado, com poção, não empilhável ou com conteúdo, um encantamento com nível mínimo (1.3: digitado pelo nome, com sugestões, e nível de 1 a 255) ou a durabilidade abaixo de X%, limitado a uma tag ou mod (só armadura encantada, ferramentas para o reparo).
- **Cinco tiers** (1.2; Básico, Avançado, Elite, Esmeralda e Ultimate), e **oito com o Allthemodium** (Allthemodium, Vibranium e Unobtainium entre a Esmeralda e o Ultimate), subidos com os Cartões de Upgrade (não há cartão Básico, o roteador já nasce Básico; desde a 1.4, cada cartão sobe de qualquer tier abaixo direto para o dele), clicando no roteador colocado ou juntando os dois na bancada. No ATM10, o Cartão Ultimate pede fragmentos de ATM Star.
- **Vinculador:** escolhe a rede ativa e põe roteadores nela, um a um ou por área, nas abas marcadas (Itens, Fluidos, Energia e, com o Mekanism e o Ars Nouveau, Químicos e Source); em "Nenhuma (desvincular)", tira essas abas da rede.
- **Configurador:** copia a configuração de um roteador e cola em outro, ou em todos os de uma área (só nos presos ao mesmo tipo de máquina, ou em qualquer uma); todas as abas ou só um tipo, Source incluída. Segurando Alt esquerdo, uma roda escolhe o modo e o tipo colado (1.6); Shift + clique no ar e Shift + roda do mouse também.
- **Upgrade de chunk loading:** mantém carregado o chunk do roteador e o da máquina, com limite por jogador na config.
- **Editor de receitas** (1.7): `/wa recipes`, só para operadores, abre uma tela para trocar, desativar e restaurar as receitas de bancada do mod (itens pelo inventário ou pelo JEI, tag no lugar do item, quantidade do resultado). Grava um datapack em `config/wirelessautomate/recipes/`, que vale em todos os mundos da instância e sobrevive a atualizações.
- **Tablet de Rede:** lista, mapa, estatísticas, redes e grupos à distância, e abre a tela do roteador de longe.
- **Baú Wireless** (1.0): guarda itens por tipo e quantidade, sem slots e com tipos ilimitados, até a capacidade do tier (de 32.768 itens no Básico a 16.777.216 na Esmeralda e 8.589.934.592 no Unobtainium, sem limite no Ultimate), subida com os mesmos Cartões de Upgrade. Entre dois Baús, o roteador move um tipo inteiro numa operação só. Tem tela em lista com busca (`@mod`) e ordenação, os gestos do Mouse Tweaks e do vanilla (Shift + arrastar, rodinha e Shift + duplo clique), filtro de entrada (o estoque vira "guardar até N") e sinal de comparador. Quebrado, leva o conteúdo e o filtro no item. Para os outros mods, é um inventário comum; como origem, enche slots de pilha grande (upgrades de pilha do Sophisticated, gavetas) de uma vez (1.5.1: 2,1 bilhões num barril com o Upgrade Ômega em um tick).
- **Tanque, Bateria e Tanque Químico Wireless** (1.0): o mesmo molde do Baú para fluidos (vários por tanque, até 131 milhões de mB na Esmeralda), energia (até 512 milhões de FE na Esmeralda) e químicos do Mekanism (só com ele). Sem limite no Ultimate. O Tanque troca baldes direto no bloco e pela tela (recipientes no cursor), a Bateria mostra a carga e a variação por tick. Para os outros mods e para o roteador, são um tanque, uma bateria e um tanque de químico comuns, que já passam bilhões por chamada.
- **Tanque de Source Wireless** (1.1, só com o Ars Nouveau): jarra fina com a coluna de vidro mostrando o nível, de 10.000 a 5.120.000 de Source na escada vanilla (sem limite no Ultimate). Os Sourcelinks num raio de 5 blocos depositam nele e as máquinas do Ars por perto tiram dele como de uma Source Jar; entre dois tanques o roteador move tudo numa operação. Mesmos Cartões de Upgrade, leva o conteúdo ao quebrar, comparador e tela própria.
- **Telas redimensionáveis** (1.1): roteador, Tablet e Vinculador mudam de tamanho pela borda e pelo canto; as abas do roteador se adaptam à largura e o texto que não cabe é abreviado, com o tooltip inteiro.
- **Livro-guia (GuideME):** na aba criativa e entregue a cada jogador no primeiro login.

| Tier | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Source/s | Alcance |
| --- | --- | --- | --- | --- | --- |
| Básico | 32 | 2.000 | 1.000 | 100 | 64 blocos |
| Avançado | 256 | 16.000 | 8.000 | 800 | 512 blocos |
| Elite | 2.048 | 128.000 | 64.000 | 6.400 | Dimensão inteira |
| Esmeralda | 16.384 | 1.024.000 | 512.000 | 51.200 | Entre dimensões |
| Allthemodium¹ | 131.072 | 8.192.000 | 4.096.000 | 409.600 | Entre dimensões |
| Vibranium¹ | 1.048.576 | 65.536.000 | 32.768.000 | 3.276.800 | Entre dimensões |
| Unobtainium¹ | 8.388.608 | 524.288.000 | 262.144.000 | 26.214.400 | Entre dimensões |
| Ultimate | Sem limite | Sem limite | Sem limite | Sem limite | Entre dimensões |

¹ Só com o mod Allthemodium; sem ele, a Esmeralda sobe direto para o Ultimate.

A vazão vale por face e por tipo (`sourcePerSecond` é a da Source). Os valores ficam na config do servidor (`<mundo>/serverconfig/wirelessautomate-server.toml`; num modpack, o padrão vai em `defaultconfigs/`), assim como o alcance, a capacidade dos armazenamentos por tier (`storage.chestCapacity`, `tankCapacity`, `batteryCapacity`, `chemicalTankCapacity` e `sourceTankCapacity`), o chunk loading e o tamanho da área do Vinculador. A capacidade conta o total guardado, sem limite de tipos diferentes.

## Integrações opcionais

| Mod | O que traz |
| --- | --- |
| Mekanism | Químicos: aba Químicos no roteador, com filtro por químico ou mod, e o Tanque Químico Wireless |
| Ars Nouveau (5.2 ou mais novo) | Source: aba Source no roteador (Source Jars, Relays e Imbuement Chamber ligam direto) e o Tanque de Source Wireless |
| Allthemodium (All The Tweaks opcional) | Os tiers Allthemodium, Vibranium e Unobtainium, e no ATM10 a receita do Cartão Ultimate com fragmentos de ATM Star |
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
| `./gradlew runGameTestServerAllthemodium` | GameTests dos tiers do ATM, com o Allthemodium, o All The Tweaks e o GeckoLib |
| `./gradlew runClient` | Cliente de dev com JEI, Sophisticated Storage, Observable, Mekanism, Ars Nouveau (com GeckoLib e Curios), Allthemodium, All The Tweaks e GuideME, para testar à mão |
| `./gradlew runData` | Geradores de dados, saída em `src/generated/resources/` |
| `./scripts/e2e.sh` | Teste de ponta a ponta num mundo real (precisa de Xvfb) |
| `./scripts/bench.sh <cenários>` | Benchmark num servidor dedicado com Sophisticated Storage (ver `docs/benchmark.md`) |

O CI (`.github/workflows/build.yml`) roda `build`, `runGameTestServer`, `runGameTestServerChemicals`, `runGameTestServerSource` e `runGameTestServerAllthemodium` a cada push e pull request.

## Organização

O mapa do código-fonte fica no [`CLAUDE.md`](CLAUDE.md) ("Mapa do código").

- `docs/especificacao.md`: especificação do mod (fonte da verdade do design).
- `docs/progresso.md`: o que está pronto, o que falta e o próximo passo.
- `docs/benchmark.md`: como rodar o benchmark e os resultados.
- `docs/pacote-de-design.md`: sprites e modelo do roteador.
- `docs/curseforge/`: descrição, capa, banner e imagens da página do CurseForge.
- `scripts/`: `setup.sh`/`setup.ps1`, `e2e.sh`, `bench.sh`, `textures/` (gera as texturas), `guide/` (gera as páginas do guia) e `curseforge/` (gera as imagens do CurseForge).

## Licença

All Rights Reserved: o código e os recursos estão aqui para consulta, sem permissão para copiar, modificar ou redistribuir (ver [`LICENSE`](LICENSE)). Para sugestões e bugs, abra uma issue.
