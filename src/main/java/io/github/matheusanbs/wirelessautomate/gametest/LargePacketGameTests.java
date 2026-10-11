package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterFaceFilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageListView;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.net.CustomPacketPayload;
import io.github.matheusanbs.wirelessautomate.net.PacketParts;
import io.github.matheusanbs.wirelessautomate.net.PayloadRegistrar;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.ServerMenus;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.AddFilterEntryPayload;
import io.github.matheusanbs.wirelessautomate.packet.FilterEntriesPayload;
import io.github.matheusanbs.wirelessautomate.packet.FilterViewPayload;
import io.github.matheusanbs.wirelessautomate.packet.LinkerSnapshotPayload;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.packet.RouterNetworksPayload;
import io.github.matheusanbs.wirelessautomate.packet.StorageActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.StorageEntriesPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletHeaderPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPagePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Porte 1.20.1: telas e pacotes grandes. O Forge 1.20.1 não divide pacotes (o NeoForge do {@code main} sim): os dados
 * de abertura de tela têm teto de 32600 bytes, um pacote do servidor de 1 MiB e um do cliente de 32767. Aqui as
 * telas abrem com mais do que cabe (muitas redes de nome longo, filtro com entradas de NBT grande, Baú com um item
 * de NBT enorme) e o teste lê o que foi de fato mandado ao jogador falso ({@link GameTestCompat#drain}, com o canal
 * do mod negociado por {@link GameTestCompat#negotiateModChannel}): a abertura cabe no teto, cada pacote do canal
 * do mod cabe no do vanilla (o construtor do pacote do vanilla recusaria), e o conteúdo inteiro chega pelos
 * complementos, juntado pelo mesmo leitor do canal. No sentido cliente → servidor, os pacotes são os que o cliente
 * mandaria ({@link PayloadRegistrar#packetsToServer}), escritos e relidos pelo pacote do vanilla, que recusa mais de
 * 32767 bytes, e o payload montado vai ao handler do servidor.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class LargePacketGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);
    /** Teto do vanilla para os dados de um pacote do servidor. */
    private static final int MAX_CLIENTBOUND = 1048576;
    /** Teto do vanilla para os dados de um pacote do cliente. */
    private static final int MAX_SERVERBOUND = 32767;

    private static ServerPlayer player(GameTestHelper helper, BlockPos near) {
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(near).above()));
        GameTestCompat.negotiateModChannel(player);
        GameTestCompat.drain(player);
        return player;
    }

    /** {@code count} redes do jogador com o nome no tamanho máximo (64), com acentos (2 bytes cada em UTF-8). */
    private static List<WaNetwork> longNetworks(GameTestHelper helper, ServerPlayer player, int count) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        List<WaNetwork> networks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StringBuilder name = new StringBuilder(String.format("Rede %04d ", i));
            while (name.length() < 64) {
                name.append('ã');
            }
            networks.add(data.create(player.getUUID(), name.toString()));
        }
        return networks;
    }

    private static void removeNetworks(GameTestHelper helper, List<WaNetwork> networks) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        networks.forEach(network -> data.remove(network.id()));
    }

    /** Uma pilha de papel com {@code bytes} bytes de NBT, diferente para cada {@code seed}. */
    private static ItemStack bigPaper(int bytes, int seed) {
        byte[] blob = new byte[bytes];
        for (int i = 0; i < blob.length; i++) {
            blob[i] = (byte) (i * 31 + seed);
        }
        ItemStack stack = new ItemStack(Items.PAPER);
        CompoundTag tag = stack.getOrCreateTag();
        tag.put("blob", new ByteArrayTag(blob));
        tag.putInt("seed", seed);
        return stack;
    }

    /** Bytes que {@code writer} escreve. */
    private static int encodedSize(Consumer<RegistryFriendlyByteBuf> writer) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer());
        writer.accept(buf);
        return buf.readableBytes();
    }

    /** A abertura cabe e veio; os pacotes do mod cabem no teto do vanilla. */
    private static RegistryFriendlyByteBuf checkSent(GameTestHelper helper, GameTestCompat.Sent sent, String screen) {
        helper.assertTrue(sent.openData() != null, screen + ": a tela não abriu");
        helper.assertTrue(sent.openSize() <= ServerMenus.MAX_OPEN_DATA,
                screen + ": abertura com " + sent.openSize() + " bytes");
        for (int size : sent.modPacketSizes()) {
            helper.assertTrue(size <= MAX_CLIENTBOUND, screen + ": pacote do mod com " + size + " bytes");
        }
        return sent.openData();
    }

    private static <T> T single(GameTestHelper helper, List<T> list, String what) {
        GameTestCompat.assertValueEqual(helper, list.size(), 1, what);
        return list.get(0);
    }

    /** Os dados de cada pacote que o cliente mandaria com {@code payload}, escritos e relidos pelo pacote do vanilla. */
    private static List<FriendlyByteBuf> clientPackets(GameTestHelper helper, CustomPacketPayload payload) {
        List<FriendlyByteBuf> data = new ArrayList<>();
        for (Packet<?> packet : PayloadRegistrar.packetsToServer(payload)) {
            FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
            ((ServerboundCustomPayloadPacket) packet).write(wire);
            // O construtor de leitura do vanilla recusa mais de 32767 bytes, como o servidor de verdade.
            ServerboundCustomPayloadPacket reread = new ServerboundCustomPayloadPacket(wire);
            helper.assertTrue(reread.getData().readableBytes() <= MAX_SERVERBOUND, "pacote do cliente grande demais");
            data.add(reread.getData());
        }
        return data;
    }

    /**
     * Os pacotes que o cliente mandaria com {@code payload}, recebidos pelo consumidor real do servidor, na montagem
     * da conexão do {@code player} (o mapa por conexão). Devolve o payload montado e põe em {@code packets[0]}
     * quantos pacotes foram.
     */
    private static CustomPacketPayload throughServerbound(GameTestHelper helper, ServerPlayer player,
            CustomPacketPayload payload, int[] packets) {
        Connection connection = player.connection.connection;
        List<FriendlyByteBuf> sent = clientPackets(helper, payload);
        packets[0] = sent.size();
        CustomPacketPayload received = null;
        for (int i = 0; i < sent.size(); i++) {
            CustomPacketPayload part = PayloadRegistrar.receiveOnServer(connection, sent.get(i));
            helper.assertTrue((part != null) == (i == sent.size() - 1),
                    "payload montado antes ou depois da última parte (" + i + " de " + sent.size() + ")");
            helper.assertTrue(PayloadRegistrar.serverPartsPending(connection) == (i < sent.size() - 1),
                    "montagem da conexão pela metade fora de hora");
            received = part;
        }
        helper.assertTrue(connection.isConnected(), "o servidor desconectou um cliente correto");
        if (received == null) {
            throw new GameTestAssertException("o servidor não montou o payload");
        }
        return received;
    }

    /** Vinculador com 300 redes de nome longo: os dados inteiros passam do teto; abre com a reduzida e chega tudo. */
    @GameTest(template = "empty")
    public static void linkerOpensWithManyLongNetworks(GameTestHelper helper) {
        ServerPlayer player = player(helper, A);
        List<WaNetwork> networks = longNetworks(helper, player, 300);
        try {
            NetworkSavedData.get(player.server).setActiveNetwork(player.getUUID(), networks.get(7).id());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
            LinkerMenu.open(player, InteractionHand.MAIN_HAND);
            if (!(player.containerMenu instanceof LinkerMenu server)) {
                throw new GameTestAssertException("a tela do Vinculador não abriu");
            }
            LinkerSnapshot full = server.snapshot();
            GameTestCompat.assertValueEqual(helper, full.networks().size(), 300, "redes no estado do servidor");
            helper.assertTrue(encodedSize(buf -> LinkerSnapshot.STREAM_CODEC.encode(buf, full)) > ServerMenus.MAX_OPEN_DATA,
                    "o estado inteiro cabe na abertura: o teste não exercita a reduzida");

            GameTestCompat.Sent sent = GameTestCompat.drain(player);
            LinkerMenu client = new LinkerMenu(server.containerId, player.getInventory(),
                    checkSent(helper, sent, "Vinculador"));
            // Na abertura, só a rede ativa (o nome dela aparece já).
            GameTestCompat.assertValueEqual(helper, client.snapshot().networks().size(), 1, "redes na abertura");
            GameTestCompat.assertValueEqual(helper, client.snapshot().activeEntry() == null ? null
                    : client.snapshot().activeEntry().name(), networks.get(7).name(), "rede ativa na abertura");
            LinkerSnapshotPayload rest = single(helper, sent.of(LinkerSnapshotPayload.class), "complementos");
            GameTestCompat.assertValueEqual(helper, rest.containerId(), server.containerId, "containerId do complemento");
            client.applySnapshot(rest.snapshot());
            GameTestCompat.assertValueEqual(helper, client.snapshot(), full, "estado depois do complemento");
        } finally {
            player.containerMenu = player.inventoryMenu;
            removeNetworks(helper, networks);
        }
        helper.succeed();
    }

    /** Roteador com 300 redes no seletor: abre sem elas e elas chegam logo depois. */
    @GameTest(template = "empty")
    public static void routerOpensWithManyLongNetworks(GameTestHelper helper) {
        helper.setBlock(A, Blocks.CHEST);
        helper.setBlock(A.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, A.above());
        ServerPlayer player = player(helper, A.above());
        List<WaNetwork> networks = longNetworks(helper, player, 300);
        try {
            RouterMenu.open(player, router);
            if (!(player.containerMenu instanceof RouterMenu server)) {
                throw new GameTestAssertException("a tela do roteador não abriu");
            }
            RouterSnapshot full = server.snapshot();
            helper.assertTrue(encodedSize(buf -> RouterSnapshot.STREAM_CODEC.encode(buf, full)) > ServerMenus.MAX_OPEN_DATA,
                    "o snapshot inteiro cabe na abertura: o teste não exercita a reduzida");
            GameTestCompat.Sent sent = GameTestCompat.drain(player);
            RouterMenu client = new RouterMenu(server.containerId, player.getInventory(),
                    checkSent(helper, sent, "roteador"));
            helper.assertTrue(client.snapshot().networks().isEmpty(), "redes na abertura reduzida");
            RouterNetworksPayload rest = single(helper, sent.of(RouterNetworksPayload.class), "complementos");
            client.applyNetworks(rest.networks());
            GameTestCompat.assertValueEqual(helper, client.snapshot(), full, "snapshot depois do complemento");
            Set<UUID> ids = new HashSet<>();
            client.snapshot().networks().forEach(entry -> ids.add(entry.id()));
            helper.assertTrue(networks.stream().allMatch(network -> ids.contains(network.id())), "rede faltando");
        } finally {
            player.containerMenu = player.inventoryMenu;
            removeNetworks(helper, networks);
        }
        helper.succeed();
    }

    /** Tablet com 300 redes: abre com o cabeçalho reduzido e recebe o cabeçalho inteiro e a página. */
    @GameTest(template = "empty")
    public static void tabletOpensWithManyLongNetworks(GameTestHelper helper) {
        ServerPlayer player = player(helper, A);
        List<WaNetwork> networks = longNetworks(helper, player, 300);
        try {
            TabletMenu.open(player);
            if (!(player.containerMenu instanceof TabletMenu server)) {
                throw new GameTestAssertException("a tela do Tablet não abriu");
            }
            TabletSnapshot full = server.snapshot();
            helper.assertTrue(encodedSize(buf -> TabletSnapshot.STREAM_CODEC.encode(buf, full)) > ServerMenus.MAX_OPEN_DATA,
                    "o snapshot inteiro cabe na abertura: o teste não exercita a reduzida");
            GameTestCompat.Sent sent = GameTestCompat.drain(player);
            TabletMenu client = new TabletMenu(server.containerId, player.getInventory(),
                    checkSent(helper, sent, "Tablet"));
            helper.assertTrue(client.snapshot().networks().isEmpty(), "redes na abertura reduzida");
            client.applyHeader(single(helper, sent.of(TabletHeaderPayload.class), "cabeçalhos").header());
            client.applyPage(single(helper, sent.of(TabletPagePayload.class), "páginas").page());
            GameTestCompat.assertValueEqual(helper, client.snapshot(), full, "snapshot depois dos complementos");
            helper.assertTrue(networks.stream().allMatch(network -> client.snapshot().network(network.id()).isPresent()),
                    "rede faltando");
        } finally {
            player.containerMenu = player.inventoryMenu;
            removeNetworks(helper, networks);
        }
        helper.succeed();
    }

    /**
     * Filtro de face com 40 entradas de 40 KiB de NBT cada (1,6 MiB): abre sem as entradas e a visão inteira chega
     * em partes (passa do 1 MiB de um pacote), igual à do servidor, com o NBT inteiro.
     */
    @GameTest(template = "empty")
    public static void filterOpensWithManyBigEntries(GameTestHelper helper) {
        helper.setBlock(A, Blocks.CHEST);
        helper.setBlock(A.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, A.above());
        List<FilterEntry> entries = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            entries.add(new FilterEntry.ItemEntry(bigPaper(40 * 1024, i), 0));
        }
        router.setFilter(ResourceType.ITEM, Direction.UP, Filter.EMPTY.withEntries(entries));
        ServerPlayer player = player(helper, A.above());
        try {
            new RouterFaceFilterTarget(router, ResourceType.ITEM, Direction.UP).open(player);
            if (!(player.containerMenu instanceof FilterMenu server)) {
                throw new GameTestAssertException("a tela de filtro não abriu");
            }
            GameTestCompat.assertValueEqual(helper, server.view().filter().entries().size(), 40, "entradas no servidor");
            GameTestCompat.Sent sent = GameTestCompat.drain(player);
            FilterMenu client = new FilterMenu(server.containerId, player.getInventory(),
                    checkSent(helper, sent, "filtro"));
            helper.assertTrue(client.view().filter().entries().isEmpty(), "entradas na abertura reduzida");
            helper.assertTrue(sent.modPacketSizes().size() >= 3,
                    "a visão de 1,6 MiB devia ir em partes: " + sent.modPacketSizes());
            FilterViewPayload rest = single(helper, sent.of(FilterViewPayload.class), "complementos");
            client.applyView(rest.view());
            GameTestCompat.assertValueEqual(helper, client.view(), server.view(), "visão depois do complemento");
            for (int i = 0; i < 40; i++) {
                FilterEntry.ItemEntry entry = (FilterEntry.ItemEntry) client.view().filter().entries().get(i);
                helper.assertTrue(ItemStack.isSameItemSameTags(entry.stack(), bigPaper(40 * 1024, i)),
                        "NBT da entrada " + i);
            }
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    /**
     * Baú com um item de NBT enorme (700 KiB), um grande (20 KiB) e um comum: os três aparecem. O enorme vem como o
     * substituto sem NBT, com a marca e a referência; o grande vem inteiro e com referência; o comum, sem. Pegar
     * o enorme e o grande pela referência (pacotes pequenos do cliente) tira os itens de verdade, com o NBT inteiro.
     */
    @GameTest(template = "empty")
    @SuppressWarnings("unchecked")
    public static void chestShowsAndGivesAHugeKey(GameTestHelper helper) {
        helper.setBlock(A, ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.BASIC));
        StorageChestBlockEntity chest = GameTestCompat.getBlockEntity(helper, A);
        ItemStorage storage = chest.storage();
        ItemStack huge = bigPaper(700 * 1024, 1);
        ItemStack big = bigPaper(20 * 1024, 2);
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        helper.assertTrue(storage.insert(huge, 3, false) == 3, "não guardou o enorme");
        helper.assertTrue(storage.insert(big, 5, false) == 5, "não guardou o grande");
        helper.assertTrue(storage.insert(cobble, 100, false) == 100, "não guardou o comum");
        ServerPlayer player = player(helper, A);
        try {
            StorageListMenu.open(player, chest);
            if (!(player.containerMenu instanceof StorageListMenu<?> opened)) {
                throw new GameTestAssertException("a tela do Baú não abriu");
            }
            StorageListMenu<ItemStack> server = (StorageListMenu<ItemStack>) opened;
            GameTestCompat.Sent openSent = GameTestCompat.drain(player);
            StorageListMenu<ItemStack> client = (StorageListMenu<ItemStack>) StorageListMenu.fromNetwork(
                    server.containerId, player.getInventory(), checkSent(helper, openSent, "Baú"));
            // A primeira sincronização sai no broadcastChanges do menu aberto, como no jogo.
            server.broadcastChanges();
            GameTestCompat.Sent sent = GameTestCompat.drain(player);
            for (int size : sent.modPacketSizes()) {
                helper.assertTrue(size <= MAX_CLIENTBOUND, "pacote do Baú com " + size + " bytes");
            }
            // A abertura pode já ter feito a primeira sincronização (o Forge inicia o menu); as duas filas valem.
            List<StorageEntriesPayload> lists = new ArrayList<>(openSent.of(StorageEntriesPayload.class));
            lists.addAll(sent.of(StorageEntriesPayload.class));
            helper.assertTrue(!lists.isEmpty(), "a lista não chegou: " + openSent.payloads() + " / " + sent.payloads());
            for (StorageEntriesPayload payload : lists) {
                List<StorageListView.Entry<ItemStack>> typed = new ArrayList<>();
                payload.entries().forEach(e -> typed.add((StorageListView.Entry<ItemStack>) (StorageListView.Entry<?>) e));
                client.view().apply(payload.reset(), payload.header(), typed);
            }
            StorageListView<ItemStack> view = client.view();
            GameTestCompat.assertValueEqual(helper, view.types(), 3, "tipos na tela");
            ItemStack shownHuge = null;
            ItemStack shownBig = null;
            ItemStack shownCobble = null;
            for (ItemStack key : view.keys()) {
                if (view.truncated(key)) {
                    shownHuge = key;
                } else if (ItemStack.isSameItemSameTags(key, big)) {
                    shownBig = key;
                } else if (ItemStack.isSameItemSameTags(key, cobble)) {
                    shownCobble = key;
                }
            }
            if (shownHuge == null || shownBig == null || shownCobble == null) {
                throw new GameTestAssertException("tipo faltando na tela: " + view.keys());
            }
            helper.assertTrue(shownHuge.is(Items.PAPER) && !shownHuge.getOrCreateTag().contains("blob"),
                    "o substituto devia vir sem o NBT: " + shownHuge.getTag());
            GameTestCompat.assertValueEqual(helper, view.count(shownHuge), 3L, "quantidade do enorme");
            GameTestCompat.assertValueEqual(helper, view.count(shownBig), 5L, "quantidade do grande");
            helper.assertTrue(view.ref(shownHuge) > 0, "o enorme sem referência");
            helper.assertTrue(view.ref(shownBig) > 0, "o grande sem referência");
            GameTestCompat.assertValueEqual(helper, view.ref(shownCobble), 0, "referência do comum");

            // Pegar o enorme pela referência: o pacote do cliente é pequeno e o cursor recebe o item de verdade.
            int[] packets = new int[1];
            StorageActionPayload take = (StorageActionPayload) throughServerbound(helper, player,
                    StorageActionPayload.byRef(server.containerId, StorageKind.CHEST,
                            StorageActionPayload.Action.TAKE_STACK, view.ref(shownHuge)), packets);
            GameTestCompat.assertValueEqual(helper, packets[0], 1, "pacotes da ação pela referência");
            helper.assertTrue(StorageListMenu.handle(player, take), "recusou pegar o enorme pela referência");
            helper.assertTrue(ItemStack.isSameItemSameTags(server.getCarried(), huge),
                    "o cursor devia ter o item enorme inteiro");
            GameTestCompat.assertValueEqual(helper, server.getCarried().getCount(), 3, "itens no cursor");
            GameTestCompat.assertValueEqual(helper, storage.count(huge), 0L, "enormes no Baú");
            server.setCarried(ItemStack.EMPTY);

            StorageActionPayload toInventory = (StorageActionPayload) throughServerbound(helper, player,
                    StorageActionPayload.byRef(server.containerId, StorageKind.CHEST,
                            StorageActionPayload.Action.TAKE_TO_INVENTORY, view.ref(shownBig)), packets);
            helper.assertTrue(StorageListMenu.handle(player, toInventory), "recusou o grande pela referência");
            GameTestCompat.assertValueEqual(helper, storage.count(big), 0L, "grandes no Baú");
            helper.assertTrue(player.getInventory().items.stream().anyMatch(s -> ItemStack.isSameItemSameTags(s, big)
                    && s.getCount() == 5), "o inventário devia ter os grandes inteiros");
            // Referência que a tela não recebeu: recusa.
            helper.assertFalse(StorageListMenu.handle(player, StorageActionPayload.byRef(server.containerId,
                    StorageKind.CHEST, StorageActionPayload.Action.TAKE_STACK, 999)), "aceitou referência desconhecida");

            // O enorme saiu: a lista da tela tira o substituto.
            server.broadcastChanges();
            helper.runAfterDelay(StorageListMenu.SYNC_INTERVAL + 1, () -> {
                server.broadcastChanges();
                for (StorageEntriesPayload payload : GameTestCompat.drain(player).of(StorageEntriesPayload.class)) {
                    List<StorageListView.Entry<ItemStack>> typed = new ArrayList<>();
                    payload.entries().forEach(e -> typed.add((StorageListView.Entry<ItemStack>) (StorageListView.Entry<?>) e));
                    client.view().apply(payload.reset(), payload.header(), typed);
                }
                GameTestCompat.assertValueEqual(helper, client.view().types(), 1, "tipos na tela depois de tirar");
                player.containerMenu = player.inventoryMenu;
                helper.succeed();
            });
        } catch (RuntimeException e) {
            player.containerMenu = player.inventoryMenu;
            throw e;
        }
    }

    /**
     * Cliente → servidor: um item de 100 KiB de NBT arrastado do JEI para o filtro passa dos 32767 bytes de um pacote
     * do cliente; vai em partes, o servidor junta e acrescenta a entrada inteira. Várias regras num pacote só
     * continuam indo num pacote.
     */
    @GameTest(template = "empty")
    public static void bigFilterEntryGoesToTheServerInParts(GameTestHelper helper) {
        helper.setBlock(A, Blocks.CHEST);
        helper.setBlock(A.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, A.above());
        ServerPlayer player = player(helper, A.above());
        RouterFaceFilterTarget target = new RouterFaceFilterTarget(router, ResourceType.ITEM, Direction.UP);
        FilterMenu menu = new FilterMenu(44, player.getInventory(), target, target.view(player));
        player.containerMenu = menu;
        try {
            ItemStack stack = bigPaper(100 * 1024, 3);
            int[] packets = new int[1];
            CustomPacketPayload received = throughServerbound(helper, player,
                    new AddFilterEntryPayload(menu.containerId, new FilterEntry.ItemEntry(stack, 0)), packets);
            helper.assertTrue(packets[0] >= 4, "100 KiB deviam ir em partes: " + packets[0] + " pacote(s)");
            helper.assertTrue(ModPayloads.handleAddFilterEntry(player, (AddFilterEntryPayload) received),
                    "o servidor recusou a entrada montada");
            List<FilterEntry> entries = router.face(ResourceType.ITEM, Direction.UP).filter().entries();
            GameTestCompat.assertValueEqual(helper, entries.size(), 1, "entradas no filtro");
            helper.assertTrue(ItemStack.isSameItemSameTags(((FilterEntry.ItemEntry) entries.get(0)).stack(), stack),
                    "a entrada devia ter o NBT inteiro");

            List<FilterEntry> mods = new ArrayList<>();
            for (int i = 0; i < FilterEntriesPayload.MAX; i++) {
                mods.add(new FilterEntry.ModEntry("mod" + i, 0));
            }
            received = throughServerbound(helper, player, FilterEntriesPayload.add(menu.containerId, mods), packets);
            GameTestCompat.assertValueEqual(helper, packets[0], 1, "pacotes das regras");
            helper.assertTrue(ModPayloads.handleFilterEntries(player, (FilterEntriesPayload) received),
                    "o servidor recusou as regras");
            GameTestCompat.assertValueEqual(helper, router.face(ResourceType.ITEM, Direction.UP).filter().entries().size(),
                    1 + FilterEntriesPayload.MAX, "entradas depois das regras");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    /**
     * Duas conexões mandando partes ao mesmo tempo, intercaladas: cada uma monta o seu payload (o mapa por conexão
     * do consumidor real). Uma parte fora de ordem (pacote novo com outro pela metade) e uma parte que declara mais
     * que o teto de 2 MiB desconectam quem mandou, sem guardar nada.
     */
    @GameTest(template = "empty")
    public static void serverPartsArePerConnectionAndViolationsDisconnect(GameTestHelper helper) {
        ServerPlayer first = player(helper, A);
        ServerPlayer second = player(helper, A);
        ServerPlayer reorder = player(helper, A);
        ServerPlayer oversized = player(helper, A);
        ItemStack stackA = bigPaper(80 * 1024, 5);
        ItemStack stackB = bigPaper(90 * 1024, 6);
        List<FriendlyByteBuf> partsA = clientPackets(helper,
                new AddFilterEntryPayload(7, new FilterEntry.ItemEntry(stackA, 0)));
        List<FriendlyByteBuf> partsB = clientPackets(helper,
                new AddFilterEntryPayload(8, new FilterEntry.ItemEntry(stackB, 0)));
        helper.assertTrue(partsA.size() >= 3 && partsB.size() >= 3, "os dois deviam ir em partes");
        Connection a = first.connection.connection;
        Connection b = second.connection.connection;
        CustomPacketPayload gotA = null;
        CustomPacketPayload gotB = null;
        for (int i = 0; i < Math.max(partsA.size(), partsB.size()); i++) {
            if (i < partsA.size()) {
                gotA = PayloadRegistrar.receiveOnServer(a, new FriendlyByteBuf(partsA.get(i).copy()));
            }
            if (i < partsB.size()) {
                gotB = PayloadRegistrar.receiveOnServer(b, new FriendlyByteBuf(partsB.get(i).copy()));
            }
        }
        helper.assertTrue(gotA instanceof AddFilterEntryPayload pa && pa.containerId() == 7
                && pa.entry() instanceof FilterEntry.ItemEntry ea && ItemStack.isSameItemSameTags(ea.stack(), stackA),
                "a primeira conexão montou errado");
        helper.assertTrue(gotB instanceof AddFilterEntryPayload pb && pb.containerId() == 8
                && pb.entry() instanceof FilterEntry.ItemEntry eb && ItemStack.isSameItemSameTags(eb.stack(), stackB),
                "a segunda conexão montou errado");
        helper.assertTrue(a.isConnected() && b.isConnected(), "desconectou um cliente correto");

        // Pacote novo com outro pela metade: erro de protocolo, desconecta e esvazia.
        Connection c = reorder.connection.connection;
        helper.assertTrue(PayloadRegistrar.receiveOnServer(c, new FriendlyByteBuf(partsA.get(0).copy())) == null,
                "montou com uma parte");
        helper.assertTrue(PayloadRegistrar.serverPartsPending(c), "a primeira parte não ficou guardada");
        helper.assertTrue(PayloadRegistrar.receiveOnServer(c, new FriendlyByteBuf(partsA.get(0).copy())) == null,
                "montou o recomeço");
        helper.assertFalse(c.isConnected(), "o recomeço não desconectou");
        helper.assertFalse(PayloadRegistrar.serverPartsPending(c), "a montagem ficou guardada");

        // Parte de 1 byte declarando 3 MiB: recusada sem alocar, desconecta.
        FriendlyByteBuf hostile = new FriendlyByteBuf(Unpooled.buffer());
        hostile.writeByte(2);
        hostile.writeVarInt(3 * 1024 * 1024);
        hostile.writeVarInt(0);
        hostile.writeByte(0);
        Connection d = oversized.connection.connection;
        helper.assertTrue(PayloadRegistrar.receiveOnServer(d, hostile) == null, "aceitou a parte acima do teto");
        helper.assertFalse(d.isConnected(), "a parte acima do teto não desconectou");
        helper.assertFalse(PayloadRegistrar.serverPartsPending(d), "a parte acima do teto ficou guardada");
        helper.succeed();
    }

    /**
     * O pior caso de cada abertura reduzida cabe no teto do Forge: nomes no tamanho máximo com acentos (2 bytes
     * cada), listas enormes (que a reduzida tira), a prévia do Vinculador cheia (512 pontos com coordenadas de 5
     * bytes), números no máximo, ícone da máquina com 100 KiB de NBT e o aviso do Tablet com argumentos longos.
     */
    @GameTest(template = "empty")
    public static void reducedOpeningsFitInTheWorstCase(GameTestHelper helper) {
        String name64 = "ã".repeat(64);
        List<RouterSnapshot.NetworkEntry> networks = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            networks.add(new RouterSnapshot.NetworkEntry(UUID.randomUUID(), name64, -1, i % 2 == 0));
        }

        // Vinculador
        List<LinkerSnapshot.RouterDot> dots = new ArrayList<>();
        for (int i = 0; i < LinkerSnapshot.MAX_DOTS; i++) {
            dots.add(new LinkerSnapshot.RouterDot(Integer.MIN_VALUE, Integer.MIN_VALUE, -1, true));
        }
        LinkerTabs all = LinkerTabs.available(LoadedTypes.LIST);
        LinkerSnapshot linker = new LinkerSnapshot(networks, Optional.of(networks.get(1000).id()), true, all, all,
                LinkerMode.values()[LinkerMode.values().length - 1],
                Optional.of(new BlockPos(-30_000_000, -64, -30_000_000)),
                Optional.of(new BlockPos(30_000_000, 320, 30_000_000)), false, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, dots, LinkerProblem.values()[LinkerProblem.values().length - 1], Long.MAX_VALUE,
                Integer.MAX_VALUE, Optional.of(new LinkerSnapshot.Outcome(Integer.MAX_VALUE, Integer.MAX_VALUE,
                        Integer.MAX_VALUE, Integer.MAX_VALUE, all, false, name64, -1)));
        int linkerSize = encodedSize(buf -> {
            buf.writeEnum(InteractionHand.OFF_HAND);
            LinkerSnapshot.STREAM_CODEC.encode(buf, linker.reduced());
        });
        helper.assertTrue(linkerSize <= ServerMenus.MAX_OPEN_DATA, "Vinculador reduzido com " + linkerSize + " bytes");

        // Roteador: o snapshot de um roteador de verdade, com o pior nome, as redes e um ícone de 100 KiB de NBT.
        helper.setBlock(A, Blocks.CHEST);
        helper.setBlock(A.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, A.above());
        ServerPlayer player = player(helper, A.above());
        RouterSnapshot real = RouterSnapshot.capture(router, player);
        RouterSnapshot worst = new RouterSnapshot(real.pos(), "ã".repeat(RenameRouterPayload.MAX_LENGTH),
                RouterTier.values()[RouterTier.values().length - 1], real.facing(), real.typeNetworks(), networks,
                true, bigPaper(100 * 1024, 9), real.machineState(), real.faces(), real.chunkLoad());
        int routerSize = encodedSize(buf -> RouterSnapshot.STREAM_CODEC.encode(buf, worst.reduced()));
        helper.assertTrue(routerSize <= ServerMenus.MAX_OPEN_DATA, "roteador reduzido com " + routerSize + " bytes");

        // Tablet
        List<TabletSnapshot.NetworkView> views = new ArrayList<>();
        for (RouterSnapshot.NetworkEntry entry : networks) {
            views.add(new TabletSnapshot.NetworkView(entry.id(), name64, -1, name64, true, true, true, true,
                    Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE,
                    Integer.MAX_VALUE, List.of()));
        }
        TabletSnapshot tablet = new TabletSnapshot(true,
                new ResourceLocation("wirelessautomate", "d".repeat(64)), new BlockPos(30_000_000, 320, 30_000_000),
                Optional.of(networks.get(0).id()), Long.MAX_VALUE, Long.MAX_VALUE,
                new TabletSnapshot.Query("ã".repeat(TabletSnapshot.MAX_SEARCH), TabletSnapshot.RoleFilter.PROBLEM,
                        Optional.of(ResourceType.ITEM), Integer.MAX_VALUE),
                Integer.MAX_VALUE, Integer.MAX_VALUE, views, List.of(), List.of(),
                Component.translatable("gui.wirelessautomate.tablet.notice.moved.unloaded", Integer.MAX_VALUE, name64,
                        Integer.MAX_VALUE), Integer.MAX_VALUE);
        int tabletSize = encodedSize(buf -> TabletSnapshot.STREAM_CODEC.encode(buf, tablet.reduced()));
        helper.assertTrue(tabletSize <= ServerMenus.MAX_OPEN_DATA, "Tablet reduzido com " + tabletSize + " bytes");
        // E o caso não é trivial: os completos passam do teto.
        helper.assertTrue(encodedSize(buf -> TabletSnapshot.STREAM_CODEC.encode(buf, tablet)) > ServerMenus.MAX_OPEN_DATA
                && encodedSize(buf -> RouterSnapshot.STREAM_CODEC.encode(buf, worst)) > ServerMenus.MAX_OPEN_DATA
                && encodedSize(buf -> LinkerSnapshot.STREAM_CODEC.encode(buf, linker)) > ServerMenus.MAX_OPEN_DATA,
                "o pior caso cabe inteiro: o teste não exercita a reduzida");
        helper.succeed();
    }
}
