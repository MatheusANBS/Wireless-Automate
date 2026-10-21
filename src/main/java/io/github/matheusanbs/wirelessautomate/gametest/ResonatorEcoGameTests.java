package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertInserted;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.formed;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CoreEco;
import io.github.matheusanbs.wirelessautomate.resonator.CoreInventory;
import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * O Eco Ressonante do Núcleo Ultimate: o Afinado Ultimate rachado queimado pelo slot do Eco (no menu e pela Porta de
 * Cristais) soma 1 de Eco e +5% na geração base e no buffer; o teto do {@code ecoMax}, a saturação, o Eco que vai ao
 * item ao quebrar e volta ao colocar, e o Núcleo de outro tier que recusa.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorEcoGameTests {
    private static final int CONTAINER_ID = 46;
    /** A Porta de Cristais da Vitrine, na face norte da caixa. */
    private static final BlockPos CRYSTAL_PORT = ResonatorTestSupport.ORIGIN.offset(2, 2, 0);

    private ResonatorEcoGameTests() {
    }

    private static ItemStack tuned(RouterTier tier, int count) {
        return new ItemStack(ModItems.TUNED_CRYSTALS.get(tier).get(), count);
    }

    private static ItemStack cracked(ItemStack stack) {
        SignalCrystalItem.setCharge(stack, 0);
        SignalCrystalItem.setIntegrity(stack, 0);
        return stack;
    }

    /** Estação e dial juntos e um Afinado Ultimate cheio na primeira câmara: a geração base não é zero. */
    private static long generating(GameTestHelper helper, ResonanceCoreBlockEntity core) {
        core.setStationForTest(98.0);
        core.setDial(98.0);
        assertInserted(helper, core, tuned(RouterTier.ULTIMATE, 1));
        long base = core.generationPerTick();
        helper.assertTrue(base > 0, "a geração base é zero");
        return base;
    }

    private static long baseMax(ResonanceCoreBlockEntity core) {
        return CoreMath.maxOutput(core.chambers(), Config.resonatorFePerCrystal(core.tier()));
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void queimarSomaUmEFatorSobe(GameTestHelper helper) {
        formed(helper, RouterTier.ULTIMATE, core -> {
            long base = generating(helper, core);
            int eco = CoreInventory.ecoSlot(core.tier());
            helper.assertValueEqual(eco, core.chambers() + 1, "slot do Eco");
            helper.assertValueEqual(core.inventory().getContainerSize(), core.chambers() + 2, "câmaras, catalisador e Eco");
            core.inventory().setItem(eco, cracked(tuned(RouterTier.ULTIMATE, 1)));
            helper.assertValueEqual(core.eco().value(), 1L, "Eco depois de queimar um");
            helper.assertTrue(core.inventory().getItem(eco).isEmpty(), "o slot do Eco guardou o cristal");
            helper.assertValueEqual(core.generationPerTick(), CoreEco.apply(base, 1, 0.05, 0), "geração com +5%");
            helper.assertTrue(core.generationPerTick() > base, "a geração não subiu");
            long max = CoreEco.apply(baseMax(core), 1, 0.05, 0);
            helper.assertValueEqual(core.maxOutput(), max, "geração máxima com o Eco");
            helper.assertValueEqual(core.capacity(), CoreMath.mulSat(max, 200), "o buffer acompanha");
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void soAfinadoUltimateRachadoVale(GameTestHelper helper) {
        formed(helper, RouterTier.ULTIMATE, core -> {
            int eco = CoreInventory.ecoSlot(core.tier());
            List<ItemStack> recusados = List.of(tuned(RouterTier.ULTIMATE, 1),
                    cracked(tuned(RouterTier.UNOBTAINIUM, 1)), cracked(new ItemStack(ModItems.SIGNAL_CRYSTAL.get())),
                    cracked(new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get())));
            for (ItemStack stack : recusados) {
                helper.assertTrue(!core.inventory().canPlaceItem(eco, stack), "o Eco aceita " + stack);
                helper.assertValueEqual(core.eco().burn(stack, false), 0, "queimou " + stack);
            }
            helper.assertValueEqual(core.eco().value(), 0L, "Eco com cristal errado");

            ServerPlayer player = ResonatorTestSupport.survivor(helper);
            ResonatorMenu menu = new ResonatorMenu(CONTAINER_ID, player.getInventory(), core);
            player.containerMenu = menu;
            try {
                helper.assertValueEqual(menu.ecoSlot(), eco, "slot do Eco no menu");
                helper.assertValueEqual(menu.inventoryStart(), eco + 1, "o inventário vem depois do Eco");
                helper.assertTrue(!menu.getSlot(eco).mayPlace(tuned(RouterTier.ULTIMATE, 1)),
                        "o menu aceita o Afinado inteiro");
                menu.setCarried(cracked(tuned(RouterTier.ULTIMATE, 3)));
                menu.clicked(eco, 0, ClickType.PICKUP, player);
                helper.assertValueEqual(core.eco().value(), 1L, "um clique queima um");
                helper.assertValueEqual(menu.getCarried().getCount(), 2, "sobra no cursor");
                menu.setCarried(ItemStack.EMPTY);
                player.getInventory().setItem(9, cracked(tuned(RouterTier.ULTIMATE, 2)));
                menu.quickMoveStack(player, menu.inventoryStart());
                helper.assertValueEqual(core.eco().value(), 3L, "Shift + clique queima a pilha");
                helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "a pilha ficou no inventário");
            } finally {
                menu.setCarried(ItemStack.EMPTY);
                player.containerMenu = player.inventoryMenu;
            }
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 200, batch = "wirelessautomate_config_eco")
    public static void tetoDoEcoMax(GameTestHelper helper) {
        formed(helper, RouterTier.ULTIMATE, core -> {
            long before = Config.ECO_MAX.get();
            try {
                Config.ECO_MAX.set(2L);
                long base = generating(helper, core);
                helper.assertValueEqual(core.eco().burn(cracked(tuned(RouterTier.ULTIMATE, 3)), false), 2,
                        "queimou além do teto");
                helper.assertValueEqual(core.eco().value(), 2L, "Eco no teto");
                helper.assertTrue(!core.inventory().canPlaceItem(CoreInventory.ecoSlot(core.tier()),
                        cracked(tuned(RouterTier.ULTIMATE, 1))), "o slot aceita no teto");
                helper.assertValueEqual(core.generationPerTick(), CoreEco.apply(base, 2, 0.05, 0), "geração com +10%");
                Config.ECO_MAX.set(1L);
                helper.assertValueEqual(core.generationPerTick(), CoreEco.apply(base, 1, 0.05, 0),
                        "o teto menor limita o Eco guardado");
                helper.assertValueEqual(core.eco().value(), 2L, "o teto menor apagou o Eco");
            } finally {
                Config.ECO_MAX.set(before);
            }
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void ecoSatura(GameTestHelper helper) {
        formed(helper, RouterTier.ULTIMATE, core -> {
            generating(helper, core);
            core.applyComponentsFromItemStack(withEco(Long.MAX_VALUE));
            helper.assertValueEqual(core.eco().value(), Long.MAX_VALUE, "Eco do item");
            helper.assertValueEqual(core.generationPerTick(), Long.MAX_VALUE, "geração saturada");
            helper.assertValueEqual(core.capacity(), Long.MAX_VALUE, "buffer saturado");
            helper.assertValueEqual(core.eco().burn(cracked(tuned(RouterTier.ULTIMATE, 1)), false), 0,
                    "queimou com o Eco no máximo");
            helper.assertValueEqual(core.eco().value(), Long.MAX_VALUE, "o Eco passou do máximo");
        });
    }

    private static ItemStack withEco(long eco) {
        ItemStack stack = new ItemStack(ModItems.RESONANCE_CORE.get());
        stack.set(ModDataComponents.RESONATOR_ECO.get(), eco);
        return stack;
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void quebrarEColocarPreservaOEco(GameTestHelper helper) {
        formed(helper, RouterTier.ULTIMATE, core -> {
            core.eco().burn(cracked(tuned(RouterTier.ULTIMATE, 7)), false);
            CompoundTag tag = core.saveWithoutMetadata(helper.getLevel().registryAccess());
            helper.assertValueEqual(tag.getLong("eco"), 7L, "chave eco no NBT");
            List<ItemStack> drops = Block.getDrops(core.getBlockState(), helper.getLevel(), core.getBlockPos(), core);
            ItemStack item = drops.stream().filter(stack -> stack.is(ModItems.RESONANCE_CORE.get())).findFirst()
                    .orElse(ItemStack.EMPTY);
            helper.assertValueEqual(item.get(ModDataComponents.RESONATOR_ECO.get()), 7L, "Eco no item quebrado");

            BlockPos loose = new BlockPos(0, 1, 0);
            BlockState state = ModBlocks.RESONANCE_CORE.get().defaultBlockState().setValue(RouterBlock.TIER,
                    RouterTier.ULTIMATE);
            helper.setBlock(loose, state);
            ResonanceCoreBlockEntity placed = ResonatorTestSupport.core(helper, loose);
            helper.assertTrue(placed != null, "o Núcleo colocado sem block entity");
            placed.applyComponentsFromItemStack(item);
            helper.assertValueEqual(placed.eco().value(), 7L, "Eco de volta ao colocar");
            ResonanceCoreBlockEntity reloaded = new ResonanceCoreBlockEntity(helper.absolutePos(loose), state);
            reloaded.loadWithComponents(tag, helper.getLevel().registryAccess());
            helper.assertValueEqual(reloaded.eco().value(), 7L, "Eco lido do NBT");
            CompoundTag custom = placed.saveCustomOnly(helper.getLevel().registryAccess());
            placed.removeComponentsFromTag(custom);
            helper.assertTrue(!custom.contains("eco"), "o Eco foi duas vezes ao item");
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void portaEntregaAoEco(GameTestHelper helper) {
        formed(helper, RouterTier.ULTIMATE, core -> {
            IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                    helper.absolutePos(CRYSTAL_PORT), Direction.NORTH);
            helper.assertTrue(items != null, "a Porta de Cristais sem capability");
            int eco = CoreInventory.ecoSlot(core.tier());
            helper.assertValueEqual(items.getSlots(), core.chambers() + 2, "câmaras, catalisador e Eco");
            helper.assertValueEqual(items.insertItem(eco, cracked(tuned(RouterTier.ULTIMATE, 3)), true).getCount(), 0,
                    "simulado: o Eco aceita a pilha");
            helper.assertValueEqual(core.eco().value(), 0L, "a simulação queimou");
            ItemStack sobra = ItemHandlerHelper.insertItem(items, cracked(tuned(RouterTier.ULTIMATE, 3)), false);
            helper.assertTrue(sobra.isEmpty(), "a Porta não entregou ao Eco");
            helper.assertValueEqual(core.eco().value(), 3L, "Eco pela Porta");
            for (int i = 0; i < core.chambers(); i++) {
                helper.assertTrue(core.crystals().get(i).isEmpty(), "o rachado entrou numa câmara");
            }
            helper.assertTrue(items.getStackInSlot(eco).isEmpty(), "o slot do Eco mostra um cristal");
            helper.assertTrue(items.extractItem(eco, 1, false).isEmpty(), "saiu algo do Eco");
            helper.assertTrue(!items.isItemValid(eco, cracked(tuned(RouterTier.UNOBTAINIUM, 1))),
                    "o Eco aceita o Afinado de outro tier");
        });
    }

    @GameTest(template = "empty7", timeoutTicks = 200)
    public static void naoUltimateRecusa(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            helper.assertValueEqual(CoreInventory.ecoSlot(core.tier()), -1, "slot do Eco fora do Ultimate");
            helper.assertValueEqual(core.inventory().getContainerSize(), core.chambers() + 1, "sem o slot do Eco");
            ItemStack fuel = cracked(tuned(RouterTier.ULTIMATE, 1));
            helper.assertValueEqual(core.eco().burn(fuel, false), 0, "o Esmeralda queimou");
            IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                    helper.absolutePos(CRYSTAL_PORT), Direction.NORTH);
            helper.assertValueEqual(items.getSlots(), core.chambers() + 1, "a Porta com o slot do Eco");
            helper.assertValueEqual(ItemHandlerHelper.insertItem(items, fuel.copy(), false).getCount(), 1,
                    "a Porta aceitou o rachado");
            ServerPlayer player = ResonatorTestSupport.survivor(helper);
            ResonatorMenu menu = new ResonatorMenu(CONTAINER_ID, player.getInventory(), core);
            helper.assertValueEqual(menu.ecoSlot(), -1, "o menu com o slot do Eco");
            helper.assertValueEqual(menu.inventoryStart(), core.chambers() + 1, "início do inventário");
            long base = generating(helper, core);
            core.applyComponentsFromItemStack(withEco(10));
            helper.assertValueEqual(core.generationPerTick(), base, "o Eco valeu fora do Ultimate");
            helper.assertValueEqual(core.eco().value(), 10L, "o Eco do item");
        });
    }
}
