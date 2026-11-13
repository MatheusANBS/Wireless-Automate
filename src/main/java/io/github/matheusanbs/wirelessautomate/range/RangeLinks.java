package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.Sources;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import java.util.Collection;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Regras do servidor do modo Vincular do Vinculador: escolher o roteador
 * ({@code linker_router}) e vincular ou desvincular uma máquina com um clique. Tudo validado aqui, a cada pedido; o
 * item só pede.
 *
 * <p>Vínculos passam sempre por {@link RouterBlockEntity#linkMachine} e
 * {@link RouterBlockEntity#unlinkMachine(BlockPos)}, que mantêm o índice do mixin e os caches.
 *
 * <p>Ordem das recusas de uma máquina, a mesma no clique e no Ctrl + clique: roteador escolhido presente;
 * {@code mayBuild} do jogador (o modo aventura não vincula) e {@code mayInteract} na máquina e no roteador
 * ({@link LinkRefusal#PROTECTED}); o próprio roteador, outro roteador, a máquina presa e um bloco sem capability de um
 * tipo carregado; o upgrade ({@link #upgradeRefusal}: sem upgrade, raio 0); a máquina que já é de outro roteador
 * ({@link LinkRefusal#OTHER_ROUTER}, pelo {@link RouterOwners}); depois o raio do tier, a distância e o limite
 * ({@link RangeRules#check}). As vagas do limite são as máquinas ativas ({@link RangeRules#used}), a conta da tela.
 *
 * <p>Um clique numa máquina vinculada a desvincula; os cartões de um grupo que esvazia vão para quem clicou, e a
 * mensagem diz quantos. O Ctrl + clique (vincular ou desvincular as do mesmo bloco) fica no {@link SameBlockLinks}.
 */
public final class RangeLinks {
    private static final String KEY = "item.wirelessautomate.linker.range.";

    private RangeLinks() {
    }

    /** O que um clique fez. */
    public enum Status {
        /** Escolheu o roteador (clique num roteador). */
        CHOSEN,
        /** Soltou o roteador escolhido (clique de novo nele): o Vinculador fica sem roteador. */
        RELEASED,
        LINKED,
        UNLINKED,
        /** Ctrl + clique aplicado ({@link SameBlockLinks}), mesmo com zero vinculadas. */
        SAME_BLOCK_DONE,
        /** Ctrl + clique numa máquina vinculada aplicado ({@link SameBlockLinks}), mesmo com zero desvinculadas. */
        SAME_BLOCK_UNLINKED,
        /** Nenhum roteador escolhido no Vinculador. */
        NO_ROUTER,
        /** O roteador escolhido está descarregado, quebrado ou em outra dimensão. */
        ROUTER_MISSING,
        /** Recusado pela regra {@link Result#refusal()}. */
        REFUSED
    }

    /**
     * Resultado de um pedido. Os números só valem no Ctrl + clique ({@link SameBlockLinks}).
     *
     * @param otherRouter no Ctrl + clique, quantas de outro roteador ficaram de fora
     * @param refusal no clique num roteador, por que ele ainda não vincula (sem upgrade, raio 0) ou
     *                {@code NONE}; recusado, o motivo
     */
    public record Result(Status status, LinkRefusal refusal, int linked, int already,
            int outOfRange, int overLimit, int protectedCount, int otherRouter, int unloadedChunks) {
        static Result of(Status status) {
            return new Result(status, LinkRefusal.NONE, 0, 0, 0, 0, 0, 0, 0);
        }

        static Result refused(LinkRefusal refusal) {
            return new Result(Status.REFUSED, refusal, 0, 0, 0, 0, 0, 0, 0);
        }

        /** O pedido foi aplicado (escolher, vincular ou desvincular). */
        public boolean ok() {
            return status == Status.CHOSEN || status == Status.LINKED || status == Status.UNLINKED
                    || status == Status.SAME_BLOCK_DONE;
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
     * Clique num roteador no modo Vincular: ele passa a ser o escolhido. A action bar diz o nome e as
     * vagas ({@code Linha de Minério · 7/32 máquinas}) ou por que ele ainda não vincula. Clicar de novo no
     * roteador já escolhido o solta: o Vinculador fica sem roteador.
     */
    public static Result choose(ServerPlayer player, ItemStack stack, RouterBlockEntity router) {
        GlobalPos current = LinkerItem.router(stack);
        if (current != null && current.dimension().equals(player.level().dimension())
                && current.pos().equals(router.getBlockPos())) {
            LinkerItem.setRouter(stack, null);
            player.displayClientMessage(Component.translatable(KEY + "released", routerName(router)), true);
            return Result.of(Status.RELEASED);
        }
        if (!mayEdit(player, router.getBlockPos())) {
            return refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        LinkerItem.setRouter(stack, GlobalPos.of(player.level().dimension(), router.getBlockPos()));
        LinkerItem.setTv(stack, null);
        LinkerItem.setDock(stack, null);
        player.displayClientMessage(chosenText(router), true);
        RouterReachSync.send(player, router);
        return new Result(Status.CHOSEN, upgradeRefusal(router), 0, 0, 0, 0, 0, 0, 0);
    }

    /** {@code Linha de Minério · 7/32 máquinas} ou, com o roteador ainda sem vincular, o que o impede. */
    public static Component chosenText(RouterBlockEntity router) {
        LinkRefusal state = upgradeRefusal(router);
        return state == LinkRefusal.NONE
                ? Component.translatable(KEY + "chosen", routerName(router), used(router), max(router))
                : Component.translatable(KEY + "chosen_inactive", routerName(router), refusalText(state, router, 0));
    }

    // ------------------------------------------------------------------ uma máquina

    /**
     * Clique numa máquina no modo Vincular: vincula ao roteador escolhido ou, se já estava vinculada a
     * ele, desvincula. Mostra o resultado na action bar.
     */
    public static Result toggle(ServerPlayer player, ItemStack stack, BlockPos pos) {
        RouterBlockEntity router = chosen(player, stack);
        if (router == null) {
            return missing(player, stack);
        }
        ServerLevel level = player.serverLevel();
        if (!mayEdit(player, pos) || !mayEdit(player, router.getBlockPos())) {
            return refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        int index = router.remoteMachines().index(pos);
        if (index >= 0) {
            Component machine = MachineNames.machine(router.remoteMachines(), pos);
            Component group = MachineNames.groupOf(router.remoteMachines(), pos);
            int cards = router.unlinkMachine(index, player);
            MutableComponent text = Component.translatable(KEY + "unlinked", machine, group, routerName(router),
                    used(router), max(router));
            player.displayClientMessage(withCards(text, cards), true);
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 0.8F);
            RouterReachSync.send(player, router);
            return Result.of(Status.UNLINKED);
        }
        LinkRefusal refusal = machineRefusal(level, router, pos, LoadedTypes.LIST);
        if (refusal == LinkRefusal.NONE) {
            refusal = upgradeRefusal(router);
        }
        if (refusal == LinkRefusal.NONE) {
            RouterBlockEntity other = RouterOwners.otherRouter(level, router, pos);
            if (other != null) {
                return refuseOtherRouter(player, pos, other);
            }
        }
        int distance = distance(router, pos);
        if (refusal == LinkRefusal.NONE) {
            refusal = rangeRefusal(router, distance, used(router));
        }
        if (refusal == LinkRefusal.NONE && !router.linkMachine(pos)) {
            refusal = LinkRefusal.NOT_A_MACHINE;
        }
        if (refusal != LinkRefusal.NONE) {
            return refuse(player, Result.refused(refusal), router, distance);
        }
        player.displayClientMessage(Component.translatable(KEY + "linked",
                MachineNames.machine(router.remoteMachines(), pos), MachineNames.groupOf(router.remoteMachines(), pos),
                routerName(router), used(router), max(router)), true);
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.4F);
        RouterReachSync.send(player, router);
        return Result.of(Status.LINKED);
    }

    /**
     * A recusa da máquina em {@code pos}, que já é de {@code other}: {@code Fornalha já é de Forja Norte em 812, 64,
     * -230}. No clique e no Ctrl + clique num grupo todo de outro dono.
     */
    static Result refuseOtherRouter(ServerPlayer player, BlockPos pos, RouterBlockEntity other) {
        player.displayClientMessage(Component.translatable(KEY + "refusal.other_router",
                RouterBlock.machineName(player.serverLevel().getBlockState(pos).getBlock()), routerName(other),
                LinkerActions.position(other.getBlockPos())), true);
        return Result.refused(LinkRefusal.OTHER_ROUTER);
    }

    /** {@code text}, mais {@code · 2 cartões devolvidos} quando {@code cards} é maior que zero. */
    static MutableComponent withCards(MutableComponent text, int cards) {
        if (cards > 0) {
            text.append(" · ").append(cards == 1 ? Component.translatable(KEY + "cards_returned.one")
                    : Component.translatable(KEY + "cards_returned", cards));
        }
        return text;
    }

    public static void appendCount(MutableComponent text, String key, int count) {
        if (count > 0) {
            text.append(" · ").append(Component.translatable(KEY + key, count));
        }
    }

    /**
     * O clique simulado em {@code pos} passa pelas proteções de outros mods. O {@code mayInteract} só
     * cobre a proteção do spawn e a borda do mundo; claims (FTB Chunks e afins) agem no
     * {@link PlayerInteractEvent.RightClickBlock}. O clique de verdade num bloco já passa por esse
     * evento antes do {@code onItemUseFirst} do Vinculador (o {@code ServerPlayerGameMode.useItemOn} o
     * posta e não chama o item se ele vem cancelado ou com {@code useItem} negado), mas o Ctrl + clique
     * vincula outras máquinas que ninguém clicou: por isso postamos o mesmo evento, como o jogo, para cada
     * uma que de fato seria vinculada (depois das recusas baratas, para não acordar listeners à toa).
     * Cancelado, ou com {@code useBlock} ou {@code useItem} negados, conta como protegida.
     */
    public static boolean clickAllowed(ServerPlayer player, InteractionHand hand, BlockPos pos) {
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
    public static LinkRefusal upgradeRefusal(RouterBlockEntity router) {
        return RangeRules.check(RouterBlockEntity.acceptsRangeUpgrade(router.rangeUpgrade()), radius(router), true,
                0, 0, Integer.MAX_VALUE);
    }

    /** Vagas usadas ({@link RangeRules#used}): as ativas, dentro do raio e até o limite, como na tela. */
    public static int used(RouterBlockEntity router) {
        return RangeRules.used(router.machineReach());
    }

    /**
     * O jogador pode mexer em {@code pos}: constrói ({@code mayBuild}: o modo aventura e o espectador não) e o
     * {@code mayInteract} deixa (proteção do spawn, borda do mundo).
     */
    public static boolean mayEdit(ServerPlayer player, BlockPos pos) {
        return player.mayBuild() && player.serverLevel().mayInteract(player, pos);
    }

    public static int distance(RouterBlockEntity router, BlockPos pos) {
        BlockPos origin = router.getBlockPos();
        return RangeRules.chebyshev(origin.getX(), origin.getY(), origin.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    static int radius(RouterBlockEntity router) {
        return Config.machineRadius(router.tier());
    }

    public static int max(RouterBlockEntity router) {
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

    static Result refuse(ServerPlayer player, Result result, RouterBlockEntity router, int distance) {
        player.displayClientMessage(refusalText(result.refusal(), router, distance), true);
        return result;
    }

    static Result missing(ServerPlayer player, ItemStack stack) {
        boolean none = chosenPos(stack) == null;
        player.displayClientMessage(Component.translatable(KEY + (none ? "no_router" : "router_missing")), true);
        return Result.of(none ? Status.NO_ROUTER : Status.ROUTER_MISSING);
    }
}
