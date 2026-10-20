package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * A ordem das Portas na casca: por x, depois y, depois z, relativos ao canto da caixa. É a mesma em que o cliente
 * varre a casca ({@code ChamberScene.readPorts}), então o índice de uma Porta de Energia vale nos dois lados.
 */
public final class PortOrder {
    private PortOrder() {
    }

    /** Uma cópia das Portas na ordem x, y, z relativa à origem. */
    public static List<BlockPos> sort(int[] origin, List<BlockPos> ports) {
        List<BlockPos> sorted = new ArrayList<>(ports);
        sorted.sort((a, b) -> compare(origin, a, b));
        return sorted;
    }

    private static int compare(int[] origin, BlockPos a, BlockPos b) {
        int x = Integer.compare(a.getX() - origin[0], b.getX() - origin[0]);
        if (x != 0) {
            return x;
        }
        int y = Integer.compare(a.getY() - origin[1], b.getY() - origin[1]);
        return y != 0 ? y : Integer.compare(a.getZ() - origin[2], b.getZ() - origin[2]);
    }
}
