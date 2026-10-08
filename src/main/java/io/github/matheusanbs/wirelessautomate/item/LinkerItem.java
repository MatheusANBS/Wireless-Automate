package io.github.matheusanbs.wirelessautomate.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.jetbrains.annotations.Nullable;

/**
 * Vinculador (controle): escolhe a rede ativa e coloca roteadores nela. Clique num roteador o põe
 * na rede ativa (criando uma se o jogador não tiver), nos dois modos. Clique no ar abre a tela
 * ({@link LinkerMenu}: rede ativa, abas, modo e, em Área, a prévia e o Vincular); Shift + clique no
 * ar alterna entre Único e Área ({@link ModDataComponents#LINKER_MODE}). Em Área, Shift + clique
 * em dois blocos marca os cantos ({@link ModDataComponents#LINKER_AREA}, ver {@link LinkerActions}).
 *
 * <p>As abas ({@link ModDataComponents#LINKER_TABS}, caixas na tela; Shift + roda do mouse percorre
 * os atalhos de {@link LinkerTabs#next}) escolhem o que o clique muda: só as abas marcadas do
 * roteador entram na rede. Químicos contam só com o Mekanism.
 *
 * <p>Com "Nenhuma (desvincular)" escolhida na tela ({@link ModDataComponents#LINKER_UNLINK}), os
 * mesmos gestos tiram as abas marcadas da rede em vez de pôr; nesse modo nenhuma rede é criada.
 */
public class LinkerItem extends Item {
    private static final String KEY = "item.wirelessautomate.linker.";

    /**
     * Codec do componente antigo {@code linker_type}: o nome do tipo em minúsculas. Aceita qualquer
     * tipo (o seletor antigo não tinha Químicos, mas não custa ler).
     */
    public static final Codec<ResourceType> TYPE_CODEC = Codec.STRING.comapFlatMap(name -> {
        for (ResourceType type : ResourceType.values()) {
            if (type.key().equals(name)) {
                return DataResult.success(type);
            }
        }
        return DataResult.error(() -> "tipo de Vinculador desconhecido: " + name);
    }, ResourceType::key);
    public static final StreamCodec<RegistryFriendlyByteBuf, ResourceType> TYPE_STREAM_CODEC =
            NeoForgeStreamCodecs.enumCodec(ResourceType.class);

    /** Codec das abas: lista de nomes em minúsculas; nomes desconhecidos são ignorados. */
    public static final Codec<LinkerTabs> TABS_CODEC =
            Codec.STRING.listOf().xmap(LinkerTabs::fromNames, LinkerTabs::names);
    public static final StreamCodec<ByteBuf, LinkerTabs> TABS_STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(LinkerTabs::new, LinkerTabs::mask);

    public LinkerItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ abas

    /**
     * Abas guardadas no Vinculador. Sem {@code linker_tabs}, um {@code linker_type} antigo vale como
     * aquela aba sozinha; sem nenhum dos dois, Todos.
     */
    public static LinkerTabs tabs(ItemStack stack) {
        LinkerTabs tabs = stack.get(ModDataComponents.LINKER_TABS.get());
        if (tabs != null) {
            return tabs;
        }
        ResourceType legacy = stack.get(ModDataComponents.LINKER_TYPE.get());
        return legacy == null ? LinkerTabs.ALL : LinkerTabs.of(legacy);
    }

    /** Grava as abas (Todos apaga o componente) e apaga o formato antigo. */
    public static void setTabs(ItemStack stack, LinkerTabs tabs) {
        stack.remove(ModDataComponents.LINKER_TYPE.get());
        if (tabs.equals(LinkerTabs.ALL)) {
            stack.remove(ModDataComponents.LINKER_TABS.get());
        } else {
            stack.set(ModDataComponents.LINKER_TABS.get(), tabs);
        }
    }

    /** As abas que valem agora (Químicos só com o Mekanism), na ordem das abas. */
    public static List<ResourceType> effectiveTabs(ItemStack stack) {
        return tabs(stack).effective(LoadedTypes.LIST);
    }

    /**
     * Shift + roda: avança ({@code direction > 0}) ou volta um atalho (Todos, Itens, Fluidos,
     * Energia, um tipo por mod presente); uma combinação que não é atalho vai para Todos. Devolve as
     * abas novas.
     */
    public static LinkerTabs cycleTabs(ItemStack stack, int direction) {
        LinkerTabs next = tabs(stack).next(direction, LoadedTypes.LIST);
        setTabs(stack, next);
        return next;
    }

    /** Nome de um tipo ("Todos" para {@code null}); o Configurador usa o mesmo. */
    public static Component typeName(@Nullable ResourceType type) {
        return type == null
                ? Component.translatable(KEY + "type.all")
                : Component.translatable("gui.wirelessautomate.router.type." + type.key());
    }

    /** "Todos", "Itens + Fluidos + Químicos" ou "nenhuma aba", pelas abas que valem agora. */
    public static Component tabsName(LinkerTabs tabs) {
        if (tabs.isAll(LoadedTypes.LIST)) {
            return typeName(null);
        }
        List<ResourceType> effective = tabs.effective(LoadedTypes.LIST);
        if (effective.isEmpty()) {
            return Component.translatable(KEY + "tabs.none");
        }
        MutableComponent text = Component.empty();
        for (int i = 0; i < effective.size(); i++) {
            if (i > 0) {
                text.append(" + ");
            }
            text.append(typeName(effective.get(i)));
        }
        return text;
    }

    // ------------------------------------------------------------------ desvincular

    /** O Vinculador está no modo desvincular ("Nenhuma" na escolha da rede). */
    public static boolean unlink(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.LINKER_UNLINK.get(), false);
    }

    public static void setUnlink(ItemStack stack, boolean unlink) {
        if (unlink) {
            stack.set(ModDataComponents.LINKER_UNLINK.get(), true);
        } else {
            stack.remove(ModDataComponents.LINKER_UNLINK.get());
        }
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
            LinkerActions.single(player, stack, router);
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(KEY + "tooltip.mode", modeName(mode(stack))).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(KEY + "tooltip.tabs", tabsName(tabs(stack))).withStyle(ChatFormatting.AQUA));
        if (unlink(stack)) {
            tooltip.add(Component.translatable(KEY + "tooltip.unlink").withStyle(ChatFormatting.GOLD));
        }
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
}
