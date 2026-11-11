# Doca de Padrões (spec)

Estado: **etapas 1 (a Doca com o AE2) e 2 (o RS2) implementadas em 11/11/2026**, pelos planos `docs/superpowers/plans/2026-11-10-doca-de-padroes-etapa-1.md` e `2026-11-11-doca-de-padroes-etapa-2.md`; as seções abaixo descrevem o que foi feito, com os ajustes da execução. As etapas 3 (arte animada) e 4 (pedido com item e fluido) seguem como desenho. A regra, o alcance e a arte foram aprovados na proposta (`2026-11-10-ae2-rs2-proposta.md`, seções "Terceira crítica: simplificar", "O alcance da Doca" e "Arte e animação da Doca"). Esta spec junta essas decisões, corrige o que a conferência das APIs mudou (seção "O que a conferência mudou") e fecha a tela, cujo mockup (aprovado em 10/11/2026) está em `docs/preview/doca-de-padroes-tela.html`. As respostas do dono estão no fim.

## A regra

**Cada lado da Doca cuida de um tipo de máquina.** O jogador encosta um provedor de padrões num lado (Pattern Provider do AE2, ou Autocrafter do RS2), vincula as máquinas daquele tipo ao mesmo lado com o Vinculador e põe os padrões delas no provedor. A Doca entrega cada pedido a uma máquina livre do lado, recolhe o resultado e devolve. Não há nada para configurar na tela.

Palavras para o jogador (tela, guia, mensagens): "lado", "máquinas", "pedido", "provedor". Só para quem implementa: unidade, perfil, pendente, porta.

## O que a conferência mudou (10/11/2026)

O código foi lido nos ramos oficiais (AE2 `1.21.1`, RS2 `support/2.x`) e nos addons do ATM10; o resultado completo está na seção "A confirmar" da proposta. O que mexe na Doca:

1. **O AE2 empurra uma execução do padrão por chamada.** A CPU de crafting chama `pushPattern` uma vez por execução, até (coprocessadores + 1) chamadas a cada 3 ticks (`CraftingCpuLogic`). O "empurrão de 1.920 minérios" da primeira crítica só existe com um padrão multiplicado. Um pedido grande ocupa as 30 máquinas mesmo assim, em poucos ticks, porque cada chamada vai para a próxima máquina livre. **Por isso a Doca deixa de repartir uma chamada entre várias máquinas** (o "lote divisível"): o padrão não diz qual é a receita mínima da máquina, e uma divisão errada deixa meia receita parada numa máquina para sempre (um padrão "2 X → 2 Y" de uma máquina que só trabalha de 2 em 2, dividido em dois "1 X"). **Decisão do dono (10/11/2026): tirar.** O guia ensina a multiplicar o padrão até o lote que uma máquina processa num tempo razoável (×8, ×64), porque cada empurrão da CPU enche uma máquina, e a pôr coprocessadores na CPU (decisão do dono, 11/11/2026; ver "Ajustes da execução da etapa 2").
2. **O modo de bloqueio do Pattern Provider não vale para a Doca.** O AE2 só o aplica a inventários comuns; para um `ICraftingMachine` ele confia na resposta. Quem segura o pedido é a Doca. A trava por redstone do provedor continua valendo; a "até o resultado" nunca destrava com a Doca, porque o resultado vai direto para a rede, sem o retorno do provedor (ver "A volta"), e a linha do lado avisa.
3. **Padrões de bancada também chegam.** O provedor oferece ao `ICraftingMachine` todo padrão, inclusive os de bancada, cortador e forja, que são do Molecular Assembler. A Doca recusa os padrões com `supportsPushInputsToExternalInventory() == false`, e a linha do lado diz por quê.
4. **A Doca não pode expor inventário nenhum** (`IItemHandler`, `IFluidHandler`). No AE2, um lado que recusa o `ICraftingMachine` cairia no inventário comum e o provedor despejaria os ingredientes nele; no RS2, o sink de inventário comum é somado ao da Doca e o pedido entraria duas vezes.
5. **A volta vai direto para a rede nos dois mods.** No AE2, o Pattern Provider expõe o inventário de retorno (9 espaços) como `IItemHandler` e `IFluidHandler` comuns, mas ele limita a volta a uns 576 itens por tick por provedor; o `CraftingServiceStorage` do AE2 fica montado na rede com prioridade máxima e é o preferido para tudo, então o que entra no armazenamento da rede passa antes pelas CPUs que esperam aquele item (`CraftingService.insertIntoCpus`): a Doca insere direto na rede ME do provedor (decisão do dono, 11/11/2026: "sempre direto"). No RS2, o Autocrafter não tem inventário: o resultado conta quando entra na rede por qualquer caminho, mas só na execução (as tarefas pegam o resultado intermediário no `beforeInsert`, que a simulação não chama; ver "Ajustes da execução da etapa 2"), então a Doca insere direto na rede do Autocrafter (`StorageNetworkComponent.insert`), sem simular antes. "Devolve ao provedor" vira "devolve à rede do provedor" no RS2.
6. **O RS2 simula e depois executa, e não aceita falha na execução.** Se a execução falhar depois de a simulação aceitar, o RS2 dá os ingredientes por entregues (some com eles, com um aviso no log). A Doca decide tudo na simulação e a execução nunca recusa. Daí o **pendente por lado** (seção "A entrega"). E a Doca nunca responde `LOCKED` (etapa 2, conferido no código): o RS2 junta os resultados dos destinos da mesma posição (`CompositePatternProviderExternalPatternSink.and`) e um `LOCKED` seguido do `SKIPPED` do destino de inventário dele, que não acha nada na Doca, vira `ACCEPTED` (a ordem dos destinos vem de um `HashSet`). A execução que viesse depois recusaria e os ingredientes sumiriam. Toda recusa da Doca ao RS2, também a do pendente, é `REJECTED`; o motivo fica gravado no lado.
7. **Simulação no NeoForge não soma.** Simular dois ingredientes no mesmo inventário, um depois do outro, pode dizer sim aos dois e só caber um. O AE2 resolve igual: o que sobra fica numa lista de envio. A Doca guarda essa sobra no pendente do lado.
8. **Mais padrões por lado já existe nos dois mods.** O ExtendedAE (36 padrões) usa a lógica do AE2 e o Advanced AE (36) tem a dele, mas os dois tentam o `ICraftingMachine` antes de tudo: a Doca os enxerga. No RS2, Autocrafters encadeados (um apontando para o outro) usam o destino do que está virado para a máquina (a raiz da corrente, o último dela, `getChainingRoot`), então uma corrente deles num lado soma os padrões.
9. **Bônus de graça no AE2:** o Pattern Access Terminal agrupa os provedores pelo `getCraftingMachineInfo` da máquina vizinha. A Doca responde com a máquina do lado ("Fornalha × 12"), e o terminal mostra cada provedor com o nome do que ele alimenta.

