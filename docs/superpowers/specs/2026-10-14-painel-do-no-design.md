# Painel do nó (etapa 1 da remodelagem das telas)

Data: 14/10/2026. Mockup aprovado pelo dono: `docs/preview/tablet-painel.html` (v3). Mapa do fluxo: `docs/preview/mapa-do-fluxo.html` (decisões D1 e D5 desta etapa). Origem: sugestão do taccio3 no CurseForge (um painel estilo XNet para copiar e colar perfis).

## Objetivo

Configurar a base inteira de um lugar só, sem trocar de ferramenta: a aba Lista do Tablet vira um painel com a busca, as máquinas agrupadas e o nó escolhido editável ao lado; a tela do roteador usa o mesmo painel do nó. Copiar um perfil e colar em uma, em um grupo ou nas marcadas, sempre com desfazer.

## O que o jogador vê

### Lista do Tablet

- **Busca única**, com o cursor nela ao abrir a aba. Procura ao mesmo tempo no nome do nó, no tipo de máquina (o nome do bloco como aparece no jogo, por `RouterBlock.machineName`, e o id do bloco) e na rede. Placeholder "Buscar máquina, rede ou nome".
- **Sugestões** enquanto digita (a partir de 1 caractere), em até três grupos com título: Máquinas, Redes e Nomes; cada sugestão com o trecho digitado destacado e quantos nós pega; no máximo 3 por grupo; rodapé "Enter: procurar "<texto>" em tudo · ↑↓ escolher". Escolher uma (clique ou Enter numa sugestão realçada) vira uma **etiqueta** na caixa ("Máquina: Fornalha ✕", "Rede: Linha de fundição ✕"); as etiquetas se somam (E). Backspace com a caixa vazia tira a última; ✕ tira aquela. Enter sem sugestão realçada procura o texto em tudo.
- **Contagem e limpar**: "12 de 42 nós · Limpar" quando há busca, etiqueta ou filtro rápido. Sem resultado: "Nenhum nó com "<texto>" · Limpar a busca".
- **Filtros rápidos** de um clique, com contagem, embaixo da busca: Com problema (descarregado, sem rede ou destino cheio), Sem rede, Descarregados. Clicar de novo desliga. Somem quando a contagem é 0 e não estão ligados. Funcionam junto com a busca.
- **Lista agrupada por tipo de máquina**, com cabeçalho recolhível "▾ Fornalha · 10" (▸ recolhido). Dentro do grupo, as linhas por nome.
- **Cada linha**: bolinha de estado; nome; segunda linha com o estado em palavras (tudo bem: "<rede> · <vazão>"; senão "destino cheio" em âmbar, "sem rede" em vermelho, "descarregada" em cinza, "pausada"); e os **seis quadradinhos** das faces na aba (tipo) atual do painel, numa grade 3 × 2 na ordem Cima, Frente, Esquerda / Baixo, Trás, Direita, na cor do modo (azul Extrai, laranja Insere, verde Armazém, apagado Nenhum). Os quadradinhos somem quando a lista fica estreita.
- **Clicar numa linha** a escolhe e mostra o painel do nó ao lado; **duplo clique** (ou Enter na linha) abre a tela completa do roteador, como o clique de hoje.
- Some a paginação de hoje ("1–8 de 12" e ‹ ›) e o filtro por papel. A lista rola; se o resultado passar de 256 nós, a última linha diz "Mostrando 256 de N · refine a busca".
- O filtro por tipo vindo das Estatísticas continua (clicar num cartão de tipo filtra a Lista e escolhe essa aba no painel).
- O modo Selecionar continua (Todos, Mover para) e ganha "Colar nos N marcados" quando há perfil copiado.

### Painel do nó (o mesmo componente no Tablet e na tela do roteador)

