# Ressonador: recarga paralela do Núcleo (etapa 1 do Reparador de Cristais) · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** o Núcleo recarrega **todos** os Opacos de uma vez (um cronômetro só, 1 catalisador e 1 de integridade por cristal), em vez de um por vez, e a câmara e a tela mostram todos recarregando juntos.

**Arquitetura:** a regra do servidor muda só no `ResonatorBatch.recharge` (a varredura passa de "o primeiro Opaco" para "todos os Opacos que o catalisador paga, na ordem das câmaras"). Uma classe pura nova, `RechargeRank`, diz quantos recarregam e quais; o servidor a usa para o `ChamberView` ganhar `rechargers` (quantos recarregam agora), e o cliente a usa para saber, por câmara, se ela está recarregando (câmara em `ChamberCrystals`, tela em `CrystalSlots`). O NBT do Núcleo e o progresso (`recharge`) não mudam: um cronômetro para todos.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), JUnit 5, GameTests, Python 3 (gerador do guia).

**Spec:** [`docs/superpowers/specs/2026-10-26-reparador-de-cristais-design.md`](../specs/2026-10-26-reparador-de-cristais-design.md), decisão 1 e etapa 1.

## Restrições globais

- Código, comentários, commits e docs em português; textos nos dois `lang` (`en_us.json`, `pt_br.json`), sempre os dois, na mesma ordem.
- **Chaves existentes não mudam:** NBT do Núcleo (`redstone`, `catalyst`, `recharge`, `crystals` etc.), componentes `crystal_charge` e `crystal_integrity`, config `resonator.*` (`rechargeTicks` 600, `crystalIntegrity`). Nova chave só no update tag da câmara: `rechargers` (byte), fora do NBT salvo. `ModPayloads.VERSION` continua `32` (nenhum payload muda; o `ResonatorStatePayload` não ganha campo: a tela deriva o limite do catalisador do slot do menu).
- **Regra de recarga (decisão da spec):** ao completar os `rechargeTicks`, recarregam os Opacos recarregáveis (`SignalCrystalItem.isRechargeable`: Opaco não rachado), na ordem das câmaras, enquanto houver catalisador; cada um gasta 1 de catalisador e 1 de integridade (o `finishRecharge` de hoje, que racha com 0). O rachado fica de fora. Catalisador menor que o número de Opacos: recarregam os primeiros e o resto espera o próximo ciclo.
- **Performance (skill `wa-performance`):** nada novo por tick; a recarga continua sendo um evento do lote de 20 ticks; o `ChamberView` só vai ao cliente quando muda (o `rechargers` entra na comparação do `matches`, um laço de até 8 câmaras que já existe); zero alocação por quadro no cliente (laços de até 8 e máscara de bits, nada de lista, lambda ou `values()`). Leia `.claude/skills/wa-performance/SKILL.md` e rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados; resolva ou justifique cada candidato no relatório.
- Lógica pura com JUnit (`src/test/java`); o que depende do jogo, com GameTest (`timeoutTicks` ≤ 300; teste que muda a config num lote `wirelessautomate_config_<assunto>` próprio e devolve em `try/finally`). Os GameTests novos forçam a recarga por um lote do tamanho dela, sem mudar a config.
- Lado do cliente: classes de tela e câmara só em `client/`, nunca referenciadas por código comum.
- Tamanho (`wa-modular`): `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados. Comentários (`wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams` (Git Bash, na raiz). Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte, nunca espere um resultado que não vai vir. Nunca dois jogos ao mesmo tempo; não mexa em `run/options.txt`.
- Commits sem linhas de atribuição (nada de `Co-Authored-By` nem `Claude-Session`), com o autor do dono; `git add` só dos arquivos da tarefa (nunca `run/`). Não faça push.

## Mapa de arquivos

Base `B/` = `src/main/java/io/github/matheusanbs/wirelessautomate/`, `T/` = `src/test/java/io/github/matheusanbs/wirelessautomate/`, `R/` = `src/main/resources/`.

| Arquivo | Papel |
| --- | --- |
| `B/resonator/RechargeRank.java` (novo, puro) | Quantos Opacos recarregam por ciclo e se o de posição N está entre eles |
| `T/resonator/RechargeRankTest.java` (novo) | JUnit da regra |
| `B/resonator/ResonatorBatch.java` (mudar) | `recharge` recarrega todos os pagos pelo catalisador |
| `B/gametest/ResonatorParallelRechargeGameTests.java` (novo) | GameTests da recarga paralela e do `rechargers` da view |
| `B/resonator/ChamberView.java` (mudar) | Campo `rechargers`, no update tag e na comparação |
| `B/client/resonator/render/ChamberCrystals.java`, `ChamberScene.java`, `ChamberParticles.java`, `PlasmaTendrils.java` (mudar) | A câmara mostra todos os Opacos pagos recarregando |
| `B/client/resonator/screen/CrystalSlots.java` (mudar) | Barra e dica por slot |
| `R/assets/wirelessautomate/lang/en_us.json`, `pt_br.json` (mudar) | A dica "Na fila" |
| `scripts/guide/gerar_guia.py` (mudar) | O guia, nos dois idiomas |
| `docs/superpowers/specs/2026-10-15-ressonador-de-sinal-design.md`, `CLAUDE.md`, `docs/progresso.md` (mudar) | Docs |

---

### Task 1: A regra pura (`RechargeRank`)

**Files:**
- Create: `B/resonator/RechargeRank.java`
- Test: `T/resonator/RechargeRankTest.java`

**Interfaces:**
- Produces: `RechargeRank.size(int rechargeable, int catalyst)` → quantos Opacos recarregam no ciclo; `RechargeRank.includes(int rank, int size)` → se o Opaco de posto `rank` (0 é o primeiro recarregável na ordem das câmaras; `-1` quando a câmara não é recarregável) está entre eles. Usados pelas Tasks 3 e 4.

- [ ] **Step 1: Escreva o teste que falha**

```java
package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RechargeRankTest {
    @Test
    void umPorCatalisadorAteOTotal() {
        assertEquals(1, RechargeRank.size(3, 1));
        assertEquals(2, RechargeRank.size(2, 5));
        assertEquals(3, RechargeRank.size(3, 3));
    }

    @Test
    void semOpacoOuSemCatalisadorNinguemRecarrega() {
        assertEquals(0, RechargeRank.size(0, 4));
        assertEquals(0, RechargeRank.size(3, 0));
        assertEquals(0, RechargeRank.size(-1, 2));
        assertEquals(0, RechargeRank.size(2, -1));
    }

    @Test
    void osPrimeirosDaFilaEntram() {
        assertTrue(RechargeRank.includes(0, 1));
        assertFalse(RechargeRank.includes(1, 1));
        assertTrue(RechargeRank.includes(1, 2));
    }

    @Test
    void camaraNaoRecarregavelNuncaEntra() {
        assertFalse(RechargeRank.includes(-1, 8));
        assertFalse(RechargeRank.includes(0, 0));
    }
}
```

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew test --tests "*RechargeRankTest"`
Expected: FAIL (compilação: `RechargeRank` não existe).

