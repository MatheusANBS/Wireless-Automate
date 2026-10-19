package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O índice de nós: a entrada de um roteador que sumiu sem {@code onRemove} sai quando o chunk dele carrega, o
 * formato antigo continua lendo e salvar reaproveita a tag de quem não mudou.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class NodeIndexGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 0);

    private static RouterBlockEntity routerOnChest(GameTestHelper helper, BlockPos machine) {
        helper.setBlock(machine, Blocks.CHEST);
        helper.setBlock(machine.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.BASIC));
        return helper.getBlockEntity(machine.above());
    }

    /**
     * O roteador em {@code A} some sem aviso (o block entity sai e o bloco vira ar direto na seção do chunk, sem
     * {@code onRemove}): a entrada fica, descarregada, até o chunk carregar. Na carga, ela sai e a do roteador que
     * continua no lugar fica.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void vanishedRouterLeavesOnChunkLoad(GameTestHelper helper) {
        RouterBlockEntity gone = routerOnChest(helper, A);
        RouterBlockEntity kept = routerOnChest(helper, B);
        ServerLevel level = helper.getLevel();
        BlockPos gonePos = helper.absolutePos(A.above());
        NodeKey goneKey = NodeKey.of(level, gonePos);
        NodeKey keptKey = NodeKey.of(level, helper.absolutePos(B.above()));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(gone)
                        && NetworkManager.get().contains(kept), "sem onLoad"))
                .thenExecute(() -> {
                    NodeIndex index = NodeIndex.get(level.getServer());
                    helper.assertTrue(index.entry(goneKey) != null && index.entry(keptKey) != null, "fora do índice");
                    level.removeBlockEntity(gonePos);
                    LevelChunk chunk = level.getChunkAt(gonePos);
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(gonePos.getY()));
                    section.setBlockState(gonePos.getX() & 15, gonePos.getY() & 15, gonePos.getZ() & 15,
                            Blocks.AIR.defaultBlockState());
                    NodeIndex.Entry stale = index.entry(goneKey);
                    helper.assertTrue(stale != null && !stale.loaded(), "a entrada sumiu sem a carga do chunk");

                    int before = index.dataVersion();
                    NodeIndex.chunkLoaded(level, chunk);
                    NodeIndex.chunkLoaded(level, level.getChunkAt(keptKey.pos()));
                    helper.assertTrue(index.entry(goneKey) == null, "a entrada do roteador sumido ficou");
                    helper.assertTrue(index.entry(keptKey) != null, "a carga tirou o roteador que continua lá");
                    helper.assertTrue(index.dataVersion() != before, "a dataVersion não mudou");
                    int after = index.dataVersion();
                    NodeIndex.chunkLoaded(level, chunk);
                    helper.assertTrue(index.dataVersion() == after, "a carga sem entrada sumida mexeu no índice");
                })
                .thenSucceed();
    }

    /**
     * Salvar duas vezes sem mudança devolve a mesma tag da entrada; mudar o nome refaz só ela. Um nó sem faces não
     * grava {@code roles} nem {@code face_modes}, e a leitura dá zero sem elas, como num arquivo antigo; a entrada
     * antiga, com {@code networks} e os campos todos, continua lendo.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void savedTagsAreReusedAndOldFormatLoads(GameTestHelper helper) {
        RouterBlockEntity router = routerOnChest(helper, A);
        ServerLevel level = helper.getLevel();
        NodeKey key = NodeKey.of(level, helper.absolutePos(A.above()));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad"))
                .thenExecute(() -> {
                    NodeIndex index = NodeIndex.get(level.getServer());
                    CompoundTag first = node(index.save(new CompoundTag(), level.registryAccess()), key);
                    CompoundTag second = node(index.save(new CompoundTag(), level.registryAccess()), key);
                    helper.assertTrue(first != null && first == second, "a tag da entrada sem mudança foi refeita");
                    helper.assertFalse(first.contains("roles") || first.contains("face_modes"),
                            "nó sem faces gravou roles ou face_modes");

                    router.setName("Índice novo");
                    CompoundTag renamed = node(index.save(new CompoundTag(), level.registryAccess()), key);
                    helper.assertTrue(renamed != null && renamed != first && renamed.getString("name").equals("Índice novo"),
                            "a tag não foi refeita depois de mudar o nome");

                    // Uma entrada no formato antigo: uma rede por tipo em "networks", papéis e modos gravados.
                    UUID network = UUID.randomUUID();
                    UUID placer = UUID.randomUUID();
                    CompoundTag legacy = new CompoundTag();
                    legacy.putString("dim", Level.OVERWORLD.location().toString());
                    legacy.putLong("pos", new BlockPos(7, 8, 9).asLong());
                    legacy.putString("name", "Antigo");
                    legacy.putString("tier", RouterTier.ELITE.getSerializedName());
                    CompoundTag networks = new CompoundTag();
                    networks.putUUID(ResourceType.ITEM.key(), network);
                    legacy.put("networks", networks);
                    legacy.putInt("roles", NodeIndex.role(ResourceType.ITEM, NodeIndex.EXTRACT));
                    legacy.putLong("face_modes", 9L);
                    legacy.putString("machine", "minecraft:barrel");
                    legacy.putUUID("placedBy", placer);
                    ListTag nodes = new ListTag();
                    nodes.add(legacy);
                    CompoundTag file = new CompoundTag();
                    file.put("nodes", nodes);
                    NodeIndex loaded = NodeIndex.load(file, level.registryAccess());
                    NodeIndex.Entry old = loaded.entry(new NodeKey(Level.OVERWORLD, new BlockPos(7, 8, 9)));
                    helper.assertTrue(old != null, "entrada antiga perdida");
                    helper.assertTrue(old.name().equals("Antigo") && old.tier() == RouterTier.ELITE, "nome ou tier antigos");
                    helper.assertTrue(old.networks(ResourceType.ITEM).equals(List.of(network))
                            && old.networks(ResourceType.FLUID).isEmpty(), "redes antigas");
                    helper.assertTrue(old.roles() == NodeIndex.role(ResourceType.ITEM, NodeIndex.EXTRACT)
                            && old.faceModes() == 9L, "papéis ou modos antigos");
                    helper.assertTrue(old.machine().toString().equals("minecraft:barrel") && placer.equals(old.placedBy()),
                            "máquina ou dono antigos");

                    // E volta: a entrada antiga regravada no formato de hoje dá o mesmo.
                    NodeIndex.Entry again = NodeIndex.load(loaded.save(new CompoundTag(), level.registryAccess()),
                            level.registryAccess()).entry(old.key());
                    helper.assertTrue(again != null && again.networks(ResourceType.ITEM).equals(List.of(network))
                            && again.roles() == old.roles() && again.faceModes() == 9L, "ida e volta da entrada antiga");

                    // Sem roles e face_modes (o que o índice grava hoje para um nó sem faces): zero.
                    legacy.remove("roles");
                    legacy.remove("face_modes");
                    NodeIndex.Entry bare = NodeIndex.load(file, level.registryAccess()).entry(old.key());
                    helper.assertTrue(bare != null && bare.roles() == 0 && bare.faceModes() == 0L, "sem as chaves não deu zero");
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
