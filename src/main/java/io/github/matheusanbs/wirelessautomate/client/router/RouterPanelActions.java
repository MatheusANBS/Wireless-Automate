package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.FaceActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.ClearIdleNetworksPayload;
import io.github.matheusanbs.wirelessautomate.packet.RenameTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.SetNetworkPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * O que o painel do nó manda da tela do roteador: pelo menu dela, ou, numa captura sem servidor, só no
 * snapshot local (modo, prioridade, redstone, rede, o nome de um grupo ou de uma máquina e o tirar a rede das faces
 * sem modo, sem o Desfazer).
 */
final class RouterPanelActions implements NodePanel.Actions {
    private final RouterMenu menu;
    private final boolean preview;

    RouterPanelActions(RouterMenu menu, boolean preview) {
        this.menu = menu;
        this.preview = preview;
    }

    @Override
    public int containerId() {
        return menu.containerId;
    }

    @Override
    public void send(CustomPacketPayload payload) {
        if (preview) {
            applyLocally(payload);
        } else {
            PacketDistributor.sendToServer(payload);
        }
    }

    @Override
    public void clearIdleNetworks(boolean undo) {
        if (!preview) {
            PacketDistributor.sendToServer(new ClearIdleNetworksPayload(menu.containerId, undo));
        } else if (!undo) {
            List<FaceView> faces = new ArrayList<>(menu.snapshot().faces());
            faces.replaceAll(face -> face.mode() == PortMode.NONE ? face.withNetwork(Optional.empty()) : face);
            menu.applySnapshot(menu.snapshot().withFaces(List.copyOf(faces)));
        }
    }

    @Override
    public int cardCount() {
        int count = 0;
        for (int i = 0; i < RouterMenu.CARD_SLOT_COUNT; i++) {
            if (menu.getSlot(i).hasItem()) {
                count++;
            }
        }
        return count;
    }

    /** Captura sem servidor: modo, prioridade, redstone, rede e nomes mudam só no snapshot local. */
    private void applyLocally(CustomPacketPayload payload) {
        RouterSnapshot snapshot = menu.snapshot();
        if (payload instanceof RenameTargetPayload p) {
            menu.applySnapshot(LabelEditor.renamed(snapshot, p));
            return;
        }
        List<FaceView> faces = new ArrayList<>(snapshot.faces());
        if (payload instanceof SetFacePayload p) {
            int index = RouterSnapshot.index(p.resource(), p.face());
            FaceView current = faces.get(index);
            faces.set(index, new FaceView(p.mode(), p.priority(), p.redstone(), current.slots(), current.filterSize(),
                    current.blacklist(), current.network()));
        } else if (payload instanceof FaceActionPayload p) {
            if (p.action() == FaceActionPayload.Action.CLEAR) {
                for (ResourceType type : p.allTypes() ? LoadedTypes.LIST : List.of(p.resource())) {
                    for (Direction side : Direction.values()) {
                        int index = RouterSnapshot.index(type, side);
                        FaceView current = faces.get(index);
                        faces.set(index, new FaceView(PortMode.NONE, current.priority(), current.redstone(),
                                current.slots(), current.filterSize(), current.blacklist(), current.network()));
                    }
                }
            } else {
                FaceView from = faces.get(RouterSnapshot.index(p.resource(), p.from()));
                int index = RouterSnapshot.index(p.resource(), p.to());
                faces.set(index, new FaceView(from.mode(), from.priority(), from.redstone(), faces.get(index).slots(),
                        from.filterSize(), from.blacklist(), from.network()));
            }
        } else if (payload instanceof SetNetworkPayload p) {
            int index = RouterSnapshot.index(p.resource(), p.face());
            faces.set(index, faces.get(index).withNetwork(p.network()));
        } else {
            return;
        }
        menu.applySnapshot(snapshot.withFaces(List.copyOf(faces)));
    }
}
