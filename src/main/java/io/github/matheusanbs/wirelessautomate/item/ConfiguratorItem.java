package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.preset.ConfiguratorArea;
import io.github.matheusanbs.wirelessautomate.preset.PresetApplier;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
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
 * Configurador (varinha), sem tela. Guarda uma cópia só, no próprio item: Shift + clique direito
 * num roteador copia a configuração (faces, filtros, prioridades, redstone e a rede de cada aba,
 * relativa ao {@code facing}) e o bloco da máquina dele. Dois modos, trocados com Shift + clique
 * direito no ar, como no Vinculador:
 * <ul>
 *   <li><b>Pincel</b> (padrão): clique direito num roteador cola nele.</li>
 *   <li><b>Área</b>: clique direito em dois blocos marca os cantos; clique direito no ar cola em
 *       todos os roteadores da área presos ao mesmo tipo de máquina ({@link ConfiguratorArea});
 * </ul>
 * Shift + clique direito num bloco que não é roteador limpa a varinha (a cópia e a área).
 * A rede de cada aba só é colada se o jogador puder usá-la ({@link PresetApplier}).
 */
public class ConfiguratorItem extends Item {
    private static final String KEY = "item.wirelessautomate.configurator.";

    public ConfiguratorItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ estado no item

    /** Modo da varinha; sem o componente, pincel ({@link LinkerMode#SINGLE}). */
    public static LinkerMode mode(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CONFIGURATOR_MODE.get(), LinkerMode.SINGLE);
    }

    public static void setMode(ItemStack stack, LinkerMode mode) {
        if (mode == LinkerMode.SINGLE) {
            stack.remove(ModDataComponents.CONFIGURATOR_MODE.get());
        } else {
            stack.set(ModDataComponents.CONFIGURATOR_MODE.get(), mode);
        }
    }

    public static Component modeName(LinkerMode mode) {
        return Component.translatable(KEY + "mode." + (mode == LinkerMode.AREA ? "area" : "brush"));
    }

    public static @Nullable LinkerArea area(ItemStack stack) {
        return stack.get(ModDataComponents.CONFIGURATOR_AREA.get());
    }

    public static void setArea(ItemStack stack, @Nullable LinkerArea area) {
        if (area == null) {
            stack.remove(ModDataComponents.CONFIGURATOR_AREA.get());
        } else {
            stack.set(ModDataComponents.CONFIGURATOR_AREA.get(), area);
        }
    }

    /** Bloco da máquina do roteador copiado; {@code null} se não há cópia (ou ela é de antes dessa regra). */
    public static @Nullable ResourceLocation machine(ItemStack stack) {
        return stack.get(ModDataComponents.CONFIGURATOR_MACHINE.get());
    }

    /** Apaga a cópia (e a máquina dela) e a área; o modo fica. Devolve se havia algo. */
    public static boolean clear(ItemStack stack) {
        boolean had = stack.has(ModDataComponents.PRESET.get()) || area(stack) != null;
        stack.remove(ModDataComponents.PRESET.get());
        stack.remove(ModDataComponents.CONFIGURATOR_MACHINE.get());
        setArea(stack, null);
        return had;
    }

    // ------------------------------------------------------------------ cliques

    /**
     * Modo Área: o clique num bloco é da varinha antes de ser do bloco (senão clicar num baú o
     * abriria) e marca um canto. Shift + clique segue o caminho normal: num roteador, copia.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (mode(stack) != LinkerMode.AREA || context.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            player.displayClientMessage(ConfiguratorArea.markCorner(player, stack, context.getClickedPos()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof RouterBlockEntity router)) {
            if (!context.isSecondaryUseActive()) {
                return InteractionResult.PASS;
            }
            // Shift + clique num bloco que não é roteador limpa a varinha, nos dois modos (e não cai
            // no use(), que trocaria o modo).
            if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable(
                        KEY + (clear(context.getItemInHand()) ? "cleared" : "nothing_to_clear")), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
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

    /** No ar: Shift + clique troca o modo; no modo Área, clique cola na área marcada. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() && mode(stack) != LinkerMode.AREA) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isSecondaryUseActive()) {
                LinkerMode mode = mode(stack).toggled();
                setMode(stack, mode);
                serverPlayer.displayClientMessage(Component.translatable(KEY + "mode", modeName(mode)), true);
            } else {
                RouterPreset preset = stack.get(ModDataComponents.PRESET.get());
                serverPlayer.displayClientMessage(preset == null
                        ? Component.translatable(KEY + "empty")
                        : ConfiguratorArea.message(ConfiguratorArea.paste(serverPlayer, stack, preset)), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
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
        stack.set(ModDataComponents.CONFIGURATOR_MACHINE.get(), ConfiguratorArea.machine(player.serverLevel(), router));
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
     * No pincel, a máquina não importa: colar num roteador é sempre escolha do jogador.
     */
    private static void paste(ServerPlayer player, ItemStack stack, RouterBlockEntity router, BlockPos pos) {
        RouterPreset preset = stack.get(ModDataComponents.PRESET.get());
        if (preset == null) {
            player.displayClientMessage(Component.translatable(KEY + "empty"), true);
            return;
        }
        PresetApplier.Checked checked = PresetApplier.check(player, preset);
        NetworkSavedData data = NetworkSavedData.get(player.server);
        checked.preset().applyTo(router);
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

    // ------------------------------------------------------------------ aparência

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.PRESET.get()) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        // Estado: o que está copiado, o modo e a área.
        RouterPreset preset = stack.get(ModDataComponents.PRESET.get());
        if (preset == null) {
            tooltip.add(Component.translatable(KEY + "tooltip.empty").withStyle(ChatFormatting.GRAY));
        } else {
            int networks = preset.distinctNetworks().size();
            String key = networks == 0 ? "tooltip.preset" : networks == 1 ? "tooltip.preset_network" : "tooltip.preset_networks";
            tooltip.add(Component.translatable(KEY + key, preset.configuredFaces()).withStyle(ChatFormatting.AQUA));
            ResourceLocation machine = machine(stack);
            if (machine != null) {
                tooltip.add(Component.translatable(KEY + "tooltip.machine",
                        BuiltInRegistries.BLOCK.get(machine).getName()).withStyle(ChatFormatting.AQUA));
            }
        }
        LinkerMode mode = mode(stack);
        String size = ConfiguratorArea.size(stack);
        tooltip.add((mode == LinkerMode.AREA && size != null
                ? Component.translatable(KEY + "tooltip.mode_area", modeName(mode), size)
                : Component.translatable(KEY + "tooltip.mode", modeName(mode))).withStyle(ChatFormatting.GOLD));

        // Comandos do modo atual, na ordem de uso: copiar, colar, limpar, trocar de modo.
        tooltip.add(Component.translatable(KEY + "tooltip.copy").withStyle(ChatFormatting.DARK_GRAY));
        if (mode == LinkerMode.AREA) {
            tooltip.add(Component.translatable(KEY + "tooltip.mark").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(KEY + "tooltip.paste_area").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable(KEY + "tooltip.paste").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable(KEY + "tooltip.clear").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + (mode == LinkerMode.AREA ? "tooltip.to_brush" : "tooltip.to_area"))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
