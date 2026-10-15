package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CasingPosition;
import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBatch;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorCasingBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorStatus;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Etapa 2 do Ressonador de Sinal: o lote do Núcleo, com a geração pela sintonia, o desgaste dos cristais e a
 * recarga com redstone. Cada teste roda o lote à mão dentro de um só {@code thenExecute}, para o lote do
 * gerenciador não cair no meio da conta. Também o clique no Núcleo, o Cartão de Upgrade, o drop ao quebrar e o
 * {@code /wa resonator}.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorCoreGameTests {
    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);

    private ResonatorCoreGameTests() {
    }

    @GameTest(template = "empty7")
    public static void emSintoniaGeraOMaximoDosCristais(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            core.setStationForTest(98.0);
            core.setDial(98.0);
            for (int i = 0; i < 3; i++) {
                assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            }
            ResonatorBatch.run(helper.getLevel(), core, 20);
            helper.assertValueEqual(core.energy(), 3L * 8_192 * 20, "energia de um lote");
        });
    }

    @GameTest(template = "empty7")
    public static void foraDaEstacaoNaoGeraNemGasta(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            core.setStationForTest(98.0);
            core.setDial(90.0);
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            ResonatorBatch.run(helper.getLevel(), core, 20);
            helper.assertValueEqual(core.energy(), 0L, "energia fora da estação");
            helper.assertValueEqual(SignalCrystalItem.charge(core.crystals().get(0)), CrystalCharge.FULL, "carga do cristal");
            helper.assertValueEqual(SignalCrystalItem.charge(core.crystals().get(1)), CrystalCharge.FULL, "carga do lapidado");
        });
    }

    @GameTest(template = "empty7")
    public static void lapidadoRendeODobro(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            tuned(core);
            assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            ResonatorBatch.run(helper.getLevel(), core, 20);
            helper.assertValueEqual(core.energy(), 128L * 2 * 20, "energia do lapidado");
        });
    }

    @GameTest(template = "empty7")
    public static void bufferCheioParaAGeracao(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            tuned(core);
            assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            for (int i = 0; i < 100 && core.energy() < core.capacity(); i++) {
                ResonatorBatch.run(helper.getLevel(), core, 20);
            }
            helper.assertValueEqual(core.energy(), core.capacity(), "buffer cheio");
            int before = SignalCrystalItem.charge(core.crystals().get(0));
            ResonatorBatch.run(helper.getLevel(), core, 20);
            helper.assertValueEqual(SignalCrystalItem.charge(core.crystals().get(0)), before,
                    "a carga não muda com o buffer cheio");
            helper.assertValueEqual(core.energy(), core.capacity(), "energia não passa da capacidade");
        });
    }

    @GameTest(template = "empty7", batch = "wirelessautomate_config")
    public static void cristalGastaNaVidaDaConfig(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            long before = Config.CRYSTAL_LIFE_TICKS.get();
            try {
                Config.CRYSTAL_LIFE_TICKS.set(40L);
                tuned(core);
                assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
                ResonatorBatch.run(helper.getLevel(), core, 20);
                helper.assertValueEqual(SignalCrystalItem.charge(core.crystals().get(0)), CrystalCharge.FULL / 2,
                        "meia vida depois de um lote");
                ResonatorBatch.run(helper.getLevel(), core, 20);
                helper.assertTrue(SignalCrystalItem.isDull(core.crystals().get(0)), "o cristal fica Opaco");
            } finally {
                Config.CRYSTAL_LIFE_TICKS.set(before);
            }
        });
    }

    @GameTest(template = "empty7", batch = "wirelessautomate_config")
    public static void redstoneRecarregaOOpaco(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            long before = Config.RECHARGE_TICKS.get();
            try {
                Config.RECHARGE_TICKS.set(40L);
                assertInserted(helper, core, crystal(false, 0));
                helper.assertValueEqual(core.addRedstone(3), 3, "redstone que entrou");
                ResonatorBatch.run(helper.getLevel(), core, 20);
                helper.assertTrue(SignalCrystalItem.isDull(core.crystals().get(0)), "ainda Opaco no meio da recarga");
                ResonatorBatch.run(helper.getLevel(), core, 20);
                helper.assertTrue(SignalCrystalItem.isFull(core.crystals().get(0)), "o cristal fica cheio");
                helper.assertValueEqual(core.redstone(), 2, "sobra de redstone");
            } finally {
                Config.RECHARGE_TICKS.set(before);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void semRedstoneOOpacoFica(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            assertInserted(helper, core, crystal(false, 0));
            for (int i = 0; i < 60; i++) {
                ResonatorBatch.run(helper.getLevel(), core, 20);
            }
            helper.assertTrue(SignalCrystalItem.isDull(core.crystals().get(0)), "o cristal continua Opaco");
            helper.assertValueEqual(core.redstone(), 0, "sem redstone");
        });
    }

    @GameTest(template = "empty7")
    public static void redstoneTemTetoDe64(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            helper.assertValueEqual(core.addRedstone(50), 50, "a primeira leva entra inteira");
            helper.assertValueEqual(core.addRedstone(50), 14, "só até 64");
            helper.assertValueEqual(core.redstone(), 64, "redstone no teto");
        });
    }

    @GameTest(template = "empty7")
    public static void camarasPeloTier(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            ItemStack sobra = core.insertCrystal(crystal(false, CrystalCharge.FULL));
            helper.assertValueEqual(sobra.getCount(), 1, "o Básico só tem uma câmara");
            ItemStack outro = core.insertCrystal(new ItemStack(ModItems.RESONATOR_CRYSTAL_PORT.get()));
            helper.assertValueEqual(outro.getCount(), 1, "só cristais entram");
            ItemStack tirado = core.takeLastCrystal();
            helper.assertTrue(tirado.is(ModItems.SIGNAL_CRYSTAL.get()), "tira o cristal");
            helper.assertTrue(core.crystals().get(0).isEmpty(), "a câmara fica vazia");
        });
    }

    /**
     * Com espaço para só um décimo do lote, o cristal gasta um décimo dos ticks (2 de 20): o desgaste acompanha o
     * que foi gerado, não o lote inteiro.
     */
    @GameTest(template = "empty7")
    public static void bufferQuaseCheioGastaSoOProporcional(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            tuned(core);
            assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            for (int i = 0; i < 100 && core.energy() < core.capacity(); i++) {
                ResonatorBatch.run(helper.getLevel(), core, 20);
            }
            long batch = core.generationPerTick() * 20;
            core.extractEnergy(batch / 10, false);
            int before = SignalCrystalItem.charge(core.crystals().get(0));
            ResonatorBatch.run(helper.getLevel(), core, 20);
            helper.assertValueEqual(core.energy(), core.capacity(), "o buffer encheu de novo");
            helper.assertValueEqual(SignalCrystalItem.charge(core.crystals().get(0)),
                    CrystalCharge.drain(before, 2, Config.crystalLifeTicks(true)), "gasto de 2 ticks");
        });
    }

    @GameTest(template = "empty7")
    public static void extrairEnergia(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            tuned(core);
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            ResonatorBatch.run(helper.getLevel(), core, 20);
            helper.assertValueEqual(core.extractEnergy(1_000L, true), 1_000L, "simulado");
            helper.assertValueEqual(core.energy(), 2_560L, "a simulação não tira");
            helper.assertValueEqual(core.extractEnergy(5_000L, false), 2_560L, "só o que tem");
            helper.assertValueEqual(core.energy(), 0L, "vazio");
        });
    }

    @GameTest(template = "empty7")
    public static void gerenciadorRodaOsLotes(GameTestHelper helper) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true)).thenExecute(() -> {
            ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
            tuned(core);
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
        }).thenWaitUntil(() -> helper.assertTrue(ResonatorTestSupport.core(helper, corePos).energy() > 0,
                "o gerenciador ainda não rodou o lote")).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void desmontadoNaoGera(GameTestHelper helper) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(ORIGIN.offset(2, 0, 0)), false))
                .thenWaitUntil(() -> assertFormed(helper, corePos, false)).thenExecute(() -> {
                    ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
                    tuned(core);
                    assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
                    long before = core.energy();
                    ResonatorBatch.run(helper.getLevel(), core, 20);
                    helper.assertValueEqual(core.energy(), before, "desmontado não gera");
                    helper.assertValueEqual(SignalCrystalItem.charge(core.crystals().get(0)), CrystalCharge.FULL,
                            "desmontado não gasta");
                }).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void cliqueComCristalPoeNoNucleo(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = survivor(helper);
            ItemStack hand = crystal(false, CrystalCharge.FULL).copyWithCount(3);
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            core.getBlockState().useItemOn(hand, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    hit(core.getBlockPos()));
            helper.assertTrue(core.crystals().get(0).is(ModItems.SIGNAL_CRYSTAL.get()), "o cristal não entrou");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "cristais na mão");
        });
    }

    @GameTest(template = "empty7")
    public static void cliqueComRedstonePoeNoNucleo(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = survivor(helper);
            ItemStack hand = new ItemStack(Items.REDSTONE, 70);
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            core.getBlockState().useItemOn(hand, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    hit(core.getBlockPos()));
            helper.assertValueEqual(core.redstone(), ResonanceCoreBlockEntity.MAX_REDSTONE, "redstone no Núcleo");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 6, "sobra na mão");
        });
    }

    @GameTest(template = "empty7")
    public static void shiftCliqueTiraOUltimoCristal(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            ServerPlayer player = survivor(helper);
            player.setShiftKeyDown(true);
            core.getBlockState().useWithoutItem(helper.getLevel(), player, hit(core.getBlockPos()));
            helper.assertTrue(player.getMainHandItem().is(ModItems.CUT_SIGNAL_CRYSTAL.get()),
                    "o último cristal não veio para a mão: " + player.getMainHandItem());
            helper.assertTrue(core.crystals().get(1).isEmpty(), "a última câmara não esvaziou");
            helper.assertTrue(core.crystals().get(0).is(ModItems.SIGNAL_CRYSTAL.get()), "a primeira câmara mudou");
        });
    }

    @GameTest(template = "empty7")
    public static void statusMostraOProblema(GameTestHelper helper) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.VITRINE, RouterTier.BASIC);
        helper.setBlock(ORIGIN.offset(2, 2, 0), ModBlocks.RESONATOR_CASING.get());
        helper.startSequence().thenWaitUntil(() -> {
            ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
            helper.assertTrue(core != null && core.lastProblem() != null, "ainda não revalidou");
        }).thenExecute(() -> {
            Component line = ResonatorStatus.line(ResonatorTestSupport.core(helper, corePos));
            helper.assertTrue(line.getContents() instanceof TranslatableContents contents && contents.getKey()
                    .equals("block.wirelessautomate.resonance_core.problem.missing_crystal_port"),
                    "linha do status: " + line);
        }).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void cartaoSobeOTierEAumentaAsCamaras(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = survivor(helper);
            ItemStack card = TierCoreItem.card(RouterTier.ADVANCED, RouterTier.BASIC);
            BlockPos pos = core.getBlockPos();
            InteractionResult result = useCard(player, card, pos);
            helper.assertTrue(result.consumesAction(), "o cartão não subiu o Núcleo: " + result);
            BlockState state = helper.getLevel().getBlockState(pos);
            helper.assertTrue(state.getValue(RouterBlock.TIER) == RouterTier.ADVANCED, "tier: " + state);
            helper.assertTrue(helper.getLevel().getBlockEntity(pos) == core, "o block entity mudou");
            helper.assertTrue(core.formed(), "a estrutura desmontou");
            helper.assertValueEqual(core.chambers(), 2, "câmaras do Avançado");
            helper.assertValueEqual(core.capacity(),
                    CoreMath.buffer(2, Config.resonatorFePerCrystal(RouterTier.ADVANCED)), "capacidade do Avançado");
            helper.assertTrue(card.isEmpty(), "o cartão não foi gasto");
        });
    }

    @GameTest(template = "empty7")
    public static void cartaoErradoRecusa(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = survivor(helper);
            ItemStack card = TierCoreItem.card(RouterTier.ELITE, RouterTier.ADVANCED);
            InteractionResult result = useCard(player, card, core.getBlockPos());
            helper.assertTrue(result == InteractionResult.FAIL, "resultado: " + result);
            helper.assertTrue(core.tier() == RouterTier.BASIC, "o tier mudou");
            helper.assertValueEqual(card.getCount(), 1, "o cartão recusado foi gasto");
        });
    }

    @GameTest(template = "empty")
    public static void quebrarNucleoDerrubaOsCristais(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModBlocks.RESONANCE_CORE.get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, relative);
        assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
        assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
        helper.assertValueEqual(core.addRedstone(5), 5, "redstone que entrou");
        ServerPlayer player = survivor(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos pos = helper.absolutePos(relative);
        helper.assertTrue(player.gameMode.destroyBlock(pos), "não quebrou o Núcleo");
        int[] counts = dropped(helper, pos);
        helper.assertValueEqual(counts[0], 2, "cristais no chão");
        helper.assertValueEqual(counts[1], 5, "redstone no chão");
        helper.assertTrue(counts[2] == RouterTier.ADVANCED.ordinal(), "o Núcleo Avançado não caiu");
        helper.succeed();
    }

    @GameTest(template = "empty7")
    public static void quebrarNucleoMontadoDerrubaEDesmonta(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            helper.assertValueEqual(core.addRedstone(3), 3, "redstone que entrou");
            ServerPlayer player = survivor(helper);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            BlockPos pos = core.getBlockPos();
            helper.assertTrue(player.gameMode.destroyBlock(pos), "não quebrou o Núcleo");
            int[] counts = dropped(helper, pos);
            helper.assertValueEqual(counts[0], 1, "cristais no chão");
            helper.assertValueEqual(counts[1], 3, "redstone no chão");
            helper.assertTrue(core.isRemoved(), "o block entity ficou");
            BlockState casing = helper.getBlockState(ORIGIN);
            helper.assertTrue(casing.getValue(ResonatorCasingBlock.POSITION) == CasingPosition.LOOSE,
                    "a carcaça continua montada: " + casing);
            BlockState port = helper.getBlockState(ORIGIN.offset(2, 2, 0));
            helper.assertTrue(!port.getValue(ResonatorPartBlock.FORMED), "a Porta continua montada: " + port);
        });
    }

    @GameTest(template = "empty7")
    public static void criativoNaoGastaCristalNemRedstone(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.CREATIVE);
            ItemStack crystals = crystal(false, CrystalCharge.FULL).copyWithCount(3);
            player.setItemInHand(InteractionHand.MAIN_HAND, crystals);
            core.getBlockState().useItemOn(crystals, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    hit(core.getBlockPos()));
            helper.assertTrue(core.crystals().get(0).is(ModItems.SIGNAL_CRYSTAL.get()), "o cristal não entrou");
            helper.assertValueEqual(crystals.getCount(), 3, "cristais na mão do criativo");
            ItemStack redstone = new ItemStack(Items.REDSTONE, 10);
            player.setItemInHand(InteractionHand.MAIN_HAND, redstone);
            core.getBlockState().useItemOn(redstone, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    hit(core.getBlockPos()));
            helper.assertValueEqual(core.redstone(), 10, "redstone no Núcleo");
            helper.assertValueEqual(redstone.getCount(), 10, "redstone na mão do criativo");
        });
    }

    @GameTest(template = "empty")
    public static void comandoDialMudaODial(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ModBlocks.RESONANCE_CORE.get());
        BlockPos pos = helper.absolutePos(relative);
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack().withPermission(2)
                .withLevel(helper.getLevel()).withSuppressedOutput();
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source,
                "wa resonator " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " dial 97.42");
        double dial = ResonatorTestSupport.core(helper, relative).dial();
        helper.assertTrue(Math.abs(dial - 97.40) < 1e-9, "dial: " + dial);
        helper.succeed();
    }

    /** Os itens no chão em volta de {@code absolute}: cristais, redstone e o ordinal do tier do Núcleo (-1 sem ele). */
    private static int[] dropped(GameTestHelper helper, BlockPos absolute) {
        int[] counts = {0, 0, -1};
        for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2))) {
            ItemStack stack = drop.getItem();
            if (stack.getItem() instanceof SignalCrystalItem) {
                counts[0] += stack.getCount();
            } else if (stack.is(Items.REDSTONE)) {
                counts[1] += stack.getCount();
            } else if (stack.is(ModItems.RESONANCE_CORE.get())) {
                counts[2] = ResonanceCoreItem.tierOf(stack).ordinal();
            }
        }
        return counts;
    }

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static BlockHitResult hit(BlockPos absolute) {
        return new BlockHitResult(Vec3.atCenterOf(absolute), Direction.EAST, absolute, false);
    }

    /** Jogador com {@code card} na mão usa o cartão no Núcleo em {@code absolute}. */
    private static InteractionResult useCard(ServerPlayer player, ItemStack card, BlockPos absolute) {
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        return card.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(absolute)));
    }

    /** Monta a Vitrine no tier, espera montar e roda {@code body} num só tick. */
    private static void formed(GameTestHelper helper, RouterTier tier, Consumer<ResonanceCoreBlockEntity> body) {
        BlockPos corePos = ResonatorTestSupport.build(helper, ORIGIN, Layout.VITRINE, tier);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenExecute(() -> body.accept(ResonatorTestSupport.core(helper, corePos))).thenSucceed();
    }

    private static void tuned(ResonanceCoreBlockEntity core) {
        core.setStationForTest(98.0);
        core.setDial(98.0);
    }

    private static ItemStack crystal(boolean cut, int charge) {
        ItemStack stack = new ItemStack(cut ? ModItems.CUT_SIGNAL_CRYSTAL.get() : ModItems.SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(stack, charge);
        return stack;
    }

    private static void assertInserted(GameTestHelper helper, ResonanceCoreBlockEntity core, ItemStack stack) {
        helper.assertTrue(core.insertCrystal(stack).isEmpty(), "o cristal não entrou");
    }

    private static void assertFormed(GameTestHelper helper, BlockPos corePos, boolean formed) {
        ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
        helper.assertTrue(core != null && core.formed() == formed, formed ? "não montou" : "não desmontou");
    }
}
