# Wireless Automate · identidade visual "Porcelana e Sinal"

Guia da identidade visual nova do mod (10 de outubro de 2026). Substitui o visual "ardósia escura com
acento ciano" do pacote de design original. Vale para os sprites dos itens, os modelos 3D dos blocos,
os ícones da interface e as telas. Quem desenhar qualquer coisa do mod lê isto antes.

## A ideia

O Wireless Automate é **equipamento de instrumentação de sinal**: aparelhos de um fabricante imaginário
de rádio e laboratório dos anos 60 a 80, mas limpos e atuais. Casco de **porcelana esmaltada**
(branco quente), chassi e para-choques de **grafite** (preto quente, fosco), uma única **listra coral**
como assinatura da marca, e uma **lente de vidro** na cor do tier: o **Olho**.

Personalidade em três palavras: **calmo, preciso, caloroso.** Nada de néon escuro, nada de hexágonos,
nada de "tech genérico". Pensa em medidores de bancada, rádios de baquelite, isoladores de porcelana,
cadernos de campo e papel milimetrado.

### O Olho (emblema)

Uma lente redonda com um brilho no canto de cima à esquerda, dentro de um anel de grafite. É o nó da
rede. Aparece:

- no topo de todo bloco (é como se acha um aparelho olhando de cima);
- no centro ou na ponta de todo item;
- na interface como marcador de aba ativa, de tier e de rede.

**O Pulso** é a animação da marca: o anel do Olho clareia e um segundo anel se expande para fora e some
(ondas de sinal). Todo item e todo bloco tem alguma animação; o Pulso é a padrão, e cada item ganha
mais uma própria (varredura de radar, filamento, ponteiro...). Lento e discreto: ciclo de 1,6 a 3,2 s.
Animação pelo `.mcmeta` do Minecraft (quadros empilhados na vertical, `frametime` em ticks).

## Materiais (a paleta)

Tudo sai de `scripts/textures/identidade.py` (`PALETA`). Tons de 4 (luz) a 0 (sombra); a luz vem de
cima à esquerda. Alfa só 0 ou 255, exceto vidro (`translucido=True`).

| Material | Uso | Tons (4 → 0) |
| --- | --- | --- |
| `porcelana` | casco de blocos, corpo dos itens, cartões, fundo das telas | `#FFFBF3 #F2EBDD #DDD3BF #BDB19A #948870` |
| `grafite` | chassi, pés, anéis, contornos, tinta das telas | `#5A5660 #3E3B44 #2A2730 #1B1920 #0F0E12` |
| `coral` | a listra da marca, o destaque único da interface, alertas de ação | `#FFB39E #FF8A6B #F0603E #C2442A #86301E` |
| `latao` | terminais, contatos, parafusos (pouco: 1 a 3 px por sprite) | `#F7E3A1 #DDBC5C #B48E2E #80621C` |
| `vidro` | lentes, visores, coluna dos tanques | `#F6FCFF #CFE6EE #8FB4C2 #5A7E8E` |
| `fluido` | água/fluido no Tanque | `#BFE0FF #5B9DF0 #2F6FD6 #1F4A99` |
| `energia` | Bateria: carga | `#FFF1B3 #FFD042 #D9931A #8F5E0C` |
| `quimico` | Tanque Químico: gás | `#E6F7B8 #A8DB5A #5E9A2B #355B16` |
| `source` | Tanque de Source (roxo do Ars) | `#F0C8FA #D08AF0 #9B4DC6 #6B2F8F` |

### Cor do tier (a lente do Olho)

A mesma lista em `identidade.py` (`LENTE`) e em `GuiPaint.tierColor`. Funciona sobre porcelana (as
telas são claras), então as cores são saturadas e um pouco mais escuras que no visual antigo:

| Tier | Lente | Brilho | Sombra |
| --- | --- | --- | --- |
| basic | `#6E7480` | `#B9BEC8` | `#454A54` |
| advanced | `#D59A1E` | `#F7D77A` | `#8E6410` |
| elite | `#1FA9A0` | `#8EE8E1` | `#136C66` |
| emerald | `#22A84E` | `#8BEBA6` | `#146A30` |
| allthemodium | `#E8740A` | `#FFC27A` | `#9A4A05` |
| vibranium | `#18B57A` | `#86F0C4` | `#0E6E4A` |
| unobtainium | `#B23FD0` | `#E9A6F5` | `#6F2384` |
| ultimate | `#7B4FE0` | `#C7B0FA` | `#4A2C92` |

Além da cor, o tier aparece como **marcas** (1 a 4 traços de grafite) para quem não distingue cores.

### Cor dos recursos e dos modos

