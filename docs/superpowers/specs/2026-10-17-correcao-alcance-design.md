# Correção do alcance: resgate, destino cheio e vincular sem rede (etapa 0 da central da rede)

Data: 17/10/2026. Ramo: `correcao-alcance`, sobre o `origin/main` 2.1.0 (com o Ressonador). Primeira entrega do refactor do Tablet ("central da rede"), cujo mockup aprovado está em `docs/preview/central-da-rede.html` (versão 6). Esta etapa sai sozinha, numa versão pequena, antes da central.

## O que muda

### 1. Resgate na máquina certa

`ItemGiveBack.rescue` insere a sobra que um destino recusou (depois de aceitar na simulação) em `destination.node.items/bulkItems`, que é a máquina **presa** do nó. Num destino remoto, a sobra ia para a máquina errada.

- Passa a usar os caches da máquina da porta (`destination.machine.items(face)` e `bulkItems(face)`). O `MachineCaches` já devolve `null` com o chunk descarregado (nunca carrega chunk).
- O comportamento com a presa não muda.

### 2. Destino cheio conta as remotas e não cria porta

`NodeProbe.fullDestinations` olha só as portas da presa e usa `NodePorts.port`, que **cria** a porta quando ela não existe.

- Passa a ler sem criar (`peek` na presa e as portas remotas existentes) e conta também os destinos remotos cheios.
- A regra de "cheio" (`isFull`, intervalo ≥ 8) não muda. Um destino sem máquina ou descarregado continua não contando como cheio.
- Efeito visível: o filtro "Com problema" e o aviso de destino cheio do Tablet passam a pegar roteadores cujo destino cheio é uma máquina vinculada.

### 3. Grupo novo nasce sem rede

Hoje, no modo Máquinas do Vinculador (clique e área), a primeira máquina de um tipo de bloco cria o grupo com as seis faces de **todos os tipos** na rede ativa de quem vincula (`RangeLinks.groupNetwork`, decisão 5 da spec da rede por face). Todas as abas aparecem com rede, mesmo sem nenhum modo.

- O Vinculador passa a vincular **sem rede**: o grupo novo nasce com todas as faces sem rede. Um grupo que já existe não muda.
- A rede entra face a face pela regra que já existe (D1): escolher um modo numa face sem rede, com uma rede ativa utilizável, põe só aquela face, naquele tipo, na rede ativa, com o aviso e o Desfazer.
- `RouterBlockEntity.linkMachine(pos, network)` continua aceitando uma rede (GameTests e a vitrine usam). Só o Vinculador deixa de passá-la.

### 4. "Tirar a rede das faces sem modo"

Para os grupos e as exceções que já nasceram com rede em tudo.

- **Onde:** no painel do nó (tela do roteador, roteador aberto pelo Tablet e painel do Tablet), na **linha Editando**, como um botão pequeno ao lado de "Voltar a igual", "Atualizar" e "Desvincular" (rótulo "Tirar redes", só o ícone se não couber; o nome inteiro na dica). Não cria linha nova nem aperta o corpo. Aparece só quando o alvo de edição é um **grupo** (o perfil) ou uma **máquina com exceção** e há ao menos uma face, de qualquer tipo, com modo Nenhum e com rede. Na presa e no painel só leitura não aparece. A tela decide pelo snapshot que já recebe (`FaceView.mode` e `FaceView.network` de todos os tipos do alvo); nada novo no snapshot.
- **O que faz:** tira a rede de todas as faces do alvo, em todos os tipos, que têm modo Nenhum e rede. Não mexe em face com modo (Extrai, Insere, Armazém), nem em prioridade, redstone ou filtro. Uma mudança só no roteador (um `changed`).
- **Aviso:** numa linha só, como os outros: "Rede tirada de N faces" (en_us: "Network removed from N faces"), com **Desfazer**. O Desfazer devolve a rede só às faces que continuam com modo Nenhum e sem rede (o que você mudou depois não é sobrescrito). Se nenhuma face pôde voltar, não há aviso. Vale até o próximo limpar do mesmo jogador, ele fechar a tela ou o servidor parar.
- **Protocolo:** payload novo cliente → servidor `ClearIdleNetworksPayload(int containerId, boolean undo)`, pelo `RouterEditHost` (vale na tela do roteador, no roteador remoto e no Tablet, com as validações de hoje: menu aberto, nó visível, carregado, ao alcance, acesso de edição). `PanelNoticePayload.Undo` ganha `RESTORE_IDLE_NETWORKS`. `ModPayloads.VERSION` sobe de `18` para `19`.
- **Dica do botão:** "Tira a rede das faces com modo Nenhum, em todas as abas. As faces que movem algo ficam como estão."

## Guia do jogador

`scripts/guide/gerar_guia.py`, nos dois idiomas, só "como operar":
- Vinculador, modo Máquinas: "Uma máquina de um tipo novo entra com todas as faces na sua rede ativa" vira "Uma máquina de um tipo novo entra sem rede; a face entra na sua rede ativa quando você escolhe um modo nela".
- Roteador de alcance: o botão "Tirar a rede das faces sem modo" e o Desfazer.

## Fora desta etapa

Tudo o mais do mockup da central (medição, Fluxos, Lista em árvore, rótulo, destaque no mundo, busca, Problemas) fica para as etapas 1 a 5.

## Testes

- GameTests novos no `RangeFixGameTests` (template `empty`): resgate de um destino remoto mentiroso vai para a máquina remota; destino remoto cheio conta em `fullDestinations`; `fullDestinations` não cria porta; Vinculador (clique e área) com rede ativa cria grupo sem rede em todos os tipos e lados; modo numa face do grupo põe só aquela face na rede ativa; limpar tira só as faces sem modo com rede, mantém as com modo, um aviso com `RESTORE_IDLE_NETWORKS`; Desfazer devolve e não sobrescreve face mudada depois; o payload pelo Tablet; payload de outro container é ignorado.
- Os GameTests existentes que esperavam o grupo nascer na rede ativa pelo Vinculador passam a esperar sem rede.
- e2e: uma captura do painel com o botão e outra com o aviso e o Desfazer (`range-6-limpar-redes`, `range-6b-limpar-aviso`), nos dois temas se o e2e já fizer a volta no escuro para o alcance.
