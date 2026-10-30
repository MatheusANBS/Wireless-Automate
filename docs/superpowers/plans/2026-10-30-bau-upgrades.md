# Upgrades do Baú (Mesa de Trabalho e Compactação): plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use `superpowers:subagent-driven-development` (recomendado) ou `superpowers:executing-plans` para executar este plano tarefa a tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** dois slots de upgrade no Baú, com o Upgrade de Mesa de Trabalho (mesa 3×3 que se repõe pelo estoque) e o Upgrade de Compactação (cadeias achadas pelas receitas, convertidas sozinhas no fim do tick, com modo global e alvo por cadeia).

**Arquitetura:** a lógica pura (a conta da conversão e a montagem das cadeias) fica em `storage/upgrade/` com JUnit. O índice de cadeias (`CompactingChains`) é montado no servidor a cada carga de receitas. O Baú guarda os dois upgrades e a grade e, com a Compactação, anota as cadeias que receberam itens e as converte no `compactContents` do fim do tick (o mesmo ponto em que o `StorageNeighbors` avisa os vizinhos). O menu genérico `StorageListMenu` ganha os slots do Baú por um ajudante (`ChestUpgradeSlots`); a tela ganha a coluna e os painéis por classes novas em `client/storage/upgrade/`.

**Tecnologias:** NeoForge 1.21.1 (Java 21), GameTests do NeoForge, JUnit 5, JEI (opcional, já no projeto).

Spec: `docs/superpowers/specs/2026-10-30-bau-upgrades-design.md`. Mockup aprovado: `docs/preview/bau-upgrades.html`.

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas. Todo texto variável de tela passa por `GuiText` (o e2e confere `GuiText.clipCount()==0`).
- Ids novos: itens `wirelessautomate:crafting_upgrade` e `wirelessautomate:compacting_upgrade`; NBT do Baú `upgrades` e `craft_grid`; componentes `storage_upgrades`, `storage_craft_grid` (do drop do Baú) e `compacting_rules` (do item do upgrade).
- Nomes: "Upgrade de Mesa de Trabalho" / "Crafting Upgrade"; "Upgrade de Compactação" / "Compacting Upgrade".
- Chaves existentes de NBT, componentes e config **não mudam**.
- `ModPayloads.VERSION` passa de `"38"` para `"39"` (na Tarefa 6, a primeira que muda payload).
- Só o Baú tem upgrades. Tanques, Bateria e Tanque de Source não mudam de comportamento nem de tela.
- Nenhum tipo do AllTheCompressed nem de outro mod: as cadeias vêm só do `RecipeManager`. O AllTheCompressed já está no `runClient` (`clientMods`, `allthecompressed_file=7361502`), só para teste à mão; os GameTests usam cadeias vanilla.
- Classes de tela só em `client/`; nada em código comum as referencia.
- Tamanho (skill `wa-modular`): tela/componente até 500 linhas, servidor até 600 (limite duro 1000). A `StorageListScreen` (538) e o `StorageListMenu` (627) só ganham ganchos curtos; o resto vai para as classes novas desta lista.
- **Performance (skill `wa-performance`, leis que esta etapa toca):**
  - Parado custa zero: nada de tick no Baú. Sem Upgrade de Compactação, a inserção paga só um teste de campo nulo.
  - Caminho quente sem alocação: o gancho de inserção não aloca (nada de lambda que captura, `Optional`, iterador boxed). O conjunto de cadeias sujas é fastutil, criado sob demanda.
  - Nenhuma busca repetida: a receita da grade só é procurada quando a grade muda; o índice de cadeias é montado uma vez por carga de receitas, nunca por inserção.
  - Trabalho proporcional à mudança: só as cadeias sujas são convertidas, O(formas) cada.
  - Sem picos no jogo: o índice é montado no `ServerStartedEvent` e na recarga (`OnDatapackSyncEvent` sem jogador), com o tempo no log.
  - O cliente só recebe o estado da Compactação com o painel aberto, e só quando muda.
  - Por quadro, só desenhar: linhas do painel, textos e medidas montados quando o estado chega, não no `render`.
