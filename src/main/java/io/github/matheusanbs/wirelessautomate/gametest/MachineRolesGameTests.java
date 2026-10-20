package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.IndexRemotes;
import io.github.matheusanbs.wirelessautomate.network.NetworkOrder;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O papel por máquina no índice de nós (etapa 3b): os papéis e as redes da presa, de cada grupo e de cada exceção,
 * a ida e volta do NBT, o formato da 3a sem as chaves novas, o aviso de mudança do perfil e a soma das máquinas.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class MachineRolesGameTests {
    private MachineRolesGameTests() {
    }

    private static final List<String> NEW_KEYS = List.of("remote_group_roles", "remote_group_nets",
            "remote_exception_roles", "remote_exception_nets", "attached_roles", "attached_nets");

    private static NodeIndex index(GameTestHelper helper) {
        return NodeIndex.get(helper.getLevel().getServer());
    }

    private static NodeIndex.Entry entry(GameTestHelper helper, RouterBlockEntity router) {
        NodeIndex.Entry entry = index(helper).entry(NodeKey.of(router.getLevel(), router.getBlockPos()));
        helper.assertTrue(entry != null, "roteador fora do índice");
        return entry;
    }

    private static void eq(GameTestHelper helper, Object actual, Object expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": esperado " + expected + ", veio " + actual);
    }

    private static int bit(ResourceType type, int role) {
        return NodeIndex.role(type, role);
    }

    private static boolean has(int roles, ResourceType type, int role) {
        return (roles & bit(type, role)) != 0;
    }

    /** A máscara {@code nets} da entrada passa no filtro da rede {@code network}. */
    private static boolean in(NodeIndex.Entry entry, long nets, UUID network) {
        return (nets & NetworkOrder.bits(entry, Set.of(network))) != 0;
    }

    private static UUID network(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rolesAndNetworksPerGroupExceptionAndAttached(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    UUID attachedNet = router.networkId(ResourceType.ITEM);
                    UUID profileNet = network(helper, "perfil");
                    UUID exceptionNet = network(helper, "exceção");
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BARREL);
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    int barrels = groupOf(router, helper.absolutePos(BARREL));
                    router.configureFace(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT, 0, RedstoneMode.IGNORE);
                    // O perfil das fornalhas: itens em cima e fluidos na Esquerda, os dois na mesma rede (duas abas).
                    EditTarget group = EditTarget.group(furnaces);
                    router.configureFace(group, ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0, RedstoneMode.IGNORE);
                    router.configureFace(group, ResourceType.FLUID, Direction.EAST, PortMode.INSERT, 0, RedstoneMode.IGNORE);
                    router.setFaceNetwork(group, ResourceType.ITEM, Direction.UP, profileNet);
                    router.setFaceNetwork(group, ResourceType.FLUID, Direction.EAST, profileNet);
                    // A segunda fornalha ganha uma exceção com Armazém de energia embaixo, noutra rede.
                    int b = router.remoteMachines().index(helper.absolutePos(FURNACE_B));
                    EditTarget machine = EditTarget.machine(b);
                    router.configureFace(machine, ResourceType.ENERGY, Direction.DOWN, PortMode.BOTH, 0,
                            RedstoneMode.IGNORE);
                    router.setFaceNetwork(machine, ResourceType.ENERGY, Direction.DOWN, exceptionNet);

                    NodeIndex.Entry entry = entry(helper, router);
                    helper.assertTrue(entry.machineRolesRead(), "papéis por máquina não lidos do roteador");
                    helper.assertTrue(has(entry.attachedRoles(), ResourceType.ITEM, NodeIndex.EXTRACT),
                            "Extrai de itens fora dos papéis da presa");
                    helper.assertFalse(has(entry.attachedRoles(), ResourceType.FLUID, NodeIndex.INSERT),
                            "o perfil entrou nos papéis da presa");
                    helper.assertTrue(in(entry, entry.attachedNets(), attachedNet), "a rede da presa fora da máscara");
                    helper.assertFalse(in(entry, entry.attachedNets(), profileNet), "a rede do perfil na máscara da presa");

                    IndexRemotes remotes = entry.remotes();
                    int g = remotes.groupIndex(furnaces);
                    int roles = remotes.groupRoles(g);
                    helper.assertTrue(has(roles, ResourceType.ITEM, NodeIndex.EXTRACT)
                            && has(roles, ResourceType.FLUID, NodeIndex.INSERT), "papéis do perfil: " + roles);
                    helper.assertFalse(has(roles, ResourceType.FLUID, NodeIndex.EXTRACT)
                            || has(roles, ResourceType.ENERGY, NodeIndex.STORAGE), "papel a mais no perfil: " + roles);
                    long nets = remotes.groupNets(g);
                    helper.assertTrue(in(entry, nets, profileNet), "a rede do perfil fora da máscara do grupo");
                    helper.assertFalse(in(entry, nets, exceptionNet), "a rede da exceção na máscara do grupo");
                    // Uma rede em duas abas é um bit só, e aparece uma vez na ordem.
                    eq(helper, entry.networkOrder().stream().filter(profileNet::equals).count(), 1L,
                            "a rede das duas abas na ordem");
                    eq(helper, Long.bitCount(nets & NetworkOrder.bits(entry, Set.of(profileNet))), 1, "bits da rede");

                    eq(helper, remotes.groupRoles(remotes.groupIndex(barrels)), 0, "papéis do grupo sem modo");
                    eq(helper, remotes.machineRoles(0), roles, "a fornalha sem exceção segue o perfil");
                    eq(helper, remotes.machineNets(0), nets, "as redes da fornalha sem exceção");
                    helper.assertTrue(remotes.hasException(b), "a exceção não chegou ao índice");
                    int exception = remotes.machineRoles(b);
                    helper.assertTrue(has(exception, ResourceType.ENERGY, NodeIndex.STORAGE),
                            "o Armazém de energia fora dos papéis da exceção");
                    helper.assertTrue(in(entry, remotes.machineNets(b), exceptionNet), "a rede da exceção fora da máscara");

                    // Mudar o modo ou a rede de uma face do perfil chega à entrada e sobe a dataVersion.
                    int version = index(helper).dataVersion();
                    router.configureFace(group, ResourceType.ITEM, Direction.UP, PortMode.INSERT, 0, RedstoneMode.IGNORE);
                    helper.assertTrue(index(helper).dataVersion() != version, "o modo do perfil não subiu a dataVersion");
                    remotes = entry(helper, router).remotes();
                    helper.assertTrue(has(remotes.groupRoles(remotes.groupIndex(furnaces)), ResourceType.ITEM,
                            NodeIndex.INSERT), "o modo novo fora dos papéis do grupo");
                    version = index(helper).dataVersion();
                    UUID moved = network(helper, "outra");
                    router.setFaceNetwork(group, ResourceType.FLUID, Direction.EAST, moved);
                    helper.assertTrue(index(helper).dataVersion() != version, "a rede do perfil não subiu a dataVersion");
                    entry = entry(helper, router);
                    remotes = entry.remotes();
                    helper.assertTrue(in(entry, remotes.groupNets(remotes.groupIndex(furnaces)), moved),
                            "a rede nova fora da máscara do grupo");

                    // Sem o Upgrade de Alcance, os papéis de grupos e exceções zeram e as redes ficam.
                    router.setRangeUpgrade(ItemStack.EMPTY);
                    entry = entry(helper, router);
                    remotes = entry.remotes();
                    eq(helper, remotes.machineRoles(0), 0, "papéis do grupo sem o upgrade");
                    eq(helper, remotes.machineRoles(b), 0, "papéis da exceção sem o upgrade");
                    helper.assertTrue(in(entry, remotes.machineNets(b), exceptionNet), "a rede da exceção sem o upgrade");
                    helper.assertTrue(has(entry.attachedRoles(), ResourceType.ITEM, NodeIndex.EXTRACT),
                            "os papéis da presa sem o upgrade");
                })
                .thenSucceed();
    }

    /** A ida e volta do NBT com as chaves novas, e o formato da 3a sem elas, que lê zero até o próximo aviso. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rolesRoundTripAndOldFormat(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    router.configureFace(ResourceType.ITEM, Direction.NORTH, PortMode.INSERT, 0, RedstoneMode.IGNORE);
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    router.configureFace(EditTarget.group(furnaces), ResourceType.ITEM, Direction.UP, PortMode.EXTRACT, 0,
                            RedstoneMode.IGNORE);
                    router.configureFace(EditTarget.machine(1), ResourceType.FLUID, Direction.DOWN, PortMode.INSERT, 0,
                            RedstoneMode.IGNORE);
                    NodeIndex.Entry entry = entry(helper, router);
                    CompoundTag saved = index(helper).save(new CompoundTag(), helper.getLevel().registryAccess());
                    CompoundTag tag = node(saved, entry.key());
                    helper.assertTrue(tag != null, "nó fora do arquivo");
                    for (String key : NEW_KEYS) {
                        helper.assertTrue(tag.contains(key), "faltou a chave " + key);
                    }
                    NodeIndex.Entry loaded = NodeIndex.load(saved, helper.getLevel().registryAccess()).entry(entry.key());
                    helper.assertTrue(loaded != null, "entrada perdida na ida e volta");
                    helper.assertFalse(loaded.machineRolesRead(), "a entrada lida do arquivo se diz lida do roteador");
                    eq(helper, loaded.attachedRoles(), entry.attachedRoles(), "papéis da presa na volta");
                    eq(helper, loaded.attachedNets(), entry.attachedNets(), "redes da presa na volta");
                    IndexRemotes now = entry.remotes();
                    IndexRemotes back = loaded.remotes();
                    for (int i = 0; i < now.size(); i++) {
                        eq(helper, back.machineRoles(i), now.machineRoles(i), "papéis da máquina " + i);
                        eq(helper, back.machineNets(i), now.machineNets(i), "redes da máquina " + i);
                    }
                    helper.assertTrue(now.machineRoles(0) != 0 && now.machineRoles(1) != now.machineRoles(0),
                            "papéis do grupo e da exceção iguais ou zero");

                    // O formato da 3a: sem as chaves novas, tudo zero, e o resto igual.
                    CompoundTag old = saved.copy();
                    CompoundTag oldNode = node(old, entry.key());
                    for (String key : NEW_KEYS) {
                        oldNode.remove(key);
                    }
                    NodeIndex.Entry fromOld = NodeIndex.load(old, helper.getLevel().registryAccess()).entry(entry.key());
                    helper.assertTrue(fromOld != null, "o formato da 3a perdeu a entrada");
                    eq(helper, fromOld.attachedRoles(), 0, "papéis da presa no formato da 3a");
                    eq(helper, fromOld.remotes().total(), now.total(), "máquinas no formato da 3a");
                    for (int i = 0; i < fromOld.remotes().size(); i++) {
                        eq(helper, fromOld.remotes().machineRoles(i), 0, "papéis da máquina " + i + " no formato da 3a");
                    }
                    eq(helper, fromOld.roles(), entry.roles(), "papéis do nó no formato da 3a");

                    // Um nó sem papéis por máquina não grava nenhuma das chaves novas.
                    router.configureFace(ResourceType.ITEM, Direction.NORTH, PortMode.NONE, 0, RedstoneMode.IGNORE);
                    router.setNetworkId(null);
                    router.setRangeUpgrade(ItemStack.EMPTY);
                    CompoundTag bare = node(index(helper).save(new CompoundTag(), helper.getLevel().registryAccess()),
                            entry.key());
                    for (String key : NEW_KEYS) {
                        helper.assertFalse(bare.contains(key), "nó sem papéis por máquina gravou " + key);
                    }
                })
                .thenSucceed();
    }

    /** Vincular, desvincular e quebrar o roteador mudam a soma das máquinas (medida dentro de um passo só). */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void machineSumFollowsLinksAndRemoval(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    NodeIndex index = index(helper);
                    router.setRangeUpgrade(rangeUpgrade());
                    int before = index.machines();
                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BARREL);
                    eq(helper, index.machines(), before + 3, "soma depois de vincular");
                    helper.assertTrue(router.unlinkMachine(helper.absolutePos(BARREL)), "não desvinculou");
                    eq(helper, index.machines(), before + 2, "soma depois de desvincular");
                    // Quebrar sai com a presa e as duas vinculadas.
                    helper.setBlock(ROUTER, Blocks.AIR);
                    eq(helper, index.machines(), before - 1, "soma depois de quebrar o roteador");
                })
                .thenSucceed();
    }

    private static CompoundTag node(CompoundTag saved, NodeKey key) {
        ListTag nodes = saved.getList("nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < nodes.size(); i++) {
            CompoundTag tag = nodes.getCompound(i);
            if (tag.getLong("pos") == key.pos().asLong()
                    && tag.getString("dim").equals(key.dimension().location().toString())) {
                return tag;
            }
        }
        return null;
    }
}
