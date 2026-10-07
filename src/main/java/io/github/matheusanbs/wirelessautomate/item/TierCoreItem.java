package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Cartão de upgrade de tier: clique no roteador para subir de tier no lugar, sem perder a
 * configuração (ou junte os dois na bancada, {@code RouterUpgradeRecipe}). O tooltip mostra o que
 * ele aumenta, com os valores da config do servidor (sincronizada com o cliente) ou os padrões.
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

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!RouterBlock.tryUpgrade(context.getLevel(), context.getClickedPos(), tier)) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        context.getLevel().playSound(player, context.getClickedPos(), SoundEvents.SMITHING_TABLE_USE,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        RouterTier from = RouterTier.values()[tier.ordinal() - 1];
        tooltip.add(Component.translatable(KEY + "upgrades", Component.translatable(from.translationKey()),
                Component.translatable(tier.translationKey())).withStyle(ChatFormatting.GRAY));
        tooltip.add(line("items", rate(from, Stat.ITEMS), rate(tier, Stat.ITEMS)));
        tooltip.add(line("fluids", rate(from, Stat.FLUID), rate(tier, Stat.FLUID)));
        tooltip.add(line("energy", rate(from, Stat.ENERGY), rate(tier, Stat.ENERGY)));
        tooltip.add(line("range", range(from), range(tier)));
        tooltip.add(Component.translatable(KEY + "use").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static final String KEY = "item.wirelessautomate.tier_core.";

    private enum Stat {
        ITEMS,
        FLUID,
        ENERGY
    }

    private static Component line(String key, Component before, Component after) {
        return Component.translatable(KEY + key, before.copy().withStyle(ChatFormatting.GRAY),
                after.copy().withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.DARK_AQUA);
    }

    /** Vazão do tier pela config (padrão se ela ainda não carregou); 0 = sem limite. */
    private static Component rate(RouterTier tier, Stat stat) {
        Config.TierValues values = Config.TIERS.get(tier);
        boolean loaded = Config.SPEC.isLoaded();
        long value = switch (stat) {
            case ITEMS -> loaded ? values.itemsPerSecond().get() : tier.defaultItemsPerSecond;
            case FLUID -> loaded ? values.fluidPerSecond().get() : tier.defaultFluidPerSecond;
            case ENERGY -> loaded ? values.energyPerTick().get() : tier.defaultEnergyPerTick;
        };
        return value <= 0 ? Component.translatable(KEY + "unlimited") : Component.literal(String.format(Locale.ROOT, "%,d", value));
    }

    private static Component range(RouterTier tier) {
        Config.TierValues values = Config.TIERS.get(tier);
        boolean loaded = Config.SPEC.isLoaded();
        boolean cross = loaded ? values.crossDimension().get() : tier.defaultCrossDimension;
        int range = loaded ? values.range().get() : tier.defaultRange;
        if (cross) {
            return Component.translatable(KEY + "range.all_dimensions");
        }
        return range <= 0 ? Component.translatable(KEY + "range.dimension")
                : Component.translatable(KEY + "range.blocks", String.format(Locale.ROOT, "%,d", range));
    }
}
