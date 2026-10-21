package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerBox;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.Sources;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Regras do servidor do modo Máquinas do Vinculador: escolher o roteador
 * ({@code linker_router}), vincular ou desvincular uma máquina com um clique e vincular as máquinas
 * de uma área. Tudo validado aqui, a cada pedido; o item e a tela só pedem.
 *
 * <p>Vínculos passam sempre por {@link RouterBlockEntity#linkMachine} e
 * {@link RouterBlockEntity#unlinkMachine(BlockPos)}, que mantêm o índice do mixin e os caches.
 *
 * <p>Ordem das recusas de uma máquina: roteador escolhido presente; {@code mayInteract} na máquina e
 * no roteador ({@link LinkRefusal#PROTECTED}); o próprio roteador, outro roteador, a máquina presa e
 * um bloco sem capability de um tipo carregado; depois {@link RangeRules#check} (upgrade, raio do
 * tier, distância e limite). As vagas do limite são as máquinas vinculadas dentro do raio (as de fora
 * não gastam vaga, como no motor).
 */
public final class RangeLinks {
    private static final String KEY = "item.wirelessautomate.linker.range.";

    private RangeLinks() {
    }

    /** O que um clique (ou o Vincular da área) fez. */
    public enum Status {
        /** Escolheu o roteador (clique num roteador). */
        CHOSEN,
        LINKED,
        UNLINKED,
        /** Vincular da área aplicado (mesmo com zero vinculadas). */
        AREA_DONE,
        /** Nenhum roteador escolhido no Vinculador. */
        NO_ROUTER,
        /** O roteador escolhido está descarregado, quebrado ou em outra dimensão. */
        ROUTER_MISSING,
        /** A área do Vinculador não serve ({@link Result#problem()}). */
        AREA_PROBLEM,
        /** Recusado pela regra {@link Result#refusal()}. */
        REFUSED
    }

    /**
     * Resultado de um pedido. Os números só valem no Vincular da área.
     *
     * @param refusal no clique num roteador, por que ele ainda não vincula (sem upgrade, raio 0) ou
     *                {@code NONE}; recusado, o motivo
     */
    public record Result(Status status, LinkRefusal refusal, LinkerProblem problem, int linked, int already,
            int outOfRange, int overLimit, int protectedCount, int unloadedChunks) {
        static Result of(Status status) {
            return new Result(status, LinkRefusal.NONE, LinkerProblem.NONE, 0, 0, 0, 0, 0, 0);
        }

        static Result refused(LinkRefusal refusal) {
            return new Result(Status.REFUSED, refusal, LinkerProblem.NONE, 0, 0, 0, 0, 0, 0);
        }

        /** O pedido foi aplicado (escolher, vincular, desvincular ou o Vincular da área). */
        public boolean ok() {
            return status == Status.CHOSEN || status == Status.LINKED || status == Status.UNLINKED
                    || status == Status.AREA_DONE;
        }
    }

    // ------------------------------------------------------------------ roteador escolhido

    /** O roteador escolhido no Vinculador; {@code null} se nenhum. */
    public static @Nullable GlobalPos chosenPos(ItemStack stack) {
        return LinkerItem.router(stack);
    }

    /**
     * O roteador escolhido, se está carregado, inteiro e na dimensão do jogador; senão {@code null}.
     * Nunca carrega o chunk.
     */
    public static @Nullable RouterBlockEntity chosen(ServerPlayer player, ItemStack stack) {
        GlobalPos pos = chosenPos(stack);
        if (pos == null || !pos.dimension().equals(player.level().dimension())) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos.pos())) {
            return null;
        }
        return level.getBlockEntity(pos.pos()) instanceof RouterBlockEntity router && !router.isRemoved()
                ? router : null;
    }

    /**
     * Clique num roteador no modo Máquinas: ele passa a ser o escolhido. A action bar diz o nome e as
     * vagas ({@code Linha de Minério · 7/32 máquinas}) ou por que ele ainda não vincula.
     */
    public static Result choose(ServerPlayer player, ItemStack stack, RouterBlockEntity router) {
        if (!player.serverLevel().mayInteract(player, router.getBlockPos())) {
            return refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        LinkerItem.setRouter(stack, GlobalPos.of(player.level().dimension(), router.getBlockPos()));
        LinkRefusal state = upgradeRefusal(router);
        player.displayClientMessage(state == LinkRefusal.NONE
                ? Component.translatable(KEY + "chosen", routerName(router), used(router), max(router))
                : Component.translatable(KEY + "chosen_inactive", routerName(router), refusalText(state, router, 0)),
                true);
        return new Result(Status.CHOSEN, state, LinkerProblem.NONE, 0, 0, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ uma máquina

    /**
     * Clique numa máquina no modo Máquinas: vincula ao roteador escolhido ou, se já estava vinculada a
     * ele, desvincula. Mostra o resultado na action bar.
     */
    public static Result toggle(ServerPlayer player, ItemStack stack, BlockPos pos) {
        RouterBlockEntity router = chosen(player, stack);
        if (router == null) {
            return missing(player, stack);
        }
        ServerLevel level = player.serverLevel();
        if (!level.mayInteract(player, pos) || !level.mayInteract(player, router.getBlockPos())) {
            return refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        if (router.remoteMachines().contains(pos)) {
            Component machine = MachineNames.machine(router.remoteMachines(), pos);
            Component group = MachineNames.groupOf(router.remoteMachines(), pos);
            router.unlinkMachine(pos);
            player.displayClientMessage(Component.translatable(KEY + "unlinked", machine, group, routerName(router),
                    used(router), max(router)), true);
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 0.8F);
            return Result.of(Status.UNLINKED);
        }
        LinkRefusal refusal = machineRefusal(level, router, pos, LoadedTypes.LIST);
        int distance = distance(router, pos);
        if (refusal == LinkRefusal.NONE) {
            refusal = rangeRefusal(router, distance, used(router));
        }
        if (refusal == LinkRefusal.NONE && !router.linkMachineIsolated(pos)) {
            refusal = LinkRefusal.NOT_A_MACHINE;
        }
        if (refusal != LinkRefusal.NONE) {
            return refuse(player, Result.refused(refusal), router, distance);
        }
        player.displayClientMessage(Component.translatable(KEY + "linked",
                MachineNames.machine(router.remoteMachines(), pos), MachineNames.groupOf(router.remoteMachines(), pos),
                routerName(router), used(router), max(router)), true);
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.4F);
        return Result.of(Status.LINKED);
    }

    // ------------------------------------------------------------------ área

    /**
     * O Vincular da tela no modo Máquinas: vincula ao roteador escolhido as máquinas da área marcada
     * (só block entities de chunks carregados) que têm capability de um tipo marcado nas abas. Pula as
     * já vinculadas, as protegidas ({@code mayInteract} e o evento de clique, ver {@link #clickAllowed}),
     * roteadores e a máquina presa; as de fora do raio e as que passam do
     * limite ficam de fora e são contadas. As mais perto do roteador entram primeiro. Mostra o
     * resultado na action bar ({@code Vinculadas 9 · 3 fora do alcance · 2 além do limite}).
     */
    public static Result linkArea(ServerPlayer player, ItemStack stack) {
        LinkerArea area = LinkerItem.area(stack);
        LinkerProblem problem = LinkerActions.checkArea(player, area);
        if (problem == LinkerProblem.NONE && LinkerItem.unlink(stack)) {
            return unlinkArea(player, stack, area);
        }
        List<ResourceType> types = LinkerItem.effectiveTabs(stack);
        if (problem == LinkerProblem.NONE && types.isEmpty()) {
            problem = LinkerProblem.NO_TABS;
        }
        if (problem != LinkerProblem.NONE) {
            // como no Vincular de roteadores: a tela já mostra o problema da área
            return new Result(Status.AREA_PROBLEM, LinkRefusal.NONE, problem, 0, 0, 0, 0, 0, 0);
        }
        RouterBlockEntity router = chosen(player, stack);
        if (router == null) {
            return missing(player, stack);
        }
        ServerLevel level = player.serverLevel();
        if (!level.mayInteract(player, router.getBlockPos())) {
            return refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        LinkRefusal state = upgradeRefusal(router);
        if (state != LinkRefusal.NONE) {
            return refuse(player, Result.refused(state), router, 0);
        }

        LinkerBox box = area.box();
        List<BlockPos> candidates = new ArrayList<>();
        int unloaded = 0;
        for (int cx = box.minChunkX(); cx <= box.maxChunkX(); cx++) {
            for (int cz = box.minChunkZ(); cz <= box.maxChunkZ(); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    unloaded++;
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    BlockPos pos = entity.getBlockPos();
                    if (!entity.isRemoved() && box.contains(pos.getX(), pos.getY(), pos.getZ())) {
                        candidates.add(pos.immutable());
                    }
                }
            }
        }
        // as mais perto do roteador ganham as vagas; empate pela posição, para ser estável
        candidates.sort(Comparator.<BlockPos>comparingInt(pos -> distance(router, pos)).thenComparingLong(BlockPos::asLong));

        int[] counts = new int[5]; // vinculadas, já, fora do raio, além do limite, protegidas
        int radius = radius(router);
        int max = max(router);
        InteractionHand hand = player.getItemInHand(InteractionHand.MAIN_HAND) == stack
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        router.batchChanges(() -> {
            int used = used(router);
            for (BlockPos pos : candidates) {
                if (machineRefusal(level, router, pos, types) != LinkRefusal.NONE) {
                    continue;
                }
                if (router.remoteMachines().contains(pos)) {
                    counts[1]++;
                } else if (!level.mayInteract(player, pos)) {
                    counts[4]++;
                } else if (distance(router, pos) > radius) {
                    counts[2]++;
                } else if (used >= max) {
                    counts[3]++;
                } else if (!clickAllowed(player, hand, pos)) {
                    counts[4]++;
                } else if (router.linkMachineIsolated(pos)) {
                    counts[0]++;
                    used++;
                }
            }
        });
        Result result = new Result(Status.AREA_DONE, LinkRefusal.NONE, LinkerProblem.NONE, counts[0], counts[1],
                counts[2], counts[3], counts[4], unloaded);
        player.displayClientMessage(areaMessage(result), true);
        if (result.linked() > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                    0.5F, 1.2F);
        }
        return result;
    }

    /**
     * Com "Nenhuma (desvincular)": tira do roteador escolhido as máquinas vinculadas dentro da área.
     * Percorre só a lista do roteador (nada de varrer o mundo, e vale com o chunk da máquina
     * descarregado), não olha as abas (a máquina sai inteira) e não exige o upgrade valendo, para
     * dar para limpar a lista depois de tirá-lo. Basta poder mexer no roteador: desvincular não toca
     * na máquina.
     */
    private static Result unlinkArea(ServerPlayer player, ItemStack stack, LinkerArea area) {
        RouterBlockEntity router = chosen(player, stack);
        if (router == null) {
            return missing(player, stack);
        }
        ServerLevel level = player.serverLevel();
        if (!level.mayInteract(player, router.getBlockPos())) {
            return refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        LinkerBox box = area.box();
        List<BlockPos> inside = new ArrayList<>();
        for (RemoteMachine machine : router.remoteMachines().machines()) {
            BlockPos pos = machine.pos();
            if (box.contains(pos.getX(), pos.getY(), pos.getZ())) {
                inside.add(pos);
            }
        }
        router.batchChanges(() -> {
            for (BlockPos pos : inside) {
                router.unlinkMachine(pos);
            }
        });
        Result result = new Result(Status.AREA_DONE, LinkRefusal.NONE, LinkerProblem.NONE, inside.size(), 0, 0, 0, 0, 0);
        player.displayClientMessage(Component.translatable(KEY + "area.unlinked", inside.size(), routerName(router),
                used(router), max(router)), true);
        if (!inside.isEmpty()) {
            level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                    0.5F, 0.8F);
        }
        return result;
    }

    /** {@code Vinculadas 9 · 2 já estavam · 3 fora do alcance · 2 além do limite · ...}. */
    public static MutableComponent areaMessage(Result result) {
        MutableComponent text = Component.translatable(KEY + "area.linked", result.linked());
        appendCount(text, "area.already", result.already());
        appendCount(text, "area.out_of_range", result.outOfRange());
        appendCount(text, "area.over_limit", result.overLimit());
        appendCount(text, "area.protected", result.protectedCount());
        appendCount(text, "area.unloaded", result.unloadedChunks());
        return text;
    }

    private static void appendCount(MutableComponent text, String key, int count) {
        if (count > 0) {
            text.append(" · ").append(Component.translatable(KEY + key, count));
        }
    }

    /**
     * O clique simulado em {@code pos} passa pelas proteções de outros mods. O {@code mayInteract} só
     * cobre a proteção do spawn e a borda do mundo; claims (FTB Chunks e afins) agem no
     * {@link PlayerInteractEvent.RightClickBlock}. O clique de verdade num bloco já passa por esse
     * evento antes do {@code onItemUseFirst} do Vinculador (o {@code ServerPlayerGameMode.useItemOn} o
     * posta e não chama o item se ele vem cancelado ou com {@code useItem} negado), mas o Vincular da
     * área não clica em nada: por isso postamos o mesmo evento, como o jogo, para cada máquina que de
     * fato seria vinculada (depois das recusas baratas, para não acordar listeners à toa). Cancelado,
     * ou com {@code useBlock} ou {@code useItem} negados, conta como protegida.
     */
    private static boolean clickAllowed(ServerPlayer player, InteractionHand hand, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        PlayerInteractEvent.RightClickBlock event = CommonHooks.onRightClickBlock(player, hand, pos, hit);
        return !event.isCanceled() && event.getUseBlock() != TriState.FALSE && event.getUseItem() != TriState.FALSE;
    }

    // ------------------------------------------------------------------ regras

    /**
     * As recusas que não dependem do raio nem do limite: o próprio roteador, outro roteador, a máquina
     * presa e um bloco sem capability de nenhum dos {@code types}. {@code NONE} se é uma máquina.
     */
    public static LinkRefusal machineRefusal(Level level, RouterBlockEntity router, BlockPos pos,
            Collection<ResourceType> types) {
        if (pos.equals(router.getBlockPos())) {
            return LinkRefusal.SELF;
        }
        if (!level.isLoaded(pos)) {
            // consultar um chunk descarregado o carregaria à força
            return LinkRefusal.NOT_A_MACHINE;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof RouterBlockEntity) {
            return LinkRefusal.ROUTER;
        }
        if (pos.equals(router.machinePos())) {
            return LinkRefusal.ATTACHED;
        }
        return isMachine(level, pos, entity, types) ? LinkRefusal.NONE : LinkRefusal.NOT_A_MACHINE;
    }

    /**
     * O bloco tem capability de algum dos {@code types} carregados, com lado nulo ou em alguma face. Só
     * no clique (consulta direta, sem cache); o chunk já precisa estar carregado.
     */
    public static boolean isMachine(Level level, BlockPos pos, Collection<ResourceType> types) {
        return isMachine(level, pos, level.getBlockEntity(pos), types);
    }

    private static boolean isMachine(Level level, BlockPos pos, @Nullable BlockEntity entity,
            Collection<ResourceType> types) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        for (ResourceType type : types) {
            if (!LoadedTypes.contains(type)) {
                continue;
            }
            BlockCapability<?, @Nullable Direction> capability = capability(type);
            if (capability != null && hasCapability(level, pos, state, entity, capability)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable BlockCapability<?, @Nullable Direction> capability(ResourceType type) {
        return switch (type) {
            case ITEM -> Capabilities.ItemHandler.BLOCK;
            case FLUID -> Capabilities.FluidHandler.BLOCK;
            case ENERGY -> Capabilities.EnergyStorage.BLOCK;
            case CHEMICAL -> Chemicals.capability();
            case SOURCE -> Sources.capability();
        };
    }

    private static boolean hasCapability(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity entity,
            BlockCapability<?, @Nullable Direction> capability) {
        if (level.getCapability(capability, pos, state, entity, null) != null) {
            return true;
        }
        for (Direction side : Direction.values()) {
            if (level.getCapability(capability, pos, state, entity, side) != null) {
                return true;
            }
        }
        return false;
    }

    /** {@link RangeRules#check} com o upgrade, o raio e o limite do roteador (mesma dimensão já conferida). */
    private static LinkRefusal rangeRefusal(RouterBlockEntity router, int distance, int used) {
        return RangeRules.check(RouterBlockEntity.acceptsRangeUpgrade(router.rangeUpgrade()), radius(router), true,
                distance, used, max(router));
    }

    /** Sem upgrade ({@code NO_UPGRADE}) ou com raio 0 ({@code NO_RANGE}); {@code NONE} se o roteador vincula. */
    private static LinkRefusal upgradeRefusal(RouterBlockEntity router) {
        return RangeRules.check(RouterBlockEntity.acceptsRangeUpgrade(router.rangeUpgrade()), radius(router), true,
                0, 0, Integer.MAX_VALUE);
    }

    /** Vagas usadas: as máquinas vinculadas dentro do raio (as de fora não gastam vaga). */
    public static int used(RouterBlockEntity router) {
        int radius = radius(router);
        int used = 0;
        for (RemoteMachine machine : router.remoteMachines().machines()) {
            if (distance(router, machine.pos()) <= radius) {
                used++;
            }
        }
        return used;
    }

    public static int distance(RouterBlockEntity router, BlockPos pos) {
        BlockPos origin = router.getBlockPos();
        return RangeRules.chebyshev(origin.getX(), origin.getY(), origin.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    private static int radius(RouterBlockEntity router) {
        return Config.machineRadius(router.tier());
    }

    private static int max(RouterBlockEntity router) {
        return Config.maxMachines(router.tier());
    }

    // ------------------------------------------------------------------ mensagens

    /** O nome do roteador ou, sem nome, o do bloco. */
    public static Component routerName(RouterBlockEntity router) {
        return router.name().isEmpty()
                ? router.getBlockState().getBlock().getName()
                : Component.literal(router.name());
    }

    /** O texto de uma recusa, com os números das que têm: distância e raio, tier e limite. */
    public static Component refusalText(LinkRefusal refusal, RouterBlockEntity router, int distance) {
        String key = "item.wirelessautomate.linker." + refusal.key();
        return switch (refusal) {
            case OUT_OF_RANGE -> Component.translatable(key, distance, radius(router));
            case LIMIT -> Component.translatable(key, Component.translatable(router.tier().translationKey()),
                    max(router));
            default -> Component.translatable(key);
        };
    }

    private static Result refuse(ServerPlayer player, Result result, RouterBlockEntity router, int distance) {
        player.displayClientMessage(refusalText(result.refusal(), router, distance), true);
        return result;
    }

    private static Result missing(ServerPlayer player, ItemStack stack) {
        boolean none = chosenPos(stack) == null;
        player.displayClientMessage(Component.translatable(KEY + (none ? "no_router" : "router_missing")), true);
        return Result.of(none ? Status.NO_ROUTER : Status.ROUTER_MISSING);
    }
}
