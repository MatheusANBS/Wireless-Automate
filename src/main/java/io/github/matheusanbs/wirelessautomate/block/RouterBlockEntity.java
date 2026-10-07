package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoadState;
import io.github.matheusanbs.wirelessautomate.chunk.RouterChunkLoader;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.item.ChunkLoaderUpgradeItem;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
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

    /** Rede de cada aba (por {@link ResourceType#ordinal()}); {@code null} = sem rede nesse tipo. */
    private final UUID[] networks = new UUID[ResourceType.values().length];
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
    private static final ResourceType[] CARD_TYPES = {ResourceType.ITEM, ResourceType.FLUID};
    /** [tipo de {@link #CARD_TYPES}][lado relativo][slot], nunca nulos. */
    private final ItemStack[][][] cards = new ItemStack[CARD_TYPES.length][SIDES.length][CARD_SLOTS];
    /** Upgrade de chunk loading no slot (ou vazio) e quem o pôs. */
    private ItemStack upgrade = ItemStack.EMPTY;
    private @Nullable UUID upgradeOwner;
    /** O chunk descarregou: o {@link #setRemoved()} que vem depois não é o roteador saindo do mundo. */
    private boolean chunkUnloading;

    /** Caches por face absoluta da máquina, criados sob demanda e válidos para {@link #cacheFacing}. */
    private final BlockCapabilityCache<IItemHandler, @Nullable Direction>[] itemCaches = newCaches();
    private final BlockCapabilityCache<IFluidHandler, @Nullable Direction>[] fluidCaches = newCaches();
    private final BlockCapabilityCache<IEnergyStorage, @Nullable Direction>[] energyCaches = newCaches();
    private @Nullable Direction cacheFacing;

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

    /** Rede em que o recurso {@code type} deste roteador entra; {@code null} = sem rede. */
    public @Nullable UUID networkId(ResourceType type) {
        return networks[type.ordinal()];
    }

    /** Põe só o tipo {@code type} numa rede (a aba do roteador, ou o Vinculador com tipo). */
    public void setNetworkId(ResourceType type, @Nullable UUID networkId) {
        if (Objects.equals(networks[type.ordinal()], networkId)) {
            return;
        }
        networks[type.ordinal()] = networkId;
        changed();
    }

    /** Põe todos os tipos na mesma rede (colocar o roteador, Vinculador em "Todos"). */
    public void setNetworkId(@Nullable UUID networkId) {
        boolean changed = false;
        for (int i = 0; i < networks.length; i++) {
            if (!Objects.equals(networks[i], networkId)) {
                networks[i] = networkId;
                changed = true;
            }
        }
        if (changed) {
            changed();
        }
    }

    /** Algum tipo está numa rede. */
    public boolean hasNetwork() {
        for (UUID network : networks) {
            if (network != null) {
                return true;
            }
        }
        return false;
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

    /** A máquina pode ter mudado (bloco trocado): a tela aberta remonta o snapshot. Não mexe nas rotas. */
    public void machineChanged() {
        changeVersion++;
        NodeIndex.track(this);
    }

    /** Soma o que o nó moveu como origem. Chamado pelo motor, uma vez por visita que moveu algo. */
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

    /** Posição da máquina onde o roteador está preso. */
    public BlockPos machinePos() {
        return RouterBlock.attachedPos(getBlockState(), worldPosition);
    }

    /** Configuração da face {@code machineFace} (absoluta) da máquina para {@code type}. Nunca nula. */
    public FaceConfig face(ResourceType type, Direction machineFace) {
        return face(type, RelativeSide.fromAbsolute(facing(), machineFace));
    }

    /** Configuração pelo lado relativo ao {@code facing}, como presets a guardam. Nunca nula. */
    public FaceConfig face(ResourceType type, RelativeSide side) {
        return faces[type.ordinal()][side.ordinal()];
    }

    public void setMode(ResourceType type, Direction machineFace, PortMode mode) {
        if (face(type, machineFace).setMode(mode)) {
            changed();
        }
    }

    public void setPriority(ResourceType type, Direction machineFace, int priority) {
        if (face(type, machineFace).setPriority(priority)) {
            changed();
        }
    }

    public void setRedstone(ResourceType type, Direction machineFace, RedstoneMode redstone) {
        if (face(type, machineFace).setRedstone(redstone)) {
            changed();
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
            changed();
        }
    }

    /** Troca o filtro da face. Energia não usa filtro, mas guardar não faz mal. */
    public void setFilter(ResourceType type, Direction machineFace, Filter filter) {
        if (face(type, machineFace).setFilter(filter)) {
            changed();
        }
    }

    // ------------------------------------------------------------------ cartões de filtro

    /** O tipo tem slots de cartão (itens e fluidos; energia não usa filtro e químicos ainda não). */
    public static boolean hasCardSlots(ResourceType type) {
        return cardIndex(type) >= 0;
    }

    private static int cardIndex(ResourceType type) {
        return switch (type) {
            case ITEM -> 0;
            case FLUID -> 1;
            default -> -1;
        };
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
        changed();
    }

    /** Um cartão foi mexido no lugar (pilha do slot alterada): salva e remonta as rotas. */
    public void cardsChanged() {
        changed();
    }

    /** Cartões nos slots da face (absoluta) para o tipo. */
    public int cardCount(ResourceType type, Direction machineFace) {
        int index = cardIndex(type);
        if (index < 0) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : cards[index][RelativeSide.fromAbsolute(facing(), machineFace).ordinal()]) {
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
        RelativeSide side = RelativeSide.fromAbsolute(facing(), machineFace);
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
        if (!removed.isEmpty()) {
            changed();
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

    /** Copia a configuração de uma face inteira, pelo lado relativo (para presets e o Configurador). */
    public void setFace(ResourceType type, RelativeSide side, FaceConfig config) {
        if (face(type, side).copyFrom(config)) {
            changed();
        }
    }

    /** Sinal de redstone no roteador, guardado e atualizado em neighborChanged (sem consulta por tick). */
    public boolean powered() {
        return powered;
    }

    /** Relê o sinal de redstone. Chamado pelo bloco quando um vizinho muda. */
    public void updatePowered() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean now = level.hasNeighborSignal(worldPosition);
        if (now != powered) {
            powered = now;
            changeVersion++;
            setChanged();
            NetworkManager.get().nodeChanged(this);
        }
    }

    /** Inventário da máquina pela face {@code machineFace}, via BlockCapabilityCache. */
    public @Nullable IItemHandler items(Direction machineFace) {
        return capability(itemCaches, Capabilities.ItemHandler.BLOCK, machineFace);
    }

    public @Nullable IFluidHandler fluids(Direction machineFace) {
        return capability(fluidCaches, Capabilities.FluidHandler.BLOCK, machineFace);
    }

    public @Nullable IEnergyStorage energy(Direction machineFace) {
        return capability(energyCaches, Capabilities.EnergyStorage.BLOCK, machineFace);
    }

    /**
     * Devolve a capability pelo cache da face, criando o cache na primeira consulta. Se o roteador
     * foi girado, a máquina mudou de lugar e os caches são refeitos. Só no servidor.
     */
    private <T> @Nullable T capability(BlockCapabilityCache<T, @Nullable Direction>[] caches,
            BlockCapability<T, @Nullable Direction> capability, Direction machineFace) {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return null;
        }
        Direction facing = facing();
        if (facing != cacheFacing) {
            clearCaches();
            cacheFacing = facing;
        }
        BlockCapabilityCache<T, @Nullable Direction> cache = caches[machineFace.ordinal()];
        if (cache == null) {
            cache = BlockCapabilityCache.create(capability, serverLevel, machinePos(), machineFace,
                    () -> !isRemoved() && facing() == facing,
                    this::capabilityInvalidated);
            caches[machineFace.ordinal()] = cache;
        }
        return cache.getCapability();
    }

    /** A capability de uma face mudou: as rotas e o que a tela mostra (slots, máquina) também. */
    private void capabilityInvalidated() {
        changeVersion++;
        NetworkManager.get().nodeChanged(this);
    }

    private void clearCaches() {
        Arrays.fill(itemCaches, null);
        Arrays.fill(fluidCaches, null);
        Arrays.fill(energyCaches, null);
        cacheFacing = null;
    }

    @SuppressWarnings("unchecked")
    private static <T> BlockCapabilityCache<T, @Nullable Direction>[] newCaches() {
        return (BlockCapabilityCache<T, @Nullable Direction>[]) new BlockCapabilityCache[FACES];
    }

    /** Salva e, no servidor, avisa o gerenciador que as rotas deste nó precisam ser refeitas. */
    private void changed() {
        changeVersion++;
        setChanged();
        if (level != null && !level.isClientSide) {
            NetworkManager.get().nodeChanged(this);
            NodeIndex.track(this);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        Direction oldFacing = facing();
        RouterTier oldTier = tier();
        super.setBlockState(state);
        boolean rotated = facing() != oldFacing;
        if (rotated || tier() != oldTier) {
            changeVersion++;
        }
        if (rotated) {
            // Girado: a configuração relativa acompanha, mas a máquina mudou de lugar.
            clearCaches();
            if (level != null && !level.isClientSide && hasChunkUpgrade()) {
                // O chunk da máquina pode ser outro.
                RouterChunkLoader.get().update(this);
            }
        }
        // Upgrade de tier muda vazão e alcance das rotas.
        if ((rotated || tier() != oldTier) && level != null && !level.isClientSide) {
            NetworkManager.get().nodeChanged(this);
            NodeIndex.track(this);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            // Ao carregar o chunk os vizinhos podem não estar carregados; aí fica o valor salvo.
            if (level.isAreaLoaded(worldPosition, 1)) {
                powered = level.hasNeighborSignal(worldPosition);
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
        CompoundTag networksTag = new CompoundTag();
        for (ResourceType type : ResourceType.values()) {
            UUID network = networks[type.ordinal()];
            if (network != null) {
                networksTag.putUUID(type.name().toLowerCase(Locale.ROOT), network);
            }
        }
        if (!networksTag.isEmpty()) {
            tag.put("networks", networksTag);
        }
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
                facesTag.put(typeKey(type), typeTag);
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
                cardsTag.put(typeKey(CARD_TYPES[t]), typeTag);
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Formato antigo: uma rede para o roteador inteiro vale para todos os tipos.
        UUID legacy = tag.hasUUID("network") ? tag.getUUID("network") : null;
        CompoundTag networksTag = tag.getCompound("networks");
        for (ResourceType type : ResourceType.values()) {
            String key = type.name().toLowerCase(Locale.ROOT);
            networks[type.ordinal()] = networksTag.hasUUID(key) ? networksTag.getUUID(key) : legacy;
        }
        name = sanitizeName(tag.getString("name"));
        powered = tag.getBoolean("powered");
        CompoundTag facesTag = tag.getCompound("faces");
        for (ResourceType type : TYPES) {
            CompoundTag typeTag = facesTag.getCompound(typeKey(type));
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
            CompoundTag typeTag = cardsTag.getCompound(typeKey(CARD_TYPES[t]));
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

    private static String typeKey(ResourceType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
