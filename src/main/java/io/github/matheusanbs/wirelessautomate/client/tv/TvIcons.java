package io.github.matheusanbs.wirelessautomate.client.tv;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle.IconExtra;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle.IconKind;
import java.util.HashMap;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * O que o Ícone da Tela mostra, no cliente: o item (com os componentes que ele tenha, como as gemas do Apotheosis),
 * o fluido ou o químico do Mekanism. Quem desenha na textura da Tela ({@code TvPaintIcon}) e o espaço do editor usam
 * as mesmas funções. O item com componentes sai do NBT que o JEI entregou ({@link #extra}) e é decodificado uma vez
 * (um cache pequeno por NBT), com o acesso aos registros do mundo em que o jogador está.
 */
public final class TvIcons {
    private static final int CACHE_MAX = 64;
    private static final HashMap<CompoundTag, ItemStack> CACHE = new HashMap<>();

    private TvIcons() {
    }

    /** O Ícone de um item do JEI ({@code stack}) ou de um fluido ou químico, pelo ingrediente; vazio se não serve. */
    public static @Nullable TvPartStyle choose(TvPartStyle part, Object ingredient) {
        if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
            return part.withIcon(Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem())), extra(stack));
        }
        if (ingredient instanceof FluidStack fluid && !fluid.isEmpty()) {
            return part.withIcon(Optional.of(BuiltInRegistries.FLUID.getKey(fluid.getFluid())),
                    new IconExtra(IconKind.FLUID, Optional.empty()));
        }
        Optional<ResourceLocation> chemical = Chemicals.ingredientId(ingredient);
        return chemical.map(id -> part.withIcon(Optional.of(id), new IconExtra(IconKind.CHEMICAL, Optional.empty())))
                .orElse(null);
    }

    /** Se o ingrediente pode virar um Ícone. */
    public static boolean accepts(Object ingredient) {
        return choose(TvPartStyle.DEFAULT, ingredient) != null;
    }

    /** O item inteiro codificado, só se tem componentes além dos do padrão (um item comum fica só com o id). */
    private static IconExtra extra(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (stack.getComponentsPatch().isEmpty() || minecraft.level == null) {
            return IconExtra.NONE;
        }
        var ops = minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Optional<CompoundTag> tag = ItemStack.SINGLE_ITEM_CODEC.encodeStart(ops, stack).result()
                .filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast);
        return new IconExtra(IconKind.ITEM, tag);
    }

    /** O item do Ícone (com os componentes, se guardou), ou vazio. */
    public static ItemStack stack(TvPartStyle part) {
        if (part.iconKind() != IconKind.ITEM || part.item().isEmpty()) {
            return ItemStack.EMPTY;
        }
        Optional<CompoundTag> tag = part.icon().stack();
        if (tag.isPresent()) {
            ItemStack decoded = decode(tag.get());
            if (!decoded.isEmpty()) {
                return decoded;
            }
        }
        var item = BuiltInRegistries.ITEM.get(part.item().get());
        return item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ItemStack decode(CompoundTag tag) {
        ItemStack cached = CACHE.get(tag);
        if (cached != null) {
            return cached;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return ItemStack.EMPTY;
        }
        RegistryAccess access = minecraft.level.registryAccess();
        ItemStack stack = ItemStack.SINGLE_ITEM_CODEC.parse(access.createSerializationContext(NbtOps.INSTANCE), tag)
                .result().orElse(ItemStack.EMPTY);
        if (CACHE.size() >= CACHE_MAX) {
            CACHE.clear();
        }
        CACHE.put(tag.copy(), stack);
        return stack;
    }

    private static @Nullable Fluid fluid(TvPartStyle part) {
        if (part.iconKind() != IconKind.FLUID || part.item().isEmpty()) {
            return null;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(part.item().get());
        return fluid == Fluids.EMPTY ? null : fluid;
    }

    /** Se há o que desenhar (o id pode ser de um mod que saiu). */
    public static boolean present(TvPartStyle part) {
        return switch (part.iconKind()) {
            case ITEM -> !stack(part).isEmpty();
            case FLUID -> fluid(part) != null;
            case CHEMICAL -> part.item().isPresent() && Chemicals.exists(part.item().get());
        };
    }

    /** Fluido ou químico (o item o chamador desenha pelo {@code ItemRenderer}): 16 × 16 em {@code (x, y)}. */
    public static void drawFlat(GuiGraphics g, Font font, TvPartStyle part, int x, int y) {
        Fluid fluid = fluid(part);
        if (fluid != null) {
            GuiPaint.fluid(g, new FluidStack(fluid, 1000), x, y);
        } else if (part.iconKind() == IconKind.CHEMICAL && part.item().isPresent()) {
            GuiPaint.chemical(g, font, part.item().get(), x, y, GuiPaint.MUTED);
        }
    }

    /** Se a imagem se mexe: relógio, bússola, brilho, textura animada ou um fluido. */
    public static boolean animated(TvPartStyle part) {
        if (part.iconKind() == IconKind.FLUID) {
            return fluid(part) != null;
        }
        ItemStack stack = stack(part);
        if (stack.isEmpty()) {
            return false;
        }
        var item = stack.getItem();
        if (item == net.minecraft.world.item.Items.CLOCK || item == net.minecraft.world.item.Items.COMPASS
                || item == net.minecraft.world.item.Items.RECOVERY_COMPASS || stack.hasFoil()) {
            return true;
        }
        var sprite = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0)
                .getParticleIcon(net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
        return sprite.contents().getUniqueFrames().count() > 1;
    }

    /** O nome para o espaço do editor e a dica. */
    public static Component name(TvPartStyle part) {
        return switch (part.iconKind()) {
            case ITEM -> stack(part).isEmpty() ? Component.literal(String.valueOf(part.item().orElse(null)))
                    : stack(part).getHoverName();
            case FLUID -> fluid(part) != null ? new FluidStack(fluid(part), 1000).getHoverName()
                    : Component.literal(String.valueOf(part.item().orElse(null)));
            case CHEMICAL -> part.item().isPresent() && Chemicals.exists(part.item().get())
                    ? Chemicals.name(part.item().get()) : Component.literal(String.valueOf(part.item().orElse(null)));
        };
    }
}
