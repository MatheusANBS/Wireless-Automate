package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.tuner.ConfiguratorTuner;
import io.github.matheusanbs.wirelessautomate.range.RemoteCapabilities;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
    /** As linhas do tooltip, na ordem do gesto. */
    private static final String[] TOOLTIP = {"analyze", "apply", "group", "modes"};

    public ConfiguratorItem(Properties properties) {
        super(properties);
    }

    /**
     * O clique num bloco é do Configurador antes de ser do bloco (senão clicar numa fornalha a abriria), com ou sem
     * Shift. Seguem o caminho normal, sem aviso: o Configurador na outra mão, um roteador (abre a tela dele) e um bloco
     * sem capability de nenhum tipo carregado (porta, alavanca, terra, e também os com block entity, como o atril, a
     * mesa de encantamento e a placa). Em descanso depois de uma análise ({@code getCooldowns}, que o
     * {@code onItemUseFirst} não confere sozinho), o clique é engolido, mas conta para a trava do botão segurado
     * ({@link ConfiguratorTuner#held}).
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!actsOn(context.getHand(), level, pos)) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (player != null && player.getCooldowns().isOnCooldown(this)) {
            if (player instanceof ServerPlayer serverPlayer) {
                ConfiguratorTuner.held(serverPlayer);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ConfiguratorTuner.click(serverPlayer, pos, context.isSecondaryUseActive());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * O Configurador age no bloco em {@code pos}: na mão principal, num bloco que não é roteador e que expõe a
     * capability de algum tipo carregado. A mesma condição vale no cliente ({@code TunerInput}).
     */
    public static boolean actsOn(InteractionHand hand, Level level, BlockPos pos) {
        return hand == InteractionHand.MAIN_HAND && !(level.getBlockEntity(pos) instanceof RouterBlockEntity)
                && RemoteCapabilities.any(level, pos);
    }

    /** No ar (ou num bloco em que ele não age): com um clique novo, a proposta pendente some. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            ConfiguratorTuner.useInAir(serverPlayer);
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        for (String line : TOOLTIP) {
            tooltip.add(Component.translatable("item.wirelessautomate.configurator.tooltip." + line)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
