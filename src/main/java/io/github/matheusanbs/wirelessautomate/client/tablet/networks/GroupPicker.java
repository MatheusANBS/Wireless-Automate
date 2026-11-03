package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static io.github.matheusanbs.wirelessautomate.client.tablet.networks.NetworksText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.DropdownModal;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * As listas que abrem sobre o véu na aba Redes, montadas na hora do clique: o seletor "Grupo" da rede ("Sem grupo" e
 * os grupos que o jogador gerencia, com ícone) e o "+ pôr uma rede neste grupo" do grupo (as redes que o jogador
 * gerencia e que estão fora dele).
 */
final class GroupPicker {
    /** A largura do seletor "Grupo" e da lista dele (mockup {@code rede_detalhe}). */
    static final int W = 112;
    private static final int NETWORKS_W = 150;

    private GroupPicker() {
    }

    /**
     * A lista do seletor "Grupo" em ({@code x}, {@code y}) da tela; {@code current} é o grupo em que a rede aparece.
     * Escolher manda {@code move} com o grupo (ou {@code null}, "Sem grupo"), só se mudou.
     */
    static DropdownModal groups(int x, int y, TabletSnapshot s, Map<UUID, ItemStack> icons, @Nullable UUID current,
            Consumer<@Nullable UUID> move, DropdownModal.Anchor anchor) {
        List<DropdownModal.Row> rows = new ArrayList<>();
        rows.add(new DropdownModal.Row(null, tr("networks.no_group"), null, GuiPaint.DISABLED, true));
        for (GroupView group : s.groups()) {
            if (group.manageable()) {
                rows.add(new DropdownModal.Row(group.id(), Component.literal(group.name()), icons.get(group.id()), 0,
                        false));
            }
        }
        return new DropdownModal(x, y, W, rows, current, id -> {
            if (!Objects.equals(id, current)) {
                move.accept(id);
            }
        }, anchor);
    }

    /** As redes que o jogador gerencia fora do grupo, com o quadradinho da cor; escolher manda {@code add}. */
    static DropdownModal networks(int x, int y, TabletSnapshot s, GroupView group, Consumer<UUID> add,
            DropdownModal.Anchor anchor) {
        List<DropdownModal.Row> rows = new ArrayList<>();
        for (NetworkView network : s.networks()) {
            if (network.manageable() && !group.networks().contains(network.id())) {
                rows.add(new DropdownModal.Row(network.id(), Component.literal(network.name()), null,
                        0xFF000000 | network.color(), false));
            }
        }
        if (rows.isEmpty()) {
            rows.add(new DropdownModal.Row(null, tr("nets.add_to_group.none"), null, GuiPaint.DISABLED, true));
        }
        return new DropdownModal(x, y, NETWORKS_W, rows, null, id -> {
            if (id != null) {
                add.accept(id);
            }
        }, anchor);
    }
}
