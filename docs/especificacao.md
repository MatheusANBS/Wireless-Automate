# Wireless Automate — Especificação

Versão de 7 de outubro de 2026 · Matheus Araújo

## Visão geral

Wireless Automate é um mod de transporte wireless de itens, fluidos, energia e químicos para NeoForge 1.21.1, feito para o ATM10. A meta é ser o transporte mais rápido do pack e, ao mesmo tempo, o mais leve em TPS.

- **Plataforma:** Minecraft 1.21.1, NeoForge, Java 21.
- **Público:** modpacks grandes de tecnologia, com ATM10 como referência.
- **Pilar 1, vazão:** o tier Ultimate não tem limite de transferência além do orçamento de TPS.
- **Pilar 2, TPS:** o mod tem um teto fixo de tempo por tick e não deve gastar nada quando não há o que mover.
- **Pilar 3, configuração:** configurar dezenas de nós deve ser rápido, com presets, cópia por área e edição à distância.

## Componentes

O mod tem um bloco e oito itens (Configurador, Tablet, Vinculador, Cartão de filtro, Upgrade de chunk loading e três Cartões de Upgrade), mais o livro-guia quando o GuideME está instalado. Todo o resto é configuração.

| Componente | Tipo | Função |
| --- | --- | --- |
| Roteador Wireless | Bloco direcional | Gruda na face da máquina onde é colocado: em cima, embaixo (de cabeça para baixo) ou de lado. Corpo de 14×6×12 px, duas antenas e quatro LEDs decorativos na frente. |
| Cartões de Upgrade (Avançado, Elite, Ultimate) | Item | Sobem o roteador um tier, sem pular tiers: clique no roteador colocado (sem perder a configuração) ou roteador + cartão na bancada. Não há cartão Básico. |
| Configurador | Item (varinha) | Copia a configuração de um roteador e cola em outro ou em todos os de uma área presos à mesma máquina. |
| Tablet de rede | Item | Gerencia nós, redes e grupos à distância. |
| Vinculador | Item (controle) | Escolhe a rede ativa e coloca roteadores nela, um a um ou por área. |
| Cartão de filtro | Item | Guarda um filtro reutilizável entre roteadores. |
| Upgrade de chunk loading | Item | Mantém carregado o chunk do roteador. |

Sem partículas de item nem animação no bloco: o modelo fica fixo (decisão do dono). O estado aparece na tela do roteador e no Tablet, e o servidor só avisa o cliente quando o estado muda.

## Tiers

Quatro tiers com escala agressiva, no estilo ATM. Os tiers diferem só em vazão, alcance e dimensões; filtros não têm limite de slots em nenhum tier.

| Tier | Itens/s | Fluido e químico/s | Energia | Source/s | Alcance |
| --- | --- | --- | --- | --- | --- |
| Básico | 512 | 32.000 mB | 16.000 FE/t | 1.000 | 128 blocos |
| Avançado | 8.192 | 512.000 mB | 256.000 FE/t | 16.000 | 1.024 blocos |
| Elite | 131.072 | 8.000.000 mB | 4.000.000 FE/t | 256.000 | Dimensão inteira |
| Ultimate | Sem limite | Sem limite | Sem limite | Sem limite | Entre dimensões |

A Source (Ars Nouveau) é opcional: sem filtro e sem cartões, como a energia; a vazão é a chave `sourcePerSecond` da config.

A vazão vale por face e por tipo. Os números ficam no arquivo de config do servidor, para o modpack ajustar. No Ultimate, o único limite é o orçamento de TPS.

## Telas da interface

São quatro telas (Roteador, Filtro, Tablet e Vinculador; o Configurador não tem tela), todas com a mesma hierarquia: o essencial à vista, ajustes secundários recolhidos e um botão principal na cor do tier.

### Roteador

