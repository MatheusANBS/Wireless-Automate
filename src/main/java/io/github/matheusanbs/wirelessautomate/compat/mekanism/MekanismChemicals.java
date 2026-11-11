package io.github.matheusanbs.wirelessautomate.compat.mekanism;

import java.util.List;
import java.util.Optional;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import org.jetbrains.annotations.Nullable;

/**
 * O que o mod usa da API do Mekanism, fora o laço de transferência ({@code ChemicalTransfer}).
 * Só é carregado com o Mekanism presente: o resto do mod passa por {@code network.Chemicals}.
 *
 * <p>A capability é criada pelo mesmo nome e tipo que o Mekanism usa
 * ({@code mekanism:chemical_handler}, {@link IChemicalHandler}); o NeoForge devolve a mesma
 * instância, então não dependemos das classes internas dele, só da API.
 */
public final class MekanismChemicals {
    public static final BlockCapability<IChemicalHandler, @Nullable Direction> BLOCK = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    /** O mesmo, para itens (tanques e cilindros do Mekanism). */
    public static final ItemCapability<IChemicalHandler, @Nullable Void> ITEM = ItemCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    public static int tanks(Object handler) {
        return ((IChemicalHandler) handler).getChemicalTanks();
    }

    /** Soma em {@code out[0]} o que há nos tanques e em {@code out[1]} a capacidade deles. */
    public static void held(Object handler, long[] out) {
        IChemicalHandler chemicals = (IChemicalHandler) handler;
        for (int tank = 0, n = chemicals.getChemicalTanks(); tank < n; tank++) {
            out[0] += chemicals.getChemicalInTank(tank).getAmount();
            out[1] += chemicals.getChemicalTankCapacity(tank);
        }
    }

    /**
     * O que o Afinador do Configurador lê de um handler: bit 1 se algum tanque aceita mais do químico que já tem, bit 2
     * se algum dá algo (simulado). Sem prova de químico, um tanque vazio não conta. Põe em {@code signature} o número
     * de tanques e a capacidade de cada um (até {@code maxTanks}).
     */
    public static int probe(Object handler, int maxTanks, List<Long> signature) {
        IChemicalHandler chemicals = (IChemicalHandler) handler;
        int tanks = chemicals.getChemicalTanks();
        signature.add((long) tanks);
        int bits = 0;
        for (int tank = 0, n = Math.min(tanks, maxTanks); tank < n; tank++) {
            signature.add(chemicals.getChemicalTankCapacity(tank));
            ChemicalStack inTank = chemicals.getChemicalInTank(tank);
            if (!inTank.isEmpty() && chemicals.insertChemical(tank, inTank.copyWithAmount(1), Action.SIMULATE).isEmpty()) {
                bits |= 1;
            }
            if (!chemicals.extractChemical(tank, Long.MAX_VALUE, Action.SIMULATE).isEmpty()) {
                bits |= 2;
            }
        }
        return bits;
    }

    private static final ResourceLocation EMPTY = ResourceLocation.fromNamespaceAndPath("mekanism", "empty");

    /** Id do químico ({@code mekanism:hydrogen}); a chave dos filtros. Chamado no laço, não aloca. */
    public static ResourceLocation id(ChemicalStack stack) {
        Holder<Chemical> holder = stack.getChemicalHolder();
        if (holder instanceof Holder.Reference<Chemical> reference) {
            return reference.key().location();
        }
        ResourceLocation key = MekanismAPI.CHEMICAL_REGISTRY.getKey(holder.value());
        return key != null ? key : EMPTY;
    }

    /** O primeiro químico guardado num item (tanque, cilindro), se houver. */
    public static Optional<ResourceLocation> chemicalIn(ItemStack stack) {
        IChemicalHandler handler = stack.getCapability(ITEM);
        if (handler == null) {
            return Optional.empty();
        }
        for (int tank = 0, n = handler.getChemicalTanks(); tank < n; tank++) {
            ChemicalStack inTank = handler.getChemicalInTank(tank);
            if (!inTank.isEmpty()) {
                return Optional.of(id(inTank));
            }
        }
        return Optional.empty();
    }

    /** Id do químico de um ingrediente de fora (o JEI entrega {@link ChemicalStack}), se for um. */
    public static Optional<ResourceLocation> ingredientId(Object ingredient) {
        return ingredient instanceof ChemicalStack stack && !stack.isEmpty() ? Optional.of(id(stack)) : Optional.empty();
    }

    /** O químico como ingrediente do JEI ({@link ChemicalStack} de 1.000 mB), ou {@code null} se ele não existe. */
    public static @Nullable Object ingredient(ResourceLocation id) {
        Chemical chemical = chemical(id);
        return chemical == null ? null : new ChemicalStack(chemical, 1_000);
    }

    /** Existe um químico registrado com esse id (o vazio não conta). */
    public static boolean exists(ResourceLocation id) {
        return !id.equals(MekanismAPI.CHEMICAL_REGISTRY.getDefaultKey()) && MekanismAPI.CHEMICAL_REGISTRY.containsKey(id);
    }

    private static @Nullable Chemical chemical(ResourceLocation id) {
        return exists(id) ? MekanismAPI.CHEMICAL_REGISTRY.get(id) : null;
    }

    public static @Nullable Component name(ResourceLocation id) {
        Chemical chemical = chemical(id);
        return chemical == null ? null : chemical.getTextComponent();
    }

    /** Textura do químico (no atlas de blocos), ou {@code null} se ele não existe. */
    public static @Nullable ResourceLocation icon(ResourceLocation id) {
        Chemical chemical = chemical(id);
        return chemical == null ? null : chemical.getIcon();
    }

    public static int tint(ResourceLocation id) {
        Chemical chemical = chemical(id);
        return chemical == null ? 0xFFFFFF : chemical.getTint();
    }

    /** Um {@link ChemicalStack} lido do NBT, ou {@code null} se o químico não existe (ou a entrada está vazia). */
    public static @Nullable Object parseStack(CompoundTag tag, HolderLookup.Provider registries) {
        ChemicalStack stack = ChemicalStack.parseOptional(registries, tag);
        return stack.isEmpty() ? null : stack;
    }

    /** O {@link ChemicalStack} {@code stack} em NBT, como o {@link #parseStack} lê. */
    public static CompoundTag saveStack(Object stack, HolderLookup.Provider registries) {
        Tag saved = ((ChemicalStack) stack).save(registries);
        return saved instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    public static boolean isEmptyStack(Object stack) {
        return ((ChemicalStack) stack).isEmpty();
    }

    public static Object copyStack(Object stack) {
        return ((ChemicalStack) stack).copy();
    }

    public static long amount(Object stack) {
        return ((ChemicalStack) stack).getAmount();
    }

    public static Object copyWithAmount(Object stack, long amount) {
        return ((ChemicalStack) stack).copyWithAmount(amount);
    }

    /** Insere {@code stack} no handler, por qualquer tanque; devolve quanto entrou. */
    public static long insert(Object handler, Object stack, boolean simulate) {
        ChemicalStack chemical = (ChemicalStack) stack;
        ChemicalStack rest = ((IChemicalHandler) handler).insertChemical(chemical,
                simulate ? Action.SIMULATE : Action.EXECUTE);
        return chemical.getAmount() - rest.getAmount();
    }

    /** Tira até {@code amount} do tanque {@code tank}; devolve o que saiu (o {@code ChemicalStack}, vazio se nada). */
    public static Object extract(Object handler, int tank, long amount, boolean simulate) {
        return ((IChemicalHandler) handler).extractChemical(tank, amount, simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    private MekanismChemicals() {
    }
}
