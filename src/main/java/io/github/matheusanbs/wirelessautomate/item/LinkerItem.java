package io.github.matheusanbs.wirelessautomate.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
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
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.jetbrains.annotations.Nullable;

/**
 * Vinculador (controle): escolhe a rede ativa e coloca roteadores nela. Clique num roteador o põe
 * na rede ativa (criando uma se o jogador não tiver), nos dois modos. Clique no ar abre a tela
 * ({@link LinkerMenu}: rede ativa, tipo, modo e, em Área, a prévia e o Vincular); Shift + clique no
 * ar alterna entre Único e Área ({@link ModDataComponents#LINKER_MODE}). Em Área, Shift + clique
 * em dois blocos marca os cantos ({@link ModDataComponents#LINKER_AREA}, ver {@link LinkerActions}).
 *
 * <p>O seletor de tipo ({@link ModDataComponents#LINKER_TYPE}, trocado com Shift + roda do mouse)
 * escolhe o que o clique vincula: sem tipo (Todos) põe todas as abas do roteador na rede; com um
 * tipo, só a aba daquele tipo.
 */
public class LinkerItem extends Item {
    private static final String KEY = "item.wirelessautomate.linker.";
    /**
     * Ordem do seletor; {@code null} é Todos. Químicos não têm posição própria: entram com Todos
     * (que põe todas as abas na rede) ou pela aba Químicos da tela do roteador.
     */
    private static final ResourceType[] CYCLE = {null, ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY};

    /** Codec do componente: o nome do tipo em minúsculas, só os tipos do seletor. */
    public static final Codec<ResourceType> TYPE_CODEC = Codec.STRING.comapFlatMap(name -> {
        for (ResourceType type : CYCLE) {
            if (type != null && key(type).equals(name)) {
                return DataResult.success(type);
            }
        }
        return DataResult.error(() -> "tipo de Vinculador desconhecido: " + name);
    }, LinkerItem::key);
    public static final StreamCodec<RegistryFriendlyByteBuf, ResourceType> TYPE_STREAM_CODEC =
            NeoForgeStreamCodecs.enumCodec(ResourceType.class);

    public LinkerItem(Properties properties) {
        super(properties);
    }

    /** Tipo que o Vinculador vincula; {@code null} = todos. */
    public static @Nullable ResourceType type(ItemStack stack) {
        return stack.get(ModDataComponents.LINKER_TYPE.get());
    }