- [ ] **Step 3: Implemente**

```java
package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * Quais Opacos o Núcleo recarrega no mesmo ciclo: todos os recarregáveis que o catalisador paga, na ordem das
 * câmaras (um catalisador por cristal). O servidor decide pelo catalisador do Núcleo, o cliente da câmara pelo
 * {@link ChamberView#rechargers()} e a tela pela pilha do slot do catalisador. Lógica pura, sem classes do Minecraft.
 */
public final class RechargeRank {
    private RechargeRank() {
    }

    /** Quantos Opacos recarregam no ciclo: um por catalisador, até o total de recarregáveis; nunca negativo. */
    public static int size(int rechargeable, int catalyst) {
        return Math.max(0, Math.min(rechargeable, catalyst));
    }

    /**
     * Se o Opaco de posto {@code rank} (0 é o primeiro recarregável na ordem das câmaras, -1 uma câmara que não é
     * recarregável) está entre os {@code size} que recarregam.
     */
    public static boolean includes(int rank, int size) {
        return rank >= 0 && rank < size;
    }
}
```

- [ ] **Step 4: Rode e veja passar**

Run: `./gradlew test --tests "*RechargeRankTest"`
Expected: PASS (4 testes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/resonator/RechargeRank.java src/test/java/io/github/matheusanbs/wirelessautomate/resonator/RechargeRankTest.java
git commit -m "Ressonador: regra pura de quais Opacos recarregam juntos"
```

---

### Task 2: O lote recarrega todos (servidor)

**Files:**
- Modify: `B/resonator/ResonatorBatch.java` (o método `recharge` e o `firstRechargeable`, linhas 104 a 150; o javadoc da classe)
- Create: `B/gametest/ResonatorParallelRechargeGameTests.java`

**Interfaces:**
- Consumes: `RechargeRank.size`; `SignalCrystalItem.isRechargeable(ItemStack)`; `ResonanceCoreBlockEntity.{crystals(), chambers(), redstone(), rechargeProgress(), setRechargeProgress(int), spendCatalyst()}` (já existem); `ResonatorBatch.finishRecharge(ItemStack)` (já existe, package-private estático).
- Produces: nada novo na API; o comportamento do `ResonatorBatch.run` muda. A Task 3 acrescenta ao arquivo de GameTests os testes da view.

- [ ] **Step 1: Escreva os GameTests que falham**

Crie `B/gametest/ResonatorParallelRechargeGameTests.java`:

```java
package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertInserted;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.crystal;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.formed;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBatch;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A recarga paralela do Núcleo (spec {@code 2026-10-26-reparador-de-cristais-design.md}): todos os Opacos que o
 * catalisador paga recarregam no mesmo ciclo. O Avançado tem 2 câmaras; a recarga é forçada por um lote do tamanho
 * dela, sem mudar a config.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorParallelRechargeGameTests {
    private ResonatorParallelRechargeGameTests() {
    }

    /** Um lote que completa a recarga em curso de uma vez. */
    private static void recharge(GameTestHelper helper, ResonanceCoreBlockEntity core) {
        ResonatorBatch.run(helper.getLevel(), core, (int) Math.min(Config.rechargeTicks(), Integer.MAX_VALUE));
    }

    /** Põe um Opaco e racha a pilha da câmara no lugar (rachado não entra pelos caminhos normais). */
    private static void crackedAt(GameTestHelper helper, ResonanceCoreBlockEntity core, int chamber) {
        helper.assertTrue(core.insertCrystalAt(chamber, crystal(true, 0)), "o Opaco não entrou");
        SignalCrystalItem.setIntegrity(core.crystals().get(chamber), 0);
        helper.assertTrue(SignalCrystalItem.isCracked(core.crystals().get(chamber)), "não rachou");
    }

    @GameTest(template = "empty7")
    public static void recarregaTodosOsOpacosDeUmaVez(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            assertInserted(helper, core, crystal(false, 0));
            assertInserted(helper, core, crystal(true, 0));
            helper.assertValueEqual(core.addCatalyst(new ItemStack(Items.REDSTONE, 2)), 2, "redstone que entrou");
            recharge(helper, core);
            for (int i = 0; i < 2; i++) {
                ItemStack stack = core.crystals().get(i);
                helper.assertTrue(SignalCrystalItem.isFull(stack), "a câmara " + i + " não encheu");
                helper.assertValueEqual(SignalCrystalItem.integrity(stack), Config.crystalIntegrity() - 1,
                        "integridade da câmara " + i);
            }
            helper.assertValueEqual(core.redstone(), 0, "um catalisador por cristal");
            helper.assertValueEqual(core.rechargeProgress(), 0, "o cronômetro não reiniciou");
        });
    }

    @GameTest(template = "empty7")
    public static void catalisadorLimitaQuantosRecarregam(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            assertInserted(helper, core, crystal(false, 0));
            assertInserted(helper, core, crystal(true, 0));
            helper.assertValueEqual(core.addCatalyst(new ItemStack(Items.REDSTONE, 1)), 1, "redstone que entrou");
            recharge(helper, core);
            helper.assertTrue(SignalCrystalItem.isFull(core.crystals().get(0)), "o primeiro Opaco não encheu");
            helper.assertTrue(SignalCrystalItem.isDull(core.crystals().get(1)), "o segundo encheu sem catalisador");
            helper.assertValueEqual(core.redstone(), 0, "o catalisador não foi gasto");
        });
    }

    @GameTest(template = "empty7")
    public static void rachadoFicaDeForaEOutroRecarrega(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            crackedAt(helper, core, 0);
            helper.assertTrue(core.insertCrystalAt(1, crystal(false, 0)), "o Opaco não entrou");
            helper.assertValueEqual(core.addCatalyst(new ItemStack(Items.REDSTONE, 2)), 2, "redstone que entrou");
            recharge(helper, core);
            helper.assertTrue(SignalCrystalItem.isCracked(core.crystals().get(0)), "o rachado mudou");
            helper.assertTrue(SignalCrystalItem.isFull(core.crystals().get(1)), "o Opaco não encheu");
            helper.assertValueEqual(core.redstone(), 1, "só o Opaco gasta catalisador");
        });
    }

    @GameTest(template = "empty7")
    public static void semCatalisadorNinguemRecarrega(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            assertInserted(helper, core, crystal(false, 0));
            assertInserted(helper, core, crystal(true, 0));
            recharge(helper, core);
            helper.assertTrue(SignalCrystalItem.isDull(core.crystals().get(0)), "encheu sem catalisador");
            helper.assertTrue(SignalCrystalItem.isDull(core.crystals().get(1)), "encheu sem catalisador");
            helper.assertValueEqual(core.rechargeProgress(), 0, "o cronômetro andou sem catalisador");
        });
    }
}
```

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew compileJava && ./gradlew runGameTestServer`
Expected: `recarregaTodosOsOpacosDeUmaVez` e `rachadoFicaDeForaEOutroRecarrega` passam ou falham pelo motivo certo: `recarregaTodosOsOpacosDeUmaVez` FALHA em "a câmara 1 não encheu" (só o primeiro enche); `catalisadorLimitaQuantosRecarregam` e `semCatalisadorNinguemRecarrega` já passam com o código de hoje.

