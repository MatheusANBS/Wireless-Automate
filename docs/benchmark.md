# Benchmark

Mede o custo do motor nos cenários do "Plano de benchmark" da [especificação](especificacao.md), num servidor dedicado de desenvolvimento, sem jogador, com baús vanilla e com o Sophisticated Storage. Os números abaixo são da rodada de 7 de outubro de 2026 (relatório bruto: `run/bench/reports/20261007-100626.md`, que não vai para o git).

## Resumo

- **Rede ociosa e destinos cheios estão resolvidos.** 500 nós sem nada para mover custam 0,019 ms/tick, e 250 origens cheias contra 250 destinos cheios custam 0,022 ms/tick, depois que tudo dorme. O MSPT do servidor não muda (0,28 ms antes e depois).
- **Muitos nós (500) fica no orçamento, com ressalvas.** Com 250 origens e 250 destinos movendo itens, o mod usa em média 0,55 ms/tick (baú vanilla) e 0,64 ms/tick (Sophisticated Storage), contra o teto de 0,5 ms, e o MSPT sobe de 0,3 para 0,9–1,0 ms. Isso acontece em 100% dos ticks: o resto continua no tick seguinte, e todas as 250 origens moveram itens em cada repetição. O excesso médio, de 10% a 30%, vem da última visita do tick, que não é interrompida. O p99 fica entre 1,1 e 2,3 ms.
- **Gargalo 1, a remontagem das rotas fica fora do orçamento.** Com 500 nós, cada mudança na rede (configurar uma face, um vizinho que troca de bloco, redstone, um chunk que carrega) custa de 7 a 8,5 ms num tick só, ou 15 vezes o orçamento. No cenário em que um nó muda por segundo, o p99 do mod vai a 8 ms e o máximo a 20–27 ms. A correção proposta está no patch 02.
- **Gargalo 2, a inserção em inventário grande.** Uma entrega custa cerca de 6 µs num barril vanilla, 17 µs num barril do Sophisticated Storage e cerca de 53 µs num barril de netherita do Sophisticated Storage (132 slots). O tempo vai quase todo para o `insertItemStacked`, que varre o destino duas vezes e é chamado duas vezes por entrega. A correção proposta está no patch 03.
- **Gargalo 3, o tier Ultimate tem teto de 40.960 itens/s por face.** O teto vem de `MAX_ATTEMPTS_PER_VISIT` (32 pilhas por visita, uma visita por tick), e não do orçamento: a vazão bruta usa só 0,2 ms (vanilla) dos 0,5 ms.
- **Justiça.** Quando a volta inteira cabe no orçamento, cada tick começa da mesma origem. As primeiras origens da lista ficam então com todo o espaço que abre nos destinos: no cenário "bigfull" vanilla, só 5 das 10 origens moveram alguma coisa. A correção proposta está no patch 04.
- **Filtros grandes não pesam.** Com filtros de milhares de entradas (cenário "types"), o custo por entrega ficou igual ao do mesmo inventário sem filtro ("big"): cerca de 53 µs no Sophisticated Storage, dominado pela inserção.

Os patches estão em `bench-patches/` (ver "Patches propostos"). Ainda não foram aplicados nem medidos no jogo, porque o motor está sendo mudado em paralelo ("rede por aba", slots de cartão).

## Como rodar

```bash
./scripts/bench.sh                                # todos os cenários, 3 repetições (cerca de 25 min)
./scripts/bench.sh many:500:3:soph,idle:500:3     # tarefas escolhidas: cenário[:n[:reps[:vanilla|soph]]]
WA_BENCH_MEASURE=400 ./scripts/bench.sh rebuild   # mais ticks de medição
WA_BENCH_JFR=1 WA_BENCH_SPRINT=1 ./scripts/bench.sh many:500:1:soph   # perfil do Java Flight Recorder
```