## Decisões

1. **Um bloco com tier**, como o roteador: `wirelessautomate:pattern_dock`, com a propriedade `tier` (os oito do `RouterTier`; os três do Allthemodium só com o mod, pela regra de sempre). Nome "Doca de Padrões" / "Pattern Dock". Sobe de tier com o Cartão de Upgrade (`TierCoreItem`), cuja dica diz o que muda: "raio 6 → 12, máquinas 8 → 16".
2. **Sem frente e sem giro.** Os seis lados são iguais e nomeados pela direção do mundo (Norte, Sul, Leste, Oeste, Cima, Baixo), como o jogador os vê com F3. Cada lado tem uma cor fixa de etiqueta (seção "Cores").
3. **Itens ou fluidos por pedido, não os dois juntos** nesta versão. Um pedido com item e fluido é recusado e a linha do lado avisa. Químicos e Source ficam para quando o Applied Mekanistics e o Refined Types forem lidos.
4. **Mesma dimensão, dentro do cubo do tier.** A ligação pela rede do mod (outra base, outra dimensão) fica para depois.
5. **Uma máquina pertence a um lado de uma Doca só.** Ela pode estar vinculada também a um roteador (decisão do dono, 10/11/2026): **o roteador leva a energia, a Doca leva só os pedidos**, e o combustível que a receita gasta vai dentro do padrão, como o AE2 faz. A Doca não gasta energia nem combustível. O guia ensina essa divisão. A unicidade é conferida contra todas as máquinas vinculadas (ativas ou não) das Docas carregadas; uma máquina de uma Doca descarregada pode entrar em outra (limite aceito pelo dono, 10/11/2026).
6. **AE2 primeiro**, RS2 na etapa seguinte (decisão do dono, 10/11/2026).

## O alcance (aprovado)

A tabela do roteador de alcance, um degrau acima. A Doca lê as chaves `machineRadius` e `maxMachines` do tier seguinte na config (`tiers.<tier>`); quem ajusta o roteador no modpack ajusta a Doca junto.

| Tier da Doca | Raio | Máquinas (a Doca inteira) |
| --- | --- | --- |
| Básico | 6 | 8 |
| Avançado | 12 | 16 |
| Elite | 16 | 32 |
| Esmeralda | 24 | 48 |
| Allthemodium | 32 | 64 |
| Vibranium | 48 | 96 |
| Unobtainium | 64 | 128 |
| Ultimate | 96 | 256 |

