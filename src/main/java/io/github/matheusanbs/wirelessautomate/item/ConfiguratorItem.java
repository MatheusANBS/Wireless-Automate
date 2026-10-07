package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
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
 * rede de cada tipo (aba) junto; cada uma só é colada se o jogador puder usá-la. Biblioteca e modo
 * Área ficam para depois.
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
        NetworkSavedData data = NetworkSavedData.get(player.server);
        for (ResourceType type : ResourceType.values()) {
            UUID id = preset.network(type);
            if (id != null && data.network(id) == null) {
                // Rede removida: o id órfão não serve para nada colado noutro roteador.
                preset = preset.withoutNetwork(type);
            }
        }
        stack.set(ModDataComponents.PRESET.get(), preset);
        List<Component> names = names(data, preset.distinctNetworks());
        Component message;
        if (names.isEmpty()) {
            message = Component.translatable(KEY + "copied", preset.configuredFaces());
        } else if (names.size() == 1) {
            message = Component.translatable(KEY + "copied_network", preset.configuredFaces(), names.getFirst());
        } else {
            message = Component.translatable(KEY + "copied_networks", preset.configuredFaces(), list(names));
        }
        player.displayClientMessage(message, true);
        player.level().playSound(null, router.getBlockPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS,
                0.8F, 1.2F);
    }

    /**
     * Cola as faces e a rede de cada tipo que o jogador pode usar (dono ou operador nível 2). A rede
     * de um tipo que não pode (de outro dono ou removida) fica como estava no roteador, com aviso.
     */
    private static void paste(ServerPlayer player, ItemStack stack, RouterBlockEntity router, BlockPos pos) {
        RouterPreset preset = stack.get(ModDataComponents.PRESET.get());
        if (preset == null) {
            player.displayClientMessage(Component.translatable(KEY + "empty"), true);
            return;
        }
        NetworkSavedData data = NetworkSavedData.get(player.server);
        List<UUID> applied = new ArrayList<>();
        List<ResourceType> keptTypes = new ArrayList<>();
        List<UUID> kept = new ArrayList<>();
        WaNetwork denied = null;
        for (ResourceType type : ResourceType.values()) {
            UUID id = preset.network(type);
            if (id == null) {
                continue;
            }
            WaNetwork network = data.network(id);
            if (network != null && ModPayloads.canUse(player, network)) {
                if (!applied.contains(id)) {
                    applied.add(id);
                }
                continue;
            }
            preset = preset.withoutNetwork(type);
            keptTypes.add(type);
            if (!kept.contains(id)) {
                kept.add(id);
            }
            if (network != null) {
                denied = network;
            }
        }
        preset.applyTo(router);
        player.displayClientMessage(pasteMessage(data, applied, keptTypes, kept, denied), true);
        player.level().playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.4F);
    }

    private static Component pasteMessage(NetworkSavedData data, List<UUID> applied, List<ResourceType> keptTypes,
            List<UUID> kept, @Nullable WaNetwork denied) {
        if (kept.isEmpty()) {
            List<Component> names = names(data, applied);
            return switch (names.size()) {
                case 0 -> Component.translatable(KEY + "pasted");
                case 1 -> Component.translatable(KEY + "pasted_network", names.getFirst());
                default -> Component.translatable(KEY + "pasted_networks", list(names));
            };
        }
        if (applied.isEmpty() && kept.size() == 1) {
            // Uma rede só, recusada: as mensagens de sempre.
            return denied != null
                    ? Component.translatable(KEY + "pasted_network_denied", denied.displayName())
                    : Component.translatable(KEY + "pasted_network_missing");
        }
        List<Component> tabs = new ArrayList<>(keptTypes.size());
        for (ResourceType type : keptTypes) {
            tabs.add(LinkerItem.typeName(type));
        }
        return Component.translatable(KEY + "pasted_network_partial", list(tabs));
    }

    private static List<Component> names(NetworkSavedData data, List<UUID> ids) {
        List<Component> names = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            WaNetwork network = data.network(id);
            if (network != null) {
                names.add(network.displayName());
            }
        }
        return names;
    }

    private static Component list(List<Component> parts) {
        return ComponentUtils.formatList(parts, Component.literal(", "));
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
            int networks = preset.distinctNetworks().size();
            String key = networks == 0 ? "tooltip.preset" : networks == 1 ? "tooltip.preset_network" : "tooltip.preset_networks";
            tooltip.add(Component.translatable(KEY + key, preset.configuredFaces()).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable(KEY + "tooltip.copy").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "tooltip.paste").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "tooltip.clear").withStyle(ChatFormatting.DARK_GRAY));
    }
}