O script prepara `run/bench` (eula, `server.properties` com um mundo plano novo, porta 25599, sem mobs e sem autosave) e sobe `./gradlew runBenchServer` com a variável `WA_BENCH`. O mod mede o servidor vazio, roda as tarefas uma depois da outra, grava o relatório em `run/bench/reports/<data>.md` (com o log do servidor ao lado) e para o servidor.

- **Run `benchServer`** (`build.gradle`, bloco "Benchmark"): copia o Sophisticated Storage, o Sophisticated Core e o Spark do Maven do Modrinth para `run/bench/mods`. Nem os GameTests nem o `runClient` carregam esses mods. O run usa heap de 2 GB e liga `-Dwirelessautomate.bench=true` (as máquinas de teste do cenário misto).
- **No jogo:** `/wa bench run <cenário> [n] [reps] [vanilla|soph]`, `/wa bench status` e `/wa bench stop`, com permissão 4. O comando monta a cena em x = z = 20000, y = 200, sobrescreve o que houver ali e desmonta tudo no fim. É para mundo de teste. O relatório sai no chat, no log e em `wirelessautomate-bench/` na pasta do servidor.
- **Variáveis:** `WA_BENCH_BASELINE` (100), `WA_BENCH_WARMUP` (padrão do cenário) e `WA_BENCH_MEASURE` (200) dão o tamanho das fases em ticks. `WA_BENCH_SPRINT=1` roda com `/tick sprint`, só para perfis. `WA_BENCH_JFR=1` grava `run/bench/reports/<data>.jfr`. Tudo fica local; nada é enviado para fora (o Spark só imprime o `spark tps` no log).

## Método

Código em `src/main/java/.../bench/`. Cada nó é uma máquina com um roteador em cima, configurado pela face de cima da máquina. Origens e destinos se alternam na grade, todos na mesma rede, com tier Básico (512 itens/s por face), exceto na vazão bruta, que usa o Ultimate. Cada repetição segue estas fases:

1. **Montagem:** máquinas cheias ou vazias conforme o cenário, roteadores sem rede e chunks forçados. A fase espera todos os roteadores se registrarem, mais 20 ticks.
2. **Linha de base (100 ticks):** o MSPT com a cena montada, mas sem rede, que é o "antes" pedido pela especificação.
3. **Rede:** todos os roteadores entram numa rede nova. O tick seguinte mede a primeira montagem das rotas.
4. **Aquecimento** (60 ticks, ou 300 nos cenários que esperam tudo dormir) e **medição** (200 ticks = 10 s), seguidos da desmontagem.

O que se mede em cada tick:

- **MSPT:** do início do `ServerTickEvent.Pre` (prioridade mais alta) ao fim do `Post` (mais baixa). O laço do mod roda no `Post`, depois que o vanilla fecha a conta dele, então o MSPT do vanilla (`/neoforge tps`) **não inclui o mod**; este inclui. O `spark tps` deu medianas compatíveis (0,8 ms no "many" vanilla, contra 0,86 daqui).
- **Mod:** `TickBudget.lastUsedNanos()`, que cobre o tick inteiro do `NetworkManager`, montagem e transferência.
- **Vazão:** soma de `RouterBlockEntity.moved(tipo)` das origens, e entregas por segundo do profiler da rede.

Para a rede continuar ocupada, a cada 20 ticks o benchmark reenche as origens e esvazia os destinos (a cada tick na vazão bruta). Isso roda depois da medição do tick e não entra nos números. As notificações de vizinho que isso dispara acordam as portas, como num jogo real.

O código do motor começa interpretado. Por isso o modo automático roda antes uma tarefa de aquecimento do JIT ("many" com 200 nós e "mixed" com 60), que é descartada. Mesmo assim, a primeira montagem de cada tarefa varia muito (de 7 a 71 ms no "many" vanilla), por causa do JIT e do GC.

