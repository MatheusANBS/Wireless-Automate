# Roteador de alcance: design

Estudo pedido pelo dono em 11/10/2026, a partir da sugestão do taccio3 no CurseForge ("fully wireless": um roteador ligado a máquinas no alcance, sem estar preso a cada uma). Mockup interativo: https://claude.ai/artifact/NALDuFUbJY7C6yyZKwbFBy (cópia em `docs/preview/alcance-remoto.html`).

**Estado:** o dono quer **testar no jogo antes de fechar os detalhes**. As decisões abaixo são as do estudo, com as recomendações aplicadas onde ele ainda não respondeu (marcadas com *provisório*). O que o teste mostrar muda esta spec antes da versão.

A versão "achatada" entre dois blocos, que o taccio3 também sugeriu, ficou fora a pedido do dono.

## Problema

Hoje cada roteador fica preso a uma máquina. Numa farm de pack grande (dezenas de trituradores, enriquecedores, fornalhas, geradores) isso pede um roteador por máquina e espaço em volta de cada uma, e configurar dezenas de roteadores. A ideia é um roteador que vincula várias máquinas no raio dele e as configura **por tipo de máquina**, não uma a uma.

## Resumo das decisões

1. **Upgrade de Alcance** (`range_upgrade`, *provisório*): item novo que entra num **segundo slot de upgrade** do roteador, ao lado do de chunk loading. Liga o modo remoto. Sem ele, o roteador é exatamente o de hoje.
2. **Máquinas vinculadas, nunca "tudo no raio"**: o roteador guarda uma lista de posições escolhidas pelo jogador com o Vinculador. A varredura de uma área roda uma vez, no clique de vincular. Por tick o motor só vê portas, como hoje.
3. **Raio e limite de máquinas por tier**, na config.
4. **Perfis por tipo de máquina**: as máquinas vinculadas do mesmo bloco formam um grupo, e o grupo tem um perfil (a configuração das faces). Configurar o perfil configura todas. Exceções por máquina quando precisar.
5. **Lados relativos à frente da máquina** nas máquinas remotas que têm frente.
6. **Sem chunk loading próprio** para as máquinas: quem usa FTB Chunks continua com ele. Máquina em chunk descarregado dorme e aparece com aviso.
7. **Vazão cheia por máquina** (*provisório*, recomendado): cada máquina vinculada recebe a vazão do tier, como se tivesse o próprio roteador. O custo vai na receita do upgrade.
8. **Acordar como a máquina presa** (decisão do dono, 11/10/2026): uma máquina remota não encosta no roteador, então o aviso de vizinho do `setChanged` dela não chega a ele. Um mixin em `Level.blockEntityChanged` confere a posição num conjunto das máquinas remotas vinculadas (`LongOpenHashSet` por nível, uma consulta por `setChanged`) e acorda as portas daquela máquina, com a mesma regra da presa (durante uma entrega, não acorda quem a abastece). A carga de um chunk acorda as máquinas vinculadas que estão nele. O teto de sono continua o de hoje (100 ticks); com o mixin, um gerador que enche acorda a porta na hora, sem esperar o backoff. É o primeiro mixin do mod.
9. **Decisões do plano da etapa 1** (dono, 11/10/2026): os **cartões ficam só no perfil do grupo** (uma exceção muda modo, prioridade, redstone e filtro embutido, e usa os cartões do grupo; copiar cartões duplicaria itens); **Esquerda e Direita são as de quem olha a frente da máquina**; a **varredura de área só considera blocos com block entity**.

## 1. Custo (por que não dá lag)

| O quê | Quando roda | Cresce com | Peso |
| --- | --- | --- | --- |
| Transferência | Por tick, dentro do orçamento (`tickBudgetMs`) | Faces configuradas das máquinas vinculadas | O mesmo de N roteadores comuns, um pouco menos (um nó só) |
| Conferir o raio | Ao vincular | Nada por tick | Zero |
| Varredura de área | Uma vez, no clique de Vincular | Volume, com teto em `linker.maxAreaVolume` | Um pico curto, uma vez |
| Capabilities | `BlockCapabilityCache` por máquina, face e tipo, criado na primeira consulta | Faces usadas | Como hoje |
| Máquina em chunk descarregado | A porta confere `level.isLoaded(pos)` antes da capability e dorme | Nada | Zero |
| Estado das máquinas | Só ao montar o snapshot da tela ou do Tablet | Máquinas do roteador aberto | Desprezível |
| Visor 3D | Cliente, com a tela aberta: **um bloco só** por vez | Nada | Igual a hoje |
| Linhas no mundo | Cliente, com o Vinculador na mão | Máquinas do roteador escolhido | Baixo |

