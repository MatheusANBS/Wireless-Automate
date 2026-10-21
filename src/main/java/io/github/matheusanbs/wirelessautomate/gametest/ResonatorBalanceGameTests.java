package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.ConfigReloadListener;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * As migrações 3 e 4 do balanceamento do Ressonador (a escada nova do FE por cristal e da energia do roteador, e o
 * Unobtainium de 184 para 10,7 milhões por cristal) e as chaves novas da config. Os dois testes de migração gravam a config e esperam a recarga do arquivo, como os de
 * {@link RouterGameTests}, por isso o prazo longo.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorBalanceGameTests {
    private static final long[] BALANCE2_FE = {128, 1_024, 8_192, 32_768, 262_144, 2_097_152, 134_217_728L, 1_073_741_824L};
    private static final long[] BALANCE2_ENERGY = {1_000, 8_000, 64_000, 512_000, 4_096_000, 32_768_000, 2_097_152_000L, 0};

    @GameTest(template = "empty", timeoutTicks = RouterGameTests.RELOAD_TIMEOUT,
            batch = "wirelessautomate_config_balance3")
    public static void migracao3TrocaOPadraoAntigo(GameTestHelper helper) {
        int reloadsBefore = ConfigReloadListener.fileReloads();
        try {
            for (RouterTier tier : RouterTier.values()) {
                Config.RESONATOR_FE_PER_CRYSTAL.get(tier).set(BALANCE2_FE[tier.ordinal()]);
                Config.TIERS.get(tier).rates().get(ResourceType.ENERGY.rateKey()).set(BALANCE2_ENERGY[tier.ordinal()]);
            }
            Config.BALANCE_VERSION.set(2);
            helper.assertValueEqual(Config.migrateBalance(), 13, "valores migrados");
            for (RouterTier tier : RouterTier.values()) {
                int i = tier.ordinal();
                helper.assertValueEqual(Config.RESONATOR_FE_PER_CRYSTAL.get(tier).get(), CoreMath.defaultFePerCrystal(i),
                        "FE por cristal, " + tier);
                helper.assertValueEqual(Config.TIERS.get(tier).rates().get(ResourceType.ENERGY.rateKey()).get(),
                        ResourceType.ENERGY.defaultRate(i), "energia do roteador, " + tier);
            }
            helper.assertValueEqual(Config.BALANCE_VERSION.get(), Config.CURRENT_BALANCE, "versão");
        } finally {
            restore();
        }
        RouterGameTests.succeedAfterReload(helper, reloadsBefore);
    }

    @GameTest(template = "empty", timeoutTicks = RouterGameTests.RELOAD_TIMEOUT,
            batch = "wirelessautomate_config_balance3_dono")
    public static void migracao3MantemOQueODonoMudou(GameTestHelper helper) {
        int reloadsBefore = ConfigReloadListener.fileReloads();
        var eliteCrystal = Config.RESONATOR_FE_PER_CRYSTAL.get(RouterTier.ELITE);
        var basicEnergy = Config.TIERS.get(RouterTier.BASIC).rates().get(ResourceType.ENERGY.rateKey());
        try {
            eliteCrystal.set(12_345L);
            basicEnergy.set(777L);
            Config.BALANCE_VERSION.set(2);
            Config.migrateBalance();
            helper.assertValueEqual(eliteCrystal.get(), 12_345L, "FE por cristal do dono");
            helper.assertValueEqual(basicEnergy.get(), 777L, "energia do dono");
            helper.assertValueEqual(Config.BALANCE_VERSION.get(), Config.CURRENT_BALANCE, "versão");
        } finally {
            restore();
        }
        RouterGameTests.succeedAfterReload(helper, reloadsBefore);
    }

    @GameTest(template = "empty", timeoutTicks = RouterGameTests.RELOAD_TIMEOUT,
            batch = "wirelessautomate_config_balance4")
    public static void migracao4TrocaOUnobtainiumAntigo(GameTestHelper helper) {
        int reloadsBefore = ConfigReloadListener.fileReloads();
        var unobtainium = Config.RESONATOR_FE_PER_CRYSTAL.get(RouterTier.UNOBTAINIUM);
        var elite = Config.RESONATOR_FE_PER_CRYSTAL.get(RouterTier.ELITE);
        try {
            unobtainium.set(184_000_000L);
            elite.set(12_345L);
            Config.BALANCE_VERSION.set(3);
            helper.assertValueEqual(Config.migrateBalance(), 1, "valores migrados");
            helper.assertValueEqual(unobtainium.get(), 10_700_000L, "FE por cristal, Unobtainium");
            helper.assertValueEqual(elite.get(), 12_345L, "FE por cristal do dono");
            helper.assertValueEqual(Config.BALANCE_VERSION.get(), Config.CURRENT_BALANCE, "versão");
        } finally {
            restore();
        }
        RouterGameTests.succeedAfterReload(helper, reloadsBefore);
    }

    @GameTest(template = "empty", timeoutTicks = RouterGameTests.RELOAD_TIMEOUT,
            batch = "wirelessautomate_config_balance4_dono")
    public static void migracao4MantemOUnobtainiumDoDono(GameTestHelper helper) {
        int reloadsBefore = ConfigReloadListener.fileReloads();
        var unobtainium = Config.RESONATOR_FE_PER_CRYSTAL.get(RouterTier.UNOBTAINIUM);
        try {
            unobtainium.set(99_000_000L);
            Config.BALANCE_VERSION.set(3);
            helper.assertValueEqual(Config.migrateBalance(), 0, "valores migrados");
            helper.assertValueEqual(unobtainium.get(), 99_000_000L, "FE por cristal do dono");
        } finally {
            restore();
        }
        RouterGameTests.succeedAfterReload(helper, reloadsBefore);
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "wirelessautomate_config")
    public static void chavesNovasTemPadrao(GameTestHelper helper) {
        helper.assertValueEqual(Config.crystalIntegrity(), 8, "integridade");
        helper.assertValueEqual(Config.recastCrystals(), 4, "cristais para refazer");
        helper.succeed();
    }

    private static void restore() {
        for (RouterTier tier : RouterTier.values()) {
            Config.RESONATOR_FE_PER_CRYSTAL.get(tier).set(CoreMath.defaultFePerCrystal(tier.ordinal()));
            Config.TIERS.get(tier).rates().get(ResourceType.ENERGY.rateKey())
                    .set(ResourceType.ENERGY.defaultRate(tier.ordinal()));
        }
        Config.BALANCE_VERSION.set(Config.CURRENT_BALANCE);
        Config.SPEC.save();
    }
}