**Contadores do motor (opcionais):** visitas por tick, µs por visita, ticks com o orçamento esgotado e tempo por remontagem vêm de quatro contadores no `NetworkManager` (`visitCount`, `exhaustedTicks`, `rebuildCount`, `rebuildNanos`; patch 01). O benchmark os lê por reflexão (`bench/EngineCounters.java`). Sem eles, essas colunas saem "—", o esgotamento é estimado pelos ticks em que o mod gastou o teto, e o cenário "rebuild" mostra o tempo do tick logo depois da mudança. A rodada abaixo foi feita com o patch 01 aplicado.

## Máquina

Contêiner Linux x86-64 com 4 CPUs e 15 GB, **dividido com outros agentes que compilavam e rodavam testes ao mesmo tempo**. Java 21.0.12, heap de 2 GB, NeoForge 21.1.256, Sophisticated Storage 1.6.2.2159, Sophisticated Core 1.5.7.2381 e Spark 1.10.124. Os tempos são de parede, então os máximos e parte da variação vêm da disputa de CPU. Compare as médias e os p99, e repita numa máquina parada antes de tirar conclusões finas.

## Cenários

| Id | Montagem | O que mede | Meta |
| --- | --- | --- | --- |
| `many` | n nós (padrão 500): metade origens com barris cheios de pedregulho, metade destinos vazios, todos ativos | Escala do gerenciador ("Muitos nós") | Abaixo do orçamento |
| `idle` | n nós, origens e destinos vazios | Custo de existir ("Rede ociosa") | Perto de 0 |
| `full` | n nós, origens cheias e destinos cheios de pedra (padrão 100, isto é, 50 origens, como na especificação) | Custo de tentar e falhar ("Destino cheio") | Perto de 0 depois de dormir |
| `raw` | 1 par de inventários grandes, tier Ultimate | Itens/s máximos ("Vazão bruta") | O máximo dentro de 0,5 ms |
| `big` | Pares de inventários grandes: um item diferente por slot na origem, destino vazio | Custo por visita em inventário grande | Abaixo do orçamento |
| `bigfull` | Como `big`, mas o destino está cheio de outros itens, exceto nos 4 últimos slots | Pior caso da inserção (varrer tudo para achar espaço) | Abaixo do orçamento |
| `types` | Como `big`, com lista negra de centenas de itens na origem e lista branca de mais de mil entradas (itens e tags) no destino | Custo de filtro e varredura ("Muitos tipos") | Abaixo do orçamento |
| `mixed` | Um terço de pares de itens (barris), um terço de fluidos e um terço de energia (máquinas de teste: fonte infinita e ralo, `bench/BenchCapabilities.java`) | Comportamento realista ("Misto") | Abaixo do orçamento |
| `rebuild` | Como `many`, e a cada segundo um nó muda de prioridade | Custo de remontar as rotas | Abaixo do orçamento |

Os inventários grandes são o baú duplo (54 slots) no vanilla e o barril de netherita do Sophisticated Storage (132 slots). Os pequenos são o barril vanilla e o barril de madeira do Sophisticated Storage, ambos com 27 slots.

## Resultados

Média ± desvio padrão entre 3 repetições. O "mod" é o tempo do `NetworkManager` por tick, com teto de 0,5 ms. "Unidades/s" são itens, mB e FE somados.

