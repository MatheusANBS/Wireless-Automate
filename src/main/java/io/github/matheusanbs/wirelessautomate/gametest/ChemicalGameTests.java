package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageChemicalTankBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Objects;
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
import org.jetbrains.annotations.Nullable;

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

    /** Os contadores da porta: a origem entregou os 2000 mB e o destino recebeu os mesmos 2000. */
    @GameTest(template = "empty")
    public static void chemicalCountsBothSides(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-quimico-fluxo");
        RouterBlockEntity source = tank(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity target = tank(helper, B, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(source)
                        && NetworkManager.get().contains(target), "roteadores não registrados"))
                .thenExecute(() -> fill(helper, A, HYDROGEN, 2_000))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(amount(helper, B, HYDROGEN), 2_000L, "hidrogênio no destino");
                    helper.assertValueEqual(NetworkManager.get().gaveForTests(source, null, ResourceType.CHEMICAL,
                            Direction.UP), 2_000L, "entregue pela origem");
                    helper.assertValueEqual(NetworkManager.get().gotForTests(target, null, ResourceType.CHEMICAL,
                            Direction.UP), 2_000L, "recebido pelo destino");
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(NetworkManager.get().gotForTests(source, null, ResourceType.CHEMICAL,
                            Direction.UP), 0L, "recebido pela origem");
                    helper.assertValueEqual(NetworkManager.get().gaveForTests(target, null, ResourceType.CHEMICAL,
                            Direction.UP), 0L, "entregue pelo destino");
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
                            List.of(new FilterEntry.ChemicalEntry(HYDROGEN, 0, 500))));
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

    // ------------------------------------------------------------------ Tanque Químico do mod

    private static StorageChemicalTankBlockEntity chemicalTank(GameTestHelper helper, BlockPos pos, RouterTier tier,
            UUID network, PortMode mode) {
        helper.setBlock(pos, ModBlocks.STORAGE.get(StorageKind.CHEMICAL_TANK).get().defaultBlockState()
                .setValue(RouterBlock.TIER, tier));
        helper.setBlock(pos.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        RouterBlockEntity router = helper.getBlockEntity(pos.above());
        router.setNetworkId(network);
        router.setMode(ResourceType.CHEMICAL, Direction.UP, mode);
        return helper.getBlockEntity(pos);
    }

    /**
     * Tanque Químico: o Mekanism o vê pela capability dele (um tanque por químico), e o roteador move
     * 3 bilhões de mB de hidrogênio de um para outro em poucos ticks, sem criar nem perder nada.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void chemicalTankToChemicalTank(GameTestHelper helper) {
        if (!Chemicals.LOADED) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-tanque-quimico");
        StorageChemicalTankBlockEntity from = chemicalTank(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        chemicalTank(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);
        helper.assertTrue(ChemicalTestSupport.hasHandler(helper.getLevel(), helper.absolutePos(A)), "capability do Mekanism");
        fill(helper, A, HYDROGEN, 3_000_000_000L);
        helper.assertValueEqual(from.storage().count(HYDROGEN), 3_000_000_000L, "guardado por id");
        helper.onEachTick(() -> helper.assertValueEqual(amount(helper, A, HYDROGEN) + amount(helper, B, HYDROGEN),
                3_000_000_000L, "hidrogênio"));
        helper.succeedWhen(() -> helper.assertValueEqual(amount(helper, B, HYDROGEN), 3_000_000_000L, "no destino"));
    }

    /** Limite por tipo do Tanque Químico: o hidrogênio só até o global, o oxigênio (entrada sem limite) sem teto, também pelo Mekanism. */
    @GameTest(template = "empty")
    public static void chemicalTankTypeLimit(GameTestHelper helper) {
        if (!Chemicals.LOADED) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-tanque-quimico-filtro");
        StorageChemicalTankBlockEntity tank = chemicalTank(helper, A, RouterTier.BASIC, network, PortMode.INSERT);
        tank.setFilter(new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ChemicalEntry(OXYGEN, 0))).withTypeLimit(400));
        long rest = ChemicalTestSupport.fill(helper.getLevel(), helper.absolutePos(A), HYDROGEN, 1_000);
        helper.assertValueEqual(rest, 600L, "hidrogênio só até o limite");
        fill(helper, A, OXYGEN, 1_000);
        helper.assertValueEqual(tank.storage().count(OXYGEN), 1_000L, "oxigênio aceito");
        helper.succeed();
    }

    /** Tanque Químico Avançado (com conteúdo e filtro) no anel do Elite sobe para o Elite com os mesmos componentes. */
    @GameTest(template = "empty")
    public static void directRecipeKeepsChemicalTank(GameTestHelper helper) {
        if (!StorageKind.CHEMICAL_TANK.loaded()) {
            helper.succeed();
            return;
        }
        TierUpgradeGameTests.directStorage(helper, StorageKind.CHEMICAL_TANK);
        helper.succeed();
    }

    /** Compara redes com {@code Objects.equals}: o {@code assertValueEqual} com nulo derruba o servidor. */
    private static void assertNetwork(GameTestHelper helper, @Nullable UUID actual, @Nullable UUID expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": esperava " + expected + ", veio " + actual);
    }
}