- [ ] **Step 3: Implemente**

Em `B/resonator/ResonatorBatch.java`, troque o javadoc da classe (linhas 7 a 10) por:

```java
/**
 * Um lote do Núcleo de Ressonância: deriva da estação, geração pela sintonia, desgaste dos cristais e recarga dos
 * Opacos com o catalisador (todos os pagos de uma vez). O gerenciador roda um lote de 20 ticks por Núcleo a cada 20
 * ticks.
 */
```

Troque os métodos `recharge`, `finishRecharge` (só o javadoc) e `firstRechargeable` (linhas 104 a 150) por:

```java
    /**
     * Um cronômetro só para todos: o progresso anda com pelo menos um Opaco recarregável e catalisador; ao completar,
     * recarregam os Opacos recarregáveis na ordem das câmaras enquanto houver catalisador, cada um gastando 1
     * ({@link #finishRecharge}). Os que o catalisador não pagou esperam o próximo ciclo.
     */
    private static boolean recharge(ResonanceCoreBlockEntity core, int ticks) {
        if (rechargeable(core) == 0) {
            // O último Opaco saiu no meio: o progresso era deles, o próximo que entrar começa do zero.
            if (core.rechargeProgress() != 0) {
                core.setRechargeProgress(0);
            }
            return false;
        }
        if (core.redstone() <= 0) {
            return false;
        }
        long progress = (long) core.rechargeProgress() + ticks;
        if (progress >= Config.rechargeTicks()) {
            for (int i = 0; i < core.chambers() && core.redstone() > 0; i++) {
                ItemStack stack = core.crystals().get(i);
                if (SignalCrystalItem.isRechargeable(stack)) {
                    core.spendCatalyst();
                    finishRecharge(stack);
                }
            }
            progress = 0;
        }
        core.setRechargeProgress((int) Math.min(progress, Integer.MAX_VALUE));
        return true;
    }

    /**
     * A fadiga: cada recarga gasta 1 de integridade. Com integridade sobrando o cristal enche; a que zera racha o
     * cristal em vez de enchê-lo (carga 0 e integridade 0), e a câmara muda para o {@code syncIfChanged}.
     */
    static void finishRecharge(ItemStack stack) {
        int left = CrystalIntegrity.spend(SignalCrystalItem.integrity(stack));
        SignalCrystalItem.setIntegrity(stack, left);
        if (!CrystalIntegrity.isCracked(left)) {
            SignalCrystalItem.setCharge(stack, CrystalCharge.FULL);
        }
    }

    /** Quantos Opacos o Núcleo recarrega (o rachado fica de fora). */
    static int rechargeable(ResonanceCoreBlockEntity core) {
        int total = 0;
        for (int i = 0; i < core.chambers(); i++) {
            if (SignalCrystalItem.isRechargeable(core.crystals().get(i))) {
                total++;
            }
        }
        return total;
    }
```