| Cenário | Armaz. | n | MSPT antes (ms) | MSPT com rede (ms) | Mod média (ms/tick) | Mod p99 | Mod máx | Ticks com orçamento esgotado | µs/visita | Unidades/s | Origens que moveram | 1ª montagem (ms) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| many | vanilla | 500 | 0,336 ± 0,043 | 0,897 ± 0,036 | 0,553 ± 0,011 | 1,14 ± 0,31 | 2,5 ± 1,2 | 100% | 45,6 ± 2,0 | 123.484 ± 3.397 | 100% | 31 ± 35 |
| many | soph | 500 | 0,303 ± 0,023 | 0,980 ± 0,064 | 0,641 ± 0,060 | 2,28 ± 1,38 | 10,5 ± 7,9 | 100% | 133,1 ± 4,9 | 49.271 ± 3.630 | 100% | 7,6 ± 1,3 |
| idle | vanilla | 500 | 0,287 ± 0,037 | 0,281 ± 0,005 | 0,019 ± 0,001 | 0,21 ± 0,01 | 0,36 ± 0,11 | 0% | 7,5 ± 0,6 | 0 | — | 5,9 ± 0,3 |
| idle | soph | 500 | 0,310 ± 0,105 | 0,282 ± 0,031 | 0,021 ± 0,005 | 0,26 ± 0,02 | 0,40 ± 0,10 | 0% | 8,4 ± 2,1 | 0 | — | 9,6 ± 5,7 |
| full | vanilla | 100 | 0,229 ± 0,013 | 0,254 ± 0,006 | 0,006 ± 0,001 | 0,08 ± 0,03 | 0,14 ± 0,04 | 0% | 12,7 ± 1,1 | 0 | — | 0,6 ± 0,1 |
| full | vanilla | 500 | 0,253 ± 0,014 | 0,279 ± 0,005 | 0,022 ± 0,005 | 0,36 ± 0,13 | 0,78 ± 0,61 | 0,2% | 8,8 ± 2,0 | 0 | — | 9,1 ± 3,1 |
| raw | vanilla | 2 | 0,244 ± 0,030 | 0,453 ± 0,004 | 0,196 ± 0,011 | 0,49 ± 0,11 | 2,4 ± 1,5 | 0% | 195,8 ± 11,3 | 40.960 ± 0 | 100% | 1,3 ± 1,7 |
| raw | soph | 2 | 0,220 ± 0,025 | 0,786 ± 0,066 | 0,515 ± 0,026 | 1,38 ± 0,55 | 2,3 ± 0,9 | 0% | 514,8 ± 26,4 | 40.960 ± 0 | 100% | 0,7 ± 0,1 |
| big | vanilla | 20 | 0,243 ± 0,012 | 0,401 ± 0,039 | 0,101 ± 0,004 | 0,23 ± 0,08 | 0,39 ± 0,11 | 0,2% | 10,1 ± 0,4 | 5.120 ± 0 | 100% | 2,9 ± 4,1 |
| big | soph | 20 | 0,246 ± 0,019 | 0,795 ± 0,034 | 0,547 ± 0,030 | 1,13 ± 0,48 | 4,7 ± 1,9 | 64 ± 26% | 69,7 ± 16,1 | 5.116 ± 10 | 100% | 0,8 ± 0,1 |
| bigfull | vanilla | 20 | 0,265 ± 0,041 | 0,274 ± 0,030 | 0,027 ± 0,005 | 0,24 ± 0,04 | 0,30 ± 0,06 | 0% | 9,9 ± 1,9 | 2.560 ± 0 | **50%** | 0,9 ± 0,7 |
| bigfull | soph | 20 | 0,240 ± 0,007 | 0,379 ± 0,029 | 0,119 ± 0,004 | 0,75 ± 0,01 | 0,83 ± 0,03 | 12,5 ± 0,5% | 43,3 ± 1,5 | 2.532 ± 3 | 100% | 1,6 ± 1,5 |
| types | soph | 20 | 0,235 ± 0,017 | 0,799 ± 0,029 | 0,549 ± 0,030 | 0,80 ± 0,21 | 7,6 ± 10,1 | 68 ± 13% | 66,4 ± 1,7 | 5.123 ± 4 | 100% | 9,0 ± 13,9 |
| mixed | vanilla | 498 | 0,265 ± 0,042 | 0,856 ± 0,100 | 0,569 ± 0,053 | 1,56 ± 0,26 | 7,6 ± 7,6 | 100% | 6,5 ± 0,8 | 29.252.257 ± 174.068 | 100% | 7,9 ± 4,3 |
| rebuild | vanilla | 500 | 0,242 ± 0,027 | 1,187 ± 0,057 | 0,919 ± 0,038 | 8,09 ± 1,11 | 19,8 ± 8,9 | 100% | 83,7 ± 3,8 | 112.400 ± 953 | 100% | 7,3 ± 1,6 |

