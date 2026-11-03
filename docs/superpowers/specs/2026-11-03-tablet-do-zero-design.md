# Tablet do zero: três abas, Redes, Máquina e Fluxos

O dono aprovou em 03/11/2026, sobre o mockup `docs/preview/tablet-do-zero.html` (as imagens saem do `gerar.py` da mesma pasta), depois de três rodadas de crítica. O pedido foi refazer o Tablet sem viés, "clean, bonito e sem poluição", com três tarefas: criar e organizar redes, entender por que uma máquina parou e ver, por recurso, quem dá e quem recebe.

## O problema

O Tablet de hoje tem 4 abas: Lista (com as visões Roteadores, Máquinas e Grade), Fluxos, Mapa e Redes. O lado do cliente tem 76 arquivos e cerca de 14,5 mil linhas. Selecionar, colar nos marcados, mover em massa, o perfil, o mapa, a árvore e os filtros rápidos competem pela mesma tela. Para responder "por que essa fornalha parou?", o jogador precisa juntar estados espalhados por linhas e cores.

## As três abas

As medidas estão em pixels de GUI. A tela tem 420×280, na escala do jogo. A moldura é a mesma nas três abas: "Tablet" em negrito em x=8 e as abas à direita, com a ativa sublinhada pelo `acc`. Uma divisória separa a coluna esquerda (148 px) do painel direito, que vai de X0=158 a X1=411. Há um cabeçalho comum: ícone ou slot, título em negrito, subtítulo em `muted`, estado à direita e uma linha embaixo.

### Redes

- **Coluna esquerda.** Os grupos aparecem com o ícone (16 px) e o nome em negrito. Um grupo pausado mostra "pausado" em `muted` à direita, na coluna das contagens. Embaixo de cada grupo ficam as redes dele: quadradinho da cor, nome e contagem de máquinas. Por último vem "Sem grupo", só o texto em `muted`, com as redes avulsas. No rodapé ficam "+ Rede" (primário) e "+ Grupo".
- **Rede escolhida.**
  - O cabeçalho mostra a cor num slot, o nome com um lápis, "N máquinas" e o estado ("ativa", ou "pausada" quando o grupo está pausado).
  - Clicar no slot abre o seletor de cor. Clicar no nome troca o nome por um campo.
  - Na linha "Grupo", um seletor com o ícone e o nome do grupo; uma rede fica em um grupo só (ver as decisões).
  - Na linha "Acesso", os segmentos "Todos" e "Convidados". Em Convidados aparecem os nomes (jogadores e times) em fichas com "x" e o link "+ convidar".
  - A seção "Nesta rede" tem uma linha por tipo presente, com o ícone do tipo, o nome e "N dão · N recebem".
  - No rodapé, "Pausar" (pausa o grupo da rede) e o texto "Apagar rede".
- **Seletor de cor.** É o da Tela de Sinal, sem alfa, sem conta-gotas e sem as cores do tema. Tem o quadrado de saturação e brilho (88×56), a faixa de matiz, o campo hexadecimal e as 9 cores recentes, com a atual emoldurada. A janela é pintada com `hi` sobre um véu (`scrim`).
- **Grupo escolhido.**
  - O cabeçalho mostra o ícone num slot, o nome com um lápis e "N redes · N máquinas".
  - A seção "Redes do grupo" lista as redes com o número de máquinas, mais o link "+ pôr uma rede neste grupo".
  - No rodapé, "Pausar grupo" e "Apagar grupo".
- **Ícone do grupo.**
  - Clicar no slot abre uma grade de 8×3 itens com busca. Ela aceita um item arrastado do JEI ("ou arraste um item do JEI"), e o escolhido fica emoldurado em `fg`.
  - Grupo sem ícone usa o item da primeira máquina dele e, sem máquina, o Tablet.

### Máquina

- **Coluna esquerda.** No topo, o campo "Buscar máquina". Embaixo, as máquinas do jogador, com passo de 24 px. Cada linha tem o ícone, o nome e o estado numa segunda linha:
  - o estado vai em `bad`, `warn` ou `muted`;
  - uma máquina andando mostra a vazão ("96 itens/s").
