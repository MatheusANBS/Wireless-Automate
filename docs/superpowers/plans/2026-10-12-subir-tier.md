# Subir tier: plano de implementação

> **Para agentes:** execute tarefa por tarefa, um subagente por tarefa, com revisão entre elas. Os passos usam caixas (`- [ ]`). Cada tarefa termina compilando, com os testes passando e com um commit.

**Objetivo:** subir o tier custa o mesmo pela bancada e pelo cartão. Um anel por tier, usado em três receitas: a receita direta (o bloco do tier anterior no centro do anel = o bloco do tier novo, com os dados do item), o cartão de um degrau (Redstone no centro = cartão "N−1 → N") e o cartão que pula (cartão "k → N−1" no centro = cartão "k → N"). O cartão passa a ter origem (componente `upgrade_from`) e só vale num bloco exatamente daquele tier; os cartões antigos, sem origem, continuam valendo de qualquer tier abaixo.

**Spec:** [`docs/superpowers/specs/2026-10-12-subir-tier-design.md`](../specs/2026-10-12-subir-tier-design.md), que é a fonte das decisões (não reabra nenhuma). Mockup aprovado: `docs/preview/subir-tier.html`. Substitui a regra de custo de [`2026-10-09-upgrade-direto-design.md`](../specs/2026-10-09-upgrade-direto-design.md).

**Stack:** Java 21, NeoForge 1.21.1, JUnit 5, GameTests, JEI (só `compat/jei`), Python 3 (guia).

## Restrições globais

