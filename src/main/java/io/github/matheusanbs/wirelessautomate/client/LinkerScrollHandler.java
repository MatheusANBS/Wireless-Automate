package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.packet.CycleLinkerTypePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shift + roda do mouse com o Vinculador na mão principal troca o tipo que ele vincula (Todos,
 * Itens, Fluidos, Energia). Só no jogo, sem tela aberta; o evento é cancelado para a roda não trocar
 * o slot da hotbar. Como na hotbar, rolar para baixo vai para o próximo. O servidor troca o tipo e
 * mostra o novo na action bar.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class LinkerScrollHandler {
    private LinkerScrollHandler() {
    }

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || event.getScrollDeltaY() == 0
                || !minecraft.options.keyShift.isDown()
                || !minecraft.player.getMainHandItem().is(ModItems.LINKER.get())) {
            return;
        }
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || !connection.hasChannel(CycleLinkerTypePayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToServer(new CycleLinkerTypePayload(event.getScrollDeltaY() > 0 ? -1 : 1));
        event.setCanceled(true);
    }
}
