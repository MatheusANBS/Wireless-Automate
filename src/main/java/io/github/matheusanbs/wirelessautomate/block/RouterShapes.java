package io.github.matheusanbs.wirelessautomate.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Formas de colisão do roteador para cada {@code facing}, rotacionadas com a mesma convenção do
 * blockstate (a do para-raios): {@code up} sem rotação, {@code down} x=180, laterais x=90 e y pela direção.
 */
final class RouterShapes {
    /** Caixas do modelo com o roteador virado para cima, em pixels: corpo e duas antenas. */
    private static final double[][] UP_BOXES = {
            {1, 0, 2, 15, 6, 14},
            {1.5, 6, 2.5, 3.5, 16, 4.5},
            {12.5, 6, 2.5, 14.5, 16, 4.5},
    };

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            SHAPES.put(facing, build(facing));
        }
    }

    static VoxelShape get(Direction facing) {
        return SHAPES.get(facing);
    }

    private static VoxelShape build(Direction facing) {
        int xRot = switch (facing) {
            case UP -> 0;
            case DOWN -> 180;
            default -> 90;
        };
        int yRot = switch (facing) {
            case SOUTH -> 180;
            case EAST -> 90;
            case WEST -> 270;
            default -> 0;
        };
        VoxelShape shape = Shapes.empty();
        for (double[] box : UP_BOXES) {
            double[] a = rotate(box[0], box[1], box[2], xRot, yRot);
            double[] b = rotate(box[3], box[4], box[5], xRot, yRot);
            shape = Shapes.or(shape, net.minecraft.world.level.block.Block.box(
                    Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                    Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])));
        }
        return shape;
    }

    private static double[] rotate(double x, double y, double z, int xRot, int yRot) {
        for (int i = 0; i < xRot / 90; i++) {
            double ny = z;
            double nz = 16 - y;
            y = ny;
            z = nz;
        }
        for (int i = 0; i < yRot / 90; i++) {
            double nx = 16 - z;
            double nz = x;
            x = nx;
            z = nz;
        }
        return new double[] {x, y, z};
    }

    private RouterShapes() {
    }
}
