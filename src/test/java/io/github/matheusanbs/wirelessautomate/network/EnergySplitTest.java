package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class EnergySplitTest {
    private static long[] split(long offered, long[] wants, int[] priorities) {
        long[] out = new long[wants.length + 2];
        Arrays.fill(out, -7);
        long total = EnergySplit.split(offered, wants, priorities, wants.length, out);
        long[] result = Arrays.copyOf(out, wants.length);
        assertEquals(Arrays.stream(result).sum(), total, "total diferente da soma");
        assertEquals(-7, out[wants.length], "escreveu além de count");
        return result;
    }

    @Test
    void singleDestinationTakesWhatItWants() {
        assertArrayEquals(new long[] {300}, split(1000, new long[] {300}, new int[] {0}));
        assertArrayEquals(new long[] {1000}, split(1000, new long[] {5000}, new int[] {0}));
    }

    @Test
    void equalPriorityIsSplitEvenly() {
        assertArrayEquals(new long[] {100, 100, 100},
                split(300, new long[] {1000, 1000, 1000}, new int[] {0, 0, 0}));
    }

    @Test
    void smallWantsLeaveTheRestToOthers() {
        assertArrayEquals(new long[] {10, 145, 145},
                split(300, new long[] {10, 1000, 1000}, new int[] {0, 0, 0}));
        assertArrayEquals(new long[] {145, 10, 145},
                split(300, new long[] {1000, 10, 1000}, new int[] {0, 0, 0}));
    }

    @Test
    void remainderGoesToFirstInOrder() {
        assertArrayEquals(new long[] {4, 3, 3},
                split(10, new long[] {100, 100, 100}, new int[] {0, 0, 0}));
        assertArrayEquals(new long[] {1, 1, 0},
                split(2, new long[] {100, 100, 100}, new int[] {0, 0, 0}));
    }

    @Test
    void higherPriorityIsServedFirst() {
        assertArrayEquals(new long[] {50, 125, 125},
                split(300, new long[] {50, 1000, 1000}, new int[] {5, 0, 0}));
        assertArrayEquals(new long[] {300, 0, 0},
                split(300, new long[] {1000, 1000, 1000}, new int[] {5, 0, 0}));
        assertArrayEquals(new long[] {100, 100, 50, 50},
                split(300, new long[] {100, 100, 1000, 1000}, new int[] {9, 9, -1, -1}));
    }

    @Test
    void refusingDestinationsGetNothing() {
        assertArrayEquals(new long[] {0, 300, 0},
                split(300, new long[] {0, 1000, -5}, new int[] {0, 0, 0}));
        assertArrayEquals(new long[] {0, 0}, split(300, new long[] {0, 0}, new int[] {0, 0}));
    }

    @Test
    void neverMoreThanOfferedOrWanted() {
        Random random = new Random(42);
        for (int round = 0; round < 2000; round++) {
            int count = 1 + random.nextInt(8);
            long[] wants = new long[count];
            int[] priorities = new int[count];
            int priority = 10;
            for (int i = 0; i < count; i++) {
                wants[i] = random.nextInt(4) == 0 ? 0 : random.nextInt(2000);
                if (random.nextBoolean()) {
                    priority -= random.nextInt(3);
                }
                priorities[i] = priority;
            }
            long offered = random.nextInt(5000);
            long[] out = split(offered, wants, priorities);
            long total = Arrays.stream(out).sum();
            long wanted = Arrays.stream(wants).sum();
            assertEquals(Math.min(offered, wanted), total, "não dividiu tudo o que dava");
            for (int i = 0; i < count; i++) {
                assertTrue(out[i] >= 0 && out[i] <= wants[i], "fora do pedido em " + i);
            }
        }
    }

    @Test
    void largeValuesDoNotOverflow() {
        long max = Integer.MAX_VALUE;
        long[] out = split(max, new long[] {max, max, max}, new int[] {0, 0, 0});
        assertEquals(max, out[0] + out[1] + out[2]);
        assertEquals(0, EnergySplit.split(500, new long[0], new int[0], 0, new long[0]));
        assertEquals(0, EnergySplit.split(-1, new long[] {10}, new int[] {0}, 1, new long[1]));
    }
}
