# Integração com o Applied Energistics 2 e o Refined Storage 2 (proposta)

Estado: **a Doca de Padrões e o alcance dela foram aprovados pelo dono em 10/11/2026** (as seções "Terceira crítica: simplificar" e "O alcance da Doca"); o resto é proposta. Nada implementado. As APIs citadas foram lidas no código dos ramos oficiais (AE2 ramo `1.21.1`, linha 19.x; RS2 ramo `support/2.x`, linha 2.0.x) por pesquisa na web, sem compilar nada; o que não deu para confirmar está em "A confirmar antes de qualquer plano".

**Para a Doca de Padrões, vale a seção "Terceira crítica: simplificar"**; a feature 3 e as duas primeiras críticas ficam como o caminho que levou até ela.

## Ponto de partida

Hoje o mod já conversa com os dois pelas capabilities comuns do NeoForge: um roteador numa Interface move itens e fluidos, e a energia chega a eles como a qualquer máquina. A especificação lista "AE2 (planejado)" e "Refined Storage 2 (planejado)" só como atalho de vazão (`docs/especificacao.md`, Integrações). Esta proposta vai além do atalho: o que cada lado ganha quando um sabe do outro, com o crafting no centro.

O princípio é o mesmo das outras integrações: os dois mods são opcionais, os tipos deles ficam só em `compat/ae2` e `compat/refinedstorage`, o resto do mod passa por uma ponte sem esses tipos (como `Chemicals` e `Sources`), e cada um ganha a sua run de GameTest (skill `wa-mod-opcional`).

## As features

### 1. Armazenamentos do mod visíveis como estoque da rede (pequena a média)

**O que o jogador ganha:** encosta um Storage Bus (AE2) ou um External Storage (RS2) no Baú, no Tanque ou na Bateria do mod e a rede enxerga o conteúdo inteiro, com as quantidades reais em `long` e sem varrer slot por slot. O Baú do mod (tipos ilimitados, bilhões de itens) vira o depósito de massa de uma rede ME ou RS.

**Como:**
- AE2: o Baú e o Tanque expõem a capability `AECapabilities.ME_STORAGE` (`MEStorage`: `insert` e `extract` com `AEKey` e `long`, `getAvailableStacks`). O Storage Bus procura essa capability primeiro, antes de cair no `IItemHandler`.
- RS2: registrar um `ExternalStorageProviderFactory` (`RefinedStorageApi.addExternalStorageProviderFactory`); o `ExternalStorageProvider` também é em `long` e dá a lista por iterador.

**Custo:** nenhum parado. A lista de estoque só é montada quando o outro mod pede; ela sai direto do `KeyedStorage`, que já guarda por tipo. Cuidar para o aviso de mudança ir pelo caminho que o `StorageNeighbors` já usa (uma vez por tick), não a cada inserção.

**Por que primeiro:** é a de maior ganho pelo menor trabalho, e não muda nada no motor.

### 2. Atalho do roteador numa rede ME ou RS (média)

**O que o jogador ganha:** um roteador preso numa Interface move milhões de itens por chamada, em vez de uma pilha por vez. É o "atalho planejado" da especificação, o mesmo ganho que os armazenamentos do mod já têm com o `BulkItems`.

**Como:** a porta do roteador, ao montar a rota, pergunta à ponte se a face tem um estoque em `long` (AE2: a capability `ME_STORAGE` do bloco; RS2: a rede do bloco, pelo `StorageNetworkComponent`) e, se tiver, usa uma visão `BulkItems`/`BulkFluids` por cima dela.

**Depende de confirmar:** se a Interface do AE2 expõe `ME_STORAGE` para vizinhos comuns (ver "A confirmar"). Se não expuser, o atalho só existe pela Doca da feature 3, que é um nó da rede.

**Custo:** a capability entra no `BlockCapabilityCache` como as outras. O risco é o `getAvailableStacks`, que monta um mapa do estoque inteiro: nunca por tick. A origem ME só deve ser lida com filtro de lista branca (extrai tipo por tipo, sem listar), e a face sem filtro numa rede ME fica fora do atalho.

### 3. Doca de Padrões: o autocrafting entregando pelas redes do mod (grande; a peça central)

**O problema que resolve:** no AE2 e no RS2, cada máquina de processamento precisa de um Pattern Provider (ou Autocrafter) encostado nela. Com vinte máquinas, são vinte provedores, canais e cabos. E quem tenta contornar com um baú e um roteador esbarra em dois limites: os ingredientes de receitas diferentes se misturam no baú, e o round-robin espalha os ingredientes de uma receita por várias máquinas, deixando todas com meio lote.