- O Ultimate tem chaves próprias, porque não há linha seguinte e o raio passa do teto do roteador (`RangeTiers.MAX_RADIUS` = 64): `patternDock.ultimateRadius` (96, de 0 a 128) e `patternDock.ultimateMaxMachines` (256, de 0 a `RangeTiers.MAX_MACHINES`).
- Distância de Chebyshev (o maior dos três eixos), como o roteador de alcance (`RangeRules`).
- O limite é da Doca inteira, somando os seis lados. Máquina fora do cubo ou além do limite não vincula, com a mensagem do motivo.
- A config baixou: as máquinas de fora continuam vinculadas, param de receber e a linha do lado avisa. Nada é desvinculado sozinho.
- Máquina em chunk descarregado fica de fora até carregar (`level.isLoaded` antes de qualquer consulta); a Doca nunca carrega chunk.
- **Ao colocar a Doca**, o cubo do alcance aparece por 5 segundos só para quem colocou. Depois, sempre que o jogador segura o Vinculador com a Doca escolhida, com as máquinas de cada lado acesas na cor do lado. É um desenho próprio da Doca (`client/dock/DockReachPaint`, pelo `DockReachPayload`); o roteador de alcance não tem um. O cliente guarda o último cubo e o mostra de novo ao pegar o Vinculador; sem cubo guardado (o jogador entrou no mundo de novo), pede um ao servidor (`DockReachRequestPayload`): um pedido sem resposta dobra a espera até o próximo (de 1 s até 10 s), e depois do de 10 s os pedidos param até o Vinculador sair da mão. O pacote acende até `RangeTiers.MAX_MACHINES` máquinas, o maior limite que a config aceita.

## Vincular (o Vinculador)

1. **Clique direito numa face da Doca:** escolhe a Doca e aquele lado. A action bar diz "Doca de Padrões · lado Norte (Fornalha) · 30/32".
2. **Ctrl + clique numa máquina:** vincula ao lado escolhido todas as máquinas do mesmo bloco no cubo, da mais perto para a mais longe, até o limite. É o `SameBlockLinks` de hoje, com a Doca no lugar do roteador (a varredura, o teto de leitura `MAX_READS` e o descanso de 10 ticks são os mesmos).
3. **Clique simples numa máquina:** vincula só ela; numa já vinculada a esse lado, desvincula.
4. **Recusas, com o motivo na action bar:** o lado já é de outro bloco ("Este lado é de Fornalha: use outro lado"); a máquina é de outro lado ou de outra Doca (com o nome e o lado de quem a tem); fora do cubo; limite do tier; área protegida (`mayBuild`/`mayInteract`, como hoje).
5. Clicar no provedor encostado não faz nada de especial: o Vinculador só vincula máquinas.

## O perfil das faces da máquina

A Doca precisa saber em que face de cada máquina pôr os ingredientes e de qual tirar o resultado. Ela usa o Afinador do Configurador sem o jogador pedir:

- **Ao vincular a primeira máquina de um lado**, a Doca roda a análise (`TunerProbe` + `TunerPlan`) nessa máquina e guarda o resultado como o perfil do lado: as faces Insere e as faces Extrai, por tipo. A action bar mostra "Lado Norte: Fornalha · Cima Insere · Baixo Extrai". As outras máquinas do lado usam o mesmo perfil, pela frente de cada uma (`RemoteSide`).
- **Se o Afinador errar** (uma máquina de lados configuráveis, por exemplo), o jogador usa o Configurador numa máquina do lado, com o gesto de sempre (dois cliques), e o resultado vale para o lado inteiro, sem exceção por máquina (decisão do dono, 10/11/2026).
- **Máquina com duas entradas** (combustível e minério, ou dois ingredientes em lados diferentes): cada ingrediente vai para a face Insere **mais seletiva** entre as que o aceitam, a que aceita menos ingredientes do pedido; no empate, a primeira pela ordem do perfil. Num pedido "minério + carvão" numa fornalha, a face de cima aceita os dois e a do lado só o carvão, então o carvão vai para o lado e o minério para cima.

## A entrega

Quando o provedor oferece um pedido a um lado:

1. **Recusa logo** se o lado não tem máquina ativa, se tem pendente, se o pedido mistura item e fluido, se é um padrão de bancada (AE2), ou se o orçamento do motor deste tick acabou. O provedor tenta de novo no tick seguinte.
2. **Escolhe a máquina** a partir do cursor do lado: pula as dormindo, as descarregadas e as de fora do alcance; para cada candidata, simula cada ingrediente nas faces Insere dela. A primeira que aceita tudo recebe. No máximo 8 candidatas por pedido; se nenhuma aceita, recusa.
3. **Executa** na máquina escolhida. O que ela não aceitar na execução (a simulação que não soma) vai para o **pendente do lado**, e o pedido conta como aceito.
4. **Avança o cursor** para depois da máquina que recebeu: os pedidos se revezam entre as máquinas.