- Cabeçalho: nome do nó; abaixo, "Fornalha · Elite · 18 blocos daqui" (no Tablet; na tela do roteador o cabeçalho é o da tela). No Tablet, botão "Tela completa" (abre a tela do roteador à distância, como hoje).
- Abas por tipo (os carregados): a ativa com ícone e nome, as outras só o ícone; à direita, o resumo "N redes".
- Linha **Editando** (só com o Upgrade de Alcance ativo): lista com "A máquina presa", "Todas as <Máquina> (N)" por grupo e cada máquina (recuada, "tem exceção" quando tiver); escolher troca o alvo, como o botão Máquinas faz hoje. Com uma máquina com exceção escolhida, o botão "Voltar a igual às outras" (o "Voltar ao perfil" de hoje) e "Desvincular".
- **Faces desdobradas como um cubo** (planificação em cruz, 4 colunas × 3 linhas): Cima em cima da Frente; Esquerda, Frente, Direita e Trás na linha do meio; Baixo embaixo da Frente. Cada face com uma tarja no topo na cor do modo, o nome (curto quando aperta: Esq., Dir.) e uma bolinha na cor da rede. Sem modo: borda tracejada. A face onde o roteador está preso aparece apagada com "roteador". A escolhida tem a borda coral.
- Face escolhida: "Baixo · <o que ela acessa>" (o nome dos slots/tanques que a face acessa, como a tela mostra hoje); os quatro modos (Extrai, Insere, Armazém, Nenhum); uma frase sobre o modo escolhido nesta máquina ("Tira da <máquina> e manda para a rede.", "Recebe da rede e põe na <máquina>.", "Recebe de quem extrai e entrega para quem insere.", "Esta face fica fora da rede."); **Rede** (lista das redes que o jogador pode usar e "Sem rede"); **Filtro** (resumo em uma linha, contando entradas e cartões) com Editar; **Prior.** − valor + (Shift = ±10); **Redstone** (lista: ignorar, ativo com sinal, ativo sem sinal). Prioridade e redstone sempre à vista (some o "Mais").
- Tipo sem filtro (Energia, Source): no lugar da linha Filtro, "Sem filtro".
- No Tablet: botão **Copiar perfil** no pé do painel, com "para colar em outras máquinas"; com perfil guardado, também **Colar perfil** (na máquina escolhida).
- Nó fora do alcance ou descarregado: o painel mostra a configuração guardada só para ler, com o motivo em cima ("Descarregada: chegue mais perto ou carregue o chunk", "Fora do alcance do tier Básico (64 blocos)"); controles apagados com a mesma dica.

### Tela do roteador

- A coluna da direita passa a ser o painel do nó; a fileira de seis botões de face embaixo do visor e a linha "Frente: Sul" saem (o cubo desdobrado mostra as faces e a do roteador); o recolhido "Mais" sai.
- O visor 3D e o cubo andam juntos: clicar numa face do visor escolhe a mesma no cubo, e vice-versa.
- Ficam: cabeçalho (nome, renomear, vazão, tier, slots de upgrade, botão de tema), visor 3D, slots de cartão da face escolhida, inventário, botão Máquinas/lista do alcance (agora também acessível pela linha Editando), giro com Shift + clique.
- Copiar e colar não aparecem na tela do roteador (continuam no Tablet e no Configurador).

### Copiar, colar e desfazer (Tablet)

- **Copiar perfil** (botão ou Ctrl+C com uma linha escolhida) guarda no item Tablet o perfil do nó: o mesmo preset do Configurador (as faces da máquina presa, em todas as abas, com as redes), mais o nome do nó e o bloco da máquina de origem. Sobrevive a fechar a tela e o jogo.
- Com perfil guardado, uma **faixa** tracejada em cima da lista: "Perfil copiado de <nó> · <máquina> · <resumo>" e "esquecer ✕".
- **Colar numa**: Colar perfil no painel ou Ctrl+V na linha escolhida.
- **Colar no grupo**: cabeçalho de cada grupo ganha "Colar nas N" (N = os nós do grupo nos resultados atuais, menos a origem); verde nos grupos da mesma máquina da origem, neutro nos outros. Dica: "Colar o perfil de <nó> nas N <máquinas> · a origem fica de fora · dá para desfazer".
- **Colar nos marcados**: no modo Selecionar.
- **Igual ou diferente**: com perfil guardado, a segunda linha de cada nó carregado diz "igual ao perfil" ou a primeira diferença ("diferente: Baixo sem filtro", "diferente: Cima em Extrai", "diferente: Baixo noutra rede"), comparando modo, rede, filtro, prioridade e redstone das faces, em todas as abas. Nós descarregados não comparam.
- **Aviso** depois de colar: "Perfil colado em <nó>" ou "Perfil colado em N nós · M ficaram de fora (descarregados ou fora do alcance)", com **Desfazer** (clique ou Ctrl+Z). Desfazer devolve cada máquina colada exatamente como estava antes (inclusive as redes), se ainda estiver carregada e ao alcance. Vale para o último colar, enquanto o Tablet estiver aberto.
- A regra das redes ao colar é a do Configurador (`PresetApplier`): a rede que o jogador não pode usar não é colada, e a face fica com a rede que já tinha (o aviso diz quantas faces ficaram).

