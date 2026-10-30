package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A aba e a face que o jogador escolheu no {@link NodePanel}, para reabrir a tela ou o nó no mesmo lugar: a aba vale
 * para qualquer roteador; a face, no mesmo roteador e alvo (no alcance, a face canônica dele). Estática, da sessão.
 */
final class PanelMemory {
    /** O roteador de uma face lembrada. */
    private record Spot(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final int REMEMBERED_FACES = 64;

    /** A última aba escolhida (vale para qualquer roteador). */
    private static @Nullable ResourceType lastType;

    /** A última face escolhida de cada alvo de cada roteador, para reabrir a tela ou o nó na mesma face. */
    private static final FaceMemory<Spot, Direction> lastFaces = new FaceMemory<>(REMEMBERED_FACES);

    private PanelMemory() {
    }

    /** Escolha do jogador: lembra a aba e a face do alvo no roteador. Mostrar um painel sem escolha não passa por aqui. */
    static void remember(PanelModel model) {
        RouterSnapshot s = model.snapshot();
        lastType = model.type();
        lastFaces.put(new Spot(s.dimension(), s.pos()), s.target(), model.face());
    }

    /** Põe no {@code model} a aba lembrada e a face lembrada deste roteador e alvo, se houver. */
    static void recall(PanelModel model, RouterSnapshot snapshot) {
        if (lastType != null && LoadedTypes.LIST.contains(lastType)) {
            model.setType(lastType);
        }
        Direction face = lastFaces.get(new Spot(snapshot.dimension(), snapshot.pos()), snapshot.target());
        if (face != null) {
            model.setFace(face);
        }
    }
}