- Todo GameTest novo com `timeoutTicks` de no máximo 300; cada teste cria a própria rede quando usa roteador; teste pertinência, não contagem global.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams` (Git Bash, na raiz). Nas tarefas de tela: também `./scripts/e2e.sh --only armazenamento --capture armazenamento`, com `run/e2e/result.txt` dizendo `OK`, e olhar as capturas. **Compile antes (`./gradlew compileJava`); se aparecer `BUILD FAILED`, pare e conserte, nunca espere o resultado de um jogo que não abriu.** Um Gradle ou jogo por vez (~3 GB cada).
- Antes de cada commit, nos arquivos mudados: `python -I .claude/skills/wa-comentarios/scripts/varrer.py`, `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` e `python -I .claude/skills/wa-modular/scripts/medir.py`; resolva ou justifique cada candidato no relatório da tarefa.
- Commits: sem linhas de atribuição (`Co-Authored-By`, `Claude-Session`), autor do dono. Sem push.
- Não mexa em `run/options.txt`.

## Mapa de arquivos

Pacote base `src/main/java/io/github/matheusanbs/wirelessautomate/`; testes JUnit em `src/test/java/io/github/matheusanbs/wirelessautomate/`.

| Arquivo | Papel |
| --- | --- |
| `storage/upgrade/CompactingMath.java` (novo, puro) | A conta da conversão de uma cadeia: contagens, fatores, alvo e espaço livre → contagens novas |
| `storage/upgrade/ChainGraph.java` (novo, puro) | Monta as cadeias a partir dos pares (menor, maior, fator), com preferência 3×3, ordem estável e corte de ciclos |
| `storage/upgrade/CompactingChains.java` (novo) | O índice do servidor: lê o `RecipeManager`, confere ida e volta, guarda item → (cadeia, nível) |
| `storage/upgrade/CompactingChain.java` (novo) | Uma cadeia: `Item[] forms`, `int[] factors` (fator entre `forms[i-1]` e `forms[i]`; `factors[0]` = 1) |
| `storage/upgrade/CompactingMode.java` (novo) | `OFF`, `COMPRESS`, `DECOMPRESS` |
| `storage/upgrade/CompactingRules.java` (novo) | Record do componente `compacting_rules`: modo global e mapa base → alvo, com codec e stream codec |
| `storage/upgrade/ChestCompacting.java` (novo) | O estado da Compactação de um Baú: cadeias sujas, gancho da inserção, conversão no fim do tick |
| `storage/upgrade/ChestUpgrades.java` (novo) | Os 2 slots de upgrade e a grade 3×3 do Baú (container, NBT, componentes do drop, regras de aceite) |
| `item/ChestUpgradeItem.java` (novo) | Item dos dois upgrades (tooltip; o tipo pelo enum `ChestUpgradeItem.Kind`) |
| `storage/KeyedStorage.java`, `storage/ItemStorage.java` (mudar) | Gancho `inserted(K)` e inserção interna sem admissão |
| `storage/StorageChestBlockEntity.java` (mudar) | Dono do `ChestUpgrades` e do `ChestCompacting`; salva, carrega, drop |
| `menu/ChestUpgradeSlots.java` (novo) | Os slots do Baú no menu: 2 de upgrade, 9 da grade, 1 de resultado; Shift + clique; repor a grade; devolver |
| `menu/ChestCraftGrid.java` (novo) | `CraftingContainer` sobre a grade do block entity (servidor) ou um container local (cliente) |
| `menu/StorageListMenu.java` (mudar) | Ganchos: cria o `ChestUpgradeSlots` quando o tipo é Baú; `quickMoveStack`, `slotsChanged`, `clicked`, `broadcastChanges` delegam |
| `packet/CompactingStatePayload.java`, `packet/CompactingRulePayload.java`, `packet/ChestCraftFillPayload.java` (novos) | Estado do painel (servidor → cliente), regra (cliente → servidor), preencher a grade pelo JEI |
| `packet/StorageActionPayload.java`, `packet/ModPayloads.java` (mudar) | Ação `RETURN_GRID`; registro dos payloads; `VERSION` 39 |
| `client/storage/upgrade/UpgradePanels.java` (novo) | Anfitrião dos painéis na tela: qual está aberto, medidas, posição dos slots, `hasClickedOutside` |
| `client/storage/upgrade/UpgradeColumn.java` (novo) | Desenho da coluna dos 2 slots e a escolha do painel |
| `client/storage/upgrade/CraftingPanel.java` (novo) | Desenho do painel da Mesa e o botão Devolver |
| `client/storage/upgrade/CompactingPanel.java` (novo) | Modo global, lista das cadeias com rolagem, linhas montadas quando o estado chega |
| `client/storage/upgrade/ChainPicker.java` (novo) | A lista suspensa com todas as formas de uma cadeia |
| `client/storage/upgrade/CompactingClientState.java` (novo) | O último `CompactingStatePayload` do menu aberto |
| `client/storage/StorageListScreen.java` (mudar) | Ganchos curtos para o `UpgradePanels` |
| `compat/jei/ChestCraftTransfer.java` (novo), `compat/jei/WirelessAutomateJeiPlugin.java` (mudar) | O "+" do JEI na Mesa do Baú |
| `registry/ModItems.java`, `registry/ModDataComponents.java`, `registry/ModCreativeTabs.java`, `WirelessAutomate.java` (mudar) | Registros, aba criativa, eventos do índice |
| `src/main/resources/data/wirelessautomate/recipe/crafting_upgrade.json`, `compacting_upgrade.json` (novos) | Receitas |
| `scripts/textures/gerar_texturas.py` (mudar) e os PNGs e modelos que ele gera | Texturas e modelos dos dois itens |
| `src/main/resources/assets/wirelessautomate/lang/en_us.json`, `pt_br.json` (mudar) | Textos |
| `gametest/ChestUpgradeGameTests.java`, `gametest/ChestCompactingGameTests.java`, `gametest/ChestCraftingGameTests.java` (novos) | GameTests |
| `client/dev/E2eStorageSteps.java` (mudar) | e2e da suíte `armazenamento`: os dois painéis |
| `scripts/guide/gerar_guia.py` (mudar) | Página do Baú: os upgrades (pt e en), só "como operar" |
| `docs/mapa/armazenamento.md`, `docs/especificacao.md`, `docs/progresso.md` (mudar) | Documentação |

---

### Tarefa 1: A conta da conversão e a montagem das cadeias (lógica pura)

**Custo:** nenhum no jogo (classes puras). Prova: JUnit.

**Files:**
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/storage/upgrade/CompactingMath.java`
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/storage/upgrade/ChainGraph.java`
- Test: `src/test/java/io/github/matheusanbs/wirelessautomate/storage/upgrade/CompactingMathTest.java`
- Test: `src/test/java/io/github/matheusanbs/wirelessautomate/storage/upgrade/ChainGraphTest.java`

**Interfaces:**
- Produces: `CompactingMath.convert(long[] counts, int[] factors, int target, long room)` devolve `long[]` novo (não altera a entrada); `factors[i]` é quantos de `i-1` fazem 1 de `i` (`factors[0]` ignorado); `room` é o espaço livre do Baú (`Long.MAX_VALUE` = sem limite).
- Produces: `CompactingMath.equivalent(long[] counts, int[] factors, int level)`: quanto a cadeia vale em itens da forma `level` (as formas de cima multiplicadas, as de baixo somadas e divididas para cima, com saturação em `Long.MAX_VALUE`). É a contagem do limite de estoque com a Compactação.
- Produces: `ChainGraph<T>` com `ChainGraph(Comparator<T> order)`, `void link(T small, T big, int factor)` e `List<Chain<T>> chains()`; `record Chain<T>(List<T> forms, int[] factors)`.

- [ ] **Passo 1: Teste da conta (falha)**

```java
package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class CompactingMathTest {
    private static final int[] NINE = {1, 9, 9, 9};

    @Test
    void compactaComSobraNaFormaDeBaixo() {
        // 10 pedregulhos com alvo 1x: 1 de 1x e sobra 1
        assertArrayEquals(new long[] {1, 1, 0, 0}, CompactingMath.convert(new long[] {10, 0, 0, 0}, NINE, 1, Long.MAX_VALUE));
    }

    @Test
    void compactaAteOAlvoEmCascata() {
        // 100 lingotes (nível 1) com alvo no topo (3): 100 = 1×81 + 2×9 + 1
        assertArrayEquals(new long[] {0, 1, 2, 1}, CompactingMath.convert(new long[] {0, 100, 0, 0}, NINE, 3, Long.MAX_VALUE));
    }

    @Test
    void descompactaTudoQuandoCabe() {
        // alvo 1: 1 de nível 2 vale 9 de nível 1 e 1 de nível 3 vale 81
        assertArrayEquals(new long[] {0, 9 + 81, 0, 0}, CompactingMath.convert(new long[] {0, 0, 1, 1}, NINE, 1, Long.MAX_VALUE));
    }

    @Test
    void descompactaSoOQueCabeAFormaMaisBarataPrimeiro() {
        // alvo 0; 2 de nível 1 (cada um rende 9, custa 8 de espaço) e 1 de nível 2 (rende 81, custa 80); espaço 20
        // cabem os 2 de nível 1 (16), o de nível 2 não (80 > 4)
        assertArrayEquals(new long[] {18, 0, 1, 0}, CompactingMath.convert(new long[] {0, 2, 1, 0}, NINE, 0, 20));
    }

    @Test
    void compactarLiberaEspacoAntesDeDescompactar() {
        // alvo 1: 18 de nível 0 viram 2 de nível 1 (libera 16); 1 de nível 2 vira 9 de nível 1 (custa 8)
        assertArrayEquals(new long[] {0, 11, 0, 0}, CompactingMath.convert(new long[] {18, 0, 1, 0}, NINE, 1, 0));
    }

    @Test
    void naoEstouraOLong() {
        long[] counts = {0, 0, 0, Long.MAX_VALUE / 2};
        long[] out = CompactingMath.convert(counts, NINE, 0, Long.MAX_VALUE);
        // nada pode ficar negativo e o total em itens não pode diminuir para a descompactação
        for (long c : out) {
            org.junit.jupiter.api.Assertions.assertTrue(c >= 0);
        }
    }

    @Test
    void alvoNoNivelAtualNaoMexe() {
        assertArrayEquals(new long[] {3, 5, 0, 0}, CompactingMath.convert(new long[] {3, 5, 0, 0}, NINE, 1, 0));
    }

    @Test
    void equivalenteContaACadeiaNaUnidadeDaForma() {
        // 1 lingote + 11 blocos = 100 lingotes; as 10 pepitas valem mais 1 lingote (a sobra de 1 pepita não conta)
        org.junit.jupiter.api.Assertions.assertEquals(101, CompactingMath.equivalent(new long[] {10, 1, 11}, new int[] {1, 9, 9}, 1));
        org.junit.jupiter.api.Assertions.assertEquals(Long.MAX_VALUE,
                CompactingMath.equivalent(new long[] {0, 0, 0, Long.MAX_VALUE}, NINE, 0));
    }
}
```

O `equivalent` em `CompactingMath`:

```java
    /**
     * Quanto a cadeia vale em itens da forma {@code level}: as formas de cima multiplicadas pelo fator até ela,
     * e as de baixo levadas para cima degrau a degrau (a sobra que não fecha um fator não conta). Satura em
     * {@code Long.MAX_VALUE}. É a contagem do limite de estoque num Baú com a Compactação.
     */
    public static long equivalent(long[] counts, int[] factors, int level) {
        long carry = 0;
        for (int i = 0; i < level; i++) {
            carry = (counts[i] + carry) / factors[i + 1];
        }
        long total = saturatedAdd(counts[level], carry);
        long ratio = 1;
        for (int i = level + 1; i < counts.length && total != Long.MAX_VALUE; i++) {
            ratio = saturatedMul(ratio, factors[i]);
            total = saturatedAdd(total, saturatedMul(counts[i], ratio));
        }
        return total;
    }
```

(`counts[i] + carry` não estoura: `carry` é no máximo `counts[i-1] / 9`, e cada `counts[i]` é um total do Baú, bem abaixo de `Long.MAX_VALUE / 2` na prática; se quiser blindar, use `saturatedAdd` ali também.)

Observação sobre `alvoNoNivelAtualNaoMexe`: 3 de nível 0 não fecham 9, então ficam; nada acima do alvo.

- [ ] **Passo 2: Rodar e ver falhar**

Run: `./gradlew test --tests "*CompactingMathTest"`
Expected: FAIL de compilação (`CompactingMath` não existe).

- [ ] **Passo 3: Implementar `CompactingMath`**

```java
package io.github.matheusanbs.wirelessautomate.storage.upgrade;

/**
 * A conta da Compactação de uma cadeia, degrau a degrau: abaixo do alvo, cada forma passa para a de cima o
 * que fecha o fator (a sobra fica); acima do alvo, as formas descem para o alvo, a mais barata primeiro,
 * até onde o espaço livre deixa. Nunca soma tudo em unidades base: 8,6 bilhões de itens × 9^11 estouraria o
 * {@code long}. Puro, com JUnit.
 */