### Regra D1: escolher o modo põe a face na rede ativa

- Vale no painel (Tablet e tela do roteador) e em qualquer caminho que mude o modo pelo `SetFacePayload`.
- Quando o modo muda de Nenhum para Extrai, Insere ou Armazém numa face **sem rede**, e o jogador tem uma **rede ativa** que pode usar, a face entra nela, no mesmo passo (uma exceção só, no alcance). Aviso: "<Face> entrou em <rede>" com Desfazer (que tira a rede só daquela face).
- Sem rede ativa: a face fica com o modo e sem rede; a linha Rede ganha o contorno coral e diz "Escolha a rede desta face", com a dica "Você não tem uma rede ativa. Com uma, a face entra sozinha." Nada é criado.
- Face que já tem rede: trocar o modo não mexe na rede. Voltar para Nenhum não tira a rede.

### Regra D5: filtro à distância

- Editar o filtro a partir do painel do Tablet ou da tela do roteador aberta à distância vale no mesmo alcance de abrir o nó (o `inReach` do Tablet), em vez dos 8 blocos fixos de hoje.
- Voltar do filtro retorna para onde o jogador estava: a tela do roteador (perto ou à distância) ou o Tablet, na mesma aba, com o mesmo nó escolhido, a mesma busca e as mesmas etiquetas.

### Teclado (Lista do Tablet)

Digitar procura; ↑ ↓ andam pelas linhas (e pelas sugestões, com a lista aberta); Enter escolhe a sugestão ou abre a tela completa da linha; Esc limpa a busca e, vazia, fecha o Tablet; Backspace com a busca vazia tira a última etiqueta; Ctrl+C copia o perfil da linha; Ctrl+V cola nela; Ctrl+Z desfaz o último colar; ← → recolhem e abrem o grupo da linha.

## Arquitetura

### Servidor

- **`menu/RouterEditHost`** (interface): o que uma ação de face precisa (`RouterBlockEntity router()`, o alvo de edição `EditTargetRef`/`EditTarget`, `boolean canEdit(ServerPlayer)` que confere alcance, carga e visibilidade, e o aviso de volta ao cliente). O `RouterMenu` (e o `RemoteRouterMenu`) e o `TabletMenu` (para o nó escolhido) implementam. Os handlers de `ModPayloads` que hoje exigem `player.containerMenu instanceof RouterMenu` passam a aceitar qualquer `RouterEditHost` do mesmo `containerId`, sem mudar o formato dos payloads de face.
- **Nó escolhido no Tablet**: `TabletMenu` guarda a `NodeKey` escolhida; o cliente escolhe por um payload novo; o servidor manda um `RouterSnapshot` do nó escolhido (o mesmo record e codec da tela do roteador), só com o Tablet aberto e um nó escolhido, quando o `changeVersion` do roteador muda ou a cada 20 ticks (por causa da vazão e do alcance). Fora do alcance ou descarregado: o snapshot guardado com a marca de só leitura e o motivo.
- **Índice**: `NodeIndex.Entry` ganha os modos por face (5 tipos × 6 lados relativos × 2 bits = 60 bits num `long`, chave NBT nova `face_modes`; ausente = tudo Nenhum, para mundos antigos), atualizado no `track` como hoje. `NodeView` leva esse `long` e a comparação com o perfil.
- **Consulta**: o `Query` passa a ter `text`, `textMachines` (ids de bloco cujo nome traduzido o cliente casou com o texto), `machines` e `networks` (etiquetas), os filtros rápidos (conjunto de `PROBLEM`, `NO_NETWORK`, `UNLOADED`) e o tipo (das Estatísticas). Some o `RoleFilter` e a página. O servidor ordena por id da máquina e depois nome, e manda até 256 nós e o total. O cabeçalho do snapshot leva as facetas (máquinas e redes distintas entre os nós visíveis, com contagem) e as contagens dos filtros rápidos.
- **Perfil no Tablet**: o item guarda o `RouterPreset` no componente `wirelessautomate:preset` (o mesmo do Configurador) e um componente novo `wirelessautomate:profile_source` (nome do nó e id do bloco da máquina de origem). Copiar, esquecer e colar são ações do `TabletMenu`, validadas como o Mover (visível, carregado, ao alcance).
- **Colar**: a lógica de colar do Configurador sai do `ConfiguratorItem` para `preset/PresetPaste` (copiar e colar num roteador por posição, com o `PresetApplier`), usada pelos dois. O Tablet cola em: um nó (chave), um grupo (id da máquina, reavaliado no servidor com a consulta atual) ou os marcados (até 256 chaves). Antes de colar, guarda `RouterPreset.copyOf` de cada nó no `TabletMenu` (o desfazer); desfazer aplica de volta exatamente, inclusive as redes nulas (`RouterPreset.restoreTo`).
- **Comparação com o perfil**: `preset/ProfileDiff.firstDifference(perfil, nó)` devolve nada (igual) ou a primeira diferença (tipo, lado, o quê: modo, rede, filtro, prioridade, redstone), percorrendo os tipos carregados e os seis lados em ordem fixa; calculada no servidor só para os nós carregados da resposta, quando o jogador tem perfil.
- **Aviso com desfazer**: payload novo servidor → cliente com o texto e o que o Desfazer faz (o último colar do Tablet, ou tirar a rede de uma face pela D1). A tela que hospeda o painel mostra o aviso por 5 segundos.
- **D1** no handler do `SetFacePayload`: com a face sem rede e o modo saindo de Nenhum, pega a rede ativa do jogador (`NetworkSavedData`); se existir e `canUse`, aplica modo e rede na mesma edição (`batchChanges` na presa, uma só exceção no alcance).
- **D5**: o alvo do filtro de face guarda de onde veio (perto, roteador à distância, Tablet) e confere o alcance de lá; o Voltar reabre a origem. O Tablet guarda, por jogador e só na sessão do servidor, a última consulta, a aba e o nó escolhido, para reabrir igual.
- `ModPayloads.VERSION` passa para `"16"`.

