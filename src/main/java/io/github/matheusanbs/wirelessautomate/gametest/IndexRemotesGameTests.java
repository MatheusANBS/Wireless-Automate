package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.network.IndexRemotes;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * As máquinas vinculadas no índice de nós ({@link IndexRemotes}): vincular, desvincular, renomear, a exceção e o
 * Atualizar chegam à entrada e sobem a {@code dataVersion}; a ida e volta do NBT, o formato antigo e o teto; e o
 * filtro por tipo do Tablet com um roteador que só usa as remotas.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class IndexRemotesGameTests {
    private IndexRemotesGameTests() {
    }

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

    /** A {@code dataVersion} subiu desde {@code before}; devolve a de agora. */
    private static int bumped(GameTestHelper helper, int before, String what) {
        int now = index(helper).dataVersion();
        helper.assertTrue(now != before, what + " não subiu a dataVersion");
        return now;
    }

    private static ResourceLocation id(net.minecraft.world.level.block.Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    /** O mesmo conteúdo, campo a campo. */
    private static void same(GameTestHelper helper, IndexRemotes a, IndexRemotes b, String what) {
        eq(helper, b.size(), a.size(), what + ": máquinas");
        eq(helper, b.total(), a.total(), what + ": total");
        eq(helper, b.groupCount(), a.groupCount(), what + ": grupos");
        for (int i = 0; i < a.size(); i++) {
            helper.assertTrue(a.pos(i) == b.pos(i) && a.group(i) == b.group(i) && a.label(i).equals(b.label(i))
                    && a.hasException(i) == b.hasException(i), what + ": máquina " + i);
        }
        for (int g = 0; g < a.groupCount(); g++) {
            helper.assertTrue(a.groupId(g) == b.groupId(g) && a.groupBlock(g).equals(b.groupBlock(g))
                    && a.groupName(g).equals(b.groupName(g)) && a.groupMachines(g) == b.groupMachines(g),
                    what + ": grupo " + g);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void linkRenameExceptionAndUnlinkReachTheIndex(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    eq(helper, entry(helper, router).remotes().total(), 0, "máquinas antes de vincular");
                    int version = index(helper).dataVersion();

                    link(helper, router, FURNACE_A);
                    link(helper, router, FURNACE_B);
                    link(helper, router, BARREL);
                    version = bumped(helper, version, "vincular");
                    IndexRemotes remotes = entry(helper, router).remotes();
                    eq(helper, remotes.total(), 3, "máquinas");
                    eq(helper, remotes.size(), 3, "máquinas guardadas");
                    // A ordem do índice é a do vínculo, a do "#N".
                    eq(helper, remotes.pos(0), helper.absolutePos(FURNACE_A).asLong(), "primeira");
                    eq(helper, remotes.pos(2), helper.absolutePos(BARREL).asLong(), "terceira");
                    int furnaces = groupOf(router, helper.absolutePos(FURNACE_A));
                    int barrels = groupOf(router, helper.absolutePos(BARREL));
                    eq(helper, remotes.group(1), furnaces, "grupo da segunda");
                    eq(helper, remotes.groupCount(), 2, "grupos");
                    int g = remotes.groupIndex(furnaces);
                    helper.assertTrue(g >= 0, "grupo das fornalhas fora do índice");
                    eq(helper, remotes.groupBlock(g), id(Blocks.FURNACE), "bloco do grupo");
                    eq(helper, remotes.groupMachines(g), 2, "fornalhas no grupo");
                    eq(helper, remotes.groupMachines(remotes.groupIndex(barrels)), 1, "barris no grupo");

                    int b = router.remoteMachines().index(helper.absolutePos(FURNACE_B));
                    NodeKey key = entry(helper, router).key();
                    CompoundTag before = node(index(helper).save(new CompoundTag(), helper.getLevel().registryAccess()), key);
                    int roles = entry(helper, router).roles();
                    long modes = entry(helper, router).faceModes();
                    helper.assertTrue(router.renameTarget(EditTarget.machine(b), "Forno dois"), "não renomeou a máquina");
                    version = bumped(helper, version, "o rótulo");
                    helper.assertTrue(entry(helper, router).roles() == roles && entry(helper, router).faceModes() == modes,
                            "o rótulo mudou papéis ou modos");
                    CompoundTag after = node(index(helper).save(new CompoundTag(), helper.getLevel().registryAccess()), key);
                    helper.assertTrue(after != null && after != before, "a tag guardada não foi refeita com o rótulo");
                    eq(helper, after.getCompound("remote_labels").getString(Integer.toString(b)), "Forno dois",
                            "remote_labels na tag nova");
                    helper.assertFalse(before.contains("remote_labels"), "rótulo na tag de antes");
                    eq(helper, entry(helper, router).remotes().label(b), "Forno dois", "rótulo no índice");
                    eq(helper, entry(helper, router).remotes().label(0), "", "rótulo de quem não tem");

                    helper.assertTrue(router.renameTarget(EditTarget.group(furnaces), "Fornos"), "não renomeou o grupo");
                    version = bumped(helper, version, "o nome do grupo");
                    remotes = entry(helper, router).remotes();
                    eq(helper, remotes.groupName(remotes.groupIndex(furnaces)), "Fornos", "nome do grupo no índice");

                    router.configureFace(EditTarget.machine(0), ResourceType.FLUID, Direction.UP, PortMode.INSERT, 0,
                            RedstoneMode.IGNORE);
                    version = bumped(helper, version, "a exceção");
                    remotes = entry(helper, router).remotes();
                    helper.assertTrue(remotes.hasException(0) && !remotes.hasException(1), "exceção no índice");

                    // A ida e volta da entrada, com rótulo, nome de grupo e exceção.
                    CompoundTag saved = index(helper).save(new CompoundTag(), helper.getLevel().registryAccess());
                    NodeIndex.Entry loaded = NodeIndex.load(saved, helper.getLevel().registryAccess())
                            .entry(entry(helper, router).key());
                    helper.assertTrue(loaded != null, "entrada perdida na ida e volta");
                    same(helper, remotes, loaded.remotes(), "ida e volta");
                    CompoundTag tag = node(saved, entry(helper, router).key());
                    helper.assertTrue(tag != null && tag.contains("remote_pos") && !tag.contains("remote_total"),
                            "chaves das máquinas");

                    // O Atualizar: a fornalha virou alto-forno e passa para um grupo novo.
                    helper.setBlock(FURNACE_A, Blocks.BLAST_FURNACE.defaultBlockState()
                            .setValue(AbstractFurnaceBlock.FACING, Direction.EAST));
                    router.updateReplaced(List.of(0));
                    version = bumped(helper, version, "mudar o grupo");
                    remotes = entry(helper, router).remotes();
                    int blasts = groupOf(router, helper.absolutePos(FURNACE_A));
                    helper.assertTrue(blasts != furnaces, "não mudou de grupo");
                    eq(helper, remotes.group(0), blasts, "grupo novo no índice");
                    eq(helper, remotes.groupBlock(remotes.groupIndex(blasts)), id(Blocks.BLAST_FURNACE), "bloco do grupo novo");
                    eq(helper, remotes.groupMachines(remotes.groupIndex(furnaces)), 1, "fornalhas depois do Atualizar");

                    helper.assertTrue(router.unlinkMachine(helper.absolutePos(BARREL)), "não desvinculou");
                    bumped(helper, version, "desvincular");
                    remotes = entry(helper, router).remotes();
                    eq(helper, remotes.total(), 2, "máquinas depois de desvincular");
                    eq(helper, remotes.groupIndex(barrels), -1, "o grupo vazio ficou");

                    // Sem mudança nenhuma, o aviso não suja o índice.
                    int still = index(helper).dataVersion();
                    NodeIndex.track(router);
                    eq(helper, index(helper).dataVersion(), still, "aviso sem mudança");
                })
                .thenSucceed();
    }

    /** O formato antigo, sem as chaves, lê sem máquinas; um nó sem máquinas não grava nenhuma delas. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void oldFormatReadsWithoutMachines(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, io.github.matheusanbs.wirelessautomate.block.RouterTier.BASIC);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    NodeIndex.track(router);
                    CompoundTag saved = index(helper).save(new CompoundTag(), helper.getLevel().registryAccess());
                    CompoundTag tag = node(saved, entry(helper, router).key());
                    helper.assertTrue(tag != null, "nó fora do arquivo");
                    for (String key : List.of("remote_pos", "remote_group", "remote_exception", "remote_labels",
                            "remote_groups", "remote_total")) {
                        helper.assertFalse(tag.contains(key), "nó sem máquinas gravou " + key);
                    }
                    IndexRemotes read = IndexRemotes.loadForTests(tag);
                    helper.assertTrue(read.isEmpty() && read.size() == 0 && read.groupCount() == 0,
                            "o formato antigo leu máquinas");
                })
                .thenSucceed();
    }

    /** Acima de 512, ficam as 512 primeiras, a contagem total e as contagens dos grupos com todas; e voltam do NBT. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void capKeepsFirstMachinesAndTotal(GameTestHelper helper) {
        RemoteMachines machines = new RemoteMachines();
        ResourceLocation chest = id(Blocks.CHEST);
        ResourceLocation barrel = id(Blocks.BARREL);
        int count = IndexRemotes.CAP + 88;
        for (int i = 0; i < count; i++) {
            machines.link(new BlockPos(i, 64, 0), i < 10 ? barrel : chest, -1, null);
        }
        IndexRemotes remotes = IndexRemotes.ofForTests(machines);
        eq(helper, remotes.size(), IndexRemotes.CAP, "guardadas");
        eq(helper, remotes.total(), count, "total");
        eq(helper, remotes.pos(IndexRemotes.CAP - 1), new BlockPos(IndexRemotes.CAP - 1, 64, 0).asLong(), "a última guardada");
        eq(helper, remotes.groupMachines(remotes.groupIndex(machines.machines().get(20).group())), count - 10,
                "baús no grupo, contando os de cima do teto");
        helper.assertTrue(remotes.matchesForTests(machines), "não bate com as próprias máquinas");

        CompoundTag tag = new CompoundTag();
        IndexRemotes back = IndexRemotes.roundTripForTests(remotes, tag);
        eq(helper, tag.getInt("remote_total"), count, "remote_total");
        eq(helper, tag.getLongArray("remote_pos").length, IndexRemotes.CAP, "remote_pos");
        same(helper, remotes, back, "ida e volta acima do teto");

        // Uma máquina acima do teto que troca de grupo, com o total igual: só as contagens dos grupos mudam.
        int above = IndexRemotes.CAP + 40;
        machines.regroup(Map.of(above, barrel));
        eq(helper, machines.size(), count, "o total mudou no regroup");
        helper.assertFalse(remotes.matchesForTests(machines), "a troca de grupo acima do teto passou");
        remotes = IndexRemotes.ofForTests(machines);
        helper.assertTrue(remotes.matchesForTests(machines), "não bate depois de remontar");

        // Uma máquina acima do teto que sai muda o índice, mesmo fora das guardadas.
        machines.unlink(count - 1);
        helper.assertFalse(remotes.matchesForTests(machines), "a saída de uma máquina acima do teto passou");
        helper.succeed();
    }

    /** Um roteador que só extrai das remotas (a presa sem modo) aparece no filtro "Só Itens" do Tablet. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void typeFilterSeesRemoteOnlyRouter(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        NodeKey key = NodeKey.of(router.getLevel(), router.getBlockPos());
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, FURNACE_A);
                    profile(helper, router, FURNACE_A, Direction.UP, PortMode.EXTRACT);
                    // Lado canônico Leste é a Esquerda da remota (a fornalha está virada para leste).
                    profile(helper, router, FURNACE_A, Direction.EAST, PortMode.INSERT);
                    NodeIndex.placedBy(router, player.getUUID());
                    NodeIndex.Entry entry = entry(helper, router);
                    helper.assertTrue((entry.roles() & NodeIndex.role(ResourceType.ITEM, NodeIndex.EXTRACT)) != 0,
                            "o perfil não entrou nos papéis");
                    eq(helper, entry.faceMode(ResourceType.ITEM, RemoteSide.TOP), PortMode.EXTRACT, "quadradinho de cima");
                    eq(helper, entry.faceMode(ResourceType.ITEM, RemoteSide.LEFT), PortMode.INSERT, "quadradinho da Esquerda");
                    eq(helper, entry.faceMode(ResourceType.ITEM, RemoteSide.RIGHT), PortMode.NONE, "quadradinho da Direita");

                    TabletMenu menu = new TabletMenu(91, player.getInventory(), player);
                    player.containerMenu = menu;
                    try {
                        menu.setQuery(Query.DEFAULT.withType(Optional.of(ResourceType.ITEM)));
                        menu.pollSnapshot();
                        TabletSnapshot items = menu.snapshot();
                        helper.assertTrue(items.nodes().stream().anyMatch(node -> node.key().equals(key)),
                                "o roteador das remotas ficou fora do filtro de itens");
                        menu.setQuery(Query.DEFAULT.withType(Optional.of(ResourceType.FLUID)));
                        menu.pollSnapshot();
                        helper.assertFalse(menu.snapshot().nodes().stream().anyMatch(node -> node.key().equals(key)),
                                "o roteador das remotas entrou no filtro de fluidos");

                        // Sem o Upgrade de Alcance as remotas não movem: saem dos papéis e dos modos, e voltam com ele.
                        router.setRangeUpgrade(ItemStack.EMPTY);
                        eq(helper, entry(helper, router).roles(), 0, "papéis sem o upgrade");
                        eq(helper, entry(helper, router).faceModes(), 0L, "modos sem o upgrade");
                        eq(helper, entry(helper, router).remotes().total(), 1, "a máquina saiu do índice sem o upgrade");
                        helper.assertFalse(listedItems(menu, key), "sem o upgrade ficou no filtro de itens");
                        router.setRangeUpgrade(rangeUpgrade());
                        helper.assertTrue(listedItems(menu, key), "com o upgrade de volta ficou fora do filtro de itens");

                        // A única fornalha ganha uma exceção sem o Extrai de cima: o perfil, que ninguém segue, sai.
                        router.configureFace(EditTarget.machine(0), ResourceType.ITEM, Direction.UP, PortMode.NONE, 0,
                                RedstoneMode.IGNORE);
                        NodeIndex.Entry now = entry(helper, router);
                        helper.assertTrue((now.roles() & NodeIndex.role(ResourceType.ITEM, NodeIndex.EXTRACT)) == 0,
                                "o perfil sem máquinas que o seguem contou nos papéis");
                        eq(helper, now.faceMode(ResourceType.ITEM, RemoteSide.TOP), PortMode.NONE, "cima depois da exceção");
                        eq(helper, now.faceMode(ResourceType.ITEM, RemoteSide.LEFT), PortMode.INSERT, "Esquerda da exceção");
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                })
                .thenSucceed();
    }

    private static boolean listedItems(TabletMenu menu, NodeKey key) {
        menu.setQuery(Query.DEFAULT.withType(Optional.of(ResourceType.ITEM)));
        menu.pollSnapshot();
        return menu.snapshot().nodes().stream().anyMatch(node -> node.key().equals(key));
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