**O que o jogador ganha:** um bloco novo, a Doca de Padrões. Ele encosta **um** Pattern Provider (ou Autocrafter) nela e liga a Doca a uma rede do mod. Cada pedido de fabricação é entregue **inteiro a uma única máquina** da rede, perto ou longe, até em outra dimensão nos tiers que permitem. As máquinas da rede se revezam, então vinte fornalhas atendem em paralelo com um provedor só. O resultado volta pelo caminho que já existe: a face de saída da máquina extrai para uma rede cuja Interface insere.

**Como:**
- AE2: a Doca expõe `AECapabilities.CRAFTING_MACHINE` (`ICraftingMachine`: `acceptsPlans` e `pushPattern(IPatternDetails, KeyCounter[], Direction)`). O Pattern Provider tenta essa capability antes de qualquer inventário, entrega o lote todo ou nada, e ainda diz **qual padrão** é. Com isso a Doca pode ter destino por padrão: "este padrão vai para a rede Fundição, aquele para a Serraria".
- RS2: registrar um `PatternProviderExternalPatternSinkFactory` (`RefinedStorageApi.addPatternProviderExternalPatternSinkFactory`); o sink recebe a coleção de recursos do lote (`accept(Collection<ResourceAmount>, Action)`). Aqui não vem a identidade do padrão, só o conteúdo, então o destino é escolhido pelo conteúdo ou é um só por Doca.
- No motor: a novidade é a **entrega em lote**, descrita na feature 4. A Doca guarda o lote num buffer próprio e só aceita o próximo quando o anterior saiu (é o que faz o modo de bloqueio do provedor funcionar).

**Custo:** a Doca não tem tick; ela é uma origem como outra qualquer, que acorda quando recebe um lote. O buffer é pequeno (um lote). A parte cara é a entrega em lote, que simula o lote inteiro no destino antes de mover: mais chamadas que uma entrega comum, mas só quando há lote.

### 4. Entrega em lote no motor (média; serve mesmo sem AE2 e RS2)

**O que é:** um modo novo de face de origem, "Lote": o conjunto que está na origem vai inteiro para **um** destino, que precisa aceitar tudo (simulação primeiro, como o Pattern Provider faz), em vez de ser repartido entre os destinos.

**Por que separada:** é pré-requisito da Doca, mas vale sozinha. Qualquer receita de vários ingredientes distribuída por várias máquinas iguais tem hoje o problema do meio lote, com ou sem autocrafting.

**Custo e cuidado:** o destino que recusa o lote dorme como hoje (por lote, não por tipo). Precisa de bench próprio: a simulação de vários tipos por destino é o caminho novo mais caro desta proposta.

### 5. Pedido de fabricação pelo estoque (grande)

**O que o jogador ganha:** uma face com "manter N" (o estoque do filtro, que já existe) cuja origem é uma rede ME ou RS passa a **pedir a fabricação** do que falta, em vez de só esperar. Exemplo: "manter 64 circuitos nesta montadora"; acabou o estoque da rede, o mod abre o pedido de fabricação sozinho.

**Como:**
- RS2: direto, `AutocraftingNetworkComponent.ensureTask(recurso, quantidade, ator, token)`, que já devolve se faltou recurso, se a tarefa já existe ou se foi criada.
- AE2: mais trabalhoso. Não há "peça N": é calcular o plano (`ICraftingService.beginCraftingCalculation`, assíncrono) e depois submeter (`submitJob`), e quem pede implementa `ICraftingRequester` e **salva os vínculos** dos pedidos em andamento. Isso exige que o bloco seja um nó da rede (`GridHelper.createManagedNode`, `IInWorldGridNodeHost`), com consumo de energia e conexão de cabo.

**Onde mora:** na própria Doca, que nesta etapa passa a ser nó da rede. Evita criar outro bloco.

**Custo:** o pedido só é avaliado quando a face de estoque acorda sem ter de onde tirar, com um intervalo mínimo entre pedidos do mesmo item (o cálculo de plano do AE2 é caro, do lado dele). Nada por tick.

### 6. Tela de Sinal e Tablet lendo a rede ME ou RS (média)

**O que o jogador ganha:** uma peça da Tela de Sinal ligada a uma Doca mostra o estoque de um item na rede, os pedidos de fabricação em andamento e o consumo de energia da rede. É o painel de estoque que muita base monta com outros mods.

**Como:** AE2, pelo `IStorageService.getCachedInventory()` (cache que o próprio AE2 atualiza no máximo uma vez por tick) e pelo `ICraftingService`; RS2, pelo `StorageNetworkComponent` e pelo `AutocraftingNetworkComponent`. Depende de a Doca ser nó (feature 5).

**Custo:** só com alguma Tela ligada e à vista, na cadência que a Tela já usa para os outros valores.

### 7. Acabamento (pequena, junto de cada etapa)

