# Vinculador de um dono: a máquina de um roteador só e o Ctrl + clique nas que se tocam

Pedido e decidido pelo dono em 13/11/2026 (mockup aprovado: `docs/preview/vinculador-um-dono.html`). Muda partes da spec `2026-11-02-vinculador-mesmo-bloco-design.md` (o "quem entra", a ordem e o desvincular do Ctrl + clique); o resto dela segue valendo.

## Regra 1: uma máquina, um roteador

- No modo **Vincular**, o clique e o Ctrl + clique **recusam a máquina que já é de outro roteador**. Conta:
  - a máquina **vinculada** a outro roteador carregado, **ativa ou não** (fora do raio ou além do limite também);
  - a **presa** de outro roteador: um roteador carregado encostado nela, com a frente (`machinePos()`) nela.
- **Texto da recusa** (clique simples): `Fornalha já é de Forja Norte em 812, 64, -230` (o nome da máquina pelo `RouterBlock.machineName`, o nome do outro roteador pelo `RangeLinks.routerName`, a posição dele pelo `LinkerActions.position`). Chave `item.wirelessautomate.linker.range.refusal.other_router`.
- **Ctrl + clique:** a de outro roteador não entra e conta num aviso `· 1 de outro roteador ficaram de fora` (chave `item.wirelessautomate.linker.range.same.other_router`, no padrão de `area.protected` e `dock.same.other_dock`). A clicada de outro roteador serve de modelo, como na Doca: ela fica de fora e as que se tocam com ela entram.
- **Ctrl + clique num grupo todo de outro dono** (nenhuma candidata, nenhuma já vinculada ao escolhido e alguma de outro dono; achado da revisão final): a clicada de outro roteador dá a recusa do clique simples (`refusal.other_router`, com o nome e a posição do dono); na Doca, a clicada de outra Doca dá a `dock.refusal.other_dock`. A presa clicada (a clicada não é de outro dono, mas as do grupo são) dá o aviso `range.same.none.taken`: `%s: as que se tocam já são de outro dono` / `%s: the touching ones already belong to another owner`, mais as contagens de sempre.
- **Fica como está:**
  - Uma máquina que já está em dois roteadores (mundos de hoje) continua nos dois. Nada é desfeito sozinho; um clique nela a tira do roteador escolhido, como sempre (o desvincular vem antes da recusa).
  - Pôr um roteador encostado numa máquina que já é de outro continua possível (o Vinculador não participa).
  - Doca e roteador continuam podendo dividir a mesma máquina (decisão de 10/11). Entre Docas a regra de um dono já existe (`DockLinks.otherDock`).
  - As outras recusas e a ordem delas não mudam. A de outro roteador vem depois das recusas da máquina (`machineRefusal`) e da do upgrade (`upgradeRefusal`: sem upgrade, raio 0), e antes do raio e do limite, na mesma ordem no clique e no Ctrl + clique.
  - `RouterBlockEntity.linkMachine` não muda: a regra é do Vinculador (testes, e2e e bench seguem vinculando direto).
- **Limite aceito:** um roteador **descarregado** não é visto (o mesmo da Doca). Uma máquina dele pode entrar em outro roteador.

## Regra 2: o Ctrl + clique segue as que se tocam

- A partir da máquina clicada, o Ctrl + clique segue pelas posições **do mesmo bloco** (o mesmo id) **encostadas pela face, pela aresta ou pela ponta** (os 26 vizinhos), em qualquer direção.
- **Vincular** (roteador e Doca): o grupo só cresce **dentro do raio** de quem vincula (o cubo de Chebyshev do roteador ou da Doca, cortado na altura do mundo) e só em **chunks carregados**. Uma posição do mesmo bloco fora do raio não entra e o grupo não continua por ela; um chunk descarregado conta em `area.unloaded`.
- O grupo **passa** pelas posições que não entram mas se tocam: as já vinculadas (contam em "já vinculadas"), a presa do roteador escolhido, as de outro lado da Doca, as de outro roteador ou de outra Doca, e as protegidas. Elas não entram, mas o grupo continua por elas.
- **Ordem com o limite:** da mais perto da clicada para a mais longe, pela ordem da busca em largura (camada por camada a partir da clicada). Antes era a mais perto do roteador.
- **Desvincular** (Ctrl + clique numa máquina já vinculada ao roteador escolhido, ou já no lado escolhido da Doca): tira **só as do grupo que se toca com a clicada** que estão vinculadas àquele roteador (ou àquele lado). O grupo do desvincular **não** tem o corte do raio (uma vinculada fora do raio, encostada, também sai), mas tem o teto de leituras e só lê chunks carregados; parado no teto, o aviso acrescenta `range.same.capped`.
- **Textos que mudam** (pt e en):
  - `range.same.none`: `%s: todas as que se tocam já estão vinculadas` / `%s: all the touching ones are already linked`;
  - `range.same.none.capped`: `%s: nenhuma nova no grupo lido` / `%s: no new ones in the part of the group read`;
  - `range.same.capped`: `o resto do grupo não foi procurado` / `the rest of the group was not searched`.
  Os textos de desvinculou não mudam. O de vinculou perde a variante "Vinculou 32 de 47" (decisão do dono, 13/11, depois da execução): a busca para numa além das vagas e não sabe o tamanho do grupo, então com o limite o aviso é `Vinculou 32 Fornalhas · limite do tier`; as chaves `linked_of` e `linked_of.one` do roteador e da Doca saíram.
- O que não muda: o gesto no cliente (`LinkerSameBlockInput`), o pacote (`LinkSameBlockPayload`), o descanso de 10 ticks, as conferências do servidor e as recusas do clicado.

## Custo

- **Por tick:** zero. Nada entra no motor, no `RemoteIndex` nem no mixin do `setChanged`.
- **Índice de donos** (`range/OwnerIndex`): as posições de todas as máquinas vinculadas dos roteadores carregados, ativas ou não, como o `DockIndex` já faz para as Docas. Refeito só quando as remotas do roteador mudam (vincular, desvincular, carga do NBT) e na carga do roteador; tirado na descarga e na quebra. Memória: uma entrada por máquina vinculada de roteador carregado, com o dono direto no mapa (uma lista só na posição com dois ou mais donos), mais a posição no registro do dono (medida pelo cenário `range` do bench, que conta o heap retido).
- **Clique simples:** uma consulta no índice e a leitura dos 6 vizinhos (a presa de outro roteador), só depois das recusas baratas.
- **Ctrl + clique:** a busca em largura lê os 26 vizinhos de cada posição do grupo (um `getBlockState` pela seção do chunk, sem block entity e sem capability), com o teto `SameBlockScan.MAX_READS` (131.072 leituras); para cedo quando já há candidatas para as vagas mais uma. Antes era o cubo inteiro do raio (até 131.072 posições lidas mesmo com um grupo pequeno). O outro dono é conferido na varredura, em cada posição do grupo que não está vinculada nem excluída (uma consulta no índice e, no roteador, os 6 vizinhos da presa); a capability e o evento de clique das proteções seguem conferidos só nas que de fato seriam vinculadas.

## Fora

- Ver os roteadores descarregados (um registro salvo dos vínculos).
- Desfazer os vínculos duplicados que já existem.
- Mudar a regra entre Doca e roteador.
