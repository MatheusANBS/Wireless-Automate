package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.ORIGIN;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertInserted;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.crystal;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.formed;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.hit;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CatalystRule;
import io.github.matheusanbs.wirelessautomate.resonator.CoreCatalyst;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBatch;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Rebalanceamento do Ressonador, etapa 1b: o Pó Carregado, o catalisador do Núcleo do Allthemodium ao Ultimate, que
 * só aceita o do próprio tier (no menu, na Porta e no clique). Roda na run comum: os itens existem sempre, e o
 * Núcleo do Allthemodium monta sem o mod.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorChargedDustGameTests {
    /** A Porta de Cristais da Vitrine montada em {@link ResonatorTestSupport#ORIGIN}. */
    private static final BlockPos CRYSTAL_PORT = ORIGIN.offset(2, 2, 0);
    private static final int CONTAINER_ID = 1;

    private ResonatorChargedDustGameTests() {
    }

    private static ItemStack charged(RouterTier tier, int count) {
        return new ItemStack(ModItems.CHARGED_DUSTS.get(tier).get(), count);
    }

    private static ItemStack dust(int count) {
        return new ItemStack(ModItems.RESONANCE_DUST.get(), count);
    }

    private static IItemHandler port(GameTestHelper helper) {
        IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(CRYSTAL_PORT), Direction.NORTH);
        helper.assertTrue(items != null, "a Porta de Cristais sem capability");
        return items;
    }

    private static void recharge(GameTestHelper helper, ResonanceCoreBlockEntity core) {
        ResonatorBatch.run(helper.getLevel(), core, (int) Math.min(Config.rechargeTicks(), Integer.MAX_VALUE));
    }

    /** Clica no Núcleo com a pilha e devolve a chave da mensagem da action bar, ou {@code null}. */
    private static String click(GameTestHelper helper, ResonanceCoreBlockEntity core, ItemStack stack) {
        TestPlayers.MenuPlayer player = TestPlayers.survivalMenuOpener(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        core.getBlockState().useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                hit(core.getBlockPos()));
        return player.actionBar != null && player.actionBar.getContents() instanceof TranslatableContents t
                ? t.getKey() : null;
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void ultimateSoAceitaOPoCarregadoDele(GameTestHelper helper) {
        formed(helper, RouterTier.ULTIMATE, core -> {
            int slot = core.chambers();
            ResonatorMenu menu = new ResonatorMenu(CONTAINER_ID, TestPlayers.inLevel(helper).getInventory(), core);
            IItemHandler items = port(helper);
            ItemStack[] refused = {new ItemStack(Items.REDSTONE, 3), dust(3), charged(RouterTier.ALLTHEMODIUM, 3),
                    charged(RouterTier.UNOBTAINIUM, 3)};
            for (ItemStack stack : refused) {
                String name = stack.getItem().toString();
                helper.assertTrue(!CoreCatalyst.accepts(RouterTier.ULTIMATE, stack), "a regra aceita " + name);
                helper.assertTrue(!menu.getSlot(menu.redstoneSlot()).mayPlace(stack), "o menu aceita " + name);
                helper.assertValueEqual(items.insertItem(slot, stack, false).getCount(), 3, "a Porta pôs " + name);
                ItemStack hand = stack.copy();
                helper.assertValueEqual(click(helper, core, hand),
                        "block.wirelessautomate.resonance_core.needs_charged_dust", "a mensagem com " + name);
                helper.assertValueEqual(hand.getCount(), 3, "o clique gastou " + name);
            }
            helper.assertValueEqual(core.redstone(), 0, "algum recusado entrou");

            ItemStack own = charged(RouterTier.ULTIMATE, 2);
            helper.assertTrue(menu.getSlot(menu.redstoneSlot()).mayPlace(own), "o menu recusa o Pó Carregado");
            helper.assertValueEqual(items.insertItem(slot, own.copy(), false).getCount(), 0, "a Porta recusou");
            ItemStack hand = own.copy();
            helper.assertTrue(click(helper, core, hand) == null, "o clique deu mensagem");
            helper.assertValueEqual(hand.getCount(), 0, "o clique não gastou");
            helper.assertValueEqual(core.redstone(), 4, "Pó Carregado no Núcleo");
            helper.assertTrue(core.catalyst().item() == ModItems.CHARGED_DUSTS.get(RouterTier.ULTIMATE).get(),
                    "o catalisador não é o Pó Carregado");
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void allthemodiumSoAceitaOPoCarregadoDele(GameTestHelper helper) {
        formed(helper, RouterTier.ALLTHEMODIUM, core -> {
            helper.assertValueEqual(core.addCatalyst(dust(1)), 0, "o Pó entrou no Allthemodium");
            helper.assertValueEqual(core.addCatalyst(new ItemStack(Items.REDSTONE)), 0, "a redstone entrou");
            helper.assertValueEqual(core.addCatalyst(charged(RouterTier.VIBRANIUM, 1)), 0, "o do Vibranium entrou");
            helper.assertValueEqual(port(helper).insertItem(core.chambers(), charged(RouterTier.ALLTHEMODIUM, 5),
                    false).getCount(), 0, "a Porta recusou o do Allthemodium");
            helper.assertValueEqual(core.redstone(), 5, "Pó Carregado no Núcleo");
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void abaixoDoAllthemodiumRecusaOPoCarregado(GameTestHelper helper) {
        helper.assertValueEqual(CatalystRule.FIRST_DUST_ONLY, RouterTier.ELITE.ordinal(), "a posição do Elite");
        helper.assertValueEqual(CatalystRule.FIRST_CHARGED, RouterTier.ALLTHEMODIUM.ordinal(), "a do Allthemodium");
        for (RouterTier tier : RouterTier.values()) {
            boolean charged = tier.ordinal() >= RouterTier.ALLTHEMODIUM.ordinal();
            helper.assertValueEqual(ModItems.CHARGED_DUSTS.containsKey(tier), charged, "Pó Carregado de " + tier);
            if (!charged) {
                helper.assertTrue(!CoreCatalyst.accepts(tier, charged(RouterTier.ALLTHEMODIUM, 1)),
                        "o Núcleo " + tier + " aceita Pó Carregado");
                helper.assertTrue(CoreCatalyst.accepts(tier, dust(1)), "o Núcleo " + tier + " recusa o Pó");
            }
        }
        formed(helper, RouterTier.EMERALD, core -> {
            helper.assertValueEqual(click(helper, core, charged(RouterTier.ALLTHEMODIUM, 1)),
                    "block.wirelessautomate.resonance_core.needs_dust", "a mensagem na Esmeralda");
            helper.assertValueEqual(core.redstone(), 0, "o Pó Carregado entrou na Esmeralda");
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void recargaGastaOPoCarregado(GameTestHelper helper) {
        formed(helper, RouterTier.ULTIMATE, core -> {
            assertInserted(helper, core, crystal(true, 0));
            helper.assertValueEqual(core.addCatalyst(charged(RouterTier.ULTIMATE, 2)), 2, "Pó Carregado que entrou");
            recharge(helper, core);
            helper.assertTrue(SignalCrystalItem.isFull(core.crystals().get(0)), "a recarga não encheu o cristal");
            helper.assertValueEqual(core.redstone(), 1, "a recarga não gastou um Pó Carregado");
            helper.assertTrue(core.catalyst().item() == ModItems.CHARGED_DUSTS.get(RouterTier.ULTIMATE).get(),
                    "o catalisador mudou");

            var registries = helper.getLevel().registryAccess();
            CompoundTag saved = core.saveWithoutMetadata(registries);
            helper.assertValueEqual(saved.getString("catalyst"), "wirelessautomate:charged_dust_ultimate", "o NBT");
            core.setRedstoneForTest(0);
            core.loadWithComponents(saved, registries);
            helper.assertValueEqual(core.redstone(), 1, "a contagem lida");
            helper.assertTrue(core.catalyst().item() == ModItems.CHARGED_DUSTS.get(RouterTier.ULTIMATE).get(),
                    "o Pó Carregado lido");
        });
    }

    /**
     * Sem o Allthemodium, só o Pó Carregado Ultimate tem receita: 8 Pós de Ressonância + 1 lingote de netherite
     * rendem 8; os do Allthemodium e a versão do Ultimate com o mod não carregam.
     */
    @GameTest(template = "empty")
    public static void receitaDoUltimateSemOAllthemodium(GameTestHelper helper) {
        List<ItemStack> grade = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            grade.add(dust(1));
        }
        grade.add(new ItemStack(Items.NETHERITE_INGOT));
        CraftingInput input = CraftingInput.of(3, 3, grade);
        var manager = helper.getLevel().getRecipeManager();
        ItemStack out = manager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(holder -> holder.value().assemble(input, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(out.is(ModItems.CHARGED_DUSTS.get(RouterTier.ULTIMATE).get()) && out.getCount() == 8,
                "saiu " + out);
        grade.set(8, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, grade), helper.getLevel())
                .isEmpty(), "o lingote de ferro casou");
        for (String id : List.of("charged_dust_allthemodium", "charged_dust_vibranium", "charged_dust_unobtainium",
                "charged_dust_ultimate")) {
            helper.assertTrue(manager.byKey(WirelessAutomate.id(id)).isEmpty(), "carregou sem o ATM: " + id);
        }
        helper.succeed();
    }

    /** O Pó de um Núcleo que já o tinha antes de subir para o Ultimate gasta até acabar, sem aceitar mais. */
    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void poAntigoNoUltimateGastaAteAcabar(GameTestHelper helper) {
        formed(helper, RouterTier.ULTIMATE, core -> {
            assertInserted(helper, core, crystal(true, 0));
            var registries = helper.getLevel().registryAccess();
            CompoundTag tag = core.saveWithoutMetadata(registries);
            tag.putInt("redstone", 1);
            tag.putString("catalyst", "wirelessautomate:resonance_dust");
            core.loadWithComponents(tag, registries);
            helper.assertTrue(core.catalyst().item() == ModItems.RESONANCE_DUST.get(), "o Pó gravado");
            helper.assertValueEqual(core.addCatalyst(dust(1)), 0, "entrou mais Pó no Ultimate");
            helper.assertValueEqual(core.addCatalyst(charged(RouterTier.ULTIMATE, 1)), 0, "misturou com o Pó");
            recharge(helper, core);
            helper.assertTrue(SignalCrystalItem.isFull(core.crystals().get(0)), "o Pó antigo não recarregou");
            helper.assertValueEqual(core.redstone(), 0, "o Pó antigo não acabou");
            helper.assertValueEqual(core.addCatalyst(charged(RouterTier.ULTIMATE, 3)), 3,
                    "o Pó Carregado não entrou depois do Pó");
        });
    }
}
