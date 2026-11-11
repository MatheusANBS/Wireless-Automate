# Pendências

O que falta fazer, por área (as mesmas do [mapa do código](../mapa/)). Um item por linha, começando pelo que falta, com a data em que surgiu. **(dono)** marca o que depende do dono. Quando um item é feito, ele **sai daqui** (o histórico do mês registra). Na dúvida se algo ainda vale, ponha em "A conferir", não apague.

## Núcleo, config e comandos

- `/wa face ... network none`: o literal `none` ganha do nome, então uma rede chamada "none" não é aceita. (12/10)
- Tirar o contador de recargas de teste do `ConfigReloadListener` (classe de produção) e levar para `gametest`. (16/10)

## Roteador, Vinculador e Configurador

- `RouterScreen` (644) e `PanelLayout` (636) estão em aviso de tamanho (tela: alvo 500, aviso 600). O `NodePanel` está em ~600, de propósito: o resto são os ganchos do e2e. (30/10)
- `RouterBlockEntity` em 977 linhas: o próximo método exige dividir antes (`RouterRange`, `RouterTargets` ou uma parte nova). (16/10)

## Motor e redes

- **(dono)** Decidir se a saída do Ressonador deve ser suavizada (hoje o lote de 20 ticks sai num tick só). (28/10)
- **(dono)** Máquina que chama `setChanged` a cada tick (faladeira) acorda as portas dela a cada tick: a origem nunca dorme e os destinos re-simulam os recusados a cada tick (A13 do backlog da `wa-performance`). Decidir se o aviso repetido sem mudança útil passa a ser desconfiado (ver as perguntas de 08/11 no histórico). (08/11)
- Bench: cenário com máquina de duas saídas (uma enchendo acima da vazão do tier) e tanque seletivo, para os M7 e M8 do backlog terem número além dos GameTests. (08/11)
- Bench: um cenário de destino seletivo (Baú do mod com milhares de tipos diante de máquinas que recusam a maioria); hoje só os GameTests medem. (02/11)
- Avaliar se o orçamento padrão de 1 ms deve subir em servidores com folga: com 1.000 máquinas ou no Sophisticated, o mod deixa de 14% a 55% da vazão para trás (`docs/benchmark-logistics-network.md`). (bench comparativo)
- Limites conhecidos: um fluido por tanque por visita; inserção em inventário grande sem índice de slots com espaço; sem custo medido por vizinho.
- Teste automático do callback que limpa os tickets de chunk ao recarregar o mundo.

## Roteador de alcance

- Teste à mão do Afinador do Configurador (etapa 3, linha de 02/11 do histórico): a pintura nas faces, o segundo clique, Shift no grupo, segurar o botão (não confirma nem apaga ao escorregar a mira para o chão ou o ar; com a tecla solta, uma tocha na mão secundária repete num bloco comum, e segurada desde uma máquina ela também espera a tecla soltar), mirar outro bloco por 2 s e clicar de novo (análise nova), e máquinas de outros mods (Mekanism, Sophisticated, uma máquina só de energia, que deve receber Insere). (02/11, 03/11)
- Teste à mão da etapa 4 do alcance (linha de 02/11 do histórico): a máquina trocada parando de receber na hora. (02/11)
- Teste à mão do Ctrl + clique do Vinculador (linha do topo de 02/11): uma fileira de fornalhas, um bloco de outro mod ("7 × Nome"), segurar o botão, o limite do tier; correndo com o Ctrl de correr o clique é normal, e na terra nada acontece (03/11). (02/11)

- Pausar grupo. (11/10)
- Presa no grupo, menores da revisão final: o grupo "×0" segue contando nas redes do nó; sem o Upgrade de Alcance, com remotas guardadas no mesmo grupo, editar a presa cria uma exceção que a tela não mostra; `setNetworkAll` com rede cria o membro numa presa nunca configurada; o slot de cartão da presa com exceção muda o grupo inteiro sem aviso;  destino "quase cheio" de pilhas parciais de outros itens entra no caminho das recusas (medir um cenário `partialfull`). (02/11)
- Página própria no guia (hoje é uma seção da página do Roteador). (11/10)
- **(dono)** Fechar as perguntas da spec: nome do item, vazão por máquina, tabela de raio e limite, Básico sem alcance; a receita e a textura seguem provisórias. (11/10)
- Conferir os achados menores da revisão da etapa 1 (`docs/superpowers/reviews/2026-10-11-roteador-de-alcance-etapa-1.md`) que a etapa 4 não cobriu (ela resolveu o "7/32", os caches na recarga da config, o `unlink` sem `isClientSide` e o grupo de id repetido no NBT). (11/10)

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

