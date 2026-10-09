# Aba Source do Ars Nouveau: plano de implementação (etapa 1)

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** com o Ars Nouveau instalado, o roteador transporta **Source** (aba nova, como a energia: sem filtro e sem cartões); sem o Ars, nada muda e o mod carrega igual.

**Arquitetura:** o Ars entra como dependência opcional, no molde do Mekanism: a API dele só em `compat/arsnouveau/` e no suporte dos GameTests de Source; o resto do mod passa pela ponte `network/Sources.java`. O `ResourceType` ganha `SOURCE` no fim; tudo o que a etapa 0 deixou genérico (abas, chips, Tablet, Configurador, comandos) acompanha. A transferência de energia vira um laço genérico de "um valor só" (`ScalarTransfer` + `ScalarAccess`), usado pela energia e pela Source.

**Stack:** Java 21, NeoForge 21.1.251 (Minecraft 1.21.1), ModDevGradle, JUnit 5, GameTests, Ars Nouveau 5.13.3.1423 (só compilação e runs de dev), GeckoLib e Curios (só runs de dev), Python 3 (guia).

**Spec:** [`docs/superpowers/specs/2026-10-08-source-ars-nouveau-design.md`](../specs/2026-10-08-source-ars-nouveau-design.md), seção "Etapa 1". Base: a etapa 0 ([spec](../specs/2026-10-08-registro-de-tipos-e-telas-design.md)), já no branch `etapa-0-registro-de-tipos`.

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- **Tipos do Ars** (`com.hollingsworth.arsnouveau.*`) só em `compat/arsnouveau/` e em `gametest/SourceTestSupport.java`; **nunca** na assinatura de método de classe `@EventBusSubscriber` ou `@GameTestHolder`. O resto do mod passa por `network/Sources.java`. A run `runGameTestServer` (sem o Ars) prova que o mod carrega sem ele.
- `ResourceType` continua puro (sem classes do Minecraft). `SOURCE` entra **no fim** do enum: chave `source`, sem filtro, sem cartões, vazão `sourcePerSecond` por segundo, mod `ars_nouveau`, padrões `1_000L, 16_000L, 256_000L, 0L`.
- Chaves existentes de NBT, componentes e config **não mudam**.
- Comentário da config: `Source por segundo, por face (Ars Nouveau; 0 = sem limite).`
- Cor da Source (ARGB): `0xFFFF5CC8`. Ícone: `textures/gui/type/source.png` (já existe).
- Dependência no `neoforge.mods.toml`: `ars_nouveau`, `type="optional"`, `versionRange="[5.2,)"`, `ordering="NONE"`, `side="BOTH"`.
- Versões de dev: Ars Nouveau `5.13.3.1423` (Maven BlameJared, `com.hollingsworth.ars_nouveau:ars_nouveau-1.21.1`), GeckoLib `4.9.2` e Curios `9.5.1+1.21.1` (Maven do Modrinth, já configurado no `build.gradle`).
- Run nova de GameTests: `gameTestServerSource`, pasta `run/gametest-source`, namespace `wirelessautomate_source`, propriedade `wirelessautomate.sourceTests=true`.
- Performance: nada de tick por bloco; capability só por `BlockCapabilityCache`; nada de sincronizar o cliente com a tela fechada.
- `ModPayloads.VERSION` continua `"8"` (nenhum payload muda de formato nesta etapa).
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource` (Git Bash, na raiz). Nas tarefas que mexem em tela, também o e2e: `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (resultado em `run/e2e/result.txt`, que deve dizer `OK`). Não rode dois jogos ao mesmo tempo (memória).

**Ajuste em relação à spec:** a spec previa um `network/SourceTransfer.java` com tipos do Ars, "no molde do `EnergyTransfer`". Copiar o laço da energia duplicaria ~100 linhas. Em vez disso, a Tarefa 2 transforma o `EnergyTransfer` num laço genérico (`ScalarTransfer`) que fala com o recurso por uma interface (`ScalarAccess`); a energia e a Source são duas implementações. Com isso os tipos do Ars ficam só em `compat/arsnouveau/` (o `network/` não importa nada do Ars), e a etapa 2 só acrescenta o lado "bulk" (`long`) do Tanque de Source na implementação da Source.

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `build.gradle`, `gradle.properties`, `src/main/templates/META-INF/neoforge.mods.toml`, `.github/workflows/build.yml` (mudar) | Ars `compileOnly`, Ars + GeckoLib + Curios no `runClient` e na run `gameTestServerSource`, dependência opcional |
| `src/main/resources/data/wirelessautomate_source/structure/empty.nbt` (novo) | Template dos GameTests de Source (cópia do `empty.nbt`) |
| `network/ScalarAccess.java` (novo) | Interface: achar o handler de uma face, perguntar, tirar e pôr um valor só (`long`) |
| `network/ScalarTransfer.java` (novo, a partir do `EnergyTransfer`) | Uma visita de uma origem de "um valor só": simula, divide com `EnergySplit`, extrai e entrega |
| `network/EnergyAccess.java` (novo) | `ScalarAccess` da energia (`BulkEnergy` primeiro, `IEnergyStorage` depois) |
| `network/EnergyTransfer.java` (apagar) | Substituído pelo `ScalarTransfer` + `EnergyAccess` |
| `network/ResourceType.java` (mudar) | `SOURCE` |
| `network/Sources.java` (novo) | Ponte: `LOADED`, `capability()`, `move(Port, long)` |
| `compat/arsnouveau/ArsSources.java` (novo) | A capability `ars_nouveau:source` e o `ScalarAccess` da Source |
| `network/NetworkManager.java`, `block/RouterBlockEntity.java`, `menu/RouterSnapshot.java` (mudar) | Caso `SOURCE` no despacho, cache da capability, slots da face |
| `Config.java`, `client/ResourceStyle.java`, `item/TierCoreItem.java`, `lang/en_us.json`, `lang/pt_br.json` (mudar) | Comentário da vazão, cor e textos, linha no tooltip dos Cartões de Upgrade |
| `gametest/SourceGameTests.java`, `gametest/SourceTestSupport.java` (novos) | GameTests da Source, na run com o Ars |
| `gametest/RouterMenuGameTests.java`, `gametest/NetworkGameTests.java` (mudar) | Sem o Ars, a aba Source não existe |
| `client/DevEndToEnd.java` (mudar) | e2e com 5 tipos e a aba Source |
| `scripts/guide/gerar_guia.py` + páginas geradas (mudar) | Página `source` e menções nas páginas do roteador, Vinculador, Configurador e Cartões |
| `CLAUDE.md`, `docs/especificacao.md`, `docs/progresso.md` (mudar) | Documentação |

---

### Task 1: Build com o Ars Nouveau opcional

