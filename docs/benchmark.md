# Benchmark

Mede o custo do motor nos cenários do "Plano de benchmark" da [especificação](especificacao.md), num servidor dedicado de desenvolvimento, sem jogador, com baús vanilla e com o Sophisticated Storage. Os números abaixo são da rodada de 7 de outubro de 2026 (relatório bruto: `run/bench/reports/20261007-100626.md`, que não vai para o git).

## Resumo

Rodada base de 7 de outubro de 2026 (motor `48c7232`, seção "Resultados") e, no mesmo dia, as correções do motor medidas antes e depois (seção "Correções aplicadas: antes e depois").

A rodada completa na máquina local (seção "Máquina local: antes e depois") confirmou as correções: remontagem de 3,3–4,0 ms para 0,29–0,56 ms, todas as origens movendo no "bigfull", 68,7 mil itens/s na vazão bruta vanilla e 66,5 mil no Sophisticated Storage, e picos do mod menores.

- **A remontagem das rotas caiu de 7,4–8,6 ms para 0,5–1,0 ms** com 500 nós, e o pico do mod no cenário "rebuild" de 14 ms para 1,5 ms (p99 de 8,6 para 1,07 ms). O que sobra é código frio: a remontagem roda poucas vezes e o JIT não chega a compilá-la.
- **Justiça entre origens resolvida:** no "bigfull" vanilla, 100% das origens movem (antes, de 50% a 60%).
- **Ultimate e Elite sem o teto de 40.960 itens/s por face:** a vazão bruta vanilla foi para 68.651 itens/s, limitada pelo orçamento. No Sophisticated Storage uma visita de 32 entregas já custa 0,5 ms, então o Ultimate continua em 40.960/s ali.
- **Rede ociosa:** 0,017 ms/tick, com p99 de 0,22 para 0,06 ms (o `pass()` não copia mais a lista de destinos).
- **Muitos nós (500):** no orçamento, como na base (0,53 ms/tick vanilla, 0,57 Sophisticated); o MSPT do servidor vai de 0,3 para 0,9 ms com a rede movendo 127 mil itens/s.
- **A inserção planejada não mudou o custo no Sophisticated Storage** (cerca de 50 µs por entrega no barril de netherita, antes e depois): a varredura do destino era barata, e o caro é o `extractItem`/`insertItem` de verdade do mod de armazenamento.

## Como rodar

```bash
./scripts/bench.sh                                # todos os cenários, 3 repetições (cerca de 30 min)
./scripts/bench.sh many:500:3:soph,idle:500:3     # tarefas escolhidas: cenário[:n[:reps[:vanilla|soph]]]
WA_BENCH_MEASURE=400 ./scripts/bench.sh rebuild   # mais ticks de medição
WA_BENCH_JFR=1 WA_BENCH_SPRINT=1 ./scripts/bench.sh many:500:1:soph   # perfil do Java Flight Recorder
```

O script prepara `run/bench` (eula, `server.properties` com um mundo plano novo, porta 25599, sem mobs e sem autosave) e sobe `./gradlew runBenchServer` com a variável `WA_BENCH`. O mod mede o servidor vazio, roda as tarefas uma depois da outra, grava o relatório em `run/bench/reports/<data>.md` (com o log do servidor ao lado) e para o servidor.

- **Run `benchServer`** (`build.gradle`, bloco "Benchmark"): copia o Sophisticated Storage, o Sophisticated Core e o Spark do Maven do Modrinth para `run/bench/mods`. O `runClient` também carrega o Sophisticated Storage e o Sophisticated Core (para testar à mão); só o Spark é exclusivo do benchmark. Os GameTests não carregam nenhum deles. O run usa heap de 2 GB e liga `-Dwirelessautomate.bench=true` (as máquinas de teste do cenário misto).
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