- **Abre com:** clique direito no roteador, ou à distância pelo Tablet.
- **Cabeçalho:** nome do nó, vazão atual da aba selecionada e tier.
- **Abas:** uma por tipo do registro (Itens, Fluidos, Energia e Químicos; Químicos só existe com Mekanism), cada uma com o ícone e a cor do tipo. Cada aba entra numa rede própria, e um ponto na cor da rede em cada aba mostra de relance quando estão em redes diferentes (vazado: sem rede). **Abas adaptáveis:** com espaço, nome inteiro em todas; mais apertado, o nome só na aba ativa; sem espaço, só o ícone (o nome aparece no tooltip).
- **Redimensionável** pela borda direita, a de baixo e a alça do canto, de 300 × 240 até a janela menos 8 px, com o tamanho lembrado na sessão; a largura e a altura extras vão para o visor 3D e a linha das abas, e a coluna da direita e o inventário acompanham a borda. Nomes longos (de rede, de nó) são abreviados e mostram o texto inteiro no tooltip.
- **Rede da aba:** à direita das abas, ocupando a largura que sobra, uma pílula com a rede da aba selecionada, na cor dela. Clicar abre a lista "Rede da aba Itens" (ou da aba atual) com as redes do jogador e "Sem rede"; escolher muda só aquela aba.
- **Visor 3D:** a máquina conectada e o roteador, na posição em que ele foi colocado, com as faces da máquina tocáveis. Botões por face logo abaixo.
- **Face selecionada:** nome e slots que ela acessa (na fornalha: entrada em cima, combustível dos lados, saída embaixo), modo Extrai, Insere, Armazém ou Nenhum, e o filtro resumido em uma linha com Editar. A dica do Armazém: recebe de quem extrai e entrega para quem insere; não troca com outro armazém.
- **Aba sem filtro (Energia):** não mostra Editar; mostra "Sem filtro…" e a vazão do tier.
- **Recolhido:** prioridade e redstone.
- **Cartões e upgrade:** dois slots de Cartão de filtro por face e por tipo, abaixo da face selecionada; o slot do Upgrade de chunk loading fica no cabeçalho (é do roteador, não da face). Os upgrades de tier não usam slot: entram por clique ou na bancada.

### Filtro

- **Abre com:** Editar no roteador, o botão Filtro dos armazenamentos, ou clique direito no ar segurando um Cartão de filtro.
- **Janela redimensionável** pelas bordas e pela alça do canto, em torno do centro (como a do Baú), com o tamanho lembrado na sessão.
- Lista branca ou negra e, para itens, ignorar ou exigir componentes iguais.
- **À esquerda:** as entradas em lista (ícone, nome, tipo, estoque), sem limite e com busca por nome, `#tag` ou `@mod`; embaixo, o inventário do jogador (Shift + clique adiciona o item exato; com o JEI, arrastar para a lista ou Shift + clique na lista dele adiciona mesmo sem ter o item).
- **À direita, quatro abas:**
  - **Entrada:** a selecionada, com o que ela pega (os itens da tag ou do mod), o estoque, Remover, e Ver tags (item) ou Editar (regra).
  - **Tags:** o inspetor (um item no slot, pelo cursor, pelo JEI, por Ver tags ou por Ctrl + clique no inventário, mostra todas as tags dele para marcar, mais o mod) e a busca em todas as tags do jogo, com quantos itens cada uma pega e a prévia dos itens sob o mouse; `@texto` busca mods. Nos químicos, que não têm tags, vira Adicionar: id do químico ou `@mod`.
  - **Regra** (só itens): monta uma regra por propriedade; o inventário acende no que ela pega antes de adicionar.
  - **Mais:** Cartão de Filtro (importar e exportar), componentes e Limpar.

### Tablet de rede

- **Abre com:** clique direito no ar segurando o Tablet, ou tecla de atalho.
- **Lista:** busca, filtro por papel e Selecionar para mover vários nós de rede. Cada nó mostra status e etiquetas de papel (Extrai itens, Insere energia…).
- **Mapa:** vista de cima com cores por status; tocar num ponto abre o nó.
- **Estatísticas:** um cartão por tipo (ícone, cor, vazão, origens e destinos), tempo do mod por tick, destinos cheios e chunks descarregados. Clicar num cartão abre a Lista só com os roteadores daquele tipo; o chip "Só …" na Lista (com ✕) tira o filtro.
- **Redimensionável** como o roteador (mínimo 300 × 240, máximo a janela menos 8 px, tamanho lembrado na sessão); os cartões se redistribuem em colunas conforme a largura.
- **Redes:** cor, membros e privacidade. Nova rede fica recolhida.
- **Grupos:** várias redes de um sistema juntas, com pausar e retomar de uma vez.

### Vinculador

