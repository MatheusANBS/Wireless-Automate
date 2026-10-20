# Ressonador: rebalanceamento, etapa 1 (motor e itens) · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** a escada nova de geração e a energia do roteador, com a migração; o rendimento em quartos; os Cristais Afinados com receitas e texturas; a fadiga (integridade, rachado, refazer); o Pó de Ressonância como catalisador do Elite para cima; e o cartão do Ultimate com o Allthemodium mais caro. A tela, a interferência e o Eco ficam para a etapa 2.

**Arquitetura:** as contas novas ficam puras no `CoreMath` e numa classe nova `CrystalIntegrity` (com JUnit). O `SignalCrystalItem` ganha o tier do afinado e a integridade (componente novo `crystal_integrity`), e o resto do Núcleo passa a perguntar ao item o rendimento em quartos. A fadiga entra no evento da recarga do `ResonatorBatch`, sem trabalho novo por tick. As receitas novas são dois tipos próprios (`tune_crystal` e `recast_crystal`), no molde do `CutCrystalRecipe`.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), ModDevGradle, JUnit 5, GameTests, Python 3 + PIL (texturas).

**Spec:** [`docs/superpowers/specs/2026-10-20-ressonador-rebalanceamento-design.md`](../specs/2026-10-20-ressonador-rebalanceamento-design.md), seções 1, 2, 3, 6 e "Migração". Mockup aprovado: `docs/preview/ressonador-rebalanceamento.html`; protótipos dos cristais: `docs/preview/ressonador-afinados-proto.png`.

## Restrições globais