**Contadores do motor:** visitas por tick, µs por visita, ticks com o orçamento esgotado e tempo por remontagem vêm de quatro contadores cumulativos e públicos do `NetworkManager` (`visitCount()`, `exhaustedTicks()`, `rebuildCount()` e `rebuildNanos()`), lidos direto pelo `bench/BenchRun`. As voltas extras do tick (ver "Correções aplicadas") contam como visitas, mas não como orçamento esgotado.

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
| `sparse` | n nós (padrão 500): só a primeira origem tem pedregulho, as outras 249 estão vazias; destinos vazios | Quantas origens vazias cada entrega acorda (visitas por tick com uma única origem ativa) | Visitas por tick perto das de uma origem só |
| `stock` | Pares de inventários grandes (padrão 20): origem cheia de pedregulho; destino com lista branca de pedregulho com estoque de 1.000 e já com 1.000 dentro | Custo de tentar inserir num destino que já atingiu o estoque | Perto de 0 (nada se move) |
| `bigstack` | 1 par (máquinas de teste): origem de 1 slot com uma pilha de 1.000.000 de pedregulhos (como gaveta ou bin, extrai até 64 por vez) para um ralo, tier Elite | Vazão de uma pilha enorme num slot só | Perto da vazão do Elite (131.072 itens/s) |
| `redstone` | Como `many` (padrão 100 nós), com um bloco de redstone em cima de cada roteador que liga e desliga a cada tick (relógio de 2 ticks); nenhuma face usa redstone | Remontagens por segundo causadas por sinal que ninguém usa | 0 remontagens/s |
| `tablet` | Como `many` (padrão 1.000 nós), com um Tablet aberto por um jogador falso dono da rede, sincronizado a cada tick | Custo do Tablet aberto, fora do laço do mod (coluna "Tablet") | Bem abaixo do orçamento |

Os inventários grandes são o baú duplo (54 slots) no vanilla e o barril de netherita do Sophisticated Storage (132 slots). Os pequenos são o barril vanilla e o barril de madeira do Sophisticated Storage, ambos com 27 slots.

Detalhes dos cenários da auditoria de performance (`sparse`, `stock`, `bigstack`, `redstone` e `tablet`):

- **`sparse`:** a cada 20 ticks o benchmark reenche só a primeira origem e esvazia os destinos. As colunas que importam são "visitas/tick" (do `visitCount()` do `NetworkManager`, que conta cada visita de origem, inclusive as voltas extras) e "origens que moveram" (deve ser 1 de 250).
- **`stock`:** nada é reenchido nem esvaziado: o destino fica sempre no estoque. Mede o custo de cada visita que tenta e falha por estoque, e se a origem chega a dormir ("dormindo" na linha da repetição).
- **`bigstack`:** usa as máquinas de teste de `bench/BenchCapabilities.java` (bloco de ouro: a pilha enorme, sem estado; bloco de ferro: o ralo), então só roda no `benchServer` e com `vanilla`.
- **`redstone`:** o benchmark troca os blocos de redstone no fim do tick, fora do tempo medido (como o reenchimento); a remontagem que isso provoca cai no tick seguinte, dentro do tempo do mod. A coluna "remontagens/s" vem de `rebuildCount()`; com o relógio, chega a 20 por segundo.
- **`tablet`:** o menu é criado direto (`new TabletMenu`) para um `FakePlayer` com o UUID do dono da rede da cena, e o benchmark chama `pollSnapshot()` (o que o `broadcastChanges()` do menu faz no servidor, menos o envio) no começo de cada tick, dentro do MSPT medido, como o `ServerPlayer` faz com a tela aberta. O tempo dessa chamada sai na coluna "Tablet (ms/tick)" e na linha da repetição (média e máximo), à parte do "mod", porque roda fora do orçamento. O pacote não é enviado (o jogador falso não tem canal de rede), então o custo medido é o de montar e comparar o snapshot, sem a serialização.

As colunas "visitas/tick", "remontagens/s" e "Tablet (ms/tick)" valem para todos os cenários; "Tablet" mostra "—" fora do `tablet`.

## Resultados (rodada base, motor 48c7232)

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

## Correções aplicadas: antes e depois

Os quatro gargalos da rodada base viraram correções no motor (`network/`), uma por commit, mais duas que a medição pediu:

1. **Contadores no `NetworkManager`** (`visitCount`, `exhaustedTicks`, `rebuildCount`, `rebuildNanos`), públicos; o benchmark lê direto, sem reflexão.
2. **Ordem compartilhada e remontagem O(origens + destinos).** `RoundRobinOrder.Layout` guarda os destinos agrupados por prioridade e é dividida entre as origens com a mesma lista; cada origem fica só com os cursores, e o `pass()` lê pela `Layout` sem copiar a lista. A origem que alcança todos os destinos do tipo (só extrai, nenhum destino na mesma máquina e face, e a caixa dos destinos inteira no alcance, `ReachBox`) usa a `Layout` de todos sem conferir um a um, e entra numa lista única de alimentadoras (`Port.sharedFeeders`) dividida entre os destinos. As outras (Armazém, alcance curto, outra dimensão) conferem destino a destino como antes, e dividem a `Layout` de quem tiver a mesma lista. A rede por aba e a regra do Armazém (`bothToBoth`) ficam iguais.
3. **Inserção planejada** (`ItemTransfer.InsertPlan`): uma varredura do destino por entrega (pilhas iguais, depois vazios, como o `insertItemStacked`), lembrando os slots que aceitaram; a inserção real vai neles e só o que sobrar cai no `insertItemStacked`, e o que nem ele aceitar volta para a origem como antes. A contagem do estoque do destino sai da mesma varredura. Filtros (`FilterSet`) e estoque (`StockLimit`) seguem as mesmas regras.
4. **Justiça entre origens** (`SourceCursor`): quando a volta inteira cabe no orçamento, a seguinte começa depois da última origem que moveu algo, como numa fila. A versão do patch 04 (girar uma origem por tick) não bastava: no "bigfull" o espaço abre a cada 20 ticks, múltiplo das 10 origens, e as mesmas 5 continuavam na frente.
5. **Voltas extras para o Ultimate e o Elite.** A visita de itens para em 32 tentativas, e cada origem tinha uma visita por tick, então os dois tiers ficavam presos em 2.048 itens/tick (40.960/s) por face; o Elite (131.072/s) também. Agora a visita avisa se parou num teto com saldo no balde, e, depois da volta inteira, o laço visita de novo só essas origens, na mesma ordem, enquanto a visita seguinte (estimada pela anterior da origem) couber no que resta (`TickBudget.fits`). Ninguém ganha a segunda visita antes de todas terem a primeira, e o teto por visita continua o mesmo.
6. **Leitura incremental das faces.** `NodePorts` guarda as portas ativas de cada tipo já preenchidas (rede, modo, prioridade, filtro, tier, máquina); o `nodeChanged` descarta a leitura do nó e a troca de rede descarta a do tipo. A montagem relê só esses nós. A config dos tiers é lida uma vez por tipo, e o `facing` uma vez por nó.

Testes novos: JUnit para `Layout` dividida e passada estável, `ReachBox`, `SourceCursor` e `TickBudget.fits`; GameTests para a ordem do empilhamento com conservação de itens e para a remontagem que relê só os nós que mudaram.

### Números

Mesma máquina, sem outra carga nas duas rodadas, 3 repetições (média ± desvio padrão está nos relatórios brutos; aqui, as médias). "Antes" é o `e4b4056` com só os contadores (relatório `20261007-103321`); "depois" é o motor com as seis correções, no `HEAD` do branch (relatório `20261007-115622`; a rodada foi interrompida antes do fim, então o "raw" do Sophisticated Storage tem 2 repetições e o "mixed" vem da rodada `20261007-104849`, com as correções 1 a 5).

