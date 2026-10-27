# Tela de Sinal, etapa 3: mais peças e dados (spec)

**Data:** 27/10/2026 (rascunho preparado durante a etapa 2; o dono aprova antes de executar). **Base:** a spec geral [`2026-10-27-tela-de-sinal-design.md`](2026-10-27-tela-de-sinal-design.md) (decisões 4, 6, 8 e "Gráficos"), a da etapa 2 e o mockup `docs/preview/tela-de-sinal.html` (seção "3 · As peças" e o painel de exemplo, que já usa Barra, Seta, Ícone e Gráfico).

## O que entra

1. As peças **Ícone de item**, **Barra**, **Medidor**, **Gráfico** e **Seta**, com as abas do editor para cada uma.
2. Os **armazenamentos do mod como dado**: Baú (estoque de um item, entra e sai por item, ocupação), Bateria, Tanque, Tanque Químico e Tanque de Source (carga, capacidade e fração), ligados à Tela pelo **Vinculador**.
3. O **relógio do jogo** como dado (hora do dia e dia), sem servidor.
4. Componentes prontos novos com as peças novas: "Item do Baú" (ícone + estoque + entra e sai), "Carga da Bateria" (barra em pé + %), "Entra e sai" (setas + valores), como no mockup.

## Decisões

1. **Ligar armazenamentos pelo Vinculador (decisão 8 da spec geral).** No modo Vincular, um clique numa Tela de Sinal a escolhe (componente novo `linker_tv`, um `GlobalPos`; escolher uma Tela esquece o roteador escolhido e escolher um roteador esquece a Tela, para o clique seguinte nunca ser ambíguo). Com uma Tela escolhida, o clique num Baú, Bateria ou Tanque do mod liga ou desliga esse armazenamento da Tela, com o resultado na action bar. Regras: **sem limite de distância e em qualquer dimensão** (pedido do dono em 27/10: a Tela pode mostrar um Baú de outra base ou do Nether), até `tv.maxLinks` armazenamentos por Tela (padrão 32), só quem edita a Tela (decisão 15 da spec geral). O jogador escolhe a Tela, vai até o armazenamento (a escolha fica no Vinculador, como a do roteador) e clica nele. O tooltip do Vinculador mostra a Tela escolhida e a dimensão dela. A Tela guarda as ligações (`links`, lista de `GlobalPos`).
2. **Fontes de dado.** A ligação (`TvBinding`) passa a ter uma fonte: `network` (a de hoje: rede × tipo × métrica), `storage` (posição de um armazenamento ligado × métrica do armazenamento × item opcional) e `clock` (métrica do relógio). O NBT antigo sem `source` lê como `network`, sem migração.
3. **Métricas de armazenamento (todas para Baú, Bateria e os três Tanques):** `stored` (quantidade: no Baú, o total ou o do item escolhido), `capacity`, `fill` (0 a 1), `in`, `out` e `balance` (entra menos sai; por segundo, na janela de 5 s como os Fluxos: no Baú, o total ou o do item escolhido; na Bateria em FE/s; nos Tanques em mB/s ou Source/s). Pedido do dono em 27/10: entra e sai em todo armazenamento, não só por item no Baú. Armazenamento removido ou desligado da Tela: "—". **Armazenamento num chunk descarregado** (longe ou em outra dimensão): o servidor nunca carrega o chunk para ler; a Tela mostra o último número lido, na cor Apagado do tema, até o chunk voltar (com o Upgrade de chunk loading de um roteador perto do Baú, ele fica sempre carregado e sempre ao vivo). O último número fica guardado na Tela (fora do NBT; depois de reiniciar o servidor, "—" até a primeira leitura).
4. **Contadores de entra e sai só do que alguma Tela vigia.** O total de um armazenamento (`ScalarStore` da Bateria e do Tanque de Source, `KeyedStorage` dos Baús e Tanques) ganha dois `long` de entra e sai, somados só enquanto o armazenamento está vigiado (um `boolean`). Por item no Baú: Cada `KeyedStorage` de itens guarda um conjunto pequeno de itens vigiados; o `insert` e o `extract` reais (não simulados) somam num contador da própria entrada só quando ela está vigiada (um `boolean` na entrada, sem busca extra no caminho quente). O `TvManager`, na volta da Tela, registra os itens vigiados nos Baús ligados e lê os contadores; a vigia some quando nenhuma Tela perto pede (o mesmo critério de "alguém perto" da etapa 1). Sem Tela vigiando, o Baú não faz nada a mais.
5. **Escolher o item:** na aba Dado, com um Baú escolhido, uma grade com os itens que estão nele (o servidor manda a lista dos tipos do Baú, até 256, só com o editor aberto e só quando pedida), clique escolhe; também arrastar do inventário ou do JEI para a grade.
6. **Ícone de item:** o item renderizado como no inventário (bloco em 3D, item chapado, com o brilho de encantamento), sem nome nem contagem (o jogador põe um Valor ou um Texto se quiser). Desenhado na textura da Tela pelo `ItemRenderer` no redesenho, no tamanho da peça (quadrada, de 8 a 128 px de desenho).
7. **Barra:** horizontal ou vertical, sentido (cresce para a direita ou esquerda, para cima ou baixo), mínimo e máximo (0 e 0 = do dado: `fill` usa 0 a 1, os outros o máximo visto na janela), cor da trilha e do cheio, cantos, borda; as regras pintam o cheio.
8. **Medidor:** arco com abertura (90 a 360°, padrão 270), espessura, trilha e cheio, mínimo e máximo como a Barra; desenhado em segmentos no redesenho (sem textura nova).
9. **Gráfico:** linha ou colunas, janela de 1 a 10 minutos, área cheia opcional, escala automática ou mínimo e máximo; o histórico é do cliente, um número por segundo por ligação usada por algum Gráfico visível, até 600 (spec geral, "Gráficos"); some quando a Tela é liberada. A Tela com Gráfico redesenha uma vez por segundo enquanto estiver à vista (só ela).
10. **Seta:** ▲, ▼ ou ▬ pelo sinal do dado (com zona morta configurável, padrão 0), cores próprias para subir, descer e parado (do tema por padrão: Alta, Baixa, Apagado).
11. **Relógio:** `time` (hora do dia do mundo, 00:00 a 23:59, como no relógio do jogo) e `day` (dia do mundo); lido no cliente, sem pacote; a Tela com relógio redesenha quando o minuto do jogo muda e ela está à vista.
12. **Guardado na rede** (pedido do dono em 27/10): as métricas novas de rede `held` e `held_capacity` (e a fração `held_fill`) somam o conteúdo e a capacidade de todas as máquinas em modo **Armazém** daquela rede e tipo (uma Bateria, um Baú, um tanque de outro mod, qualquer máquina presa ou vinculada em Armazém). Lidas pelo `FlowSampler` na amostra que ele já faz, uma vez por volta, pelos caches de capability das portas (sem busca nova), e só quando alguma Tela pede essa métrica daquela rede (sem Tela, a amostra não lê nada a mais). Máquina em chunk descarregado fica de fora da soma.
13. *(Removida pelo dono em 27/10: "um item pela rede" fica fora; mexeria no caminho quente de itens.)*
14. **Acesso na hora de mostrar:** armazenamento ligado vale para quem olha (foi ligado por quem edita, que estava perto e tinha permissão); o servidor só confere que a ligação ainda existe e o bloco é um armazenamento do mod.