- **Máquina que recusa dorme**, como os destinos do motor hoje: acorda quando ela muda (o `setChanged` dela, pelo mixin e o `RemoteIndex`, que passa a conhecer a Doca) ou no teto de sono. O custo de um pedido não cresce com o número de máquinas cheias.
- **O pendente** é uma lista pequena por lado (o resto de um pedido, salvo no NBT; ao quebrar a Doca, o pendente inteiro, itens e fluidos de todos os lados, vai no item da Doca, no componente `pattern_dock_pending` com o bloco de cada lado, e volta aos mesmos lados ao recolocá-la). Enquanto existir, o lado recusa pedidos novos e tenta entregá-lo primeiro, na máquina que recebeu o pedido; se ela saiu do lado, foi quebrada ou trocada por outro bloco, ou não tem mais inventário numa face Insere, ele passa para a próxima máquina ativa do lado. O lado com pendente guarda o bloco mesmo sem máquinas, até o pendente sair numa máquina daquele bloco. É o que garante "espera quando todas estão cheias, sem perder nada". Um fluido que o provedor recusa depois de aceitar na simulação volta à máquina pela face Extrai, e o que ela não aceitar também se perde (um handler que mente na simulação).
- **AE2:** a capability `AECapabilities.CRAFTING_MACHINE` em cada face; `acceptsPlans()` é falso no lado sem máquina ativa ou com pendente, e `pushPattern(padrão, entradas, lado)` faz os passos acima. O lado vem no último argumento.
- **RS2:** um `PatternProviderExternalPatternSinkFactory` registrado no carregamento do mod. O Autocrafter cria o destino uma vez e o guarda, então o destino da Doca é preguiçoso: a cada pedido confere se ainda há uma Doca na posição (senão `SKIPPED`). `SIMULATE` faz os passos 1 e 2 e devolve `ACCEPTED` ou `REJECTED`; `EXECUTE` faz o 3 e sempre devolve `ACCEPTED`. `isEmpty()` diz se o lado está sem pendente e sem máquina trabalhando (a trava "até a máquina esvaziar" do Autocrafter).
- **O orçamento:** a entrega roda dentro do tick do AE2 ou do RS2, fora do gerenciador. O tempo dela vira uma dívida no `TickBudget` (`charge`), descontada no começo do tick seguinte do motor; sem folga para a dívida (`outsideRoom`), a oferta é recusada e o provedor tenta no tick seguinte.

## A volta

- **A volta é um visitante do motor** (`dock/DockReturn`, um `EngineVisitor` por Doca, visitado no laço de transferência do `NetworkManager` sob o mesmo orçamento de 1 ms). Ela não é uma rota de rede: o motor é preso ao roteador em toda parte, e a Doca entra pelas costuras genéricas (`RemoteListener`, `CacheOwner`, `EngineVisitor`). O visitante guarda as máquinas acordadas (as que receberam pedido e as que avisaram pelo mixin) e dorme sem nenhuma; tira das faces Extrai até a vazão do tier da Doca (a tabela do roteador) e insere no vizinho do lado, simulando antes. Sob carga, a volta dos visitantes e a das portas se revezam na frente do orçamento.
- **AE2:** o destino é a rede ME do vizinho, quando ele é um nó do AE2 (`IN_WORLD_GRID_NODE_HOST`): a Doca insere no armazenamento da rede (`getStorageService().getInventory().insert`, simulando antes), e a CPU que espera o item o conta ali. O nó é o da face encostada, ou o principal do bloco (o provedor direcional que empurra para a Doca não expõe o nó nessa face), ou o da parte nessa face; só serve ativo (energia, rede pronta e canal), conferido a cada inserção com três leituras (o nó ativo e na mesma grade). Sem nó ativo ou sem espaço na rede, a volta marca "provedor cheio" e dorme.
- **RS2:** o destino é a rede do Autocrafter (que não tem inventário): a Doca acha o nó pelo `AbstractNetworkNodeContainerBlockEntity` da `common-api` e insere pelo `StorageNetworkComponent`. Aqui a volta não simula a inserção: simula a extração, insere de verdade e tira da máquina o que entrou (ver "Ajustes da execução da etapa 2"). Sem rede (Autocrafter desligado), a volta dorme.
- **Outro vizinho** (um baú, por exemplo): o `IItemHandler`/`IFluidHandler` dele pela face encostada. A rede do vizinho é olhada uma vez, guardada por lado e esquecida quando o bloco vizinho muda.
- Só sai o que a máquina dá pela face Extrai: o combustível e o que está nas entradas ficam.

## A tela

