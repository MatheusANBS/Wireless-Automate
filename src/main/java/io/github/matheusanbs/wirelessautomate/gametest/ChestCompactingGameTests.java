package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChain;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChains;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** O índice das cadeias de compactação montado das receitas de mesa. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ChestCompactingGameTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void indiceAchaPepitaLingoteBloco(GameTestHelper helper) {
        CompactingChain chain = CompactingChains.get().chainOf(Items.IRON_INGOT);
        helper.assertTrue(chain != null, "o lingote de ferro tem cadeia");
        helper.assertTrue(chain.levelOf(Items.IRON_NUGGET) == 0, "pepita na base");
        helper.assertTrue(chain.levelOf(Items.IRON_INGOT) == 1, "lingote no nível 1");
        helper.assertTrue(chain.levelOf(Items.IRON_BLOCK) == 2, "bloco no nível 2");
        helper.assertTrue(chain.factors()[1] == 9 && chain.factors()[2] == 9, "fatores 9");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void indiceAchaCruEBlocoCru(GameTestHelper helper) {
        CompactingChain chain = CompactingChains.get().chainOf(Items.RAW_IRON);
        helper.assertTrue(chain != null && chain.levelOf(Items.RAW_IRON_BLOCK) == 1, "cru e bloco cru");
        helper.assertTrue(chain.levelOf(Items.IRON_INGOT) < 0, "cru não vira lingote (fundir fica de fora)");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void indiceIgnoraOQueNaoVolta(GameTestHelper helper) {
        helper.assertTrue(CompactingChains.get().chainOf(Items.WHITE_WOOL) == null, "lã não volta");
        helper.assertTrue(CompactingChains.get().chainOf(Items.SANDSTONE) == null, "arenito não volta");
        helper.assertTrue(CompactingChains.get().chainOf(Items.SNOW_BLOCK) == null, "neve não volta");
        helper.succeed();
    }
}