public final class CompactingMath {
    /**
     * @param counts quanto há de cada forma, da base para cima (não é alterado)
     * @param factors {@code factors[i]} = quantos de {@code i-1} fazem 1 de {@code i}; {@code factors[0]} é ignorado
     * @param target o nível alvo
     * @param room o espaço livre do Baú, em itens ({@code Long.MAX_VALUE} = sem limite)
     * @return as contagens novas
     */
    public static long[] convert(long[] counts, int[] factors, int target, long room) {
        long[] out = counts.clone();
        long free = room;
        // 1. Abaixo do alvo: só diminui o total, então libera espaço para o passo 2.
        for (int i = 0; i < target; i++) {
            long carry = out[i] / factors[i + 1];
            if (carry > 0) {
                out[i] -= carry * factors[i + 1];
                out[i + 1] += carry;
                free = saturatedAdd(free, carry * (factors[i + 1] - 1));
            }
        }
        // 2. Acima do alvo: a forma mais próxima do alvo primeiro (a que gasta menos espaço por item).
        long ratio = 1;
        for (int i = target + 1; i < out.length && ratio != Long.MAX_VALUE; i++) {
            ratio = saturatedMul(ratio, factors[i]);
            if (out[i] == 0 || ratio == Long.MAX_VALUE) {
                continue;
            }
            long perItem = ratio - 1;
            long byRoom = free == Long.MAX_VALUE ? Long.MAX_VALUE : free / perItem;
            long byLong = (Long.MAX_VALUE - out[target]) / ratio;
            long moved = Math.min(out[i], Math.min(byRoom, byLong));
            if (moved > 0) {
                out[i] -= moved;
                out[target] += moved * ratio;
                if (free != Long.MAX_VALUE) {
                    free -= moved * perItem;
                }
            }
        }
        return out;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    private static long saturatedMul(long a, long b) {
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        return high != 0 || low < 0 ? Long.MAX_VALUE : low;
    }

    private CompactingMath() {
    }
}
```

- [ ] **Passo 4: Rodar e ver passar**

Run: `./gradlew test --tests "*CompactingMathTest"`
Expected: PASS (8 testes).

- [ ] **Passo 5: Teste do grafo (falha)**

```java
package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChainGraphTest {
    private static ChainGraph<String> graph() {
        return new ChainGraph<>(Comparator.naturalOrder());
    }

    @Test
    void ligaParesEmCadeia() {
        ChainGraph<String> g = graph();
        g.link("ingot", "block", 9);
        g.link("nugget", "ingot", 9);
        g.link("block", "block_1x", 9);
        List<ChainGraph.Chain<String>> chains = g.chains();
        assertEquals(1, chains.size());
        assertEquals(List.of("nugget", "ingot", "block", "block_1x"), chains.get(0).forms());
        assertArrayEquals(new int[] {1, 9, 9, 9}, chains.get(0).factors());
    }

    @Test
    void preferePrimeiroO3x3() {
        ChainGraph<String> g = graph();
        g.link("a", "a_small_block", 4);
        g.link("a", "a_block", 9);
        assertEquals(List.of("a", "a_block"), g.chains().get(0).forms());
    }

    @Test
    void doisMaioresDoMesmoTamanhoUsaOPrimeiroNaOrdem() {
        ChainGraph<String> g = graph();
        g.link("x", "z_block", 9);
        g.link("x", "b_block", 9);
        assertEquals(List.of("x", "b_block"), g.chains().get(0).forms());
    }

    @Test
    void cortaCiclo() {
        ChainGraph<String> g = graph();
        g.link("a", "b", 9);
        g.link("b", "a", 9);
        for (ChainGraph.Chain<String> chain : g.chains()) {
            assertEquals(chain.forms().size(), chain.forms().stream().distinct().count());
        }
    }

    @Test
    void doisMenoresParaOMesmoMaiorFicaUmSo() {
        ChainGraph<String> g = graph();
        g.link("b_dust", "block", 9);
        g.link("a_ingot", "block", 9);
        List<ChainGraph.Chain<String>> chains = g.chains();
        assertEquals(1, chains.size());
        assertEquals(List.of("a_ingot", "block"), chains.get(0).forms());
    }
}
```

- [ ] **Passo 6: Rodar e ver falhar**

Run: `./gradlew test --tests "*ChainGraphTest"`
Expected: FAIL de compilação.

- [ ] **Passo 7: Implementar `ChainGraph`**

```java
package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Monta as cadeias de compactação a partir dos pares conferidos (menor, maior, fator 4 ou 9). Cada forma
 * sobe por uma ligação só: a 3×3 antes da 2×2 e, no empate, o maior primeiro na ordem dada. Cada maior aceita
 * um menor só (o primeiro na ordem). Ciclos são cortados. A ordem dada torna o resultado determinístico.
 * Puro, com JUnit.
 */
public final class ChainGraph<T> {
    public record Chain<T>(List<T> forms, int[] factors) {
    }

    private record Up<T>(T big, int factor) {
    }

    private final Comparator<T> order;
    private final Map<T, Up<T>> up = new HashMap<>();

    public ChainGraph(Comparator<T> order) {
        this.order = order;
    }

    public void link(T small, T big, int factor) {
        if (small.equals(big)) {
            return;
        }
        Up<T> current = up.get(small);
        if (current == null || factor > current.factor()
                || (factor == current.factor() && order.compare(big, current.big()) < 0)) {
            up.put(small, new Up<>(big, factor));
        }
    }

    public List<Chain<T>> chains() {
        // Cada maior fica com um menor só: o primeiro na ordem.
        Map<T, T> down = new HashMap<>();
        TreeSet<T> smalls = new TreeSet<>(order);
        smalls.addAll(up.keySet());
        for (T small : smalls) {
            down.putIfAbsent(up.get(small).big(), small);
        }
        List<Chain<T>> out = new ArrayList<>();
        Set<T> used = new HashSet<>();
        for (T start : smalls) {
            if (down.containsKey(start) && down.get(start) != null && !isCycleRoot(start, down)) {
                continue;
            }
            if (used.contains(start)) {
                continue;
            }
            List<T> forms = new ArrayList<>();
            List<Integer> factors = new ArrayList<>();
            forms.add(start);
            factors.add(1);
            used.add(start);
            T at = start;
            while (true) {
                Up<T> next = up.get(at);
                if (next == null || used.contains(next.big()) || !at.equals(down.get(next.big()))) {
                    break;
                }
                forms.add(next.big());
                factors.add(next.factor());
                used.add(next.big());
                at = next.big();
            }
            if (forms.size() >= 2) {
                int[] f = new int[factors.size()];
                for (int i = 0; i < f.length; i++) {
                    f[i] = factors.get(i);
                }
                out.add(new Chain<>(List.copyOf(forms), f));
            }
        }
        return out;
    }

