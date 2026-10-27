package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.FlowSampler;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.TvValuesPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageWatch;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvManager;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStorageMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Os armazenamentos do mod ligados a uma Tela de Sinal como dado: a leitura (quantidade, capacidade, fração, por
 * item no Baú), a ligação desfeita, o Baú em outra dimensão e num chunk descarregado (o último número, marcado como
 * antigo, sem nunca carregar o chunk) e os contadores de entra e sai, que só existem com o armazenamento vigiado e só
 * contam inserção e extração reais. A Tela fica só na memória (o tick real não a vê) e os testes chamam o mesmo
 * caminho da visita; cada teste dos contadores roda dentro de um tick, porque sem Tela carregada o tick real solta
 * todas as vigias.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvStorageFeedGameTests {
    private static final BlockPos TV = new BlockPos(1, 1, 1);
    private static final BlockPos CHEST = new BlockPos(0, 1, 3);
    private static final BlockPos BATTERY = new BlockPos(2, 1, 3);
    private static final ResourceLocation DIAMOND = BuiltInRegistries.ITEM.getKey(Items.DIAMOND);

    private static long at;

    private TvStorageFeedGameTests() {
    }

    private static TvPiece value(int x, TvBinding binding) {
        return new TvPiece(TvPieceType.VALUE, x, 0, 10, 10, 0, "", Optional.of(binding), TvFormat.DEFAULT,
                TvStyle.DEFAULT);
    }

    /** Uma Tela só na memória, dona de {@code owner}, com os armazenamentos ligados e uma peça por ligação. */
    private static TvBlockEntity tv(GameTestHelper helper, List<GlobalPos> links, TvBinding... bindings) {
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(TV),
                ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING, Direction.NORTH));
        tv.setOwner(UUID.randomUUID());
        for (GlobalPos link : links) {
            tv.link(link);
        }
        TvPiece[] pieces = new TvPiece[bindings.length];
        for (int i = 0; i < bindings.length; i++) {
            pieces[i] = value(i * 12, bindings[i]);
        }
        tv.setLayout(TvLayout.of(List.of(pieces)));
        return tv;
    }

    private static GlobalPos pos(GameTestHelper helper, BlockPos relative) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative));
    }

    private static TvBinding stored(GlobalPos pos, TvStorageMetric metric, Item item) {
        return TvBinding.storage(ResourceType.ITEM, pos, metric,
                item == null ? Optional.empty() : Optional.of(BuiltInRegistries.ITEM.getKey(item)));
    }

    private static ServerPlayer near(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.menuOpener(helper);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(TV)).add(0, 0, 10));
        return player;
    }

    /** Uma visita com um relatório novo (o {@code sampledAt} sobe), que obriga a ler de novo. */
    private static double[] visit(TvBlockEntity tv, ServerPlayer player) {
        TvManager.sampleForTests(tv, new FlowSampler.Report(++at, List.of(),
                FlowSampler.Report.EMPTY.withoutNetwork(), List.of()), List.of(player));
        return tv.feed().values();
    }

    private static StorageChestBlockEntity chest(GameTestHelper helper) {
        helper.setBlock(CHEST, ModBlocks.STORAGE_CHEST.get().defaultBlockState());
        return helper.getBlockEntity(CHEST);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void leQuantidadeCapacidadeEFracaoDoBau(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        chest.storage().insert(new ItemStack(Items.DIAMOND), 8_192, false);
        chest.storage().insert(new ItemStack(Items.GOLD_INGOT), 100, false);
        GlobalPos pos = pos(helper, CHEST);
        TvBlockEntity tv = tv(helper, List.of(pos), stored(pos, TvStorageMetric.STORED, null),
                stored(pos, TvStorageMetric.CAPACITY, null), stored(pos, TvStorageMetric.FILL, null),
                stored(pos, TvStorageMetric.STORED, Items.DIAMOND), stored(pos, TvStorageMetric.FILL, Items.DIAMOND));
        double[] values = visit(tv, near(helper));
        helper.assertValueEqual(values[0], 8_292.0, "o total do Baú");
        helper.assertValueEqual(values[1], (double) chest.capacity(), "a capacidade");
        helper.assertValueEqual(values[2], 8_292.0 / chest.capacity(), "a fração do total");
        helper.assertValueEqual(values[3], 8_192.0, "só os diamantes");
        helper.assertValueEqual(values[4], 8_192.0 / chest.capacity(), "a fração dos diamantes");
        helper.assertTrue(tv.feed().stale().length == 0, "nada é antigo com o chunk carregado");
        helper.assertTrue(chest.watch() == null, "ler quantidade não liga a vigia");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void leACargaDaBateria(GameTestHelper helper) {
        helper.setBlock(BATTERY, ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState());
        StorageBatteryBlockEntity battery = helper.getBlockEntity(BATTERY);
        battery.store().insert(5_000, false);
        GlobalPos pos = pos(helper, BATTERY);
        TvBlockEntity tv = tv(helper, List.of(pos),
                TvBinding.storage(ResourceType.ENERGY, pos, TvStorageMetric.STORED, Optional.empty()),
                TvBinding.storage(ResourceType.ENERGY, pos, TvStorageMetric.FILL, Optional.empty()));
        double[] values = visit(tv, near(helper));
        helper.assertValueEqual(values[0], 5_000.0, "a carga");
        helper.assertValueEqual(values[1], 5_000.0 / battery.capacity(), "a fração");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void desligadoOuBlocoTrocadoViraTraco(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        chest.storage().insert(new ItemStack(Items.DIAMOND), 10, false);
        GlobalPos pos = pos(helper, CHEST);
        TvBinding binding = stored(pos, TvStorageMetric.STORED, null);
        TvBlockEntity unlinked = tv(helper, List.of(), binding);
        ServerPlayer player = near(helper);
        helper.assertTrue(Double.isNaN(visit(unlinked, player)[0]), "sem ligação deveria ser NaN");
        TvBlockEntity linked = tv(helper, List.of(pos), binding);
        helper.assertValueEqual(visit(linked, player)[0], 10.0, "ligado");
        helper.setBlock(CHEST, Blocks.STONE);
        helper.assertTrue(Double.isNaN(visit(linked, player)[0]), "o bloco trocado deveria virar NaN");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bauComItemInexistenteViraTracoEItemAusenteEZero(GameTestHelper helper) {
        chest(helper);
        GlobalPos pos = pos(helper, CHEST);
        TvBinding ghost = TvBinding.storage(ResourceType.ITEM, pos, TvStorageMetric.STORED,
                Optional.of(ResourceLocation.fromNamespaceAndPath("mod_que_nao_existe", "item")));
        TvBlockEntity tv = tv(helper, List.of(pos), ghost, stored(pos, TvStorageMetric.STORED, Items.DIAMOND));
        double[] values = visit(tv, near(helper));
        helper.assertTrue(Double.isNaN(values[0]), "item que não existe deveria ser NaN");
        helper.assertValueEqual(values[1], 0.0, "item que o Baú não tem é 0");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void bauEmOutraDimensaoELeNoChunkDescarregadoMostraOUltimoNumero(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "o servidor de teste não tem o Nether");
        BlockPos far = new BlockPos(4_000 + (helper.absolutePos(TV).getX() & 0xFF) * 16, 70, 4_000);
        ChunkPos chunk = new ChunkPos(far);
        nether.setChunkForced(chunk.x, chunk.z, true);
        nether.setBlock(far, ModBlocks.STORAGE_CHEST.get().defaultBlockState(), 3);
        ((StorageChestBlockEntity) nether.getBlockEntity(far)).storage().insert(new ItemStack(Items.DIAMOND), 77, false);
        GlobalPos pos = GlobalPos.of(Level.NETHER, far);
        TvBinding binding = stored(pos, TvStorageMetric.STORED, null);
        TvBlockEntity tv = tv(helper, List.of(pos), binding);
        ServerPlayer player = near(helper);
        helper.assertValueEqual(visit(tv, player)[0], 77.0, "o Baú do Nether");
        helper.assertTrue(tv.feed().stale().length == 0, "carregado não é antigo");
        nether.setChunkForced(chunk.x, chunk.z, false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(nether.isLoaded(far), "o chunk ainda está carregado"))
                .thenExecute(() -> {
                    double[] values = visit(tv, player);
                    helper.assertValueEqual(values[0], 77.0, "o último número");
                    helper.assertTrue(tv.feed().stale().length == 1 && (tv.feed().stale()[0] & 1L) == 1L,
                            "o último número tem de vir marcado como antigo");
                    helper.assertFalse(nether.isLoaded(far), "ler não pode carregar o chunk");
                    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                            helper.getLevel().registryAccess());
                    TvValuesPayload.STREAM_CODEC.encode(buf, new TvValuesPayload(tv.getBlockPos(), 1, values,
                            tv.feed().stale()));
                    TvValuesPayload back = TvValuesPayload.STREAM_CODEC.decode(buf);
                    helper.assertTrue(back.stale().length == 1 && back.stale()[0] == 1L, "o payload perdeu o bit");
                    nether.getChunk(chunk.x, chunk.z);
                    helper.assertTrue(nether.isLoaded(far), "o chunk não voltou");
                    ((StorageChestBlockEntity) nether.getBlockEntity(far)).storage()
                            .insert(new ItemStack(Items.DIAMOND), 3, false);
                    helper.assertValueEqual(visit(tv, player)[0], 80.0, "voltou ao vivo");
                    helper.assertTrue(tv.feed().stale().length == 0, "ao vivo não é antigo");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void contadoresSomamSoOReal(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        GlobalPos pos = pos(helper, CHEST);
        helper.assertTrue(chest.watch() == null, "o Baú nasce sem vigia");
        chest.storage().insert(new ItemStack(Items.DIAMOND), 5, false);
        helper.assertTrue(chest.watch() == null, "sem Tela o Baú não conta nada");
        TvBlockEntity tv = tv(helper, List.of(pos), stored(pos, TvStorageMetric.IN, Items.DIAMOND),
                stored(pos, TvStorageMetric.OUT, null));
        visit(tv, near(helper));
        StorageWatch watch = chest.watch();
        helper.assertTrue(watch != null, "a taxa deveria ligar a vigia");
        chest.storage().insert(new ItemStack(Items.DIAMOND), 100, false);
        chest.storage().insert(new ItemStack(Items.GOLD_INGOT), 50, false);
        chest.storage().insert(new ItemStack(Items.DIAMOND), 10, true);
        chest.storage().extract(new ItemStack(Items.DIAMOND), 30, false);
        chest.storage().extract(new ItemStack(Items.GOLD_INGOT), 1_000, true);
        helper.assertValueEqual(watch.totalIn(), 150L, "entrou no total (sem a simulada)");
        helper.assertValueEqual(watch.totalOut(), 30L, "saiu no total (sem a simulada)");
        helper.assertValueEqual(watch.itemIn(Items.DIAMOND), 100L, "diamantes que entraram");
        helper.assertValueEqual(watch.itemOut(Items.DIAMOND), 30L, "diamantes que saíram");
        helper.assertValueEqual(watch.itemIn(Items.GOLD_INGOT), 0L, "o ouro não é vigiado");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aTaxaVemDaJanelaEChegaAoFeed(GameTestHelper helper) {
        helper.setBlock(BATTERY, ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState());
        StorageBatteryBlockEntity battery = helper.getBlockEntity(BATTERY);
        GlobalPos pos = pos(helper, BATTERY);
        TvBlockEntity tv = tv(helper, List.of(pos),
                TvBinding.storage(ResourceType.ENERGY, pos, TvStorageMetric.IN, Optional.empty()),
                TvBinding.storage(ResourceType.ENERGY, pos, TvStorageMetric.OUT, Optional.empty()),
                TvBinding.storage(ResourceType.ENERGY, pos, TvStorageMetric.BALANCE, Optional.empty()));
        ServerPlayer player = near(helper);
        visit(tv, player);
        StorageWatch watch = battery.watch();
        helper.assertTrue(watch != null, "a vigia não ligou");
        long now = helper.getLevel().getServer().getTickCount();
        battery.store().insert(1_000, false);
        battery.store().extract(250, false);
        watch.sample(now + 20);
        double[] values = visit(tv, player);
        helper.assertValueEqual(values[0], 1_000.0, "entra por segundo");
        helper.assertValueEqual(values[1], 250.0, "sai por segundo");
        helper.assertValueEqual(values[2], 750.0, "o saldo");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void semNinguemPertoOuSemTaxaNaoHaVigia(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        GlobalPos pos = pos(helper, CHEST);
        TvBlockEntity rate = tv(helper, List.of(pos), stored(pos, TvStorageMetric.IN, null));
        TvManager.sampleForTests(rate, new FlowSampler.Report(++at, List.of(), FlowSampler.Report.EMPTY.withoutNetwork(),
                List.of()), List.of());
        helper.assertTrue(chest.watch() == null, "ninguém perto não pode ligar a vigia");
        TvBlockEntity amount = tv(helper, List.of(pos), stored(pos, TvStorageMetric.STORED, null),
                stored(pos, TvStorageMetric.FILL, Items.DIAMOND));
        visit(amount, near(helper));
        helper.assertTrue(chest.watch() == null, "quantidade e fração não ligam a vigia");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_tv_sweep")
    public static void vigiaSoltaQuandoNinguemRenova(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper);
        GlobalPos pos = pos(helper, CHEST);
        TvBlockEntity tv = tv(helper, List.of(pos), stored(pos, TvStorageMetric.IN, null));
        visit(tv, near(helper));
        helper.assertTrue(chest.watch() != null, "a vigia não ligou");
        helper.assertTrue(TvManager.watchedStoragesForTests() >= 1, "o armazenamento deveria estar vigiado");
        long now = helper.getLevel().getServer().getTickCount();
        TvManager.sweepForTests(now + 10);
        helper.assertTrue(chest.watch() != null, "o arrendamento ainda vale");
        TvManager.sweepForTests(now + 1_000);
        helper.assertTrue(chest.watch() == null, "a vigia vencida deveria soltar");
        chest.storage().insert(new ItemStack(Items.DIAMOND), 5, false);
        helper.assertTrue(chest.watch() == null, "solta, o Baú não conta de novo");
        helper.succeed();
    }
}