Mockup: `docs/preview/doca-de-padroes-tela.html` (para o ok do dono). Clique direito na Doca abre. Sem inventário do jogador, sem opção nenhuma.

- **Cabeçalho do mod:** a faixa e o Olho na cor do tier, "Doca de Padrões" e o nome do tier, o botão de tema.
- **Resumo:** "30/32 máquinas" com uma barra fina, e embaixo "raio 16".
- **Seis linhas, uma por lado**, na ordem Norte, Sul, Leste, Oeste, Cima, Baixo. Cada uma: a etiqueta na cor do lado com o nome dele; o ícone e o nome da máquina; "12 máquinas · 9 trabalhando"; e o estado em uma frase com um sinal de forma (ponto cheio, anel, triângulo), para não depender só da cor.
- **Lado sem nada** (sem provedor e sem máquina): a linha fica apagada, com "livre".
- **"Tem provedor"** sem tipos fora da API do AE2: o vizinho do lado expõe a `GENERIC_INTERNAL_INV` e não a `ME_STORAGE` (o Pattern Provider tem o retorno; a Interface tem o estoque), ou o lado recebeu uma oferta do AE2 nos últimos 60 s (os provedores de addon). No RS2: o vizinho é um Autocrafter (o id no namespace `refinedstorage` terminado em `autocrafter`) ou o lado recebeu uma oferta dele nos últimos 60 s.
- **A dica da linha** (mouse em cima) diz o provedor encostado ("Pattern Provider", "Autocrafter") e o motivo inteiro.

Os estados, do mais grave ao mais leve (a linha mostra o primeiro que vale):

| Estado | Quando | Sinal |
| --- | --- | --- |
| Sem provedor neste lado | Há máquinas e nada encostado que empurra | triângulo, erro |
| Nenhuma máquina vinculada | Há provedor e nenhuma máquina | triângulo, erro |
| Tire a trava do provedor | O provedor do AE2 está em "travar até o resultado", que nunca destrava com a Doca (lido pela API só quando a tela confere) | triângulo, erro |
| Padrão de bancada recusado | O último pedido recusado era de bancada (AE2) | triângulo, erro |
| Pedido com item e fluido recusado | O último pedido recusado misturava os dois | triângulo, erro |
| Provedor cheio | A volta está parada porque o destino recusou (a rede cheia, sem energia ou sem canal, ou o inventário encostado cheio) | anel, atenção |
| Todas cheias | O último pedido não achou máquina livre | anel, atenção |
| Pedido pendente | O resto do último pedido espera a máquina receber (o lado recusa pedidos até lá) | anel, atenção |
| N fora do alcance | A config baixou o raio ou o limite | anel, atenção, como nota ao lado do estado principal |
| N em área descarregada | Máquinas em chunk descarregado | anel, atenção, como nota |
| Trabalhando | Alguma máquina recebeu pedido ou devolveu nos últimos 15 s | ponto, ok |
| Pronto | Tem provedor e máquinas, e nada a fazer | ponto, cinza |

"Trabalhando" conta as máquinas cuja última entrega ou volta foi nos últimos 15 s (um `long` por máquina, sem ler a máquina). Os motivos de recusa guardados são o último por lado, que some no próximo pedido aceito.

**Sincronia:** só com a tela aberta, uma vez por segundo e só se algo mudou (um payload pequeno por lado: bloco, contagens e o código do estado). Fechada, nada vai para o cliente.

## Cores

- **Tier:** `GuiPaint.tierColor` e a lente do `identidade.py`, para a faixa, o Olho, o circuito animado e o cristal.
- **Lados (etiquetas na arte e na tela):** provisórias, tiradas da paleta de recursos: Norte `#B57A3A`, Sul `#2F6FD6`, Leste `#D9931A`, Oeste `#5E9A2B`, Cima `#8E4FC9`, Baixo `#C2486B`. As mesmas acendem as máquinas no mundo com o Vinculador.
- **Estados:** os do `ESTADO` do `identidade.py`: ok `#2F9D5C`, atenção `#D88A1A`, erro `#D5453A`; "Pronto" no cinza de texto apagado do tema.

## Integração com os mods (sem quebrar sem eles)

