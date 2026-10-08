# Tanque de Source Wireless: plano de implementação (etapa 2)

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** um quinto armazenamento, o **Tanque de Source Wireless**: guarda Source em `long`, tem tier, quebra e volta com o conteúdo, e as máquinas do Ars Nouveau (Sourcelinks, Enchanting Apparatus, Imbuement Chamber, rituais, Spell Turrets, Relays) o usam como se fosse uma Source Jar. Modelo fino de peças, como a jarra do Ars, com o nível de Source visível.

**Arquitetura:** o tanque é um `StorageKind` novo, no molde da Bateria: o conteúdo de "um valor só" (`ScalarStore`, o `EnergyStore` de hoje com outro nome) vive numa base comum (`ScalarStorageBlockEntity`) que a Bateria e o tanque estendem. O bloco é sempre registrado (como o Tanque Químico) e só fica visível e com receita com o Ars. A API do Ars fica em `compat/arsnouveau/`: a capability `ISourceCap` do bloco (para o roteador e os Relays) e o registro no `SourceManager` (para as máquinas). Entre dois tanques, o roteador passa tudo numa operação pela capability nossa `BulkSource` (`long`). A tela da Bateria vira a tela de "um valor só", usada pelos dois.

**Stack:** Java 21, NeoForge 21.1.251, ModDevGradle, JUnit 5, GameTests (run comum e `gameTestServerSource`), Python 3 + PIL (texturas e modelos).