- **Abre com:** clique direito no ar. Shift + clique direito no ar alterna entre Único e Área.
- **Redimensionável** como o roteador (mínimo 300 × 204, máximo a janela menos 8 px, tamanho lembrado na sessão): a largura extra vai toda para a coluna da direita e a altura para o mapa da área e para as linhas da lista de redes.
- Rede ativa: é nela que o Vinculador põe os roteadores (o primeiro vínculo cria uma, se o jogador não tiver). Roteador colocado não entra em rede nenhuma.
- **Abas:** uma caixa por tipo disponível, sem o quadradinho de marcar: marcada = borda e fundo na cor do tipo; o botão **Todos** marca ou desmarca todos de uma vez. A coluna da esquerda tem 144 px, e a lista de redes encolhe quando os tipos pedem mais linhas. Só as abas marcadas mudam. O botão de vincular diz só quantos roteadores ("Vincular 12"); as abas marcadas aparecem nas caixas e no tooltip do botão (decisão do dono em 8/10/2026: a contagem "· N abas" não cabia em português). Pelo menos uma fica marcada (o servidor recusa desmarcar a última). Shift + roda do mouse com o Vinculador na mão percorre os atalhos Todos → Itens → Fluidos → Energia → Químicos (este só com o Mekanism) → Todos; uma combinação marcada na tela vai para Todos nos dois sentidos. A action bar e o tooltip mostram a seleção ("Todos" ou "Itens + Fluidos + Químicos"). Sem o Mekanism, a aba Químicos guardada no item é ignorada (o item continua com ela ao voltar para uma instância com o Mekanism). Itens antigos com o componente `linker_type` valem como aquela aba sozinha; a primeira troca grava `linker_tabs`.
- **Desvincular:** a primeira linha da lista de redes é "Nenhuma (desvincular)" (componente `linker_unlink` no item, sem mexer na rede ativa do jogador). Com ela, os mesmos gestos tiram as abas marcadas da rede em vez de pôr ("Desvinculado: Itens + Fluidos"; na área, "12 roteadores desvinculados (Itens + Fluidos)") e nunca criam rede. Escolher ou criar uma rede sai do modo.
- **Único:** clique direito num roteador põe as abas marcadas na rede ativa (ou as tira, desvinculando).
- **Área:** Shift + clique em dois blocos marca os cantos; a tela mostra uma prévia de cima, quantos roteadores ficam dentro e o botão Vincular (Desvincular no modo desvincular).
- **Proteção:** o clique num roteador passa pela checagem do jogo (proteção do spawn, borda do mundo); na área, cada roteador passa pela mesma checagem e os protegidos ficam de fora (a action bar conta). Vincular exige uma rede ativa que o jogador pode usar; desvincular não usa rede, como o "sem rede" da tela do roteador.

### Configurador

Sem tela: tudo é feito com cliques. O tooltip mostra o estado (cópia, máquina, modo e área, tipo colado) e os comandos do modo atual.

| Gesto | Pincel (padrão) | Área |
| --- | --- | --- |
| Shift + clique direito num roteador | Copia a configuração | Copia a configuração |
| Clique direito num roteador | Cola nele | Marca um canto |
| Clique direito num bloco | — | Marca um canto (1º, 2º; o 3º recomeça) |
| Clique direito no ar | — | Cola em todos os roteadores da área presos à mesma máquina |
| Shift + clique direito num bloco sem roteador | Limpa a varinha (cópia e área) | Limpa a varinha (cópia e área) |
| Shift + clique direito no ar | Troca para Área | Troca para Pincel |
| Shift + roda do mouse | Troca o tipo colado | Troca o tipo colado |

- **Tipo colado:** Todos (padrão, sem componente), Itens, Fluidos, Energia e, só com o Mekanism, Químicos (componente `configurator_type`; a action bar mostra o tipo). Copiar sempre copia tudo; o tipo vale ao colar, no pincel e na área. Em Todos, todas as abas (faces e redes). Num tipo, só as faces e a rede daquela aba, ainda sob a regra das redes; as outras abas do roteador ficam como estavam. Exemplo: copiar um roteador configurado só para fluidos e colar em Fluidos sem mexer nos itens e na energia dos outros.

## Filtros

Cada face da máquina, para cada tipo, tem um filtro embutido sem limite de entradas, mais slots para cartões. O recurso passa se for aceito pelo conjunto.

| Regra | Exemplo | Vale para |
| --- | --- | --- |
| Exato | Lingote de ferro, água | Itens, fluidos, químicos |
| Tag | `#c:ingots`, `#c:ores` | Itens, fluidos |
| Mod | `@mekanism` | Itens, fluidos, químicos |
| Regra por propriedade | Qualquer item encantado | Itens |