O raio não pesa por tick. Pode ser grande.

## 2. Balanceamento

| Tier | Raio (blocos, distância de Chebyshev) | Máquinas vinculadas |
| --- | --- | --- |
| Básico | — (não aceita o upgrade) | 1 (a presa) |
| Avançado | 6 | 8 |
| Elite | 12 | 16 |
| Esmeralda | 16 | 32 |
| Allthemodium | 24 | 48 |
| Vibranium | 32 | 64 |
| Unobtainium | 48 | 96 |
| Ultimate | 64 | 128 |

Rebalanceado pelo dono em 11/10/2026: raio menor, com 64 blocos no máximo (quatro chunks para cada lado), e mais máquinas.

- Config, seção `tiers.<tier>`: chaves novas `machineRadius` (teto 64) e `maxMachines` (teto 512). A chave `range` que já existe é o alcance da **rede** e não muda.
- Valores padrão por arrays pela posição do `RouterTier`, como vazão e capacidade (um tier novo pede um valor em cada array).
- A máquina presa conta no limite? *Provisório:* **não**; o limite é das remotas, e a presa é sempre a primeira da lista.
- Mesma dimensão sempre (o `crossDimension` é da rede, não do vínculo).

## 3. Vincular no mundo (Vinculador, modo Máquinas)

O `LinkerMode` ganha `MACHINES` (Único → Área → Máquinas no Shift + clique no ar e nos botões da tela). Sem ferramenta nova.

| Gesto (modo Máquinas) | Efeito |
| --- | --- |
| Clique num roteador | Escolhe o roteador (componente novo no item, a posição e a dimensão). O alcance aparece no mundo |
| Clique numa máquina | Vincula ou desvincula essa máquina do roteador escolhido |
| Shift + clique em dois blocos | Marca os cantos da área (o mesmo componente da área de hoje) |
| Clique no ar | Abre a tela do Vinculador com a prévia e o botão Vincular |
| Shift + clique no ar | Próximo modo |

Regras do servidor (todas validadas no servidor, a cada pacote):
- Roteador com o Upgrade de Alcance, tier com raio > 0.
- Mesma dimensão, distância ≤ `machineRadius`, total ≤ `maxMachines`.
- Só blocos com alguma capability que o mod transporta. Na área, as caixas de tipo do Vinculador (`LinkerTabs`) filtram: entra quem tem capability de um tipo marcado.
- Só onde o jogador pode interagir (`mayInteract` e o evento de clique), para respeitar claims (FTB Chunks).
- Não vincula o próprio roteador, outro roteador nem uma máquina já vinculada a ele.
- Uma máquina pode estar vinculada a vários roteadores (como hoje dá para prender dois roteadores na mesma máquina).

Action bar: `Linha de Minério · 12/32 máquinas`; recusas dizem o motivo (`Fora do alcance: 21 blocos (raio 16)`, `Limite do tier Esmeralda: 32 máquinas`).

Tela do Vinculador no modo Máquinas: a coluna da direita mostra o roteador escolhido (nome, tier, raio, `7/32 máquinas`) e a prévia da área: na área com os tipos marcados, cabem no limite, passam do limite, fora do raio. O botão diz só o número (`Vincular 9`), com o detalhe no tooltip.

No mundo, com o Vinculador no modo Máquinas e um roteador escolhido (cliente, `AreaRenderer`): contorno do alcance na cor do tier, contorno das máquinas vinculadas e uma linha fina do roteador a cada uma. Sem o Vinculador na mão, nada.

Máquina recém-vinculada entra no grupo do tipo dela e já segue o perfil (seção 4): vincular é configurar.

