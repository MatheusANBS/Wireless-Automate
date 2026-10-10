package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Químicos do Mekanism: tanques de químico de teste ({@link ChemicalTestTanks}, um tanque da API
 * do Mekanism num bloco vanilla), um roteador em cima de cada (facing=UP, face configurada
 * {@link Direction#UP}). Rodam só na run {@code runGameTestServerChemicals}, que tem o Mekanism na
 * pasta mods e liga o namespace {@value #NAMESPACE} (o template é
 * {@code data/wirelessautomate_chemicals/structures/empty.nbt}). Sem o Mekanism, só passam.
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
    private static final ResourceLocation HYDROGEN = new ResourceLocation("mekanism", "hydrogen");
    private static final ResourceLocation OXYGEN = new ResourceLocation("mekanism", "oxygen");
    /** Um tipo de infusão (não é gás): o Mekanism 10.4 do 1.20.1 separa os químicos em quatro tipos (D4). */
    private static final ResourceLocation REDSTONE_INFUSION = new ResourceLocation("mekanism", "redstone");

    private static UUID newNetwork(GameTestHelper helper, String name) {
        return NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
    }

    /** Tanque de teste em {@code pos}, com um roteador em cima na rede, no modo dado. */
    private static RouterBlockEntity tank(GameTestHelper helper, BlockPos pos, UUID network, PortMode mode) {
        ChemicalTestSupport.reset(helper.absolutePos(pos));
        helper.setBlock(pos, ChemicalTestTanks.BLOCK.get());
        BlockPos routerPos = pos.above();
        helper.setBlock(routerPos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, routerPos);
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
        GameTestCompat.assertValueEqual(helper, rest, 0L, "sobra ao encher o tanque");
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
                    GameTestCompat.assertValueEqual(helper, amount(helper, B, HYDROGEN), 2_000L, "hidrogênio no destino");
                    GameTestCompat.assertValueEqual(helper, amount(helper, A, HYDROGEN), 0L, "hidrogênio na origem");
                })
                .thenSucceed();
    }

    /**
     * Porte 1.20.1 (D4): um químico que não é gás (a infusão de redstone) também anda, pela capability de infusão.
     * No {@code main} só havia um tipo de químico; aqui o roteador roda uma vez por tipo que a face oferece.
     */
    @GameTest(template = "empty")
    public static void infusionMovesBetweenTanks(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-quimico-infusao");
        RouterBlockEntity source = tank(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity target = tank(helper, B, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(source)
                        && NetworkManager.get().contains(target), "roteadores não registrados"))
                .thenExecute(() -> {
                    fill(helper, A, REDSTONE_INFUSION, 2_000);
                    fill(helper, A, HYDROGEN, 1_000);
                })
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, B, REDSTONE_INFUSION), 2_000L, "infusão no destino");
                    GameTestCompat.assertValueEqual(helper, amount(helper, A, REDSTONE_INFUSION), 0L, "infusão na origem");
                    GameTestCompat.assertValueEqual(helper, amount(helper, B, HYDROGEN), 1_000L, "hidrogênio no destino");
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
                    GameTestCompat.assertValueEqual(helper, amount(helper, B, HYDROGEN), 0L, "o filtro deixou passar hidrogênio");
                    // Agora hidrogênio com estoque: a origem guarda 500 mB.
                    source.setFilter(ResourceType.CHEMICAL, Direction.UP, new Filter(Filter.ListMode.WHITELIST, false,
                            List.of(new FilterEntry.ChemicalEntry(HYDROGEN, 500))));
                })
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, amount(helper, B, HYDROGEN), 500L, "hidrogênio no destino"))
                .thenIdle(20)
                .thenExecute(() -> GameTestCompat.assertValueEqual(helper, amount(helper, A, HYDROGEN), 500L, "estoque na origem"))
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
        helper.setBlock(A, ChemicalTestTanks.MANY_TANKS_BLOCK.get());
        helper.setBlock(A.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity source = GameTestCompat.getBlockEntity(helper, A.above());
        source.setNetworkId(network);
        source.setMode(ResourceType.CHEMICAL, Direction.UP, PortMode.EXTRACT);
        RouterBlockEntity target = tank(helper, B, network, PortMode.INSERT);
        GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.fillTank(machine, 18, HYDROGEN, 1_000), 0L, "sobra ao encher");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(source)
                        && NetworkManager.get().contains(target), "roteadores não registrados"))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, B, HYDROGEN), 1_000L, "hidrogênio no destino");
                    GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.tankAmount(machine, 18), 0L, "tanque 18");
                })
                .thenSucceed();
    }

    /** A entrada de químico sobrevive aos dois codecs (salvo e rede) e o filtro casa por id e por mod. */
    @GameTest(template = "empty")
    public static void chemicalEntryCodecsAndMatching(GameTestHelper helper) {
        Filter filter = new Filter(Filter.ListMode.WHITELIST, false, List.of(
                new FilterEntry.ChemicalEntry(HYDROGEN, 250), new FilterEntry.ModEntry("othermod", 0)));
        var ops = NbtOps.INSTANCE;
        Tag tag = Filter.CODEC.encodeStart(ops, filter).getOrThrow(false, error -> {});
        GameTestCompat.assertValueEqual(helper, Filter.CODEC.parse(ops, tag).getOrThrow(false, error -> {}), filter, "codec salvo");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer());
        try {
            Filter.STREAM_CODEC.encode(buf, filter);
            GameTestCompat.assertValueEqual(helper, Filter.STREAM_CODEC.decode(buf), filter, "codec de rede");
        } finally {
            buf.release();
        }
        helper.assertTrue(filter.testChemical(HYDROGEN), "hidrogênio exato");
        helper.assertTrue(!filter.testChemical(OXYGEN), "oxigênio não está no filtro");
        helper.assertTrue(filter.testChemical(new ResourceLocation("othermod", "gas")), "mod");
        GameTestCompat.assertValueEqual(helper, filter.chemicalStock(HYDROGEN), 250L, "estoque");
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
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, A.above());
        router.setNetworkId(before);

        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        GameTestCompat.assertValueEqual(helper, LinkerItem.effectiveTabs(linker), LoadedTypes.LIST, "Todos");
        LinkerItem.setTabs(linker, LinkerTabs.of(ResourceType.ITEM, ResourceType.CHEMICAL));
        List<ResourceType> tabs = LinkerItem.effectiveTabs(linker);
        GameTestCompat.assertValueEqual(helper, tabs, List.of(ResourceType.ITEM, ResourceType.CHEMICAL), "Itens + Químicos");

        LinkerActions.apply(router, tabs, target);
        GameTestCompat.assertValueEqual(helper, router.networkId(ResourceType.CHEMICAL), target, "químicos vinculados");
        GameTestCompat.assertValueEqual(helper, router.networkId(ResourceType.ITEM), target, "itens vinculados");
        GameTestCompat.assertValueEqual(helper, router.networkId(ResourceType.FLUID), before, "fluidos mudaram");
        helper.assertTrue(LinkerActions.inTarget(router, tabs, target), "não conta como vinculado");

        LinkerActions.apply(router, List.of(ResourceType.CHEMICAL), null);
        helper.assertTrue(router.networkId(ResourceType.CHEMICAL) == null, "químicos não desvinculados");
        GameTestCompat.assertValueEqual(helper, router.networkId(ResourceType.ITEM), target, "itens mudaram ao desvincular");

        LinkerItem.setTabs(linker, LinkerTabs.of(ResourceType.ENERGY));
        GameTestCompat.assertValueEqual(helper, LinkerItem.cycleTabs(linker, 1), LinkerTabs.of(ResourceType.CHEMICAL), "atalho Químicos");
        GameTestCompat.assertValueEqual(helper, LinkerItem.cycleTabs(linker, 1), LinkerTabs.ALL, "depois de Químicos");
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        data.remove(before);
        data.remove(target);
        helper.succeed();
    }

    // ------------------------------------------------------------------ Tanque Químico do mod

    private static StorageChemicalTankBlockEntity chemicalTank(GameTestHelper helper, BlockPos pos, RouterTier tier,
            UUID network, PortMode mode) {
        helper.setBlock(pos, ModBlocks.STORAGE.get(StorageKind.CHEMICAL_TANK).get().defaultBlockState()
                .setValue(RouterBlock.TIER, tier));
        helper.setBlock(pos.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, pos.above());
        router.setNetworkId(network);
        router.setMode(ResourceType.CHEMICAL, Direction.UP, mode);
        return GameTestCompat.getBlockEntity(helper, pos);
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
        GameTestCompat.assertValueEqual(helper, from.storage().count(HYDROGEN), 3_000_000_000L, "guardado por id");
        helper.onEachTick(() -> GameTestCompat.assertValueEqual(helper, amount(helper, A, HYDROGEN) + amount(helper, B, HYDROGEN),
                3_000_000_000L, "hidrogênio"));
        helper.succeedWhen(() -> GameTestCompat.assertValueEqual(helper, amount(helper, B, HYDROGEN), 3_000_000_000L, "no destino"));
    }

    /** Filtro de entrada do Tanque Químico: só o oxigênio entra, também pelo Mekanism. */
    @GameTest(template = "empty")
    public static void chemicalTankInputFilter(GameTestHelper helper) {
        if (!Chemicals.LOADED) {
            helper.succeed();
            return;
        }
        UUID network = newNetwork(helper, "teste-tanque-quimico-filtro");
        StorageChemicalTankBlockEntity tank = chemicalTank(helper, A, RouterTier.BASIC, network, PortMode.INSERT);
        tank.setFilter(new Filter(Filter.ListMode.WHITELIST, false, List.of(new FilterEntry.ChemicalEntry(OXYGEN, 0))));
        long rest = ChemicalTestSupport.fill(helper.getLevel(), helper.absolutePos(A), HYDROGEN, 1_000);
        GameTestCompat.assertValueEqual(helper, rest, 1_000L, "hidrogênio recusado");
        fill(helper, A, OXYGEN, 1_000);
        GameTestCompat.assertValueEqual(helper, tank.storage().count(OXYGEN), 1_000L, "oxigênio aceito");
        helper.succeed();
    }

    // ------------------------------------------------------------------ os quatro tipos (porte 1.20.1, etapa 2)

    /** Índices dos subtipos em {@code Chemicals.capabilities()} (gás, infusão, pigmento, slurry). */
    private static final int GAS = 0;
    private static final int INFUSION = 1;
    private static final int PIGMENT = 2;
    private static final int SLURRY = 3;
    private static final BlockPos C = new BlockPos(2, 1, 0);
    private static final BlockPos D = new BlockPos(0, 1, 2);
    /** O meio da estrutura: não encosta em nenhuma das quatro posições dos cantos. */
    private static final BlockPos MID = new BlockPos(1, 1, 1);
    private static final ResourceLocation SULFURIC_ACID = new ResourceLocation("mekanism", "sulfuric_acid");
    private static final ResourceLocation CARBON_INFUSION = new ResourceLocation("mekanism", "carbon");
    private static final ResourceLocation BLACK_PIGMENT = new ResourceLocation("mekanism", "black");
    private static final ResourceLocation BLUE_PIGMENT = new ResourceLocation("mekanism", "blue");
    private static final ResourceLocation DIRTY_IRON = new ResourceLocation("mekanism", "dirty_iron");
    private static final ResourceLocation CLEAN_IRON = new ResourceLocation("mekanism", "clean_iron");
    /** Uma origem que já dormiu várias vezes seguidas tem o próximo sono pelo menos assim (ticks), como no WakeGameTests. */
    private static final int DEEP_SLEEP = 32;

    /** Falha (não passa) sem os tanques de teste: este namespace só roda na run de químicos, que os liga. */
    private static boolean needsTestTanks(GameTestHelper helper) {
        if (!ChemicalTestTanks.enabled()) {
            helper.fail("precisa do Mekanism e de -Dwirelessautomate.chemicalTests=true (run gameTestServerChemicals)");
            return false;
        }
        return true;
    }

    private static void assertSubtype(GameTestHelper helper, ResourceLocation chemical, int subtype) {
        GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.subtypeOf(chemical), subtype, "subtipo de " + chemical);
    }

    private static void waitRegistered(GameTestHelper helper, RouterBlockEntity... routers) {
        for (RouterBlockEntity router : routers) {
            helper.assertTrue(NetworkManager.get().contains(router), "roteador não registrado");
        }
    }

    private static long stored(GameTestHelper helper, BlockPos pos, ResourceLocation chemical) {
        return ChemicalTestSupport.stored(helper.absolutePos(pos), chemical);
    }

    /** Pigmento (um químico que não é gás nem infusão) anda entre tanques de teste, pela capability de pigmento. */
    @GameTest(template = "empty")
    public static void pigmentMovesBetweenTanks(GameTestHelper helper) {
        if (!needsTestTanks(helper)) {
            return;
        }
        assertSubtype(helper, BLACK_PIGMENT, PIGMENT);
        UUID network = newNetwork(helper, "teste-quimico-pigmento");
        RouterBlockEntity source = tank(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity target = tank(helper, B, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenExecute(() -> fill(helper, A, BLACK_PIGMENT, 2_000))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, B, BLACK_PIGMENT), 2_000L, "pigmento no destino");
                    GameTestCompat.assertValueEqual(helper, amount(helper, A, BLACK_PIGMENT), 0L, "pigmento na origem");
                })
                .thenSucceed();
    }

    /** Slurry anda entre tanques de teste, pela capability de slurry. */
    @GameTest(template = "empty")
    public static void slurryMovesBetweenTanks(GameTestHelper helper) {
        if (!needsTestTanks(helper)) {
            return;
        }
        assertSubtype(helper, DIRTY_IRON, SLURRY);
        UUID network = newNetwork(helper, "teste-quimico-slurry");
        RouterBlockEntity source = tank(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity target = tank(helper, B, network, PortMode.INSERT);
        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenExecute(() -> fill(helper, A, DIRTY_IRON, 2_000))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, amount(helper, B, DIRTY_IRON), 2_000L, "slurry no destino");
                    GameTestCompat.assertValueEqual(helper, amount(helper, A, DIRTY_IRON), 0L, "slurry na origem");
                })
                .thenSucceed();
    }

    /**
     * Tanque Químico com gás e slurry: cada capability vê só o seu tipo (o {@code IGasHandler} mostra o hidrogênio e um
     * tanque vazio, o {@code ISlurryHandler} a slurry e um vazio, os outros dois só o vazio), e o roteador tira os dois
     * pela mesma face, cada um para o destino do seu tipo (um tanque de teste só de gás, outro só de slurry).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chemicalTankGasAndSlurryBySeparateCapabilities(GameTestHelper helper) {
        if (!needsTestTanks(helper)) {
            return;
        }
        UUID network = newNetwork(helper, "teste-tanque-quimico-gas-slurry");
        StorageChemicalTankBlockEntity from = chemicalTank(helper, A, RouterTier.ULTIMATE, network, PortMode.EXTRACT);
        RouterBlockEntity source = GameTestCompat.getBlockEntity(helper, A.above());
        RouterBlockEntity gasTarget = tank(helper, B, network, PortMode.INSERT);
        ChemicalTestSupport.exposeOnly(helper.absolutePos(B), GAS);
        RouterBlockEntity slurryTarget = tank(helper, C, network, PortMode.INSERT);
        ChemicalTestSupport.exposeOnly(helper.absolutePos(C), SLURRY);
        GameTestCompat.assertValueEqual(helper, from.storage().insert(HYDROGEN, 2_000, false), 2_000L, "hidrogênio no tanque");
        GameTestCompat.assertValueEqual(helper, from.storage().insert(DIRTY_IRON, 3_000, false), 3_000L, "slurry no tanque");

        var level = helper.getLevel();
        BlockPos tankPos = helper.absolutePos(A);
        GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.tanksSeenBy(level, tankPos, GAS),
                Arrays.asList(HYDROGEN, null), "o IGasHandler do tanque");
        GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.tanksSeenBy(level, tankPos, SLURRY),
                Arrays.asList(DIRTY_IRON, null), "o ISlurryHandler do tanque");
        GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.tanksSeenBy(level, tankPos, INFUSION),
                Collections.singletonList((ResourceLocation) null), "o IInfusionHandler do tanque");
        GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.tanksSeenBy(level, tankPos, PIGMENT),
                Collections.singletonList((ResourceLocation) null), "o IPigmentHandler do tanque");

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, gasTarget, slurryTarget))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, stored(helper, B, HYDROGEN), 2_000L, "hidrogênio no destino de gás");
                    GameTestCompat.assertValueEqual(helper, stored(helper, C, DIRTY_IRON), 3_000L, "slurry no destino de slurry");
                    helper.assertTrue(from.storage().isEmpty(), "o Tanque Químico não esvaziou");
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, stored(helper, B, DIRTY_IRON), 0L, "slurry no destino só de gás");
                    GameTestCompat.assertValueEqual(helper, stored(helper, C, HYDROGEN), 0L, "gás no destino só de slurry");
                })
                .thenSucceed();
    }

    /**
     * Encher e esvaziar um tanque do Mekanism (item, que expõe as quatro capabilities) pelo Tanque Químico, o caminho
     * da tela ({@link Chemicals#fillContainer}, {@link Chemicals#emptyContainer}), com um químico de cada tipo. O tanque
     * do Mekanism guarda um tipo por vez: cheio de gás, não aceita slurry. O tanque básico passa no máximo 1.000 mB por
     * operação (o limite de vazão do item no Mekanism; no {@code main} também: cada clique da tela é uma operação).
     */
    @GameTest(template = "empty")
    public static void chemicalTankFillsAndEmptiesMekanismTankByType(GameTestHelper helper) {
        if (!Chemicals.LOADED) {
            helper.fail("precisa do Mekanism (run gameTestServerChemicals)");
            return;
        }
        helper.setBlock(A, ModBlocks.STORAGE.get(StorageKind.CHEMICAL_TANK).get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.BASIC));
        StorageChemicalTankBlockEntity tank = GameTestCompat.getBlockEntity(helper, A);
        var storage = tank.storage();
        ItemStack container = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("mekanism", "basic_chemical_tank")));
        helper.assertTrue(!container.isEmpty(), "sem o item mekanism:basic_chemical_tank");
        for (int subtype = GAS; subtype <= SLURRY; subtype++) {
            helper.assertTrue(ChemicalTestSupport.itemExposes(container, subtype), "o tanque do Mekanism sem a capability " + subtype);
        }
        List<ResourceLocation> chemicals = List.of(HYDROGEN, REDSTONE_INFUSION, BLACK_PIGMENT, DIRTY_IRON);
        for (int subtype = GAS; subtype <= SLURRY; subtype++) {
            ResourceLocation id = chemicals.get(subtype);
            assertSubtype(helper, id, subtype);
            GameTestCompat.assertValueEqual(helper, storage.insert(id, 1_000, false), 1_000L, "no Tanque Químico: " + id);
            GameTestCompat.assertValueEqual(helper, Chemicals.fillContainer(container, storage, id), 1_000L, "encher com " + id);
            GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.itemAmount(container, id), 1_000L, "no item: " + id);
            GameTestCompat.assertValueEqual(helper, storage.count(id), 0L, "sobrou no Tanque Químico: " + id);
            GameTestCompat.assertValueEqual(helper, Chemicals.emptyContainer(container, storage), 1_000L, "esvaziar " + id);
            GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.itemAmount(container, id), 0L, "ficou no item: " + id);
            GameTestCompat.assertValueEqual(helper, storage.count(id), 1_000L, "voltou ao Tanque Químico: " + id);
        }
        // Com gás no item, a slurry não entra (o tanque do Mekanism é de um tipo por vez) e nada sai do Tanque Químico.
        GameTestCompat.assertValueEqual(helper, Chemicals.fillContainer(container, storage, HYDROGEN), 1_000L, "encher de novo com gás");
        GameTestCompat.assertValueEqual(helper, Chemicals.fillContainer(container, storage, DIRTY_IRON), 0L, "slurry num item com gás");
        GameTestCompat.assertValueEqual(helper, storage.count(DIRTY_IRON), 1_000L, "slurry no Tanque Químico");
        helper.succeed();
    }

    /**
     * Máquina real do Mekanism (Câmara de Dissolução Química) com a face de cima configurada como entrada de gás (o
     * padrão dela) e saída de slurry (mudada pela configuração de lados): o roteador nessa face, em entrada e saída,
     * põe ácido sulfúrico na câmara e tira a slurry dela, pela mesma face. Depois, os dois sentidos proibidos, com a
     * rede oferecendo o caminho: um destino de gás vivo (recebe o oxigênio que a câmara recusa) não recebe o ácido da
     * câmara nem o hidrogênio do tanque de saída de gás dela (a face de cima é só entrada de gás: este é o negativo que
     * depende da configuração de lados), e a slurry de uma origem de fora, que só tem a câmara como destino, não entra.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void mekanismMachineFaceTakesGasAndGivesSlurry(GameTestHelper helper) {
        if (!needsTestTanks(helper)) {
            return;
        }
        Block chamberBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("mekanism", "chemical_dissolution_chamber"));
        helper.assertTrue(chamberBlock != null && chamberBlock != Blocks.AIR, "sem a Câmara de Dissolução Química");
        UUID network = newNetwork(helper, "teste-quimico-maquina");
        helper.setBlock(A, chamberBlock);
        BlockEntity chamber = helper.getBlockEntity(A);
        var level = helper.getLevel();
        BlockPos chamberPos = helper.absolutePos(A);
        helper.assertTrue(ChemicalTestSupport.tanksSeenBy(level, chamberPos, GAS) != null, "a face de cima não recebe gás");
        helper.assertTrue(ChemicalTestSupport.tanksSeenBy(level, chamberPos, SLURRY) == null, "slurry na face de cima antes de configurar");
        ChemicalTestSupport.setSideData(chamber, "SLURRY", "OUTPUT", Direction.UP);
        helper.assertTrue(ChemicalTestSupport.tanksSeenBy(level, chamberPos, SLURRY) != null, "a face de cima não dá slurry");
        GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.fillMachineTank(chamber, 0, DIRTY_IRON, 1_000), 0L,
                "sobra ao pôr slurry na câmara");
        helper.setBlock(A.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity machine = GameTestCompat.getBlockEntity(helper, A.above());
        machine.setNetworkId(network);
        machine.setMode(ResourceType.CHEMICAL, Direction.UP, PortMode.BOTH);
        RouterBlockEntity gasSource = tank(helper, B, network, PortMode.EXTRACT);
        // Destino da slurry da câmara: só slurry e só a dirty_iron (a clean_iron de fora não tem outro destino que a câmara).
        RouterBlockEntity slurryTarget = tank(helper, C, network, PortMode.INSERT);
        ChemicalTestSupport.exposeOnly(helper.absolutePos(C), SLURRY);
        slurryTarget.setFilter(ResourceType.CHEMICAL, Direction.UP, new Filter(Filter.ListMode.WHITELIST, false,
                List.of(new FilterEntry.ChemicalEntry(DIRTY_IRON, 0))));
        // Origem de slurry de fora: o único destino que aceitaria a clean_iron é a câmara, pela face de saída de slurry.
        RouterBlockEntity slurrySource = tank(helper, D, network, PortMode.EXTRACT);
        fill(helper, D, CLEAN_IRON, 1_000);
        fill(helper, B, SULFURIC_ACID, 1_000);
        RouterBlockEntity[] gasTarget = {null};

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, machine, gasSource, slurryTarget, slurrySource))
                .thenWaitUntil(() -> {
                    GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.machineAmount(chamber, SULFURIC_ACID), 1_000L,
                            "ácido na câmara");
                    GameTestCompat.assertValueEqual(helper, stored(helper, C, DIRTY_IRON), 1_000L, "slurry no destino");
                })
                .thenExecute(() -> {
                    // Com a slurry fora, o tanque de saída da câmara (um tipo por vez) recebe hidrogênio, por dentro.
                    GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.fillMachineTank(chamber, 1, HYDROGEN, 1_000), 0L,
                            "sobra ao pôr hidrogênio na saída de gás da câmara");
                    // Destino de gás que aceita tudo; o oxigênio (que a câmara não aceita) prova que ele recebe.
                    gasTarget[0] = tank(helper, MID, network, PortMode.INSERT);
                    ChemicalTestSupport.exposeOnly(helper.absolutePos(MID), GAS);
                    fill(helper, B, OXYGEN, 500);
                })
                .thenWaitUntil(() -> {
                    // O tanque de teste tem um tanque de gás só: o que vazasse da câmara tomaria o lugar do oxigênio,
                    // então o vazamento é conferido antes (a mensagem do tempo esgotado aponta para ele).
                    GameTestCompat.assertValueEqual(helper, stored(helper, MID, HYDROGEN), 0L,
                            "hidrogênio tirado pela face de entrada de gás");
                    GameTestCompat.assertValueEqual(helper, stored(helper, MID, SULFURIC_ACID), 0L, "ácido tirado da câmara");
                    GameTestCompat.assertValueEqual(helper, stored(helper, MID, OXYGEN), 500L, "oxigênio no destino de gás");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, stored(helper, MID, SULFURIC_ACID), 0L, "ácido tirado da câmara");
                    GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.machineAmount(chamber, SULFURIC_ACID), 1_000L,
                            "ácido sumiu da câmara");
                    GameTestCompat.assertValueEqual(helper, stored(helper, MID, HYDROGEN), 0L,
                            "hidrogênio tirado pela face de entrada de gás");
                    GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.machineAmount(chamber, HYDROGEN), 1_000L,
                            "hidrogênio sumiu da câmara");
                    GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.machineAmount(chamber, CLEAN_IRON), 0L,
                            "slurry de fora entrou na câmara");
                    GameTestCompat.assertValueEqual(helper, stored(helper, D, CLEAN_IRON), 1_000L, "slurry de fora saiu da origem");
                })
                .thenSucceed();
    }

    /**
     * Destino sem o tipo: a origem tem slurry e o único destino é um tanque só de gás. Ele é pulado sem dormir, nada
     * anda e a origem dorme esperando destino (ofereceu algo que o filtro do destino aceitaria). Quando o destino passa
     * a expor slurry (sem trocar de block entity, com o aviso aos vizinhos de uma máquina do Mekanism que muda o lado),
     * a origem acorda na hora (só quem espera destino acorda: uma origem vazia continuaria no sono fundo) e a slurry
     * entra.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void destinationWithoutTypeIsSkippedAndSourceWaits(GameTestHelper helper) {
        if (!needsTestTanks(helper)) {
            return;
        }
        UUID network = newNetwork(helper, "teste-quimico-sem-tipo");
        RouterBlockEntity source = tank(helper, A, network, PortMode.EXTRACT);
        RouterBlockEntity target = tank(helper, B, network, PortMode.INSERT);
        BlockPos targetPos = helper.absolutePos(B);
        ChemicalTestSupport.exposeOnly(targetPos, GAS);
        fill(helper, A, DIRTY_IRON, 1_000);
        long[] exposedAt = {0};

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, target))
                .thenWaitUntil(() -> {
                    int interval = NetworkManager.get().sourceSleepInterval(source, ResourceType.CHEMICAL, Direction.UP);
                    helper.assertTrue(interval >= DEEP_SLEEP, "a origem ainda não dorme fundo: próximo sono de " + interval);
                })
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, stored(helper, B, DIRTY_IRON), 0L, "slurry no destino só de gás");
                    GameTestCompat.assertValueEqual(helper, amount(helper, A, DIRTY_IRON), 1_000L, "slurry na origem");
                    ChemicalTestSupport.exposeAlso(helper.getLevel(), targetPos, SLURRY);
                    exposedAt[0] = helper.getTick();
                    GameTestCompat.assertValueEqual(helper,
                            NetworkManager.get().sourceSleepInterval(source, ResourceType.CHEMICAL, Direction.UP), 1,
                            "próximo sono da origem, que esperava destino");
                })
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, stored(helper, B, DIRTY_IRON), 1_000L,
                        "slurry no destino"))
                .thenExecute(() -> helper.assertTrue(helper.getTick() - exposedAt[0] < 10,
                        "a origem demorou " + (helper.getTick() - exposedAt[0]) + " ticks para entregar"))
                .thenSucceed();
    }

    /**
     * Filtro por químico com os quatro tipos: a origem (muitos tanques, dois químicos de cada tipo) tem lista branca
     * com um id de cada tipo, e só esses quatro vão para o Tanque Químico; os outros quatro ficam. Trocada por
     * {@code @mekanism}, os outros também vão.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void chemicalFilterByIdAndModWithFourTypes(GameTestHelper helper) {
        if (!needsTestTanks(helper)) {
            return;
        }
        List<ResourceLocation> picked = List.of(HYDROGEN, REDSTONE_INFUSION, BLACK_PIGMENT, DIRTY_IRON);
        List<ResourceLocation> others = List.of(OXYGEN, CARBON_INFUSION, BLUE_PIGMENT, CLEAN_IRON);
        for (int subtype = GAS; subtype <= SLURRY; subtype++) {
            assertSubtype(helper, picked.get(subtype), subtype);
            assertSubtype(helper, others.get(subtype), subtype);
        }
        UUID network = newNetwork(helper, "teste-quimico-filtro-quatro");
        BlockPos machine = helper.absolutePos(A);
        ChemicalTestSupport.reset(machine);
        helper.setBlock(A, ChemicalTestTanks.MANY_TANKS_BLOCK.get());
        helper.setBlock(A.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP)
                .setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
        RouterBlockEntity source = GameTestCompat.getBlockEntity(helper, A.above());
        source.setNetworkId(network);
        source.setMode(ResourceType.CHEMICAL, Direction.UP, PortMode.EXTRACT);
        for (int subtype = GAS; subtype <= SLURRY; subtype++) {
            // Tanques diferentes por químico (o 0 e o 5 de cada tipo).
            GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.fillTank(machine, 0, picked.get(subtype), 1_000), 0L, "sobra");
            GameTestCompat.assertValueEqual(helper, ChemicalTestSupport.fillTank(machine, 5, others.get(subtype), 1_000), 0L, "sobra");
        }
        source.setFilter(ResourceType.CHEMICAL, Direction.UP, new Filter(Filter.ListMode.WHITELIST, false, List.of(
                new FilterEntry.ChemicalEntry(HYDROGEN, 0), new FilterEntry.ChemicalEntry(REDSTONE_INFUSION, 0),
                new FilterEntry.ChemicalEntry(BLACK_PIGMENT, 0), new FilterEntry.ChemicalEntry(DIRTY_IRON, 0))));
        StorageChemicalTankBlockEntity target = chemicalTank(helper, B, RouterTier.ULTIMATE, network, PortMode.INSERT);
        RouterBlockEntity targetRouter = GameTestCompat.getBlockEntity(helper, B.above());

        helper.startSequence()
                .thenWaitUntil(() -> waitRegistered(helper, source, targetRouter))
                .thenWaitUntil(() -> {
                    for (ResourceLocation id : picked) {
                        GameTestCompat.assertValueEqual(helper, target.storage().count(id), 1_000L, "no destino: " + id);
                    }
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    for (ResourceLocation id : others) {
                        GameTestCompat.assertValueEqual(helper, target.storage().count(id), 0L, "o filtro deixou passar " + id);
                    }
                    source.setFilter(ResourceType.CHEMICAL, Direction.UP, new Filter(Filter.ListMode.WHITELIST, false,
                            List.of(new FilterEntry.ModEntry("mekanism", 0))));
                })
                .thenWaitUntil(() -> {
                    for (ResourceLocation id : others) {
                        GameTestCompat.assertValueEqual(helper, target.storage().count(id), 1_000L, "pelo @mekanism: " + id);
                    }
                })
                .thenSucceed();
    }
}
