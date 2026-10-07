# Wireless Automate

Transporte wireless de itens, fluidos, energia e químicos para **NeoForge 1.21.1** (Java 21), feito para o ATM10. A meta é ser o transporte mais rápido do pack e o mais leve em TPS: um gerenciador central, sem tick por bloco, com teto de tempo por tick.

**Estado:** versão 0.1.0 publicada no CurseForge (projeto 1732160). Licença All Rights Reserved.

## Recursos

- **Roteador Wireless:** preso a qualquer face de uma máquina, acessa todas as faces dela.
- **Redes por aba:** cada aba do roteador (Itens, Fluidos, Energia, Químicos) entra na sua própria rede; tudo do mesmo tipo na mesma rede troca entre si.
- **Modos por face e por tipo:** Extrair, Inserir, Armazém ou Nenhum, com prioridade, round-robin no empate e controle por redstone.
- **Filtros sem limite:** item ou fluido exato, tag e mod, lista branca ou negra, limite de estoque, e Cartões de Filtro (dois slots por face e por tipo, copiáveis na bancada).
- **Quatro tiers**, subidos com os Cartões de Upgrade Avançado, Elite e Ultimate (não há cartão Básico, o roteador já nasce Básico), clicando no roteador colocado ou juntando os dois na bancada.
- **Vinculador:** escolhe a rede ativa e põe roteadores nela, um a um ou por área, nas abas marcadas (Itens, Fluidos, Energia e, com o Mekanism, Químicos); em "Nenhuma (desvincular)", tira essas abas da rede.
- **Configurador:** copia a configuração de um roteador e cola em outro, ou em todos os de uma área presos ao mesmo tipo de máquina; todas as abas ou só um tipo (Shift + roda do mouse).
- **Upgrade de chunk loading:** mantém carregado o chunk do roteador e o da máquina, com limite por jogador na config.
- **Tablet de Rede:** lista, mapa, estatísticas, redes e grupos à distância, e abre a tela do roteador de longe.
- **Livro-guia (GuideME):** na aba criativa e entregue a cada jogador no primeiro login.

| Tier | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Alcance |
| --- | --- | --- | --- | --- |
| Básico | 512 | 32.000 | 16.000 | 128 blocos |
| Avançado | 8.192 | 512.000 | 256.000 | 1.024 blocos |
| Elite | 131.072 | 8.000.000 | 4.000.000 | Dimensão inteira |
| Ultimate | Sem limite | Sem limite | Sem limite | Entre dimensões |

A vazão vale por face e por tipo. Os valores ficam na config do servidor (`config/wirelessautomate-server.toml`).

## Integrações opcionais

| Mod | O que traz |
| --- | --- |
| Mekanism | Químicos: aba Químicos no roteador, com filtro por químico ou mod |
| JEI | Arrastar e Shift + clique para os filtros, e a receita de upgrade na bancada |
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
| `./gradlew runClient` | Cliente de dev com JEI, Sophisticated Storage, Observable, Mekanism e GuideME, para testar à mão |
| `./gradlew runData` | Geradores de dados, saída em `src/generated/resources/` |
| `./scripts/e2e.sh` | Teste de ponta a ponta num mundo real (precisa de Xvfb) |
| `./scripts/bench.sh <cenários>` | Benchmark num servidor dedicado com Sophisticated Storage (ver `docs/benchmark.md`) |

O CI (`.github/workflows/build.yml`) roda `build`, `runGameTestServer` e `runGameTestServerChemicals` a cada push e pull request.

## Organização

O mapa do código-fonte fica no [`CLAUDE.md`](CLAUDE.md) ("Mapa do código").

- `docs/especificacao.md`: especificação do mod (fonte da verdade do design).
- `docs/progresso.md`: o que está pronto, o que falta e o próximo passo.
- `docs/benchmark.md`: como rodar o benchmark e os resultados.
- `docs/pacote-de-design.md`: sprites e modelo do roteador.
- `docs/curseforge/`: descrição, capa, banner e imagens da página do CurseForge.
- `scripts/`: `setup.sh`/`setup.ps1`, `e2e.sh`, `bench.sh`, `textures/` (gera as texturas), `guide/` (gera as páginas do guia) e `curseforge/` (gera as imagens do CurseForge).
