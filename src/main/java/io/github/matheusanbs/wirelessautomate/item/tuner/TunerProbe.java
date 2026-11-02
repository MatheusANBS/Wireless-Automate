package io.github.matheusanbs.wirelessautomate.item.tuner;

import io.github.matheusanbs.wirelessautomate.item.tuner.TunerPlan.Face;
import io.github.matheusanbs.wirelessautomate.item.tuner.TunerPlan.Role;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.Sources;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A leitura da máquina pelo Afinador do Configurador: para cada face e cada tipo carregado, a capability daquela face
 * por consulta direta ({@code level.getCapability}, sem cache: é um clique) e o papel dela pelas provas, e a decisão
 * do {@link TunerPlan}. Quem chama conferiu que o chunk está carregado.
 *
 * <p>Custo, só no clique: itens, até 6 faces × {@link #MAX_SLOTS} slots × {@link #MAX_PROBES} provas, cada uma um
 * {@code isItemValid} e, no slot vazio, um {@code insertItem} simulado; fluidos, dois {@code fill} e um {@code drain}
 * simulados por face; o resto, uma leitura por face.
 */
public final class TunerProbe {
    /** Provas de item por análise, no máximo (o bitset de cada slot é um {@code long}). */
    public static final int MAX_PROBES = 64;
    /** Slots (ou tanques) lidos por face; os que passam disso ficam fora da análise. */
    public static final int MAX_SLOTS = 27;

    /** As provas fixas, comuns às máquinas de sempre (fornalhas, moinhos, plantadores, alquimia). */
    private static final Item[] COMMON = {Items.COBBLESTONE, Items.IRON_ORE, Items.RAW_IRON, Items.COAL,
            Items.CHARCOAL, Items.OAK_LOG, Items.SAND, Items.WHEAT, Items.WHEAT_SEEDS, Items.REDSTONE,
            Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.BONE, Items.NETHER_WART};

    private static final Direction[] DIRECTIONS = Direction.values();

    private TunerProbe() {
    }

    /**
     * Os modos propostos para a máquina em {@code pos}, por tipo (só os tipos em que alguma face tem algo), cada um
     * pelas faces absolutas ({@link TunerPlan#decide}); vazio se nada foi achado. {@code front} é a frente guardada da
     * máquina ({@code -1}: sem frente), para as preferências por lado.
     */
    public static Map<ResourceType, PortMode[]> analyze(Level level, BlockPos pos, int front, Player player) {
        Map<ResourceType, PortMode[]> proposal = new EnumMap<>(ResourceType.class);
        for (ResourceType type : LoadedTypes.LIST) {
            Face[] faces = switch (type) {
                case ITEM -> items(level, pos, player);
                case FLUID -> fluids(level, pos);
                case ENERGY -> energy(level, pos);
                case CHEMICAL -> chemicals(level, pos);
                case SOURCE -> source(level, pos);
            };
            PortMode[] modes = TunerPlan.decide(faces, front);
            if (modes != null) {
                proposal.put(type, modes);
            }
        }
        return proposal;
    }

    // ------------------------------------------------------------------ itens

    private static Face[] items(Level level, BlockPos pos, Player player) {
        IItemHandler[] handlers = new IItemHandler[TunerPlan.FACES];
        boolean any = false;
        for (Direction face : DIRECTIONS) {
            handlers[face.ordinal()] = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
            any |= handlers[face.ordinal()] != null;
        }
        Face[] faces = nothing();
        if (!any) {
            return faces;
        }
        List<ItemStack> probes = itemProbes(handlers, player);
        for (int face = 0; face < TunerPlan.FACES; face++) {
            if (handlers[face] != null) {
                faces[face] = items(handlers[face], probes);
            }
        }
        return faces;
    }

    /**
     * As provas de item, sem repetir e com uma unidade cada: as fixas, as que já estão nos slots da máquina e as do
     * inventário do jogador, até {@link #MAX_PROBES}.
     */
    private static List<ItemStack> itemProbes(IItemHandler[] handlers, Player player) {
        List<ItemStack> probes = new ArrayList<>(MAX_PROBES);
        for (Item item : COMMON) {
            addProbe(probes, new ItemStack(item));
        }
        for (IItemHandler handler : handlers) {
            if (handler != null) {
                for (int slot = 0, n = Math.min(handler.getSlots(), MAX_SLOTS); slot < n; slot++) {
                    addProbe(probes, handler.getStackInSlot(slot));
                }
            }
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0, n = inventory.getContainerSize(); slot < n; slot++) {
            addProbe(probes, inventory.getItem(slot));
        }
        return probes;
    }

    private static void addProbe(List<ItemStack> probes, ItemStack stack) {
        if (stack.isEmpty() || probes.size() >= MAX_PROBES) {
            return;
        }
        for (ItemStack probe : probes) {
            if (ItemStack.isSameItemSameComponents(probe, stack)) {
                return;
            }
        }
        probes.add(stack.copyWithCount(1));
    }

    /**
     * Uma face de itens. Por slot: as provas aceitas (o {@code isItemValid} e, no slot vazio, o {@code insertItem}
     * simulado; no ocupado, só o {@code isItemValid}, porque outro item não cabe ali e nem por isso o slot deixa de
     * ser entrada).
     * O slot é <i>entrada</i> se aceita alguma; <i>saída</i> se não aceita nenhuma e dá algo (ou está vazio); e
     * <i>aberto</i> (os dois) se o {@code isItemValid} aceita todas as provas, como num baú.
     */
    private static Face items(IItemHandler handler, List<ItemStack> probes) {
        int slots = Math.min(handler.getSlots(), MAX_SLOTS);
        if (slots <= 0) {
            return Face.NOTHING;
        }
        List<Long> signature = new ArrayList<>(1 + 2 * slots);
        signature.add((long) handler.getSlots());
        boolean in = false;
        boolean out = false;
        boolean open = true;
        for (int slot = 0; slot < slots; slot++) {
            boolean empty = handler.getStackInSlot(slot).isEmpty();
            boolean valid = true;
            long accepted = 0;
            for (int i = 0, n = probes.size(); i < n; i++) {
                ItemStack probe = probes.get(i);
                if (!handler.isItemValid(slot, probe)) {
                    valid = false;
                } else if (!empty || handler.insertItem(slot, probe, true).isEmpty()) {
                    accepted |= 1L << i;
                }
            }
            signature.add((long) handler.getSlotLimit(slot));
            signature.add(accepted);
            boolean slotOpen = valid && accepted != 0;
            open &= slotOpen;
            in |= accepted != 0;
            out |= slotOpen || accepted == 0 && (empty || !handler.extractItem(slot, 1, true).isEmpty());
        }
        return face(in, out, open, signature);
    }

    // ------------------------------------------------------------------ fluidos

    private static Face[] fluids(Level level, BlockPos pos) {
        Face[] faces = nothing();
        FluidStack water = new FluidStack(Fluids.WATER, 1000);
        FluidStack lava = new FluidStack(Fluids.LAVA, 1000);
        for (Direction face : DIRECTIONS) {
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
            if (handler != null) {
                faces[face.ordinal()] = fluids(handler, water, lava);
            }
        }
        return faces;
    }

    /**
     * Uma face de fluidos: <i>entrada</i> se o {@code fill} simulado aceita água ou lava (ou um tanque com algo dentro
     * vale para uma delas, cheio ou não); <i>saída</i> se o {@code drain} simulado dá algo, ou se os tanques estão
     * vazios e recusam as duas; <i>aberta</i> se aceita as duas, ou aceita e dá.
     */
    private static Face fluids(IFluidHandler handler, FluidStack water, FluidStack lava) {
        int tanks = handler.getTanks();
        if (tanks <= 0) {
            return Face.NOTHING;
        }
        long accepted = (handler.fill(water, IFluidHandler.FluidAction.SIMULATE) > 0 ? 1 : 0)
                | (handler.fill(lava, IFluidHandler.FluidAction.SIMULATE) > 0 ? 2 : 0);
        List<Long> signature = new ArrayList<>(2 + Math.min(tanks, MAX_SLOTS));
        signature.add((long) tanks);
        boolean empty = true;
        boolean held = false;
        for (int tank = 0, n = Math.min(tanks, MAX_SLOTS); tank < n; tank++) {
            signature.add((long) handler.getTankCapacity(tank));
            FluidStack inTank = handler.getFluidInTank(tank);
            if (!inTank.isEmpty()) {
                empty = false;
                held |= handler.isFluidValid(tank, inTank);
            }
        }
        signature.add(accepted);
        boolean in = accepted != 0 || held;
        boolean drains = !handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE).isEmpty();
        boolean out = drains || accepted == 0 && empty;
        return face(in, out, accepted == 3 || in && drains, signature);
    }

    // ------------------------------------------------------------------ energia, químicos e Source

    /** Energia: <i>entrada</i> se recebe, <i>saída</i> se dá; aberta (uma bateria) com os dois. */
    private static Face[] energy(Level level, BlockPos pos) {
        Face[] faces = nothing();
        for (Direction face : DIRECTIONS) {
            IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face);
            if (energy != null) {
                boolean in = energy.canReceive();
                boolean out = energy.canExtract();
                faces[face.ordinal()] = face(in, out, in && out,
                        List.of(in ? 1L : 0L, out ? 1L : 0L, (long) energy.getMaxEnergyStored()));
            }
        }
        return faces;
    }

    /** Químicos, pela ponte ({@link Chemicals#probe}): sem prova de químico, um tanque vazio fica de fora. */
    private static Face[] chemicals(Level level, BlockPos pos) {
        Face[] faces = nothing();
        BlockCapability<?, @Nullable Direction> capability = Chemicals.capability();
        if (capability == null) {
            return faces;
        }
        for (Direction face : DIRECTIONS) {
            Object handler = level.getCapability(capability, pos, face);
            if (handler != null) {
                List<Long> signature = new ArrayList<>();
                int bits = Chemicals.probe(handler, MAX_SLOTS, signature);
                faces[face.ordinal()] = face((bits & 1) != 0, (bits & 2) != 0, bits == 3, signature);
            }
        }
        return faces;
    }

    /** Source, pela ponte ({@link Sources#probe}): como a energia. */
    private static Face[] source(Level level, BlockPos pos) {
        Face[] faces = nothing();
        BlockCapability<?, @Nullable Direction> capability = Sources.capability();
        if (capability == null) {
            return faces;
        }
        for (Direction face : DIRECTIONS) {
            Object handler = level.getCapability(capability, pos, face);
            if (handler != null) {
                List<Long> signature = new ArrayList<>(1);
                int bits = Sources.probe(handler, signature);
                faces[face.ordinal()] = face((bits & 1) != 0, (bits & 2) != 0, bits == 3, signature);
            }
        }
        return faces;
    }

    // ------------------------------------------------------------------ apoio

    private static Face[] nothing() {
        Face[] faces = new Face[TunerPlan.FACES];
        Arrays.fill(faces, Face.NOTHING);
        return faces;
    }

    private static Face face(boolean in, boolean out, boolean open, List<Long> signature) {
        Role role = in && out || open ? Role.BOTH : in ? Role.IN : out ? Role.OUT : Role.NONE;
        return role == Role.NONE ? Face.NOTHING : new Face(role, open, signature);
    }
}
