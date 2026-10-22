package io.github.matheusanbs.wirelessautomate.linker;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Regras do Vinculador do lado do servidor que não são do vínculo em si (esse é do {@link RangeLinks}):
 * marcar cantos, conferir a área e as ações da tela. Tudo validado aqui; a tela e o item só pedem.
 */
public final class LinkerActions {
    private static final String KEY = "item.wirelessautomate.linker.";

    private LinkerActions() {
    }

    /** O que um Shift + clique num bloco fez. */
    public enum Mark {
        FIRST,
        SECOND,
        /** O canto 2 deixaria a área acima do volume máximo; o canto 1 fica. */
        TOO_BIG
    }

    public static long maxVolume() {
        return Config.read(Config.LINKER_MAX_AREA_VOLUME);
    }

    /** Distância máxima até a área; 0 = sem limite. */
    public static int maxDistance() {
        return Config.read(Config.LINKER_MAX_DISTANCE);
    }

    /** Canto 1 se não há área (ou ela está completa, ou é de outra dimensão); senão, o canto 2. */
    public static Mark markCorner(ServerPlayer player, ItemStack stack, BlockPos pos) {
        LinkerArea area = LinkerItem.area(stack);
        if (area == null || area.complete() || !area.dimension().equals(player.level().dimension())) {
            LinkerItem.setArea(stack, LinkerArea.firstCorner(player.level().dimension(), pos));
            player.displayClientMessage(Component.translatable(KEY + "corner1", position(pos)), true);
            return Mark.FIRST;
        }
        LinkerArea next = area.withSecond(pos);
        LinkerBox box = next.box();
        if (box.volume() > maxVolume()) {
            player.displayClientMessage(Component.translatable(KEY + "too_big", box.volume(), maxVolume()), true);
            return Mark.TOO_BIG;
        }
        LinkerItem.setArea(stack, next);
        player.displayClientMessage(Component.translatable(KEY + "corner2", position(pos), box.sizeX(), box.sizeY(),
                box.sizeZ()), true);
        return Mark.SECOND;
    }

    public static Component position(BlockPos pos) {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    /**
     * As regras de uma área marcada: completa, nesta dimensão, dentro do volume e da distância da config.
     */
    public static LinkerProblem checkArea(ServerPlayer player, @Nullable LinkerArea area) {
        if (area == null) {
            return LinkerProblem.NO_AREA;
        }
        if (!area.complete()) {
            return LinkerProblem.INCOMPLETE;
        }
        if (!area.dimension().equals(player.level().dimension())) {
            return LinkerProblem.OTHER_DIMENSION;
        }
        LinkerBox box = area.box();
        if (box.volume() > maxVolume()) {
            return LinkerProblem.TOO_BIG;
        }
        int distance = maxDistance();
        if (distance > 0 && box.distanceSqTo(player.getX(), player.getY(), player.getZ())
                > (double) distance * distance) {
            return LinkerProblem.TOO_FAR;
        }
        return LinkerProblem.NONE;
    }

    /**
     * Uma ação da tela do Vinculador. Só age com a tela daquele {@code containerId} aberta e o
     * Vinculador ainda na mão; depois o menu manda o estado novo. Devolve se aplicou.
     */
    public static boolean handle(@Nullable ServerPlayer player, LinkerActionPayload payload) {
        LinkerMenu menu = player != null && player.containerMenu instanceof LinkerMenu m
                && m.containerId == payload.containerId() ? m : null;
        if (menu == null || !menu.stillValid(player)) {
            return false;
        }
        ItemStack stack = menu.linker(player);
        boolean applied = switch (payload.op()) {
            case SET_UNLINK -> {
                if (payload.value() != 0 && payload.value() != 1) {
                    yield false;
                }
                LinkerItem.setUnlink(stack, payload.value() == 1);
                yield true;
            }
            case CLEAR_AREA -> {
                LinkerItem.setArea(stack, null);
                yield true;
            }
            case LINK -> {
                RangeLinks.Result result = RangeLinks.linkArea(player, stack);
                menu.setOutcome(result, LinkerItem.unlink(stack));
                yield result.ok();
            }
        };
        if (applied && payload.op() != LinkerActionPayload.Op.LINK) {
            menu.setOutcome(null, false);
        }
        menu.refresh();
        return applied;
    }
}
