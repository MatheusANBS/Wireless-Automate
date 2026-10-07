package io.github.matheusanbs.wirelessautomate.preset;

import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * As ações de área do Configurador sobre a pilha do item, vindas dos cliques no mundo
 * ({@code ConfiguratorItem}) ou da tela ({@code ConfiguratorMenu}). Cada uma muda os componentes da
 * pilha, age nos roteadores e devolve a mensagem para o jogador. Só no servidor.
 */
public final class AreaActions {
    private static final String KEY = "item.wirelessautomate.configurator.area.";

    public static AreaSelection selection(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CONFIGURATOR_AREA.get(), AreaSelection.EMPTY);
    }

    public static @Nullable AreaClipboard clipboard(ItemStack stack) {
        return stack.get(ModDataComponents.AREA_CLIPBOARD.get());
    }

    private static void setSelection(ItemStack stack, AreaSelection selection) {
        if (selection.equals(AreaSelection.EMPTY)) {
            stack.remove(ModDataComponents.CONFIGURATOR_AREA.get());
        } else {
            stack.set(ModDataComponents.CONFIGURATOR_AREA.get(), selection);
        }
    }

    public static Component setMode(ItemStack stack, AreaSelection.Mode mode) {
        setSelection(stack, selection(stack).withMode(mode));
        return Component.translatable(KEY + "mode." + mode.getSerializedName());
    }

    /** Shift + clique num bloco no modo Área: marca o primeiro ou o segundo canto. */
    public static Component markCorner(ServerPlayer player, ItemStack stack, BlockPos pos) {
        AreaSelection selection = selection(stack).mark(GlobalPos.of(player.level().dimension(), pos.immutable()));
        setSelection(stack, selection);
        player.level().playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F,
                selection.corner2().isPresent() ? 1.4F : 1.1F);
        BoundingBox box = selection.box();
        if (box == null) {
            return Component.translatable(KEY + "corner1", pos.toShortString());
        }
        String size = box.getXSpan() + "×" + box.getYSpan() + "×" + box.getZSpan();
        AreaOps.Problem problem = AreaOps.check(player, selection);
        if (problem != null) {
            return Component.translatable(KEY + "corner2_problem", size, problem(problem));
        }
        int routers = AreaOps.scan(player.serverLevel(), box).size();
        return Component.translatable(KEY + "corner2", size, routers);
    }

    public static Component clearCorners(ItemStack stack) {
        setSelection(stack, selection(stack).withoutCorners());
        return Component.translatable(KEY + "cleared");
    }

    public static Component problem(AreaOps.Problem problem) {
        return problem == AreaOps.Problem.TOO_LARGE
                ? Component.translatable(KEY + "problem.too_large", AreaOps.MAX_VOLUME)
                : problem == AreaOps.Problem.TOO_FAR
                        ? Component.translatable(KEY + "problem.too_far", AreaOps.MAX_REACH)
                        : Component.translatable(KEY + "problem." + problem.key);
    }

    /** Copiar área: os roteadores da área marcada vão para a cópia do item, relativos ao primeiro canto. */
    public static Component copy(ServerPlayer player, ItemStack stack) {
        AreaSelection selection = selection(stack);
        AreaOps.Problem problem = AreaOps.check(player, selection);
        if (problem != null) {
            return problem(problem);
        }
        List<AreaOps.Found> found = AreaOps.scan(player.serverLevel(), selection.box());
        if (found.isEmpty()) {
            return Component.translatable(KEY + "copy_none");
        }
        AreaClipboard clipboard = AreaOps.copy(player, found, selection.origin());
        stack.set(ModDataComponents.AREA_CLIPBOARD.get(), clipboard);
        setSelection(stack, selection.withAnchor(null));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.2F);
        return found.size() > clipboard.size()
                ? Component.translatable(KEY + "copied_capped", clipboard.size(), found.size())
                : Component.translatable(KEY + "copied", clipboard.size());
    }

    public static Component clearClipboard(ItemStack stack) {
        stack.remove(ModDataComponents.AREA_CLIPBOARD.get());
        setSelection(stack, selection(stack).withAnchor(null));
        return Component.translatable(KEY + "clipboard_cleared");
    }

    /**
     * Clique num bloco no modo Área com uma cópia: o primeiro clique escolhe a origem e diz quantas
     * posições têm roteador; clicar de novo no mesmo bloco cola.
     */
    public static Component clickAnchor(ServerPlayer player, ItemStack stack, BlockPos pos) {
        AreaClipboard clipboard = clipboard(stack);
        if (clipboard == null) {
            return Component.translatable(KEY + "no_clipboard");
        }
        GlobalPos anchor = GlobalPos.of(player.level().dimension(), pos.immutable());
        AreaSelection selection = selection(stack);
        if (selection.anchor().filter(anchor::equals).isPresent()) {
            return paste(player, stack);
        }
        setSelection(stack, selection.withAnchor(anchor));
        int hits = AreaOps.hits(player.serverLevel(), clipboard, pos);
        return Component.translatable(KEY + "anchor", hits, clipboard.size());
    }

    /** Cola a cópia na origem escolhida. A origem continua marcada, para colar de novo se precisar. */
    public static Component paste(ServerPlayer player, ItemStack stack) {
        AreaClipboard clipboard = clipboard(stack);
        if (clipboard == null) {
            return Component.translatable(KEY + "no_clipboard");
        }
        AreaSelection selection = selection(stack);
        if (selection.anchor().isEmpty()) {
            return Component.translatable(KEY + "no_anchor");
        }
        AreaOps.Outcome outcome = AreaOps.paste(player, clipboard, selection.anchor().get());
        if (outcome == null) {
            return problem(AreaOps.Problem.TOO_FAR);
        }
        if (outcome.applied() > 0) {
            player.level().playSound(null, selection.anchor().get().pos(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS, 0.5F, 1.4F);
        }
        return outcomeMessage("pasted", outcome);
    }

    /** Aplica um preset em todos os roteadores da área marcada (com {@code machine}, só nos presos a esse bloco). */
    public static Component apply(ServerPlayer player, ItemStack stack, RouterPreset preset,
            @Nullable ResourceLocation machine) {
        AreaSelection selection = selection(stack);
        AreaOps.Problem problem = AreaOps.check(player, selection);
        if (problem != null) {
            return problem(problem);
        }
        List<AreaOps.Found> found = AreaOps.scan(player.serverLevel(), selection.box());
        AreaOps.Outcome outcome = AreaOps.apply(player, found, preset, machine);
        if (outcome.applied() > 0) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.5F, 1.4F);
        }
        return outcomeMessage("applied", outcome);
    }

    private static Component outcomeMessage(String key, AreaOps.Outcome outcome) {
        Component message = Component.translatable(KEY + key, outcome.applied());
        if (outcome.missing() > 0) {
            message = message.copy().append(" · ").append(Component.translatable(KEY + "missing", outcome.missing()));
        }
        if (outcome.protectedCount() > 0) {
            message = message.copy().append(" · ")
                    .append(Component.translatable(KEY + "protected", outcome.protectedCount()));
        }
        if (outcome.droppedNetworks()) {
            message = message.copy().append(" · ").append(Component.translatable(KEY + "networks_dropped"));
        }
        return message;
    }

    private AreaActions() {
    }
}
