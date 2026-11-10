# Lixeira (spec)

Estado: **visual e mecânica aprovados pelo dono em 10/11/2026.** O visual, na segunda versão (a forma escolhida entre as quatro primeiras, refeita no mesmo dia nos moldes da Doca de Padrões: casco de porcelana, miolo escuro com luz, laços que emendam). A mecânica, pelas respostas da seção "Respostas do dono"; o mockup da tela também foi aprovado. Nada implementado; o próximo passo é o plano.

## Motivação

Pedido do dono de 14/10/2026 (`docs/progresso/pendencias.md`, Ideias): um bloco do mod que some com itens, fluidos, energia, químicos e Source, para as redes não dependerem do Trash Cans. O uso típico é o destino de transbordo: a face da Lixeira entra na rede com a menor prioridade e recebe o que nenhum outro destino aceitou.

## Decisões

1. **Um bloco só, sem tier.** Some com os cinco recursos pelo mesmo bloco; não há capacidade nem vazão para escalar por tier. Id `wirelessautomate:trash`, nome "Lixeira" / "Trash Bin" (decisão do dono, 10/11/2026).
2. **Aceita tudo o que está ligado, não guarda nada.** Cada capability de um tipo ligado na tela aceita a quantidade inteira pedida e descarta; nada sai (extração sempre vazia, comparador sempre 0).
3. **Cinco capabilities, registradas por bloco** (`event.registerBlock`), com handlers que não guardam recurso (um objeto por tipo para todas as Lixeiras); o provedor só devolve o handler se o tipo está ligado na tela (seção "A tela"). É o padrão que o bench já usa nos sumidouros (`bench/BenchCapabilities`: `ItemVoid`, `FluidVoid`, `EnergyVoid`); as classes da Lixeira são de produção, em pacote próprio (`trash/`), e o bench pode passar a usá-las.
   - Itens: `Capabilities.ItemHandler.BLOCK` e a visão `BulkItems` (o roteador entrega em `long`, sem o limite de uma pilha por chamada).
   - Fluidos: `Capabilities.FluidHandler.BLOCK` e `BulkFluids`.
   - Energia: `Capabilities.EnergyStorage.BLOCK` e `BulkEnergy`.
   - Químicos: só com o Mekanism, registrada em `compat/mekanism` (a regra do `CLAUDE.md`: nenhum tipo do Mekanism fora de lá).
   - Source: só com o Ars Nouveau, em `compat/arsnouveau`, com a `BulkSource`.
4. **Sem filtro próprio** (decisão do dono, 10/11/2026). O filtro que protege de jogar fora a coisa errada é o da face do roteador; o que a Lixeira tem é a chave de liga e desliga por tipo.
5. **Tem tela, com uma tecla por tipo** (decisão do dono, 10/11/2026; seção "A tela"). Sem contador do que foi descartado.
6. **Todos os lados iguais.** Sem `facing`: o modelo é simétrico nas quatro laterais.
7. **A luz é da cor do Ultimate** (decisão do dono, 10/11/2026): como a Lixeira não tem tier, o circuito, o vórtice e o cristal usam a lente do Ultimate (`#7B4FE0`, brilho `#C7B0FA`, sombra `#4A2C92`, de `scripts/textures/identidade.py`), sempre.
8. **A receita fica para o balanceamento das receitas** e **a textura fica como atlas pintado** (decisões do dono, 10/11/2026); a página do guia entra na etapa da mecânica.

## Modelo 3D

Pronto no Blockbench: `docs/preview/lixeira.bbmodel` (formato genérico, 43 caixas em três grupos, atlas 64 × 64 embutido, também em `docs/preview/lixeira-textura.png`). As coordenadas do arquivo têm o bloco centrado em x e z (-8 a 8); no jogo, somar 8.

Segue a regra da identidade para os blocos que trabalham (`docs/identidade-visual.md`): **casco de porcelana, miolo escuro com luz**. Como a Lixeira não tem tier, a luz é a da lente do Ultimate.

| Grupo | Caixas | O que é | No jogo |
| --- | --- | --- | --- |
| `base` | 38 | Piso (y 0 a 4) com a faixa de alerta coral e grafite; quatro paredes (4 a 11) com montantes de porcelana nos cantos e o painel escuro com o circuito de luz; borda de porcelana saliente para dentro (11 a 13) com a linha coral embaixo e um rebite de latão no meio de cada lado; quatro pilares de porcelana de 3 × 3 (13 a 16); em cada parede, uma escotilha de grafite saliente com fenda e quatro parafusos de latão; a placa do vórtice no fundo (y 4 a 5) | Modelo de bloco, estático; o circuito e o vórtice são a parte animada da textura |
| `anel` | 4 | Moldura quadrada de latão de 7 × 7, 1 de altura, em y 14,5 a 15,5; pivô (0, 15, 0) | Block entity renderer |
| `nucleo` | 1 | Cristal na cor da lente do Ultimate: cubo de 2,6 em pé sobre a ponta (rotação 45°, 0°, 35,264°), centro em y 16,5 | Block entity renderer |

Para virar bloco:

