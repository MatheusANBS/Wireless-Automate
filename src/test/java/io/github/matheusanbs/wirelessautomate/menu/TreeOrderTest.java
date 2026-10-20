package io.github.matheusanbs.wirelessautomate.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TreeOrderTest {
    /** Grupos 7 (dez máquinas, uma acima das guardadas) e 9 (duas), misturados na ordem de vínculo. */
    private static final int[] GROUP_OF = {7, 7, 9, 7, 7, 7, 7, 7, 7, 9, 7, 7};
    private static final int[] GROUP_IDS = {7, 9};
    private static final int[] GROUP_TOTALS = {11, 2};

    private static final TreeOrder.Machines MACHINES = new TreeOrder.Machines() {
        @Override
        public int size() {
            return GROUP_OF.length;
        }

        @Override
        public int machineGroup(int i) {
            return GROUP_OF[i];
        }

        @Override
        public int groupCount() {
            return GROUP_IDS.length;
        }

        @Override
        public int groupId(int g) {
            return GROUP_IDS[g];
        }

        @Override
        public int groupTotal(int g) {
            return GROUP_TOTALS[g];
        }
    };

    private static List<String> plan(boolean attached, Set<Integer> collapsed, Set<Integer> showAll, int budget) {
        List<String> rows = new ArrayList<>();
        int emitted = TreeOrder.plan(attached, MACHINES, collapsed::contains, showAll::contains, budget,
                new TreeOrder.Rows() {
                    @Override
                    public void attached() {
                        rows.add("presa");
                    }

                    @Override
                    public void group(int g) {
                        rows.add("g" + GROUP_IDS[g]);
                    }

                    @Override
                    public void machine(int i) {
                        rows.add("m" + i);
                    }

                    @Override
                    public void more(int g, int hidden) {
                        rows.add("+" + hidden);
                    }
                });
        assertEquals(rows.size(), emitted);
        return rows;
    }

    @Test
    void attachedThenGroupsWithEightMachinesAndMore() {
        assertEquals(List.of("presa", "g7", "m0", "m1", "m3", "m4", "m5", "m6", "m7", "m8", "+3", "g9", "m2", "m9"),
                plan(true, Set.of(), Set.of(), 256));
    }

    @Test
    void showAllListsEveryStoredMachineAndCountsTheRestAboveTheCap() {
        assertEquals(List.of("g7", "m0", "m1", "m3", "m4", "m5", "m6", "m7", "m8", "m10", "m11", "+1", "g9", "m2", "m9"),
                plan(false, Set.of(), Set.of(7), 256));
    }

    @Test
    void collapsedGroupShowsOnlyItsRow() {
        assertEquals(List.of("presa", "g7", "g9", "m2", "m9"), plan(true, Set.of(7), Set.of(), 256));
    }

    @Test
    void budgetCutsAnywhere() {
        assertEquals(List.of(), plan(true, Set.of(), Set.of(), 0));
        assertEquals(List.of("presa", "g7", "m0"), plan(true, Set.of(), Set.of(), 3));
        assertEquals(List.of("presa", "g7", "m0", "m1", "m3", "m4", "m5", "m6", "m7", "m8"),
                plan(true, Set.of(), Set.of(), 10));
    }
}
