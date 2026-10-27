package io.github.matheusanbs.wirelessautomate.tv;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A validação de uma área de Tela de Sinal no mundo: tamanho, parede atrás de cada célula, célula livre e sem
 * cruzar outra Tela. Roda só quando alguém aplica um tamanho (e na prévia do cliente), nunca por tick, e nunca
 * consulta um chunk descarregado.
 */
public final class TvAreaCheck {
    /** As Telas mais longe que isto (do bloco colocado) não entram na comparação de caixas. */
    private static final double OTHERS_RANGE_SQ = 64.0 * 64.0;

    public enum Kind { NONE, TOO_BIG, NO_WALL, BLOCKED, OVERLAP }

    /** O primeiro problema achado e a posição da célula, quando há uma. */
    public record Problem(Kind kind, @Nullable BlockPos pos) {
        public static final Problem NONE = new Problem(Kind.NONE, null);

        public boolean ok() {
            return kind == Kind.NONE;
        }
    }

    private TvAreaCheck() {
    }

    public static Problem check(Level level, BlockPos pos, Direction facing, TvArea area, int maxWidth,
                                int maxHeight, Iterable<TvBlockEntity> others) {
        if (!TvArea.fits(area.width(), area.height(), maxWidth, maxHeight)) {
            return new Problem(Kind.TOO_BIG, null);
        }
        Direction right = TvBlock.rightOf(facing);
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos wall = new BlockPos.MutableBlockPos();
        for (int i = 0; i < area.cells(); i++) {
            int col = area.rightOf(i);
            int row = area.upOf(i);
            if (col == 0 && row == 0) {
                continue;
            }
            cell.set(pos).move(right, col).move(Direction.UP, row);
            if (!level.isLoaded(cell)) {
                return new Problem(Kind.BLOCKED, cell.immutable());
            }
            wall.set(cell).move(facing.getOpposite());
            if (!level.isLoaded(wall) || !level.getBlockState(wall).isFaceSturdy(level, wall, facing)) {
                return new Problem(Kind.NO_WALL, cell.immutable());
            }
            BlockState state = level.getBlockState(cell);
            if (!state.isAir() && !state.canBeReplaced()) {
                return new Problem(Kind.BLOCKED, cell.immutable());
            }
        }
        AABB box = TvBlockEntity.box(pos, facing, area);
        for (TvBlockEntity other : others) {
            if (other.getLevel() != level || other.getBlockPos().equals(pos)
                    || other.getBlockPos().distSqr(pos) > OTHERS_RANGE_SQ) {
                continue;
            }
            if (box.intersects(other.renderBox())) {
                return new Problem(Kind.OVERLAP, other.getBlockPos());
            }
        }
        return Problem.NONE;
    }
}