- **Afinador do Configurador:** reconhecer Interface, Pattern Provider, Autocrafter e a Doca, e propor o modo certo das faces.
- **Guia:** uma página por mod, com as montagens prontas (depósito de massa, linha de fundição com uma Doca, estoque mantido).
- **JEI:** a informação da Doca.

## Crítica pelo jogador min-max (10/11/2026)

O caso de teste: um pedido de X itens tem de ocupar mais de 30 máquinas iguais ao mesmo tempo, com o mínimo de blocos e de cliques. A Doca como descrita na feature 3 **falha** nesse caso em quatro pontos, e a proposta muda assim:

| Defeito da primeira versão | Efeito para o min-max | Correção proposta |
| --- | --- | --- |
| O buffer guarda um lote e só aceita o próximo quando o anterior saiu | A Doca vira o gargalo: um lote por vez, por mais máquinas que haja | Sem buffer no caminho feliz: a Doca só diz "aceito" ao provedor se alguma máquina aceita o lote **agora**, e entrega na mesma chamada. Um buffer pequeno fica só para o lote que perdeu a máquina no meio (máquina quebrada) |
| O lote vai inteiro para uma máquina | Um padrão multiplicado (×64) ocupa uma máquina e deixa 29 paradas; para ocupar as 30 o jogador depende de o AE2 empurrar 30 vezes | **Lote divisível:** a Doca conhece a unidade da receita (no AE2 pelos ingredientes do padrão; no RS2 pelo máximo divisor comum das quantidades) e reparte o lote em unidades inteiras entre as máquinas livres. Um empurrão de 1.920 minérios vira 64 em cada uma das 30. Nunca meia unidade em máquina nenhuma |
| Ligar a Doca às máquinas por duas redes (ida e volta) e 30 roteadores | Trinta roteadores e sessenta faces para configurar | **A Doca tem alcance próprio**, como o roteador de alcance: escolhe-se a Doca no Vinculador e um Ctrl + clique numa máquina vincula todas as do mesmo bloco no raio (o que o Vinculador já faz). As redes ficam para quem quer mandar para longe ou para outra dimensão |
| O resultado volta por uma rede até uma Interface | Mais um bloco e mais uma rede por linha | A Doca recolhe a saída das máquinas vinculadas e devolve ao provedor encostado nela. Depende de confirmar por onde o Pattern Provider aceita o retorno (item 3 de "A confirmar"); se não der, a volta é por uma face Insere numa Interface |

### As três formas de usar, da mais fácil à mais flexível

1. **Um clique (uma linha de máquinas iguais).** Põe a Doca encostada no Pattern Provider. Com o Vinculador, clica na Doca e dá Ctrl + clique numa das máquinas: todas as do mesmo bloco no raio entram, com as faces propostas pelo Afinador. Pronto: o pedido se reparte entre elas e o resultado volta sozinho.
2. **Várias linhas numa Doca só (fornalhas, trituradores, montadoras).** As máquinas vinculadas já ficam em grupos por bloco. Cada padrão que chega pela primeira vez aparece na tela da Doca e vai, por padrão, para o primeiro grupo que aceita todos os ingredientes ("Auto"); o jogador pode fixar "este padrão → este grupo". Só no AE2, que diz qual é o padrão; no RS2 vale o "Auto".
3. **Pela rede (máquinas longe, em outra base ou dimensão).** A Doca vira uma origem em modo Lote numa rede do mod, e qualquer face Insere dessa rede recebe. É a forma da primeira versão da proposta, agora como opção e não como caminho único.

### Limites que o min-max vai encontrar, e o que fazer com cada um

- **Máquinas por Doca.** Se ela seguir a tabela do Upgrade de Alcance, 30 máquinas pedem o tier Esmeralda (32); Ultimate chega a 128 (Avançado 8, Elite 16, Allthemodium 48, Vibranium 64, Unobtainium 96). Decidir se a Doca usa essa tabela ou uma própria.
- **Quantos empurrões o AE2 faz por tick** é dele (CPU e coprocessadores), não do mod. O lote divisível existe justamente para um empurrão grande bastar; o guia deve ensinar a usar padrões multiplicados com a Doca.
- **Custo de escolher a máquina.** Simular o lote em 30 destinos a cada empurrão seria caro. A máquina que recusou dorme, como os destinos de hoje, e a Doca anda por um cursor: o custo por empurrão não cresce com o número de máquinas cheias.
- **Orçamento de 1 ms.** A entrega na mesma chamada do provedor acontece no tick do AE2, fora do gerenciador. Ela tem de contar no orçamento do mod (e recusar quando ele acabou, para o provedor tentar no tick seguinte), senão a Doca vira um jeito de furar o teto.
- **Máquinas que não podem misturar receitas.** Opção "só máquina vazia" por grupo: o lote só vai para a máquina cuja entrada está vazia. Custa uma leitura dos slots de entrada por entrega, só com a opção ligada.
- **Receitas com item e fluido (ou químico) no mesmo lote.** O lote atômico entre tipos de recurso é mais difícil que só itens. Fica para a segunda etapa da Doca, mas precisa existir: é o caso comum no Mekanism.
- **Ver o gargalo.** A tela da Doca mostra máquinas ocupadas e livres, lotes por minuto e o motivo de estar parada ("todas cheias", "sem padrão para o grupo", "orçamento"). Só com a tela aberta.