**Files:**
- Modify: `gradle.properties`
- Modify: `build.gradle`
- Modify: `src/main/templates/META-INF/neoforge.mods.toml`
- Modify: `.github/workflows/build.yml`
- Create: `src/main/resources/data/wirelessautomate_source/structure/empty.nbt` (cópia de `src/main/resources/data/wirelessautomate_chemicals/structure/empty.nbt`)
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/gametest/SourceGameTests.java`

**Interfaces:**
- Produces: a run `runGameTestServerSource` (namespace `wirelessautomate_source`, propriedade `wirelessautomate.sourceTests=true`); a classe `SourceGameTests` com a constante `static final String NAMESPACE = "wirelessautomate_source"` e o método `static boolean enabled()`; as classes do Ars no classpath de compilação.

- [ ] **Step 1: Versões no `gradle.properties`**

Depois da linha `mekanism_version=...`, acrescente:

```properties
# Ars Nouveau para 1.21.1 em https://maven.blamejared.com/com/hollingsworth/ars_nouveau/ars_nouveau-1.21.1/
# (a API de Source é a mesma de 5.2.0.750 a 5.13.3.1423; o mods.toml pede [5.2,))
ars_version=5.13.3.1423
# Dependências do Ars, só nas runs de dev (Maven do Modrinth)
geckolib_version=4.9.2
curios_version=9.5.1+1.21.1
```

- [ ] **Step 2: Repositório e compilação no `build.gradle`**

No bloco `maven { name = 'BlameJared' ... content { ... } }`, acrescente a linha `includeGroup 'com.hollingsworth.ars_nouveau'` (com o comentário `// Ars Nouveau (Source, ver compat/arsnouveau).`).

No bloco `dependencies` principal, depois do `compileOnly "mekanism:..."`:

```groovy
    // Ars Nouveau: o jar inteiro só na compilação (não há jar de API), sem as dependências dele. O
    // mod completo, com GeckoLib e Curios, vai para a pasta mods do runClient e da run
    // gameTestServerSource (abaixo). Tudo que toca na API fica em compat/arsnouveau e só carrega
    // com o Ars presente.
    compileOnly("com.hollingsworth.ars_nouveau:ars_nouveau-${minecraft_version}:${ars_version}") {
        transitive = false
    }
```

- [ ] **Step 3: Ars nas runs de dev**

Em `configurations { ... }` (o bloco com `benchMods`, `clientMods`, `chemicalTestMods`), acrescente:

```groovy
    sourceTestMods {
        canBeConsumed = false
        transitive = false
    }
```

No `dependencies` das runs (o que tem `clientMods ...`), acrescente:

```groovy
    // Ars Nouveau (Source) e as dependências obrigatórias dele.
    clientMods "com.hollingsworth.ars_nouveau:ars_nouveau-${minecraft_version}:${ars_version}"
    clientMods "maven.modrinth:geckolib:${geckolib_version}"
    clientMods "maven.modrinth:curios:${curios_version}"
    sourceTestMods "com.hollingsworth.ars_nouveau:ars_nouveau-${minecraft_version}:${ars_version}"
    sourceTestMods "maven.modrinth:geckolib:${geckolib_version}"
    sourceTestMods "maven.modrinth:curios:${curios_version}"
```

No `prepareClientMods`, acrescente `'ars_nouveau-*.jar', 'geckolib-*.jar', 'curios-*.jar'` à lista do `exclude` do `preserve`. Atualize o comentário de cima ("O cliente de dev ... carrega o Sophisticated Storage, o Observable, o Mekanism, o Ars Nouveau e o GuideME").

Depois do bloco da `gameTestServerChemicals`, acrescente:

```groovy
// GameTests de Source: um servidor de testes próprio, com o Ars Nouveau (e GeckoLib e Curios) na
// pasta mods, que roda só o namespace wirelessautomate_source (gametest/SourceGameTests).
// ./gradlew runGameTestServerSource
var prepareSourceTestMods = tasks.register('prepareSourceTestMods', Sync) {
    from configurations.sourceTestMods
    into layout.projectDirectory.dir('run/gametest-source/mods')
}

neoForge.runs {
    gameTestServerSource {
        type = 'gameTestServer'
        gameDirectory = project.file('run/gametest-source')
        systemProperty 'neoforge.enabledGameTestNamespaces', 'wirelessautomate_source'
        systemProperty 'wirelessautomate.sourceTests', 'true'
        taskBefore prepareSourceTestMods
    }
}
```

No fim do arquivo, acrescente o apagar do mundo e a conferência do log para a run nova:

```groovy
def sourceTestWorld = layout.projectDirectory.dir('run/gametest-source/world').asFile
tasks.matching { it.name == 'runGameTestServerSource' }.configureEach {
    doFirst {
        sourceTestWorld.deleteDir()
    }
}
```

e troque o mapa da conferência por
`[runGameTestServer: 'run/gametest', runGameTestServerChemicals: 'run/gametest-chemicals', runGameTestServerSource: 'run/gametest-source'].each { ... }`
(o corpo não muda). Coloque o bloco `sourceTestWorld` antes desse mapa.

- [ ] **Step 4: Dependência opcional no `neoforge.mods.toml`**

Depois do bloco do Mekanism:

```toml
# Ars Nouveau: a aba Source. A API de Source é a mesma desde a 5.2.0 (a primeira para 1.21.1).
[[dependencies.${mod_id}]]
    modId="ars_nouveau"
    type="optional"
    versionRange="[5.2,)"
    ordering="NONE"
    side="BOTH"
```

- [ ] **Step 5: Template e primeiro GameTest da run nova**

Copie o template:

```bash
mkdir -p src/main/resources/data/wirelessautomate_source/structure
cp src/main/resources/data/wirelessautomate_chemicals/structure/empty.nbt src/main/resources/data/wirelessautomate_source/structure/empty.nbt
```

Crie `gametest/SourceGameTests.java` (sem tipos do Ars):

```java
package io.github.matheusanbs.wirelessautomate.gametest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Source do Ars Nouveau: Source Jars de verdade, um roteador em cima de cada (facing=UP, face
 * configurada {@link net.minecraft.core.Direction#UP}). Rodam só na run {@code runGameTestServerSource},
 * que tem o Ars na pasta mods e liga o namespace {@value #NAMESPACE} (o template é
 * {@code data/wirelessautomate_source/structure/empty.nbt}). Sem o Ars, só passam.
 *
 * <p>Nenhum tipo do Ars nas assinaturas: o NeoForge inspeciona esta classe por reflexão mesmo sem o
 * Ars. O que usa a API fica em {@code SourceTestSupport}.
 */
@GameTestHolder(SourceGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class SourceGameTests {
    static final String NAMESPACE = "wirelessautomate_source";
    static final ResourceLocation JAR = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_jar");

    /** A run de Source está ligada e o Ars está presente. */
    static boolean enabled() {
        return Boolean.getBoolean("wirelessautomate.sourceTests") && ModList.get().isLoaded("ars_nouveau");
    }

    /** A run de Source carrega o Ars, e a Source Jar existe no registro. */
    @GameTest(template = "empty")
    public static void arsNouveauIsLoaded(GameTestHelper helper) {
        if (!Boolean.getBoolean("wirelessautomate.sourceTests")) {
            helper.succeed();
            return;
        }
        helper.assertTrue(ModList.get().isLoaded("ars_nouveau"), "Ars Nouveau não carregou na run de Source");
        helper.assertTrue(BuiltInRegistries.BLOCK.get(JAR) != Blocks.AIR, "sem a Source Jar no registro");
        helper.succeed();
    }

    private SourceGameTests() {
    }
}
```

