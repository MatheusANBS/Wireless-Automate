# A presa vira máquina do grupo (etapa 1 do fim do roteador de alcance)

Aprovado pelo dono em 02/11/2026 sobre o mockup `docs/preview/alcance-presa-no-grupo.html` (os quatro pontos: bloco trocado na frente, mundos antigos sem perder nada, o grupo da presa não some, o Configurador fica sem função até a etapa 3).

## O problema

A máquina onde o roteador está preso (a "presa") tem um modelo à parte das máquinas do alcance: as faces ficam no `RouterFaces` por `RelativeSide` do roteador, os cartões no `RouterCards`, e a tela mostra a linha "Máquina presa" fora dos grupos. Com o Upgrade de Alcance, uma fornalha presa e três fornalhas vinculadas são configuradas em dois lugares. O dono quer a presa agrupada com as máquinas do mesmo bloco.

Junto: a máquina vinculada pelo Vinculador entra hoje num grupo que já existe com uma exceção vazia (`linkMachineIsolated`), e só a primeira do grupo funciona. Passa a seguir o perfil.

## O modelo

- **Membro presa.** `RemoteMachines` ganha um membro opcional, a presa: o id do grupo, a exceção (opcional) e a última frente conhecida. Não está na lista `machines` (não conta no limite do tier, não entra no `RemoteIndex`, não depende do Upgrade de Alcance nem do raio). A posição é sempre a do bloco na frente do roteador (`RouterBlock.attachedPos`).
- **Quando existe.** O membro nasce na primeira escrita na presa (ou na migração) com o bloco da frente que não é ar: entra no primeiro grupo daquele bloco, ou num grupo novo com o perfil vazio. Ler a presa sem membro dá a configuração vazia (tudo Nenhum, sem rede). Um roteador num bloco que ninguém configurou não cria grupo.
- **A configuração da presa** é a do membro: a exceção, ou o perfil do grupo. Os cartões são os do grupo (decisão 9 da spec do alcance: cartões só no grupo). Os lados são `RemoteSide`, relativos à frente da máquina, como as remotas. O `RelativeSide` do roteador deixa de guardar configuração.
- **Escrever na presa** (a API de sempre do roteador: `setMode`, `configureFace`, `setFaceNetwork`, `setFilter`, `setCard`, `setAttachedFacesNetwork`..., e o alvo `EditTarget.ATTACHED` das telas) tem a semântica de editar uma máquina: se a presa é a única máquina do grupo, muda o perfil; senão, copia o perfil para a exceção e aplica, e a exceção igual ao perfil some. Assim, sem o Upgrade de Alcance (grupo de uma máquina só), editar a presa é editar o perfil, como antes.
- **`face()` da presa é só leitura** para quem está fora do roteador: devolve a configuração que vale (exceção ou perfil). Ninguém fora do roteador muda o `FaceConfig` devolvido; toda escrita passa pelos métodos.
- **Grupo com a presa nunca sai** quando a última remota é desvinculada. O grupo que a presa deixa (bloco trocado) fica, mesmo sem máquina ("×0"), a não ser que esteja vazio de verdade (perfil igual ao vazio e sem cartões): esse sai sem perda. Apagar um grupo à mão é a etapa 2.

## Bloco trocado na frente

- **Ar** (máquina quebrada): o membro fica no grupo, como uma remota que "sumiu"; nada se perde, e recolocar a máquina volta a funcionar.
- **Outro bloco** (que não é ar): o membro passa para o grupo do bloco novo (o primeiro daquele bloco, ou um novo com o perfil vazio), sem o Atualizar. A exceção é descartada (ela era do bloco antigo). O grupo antigo segue a regra acima.
- **Quando confere:** no aviso de vizinho da posição da máquina (`machineNeighborChanged`, que já distingue troca de bloco de troca de estado), ao girar o roteador para outra face (`facing` novo) e na primeira leitura com o nível pronto. Nunca por tick. Só com `level.isLoaded(machinePos)`.
- **Girar o `spin`** não muda mais nada na presa: a configuração é da máquina, não do roteador.
- **Girar a máquina** (frente nova): a configuração acompanha a frente, como nas remotas.