- Tipos do AE2 só em `compat/ae2`, tipos do RS2 só em `compat/refinedstorage`, nunca na assinatura de um método de classe `@EventBusSubscriber` ou `@GameTestHolder` (a regra do Mekanism). O bloco e a volta pelo lado do AE2 usam só capabilities do NeoForge.
- Sem nenhum dos dois, a Doca existe, vincula e mostra "Sem provedor neste lado". A receita fica para o balanceamento das receitas e só carrega com o AE2 ou o RS2; a Doca fica sempre na aba criativa (decisão do dono, 10/11/2026).
- **Dependências:** AE2 `org.appliedenergistics:appliedenergistics2:19.2.17:api` (Maven Central) como `compileOnly`, a mesma versão que o `runClient` já carregava (o ATM10 8.5 traz a 19.2.18; a API usada é a mesma); RS2 `com.refinedmods.refinedstorage:refinedstorage-common-api:2.0.9` (com as APIs que ele traz, no `maven.creeperhost.net`) como `compileOnly`. Os mods inteiros (AE2 com o GuideME; `refinedstorage-neoforge` 2.0.9) só no `runClient` e nas runs de GameTest novas. São as versões do ATM10 8.5.
- **Runs novas:** `runGameTestServerAe2` e `runGameTestServerRefined`, como a dos químicos. O `runGameTestServer` continua sem os dois e prova que o mod carrega sem eles.
- **Os addons do AE2 do ATM10** (Advanced AE 1.6.12 e ExtendedAE 1.21-2.2.43, com o GeckoLib e o Glodium; os dois trazem o `ae2wtlib_api` e o Advanced AE o `ae2addonlib` dentro do jar) estão no `runClient` e na run `runGameTestServerAe2`, sem código nem dependência de compilação: o Extended Pattern Provider usa a `PatternProviderLogic` do AE2; o Advanced Pattern Provider tem lógica própria (`AdvPatternProviderLogic`), mas tenta o `ICraftingMachine` antes de tudo; a CPU quântica (`AdvCraftingCPULogic`) recebe a inserção na rede por um mixin no `CraftingService.insertIntoCpus`, então conta a volta direta da Doca. Os três têm GameTest com um pedido de verdade pela CPU (`Ae2AddonDockGameTests`; a CPU quântica é um Quantum Core sozinho, que já forma o multibloco).
- **O teto de entregas é o orçamento do motor**, não o provedor: uma CPU quântica grande empurra muitas execuções por tick, e cada oferta à Doca custa de 2,5 a 3,9 µs (bench `doca`). Com o orçamento de 1 ms por tick, o mod inteiro entrega da ordem de 5 a 8 mil pedidos por segundo; o resto é recusado com o motivo `BUDGET` antes de tirar qualquer coisa do provedor, e a CPU tenta de novo no tick seguinte: espera, não perda.

## Custo de performance (lei por lei)

- **Parado custa zero.** A Doca não tem ticker. A entrega só roda quando o provedor oferece; a volta são portas do motor que dormem com a máquina vazia.
- **Sem busca por pedido.** As faces Insere de cada máquina ficam em `BlockCapabilityCache` (`MachineCaches`); o provedor encostado também. O destino do RS2 confere a Doca na posição com uma leitura de block entity por pedido.
- **A exceção: a trava "até a máquina esvaziar" do Autocrafter.** Com ela, o RS2 pergunta `isEmpty()` ao destino a cada tick do Autocrafter: uma leitura de block entity (a Doca na posição) e um laço pelas máquinas do lado, comparando o `long` da última atividade, sem consultar nenhuma máquina. É o único custo por tick da Doca, e só existe com essa trava ligada.
- **Proporcional ao trabalho.** Por pedido: no máximo 8 candidatas, cada uma com uma simulação por ingrediente. Máquinas cheias dormem fora do caminho.
- **Sem pico.** O Ctrl + clique é o de hoje (teto de leitura). No raio 96 do Ultimate ele olha uns 170 chunks contra uns 80 no 64: medir antes de fechar.
- **Cliente só com o que muda.** Tela: 1 Hz e só com mudança. O cubo ao colocar: um payload só para quem colocou.
- **Memória:** por lado, o perfil, a lista de máquinas (posição e o `long` da última atividade), o cursor e o pendente. Nada por pedido.
- **Bench:** o cenário `doca` do `bench.sh` (32 fornalhas numa Doca Elite, um baú no lugar do provedor, 32 pedidos por tick pela API da entrega): o mod de 0,059 para 0,083 ms/tick com a volta ligada, 640 pedidos aceitos e 256 itens devolvidos por segundo (`docs/benchmark.md`). O cenário `doca-ae2` mede o caminho inteiro do AE2 (CPU de 32 coprocessadores, Pattern Provider e 32 Altos-fornos), com o AE2 no servidor do bench só nas rodadas com ele.
- **Protocolo:** menu e payloads novos (`DockStatePayload`, `DockReachPayload`, `DockReachRequestPayload`): `ModPayloads.VERSION` foi de 49 para 50.

## Testes

