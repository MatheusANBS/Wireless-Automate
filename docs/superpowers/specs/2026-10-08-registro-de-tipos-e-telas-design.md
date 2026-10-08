# Registro de tipos e telas escalonáveis (etapa 0 da compatibilidade com o Ars Nouveau)

Data: 8/10/2026. Proposta visual aprovada pelo dono: [Source e telas escalonáveis](https://claude.ai/artifact/XxvxW9Gejgxi9qj4LZ5ocF).

## Contexto e objetivo

O Ars Nouveau traz um quinto recurso, **Source**. Hoje cada tipo de recurso é tratado à mão em ~30 arquivos (`switch` por tipo, `if (Chemicals.LOADED)`, colunas fixas no Tablet, caixas fixas no Vinculador), e a linha de abas do roteador já não cabe com 4 tipos (o seletor de rede aparece cortado).

O trabalho foi dividido em três etapas, cada uma com spec, plano, testes e commit próprios:

| Etapa | O que entrega |
| --- | --- |
| **0. Registro de tipos e telas** (esta spec) | Tipos de recurso descritos num lugar só; telas que se ajustam ao número de tipos e ao tamanho da janela. Nenhum comportamento novo para o jogador além das telas. |
| 1. Aba Source | Ars Nouveau opcional (API só em `compat/arsnouveau`), transferência de Source, aba no roteador, Vinculador, Configurador e Tablet, GameTests numa run com o Ars, página no guia. |
| 2. Tanque de Source Wireless | Quinto armazenamento, no molde da Bateria, registrado no `SourceManager` do Ars. |

Decisões já tomadas pelo dono e que valem para as etapas 1 e 2 (registradas aqui para não se perderem):

- Vazão de Source por face: Básico 1.000/s, Avançado 16.000/s, Elite 256.000/s, Ultimate sem limite (config `tiers.<tier>.sourcePerSecond`).
- Capacidade do Tanque de Source: Básico 160.000, Avançado 2.560.000, Elite 40.960.000, Ultimate sem limite (config `storage.sourceTankCapacity`). Para as máquinas do Ars (que contam em `int`), o tanque mostra no máximo `Integer.MAX_VALUE`.
- Source não tem tipos: sem filtro e sem cartões, como a energia.
- Fatos da investigação do Ars Nouveau 5.13.3 (1.21.1): capability `ars_nouveau:source` (`ISourceCap`, por face, `int`, `receiveSource`/`extractSource` com simulação); exige GeckoLib e Curios; as máquinas do Ars acham jarras por `instanceof SourceJarTile` num raio ou pelo `SourceManager.INSTANCE.addInterface(level, ISpecialSourceProvider)`.

## Escopo da etapa 0

1. Registro de tipos de recurso (servidor e cliente).
2. Motor, rede, Vinculador, Configurador, Tablet, JEI e telas passam a ler do registro.
3. Tela do roteador redimensionável, com abas que se ajustam e seletor de rede maior.
4. Vinculador com chips de tipo montados pelo registro.
5. Tablet redimensionável, com a aba Estatísticas em cartões por tipo; clique no cartão abre a Lista filtrada pelo tipo.
6. Regra geral: **nenhum texto vaza do seu espaço** em nenhuma tela, em nenhum tamanho.

Fora do escopo: o tipo Source em si (etapa 1), mudanças de comportamento do motor, formato de save.

## Desenho

### 1. Registro de tipos (lado comum)

**Abordagem escolhida:** o `enum ResourceType` continua sendo o registro (os índices por `ordinal()` em arrays, NBT e payloads continuam valendo), mas passa a carregar tudo o que hoje está espalhado em `switch`. Alternativa descartada: um registro aberto no estilo `DeferredRegister`, que quebraria os arrays por `ordinal()`, o NBT e os payloads sem ganho real, porque os tipos são um conjunto fechado do mod (um tipo novo é sempre código novo de transferência).

Cada constante do `ResourceType` declara:

| Campo | Para quê | Exemplo (Fluidos) |
| --- | --- | --- |
| `key` | Chave estável em NBT, componentes, comandos e traduções (`gui.wirelessautomate.type.<key>`). Mantém as chaves de hoje. | `fluid` |
| `typed` | Tem tipos dentro (filtro, cartões, estoque). | `true` |
| `rateConfig` | Qual valor de vazão da config do tier vale para ele, e a unidade de tempo (por segundo ou por tick). Chaves da config não mudam. | `fluidPerSecond`, por segundo |
| `unit` | Chave da unidade (`gui.wirelessautomate.unit.*`). | `mb` |
| `available()` | Se o tipo existe nesta instância (mod que habilita carregado). | sempre |
| ordem | A ordem das constantes é a ordem das abas, chips, cartões e da Shift + roda. | 2º |

Helpers estáticos: `ResourceType.available()` (lista dos disponíveis, na ordem) e `ResourceType.byKey(String)`.

O que sai do código espalhado e passa a usar o registro: `RouterBlockEntity` (`CARD_TYPES`, `hasCardSlots`, `cardIndex` derivados de `typed`), `RouterPreset`, `NodePorts`, `NodeIndex`, `NetworkRoutes`, `NetworkManager` (o despacho para `ItemTransfer`/`FluidTransfer`/... vira um campo de transferência por tipo), `LinkerTabs` (`available(boolean chemicals)` e `effective(boolean chemicals)` passam a usar `available()` do registro), `PasteTypes`, `TierCoreItem`, `FilterCardItem`, `WaCommand`, `BenchScene` e os menus/snapshots.

**Capabilities por tipo:** o `RouterBlockEntity` mantém os `BlockCapabilityCache` por tipo e face; o registro só diz qual capability (e a "bulk" do mod, quando existe) cada tipo usa. Químicos continuam passando pela ponte `Chemicals` (tipos do Mekanism só em `compat/mekanism`).

### 2. Estilo dos tipos (só cliente)

Uma classe `client/ResourceStyle` (nunca referenciada por código comum) com, por tipo: cor, ícone 8×8 (sprite novo gerado pelo `scripts/textures/gerar_texturas.py`) e o formatador de valor (`RateFormat`).

Cores aprovadas: Itens `#D9A35B`, Fluidos `#3D8BFF`, Energia `#FFB020`, Químicos `#B45CFF` (Source será `#FF5CC8`). Corrige o Tablet, onde hoje Itens e Fluidos usam o mesmo azul.

### 3. Barra de abas adaptável (widget comum)

Um widget `client/TypeTabBar`, usado pelo roteador (e reaproveitável), que recebe a largura disponível e os tipos disponíveis e escolhe o modo:

1. **Com nome:** todas as abas com ícone e nome, se couberem.
2. **Nome só na ativa:** a ativa com ícone e nome, as outras só ícone (20 px).
3. **Só ícones.**

Em todos os modos: bolinha da rede da aba, sublinhado na cor do tipo na aba ativa e tooltip com o nome do tipo. A escolha é pura (largura disponível + larguras medidas → modo) e vai para uma classe sem Minecraft com teste JUnit.

### 4. Tela do roteador

- **Redimensionável** pela borda direita, pela de baixo e pelo canto, em torno do centro, no mesmo mecanismo da `StorageListScreen` (a borda puxada segue o mouse, a oposta se move igual). Mínimo: o tamanho de hoje (300 × 240). Máximo: o que couber na janela. Tamanho lembrado na sessão.
- **Para onde vai o espaço extra:** só para o visor 3D (largura e altura) e para a linha das abas. A coluna da direita (face, modos, filtro, cartões, inventário) mantém a largura de hoje, ancorada à borda direita; o inventário do jogador acompanha (troca de posição dos slots no cliente, como no Baú).
- **Linha das abas:** `TypeTabBar` à esquerda; o **seletor de rede** fica com todo o espaço que sobra (mínimo 92 px), com a bolinha da cor da rede e o nome inteiro (abreviado com reticências só se não couber, nome inteiro no tooltip).
- Abas de tipo sem tipos (`typed == false`): sem filtro e sem cartões; no lugar, "Sem filtro: <tipo> não tem tipos" e a vazão do tier.

### 5. Vinculador

- As caixas por aba viram **chips** (caixa de marcar, ícone, nome), em grade de 2 colunas que cresce para baixo, montada pelo `ResourceType.available()`; borda na cor do tipo quando marcado.
- **Todos** marca/desmarca os disponíveis. O título mostra o resumo ("Todos os tipos", "Itens, Fluidos", "Nenhum tipo").
- O botão de vincular diz quantos tipos ("Vincular 2 · 3 tipos") e fica desligado sem tipo marcado.
- Shift + roda do mouse e o Configurador seguem a ordem do registro.

### 6. Tablet

- **Redimensionável** como o roteador (mínimo: o tamanho de hoje). Lista e Mapa ganham linhas e área com a altura.
- **Estatísticas:** um cartão por tipo disponível, em grade: colunas = quantas cabem com largura mínima de cartão (3 no tamanho mínimo, até todos numa linha). Cada cartão: ícone, nome, vazão total da seleção na cor do tipo, e uma linha com unidade, origens ↑, destinos ↓ e dormindo. Borda de cima na cor do tipo.
- **Clique no cartão:** vai para a aba Lista com o filtro de tipo naquele tipo.
- **Payload:** `TabletSnapshot.NetworkView` troca os quatro campos `itemRate`…`chemicalRate` por um array por `ordinal()`, e ganha origens, destinos e dormindo por tipo. Sobe `ModPayloads.VERSION` (de 7 para 8).

### 7. Nenhum texto vaza

Regra para todas as telas tocadas (roteador, Vinculador, Tablet) e para os widgets novos:

- Todo texto desenhado recebe a largura disponível. Se não couber: primeiro quebra linha onde o layout prevê duas linhas (como a linha de baixo dos cartões do Tablet, que passa para duas linhas); senão é abreviado com reticências e o texto inteiro vai para o tooltip.
- Um helper único (`GuiText.fit(font, text, width)` e `GuiText.wrap(...)`) faz isso; nenhuma tela chama `drawString` com texto variável sem passar por ele.
- O e2e captura cada tela nova no tamanho mínimo e no máximo, em inglês e em português, com nomes longos (rede com 40 caracteres) e os 4 tipos de hoje (o `runClient` tem o Mekanism); o caso de 5 tipos entra no e2e da etapa 1 e verifica pelo helper que nenhum texto foi desenhado além do seu retângulo (um contador de "textos cortados sem tooltip" que precisa ficar em zero).

## Testes

- **JUnit:** escolha do modo da barra de abas; colunas do Tablet por largura; `GuiText.fit`/`wrap` (lógica pura, com uma fonte falsa de largura fixa); ordem e disponibilidade do registro; `LinkerTabs` e `PasteTypes` com o registro.
- **GameTests:** os existentes continuam passando sem mudança de comportamento (149 + 7); um teste novo garante que todo tipo do registro tem chave de NBT única e que um roteador salvo antes (chaves de hoje) carrega igual.
- **e2e:** roteador, Vinculador e Tablet capturados nos tamanhos mínimo e máximo, nos dois idiomas, com o contador de texto vazado em zero; arrastar a borda do roteador e do Tablet; Shift + clique no inventário do roteador depois de redimensionar; clique num cartão do Tablet abrindo a Lista filtrada.
- Antes do commit: `./gradlew build runGameTestServer runGameTestServerChemicals` e o `./scripts/e2e.sh`.

## Documentação

`CLAUDE.md` (mapa do código: registro, `ResourceStyle`, `TypeTabBar`, `GuiText`), `docs/especificacao.md` (telas), `docs/progresso.md`, guia (páginas do roteador, Vinculador e Tablet, com as capturas novas) e as traduções `en_us`/`pt_br`.

## Riscos

- **Telas grandes demais para mudar de uma vez** (`RouterScreen` 1.183 linhas, `TabletScreen` 1.732): a mudança de layout vem depois da troca para o registro, em passos separados no plano, cada um com o e2e passando.
- **Protocolo:** cliente e servidor precisam ser da mesma versão (já era assim desde a 1.0).
- **Save:** as chaves de NBT não mudam; o GameTest de compatibilidade cobre isso.
