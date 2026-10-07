package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Configurador (varinha) como pincel: Shift + clique direito num roteador copia a configuração
 * para o item, e clique direito noutro roteador cola. A cópia é relativa ao {@code facing} e leva a
 * rede junto, que só é colada se o jogador puder usá-la. Biblioteca e modo Área ficam para depois.
 */
public class ConfiguratorItem extends Item {
    private static final String KEY = "item.wirelessautomate.configurator.";

    public ConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof RouterBlockEntity router)) {
            // Shift + clique noutro bloco não pode cair no use() e descartar a cópia.
            return context.isSecondaryUseActive() ? InteractionResult.FAIL : InteractionResult.PASS;
        }
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            ItemStack stack = context.getItemInHand();
            if (context.isSecondaryUseActive()) {
                copy(player, stack, router);
            } else {
                paste(player, stack, router, context.getClickedPos());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void copy(ServerPlayer player, ItemStack stack, RouterBlockEntity router) {
        RouterPreset preset = RouterPreset.copyOf(router);
        WaNetwork network = network(player, preset);
        if (network == null) {
            // Rede removida: o id órfão não serve para nada colado noutro roteador.
            preset = preset.withoutNetwork();
        }
        stack.set(ModDataComponents.PRESET.get(), preset);
        player.displayClientMessage(network == null
                ? Component.translatable(KEY + "copied", preset.configuredFaces())
                : Component.translatable(KEY + "copied_network", preset.configuredFaces(), network.displayName()),
                true);
        player.level().playSound(null, router.getBlockPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS,
                0.8F, 1.2F);
    }

    private static void paste(ServerPlayer player, ItemStack stack, RouterBlockEntity router, BlockPos pos) {
        RouterPreset preset = stack.get(ModDataComponents.PRESET.get());
        if (preset == null) {
            player.displayClientMessage(Component.translatable(KEY + "empty"), true);
            return;
        }
        Component message;
        if (preset.network() == null) {
            message = Component.translatable(KEY + "pasted");
        } else {
            WaNetwork network = network(player, preset);
            if (network == null) {
                preset = preset.withoutNetwork();
                message = Component.translatable(KEY + "pasted_network_missing");
            } else if (!network.owner().equals(player.getUUID()) && !player.hasPermissions(2)) {
                preset = preset.withoutNetwork();
                message = Component.translatable(KEY + "pasted_network_denied", network.displayName());
            } else {
                message = Component.translatable(KEY + "pasted_network", network.displayName());
            }
        }
        preset.applyTo(router);
        player.displayClientMessage(message, true);
        player.level().playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.4F);
    }

    private static @Nullable WaNetwork network(ServerPlayer player, RouterPreset preset) {
        return preset.network() == null ? null : NetworkSavedData.get(player.server).network(preset.network());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(stack);
        }
        // TODO(v1): Shift + clique direito no ar abre a biblioteca de presets; por ora descarta a cópia.
        if (!level.isClientSide) {
            boolean had = stack.remove(ModDataComponents.PRESET.get()) != null;
            player.displayClientMessage(Component.translatable(KEY + (had ? "cleared" : "empty")), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.PRESET.get()) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        RouterPreset preset = stack.get(ModDataComponents.PRESET.get());
        if (preset == null) {
            tooltip.add(Component.translatable(KEY + "tooltip.empty").withStyle(ChatFormatting.GRAY));
        } else {
            String key = preset.network() == null ? "tooltip.preset" : "tooltip.preset_network";
            tooltip.add(Component.translatable(KEY + key, preset.configuredFaces()).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable(KEY + "tooltip.copy").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "tooltip.paste").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "tooltip.clear").withStyle(ChatFormatting.DARK_GRAY));
    }
}