- **Adicionar:** Shift + clique num item do inventário, ou clique no JEI mesmo sem ter o item. Arrastar do JEI para a lista também funciona. Tags e mods pelo inspetor ou pela busca, várias de uma vez. Duplicados são ignorados.
- **Regra por propriedade:** pega o item que cumpre **todas** as condições marcadas, cada uma com tanto faz, sim ou não: encantado (inclusive livro), danificado, renomeado, com poção, empilhável e com conteúdo (caixa de shulker, bundle); mais um encantamento com nível mínimo (no item ou no livro), a durabilidade restante (≥ ou < uma porcentagem) e o "só em" (uma tag ou mod: encantado + `#c:armors` = só armadura encantada). Aceita estoque e lista negra como qualquer entrada, e é editável depois.
- **Tamanho:** sem limite na tela, com rolagem. Um teto interno de 4.096 entradas protege o dado salvo e o pacote de rede.
- **Custo:** o filtro é compilado em conjuntos de hash, então conferir um item custa o mesmo com 9 ou com milhares de entradas. As regras por propriedade não cabem num mapa: são perguntadas em ordem, só as que vêm antes da melhor resposta dos mapas, então o custo cresce com o número de regras (poucas, na prática).
- **Modo:** lista branca ou negra, por filtro.
- **Componentes:** ignorar (picareta encantada = picareta) ou exigir iguais.
- **Estoque:** ao inserir, aceitar só até N no destino; ao extrair, manter sempre N na origem.
- **Cartões:** carregam o filtro completo e podem ser duplicados.

## Redes, distribuição e redstone

Não se liga um roteador a outro: cada aba (tipo de recurso) de um roteador entra numa rede, e tudo do mesmo tipo na mesma rede troca entre si. Quem envia e quem recebe vem da configuração das faces da máquina; a ordem de entrega é por prioridade e, empatando, por round-robin.

- **Rede por aba:** os itens, fluidos e energia de um roteador podem ir para redes diferentes. Exemplo: a fornalha da Linha 5x com Itens na rede "Linha 5x" e Energia na "Base", e o gerador com Energia na "Base". Ao colocar, nenhuma aba entra em rede: o jogador configura o primeiro roteador e replica com o Configurador; quem não quer separar nada vincula todas as abas de uma vez (Vinculador em Todos).
- **Faces da máquina:** o roteador acessa a máquina por qualquer face, não só pela que está encostado, porque o NeoForge consulta inventários informando a face. Cada face, por tipo, fica em Extrai, Insere, Armazém ou Nenhum.
- **Armazém:** a face recebe de quem extrai e entrega para quem insere, mas não troca com outra face Armazém (assim os recursos não vão e voltam entre dois baús). Serve para buffers e baús de armazenamento.
- **Entrar numa rede:** o roteador nasce sem rede (o item que traz dados do block entity, como um roteador quebrado e pego de volta, mantém as redes que trouxe). Entra pelo seletor de cada aba na tela do roteador, pelo Vinculador (as abas marcadas na tela dele) ou colando com o Configurador. Sai pelo mesmo seletor ("sem rede") ou pelo Vinculador em "Nenhuma (desvincular)".
- **Em massa:** Vinculador em modo Área, seleção múltipla no Tablet, ou o Configurador copiando a rede junto com o preset.
- **Grupos:** juntam várias redes de um mesmo sistema para ver, pausar e retomar tudo de uma vez.
- **Redstone:** por face e por tipo: ignorar, ativo com sinal ou ativo sem sinal.
- **Chunks:** origem ou destino descarregado pausa a rota sem custo; o upgrade de chunk loading mantém o chunk do roteador carregado.
- **Visualização (planejado):** segurando o Configurador ou o Tablet, linhas de conexão saem de quem extrai para quem insere.

### Exemplo: processamento 5x do Mekanism

Redes divididas por fluxo, não por máquina: cria-se uma rede nova quando o mesmo recurso pode ir para destinos que precisam ficar separados.

| Rede | Tipo | Extrai | Insere |
| --- | --- | --- | --- |
| Minério | Itens | Buffer do minerador | Dissolução (`#c:ores`) |
| Slurries | Químicos | Dissolução e lavadoras | Lavadoras (suja) e cristalizadores (limpa) |
| Intermediários | Itens | Cada máquina da cadeia | A próxima, filtrada por tag; lingotes vão para o armazenamento |
| Utilidades | Fluidos e químicos | Separadores, infusores, bomba | Água, O₂, HCl e ácido sulfúrico para os consumidores |
| Energia | Energia | Geradores | Todas as máquinas |

As cinco redes formam o grupo Linha 5x. Para replicar a linha, copie com o Configurador o roteador de cada estágio e cole numa área que cubra todas as cópias: só os roteadores presos ao mesmo tipo de máquina recebem a configuração.

## Presets e replicação

O Configurador guarda uma cópia só, no próprio item (decisão do dono: sem biblioteca, sem código de texto e sem tela, para ficar simples).

