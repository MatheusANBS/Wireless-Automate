package io.github.matheusanbs.wirelessautomate.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

/**
 * Regra de item por propriedade: pega o item que cumpre <b>todas</b> as condições marcadas, sem
 * depender de qual item é ("qualquer item encantado", "ferramenta com menos de 25% de durabilidade").
 * Imutável; vira a entrada {@link FilterEntry.RuleEntry} do filtro.
 *
 * @param flags       cada {@link Property} marcada: {@code true} = precisa ter, {@code false} = não pode ter
 * @param enchantment encantamento com nível mínimo (no item ou guardado num livro)
 * @param durability  durabilidade restante, em porcentagem; só itens que gastam
 * @param scope       "só em": vazio, {@code #tag} ou {@code @mod}
 */
public record ItemRule(Map<Property, Boolean> flags, Optional<Enchant> enchantment, Optional<Durability> durability,
        String scope) {
    public static final int MAX_SCOPE = 128;
    public static final ItemRule EMPTY = new ItemRule(Map.of(), Optional.empty(), Optional.empty(), "");

    public ItemRule {
        EnumMap<Property, Boolean> copy = new EnumMap<>(Property.class);
        copy.putAll(flags);
        flags = Map.copyOf(copy);
        scope = scope.strip();
    }

    /** Propriedades de sim ou não, na ordem da tela. */
    public enum Property implements StringRepresentable {
        /** Com algum encantamento, no item ou guardado (livro encantado). */
        ENCHANTED,
        /** Com algum desgaste. */
        DAMAGED,
        /** Com nome dado na bigorna. */
        NAMED,
        /** Com efeito de poção (poções, flechas, ensopado suspeito). */
        POTION,
        /** Empilha mais de um. */
        STACKABLE,
        /** Guarda itens dentro (caixa de shulker, bundle). */
        CONTENTS;

        public static final Codec<Property> CODEC = StringRepresentable.fromEnum(Property::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Encantamento {@code id} com nível {@code minLevel} ou mais. */
    public record Enchant(ResourceLocation id, int minLevel) {
        public static final int MAX_LEVEL = 255;

        public Enchant {
            minLevel = Math.max(1, Math.min(MAX_LEVEL, minLevel));
        }

        static final Codec<Enchant> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(Enchant::id),
                Codec.INT.optionalFieldOf("level", 1).forGetter(Enchant::minLevel)).apply(i, Enchant::new));
        static final StreamCodec<ByteBuf, Enchant> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Enchant::id, ByteBufCodecs.VAR_INT, Enchant::minLevel, Enchant::new);
    }

    /** Durabilidade restante: {@code atLeast} = pelo menos {@code percent}%, senão abaixo de {@code percent}%. */
    public record Durability(boolean atLeast, int percent) {
        public Durability {
            percent = Math.max(1, Math.min(100, percent));
        }

        static final Codec<Durability> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("at_least", true).forGetter(Durability::atLeast),
                Codec.INT.fieldOf("percent").forGetter(Durability::percent)).apply(i, Durability::new));
        static final StreamCodec<ByteBuf, Durability> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Durability::atLeast, ByteBufCodecs.VAR_INT, Durability::percent, Durability::new);

        /** {@code remaining} de {@code max} cumpre a condição? Sem conta em ponto flutuante. */
        public boolean test(int remaining, int max) {
            boolean enough = (long) remaining * 100 >= (long) percent * max;
            return atLeast == enough;
        }
    }

    public static final Codec<ItemRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Property.CODEC, Codec.BOOL).optionalFieldOf("flags", Map.of()).forGetter(ItemRule::flags),
            Enchant.CODEC.optionalFieldOf("enchantment").forGetter(ItemRule::enchantment),
            Durability.CODEC.optionalFieldOf("durability").forGetter(ItemRule::durability),
            Codec.STRING.optionalFieldOf("scope", "").forGetter(ItemRule::scope)).apply(i, ItemRule::new));

    public static final StreamCodec<ByteBuf, ItemRule> STREAM_CODEC = StreamCodec.of(ItemRule::encode, ItemRule::decode);

    private static void encode(ByteBuf buf, ItemRule rule) {
        int yes = 0;
        int no = 0;
        for (Map.Entry<Property, Boolean> flag : rule.flags.entrySet()) {
            if (flag.getValue()) {
                yes |= 1 << flag.getKey().ordinal();
            } else {
                no |= 1 << flag.getKey().ordinal();
            }
        }
        ByteBufCodecs.VAR_INT.encode(buf, yes);
        ByteBufCodecs.VAR_INT.encode(buf, no);
        ByteBufCodecs.optional(Enchant.STREAM_CODEC).encode(buf, rule.enchantment);
        ByteBufCodecs.optional(Durability.STREAM_CODEC).encode(buf, rule.durability);
        ByteBufCodecs.stringUtf8(MAX_SCOPE).encode(buf, rule.scope);
    }

    private static ItemRule decode(ByteBuf buf) {
        int yes = ByteBufCodecs.VAR_INT.decode(buf);
        int no = ByteBufCodecs.VAR_INT.decode(buf);
        EnumMap<Property, Boolean> flags = new EnumMap<>(Property.class);
        for (Property property : Property.values()) {
            int bit = 1 << property.ordinal();
            if ((yes & bit) != 0) {
                flags.put(property, true);
            } else if ((no & bit) != 0) {
                flags.put(property, false);
            }
        }
        return new ItemRule(flags, ByteBufCodecs.optional(Enchant.STREAM_CODEC).decode(buf),
                ByteBufCodecs.optional(Durability.STREAM_CODEC).decode(buf), ByteBufCodecs.stringUtf8(MAX_SCOPE).decode(buf));
    }

    // ------------------------------------------------------------------ edição

    /** Sem nenhuma condição: não é uma regra válida (pegaria tudo). */
    public boolean isEmpty() {
        return flags.isEmpty() && enchantment.isEmpty() && durability.isEmpty() && scope.isEmpty();
    }

    /** O "só em" é vazio, uma tag ({@code #c:armors}) ou um mod ({@code @mekanism}) bem escritos. */
    public boolean validScope() {
        return scope.isEmpty() || scopeTag() != null || scopeMod() != null;
    }

    /** Regra que o servidor aceita: alguma condição e o "só em" bem escrito. */
    public boolean isValid() {
        return !isEmpty() && validScope() && scope.length() <= MAX_SCOPE;
    }

    public @Nullable Boolean flag(Property property) {
        return flags.get(property);
    }

    /** Marca a propriedade: {@code true}, {@code false} ou {@code null} (tanto faz). */
    public ItemRule withFlag(Property property, @Nullable Boolean value) {
        EnumMap<Property, Boolean> copy = new EnumMap<>(Property.class);
        copy.putAll(flags);
        if (value == null) {
            copy.remove(property);
        } else {
            copy.put(property, value);
        }
        return new ItemRule(copy, enchantment, durability, scope);
    }

    public ItemRule withEnchantment(Optional<Enchant> enchantment) {
        return new ItemRule(flags, enchantment, durability, scope);
    }

    public ItemRule withDurability(Optional<Durability> durability) {
        return new ItemRule(flags, enchantment, durability, scope);
    }

    public ItemRule withScope(String scope) {
        return new ItemRule(flags, enchantment, durability, scope);
    }

    /** A tag do "só em" ({@code #c:armors}), ou {@code null}. */
    public @Nullable ResourceLocation scopeTag() {
        if (!scope.startsWith("#")) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(scope.substring(1));
        return id == null || id.getPath().isEmpty() ? null : id;
    }

    /** O mod do "só em" ({@code @mekanism}), ou {@code null}. */
    public @Nullable String scopeMod() {
        if (!scope.startsWith("@")) {
            return null;
        }
        String mod = scope.substring(1);
        return mod.matches("[a-z0-9_.-]{1,64}") ? mod : null;
    }

    // ------------------------------------------------------------------ texto

    /** "Encantado · Fortuna ≥ III · Durabilidade < 25% em #c:tools", para a tela e o tooltip do cartão. */
    public Component describe() {
        MutableComponent text = Component.empty();
        for (Property property : Property.values()) {
            Boolean value = flags.get(property);
            if (value != null) {
                part(text, Component.translatable(TEXT + "prop." + property.getSerializedName() + (value ? "" : ".no")));
            }
        }
        enchantment.ifPresent(e -> part(text, Component.translatable(TEXT + "text.enchantment", enchantmentName(e.id()),
                Component.translatable("enchantment.level." + e.minLevel()))));
        durability.ifPresent(d -> part(text, Component.translatable(
                TEXT + (d.atLeast() ? "text.durability.at_least" : "text.durability.below"), d.percent())));
        if (text.getSiblings().isEmpty()) {
            text.append(Component.translatable(TEXT + "text.any"));
        }
        if (!scope.isEmpty()) {
            text.append(Component.translatable(TEXT + "text.scope", scope));
        }
        return text;
    }

    /** Nome traduzido de um encantamento pelo id ({@code enchantment.minecraft.fortune}), sem o registro. */
    public static Component enchantmentName(ResourceLocation id) {
        return Component.translatable(Util.makeDescriptionId("enchantment", id));
    }

    private static final String TEXT = "gui.wirelessautomate.filter.rule.";

    private static void part(MutableComponent text, Component part) {
        if (!text.getSiblings().isEmpty()) {
            text.append(" · ");
        }
        text.append(part);
    }

    // ------------------------------------------------------------------ correspondência

    /**
     * A regra pronta para perguntar sobre pilhas: o "só em" e o encantamento já resolvidos. O filtro
     * compila uma vez ({@link ItemMatcher}); a tela, para acender o inventário.
     */
    public Predicate<ItemStack> compile() {
        ResourceLocation tagId = scopeTag();
        TagKey<Item> tag = tagId == null ? null : TagKey.create(Registries.ITEM, tagId);
        String mod = scopeMod();
        boolean badScope = !scope.isEmpty() && tag == null && mod == null;
        ResourceKey<Enchantment> enchantKey = enchantment.map(e -> ResourceKey.create(Registries.ENCHANTMENT, e.id())).orElse(null);
        int minLevel = enchantment.map(Enchant::minLevel).orElse(0);
        Property[] properties = flags.keySet().toArray(Property[]::new);
        boolean[] wanted = new boolean[properties.length];
        for (int i = 0; i < properties.length; i++) {
            wanted[i] = flags.get(properties[i]);
        }
        Durability wear = durability.orElse(null);
        return stack -> {
            if (badScope || stack.isEmpty()) {
                return false;
            }
            if (tag != null && !stack.is(tag)) {
                return false;
            }
            if (mod != null && !namespace(stack.getItem()).equals(mod)) {
                return false;
            }
            for (int i = 0; i < properties.length; i++) {
                if (has(stack, properties[i]) != wanted[i]) {
                    return false;
                }
            }
            if (enchantKey != null && enchantLevel(stack, enchantKey) < minLevel) {
                return false;
            }
            if (wear != null) {
                if (!stack.isDamageableItem()) {
                    return false;
                }
                int max = stack.getMaxDamage();
                if (!wear.test(max - stack.getDamageValue(), max)) {
                    return false;
                }
            }
            return true;
        };
    }

    /** A pilha tem a propriedade? */
    public static boolean has(ItemStack stack, Property property) {
        return switch (property) {
            case ENCHANTED -> !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
                    || !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
            case DAMAGED -> stack.isDamaged();
            case NAMED -> stack.has(DataComponents.CUSTOM_NAME);
            case POTION -> stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).hasEffects()
                    || !stack.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY).effects().isEmpty();
            case STACKABLE -> stack.getMaxStackSize() > 1;
            case CONTENTS -> stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems().iterator().hasNext()
                    || !stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).isEmpty();
        };
    }

    /** Maior nível do encantamento na pilha, no item ou guardado (livro); 0 se não tem. */
    public static int enchantLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        return Math.max(level(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY), key),
                level(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY), key));
    }

    private static int level(ItemEnchantments enchantments, ResourceKey<Enchantment> key) {
        if (enchantments.isEmpty()) {
            return 0;
        }
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            if (entry.getKey().is(key)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    @SuppressWarnings("deprecation")
    private static String namespace(Item item) {
        return item.builtInRegistryHolder().key().location().getNamespace();
    }
}
