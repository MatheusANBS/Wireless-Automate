package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks.Result;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks.Status;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import io.github.matheusanbs.wirelessautomate.range.SameBlockScan.Scan;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * O Ctrl + clique do Vinculador no modo Vincular ({@code LinkSameBlockPayload}). Com a clicada já vinculada ao
 * roteador escolhido, desvincula as do grupo que se toca com ela ({@link #unlink}). Senão, vincula ao roteador
 * escolhido as máquinas do mesmo bloco (o mesmo id de registro) que se tocam com a clicada (pela face, pela aresta ou
 * pela ponta, {@link SameBlockScan}) dentro do raio do tier, da mais perto da clicada para a mais longe, até o limite.
 * O grupo passa pelas que não entram: as já vinculadas, a presa e as que já são de outro roteador
 * ({@link RouterOwners#otherRouter}), que não ocupam vaga e contam no aviso {@code same.other_router}; a clicada de
 * outro roteador não recusa o pedido: ela fica de fora e as que se tocam com ela entram. O servidor não sabe do Ctrl,
 * então o cliente cancela o uso normal e manda o pacote; aqui se refazem todas as conferências do clique (item, modo,
 * distância do jogador ao bloco, roteador escolhido, {@code mayBuild}/{@code mayInteract}, as recusas do
 * {@link RangeLinks}).
 *
 * <p>Custo, só no clique: a busca do {@link SameBlockScan} pelo grupo, até 26 leituras por posição, com o teto
 * {@link SameBlockScan#MAX_READS}; o outro roteador é conferido em cada uma do grupo que não está vinculada nem é a
 * presa. A capability ({@link RangeLinks#isMachine}) e o evento de clique das proteções
 * ({@link RangeLinks#clickAllowed}) só são conferidos nas que de fato seriam vinculadas.
 */
public final class SameBlockLinks {
    private static final String KEY = "item.wirelessautomate.linker.range.same.";
    /** O descanso do Vinculador depois de um Ctrl + clique aceito pelo servidor (meio segundo). */
    public static final int COOLDOWN_TICKS = 10;

    private SameBlockLinks() {
    }

    /**
     * O pacote do Ctrl + clique em {@code pos}. Sem o Vinculador no modo Vincular na mão principal (ou com uma Tela
     * de Sinal escolhida nele, como o cliente confere), com o bloco
     * descarregado ou longe da mão do jogador, ou com o Vinculador no descanso ({@link #COOLDOWN_TICKS}), ignora
     * (devolve {@code null}, sem mensagem: o cliente não pediria).
     */
    public static @Nullable Result link(@Nullable ServerPlayer player, BlockPos pos) {
        if (player == null) {
            return null;
        }
        ItemStack stack = player.getMainHandItem();
        ServerLevel level = player.serverLevel();
        if (!(stack.getItem() instanceof LinkerItem) || LinkerItem.mode(stack) != LinkerMode.LINK
                || LinkerItem.tv(stack) != null || !level.isLoaded(pos) || !player.canInteractWithBlock(pos, 1.0)
                || player.getCooldowns().isOnCooldown(stack.getItem())) {
            return null;
        }
        // o descanso segura um cliente que mande o pacote em rajada: a varredura custa até SameBlockScan.MAX_READS leituras
        player.getCooldowns().addCooldown(stack.getItem(), COOLDOWN_TICKS);
        RouterBlockEntity router = RangeLinks.chosen(player, stack);
        if (router == null) {
            return RangeLinks.missing(player, stack);
        }
        if (router.remoteMachines().index(pos) >= 0) {
            return unlink(player, router, pos);
        }
        if (!RangeLinks.mayEdit(player, pos) || !RangeLinks.mayEdit(player, router.getBlockPos())) {
            return RangeLinks.refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        // a presa clicada serve de modelo: ela fica de fora e as outras do mesmo bloco entram
        LinkRefusal refusal = RangeLinks.machineRefusal(level, router, pos, LoadedTypes.LIST);
        if (refusal == LinkRefusal.ATTACHED) {
            refusal = LinkRefusal.NONE;
        }
        if (refusal == LinkRefusal.NONE) {
            refusal = RangeLinks.upgradeRefusal(router);
        }
        int distance = RangeLinks.distance(router, pos);
        if (refusal == LinkRefusal.NONE && distance > RangeLinks.radius(router)) {
            refusal = LinkRefusal.OUT_OF_RANGE;
        }
        if (refusal != LinkRefusal.NONE) {
            return RangeLinks.refuse(player, Result.refused(refusal), router, distance);
        }

        Block block = level.getBlockState(pos).getBlock();
        int room = RangeLinks.max(router) - RangeLinks.used(router);
        // uma além das vagas: o "limite do tier" só aparece quando há de fato uma que ficou de fora
        Scan scan = scan(player, router, pos, block, Math.max(1, room + 1));
        LongArrayList candidates = scan.candidates();
        if (candidates.isEmpty() && scan.protectedCount() > 0) {
            return RangeLinks.refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        if (!candidates.isEmpty() && room <= 0) {
            return RangeLinks.refuse(player, Result.refused(LinkRefusal.LIMIT), router, 0);
        }

        int[] counts = new int[3]; // vinculadas, além do limite, protegidas pelo evento de clique
        router.batchChanges(() -> {
            for (int i = 0; i < candidates.size(); i++) {
                BlockPos machine = BlockPos.of(candidates.getLong(i));
                if (counts[0] >= room) {
                    counts[1]++;
                } else if (!RangeLinks.isMachine(level, machine, LoadedTypes.LIST)) {
                    // o mesmo bloco sem capability nesta posição (o estado conta): não ocupa vaga
                    continue;
                } else if (!RangeLinks.clickAllowed(player, InteractionHand.MAIN_HAND, machine)) {
                    counts[2]++;
                } else if (router.linkMachine(machine)) {
                    counts[0]++;
                }
            }
        });
        if (counts[0] == 0 && counts[2] > 0) {
            // todas as achadas recusadas pelo evento de clique das proteções
            return RangeLinks.refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        Result result = new Result(Status.SAME_BLOCK_DONE, LinkRefusal.NONE, counts[0],
                scan.already(), 0, counts[1], scan.protectedCount() + counts[2], scan.taken(), scan.unloadedChunks());
        boolean limit = counts[1] > 0;
        player.displayClientMessage(message(block, result, candidates.size(), scan, limit), true);
        if (result.linked() > 0) {
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.2F);
        }
        return result;
    }

    /**
     * O Ctrl + clique numa máquina já vinculada: tira do roteador as vinculadas do grupo que se toca com a clicada
     * ({@link SameBlockScan}, sem o corte do raio: uma encostada fora do raio também sai; a presa fica). Como o
     * desvincular de um clique, basta poder mexer no roteador e não exige o upgrade valendo; os cartões dos grupos que
     * esvaziam vão para o jogador. Lê o grupo, com o teto {@link SameBlockScan#MAX_READS}.
     */
    private static Result unlink(ServerPlayer player, RouterBlockEntity router, BlockPos pos) {
        if (!RangeLinks.mayEdit(player, router.getBlockPos())) {
            return RangeLinks.refuse(player, Result.refused(LinkRefusal.PROTECTED), router, 0);
        }
        ServerLevel level = player.serverLevel();
        Block block = level.getBlockState(pos).getBlock();
        LongArrayList same = SameBlockScan.scan(player, pos, router.getBlockPos(), SameBlockScan.NO_RADIUS, block,
                linked(router), LongSets.EMPTY_SET, key -> false, Integer.MAX_VALUE).linkedPositions();
        RemoteMachines remotes = router.remoteMachines();
        int[] cards = new int[1];
        router.batchChanges(() -> {
            for (int i = 0; i < same.size(); i++) {
                cards[0] += Math.max(0, router.unlinkMachine(remotes.index(BlockPos.of(same.getLong(i))), player));
            }
        });
        Component single = RouterBlock.machineName(block);
        MutableComponent text = same.isEmpty()
                ? Component.translatable(KEY + "unlinked.none", single, RangeLinks.routerName(router))
                : Component.translatable(KEY + (same.size() == 1 ? "unlinked.one" : "unlinked"), same.size(),
                        same.size() == 1 ? single : plural(block, single), RangeLinks.routerName(router),
                        RangeLinks.used(router), RangeLinks.max(router));
        player.displayClientMessage(RangeLinks.withCards(text, cards[0]), true);
        if (!same.isEmpty()) {
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 0.8F);
        }
        return new Result(Status.SAME_BLOCK_UNLINKED, LinkRefusal.NONE, same.size(), 0, 0, 0, 0, 0, 0);
    }

    /**
     * As que se tocam com {@code start} no raio do roteador ({@link SameBlockScan}): as já vinculadas contam em
     * {@code already}, a presa fica de fora e as de outro roteador contam em {@code taken}.
     */
    static Scan scan(ServerPlayer player, RouterBlockEntity router, BlockPos start, Block block, int needed) {
        ServerLevel level = player.serverLevel();
        return SameBlockScan.scan(player, start, router.getBlockPos(), RangeLinks.radius(router), block,
                linked(router), LongSets.singleton(router.machinePos().asLong()),
                key -> RouterOwners.otherRouter(level, router, BlockPos.of(key)) != null, needed);
    }

    /** As posições das remotas do roteador. */
    private static LongOpenHashSet linked(RouterBlockEntity router) {
        LongOpenHashSet linked = new LongOpenHashSet();
        for (RemoteMachine machine : router.remoteMachines().machines()) {
            linked.add(machine.pos().asLong());
        }
        return linked;
    }

    // ------------------------------------------------------------------ mensagens

    /**
     * {@code Vinculou 7 Fornalhas}, {@code Vinculou 32 Fornalhas · limite do tier} (a busca para numa além das vagas,
     * então não sabe o tamanho do grupo) ou, sem nenhuma nova, {@code Fornalha: todas as que se tocam já estão vinculadas} ou
     * {@code Fornalha: nenhuma nova no grupo lido} (parada no teto); mais as protegidas, as de outro roteador, os
     * chunks descarregados e o teto.
     */
    static MutableComponent message(Block block, Result result, int found, Scan scan, boolean limit) {
        Component single = RouterBlock.machineName(block);
        MutableComponent text;
        int linked = result.linked();
        if (found == 0) {
            text = Component.translatable(KEY + (scan.capped() ? "none.capped" : "none"), single);
        } else {
            text = Component.translatable(KEY + (linked == 1 ? "linked.one" : "linked"), linked,
                    linked == 1 ? single : plural(block, single));
            if (limit) {
                text.append(" · ").append(Component.translatable(KEY + "limit"));
            }
        }
        RangeLinks.appendCount(text, "area.protected", result.protectedCount());
        RangeLinks.appendCount(text, "same.other_router", result.otherRouter());
        RangeLinks.appendCount(text, "area.unloaded", result.unloadedChunks());
        if (scan.capped()) {
            text.append(" · ").append(Component.translatable(KEY + "capped"));
        }
        return text;
    }

    /**
     * O nome no plural ({@code Fornalhas}) se o idioma tem a chave
     * {@code item.wirelessautomate.linker.range.same.plural.<mod>.<bloco>}; senão {@code × Fornalha}.
     */
    public static Component plural(Block block, Component single) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        String key = KEY + "plural." + id.getNamespace() + "." + id.getPath();
        return Language.getInstance().has(key) ? Component.translatable(key)
                : Component.translatable(KEY + "times", single);
    }
}
