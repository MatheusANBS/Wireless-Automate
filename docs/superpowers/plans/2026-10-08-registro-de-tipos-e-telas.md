# Registro de tipos e telas escalonáveis: plano de implementação (etapa 0)

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** tipos de recurso descritos num lugar só (o `enum ResourceType`) e telas do roteador, Vinculador e Tablet que se ajustam ao número de tipos e ao tamanho da janela, sem mudar o comportamento do motor.

**Arquitetura:** o `ResourceType` ganha os dados de cada tipo (chave, filtro, cartões, vazão padrão, mod que habilita) e continua puro (sem classes do Minecraft), para os testes JUnit. `LoadedTypes` (lado do jogo) diz quais tipos existem nesta instância. No cliente, `ResourceStyle` concentra cor, ícone e textos de cada tipo; `TabLayout` e `CardGrid` (puros, com JUnit) decidem o layout; `GuiText` desenha texto que nunca vaza (abrevia e dá tooltip). Roteador e Tablet passam a ter tamanho variável, no mecanismo da `FilterScreen`.

**Stack:** Java 21, NeoForge 21.1.251 (Minecraft 1.21.1), ModDevGradle, JUnit 5, GameTests, Python 3 + PIL (texturas).

**Spec:** [`docs/superpowers/specs/2026-10-08-registro-de-tipos-e-telas-design.md`](../specs/2026-10-08-registro-de-tipos-e-telas-design.md). Proposta visual aprovada: https://claude.ai/artifact/XxvxW9Gejgxi9qj4LZ5ocF

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- `ResourceType`, `LinkerTabs`, `PasteTypes`, `TabLayout` e `CardGrid` não importam classes do Minecraft nem do NeoForge (rodam no JUnit).
- Chaves de NBT, de componentes e da config **não mudam**: `item`, `fluid`, `energy`, `chemical`; `itemsPerSecond`, `fluidPerSecond`, `energyPerTick`.
- Tipos do Mekanism só em `compat/mekanism`, `ChemicalTransfer` e `gametest/ChemicalTestSupport`; nunca na assinatura de método de classe `@EventBusSubscriber` ou `@GameTestHolder`.
- Classes de tela só em `client/`, nunca referenciadas por código comum.
- Performance: nada de tick por bloco, nada de busca de capability por tick, nada de sincronizar o cliente com a tela fechada; o snapshot do Tablet só é reenviado quando muda (registros com `List`, não arrays, para o `equals` funcionar).
- Protocolo de rede: `ModPayloads.VERSION` passa de `"7"` para `"8"` (uma vez, na Tarefa 5).
- Cores por tipo (ARGB): Itens `0xFFD9A35B`, Fluidos `0xFF3D8BFF`, Energia `0xFFFFB020`, Químicos `0xFFB45CFF`.
- Tamanho mínimo do roteador e do Tablet: 300 × 240 (o de hoje); máximo: a janela menos 8 px. A largura extra do roteador vai só para o visor 3D e a linha das abas.
- Nenhum texto variável desenhado sem `GuiText` nas telas tocadas; texto abreviado sempre tem tooltip com o texto inteiro.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals` (Git Bash, na raiz). Nas tarefas de tela, também o e2e: no Windows, `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (a janela abre e o roteiro roda sozinho; resultado em `run/e2e/result.txt`, que deve dizer `OK`). Não rode dois jogos ao mesmo tempo (memória).
- Commits terminam com a linha `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

**Ajuste em relação à spec:** a spec dizia que o despacho da transferência viraria "um campo por tipo". Isso faria o `ResourceType` carregar `ItemTransfer` (classes do Minecraft) e quebraria o JUnit. Fica um `switch` exaustivo, sem `default`, em `NetworkManager.visit`: um tipo novo não compila até ganhar o seu caso, que é a garantia que a spec queria.

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `network/ResourceType.java` (mudar) | Registro: chave, filtro, cartões, chave e unidade da vazão, mod exigido, vazão padrão por tier; `byKey`, `available(Predicate<String>)` |
| `network/LoadedTypes.java` (novo) | Tipos que existem nesta instância (`ModList`) |
| `linker/LinkerTabs.java`, `preset/PasteTypes.java` (mudar) | Recebem a lista de tipos disponíveis em vez de `boolean chemicals` |
| `Config.java`, `block/RouterTier.java`, `item/TierCoreItem.java`, `network/NetworkRoutes.java` (mudar) | Vazão por tier lida pelo registro |
| `block/RouterBlockEntity.java`, `network/RouterPreset.java`, `network/NodeIndex.java`, `item/LinkerItem.java`, `item/ConfiguratorItem.java`, `item/FilterCardItem.java`, `command/WaCommand.java`, `menu/TabletMenu.java` (mudar) | Usam `type.key()`, `cards()`, `LoadedTypes` |
| `network/NetworkStats.java`, `network/NetworkManager.java`, `menu/TabletSnapshot.java`, `menu/TabletMenu.java` (mudar) | Estatística por tipo e filtro por tipo na Lista |
| `client/ResourceStyle.java` (novo) | Cor, ícone, nome, vazão e acesso de cada tipo |
| `client/TabLayout.java`, `client/CardGrid.java` (novos, puros) | Escolha do modo das abas e colunas da grade |
| `client/GuiText.java` (novo) | Texto que cabe: abrevia, quebra linha e registra o tooltip |
| `client/RouterScreen.java`, `menu/RouterMenu.java` (mudar) | Abas adaptáveis, seletor de rede, redimensionar |
| `client/LinkerScreen.java`, `menu/LinkerSnapshot.java`, `linker/LinkerActions.java`, `packet/LinkerActionPayload.java` (mudar) | Chips de tipo, "Todos", tipos disponíveis vindos do servidor |
| `client/TabletScreen.java` (mudar) | Cartões por tipo, clique filtra a Lista, redimensionar |
| `client/DevEndToEnd.java`, `client/DevScreenshot.java` (mudar) | Roteiro do e2e e capturas |
| `scripts/textures/gerar_texturas.py` (mudar) | Ícones `gui/type/<chave>.png` |
| Testes novos/alterados | `src/test/.../network/ResourceTypeTest.java`, `linker/LinkerTabsTest.java`, `preset/PasteTypesTest.java`, `client/TabLayoutTest.java`, `client/CardGridTest.java`; GameTests em `RouterConfigGameTests` e `TabletGameTests` |

Caminho base do Java: `src/main/java/io/github/matheusanbs/wirelessautomate/` (abreviado como `<base>`). Testes: `src/test/java/io/github/matheusanbs/wirelessautomate/`.

---

### Tarefa 1: O registro (`ResourceType`) e `LoadedTypes`

**Arquivos:**
- Mudar: `<base>network/ResourceType.java`
- Criar: `<base>network/LoadedTypes.java`
- Teste: `src/test/java/io/github/matheusanbs/wirelessautomate/network/ResourceTypeTest.java`

**Interfaces:**
- Produz: `String ResourceType.key()`, `boolean filtered()`, `boolean cards()`, `String rateKey()`, `boolean ratePerTick()`, `@Nullable String requiredMod()`, `long defaultRate(int tier)` (tier = `RouterTier.ordinal()`), `static @Nullable ResourceType byKey(String)`, `static List<ResourceType> available(Predicate<String> modLoaded)`; `LoadedTypes.LIST` (`List<ResourceType>`, imutável) e `LoadedTypes.contains(ResourceType)`.

- [ ] **Passo 1: escrever o teste que falha**

```java
package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceTypeTest {
    @Test
    void keysAreTheSavedNamesAndUnique() {
        assertEquals("item", ResourceType.ITEM.key());
        assertEquals("fluid", ResourceType.FLUID.key());
        assertEquals("energy", ResourceType.ENERGY.key());
        assertEquals("chemical", ResourceType.CHEMICAL.key());
        Set<String> keys = new HashSet<>();
        for (ResourceType type : ResourceType.values()) {
            assertTrue(keys.add(type.key()), "chave repetida: " + type.key());
        }
    }

    @Test
    void byKeyFindsAndRejects() {
        assertSame(ResourceType.FLUID, ResourceType.byKey("fluid"));
        assertNull(ResourceType.byKey("FLUID"));
        assertNull(ResourceType.byKey("source"));
    }

    @Test
    void filterAndCards() {
        assertTrue(ResourceType.ITEM.filtered() && ResourceType.ITEM.cards());
        assertTrue(ResourceType.FLUID.filtered() && ResourceType.FLUID.cards());
        assertFalse(ResourceType.ENERGY.filtered() || ResourceType.ENERGY.cards());
        assertTrue(ResourceType.CHEMICAL.filtered());
        assertFalse(ResourceType.CHEMICAL.cards());
    }

    @Test
    void defaultRatesMatchTheTierTable() {
        assertEquals(512L, ResourceType.ITEM.defaultRate(0));
        assertEquals(131_072L, ResourceType.ITEM.defaultRate(2));
        assertEquals(512_000L, ResourceType.FLUID.defaultRate(1));
        assertEquals(4_000_000L, ResourceType.ENERGY.defaultRate(2));
        assertEquals(0L, ResourceType.ENERGY.defaultRate(3));
        assertEquals(ResourceType.FLUID.defaultRate(1), ResourceType.CHEMICAL.defaultRate(1));
    }

    @Test
    void rateKeysKeepTheConfigNames() {
        assertEquals("itemsPerSecond", ResourceType.ITEM.rateKey());
        assertEquals("fluidPerSecond", ResourceType.FLUID.rateKey());
        assertEquals("fluidPerSecond", ResourceType.CHEMICAL.rateKey());
        assertEquals("energyPerTick", ResourceType.ENERGY.rateKey());
        assertTrue(ResourceType.ENERGY.ratePerTick());
        assertFalse(ResourceType.ITEM.ratePerTick());
    }

    @Test
    void availableDependsOnTheMods() {
        assertEquals(List.of(ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY),
                ResourceType.available(mod -> false));
        assertEquals(List.of(ResourceType.values()), ResourceType.available(mod -> mod.equals("mekanism")));
    }
}
```

- [ ] **Passo 2: rodar e ver falhar**

Rode: `./gradlew test --tests "*ResourceTypeTest"`
Esperado: falha de compilação (`key()` não existe).

- [ ] **Passo 3: implementar o registro**

`<base>network/ResourceType.java`:

```java
package io.github.matheusanbs.wirelessautomate.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * Tipos de recurso que uma face transporta, e o registro do que cada um é. Cada um tem modo, filtro
 * e redstone próprios por face. A ordem das constantes é a ordem das abas, dos chips do Vinculador,
 * dos cartões do Tablet e da Shift + roda; o {@code ordinal} indexa arrays e payloads, então um tipo
 * novo entra sempre no fim.
 *
 * <p>Lógica pura (sem classes do Minecraft), testada por JUnit. Quais tipos existem nesta instância
 * fica em {@link LoadedTypes}; cor, ícone e textos, no cliente ({@code client.ResourceStyle}).
 */
public enum ResourceType {
    ITEM("item", true, true, "itemsPerSecond", false, null, 512L, 8_192L, 131_072L, 0L),
    FLUID("fluid", true, true, "fluidPerSecond", false, null, 32_000L, 512_000L, 8_000_000L, 0L),
    ENERGY("energy", false, false, "energyPerTick", true, null, 16_000L, 256_000L, 4_000_000L, 0L),
    /** Só existe com o Mekanism instalado. Divide a vazão com os fluidos e não tem cartões. */
    CHEMICAL("chemical", true, false, "fluidPerSecond", false, "mekanism", 32_000L, 512_000L, 8_000_000L, 0L);

    private final String key;
    private final boolean filtered;
    private final boolean cards;
    private final String rateKey;
    private final boolean ratePerTick;
    private final @Nullable String requiredMod;
    private final long[] defaultRate;

    ResourceType(String key, boolean filtered, boolean cards, String rateKey, boolean ratePerTick,
            @Nullable String requiredMod, long... defaultRate) {
        this.key = key;
        this.filtered = filtered;
        this.cards = cards;
        this.rateKey = rateKey;
        this.ratePerTick = ratePerTick;
        this.requiredMod = requiredMod;
        this.defaultRate = defaultRate;
    }

    /** Chave estável em NBT, componentes, comandos e traduções. */
    public String key() {
        return key;
    }

    /** Tem tipos dentro: filtro e estoque na face. */
    public boolean filtered() {
        return filtered;
    }

    /** Tem slots de Cartão de Filtro na face. */
    public boolean cards() {
        return cards;
    }

    /** Chave da vazão dentro de {@code tiers.<tier>} na config (dois tipos podem dividir a mesma). */
    public String rateKey() {
        return rateKey;
    }

    /** A vazão da config é por tick (energia); senão, por segundo. */
    public boolean ratePerTick() {
        return ratePerTick;
    }

    /** O mod sem o qual o tipo não existe, ou {@code null}. */
    public @Nullable String requiredMod() {
        return requiredMod;
    }