- **Máquina escolhida.**
  - **Cabeçalho:** o ícone, o nome e "Roteador <tier> · a N blocos".
  - **Cartão do veredito.** Uma faixa de 2 px na cor do estado, o título em negrito ("Parada: destinos cheios") e o detalhe em `muted` ("2 cheios em Fundição"). Se houver uma ação, aparece o botão primário que a resolve ("Ver destinos"). Embaixo vêm os outros problemas, um por linha, com a marca de erro na cor e um link de ação ("Editar").
  - **Faces:** só as que estão em uso, com o rótulo "Faces" em x=X0+4. Cada linha tem o lado, a seta do modo desenhada na cor do modo, o nome do modo e a rede (ou "sem rede" em `bad`). O chevron à direita abre a linha.
    - Uma face aberta mostra os segmentos Extrai, Insere, Armazém e Nenhum, o seletor de rede e o link "Filtro e mais". Sem rede, o seletor tem borda `bad` e o texto "Escolher rede".
    - As faces sem uso ficam recolhidas numa linha só ("3 faces sem uso").
  - **Rodapé:** "Mostrar no mundo" e "Abrir roteador".
- **"Filtro e mais"** abre a tela do roteador já naquela máquina, tipo e face.

### Fluxos

- **No topo**, os segmentos dos tipos carregados, com o ícone de cada um. Embaixo, o total em negrito ("400 itens/s"), o resumo "· 4 dão · 5 recebem" e o seletor "Todas as redes" (ou uma rede).
- **Duas colunas, Dão e Recebem.**
  - Os títulos vão em `muted`.
  - Cada linha tem o ícone, o nome e, embaixo, o quadradinho e o nome da rede, com a vazão à direita.
  - As linhas vão da maior vazão para a menor.
  - Uma máquina parada mostra o motivo no lugar do número, com a mesma palavra da aba Máquina.
- **Armazéns.** Embaixo das colunas, cada um na coluna do sentido: esvaziando na de Dão, enchendo na de Recebem. O saldo usa `ok` para "+N" e `fg` para "−N".
- Clicar numa linha leva à aba Máquina com aquela máquina escolhida.

## O que sai do Tablet

- O Mapa, a árvore de roteadores e a visão por blocos (Grade).
- Selecionar, colar nos marcados, mover em massa e o perfil no Tablet. Copiar e colar por máquina continua na tela do roteador.
- Os filtros rápidos, as etiquetas na busca, os contadores do topo, os Fluxos por rede e a barra de tempo do tick.
- As classes do servidor que só serviam a isso também saem: `TabletMap`, `TabletMarks`, `MarkedTargets`, `MarkedPaste`, `MarkedMove`, `PasteUndo` (se nada fora do Tablet usar), `TabletProfile`, `TabletTree` e `TabletRows` na parte de árvore. Junto saem os payloads `TabletMapPayload`, `TabletMoveNodesPayload`, `TabletProfilePayload` e `TabletTreePayload`, e os GameTests, JUnits e passos de e2e dessas partes.

## Decisões

1. **Paleta.** As cores são as do `GuiPalette`, nos dois temas.
   - O `acc` (laranja) marca só a aba ativa e o botão primário.
   - A linha escolhida tem a faixa de 2 px em `fg`.
   - O campo com foco tem a borda de 1 px em `fg`.
   - O erro é um carmim frio, longe do laranja: `0xB8304F` no claro e `0xE5577A` no escuro. Se o `GuiPalette` já tiver um vermelho de erro, ele é ajustado para esses valores.
   - Nos segmentos, o escolhido fica em `hi` com moldura de 1 px em `fg`, e os outros ficam planos (`face`), com o texto em `muted`.
2. **Grupo com ícone.**
   - O `WaGroup` ganha `icon`, o id de um item (`ResourceLocation`), opcional. Na chave NBT `icon`, só é gravado quando existe. Um grupo antigo carrega sem ícone.
   - Uma ação nova no `TabletActionPayload` muda o ícone: `GROUP_ICON`, com o id em `text`.
3. **Uma rede, um grupo.** Hoje uma rede pode estar em vários grupos. A tela nova trata o grupo como o lugar da rede: o seletor "Grupo" move a rede, tirando-a dos outros. Um mundo antigo com uma rede em vários grupos fica como está até alguém mexer, e a lista mostra a rede no primeiro grupo dela.
4. **Cor livre.** A paleta fixa (`NetworkSavedData.palette()`) deixa de ser a única escolha. Uma rede nova recebe a próxima cor da paleta, e o seletor aceita qualquer RGB. A chave `color` continua igual.
5. **Seletor de cor comum.**
   - O popup da Tela de Sinal sai para um componente em `client/` (por exemplo `client/color/ColorPopup`), com alfa, conta-gotas e cores do tema opcionais. A Tela de Sinal passa a usá-lo com tudo ligado, sem mudar o visual nem o comportamento dela, e o e2e da Tela de Sinal prova isso.
   - O `TvHsv` vai para o mesmo lugar, e os testes JUnit dele (`TvPickerMathTest`) vão junto.
   - As cores recentes ficam como hoje: 8, só na sessão. O mockup mostra 9, mas vale o que já existe.
