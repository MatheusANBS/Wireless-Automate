package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.CapCacheChunks;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NetworkStats;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Porte 1.20.1: o índice de chunks do {@code CapCache} ({@code CapCacheChunks}), num namespace próprio
 * ({@value #NAMESPACE}, template {@code data/wirelessautomate_chunks/structures/empty.nbt}) que a run comum liga
 * junto com o do mod ({@code forge.enabledGameTestNamespaces}). Gera, salva e lê centenas de chunks longe dos outros
 * testes: num lote próprio ({@code chunks}), e só passa depois de os chunks descarregarem. A ordem dos lotes não
 * importa: os testes de vazão da run comum medem o que uma visita move, não o tempo.
 */
@GameTestHolder(ChunkCapCacheGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class ChunkCapCacheGameTests {
    static final String NAMESPACE = "wirelessautomate_chunks";
    /** Ticket dos chunks do teste; sem prazo. */
    private static final TicketType<ChunkPos> TEST_TICKET = TicketType.create("wirelessautomate_capcache_test",
            Comparator.comparingLong(ChunkPos::toLong));
    /** O destino tem de dormir pelo menos isto antes da promoção, para o limite de 40 ticks discriminar. */
    private static final int DEEP_SLEEP = 60;

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    private static int count(@Nullable BlockEntity be, Item item) {
        if (!(be instanceof ChestBlockEntity chest)) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static @Nullable NetworkStats stats(ServerLevel level, UUID network) {
        for (NetworkStats stats : NetworkManager.get().stats(level.getServer())) {
            if (stats.id().equals(network)) {
                return stats;
            }
        }
        return null;
    }

    /**
     * O chunk da máquina carregando acorda a porta (o índice de chunks dos caches, como o
     * {@code invalidateCapabilities(ChunkPos)} do NeoForge). Longe dos outros testes, num par de chunks vizinhos
     * presos por um ticket: o roteador de destino no chunk R, na borda, e o baú dele no chunk M. Os dois descarregam
     * de verdade (sem ticket). Depois só R volta, como borda de um ticket de nível 33 (carregado, com M ainda fora):
     * o destino consulta a máquina com o chunk dela fora e dorme sem máquina. Promover M a carregado (o ticket ganha
     * raio 1) posta o {@code ChunkEvent.Load} dele, o cache é invalidado, a porta acorda e os diamantes entram logo,
     * sem esperar o teto do sono (100 ticks). Nada lê o chunk M antes da promoção (o {@code getBlockState} o
     * carregaria): só {@code hasChunk}. Num lote próprio (ver a classe).
     */
    @GameTest(template = "empty", timeoutTicks = 2400, batch = "chunks")
    public static void machineChunkLoadWakesPort(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        ChunkPos routerChunk = new ChunkPos((base.getX() >> 4) + 512, base.getZ() >> 4);
        ChunkPos machineChunk = new ChunkPos(routerChunk.x + 1, routerChunk.z);
        int y = base.getY() + 1;
        int z = routerChunk.getMinBlockZ() + 8;
        BlockPos sourceChest = new BlockPos(routerChunk.getMinBlockX() + 8, y, z);
        BlockPos sourceRouter = sourceChest.above();
        // O roteador na última coluna de R, preso pelo lado oeste do baú em M (facing=WEST: o roteador fica a oeste).
        BlockPos targetRouter = new BlockPos(routerChunk.getMaxBlockX(), y, z);
        BlockPos targetChest = targetRouter.east();
        UUID network = newNetwork(helper, "teste-cache-chunk");
        BlockEntity[] unloading = new BlockEntity[2];
        long[] promotedAt = {0};
        int chunksBefore = level.getChunkSource().getLoadedChunksCount();

        helper.startSequence()
                // Os dois chunks carregados (R com nível 32, M com 33).
                .thenExecute(() -> level.getChunkSource().addRegionTicket(TEST_TICKET, routerChunk, 1, routerChunk))
                .thenWaitUntil(() -> helper.assertTrue(level.hasChunk(routerChunk.x, routerChunk.z)
                        && level.hasChunk(machineChunk.x, machineChunk.z), "os chunks não carregaram"))
                .thenExecute(() -> {
                    level.setBlock(sourceChest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(targetChest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(sourceRouter, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP),
                            Block.UPDATE_ALL);
                    level.setBlock(targetRouter, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.WEST),
                            Block.UPDATE_ALL);
                    RouterBlockEntity source = (RouterBlockEntity) level.getBlockEntity(sourceRouter);
                    RouterBlockEntity target = (RouterBlockEntity) level.getBlockEntity(targetRouter);
                    source.setNetworkId(network);
                    source.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    target.setNetworkId(network);
                    target.setMode(ResourceType.ITEM, Direction.WEST, PortMode.INSERT);
                    unloading[0] = source;
                    unloading[1] = level.getBlockEntity(targetChest);
                })
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains((RouterBlockEntity) unloading[0]),
                        "roteador não registrado"))
                // Sem ticket, os dois descarregam de verdade (o roteador e o baú saem do mundo).
                .thenExecute(() -> {
                    level.getChunkSource().removeRegionTicket(TEST_TICKET, routerChunk, 1, routerChunk);
                })
                .thenWaitUntil(() -> helper.assertTrue(unloading[0].isRemoved() && unloading[1].isRemoved(),
                        "os chunks não descarregaram"))
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, CapCacheChunks.count(level, machineChunk), 0,
                            "caches do chunk da máquina depois de descarregar");
                    // Só R volta: nível 33 nele, 34 em M (fora).
                    level.getChunkSource().addRegionTicket(TEST_TICKET, routerChunk, 0, routerChunk);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(level.hasChunk(routerChunk.x, routerChunk.z), "o chunk do roteador não voltou");
                    BlockEntity target = level.getBlockEntity(targetRouter);
                    BlockEntity source = level.getBlockEntity(sourceRouter);
                    helper.assertTrue(target instanceof RouterBlockEntity && source instanceof RouterBlockEntity,
                            "os roteadores não voltaram: " + target + ", " + source + ", " + level.getBlockState(targetRouter));
                    helper.assertTrue(NetworkManager.get().contains((RouterBlockEntity) target)
                            && NetworkManager.get().contains((RouterBlockEntity) source), "os roteadores não se registraram");
                })
                .thenExecute(() -> {
                    helper.assertFalse(level.hasChunk(machineChunk.x, machineChunk.z), "o chunk da máquina carregou junto");
                    ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(sourceChest);
                    chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
                })
                .thenWaitUntil(() -> {
                    NetworkStats stats = stats(level, network);
                    helper.assertTrue(stats != null && stats.destinationsSleeping() == 1, "o destino com o chunk fora não dormiu");
                    helper.assertFalse(level.hasChunk(machineChunk.x, machineChunk.z), "o chunk da máquina carregou");
                    // Os caches do destino (o de itens do Baú do mod e o de itens) miram o chunk da máquina.
                    helper.assertTrue(CapCacheChunks.count(level, machineChunk) >= 1, "nenhum cache no índice do chunk da máquina");
                })
                .thenExecute(() -> {
                    // Sono fundo antes de promover: sem o índice, a entrega só sairia quando ele vencesse.
                    RouterBlockEntity target = (RouterBlockEntity) level.getBlockEntity(targetRouter);
                    long left = NetworkManager.get().destinationSleepLeft(target, ResourceType.ITEM, Direction.WEST,
                            level.getServer().getTickCount());
                    helper.assertTrue(left >= DEEP_SLEEP, "o destino dorme só mais " + left + " ticks");
                    level.getChunkSource().addRegionTicket(TEST_TICKET, routerChunk, 1, routerChunk);
                    promotedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(level.hasChunk(machineChunk.x, machineChunk.z), "o chunk da máquina não carregou");
                    GameTestCompat.assertValueEqual(helper, count(level.getBlockEntity(targetChest), Items.DIAMOND), 10,
                            "diamantes no baú do chunk da máquina");
                })
                .thenExecute(() -> {
                    long ticks = helper.getTick() - promotedAt[0];
                    helper.assertTrue(ticks < 40, "a entrega demorou " + ticks + " ticks depois de o chunk carregar");
                    level.getChunkSource().removeRegionTicket(TEST_TICKET, routerChunk, 0, routerChunk);
                    level.getChunkSource().removeRegionTicket(TEST_TICKET, routerChunk, 1, routerChunk);
                })
                // Espera os chunks gerados em volta descarregarem: senão salvar e descarregar entra no lote seguinte.
                .thenWaitUntil(() -> helper.assertTrue(level.getChunkSource().getLoadedChunksCount() <= chunksBefore,
                        "chunks do teste ainda carregados: " + level.getChunkSource().getLoadedChunksCount() + " > " + chunksBefore))
                .thenSucceed();
    }
}