    /** Vazão padrão do tier ({@code RouterTier.ordinal()}); 0 = sem limite. */
    public long defaultRate(int tier) {
        return defaultRate[tier];
    }

    public static @Nullable ResourceType byKey(String key) {
        for (ResourceType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return null;
    }

    /** Os tipos cujo mod está carregado, na ordem do registro. */
    public static List<ResourceType> available(Predicate<String> modLoaded) {
        List<ResourceType> list = new ArrayList<>();
        for (ResourceType type : values()) {
            if (type.requiredMod == null || modLoaded.test(type.requiredMod)) {
                list.add(type);
            }
        }
        return List.copyOf(list);
    }
}
```

`<base>network/LoadedTypes.java`:

```java
package io.github.matheusanbs.wirelessautomate.network;

import java.util.List;
import net.neoforged.fml.ModList;

/** Os tipos de recurso que existem nesta instância (o mod de cada um está carregado), na ordem do registro. */
public final class LoadedTypes {
    public static final List<ResourceType> LIST = ResourceType.available(LoadedTypes::modLoaded);

    public static boolean contains(ResourceType type) {
        return LIST.contains(type);
    }

    private static boolean modLoaded(String mod) {
        return ModList.get() != null && ModList.get().isLoaded(mod);
    }

    private LoadedTypes() {
    }
}
```

- [ ] **Passo 4: rodar e ver passar**

Rode: `./gradlew test --tests "*ResourceTypeTest"`
Esperado: 6 testes PASS.

- [ ] **Passo 5: commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/network/ResourceType.java src/main/java/io/github/matheusanbs/wirelessautomate/network/LoadedTypes.java src/test/java/io/github/matheusanbs/wirelessautomate/network/ResourceTypeTest.java
git commit -m "Registro de tipos: dados de cada ResourceType e LoadedTypes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Tarefa 2: `LinkerTabs` e `PasteTypes` pela lista de tipos disponíveis

**Arquivos:**
- Mudar: `<base>linker/LinkerTabs.java`, `<base>preset/PasteTypes.java`
- Mudar (chamadas): `<base>item/LinkerItem.java` (L56-63, 103-105, 112-116, 119-142), `<base>linker/LinkerActions.java` (L167, 173, 256, 382-396), `<base>item/ConfiguratorItem.java` (L70-79, 105-109, 121-123), `<base>menu/LinkerSnapshot.java` (L36, 49-66, 89-91, 160, 178-179 e o decode), `<base>client/LinkerScreen.java` (L127-149, 260-262, 281-308), `<base>client/DevScreenshot.java` (L588-633, 673), `<base>client/DevEndToEnd.java` (L1850-1864), GameTests que chamam `effective(...)`, `isAll(...)`, `isEmpty(...)` ou `snapshot().chemicals()` (procure com `grep -rn "chemicals()\|effective(\|isAll(\|isEmpty(Chemicals" src/main/java`)
- Teste: `src/test/.../linker/LinkerTabsTest.java`, `src/test/.../preset/PasteTypesTest.java`

**Interfaces:**
- Consome: `ResourceType.key()`, `ResourceType.byKey`, `LoadedTypes.LIST` (Tarefa 1).
- Produz: `LinkerTabs.effective(List<ResourceType> available)`, `isAll(List<ResourceType>)`, `isEmpty(List<ResourceType>)`, `static LinkerTabs[] shortcuts(List<ResourceType>)`, `next(int direction, List<ResourceType>)`, `static LinkerTabs available(List<ResourceType>)` → `of(available)`; `PasteTypes.cycle(List<ResourceType>)` → `ResourceType[]` com `null` na frente; `PasteTypes.next(@Nullable ResourceType, int, List<ResourceType>)`; `LinkerSnapshot.available()` (`LinkerTabs`, máscara dos tipos que o servidor tem) no lugar de `chemicals()`. `LinkerTabs.key(type)` sai (use `type.key()`).

- [ ] **Passo 1: reescrever os testes**

`LinkerTabsTest.java` (substitui o arquivo inteiro):

```java
package io.github.matheusanbs.wirelessautomate.linker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkerTabsTest {
    private static final ResourceType ITEM = ResourceType.ITEM;
    private static final ResourceType FLUID = ResourceType.FLUID;
    private static final ResourceType ENERGY = ResourceType.ENERGY;
    private static final ResourceType CHEMICAL = ResourceType.CHEMICAL;
    private static final List<ResourceType> BASE = List.of(ITEM, FLUID, ENERGY);
    private static final List<ResourceType> WITH_CHEMICALS = List.of(ITEM, FLUID, ENERGY, CHEMICAL);

    @Test
    void toggleAddsAndRemoves() {
        LinkerTabs tabs = LinkerTabs.of(ITEM).toggle(FLUID);
        assertEquals(List.of(ITEM, FLUID), tabs.types());
        assertEquals(List.of(FLUID), tabs.toggle(ITEM).types());
    }

    @Test
    void unavailableTypesAreKeptButIgnored() {
        LinkerTabs tabs = LinkerTabs.of(ITEM, CHEMICAL);
        assertEquals(List.of(ITEM, CHEMICAL), tabs.types());
        assertEquals(List.of(ITEM), tabs.effective(BASE));
        assertEquals(List.of(ITEM, CHEMICAL), tabs.effective(WITH_CHEMICALS));
        assertTrue(LinkerTabs.of(CHEMICAL).isEmpty(BASE));
        assertFalse(LinkerTabs.of(CHEMICAL).isEmpty(WITH_CHEMICALS));
    }

    @Test
    void allDependsOnWhatIsAvailable() {
        assertTrue(LinkerTabs.ALL.isAll(BASE));
        assertTrue(LinkerTabs.ALL.isAll(WITH_CHEMICALS));
        LinkerTabs three = LinkerTabs.of(ITEM, FLUID, ENERGY);
        assertTrue(three.isAll(BASE));
        assertFalse(three.isAll(WITH_CHEMICALS));
        assertEquals(LinkerTabs.of(WITH_CHEMICALS), LinkerTabs.available(WITH_CHEMICALS));
    }

    @Test
    void wheelCyclesAllThenEachAvailableType() {
        assertEquals(LinkerTabs.of(ITEM), LinkerTabs.ALL.next(1, BASE));
        assertEquals(LinkerTabs.of(ENERGY), LinkerTabs.of(FLUID).next(1, BASE));
        assertSame(LinkerTabs.ALL, LinkerTabs.of(ENERGY).next(1, BASE));
        assertEquals(LinkerTabs.of(CHEMICAL), LinkerTabs.of(ENERGY).next(1, WITH_CHEMICALS));
        assertEquals(LinkerTabs.of(CHEMICAL), LinkerTabs.ALL.next(-1, WITH_CHEMICALS));
        assertSame(LinkerTabs.ALL, LinkerTabs.of(ITEM, FLUID).next(1, BASE));
    }

    @Test
    void namesRoundTripAndIgnoreUnknown() {
        LinkerTabs tabs = LinkerTabs.of(FLUID, CHEMICAL);
        assertEquals(List.of("fluid", "chemical"), tabs.names());
        assertEquals(tabs, LinkerTabs.fromNames(List.of("fluid", "chemical", "plasma")));
    }
}
```

`PasteTypesTest.java` (substitui o arquivo inteiro):

```java
package io.github.matheusanbs.wirelessautomate.preset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class PasteTypesTest {
    private static final List<ResourceType> BASE = List.of(ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY);
    private static final List<ResourceType> WITH_CHEMICALS = List.of(ResourceType.values());

    @Test
    void forwardSkipsUnavailable() {
        assertEquals(ResourceType.ITEM, PasteTypes.next(null, 1, BASE));
        assertEquals(ResourceType.FLUID, PasteTypes.next(ResourceType.ITEM, 1, BASE));
        assertEquals(ResourceType.ENERGY, PasteTypes.next(ResourceType.FLUID, 1, BASE));
        assertNull(PasteTypes.next(ResourceType.ENERGY, 1, BASE));
    }

    @Test
    void forwardWithChemicals() {
        assertEquals(ResourceType.CHEMICAL, PasteTypes.next(ResourceType.ENERGY, 1, WITH_CHEMICALS));
        assertNull(PasteTypes.next(ResourceType.CHEMICAL, 1, WITH_CHEMICALS));
    }

    @Test
    void backwardWrapsAround() {
        assertEquals(ResourceType.ENERGY, PasteTypes.next(null, -1, BASE));
        assertEquals(ResourceType.CHEMICAL, PasteTypes.next(null, -1, WITH_CHEMICALS));
        assertNull(PasteTypes.next(ResourceType.ITEM, -5, WITH_CHEMICALS));
    }

    @Test
    void unavailableCountsAsAll() {
        assertEquals(ResourceType.ITEM, PasteTypes.next(ResourceType.CHEMICAL, 1, BASE));
        assertEquals(ResourceType.ENERGY, PasteTypes.next(ResourceType.CHEMICAL, -1, BASE));
    }

    @Test
    void zeroDirectionStays() {
        assertEquals(ResourceType.FLUID, PasteTypes.next(ResourceType.FLUID, 0, WITH_CHEMICALS));
    }

    @Test
    void cycleOrder() {
        assertArrayEquals(new ResourceType[] {null, ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY},
                PasteTypes.cycle(BASE));
        assertEquals(5, PasteTypes.cycle(WITH_CHEMICALS).length);
    }
}
```

- [ ] **Passo 2: rodar e ver falhar**

Rode: `./gradlew test --tests "*LinkerTabsTest" --tests "*PasteTypesTest"`
Esperado: falha de compilação (assinaturas com `List<ResourceType>` não existem).

- [ ] **Passo 3: implementar `LinkerTabs` e `PasteTypes`**

Em `LinkerTabs.java`, troque os métodos que recebem `boolean chemicals` por estes (o resto do record fica), apague `key(ResourceType)` e use `type.key()` em `fromNames` e `names`; atualize o javadoc da classe para falar em "tipos disponíveis" em vez de Mekanism:

```java
    public static LinkerTabs fromNames(Collection<String> names) {
        int mask = 0;
        for (String name : names) {
            ResourceType type = ResourceType.byKey(name);
            if (type != null) {
                mask |= bit(type);
            }
        }
        return new LinkerTabs(mask);
    }

    /** Chaves salvas, na ordem das abas. */
    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (ResourceType type : types()) {
            names.add(type.key());
        }
        return names;
    }

    /** As abas que valem agora: as guardadas que existem nesta instância. */
    public List<ResourceType> effective(List<ResourceType> available) {
        List<ResourceType> list = types();
        list.retainAll(available);
        return list;
    }

    /** Todas as abas disponíveis estão marcadas (é "Todos"). */
    public boolean isAll(List<ResourceType> available) {
        return effective(available).size() == available.size();
    }

    /** Nenhuma aba que valha agora. */
    public boolean isEmpty(List<ResourceType> available) {
        return effective(available).isEmpty();
    }

    /** Todas as disponíveis marcadas. */
    public static LinkerTabs available(List<ResourceType> available) {
        return of(available);
    }

    /** Atalhos da roda, em ordem: Todos e depois cada aba disponível sozinha. Cópia: pode mexer. */
    public static LinkerTabs[] shortcuts(List<ResourceType> available) {
        LinkerTabs[] shortcuts = new LinkerTabs[available.size() + 1];
        shortcuts[0] = ALL;
        for (int i = 0; i < available.size(); i++) {
            shortcuts[i + 1] = of(available.get(i));
        }
        return shortcuts;
    }

    /**
     * Avança ({@code direction > 0}) ou volta ({@code direction < 0}) um atalho, dando a volta. O
     * atalho atual é achado pelas abas que valem agora; uma combinação que não é atalho vai para
     * Todos. Direção 0 fica.
     */
    public LinkerTabs next(int direction, List<ResourceType> available) {
        if (direction == 0) {
            return this;
        }
        LinkerTabs[] shortcuts = shortcuts(available);
        int index = -1;
        if (isAll(available)) {
            index = 0;
        } else {
            List<ResourceType> effective = effective(available);
            for (int i = 1; i < shortcuts.length; i++) {
                if (effective.equals(shortcuts[i].types())) {
                    index = i;
                    break;
                }
            }
        }
        if (index < 0) {
            return ALL;
        }
        return shortcuts[Math.floorMod(index + Integer.signum(direction), shortcuts.length)];
    }
```

`PasteTypes.java` (substitui o corpo da classe):

```java
/**
 * Seletor de tipo do Configurador (lógica pura): qual aba o colar aplica. {@code null} é Todos.
 * A ordem é Todos e depois os tipos disponíveis, na ordem do registro.
 */
public final class PasteTypes {
    private PasteTypes() {
    }

    /** Posições do seletor, em ordem ({@code null} = Todos). Cópia: pode mexer. */
    public static ResourceType[] cycle(List<ResourceType> available) {
        ResourceType[] cycle = new ResourceType[available.size() + 1];
        for (int i = 0; i < available.size(); i++) {
            cycle[i + 1] = available.get(i);
        }
        return cycle;
    }

