package io.github.matheusanbs.wirelessautomate.network;

/**
 * Caixa que envolve posições de blocos, para saber de uma vez se todas estão ao alcance de um
 * ponto: basta o canto mais distante da caixa estar. Condição suficiente (a caixa pode ser maior
 * que o conjunto); quem usa cai na conferência um a um quando ela falha. Lógica pura, testada por JUnit.
 */
final class ReachBox {
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private boolean empty = true;

    void clear() {
        empty = true;
    }

    boolean isEmpty() {
        return empty;
    }

    void include(int x, int y, int z) {
        if (empty) {
            minX = maxX = x;
            minY = maxY = y;
            minZ = maxZ = z;
            empty = false;
            return;
        }
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
    }

    /** Quadrado da distância de (x, y, z) ao ponto mais distante da caixa; 0 se vazia. */
    long maxDistanceSqr(int x, int y, int z) {
        if (empty) {
            return 0;
        }
        long dx = Math.max(Math.abs((long) x - minX), Math.abs((long) x - maxX));
        long dy = Math.max(Math.abs((long) y - minY), Math.abs((long) y - maxY));
        long dz = Math.max(Math.abs((long) z - minZ), Math.abs((long) z - maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Todo ponto da caixa está a no máximo {@code range} de (x, y, z), pela distância euclidiana;
     * {@code range <= 0} é sem limite.
     */
    boolean allWithin(int x, int y, int z, int range) {
        return range <= 0 || maxDistanceSqr(x, y, z) <= (long) range * range;
    }
}
