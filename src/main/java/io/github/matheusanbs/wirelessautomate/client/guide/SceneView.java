package io.github.matheusanbs.wirelessautomate.client.guide;

import io.github.matheusanbs.wirelessautomate.client.guide.doc.SceneCamera;

/**
 * A vista de uma cena do livro: o yaw, o pitch e o zoom que o jogador deu a ela arrastando e rodando o mouse, a partir
 * da {@link SceneCamera} e do {@code zoom} da página. Sem Minecraft: a cena só lê os números e a {@link #version()}, que
 * muda a cada mudança, para refazer a matriz do quadro só quando ela mudou.
 */
public final class SceneView {
    /** Graus por pixel de tela arrastado, no yaw e no pitch. */
    public static final double DEGREES_PER_PIXEL = 0.5;
    /** O fator do zoom por clique da roda. */
    public static final double ZOOM_STEP = 1.1;
    /** O zoom fica entre estes fatores do enquadramento da página. */
    public static final double ZOOM_MIN = 0.5;
    public static final double ZOOM_MAX = 3;
    /** O pitch fica entre o horizonte e a vista de cima. */
    public static final double PITCH_MIN = 0;
    public static final double PITCH_MAX = 90;

    /** O yaw, o pitch e o zoom num instante; igual quando a vista é a mesma. */
    public record State(double yaw, double pitch, double zoom) {
    }

    private final double homeYaw;
    private final double homePitch;
    private final double frame;
    private double yaw;
    private double pitch;
    private double zoom = 1;
    private int version;

    /** A vista da página: a câmera dela e o {@code zoom} da {@code GameScene} (o enquadramento, {@link #frame}). */
    public SceneView(SceneCamera camera, double pageZoom) {
        this.homeYaw = camera.yaw();
        this.homePitch = Math.clamp(camera.pitch(), PITCH_MIN, PITCH_MAX);
        this.frame = frame(pageZoom);
        this.yaw = homeYaw;
        this.pitch = homePitch;
    }

    /**
     * O fator da página sobre o enquadramento automático (a cena inteira na moldura): as páginas usam 4 nas cenas de 3 ou 4
     * blocos e 3 nas maiores; 4 dá a vista inteira, como na etapa 1, e cada ponto a menos afasta um oitavo,
     * deixando folga para a cena grande girar sem sair da moldura. Preso entre 0,5 e 1.
     */
    public static double frame(double pageZoom) {
        return Math.clamp(0.5 + pageZoom / 8, 0.5, 1);
    }

    /** Gira pelo arrasto, em pixels de tela: para a direita, o yaw sobe; para baixo, o pitch sobe (preso). */
    public void drag(double dxPixels, double dyPixels) {
        double y = (yaw + dxPixels * DEGREES_PER_PIXEL) % 360;
        double p = Math.clamp(pitch + dyPixels * DEGREES_PER_PIXEL, PITCH_MIN, PITCH_MAX);
        set(y < 0 ? y + 360 : y, p, zoom);
    }

    /** A roda: cada clique para cima ({@code delta > 0}) aproxima um passo, para baixo afasta; preso. */
    public void scroll(double delta) {
        set(yaw, pitch, Math.clamp(zoom * Math.pow(ZOOM_STEP, delta), ZOOM_MIN, ZOOM_MAX));
    }

    /** Volta à vista da página. */
    public void reset() {
        set(homeYaw, homePitch, 1);
    }

    private void set(double y, double p, double z) {
        if (y != yaw || p != pitch || z != zoom) {
            yaw = y;
            pitch = p;
            zoom = z;
            version++;
        }
    }

    public double yaw() {
        return yaw;
    }

    public double pitch() {
        return pitch;
    }

    /** O zoom do jogador, relativo ao enquadramento da página (1 = o da página). */
    public double zoom() {
        return zoom;
    }

    /** O fator total sobre o enquadramento automático: o da página vezes o zoom do jogador. */
    public double scale() {
        return frame * zoom;
    }

    /** Muda a cada mudança da vista. */
    public int version() {
        return version;
    }

    public State state() {
        return new State(yaw, pitch, zoom);
    }

    /** A vista é a da página. */
    public boolean isHome() {
        return state().equals(new State(homeYaw, homePitch, 1));
    }
}
