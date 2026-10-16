# Ressonador de Sinal, etapa 1: minério e cristais · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** o minério de Quartzo de Sinal nasce no Overworld e dá Cristais de Sinal; o Cristal de Sinal e o Cristal Lapidado têm carga (componente) e ficam Opacos com carga 0; o Lapidado sai de uma receita que só aceita cristais cheios. Tudo testável no jogo sem o gerador.

**Arquitetura:** pacote novo `resonator/`, com a lógica pura da carga (`CrystalCharge`, com JUnit), o item dos cristais (`SignalCrystalItem`, um para os dois tipos) e a receita do Lapidado (`CutCrystalRecipe`, um `ShapedRecipe` de tipo próprio). O minério é um `DropExperienceBlock` do vanilla; a geração é só por dados (feature, placed feature, biome modifier do NeoForge). Texturas e modelos saem dos scripts de sempre.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), ModDevGradle, JUnit 5, GameTests, Python 3 + PIL (texturas).

**Spec:** [`docs/superpowers/specs/2026-10-15-ressonador-de-sinal-design.md`](../specs/2026-10-15-ressonador-de-sinal-design.md), seções "Cristais", "Minério", "Identidade" e "Etapas" (etapa 1). Mockup aprovado: `docs/preview/ressonador.html`; protótipo das texturas: `docs/preview/ressonador-proto.py`.

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; textos nos dois `lang` (`en_us.json` e `pt_br.json`), sempre os dois.
- Chaves de NBT, componentes e config existentes **não mudam**. Componente novo: `wirelessautomate:crystal_charge` (inteiro de 0 a 1.000.000; sem componente = cheio).
- Ids novos: blocos e itens `signal_quartz_ore` e `deepslate_signal_quartz_ore`; itens `signal_crystal` e `cut_signal_crystal`; tipo de receita `wirelessautomate:cut_crystal`.
- Nomes: "Minério de Quartzo de Sinal" / "Signal Quartz Ore"; "Minério de Quartzo de Sinal de Ardósia" / "Deepslate Signal Quartz Ore"; "Cristal de Sinal" / "Signal Crystal"; "Cristal Lapidado" / "Cut Signal Crystal"; opacos "Cristal de Sinal Opaco" / "Dull Signal Crystal" e "Cristal Lapidado Opaco" / "Dull Cut Signal Crystal".
- `ModPayloads.VERSION` continua `"16"` (nenhum payload muda).
- Nada de config nova nesta etapa: a duração dos cristais (12.000 e 24.000 ticks) entra na config na etapa 2, com o Núcleo que gasta.
- Performance: nada de tick por bloco (esta etapa não tem tick nenhum).
- Texturas: edite os scripts (`scripts/textures/gerar_texturas.py`, `scripts/textures/blocos.py`), nunca os PNGs. Paleta do cristal `#FFF0EA #FFC9B8 #F2917A #C9604B #8A3A2C`; opaco `#F4F0EA #D9D2C8 #B5ADA2 #8C857C #5E5952`.
- Lógica pura sem classes do Minecraft vai com JUnit em `src/test/java`; o que depende do jogo vai com GameTest (`@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, template `"empty"`).
- Comentários: rode `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados antes de cada commit e resolva os candidatos. Tamanho: `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` (Git Bash, na raiz). Compile antes (`./gradlew compileJava`); se aparecer `BUILD FAILED`, pare e conserte. Nunca rode dois jogos ao mesmo tempo.
- Commits sem linhas de atribuição (`Co-Authored-By`, `Claude-Session`), com o autor do dono. Não faça push.

## Mapa de arquivos

Base: `src/main/java/io/github/matheusanbs/wirelessautomate/` (abaixo, `B/`) e `src/main/resources/` (abaixo, `R/`).

| Arquivo | Papel |
| --- | --- |
| `B/resonator/CrystalCharge.java` (novo) | Lógica pura da carga: cheio, opaco, gasto por ticks, porcentagem |
| `src/test/java/io/github/matheusanbs/wirelessautomate/resonator/CrystalChargeTest.java` (novo) | JUnit da carga |
| `B/resonator/SignalCrystalItem.java` (novo) | Item dos dois cristais: carga no componente, nome e dica do Opaco, fator de rendimento |
| `B/resonator/CutCrystalRecipe.java` (novo) | Receita do Lapidado: `ShapedRecipe` que só aceita cristais cheios |
| `B/registry/ModDataComponents.java` (mudar) | `CRYSTAL_CHARGE` |
| `B/registry/ModItems.java`, `ModBlocks.java`, `ModCreativeTabs.java`, `ModRecipes.java` (mudar) | Registros e aba criativa |
| `B/client/ClientSetup.java` (mudar) | Predicado `wirelessautomate:dull` dos dois cristais |
| `B/gametest/ResonatorCrystalGameTests.java` (novo) | GameTests de itens, minério, geração e receita |
| `R/data/wirelessautomate/loot_table/blocks/*signal_quartz_ore.json` (novos) | Drops do minério |
| `R/data/wirelessautomate/worldgen/...`, `R/data/wirelessautomate/neoforge/biome_modifier/...` (novos) | Geração no Overworld |
| `R/data/minecraft/tags/block/...`, `R/data/c/tags/{block,item}/...` (novos e mudar) | Ferramenta e tags `c:` |
| `R/data/wirelessautomate/recipe/cut_signal_crystal.json` (novo) | A receita |
| `R/assets/wirelessautomate/lang/en_us.json`, `pt_br.json` (mudar) | Textos |
| `scripts/textures/gerar_texturas.py`, `scripts/textures/blocos.py` (mudar) + PNGs, modelos e blockstates gerados | Sprites e modelos |
| `CLAUDE.md`, `docs/progresso.md`, `docs/especificacao.md` (mudar) | Documentação |

---

### Task 1: A carga do cristal

**Files:**
- Create: `B/resonator/CrystalCharge.java`
- Create: `src/test/java/io/github/matheusanbs/wirelessautomate/resonator/CrystalChargeTest.java`
- Modify: `B/registry/ModDataComponents.java`

**Interfaces:**
- Produces: `CrystalCharge.FULL` (`int`, 1.000.000); `CrystalCharge.clamp(int) → int`; `CrystalCharge.isDull(int) → boolean`; `CrystalCharge.drain(int charge, long ticks, long lifeTicks) → int`; `CrystalCharge.percent(int) → int`; `ModDataComponents.CRYSTAL_CHARGE` (`Supplier<DataComponentType<Integer>>`, id `crystal_charge`).

- [ ] **Step 1: Escreva o teste que falha**

```java
package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrystalChargeTest {
    @Test
    void cristalCheioAcabaExatamenteNaVidaEmLotesDe20Ticks() {
        int carga = CrystalCharge.FULL;
        for (int lote = 0; lote < 599; lote++) {
            carga = CrystalCharge.drain(carga, 20, 12_000);
        }
        assertFalse(CrystalCharge.isDull(carga), "ainda tem carga no lote 599");
        carga = CrystalCharge.drain(carga, 20, 12_000);
        assertTrue(CrystalCharge.isDull(carga), "acaba no lote 600 = 12.000 ticks");
        assertEquals(0, carga);
    }

    @Test
    void lapidadoDuraODobro() {
        int carga = CrystalCharge.FULL;
        for (int lote = 0; lote < 1_199; lote++) {
            carga = CrystalCharge.drain(carga, 20, 24_000);
        }
        assertFalse(CrystalCharge.isDull(carga));
        assertEquals(0, CrystalCharge.drain(carga, 20, 24_000));
    }

    @Test
    void semTicksOuSemVidaNaoGasta() {
        assertEquals(500_000, CrystalCharge.drain(500_000, 0, 12_000));
        assertEquals(500_000, CrystalCharge.drain(500_000, 20, 0));
    }

    @Test
    void cargaForaDaFaixaVoltaParaDentro() {
        assertEquals(0, CrystalCharge.clamp(-5));
        assertEquals(CrystalCharge.FULL, CrystalCharge.clamp(CrystalCharge.FULL + 1));
        assertEquals(0, CrystalCharge.drain(10, 20, 12_000));
    }

    @Test
    void porcentagemArredondaParaCima() {
        assertEquals(100, CrystalCharge.percent(CrystalCharge.FULL));
        assertEquals(50, CrystalCharge.percent(500_000));
        assertEquals(1, CrystalCharge.percent(1), "com carga nunca mostra 0%");
        assertEquals(0, CrystalCharge.percent(0));
    }
}
```

- [ ] **Step 2: Rode e veja falhar**

Run: `./gradlew test --tests '*CrystalChargeTest'`
Expected: falha de compilação (`CrystalCharge` não existe).

- [ ] **Step 3: Implemente**

```java
package io.github.matheusanbs.wirelessautomate.resonator;

/**
 * A carga de um Cristal de Sinal ou Lapidado, de 0 (Opaco) a {@link #FULL} (cheio). A carga é uma fração
 * da vida: a duração em ticks vem da config e só muda a velocidade do gasto, então mudar a config não
 * mexe nos cristais já guardados. Lógica pura, sem classes do Minecraft.
 */
public final class CrystalCharge {
    public static final int FULL = 1_000_000;

    private CrystalCharge() {
    }

    public static int clamp(int charge) {
        return Math.max(0, Math.min(FULL, charge));
    }

    public static boolean isDull(int charge) {
        return charge <= 0;
    }

    /**
     * Gasta {@code ticks} de uma vida de {@code lifeTicks}. O gasto arredonda para cima, para o cristal
     * nunca durar mais que a vida; sem ticks ou sem vida, não gasta.
     */
    public static int drain(int charge, long ticks, long lifeTicks) {
        int atual = clamp(charge);
        if (ticks <= 0 || lifeTicks <= 0) {
            return atual;
        }
        long gasto = (ticks * FULL + lifeTicks - 1) / lifeTicks;
        return (int) Math.max(0, atual - gasto);
    }

    /** A porcentagem da dica, arredondada para cima: um cristal com carga nunca mostra 0%. */
    public static int percent(int charge) {
        return (int) (((long) clamp(charge) * 100 + FULL - 1) / FULL);
    }
}
```

- [ ] **Step 4: Rode e veja passar**

Run: `./gradlew test --tests '*CrystalChargeTest'`
Expected: 5 testes passando.

- [ ] **Step 5: Registre o componente**

Em `B/registry/ModDataComponents.java`, junto dos outros, com os imports `net.minecraft.util.ExtraCodecs` e (se faltar) `net.minecraft.network.codec.ByteBufCodecs`:

```java
    /**
     * Carga de um Cristal de Sinal ou Lapidado ({@link io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge}).
     * Sem o componente, o cristal está cheio; com 0, está Opaco.
     */
    public static final Supplier<DataComponentType<Integer>> CRYSTAL_CHARGE =
            DATA_COMPONENTS.registerComponentType("crystal_charge", builder -> builder
                    .persistent(ExtraCodecs.intRange(0, 1_000_000))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
```

- [ ] **Step 6: Compile, comentários e commit**

Run: `./gradlew compileJava test`, depois a varredura de comentários nos dois arquivos novos e no `ModDataComponents.java`.

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/resonator/CrystalCharge.java src/test/java/io/github/matheusanbs/wirelessautomate/resonator/CrystalChargeTest.java src/main/java/io/github/matheusanbs/wirelessautomate/registry/ModDataComponents.java
git commit -m "Ressonador: carga do cristal (CrystalCharge) e o componente crystal_charge"
```

---

### Task 2: Os cristais

**Files:**
- Create: `B/resonator/SignalCrystalItem.java`
- Create: `B/gametest/ResonatorCrystalGameTests.java`
- Modify: `B/registry/ModItems.java`, `B/registry/ModCreativeTabs.java`, `B/client/ClientSetup.java`
- Modify: `R/assets/wirelessautomate/lang/en_us.json`, `R/assets/wirelessautomate/lang/pt_br.json`

**Interfaces:**
- Consumes: `CrystalCharge`, `ModDataComponents.CRYSTAL_CHARGE` (Task 1).
- Produces: `ModItems.SIGNAL_CRYSTAL` e `ModItems.CUT_SIGNAL_CRYSTAL` (`DeferredItem<SignalCrystalItem>`); `SignalCrystalItem.charge(ItemStack) → int`; `SignalCrystalItem.setCharge(ItemStack, int)`; `SignalCrystalItem.isDull(ItemStack) → boolean`; `SignalCrystalItem.isFull(ItemStack) → boolean`; `SignalCrystalItem#isCut() → boolean`; `SignalCrystalItem#yield() → int` (1 ou 2); a classe `ResonatorCrystalGameTests`; o predicado de modelo `wirelessautomate:dull` (0 ou 1).

- [ ] **Step 1: Escreva os GameTests que falham**

```java
package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Etapa 1 do Ressonador de Sinal: os cristais, o minério, a geração e a receita do Lapidado. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorCrystalGameTests {
    private ResonatorCrystalGameTests() {
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static boolean hasLine(List<Component> lines, String translationKey) {
        return lines.stream().anyMatch(line -> key(line).equals(translationKey));
    }

    @GameTest(template = "empty")
    public static void cristalNovoEstaCheioESemComponente(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
        helper.assertValueEqual(SignalCrystalItem.charge(stack), CrystalCharge.FULL, "carga do cristal novo");
        helper.assertTrue(SignalCrystalItem.isFull(stack), "cristal novo está cheio");
        helper.assertFalse(SignalCrystalItem.isDull(stack), "cristal novo não está Opaco");
        SignalCrystalItem.setCharge(stack, 400_000);
        SignalCrystalItem.setCharge(stack, CrystalCharge.FULL);
        helper.assertTrue(ItemStack.isSameItemSameComponents(stack, new ItemStack(ModItems.SIGNAL_CRYSTAL.get())),
                "cheio de novo volta a empilhar com o novo (sem componente)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cargaZeroDeixaOpacoComNomeProprioEEmpilha(GameTestHelper helper) {
        for (Item item : List.of(ModItems.SIGNAL_CRYSTAL.get(), ModItems.CUT_SIGNAL_CRYSTAL.get())) {
            ItemStack a = new ItemStack(item);
            ItemStack b = new ItemStack(item);
            SignalCrystalItem.setCharge(a, 0);
            SignalCrystalItem.setCharge(b, -3);
            helper.assertTrue(SignalCrystalItem.isDull(a), "carga 0 é Opaco");
            helper.assertValueEqual(key(a.getHoverName()), item.getDescriptionId() + ".dull", "nome do Opaco");
            helper.assertTrue(ItemStack.isSameItemSameComponents(a, b), "Opacos do mesmo tipo empilham");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dicaMostraCargaOuRecarga(GameTestHelper helper) {
        Item.TooltipContext ctx = Item.TooltipContext.of(helper.getLevel());
        ItemStack meio = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(meio, 500_000);
        helper.assertTrue(hasLine(meio.getTooltipLines(ctx, null, TooltipFlag.NORMAL),
                "item.wirelessautomate.signal_crystal.tooltip.charge"), "dica com a carga");
        ItemStack opaco = new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(opaco, 0);
        List<Component> linhas = opaco.getTooltipLines(ctx, null, TooltipFlag.NORMAL);
        helper.assertTrue(hasLine(linhas, "item.wirelessautomate.signal_crystal.tooltip.dull"), "dica da recarga");
        helper.assertTrue(hasLine(linhas, "item.wirelessautomate.cut_signal_crystal.tooltip.yield"), "dica do rendimento");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lapidadoRendeODobro(GameTestHelper helper) {
        helper.assertValueEqual(ModItems.SIGNAL_CRYSTAL.get().yield(), 1, "rendimento do Cristal de Sinal");
        helper.assertValueEqual(ModItems.CUT_SIGNAL_CRYSTAL.get().yield(), 2, "rendimento do Lapidado");
        helper.assertTrue(ModItems.CUT_SIGNAL_CRYSTAL.get().isCut(), "o Lapidado é lapidado");
        helper.succeed();
    }
}
```

- [ ] **Step 2: Compile e veja falhar**

Run: `./gradlew compileJava`
Expected: `BUILD FAILED` (`SignalCrystalItem` e `ModItems.SIGNAL_CRYSTAL` não existem).

- [ ] **Step 3: O item**

```java
package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Cristal de Sinal e Cristal Lapidado. A carga fica no componente {@code crystal_charge} (sem ele, cheio);
 * com carga 0 o cristal fica Opaco: o mesmo item, com nome e textura de Opaco, que o Núcleo recarrega.
 */
public class SignalCrystalItem extends Item {
    private final boolean cut;

    public SignalCrystalItem(boolean cut, Properties properties) {
        super(properties);
        this.cut = cut;
    }

    public boolean isCut() {
        return cut;
    }

    /** Quanto o cristal rende no Núcleo: o Lapidado, o dobro. */
    public int yield() {
        return cut ? 2 : 1;
    }

    public static int charge(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CRYSTAL_CHARGE.get(), CrystalCharge.FULL);
    }

    /** Cheio tira o componente, para o cristal recarregado empilhar com o novo. */
    public static void setCharge(ItemStack stack, int charge) {
        int valor = CrystalCharge.clamp(charge);
        if (valor == CrystalCharge.FULL) {
            stack.remove(ModDataComponents.CRYSTAL_CHARGE.get());
        } else {
            stack.set(ModDataComponents.CRYSTAL_CHARGE.get(), valor);
        }
    }

    public static boolean isDull(ItemStack stack) {
        return stack.getItem() instanceof SignalCrystalItem && CrystalCharge.isDull(charge(stack));
    }

    public static boolean isFull(ItemStack stack) {
        return stack.getItem() instanceof SignalCrystalItem && charge(stack) == CrystalCharge.FULL;
    }

    @Override
    public Component getName(ItemStack stack) {
        return isDull(stack) ? Component.translatable(getDescriptionId() + ".dull") : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        int carga = charge(stack);
        if (CrystalCharge.isDull(carga)) {
            lines.add(Component.translatable("item.wirelessautomate.signal_crystal.tooltip.dull")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("item.wirelessautomate.signal_crystal.tooltip.charge",
                    CrystalCharge.percent(carga)).withStyle(ChatFormatting.GRAY));
        }
        if (cut) {
            lines.add(Component.translatable("item.wirelessautomate.cut_signal_crystal.tooltip.yield")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
```

- [ ] **Step 4: Registros, aba criativa e predicado**

Em `B/registry/ModItems.java` (import `io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem`), depois do `RANGE_UPGRADE`:

```java
    /** Cristais do Ressonador de Sinal: o de Sinal (rende 1×) e o Lapidado (rende 2× e dura o dobro). */
    public static final DeferredItem<SignalCrystalItem> SIGNAL_CRYSTAL = ITEMS.registerItem("signal_crystal",
            props -> new SignalCrystalItem(false, props), new Item.Properties());
    public static final DeferredItem<SignalCrystalItem> CUT_SIGNAL_CRYSTAL = ITEMS.registerItem("cut_signal_crystal",
            props -> new SignalCrystalItem(true, props), new Item.Properties());
```

Em `B/registry/ModCreativeTabs.java`, depois de `output.accept(ModItems.RANGE_UPGRADE.get());`:

```java
                        output.accept(ModItems.SIGNAL_CRYSTAL.get());
                        output.accept(ModItems.CUT_SIGNAL_CRYSTAL.get());
```

Em `B/client/ClientSetup.java`, dentro do `event.enqueueWork(() -> { ... })` do `clientSetup`, depois do laço dos armazenamentos (import `io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem`), e acrescente ao javadoc do método uma frase sobre o `wirelessautomate:dull`:

```java
            for (var crystal : List.of(ModItems.SIGNAL_CRYSTAL, ModItems.CUT_SIGNAL_CRYSTAL)) {
                ItemProperties.register(crystal.get(), WirelessAutomate.id("dull"),
                        (stack, level, entity, seed) -> SignalCrystalItem.isDull(stack) ? 1F : 0F);
            }
```

(Import `java.util.List` se faltar.)

- [ ] **Step 5: Textos nos dois idiomas**

`en_us.json`:

```json
  "item.wirelessautomate.signal_crystal": "Signal Crystal",
  "item.wirelessautomate.signal_crystal.dull": "Dull Signal Crystal",
  "item.wirelessautomate.cut_signal_crystal": "Cut Signal Crystal",
  "item.wirelessautomate.cut_signal_crystal.dull": "Dull Cut Signal Crystal",
  "item.wirelessautomate.signal_crystal.tooltip.charge": "Charge: %s%%",
  "item.wirelessautomate.signal_crystal.tooltip.dull": "Recharges with redstone in a Resonance Core",
  "item.wirelessautomate.cut_signal_crystal.tooltip.yield": "Yields twice as much and lasts twice as long",
```

`pt_br.json`:

```json
  "item.wirelessautomate.signal_crystal": "Cristal de Sinal",
  "item.wirelessautomate.signal_crystal.dull": "Cristal de Sinal Opaco",
  "item.wirelessautomate.cut_signal_crystal": "Cristal Lapidado",
  "item.wirelessautomate.cut_signal_crystal.dull": "Cristal Lapidado Opaco",
  "item.wirelessautomate.signal_crystal.tooltip.charge": "Carga: %s%%",
  "item.wirelessautomate.signal_crystal.tooltip.dull": "Recarrega com redstone no Núcleo de Ressonância",
  "item.wirelessautomate.cut_signal_crystal.tooltip.yield": "Rende o dobro e dura o dobro",
```

Ponha as chaves de nome perto das outras `item.wirelessautomate.*` do topo e as de dica perto das outras dicas, nos dois arquivos, na mesma ordem.

- [ ] **Step 6: Rode os testes**

Run: `./gradlew build runGameTestServer`
Expected: `BUILD SUCCESSFUL`, os 4 GameTests novos passando junto dos antigos.

- [ ] **Step 7: Verificação completa, comentários, tamanho e commit**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`, a varredura de comentários e o `medir.py` nos arquivos mudados.

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/resonator/SignalCrystalItem.java src/main/java/io/github/matheusanbs/wirelessautomate/gametest/ResonatorCrystalGameTests.java src/main/java/io/github/matheusanbs/wirelessautomate/registry/ModItems.java src/main/java/io/github/matheusanbs/wirelessautomate/registry/ModCreativeTabs.java src/main/java/io/github/matheusanbs/wirelessautomate/client/ClientSetup.java src/main/resources/assets/wirelessautomate/lang/en_us.json src/main/resources/assets/wirelessautomate/lang/pt_br.json
git commit -m "Ressonador: Cristal de Sinal e Cristal Lapidado, com carga e o estado Opaco"
```

---

### Task 3: O minério e a geração

**Files:**
- Modify: `B/registry/ModBlocks.java`, `B/registry/ModItems.java`, `B/registry/ModCreativeTabs.java`
- Modify: `B/gametest/ResonatorCrystalGameTests.java`
- Create: `R/data/wirelessautomate/loot_table/blocks/signal_quartz_ore.json`, `R/data/wirelessautomate/loot_table/blocks/deepslate_signal_quartz_ore.json`
- Create: `R/data/wirelessautomate/worldgen/configured_feature/signal_quartz_ore.json`, `R/data/wirelessautomate/worldgen/placed_feature/signal_quartz_ore.json`, `R/data/wirelessautomate/neoforge/biome_modifier/signal_quartz_ore.json`
- Modify: `R/data/minecraft/tags/block/mineable/pickaxe.json`
- Create: `R/data/minecraft/tags/block/needs_iron_tool.json`
- Create: `R/data/c/tags/block/ores.json`, `R/data/c/tags/block/ores/signal_quartz.json`, `R/data/c/tags/block/ores_in_ground/stone.json`, `R/data/c/tags/block/ores_in_ground/deepslate.json` e os mesmos quatro em `R/data/c/tags/item/`
- Modify: `R/assets/wirelessautomate/lang/en_us.json`, `pt_br.json`

**Interfaces:**
- Consumes: `ModItems.SIGNAL_CRYSTAL` (Task 2).
- Produces: `ModBlocks.SIGNAL_QUARTZ_ORE`, `ModBlocks.DEEPSLATE_SIGNAL_QUARTZ_ORE` (`DeferredBlock<DropExperienceBlock>`); `ModItems.SIGNAL_QUARTZ_ORE`, `ModItems.DEEPSLATE_SIGNAL_QUARTZ_ORE` (`DeferredItem<BlockItem>`); a feature e a placed feature `wirelessautomate:signal_quartz_ore`.

- [ ] **Step 1: Escreva os GameTests que falham**

Acrescente a `ResonatorCrystalGameTests` (imports: `io.github.matheusanbs.wirelessautomate.registry.ModBlocks`, `net.minecraft.core.BlockPos`, `net.minecraft.core.Holder`, `net.minecraft.core.HolderSet`, `net.minecraft.core.registries.Registries`, `net.minecraft.resources.ResourceKey`, `net.minecraft.server.level.ServerLevel`, `net.minecraft.world.item.Items`, `net.minecraft.world.item.enchantment.Enchantments`, `net.minecraft.world.level.biome.Biomes`, `net.minecraft.world.level.block.Block`, `net.minecraft.world.level.block.state.BlockState`, `net.minecraft.world.level.levelgen.GenerationStep`, `net.minecraft.world.level.levelgen.placement.PlacedFeature`):

```java
    private static List<ItemStack> drops(GameTestHelper helper, BlockState state, ItemStack tool) {
        ServerLevel level = helper.getLevel();
        return Block.getDrops(state, level, helper.absolutePos(new BlockPos(1, 1, 1)), null, null, tool);
    }

    @GameTest(template = "empty")
    public static void minerioDaDeUmATresCristais(GameTestHelper helper) {
        for (var ore : List.of(ModBlocks.SIGNAL_QUARTZ_ORE, ModBlocks.DEEPSLATE_SIGNAL_QUARTZ_ORE)) {
            for (int i = 0; i < 30; i++) {
                List<ItemStack> saida = drops(helper, ore.get().defaultBlockState(), new ItemStack(Items.IRON_PICKAXE));
                helper.assertValueEqual(saida.size(), 1, "uma pilha por quebra");
                ItemStack pilha = saida.get(0);
                helper.assertTrue(pilha.is(ModItems.SIGNAL_CRYSTAL.get()), "o minério dá Cristal de Sinal");
                helper.assertTrue(pilha.getCount() >= 1 && pilha.getCount() <= 3, "de 1 a 3 cristais: " + pilha.getCount());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void toqueDeSedaDaOMinerio(GameTestHelper helper) {
        ItemStack picareta = new ItemStack(Items.IRON_PICKAXE);
        picareta.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH), 1);
        List<ItemStack> saida = drops(helper, ModBlocks.SIGNAL_QUARTZ_ORE.get().defaultBlockState(), picareta);
        helper.assertValueEqual(saida.size(), 1, "uma pilha");
        helper.assertTrue(saida.get(0).is(ModItems.SIGNAL_QUARTZ_ORE.get()), "Toque de Seda dá o minério");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void minerioPedePicaretaDeFerro(GameTestHelper helper) {
        BlockState ore = ModBlocks.SIGNAL_QUARTZ_ORE.get().defaultBlockState();
        helper.assertTrue(ore.requiresCorrectToolForDrops(), "precisa de ferramenta");
        helper.assertFalse(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(ore), "pedra não serve");
        helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(ore), "ferro serve");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void minerioEntraNosBiomasDoOverworld(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        ResourceKey<PlacedFeature> chave = ResourceKey.create(Registries.PLACED_FEATURE, WirelessAutomate.id("signal_quartz_ore"));
        helper.assertTrue(access.registryOrThrow(Registries.PLACED_FEATURE).containsKey(chave), "placed feature registrada");
        var planicie = access.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS).value();
        List<HolderSet<PlacedFeature>> etapas = planicie.getGenerationSettings().features();
        int minerios = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
        boolean presente = etapas.size() > minerios
                && etapas.get(minerios).stream().anyMatch((Holder<PlacedFeature> h) -> h.is(chave));
        helper.assertTrue(presente, "o biome modifier põe o minério nas Planícies, na etapa dos minérios");
        helper.succeed();
    }
```

- [ ] **Step 2: Compile e veja falhar**

Run: `./gradlew compileJava`
Expected: `BUILD FAILED` (`ModBlocks.SIGNAL_QUARTZ_ORE` não existe).

- [ ] **Step 3: Blocos, itens e aba criativa**

Em `B/registry/ModBlocks.java` (imports `net.minecraft.util.valueproviders.UniformInt`, `net.minecraft.world.level.block.DropExperienceBlock`, `net.minecraft.world.level.block.state.properties.NoteBlockInstrument`):

```java
    /** Minério de Quartzo de Sinal, na pedra e na ardósia: dá Cristais de Sinal (geração em data/.../worldgen). */
    public static final DeferredBlock<DropExperienceBlock> SIGNAL_QUARTZ_ORE = BLOCKS.registerBlock("signal_quartz_ore",
            props -> new DropExperienceBlock(UniformInt.of(2, 5), props),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 3.0F));
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_SIGNAL_QUARTZ_ORE = BLOCKS.registerBlock(
            "deepslate_signal_quartz_ore",
            props -> new DropExperienceBlock(UniformInt.of(2, 5), props),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(4.5F, 3.0F)
                    .sound(SoundType.DEEPSLATE));
```

Em `B/registry/ModItems.java` (import `net.minecraft.world.item.BlockItem`), antes dos cristais:

```java
    public static final DeferredItem<BlockItem> SIGNAL_QUARTZ_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.SIGNAL_QUARTZ_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_SIGNAL_QUARTZ_ORE =
            ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_SIGNAL_QUARTZ_ORE);
```

Em `B/registry/ModCreativeTabs.java`, antes dos cristais:

```java
                        output.accept(ModItems.SIGNAL_QUARTZ_ORE.get());
                        output.accept(ModItems.DEEPSLATE_SIGNAL_QUARTZ_ORE.get());
```

- [ ] **Step 4: Drops**

`R/data/wirelessautomate/loot_table/blocks/signal_quartz_ore.json` (no da ardósia, troque os dois `signal_quartz_ore` por `deepslate_signal_quartz_ore`, inclusive no `random_sequence`):

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "bonus_rolls": 0,
      "entries": [
        {
          "type": "minecraft:alternatives",
          "children": [
            {
              "type": "minecraft:item",
              "name": "wirelessautomate:signal_quartz_ore",
              "conditions": [
                {
                  "condition": "minecraft:match_tool",
                  "predicate": {
                    "predicates": {
                      "minecraft:enchantments": [
                        { "enchantments": "minecraft:silk_touch", "levels": { "min": 1 } }
                      ]
                    }
                  }
                }
              ]
            },
            {
              "type": "minecraft:item",
              "name": "wirelessautomate:signal_crystal",
              "functions": [
                {
                  "function": "minecraft:set_count",
                  "count": { "type": "minecraft:uniform", "min": 1.0, "max": 3.0 },
                  "add": false
                },
                {
                  "function": "minecraft:apply_bonus",
                  "enchantment": "minecraft:fortune",
                  "formula": "minecraft:ore_drops"
                },
                { "function": "minecraft:explosion_decay" }
              ]
            }
          ]
        }
      ]
    }
  ],
  "random_sequence": "wirelessautomate:blocks/signal_quartz_ore"
}
```

- [ ] **Step 5: Geração**

`R/data/wirelessautomate/worldgen/configured_feature/signal_quartz_ore.json`:

```json
{
  "type": "minecraft:ore",
  "config": {
    "size": 7,
    "discard_chance_on_air_exposure": 0.0,
    "targets": [
      {
        "target": { "predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables" },
        "state": { "Name": "wirelessautomate:signal_quartz_ore" }
      },
      {
        "target": { "predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables" },
        "state": { "Name": "wirelessautomate:deepslate_signal_quartz_ore" }
      }
    ]
  }
}
```

`R/data/wirelessautomate/worldgen/placed_feature/signal_quartz_ore.json`:

```json
{
  "feature": "wirelessautomate:signal_quartz_ore",
  "placement": [
    { "type": "minecraft:count", "count": 6 },
    { "type": "minecraft:in_square" },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:trapezoid",
        "min_inclusive": { "absolute": -64 },
        "max_inclusive": { "absolute": 32 }
      }
    },
    { "type": "minecraft:biome" }
  ]
}
```

`R/data/wirelessautomate/neoforge/biome_modifier/signal_quartz_ore.json`:

```json
{
  "type": "neoforge:add_features",
  "biomes": "#minecraft:is_overworld",
  "features": "wirelessautomate:signal_quartz_ore",
  "step": "underground_ores"
}
```

- [ ] **Step 6: Tags**

Em `R/data/minecraft/tags/block/mineable/pickaxe.json`, acrescente ao `values` `"wirelessautomate:signal_quartz_ore"` e `"wirelessautomate:deepslate_signal_quartz_ore"`.

`R/data/minecraft/tags/block/needs_iron_tool.json`:

```json
{
  "values": [
    "wirelessautomate:signal_quartz_ore",
    "wirelessautomate:deepslate_signal_quartz_ore"
  ]
}
```

Em `R/data/c/tags/block/` e em `R/data/c/tags/item/` (os mesmos quatro arquivos nas duas pastas):

- `ores.json`: `{ "values": ["#c:ores/signal_quartz"] }`
- `ores/signal_quartz.json`: `{ "values": ["wirelessautomate:signal_quartz_ore", "wirelessautomate:deepslate_signal_quartz_ore"] }`
- `ores_in_ground/stone.json`: `{ "values": ["wirelessautomate:signal_quartz_ore"] }`
- `ores_in_ground/deepslate.json`: `{ "values": ["wirelessautomate:deepslate_signal_quartz_ore"] }`

- [ ] **Step 7: Textos**

`en_us.json`: `"block.wirelessautomate.signal_quartz_ore": "Signal Quartz Ore"`, `"block.wirelessautomate.deepslate_signal_quartz_ore": "Deepslate Signal Quartz Ore"`.
`pt_br.json`: `"block.wirelessautomate.signal_quartz_ore": "Minério de Quartzo de Sinal"`, `"block.wirelessautomate.deepslate_signal_quartz_ore": "Minério de Quartzo de Sinal de Ardósia"`.
Perto das outras chaves `block.wirelessautomate.*`, na mesma ordem nos dois.

- [ ] **Step 8: Rode os testes**

Run: `./gradlew build runGameTestServer`
Expected: `BUILD SUCCESSFUL`, os 8 GameTests de `ResonatorCrystalGameTests` passando. Se `minerioEntraNosBiomasDoOverworld` falhar, confira o caminho do biome modifier (`data/wirelessautomate/neoforge/biome_modifier/`) antes de mexer no teste.

- [ ] **Step 9: Verificação completa, comentários, tamanho e commit**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`, a varredura e o `medir.py`.

