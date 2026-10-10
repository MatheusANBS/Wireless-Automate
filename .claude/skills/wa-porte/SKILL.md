---
name: wa-porte
description: Investiga o que muda entre a versão de Minecraft e loader do Wireless Automate (hoje 1.21.1 NeoForge, o ATM10) e outra versão alvo (26.1 NeoForge do ATM11, 1.20.1 Forge do ATM9 e do ATM9 To the Sky, ou qualquer outra), e mede o impacto no código - relatório de impacto por área com fontes, o ramo do porte, o build mínimo e o plano em tarefas. Use quando o dono falar em "portar", "porte", "outra versão", "1.20.1", "26.1", "ATM11", "ATM9", "To the Sky", "ramo da versão", "o que muda entre versões", ou quando uma feature do main precisar ir para um ramo de outra versão. Não use para feature nova no main (wa-feature) nem para fechar versão (wa-release).
---

# Porte para outra versão de Minecraft

O `main` é a linha principal (hoje 1.21.1 NeoForge, o ATM10). Cada outra versão vive num ramo próprio,
de longa duração, que **nunca volta para o `main`**. A estratégia, a ordem dos portes e o estado de cada
ramo ficam em `docs/portes.md`: leia antes de começar e atualize ao terminar.

Esta skill tem duas partes: **investigar** (sempre primeiro, sem mexer em código) e **portar** (só com o
ok do dono para o relatório).

## Regra de ouro: nada de memória

Mudança de API entre versões é exatamente o tipo de coisa que se lembra errado. Todo item do relatório
de impacto leva a **fonte** (link do primer, das notas de versão, do javadoc, do código-fonte do jogo
descompilado ou do jar do mod) e o **grau de certeza**:
- **confirmado:** visto na fonte ou compilando;
- **provável:** está num primer ou numa nota, mas ainda não foi visto no código;
- **a verificar:** suspeita, sem fonte ainda.

Sem fonte, o item fica "a verificar". Nunca escreva uma assinatura de método, um nome de classe ou um
número de versão que não foi visto.

## 1. Fixar o alvo

Responda com o dono, uma pergunta de cada vez, só o que não der para descobrir sozinho:
- **Pack de referência:** qual modpack define o alvo (ATM11, ATM9, ATM9 To the Sky...). O pack manda na
  versão do Minecraft, do loader e dos mods opcionais.
- **Versões exatas:** Minecraft, loader (Forge ou NeoForge) e build mínimo do loader. Tire do próprio
  pack: na pasta da instância do launcher (`minecraftinstance.json` e a pasta `mods/`) ou da página de
  dependências do arquivo do pack no CurseForge. Anote os jars de Mekanism, Ars Nouveau, Allthemodium,
  All The Tweaks, JEI e GuideME que vêm no pack (ou que não vêm).
- **Java e Gradle** exigidos pelo alvo (as notas do loader dizem).

Dois packs na mesma versão de Minecraft e no mesmo loader são **um alvo só** (o ATM9 e o ATM9 To the Sky
usam 1.20.1 Forge: um ramo serve os dois).

## 2. Juntar as fontes