- Código, comentários, commits e docs em português; textos nos dois `lang` (`en_us.json`, `pt_br.json`), sempre os dois, na mesma ordem.
- **Chaves existentes não mudam:** NBT do Núcleo (`redstone`, `crystals` etc.), componente `crystal_charge`, config `resonator.*` e `tiers.<tier>.energyPerTick`. Novas nesta etapa: componente `crystal_integrity`; config `resonator.crystalIntegrity` (padrão 8, 1..64) e `resonator.recastCrystals` (padrão 4, 1..9); `migration.balanceVersion` vai a 3.
- **Valores** (copiados da spec): FE por cristal Básico 512, Avançado 8.192, Elite 16.384, Esmeralda 262.144, Allthemodium 8.388.608, Vibranium 16.777.216, Unobtainium 268.435.456, Ultimate 536.870.912. Energia do roteador (FE/t): 1.024; 32.768; 131.072; 2.097.152; 83.886.080; 268.435.456; 4.294.967.296; 0 (sem limite).
- **Rendimento em quartos:** Sinal 4, Lapidado 8, Afinado 8. Em Núcleo Elite ou acima, Sinal e Lapidado valem 1/4 (1 e 2), e o Afinado de tier menor que o do Núcleo vale 2 (como o Lapidado).
- Allthemodium opcional e sem código: os Afinados do Allthemodium, Vibranium e Unobtainium sempre registrados, mas só na aba criativa, no JEI e nas receitas com o mod (`RouterTier.loaded()`, `neoforge:conditions`), como os cartões.
- `ModPayloads.VERSION` continua `"25"` nesta etapa (nenhum payload muda).
- **Performance (skill `wa-performance`):** nada novo por tick; a fadiga só no evento da recarga; nada de `values()` de enum no lote (use uma constante); `setChanged` só onde já existe. Leia `.claude/skills/wa-performance/SKILL.md` e rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados; resolva ou justifique cada candidato no relatório.
- Lógica pura com JUnit (`src/test/java`); o que depende do jogo, com GameTest (`timeoutTicks` ≤ 300; teste que muda a config num lote `wirelessautomate_config_<assunto>` próprio e devolve em `try/finally`).
- Texturas: edite `scripts/textures/gerar_texturas.py` (e o `blocos.py` se preciso), nunca os PNGs; rode o gerador.
- Tamanho (`wa-modular`): `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados; servidor até 600 linhas (dividir acima de 1000). O `ResonanceCoreBlockEntity` tem 631 linhas: o que crescer nele vai para classe de pacote nova. Comentários (`wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` (Git Bash, na raiz). Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte. Nunca dois jogos ao mesmo tempo; não mexa em `run/options.txt`.
- Commits sem linhas de atribuição, com o autor do dono; `git add` só dos arquivos da tarefa (nunca `run/`). Não faça push.

## Mapa de arquivos

Base `B/` = `src/main/java/io/github/matheusanbs/wirelessautomate/`, `T/` = `src/test/java/io/github/matheusanbs/wirelessautomate/`, `R/` = `src/main/resources/`.

| Arquivo | Papel |
| --- | --- |
| `B/resonator/CoreMath.java` (mudar) | Escada nova, rendimento em quartos (`yieldQuarters`), `perTick` e `maxOutput` em quartos |
| `T/resonator/CoreMathTest.java` (mudar) | Escada, quartos, saturação |
| `B/resonator/CrystalIntegrity.java` (novo, puro) | Integridade: cheia, gastar uma recarga, rachado |
| `T/resonator/CrystalIntegrityTest.java` (novo) | JUnit da integridade |
| `B/network/ResourceType.java`, `B/Config.java` (mudar) | Energia do roteador, chaves novas, `migrateBalance3` |
| `B/resonator/SignalCrystalItem.java` (mudar) | Tier do afinado, rendimento em quartos, integridade, rachado, nomes e dicas |
| `B/registry/ModItems.java`, `B/registry/ModDataComponents.java`, `B/registry/ModCreativeTabs.java` (mudar) | 6 Afinados, Pó de Ressonância, componente `crystal_integrity` |
| `B/client/ClientSetup.java` (mudar) | Predicado `wirelessautomate:cracked` nos cristais |
| `B/resonator/ResonanceCoreBlockEntity.java` (mudar) | `generationPerTick` e `capacity` em quartos |
| `B/resonator/CoreCatalyst.java` (novo) | Regra do catalisador por tier: o que o slot aceita e o que a recarga gasta |
| `B/resonator/ResonatorBatch.java` (mudar) | Fadiga na recarga, pular rachados |
| `B/resonator/CoreInventory.java`, `B/menu/ResonatorMenu.java`, `B/resonator/ResonatorPorts.java` (mudar) | Catalisador por tier; a Porta extrai o rachado |
| `B/resonator/TuneCrystalRecipe.java`, `B/resonator/RecastCrystalRecipe.java` (novos), `B/registry/ModRecipes.java` (mudar) | Os dois tipos de receita |
| `R/data/wirelessautomate/recipe/tuned_signal_crystal_*.json`, `resonance_dust.json`, `recast_crystal.json` (novos), `tier_core_ultimate_atm.json` (mudar) | Receitas |
| `scripts/textures/gerar_texturas.py` (mudar) | Afinados, Opacos dos Afinados, Rachados, Pó e os modelos com os overrides |
| `R/assets/wirelessautomate/lang/en_us.json`, `pt_br.json` (mudar) | Nomes e dicas |
| `B/gametest/ResonatorTunedGameTests.java`, `ResonatorFatigueGameTests.java`, `ResonatorBalanceGameTests.java` (novos) | GameTests da etapa |

---

### Task 1: Escada nova e rendimento em quartos (puro)

**Files:**
- Modify: `B/resonator/CoreMath.java`
- Test: `T/resonator/CoreMathTest.java`

**Interfaces:**
- Produces: `CoreMath.defaultFePerCrystal(int)` com a escada nova; `CoreMath.yieldQuarters(int kind, int crystalTier, int coreTier)` (kind: `KIND_SIGNAL = 0`, `KIND_CUT = 1`, `KIND_TUNED = 2`; `crystalTier` é o ordinal do tier do afinado ou −1); `CoreMath.perTick(long fePerCrystal, int quarterSum, double efficiency)`; `CoreMath.maxOutput(int chambers, long fePerCrystal)` (8 quartos por câmara, igual à conta de hoje); `CoreMath.ELITE_ORDINAL = 2`.
- Custo: só contas, chamadas onde as de hoje já são.

- [ ] **Step 1: Teste que falha**

```java
@Test
void escadaNova() {
    long[] esperado = {512, 8_192, 16_384, 262_144, 8_388_608, 16_777_216, 268_435_456L, 536_870_912L};
    for (int i = 0; i < esperado.length; i++) {
        assertEquals(esperado[i], CoreMath.defaultFePerCrystal(i), "tier " + i);
    }
}

@Test
void quartosPorTier() {
    // Básico (0) e Avançado (1): Sinal 4, Lapidado 8, Afinado 8
    assertEquals(4, CoreMath.yieldQuarters(CoreMath.KIND_SIGNAL, -1, 0));
    assertEquals(8, CoreMath.yieldQuarters(CoreMath.KIND_CUT, -1, 1));
    // Elite (2) para cima: Sinal 1, Lapidado 2, Afinado do tier ou maior 8, de tier menor 2
    assertEquals(1, CoreMath.yieldQuarters(CoreMath.KIND_SIGNAL, -1, 2));
    assertEquals(2, CoreMath.yieldQuarters(CoreMath.KIND_CUT, -1, 7));
    assertEquals(8, CoreMath.yieldQuarters(CoreMath.KIND_TUNED, 3, 3));
    assertEquals(8, CoreMath.yieldQuarters(CoreMath.KIND_TUNED, 7, 4));
    assertEquals(2, CoreMath.yieldQuarters(CoreMath.KIND_TUNED, 2, 3));
    assertEquals(8, CoreMath.yieldQuarters(CoreMath.KIND_TUNED, 2, 0));
}

