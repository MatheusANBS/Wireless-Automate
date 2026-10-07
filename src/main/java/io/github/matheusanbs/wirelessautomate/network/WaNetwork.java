package io.github.matheusanbs.wirelessautomate.network;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Uma rede: tudo que estiver nela troca recursos entre si. Guardada no {@link NetworkSavedData}.
 *
 * @param color    cor RGB (0xRRGGBB), usada nas telas e no LED de rede
 * @param isPublic pública: outros jogadores podem escolhê-la (rede ativa, mover nós para ela);
 *                 privada (o padrão): só o dono e operadores
 */
public record WaNetwork(UUID id, String name, int color, UUID owner, boolean isPublic) {
    /** Rede privada. */
    public WaNetwork(UUID id, String name, int color, UUID owner) {
        this(id, name, color, owner, false);
    }

    public WaNetwork withName(String name) {
        return new WaNetwork(id, name, color, owner, isPublic);
    }

    public WaNetwork withColor(int color) {
        return new WaNetwork(id, name, color & 0xFFFFFF, owner, isPublic);
    }

    public WaNetwork withPublic(boolean isPublic) {
        return new WaNetwork(id, name, color, owner, isPublic);
    }

    /** O jogador pode gerenciar a rede (renomear, cor, privacidade, remover): dono ou operador nível 2. */
    public boolean canManage(ServerPlayer player) {
        return owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    /** O jogador pode pôr nós nesta rede ou usá-la como ativa: quem gerencia ou qualquer um se for pública. */
    public boolean canUse(ServerPlayer player) {
        return isPublic || canManage(player);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putInt("color", color);
        tag.putUUID("owner", owner);
        if (isPublic) {
            tag.putBoolean("public", true);
        }
        return tag;
    }

    public static WaNetwork load(CompoundTag tag) {
        return new WaNetwork(tag.getUUID("id"), tag.getString("name"), tag.getInt("color") & 0xFFFFFF,
                tag.getUUID("owner"), tag.getBoolean("public"));
    }

    /** Nome da rede pintado com a cor dela, para mensagens. */
    public MutableComponent displayName() {
        return Component.literal(name).withStyle(style -> style.withColor(color));
    }
}