Com isso a ordem muda: a entrega em lote (feature 4) passa a incluir o lote divisível, e a Doca nasce já com alcance próprio; a ligação por rede vira a parte opcional dela.

## Segunda crítica: 30 padrões para vários tipos de máquina (10/11/2026)

O caso de teste agora: o jogador tem uns 30 padrões, de quatro ou cinco tipos de máquina, e quer tudo numa Doca, configurado em um minuto. A forma 2 da crítica anterior ("Auto" pelo primeiro grupo que aceita, ou fixar padrão por padrão) falha em dois pontos:

- **O "Auto" erra justamente nas bases min-max.** Minério de ferro é aceito pela fornalha, pelo triturador e pela câmara de enriquecimento. "O primeiro grupo que aceita os ingredientes" manda o padrão para a máquina errada sem avisar.
- **Fixar um por um não escala.** Trinta padrões são trinta escolhas numa lista.

Há também um limite físico que a proposta não dizia: **a Doca não guarda padrões**, quem guarda é o provedor encostado nela. O Pattern Provider do AE2 tem poucos espaços de padrão, então 30 padrões pedem vários provedores em volta da Doca (ela tem seis faces).

### Como endereçar muitos padrões depressa

Quatro mecanismos, do que não pede tela ao que pede:

1. **A face decide.** Cada face da Doca pode ser presa a um grupo de máquinas. Tudo o que o provedor da face norte empurra vai para as fornalhas; o da face leste, para os trituradores. O jogador separa os padrões fisicamente, pondo cada um no provedor certo, e não configura padrão nenhum. Funciona igual no RS2, que não diz qual é o padrão mas diz por qual face o lote chegou. Serve até seis tipos de máquina por Doca.
2. **A Doca aprende pelo tipo de receita.** Ao receber um padrão novo, a Doca procura nas receitas do jogo qual tipo de receita produz aquela saída com aqueles ingredientes (fundição, trituração...). Na primeira vez que o jogador manda um padrão de um tipo para um grupo, **todos os outros padrões do mesmo tipo seguem sozinhos**. Trinta padrões de quatro tipos de máquina viram quatro escolhas. A busca acontece uma vez por padrão novo e fica guardada; nada por empurrão. Só no AE2. Quando duas máquinas fazem o mesmo tipo de receita, ou a receita não está no registro comum do jogo, a Doca **pergunta** em vez de adivinhar: o padrão fica "sem destino" na tela e o provedor é recusado até o jogador escolher.
3. **Marcar vários e soltar no grupo.** Na tela da Doca, os padrões já vistos aparecem numa lista com busca, como a dos filtros; Shift + clique marca vários e um clique no grupo manda todos.
4. **Carimbar no mundo.** Com o Vinculador apontado para a Doca, clicar numa máquina vinculada segurando um padrão codificado fixa aquele padrão naquela máquina (ou no grupo dela, com Shift). É o caso do padrão que só pode ir para uma máquina específica, com configuração própria.

A ordem em que valem: padrão fixado (3 ou 4) ganha do aprendido (2), que ganha da face (1). Sem nenhum, o lote é recusado e a tela diz por quê.

### O que mais esse cenário exige

- **Padrão para uma máquina só, não para o grupo.** Os grupos do alcance são por bloco. Para máquinas iguais com configurações diferentes, a Doca precisa de grupos feitos à mão (dividir um grupo), além do carimbo.
- **Ingrediente na face certa da máquina.** Em máquina com duas entradas em lados diferentes, o lote tem de pôr cada ingrediente na face dele. Isso sai do perfil de faces do grupo (faces Insere com filtro), que o Afinador já propõe; a entrega em lote simula o conjunto pelas faces do perfil.
- **Copiar uma Doca pronta.** O perfil do Tablet deve levar os destinos aprendidos e fixados, para a segunda Doca nascer configurada.
- **A Doca guardar os próprios padrões** (ser ela o provedor, como nó da rede, com espaços de padrão por tier) elimina os provedores em volta: um bloco e um canal para dezenas de padrões. É o que mais agrada a esse jogador, mas pede a Doca como nó (a mesma exigência do pedido de fabricação, feature 5) e uma segunda implementação inteira para o RS2. Proposta: os provedores encostados na primeira versão, a Doca com padrões próprios na etapa em que ela vira nó.

### A confirmar para esta parte

