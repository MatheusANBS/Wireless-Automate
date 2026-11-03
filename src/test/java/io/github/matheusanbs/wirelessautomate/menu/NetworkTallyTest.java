package io.github.matheusanbs.wirelessautomate.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NetworkTallyTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    @Test
    void countsMachinesAndRolesPerTypeAndNetwork() {
        Map<ResourceType, List<UUID>> byType = new EnumMap<>(ResourceType.class);
        byType.put(ResourceType.ITEM, List.of(A));
        byType.put(ResourceType.FLUID, List.of(B));
        List<UUID> order = List.of(A, B);
        Map<UUID, NetworkTally.Counts> totals = new HashMap<>();
        totals.put(A, new NetworkTally.Counts());
        totals.put(B, new NetworkTally.Counts());

        int extractItem = NetworkTally.bit(ResourceType.ITEM, NetworkTally.EXTRACT);
        int insertFluid = NetworkTally.bit(ResourceType.FLUID, NetworkTally.INSERT);
        int storageItem = NetworkTally.bit(ResourceType.ITEM, NetworkTally.STORAGE);
        NetworkTally.machine(extractItem | insertFluid, 0b11, order, byType::get, totals);
        NetworkTally.machine(storageItem, 0b01, order, byType::get, totals);
        NetworkTally.machine(0, 0b10, order, byType::get, totals);

        NetworkTally.Counts a = totals.get(A);
        assertEquals(2, a.machines());
        assertEquals(2, a.givers(ResourceType.ITEM));
        assertEquals(1, a.receivers(ResourceType.ITEM));
        assertEquals(0, a.receivers(ResourceType.FLUID));
        NetworkTally.Counts b = totals.get(B);
        assertEquals(2, b.machines());
        assertEquals(1, b.receivers(ResourceType.FLUID));
        assertEquals(0, b.givers(ResourceType.ITEM));
    }

    @Test
    void networksOutsideTheMapAreIgnored() {
        Map<UUID, NetworkTally.Counts> totals = new HashMap<>();
        NetworkTally.machine(NetworkTally.bit(ResourceType.ITEM, NetworkTally.EXTRACT), 1L, List.of(A),
                type -> List.of(A), totals);
        assertEquals(0, totals.size());
    }
}
