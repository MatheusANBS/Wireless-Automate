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

O mod tem um bloco e sete itens. Todo o resto é configuração.

| Componente | Tipo | Função |
| --- | --- | --- |
| Roteador Wireless | Bloco direcional | Gruda na face da máquina onde é colocado: em cima, embaixo (de cabeça para baixo) ou de lado. Corpo de 14×6×12 px, duas antenas e quatro LEDs: energia, rede, atividade e destino cheio. |
| Núcleo de tier | Item | Clique no roteador para subir de tier no lugar, sem perder a configuração. |
| Configurador | Item (varinha) | Copia e cola configurações, aplica presets e trabalha por área. |
| Tablet de rede | Item | Gerencia nós, redes e grupos à distância. |
| Vinculador | Item (controle) | Escolhe a rede ativa e coloca roteadores nela, um a um ou por área. |
| Cartão de filtro | Item | Guarda um filtro reutilizável entre roteadores. |
| Upgrade de chunk loading | Item | Mantém carregado o chunk do roteador. |

Sem partículas de item: a atividade aparece só nos LEDs e nas antenas, e o servidor só avisa o cliente quando o estado muda.

## Tiers

Quatro tiers com escala agressiva, no estilo ATM. Os tiers diferem só em vazão, alcance e dimensões; filtros não têm limite de slots em nenhum tier.

| Tier | Itens/s | Fluido e químico/s | Energia | Alcance |
| --- | --- | --- | --- | --- |
| Básico | 512 | 32.000 mB | 16.000 FE/t | 128 blocos |
| Avançado | 8.192 | 512.000 mB | 256.000 FE/t | 1.024 blocos |
| Elite | 131.072 | 8.000.000 mB | 4.000.000 FE/t | Dimensão inteira |
| Ultimate | Sem limite | Sem limite | Sem limite | Entre dimensões |

A vazão vale por face e por tipo. Os números ficam no arquivo de config do servidor, para o modpack ajustar. No Ultimate, o único limite é o orçamento de TPS.

## Telas da interface

São cinco telas, todas com a mesma hierarquia: o essencial à vista, ajustes secundários recolhidos e um botão principal na cor do tier.

### Roteador

- **Abre com:** clique direito no roteador, ou à distância pelo Tablet.
- **Cabeçalho:** nome do nó, seletor de rede e tier. As abas Itens, Fluidos, Energia e Químicos mostram a vazão atual na mesma linha. Químicos só existe com Mekanism.
- **Visor 3D:** a máquina conectada e o roteador, na posição em que ele foi colocado, com as faces da máquina tocáveis. Botões por face logo abaixo.
- **Face selecionada:** nome e slots que ela acessa (na fornalha: entrada em cima, combustível dos lados, saída embaixo), modo Extrai, Insere, Ambos ou Nenhum, e o filtro resumido em uma linha com Editar.
- **Recolhido:** prioridade e redstone.
- **Rodapé:** slots de cartões de filtro e de upgrade.

### Filtro

- **Abre com:** Editar no roteador, ou clique direito no ar segurando um Cartão de filtro.
- Lista branca ou negra e, para itens, ignorar ou exigir componentes iguais.
- Grade de entradas sem limite, com rolagem. Tocar numa entrada mostra a quantidade de estoque e Remover.
- Abas Inventário e JEI: Shift + clique num item do inventário adiciona; clique no JEI adiciona mesmo sem ter o item.
- **Recolhido:** regras por tag (`#c:ores`) ou mod (`@mekanism`).

### Tablet de rede

- **Abre com:** clique direito no ar segurando o Tablet, ou tecla de atalho.
- **Lista:** busca, filtro por papel e Selecionar para mover vários nós de rede. Cada nó mostra status e etiquetas de papel (Extrai itens, Insere energia…).
- **Mapa:** vista de cima com cores por status; tocar num ponto abre o nó.
- **Estatísticas:** vazão por tipo, tempo do mod por tick, destinos cheios e chunks descarregados.
- **Redes:** cor, membros e privacidade. Nova rede fica recolhida.
- **Grupos:** várias redes de um sistema juntas, com pausar e retomar de uma vez.

### Vinculador