    /**
     * Avança ({@code direction > 0}) ou volta ({@code direction < 0}) uma posição, dando a volta.
     * Um tipo que não está disponível conta como Todos. Direção 0 fica.
     */
    public static @Nullable ResourceType next(@Nullable ResourceType current, int direction,
            List<ResourceType> available) {
        ResourceType[] cycle = cycle(available);
        int index = 0;
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == current) {
                index = i;
                break;
            }
        }
        return cycle[Math.floorMod(index + Integer.signum(direction), cycle.length)];
    }
}
```

(Importe `java.util.List`.)

- [ ] **Passo 4: atualizar as chamadas**

Troque, em todo `src/main/java`:
- `Chemicals.LOADED` passado a `effective`, `isAll`, `isEmpty`, `next`, `shortcuts`, `PasteTypes.next` → `LoadedTypes.LIST`.
- `LinkerTabs.key(type)` e `type.name().toLowerCase(Locale.ROOT)` em `LinkerItem` e `ConfiguratorItem` (L121-123: apague o `key` privado) → `type.key()`.
- `LinkerActions.toggleTab` (L382-396): a checagem `type == ResourceType.CHEMICAL && !Chemicals.LOADED` vira `!LoadedTypes.contains(type)`.
- `LinkerSnapshot`: o componente `boolean chemicals` vira `LinkerTabs available` (javadoc: "os tipos que existem no servidor; a tela mostra um chip para cada"). Em `capture()` passe `LinkerTabs.available(LoadedTypes.LIST)`. No codec, `buf.writeBoolean(s.chemicals)` vira `buf.writeVarInt(s.available.mask())` e o decode lê `new LinkerTabs(buf.readVarInt())`. `effectiveTabs()` vira `tabs.effective(available.types())`.
- `LinkerScreen`: `snapshot().chemicals()` → `snapshot().available().types()`; a visibilidade da L260-262 vira `tabButtons.get(i).visible = s.available().contains(TABS[i]);`.
- `DevScreenshot` (L588-633, 673): onde passa `true`/`false` para `chemicals`, passe `LinkerTabs.available(LoadedTypes.LIST)`.
- `DevEndToEnd` L1853: `linkerScreen().getMenu().snapshot().chemicals()` → `linkerScreen().getMenu().snapshot().available().contains(type)`; e `type.name().toLowerCase(java.util.Locale.ROOT)` → `type.key()`.
- GameTests que passam `Chemicals.LOADED` ou `true/false` para esses métodos: passe `LoadedTypes.LIST` (ou a lista literal que o teste quer).

- [ ] **Passo 5: rodar tudo**

Rode: `./gradlew build runGameTestServer runGameTestServerChemicals`
Esperado: `BUILD SUCCESSFUL`, "All 149 required tests passed" e "All 7 required tests passed".

- [ ] **Passo 6: commit**

```bash
git add -A src/main/java src/test/java
git commit -m "Vinculador e Configurador pela lista de tipos disponíveis

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Tarefa 3: Vazão por tier, chaves e cartões pelo registro

**Arquivos:**
- Mudar: `<base>Config.java` (L29-31, 49-62), `<base>block/RouterTier.java`, `<base>item/TierCoreItem.java` (L61-94), `<base>network/NetworkRoutes.java` (L61-66, 121, 489-495), `<base>network/NetworkManager.java` (L117-119, 413-420), `<base>block/RouterBlockEntity.java` (L89-95, 317-330, 749-755, 765-801, 817-840, 869-871), `<base>network/RouterPreset.java` (L39-40), `<base>network/NodeIndex.java` (L172, 209, 235), `<base>item/FilterCardItem.java` (L44-73), `<base>command/WaCommand.java` (L47-54), `<base>menu/TabletMenu.java` (L93-94)
- Teste: `<base>gametest/RouterConfigGameTests.java` (GameTest novo)

**Interfaces:**
- Consome: Tarefa 1.
- Produz: `Config.TierValues(Map<String, ModConfigSpec.LongValue> rates, IntValue range, BooleanValue crossDimension)` com `long rate(ResourceType type)`; `RouterTier` perde `defaultItemsPerSecond`, `defaultFluidPerSecond`, `defaultEnergyPerTick`.

- [ ] **Passo 1: escrever o GameTest que protege o save**

Em `RouterConfigGameTests.java`, acrescente (use os helpers e constantes de posição que a classe já tem para colocar um baú com roteador; se a classe não tiver um helper de "baú + roteador", copie o `place` de `TransferGameTests`):

```java
    /** As chaves salvas por tipo continuam as de sempre, e um roteador salvo volta igual. */
    @GameTest(template = "empty")
    public static void savedKeysStayTheSame(GameTestHelper helper) {
        UUID network = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "teste-chaves").id();
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.CHEST);
        helper.setBlock(new BlockPos(0, 2, 0), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(new BlockPos(0, 2, 0));
        router.setNetworkId(network);
        router.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);

        CompoundTag tag = router.saveWithoutMetadata(helper.getLevel().registryAccess());
        Set<String> networks = tag.getCompound("networks").getAllKeys();
        helper.assertTrue(networks.equals(Set.of("item", "fluid", "energy", "chemical")), "chaves de rede: " + networks);
        helper.assertTrue(tag.getCompound("faces").contains("fluid"), "face de fluido salva fora da chave fluid");

        helper.setBlock(new BlockPos(2, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity copy = helper.getBlockEntity(new BlockPos(2, 2, 2));
        copy.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(network.equals(copy.networkId(ResourceType.ENERGY)), "rede de energia perdida");
        helper.assertTrue(copy.face(ResourceType.FLUID, Direction.UP).mode() == PortMode.INSERT, "modo de fluido perdido");
        helper.succeed();
    }
```

- [ ] **Passo 2: rodar e ver passar já (é proteção, não comportamento novo)**

Rode: `./gradlew runGameTestServer`
Esperado: 150 testes passando. Se este teste falhar antes da mudança, o helper ou os imports estão errados: corrija o teste antes de seguir.

- [ ] **Passo 3: config e tiers pelo registro**

Em `Config.java`, troque o record e o laço dos tiers:

```java
    /** Comentário de cada chave de vazão, na ordem em que aparecem no arquivo. */
    private static final Map<String, String> RATE_COMMENTS = Map.of(
            "itemsPerSecond", "Itens por segundo, por face e por tipo (0 = sem limite).",
            "fluidPerSecond", "Fluido e químico em mB por segundo (0 = sem limite).",
            "energyPerTick", "Energia em FE por tick (0 = sem limite).");

    public record TierValues(
            Map<String, ModConfigSpec.LongValue> rates,
            ModConfigSpec.IntValue range,
            ModConfigSpec.BooleanValue crossDimension) {
        /** Vazão do tipo na unidade da config ({@link ResourceType#ratePerTick()}); 0 = sem limite. */
        public long rate(ResourceType type) {
            return rates.get(type.rateKey()).get();
        }
    }
```

```java
        builder.push("tiers");
        for (RouterTier tier : RouterTier.values()) {
            builder.push(tier.getSerializedName());
            Map<String, ModConfigSpec.LongValue> rates = new LinkedHashMap<>();
            for (ResourceType type : ResourceType.values()) {
                if (!rates.containsKey(type.rateKey())) {
                    rates.put(type.rateKey(), builder.comment(RATE_COMMENTS.get(type.rateKey()))
                            .defineInRange(type.rateKey(), type.defaultRate(tier.ordinal()), 0L, Long.MAX_VALUE));
                }
            }
            TIERS.put(tier, new TierValues(Map.copyOf(rates),
                    builder.comment("Alcance em blocos (0 = a dimensão inteira).")
                            .defineInRange("range", tier.defaultRange, 0, Integer.MAX_VALUE),
                    builder.comment("Permite rotas entre dimensões.")
                            .define("crossDimension", tier.defaultCrossDimension)));
            builder.pop();
        }
        builder.pop();
```

(Importe `LinkedHashMap` e `ResourceType`.) Em `RouterTier`, apague os três campos de vazão e os parâmetros correspondentes do construtor: `BASIC("basic", 128, false)`, `ADVANCED("advanced", 1_024, false)`, `ELITE("elite", 0, false)`, `ULTIMATE("ultimate", 0, true)`; o javadoc diz que a vazão padrão fica em `ResourceType.defaultRate`.

Em `TierCoreItem`, o `enum Stat` passa a guardar o tipo e o valor sai do registro:

```java
    private enum Stat {
        ITEMS(ResourceType.ITEM), FLUID(ResourceType.FLUID), ENERGY(ResourceType.ENERGY);

        final ResourceType type;

        Stat(ResourceType type) {
            this.type = type;
        }
    }
```

e o `switch` das L90-94 vira `return loaded ? values.rate(stat.type) : stat.type.defaultRate(tier.ordinal());`.

Em `NetworkRoutes`:

```java
    static final ResourceType[] TRANSFER_TYPES = LoadedTypes.LIST.toArray(new ResourceType[0]);
```

```java
    /** Taxa do balde em unidades por segundo (0 = sem limite). */
    static long ratePerSecond(ResourceType type, Config.TierValues tier) {
        long value = tier.rate(type);
        return type.ratePerTick() ? RateLimiter.perSecondFromPerTick(value) : value;
    }
```

e apague o `bit(ResourceType)` da L121, trocando os usos por `NetworkManager.typeBit(type)`.

Em `NetworkManager.visit` (L415-420), mantenha o `switch` exaustivo e acrescente o comentário: `// Sem default: um tipo novo no ResourceType não compila até ganhar o seu caso aqui.`

- [ ] **Passo 4: chaves e cartões pelo registro**

Em `RouterBlockEntity`:

```java
    /** Tipos com slots de Cartão de Filtro, na ordem do registro. */
    private static final ResourceType[] CARD_TYPES = Arrays.stream(TYPES).filter(ResourceType::cards)
            .toArray(ResourceType[]::new);
    private static final int CARD_TYPES_MASK = cardTypesMask();

    private static int cardTypesMask() {
        int mask = 0;
        for (ResourceType type : CARD_TYPES) {
            mask |= NetworkManager.typeBit(type);
        }
        return mask;
    }

    private static int cardIndex(ResourceType type) {
        for (int i = 0; i < CARD_TYPES.length; i++) {
            if (CARD_TYPES[i] == type) {
                return i;
            }
        }
        return -1;
    }
```

`hasCardSlots(type)` retorna `type.cards()`. Apague `typeKey` (L869-871) e troque cada `typeKey(t)` e `type.name().toLowerCase(Locale.ROOT)` por `t.key()` (save e load das L749-840). Mesma troca em `RouterPreset` L39-40 (`keyCodec(TYPES, ResourceType::key)`), `NodeIndex` (L172, 209, 235), `LinkerItem` L56-63 (`ResourceType.byKey`).

`FilterCardItem` L59-65: o codec vira

```java
            Codec.STRING.comapFlatMap(key -> {
                ResourceType type = ResourceType.byKey(key);
                return type != null && type.cards() ? DataResult.success(type)
                        : DataResult.error(() -> "tipo de cartão inválido: " + key);
            }, ResourceType::key)
```

e a checagem do construtor do `Contents` (L44-53) vira `if (!type.cards()) throw new IllegalArgumentException(...)`.

`WaCommand` L47-54: o mapa `TYPES` passa a ser montado de `LoadedTypes.LIST` (`type.key() → type`). Com o Mekanism, `/wa face ... chemical ...` passa a funcionar; anote isso na linha do histórico.

`TabletMenu` L93-94: `TRANSFER_TYPES` vira `ResourceType.values()` (o tamanho dos arrays por tipo segue o registro; tipos sem mod ficam em zero).

- [ ] **Passo 5: rodar tudo**

Rode: `./gradlew build runGameTestServer runGameTestServerChemicals`
Esperado: `BUILD SUCCESSFUL`, 150 + 7 GameTests passando. Abra `run/config/wirelessautomate-server.toml` se existir de uma execução anterior e confira que as chaves `itemsPerSecond`, `fluidPerSecond` e `energyPerTick` continuam em cada tier.

- [ ] **Passo 6: commit**

