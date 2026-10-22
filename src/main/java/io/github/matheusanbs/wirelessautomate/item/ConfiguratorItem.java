package io.github.matheusanbs.wirelessautomate.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.preset.PasteTypes;
import io.github.matheusanbs.wirelessautomate.preset.PresetApplier;
import io.github.matheusanbs.wirelessautomate.preset.PresetPaste;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.jetbrains.annotations.Nullable;

/**
 * Configurador (pincel), sem tela. Guarda uma cópia só, no próprio item: Shift + clique direito num
 * roteador copia a configuração (faces, filtros, prioridades, redstone e a rede de cada aba, relativa
 * ao {@code facing}) e o bloco da máquina dele; clique direito num roteador cola nele. Shift + clique
 * direito num bloco que não é roteador limpa a cópia. A rede de cada face só é colada se o jogador
 * puder usá-la e só sai de uma rede que ele gerencia ({@link PresetApplier}, {@link PresetPaste}).
 *
 * <p>O seletor de tipo ({@link ModDataComponents#CONFIGURATOR_TYPE}, trocado com Shift + roda do
 * mouse ou pela roda radial) escolhe o que o colar aplica: sem tipo (Todos), todas as abas; com um
 * tipo, só as faces daquela aba (com as redes delas), e as outras abas do roteador ficam como estavam. Copiar
 * sempre copia tudo.
 */
public class ConfiguratorItem extends Item {
    private static final String KEY = "item.wirelessautomate.configurator.";

    /**
     * Codec do componente: o nome do tipo em minúsculas. Aceita todos os tipos, inclusive Químicos
     * sem o Mekanism (o item não se perde ao trocar de instância; o seletor só pula o tipo).
     */
    public static final Codec<ResourceType> TYPE_CODEC = Codec.STRING.comapFlatMap(name -> {
        for (ResourceType type : ResourceType.values()) {
            if (type.key().equals(name)) {
                return DataResult.success(type);
            }
        }
        return DataResult.error(() -> "tipo de Configurador desconhecido: " + name);
    }, ResourceType::key);
    public static final StreamCodec<RegistryFriendlyByteBuf, ResourceType> TYPE_STREAM_CODEC =
            NeoForgeStreamCodecs.enumCodec(ResourceType.class);

    public ConfiguratorItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ seletor de tipo

    /** Tipo (aba) que o Configurador cola; {@code null} = todos. */
    public static @Nullable ResourceType type(ItemStack stack) {
        return stack.get(ModDataComponents.CONFIGURATOR_TYPE.get());
    }

    /** {@code null} = Todos (sem o componente). */
    public static void setType(ItemStack stack, @Nullable ResourceType type) {
        if (type == null) {
            stack.remove(ModDataComponents.CONFIGURATOR_TYPE.get());
        } else {
            stack.set(ModDataComponents.CONFIGURATOR_TYPE.get(), type);
        }
    }

    /**
     * Avança ({@code direction > 0}) ou volta o seletor: Todos → um tipo por vez, na ordem do registro e só os carregados (Químicos com o Mekanism, Source
     * com o Ars Nouveau) → Todos. Devolve o tipo novo ({@code null} = todos).
     */
    public static @Nullable ResourceType cycleType(ItemStack stack, int direction) {
        ResourceType next = PasteTypes.next(type(stack), direction, LoadedTypes.LIST);
        setType(stack, next);
        return next;
    }

    /** Nome do tipo do seletor ("Todos" para {@code null}). */
    public static Component typeName(@Nullable ResourceType type) {
        return type == null
                ? Component.translatable(KEY + "type.all")
                : Component.translatable("gui.wirelessautomate.router.type." + type.key());
    }

    /** "só a aba Fluidos", para juntar às mensagens de colar; {@code null} com Todos. */
    public static @Nullable Component onlyTab(@Nullable ResourceType type) {
        return type == null ? null : Component.translatable(KEY + "only_tab", typeName(type));
    }

    // ------------------------------------------------------------------ estado no item

    /** Bloco da máquina do roteador copiado; {@code null} se não há cópia (ou ela é de antes dessa regra). */
    public static @Nullable ResourceLocation machine(ItemStack stack) {
        return stack.get(ModDataComponents.CONFIGURATOR_MACHINE.get());
    }

    /** Apaga a cópia e a máquina dela. Devolve se havia algo. */
    public static boolean clear(ItemStack stack) {
        boolean had = stack.has(ModDataComponents.PRESET.get());
        stack.remove(ModDataComponents.PRESET.get());
        stack.remove(ModDataComponents.CONFIGURATOR_MACHINE.get());
        return had;
    }

    // ------------------------------------------------------------------ cliques

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof RouterBlockEntity router)) {
            if (!context.isSecondaryUseActive()) {
                return InteractionResult.PASS;
            }
            // Shift + clique num bloco que não é roteador limpa a cópia.
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

    private static void copy(ServerPlayer player, ItemStack stack, RouterBlockEntity router) {
        RouterPreset preset = PresetPaste.copy(player, router);
        NetworkSavedData data = NetworkSavedData.get(player.server);
        stack.set(ModDataComponents.PRESET.get(), preset);
        stack.set(ModDataComponents.CONFIGURATOR_MACHINE.get(), PresetPaste.machine(player.serverLevel(), router));
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
     * Cola as faces, cada uma com a sua rede se o jogador pode usá-la (dono ou operador nível 2). A
     * face cuja rede ele não pode usar (de outro dono ou removida) fica com a rede que tinha, com aviso.
     * A máquina não importa: colar num roteador é sempre escolha do jogador. Com um tipo
     * no seletor, só aquela aba é colada (e só as redes das faces dela entram na regra).
     */
    private static void paste(ServerPlayer player, ItemStack stack, RouterBlockEntity router, BlockPos pos) {
        RouterPreset preset = stack.get(ModDataComponents.PRESET.get());
        if (preset == null) {
            player.displayClientMessage(Component.translatable(KEY + "empty"), true);
            return;
        }
        ResourceType type = type(stack);
        PresetApplier.Checked checked = PresetPaste.paste(player, router, preset, type);
        NetworkSavedData data = NetworkSavedData.get(player.server);
        Component message = pasteMessage(data, checked.applied(), checked.keptTypes(), checked.kept(),
                checked.denied());
        Component only = onlyTab(type);
        player.displayClientMessage(only == null ? message
                : Component.empty().append(message).append(" · ").append(only), true);
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
            tabs.add(typeName(type));
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
        // Estado: o que está copiado e o tipo colado.
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
                        RouterBlock.machineName(BuiltInRegistries.BLOCK.get(machine))).withStyle(ChatFormatting.AQUA));
            }
        }
        tooltip.add(Component.translatable(KEY + "tooltip.type", typeName(type(stack))).withStyle(ChatFormatting.GOLD));

        // Comandos, na ordem de uso: copiar, colar, limpar, a roda e o tipo. A tecla da roda vai por
        // Component.keybind (sem classe de cliente aqui).
        tooltip.add(Component.translatable(KEY + "tooltip.copy").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "tooltip.paste").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "tooltip.clear").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "tooltip.wheel",
                Component.keybind("key.wirelessautomate.configurator_wheel")).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "tooltip.cycle").withStyle(ChatFormatting.DARK_GRAY));
    }
}