@Test
void perTickEmQuartos() {
    assertEquals(1_024, CoreMath.perTick(512, 8, 1.0));
    assertEquals(128, CoreMath.perTick(512, 1, 1.0));
    assertEquals(921, CoreMath.perTick(512, 8, 0.9));
    assertEquals(0, CoreMath.perTick(512, 0, 1.0));
    assertEquals(8_589_934_592L, CoreMath.maxOutput(8, 536_870_912L));
}
```

- [ ] **Step 2: Rodar e ver falhar:** `./gradlew test --tests "*CoreMathTest"` → falha (escada antiga e métodos que não existem). Os testes antigos do `CoreMathTest` que conferem a escada ou o `perTick` em rendimento inteiro mudam junto: atualize-os para os valores novos em quartos.

- [ ] **Step 3: Implementar**

```java
public static final int KIND_SIGNAL = 0;
public static final int KIND_CUT = 1;
public static final int KIND_TUNED = 2;
/** A partir deste tier o cristal comum rende 1/4 e o Afinado de tier menor rende como o Lapidado. */
public static final int ELITE_ORDINAL = 2;

private static final long[] DEFAULT_FE_PER_CRYSTAL = {
    512, 8_192, 16_384, 262_144, 8_388_608, 16_777_216, 268_435_456L, 536_870_912L
};

/** Rendimento em quartos de um cristal numa câmara: 4 é o rendimento 1 do Cristal de Sinal. */
public static int yieldQuarters(int kind, int crystalTier, int coreTier) {
    int full = kind == KIND_SIGNAL ? 4 : 8;
    if (coreTier < ELITE_ORDINAL) {
        return full;
    }
    if (kind == KIND_TUNED && crystalTier >= coreTier) {
        return 8;
    }
    return full / 4;
}

/** Geração máxima por tick: todas as câmaras com rendimento 2 (8 quartos) a 100%. */
public static long maxOutput(int chambers, long fePerCrystal) {
    return mulSat(mulSat(chambers, fePerCrystal), 2);
}

/** FE por tick de cristais que somam {@code quarterSum} quartos de rendimento, na eficiência dada; arredonda para baixo. */
public static long perTick(long fePerCrystal, int quarterSum, double efficiency) {
    if (quarterSum <= 0 || efficiency <= 0) {
        return 0;
    }
    double porQuarto = fePerCrystal / 4.0;
    return (long) Math.floor(Math.min((double) Long.MAX_VALUE, porQuarto * quarterSum * Math.min(1.0, efficiency)));
}
```

Atualize o javadoc da tabela (`DEFAULT_FE_PER_CRYSTAL`): "acima do melhor gerador de cada tier no pack; ver a spec de 20/10/2026".

- [ ] **Step 4: Rodar e ver passar:** `./gradlew test --tests "*CoreMathTest"` → PASS.

- [ ] **Step 5: Manter o build verde:** a única chamada do `perTick`, `ResonanceCoreBlockEntity.generationPerTick`, passa a somar `item.yield() * 4` (o comportamento de hoje, em quartos); a Task 3 troca pela regra nova. Procure outras chamadas do `perTick` e do `maxOutput` (JEI, dica do slot) e ajuste do mesmo jeito.

- [ ] **Step 6: Commit** (`git add` do `CoreMath.java`, do teste e do Núcleo): `Ressonador: escada nova e rendimento em quartos`.

---

### Task 2: Energia do roteador, chaves novas e migração 3

**Files:**
- Modify: `B/network/ResourceType.java:21-22`, `B/Config.java`
- Create: `B/gametest/ResonatorBalanceGameTests.java`

**Interfaces:**
- Consumes: `CoreMath.defaultFePerCrystal` (Task 1).
- Produces: `Config.CURRENT_BALANCE = 3`; `Config.crystalIntegrity()` (int); `Config.recastCrystals()` (int); `ResourceType.ENERGY.defaultRate(ordinal)` com a escada nova.
- Custo: só na carga da config.

- [ ] **Step 1: GameTests que falham** (lote `wirelessautomate_config_balance3`, template `empty`), no molde dos de migração do `RouterGameTests` (lotes `wirelessautomate_config_migrate_*`):
  - `migracao3TrocaOPadraoAntigo`: põe `balanceVersion = 2`, o `fePerCrystal` de cada tier no padrão do balanceamento 2 (`{128, 1_024, 8_192, 32_768, 262_144, 2_097_152, 134_217_728, 1_073_741_824}`) e o `energyPerTick` de cada tier no padrão de hoje (`{1_000, 8_000, 64_000, 512_000, 4_096_000, 32_768_000, 2_097_152_000, 0}`); chama `Config.migrateBalance()`; confere a escada nova nos dois, `balanceVersion == 3` e o retorno = 15 (8 + 7; o Ultimate do roteador já é 0).
  - `migracao3MantemOQueODonoMudou`: com `fePerCrystal` do Elite em 12.345 e `energyPerTick` do Básico em 777, a migração não toca nesses dois.
  - `chavesNovasTemPadrao`: `Config.crystalIntegrity() == 8` e `Config.recastCrystals() == 4`.
  Todos restauram em `try/finally` e salvam a config (`Config.SPEC.save()`), como os testes de migração que já existem.

- [ ] **Step 2: Implementar**
  - `ResourceType.ENERGY`: `1_024L, 32_768L, 131_072L, 2_097_152L, 83_886_080L, 268_435_456L, 4_294_967_296L, 0L`, e o javadoc da linha: "A energia de cada tier escoa a geração cheia do Ressonador do mesmo tier por uma face."
  - `Config`: no bloco `resonator`, depois de `RECHARGE_TICKS`:

```java
CRYSTAL_INTEGRITY = builder
        .comment("Cargas de um cristal até rachar: cada recarga gasta 1; com 0 ele racha e precisa ser refeito.")
        .defineInRange("crystalIntegrity", 8, 1, 64);
