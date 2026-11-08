package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.ConfigReloadListener;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;
import io.github.matheusanbs.wirelessautomate.resonator.EnergyBalance;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * As migrações 3, 4 e 5 do balanceamento do Ressonador (a escada nova do FE por cristal e da energia do roteador, o
 * Unobtainium de 184 para 10,7 milhões por cristal, e a energia do roteador e a Bateria ligadas ao Ressonador) e as
 * chaves novas da config. Os dois testes de migração gravam a config e esperam a recarga do arquivo, como os de
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
            helper.assertValueEqual(Config.migrateBalance(), 15, "valores migrados");
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

    private static final long[] BALANCE4_ENERGY = {1_000, 32_768, 64_000, 2_097_152, 67_108_864, 134_217_728L, 4_294_967_296L, 0};
    private static final long[] BALANCE4_BATTERY = {1_000_000L, 8_000_000L, 64_000_000L, 512_000_000L, 4_096_000_000L,
            32_768_000_000L, 262_144_000_000L, 0};

    @GameTest(template = "empty", timeoutTicks = RouterGameTests.RELOAD_TIMEOUT,
            batch = "wirelessautomate_config_balance5")
    public static void migracao5TrocaOPadraoAntigoDoRoteadorEDaBateria(GameTestHelper helper) {
        int reloadsBefore = ConfigReloadListener.fileReloads();
        try {
            for (RouterTier tier : RouterTier.values()) {
                Config.TIERS.get(tier).rates().get(ResourceType.ENERGY.rateKey()).set(BALANCE4_ENERGY[tier.ordinal()]);
                Config.STORAGE_CAPACITY.get(StorageKind.BATTERY).get(tier).set(BALANCE4_BATTERY[tier.ordinal()]);
            }
            Config.BALANCE_VERSION.set(4);
            // o Ultimate já é sem limite nos dois: 7 tiers x 2 valores
            helper.assertValueEqual(Config.migrateBalance(), 14, "valores migrados");
            for (RouterTier tier : RouterTier.values()) {
                int i = tier.ordinal();
                helper.assertValueEqual(Config.TIERS.get(tier).rates().get(ResourceType.ENERGY.rateKey()).get(),
                        EnergyBalance.routerEnergy(i), "energia do roteador, " + tier);
                helper.assertValueEqual(Config.STORAGE_CAPACITY.get(StorageKind.BATTERY).get(tier).get(),
                        EnergyBalance.batteryCapacity(i), "capacidade da Bateria, " + tier);
            }
            helper.assertValueEqual(Config.BALANCE_VERSION.get(), Config.CURRENT_BALANCE, "versão");
        } finally {
            restore();
        }
        RouterGameTests.succeedAfterReload(helper, reloadsBefore);
    }

    @GameTest(template = "empty", timeoutTicks = RouterGameTests.RELOAD_TIMEOUT,
            batch = "wirelessautomate_config_balance5_dono")
    public static void migracao5MantemOQueODonoMudou(GameTestHelper helper) {
        int reloadsBefore = ConfigReloadListener.fileReloads();
        var basicEnergy = Config.TIERS.get(RouterTier.BASIC).rates().get(ResourceType.ENERGY.rateKey());
        var eliteBattery = Config.STORAGE_CAPACITY.get(StorageKind.BATTERY).get(RouterTier.ELITE);
        try {
            basicEnergy.set(777L);
            eliteBattery.set(123_456_789L);
            Config.BALANCE_VERSION.set(4);
            Config.migrateBalance();
            helper.assertValueEqual(basicEnergy.get(), 777L, "energia do dono");
            helper.assertValueEqual(eliteBattery.get(), 123_456_789L, "capacidade do dono");
            helper.assertValueEqual(Config.BALANCE_VERSION.get(), Config.CURRENT_BALANCE, "versão");
        } finally {
            restore();
        }
        RouterGameTests.succeedAfterReload(helper, reloadsBefore);
    }

    private static final long[] BALANCE5_FLUID = {2_000L, 16_000L, 128_000L, 1_024_000L, 8_192_000L, 65_536_000L,
            524_288_000L, 0};
    private static final long[] BALANCE5_SOURCE = {100L, 800L, 6_400L, 51_200L, 409_600L, 3_276_800L, 26_214_400L, 0};
    private static final long[] BALANCE5_CHEST = {32_768L, 262_144L, 2_097_152L, 16_777_216L, 134_217_728L,
            1_073_741_824L, 8_589_934_592L, 0};
    private static final long[] BALANCE5_TANK = {256_000L, 2_048_000L, 16_384_000L, 131_072_000L, 1_048_576_000L,
            8_388_608_000L, 67_108_864_000L, 0};
    private static final long[] BALANCE5_SOURCE_TANK = {10_000L, 80_000L, 640_000L, 5_120_000L, 40_960_000L,
            327_680_000L, 2_621_440_000L, 0};

    /** A migração 6: fluido, químico (a mesma chave) e Source, e as capacidades do Baú, dos Tanques e do Tanque de Source. */
    @GameTest(template = "empty", timeoutTicks = RouterGameTests.RELOAD_TIMEOUT,
            batch = "wirelessautomate_config_balance6")
    public static void migracao6TrocaAsVazoesEAsCapacidadesAntigas(GameTestHelper helper) {
        int reloadsBefore = ConfigReloadListener.fileReloads();
        try {
            for (RouterTier tier : RouterTier.values()) {
                int i = tier.ordinal();
                Config.TIERS.get(tier).rates().get(ResourceType.FLUID.rateKey()).set(BALANCE5_FLUID[i]);
                Config.TIERS.get(tier).rates().get(ResourceType.SOURCE.rateKey()).set(BALANCE5_SOURCE[i]);
                Config.STORAGE_CAPACITY.get(StorageKind.CHEST).get(tier).set(BALANCE5_CHEST[i]);
                Config.STORAGE_CAPACITY.get(StorageKind.TANK).get(tier).set(BALANCE5_TANK[i]);
                Config.STORAGE_CAPACITY.get(StorageKind.CHEMICAL_TANK).get(tier).set(BALANCE5_TANK[i]);
                Config.STORAGE_CAPACITY.get(StorageKind.SOURCE_TANK).get(tier).set(BALANCE5_SOURCE_TANK[i]);
            }
            Config.BALANCE_VERSION.set(5);
            // o Ultimate já é sem limite em tudo: 7 tiers × (2 vazões + 4 capacidades)
            helper.assertValueEqual(Config.migrateBalance(), 42, "valores migrados");
            for (RouterTier tier : RouterTier.values()) {
                int i = tier.ordinal();
                helper.assertValueEqual(Config.TIERS.get(tier).rate(ResourceType.FLUID), ResourceType.FLUID.defaultRate(i),
                        "fluido, " + tier);
                helper.assertValueEqual(Config.TIERS.get(tier).rate(ResourceType.CHEMICAL),
                        ResourceType.CHEMICAL.defaultRate(i), "químico, " + tier);
                helper.assertValueEqual(Config.TIERS.get(tier).rate(ResourceType.SOURCE), ResourceType.SOURCE.defaultRate(i),
                        "Source, " + tier);
                for (StorageKind kind : StorageKind.values()) {
                    helper.assertValueEqual(Config.STORAGE_CAPACITY.get(kind).get(tier).get(), kind.defaultCapacity(tier),
                            kind + ", " + tier);
                }
            }
            helper.assertValueEqual(Config.BALANCE_VERSION.get(), Config.CURRENT_BALANCE, "versão");
        } finally {
            restore();
        }
        RouterGameTests.succeedAfterReload(helper, reloadsBefore);
    }

    @GameTest(template = "empty", timeoutTicks = RouterGameTests.RELOAD_TIMEOUT,
            batch = "wirelessautomate_config_balance6_dono")
    public static void migracao6MantemOQueODonoMudou(GameTestHelper helper) {
        int reloadsBefore = ConfigReloadListener.fileReloads();
        var basicFluid = Config.TIERS.get(RouterTier.BASIC).rates().get(ResourceType.FLUID.rateKey());
        var eliteChest = Config.STORAGE_CAPACITY.get(StorageKind.CHEST).get(RouterTier.ELITE);
        try {
            basicFluid.set(777L);
            eliteChest.set(123_456_789L);
            Config.BALANCE_VERSION.set(5);
            Config.migrateBalance();
            helper.assertValueEqual(basicFluid.get(), 777L, "fluido do dono");
            helper.assertValueEqual(eliteChest.get(), 123_456_789L, "capacidade do dono");
            helper.assertValueEqual(Config.BALANCE_VERSION.get(), Config.CURRENT_BALANCE, "versão");
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
            for (ResourceType type : new ResourceType[] {ResourceType.FLUID, ResourceType.SOURCE}) {
                Config.TIERS.get(tier).rates().get(type.rateKey()).set(type.defaultRate(tier.ordinal()));
            }
            for (StorageKind kind : StorageKind.values()) {
                Config.STORAGE_CAPACITY.get(kind).get(tier).set(kind.defaultCapacity(tier));
            }
        }
        Config.BALANCE_VERSION.set(Config.CURRENT_BALANCE);
        Config.SPEC.save();
    }
}
