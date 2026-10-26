package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlockEntity;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** O Reparador de Cristais: o bloco, os slots e o NBT. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RepairerGameTests {
    static final BlockPos A = new BlockPos(1, 1, 1);

    private RepairerGameTests() {
    }

    /** Põe o Reparador e espera o `onLoad`, que só roda no tick seguinte. */
    static void repairer(GameTestHelper helper, java.util.function.Consumer<RepairerBlockEntity> body) {
        helper.setBlock(A, ModBlocks.CRYSTAL_REPAIRER.get().defaultBlockState());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getBlockEntity(A) instanceof RepairerBlockEntity, "sem block entity"))
                .thenExecute(() -> body.accept((RepairerBlockEntity) helper.getBlockEntity(A)))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void temDezesseteSlotsVazios(GameTestHelper helper) {
        repairer(helper, repairer -> {
            helper.assertValueEqual(repairer.items().size(), RepairerSlots.TOTAL, "slots");
            for (int i = 0; i < RepairerSlots.TOTAL; i++) {
                helper.assertTrue(repairer.items().get(i).isEmpty(), "o slot " + i + " começa vazio");
            }
        });
    }
}