- Quantos espaços de padrão têm o Pattern Provider do AE2 e os provedores estendidos dos addons, e se esses addons empurram pela mesma capability (`ICraftingMachine`) que o provedor do AE2. Se não empurrarem, a Doca não os enxerga.
- Se o padrão de processamento do AE2 dá para casar com uma receita do registro do jogo de forma confiável (ele guarda entradas e saídas, não a receita).

## Terceira crítica: simplificar (10/11/2026). Vale esta versão da Doca

Pedido do dono: tirar os termos e as condições complicadas e simplificar o uso. As duas críticas anteriores resolveram os casos do min-max empilhando mecanismos: quatro formas de endereçar, uma ordem de precedência entre elas, um modo "Auto", opções por grupo, três formas de ligar. Para usar, o jogador teria de aprender tudo isso. A versão abaixo troca tudo por **uma regra**.

### A regra

**Cada lado da Doca cuida de um tipo de máquina.**

### Como se usa (três passos)

1. Ponha a Doca e encoste um Pattern Provider (ou Autocrafter) num lado dela.
2. Com o Vinculador, clique nesse lado da Doca e dê Ctrl + clique numa máquina. Todas as máquinas iguais por perto entram naquele lado.
3. Ponha os padrões dessas máquinas no provedor desse lado.

Para outro tipo de máquina, repita em outro lado. Acabou: não há mais nada para configurar.

### O que a Doca faz sozinha

- **Reparte o pedido por igual entre as máquinas livres**, sempre em receitas inteiras. Um pedido grande ocupa as 30 de uma vez.
- **Põe cada ingrediente na entrada certa** da máquina (o que o Afinador já sabe fazer).
- **Recolhe o resultado e devolve** ao provedor do mesmo lado.
- **Espera** quando todas as máquinas estão cheias, sem perder nada.

### A tela

Seis linhas, uma por lado, e no topo o total da Doca ("30/32": máquinas vinculadas e o máximo do tier). Cada linha mostra o ícone da máquina, quantas estão vinculadas naquele lado, quantas estão trabalhando e, se o lado está parado, o motivo em uma frase ("todas cheias", "nenhuma máquina vinculada", "sem provedor neste lado"). Nenhuma opção para marcar.

### O que saiu da proposta da Doca

| Saiu | Por quê | O que o jogador faz no lugar |
| --- | --- | --- |
| Aprender pelo tipo de receita | Regra invisível, com casos em que a Doca "pergunta"; difícil de explicar e de confiar | Põe o padrão no provedor do lado certo |
| Lista de padrões, marcar vários, fixar padrão em grupo | Uma tela inteira só para endereçar | Idem: o lado é o endereço |
| Carimbar padrão numa máquina | Mais um gesto para decorar | Vincula só aquela máquina a um lado |
| Ordem de precedência (fixado, aprendido, face) | Só existia porque havia três mecanismos | Não há mais o que ordenar |
| Modo "Auto" | Errava em silêncio | Não existe: lado sem máquina não aceita pedido |
| Opção "só máquina vazia" | Uma condição a mais por grupo | A Doca só entrega receitas inteiras; se isso não bastar em algum mod, volta como ajuste depois |
| Grupos feitos à mão | Conceito novo | Outro lado, ou outra Doca |
| Ligar pela rede do mod (outra base, outra dimensão) | Segunda forma de fazer a mesma coisa | Fica para uma etapa posterior, se fizer falta |

### Palavras

Na tela, no guia e nas mensagens: "lado", "máquinas", "pedido", "receita inteira". Ficam só neste documento, para quem implementa: lote, lote divisível, unidade da receita, grupo, perfil, nó.

### Limites, ditos de forma simples

- **Seis tipos de máquina por Doca**, um por lado. Mais que isso, outra Doca.
- **Máquinas:** um máximo por tier para a Doca inteira, somando os seis lados (seção "O alcance da Doca"), mostrado no topo da tela como "30/32".
- **Padrões por lado:** os que cabem no provedor encostado. Para mais padrões do mesmo tipo de máquina, um provedor maior de addon, se ele empurrar do mesmo jeito que o do AE2 (a confirmar).
- **Receitas com item e fluido juntos** continuam na segunda etapa.

### O que não muda por dentro

A entrega repartida em receitas inteiras, a máquina cheia que dorme, a contagem no orçamento de 1 ms e os dois pontos de API a confirmar (o retorno pelo provedor e se os provedores de addon empurram do mesmo jeito). O ponto "casar padrão com receita do jogo" deixa de ser necessário.

A Doca que guarda os próprios padrões e o pedido de fabricação pelo estoque continuam como etapas posteriores, e só entram se couberem na mesma regra (um lado, um tipo de máquina).

## O alcance da Doca (10/11/2026, em idealização com o dono)

