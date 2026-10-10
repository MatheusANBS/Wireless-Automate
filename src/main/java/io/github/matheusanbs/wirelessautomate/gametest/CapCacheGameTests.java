package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
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
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Porte 1.20.1: o {@code CapCache} do roteador (o papel do {@code BlockCapabilityCache} do NeoForge) e os handlers
 * de blocos vanilla sem block entity ({@code VanillaBlockHandlers}). Cada teste prova uma regra de quando o cache
 * se refaz: bloco da máquina trocado, máquina que passa a oferecer a capability sem trocar de block entity, máquina
 * quebrada e recolocada com o roteador vivo, mudança só de estado (não acorda), o chunk da máquina carregando; e o
 * compostor recebendo e entregando. Máquinas em y=1 com o roteador em cima (facing=UP), salvo onde diz.
 *
 * <p>Em lotes próprios ({@code capcache} e {@code capcache_chunks}): os lotes comuns ficam com a mesma composição de
 * antes, e os testes de vazão por tick deles (o orçamento do motor é em ms) não dividem o tick com estes. O de
 * chunks gera e salva centenas de chunks longe dali, e o trabalho dos workers e do coletor de lixo atrasava os
 * testes de vazão dos lotes seguintes: o nome {@code capcache_chunks} o põe por último (o {@code GameTestRunner} do
 * 1.20.1 agrupa os lotes num {@code HashMap}; o balde de {@code capcache_chunks} vem depois do de
 * {@code defaultBatch}).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class CapCacheGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final BlockPos C = new BlockPos(2, 1, 0);
    private static final BlockPos D = new BlockPos(0, 1, 2);
    /** Uma origem que já dormiu várias vezes seguidas tem o próximo sono pelo menos assim (ticks). */
    private static final int DEEP_SLEEP = 32;
    /** Ticket dos chunks do teste de carga ({@link #machineChunkLoadWakesPort}); sem prazo. */
    private static final TicketType<ChunkPos> TEST_TICKET = TicketType.create("wirelessautomate_capcache_test",
            Comparator.comparingLong(ChunkPos::toLong));

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    private static RouterBlockEntity place(GameTestHelper helper, BlockPos machine, BlockState machineState, UUID network) {
        helper.setBlock(machine, machineState);
        BlockPos routerPos = machine.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, routerPos);
        router.setNetworkId(network);
        return router;
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

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        return count(helper.getBlockEntity(pos), item);
    }

    private static void waitRegistered(GameTestHelper helper, RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
        }
    }

    private static @Nullable NetworkStats stats(ServerLevel level, UUID network) {
        for (NetworkStats stats : NetworkManager.get().stats(level.getServer())) {
            if (stats.id().equals(network)) {
                return stats;
            }
        }
        return null;
    }

    private static void assertDestinationSleeping(GameTestHelper helper, UUID network) {
        NetworkStats stats = stats(helper.getLevel(), network);
        helper.assertTrue(stats != null && stats.destinationsSleeping() == 1, "o destino sem a capability não dormiu");
    }

    /**
     * Trocar a máquina por outro bloco com outra capability: o destino de fluido está num baú (só itens) e dorme sem
     * máquina; trocar o baú por um caldeirão vazio (o bloco mudou, o roteador invalida os caches) faz o roteador usar o
     * handler do caldeirão, e a água sai logo, sem esperar o teto do sono.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "capcache")
    public static void machineSwappedForBlockWithOtherCapability(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-cache-troca");
        RouterBlockEntity source = place(helper, A,
                Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), network);
        source.setMode(ResourceType.FLUID, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity target = place(helper, B, Blocks.CHEST.defaultBlockState(), network);
        target.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);
        long[] swappedAt = {0};

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> assertDestinationSleeping(helper, network))
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.WATER_CAULDRON, A);
                    helper.setBlock(B, Blocks.CAULDRON);
                    swappedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(Blocks.CAULDRON, A);
                    helper.assertBlockPresent(Blocks.WATER_CAULDRON, B);
                    helper.assertBlockProperty(B, LayeredCauldronBlock.LEVEL, 3);
                })
                .thenExecute(() -> helper.assertTrue(helper.getTick() - swappedAt[0] < 10,
                        "a entrega demorou " + (helper.getTick() - swappedAt[0]) + " ticks depois da troca"))
                .thenSucceed();
    }

    /**
     * Cache negativo com o mesmo block entity: o tanque de teste não oferece fluido (o lado "nenhum" do Mekanism) e o
     * destino dorme sem máquina; ligado ({@link TestMachines#switchOn}, sem trocar de block entity, com o aviso aos
     * vizinhos de uma máquina real), o roteador pergunta de novo ao cache nulo, acorda e a água entra.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "capcache")
    public static void machineStartsOfferingWithoutNewBlockEntity(GameTestHelper helper) {
        if (!TestMachines.enabled()) {
            helper.fail("precisa das máquinas de teste (-Dwirelessautomate.gameTests=true)");
            return;
        }
        UUID network = newNetwork(helper, "teste-cache-negativo");
        RouterBlockEntity source = place(helper, A,
                Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), network);
        source.setMode(ResourceType.FLUID, Direction.UP, PortMode.EXTRACT);
        BlockPos tank = helper.absolutePos(B);
        TestMachines.reset(tank);
        RouterBlockEntity target = place(helper, B, TestMachines.SWITCH_TANK.get().defaultBlockState(), network);
        target.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);
        BlockEntity[] machine = {null};
        long[] switchedAt = {0};

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> assertDestinationSleeping(helper, network))
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.WATER_CAULDRON, A);
                    machine[0] = helper.getBlockEntity(B);
                    TestMachines.switchOn(helper.getLevel(), tank);
                    switchedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, TestMachines.switchTank(tank).getFluidAmount(), 1_000,
                        "água no tanque ligado"))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.CAULDRON, A);
                    helper.assertTrue(helper.getBlockEntity(B) == machine[0], "o block entity do tanque foi trocado");
                    helper.assertTrue(helper.getTick() - switchedAt[0] < 10,
                            "a entrega demorou " + (helper.getTick() - switchedAt[0]) + " ticks depois de ligar");
                })
                .thenSucceed();
    }

    /**
     * Máquina quebrada e recolocada com o roteador vivo: o baú da origem sai sem avisar os vizinhos (o roteador não
     * vê o ar e não cai) e um baú novo entra no lugar, com outro block entity e o mesmo bloco. O roteador continua o
     * mesmo e tira do baú novo.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "capcache")
    public static void machineBrokenAndReplacedWithRouterAlive(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-cache-recolocar");
        RouterBlockEntity source = place(helper, A, Blocks.CHEST.defaultBlockState(), network);
        source.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity target = place(helper, B, Blocks.CHEST.defaultBlockState(), network);
        target.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        BlockEntity[] old = {null};

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    BlockPos chest = helper.absolutePos(A);
                    old[0] = level.getBlockEntity(chest);
                    // Sem avisar vizinhos nem formas (o roteador cairia sem a máquina): só o bloco sai.
                    level.setBlock(chest, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
                    ChestBlockEntity fresh = GameTestCompat.getBlockEntity(helper, A);
                    helper.assertTrue(fresh != old[0] && old[0].isRemoved(), "o baú não foi trocado");
                    fresh.setItem(0, new ItemStack(Items.DIAMOND, 10));
                })
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, count(helper, B, Items.DIAMOND), 10, "diamantes em B"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(A.above()) == source && !source.isRemoved(), "o roteador foi trocado");
                    GameTestCompat.assertValueEqual(helper, count(helper, A, Items.DIAMOND), 0, "diamantes no baú novo");
                })
                .thenSucceed();
    }

    /**
     * Mudança só de estado não acorda: a fogueira (block entity sem capability de itens) da origem dorme fundo
     * sem o que tirar; apagá-la (mesmo bloco, mesmo block entity) chega ao roteador pelo {@code neighborChanged}, e os
     * caches negativos perguntam de novo, mas sem capability nova não avisam: a porta continua dormindo. (Uma
     * máquina com sinal de comparador, como a fornalha, acorda as portas pelo {@code onNeighborChange} do roteador,
     * como no {@code main}: o aviso de conteúdo mudado.)
     */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "capcache")
    public static void machineStateChangeDoesNotWakeSleepingPort(GameTestHelper helper) {
        UUID network = newNetwork(helper, "teste-cache-estado");
        RouterBlockEntity source = place(helper, A, Blocks.CAMPFIRE.defaultBlockState(), network);
        source.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity target = place(helper, B, Blocks.CHEST.defaultBlockState(), network);
        target.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> {
                    int interval = NetworkManager.get().sourceSleepInterval(source, ResourceType.ITEM, Direction.UP);
                    helper.assertTrue(interval >= DEEP_SLEEP, "a fogueira ainda não dorme fundo: " + interval);
                })
                .thenExecute(() -> {
                    BlockEntity campfire = helper.getBlockEntity(A);
                    int version = source.changeVersion();
                    helper.setBlock(A, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
                    helper.assertTrue(helper.getBlockEntity(A) == campfire, "o block entity da fogueira foi trocado");
                    GameTestCompat.assertValueEqual(helper, source.changeVersion(), version, "versão depois de apagar a fogueira");
                    int interval = NetworkManager.get().sourceSleepInterval(source, ResourceType.ITEM, Direction.UP);
                    helper.assertTrue(interval >= DEEP_SLEEP, "apagar a fogueira acordou a porta: próximo sono de " + interval);
                })
                .thenSucceed();
    }

    /**
     * Compostor sem block entity (o handler do NeoForge, refeito a cada chamada): o roteador de cima põe 7 tortas de
     * abóbora (chance 100%), o compostor chega ao nível 7 e, 20 ticks depois, ao 8; o roteador de baixo (facing=DOWN)
     * tira a farinha de osso para outro baú. Duas redes, para as tortas não irem direto para o baú da farinha.
     */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "capcache")
    public static void composterTakesCompostAndGivesBoneMeal(GameTestHelper helper) {
        UUID input = newNetwork(helper, "teste-compostor-entrada");
        UUID output = newNetwork(helper, "teste-compostor-saida");
        RouterBlockEntity pies = place(helper, A, Blocks.CHEST.defaultBlockState(), input);
        pies.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity top = place(helper, C, Blocks.COMPOSTER.defaultBlockState(), input);
        top.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        BlockPos bottomPos = C.below();
        helper.setBlock(bottomPos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.DOWN));
        RouterBlockEntity bottom = GameTestCompat.getBlockEntity(helper, bottomPos);
        bottom.setNetworkId(output);
        bottom.setMode(ResourceType.ITEM, Direction.DOWN, PortMode.EXTRACT);
        RouterBlockEntity meal = place(helper, D, Blocks.CHEST.defaultBlockState(), output);
        meal.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        ChestBlockEntity chest = GameTestCompat.getBlockEntity(helper, A);
        chest.setItem(0, new ItemStack(Items.PUMPKIN_PIE, 7));

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, pies, top, bottom, meal))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, count(helper, A, Items.PUMPKIN_PIE), 0, "tortas no baú"))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, count(helper, D, Items.BONE_MEAL), 1, "farinha de osso"))
                .thenExecute(() -> {
                    helper.assertBlockProperty(C, ComposterBlock.LEVEL, 0);
                    GameTestCompat.assertValueEqual(helper, count(helper, D, Items.PUMPKIN_PIE), 0, "tortas no baú da farinha");
                })
                .thenSucceed();
    }

    /**
     * O chunk da máquina carregando acorda a porta (o índice de chunks dos caches, como o
     * {@code invalidateCapabilities(ChunkPos)} do NeoForge). Longe dos outros testes, num par de chunks vizinhos
     * presos por um ticket: o roteador de destino no chunk R, na borda, e o baú dele no chunk M. Os dois descarregam
     * de verdade (sem ticket). Depois só R volta, como borda de um ticket de nível 33 (carregado, com M ainda fora):
     * o destino consulta a máquina com o chunk dela fora e dorme sem máquina. Promover M a carregado (o ticket ganha
     * raio 1) posta o {@code ChunkEvent.Load} dele, o cache é invalidado, a porta acorda e os diamantes entram logo,
     * sem esperar o teto do sono (100 ticks). Nada lê o chunk M antes da promoção (o {@code getBlockState} o
     * carregaria): só {@code hasChunk}. Num lote próprio, o último (ver a classe).
     */
    @GameTest(template = "empty", timeoutTicks = 2400, batch = "capcache_chunks")
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