Recursos (`RECURSO`, também em `ResourceStyle.color`): itens `#B57A3A`, fluidos `#2F6FD6`,
energia `#D9931A`, químicos `#5E9A2B`, source `#8E4FC9`. Modos de porta (`MODO`, também em
`GuiPaint.modeColor`): extrai `#2F6FD6`, insere `#E8742B`, armazém `#2F9D5C`, nenhum `#948870`.
Estados: ok `#2F9D5C`, atenção `#D88A1A`, erro `#D5453A`, pausado `#6B6FD0`.

## Os itens (16×16, `item/generated`, todos animados)

Cada item é um aparelho reconhecível, não um ícone genérico. Contorno de 1 px em `grafite.0`
nos itens. Famílias por silhueta: **cartões** (Filtro), **válvulas** (upgrades de tier),
**aparelhos de mão** (Configurador, Vinculador, Tablet), **papel** (Guia), **lanterna** (chunk loading).

| Item | Aparelho | Animação própria | Quadros |
| --- | --- | --- | --- |
| Configurador | caneta-sonda de porcelana na diagonal, pegada de grafite, clipe coral, ponta com o Olho | a ponta pisca (Pulso) | 4 |
| Vinculador | transceptor de mão: corpo de porcelana, antena telescópica de grafite com bolinha, listra coral, medidor de 3 barras | as barras do medidor sobem e descem | 6 |
| Tablet de Rede | prancheta de porcelana com tela arredondada de vidro, moldura de grafite, Olho embaixo | varredura de radar na tela (um raio gira, com rastro) | 8 |
| Cartão de Filtro | cartão perfurado de porcelana com canto cortado, furos de grafite em linhas, listra coral | uma faixa de luz corre pelos furos | 6 |
| Cartões de Upgrade (7 tiers) | válvula (tubo de vácuo): bulbo de vidro, filamento na cor do tier, base de grafite com 1 a 4 marcas e pinos de latão (ATM: pinos na cor do tier) | o filamento pulsa e um brilho sobe no bulbo | 8 |
| Upgrade de Chunk Loading | Lanterna de Vigia: lanterna de mão de porcelana com gaiola de grafite, anel coral na tampa, alça em cima e uma chama verde (`chunk`) que nunca apaga ("fica acesa enquanto você está longe") | a chama tremula (muda de forma e altura, com brilho no miolo e halo no vidro) | 6 |
| Guia | caderno de campo: capa coral com o Olho em porcelana, lombada espiral de grafite | o Olho da capa pulsa | 4 |

Mesma quantidade de detalhe em todos: nada de item "simples" ao lado de outro cheio de detalhes.

## Os blocos (modelos 3D com elementos, nada de cubo liso)

Todos os blocos têm: **pés ou para-choques de grafite** nos cantos, casco de porcelana, **uma listra
coral** horizontal e o **Olho no topo** (lente do tier, com o Pulso animado na textura). Ambient
occlusion desligado nos modelos com elementos finos. Blocos que não preenchem o cubo precisam de
`noOcclusion()` no registro (visual, sem lógica).

Construção limpa, conferida por `blocos.validar_geometria` em todo modelo gerado: os elementos
**encostam, não se atravessam** (só o fluido, o gás e a Source ficam dentro da caixa de vidro, marcados
com `dentro=True`); duas faces desenhadas nunca dividem o mesmo plano com área em comum (é o que pisca
no jogo); e toda face não desenhada está inteiramente coberta por um vizinho encostado (senão é buraco).
Pés ficam debaixo do corpo, sem a face de cima; uma caixa de vidro só por coluna (duas se atravessando
piscam); nada de caixas a 45° cruzando o corpo. As galerias do jogo (`WA_SCREENSHOT_ONLY=giro` e
`=blocos` no `DevScreenshot`) mostram os modelos reais renderizados pelo jogo.

