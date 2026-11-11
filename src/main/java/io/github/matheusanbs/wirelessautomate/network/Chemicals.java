package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.compat.mekanism.MekanismChemicals;
import io.github.matheusanbs.wirelessautomate.compat.mekanism.MekanismStorage;
import io.github.matheusanbs.wirelessautomate.storage.ChemicalStorage;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Ponte para os químicos do Mekanism sem carregar classes dele quando ele não está instalado.
 * Quem chama confere {@link #LOADED} (ou chama só com um handler que veio daqui); as classes que
 * usam a API do Mekanism ({@link MekanismChemicals}, {@link ChemicalTransfer}) só são carregadas
 * pela JVM quando um desses métodos é chamado com ele presente.
 */
public final class Chemicals {
    /** Um id que nunca é químico, para comparar sem nulos. */
    public static final ResourceLocation EMPTY_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "empty");

    /** O Mekanism está instalado: a aba Químicos existe e o motor move químicos. */
    public static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("mekanism");

    /** A capability de bloco do químico ({@code mekanism:chemical_handler}), ou {@code null} sem o Mekanism. */
    public static @Nullable BlockCapability<?, @Nullable Direction> capability() {
        return LOADED ? MekanismChemicals.BLOCK : null;
    }

    /** Tanques de um handler de químico (o objeto de {@code RouterBlockEntity.chemicals}). */
    public static int tanks(Object handler) {
        return MekanismChemicals.tanks(handler);
    }

    /**
     * O Afinador do Configurador lendo um handler de químico: bit 1 entra, bit 2 sai; a assinatura dos tanques vai em
     * {@code signature} (ver {@link MekanismChemicals#probe}).
     */
    public static int probe(Object handler, int maxTanks, List<Long> signature) {
        return MekanismChemicals.probe(handler, maxTanks, signature);
    }

    /** Algum tanque do handler de químico guarda algo. Só com o Mekanism. */
    public static boolean holdsAny(Object handler) {
        return MekanismChemicals.holdsAny(handler);
    }

    /** Soma o guardado e a capacidade dos tanques do handler em {@code out[0]} e {@code out[1]}. */
    static void held(Object handler, long[] out) {
        MekanismChemicals.held(handler, out);
    }

    /** O primeiro químico guardado num item (tanque do Mekanism), se houver. */
    public static Optional<ResourceLocation> chemicalIn(ItemStack stack) {
        return LOADED && !stack.isEmpty() ? MekanismChemicals.chemicalIn(stack) : Optional.empty();
    }

    /** Id do químico de um ingrediente do JEI ({@code ChemicalStack}), se for um. */
    public static Optional<ResourceLocation> ingredientId(Object ingredient) {
        return LOADED ? MekanismChemicals.ingredientId(ingredient) : Optional.empty();
    }

    /** O químico como ingrediente do JEI ({@code ChemicalStack}), ou {@code null} (sem o Mekanism ou sem o químico). */
    public static @Nullable Object ingredient(ResourceLocation id) {
        return LOADED ? MekanismChemicals.ingredient(id) : null;
    }

    /** Existe um químico com esse id (sempre falso sem o Mekanism). */
    public static boolean exists(ResourceLocation id) {
        return LOADED && MekanismChemicals.exists(id);
    }

    /** Nome do químico para mostrar; o id se ele não existir (ou sem o Mekanism). */
    public static Component name(ResourceLocation id) {
        Component name = LOADED ? MekanismChemicals.name(id) : null;
        return name != null ? name : Component.literal(id.toString());
    }

    /** Textura do químico no atlas de blocos, ou {@code null}. */
    public static @Nullable ResourceLocation icon(ResourceLocation id) {
        return LOADED ? MekanismChemicals.icon(id) : null;
    }

    /** Cor RGB do químico (branco se não existir). */
    public static int tint(ResourceLocation id) {
        return LOADED ? MekanismChemicals.tint(id) : 0xFFFFFF;
    }

    /** Registra a capability de químico do Tanque Químico; sem o Mekanism, nada. */
    public static void registerStorage(RegisterCapabilitiesEvent event) {
        if (LOADED) {
            MekanismStorage.register(event);
        }
    }

    /** Enche o recipiente do Mekanism com o químico do Tanque Químico; devolve quanto passou (0 sem ele). */
    public static long fillContainer(ItemStack container, ChemicalStorage storage, ResourceLocation id) {
        return LOADED && !container.isEmpty() ? MekanismStorage.fillContainer(container, storage, id) : 0;
    }

    /** Esvazia o recipiente do Mekanism no Tanque Químico; devolve quanto passou (0 sem ele). */
    public static long emptyContainer(ItemStack container, ChemicalStorage storage) {
        return LOADED && !container.isEmpty() ? MekanismStorage.emptyContainer(container, storage) : 0;
    }

    /**
     * Um químico ({@code ChemicalStack}) lido do NBT, como {@code Object}; {@code null} sem o Mekanism, com o químico
     * que não existe mais ou com a entrada vazia (quem guarda a entrada crua decide o que fazer com ela).
     */
    public static @Nullable Object parseStack(CompoundTag tag, HolderLookup.Provider registries) {
        return LOADED ? MekanismChemicals.parseStack(tag, registries) : null;
    }

    /** Um químico que veio do {@link #parseStack} (ou de outra ponte com o Mekanism) em NBT. */
    public static CompoundTag saveStack(Object stack, HolderLookup.Provider registries) {
        return MekanismChemicals.saveStack(stack, registries);
    }

    /** O químico {@code stack} (um {@code ChemicalStack}) está vazio. Só com o Mekanism. */
    public static boolean isEmptyStack(Object stack) {
        return MekanismChemicals.isEmptyStack(stack);
    }

    /** Uma cópia do químico {@code stack} (um {@code ChemicalStack}). Só com o Mekanism. */
    public static Object copyStack(Object stack) {
        return MekanismChemicals.copyStack(stack);
    }

    /** A quantidade (mB) do químico {@code stack}. Só com o Mekanism. */
    public static long amount(Object stack) {
        return MekanismChemicals.amount(stack);
    }

    /** {@code a} e {@code b} ({@code ChemicalStack}) são o mesmo químico, sem olhar a quantidade. Só com o Mekanism. */
    public static boolean sameChemical(Object a, Object b) {
        return MekanismChemicals.sameChemical(a, b);
    }

    /** Uma cópia do químico {@code stack} com {@code amount} mB. Só com o Mekanism. */
    public static Object copyWithAmount(Object stack, long amount) {
        return MekanismChemicals.copyWithAmount(stack, amount);
    }

    /**
     * Insere o químico {@code stack} no handler {@code handler} (um {@code IChemicalHandler}, como o de
     * {@code MachineCaches.chemicals}); devolve quanto entrou. Não muda {@code stack}. Só com o Mekanism.
     */
    public static long insert(Object handler, Object stack, boolean simulate) {
        return MekanismChemicals.insert(handler, stack, simulate);
    }

    /** Tira até {@code amount} mB do tanque {@code tank} do handler; devolve o químico que saiu. Só com o Mekanism. */
    public static Object extract(Object handler, int tank, long amount, boolean simulate) {
        return MekanismChemicals.extract(handler, tank, amount, simulate);
    }

    /** Uma visita de uma origem de químicos; sem o Mekanism não há portas de químico. */
    static boolean move(Port source, long now) {
        return LOADED && ChemicalTransfer.move(source, now);
    }

    private Chemicals() {
    }
}