### Cliente

- **`client/NodePanel`**: o painel do nó como componente (desenho, clique, roda, teclado e dicas por geometria, sem widget por controle, como a Lista do Tablet), alimentado por um `RouterSnapshot` e um enviador de ações (o `containerId` da tela que o hospeda). Usado pela `RouterScreen` (coluna da direita) e pela `TabletScreen` (lado direito da Lista). Todo texto variável passa pelo `GuiText`; as cores pelo `GuiPaint`.
- **`client/FaceNet`**: a geometria da planificação (posição de cada lado relativo numa grade 4 × 3, nomes curtos), lógica pura com JUnit.
- **`client/TabletSearch`**: as sugestões (casar texto com facetas e nomes, destacar o trecho, ordenar, limitar 3 por grupo) e as etiquetas, lógica pura com JUnit.
- **`client/ListGroups`**: agrupar a resposta por máquina, recolher, achatar em linhas para rolar e navegar com o teclado, lógica pura com JUnit.

## Fora desta etapa (etapa 2)

Redes só no Tablet e a lista do Vinculador; a mesma regra para Mover e Vinculador (D2); o perfil com os grupos e exceções do alcance (D3); as máquinas do alcance como linhas na Lista (D4); o nome "Perfis" no lugar de grupos de máquinas (D6).

## Testes

- JUnit: `FaceNet`, `TabletSearch`, `ListGroups` (e o que mais sair puro).
- GameTests: os handlers pela interface (tela do roteador e Tablet), a D1 (presa, perfil e exceção do alcance; com e sem rede ativa; rede que não pode usar), o índice com `face_modes` (ida e volta, mundo antigo), a consulta (texto, máquinas, redes, filtros rápidos, ordem, limite 256, facetas), copiar/colar/desfazer (um, grupo, marcados, rede que não pode usar, nó descarregado, desfazer devolvendo rede nula), o `ProfileDiff`, a edição pelo Tablet fora do alcance recusada, o filtro à distância e o Voltar ao Tablet. Os testes do Tablet que usam `RoleFilter` e página são reescritos para a consulta nova.
- e2e: os passos do Tablet (buscar pela caixa com sugestão e etiqueta, filtro rápido, escolher a linha, editar uma face no painel, copiar, colar no grupo, desfazer, filtro à distância e Voltar, duplo clique abre a tela completa) e os da tela do roteador (o painel na coluna da direita, o cubo, o visor sincronizado, prioridade e redstone sem "Mais"); a volta escura e a volta em pt_br com `clipCheck`. As capturas do `DevScreenshot` refeitas.

## Guia e docs

Páginas `network-tablet` e `router` do guia (nos dois idiomas, pelo `gerar_guia.py`); `docs/especificacao.md` (telas do roteador e do Tablet); `CLAUDE.md` (mapa); `docs/progresso.md`.
