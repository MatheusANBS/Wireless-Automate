package io.github.matheusanbs.wirelessautomate.client.fx;

import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * O quadro dos efeitos: a posição da câmera, a direita e o alto dela no mundo (para os sprites que olham para a
 * câmera), as matrizes do quadro e o tempo. Um só, reaproveitado a cada quadro pelo {@link FxStages}; os efeitos
 * leem os campos e desenham com coordenadas relativas à câmera ({@code mundo − cam}).
 */
public final class FxContext {
    public double camX;
    public double camY;
    public double camZ;
    /** A direita da câmera no mundo. */
    public float rx;
    public float ry;
    public float rz;
    /** O alto da câmera no mundo. */
    public float ux;
    public float uy;
    public float uz;
    /** Cópias das matrizes do quadro (a rotação da câmera e a projeção do mundo). */
    public final Matrix4f modelView = new Matrix4f();
    public final Matrix4f projection = new Matrix4f();
    public float partialTick;
    public long gameTime;
    /** Rascunho do {@link #ndc}; só a thread de render o usa. */
    private final Vector4f clip = new Vector4f();

    /** Pega a câmera e as matrizes do quadro; sem alocar. */
    public void begin(Camera camera, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, float partialTick,
            long gameTime) {
        camX = camera.getPosition().x;
        camY = camera.getPosition().y;
        camZ = camera.getPosition().z;
        // a câmera guarda a esquerda; a direita é o oposto
        Vector3f left = camera.getLeftVector();
        rx = -left.x();
        ry = -left.y();
        rz = -left.z();
        Vector3f up = camera.getUpVector();
        ux = up.x();
        uy = up.y();
        uz = up.z();
        modelView.set(modelViewMatrix);
        projection.set(projectionMatrix);
        this.partialTick = partialTick;
        this.gameTime = gameTime;
    }

    /** Copia só as matrizes (para o gancho tardio, no último estágio do mundo). */
    void copyMatrices(Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        modelView.set(modelViewMatrix);
        projection.set(projectionMatrix);
    }

    /**
     * A posição de um ponto relativo à câmera (em blocos) em coordenadas normalizadas da tela (de −1 a 1, y para cima),
     * pela {@code projeção × modelview} do quadro. Atrás da câmera o resultado não vale nada: devolve 0, 0.
     */
    public void ndc(float x, float y, float z, float[] out) {
        clip.set(x, y, z, 1f);
        modelView.transform(clip);
        projection.transform(clip);
        if (clip.w > 1e-6f) {
            out[0] = clip.x / clip.w;
            out[1] = clip.y / clip.w;
        } else {
            out[0] = 0;
            out[1] = 0;
        }
    }
}