**Spec:** [`docs/superpowers/specs/2026-10-08-source-ars-nouveau-design.md`](../specs/2026-10-08-source-ars-nouveau-design.md), seção "Etapa 2". Visual aprovado: [Tanque de Source Wireless](https://claude.ai/artifact/FanHHL2WabTurq35HM4Rcd) (versão 3). Protótipo das texturas e do modelo, já aprovado: [`2026-10-08-tanque-de-source-prototipo.py`](2026-10-08-tanque-de-source-prototipo.py) (render em [`…-prototipo.png`](2026-10-08-tanque-de-source-prototipo.png)).

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- **Tipos do Ars** só em `compat/arsnouveau/` e em `gametest/SourceTestSupport.java`; nunca na assinatura de método de classe `@EventBusSubscriber` ou `@GameTestHolder`. O resto passa por `network/Sources.java`. A run `runGameTestServer` (sem o Ars) prova que o mod carrega sem ele.
- `StorageKind.SOURCE_TANK`: id `storage_source_tank`, config `sourceTankCapacity`, recurso `ResourceType.SOURCE`, unidade `source`, capacidades `160_000L, 2_560_000L, 40_960_000L, 0L` (0 = sem limite). Entra **no fim** do enum.
- O bloco do tanque é **sempre registrado** (como o Tanque Químico); sem o Ars ele some da aba criativa e do JEI, a receita não carrega e o tooltip do item diz que precisa do Ars.
- Cores (ARGB): Source passa a `0xFFB36DE0` (o roxo do Ars) e Químicos a `0xFF97C853` (o verde do Tanque Químico). Na barra da tela do tanque: `0xFF9749C2` (corpo), `0xFF6B2F8F` (sombra), `0xFFEA8EF3` (brilho).
- Nível visível: propriedade de bloco `fill` de 0 a 10 (só no tanque). 0 = vazio; com Source, `ceil(10 × guardado / capacidade)` entre 1 e 10; sem limite, 10 com qualquer Source. O bloco só troca de estado quando o nível muda.
- Para o Ars (`ISourceCap`, `ISourceTile`), tudo em `int`: quantidade e capacidade mostradas no máximo `Integer.MAX_VALUE`. `ISourceTile.addSource(n)`/`removeSource(n)` (sem simulação) devolvem o "total novo" **relativo ao `getSource()` de antes** (`antes ± quanto passou`, limitado a `[0, Integer.MAX_VALUE]`), para o `SourceUtil.takeSourceMultiple` (que calcula `antes − depois`) acertar mesmo com mais de `Integer.MAX_VALUE` guardado; as versões com `simulate` devolvem quanto passou.
- Receita (com a condição `neoforge:mod_loaded` `ars_nouveau`): padrão `IGI`/`GTG`/`IGI`, `I` = `minecraft:iron_ingot`, `G` = `ars_nouveau:source_gem`, `T` = `wirelessautomate:storage_tank`, resultado 1 `wirelessautomate:storage_source_tank`, categoria `redstone`.
- Texturas e modelos do tanque saem do `scripts/textures/gerar_texturas.py` (nada de PNG ou JSON de modelo editado à mão). As texturas são desenhadas no script; do Ars, só cores (os PNGs dele são GPL e o mod é "todos os direitos reservados").
- Performance: nada de tick por bloco; o tanque não faz tick; o estado `fill` só muda quando o nível muda; a tela só sincroniza com ela aberta.
- `ModPayloads.VERSION` continua `"8"` (a 1.1.0 ainda não saiu; o payload da tela muda de nome nesta etapa, junto com o resto da 1.1.0).
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource` (Git Bash, na raiz). Nas tarefas de tela, também o e2e: `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (resultado em `run/e2e/result.txt` = `OK`; confira que o `guiScale` de `run/options.txt` não mudou). Nunca dois jogos ao mesmo tempo.
- Commits terminam com a linha `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

**Ajustes em relação à spec (decididos pelo dono no mockup):**
1. Modelo de peças fino (base, trilhos, coluna de vidro, tampa, pescoço e gema), com nível visível, em vez de um cubo com painel.
2. Cores: Source no roxo do Ars e Químicos no verde-limão (a spec dizia Source `#FF5CC8`).
3. O bloco é sempre registrado e escondido sem o Ars (a spec dizia "só registrado com o Ars"): registrar condicionalmente deixaria blocos inválidos num mundo que perde o mod, e o Tanque Químico já faz assim.
4. A spec previa só a Bateria "generalizada"; aqui o `EnergyStore` vira `ScalarStore` e a Bateria e o tanque dividem uma base (`ScalarStorageBlockEntity`), a tela, o menu e o payload.

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `client/ResourceStyle.java`, `scripts/textures/gerar_texturas.py` (ícones) | Cores novas de Source e Químicos |
| `storage/StorageKind.java` | `SOURCE_TANK`, `loaded()`, `hasTypes()` pelo recurso |
| `storage/ScalarStore.java` (renomear de `EnergyStore`), `EnergyStoreHandler.java` | Conteúdo de um valor só, em `long` |
| `storage/ScalarStorageBlockEntity.java` (novo) | Base da Bateria e do tanque (salvar, quebrar, comparador, `store()`) |
| `storage/StorageBatteryBlockEntity.java` | Passa a estender a base |
| `storage/StorageSourceTankBlockEntity.java`, `storage/StorageSourceTankBlock.java` (novos) | O tanque: nível `fill`, forma fina, sem oclusão |
| `storage/BulkSource.java` (novo), `storage/StorageCapabilities.java` | Capability nossa em `long` para o roteador |
| `storage/StorageMath.java` + JUnit | `fillLevel` |
| `storage/StorageBlock.java`, `StorageBlockItem.java`, `registry/*`, `menu/ListKind.java`, `menu/StorageListMenu.java`, `Config.java`, `item/TierCoreItem.java`, `compat/jei/WirelessAutomateJeiPlugin.java` | Registro, casos do `switch` e `kind.loaded()` no lugar de `Chemicals.LOADED` |
| `compat/arsnouveau/ArsStorage.java` (novo), `network/Sources.java`, `compat/arsnouveau/ArsSources.java`, `block/RouterBlockEntity.java` | `ISourceCap` do bloco, provider do `SourceManager`, lado bulk no roteador |
| `menu/StorageScalarMenu.java`, `packet/ScalarStatePayload.java`, `client/StorageScalarScreen.java` (renomear dos da Bateria) | Tela de um valor só |
| `gametest/StorageGameTests.java`, `gametest/SourceGameTests.java`, `gametest/SourceTestSupport.java` | Testes |
| `data/…`, `assets/…` | Receita, loot, tag de picareta, blockstate e modelos gerados, traduções |
| `scripts/guide/gerar_guia.py`, `client/DevEndToEnd.java`, `CLAUDE.md`, `docs/especificacao.md`, `docs/progresso.md` | Guia, e2e, documentação |

---

### Task 1: Cores novas de Source e Químicos

**Files:**
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/client/ResourceStyle.java`
- Modify: `scripts/textures/gerar_texturas.py` (`ICONES_TIPO["chemical"]` e `ICONES_TIPO["source"]`)
- Regenerate: `src/main/resources/assets/wirelessautomate/textures/gui/type/chemical.png`, `source.png`, `docs/preview/folha-de-sprites.png` (e a vitrine que o script grava)

**Interfaces:**
- Produces: `ResourceStyle.color(SOURCE) == 0xFFB36DE0`, `ResourceStyle.color(CHEMICAL) == 0xFF97C853`.

- [ ] **Step 1: `ResourceStyle`**

No `color`: `case CHEMICAL -> 0xFF97C853;` e `case SOURCE -> 0xFFB36DE0;`.

- [ ] **Step 2: Ícones**

No `gerar_texturas.py`, troque só as legendas (o desenho fica):
- `"chemical"`: `legenda(b="#97c853", l="#d9f2a6", d="#5f8c2e", e="#c8d2dc")`
- `"source"`: `legenda(b="#b36de0", l="#ea8ef3", d="#6b2f8f", e="#ffffff")`; e troque o comentário de cima por `# Source (Ars Nouveau): o roxo da Source do Ars.`

Rode `python scripts/textures/gerar_texturas.py` e confira com `git status` que só mudaram os dois ícones e as prévias (`docs/preview/…`).

- [ ] **Step 3: Documentos que citam as cores**

Procure `B45CFF`, `b45cff`, `FF5CC8`, `ff5cc8` em `CLAUDE.md`, `docs/` (fora de `docs/superpowers/`, que é histórico) e `src/` e troque pelas novas. Em `docs/superpowers/specs/2026-10-08-source-ars-nouveau-design.md`, acrescente no fim da seção "Decisões do dono" uma linha: "Cores (decidido no mockup do tanque): Source `#B36DE0` (roxo do Ars) e Químicos `#97C853`."

- [ ] **Step 4: Rodar e conferir**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`, depois `WA_E2E="$PWD/run/e2e" ./gradlew runClient`.
Expected: tudo passa. Abra `run/e2e/1d-roteador-aba-source.png` e a captura das Estatísticas do Tablet: Source em roxo, Químicos em verde, distinguíveis entre si e das outras cores.

- [ ] **Step 5: Commit**

```bash
git add -A src/main scripts/textures docs CLAUDE.md
git commit -m "Etapa 2: Source no roxo do Ars e Químicos no verde do Tanque Químico

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: O tanque como armazenamento (sem o Ars)

Tudo o que não depende do Ars: o tipo de armazenamento, o conteúdo, o bloco com o nível, registros, dados, traduções e os GameTests na run comum.

**Files:**
- Modify: `storage/StorageKind.java`, `storage/StorageMath.java`, `src/test/java/io/github/matheusanbs/wirelessautomate/storage/StorageMathTest.java`
- Rename: `storage/EnergyStore.java` → `storage/ScalarStore.java` (e as referências em `EnergyStoreHandler`, `StorageBatteryBlockEntity`, `DevEndToEnd`, testes)
- Create: `storage/BulkSource.java`, `storage/ScalarStorageBlockEntity.java`, `storage/StorageSourceTankBlockEntity.java`, `storage/StorageSourceTankBlock.java`
- Modify: `storage/StorageBatteryBlockEntity.java`, `storage/StorageBlock.java`, `storage/StorageBlockItem.java`, `storage/StorageCapabilities.java`, `registry/ModBlocks.java`, `registry/ModBlockEntities.java`, `registry/ModCreativeTabs.java`, `menu/ListKind.java`, `menu/StorageListMenu.java`, `Config.java`, `item/TierCoreItem.java`, `compat/jei/WirelessAutomateJeiPlugin.java`
- Create: `src/main/resources/data/wirelessautomate/loot_table/blocks/storage_source_tank.json`, `src/main/resources/data/wirelessautomate/recipe/storage_source_tank.json`
- Modify: `src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json`, `lang/en_us.json`, `lang/pt_br.json`
- Modify: `gametest/StorageGameTests.java`

**Interfaces:**
- Produces:
  - `StorageKind.SOURCE_TANK`; `boolean StorageKind.loaded()` (`LoadedTypes.contains(resource)`); `hasTypes()` = `resource.filtered()`.
  - `public final class ScalarStore implements BulkEnergy, BulkSource` (métodos do `EnergyStore` de hoje: `stored()`, `capacity()`, `version()`, `isEmpty()`, `insert`, `extract`, `set`) + `void replace(long value)` (troca o valor, limitado a `[0, capacidade]`, e avisa como uma mudança real).
  - `public interface BulkSource { BlockCapability<BulkSource, @Nullable Direction> BLOCK; long insert(long, boolean); long extract(long, boolean); }` com id `wirelessautomate:bulk_source`.
  - `public abstract class ScalarStorageBlockEntity extends StorageBlockEntity` com `ScalarStore store()` e o gancho `protected void contentsChanged()`.
  - `StorageSourceTankBlockEntity extends ScalarStorageBlockEntity`, com `void refreshFill()`.
  - `StorageSourceTankBlock extends StorageBlock` com `public static final IntegerProperty FILL` (0..10).
  - `ModBlockEntities.SOURCE_TANK` (`BlockEntityType<StorageSourceTankBlockEntity>`, id `storage_source_tank`).
  - `StorageMath.fillLevel(long stored, long capacity)` → `int` de 0 a 10.
  - A tela do tanque nesta tarefa ainda é a da Bateria (o `open` chama o mesmo menu); a Task 5 generaliza a tela.

- [ ] **Step 1: JUnit do nível (falha)**

Em `StorageMathTest`, acrescente:

```java
    @Test
    void fillLevelGoesFromEmptyToFull() {
        assertEquals(0, StorageMath.fillLevel(0, 160_000));
        assertEquals(1, StorageMath.fillLevel(1, 160_000));
        assertEquals(1, StorageMath.fillLevel(16_000, 160_000));
        assertEquals(2, StorageMath.fillLevel(16_001, 160_000));
        assertEquals(5, StorageMath.fillLevel(80_000, 160_000));
        assertEquals(10, StorageMath.fillLevel(160_000, 160_000));
        assertEquals(10, StorageMath.fillLevel(999_999, 160_000));
    }

    @Test
    void fillLevelWithoutLimitIsFullWithAnything() {
        assertEquals(0, StorageMath.fillLevel(0, 0));
        assertEquals(10, StorageMath.fillLevel(1, 0));
        assertEquals(10, StorageMath.fillLevel(Long.MAX_VALUE, 0));
    }
```

Run: `./gradlew test` → FAIL (compilação: `fillLevel` não existe).

- [ ] **Step 2: `fillLevel`**

Em `StorageMath`:

```java
    /**
     * Nível mostrado pelo Tanque de Source, de 0 (vazio) a 10 (cheio): com qualquer conteúdo, pelo
     * menos 1, arredondado para cima. Sem limite, 10 com qualquer coisa.
     */
    public static int fillLevel(long stored, long capacity) {
        if (stored <= 0) {
            return 0;
        }
        if (capacity <= 0 || stored >= capacity) {
            return 10;
        }
        return Math.max(1, (int) Math.ceil(10.0 * stored / capacity));
    }
```

Run: `./gradlew test` → PASS.

- [ ] **Step 3: `StorageKind`**

Depois de `CHEMICAL_TANK(...)`:

```java
    /** Source do Ars Nouveau. Só aparece (aba, JEI, receita) com o Ars; o bloco existe sempre. */
    SOURCE_TANK("storage_source_tank", "sourceTankCapacity", ResourceType.SOURCE, "source",
            new long[] {160_000L, 2_560_000L, 40_960_000L, 0L});
```

Troque `hasTypes()` por `return resource.filtered();` (javadoc: "Guarda vários tipos (lista na tela e filtro de entrada): o Baú e os Tanques de fluido e químico; a Bateria e o Tanque de Source não.") e acrescente:

```java
    /** O mod que o recurso exige está carregado (o Tanque Químico com o Mekanism, o de Source com o Ars). */
    public boolean loaded() {
        return LoadedTypes.contains(resource);
    }
```

Atualize o javadoc da classe ("Os armazenamentos do mod") e o da unidade (`items`, `mb`, `fe` ou `source`).

- [ ] **Step 4: `ScalarStore`, `BulkSource` e a base comum**

`git mv` de `EnergyStore.java` para `ScalarStore.java`; renomeie a classe, mude o javadoc para "Conteúdo de um valor só (a energia da Bateria, a Source do Tanque de Source), em `long`, até a capacidade do tier (lida a cada entrada; `<= 0` é sem limite)…" e faça-a `implements BulkEnergy, BulkSource`. Acrescente:

```java
    /** Troca o valor (limitado a zero e à capacidade) e avisa, como uma mudança real. Para o {@code setSource} do Ars. */
    public void replace(long value) {
        long limit = capacity.getAsLong() <= 0 ? Long.MAX_VALUE : capacity.getAsLong();
        long next = Math.max(0, Math.min(value, limit));
        if (next != stored) {
            stored = next;
            changed();
        }
    }
```

Troque `EnergyStore` por `ScalarStore` em `EnergyStoreHandler`, `StorageBatteryBlockEntity` e em qualquer outro lugar (`grep -rn EnergyStore src`).

`storage/BulkSource.java`:

```java
package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * Source em {@code long}, sem o teto de {@link Integer#MAX_VALUE} por chamada da capability do Ars. É a
 * que o roteador procura antes da do Ars: entre dois Tanques de Source, bilhões passam numa chamada.
 * Não usa tipos do Ars.
 */
public interface BulkSource {
    BlockCapability<BulkSource, @Nullable Direction> BLOCK =
            BlockCapability.createSided(WirelessAutomate.id("bulk_source"), BulkSource.class);

    /** Guarda até {@code amount}; devolve quanto coube. */
    long insert(long amount, boolean simulate);

    /** Tira até {@code amount}; devolve quanto saiu. */
    long extract(long amount, boolean simulate);
}
```

`storage/ScalarStorageBlockEntity.java`: mova para cá tudo o que hoje está em `StorageBatteryBlockEntity` e não é da energia (o campo `store`, `store()`, `isEmptyContents`, `total`, `types`, `contentsVersion`, `saveContents` em `LongTag`, `loadContents`, `clearContents`). O `ScalarStore` é criado com `onChange = this::onStoreChanged`, que chama `setChanged()` e depois `contentsChanged()` (gancho vazio, `protected`). O construtor recebe `(BlockEntityType<?> type, StorageKind kind, BlockPos pos, BlockState state)`. `contentsKey()` e `open(...)` continuam abstratos.

`StorageBatteryBlockEntity` passa a estender a base e fica só com o `EnergyStoreHandler`, `contentsKey()` = `"energy"` (não muda: é a chave salva) e `open`.

- [ ] **Step 5: O tanque**

`storage/StorageSourceTankBlockEntity.java`:

```java
package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.menu.StorageBatteryMenu;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tanque de Source: Source em {@code long} ({@link ScalarStore}). Para o roteador, a {@link BulkSource};
 * para o Ars, a {@code ISourceCap} e o provider do {@code SourceManager} ({@code compat/arsnouveau}).
 * O nível da coluna ({@link StorageSourceTankBlock#FILL}) acompanha o conteúdo, e o estado do bloco só
 * muda quando o nível muda.
 */
public class StorageSourceTankBlockEntity extends ScalarStorageBlockEntity {
    public StorageSourceTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOURCE_TANK.get(), StorageKind.SOURCE_TANK, pos, state);
    }

    @Override
    protected void contentsChanged() {
        refreshFill();
    }

    /** Põe no bloco o nível do conteúdo atual (no servidor; sem nada se já for esse). */
    public void refreshFill() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(StorageSourceTankBlock.FILL)) {
            return;
        }
        int fill = StorageMath.fillLevel(store().stored(), capacity());
        if (state.getValue(StorageSourceTankBlock.FILL) != fill) {
            level.setBlock(worldPosition, state.setValue(StorageSourceTankBlock.FILL, fill), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected String contentsKey() {
        return "source";
    }

    @Override
    public void open(ServerPlayer player) {
        StorageBatteryMenu.open(player, this);
    }
}
```

(Se `StorageBatteryMenu.open` hoje recebe `StorageBatteryBlockEntity`, mude o parâmetro para `ScalarStorageBlockEntity`; a Task 5 renomeia o menu. O título vem do nome do bloco, então já sai certo.)

Chame `refreshFill()` também depois de carregar um item cheio (`applyImplicitComponents` do tanque: sobrescreva, chame o `super` e depois `refreshFill()`) e depois de um upgrade de tier (em `StorageBlock.tryUpgrade`, depois do `setBlockAndUpdate`: `if (level.getBlockEntity(pos) instanceof StorageSourceTankBlockEntity tank) tank.refreshFill();`).

`storage/StorageSourceTankBlock.java`: estende `StorageBlock`, acrescenta `public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 10);` ao `createBlockStateDefinition` (com o `TIER` do pai), estado padrão com `FILL` 0, `codec()` próprio (o mesmo formato do `StorageBlock.CODEC`, construindo `StorageSourceTankBlock`), e a forma fina, igual para colisão e contorno:

```java
    /** Base 10 × 2, corpo e tampa 8 × 12 e 10 × 1, pescoço e gema (o modelo do gerar_texturas.py). */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(4, 2, 4, 12, 13, 12),
            Block.box(3, 13, 3, 13, 14, 13),
            Block.box(5, 14, 5, 11, 15, 11),
            Block.box(7, 15, 7, 9, 18, 9));
```

(sobrescreva `getShape` devolvendo `SHAPE`). Em `ModBlocks`, registre o `SOURCE_TANK` com `new StorageSourceTankBlock(kind, props)` e as propriedades de sempre mais `.noOcclusion()`; os outros continuam `StorageBlock`. Em `StorageBlock.newBlockEntity`, `case SOURCE_TANK -> new StorageSourceTankBlockEntity(pos, state);`.

`ModBlockEntities`:

```java
    public static final Supplier<BlockEntityType<StorageSourceTankBlockEntity>> SOURCE_TANK =
            BLOCK_ENTITY_TYPES.register("storage_source_tank", () -> BlockEntityType.Builder.of(
                    StorageSourceTankBlockEntity::new, ModBlocks.STORAGE.get(StorageKind.SOURCE_TANK).get()).build(null));
```

`StorageCapabilities`: `event.registerBlockEntity(BulkSource.BLOCK, ModBlockEntities.SOURCE_TANK.get(), (tank, side) -> tank.store());` (a `ISourceCap` entra na Task 4). Atualize o javadoc.

- [ ] **Step 6: Casos do `switch` e `kind.loaded()`**

- `Config`: `case SOURCE_TANK -> "Capacidade do Tanque de Source por tier, em Source (0 = sem limite).";`
- `ListKind.of`: `case BATTERY, SOURCE_TANK -> throw new IllegalArgumentException(kind + " não tem lista");` (ajuste a mensagem do caso da Bateria).
- `StorageListMenu` (linha do `case BATTERY -> false;`): `case BATTERY, SOURCE_TANK -> false;`.
- Troque cada `kind == StorageKind.CHEMICAL_TANK && !Chemicals.LOADED` / `kind != StorageKind.CHEMICAL_TANK || Chemicals.LOADED` por `!kind.loaded()` / `kind.loaded()` em `ModCreativeTabs`, `TierCoreItem`, `WirelessAutomateJeiPlugin`, `StorageBlockItem` e `DevEndToEnd` (atualize o comentário da aba criativa: "Os armazenamentos de outros mods (Químico, Source) só aparecem com eles.").
- `StorageBlockItem`: a linha vermelha passa a `Component.translatable("block.wirelessautomate." + kind.id + ".needs_mod")` quando `!kind.loaded()`; renomeie a chave `block.wirelessautomate.storage_chemical_tank.needs_mekanism` para `block.wirelessautomate.storage_chemical_tank.needs_mod` nos dois idiomas (procure outros usos dela).
- `StorageBlock.summary`: o caso sem tipos (`summary.energy`) já serve ao tanque.

- [ ] **Step 7: Dados e traduções**

- `loot_table/blocks/storage_source_tank.json`: cópia do `storage_chemical_tank.json` trocando o id (o `copy_state` leva só `tier`; o nível volta pelo `refreshFill` ao colocar).
- `recipe/storage_source_tank.json`: exatamente a receita das Restrições globais (formato do `storage_chemical_tank.json`).
- `tags/block/mineable/pickaxe.json`: acrescente `"wirelessautomate:storage_source_tank"`.

Traduções (junto das irmãs):

| Chave | en_us | pt_br |
| --- | --- | --- |
| `block.wirelessautomate.storage_source_tank` | `Wireless Source Tank` | `Tanque de Source Wireless` |
| `block.wirelessautomate.storage_source_tank.needs_mod` | `Requires Ars Nouveau` | `Requer o Ars Nouveau` |
| `block.wirelessautomate.storage_chemical_tank.needs_mod` (renomeada) | `Requires Mekanism` | `Requer o Mekanism` (o texto pt atual) |
| `item.wirelessautomate.tier_core.storage_source_tank` | `  Source Tank (Source): %s → %s` | `  Tanque de Source (Source): %s → %s` |
| `gui.wirelessautomate.unit.source` | `%s Source` | `%s Source` |

- [ ] **Step 8: GameTests na run comum (sem o Ars)**

Em `StorageGameTests`, um helper `storageSourceTank(helper, pos, tier)` (como o `storageBattery`) e:

```java
    /** Tanque de Source: guarda em long até a capacidade, e o nível do bloco acompanha o conteúdo. */
    @GameTest(template = "empty")
    public static void sourceTankStoresAndShowsLevel(GameTestHelper helper) {
        StorageSourceTankBlockEntity tank = storageSourceTank(helper, A, RouterTier.BASIC);
        long capacity = Config.storageCapacity(StorageKind.SOURCE_TANK, RouterTier.BASIC);
        helper.assertValueEqual(capacity, 160_000L, "capacidade do Básico");
        helper.assertValueEqual(tank.store().insert(80_000, false), 80_000L, "metade");
        helper.assertValueEqual(helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 5, "nível na metade");
        helper.assertValueEqual(tank.store().insert(999_999, false), 80_000L, "até a capacidade");
        helper.assertValueEqual(helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 10, "cheio");
        helper.assertValueEqual(tank.signal(), 15, "comparador cheio");
        tank.store().extract(160_000, false);
        helper.assertValueEqual(helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 0, "vazio");
        helper.succeed();
    }

    /** Upgrade de tier com Source dentro: o conteúdo fica e o nível é recalculado pela capacidade nova. */
    @GameTest(template = "empty")
    public static void sourceTankUpgradeKeepsSourceAndRefreshesLevel(GameTestHelper helper) {
        StorageSourceTankBlockEntity tank = storageSourceTank(helper, A, RouterTier.BASIC);
        tank.store().insert(160_000, false);
        helper.assertTrue(StorageBlock.tryUpgrade(helper.getLevel(), helper.absolutePos(A), RouterTier.ADVANCED), "upgrade");
        StorageSourceTankBlockEntity upgraded = helper.getBlockEntity(A);
        helper.assertValueEqual(upgraded.store().stored(), 160_000L, "Source depois do upgrade");
        helper.assertValueEqual(helper.getBlockState(A).getValue(StorageSourceTankBlock.FILL), 1, "nível com a capacidade nova");
        helper.succeed();
    }
```

E no teste `tankAndBatteryKeepContentsWhenBroken` (ou num novo no mesmo molde, se o atual ficar confuso), um Tanque de Source Ultimate (sem limite) com `3_000_000_001L` quebrado pelo jogador falso, apanhado com `pickUp(helper, C, StorageKind.SOURCE_TANK, 3_000_000_001L)` e recolocado com `place(...)`: a Source volta igual, o tier no item é Ultimate e o nível do bloco recolocado é 10. Use uma posição livre do template (por exemplo `C = new BlockPos(0, 1, 2)` se não houver outra constante).

- [ ] **Step 9: Rodar**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`
Expected: tudo passa; a run comum com 155 GameTests (152 + 3). O log do cliente pode reclamar de modelo faltando para `storage_source_tank` (os modelos chegam na Task 3); nada mais.

- [ ] **Step 10: Commit**

```bash
git add -A src/main src/test
git commit -m "Etapa 2: Tanque de Source como armazenamento (conteúdo, nível, registros e dados)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Texturas e modelos do tanque pelo gerador

**Files:**
- Modify: `scripts/textures/gerar_texturas.py`
- Generate: `src/main/resources/assets/wirelessautomate/textures/block/storage_source_tank_*.png`, `models/block/storage_source_tank_<tier>_<fill>.json` (44), `models/item/storage_source_tank.json`, `blockstates/storage_source_tank.json`, `docs/preview/folha-de-sprites.png`
- Reference: `docs/superpowers/plans/2026-10-08-tanque-de-source-prototipo.py` e `…-prototipo.png` (aprovados)

**Interfaces:**
- Consumes: o id `storage_source_tank` e a propriedade `fill` (0..10) da Task 2; as paletas `casco` e `roteador_<tier>` do script.

- [ ] **Step 1: Texturas no gerador**

Leve para o `gerar_texturas.py` as funções do protótipo, com os mesmos desenhos e cores (`SRC`, `GEM`, `VIDRO`, `metal_lado`, `metal_topo`, `trilho`, `trilho_leste`, `vidro`, `liquido`, `superficie`, `gema`, `pescoco`), adaptadas ao estilo do script (paletas novas em `PALETAS`: `"source_tanque"` com as cores de `SRC`, `"source_gema"` com as de `GEM` e `"vidro_tanque"` com as de `VIDRO`; comentário dizendo que só a cor da Source e da gema vem do Ars). Gere, no `gerar()`:

| Sprite | Função |
| --- | --- |
| `block/storage_source_tank_<tier>_base_side` | `metal_lado(tier)` |
| `block/storage_source_tank_<tier>_base_top` | `metal_topo(tier)` |
| `block/storage_source_tank_<tier>_cap_side` | `metal_lado(tier, tampa=True)` |
| `block/storage_source_tank_<tier>_cap_top` | `metal_topo(tier, tampa=True)` |
| `block/storage_source_tank_<tier>_rail` | `trilho(tier)` |
| `block/storage_source_tank_<tier>_neck` | `pescoco(tier)` |
| `block/storage_source_tank_rail_side` | `trilho_leste()` |
| `block/storage_source_tank_glass` | `vidro()` |
| `block/storage_source_tank_source` | `liquido()` |
| `block/storage_source_tank_surface` | `superficie()` |
| `block/storage_source_tank_gem` | `gema()` |

Todos 16 × 16 com alfa só 0 ou 255 (o `gerar()` já confere).

- [ ] **Step 2: Modelos e blockstate pelo gerador**

Uma função nova no script, `modelos_tanque_source()`, chamada pelo `main()` (só quando não for `--so-folha`), grava:

- `models/block/storage_source_tank_<tier>_<fill>.json` para cada tier e `fill` de 0 a 10, com `"parent": "minecraft:block/block"`, `"render_type": "minecraft:cutout"`, `"ambientocclusion": false`, `"textures"` (inclusive `"particle"` = a base) e os elementos da tabela abaixo. Sem `uv` nas faces (o jogo calcula pela posição); `"cullface"` só nas faces de baixo da base (`"down"`).

| Elemento | from → to | Faces |
| --- | --- | --- |
| base | `[3,0,3]` → `[13,2,13]` | `up` base_top, laterais base_side, `down` base_top |
| trilhos (4) | `[4,2,4]`→`[5,13,5]`, `[11,2,4]`→`[12,13,5]`, `[4,2,11]`→`[5,13,12]`, `[11,2,11]`→`[12,13,12]` | `north`/`south` rail, `east`/`west` rail_side; sem `up`/`down` |
| Source (só com `fill` > 0) | `[5,2,5]` → `[11, 2 + max(1, round(fill × 11 / 10)), 11]` | `up` surface, laterais source |
| vidro | `[4,2,4]` → `[12,13,12]` | só as 4 laterais, glass |
| tampa | `[3,13,3]` → `[13,14,13]` | `up` cap_top, `down` base_top, laterais cap_side |
| pescoço | `[5,14,5]` → `[11,15,11]` | `up` cap_top, laterais neck |
| gema | `[7,15,7]` → `[9,18,9]` | todas gem |

- `blockstates/storage_source_tank.json`: uma variante por `fill=N,tier=T` (44) apontando para o modelo correspondente.
- `models/item/storage_source_tank.json`: `"parent"` = o modelo do Básico com `fill` 6, e os `overrides` por `wirelessautomate:tier` (1, 2, 3) para o Avançado, Elite e Ultimate com `fill` 6, no mesmo formato do `storage_chemical_tank.json`.

Os JSON saem com 2 espaços de indentação e `\n` no fim, para o `git diff` ficar limpo.

- [ ] **Step 3: Prévia na folha**

No `folha()`, acrescente uma linha "Tanque de Source" com os quatro tiers montados com `fill` 6 e uma com os níveis 0, 2, 5, 8 e 10 no Elite, usando o renderizador de caixas do protótipo (`render`, leve-o junto, com o nome `tanque_source_montado`). Rode `python scripts/textures/gerar_texturas.py`, abra `docs/preview/folha-de-sprites.png` e compare com `docs/superpowers/plans/2026-10-08-tanque-de-source-prototipo.png`: devem bater.

- [ ] **Step 4: Ver no jogo**

`./gradlew runClient`, num mundo criativo coloque um Tanque de Source de cada tier (pela aba criativa; o `runClient` tem o Ars), ponha Source num deles (por exemplo com um roteador e uma Source Jar criativa, ou o comando `/data` se for mais rápido) e confira: o modelo fino, o vidro transparente com a Source dentro, o nível subindo, o contorno de seleção na forma do modelo e o item na mão e no inventário com o tier certo. Tire uma captura (F2) e guarde em `run/screenshots/`; descreva no relatório.

- [ ] **Step 5: Rodar e commitar**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`
Expected: tudo passa.

```bash
git add -A scripts/textures src/main/resources/assets docs/preview
git commit -m "Etapa 2: texturas e modelo fino do Tanque de Source, com o nível visível

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: O tanque para o Ars Nouveau

**Files:**
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/compat/arsnouveau/ArsStorage.java`
- Modify: `compat/arsnouveau/ArsSources.java`, `network/Sources.java`, `block/RouterBlockEntity.java`, `storage/StorageCapabilities.java`, `storage/StorageSourceTankBlockEntity.java`
- Modify: `gametest/SourceGameTests.java`, `gametest/SourceTestSupport.java`

**Interfaces:**
- Consumes: `ScalarStore`, `BulkSource.BLOCK`, `StorageSourceTankBlockEntity`, `ModBlockEntities.SOURCE_TANK` (Task 2); `ArsSources.BLOCK`, `ArsSources.ACCESS`, `Sources.LOADED` (etapa 1).
- Produces: `Sources.registerStorage(RegisterCapabilitiesEvent)`, `Sources.registerProvider(StorageSourceTankBlockEntity)`; `RouterBlockEntity.bulkSource(Direction)` → `@Nullable BulkSource`.

- [ ] **Step 1: GameTests de Source com o tanque (falham)**

Em `SourceGameTests`, um helper `sourceTank(helper, pos, tier, network, mode)` que põe o tanque (`ModBlocks.STORAGE.get(StorageKind.SOURCE_TANK)` com o tier) e um roteador em cima, como o `jar(...)`; e um `tank(helper, pos)` que devolve o block entity. Testes (todos começam com `if (!enabled()) { helper.succeed(); return; }`):

1. `routerFillsTankFromJar` (Ultimate): Source Jar com 5.000 em `A` (Extrair) → tanque Básico em `B` (Inserir): espera o tanque com 5.000 e a jarra com 0.
2. `twoTanksMoveBillionsInOneVisit`: dois tanques Ultimate com roteadores Ultimate; o de origem com `3_000_000_000L` (posto antes de os roteadores registrarem); confere que os dois roteadores enxergam a `BulkSource` (`router.bulkSource(Direction.UP) != null`) e espera o destino com `3_000_000_000L` e a origem vazia (mais que `Integer.MAX_VALUE`: sem o caminho bulk, a capability do Ars passaria no máximo `Integer.MAX_VALUE` por visita).
3. `arsMachinesTakeFromTank`: tanque Básico com 5.000; depois que o provider existe (`thenWaitUntil(() -> SourceTestSupport.providerAt(level, pos))`), `SourceTestSupport.takeNearby(level, center, 5, 2_000)` devolve não nulo e o tanque fica com 3.000; outro pedido de 9.000 (sem outra fonte por perto) devolve nulo e o tanque continua com 3.000 (o Ars devolve o que tirou).
4. `arsTakesExactAmountFromHugeTank`: tanque Ultimate com `3_000_000_000L`; `takeNearby(…, 1_000)` devolve não nulo e o tanque fica com `2_999_999_000L` exatos (o caso do `int`).
5. `sourcelinksSeeTank`: tanque Básico vazio; `SourceTestSupport.canGiveNearby(level, center, 5)` tem um provider na posição do tanque; cheio (160.000), não tem.
6. `providerInvalidAfterBreak`: depois que o provider existe, `helper.setBlock(pos, Blocks.AIR)`; no tick seguinte nenhum provider válido na posição (`SourceTestSupport.providerAt` falso).
7. `tankHasSourceCapability`: `SourceTestSupport.hasSource(level, pos)` verdadeiro; `amount` e `capacity` em `int` (`capacity` do Ultimate = `Integer.MAX_VALUE`; com `3_000_000_000L`, `amount` = `Integer.MAX_VALUE`).
8. `sourceTankRecipeLoaded`: o gerenciador de receitas tem `wirelessautomate:storage_source_tank`.

`center` é uma posição a 2 blocos do tanque (dentro do raio 5). Em `SourceTestSupport` (tipos do Ars aqui), acrescente `providerAt`, `takeNearby` (chama `SourceUtil.takeSourceMultiple`), `canGiveNearby` (chama `SourceUtil.canGiveSource` e confere `getCurrentPos()`), devolvendo tipos do Minecraft/JDK (`boolean`, `BlockPos`, `List<BlockPos>`).

Run: `./gradlew runGameTestServerSource` → os testes novos falham (sem capability, sem provider).

- [ ] **Step 2: A `ISourceCap` e o provider (compat)**

`compat/arsnouveau/ArsStorage.java` (só a API do Ars aqui):
- `public static void register(RegisterCapabilitiesEvent event)`: `event.registerBlockEntity(ArsSources.BLOCK, ModBlockEntities.SOURCE_TANK.get(), (tank, side) -> new TankCap(tank));`
- `TankCap implements ISourceCap` sobre `tank.store()` e `tank.capacity()`: `getSource()` = o guardado limitado a `Integer.MAX_VALUE`; `getSourceCapacity()`/`getMaxSource()` = `Integer.MAX_VALUE` se a capacidade for 0 ou passar do `int`, senão a capacidade; `receiveSource(n, sim)`/`extractSource(n, sim)` = `(int) store.insert/extract(max(0, n), sim)`; `canAcceptSource(n)` = `receiveSource(n, true) > 0`; `canProvideSource(n)` = `extractSource(n, true) > 0`; `getMaxExtract()`/`getMaxReceive()` = `Integer.MAX_VALUE`; `setSource(n)` = `store.replace(max(0, n))`; `setMaxSource(n)` não faz nada (a capacidade é do tier).
- `public static void registerProvider(StorageSourceTankBlockEntity tank)`: só no servidor; `SourceManager.INSTANCE.addInterface(tank.getLevel(), new TankProvider(tank))`.
- `TankProvider implements ISpecialSourceProvider`: `getSource()` = um `TankTile` (um por provider, criado no construtor); `isValid()` = `!tank.isRemoved() && tank.getLevel() != null && tank.getLevel().getBlockEntity(tank.getBlockPos()) == tank`; `getCurrentPos()` = `tank.getBlockPos()`.
- `TankTile implements ISourceTile` com a semântica das Restrições globais:

```java
        @Override public int getTransferRate() { return Integer.MAX_VALUE; }
        @Override public boolean canAcceptSource() { return store().insert(1, true) > 0; }
        @Override public int getSource() { return clamp(store().stored()); }
        @Override public int getMaxSource() { return maxSource(tank); }
        @Override public int setSource(int source) { store().replace(Math.max(0, source)); return getSource(); }
        /** Total novo relativo ao getSource() de antes (o Ars calcula o que passou como antes − depois). */
        @Override public int addSource(int source) {
            int before = getSource();
            return clamp((long) before + store().insert(Math.max(0, source), false));
        }
        @Override public int removeSource(int source) {
            int before = getSource();
            return (int) Math.max(0, before - store().extract(Math.max(0, source), false));
        }
        @Override public int addSource(int source, boolean simulate) { return (int) store().insert(Math.max(0, source), simulate); }
        @Override public int removeSource(int source, boolean simulate) { return (int) store().extract(Math.max(0, source), simulate); }
```

com `clamp(long)` = `(int) Math.min(Math.max(0, v), Integer.MAX_VALUE)`.

Nada disso tem tipos do Ars na assinatura pública que o resto do mod vê: `register(RegisterCapabilitiesEvent)` e `registerProvider(StorageSourceTankBlockEntity)`.

- [ ] **Step 3: Ponte, registro e roteador**

- `Sources`: `public static void registerStorage(RegisterCapabilitiesEvent event) { if (LOADED) ArsStorage.register(event); }` e `public static void registerProvider(StorageSourceTankBlockEntity tank) { if (LOADED) ArsStorage.registerProvider(tank); }`.
- `StorageCapabilities.register`: depois do `Chemicals.registerStorage(event)`, `Sources.registerStorage(event);`.
- `StorageSourceTankBlockEntity.onLoad()`: `super.onLoad();` e, no servidor, `Sources.registerProvider(this);`.
- `RouterBlockEntity`: cache `bulkSourceCaches` e `public @Nullable BulkSource bulkSource(Direction machineFace)` (como o `bulkEnergy`, tipo `ResourceType.SOURCE`), com `Arrays.fill(bulkSourceCaches, null)` no `clearCaches()`.
- `ArsSources.ACCESS`: o handler tenta o bulk primeiro (`BulkSource bulk = node.bulkSource(face); return bulk != null ? bulk : node.arsSource(face);`) e cada método trata os dois (`handler instanceof BulkSource bulk ? bulk.extract(amount, simulate) : …`, `canExtract`/`canReceive` verdadeiros para o bulk), no molde do `EnergyAccess`. Atualize o javadoc.

- [ ] **Step 4: Rodar**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`
Expected: tudo passa; a run de Source com 14 testes (6 + 8).

Se `twoTanksMoveBillionsInOneVisit` falhar, confira primeiro se o roteador pegou a `BulkSource`; não afrouxe o teste.

- [ ] **Step 5: Commit**

```bash
git add -A src/main
git commit -m "Etapa 2: Tanque de Source para o Ars (capability, SourceManager) e caminho bulk no roteador

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Tela de um valor só (Bateria e Tanque de Source)

**Files:**
- Rename: `menu/StorageBatteryMenu.java` → `menu/StorageScalarMenu.java`, `packet/BatteryStatePayload.java` → `packet/ScalarStatePayload.java`, `client/StorageBatteryScreen.java` → `client/StorageScalarScreen.java`
- Modify: `registry/ModMenus.java`, `packet/ModPayloads.java`, `client/ClientSetup.java`, `storage/StorageBatteryBlockEntity.java`, `storage/StorageSourceTankBlockEntity.java`, `client/DevEndToEnd.java`, `client/DevScreenshot.java` (se usar a tela da Bateria), `lang/en_us.json`, `lang/pt_br.json`

**Interfaces:**
- Consumes: `ScalarStorageBlockEntity` (Task 2).
- Produces: `StorageScalarMenu.open(ServerPlayer, ScalarStorageBlockEntity)`; `StorageScalarMenu.kind()` → `StorageKind`; menu type `ModMenus.STORAGE_SCALAR` (id `storage_scalar`); payload `ScalarStatePayload` (id `scalar_state`, mesmos campos).

- [ ] **Step 1: Menu e payload**

Renomeie (com `git mv`) e generalize: o menu guarda um `ScalarStorageBlockEntity` (lê `store().stored()` e `capacity()`), manda o `StorageKind` no buffer de abertura (junto da posição) e expõe `kind()` no cliente. O resto (quando sincroniza, a variação por tick) não muda. `ModMenus.STORAGE_SCALAR` (id `storage_scalar`) no lugar do `STORAGE_BATTERY`.

- [ ] **Step 2: A tela**

`StorageScalarScreen` desenha pelo `kind`:

| | Bateria | Tanque de Source |
| --- | --- | --- |
| Chaves de texto | `gui.wirelessautomate.battery.*` (as de hoje) | `gui.wirelessautomate.source_tank.*` |
| Cor da barra (corpo, sombra, brilho) | as de hoje (`0xFFFFD34D`, `0xFFC8901C`, mistura com branco) | `0xFF9749C2`, `0xFF6B2F8F`, `0xFFEA8EF3` |
| Variação | por tick (`FE/t`) | por segundo (a variação por tick × 20) |
| Linha de dica | não tem | `gui.wirelessautomate.source_tank.hint`, com o ícone `ResourceStyle.drawIcon(g, SOURCE, …)`, quebrada por `GuiText.wrap` em até 2 linhas, abaixo de um filete |
| Altura | 104 | 104 + 26 |

Todo texto variável passa por `GuiText` (troque os `GuiPaint.ellipsize` + `GuiPaint.text` da tela da Bateria por `GuiText.draw`, com a largura disponível), para nenhum texto vazar e o e2e contar os cortados.

Traduções novas (o formato das da Bateria):

| Chave | en_us | pt_br |
| --- | --- | --- |
| `gui.wirelessautomate.source_tank.exact` | `%s of %s Source` | `%s de %s Source` |
| `gui.wirelessautomate.source_tank.exact.unlimited` | `%s Source (unlimited)` | `%s Source (sem limite)` |
| `gui.wirelessautomate.source_tank.amount` | `%s of %s Source` | `%s de %s Source` |
| `gui.wirelessautomate.source_tank.amount.unlimited` | `%s Source · unlimited` | `%s Source · sem limite` |
| `gui.wirelessautomate.source_tank.charging` | `Filling %s Source/s` | `Enchendo %s Source/s` |
| `gui.wirelessautomate.source_tank.draining` | `Draining %s Source/s` | `Esvaziando %s Source/s` |
| `gui.wirelessautomate.source_tank.idle` | `Idle` | `Parado` |
| `gui.wirelessautomate.source_tank.loading` | `Reading…` | `Lendo…` |
| `gui.wirelessautomate.source_tank.hint` | `Sourcelinks and Ars machines nearby use this tank like a Source Jar.` | `Sourcelinks e máquinas do Ars por perto usam este tanque como uma Source Jar.` |

`StorageBatteryBlockEntity.open` e `StorageSourceTankBlockEntity.open` chamam `StorageScalarMenu.open(player, this)`.

- [ ] **Step 3: e2e**

No `DevEndToEnd`, junto dos passos da Bateria (perto de `bateria-1-tela`): com `StorageKind.SOURCE_TANK.loaded()`, ponha um Tanque de Source Avançado com `1_640_000` de Source ao lado da Bateria, abra a tela (como a da Bateria), espere `received()`, `clipCheck("tanque de Source")` e `capture("tanque-source-1-tela")`; na volta em português, a mesma tela com `clipCheck` e `capture("tanque-source-1-tela-pt")`. Troque as referências à tela e ao menu da Bateria pelos nomes novos.

- [ ] **Step 4: Rodar**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`, depois `WA_E2E="$PWD/run/e2e" ./gradlew runClient`
Expected: tudo passa; `run/e2e/result.txt` = `OK`. Abra `bateria-1-tela.png` (igual a antes), `tanque-source-1-tela.png` e `tanque-source-1-tela-pt.png` (barra roxa, quantidade, "Enchendo…/Parado", a dica em até duas linhas, nada vazando) e compare com a tela do mockup.

- [ ] **Step 5: Commit**

```bash
git add -A src/main
git commit -m "Etapa 2: tela de um valor só para a Bateria e o Tanque de Source

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Guia e documentação

**Files:**
- Modify: `scripts/guide/gerar_guia.py` (e as páginas geradas), `client/DevEndToEnd.java` (`GUIDE_PAGES`)
- Modify: `CLAUDE.md`, `docs/especificacao.md`, `docs/progresso.md`

- [ ] **Step 1: Página do tanque**

No `gerar_guia.py`, uma página `wireless-source-tank.md` no molde da `wireless-chemical-tank.md`, nos dois idiomas, `front('Tanque de Source Wireless', 'wirelessautomate:storage_source_tank', 17, item_ids=['wirelessautomate:storage_source_tank'])` (passe `troubleshooting.md` para 18 e `recipes.md` para 19):
- o que é (guarda Source em grande quantidade; só existe com o Ars Nouveau);
- "Para o Ars": Sourcelinks num raio de 5 blocos depositam nele, e as máquinas do Ars por perto (Enchanting Apparatus, Imbuement Chamber, rituais, Spell Turrets, Relays de depósito) tiram dele, como de uma Source Jar;
- "Com o roteador": aba Source, e entre dois tanques tudo passa de uma vez;
- a coluna mostra quanto ele tem;
- tabela de capacidade por tier (160.000, 2.560.000, 40.960.000, sem limite), quebrar guarda a Source e o tier, Cartões de Upgrade, comparador (no mesmo formato da página do Tanque Químico);
- a cena 3D (`SCENE_STORAGE_PAIR` com `BLOCK='storage_source_tank'`, se servir; senão uma cena com um tanque, um roteador em cima e uma linha até outro tanque) e `<RecipeFor id="wirelessautomate:storage_source_tank" />`.

Na página `source.md`, troque o exemplo para citar o tanque ("No lugar das Source Jars, um [Tanque de Source Wireless](wireless-source-tank.md) guarda muito mais e as máquinas do Ars tiram dele do mesmo jeito.") e, em `index.md` e `recipes.md`, acrescente o tanque onde estão os outros armazenamentos. Rode o script e confira o `git diff` das páginas.

Acrescente `"wireless-source-tank"` à `GUIDE_PAGES` do e2e, depois de `"source"`.

- [ ] **Step 2: Documentação**

- `CLAUDE.md`: na linha de `storage/`, os cinco armazenamentos, o `ScalarStore` (no lugar do `EnergyStore`), a base `ScalarStorageBlockEntity`, a `BulkSource` e o nível `fill` do tanque; na de `compat/arsnouveau/`, o `ArsStorage` (capability do tanque e o provider do `SourceManager`); na de `menu/`, `StorageScalarMenu` no lugar do `StorageBatteryMenu` (e o payload); na de `client/`, `StorageScalarScreen`; na dos GameTests, a contagem nova (comum e Source); no `scripts/textures`, que ele também gera os modelos e o blockstate do Tanque de Source.
- `docs/especificacao.md`: o Tanque de Source na seção dos armazenamentos (capacidades, Ars, nível, receita) e as cores novas dos tipos.
- `docs/progresso.md`: etapa 2 pronta; próximo passo "fechamento da 1.1.0: `mod_description`, README, changelog, CurseForge, jar e release `v1.1.0`"; linha no histórico (8/10/2026).

- [ ] **Step 3: Rodar**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`, depois `WA_E2E="$PWD/run/e2e" ./gradlew runClient`
Expected: tudo passa; `result.txt` = `OK`; `guia-wireless-source-tank.png` e `guia-wireless-source-tank-pt.png` legíveis, com a receita e a cena.

- [ ] **Step 4: Commit**

```bash
git add -A scripts/guide src/main/resources/assets/wirelessautomate/guides src/main/java/io/github/matheusanbs/wirelessautomate/client/DevEndToEnd.java CLAUDE.md docs/especificacao.md docs/progresso.md
git commit -m "Etapa 2: página do Tanque de Source no guia e documentação

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
