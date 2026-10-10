package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Tablet de rede: gerencia nós, redes e grupos à distância. Clique direito no ar (ou a tecla de
 * atalho, com o Tablet em qualquer lugar do inventário) abre a tela ({@link TabletMenu}).
 */
public class NetworkTabletItem extends Item {
    public NetworkTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            TabletMenu.open(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wirelessautomate.network_tablet.tooltip")
                .withStyle(style -> style.withColor(0x93A0AE)));
    }
}
