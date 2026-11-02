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
 * <p>Custo, só no clique: itens, até 6 faces × {@link #MAX_SLOTS} slots × {@link #MAX_PROBES} provas no slot vazio
 * (um {@code isItemValid} e um {@code insertItem} simulado cada) e um {@code insertItem} e um {@code extractItem}
 * simulados no ocupado; fluidos, dois {@code fill} e um {@code drain} simulados por face; o resto, uma leitura por face.
 *
 * <p>Limite: só os {@link #MAX_SLOTS} primeiros slots (ou tanques) de cada face entram na análise. Uma face com mais
 * (um baú grande de outro mod) é decidida por eles; o número total de slots entra na assinatura, então faces
 * diferentes continuam diferentes.
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
        List<ResourceType> types = LoadedTypes.LIST;
        Face[][] byType = new Face[types.size()][];
        boolean[] scalar = new boolean[types.size()];
        for (int i = 0; i < byType.length; i++) {
            ResourceType type = types.get(i);
            scalar[i] = type == ResourceType.ENERGY || type == ResourceType.SOURCE;
            byType[i] = switch (type) {
                case ITEM -> items(level, pos, player);
                case FLUID -> fluids(level, pos);
                case ENERGY -> energy(level, pos);
                case CHEMICAL -> chemicals(level, pos);
                case SOURCE -> source(level, pos);
            };
        }
        PortMode[][] modes = TunerPlan.decide(byType, scalar, front);
        Map<ResourceType, PortMode[]> proposal = new EnumMap<>(ResourceType.class);
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] != null) {
                proposal.put(types.get(i), modes[i]);
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
     * Uma face de itens (também para os GameTests, com um handler de teste). Por slot, até {@link #MAX_SLOTS}:
     * <ul>
     * <li>vazio: as provas aceitas pelo {@code isItemValid} e pelo {@code insertItem} simulado; <i>entrada</i> se
     * aceita alguma, <i>aberto</i> (entrada e saída, como num baú) se aceita todas, e <i>saída fraca</i> se recusa
     * todas (um slot de produto vazio);</li>
     * <li>ocupado: só o {@code insertItem} simulado de uma unidade do próprio item vale (o {@code isItemValid} diz
     * sim até em slot de saída, no {@code ItemStackHandler}, no {@code InvWrapper} e no Mekanism): aceito, o slot é
     * aberto; recusado e com algo que sai pelo {@code extractItem} simulado, é <i>saída</i> de verdade.</li>
     * </ul>
     * A face é aberta se todos os slots lidos são; a saída dela é fraca se nenhum slot de saída de verdade (ocupado,
     * sem aceitar mais do próprio item) dá algo agora.
     */
    public static Face items(IItemHandler handler, List<ItemStack> probes) {
        int slots = Math.min(handler.getSlots(), MAX_SLOTS);
        if (slots <= 0) {
            return Face.NOTHING;
        }
        long all = probes.size() >= 64 ? -1L : (1L << probes.size()) - 1;
        List<Long> signature = new ArrayList<>(1 + 2 * slots);
        signature.add((long) handler.getSlots());
        boolean in = false;
        boolean out = false;
        boolean realOut = false;
        boolean open = true;
        for (int slot = 0; slot < slots; slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            signature.add((long) handler.getSlotLimit(slot));
            if (inSlot.isEmpty()) {
                long accepted = 0;
                for (int i = 0, n = probes.size(); i < n; i++) {
                    ItemStack probe = probes.get(i);
                    if (handler.isItemValid(slot, probe) && handler.insertItem(slot, probe, true).isEmpty()) {
                        accepted |= 1L << i;
                    }
                }
                signature.add(accepted);
                boolean slotOpen = accepted != 0 && accepted == all;
                open &= slotOpen;
                in |= accepted != 0;
                out |= slotOpen || accepted == 0;
            } else {
                boolean own = handler.insertItem(slot, inSlot.copyWithCount(1), true).isEmpty();
                boolean gives = !handler.extractItem(slot, 1, true).isEmpty();
                // Ocupado: o que importa é se o próprio item ainda entra, não qual item é.
                signature.add(own ? -1L : -2L);
                open &= own;
                in |= own;
                out |= own || gives;
                // Só o slot que não aceita mais do próprio item é saída de verdade; o aberto com algo é entrada também.
                realOut |= !own && gives;
            }
        }
        return face(in, out, open, signature, !realOut);
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
        return face(in, out, accepted == 3 || in && drains, signature, !drains);
    }

    // ------------------------------------------------------------------ energia, químicos e Source

    /** Energia, face a face ({@link #energy(IEnergyStorage)}). */
    private static Face[] energy(Level level, BlockPos pos) {
        Face[] faces = nothing();
        for (Direction face : DIRECTIONS) {
            IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face);
            if (energy != null) {
                boolean in = energy.canReceive();
                boolean out = energy.canExtract();
                faces[face.ordinal()] = energy(energy);
            }
        }
        return faces;
    }

    /**
     * Uma face de energia (também para os GameTests): <i>entrada</i> se recebe, <i>saída</i> se dá, aberta com os
     * dois. Um consumidor que diz que também dá (o {@code EnergyStorage} comum) fica AMBOS aqui; o {@link TunerPlan}
     * o trata como entrada fora do armazenamento.
     */
    public static Face energy(IEnergyStorage energy) {
        boolean in = energy.canReceive();
        boolean out = energy.canExtract();
        return face(in, out, in && out, List.of(in ? 1L : 0L, out ? 1L : 0L, (long) energy.getMaxEnergyStored()),
                false);
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
                faces[face.ordinal()] = face((bits & 1) != 0, (bits & 2) != 0, bits == 3, signature, false);
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
                faces[face.ordinal()] = face((bits & 1) != 0, (bits & 2) != 0, bits == 3, signature, false);
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

    /** A face lida; sem papel, fica só a marca de que tem a capability (no armazenamento ela vai para Nenhum). */
    private static Face face(boolean in, boolean out, boolean open, List<Long> signature, boolean weakOut) {
        Role role = in && out || open ? Role.BOTH : in ? Role.IN : out ? Role.OUT : Role.NONE;
        return new Face(role, open && role != Role.NONE, signature, out && weakOut, true);
    }
}