- **Abre com:** clique direito no ar. Shift + clique direito no ar alterna entre Único e Área.
- Rede ativa: todo roteador colocado já entra nela.
- **Único:** clique direito num roteador o coloca na rede ativa.
- **Área:** Shift + clique em dois blocos marca os cantos; a tela mostra uma prévia de cima, quantos roteadores ficam dentro e o botão Vincular.

### Configurador

- **Abre com:** Shift + clique direito no ar.
- **Biblioteca:** presets, Aplicar, Exportar código. Salvar e importar ficam recolhidos.
- **Área:** Copiar área e Aplicar em área (ver Presets e replicação).

## Filtros

Cada face da máquina, para cada tipo, tem um filtro embutido sem limite de entradas, mais slots para cartões. O recurso passa se for aceito pelo conjunto.

| Regra | Exemplo | Vale para |
| --- | --- | --- |
| Exato | Lingote de ferro, água | Itens, fluidos, químicos |
| Tag | `#c:ingots`, `#c:ores` | Itens, fluidos |
| Mod | `@mekanism` | Itens, fluidos, químicos |

- **Adicionar:** Shift + clique num item do inventário, ou clique no JEI mesmo sem ter o item. Arrastar do JEI para um slot também funciona. Duplicados são ignorados.
- **Tamanho:** sem limite na tela, com rolagem. Um teto interno de 4.096 entradas protege o dado salvo e o pacote de rede.
- **Custo:** o filtro é compilado em conjuntos de hash, então conferir um item custa o mesmo com 9 ou com milhares de entradas.
- **Modo:** lista branca ou negra, por filtro.
- **Componentes:** ignorar (picareta encantada = picareta) ou exigir iguais.
- **Estoque:** ao inserir, aceitar só até N no destino; ao extrair, manter sempre N na origem.
- **Cartões:** carregam o filtro completo e podem ser duplicados.

## Redes, distribuição e redstone

Não se liga um roteador a outro: cada roteador entra numa rede, e tudo na mesma rede troca entre si. Quem envia e quem recebe vem da configuração das faces da máquina; a ordem de entrega é por prioridade e, empatando, por round-robin.

- **Faces da máquina:** o roteador acessa a máquina por qualquer face, não só pela que está encostado, porque o NeoForge consulta inventários informando a face. Cada face, por tipo, fica em Extrai, Insere, Ambos ou Nenhum.
- **Entrar numa rede:** automático ao colocar (rede ativa), pelo seletor na tela do roteador ou pelo Vinculador.
- **Em massa:** Vinculador em modo Área, seleção múltipla no Tablet, ou o Configurador copiando a rede junto com o preset.
- **Grupos:** juntam várias redes de um mesmo sistema para ver, pausar e retomar tudo de uma vez.
- **Redstone:** por face e por tipo: ignorar, ativo com sinal ou ativo sem sinal.
- **Chunks:** origem ou destino descarregado pausa a rota sem custo; o upgrade de chunk loading mantém o chunk do roteador carregado.
- **Visualização:** segurando o Configurador ou o Tablet, linhas de conexão saem de quem extrai para quem insere.

### Exemplo: processamento 5x do Mekanism

Redes divididas por fluxo, não por máquina: cria-se uma rede nova quando o mesmo recurso pode ir para destinos que precisam ficar separados.

| Rede | Tipo | Extrai | Insere |
| --- | --- | --- | --- |
| Minério | Itens | Buffer do minerador | Dissolução (`#c:ores`) |
| Slurries | Químicos | Dissolução e lavadoras | Lavadoras (suja) e cristalizadores (limpa) |
| Intermediários | Itens | Cada máquina da cadeia | A próxima, filtrada por tag; lingotes vão para o armazenamento |
| Utilidades | Fluidos e químicos | Separadores, infusores, bomba | Água, O₂, HCl e ácido sulfúrico para os consumidores |
| Energia | Energia | Geradores | Todas as máquinas |

As cinco redes formam o grupo Linha 5x. Um preset por estágio, aplicado com o Configurador, configura cada cópia da linha.

## Presets e replicação

Os presets ficam numa biblioteca por jogador; o Configurador aplica um a um ou por área.