| Cenário | Armaz. | MSPT sem rede (ms) | MSPT com rede antes → depois (ms) | Mod média (ms/tick) | Mod p99 (ms) | Mod máx (ms) | µs/entrega | Unidades/s | Origens que moveram |
|---|---|---|---|---|---|---|---|---|---|
| many 500 | vanilla | 0,28 / 0,34 | 0,83 → 0,89 | 0,537 → 0,530 | 0,83 → 0,71 | 2,5 → 1,3 | 5,5 → 5,2 | 125.195 → 127.113 | 100% → 100% |
| many 500 | soph | 0,25 / 0,34 | 0,88 → 0,93 | 0,589 → 0,567 | 1,03 → 1,18 | 7,5 → 2,9 | 18,5 → 14,5 | 41.523 → 50.534 | 100% → 100% |
| idle 500 | vanilla | 0,24 / 0,30 | 0,26 → 0,30 | 0,017 → 0,017 | 0,22 → 0,06 | 0,30 → 0,15 | — | 0 | — |
| rebuild 500 | vanilla | 0,20 / 0,28 | 1,15 → 0,85 | 0,920 → 0,550 | 8,63 → 1,07 | 14,2 → 1,5 | 10,6 → 5,9 | 111.073 → 116.588 | 100% → 100% |
| big 20 | vanilla | 0,21 / 0,28 | 0,27 → 0,39 | 0,092 → 0,112 | 0,18 → 0,39 | 0,34 → 1,23 | 7,6 → 9,3 | 5.120 → 5.120 | 100% → 100% |
| big 20 | soph | 0,21 / 0,24 | 0,75 → 0,81 | 0,531 → 0,533 | 1,22 → 0,91 | 2,8 → 1,6 | 52,3 → 51,5 | 5.125 → 5.114 | 100% → 100% |
| bigfull 20 | vanilla | 0,19 / 0,24 | 0,24 → 0,26 | 0,033 → 0,026 | 0,26 → 0,20 | 0,90 → 0,31 | 16,3 → 13,2 | 2.560 → 2.560 | **60% → 100%** |
| bigfull 20 | soph | 0,20 / 0,24 | 0,33 → 0,38 | 0,128 → 0,134 | 0,74 → 0,81 | 1,05 → 2,2 | 64,1 → 65,8 | 2.532 → 2.507 | 100% → 100% |
| raw 2 (Ultimate) | vanilla | 0,19 / 0,24 | 0,36 → 0,47 | 0,140 → 0,206 | 0,40 → 0,54 | 0,83 → 2,9 | 4,4 → 3,8 | **40.960 → 68.651** | 100% → 100% |
| raw 2 (Ultimate) | soph | 0,20 / 0,27 | 0,71 → 0,77 | 0,487 → 0,489 | 0,99 → 0,98 | 1,9 → 2,6 | 15,2 → 15,3 | 40.960 → 40.960 | 100% → 100% |
| mixed 498 | vanilla | 0,23 / 0,26 | 0,77 → 0,78 | 0,523 → 0,520 | 0,79 → 0,91 | 1,5 → 1,8 | 0,23 → 0,18 | 29,3 M → 29,2 M | 100% → 100% |

Remontagem com 500 nós (250 origens × 250 destinos, uma por segundo no cenário "rebuild"): **7,4–8,6 ms → 0,54–1,0 ms** por remontagem (média por repetição), e o tick seguinte à mudança de 12–17 ms para 1,0–1,5 ms no máximo. Primeira montagem do "many" vanilla: 16,7 ± 9,3 → 2,4 ± 0,6 ms.

Variação entre repetições: as médias do mod variam 1% a 4% (desvio padrão sobre a média); os p99 e máximos variam muito mais (até 50% e 100%), porque um único GC ou um tick de disputa de CPU decide o máximo. A linha de base sem rede (MSPT sem rede) variou de 0,19–0,28 ms numa rodada para 0,24–0,34 ms na outra só pelo estado da máquina; compare diferenças pequenas com cuidado.

### Leitura

- **Orçamento.** Nos cenários que sempre têm trabalho ("many", "rebuild", "mixed", "big" Sophisticated), o mod fica em 0,52–0,57 ms por tick para um teto de 0,5 ms: o laço confere o relógio antes de cada visita, e a última passa um pouco. Nos outros, fica bem abaixo (0,017 ms ocioso, 0,03 ms no "bigfull"). O MSPT do servidor sobe de cerca de 0,3 ms para 0,8–0,9 ms com 500 nós movendo, e para 0,3 ms com a rede ociosa.
- **Remontagem.** O custo que sobra (0,5–1,0 ms) é de código frio: a remontagem roda só quando algo muda, e o JIT não chega a compilá-la (numa rodada instrumentada, a leitura das faces caiu de cerca de 2 ms para 0,2 ms com a leitura incremental, e o resto se divide entre os laços de origens e destinos, cada um a cerca de 1 µs por item, típico do interpretador). Dividir a montagem de uma rede entre ticks exigiria montar as rotas novas à parte e trocar no fim, porque as portas são mudadas no lugar; não compensou agora. Fica como próximo passo se aparecer rede com milhares de nós.
- **Inserção.** Numa rodada instrumentada no barril de netherita do Sophisticated Storage, por entrega: a simulação (a varredura do plano) custou cerca de 6–20 µs, a extração real 30–75 µs e a inserção real 10–50 µs. O caro é o próprio Sophisticated Storage ao mudar o inventário, então o plano não mexeu na média dele; no vanilla, a entrega ficou igual ou um pouco mais barata. A correção fica pela leitura de uma varredura em vez de quatro, que não piora nada; o relatório base atribuía 55% do tempo ao `insertItemStacked` contando o tempo das inserções dentro dele.
- **Vazão bruta.** O Ultimate vanilla passou a ser limitado pelo orçamento (cerca de 1,7 visitas de 32 pilhas por tick, 68,7 mil itens/s). No Sophisticated Storage, uma visita leva cerca de 0,49 ms, então a volta extra não cabe e a vazão continua em 40.960/s, sem passar do teto.
- **Justiça.** No "bigfull" vanilla as 10 origens passaram a mover; nos outros cenários já moviam todas.

