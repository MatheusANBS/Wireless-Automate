package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Químicos do Mekanism: tanques de químico de teste ({@link ChemicalTestTanks}, um tanque da API
 * do Mekanism num bloco vanilla), um roteador em cima de cada (facing=UP, face configurada
 * {@link Direction#UP}). Rodam só na run {@code runGameTestServerChemicals}, que tem o Mekanism na
 * pasta mods e liga o namespace {@value #NAMESPACE} (o template é
 * {@code data/wirelessautomate_chemicals/structure/empty.nbt}). Sem o Mekanism, só passam.
 *
 * <p>Nenhum tipo do Mekanism nas assinaturas: o NeoForge inspeciona esta classe por reflexão mesmo
 * sem o Mekanism. O que usa a API fica em {@link ChemicalTestSupport}.
 */
@GameTestHolder(ChemicalGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class ChemicalGameTests {
    static final String NAMESPACE = "wirelessautomate_chemicals";
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final ResourceLocation HYDROGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen");
    private static final ResourceLocation OXYGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen");

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    /** Tanque de teste em {@code pos}, com um roteador em cima na rede, no modo dado. */
    private static RouterBlockEntity tank(GameTestHelper helper, BlockPos pos, UUID network, PortMode mode) {
        ChemicalTestSupport.reset(helper.absolutePos(pos));
        helper.setBlock(pos, ChemicalTestTanks.BLOCK);
        BlockPos routerPos = pos.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(routerPos);
        router.setNetworkId(network);
        router.setMode(ResourceType.CHEMICAL, Direction.UP, mode);
        return router;
    }

    private static long amount(GameTestHelper helper, BlockPos pos, ResourceLocation chemical) {
        helper.assertTrue(ChemicalTestSupport.hasHandler(helper.getLevel(), helper.absolutePos(pos)),
                "tanque de teste sem capability de químico em " + pos.toShortString());
        return ChemicalTestSupport.amount(helper.getLevel(), helper.absolutePos(pos), chemical);
    }

    private static void fill(GameTestHelper helper, BlockPos pos, ResourceLocation chemical, long amount) {
        long rest = ChemicalTestSupport.fill(helper.getLevel(), helper.absolutePos(pos), chemical, amount);
        helper.assertValueEqual(rest, 0L, "sobra ao encher o tanque");
    }

    @GameTest(template = "empty")
    public static void chemicalMovesBetweenTanks(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-quimico");
        RouterBlockEntity source = tank(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity target = tank(helper, B, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(source)
                        && NetworkManager.get().contains(target), "roteadores não registrados"))
                .thenExecute(() -> fill(helper, A, HYDROGEN, 2_000))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(amount(helper, B, HYDROGEN), 2_000L, "hidrogênio no destino");
                    helper.assertValueEqual(amount(helper, A, HYDROGEN), 0L, "hidrogênio na origem");
                })
                .thenSucceed();
    }

    /** Lista branca de oxigênio na origem: o hidrogênio fica; o estoque mantém 500 mB na origem. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chemicalFilterAndStock(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-quimico-filtro");
        RouterBlockEntity source = tank(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity target = tank(helper, B, network, PortMode.INSERT);
        source.setFilter(ResourceType.CHEMICAL, Direction.UP, new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ChemicalEntry(OXYGEN, 0))));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(source)
                        && NetworkManager.get().contains(target), "roteadores não registrados"))
                .thenExecute(() -> fill(helper, A, HYDROGEN, 1_000))
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertValueEqual(amount(helper, B, HYDROGEN), 0L, "o filtro deixou passar hidrogênio");
                    // Agora hidrogênio com estoque: a origem guarda 500 mB.
                    source.setFilter(ResourceType.CHEMICAL, Direction.UP, new Filter(Filter.ListMode.WHITELIST, false,
                            List.of(new FilterEntry.ChemicalEntry(HYDROGEN, 500))));
                })
                .thenWaitUntil(() -> helper.assertValueEqual(amount(helper, B, HYDROGEN), 500L, "hidrogênio no destino"))
                .thenIdle(20)
                .thenExecute(() -> helper.assertValueEqual(amount(helper, A, HYDROGEN), 500L, "estoque na origem"))
                .thenSucceed();
    }

    /** Químico num tanque acima da janela de uma visita (16): o cursor de tanques chega nele. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chemicalBeyondSixteenTanksMoves(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-quimico-muitos-tanques");
        BlockPos machine = helper.absolutePos(A);
        ChemicalTestSupport.reset(machine);
        helper.setBlock(A, ChemicalTestTanks.MANY_TANKS_BLOCK);
        helper.setBlock(A.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity source = helper.getBlockEntity(A.above());
        source.setNetworkId(network);
        source.setMode(ResourceType.CHEMICAL, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity target = tank(helper, B, network, PortMode.INSERT);
        helper.assertValueEqual(ChemicalTestSupport.fillTank(machine, 18, HYDROGEN, 1_000), 0L, "sobra ao encher");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(source)
                        && NetworkManager.get().contains(target), "roteadores não registrados"))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(amount(helper, B, HYDROGEN), 1_000L, "hidrogênio no destino");
                    helper.assertValueEqual(ChemicalTestSupport.tankAmount(machine, 18), 0L, "tanque 18");
                })
                .thenSucceed();
    }

    /** A entrada de químico sobrevive aos dois codecs (salvo e rede) e o filtro casa por id e por mod. */
    @GameTest(template = "empty")
    public static void chemicalEntryCodecsAndMatching(GameTestHelper helper) {
        Filter filter = new Filter(Filter.ListMode.WHITELIST, false, List.of(
                new FilterEntry.ChemicalEntry(HYDROGEN, 250), new FilterEntry.ModEntry("othermod", 0)));
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag tag = Filter.CODEC.encodeStart(ops, filter).getOrThrow();
        helper.assertValueEqual(Filter.CODEC.parse(ops, tag).getOrThrow(), filter, "codec salvo");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            Filter.STREAM_CODEC.encode(buf, filter);
            helper.assertValueEqual(Filter.STREAM_CODEC.decode(buf), filter, "codec de rede");
        } finally {
            buf.release();
        }
        helper.assertTrue(filter.testChemical(HYDROGEN), "hidrogênio exato");
        helper.assertTrue(!filter.testChemical(OXYGEN), "oxigênio não está no filtro");
        helper.assertTrue(filter.testChemical(ResourceLocation.fromNamespaceAndPath("othermod", "gas")), "mod");
        helper.assertValueEqual(filter.chemicalStock(HYDROGEN), 250L, "estoque");
        // Uma entrada de químico não casa com itens (vale só para o tipo dela).
        helper.assertTrue(!filter.testItem(Items.STONE.getDefaultInstance()), "casou com item");
        helper.succeed();
    }

    /**
     * Com o Mekanism, o Vinculador conta a aba Químicos: Todos e a combinação Itens + Químicos a
     * incluem, a roda tem o atalho Químicos e vincular ou desvincular mexe na aba de químicos. Sem
     * jogador falso (o Mekanism não aceita um nesta run).
     */
    @GameTest(template = "empty")
    public static void linkerHandlesChemicalTab(GameTestHelper helper) {
        if (!Chemicals.LOADED) {
            helper.succeed();
            return;
        }
        UUID before = newNetwork(helper, "teste-vinculador-quimicos-antes");
        UUID target = newNetwork(helper, "teste-vinculador-quimicos");
        helper.setBlock(A, Blocks.STONE);
        helper.setBlock(A.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(A.above());
        router.setNetworkId(before);

        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        helper.assertValueEqual(LinkerItem.effectiveTabs(linker), List.of(ResourceType.values()), "Todos");
        LinkerItem.setTabs(linker, LinkerTabs.of(ResourceType.ITEM, ResourceType.CHEMICAL));
        List<ResourceType> tabs = LinkerItem.effectiveTabs(linker);
        helper.assertValueEqual(tabs, List.of(ResourceType.ITEM, ResourceType.CHEMICAL), "Itens + Químicos");

        LinkerActions.apply(router, tabs, target);
        helper.assertValueEqual(router.networkId(ResourceType.CHEMICAL), target, "químicos vinculados");
        helper.assertValueEqual(router.networkId(ResourceType.ITEM), target, "itens vinculados");
        helper.assertValueEqual(router.networkId(ResourceType.FLUID), before, "fluidos mudaram");
        helper.assertTrue(LinkerActions.inTarget(router, tabs, target), "não conta como vinculado");

        LinkerActions.apply(router, List.of(ResourceType.CHEMICAL), null);
        helper.assertTrue(router.networkId(ResourceType.CHEMICAL) == null, "químicos não desvinculados");
        helper.assertValueEqual(router.networkId(ResourceType.ITEM), target, "itens mudaram ao desvincular");

        LinkerItem.setTabs(linker, LinkerTabs.of(ResourceType.ENERGY));
        helper.assertValueEqual(LinkerItem.cycleTabs(linker, 1), LinkerTabs.of(ResourceType.CHEMICAL), "atalho Químicos");
        helper.assertValueEqual(LinkerItem.cycleTabs(linker, 1), LinkerTabs.ALL, "depois de Químicos");
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        data.remove(before);
        data.remove(target);
        helper.succeed();
    }
}