- [ ] **Step 6: CI**

Em `.github/workflows/build.yml`, depois do passo que roda `./gradlew runGameTestServerChemicals`, acrescente um passo igual com `./gradlew runGameTestServerSource` (mesmo formato do de químicos, nome "GameTests de Source (Ars Nouveau)").

- [ ] **Step 7: Rodar e conferir**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`
Expected: BUILD SUCCESSFUL; as três runs chegam a "required tests passed"; a run de Source mostra `arsNouveauIsLoaded` passando.

Se o servidor da run de Source não subir:
- `NoClassDefFoundError` de `org.apache.lucene.*`: acrescente, no `build.gradle`, `additionalRuntimeClasspath 'org.apache.lucene:lucene-core:10.1.0'`, `'org.apache.lucene:lucene-analysis-common:10.1.0'` e `'org.apache.lucene:lucene-queryparser:10.1.0'` (o Ars usa no índice da documentação) e rode de novo.
- Exigência de versão do NeoForge maior que a 21.1.251: **pare** e reporte BLOCKED com a mensagem; não suba o `neo_version` (ele vira o mínimo do mod e o ATM10 usa 21.1.251).

Também confira que o cliente sobe com o Ars: `./gradlew runClient`, espere o menu principal e feche. Se faltar lucene no cliente, a mesma correção acima resolve os dois.

- [ ] **Step 8: Commit**

```bash
git add gradle.properties build.gradle src/main/templates/META-INF/neoforge.mods.toml .github/workflows/build.yml src/main/resources/data/wirelessautomate_source src/main/java/io/github/matheusanbs/wirelessautomate/gametest/SourceGameTests.java
git commit -m "Etapa 1: Ars Nouveau opcional no build, no runClient e numa run de GameTests própria"
```

---

### Task 2: Laço de "um valor só" (`ScalarTransfer`) no lugar do `EnergyTransfer`

Refatoração sem mudança de comportamento: os GameTests de energia que já existem (`TransferGameTests`, `WakeGameTests`, `StorageGameTests` com a Bateria) são a rede de segurança.

**Files:**
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/network/ScalarAccess.java`
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/network/ScalarTransfer.java`
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/network/EnergyAccess.java`
- Delete: `src/main/java/io/github/matheusanbs/wirelessautomate/network/EnergyTransfer.java`
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/network/NetworkManager.java` (o caso `ENERGY` de `visit`)

**Interfaces:**
- Produces: `public interface ScalarAccess` (métodos abaixo); `final class ScalarTransfer` com `static boolean move(Port source, long now, ScalarAccess access)` (pacote `network`); `final class EnergyAccess implements ScalarAccess` com `static final EnergyAccess INSTANCE`.

- [ ] **Step 1: A interface**

```java
package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Como o {@link ScalarTransfer} fala com um recurso de "um valor só" (sem tipos dentro: energia,
 * Source). O handler é o objeto da capability da face, devolvido como {@code Object} para o motor não
 * depender da API de outro mod; só a implementação sabe o tipo dele. As quantidades são {@code long};
 * a implementação corta no teto da API de fora ({@code int}) quando for o caso.
 *
 * <p>As implementações não guardam estado e não alocam: o laço chama estes métodos a cada visita.
 */
public interface ScalarAccess {
    /** O handler da máquina pela face {@code machineFace} do nó (pelo cache do roteador), ou {@code null}. */
    @Nullable Object handler(RouterBlockEntity node, Direction machineFace);

    /** O handler pode dar alguma coisa (sem olhar quanto tem). */
    boolean canExtract(Object handler);

    /** O handler pode receber alguma coisa (sem olhar quanto cabe). */
    boolean canReceive(Object handler);

    /** Tira até {@code amount}; devolve quanto saiu (ou sairia, simulando). */
    long extract(Object handler, long amount, boolean simulate);

    /** Põe até {@code amount}; devolve quanto entrou (ou entraria, simulando). */
    long insert(Object handler, long amount, boolean simulate);
}
```

- [ ] **Step 2: O laço genérico**

Crie `ScalarTransfer.java` com o conteúdo de `EnergyTransfer.java`, trocando os dois tipos de handler (`BulkEnergy`/`IEnergyStorage`) por `Object` + `ScalarAccess`. O código inteiro:

```java
package io.github.matheusanbs.wirelessautomate.network;

import java.util.Arrays;
import java.util.List;

/**
 * Move um recurso de "um valor só" (energia, Source) de uma origem para os destinos dela num único
 * passe: simula a extração, pergunta (simulando) quanto cada destino aceita, divide com
 * {@link EnergySplit}, extrai de verdade o total dividido e entrega. Extrair antes de entregar
 * garante que nunca se cria nada: se a extração real der menos, os primeiros da ordem recebem até
 * acabar; o que um destino recusar depois de aceitar na simulação volta para a origem, e o que nem
 * ela aceitar se perde.
 *
 * <p>O recurso chega pelo {@link ScalarAccess}: {@link EnergyAccess} (a Bateria do mod em
 * {@code long}, as outras máquinas em {@code int}) e o da Source ({@code compat/arsnouveau}).
 *
 * <p>Os vetores de trabalho são reaproveitados entre chamadas (só a thread do servidor usa).
 */
final class ScalarTransfer {
    private static long[] wants = new long[16];
    private static long[] shares = new long[16];
    private static int[] priorities = new int[16];
    private static Object[] targets = new Object[16];

    /** Uma visita. Devolve {@code true} se moveu algo. */
    static boolean move(Port source, long now, ScalarAccess access) {
        Object handler = access.handler(source.node, source.face);
        RoundRobinOrder<Port> order = source.order;
        if (handler == null || !access.canExtract(handler) || order == null) {
            SourceSleep.nothingToMove(source, now);
            return false;
        }
        long tokens = source.limiter.available(now);
        if (tokens <= 0) {
            return false;
        }
        List<Port> pass = order.pass();
        if (!NetworkManager.hasAwakeDestination(pass, now)) {
            SourceSleep.untilDestinations(source, pass, now);
            return false;
        }
        long offered = access.extract(handler, tokens, true);
        if (offered <= 0) {
            SourceSleep.nothingToMove(source, now);
            return false;
        }
        // Tem o que dar e há destino acordado: se dormir, foi esperando destino (motivo do sono).
        source.offered = true;
        int count = pass.size();
        ensureCapacity(count);
        for (int i = 0; i < count; i++) {
            Port destination = pass.get(i);
            priorities[i] = destination.priority;
            wants[i] = 0;
            targets[i] = null;
            if (destination.destinationBackoff.isSleeping(now)) {
                continue;
            }
            Object target = access.handler(destination.node, destination.face);
            if (target == null) {
                // Destino sem máquina (ou com o chunk dela descarregado): dorme até a capability
                // voltar (o listener dela o acorda) ou o teto do sono, sem contar como cheio.
                destination.sleepWithoutMachine(now);
                continue;
            }
            long accepts = access.canReceive(target) ? access.insert(target, offered, true) : 0;
            if (accepts <= 0) {
                destination.destinationBackoff.sleep(now);
                continue;
            }
            wants[i] = accepts;
            targets[i] = target;
        }
        long total = EnergySplit.split(offered, wants, priorities, count, shares);
        long delivered = 0;
        if (total > 0) {
            long taken = access.extract(handler, total, false);
            long left = taken;
            for (int i = 0; i < count && left > 0; i++) {
                if (shares[i] <= 0) {
                    continue;
                }
                long received = access.insert(targets[i], Math.min(shares[i], left), false);
                if (received > 0) {
                    Port destination = pass.get(i);
                    destination.destinationBackoff.wake();
                    order.delivered(destination);
                    if (source.network != null) {
                        source.network.ops++;
                    }
                    left -= received;
                    delivered += received;
                }
            }
            if (left > 0) {
                access.insert(handler, left, false);
            }
        }
        Arrays.fill(targets, 0, count, null);
        if (delivered > 0) {
            source.node.addMoved(source.type, delivered);
            source.limiter.consume(delivered);
            source.sourceBackoff.wake();
            return true;
        }
        // Destinos que recusaram dormiram: se foram todos, a origem dorme até o primeiro acordar.
        SourceSleep.idle(source, pass, now);
        return false;
    }

