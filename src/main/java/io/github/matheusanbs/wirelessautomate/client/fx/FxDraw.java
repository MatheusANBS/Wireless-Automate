package io.github.matheusanbs.wirelessautomate.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * O desenho dos efeitos: o sprite que olha para a câmera ({@code spr}), o decalque num plano ({@code dec}) e a fita
 * contínua entre pontos ({@code fita}), portados de {@code shared.js} do filme. Escrevem vértices {@code NEW_ENTITY}
 * (posição, cor, uv, a dissolução no uv1, a luz no uv2 e a normal para cima) com coordenadas já relativas à câmera,
 * em blocos. Não criam objeto: o desenho roda só na thread de render, então os rascunhos são estáticos.
 */
public final class FxDraw {
    /** Abaixo disto o desenho não vale o vértice (o {@code a > 0.002} do filme). */
    private static final float MIN_ALPHA = 0.002f;
    /** Menor vetor que ainda se normaliza (placas Intel: normalizar zero vira NaN e quadrado preto). */
    private static final float EPS = 1e-6f;

    /** Os lados esquerdo e direito de cada amostra da fita, e o u acumulado. */
    private static final float[] LEFT = new float[FxRibbon.MAX * 3];
    private static final float[] RIGHT = new float[FxRibbon.MAX * 3];
    private static final float[] U = new float[FxRibbon.MAX];

    private FxDraw() {
    }

    /**
     * O {@code spr}: um quad de meio lado {@code half} centrado em {@code (x, y, z)}, virado para a câmera e girado
     * {@code rot} radianos.
     *
     * @param dissolve a dissolução de 0 (inteiro) a 1000 (some), na trama Bayer do shader
     * @param light a luz empacotada ({@code LightTexture.FULL_BRIGHT} nas folhas de luz)
     * @return se o quad saiu (falso quando o alfa ou o tamanho são nulos: nada foi para o buffer)
     */
    public static boolean sprite(VertexConsumer out, FxContext c, FxSheet sheet, int frame, float x, float y, float z,
            float half, float rot, int rgb, float alpha, int dissolve, int light) {
        return decal(out, sheet, frame, x, y, z, c.rx, c.ry, c.rz, c.ux, c.uy, c.uz, half, rot, rgb, alpha, dissolve,
                light);
    }

    /**
     * O {@code dec}: o mesmo quad, mas nos eixos dados ({@code a1} e {@code a2}) no lugar da direita e do alto da
     * câmera, para o que fica colado numa superfície. Devolve se o quad saiu.
     */
    public static boolean decal(VertexConsumer out, FxSheet sheet, int frame, float cx, float cy, float cz,
            float a1x, float a1y, float a1z, float a2x, float a2y, float a2z, float half, float rot, int rgb,
            float alpha, int dissolve, int light) {
        if (!(alpha > MIN_ALPHA) || !(half > 0)) {
            return false;
        }
        float cos = (float) Math.cos(rot) * half;
        float sin = (float) Math.sin(rot) * half;
        float e1x = a1x * cos + a2x * sin;
        float e1y = a1y * cos + a2y * sin;
        float e1z = a1z * cos + a2z * sin;
        float e2x = -a1x * sin + a2x * cos;
        float e2y = -a1y * sin + a2y * cos;
        float e2z = -a1z * sin + a2z * cos;
        float v0 = sheet.v0(frame);
        float v1 = sheet.v1(frame);
        int r = rgb >> 16 & 255;
        int g = rgb >> 8 & 255;
        int b = rgb & 255;
        int a = alphaByte(alpha);
        // (−e1−e2, +e1−e2, +e1+e2, −e1+e2) com uv (0,1) (1,1) (1,0) (0,0), como o filme
        vertex(out, cx - e1x - e2x, cy - e1y - e2y, cz - e1z - e2z, r, g, b, a, 0f, v1, dissolve, light);
        vertex(out, cx + e1x - e2x, cy + e1y - e2y, cz + e1z - e2z, r, g, b, a, 1f, v1, dissolve, light);
        vertex(out, cx + e1x + e2x, cy + e1y + e2y, cz + e1z + e2z, r, g, b, a, 1f, v0, dissolve, light);
        vertex(out, cx - e1x + e2x, cy - e1y + e2y, cz - e1z + e2z, r, g, b, a, 0f, v0, dissolve, light);
        return true;
    }