```bash
git add src/main/java src/main/resources/data src/main/resources/assets/wirelessautomate/lang
git commit -m "Ressonador: minério de Quartzo de Sinal (pedra e ardósia), drops e geração no Overworld"
```

---

### Task 4: Texturas e modelos

**Files:**
- Modify: `scripts/textures/gerar_texturas.py` (sprites e modelos dos cristais)
- Modify: `scripts/textures/blocos.py` (sprites, modelos e blockstates do minério)
- Gerados: `R/assets/wirelessautomate/textures/item/{signal_crystal,signal_crystal_dull,cut_signal_crystal,cut_signal_crystal_dull}.png` (+ `.mcmeta`), `textures/block/{signal_quartz_ore,deepslate_signal_quartz_ore}.png` (+ `.mcmeta`), `models/item/*.json`, `models/block/*.json`, `blockstates/*.json`, `docs/preview/folha-de-sprites.png`

**Interfaces:**
- Consumes: o predicado `wirelessautomate:dull` (Task 2); os ids dos blocos e itens (Tasks 2 e 3).
- Produces: as texturas e modelos; nenhuma API Java.

- [ ] **Step 1: Os cristais em `gerar_texturas.py`**

Leve para `gerar_texturas.py` as funções `cristal()`, `lapidado()` e `opaco()` de `docs/preview/ressonador-proto.py` (com as grades `BRUTO` e `LAPIDADO`, as paletas `SINAL` e `OPACO`, `leg`, `base`, `interior_px` e `put` que elas usam; renomeie para não colidir com o `put` e o `LEG` que já existem, por exemplo `CRISTAL_SINAL`, `CRISTAL_OPACO`, `_leg_cristal`, `_cristal_base`). Mantenha os quadros e frametimes do protótipo: Cristal de Sinal 10 × 4, Lapidado 12 × 3, Opaco 8 × 6. Generalize `opaco()` para receber a grade (`opaco(BRUTO)` e `opaco(LAPIDADO)`), porque o Lapidado tem o seu Opaco. Em `gerar_itens()`:

```python
    sprites["item/signal_crystal"] = cristal()
    sprites["item/cut_signal_crystal"] = lapidado()
    sprites["item/signal_crystal_dull"] = opaco(BRUTO)
    sprites["item/cut_signal_crystal_dull"] = opaco(LAPIDADO)
```

Em `modelos_itens()`:

```python
    for nome in ("signal_crystal", "cut_signal_crystal"):
        grava_json(MODELOS / f"models/item/{nome}_dull.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"wirelessautomate:item/{nome}_dull"},
        })
        grava_json(MODELOS / f"models/item/{nome}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"wirelessautomate:item/{nome}"},
            "overrides": [{"predicate": {"wirelessautomate:dull": 1}, "model": f"wirelessautomate:item/{nome}_dull"}],
        })
```

- [ ] **Step 2: O minério em `blocos.py`**

Leve `minerio(tons, semente)` com `PEDRA`, `ARDOSIA`, `CRIST` e `POS` de `docs/preview/ressonador-proto.py` para `blocos.py` (12 quadros × 4 ticks). Em `sprites()`:

```python
    sp["block/signal_quartz_ore"] = minerio(PEDRA, 3)
    sp["block/deepslate_signal_quartz_ore"] = minerio(ARDOSIA, 7)
```

(use o nome do dicionário que `sprites()` já usa). Em `modelos()`, para cada um dos dois nomes:

```python
    for nome in ("signal_quartz_ore", "deepslate_signal_quartz_ore"):
        grava_json(MODELOS / f"models/block/{nome}.json", {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": f"wirelessautomate:block/{nome}"},
        })
        grava_json(MODELOS / f"blockstates/{nome}.json", {"variants": {"": {"model": f"wirelessautomate:block/{nome}"}}})
        grava_json(MODELOS / f"models/item/{nome}.json", {"parent": f"wirelessautomate:block/{nome}"})
```

Se `blocos.modelos()` recebe a lista de texturas usadas (`texturas`), inclua as duas para o `limpar` não apagá-las.

- [ ] **Step 3: Gere e confira**

Run: `python -I scripts/textures/gerar_texturas.py`
Expected: a linha final com a contagem de sprites, sem erro de `validar` (alfa só 0 ou 255 nos itens e no minério). Abra `docs/preview/folha-de-sprites.png` e confira: os quatro cristais e os dois minérios aparecem, iguais à prancha `docs/preview/ressonador-prancha.png` e ao protótipo; o Opaco do Lapidado tem a forma do Lapidado. Confira que nenhum PNG antigo sumiu (`git status` só com arquivos novos e os modelos dos cristais e do minério).

- [ ] **Step 4: Confira no jogo**

Run: `./gradlew runClient` e, num mundo criativo: os quatro itens na aba do mod com as animações; `/give @s wirelessautomate:signal_crystal[wirelessautomate:crystal_charge=0]` mostra o Opaco com o nome "Cristal de Sinal Opaco" e a dica da recarga; `/give @s wirelessautomate:cut_signal_crystal[wirelessautomate:crystal_charge=500000]` mostra "Carga: 50%"; o minério colocado anima. Feche o cliente antes de seguir (memória).