- Código, comentários, commits e docs em português; textos novos em `en_us.json` e `pt_br.json`, sempre os dois. Chaves de lang que deixarem de ser usadas saem dos dois arquivos.
- Ramo `roteador-de-alcance`. Commits sem linhas de atribuição (nada de `Co-Authored-By` nem `Claude-Session`), com o autor do dono. Um commit ao fim de cada tarefa, com a mensagem "Subir tier: ...". Sem push.
- **`ModPayloads.VERSION` não muda** (hoje `"14"`): nenhum payload do mod muda. O componente novo `upgrade_from` viaja dentro do `ItemStack` pelo stream codec registrado no `ModDataComponents` (o registro de tipos de componente é sincronizado pelo NeoForge no login, como os outros componentes do mod), e o tipo de receita novo viaja pelo stream codec do serializer na sincronização de receitas do vanilla. Nada disso passa pelo `ModPayloads`.
- **Chaves existentes de NBT, componentes e config não mudam de significado.** Os ids dos itens `tier_core_<tier>` e os ids das receitas `wirelessautomate:tier_core_<tier>` (e `tier_core_ultimate_atm`, `tier_core_ultimate_atm_star`) continuam os mesmos; muda só o tipo (`type`) do JSON. Componente novo: `wirelessautomate:upgrade_from` (o nome serializado do tier, `RouterTier.getSerializedName()`); ausente = cartão antigo.
- **Performance:** nada de tick, nada de varredura. Tudo acontece no clique, na bancada (`matches`/`assemble`) e no tooltip. O `matches` da receita nova faz no máximo 9 testes de ingrediente, como uma shaped vanilla.
- Mekanism, Ars e Allthemodium continuam opcionais: os anéis do Allthemodium e do All The Tweaks ficam em JSON com `neoforge:conditions` e tags `c:`, como hoje; nada de tipos ou `compileOnly` desses mods. Armazenamentos fora do ar (`StorageKind.loaded()` falso) não aparecem no JEI, como hoje.
- Nada de tipos do JEI fora de `compat/jei`. Classes de tela só em `client/`.
- GameTests: `@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, template `"empty"`. Teste que precisar de rede cria a própria (`NetworkSavedData.get(server).create(...)`). Teste pertinência (`contains`), não contagem. Espere o `onLoad` com `startSequence().thenWaitUntil(...)`. Teste que muda a config vai no lote `wirelessautomate_config` e devolve o valor em `try/finally`.
- Lógica pura (sem classes do Minecraft) vai com JUnit em `src/test/java` (aqui, `TierLadder` e `TierLadderTest`).
- Compile antes de rodar jogo (`./gradlew compileJava`). Com `BUILD FAILED`, pare e conserte; nunca espere um resultado que não virá.
- Nunca rode dois jogos (ou builds com jogo) ao mesmo tempo; um Gradle por vez. Não mexa em `run/options.txt`.
- **Verificação antes de cada commit:** `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` (nesta feature a run do Allthemodium entra em toda tarefa que mexe em regra, receita ou JSON, porque a escada e os anéis do ATM mudam com ela: Tarefas 1, 2 e 3; nas Tarefas 4 e 5 basta uma vez, no fim da 5). Nas tarefas que tocam tooltip, JEI ou guia (4 e 5), também o e2e: `./scripts/e2e.sh` (ou `WA_E2E="$PWD/run/e2e" ./gradlew runClient`), com `run/e2e/result.txt` = `OK`.
- Não invente: onde este plano diz **Pergunta**, investigue ou pergunte ao dono antes de seguir.

## O que o código mostra hoje (ponto de partida)

- **Um item por tier de destino:** `ModItems.TIER_CORES` (`EnumMap<RouterTier, DeferredItem<TierCoreItem>>`, Avançado a Ultimate, sem Básico), ids `tier_core_<tier>`. `TierCoreItem` guarda só o `tier` (destino).
- **Clique:** `TierCoreItem.useOn` chama `RouterBlock.tryUpgrade(level, pos, tier)` e, se não for roteador, `StorageBlock.tryUpgrade(level, pos, tier)`; os dois testam `state.getValue(TIER).canUpgradeTo(target)` e trocam só a propriedade `TIER` do `BlockState` (o block entity fica, então a configuração do roteador e o conteúdo do armazenamento continuam). `RouterBlock.useItemOn` e `StorageBlock.useItemOn` devolvem `SKIP_DEFAULT_BLOCK_INTERACTION` com um `TierCoreItem` na mão, então o clique **com ou sem Shift** chega ao `useOn`. Não há mensagem na action bar, nem de sucesso nem de recusa.
- **Regra:** `RouterTier.canUpgradeTo(target)` → `TierLadder.canUpgrade(REQUIRED_MODS, from, to, modLoaded)` = `to > from && loaded(to)` (qualquer tier abaixo). `TierLadder.next`/`previous` pulam os degraus cujo mod não está carregado; JUnit em `src/test/java/.../block/TierLadderTest.java`.
- **Bancada bloco + cartão:** `recipe/RouterUpgradeRecipe` (`CustomRecipe`, logo especial, tipo `wirelessautomate:router_upgrade`, JSON `recipe/router_upgrade.json`): exatamente um `RouterBlockItem` **ou** `StorageBlockItem` e um `TierCoreItem`; o resultado é `router.copyWithCount(1)` com o `BLOCK_STATE` no tier novo, **mantendo todos os outros componentes** do item.
- **O que o item do bloco carrega:** o roteador quebrado solta os cartões e upgrades (`RouterBlock.onRemove`) e a loot table `loot_table/blocks/router.json` só copia o `tier` (`copy_state`); o item do roteador **não** carrega faces, redes nem máquinas, só o tier e o que estiver no `ItemStack` (por exemplo o `custom_name` da bigorna). O armazenamento quebrado leva `storage_contents` (referência ao `StorageSavedData`) e `storage_filter` (`copy_components` na loot table e `StorageBlockEntity.collectImplicitComponents`). Ver o ponto a confirmar 1.
- **Receitas dos cartões:** `recipe/tier_core_<tier>.json`, todas `minecraft:crafting_shaped` com o cartão anterior no centro; Allthemodium, Vibranium e Unobtainium com `neoforge:mod_loaded allthemodium`; o Ultimate em três arquivos (`tier_core_ultimate`: sem o ATM, Cartão Esmeralda; `tier_core_ultimate_atm`: ATM sem All The Tweaks, Cartão Unobtainium; `tier_core_ultimate_atm_star`: ATM + All The Tweaks).
- **Editor de receitas:** `RecipeEditor.isCraftingSerializer` só aceita `RecipeSerializer.SHAPED_RECIPE` e `SHAPELESS_RECIPE`; `RecipeJson.toDraft` só lê `minecraft:crafting_shaped`/`shapeless`. Hoje os nove `tier_core_*` aparecem no editor. `RecipeEditorGameTests.keepsConditions` usa o JSON do jar `recipe/tier_core_allthemodium.json`.
- **JEI:** `WirelessAutomateJeiPlugin.registerItemSubtypes` registra o tier como subtipo do roteador e dos armazenamentos (não dos cartões); `registerRecipes` monta, para cada par (origem carregada, destino com cartão acima), uma `ShapelessRecipe` de exibição bloco + cartão (`jei/router_upgrade_<o>_<d>`, `jei/<kind.id>_upgrade_<o>_<d>`). O javadoc diz que o JEI não mostra o `RouterUpgradeRecipe` por ser receita especial.
- **Aba criativa:** `ModCreativeTabs` põe `ModItems.TIER_CORES.get(tier).get()` (sem componente) para cada tier carregado; o JEI lista o que está ali.
- **Tooltip:** `TierCoreItem.appendHoverText` mostra `tier_core.upgrades` ("Sobe para %s, de qualquer tier abaixo:") e as linhas de vazão, alcance e capacidade do **destino** (`line(key, value)`, um `%s` por linha), o `use` e o `requires`.
- **Guia:** `scripts/guide/gerar_guia.py`, página `upgrade-cards.md` (pt ~:624–672, en ~:674–720), com `<RecipeFor id="wirelessautomate:tier_core_<tier>" />`; a tabela de tiers usa `card_table`/`CARD_IMAGES` (~:244–253).
- **e2e:** `client/DevEndToEnd` usa cartões sem origem na vitrine (~:1848–1851, ~:1893–1899) e no passo "Baú: Cartão de Upgrade no bloco" (~:2185–2200, `chestUpgrade()` = `EMERALD.next()`), que sobe um Baú **Esmeralda**.

## Pontos a confirmar (antes da tarefa indicada)

1. **"Com todos os dados" na receita direta (decisão 2, Tarefa 3).** A receita direta, como o `RouterUpgradeRecipe`, copia **o `ItemStack` inteiro** e troca só o tier: o armazenamento mantém o conteúdo (pela referência `storage_contents`) e o filtro; o roteador mantém o que o item carrega (nome da bigorna e qualquer outro componente, inclusive um `block_entity_data` de um Ctrl + clique do meio no criativo). Mas o item do roteador quebrado **não carrega** faces, redes, cartões, upgrades nem máquinas (loot table só com o tier; `onRemove` solta cartões e upgrades), então "subir pela bancada com a configuração" só existe se o item a tiver. O plano **não** muda o drop do roteador (seria outra feature). Confirme com o dono que está bem e que o guia diz: "no lugar, com o cartão, o roteador mantém toda a configuração; na bancada, o roteador sobe como item (nome mantido), e o armazenamento mantém o conteúdo".
2. **Shift (decisão 6, Tarefa 2).** A spec diz "Shift + clique direito"; hoje o clique com o cartão funciona com e sem Shift (o `useItemOn` dos dois blocos pula para o `useOn` do cartão). O plano **mantém os dois** (não tira um gesto que funciona) e escreve "Shift + clique" no tooltip e no guia, porque é o que funciona em qualquer bloco. Se o dono quiser **só** com Shift, o `useOn` passa a exigir `context.isSecondaryUseActive()` e os dois `useItemOn` deixam de pular sem Shift.
3. **Bloco "órfão" num tier do Allthemodium sem o mod (Tarefas 1 e 2).** Um roteador que ficou no Vibranium depois que o ATM saiu: pela regra de origem exata, só um cartão de origem Vibranium (que não se fabrica mais sem o ATM) ou um cartão antigo o sobe no lugar. O plano deixa a **receita direta** aceitá-lo (o centro vale se `bloco.next() == tier do anel`, e sem o ATM `VIBRANIUM.next() == ULTIMATE`), então ele sobe na bancada com o anel do Ultimate. No lugar, fica preso até ser quebrado. Confirme que basta.
4. **Overrides antigos do editor de receitas (Tarefa 3).** Um override em `config/wirelessautomate/recipes/data/wirelessautomate/recipe/tier_core_<tier>.json`, feito antes desta versão, é uma `crafting_shaped` com o mesmo id e ganha do JSON do jar: o cartão sai **sem origem** (cartão antigo, de qualquer tier abaixo) e com o anel velho. O `RecipeEditor` passa a não listar esses ids (o padrão do jar não é mais shaped), então o jogador não vê o override nem consegue restaurá-lo pela tela. Proposta do plano: na carga do editor, um aviso no log para cada override em disco cujo padrão no jar não é editável (`WirelessAutomate.LOGGER.warn("Override de receita sem padrão editável: ...")`), sem apagar nada. **Pergunta:** o dono quer também que a tela do editor liste esses órfãos com "Restaurar"? (Fora do escopo da spec; só se ele pedir.)
5. **GuideME com o tipo novo (Tarefa 5).** Não está confirmado que o `<RecipeFor>` do GuideME desenha um `CraftingRecipe` que não é `ShapedRecipe`/`ShapelessRecipe`. Confira na captura `guia-upgrade-cards*.png` do e2e. Se não desenhar, troque os `<RecipeFor>` da página por uma tabela com os anéis em `<ItemImage>` (gerada pelo script a partir de uma lista no próprio `gerar_guia.py`) e avise o dono.
6. **JEI mostra a receita nova sozinho? (Tarefa 4).** O NeoForge 21.1.248 **não tem** `IShapedRecipe` (conferido no `neoforge-21.1.248-sources.jar`: não há `net/neoforged/neoforge/common/crafting/IShapedRecipe.java`), e o livro de receitas do vanilla só posiciona ingredientes de um `ShapedRecipe`. Por isso o plano faz o `TierUpgradeRecipe` **estender `ShapedRecipe`** (construtor público `ShapedRecipe(String group, CraftingBookCategory, ShapedRecipePattern, ItemStack result)`), com o padrão "anel + Redstone" e o resultado "cartão de um degrau", e sobrescreve `matches`/`assemble` para os três centros. Assim o livro de receitas, o JEI e o GuideME veem uma shaped comum (o cartão de um degrau) a partir do próprio JSON. Não está confirmado que o JEI e o GuideME aceitam uma subclasse de `ShapedRecipe` com serializer próprio; confira no e2e. Se o JEI não desenhar, o plugin gera também o cartão de um degrau (`jei/tier_core_step_<tier>`); anote no relatório.

## Decisões de implementação (justificadas)

### Um JSON por tier, de um tipo próprio, gera as três receitas

O anel de cada tier mora num lugar só: o JSON `recipe/tier_core_<tier>.json` (mesmos ids de hoje), agora do tipo `wirelessautomate:tier_upgrade`. Uma única classe, `recipe/TierUpgradeRecipe`, olha o **centro** da grade e decide qual das três receitas é:

| Centro | Condição | Resultado |
| --- | --- | --- |
| o ingrediente `step` (padrão: Redstone) | sempre | cartão `tier_core_<N>` com `upgrade_from` = `N.previous()` (o degrau anterior na escada **desta instância**) |
| um `TierCoreItem` **com** `upgrade_from` = k, destino T | `T.next() == N` (e `k < T`) | cartão `tier_core_<N>` com `upgrade_from` = k |
| um `RouterBlockItem` ou `StorageBlockItem` de tier B | `B.next() == N` | o mesmo item (todos os componentes), com o `BLOCK_STATE` no tier N |

Por que assim, e não três JSON por tier nem JSON vanilla:

- **Um anel, um lugar.** Três JSON por tier repetiriam o anel três vezes (nove tiers e variantes = 27 arquivos), e um datapack ou o KubeJS que mudasse um esqueceria os outros dois, quebrando "o mesmo custo pelos dois caminhos". Com um JSON, trocar o anel troca as três receitas.
- **A escada não vai para o JSON.** A origem do cartão de um degrau e o bloco aceito no centro saem do `TierLadder` em tempo de execução (`previous`/`next`), não de um campo do JSON. As três variantes do Ultimate continuam separadas só pela condição de mod (o anel muda com o All The Tweaks), e cada uma acerta sozinha o degrau anterior: Esmeralda sem o ATM, Unobtainium com ele.
- **JSON vanilla não preserva os dados nem a origem.** Uma `crafting_shaped` com um roteador no centro aceitaria o item mas devolveria um roteador novo, sem o nome e sem o conteúdo do armazenamento, e não teria como copiar `upgrade_from` do cartão do centro para o resultado.
- **Continua editável por datapack e KubeJS:** o mesmo id, um JSON legível (`pattern` e `key` iguais aos de uma shaped). O **editor de receitas do mod deixa de listar os cartões** (ele só trata `crafting_shaped`/`shapeless`, `RecipeEditor.isCraftingSerializer`); fica fora do escopo da spec. Anote no guia do editor, se ele cita os cartões, e no relatório final.

Formato do JSON (o centro do `pattern` é sempre um espaço; `step` é opcional):

```json
{
  "type": "wirelessautomate:tier_upgrade",
  "category": "misc",
  "tier": "elite",
  "pattern": [
    "NON",
    "O O",
    "NON"
  ],
  "key": {
    "N": { "item": "minecraft:netherite_ingot" },
    "O": { "item": "minecraft:obsidian" }
  },
  "step": { "item": "minecraft:redstone" }
}
```

Com condição, como hoje: `"neoforge:conditions": [...]` no topo (o NeoForge trata isso antes do codec, para qualquer tipo de receita).

### Regra do cartão com origem (`TierLadder`, puro)

- **Usar** (clique e bancada bloco + cartão): com origem k, só se o tier do bloco é **exatamente** k e o destino está acima e carregado; sem origem (cartão antigo), a regra de hoje (`canUpgrade`: qualquer tier abaixo).
- **Estender** (cartão que pula e receita direta): o item do centro está no degrau **imediatamente** abaixo do anel na escada desta instância (`next(centro) == anel`).
- **Origem do cartão de um degrau:** `previous(anel)`.

Com o Allthemodium: `previous(ULTIMATE) == UNOBTAINIUM`, `next(EMERALD) == ALLTHEMODIUM`. Sem ele: `previous(ULTIMATE) == EMERALD`, `next(EMERALD) == ULTIMATE`, e `next(VIBRANIUM) == ULTIMATE` (bloco órfão, ponto 3).

### Anéis (decisão 1 da spec; os mesmos itens e tags de hoje)

| Arquivo | Condições | `pattern` | `key` |
| --- | --- | --- | --- |
| `tier_core_advanced.json` | nenhuma | `GDG`, `D D`, `GDG` | G `minecraft:gold_ingot`, D `minecraft:diamond` |
| `tier_core_elite.json` | nenhuma | `NON`, `O O`, `NON` | N `minecraft:netherite_ingot`, O `minecraft:obsidian` |
| `tier_core_emerald.json` | nenhuma | `BSB`, `R R`, `BYB` | B `minecraft:emerald_block`, S `minecraft:nether_star`, R `minecraft:crying_obsidian`, Y `minecraft:ender_eye` |
| `tier_core_allthemodium.json` | ATM | `IBI`, `I I`, `IBI` | I `c:ingots/allthemodium`, B `c:storage_blocks/allthemodium` |
| `tier_core_vibranium.json` | ATM | `IAI`, `B B`, `IAI` | I `c:ingots/vibranium`, A `c:ingots/vibranium_allthemodium_alloy`, B `c:storage_blocks/vibranium` |
| `tier_core_unobtainium.json` | ATM | `IAI`, `B B`, `IAI` | I `c:ingots/unobtainium`, A `c:ingots/unobtainium_vibranium_alloy`, B `c:storage_blocks/unobtainium` |
| `tier_core_ultimate.json` | não ATM | `XDX`, `B B`, `XBX` | X `minecraft:echo_shard`, D `minecraft:dragon_egg`, B `minecraft:netherite_block` |
| `tier_core_ultimate_atm.json` | ATM e não All The Tweaks | `XDX`, `B B`, `XBX` | idem |
| `tier_core_ultimate_atm_star.json` | ATM e All The Tweaks | `SDS`, `U U`, `SUS` | S `allthetweaks:atm_star_shard`, D `minecraft:dragon_egg`, U `c:storage_blocks/unobtainium_allthemodium_alloy` |

O que muda em relação a hoje: o centro (era o cartão anterior, ou o bloco de ouro no Avançado), o anel do Elite (era ` S `/`N N`/` N ` com estrela do Nether e três netherites) e um olho do Ender do Esmeralda, que vira a estrela do Nether (decisões 1 e 9).

## Mapa de arquivos

Pacote base `src/main/java/io/github/matheusanbs/wirelessautomate/`.

| Arquivo | Papel na mudança |
| --- | --- |
| `block/TierLadder.java`, `src/test/java/.../block/TierLadderTest.java` | Regras puras novas: `cardApplies`, `oneStep` (com JUnit) |
| `block/RouterTier.java` | `CODEC`/`STREAM_CODEC` do tier; `acceptsCard(origin, target)`, `stepsTo(ring)` |
| `registry/ModDataComponents.java` | Componente `upgrade_from` (`RouterTier`) |
| `item/TierCoreItem.java` | Origem (`origin`, `card`), uso com origem e mensagens na action bar, nome "Cartão de Upgrade: A → B", tooltip com origem e de cartão antigo |
| `block/RouterBlock.java`, `storage/StorageBlock.java` | `tryUpgrade` com a origem do cartão |
| `recipe/RouterUpgradeRecipe.java` | Bloco + cartão pela regra com origem; `withTier(stack, tier)` compartilhado |
| `recipe/TierUpgradeRecipe.java` (novo), `registry/ModRecipes.java` | O tipo `wirelessautomate:tier_upgrade` (receita, codec, stream codec, serializer) |
| `src/main/resources/data/wirelessautomate/recipe/tier_core_*.json` (os nove) | Os anéis no formato novo |
| `registry/ModCreativeTabs.java` | Cartões com origem (o de um degrau) na aba criativa |
| `recipe/edit/RecipeEditor.java` | Aviso no log de override sem padrão editável (ponto 4) |
| `compat/jei/WirelessAutomateJeiPlugin.java` | Subtipo do cartão pela origem; diretas, exemplo de cartão que pula e pares bloco + cartão com origem |
| `gametest/RecipeGameTests.java`, `RouterGameTests.java`, `StorageGameTests.java`, `AtmGameTests.java`, `RecipeEditorGameTests.java`, `TierUpgradeGameTests.java` (novo) | Testes |
| `client/DevEndToEnd.java` | Cartões com origem na vitrine e no Baú; capturas do tooltip e do JEI |
| `lang/en_us.json`, `lang/pt_br.json` | Nome, tooltip e action bar |
| `scripts/guide/gerar_guia.py` (e páginas geradas), `docs/especificacao.md`, `CLAUDE.md`, `docs/progresso.md` | Guia e docs |

---

### Tarefa 1: a regra pura com origem

**Objetivo:** a regra existe e está testada, sem nada usar ainda.

**Arquivos:** `block/TierLadder.java`, `src/test/java/io/github/matheusanbs/wirelessautomate/block/TierLadderTest.java`, `block/RouterTier.java`.

- [ ] `TierLadder` (javadoc em cada método, dizendo o porquê do custo igual):
  - `static boolean cardApplies(List<@Nullable String> requiredMods, int origin, int to, int block, Predicate<String> modLoaded)`: `origin < 0` (cartão antigo) → `canUpgrade(requiredMods, block, to, modLoaded)`; senão `block == origin && canUpgrade(requiredMods, origin, to, modLoaded)`;
  - `static boolean oneStep(List<@Nullable String> requiredMods, int from, int to, Predicate<String> modLoaded)`: `next(requiredMods, from, modLoaded) == to` (e `to` carregado, que o `next` já garante). É o "estender um degrau" do cartão que pula e o centro da receita direta;
  - `canUpgrade` fica (cartão antigo); `previous` já dá a origem do cartão de um degrau.
- [ ] JUnit novos em `TierLadderTest` (com os `MODS`, `WITH` e `WITHOUT` que já existem):
  - `cardWithOriginNeedsExactTier`: origem Básico, destino Elite: bloco Básico sim; Avançado, Elite e Ultimate não;
  - `cardWithOriginNeverGoesDown`: origem Elite, destino Avançado (cartão impossível, montado à mão): não;
  - `legacyCardKeepsAnyLowerTier`: `origin = -1` repete os casos de `canUpgrade` (Básico → Ultimate sim, Elite → Avançado não, Elite → Elite não);
  - `cardToAtmTierNeedsTheMod`: origem Esmeralda, destino Allthemodium: com o mod sim, sem não;
  - `oneStepFollowsTheLadder`: sem o mod, Esmeralda → Ultimate sim e Esmeralda → Allthemodium não; com o mod, Esmeralda → Allthemodium sim e Esmeralda → Ultimate não; Básico → Elite não (pula); Ultimate → nada;
  - `oneStepFromAnOrphanAtmTier`: sem o mod, Vibranium → Ultimate sim (ponto 3);
  - `stepCardOrigin`: `previous` do Ultimate = Esmeralda sem o mod e Unobtainium com ele; do Avançado = Básico.
- [ ] `RouterTier`:
  - `public static final Codec<RouterTier> CODEC = StringRepresentable.fromEnum(RouterTier::values)` e `STREAM_CODEC` pelo nome (`ByteBufCodecs.STRING_UTF8.map(...)`, recusando nome desconhecido com `DecoderException`) ou pelo `ordinal` (`ByteBufCodecs.idMapper`); prefira o nome, como o NBT (o javadoc da classe diz que a posição só vale dentro de uma versão);
  - `boolean acceptsCard(@Nullable RouterTier origin, RouterTier target)` (este = tier do bloco), delegando a `TierLadder.cardApplies` com `origin == null ? -1 : origin.ordinal()`;
  - `boolean stepsTo(RouterTier ring)`, delegando a `TierLadder.oneStep`;
  - `canUpgradeTo` fica, com o javadoc "regra do cartão antigo, sem origem".
- [ ] Verificação completa (sem e2e). Commit "Subir tier: regra do cartão com origem no TierLadder".

### Tarefa 2: componente `upgrade_from`, uso do cartão, nome, tooltip e bancada bloco + cartão

**Objetivo:** um cartão com origem funciona no clique e na bancada; o cartão antigo continua como hoje. Ainda não se fabrica cartão com origem (os testes o montam à mão).

**Arquivos:** `registry/ModDataComponents.java`, `item/TierCoreItem.java`, `block/RouterBlock.java`, `storage/StorageBlock.java`, `recipe/RouterUpgradeRecipe.java`, `lang/en_us.json`, `lang/pt_br.json`, `gametest/RouterGameTests.java`, `gametest/StorageGameTests.java`, `gametest/RecipeGameTests.java`, `gametest/TierUpgradeGameTests.java` (novo).

- [ ] `ModDataComponents.UPGRADE_FROM`: `registerComponentType("upgrade_from", builder -> builder.persistent(RouterTier.CODEC).networkSynchronized(RouterTier.STREAM_CODEC))`, com javadoc: o tier de origem do Cartão de Upgrade; ausente = cartão antigo (de antes desta versão), que sobe de qualquer tier abaixo.
- [ ] `TierCoreItem`:
  - `static @Nullable RouterTier origin(ItemStack stack)` e `static ItemStack card(RouterTier to, @Nullable RouterTier from)` (um cartão `tier_core_<to>` com o componente; `from == null` = sem componente);
  - `useOn`: lê o `BlockState` em `context.getClickedPos()`; se não é `RouterBlock` nem `StorageBlock`, `PASS` (como hoje). Se o tier do bloco não aceita o cartão (`blockTier.acceptsCard(origin(stack), tier)`):
    - com origem: no servidor, `player.displayClientMessage(..., true)` com `tier_core.refused.router` ou `tier_core.refused.storage` (argumento: o nome do tier de origem), e devolve `InteractionResult.FAIL` nos dois lados (o cartão fica na mão);
    - sem origem: `PASS`, como hoje;
  - se aceita: `RouterBlock.tryUpgrade`/`StorageBlock.tryUpgrade` com a origem, gasta o cartão (fora do criativo), som de hoje, e no servidor a action bar `tier_core.upgraded.router` ("Roteador %s → %s") ou `tier_core.upgraded.storage` ("%s %s → %s", com o nome do bloco por `state.getBlock().getName()`), como no mockup;
  - `getName(ItemStack)`: com origem, `Component.translatable(KEY + "named", tier de origem, tier do cartão)` ("Cartão de Upgrade: Básico → Elite"); sem origem, o nome de hoje;
  - `appendHoverText`, **com origem**: `tier_core.upgrades_from` ("Sobe um roteador %s para %s", `GRAY`), `tier_core.only` ("Vale só num roteador %s", `GOLD` ou `YELLOW`), e as linhas de hoje com o valor `tier_core.change` = "%s → %s" (o valor da origem e o do destino, os dois em `AQUA`, pelo `rate`/`range`/`capacity` de cada tier). **Sem origem:** `tier_core.legacy` ("Cartão antigo: sobe qualquer tier abaixo de %s", `GRAY`) e as linhas de hoje, com um valor só (o do destino). Depois, nos dois: `use` e `requires` como hoje;
  - o javadoc da classe passa a explicar origem, cartão antigo e as três receitas.
- [ ] `RouterBlock.tryUpgrade(Level, BlockPos, RouterTier target, @Nullable RouterTier origin)` e `StorageBlock.tryUpgrade(...)` igual: a condição vira `!tier.acceptsCard(origin, target)`. O `tryUpgrade(level, pos, target)` de três argumentos fica como atalho de **cartão antigo** (`origin = null`), porque `ChunkLoaderGameTests` (~:170), `TabletGameTests` (~:169), `RouterGameTests`, `StorageGameTests` e `AtmGameTests` o usam só para pôr um bloco num tier; o javadoc diz isso.
- [ ] `RouterUpgradeRecipe.upgraded`: guarda o `ItemStack` do cartão (não só o tier) e testa `RouterBlockItem.tierOf(router).acceptsCard(TierCoreItem.origin(card), core)`. Extraia `public static ItemStack withTier(ItemStack block, RouterTier tier)` (cópia de 1 com o `BLOCK_STATE` trocado) para a receita direta da Tarefa 3 usar. Javadoc: "um cartão com origem só vale num bloco daquele tier; o antigo, de qualquer tier abaixo".
- [ ] Lang, nos dois arquivos:
  - novas: `tier_core.named` ("Cartão de Upgrade: %s → %s" / "Upgrade Card: %s → %s"), `tier_core.upgrades_from` ("Sobe um roteador %s para %s" / "Raises a %s router to %s"), `tier_core.only` ("Vale só num roteador %s" / "Only works on a %s router"), `tier_core.legacy` ("Cartão antigo: sobe qualquer tier abaixo de %s" / "Old card: raises any tier below %s"), `tier_core.change` ("%s → %s"), `tier_core.refused.router` ("Este cartão sobe um roteador %s" / "This card raises a %s router"), `tier_core.refused.storage` ("Este cartão sobe um armazenamento %s" / "This card raises a %s storage"), `tier_core.upgraded.router` ("Roteador %s → %s" / "Router %s → %s"), `tier_core.upgraded.storage` ("%s %s → %s");
  - `tier_core.use`: "Shift + clique num roteador ou armazenamento colocado, ou junte os dois na bancada" / "Shift + use on a placed router or storage, or combine both in a crafting table" (ponto 2);
  - sai `tier_core.upgrades` (substituída por `upgrades_from` e `legacy`); confira com `grep -rn 'tier_core.upgrades"' src` que nada mais a usa (o `RecipeGameTests` não a usa).
- [ ] GameTests novos em `TierUpgradeGameTests` (template `empty`; nenhum precisa de rede):
  - `cardWithOriginOnClick`: roteador Básico colocado; `TierCoreItem.card(ELITE, BASIC)` aplicado pelo `useOn` de um jogador de teste (`helper.makeMockPlayer`, com `UseOnContext` montado como nos testes do Vinculador; procure o padrão em `RangeGameTests`/`LinkerGameTests`) sobe para Elite e gasta o cartão; o block entity é o mesmo (`helper.getBlockEntity` antes e depois, `==`) e uma face configurada antes continua igual;
  - `cardWithOriginRefusesOtherTier`: roteador Avançado + cartão Básico → Elite: o tier não muda, o cartão fica, o resultado é `FAIL`;
  - `cardWithOriginOnStorage`: Baú Básico com itens + cartão Básico → Elite: sobe e o conteúdo fica; Baú Avançado + o mesmo cartão: recusa;
  - `legacyCardStillJumps`: cartão sem componente sobe Básico → Elite e Avançado → Elite; não desce;
  - `cardNameAndTooltip`: `getHoverName` do cartão Básico → Elite contém os dois nomes de tier; o tooltip tem `upgrades_from` e a linha de itens com `change` (os dois valores); o do cartão antigo tem `legacy` e não tem `upgrades_from`;
  - `benchUsesOrigin`: `RouterUpgradeRecipe` com roteador Básico + cartão Básico → Elite casa e devolve Elite com o `custom_name`; roteador Avançado + o mesmo cartão não casa; roteador Básico + cartão antigo Elite casa (como hoje).
- [ ] Ajustes nos existentes:
  - `RouterGameTests.tierCoreJumpsToAnyHigherTier` (~:45–57) e o teste da escada sem o ATM (~:70–80): continuam valendo pelo atalho (cartão antigo); só o javadoc/nome, se disser "cartão" sem dizer "antigo";
  - `StorageGameTests.chestUpgradeRecipe` (~:511–533): continua (cartões antigos); acrescente o caso com origem (Baú Básico + cartão Básico → Elite casa; Baú Avançado + o mesmo não);
  - `RecipeGameTests.routerUpgradeRecipe` (~:122–185): o laço de pares continua com cartões antigos; a asserção de tooltip (~:175–185) passa a usar um cartão com origem (Básico → Avançado: "64 → 256"? confira os valores da config de teste; hoje ela procura "256" e "512") **e** um antigo (a linha só com "256").
- [ ] Verificação completa (sem e2e; o tooltip vai para o e2e na Tarefa 4). Commit "Subir tier: cartão com origem no clique e na bancada".

### Tarefa 3: o tipo de receita `tier_upgrade` e os anéis

**Objetivo:** as três receitas por tier, com um anel cada, e a aba criativa com cartões com origem. Não se fabrica mais cartão sem origem.

**Arquivos:** `recipe/TierUpgradeRecipe.java` (novo), `registry/ModRecipes.java`, os nove `src/main/resources/data/wirelessautomate/recipe/tier_core_*.json`, `registry/ModCreativeTabs.java`, `recipe/edit/RecipeEditor.java`, `gametest/RecipeGameTests.java`, `gametest/AtmGameTests.java`, `gametest/RecipeEditorGameTests.java`, `gametest/TierUpgradeGameTests.java`.

- [ ] `TierUpgradeRecipe extends ShapedRecipe` (ponto 6; não há `IShapedRecipe` no NeoForge 21.1.248):
  - campos próprios: `RouterTier tier` (destino), `ShapedRecipePattern ring` (o decodificado do JSON, 3×3 com o centro vazio e com o `Data` para o codec reescrever o JSON), `Ingredient step`. Ao `super(...)` vão o grupo `""`, a categoria, um `ShapedRecipePattern` 3×3 **sem** `Data` com os ingredientes do anel e o `step` no índice 4 (`new ShapedRecipePattern(3, 3, ingredientes, Optional.empty())`, construtor público) e o resultado `TierCoreItem.card(tier, tier.previous())`. Atenção: `ShapedRecipePattern` só se codifica com o `Data`, por isso o codec usa o `ring`, não o `pattern` herdado;
  - `MAP_CODEC` (`RecordCodecBuilder.mapCodec`): `category` (`CraftingBookCategory.CODEC`, padrão `MISC`), `tier` (`RouterTier.CODEC`), o `ShapedRecipePattern.MAP_CODEC` (os campos `pattern` e `key`, iguais aos de uma shaped) e `step` (`Ingredient.CODEC_NONEMPTY`, `optionalFieldOf` com padrão `Ingredient.of(Items.REDSTONE)`). Valide com `DataResult.error`: o tier tem cartão (`ModItems.TIER_CORES.containsKey`, ou seja, não é Básico); o padrão tem 3 × 3 depois do encolhimento do vanilla (`ring.width() == 3 && ring.height() == 3`); o centro (`ring.ingredients().get(4)`) está vazio. Um anel com uma borda vazia encolhe e é recusado com uma mensagem clara no log. **Pergunta para o implementador:** o resultado do `super` depende de `tier.previous()`, que depende do `ModList`; confira que o `ModList` já está pronto quando as receitas são lidas (está, na carga de datapacks do servidor e na sincronização do cliente), senão monte o resultado preguiçosamente em `getResultItem`;
  - `STREAM_CODEC`: `CraftingBookCategory.STREAM_CODEC`, `RouterTier.STREAM_CODEC`, `ShapedRecipePattern.STREAM_CODEC`, `Ingredient.CONTENTS_STREAM_CODEC` (as receitas vão para o cliente no 1.21.1, e o JEI lê do cliente);
  - `result(CraftingInput input, HolderLookup.Provider registries)`, privado, usado por `matches` e `assemble`: se `input.width() != 3 || input.height() != 3`, vazio. Centro = `input.getItem(4)`; o resto confere pelo `ring.matches(CraftingInput.of(3, 3, itens com o índice 4 vazio))` (o `ShapedRecipePattern.matches` já aceita o espelhado, como uma shaped vanilla). Depois, pelo centro (tabela das "Decisões de implementação"):
    - `step.test(centro)` → `TierCoreItem.card(tier, tier.previous())` (se `previous()` for `null`, vazio);
    - `centro.getItem() instanceof TierCoreItem core && TierCoreItem.origin(centro) != null && core.tier().stepsTo(tier)` → `TierCoreItem.card(tier, origin)`; cartão antigo no centro não casa (spec, fora do escopo: converter cartões antigos);
    - `RouterBlockItem`/`StorageBlockItem` com `RouterBlockItem.tierOf(centro).stepsTo(tier)` → `RouterUpgradeRecipe.withTier(centro, tier)`;
    - e só se `tier.loaded()`.
  - sobrescreva `matches` (resultado não vazio) e `assemble` (o resultado); `getResultItem`, `getIngredients` (anel + `step`), `getWidth`/`getHeight`, `canCraftInDimensions` e `isSpecial() == false` vêm do `ShapedRecipe`; sobrescreva `getSerializer()` = `ModRecipes.TIER_UPGRADE.get()`. Confira que o `ShapedRecipe.isIncomplete()` não marca a receita (ele olha os ingredientes do padrão herdado);
  - o centro gasta um (os ingredientes de bancada gastam um de cada slot; um armazenamento vazio empilhado no centro sai um só).
- [ ] `ModRecipes.TIER_UPGRADE`: `RECIPE_SERIALIZERS.register("tier_upgrade", () -> new RecipeSerializer<>() { codec(), streamCodec() })` (ou uma classe `Serializer` aninhada), com javadoc `{"type": "wirelessautomate:tier_upgrade"}`. Atualize o javadoc do `ROUTER_UPGRADE` ("roteador ou armazenamento + cartão; com origem, só no tier dela").
- [ ] Os nove JSON, pela tabela de anéis das "Decisões de implementação", mantendo `neoforge:conditions` iguais aos de hoje, `category` `misc`, sem `result` e sem `step` (padrão). Confira que o `tier` de cada arquivo é o do nome (os três do Ultimate com `"tier": "ultimate"`).
- [ ] `ModCreativeTabs`: em vez de `TIER_CORES.get(tier).get()`, `TierCoreItem.card(tier, tier.previous())` para cada tier carregado com cartão (o JEI lista o que está na aba; um cartão antigo não se fabrica mais). Ajuste o comentário.
- [ ] `RecipeEditor` (ponto 4): ao listar, um `LOGGER.warn` por override em disco sem padrão editável no jar. Um aviso por id por carga do servidor (não por abertura de tela). Se o dono responder que quer mais, pare e pergunte.
- [ ] GameTests novos em `TierUpgradeGameTests`:
  - `stepCardRecipe`: para cada tier carregado com cartão, anel (pelos itens da tabela; nos do ATM, só na run deles) + Redstone = cartão com `upgrade_from` = `previous()`, pela receita de id `tier_core_<tier>` (sem o ATM: `tier_core_ultimate` e origem Esmeralda);
  - `jumpCardRecipe`: anel do Elite + cartão Básico → Avançado = cartão Básico → Elite; anel do Esmeralda + cartão Básico → Elite = Básico → Esmeralda; anel do Esmeralda + cartão Básico → Avançado não casa (não estende dois degraus); anel do Elite + cartão antigo Avançado não casa; anel do Ultimate (sem o ATM) + cartão Avançado → Esmeralda = Avançado → Ultimate;
  - `directRecipeKeepsRouterData`: anel do Elite + roteador Avançado com `custom_name` (e um `block_entity_data` posto à mão, se for simples) = roteador Elite com os mesmos componentes, fora o `block_state`; roteador Básico no centro não casa (sobe um degrau só); roteador Elite no centro não casa;
  - `directRecipeKeepsStorageContents`: Baú Avançado com `storage_contents` e `storage_filter` no centro do anel do Elite = Baú Elite com os dois componentes iguais; colocar o item (como em `StorageGameTests`, procure o teste de quebrar e recolocar) devolve o conteúdo. Repita o resultado (sem colocar) para o Tanque e a Bateria; o Tanque Químico e o de Source só se `kind.loaded()` (nas runs deles);
  - `sameRingThreeRecipes`: para cada tier carregado, as três grades (Redstone, cartão um degrau abaixo com origem Básico, bloco um degrau abaixo) casam com o **mesmo** id `tier_core_<tier>` (é o que garante o mesmo anel);
  - `sameCostBothWays`: de Básico até cada tier carregado, a soma dos ingredientes do anel (`getIngredients()` sem o índice 4) das receitas de `next` em `next` até o destino, contada por item de exemplo (`ingredient.getItems()[0].getItem()`), é igual nos dois caminhos: bancada degrau a degrau = os mesmos anéis; cartão = os mesmos anéis + 1 Redstone. Escreva o teste como a montagem dos dois caminhos pela receita real (bancada: `assemble` com o bloco; cartão: `assemble` com a Redstone e depois com o cartão), somando o que foi posto na grade;
  - `noCardWithoutOrigin`: nenhuma receita de bancada carregada (laço por `getAllRecipesFor(RecipeType.CRAFTING)`) tem `getResultItem` = um `TierCoreItem` sem `upgrade_from`.
- [ ] Ajustes nos existentes:
  - `RecipeGameTests.tierCoreRecipes` (~:73–120): reescreva pelos anéis novos (os casos que ficam em `TierUpgradeGameTests` saem daqui, para não duplicar). As asserções "receita do ATM carregou sem o mod" (~:108–112), "Elite sem o Avançado" (vira: anel do Elite com um diamante no centro não casa) e "não há núcleo Básico" ficam;
  - `AtmGameTests.allthemodiumRecipes` (~:85–124): os anéis do ATM com a Redstone no centro dão o cartão com a origem certa (Allthemodium ← Esmeralda, Vibranium ← Allthemodium, Unobtainium ← Vibranium, Ultimate ← Unobtainium pelo `tier_core_ultimate_atm_star`); acrescente o cartão que pula (Esmeralda → Allthemodium + anel do Vibranium = Esmeralda → Vibranium) e a receita direta (roteador Esmeralda + anel do Allthemodium = Allthemodium; roteador Esmeralda + anel do Ultimate não casa). As asserções "outra versão do Ultimate carregou" e "Ultimate vanilla com o ATM" (agora: anel `XDX`/`B B`/`XBX` com roteador ou Redstone no centro não casa) ficam;
  - `AtmGameTests.fullLadderWithAllthemodium` (~:47–80): continua (atalho de cartão antigo); acrescente que um cartão com origem Esmeralda → Ultimate (montado à mão) não sobe um roteador Allthemodium;
  - `RecipeEditorGameTests.keepsConditions` (~:228–249): passa a usar `recipe/storage_chemical_tank.json` (shaped com `neoforge:mod_loaded mekanism`; o teste só lê o recurso do jar, não precisa do Mekanism carregado) e a conferir `mekanism` no lugar de `allthemodium`. Procure outros testes do editor que contem com um `tier_core_*` na lista (`grep -n tier_core gametest/RecipeEditorGameTests.java`).
- [ ] Verificação completa (sem e2e). Confira no log do `runGameTestServer` que nenhuma receita `tier_core_*` foi recusada pelo codec (procure `wirelessautomate:tier_core` e "Couldn't parse"/"Parsing error"). Commit "Subir tier: receita tier_upgrade com um anel por tier".

### Tarefa 4: JEI e e2e

**Objetivo:** o JEI mostra o que a decisão 8 pede, e o e2e confere tooltip e JEI.

**Arquivos:** `compat/jei/WirelessAutomateJeiPlugin.java`, `client/DevEndToEnd.java`.

- [ ] `registerItemSubtypes`: um `ISubtypeInterpreter<ItemStack>` para cada `TierCoreItem` (`ModItems.TIER_CORES.values()`), com `getSubtypeData` = `TierCoreItem.origin(stack)` (ou um marcador `"legacy"` sem origem) e `getLegacyStringSubtypeInfo` = o nome serializado (ou `""`). Sem isso o JEI junta "Básico → Elite" e "Avançado → Elite".
- [ ] `registerRecipes`, lendo os anéis das receitas carregadas no cliente (`Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)`, filtrando `instanceof TierUpgradeRecipe`; o JEI registra as receitas ao entrar no mundo, então o nível existe; confira e, se for `null`, registre nada e avise no log). Exponha no `TierUpgradeRecipe` o que o plugin precisa (`tier()`, `ring()`, `step()`), sem lógica de JEI lá. Para cada receita, com `from = tier.previous()`:
  - **receita direta**: uma `ShapedRecipe` de exibição 3×3 (o anel com o roteador de tier `from` no centro → roteador `tier`), id `jei/router_direct_<tier>`; o mesmo para cada armazenamento `kind.loaded()`, id `jei/<kind.id>_direct_<tier>`. Monte o padrão com `ShapedRecipePattern` 3×3 (`new ShapedRecipePattern(3, 3, ingredientes, Optional.empty())` ou `ShapedRecipePattern.of(...)`; confira qual é público na versão) e `Ingredient.of(RouterBlockItem.withTier(...))`, como o código de hoje faz no shapeless;
  - **cartão que pula, um exemplo por destino** (só se `from.previous() != null`, ou seja, do Elite em diante): o anel com o cartão Básico → `from` no centro → cartão Básico → `tier`, id `jei/tier_core_jump_<tier>`;
  - **o cartão de um degrau** aparece sozinho, pela própria receita (uma `ShapedRecipe`, ponto 6); se não aparecer, gere `jei/tier_core_step_<tier>`;
  - **bancada bloco + cartão** (a categoria de hoje): troque o laço de `canUpgradeTo` pelos pares (origem carregada `o`, destino `d` com cartão, `o.acceptsCard(o, d)`), com o cartão `TierCoreItem.card(d, o)` no lugar do cartão sem componente. Ids de hoje (`jei/router_upgrade_<o>_<d>`, `jei/<kind.id>_upgrade_<o>_<d>`).
  - Atualize o javadoc da classe e do método.
- [ ] e2e (`DevEndToEnd`):
  - vitrine (~:1848–1851 e ~:1893–1899): os cartões com origem (o de um degrau, `TierCoreItem.card(tier, tier.previous())`; no `summon`, o componente `"wirelessautomate:upgrade_from":"<origem>"` dentro de `components`);
  - passo "Baú: Cartão de Upgrade no bloco" (~:2185–2200): o cartão com origem Esmeralda (`TierCoreItem.card(chestUpgrade(), RouterTier.EMERALD)`), e o passo confere o tier novo como hoje; antes dele, um passo que tenta um cartão de origem errada (Básico → `chestUpgrade()`) e confere que o tier não mudou e o cartão ficou na mão;
  - captura nova `cartao-1-tooltip`: o inventário aberto com o mouse sobre um cartão Básico → Elite (siga o padrão da captura `bau-4-tooltip-mao`, ~:2231, e o `hover` dos outros passos);
  - capturas novas do JEI, se o e2e conseguir abrir a tela de receitas do JEI sem tipos do JEI fora de `compat/jei` (por exemplo, o mouse sobre o cartão no inventário e a tecla de receitas do JEI, `R` por padrão): `cartao-2-jei-um-degrau`, `cartao-3-jei-direta`, `cartao-4-jei-pula`. **Se não der**, pergunte ao dono antes de criar infraestrutura nova; o mínimo é um passo que confere, pelo log do plugin (uma linha `LOGGER.info` com o número de receitas de exibição registradas), que o JEI recebeu as receitas;
  - a volta em pt_br já existente mostra o nome e o tooltip em português; confira a captura.
- [ ] Verificação completa e e2e; olhe cada captura (o nome do cartão cabe no tooltip; as grades do JEI mostram o anel certo e o centro certo). Commit "Subir tier: JEI e capturas".

### Tarefa 5: guia, docs e verificação final

**Arquivos:** `scripts/guide/gerar_guia.py` (e as páginas geradas), `docs/especificacao.md`, `CLAUDE.md`, `docs/progresso.md`.

- [ ] Guia (só "como operar"; gere as páginas pelo script, nunca à mão), página `upgrade-cards.md` nos dois idiomas (~:624–720):
  - a introdução: subir um degrau custa o anel daquele tier; três jeitos (receita direta, cartão de um degrau, cartão que pula), com o mesmo custo;
  - o cartão tem origem ("Básico → Elite") e só vale num bloco daquele tier; o cartão antigo continua subindo de qualquer tier abaixo;
  - a tabela "Como usar": no mundo, Shift + clique (mantém toda a configuração); na bancada, bloco + cartão ou bloco no centro do anel (o que o item carrega: nome e, no armazenamento, o conteúdo; ver ponto 1);
  - "Receitas": os `<RecipeFor>` de hoje (ponto 5) e um parágrafo do Allthemodium com os anéis dele; tire "Como cada cartão leva o anterior na receita, ir direto ao tier final é o caminho mais barato";
  - procure no script outras menções ("qualquer tier abaixo", "any lower tier", "clique no roteador", "Use on the router", ~:475, ~:557, ~:530) e alinhe.
- [ ] Rode `python scripts/guide/gerar_guia.py` e confira com `git status` que só as páginas esperadas mudaram.
- [ ] `docs/especificacao.md`: a linha dos Cartões de Upgrade na tabela de componentes (~:22), a dos armazenamentos (~:203), "Receitas" dos armazenamentos (~:241), o parágrafo de "Receitas e progressão" (~:286) e a linha do upgrade na bancada (~:296, se existir): um anel por tier nas três receitas, origem do cartão, cartão antigo, com uma frase do motivo e o link para esta spec.
- [ ] `CLAUDE.md`:
  - a linha do `RouterTier.java, TierLadder.java`: `acceptsCard` (origem exata; antigo de qualquer tier abaixo) e `stepsTo`/`oneStep`;
  - a do `item/`: `TierCoreItem` com a origem (`upgrade_from`);
  - a do `recipe/`: `TierUpgradeRecipe` (`wirelessautomate:tier_upgrade`, um anel por tier, três receitas) e o `RouterUpgradeRecipe` com origem;
  - a do `recipe/edit/`: os cartões ficam fora do editor (tipo próprio);
  - a contagem de GameTests e a lista (`TierUpgradeGameTests`).
- [ ] `docs/progresso.md`: na tabela, a linha dos tiers e cartões (custo igual, origem, receita direta); o próximo passo (o dono testa no jogo, junto com o roteador de alcance e a rede por face); uma linha no histórico com os números de JUnit e GameTests das cinco runs.
- [ ] Verificação final completa: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` e `./scripts/e2e.sh` com `run/e2e/result.txt` = `OK`; olhe as capturas do guia (`guia-upgrade-cards*.png`, pt e en).
- [ ] Commit "Subir tier: guia e docs".