- **JUnit** (lógica pura): o alcance um degrau acima e o Ultimate; a escolha da máquina pelo cursor (dormindo, fora do alcance, descarregada, máximo de 8); o estado da linha a partir dos fatos do lado; o pendente.
- **GameTests comuns** (sem os mods): vincular por lado, a recusa de outro bloco no mesmo lado, a máquina de outra Doca, fora do cubo, o limite do tier, a config que baixa, o perfil pelo Afinador na primeira máquina, quebrar e recolocar com o pendente.
- **`runGameTestServerAe2`:** provedor com um padrão de fornalha encostado num lado e três fornalhas: a CPU entrega e as três trabalham ao mesmo tempo; o resultado vai direto para a rede (o retorno do provedor fica vazio o tempo todo) e o pedido termina; a rede sem energia segura a volta ("provedor cheio") e a retoma (só a energia cai: o drive fica na grade); o provedor quebrado com o resultado na máquina segura a volta e a retoma no provedor novo; todas cheias seguram o provedor sem perder nada; padrão de bancada recusado; o provedor com "trava até o resultado" mostra o estado novo na linha; o Pattern Access Terminal mostra o nome do lado.
- **`runGameTestServerRefined`:** o mesmo com o Autocrafter, mais a simulação seguida de execução que nunca perde ingrediente, o resultado intermediário que chega à tarefa com o disco cheio e a trava "até receber todos os resultados" com a volta direta.
- **e2e:** a tela nos dois temas, nos estados do mockup.

## Etapas

1. **A Doca com o AE2:** bloco com tiers, modelo estático (sem animação), alcance, Vinculador, perfil pelo Afinador, entrega e volta pelo AE2, tela e payloads, cubo ao colocar, traduções, loot table, tag de picareta, JEI (informação), guia, a run `runGameTestServerAe2`, e2e da tela, bench.
2. **O RS2:** o destino, a volta pela rede, a run `runGameTestServerRefined`.
3. **A arte animada:** o circuito em textura animada por tier, o giroscópio, o pacote e os fragmentos no renderer (skill `wa-shaders`), o laço de trabalho quando a Doca entrega.
4. **Pedido com item e fluido juntos** (o caso comum do Mekanism).

## Fora de escopo

Repartir uma chamada entre máquinas; a Doca guardando os próprios padrões; ligar a Doca pela rede do mod (outra dimensão); pedido de fabricação pelo estoque; químicos e Source; a Doca no Tablet e na Tela de Sinal.

## Respostas do dono (10/11/2026)

| Pergunta | Resposta |
| --- | --- |
| Repartir uma chamada entre máquinas (lote divisível) | Tirar: cada pedido inteiro numa máquina |
| Ordem dos mods | AE2 na etapa 1, RS2 na etapa 2 |
| Afinador que errou | O Configurador numa máquina refaz o perfil do lado inteiro |
| Receita | Fica para o balanceamento das receitas, só com o AE2 ou o RS2 |

Falta só o ok do mockup da tela (`docs/preview/doca-de-padroes-tela.html`); depois, o plano da etapa 1.

## Ajustes da execução (11/11/2026)

O que a implementação da etapa 1 mudou em relação ao desenho, além do que já está nas seções acima:

- **O bloco não esconde as faces dos vizinhos** (`noOcclusion`): as vigias de lados opostos se alinham, e com oclusão se veria o vazio pela vigia. A colisão continua cheia; a luz atravessa a Doca.
- **O modelo estático** tem 174 elementos (das 193 caixas saíram o giroscópio, o cristal, o pacote e os fragmentos, da etapa 3); o circuito brilha no escuro e é estático até a textura animada da etapa 3.
- **A face mais seletiva** vale para a entrega: num pedido "minério + carvão" a fornalha recebe o carvão na face do combustível.
- **O primeiro vínculo de um lado** roda o Afinador e grava o perfil; o lado esquece o perfil quando a última máquina sai.
- **Recarga da config:** o raio e o limite novos valem sem recolocar a Doca (os visitantes recebem o aviso da recarga).
- **O Ctrl + clique no raio 96** (Ultimate) custa de 2,4 a 3,9 ms por clique (só no clique, com o descanso de 10 ticks).
- **Unicidade por todas as vinculadas** (decisão do dono, 11/11/2026): a recusa "máquina de outra Doca", o Ctrl + clique e o Configurador conferem todas as máquinas vinculadas às Docas carregadas, ativas ou não (fora do alcance ou além do limite também), por um índice próprio (`DockIndex`) que o mixin não consulta. A Doca descarregada continua sem ser vista.
- **O pendente vai no item** (decisão do dono, 11/11/2026): a quebra leva o pendente de todos os lados no componente `pattern_dock_pending` (só com pendente, para as Docas vazias empilharem), pela loot table (`copy_components`, com o `survives_explosion`); no criativo ou sem a ferramenta, o próprio bloco solta o item, como a caixa de shulker. Recolocada, cada lado volta com o bloco e o pendente e, sem máquinas, guarda o bloco até ele sair. Sem item nenhum (`/setblock`, a explosão que destrói o drop), os itens pendentes caem no chão.