- [ ] **Step 5: Verificação completa e commit**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`, a varredura de comentários nos dois scripts.

```bash
git add scripts/textures src/main/resources/assets docs/preview/folha-de-sprites.png
git commit -m "Ressonador: texturas animadas e modelos dos cristais (com o Opaco) e do minério"
```

---

### Task 5: A receita do Lapidado e a documentação

**Files:**
- Create: `B/resonator/CutCrystalRecipe.java`
- Modify: `B/registry/ModRecipes.java`
- Create: `R/data/wirelessautomate/recipe/cut_signal_crystal.json`
- Modify: `B/gametest/ResonatorCrystalGameTests.java`
- Modify: `CLAUDE.md`, `docs/progresso.md`, `docs/especificacao.md`

**Interfaces:**
- Consumes: `SignalCrystalItem.isFull(ItemStack)` (Task 2), `ModItems.SIGNAL_CRYSTAL`, `ModItems.CUT_SIGNAL_CRYSTAL`.
- Produces: `ModRecipes.CUT_CRYSTAL` (`Supplier<RecipeSerializer<CutCrystalRecipe>>`, id `cut_crystal`).

- [ ] **Step 1: Escreva os GameTests que falham**

Acrescente a `ResonatorCrystalGameTests` (imports `java.util.ArrayList`, `java.util.Optional`, `net.minecraft.world.item.crafting.CraftingInput`, `net.minecraft.world.item.crafting.CraftingRecipe`, `net.minecraft.world.item.crafting.RecipeHolder`, `net.minecraft.world.item.crafting.RecipeType`):

```java
    /** A cruz: cristais em cima, à esquerda, à direita e embaixo; o fragmento de ametista no meio. */
    private static CraftingInput cruz(ItemStack... cristais) {
        List<ItemStack> grade = new ArrayList<>(List.of(ItemStack.EMPTY, cristais[0], ItemStack.EMPTY,
                cristais[1], new ItemStack(Items.AMETHYST_SHARD), cristais[2],
                ItemStack.EMPTY, cristais[3], ItemStack.EMPTY));
        return CraftingInput.of(3, 3, grade);
    }

    private static ItemStack cristal(int carga) {
        ItemStack stack = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(stack, carga);
        return stack;
    }

    private static Optional<RecipeHolder<CraftingRecipe>> receita(GameTestHelper helper, CraftingInput input) {
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    }

    @GameTest(template = "empty")
    public static void quatroCristaisCheiosViramUmLapidado(GameTestHelper helper) {
        int cheio = CrystalCharge.FULL;
        CraftingInput input = cruz(cristal(cheio), cristal(cheio), cristal(cheio), cristal(cheio));
        var achada = receita(helper, input);
        helper.assertTrue(achada.isPresent(), "a receita do Lapidado existe");
        ItemStack saida = achada.get().value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(saida.is(ModItems.CUT_SIGNAL_CRYSTAL.get()), "sai um Cristal Lapidado");
        helper.assertValueEqual(saida.getCount(), 1, "um só");
        helper.assertTrue(SignalCrystalItem.isFull(saida), "sai cheio");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cristalGastoOuOpacoNaoViraLapidado(GameTestHelper helper) {
        int cheio = CrystalCharge.FULL;
        helper.assertTrue(receita(helper, cruz(cristal(cheio), cristal(cheio), cristal(cheio), cristal(0))).isEmpty(),
                "um Opaco recusa a receita");
        helper.assertTrue(receita(helper, cruz(cristal(cheio), cristal(999_999), cristal(cheio), cristal(cheio))).isEmpty(),
                "um cristal já usado recusa a receita");
        helper.succeed();
    }
```

- [ ] **Step 2: Compile, rode e veja falhar**

Run: `./gradlew build runGameTestServer`
Expected: os dois testes novos falham (a receita não existe).

- [ ] **Step 3: A receita**

```java
package io.github.matheusanbs.wirelessautomate.resonator;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

/**
 * {@code {"type": "wirelessautomate:cut_crystal"}}: a receita do Cristal Lapidado. Igual a uma receita com
 * forma, mas só aceita Cristais de Sinal cheios: com a receita comum, quatro Opacos virariam um Lapidado
 * cheio. Por ser tipo próprio, fica fora do editor de receitas, como os anéis dos Cartões de Upgrade.
 */
public class CutCrystalRecipe extends ShapedRecipe {
    private final ShapedRecipePattern grade;
    private final ItemStack resultado;

    public CutCrystalRecipe(CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result) {
        super("", category, pattern, result);
        this.grade = pattern;
        this.resultado = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!super.matches(input, level)) {
            return false;
        }
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof SignalCrystalItem && !SignalCrystalItem.isFull(stack)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return resultado.copy();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.CUT_CRYSTAL.get();
    }

    public static final MapCodec<CutCrystalRecipe> MAP_CODEC = RecordCodecBuilder.<CutCrystalRecipe>mapCodec(instance -> instance.group(
                    CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)
                            .forGetter(CutCrystalRecipe::category),
                    ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.grade),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.resultado))
            .apply(instance, CutCrystalRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CutCrystalRecipe> STREAM_CODEC = StreamCodec.composite(
            CraftingBookCategory.STREAM_CODEC, CutCrystalRecipe::category,
            ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.grade,
            ItemStack.STREAM_CODEC, recipe -> recipe.resultado,
            CutCrystalRecipe::new);

    public static final class Serializer implements RecipeSerializer<CutCrystalRecipe> {
        @Override
        public MapCodec<CutCrystalRecipe> codec() {
            return MAP_CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CutCrystalRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
```

Em `B/registry/ModRecipes.java` (import `io.github.matheusanbs.wirelessautomate.resonator.CutCrystalRecipe`):

```java
    /** {@code {"type": "wirelessautomate:cut_crystal"}}: o Cristal Lapidado, só com Cristais de Sinal cheios. */
    public static final Supplier<RecipeSerializer<CutCrystalRecipe>> CUT_CRYSTAL =
            RECIPE_SERIALIZERS.register("cut_crystal", CutCrystalRecipe.Serializer::new);
```

`R/data/wirelessautomate/recipe/cut_signal_crystal.json`:

```json
{
  "type": "wirelessautomate:cut_crystal",
  "category": "misc",
  "pattern": [
    " C ",
    "CAC",
    " C "
  ],
  "key": {
    "C": { "item": "wirelessautomate:signal_crystal" },
    "A": { "item": "minecraft:amethyst_shard" }
  },
  "result": { "id": "wirelessautomate:cut_signal_crystal", "count": 1 }
}
```

- [ ] **Step 4: Rode os testes**

Run: `./gradlew build runGameTestServer`
Expected: `BUILD SUCCESSFUL`, os 10 testes de `ResonatorCrystalGameTests` passando. Confira também que o editor de receitas (`RecipeEditorGameTests`) continua passando: o tipo próprio fica de fora da lista dele.

- [ ] **Step 5: JEI no cliente**

Run: `./gradlew runClient`; no JEI, o Cristal Lapidado mostra a receita na bancada (é um `ShapedRecipe`). Se não mostrar, registre-a no plugin `compat/jei/` do jeito que as receitas do `TierUpgradeRecipe` são registradas lá, e diga isso no relatório. Feche o cliente.

- [ ] **Step 6: Documentação**

- `CLAUDE.md`, no mapa do código: uma linha para `resonator/` ("Ressonador de Sinal (gerador multibloco; spec `docs/superpowers/specs/2026-10-15-ressonador-de-sinal-design.md`): `CrystalCharge` (carga do cristal, pura com JUnit), `SignalCrystalItem` (Cristal de Sinal e Lapidado, carga no componente `crystal_charge`, Opaco com carga 0 pelo predicado `wirelessautomate:dull`) e `CutCrystalRecipe` (`wirelessautomate:cut_crystal`, só cristais cheios). O minério (`signal_quartz_ore`, `deepslate_signal_quartz_ore`) é um `DropExperienceBlock` do vanilla, com a geração só por dados"); na linha do `gametest/`, o `ResonatorCrystalGameTests` e a nova contagem de testes; acrescente `resonator/CrystalCharge.java` à lista das classes de lógica pura.
- `docs/especificacao.md`: no roadmap, um item "Ressonador de Sinal (gerador multibloco)" com a etapa 1 marcada e um link para a spec.
- `docs/progresso.md`: na tabela de estado, uma linha "Ressonador de Sinal" (🟡, etapa 1 pronta: minério, cristais e receita; falta a estrutura, o Núcleo, a tela e a câmara); o próximo passo ("Ressonador, etapa 2: estrutura e Núcleo"); uma linha no histórico com a data, o que foi feito e os números de testes (JUnit e GameTests das quatro runs).

- [ ] **Step 7: Verificação completa e commit**

Run: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`, a varredura de comentários e o `medir.py` nos arquivos mudados.

```bash
git add src/main/java src/main/resources/data/wirelessautomate/recipe/cut_signal_crystal.json CLAUDE.md docs/progresso.md docs/especificacao.md
git commit -m "Ressonador: receita do Cristal Lapidado (só cristais cheios) e documentação da etapa 1"
```
