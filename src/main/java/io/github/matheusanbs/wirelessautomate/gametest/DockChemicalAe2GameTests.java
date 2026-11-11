package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.compat.ae2.Ae2Chemicals;
import io.github.matheusanbs.wirelessautomate.dock.DockAe2;
import io.github.matheusanbs.wirelessautomate.dock.DockRs2;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Químicos da Doca com o AE2 de verdade e o Applied Mekanistics: um pedido de uma CPU com item e químico (um minério e
 * 1.000 mB de hidrogênio dão 1.000 mB de oxigênio) passa pelo Pattern Provider e chega inteiro à máquina de químico de
 * teste ({@link ChemicalTestTanks#DOCK_MACHINE_BLOCK}); o oxigênio que ela "produz" volta direto à rede ME como
 * {@code MekanismKey}, a CPU o conta e o pedido termina. Rodam só na run {@code runGameTestServerDockChemicals}, que
 * tem o Mekanism, o AE2, o Applied Mekanistics, o RS2 e a integração do RS2 na pasta mods e liga o namespace
 * {@value #NAMESPACE} (o mesmo dos testes do RS2, {@code DockChemicalRs2GameTests}).
 *
 * <p>Nenhum tipo do AE2 nem do Mekanism nas assinaturas: as chaves passam como {@code Object} pelo
 * {@link Ae2TestSupport} e pelo {@link ChemicalTestSupport}.
 */
@GameTestHolder(DockChemicalAe2GameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class DockChemicalAe2GameTests {
    static final String NAMESPACE = "wirelessautomate_dockchem";

    private static final BlockPos DOCK = new BlockPos(2, 1, 2);
    /** O provedor ao norte da Doca; o armazenamento de crafting, a energia e o drive como em {@link Ae2DockGameTests}. */
    private static final BlockPos PROVIDER = DOCK.north();
    private static final BlockPos CPU = PROVIDER.west();
    private static final BlockPos ENERGY = CPU.west();
    private static final BlockPos DRIVE = CPU.north();
    private static final BlockPos MACHINE = new BlockPos(4, 1, 2);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final ResourceLocation HYDROGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen");
    private static final ResourceLocation OXYGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen");

    private DockChemicalAe2GameTests() {
    }

    /** A run dos químicos da Doca, com todos os mods dela. */
    static boolean enabled() {
        return Boolean.getBoolean("wirelessautomate.dockChemicalTests") && ModList.get().isLoaded("appmek")
                && ModList.get().isLoaded("refinedstorage_mekanism_integration") && ChemicalTestTanks.enabled();
    }

    /** A run carrega os cinco mods, as pontes os veem e a máquina de químico de teste está ligada. */
    @GameTest(template = "empty")
    public static void dockChemicalsLoaded(GameTestHelper helper) {
        if (!Boolean.getBoolean("wirelessautomate.dockChemicalTests")) {
            helper.succeed();
            return;
        }
        for (String mod : new String[] {"mekanism", "ae2", "appmek", "refinedstorage",
                "refinedstorage_mekanism_integration"}) {
            helper.assertTrue(ModList.get().isLoaded(mod), mod + " não carregou na run dos químicos da Doca");
        }
        helper.assertTrue(Chemicals.LOADED && DockAe2.LOADED && DockRs2.LOADED, "uma ponte não vê o mod dela");
        helper.assertTrue(Ae2Chemicals.LOADED, "a ponte do Applied Mekanistics não o vê");
        helper.assertTrue(ChemicalTestTanks.enabled(), "a máquina de químico de teste está desligada");
        helper.succeed();
    }

    /**
     * A rede de verdade: um pedido de 1.000 mB de oxigênio pela CPU. O provedor empurra o minério e o hidrogênio à
     * Doca, que os põe na máquina (o minério em cima, o hidrogênio na entrada do lado); o oxigênio da saída volta à
     * rede ME, sem passar pelo retorno do provedor, e o pedido termina.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void cpuChemicalOrderReturnsToNetwork(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = network(helper);
        BlockPos machine = helper.absolutePos(MACHINE);
        BlockPos provider = helper.absolutePos(PROVIDER);
        Object hydrogen = Ae2TestSupport.chemicalKey(ChemicalTestSupport.chemical(HYDROGEN, 1));
        Object oxygen = Ae2TestSupport.chemicalKey(ChemicalTestSupport.chemical(OXYGEN, 1));
        helper.assertTrue(Ae2TestSupport.installPattern(helper.getLevel(), provider,
                Ae2TestSupport.itemAndChemicalPattern(Items.RAW_IRON, 1, hydrogen, 1_000, oxygen, 1_000)),
                "o padrão não entrou no provedor");
        Object[] plan = new Object[1];
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> DockChemicalGameTests.linkMachine(helper, dock, MACHINE))
                .thenWaitUntil(() -> {
                    String missing = Ae2TestSupport.notReadyFor(helper.getLevel(), provider, oxygen);
                    if (missing != null) {
                        throw new GameTestAssertException("a rede ainda não subiu: " + missing);
                    }
                })
                .thenExecute(() -> {
                    helper.assertTrue(Ae2TestSupport.stock(helper.getLevel(), provider, Items.RAW_IRON, 1) == 1,
                            "o minério não coube");
                    helper.assertTrue(Ae2TestSupport.stockKey(helper.getLevel(), provider, hydrogen, 1_000) == 1_000,
                            "o hidrogênio não coube");
                })
                .thenIdle(2)
                .thenExecute(() -> plan[0] = Ae2TestSupport.planKey(helper.getLevel(), provider, oxygen, 1_000))
                .thenWaitUntil(() -> helper.assertTrue(Ae2TestSupport.planned(plan[0]), "calculando"))
                .thenExecute(() -> {
                    String error = Ae2TestSupport.submit(helper.getLevel(), provider, plan[0]);
                    if (error != null) {
                        throw new GameTestAssertException(error);
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        ChemicalTestSupport.dockMachineInput(machine, HYDROGEN) == 1_000,
                        "hidrogênio na máquina: " + ChemicalTestSupport.dockMachineInput(machine, HYDROGEN)))
                .thenExecute(() -> {
                    ItemStack slot = ChemicalTestSupport.dockMachineItem(machine);
                    helper.assertTrue(slot.is(Items.RAW_IRON) && slot.getCount() == 1, "slot: " + slot);
                    helper.assertFalse(dock.side(NORTH).hasPending(), "ficou pendente");
                    helper.assertTrue(Ae2TestSupport.storedKey(helper.getLevel(), provider, hydrogen) == 0,
                            "sobrou hidrogênio na rede");
                    // A receita pronta: a máquina de teste não tem block entity, então o aviso do setChanged dela
                    // (que acorda a volta) vai à mão.
                    ChemicalTestSupport.fillDockMachineOutput(machine, OXYGEN, 1_000);
                    helper.getLevel().blockEntityChanged(machine);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        Ae2TestSupport.storedKey(helper.getLevel(), provider, oxygen) == 1_000,
                        "oxigênio na rede: " + Ae2TestSupport.storedKey(helper.getLevel(), provider, oxygen)
                                + ", na saída " + ChemicalTestSupport.dockMachineOutput(machine) + ", devolvidos "
                                + dock.side(NORTH).returnedChemical() + ", provedor cheio "
                                + dock.side(NORTH).providerFull()))
                .thenExecute(() -> {
                    helper.assertTrue(ChemicalTestSupport.dockMachineOutput(machine) == 0, "sobrou na saída");
                    long returned = dock.side(NORTH).returnedChemical();
                    helper.assertTrue(returned == 1_000, "devolvidos: " + returned);
                    helper.assertTrue(Ae2TestSupport.returnEmpty(helper.getLevel(), provider, Direction.SOUTH),
                            "o oxigênio passou pelo retorno do provedor");
                })
                .thenWaitUntil(() -> helper.assertFalse(Ae2TestSupport.requestingKey(helper.getLevel(), provider,
                        oxygen), "o pedido não terminou"))
                .thenSucceed();
    }

    /**
     * A Doca, a máquina de químico, o provedor ao norte e a rede ligada a ele: energia criativa, um armazenamento de
     * crafting e um drive com uma célula de itens e uma de químicos.
     */
    private static PatternDockBlockEntity network(GameTestHelper helper) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER,
                RouterTier.BASIC));
        ChemicalTestSupport.resetDockMachine(helper.absolutePos(MACHINE));
        helper.setBlock(MACHINE, ChemicalTestTanks.DOCK_MACHINE_BLOCK);
        helper.setBlock(PROVIDER, Ae2DockGameTests.ae2("pattern_provider"));
        helper.setBlock(CPU, Ae2DockGameTests.ae2("1k_crafting_storage"));
        helper.setBlock(ENERGY, Ae2DockGameTests.ae2("creative_energy_cell"));
        helper.setBlock(DRIVE, Ae2DockGameTests.ae2("drive"));
        BlockPos drive = helper.absolutePos(DRIVE);
        helper.assertTrue(Ae2TestSupport.insertCell(helper.getLevel(), drive), "a célula de itens não entrou");
        helper.assertTrue(Ae2TestSupport.insertCell(helper.getLevel(), drive,
                ResourceLocation.fromNamespaceAndPath("appmek", "chemical_storage_cell_1k"), 1),
                "a célula de químicos não entrou");
        return helper.getBlockEntity(DOCK);
    }

    private static void registered(PatternDockBlockEntity dock) {
        if (!NetworkManager.get().hasVisitor(dock.returns())) {
            throw new GameTestAssertException("a Doca ainda não carregou");
        }
    }
}
