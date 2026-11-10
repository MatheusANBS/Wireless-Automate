package io.github.matheusanbs.wirelessautomate.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import org.junit.jupiter.api.Test;

class StorageBalanceTest {
    @Test
    void umaHoraDaVazao() {
        assertEquals(115_200L, StorageBalance.hourOf(32));
        assertEquals(28_800_000L, StorageBalance.hourOf(8_000));
        assertEquals(0L, StorageBalance.hourOf(0));
        assertEquals(Long.MAX_VALUE, StorageBalance.hourOf(Long.MAX_VALUE / 10));
    }

    /** O {@code StorageKind} usa estas tabelas; ele carrega classes do Minecraft, então quem o confere é o GameTest da migração 6. */
    @Test
    void tabelasSeguemAsVazoesPadrao() {
        assertArrayEquals(new long[] {115_200L, 921_600L, 7_372_800L, 58_982_400L, 471_859_200L, 3_774_873_600L,
                30_198_988_800L, 0L}, StorageBalance.hourOf(ResourceType.ITEM.defaultRates()));
        assertArrayEquals(new long[] {28_800_000L, 230_400_000L, 1_843_200_000L, 14_745_600_000L, 117_964_800_000L,
                943_718_400_000L, 7_549_747_200_000L, 0L}, StorageBalance.hourOf(ResourceType.FLUID.defaultRates()));
        assertArrayEquals(new long[] {3_600_000L, 28_800_000L, 230_400_000L, 1_843_200_000L, 14_745_600_000L,
                117_964_800_000L, 943_718_400_000L, 0L}, StorageBalance.hourOf(ResourceType.SOURCE.defaultRates()));
    }

}