(O `finishRecharge` fica igual; só confirme que não restou o `firstRechargeable` nem referência a ele.)

- [ ] **Step 4: Rode as suítes do Ressonador**

Run: `./gradlew compileJava && ./gradlew runGameTestServer`
Expected: os 4 testes novos passam. Se algum teste antigo (`ResonatorCoreGameTests`, `ResonatorFatigueGameTests`, `ResonatorSyncGameTests`, `ResonatorMenuGameTests`, `ResonatorLifeGameTests`) falhar porque supunha a fila de um por vez (dois Opacos e um recarregando só o primeiro), atualize a asserção para a regra paralela e diga no relatório qual e por quê; teste que só tem um Opaco não muda.

- [ ] **Step 5: Verificações da tarefa**

Run: `python -I .claude/skills/wa-performance/scripts/varrer_perf.py src/main/java/io/github/matheusanbs/wirelessautomate/resonator/ResonatorBatch.java` e `python -I .claude/skills/wa-comentarios/scripts/varrer.py src/main/java/io/github/matheusanbs/wirelessautomate/resonator/ResonatorBatch.java src/main/java/io/github/matheusanbs/wirelessautomate/gametest/ResonatorParallelRechargeGameTests.java`
Expected: sem candidatos novos (o laço de até 8 câmaras já existia no lote).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/resonator/ResonatorBatch.java src/main/java/io/github/matheusanbs/wirelessautomate/gametest/ResonatorParallelRechargeGameTests.java
git commit -m "Ressonador: o Núcleo recarrega todos os Opacos pagos pelo catalisador de uma vez"
```

(Inclua no `git add` os GameTests antigos que você tiver atualizado no Step 4.)

---

### Task 3: A view e a câmara (cliente)

**Files:**
- Modify: `B/resonator/ChamberView.java`
- Modify: `B/client/resonator/render/ChamberCrystals.java` (os métodos `render`, `recharging`, `recharge`)
- Modify: `B/client/resonator/render/ChamberScene.java` (os campos `rechargingPrev` e `flashFired`, o trecho do clarão no `update` e o `trackRecharge`)
- Modify: `B/client/resonator/render/ChamberParticles.java` (a linha 153)
- Modify: `B/client/resonator/render/PlasmaTendrils.java` (as linhas 45 e 58)
- Modify: `B/gametest/ResonatorParallelRechargeGameTests.java` (testes da view)

**Interfaces:**
- Consumes: `RechargeRank.size` e `RechargeRank.includes` (Task 1); `ResonatorBatch.rechargeable(core)` não (package-private em outro lugar: o `ChamberView` conta por conta própria).
- Produces: `ChamberView.rechargers()` (int, 0 a 8, quantos Opacos recarregam agora); `ChamberCrystals.isRecharging(int i, byte[] chambers, ChamberView view)` e `ChamberCrystals.firstRecharging(byte[] chambers, ChamberView view)` (o índice da primeira câmara que recarrega, ou -1). Some `ChamberCrystals.recharging(byte[], ChamberView)`.

- [ ] **Step 1: Escreva os GameTests da view que falham**

Acrescente ao `ResonatorParallelRechargeGameTests` (e os imports `io.github.matheusanbs.wirelessautomate.resonator.ChamberView` e `net.minecraft.nbt.CompoundTag`):

```java
    @GameTest(template = "empty7")
    public static void viewDizQuantosRecarregam(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            assertInserted(helper, core, crystal(false, 0));
            assertInserted(helper, core, crystal(true, 0));
            helper.assertValueEqual(ChamberView.of(core, -1).rechargers(), 0, "sem catalisador");
            core.addCatalyst(new ItemStack(Items.REDSTONE, 1));
            helper.assertValueEqual(ChamberView.of(core, -1).rechargers(), 1, "um catalisador, dois Opacos");
            core.addCatalyst(new ItemStack(Items.REDSTONE, 3));
            helper.assertValueEqual(ChamberView.of(core, -1).rechargers(), 2, "catalisador de sobra");
        });
    }

    @GameTest(template = "empty7")
    public static void rachadoNaoContaNosQueRecarregam(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            crackedAt(helper, core, 0);
            core.addCatalyst(new ItemStack(Items.REDSTONE, 2));
            helper.assertValueEqual(ChamberView.of(core, -1).rechargers(), 0, "só o rachado");
            helper.assertTrue(core.insertCrystalAt(1, crystal(false, 0)), "o Opaco não entrou");
            helper.assertValueEqual(ChamberView.of(core, -1).rechargers(), 1, "o rachado contou");
        });
    }

    @GameTest(template = "empty7")
    public static void rechargersVaiNoUpdateTag(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            assertInserted(helper, core, crystal(false, 0));
            assertInserted(helper, core, crystal(true, 0));
            core.addCatalyst(new ItemStack(Items.REDSTONE, 2));
            ChamberView view = ChamberView.of(core, 100);
            CompoundTag tag = new CompoundTag();
            view.save(tag);
            helper.assertValueEqual(ChamberView.load(tag).rechargers(), 2, "o update tag perdeu o rechargers");
            helper.assertTrue(ChamberView.load(tag).equals(view), "a view lida não é a gravada");
        });
    }
