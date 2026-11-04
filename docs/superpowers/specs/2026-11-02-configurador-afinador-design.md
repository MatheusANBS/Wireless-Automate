# Configurador: o Afinador (etapa 3 do roteador de alcance)

Escolhido pelo dono em 02/11/2026 entre as três ideias de `docs/preview/configurador-novo.html` ("Só o Afinador"). O Configurador estava sem função desde a etapa 1.

## O que faz

O jogador clica numa máquina com o Configurador; ele descobre sozinho, face a face, o que a máquina aceita e o que ela dá, mostra a proposta pintada nas faces da máquina no mundo e, num segundo clique, aplica os **modos** (Insere, Extrai, Nenhum) por tipo na configuração daquela máquina.

## O gesto

1. **Clique direito numa máquina** (bloco que não é roteador) com o Configurador na mão principal: analisa a máquina e mostra a proposta (pintura nas faces, por até 10 s, e uma linha na action bar: "Afinador: Cima Insere · Lados Insere · Baixo Extrai · clique de novo para aplicar (Shift: no grupo inteiro)").
2. **Clique direito de novo na mesma máquina** em até 10 s (e no mínimo 6 ticks depois do primeiro; o Configurador descansa 10 ticks depois de toda análise, com proposta, sem nada a configurar ou com falha, para o botão segurado não confirmar sozinho): aplica na máquina; **Shift + clique** aplica no perfil do grupo inteiro. Aviso na action bar: "Afinador aplicado em Fornalha #2 · 3 faces" (ou "no grupo Fornalha · 3 faces").
3. Clicar em outra máquina refaz a análise para ela; no ar, ou depois de 10 s, a proposta some. A proposta pendente fica na memória do servidor por jogador (limpa no logout e na parada), nunca no NBT.
   - **Botão segurado:** o jogo repete o uso a cada 4 ticks. O cliente (`TunerInput`) não manda nenhum uso do Configurador depois de um toque numa máquina até a tecla soltar (nem na máquina, nem no chão ou no ar: escorregar a mira não apaga a proposta). O servidor tem a própria trava, contra qualquer cliente: guarda por jogador o tick da última tentativa, inclusive as engolidas no descanso, e só aceita a confirmação depois de uma pausa maior que 5 ticks entre tentativas (`TunerPending.attempt`). O uso no ar só apaga a proposta com um clique novo, e o primeiro depois do descanso só vale se veio uma pausa depois do fim dele (no descanso o jogo nem chama o `use` do item).
   - **Bloco sem capability:** um bloco que não expõe a capability de nenhum tipo carregado (terra, porta, e também os com block entity, como o atril, a mesa de encantamento e a placa) não é do Configurador: o clique segue para o bloco, sem aviso.
4. **Qual roteador:** a máquina tem de ser a presa ou uma remota vinculada de algum roteador que o jogador pode editar (`RouterEditHost`/regras de sempre: `mayInteract` e `mayBuild` na máquina, acesso ao roteador, `TabletMenu.inReach`/`RangeLinks.clickAllowed` como o modo Abrir do Vinculador). A presa vem primeiro; com mais de um roteador, o primeiro na mesma ordem do modo Abrir. Sem roteador: "Vincule a máquina a um roteador primeiro" e nada é analisado.
5. Clique num roteador com o Configurador abre a tela dele, como hoje.

## A análise (lógica pura, com JUnit)

Para cada face absoluta da máquina e cada tipo carregado, o servidor lê a capability daquela face (a mesma que o motor usaria) e classifica:

- **Itens:** para cada slot do handler da face (até 27; os que passam disso ficam fora da análise, e o número total entra na assinatura): no slot **vazio**, as provas aceitas pelo `isItemValid` e pelo `insertItem` simulado; *entrada* se aceita alguma, *aberto* (entrada e saída, como num baú) se aceita todas, *saída fraca* se recusa todas (um slot de produto vazio). No slot **ocupado e não cheio** só vale o `insertItem` simulado de uma unidade do próprio item (o `isItemValid` diz sim até em slot de saída, no `ItemStackHandler`, no `InvWrapper` e no Mekanism): aceito, o slot é aberto; recusado e com algo que sai pelo `extractItem` simulado, é *saída* de verdade. No slot **cheio** (a quantidade no menor entre o limite do slot e o tamanho máximo do item) a inserção não prova nada, então decide o `isItemValid` do próprio item: verdadeiro, aberto (o slot do barril, e um baú meio cheio continua armazenamento); falso, saída de verdade (a saída cheia da fornalha). Uma saída cheia que diz sim no `isItemValid` (o Mekanism) conta como aberta, mas a face só é aberta com todos os slots abertos, então a entrada que aceita só parte das provas a mantém máquina. Itens de prova: os da lista fixa curta (pedregulho, minério de ferro, ferro bruto, carvão, carvão vegetal, tronco de carvalho, areia, trigo, semente de trigo, pó de redstone, balde de água, balde de lava, osso, verruga do Nether), os que já estão nos slots da máquina e os do inventário do jogador, sem repetir, até 64 provas por análise.
- **Fluidos:** *entrada* se `fill` simulado aceita água ou lava (ou um tanque com algo aceita o próprio fluido); *saída* se `drain` simulado dá algo (ou tanques vazios que recusam os dois, saída fraca); aberta se aceita os dois, ou aceita e dá.
- **Energia:** *entrada* se `canReceive`; *saída* se `canExtract`; os dois, AMBOS aberta. Um consumidor com o `EnergyStorage` comum (que diz que também dá) lê igual a uma bateria.
- **Químicos e Source:** pelas pontes de sempre (`Chemicals.probe`, `Sources.probe`), se carregados: químico com *saída* por `extract` simulado e *entrada* se o tanque aceita mais do que já tem (sem prova de químico, um tanque vazio fica de fora); Source como a energia.

