---
name: wa-mod-opcional
description: Receita para integrar outro mod como dependência opcional do Wireless Automate (como já foi feito com o Mekanism para Químicos e com o Ars Nouveau para Source) - novo tipo de recurso, ponte sem tipos do mod, pacote compat, run de GameTest própria, aba, cor, armazenamento e guia, sem quebrar o mod quando o outro não está instalado. Use quando o dono pedir compatibilidade com um mod ("suporte ao X", "integração com Y", "aba de Z", "transportar mana/essência/ar do mod W", "um tanque para W"), junto com a skill wa-feature, que cuida do fluxo de mockup, plano e subagentes.
---

# Integrar um mod opcional

O mod precisa carregar igual sem o outro mod. Quase todo erro de integração aparece quando o NeoForge inspeciona uma classe por reflexão e esbarra num tipo que não existe. Por isso a regra central: **tipos do mod só no pacote `compat/<mod>/` e no suporte de GameTest dele**, e nunca na assinatura de um método de classe `@EventBusSubscriber` ou `@GameTestHolder`. A run `runGameTestServer` roda sem nenhum mod opcional e é ela que prova isso.

Siga o fluxo da `wa-feature` (mockup, spec, plano, subagentes). Esta skill diz **o que** o plano precisa cobrir. Use como modelo o plano da Source (`docs/superpowers/plans/2026-10-08-aba-source.md`) e o do Tanque de Source (`2026-10-08-tanque-de-source.md`).

## Pesquisa antes do mockup

- Qual capability de bloco o mod expõe (a do Ars é `ars_nouveau:source`; o Mekanism usa a API dele de químicos)? É `int` ou `long`? Tem jar só de API?
- Qual a versão mínima com essa API? Isso vira o `versionRange` (Ars: `[5.2,)`, porque a 5.2.0 foi a primeira para 1.21.1).
- Abra o jar do mod em `run/mods` (`unzip -l ... 'assets/<mod>/blockstates/*'`) para ver blocos e estados reais. Nunca invente ids.
- Diferenças entre versões da API: no Ars 5.2, `setMaxSource` é abstrato, e o `TankTile` implementa sem `@Override` para compilar nas duas.

## Peças (uma etapa por camada)

**Etapa 1: o tipo de recurso**
- `build.gradle`: `compileOnly` (com `transitive = false` se não houver jar de API); o mod completo e as dependências dele em `clientMods` e numa configuração nova `<x>TestMods`; uma task `prepare<X>TestMods` (Sync para `run/gametest-<x>/mods`) e uma run `gameTestServer<X>` com `neoforge.enabledGameTestNamespaces=wirelessautomate_<x>` e a propriedade `wirelessautomate.<x>Tests=true`. Inclua o jar no `exclude` do `preserve` do `prepareClientMods`.
- `gradle.properties`: versões, com o Maven de onde vêm.
- `neoforge.mods.toml` (template): dependência com `type="optional"`, `versionRange`, `ordering="NONE"`, `side="BOTH"`.
- `.github/workflows/build.yml`: um passo para a run nova.
- `src/main/resources/data/wirelessautomate_<x>/structure/empty.nbt`: cópia do `empty.nbt`.
- `network/ResourceType.java`: entrada nova **no fim** do enum (chave, filtro, cartões, chave da vazão na config, vazões padrão por tier, id do mod). As chaves de NBT, componentes e config existentes não mudam.
- O caso novo no `switch` sem `default` do `NetworkManager.visit`: o compilador aponta onde falta.
- Ponte `network/<Xs>.java` no molde de `Sources.java`: `LOADED` pelo `ModList`, `capability()`, `move(...)`. É a única classe fora do `compat` que conhece a classe do compat, e só a chama com `LOADED`.
- Recurso de um valor só (como energia e Source): implemente um `ScalarAccess` em `compat/<x>/` e reuse o `ScalarTransfer`. Recurso com tipos (como fluidos e químicos): veja `ChemicalTransfer` e `compat/mekanism/`.
- Cliente: `client/ResourceStyle` (cor ARGB, ícone em `textures/gui/type/<x>.png`, nome), as duas `lang`, o comentário da config. Abas, chips do Vinculador, Tablet, Configurador e comandos leem do registro e acompanham sozinhos.
- GameTests em `gametest/<X>GameTests.java` (`@GameTestHolder("wirelessautomate_<x>")`) com blocos reais do mod, e `<X>TestSupport.java` com os tipos dele.

**Etapa 2 (opcional): o armazenamento do mod**
- Entrada em `StorageKind`, block entity sobre `StorageBlockEntity` ou `ScalarStorageBlockEntity`, e uma capability bulk própria (`wirelessautomate:bulk_<x>`) para o roteador mover milhões por operação.
- Exposição ao mod: a capability dele e, se o mod tiver uma rede própria (o `SourceManager` do Ars), o registro como provedor.
- Se a API for `int`, limite a visão a `Integer.MAX_VALUE` e responda certo nas bordas. Pegadas daqui: `canAcceptSource` precisava de `getSource() < getMaxSource()` e `setSource` virou delta para não derrubar o conteúdo.
- Bloco sempre registrado, mas fora da aba criativa, do JEI e da receita sem o mod.
- Texturas e modelo por `scripts/textures/gerar_texturas.py`, a partir das do próprio mod, para combinar (o dono pediu o "roxo do Ars").

**Fechamento**
- Páginas do guia nos dois idiomas, por `scripts/guide/gerar_guia.py`.
- `CLAUDE.md` (mapa do código, convenções), `docs/especificacao.md` e `docs/progresso.md`.
- e2e com o tipo novo: o roteador com N abas (`1f-roteador-largo`), o Tablet com N cartões e o Vinculador com N chips.

## Verificação

As quatro runs, mais o e2e:

```bash
./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServer<X>
```

A `runGameTestServer`, sem nenhum mod opcional, é a que pega um tipo do mod vazando para fora do `compat`.
