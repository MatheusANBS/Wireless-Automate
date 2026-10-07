package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Vinculador (controle): escolhe a rede ativa e coloca roteadores nela. Por enquanto só o modo
 * Único: clique num roteador o põe na rede ativa (criando uma se o jogador não tiver), e clique no
 * ar mostra qual é a rede ativa. Modo Área e tela ficam para depois (ver roadmap).
 */
public class LinkerItem extends Item {
    public LinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof RouterBlockEntity router)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            WaNetwork network = NetworkSavedData.get(player.server).activeOrCreate(player);
            if (network.id().equals(router.networkId())) {
                player.displayClientMessage(Component.translatable("item.wirelessautomate.linker.already",
                        network.displayName()), true);
            } else {
                router.setNetworkId(network.id());
                player.displayClientMessage(Component.translatable("item.wirelessautomate.linker.linked",
                        network.displayName()), true);
                level.playSound(null, context.getClickedPos(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.BLOCKS, 0.5F, 1.4F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkSavedData data = NetworkSavedData.get(serverPlayer.server);
            UUID active = data.activeNetwork(serverPlayer.getUUID());
            WaNetwork network = active == null ? null : data.network(active);
            serverPlayer.displayClientMessage(network == null
                    ? Component.translatable("item.wirelessautomate.linker.no_active")
                    : Component.translatable("item.wirelessautomate.linker.active", network.displayName()), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
