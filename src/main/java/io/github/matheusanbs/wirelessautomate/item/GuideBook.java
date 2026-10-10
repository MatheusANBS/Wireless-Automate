package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * O livro-guia (GuideME, opcional): o item genérico dele ({@code guideme:guide}) com o id do nosso guia
 * no NBT (porte 1.20.1: o GuideME 20.1 lê a chave {@code guideId}, string, no lugar do componente
 * {@code guideme:guide_id}). Só pelo registro, sem classes do GuideME; sem ele, não há livro. Vai na aba criativa e é entregue uma vez a cada jogador no
 * primeiro login (config {@code guide.giveOnFirstJoin}).
 */
@Mod.EventBusSubscriber(modid = WirelessAutomate.MODID)
public final class GuideBook {
    /** Marca nos dados persistentes do jogador (sobrevivem à morte): o livro já foi entregue. */
    private static final String GIVEN = WirelessAutomate.MODID + ":guide_given";

    /** A chave do id do guia no NBT do livro ({@code GuideItem.TAG_GUIDE_ID} do GuideME 20.1). */
    private static final String GUIDE_ID = "guideId";

    /** O livro, ou vazio sem o GuideME. */
    public static Optional<ItemStack> create() {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation("guideme", "guide"));
        if (item == Items.AIR) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString(GUIDE_ID, WirelessAutomate.id("guide").toString());
        return Optional.of(stack);
    }

    /** O livro já foi entregue a este jogador. */
    public static boolean given(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(GIVEN);
    }

    /** Público: o Forge não chama um {@code @SubscribeEvent} que não seja. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer
                || !Config.SPEC.isLoaded() || !Config.GIVE_GUIDE_ON_FIRST_JOIN.get()) {
            return;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN)) {
            return;
        }
        // Sem o GuideME não marca: se ele for instalado depois, o livro chega no login seguinte.
        create().ifPresent(book -> {
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
            persisted.putBoolean(GIVEN, true);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        });
    }

    private GuideBook() {
    }
}
