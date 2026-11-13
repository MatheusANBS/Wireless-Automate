package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.dock.DockChoice;
import io.github.matheusanbs.wirelessautomate.dock.DockLinks;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerOpen;
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
 * <p><b>Vincular</b>: clique num roteador o escolhe ({@link ModDataComponents#LINKER_ROUTER}; de novo nele, solta);
 * clique em outro bloco vincula ou desvincula essa máquina a ele ({@link RangeLinks}); Ctrl + clique numa máquina
 * vincula as do mesmo bloco que se tocam com ela, no raio, ou, se ela já está vinculada, desvincula as que se tocam
 * ({@code client/linker/LinkerSameBlockInput} e {@link io.github.matheusanbs.wirelessautomate.range.SameBlockLinks}).
 * O clique no ar mostra o que está escolhido, e Shift + clique no ar o solta ({@link LinkerActions}).
 *
 * <p>Com uma Tela de Sinal escolhida ({@link ModDataComponents#LINKER_TV}, por um clique nela, que esquece o
 * roteador), o clique num armazenamento do mod o liga ou desliga da Tela ({@link TvLinks}).
 *
 * <p>Com uma Doca de Padrões escolhida ({@link ModDataComponents#LINKER_DOCK}, por um clique numa face dela, que
 * escolhe aquele lado e esquece o roteador e a Tela), o clique numa máquina a vincula ou desvincula daquele lado, e o
 * Ctrl + clique faz o mesmo com as do mesmo bloco que se tocam com ela ({@link DockLinks}, {@code dock/DockSameBlock}).
 *
 * <p><b>Abrir</b>: clique numa máquina abre a tela do roteador que chega nela ({@link LinkerOpen}).
 */
public class LinkerItem extends Item {
    private static final String KEY = "item.wirelessautomate.linker.";

    public LinkerItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ cliques

    /**
     * O clique (sem Shift) num bloco é do Vinculador antes de ser do bloco (senão clicar numa fornalha a
     * abriria). Vincular: num roteador, escolhe o roteador; numa Doca, escolhe a Doca e o lado clicado; em outro
     * bloco, vincula ou desvincula a máquina (da Tela, da Doca ou do roteador escolhido).
     * Abrir: abre o roteador que chega na máquina. Shift + clique vai para o {@link #useOn}.
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
            } else if (level.getBlockEntity(context.getClickedPos()) instanceof PatternDockBlockEntity dock) {
                DockLinks.choose(player, stack, dock, context.getClickedFace().ordinal());
            } else if (tv(stack) != null) {
                TvLinks.toggle(player, stack, context.getClickedPos());
            } else if (dock(stack) != null) {
                DockLinks.toggle(player, stack, context.getClickedPos());
            } else {
                RangeLinks.toggle(player, stack, context.getClickedPos());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Só chega aqui com Shift, depois do {@code PASS} do {@link #onItemUseFirst}. Consome o clique, sem agir: um
     * {@code PASS} faria o jogo seguir para o {@link #use} como se o clique fosse no ar, e o Shift + clique num bloco
     * soltaria o roteador. Abrir explica que o modo se troca com Shift + roda.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player
                && mode(context.getItemInHand()) == LinkerMode.OPEN) {
            player.displayClientMessage(Component.translatable(KEY + "open.shift_hint"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * No ar. Vincular: com Shift solta o roteador, a Tela ou a Doca escolhidos ({@link LinkerActions#release}); sem
     * Shift mostra o que está escolhido ({@link LinkerActions#showChosen}). Abrir: explica o gesto. O modo se troca
     * com Shift + roda do mouse ({@code client/HeldScrollHandler}).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (mode(stack) != LinkerMode.LINK) {
                serverPlayer.displayClientMessage(Component.translatable(KEY + "open.hint"), true);
            } else if (serverPlayer.isSecondaryUseActive()) {
                LinkerActions.release(serverPlayer, stack);
            } else {
                LinkerActions.showChosen(serverPlayer, stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // ------------------------------------------------------------------ modo, roteador, Tela e Doca

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

    /** Doca de Padrões e lado escolhidos ao vincular; {@code null} se nenhuma. */
    public static @Nullable DockChoice dock(ItemStack stack) {
        return stack.get(ModDataComponents.LINKER_DOCK.get());
    }

    /** {@code null} esquece a Doca escolhida. */
    public static void setDock(ItemStack stack, @Nullable DockChoice dock) {
        if (dock == null) {
            stack.remove(ModDataComponents.LINKER_DOCK.get());
        } else {
            stack.set(ModDataComponents.LINKER_DOCK.get(), dock);
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
            DockChoice dock = dock(stack);
            if (dock != null) {
                tooltip.add(Component.translatable(KEY + "tooltip.dock", LinkerActions.position(dock.pos().pos()),
                        DockLinks.sideName(dock.side())).withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(Component.translatable(KEY + "tooltip.link_click").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(KEY + "tooltip.same_block").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(KEY + "tooltip.release").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable(KEY + "tooltip.toggle").withStyle(ChatFormatting.DARK_GRAY));
    }
}