## Riscos principais

1. **Override antigo do editor ganha do JSON novo** (ponto 4): mesmo id, `crafting_shaped`, cartão sem origem e anel velho. O aviso no log ajuda, mas o jogador não vê pela tela.
2. **Codec do anel** (Tarefa 3): o `ShapedRecipePattern` encolhe bordas vazias; um anel de datapack com uma borda vazia é recusado (de propósito). O centro precisa ser exatamente o índice 4 da grade 3×3 que a bancada entrega; se o `CraftingInput` vier encolhido de outro jeito (por exemplo, por um mod de bancada), a receita não casa. Os GameTests montam a grade 3×3 inteira; confira à mão uma vez no `runClient`.
3. **JEI e GuideME com o tipo novo** (pontos 5 e 6): a receita estende `ShapedRecipe`, mas com serializer próprio; o desenho não está confirmado e há um plano B para cada um. O editor de receitas deixa de mostrar os cartões (`RecipeEditor.isCraftingSerializer` compara o serializer, não a classe), o que é o esperado.
4. **Bloco órfão no ATM** (ponto 3): sobe pela bancada, não pelo cartão no lugar.
5. **"Todos os dados" na bancada** (ponto 1): o item do roteador não carrega a configuração hoje; o guia tem de dizer isso sem prometer o que não acontece.
