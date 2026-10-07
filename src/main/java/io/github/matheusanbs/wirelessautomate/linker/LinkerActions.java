package io.github.matheusanbs.wirelessautomate.linker;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload;
import java.util.List;
import java.util.Objects;
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
 * Regras do Vinculador do lado do servidor: marcar cantos, conferir a área, vincular (ou, no modo
 * desvincular, tirar da rede) um roteador ou a área, e as ações da tela. Tudo validado aqui; a tela e
 * o item só pedem.
 *
 * <p>Proteção: o clique num roteador passa pela checagem do próprio jogo (proteção do spawn, borda do
 * mundo) antes de chegar ao item; na área, cada roteador passa pela mesma checagem
 * ({@code mayInteract}) e os protegidos ficam de fora. Vincular exige uma rede ativa que o jogador
 * pode usar; desvincular não usa rede nenhuma, então vale para qualquer roteador que ele alcança (como
 * o "sem rede" da tela do roteador).
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
     * Resultado de vincular (ou desvincular) uma área.
     *
     * @param linked         roteadores que mudaram (entraram na rede ou saíram dela)
     * @param already        roteadores que já estavam nela (ou já sem rede, ao desvincular)
     * @param protectedCount roteadores em área protegida para o jogador (ficaram como estavam)
     * @param unloadedChunks chunks da área que estavam descarregados (os roteadores deles ficaram de fora)
     * @param network        a rede ativa; {@code null} ao desvincular
     * @param unlink         foi um desvincular
     */
    public record LinkResult(LinkerProblem problem, int linked, int already, int protectedCount, int unloadedChunks,
            @Nullable WaNetwork network, boolean unlink) {
        static LinkResult refused(LinkerProblem problem) {
            return new LinkResult(problem, 0, 0, 0, 0, null, false);
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

    /**
     * Por que a área do Vinculador não pode ser vinculada (ou desvinculada) agora, na ordem em que a
     * tela mostra. A rede ativa só conta ao vincular.
     */
    public static LinkerProblem check(ServerPlayer player, ItemStack stack) {
        LinkerProblem problem = checkArea(player, LinkerItem.area(stack));
        if (problem != LinkerProblem.NONE) {
            return problem;
        }
        if (LinkerItem.effectiveTabs(stack).isEmpty()) {
            return LinkerProblem.NO_TABS;
        }
        if (LinkerItem.unlink(stack)) {
            return LinkerProblem.NONE;
        }
        WaNetwork network = activeNetwork(player);
        if (network != null && !network.canUse(player)) {
            return LinkerProblem.FOREIGN_NETWORK;
        }
        return LinkerProblem.NONE;
    }

    /**
     * As regras de uma área marcada, sem olhar a rede: completa, nesta dimensão, dentro do volume e
     * da distância da config. Valem também para a área do Configurador.
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
     * Clique num roteador (os dois modos): põe as abas marcadas na rede ativa (criando uma se o
     * jogador não tiver) ou, no modo desvincular, as tira da rede, sem criar rede. Mostra o resultado
     * na action bar e devolve se o roteador mudou.
     */
    public static boolean single(ServerPlayer player, ItemStack stack, RouterBlockEntity router) {
        LinkerTabs tabs = LinkerItem.tabs(stack);
        List<ResourceType> types = LinkerItem.effectiveTabs(stack);
        if (types.isEmpty()) {
            player.displayClientMessage(Component.translatable(KEY + "no_tabs"), true);
            return false;
        }
        Component tabsName = LinkerItem.tabsName(tabs);
        boolean all = tabs.isAll(Chemicals.LOADED);
        if (LinkerItem.unlink(stack)) {
            if (inTarget(router, types, null)) {
                player.displayClientMessage(Component.translatable(KEY + "already_unlinked", tabsName), true);
                return false;
            }
            apply(router, types, null);
            player.displayClientMessage(Component.translatable(KEY + "unlinked", tabsName), true);
            player.level().playSound(null, router.getBlockPos(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS, 0.5F, 0.8F);
            return true;
        }
        WaNetwork network = NetworkSavedData.get(player.server).activeOrCreate(player);
        if (!network.canUse(player)) {
            player.displayClientMessage(Component.translatable(KEY + "foreign", network.displayName()), true);
            return false;
        }
        if (inTarget(router, types, network.id())) {
            player.displayClientMessage(all
                    ? Component.translatable(KEY + "already", network.displayName())
                    : Component.translatable(KEY + "already_tabs", network.displayName(), tabsName), true);
            return false;
        }
        apply(router, types, network.id());
        player.displayClientMessage(all
                ? Component.translatable(KEY + "linked", network.displayName())
                : Component.translatable(KEY + "linked_tabs", network.displayName(), tabsName), true);
        player.level().playSound(null, router.getBlockPos(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.BLOCKS, 0.5F, 1.4F);
        return true;
    }

    /**
     * Põe as abas marcadas de todos os roteadores carregados da área na rede ativa (sem rede ativa,
     * cria uma, como o modo Único) ou, no modo desvincular, as tira da rede (sem criar rede).
     * Roteadores em área protegida para o jogador ficam de fora. Mostra o resultado na action bar.
     */
    public static LinkResult link(ServerPlayer player, ItemStack stack) {
        LinkerProblem problem = check(player, stack);
        if (problem != LinkerProblem.NONE) {
            return LinkResult.refused(problem);
        }
        boolean unlink = LinkerItem.unlink(stack);
        WaNetwork network = unlink ? null : NetworkSavedData.get(player.server).activeOrCreate(player);
        UUID target = network == null ? null : network.id();
        LinkerTabs tabs = LinkerItem.tabs(stack);
        List<ResourceType> types = LinkerItem.effectiveTabs(stack);
        ServerLevel level = player.serverLevel();
        LinkerScan scan = LinkerScan.of(level, LinkerItem.area(stack).box());
        int linked = 0;
        int already = 0;
        int protectedCount = 0;
        for (RouterBlockEntity router : scan.routers()) {
            if (!level.mayInteract(player, router.getBlockPos())) {
                protectedCount++;
            } else if (inTarget(router, types, target)) {
                already++;
            } else {
                apply(router, types, target);
                linked++;
            }
        }
        LinkResult result = new LinkResult(LinkerProblem.NONE, linked, already, protectedCount, scan.unloadedChunks(),
                network, unlink);
        player.displayClientMessage(resultMessage(result, tabs), true);
        if (linked > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                    0.5F, unlink ? 0.8F : 1.2F);
        }
        return result;
    }

    /**
     * "5 roteadores vinculados a Base (Itens + Fluidos) · 2 já estavam · 1 chunk descarregado ficou de
     * fora", ou "12 roteadores desvinculados (Itens + Fluidos)".
     */
    public static MutableComponent resultMessage(LinkResult result, LinkerTabs tabs) {
        Component tabsName = LinkerItem.tabsName(tabs);
        MutableComponent text;
        if (result.unlink()) {
            text = Component.translatable(KEY + "area.unlinked", result.linked(), tabsName);
        } else {
            Component name = result.network() == null ? Component.empty() : result.network().displayName();
            text = tabs.isAll(Chemicals.LOADED)
                    ? Component.translatable(KEY + "area.linked", result.linked(), name)
                    : Component.translatable(KEY + "area.linked_tabs", result.linked(), name, tabsName);
        }
        if (result.already() > 0) {
            text.append(" · ").append(Component.translatable(
                    KEY + (result.unlink() ? "area.already_unlinked" : "area.already"), result.already()));
        }
        if (result.protectedCount() > 0) {
            text.append(" · ").append(Component.translatable(KEY + "area.protected", result.protectedCount()));
        }
        if (result.unloadedChunks() > 0) {
            text.append(" · ").append(Component.translatable(KEY + "area.unloaded", result.unloadedChunks()));
        }
        return text;
    }

    /**
     * Todas as abas {@code types} do roteador já estão em {@code target} ({@code null} = já sem rede).
     * Sem abas, nada muda e conta como já estar.
     */
    public static boolean inTarget(RouterBlockEntity router, List<ResourceType> types, @Nullable UUID target) {
        for (ResourceType type : types) {
            if (!Objects.equals(target, router.networkId(type))) {
                return false;
            }
        }
        return true;
    }

    /** Põe as abas {@code types} do roteador em {@code target} ({@code null} tira da rede). */
    public static void apply(RouterBlockEntity router, List<ResourceType> types, @Nullable UUID target) {
        router.setNetworkId(types, target);
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
            case SET_ACTIVE -> leaveUnlink(stack, setActive(player, payload.network().orElse(null)));
            case CREATE_NETWORK -> leaveUnlink(stack, createNetwork(player, payload.text()));
            case SET_UNLINK -> {
                if (payload.value() != 0 && payload.value() != 1) {
                    yield false;
                }
                LinkerItem.setUnlink(stack, payload.value() == 1);
                yield true;
            }
            case TOGGLE_TAB -> toggleTab(stack, payload.value());
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
                    menu.setOutcome(result, LinkerItem.tabs(stack));
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

    /** Rede existente que o jogador pode usar (dono, operador ou rede pública). */
    public static boolean setActive(ServerPlayer player, @Nullable UUID id) {
        NetworkSavedData data = NetworkSavedData.get(player.server);
        WaNetwork network = id == null ? null : data.network(id);
        if (network == null || !network.canUse(player)) {
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

    /** Escolher uma rede (ou criar uma) sai do modo desvincular. */
    private static boolean leaveUnlink(ItemStack stack, boolean applied) {
        if (applied) {
            LinkerItem.setUnlink(stack, false);
        }
        return applied;
    }

    /**
     * Marca ou desmarca uma aba ({@code value} = {@code ordinal} do tipo). Químicos só com o
     * Mekanism; desmarcar a última aba que vale é recusado.
     */
    private static boolean toggleTab(ItemStack stack, int value) {
        if (value < 0 || value >= ResourceType.values().length) {
            return false;
        }
        ResourceType type = ResourceType.values()[value];
        if (type == ResourceType.CHEMICAL && !Chemicals.LOADED) {
            return false;
        }
        LinkerTabs next = LinkerItem.tabs(stack).toggle(type);
        if (next.isEmpty(Chemicals.LOADED)) {
            return false;
        }
        LinkerItem.setTabs(stack, next);
        return true;
    }
}
