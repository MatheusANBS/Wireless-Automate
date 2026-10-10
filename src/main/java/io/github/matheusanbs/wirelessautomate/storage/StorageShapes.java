package io.github.matheusanbs.wirelessautomate.storage;

import java.util.Map;

/**
 * Caixas de colisão e seleção dos armazenamentos, em pixels ({@code x0, y0, z0, x1, y1, z1}), por id do
 * bloco ({@link StorageKind#id}). Poucas caixas que abraçam o modelo gerado por
 * {@code scripts/textures/blocos.py}: são as mesmas de {@code HITBOXES} lá, que a geração confere contra
 * os elementos de cada modelo ({@code docs/preview/hitboxes.txt} lista as envolventes). Mude aqui e lá.
 * Lógica pura (sem classes do Minecraft): o {@link StorageBlock} monta o {@code VoxelShape}.
 */
public final class StorageShapes {
    private static final Map<String, double[][]> BOXES = Map.of(
            // Baú: pés, corpo recuado, tampa cheia e as gavetas salientes de cada lado.
            "storage_chest", new double[][] {
                    {1, 0, 1, 4, 2, 4}, {12, 0, 1, 15, 2, 4}, {1, 0, 12, 4, 2, 15}, {12, 0, 12, 15, 2, 15},
                    {1, 2, 1, 15, 14, 15},
                    {0, 14, 0, 16, 16, 16},
                    {2, 2, 15, 14, 13, 16}, {2, 2, 0, 14, 13, 1}, {15, 2, 2, 16, 13, 14}, {0, 2, 2, 1, 13, 14}},
            // Tanque: pés e base, anéis com a coluna de vidro e as réguas, tampa.
            "storage_tank", new double[][] {
                    {2, 0, 2, 14, 4, 14},
                    {3, 4, 3, 13, 14, 13},
                    {2, 14, 2, 14, 16, 14}},
            // Bateria: pés e plinto, células, os quatro visores, cornija, tampa e os dois terminais.
            "storage_battery", new double[][] {
                    {1, 0, 1, 15, 2, 15},
                    {2, 2, 2, 14, 13, 14},
                    {6, 2, 14, 10, 13, 15}, {6, 2, 1, 10, 13, 2}, {14, 2, 6, 15, 13, 10}, {1, 2, 6, 2, 13, 10},
                    {1, 13, 1, 15, 14, 15},
                    {2, 14, 2, 14, 15, 14},
                    {3, 15, 3, 5, 16, 5}, {11, 15, 11, 13, 16, 13}},
            // Tanque Químico: pés e anel, pescoço, ombro de baixo, equador, ombro de cima, calota e volante.
            "storage_chemical_tank", new double[][] {
                    {3, 0, 3, 13, 2, 13},
                    {4, 2, 4, 12, 3, 12},
                    {3, 3, 3, 13, 5, 13},
                    {2, 5, 2, 14, 11, 14},
                    {3, 11, 3, 13, 13, 13},
                    {4, 13, 4, 12, 15, 12}},
            // Tanque de Source (igual nos 11 níveis): para-choques, base com a coluna e a tampa, colar e gema.
            "storage_source_tank", new double[][] {
                    {2, 0, 2, 14, 1, 14},
                    {3, 0, 3, 13, 13, 13},
                    {6, 13, 6, 10, 14, 10},
                    {6.5, 14, 6.5, 9.5, 16, 9.5}});

    /** Os ids conhecidos (os de {@link StorageKind}). */
    public static Iterable<String> ids() {
        return BOXES.keySet();
    }

    /** As caixas do bloco {@code id}, em pixels; falha com um id desconhecido. */
    public static double[][] boxes(String id) {
        double[][] boxes = BOXES.get(id);
        if (boxes == null) {
            throw new IllegalArgumentException("sem hitbox para " + id);
        }
        return boxes;
    }

    private StorageShapes() {
    }
}