```

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew compileJava`
Expected: FAIL (compilação: `rechargers()` não existe).

- [ ] **Step 3: Implemente o `ChamberView`**

Em `B/resonator/ChamberView.java`:

1. Acrescente ao javadoc do record, depois de `@param rechargeTicks`:
```java
 * @param rechargers quantos Opacos recarregam agora: um por catalisador no Núcleo, até o total de recarregáveis
 *     ({@link RechargeRank#size}); 0 sem recarga
```
2. Inclua o componente `int rechargers` logo depois de `int rechargeTicks` na declaração do record:
```java
public record ChamberView(byte[] chambers, byte[] tiers, int band, boolean tuned, long rechargeStart, int rechargeTicks,
        int rechargers, long formedAt, int[] origin, byte[] flows, boolean full) {
```
3. `EMPTY`: acrescente `0,` depois de `DEFAULT_RECHARGE_TICKS,`:
```java
    public static final ChamberView EMPTY = new ChamberView(new byte[0], new byte[0], 0, false, -1, DEFAULT_RECHARGE_TICKS, 0, -1,
            new int[3], new byte[0], false);
```
4. No construtor compacto, depois de `rechargeTicks = Math.max(1, rechargeTicks);`:
```java
        rechargers = Math.max(0, rechargers);
```
5. Em `of`, acrescente o argumento depois do `(int) Math.min(Config.rechargeTicks(), Integer.MAX_VALUE)`:
```java
        return new ChamberView(chambers, tiers, TuneSignal.band(core.efficiency()),
                TuneSignal.tuned(core.station(), core.dial()), rechargeStart,
                (int) Math.min(Config.rechargeTicks(), Integer.MAX_VALUE), rechargers(core), core.formedAt(), core.origin(),
                core.flow().bands(), core.flow().full());
```
6. Em `matches`, inclua `|| rechargers != rechargers(core)` na condição de saída antecipada (junto de `rechargeTicks`).
7. Troque o método `recharging(core)` (linhas 141 a 153) por:
```java
    /** Quantos Opacos recarregam agora: um por catalisador no Núcleo, até o total de recarregáveis (o rachado fica de fora). */
    static int rechargers(ResonanceCoreBlockEntity core) {
        int rechargeable = 0;
        for (int i = 0; i < core.chambers(); i++) {
            if (SignalCrystalItem.isRechargeable(core.crystals().get(i))) {
                rechargeable++;
            }
        }
        return RechargeRank.size(rechargeable, core.redstone());
    }

    /** Se há recarga em curso: um Opaco não rachado numa câmara do tier e catalisador no Núcleo. */
    static boolean recharging(ResonanceCoreBlockEntity core) {
        return rechargers(core) > 0;
    }
```
8. `save`: depois de `tag.putInt("recharge_ticks", rechargeTicks);` acrescente `tag.putByte("rechargers", (byte) rechargers);`.
9. `load`: acrescente `tag.getByte("rechargers"),` depois de `tag.getInt("recharge_ticks"),`.
10. `equals`: acrescente `&& rechargers == view.rechargers` depois de `rechargeTicks == view.rechargeTicks`; `hashCode`: depois de `hash = 31 * hash + rechargeTicks;` acrescente `hash = 31 * hash + rechargers;`; `toString`: depois de `", rechargeTicks=" + rechargeTicks` acrescente `+ ", rechargers=" + rechargers`.

