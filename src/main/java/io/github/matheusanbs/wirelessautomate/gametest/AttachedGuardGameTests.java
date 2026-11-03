package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
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
 * Os limites do membro presa: diante de um bloco que não é máquina, o roteador de alcance não ganha grupo pelo
 * "todas as faces numa rede" (sem o alcance, a presa nasce para esperar a máquina, ver {@code WakeGameTests}), e o NBT
 * da presa sem a chave do grupo não entra num grupo qualquer.
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
