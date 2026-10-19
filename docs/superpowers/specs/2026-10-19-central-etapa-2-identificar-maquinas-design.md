# Central da rede, etapa 2: identificar máquinas

Data: 19/10/2026. Ramo: `central-etapa-2`, a partir do `main` 2.2.0. Mockup aprovado: `docs/preview/central-etapa-2.html` (com as telas da versão 6 da central, `docs/preview/central-da-rede.html`). Decisões do dono: até 64 máquinas acesas, o destaque até a distância de visão e rótulo de 32 caracteres.

## O que entrega

As máquinas vinculadas pelo Upgrade de Alcance ganham nome e lugar:
- o rótulo de cada máquina vinculada e o nome de cada grupo, editáveis;
- as coordenadas nas linhas;
- o destaque no mundo, aceso pelo Olho ◉, que aparece através dos blocos;
- o clique com o Tablet numa máquina, que abre a central com ela escolhida;
- o rótulo e o grupo na mensagem do Vinculador.

Tudo isso vale na tela do roteador e no painel do nó do Tablet de hoje. A Lista do Tablet com as máquinas como linhas é a etapa 3.

## Custo (skill `wa-performance`)

Nada desta etapa toca no laço do motor.

| Parte | Por tick | Por quadro | Rede | Memória e save |
| --- | --- | --- | --- | --- |
| Rótulo e nome do grupo | 0 | 0: o texto vem no snapshot | Um pacote cliente → servidor ao salvar. O snapshot da tela aberta só sai se mudou: +1 byte por máquina sem rótulo | `label` no NBT da máquina só quando há rótulo |
| Coordenadas | 0 | 0: o texto é montado quando o snapshot muda | 0: a posição já vai | 0 |
| Destaque no mundo | 0 | Nada aceso: uma conferência de lista vazia. Aceso: arestas, véus e linha por máquina, sem alocar, sem teste de profundidade, só na dimensão atual e até a distância de visão | 0 | Só na sessão do cliente, até 64 |
| Tablet clicado numa máquina | 0 | 0 | Só no clique | 0: consulta O(1) ao `RemoteIndex` e aos 6 vizinhos |
| Mensagem do Vinculador | 0 | 0 | A mensagem que já sai | 0 |

O rótulo e o nome do grupo são mudanças só visuais. Elas sobem o `changeVersion`, chamam `setChanged()` e o `NodeIndex.track`, sem o `changed()` que suja as rotas: renomear não remonta nada.

## Rótulo e nome do grupo (servidor)

- `RemoteMachine` ganha `String label` (vazio = sem rótulo), salvo na chave `label` só quando não é vazio. A leitura sem a chave dá vazio.
- `MachineGroup.setName` passa a limpar o texto como o nome do roteador: `StringUtil.filterText`, `strip`, até 32 caracteres. A chave `name` não muda.
- O limite é o `RenameRouterPayload.MAX_LENGTH` (32).
- Payload novo cliente → servidor: `RenameTargetPayload(int containerId, EditTarget.Kind kind, int id, Optional<BlockPos> machinePos, String name)`.
  - `kind` é `GROUP` ou `MACHINE`.
  - Numa `MACHINE`, `id` é o índice e `machinePos` tem de bater com a máquina daquele índice (a mesma conferência do `UnlinkMachinePayload`). Num `GROUP`, `id` é o id do grupo.
  - O handler resolve pelo `ModPayloads.host(player, containerId)` (vale na tela do roteador, no roteador remoto e no Tablet) e recusa um texto com mais de 32 caracteres depois do `strip`.
  - O texto vazio tira o rótulo.
- O roteador aplica pela parte de pacote do alcance (`RouterRange`), sem crescer o `RouterBlockEntity` além de um método de entrada.
- `RangeView.MachineView` ganha `String label`, gravado com o mesmo teto do nome do grupo (`RouterSnapshot.NAME_LENGTH`).
- Protocolo: `ModPayloads.VERSION` passa de `"22"` para `"23"`.

## Nomes nas telas (cliente)

- **Nome de uma máquina:** o rótulo, ou "#N" quando não tem, como hoje. Onde hoje aparece "Fornalha #4" (a pílula do alvo, a dica, a lista do alvo), aparece o rótulo, ou "Fornalha #4" quando não há rótulo. Um só método monta o nome (`RangeList.machineName`).
- **Linha da máquina na lista do roteador:** passa a ter duas linhas, com a mesma altura da linha do grupo.
  - Na primeira, o nome.
  - Na segunda, as coordenadas, a distância ao roteador e o estado em palavras quando não está carregada e movendo: descarregada, sumiu, fora do raio, acima do limite, trocada.
  - À direita, a marca "exc." e o Olho ◉.
- **Editar o nome:** o ✎ entra nos botões pequenos da linha escolhida (máquina ou grupo), e o duplo clique no nome da linha também edita.
  - No painel do Tablet, onde a linha Editando aparece, o ✎ fica ao lado da pílula do alvo quando o alvo é um grupo ou uma máquina.
  - A edição é uma caixa de texto no lugar do nome: Enter salva, Esc cancela, clicar fora salva, como o renomear do roteador. O máximo é 32 caracteres; a caixa começa com o rótulo atual.
  - Só se edita com o painel editável: nada aparece no Tablet só leitura.