## 4. Perfis por tipo de máquina

- **Grupo:** as máquinas remotas do mesmo bloco (id do registro) formam um grupo, por padrão. *Provisório:* separar e renomear grupos fica para a etapa 3. Uma máquina nova entra no primeiro grupo do tipo dela.
- **Perfil:** a configuração do grupo, por aba (tipo de recurso) e por lado: `FaceConfig` (modo, prioridade, redstone, filtro embutido) e os cartões. Mudar o perfil vale para todas as máquinas do grupo que não são exceção.
- **Exceção:** mudar uma máquina sozinha copia o perfil para ela e aplica a mudança. Se a configuração dela voltar a ser igual ao perfil, deixa de ser exceção. Botões: **Voltar ao perfil** (tira a exceção) e **Usar como perfil** (a configuração da máquina vira o perfil do grupo).
- **Seleção múltipla:** Shift + clique na lista escolhe várias máquinas do mesmo grupo; a mudança vale para as escolhidas (vira exceção nelas).
- **Pausar grupo** (etapa 3): desliga o grupo inteiro sem mexer nas faces.
- **A máquina presa** fica fora dos grupos: continua com a configuração de hoje, relativa ao `facing` + `spin` do roteador.

### Lados relativos à frente da máquina

Numa farm, as máquinas ficam viradas para lados diferentes. Nas remotas, os lados do perfil são **Frente, Trás, Esquerda, Direita, Cima e Baixo**, relativos à frente de cada máquina: a propriedade `HORIZONTAL_FACING` (ou `FACING`, só o horizontal) do `BlockState`. Bloco sem frente usa os lados absolutos (Norte, Sul, Leste, Oeste, Cima, Baixo). A troca de "Trás" para a face absoluta acontece ao montar a porta e de novo quando o estado do bloco muda (a capability invalida). *Provisório:* relativo como padrão; a tela tem a alternância Relativo/Absoluto só para mostrar.

## 5. Tela do roteador com o Upgrade de Alcance

Sem o upgrade, a tela não muda. Com ele:

- **Coluna da esquerda: lista agrupada.** Cabeçalho `Máquinas 63/128 · 5 tipos`, busca, e um item por grupo (`Triturador ×24`, com `1 exceção` e avisos), que abre (▸/▾) para as máquinas (`#3 · 12 blocos`, bolinha de estado, marca `exceção`). A máquina presa aparece no topo. Todo texto passa por `GuiText`.
- **Escolher o grupo** edita o perfil; o rodapé da coluna da direita diz `Vale para 23 de 24 · 1 exceção`. **Escolher uma máquina** mostra `segue o perfil` ou `exceção`. **Shift + clique** escolhe várias.
- **Visor:** um bloco por vez (o tipo do grupo, com blocos atrás indicando quantidade, ou a máquina escolhida), faces clicáveis como hoje e uma marca na frente da máquina. Modo **Mapa**: vista de cima 2D com o alcance, todas as máquinas, o grupo escolhido em destaque; clique escolhe.
- **Botões de face** com os nomes relativos (Cima, Frente, Dir., Baixo, Trás, Esq.) nas remotas.
- **Coluna da direita:** a de hoje (modo, filtro, cartões), mais o rodapé do alcance da mudança e os botões Voltar ao perfil / Usar como perfil (máquina) ou Pausar grupo (grupo, etapa 3).
- **Estados:** verde carregada; âmbar chunk descarregado ("Volta a mover quando o chunk carregar"); vermelho a máquina sumiu (bloco quebrado ou trocado por outro sem capability), com **Desvincular**. O mod não desvincula sozinho, porque a máquina pode voltar.

## 6. Tablet

- Lista: a linha do roteador ganha `12 máquinas · Itens, Energia` e os avisos (`1 em chunk descarregado · 1 sumiu`), em âmbar.
- Estatísticas: o resumo de avisos soma `N máquinas em chunk descarregado` e `N máquinas sumiram`, ao lado de "destinos cheios".
- O `NodeProbe` passa a contar as máquinas por estado. Só com a tela aberta.

## 7. Por dentro