    /**
     * Avança ({@code direction > 0}) ou volta o seletor: Todos → Itens → Fluidos → Energia → Todos.
     * Devolve o tipo novo ({@code null} = todos).
     */
    public static @Nullable ResourceType cycleType(ItemStack stack, int direction) {
        ResourceType current = type(stack);
        int index = 0;
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == current) {
                index = i;
                break;
            }
        }
        ResourceType next = CYCLE[Math.floorMod(index + Integer.signum(direction), CYCLE.length)];
        if (next == null) {
            stack.remove(ModDataComponents.LINKER_TYPE.get());
        } else {
            stack.set(ModDataComponents.LINKER_TYPE.get(), next);
        }
        return next;
    }

    /** Nome do tipo do seletor ("Todos" para {@code null}). */
    public static Component typeName(@Nullable ResourceType type) {
        return type == null
                ? Component.translatable(KEY + "type.all")
                : Component.translatable("gui.wirelessautomate.router.type." + key(type));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        Player user = context.getPlayer();
        boolean sneaking = user != null && user.isSecondaryUseActive();
        if (sneaking && mode(stack) == LinkerMode.AREA) {
            // Área: Shift + clique em qualquer bloco marca um canto
            if (!level.isClientSide && user instanceof ServerPlayer player) {
                LinkerActions.markCorner(player, stack, context.getClickedPos());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof RouterBlockEntity router)) {
            if (sneaking) {
                // Único: Shift + clique num bloco não alterna o modo (isso é no ar); só explica
                if (!level.isClientSide && user instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.translatable(KEY + "area_hint"), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            // clique num bloco qualquer segue para o use(): abre a tela
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && user instanceof ServerPlayer player) {
            WaNetwork network = NetworkSavedData.get(player.server).activeOrCreate(player);
            ResourceType type = type(stack);
            if (!network.canUse(player)) {
                player.displayClientMessage(Component.translatable(KEY + "foreign", network.displayName()), true);
            } else if (LinkerActions.inNetwork(router, type, network.id())) {
                player.displayClientMessage(type == null
                        ? Component.translatable(KEY + "already", network.displayName())
                        : Component.translatable(KEY + "already_type", typeName(type), network.displayName()), true);
            } else {
                LinkerActions.apply(router, type, network.id());
                player.displayClientMessage(type == null
                        ? Component.translatable(KEY + "linked", network.displayName())
                        : Component.translatable(KEY + "linked_type", typeName(type), network.displayName()), true);
                level.playSound(null, context.getClickedPos(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.BLOCKS, 0.5F, 1.4F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** No ar: Shift + clique alterna Único/Área; clique abre a tela. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isSecondaryUseActive()) {
                LinkerMode mode = mode(stack).toggled();
                setMode(stack, mode);
                serverPlayer.displayClientMessage(Component.translatable(KEY + "mode", modeName(mode)), true);
            } else {
                LinkerMenu.open(serverPlayer, hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // ------------------------------------------------------------------ modo e área

    /** Modo do Vinculador; sem o componente, Único. */
    public static LinkerMode mode(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.LINKER_MODE.get(), LinkerMode.SINGLE);
    }

    public static void setMode(ItemStack stack, LinkerMode mode) {
        if (mode == LinkerMode.SINGLE) {
            stack.remove(ModDataComponents.LINKER_MODE.get());
        } else {
            stack.set(ModDataComponents.LINKER_MODE.get(), mode);
        }
    }

    public static Component modeName(LinkerMode mode) {
        return Component.translatable(KEY + "mode." + mode.getSerializedName());
    }

    /** Cantos marcados; {@code null} se nenhum. */
    public static @Nullable LinkerArea area(ItemStack stack) {
        return stack.get(ModDataComponents.LINKER_AREA.get());
    }

    /** {@code null} apaga os cantos. */
    public static void setArea(ItemStack stack, @Nullable LinkerArea area) {
        if (area == null) {
            stack.remove(ModDataComponents.LINKER_AREA.get());
        } else {
            stack.set(ModDataComponents.LINKER_AREA.get(), area);
        }
    }

    /** {@code null} = Todos. */
    public static void setType(ItemStack stack, @Nullable ResourceType type) {
        if (type == null) {
            stack.remove(ModDataComponents.LINKER_TYPE.get());
        } else {
            stack.set(ModDataComponents.LINKER_TYPE.get(), type);
        }
    }

    /** O tipo está no seletor (químicos ficam de fora até o Mekanism entrar). */
    public static boolean selectable(ResourceType type) {
        for (ResourceType each : CYCLE) {
            if (each == type) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(KEY + "tooltip.mode", modeName(mode(stack))).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(KEY + "tooltip.type", typeName(type(stack))).withStyle(ChatFormatting.AQUA));
        LinkerArea area = area(stack);
        if (area != null) {
            tooltip.add(Component.translatable(KEY + "tooltip.corner1", LinkerActions.position(area.first()))
                    .withStyle(ChatFormatting.GRAY));
            area.second().ifPresent(second -> tooltip.add(Component.translatable(KEY + "tooltip.corner2",
                    LinkerActions.position(second)).withStyle(ChatFormatting.GRAY)));
        }
        tooltip.add(Component.translatable(KEY + "tooltip.open").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "tooltip.toggle").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "tooltip.cycle").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String key(ResourceType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