- **A base** passa pelo `scripts/textures/bbmodel_json.py`, que espera o formato `java_block`: converter o projeto (ou só o grupo `base`) antes. Todas as caixas da base são alinhadas aos eixos e cabem em 0 a 16 (as escotilhas e os parafusos saem até 0,4 da face: conferir o limite na conversão).
- **A paleta é a do mod** (`scripts/textures/identidade.py`): porcelana, grafite, coral, latão e a lente do Ultimate. A textura fica como atlas pintado, convertido como o do Reparador e o da Tela (decisão do dono, 10/11/2026).
- **O circuito e o vórtice** levam o `neoforge_data` de luz cheia que o conversor já sabe gravar, para brilhar no escuro.
- **Colisão** (decisão do dono, 10/11/2026): segue o desenho, com a caixa cheia de 0 a 13 e os quatro pilares de 13 a 16.
- **O cristal passa do topo do bloco** (chega a y 19 parado e a uns 21 no pico do engolir): o renderer precisa do `getRenderBoundingBox` maior, como os do Reparador, do Ressonador e da Tela.

## Animação

Referência visual: `docs/preview/lixeira-idle.gif` (o repouso, da altura do jogador) e `docs/preview/lixeira-engolir.gif` (engolindo, de cima, com o vórtice à vista). Os keyframes estão no `.bbmodel` (`animation.lixeira.repouso` e `animation.lixeira.engolindo`); o renderer reproduz por fórmula, a partir do `gameTime`, sem arquivo de animação.

São **dois laços de 6 segundos**, feitos para rodar o tempo todo; a emenda foi conferida em número (diferença zero entre o fim e o começo de cada peça).

**Repouso:**

| Parte | Movimento |
| --- | --- |
| Anel | Uma volta em Y por laço; sobe e desce ±0,4 (seno, período de 3 s) |
| Cristal | Duas voltas em Y no sentido contrário; sobe e desce ±0,45, em fase oposta à do anel; pulsa de 0,94 a 1,06 de escala a cada 1,5 s |

**Engolindo:** o dono aprovou que o anel e o cristal sumam para dentro do bloco durante o mergulho ("afinal ele está engolindo"). Nada de onda nem efeito extra por fora.

| Tempo | Anel: altura, escala em X e Z | Cristal: altura, escala |
| --- | --- | --- |
| 0,0 a 1,0 s | 0; 1,0 | 0; 1,0 |
| 1,3 s | +1,5; 1,0 (antecipação) | +1,8; 1,3 |
| 1,9 s | -7,5; 0,6 (mergulho) | -8,0; 0,3 |
| 3,1 s | -7,5; 0,6 | -8,0; 0,3 |
| 3,7 s | +0,8; 1,05 (volta passando do ponto) | +1,0; 1,2 |
| 4,0 a 6,0 s | 0; 1,0 (calmo, para o laço repetir sem cansar) | 0; 1,0 |

Entre 1,0 e 4,0 s o anel dá uma volta a mais e o cristal duas, sobre o giro constante do repouso. A interpolação entre os pontos é suave.

**Textura animada (nos dois laços):**

- **Circuito das paredes:** o desenho é fixo; o brilho de cada traço oscila entre a sombra, a lente e o brilho do Ultimate, numa onda que cruza a face na diagonal a cada 3 segundos. É a mesma técnica da Doca (textura animada `.mcmeta`, custo zero).
- **Vórtice do fundo:** três braços na cor do Ultimate girando, uma volta a cada 3 segundos.

**Quando o engolindo toca** (decisão do dono, 10/11/2026): enquanto a Lixeira estiver descartando alguma coisa, com o laço sempre terminando a volta que começou. Sem descarte, o repouso.

## A tela

Decisão do dono (10/11/2026): a Lixeira é para tudo (itens, fluidos, energia, químicos e Source) e tem uma tela própria com **uma tecla de liga e desliga por tipo**. Mais nada: sem filtro por tipo e sem contador. Mockup aprovado: `docs/preview/lixeira-tela.html` (o layout B, "Teclas"; a lista com chave ficou no arquivo só como a alternativa descartada).

- **Cinco teclas de porcelana lado a lado**, no espírito das teclas de modo das faces do roteador: o crachá de tipo do mod (`ResourceStyle`), o nome curto embaixo e uma barrinha na cor do recurso. **Acesa aceita; apagada, com o contorno tracejado e o crachá cinza, recusa.** O nome inteiro vai na dica da tecla.
- **Químicos e Source** só aparecem com o Mekanism e o Ars Nouveau instalados (`LoadedTypes`); sem eles a tela tem três teclas.
- **Cabeçalho do mod:** a faixa e o Olho na cor do Ultimate, o título e o botão de tema. **Rodapé:** uma linha "Aceitando" com "tudo", "nada" ou "N de M".
- **Sem inventário do jogador:** a tela não tem slots.
- **Ligado, a Lixeira aceita e descarta aquele tipo. Desligado, ela não oferece a capability dele:** para o roteador, o funil ou o cabo, é como se o bloco não aceitasse aquele recurso. Assim uma Lixeira numa rede de itens não drena a energia de quem estiver ligado nela.
- **Nasce com os cinco tipos ligados** (decisão do dono, 10/11/2026): é a "lixeira para tudo"; quem não quer um tipo desliga na tela.
- **Abre com clique direito**, de mão vazia ou não, como os armazenamentos.
- **Estado só por cor e contorno:** como a tecla não tem texto de estado, o apagado precisa ler também para quem não distingue cores. O contorno tracejado e o crachá cinza fazem esse papel; conferir no e2e nos dois temas.

