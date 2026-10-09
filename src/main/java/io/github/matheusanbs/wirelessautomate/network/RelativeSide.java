package io.github.matheusanbs.wirelessautomate.network;

import java.util.Locale;
import net.minecraft.core.Direction;

/**
 * Face da máquina em relação ao {@code facing} e ao giro ({@code spin}) do roteador. A configuração
 * por face é salva assim, para que presets e blocos girados funcionem com o roteador virado para
 * qualquer lado.
 *
 * <p>Os nomes são de quem olha de fora para a face {@link #FRONT}, com {@link #TOP} para cima na
 * visão. Com o roteador numa lateral da máquina e sem giro, {@link #TOP} é para cima e {@link #LEFT}
 * e {@link #RIGHT} são a esquerda e a direita de quem olha. A conversão parte das direções do caso
 * {@code facing=up} sem giro, em que {@link #TOP} é para onde apontam os LEDs (sul): primeiro o
 * {@code spin} as gira em torno de Y no sentido horário visto de cima ({@link Direction#getClockWise()}),
 * {@code spin} vezes; depois aplica a mesma rotação do blockstate (a do para-raios: {@code up} sem
 * rotação, {@code down} x=180, laterais x=90 + y). Com {@code spin=0} o resultado é o de antes do giro.
 */
public enum RelativeSide {
    /** A face onde o roteador está preso (igual ao {@code facing}). */
    FRONT(Direction.UP),
    /** A face oposta à do roteador. */
    BACK(Direction.DOWN),
    /** Para onde apontam os LEDs do roteador. */
    TOP(Direction.SOUTH),
    /** O lado das antenas. */
    BOTTOM(Direction.NORTH),
    LEFT(Direction.EAST),
    RIGHT(Direction.WEST);

    /** Giros possíveis do roteador (0 a 3, de 90° cada). */
    public static final int SPINS = 4;

    private static final RelativeSide[] VALUES = values();
    private static final Direction[] DIRECTIONS = Direction.values();
    /** [facing][spin][lado relativo] → face absoluta. */
    private static final Direction[][][] TO_ABSOLUTE = new Direction[DIRECTIONS.length][SPINS][VALUES.length];
    /** [facing][spin][face absoluta] → lado relativo. */
    private static final RelativeSide[][][] FROM_ABSOLUTE =
            new RelativeSide[DIRECTIONS.length][SPINS][DIRECTIONS.length];

    static {
        for (Direction facing : DIRECTIONS) {
            for (int spin = 0; spin < SPINS; spin++) {
                for (RelativeSide side : VALUES) {
                    Direction absolute = rotate(spin(side.whenUp, spin), facing);
                    TO_ABSOLUTE[facing.ordinal()][spin][side.ordinal()] = absolute;
                    FROM_ABSOLUTE[facing.ordinal()][spin][absolute.ordinal()] = side;
                }
            }
        }
    }

    /** Face absoluta com o roteador em {@code facing=up}, sem giro nem rotação de modelo. */
    private final Direction whenUp;
    private final String key = name().toLowerCase(Locale.ROOT);

    RelativeSide(Direction whenUp) {
        this.whenUp = whenUp;
    }

    /** Nome em minúsculas, usado como chave no NBT. */
    public String key() {
        return key;
    }

    /** Face absoluta deste lado com o roteador em {@code facing} e girado {@code spin} vezes (0 a 3). */
    public Direction toAbsolute(Direction facing, int spin) {
        return TO_ABSOLUTE[facing.ordinal()][spin][ordinal()];
    }

    public static Direction toAbsolute(Direction facing, int spin, RelativeSide side) {
        return side.toAbsolute(facing, spin);
    }

    public static RelativeSide fromAbsolute(Direction facing, int spin, Direction absolute) {
        return FROM_ABSOLUTE[facing.ordinal()][spin][absolute.ordinal()];
    }

    /** Giro em torno de Y, horário visto de cima, {@code spin} vezes; cima e baixo ficam. */
    private static Direction spin(Direction dir, int spin) {
        for (int i = 0; i < spin; i++) {
            dir = dir.getAxis() == Direction.Axis.Y ? dir : dir.getClockWise();
        }
        return dir;
    }

    /** Rotação do blockstate para {@code facing}: primeiro x, depois y (como em RouterShapes). */
    private static Direction rotate(Direction dir, Direction facing) {
        int xSteps = switch (facing) {
            case UP -> 0;
            case DOWN -> 2;
            default -> 1;
        };
        int ySteps = switch (facing) {
            case SOUTH -> 2;
            case EAST -> 1;
            case WEST -> 3;
            default -> 0;
        };
        for (int i = 0; i < xSteps; i++) {
            dir = rotateX(dir);
        }
        return spin(dir, ySteps);
    }

    /** x=90 do modelo: cima vira norte, norte vira baixo, baixo vira sul e sul vira cima. */
    private static Direction rotateX(Direction dir) {
        return switch (dir) {
            case UP -> Direction.NORTH;
            case NORTH -> Direction.DOWN;
            case DOWN -> Direction.SOUTH;
            case SOUTH -> Direction.UP;
            default -> dir;
        };
    }
}