- GameTests que faltam: racha no meio do ciclo de recarga, sync da contagem do catalisador e o `onRemove` largando os 17 slots. (26/10)
- A suíte e2e `ressonador-desempenho` repete as asserções; atualizar o spec da animação. (26/10)

## Tela de Sinal

- A busca da grade de itens casa o nome em inglês do servidor (`TvStorageContents`/`TvItemSearch`): em pt_br só acha pelo id. (27/10)
- `FlowSampler.java` em 809 linhas (aviso de servidor). (27/10)
- Visuais: no layout Rede 4×3 o "Chemicals" encosta na seta; medidor em 0% e gráfico quase vazio com a rede parada; contraste baixo cartão × fundo na Porcelana. (27/10)
- Capturas de conferência do Ícone no e2e (baú, vidro, relógio, espada encantada, slime, diamante). (27/10)
- Para quando a biblioteca for reescrita: o `SAVE_ART` codifica a biblioteca inteira a cada pedido, e as artes fora da cota por jogador. (29/10)
- Refazer as imagens antigas da vitrine, que podem mostrar a parede da Tela ao fundo. (29/10)

## Livro-guia

- **(dono)** Testar a tecla G com o JEI de verdade (a lista, os favoritos e a tela de receitas; com a busca do JEI com foco ela não deve abrir) e abrir o livro a partir de um contêiner (baú, inventário); a ponte compila e o e2e cobre o inventário, mas sem o JEI. (30/10)
- **(dono)** Testar no jogo as cenas que giram (arrasto, roda, clique duplo e a dica das anotações), principalmente no Ressonador (a Porta de Energia) e nas redes. (30/10)
- Depois, se o dono quiser: som e animação de folha virando; tabelas com os números do servidor. (30/10)
- Dica da folha de rosto com a tecla do livro desassociada diz "Press Not bound…" (a dica do item já se esconde nesse caso). (30/10)
- e2e do livro: o passo do baú põe o baú em `offset(2,0,0)` sem conferir se o lugar está vazio e depois põe ar ali; o ramo "contêiner fechado pelo servidor" do `onClose` sem passo próprio. (30/10)
- Refazer a `docs/curseforge/feature-7-guide.png` pela vitrine (`wa-curseforge-imagens`), que ainda mostra o livro do GuideME, e trocar o link na descrição. (30/10)

## Integrações