```bash
git add -A src/main/java
git commit -m "Vazão, chaves salvas e cartões lidos do registro de tipos

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Tarefa 4: Ícones dos tipos e `ResourceStyle`

**Arquivos:**
- Mudar: `scripts/textures/gerar_texturas.py`
- Criar (gerados): `src/main/resources/assets/wirelessautomate/textures/gui/type/{item,fluid,energy,chemical}.png`
- Criar: `<base>client/ResourceStyle.java`
- Mudar: `<base>client/RouterScreen.java` (L241-243, 262-279), `<base>client/TabletScreen.java` (L209-216, 308-316, 1345-1355), `<base>client/LinkerScreen.java` (L113-116), `<base>client/FilterScreen.java` (L292)

**Interfaces:**
- Produz: `ResourceStyle.color(ResourceType) → int`, `ResourceStyle.name(ResourceType) → Component`, `ResourceStyle.rate(ResourceType, long) → Component`, `ResourceStyle.access(ResourceType, int slots) → Component`, `ResourceStyle.drawIcon(GuiGraphics, ResourceType, int x, int y)` (8 × 8).

- [ ] **Passo 1: ícones no script**

Em `gerar_texturas.py`, antes de `def gerar()`, acrescente:

```python
# Ícones dos tipos de recurso (8x8, dobrados para 16x16; a tela desenha em 8x8).
ICONES_TIPO = {
    "item": ([
        "........",
        ".OOOOOO.",
        "OLwwwwwO",
        "OwwOOwwO",
        "OWWOOWWO",
        "OwwwwwwO",
        "OWWWWWWO",
        ".OOOOOO.",
    ], legenda(O="#0b0f14", L="#f2cf8f", w="#d9a35b", W="#8a6236")),
    "fluid": ([
        "...O....",
        "..ObO...",
        "..ObO...",
        ".ObBbO..",
        "ObBbbbO.",
        "ObbbbdO.",
        ".OdddO..",
        "..OOO...",
    ], legenda(O="#0b0f14", b="#3d8bff", B="#9cc6ff", d="#1f5bb8")),
    "energy": ([
        "....OO..",
        "...OyO..",
        "..OyO...",
        ".OyyyyO.",
        "..OOyO..",
        "...OyO..",
        "..OyO...",
        "..OO....",
    ], legenda(O="#0b0f14", y="#ffb020")),
    "chemical": ([
        "..OOOO..",
        "...gg...",
        "...OO...",
        "..OCcO..",
        ".OCccCO.",
        ".OccccO.",
        ".OccccO.",
        "..OOOO..",
    ], legenda(O="#0b0f14", g="#c8d2dc", C="#e2c2ff", c="#b45cff")),
}


def icone_tipo(grade: list[str], leg: dict[str, tuple[int, int, int, int]]) -> Image.Image:
    """Ícone 8x8 dobrado para 16x16 (pixel a pixel), no padrão de 16x16 do script."""
    dobrada = [("".join(ch * 2 for ch in linha)) for linha in grade for _ in range(2)]
    return pinta(dobrada, leg)
```

e, dentro de `gerar()`, depois das portas:

```python
    for nome, (grade, leg) in ICONES_TIPO.items():
        sprites[f"gui/type/{nome}"] = icone_tipo(grade, leg)
```

Na `folha()`, acrescente uma linha `("Tipos de recurso", [(n, tile(sprites[f"gui/type/{n}"])) for n in ICONES_TIPO])` junto da linha "Portas da tela" (L884), no mesmo formato.

- [ ] **Passo 2: gerar**

Rode: `python scripts/textures/gerar_texturas.py`
Esperado: a mensagem com a contagem de sprites (4 a mais que antes) e os quatro PNGs em `textures/gui/type/`. Abra `docs/preview/folha-de-sprites.png` e confira a linha nova.

- [ ] **Passo 3: `ResourceStyle`**

```java
package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Como cada tipo de recurso aparece nas telas: cor, ícone, nome, vazão e acesso. O único lugar do
 * cliente com um {@code switch} por tipo; sem {@code default}, para um tipo novo não compilar sem estilo.
 */
public final class ResourceStyle {
    private ResourceStyle() {
    }

    public static int color(ResourceType type) {
        return switch (type) {
            case ITEM -> 0xFFD9A35B;
            case FLUID -> 0xFF3D8BFF;
            case ENERGY -> 0xFFFFB020;
            case CHEMICAL -> 0xFFB45CFF;
        };
    }

    public static Component name(ResourceType type) {
        return Component.translatable("gui.wirelessautomate.router.type." + type.key());
    }

    /** Vazão já na unidade da tela (itens/s, mB/s ou B/s, FE/t). */
    public static Component rate(ResourceType type, long value) {
        String abbreviated = RateFormat.abbreviate(value);
        return switch (type) {
            case ITEM -> Component.translatable("gui.wirelessautomate.router.rate.items", abbreviated);
            case FLUID, CHEMICAL -> value >= 1000
                    ? Component.translatable("gui.wirelessautomate.router.rate.buckets", RateFormat.abbreviate(value / 1000))
                    : Component.translatable("gui.wirelessautomate.router.rate.millibuckets", abbreviated);
            case ENERGY -> Component.translatable("gui.wirelessautomate.router.rate.energy", abbreviated);
        };
    }

    /** O que a face oferece (slots, tanques, bateria). */
    public static Component access(ResourceType type, int slots) {
        String prefix = "gui.wirelessautomate.router.access.";
        return switch (type) {
            case ITEM -> slots == 1 ? Component.translatable(prefix + "slot") : Component.translatable(prefix + "slots", slots);
            case FLUID, CHEMICAL -> slots == 1 ? Component.translatable(prefix + "tank")
                    : Component.translatable(prefix + "tanks", slots);
            case ENERGY -> Component.translatable(prefix + "energy");
        };
    }

    public static ResourceLocation icon(ResourceType type) {
        return ResourceLocation.fromNamespaceAndPath(WirelessAutomate.MODID, "textures/gui/type/" + type.key() + ".png");
    }

    /** Ícone 8 × 8 em ({@code x}, {@code y}). */
    public static void drawIcon(GuiGraphics g, ResourceType type, int x, int y) {
        g.blit(icon(type), x, y, 8, 8, 0, 0, 16, 16, 16, 16);
    }
}
```

- [ ] **Passo 4: telas usam o `ResourceStyle`**

- `RouterScreen`: `typeName(t)` passa a retornar `ResourceStyle.name(t)`; `access()` vira `ResourceStyle.access(type, view.slots())`; o corpo de `rate()` (L272-278) vira `ResourceStyle.rate(type, value)`.
- `TabletScreen`: `rate(ResourceType, long)` (L308-316) vira `ResourceStyle.rate(type, value)`; `typeName` (L213) delega a `ResourceStyle.name`. Nas `statColumn` (L1345-1355) troque as cores fixas por `ResourceStyle.color(type)` (corrige Itens e Fluidos com o mesmo azul). As chaves `gui.wirelessautomate.tablet.rate.*` ficam sem uso: confira com `grep -rn "tablet.rate" src/main/java` que nada mais as usa e apague-as dos dois `lang`.
- `LinkerScreen.typeName` e `FilterScreen.typeName`: para o tipo não nulo, `ResourceStyle.name(type)`.

- [ ] **Passo 5: rodar**

Rode: `./gradlew build runGameTestServer runGameTestServerChemicals`
Esperado: tudo passando. Depois, `WA_E2E="$PWD/run/e2e" ./gradlew runClient` e `run/e2e/result.txt` = `OK` (as telas mudaram só a cor da coluna de Itens no Tablet).

- [ ] **Passo 6: commit**

```bash
git add scripts/textures/gerar_texturas.py docs/preview src/main/resources/assets/wirelessautomate/textures/gui/type src/main/resources/assets/wirelessautomate/lang src/main/java
git commit -m "Ícones e estilo por tipo de recurso (ResourceStyle)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Tarefa 5: Estatística por tipo e filtro por tipo na Lista (servidor e protocolo)

**Arquivos:**
- Mudar: `<base>network/NetworkStats.java`, `<base>network/NetworkManager.java` (L261-298), `<base>menu/TabletSnapshot.java` (L78-106, 180-276), `<base>menu/TabletMenu.java` (L147, 387-432, 477-504, 556-568, 656-671, 682-690, 397-399), `<base>packet/ModPayloads.java` (L52), `<base>client/TabletScreen.java` (só o que precisa compilar: `sendQuery` e as somas das L1328-1343)
- Teste: `<base>gametest/TabletGameTests.java`

**Interfaces:**
- Produz: `NetworkStats.byType()` → `List<TypeCount>` com `record TypeCount(int sources, int destinations, int sleeping)` (índice = `ordinal`); `TabletSnapshot.TypeStats(long rate, int sources, int destinations, int sleeping)`; `NetworkView.types()` → `List<TypeStats>` (no lugar de `itemRate`…`chemicalRate`) e `NetworkView.type(ResourceType)`; `Query(String search, RoleFilter role, Optional<ResourceType> type, int page)` com `DEFAULT = new Query("", RoleFilter.ALL, Optional.empty(), 0)`.

- [ ] **Passo 1: escrever o GameTest que falha**

Em `TabletGameTests`, acrescente (os helpers `data`, `chestWithRouter`, `playerWithTablet`, `openTablet`, `fresh`, `listed`, `key` e as posições `A`, `B` já existem na classe):

```java
    @GameTest(template = "empty")
    public static void listFiltersByType(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper, A);
        UUID network = data(helper).create(player.getUUID(), "teste-filtro-tipo").id();
        RouterBlockEntity items = chestWithRouter(helper, A, network);
        items.configureFace(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0, RedstoneMode.IGNORE);
        RouterBlockEntity energy = chestWithRouter(helper, B, network);
        energy.configureFace(ResourceType.ENERGY, Direction.UP, PortMode.INSERT, 0, RedstoneMode.IGNORE);
        NodeIndex.placedBy(items, player.getUUID());
        NodeIndex.placedBy(energy, player.getUUID());
        TabletMenu menu = openTablet(player, 81);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(energy), "sem onLoad"))
                .thenExecute(() -> {
                    menu.setQuery(new Query("", RoleFilter.ALL, Optional.of(ResourceType.ENERGY), 0));
                    TabletSnapshot s = fresh(menu);
                    helper.assertTrue(listed(s, key(energy)), "nó de energia fora do filtro de energia");
                    helper.assertFalse(listed(s, key(items)), "nó só de itens no filtro de energia");
                    helper.assertTrue(s.query().type().equals(Optional.of(ResourceType.ENERGY)), "filtro perdido");
                    helper.assertTrue(s.network(network).map(n -> n.types().size() == ResourceType.values().length)
                            .orElse(false), "estatística sem um item por tipo");
                })
                .thenSucceed();
    }
```

Atualize as chamadas existentes de `new Query(texto, RoleFilter.ALL, 0)` nas L263 e L274 para `new Query(texto, RoleFilter.ALL, Optional.empty(), 0)`.

- [ ] **Passo 2: rodar e ver falhar**

Rode: `./gradlew runGameTestServer`
Esperado: falha de compilação (`Query` sem o tipo, `types()` não existe).

- [ ] **Passo 3: contagem por tipo no motor**

`NetworkStats` ganha o componente `List<TypeCount> byType` (último) e o record aninhado:

```java
    /** Origens, destinos e portas dormindo de um tipo (índice = {@link ResourceType#ordinal()}). */
    public record TypeCount(int sources, int destinations, int sleeping) {
    }
```

Em `NetworkManager.stats` (L261-298), dentro do laço `for (ResourceType type : TYPES)`, conte por tipo e monte a lista:

```java
            List<NetworkStats.TypeCount> byType = new ArrayList<>(TYPES.length);
            for (ResourceType type : TYPES) {
                List<Port> typeSources = network.sourcesOf[type.ordinal()];
                List<Port> typeDestinations = network.destinationsOf[type.ordinal()];
                int sleeping = 0;
                sourceCount += typeSources.size();
                destinationCount += typeDestinations.size();
                for (Port port : typeSources) {
                    if (port.sourceBackoff.isSleeping(now)) {
                        sourcesSleeping++;
                        sleeping++;
                    }
                }
                for (Port port : typeDestinations) {
                    if (port.destinationBackoff.isSleeping(now)) {
                        destinationsSleeping++;
                        sleeping++;
                        if (NodeProbe.isFull(port, now)) {
                            destinationsFull++;
                        }
                    }
                }
                byType.add(new NetworkStats.TypeCount(typeSources.size(), typeDestinations.size(), sleeping));
            }
```

e passe `List.copyOf(byType)` no construtor do `NetworkStats` (mantenha os nomes de variável que o método já usa; o trecho acima mostra a contagem que muda).

- [ ] **Passo 4: snapshot e consulta**

Em `TabletSnapshot`:

```java
    /** Vazão e portas de um tipo numa rede; vazão na unidade da tela (itens/s, mB/s, FE/t). */
    public record TypeStats(long rate, int sources, int destinations, int sleeping) {
        public static final TypeStats EMPTY = new TypeStats(0, 0, 0, 0);
    }
```

`NetworkView` troca `long itemRate, long fluidRate, long energyRate, long chemicalRate` por `List<TypeStats> types` (javadoc: "por `ResourceType.ordinal()`") e ganha:

```java
        public TypeStats type(ResourceType type) {
            return type.ordinal() < types.size() ? types.get(type.ordinal()) : TypeStats.EMPTY;
        }
```

No `encodeHeader`, no lugar das quatro `writeVarLong` das vazões:

```java
            buf.writeVarInt(n.types().size());
            for (TypeStats t : n.types()) {
                buf.writeVarLong(t.rate());
                buf.writeVarInt(t.sources());
                buf.writeVarInt(t.destinations());
                buf.writeVarInt(t.sleeping());
            }
```

e no `decodeHeader`, na mesma posição:

