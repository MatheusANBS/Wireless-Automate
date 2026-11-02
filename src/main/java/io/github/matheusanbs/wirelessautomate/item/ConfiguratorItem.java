package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.tuner.ConfiguratorTuner;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 * O Configurador: o Afinador ({@link ConfiguratorTuner}). Clique numa máquina analisa e propõe os modos das faces;
 * o segundo clique na mesma máquina aplica (Shift: no perfil do grupo); clique no ar esquece a proposta. Num roteador,
 * o clique segue para o bloco e abre a tela dele. Os componentes {@code configurator_*} continuam registrados para os
 * itens dos mundos que já os têm.
 */
public class ConfiguratorItem extends Item {
    public ConfiguratorItem(Properties properties) {
        super(properties);
    }

    /**
     * O clique num bloco é do Configurador antes de ser do bloco (senão clicar numa fornalha a abriria), com ou sem
     * Shift; num roteador, ou com o Configurador na outra mão, segue o caminho normal.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        if (context.getHand() != InteractionHand.MAIN_HAND
                || level.getBlockEntity(context.getClickedPos()) instanceof RouterBlockEntity) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            ConfiguratorTuner.click(player, context.getClickedPos(), context.isSecondaryUseActive());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** No ar: a proposta pendente some. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            ConfiguratorTuner.cancel(serverPlayer);
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.wirelessautomate.configurator.tooltip.no_function")
                .withStyle(ChatFormatting.GRAY));
    }
}
