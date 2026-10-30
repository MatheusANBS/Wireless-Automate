# Pendências

O que falta fazer, por área (as mesmas do [mapa do código](../mapa/)). Um item por linha, começando pelo que falta, com a data em que surgiu. **(dono)** marca o que depende do dono. Quando um item é feito, ele **sai daqui** (o histórico do mês registra). Na dúvida se algo ainda vale, ponha em "A conferir", não apague.

## Núcleo, config e comandos

- Pôr em inglês os comandos de moderação de imagens da Tela (`/wa tela imagem remover|banir|desbanir`, `/wa tela imagens`), como os outros. (29/10)
- `/wa face ... network none`: o literal `none` ganha do nome, então uma rede chamada "none" não é aceita. (12/10)
- Tirar o contador de recargas de teste do `ConfigReloadListener` (classe de produção) e levar para `gametest`. (16/10)

## Roteador, Vinculador e Configurador

- Singular em "1 máquinas" no Vinculador (`LinkerText`, chaves `outcome.linked`, `outcome.unlinked` e `area.ready.with_linked`). (22/10)
- Dividir o `NodePanel` (822 linhas, acima do limite de tela pelo `medir.py`). `RouterScreen` (644) e `PanelLayout` (636) estão em aviso. (30/10)
- `RouterBlockEntity` em 977 linhas: o próximo método exige dividir antes (`RouterRange`, `RouterTargets` ou uma parte nova). (16/10)
- **(dono)** Testar as três receitas do anel numa bancada de outro mod, o Shift + clique com cartão de outra origem e o aviso de override antigo de `tier_core_*`. A variante `tier_core_ultimate_atm` (ATM sem o All The Tweaks) não roda em nenhuma run. (12/10)

## Motor e redes

- **(dono)** Decidir se a saída do Ressonador deve ser suavizada (hoje o lote de 20 ticks sai num tick só). (28/10)
- Ver se os fluidos pedem o mesmo tratamento da recusa por tipo (Salvaging Table do Apotheosis). (11/10)
- Avaliar se o orçamento padrão de 1 ms deve subir em servidores com folga: com 1.000 máquinas ou no Sophisticated, o mod deixa de 14% a 55% da vazão para trás (`docs/benchmark-logistics-network.md`). (bench comparativo)
- Limites conhecidos: um fluido por tanque por visita; inserção em inventário grande sem índice de slots com espaço; sem custo medido por vizinho.
- Teste automático do callback que limpa os tickets de chunk ao recarregar o mundo.

## Roteador de alcance

- Pausar grupo e os perfis do alcance no Configurador (o `RouterPreset` só leva a máquina presa). (11/10)
- Página própria no guia (hoje é uma seção da página do Roteador). (11/10)
- **(dono)** Fechar as perguntas da spec: nome do item, vazão por máquina, tabela de raio e limite, Básico sem alcance; a receita e a textura seguem provisórias. (11/10)
- Conferir os 18 achados menores da revisão da etapa 1 (`docs/superpowers/reviews/2026-10-11-roteador-de-alcance-etapa-1.md`). (11/10)

## Tablet

- O "via" de um roteador sem nome usa o bloco da presa ou "roteador": o servidor não manda o nome padrão. (20/10)
- Na tela mínima (420 × 280), a segunda linha da máquina corta as coordenadas. (20/10)
- Copiar o perfil de uma máquina ou grupo para colar noutra. (20/10)
- **(dono)** Decidir se a linha do grupo mostra a soma da vazão (o mockup mostra, a spec não). (19/10)
- O operador recebe a lista de acesso de todas as redes da página (melhor só a da rede escolhida); o `online` e a contagem de membros só atualizam quando o snapshot muda por outro motivo. (22/10)
- Menores da etapa 3b: blocos marcados que continuam marcados ao voltar a Roteadores; "Nova rede Novo grupo" colados; o `machineCount` do botão pode contar duas vezes; desmarcar uma máquina de um bloco marcado tira as escondidas sem aviso; a ordem dos bits do `NetworkOrder` depende do `LoadedTypes.LIST`. (20/10)
- Mapa com um quad por pixel de traço; se pesar, o `MapLines` passa a dar segmentos. (19/10)

## Armazenamentos

- Os armazenamentos no `DevScreenshot` (não há captura de Baú, Tanque nem Bateria).
- **(dono)** Testar os recipientes de químico na tela dos armazenamentos com o Mekanism de verdade.

## Ressonador

- **(dono)** Decidir o passo 5 do spec da câmara: arcos finos dos cristais e o estalo sonoro (a sugestão é tirar, a não ser pelo som). (23/10)

## Reparador

- Tirar o código morto: `PEDESTAL_GLOW_Y` (sem uso) e `RepairFrame.light` (gravado, nunca lido). (26/10)
- Tirar o `new AABB` por quadro do `RepairerRenderer.getRenderBoundingBox`. (26/10)
- GameTests que faltam: racha no meio do ciclo de recarga, sync da contagem do catalisador e o `onRemove` largando os 17 slots. (26/10)
- A suíte e2e `ressonador-desempenho` repete as asserções; atualizar o spec da animação. (26/10)

