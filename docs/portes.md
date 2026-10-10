# Portes para outras versões

Plano dos portes do Wireless Automate para outras versões de Minecraft. A investigação de cada alvo segue
a skill `wa-porte` (`.claude/skills/wa-porte/`), que gera o relatório de impacto em
`docs/portes/<alvo>-impacto.md`. Atualize este arquivo a cada passo.

## Estratégia

- **O `main` é a linha principal:** 1.21.1 NeoForge, o ATM10. Feature nova nasce no `main` (skill
  `wa-feature`).
- **Um ramo por alvo:** um alvo é uma versão de Minecraft com um loader. Ramos de longa duração, com o
  nome `mc/<versão>-<loader>`, criados a partir do `main` e que **nunca voltam para ele**.
- **Features descem do `main` para os ramos:** por `cherry-pick` quando der, à mão quando a API mudar.
  Um ramo pode ficar algumas versões atrás; a tabela abaixo diz qual versão do `main` cada um espelha.
- **Lógica pura idêntica:** as classes puras com JUnit (`TickBudget`, `RoundRobinOrder`, `WheelLayout`,
  `PasteMode`...) ficam iguais em todos os ramos. É a parte do mod que não depende de versão e a que
  mais custa reescrever.
- **Versão e arquivos:** cada ramo usa o `mod_version` do `main` que espelha; o jar e a tag levam o jogo e
  o loader (`wirelessautomate-1.6.0+mc1.20.1-forge.jar`, tag `v1.6.0+mc1.20.1-forge`). No CurseForge, é o
  mesmo projeto com arquivos marcados para cada versão do jogo.

### Por que ramos e não um projeto multiversão

Ferramentas como o Stonecutter (um código só, com trechos por versão) ou o Architectury (módulo comum e
um módulo por loader) evitam duplicar código. Aqui pesam contra, por enquanto:
- o 1.20.1 do ATM9 é **Forge**, não NeoForge;
- entre os alvos mudam justamente as APIs que o mod mais usa: componentes de item, payloads e
  capabilities (ver o inventário da skill);
- o código de cada versão ficaria cheio de trechos condicionais.

Vale reavaliar se a manutenção de três ramos começar a pesar (por exemplo, se toda feature exigir porte
à mão nos três).

## Alvos

| Alvo | Ramo | Packs | Loader | Estado | Espelha |
| --- | --- | --- | --- | --- | --- |
| 1.21.1 | `main` | ATM10, ATM10 To the Sky | NeoForge 21.1.248+ | Publicado | 1.6.0 |
| 1.20.1 | `mc/1.20.1-forge` | ATM9, ATM9 To the Sky (os dois na mesma versão, um ramo só) | Forge 47.4.10+ | Etapa 1: compila e abre; GameTests na etapa 2 ([relatório](portes/1.20.1-forge-impacto.md)) | 1.6.0 |
| 26.1 | `mc/26.1-neoforge` (a criar) | ATM11 (alfa, Minecraft 26.1.2) | NeoForge 26.1 | A investigar | — |

## O que já se sabe (a confirmar pela investigação)

**26.1 (ATM11).** Mesmo loader do `main`, mas o salto passa por todas as versões entre 1.21.1 e 26.1. As
notas do NeoForge falam em Java 25, Gradle 9.1+, o jogo sem ofuscação e versões do NeoForge com quatro
números. Relatos de porte citam a troca de `ResourceLocation` por `Identifier`. Os primers do caminho
cobrem mudanças grandes em receitas, modelos de item, NBT, desenho da GUI e transporte de itens. O ATM11
ainda é alfa: os mods opcionais do pack podem não ter chegado lá.

**1.20.1 (ATM9 e ATM9 To the Sky).** Outro loader (Forge) e uma versão de antes dos componentes de item
e dos payloads atuais: é o porte mais trabalhoso. Pontos que devem pesar:
- estado dos itens em NBT;
- rede pelo `SimpleChannel`;
- capabilities pelo `LazyOptional`, com o cache feito pelo próprio mod;
- pastas de dados no plural;
- os químicos do Mekanism divididos em quatro tipos;
- o GuideME pode não existir.

