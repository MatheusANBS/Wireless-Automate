package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TabletPayloads;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload.Action;
import io.github.matheusanbs.wirelessautomate.preset.ProfileDiff;
import io.github.matheusanbs.wirelessautomate.preset.ProfileDiff.ProfileMatch;
import io.github.matheusanbs.wirelessautomate.preset.ProfileDiff.What;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSource;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * O perfil no Tablet: copiar para o item, esquecer, colar num nó, no grupo e nos marcados, desfazer o
 * último colar, a regra das redes e a comparação "igual ao perfil" ({@link ProfileDiff}). Os testes do lote
 * dividem o índice e as redes, então cada um cria as suas.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TabletProfileGameTests {
    private static final BlockPos M1 = new BlockPos(0, 0, 0);
    private static final BlockPos M2 = new BlockPos(2, 0, 0);
    private static final BlockPos M3 = new BlockPos(0, 0, 2);
    private static final BlockPos M4 = new BlockPos(2, 0, 2);
    private static final ResourceLocation CHEST = BuiltInRegistries.BLOCK.getKey(Blocks.CHEST);

    @SuppressWarnings("removal")
    private static ServerPlayer playerWithTablet(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(M1).above(3)));
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        return player;
    }

    private static TabletMenu openTablet(ServerPlayer player, int containerId) {
        TabletMenu menu = new TabletMenu(containerId, player.getInventory(), player);
        player.containerMenu = menu;
        return menu;
    }

    /** Roteador com {@code facing=up} sobre a máquina {@code machine}, nomeado e colocado pelo jogador. */
    private static RouterBlockEntity owned(GameTestHelper helper, ServerPlayer player, BlockPos machine, Block block,
            String name) {
        helper.setBlock(machine, block);
        BlockPos pos = machine.above();
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(pos);
        router.setName(name);
        NodeIndex.placedBy(router, player.getUUID());
        return router;
    }

    private static NodeKey key(RouterBlockEntity router) {
        return NodeKey.of(router.getLevel(), router.getBlockPos());
    }

    private static void loaded(GameTestHelper helper, RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad");
        }
    }

    private static void eq(GameTestHelper helper, @Nullable Object actual, @Nullable Object expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": esperado " + expected + ", veio " + actual);
    }

    private static Filter onlyOf(net.minecraft.world.item.Item item) {
        return new Filter(Filter.ListMode.WHITELIST, false, List.of(new FilterEntry.ItemEntry(new ItemStack(item), 0)));
    }

    private static int profile(ServerPlayer player, int containerId, Action action, @Nullable NodeKey key,
            @Nullable ResourceLocation machine, List<NodeKey> keys) {
        return TabletPayloads.handleProfile(player, new TabletProfilePayload(containerId, action,
                Optional.ofNullable(key), Optional.ofNullable(machine), keys));
    }

    private static int profile(ServerPlayer player, int containerId, Action action, @Nullable NodeKey key) {
        return profile(player, containerId, action, key, null, List.of());
    }

    /** Dois roteadores iguais menos o que cada teste muda (o modo da frente, em Itens). */
    private static void configure(RouterBlockEntity router, UUID network) {
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        router.setPriority(ResourceType.ITEM, Direction.UP, 5);
        router.setFilter(ResourceType.ITEM, Direction.UP, onlyOf(Items.DIAMOND));
        router.setMode(ResourceType.FLUID, Direction.DOWN, PortMode.INSERT);
        if (network != null) {
            router.setFaceNetwork(ResourceType.ITEM, Direction.UP, network);
        }
    }

    private static boolean same(RouterBlockEntity a, RouterBlockEntity b) {
        return RouterPreset.copyOf(a).equals(RouterPreset.copyOf(b));
    }

    /** As chaves de tradução do último aviso (o texto muda com o idioma; as chaves não). */
    private static String text(TabletMenu menu) {
        StringBuilder keys = new StringBuilder();
        if (menu.lastNotice() != null) {
            collectKeys(menu.lastNotice(), keys);
        }
        return keys.toString();
    }

    private static void collectKeys(Component component, StringBuilder keys) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            keys.append(translatable.getKey()).append(' ');
        }
        component.getSiblings().forEach(sibling -> collectKeys(sibling, keys));
    }

    @GameTest(template = "empty")
    public static void copyStoresProfileInTheTablet(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork net = data.create(player.getUUID(), "teste-perfil-copia");
        RouterBlockEntity source = owned(helper, player, M1, Blocks.CHEST, "Origem");
        configure(source, net.id());
        RouterBlockEntity foreign = owned(helper, player, M2, Blocks.CHEST, "Alheio");
        NodeIndex.placedBy(foreign, UUID.randomUUID());
        TabletMenu menu = openTablet(player, 90);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, source, foreign))
                .thenExecute(() -> {
                    try {
                        eq(helper, profile(player, 91, Action.COPY, key(source)), -1, "outro containerId");
                        eq(helper, profile(player, 90, Action.COPY, key(foreign)), -1, "nó que o jogador não vê");
                        helper.assertTrue(!player.getInventory().getItem(0).has(ModDataComponents.PRESET.get()),
                                "copiou o que foi recusado");
                        helper.assertTrue(profile(player, 90, Action.COPY, key(source)) >= 0, "recusou copiar");
                        ItemStack tablet = player.getInventory().getItem(0);
                        RouterPreset preset = tablet.get(ModDataComponents.PRESET.get());
                        helper.assertTrue(preset != null && preset.equals(RouterPreset.copyOf(source)), "preset no Tablet");
                        eq(helper, preset.configuredFaces(), 2, "faces configuradas");
                        ProfileSource from = tablet.get(ModDataComponents.PROFILE_SOURCE.get());
                        helper.assertTrue(from != null && from.nodeName().equals("Origem")
                                && from.machine().equals(CHEST), "origem no Tablet");
                        eq(helper, from.origin(), key(source), "nó de origem");

                        // cabeçalho: o resumo (tipos e faces com modo)
                        menu.pollSnapshot();
                        TabletSnapshot snapshot = menu.snapshot();
                        helper.assertTrue(snapshot.profile().isPresent(), "cabeçalho sem perfil");
                        TabletSnapshot.ProfileSummary summary = snapshot.profile().get();
                        eq(helper, summary.sourceName(), "Origem", "nome no resumo");
                        eq(helper, summary.machine(), CHEST, "máquina no resumo");
                        eq(helper, summary.faces(), 2, "faces com modo no resumo");
                        helper.assertTrue(summary.types().contains(ResourceType.ITEM)
                                && summary.types().contains(ResourceType.FLUID), "tipos no resumo");
                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                                helper.getLevel().registryAccess());
                        TabletSnapshot.HEADER_CODEC.encode(buf, snapshot);
                        helper.assertTrue(TabletSnapshot.HEADER_CODEC.decode(buf).profile().equals(snapshot.profile()),
                                "resumo pelo codec");

                        // esquecer
                        helper.assertTrue(profile(player, 90, Action.FORGET, null) >= 0, "recusou esquecer");
                        helper.assertTrue(!tablet.has(ModDataComponents.PRESET.get())
                                && !tablet.has(ModDataComponents.PROFILE_SOURCE.get()), "o perfil ficou no Tablet");
                        menu.pollSnapshot();
                        helper.assertTrue(menu.snapshot().profile().isEmpty(), "o cabeçalho ainda leva o perfil");
                        helper.succeed();
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    @GameTest(template = "empty")
    public static void pasteOneAppliesAndKeepsTheOrigin(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        RouterBlockEntity source = owned(helper, player, M1, Blocks.CHEST, "Origem");
        RouterBlockEntity target = owned(helper, player, M2, Blocks.CHEST, "Alvo");
        configure(source, null);
        TabletMenu menu = openTablet(player, 92);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, source, target))
                .thenExecute(() -> {
                    try {
                        eq(helper, profile(player, 92, Action.PASTE_ONE, key(target)), -1, "colou sem perfil");
                        profile(player, 92, Action.COPY, key(source));
                        eq(helper, profile(player, 93, Action.PASTE_ONE, key(target)), -1, "outro containerId");
                        eq(helper, profile(player, 92, Action.PASTE_ONE, key(target)), 1, "nós colados");
                        helper.assertTrue(same(source, target), "o alvo não ficou como o perfil");
                        helper.assertTrue(text(menu).contains("pasted"), "aviso: " + text(menu));
                        helper.assertTrue(menu.canUndo(), "sem desfazer depois de colar");
                        // colar só na origem não cola nada: o aviso diz isso, não "colado em 0 nós"
                        eq(helper, profile(player, 92, Action.PASTE_ONE, key(source)), 0, "colou na origem");
                        helper.assertTrue(text(menu).contains("nothing_pasted "), "aviso sem colar: " + text(menu));
                        helper.succeed();
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    @GameTest(template = "empty")
    public static void pasteGroupSkipsOriginUnloadedAndFollowsTheQuery(GameTestHelper helper) throws Exception {
        ServerPlayer player = playerWithTablet(helper);
        RouterBlockEntity source = owned(helper, player, M1, Blocks.CHEST, "Origem");
        RouterBlockEntity alvo1 = owned(helper, player, M2, Blocks.CHEST, "Alvo 1");
        RouterBlockEntity alvo2 = owned(helper, player, M3, Blocks.CHEST, "Alvo 2");
        RouterBlockEntity other = owned(helper, player, M4, Blocks.FURNACE, "Forno");
        configure(source, null);
        BlockPos far = new BlockPos(3_000_000, 64, 3_000_000);
        NodeKey ghost = new NodeKey(helper.getLevel().dimension(), far);
        TabletMenu menu = openTablet(player, 94);
        NodeIndex index = NodeIndex.get(helper.getLevel().getServer());
        Field field = NodeIndex.class.getDeclaredField("entries");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<NodeKey, NodeIndex.Entry> entries = (Map<NodeKey, NodeIndex.Entry>) field.get(index);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, source, alvo1, alvo2, other))
                .thenExecute(() -> {
                    try {
                        CompoundTag node = new CompoundTag();
                        node.putString("dim", ghost.dimension().location().toString());
                        node.putLong("pos", far.asLong());
                        node.putString("tier", RouterTier.BASIC.getSerializedName());
                        node.putString("machine", CHEST.toString());
                        node.putString("name", "Fantasma");
                        node.putUUID("placedBy", player.getUUID());
                        CompoundTag file = new CompoundTag();
                        ListTag nodes = new ListTag();
                        nodes.add(node);
                        file.put("nodes", nodes);
                        entries.put(ghost, NodeIndex.load(file, helper.getLevel().registryAccess()).entry(ghost));
                        helper.assertFalse(helper.getLevel().hasChunkAt(far), "o chunk longe já estava carregado");

                        profile(player, 94, Action.COPY, key(source));
                        // com a origem diferente: ela não pode ser recolada
                        source.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);

                        // a consulta atual manda: só os "alvo"
                        menu.setQuery(Query.of("alvo"));
                        eq(helper, profile(player, 94, Action.PASTE_GROUP, null, CHEST, List.of()), 2, "colados na consulta");
                        helper.assertTrue(same(alvo1, alvo2) && RouterPreset.copyOf(alvo1).configuredFaces() == 2,
                                "os alvos não receberam o perfil");
                        helper.assertTrue(RouterPreset.copyOf(other).configuredFaces() == 0, "colou noutra máquina");
                        eq(helper, source.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT,
                                "colou na origem");

                        // consulta limpa: todos da máquina, menos a origem; o descarregado fica de fora
                        menu.setQuery(Query.DEFAULT);
                        alvo1.setMode(ResourceType.ITEM, Direction.UP, PortMode.NONE);
                        eq(helper, profile(player, 94, Action.PASTE_GROUP, null, CHEST, List.of()), 2, "colados sem consulta");
                        eq(helper, alvo1.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.EXTRACT, "alvo 1");
                        eq(helper, source.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT,
                                "colou na origem de novo");
                        helper.assertTrue(text(menu).contains("pasted") && text(menu).contains("skipped"),
                                "aviso sem os que ficaram de fora: " + text(menu));
                        helper.assertFalse(helper.getLevel().hasChunkAt(far), "colar carregou o chunk");

                        // máquina que não está nos nós visíveis: nada
                        eq(helper, profile(player, 94, Action.PASTE_GROUP, null, null, List.of()), -1, "grupo sem máquina");
                        helper.succeed();
                    } finally {
                        entries.remove(ghost);
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    @GameTest(template = "empty")
    public static void pasteSelectedLimitsAndValidates(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        RouterBlockEntity source = owned(helper, player, M1, Blocks.CHEST, "Origem");
        RouterBlockEntity mine = owned(helper, player, M2, Blocks.CHEST, "Meu");
        RouterBlockEntity foreign = owned(helper, player, M3, Blocks.CHEST, "Alheio");
        NodeIndex.placedBy(foreign, UUID.randomUUID());
        configure(source, null);
        TabletMenu menu = openTablet(player, 95);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, source, mine, foreign))
                .thenExecute(() -> {
                    try {
                        profile(player, 95, Action.COPY, key(source));
                        List<NodeKey> many = new ArrayList<>();
                        for (int i = 0; i < TabletProfilePayload.MAX_KEYS + 1; i++) {
                            many.add(new NodeKey(helper.getLevel().dimension(), new BlockPos(i, 70, 0)));
                        }
                        eq(helper, profile(player, 95, Action.PASTE_SELECTED, null, null, many), -1, "257 chaves");
                        eq(helper, profile(player, 95, Action.PASTE_SELECTED, null, null, List.of()), -1, "sem chaves");

                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                                helper.getLevel().registryAccess());
                        TabletProfilePayload tooMany = new TabletProfilePayload(95, Action.PASTE_SELECTED,
                                Optional.empty(), Optional.empty(), many);
                        boolean refused = false;
                        try {
                            TabletProfilePayload.STREAM_CODEC.encode(buf, tooMany);
                            TabletProfilePayload.STREAM_CODEC.decode(buf);
                        } catch (RuntimeException e) {
                            refused = true;
                        }
                        helper.assertTrue(refused, "o codec aceitou mais de 256 chaves");

                        TabletProfilePayload ok = new TabletProfilePayload(95, Action.PASTE_SELECTED, Optional.empty(),
                                Optional.of(CHEST), List.of(key(mine), key(foreign), key(source)));
                        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
                        TabletProfilePayload.STREAM_CODEC.encode(buf, ok);
                        helper.assertTrue(TabletProfilePayload.STREAM_CODEC.decode(buf).equals(ok), "payload pelo codec");

                        // o alheio (invisível) e a origem ficam de fora; só o meu recebe
                        source.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
                        eq(helper, profile(player, 95, Action.PASTE_SELECTED, null, null,
                                List.of(key(mine), key(foreign), key(source))), 1, "colados nos marcados");
                        eq(helper, mine.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.EXTRACT, "nó marcado");
                        helper.assertTrue(RouterPreset.copyOf(foreign).configuredFaces() == 0, "colou num nó que não vê");
                        eq(helper, source.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.INSERT, "colou na origem");
                        helper.succeed();
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    @GameTest(template = "empty")
    public static void networkTheViewerCannotUseStaysAsItWas(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork mine = data.create(player.getUUID(), "teste-perfil-minha");
        WaNetwork foreignNet = data.create(UUID.randomUUID(), "teste-perfil-alheia");
        RouterBlockEntity source = owned(helper, player, M1, Blocks.CHEST, "Origem");
        RouterBlockEntity target = owned(helper, player, M2, Blocks.CHEST, "Alvo");
        configure(source, foreignNet.id());
        source.setFaceNetwork(ResourceType.FLUID, Direction.DOWN, mine.id());
        target.setFaceNetwork(ResourceType.ITEM, Direction.UP, mine.id());
        TabletMenu menu = openTablet(player, 96);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, source, target))
                .thenExecute(() -> {
                    try {
                        profile(player, 96, Action.COPY, key(source));
                        eq(helper, profile(player, 96, Action.PASTE_ONE, key(target)), 1, "colados");
                        eq(helper, target.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.EXTRACT, "modo colado");
                        eq(helper, target.faceNetwork(ResourceType.ITEM, Direction.UP), mine.id(),
                                "a rede que não pode usar entrou");
                        eq(helper, target.faceNetwork(ResourceType.FLUID, Direction.DOWN), mine.id(),
                                "a rede que pode usar não entrou");
                        helper.assertTrue(text(menu).contains("kept"), "aviso sem as faces mantidas: " + text(menu));
                        helper.succeed();
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    @GameTest(template = "empty")
    public static void undoRestoresModeFilterAndNetworksIncludingNone(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork net = data.create(player.getUUID(), "teste-perfil-desfaz");
        RouterBlockEntity source = owned(helper, player, M1, Blocks.CHEST, "Origem");
        RouterBlockEntity target = owned(helper, player, M2, Blocks.CHEST, "Alvo");
        RouterBlockEntity third = owned(helper, player, M3, Blocks.CHEST, "Terceiro");
        configure(source, net.id());
        // o alvo começa com outro modo, outro filtro e sem rede; o terceiro, com rede
        target.setMode(ResourceType.ITEM, Direction.UP, PortMode.BOTH);
        target.setFilter(ResourceType.ITEM, Direction.UP, onlyOf(Items.STONE));
        third.setFaceNetwork(ResourceType.ITEM, Direction.UP, net.id());
        TabletMenu menu = openTablet(player, 97);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, source, target, third))
                .thenExecute(() -> {
                    try {
                        eq(helper, profile(player, 97, Action.UNDO, null), -1, "desfez sem ter colado");
                        RouterPreset targetBefore = RouterPreset.copyOf(target);
                        RouterPreset thirdBefore = RouterPreset.copyOf(third);
                        profile(player, 97, Action.COPY, key(source));
                        eq(helper, profile(player, 97, Action.PASTE_SELECTED, null, null,
                                List.of(key(target), key(third))), 2, "colados");
                        eq(helper, target.faceNetwork(ResourceType.ITEM, Direction.UP), net.id(), "rede colada");
                        helper.assertTrue(!RouterPreset.copyOf(target).equals(targetBefore), "colar não mudou o alvo");

                        eq(helper, profile(player, 98, Action.UNDO, null), -1, "outro containerId");
                        eq(helper, profile(player, 97, Action.UNDO, null), 2, "restaurados");
                        helper.assertTrue(RouterPreset.copyOf(target).equals(targetBefore), "o alvo não voltou: "
                                + RouterPreset.copyOf(target) + " x " + targetBefore);
                        eq(helper, target.faceNetwork(ResourceType.ITEM, Direction.UP), null, "voltou a sem rede");
                        eq(helper, target.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.BOTH, "modo");
                        eq(helper, target.face(ResourceType.ITEM, Direction.UP).filter(), onlyOf(Items.STONE), "filtro");
                        helper.assertTrue(RouterPreset.copyOf(third).equals(thirdBefore), "o terceiro não voltou");
                        helper.assertFalse(menu.canUndo(), "ainda pode desfazer depois de desfazer");
                        eq(helper, profile(player, 97, Action.UNDO, null), -1, "desfez duas vezes");
                        helper.succeed();
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    @GameTest(template = "empty")
    public static void undoIsLostWhenTheTabletCloses(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        RouterBlockEntity source = owned(helper, player, M1, Blocks.CHEST, "Origem");
        RouterBlockEntity target = owned(helper, player, M2, Blocks.CHEST, "Alvo");
        configure(source, null);
        TabletMenu first = openTablet(player, 99);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, source, target))
                .thenExecute(() -> {
                    try {
                        profile(player, 99, Action.COPY, key(source));
                        profile(player, 99, Action.PASTE_ONE, key(target));
                        helper.assertTrue(first.canUndo(), "sem desfazer");
                        RouterPreset pasted = RouterPreset.copyOf(target);
                        // fechar o Tablet e abrir de novo: o menu novo não sabe o que foi colado
                        TabletMenu second = openTablet(player, 100);
                        helper.assertFalse(second.canUndo(), "o desfazer sobreviveu ao fechar");
                        eq(helper, profile(player, 100, Action.UNDO, null), -1, "desfez num menu novo");
                        eq(helper, profile(player, 99, Action.UNDO, null), -1, "desfez num menu fechado");
                        helper.assertTrue(RouterPreset.copyOf(target).equals(pasted), "o alvo mudou");
                        helper.succeed();
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    @GameTest(template = "empty")
    public static void undoSkipsWhatIsOutOfReach(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        RouterBlockEntity source = owned(helper, player, M1, Blocks.CHEST, "Origem");
        RouterBlockEntity target = owned(helper, player, M2, Blocks.CHEST, "Alvo");
        configure(source, null);
        TabletMenu menu = openTablet(player, 101);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, source, target))
                .thenExecute(() -> {
                    try {
                        Vec3 near = player.position();
                        RouterPreset before = RouterPreset.copyOf(target);
                        profile(player, 101, Action.COPY, key(source));
                        profile(player, 101, Action.PASTE_ONE, key(target));
                        RouterPreset pasted = RouterPreset.copyOf(target);
                        player.moveTo(near.add(300, 0, 0));
                        eq(helper, profile(player, 101, Action.UNDO, null), 0, "restaurados fora do alcance");
                        helper.assertTrue(RouterPreset.copyOf(target).equals(pasted), "desfez fora do alcance");
                        player.moveTo(near);
                        helper.assertTrue(before.configuredFaces() == 0, "teste mal montado");
                        helper.succeed();
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    @GameTest(template = "empty")
    public static void profileDiffFindsTheFirstDifference(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork one = data.create(player.getUUID(), "teste-diff-um");
        WaNetwork two = data.create(player.getUUID(), "teste-diff-dois");
        RouterBlockEntity a = owned(helper, player, M1, Blocks.CHEST, "A");
        RouterBlockEntity b = owned(helper, player, M2, Blocks.CHEST, "B");
        configure(a, one.id());
        configure(b, one.id());
        RouterPreset profile = RouterPreset.copyOf(a);
        helper.assertTrue(ProfileDiff.firstDifference(profile, b).isEmpty(), "cópias diferem");
        helper.assertTrue(ProfileDiff.match(profile, b).equal(), "cópias não são iguais");

        // a face de cima do baú, nos lados da máquina (o painel mostra Cima)
        b.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        eq(helper, ProfileDiff.firstDifference(profile, b).orElse(null),
                ProfileMatch.differs(ResourceType.ITEM, RemoteSide.TOP, What.MODE), "modo");
        b.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);

        b.setFaceNetwork(ResourceType.ITEM, Direction.UP, two.id());
        eq(helper, ProfileDiff.firstDifference(profile, b).orElse(null),
                ProfileMatch.differs(ResourceType.ITEM, RemoteSide.TOP, What.NETWORK), "rede");
        b.setFaceNetwork(ResourceType.ITEM, Direction.UP, one.id());

        b.setFilter(ResourceType.ITEM, Direction.UP, onlyOf(Items.STONE));
        eq(helper, ProfileDiff.firstDifference(profile, b).orElse(null),
                ProfileMatch.differs(ResourceType.ITEM, RemoteSide.TOP, What.FILTER), "filtro");
        b.setFilter(ResourceType.ITEM, Direction.UP, onlyOf(Items.DIAMOND));

        b.setPriority(ResourceType.ITEM, Direction.UP, 9);
        eq(helper, ProfileDiff.firstDifference(profile, b).orElse(null),
                ProfileMatch.differs(ResourceType.ITEM, RemoteSide.TOP, What.PRIORITY), "prioridade");
        b.setPriority(ResourceType.ITEM, Direction.UP, 5);

        b.setRedstone(ResourceType.ITEM, Direction.UP, RedstoneMode.HIGH);
        eq(helper, ProfileDiff.firstDifference(profile, b).orElse(null),
                ProfileMatch.differs(ResourceType.ITEM, RemoteSide.TOP, What.REDSTONE), "redstone");
        b.setRedstone(ResourceType.ITEM, Direction.UP, RedstoneMode.IGNORE);

        // a ordem é a dos tipos carregados e depois a dos lados da máquina: uma diferença em Fluidos vem depois de Itens
        b.setMode(ResourceType.FLUID, Direction.DOWN, PortMode.EXTRACT);
        eq(helper, ProfileDiff.firstDifference(profile, b).orElse(null),
                ProfileMatch.differs(ResourceType.FLUID, RemoteSide.BOTTOM, What.MODE), "fluido");
        b.setPriority(ResourceType.ITEM, Direction.UP, 1);
        eq(helper, ProfileDiff.firstDifference(profile, b).orElse(null),
                ProfileMatch.differs(ResourceType.ITEM, RemoteSide.TOP, What.PRIORITY), "itens antes de fluidos");

        // um tipo que o jogo não carrega não conta
        helper.assertTrue(LoadedTypes.contains(ResourceType.ITEM), "Itens sempre carregado");

        // faces que o perfil não tem: sem rede no perfil, a rede do nó não conta (colar não a tira)
        b.setPriority(ResourceType.ITEM, Direction.UP, 5);
        b.setMode(ResourceType.FLUID, Direction.DOWN, PortMode.INSERT);
        b.setFaceNetwork(ResourceType.FLUID, Direction.DOWN, two.id());
        helper.assertTrue(ProfileDiff.firstDifference(profile, b).isEmpty(),
                "rede do nó onde o perfil não tem");
        helper.succeed();
    }

    /**
     * O lado da diferença e os quadradinhos da Lista são os da máquina, como no painel: com o roteador preso na
     * lateral de um baú virado para o Sul, a face Leste é a Direita do baú (vista de frente), não a Frente do
     * roteador.
     */
    @GameTest(template = "empty")
    public static void profileDiffAndIndexNameTheMachineSide(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        WaNetwork network = NetworkSavedData.get(helper.getLevel().getServer()).create(player.getUUID(), "teste-lado");
        RouterBlockEntity a = owned(helper, player, M1, Blocks.CHEST, "A");
        configure(a, network.id());
        RouterPreset profile = RouterPreset.copyOf(a);
        helper.setBlock(M4, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        helper.setBlock(M4.east(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.EAST));
        RouterBlockEntity side = helper.getBlockEntity(M4.east());
        NodeIndex.placedBy(side, player.getUUID());
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, side))
                .thenExecute(() -> {
                    side.setMode(ResourceType.ITEM, Direction.EAST, PortMode.INSERT);
                    eq(helper, side.attachedFront(), Direction.SOUTH.ordinal(), "frente do baú");
                    eq(helper, ProfileDiff.firstDifference(profile, side).orElse(null),
                            ProfileMatch.differs(ResourceType.ITEM, RemoteSide.RIGHT, What.MODE), "lado da diferença");
                    NodeIndex.Entry entry = NodeIndex.get(helper.getLevel().getServer()).entry(key(side));
                    eq(helper, entry.faceMode(ResourceType.ITEM, RemoteSide.RIGHT), PortMode.INSERT, "quadradinho da Direita");
                    eq(helper, entry.faceMode(ResourceType.ITEM, RemoteSide.FRONT), PortMode.NONE, "quadradinho da Frente");
                    helper.succeed();
                });
    }

    @GameTest(template = "empty")
    public static void restoreToLeavesFacesExactlyAsInThePreset(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork net = data.create(player.getUUID(), "teste-restaura");
        RouterBlockEntity router = owned(helper, player, M1, Blocks.CHEST, "R");
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        RouterPreset before = RouterPreset.copyOf(router);
        router.setFaceNetwork(ResourceType.ITEM, Direction.UP, net.id());
        router.setMode(ResourceType.FLUID, Direction.DOWN, PortMode.BOTH);
        router.setFaceNetwork(ResourceType.ENERGY, Direction.EAST, net.id());
        before.restoreTo(router);
        helper.assertTrue(RouterPreset.copyOf(router).equals(before), "restoreTo não devolveu o preset");
        eq(helper, router.faceNetwork(ResourceType.ITEM, Direction.UP), null, "rede de Itens");
        eq(helper, router.faceNetwork(ResourceType.ENERGY, Direction.EAST), null, "rede de Energia");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nodeViewComparesOnlyWithAStoredProfile(GameTestHelper helper) throws Exception {
        ServerPlayer player = playerWithTablet(helper);
        RouterBlockEntity source = owned(helper, player, M1, Blocks.CHEST, "Origem");
        RouterBlockEntity same = owned(helper, player, M2, Blocks.CHEST, "Igual");
        RouterBlockEntity different = owned(helper, player, M3, Blocks.CHEST, "Diferente");
        configure(source, null);
        configure(same, null);
        configure(different, null);
        different.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        BlockPos far = new BlockPos(3_000_000, 64, 3_000_000);
        NodeKey ghost = new NodeKey(helper.getLevel().dimension(), far);
        TabletMenu menu = openTablet(player, 102);
        NodeIndex index = NodeIndex.get(helper.getLevel().getServer());
        Field field = NodeIndex.class.getDeclaredField("entries");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<NodeKey, NodeIndex.Entry> entries = (Map<NodeKey, NodeIndex.Entry>) field.get(index);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, source, same, different))
                .thenExecute(() -> {
                    try {
                        CompoundTag node = new CompoundTag();
                        node.putString("dim", ghost.dimension().location().toString());
                        node.putLong("pos", far.asLong());
                        node.putString("tier", RouterTier.BASIC.getSerializedName());
                        node.putString("machine", CHEST.toString());
                        node.putString("name", "Fantasma");
                        node.putUUID("placedBy", player.getUUID());
                        CompoundTag file = new CompoundTag();
                        ListTag nodes = new ListTag();
                        nodes.add(node);
                        file.put("nodes", nodes);
                        entries.put(ghost, NodeIndex.load(file, helper.getLevel().registryAccess()).entry(ghost));

                        menu.pollSnapshot();
                        for (NodeView view : menu.snapshot().nodes()) {
                            helper.assertTrue(view.profile().isEmpty(), "comparação sem perfil: " + view.name());
                        }

                        profile(player, 102, Action.COPY, key(source));
                        menu.setQuery(Query.DEFAULT);
                        helper.assertTrue(menu.pollSnapshot() != null, "sem snapshot depois de copiar");
                        TabletSnapshot snapshot = menu.snapshot();
                        int seen = 0;
                        for (NodeView view : snapshot.nodes()) {
                            if (view.key().equals(key(source)) || view.key().equals(key(same))) {
                                helper.assertTrue(view.profile().isPresent() && view.profile().get().equal(),
                                        "esperava igual: " + view.name() + " " + view.profile());
                                seen++;
                            } else if (view.key().equals(key(different))) {
                                eq(helper, view.profile().orElse(null),
                                        ProfileMatch.differs(ResourceType.ITEM, RemoteSide.TOP, What.MODE), "diferente");
                                seen++;
                            } else if (view.key().equals(ghost)) {
                                helper.assertTrue(view.profile().isEmpty(), "nó descarregado compara");
                                seen++;
                            }
                        }
                        eq(helper, seen, 4, "nós vistos");

                        // a comparação vai na página pelo codec
                        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                                helper.getLevel().registryAccess());
                        TabletSnapshot.Page.STREAM_CODEC.encode(buf, snapshot.page());
                        helper.assertTrue(TabletSnapshot.Page.STREAM_CODEC.decode(buf).equals(snapshot.page()),
                                "página pelo codec");

                        // colar no diferente o deixa igual na próxima montagem
                        profile(player, 102, Action.PASTE_ONE, key(different));
                        menu.setQuery(Query.of(""));
                        menu.pollSnapshot();
                        for (NodeView view : menu.snapshot().nodes()) {
                            if (view.key().equals(key(different))) {
                                helper.assertTrue(view.profile().isPresent() && view.profile().get().equal(),
                                        "continua diferente depois de colar: " + view.profile());
                            }
                        }

                        profile(player, 102, Action.FORGET, null);
                        menu.setQuery(Query.DEFAULT);
                        menu.pollSnapshot();
                        for (NodeView view : menu.snapshot().nodes()) {
                            helper.assertTrue(view.profile().isEmpty(), "comparação depois de esquecer: " + view.name());
                        }
                        helper.succeed();
                    } finally {
                        entries.remove(ghost);
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    @GameTest(template = "empty")
    public static void profileSourceCodecs(GameTestHelper helper) {
        ProfileSource source = new ProfileSource("Nó", CHEST, NodeKey.of(helper.getLevel(), BlockPos.ZERO));
        var tag = ProfileSource.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, source).getOrThrow();
        helper.assertTrue(ProfileSource.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow().equals(source),
                "ProfileSource pelo NBT");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        ProfileSource.STREAM_CODEC.encode(buf, source);
        helper.assertTrue(ProfileSource.STREAM_CODEC.decode(buf).equals(source), "ProfileSource pela rede");
        helper.succeed();
    }

    /** Um nome longo vindo do NBT (criativo, /give) é cortado no codec e o pacote ainda codifica. */
    @GameTest(template = "empty")
    public static void profileSourceLongNameIsCut(GameTestHelper helper) {
        ProfileSource source = new ProfileSource("Nó", CHEST, NodeKey.of(helper.getLevel(), BlockPos.ZERO));
        CompoundTag tag = (CompoundTag) ProfileSource.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, source)
                .getOrThrow();
        tag.putString("name", "x".repeat(300));
        ProfileSource parsed = ProfileSource.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
        eq(helper, parsed.nodeName().length(), ProfileSource.MAX_NAME, "nome não foi cortado");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        ProfileSource.STREAM_CODEC.encode(buf, parsed);
        helper.assertTrue(ProfileSource.STREAM_CODEC.decode(buf).equals(parsed), "ProfileSource longo pela rede");
        helper.succeed();
    }

    /** Copiar com o jogador em outra dimensão (tier com crossDimension) grava a máquina do nó, não a do mundo do jogador. */
    @GameTest(template = "empty", batch = "wirelessautomate_config")
    public static void copyAcrossDimensionsRecordsTheNodeMachine(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        helper.setBlock(M1, Blocks.CHEST);
        helper.setBlock(M1.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP)
                .setValue(RouterBlock.TIER, RouterTier.ELITE));
        RouterBlockEntity source = helper.getBlockEntity(M1.above());
        source.setName("Origem");
        NodeIndex.placedBy(source, player.getUUID());
        configure(source, null);
        TabletMenu menu = openTablet(player, 120);
        net.minecraft.server.level.ServerLevel home = helper.getLevel();
        boolean before = io.github.matheusanbs.wirelessautomate.Config.TIERS.get(RouterTier.ELITE).crossDimension().get();
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, source))
                .thenExecute(() -> {
                    try {
                        io.github.matheusanbs.wirelessautomate.Config.TIERS.get(RouterTier.ELITE).crossDimension().set(true);
                        net.minecraft.server.level.ServerLevel nether = home.getServer()
                                .getLevel(net.minecraft.world.level.Level.NETHER);
                        helper.assertTrue(nether != null, "sem o Nether");
                        BlockPos machinePos = source.machinePos();
                        boolean wasLoaded = nether.hasChunkAt(machinePos);
                        player.setServerLevel(nether);
                        helper.assertTrue(profile(player, 120, Action.COPY, key(source)) >= 0, "recusou copiar");
                        ProfileSource from = player.getInventory().getItem(0).get(ModDataComponents.PROFILE_SOURCE.get());
                        helper.assertTrue(from != null, "sem origem no Tablet");
                        eq(helper, from.machine(), CHEST, "máquina gravada");
                        if (!wasLoaded) {
                            helper.assertFalse(nether.hasChunkAt(machinePos), "copiar carregou o chunk no Nether");
                        }
                        helper.succeed();
                    } finally {
                        player.setServerLevel(home);
                        io.github.matheusanbs.wirelessautomate.Config.TIERS.get(RouterTier.ELITE).crossDimension().set(before);
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    /** Desfazer pula o nó que mudou depois do colar e conta-o no aviso; os outros voltam. */
    @GameTest(template = "empty")
    public static void undoSkipsNodesChangedAfterThePaste(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        RouterBlockEntity source = owned(helper, player, M1, Blocks.CHEST, "Origem");
        RouterBlockEntity changed = owned(helper, player, M2, Blocks.CHEST, "Mudado");
        RouterBlockEntity kept = owned(helper, player, M3, Blocks.CHEST, "Intacto");
        configure(source, null);
        TabletMenu menu = openTablet(player, 121);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, source, changed, kept))
                .thenExecute(() -> {
                    try {
                        RouterPreset keptBefore = RouterPreset.copyOf(kept);
                        profile(player, 121, Action.COPY, key(source));
                        eq(helper, profile(player, 121, Action.PASTE_SELECTED, null, null,
                                List.of(key(changed), key(kept))), 2, "colados");
                        changed.setMode(ResourceType.ITEM, Direction.DOWN, PortMode.BOTH);
                        RouterPreset changedNow = RouterPreset.copyOf(changed);
                        eq(helper, profile(player, 121, Action.UNDO, null), 1, "restaurados");
                        helper.assertTrue(RouterPreset.copyOf(changed).equals(changedNow), "desfez por cima da mudança");
                        helper.assertTrue(RouterPreset.copyOf(kept).equals(keptBefore), "o intacto não voltou");
                        helper.assertTrue(text(menu).contains("undone_skipped"), "aviso sem os de fora: " + text(menu));
                        helper.succeed();
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }

    /** Colar em vários nós e desfazer gastam fichas por jogador; sem ficha, recusa com um aviso. */
    @GameTest(template = "empty")
    public static void bulkProfileActionsAreRateLimited(GameTestHelper helper) {
        ServerPlayer player = playerWithTablet(helper);
        RouterBlockEntity source = owned(helper, player, M1, Blocks.CHEST, "Origem");
        RouterBlockEntity target = owned(helper, player, M2, Blocks.CHEST, "Alvo");
        configure(source, null);
        TabletMenu menu = openTablet(player, 122);
        helper.startSequence()
                .thenWaitUntil(() -> loaded(helper, source, target))
                .thenExecute(() -> {
                    try {
                        profile(player, 122, Action.COPY, key(source));
                        int refused = 0;
                        for (int i = 0; i < 12; i++) {
                            if (profile(player, 122, Action.PASTE_SELECTED, null, null, List.of(key(target))) < 0) {
                                refused++;
                            }
                        }
                        helper.assertTrue(refused > 0, "nenhum colar recusado em 12 seguidos");
                        helper.assertTrue(text(menu).contains("busy"), "aviso sem a recusa: " + text(menu));
                        helper.succeed();
                    } finally {
                        player.containerMenu = player.inventoryMenu;
                    }
                });
    }
}
