package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.Sources;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Cartão de Upgrade de tier: um item por tier de destino ({@code tier_core_<destino>}), com o tier de origem no
 * componente {@code upgrade_from} ({@link ModDataComponents#UPGRADE_FROM}). Shift + clique (vale também sem o
 * Shift) num roteador, armazenamento ou Núcleo de Ressonância colocado o leva ao tier do cartão no lugar, sem perder
 * a configuração nem o conteúdo; ou junte os dois na bancada ({@code RouterUpgradeRecipe}, só roteador e
 * armazenamento). O Núcleo pede também 1 Coração do Ressonador do tier de destino no inventário.
 *
 * <p>Um cartão com origem k ("k → destino") só vale num bloco exatamente do tier k; num bloco de outro tier, o
 * cartão fica na mão e a action bar avisa. O cartão sem origem é o cartão antigo, de antes desta regra: sobe de
 * qualquer tier abaixo, como antes.
 *
 * <p>As receitas de cada tier usam um anel só, com três centros: Redstone (o cartão de um degrau, com origem no
 * tier anterior da escada), um cartão do tier anterior (o cartão que pula, que mantém a origem) e o bloco do tier
 * anterior (a receita direta). Assim, subir custa o mesmo pela bancada e pelo cartão.
 *
 * <p>O tooltip mostra o que ele aumenta, com os valores da config do servidor (sincronizada com o cliente) ou os
 * padrões: "origem → destino" no cartão com origem, só o destino no cartão antigo.
 */
public class TierCoreItem extends Item {
    private final RouterTier tier;

    public TierCoreItem(RouterTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public RouterTier tier() {
        return tier;
    }

    /** Tier de origem do cartão, ou {@code null} no cartão antigo (sem o componente). */
    public static @Nullable RouterTier origin(ItemStack stack) {
        return stack.get(ModDataComponents.UPGRADE_FROM.get());
    }

    /** Cartão "from → to" ({@code to} de Avançado a Ultimate); {@code from == null} dá o cartão antigo. */
    public static ItemStack card(RouterTier to, @Nullable RouterTier from) {
        ItemStack stack = new ItemStack(ModItems.TIER_CORES.get(to).get());
        if (from != null) {
            stack.set(ModDataComponents.UPGRADE_FROM.get(), from);
        }
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!tier.loaded()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        Target target = state.getBlock() instanceof RouterBlock ? Target.ROUTER
                : state.getBlock() instanceof StorageBlock ? Target.STORAGE
                : state.getBlock() instanceof ResonanceCoreBlock ? Target.CORE : null;
        if (target == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        RouterTier origin = origin(stack);
        RouterTier blockTier = state.getValue(RouterBlock.TIER);
        Player player = context.getPlayer();
        if (!blockTier.acceptsCard(origin, tier)) {
            if (origin == null) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(Component.translatable(
                        KEY + "refused." + target.key, tierName(origin))
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        boolean upgraded = switch (target) {
            case ROUTER -> RouterBlock.tryUpgrade(level, context.getClickedPos(), tier, origin);
            case STORAGE -> StorageBlock.tryUpgrade(level, context.getClickedPos(), tier, origin);
            case CORE -> ResonanceCoreBlock.tryUpgrade(level, context.getClickedPos(), tier, origin, player);
        };
        if (!upgraded) {
            // No Núcleo, a única recusa que sobra é a falta do Coração do Ressonador, já avisada na action bar.
            return target == Target.CORE ? InteractionResult.FAIL : InteractionResult.PASS;
        }
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(player, context.getClickedPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS,
                1.0F, 1.0F);
        if (!level.isClientSide && player != null) {
            Component from = tierName(blockTier).withStyle(ChatFormatting.GREEN);
            Component to = tierName(tier).withStyle(ChatFormatting.GREEN);
            player.displayClientMessage(target == Target.ROUTER
                    ? Component.translatable(KEY + "upgraded.router", from, to)
                    : Component.translatable(KEY + "upgraded." + target.key, state.getBlock().getName(), from, to),
                    true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Com origem, "Cartão de Upgrade: Básico → Elite"; sem origem (cartão antigo), o nome do item. */
    @Override
    public Component getName(ItemStack stack) {
        RouterTier origin = origin(stack);
        if (origin == null) {
            return super.getName(stack);
        }
        return Component.translatable(KEY + "named", tierName(origin), tierName(tier));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (!tier.loaded()) {
            tooltip.add(Component.translatable(KEY + "requires").withStyle(ChatFormatting.RED));
        }
        if (tier.previous() == null) {
            return;
        }
        RouterTier origin = origin(stack);
        if (origin != null) {
            tooltip.add(Component.translatable(KEY + "upgrades_from", tierName(origin), tierName(tier))
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(KEY + "only", tierName(origin)).withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable(KEY + "legacy", tierName(tier)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(KEY + "heart", ModItems.RESONATOR_HEARTS.get(tier).get().getDescription())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(line("items", origin, t -> rate(t, Stat.ITEMS)));
        tooltip.add(line("fluids", origin, t -> rate(t, Stat.FLUID)));
        tooltip.add(line("energy", origin, t -> rate(t, Stat.ENERGY)));
        if (Sources.LOADED) {
            tooltip.add(line("source", origin, t -> rate(t, Stat.SOURCE)));
        }
        tooltip.add(line("range", origin, TierCoreItem::range));
        for (StorageKind kind : StorageKind.values()) {
            if (kind.loaded()) {
                tooltip.add(line(kind.id, origin, t -> capacity(kind, t)));
            }
        }
        tooltip.add(Component.translatable(KEY + "use").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static final String KEY = "item.wirelessautomate.tier_core.";

    /** O bloco que o cartão sobe no clique; {@code key} completa as chaves {@code refused.*} e {@code upgraded.*}. */
    private enum Target {
        ROUTER("router"),
        STORAGE("storage"),
        CORE("core");

        final String key;

        Target(String key) {
            this.key = key;
        }
    }

    private enum Stat {
        ITEMS(ResourceType.ITEM),
        FLUID(ResourceType.FLUID),
        ENERGY(ResourceType.ENERGY),
        SOURCE(ResourceType.SOURCE);

        final ResourceType type;

        Stat(ResourceType type) {
            this.type = type;
        }
    }

    private static MutableComponent tierName(RouterTier tier) {
        return Component.translatable(tier.translationKey());
    }

    /** Linha do tooltip: o valor do destino no cartão antigo, ou "origem → destino" ({@code change}) com origem. */
    private Component line(String key, @Nullable RouterTier origin, Function<RouterTier, Component> value) {
        if (origin == null) {
            return line(key, value.apply(tier));
        }
        return line(key, Component.translatable(KEY + "change",
                value.apply(origin).copy().withStyle(ChatFormatting.AQUA),
                value.apply(tier).copy().withStyle(ChatFormatting.AQUA)));
    }

    private static Component line(String key, Component value) {
        return Component.translatable(KEY + key, value.copy().withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.DARK_AQUA);
    }

    /** Vazão do tier pela config (padrão se ela ainda não carregou); 0 = sem limite. */
    private static Component rate(RouterTier tier, Stat stat) {
        long value = Config.TIERS.get(tier).rate(stat.type);
        return value <= 0 ? Component.translatable(KEY + "unlimited") : Component.literal(grouped(value));
    }

    /**
     * Número com separador de milhar do idioma: a chave {@code wirelessautomate.number.group} é
     * "." em português e "," em inglês ({@link Language} existe no cliente e no servidor).
     */
    public static String grouped(long value) {
        String separator = Language.getInstance().getOrDefault("wirelessautomate.number.group", ",");
        return String.format(Locale.ROOT, "%,d", value).replace(",", separator);
    }

    /** Capacidade do armazenamento no tier, pela config; 0 = sem limite. */
    private static Component capacity(StorageKind kind, RouterTier tier) {
        long value = Config.storageCapacity(kind, tier);
        return value <= 0 ? Component.translatable(KEY + "unlimited") : Component.literal(grouped(value));
    }

    private static Component range(RouterTier tier) {
        Config.TierValues values = Config.TIERS.get(tier);
        boolean cross = Config.read(values.crossDimension());
        int range = Config.read(values.range());
        if (cross) {
            return Component.translatable(KEY + "range.all_dimensions");
        }
        return range <= 0 ? Component.translatable(KEY + "range.dimension")
                : Component.translatable(KEY + "range.blocks", grouped(range));
    }
}