- **Biblioteca por jogador:** salva no mundo; cada preset guarda faces, filtros, prioridades, redstone e rede.
- **Pincel:** Shift + clique copia de um roteador, clique cola em outro.
- **Aplicação relativa:** as faces são salvas em relação à orientação do roteador, então um preset funciona com o bloco virado para qualquer lado.
- **Copiar área:** Shift + clique em dois blocos marca uma área; o Configurador copia todos os roteadores dela, cada um com a sua configuração e posição relativa. Clicar no início de uma linha clonada mostra quais posições têm roteador e cola em todas de uma vez.
- **Aplicar em área:** um único preset vai para todos os roteadores da área, com a opção de limitar a um tipo de máquina.
- **Código de texto:** exporta um preset (prefixo `WA1:`) para colar no chat ou em outro mundo. Itens de mods ausentes são ignorados na importação, com aviso.

## Integrações

A base são as capabilities padrão do NeoForge, que cobrem quase todo mod do ATM10 sem código específico. As integrações diretas existem só para ganhar vazão.

| Alvo | Caminho | Ganho | Dependência |
| --- | --- | --- | --- |
| Qualquer inventário, tanque ou máquina | Capabilities de item, fluido e energia do NeoForge | Compatibilidade geral | Nenhuma |
| Químicos do Mekanism | API de químicos do Mekanism | Suporte a gases e afins | Opcional |
| AE2 | Armazenamento por chave e quantidade `long` | Uma chamada move milhões de itens | Opcional |
| Refined Storage 2 | API de armazenamento por recurso | Mesmo ganho do AE2 | Opcional |
| Sophisticated Storage | Handler de itens com cache de slots | Varredura incremental de baús grandes | Nenhuma |
| JEI | Ingredientes fantasmas | Arrastar e clicar para os filtros | Opcional |

Sem um mod opcional instalado, a parte correspondente simplesmente não carrega e o resto funciona normal.

## Arquitetura de performance

Regra de ouro: uma operação que não move nada não pode custar nada. O custo vem de verificar, filtrar e tentar inserir em destinos cheios, então a arquitetura evita cada um desses passos.

Ciclo do gerenciador a cada tick: pega a próxima rota acordada (em ordem de prioridade) → se o orçamento acabou, salva o cursor e segue no próximo tick → senão, move o lote com o filtro já em cache → se o destino recusou, ele dorme até o vizinho avisar uma mudança; se aceitou, segue para a próxima rota.

### Técnicas

1. **Gerenciador central:** os nós não fazem tick. Um único gerenciador por servidor processa todas as redes, com rotas pré-ordenadas por prioridade e reconstruídas só quando a rede muda.
2. **Orçamento de tempo:** o mod tem um teto de tempo por tick (padrão 0,5 ms, configurável). Se o trabalho não couber, continua no tick seguinte a partir de cursores salvos. O teto se reduz sozinho quando o MSPT do servidor sobe.
3. **Destinos dormindo:** um destino que recusa um recurso entra numa lista negativa por tipo de recurso e é pulado. Ele acorda quando o inventário vizinho avisa uma mudança ao nó, com custo zero enquanto nada muda. Como reserva, há checagens com intervalo crescente, de 1 tick até alguns segundos.
4. **Lotes:** em vez de mover pouco a cada tick, move a quantia de vários ticks numa operação só. A vazão é a mesma e o custo fixo cai. A energia é distribuída num único passe, com contas em `long`.
5. **Filtros compilados:** ao salvar, o filtro vira conjuntos de hash. A resposta "este recurso passa?" fica em cache por tipo e só é refeita quando o filtro muda ou as tags recarregam.
6. **Caches de capability:** cada face usa `BlockCapabilityCache`, que guarda a referência ao vizinho e avisa quando ela muda. Nenhuma busca de capability por tick.
7. **Varredura incremental:** em inventários grandes, um cursor lembra onde parou e um índice guarda os slots com espaço ou com o recurso desejado.
8. **Custo por vizinho:** o mod mede o tempo de cada destino. Destinos lentos de outros mods passam a ser chamados com menos frequência, sem frear o resto da rede.
9. **Sem trabalho no cliente:** a tela recebe só diferenças, e só enquanto está aberta. Nada é sincronizado com a tela fechada.
10. **Profiler embutido:** `/wa profile` mostra ms/tick por rede, operações, destinos dormindo e acordados.

