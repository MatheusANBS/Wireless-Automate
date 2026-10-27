package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * O Vinculador com uma Tela de Sinal: o clique na Tela a escolhe ({@code linker_tv}, que esquece o roteador
 * escolhido) e, com uma Tela escolhida, o clique num armazenamento do mod o liga ou desliga dela. Sem limite de
 * distância e em qualquer dimensão: a Tela escolhida é lida pela posição guardada, e como a escolha vale de longe,
 * ligar ou desligar carrega o chunk dela de uma vez (um clique do jogador, não uma leitura por tick). Tudo validado
 * aqui a cada clique ({@link TvLinkRule}); o item só pede.
 */
public final class TvLinks {
    private static final String KEY = "item.wirelessautomate.linker.tv.";

    private TvLinks() {
    }

    /** Clique numa Tela: ela passa a ser a escolhida e o roteador escolhido é esquecido. */
    public static void choose(ServerPlayer player, ItemStack stack, TvBlockEntity tv) {
        ServerLevel level = player.serverLevel();
        if (!tv.canEdit(player) || !level.mayInteract(player, tv.getBlockPos())) {
            player.displayClientMessage(Component.translatable(KEY + "no_permission"), true);
            return;
        }
        LinkerItem.setTv(stack, GlobalPos.of(level.dimension(), tv.getBlockPos()));
        LinkerItem.setRouter(stack, null);
        player.displayClientMessage(Component.translatable(KEY + "chosen", LinkerActions.position(tv.getBlockPos()),
                tv.links().size(), Config.tvMaxLinks()), true);
    }

    /** A Tela escolhida, ou {@code null} se sumiu (quebrada ou a dimensão não existe mais). */
    public static @Nullable TvBlockEntity chosen(ServerPlayer player, ItemStack stack) {
        GlobalPos pos = LinkerItem.tv(stack);
        ServerLevel level = pos == null ? null : player.server.getLevel(pos.dimension());
        return level != null && level.getBlockEntity(pos.pos()) instanceof TvBlockEntity tv && !tv.isRemoved()
                ? tv : null;
    }

    /** Clique num bloco com a Tela escolhida: liga ou desliga o armazenamento, com o resultado na action bar. */
    public static TvLinkRule.Action toggle(ServerPlayer player, ItemStack stack, BlockPos clicked) {
        TvBlockEntity tv = chosen(player, stack);
        if (tv == null) {
            player.displayClientMessage(Component.translatable(KEY + "missing"), true);
            return TvLinkRule.Action.NO_PERMISSION;
        }
        ServerLevel level = player.serverLevel();
        BlockEntity target = level.getBlockEntity(clicked);
        boolean storage = target instanceof StorageBlockEntity found && found.kind().loaded();
        GlobalPos pos = GlobalPos.of(level.dimension(), clicked);
        boolean allowed = tv.canEdit(player) && level.mayInteract(player, clicked);
        TvLinkRule.Action action = TvLinkRule.decide(allowed, tv.hasLink(pos), storage, tv.links().size(),
                Config.tvMaxLinks());
        switch (action) {
            case LINK -> {
                tv.link(pos);
                done(player, level, clicked, "linked", tv, 1.4F);
            }
            case UNLINK -> {
                tv.unlink(pos);
                done(player, level, clicked, "unlinked", tv, 0.8F);
            }
            case NO_PERMISSION -> player.displayClientMessage(Component.translatable(KEY + "no_permission"), true);
            case NOT_A_STORAGE -> player.displayClientMessage(Component.translatable(KEY + "not_a_storage"), true);
            case FULL -> player.displayClientMessage(Component.translatable(KEY + "full", Config.tvMaxLinks()), true);
        }
        return action;
    }

    private static void done(ServerPlayer player, ServerLevel level, BlockPos clicked, String key, TvBlockEntity tv,
            float pitch) {
        player.displayClientMessage(Component.translatable(KEY + key, level.getBlockState(clicked).getBlock().getName(),
                LinkerActions.position(tv.getBlockPos()), tv.links().size(), Config.tvMaxLinks()), true);
        level.playSound(null, clicked, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, pitch);
    }
}