- **Doca de Padrões, etapas 1 a 4** (ramo `ccr-71b175d3-weymk6`, 11/11): teste à mão no jogo com o AE2 (a linha de fornalhas, o Ctrl + clique, a tela, o cubo, um pedido grande pela CPU com o resultado direto na rede, o aviso da trava "até o resultado") e com o RS2 (o Autocrafter num lado, Autocrafters encadeados, o resultado na rede), e a animação (o laço de trabalho quando entrega, o pacote pela vigia do lado certo, o cristal aceso à noite); da etapa 4, um padrão do Mekanism com item, fluido e químico pela CPU do AE2 (com o Applied Mekanistics) e pelo Autocrafter do RS2 (com a integração), e o químico de volta à rede. (11/11)
- Doca: o Applied Mekanistics e a Refined Storage Mekanism Integration não estão no `runClient` (só na run `gameTestServerDockChemicals`); para o teste à mão com químicos, pô-los no `clientMods` do `build.gradle` ou testar num pack. (11/11)
- Doca: o químico que o destino recusa de verdade e a máquina não aceita de volta (uma saída que só dá) se perde e vai para o log, como no `ChemicalTransfer` e no fluido. (11/11)
- Doca, e2e: na variante `pt`, a captura `doca-config-pt` falha a conferência "nenhum texto cortado": a nota "2 fora do alcance" do Leste sai cortada ("2 fora do alca…") ao lado de "Tire a trava do provedor". Vem da etapa 2 (os textos não mudaram na etapa 4) e só aparece com `--variants pt`; encurtar um dos dois textos ou dar mais largura à nota. (11/11)
- Doca: a animação não foi vista com shaderpack (o cliente de dev não tem o Iris) nem medida numa GPU (o `doca-desempenho` roda por software, sob o Xvfb); conferir num cliente de verdade com 50 Docas à vista. (11/11)
- Doca, da revisão final da etapa 3 (não conferidos no dev): com o Iris os BERs também vão ao passe de sombra (o custo por Doca quase dobra) e a luz cheia do cristal e dos fragmentos chega como lightmap 15/15, não como emissivo (alguns shaderpacks a apagam um pouco); o `entitySolid` no atlas de blocos amostra sem mipmap, então de longe os anéis podem cintilar (o normal dos BERs do vanilla); no item na mão e no inventário, o circuito animado depende de o Embeddium marcar o sprite como ativo ("Animate Only Visible Textures"). (11/11)
- Doca: a unicidade não vê uma Doca **descarregada**: uma máquina dela pode entrar em outra Doca (limite aceito pelo dono). Com a mesma máquina em duas Docas, o resultado pode voltar pela rede do provedor da outra; em redes ME diferentes o pedido não termina. (11/11)
- Doca: o fluido que o provedor recusa na execução depois de aceitar na simulação volta à máquina pela face Extrai, e o que ela não aceitar se perde (raro: um handler que mente na simulação). (11/11)
- Doca e RS2: o `ItemResource` e o `FluidResource` vêm da `refinedstorage-common` (interna, `@API(INTERNAL)`), não da API publicada; podem mudar entre versões do RS2. Conferir a cada versão nova do RS2 (o `versionRange` começa na 2.0.0-milestone.4.14, conferida até a 2.0.9). (11/11)
- Doca e RS2: a dica do provedor mostra "Autocrafter", sem o "× N" de uma corrente: contar a corrente pede a direção do Autocrafter, que está numa classe interna do RS2 (`AbstractDirectionalBlock`), fora da `common-api`. (11/11)
- Doca: o Ctrl + clique no raio 96 (Ultimate) custa de 2,4 a 3,9 ms por clique; avaliar baixar o teto de leitura só para a Doca. (11/11)
- Doca: com pendente, o lado guarda o bloco mesmo sem máquinas (para trocar de máquina, vincular uma do bloco antigo ou quebrar a Doca); decidir se o pendente vai ao chão ao desvincular a última. (11/11)
- **(dono)** Doca: um químico guardado cru (o item da Doca com pendente de químico num mundo sem o Mekanism, ou com o químico de um mod removido) nunca é descartado e prende o lado ao bloco mesmo sem máquinas, mas nada na tela explica por quê (o `hasPending` é falso; só o `keepsPending` o vê). Sugestão da revisão final da etapa 4: uma linha de estado ou dica ("Químico guardado sem o Mekanism") e, se o dono quiser, um jeito explícito de descartar a entrada. Decidir. (11/11)
- **(dono)** Doca: a receita (fica para o balanceamento das receitas). (10/11)
- `RouterBlockEntity` em 999 linhas: qualquer linha nova exige dividir antes. (11/11)


## Testes, e2e e bench

- O passo "Baú: filtro, adicionar a tag" do e2e falha às vezes no e2e completo ("Adicionar está desativado"): corrida de 1 tick entre marcar a tag e clicar em Adicionar; sozinho (`--only armazenamento`) passa. (02/11)
- O e2e completo padrão passa de 600 s neste ambiente (~615 s): sem `E2E_TIMEOUT=1200` ele estoura na suíte `guia` com tudo `OK` até ali. (02/11)
- GameTest próprio para `/wa network invite` e `team`. (22/10)
- Rodar as variantes `pt` e `escuro` do e2e (não rodaram na 2.12 nem na 2.13, por decisão do dono). (29/10)
- O passo "alcance: tema escuro" do e2e falhou uma vez sem causa achada; ficar de olho. (22/10)
- Bench comparativo: medir nós com vários canais do lado de lá e uma base real (máquinas do Mekanism).