- **Grupo:** o nome dado, ou o nome do bloco, como hoje.

## Destaque no mundo (cliente)

- `MachineHighlights`: o conjunto das máquinas acesas, só na sessão. Cada uma tem a dimensão, a posição da máquina e a do roteador (para a linha).
  - Até 64. Acender a 65ª apaga a mais antiga e mostra na barra de ação: "Limite de 64 máquinas acesas: a mais antiga foi apagada".
  - Ligar de novo uma acesa a apaga.
  - Fica limpo ao sair do mundo (`ClientPlayerNetworkEvent.LoggingOut`).
  - Nada vai ao servidor nem ao save.
  - A lógica de conjunto é pura, com JUnit.
- **O desenho**, num `@EventBusSubscriber` próprio do cliente, no `RenderLevelStageEvent` depois dos blocos translúcidos:
  - sem nada aceso, volta na primeira linha;
  - só as máquinas da dimensão atual e dentro da distância de visão (`options.getEffectiveRenderDistance() × 16` blocos);
  - por máquina: o contorno ciano do mockup (`#3FC9BE` na aresta, véu leve nas faces) e uma linha contínua até o roteador;
  - um `RenderType` novo de linhas e outro de faces, os dois sem teste e sem gravação de profundidade, para aparecer através dos blocos;
  - nada alocado por quadro.
- **Onde se liga:**
  - o Olho ◉ em cada linha de máquina da lista do roteador;
  - o botão "◉ No mundo" na linha dos botões da máquina no painel (a mesma de Voltar a igual, Atualizar e Desvincular), no roteador e no Tablet.
  
  O Olho aceso fica ciano; apagado, cinza.
- **Apagar todas:** uma linha embaixo da lista das máquinas do roteador, "N máquinas acesas no mundo · Apagar todas", só quando há alguma acesa. No Tablet, o mesmo "Apagar todas (N)" no pé da Lista, ao lado do Selecionar, só quando há alguma acesa.

## Do mundo para a tela (servidor)

- **Tablet clicado num bloco** (`NetworkTabletItem.useOn`, que hoje não existe):
  - procura os roteadores daquela máquina: os que a vinculam (consulta nova `RemoteIndex.routersAt(Level, BlockPos)`, só as remotas ativas) e os presos nela (os 6 vizinhos com `RouterBlock` e `FACING` virado para ela);
  - fica com o primeiro que o jogador vê, pela regra de visibilidade do Tablet, preferindo o preso;
  - abre o Tablet na Lista com o nó escolhido e, se for remota, a máquina como alvo do painel;
  - sem nenhum roteador: se o bloco tem capability de item, fluido ou energia (consultada uma vez, no clique), mostra "Esta máquina não está em nenhum roteador seu" na barra de ação; em qualquer caso o clique segue como hoje (abre o Tablet);
  - clicar num roteador continua abrindo a tela dele.
- **Vinculador no modo Máquinas:** as mensagens de vincular e desvincular dizem a máquina e o grupo: "Fundição 3 (Fornalhas) vinculada a Forja · 15/32 máquinas", e "Fornalha #4 (Fornalhas)…" sem rótulo. Ao desvincular, o nome é lido antes de tirar.

## Guia do jogador

`scripts/guide/gerar_guia.py`, nos dois idiomas, só "como operar": na página do Upgrade de Alcance, dar nome a uma máquina ou a um grupo (✎ ou duplo clique), o Olho e o "Apagar todas"; na do Tablet, clicar numa máquina com o Tablet.

## Testes

- **GameTests:**
  - o rótulo faz a ida e volta pelo NBT, e uma máquina sem a chave lê vazio;
  - o `RenameTargetPayload` pelo menu do roteador: rótulo de máquina, nome de grupo, texto vazio tira, mais de 32 caracteres recusado, `machinePos` errada recusada;
  - o snapshot leva o rótulo, e renomear não suja as rotas;
  - o Tablet clicado numa remota abre com o nó e o alvo certos, e numa presa com o nó certo;
  - sem roteador, nada abre de outro jogador;
  - a mensagem do Vinculador leva o rótulo e o grupo.
- **JUnit:** o `MachineHighlights` (limite, a mais antiga sai, ligar de novo apaga, limpar).
- **e2e:**
  - a lista do roteador com rótulos, coordenadas e um Olho aceso;
  - a edição do nome aberta;
  - o painel do Tablet com o ✎ e o "No mundo";
  - uma captura do mundo com uma máquina acesa atrás de um bloco;
  - nos dois temas onde o e2e já faz a volta.
- **Bench:** `tablet:1000:3`, `many:500:3` e `range:200:3`, antes e depois, sem mudança fora do ruído. O tempo de quadro com 0 e com 64 acesas é medido por `System.nanoTime` num build local.
