package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.network.RateMath;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoadState;
import io.github.matheusanbs.wirelessautomate.chunk.RouterChunkLoader;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.item.ChunkLoaderUpgradeItem;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.item.RangeUpgradeItem;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.IdleNetworkFace;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RangeRules;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import io.github.matheusanbs.wirelessautomate.storage.BulkSource;
import io.github.matheusanbs.wirelessautomate.storage.BulkFluids;
import io.github.matheusanbs.wirelessautomate.storage.BulkItems;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Dados de um nó. Não faz tick: quem move recursos é o {@link NetworkManager}, que só conhece
 * os nós carregados no servidor.
 *
 * <p>A configuração das faces é guardada por {@link RelativeSide}, então girar o bloco gira a
 * configuração junto. A API recebe e devolve faces absolutas da máquina.
 *
 * <p>Para a tela do roteador há duas coisas baratas: {@link #changeVersion()}, que muda a cada
 * alteração do que a tela mostra (a tela aberta compara e reenvia; com ela fechada ninguém olha), e
 * os totais movidos por tipo ({@link #moved}), somados pelo motor numa visita que moveu algo.
 *
 * <p>Cartões de Filtro: cada face, para itens e para fluidos, tem {@link #CARD_SLOTS} slots de
 * cartão, guardados por lado relativo como a configuração. O motor junta o filtro embutido e os dos
 * cartões num {@link FilterSet} na montagem das rotas ({@link #filterSet}). Os cartões são itens
 * físicos: saem do roteador quebrado e não vão no preset do Configurador (que só copia a
 * configuração; o cartão é duplicado por receita ou exportando o filtro).
 *
 * <p>Upgrade de chunk loading: um slot ({@link #upgrade()}), com o dono (quem pôs o upgrade). Quem
 * força os chunks é o {@link RouterChunkLoader}, avisado quando o upgrade muda, quando o nó carrega,
 * descarrega ou sai do mundo. Como os cartões, o upgrade sai do roteador quebrado e fica no upgrade
 * de tier (o block entity é mantido).
 *
 * <p>Este block entity é a API do nó para o resto do mod e delega às partes: as faces e as redes
 * ({@link RouterFaces}), os cartões ({@link RouterCards}), os upgrades ({@link RouterUpgrades}), a edição
 * por alvo ({@link RouterTargets}), o alcance ({@link RouterRange}, com os caches das remotas em
 * {@link RouterRemoteCaches}), a máquina presa e o sinal de redstone ({@link RouterNeighbors}), a presença no
 * motor, no índice e no chunk loading ({@link RouterPresence}), o aviso das mudanças ({@link RouterChanges}) e o
 * NBT ({@link RouterNbt}).
 */
public class RouterBlockEntity extends BlockEntity {
    private static final ResourceType[] TYPES = ResourceType.values();

    /** Nome do nó dado pelo jogador; vazio = sem nome. */
    private String name = "";
    /** Nome dado à máquina presa; vazio sem nome. Fica quando o bloco preso é trocado. */
    private String attachedLabel = "";
    /** Configuração das faces da presa, por tipo e lado relativo. */
    private final RouterFaces faces = new RouterFaces();
    /** Muda a cada alteração do que a tela mostra. Não é salvo. */
    private int changeVersion;
    /** Total movido como origem, por {@link ResourceType#ordinal()}, desde que o nó carregou. Não é salvo. */
    private final long[] moved = new long[TYPES.length];
    /** Slots de cartão por face e por tipo. */
    public static final int CARD_SLOTS = 2;
    /** Cartões de Filtro da presa, por tipo, lado relativo e slot. */
    private final RouterCards cards = new RouterCards();
    /** Os slots dos upgrades de chunk loading (com o dono) e de alcance. */
    private final RouterUpgrades upgrades = new RouterUpgrades();
    /** Máquinas remotas vinculadas e os grupos delas; guardadas mesmo sem o upgrade (aí não viram porta). */
    private final RemoteMachines remotes = new RemoteMachines();
    /** A máquina presa (caches, bloco e frente) e o sinal de redstone. */
    private final RouterNeighbors neighbors = new RouterNeighbors(this);
    /** O alcance: vincular, desvincular, a frente das remotas e os caches delas. */
    private final RouterRange range = new RouterRange(this, remotes);
    /** A edição por alvo (presa, grupo ou máquina remota). */
    private final RouterTargets targets = new RouterTargets(this, faces, remotes);
    /** Entrar e sair do motor, do índice e do chunk loading. */
    private final RouterPresence presence = new RouterPresence(this, neighbors, range);
    /** O aviso das mudanças, com o lote de {@link #batchChanges}. */
    private final RouterChanges changes = new RouterChanges(this);

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROUTER.get(), pos, state);
    }

    public RouterTier tier() {
        return getBlockState().getValue(RouterBlock.TIER);
    }

    /**
     * <b>Atalho de transição, só para testes, e2e e bench</b>: a rede comum às seis faces da máquina
     * presa no tipo {@code type}, ou {@code null} se nenhuma tem rede ou se estão em redes diferentes.
     * A rede é da face: use {@link #faceNetwork(ResourceType, Direction)},
     * {@link #faceNetwork(EditTarget, ResourceType, Direction)} ou {@link #networks(ResourceType)}.
     */
    public @Nullable UUID networkId(ResourceType type) {
        return faces.commonNetwork(type);
    }

    /**
     * Atalho: põe as seis faces da presa, dos perfis e das exceções do tipo {@code type} na rede
     * {@code networkId} ({@code null} tira da rede). É o {@link #setNetworkAllFaces}; a rede é da face
     * ({@link #setFaceNetwork(ResourceType, Direction, UUID)}).
     */
    public void setNetworkId(ResourceType type, @Nullable UUID networkId) {
        setNetworkAllFaces(List.of(type), networkId);
    }

    /** Atalho: todas as faces de todos os tipos na rede {@code networkId}. Ver {@link #setNetworkAllFaces}. */
    public void setNetworkId(@Nullable UUID networkId) {
        setNetworkAllFaces(Arrays.asList(TYPES), networkId);
    }

    /** Atalho: todas as faces dos tipos {@code types} na rede {@code networkId}. Ver {@link #setNetworkAllFaces}. */
    public void setNetworkId(Collection<ResourceType> types, @Nullable UUID networkId) {
        setNetworkAllFaces(types, networkId);
    }

    /** Rede da face {@code machineFace} (absoluta) da máquina presa no tipo {@code type}; {@code null} = nenhuma. */
    public @Nullable UUID faceNetwork(ResourceType type, Direction machineFace) {
        return face(type, machineFace).network();
    }

    /** Põe a face {@code machineFace} (absoluta) da máquina presa na rede {@code network} ({@code null} tira). */
    public void setFaceNetwork(ResourceType type, Direction machineFace, @Nullable UUID network) {
        if (face(type, machineFace).setNetwork(network)) {
            changed(NetworkManager.typeBit(type));
        }
    }

    /**
     * Põe a face {@code side} (relativa ao {@code facing} + {@code spin}, como os presets a guardam)
     * da máquina presa na rede {@code network} ({@code null} tira).
     */
    public void setFaceNetwork(ResourceType type, RelativeSide side, @Nullable UUID network) {
        if (face(type, side).setNetwork(network)) {
            changed(NetworkManager.typeBit(type));
        }
    }

    /** Rede da face {@code side} do alvo, no quadro da tela: numa máquina remota, a que vale (exceção ou perfil). */
    public @Nullable UUID faceNetwork(EditTarget target, ResourceType type, Direction side) {
        return face(target, type, side).network();
    }

    /** Põe a face {@code side} do alvo (quadro da tela) na rede {@code network}, como as outras edições por alvo. */
    public void setFaceNetwork(EditTarget target, ResourceType type, Direction side, @Nullable UUID network) {
        targets.setFaceNetwork(target, type, side, network);
    }

    /** Tira a rede das faces sem modo de um grupo ou de uma exceção, numa mudança só; devolve o que tirou. */
    public List<IdleNetworkFace> clearIdleNetworks(EditTarget target) {
        return targets.clearIdleNetworks(target);
    }

    /** Devolve as redes às faces de {@code faces} que continuam sem modo e sem rede, numa mudança só; quantas. */
    public int restoreIdleNetworks(EditTarget target, List<IdleNetworkFace> faces) {
        return targets.restoreIdleNetworks(target, faces);
    }

    /**
     * Põe as faces absolutas da máquina presa marcadas em {@code absoluteFaceMask} (bit =
     * {@link Direction#ordinal()}) dos tipos {@code types} na rede {@code network}, com um aviso só.
     * Devolve quantas faces mudaram (Vinculador).
     */
    public int setAttachedFacesNetwork(Collection<ResourceType> types, int absoluteFaceMask, @Nullable UUID network) {
        return targets.setAttachedNetwork(types, absoluteFaceMask, network);
    }

    /** As faces absolutas da presa em {@code absoluteFaceMask} e {@code types} já estão em {@code network} (Vinculador). */
    public boolean attachedFacesIn(Collection<ResourceType> types, int absoluteFaceMask, @Nullable UUID network) {
        return faces.attachedIn(types, facing(), spin(), absoluteFaceMask, network);
    }

    /**
     * Põe as seis faces da máquina presa, dos perfis de todos os grupos e das exceções, nos tipos
     * {@code types}, na rede {@code network} ({@code null} tira da rede), com um aviso só. Usado pelo
     * Tablet, pelo Vinculador e pelos atalhos {@link #setNetworkId}.
     */
    public void setNetworkAllFaces(Collection<ResourceType> types, @Nullable UUID network) {
        setNetworkAllFaces(types, network, from -> true);
    }

    /**
     * Como {@link #setNetworkAllFaces(Collection, UUID)}, mas uma face numa rede de onde
     * {@code canLeave} recusa tirar (recebe a rede atual da face, nunca nula) fica como está: o Tablet
     * só tira faces de redes que o jogador gerencia. Faces sem rede, ou já na rede {@code network},
     * não perguntam. Devolve quantas faces ficaram por causa do {@code canLeave}.
     */
    public int setNetworkAllFaces(Collection<ResourceType> types, @Nullable UUID network, Predicate<UUID> canLeave) {
        return targets.setNetworkAll(types, network, canLeave);
    }

    /**
     * As redes distintas do tipo {@code type} (sem nulo, na ordem em que aparecem) nas faces da presa,
     * dos perfis e das exceções. Sem cache: só o motor (no {@code nodeChanged}), o índice e o snapshot
     * chamam.
     */
    public UUID[] networks(ResourceType type) {
        return faces.networks(remotes, type);
    }

    /** Alguma face (presa, perfil ou exceção) de algum tipo está numa rede. */
    public boolean hasNetwork() {
        return faces.hasNetwork(remotes);
    }

    /**
     * Frente horizontal da máquina presa ({@link RemoteMachines#frontOf}), ou −1 sem nível, sem frente
     * ou com a máquina num chunk descarregado (na borda do chunk ela pode estar no vizinho; ler o bloco
     * o carregaria à força). Só para a tela e o Vinculador, nunca por tick.
     */
    public int attachedFront() {
        return neighbors.attachedFront();
    }

    /** Nome do nó; vazio se o jogador não deu um. */
    public String name() {
        return name;
    }

    /** Dá nome ao nó (limpo por {@link RenameRouterPayload#clean}); vazio tira o nome. Não muda rotas: não avisa o motor. */
    public void setName(String name) {
        String clean = RenameRouterPayload.clean(name);
        if (clean.equals(this.name)) {
            return;
        }
        this.name = clean;
        nameChanged();
    }

    /** Nome dado à máquina presa; vazio se o jogador não deu um. */
    public String attachedLabel() {
        return attachedLabel;
    }

    /** Dá nome à máquina presa (limpo por {@link RenameRouterPayload#clean}); vazio tira o nome. Não muda rotas. */
    public void setAttachedLabel(String label) {
        String clean = RenameRouterPayload.clean(label);
        if (clean.equals(attachedLabel)) {
            return;
        }
        attachedLabel = clean;
        nameChanged();
    }

    /** Um nome novo (do nó, de grupo ou de máquina) muda só a tela e o índice, nunca as rotas. */
    void nameChanged() {
        changeVersion++;
        setChanged();
        NodeIndex.track(this);
    }

    /**
     * Dá nome ao grupo, rótulo à máquina remota ({@link RouterRange#rename}) ou nome à máquina presa
     * ({@code ATTACHED}, que vale sem o Upgrade de Alcance); não muda rotas.
     */
    public boolean renameTarget(EditTarget target, String name) {
        if (target.kind() == EditTarget.Kind.ATTACHED) {
            String before = attachedLabel;
            setAttachedLabel(name);
            return !before.equals(attachedLabel);
        }
        return range.rename(target, name);
    }

    /**
     * Versão do que a tela do roteador mostra: muda com faces, rede, nome, {@code facing}, tier e quando a
     * máquina muda (cache de capability invalidado ou bloco trocado). O sinal de redstone não a muda: quem o
     * mostra confere à parte. Só serve para comparar com uma versão lida antes.
     */
    public int changeVersion() {
        return changeVersion;
    }

    /** A máquina mudou (bloco trocado ou removido): a tela aberta remonta o snapshot. Não mexe nas rotas. */
    public void machineChanged() {
        changeVersion++;
        NodeIndex.track(this);
    }

    /**
     * O vizinho na posição da máquina avisou mudança: só conta como máquina mudada se o <b>bloco</b>
     * dela mudou. Trocar só o estado (fornalha acesa, máquina "ativa") não refaz o índice nem a tela.
     */
    public void machineNeighborChanged() {
        neighbors.machineNeighborChanged();
    }

    /**
     * Soma o que o nó moveu como origem. Chamado pelo motor, uma vez por visita que moveu algo. Pode
     * dar a volta no {@code long}: as telas só usam a diferença entre amostras ({@link RateMath}).
     */
    public void addMoved(ResourceType type, long amount) {
        moved[type.ordinal()] += amount;
    }

    /** Total movido como origem desde que o nó carregou (itens, mB ou FE). */
    public long moved(ResourceType type) {
        return moved[type.ordinal()];
    }

    public Direction facing() {
        return getBlockState().getValue(RouterBlock.FACING);
    }

    /** Giro do roteador em torno do eixo da face onde está preso (0 a 3, de 90° cada). */
    public int spin() {
        return getBlockState().getValue(RouterBlock.SPIN);
    }

    /**
     * Gira o roteador para {@code newSpin} (0 a 3) sem mudar a configuração de nenhuma face absoluta
     * da máquina: como ela é salva por {@link RelativeSide}, faces e cartões de todos os tipos trocam
     * de lado relativo antes do estado novo. Só no servidor; o {@link #setBlockState} avisa o motor.
     */
    public void spinTo(int newSpin) {
        int oldSpin = spin();
        if (level == null || level.isClientSide || newSpin == oldSpin) {
            return;
        }
        int[] target = RouterFaces.spinTargets(facing(), oldSpin, newSpin);
        faces.rotate(target);
        cards.rotate(target);
        setChanged();
        level.setBlock(worldPosition, getBlockState().setValue(RouterBlock.SPIN, newSpin), Block.UPDATE_ALL);
    }

    /** Posição da máquina onde o roteador está preso. */
    public BlockPos machinePos() {
        return RouterBlock.attachedPos(getBlockState(), worldPosition);
    }

    /** Configuração da face {@code machineFace} (absoluta) da máquina para {@code type}. Nunca nula. */
    public FaceConfig face(ResourceType type, Direction machineFace) {
        return face(type, RelativeSide.fromAbsolute(facing(), spin(), machineFace));
    }

    /** Configuração pelo lado relativo ao {@code facing}, como presets a guardam. Nunca nula. */
    public FaceConfig face(ResourceType type, RelativeSide side) {
        return faces.get(type, side);
    }

    public void setMode(ResourceType type, Direction machineFace, PortMode mode) {
        if (face(type, machineFace).setMode(mode)) {
            changed(NetworkManager.typeBit(type));
        }
    }

    public void setPriority(ResourceType type, Direction machineFace, int priority) {
        if (face(type, machineFace).setPriority(priority)) {
            changed(NetworkManager.typeBit(type));
        }
    }

    public void setRedstone(ResourceType type, Direction machineFace, RedstoneMode redstone) {
        if (face(type, machineFace).setRedstone(redstone)) {
            changed(NetworkManager.typeBit(type));
        }
    }

    /** Configura modo, prioridade e redstone de uma face (absoluta) de uma vez, avisando o motor uma vez só. */
    public void configureFace(ResourceType type, Direction machineFace, PortMode mode, int priority,
            RedstoneMode redstone) {
        FaceConfig config = face(type, machineFace);
        boolean changed = config.setMode(mode);
        changed |= config.setPriority(priority);
        changed |= config.setRedstone(redstone);
        if (changed) {
            changed(NetworkManager.typeBit(type));
        }
    }

    /** Troca o filtro da face. Energia não usa filtro, mas guardar não faz mal. */
    public void setFilter(ResourceType type, Direction machineFace, Filter filter) {
        if (face(type, machineFace).setFilter(filter)) {
            changed(NetworkManager.typeBit(type));
        }
    }

    // ------------------------------------------------------------------ cartões de filtro

    /** O tipo tem slots de cartão ({@link ResourceType#cards()}). */
    public static boolean hasCardSlots(ResourceType type) {
        return type.cards();
    }

    /** {@code stack} pode ir num slot de cartão de {@code type}: um Cartão de Filtro do mesmo tipo. */
    public static boolean acceptsCard(ResourceType type, ItemStack stack) {
        return hasCardSlots(type) && FilterCardItem.isCard(stack) && FilterCardItem.contents(stack).type() == type;
    }

    /** O cartão no slot; vazio se não houver ou se o tipo não tem slots. Não altere a pilha devolvida. */
    public ItemStack card(ResourceType type, RelativeSide side, int slot) {
        return cards.get(type, side, slot);
    }

    /**
     * Põe {@code stack} (guardada como está, sem cópia) no slot e avisa o motor. Não valida o
     * tipo: quem chama (o slot do menu) já validou com {@link #acceptsCard}; um cartão que não
     * vale para o tipo fica guardado, mas o motor o ignora.
     */
    public void setCard(ResourceType type, RelativeSide side, int slot, ItemStack stack) {
        if (cards.set(type, side, slot, stack)) {
            changed(NetworkManager.typeBit(type));
        }
    }

    /** Um cartão foi mexido no lugar (pilha do slot alterada): salva e remonta as rotas. */
    public void cardsChanged() {
        changed(RouterCards.CARD_TYPES_MASK);
    }

    /** Cartões nos slots da face (absoluta) para o tipo. */
    public int cardCount(ResourceType type, Direction machineFace) {
        return cards.count(type, RelativeSide.fromAbsolute(facing(), spin(), machineFace));
    }

    /**
     * Os filtros da face (absoluta) para o tipo: o embutido e os dos cartões válidos, na ordem dos
     * slots. Chamado pelo motor na montagem das rotas, não por tick; sem cartões não aloca.
     */
    public FilterSet filterSet(ResourceType type, Direction machineFace) {
        RelativeSide side = RelativeSide.fromAbsolute(facing(), spin(), machineFace);
        return cards.filterSet(type, side, face(type, side).filter());
    }

    /**
     * Os filtros do lado {@code side} da máquina remota para o tipo: o embutido da configuração que vale
     * para ela (exceção ou perfil) e os cartões válidos do grupo, na ordem dos slots. Chamado pelo
     * motor na montagem das rotas; sem cartões não aloca.
     */
    public FilterSet filterSet(RemoteMachine machine, ResourceType type, RemoteSide side) {
        return RouterCards.remoteFilterSet(remotes, machine, type, side);
    }

    /** Tira todos os cartões (para soltar no mundo quando o roteador sai). */
    public NonNullList<ItemStack> removeAllCards() {
        NonNullList<ItemStack> removed = NonNullList.create();
        cards.removeAll(removed);
        removed.addAll(remotes.removeAllCards());
        if (!removed.isEmpty()) {
            changed(RouterCards.CARD_TYPES_MASK);
        }
        return removed;
    }

    // ------------------------------------------------------------------ upgrade de chunk loading

    /** {@code stack} pode ir no slot de upgrade. */
    public static boolean acceptsUpgrade(ItemStack stack) {
        return stack.getItem() instanceof ChunkLoaderUpgradeItem;
    }

    /** O upgrade no slot; vazio se não houver. Não altere a pilha devolvida. */
    public ItemStack upgrade() {
        return upgrades.chunk();
    }

    /** Há um Upgrade de chunk loading no slot. */
    public boolean hasChunkUpgrade() {
        return acceptsUpgrade(upgrades.chunk());
    }

    /** Quem pôs o upgrade (o dono para o limite de chunks); {@code null} sem upgrade ou sem dono conhecido. */
    public @Nullable UUID upgradeOwner() {
        return upgrades.chunkOwner();
    }

    /**
     * Põe {@code stack} (guardada como está) no slot de upgrade, com o dono, e reavalia os tickets.
     * Não valida o item: quem chama (o slot do menu) já validou com {@link #acceptsUpgrade}.
     */
    public void setUpgrade(ItemStack stack, @Nullable UUID owner) {
        upgrades.setChunk(stack, owner);
        changeVersion++;
        setChanged();
        if (level != null && !level.isClientSide && !isRemoved()) {
            RouterChunkLoader.get().update(this);
        }
    }

    /** Tira o upgrade (para soltar no mundo quando o roteador sai); vazio se não havia. */
    public ItemStack removeUpgrade() {
        ItemStack removed = upgrades.chunk();
        if (!removed.isEmpty()) {
            setUpgrade(ItemStack.EMPTY, null);
        }
        return removed;
    }

    // ------------------------------------------------------------------ upgrade de alcance

    /** {@code stack} é um Upgrade de Alcance (sem olhar o tier do roteador). */
    public static boolean acceptsRangeUpgrade(ItemStack stack) {
        return stack.getItem() instanceof RangeUpgradeItem;
    }

    /** {@code stack} pode ir no slot de alcance deste roteador: é o upgrade e o raio do tier é maior que 0. */
    public boolean acceptsRangeUpgradeHere(ItemStack stack) {
        return acceptsRangeUpgrade(stack) && Config.machineRadius(tier()) > 0;
    }

    /** O Upgrade de Alcance no slot; vazio se não houver. Não altere a pilha devolvida. */
    public ItemStack rangeUpgrade() {
        return upgrades.range();
    }

    /**
     * O upgrade de alcance vale: está no slot e o raio do tier é maior que 0. Se a config baixar o
     * raio para 0 com o upgrade já colocado, ele fica no slot e só deixa de valer.
     */
    public boolean rangeActive() {
        return acceptsRangeUpgrade(upgrades.range()) && Config.machineRadius(tier()) > 0;
    }

    /**
     * Põe {@code stack} (guardada como está) no slot de alcance e avisa o motor para remontar as
     * portas do nó. Não valida o item: quem chama (o slot do menu) já validou.
     */
    public void setRangeUpgrade(ItemStack stack) {
        upgrades.setRange(stack);
        range.pruneCaches();
        range.remotesChanged();
        changed(NetworkManager.ALL_TYPES);
    }

    /** Tira o upgrade de alcance (para soltar no mundo quando o roteador sai); vazio se não havia. */
    public ItemStack removeRangeUpgrade() {
        ItemStack removed = upgrades.range();
        if (!removed.isEmpty()) {
            setRangeUpgrade(ItemStack.EMPTY);
        }
        return removed;
    }

    // ------------------------------------------------------------------ máquinas remotas

    /**
     * As máquinas remotas e os grupos. Para ler; mude pelos métodos deste roteador, que salvam e avisam
     * o motor.
     */
    public RemoteMachines remoteMachines() {
        return remotes;
    }

    /**
     * Atalho de {@link #linkMachine(BlockPos, UUID)} sem rede: um grupo novo nasce com as faces sem
     * rede (testes e e2e, que não têm jogador).
     */
    public boolean linkMachine(BlockPos pos) {
        return linkMachine(pos, null);
    }

    /**
     * Vincula o bloco em {@code pos} como máquina remota, lendo o id e a frente do mundo. Só no
     * servidor e com o chunk carregado. Recusa ar, a posição do próprio roteador e uma posição já
     * vinculada. As regras de raio, limite e dimensão ficam com quem chama (o Vinculador, que vincula
     * sempre sem rede). Se a máquina abre um grupo novo, o perfil dele nasce com todas as faces na rede
     * {@code network} ({@code null} = sem rede); um grupo que já existe não muda.
     */
    public boolean linkMachine(BlockPos pos, @Nullable UUID network) {
        return range.link(pos, network);
    }

    /** Desvincula a máquina do índice {@code index}; um grupo que esvazia sai e os cartões caem no roteador. */
    public boolean unlinkMachine(int index) {
        return range.unlink(index);
    }

    /** Como {@link #unlinkMachine(int)}, pela posição da máquina. */
    public boolean unlinkMachine(BlockPos pos) {
        return unlinkMachine(remotes.index(pos));
    }

    /** Tira a exceção da máquina do índice {@code index}: ela volta a seguir o perfil do grupo. */
    public boolean revertToProfile(int index) {
        return range.revertToProfile(index);
    }

    /**
     * Troca o perfil do grupo {@code groupId} por uma cópia de {@code config} (com as redes); as exceções que ficam
     * iguais ao perfil somem, as outras ficam. Devolve se mudou (o Tablet, ao colar e mover num grupo inteiro).
     */
    public boolean setGroupProfile(int groupId, MachineConfig config) {
        return range.setGroupProfile(groupId, config);
    }

    /**
     * Troca a exceção da máquina do índice {@code index} por uma cópia de {@code config}; {@code null}, ou igual ao
     * perfil do grupo, tira a exceção. Devolve se mudou (o Tablet, ao colar e mover numa máquina).
     */
    public boolean setMachineException(int index, @Nullable MachineConfig config) {
        return range.setMachineException(index, config);
    }

    /** O bloco no lugar da trocada ({@code reach} do {@link #machineReach}; carregada, outro bloco com capability). */
    public @Nullable ResourceLocation replacedBy(RemoteMachine machine, RangeRules.Reach reach) {
        return range.replacedBy(machine, reach);
    }

    /** Atualizar: as trocadas de {@code indices} vão para o grupo do bloco novo ({@link RemoteMachines#regroup}). */
    public RemoteMachines.Regroup updateReplaced(List<Integer> indices) {
        return range.updateReplaced(indices);
    }

    /**
     * Configuração da face {@code side} do alvo para {@code type}. O lado vem no quadro da tela: na
     * presa, a face absoluta da máquina; num grupo ou numa máquina remota, o quadro canônico
     * ({@link RemoteSide#fromCanonical}). Numa máquina, a que vale (exceção ou perfil). Nunca nula: um
     * alvo que não existe devolve uma configuração padrão solta. Não mude a devolvida; use os setters.
     */
    public FaceConfig face(EditTarget target, ResourceType type, Direction side) {
        return targets.face(target, type, side);
    }

    /**
     * Configura modo, prioridade e redstone de uma face do alvo, avisando o motor uma vez só. Na presa,
     * é o {@link #configureFace(ResourceType, Direction, PortMode, int, RedstoneMode)} de sempre.
     */
    public void configureFace(EditTarget target, ResourceType type, Direction side, PortMode mode, int priority,
            RedstoneMode redstone) {
        configureFace(target, type, side, mode, priority, redstone, null);
    }

    /**
     * Como {@link #configureFace(EditTarget, ResourceType, Direction, PortMode, int, RedstoneMode)}, e
     * se {@code joinNetwork} não é {@code null} e a face está sem rede, põe a face nela na mesma
     * edição: um aviso só ao motor na presa e uma exceção só numa máquina remota. Uma face que já
     * tem rede não muda de rede.
     */
    public void configureFace(EditTarget target, ResourceType type, Direction side, PortMode mode, int priority,
            RedstoneMode redstone, @Nullable UUID joinNetwork) {
        targets.configureFace(target, type, side, mode, priority, redstone, joinNetwork);
    }

    /** Troca o filtro embutido de uma face do alvo. */
    public void setFilter(EditTarget target, ResourceType type, Direction side, Filter filter) {
        targets.setFilter(target, type, side, filter);
    }

    /**
     * O cartão no slot de uma face do alvo; vazio se não houver. Num grupo ou numa máquina remota, são
     * os cartões do grupo (a máquina usa os do grupo dela). Não altere a pilha devolvida.
     */
    public ItemStack card(EditTarget target, ResourceType type, Direction side, int slot) {
        return targets.card(target, type, side, slot);
    }

    /** Põe {@code stack} (guardada como está) no slot de cartão do alvo e avisa o motor. Não valida o cartão. */
    public void setCard(EditTarget target, ResourceType type, Direction side, int slot, ItemStack stack) {
        targets.setCard(target, type, side, slot, stack);
    }

    /** Cartões nos slots de uma face do alvo para o tipo. */
    public int cardCount(EditTarget target, ResourceType type, Direction side) {
        return targets.cardCount(target, type, side);
    }

    /** Estado do upgrade para a tela. Só no servidor; no cliente, {@link ChunkLoadState#NONE}. */
    public ChunkLoadState chunkLoadState() {
        if (level == null || level.isClientSide) {
            return ChunkLoadState.NONE;
        }
        return RouterChunkLoader.get().state(this);
    }

    /** O estado do upgrade mudou (ativo, limite, desligado): a tela aberta remonta o snapshot. */
    public void chunkLoadStateChanged() {
        changeVersion++;
    }

    /**
     * Copia modo, prioridade, redstone e filtro de uma face, pelo lado relativo (presets e o
     * Configurador). A rede da face não muda: colar decide a rede à parte, pela regra das redes.
     */
    public void setFace(ResourceType type, RelativeSide side, FaceConfig config) {
        if (face(type, side).copySettingsFrom(config)) {
            changed(NetworkManager.typeBit(type));
        }
    }

    /** Sinal de redstone no roteador, guardado e atualizado em neighborChanged (sem consulta por tick). */
    public boolean powered() {
        return neighbors.powered();
    }

    /**
     * Relê o sinal de redstone. Chamado pelo bloco quando um vizinho muda. Com alguma face que usa
     * redstone ({@link #redstoneTypes}), trava ou destrava as portas dela no motor, sem remontar as rotas
     * (um relógio de redstone ao lado não custa remontagem).
     */
    public void updatePowered() {
        neighbors.updatePowered();
    }

    /**
     * Máscara ({@link NetworkManager#typeBit}) dos tipos com alguma face com modo e controle por
     * redstone: na presa, nos perfis dos grupos ou nas exceções das máquinas remotas (o sinal é o do
     * roteador e vale para todas).
     */
    public int redstoneTypes() {
        return faces.redstoneTypes(remotes);
    }

    /** Inventário da máquina pela face {@code machineFace}, via BlockCapabilityCache. */
    public @Nullable IItemHandler items(Direction machineFace) {
        return neighbors.attachedCaches().items(machineFace);
    }

    /**
     * Itens por tipo e quantidade (o Baú do mod) pela face, ou {@code null} se a máquina não tiver.
     * O motor tenta esta antes de {@link #items}: entre dois baús do mod, um tipo inteiro numa chamada.
     */
    public @Nullable BulkItems bulkItems(Direction machineFace) {
        return neighbors.attachedCaches().bulkItems(machineFace);
    }

    /**
     * Fluidos por tipo em {@code long} (o Tanque do mod) pela face, ou {@code null} se a máquina não
     * tiver. O motor tenta esta antes de {@link #fluids}: sem o teto de {@link Integer#MAX_VALUE} mB.
     */
    public @Nullable BulkFluids bulkFluids(Direction machineFace) {
        return neighbors.attachedCaches().bulkFluids(machineFace);
    }

    public @Nullable IFluidHandler fluids(Direction machineFace) {
        return neighbors.attachedCaches().fluids(machineFace);
    }

    /**
     * Energia em {@code long} (a Bateria do mod) pela face, ou {@code null} se a máquina não tiver. O
     * motor tenta esta antes de {@link #energy}: sem o teto de {@link Integer#MAX_VALUE} FE por chamada.
     */
    public @Nullable BulkEnergy bulkEnergy(Direction machineFace) {
        return neighbors.attachedCaches().bulkEnergy(machineFace);
    }

    public @Nullable IEnergyStorage energy(Direction machineFace) {
        return neighbors.attachedCaches().energy(machineFace);
    }

    /**
     * Handler de químicos do Mekanism ({@code IChemicalHandler}) pela face, ou {@code null} sem a
     * capability ou sem o Mekanism. Devolvido como {@code Object}: só o código de
     * {@code compat/mekanism} e o {@code ChemicalTransfer} sabem o tipo.
     */
    public @Nullable Object chemicals(Direction machineFace) {
        return neighbors.attachedCaches().chemicals(machineFace);
    }

    /**
     * Source em {@code long} (o Tanque de Source do mod) pela face, ou {@code null} se a máquina não
     * tiver. O motor tenta esta antes de {@link #arsSource}: sem o teto de {@link Integer#MAX_VALUE} por
     * chamada da capability do Ars.
     */
    public @Nullable BulkSource bulkSource(Direction machineFace) {
        return neighbors.attachedCaches().bulkSource(machineFace);
    }

    /**
     * Source do Ars Nouveau ({@code ISourceCap}) pela face, ou {@code null} sem a capability ou sem o
     * Ars. Devolvido como {@code Object}: só o código de {@code compat/arsnouveau} sabe o tipo.
     */
    public @Nullable Object arsSource(Direction machineFace) {
        return neighbors.attachedCaches().arsSource(machineFace);
    }

    /**
     * O estado de cada máquina remota no motor (ativa, fora do raio, além do limite), na ordem de
     * {@link RemoteMachines#machines()}, com o raio e o limite do tier na config. Não olha o upgrade:
     * sem ele ({@link #rangeActive()} falso) nenhuma vira porta.
     */
    public RangeRules.Reach[] machineReach() {
        RouterTier tier = tier();
        return remotes.reach(worldPosition, Config.machineRadius(tier), Config.maxMachines(tier));
    }

    /** Os caches de capability da máquina presa (uma instância só, que acompanha o giro). */
    public MachineCaches attachedCaches() {
        return neighbors.attachedCaches();
    }

    /**
     * Os caches da máquina remota em {@code pos}, criados na primeira consulta. Só para máquinas
     * vinculadas: o motor pede na leitura das portas; desvincular descarta.
     */
    public MachineCaches remoteCaches(BlockPos pos) {
        return range.caches(pos);
    }

    /**
     * A máquina remota {@code machine} avisou mudança (chamado pelo {@link RemoteIndex}: o
     * {@code setChanged} dela, pelo mixin, ou a carga do chunk dela). Com o estado do bloco
     * ({@code state}, nulo se não deu para ler sem carregar o chunk), relê a frente: girar a máquina
     * (com chave inglesa, por exemplo) não invalida a capability, então é aqui que os lados relativos
     * acompanham; se mudou, as rotas do nó são refeitas. Depois acorda as portas dela, pela regra do
     * motor ({@link NetworkManager#wakeMachine}).
     */
    public void remoteMachineChanged(RemoteMachine machine, @Nullable BlockState state) {
        range.machineChanged(machine, state);
    }

    /**
     * Relê a frente da máquina remota pelo mundo, só com o chunk carregado. Chamado pela remontagem das
     * portas ({@code NetworkRoutes.readRemotePorts}): grava e devolve {@code true} se mudou, sem
     * avisar o motor (quem chama decide o que remontar).
     */
    public boolean rereadFront(RemoteMachine machine) {
        return range.rereadFront(machine);
    }

    /**
     * Um cache de capability de {@code caches} foi invalidado (chamado pela {@link MachineCaches}). Na
     * presa, a tela refaz o snapshot e o motor acorda só a porta dessa face e tipo, e as origens que
     * esperam por ela; nada é remontado. Numa remota, a frente é relida (só com o chunk carregado): se
     * mudou, os lados relativos mudaram e as rotas do nó são refeitas; senão, acorda a porta da face.
     */
    public void machineInvalidated(MachineCaches caches, ResourceType type, Direction machineFace) {
        changeVersion++;
        if (level == null || level.isClientSide) {
            return;
        }
        if (!caches.isRemote()) {
            NetworkManager.get().capabilityChanged(this, type, machineFace);
            return;
        }
        range.invalidated(level, caches.pos(), type, machineFace);
    }

    /** O que a tela mostra mudou sem mexer nas rotas: a tela aberta remonta o snapshot. */
    void bumpChangeVersion() {
        changeVersion++;
    }

    /**
     * Junta todas as mudanças de configuração feitas em {@code changes} num aviso só: salvar, a tela,
     * o motor (com os tipos que mudaram somados) e o índice. Colar um preset mexe em dezenas de faces e
     * várias redes; sem isso seria um aviso por mudança. Pode ser aninhado: avisa ao sair do mais de fora.
     */
    public void batchChanges(Runnable changes) {
        this.changes.batch(changes);
    }

    /**
     * Salva e, no servidor, avisa o gerenciador que as rotas dos tipos {@code types} (máscara de
     * {@link NetworkManager#typeBit}) deste nó precisam ser refeitas. Dentro de {@link #batchChanges}
     * só acumula.
     */
    void changed(int types) {
        changes.changed(types);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        Direction oldFacing = facing();
        RouterTier oldTier = tier();
        int oldSpin = spin();
        super.setBlockState(state);
        presence.stateChanged(oldFacing, oldTier, oldSpin);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        presence.loaded();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        presence.chunkUnloaded();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        presence.removed();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!name.isEmpty()) {
            tag.putString("name", name);
        }
        if (!attachedLabel.isEmpty()) {
            tag.putString("attached_label", attachedLabel);
        }
        neighbors.save(tag);
        RouterNbt.save(tag, registries, faces, cards, upgrades, remotes);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UUID[] legacy = RouterNbt.legacyNetworks(tag);
        name = RenameRouterPayload.clean(tag.getString("name"));
        attachedLabel = RenameRouterPayload.clean(tag.getString("attached_label"));
        neighbors.load(tag);
        RouterNbt.load(tag, registries, faces, cards, upgrades, remotes, legacy);
        presence.nbtLoaded();
    }
}