Uma regra, dois números por tier, nada para configurar.

### A regra

**A Doca alcança as máquinas dentro de um cubo em volta dela. O tamanho do cubo e o máximo de máquinas vêm do tier da Doca.**

É a mesma forma do roteador de alcance (distância contada no maior dos três eixos), então quem já usa o Vinculador não aprende nada novo. O dono chegou a tirar o limite de máquinas e voltou com ele no mesmo dia (10/11/2026): **o limite fica.**

### Números

**A tabela do roteador de alcance (`RangeTiers`), um degrau à frente.** O dono fixou a tabela base (10/11/2026) e pediu para repensar a Doca como um bloco que nasce com alcance, porque é para isso que ela existe. No roteador, o alcance é um extra que só começa no Avançado; na Doca, ele é a função, então a Doca Básica já usa a primeira linha da tabela, e cada tier usa a linha do tier seguinte.

| Tier da Doca | Usa a linha do roteador | Raio (blocos) | Máquinas (a Doca inteira) |
| --- | --- | --- | --- |
| Básico | Avançado | 6 | 8 |
| Avançado | Elite | 12 | 16 |
| Elite | Esmeralda | 16 | 32 |
| Esmeralda | Allthemodium | 24 | 48 |
| Allthemodium | Vibranium | 32 | 64 |
| Vibranium | Unobtainium | 48 | 96 |
| Unobtainium | Ultimate | 64 | 128 |
| Ultimate | (não há linha seguinte) | 96 | 256 |

- **Nenhum número novo, menos um.** A Doca lê os valores da config do roteador, um degrau acima: quem ajusta a tabela do roteador no modpack ajusta a Doca junto. Os únicos valores próprios são os do Ultimate, que não tem linha seguinte. O dono liberou o raio maior nele e aprovou os valores (10/11/2026): 96, o passo seguinte da progressão da tabela (32, 48, 64, 96), e o dobro de máquinas, 256. Como a config do roteador aceita no máximo 64 de raio (`RangeTiers.MAX_RADIUS`), o Ultimate da Doca ganha a sua própria chave de config, com teto próprio.
- **Por que o raio maior custa pouco:** a entrega não depende da distância, e a Doca não carrega área nenhuma. O que cresce é só o Ctrl + clique, que num raio de 96 olha uns 170 chunks (contra uns 80 no raio 64) para ver quais estão carregados, uma vez por clique e com o teto de leitura que já existe. Medir esse clique antes de fechar; nada disso roda por tick.
- **Sem os tiers do Allthemodium** a escada é Básico, Avançado, Elite, Esmeralda e Ultimate: 8, 16, 32, 48 e 256 máquinas.

### O que "nasce com alcance" muda no resto

- **Funciona ao sair da bancada.** A Doca Básica já vincula 8 máquinas a 6 blocos, o tamanho de uma primeira linha de fornalhas ao lado do sistema. Não há Doca que não faz nada.
- **Não existe Upgrade de Alcance para ela**, nem slot para ele. Um item a menos para explicar.
- **O alcance entra no custo da receita:** a Doca Básica deve custar mais que um roteador Básico, na faixa do roteador Avançado com o upgrade. A receita fica para a spec.
- **Ao colocar a Doca, o cubo do alcance aparece por alguns segundos**, só no cliente de quem colocou, para o jogador ver de cara até onde ela chega. Depois, só com o Vinculador na mão.
- **Subir o tier da Doca é subir o alcance.** A dica do Cartão de Upgrade sobre a Doca diz o que ela ganha: "raio 6 → 12, máquinas 8 → 16".

### O que o jogador vê

- **Segurando o Vinculador com a Doca escolhida**, o cubo do alcance aparece desenhado no mundo e as máquinas já vinculadas ficam acesas, cada lado da Doca com a sua cor.
- **No topo da tela**, "30/32": as máquinas vinculadas na Doca inteira e o máximo do tier. Cada linha de lado mostra só quantas são dele.
- **Máquina fora do cubo, ou além do máximo,** não vincula; a mensagem diz o motivo, como o Vinculador já faz.

### Casos de borda, em uma frase cada

- **A distância não muda a velocidade.** Perto ou no limite do cubo, a entrega é a mesma.
- **Máquina em área descarregada fica de fora** até a área carregar; a Doca nunca carrega área nenhuma (a regra do mod para máquinas remotas). A linha do lado diz "12 em área descarregada".
- **Uma máquina pertence a um lado de uma Doca só.** Tentar vincular a mesma máquina a outra Doca é recusado, com o nome de quem a tem.
- **A Doca baixou de alcance ou de máximo** (config do servidor mudou): as máquinas que ficaram de fora continuam vinculadas, mas param de receber, e a linha avisa. Nada é desvinculado sozinho.
- **Mesma dimensão.** Outra dimensão e distâncias além do cubo ficam para a ligação pela rede do mod, numa etapa posterior.

