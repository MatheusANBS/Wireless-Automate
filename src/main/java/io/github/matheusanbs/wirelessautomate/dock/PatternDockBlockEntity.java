package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.CacheOwner;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.RangeRules;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteListener;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * O block entity da Doca de Padrões, sem ticker: os seis {@link DockSide} (nulo = lado nunca usado), as máquinas por
 * posição para achar a de um aviso sem varrer os lados, e o registro no {@link RemoteIndex} das máquinas que estão no
 * alcance do tier ({@link Config#dockRadius}, distância de Chebyshev) até o máximo ({@link Config#dockMaxMachines}),
 * contadas na ordem dos lados e, em cada lado, na ordem de vínculo.
 *
 * <p>O registro no índice muda só no carregamento ({@link #onLoad}), num vínculo ou desvínculo, numa troca de tier e
 * na recarga da config; sai no {@link #setRemoved} e no {@link #onChunkUnloaded}. A volta do resultado
 * ({@link DockReturn}) entra no laço do motor no mesmo {@code onLoad} e sai junto com o índice. Os avisos das máquinas (o {@code setChanged} pelo mixin, a
 * carga do chunk e um cache invalidado) chegam todos ao {@link #machineChanged}.
 */
public class PatternDockBlockEntity extends BlockEntity implements RemoteListener, CacheOwner {
    public static final int SIDES = 6;
    /**
     * O evento de bloco "um lado entregou ou devolveu" ({@link #signalWork}), com o lado no parâmetro: o cliente anima
     * o laço de trabalho ({@link DockWork}). Não é payload do mod: o jogo manda só a quem está a menos de 64 blocos.
     */
    public static final int EVENT_WORK = 1;

    private final DockSide[] sides = new DockSide[SIDES];
    /** Todas as máquinas da Doca, pela posição ({@code BlockPos.asLong}). */
    private final Long2ObjectOpenHashMap<DockMachine> machineAt = new Long2ObjectOpenHashMap<>();
    /** Dentro de um {@link #batchChanges}: os vínculos só marcam {@link #batchDirty}, e o registro é refeito no fim. */
    private boolean batching;
    private boolean batchDirty;
    /** A volta do resultado, no laço do motor enquanto a Doca está carregada no servidor. */
    private final DockReturn returns = new DockReturn(this);
    /**
     * A máquina de crafting do AE2 de cada face ({@code compat/ae2/Ae2DockMachine}), criada na primeira consulta da
     * capability; {@code Object} para esta classe carregar sem o AE2.
     */
    private Object @Nullable [] ae2Faces;
    /** Um item da Doca com o pendente foi feito ou vai ser feito pela quebra ({@link #dropPendingWithoutItem}). */
    private boolean dropExpected;
    /** No servidor: o tick ({@code getTickCount}) do último evento de trabalho mandado, para o intervalo. Não é salvo. */
    private long lastWorkSent = DockWork.NEVER;
    /** No servidor: quantos eventos de trabalho o jogo processou nesta Doca (para os testes). */
    private int workEvents;
    /** No cliente: o relógio do laço de trabalho, alimentado pelos eventos. Não é salvo. */
    private final DockWork work = new DockWork();

    public PatternDockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PATTERN_DOCK.get(), pos, state);
    }

    public RouterTier tier() {
        return getBlockState().getValue(RouterBlock.TIER);
    }

    /** O lado {@code side} ({@code Direction.ordinal()}), ou {@code null} se nunca foi usado. */
    public @Nullable DockSide side(int side) {
        return sides[side];
    }

    /** A máquina em {@code pos}, de qualquer lado, ou {@code null}. */
    public @Nullable DockMachine machineAt(BlockPos pos) {
        return machineAt.get(pos.asLong());
    }

    /** O {@link #machineAt(BlockPos)} pela posição em {@code long}, sem criar a posição. */
    @Nullable DockMachine machineAt(long pos) {
        return machineAt.get(pos);
    }

    /** Quantas máquinas a Doca tem vinculadas, somando os seis lados (dentro do alcance ou não). */
    public int machineCount() {
        return machineAt.size();
    }

    /** Quantas máquinas estão ativas (no alcance e dentro do máximo): as vagas usadas, como na tela. */
    public int activeCount() {
        int count = 0;
        for (DockMachine machine : machineAt.values()) {
            if (machine.active()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Roda {@code changes} (vários vínculos e desvínculos, como os do Ctrl + clique) refazendo o registro no índice e
     * salvando uma vez só, no fim, em vez de uma vez por máquina.
     */
    public void batchChanges(Runnable changes) {
        if (batching) {
            changes.run();
            return;
        }
        batching = true;
        try {
            changes.run();
        } finally {
            batching = false;
            if (batchDirty) {
                batchDirty = false;
                linksChanged();
            }
        }
    }

    /**
     * Vincula a máquina em {@code pos} (bloco {@code block}, frente {@code front} ou −1) ao lado {@code side}, criando o
     * lado no primeiro vínculo, e refaz o registro no índice. Recusa ({@code false}) uma máquina já vinculada a esta
     * Doca ou de outro bloco que o do lado; o alcance e o limite são conferidos por quem chama (o Vinculador).
     */
    public boolean link(int side, BlockPos pos, ResourceLocation block, int front) {
        if (machineAt.containsKey(pos.asLong())) {
            return false;
        }
        DockSide dockSide = sides[side];
        if (dockSide == null) {
            dockSide = new DockSide(side);
            sides[side] = dockSide;
        }
        if (dockSide.block() == null) {
            dockSide.assignBlock(block);
        } else if (!dockSide.block().equals(block)) {
            return false;
        }
        DockMachine machine = new DockMachine(side, pos, front);
        dockSide.addMachine(machine);
        machineAt.put(pos.asLong(), machine);
        linksChanged();
        return true;
    }

    /**
     * Desvincula a máquina em {@code pos} do lado {@code side} e refaz o registro no índice. Sem máquinas e sem
     * pendente, o lado esquece o bloco (o próximo vínculo o escolhe de novo) e deixa de existir. Com pendente, o lado
     * guarda o bloco: o resto do pedido só serve a uma máquina daquele bloco, e sai na próxima vinculada (ou vai no
     * item, ao quebrar a Doca).
     */
    public boolean unlink(int side, BlockPos pos) {
        DockSide dockSide = sides[side];
        DockMachine machine = dockSide == null ? null : dockSide.removeMachine(pos);
        if (machine == null) {
            return false;
        }
        machine.discard();
        machineAt.remove(pos.asLong());
        if (dockSide.machines().isEmpty() && !dockSide.hasPending()) {
            dockSide.assignBlock(null);
            sides[side] = null;
        }
        linksChanged();
        return true;
    }

    /** A volta do resultado desta Doca (o visitante do motor). */
    public DockReturn returns() {
        return returns;
    }

    /** O objeto do AE2 da face {@code side} ({@link DockAe2}), ou {@code null} antes da primeira consulta. */
    public @Nullable Object ae2Face(int side) {
        return ae2Faces == null ? null : ae2Faces[side];
    }

    public void setAe2Face(int side, Object face) {
        if (ae2Faces == null) {
            ae2Faces = new Object[SIDES];
        }
        ae2Faces[side] = face;
    }

    /**
     * O tier ou a config mudou (Cartão de Upgrade, recarga): o alcance, o limite e a vazão mudaram com ele. As máquinas
     * que ficaram ativas voltam à fila da volta, como no {@link #onLoad}.
     */
    public void reachChanged() {
        refreshIndex();
        if (level != null && !level.isClientSide) {
            returns.refreshRates();
            wakeReturns();
        }
    }

    /**
     * Põe na fila da volta cada máquina ativa: um resultado que já estava na saída de uma máquina parada (que não chama
     * {@code setChanged} de novo) volta sem esperar ela mudar. O(máquinas) uma vez; as vazias saem da fila na primeira
     * visita.
     */
    private void wakeReturns() {
        for (DockMachine machine : machineAt.values()) {
            returns.wake(machine);
        }
    }

    /** O que só muda o NBT: marca o chunk sem avisar os vizinhos (ninguém lê a Doca por comparador). */
    public void saveChanged() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    private void linksChanged() {
        if (batching) {
            batchDirty = true;
            return;
        }
        saveChanged();
        refreshIndex();
    }

    /**
     * Refaz o registro no {@link RemoteIndex} e a marca {@link DockMachine#active} de cada máquina: no alcance do tier
     * e até o máximo, na ordem dos lados e de vínculo. Todas as vinculadas, ativas ou não, vão ao {@link DockIndex}
     * (a unicidade entre Docas). Só no servidor e com a Doca no mundo.
     */
    private void refreshIndex() {
        if (level == null || level.isClientSide || isRemoved()) {
            return;
        }
        RouterTier tier = tier();
        int radius = Config.dockRadius(tier);
        int max = Config.dockMaxMachines(tier);
        List<RemoteMachine> active = new ArrayList<>(Math.min(machineAt.size(), max));
        for (DockSide side : sides) {
            if (side == null) {
                continue;
            }
            int sideActive = 0;
            for (DockMachine machine : side.machines()) {
                BlockPos pos = machine.pos();
                boolean inReach = active.size() < max && RangeRules.chebyshev(worldPosition.getX(),
                        worldPosition.getY(), worldPosition.getZ(), pos.getX(), pos.getY(), pos.getZ()) <= radius;
                machine.setActive(inReach);
                if (inReach) {
                    active.add(machine.remote());
                    sideActive++;
                }
            }
            side.setActiveMachines(sideActive);
        }
        RemoteIndex.get().update(this, active);
        DockIndex.get().update(this, level, machineAt.keySet().toLongArray());
    }

    /** A Doca saiu do mundo ou o chunk dela descarregou: sai do índice, e os caches das máquinas deixam de valer. */
    private void leave() {
        if (level == null || level.isClientSide) {
            return;
        }
        RemoteIndex.get().remove(this);
        DockIndex.get().remove(this);
        NetworkManager.get().removeVisitor(returns);
        returns.discard();
        for (DockMachine machine : machineAt.values()) {
            machine.discard();
        }
    }

    /**
     * O ponto único dos avisos de uma máquina (o {@code setChanged} dela, a carga do chunk dela e um cache de
     * capability invalidado): marca a máquina como mudada ({@link DockMachine#changed}), a acorda para a entrega e
     * para a volta ({@link DockReturn#wake}) e, se ela tem o pendente do lado, enfileira uma tentativa dele ({@link DockPending#flushSoon}). A tentativa não
     * roda aqui: o aviso chega de dentro do {@code setChanged} da máquina, de uma entrega do motor ou de um listener
     * de cache, onde inserir nela reentraria (ver o {@code flushSoon}).
     */
    void machineChanged(DockMachine machine) {
        machine.markChanged();
        machine.wake();
        returns.wake(machine);
        DockSide side = sides[machine.side()];
        if (side != null && side.hasPending() && side.pendingAtKey() == machine.pos().asLong()) {
            DockPending.flushSoon(this, side);
        }
    }

    /** Algum lado tem pendente: a quebra leva o item com o componente {@code pattern_dock_pending}. */
    public boolean hasPending() {
        for (DockSide side : sides) {
            if (side != null && side.hasPending()) {
                return true;
            }
        }
        return false;
    }

    /** A quebra vai rodar a loot table depois de tirar o bloco (sobrevivência com a ferramenta certa). */
    void expectDrop() {
        dropExpected = true;
    }

    /**
     * O bloco saiu do mundo sem um item da Doca com o pendente ({@code /setblock}, a remoção de outro mod sem loot, a
     * Doca que a explosão destruiu sem drop): os itens pendentes caem no chão e os fluidos se perdem. Com o item feito
     * ({@link #collectImplicitComponents}, ou o {@link #expectDrop} da quebra no sobrevivência), o pendente fica só
     * no item. Não esvazia os lados: a loot table do sobrevivência roda depois de tirar o bloco e lê este block entity.
     */
    void dropPendingWithoutItem() {
        if (dropExpected || level == null || level.isClientSide) {
            return;
        }
        for (DockSide side : sides) {
            if (side == null || !side.hasPending()) {
                continue;
            }
            for (ItemStack stack : side.pendingItems()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                        stack.copy());
            }
        }
    }

    /** O drop leva o pendente de todos os lados, com o bloco de cada um; sem pendente, nada (e empilha). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        List<DockPendingContents.Side> pending = new ArrayList<>(0);
        for (DockSide side : sides) {
            if (side != null && side.hasPending() && side.block() != null) {
                pending.add(new DockPendingContents.Side(side.side(), side.block(), side.pendingItems(),
                        side.pendingFluids()));
            }
        }
        if (!pending.isEmpty()) {
            components.set(ModDataComponents.PATTERN_DOCK_PENDING.get(), new DockPendingContents(pending));
            // um item com o pendente existe: se o bloco sair agora, nada cai no chão
            dropExpected = true;
        }
    }

    /**
     * Colocada a partir de um item com pendente: cada lado volta com o bloco e o pendente. Sem máquinas, o lado guarda
     * o bloco até o pendente sair na primeira máquina daquele bloco vinculada a ele.
     */
    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        DockPendingContents contents = input.get(ModDataComponents.PATTERN_DOCK_PENDING.get());
        if (contents == null) {
            return;
        }
        for (DockPendingContents.Side saved : contents.sides()) {
            DockSide side = sides[saved.side()];
            if (side == null) {
                side = new DockSide(saved.side());
                sides[saved.side()] = side;
            }
            if (side.block() == null) {
                side.assignBlock(saved.block());
            } else if (!side.block().equals(saved.block()) || side.hasPending()) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>(saved.items().size());
            for (ItemStack stack : saved.items()) {
                items.add(stack.copy());
            }
            List<FluidStack> fluids = new ArrayList<>(saved.fluids().size());
            for (FluidStack stack : saved.fluids()) {
                fluids.add(stack.copy());
            }
            // a posição da própria Doca nunca é de uma máquina: o pendente vai à primeira ativa do lado que o sirva
            side.setPending(items, fluids, worldPosition);
        }
    }

    /**
     * Uma máquina avisou mudança pelo índice. Com o estado do bloco, relê a frente, menos durante a visita de uma porta
     * de roteador ({@link NetworkManager#visiting()}), que é uma entrega ou retirada e nunca gira a máquina. Na visita da
     * volta da Doca e numa entrega da Doca o {@code visiting()} é falso e a frente é relida, o que só custa a leitura.
     */
    @Override
    public void remoteMachineChanged(RemoteMachine remote, @Nullable BlockState state) {
        DockMachine machine = machineAt.get(remote.pos().asLong());
        if (machine == null) {
            return;
        }
        if (state != null && !NetworkManager.visiting()) {
            int front = RemoteMachines.frontOf(state);
            if (front != machine.front()) {
                machine.setFront(front);
                saveChanged();
            }
        }
        machineChanged(machine);
    }

    /** Um cache de capability de uma máquina foi invalidado (a block entity saiu ou entrou). */
    @Override
    public void machineInvalidated(MachineCaches caches, ResourceType type, Direction face) {
        DockMachine machine = machineAt.get(caches.pos().asLong());
        if (machine != null && machine.cachesIfCreated() == caches) {
            machineChanged(machine);
        }
    }

    @Override
    public @Nullable Level listenerLevel() {
        return level;
    }

    @Override
    public BlockPos listenerPos() {
        return worldPosition;
    }

    /**
     * Um lado entregou ou devolveu no tick {@code now} (o {@code getTickCount} do servidor): manda o evento de trabalho
     * com o lado, no máximo um a cada {@link DockWork#EVENT_INTERVAL} ticks por Doca. Custo: uma comparação; o evento
     * vai para a fila de eventos de bloco do nível, que junta os iguais do mesmo tick.
     */
    public void signalWork(int side, long now) {
        if (level == null || level.isClientSide || !DockWork.shouldSend(now, lastWorkSent)) {
            return;
        }
        lastWorkSent = now;
        level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_WORK, side);
    }

    /** No servidor: quantos eventos de trabalho o jogo já processou nesta Doca. */
    public int workEvents() {
        return workEvents;
    }

    /** No cliente: o relógio do laço de trabalho (o tick e o lado do último evento). */
    public DockWork work() {
        return work;
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != EVENT_WORK || param < 0 || param >= SIDES) {
            return super.triggerEvent(id, param);
        }
        if (level != null && level.isClientSide) {
            work.event(level.getGameTime(), param);
        } else {
            workEvents++;
        }
        return true;
    }

    @Override
    public @Nullable Level ownerLevel() {
        return level;
    }

    @Override
    public boolean ownerRemoved() {
        return isRemoved();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshIndex();
        if (level != null && !level.isClientSide) {
            returns.refreshRates();
            NetworkManager.get().addVisitor(returns);
            wakeReturns();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        leave();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        leave();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (DockSide side : sides) {
            if (side != null) {
                list.add(side.save(registries));
            }
        }
        if (!list.isEmpty()) {
            tag.put("sides", list);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (DockMachine machine : machineAt.values()) {
            machine.discard();
        }
        machineAt.clear();
        Arrays.fill(sides, null);
        ListTag list = tag.getList("sides", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            DockSide side = DockSide.load(list.getCompound(i), registries);
            if (side == null || sides[side.side()] != null) {
                continue;
            }
            sides[side.side()] = side;
            for (DockMachine machine : side.machines()) {
                machineAt.putIfAbsent(machine.pos().asLong(), machine);
            }
        }
        // Um NBT carregado com a Doca já no mundo (um comando, um esquema colado) refaz o registro; na carga do
        // chunk o level ainda é nulo e quem refaz é o onLoad.
        refreshIndex();
    }
}