- **Cópia:** faces, filtros, prioridades, redstone e a rede de cada aba, mais o tipo de bloco da máquina do roteador copiado. Copiar de novo substitui a cópia.
- **Tipo colado:** Shift + roda do mouse escolhe Todos ou uma aba só (Itens, Fluidos, Energia, Químicos com o Mekanism); colar num tipo só não toca nas outras abas do destino (faces e rede).
- **Pincel:** Shift + clique copia de um roteador, clique cola em outro (em qualquer máquina).
- **Aplicação relativa:** as faces são salvas em relação à orientação do roteador, então a cópia funciona com o bloco virado para qualquer lado.
- **Colar em área:** no modo Área, cliques em dois blocos marcam a área (contorno no mundo, como no Vinculador) e clique no ar cola em todos os roteadores carregados dela **presos ao mesmo tipo de máquina** do copiado; os outros ficam como estavam e a action bar conta quantos. A área segue os limites do Vinculador (`linker.maxAreaVolume` e `linker.maxDistance`).
- **Redes:** cada aba só leva a rede se o jogador puder usá-la; senão fica com a de antes, com aviso.

## Integrações

A base são as capabilities padrão do NeoForge, que cobrem quase todo mod do ATM10 sem código específico. As integrações diretas existem só para ganhar vazão.

| Alvo | Caminho | Ganho | Dependência |
| --- | --- | --- | --- |
| Qualquer inventário, tanque ou máquina | Capabilities de item, fluido e energia do NeoForge | Compatibilidade geral | Nenhuma |
| Químicos do Mekanism | API de químicos do Mekanism | Suporte a gases e afins | Opcional |
| Source do Ars Nouveau | Capability `ars_nouveau:source` (só em `compat/arsnouveau`) | Aba Source: jarras, relays e máquinas do Ars | Opcional |
| AE2 (planejado) | Armazenamento por chave e quantidade `long` | Uma chamada move milhões de itens | Opcional |
| Refined Storage 2 (planejado) | API de armazenamento por recurso | Mesmo ganho do AE2 | Opcional |
| Sophisticated Storage (planejado) | Handler de itens com cache de slots | Varredura incremental de baús grandes | Nenhuma |
| JEI | Ingredientes fantasmas e a receita de upgrade na bancada | Arrastar e clicar para os filtros | Opcional |
| GuideME | Livro-guia data-driven | Guia no jogo, em inglês e português | Opcional |

Hoje existem as capabilities do NeoForge, os químicos do Mekanism, a Source do Ars Nouveau, o JEI e o GuideME; os atalhos de AE2, RS2 e Sophisticated Storage são planejados (o Sophisticated já funciona pelas capabilities). Sem um mod opcional instalado, a parte correspondente simplesmente não carrega e o resto funciona normal.

## Armazenamento do mod (planejado, 0.2)

Motivação: no teste do dono (8/10/2026), 12 milhões de pedregulhos entre dois barris do Sophisticated Storage no Ultimate pararam em ~274 mil itens/s, com o orçamento de 1 ms cheio. O teto não é do motor: o `IItemHandler` extrai no máximo uma pilha (64) por chamada, então cada 64 itens custam quatro chamadas ao outro mod. Um armazenamento do próprio mod guarda **quantidades `long` por tipo** e conversa com o roteador por uma API interna, sem esse limite.

Decisões do dono (8/10/2026):

- **Cinco blocos separados**, cada um com tiers: Baú (itens), Tanque (fluidos), Bateria (energia), Tanque Químico (só com o Mekanism, pela ponte `Chemicals`) e Tanque de Source (só com o Ars Nouveau, pela ponte `Sources`).
- **Capacidade por tier**, com os mesmos Cartões de Upgrade do roteador (Básico → Avançado → Elite → Ultimate, sem pular e sem perder o conteúdo). O Ultimate não tem limite (satura em `Long.MAX_VALUE`).
- **Tipos ilimitados:** o limite é só a quantidade total do tier.
- **Tela em lista com busca** (estilo terminal do AE2), redimensionável pelas bordas e pela alça do canto, sempre centralizada: grade rolável de tipos com a contagem abreviada (12,6M), busca por nome e `@mod`, ordenação por quantidade, nome ou mod. Clique tira uma pilha, Shift + clique no inventário guarda.
- **O roteador continua sendo colocado na face, como em qualquer máquina.** Ele reconhece o armazenamento do mod e usa o atalho; a configuração por face, as redes e os filtros não mudam.
- **Versão 0.2**, depois do envio da 0.1.1.

Capacidade proposta por tier (ajustável na config do servidor, seção `storage`):

| Tier | Baú (itens) | Tanque e Tanque Químico | Bateria | Tanque de Source |
| --- | --- | --- | --- | --- |
| Básico | 262.144 | 1.000.000 mB | 16.000.000 FE | 160.000 |
| Avançado | 16.777.216 | 64.000.000 mB | 1.000.000.000 FE | 2.560.000 |
| Elite | 1.073.741.824 | 4.000.000.000 mB | 64.000.000.000 FE | 40.960.000 |
| Ultimate | Sem limite | Sem limite | Sem limite | Sem limite |