```java
            int typeCount = buf.readVarInt();
            List<TypeStats> types = new ArrayList<>(typeCount);
            for (int t = 0; t < typeCount; t++) {
                types.add(new TypeStats(buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
```

`Query` ganha `Optional<ResourceType> type` antes de `page`; no `STREAM_CODEC`, depois do `writeEnum(role)`: `buf.writeVarInt(q.type().map(t -> t.ordinal() + 1).orElse(0));` e na leitura `int t = buf.readVarInt(); Optional<ResourceType> type = t <= 0 || t > ResourceType.values().length ? Optional.empty() : Optional.of(ResourceType.values()[t - 1]);`.

- [ ] **Passo 5: menu do Tablet**

Em `TabletMenu`:
- `Totals.rates(long elapsed)` (L656-671) vira por tipo, pela unidade do registro:

```java
        long[] rates(long elapsed) {
            ResourceType[] types = ResourceType.values();
            long[] rates = new long[types.length];
            for (int t = 0; t < types.length; t++) {
                long perTick = types[t].ratePerTick() ? moved[t] : moved[t] * 20;
                rates[t] = (perTick + elapsed / 2) / elapsed;
            }
            return rates;
        }
```

- Na montagem do `NetworkView` (L417-432):

```java
            long[] rates = networkRates.getOrDefault(id, new long[ResourceType.values().length]);
            List<TabletSnapshot.TypeStats> types = new ArrayList<>(rates.length);
            for (ResourceType type : ResourceType.values()) {
                NetworkStats.TypeCount count = s == null ? null : s.byType().get(type.ordinal());
                types.add(count == null ? new TabletSnapshot.TypeStats(rates[type.ordinal()], 0, 0, 0)
                        : new TabletSnapshot.TypeStats(rates[type.ordinal()], count.sources(), count.destinations(),
                                count.sleeping()));
            }
```

e passe `List.copyOf(types)` no lugar das quatro vazões.
- Filtro por tipo: em `build`, onde `matchesRole(entry, ...)` decide se o nó entra, acrescente `&& matchesType(entry, query.type())`:

```java
    /** Sem tipo escolhido passa tudo; com tipo, só nós com algum papel (extrai, insere, armazém) nele. */
    private static boolean matchesType(NodeIndex.Entry entry, Optional<ResourceType> type) {
        if (type.isEmpty()) {
            return true;
        }
        int mask = NodeIndex.role(type.get(), NodeIndex.EXTRACT) | NodeIndex.role(type.get(), NodeIndex.INSERT)
                | NodeIndex.role(type.get(), NodeIndex.STORAGE);
        return (entry.roles() & mask) != 0;
    }
```

- L397-399 (página clampada): `new Query(search, role, type, pages - 1)` com o tipo da consulta atual.

`ModPayloads.VERSION = "8";`

No `TabletScreen`, só para compilar nesta tarefa: `sendQuery()` passa `Optional.empty()` (o filtro entra na tela na Tarefa 9); as somas das L1328-1343 usam `n.type(ResourceType.ITEM).rate()` etc.

- [ ] **Passo 6: rodar**

Rode: `./gradlew build runGameTestServer runGameTestServerChemicals`
Esperado: 151 + 7 GameTests passando, inclusive `listfiltersbytype`.

- [ ] **Passo 7: commit**

```bash
git add -A src/main/java
git commit -m "Tablet: estatística por tipo e filtro por tipo na Lista (protocolo 8)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Tarefa 6: Layout puro (`TabLayout`, `CardGrid`) e texto que cabe (`GuiText`)

**Arquivos:**
- Criar: `<base>client/TabLayout.java`, `<base>client/CardGrid.java`, `<base>client/GuiText.java`
- Teste: `src/test/.../client/TabLayoutTest.java`, `src/test/.../client/CardGridTest.java`

**Interfaces:**
- Produz: `TabLayout.choose(int[] fullWidths, int active, int available) → TabLayout.Result(Mode mode, int[] widths)`, `TabLayout.ICON_TAB = 22`, `TabLayout.GAP = 3`, `enum TabLayout.Mode { FULL, ACTIVE_NAME, ICONS }`; `CardGrid.columns(int count, int available, int minWidth, int gap) → int`; `GuiText.beginFrame()`, `GuiText.draw(GuiGraphics, Font, Component, int x, int y, int width, int color)`, `GuiText.wrap(GuiGraphics, Font, Component, int x, int y, int width, int maxLines, int color) → int` (altura usada), `GuiText.clipAt(double, double) → @Nullable Component`, `GuiText.clipCount() → int`.

- [ ] **Passo 1: testes que falham**

```java
package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TabLayoutTest {
    private static final int[] FULL = {50, 52, 50, 66, 48};

    @Test
    void everyNameWhenItFits() {
        TabLayout.Result r = TabLayout.choose(FULL, 0, 400);
        assertEquals(TabLayout.Mode.FULL, r.mode());
        assertArrayEquals(FULL, r.widths());
    }

    @Test
    void onlyTheActiveNameWhenTight() {
        TabLayout.Result r = TabLayout.choose(FULL, 3, 170);
        assertEquals(TabLayout.Mode.ACTIVE_NAME, r.mode());
        assertArrayEquals(new int[] {22, 22, 22, 66, 22}, r.widths());
    }

    @Test
    void iconsWhenEvenThatDoesNotFit() {
        TabLayout.Result r = TabLayout.choose(FULL, 3, 110);
        assertEquals(TabLayout.Mode.ICONS, r.mode());
        assertArrayEquals(new int[] {22, 22, 22, 22, 22}, r.widths());
    }

    @Test
    void exactFitCountsAsFitting() {
        int total = 50 + 52 + 50 + 66 + 48 + 4 * TabLayout.GAP;
        assertEquals(TabLayout.Mode.FULL, TabLayout.choose(FULL, 0, total).mode());
        assertEquals(TabLayout.Mode.ACTIVE_NAME, TabLayout.choose(FULL, 0, total - 1).mode());
    }
}
```

```java
package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CardGridTest {
    @Test
    void columnsFollowTheWidth() {
        assertEquals(3, CardGrid.columns(5, 282, 86, 4));
        assertEquals(5, CardGrid.columns(5, 452, 86, 4));
        assertEquals(4, CardGrid.columns(5, 445, 86, 4));
    }

    @Test
    void neverMoreColumnsThanCards() {
        assertEquals(4, CardGrid.columns(4, 1000, 86, 4));
    }

    @Test
    void atLeastOneColumn() {
        assertEquals(1, CardGrid.columns(5, 40, 86, 4));
        assertEquals(1, CardGrid.columns(0, 300, 86, 4));
    }
}
```

- [ ] **Passo 2: rodar e ver falhar**

Rode: `./gradlew test --tests "*TabLayoutTest" --tests "*CardGridTest"`
Esperado: falha de compilação.

- [ ] **Passo 3: implementar**

```java
package io.github.matheusanbs.wirelessautomate.client;

/**
 * Como a linha de abas de tipo cabe na largura (lógica pura): todas com nome; só a ativa com nome e as
 * outras no ícone; ou só ícones.
 */
public final class TabLayout {
    /** Largura de uma aba só com a bolinha da rede e o ícone. */
    public static final int ICON_TAB = 22;
    public static final int GAP = 3;

    public enum Mode { FULL, ACTIVE_NAME, ICONS }

    public record Result(Mode mode, int[] widths) {
    }

    private TabLayout() {
    }

    /** {@code fullWidths}: largura de cada aba com o nome; {@code available}: espaço para as abas. */
    public static Result choose(int[] fullWidths, int active, int available) {
        if (total(fullWidths) <= available) {
            return new Result(Mode.FULL, fullWidths.clone());
        }
        int[] compact = new int[fullWidths.length];
        for (int i = 0; i < compact.length; i++) {
            compact[i] = i == active ? fullWidths[i] : ICON_TAB;
        }
        if (total(compact) <= available) {
            return new Result(Mode.ACTIVE_NAME, compact);
        }
        int[] icons = new int[fullWidths.length];
        java.util.Arrays.fill(icons, ICON_TAB);
        return new Result(Mode.ICONS, icons);
    }

    private static int total(int[] widths) {
        int sum = GAP * Math.max(0, widths.length - 1);
        for (int w : widths) {
            sum += w;
        }
        return sum;
    }
}
```

```java
package io.github.matheusanbs.wirelessautomate.client;

/** Colunas de uma grade de cartões (lógica pura): quantas cabem, sem passar do número de cartões. */
public final class CardGrid {
    private CardGrid() {
    }

    public static int columns(int count, int available, int minWidth, int gap) {
        int fit = (available + gap) / (minWidth + gap);
        return Math.max(1, Math.min(count, fit));
    }
}
```

```java
package io.github.matheusanbs.wirelessautomate.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Texto variável que nunca passa do espaço: abrevia com reticências (ou quebra linha) e guarda o
 * texto inteiro para o tooltip. A tela chama {@link #beginFrame()} no começo do {@code render} e
 * mostra {@link #clipAt} no fim, se nenhum outro tooltip apareceu.
 */
public final class GuiText {
    /** Um texto cortado: a área dele e o texto inteiro. */
    public record Clip(int x, int y, int width, int height, Component full) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

    private static final List<Clip> CLIPS = new ArrayList<>();

    private GuiText() {
    }

    public static void beginFrame() {
        CLIPS.clear();
    }

    /** Uma linha em ({@code x}, {@code y}), cortada em {@code width}. */
    public static void draw(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        if (width <= 0) {
            return;
        }
        if (font.width(text) <= width) {
            GuiPaint.text(g, font, text, x, y, color);
            return;
        }
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, text, width), x, y, color);
        CLIPS.add(new Clip(x, y - 1, width, font.lineHeight + 1, text));
    }

    /** Até {@code maxLines} linhas de 10 px; a última é abreviada se sobrar texto. Devolve a altura usada. */
    public static int wrap(GuiGraphics g, Font font, Component text, int x, int y, int width, int maxLines, int color) {
        if (width <= 0 || maxLines <= 0) {
            return 0;
        }
        List<FormattedCharSequence> lines = font.split(text, width);
        int shown = Math.min(lines.size(), maxLines);
        for (int i = 0; i < shown; i++) {
            if (i == shown - 1 && lines.size() > maxLines) {
                break;
            }
            GuiPaint.text(g, font, lines.get(i), x, y + i * 10, color);
        }
        if (lines.size() > maxLines) {
            int last = maxLines - 1;
            Component rest = Component.literal(remainder(font, text, width, last));
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, rest, width), x, y + last * 10, color);
            CLIPS.add(new Clip(x, y - 1, width, maxLines * 10 + 1, text));
        }
        return shown * 10;
    }

    /** O texto a partir da linha {@code line} da quebra (para abreviar a última linha visível). */
    private static String remainder(Font font, Component text, int width, int line) {
        String plain = text.getString();
        List<net.minecraft.network.chat.FormattedText> parts = font.getSplitter()
                .splitLines(plain, width, net.minecraft.network.chat.Style.EMPTY);
        StringBuilder rest = new StringBuilder();
        for (int i = line; i < parts.size(); i++) {
            if (!rest.isEmpty()) {
                rest.append(' ');
            }
            rest.append(parts.get(i).getString().strip());
        }
        return rest.toString();
    }

    public static @Nullable Component clipAt(double mx, double my) {
        for (int i = CLIPS.size() - 1; i >= 0; i--) {
            if (CLIPS.get(i).contains(mx, my)) {
                return CLIPS.get(i).full();
            }
        }
        return null;
    }

    /** Textos cortados no último frame (o e2e confere que cada um tem tooltip). */
    public static int clipCount() {
        return CLIPS.size();
    }

    /** O centro do primeiro texto cortado, ou {@code null}. */
    public static int @Nullable [] firstClipCenter() {
        if (CLIPS.isEmpty()) {
            return null;
        }
        Clip c = CLIPS.getFirst();
        return new int[] {c.x() + c.width() / 2, c.y() + c.height() / 2};
    }
}
```

- [ ] **Passo 4: rodar**

Rode: `./gradlew test`
Esperado: todos os JUnit passando (inclusive os 7 novos).

- [ ] **Passo 5: commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/client/TabLayout.java src/main/java/io/github/matheusanbs/wirelessautomate/client/CardGrid.java src/main/java/io/github/matheusanbs/wirelessautomate/client/GuiText.java src/test/java/io/github/matheusanbs/wirelessautomate/client
git commit -m "Layout das abas, grade de cartões e texto que cabe (GuiText)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Tarefa 7: Roteador com abas adaptáveis e seletor de rede maior

**Arquivos:**
- Mudar: `<base>client/RouterScreen.java`, `<base>client/DevEndToEnd.java`, `lang/en_us.json`, `lang/pt_br.json`

**Interfaces:**
- Consome: `LoadedTypes.LIST`, `ResourceStyle`, `TabLayout`, `GuiText` (Tarefas 1, 4, 6).
- Produz: `RouterScreen.layoutTabs()` (chamado de `init()` e quando a aba ativa ou a largura mudam); `RouterScreen.tabMode()` → `TabLayout.Mode` (gancho do e2e).

- [ ] **Passo 1: tipos do registro e largura das abas**

No construtor (L164-168), troque a lista montada à mão por `types.addAll(LoadedTypes.LIST);`.

No `init()`, troque o laço das abas (L307-315) por botões criados sem posição e uma chamada a `layoutTabs()`:

```java
        for (ResourceType t : types) {
            FlatButton tab = add(new FlatButton(0, y + TAB_Y, TabLayout.ICON_TAB, TAB_H, typeName(t),
                    (g, b, hovered) -> paintTab(g, b, hovered, t), () -> selectType(t))
                    .tooltip(() -> tr("tab.tooltip", typeName(t), networkLabel(t))));
            tabButtons.put(t, tab);
        }
        layoutTabs();
