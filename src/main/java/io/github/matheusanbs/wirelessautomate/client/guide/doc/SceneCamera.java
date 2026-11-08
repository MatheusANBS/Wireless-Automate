package io.github.matheusanbs.wirelessautomate.client.guide.doc;

/** A câmera isométrica de uma cena, em graus. */
public record SceneCamera(double yaw, double pitch) {
    /** A de quase todas as cenas das páginas, para a que não declara nenhuma. */
    public static final SceneCamera DEFAULT = new SceneCamera(200, 30);
}