## O que mudou na execução

- A decisão 13 (um item pela rede) foi removida pelo dono e fica fora.
- Entrou o "guardado na rede" (decisão 12) e a busca na grade de itens do Baú ligado, com o arrastar do JEI para a grade e o campo do Ícone.
- Entraram os layouts da Tela inteira: quatro prontos (Energia, Armazenamento, Rede e Relógio) e os do jogador, guardados na biblioteca (campo de nome, Salvar layout atual, Remover em dois cliques), no seletor da base do editor.
- Os componentes prontos novos da decisão do mockup ficaram para depois dos layouts prontos; o guia cobre tudo isto (páginas da Tela de Sinal e do Vinculador).

## Modelo e chaves

- `TvPieceType`: `icon`, `bar`, `gauge`, `graph`, `arrow` (as três de antes não mudam).
- Métricas novas de rede: `held`, `held_capacity`, `held_fill`.
- `TvBinding`: campo opcional `source` (`network` padrão, `storage`, `clock`), `pos` (storage), `item` (opcional, só de armazenamento: o `ItemStack` sem contagem, pelo codec do vanilla), e as métricas novas num enum próprio por fonte (`TvStorageMetric`, `TvClockMetric`).
- `TvStyle`/peça: os campos das peças novas, todos opcionais: `direction`, `reverse`, `min`, `max`, `track`, `sweep`, `thickness`, `window`, `graphMode`, `area`, `deadZone`, `upColor`, `downColor`, `flatColor`, `item` (do Ícone).
- `TvBlockEntity`: `links` (lista de `GlobalPos`).
- Componente novo `linker_tv` (`GlobalPos`).
- Config do servidor `tv`: `maxLinks` (padrão 32).
- Payloads novos: `TvStorageContentsPayload` (servidor → cliente, os itens de um Baú ligado, com o editor aberto) e o pedido dele pelo `TvLibraryActionPayload` ou um irmão. `ModPayloads.VERSION` sobe para `"37"`.

## Custo

- **Servidor:** Baú sem Tela vigiando: zero a mais. Com vigia: um `boolean` lido no `insert`/`extract` e um `long +=`. O `TvManager` lê os armazenamentos ligados só na volta da Tela, com alguém perto, só os carregados (`server.getLevel(dimensão)` e `level.isLoaded(pos)`, nunca forçando o chunk), pela posição guardada (sem busca de capability por tick: o block entity do mod é lido direto).
- **Rede:** os números novos vão no mesmo `TvValuesPayload`; a lista de itens do Baú só com o editor aberto e pedida.
- **Cliente:** Ícone, Barra, Medidor e Seta só no redesenho. Gráfico e relógio redesenham por tempo (1 por segundo, e por minuto do jogo), só a Tela à vista que os tem. O histórico do Gráfico é um `double[600]` circular por ligação usada.

## Fora

Um item (ou fluido) pela rede; storages de outros mods (Sophisticated, AE2); itens por tag; fórmulas entre dados; dados do Ressonador.
