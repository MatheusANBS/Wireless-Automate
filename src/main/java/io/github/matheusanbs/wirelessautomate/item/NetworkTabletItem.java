package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.menu.TabletClick;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Tablet de rede: gerencia nós, redes e grupos à distância. Clique direito no ar (ou a tecla de
 * atalho, com o Tablet em qualquer lugar do inventário) abre a tela ({@link TabletMenu}); numa máquina,
 * abre o nó dela ({@link TabletClick}).
 */
public class NetworkTabletItem extends Item {
    public NetworkTabletItem(Properties properties) {
        super(properties);
    }

    /**
     * Clique numa máquina, antes da interação do bloco (que abriria o forno ou o baú): abre o nó dela
     * ({@link TabletClick}). Um roteador segue para a tela dele. No cliente devolve {@code PASS} (não sabe
     * se há roteador); o {@link #use} que vem em seguida não reabre se o nó já abriu.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player
                && !(level.getBlockState(context.getClickedPos()).getBlock() instanceof RouterBlock)
                && TabletClick.open(player, level, context.getClickedPos())) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Sem roteador, a interação do bloco ou o {@link #use} seguem; o aviso só sai se chegou até aqui. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player) {
            TabletClick.notifyNotLinked(player, level, context.getClickedPos());
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && !(serverPlayer.containerMenu instanceof TabletMenu)) {
            TabletMenu.open(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wirelessautomate.network_tablet.tooltip")
                .withStyle(style -> style.withColor(0x93A0AE)));
    }
}