RECAST_CRYSTALS = builder
        .comment("Cristais de Sinal cheios que refazem um cristal rachado na bancada.")
        .defineInRange("recastCrystals", 4, 1, 9);
```

  e o comentário do `fePerCrystal` passa a: "FE por tick de um cristal de rendimento 1 a 100% de sintonia (Lapidado e Afinado rendem 2; do Elite para cima, o cristal comum rende 1/4)."
  - `CURRENT_BALANCE = 3`; constantes `BALANCE2_FE_PER_CRYSTAL = {128, 1_024, 8_192, 32_768, 262_144, 2_097_152, 134_217_728L, 1_073_741_824L}` e `BALANCE2_ENERGY = {1_000, 8_000, 64_000, 512_000, 4_096_000, 32_768_000, 2_097_152_000L, 0}`; `migrateBalance3()` troca cada valor que ainda está no padrão do 2 e é diferente do novo; em `migrateBalance()`: `changed += version < 3 ? migrateBalance3() : 0;` depois do passo 2 (o passo 2 passa a rodar só com `version < 2`). Getters `crystalIntegrity()` e `recastCrystals()` pelo `read`, como os outros.
  - Confira o caminho da energia por `long` até a face (`ScalarTransfer`, `EnergyAccess`, `BulkEnergy`): 4.294.967.296 passa de `int`. Se algo corta em `int`, satura com `Math.min(Integer.MAX_VALUE, ...)` só onde a API do NeoForge exige `int`, e registre no relatório.
  - O `configDoRessonadorTemOsPadroes` (`ResonatorFormationGameTests:330`) e os testes que conferem a vazão de energia padrão mudam para a escada nova.

- [ ] **Step 3: Rodar:** `./gradlew build runGameTestServer` → os três testes novos passam, nenhum antigo quebra.

- [ ] **Step 4: Commit:** `Ressonador: energia do roteador por tier e migração 3`.

---

### Task 3: Afinados, integridade e rachado no item

**Files:**
- Create: `B/resonator/CrystalIntegrity.java`, `T/resonator/CrystalIntegrityTest.java`
- Modify: `B/resonator/SignalCrystalItem.java`, `B/registry/ModItems.java`, `B/registry/ModDataComponents.java`, `B/registry/ModCreativeTabs.java`, `B/client/ClientSetup.java`, `B/resonator/ResonanceCoreBlockEntity.java` (`generationPerTick`, `capacity`), `R/assets/wirelessautomate/lang/*.json`
- Create: `B/gametest/ResonatorTunedGameTests.java`

**Interfaces:**
- Consumes: `CoreMath.yieldQuarters`, `CoreMath.KIND_*` (Task 1); `Config.crystalIntegrity()` (Task 2).
- Produces:
  - `CrystalIntegrity.spend(int integrity)` → integridade depois de uma recarga (nunca abaixo de 0); `CrystalIntegrity.isCracked(int)`; `CrystalIntegrity.clamp(int, int max)`.
  - `SignalCrystalItem(boolean cut, @Nullable RouterTier tuned, Properties)`; `tunedTier()` (`RouterTier` ou `null`); `kind()` (`CoreMath.KIND_*`); `yieldQuarters(RouterTier coreTier)`; `static int integrity(ItemStack)` (sem componente = `Config.crystalIntegrity()`); `static void setIntegrity(ItemStack, int)` (cheio tira o componente); `static boolean isCracked(ItemStack)`.
  - `ModItems.TUNED_CRYSTALS` (`Map<RouterTier, DeferredItem<SignalCrystalItem>>`, de Elite a Ultimate) e `ModItems.RESONANCE_DUST` (`DeferredItem<Item>`).
  - `ModDataComponents.CRYSTAL_INTEGRITY` (`"crystal_integrity"`, `ExtraCodecs.intRange(0, 64)`, rede `VAR_INT`).
- Custo: `generationPerTick` faz a mesma volta pelas câmaras de hoje; o `capacity` não muda de custo.

- [ ] **Step 1: JUnit que falha** (`CrystalIntegrityTest`): `spend(8) == 7`, `spend(1) == 0`, `spend(0) == 0`, `isCracked(0)`, `!isCracked(1)`, `clamp(99, 8) == 8`, `clamp(-3, 8) == 0`.

- [ ] **Step 2: Implementar o puro**

```java
/** A integridade de um cristal do Ressonador: cada recarga gasta 1, e com 0 ele racha. Lógica pura. */
public final class CrystalIntegrity {
    private CrystalIntegrity() {
    }