    /** Num ciclo ninguém fica sem "de baixo": a raiz é o menor do ciclo na ordem. */
    private boolean isCycleRoot(T start, Map<T, T> down) {
        Set<T> seen = new HashSet<>();
        T at = start;
        while (at != null && seen.add(at)) {
            at = down.get(at);
        }
        if (at == null) {
            return false;
        }
        T min = at;
        T walk = down.get(at);
        while (walk != null && !walk.equals(at)) {
            if (order.compare(walk, min) < 0) {
                min = walk;
            }
            walk = down.get(walk);
        }
        return min.equals(start);
    }
}
```

- [ ] **Passo 8: Rodar e ver passar**

Run: `./gradlew test --tests "*ChainGraphTest" --tests "*CompactingMathTest"`
Expected: PASS. Se `cortaCiclo` ou `doisMenoresParaOMesmoMaiorFicaUmSo` falharem, corrija a implementação, não o teste.

- [ ] **Passo 9: Varreduras e commit**

```bash
python -I .claude/skills/wa-comentarios/scripts/varrer.py
git add src/main/java/io/github/matheusanbs/wirelessautomate/storage/upgrade src/test/java/io/github/matheusanbs/wirelessautomate/storage/upgrade
git commit -m "Baú: a conta da Compactação e a montagem das cadeias (lógica pura)"
```

---

### Tarefa 2: O índice de cadeias pelas receitas

**Custo:** uma montagem por carga de receitas (início do servidor e `/reload`), fora do jogo; consulta O(1) por item (`Reference2ObjectOpenHashMap<Item, Entry>`). Logar o tempo e o número de cadeias (`info`). Prova: GameTest e o tempo no log do runClient com o AllTheCompressed (anotar no relatório).

**Files:**
- Create: `storage/upgrade/CompactingChain.java`, `storage/upgrade/CompactingChains.java`
- Modify: `WirelessAutomate.java` (eventos)
- Create: `gametest/ChestCompactingGameTests.java` (os testes do índice; a Tarefa 4 acrescenta os do Baú)

**Interfaces:**
- Consumes: `ChainGraph` (Tarefa 1).
- Produces:
  - `public record CompactingChain(int id, Item[] forms, int[] factors, ItemStack[] keys)`, com `int levelOf(Item item)` (−1 se não está) e `Item base()`. `keys[i]` é `new ItemStack(forms[i])`, criada uma vez na montagem e nunca alterada (a chave de contagem no Baú, sem alocar por conversão).
  - `CompactingChains.get()` → a instância do servidor (estática, como o `NetworkManager`), com `@Nullable CompactingChain chainOf(Item item)`, `@Nullable CompactingChain byId(int id)`, `int size()`, `int generation()` (sobe a cada montagem: os ids das cadeias só valem dentro de uma geração), `static void rebuild(MinecraftServer server)`, `static void reset()`.
  - `chainOf` só para pilhas sem componentes além dos padrão: quem chama confere `stack.isComponentsPatchEmpty()`.

- [ ] **Passo 1: GameTests do índice (falham)**

Em `gametest/ChestCompactingGameTests.java` (com `@GameTestHolder(WirelessAutomate.MODID)` e `@PrefixGameTestTemplate(false)`, template `"empty"`):

```java
@GameTest(template = "empty", timeoutTicks = 40)
public static void indiceAchaPepitaLingoteBloco(GameTestHelper helper) {
    CompactingChain chain = CompactingChains.get().chainOf(Items.IRON_INGOT);
    helper.assertTrue(chain != null, "o lingote de ferro tem cadeia");
    helper.assertTrue(chain.levelOf(Items.IRON_NUGGET) == 0, "pepita na base");
    helper.assertTrue(chain.levelOf(Items.IRON_INGOT) == 1, "lingote no nível 1");
    helper.assertTrue(chain.levelOf(Items.IRON_BLOCK) == 2, "bloco no nível 2");
    helper.assertTrue(chain.factors()[1] == 9 && chain.factors()[2] == 9, "fatores 9");
    helper.succeed();
}

@GameTest(template = "empty", timeoutTicks = 40)
public static void indiceAchaCruEBlocoCru(GameTestHelper helper) {
    CompactingChain chain = CompactingChains.get().chainOf(Items.RAW_IRON);
    helper.assertTrue(chain != null && chain.levelOf(Items.RAW_IRON_BLOCK) == 1, "cru ↔ bloco cru");
    helper.assertTrue(chain.levelOf(Items.IRON_INGOT) < 0, "cru não vira lingote (fundir fica de fora)");
    helper.succeed();
}

@GameTest(template = "empty", timeoutTicks = 40)
public static void indiceIgnoraOQueNaoVolta(GameTestHelper helper) {
    helper.assertTrue(CompactingChains.get().chainOf(Items.WHITE_WOOL) == null, "lã não volta");
    helper.assertTrue(CompactingChains.get().chainOf(Items.SANDSTONE) == null, "arenito não volta");
    helper.assertTrue(CompactingChains.get().chainOf(Items.SNOW_BLOCK) == null, "neve não volta");
    helper.succeed();
}
```

(Confira antes, nas receitas vanilla 1.21.1, que a lã não tem volta para 4 fios e que a areia e a neve também não; se algum caso tiver volta, troque por outro item sem volta, como `Items.QUARTZ_BLOCK`, e anote no relatório.)

- [ ] **Passo 2: Rodar e ver falhar**

Run: `./gradlew compileJava` (FAIL: classes não existem).

- [ ] **Passo 3: Implementar `CompactingChain` e `CompactingChains`**

A montagem, em `rebuild(MinecraftServer server)`:

1. `RecipeManager manager = server.getRecipeManager(); ServerLevel level = server.overworld();`
2. **Candidatos de volta:** para cada `RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)`: ignore `holder.value().isSpecial()`; pegue `holder.value().getIngredients()` e conte os não vazios; com exatamente 1 não vazio, e `getResultItem(server.registryAccess())` com contagem 4 ou 9 e `isComponentsPatchEmpty()`, para cada `ItemStack big : ingrediente.getItems()` com `big.isComponentsPatchEmpty()`: candidato `(small = resultado.getItem(), big = big.getItem(), n = contagem)`.
3. **Conferência de verdade, nos dois sentidos**, com o `RecipeManager` (pega a receita que o jogador pegaria):
   - volta: `manager.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(1, 1, List.of(new ItemStack(big))), level)` dá `small` × `n`;
   - ida: `int side = n == 9 ? 3 : 2;` `CraftingInput.of(side, side, Collections.nCopies(n, new ItemStack(small)))` dá `big` × 1.
   Só então `graph.link(small, big, n)`. Guarde os candidatos num `HashSet` de pares para não conferir o mesmo par duas vezes.
4. `ChainGraph<Item>` com a ordem `Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item))`.
5. Para cada `Chain<Item>` devolvida, `new CompactingChain(id++, forms.toArray(Item[]::new), factors, keys)` (com `keys[i] = new ItemStack(forms[i])`) e, para cada forma, `index.put(item, chain)`.
6. Troque a instância de uma vez (`INSTANCE = new CompactingChains(index, byId)`), e logue: `WirelessAutomate.LOGGER.info("Compactação: {} cadeias de {} receitas em {} ms", ...)`.

Eventos em `WirelessAutomate`: no `onServerStarted` existente, `CompactingChains.rebuild(event.getServer())`; um `OnDatapackSyncEvent` novo que chama `rebuild` só quando `event.getPlayer() == null` (recarga); no `onServerStopped`, `CompactingChains.reset()` (instância vazia). Os GameTests rodam depois do `ServerStartedEvent`, então o índice já existe.

Comentário de classe de `CompactingChains` explicando a regra da ida e volta (copie a decisão 1 da spec).

- [ ] **Passo 4: Rodar e ver passar**

Run: `./gradlew build runGameTestServer`
Expected: os 3 testes novos passam; o log mostra a linha "Compactação: N cadeias".

- [ ] **Passo 5: Medir com o AllTheCompressed**

Run: `./gradlew runClient`, entre num mundo, procure "Compactação:" em `run/logs/latest.log`, feche o jogo. Anote no relatório o número de cadeias (esperado ≥ 200) e os ms. Se passar de 500 ms, pare e informe (o plano supõe que a montagem cabe na carga).

- [ ] **Passo 6: Varreduras e commit**

```bash
python -I .claude/skills/wa-comentarios/scripts/varrer.py
python -I .claude/skills/wa-performance/scripts/varrer_perf.py
git add -A src/main/java/io/github/matheusanbs/wirelessautomate
git commit -m "Baú: o índice das cadeias de compactação pelas receitas de mesa"
```

---

### Tarefa 3: Os itens dos upgrades e os slots do Baú

**Custo:** dois campos no block entity (o `ChestUpgrades`, com 11 pilhas). NBT `upgrades` e `craft_grid` só gravados quando não vazios. Nenhum tick. Prova: GameTest de salvar, quebrar e colocar.

**Files:**
- Create: `item/ChestUpgradeItem.java`, `storage/upgrade/ChestUpgrades.java`, `storage/upgrade/CompactingMode.java`, `storage/upgrade/CompactingRules.java`
- Modify: `registry/ModItems.java`, `registry/ModDataComponents.java`, `registry/ModCreativeTabs.java`, `storage/StorageChestBlockEntity.java`
- Create: `src/main/resources/data/wirelessautomate/recipe/crafting_upgrade.json`, `compacting_upgrade.json`
- Modify: `scripts/textures/gerar_texturas.py` (+ os PNGs e modelos gerados), `lang/en_us.json`, `lang/pt_br.json`
- Create: `gametest/ChestUpgradeGameTests.java`

**Interfaces:**
- Produces:
  - `ChestUpgradeItem` com `enum Kind { CRAFTING, COMPACTING }` e `Kind kind()`; itens `ModItems.CRAFTING_UPGRADE` e `ModItems.COMPACTING_UPGRADE`.
  - `enum CompactingMode { OFF, COMPRESS, DECOMPRESS }` (`StringRepresentable`, nomes `off`, `compress`, `decompress`).
  - `record CompactingRules(CompactingMode mode, Set<ResourceLocation> targets)` com `static final CompactingRules DEFAULT = new CompactingRules(CompactingMode.OFF, Set.of())` (o upgrade novo não muda nada até o jogador escolher), `CODEC`, `STREAM_CODEC`, `CompactingRules withMode(CompactingMode)`, `CompactingRules withTarget(CompactingChain chain, @Nullable Item target)` (tira do conjunto todas as formas da cadeia e põe a nova; nulo = volta ao global), `boolean hasOwn(CompactingChain chain)` e `int targetLevel(CompactingChain chain)` (a primeira forma da cadeia cujo id está no conjunto; sem nenhuma, o modo global; −1 = não mexe). Os alvos são ids em texto (`ResourceLocation`), não `Item`: um mod removido não pode fazer o componente falhar ao ler (isso apagaria o item do upgrade), e a regra continua achando a cadeia se uma forma nova entrar abaixo da base num `/reload`.
  - Componente `ModDataComponents.COMPACTING_RULES` (`DataComponentType<CompactingRules>`, persistente e sincronizado).
  - Componentes `STORAGE_UPGRADES` e `STORAGE_CRAFT_GRID` (`ItemContainerContents`).
  - `ChestUpgrades` (dentro do `StorageChestBlockEntity`): `ItemStack upgrade(int slot)` (0 ou 1), `void setUpgrade(int slot, ItemStack)`, `boolean accepts(int slot, ItemStack)` (só `ChestUpgradeItem`, e um tipo que ainda não está no outro slot), `boolean has(ChestUpgradeItem.Kind)`, `@Nullable ItemStack find(Kind)`, `NonNullList<ItemStack> grid()` (9), `void save(CompoundTag, HolderLookup.Provider)`, `void load(CompoundTag, HolderLookup.Provider)`, `void collect(DataComponentMap.Builder)`, `void apply(DataComponentInput)`; e um `Runnable onChange` que o block entity passa (o `setChanged` do Baú).
  - `StorageChestBlockEntity.upgrades()`.

- [ ] **Passo 1: GameTests (falham)**

Em `gametest/ChestUpgradeGameTests.java`:
- `aceitaUmDeCadaTipo`: Baú colocado, `accepts(0, crafting)` verdadeiro; com a Mesa no slot 0, `accepts(1, crafting)` falso e `accepts(1, compacting)` verdadeiro; `accepts(0, new ItemStack(Items.STONE))` falso.
- `upgradesEGradeViajamNoDrop`: põe os dois upgrades e um lingote na grade; monta o drop como o `StorageGameTests` faz para o filtro (leia o teste do filtro no drop lá e siga o mesmo caminho); coloca o item de novo e confere os dois upgrades e o lingote na grade.
- `upgradesSalvamECarregam`: `saveWithoutMetadata` e `loadWithComponents` num block entity novo; os slots voltam iguais.

- [ ] **Passo 2: Rodar e ver falhar** (`./gradlew compileJava`).

- [ ] **Passo 3: Implementar.** Pontos que não são óbvios:
  - `CompactingRules.CODEC`: `RecordCodecBuilder` com `mode` (`StringRepresentable.fromEnum`) e `targets` (`ResourceLocation.CODEC.listOf()` virando `Set` imutável), `optionalFieldOf("targets", List.of())`. `STREAM_CODEC` com `ResourceLocation.STREAM_CODEC` e `ByteBufCodecs.collection(HashSet::new, ..., 256)`. Teste no GameTest: um componente com um id que não existe (`"modsumido:bloco"`) é lido sem erro e ignorado.
  - `ChestUpgrades` grava `upgrades` e `craft_grid` com `ContainerHelper.saveAllItems` (só se não vazios) e lê com `loadAllItems`. O drop (`collect`) põe `ItemContainerContents.fromItems(...)` só quando não vazio, e o `apply` lê; `StorageChestBlockEntity` chama os dois e também tira as chaves no `removeComponentsFromTag`. O `collectImplicitComponents` da base marca `dropExpected` só com conteúdo: um Baú vazio com upgrades também precisa guardar, mas os upgrades vão no próprio componente do item (não no `StorageSavedData`), então nada muda no `stash`.
  - O drop do Baú vazio e sem filtro hoje empilha (sem componentes). Com upgrades ele passa a levar os componentes: documente isso no comentário do `collect`.
  - Receitas (`crafting_shapeless`): `crafting_upgrade` = `minecraft:crafting_table` + `wirelessautomate:signal_crystal`; `compacting_upgrade` = `minecraft:piston` + `wirelessautomate:signal_crystal` + `minecraft:iron_block`. Categoria `misc`.
  - Itens com `stacksTo(1)`; tooltip de uma linha cada (`tooltip.wirelessautomate.crafting_upgrade`, `tooltip.wirelessautomate.compacting_upgrade`): "Põe uma mesa de trabalho no Baú." / "Compacta e descompacta sozinho os itens do Baú."
  - Aba criativa: depois do `RANGE_UPGRADE`.
  - Texturas: em `gerar_texturas.py`, dois sprites novos ao lado de `sprites["item/range_upgrade"]` (Mesa: a moldura do cartão de upgrade com a grade 3×3 em madeira; Compactação: a mesma moldura com duas setas amarelas apontando uma para a outra, como o ícone do mockup), os modelos com `grava_json` e os nomes na lista dos modelos de item (perto da linha com `"chunk_loader_upgrade", "range_upgrade"`). Rode o script e anexe os dois PNGs ao relatório: o dono aprova o visual.

- [ ] **Passo 4: Rodar e ver passar** (`./gradlew build runGameTestServer`).

- [ ] **Passo 5: Varreduras e commit** ("Baú: os itens dos upgrades de Mesa e de Compactação e os dois slots").

---

### Tarefa 4: A Compactação no Baú

**Custo:** com o upgrade, cada inserção faz um `instanceof`, um `isComponentsPatchEmpty` e um `get` no índice; se o item é de cadeia, um `add` num `IntOpenHashSet` (criado sob demanda). Nenhuma alocação por inserção. No fim do tick, O(formas) por cadeia suja. Sem o upgrade, o gancho é um teste de nulo. Prova: GameTests e o bench `chest` antes e depois (ver `references/medir.md` da `wa-performance`; o cenário de Baú existente; tabela no relatório).

**Files:**
- Create: `storage/upgrade/ChestCompacting.java`
- Modify: `storage/KeyedStorage.java`, `storage/ItemStorage.java`, `storage/StorageChestBlockEntity.java`
- Modify: `gametest/ChestCompactingGameTests.java`

**Interfaces:**
- Consumes: `CompactingChains`, `CompactingChain`, `CompactingRules`, `CompactingMath`, `ChestUpgrades`.
- Produces:
  - `KeyedStorage`: `protected void inserted(K key)` (vazio; chamado no fim de um `insert` que guardou algo), `public long insertInternal(K key, long amount)` (só a capacidade, sem admissão, sem `inserted` e sem contar no `StorageWatch`) e `public long extractInternal(K key, long amount)` (sem contar no `StorageWatch`). A conversão é movimento interno: contada no vigia, a Tela de Sinal mostraria vazão de itens que nunca entraram nem saíram.
  - `ItemStorage`: `public interface InsertListener { void inserted(ItemStack key); }` e `void setInsertListener(@Nullable InsertListener)`.
  - `BulkItems`: `default long stockCount(ItemStack key, boolean components)` (hoje: `components ? count(key) : countItem(key)`). `ItemStorage` sobrescreve: com o `ChestCompacting` ativo e a chave de cadeia sem componentes, devolve `CompactingMath.equivalent(...)` da cadeia no nível da chave; senão o de hoje. Os dois lugares que contam para o limite passam a chamar `stockCount`: `ItemTransfer.bulkAccepts` (linha ~413) e `StorageChestBlockEntity.admit`. Custo: um teste de nulo sem o upgrade; O(formas) com ele, só quando há limite.
  - `ChestCompacting.unpackFor(ItemStack key, long need)`: com as regras ativas e a chave de cadeia, desce por dentro (`extractInternal` + `insertInternal`) a forma mais próxima acima, uma por vez, até `count(key) >= need` ou acabar o que descer ou o espaço; devolve se conseguiu. A sobra não suja a cadeia (fica até a próxima entrada). Usado pela Mesa (Tarefa 5) e pelo JEI (Tarefa 8).
  - `ChestCompacting`: `void setRules(@Nullable CompactingRules)` (nulo = sem upgrade), `void markAll()` (todas as cadeias presentes), `void markChain(int chainId)`, `void convertDirty()` (chamado no fim do tick), `int version()` (sobe quando as cadeias presentes ou as regras mudam; a Tarefa 6 usa) e `IntList presentChains()` (as cadeias com algum item no Baú, na ordem do índice).

- [ ] **Passo 1: GameTests (falham)**, em `ChestCompactingGameTests` (helpers: Baú colocado pelo jeito do `StorageGameTests`; o upgrade posto direto por `upgrades().setUpgrade`):
  - `compactaNaEntradaPeloHandler`: com o upgrade (modo Compactar), insere 100 lingotes de ferro pelo `ItemStorageHandler`. Na cadeia vanilla (pepita, lingote, bloco) o topo é o bloco: 100 lingotes = 11 blocos + 1 lingote. `thenWaitUntil` `count(IRON_BLOCK)==11` e `count(IRON_INGOT)==1`.
  - `compactaNaEntradaPeloRoteador`: um roteador tirando lingotes de um baú vanilla para o Baú (rede própria, como os testes do `StorageGameTests`); espera os blocos aparecerem.
  - `alvoEspecificoVenceOGlobal`: modo global Compactar e regra da cadeia do ferro com alvo lingote (`withTarget(chain, Items.IRON_INGOT)`); põe 2 blocos; espera 18 lingotes e 0 blocos.
  - `descompactarParaNaCapacidade`: Baú do menor tier, `long cap = chest.capacity()`; guarda `cap - 30` pedregulhos (sem cadeia vanilla, só ocupam espaço) e 4 blocos de ferro (espaço livre: 26); regra do ferro com alvo lingote. Cada bloco que desce rende 9 lingotes e gasta 8 de espaço: cabem 3 (24 ≤ 26). Espera 27 lingotes, 1 bloco e `total() <= cap`.
  - `semUpgradeNaoMexe`: sem upgrade, 100 lingotes ficam 100 lingotes depois de 2 ticks.
  - `upgradeNovoNaoMexe`: com o upgrade recém-posto (regras padrão, modo Desligada), 100 lingotes ficam 100 lingotes.
  - `limiteDoFiltroContaACadeia`: filtro do Baú com limite de 100 lingotes de ferro e modo Compactar; o handler aceita 100 lingotes (que viram 11 blocos + 1 lingote) e recusa o 101º (`insert` simulado devolve 0).
  - `limiteDoRoteadorContaACadeia`: roteador com estoque de 100 lingotes no destino (o Baú com Compactar), origem com 200 lingotes; depois de estabilizar, o Baú vale 100 lingotes (`stockCount`) e a origem ficou com 100.
  - `descerNaHoraParaAMesa`: modo Compactar, 2 blocos de ferro; `unpackFor(lingote, 3)` deixa 1 bloco e 9 lingotes.
  - `itemComComponenteNaoCompacta`: 9 lingotes com nome (`DataComponents.CUSTOM_NAME`) ficam como estão.
  - `conversaoIgnoraOFiltro`: filtro de entrada que só aceita lingote; com o modo Compactar, 9 lingotes viram 1 bloco mesmo o filtro recusando bloco.

- [ ] **Passo 2: Rodar e ver falhar.**

- [ ] **Passo 3: `KeyedStorage` e `ItemStorage`.** Separe o corpo do `insert` num `private long put(K key, long amount, boolean simulate, boolean external)`; `insert` chama com `external = true` (admissão, vigia e, se guardou algo, `inserted(entry.key)` no fim). `insertInternal` chama `put(key, amount, false, false)`: sem admissão, sem vigia e **sem** `inserted` (a conversão não suja a própria cadeia). O mesmo para o `extract` com `extractInternal`. `ItemStorage` sobrescreve `inserted` com `if (listener != null) listener.inserted(key);`. GameTest a mais: `conversaoNaoContaNoVigia` (com `startWatch()`, 9 lingotes viram 1 bloco e o vigia registra só a entrada dos 9 lingotes).

- [ ] **Passo 4: `ChestCompacting`.**
  - Campos: o `ItemStorage`, um `LongSupplier capacity`, `@Nullable CompactingRules rules`, `@Nullable IntOpenHashSet dirty`, `int version`, e o último `contentsVersion` em que `presentChains` foi montado (cache: só remonta quando o conteúdo mudou e alguém pede).
  - É o `InsertListener` do `ItemStorage` (o próprio objeto, sem lambda): `inserted(key)` → `if (rules == null || !key.isComponentsPatchEmpty()) return;` → `chain = CompactingChains.get().chainOf(key.getItem())` → se não nulo, `dirty().add(chain.id())`.
  - Geração: guarde a `CompactingChains.get().generation()` junto do conjunto sujo. Se no `inserted` ou no `convertDirty` a geração do índice for outra (houve `/reload`), limpe o conjunto, atualize a geração e chame `markAll()` antes de seguir: os ids velhos apontariam para outras cadeias. GameTest a mais: `recargaDoIndiceNaoConverteCadeiaErrada` (suja a cadeia do ferro, chama `CompactingChains.rebuild(server)` e confere que no fim do tick só o ferro mudou).
  - `convertDirty()`: para cada id sujo, a cadeia; `int target = rules.targetLevel(chain)`; se `< 0`, pula; monta `long[] counts` com `storage.count(new ItemStack(form))`. Para não alocar uma pilha por forma a cada conversão, guarde na `CompactingChain` (Tarefa 2) um `ItemStack[] keys` imutável com as pilhas de 1 de cada forma, e use `storage.count(chain.keys()[i])`. `room = capacity <= 0 ? Long.MAX_VALUE : capacity - storage.total()`. `long[] next = CompactingMath.convert(counts, chain.factors(), target, room)`. Aplique primeiro as reduções (`storage.extractInternal(keys[i], counts[i] - next[i])`) e depois os aumentos (`storage.insertInternal(keys[i], next[i] - counts[i])`). Se um aumento couber menos que o pedido (não deve), logue `warn` com a cadeia e devolva a diferença para a forma de onde saiu (nada se perde). Limpa `dirty` no fim (sem `trim` por tick).
  - `markAll()`: percorre os tipos do Baú uma vez (`storage.slots()`/`key(i)`) e suja as cadeias. Chamado quando o upgrade entra e quando as regras mudam.
  - O `version` sobe em `setRules` e quando `presentChains` muda de verdade.
- [ ] **Passo 5: `StorageChestBlockEntity`.** Cria o `ChestCompacting` e liga: quando o slot de upgrade muda (o `onChange` do `ChestUpgrades`), `compacting.setRules(stack com COMPACTING ? stack.get(COMPACTING_RULES) com DEFAULT : null)` e, se passou a ter regras, `markAll()` e `markContentsChanged()` para entrar na fila. Sobrescreve `compactContents()`: `compacting.convertDirty(); super.compactContents();` (o `KeyedStorageBlockEntity` já chama `keyed().compact()`; garanta a ordem: converter antes de fechar os buracos). No `onLoad`/`loadAdditional`, aplica as regras do slot carregado sem sujar (o conteúdo carregado já estava convertido).

  Observação: a conversão chama `changed()`, que põe o Baú de novo na fila do `StorageNeighbors` (a lista trocada, então no tick seguinte): é um aviso a mais aos vizinhos, uma vez, e sem nova conversão (nada sujo). Aceitável; documente no comentário do `compactContents`.

- [ ] **Passo 6: Rodar e ver passar** (`./gradlew build runGameTestServer`).

- [ ] **Passo 7: Bench antes e depois.** Rode o cenário de Baú do `/wa bench` em `main` (antes) e neste ramo (depois), sem upgrade no Baú; a diferença tem de ficar dentro do ruído. Anote a tabela no relatório e em `docs/benchmark.md`.

- [ ] **Passo 8: Varreduras e commit** ("Baú: a Compactação converte as cadeias no fim do tick").

---

### Tarefa 5: A Mesa de Trabalho no menu

**Custo:** a receita só é procurada no `slotsChanged` da grade (com a dica do último resultado, como o `CraftingMenu`). Repor a grade é O(9) por craft. Os slots usam a sincronização normal do menu. Prova: GameTests com um jogador de teste.

**Files:**
- Create: `menu/ChestUpgradeSlots.java`, `menu/ChestCraftGrid.java`
- Modify: `menu/StorageListMenu.java`, `packet/StorageActionPayload.java`
- Create: `gametest/ChestCraftingGameTests.java`

**Interfaces:**
- Consumes: `ChestUpgrades` (Tarefa 3), `ItemStorage`.
- Produces:
  - Índices dos slots do menu do Baú: inventário 0–35 (como hoje), upgrades 36–37, grade 38–46, resultado 47. Constantes em `ChestUpgradeSlots`: `UPGRADE_FIRST = 36`, `GRID_FIRST = 38`, `RESULT = 47`.
  - `ChestUpgradeSlots.place(int upgradeX, int upgradeY, int gridX, int gridY, int resultX, int resultY)` (a tela posiciona; os slots fora do painel aberto vão para fora da tela, como o `placeInventory` faz) e `boolean craftingActive()` (Mesa no slot), `boolean compactingActive()`.
  - Ação `StorageActionPayload.Action.RETURN_GRID`.

- [ ] **Passo 1: GameTests (falham)**, com `TestPlayers.inLevel` e o menu aberto no servidor (siga o `StorageSlotGameTests` para abrir o `StorageListMenu` e clicar):
  - `resultadoRepoeAGradePeloBau`: Baú com 64 tábuas; Mesa no slot; grade com 2 tábuas (uma em cima da outra: graveto); `clicked(RESULT, 0, PICKUP)`; o cursor tem 4 gravetos, a grade continua com 2 tábuas e o Baú com 62.
  - `shiftCraftaAteUmaPilha`: mesma montagem; `clicked(RESULT, 0, QUICK_MOVE)`. Uma pilha de gravetos (64) são 16 crafts. A grade começa com 2 tábuas e cada craft a repõe com 2 do Baú, então o Baú dá 32 tábuas: o inventário fica com 64 gravetos, a grade com 2 tábuas e o Baú com 64 − 32 = 32 (escreva essa conta num comentário do teste).
  - `devolverPoeAGradeNoBau`: grade com 3 pedras; `RETURN_GRID`; grade vazia e o Baú com 3 pedras a mais.
  - `semMesaOsSlotsNaoAceitam`: sem o upgrade, a grade não aceita item (`mayPlace` falso) e o resultado fica vazio.
  - `doisJogadoresNaoDuplicam`: dois jogadores de teste com o menu do mesmo Baú; A monta 2 tábuas (graveto), B vê o resultado; A tira as tábuas da grade; B tenta tirar o resultado (`PICKUP` e `QUICK_MOVE`): nada sai, e a soma de tábuas e gravetos no mundo fica igual.
  - `shiftNoResultadoNaoGuardaSemConsumir`: `quickMoveStack(player, RESULT)` direto (o caminho do `StorageListMenu` de hoje guardaria o item no Baú sem `onTake`): ou consome a grade, ou não move nada.
  - `duploCliqueNaoJuntaDoResultado`: com gravetos no cursor e o resultado mostrando gravetos, `PICKUP_ALL` não tira nada do slot do resultado.
  - `tirarAMesaDevolveAGrade`: com a grade cheia, tirar o upgrade do slot 36 põe a grade no Baú.

- [ ] **Passo 2: Rodar e ver falhar.**

- [ ] **Passo 3: `ChestCraftGrid`** implementa `CraftingContainer` (3×3) sobre uma `NonNullList<ItemStack>`: no servidor a do `ChestUpgrades.grid()` do Baú (mudança → `setChanged` do Baú e `menu.slotsChanged(this)`); no cliente uma lista local. `fillStackedContents` como o `TransientCraftingContainer`.

- [ ] **Passo 4: `ChestUpgradeSlots`.**
  - Slots de upgrade: `mayPlace` = `ChestUpgrades.accepts(slot, stack)`; `getMaxStackSize` 1. No cliente, um `SimpleContainer(2)` (o servidor manda o conteúdo pela sincronização normal).
  - Slots da grade: `mayPlace` só com a Mesa no slot (`craftingActive()`).
  - **Dois jogadores, uma grade:** a grade é do Baú e o `ResultContainer` é de cada menu. Toda mudança da grade (de qualquer menu, da devolução, do JEI) recalcula o resultado de **todos** os `StorageListMenu` abertos naquele Baú: o `ChestUpgrades` avisa por um `Runnable` que cada `ChestUpgradeSlots` registra ao abrir e tira no `removed` (lista pequena no block entity, criada sob demanda). E o `ChestResultSlot` confere de novo, no `mayPickup` e no começo do `onTake`, que a receita usada ainda casa com a grade (`recipe.value().matches(grid.asCraftInput(), level)`); se não casa, não entrega, limpa o resultado e recalcula.
  - `StorageListMenu.canTakeItemForPickAll(stack, slot)`: falso para o slot do resultado (como o `CraftingMenu`).
  - Resultado: um `ResultContainer` e uma classe `ChestResultSlot extends ResultSlot`. No `onTake(player, stack)`: guarde uma cópia das 9 pilhas da grade (`before`), chame `super.onTake` (consome como a mesa vanilla, com os restos) e reponha: para cada `i`, se `before[i]` não vazio e `grid[i]` está vazio ou é o mesmo item e componentes com menos, tire do Baú `before[i].getCount() - grid[i].getCount()` de `before[i].copyWithCount(1)` (`storage.extract`) e some na grade. Se o Baú não tem o bastante e o upgrade de Compactação está lá, chame antes `compacting.unpackFor(key, falta)` (desce uma forma por dentro). GameTest a mais em `ChestCraftingGameTests`: `mesaDesceOBlocoParaRepor` (modo Compactar, só 2 blocos de ferro no Baú, grade com a receita do balde: 3 lingotes em V; tirar o balde repõe os 3 lingotes descendo 1 bloco, e o Baú fica com 1 bloco e 6 lingotes). O resto vanilla que ficou num slot (balde vazio) não é tocado: só repõe slot vazio ou do mesmo item.
  - `slotsChanged(grid)` (servidor): `CraftingInput input = grid.asCraftInput();` `Optional<RecipeHolder<CraftingRecipe>> recipe = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level, lastRecipe);` resultado em `resultContainer` (respeitando `resultContainer.setRecipeUsed`), e mande o slot do resultado ao jogador (`player.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT, result))`), como o `CraftingMenu.slotChangedCraftingGrid` (é `protected static` lá; copie a lógica com um comentário dizendo de onde veio).
  - Shift + clique no resultado: no `StorageListMenu.clicked`, se `slotId == RESULT && clickType == QUICK_MOVE` e for o Baú, delegue a `ChestUpgradeSlots.quickCraft(player)`: repete enquanto o resultado não estiver vazio, for o mesmo item de antes e a soma movida for menor que `maxStackSize` do resultado; cada volta move o resultado para o inventário (`moveItemStackTo` nos slots 0–35, do fim para o começo como o vanilla) e chama `slot.onTake`. Para no primeiro que não couber.
  - Shift + clique num item do inventário com a Mesa aberta: continua indo para o Baú (o comportamento de hoje); não vai para a grade.
  - `RETURN_GRID`: para cada pilha da grade, `storage.insert` (com o filtro: é uma inserção normal); o que não coube vai para o inventário (`player.getInventory().add`) e o resto cai (`player.drop`).
  - Tirar o upgrade de Mesa (o slot 36/37 ficou sem `CRAFTING`): o mesmo `RETURN_GRID` no servidor, chamado pelo `onChange` do `ChestUpgrades` se a grade não estiver vazia (sem jogador: o que não coube no Baú cai no mundo, em cima do bloco, com `Containers.dropItemStack`).
  - `quickMoveStack` do resultado e da grade: grade → Baú (`storage.insert`), depois inventário.
- [ ] **Passo 5: `StorageListMenu`** cria o `ChestUpgradeSlots` só quando `kind` é o Baú (no construtor do servidor e no do cliente), depois do `addInventory`. Delegue `quickMoveStack` para índices ≥ 36, `slotsChanged`, `clicked` (o caso do resultado) e `removed` (nada a guardar: a grade já está no block entity). Mantenha o arquivo abaixo de 700 linhas.
- [ ] **Passo 6: Rodar e ver passar** (`./gradlew build runGameTestServer`).
- [ ] **Passo 7: Varreduras e commit** ("Baú: a Mesa de Trabalho do upgrade, com a grade que se repõe").

---

### Tarefa 6: O estado da Compactação na rede

**Custo:** o servidor monta o estado só com o painel da Compactação aberto (o cliente avisa abrir e fechar) e só quando a `ChestCompacting.version()` muda; um pacote por mudança, com a lista das cadeias presentes (ids de item pela registry, um `int` por forma) e as regras. Nada com a tela fechada. Prova: GameTest que conta os pacotes (ou a versão) e confere nenhum envio sem mudança.

**Files:**
- Create: `packet/CompactingStatePayload.java`, `packet/CompactingRulePayload.java`
- Modify: `packet/ModPayloads.java` (`VERSION` `"39"` e registro), `menu/ChestUpgradeSlots.java` (o envio no `broadcastChanges`), `storage/upgrade/ChestCompacting.java`
- Modify: `gametest/ChestCompactingGameTests.java`

**Interfaces:**
- Produces:
  - `record CompactingStatePayload(int containerId, CompactingMode mode, List<Row> rows)`; `record Row(List<Item> forms, int target, boolean own)` (`target` = nível; −1 = parada). Limite de 512 linhas (as primeiras na ordem do índice; o resto não aparece; comente o porquê).
  - `record CompactingRulePayload(int containerId, Kind kind, int mode, Optional<Item> item)` com `enum Kind { WATCH, UNWATCH, MODE, TARGET, CLEAR }` (`MODE` usa `mode`, o ordinal; `TARGET` leva a **forma alvo** e `CLEAR` qualquer forma da cadeia, pela registry). Pelo item e não pelo nível nem pelo id da cadeia: depois de um `/reload` o nível e o id podem apontar para outra coisa, o item não.
  - No servidor, o `CompactingRulePayload` altera o componente `COMPACTING_RULES` da pilha do upgrade no slot (cópia nova com `set`), o que passa pelo `onChange` do `ChestUpgrades` → `setRules` → `markAll` (ou só a cadeia, no `TARGET`/`CLEAR`).
  - Validação no servidor: o jogador tem o menu do Baú aberto com aquele `containerId`, `stillValid`, o upgrade de Compactação está no Baú e o item pertence a uma cadeia do índice atual.
- [ ] **Passo 1: GameTests (falham):** `regraPeloPayloadMudaOAlvo` (envia `TARGET` pelo handler do servidor com um jogador de teste e confere a conversão); `payloadInvalidoEIgnorado` (nível fora da cadeia, item que não é base, menu de outro bloco); `semPainelNaoMandaEstado` (sem `WATCH`, o `broadcastChanges` não monta nada: exponha um contador `ChestUpgradeSlots.sentStates()` para o teste).
- [ ] **Passo 2: Rodar e ver falhar.**
- [ ] **Passo 3: Implementar** (payloads com `StreamCodec`, registro no `ModPayloads`, `VERSION` `"39"`, envio no `broadcastChanges` só com `watching && version != sentVersion`).
- [ ] **Passo 4: Rodar e ver passar.**
- [ ] **Passo 5: Varreduras e commit** ("Baú: o estado e as regras da Compactação na rede (protocolo 39)").

---

### Tarefa 7: A tela: coluna de upgrades e os dois painéis

**Custo:** nada por quadro além de desenhar: as linhas do painel da Compactação (nomes, rótulos, medidas) são montadas quando o `CompactingStatePayload` chega ou a lista rola, com `GuiText`; a lista desenha só as linhas à vista. Prova: e2e com capturas e `GuiText.clipCount()==0`.

**Files:**
- Create: `client/storage/upgrade/UpgradePanels.java`, `UpgradeColumn.java`, `CraftingPanel.java`, `CompactingPanel.java`, `ChainPicker.java`, `CompactingClientState.java`
- Modify: `client/storage/StorageListScreen.java` (só ganchos), registro do handler do `CompactingStatePayload` no cliente
- Modify: `client/dev/E2eStorageSteps.java`, `lang/en_us.json`, `lang/pt_br.json`

**Interfaces:**
- Consumes: `ChestUpgradeSlots.place(...)`, `craftingActive()`, `compactingActive()`, os payloads da Tarefa 6, `StorageActionPayload.Action.RETURN_GRID`.
- Produces: `UpgradePanels` com `int extraLeft()`, `int extraRight()` (a largura que a coluna e o painel somam ao centralizar a janela), `void layout(int leftPos, int topPos, int imageHeight)`, `void render(...)`, `boolean mouseClicked/mouseScrolled/keyPressed(...)`, `boolean isInside(double x, double y)`.

Layout (do mockup, em pixels da GUI):
- **Coluna:** 30 de largura (aba de 6 + slot de 18 + margens), colada à esquerda do painel do Baú, a partir de `topPos + 20`; os dois slots de 18 um sob o outro, com 2 entre eles; o slot do painel aberto com a borda `GuiPaint.ACCENT`. Os slots são slots normais (clique pega e põe o item, como qualquer slot). A escolha do painel é por uma aba de 6 × 18 colada à esquerda de cada slot ocupado: clicar na aba abre o painel daquele upgrade. Com um upgrade só, o painel dele abre sozinho; ao pôr o segundo, o aberto continua; ao tirar o do painel aberto, abre o outro (ou nenhum). O painel escolhido é lembrado só na sessão da tela.
- **Painel da Mesa:** 96 de largura à direita do Baú: título, a grade 3×3 de slots 18, a seta, o resultado 26×26, o botão "Devolver" (`FlatButton`) e a linha de dica (`GuiText`, quebra em duas linhas no máximo).
- **Painel da Compactação:** 150 de largura: título; "Global" e o seletor de três botões (Desligada / Compactar / Descompactar); "Cadeias no Baú"; a lista com linhas de 20: ícone da base (16), nome (`GuiText`, corta com "…" só se faltar espaço: o e2e conta), `‹` (10), o ícone da forma alvo num slot de 18 com o rótulo "1x"…"9x" no canto, `›` (10), o nome da forma (36), a etiqueta Global/Própria. Rodinha sobre o ícone anda uma forma; clique no ícone abre o `ChainPicker` (grade de 6 colunas de slots 18 com todas as formas, abaixo da linha, por cima das outras; fecha com Esc, clique fora ou escolha). Rolagem da lista pela rodinha fora dos ícones e por uma barra fina como a da `StorageGrid`.
- Nome das formas na linha: "base" para o nível 0 de cadeias cujas formas acima são "1x…9x"; senão o nome curto do item (o `getHoverName` cortado pelo `GuiText`). Calculado quando o estado chega.
- `hasClickedOutside` da `StorageListScreen` passa a chamar `upgradePanels.isInside` antes do teste padrão (sem isso, clicar na coluna ou no painel joga o item do cursor no chão).
- Centralização: `leftPos` passa a considerar `extraLeft()` e `extraRight()` para a janela inteira caber na tela; o redimensionar continua limitado pelo espaço que sobra.
- Textos novos em `en_us.json` e `pt_br.json` (`gui.wirelessautomate.upgrade.*`): os títulos, "Global", os três modos, "Cadeias no Baú"/"Chains in this chest", "Própria"/"Custom", "parada"/"off", "Devolver"/"Return", as dicas.

- [ ] **Passo 1:** os passos do e2e em `E2eStorageSteps` (falham): abrir o Baú com os dois upgrades, capturar `armazenamento-upgrade-mesa` com uma receita montada; trocar para a Compactação com pedregulho, ferro e terra no Baú (cadeias vanilla do ferro; o pedregulho só tem cadeia com o AllTheCompressed, então use ferro, ouro e diamante); capturar `armazenamento-upgrade-compactacao`; abrir o `ChainPicker` do ferro e capturar `armazenamento-upgrade-seletor`; escolher o lingote e conferir no servidor a regra; conferir `GuiText.clipCount()==0`.
- [ ] **Passo 2:** implementar as classes (cada uma abaixo de 500 linhas; a `StorageListScreen` com no máximo +40 linhas).
- [ ] **Passo 3:** `./gradlew compileJava`, depois `./scripts/e2e.sh --only armazenamento --capture armazenamento --variants pt,escuro`; `run/e2e/result.txt` com `OK`; olhe as capturas novas (folha de contato com `scripts/folha.py` da skill `wa-curseforge-imagens`) e anexe ao relatório.
- [ ] **Passo 4:** varreduras e commit ("Baú: a coluna de upgrades e os painéis da Mesa e da Compactação").

---

### Tarefa 8: O "+" do JEI na Mesa do Baú

**Custo:** um pacote por clique no "+", com até 9 slots × 16 alternativas; o servidor procura cada alternativa no Baú (O(1) por `count`) e no inventário (O(36)). Prova: GameTest do handler do payload; o arrastar do JEI real só se testa à mão.

**Files:**
- Create: `packet/ChestCraftFillPayload.java`, `compat/jei/ChestCraftTransfer.java`
- Modify: `packet/ModPayloads.java`, `menu/ChestUpgradeSlots.java`, `compat/jei/WirelessAutomateJeiPlugin.java`
- Modify: `gametest/ChestCraftingGameTests.java`

**Interfaces:**
- Produces: `record ChestCraftFillPayload(int containerId, List<List<ItemStack>> slots, boolean maxTransfer)` (9 listas, até 16 pilhas de 1 cada); `ChestUpgradeSlots.fill(ServerPlayer, List<List<ItemStack>>, boolean max)`.
- `ChestCraftTransfer implements IRecipeTransferHandler<StorageListMenu<?>, RecipeHolder<CraftingRecipe>>` para `RecipeTypes.CRAFTING`: só com a Mesa no Baú (senão devolve um erro do JEI "precisa do Upgrade de Mesa de Trabalho"); monta as 9 listas pelos `IRecipeSlotView` de entrada (as receitas sem forma ocupam a grade da esquerda para a direita; as com forma respeitam a largura pelo `IShapedRecipe`/`ShapedRecipe`); no `doTransfer`, manda o payload.
- No servidor, `fill`: devolve a grade ao Baú (`RETURN_GRID`), depois para cada slot pega a primeira alternativa que existe no Baú (com a Compactação, também a que dá para descer por `unpackFor`) (tirando 1, ou com `max` até a pilha cheia do mesmo ingrediente igual para todos os slots, limitada pelo que há), senão do inventário; slot sem nenhuma fica vazio.

- [ ] **Passo 1:** GameTest `jeiPreencheAGradePeloBauEDepoisPeloInventario` chamando o handler do servidor direto (sem o JEI). Falha.
- [ ] **Passo 2:** implementar; registrar o handler no plugin do JEI (`registerRecipeTransferHandlers`). Os tipos do JEI só em `compat/jei`.
- [ ] **Passo 3:** rodar e ver passar; varreduras; commit ("Baú: o + do JEI monta a receita na Mesa do Baú").

---

### Tarefa 9: Guia, docs e fechamento da etapa

**Files:**
- Modify: `scripts/guide/gerar_guia.py` (página do Baú, `wireless-chest.md`, pt e en: uma seção "Upgrades" com "como operar": pôr o upgrade, abrir o painel, Mesa repõe a grade, Shift faz uma pilha, Compactação com o modo global e o alvo por cadeia, descompactar só o que cabe) e rode o script para gerar as páginas
- Modify: `docs/mapa/armazenamento.md` (linhas de `storage/upgrade/`, `menu/ChestUpgradeSlots`/`ChestCraftGrid`, `client/storage/upgrade/`), `docs/mapa/cliente-e-pacotes.md` (os payloads), `docs/mapa/testes.md` (os GameTests novos), `docs/mapa/logica-pura.md` (`CompactingMath`, `ChainGraph`)
- Modify: `docs/especificacao.md` (o Baú e os upgrades), `docs/progresso.md` (tabela, próximo passo, histórico com os números de testes e o e2e)

- [ ] **Passo 1:** guia e docs.
- [ ] **Passo 2:** `./scripts/e2e.sh --only guia`, `OK`.
- [ ] **Passo 3:** a verificação completa das 5 runs de GameTest + build, e o e2e `armazenamento`.
- [ ] **Passo 4:** commit ("Baú: guia e docs dos upgrades").

Depois: revisão final do ramo inteiro (modelo mais capaz), com as leis da `wa-performance`, o `varrer_perf.py`, o `medir.py` e o `varrer.py` em todo o diff contra o `main`, e a onda de correções. O dono testa à mão com a farm da lâmpada no runClient (AllTheCompressed já lá). A versão (2.13.0) é com a skill `wa-release`, quando o dono pedir.