- [ ] **Step 4: Implemente a câmara**

`B/client/resonator/render/ChamberCrystals.java` (importe `io.github.matheusanbs.wirelessautomate.resonator.RechargeRank`):

- No `render`, apague a linha `int recharging = recharging(chambers, view);` e troque `recharging == i ? CrystalParam.recharging(progress)` por `isRecharging(i, chambers, view) ? CrystalParam.recharging(progress)`.
- Troque `recharging` e `recharge` (linhas 154 a 173) por:

```java
    /** O posto da câmara {@code i} entre os Opacos recarregáveis (0 é o primeiro), ou -1 se ela não é um. */
    private static int rank(int i, byte[] chambers) {
        if (!rechargeable(chambers[i])) {
            return -1;
        }
        int rank = 0;
        for (int j = 0; j < i; j++) {
            if (rechargeable(chambers[j])) {
                rank++;
            }
        }
        return rank;
    }

    /** Opaco que o Núcleo recarrega: sem carga e não rachado. */
    private static boolean rechargeable(byte code) {
        return isDull(code) && code < CRACKED_SIGNAL;
    }

    /** Se a câmara {@code i} é um Opaco que recarrega agora: recarga em curso e o posto dentro do que o catalisador paga. */
    static boolean isRecharging(int i, byte[] chambers, ChamberView view) {
        return view.rechargeStart() >= 0 && RechargeRank.includes(rank(i, chambers), view.rechargers());
    }

    /** A primeira câmara que recarrega agora, ou -1. */
    static int firstRecharging(byte[] chambers, ChamberView view) {
        for (int i = 0; i < chambers.length; i++) {
            if (isRecharging(i, chambers, view)) {
                return i;
            }
        }
        return -1;
    }

    /** De 0 a 1, o quanto a recarga da câmara {@code i} já andou; 0 se ela não recarrega. */
    static double recharge(int i, byte[] chambers, double ticks, ChamberView view) {
        if (!isRecharging(i, chambers, view)) {
            return 0;
        }
        return Math.clamp((ticks - view.rechargeStart()) / view.rechargeTicks(), 0.0, 1.0);
    }
```

`B/client/resonator/render/ChamberScene.java`:

- Troque os campos (linhas 74 a 76):
```java
    /** As câmaras que recarregavam na view anterior (um bit por câmara) e as que já tiveram o clarão do fim. */
    private int rechargingPrev;
    private int flashFired;
```
- Em `update`, no bloco que recria `flashAt` (linha 97) troque `rechargingPrev = -1;` por `rechargingPrev = 0;` e acrescente `flashFired = 0;` logo depois.
- Troque o trecho do clarão por progresso (linhas 138 a 142) por:
```java
        for (int i = 0; i < chambers.length; i++) {
            int bit = 1 << i;
            if ((rechargingPrev & bit) != 0 && (flashFired & bit) == 0
                    && ChamberCrystals.recharge(i, chambers, gameTime, view) >= 1) {
                flashAt[i] = gameTime;
                flashFired |= bit;
            }
        }
```
- Troque o `trackRecharge` (javadoc e método, linhas 152 a 166) por:
```java
    /**
     * O clarão de uma câmara sai quando ela deixa de ser Opaco que recarrega (a view nova) e ainda não saiu pelo
     * progresso (a recarga do cliente chegou a 1 antes); só então, e nunca por uma câmara esvaziada.
     */
    private void trackRecharge(long gameTime) {
        int now = 0;
        for (int i = 0; i < chambers.length; i++) {
            if (ChamberCrystals.isRecharging(i, chambers, view)) {
                now |= 1 << i;
            }
        }
        if (now == rechargingPrev) {
            return;
        }
        for (int i = 0; i < chambers.length; i++) {
            int bit = 1 << i;
            if ((rechargingPrev & bit) != 0 && (now & bit) == 0 && (flashFired & bit) == 0
                    && (chambers[i] == 1 || chambers[i] == 2)) {
                flashAt[i] = gameTime;
            }
        }
        flashFired &= now;
        rechargingPrev = now;
    }
```

`B/client/resonator/render/ChamberParticles.java` linha 153: `int target = ChamberCrystals.recharging(chambers, view);` vira `int target = ChamberCrystals.firstRecharging(chambers, view);`.