- **`RouterBlockEntity`:** a máquina presa continua com as chaves de NBT de hoje. As remotas vão numa lista nova (`machines`: posição, grupo, exceção opcional) e os grupos noutra (`groups`: id do bloco, nome, perfil por tipo). Mundo antigo carrega igual; sem o upgrade a lista fica guardada, mas não vira porta.
- **Caches** por máquina remota, criados na primeira consulta, como os de hoje (`capability(...)`), com o `isLoaded` antes.
- **`Port`** ganha o índice da máquina (−1 ou 0 para a presa). Rotas, sono, backoff, round-robin, `EnergySplit` não mudam. O `isSameMachine` já compara a posição da máquina.
- **Vazão por máquina:** cada porta usa a vazão do tier, como hoje.
- **Mudar um perfil** é um `batchChanges` só (um aviso ao motor, não um por máquina).
- **`RouterSnapshot`:** grupos (perfil, contagem, exceções, avisos), máquinas (estado, distância, exceção) e o `BlockState` só da máquina escolhida. Continua indo só com a tela aberta.
- **Payloads:** escolher máquina/grupo na tela, mudar a configuração com alvo (perfil do grupo ou lista de máquinas), voltar ao perfil, usar como perfil, desvincular, vincular pelo Vinculador. `ModPayloads.VERSION` passa de `12` para `13`.
- **Configurador** (etapa 3): o preset leva os perfis por id de bloco; colar configura os grupos que batem e deixa os outros.
- **Item e receita:** `item/RangeUpgradeItem` (ou reaproveitar o molde do `ChunkLoaderUpgradeItem`), textura pelo `gerar_texturas.py`, receita cara (*provisório*, a decidir no teste), nome e tooltip nos dois `lang` (tooltip com raio e limite do tier do roteador).
- **Segundo slot de upgrade** no `RouterMenu`, depois do de chunk loading (os índices de antes não mudam). O Básico recusa o Upgrade de Alcance no slot.

## 8. Etapas

1. **Modelo e motor:** várias máquinas, grupos, perfis, exceções, lados relativos à frente, Upgrade de Alcance e o slot, config, Vinculador no modo Máquinas com as regras e a action bar. Acordar como a máquina presa, pelo mixin e pela carga de chunk (decisão 8). **Tela mínima** (decisão do dono, 11/10/2026, para testar no jogo antes da etapa 2): na tela do roteador com o upgrade, uma lista simples de grupos e máquinas na coluna da esquerda; escolher um grupo edita o perfil e escolher uma máquina edita a exceção, com os controles de face de hoje e os botões Voltar ao perfil e Desvincular. Sem Mapa, sem seleção múltipla e sem o visor por grupo (o visor mostra a máquina escolhida). GameTests: transferência com máquinas remotas (inclusive giradas: "Trás" segue a frente), limite e raio recusados, chunk descarregado faz dormir, máquina quebrada vira "sumiu", mundo antigo carrega igual, perfil vale para o grupo e exceção não muda com ele.
2. **Telas:** a tela do roteador completa (lista agrupada com busca, seleção múltipla, visor por grupo, Mapa, Usar como perfil), a tela do Vinculador no modo Máquinas e os avisos no Tablet. E2E com capturas.
3. **Acabamento:** separar e renomear grupos, pausar grupo, perfis no Configurador, linhas no mundo, guia (`gerar_guia.py`, dois idiomas) e o `progresso.md`.

## Perguntas em aberto (responder depois do teste no jogo)

- Nome do item: "Upgrade de Alcance" (`range_upgrade`)?
- Vazão por máquina (recomendado) ou dividida?
- Os números da tabela, principalmente o Ultimate (raio 64, 128 máquinas).
- Linhas no mundo só com o Vinculador ou também com o Tablet (a "visualização" já planejada na especificação)?
- O Básico fica sem alcance?
- Grupo pelo id do bloco por padrão, e lados relativos à frente como padrão (os dois recomendados)?

## Fica de fora

- A versão achatada entre dois blocos.
- Vincular máquinas de outra dimensão.
- Carregar os chunks das máquinas vinculadas.
- Vincular tudo no raio automaticamente.
