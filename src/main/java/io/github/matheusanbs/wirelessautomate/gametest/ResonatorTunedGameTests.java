package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertInserted;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.crystal;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.formed;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.resonator.TuneCrystalRecipe;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Rebalanceamento do Ressonador, etapa 1: os Cristais Afinados, o rendimento em quartos por tier do Núcleo, o
 * cristal rachado (integridade 0), que não gera, e as receitas de afinar, refazer e do Pó de Ressonância.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorTunedGameTests {
    private ResonatorTunedGameTests() {
    }

    private static ItemStack tuned(RouterTier tier) {
        return new ItemStack(ModItems.TUNED_CRYSTALS.get(tier).get());
    }

    /** Estação e dial juntos: eficiência 100%, para a geração não ser zero. */
    private static void tune(ResonanceCoreBlockEntity core) {
        core.setStationForTest(98.0);
        core.setDial(98.0);
    }

    private static void assertGeneration(GameTestHelper helper, ResonanceCoreBlockEntity core, int quarters, String what) {
        long esperado = CoreMath.perTick(Config.resonatorFePerCrystal(core.tier()), quarters, core.efficiency());
        helper.assertTrue(esperado > 0, "a geração esperada é zero: " + what);
        helper.assertValueEqual(core.generationPerTick(), esperado, what);
    }

    @GameTest(template = "empty7")
    public static void afinadoDoTierRendeCheio(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            tune(core);
            for (int i = 0; i < 4; i++) {
                assertInserted(helper, core, tuned(RouterTier.EMERALD));
            }
            assertGeneration(helper, core, 32, "quatro Afinados Esmeralda, 8 quartos cada");
        });
    }

    @GameTest(template = "empty7")
    public static void lapidadoComumRendeUmQuartoNoElite(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            tune(core);
            for (int i = 0; i < 3; i++) {
                assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            }
            assertGeneration(helper, core, 6, "três Lapidados no Elite, 2 quartos cada");
        });
    }

    @GameTest(template = "empty7")
    public static void afinadoDeTierMenorRendeComoLapidado(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            tune(core);
            assertInserted(helper, core, tuned(RouterTier.ELITE));
            helper.assertValueEqual(ModItems.TUNED_CRYSTALS.get(RouterTier.ELITE).get().yieldQuarters(RouterTier.EMERALD),
                    2, "quartos do Afinado Elite no Esmeralda");
            assertGeneration(helper, core, 2, "um Afinado Elite no Esmeralda");
        });
    }

    @GameTest(template = "empty7")
    public static void basicoAceitaQualquerCristalCheio(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            tune(core);
            assertInserted(helper, core, tuned(RouterTier.ULTIMATE));
            assertGeneration(helper, core, 8, "um Afinado Ultimate no Básico");
        });
    }

    @GameTest(template = "empty7")
    public static void rachadoNaoGera(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            tune(core);
            assertInserted(helper, core, tuned(RouterTier.EMERALD));
            assertInserted(helper, core, tuned(RouterTier.EMERALD));
            // A pilha da câmara é a do Núcleo: racha o segundo com a carga cheia, para só a integridade decidir.
            SignalCrystalItem.setIntegrity(core.crystals().get(1), 0);
            helper.assertTrue(SignalCrystalItem.isCracked(core.crystals().get(1)), "o cristal não rachou");
            helper.assertTrue(SignalCrystalItem.isFull(core.crystals().get(1)), "o rachado perdeu a carga");
            assertGeneration(helper, core, 8, "só o Afinado íntegro gera");
        });
    }

    @GameTest(template = "empty")
    public static void nomeEDicaDoRachado(GameTestHelper helper) {
        int max = Config.crystalIntegrity();
        ItemStack stack = tuned(RouterTier.ELITE);
        helper.assertTrue(!SignalCrystalItem.isCracked(stack), "o cristal novo está rachado");
        helper.assertValueEqual(SignalCrystalItem.integrity(stack), max, "integridade do cristal novo");

        SignalCrystalItem.setIntegrity(stack, max - 3);
        List<Component> lines = tooltip(stack);
        helper.assertTrue(hasLine(lines, "item.wirelessautomate.signal_crystal.tooltip.integrity", max - 3, max),
                "a dica não mostra a integridade: " + lines);
        helper.assertTrue(hasLine(lines, "item.wirelessautomate.signal_crystal.tooltip.tuned"),
                "a dica não diz o tier do Afinado: " + lines);
        helper.assertValueEqual(key(stack.getHoverName()), "item.wirelessautomate.tuned_signal_crystal_elite",
                "nome do Afinado gasto");

        SignalCrystalItem.setIntegrity(stack, 0);
        helper.assertTrue(SignalCrystalItem.isCracked(stack), "integridade 0 não é rachado");
        helper.assertValueEqual(key(stack.getHoverName()), "item.wirelessautomate.tuned_signal_crystal_elite.cracked",
                "nome do Afinado rachado");
        helper.assertTrue(hasLine(tooltip(stack), "item.wirelessautomate.signal_crystal.tooltip.cracked",
                Config.recastCrystals()), "a dica do rachado não diz como refazer");

        ItemStack sinal = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(sinal, 0);
        SignalCrystalItem.setIntegrity(sinal, 0);
        helper.assertValueEqual(key(sinal.getHoverName()), "item.wirelessautomate.signal_crystal.cracked",
                "o rachado ganha do Opaco no nome");

        SignalCrystalItem.setIntegrity(stack, max);
        helper.assertTrue(!stack.has(ModDataComponents.CRYSTAL_INTEGRITY.get()), "a integridade cheia não tirou o componente");
        helper.assertTrue(ItemStack.isSameItemSameComponents(stack, tuned(RouterTier.ELITE)),
                "o cristal refeito não empilha com o novo");
        helper.succeed();
    }

    /** Integridade 0 e carga 0: um cristal rachado como a recarga o deixa. */
    private static ItemStack cracked(ItemStack stack) {
        SignalCrystalItem.setCharge(stack, 0);
        SignalCrystalItem.setIntegrity(stack, 0);
        return stack;
    }

    /** O que a bancada faz com esses itens, nas primeiras casas da grade 3×3; vazio se nenhuma receita casa. */
    private static Optional<ItemStack> craft(GameTestHelper helper, ItemStack... items) {
        List<ItemStack> grade = new ArrayList<>(List.of(items));
        while (grade.size() < 9) {
            grade.add(ItemStack.EMPTY);
        }
        CraftingInput input = CraftingInput.of(3, 3, grade);
        var level = helper.getLevel();
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(holder -> holder.value().assemble(input, level.registryAccess()));
    }

    private static ItemStack signal() {
        return new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
    }

    /** Sai o item, cheio e íntegro, sem componentes: empilha com um novo. */
    private static void assertNew(GameTestHelper helper, Optional<ItemStack> out, ItemStack expected, String what) {
        helper.assertTrue(out.isPresent(), what + ": a receita não casou");
        helper.assertTrue(ItemStack.isSameItemSameComponents(out.get(), expected), what + ": saiu " + out.get()
                + " " + out.get().getComponentsPatch());
        helper.assertValueEqual(out.get().getCount(), expected.getCount(), what + ": contagem");
    }

    @GameTest(template = "empty")
    public static void afinarLapidadoCheio(GameTestHelper helper) {
        ItemStack netherite = new ItemStack(Items.NETHERITE_INGOT);
        assertNew(helper, craft(helper, crystal(true, CrystalCharge.FULL), netherite, netherite),
                tuned(RouterTier.ELITE), "Lapidado + 2 netherite");
        // A integridade gasta do Lapidado não passa para o Afinado: ele sai como um novo.
        ItemStack gasto = crystal(true, CrystalCharge.FULL);
        SignalCrystalItem.setIntegrity(gasto, 2);
        assertNew(helper, craft(helper, netherite, gasto, netherite), tuned(RouterTier.ELITE), "Lapidado com integridade 2");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void afinarRecusaGastoOuRachado(GameTestHelper helper) {
        ItemStack netherite = new ItemStack(Items.NETHERITE_INGOT);
        helper.assertTrue(craft(helper, crystal(true, CrystalCharge.FULL - 1), netherite, netherite).isEmpty(),
                "um Lapidado de carga parcial foi afinado");
        helper.assertTrue(craft(helper, crystal(true, 0), netherite, netherite).isEmpty(), "um Opaco foi afinado");
        ItemStack rachadoCheio = crystal(true, CrystalCharge.FULL);
        SignalCrystalItem.setIntegrity(rachadoCheio, 0);
        helper.assertTrue(craft(helper, rachadoCheio, netherite, netherite).isEmpty(), "um rachado cheio foi afinado");
        helper.assertTrue(craft(helper, cracked(crystal(true, CrystalCharge.FULL)), netherite, netherite).isEmpty(),
                "um rachado foi afinado");
        helper.assertTrue(craft(helper, signal(), netherite, netherite).isEmpty(), "um Cristal de Sinal foi afinado");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reafinarDeTierMenor(GameTestHelper helper) {
        ItemStack esmeralda = new ItemStack(Items.EMERALD_BLOCK);
        ItemStack estrela = new ItemStack(Items.NETHER_STAR);
        assertNew(helper, craft(helper, tuned(RouterTier.ELITE), esmeralda, estrela, esmeralda),
                tuned(RouterTier.EMERALD), "Afinado Elite → Esmeralda");
        assertNew(helper, craft(helper, esmeralda, esmeralda, estrela, crystal(true, CrystalCharge.FULL)),
                tuned(RouterTier.EMERALD), "Lapidado → Esmeralda");
        helper.assertTrue(craft(helper, tuned(RouterTier.EMERALD), esmeralda, estrela, esmeralda).isEmpty(),
                "o Afinado Esmeralda foi afinado de novo para o Esmeralda");
        ItemStack netherite = new ItemStack(Items.NETHERITE_INGOT);
        helper.assertTrue(craft(helper, tuned(RouterTier.EMERALD), netherite, netherite).isEmpty(),
                "o Afinado Esmeralda voltou para o Elite");
        // Sem o Allthemodium, o Ultimate vem da receita sem os materiais do mod, e as do mod não carregam.
        assertNew(helper, craft(helper, tuned(RouterTier.EMERALD), new ItemStack(Items.NETHERITE_BLOCK), estrela,
                new ItemStack(Items.ECHO_SHARD)), tuned(RouterTier.ULTIMATE), "Ultimate sem o ATM");
        for (String tier : List.of("allthemodium", "vibranium", "unobtainium", "ultimate")) {
            helper.assertTrue(helper.getLevel().getRecipeManager()
                    .byKey(WirelessAutomate.id("tuned_signal_crystal_" + tier)).isEmpty(),
                    "a receita com o Allthemodium carregou sem ele: " + tier);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void refazerRachado(GameTestHelper helper) {
        int n = Config.recastCrystals();
        List<ItemStack> grade = new ArrayList<>();
        grade.add(cracked(tuned(RouterTier.EMERALD)));
        for (int i = 0; i < n; i++) {
            grade.add(signal());
        }
        assertNew(helper, craft(helper, grade.toArray(ItemStack[]::new)), tuned(RouterTier.EMERALD),
                "Afinado Esmeralda rachado + " + n + " Cristais de Sinal");
        helper.assertTrue(craft(helper, grade.subList(0, n).toArray(ItemStack[]::new)).isEmpty(),
                "refez com um Cristal de Sinal a menos");
        List<ItemStack> mais = new ArrayList<>(grade);
        mais.add(signal());
        helper.assertTrue(craft(helper, mais.toArray(ItemStack[]::new)).isEmpty(), "refez com um Cristal de Sinal a mais");
        List<ItemStack> gasto = new ArrayList<>(grade);
        gasto.set(1, crystal(false, CrystalCharge.FULL - 1));
        helper.assertTrue(craft(helper, gasto.toArray(ItemStack[]::new)).isEmpty(), "refez com um Cristal de Sinal gasto");
        List<ItemStack> lapidado = new ArrayList<>(grade);
        lapidado.set(1, crystal(true, CrystalCharge.FULL));
        helper.assertTrue(craft(helper, lapidado.toArray(ItemStack[]::new)).isEmpty(), "refez com um Lapidado");
        List<ItemStack> intacto = new ArrayList<>(grade);
        intacto.set(0, tuned(RouterTier.EMERALD));
        helper.assertTrue(craft(helper, intacto.toArray(ItemStack[]::new)).isEmpty(), "refez um cristal sem rachadura");
        List<ItemStack> dois = new ArrayList<>(grade);
        dois.set(1, cracked(signal()));
        helper.assertTrue(craft(helper, dois.toArray(ItemStack[]::new)).isEmpty(), "refez dois rachados de uma vez");
        // O Cristal de Sinal rachado também se refaz, com outros cheios.
        List<ItemStack> sinal = new ArrayList<>(grade);
        sinal.set(0, cracked(signal()));
        assertNew(helper, craft(helper, sinal.toArray(ItemStack[]::new)), signal(), "Cristal de Sinal rachado");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poDeRessonancia(GameTestHelper helper) {
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        ItemStack glowstone = new ItemStack(Items.GLOWSTONE_DUST);
        assertNew(helper, craft(helper, redstone, glowstone, signal()), new ItemStack(ModItems.RESONANCE_DUST.get(), 4),
                "redstone + glowstone + Cristal de Sinal");
        helper.assertTrue(craft(helper, redstone, glowstone, crystal(false, CrystalCharge.FULL - 1)).isEmpty(),
                "o Pó saiu de um Cristal de Sinal gasto");
        helper.succeed();
    }

    /** O stream codec leva a receita ao cliente: a volta casa com os mesmos itens. */
    @GameTest(template = "empty")
    public static void receitaDeAfinarVoltaPeloStream(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(WirelessAutomate.id("tuned_signal_crystal_emerald"));
        helper.assertTrue(holder.isPresent() && holder.get().value() instanceof TuneCrystalRecipe,
                "a receita carregada é a do tipo próprio");
        TuneCrystalRecipe original = (TuneCrystalRecipe) holder.get().value();
        var acesso = helper.getLevel().registryAccess();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), acesso);
        TuneCrystalRecipe.STREAM_CODEC.encode(buf, original);
        TuneCrystalRecipe volta = TuneCrystalRecipe.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(volta.getIngredients().size(), original.getIngredients().size(), "ingredientes");
        helper.assertTrue(ItemStack.matches(volta.getResultItem(acesso), original.getResultItem(acesso)), "resultado");
        List<ItemStack> grade = new ArrayList<>(List.of(tuned(RouterTier.ELITE), new ItemStack(Items.EMERALD_BLOCK),
                new ItemStack(Items.NETHER_STAR), new ItemStack(Items.EMERALD_BLOCK)));
        while (grade.size() < 9) {
            grade.add(ItemStack.EMPTY);
        }
        helper.assertTrue(volta.matches(CraftingInput.of(3, 3, grade), helper.getLevel()), "a volta não casa");
        helper.succeed();
    }

    private static List<Component> tooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
        return lines;
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    private static boolean hasLine(List<Component> lines, String key, Object... args) {
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
                if (args.length == 0) {
                    return true;
                }
                Object[] got = t.getArgs();
                if (got.length != args.length) {
                    continue;
                }
                boolean same = true;
                for (int i = 0; i < args.length; i++) {
                    same &= String.valueOf(args[i]).equals(String.valueOf(got[i]));
                }
                if (same) {
                    return true;
                }
            }
        }
        return false;
    }
}