## Máquina local: antes e depois (7 de outubro de 2026)

Medida com o orçamento de 0,5 ms, o padrão da época; depois o padrão passou a 1 ms (`tickBudgetMs`), e os cenários limitados pelo orçamento ganham vazão na mesma proporção, com o custo do mod subindo junto.

Rodada completa numa máquina local parada, um servidor de cada vez (uma primeira tentativa foi descartada porque outro servidor rodou junto). Relatórios brutos, fora do git: `run/bench/reports/20261007-100308.md` (depois), `20261007-101823.md` (remontagem aquecida) e `20261007-102759-antes.md` (copiado do checkout do antes).

- **Máquina:** Intel Core i7-14650HX (16 núcleos, 24 threads), 32 GB de RAM, Windows 11 Pro, Java Temurin 21.0.12, heap de 2 GB. O servidor vazio fica em 0,57 ms de MSPT (p99 1,15 ms).
- **Antes:** `712a246` (só os contadores, sobre `e4b4056`). **Depois:** `f0f3914` (`main`, com as seis correções).
- **Comandos:** os da lista abaixo, pelo Git Bash, com o `JAVA_HOME` apontando para o JDK 21 (o padrão da máquina é o 17). O `scripts/bench.sh` rodou sem mudanças; cada rodada completa leva cerca de 16 minutos.

```bash
./scripts/bench.sh "many:500:3,many:500:3:soph,idle:500:3,full:500:3,rebuild:500:3,big:20:3,big:20:3:soph,bigfull:20:3,bigfull:20:3:soph,raw:2:3,raw:2:3:soph,types:20:3:soph,mixed:498:3"
WA_BENCH_MEASURE=2000 ./scripts/bench.sh "rebuild:500:3"   # remontagem aquecida, só no depois
```

Médias de 3 repetições. Mod em ms/tick (média, p99 e máximo); orçamento de 0,5 ms.

| Cenário | Mod média antes → depois | Mod p99 antes → depois | Mod máx antes → depois | Unidades/s antes → depois | Origens que moveram |
|---|---|---|---|---|---|
| many, vanilla, 500 | 0,534 → 0,515 | 0,632 → 0,575 | 2,23 → 1,53 | 124.398 → 129.337 | 100% → 100% |
| many, Sophisticated, 500 | 0,571 → 0,533 | 0,886 → 0,716 | 3,56 → 0,88 | 65.007 → 94.276 | 100% → 100% |
| idle, vanilla, 500 | 0,0122 → 0,0098 | 0,175 → 0,044 | 0,28 → 0,11 | 0 → 0 | — |
| full, vanilla, 500 | 0,0114 → 0,0125 | 0,165 → 0,145 | 0,35 → 0,30 | 0 → 0 | — |
| rebuild, vanilla, 500 | 0,681 → 0,508 | 3,898 → 0,586 | 5,47 → 0,69 | 126.091 → 127.755 | 100% → 100% |
| big, vanilla, 20 | 0,063 → 0,067 | 0,157 → 0,162 | 0,17 → 0,21 | 5.120 → 5.120 | 100% → 100% |
| big, Sophisticated, 20 | 0,348 → 0,345 | 0,535 → 0,553 | 1,27 → 0,64 | 5.117 → 5.128 | 100% → 100% |
| bigfull, vanilla, 20 | 0,020 → 0,016 | 0,161 → 0,151 | 0,30 → 0,21 | 2.560 → 2.560 | **50% → 100%** |
| bigfull, Sophisticated, 20 | 0,069 → 0,067 | 0,642 → 0,660 | 0,77 → 0,79 | 2.530 → 2.544 | 100% → 100% |
| raw, vanilla, 2 | 0,100 → 0,139 | 0,279 → 0,276 | 0,31 → 0,39 | **40.960 → 68.745** | 100% → 100% |
| raw, Sophisticated, 2 | 0,324 → 0,431 | 0,958 → 0,736 | 1,69 → 0,86 | **40.960 → 66.492** | 100% → 100% |
| types, Sophisticated, 20 | 0,371 → 0,353 | 0,556 → 0,536 | 0,58 → 0,56 | 5.123 → 5.120 | 100% → 100% |
| mixed, vanilla, 498 | 0,503 → 0,489 | 0,528 → 0,516 | 0,54 → 0,53 | 29,30 mi → 29,25 mi | 100% → 100% |

