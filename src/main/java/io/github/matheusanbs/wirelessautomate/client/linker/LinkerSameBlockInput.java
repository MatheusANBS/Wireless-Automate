package io.github.matheusanbs.wirelessautomate.client.linker;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.packet.LinkSameBlockPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Ctrl + clique direito com o Vinculador na mão principal, no modo Vincular, sem Shift e sem uma Tela de Sinal
 * escolhida, num bloco que não é roteador nem Tela: cancela o uso normal e manda o {@link LinkSameBlockPayload} (o
 * servidor vincula as máquinas do mesmo bloco no raio e refaz todas as conferências). Uma vez por clique: segurando o
 * botão, o jogo repete o uso a cada 4 ticks, e as repetições são canceladas sem pacote até a tecla soltar.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class LinkerSameBlockInput {
    /** A tecla de uso soltou desde o último Ctrl + clique. */
    private static boolean released = true;

    private LinkerSameBlockInput() {
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!released && !Minecraft.getInstance().options.keyUse.isDown()) {
            released = true;
        }
    }

    @SubscribeEvent
    static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || !Screen.hasControlDown()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || !applies(minecraft, hit)) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(released);
        if (released) {
            released = false;
            send(minecraft, hit);
        }
    }

    /**
     * O gesto vale no bloco mirado: Vinculador na mão principal, modo Vincular, sem Shift, sem Tela escolhida, bloco
     * que não é roteador nem Tela. Não confere o Ctrl (o e2e chama direto).
     */
    public static boolean applies(Minecraft minecraft, BlockHitResult hit) {
        if (minecraft.player == null || minecraft.level == null || hit.getType() != HitResult.Type.BLOCK
                || minecraft.player.isSecondaryUseActive()) {
            return false;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (!held.is(ModItems.LINKER.get()) || LinkerItem.mode(held) != LinkerMode.LINK || LinkerItem.tv(held) != null) {
            return false;
        }
        BlockEntity entity = minecraft.level.getBlockEntity(hit.getBlockPos());
        return !(entity instanceof RouterBlockEntity) && !(entity instanceof TvBlockEntity);
    }

    /** Manda o pedido do bloco de {@code hit}; {@code false} se o servidor não conhece o pacote. */
    public static boolean send(Minecraft minecraft, BlockHitResult hit) {
        ClientPacketListener connection = minecraft.getConnection();
        LinkSameBlockPayload payload = new LinkSameBlockPayload(hit.getBlockPos());
        if (connection == null || !connection.hasChannel(payload.type())) {
            return false;
        }
        PacketDistributor.sendToServer(payload);
        return true;
    }
}
