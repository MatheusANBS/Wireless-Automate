package io.github.matheusanbs.wirelessautomate.item;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * O livro-guia (GuideME, opcional): o item genérico dele ({@code guideme:guide}) com o componente
 * {@code guideme:guide_id} apontando para o nosso guia. Só pelos registros, sem classes do
 * GuideME; sem ele, não há livro. Vai na aba criativa e é entregue uma vez a cada jogador no
 * primeiro login (config {@code guide.giveOnFirstJoin}).
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID)
public final class GuideBook {
    /** Marca nos dados persistentes do jogador (sobrevivem à morte): o livro já foi entregue. */
    private static final String GIVEN = WirelessAutomate.MODID + ":guide_given";

    /** O livro, ou vazio sem o GuideME. */
    @SuppressWarnings("unchecked")
    public static Optional<ItemStack> create() {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("guideme", "guide"));
        DataComponentType<?> guideId = BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                ResourceLocation.fromNamespaceAndPath("guideme", "guide_id"));
        if (item == Items.AIR || guideId == null) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(item);
        stack.set((DataComponentType<ResourceLocation>) guideId, WirelessAutomate.id("guide"));
        return Optional.of(stack);
    }

    /** O livro já foi entregue a este jogador. */
    public static boolean given(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(GIVEN);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
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