Como a transferência usa o atalho:

- **Capability própria** (`wirelessautomate:bulk_items` e as equivalentes de fluido, energia e químico), lida pelo mesmo `BlockCapabilityCache` da face. Nada de `instanceof` nem busca por tick. API por chave e quantidade: `insert(chave, long, simular)` e `extract(chave, long, simular)`, mais a lista de chaves com quantidade.
- **Do mod para o mod:** uma operação por tipo de item, qualquer que seja a quantidade. Os 12 milhões viram uma chamada; o limite volta a ser o tier do roteador (ou nenhum, no Ultimate).
- **Do mod para outro mod:** a origem entrega em blocos maiores que uma pilha, e o destino aceita o que a inserção dele aceitar. A sobra volta para o nosso baú.
- **De outro mod para o mod:** limitado pela extração da origem (64 por chamada), mas a inserção do nosso lado é O(1).
- Filtros da face (embutido e cartões), estoque, prioridade, round-robin e sono valem igual: a regra é a mesma do `ItemTransfer`, só a chamada muda.

Compatibilidade: o bloco também expõe as capabilities padrão do NeoForge (`IItemHandler` com um slot virtual por tipo mais um vazio, extração de no máximo uma pilha; `IFluidHandler`, `IEnergyStorage`), então funis, AE2, RS e outros mods o veem como um inventário comum. O atalho é só para o nosso roteador.

Também no desenho:

- **Filtro de entrada no bloco**, reaproveitando `FilterSet`, a tela de filtro e o Cartão de Filtro: o que pode entrar, por qualquer caminho.
- **Sem tick:** o block entity só guarda dados e avisa o `NetworkManager` quando o conteúdo muda (acorda as origens e os destinos presos a ele). A tela recebe diferenças e só enquanto está aberta.

Decididos pelo dono depois (8/10/2026):

- **Quebrar o bloco:** o conteúdo fica no servidor (um `SavedData` com um id); o item leva só o id e um resumo (tipos e total). Não estoura o limite de pacote. O item é uma referência: duplicá-lo (criativo, dupe) não duplica o conteúdo, os dois apontam para o mesmo.
- **Capacidades:** a tabela acima é o padrão, ajustável na config.
- **Receitas:** vanilla, só do tier Básico de cada bloco; os tiers seguintes vêm dos Cartões de Upgrade, como no roteador.
- **Texturas:** prontas em `scripts/textures/gerar_texturas.py` (ver "Armazenamento do mod" no pacote de design).
- **Tanque, Bateria e Tanque Químico prontos (8/10/2026):** no mesmo molde do Baú. As APIs de fluido e energia do NeoForge são em `int` (até ~2,1 bilhões por chamada, e o motor faz uma por tipo por tick), então o Tanque e a Bateria têm capabilities próprias em `long` (`BulkFluids`, `BulkEnergy`) que o roteador usa quando a origem ou o destino é do mod; a de químico do Mekanism já é em `long`. O Tanque troca baldes no bloco e pela tela; a Bateria tem uma tela própria com a carga e a variação por tick, e não tem filtro.
- **Tanque de Source pronto (8/10/2026, etapa 2):** guarda Source em `long` na base `ScalarStorageBlockEntity` (a mesma da Bateria, com o `ScalarStore`). Capacidade por tier de 160.000, 2.560.000, 40.960.000 e sem limite (config `sourceTankCapacity`). Sem filtro nem lista: a tela (`StorageScalarScreen`) tem a barra roxa, a vazão em Source/s e uma linha de dica. O bloco mostra o nível na coluna de vidro (propriedade `fill`, 0 a 10, forma fina como a Source Jar). Para o roteador, a capability `wirelessautomate:bulk_source` (em `long`, entre dois tanques tudo passa de uma vez). Para o Ars, `ArsStorage` dá a `ISourceCap` (roteador e Relays) e registra o provider no `SourceManager`: Sourcelinks a 5 blocos depositam e as máquinas do Ars tiram, como de uma Source Jar; a visão em `int` fica limitada a `Integer.MAX_VALUE`. Receita vanilla `IGI/GTG/IGI` com ferro, gema de Source e um Tanque Wireless, só com o Ars. Sempre registrado; sem o Ars some da aba criativa e do JEI, e o tooltip diz que precisa do Ars.
- **Cores dos tipos:** Químicos `#97C853` (o verde do Tanque Químico) e Source `#B36DE0` (o roxo do Ars). Na barra do Tanque de Source: corpo `#9749C2`, sombra `#6B2F8F`, brilho `#EA8EF3`.
- **Baú pronto (8/10/2026):** tudo o que está acima para o Baú, mais o filtro de entrada indo junto no item quebrado e o upgrade na bancada (a mesma receita do roteador). Na tela: clique pega uma pilha, botão direito meia, Shift + clique manda para o inventário; com item no cursor, clicar na lista guarda (botão direito, um). A tela recebe só as diferenças por tipo, no máximo a cada 5 ticks, e só aberta.

