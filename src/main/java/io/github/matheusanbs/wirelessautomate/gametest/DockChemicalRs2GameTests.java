package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.compat.refinedstorage.Rs2Chemicals;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Químicos da Doca com o Refined Storage 2 de verdade e a integração dele com o Mekanism: um pedido só de químico pelo
 * destino que a fábrica do RS2 dá ao Autocrafter (o químico chega à Doca, e não só ao destino de químico da
 * integração, que responde {@code SKIPPED}); e um pedido de uma rede de verdade com item e químico (um minério e
 * 1.000 mB de hidrogênio dão 1.000 mB de oxigênio) que chega inteiro à máquina de químico de teste
 * ({@link ChemicalTestTanks#DOCK_MACHINE_BLOCK}), com o oxigênio de volta à rede como {@code ChemicalResource} e a
 * tarefa terminada; e a máquina que dá menos do que ofereceu, com o que entrou a mais na rede tirado de novo. Rodam
 * só na run {@code runGameTestServerDockChemicals}, no namespace de {@link DockChemicalAe2GameTests}.
 *
 * <p>Nenhum tipo do RS2 nem do Mekanism nas assinaturas: os recursos passam como {@code Object} pelo
 * {@link Rs2TestSupport} e pelo {@link ChemicalTestSupport}.
 */
@GameTestHolder(DockChemicalAe2GameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class DockChemicalRs2GameTests {
    private static final BlockPos DOCK = new BlockPos(2, 1, 2);
    /** O Autocrafter ao norte da Doca, virado para ela; o armazenamento de itens e o Controlador a oeste. */
    private static final BlockPos AUTOCRAFTER = DOCK.north();
    private static final BlockPos STORAGE = AUTOCRAFTER.west();
    private static final BlockPos CONTROLLER = STORAGE.west();
    /** O armazenamento de químicos, ao norte do de itens. */
    private static final BlockPos CHEMICAL_STORAGE = STORAGE.north();
    private static final BlockPos MACHINE = new BlockPos(4, 1, 2);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final ResourceLocation HYDROGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen");
    private static final ResourceLocation OXYGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen");
    private static final String ACCEPTED = "ACCEPTED";

    private DockChemicalRs2GameTests() {
    }

    private static Block block(String namespace, String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /** A Doca e a máquina de químico, ainda sem vínculo. */
    private static PatternDockBlockEntity dock(GameTestHelper helper) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER,
                RouterTier.BASIC));
        ChemicalTestSupport.resetDockMachine(helper.absolutePos(MACHINE));
        helper.setBlock(MACHINE, ChemicalTestTanks.DOCK_MACHINE_BLOCK);
        return helper.getBlockEntity(DOCK);
    }

    private static void registered(PatternDockBlockEntity dock) {
        if (!NetworkManager.get().hasVisitor(dock.returns())) {
            throw new GameTestAssertException("a Doca ainda não carregou");
        }
    }

    private static void room() {
        if (!NetworkManager.get().budget().outsideRoom()) {
            throw new GameTestAssertException("sem orçamento neste tick");
        }
    }

    /**
     * Um pedido só de 1.000 mB de hidrogênio pelo destino do Autocrafter: a simulação e a execução aceitam, e o
     * hidrogênio entra inteiro na entrada da máquina, sem sobrar pendente.
     */
    @GameTest(template = "empty")
    public static void chemicalOnlyOrderReachesTheMachine(GameTestHelper helper) {
        if (!DockChemicalAe2GameTests.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        BlockPos machine = helper.absolutePos(MACHINE);
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> {
                    helper.assertTrue(Rs2Chemicals.LOADED, "a ponte da integração não a vê");
                    DockChemicalGameTests.linkMachine(helper, dock, MACHINE);
                })
                .thenWaitUntil(DockChemicalRs2GameTests::room)
                .thenExecute(() -> {
                    Object sink = Rs2TestSupport.sink(helper.getLevel(), helper.absolutePos(DOCK), Direction.NORTH);
                    Object hydrogen = Rs2TestSupport.chemicalResource(ChemicalTestSupport.chemical(HYDROGEN, 1));
                    String simulated = Rs2TestSupport.acceptResources(sink, true, hydrogen, 1_000);
                    helper.assertTrue(ACCEPTED.equals(simulated), "simulação: " + simulated + ", motivo "
                            + dock.side(NORTH).lastRefusal());
                    String executed = Rs2TestSupport.acceptResources(sink, false, hydrogen, 1_000);
                    helper.assertTrue(ACCEPTED.equals(executed), "execução: " + executed + ", motivo "
                            + dock.side(NORTH).lastRefusal());
                    long input = ChemicalTestSupport.dockMachineInput(machine, HYDROGEN);
                    helper.assertTrue(input == 1_000, "entrada: " + input);
                    helper.assertFalse(dock.side(NORTH).hasPending(), "ficou pendente");
                })
                .thenSucceed();
    }

    /**
     * A rede de verdade (Controlador Criativo, armazenamento de itens e de químicos, Autocrafter com o padrão): um
     * pedido de 1.000 mB de oxigênio. O Autocrafter entrega o minério e o hidrogênio à Doca, que os põe na máquina; o
     * oxigênio da saída volta à rede pela volta da Doca (insere antes, tira depois) e a tarefa termina.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void autocrafterChemicalOrderReturnsToNetwork(GameTestHelper helper) {
        if (!DockChemicalAe2GameTests.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        helper.setBlock(CONTROLLER, block("refinedstorage", "creative_controller"));
        helper.setBlock(STORAGE, block("refinedstorage", "1k_storage_block"));
        helper.setBlock(CHEMICAL_STORAGE, block("refinedstorage_mekanism_integration", "64b_chemical_storage_block"));
        helper.setBlock(AUTOCRAFTER, Rs2TestSupport.facing(block("refinedstorage", "autocrafter"), Direction.SOUTH));
        BlockPos autocrafter = helper.absolutePos(AUTOCRAFTER);
        BlockPos machine = helper.absolutePos(MACHINE);
        Object hydrogen = Rs2TestSupport.chemicalResource(ChemicalTestSupport.chemical(HYDROGEN, 1));
        Object oxygen = Rs2TestSupport.chemicalResource(ChemicalTestSupport.chemical(OXYGEN, 1));
        helper.assertTrue(Rs2TestSupport.installPattern(helper.getLevel(), autocrafter,
                Rs2TestSupport.itemAndChemicalPattern(Items.RAW_IRON, 1, hydrogen, 1_000, oxygen, 1_000)),
                "o padrão não entrou no Autocrafter");
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenExecute(() -> DockChemicalGameTests.linkMachine(helper, dock, MACHINE))
                .thenWaitUntil(() -> helper.assertTrue(Rs2TestSupport.knows(helper.getLevel(), autocrafter, oxygen),
                        "a rede não conhece o padrão"))
                .thenExecute(() -> {
                    helper.assertTrue(Rs2TestSupport.stock(helper.getLevel(), autocrafter, Items.RAW_IRON, 1) == 1,
                            "o minério não coube");
                    helper.assertTrue(Rs2TestSupport.stock(helper.getLevel(), autocrafter, hydrogen, 1_000) == 1_000,
                            "o hidrogênio não coube");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    String result = Rs2TestSupport.request(helper.getLevel(), autocrafter, oxygen, 1_000);
                    helper.assertTrue("TASK_CREATED".equals(result), "o pedido: " + result);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        ChemicalTestSupport.dockMachineInput(machine, HYDROGEN) == 1_000,
                        "hidrogênio na máquina: " + ChemicalTestSupport.dockMachineInput(machine, HYDROGEN)))
                .thenExecute(() -> {
                    ItemStack slot = ChemicalTestSupport.dockMachineItem(machine);
                    helper.assertTrue(slot.is(Items.RAW_IRON) && slot.getCount() == 1, "slot: " + slot);
                    helper.assertFalse(dock.side(NORTH).hasPending(), "ficou pendente");
                    helper.assertTrue(Rs2TestSupport.stored(helper.getLevel(), autocrafter, hydrogen) == 0,
                            "sobrou hidrogênio na rede");
                    // A receita pronta: a máquina de teste não tem block entity, então o aviso do setChanged dela
                    // (que acorda a volta) vai à mão.
                    ChemicalTestSupport.fillDockMachineOutput(machine, OXYGEN, 1_000);
                    helper.getLevel().blockEntityChanged(machine);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        Rs2TestSupport.stored(helper.getLevel(), autocrafter, oxygen) == 1_000,
                        "oxigênio na rede: " + Rs2TestSupport.stored(helper.getLevel(), autocrafter, oxygen)
                                + ", na saída " + ChemicalTestSupport.dockMachineOutput(machine) + ", devolvidos "
                                + dock.side(NORTH).returnedChemical() + ", provedor cheio "
                                + dock.side(NORTH).providerFull()))
                .thenExecute(() -> {
                    helper.assertTrue(ChemicalTestSupport.dockMachineOutput(machine) == 0, "sobrou na saída");
                    long returned = dock.side(NORTH).returnedChemical();
                    helper.assertTrue(returned == 1_000, "devolvidos: " + returned);
                })
                .thenWaitUntil(() -> helper.assertTrue(Rs2TestSupport.tasks(helper.getLevel(), autocrafter) == 0,
                        "a tarefa não terminou"))
                .thenSucceed();
    }

    /** A rede guarda só o que saiu da máquina ({@code returned} mB), e o resto dos 2.000 mB continua na saída. */
    private static void reclaimed(GameTestHelper helper, PatternDockBlockEntity dock, BlockPos autocrafter,
            BlockPos machine, Object oxygen, long returned) {
        long stored = Rs2TestSupport.stored(helper.getLevel(), autocrafter, oxygen);
        long output = ChemicalTestSupport.dockMachineOutput(machine);
        long counted = dock.side(NORTH).returnedChemical();
        helper.assertTrue(stored == returned && counted == returned && output == 2_000 - returned, "na rede "
                + stored + ", na saída " + output + ", devolvidos " + counted + " (esperado " + returned + ")");
    }

    /**
     * A saída da máquina oferece 2.000 mB de oxigênio na simulação e dá só 500 mB por extração. A volta insere antes
     * na rede (sem tarefa, o oxigênio vai ao armazenamento de químicos) o que a máquina ofereceu e tira de novo o que
     * ela não deu ({@code reclaimChemical}): a cada visita a rede guarda só o que saiu da máquina.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void stingyMachineChemicalReclaimedFromNetwork(GameTestHelper helper) {
        if (!DockChemicalAe2GameTests.enabled()) {
            helper.succeed();
            return;
        }
        PatternDockBlockEntity dock = dock(helper);
        helper.setBlock(CONTROLLER, block("refinedstorage", "creative_controller"));
        helper.setBlock(STORAGE, block("refinedstorage", "1k_storage_block"));
        helper.setBlock(CHEMICAL_STORAGE, block("refinedstorage_mekanism_integration", "64b_chemical_storage_block"));
        helper.setBlock(AUTOCRAFTER, Rs2TestSupport.facing(block("refinedstorage", "autocrafter"), Direction.SOUTH));
        BlockPos autocrafter = helper.absolutePos(AUTOCRAFTER);
        BlockPos machine = helper.absolutePos(MACHINE);
        ChemicalTestSupport.dockMachineStingy(machine, 500);
        Object oxygen = Rs2TestSupport.chemicalResource(ChemicalTestSupport.chemical(OXYGEN, 1));
        helper.startSequence()
                .thenWaitUntil(() -> registered(dock))
                .thenWaitUntil(() -> helper.assertTrue(Rs2TestSupport.stores(helper.getLevel(), autocrafter),
                        "a rede ainda não guarda"))
                .thenExecute(() -> {
                    DockChemicalGameTests.linkMachine(helper, dock, MACHINE);
                    ChemicalTestSupport.fillDockMachineOutput(machine, OXYGEN, 2_000);
                    // a máquina de teste não tem block entity: o aviso que acorda a volta vai à mão
                    helper.getLevel().blockEntityChanged(machine);
                })
                .thenWaitUntil(() -> helper.assertTrue(dock.side(NORTH).returnedChemical() > 0,
                        "nada voltou; provedor cheio " + dock.side(NORTH).providerFull()))
                .thenExecute(() -> {
                    reclaimed(helper, dock, autocrafter, machine, oxygen, 500);
                    // sem block entity, a máquina não avisa a extração: o aviso da sobra vai à mão
                    helper.getLevel().blockEntityChanged(machine);
                })
                .thenWaitUntil(() -> helper.assertTrue(dock.side(NORTH).returnedChemical() >= 1_000,
                        "a segunda visita não devolveu"))
                .thenExecute(() -> reclaimed(helper, dock, autocrafter, machine, oxygen, 1_000))
                .thenSucceed();
    }
}