Cada face fica com um papel por tipo: **ENTRADA** (só entradas), **SAÍDA** (só saídas), **AMBOS** (entradas e saídas), **NADA**.

**Faces iguais** (a máquina que expõe os mesmos slots em todos os lados) contam como uma só: duas faces com a mesma assinatura (número de slots, limites e o padrão de provas aceitas por slot) recebem o papel uma vez, na face preferida.

**Decisão dos modos:**
0. **Armazenamento:** se, em **todo** tipo achado (itens inclusive), as faces com algo são AMBOS abertas e têm a mesma assinatura (um baú, um barril, um tanque, uma bateria), em cada tipo uma face só recebe **Armazém** (preferência: Cima, Trás, Esquerda, Direita, Frente, Baixo) e as outras faces com a capability daquele tipo vão para **Nenhum** (um Extrai ao lado de um Armazém esvaziaria a máquina nela mesma); os passos abaixo não valem. Energia e Source não provam armazenamento sozinhas: uma máquina **só** de tipos escalares só é armazenamento se for a Bateria ou o Tanque de Source do mod (as capabilities em `long`, `BulkEnergy` e `BulkSource`, que só eles registram); a de outro mod segue os passos abaixo e recebe Insere numa face. Na dúvida, Insere, que não esvazia a máquina, vale mais que Armazém. Com outro tipo de armazenamento junto (um baú que também guarda energia), a energia é Armazém também.
1. Fora do armazenamento, uma face AMBOS de energia ou de Source conta como ENTRADA (um consumidor com o `EnergyStorage` comum diz que também dá): esses tipos nunca recebem Extrai por AMBOS.
2. Faces SAÍDA → Extrai, mas só uma por assinatura (preferência: Baixo, Trás, Esquerda, Direita, Frente, Cima), as de saída de verdade antes das de saída fraca.
3. Sem SAÍDA, uma face AMBOS (a preferida acima, de novo a de saída de verdade antes) → Extrai.
4. Faces ENTRADA e as AMBOS que sobraram → Insere, uma por assinatura (preferência: Cima, Trás, Esquerda, Direita, Frente, Baixo).
5. O resto → Nenhum.

Sem filtro: a recusa do item errado já é barata no motor (a recusa por item da correção do destino seletivo), então a face do combustível recebe só o que a máquina aceita ali.

## O que muda na máquina

Só os modos dos tipos em que a análise achou algo. A prioridade, o redstone, o filtro e a **rede** de cada face ficam como estão. Aplicar segue a semântica de máquina (presa sozinha muda o perfil; máquina com outras do bloco vira exceção, que some se igual ao perfil); Shift aplica no perfil do grupo. Se nenhuma face mudada para um modo (Insere, Extrai, Armazém) tem rede, o aviso termina com "· escolha a rede no painel". Um Desfazer não existe (o segundo clique já é a confirmação, e a pintura mostra antes).

## A pintura (cliente)

Enquanto a proposta vale (10 s, ou até o jogador mirar outro bloco por 2 s), cada face da máquina com modo recebe uma moldura colorida translúcida por fora do bloco, na cor do modo (as mesmas cores do painel: Insere, Extrai), com a letra do tipo quando há mais de um tipo na face. Com os cinco tipos na mesma face (Mekanism e Ars juntos), as molduras e a linha de letras encolhem para caber. O servidor manda a proposta num payload (`ConfiguratorProposalPayload`: posição, até 6 faces × tipos com o modo, e o prazo) só ao jogador que clicou, uma vez por análise. Nada por tick no servidor; no cliente, o desenho só existe enquanto há proposta.

A proposta **some nos dois lados** (decisão do dono): quando a pintura some porque o jogador mirou outro bloco por 2 s, ou porque ele renasceu (morte), o cliente manda `ConfiguratorCancelPayload` (a posição da máquina) e o servidor descarta a proposta pendente daquela máquina; o próximo clique nela faz uma análise nova. Sair do mundo, a máquina quebrada e o prazo vencido não mandam nada (a do servidor também não vale mais).

Uma exceção da capability de outro mod durante a análise (as provas simuladas que ele não espera) não derruba o servidor: fica no log, com o id do bloco, e a action bar diz que a máquina falhou na análise.

## Configurador: o resto

O item, a receita e os componentes ficam. O tooltip passa a explicar o gesto (dois idiomas). A página do guia do Configurador volta a ser uma página de verdade ("como operar"). Os textos "sem função por enquanto" saem do guia, do README, da descrição do CurseForge e da especificação.

## Custo

Só no clique: até 6 faces × tipos × (slots × provas) simulações, limitado a 64 provas e 27 slots por face (o que passar disso é ignorado na análise), uma vez por clique. Nada por tick. Memória: uma proposta e um `long` (a última tentativa) por jogador com clique recente, limpos no logout e na parada. Rede: um payload por análise e, no máximo, um de volta por proposta apagada no cliente.

## Fora

A Sonda (diagnóstico ao mirar) e o Molde (carimbar uma linha) ficam como ideias para depois.