Por ordem de confiança:
1. **Primers do NeoForge** (https://docs.neoforged.net/primer/docs/): um por salto de versão
   (1.21.1 → 1.21.2 → ... → 1.21.11 → 26.1). Para ir do 1.21.1 ao 26.1, leia todos os do caminho.
2. **Notas de lançamento do loader** (blog do NeoForge, https://neoforged.net/news/; para o Forge 1.20.1,
   o changelog do Forge e a documentação do Forge 1.20.x).
3. **Código do jogo e do loader do alvo**, descompilado pelo próprio Gradle num projeto mínimo do alvo
   (passo 4). É a fonte final: o que compila vale mais que qualquer texto.
4. **API de cada mod opcional na versão do alvo** (Mekanism, Ars Nouveau, JEI, GuideME): o jar da API,
   o repositório no GitHub na tag da versão, o Maven de onde o `build.gradle` baixa.
5. **Mods grandes já portados** (AE2, Mekanism) como exemplo de como resolveram a mesma mudança.

Use o WebFetch para ler os primers e as notas. Guarde os links que usar: eles vão no relatório.

## 3. Inventário do código

Rode na raiz do `main`:

```bash
bash .claude/skills/wa-porte/scripts/inventario.sh
```

A tabela diz quantos arquivos usam cada API sensível a versão. Para cada área com mudança no alvo,
abra os arquivos (`grep -rl`) e veja o uso real. A lista das áreas e o que costuma mudar em cada uma
está em `references/areas.md`.

## 4. Projeto mínimo do alvo

Antes de estimar, prove o ambiente: num diretório do scratchpad, monte um projeto vazio do alvo (o
template do MDK do loader, com a mesma ferramenta de build que o ramo vai usar) e rode o build e o
`runClient`. Isso confirma Java, Gradle, plugin e mapeamentos, e deixa o código-fonte do jogo
descompilado para consultar. Anote no relatório o que precisou.

## 5. Relatório de impacto

Escreva `docs/portes/<alvo>-impacto.md` (por exemplo `docs/portes/1.20.1-forge-impacto.md`):

1. **Alvo:** pack de referência, versões exatas (Minecraft, loader, Java, Gradle, plugin), mods
   opcionais presentes no pack e as versões deles.
2. **Resumo:** tamanho do porte (pequeno, médio, grande), as três maiores mudanças e o que fica de fora.
3. **Tabela por área** (as de `references/areas.md`): o que muda, arquivos afetados (números do
   inventário), estratégia, risco (baixo, médio, alto), certeza e fonte.
4. **Mods opcionais:** para cada um, se existe no alvo, como a API difere e se a feature dele entra.
5. **Performance:** o que muda nos três pilares (sem tick por bloco, capability só por cache, nada de
   sincronizar com a tela fechada). Se o alvo não tem `BlockCapabilityCache`, diga como o cache vai
   ser feito.
6. **Dados do mundo:** se um mundo do `main` abre no alvo (em geral não abre entre versões; diga isso no
   changelog do ramo).
7. **Plano em tarefas:** na ordem (build, registros, dados, rede, capabilities e motor, telas, compat,
   GameTests, e2e, guia), cada uma terminando testável.

Mostre o resumo ao dono e espere o ok antes de criar o ramo.

## 6. Portar (com o ok)

- **Ramo:** `mc/<versão>-<loader>` a partir do `main` (por exemplo `mc/1.20.1-forge`, `mc/26.1-neoforge`).
  Nada de merge de volta no `main`.
- **Execução:** o fluxo da `wa-feature` (plano em `docs/superpowers/plans/`, um subagente por tarefa,
  revisão). O primeiro marco é **compilar**, depois os GameTests, depois o e2e.
- **Lógica pura igual:** as classes puras com JUnit (lista no `CLAUDE.md`) devem ficar idênticas ao
  `main`. Se o alvo obrigar a mudar uma, anote no `docs/portes.md`.
- **Versão:** o mesmo `mod_version` do `main` que ele espelha. O jar e a tag levam a versão do jogo e o
  loader: `wirelessautomate-<versão>+mc<mc>-<loader>.jar` e tag `v<versão>+mc<mc>-<loader>`.
- **`CLAUDE.md` do ramo:** atualize as versões, os comandos e as armadilhas do alvo (o arquivo é por
  ramo).

## 7. Trazer uma feature do `main` para um ramo

1. Liste o que falta: `git log --oneline <ramo>..main` não serve (os ramos divergem); use o
   `docs/portes.md`, que registra até qual versão do `main` cada ramo está.
2. Tente `git cherry-pick` dos commits da feature. Conflito em código de API: porte à mão, usando o
   relatório de impacto do ramo como guia.
3. Rode a verificação do ramo e atualize o `docs/portes.md` (versão espelhada).

## Ao terminar

Atualize `docs/portes.md` (estado do alvo, link do relatório, próximo passo) e o `docs/progresso.md`.