Remontagens no "rebuild" (10 por repetição): **3,3–4,0 ms cada antes, 0,29–0,56 ms depois**; o tick logo depois da mudança, de 3,7–7,0 ms para 0,51–0,76 ms. Na rodada aquecida (100 remontagens por repetição), 0,14–0,48 ms cada, caindo a cada repetição conforme o JIT compila; o pior tick depois de uma mudança ficou entre 0,57 e 1,83 ms, e o mod máx em 1,96 ms.

### Critérios

- **"rebuild": passou.** Remontagem abaixo de 1 ms (0,29–0,56 ms, contra 3,3–4,0 ms antes nesta máquina e 7–8,5 ms na nuvem). O p99 (0,586 ms) e o máx (0,69 ms) ficaram perto dos do "many" (0,575 e 1,53 ms). Na rodada aquecida o máx sobe para 1,96 ms, porque são dez vezes mais remontagens.
- **"bigfull" vanilla: passou.** Todas as origens movem (antes, 50%).
- **"raw" vanilla: passou.** 68.745 itens/s, acima de 40.960, com o mod em 0,139 ms/tick em média.
- **"idle" e "full": passou.** Mod perto de 0 (0,010 e 0,013 ms/tick). O p99 caiu de 0,175 para 0,044 ms no "idle"; no "full" caiu de 0,165 para 0,145 ms, dentro da variação entre repetições (± 0,08).
- **"many" e "mixed": dentro do esperado.** Mod perto de 0,5 ms e as mesmas vazões no vanilla; o MSPT com rede ficou em 0,63–0,73 ms, longe de passar de 1 ms.

### Leitura

- **Sophisticated Storage mais rápido aqui do que na nuvem.** Com a CPU local mais rápida, uma visita no barril de netherita custa menos, e a volta extra do Ultimate passou a caber no orçamento: a vazão bruta foi de 40.960 para 66.492 itens/s (na nuvem tinha ficado em 40.960). No "many" Sophisticated, a entrega caiu de 11,3 para 7,5 µs e a vazão subiu de 65 mil para 94 mil itens/s.
- **Picos menores.** O mod máx caiu nos cenários com trabalho constante ("many" Sophisticated de 3,56 para 0,88 ms, "rebuild" de 5,47 para 0,69 ms), que é o que o jogador sente como travada.
- **"mixed".** O orçamento esgotou em 69% dos ticks depois, contra 99% antes, com a mesma vazão.

## Limitações e próximos passos

- **Estouro pela última visita** (ainda não feito): levar o prazo do `TickBudget` para dentro do `ItemTransfer` e conferir entre as tentativas de slot, parando a visita com o cursor de slot salvo. Uma visita de 0,1–0,5 ms no Sophisticated Storage ainda passa do teto.
- **Remontagem dividida entre ticks** (ainda não feito): montar as rotas novas à parte e trocar no fim, para redes de milhares de nós; hoje 500 nós custam menos de 1 ms.
- A rodada base dividiu a máquina com outros agentes; a da máquina local (seção acima) rodou numa máquina parada. Falta comparar com outros mods de transporte do ATM10, como pede a especificação ("Comparação").
- O MSPT medido aqui inclui o mod, mas o MSPT que o `TickBudget.adapt` lê (`getAverageTickTimeNanos`) não inclui, porque o `Post` roda depois da conta do vanilla. O orçamento adaptativo, portanto, não enxerga o próprio custo do mod. É aceitável com 0,5–1 ms, mas vale saber.
- Não há ainda o GameTest de regressão de performance da especificação. O caminho natural é um cenário pequeno (por exemplo, `rebuild` com 100 nós) que falhe se a remontagem passar de um limite folgado.
- Energia e fluido do cenário misto usam máquinas de teste sem custo próprio: medem só o mod. Caldeirões não servem, porque trocam de bloco a cada balde e, com isso, remontam a rede inteira (o `AbstractCauldronBlock` invalida a capability).

