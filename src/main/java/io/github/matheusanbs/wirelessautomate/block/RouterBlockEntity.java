package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringUtil;
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
 */
public class RouterBlockEntity extends BlockEntity {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final RelativeSide[] SIDES = RelativeSide.values();
    private static final int FACES = Direction.values().length;

    private @Nullable UUID networkId;
    /** Nome do nó dado pelo jogador; vazio = sem nome. */
    private String name = "";
    /** [tipo][lado relativo], tudo alocado de início: consultas não alocam. */
    private final FaceConfig[][] faces = new FaceConfig[TYPES.length][SIDES.length];
    private boolean powered;
    /** Muda a cada alteração do que a tela mostra. Não é salvo. */
    private int changeVersion;
    /** Total movido como origem, por {@link ResourceType#ordinal()}, desde que o nó carregou. Não é salvo. */
    private final long[] moved = new long[TYPES.length];

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
    }

    public RouterTier tier() {
        return getBlockState().getValue(RouterBlock.TIER);
    }

    public @Nullable UUID networkId() {
        return networkId;
    }

    public void setNetworkId(@Nullable UUID networkId) {
        if (Objects.equals(this.networkId, networkId)) {
            return;
        }
        this.networkId = networkId;
        changed();
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
        }
        // Upgrade de tier muda vazão e alcance das rotas.
        if ((rotated || tier() != oldTier) && level != null && !level.isClientSide) {
            NetworkManager.get().nodeChanged(this);
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
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        removeFromManager();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        removeFromManager();
    }

    private void removeFromManager() {
        if (level != null && !level.isClientSide) {
            NetworkManager.get().removeNode(this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (networkId != null) {
            tag.putUUID("network", networkId);
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        networkId = tag.hasUUID("network") ? tag.getUUID("network") : null;
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
        changeVersion++;
        // Carga sobre um nó já no mundo (ex.: /data merge): as rotas mudam.
        if (level != null && !level.isClientSide) {
            NetworkManager.get().nodeChanged(this);
        }
    }

    private static String typeKey(ResourceType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