```

```java
    /** Mínimo do seletor de rede à direita das abas (bolinha, um pedaço do nome e a seta). */
    private static final int NET_MIN = 92;
    private TabLayout.Mode tabMode = TabLayout.Mode.FULL;

    private void selectType(ResourceType t) {
        type = t;
        layoutTabs();
    }

    /** Larguras e posições das abas pelo espaço que sobra depois do seletor de rede. */
    private void layoutTabs() {
        int[] full = new int[types.size()];
        int active = 0;
        for (int i = 0; i < types.size(); i++) {
            full[i] = TAB_DOT + 8 + 3 + font.width(typeName(types.get(i))) + 12;
            if (types.get(i) == type) {
                active = i;
            }
        }
        int available = (x1() - X0) - NET_MIN - 6;
        TabLayout.Result layout = TabLayout.choose(full, active, available);
        tabMode = layout.mode();
        int tabX = leftPos + X0;
        for (int i = 0; i < types.size(); i++) {
            FlatButton tab = tabButtons.get(types.get(i));
            tab.setX(tabX);
            tab.setWidth(layout.widths()[i]);
            tabX += layout.widths()[i] + TabLayout.GAP;
        }
    }

    public TabLayout.Mode tabMode() {
        return tabMode;
    }
```

(Até a Tarefa 8, `x1()` é só `return X1;`: crie o método agora e troque os usos de `X1` por `x1()` neste arquivo.)

- [ ] **Passo 2: aba desenhada com ícone, nome só quando cabe**

`paintTab` (L913-925): depois do fundo e da bolinha da rede (que ficam), desenhe o ícone e, se a largura passar de `TabLayout.ICON_TAB`, o nome; a aba ativa ganha o sublinhado na cor do tipo:

```java
        ResourceStyle.drawIcon(g, t, b.getX() + 4 + TAB_DOT, b.getY() + 4);
        if (b.getWidth() > TabLayout.ICON_TAB) {
            int textX = b.getX() + 4 + TAB_DOT + 8 + 3;
            GuiText.draw(g, font, typeName(t), textX, b.getY() + 4, b.getX() + b.getWidth() - 4 - textX,
                    t == type ? GuiPaint.FG : GuiPaint.MUTED);
        }
        if (t == type) {
            g.fill(b.getX() + 1, b.getY() + b.getHeight() - 2, b.getX() + b.getWidth() - 1, b.getY() + b.getHeight() - 1,
                    ResourceStyle.color(t));
        }
```

- [ ] **Passo 3: seletor de rede com todo o espaço que sobra**

Em `refresh()` (L376-386), o seletor deixa de ter teto fixo:

```java
        int tabsEnd = leftPos + X0;
        for (FlatButton tab : tabButtons.values()) {
            tabsEnd = Math.max(tabsEnd, tab.getX() + tab.getWidth());
        }
        int netX = tabsEnd + 6;
        int netW = leftPos + x1() - netX;
        netTextMax = Math.max(24, netW - NET_PILL_PAD);
        networkButton.setX(netX);
        networkButton.setWidth(netW);
```

Apague `NET_TEXT_MAX` e o desenho do rótulo "Rede" à esquerda do seletor (L779, a chave `network.label` continua no tooltip do seletor: em `networkTooltip()`, comece com `tr("network.label")`). `paintNetwork` já abrevia com `netTextMax` e já põe o nome inteiro no tooltip quando corta.

- [ ] **Passo 4: aba sem tipos (energia) mostra a vazão do tier**

No lugar do texto `filter.energy` (L827), para qualquer tipo com `!type.filtered()`:

```java
            GuiText.draw(g, font, tr("filter.untyped", typeName(type)), x + rx(), y + FILTER_Y + 3, RW, GuiPaint.DISABLED);
            long tierRate = Config.TIERS.get(snapshot().tier()).rate(type);
            Component rateText = tierRate == 0 ? tr("rate.unlimited") : ResourceStyle.rate(type, tierRate);
            GuiText.draw(g, font, tr("rate.tier", rateText), x + rx(), y + FILTER_Y + 17, RW, GuiPaint.MUTED);
```

(`rx()` é `RX` até a Tarefa 8; crie o método agora. Leia o tier do snapshot pelo campo que `renderBg` já usa para a pílula do tier.) Os slots de cartão e o botão Editar já ficam escondidos quando o tipo não tem filtro/cartões (`hasFilter()`, `hasCardSlots`): confira que continuam.

Traduções novas (en_us / pt_br):
- `gui.wirelessautomate.router.filter.untyped`: `"No filter: %s has no types"` / `"Sem filtro: %s não tem tipos"`
- `gui.wirelessautomate.router.rate.tier`: `"Tier rate: %s"` / `"Vazão do tier: %s"`
- `gui.wirelessautomate.router.rate.unlimited`: `"no limit"` / `"sem limite"`

Apague `gui.wirelessautomate.router.filter.energy` dos dois arquivos.

- [ ] **Passo 5: todo texto variável pelo `GuiText`**

Troque, no `RouterScreen`, cada `GuiPaint.text(g, font, GuiPaint.ellipsize(font, X, W), ...)` por `GuiText.draw(g, font, X, ..., W, cor)` (L805-845, 879, 987: acesso, filtro, prioridade, redstone, título, nome do modo). O `rate()` no cabeçalho (L758) passa a ter largura: de `x + titleEnd + 8` até `tierX - 6`, via `GuiText.draw` com alinhamento à direita feito à mão (`int w = Math.min(font.width(rate), space); GuiText.draw(g, font, rate, tierX - 6 - w, ..., w, MUTED)`).

No começo do `render` (L694), antes do `super.render`, chame `GuiText.beginFrame();`. No fim do `render`, depois dos tooltips que já existem:

```java
        if (!hasTooltip) {
            Component clipped = GuiText.clipAt(mouseX, mouseY);
            if (clipped != null) {
                g.renderTooltip(font, clipped, mouseX, mouseY);
            }
        }
```

(`hasTooltip` = se algum tooltip de botão ou do visor já foi desenhado neste frame; use a mesma condição que o `render` já usa para não sobrepor tooltips.)

Para conferir: `grep -n "GuiPaint.text(" src/main/java/io/github/matheusanbs/wirelessautomate/client/RouterScreen.java` só pode listar textos fixos (rótulos de tradução sem variável) cuja largura cabe no tamanho mínimo.

- [ ] **Passo 6: e2e**

Em `DevEndToEnd`, depois do passo "de volta à aba Itens", acrescente:

```java
        // No mínimo (300), com os 4 tipos do runClient, as abas cabem com o nome só na ativa.
        list.add(new Step("abas no tamanho mínimo", STEP_TIMEOUT_MS,
                () -> { },
                () -> routerScreen().tabMode() == TabLayout.Mode.ACTIVE_NAME,
                () -> "modo das abas " + routerScreen().tabMode()));
        list.add(capture("1b-roteador-abas"));
```

- [ ] **Passo 7: rodar**

Rode: `./gradlew build runGameTestServer runGameTestServerChemicals` e depois `WA_E2E="$PWD/run/e2e" ./gradlew runClient`.
Esperado: tudo passando; `run/e2e/result.txt` = `OK`; em `run/e2e/1b-roteador-abas.png`, 4 abas (o `runClient` tem o Mekanism), a ativa com nome, e o seletor com o nome inteiro da rede.

- [ ] **Passo 8: commit**

```bash
git add -A src/main/java src/main/resources/assets/wirelessautomate/lang
git commit -m "Roteador: abas adaptáveis, seletor de rede maior e vazão do tier em abas sem filtro

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Tarefa 8: Roteador redimensionável

**Arquivos:**
- Mudar: `<base>client/RouterScreen.java`, `<base>menu/RouterMenu.java`, `<base>client/DevEndToEnd.java`, `lang/*.json`

**Interfaces:**
- Consome: Tarefa 7 (`x1()`, `rx()`, `layoutTabs()`).
- Produz: `RouterMenu.placeSlots(int rightX, int upgradeX, int inventoryY)`; `RouterScreen.previewResize(int width, int height)` e `RouterScreen.size()` → `int[] {w, h}` (ganchos do e2e).

- [ ] **Passo 1: slots que mudam de lugar**

Em `RouterMenu`, acrescente (os slots de cartão e de upgrade são recriados do mesmo tipo, para manter `mayPlace`, limites e os `instanceof`):

```java
    /**
     * Põe os slots no layout da tela (só no cliente, depois de redimensionar): cartões e inventário a
     * partir de {@code rightX}, inventário com o topo em {@code inventoryY}, upgrade em {@code upgradeX}.
     * A posição do {@link Slot} é final: cada slot é trocado por um igual, no mesmo índice.
     */
    public void placeSlots(int rightX, int upgradeX, int inventoryY) {
        for (int i = 0; i < slots.size(); i++) {
            Slot old = slots.get(i);
            Slot moved;
            if (old instanceof CardSlot) {
                moved = new CardSlot(old.container, old.getContainerSlot(), rightX + old.getContainerSlot() * 18, CARD_Y);
            } else if (old instanceof UpgradeSlot) {
                moved = new UpgradeSlot(old.container, upgradeX, UPGRADE_Y);
            } else {
                int c = old.getContainerSlot();
                int sx = rightX + (c % 9) * 18;
                int sy = c < 9 ? inventoryY + 58 : inventoryY + (c / 9 - 1) * 18;
                if (old.x == sx && old.y == sy) {
                    continue;
                }
                moved = new Slot(old.container, c, sx, sy);
            }
            moved.index = old.index;
            slots.set(i, moved);
        }
    }
```

Confira os construtores de `CardSlot` e `UpgradeSlot` (L280-299, 387+): se o `UpgradeSlot` recebe só `(container, x, y)`, use assim; ajuste a chamada se a assinatura for outra.

- [ ] **Passo 2: tamanho variável na tela**

Em `RouterScreen`, troque as constantes que dependem de `W`/`H` por métodos, com o tamanho lembrado na sessão (como `FilterScreen.savedW/savedH`):

```java
    private static final int MIN_W = 300;
    private static final int MIN_H = 240;
    private static final int GRIP = 7;
    private static final int EDGE = 3;
    private static int savedW = MIN_W;
    private static int savedH = MIN_H;

    private int w = MIN_W;
    private int h = MIN_H;
    private @Nullable Resize resizing;
    private double resizeCenterX;
    private double resizeCenterY;
    private double resizeGrabX;
    private double resizeGrabY;

    private enum Resize { WIDTH, HEIGHT, BOTH }

    private int x1() { return w - 9; }
    /** Coluna da direita: largura fixa ({@link #RW}), presa à borda direita. */
    private int rx() { return x1() - RW; }
    private int viewW() { return rx() - 8 - X0; }
    private int viewH() { return VIEW_H + (h - MIN_H); }
    private int facesY() { return BODY_Y + viewH() + 3; }
    private int faceW() { return (viewW() - 2 * FACE_GAP) / 3; }
    private int advSepY() { return ADV_SEP_Y + (h - MIN_H); }
    private int moreY() { return MORE_Y + (h - MIN_H); }
    private int prioY() { return PRIO_Y + (h - MIN_H); }
    private int redstoneY() { return REDSTONE_Y + (h - MIN_H); }
    private int inventoryY() { return RouterMenu.INVENTORY_Y + (h - MIN_H); }
    private int maxW() { return Math.max(MIN_W, width - 8); }
    private int maxH() { return Math.max(MIN_H, height - 8); }
```

Mantenha `RW = 162` como constante (a coluna da direita não cresce). Troque todos os usos de `X1`, `RX`, `VIEW_W`, `VIEW_H`, `FACES_Y`, `FACE_W`, `LX1` (= `X0 + viewW()`), `ADV_SEP_Y`, `MORE_Y`, `PRIO_Y`, `REDSTONE_Y`, `W`, `H` e das posições do inventário em `renderBg` (L849-856) pelos métodos. Os fundos dos slots de cartão e de upgrade passam a usar `rx() + 1 - 1` e `w - 26 - 1` no lugar de `CARD_BG_X`/`UPGRADE_BG_X`.

No `init()`, antes do `super.init()`:

