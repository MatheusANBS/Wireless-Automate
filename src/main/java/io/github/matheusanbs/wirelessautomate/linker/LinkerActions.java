package io.github.matheusanbs.wirelessautomate.linker;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Regras do Vinculador do lado do servidor: marcar cantos, conferir a área, vincular por área e as
 * ações da tela. Tudo validado aqui; a tela e o item só pedem.
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

    /**
     * Resultado de vincular uma área.
     *
     * @param linked         roteadores que mudaram de rede
     * @param already        roteadores que já estavam nela
     * @param unloadedChunks chunks da área que estavam descarregados (os roteadores deles ficaram de fora)
     */
    public record LinkResult(LinkerProblem problem, int linked, int already, int unloadedChunks,
            @Nullable WaNetwork network) {
        static LinkResult refused(LinkerProblem problem) {
            return new LinkResult(problem, 0, 0, 0, null);
        }

        public boolean ok() {
            return problem == LinkerProblem.NONE;
        }
    }

    public static long maxVolume() {
        return Config.LINKER_MAX_AREA_VOLUME.get();
    }

    /** Distância máxima até a área; 0 = sem limite. */
    public static int maxDistance() {
        return Config.LINKER_MAX_DISTANCE.get();
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
        int routers = LinkerScan.of(player.serverLevel(), box).routers().size();
        player.displayClientMessage(Component.translatable(KEY + "corner2", position(pos), box.sizeX(), box.sizeY(),
                box.sizeZ(), routers), true);
        return Mark.SECOND;
    }

    public static Component position(BlockPos pos) {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    /** A rede ativa do jogador, se existir. */
    public static @Nullable WaNetwork activeNetwork(ServerPlayer player) {
        NetworkSavedData data = NetworkSavedData.get(player.server);
        UUID active = data.activeNetwork(player.getUUID());
        return active == null ? null : data.network(active);
    }

    /** Por que a área do Vinculador não pode ser vinculada agora (na ordem em que a tela mostra). */
    public static LinkerProblem check(ServerPlayer player, ItemStack stack) {
        LinkerArea area = LinkerItem.area(stack);
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
        WaNetwork network = activeNetwork(player);
        if (network != null && !ModPayloads.canUse(player, network)) {
            return LinkerProblem.FOREIGN_NETWORK;
        }
        return LinkerProblem.NONE;
    }

    /**
     * Põe todos os roteadores carregados da área na rede ativa, no tipo do Vinculador (ou em todas
     * as abas). Sem rede ativa, cria uma, como o modo Único. Mostra o resultado na action bar.
     */
    public static LinkResult link(ServerPlayer player, ItemStack stack) {
        LinkerProblem problem = check(player, stack);
        if (problem != LinkerProblem.NONE) {
            return LinkResult.refused(problem);
        }
        WaNetwork network = NetworkSavedData.get(player.server).activeOrCreate(player);
        ResourceType type = LinkerItem.type(stack);
        ServerLevel level = player.serverLevel();
        LinkerScan scan = LinkerScan.of(level, LinkerItem.area(stack).box());
        int linked = 0;
        int already = 0;
        for (RouterBlockEntity router : scan.routers()) {
            if (inNetwork(router, type, network.id())) {
                already++;
            } else {
                apply(router, type, network.id());
                linked++;
            }
        }
        LinkResult result = new LinkResult(LinkerProblem.NONE, linked, already, scan.unloadedChunks(), network);
        player.displayClientMessage(resultMessage(result, type), true);
        if (linked > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                    0.5F, 1.2F);
        }
        return result;
    }

    /** "5 roteadores vinculados a Base · 2 já estavam · 1 chunk descarregado ficou de fora". */
    public static MutableComponent resultMessage(LinkResult result, @Nullable ResourceType type) {
        Component name = result.network() == null ? Component.empty() : result.network().displayName();
        MutableComponent text = type == null
                ? Component.translatable(KEY + "area.linked", result.linked(), name)
                : Component.translatable(KEY + "area.linked_type", result.linked(), name, LinkerItem.typeName(type));
        if (result.already() > 0) {
            text.append(" · ").append(Component.translatable(KEY + "area.already", result.already()));
        }
        if (result.unloadedChunks() > 0) {
            text.append(" · ").append(Component.translatable(KEY + "area.unloaded", result.unloadedChunks()));
        }
        return text;
    }

    /** O tipo (ou, com {@code null}, todos os tipos) do roteador já está na rede. */
    public static boolean inNetwork(RouterBlockEntity router, @Nullable ResourceType type, UUID network) {
        if (type != null) {
            return network.equals(router.networkId(type));
        }
        for (ResourceType each : ResourceType.values()) {
            if (!network.equals(router.networkId(each))) {
                return false;
            }
        }
        return true;
    }

    /** Põe o tipo (ou, com {@code null}, todas as abas) do roteador na rede. */
    public static void apply(RouterBlockEntity router, @Nullable ResourceType type, UUID network) {
        if (type == null) {
            router.setNetworkId(network);
        } else {
            router.setNetworkId(type, network);
        }
    }

    // ------------------------------------------------------------------ tela

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
            case SET_ACTIVE -> setActive(player, payload.network().orElse(null));
            case CREATE_NETWORK -> createNetwork(player, payload.text());
            case SET_TYPE -> setType(stack, payload.value());
            case SET_MODE -> {
                if (payload.value() < 0 || payload.value() >= LinkerMode.values().length) {
                    yield false;
                }
                LinkerItem.setMode(stack, LinkerMode.values()[payload.value()]);
                yield true;
            }
            case CLEAR_AREA -> {
                LinkerItem.setArea(stack, null);
                yield true;
            }
            case LINK -> {
                LinkResult result = link(player, stack);
                if (result.ok()) {
                    menu.setOutcome(result, LinkerItem.type(stack));
                }
                yield result.ok();
            }
        };
        if (applied && payload.op() != LinkerActionPayload.Op.LINK) {
            menu.setOutcome(null, null);
        }
        menu.refresh();
        return applied;
    }

    /** Rede existente que o jogador pode usar (dono ou operador). */
    public static boolean setActive(ServerPlayer player, @Nullable UUID id) {
        NetworkSavedData data = NetworkSavedData.get(player.server);
        WaNetwork network = id == null ? null : data.network(id);
        if (network == null || !ModPayloads.canUse(player, network)) {
            return false;
        }
        data.setActiveNetwork(player.getUUID(), network.id());
        return true;
    }

    /**
     * Cria uma rede do jogador e a torna ativa. Nome de 1 a {@link LinkerActionPayload#MAX_NAME_LENGTH}
     * caracteres; se ele já tem uma rede com esse nome, só a ativa.
     */
    public static boolean createNetwork(ServerPlayer player, String rawName) {
        String name = rawName.strip();
        if (name.isEmpty() || name.length() > LinkerActionPayload.MAX_NAME_LENGTH) {
            return false;
        }
        NetworkSavedData data = NetworkSavedData.get(player.server);
        WaNetwork network = data.byName(player.getUUID(), name);
        if (network == null) {
            network = data.create(player.getUUID(), name);
        }
        data.setActiveNetwork(player.getUUID(), network.id());
        return true;
    }

    /** −1 = Todos; senão um tipo do seletor (químicos ficam de fora). */
    private static boolean setType(ItemStack stack, int value) {
        if (value == -1) {
            LinkerItem.setType(stack, null);
            return true;
        }
        if (value < 0 || value >= ResourceType.values().length || !LinkerItem.selectable(ResourceType.values()[value])) {
            return false;
        }
        LinkerItem.setType(stack, ResourceType.values()[value]);
        return true;
    }
}