## Arquitetura de performance

Regra de ouro: uma operação que não move nada não pode custar nada. O custo vem de verificar, filtrar e tentar inserir em destinos cheios, então a arquitetura evita cada um desses passos.

Ciclo do gerenciador a cada tick: pega a próxima rota acordada (em ordem de prioridade) → se o orçamento acabou, salva o cursor e segue no próximo tick → senão, move o lote com o filtro já em cache → se o destino recusou, ele dorme até o vizinho avisar uma mudança; se aceitou, segue para a próxima rota.

### Técnicas

1. **Gerenciador central:** os nós não fazem tick. Um único gerenciador por servidor processa todas as redes, com rotas pré-ordenadas por prioridade e reconstruídas só quando a rede muda, e só no tipo de recurso que mudou. Sinal de redstone só remonta se alguma face usa redstone; invalidação de capability e mudança de estado da máquina não remontam.
2. **Orçamento de tempo:** o mod tem um teto de tempo por tick (padrão 1 ms, configurável em `config/wirelessautomate-server.toml`). Se o trabalho não couber, continua no tick seguinte a partir de cursores salvos. O teto se reduz sozinho quando o MSPT do servidor sobe.
3. **Destinos dormindo:** um destino que recusa um recurso entra numa lista negativa por tipo de recurso e é pulado. Ele acorda quando o inventário vizinho avisa uma mudança ao nó, com custo zero enquanto nada muda. Como reserva, há checagens com intervalo crescente, de 1 tick até alguns segundos. As origens também dormem, com o motivo guardado: a vazia acorda quando a própria máquina muda, a que espera destino acorda junto com o primeiro destino que acordar. As entregas do próprio mod não acordam as outras origens, e um destino sem máquina dorme até a máquina aparecer.
4. **Lotes:** em vez de mover pouco a cada tick, move a quantia de vários ticks numa operação só. A vazão é a mesma e o custo fixo cai. A energia é distribuída num único passe, com contas em `long`.
5. **Filtros compilados:** ao salvar, o filtro vira conjuntos de hash, com as tags já expandidas num mapa imutável, refeito só quando as tags recarregam. Cada slot passa pelo filtro uma vez só (passa e estoque juntos), e o destino que atingiu o estoque para a varredura na hora.
6. **Caches de capability:** cada face usa `BlockCapabilityCache`, que guarda a referência ao vizinho e avisa quando ela muda. Nenhuma busca de capability por tick.
7. **Varredura incremental (planejado):** em inventários grandes, um cursor lembra onde parou e um índice guarda os slots com espaço ou com o recurso desejado.
8. **Custo por vizinho (planejado):** o mod mede o tempo de cada destino. Destinos lentos de outros mods passam a ser chamados com menos frequência, sem frear o resto da rede.
9. **Sem trabalho no cliente:** a tela recebe só diferenças, e só enquanto está aberta. Nada é sincronizado com a tela fechada.
10. **Profiler embutido:** `/wa profile` mostra ms/tick por rede, operações, destinos dormindo e acordados.

## Plano de benchmark

O Sophisticated Storage é o banco de testes: baús com centenas de slots e slots com quantidades enormes forçam os dois piores casos ao mesmo tempo. A meta é manter o mod abaixo do orçamento em todos os cenários.

| Cenário | Montagem | O que mede | Meta |
| --- | --- | --- | --- |
| Vazão bruta | 1 baú cheio enviando para 1 baú vazio, tier Ultimate | Itens/s máximos | Maior número possível dentro do orçamento |
| Destino cheio | 50 origens enviando para destinos já cheios | Custo de tentar e falhar | Perto de 0 ms/tick após dormir |
| Muitos tipos | Baú com centenas de itens diferentes e filtros grandes | Custo de filtro e varredura | Abaixo do orçamento |
| Muitos nós | 500 nós ativos numa rede | Escala do gerenciador | Abaixo do orçamento |
| Rede ociosa | 500 nós sem nada para mover | Custo de existir | Perto de 0 ms/tick |
| Misto | Itens, fluidos e energia ao mesmo tempo | Comportamento realista | Abaixo do orçamento |

- **Medição:** profiler embutido mais o Spark, com MSPT antes e depois de colocar a rede.
- **Comparação:** os mesmos cenários com outros mods de transporte do ATM10, para sustentar o "mais eficiente".
- **Regressão (planejado):** um cenário automatizado em GameTest rodará a cada mudança de código para detectar perdas de performance.

