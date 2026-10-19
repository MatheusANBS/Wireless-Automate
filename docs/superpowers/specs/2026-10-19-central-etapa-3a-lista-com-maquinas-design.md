# Central da rede, etapa 3a: a Lista com as máquinas

Data: 19/10/2026. Ramo: `central-etapa-3a`, a partir do `main` com a etapa 2. Mockup aprovado: `docs/preview/central-etapa-3.html` (com as telas da versão 6, `docs/preview/central-da-rede.html`, seções Lista e Mapa).

**Decisões do dono:**
- máquinas salvas no índice, compactas;
- vazão só nas linhas à vista;
- abertos e recolhidos lembrados no servidor;
- duas entregas, 3a e 3b.

A 3b terá a visão por máquinas, selecionar e colar em máquinas e as abas reorganizadas.

## O que entrega

- As máquinas vinculadas entram no índice de nós, salvas, compactas. O filtro por tipo passa a contar as máquinas vinculadas.
- Na Lista do Tablet, o roteador abre em árvore: a máquina presa, os grupos e, dentro de um grupo aberto, as máquinas.
  - Até 8 máquinas por grupo e "Mostrar todas".
  - Cada linha de máquina tem o nome, as coordenadas e o estado em palavras, a vazão, a marca "exceção" e o Olho ◉.
  - Clicar escolhe a máquina como alvo do painel.
- O limite de 256 da página passa a contar linhas.
- O Tablet lembra, ao reabrir, os roteadores abertos, os grupos recolhidos e os grupos com "Mostrar todas".
- O Mapa mostra as máquinas do roteador escolhido, ligadas a ele por uma linha, com o contorno ciano nas acesas; clicar numa máquina a escolhe no painel.

## Custo (skill `wa-performance`)

O motor não muda.

| Parte | Servidor | Rede | Memória e save |
| --- | --- | --- | --- |
| Máquinas no índice | Lidas só no aviso de mudança do roteador, que já atualiza o índice | 0 | ~12 B por máquina. O save regrava só as entradas que mudaram (o cache da R3). Teto de 512 por roteador |
| Árvore | Montada com a página, só para os roteadores abertos de quem olha | Só as linhas à vista e só quando mudam | 0 |
| Estado e vazão por linha de máquina | Só das linhas à vista, uma vez por amostra (20 ticks): a diferença do total entregue e recebido das portas da máquina, e o sono delas | ~6 B por linha no pacote pequeno | Um `long[]` por linha à vista na amostra |
| Filtro por tipo | Papéis e modos com os grupos e as exceções, no mesmo aviso | 0 | 0 |
| Lembrar | Junto do estado do Tablet por jogador | Uma vez na abertura | Alguns bytes por jogador |
| Mapa | 0: as posições vêm no painel | 0 | 0 |

## As máquinas no índice (servidor)

- **`NodeIndex.Entry`** ganha a lista das máquinas vinculadas do roteador:
  - por máquina: a posição (`long`), o id do grupo, o rótulo (vazio sem rótulo) e se tem exceção;
  - por grupo: o id, o bloco, o nome e quantas máquinas tem.
  - Tudo é lido do `RouterBlockEntity` no `Entry.read`, junto do resto, e comparado para saber se mudou.
- **NBT compacto,** chaves novas só quando há máquinas:
  - `remote_pos` (`LongArrayTag`) e `remote_group` (`IntArrayTag`);
  - `remote_exception` (`IntArrayTag` com os índices das exceções);
  - `remote_labels` (`CompoundTag` índice → rótulo, só com rótulo);
  - `remote_groups` (`ListTag` com `id`, `block` e `name`).
  - O formato antigo, sem as chaves, lê sem máquinas.
- **Teto:** 512 máquinas por roteador no índice; acima disso, guarda as 512 primeiras e a contagem total.
- **O conserto do filtro por tipo:** `roles` e `faceModes` passam a juntar a máquina presa, os perfis dos grupos e as exceções. O modo de um lado conta se alguma das máquinas o tem.
- **`dataVersion`** sobe quando as máquinas ou os grupos mudam, como qualquer dado salvo.

## A página com as máquinas (servidor e protocolo)

- **Estado da árvore** de cada Tablet aberto (no `TabletMenu`, por uma parte de pacote nova):
  - os roteadores abertos (`Set<NodeKey>`);
  - os grupos recolhidos (`Set<GroupKey>`, com `GroupKey(NodeKey node, int group)`);
  - os grupos com "Mostrar todas" (`Set<GroupKey>`).
  
  A presa e os grupos de um roteador aberto começam abertos.
- **Payload novo cliente → servidor:** `TabletTreePayload(int containerId, Action action, NodeKey node, int group)`, com `Action { OPEN, CLOSE, COLLAPSE_GROUP, EXPAND_GROUP, SHOW_ALL, SHOW_FEW }`.
  - O servidor valida a visibilidade do nó e o tamanho dos conjuntos (até 64 abertos e 256 grupos por jogador).
  - Cada ação refaz só a página desse jogador.
