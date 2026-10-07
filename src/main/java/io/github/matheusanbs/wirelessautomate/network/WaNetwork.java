package io.github.matheusanbs.wirelessautomate.network;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Uma rede: tudo que estiver nela troca recursos entre si. Guardada no {@link NetworkSavedData}.
 *
 * @param color cor RGB (0xRRGGBB), usada nas telas e no LED de rede
 */
public record WaNetwork(UUID id, String name, int color, UUID owner) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putInt("color", color);
        tag.putUUID("owner", owner);
        return tag;
    }

    public static WaNetwork load(CompoundTag tag) {
        return new WaNetwork(tag.getUUID("id"), tag.getString("name"), tag.getInt("color") & 0xFFFFFF,
                tag.getUUID("owner"));
    }

    /** Nome da rede pintado com a cor dela, para mensagens. */
    public MutableComponent displayName() {
        return Component.literal(name).withStyle(style -> style.withColor(color));
    }
}
