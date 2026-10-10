package io.github.matheusanbs.wirelessautomate.compat.mekanism;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.registries.IForgeRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * O que o mod usa da API do Mekanism, fora o laço de transferência ({@code ChemicalTransfer}).
 * Só é carregado com o Mekanism presente: o resto do mod passa por {@code network.Chemicals}.
 *
 * <p>Porte 1.20.1 (Mekanism 10.4, D4): são quatro tipos de químico, cada um com o seu registro, a sua pilha
 * e a sua capability ({@link Subtype}), na ordem gás, infusão, pigmento, slurry. As capabilities ficam em
 * {@code mekanism.common} (fora da API), mas o Forge as identifica pelo tipo do {@link CapabilityToken}: pedir
 * {@code CapabilityManager.get} com o mesmo tipo devolve a mesma instância, sem depender das classes internas.
 *
 * <p>O id de um químico continua um {@link ResourceLocation} no mod todo; ele é resolvido procurando nos
 * quatro registros, na mesma ordem ({@link #subtype}). Se um addon repetir um id em dois registros, vale o
 * primeiro, e o mod avisa no log uma vez, quando monta o índice (no primeiro uso, com os registros prontos).
 */
public final class MekanismChemicals {
    public static final Capability<IGasHandler> GAS = CapabilityManager.get(new CapabilityToken<IGasHandler>() {
    });
    public static final Capability<IInfusionHandler> INFUSION = CapabilityManager.get(new CapabilityToken<IInfusionHandler>() {
    });
    public static final Capability<IPigmentHandler> PIGMENT = CapabilityManager.get(new CapabilityToken<IPigmentHandler>() {
    });
    public static final Capability<ISlurryHandler> SLURRY = CapabilityManager.get(new CapabilityToken<ISlurryHandler>() {
    });

    /**
     * Um tipo de químico do Mekanism 10.4: índice (a ordem de {@link #SUBTYPES}), capability, registro e
     * como criar a pilha. Genérico em {@code <C, S>} para o {@code ChemicalTransfer} e o Tanque Químico
     * escreverem a lógica uma vez só.
     */
    public static final class Subtype<C extends Chemical<C>, S extends ChemicalStack<C>> {
        public final int index;
        public final String name;
        public final Capability<? extends IChemicalHandler<C, S>> capability;
        private final Supplier<IForgeRegistry<C>> registry;
        private final Supplier<S> empty;

        Subtype(int index, String name, Capability<? extends IChemicalHandler<C, S>> capability,
                Supplier<IForgeRegistry<C>> registry, Supplier<S> empty) {
            this.index = index;
            this.name = name;
            this.capability = capability;
            this.registry = registry;
            this.empty = empty;
        }

        public IForgeRegistry<C> registry() {
            return registry.get();
        }

        public S empty() {
            return empty.get();
        }

        /** Pilha de {@code amount} do químico (vazia com 0 ou menos). */
        @SuppressWarnings("unchecked")
        public S stack(C chemical, long amount) {
            return amount <= 0 || chemical.isEmptyType() ? empty() : (S) chemical.getStack(amount);
        }

        /** Pilha do químico {@code id} deste tipo, ou a vazia se o id não é deste tipo. */
        public S stack(ResourceLocation id, long amount) {
            C chemical = amount <= 0 || subtype(id) != index ? null : registry().getValue(id);
            return chemical == null ? empty() : stack(chemical, amount);
        }

        /** Cópia de {@code stack} com outra quantidade. */
        public S withAmount(S stack, long amount) {
            return stack(stack.getType(), amount);
        }

        /** O handler deste tipo num item (tanque, cilindro), ou {@code null}. */
        public @Nullable IChemicalHandler<C, S> item(ItemStack stack) {
            return stack.isEmpty() ? null : stack.getCapability(capability).resolve().orElse(null);
        }

        /** O handler (o {@code Object} do roteador) visto como deste tipo. */
        @SuppressWarnings("unchecked")
        public IChemicalHandler<C, S> handler(Object handler) {
            return (IChemicalHandler<C, S>) handler;
        }
    }

    public static final Subtype<Gas, GasStack> GASES = new Subtype<>(0, "gás", GAS,
            MekanismAPI::gasRegistry, () -> GasStack.EMPTY);
    public static final Subtype<InfuseType, InfusionStack> INFUSIONS = new Subtype<>(1, "infusão", INFUSION,
            MekanismAPI::infuseTypeRegistry, () -> InfusionStack.EMPTY);
    public static final Subtype<Pigment, PigmentStack> PIGMENTS = new Subtype<>(2, "pigmento", PIGMENT,
            MekanismAPI::pigmentRegistry, () -> PigmentStack.EMPTY);
    public static final Subtype<Slurry, SlurryStack> SLURRIES = new Subtype<>(3, "slurry", SLURRY,
            MekanismAPI::slurryRegistry, () -> SlurryStack.EMPTY);

    /** Os quatro tipos, na ordem gás, infusão, pigmento, slurry (a de {@link #CAPABILITIES}). */
    public static final List<Subtype<?, ?>> SUBTYPES = List.of(GASES, INFUSIONS, PIGMENTS, SLURRIES);

    /** As capabilities de bloco e de item, uma por tipo, na ordem de {@link #SUBTYPES} ({@code Chemicals.capabilities()}). */
    public static final List<Capability<?>> CAPABILITIES = List.of(GAS, INFUSION, PIGMENT, SLURRY);

    private static final ResourceLocation EMPTY = new ResourceLocation("mekanism", "empty");

    /** Tipo de cada id dos quatro registros; montado no primeiro uso (os registros do Forge não mudam depois). */
    private static volatile @Nullable Object2IntOpenHashMap<ResourceLocation> index;

    /** As colisões de id já foram logadas (uma vez por processo, mesmo que cliente e servidor montem o índice). */
    private static volatile boolean collisionsLogged;

    /** Índice do tipo (em {@link #SUBTYPES}) do químico {@code id}, ou -1 se ele não existe. Não aloca. */
    public static int subtype(ResourceLocation id) {
        Object2IntOpenHashMap<ResourceLocation> map = index;
        if (map == null) {
            map = buildIndex();
            if (map == null) {
                return -1;
            }
        }
        return map.getInt(id);
    }

    private static synchronized @Nullable Object2IntOpenHashMap<ResourceLocation> buildIndex() {
        if (index != null) {
            return index;
        }
        for (Subtype<?, ?> type : SUBTYPES) {
            if (type.registry() == null || type.registry().isEmpty()) {
                // Cedo demais (registros ainda não criados ou não preenchidos): tenta de novo no próximo uso.
                return null;
            }
        }
        Object2IntOpenHashMap<ResourceLocation> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);
        for (Subtype<?, ?> type : SUBTYPES) {
            for (ResourceLocation id : type.registry().getKeys()) {
                if (id.equals(EMPTY)) {
                    continue;
                }
                int first = map.getInt(id);
                if (first >= 0) {
                    if (collisionsLogged) {
                        continue;
                    }
                    WirelessAutomate.LOGGER.warn("O químico {} existe como {} e como {}; o Wireless Automate usa o {}",
                            id, SUBTYPES.get(first).name, type.name, SUBTYPES.get(first).name);
                } else {
                    map.put(id, type.index);
                }
            }
        }
        collisionsLogged = true;
        index = map;
        return map;
    }

    /** O tipo do químico {@code id}, ou {@code null} se ele não existe. */
    public static @Nullable Subtype<?, ?> typeOf(ResourceLocation id) {
        int subtype = subtype(id);
        return subtype < 0 ? null : SUBTYPES.get(subtype);
    }

    /** Tanques de um handler de químico de qualquer tipo. */
    public static int tanks(Object handler) {
        return ((IChemicalHandler<?, ?>) handler).getTanks();
    }

    /** Id do químico ({@code mekanism:hydrogen}); a chave dos filtros. Chamado no laço, não aloca. */
    public static ResourceLocation id(ChemicalStack<?> stack) {
        ResourceLocation key = stack.getTypeRegistryName();
        return key != null ? key : EMPTY;
    }

    /** O primeiro químico guardado num item (tanque, cilindro), procurando nos tipos em ordem, se houver. */
    public static Optional<ResourceLocation> chemicalIn(ItemStack stack) {
        for (Subtype<?, ?> type : SUBTYPES) {
            IChemicalHandler<?, ?> handler = type.item(stack);
            if (handler == null) {
                continue;
            }
            for (int tank = 0, n = handler.getTanks(); tank < n; tank++) {
                ChemicalStack<?> inTank = handler.getChemicalInTank(tank);
                if (!inTank.isEmpty()) {
                    return Optional.of(id(inTank));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Id do químico de um ingrediente de fora, se for um. O JEI entrega {@link GasStack}, {@link InfusionStack},
     * {@link PigmentStack} ou {@link SlurryStack}: todos {@link ChemicalStack}.
     */
    public static Optional<ResourceLocation> ingredientId(Object ingredient) {
        return ingredient instanceof ChemicalStack<?> stack && !stack.isEmpty() ? Optional.of(id(stack)) : Optional.empty();
    }

    /** O químico como ingrediente do JEI (a pilha do tipo achado, de 1.000 mB), ou {@code null} se ele não existe. */
    public static @Nullable Object ingredient(ResourceLocation id) {
        Subtype<?, ?> type = typeOf(id);
        if (type == null) {
            return null;
        }
        ChemicalStack<?> stack = type.stack(id, 1_000);
        return stack.isEmpty() ? null : stack;
    }

    /** Pilha de {@code amount} do químico {@code id}, do tipo achado, ou {@code null} se ele não existe. */
    public static @Nullable ChemicalStack<?> stack(ResourceLocation id, long amount) {
        Subtype<?, ?> type = typeOf(id);
        return type == null ? null : type.stack(id, amount);
    }

    /** Existe um químico registrado com esse id em algum dos quatro registros (o vazio não conta). */
    public static boolean exists(ResourceLocation id) {
        return subtype(id) >= 0;
    }

    private static @Nullable Chemical<?> chemical(ResourceLocation id) {
        Subtype<?, ?> type = typeOf(id);
        return type == null ? null : type.registry().getValue(id);
    }

    public static @Nullable Component name(ResourceLocation id) {
        Chemical<?> chemical = chemical(id);
        return chemical == null ? null : chemical.getTextComponent();
    }

    /** Textura do químico (no atlas de blocos), ou {@code null} se ele não existe. */
    public static @Nullable ResourceLocation icon(ResourceLocation id) {
        Chemical<?> chemical = chemical(id);
        return chemical == null ? null : chemical.getIcon();
    }

    public static int tint(ResourceLocation id) {
        Chemical<?> chemical = chemical(id);
        return chemical == null ? 0xFFFFFF : chemical.getTint();
    }

    private MekanismChemicals() {
    }
}
