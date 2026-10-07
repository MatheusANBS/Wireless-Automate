package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Núcleo de tier: clique no roteador para subir de tier no lugar, sem perder a configuração. */
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
}