O servidor sem cena teve MSPT de 0,85 ms. É mais alto que a linha de base dos cenários porque, sem chunk forçado e sem jogador, o vanilla para de rodar entidades depois de 300 ticks.

### Leitura por cenário

- **Muitos nós.** No vanilla, o mod moveu 123 mil itens/s, ou 96% dos 128 mil que o tier Básico permite para 250 origens, e todas as origens moveram. O trabalho que não cabe continua no tick seguinte: o orçamento esgota em todos os ticks, cerca de 12 visitas por tick, e o cursor dá a volta. No Sophisticated Storage, cada entrega custa três vezes mais (cerca de 17 µs contra 6). A vazão cai para 49 mil itens/s (38% do teto do tier), limitada pelo orçamento, como esperado. A média passa um pouco do teto (0,55 e 0,64 ms), porque o laço confere o relógio antes de cada visita e não durante: a última visita do tick estoura. Uma visita no Sophisticated Storage leva cerca de 130 µs.
- **Rede ociosa e destino cheio.** Perto de 0, como pede a especificação. As cerca de 2,5 visitas por tick são as checagens de reserva do backoff (500 portas com teto de 100 ticks), a uns 8 µs cada. Parte desse custo é o `pass()`, que copia a lista de 250 destinos em toda visita. O p99 de 0,2–0,36 ms vem dos ticks em que muitas origens acordam juntas.
- **Vazão bruta.** Exatamente 2.048 itens por tick, isto é, 32 tentativas × 64, nos dois armazenamentos. No vanilla o mod usa só 0,2 ms; o limite é o `MAX_ATTEMPTS_PER_VISIT`, e não o orçamento. No Sophisticated Storage, a visita única de 32 entregas leva cerca de 0,5 ms, o orçamento inteiro.
- **Inventário grande.** O baú duplo custa cerca de 8 µs por entrega. O barril de netherita do Sophisticated Storage custa cerca de 53 µs por entrega com o destino vazio ("big") e cerca de 60 µs com o destino quase cheio ("bigfull"). Cada entrega varre os 132 slots do destino quatro vezes, em duas chamadas de `insertItemStacked` com duas passadas cada. Com só 10 pares, o "big" do Sophisticated Storage já encosta no orçamento.
- **Muitos tipos.** Igual ao "big" do Sophisticated Storage (66 µs por visita, 5.123 itens/s), então o filtro compilado com cache não aparece no custo.
- **Misto.** 249 origens de três tipos (83 de itens, 83 de fluido e 83 de energia) a 6,5 µs por visita, com 100% das origens ativas. As de itens moveram 42,5 mil itens/s, o teto do tier (83 × 512). Fluido e energia também ficaram nos tetos do tier: 83 × 32.000 mB/s e 83 × 16.000 FE/t.
- **Remontagem.** Cada remontagem custa 7–8,5 ms com 500 nós (250 origens × 250 destinos), fora do orçamento. Acontece a cada mudança de configuração, de redstone ou de capability de qualquer nó da rede.

## Gargalos e patches propostos

Os patches estão em `/tmp/claude-0/.../scratchpad/bench-patches/` (cópia entregue ao orquestrador), feitos sobre o `HEAD` `48c7232`. Não foram aplicados neste branch: o motor (`ItemTransfer`, `NetworkRoutes`, `NetworkManager`) está sendo mudado em paralelo. Eles também ainda não foram compilados nem medidos; o próximo passo é aplicá-los sobre o motor novo e rodar `./scripts/bench.sh many:500:3,many:500:3:soph,rebuild:500:3,big:20:3:soph,bigfull:20:3`.

