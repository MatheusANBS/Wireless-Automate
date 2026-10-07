package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.ConfiguratorMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.preset.AreaActions;
import io.github.matheusanbs.wirelessautomate.preset.AreaClipboard;
import io.github.matheusanbs.wirelessautomate.preset.AreaSelection;
import io.github.matheusanbs.wirelessautomate.preset.PresetApplier;
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
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Configurador (varinha). No modo pincel, Shift + clique direito num roteador copia a configuração
 * para o item e clique direito noutro roteador cola; a cópia é relativa ao {@code facing} e leva a
 * rede de cada tipo (aba), colada só se o jogador puder usá-la ({@link PresetApplier}). No modo
 * Área, Shift + clique num bloco marca os cantos e clique num bloco escolhe onde colar a cópia de
 * área (clicar de novo no mesmo bloco cola); veja {@link AreaActions}. Shift + clique direito no ar
 * abre a tela ({@link ConfiguratorMenu}): biblioteca de presets, código {@code WA1:} e área.
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
        PresetApplier.Checked checked = PresetApplier.check(player, preset);
        NetworkSavedData data = NetworkSavedData.get(player.server);
        preset = checked.preset();
        preset.applyTo(router);
        player.displayClientMessage(pasteMessage(data, checked.applied(), checked.keptTypes(), checked.kept(),
                checked.denied()), true);
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
        // jogadores falsos (máquinas de outros mods) não abrem telas
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer)) {
            ConfiguratorMenu.open(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Modo Área: o clique num bloco é da varinha antes de ser do bloco (senão clicar num baú o
     * abriria). Shift + clique marca um canto; clique escolhe a origem da colagem ou cola.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (AreaActions.selection(stack).mode() != AreaSelection.Mode.AREA) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            BlockPos pos = context.getClickedPos();
            Component message = context.isSecondaryUseActive()
                    ? AreaActions.markCorner(player, stack, pos)
                    : AreaActions.clickAnchor(player, stack, pos);
            player.displayClientMessage(message, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
        AreaSelection selection = AreaActions.selection(stack);
        AreaClipboard clipboard = AreaActions.clipboard(stack);
        if (clipboard != null) {
            tooltip.add(Component.translatable(KEY + "tooltip.clipboard", clipboard.size()).withStyle(ChatFormatting.AQUA));
        }
        if (selection.mode() == AreaSelection.Mode.AREA) {
            tooltip.add(Component.translatable(KEY + "tooltip.mode_area").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable(KEY + "tooltip.mark").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(KEY + "tooltip.anchor").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable(KEY + "tooltip.copy").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(KEY + "tooltip.paste").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable(KEY + "tooltip.open").withStyle(ChatFormatting.DARK_GRAY));
    }
}