## Tela de Sinal

- A busca da grade de itens casa o nome em inglês do servidor (`TvStorageContents`/`TvItemSearch`): em pt_br só acha pelo id. (27/10)
- `FlowSampler.java` em 809 linhas (aviso de servidor). (27/10)
- Visuais: no layout Rede 4×3 o "Chemicals" encosta na seta; medidor em 0% e gráfico quase vazio com a rede parada; contraste baixo cartão × fundo na Porcelana. (27/10)
- Capturas de conferência do Ícone no e2e (baú, vidro, relógio, espada encantada, slime, diamante). (27/10)
- Para quando a biblioteca for reescrita: o `SAVE_ART` codifica a biblioteca inteira a cada pedido, e as artes fora da cota por jogador. (29/10)
- Refazer as imagens antigas da vitrine, que podem mostrar a parede da Tela ao fundo. (29/10)
- **(dono)** Testar o Ícone animado num modpack com o Sodium (`compat/sodium/SodiumSprites`). (28/10)

## Integrações

- **(dono)** Testar à mão o arrastar e o "+" do JEI real na Tela, no Baú e na Mesa de Trabalho (os GameTests cobrem só o servidor). (30/10)
- **(dono)** Testar os químicos com máquinas reais do Mekanism (faces ligadas pela ferramenta dele).
- **(dono)** Testar num ATM10 real: receitas do ATM no JEI, tooltip dos cartões, cores e a migração da config num servidor que vinha da 1.1.
- **(dono)** Testar o acesso às redes com dois jogadores reais (as sugestões dependem do `usercache.json`). (22/10)

## Testes, e2e e bench

- GameTest próprio para `/wa network invite` e `team`. (22/10)
- Rodar as variantes `pt` e `escuro` do e2e (não rodaram na 2.12 nem na 2.13, por decisão do dono). (29/10)
- O passo "alcance: tema escuro" do e2e falhou uma vez sem causa achada; ficar de olho. (22/10)
- Bench comparativo: medir nós com vários canais do lado de lá e uma base real (máquinas do Mekanism).

## Docs, scripts e publicação

- O `gerar_texturas.py` inteiro apaga `textures/gui/tv/*` (136 arquivos), porque a limpeza varre a pasta `gui` toda: tirar `gui/tv` da limpeza. (30/10)
- **(dono)** Subir o jar e o changelog da 2.13.0 no CurseForge e no Modrinth. (30/10)
- **(dono)** Quando o Modrinth aprovar, pôr o link no `README.md`, no `README.en.md` e na `descricao.md`. (30/10)
- **(dono)** Ler o guia novo no jogo e dizer se algum corte foi longe demais (Tela de Sinal: nomes das cores do tema, girar e duplicar arte; Baú: Devolver da Mesa, rodinha na Compactação; Tablet: Tab que completa; Roteador: Tirar redes). (30/10)
- **(dono)** Decidir o destino da worktree `C:/wab` (fora do repositório, com 2 arquivos mudados). (30/10)
- Portes (`docs/portes.md`): investigar o 26.1 NeoForge (ATM11); o 1.20.1 Forge espera o ok do dono e a decisão do Mekanism (só gás ou os quatro químicos). (10/10)

## Ideias (pedidos do dono, não começados)

- Lixeira do mod: um bloco que some com itens, fluidos, energia, químicos e Source, para não depender do Trash Cans. (14/10)
- Atualizar sozinho a máquina que trocou de bloco (a remota já tem o ⟳ Atualizar): ver se a máquina presa precisa do mesmo e se dá para fazer sozinho quando o bloco novo é da mesma família. (14/10)
- Atalhos de AE2 e RS2.
- Balanceamento das receitas.

## A conferir

Itens antigos que não deu para confirmar se ainda valem. Confira no código antes de fazer; se não valer mais, apague.

- **(dono)** Etapa 2 da remodelagem, adiada em 14/10: boa parte foi superada (o Vinculador perdeu redes e abas na 2.7.0); D3 (perfil copiado com os do alcance) e D6 ("Perfil" no lugar de grupo) ainda interessam?
- O Igualar da barra Editando cobre o "Usar como perfil" da spec do alcance?
- Menores da rede por face (12/10): girar a máquina com a tela aberta não atualiza a frente; "Lados por máquina" com uma máquina só; a vazão por rede no Tablet soma o total do tipo; o redstone suja todas as redes do nó.
- Menores de 17 a 19/10: o aviso do Tablet não cobre máquina só de químico ou Source nem remota fora do raio; o clique numa máquina mantém a busca salva; `RenameRouterPayload.clean` ainda no pacote `packet`; `ListTexts.stateLine` montado a cada quadro; a marca "exceção" que some na linha da máquina.
- Itens visuais da revisão da etapa 3 da Tela (o "C" de 27/10): não aparecem corrigidos em nenhum commit.
- A vazão na tela do roteador conta só o que ele moveu como origem (os Fluxos do Tablet podem ter resolvido).
