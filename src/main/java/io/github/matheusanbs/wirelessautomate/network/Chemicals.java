package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.compat.mekanism.MekanismChemicals;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
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

    /** O primeiro químico guardado num item (tanque do Mekanism), se houver. */
    public static Optional<ResourceLocation> chemicalIn(ItemStack stack) {
        return LOADED && !stack.isEmpty() ? MekanismChemicals.chemicalIn(stack) : Optional.empty();
    }

    /** Id do químico de um ingrediente do JEI ({@code ChemicalStack}), se for um. */
    public static Optional<ResourceLocation> ingredientId(Object ingredient) {
        return LOADED ? MekanismChemicals.ingredientId(ingredient) : Optional.empty();
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

    /** Uma visita de uma origem de químicos; sem o Mekanism não há portas de químico. */
    static boolean move(Port source, long now) {
        return LOADED && ChemicalTransfer.move(source, now);
    }

    private Chemicals() {
    }
}
