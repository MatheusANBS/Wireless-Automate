package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerOpen;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvLinks;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
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
import org.jetbrains.annotations.Nullable;

/**
 * Vinculador (controle), com dois modos ({@link LinkerMode}, componente
 * {@link ModDataComponents#LINKER_MODE}); Shift + roda do mouse passa ao outro.
 *
 * <p><b>Vincular</b>: clique num roteador o escolhe ({@link ModDataComponents#LINKER_ROUTER}); clique em outro
 * bloco vincula ou desvincula essa máquina a ele ({@link RangeLinks}). Shift + clique em dois blocos marca os
 * cantos de uma área ({@link ModDataComponents#LINKER_AREA}, ver {@link LinkerActions}); clique no ar abre a
 * tela ({@link LinkerMenu}), que vincula as máquinas da área e escolhe vincular ou desvincular
 * ({@link ModDataComponents#LINKER_UNLINK}: os mesmos gestos tiram em vez de pôr).
 *
 * <p>Com uma Tela de Sinal escolhida ({@link ModDataComponents#LINKER_TV}, por um clique nela, que esquece o
 * roteador), o clique num armazenamento do mod o liga ou desliga da Tela ({@link TvLinks}).
 *
 * <p><b>Abrir</b>: clique numa máquina abre a tela do roteador que chega nela ({@link LinkerOpen}).
 */
public class LinkerItem extends Item {
    private static final String KEY = "item.wirelessautomate.linker.";

    public LinkerItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ desvincular

    /** O Vinculador está no modo desvincular (os gestos tiram as máquinas do roteador em vez de pôr). */
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

    // ------------------------------------------------------------------ cliques

    /**
     * O clique (sem Shift) num bloco é do Vinculador antes de ser do bloco (senão clicar numa fornalha a
     * abriria). Vincular: num roteador, escolhe o roteador; em outro bloco, vincula ou desvincula a máquina.
     * Abrir: abre o roteador que chega na máquina. Shift + clique segue o caminho normal ({@link #useOn}).
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            if (mode(stack) == LinkerMode.OPEN) {
                LinkerOpen.click(player, stack, context.getClickedPos());
            } else if (level.getBlockEntity(context.getClickedPos()) instanceof RouterBlockEntity router) {
                RangeLinks.choose(player, stack, router);
            } else if (level.getBlockEntity(context.getClickedPos()) instanceof TvBlockEntity tv) {
                TvLinks.choose(player, stack, tv);
            } else if (tv(stack) != null) {
                TvLinks.toggle(player, stack, context.getClickedPos());
            } else {
                RangeLinks.toggle(player, stack, context.getClickedPos());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Só chega aqui com Shift: vincular marca um canto; abrir só explica (o modo se troca com Shift + roda). */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            if (mode(stack).marksArea()) {
                LinkerActions.markCorner(player, stack, context.getClickedPos());
            } else {
                player.displayClientMessage(Component.translatable(KEY + "open.shift_hint"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * No ar, com ou sem Shift: abre a tela (Vincular) ou explica o gesto (Abrir). O modo se troca com Shift + roda
     * do mouse ({@code client/HeldScrollHandler}).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (mode(stack) == LinkerMode.LINK) {
                LinkerMenu.open(serverPlayer, hand);
            } else {
                serverPlayer.displayClientMessage(Component.translatable(KEY + "open.hint"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // ------------------------------------------------------------------ modo, roteador e área

    /** Modo do Vinculador; sem o componente, Vincular. */
    public static LinkerMode mode(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.LINKER_MODE.get(), LinkerMode.LINK);
    }

    public static void setMode(ItemStack stack, LinkerMode mode) {
        if (mode == LinkerMode.LINK) {
            stack.remove(ModDataComponents.LINKER_MODE.get());
        } else {
            stack.set(ModDataComponents.LINKER_MODE.get(), mode);
        }
    }

    public static Component modeName(LinkerMode mode) {
        return Component.translatable(KEY + "mode." + mode.getSerializedName());
    }

    /** Roteador escolhido ao vincular; {@code null} se nenhum. */
    public static @Nullable GlobalPos router(ItemStack stack) {
        return stack.get(ModDataComponents.LINKER_ROUTER.get());
    }

    /** {@code null} esquece o roteador escolhido. */
    public static void setRouter(ItemStack stack, @Nullable GlobalPos router) {
        if (router == null) {
            stack.remove(ModDataComponents.LINKER_ROUTER.get());
        } else {
            stack.set(ModDataComponents.LINKER_ROUTER.get(), router);
        }
    }

    /** Tela de Sinal escolhida ao ligar armazenamentos; {@code null} se nenhuma. */
    public static @Nullable GlobalPos tv(ItemStack stack) {
        return stack.get(ModDataComponents.LINKER_TV.get());
    }

    /** {@code null} esquece a Tela escolhida. */
    public static void setTv(ItemStack stack, @Nullable GlobalPos tv) {
        if (tv == null) {
            stack.remove(ModDataComponents.LINKER_TV.get());
        } else {
            stack.set(ModDataComponents.LINKER_TV.get(), tv);
        }
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
        LinkerMode mode = mode(stack);
        tooltip.add(Component.translatable(KEY + "tooltip.mode", modeName(mode)).withStyle(ChatFormatting.AQUA));
        if (mode == LinkerMode.OPEN) {
            tooltip.add(Component.translatable(KEY + "tooltip.open_click").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            if (unlink(stack)) {
                tooltip.add(Component.translatable(KEY + "tooltip.unlink").withStyle(ChatFormatting.GOLD));
            }
            GlobalPos router = router(stack);
            if (router != null) {
                tooltip.add(Component.translatable(KEY + "tooltip.router", LinkerActions.position(router.pos()))
                        .withStyle(ChatFormatting.GRAY));
            }
            GlobalPos tv = tv(stack);
            if (tv != null) {
                tooltip.add(Component.translatable(KEY + "tooltip.tv", LinkerActions.position(tv.pos()),
                        tv.dimension().location().toString()).withStyle(ChatFormatting.GRAY));
            }
            LinkerArea area = area(stack);
            if (area != null) {
                tooltip.add(Component.translatable(KEY + "tooltip.corner1", LinkerActions.position(area.first()))
                        .withStyle(ChatFormatting.GRAY));
                area.second().ifPresent(second -> tooltip.add(Component.translatable(KEY + "tooltip.corner2",
                        LinkerActions.position(second)).withStyle(ChatFormatting.GRAY)));
            }
            tooltip.add(Component.translatable(KEY + "tooltip.link_click").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(KEY + "tooltip.open").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable(KEY + "tooltip.toggle").withStyle(ChatFormatting.DARK_GRAY));
    }
}
