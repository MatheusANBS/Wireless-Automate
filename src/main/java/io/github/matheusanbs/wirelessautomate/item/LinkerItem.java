package io.github.matheusanbs.wirelessautomate.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.jetbrains.annotations.Nullable;

/**
 * Vinculador (controle): escolhe a rede ativa e coloca roteadores nela. Por enquanto só o modo
 * Único: clique num roteador o põe na rede ativa (criando uma se o jogador não tiver), e clique no
 * ar mostra qual é a rede ativa. Modo Área e tela ficam para depois (ver roadmap).
 *
 * <p>O seletor de tipo ({@link ModDataComponents#LINKER_TYPE}, trocado com Shift + roda do mouse)
 * escolhe o que o clique vincula: sem tipo (Todos) põe todas as abas do roteador na rede; com um
 * tipo, só a aba daquele tipo.
 */
public class LinkerItem extends Item {
    private static final String KEY = "item.wirelessautomate.linker.";
    /** Ordem do seletor; {@code null} é Todos. Químicos ficam de fora até o Mekanism entrar. */
    private static final ResourceType[] CYCLE = {null, ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY};

    /** Codec do componente: o nome do tipo em minúsculas, só os tipos do seletor. */
    public static final Codec<ResourceType> TYPE_CODEC = Codec.STRING.comapFlatMap(name -> {
        for (ResourceType type : CYCLE) {
            if (type != null && key(type).equals(name)) {
                return DataResult.success(type);
            }
        }
        return DataResult.error(() -> "tipo de Vinculador desconhecido: " + name);
    }, LinkerItem::key);
    public static final StreamCodec<RegistryFriendlyByteBuf, ResourceType> TYPE_STREAM_CODEC =
            NeoForgeStreamCodecs.enumCodec(ResourceType.class);

    public LinkerItem(Properties properties) {
        super(properties);
    }

    /** Tipo que o Vinculador vincula; {@code null} = todos. */
    public static @Nullable ResourceType type(ItemStack stack) {
        return stack.get(ModDataComponents.LINKER_TYPE.get());
    }

    /**
     * Avança ({@code direction > 0}) ou volta o seletor: Todos → Itens → Fluidos → Energia → Todos.
     * Devolve o tipo novo ({@code null} = todos).
     */
    public static @Nullable ResourceType cycleType(ItemStack stack, int direction) {
        ResourceType current = type(stack);
        int index = 0;
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == current) {
                index = i;
                break;
            }
        }
        ResourceType next = CYCLE[Math.floorMod(index + Integer.signum(direction), CYCLE.length)];
        if (next == null) {
            stack.remove(ModDataComponents.LINKER_TYPE.get());
        } else {
            stack.set(ModDataComponents.LINKER_TYPE.get(), next);
        }
        return next;
    }

    /** Nome do tipo do seletor ("Todos" para {@code null}). */
    public static Component typeName(@Nullable ResourceType type) {
        return type == null
                ? Component.translatable(KEY + "type.all")
                : Component.translatable("gui.wirelessautomate.router.type." + key(type));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof RouterBlockEntity router)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            WaNetwork network = NetworkSavedData.get(player.server).activeOrCreate(player);
            ResourceType type = type(context.getItemInHand());
            if (inNetwork(router, type, network.id())) {
                player.displayClientMessage(type == null
                        ? Component.translatable(KEY + "already", network.displayName())
                        : Component.translatable(KEY + "already_type", typeName(type), network.displayName()), true);
            } else {
                if (type == null) {
                    router.setNetworkId(network.id());
                } else {
                    router.setNetworkId(type, network.id());
                }
                player.displayClientMessage(type == null
                        ? Component.translatable(KEY + "linked", network.displayName())
                        : Component.translatable(KEY + "linked_type", typeName(type), network.displayName()), true);
                level.playSound(null, context.getClickedPos(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.BLOCKS, 0.5F, 1.4F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** O tipo (ou, com {@code null}, todos os tipos) já está na rede. */
    private static boolean inNetwork(RouterBlockEntity router, @Nullable ResourceType type, UUID network) {
        if (type != null) {
            return network.equals(router.networkId(type));
        }
        for (ResourceType each : ResourceType.values()) {
            if (!network.equals(router.networkId(each))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkSavedData data = NetworkSavedData.get(serverPlayer.server);
            UUID active = data.activeNetwork(serverPlayer.getUUID());
            WaNetwork network = active == null ? null : data.network(active);
            serverPlayer.displayClientMessage(network == null
                    ? Component.translatable(KEY + "no_active")
                    : Component.translatable(KEY + "active", network.displayName()), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(KEY + "tooltip.type", typeName(type(stack))).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(KEY + "tooltip.cycle").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String key(ResourceType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