- **A página passa a ser uma lista de linhas na ordem da tela:**
  - o nó, como hoje;
  - logo abaixo, só se o nó está aberto: a linha da presa, as linhas dos grupos e as linhas das máquinas dos grupos abertos (até 8, ou todas com "Mostrar todas"), e uma linha "mais N" quando corta.
- **O limite de 256 conta linhas.** Os nós que não cabem continuam contados em `matchingNodes`, como hoje.
- **As linhas de máquina vêm do índice.** Funcionam com o roteador descarregado; nesse caso o estado é "descarregada", sem vazão.
- **O "live" da página** (estado e vazão) segue a ordem das linhas. O estado de uma linha de máquina é:
  - movendo, se alguma porta dela entregou ou recebeu desde a amostra anterior;
  - entrada cheia, se um destino dela está cheio;
  - dormindo;
  - descarregada, sem o chunk;
  - fora do raio, acima do limite ou trocada, pela mesma regra do `RouterSnapshot.state`.
  
  A vazão é por tipo, a diferença de (`gaveRead` + `gave`) e de (`gotRead` + `got`) das portas da máquina entre duas amostras. É medida só para as linhas de máquina da página de alguém (compartilhada pelo `TabletSample`, como a dos nós).
- **Protocolo:** `ModPayloads.VERSION` passa de `"23"` para `"24"`.

## A árvore na Lista (cliente)

- **O nó** continua como hoje, com os seis quadradinhos e a vazão, mais o ▸/▾ na frente quando tem máquinas vinculadas.
- **A presa:** "Máquina presa · <bloco> · <lado> do roteador".
- **O grupo:** "<nome> ×N", e "N exceções" só quando há; ▸/▾ recolhe.
- **A máquina:** na primeira linha, o nome (o rótulo, ou "#N"); na segunda, as coordenadas e o estado em palavras. À direita, a vazão (↓ recebe, ↑ dá) na cor do tipo, a marca "exceção" quando é uma e o Olho ◉, o mesmo da tela do roteador (`MachineHighlights`).
- **"… mais N · Mostrar todas"** no fim de um grupo cortado.
- **Clicar** numa linha de máquina escolhe o nó e põe a máquina como alvo do painel. Na presa, põe a presa; no grupo, põe o grupo.
- **O teclado** segue as linhas (↑ ↓), e ← → abrem e fecham o nó ou o grupo.
- **Selecionar** continua marcando nós. As máquinas entram na 3b.

## O Mapa (cliente)

O roteador escolhido mostra as máquinas dele, das posições do `RangeView` do painel, que já vem: quadradinhos ligados ao roteador por uma linha. As máquinas acesas pelo Olho ganham o contorno ciano. Clicar numa máquina a escolhe como alvo do painel. Os outros roteadores ficam como hoje.

## Lembrar ao reabrir

`TabletMenu.State` ganha os três conjuntos da árvore, gravados ao fechar e restaurados ao abrir. Ficam na memória do servidor, como o resto do estado: limpos no logout e ao parar o servidor.

## Divisões antes de crescer (`wa-modular`)

Antes de tudo, numa tarefa só de divisão que não muda o jogo:
- o `RouterBlockEntity` (997 linhas) passa uma parte para uma parte de pacote;
- a `TabletList` (612) e a `TabletScreen` (686) também saem em partes.

As capturas do e2e têm de sair iguais (`comparar.py`).

## Guia do jogador

`scripts/guide/gerar_guia.py`, nos dois idiomas, só "como operar": a Lista com o roteador aberto em árvore, o clique numa máquina, "Mostrar todas" e o Mapa com as máquinas.

## Testes

- **GameTests:**
  - as máquinas no índice (ida e volta do NBT, formato antigo, teto de 512, atualização ao vincular, desvincular e renomear);
  - o filtro por tipo com um roteador que só usa as remotas;
  - o `TabletTreePayload` (abrir, recolher, mostrar todas, visibilidade, limites);
  - a página com as linhas na ordem certa e o limite contando linhas;
  - o "live" de uma linha de máquina (movendo com vazão, entrada cheia, descarregada);
  - lembrar ao reabrir.
- **JUnit:** a lógica pura nova, como a montagem das linhas da árvore e o modelo de linhas no cliente.
- **e2e:**
  - a Lista com um roteador aberto em árvore;
  - um grupo com "Mostrar todas";
  - a escolha de uma máquina pelo clique;
  - o Mapa com as máquinas;
  - tudo nos dois temas e em pt.
- **Bench:**
  - um cenário novo `tablet-range`: roteadores de alcance com máquinas, um jogador falso com alguns roteadores abertos na árvore, medindo o tempo do Tablet e os KB/s;
  - antes e depois: `tablet:1000:3`, `range:200:3` (o heap e o tamanho do arquivo do índice), `many:500:3` e o novo `tablet-range`.
