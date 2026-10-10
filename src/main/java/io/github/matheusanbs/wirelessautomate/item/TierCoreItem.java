package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.Sources;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Cartão de upgrade de tier: clique no roteador (ou num armazenamento) de qualquer tier abaixo para levá-lo ao tier do
 * cartão no lugar, sem perder a configuração (ou junte os dois na bancada, {@code RouterUpgradeRecipe}). O tooltip mostra o que
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
        if (!tier.loaded()) {
            return InteractionResult.PASS;
        }
        if (!RouterBlock.tryUpgrade(context.getLevel(), context.getClickedPos(), tier)
                && !StorageBlock.tryUpgrade(context.getLevel(), context.getClickedPos(), tier)) {
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (!tier.loaded()) {
            tooltip.add(Component.translatable(KEY + "requires").withStyle(ChatFormatting.RED));
        }
        if (tier.previous() == null) {
            return;
        }
        tooltip.add(Component.translatable(KEY + "upgrades", Component.translatable(tier.translationKey()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(line("items", rate(tier, Stat.ITEMS)));
        tooltip.add(line("fluids", rate(tier, Stat.FLUID)));
        tooltip.add(line("energy", rate(tier, Stat.ENERGY)));
        if (Sources.LOADED) {
            tooltip.add(line("source", rate(tier, Stat.SOURCE)));
        }
        tooltip.add(line("range", range(tier)));
        for (StorageKind kind : StorageKind.values()) {
            if (kind.loaded()) {
                tooltip.add(line(kind.id, capacity(kind, tier)));
            }
        }
        tooltip.add(Component.translatable(KEY + "use").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static final String KEY = "item.wirelessautomate.tier_core.";

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