    /**
     * A {@code fita}: a textura corre ao longo das {@code n} amostras de {@code buf} (pontos relativos à câmera, em
     * blocos; a meia largura em {@code buf.w} e o alfa em {@code buf.a}). O lado de cada amostra é
     * {@code normalize(cross(tangente, ponto)) × w}, com a tangente média dos vizinhos, e as amostras vizinhas dividem
     * os vértices: sem emenda. O {@code u} acumula o comprimento em blocos, dividido por {@code repUnits} (os blocos
     * por repetição da textura) menos {@code offset} (fração de repetição). O {@code v} vai do lado L ao R. Devolve se
     * ao menos um segmento saiu (com o alfa de todos abaixo do mínimo a fita some).
     */
    public static boolean ribbon(VertexConsumer out, FxContext c, FxSheet sheet, int frame, FxRibbon.Buffer buf, int n,
            int rgb, float repUnits, float offset, int dissolve, int light) {
        // repUnits <= 0 (ou NaN) dividiria por zero; mais amostras que o rascunho estourariam os arrays
        if (n < 2 || !(repUnits > 0) || n > FxRibbon.MAX) {
            return false;
        }
        float[] p = buf.pts;
        float run = 0;
        float prevSx = c.rx;
        float prevSy = c.ry;
        float prevSz = c.rz;
        for (int i = 0; i < n; i++) {
            int i3 = i * 3;
            if (i > 0) {
                float dx = p[i3] - p[i3 - 3];
                float dy = p[i3 + 1] - p[i3 - 2];
                float dz = p[i3 + 2] - p[i3 - 1];
                run += (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            int lo = Math.max(i - 1, 0) * 3;
            int hi = Math.min(i + 1, n - 1) * 3;
            float tx = p[hi] - p[lo];
            float ty = p[hi + 1] - p[lo + 1];
            float tz = p[hi + 2] - p[lo + 2];
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            float sx = prevSx;
            float sy = prevSy;
            float sz = prevSz;
            if (tl > EPS) {
                tx /= tl;
                ty /= tl;
                tz /= tl;
                // cross(tangente, ponto − câmera); o ponto já é relativo à câmera
                float qx = ty * p[i3 + 2] - tz * p[i3 + 1];
                float qy = tz * p[i3] - tx * p[i3 + 2];
                float qz = tx * p[i3 + 1] - ty * p[i3];
                float ql = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
                if (ql > EPS) {
                    sx = qx / ql;
                    sy = qy / ql;
                    sz = qz / ql;
                }
            }
            // sem lado novo (vetor zero), repete o do vizinho em vez de gerar NaN
            prevSx = sx;
            prevSy = sy;
            prevSz = sz;
            float w = buf.w[i];
            LEFT[i3] = p[i3] + sx * w;
            LEFT[i3 + 1] = p[i3 + 1] + sy * w;
            LEFT[i3 + 2] = p[i3 + 2] + sz * w;
            RIGHT[i3] = p[i3] - sx * w;
            RIGHT[i3 + 1] = p[i3 + 1] - sy * w;
            RIGHT[i3 + 2] = p[i3 + 2] - sz * w;
            U[i] = run / repUnits - offset;
        }
        float v0 = sheet.v0(frame);
        float v1 = sheet.v1(frame);
        int r = rgb >> 16 & 255;
        int g = rgb >> 8 & 255;
        int b = rgb & 255;
        boolean drawn = false;
        for (int i = 0; i < n - 1; i++) {
            float a0 = buf.a[i];
            float a1 = buf.a[i + 1];
            if (a0 < MIN_ALPHA && a1 < MIN_ALPHA) {
                continue;
            }
            int j = i * 3 + 3;
            int k = i * 3;
            int ia0 = alphaByte(a0);
            int ia1 = alphaByte(a1);
            drawn = true;
            vertex(out, LEFT[k], LEFT[k + 1], LEFT[k + 2], r, g, b, ia0, U[i], v0, dissolve, light);
            vertex(out, LEFT[j], LEFT[j + 1], LEFT[j + 2], r, g, b, ia1, U[i + 1], v0, dissolve, light);
            vertex(out, RIGHT[j], RIGHT[j + 1], RIGHT[j + 2], r, g, b, ia1, U[i + 1], v1, dissolve, light);
            vertex(out, RIGHT[k], RIGHT[k + 1], RIGHT[k + 2], r, g, b, ia0, U[i], v1, dissolve, light);
        }
        return drawn;
    }

    private static int alphaByte(float alpha) {
        return Math.min(255, Math.max(0, (int) (alpha * 255f + 0.5f)));
    }

    private static void vertex(VertexConsumer out, float x, float y, float z, int r, int g, int b, int a, float u,
            float v, int dissolve, int light) {
        out.addVertex(x, y, z).setColor(r, g, b, a).setUv(u, v).setUv1(dissolve, 0)
                .setUv2(light & 0xFFFF, light >> 16 & 0xFFFF).setNormal(0f, 1f, 0f);
    }
}
