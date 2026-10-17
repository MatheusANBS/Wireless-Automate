# Medir antes e depois

Toda mudança no motor, nos menus ou no render tem número. Um Gradle, jogo ou bench por vez: cada um usa ~3 GB, e dois juntos já derrubaram o cliente e distorcem a medida.

## O bench (servidor dedicado, sem jogador)

```bash
./scripts/bench.sh many:500:3:vanilla,raw:1:3:vanilla,mixed:498:3,idle:500:3,tablet:1000:3
```

- **Os cinco do dia a dia** (~30 min): `many` (escala do gerenciador), `raw` (vazão bruta, custo por visita), `mixed` (itens, fluidos e energia), `idle` (custo de existir, tem de ser perto de 0) e `tablet` (o Tablet aberto, coluna "Tablet").
- **Por assunto**, quando a mudança é deles: `full` e `sparse` (sono e acordar), `rebuild` e `redstone` (remontagem), `big`, `bigfull`, `types`, `stock` e `bigstack` (inventários grandes, filtro, estoque), `resonator-gen`, `-idle` e `-event` (Ressonador). Tabela completa em `docs/benchmark.md`.
- **Antes:** rode no commit de base (o `origin/main` ou o commit anterior à mudança) e anote o arquivo `run/bench/reports/<data>.md`. **Depois:** o mesmo comando no commit novo.
- **Ruído:** cada linha traz média ± desvio de 3 repetições. Diferença menor que o ± não é diferença. Regressão acima do ± no mod ms/tick, no µs por visita ou no p99 não entra sem uma razão escrita e aceita na revisão.
- **Registre** a tabela antes × depois em `docs/benchmark.md`, numa seção datada, com a leitura em duas ou três linhas.

## Achar o ponto quente

- `WA_BENCH_JFR=1 ./scripts/bench.sh <cenário>` grava `run/bench/reports/<data>.jfr`; abra no JDK Mission Control (Method Profiling e Allocations). Procure o que aparece na pilha do `NetworkManager.tick` e as alocações por visita.
- `/wa profile` no jogo: ms/tick por rede, operações, portas acordadas e dormindo, e as linhas do Ressonador e dos Fluxos (média e última amostra).
- O Spark (já na run `benchServer`) para o MSPT do servidor inteiro.

## Cliente

- Não há bench automático de FPS. Para telas: abra a tela com uma base grande (o mundo do e2e ou a vitrine) e compare o tempo de quadro no F3 (ou o `/debug start` do vanilla) antes e depois, com a mesma janela e escala. Para travadas (busca, abrir tela), meça com `System.nanoTime` em volta do trecho num build local e tire antes do commit.
- Regra prática: nenhuma tela pode custar mais de ~1 ms por quadro numa base grande, e nada pode travar mais de ~16 ms (um quadro) ao digitar ou rolar.

## GameTests e o relógio

Um GameTest travado gira o servidor a centenas de ticks por segundo e enche o log (100 MB em 10 min). Todo teste novo leva `timeoutTicks` de no máximo 300. Se uma run passar de ~10 min, olhe `run/gametest/logs/latest.log`, ache o teste que entrou e não saiu, e corrija antes de rodar de novo.
