package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.ORIGIN;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertInserted;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.crystal;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.formed;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.hit;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.survivor;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.CoreCatalyst;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBatch;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Rebalanceamento do Ressonador, etapa 1: a fadiga do cristal na recarga (integridade, rachado) e o catalisador por
 * tier (redstone ou Pó no Básico e no Avançado, só o Pó do Elite para cima). A recarga é forçada por um lote do
 * tamanho dela, sem mudar a config.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorFatigueGameTests {
    /** A Porta de Cristais da Vitrine montada em {@link ResonatorTestSupport#ORIGIN}. */
    private static final BlockPos CRYSTAL_PORT = ORIGIN.offset(2, 2, 0);
    private static final int CONTAINER_ID = 1;

    private ResonatorFatigueGameTests() {
    }

    /** Um lote que completa a recarga em curso de uma vez. */
    private static void recharge(GameTestHelper helper, ResonanceCoreBlockEntity core) {
        ResonatorBatch.run(helper.getLevel(), core, (int) Math.min(Config.rechargeTicks(), Integer.MAX_VALUE));
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

    private static ResonatorMenu menu(GameTestHelper helper, ResonanceCoreBlockEntity core) {
        return new ResonatorMenu(CONTAINER_ID, TestPlayers.inLevel(helper).getInventory(), core);
    }

    /** Põe um Opaco e racha a pilha da câmara no lugar (rachado não entra pelos caminhos normais). */
    private static void crackedAt(GameTestHelper helper, ResonanceCoreBlockEntity core, int chamber) {
        helper.assertTrue(core.insertCrystalAt(chamber, crystal(true, 0)), "o Opaco não entrou");
        SignalCrystalItem.setIntegrity(core.crystals().get(chamber), 0);
        helper.assertTrue(SignalCrystalItem.isCracked(core.crystals().get(chamber)), "não rachou");
    }

    @GameTest(template = "empty7")
    public static void recargaGastaIntegridade(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            assertInserted(helper, core, crystal(true, 0));
            helper.assertValueEqual(core.addCatalyst(new ItemStack(Items.REDSTONE)), 1, "redstone que entrou");
            recharge(helper, core);
            ItemStack stack = core.crystals().get(0);
            helper.assertTrue(SignalCrystalItem.isFull(stack), "a recarga não encheu o cristal");
            helper.assertValueEqual(SignalCrystalItem.integrity(stack), Config.crystalIntegrity() - 1,
                    "integridade depois de uma recarga");
            helper.assertValueEqual(core.redstone(), 0, "a recarga não gastou o catalisador");
        });
    }

    @GameTest(template = "empty7")
    public static void ultimaRecargaRacha(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ItemStack last = crystal(true, 0);
            SignalCrystalItem.setIntegrity(last, 1);
            assertInserted(helper, core, last);
            helper.assertValueEqual(core.addCatalyst(new ItemStack(Items.REDSTONE, 2)), 2, "redstone que entrou");
            int syncs = core.syncCount();
            recharge(helper, core);
            ItemStack stack = core.crystals().get(0);
            helper.assertTrue(SignalCrystalItem.isCracked(stack), "a última recarga não rachou o cristal");
            helper.assertValueEqual(SignalCrystalItem.charge(stack), 0, "o rachado encheu");
            helper.assertValueEqual(SignalCrystalItem.integrity(stack), 0, "integridade do rachado");
            helper.assertValueEqual(core.redstone(), 1, "a recarga que racha gasta o catalisador");
            helper.assertTrue(core.syncCount() > syncs, "a câmara não foi mandada com o cristal rachado");
            // Na câmara, 6 é o Lapidado rachado: o cliente o separa do Opaco que recarrega.
            helper.assertValueEqual((int) ChamberView.of(core, -1).chambers()[0], 6, "código da câmara do rachado");
            recharge(helper, core);
            helper.assertValueEqual(core.redstone(), 1, "o rachado gastou catalisador");
            helper.assertValueEqual(core.rechargeProgress(), 0, "o rachado entrou na recarga");
            helper.assertTrue(SignalCrystalItem.isCracked(core.crystals().get(0)), "o rachado mudou");
        });
    }

    @GameTest(template = "empty7")
    public static void rachadoNaoRecarrega(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            crackedAt(helper, core, 0);
            helper.assertValueEqual(core.addCatalyst(new ItemStack(Items.REDSTONE, 3)), 3, "redstone que entrou");
            ResonatorMenu menu = menu(helper, core);
            ResonatorBatch.run(helper.getLevel(), core, 20);
            helper.assertValueEqual(core.rechargeProgress(), 0, "só com o rachado, a recarga andou");
            helper.assertValueEqual(menu.pollState(0).recharge(), 0, "a tela mostra recarga só com o rachado");
            helper.assertTrue(core.insertCrystalAt(1, crystal(false, 0)), "o Opaco não entrou");
            ResonatorBatch.run(helper.getLevel(), core, 20);
            helper.assertValueEqual(core.rechargeProgress(), 20, "a recarga não foi para o Opaco");
            recharge(helper, core);
            helper.assertTrue(SignalCrystalItem.isFull(core.crystals().get(1)), "o Opaco não encheu");
            helper.assertTrue(SignalCrystalItem.isCracked(core.crystals().get(0)), "o rachado mudou");
            helper.assertValueEqual(core.redstone(), 2, "uma recarga, um catalisador");
        });
    }

    @GameTest(template = "empty7")
    public static void eliteSoAceitaPo(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            ItemStack redstone = new ItemStack(Items.REDSTONE, 5);
            int slot = core.chambers();
            helper.assertTrue(!CoreCatalyst.accepts(RouterTier.ELITE, redstone), "a regra aceita redstone no Elite");
            helper.assertValueEqual(core.addCatalyst(redstone), 0, "redstone entrou no Elite");
            helper.assertTrue(!core.inventory().canPlaceItem(slot, redstone), "o inventário aceita redstone");
            ResonatorMenu menu = menu(helper, core);
            helper.assertTrue(!menu.getSlot(menu.redstoneSlot()).mayPlace(redstone), "o slot do menu aceita redstone");
            IItemHandler items = port(helper);
            helper.assertTrue(!items.isItemValid(slot, redstone), "a Porta aceita redstone");
            helper.assertValueEqual(items.insertItem(slot, redstone, false).getCount(), 5, "a Porta pôs redstone");
            ServerPlayer player = survivor(helper);
            player.setItemInHand(InteractionHand.MAIN_HAND, redstone);
            core.getBlockState().useItemOn(redstone, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    hit(core.getBlockPos()));
            helper.assertValueEqual(redstone.getCount(), 5, "o clique gastou a redstone");
            helper.assertValueEqual(core.redstone(), 0, "o clique pôs redstone");

            helper.assertTrue(menu.getSlot(menu.redstoneSlot()).mayPlace(dust(1)), "o slot do menu recusa o Pó");
            helper.assertValueEqual(items.insertItem(slot, dust(3), false).getCount(), 0, "a Porta recusou o Pó");
            ItemStack hand = dust(2);
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            core.getBlockState().useItemOn(hand, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    hit(core.getBlockPos()));
            helper.assertValueEqual(core.redstone(), 5, "Pó no Núcleo");
            helper.assertTrue(core.catalyst().item() == ModItems.RESONANCE_DUST.get(), "o catalisador não é o Pó");
            helper.assertTrue(items.getStackInSlot(slot).is(ModItems.RESONANCE_DUST.get()), "a Porta mostra outro item");
            helper.assertValueEqual(core.inventory().getItem(slot).getCount(), 5, "o inventário mostra outra contagem");
        });
    }

    @GameTest(template = "empty7")
    public static void basicoAceitaOsDoisSemMisturar(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            int slot = core.chambers();
            helper.assertTrue(CoreCatalyst.accepts(RouterTier.BASIC, new ItemStack(Items.REDSTONE)), "Básico sem redstone");
            helper.assertTrue(CoreCatalyst.accepts(RouterTier.ADVANCED, dust(1)), "Avançado sem o Pó");
            helper.assertValueEqual(core.addCatalyst(new ItemStack(Items.REDSTONE, 2)), 2, "redstone que entrou");
            helper.assertValueEqual(core.addCatalyst(dust(1)), 0, "o Pó misturou com a redstone");
            ResonatorMenu menu = menu(helper, core);
            helper.assertTrue(!menu.getSlot(menu.redstoneSlot()).mayPlace(dust(1)), "o menu mistura o Pó");
            helper.assertTrue(menu.getSlot(menu.redstoneSlot()).mayPlace(new ItemStack(Items.REDSTONE)),
                    "o menu recusa mais redstone");
            IItemHandler items = port(helper);
            helper.assertValueEqual(items.insertItem(slot, dust(1), false).getCount(), 1, "a Porta misturou o Pó");
            ItemStack taken = core.inventory().removeItemNoUpdate(slot);
            helper.assertTrue(taken.is(Items.REDSTONE) && taken.getCount() == 2, "saiu " + taken);
            helper.assertValueEqual(items.insertItem(slot, dust(4), false).getCount(), 0, "o Pó não entrou no vazio");
            helper.assertTrue(core.catalyst().item() == ModItems.RESONANCE_DUST.get(), "o catalisador não é o Pó");
            helper.assertValueEqual(core.addCatalyst(new ItemStack(Items.REDSTONE)), 0, "a redstone misturou com o Pó");
            ItemStack renamed = dust(1);
            renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Pó"));
            helper.assertValueEqual(core.addCatalyst(renamed), 0, "o Pó renomeado entrou");
        });
    }

    @GameTest(template = "empty7")
    public static void redstoneAntigaNoEliteGastaAteAcabar(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            assertInserted(helper, core, crystal(true, 0));
            var registries = helper.getLevel().registryAccess();
            CompoundTag tag = core.saveWithoutMetadata(registries);
            tag.putInt("redstone", 1);
            tag.remove("catalyst");
            core.loadWithComponents(tag, registries);
            helper.assertValueEqual(core.redstone(), 1, "a redstone gravada");
            helper.assertTrue(core.catalyst().item() == Items.REDSTONE, "sem a chave, o catalisador não é redstone");
            helper.assertValueEqual(core.addCatalyst(dust(1)), 0, "o Pó misturou com a redstone antiga");
            recharge(helper, core);
            helper.assertTrue(SignalCrystalItem.isFull(core.crystals().get(0)), "a redstone antiga não recarregou");
            helper.assertValueEqual(core.redstone(), 0, "a redstone antiga não acabou");
            helper.assertValueEqual(core.addCatalyst(dust(2)), 2, "o Pó não entrou depois da redstone");

            CompoundTag saved = core.saveWithoutMetadata(registries);
            helper.assertValueEqual(saved.getString("catalyst"), "wirelessautomate:resonance_dust", "o NBT do Pó");
            helper.assertValueEqual(saved.getInt("redstone"), 2, "a contagem no NBT");
            core.setRedstoneForTest(0);
            core.loadWithComponents(saved, registries);
            helper.assertValueEqual(core.redstone(), 2, "a contagem lida");
            helper.assertTrue(core.catalyst().item() == ModItems.RESONANCE_DUST.get(), "o Pó lido");
        });
    }

    /**
     * O rachado sai pela Porta como o Opaco, mas mesmo com catalisador no Núcleo: o Opaco espera porque ainda vai
     * recarregar, e o rachado nunca recarrega (preso, ele tomaria a câmara de quem abastece o catalisador sem parar).
     */
    @GameTest(template = "empty7")
    public static void portaExtraiRachado(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            crackedAt(helper, core, 0);
            helper.assertTrue(core.insertCrystalAt(1, crystal(false, 0)), "o Opaco não entrou");
            helper.assertValueEqual(core.addCatalyst(new ItemStack(Items.REDSTONE)), 1, "redstone que entrou");
            IItemHandler items = port(helper);
            helper.assertTrue(items.extractItem(1, 1, false).isEmpty(), "com catalisador o Opaco saiu");
            ItemStack simulated = items.extractItem(0, 1, true);
            helper.assertTrue(SignalCrystalItem.isCracked(simulated), "a simulação não dá o rachado");
            helper.assertTrue(!core.crystals().get(0).isEmpty(), "a simulação tirou");
            ItemStack taken = items.extractItem(0, 1, false);
            helper.assertTrue(SignalCrystalItem.isCracked(taken), "o rachado não saiu");
            helper.assertTrue(core.crystals().get(0).isEmpty(), "a câmara do rachado não esvaziou");
            helper.assertTrue(items.extractItem(2, 1, false).isEmpty(), "câmara vazia deu item");
        });
    }

    @GameTest(template = "empty7")
    public static void rachadoNaoEntraNaCamara(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ItemStack cracked = crystal(true, 0);
            SignalCrystalItem.setIntegrity(cracked, 0);
            helper.assertValueEqual(core.insertCrystal(cracked).getCount(), 1, "o rachado entrou pelo clique");
            helper.assertTrue(!core.insertCrystalAt(0, cracked), "o rachado entrou pela câmara");
            helper.assertTrue(!core.inventory().canPlaceItem(0, cracked), "o inventário aceita o rachado");
            ResonatorMenu menu = menu(helper, core);
            helper.assertTrue(!menu.getSlot(0).mayPlace(cracked), "o slot do menu aceita o rachado");
            IItemHandler items = port(helper);
            helper.assertTrue(!items.isItemValid(0, cracked), "a Porta aceita o rachado");
            helper.assertValueEqual(items.insertItem(0, cracked, false).getCount(), 1, "a Porta pôs o rachado");
            ServerPlayer player = survivor(helper);
            ItemStack hand = cracked.copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            core.getBlockState().useItemOn(hand, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    hit(core.getBlockPos()));
            helper.assertValueEqual(hand.getCount(), 1, "o clique gastou o rachado");
            for (int i = 0; i < core.chambers(); i++) {
                helper.assertTrue(core.crystals().get(i).isEmpty(), "uma câmara ficou com o rachado");
            }
            helper.assertTrue(menu.getSlot(0).mayPlace(crystal(true, CrystalCharge.FULL)), "o slot recusa o íntegro");
        });
    }
}
