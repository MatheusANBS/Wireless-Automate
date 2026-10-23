package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.ORIGIN;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertFormed;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.assertInserted;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.build;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.core;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.crystal;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.formed;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBatch;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorFormation;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Etapa 3 do Ressonador de Sinal: a câmara que vai ao cliente pelo update tag ({@link ChamberView}), mandada só
 * quando muda, e a carga do cristal como barra do item. Tudo roda num só {@code thenExecute}, para o lote do
 * gerenciador não cair no meio da contagem.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorSyncGameTests {
    private ResonatorSyncGameTests() {
    }

    @GameTest(template = "empty7")
    public static void updateTagTemAsCamarasEAFaixa(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            core.setStationForTest(98.0);
            core.setDial(98.0);
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            assertInserted(helper, core, crystal(true, 0));
            ChamberView view = tagView(helper, core);
            helper.assertTrue(Arrays.equals(view.chambers(), new byte[] {1, 2, 4, 0}),
                    "câmaras erradas: " + Arrays.toString(view.chambers()));
            helper.assertTrue(view.band() == 7, "faixa " + view.band() + ", esperava 7");
            helper.assertTrue(view.tuned(), "a lâmpada não acendeu em sintonia");
            helper.assertTrue(view.formedAt() >= 0, "sem o tempo da montagem");
            BlockPos origin = helper.absolutePos(ORIGIN);
            helper.assertTrue(Arrays.equals(view.origin(), new int[] {origin.getX(), origin.getY(), origin.getZ()}),
                    "origem errada: " + Arrays.toString(view.origin()));
            helper.assertTrue(view.rechargeStart() == -1, "recarga sem redstone");
            core.addRedstone(3);
            ChamberView recharging = tagView(helper, core);
            helper.assertTrue(recharging.rechargeStart() == helper.getLevel().getGameTime(),
                    "a recarga não começou agora: " + recharging.rechargeStart());
        });
    }

    @GameTest(template = "empty7")
    public static void viewVaiEVoltaDoNbt(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            assertInserted(helper, core, crystal(false, 0));
            core.addRedstone(1);
            ChamberView view = ChamberView.of(core, 100);
            CompoundTag tag = new CompoundTag();
            view.save(tag);
            helper.assertTrue(view.equals(ChamberView.load(tag)), "a view não voltou igual do NBT");
            helper.assertTrue(ChamberView.load(new CompoundTag()).equals(ChamberView.EMPTY), "sem câmaras não é EMPTY");
            view.chambers()[0] = 9;
            view.origin()[0] = 9;
            ChamberView.EMPTY.origin()[0] = 9;
            helper.assertTrue(view.equals(ChamberView.load(tag)), "a view mudou pelo array devolvido");
            helper.assertTrue(ChamberView.EMPTY.origin()[0] == 0, "o EMPTY mudou pelo array devolvido");
            helper.assertTrue(ChamberView.EMPTY.rechargeTicks() > 0, "o EMPTY tem recarga de 0 ticks");
        });
    }

    @GameTest(template = "empty7")
    public static void viewTemOTierDoAfinado(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            ChamberView plain = ChamberView.of(core, -1);
            CompoundTag plainTag = new CompoundTag();
            plain.save(plainTag);
            helper.assertTrue(!plainTag.contains("tiers"), "a chave tiers foi sem nenhum Afinado");
            helper.assertTrue(plain.tierAt(0) == 0 && plain.tierAt(7) == 0, "tier sem Afinado");
            assertInserted(helper, core, new ItemStack(ModItems.TUNED_CRYSTALS.get(RouterTier.ELITE).get()));
            ChamberView view = ChamberView.of(core, -1);
            helper.assertTrue(view.tierAt(0) == 0, "o Cristal de Sinal ganhou tier " + view.tierAt(0));
            helper.assertTrue(view.tierAt(1) == RouterTier.ELITE.ordinal() + 1,
                    "tier do Afinado " + view.tierAt(1) + ", esperava " + (RouterTier.ELITE.ordinal() + 1));
            helper.assertTrue(!view.equals(plain), "a view com Afinado é igual à sem");
            CompoundTag tag = new CompoundTag();
            view.save(tag);
            helper.assertTrue(view.equals(ChamberView.load(tag)), "a view com tier não voltou igual do NBT");
            helper.assertTrue(view.equals(ChamberView.of(core, -1)), "a view do Núcleo agora não confere");
            view.tiers()[1] = 9;
            helper.assertTrue(view.tierAt(1) == RouterTier.ELITE.ordinal() + 1, "o tier mudou pelo array devolvido");
        });
    }

    @GameTest(template = "empty7")
    public static void montagemMandaUmaUpdateTag(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            ResonatorFormation.disassemble(helper.getLevel(), core);
            helper.assertTrue(!core.formed(), "não desmontou");
            int before = core.syncCount();
            ResonatorFormation.revalidate(helper.getLevel(), core);
            helper.assertTrue(core.formed(), "não montou de novo");
            helper.assertTrue(core.syncCount() == before + 1,
                    "a montagem mandou " + (core.syncCount() - before) + " update tags, esperava 1");
            BlockPos origin = helper.absolutePos(ORIGIN);
            int[] sent = tagView(helper, core).origin();
            helper.assertTrue(Arrays.equals(sent, new int[] {origin.getX(), origin.getY(), origin.getZ()}),
                    "origem errada: " + Arrays.toString(sent));
        });
    }

    @GameTest(template = "empty7")
    public static void loteSemMudancaNaoManda(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            core.setStationForTest(98.0);
            core.setDial(98.0);
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            assertInserted(helper, core, crystal(true, CrystalCharge.FULL));
            core.syncIfChanged();
            int before = core.syncCount();
            for (int i = 0; i < 5; i++) {
                ResonatorBatch.run(helper.getLevel(), core, 20);
            }
            helper.assertTrue(core.energy() > 0, "o lote não gerou");
            helper.assertTrue(core.syncCount() == before, "lote sem mudança mandou o update tag");
        });
    }

    @GameTest(template = "empty7")
    public static void mudarDeFaixaManda(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            core.setStationForTest(98.0);
            core.setDial(98.0);
            int before = core.syncCount();
            core.setDial(97.0);
            helper.assertTrue(core.syncCount() == before + 1, "a faixa mudou e não mandou");
            core.setDial(97.05);
            helper.assertTrue(core.syncCount() == before + 1, "mandou sem mudar de faixa nem de lâmpada");
        });
    }

    @GameTest(template = "empty7")
    public static void lampadaManda(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            core.setStationForTest(98.0);
            core.setDial(97.7);
            int before = core.syncCount();
            core.setDial(97.8);
            helper.assertTrue(core.syncCount() == before + 1, "a lâmpada acendeu e não mandou");
        });
    }

    @GameTest(template = "empty7")
    public static void cristalNovoManda(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            int before = core.syncCount();
            assertInserted(helper, core, crystal(false, CrystalCharge.FULL));
            helper.assertTrue(core.syncCount() == before + 1, "o cristal novo não mandou");
            helper.assertTrue(!core.takeLastCrystal().isEmpty(), "não tirou o cristal");
            helper.assertTrue(core.syncCount() == before + 2, "tirar o cristal não mandou");
            assertInserted(helper, core, crystal(false, 0));
            int dull = core.syncCount();
            helper.assertTrue(core.addRedstone(3) == 3, "a redstone não entrou");
            helper.assertTrue(core.syncCount() == dull + 1, "a recarga começou e não mandou");
            helper.assertTrue(core.addRedstone(2) == 2, "a redstone não entrou de novo");
            helper.assertTrue(core.syncCount() == dull + 1, "a mesma recarga mandou de novo");
        });
    }

    /**
     * O progresso da recarga só anda no lote: entre lotes, com a recarga em curso, um dial que não muda faixa nem
     * lâmpada não manda nada e o início da recarga fica o mesmo, enquanto o progresso anda. A espera passa por pelo
     * menos um lote do gerenciador.
     */
    @GameTest(template = "empty7")
    public static void recargaEstavelEntreLotes(GameTestHelper helper) {
        BlockPos corePos = build(helper, ORIGIN, Layout.VITRINE, RouterTier.EMERALD);
        int[] count = new int[1];
        long[] start = new long[1];
        int[] progress = new int[1];
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true)).thenExecute(() -> {
            ResonanceCoreBlockEntity core = core(helper, corePos);
            core.setStationForTest(98.0);
            core.setDial(98.0);
            assertInserted(helper, core, crystal(false, 0));
            core.addRedstone(3);
            count[0] = core.syncCount();
            start[0] = tagView(helper, core).rechargeStart();
            progress[0] = core.rechargeProgress();
            helper.assertTrue(start[0] >= 0, "a recarga não começou");
        }).thenIdle(25).thenExecute(() -> {
            ResonanceCoreBlockEntity core = core(helper, corePos);
            core.setDial(98.05);
            helper.assertTrue(core.syncCount() == count[0], "a recarga em curso mandou o update tag sem mudança");
            long now = tagView(helper, core).rechargeStart();
            helper.assertTrue(now == start[0], "o início da recarga andou: " + start[0] + " → " + now);
            helper.assertTrue(core.rechargeProgress() > progress[0],
                    "a recarga não andou entre os lotes: " + progress[0] + " → " + core.rechargeProgress());
        }).thenSucceed();
    }

    /**
     * O update tag pedido várias vezes, em ticks diferentes (como quando o chunk vai a jogadores novos), não muda o
     * início guardado da recarga nem manda nada: só o lote e as mudanças do Núcleo o atualizam.
     */
    @GameTest(template = "empty7")
    public static void updateTagRepetidoNaoMudaARecarga(GameTestHelper helper) {
        BlockPos corePos = build(helper, ORIGIN, Layout.VITRINE, RouterTier.EMERALD);
        int[] count = new int[1];
        long[] start = new long[1];
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true)).thenExecute(() -> {
            ResonanceCoreBlockEntity core = core(helper, corePos);
            assertInserted(helper, core, crystal(false, 0));
            core.addRedstone(3);
            count[0] = core.syncCount();
            start[0] = core.syncedRechargeStart();
            helper.assertTrue(start[0] >= 0, "a recarga não começou");
            for (int i = 0; i < 5; i++) {
                assertTagKeepsRecharge(helper, core, count[0], start[0]);
            }
        }).thenIdle(3).thenExecute(() -> {
            ResonanceCoreBlockEntity core = core(helper, corePos);
            for (int i = 0; i < 5; i++) {
                assertTagKeepsRecharge(helper, core, count[0], start[0]);
            }
        }).thenSucceed();
    }

    private static void assertTagKeepsRecharge(GameTestHelper helper, ResonanceCoreBlockEntity core, int count,
            long start) {
        long sent = tagView(helper, core).rechargeStart();
        helper.assertTrue(sent == start, "o update tag levou outro início: " + start + " → " + sent);
        helper.assertTrue(core.syncedRechargeStart() == start,
                "o update tag mudou o início guardado: " + start + " → " + core.syncedRechargeStart());
        helper.assertTrue(core.syncCount() == count, "o update tag mandou a câmara");
    }

    /** O clarão só na transição entre duas views recebidas; a primeira, já sintonizada, não marca. */
    @GameTest(template = "empty7")
    public static void claraoSoNaTransicao(GameTestHelper helper) {
        formed(helper, RouterTier.EMERALD, core -> {
            var registries = helper.getLevel().registryAccess();
            core.setStationForTest(98.0);
            core.setDial(98.0);
            core.handleUpdateTag(core.getUpdateTag(registries), registries);
            helper.assertTrue(core.clientView().tuned() && core.tunedAt() == -1, "a chegada já sintonizada marcou");
            core.setDial(96.0);
            core.handleUpdateTag(core.getUpdateTag(registries), registries);
            core.setDial(98.0);
            core.handleUpdateTag(core.getUpdateTag(registries), registries);
            helper.assertTrue(core.tunedAt() == helper.getLevel().getGameTime(), "a lâmpada acendeu e não marcou");
        });
    }

    @GameTest(template = "empty")
    public static void barraDoCristal(GameTestHelper helper) {
        ItemStack full = crystal(false, CrystalCharge.FULL);
        ItemStack dull = crystal(true, 0);
        ItemStack half = crystal(false, CrystalCharge.FULL / 2);
        helper.assertTrue(!full.isBarVisible(), "o cristal cheio tem barra");
        helper.assertTrue(!dull.isBarVisible(), "o Opaco tem barra");
        helper.assertTrue(half.isBarVisible(), "o cristal pela metade não tem barra");
        helper.assertTrue(half.getBarWidth() == 7, "largura " + half.getBarWidth() + ", esperava 7");
        helper.assertTrue(half.getBarColor() == 0xF0603E, "cor errada da barra");
        helper.assertTrue(new ItemStack(ModItems.SIGNAL_CRYSTAL.get()).getBarWidth() == 13, "cheio não é 13");
        helper.succeed();
    }

    private static ChamberView tagView(GameTestHelper helper, ResonanceCoreBlockEntity core) {
        return ChamberView.load(core.getUpdateTag(helper.getLevel().registryAccess()));
    }
}
