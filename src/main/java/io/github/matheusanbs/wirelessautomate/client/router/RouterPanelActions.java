package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.packet.SetFacePayload;
import io.github.matheusanbs.wirelessautomate.packet.SetNetworkPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * O que o painel do nó manda da tela do roteador: pelo menu dela, ou, numa captura sem servidor, só no
 * snapshot local (modo, prioridade, redstone e rede).
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
    public int cardCount() {
        int count = 0;
        for (int i = 0; i < RouterMenu.CARD_SLOT_COUNT; i++) {
            if (menu.getSlot(i).hasItem()) {
                count++;
            }
        }
        return count;
    }

    /** Captura sem servidor: modo, prioridade, redstone e rede mudam só no snapshot local. */
    private void applyLocally(CustomPacketPayload payload) {
        RouterSnapshot snapshot = menu.snapshot();
        List<FaceView> faces = new ArrayList<>(snapshot.faces());
        if (payload instanceof SetFacePayload p) {
            int index = RouterSnapshot.index(p.resource(), p.face());
            FaceView current = faces.get(index);
            faces.set(index, new FaceView(p.mode(), p.priority(), p.redstone(), current.slots(), current.filterSize(),
                    current.blacklist(), current.network()));
        } else if (payload instanceof SetNetworkPayload p) {
            int index = RouterSnapshot.index(p.resource(), p.face());
            faces.set(index, faces.get(index).withNetwork(p.network()));
        } else {
            return;
        }
        menu.applySnapshot(snapshot.withFaces(List.copyOf(faces)));
    }
}