## Plano de benchmark

O Sophisticated Storage é o banco de testes: baús com centenas de slots e slots com quantidades enormes forçam os dois piores casos ao mesmo tempo. A meta é manter o mod abaixo do orçamento em todos os cenários.

| Cenário | Montagem | O que mede | Meta |
| --- | --- | --- | --- |
| Vazão bruta | 1 baú cheio enviando para 1 baú vazio, tier Ultimate | Itens/s máximos | Maior número possível dentro de 0,5 ms/tick |
| Destino cheio | 50 origens enviando para destinos já cheios | Custo de tentar e falhar | Perto de 0 ms/tick após dormir |
| Muitos tipos | Baú com centenas de itens diferentes e filtros grandes | Custo de filtro e varredura | Abaixo do orçamento |
| Muitos nós | 500 nós ativos numa rede | Escala do gerenciador | Abaixo do orçamento |
| Rede ociosa | 500 nós sem nada para mover | Custo de existir | Perto de 0 ms/tick |
| Misto | Itens, fluidos e energia ao mesmo tempo | Comportamento realista | Abaixo do orçamento |

- **Medição:** profiler embutido mais o Spark, com MSPT antes e depois de colocar a rede.
- **Comparação:** os mesmos cenários com outros mods de transporte do ATM10, para sustentar o "mais eficiente".
- **Regressão:** um cenário automatizado em GameTest roda a cada mudança de código para detectar perdas de performance.

## Receitas e progressão

O Básico usa só itens vanilla. Os tiers altos pedem materiais de mods do ATM, com receita alternativa quando o mod não está instalado.

| Tier | Ideia de receita | Alternativa sem o mod |
| --- | --- | --- |
| Básico | Ferro, redstone, olho de ender | — |
| Avançado | Ouro, diamante, componente básico de mod tech | Bloco de diamante |
| Elite | Netherita e liga avançada do Mekanism | Netherita e estrela do Nether |
| Ultimate | Material de endgame do ATM | Ovo do dragão ou equivalente caro |

As receitas ficam em data packs, então o modpack pode trocar tudo sem mexer no código. Os materiais exatos serão definidos no balanceamento.

## Roadmap

O v1 entrega o motor de transferência e a configuração essencial; o v2 completa a experiência.

**v1, motor e essencial**

- [ ] Projeto NeoForge 1.21.1 e gerenciador central com orçamento de tempo
- [ ] Roteador direcional com itens, fluidos e energia, configurado por face da máquina
- [ ] Redes, rede ativa, prioridade, round-robin e redstone
- [ ] Filtros sem limite (inventário, JEI, tags, mod, estoque) e cartões
- [ ] Tiers e núcleos de upgrade
- [ ] Configurador como pincel e Vinculador modo Único
- [ ] Profiler embutido e benchmark com Sophisticated Storage

**v2, escala e integrações**

- [ ] Químicos do Mekanism
- [ ] Tablet: lista, mapa, estatísticas, redes e grupos
- [ ] Biblioteca de presets e código de texto
- [ ] Configurador por área (copiar e aplicar) e Vinculador modo Área
- [ ] Atalhos para AE2 e Refined Storage 2
- [ ] Upgrade de chunk loading
- [ ] Texturas finais no Blockbench e balanceamento das receitas

### Decisões tomadas (7 de outubro de 2026)

- **Canais:** só redes mais filtros no v1. Fluxos separados usam redes diferentes.
- **Receitas dos tiers altos:** só itens vanilla. Avançado: ouro e diamante. Elite: netherita e estrela do Nether. Ultimate: ovo do dragão. O modpack ajusta por datapack ou KubeJS.
- **Orçamento padrão:** 0,5 ms/tick.
- **Cartões no roteador:** slots de cartão por face e por tipo, além do filtro embutido. O recurso passa se o filtro embutido ou algum cartão aceitar.
- **Duplicar cartões:** receita sem forma, cartão configurado + cartão vazio = dois cartões iguais.
- **JEI:** integração opcional já no v1 (dependência só de compilação, plugin carregado só com o JEI).
- **Modo Ambos:** uma face Ambos não entrega para outra face Ambos; extrai para faces que só inserem e recebe de faces que só extraem.
