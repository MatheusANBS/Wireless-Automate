# Configurador: o Afinador (etapa 3 do roteador de alcance)

Escolhido pelo dono em 02/11/2026 entre as três ideias de `docs/preview/configurador-novo.html` ("Só o Afinador"). O Configurador estava sem função desde a etapa 1.

## O que faz

O jogador clica numa máquina com o Configurador; ele descobre sozinho, face a face, o que a máquina aceita e o que ela dá, mostra a proposta pintada nas faces da máquina no mundo e, num segundo clique, aplica os **modos** (Insere, Extrai, Nenhum) por tipo na configuração daquela máquina.

## O gesto

1. **Clique direito numa máquina** (bloco que não é roteador) com o Configurador na mão principal: analisa a máquina e mostra a proposta (pintura nas faces, por até 10 s, e uma linha na action bar: "Afinador: Cima Insere · Lados Insere · Baixo Extrai · clique de novo para aplicar (Shift: no grupo inteiro)").
2. **Clique direito de novo na mesma máquina** em até 10 s: aplica na máquina; **Shift + clique** aplica no perfil do grupo inteiro. Aviso na action bar: "Afinador aplicado em Fornalha #2 · 3 faces" (ou "no grupo Fornalha · 3 faces").
3. Clicar em outra máquina refaz a análise para ela; no ar, ou depois de 10 s, a proposta some. A proposta pendente fica na memória do servidor por jogador (limpa no logout e na parada), nunca no NBT.
4. **Qual roteador:** a máquina tem de ser a presa ou uma remota vinculada de algum roteador que o jogador pode editar (`RouterEditHost`/regras de sempre: `mayInteract` e `mayBuild` na máquina, acesso ao roteador, `TabletMenu.inReach`/`RangeLinks.clickAllowed` como o modo Abrir do Vinculador). A presa vem primeiro; com mais de um roteador, o primeiro na mesma ordem do modo Abrir. Sem roteador: "Vincule a máquina a um roteador primeiro" e nada é analisado.
5. Clique num roteador com o Configurador abre a tela dele, como hoje.

## A análise (lógica pura, com JUnit)

Para cada face absoluta da máquina e cada tipo carregado, o servidor lê a capability daquela face (a mesma que o motor usaria) e classifica:

- **Itens:** para cada slot do handler da face: *entrada* se algum item de prova é aceito (`isItemValid` e `insertItem` simulado aceitam); *saída* se nenhum item de prova é aceito e o slot pode dar (`extractItem` simulado devolve algo, ou o slot está vazio e recusa tudo). Itens de prova: os do inventário do jogador (sem repetir), os que já estão nos slots da máquina e uma lista fixa curta de itens comuns (pedregulho, minério de ferro, ferro bruto, carvão, carvão vegetal, tronco de carvalho, areia, trigo, semente de trigo, pó de redstone, balde de água, balde de lava, osso, verruga do Nether), até 64 provas por análise.
- **Fluidos:** *entrada* se `fill` simulado aceita água ou lava; *saída* se `drain` simulado dá algo (ou tanque vazio que recusa os dois).
- **Energia:** *entrada* se `canReceive`; *saída* se `canExtract` e não `canReceive`.
- **Químicos e Source:** pelas pontes de sempre (`Chemicals`, `Sources`), se carregados, com a mesma regra do fluido (sem prova de químico: só *saída* por `extract` simulado e *entrada* se o handler aceita algum dos que já tem); se a ponte não der para fazer barato, ficam fora (Nenhum) nesta etapa.

Cada face fica com um papel por tipo: **ENTRADA** (só entradas), **SAÍDA** (só saídas), **AMBOS** (entradas e saídas), **NADA**.

**Faces iguais** (a máquina que expõe os mesmos slots em todos os lados) contam como uma só: duas faces com a mesma assinatura (número de slots, limites e o padrão de provas aceitas por slot) recebem o papel uma vez, na face preferida.

**Decisão dos modos, por tipo:**
0. **Armazenamento:** se todas as faces com algo são AMBOS e têm a mesma assinatura (um baú, um barril, um tanque, uma bateria), uma face só recebe **Armazém** (preferência: Cima, Trás, Esquerda, Direita, Frente, Baixo) e as outras ficam como estão; os passos abaixo não valem.
1. Faces SAÍDA → Extrai, mas só uma por assinatura (preferência: Baixo, Trás, Esquerda, Direita, Frente, Cima).
2. Sem SAÍDA, uma face AMBOS (a preferida acima) → Extrai.
3. Faces ENTRADA e as AMBOS que sobraram → Insere, uma por assinatura (preferência: Cima, Trás, Esquerda, Direita, Frente, Baixo).
4. O resto → Nenhum.

Sem filtro: a recusa do item errado já é barata no motor (a recusa por item da correção do destino seletivo), então a face do combustível recebe só o que a máquina aceita ali.

## O que muda na máquina

Só os modos dos tipos em que a análise achou algo. A prioridade, o redstone, o filtro e a **rede** de cada face ficam como estão. Aplicar segue a semântica de máquina (presa sozinha muda o perfil; máquina com outras do bloco vira exceção, que some se igual ao perfil); Shift aplica no perfil do grupo. Se nenhuma face mudada tem rede, o aviso termina com "· escolha a rede no painel". Um Desfazer não existe (o segundo clique já é a confirmação, e a pintura mostra antes).

## A pintura (cliente)

Enquanto a proposta vale (10 s, ou até o jogador mirar outro bloco por 2 s), cada face da máquina com modo recebe uma moldura colorida translúcida por fora do bloco, na cor do modo (as mesmas cores do painel: Insere, Extrai), com a letra do tipo quando há mais de um tipo na face. O servidor manda a proposta num payload (`ConfiguratorProposalPayload`: posição, até 6 faces × tipos com o modo, e o prazo) só ao jogador que clicou, uma vez por análise. Nada por tick no servidor; no cliente, o desenho só existe enquanto há proposta.

## Configurador: o resto

O item, a receita e os componentes ficam. O tooltip passa a explicar o gesto (dois idiomas). A página do guia do Configurador volta a ser uma página de verdade ("como operar"). Os textos "sem função por enquanto" saem do guia, do README, da descrição do CurseForge e da especificação.

## Custo

Só no clique: até 6 faces × tipos × (slots × provas) simulações, limitado a 64 provas e 27 slots por face (o que passar disso é ignorado na análise), uma vez por clique. Nada por tick. Memória: uma proposta por jogador com clique recente. Rede: um payload por análise.

## Fora

A Sonda (diagnóstico ao mirar) e o Molde (carimbar uma linha) ficam como ideias para depois.
