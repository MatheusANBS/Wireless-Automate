package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

/**
 * O Afinador do Configurador numa máquina de um lado da Doca de Padrões: o segundo clique refaz o perfil do lado
 * inteiro, com Shift ou sem (sem exceção por máquina, decisão do dono, 10/11/2026). O gesto (análise, pintura, prazo)
 * é o do {@code ConfiguratorTuner}, que só chega aqui quando nenhum roteador que o jogador pode editar alcança a
 * máquina: numa máquina de um roteador e de uma Doca, vale o roteador.
 */
public final class DockTuner {
    private static final String KEY = "item.wirelessautomate.configurator.tuner.";

    private DockTuner() {
    }

    /** A Doca e a máquina do lado em que o clique caiu. */
    public record Target(PatternDockBlockEntity dock, DockMachine machine) {
        /** A frente guardada da máquina ({@code -1}: sem frente). */
        public int front() {
            return machine.front();
        }
    }

    /**
     * A primeira Doca carregada, no nível do jogador, que tem a máquina em {@code pos} num lado e em que o jogador
     * pode mexer ({@code mayBuild}, {@code mayInteract} e as proteções de outros mods na Doca, como no roteador);
     * {@code null} sem nenhuma. Vale para a máquina ativa ou não (fora do alcance, além do limite). Só no clique: uma
     * consulta ao {@link DockIndex}.
     */
    public static @Nullable Target find(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (PatternDockBlockEntity dock : DockIndex.get().at(level, pos)) {
            if (accessible(player, level, dock)) {
                DockMachine machine = dock.machineAt(pos);
                if (machine != null && dock.side(machine.side()) != null) {
                    return new Target(dock, machine);
                }
            }
        }
        return null;
    }

    /** Alguma Doca carregada tem a máquina em {@code pos} num lado, com acesso ou sem. */
    public static boolean linked(ServerLevel level, BlockPos pos) {
        for (PatternDockBlockEntity dock : DockIndex.get().at(level, pos)) {
            if (!dock.isRemoved() && dock.machineAt(pos) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean accessible(ServerPlayer player, ServerLevel level, PatternDockBlockEntity dock) {
        BlockPos at = dock.getBlockPos();
        return !dock.isRemoved() && dock.getLevel() == level && level.isLoaded(at) && RangeLinks.mayEdit(player, at)
                && RangeLinks.clickAllowed(player, InteractionHand.MAIN_HAND, at);
    }

    /** O texto da proposta numa máquina do lado: as faces e "clique de novo para aplicar no lado Norte da Doca". */
    public static Component proposal(Target target, Component faces) {
        return Component.translatable(KEY + "proposal_dock", faces, DockLinks.sideName(target.machine().side()));
    }

    /**
     * Aplica os modos (por face absoluta da máquina clicada) no perfil do lado, convertidos pela frente dela; as faces
     * sem modo ficam. Devolve o aviso da action bar.
     */
    public static Component apply(Target target, Map<ResourceType, PortMode[]> modes) {
        int side = target.machine().side();
        DockSide dockSide = target.dock().side(side);
        Component name = DockLinks.sideName(side);
        if (dockSide == null) {
            return Component.translatable(KEY + "unchanged_dock", name);
        }
        int count = Integer.bitCount(DockLinks.applyModes(dockSide.profile(), modes, target.front()));
        if (count == 0) {
            return Component.translatable(KEY + "unchanged_dock", name);
        }
        target.dock().saveChanged();
        return Component.translatable(KEY + "applied_dock", name,
                Component.translatable(KEY + (count == 1 ? "faces.one" : "faces"), count));
    }
}
