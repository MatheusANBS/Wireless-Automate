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
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RangeRules;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import io.github.matheusanbs.wirelessautomate.storage.BulkSource;
import io.github.matheusanbs.wirelessautomate.storage.BulkFluids;
import io.github.matheusanbs.wirelessautomate.storage.BulkItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.StringUtil;
import net.minecraft.world.Containers;
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
 */
public class RouterBlockEntity extends BlockEntity {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final RelativeSide[] SIDES = RelativeSide.values();
    private static final int FACES = Direction.values().length;

    /** Nome do nó dado pelo jogador; vazio = sem nome. */
    private String name = "";
    /** [tipo][lado relativo], tudo alocado de início: consultas não alocam. */
    private final FaceConfig[][] faces = new FaceConfig[TYPES.length][SIDES.length];
    private boolean powered;
    /** Muda a cada alteração do que a tela mostra. Não é salvo. */
    private int changeVersion;
    /** Total movido como origem, por {@link ResourceType#ordinal()}, desde que o nó carregou. Não é salvo. */
    private final long[] moved = new long[TYPES.length];
    /** Slots de cartão por face e por tipo. */
    public static final int CARD_SLOTS = 2;
    /** Tipos com slots de cartão, na ordem de {@link #cards}. */
    private static final ResourceType[] CARD_TYPES = Arrays.stream(TYPES).filter(ResourceType::cards)
            .toArray(ResourceType[]::new);
    private static final int CARD_TYPES_MASK = cardTypesMask();
    /** [tipo de {@link #CARD_TYPES}][lado relativo][slot], nunca nulos. */
    private final ItemStack[][][] cards = new ItemStack[CARD_TYPES.length][SIDES.length][CARD_SLOTS];
    /** Upgrade de chunk loading no slot (ou vazio) e quem o pôs. */
    private ItemStack upgrade = ItemStack.EMPTY;
    private @Nullable UUID upgradeOwner;
    /** Upgrade de Alcance no segundo slot (ou vazio). */
    private ItemStack rangeUpgrade = ItemStack.EMPTY;
    /** Máquinas remotas vinculadas e os grupos delas; guardadas mesmo sem o upgrade (aí não viram porta). */
    private final RemoteMachines remotes = new RemoteMachines();
    /** O chunk descarregou: o {@link #setRemoved()} que vem depois não é o roteador saindo do mundo. */
    private boolean chunkUnloading;