`B/client/resonator/render/PlasmaTendrils.java`: apague a linha 45 (`int recharging = ChamberCrystals.recharging(chambers, view);`) e troque a condição da linha 58 por:
```java
            if (reverse && (!ChamberCrystals.isRecharging(i, chambers, view)
                    || ChamberCrystals.recharge(i, chambers, ticks, view) >= 1)) {
```

- [ ] **Step 5: Compile e rode**

Run: `./gradlew compileJava && ./gradlew runGameTestServer`
Expected: compila (nenhum uso restante de `ChamberCrystals.recharging(`; confira com `grep -rn "ChamberCrystals.recharging(" src`); os 3 testes novos da view e os 4 da Task 2 passam; `ResonatorSyncGameTests` continua verde (o update tag só sai quando a view muda: o `rechargers` mudou junto com o resto).

- [ ] **Step 6: Verificações da tarefa**

Run: `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` (arquivos mudados) e `python -I .claude/skills/wa-comentarios/scripts/varrer.py` (idem). Sem alocação nova por quadro: só laços de até 8 e máscara de bits. `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados: `ChamberScene` e `ChamberView` continuam dentro do limite.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/resonator/ChamberView.java src/main/java/io/github/matheusanbs/wirelessautomate/client/resonator/render/ChamberCrystals.java src/main/java/io/github/matheusanbs/wirelessautomate/client/resonator/render/ChamberScene.java src/main/java/io/github/matheusanbs/wirelessautomate/client/resonator/render/ChamberParticles.java src/main/java/io/github/matheusanbs/wirelessautomate/client/resonator/render/PlasmaTendrils.java src/main/java/io/github/matheusanbs/wirelessautomate/gametest/ResonatorParallelRechargeGameTests.java
git commit -m "Ressonador: a câmara mostra todos os Opacos pagos recarregando juntos"
```

---

### Task 4: A tela e os textos

**Files:**
- Modify: `B/client/resonator/screen/CrystalSlots.java` (as linhas 73 e 117 e o método `recharging()`, 137 a 146)
- Modify: `R/assets/wirelessautomate/lang/en_us.json`, `pt_br.json` (a chave `gui.wirelessautomate.resonator.chamber.waiting`, linha 1195 nos dois)

**Interfaces:**
- Consumes: `RechargeRank.includes(int, int)`; `SignalCrystalItem.isRechargeable`; `menu.redstoneSlot()`, `menu.chambers()`, `menu.getSlot(int).getItem()` (o catalisador é a pilha do slot do menu).
- Produces: nada novo para outras tarefas.

- [ ] **Step 1: Implemente**

Em `CrystalSlots.java` (importe `io.github.matheusanbs.wirelessautomate.resonator.RechargeRank`):

- Linha 73: `slot.index != recharging()` vira `!recharging(slot.index)`.
- Linha 117: `index != recharging()` vira `!recharging(index)`.
- Troque o método `recharging()` (javadoc e corpo) por:

```java
    /**
     * Se a câmara {@code index} é um Opaco que recarrega no ciclo: o posto dela entre os recarregáveis cabe no que o
     * catalisador paga, a mesma regra do servidor. Sem catalisador o primeiro ainda conta (a dica diz que falta o
     * catalisador no Núcleo), por isso o mínimo de 1.
     */
    private boolean recharging(int index) {
        int paid = Math.max(1, menu.getSlot(menu.redstoneSlot()).getItem().getCount());
        int rank = 0;
        for (int i = 0; i < menu.chambers(); i++) {
            if (SignalCrystalItem.isRechargeable(menu.getSlot(i).getItem())) {
                if (i == index) {
                    return RechargeRank.includes(rank, paid);
                }
                rank++;
            }
        }
        return false;
    }
```

Nos dois `lang`, troque o valor da chave `gui.wirelessautomate.resonator.chamber.waiting`:
- `pt_br.json`: `"Na fila: falta catalisador para este Opaco"`
- `en_us.json`: `"Waiting: not enough catalyst for this Dull crystal"`

- [ ] **Step 2: Compile e confira**

Run: `./gradlew compileJava` e `grep -rn "recharging()" src/main/java/io/github/matheusanbs/wirelessautomate/client/resonator/screen` (sem resultado).
Expected: compila; nenhum uso do método antigo.

- [ ] **Step 3: Rode a tela no e2e do Ressonador**

