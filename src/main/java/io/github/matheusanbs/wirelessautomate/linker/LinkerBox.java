package io.github.matheusanbs.wirelessautomate.linker;

/**
 * Caixa de blocos da área do Vinculador, com os dois cantos inclusos. Lógica pura, sem classes do
 * Minecraft (testada por JUnit): volume, pertinência, chunks cobertos e distância até um ponto.
 */
public record LinkerBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public LinkerBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("caixa invertida");
        }
    }

    /** Caixa entre dois cantos, em qualquer ordem. */
    public static LinkerBox of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new LinkerBox(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    /** Blocos dentro da caixa (em {@code long}: a caixa pode passar de 2³¹ antes da recusa). */
    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public int minChunkX() {
        return minX >> 4;
    }

    public int maxChunkX() {
        return maxX >> 4;
    }

    public int minChunkZ() {
        return minZ >> 4;
    }

    public int maxChunkZ() {
        return maxZ >> 4;
    }

    /** Chunks (colunas) que a caixa toca. */
    public long chunkCount() {
        return (long) (maxChunkX() - minChunkX() + 1) * (maxChunkZ() - minChunkZ() + 1);
    }

    /**
     * Quadrado da distância de um ponto à caixa (0 dentro dela). A caixa ocupa os blocos inteiros:
     * vai de {@code min} a {@code max + 1} em cada eixo.
     */
    public double distanceSqTo(double x, double y, double z) {
        double dx = axis(x, minX, maxX + 1);
        double dy = axis(y, minY, maxY + 1);
        double dz = axis(z, minZ, maxZ + 1);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axis(double value, double min, double max) {
        return value < min ? min - value : value > max ? value - max : 0;
    }
}