    /** Caches de capability da máquina presa; apontados de novo quando o roteador gira. */
    private final MachineCaches attachedCaches = MachineCaches.attached(this, BlockPos.ZERO);
    /** Caches das máquinas remotas, por {@code pos.asLong()}, criados sob demanda e descartados ao desvincular. */
    private @Nullable Long2ObjectOpenHashMap<MachineCaches> remoteCaches;
    /** Bloco da máquina no último aviso do vizinho ({@link #machineNeighborChanged}); {@code null} = desconhecido. */
    private @Nullable Block machineBlock;
    /** Profundidade de {@link #batchChanges} e os tipos que mudaram dentro dele. */
    private int batchDepth;
    private int batchTypes;

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROUTER.get(), pos, state);
        for (FaceConfig[] row : faces) {
            for (int i = 0; i < row.length; i++) {
                row[i] = new FaceConfig();
            }
        }
        clearCards();
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
        FaceConfig[] row = faces[type.ordinal()];
        UUID network = row[0].network();
        for (int i = 1; i < row.length; i++) {
            if (!Objects.equals(network, row[i].network())) {
                return null;
            }
        }
        return network;
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

    /**
     * Rede da face {@code side} do alvo de edição, no quadro da tela (como
     * {@link #face(EditTarget, ResourceType, Direction)}): numa máquina remota, a que vale (exceção ou perfil).
     */
    public @Nullable UUID faceNetwork(EditTarget target, ResourceType type, Direction side) {
        return face(target, type, side).network();
    }

    /**
     * Põe a face {@code side} do alvo (quadro da tela) na rede {@code network}. Num grupo, muda o
     * perfil; numa máquina remota, cria a exceção (ou a apaga se ficar igual ao perfil), como as
     * outras edições por alvo.
     */
    public void setFaceNetwork(EditTarget target, ResourceType type, Direction side, @Nullable UUID network) {
        if (target.attached()) {
            setFaceNetwork(type, side, network);
            return;
        }
        editRemote(target, type, side, config -> config.setNetwork(network));
    }

    /**
     * Põe as faces absolutas da máquina presa marcadas em {@code absoluteFaceMask} (bit =
     * {@link Direction#ordinal()}) dos tipos {@code types} na rede {@code network}, com um aviso só.
     * Devolve quantas faces mudaram (Vinculador).
     */
    public int setAttachedFacesNetwork(Collection<ResourceType> types, int absoluteFaceMask, @Nullable UUID network) {
        int[] count = new int[1];
        batchChanges(() -> {
            for (ResourceType type : types) {
                for (Direction face : Direction.values()) {
                    if ((absoluteFaceMask & (1 << face.ordinal())) != 0 && face(type, face).setNetwork(network)) {
                        count[0]++;
                        changed(NetworkManager.typeBit(type));
                    }
                }
            }
        });
        return count[0];
    }

    /**
     * Todas as faces absolutas da presa marcadas em {@code absoluteFaceMask}, nos tipos {@code types},
     * já estão na rede {@code network} (o "já estavam" do Vinculador).
     */
    public boolean attachedFacesIn(Collection<ResourceType> types, int absoluteFaceMask, @Nullable UUID network) {
        for (ResourceType type : types) {
            for (Direction face : Direction.values()) {
                if ((absoluteFaceMask & (1 << face.ordinal())) != 0
                        && !Objects.equals(network, face(type, face).network())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Põe as seis faces da máquina presa, dos perfis de todos os grupos e das exceções, nos tipos
     * {@code types}, na rede {@code network} ({@code null} tira da rede), com um aviso só. É o que a
     * rede da aba fazia antes (Tablet, atalhos de teste, bench, comandos).
     */
    public void setNetworkAllFaces(Collection<ResourceType> types, @Nullable UUID network) {
        batchChanges(() -> {
            for (ResourceType type : types) {
                boolean changed = false;
                for (FaceConfig config : faces[type.ordinal()]) {
                    changed |= config.setNetwork(network);
                }
                for (MachineGroup group : remotes.groups()) {
                    changed |= setNetwork(group.profile(), type, network);
                }
                for (RemoteMachine machine : remotes.machines()) {
                    MachineConfig exception = machine.exception();
                    if (exception != null) {
                        changed |= setNetwork(exception, type, network);
                    }
                }
                if (changed) {
                    changed(NetworkManager.typeBit(type));
                }
            }
            // como no editRemote: a exceção que ficou igual ao perfil do grupo some
            for (RemoteMachine machine : remotes.machines()) {
                MachineConfig exception = machine.exception();
                if (exception != null && exception.sameAs(remotes.groupOf(machine).profile())) {
                    machine.setException(null);
                }
            }
        });
    }

    private static boolean setNetwork(MachineConfig config, ResourceType type, @Nullable UUID network) {
        boolean changed = false;
        for (RemoteSide side : RemoteSide.values()) {
            changed |= config.face(type, side).setNetwork(network);
        }
        return changed;
    }

    /**
     * As redes distintas do tipo {@code type} (sem nulo, na ordem em que aparecem) nas faces da presa,
     * dos perfis e das exceções. Sem cache: só o motor (no {@code nodeChanged}), o índice e o snapshot
     * chamam.
     */
    public UUID[] networks(ResourceType type) {
        Set<UUID> found = new LinkedHashSet<>();
        for (FaceConfig config : faces[type.ordinal()]) {
            addNetwork(found, config);
        }
        for (MachineGroup group : remotes.groups()) {
            addNetworks(found, group.profile(), type);
        }
        for (RemoteMachine machine : remotes.machines()) {
            MachineConfig exception = machine.exception();
            if (exception != null) {
                addNetworks(found, exception, type);
            }
        }
        return found.toArray(UUID[]::new);
    }

    private static void addNetworks(Set<UUID> found, MachineConfig config, ResourceType type) {
        for (RemoteSide side : RemoteSide.values()) {
            addNetwork(found, config.face(type, side));
        }
    }

    private static void addNetwork(Set<UUID> found, FaceConfig config) {
        if (config.network() != null) {
            found.add(config.network());
        }
    }

    /** Alguma face (presa, perfil ou exceção) de algum tipo está numa rede. */
    public boolean hasNetwork() {
        for (ResourceType type : TYPES) {
            if (networks(type).length > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Frente horizontal da máquina presa ({@link RemoteMachines#frontOf}), ou −1 sem nível ou sem
     * frente. Só para a tela e o Vinculador, nunca por tick (a presa é vizinha, o chunk está carregado).
     */
    public int attachedFront() {
        return level == null ? -1 : RemoteMachines.frontOf(level.getBlockState(machinePos()));
    }

    /** Nome do nó; vazio se o jogador não deu um. */
    public String name() {
        return name;
    }

    /**
     * Dá nome ao nó: tira caracteres inválidos e espaços das pontas e corta em
     * {@link RenameRouterPayload#MAX_LENGTH}. Vazio tira o nome. Não muda rotas, então não avisa o motor.
     */
    public void setName(String name) {
        String clean = sanitizeName(name);
        if (clean.equals(this.name)) {
            return;
        }
        this.name = clean;
        changeVersion++;
        setChanged();
        NodeIndex.track(this);
    }

    private static String sanitizeName(String name) {
        String clean = StringUtil.filterText(name).strip();
        return clean.length() > RenameRouterPayload.MAX_LENGTH
                ? clean.substring(0, RenameRouterPayload.MAX_LENGTH).strip()
                : clean;
    }

    /**
     * Versão do que a tela do roteador mostra: muda com faces, rede, nome, sinal de redstone,
     * {@code facing}, tier e quando a máquina muda (cache de capability invalidado ou bloco
     * trocado). Só serve para comparar com uma versão lida antes.
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
        if (level == null) {
            return;
        }
        Block block = level.getBlockState(machinePos()).getBlock();
        if (block != machineBlock) {
            machineBlock = block;
            machineChanged();
            if (!level.isClientSide) {
                // Máquina nova: as portas dela (e quem espera por elas) tentam de novo, mesmo que a
                // troca não invalide nenhuma capability já consultada.
                NetworkManager.get().wake(this);
            }
        }
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
        Direction facing = facing();
        int[] target = new int[SIDES.length];
        for (RelativeSide side : SIDES) {
            target[side.ordinal()] = RelativeSide.fromAbsolute(facing, newSpin, side.toAbsolute(facing, oldSpin)).ordinal();
        }
        for (FaceConfig[] row : faces) {
            FaceConfig[] old = row.clone();
            for (int i = 0; i < old.length; i++) {
                row[target[i]] = old[i];
            }
        }
        for (ItemStack[][] byType : cards) {
            ItemStack[][] old = byType.clone();
            for (int i = 0; i < old.length; i++) {
                byType[target[i]] = old[i];
            }
        }
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
        return faces[type.ordinal()][side.ordinal()];
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

    private static int cardTypesMask() {
        int mask = 0;
        for (ResourceType type : CARD_TYPES) {
            mask |= NetworkManager.typeBit(type);
        }
        return mask;
    }

    private static int cardIndex(ResourceType type) {
        for (int i = 0; i < CARD_TYPES.length; i++) {
            if (CARD_TYPES[i] == type) {
                return i;
            }
        }
        return -1;
    }

    /** {@code stack} pode ir num slot de cartão de {@code type}: um Cartão de Filtro do mesmo tipo. */
    public static boolean acceptsCard(ResourceType type, ItemStack stack) {
        return hasCardSlots(type) && FilterCardItem.isCard(stack) && FilterCardItem.contents(stack).type() == type;
    }

    /** O cartão no slot; vazio se não houver ou se o tipo não tem slots. Não altere a pilha devolvida. */
    public ItemStack card(ResourceType type, RelativeSide side, int slot) {
        int index = cardIndex(type);
        if (index < 0 || slot < 0 || slot >= CARD_SLOTS) {
            return ItemStack.EMPTY;
        }
        return cards[index][side.ordinal()][slot];
    }

    /**
     * Põe {@code stack} (guardada como está, sem cópia) no slot e avisa o motor. Não valida o
     * tipo: quem chama (o slot do menu) já validou com {@link #acceptsCard}; um cartão que não
     * vale para o tipo fica guardado, mas o motor o ignora.
     */
    public void setCard(ResourceType type, RelativeSide side, int slot, ItemStack stack) {
        int index = cardIndex(type);
        if (index < 0 || slot < 0 || slot >= CARD_SLOTS) {
            return;
        }
        cards[index][side.ordinal()][slot] = stack;
        changed(NetworkManager.typeBit(type));
    }

    /** Um cartão foi mexido no lugar (pilha do slot alterada): salva e remonta as rotas. */
    public void cardsChanged() {
        changed(CARD_TYPES_MASK);
    }

    /** Cartões nos slots da face (absoluta) para o tipo. */
    public int cardCount(ResourceType type, Direction machineFace) {
        int index = cardIndex(type);
        if (index < 0) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : cards[index][RelativeSide.fromAbsolute(facing(), spin(), machineFace).ordinal()]) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Os filtros da face (absoluta) para o tipo: o embutido e os dos cartões válidos, na ordem dos
     * slots. Chamado pelo motor na montagem das rotas, não por tick; sem cartões não aloca.
     */
    public FilterSet filterSet(ResourceType type, Direction machineFace) {
        RelativeSide side = RelativeSide.fromAbsolute(facing(), spin(), machineFace);
        Filter embedded = face(type, side).filter();
        int index = cardIndex(type);
        if (index < 0) {
            return embedded.asSet();
        }
        List<Filter> fromCards = null;
        for (ItemStack stack : cards[index][side.ordinal()]) {
            if (acceptsCard(type, stack)) {
                if (fromCards == null) {
                    fromCards = new ArrayList<>(CARD_SLOTS);
                }
                fromCards.add(FilterCardItem.contents(stack).filter());
            }
        }
        return fromCards == null ? embedded.asSet() : FilterSet.of(embedded, fromCards);
    }

    /**
     * Os filtros do lado {@code side} da máquina remota para o tipo: o embutido da configuração que vale
     * para ela (exceção ou perfil) e os cartões válidos do grupo, na ordem dos slots. Chamado pelo
     * motor na montagem das rotas; sem cartões não aloca.
     */
    public FilterSet filterSet(RemoteMachine machine, ResourceType type, RemoteSide side) {
        Filter embedded = remotes.effective(machine).face(type, side).filter();
        if (MachineGroup.cardIndex(type) < 0) {
            return embedded.asSet();
        }
        MachineGroup group = remotes.groupOf(machine);
        List<Filter> fromCards = null;
        for (int slot = 0; slot < CARD_SLOTS; slot++) {
            ItemStack stack = group.card(type, side, slot);
            if (acceptsCard(type, stack)) {
                if (fromCards == null) {
                    fromCards = new ArrayList<>(CARD_SLOTS);
                }
                fromCards.add(FilterCardItem.contents(stack).filter());
            }
        }
        return fromCards == null ? embedded.asSet() : FilterSet.of(embedded, fromCards);
    }

    /** Tira todos os cartões (para soltar no mundo quando o roteador sai). */
    public NonNullList<ItemStack> removeAllCards() {
        NonNullList<ItemStack> removed = NonNullList.create();
        for (ItemStack[][] byType : cards) {
            for (ItemStack[] bySide : byType) {
                for (int i = 0; i < bySide.length; i++) {
                    if (!bySide[i].isEmpty()) {
                        removed.add(bySide[i]);
                        bySide[i] = ItemStack.EMPTY;
                    }
                }
            }
        }
        removed.addAll(remotes.removeAllCards());
        if (!removed.isEmpty()) {
            changed(CARD_TYPES_MASK);
        }
        return removed;
    }

    private void clearCards() {
        for (ItemStack[][] byType : cards) {
            for (ItemStack[] bySide : byType) {
                Arrays.fill(bySide, ItemStack.EMPTY);
            }
        }
    }

    // ------------------------------------------------------------------ upgrade de chunk loading

    /** {@code stack} pode ir no slot de upgrade. */
    public static boolean acceptsUpgrade(ItemStack stack) {
        return stack.getItem() instanceof ChunkLoaderUpgradeItem;
    }

    /** O upgrade no slot; vazio se não houver. Não altere a pilha devolvida. */
    public ItemStack upgrade() {
        return upgrade;
    }

    /** Há um Upgrade de chunk loading no slot. */
    public boolean hasChunkUpgrade() {
        return acceptsUpgrade(upgrade);
    }

    /** Quem pôs o upgrade (o dono para o limite de chunks); {@code null} sem upgrade ou sem dono conhecido. */
    public @Nullable UUID upgradeOwner() {
        return upgradeOwner;
    }

    /**
     * Põe {@code stack} (guardada como está) no slot de upgrade, com o dono, e reavalia os tickets.
     * Não valida o item: quem chama (o slot do menu) já validou com {@link #acceptsUpgrade}.
     */
    public void setUpgrade(ItemStack stack, @Nullable UUID owner) {
        upgrade = stack;
        upgradeOwner = stack.isEmpty() ? null : owner;
        changeVersion++;
        setChanged();
        if (level != null && !level.isClientSide && !isRemoved()) {
            RouterChunkLoader.get().update(this);
        }
    }

    /** Tira o upgrade (para soltar no mundo quando o roteador sai); vazio se não havia. */
    public ItemStack removeUpgrade() {
        ItemStack removed = upgrade;
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
        return rangeUpgrade;
    }

    /**
     * O upgrade de alcance vale: está no slot e o raio do tier é maior que 0. Se a config baixar o
     * raio para 0 com o upgrade já colocado, ele fica no slot e só deixa de valer.
     */
    public boolean rangeActive() {
        return acceptsRangeUpgrade(rangeUpgrade) && Config.machineRadius(tier()) > 0;
    }

    /**
     * Põe {@code stack} (guardada como está) no slot de alcance e avisa o motor para remontar as
     * portas do nó. Não valida o item: quem chama (o slot do menu) já validou.
     */
    public void setRangeUpgrade(ItemStack stack) {
        rangeUpgrade = stack;
        pruneRemoteCaches();
        remotesChanged();
        changed(NetworkManager.ALL_TYPES);
    }

    /** Tira o upgrade de alcance (para soltar no mundo quando o roteador sai); vazio se não havia. */
    public ItemStack removeRangeUpgrade() {
        ItemStack removed = rangeUpgrade;
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
     * Vincula o bloco em {@code pos} como máquina remota, lendo o id e a frente do mundo. Só no
     * servidor e com o chunk carregado. Recusa ar, a posição do próprio roteador e uma posição já
     * vinculada. As regras de raio, limite e dimensão ficam com quem chama (o Vinculador).
     */
    public boolean linkMachine(BlockPos pos) {
        if (level == null || level.isClientSide || !level.isLoaded(pos) || pos.equals(worldPosition)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()
                || !remotes.link(pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()), RemoteMachines.frontOf(state))) {
            return false;
        }
        remotesChanged();
        changed(NetworkManager.ALL_TYPES);
        return true;
    }

    /**
     * Desvincula a máquina do índice {@code index}. Se o grupo dela esvazia, ele sai e os cartões dele
     * caem na posição do roteador.
     */
    public boolean unlinkMachine(int index) {
        if (remotes.machine(index) == null) {
            return false;
        }
        dropAtRouter(remotes.unlink(index));
        pruneRemoteCaches();
        remotesChanged();
        changed(NetworkManager.ALL_TYPES);
        return true;
    }

    /** Como {@link #unlinkMachine(int)}, pela posição da máquina. */
    public boolean unlinkMachine(BlockPos pos) {
        return unlinkMachine(remotes.index(pos));
    }

    /** Tira a exceção da máquina do índice {@code index}: ela volta a seguir o perfil do grupo. */
    public boolean revertToProfile(int index) {
        RemoteMachine machine = remotes.machine(index);
        if (machine == null || !machine.hasException()) {
            return false;
        }
        machine.setException(null);
        changed(NetworkManager.ALL_TYPES);
        return true;
    }

    private void dropAtRouter(List<ItemStack> stacks) {
        if (level == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        }
    }

    /** O grupo de um alvo remoto (o próprio grupo, ou o da máquina); {@code null} na presa ou num alvo que não existe. */
    private @Nullable MachineGroup groupFor(EditTarget target) {
        return switch (target.kind()) {
            case ATTACHED -> null;
            case GROUP -> remotes.group(target.id());
            case MACHINE -> {
                RemoteMachine machine = remotes.machine(target.id());
                yield machine == null ? null : remotes.groupOf(machine);
            }
        };
    }

    /**
     * Configuração da face {@code side} do alvo para {@code type}. O lado vem no quadro da tela: na
     * presa, a face absoluta da máquina; num grupo ou numa máquina remota, o quadro canônico
     * ({@link RemoteSide#fromCanonical}). Numa máquina, a que vale (exceção ou perfil). Nunca nula: um
     * alvo que não existe devolve uma configuração padrão solta. Não mude a devolvida; use os setters.
     */
    public FaceConfig face(EditTarget target, ResourceType type, Direction side) {
        return switch (target.kind()) {
            case ATTACHED -> face(type, side);
            case GROUP -> {
                MachineGroup group = remotes.group(target.id());
                yield group == null ? new FaceConfig() : group.profile().face(type, remoteSide(side));
            }
            case MACHINE -> {
                RemoteMachine machine = remotes.machine(target.id());
                yield machine == null ? new FaceConfig() : remotes.effective(machine).face(type, remoteSide(side));
            }
        };
    }

    /**
     * Configura modo, prioridade e redstone de uma face do alvo, avisando o motor uma vez só. Na presa,
     * é o {@link #configureFace(ResourceType, Direction, PortMode, int, RedstoneMode)} de sempre.
     */
    public void configureFace(EditTarget target, ResourceType type, Direction side, PortMode mode, int priority,
            RedstoneMode redstone) {
        if (target.attached()) {
            configureFace(type, side, mode, priority, redstone);
            return;
        }
        editRemote(target, type, side, config -> config.setMode(mode)
                | config.setPriority(priority) | config.setRedstone(redstone));
    }

    /** Troca o filtro embutido de uma face do alvo. */
    public void setFilter(EditTarget target, ResourceType type, Direction side, Filter filter) {
        if (target.attached()) {
            setFilter(type, side, filter);
            return;
        }
        editRemote(target, type, side, config -> config.setFilter(filter));
    }

    /**
     * Muda uma face de um grupo ou de uma máquina remota. No grupo, muda o perfil (vale para as
     * máquinas sem exceção) e as exceções que ficaram iguais ao perfil somem. Numa máquina sem
     * exceção, copia o perfil para a exceção e aplica; se a exceção fica igual ao perfil, ela some. Um
     * aviso só ao motor, com o tipo.
     */
    private boolean editRemote(EditTarget target, ResourceType type, Direction side, Predicate<FaceConfig> edit) {
        RemoteSide remote = remoteSide(side);
        if (target.kind() == EditTarget.Kind.GROUP) {
            MachineGroup group = remotes.group(target.id());
            if (group == null || !edit.test(group.profile().face(type, remote))) {
                return false;
            }
            for (RemoteMachine machine : remotes.machines()) {
                MachineConfig exception = machine.exception();
                if (machine.group() == group.id() && exception != null && exception.sameAs(group.profile())) {
                    machine.setException(null);
                }
            }
            changed(NetworkManager.typeBit(type));
            return true;
        }
        RemoteMachine machine = remotes.machine(target.id());
        if (machine == null) {
            return false;
        }
        MachineConfig profile = remotes.groupOf(machine).profile();
        MachineConfig exception = machine.exception();
        MachineConfig config = exception != null ? exception : profile.copy();
        if (!edit.test(config.face(type, remote))) {
            return false;
        }
        machine.setException(config.sameAs(profile) ? null : config);
        changed(NetworkManager.typeBit(type));
        return true;
    }

    private static RemoteSide remoteSide(Direction canonical) {
        return RemoteSide.fromCanonical(canonical.ordinal());
    }

    /**
     * O cartão no slot de uma face do alvo; vazio se não houver. Num grupo ou numa máquina remota, são
     * os cartões do grupo (a máquina usa os do grupo dela). Não altere a pilha devolvida.
     */
    public ItemStack card(EditTarget target, ResourceType type, Direction side, int slot) {
        if (target.attached()) {
            return card(type, RelativeSide.fromAbsolute(facing(), spin(), side), slot);
        }
        MachineGroup group = groupFor(target);
        return group == null ? ItemStack.EMPTY : group.card(type, remoteSide(side), slot);
    }

    /** Põe {@code stack} (guardada como está) no slot de cartão do alvo e avisa o motor. Não valida o cartão. */
    public void setCard(EditTarget target, ResourceType type, Direction side, int slot, ItemStack stack) {
        if (target.attached()) {
            setCard(type, RelativeSide.fromAbsolute(facing(), spin(), side), slot, stack);
            return;
        }
        MachineGroup group = groupFor(target);
        if (group != null && group.setCard(type, remoteSide(side), slot, stack)) {
            changed(NetworkManager.typeBit(type));
        }
    }

    /** Cartões nos slots de uma face do alvo para o tipo. */
    public int cardCount(EditTarget target, ResourceType type, Direction side) {
        if (target.attached()) {
            return cardCount(type, side);
        }
        int count = 0;
        for (int slot = 0; slot < CARD_SLOTS; slot++) {
            if (!card(target, type, side, slot).isEmpty()) {
                count++;
            }
        }
        return count;
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
        return powered;
    }

    /**
     * Relê o sinal de redstone. Chamado pelo bloco quando um vizinho muda. Só remonta os tipos com
     * alguma face que usa redstone ({@link #redstoneTypes}): sem nenhuma, o sinal não muda rota (um
     * relógio de redstone ao lado não remonta a rede a cada pulso).
     */
    public void updatePowered() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean now = level.hasNeighborSignal(worldPosition);
        if (now != powered) {
            powered = now;
            changeVersion++;
            setChanged();
            int types = redstoneTypes();
            if (types != 0) {
                NetworkManager.get().nodeChanged(this, types);
            }
        }
    }

    /** Máscara ({@link NetworkManager#typeBit}) dos tipos com alguma face com modo e controle por redstone. */
    public int redstoneTypes() {
        int types = 0;
        for (ResourceType type : TYPES) {
            for (FaceConfig config : faces[type.ordinal()]) {
                if (config.redstone() != RedstoneMode.IGNORE && config.mode() != PortMode.NONE) {
                    types |= NetworkManager.typeBit(type);
                    break;
                }
            }
        }
        return types;
    }

    /** Inventário da máquina pela face {@code machineFace}, via BlockCapabilityCache. */
    public @Nullable IItemHandler items(Direction machineFace) {
        return attachedCaches.items(machineFace);
    }

    /**
     * Itens por tipo e quantidade (o Baú do mod) pela face, ou {@code null} se a máquina não tiver.
     * O motor tenta esta antes de {@link #items}: entre dois baús do mod, um tipo inteiro numa chamada.
     */
    public @Nullable BulkItems bulkItems(Direction machineFace) {
        return attachedCaches.bulkItems(machineFace);
    }

    /**
     * Fluidos por tipo em {@code long} (o Tanque do mod) pela face, ou {@code null} se a máquina não
     * tiver. O motor tenta esta antes de {@link #fluids}: sem o teto de {@link Integer#MAX_VALUE} mB.
     */
    public @Nullable BulkFluids bulkFluids(Direction machineFace) {
        return attachedCaches.bulkFluids(machineFace);
    }

    public @Nullable IFluidHandler fluids(Direction machineFace) {
        return attachedCaches.fluids(machineFace);
    }

    /**
     * Energia em {@code long} (a Bateria do mod) pela face, ou {@code null} se a máquina não tiver. O
     * motor tenta esta antes de {@link #energy}: sem o teto de {@link Integer#MAX_VALUE} FE por chamada.
     */
    public @Nullable BulkEnergy bulkEnergy(Direction machineFace) {
        return attachedCaches.bulkEnergy(machineFace);
    }

    public @Nullable IEnergyStorage energy(Direction machineFace) {
        return attachedCaches.energy(machineFace);
    }

    /**
     * Handler de químicos do Mekanism ({@code IChemicalHandler}) pela face, ou {@code null} sem a
     * capability ou sem o Mekanism. Devolvido como {@code Object}: só o código de
     * {@code compat/mekanism} e o {@code ChemicalTransfer} sabem o tipo.
     */
    public @Nullable Object chemicals(Direction machineFace) {
        return attachedCaches.chemicals(machineFace);
    }

    /**
     * Source em {@code long} (o Tanque de Source do mod) pela face, ou {@code null} se a máquina não
     * tiver. O motor tenta esta antes de {@link #arsSource}: sem o teto de {@link Integer#MAX_VALUE} por
     * chamada da capability do Ars.
     */
    public @Nullable BulkSource bulkSource(Direction machineFace) {
        return attachedCaches.bulkSource(machineFace);
    }

    /**
     * Source do Ars Nouveau ({@code ISourceCap}) pela face, ou {@code null} sem a capability ou sem o
     * Ars. Devolvido como {@code Object}: só o código de {@code compat/arsnouveau} sabe o tipo.
     */
    public @Nullable Object arsSource(Direction machineFace) {
        return attachedCaches.arsSource(machineFace);
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
        return attachedCaches;
    }

    /**
     * Os caches da máquina remota em {@code pos}, criados na primeira consulta. Só para máquinas
     * vinculadas: o motor pede na leitura das portas; desvincular descarta.
     */
    public MachineCaches remoteCaches(BlockPos pos) {
        if (remoteCaches == null) {
            remoteCaches = new Long2ObjectOpenHashMap<>();
        }
        long key = pos.asLong();
        MachineCaches caches = remoteCaches.get(key);
        if (caches == null) {
            caches = MachineCaches.remote(this, pos);
            remoteCaches.put(key, caches);
        }
        return caches;
    }

    /**
     * Descarta os caches (e os listeners deles) das remotas que não estão mais vinculadas; sem o
     * upgrade valendo, de todas. Chamado ao desvincular, ao carregar o NBT e quando o upgrade sai.
     */
    private void pruneRemoteCaches() {
        if (remoteCaches == null) {
            return;
        }
        boolean keepAny = rangeActive();
        var iterator = remoteCaches.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!keepAny || !remotes.contains(entry.getValue().pos())) {
                entry.getValue().discard();
                iterator.remove();
            }
        }
        if (remoteCaches.isEmpty()) {
            remoteCaches = null;
        }
    }

    /**
     * As máquinas vinculadas ou o upgrade mudaram: refaz o registro no {@link RemoteIndex} (só se o nó
     * está no motor e o upgrade vale; senão, só tira).
     */
    private void remotesChanged() {
        if (level != null && !level.isClientSide) {
            RemoteIndex.get().update(this);
        }
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
        // Durante uma visita do motor o aviso é a nossa entrega ou extração, que nunca gira a máquina.
        if (state != null && !NetworkManager.visiting()) {
            refreshFront(machine, state);
        }
        NetworkManager.get().wakeMachine(this, machine.pos());
    }

    /**
     * Relê a frente da máquina remota em {@code state}. Se mudou, grava e refaz as rotas do nó (os
     * lados relativos mudaram) e devolve {@code true}. A regra num lugar só: o aviso da máquina
     * ({@link #remoteMachineChanged}) e a invalidação da capability ({@link #machineInvalidated}).
     */
    private boolean refreshFront(RemoteMachine machine, BlockState state) {
        if (!storeFront(machine, state)) {
            return false;
        }
        changed(NetworkManager.ALL_TYPES);
        return true;
    }

    /**
     * Relê a frente da máquina remota pelo mundo, só com o chunk carregado. Chamado pela remontagem das
     * portas ({@code NetworkRoutes.readRemotePorts}): grava e devolve {@code true} se mudou, sem
     * avisar o motor (quem chama decide o que remontar).
     */
    public boolean rereadFront(RemoteMachine machine) {
        if (level == null || level.isClientSide || !level.isLoaded(machine.pos())) {
            return false;
        }
        return storeFront(machine, level.getBlockState(machine.pos()));
    }

    /** Grava a frente de {@code state} na máquina, se mudou (salva e refaz o snapshot), sem avisar o motor. */
    private boolean storeFront(RemoteMachine machine, BlockState state) {
        int front = RemoteMachines.frontOf(state);
        if (front == machine.front()) {
            return false;
        }
        machine.setFront(front);
        changeVersion++;
        setChanged();
        return true;
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
        BlockPos pos = caches.pos();
        if (level.isLoaded(pos)) {
            RemoteMachine machine = remotes.machine(remotes.index(pos));
            if (machine != null && refreshFront(machine, level.getBlockState(pos))) {
                return;
            }
        }
        NetworkManager.get().capabilityChanged(this, pos, type, machineFace);
    }

    /**
     * Junta todas as mudanças de configuração feitas em {@code changes} num aviso só: salvar, a tela,
     * o motor (com os tipos que mudaram somados) e o índice. Colar um preset mexe em até 24 faces e 4
     * redes; sem isso seriam até 28 avisos. Pode ser aninhado: avisa ao sair do mais de fora.
     */
    public void batchChanges(Runnable changes) {
        batchDepth++;
        try {
            changes.run();
        } finally {
            if (--batchDepth == 0 && batchTypes != 0) {
                int types = batchTypes;
                batchTypes = 0;
                notifyChanged(types);
            }
        }
    }

    /**
     * Salva e, no servidor, avisa o gerenciador que as rotas dos tipos {@code types} (máscara de
     * {@link NetworkManager#typeBit}) deste nó precisam ser refeitas. Dentro de {@link #batchChanges}
     * só acumula.
     */
    private void changed(int types) {
        if (batchDepth > 0) {
            batchTypes |= types;
            return;
        }
        notifyChanged(types);
    }

    private void notifyChanged(int types) {
        changeVersion++;
        setChanged();
        if (level != null && !level.isClientSide) {
            NetworkManager.get().nodeChanged(this, types);
            NodeIndex.track(this);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        Direction oldFacing = facing();
        RouterTier oldTier = tier();
        int oldSpin = spin();
        super.setBlockState(state);
        boolean rotated = facing() != oldFacing;
        // Girado no próprio eixo (spin): a máquina fica no lugar, então os caches valem; como o
        // upgrade de tier, só as rotas e a tela precisam saber.
        boolean changed = rotated || tier() != oldTier || spin() != oldSpin;
        if (changed) {
            changeVersion++;
        }
        if (rotated) {
            // Girado: a configuração relativa acompanha, mas a máquina mudou de lugar.
            attachedCaches.retarget(machinePos());
            machineBlock = null;
            if (level != null && !level.isClientSide && hasChunkUpgrade()) {
                // O chunk da máquina pode ser outro.
                RouterChunkLoader.get().update(this);
            }
        }
        // Upgrade de tier muda vazão e alcance das rotas; o giro muda o lado relativo de cada face.
        if (changed && level != null && !level.isClientSide) {
            NetworkManager.get().nodeChanged(this);
            NodeIndex.track(this);
            if (tier() != oldTier) {
                // O raio do tier novo decide se o upgrade de alcance vale.
                remotesChanged();
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            // Ao carregar o chunk os vizinhos podem não estar carregados; aí fica o valor salvo.
            if (level.isAreaLoaded(worldPosition, 1)) {
                powered = level.hasNeighborSignal(worldPosition);
                machineBlock = level.getBlockState(machinePos()).getBlock();
            }
            NetworkManager.get().addNode(this);
            NodeIndex.track(this);
            chunkUnloading = false;
            RouterChunkLoader.get().loaded(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        removeFromManager();
        if (level != null && !level.isClientSide) {
            // Descarregar não tira os tickets: eles são salvos e voltam com o mundo.
            chunkUnloading = true;
            RouterChunkLoader.get().unloaded(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        removeFromManager();
        if (level != null && !level.isClientSide && !chunkUnloading) {
            RouterChunkLoader.get().removed(this);
        }
    }

    private void removeFromManager() {
        if (level != null && !level.isClientSide) {
            NetworkManager.get().removeNode(this);
            NodeIndex.untrack(this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!name.isEmpty()) {
            tag.putString("name", name);
        }
        if (powered) {
            tag.putBoolean("powered", true);
        }
        CompoundTag facesTag = new CompoundTag();
        for (ResourceType type : TYPES) {
            CompoundTag typeTag = new CompoundTag();
            for (RelativeSide side : SIDES) {
                FaceConfig config = face(type, side);
                if (!config.isDefault()) {
                    typeTag.put(side.key(), config.save(registries));
                }
            }
            if (!typeTag.isEmpty()) {
                facesTag.put(type.key(), typeTag);
            }
        }
        if (!facesTag.isEmpty()) {
            tag.put("faces", facesTag);
        }
        CompoundTag cardsTag = new CompoundTag();
        for (int t = 0; t < CARD_TYPES.length; t++) {
            CompoundTag typeTag = new CompoundTag();
            for (RelativeSide side : SIDES) {
                ListTag list = new ListTag();
                ItemStack[] slots = cards[t][side.ordinal()];
                for (int slot = 0; slot < slots.length; slot++) {
                    if (!slots[slot].isEmpty()) {
                        CompoundTag slotTag = new CompoundTag();
                        slotTag.putByte("slot", (byte) slot);
                        list.add(slots[slot].save(registries, slotTag));
                    }
                }
                if (!list.isEmpty()) {
                    typeTag.put(side.key(), list);
                }
            }
            if (!typeTag.isEmpty()) {
                cardsTag.put(CARD_TYPES[t].key(), typeTag);
            }
        }
        if (!cardsTag.isEmpty()) {
            tag.put("cards", cardsTag);
        }
        if (!upgrade.isEmpty()) {
            tag.put("upgrade", upgrade.save(registries));
            if (upgradeOwner != null) {
                tag.putUUID("upgrade_owner", upgradeOwner);
            }
        }
        if (!rangeUpgrade.isEmpty()) {
            tag.put("range_upgrade", rangeUpgrade.save(registries));
        }
        if (!remotes.machines().isEmpty()) {
            tag.put("machines", remotes.saveMachines(registries));
        }
        if (!remotes.groups().isEmpty()) {
            tag.put("groups", remotes.saveGroups(registries));
        }
    }

    /**
     * Migração da rede da aba: põe {@code network} nas faces do tipo ainda sem rede da presa, dos
     * perfis e das exceções. Faces que já têm rede ficam. Sem avisar ninguém: o fim da carga avisa.
     */
    private void migrateLegacyNetwork(ResourceType type, UUID network) {
        for (FaceConfig config : faces[type.ordinal()]) {
            if (config.network() == null) {
                config.setNetwork(network);
            }
        }
        for (MachineGroup group : remotes.groups()) {
            migrateLegacyNetwork(group.profile(), type, network);
        }
        for (RemoteMachine machine : remotes.machines()) {
            MachineConfig exception = machine.exception();
            if (exception != null) {
                migrateLegacyNetwork(exception, type, network);
            }
        }
    }

    private static void migrateLegacyNetwork(MachineConfig config, ResourceType type, UUID network) {
        for (RemoteSide side : RemoteSide.values()) {
            FaceConfig face = config.face(type, side);
            if (face.network() == null) {
                face.setNetwork(network);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Formato antigo (rede da aba em "networks", ou ainda mais antigo, uma rede só em "network"):
        // só lido, para migrar para as faces depois de carregá-las.
        UUID single = tag.hasUUID("network") ? tag.getUUID("network") : null;
        CompoundTag networksTag = tag.getCompound("networks");
        UUID[] legacy = new UUID[TYPES.length];
        for (ResourceType type : TYPES) {
            String key = type.key();
            legacy[type.ordinal()] = networksTag.hasUUID(key) ? networksTag.getUUID(key) : single;
        }
        name = sanitizeName(tag.getString("name"));
        powered = tag.getBoolean("powered");
        CompoundTag facesTag = tag.getCompound("faces");
        for (ResourceType type : TYPES) {
            CompoundTag typeTag = facesTag.getCompound(type.key());
            for (RelativeSide side : SIDES) {
                FaceConfig config = face(type, side);
                if (typeTag.contains(side.key(), CompoundTag.TAG_COMPOUND)) {
                    config.copyFrom(FaceConfig.load(typeTag.getCompound(side.key()), registries));
                } else {
                    config.reset();
                }
            }
        }
        clearCards();
        CompoundTag cardsTag = tag.getCompound("cards");
        for (int t = 0; t < CARD_TYPES.length; t++) {
            CompoundTag typeTag = cardsTag.getCompound(CARD_TYPES[t].key());
            for (RelativeSide side : SIDES) {
                ListTag list = typeTag.getList(side.key(), Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag slotTag = list.getCompound(i);
                    int slot = slotTag.getByte("slot");
                    ItemStack[] slots = cards[t][side.ordinal()];
                    if (slot >= 0 && slot < CARD_SLOTS) {
                        // Item de mod removido: a pilha não lê e o slot fica vazio.
                        ItemStack.parse(registries, slotTag).ifPresent(stack -> slots[slot] = stack);
                    }
                }
            }
        }
        upgrade = tag.contains("upgrade", Tag.TAG_COMPOUND)
                ? ItemStack.parse(registries, tag.getCompound("upgrade")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        upgradeOwner = !upgrade.isEmpty() && tag.hasUUID("upgrade_owner") ? tag.getUUID("upgrade_owner") : null;
        rangeUpgrade = tag.contains("range_upgrade", Tag.TAG_COMPOUND)
                ? ItemStack.parse(registries, tag.getCompound("range_upgrade")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        // Sem as chaves (mundo de antes do alcance), as listas ficam vazias.
        remotes.load(tag.getList("machines", Tag.TAG_COMPOUND), tag.getList("groups", Tag.TAG_COMPOUND), registries);
        for (ResourceType type : TYPES) {
            UUID network = legacy[type.ordinal()];
            if (network != null) {
                migrateLegacyNetwork(type, network);
            }
        }
        pruneRemoteCaches();
        // As instâncias de RemoteMachine são outras: o índice do mixin aponta para as novas.
        remotesChanged();
        changeVersion++;
        // Carga sobre um nó já no mundo (ex.: /data merge): as rotas mudam.
        if (level != null && !level.isClientSide) {
            NetworkManager.get().nodeChanged(this);
            NodeIndex.track(this);
            if (!isRemoved()) {
                RouterChunkLoader.get().loaded(this);
            }
        }
    }
}