    public static int clamp(int integrity, int max) {
        return Math.max(0, Math.min(max, integrity));
    }

    public static int spend(int integrity) {
        return Math.max(0, integrity - 1);
    }

    public static boolean isCracked(int integrity) {
        return integrity <= 0;
    }
}
```

- [ ] **Step 3: GameTests que falham** (`ResonatorTunedGameTests`, template `empty7`, apoio `ResonatorTestSupport`):
  - `afinadoDoTierRendeCheio`: Núcleo Esmeralda montado com 4 Afinados Esmeralda gera `CoreMath.perTick(fe, 32, eficiência)`.
  - `lapidadoComumRendeUmQuartoNoElite`: Núcleo Elite com 3 Lapidados gera `perTick(fe, 6, eficiência)`.
  - `afinadoDeTierMenorRendeComoLapidado`: Núcleo Esmeralda com um Afinado Elite rende 2 quartos.
  - `basicoAceitaQualquerCristalCheio`: Núcleo Básico com um Afinado Ultimate rende 8 quartos.
  - `rachadoNaoGera`: cristal com integridade 0 numa câmara não entra na soma.
  - `nomeEDicaDoRachado`: `getName` de um rachado usa a chave `.cracked`; a dica mostra "Integridade X de Y".

- [ ] **Step 4: Implementar o item e o registro**
  - `SignalCrystalItem`: campo `@Nullable RouterTier tuned`; construtor de três argumentos (o de dois chama com `null`); `kind()` (afinado → `KIND_TUNED`; senão `cut ? KIND_CUT : KIND_SIGNAL`); `yieldQuarters(RouterTier core)` = `CoreMath.yieldQuarters(kind(), tuned == null ? -1 : tuned.ordinal(), core.ordinal())`; o `yield()` antigo some se nada mais o usar (procure as chamadas: dica do slot em `client/resonator/screen/CrystalSlots` e JEI usam `yield`; troque pelos quartos ou por `kind()`).
  - Integridade: `integrity(stack)` = `stack.getOrDefault(CRYSTAL_INTEGRITY, Config.crystalIntegrity())` limitado ao máximo atual; `setIntegrity` tira o componente quando chega ao máximo (para empilhar com o novo, como a carga); `isCracked`.
  - `getName`: rachado → `getDescriptionId() + ".cracked"`; senão Opaco → `.dull`; senão o nome. `appendHoverText`: linha "Integridade %s de %s" quando abaixo do máximo; "Rachado: refaça com %s Cristais de Sinal" no rachado; para Afinados, "Afinado para o Núcleo %s ou abaixo" com o nome do tier (`RouterTier` tem o nome traduzido; use a mesma chave que o `TierCoreItem` usa).
  - `isBarVisible`: o rachado não tem barra.
  - `ModItems`: `TUNED_CRYSTALS` num `static` como o `TIER_CORES`, de `RouterTier.ELITE` em diante, com o nome `tuned_signal_crystal_<tier>`; `RESONANCE_DUST = ITEMS.registerSimpleItem("resonance_dust")`.
  - `ModCreativeTabs`: depois do Lapidado, os Afinados com `tier.loaded()` e o Pó.
  - `ClientSetup`: o predicado `dull` passa a valer para os Afinados também; predicado novo `cracked` (1 se rachado) em todos os cristais.
  - `ResonanceCoreBlockEntity.generationPerTick`: soma `item.yieldQuarters(tier())` dos cristais não Opacos e não rachados; `perTick(fe, soma, efficiency())`. `capacity()` continua pelo `maxOutput`.
  - `lang` (os dois): `item.wirelessautomate.tuned_signal_crystal_<tier>`, `.dull` e `.cracked` de cada um; `.cracked` do Sinal e do Lapidado; `item.wirelessautomate.resonance_dust`; as dicas `item.wirelessautomate.signal_crystal.tooltip.integrity`, `.tooltip.cracked`, `.tooltip.tuned`. Nomes em pt: "Cristal Afinado (Elite)", "Cristal Afinado Opaco (Elite)", "Cristal Afinado Rachado (Elite)", "Cristal de Sinal Rachado", "Cristal Lapidado Rachado", "Pó de Ressonância". Em en: "Tuned Signal Crystal (Elite)", "Dull Tuned Signal Crystal (Elite)", "Cracked Tuned Signal Crystal (Elite)", "Cracked Signal Crystal", "Cracked Cut Signal Crystal", "Resonance Dust".

- [ ] **Step 5: Rodar:** `./gradlew test build runGameTestServer runGameTestServerAllthemodium` → os testes da tarefa passam; o `ResonatorCoreGameTests` e o `ResonatorMenuGameTests` continuam passando (eles usam Núcleos Básicos ou rendem como antes no Básico; o que conferir rendimento no Elite com Lapidado comum muda para o valor de 1/4 com a explicação no próprio teste).

- [ ] **Step 6: Commit:** `Ressonador: Cristais Afinados, integridade e rachado`.

---

### Task 4: Texturas e modelos

**Files:**
- Modify: `scripts/textures/gerar_texturas.py`
- Gerados: `R/assets/wirelessautomate/textures/item/tuned_signal_crystal_<tier>.png` (+ `_dull`, `_cracked`), `signal_crystal_cracked.png`, `cut_signal_crystal_cracked.png`, `resonance_dust.png`, os `.mcmeta` das animações e os modelos `R/assets/wirelessautomate/models/item/*.json` com os overrides `dull` e `cracked`; `docs/preview/folha-de-sprites.png`.

**Interfaces:**
- Consumes: os nomes de registro da Task 3.
- Custo: nenhum em jogo.

- [ ] **Step 1: Implementar** no `gerar_texturas.py`, a partir do protótipo aprovado (`docs/preview/ressonador-afinados-proto.png`):
  - `afinado(tier)`: a grade `LAPIDADO`, com a cinta (`L`, `l`, `m`) nos tons da cor do tier (`GuiPaint.tierColor`: Elite `#1FA9A0`, Esmeralda `#22A84E`, Allthemodium `#E8740A`, Vibranium `#18B57A`, Unobtainium `#B23FD0`, Ultimate `#7B4FE0`; claro = mistura com branco 0,45, médio = a cor, escuro = mistura com preto 0,35) e duas gemas do material em (7,3) e (8,11); a mesma animação do `lapidado()` (brilho na cinta, miolo que pulsa, onda).
  - Opaco do Afinado: `opaco(LAPIDADO, BRASA_LAPIDADO)` com a cinta do tier.
  - Rachado (Sinal, Lapidado e cada Afinado): a forma com a paleta `CRISTAL_OPACO` e a rachadura escura `#2A2522` em (6,4), (7,5), (7,6), (8,8), (8,9), (9,10) no Lapidado e no Afinado, e numa diagonal equivalente dentro do `BRUTO` no Sinal (confira com o `_interior_px` que cada pixel está dentro do contorno, como a brasa do Opaco); sem animação.
  - Pó de Ressonância: o protótipo (`po()` de `docs/preview/ressonador-afinados-proto.png`): monte de pó coral e vermelho com três faíscas `#FFE68A`; sem animação.
  - Modelos: os de Afinado como os do Lapidado, com override `dull` e `cracked` (o `cracked` por último, para ganhar do `dull`); o Sinal e o Lapidado ganham o override `cracked`.
  - Folha de sprites com as peças novas.
- [ ] **Step 2: Rodar o gerador** (`python -I scripts/textures/gerar_texturas.py`) e abrir a folha `docs/preview/folha-de-sprites.png` (a ferramenta Read mostra imagens); descrever o que viu no relatório.
- [ ] **Step 3:** `./gradlew build` (nenhum modelo quebrado no log da run de GameTest: procure `Missing textures` e `Unable to load model`).
- [ ] **Step 4: Commit:** `Ressonador: texturas dos Afinados, rachados e Pó`.

---

### Task 5: Receitas (Afinar, Refazer, Pó e o cartão do Ultimate)

**Files:**
- Create: `B/resonator/TuneCrystalRecipe.java`, `B/resonator/RecastCrystalRecipe.java`
- Modify: `B/registry/ModRecipes.java`
- Create: `R/data/wirelessautomate/recipe/tuned_signal_crystal_elite.json`, `_emerald.json`, `_allthemodium.json`, `_vibranium.json`, `_unobtainium.json`, `_ultimate.json`, `_ultimate_no_atm.json`, `resonance_dust.json`, `recast_crystal.json`
- Modify: `R/data/wirelessautomate/recipe/tier_core_ultimate_atm.json`
- Test: GameTests no `ResonatorTunedGameTests` (receitas) e o `AtmGameTests` (namespace `wirelessautomate_atm`) para o cartão

**Interfaces:**
- Consumes: `SignalCrystalItem` (Task 3), `Config.recastCrystals()` (Task 2).
- Produces: `ModRecipes.TUNE_CRYSTAL` (`"tune_crystal"`), `ModRecipes.RECAST_CRYSTAL` (`"recast_crystal"`).
- Custo: só na bancada.

- [ ] **Step 1: GameTests que falham** (pela `RecipeManager` do nível, como os do `CutCrystalRecipe` no `ResonatorCrystalGameTests`):
  - `afinarLapidadoCheio`: Lapidado cheio + 2 lingotes de netherite → Afinado Elite cheio e íntegro.
  - `afinarRecusaGastoOuRachado`: o mesmo com Lapidado de carga parcial ou rachado não casa.
  - `reafinarDeTierMenor`: Afinado Elite cheio + 2 blocos de esmeralda + Nether Star → Afinado Esmeralda.
  - `refazerRachado`: Afinado Esmeralda rachado + 4 Cristais de Sinal cheios → Afinado Esmeralda cheio e íntegro; com 3 cristais não casa; com um Cristal de Sinal gasto não casa.
  - `poDeRessonancia`: redstone + glowstone em pó + Cristal de Sinal cheio → 4 Pós.
  - No `AtmGameTests`: o cartão Ultimate (anel com Redstone no centro) com Nether Stars nos cantos, ovo do dragão em cima e 3 blocos de liga Unobtainium-Vibranium casa; com o anel antigo (echo shards e netherite) não casa.

- [ ] **Step 2: Implementar os tipos**
  - `TuneCrystalRecipe extends ShapelessRecipe`, no molde do `CutCrystalRecipe` (codecs do grupo `category`, `ingredients`, `result`): `matches` exige que todo `SignalCrystalItem` da grade esteja cheio (`isFull`) e não rachado; `assemble` devolve o resultado sem componentes.
  - `RecastCrystalRecipe extends CustomRecipe` (sem JSON de ingredientes; o JSON só tem o `type` e a `category`): casa quando a grade tem exatamente um cristal rachado e exatamente `Config.recastCrystals()` Cristais de Sinal cheios (`ModItems.SIGNAL_CRYSTAL`, `isFull`, não rachados) e nada mais; `assemble` devolve uma cópia de 1 do rachado sem `crystal_charge` e sem `crystal_integrity`.
  - Registre os dois no `ModRecipes` como o `CUT_CRYSTAL`.
- [ ] **Step 3: JSONs** (`type: wirelessautomate:tune_crystal`, `category: misc`), cada um com a lista de cristais de entrada aceitos num ingrediente de lista: `[{"item":"wirelessautomate:cut_signal_crystal"}, {"item":"wirelessautomate:tuned_signal_crystal_<tier menor>"} ...]`. Materiais da spec (seção 2); os do Allthemodium, Vibranium, Unobtainium e o Ultimate com ATM levam `"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "allthemodium"}]`; o `_ultimate_no_atm` leva o `neoforge:not` dessa condição. `resonance_dust.json`: `crafting_shapeless` com `minecraft:redstone`, `minecraft:glowstone_dust` e `wirelessautomate:signal_crystal`, resultado `resonance_dust` ×4 (o Cristal de Sinal gasto também casaria numa receita comum: use o tipo `tune_crystal`, que já exige cheio). `recast_crystal.json`: `{"type": "wirelessautomate:recast_crystal", "category": "misc"}`.
- [ ] **Step 4: Cartão do Ultimate** (`tier_core_ultimate_atm.json`): padrão `"SDS","B B","SBS"`, `S` = `minecraft:nether_star`, `D` = `minecraft:dragon_egg`, `B` = tag `c:storage_blocks/unobtainium_vibranium_alloy`; condições iguais. Confira os testes do `TierUpgradeGameTests` e do `AtmGameTests` que citam o anel do Ultimate.
- [ ] **Step 5: Rodar:** `./gradlew build runGameTestServer runGameTestServerAllthemodium` → PASS.
- [ ] **Step 6: Commit:** `Ressonador: receitas de afinar, refazer, Pó e o cartão do Ultimate`.

---

### Task 6: Fadiga na recarga e catalisador por tier

**Files:**
- Create: `B/resonator/CoreCatalyst.java`
- Modify: `B/resonator/ResonatorBatch.java`, `B/resonator/ResonanceCoreBlockEntity.java` (só o que a regra pede: o tipo do catalisador guardado), `B/resonator/CoreInventory.java`, `B/menu/ResonatorMenu.java` (`RedstoneSlot.mayPlace`), `B/resonator/ResonatorPorts.java` (`CoreItems`), `R/assets/wirelessautomate/lang/*.json` (as mensagens de recusa)
- Create: `B/gametest/ResonatorFatigueGameTests.java`

**Interfaces:**
- Consumes: `SignalCrystalItem.integrity/setIntegrity/isCracked` e `CrystalIntegrity.spend` (Task 3); `ModItems.RESONANCE_DUST` (Task 3).
- Produces: `CoreCatalyst.accepts(RouterTier coreTier, ItemStack stack)` (Básico e Avançado: redstone comum ou Pó sem componentes; Elite ou acima: só o Pó); o Núcleo guarda **o item do catalisador** junto da contagem: NBT novo `catalyst` (string do id: `minecraft:redstone` ou `wirelessautomate:resonance_dust`; sem a chave = redstone, que é o de hoje), sem mudar a chave `redstone` (a contagem). O slot só aceita o mesmo item que já está lá enquanto a contagem for > 0.
- Custo: a regra roda só na inserção (menu e Porta) e no fim de uma recarga.

- [ ] **Step 1: GameTests que falham** (`ResonatorFatigueGameTests`, template `empty7`; os que mudam `rechargeTicks` ou `crystalLifeTicks` para acelerar vão no lote `wirelessautomate_config_fatigue`, devolvendo em `try/finally`):
  - `recargaGastaIntegridade`: Núcleo Básico com Lapidado Opaco e redstone; depois de uma recarga, carga cheia e `integrity == crystalIntegrity() - 1`.
  - `ultimaRecargaRacha`: com integridade 1, a recarga deixa o cristal rachado (carga 0, integridade 0), gasta o catalisador e não o enche.
  - `rachadoNaoRecarrega`: com um rachado e um Opaco, a recarga vai para o Opaco.
  - `eliteSoAceitaPo`: no Núcleo Elite, o slot recusa redstone (menu e Porta) e aceita o Pó; no Básico, aceita os dois, mas não misturados.
  - `redstoneAntigaNoEliteGastaAteAcabar`: Núcleo Elite com redstone gravada (pelo NBT de antes) recarrega gastando a redstone e, ao zerar, passa a aceitar o Pó.
  - `portaExtraiRachado`: a Porta de Cristais tira o rachado nas mesmas condições do Opaco.
  - `rachadoNaoEntraNaCamara`: o slot da câmara (menu), o `insertCrystal` do clique com a mão e a Porta recusam um cristal rachado.
- [ ] **Step 2: Implementar**
  - `ResonatorBatch.recharge`: `firstDull` passa a pular os rachados (helper `firstRechargeable`); ao completar, gasta 1 do catalisador e chama um método do Núcleo `finishRecharge(int chamber)` que faz `int restante = CrystalIntegrity.spend(SignalCrystalItem.integrity(stack))`; com `restante == 0`, deixa carga 0 e integridade 0 (rachou); senão, carga cheia e `setIntegrity(stack, restante)`. Um cristal que racha conta como "câmara mudou" para o `syncIfChanged` (a etapa 2 desenha o rachado).
  - `dullCount` (que acorda as Portas) conta Opacos e rachados (os dois saem pela Porta).
  - `CoreInventory.canPlaceItem` e `ResonatorMenu.RedstoneSlot.mayPlace`: `CoreCatalyst.accepts(core.tier(), stack)` e o mesmo item do que já está lá; `ResonatorPorts.CoreItems.isItemValid` e `insertItem`: a mesma regra; `getStackInSlot` devolve o item certo (redstone ou Pó) com a contagem.
  - `CoreItems.extractItem`: aceita `SignalCrystalItem.isDull(stack) || SignalCrystalItem.isCracked(stack)`.
  - Câmaras: `CoreInventory.canPlaceItem`, `ResonatorMenu.ChamberSlot.mayPlace`, `ResonanceCoreBlockEntity.insertCrystal`/`insertCrystalAt` e `CoreItems.isItemValid` recusam `SignalCrystalItem.isCracked(stack)` (o rachado só sai; a etapa 2 dá a ele o slot do Eco no Ultimate).
  - Mensagens de recusa no clique com a mão (`ResonanceCoreBlock`, onde hoje sai `redstone_full`): "Este Núcleo recarrega com Pó de Ressonância" (`block.wirelessautomate.resonance_core.needs_dust`), nos dois `lang`.
- [ ] **Step 3: Rodar:** `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` → PASS (os testes de recarga que já existem, em Núcleo Básico com redstone, continuam passando).
- [ ] **Step 4: Commit:** `Ressonador: fadiga do cristal e catalisador por tier`.

---

### Task 7: Fechamento da etapa 1

**Files:**
- Modify: `docs/progresso.md`, `CLAUDE.md` (mapa do código: `CrystalIntegrity`, `CoreCatalyst`, `TuneCrystalRecipe`, `RecastCrystalRecipe`, o componente `crystal_integrity`, a migração 3 na linha da `Config`)

- [ ] **Step 1:** rodar a verificação inteira (`./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`) e as três varreduras (`varrer_perf.py`, `varrer.py`, `medir.py`) nos arquivos do ramo; anotar os números de testes.
- [ ] **Step 2:** `docs/progresso.md`: a tabela de estado (Ressonador: rebalanceamento, etapa 1 pronta), o próximo passo (etapa 2: interferência, Eco e tela) e uma linha no histórico com os números.
- [ ] **Step 3: Commit:** `Ressonador: etapa 1 do rebalanceamento (motor e itens) e progresso`.

## Revisão final do ramo

Antes do merge, o revisor final (modelo mais capaz) confere a etapa contra a spec e as leis da `wa-performance`, roda as três varreduras e procura: rendimento calculado em outro lugar com a regra antiga (JEI, dica do slot, `share` do `CrystalSlots`), `int` cortando a energia nova, migração que mexe em valor mudado pelo dono, e rachado entrando em câmara.