## Auditoria de performance: antes e depois (8 de outubro de 2026)

Rodada completa (`./scripts/bench.sh`, 3 repetições) no container da nuvem (4 núcleos), com a máquina parada: antes = `fe987ba` (motor da 0.1.1 com os cenários novos), depois = todas as correções de [auditoria-performance.md](auditoria-performance.md). Mesma máquina nas duas, então os números se comparam entre si, não com as seções acima. Em "many", "rebuild" e "tablet" o mod sempre esgota o orçamento de 1 ms (é o teto que segura a vazão), então o que conta lá é a vazão e o custo por entrega.

| Cenário | Armaz. | n | Mod média (ms/tick) | Mod p99 | Orçamento esgotado (%) | µs/visita | Unidades/s | Visitas/tick | Remontagens/s | Tablet (ms/tick) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| many | vanilla | 500 | 1,0569 → 1,0373 | 1,8494 → 1,7383 | 100,0 → 100,0 | 15,11 → 9,54 | 128160 → 128127 | 70,5 → 112,1 | 0,0 → 0,0 | — |
| many | soph | 500 | 1,1256 → 1,1681 | 2,5829 → 2,8934 | 100,0 → 100,0 | 118,03 → 107,07 | 97638 → 112278 | 9,5 → 11,0 | 0,0 → 0,0 | — |
| idle | vanilla | 500 | 0,0200 → 0,0226 | 0,1375 → 0,1317 | 0,0 → 0,0 | 8,01 → 9,05 | 0 → 0 | 2,5 → 2,5 | 0,0 → 0,0 | — |
| idle | soph | 500 | 0,0228 → 0,0214 | 0,1996 → 0,1053 | 0,0 → 0,0 | 9,13 → 8,55 | 0 → 0 | 2,5 → 2,5 | 0,0 → 0,0 | — |
| full | vanilla | 100 | 0,0088 → 0,0065 | 0,0494 → 0,0078 | 0,0 → 0,0 | 17,53 → 12,93 | 0 → 0 | 0,5 → 0,5 | 0,0 → 0,0 | — |
| full | vanilla | 500 | 0,0232 → 0,0279 | 0,2387 → 0,4002 | 0,2 → 1,0 | 9,29 → 11,17 | 0 → 0 | 2,5 → 2,5 | 0,0 → 0,0 | — |
| raw | vanilla | 2 | 0,2304 → 0,2815 | 0,3654 → 0,5960 | 0,0 → 0,0 | 115,31 → 141,32 | 69073 → 68885 | 2,0 → 2,0 | 0,0 → 0,0 | — |
| raw | soph | 2 | 0,8281 → 0,7527 | 2,0253 → 2,7224 | 0,0 → 0,0 | 553,84 → 560,80 | 61167 → 56388 | 1,5 → 1,4 | 0,0 → 0,0 | — |
| big | vanilla | 20 | 0,1096 → 0,1191 | 0,2717 → 0,2535 | 0,0 → 0,2 | 10,96 → 11,93 | 5120 → 5120 | 10,0 → 10,0 | 0,0 → 0,0 | — |
| big | soph | 20 | 0,7269 → 0,7797 | 1,5855 → 1,4713 | 5,3 → 5,8 | 74,51 → 79,55 | 5115 → 5120 | 9,8 → 9,8 | 0,0 → 0,0 | — |
| bigfull | vanilla | 20 | 0,0333 → 0,0375 | 0,2354 → 0,2722 | 0,0 → 0,2 | 12,12 → 14,62 | 2560 → 2560 | 2,8 → 2,6 | 0,0 → 0,0 | — |
| bigfull | soph | 20 | 0,1434 → 0,1319 | 1,2533 → 1,1273 | 5,3 → 5,2 | 41,37 → 29,33 | 2544 → 2534 | 3,4 → 4,5 | 0,0 → 0,0 | — |
| types | soph | 20 | 0,7440 → 0,7213 | 1,2050 → 1,3665 | 4,3 → 3,5 | 75,22 → 73,52 | 5120 → 5120 | 9,9 → 9,8 | 0,0 → 0,0 | — |
| mixed | vanilla | 498 | 0,9874 → 0,9344 | 1,5229 → 1,5883 | 58,2 → 27,5 | 4,53 → 3,90 | 29235845 → 29281987 | 220,3 → 239,8 | 0,0 → 0,0 | — |
| rebuild | vanilla | 500 | 1,0421 → 1,0260 | 2,1831 → 1,3796 | 100,0 → 100,0 | 21,17 → 8,62 | 127442 → 127970 | 49,9 → 119,6 | 1,0 → 1,0 | — |
| sparse | vanilla | 500 | 0,2583 → 0,0719 | 0,7532 → 0,2064 | 0,7 → 0,2 | 1,03 → 15,19 | 512 → 512 | 249,8 → 4,7 | 0,0 → 0,0 | — |
| sparse | soph | 500 | 0,6192 → 0,2289 | 1,1736 → 0,6426 | 1,0 → 0,0 | 2,48 → 48,35 | 512 → 512 | 249,2 → 4,7 | 0,0 → 0,0 | — |
| stock | soph | 20 | 1,5232 → 0,0252 | 3,1574 → 0,3347 | 96,0 → 0,0 | 1570,29 → 44,97 | 0 → 0 | 1,0 → 0,6 | 0,0 → 0,0 | — |
| bigstack | vanilla | 2 | 0,0356 → 0,0454 | 0,1084 → 0,1137 | 0,0 → 0,0 | 35,62 → 11,35 | 1280 → 131072 | 1,0 → 4,0 | 0,0 → 0,0 | — |
| redstone | vanilla | 100 | 0,4962 → 0,3429 | 0,9192 → 1,2203 | 1,2 → 0,7 | 10,01 → 6,87 | 25600 → 25600 | 49,6 → 50,0 | 20,0 → 0,0 | — |
| tablet | vanilla | 1000 | 1,0413 → 1,0548 | 1,4276 → 2,0344 | 100,0 → 100,0 | 46,38 → 41,03 | 230006 → 254556 | 22,5 → 25,7 | 0,0 → 0,0 | 0,1667 → 0,1234 |