1. **`01-contadores-networkmanager.patch`**: quatro contadores cumulativos no `NetworkManager` (`visitCount`, `exhaustedTicks`, `rebuildCount` e `rebuildNanos`, mais o cronômetro em volta de `rebuild()`). O custo é desprezível, e eles dão ao benchmark as colunas por visita e por remontagem. Foram usados nesta rodada.
2. **`02-ordem-compartilhada.patch`** (gargalo 1, mais o custo das visitas ociosas). Diagnóstico: `NetworkRoutes.rebuild` cria um `RoundRobinOrder` por origem, e cada construtor monta dois `HashMap` com a lista inteira de destinos e a ordena. Isso dá O(origens × destinos) em alocação e hashing a cada remontagem (cerca de 8 ms com 500 nós, 1,2 ms com 50). Correção: separar a parte imutável (`RoundRobinOrder.Layout`: destinos ordenados, grupos e índice), dividida entre as origens com a mesma lista de destinos (o caso comum: todas alcançam todas). Cada origem fica só com os cursores. O `pass()` deixa de copiar a lista toda (O(destinos) por visita) e passa a ler pela `Layout` com os cursores congelados no início da passada, com a mesma semântica. Inclui dois testes JUnit novos. Efeito esperado: a montagem cai para O(origens + destinos) em mapas e ordenação. A parte O(origens × destinos) que sobra é só alcance e `feeders`. As visitas ociosas também ficam mais baratas. Próximo passo, se ainda não bastar: remontar só a rede e o tipo que mudaram e pôr a remontagem dentro do orçamento.
3. **`03-insercao-planejada.patch`** (gargalo 2). Diagnóstico: no perfil do JFR (`WA_BENCH_JFR=1`), o `ItemHandlerHelper.insertItemStacked` é cerca de 55% do tempo do `ItemTransfer`, e o `RoundRobinOrder.pass` cerca de 13%. Cada entrega chama o `insertItemStacked` na simulação e de novo na inserção real, e cada chamada varre o destino duas vezes (pilhas iguais, depois slots vazios). Correção: `ItemTransfer.InsertPlan`, que faz uma varredura só na simulação (anota os slots vazios enquanto procura pilhas iguais) e lembra os slots que aceitaram. A inserção real vai direto neles, e cai no `insertItemStacked` só se o destino mudou no meio. O resultado é o mesmo do `insertItemStacked`. Efeito esperado: de 4 varreduras do destino por entrega para 1, o que importa mais nos 132 slots do Sophisticated Storage. Depois disso vem a técnica 7 da especificação (índice de slots com espaço por destino).
4. **`04-cursor-gira.patch`** (justiça). Quando a volta inteira cabe no orçamento, o próximo tick começa uma origem adiante, em vez de sempre na mesma.

Sem patch, só a proposta:

- **Estouro do orçamento pela última visita:** levar o prazo do `TickBudget` para dentro do `ItemTransfer` e conferir entre as tentativas de slot, parando a visita, com o cursor de slot já salvo. Assim, uma visita de 130–500 µs no Sophisticated Storage não estoura o teto.
- **Teto do Ultimate:** com taxa ilimitada, deixar a mesma origem ser visitada de novo no mesmo tick enquanto houver orçamento, ou escalar `MAX_ATTEMPTS_PER_VISIT`, para a vazão bruta ser limitada pelo orçamento, como diz a especificação.

## Limitações e próximos passos

- A máquina era compartilhada. Repita os números numa máquina parada e compare com outros mods de transporte do ATM10, como pede a especificação ("Comparação"), o que não foi feito.
- O MSPT medido aqui inclui o mod, mas o MSPT que o `TickBudget.adapt` lê (`getAverageTickTimeNanos`) não inclui, porque o `Post` roda depois da conta do vanilla. O orçamento adaptativo, portanto, não enxerga o próprio custo do mod. É aceitável com 0,5 ms, mas vale saber.
- Não há ainda o GameTest de regressão de performance da especificação. O caminho natural é um cenário pequeno (por exemplo, `rebuild` com 100 nós) que falhe se a remontagem passar de um limite folgado.
- Energia e fluido do cenário misto usam máquinas de teste sem custo próprio: medem só o mod. Caldeirões não servem, porque trocam de bloco a cada balde e, com isso, remontam a rede inteira (o `AbstractCauldronBlock` invalida a capability).