| Bloco | Forma |
| --- | --- |
| Roteador | prato de porcelana na base (y 0..1), quatro para-choques de grafite nos cantos, corpo baixo de grafite recuado (y 1..5) com a listra coral e a fenda do Olho que varre na frente (sul), na própria textura; mastro de 2×2 e a **antena parabólica**: um prato sólido de 6×6×1 que, em pé e virado para o sul, cai 45° para trás (rotação −45° em x pela aresta de baixo e de trás, no topo do mastro), com o interior na cor do tier olhando para a frente e para cima e as costas de grafite; um braço de 1 px sai do centro do prato até o receptor de latão (1 px). Duas hastes finas (1×1) de grafite sobre os para-choques de trás, até y=12, com a ponta de latão, dentro das caixas de colisão de `RouterShapes.UP_BOXES`. Mesma convenção de `facing` e `spin`; silhueta dentro de 1..15 × 0..16 × 2..14 |
| Baú Wireless | arquivo de gavetas: pés debaixo do corpo, corpo de porcelana recuado 1 px, duas gavetas salientes por lado (a face de dentro contra o corpo) com puxador de grafite e etiqueta na cor do tier, tampa cheia com o Olho |
| Tanque Wireless | uma coluna de vidro (uma caixa só) entre base e tampa de porcelana com anéis de grafite, fluido azul dentro com a superfície ondulando (animada), quatro réguas de nível encostadas no vidro |
| Bateria Wireless | três células empilhadas com separadores de grafite rentes, plinto e cornija de grafite, dois terminais de latão no topo, um visor de carga saliente por lado, encostado nas células, com segmentos que correm (animado), listra coral na tampa |
| Tanque Químico Wireless | vaso de pressão por caixas empilhadas e encostadas (sem rotações): anel, ombro com a faixa de perigo grafite e coral, equador com a vigia de vidro (o gás verde gira por dentro, animado), ombro de cima, calota com o Olho e um volante vazado de quatro barras de grafite pelo qual o Olho aparece |
| Tanque de Source Wireless | mantém a jarra e os 11 níveis de `fill`: para-choques nos cantos, base e tampa de porcelana, quatro trilhos de grafite nos cantos de fora da coluna de vidro (uma caixa só), Source roxa dentro ondulando (animada), colar de grafite e uma gema pequena (3×3×2) no topo, pulsando |

As quatro laterais de um armazenamento são iguais (o roteador se prende em qualquer face).

## A interface

Telas **claras**: porcelana com tinta de grafite, como um manual de instrumento. Uma cor de ação
(coral), a cor do tier só no Olho do cabeçalho e numa listra fina, e as cores dos recursos nos
ícones, abas e números.

- **Painel:** fundo `porcelana.3` (`#F2EBDD`), contorno de 1 px `grafite.2`, sombra deslocada de
  2 px para baixo e para a direita em `porcelana.1`; cabeçalho com uma listra de 3 px na cor do tier
  e o Olho ao lado do título.
- **Áreas afundadas** (listas, slots, visor): `porcelana.2` com linha de sombra `porcelana.1` em cima
  e à esquerda. Slot 18×18 com o mesmo recuo.
- **Botões:** fundo `porcelana.4`, borda `grafite.2`; com o mouse em cima a borda vira coral;
  selecionado: fundo `grafite.2` com texto `porcelana.4`.
- **Abas:** etiquetas de arquivo. A ativa tem fundo porcelana claro, texto de grafite e um sublinhado
  coral de 2 px; as outras só o texto em `grafite.4`.
- **Texto:** `grafite.1` (`#2A2730`) sem sombra; secundário `#7A7366`; desativado `#B5AC9D`.
- **Visor 3D do roteador:** papel milimetrado (fundo `porcelana.4`, linhas `porcelana.2` a cada
  8 px de tela, uma linha mais forte a cada 4) em vez do gradiente escuro.
- **Portas (modo de face):** teclas quadradas de porcelana, no espírito de crachá: quadrado 14×14 com
  os cantos de 1 px cortados, fundo `porcelana.4`, borda `grafite.2`, luz branca em cima e à esquerda e
  sombra de tecla `porcelana.1` embaixo e à direita; no centro, uma seta grossa (haste de 3 px, cabeça
  de 7 px) na cor do modo, com 1 px de sombra e contorno de grafite. Extrai: seta para cima; Insere:
  para baixo; Armazém: seta dupla, haste curta; Nenhum: tecla apagada (`porcelana.2`, borda tracejada
  `grafite.4` e um traço horizontal no lugar da seta). Apagadas (alpha 0,5) continuam legíveis.
- **Ícones de tipo** (9×9): crachás de linha de metrô. Um disco cheio na cor do recurso (`RECURSO`,
  com a sombra ×0,75 embaixo à direita e um pixel de brilho) com contorno `grafite.1` e um símbolo
  branco de traço grosso, 5×5 no máximo: saco amarrado (Itens), gota (Fluidos), raio (Energia),
  frasco erlenmeyer (Químicos), estrela de 4 pontas com ponto (Source). Nada de objetos miúdos: têm de
  ler em 1× sobre `porcelana.3` e `porcelana.4`.
- **Pílulas de rede e de tier:** contorno na cor, fundo porcelana, bolinha (Olho) na cor.
- **Barras** (orçamento, carga): trilho `porcelana.2`, preenchimento na cor do estado, sem brilho.
- A alça de redimensionar e as bordas acesas usam coral.

Só o visual muda: posições, tamanhos, widgets, payloads e lógica ficam como estão.