    private static void ensureCapacity(int count) {
        if (wants.length >= count) {
            return;
        }
        int size = Math.max(count, wants.length * 2);
        wants = new long[size];
        shares = new long[size];
        priorities = new int[size];
        targets = new Object[size];
    }

    private ScalarTransfer() {
    }
}
```

- [ ] **Step 3: O acesso da energia**

```java
package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Energia para o {@link ScalarTransfer}. A Bateria do mod ({@link BulkEnergy}) vem primeiro: o lado
 * dela é em {@code long}, sem o teto de {@link Integer#MAX_VALUE} FE por chamada do
 * {@link IEnergyStorage}. Entre duas Baterias, bilhões de FE passam por tick; com máquinas e cabos de
 * outros mods, o lado deles continua em {@code int}.
 */
final class EnergyAccess implements ScalarAccess {
    static final EnergyAccess INSTANCE = new EnergyAccess();

    @Override
    public @Nullable Object handler(RouterBlockEntity node, Direction machineFace) {
        BulkEnergy bulk = node.bulkEnergy(machineFace);
        return bulk != null ? bulk : node.energy(machineFace);
    }

    @Override
    public boolean canExtract(Object handler) {
        return handler instanceof BulkEnergy || ((IEnergyStorage) handler).canExtract();
    }

    @Override
    public boolean canReceive(Object handler) {
        return handler instanceof BulkEnergy || ((IEnergyStorage) handler).canReceive();
    }

    @Override
    public long extract(Object handler, long amount, boolean simulate) {
        return handler instanceof BulkEnergy bulk ? bulk.extract(amount, simulate)
                : ((IEnergyStorage) handler).extractEnergy((int) Math.min(amount, Integer.MAX_VALUE), simulate);
    }

    @Override
    public long insert(Object handler, long amount, boolean simulate) {
        return handler instanceof BulkEnergy bulk ? bulk.insert(amount, simulate)
                : ((IEnergyStorage) handler).receiveEnergy((int) Math.min(amount, Integer.MAX_VALUE), simulate);
    }

    private EnergyAccess() {
    }
}
```

Nota de equivalência: o `EnergyTransfer` antigo não perguntava `canExtract`/`canReceive` à Bateria (só ao `IEnergyStorage`); aqui a Bateria responde `true` nos dois, o que dá o mesmo resultado.

- [ ] **Step 4: Despacho e remoção**

Em `NetworkManager.visit`, troque `case ENERGY -> EnergyTransfer.move(source, now) ? MOVED : 0;` por `case ENERGY -> ScalarTransfer.move(source, now, EnergyAccess.INSTANCE) ? MOVED : 0;`. Apague `EnergyTransfer.java`. Procure referências restantes (`grep -rn EnergyTransfer src docs CLAUDE.md`) e troque por `ScalarTransfer` (javadoc e o mapa do `CLAUDE.md`: a linha `network/ItemTransfer.java, FluidTransfer.java, EnergyTransfer.java` passa a `network/ItemTransfer.java, FluidTransfer.java, ScalarTransfer.java` com o papel "Uma visita de uma origem, por tipo de recurso (energia e Source pelo `ScalarTransfer`, com um `ScalarAccess` cada)").

- [ ] **Step 5: Rodar**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`
Expected: tudo passa, com o mesmo número de GameTests de antes (a refatoração não muda comportamento).

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java/io/github/matheusanbs/wirelessautomate/network CLAUDE.md
git commit -m "Etapa 1: transferência de energia num laço genérico de um valor só (ScalarTransfer)"
```

---

### Task 3: Tipo Source no registro, no motor e no roteador

**Files:**
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/network/ResourceType.java`
- Modify: `src/test/java/io/github/matheusanbs/wirelessautomate/network/ResourceTypeTest.java`
- Modify: `src/test/java/io/github/matheusanbs/wirelessautomate/preset/PasteTypesTest.java`
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/network/Sources.java`
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/compat/arsnouveau/ArsSources.java`
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/network/NetworkManager.java`
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/block/RouterBlockEntity.java`
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/menu/RouterSnapshot.java`
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/Config.java`
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/client/ResourceStyle.java`
- Modify: `src/main/resources/assets/wirelessautomate/lang/en_us.json`, `pt_br.json`
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/gametest/SourceTestSupport.java`
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/gametest/SourceGameTests.java`
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/gametest/RouterMenuGameTests.java`
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/gametest/NetworkGameTests.java`

**Interfaces:**
- Consumes: `ScalarAccess`, `ScalarTransfer.move(Port, long, ScalarAccess)` (Task 2); `SourceGameTests.NAMESPACE`, `SourceGameTests.JAR`, `SourceGameTests.enabled()` (Task 1).
- Produces: `ResourceType.SOURCE`; `Sources.LOADED`, `Sources.capability()`, `Sources.move(Port, long)`; `ArsSources.BLOCK` (`BlockCapability<ISourceCap, @Nullable Direction>`) e `ArsSources.ACCESS` (`ScalarAccess`); `RouterBlockEntity.arsSource(Direction)` → `@Nullable Object`. A etapa 2 vai acrescentar o lado bulk no `ArsSources.ACCESS`.

- [ ] **Step 1: Testes JUnit do registro (falham)**

Em `ResourceTypeTest`:
- em `keysAreTheSavedNamesAndUnique`, acrescente `assertEquals("source", ResourceType.SOURCE.key());`
- em `byKeyFindsAndRejects`, troque `assertNull(ResourceType.byKey("source"));` por `assertSame(ResourceType.SOURCE, ResourceType.byKey("source"));` e acrescente `assertNull(ResourceType.byKey("mana"));`
- em `filterAndCards`, acrescente `assertFalse(ResourceType.SOURCE.filtered() || ResourceType.SOURCE.cards());`
- em `defaultRatesMatchTheTierTable`, acrescente:

```java
        assertEquals(1_000L, ResourceType.SOURCE.defaultRate(0));
        assertEquals(16_000L, ResourceType.SOURCE.defaultRate(1));
        assertEquals(256_000L, ResourceType.SOURCE.defaultRate(2));
        assertEquals(0L, ResourceType.SOURCE.defaultRate(3));
```

- em `rateKeysKeepTheConfigNames`, acrescente `assertEquals("sourcePerSecond", ResourceType.SOURCE.rateKey());` e `assertFalse(ResourceType.SOURCE.ratePerTick());`
- troque `availableDependsOnTheMods` por:

```java
    @Test
    void availableDependsOnTheMods() {
        assertEquals(List.of(ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY),
                ResourceType.available(mod -> false));
        assertEquals(List.of(ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY, ResourceType.CHEMICAL),
                ResourceType.available(mod -> mod.equals("mekanism")));
        assertEquals(List.of(ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY, ResourceType.SOURCE),
                ResourceType.available(mod -> mod.equals("ars_nouveau")));
        assertEquals(List.of(ResourceType.values()), ResourceType.available(mod -> true));
        assertEquals("ars_nouveau", ResourceType.SOURCE.requiredMod());
    }
```

- acrescente:

```java
    @Test
    void sourceIsLastSoSavedOrdinalsKeep() {
        assertEquals(4, ResourceType.SOURCE.ordinal());
        assertEquals(3, ResourceType.CHEMICAL.ordinal());
    }
```

Em `PasteTypesTest`, troque `WITH_CHEMICALS = List.of(ResourceType.values())` por `List.of(ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY, ResourceType.CHEMICAL)` e acrescente:

```java
    @Test
    void sourceComesAfterChemicals() {
        List<ResourceType> all = List.of(ResourceType.values());
        assertEquals(ResourceType.SOURCE, PasteTypes.next(ResourceType.CHEMICAL, 1, all));
        assertNull(PasteTypes.next(ResourceType.SOURCE, 1, all));
        assertEquals(ResourceType.SOURCE, PasteTypes.next(null, -1, all));
    }
```

(confira os imports `assertEquals`/`assertNull` que o arquivo já tem).

Run: `./gradlew test`
Expected: FAIL de compilação (`ResourceType.SOURCE` não existe).

- [ ] **Step 2: A constante**

No `ResourceType`, depois de `CHEMICAL(...)`, troque o `;` por `,` e acrescente:

```java
    /** Só existe com o Ars Nouveau instalado. Um valor só, como a energia: sem filtro e sem cartões. */
    SOURCE("source", false, false, "sourcePerSecond", false, "ars_nouveau", 1_000L, 16_000L, 256_000L, 0L);
```

- [ ] **Step 3: A capability do Ars e a ponte**

`compat/arsnouveau/ArsSources.java`:

```java
package io.github.matheusanbs.wirelessautomate.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.ScalarAccess;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * O que o mod usa da API do Ars Nouveau para a Source. Só é carregado com o Ars presente: o resto do
 * mod passa por {@code network.Sources}.
 *
 * <p>A capability é criada pelo mesmo nome e tipo que o Ars usa ({@code ars_nouveau:source},
 * {@link ISourceCap}); o NeoForge devolve a mesma instância. Ela é registrada pelo Ars para a Source
 * Jar, a jarra criativa, os Sourcelinks, os Relays e a Imbuement Chamber.
 */
public final class ArsSources {
    public static final BlockCapability<ISourceCap, @Nullable Direction> BLOCK = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source"), ISourceCap.class);

    /** A Source para o laço de um valor só; a {@link ISourceCap} é em {@code int}: corta no teto dela. */
    public static final ScalarAccess ACCESS = new ScalarAccess() {
        @Override
        public @Nullable Object handler(RouterBlockEntity node, Direction machineFace) {
            return node.arsSource(machineFace);
        }

        @Override
        public boolean canExtract(Object handler) {
            return ((ISourceCap) handler).canExtract();
        }

        @Override
        public boolean canReceive(Object handler) {
            return ((ISourceCap) handler).canReceive();
        }

        @Override
        public long extract(Object handler, long amount, boolean simulate) {
            return ((ISourceCap) handler).extractSource(clamp(amount), simulate);
        }

        @Override
        public long insert(Object handler, long amount, boolean simulate) {
            return ((ISourceCap) handler).receiveSource(clamp(amount), simulate);
        }
    };

    private static int clamp(long amount) {
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

    private ArsSources() {
    }
}
```

`network/Sources.java`:

```java
package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.compat.arsnouveau.ArsSources;
import net.minecraft.core.Direction;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * Ponte para a Source do Ars Nouveau sem carregar classes dele quando ele não está instalado. Quem
 * chama confere {@link #LOADED} (ou chama só com um handler que veio daqui); {@link ArsSources} só é
 * carregada pela JVM quando um destes métodos é chamado com o Ars presente.
 */
public final class Sources {
    /** O Ars Nouveau está instalado: a aba Source existe e o motor move Source. */
    public static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("ars_nouveau");

    /** A capability de bloco da Source ({@code ars_nouveau:source}), ou {@code null} sem o Ars. */
    public static @Nullable BlockCapability<?, @Nullable Direction> capability() {
        return LOADED ? ArsSources.BLOCK : null;
    }

    /** Uma visita de uma origem de Source; sem o Ars não há portas de Source. */
    static boolean move(Port source, long now) {
        return LOADED && ScalarTransfer.move(source, now, ArsSources.ACCESS);
    }

    private Sources() {
    }
}
```

- [ ] **Step 4: Motor, roteador e snapshot**

`NetworkManager.visit`: acrescente `case SOURCE -> Sources.move(source, now) ? MOVED : 0;` depois do caso `CHEMICAL`.

`RouterBlockEntity`:
- import `io.github.matheusanbs.wirelessautomate.network.Sources`;
- depois de `chemicalCaches`:

```java
    /** Source do Ars Nouveau; o handler fica como {@code Object} para esta classe não depender dele. */
    private final BlockCapabilityCache<Object, @Nullable Direction>[] sourceCaches = newCaches();
```

- depois de `chemicals(Direction)`:

```java
    /**
     * Source do Ars Nouveau ({@code ISourceCap}) pela face, ou {@code null} sem a capability ou sem o
     * Ars. Devolvido como {@code Object}: só o código de {@code compat/arsnouveau} sabe o tipo.
     */
    @SuppressWarnings("unchecked")
    public @Nullable Object arsSource(Direction machineFace) {
        BlockCapability<?, @Nullable Direction> capability = Sources.capability();
        return capability == null ? null
                : capability(sourceCaches, (BlockCapability<Object, @Nullable Direction>) capability,
                        ResourceType.SOURCE, machineFace);
    }
```

- em `clearCaches()`, acrescente `Arrays.fill(sourceCaches, null);`.

`RouterSnapshot.slots`: acrescente o caso e atualize o javadoc para "Slots (itens), tanques (fluidos e químicos) ou 1 (energia e Source) da face; {@code -1} sem a capability.":

```java
            case SOURCE -> router.arsSource(face) == null ? -1 : 1;
```

- [ ] **Step 5: Config, estilo e textos**

`Config.RATE_COMMENTS`: acrescente a entrada `"sourcePerSecond", "Source por segundo, por face (Ars Nouveau; 0 = sem limite)."`.

`ResourceStyle`:
- `color`: `case SOURCE -> 0xFFFF5CC8;`
- `rate`: `case SOURCE -> Component.translatable("gui.wirelessautomate.router.rate.source", abbreviated);`
- `access`: `case SOURCE -> Component.translatable(prefix + "source");`
- javadoc de `rate`: "(itens/s, mB/s ou B/s, FE/t, Source/s)"; de `access`: "(slots, tanques, bateria, Source)".

Traduções (mantenha a ordem: cada chave junto das irmãs):

| Chave | en_us | pt_br |
| --- | --- | --- |
| `gui.wirelessautomate.router.type.source` | `Source` | `Source` |
| `gui.wirelessautomate.router.rate.source` | `%s Source/s` | `%s Source/s` |
| `gui.wirelessautomate.router.access.source` | `Source` | `Source` |
| `gui.wirelessautomate.tablet.type.source` | `Source` | `Source` |

- [ ] **Step 6: Sem o Ars, a aba não existe (run comum)**

Em `RouterMenuGameTests`, depois de `helper.assertValueEqual(snapshot.face(ResourceType.CHEMICAL, Direction.UP).slots(), -1, "químicos");`:

```java
        helper.assertValueEqual(snapshot.face(ResourceType.SOURCE, Direction.UP).slots(), -1, "Source");
```

Em `NetworkGameTests`, depois do bloco `if (!Chemicals.LOADED) { ... }`:

```java
            if (!Sources.LOADED) {
                helper.assertTrue(router.networkId(ResourceType.SOURCE) == null, "Source entrou sem o Ars Nouveau");
            }
```

(import `io.github.matheusanbs.wirelessautomate.network.Sources`).

- [ ] **Step 7: Suporte dos GameTests de Source (API do Ars)**

`gametest/SourceTestSupport.java`:

```java
package io.github.matheusanbs.wirelessautomate.gametest;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import io.github.matheusanbs.wirelessautomate.compat.arsnouveau.ArsSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * A parte dos testes de Source que usa a API do Ars Nouveau. Não é {@code @GameTestHolder} nem
 * {@code @EventBusSubscriber}: o NeoForge inspeciona essas classes por reflexão, e um tipo do Ars numa
 * assinatura impede o mod de carregar sem ele. Só é chamada por {@link SourceGameTests} com o Ars presente.
 */
final class SourceTestSupport {
    private static ISourceCap cap(ServerLevel level, BlockPos pos) {
        ISourceCap cap = level.getCapability(ArsSources.BLOCK, pos, Direction.UP);
        if (cap == null) {
            throw new IllegalStateException("sem capability de Source em " + pos.toShortString());
        }
        return cap;
    }

    static boolean hasSource(ServerLevel level, BlockPos pos) {
        return level.getCapability(ArsSources.BLOCK, pos, Direction.UP) != null;
    }

    static int amount(ServerLevel level, BlockPos pos) {
        return cap(level, pos).getSource();
    }

    static void set(ServerLevel level, BlockPos pos, int amount) {
        cap(level, pos).setSource(amount);
    }

    static int capacity(ServerLevel level, BlockPos pos) {
        return cap(level, pos).getSourceCapacity();
    }

    private SourceTestSupport() {
    }
}
```

- [ ] **Step 8: GameTests de Source (falham antes dos Steps 2 a 4, passam depois)**

Em `SourceGameTests`, acrescente os imports de `RouterBlock`, `RouterBlockEntity`, `RouterTier`, `NetworkManager`, `NetworkSavedData`, `PortMode`, `ResourceType`, `ModBlocks`, `BlockPos`, `Direction`, `UUID`, e:

```java
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 0);
    private static final BlockPos C = new BlockPos(0, 1, 2);

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    /** Source Jar em {@code pos}, com um roteador do tier em cima, na rede, no modo dado. */
    private static RouterBlockEntity jar(GameTestHelper helper, BlockPos pos, RouterTier tier, UUID network,
            PortMode mode) {
        helper.setBlock(pos, BuiltInRegistries.BLOCK.get(JAR));
        BlockPos routerPos = pos.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, tier));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
        router.setNetworkId(network);
        router.setMode(ResourceType.SOURCE, Direction.UP, mode);
        return router;
    }

    private static int amount(GameTestHelper helper, BlockPos pos) {
        helper.assertTrue(SourceTestSupport.hasSource(helper.getLevel(), helper.absolutePos(pos)),
                "Source Jar sem capability em " + pos.toShortString());
        return SourceTestSupport.amount(helper.getLevel(), helper.absolutePos(pos));
    }

    private static void set(GameTestHelper helper, BlockPos pos, int amount) {
        SourceTestSupport.set(helper.getLevel(), helper.absolutePos(pos), amount);
    }

    private static boolean registered(RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            if (!NetworkManager.get().contains(router)) {
                return false;
            }
        }
        return true;
    }

    /** Uma jarra para outra pela rede, sem limite de vazão (Ultimate). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sourceMovesBetweenJars(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source");
        RouterBlockEntity from = jar(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity to = jar(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, to), "roteadores não registrados"))
                .thenExecute(() -> set(helper, A, 5_000))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(amount(helper, B), 5_000, "Source no destino");
                    helper.assertValueEqual(amount(helper, A), 0, "Source na origem");
                })
                .thenSucceed();
    }

    /** Prioridade maior enche primeiro; a outra jarra não recebe nada enquanto a primeira aceita. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void higherPriorityJarFillsFirst(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-prioridade");
        RouterBlockEntity from = jar(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity high = jar(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);
        RouterBlockEntity low = jar(helper, C, RouterTier.ULTIMATE, network, PortMode.INSERT);
        high.setPriority(ResourceType.SOURCE, Direction.UP, 5);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, high, low), "roteadores não registrados"))
                .thenExecute(() -> set(helper, A, 3_000))
                .thenWaitUntil(() -> helper.assertValueEqual(amount(helper, B), 3_000, "Source na prioridade 5"))
                .thenIdle(10)
                .thenExecute(() -> helper.assertValueEqual(amount(helper, C), 0, "Source na prioridade 0"))
                .thenSucceed();
    }

    /** Mesma prioridade: a Source se divide por igual entre as jarras. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void equalPrioritySplitsEvenly(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-divisao");
        RouterBlockEntity from = jar(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity first = jar(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);
        RouterBlockEntity second = jar(helper, C, RouterTier.ULTIMATE, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, first, second), "roteadores não registrados"))
                .thenExecute(() -> set(helper, A, 4_000))
                .thenWaitUntil(() -> helper.assertValueEqual(amount(helper, A), 0, "Source na origem"))
                .thenExecute(() -> {
                    helper.assertValueEqual(amount(helper, B), 2_000, "primeira jarra");
                    helper.assertValueEqual(amount(helper, C), 2_000, "segunda jarra");
                })
                .thenSucceed();
    }

    /** Destino cheio: nada sai da origem; esvaziado, o destino acorda e recebe. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fullJarSleepsAndWakes(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-cheia");
        RouterBlockEntity from = jar(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity to = jar(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, to), "roteadores não registrados"))
                .thenExecute(() -> {
                    set(helper, B, SourceTestSupport.capacity(helper.getLevel(), helper.absolutePos(B)));
                    set(helper, A, 1_000);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertValueEqual(amount(helper, A), 1_000, "saiu Source para uma jarra cheia");
                    set(helper, B, 0);
                })
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(amount(helper, B), 1_000, "Source depois de esvaziar o destino");
                    helper.assertValueEqual(amount(helper, A), 0, "Source na origem");
                })
                .thenSucceed();
    }

    /** Básico: 1.000 Source/s (o balde começa cheio com um segundo); nada se perde no caminho. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void basicTierLimitsSourceRate(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-source-vazao");
        RouterBlockEntity from = jar(helper, A, RouterTier.BASIC, network, PortMode.EXTRACT);
        RouterBlockEntity to = jar(helper, B, RouterTier.BASIC, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(registered(from, to), "roteadores não registrados"))
                .thenExecute(() -> set(helper, A, 10_000))
                .thenWaitUntil(() -> helper.assertTrue(amount(helper, B) > 0, "nada chegou"))
                .thenIdle(20)
                .thenExecute(() -> {
                    int moved = amount(helper, B);
                    // Um segundo de balde (1.000) + 20 ticks a 50 por tick (1.000), com folga de um tick.
                    helper.assertTrue(moved <= 2_050, "passou do limite do tier: " + moved);
                    helper.assertValueEqual(amount(helper, A) + moved, 10_000, "Source perdida ou criada");
                })
                .thenSucceed();
    }
```

Mude o javadoc de `arsNouveauIsLoaded` para também conferir que a jarra tem a capability: depois das duas asserções, acrescente

```java
        helper.setBlock(A, BuiltInRegistries.BLOCK.get(JAR));
        helper.assertTrue(SourceTestSupport.hasSource(helper.getLevel(), helper.absolutePos(A)),
                "Source Jar sem a capability ars_nouveau:source");
```

Se `helper.setBlock(BlockPos, Block)` não aceitar o bloco (assinatura), use `helper.setBlock(pos, block.defaultBlockState())`.

- [ ] **Step 9: Rodar**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`
Expected: JUnit passa; as três runs chegam a "required tests passed"; a de Source com 6 testes.

Se um teste de Source falhar por tempo (o roteador dormindo antes de a jarra ganhar Source), **não** aumente prazos às cegas: confira no log o motivo e o `MAX_SLEEP_TICKS` do `NetworkManager`; o prazo do teste precisa cobrir um sono inteiro mais a transferência.

- [ ] **Step 10: Commit**

```bash
git add -A src/main/java src/test/java src/main/resources/assets/wirelessautomate/lang
git commit -m "Etapa 1: Source do Ars Nouveau no registro, no motor e no roteador, com GameTests"
```

---

### Task 4: Cartões de Upgrade e e2e com cinco tipos

**Files:**
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/item/TierCoreItem.java`
- Modify: `src/main/resources/assets/wirelessautomate/lang/en_us.json`, `pt_br.json`
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/client/DevEndToEnd.java`

**Interfaces:**
- Consumes: `ResourceType.SOURCE`, `Sources.LOADED` (Task 3); `RouterScreen.tabMode()`, `GuiText.clipCount()`, os passos `clipCheck(...)` e `capture(...)` do `DevEndToEnd` (etapa 0).

- [ ] **Step 1: Linha de Source no tooltip dos Cartões de Upgrade**

Em `TierCoreItem`:
- no enum `Stat`, acrescente `SOURCE(ResourceType.SOURCE)` depois de `ENERGY(ResourceType.ENERGY)` (troque o `;` de lugar);
- em `appendHoverText`, depois da linha de energia:

```java
        if (Sources.LOADED) {
            tooltip.add(line("source", rate(from, Stat.SOURCE), rate(tier, Stat.SOURCE)));
        }
```

(import `io.github.matheusanbs.wirelessautomate.network.Sources`).

Traduções, junto de `item.wirelessautomate.tier_core.energy`:

| Chave | en_us | pt_br |
| --- | --- | --- |
| `item.wirelessautomate.tier_core.source` | `  Source/s: %s → %s` | `  Source/s: %s → %s` |

- [ ] **Step 2: e2e com o Ars no `runClient`**

O `runClient` agora tem o Mekanism e o Ars: 5 tipos. Ajuste o `DevEndToEnd`:

1. **Abas no tamanho mínimo** (passo "abas no tamanho mínimo", hoje espera `TabLayout.Mode.ACTIVE_NAME` com 4 tipos) e **roteador maior** (espera `FULL` em 420 × 300): rode o e2e uma vez, veja nas capturas `1b-roteador-abas` e `1c-roteador-grande` e no log o modo que o `TabLayout` escolheu com 5 tipos, e troque as expectativas pelos modos observados, com o comentário "com os 5 tipos do runClient (Mekanism e Ars)". Se em 420 × 300 o modo não for `FULL`, tudo bem: a expectativa passa a ser a observada; o que não pode é texto cortado sem tooltip (o `clipCheck` cobre).
2. **Aba Source no roteador:** depois do passo "de volta à aba Itens", acrescente:

```java
        if (LoadedTypes.contains(ResourceType.SOURCE)) {
            list.add(new Step("aba Source", STEP_TIMEOUT_MS,
                    () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.router.type.source")), "aba Source")),
                    () -> routerScreen().getMenu().selectedType() == ResourceType.SOURCE,
                    () -> "aba na tela " + routerScreen().getMenu().selectedType()));
            list.add(clipCheck("aba Source"));
            list.add(capture("1d-roteador-aba-source"));
            list.add(new Step("de volta à aba Itens depois da Source", STEP_TIMEOUT_MS,
                    () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.router.type.item")), "aba Itens")),
                    () -> routerScreen().getMenu().selectedType() == ResourceType.ITEM,
                    () -> "aba na tela " + routerScreen().getMenu().selectedType()));
        }
```

(se a aba Source estiver como só ícone, o `byMessage` do nome pode não achar o botão: nesse caso use o mesmo jeito que o e2e já usa para clicar numa aba só com ícone, se houver; senão, chame `routerScreen().getMenu()` pelo método que a tela usa ao trocar de aba, com o mesmo efeito de um clique. Explique a escolha no relatório.)
3. **Vinculador "só a aba Fluidos":** acrescente `ResourceType.SOURCE` à lista de tipos desmarcados.
4. **Vinculador em português, "pt: três abas":** o passo desmarca Químicos a partir de Todos e espera 3 abas. Com 5 tipos, desmarque Químicos **e** Source (dois cliques, cada um só se o tipo estiver em `linkerScreen().getMenu().snapshot().available()`), e mantenha a espera de 3 abas e o comentário sobre o título mais longo.
A `GUIDE_PAGES` não muda nesta tarefa: a página `source` nasce na Task 5, que a acrescenta.

Qualquer outro passo que falhe por contar 4 tipos fixos: troque o número por `LoadedTypes.LIST.size()` (ou pela lista), preservando a intenção do passo.

- [ ] **Step 3: Rodar**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`, depois `WA_E2E="$PWD/run/e2e" ./gradlew runClient`
Expected: tudo passa; `run/e2e/result.txt` = `OK`. Abra as capturas `1b-roteador-abas`, `1c-roteador-grande`, `1d-roteador-aba-source`, a das Estatísticas do Tablet e `8c-vinculador-pt` e confira: 5 abas/cartões/chips, Source em rosa, nenhum texto vazando, "Sem filtro: Source não tem tipos" na aba Source.

- [ ] **Step 4: Commit**

```bash
git add -A src/main/java src/main/resources/assets/wirelessautomate/lang
git commit -m "Etapa 1: Source no tooltip dos Cartões de Upgrade e e2e com cinco tipos"
```

---

### Task 5: Guia e documentação

**Files:**
- Modify: `scripts/guide/gerar_guia.py` (e as páginas geradas em `src/main/resources/assets/wirelessautomate/guides/wirelessautomate/guide/` e `_pt_br/`)
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/client/DevEndToEnd.java` (`GUIDE_PAGES`: acrescente `"source"` depois de `"chemicals"`)
- Modify: `CLAUDE.md`, `docs/especificacao.md`, `docs/progresso.md`

- [ ] **Step 1: Página `source` no guia**

No `gerar_guia.py`, depois da página `chemicals.md`, acrescente `page('source.md', ...)` com `front('Source (Ars Nouveau)', 'minecraft:amethyst_shard', 16)` nos dois idiomas, e passe `troubleshooting.md` para a posição 17 e `recipes.md` para 18. Conteúdo (pt; o en é a tradução direta, no mesmo formato da página `chemicals`):

```markdown
# Source do Ars Nouveau

Com o **Ars Nouveau** instalado, o roteador ganha a aba **Source**. Sem o Ars, a aba não aparece e
o resto do mod funciona igual.

## O que funciona

| Recurso | Source |
| --- | --- |
| Modo, prioridade e redstone por face | Sim, como nas outras abas. |
| Rede própria na aba | Sim. |
| Vinculador e Configurador | Sim: o chip **Source** e o atalho na roda do mouse. |
| Vazão | Por tier: Básico 1.000/s, Avançado 16.000/s, Elite 256.000/s, Ultimate sem limite. |
| Filtro e Cartão de Filtro | Não: a Source não tem tipos, como a energia. |

## Exemplo: Source dos Sourcelinks até o Enchanting Apparatus

| Passo | O que fazer |
| --- | --- |
| **1** | Perto dos Sourcelinks, uma **Source Jar** com um roteador em cima, na aba **Source**, em **Extrair**. |
| **2** | Perto do Enchanting Apparatus (ou de qualquer máquina do Ars), outra Source Jar com um roteador em **Inserir**, na mesma rede. |
| **3** | Os Sourcelinks enchem a primeira jarra; o roteador leva a Source para a segunda, e a máquina tira dela. |

O roteador também liga direto nos Relays do Ars e na Imbuement Chamber.
```

Rode `python scripts/guide/gerar_guia.py` (gera `source.md` nos dois idiomas).

- [ ] **Step 2: Menções nas outras páginas**

No `gerar_guia.py`, nos dois idiomas:
- `index.md`: a frase de abertura ("itens, fluidos, energia e químicos do Mekanism") ganha "e a Source do Ars Nouveau", e a tabela de páginas ganha uma linha para [Source](source.md) junto da de químicos, se a de químicos estiver lá.
- `router.md`: a linha "**Tipos**" fica "Itens, Fluidos, Energia e, com o Mekanism, Químicos; com o Ars Nouveau, Source." (e o equivalente em inglês); a linha "**1. Pick the type**"/"1. Escolha o tipo" lista Source.
- Tabelas de tier em `router.md` e `upgrade-cards.md`: nova coluna **Source/s** (`1.000`, `16.000`, `256.000`, `sem limite`/`no limit`), com uma nota "Source só com o Ars Nouveau".
- `linker.md` e `configurator.md`: nas linhas de abas e de Shift + roda, "Químicos (com o Mekanism)" vira "Químicos (com o Mekanism), Source (com o Ars Nouveau)".

Rode o script de novo e confira o `git diff` das páginas geradas (só as mudanças acima).

- [ ] **Step 3: Documentação**

- `CLAUDE.md`:
  - no mapa, uma linha nova: `` `compat/arsnouveau/`, `network/Sources.java` `` → "Source do Ars Nouveau (só a API dele, em `ArsSources`: a capability `ars_nouveau:source` e o `ScalarAccess` da Source). O resto do mod passa pela ponte `Sources` (`Sources.LOADED`), sem tipos do Ars";
  - na linha do `ResourceType`, "(Itens, Fluidos, Energia, Químicos, Source)" e "`LoadedTypes` diz quais estão carregados (Químicos só com o Mekanism, Source só com o Ars Nouveau)";
  - na linha dos GameTests, `SourceGameTests` (6 testes, Source Jars de verdade) no namespace `wirelessautomate_source`, só na run `gameTestServerSource`;
  - em "Comandos", `./gradlew runGameTestServerSource # GameTests de Source, num servidor com o Ars Nouveau (e GeckoLib e Curios) na pasta mods`, e o `runClient` com o Ars na lista;
  - em "Antes de commitar", o comando com as três runs;
  - em "Convenções e armadilhas", logo depois da regra do Mekanism: "**Ars Nouveau é opcional:** tipos da API dele só em `compat/arsnouveau` e `gametest/SourceTestSupport`, e nunca na assinatura de um método de classe `@EventBusSubscriber` ou `@GameTestHolder` (mesmo motivo do Mekanism)."
- `docs/especificacao.md`: na seção dos tipos de recurso e na tabela de tiers, a Source (vazão por tier, sem filtro e sem cartões, opcional com o Ars); na seção de integrações, o Ars Nouveau.
- `docs/progresso.md`: a etapa 1 na tabela de estado como pronta; próximo passo: "Etapa 2: Tanque de Source Wireless (plano a escrever)"; uma linha no histórico com a data 8/10/2026.

- [ ] **Step 4: Rodar**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`, depois `WA_E2E="$PWD/run/e2e" ./gradlew runClient`
Expected: tudo passa; `run/e2e/result.txt` = `OK`; capturas `guia-source.png` e `guia-source-pt.png` geradas e legíveis.

- [ ] **Step 5: Commit**

```bash
git add -A scripts/guide src/main/resources/assets/wirelessautomate/guides src/main/java/io/github/matheusanbs/wirelessautomate/client/DevEndToEnd.java CLAUDE.md docs/especificacao.md docs/progresso.md
git commit -m "Etapa 1: página Source no guia e documentação"
```