Leitura:
- **sparse:** com 1 origem ativa e 249 vazias, as visitas por tick caem de ~250 para ~5 e o custo do mod cai 3,6× (vanilla) e 2,7× (Sophisticated). O µs/visita sobe porque sobram só as visitas que trabalham.
- **stock:** destino no estoque passa de 1,5 ms/tick (orçamento esgotado em 96% dos ticks, nada se move) para 0,025 ms/tick, 60× menos.
- **bigstack:** a pilha enorme num slot passa de 1.280 itens/s para 131.072 itens/s, o teto do Elite.
- **redstone:** 20 remontagens/s por um relógio que ninguém usa viram 0.
- **mixed:** orçamento esgotado cai de 58% para 27,5% dos ticks com a mesma vazão; **many soph** move 15% mais (97,6 mil → 112 mil itens/s); **tablet** −26% no Tablet aberto e +11% de vazão.
- **raw vanilla:** esta rodada mostrou uma piora (115 → 141 µs/visita). A causa era o #14, que no baú vanilla nunca reconhecia a pilha cheia (o `getSlotLimit` é 99, a pilha para em 64) e só somava trabalho; corrigido em `ae302ec` (o máximo do item decide). Rodadas alternadas de `raw:2:5:vanilla` depois da correção, medianas de µs/visita: base 121 e 136, antes da correção 120 e 137, com a correção 111 e 117 (8–15% abaixo da base). A vazão bruta fica em ~69 mil itens/s, o teto do Ultimate.
- O resto fica dentro do ruído da máquina (idle, full, big, bigfull, types).
