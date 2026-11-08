package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * O livro-guia do mod ({@link GuideBookItem}): vai na aba criativa e é entregue uma vez a cada jogador no
 * primeiro login (config {@code guide.giveOnFirstJoin}). A marca de entrega fica nos dados persistentes do
 * jogador, que sobrevivem à morte.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID)
public final class GuideBook {
    /** A marca da entrega do livro próprio do mod. */
    private static final String GIVEN = WirelessAutomate.MODID + ":guide_v2_given";

    /** O livro, numa pilha nova. */
    public static ItemStack create() {
        return new ItemStack(ModItems.GUIDE.get());
    }

    /** O livro já foi entregue a este jogador. */
    public static boolean given(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(GIVEN);
    }

    /** Entrega o livro ao jogador (no inventário, ou no chão se estiver cheio) se ainda não entregou, e marca. */
    public static boolean deliver(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN)) {
            return false;
        }
        ItemStack book = create();
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        persisted.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        return true;
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && Config.SPEC.isLoaded() && Config.GIVE_GUIDE_ON_FIRST_JOIN.get()) {
            deliver(player);
        }
    }

    private GuideBook() {
    }
}
