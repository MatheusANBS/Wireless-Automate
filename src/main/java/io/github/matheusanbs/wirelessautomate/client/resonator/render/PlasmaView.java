package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * A matriz que leva um vetor do mundo para o quadro do billboard do plasma. O quad é girado pela rotação da câmera
 * (a mesma das partículas do vanilla), então o que está fixo no mundo precisa do inverso dela para o shader
 * desenhar a curva parada no mundo e não presa à tela. Pura: sem JOML e sem alocar.
 */
final class PlasmaView {
    private PlasmaView() {
    }

    /**
     * Escreve em {@code out} (9 floats, coluna-maior, o layout do uniform {@code mat3}) o inverso da rotação do
     * quaternion unitário {@code (x, y, z, w)}: a transposta, que é o inverso de uma rotação.
     */
    static void inverseRotation(float x, float y, float z, float w, float[] out) {
        float m00 = 1 - 2 * (y * y + z * z);
        float m01 = 2 * (x * y + z * w);
        float m02 = 2 * (x * z - y * w);
        float m10 = 2 * (x * y - z * w);
        float m11 = 1 - 2 * (x * x + z * z);
        float m12 = 2 * (y * z + x * w);
        float m20 = 2 * (x * z + y * w);
        float m21 = 2 * (y * z - x * w);
        float m22 = 1 - 2 * (x * x + y * y);
        out[0] = m00;
        out[1] = m10;
        out[2] = m20;
        out[3] = m01;
        out[4] = m11;
        out[5] = m21;
        out[6] = m02;
        out[7] = m12;
        out[8] = m22;
    }
}
