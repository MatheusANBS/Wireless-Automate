package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.EditTargetRef;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Os limites do membro presa: diante de um bloco que não é máquina, o roteador de alcance não ganha grupo por nenhuma
 * escrita (o "todas as faces numa rede", o modo ou a rede de uma face; sem o alcance, a presa nasce para esperar a
 * máquina, ver {@code WakeGameTests}), e o NBT da presa sem a chave do grupo não entra num grupo qualquer.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class AttachedGuardGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);

    private static RouterBlockEntity routerOn(GameTestHelper helper, Block block) {
        helper.setBlock(MACHINE, block);
        helper.setBlock(MACHINE.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        return helper.getBlockEntity(MACHINE.above());
    }

    /** O roteador de alcance preso na pedra com todas as faces numa rede: nenhum grupo da pedra. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rangeWallGetsNoGroupFromNetworkAll(GameTestHelper helper) {
        RouterBlockEntity router = routerOn(helper, Blocks.STONE);
        router.setRangeUpgrade(RangeTestSupport.rangeUpgrade());
        helper.assertTrue(router.rangeActive(), "o alcance não vale");
        UUID network = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "parede").id();
        router.setNetworkId(network);
        RemoteMachines remotes = router.remoteMachines();
        helper.assertTrue(remotes.attached() == null, "a pedra virou membro presa");
        helper.assertValueEqual(remotes.groups().size(), 0, "grupos criados para a pedra");
        helper.succeed();
    }

    /**
     * A pedra do roteador de alcance também não vira membro por uma escrita de uma face (modo, rede) nem guarda
     * cartão: o slot recusaria e a escrita é ignorada.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rangeWallGetsNoGroupFromFaceEdit(GameTestHelper helper) {
        RouterBlockEntity router = routerOn(helper, Blocks.STONE);
        router.setRangeUpgrade(RangeTestSupport.rangeUpgrade());
        UUID network = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "face").id();
        router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
        router.setFaceNetwork(ResourceType.ITEM, Direction.NORTH, network);
        RemoteMachines remotes = router.remoteMachines();
        helper.assertTrue(remotes.attached() == null, "a pedra virou membro presa");
        helper.assertValueEqual(remotes.groups().size(), 0, "grupos criados para a pedra");
        helper.assertFalse(router.attachedCanHold(), "a pedra do alcance guarda cartão");
        helper.succeed();
    }

    /**
     * O roteador de alcance preso na pedra com um baú vinculado: a lista não mostra a presa ({@code attachedShown}) e a
     * tela, que abriria na presa, abre no baú.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rangeWallHidesAttachedAndOpensOnFirstMachine(GameTestHelper helper) {
        RouterBlockEntity router = routerOn(helper, Blocks.STONE);
        router.setRangeUpgrade(RangeTestSupport.rangeUpgrade());
        BlockPos chest = helper.absolutePos(MACHINE.east(2));
        helper.setBlock(MACHINE.east(2), Blocks.CHEST);
        helper.assertTrue(router.linkMachine(chest), "o baú não vinculou");
        EditTargetRef opened = RouterMenu.skipHiddenAttached(router, EditTargetRef.ATTACHED);
        helper.assertTrue(opened.target().equals(EditTarget.machine(0)), "a tela abre na presa: " + opened);
        RouterSnapshot snapshot = RouterSnapshot.capture(router, TestPlayers.inLevel(helper), opened.target());
        helper.assertFalse(snapshot.range().attachedShown(), "a pedra aparece na lista");

        // trocada a pedra por um barril, a presa volta para a lista
        helper.setBlock(MACHINE, Blocks.BARREL);
        helper.assertTrue(RouterSnapshot.capture(router, TestPlayers.inLevel(helper), EditTarget.ATTACHED_TARGET)
                .range().attachedShown(), "o barril preso não aparece na lista");
        helper.succeed();
    }

    /** O mesmo diante de uma máquina de verdade cria o membro, no grupo do bloco. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rangeMachineGetsGroupFromNetworkAll(GameTestHelper helper) {
        RouterBlockEntity router = routerOn(helper, Blocks.CHEST);
        router.setRangeUpgrade(RangeTestSupport.rangeUpgrade());
        UUID network = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "baú").id();
        router.setNetworkId(network);
        helper.assertTrue(router.remoteMachines().attached() != null, "o baú não virou membro presa");
        helper.succeed();
    }

    /** A presa gravada sem a chave {@code group} é descartada, em vez de entrar no grupo 0. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void attachedWithoutGroupKeyIsDropped(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        RouterBlockEntity router = routerOn(helper, Blocks.CHEST);
        router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
        helper.assertTrue(router.remoteMachines().attached() != null, "a escrita não criou o membro presa");
        helper.assertValueEqual(router.remoteMachines().attached().group(), 0, "o primeiro grupo não é o 0");
        CompoundTag tag = router.saveWithoutMetadata(registries);
        tag.getCompound("attached").remove("group");
        RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
        copy.loadWithComponents(tag, registries);
        helper.assertTrue(copy.remoteMachines().attached() == null, "a presa sem grupo entrou no grupo 0");
        helper.succeed();
    }

    private AttachedGuardGameTests() {
    }
}
