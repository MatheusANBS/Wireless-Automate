package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * O catalisador da recarga do Núcleo: a regra do que o slot aceita por tier e o que está guardado nele, o item e a
 * contagem (até {@link #MAX}). O slot guarda um item só: outro entra quando a contagem chega a 0. A regra roda só na
 * inserção (clique, menu e Porta); a recarga gasta o que estiver lá, mesmo que o tier não o aceite mais (a redstone de
 * um Núcleo Elite gravado antes da regra, ou o Pó de um Núcleo que subiu para o Allthemodium, acaba normalmente).
 *
 * <p>NBT: a contagem na chave {@code redstone} (a de antes) e o item na {@code catalyst}; sem ela, redstone.
 */
public final class CoreCatalyst {
    public static final int MAX = 64;

    private Item item = Items.REDSTONE;
    private int count;

    /** Se o slot do Núcleo do tier aceita a pilha. Só pilhas sem componentes: o Núcleo guarda só item e contagem. */
    public static boolean accepts(RouterTier coreTier, ItemStack stack) {
        return !stack.isEmpty() && stack.getComponentsPatch().isEmpty() && accepts(coreTier, stack.getItem());
    }

    /**
     * A regra por item, pela {@link CatalystRule}: a redstone só no Básico e no Avançado, o Pó de Ressonância até a
     * Esmeralda e o Pó Carregado só no Núcleo do tier dele. Um catalisador novo é um caso a mais aqui (e no
     * {@link #isCatalyst}).
     */
    static boolean accepts(RouterTier coreTier, Item item) {
        if (item == Items.REDSTONE) {
            return CatalystRule.accepts(coreTier.ordinal(), CatalystRule.Kind.REDSTONE, -1);
        }
        if (item == ModItems.RESONANCE_DUST.get()) {
            return CatalystRule.accepts(coreTier.ordinal(), CatalystRule.Kind.DUST, -1);
        }
        return item instanceof ChargedDustItem charged
                && CatalystRule.accepts(coreTier.ordinal(), CatalystRule.Kind.CHARGED, charged.tier().ordinal());
    }

    /** Se o item é catalisador em algum tier: o clique com ele no Núcleo é tratado, e o NBT o aceita. */
    public static boolean isCatalyst(Item item) {
        return item == Items.REDSTONE || item == ModItems.RESONANCE_DUST.get() || item instanceof ChargedDustItem;
    }

    /** O motivo da recusa de um catalisador que o Núcleo do tier não aceita. */
    public static Component refusal(RouterTier coreTier) {
        if (CatalystRule.needsCharged(coreTier.ordinal())) {
            return Component.translatable("block.wirelessautomate.resonance_core.needs_charged_dust",
                    ModItems.CHARGED_DUSTS.get(coreTier).get().getDescription());
        }
        return Component.translatable("block.wirelessautomate.resonance_core.needs_dust");
    }

    public int count() {
        return count;
    }

    public Item item() {
        return item;
    }

    /** O conteúdo como pilha nova, ou {@link ItemStack#EMPTY}. */
    public ItemStack stack() {
        return count > 0 ? new ItemStack(item, count) : ItemStack.EMPTY;
    }

    /** Se a pilha entra agora: a regra do tier e, com o slot ocupado, o mesmo item. */
    public boolean canAdd(RouterTier coreTier, ItemStack stack) {
        return accepts(coreTier, stack) && (count == 0 || stack.is(item));
    }

    /** Soma até {@code amount} do {@code added} sem passar de {@link #MAX}; devolve quanto entrou. Sem checar o tier. */
    int add(Item added, int amount) {
        if (count > 0 && added != item) {
            return 0;
        }
        int moved = Math.max(0, Math.min(amount, MAX - count));
        if (moved > 0) {
            item = added;
            count += moved;
        }
        return moved;
    }

    /** Troca o conteúdo: {@code amount} entre 0 e {@link #MAX}. */
    void set(Item newItem, int amount) {
        item = newItem;
        count = Math.max(0, Math.min(MAX, amount));
    }

    void spend() {
        count = Math.max(0, count - 1);
    }

    void save(CompoundTag tag) {
        tag.putInt("redstone", count);
        tag.putString("catalyst", BuiltInRegistries.ITEM.getKey(item).toString());
    }

    void load(CompoundTag tag) {
        count = Math.max(0, Math.min(MAX, tag.getInt("redstone")));
        item = Items.REDSTONE;
        if (tag.contains("catalyst")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("catalyst"));
            Item loaded = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (loaded != null && isCatalyst(loaded)) {
                item = loaded;
            }
        }
    }
}
