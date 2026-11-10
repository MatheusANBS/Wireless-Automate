package io.github.matheusanbs.wirelessautomate.client.linker;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.packet.LinkSameBlockPayload;
import io.github.matheusanbs.wirelessautomate.range.RemoteCapabilities;
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
 * Ctrl (Cmd no Mac, o {@code Screen.hasControlDown}) + clique direito com o Vinculador na mão principal, no modo
 * Vincular, sem Shift e sem uma Tela de Sinal escolhida, numa máquina (bloco com a capability de algum tipo carregado,
 * como o {@code ConfiguratorItem.actsOn}) que não é roteador, Tela nem Doca: cancela o uso normal e manda o
 * {@link LinkSameBlockPayload} (o servidor vincula as máquinas do mesmo bloco no raio do roteador ou no cubo da Doca
 * escolhidos ou, se a clicada já é deles, desvincula, e refaz todas as conferências).
 * Uma vez por clique: segurando o botão, o jogo repete o uso a cada 4 ticks, e as repetições são canceladas sem pacote
 * até a tecla soltar. Correndo (andando com o Ctrl, que é a tecla padrão de correr), o gesto não vale e o clique segue como um
 * clique normal do Vinculador.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class LinkerSameBlockInput {
    /** A tecla de uso soltou desde o último Ctrl + clique. */
    private static boolean released = true;
    /** Gancho do e2e: o Ctrl de verdade não chega ao {@code Screen.hasControlDown()} na janela do e2e. */
    private static boolean forcedControl;
    /** Quantos pedidos o gesto mandou desde que o jogo abriu (gancho do e2e). */
    private static int sent;

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
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND
                || !(forcedControl || Screen.hasControlDown())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || sprinting(minecraft) || !applies(minecraft, hit)) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(released);
        if (released) {
            released = false;
            if (send(minecraft, hit)) {
                sent++;
            }
        }
    }

    /** Quantos pedidos o gesto mandou (gancho do e2e). */
    public static int sentCount() {
        return sent;
    }

    /** Gancho do e2e: faz de conta que o Ctrl está apertado (ou solta). */
    public static void forceControl(boolean down) {
        forcedControl = down;
    }

    /**
     * O jogador está correndo de fato. A tecla de correr apertada não basta: o padrão dela é o próprio Ctrl, e parado
     * ele não corre; só andando para a frente com o Ctrl o clique é o de quem corre, não o do gesto.
     */
    public static boolean sprinting(Minecraft minecraft) {
        return minecraft.player != null && minecraft.player.isSprinting();
    }

    /**
     * O gesto vale no bloco mirado: Vinculador na mão principal, modo Vincular, sem Shift, sem Tela escolhida, uma
     * máquina (capability de algum tipo carregado) que não é roteador, Tela nem Doca. Não confere o Ctrl nem a
     * corrida. Com uma Doca escolhida o gesto vale igual: o servidor manda o pedido à Doca.
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
        return !(entity instanceof RouterBlockEntity) && !(entity instanceof TvBlockEntity)
                && !(entity instanceof PatternDockBlockEntity) && RemoteCapabilities.any(minecraft.level, hit.getBlockPos());
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