## Receitas e progressão

Todas as receitas usam só itens vanilla e ficam em data packs (`data/wirelessautomate/recipe/`), então o modpack pode trocar tudo sem mexer no código. O roteador nasce Básico; os tiers seguintes vêm dos Cartões de Upgrade, um de cada vez.

| Item | Receita |
| --- | --- |
| Roteador (Básico) | Olho de ender em cima, ferro + redstone + ferro no meio, três ferros embaixo |
| Cartão de Upgrade Avançado | Lingotes de ouro nos cantos, diamantes nas bordas, bloco de ouro no centro |
| Cartão de Upgrade Elite | Estrela do Nether em cima, Cartão Avançado no centro, três lingotes de netherita em volta |
| Cartão de Upgrade Ultimate | Ovo do dragão em cima, Cartão Elite no centro, três blocos de netherita e quatro fragmentos de eco |
| Upgrade do roteador na bancada | Roteador + Cartão de Upgrade do tier seguinte, sem forma (também no JEI) |
| Cartão de filtro (2) | Papel, redstone e comparador (`PRP` / `PCP`) |
| Cópia de Cartão de filtro | Cartão configurado + cartão vazio, sem forma = dois iguais |
| Upgrade de chunk loading | Olho de ender e diamante cercados de obsidiana, com duas redstones |
| Configurador | Fragmento de ametista na ponta de dois gravetos, na diagonal |
| Vinculador | Pérola do ender em cima, redstone entre cobres e três lingotes de cobre embaixo |
| Tablet de rede | Lingotes de ferro dos lados; no meio, de cima para baixo, painel de vidro, olho de ender e redstone |
| Livro-guia (só com o GuideME) | Livro + redstone, sem forma |

O balanceamento das receitas (materiais de mods do ATM nos tiers altos) ainda está em aberto.

## Roadmap

O v1 entrega o motor de transferência e a configuração essencial; o v2 completa a experiência.

**v1, motor e essencial**

- [x] Projeto NeoForge 1.21.1 e gerenciador central com orçamento de tempo
- [x] Roteador direcional com itens, fluidos e energia, configurado por face da máquina
- [x] Redes, rede ativa, prioridade, round-robin e redstone
- [x] Filtros sem limite (inventário, JEI, tags, mod, estoque) e cartões
- [x] Filtro v2 (1.0): tela em lista redimensionável, inspetor e busca de tags e regras por propriedade
- [x] Tiers e Cartões de Upgrade
- [x] Configurador como pincel e Vinculador modo Único
- [x] Profiler embutido e benchmark com Sophisticated Storage

**v2, escala e integrações**

- [x] Químicos do Mekanism (aba Químicos com modo, prioridade, redstone, rede e filtro exato ou por mod; sem cartões de filtro)
- [x] Source do Ars Nouveau (aba Source, sem filtro e sem cartões, vazão por tier)
- [x] Tablet: lista, mapa, estatísticas, redes e grupos
- [x] Configurador colando em área (mesma máquina) e Vinculador modo Área
- [ ] Atalhos para AE2 e Refined Storage 2
- [x] Upgrade de chunk loading
- [x] Texturas finais (geradas por `scripts/textures/gerar_texturas.py`)
- [x] Livro-guia no GuideME (opcional)
- [ ] Balanceamento das receitas

**0.2, armazenamento do mod**

- [ ] Baú, Tanque, Bateria e Tanque Químico com tiers, tela em lista e atalho no roteador (ver "Armazenamento do mod")

### Decisões tomadas (7 de outubro de 2026)

- **Canais:** só redes mais filtros no v1. Fluxos separados usam redes diferentes.
- **Receitas dos tiers altos:** só itens vanilla. Avançado: ouro e diamante. Elite: netherita e estrela do Nether. Ultimate: ovo do dragão. O modpack ajusta por datapack ou KubeJS.
- **Orçamento padrão:** 1 ms/tick (era 0,5 ms; o dono trocou por mais vazão em 7/10/2026, depois do benchmark).
- **Cartões no roteador:** slots de cartão por face e por tipo, além do filtro embutido. O recurso passa se o filtro embutido ou algum cartão aceitar.
- **Duplicar cartões:** receita sem forma, cartão configurado + cartão vazio = dois cartões iguais.
- **JEI:** integração opcional já no v1 (dependência só de compilação, plugin carregado só com o JEI).
- **Modo Armazém** (antes "Ambos"): uma face Armazém não entrega para outra face Armazém; extrai para faces que só inserem e recebe de faces que só extraem.