## Mundos antigos

As chaves de NBT `faces` e `cards` do roteador (a presa antiga, por `RelativeSide`) são lidas para um rascunho e convertidas na primeira oportunidade com o nível pronto e a máquina carregada (o `onLoad`, ou a primeira leitura depois):

1. Rascunho vazio (tudo no padrão, sem cartões): some.
2. Lados: `RelativeSide` do roteador → face absoluta (`facing` + `spin`) → `RemoteSide` pela frente da máquina (sem frente, Norte), como o `ProfileSides`.
3. Bloco da frente é ar: o rascunho fica guardado (continua salvo nas chaves antigas) até haver bloco.
4. Sem grupo do bloco: grupo novo com o rascunho como perfil e os cartões dele.
5. Com grupo do bloco e cartões iguais (ou rascunho sem cartões): entra no grupo; se a configuração difere do perfil, como exceção.
6. Com grupo do bloco e cartões diferentes: grupo novo do mesmo bloco com o rascunho e os cartões (a exceção não tem cartões).
7. A rede antiga da aba (`networks`/`network`, migração de mundos de antes da rede por face) vai para o rascunho antes da conversão, como hoje.

Depois de convertido, o roteador não grava mais `faces` nem `cards`.

## Motor

A montagem das portas da presa (`NetworkRoutes.readPorts`) lê pela fachada: a configuração da face absoluta vem do membro, convertida pela frente da máquina (lida uma vez por montagem), e os filtros pelos cartões do grupo (`RouterCards.remoteFilterSet`). As portas da presa continuam no array `ports` do `NodePorts`, com o `attachedCaches` e o aviso de vizinho de sempre. Nada muda no tick.

## Telas e Tablet

- **Roteador:** some a linha "Máquina presa". A presa é a primeira linha dentro do grupo dela, com o rótulo "presa" (ou o nome dado) no lugar do "#N · distância" e o Olho. As remotas mantêm a numeração `#1..#N` (a presa não é numerada, para o "#N" seguir igual no Tablet). A contagem do grupo inclui a presa ("Fornalha ×4"), e "vale para N de M" também. Clicar na presa edita a presa (alvo `ATTACHED`); "Voltar ao perfil" aparece quando ela tem exceção.
- **Sem o Upgrade de Alcance:** a tela segue sem lista; o painel edita a presa, que é o grupo de uma máquina só.
- **Tablet, árvore:** a linha da presa vai para dentro do grupo dela, antes das remotas. **Visão por máquinas:** já agrupa por bloco; nada muda além dos textos.
- **Índice:** o `NodeIndex.Entry` guarda o grupo da presa (`attached_group`, −1 sem grupo; ausente nos índices antigos = −1).
- **Protocolo:** `ModPayloads.VERSION` sobe uma vez.

## Configurador

Sai toda a lógica: copiar e colar, o seletor de tipo, a roda, as duas mensagens (`ConfiguratorWheelPayload`, `CycleConfiguratorTypePayload`), a tecla da roda e a exceção no `RouterBlock.useItemOn`. O item, a receita, o modelo e os componentes (`configurator_*`, inclusive os antigos) continuam registrados, para os itens não sumirem dos mundos. O tooltip diz que a função nova vem numa próxima versão. A página do guia vira uma nota curta. O `RouterPreset`, o `PresetPaste` e o `PresetApplier` ficam: o Tablet usa.

## Custo

- Tick: nada novo. A fachada só roda na montagem das rotas, na tela e nas edições.
- Montagem: a frente da máquina lida uma vez por montagem do tipo (um `getBlockState` com `isLoaded`), como as remotas.
- Memória: o `RouterFaces` deixa de alocar 36 `FaceConfig` por roteador (6 lados × 6 tipos); a presa usa o perfil do grupo.
- Rede: o snapshot e o índice ganham um `int` (o grupo da presa).
- Bench antes e depois: `many`, `full`, `raw`, `rebuild` e `mixed`.

## Fora desta etapa

Desvincular o grupo inteiro, copiar e colar por máquina, o Atualizar sem perder cartões (etapa 2) e a função nova do Configurador (etapa 3).