### Decisões do dono (aprovadas em 10/11/2026)

1. **O limite de máquinas é da Doca inteira**, somando os seis lados (não por lado).
2. **O Ultimate tem 96 de raio e 256 máquinas**, com chave de config própria.
3. **O Ctrl + clique do Vinculador pega todas as máquinas iguais do cubo**, como já faz no roteador de alcance (da mais perto para a mais longe, até o máximo); um clique simples tira as que sobraram.

## Arte e animação da Doca (aprovadas pelo dono em 10/11/2026)

Feitas no Blockbench pelo MCP, em nove versões criticadas por GIFs. O que o dono escolheu no caminho: a forma de esqueleto com o giroscópio no meio (e não uma caixa de porcelana com medidor), os materiais da identidade do mod sobre essa forma, nada de destaque nos cantos do bloco, o circuito de luz à maneira do ME Controller, painéis com janela, e o circuito **na cor do tier** (o arco-íris da referência foi testado e descartado).

Arquivos, em `docs/preview/`: `doca-de-padroes.bbmodel` (formato genérico, 193 caixas, atlas 64 × 64 embutido, as duas animações), `doca-de-padroes-textura.png`, `doca-de-padroes-trabalhando.gif` e `doca-de-padroes-repouso.gif`. As coordenadas do modelo têm o bloco centrado em x e z; no jogo, somar 8.

### O modelo

| Parte | O que é | No jogo |
| --- | --- | --- |
| Esqueleto | 12 vigas de **porcelana** de 2 × 2 nas arestas do cubo, cantos lisos; uma linha coral por dentro das vigas de cima | Modelo de bloco, estático |
| Painel (um por lado) | Placa escura recuada 0,6 dentro das vigas, com os traços do circuito pintados em volta da janela (um anel com ramais para as vigas e para a janela) | Modelo de bloco; os traços são a parte animada da textura |
| Vigia (uma por lado) | Abertura de 6 × 6 com os cantos chanfrados, moldura saliente de grafite claro, quatro parafusos de latão, lábio interno recuado, duas dobradiças e um trinco de latão | Modelo de bloco, estático |
| Contatos | Quatro pastilhas de latão por lado em volta da moldura (onde o provedor encosta); a de cima leva a etiqueta na cor do lado | Modelo de bloco; o fechar das garras, se entrar, é do renderer |
| Giroscópio | Três anéis quadrados (latão, porcelana e grafite claro, um em cada eixo) e o cristal na cor da lente do tier | Renderer |
| Pacote e fragmentos | Uma caixa de latão com fita de porcelana e quatro fragmentos do cristal | Renderer, só no laço de trabalho |

### As animações

Dois laços de 6 segundos, pensados para rodar o tempo todo. A emenda foi conferida em número (diferença zero entre o fim e o começo de cada peça).

- **Trabalhando:** os três anéis giram em velocidade constante (duas voltas, uma e uma por laço, em sentidos alternados) e o de latão balança 10°. A cada laço um pacote entra pela vigia do lado ativo e vai até o centro (0,2 a 1,2 s), o cristal o absorve e incha (1,05 a 1,45 s), solta quatro fragmentos que entram em órbita (1,4 a 2,3 s), os fragmentos saem e somem (2,3 a 2,8 s), voltam em espiral (3,3 a 4,4 s) e o pacote retorna à vigia (4,6 a 5,8 s).
- **Repouso:** o mesmo giroscópio aberto, girando à metade da velocidade, e o cristal respirando. Sem pacote nem fragmentos. (Os anéis deitados um dentro do outro foram descartados: liam como o modelo duplicado.)
- **O circuito:** o desenho é fixo; o brilho de cada traço oscila entre a sombra, a lente e o brilho da cor do tier, numa onda que cruza a face na diagonal a cada 3 segundos (duas voltas por laço).

### Como levar ao jogo

- **Circuito:** textura animada (`.mcmeta`), uma por tier, gerada pelo script de texturas. É como o AE2 faz no ME Controller (decomposição da referência: desenho fixo, só a cor muda) e custa zero. Um renderer ou um shader só entram se o dono quiser a cor reagindo ao trabalho.
- **Giroscópio, pacote e fragmentos:** block entity renderer por fórmula a partir do `gameTime`, como o Ressonador e o Reparador (skill `wa-shaders`). Umas 15 peças móveis por Doca; limite de distância e medição de quadro antes de fechar.
- **Parte estática:** converter para `java_block` e passar pelo `scripts/textures/bbmodel_json.py`.

### Pendências da arte

