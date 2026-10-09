# Giro do roteador e encantamento por nome: design

Aprovado pelo dono em 9/10/2026 a partir do mockup "Giro do roteador e encantamento" (artifact).

## 1. Giro do roteador

**O que o jogador faz.** Shift + clique direito com as **duas mãos vazias** num roteador gira o roteador 90° em torno do eixo da face onde ele está preso, no sentido horário de quem olha a face de frente (de fora, olhando para a máquina). O quarto clique volta ao começo. Vale no chão, no teto e nas quatro paredes. O clique normal continua abrindo a tela; antes, Shift + clique com a mão vazia também abria a tela, e agora gira.

**Ao colocar** nada muda: o roteador nasce com `spin=0`, a posição de hoje. Roteadores de mundos existentes ficam em `spin=0` (o valor padrão do estado), que é exatamente o visual de hoje.

**Girar é só visual.** A configuração de cada face **absoluta** da máquina (modo, prioridade, redstone e filtro por tipo, os dois cartões por face e tipo) continua a mesma depois do giro. Como ela é salva por `RelativeSide`, o roteador remapeia os lados relativos por dentro na hora do giro. A rede de cada aba, o upgrade de chunk loading e o tier não mudam.

**Detalhes do gesto.**
- Só gira se o jogador pode mexer no bloco (`player.mayBuild()` e `level.mayInteract(player, pos)`); senão não faz nada (e não abre a tela).
- Toca um som curto de girar no servidor (`SoundEvents.ITEM_FRAME_ROTATE_ITEM`, categoria `BLOCKS`).
- Avisa o `NetworkManager` (`nodeChanged`) e marca o block entity como alterado, para a tela aberta e as rotas verem a mudança.

**Estado.**
- Propriedade nova `spin`, `IntegerProperty` 0 a 3, padrão 0, no `RouterBlock`. Nome no blockstate: `spin`.
- O upgrade de tier mantém o `spin` (troca só o `TIER` do estado).
- Item do roteador, Configurador e Vinculador não carregam o giro. O preset do Configurador continua relativo ao roteador, como hoje.
- `rotate(Rotation)` (estruturas) gira o `facing` como hoje e escolhe o `spin` que leva o lado `TOP` para a direção girada, para que o roteador inteiro gire junto com a estrutura. `mirror` usa a mesma regra (o espelho não preserva a lateralidade; manter o `TOP` basta).

**Convenção de `RelativeSide`.** A conversão passa a ser por `facing` + `spin`: primeiro o `spin` gira as direções do caso `facing=up` em torno de Y no sentido horário visto de cima (`Direction.getClockWise()`: norte → leste → sul → oeste), `spin` vezes; depois aplica a rotação do `facing` que já existe (x, depois y). Com `spin=0` o resultado é o de hoje. As sobrecargas sem `spin` saem, para o compilador mostrar todo lugar que precisa passar o giro.

**Visual.** O blockstate do roteador passa a ter `facing × spin × tier` = 6 × 4 × 8 = 192 variantes. Os ângulos `x`/`y` do blockstate não fazem os 4 giros nas paredes, então cada tier ganha três modelos filhos, `router_<tier>_spin1..3`, com o modelo do tier como pai e a transformação raiz do NeoForge (`"transform": {"origin": "center", "rotation": {"y": -90 × spin}}`, sinal a confirmar na captura) que gira a geometria em torno de Y antes da rotação do blockstate. Tudo gerado por `scripts/textures/gerar_texturas.py`. Se a transformação raiz não funcionar (quads ou cullface errados), o script gera a geometria girada no próprio modelo. O modelo de item não muda.

**Colisão.** `RouterShapes` gira as caixas do caso `up` primeiro pelo `spin` (em torno de Y, mesma convenção) e depois pelo `facing`.

**Fica de fora:** chave inglesa de outros mods (`c:tools/wrench`) e escolher o giro pela direção do olhar ao colocar.

## 2. Encantamento por nome e nível até 255

Na aba Regra da tela de filtro, com "Encantamento" marcado:

**Campo do encantamento** (no lugar das setas `<` `>`):
- Um `EditBox` que mostra o nome traduzido do encantamento escolhido.
- Ao ganhar o foco ou ao digitar, abre por cima das linhas de baixo uma lista com até **5 sugestões** (ícone de livro encantado, nome traduzido e o id em cinza). A busca olha o nome traduzido e o id (`fortune`, `minecraft:fortune`, `apotheosis:...`), sem diferença de acentos nem de maiúsculas. Ordem: primeiro quem começa com o texto (no nome, no caminho do id ou no id inteiro), depois quem só contém; dentro de cada grupo, a ordem alfabética do nome. Texto vazio (ou igual ao escolhido) lista todos.
- Com mais de 5, a roda do mouse sobre a lista rola e uma linha "+N" diz quantos sobram.
- Escolher: clique numa sugestão, ou ↑ ↓ e Enter/Tab. Esc ou clicar fora fecha a lista e volta o texto para o último escolhido.
- Texto que não corresponde a um encantamento escolhido: borda vermelha e Adicionar/Salvar desligado.
- A lista vem do registro de encantamentos do jogo (todos os mods).

**Campo do nível** (no lugar do botão `≥ V`):
- `EditBox` só com dígitos, até 3, com "≥" antes. Vale de **1 a 255** para qualquer encantamento (sem o teto do nível máximo dele). O modelo (`ItemRule.Enchant.MAX_LEVEL = 255`) e os codecs não mudam.
- Roda do mouse sobre o campo: +1/−1 (Shift: ±10), limitado a 1..255. Vazio ou fora da faixa: borda vermelha e Adicionar desligado.

**Rótulos.** No resumo da regra e na lista de entradas, o nível sai em romanos até X (`enchantment.level.N` do jogo) e em algarismos daí em diante (`Eficiência ≥ 37`).

**Fica de fora:** mais de um encantamento por regra e nível máximo ("até N").

## Protocolo e dados

- Nenhum payload novo; `ModPayloads.VERSION` não muda (a regra já viaja com o nível em `VAR_INT`).
- NBT do roteador não muda (as faces continuam por `RelativeSide`); o `spin` vive no blockstate.