Run: `WA_E2E="$PWD/run/e2e" WA_E2E_ONLY=ressonador ./gradlew runClient` (um cliente só; compile antes; se aparecer `BUILD FAILED`, pare) e olhe `run/run/e2e/result.txt` (precisa dizer `OK`) e a captura `ressonador-tela` (a barra de recarga sobre o primeiro Opaco e a dica). Confira o log: `grep -c "Couldn't compile\|não compilou" run/logs/latest.log` deve dar 0.
Expected: `OK`, sem texto cortado, sem shader falhando. A captura `ressonador-camara-recarga` continua mostrando o tentáculo invertido e o clarão (com um Opaco só, nada muda).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/client/resonator/screen/CrystalSlots.java src/main/resources/assets/wirelessautomate/lang/en_us.json src/main/resources/assets/wirelessautomate/lang/pt_br.json
git commit -m "Ressonador: a tela mostra a recarga de todos os Opacos pagos"
```

---

### Task 5: Guia, specs e progresso

**Files:**
- Modify: `scripts/guide/gerar_guia.py` (linhas 2523 a 2527 em pt e 2708 a 2712 em inglês) e as páginas geradas
- Modify: `docs/superpowers/specs/2026-10-15-ressonador-de-sinal-design.md` (linha 110)
- Modify: `CLAUDE.md` (a linha da tabela "Mapa do código" que lista a lógica pura: `CrystalCharge.java`, `CrystalIntegrity.java`… e a do `resonator/`)
- Modify: `docs/progresso.md`

- [ ] **Step 1: O guia**

Em `scripts/guide/gerar_guia.py` (edite o script, nunca os `.md` gerados), troque nos dois idiomas:

Português (linhas 2523 a 2527):
```
3. O Núcleo recarrega **todos os Opacos de uma vez**: leva 30 segundos, gasta 1 catalisador por cristal e cada um volta cheio. Com menos catalisador que Opacos, recarregam os primeiros e os outros esperam o próximo ciclo.

O slot do catalisador guarda um tipo por vez: para trocar de catalisador, espere o que está lá acabar ou tire-o
pela tela. A Porta de Cristais só devolve os Opacos quando o Núcleo está sem catalisador; com ele, o Opaco fica lá
dentro esperando o catalisador e o ciclo de recarga.
```
Inglês (linhas 2708 a 2712):
```
3. The Core recharges **all Dull crystals at once**: it takes 30 seconds, uses 1 catalyst per crystal, and each one comes back full. With less catalyst than Dull crystals, the first ones recharge and the rest wait for the next cycle.

The catalyst slot holds one kind at a time: to switch catalysts, wait for the current one to run out or take it out
through the screen. The Crystal Port only hands the Dull crystals back when the Core has no catalyst; with catalyst
in it, the Dull crystal stays inside, waiting for the catalyst and the recharge cycle.
```
Rode o gerador: `python scripts/guide/gerar_guia.py` e confira com `git status` que só os `.md` do guia do Ressonador (`signal-quartz`, nos dois idiomas) mudaram. Se o gerador reescrever outros arquivos, `git checkout --` neles.

- [ ] **Step 2: Specs, mapa do código e progresso**

- Spec de 15/10, linha 110: `- **Recarga:** um Opaco por vez; …` vira `- **Recarga:** todos os Opacos pagos pelo catalisador de uma vez (spec de 26/10/2026); com catalisador no Núcleo, a recarga leva `rechargeTicks` e consome 1 por cristal no fim.`
- `CLAUDE.md`: na linha da tabela que lista a lógica pura do Ressonador, acrescente `resonator/RechargeRank.java`; na descrição do `ResonatorBatch`/`ChamberView` (linha do `resonator/`), troque "recarga do Opaco" por "recarga paralela dos Opacos (`RechargeRank`; `ChamberView.rechargers`)" e acrescente `ResonatorParallelRechargeGameTests` (7 testes) à lista dos GameTests do Ressonador.
- `docs/progresso.md`: uma linha no histórico (data de hoje: "Recarga paralela do Núcleo (etapa 1 do Reparador de Cristais): todos os Opacos pagos recarregam de uma vez; spec e plano em `docs/superpowers/...`; GameTests e e2e do Ressonador OK, com os números da execução") e o próximo passo: "etapa 2 do Reparador: o bloco, o block entity, a tela e a energia (spec de 26/10/2026)".

- [ ] **Step 3: Verificação completa**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams`
Expected: tudo verde (a run comum com os 7 GameTests novos a mais).

- [ ] **Step 4: Commit**

```bash
git add scripts/guide/gerar_guia.py src/main/resources/assets/wirelessautomate/guides docs/superpowers/specs/2026-10-15-ressonador-de-sinal-design.md CLAUDE.md docs/progresso.md
git commit -m "Docs: guia, spec e progresso da recarga paralela do Núcleo"
```

---

## Autorrevisão (feita ao escrever)

- **Cobertura da spec (decisão 1 e etapa 1):** recarga paralela com um cronômetro (Task 2); catalisador por cristal e integridade por cristal (Task 2, teste 1); rachado de fora (Task 2, teste 3); catalisador insuficiente (Task 2, teste 2); câmara e tela coerentes (Tasks 3 e 4); guia, JEI (o JEI da Recarga usa só o tempo da config: não muda) e docs (Task 5). Fica para a etapa 2 o Reparador em si.
- **Sem pendências no texto:** todos os trechos de código estão completos; os nomes batem entre tarefas (`RechargeRank.size/includes`, `ChamberView.rechargers()`, `ChamberCrystals.isRecharging/firstRecharging`, `ResonatorBatch.rechargeable`).
- **Risco conhecido:** um Opaco que entra depois de o cronômetro andar entra no mesmo ciclo (o progresso é do Núcleo, não do cristal); já era assim com o progresso por Núcleo e não muda aqui.