## Docs, scripts e publicação

- Changelog da próxima versão: uma remota vinculada ao lado da presa sozinha herda na hora a config dela (inclusive Extrair e a rede); avisar no changelog. (03/11)
- **(dono)** A 2.14.0 e a 2.15.1 não têm release no GitHub (só a v2.15.0 e, agora, a v2.16.0): decidir se vale criar as duas a posteriori. (08/11)
- **(dono)** Quando o Modrinth aprovar, pôr o link no `README.md`, no `README.en.md` e na `descricao.md`. (30/10)
- **(dono)** Decidir o destino da worktree `C:/wab` (fora do repositório, com 2 arquivos mudados). (30/10)
- Portes (`docs/portes.md`): investigar o 26.1 NeoForge (ATM11); o 1.20.1 Forge espera o ok do dono e a decisão do Mekanism (só gás ou os quatro químicos). (10/10)

## Ideias (pedidos do dono, não começados)

- Lixeira do mod: um bloco que some com itens, fluidos, energia, químicos e Source, para não depender do Trash Cans. Visual aprovado em 10/11 (refeito no mesmo dia com casco de porcelana, nos moldes da Doca) e spec pronta (`docs/superpowers/specs/2026-11-10-lixeira-design.md`, modelo em `docs/preview/lixeira.bbmodel`); a mecânica foi respondida pelo dono em 10/11 (id `trash`, o laço engolindo enquanto descarta, tela com liga e desliga por tipo, receita no balanceamento, atlas pintado, luz do Ultimate); o mockup da tela foi aprovado (layout de teclas, `docs/preview/lixeira-tela.html`), ela nasce com os cinco tipos ligados e a colisão segue o desenho; o plano da etapa 1 (mecânica, bloco e tela) está em `docs/superpowers/plans/2026-11-10-lixeira-etapa-1.md`, à espera do ok do dono para executar; a etapa 2 (o anel, o cristal e o laço engolindo no renderer) e a receita vêm depois. (14/10)
- Atualizar sozinho a máquina que trocou de bloco (a remota já tem o ⟳ Atualizar): ver se a máquina presa precisa do mesmo e se dá para fazer sozinho quando o bloco novo é da mesma família. (14/10)
- Integração com o AE2 e o RS2: proposta em `docs/superpowers/specs/2026-11-10-ae2-rs2-proposta.md`. A Doca de Padrões saiu dela (etapas 1 a 4 feitas, spec em `docs/superpowers/specs/2026-11-10-doca-de-padroes-design.md`); os atalhos de vazão e as outras features seguem como proposta. (10/11)
- Balanceamento das receitas.

## A conferir

Itens antigos que não deu para confirmar se ainda valem. Confira no código antes de fazer; se não valer mais, apague.

- **(dono)** Etapa 2 da remodelagem, adiada em 14/10: boa parte foi superada (o Vinculador perdeu redes e abas na 2.7.0); D3 (perfil copiado com os do alcance) e D6 ("Perfil" no lugar de grupo) ainda interessam?
- O Igualar da barra Editando cobre o "Usar como perfil" da spec do alcance?
- Menores da rede por face (12/10): girar a máquina com a tela aberta não atualiza a frente; "Lados por máquina" com uma máquina só; a vazão por rede no Tablet soma o total do tipo; o redstone suja todas as redes do nó.
- Menores de 17 a 19/10: o aviso do Tablet não cobre máquina só de químico ou Source nem remota fora do raio; o clique numa máquina mantém a busca salva; `RenameRouterPayload.clean` ainda no pacote `packet`; `ListTexts.stateLine` montado a cada quadro; a marca "exceção" que some na linha da máquina.
- Itens visuais da revisão da etapa 3 da Tela (o "C" de 27/10): não aparecem corrigidos em nenhum commit.
- A vazão na tela do roteador conta só o que ele moveu como origem (os Fluxos do Tablet podem ter resolvido).
