package io.github.matheusanbs.wirelessautomate.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterCodecs;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.menu.CardFilterTarget;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cartão de Filtro: carrega um filtro completo de itens ou de fluidos. Clique direito no ar abre a
 * tela de filtro do cartão; Shift + clique direito no ar alterna entre itens e fluidos (só vazio).
 * Na tela de filtro de um roteador, com o cartão na mão principal, dá para importar e exportar.
 * Exportar para uma pilha de cartões grava o filtro em todos: é assim que se duplicam.
 */
public class FilterCardItem extends Item {
    private static final String KEY = "item.wirelessautomate.filter_card.";
    /** Entradas listadas na dica do item. */
    private static final int TOOLTIP_ENTRIES = 5;

    /** O componente do cartão: o tipo (ITEM ou FLUID) e o filtro. Sem componente = itens, vazio. */
    public record Contents(ResourceType type, Filter filter) {
        public static final Contents EMPTY = new Contents(ResourceType.ITEM, Filter.EMPTY);

        public Contents {
            Objects.requireNonNull(type);
            Objects.requireNonNull(filter);
            if (!type.cards()) {
                throw new IllegalArgumentException("Cartão de filtro de " + type);
            }
        }

        public boolean isDefault() {
            return equals(EMPTY);
        }

        private static final Codec<ResourceType> TYPE_CODEC = Codec.STRING.comapFlatMap(
                key -> {
                    ResourceType type = ResourceType.byKey(key);
                    return type != null && type.cards() ? DataResult.success(type)
                            : DataResult.error(() -> "tipo de cartão inválido: " + key);
                },
                ResourceType::key);

        public static final Codec<Contents> CODEC = RecordCodecBuilder.create(i -> i.group(
                TYPE_CODEC.optionalFieldOf("type", ResourceType.ITEM).forGetter(Contents::type),
                FilterCodecs.LENIENT.lenientOptionalFieldOf("filter", Filter.EMPTY).forGetter(Contents::filter))
                .apply(i, Contents::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Contents> STREAM_CODEC = StreamCodec.composite(
                NeoForgeStreamCodecs.enumCodec(ResourceType.class), Contents::type,
                Filter.STREAM_CODEC, Contents::filter,
                Contents::new);
    }

    public FilterCardItem(Properties properties) {
        super(properties);
    }

    /** O que o cartão carrega; um cartão sem componente é de itens e vazio. */
    public static Contents contents(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CARD_FILTER.get(), Contents.EMPTY);
    }

    /** Grava no cartão; o conteúdo padrão remove o componente, para empilhar com cartões novos. */
    public static void setContents(ItemStack stack, Contents contents) {
        if (contents.isDefault()) {
            stack.remove(ModDataComponents.CARD_FILTER.get());
        } else {
            stack.set(ModDataComponents.CARD_FILTER.get(), contents);
        }
    }

    public static boolean isCard(ItemStack stack) {
        return stack.getItem() instanceof FilterCardItem;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isSecondaryUseActive()) {
                toggleType(serverPlayer, stack);
            } else {
                CardFilterTarget.open(serverPlayer, hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void toggleType(ServerPlayer player, ItemStack stack) {
        Contents contents = contents(stack);
        if (!contents.filter().isDefault()) {
            player.displayClientMessage(Component.translatable(KEY + "not_empty"), true);
            return;
        }
        ResourceType type = contents.type() == ResourceType.ITEM ? ResourceType.FLUID : ResourceType.ITEM;
        setContents(stack, new Contents(type, Filter.EMPTY));
        player.displayClientMessage(Component.translatable(KEY + "type_changed", typeName(type)), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS,
                0.6F, 1.3F);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !contents(stack).filter().isEmpty() || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        Contents contents = contents(stack);
        Filter filter = contents.filter();
        Component mode = Component.translatable(KEY + (filter.listMode() == Filter.ListMode.BLACKLIST
                ? "tooltip.blacklist" : "tooltip.whitelist"));
        tooltip.add(Component.translatable(KEY + "tooltip.summary", typeName(contents.type()), mode)
                .withStyle(ChatFormatting.AQUA));
        if (filter.isEmpty()) {
            tooltip.add(Component.translatable(KEY + "tooltip.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(KEY + "tooltip.entries", filter.entries().size())
                    .withStyle(ChatFormatting.GRAY));
            List<FilterEntry> entries = filter.entries();
            for (int i = 0; i < Math.min(TOOLTIP_ENTRIES, entries.size()); i++) {
                tooltip.add(Component.literal("  ").append(describe(entries.get(i))).withStyle(ChatFormatting.GRAY));
            }
            if (entries.size() > TOOLTIP_ENTRIES) {
                tooltip.add(Component.translatable(KEY + "tooltip.more", entries.size() - TOOLTIP_ENTRIES)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (contents.type() == ResourceType.ITEM && filter.matchComponents()) {
            tooltip.add(Component.translatable(KEY + "tooltip.components").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(KEY + "tooltip.open").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "tooltip.toggle").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(KEY + "tooltip.router").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Nome curto de uma entrada: o item, fluido ou químico, {@code #tag}, {@code @mod} ou a regra, com o estoque. */
    public static Component describe(FilterEntry entry) {
        Component name = switch (entry) {
            case FilterEntry.ItemEntry e -> e.stack().getHoverName();
            case FilterEntry.FluidEntry e -> e.stack().getHoverName();
            case FilterEntry.TagEntry e -> Component.literal("#" + e.tag());
            case FilterEntry.ModEntry e -> Component.literal("@" + e.modId());
            case FilterEntry.ChemicalEntry e -> Chemicals.name(e.chemical());
            case FilterEntry.RuleEntry e -> e.rule().describe();
        };
        return entry.stock() > 0 ? Component.translatable(KEY + "tooltip.stock", name, entry.stock()) : name;
    }

    public static Component typeName(ResourceType type) {
        return Component.translatable("gui.wirelessautomate.router.type." + type.key());
    }
}