## Custo de performance (lei por lei)

- **Nada de tick por bloco.** A Lixeira não tem ticker no servidor. Os handlers não guardam recurso nenhum.
- **Sem busca de capability por tick.** O roteador chega nela pelo `BlockCapabilityCache`, como em qualquer máquina. Trocar uma chave na tela invalida as capabilities do bloco uma vez (`level.invalidateCapabilities`), e o cache de quem está ligado nela se refaz sozinho.
- **Estado por bloco:** as cinco chaves (cinco bits) num block entity sem ticker, salvas no NBT e levadas no item ao quebrar. O provedor da capability lê o bit do tipo e devolve o handler comum (um objeto por tipo para todas as Lixeiras) ou nada.
- **Nada sincronizado com a tela fechada.** A tela mostra só as cinco chaves, que vão na abertura do menu; cada clique é um pedido pequeno ao servidor, e a resposta só vai para quem está com a tela aberta. O único aviso com a tela fechada é o de entrar e sair do laço engolindo: um evento de bloco (`level.blockEvent`), que o jogo só manda a quem está perto, no máximo um a cada 120 ticks por bloco (a duração do laço), com um campo de "último aviso" no mesmo block entity. O circuito e o vórtice são textura animada e não pedem aviso nenhum.
- **Motor:** um destino que sempre aceita nunca dorme como cheio, então uma Lixeira numa rede recebe todo tick enquanto houver origem com recurso. É o comportamento pedido, mas o guia precisa dizer para pôr a face dela na menor prioridade, senão ela disputa com os destinos de verdade. Medir no bench um cenário com Lixeira como transbordo antes de fechar.
- **Cliente:** o renderer desenha 5 caixas (4 do anel e o cristal), sem alocar por quadro, e só a menos de 64 blocos. Além dessa distância fica só o modelo estático.
- **Protocolo:** a tela traz um menu e um payload novos, logo `ModPayloads.VERSION` sobe.

## Testes

- **GameTests** (lote comum): um roteador com a face numa rede entrega itens, fluidos e energia à Lixeira e a origem esvazia; a Lixeira nunca devolve nada (extração vazia nas três capabilities); com um destino de prioridade maior na mesma rede, ele enche antes de a Lixeira receber; funil em cima entrega itens; com um tipo desligado na tela, a capability dele some e o roteador para de entregar aquele tipo, e volta ao religar; as chaves sobrevivem a quebrar e recolocar o bloco.
- **Runs dos opcionais:** o mesmo para químicos (`runGameTestServerChemicals`) e Source (`runGameTestServerSource`). O `runGameTestServer`, sem o Mekanism e sem o Ars, prova que o bloco carrega sem eles.
- **e2e:** a tela (abrir, desligar e religar um tipo, nos dois temas) e duas capturas do bloco, uma em repouso e uma no meio do engolindo, com o tempo parado, para comparar com os GIFs.

## Etapas

1. **Mecânica, bloco e tela:** bloco, block entity com as cinco chaves, as cinco capabilities, menu, tela e payload, modelo estático da base, traduções nos dois idiomas, loot table, tag de picareta, página do guia, JEI (informação), GameTests nas três runs, e2e da tela, bench do transbordo. A receita entra no balanceamento.
2. **Animação:** o renderer do anel e do cristal (skill `wa-shaders`), o evento do laço engolindo e o e2e das capturas.

## Fora de escopo

Tiers; filtro por tipo; contador do que foi descartado; lixeira como item de mão ou upgrade de outro bloco; efeitos por fora do bloco no engolindo; a Lixeira no Tablet.

## Respostas do dono (10/11/2026) e o que falta

Perguntadas uma a uma no fim da sessão de 10/11:

| Pergunta | Resposta |
| --- | --- |
| Id e nome | `wirelessautomate:trash`, "Lixeira" / "Trash Bin" |
| Quando o engolindo toca | Enquanto a Lixeira estiver descartando; no resto do tempo, o repouso |
| Filtro próprio | Não. No lugar, uma tela com liga e desliga por tipo (a Lixeira é para os cinco recursos) |
| Receita | Fica para o balanceamento das receitas |
| Textura | Atlas pintado, como o Reparador e a Tela de Sinal |
| Cor da luz | A da lente do Ultimate |
| Layout da tela | B, as teclas (mockup `docs/preview/lixeira-tela.html`) |
| Ao colocar o bloco | Os cinco tipos ligados |
| Colisão | Segue o desenho |

Não falta decisão nenhuma do dono: o próximo passo é o plano (skill `wa-feature`).
