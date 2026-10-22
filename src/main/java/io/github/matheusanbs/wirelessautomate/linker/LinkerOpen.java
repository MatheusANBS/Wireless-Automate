package io.github.matheusanbs.wirelessautomate.linker;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.menu.EditTargetRef;
import io.github.matheusanbs.wirelessautomate.menu.RemoteRouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletClick;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.packet.LinkerOpenListPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * O modo Abrir do Vinculador: clique numa máquina abre a tela do roteador que chega nela, já editando a
 * máquina. Os candidatos são o roteador preso nela e os que a têm vinculada pelo alcance; com um só, abre
 * direto, com mais de um o servidor manda a lista curta e a escolha volta num pedido
 * ({@link #pick}). Nada fica guardado no servidor entre a lista e a escolha: cada pedido recalcula tudo.
 *
 * <p>Só no clique: uma consulta ao {@link RemoteIndex} e aos seis vizinhos. Um candidato precisa estar ao
 * alcance do tier ({@link TabletMenu#inReach}), passar por {@code mayInteract} e pelas proteções de outros
 * mods ({@link RangeLinks#clickAllowed}), conferidas no próprio roteador: ele pode estar longe da máquina, no
 * claim de outro jogador, e a escolha na lista chega por pacote, sem clique.
 */
public final class LinkerOpen {
    /** No máximo tantos roteadores na lista. */
    public static final int MAX_CHOICES = 16;

    private LinkerOpen() {
    }

    /** Como o roteador chega na máquina. */
    public enum Kind {
        /** Preso nela. */
        ATTACHED,
        /** Vinculado pelo Upgrade de Alcance. */
        REMOTE
    }

    /** Uma linha da lista: o roteador, como chega, a distância (em blocos) e o nome para mostrar. */
    public record Choice(BlockPos router, Kind kind, int distance, String name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Choice> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Choice::router,
                NeoForgeStreamCodecs.enumCodec(Kind.class), Choice::kind,
                ByteBufCodecs.VAR_INT, Choice::distance,
                ByteBufCodecs.stringUtf8(64), Choice::name,
                Choice::new);
    }

    /**
     * Os roteadores que chegam em {@code pos} e que o jogador pode abrir: os presos primeiro, depois os de
     * alcance por distância; no máximo {@link #MAX_CHOICES}.
     */
    public static List<RouterBlockEntity> candidates(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) {
            return List.of();
        }
        List<RouterBlockEntity> routers = new ArrayList<>(TabletClick.attachedRouters(level, pos));
        for (RouterBlockEntity router : RemoteIndex.get().routersAt(level, pos)) {
            if (!routers.contains(router)) {
                routers.add(router);
            }
        }
        List<RouterBlockEntity> open = new ArrayList<>(routers.size());
        for (RouterBlockEntity router : routers) {
            BlockPos at = router.getBlockPos();
            if (TabletMenu.inReach(player, router) && level.mayInteract(player, at)
                    && RangeLinks.clickAllowed(player, hand, at)) {
                open.add(router);
            }
        }
        open.sort(Comparator.<RouterBlockEntity, Boolean>comparing(router -> !attached(router, pos))
                .thenComparingInt(router -> RangeLinks.distance(router, pos))
                .thenComparingLong(router -> router.getBlockPos().asLong()));
        return open.size() > MAX_CHOICES ? open.subList(0, MAX_CHOICES) : open;
    }

    private static boolean attached(RouterBlockEntity router, BlockPos machine) {
        return machine.equals(router.machinePos());
    }

    /** Clique no modo Abrir: nenhum candidato avisa; um abre; mais de um manda a lista. */
    public static void click(ServerPlayer player, ItemStack stack, BlockPos pos) {
        InteractionHand hand = handOf(player, stack);
        List<RouterBlockEntity> routers = candidates(player, pos, hand);
        if (routers.isEmpty()) {
            player.displayClientMessage(Component.translatable("item.wirelessautomate.linker.open.none"), true);
            return;
        }
        if (routers.size() == 1) {
            open(player, routers.getFirst(), pos);
            return;
        }
        List<Choice> choices = new ArrayList<>(routers.size());
        for (RouterBlockEntity router : routers) {
            choices.add(new Choice(router.getBlockPos(), attached(router, pos) ? Kind.ATTACHED : Kind.REMOTE,
                    RangeLinks.distance(router, pos), RangeLinks.routerName(router).getString()));
        }
        LinkerOpenListPayload payload = new LinkerOpenListPayload(pos, choices);
        // jogadores falsos (GameTests) não negociam os canais do mod
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * A escolha na lista: só vale com um Vinculador em modo Abrir na mão e se o roteador ainda é um
     * candidato da máquina (recalculado agora). Devolve se abriu.
     */
    public static boolean pick(ServerPlayer player, BlockPos machine, BlockPos routerPos) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof LinkerItem && LinkerItem.mode(stack) == LinkerMode.OPEN) {
                for (RouterBlockEntity router : candidates(player, machine, hand)) {
                    if (router.getBlockPos().equals(routerPos)) {
                        open(player, router, machine);
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    private static InteractionHand handOf(ServerPlayer player, ItemStack stack) {
        return player.getItemInHand(InteractionHand.MAIN_HAND) == stack ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
    }

    /** Abre a tela do roteador já editando a máquina (a presa abre na presa; se o alvo sumiu, também). */
    private static void open(ServerPlayer player, RouterBlockEntity router, BlockPos machine) {
        EditTargetRef target = EditTargetRef.ATTACHED;
        if (!attached(router, machine)) {
            int index = router.remoteMachines().index(machine);
            EditTargetRef resolved = index < 0 ? null
                    : EditTargetRef.resolve(router, EditTarget.machine(index), machine, null);
            if (resolved != null) {
                target = resolved;
            }
        }
        RemoteRouterMenu.open(player, router, target);
    }
}