## Ajustes da execução da etapa 2 (11/11/2026)

- **Toda recusa ao RS2 é `REJECTED`**, também a do pendente, nunca `LOCKED` (item 6 de "O que a conferência mudou"): conferido no código do RS2 durante a etapa.
- **Os recursos do RS2 vêm da `refinedstorage-common`**, interna (`@API(INTERNAL)`): o `ItemResource` e o `FluidResource` não estão na `common-api` publicada. A dependência é `compileOnly` das duas; conferir a cada versão nova do RS2 (pendência registrada).
- **A volta do AE2 vai direto para a rede ME** (decisão do dono, 11/11/2026: "sempre direto"), pelo mesmo molde da do RS2 (`compat/ae2/Ae2DockNetwork` e `compat/refinedstorage/Rs2DockNetwork`, os dois um `dock/DockReturnNetwork`): sai o teto do retorno de 9 espaços do Pattern Provider, e o provedor não fica no meio. A ordem de escolha do destino do lado é a rede do AE2, a do RS2 e, por último, o inventário do vizinho.
- **A trava "até o resultado" deixa de destravar** com a Doca (ela só destrava pelo retorno do provedor, que a volta não usa mais). A tela lê a trava pela API (`IConfigurableObject`, `Settings.LOCK_CRAFTING_MODE`) e mostra "Tire a trava do provedor" (`PROVIDER_LOCK`, logo depois de "Nenhuma máquina vinculada"). A leitura é só na conferência da tela, nunca por tick.
- **O conselho dos padrões muda** (decisão do dono, 11/11/2026): multiplicar o padrão até o lote que uma máquina processa num tempo razoável (×8, ×64), porque cada empurrão da CPU vai inteiro para uma máquina e a enche; mais coprocessadores continuam ajudando, e pedidos pequenos ocupam menos máquinas.
- **"Autocrafter × N" na dica do provedor ficou de fora**: a direção do Autocrafter (para contar a corrente) está numa classe interna do RS2 (`AbstractDirectionalBlock`), fora da `common-api`; a dica mostra o nome do Autocrafter encostado.
- **A volta para a rede do RS2 insere antes de tirar** (revisão final da etapa 2). O `RootStorageImpl.insert` do RS2 só chama os `beforeInsert` das tarefas com `Action.EXECUTE` (o teste `shouldNotNotifyListenerWhenSimulating` do próprio RS2); a simulação vai direto ao disco. Com os discos cheios ou sem disco, a simulação dava zero, o lado marcava "provedor cheio" e a tarefa parava com o resultado intermediário na máquina. A saída (a) era somar à simulação o que as tarefas ainda esperam daquele recurso, mas a API publicada não mostra isso: o `TaskStatusProvider` monta o estado inteiro de cada tarefa (caro, e o que conta é o `expectedOutputs` interno de cada `ExternalTaskPattern`). Ficou a (b), só nesse destino (`DockReturnNetwork.insertsFirst`): simular a extração da máquina, inserir de verdade na rede o que ela ofereceu, tirar da máquina exatamente o que entrou. Mesmo slot, mesmo tick, mesma thread e nada no meio, então a máquina dá o que prometeu; se der menos (uma máquina que mente na simulação), o que entrou a mais sai da rede de novo (`reclaim`, uma extração do armazenamento) e, se uma tarefa já o pegou, vai para o log. Nunca sai da máquina mais do que entrou, e nada vai para o chão sem antes voltar para a máquina. O produto final do pedido (a raiz) o RS2 só conta depois de guardado (`afterInsert`): sem lugar no disco, ele fica na máquina e o lado marca "provedor cheio", como com qualquer importador do RS2.
- **As travas do Autocrafter com a Doca:** "até receber todos os resultados" funciona com a volta direta (a tarefa avisa o Autocrafter ao pegar o resultado inserido na rede; GameTest). "Até a máquina esvaziar" lê o lado como vazio só sem pendente e sem entrega ou volta nos últimos 15 s (`DockStatus.WORKING_TICKS`): depois de cada movimento o lado inteiro fica travado 15 s, mesmo com as máquinas já vazias, e só um lote por vez circula nele.
- **O mínimo do RS2 no `neoforge.mods.toml` é 2.0.9**: o `ItemResource`, o `FluidResource` e o `PlatformPatternProviderExternalPatternSink` só foram conferidos nela.
- **A captura do e2e com um lado de Autocrafter ficou de fora** (plano da etapa 2, tarefa 4): o `E2eDockSteps` não monta um Autocrafter. O "tem provedor" com o Autocrafter tem GameTest (`providerDetection` do RS2); a captura fica para quando a tela da Doca mudar.