6. **Abrir o roteador numa face.** O `TabletOpenNodePayload` passa a levar o alvo (`EditTargetRef`), o tipo e a face, opcionais, e o `RemoteRouterMenu.open(player, router, EditTargetRef)` já existe. A tela do roteador abre naquele tipo e face. Isso muda `ModPayloads.VERSION`.
7. **Veredito da máquina.**
   - É montado no servidor, só para a máquina escolhida, com a aba Máquina aberta, uma vez por segundo, e vai num payload próprio só quando muda.
   - A classe nova fica em `network/`, porque o `Port` é package-private. Ela devolve um veredito puro: o motivo principal, o detalhe, a ação e a lista de problemas, montados com o que o motor já guarda:
     - `faceNetwork == null` numa face em uso: "sem rede";
     - `full` nos destinos: "destinos cheios", contando quantos e em qual rede;
     - `stopped`: redstone;
     - grupo pausado: "pausada";
     - chunk descarregado: "descarregada";
     - o filtro da face.
   - A lógica que transforma esses fatos num veredito fica numa classe pura, com JUnit.
   - **Limite conhecido:** o motor não distingue "vazia" de "o filtro barrou", porque `waitsDestination == false` vale para os dois. O veredito diz "nada para mandar", e a linha do filtro aparece como problema quando a face tem filtro.
8. **Vazão por máquina nos Fluxos.**
   - O mockup supunha "a média de 5 s que já existe por porta", mas ela não existe: a janela de 5 s é por rede × tipo (`FlowWindow` no `FlowSampler`). A vazão por máquina hoje é a diferença de cerca de 1 s do `TabletMachineSample`.
   - Os Fluxos usam essa amostra de 1 s, suavizada no cliente com a mesma janela de 5 s (`FlowWindow`, que é pura) por máquina, e só com a aba aberta.
   - Fica de fora uma janela por porta no motor.
9. **Fluxos com filtro de rede.** O seletor "Todas as redes" fica e filtra no cliente: a lista já chega com a rede de cada máquina.
10. **Aba inicial.** O Tablet abre na última aba usada (por jogador, na sessão). Clicar com o Tablet numa máquina abre em Máquina, com ela escolhida.

## Custo

- Com a tela fechada, nada roda nem é enviado. Aberta, só a aba à vista recebe dados, e só quando eles mudam.
- **Redes:** o cabeçalho e as redes já saem por mudança (`TabletHeaderPayload`). O ícone do grupo é um id a mais por grupo.
- **Máquina:**
  - A lista é a de máquinas de hoje, a cada 20 ticks e só se mudou.
  - O veredito é de uma máquina só, 1 por segundo, e lê campos que o motor já mantém, sem consultar capabilities.
- **Fluxos:**
  - Um tipo por vez, no máximo 32 linhas por coluna, 1 por segundo e só se mudou.
  - O payload de hoje, agregado por rede, dá lugar a linhas por máquina do tipo escolhido.
- **Cliente:** nada é recalculado por quadro além de desenhar. As linhas e os textos ficam em cache e são refeitos só quando chega um payload.

## Etapas

1. **Moldura e Redes.**
   - A moldura nova com as 3 abas, os componentes comuns (segmentos, slot, seletor, campo, link, janela com véu) e a aba Redes completa.
   - Também entram o ícone do grupo (NBT e ação), a decisão "uma rede, um grupo" e o seletor de cor comum, com a Tela de Sinal migrada para ele.
   - Durante a transição, Máquina e Fluxos mostram as abas antigas (a visão Máquinas da Lista e o Fluxos de hoje) dentro da moldura nova, e o Mapa sai da barra.
2. **Máquina.** A lista, o veredito (servidor, payload e lógica pura com JUnit), as faces editáveis ali mesmo, "Mostrar no mundo" e "Abrir roteador"/"Filtro e mais" na face (payload novo).
3. **Fluxos.** As linhas por máquina do tipo escolhido, os armazéns pelo saldo, o filtro de rede e o clique que leva à Máquina.
4. **Limpeza.** Sai o que está em "O que sai do Tablet", com os testes e o e2e. Junto vêm o guia (páginas do Tablet nos dois idiomas), `docs/mapa/tablet.md` e `docs/especificacao.md`.

Cada etapa termina testável no jogo e tem plano próprio em `docs/superpowers/plans/2026-11-03-tablet-do-zero-etapa-N.md`.