```java
        w = Math.max(MIN_W, Math.min(savedW, maxW()));
        h = Math.max(MIN_H, Math.min(savedH, maxH()));
        imageWidth = w;
        imageHeight = h;
```

e, depois de montar os widgets: `menu.placeSlots(rx() + 1, w - 26, inventoryY());` e `machineView.setBounds(x + X0 + 1, y + BODY_Y + 1, viewW() - 2, viewH() - 2);`.

- [ ] **Passo 3: arrastar a borda e o canto**

Copie de `FilterScreen` (L1389-1448) o mesmo par `resizeAt`/`dragResize`, adaptado:

```java
    private @Nullable Resize resizeAt(double mx, double my) {
        int right = leftPos + w;
        int bottom = topPos + h;
        if (mx < leftPos || my < topPos || mx >= right || my >= bottom) {
            return null;
        }
        if (mx >= right - GRIP && my >= bottom - GRIP) {
            return Resize.BOTH;
        }
        if (mx >= right - EDGE) {
            return Resize.WIDTH;
        }
        return my >= bottom - EDGE ? Resize.HEIGHT : null;
    }

    private void dragResize(double mx, double my) {
        int newW = w;
        int newH = h;
        if (resizing != Resize.HEIGHT) {
            double wanted = 2 * (mx + resizeGrabX - resizeCenterX);
            newW = Math.max(MIN_W, Math.min(maxW(), (int) Math.round(wanted / 2) * 2));
        }
        if (resizing != Resize.WIDTH) {
            double wanted = 2 * (my + resizeGrabY - resizeCenterY);
            newH = Math.max(MIN_H, Math.min(maxH(), (int) Math.round(wanted / 2) * 2));
        }
        if (newW != w || newH != h) {
            savedW = newW;
            savedH = newH;
            rebuildWidgets();
        }
    }
```

Em `mouseClicked`, antes de tudo (botão esquerdo): se `resizeAt` não for nulo, guarde `resizing`, `resizeCenterX = leftPos + w / 2.0`, `resizeCenterY = topPos + h / 2.0`, `resizeGrabX = leftPos + w - mx`, `resizeGrabY = topPos + h - my` e retorne `true`. Em `mouseDragged`, com `resizing != null`, chame `dragResize` e retorne `true`. Em `mouseReleased`, zere `resizing`. Em `resize(Minecraft, int, int)`, zere `resizing` antes do `super`. No `render`, com o mouse numa borda (ou arrastando), mostre `Component.translatable("gui.wirelessautomate.resize.tooltip")`. Em `renderBg`, desenhe a alça do canto e o destaque da borda como `StorageListScreen.renderBg` (L709-727), com a cor do tier.

`rebuildWidgets()` refaz o `init()`: o estado da tela (aba, face, lista de redes aberta, prioridade expandida) está em campos e sobrevive; o nome em edição é fechado (`renaming = false`) antes de reconstruir.

Ganchos do e2e:

```java
    /** e2e: muda o tamanho como se a borda tivesse sido arrastada. */
    public void previewResize(int width, int height) {
        savedW = width;
        savedH = height;
        rebuildWidgets();
    }

    public int[] size() {
        return new int[] {w, h};
    }
```

Tradução: `gui.wirelessautomate.resize.tooltip`: `"Drag to resize"` / `"Arraste para redimensionar"`.

- [ ] **Passo 4: e2e**

Depois do passo da Tarefa 7, acrescente:

```java
        list.add(new Step("roteador maior", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(420, 300),
                () -> routerScreen().size()[0] == Math.min(420, Math.max(300, routerScreen().width - 8))
                        && routerScreen().tabMode() == TabLayout.Mode.FULL
                        && routerScreen().getMenu().slots.get(RouterMenu.INVENTORY_START).x
                                == routerScreen().size()[0] - 9 - 162 + 1,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size()) + ", abas " + routerScreen().tabMode()));
        list.add(capture("1c-roteador-grande"));
        list.add(new Step("roteador de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(300, 240),
                () -> routerScreen().size()[0] == 300 && routerScreen().size()[1] == 240,
                () -> "tamanho " + java.util.Arrays.toString(routerScreen().size())));
```

(Se a janela do e2e tiver menos de 428 px de GUI, a condição usa o máximo possível; o modo `FULL` com 4 abas cabe a partir de ~340 px.)

- [ ] **Passo 5: rodar**

Rode: `./gradlew build runGameTestServer runGameTestServerChemicals` e o e2e.
Esperado: tudo passando; `1c-roteador-grande.png` com o visor 3D maior, a coluna da direita do mesmo tamanho e o inventário colado na borda de baixo. Teste à mão no `runClient`: arrastar o canto, Shift + clique num item do inventário depois de crescer, fechar e abrir de novo (o tamanho volta).

- [ ] **Passo 6: commit**

```bash
git add -A src/main/java src/main/resources/assets/wirelessautomate/lang
git commit -m "Roteador redimensionável pela borda e pelo canto

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Tarefa 9: Vinculador com chips de tipo

**Arquivos:**
- Mudar: `<base>client/LinkerScreen.java`, `<base>packet/LinkerActionPayload.java` (enum `Op`), `<base>linker/LinkerActions.java` (L314 e um caso novo), `<base>client/DevEndToEnd.java`, `<base>gametest/LinkerAreaGameTests.java`, `lang/*.json`

**Interfaces:**
- Consome: `LinkerSnapshot.available()` (Tarefa 2), `ResourceStyle`, `GuiText`.
- Produz: `LinkerActionPayload.Op.SET_TABS` (valor = máscara de `LinkerTabs`).

- [ ] **Passo 1: GameTest do "Todos"**

Em `LinkerAreaGameTests`, perto dos testes de `TOGGLE_TAB` (L324-350), no mesmo formato deles (mesmos helpers para o jogador com o Vinculador e para mandar o payload):

```java
    @GameTest(template = "empty")
    public static void setTabsMarksAllAvailable(GameTestHelper helper) {
        ServerPlayer player = playerWithLinker(helper);
        ItemStack stack = player.getMainHandItem();
        LinkerItem.setTabs(stack, LinkerTabs.of(ResourceType.FLUID));
        LinkerActions.handle(player, new LinkerActionPayload(LinkerActionPayload.Op.SET_TABS, Optional.empty(), "",
                LinkerTabs.available(LoadedTypes.LIST).mask()));
        helper.assertTrue(LinkerItem.tabs(stack).isAll(LoadedTypes.LIST), "Todos não marcou tudo");
        LinkerActions.handle(player, new LinkerActionPayload(LinkerActionPayload.Op.SET_TABS, Optional.empty(), "", 0));
        helper.assertTrue(LinkerItem.tabs(stack).isAll(LoadedTypes.LIST), "conjunto vazio foi aceito");
        helper.succeed();
    }
```

Use os nomes reais dos helpers e do método que aplica o payload no servidor (veja como os testes das L324-350 chamam `TOGGLE_TAB`); `LinkerItem.setTabs` é o setter que o item já usa para o componente `linker_tabs` (se tiver outro nome, use o existente).

- [ ] **Passo 2: rodar e ver falhar**

Rode: `./gradlew runGameTestServer`
Esperado: falha de compilação (`SET_TABS` não existe).

- [ ] **Passo 3: `SET_TABS` no servidor**

Acrescente `SET_TABS` ao fim do `enum Op` em `LinkerActionPayload` e, em `LinkerActions` (junto do `case TOGGLE_TAB` da L314), `case SET_TABS -> setTabs(stack, payload.value());`:

```java
    /** "Todos" na tela: grava a máscara pedida, se ela tiver algum tipo disponível. */
    private static boolean setTabs(ItemStack stack, int mask) {
        LinkerTabs next = new LinkerTabs(mask);
        if (next.isEmpty(LoadedTypes.LIST)) {
            return false;
        }
        LinkerItem.setTabs(stack, next);
        return true;
    }
```

- [ ] **Passo 4: chips na tela**

Em `LinkerScreen`:
- `TABS` continua `ResourceType.values()`; a posição de cada chip passa a ser calculada em `refresh()` pela ordem entre os visíveis (`snapshot().available().contains(t)`): coluna `i % 2`, linha `i / 2`, a partir de `TYPE_Y`, com `typeW = (LW - 2) / 2` e `TYPE_H = 16` como hoje.
- A lista de redes encolhe quando os tipos precisam de mais linhas: `typeRows = (visíveis + 1) / 2`; `listRows = 8 - typeRows` (2 linhas de tipo → 6 de rede, como hoje; 3 → 5). `ROWS`, `LIST_H`, `NEW_Y`, `TYPE_LABEL_Y` e `TYPE_Y` viram métodos que usam `listRows()`. Os 6 botões de linha da lista continuam criados; os que passam de `listRows()` ficam invisíveis.
- Botão **Todos** à direita do rótulo "Tipos" (`x + X0 + LW - 50`, `y + typeLabelY() - 3`, 50 × 12), pintado como caixa de marcar com `tr("type.all")`; ação: se todas as disponíveis estão marcadas, manda `SET_TABS` com a máscara só da primeira disponível (não dá para ficar sem nenhuma); senão manda `SET_TABS` com `LinkerTabs.available(snapshot().available().types()).mask()`. Em preview (`DevScreenshot`), aplique direto no snapshot local como o `toggleTab` faz.
- `paintCheck` (L834-847): caixa 7 × 7, ícone `ResourceStyle.drawIcon` logo depois e o nome por `GuiText.draw` com a largura que sobra; marcado, a borda do chip na cor `ResourceStyle.color(t)` (em vez de `ACCENT`; no modo desvincular continua `UNLINK`). A mensagem do botão continua `typeName(t)` (o e2e acha o chip por ela).
- O botão de vincular diz quantos tipos: `linkLabel()` passa a `tr(unlink() ? "unlink.count.types" : "link.count.types", toLink(), effectiveCount)`; mantenha as chaves antigas `link.count`/`unlink.count` para quando todos os tipos estão marcados.
- Título (L465-472) e demais textos variáveis por `GuiText.draw`/`GuiText.wrap`; `GuiText.beginFrame()` no começo do `render` e o tooltip de texto cortado no fim, como no roteador.

Traduções:
- `gui.wirelessautomate.linker.link.count.types`: `"Link %s · %s tabs"` / `"Vincular %s · %s abas"`
- `gui.wirelessautomate.linker.unlink.count.types`: `"Unlink %s · %s tabs"` / `"Desvincular %s · %s abas"`

- [ ] **Passo 5: e2e**

O passo "só a aba Fluidos" (L1850-1864) continua achando os chips pelo nome. Acrescente, antes dele:

```java
        list.add(new Step("Todos marca tudo", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.type.all"), "Todos")),
                () -> serverLinker(stack -> LinkerItem.tabs(stack).isAll(LoadedTypes.LIST)),
                () -> "abas " + linkerScreen().getMenu().snapshot().tabs()));
        list.add(capture("8b-vinculador-chips"));
```

- [ ] **Passo 6: rodar**

Rode: `./gradlew build runGameTestServer runGameTestServerChemicals` e o e2e.
Esperado: tudo passando; `8b-vinculador-chips.png` com 4 chips (ícone, nome, borda na cor do tipo) e o botão Todos.

- [ ] **Passo 7: commit**

```bash
git add -A src/main/java src/main/resources/assets/wirelessautomate/lang
git commit -m "Vinculador: chips de tipo pelo registro e botão Todos

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Tarefa 10: Tablet com cartões por tipo, filtro na Lista e redimensionar

**Arquivos:**
- Mudar: `<base>client/TabletScreen.java`, `<base>client/DevEndToEnd.java`, `lang/*.json`

**Interfaces:**
- Consome: `NetworkView.type(ResourceType)`, `TypeStats`, `Query.type()` (Tarefa 5), `CardGrid`, `GuiText`, `ResourceStyle`.
- Produz: `TabletScreen.previewResize(int, int)`, `TabletScreen.size()`, `TabletScreen.cardCenter(ResourceType) → int[]`, `TabletScreen.currentTab()` (ganchos do e2e).

- [ ] **Passo 1: tamanho variável**

Mesmo mecanismo da Tarefa 8 (`MIN_W = 300`, `MIN_H = 240`, `savedW/savedH` estáticos, `resizeAt`, `dragResize`, `rebuildWidgets()`, alça e tooltip). Métodos no lugar das constantes:

| Hoje | Vira |
| --- | --- |
| `X1` | `x1() = w - 9` |
| `NODE_ROWS = 6` | `nodeRows() = 6 + (h - MIN_H) / NODE_ROW` |
| `ACTION_Y`, `PAGER_Y` | derivados de `nodeRows()` como hoje |
| `MAP_H = 136` | `mapH() = 136 + (h - MIN_H)`; `MAP_INFO_Y`, `LEGEND_Y` derivados |
| `SIDE_BOTTOM = 200`, `NEW_Y = 203`, `NEW_BOX_Y = 217` | `+ (h - MIN_H)` |
| `RW = X1 - RX` | `rw() = x1() - RX` |
| `W`, `H` em `panel`, `x + W / 2`, `y + H - 12`, L1124 `W - 20` | `w`, `h` |