O ATM9 é um pack grande e estável, com muito jogador.

## Desvios do 1.20.1

O que o ramo `mc/1.20.1-forge` faz diferente do `main`, por limite do jogo ou dos mods dessa versão:
- **Químicos:** o Mekanism 10.4 tem quatro tipos (gás, infusão, pigmento, slurry), cada um com a sua
  capability. O mod os trata como o tipo Químicos, numa fila única de tanques por face (os tanques dos
  quatro tipos em sequência) e com a vazão somada. Destino que tem químicos, mas não o tipo oferecido, é pulado
  sem dormir; a origem dorme esperando destino e acorda quando ele passa a oferecer o tipo. Provado pelos
  `ChemicalGameTests` nos quatro tipos (inclusive com uma Câmara de Dissolução Química de verdade, gás entrando e
  slurry saindo pela mesma face). O exemplo 5x da especificação usa `#forge:ores`.
- **Source:** o Ars 4.12 não tem capability de Source; o roteador acha o `ISourceTile` pelo block entity.
  Os Relays do Ars 4.12 só ligam em `AbstractSourceMachine`: um mixin (`compat/arsnouveau/mixin`) entrega a
  eles a `RelayView` do Tanque de Source, e eles ligam e transferem nos dois sentidos como no `main`. Origem e
  destino seguem a capability do `main` (Imbuement Chamber não é drenada, Sourcelink não recebe, Creative Jar
  é ralo e fonte).
- **JEI 15:** sem o Shift + clique de um ingrediente do JEI para o filtro; arrastar funciona.
- **Caldeirão e compostor:** o Forge não dá handler a esses blocos sem block entity; o ramo tem os do NeoForge
  (`network/VanillaBlockHandlers`, porte do `CauldronWrapper` e o handler do compostor), achados pelo `CapCache`
  na busca por bloco. Caldeirões de outros mods não entram (o 1.20.1 não tem o registro de conteúdo de caldeirão).
- **Cache de capability:** o `network/CapCache` faz o papel do `BlockCapabilityCache`; o que o Forge não avisa vem
  do `neighborChanged` da máquina (block entity trocado, cache negativo que passa a achar a capability) e do índice
  de chunks `CapCacheChunks` (`ChunkEvent.Load/Unload`).
- **Campos de texto:** o `EditBox` do 1.20.1 desenha o texto com sombra.

## Ordem sugerida

1. **Investigar os dois alvos** com a `wa-porte`, só relatórios, sem código.
2. **Decidir a ordem com os números dos relatórios.**
   - A favor do 1.20.1 primeiro: o público grande e estável do ATM9.
   - A favor do 26.1: o porte provavelmente menor, mas num alvo ainda em alfa, que muda.
   - Hoje a sugestão é o 1.20.1 primeiro e o 26.1 quando o ATM11 sair do alfa.
3. **Portar um alvo por vez:** primeiro compilar, depois os GameTests, o e2e e o release do ramo.

## Histórico

| Data | O que foi feito |
| --- | --- |
| 2026-10-10 | Estratégia de ramos por alvo e a skill `wa-porte` para investigar o impacto de cada porte. |
| 2026-10-10 | 1.20.1 Forge investigado: projeto mínimo (MDG 2.0.148 `legacyforge`, Java 17, Gradle 9.2.1) compila e abre; relatório em `docs/portes/1.20.1-forge-impacto.md`. |
| 2026-10-10 | 1.20.1, etapa 1 (ramo `mc/1.20.1-forge`, espelha a 1.6.0): o mod inteiro compila no Forge 47.4.10, JUnit verde, 170 de 172 GameTests da run comum passando na primeira rodada e o cliente de dev abre sem os mods opcionais, com as telas nas capturas. GameTests, runs com os mods opcionais remapeados e e2e ficam para a etapa 2. |