- **A passagem entre repouso e trabalhando** não está animada (só muda a velocidade e aparecem o pacote e os fragmentos).
- **A janela de 6 × 6 mostra só o centro do giroscópio.** Abrir para 8 × 8 mostra mais e deixa menos painel para o circuito; fica para o dono decidir vendo no jogo.
- **Sem vidro na vigia.** O vidro translúcido escondia o giroscópio no Blockbench; avaliar no jogo com o tipo de render de vidro.
- **193 caixas** é muito para um bloco; ver o que dá para fundir na conversão.
- **As cores das etiquetas dos lados** são provisórias (tiradas da paleta de recursos).
- **Só o lado leste tem a entrada do pacote animada** na demonstração; no jogo é o lado que estiver entregando.
- **Casco de porcelana (decisão do dono, 10/11/2026):** a primeira versão aprovada tinha as vigas de grafite e o dono achou que fugia da paleta do mod ("parece de outro mod"). As vigas passaram a porcelana e o estilo novo ficou só no miolo: é a regra dos blocos que trabalham, registrada em `docs/identidade-visual.md`. O anel de porcelana do giroscópio perdeu um pouco de destaque contra o casco.

## O que proponho não fazer

- **Levar canais ME ou cabos RS pelo ar** (o roteador como ponte entre duas partes da rede). Concorre com a Ponte Quântica e com o transmissor do RS, obriga a manter conexões de rede entre chunks e dimensões, e foge do que o mod faz, que é mover recurso.
- **Energia:** já funciona pelas capabilities; não há ganho que justifique código.
- **Químicos e Source dentro do ME ou do RS** nesta rodada. Dependem de um terceiro mod (Applied Mekanistics, Ars Énergistique, Refined Storage Mekanism Integration, Refined Types), cujo código não foi lido. Fica para depois dos itens e fluidos.

## Ordem sugerida

| Etapa | Features | Tamanho | Por quê nessa ordem |
| --- | --- | --- | --- |
| 1 | 1 (armazenamentos visíveis) | Pequena a média | Maior ganho pelo menor risco; monta a base do `compat` e as duas runs de GameTest |
| 2 | 2 (atalho do roteador) | Média | Reusa a ponte da etapa 1 no sentido contrário |
| 3 | 4 (entrega em lote) | Média | Só motor, sem os mods; tem valor sozinha e destrava a 3 |
| 4 | 3 (Doca de Padrões) | Grande | A feature de crafting; pede mockup e spec próprios |
| 5 | 5 e 6 (pedido de fabricação, Tela e Tablet) | Grande | A Doca vira nó da rede; só vale depois de a Doca existir |

A feature 7 acompanha cada etapa.

## Riscos

- **API experimental no AE2:** o pacote `appeng.api.behaviors` (as estratégias de armazenamento externo) é marcado como experimental e pode mudar em qualquer versão. A proposta usa só `ME_STORAGE`, `ICraftingMachine` e os serviços de rede, que ficam fora dele.
- **Versões:** o AE2 pede NeoForge 21.1.169 ou mais novo, abaixo do mínimo do mod (21.1.248), então não força subir o `neo_version`. As versões que o ATM10 traz não foram achadas.
- **Duas APIs diferentes para a mesma feature:** cada feature custa quase o dobro para cobrir os dois mods. Dá para entregar o AE2 primeiro em cada etapa e o RS2 em seguida.
- **Protocolo e mundo:** a Doca é bloco novo com tela, logo payload novo e `ModPayloads.VERSION`.

## A confirmar antes de qualquer plano

1. As versões do AE2 e do RS2 no ATM10 (o manifesto do pack).
2. Se a Interface do AE2 expõe `ME_STORAGE` a um vizinho comum, e em que condições.
3. Por onde o resultado de uma máquina volta ao Pattern Provider do AE2 (qual capability o provedor expõe), para garantir que o caminho de volta pelas redes do mod fecha o pedido.
4. Os valores de `ExternalPatternSink.Result` no RS2 e como o Autocrafter trata a recusa.
5. Os identificadores Maven dos módulos de API do RS2 (`com.refinedmods.refinedstorage`, repositório `maven.creeperhost.net`; o do AE2 é `org.appliedenergistics:appliedenergistics2:<versão>:api`, no Maven Central).
6. Como o Storage Bus do AE2 e o External Storage do RS2 tratam quantidades acima de `int` quando leem um `IItemHandler` (mede o ganho real da feature 1).

## Perguntas para o dono

1. A Doca de Padrões é o caminho, ou prefere algo mais simples para o crafting (só os atalhos e o lote, sem bloco novo)?
2. AE2 e RS2 juntos em cada etapa, ou o AE2 primeiro?
3. A entrega em lote entra como modo de face ("Lote", ao lado de Extrai, Insere e Armazém) ou como opção do filtro?
4. O pedido de fabricação pelo estoque interessa, sabendo que no AE2 ele obriga a Doca a ser nó da rede, com cabo e energia?