O `mapW()` já usa `X1 - X0`: passa a `x1() - X0`.

- [ ] **Passo 2: cartões por tipo**

`renderStats` (L1314-1407): mantenha a barra de tempo do mod (L1319-1325). No lugar das colunas (L1345-1355):

```java
    /** 3 cartões por linha no tamanho mínimo, 5 numa linha a partir de 470 px. */
    private static final int CARD_MIN_W = 86;
    private static final int CARD_GAP = 4;
    /** Nome, vazão e até duas linhas de detalhe (26 + 2 × 10 + 1). */
    private static final int CARD_H = 47;

    /** Retângulos dos cartões no último frame, para o clique e o tooltip. */
    private final Map<ResourceType, int[]> cardRects = new EnumMap<>(ResourceType.class);

    private int renderTypeCards(GuiGraphics g, int top) {
        cardRects.clear();
        List<ResourceType> shown = LoadedTypes.LIST;
        int avail = x1() - X0;
        int cols = CardGrid.columns(shown.size(), avail, CARD_MIN_W, CARD_GAP);
        int cardW = (avail - CARD_GAP * (cols - 1)) / cols;
        for (int i = 0; i < shown.size(); i++) {
            ResourceType t = shown.get(i);
            long rate = 0;
            int sources = 0;
            int destinations = 0;
            int sleeping = 0;
            for (TabletSnapshot.NetworkView n : snapshot().networks()) {
                TabletSnapshot.TypeStats s = n.type(t);
                rate += s.rate();
                sources += s.sources();
                destinations += s.destinations();
                sleeping += s.sleeping();
            }
            int cx = leftPos + X0 + (i % cols) * (cardW + CARD_GAP);
            int cy = top + (i / cols) * (CARD_H + CARD_GAP);
            GuiPaint.box(g, cx, cy, cardW, CARD_H, GuiPaint.INSET, GuiPaint.LINE);
            g.fill(cx, cy, cx + cardW, cy + 1, ResourceStyle.color(t));
            ResourceStyle.drawIcon(g, t, cx + 4, cy + 4);
            GuiText.draw(g, font, ResourceStyle.name(t), cx + 15, cy + 4, cardW - 19, GuiPaint.FG);
            GuiText.draw(g, font, ResourceStyle.rate(t, rate), cx + 4, cy + 15, cardW - 8, ResourceStyle.color(t));
            Component detail = sleeping > 0 ? tr("stats.card.detail.sleeping", sources, destinations, sleeping)
                    : tr("stats.card.detail", sources, destinations);
            GuiText.wrap(g, font, detail, cx + 4, cy + 26, cardW - 8, 2, GuiPaint.MUTED);
            cardRects.put(t, new int[] {cx, cy, cardW, CARD_H});
        }
        int rows = (shown.size() + cols - 1) / cols;
        return top + rows * (CARD_H + CARD_GAP);
    }
```

`renderStats` chama `int after = renderTypeCards(g, y + BODY_Y + 20);`, desenha os avisos com `GuiText.wrap(g, font, warnings, x + X0, after + 2, x1() - X0, 2, cor)` e começa a lista de redes (separador e cartões por rede, L1362-1406) em `statsListY() = after + 26 - topPos` no lugar de `STATS_LIST_Y`. A quantidade de cartões de rede visíveis vira `(h - 8 - statsListY()) / STAT_CARD`, e a linha de vazões de cada rede (L1378-1383) monta a lista com os tipos de `LoadedTypes.LIST`, `ResourceStyle.rate(t, n.type(t).rate())` separados por ` · `, por `GuiText.draw`.

Traduções:
- `gui.wirelessautomate.tablet.stats.card.detail`: `"%s↑ %s↓"` / `"%s↑ %s↓"`
- `gui.wirelessautomate.tablet.stats.card.detail.sleeping`: `"%s↑ %s↓ · %s asleep"` / `"%s↑ %s↓ · %s dormindo"`
- `gui.wirelessautomate.tablet.stats.card.tooltip`: `"Click to list the routers with %s"` / `"Clique para listar os roteadores com %s"`
- `gui.wirelessautomate.tablet.list.type`: `"Only %s ✕"` / `"Só %s ✕"`
- `gui.wirelessautomate.tablet.list.type.tooltip`: `"Click to show every type"` / `"Clique para mostrar todos os tipos"`

- [ ] **Passo 3: clique no cartão filtra a Lista**

Estado novo: `private Optional<ResourceType> typeFilter = Optional.empty();`, iniciado de `menu.snapshot().query().type()` no construtor. `sendQuery()` passa `typeFilter`.

Em `clickBody` (L913-982), caso `STATS`:

```java
            case STATS -> {
                for (Map.Entry<ResourceType, int[]> e : cardRects.entrySet()) {
                    int[] r = e.getValue();
                    if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                        typeFilter = Optional.of(e.getKey());
                        page = 0;
                        listScroll = 0;
                        switchTab(Tab.LIST);
                        sendQuery();
                        return true;
                    }
                }
                return false;
            }
```

(Ajuste ao formato de retorno que o `clickBody` usa.) Em `bodyTooltip` (L1059-1088), no `STATS`, o cartão sob o mouse mostra `tr("stats.card.tooltip", ResourceStyle.name(t))`.

Na Lista, com `typeFilter` presente, um chip na linha do paginador, à esquerda (`x + X0`, `y + PAGER_Y`, largura `font.width(texto) + 10`, altura 14), com `tr("list.type", ResourceStyle.name(t))` e o ícone; clicar limpa o filtro (`typeFilter = Optional.empty(); page = 0; sendQuery();`). É um `FlatButton` da aba LIST, visível só com filtro (em `refresh()`).

- [ ] **Passo 4: texto pelo `GuiText`**

Mesma regra do roteador: `GuiText.beginFrame()` no começo do `render`, tooltip de texto cortado no fim, e todo `GuiPaint.ellipsize` / `wrapped(...)` com texto variável trocado por `GuiText.draw` / `GuiText.wrap` (o helper `wrapped` da L185 vira uma chamada a `GuiText.wrap`).

Ganchos do e2e:

```java
    public void previewResize(int width, int height) {
        savedW = width;
        savedH = height;
        rebuildWidgets();
    }

    public int[] size() {
        return new int[] {w, h};
    }

    public int @Nullable [] cardCenter(ResourceType type) {
        int[] r = cardRects.get(type);
        return r == null ? null : new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    public Tab currentTab() {
        return tab;
    }
```

(Torne o `enum Tab` público se ainda não for.)

- [ ] **Passo 5: e2e**

Nos `tabletSteps` (L802-923), depois da captura `7-tablet-lista`, acrescente:

```java
        list.add(new Step("aba Estatísticas", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.tablet.tab.stats"), "aba Estatísticas")),
                () -> tabletScreen().cardCenter(ResourceType.ITEM) != null,
                () -> "aba " + tabletScreen().currentTab()));
        list.add(capture("7c-tablet-estatisticas"));
        list.add(new Step("Tablet maior", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(470, 260),
                () -> tabletScreen().size()[0] > 300 && tabletScreen().cardCenter(ResourceType.ITEM) != null,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(capture("7d-tablet-estatisticas-grande"));
        list.add(new Step("cartão de energia filtra a Lista", STEP_TIMEOUT_MS,
                () -> {
                    int[] c = tabletScreen().cardCenter(ResourceType.ENERGY);
                    click(tabletScreen(), c[0], c[1]);
                },
                () -> tabletScreen().currentTab() == TabletScreen.Tab.LIST
                        && tabletScreen().getMenu().snapshot().query().type().equals(Optional.of(ResourceType.ENERGY)),
                () -> "aba " + tabletScreen().currentTab() + ", consulta " + tabletScreen().getMenu().snapshot().query()));
        list.add(capture("7e-tablet-lista-energia"));
        list.add(new Step("Tablet de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(300, 240),
                () -> tabletScreen().size()[0] == 300,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
```

Confira a chave da aba Estatísticas no `en_us.json` (`gui.wirelessautomate.tablet.tab.stats`, montada em L333 de `TabletScreen` a partir de `Tab.STATS`); use a que existir.

- [ ] **Passo 6: rodar**

Rode: `./gradlew build runGameTestServer runGameTestServerChemicals` e o e2e.
Esperado: tudo passando; `7c` com 3 cartões na primeira linha e 1 na segunda (4 tipos), `7d` com os 4 numa linha, `7e` na Lista com o chip "Só Energia ✕". Em nenhuma captura um texto passa da borda do seu cartão.

- [ ] **Passo 7: commit**

```bash
git add -A src/main/java src/main/resources/assets/wirelessautomate/lang
git commit -m "Tablet: cartões por tipo, clique filtra a Lista e tela redimensionável

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Tarefa 11: Texto vazando nos dois idiomas e documentação

**Arquivos:**
- Mudar: `<base>client/DevEndToEnd.java`, `<base>client/DevScreenshot.java` (se precisar de ganchos), `scripts/guide/gerar_guia.py`, páginas geradas em `src/main/resources/assets/wirelessautomate/guides/wirelessautomate/guide/` (e `_pt_br/`), `CLAUDE.md`, `docs/especificacao.md`, `docs/progresso.md`

- [ ] **Passo 1: e2e em português com nome longo**

Acrescente ao roteiro, depois dos passos do Tablet, uma volta em português (`language("pt_br")`, helper existente) com uma rede de nome longo (crie pelo mesmo caminho que o roteiro já usa para criar redes, nome `"Rede de teste com um nome bem comprido 40"`), abrindo o roteador, o Vinculador e o Tablet nos tamanhos mínimo e máximo e capturando:
`1d-roteador-pt-min`, `1e-roteador-pt-max`, `8c-vinculador-pt`, `7f-tablet-pt-min`, `7g-tablet-pt-max`. Em cada tela, um passo que, se `GuiText.clipCount() > 0`, move o mouse para `GuiText.firstClipCenter()` (helper `moveMouse`) e confere que `GuiText.clipAt(...)` devolve o texto inteiro (não nulo). Volte para `en_us` no fim.

- [ ] **Passo 2: rodar o e2e e olhar as capturas**

Rode: `WA_E2E="$PWD/run/e2e" ./gradlew runClient`
Esperado: `run/e2e/result.txt` = `OK`. Abra as capturas novas e confira, uma por uma, que nenhum texto passa da borda do seu botão, cartão ou painel. Se algum passar, troque aquele desenho por `GuiText` e rode de novo.

- [ ] **Passo 3: guia**

Em `scripts/guide/gerar_guia.py`, nas páginas do roteador, do Vinculador e do Tablet (procure pelos títulos das páginas), acrescente, nos dois idiomas:
- Roteador: "Arraste a borda direita, a de baixo ou o canto para aumentar a tela; o visor 3D cresce. Com pouco espaço, as abas mostram só o ícone (o nome aparece ao passar o mouse)." / "Drag the right edge, the bottom edge or the corner to make the screen bigger; the 3D view grows. When space is short, the tabs show only their icon (hover for the name)."
- Vinculador: "Marque os tipos nas caixas; Todos marca ou desmarca todos." / "Tick the types in the boxes; All ticks or unticks every one."
- Tablet: "A aba Estatísticas tem um cartão por tipo. Clique num cartão para ver na Lista só os roteadores daquele tipo; o chip "Só …" na Lista tira o filtro." / "The Statistics tab has one card per type. Click a card to list only the routers of that type; the "Only …" chip in the List removes the filter."

Rode `python scripts/guide/gerar_guia.py` e confira o diff das páginas.

- [ ] **Passo 4: docs do projeto**

- `CLAUDE.md`, mapa do código: `network/ResourceType.java` (registro dos tipos: chave, filtro, cartões, vazão padrão, mod exigido), `network/LoadedTypes.java`, `client/ResourceStyle.java`, `client/TabLayout.java` e `client/CardGrid.java` (lógica pura com JUnit, na lista de lógica pura), `client/GuiText.java` (todo texto variável das telas passa por ele), e a nota de que roteador e Tablet são redimensionáveis; contagem de GameTests (151 na run comum).
- `docs/especificacao.md`: telas do roteador, Vinculador e Tablet (redimensionar, abas adaptáveis, chips, cartões por tipo e filtro da Lista).
- `docs/progresso.md`: tabela de estado (Telas), próximo passo = "Etapa 1: aba Source (spec e plano a escrever sobre o registro)", e a linha no histórico com o que entrou, os números de testes e que `/wa face` passou a aceitar `chemical` com o Mekanism.

- [ ] **Passo 5: verificação final**

Rode: `./gradlew build runGameTestServer runGameTestServerChemicals` e o e2e.
Esperado: `BUILD SUCCESSFUL`, 151 + 7 GameTests, todos os JUnit, `result.txt` = `OK`.

- [ ] **Passo 6: commit**

```bash
git add -A scripts/guide src/main docs CLAUDE.md
git commit -m "Etapa 0: e2e em português, guia e documentação do registro e das telas

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
