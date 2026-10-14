package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Roteador de alcance, o modelo das máquinas remotas: grupos por bloco, perfil, exceções, cartões do grupo e o
 * NBT ({@code machines}/{@code groups}).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeMachineGameTests {
    /**
     * O redstone que remonta: um roteador cujas únicas faces com redstone estão num perfil (ou numa
     * exceção) conta esse tipo em {@code redstoneTypes}, para o sinal chegar às remotas.
     */
    @GameTest(template = "empty")
    public static void redstoneTypesCountRemoteFaces(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    int item = NetworkManager.typeBit(ResourceType.ITEM);
                    int fluid = NetworkManager.typeBit(ResourceType.FLUID);
                    helper.assertTrue((router.redstoneTypes() & item) == 0, "itens sem redstone já contavam");
                    int group = groupOf(router, helper.absolutePos(FURNACE_A));
                    router.configureFace(EditTarget.group(group), ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0,
                            RedstoneMode.HIGH);
                    helper.assertTrue((router.redstoneTypes() & item) != 0, "o perfil com redstone não contou");
                    helper.assertTrue((router.redstoneTypes() & fluid) == 0, "outro tipo contou");

                    int machine = router.remoteMachines().index(helper.absolutePos(FURNACE_A));
                    router.configureFace(EditTarget.machine(machine), ResourceType.FLUID, Direction.UP, PortMode.INSERT, 0,
                            RedstoneMode.LOW);
                    helper.assertTrue(router.remoteMachines().machine(machine).hasException(), "não criou a exceção");
                    helper.assertTrue((router.redstoneTypes() & fluid) != 0, "a exceção com redstone não contou");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void remoteMachinesSavedAndReloaded(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BARREL);
                    helper.assertFalse(router.linkMachine(helper.absolutePos(FURNACE_A)), "vinculou a mesma posição duas vezes");
                    BlockPos a = helper.absolutePos(FURNACE_A);
                    BlockPos b = helper.absolutePos(FURNACE_B);
                    BlockPos barrel = helper.absolutePos(BARREL);
                    int furnaces = groupOf(router, a);
                    int barrels = groupOf(router, barrel);
                    helper.assertTrue(furnaces == groupOf(router, b), "as fornalhas ficaram em grupos diferentes");
                    helper.assertTrue(furnaces != barrels, "fornalha e barril no mesmo grupo");

                    // Perfil das fornalhas, exceção na B e cartões nos dois grupos (pela máquina, no do barril).
                    router.configureFace(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, PortMode.EXTRACT, 3,
                            RedstoneMode.IGNORE);
                    int indexB = router.remoteMachines().index(b);
                    router.configureFace(EditTarget.machine(indexB), ResourceType.ITEM, Direction.UP, PortMode.INSERT, 0,
                            RedstoneMode.IGNORE);
                    router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0, itemCard());
                    int indexBarrel = router.remoteMachines().index(barrel);
                    router.setCard(EditTarget.machine(indexBarrel), ResourceType.ITEM, Direction.UP, 1, itemCard());

                    CompoundTag tag = router.saveWithFullMetadata(helper.getLevel().registryAccess());
                    helper.assertTrue(tag.contains("machines") && tag.contains("groups"), "machines/groups não foram salvos");
                    RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
                    copy.loadWithComponents(tag, helper.getLevel().registryAccess());

                    RemoteMachines remotes = copy.remoteMachines();
                    helper.assertTrue(remotes.contains(a) && remotes.contains(b) && remotes.contains(barrel),
                            "máquinas não foram lidas");
                    helper.assertTrue(remotes.index(a) == router.remoteMachines().index(a)
                            && remotes.index(barrel) == indexBarrel, "a ordem de vínculo mudou");
                    helper.assertTrue(remotes.machine(remotes.index(a)).front() == Direction.EAST.ordinal()
                            && remotes.machine(remotes.index(b)).front() == Direction.SOUTH.ordinal(),
                            "a frente das fornalhas não foi lida");
                    helper.assertTrue(remotes.machine(indexBarrel).front() == -1, "barril para cima ganhou frente");
                    helper.assertTrue(remotes.group(furnaces) != null && remotes.group(barrels) != null, "grupos não foram lidos");
                    helper.assertTrue(remotes.group(furnaces).block().equals(BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE)),
                            "id do bloco do grupo errado");

                    FaceConfig profile = copy.face(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH);
                    helper.assertTrue(profile.mode() == PortMode.EXTRACT && profile.priority() == 3, "perfil não foi lido");
                    helper.assertFalse(remotes.machine(remotes.index(a)).hasException(), "a fornalha A ganhou exceção");
                    helper.assertTrue(remotes.machine(indexB).hasException(), "a exceção da B não foi lida");
                    helper.assertTrue(copy.face(EditTarget.machine(indexB), ResourceType.ITEM, Direction.UP).mode() == PortMode.INSERT
                            && copy.face(EditTarget.machine(indexB), ResourceType.ITEM, Direction.SOUTH).mode() == PortMode.EXTRACT,
                            "a exceção da B não tem a mudança e o perfil copiado");
                    helper.assertTrue(copy.card(EditTarget.group(furnaces), ResourceType.ITEM, Direction.SOUTH, 0)
                            .is(ModItems.FILTER_CARD.get()), "cartão do grupo das fornalhas não foi lido");
                    helper.assertTrue(copy.card(EditTarget.machine(indexB), ResourceType.ITEM, Direction.SOUTH, 0)
                            .is(ModItems.FILTER_CARD.get()), "a máquina não lê os cartões do grupo");
                    helper.assertTrue(copy.card(EditTarget.group(barrels), ResourceType.ITEM, Direction.UP, 1)
                            .is(ModItems.FILTER_CARD.get()), "cartão posto pela máquina não foi para o grupo do barril");
                    helper.assertTrue(copy.card(EditTarget.ATTACHED_TARGET, ResourceType.ITEM, Direction.UP, 1).isEmpty(),
                            "cartão de grupo foi para a máquina presa");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void legacyNbtLoadsWithoutMachines(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    // Um NBT sem machines/groups (de antes do Upgrade de Alcance): só a máquina presa configurada.
                    router.configureFace(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 2, RedstoneMode.IGNORE);
                    CompoundTag legacy = router.saveWithFullMetadata(helper.getLevel().registryAccess());
                    helper.assertFalse(legacy.contains("machines") || legacy.contains("groups"),
                            "um roteador sem remotas salvou as listas");

                    link(helper, router, FURNACE_A);
                    router.loadWithComponents(legacy, helper.getLevel().registryAccess());
                    helper.assertTrue(router.remoteMachines().isEmpty(), "NBT sem as chaves deixou máquinas");
                    FaceConfig attached = router.face(EditTarget.ATTACHED_TARGET, ResourceType.ITEM, Direction.UP);
                    helper.assertTrue(attached.mode() == PortMode.EXTRACT && attached.priority() == 2,
                            "a máquina presa perdeu a configuração");

                    // E funciona: vincula de novo e configura.
                    link(helper, router, FURNACE_A);
                    router.configureFace(EditTarget.group(groupOf(router, helper.absolutePos(FURNACE_A))), ResourceType.ITEM,
                            Direction.NORTH, PortMode.INSERT, 0, RedstoneMode.IGNORE);
                    helper.assertTrue(router.face(EditTarget.machine(0), ResourceType.ITEM, Direction.NORTH).mode() == PortMode.INSERT,
                            "a máquina vinculada depois da carga não segue o perfil");
                    helper.assertTrue(router.face(ResourceType.ITEM, Direction.UP).mode() == PortMode.EXTRACT,
                            "editar o grupo mexeu na máquina presa");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void profileChangeSkipsException(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    RemoteMachines remotes = router.remoteMachines();
                    int indexA = remotes.index(helper.absolutePos(FURNACE_A));
                    int indexB = remotes.index(helper.absolutePos(FURNACE_B));
                    EditTarget group = EditTarget.group(remotes.machine(indexA).group());

                    router.configureFace(EditTarget.machine(indexB), ResourceType.FLUID, Direction.UP, PortMode.INSERT, 1,
                            RedstoneMode.IGNORE);
                    helper.assertTrue(remotes.machine(indexB).hasException(), "editar a máquina não criou exceção");
                    helper.assertTrue(router.face(group, ResourceType.FLUID, Direction.UP).mode() == PortMode.NONE,
                            "editar a máquina mudou o perfil");

                    router.configureFace(group, ResourceType.ITEM, Direction.WEST, PortMode.EXTRACT, 0, RedstoneMode.IGNORE);
                    router.setFilter(group, ResourceType.ITEM, Direction.WEST, Filter.EMPTY.withListMode(Filter.ListMode.BLACKLIST));
                    FaceConfig onA = router.face(EditTarget.machine(indexA), ResourceType.ITEM, Direction.WEST);
                    helper.assertTrue(onA.mode() == PortMode.EXTRACT && onA.filter().listMode() == Filter.ListMode.BLACKLIST,
                            "o perfil não vale para a máquina sem exceção");
                    helper.assertTrue(remotes.effective(remotes.machine(indexA)) == remotes.group(group.id()).profile(),
                            "a máquina sem exceção não usa o perfil");
                    FaceConfig onB = router.face(EditTarget.machine(indexB), ResourceType.ITEM, Direction.WEST);
                    helper.assertTrue(onB.mode() == PortMode.NONE, "o perfil mudou a exceção");
                    helper.assertTrue(router.face(EditTarget.machine(indexB), ResourceType.FLUID, Direction.UP).mode()
                            == PortMode.INSERT, "a exceção perdeu a própria mudança");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void exceptionEqualToProfileDisappears(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    RemoteMachines remotes = router.remoteMachines();
                    int indexA = remotes.index(helper.absolutePos(FURNACE_A));
                    int indexB = remotes.index(helper.absolutePos(FURNACE_B));
                    EditTarget group = EditTarget.group(remotes.machine(indexA).group());

                    // Desfazer a mudança na máquina tira a exceção.
                    router.configureFace(EditTarget.machine(indexA), ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT, 0,
                            RedstoneMode.IGNORE);
                    helper.assertTrue(remotes.machine(indexA).hasException(), "editar a máquina não criou exceção");
                    router.configureFace(EditTarget.machine(indexA), ResourceType.ITEM, Direction.NORTH, PortMode.NONE, 0,
                            RedstoneMode.IGNORE);
                    helper.assertFalse(remotes.machine(indexA).hasException(), "a exceção igual ao perfil ficou");

                    // O perfil alcançar a exceção também a tira.
                    router.configureFace(EditTarget.machine(indexB), ResourceType.ITEM, Direction.NORTH, PortMode.INSERT, 0,
                            RedstoneMode.IGNORE);
                    helper.assertTrue(remotes.machine(indexB).hasException(), "editar a B não criou exceção");
                    router.configureFace(group, ResourceType.ITEM, Direction.NORTH, PortMode.INSERT, 0, RedstoneMode.IGNORE);
                    helper.assertFalse(remotes.machine(indexB).hasException(), "a exceção alcançada pelo perfil ficou");

                    // Voltar ao perfil.
                    router.configureFace(EditTarget.machine(indexB), ResourceType.ENERGY, Direction.DOWN, PortMode.EXTRACT, 0,
                            RedstoneMode.IGNORE);
                    helper.assertTrue(router.revertToProfile(indexB), "voltar ao perfil não fez nada");
                    helper.assertFalse(remotes.machine(indexB).hasException(), "voltar ao perfil deixou a exceção");
                    helper.assertTrue(router.face(EditTarget.machine(indexB), ResourceType.ENERGY, Direction.DOWN).mode()
                            == PortMode.NONE, "voltar ao perfil não voltou a configuração");

                    // Uma exceção sobrevive ao NBT só se diferente do perfil.
                    CompoundTag tag = router.saveWithFullMetadata(helper.getLevel().registryAccess());
                    RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
                    copy.loadWithComponents(tag, helper.getLevel().registryAccess());
                    helper.assertFalse(copy.remoteMachines().machine(indexB).hasException(), "exceção apagada voltou pelo NBT");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void unlinkLastMachineDropsGroupCards(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BARREL);
                    BlockPos a = helper.absolutePos(FURNACE_A);
                    int furnaces = groupOf(router, a);
                    router.setCard(EditTarget.group(furnaces), ResourceType.ITEM, Direction.EAST, 0, itemCard());

                    // A outra fornalha ainda está no grupo: nada cai.
                    helper.assertTrue(router.unlinkMachine(a), "não desvinculou a fornalha A");
                    helper.assertFalse(router.remoteMachines().contains(a), "a fornalha A continua vinculada");
                    helper.assertTrue(router.remoteMachines().group(furnaces) != null, "o grupo saiu com uma máquina nele");
                    helper.assertItemEntityNotPresent(ModItems.FILTER_CARD.get(), ROUTER, 3.0);

                    // A última sai: o grupo some e o cartão cai no roteador.
                    helper.assertTrue(router.unlinkMachine(helper.absolutePos(FURNACE_B)), "não desvinculou a fornalha B");
                    helper.assertTrue(router.remoteMachines().group(furnaces) == null, "o grupo vazio ficou");
                    helper.assertItemEntityPresent(ModItems.FILTER_CARD.get(), ROUTER, 2.0);
                    helper.assertTrue(router.remoteMachines().contains(helper.absolutePos(BARREL)), "o barril saiu junto");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void breakDropsGroupCards(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, BARREL);
                    int barrels = groupOf(router, helper.absolutePos(BARREL));
                    router.setCard(EditTarget.group(barrels), ResourceType.ITEM, Direction.DOWN, 1, itemCard());
                    helper.destroyBlock(ROUTER);
                    helper.assertBlockNotPresent(ModBlocks.ROUTER.get(), ROUTER);
                    helper.assertItemEntityPresent(ModItems.FILTER_CARD.get(), ROUTER, 2.0);
                })
                .thenSucceed();
    }
}
