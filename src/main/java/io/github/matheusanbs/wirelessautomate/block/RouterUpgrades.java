package io.github.matheusanbs.wirelessautomate.block;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Os dois slots de upgrade de um {@link RouterBlockEntity}: o de chunk loading, com o dono (quem o pôs), e o
 * de alcance, e as chaves deles no NBT ({@code upgrade}, {@code upgrade_owner} e {@code range_upgrade}). Só
 * guarda: as regras e os avisos ficam no roteador.
 */
final class RouterUpgrades {
    /** Upgrade de chunk loading no slot (ou vazio) e quem o pôs. */
    private ItemStack chunk = ItemStack.EMPTY;
    private @Nullable UUID chunkOwner;
    /** Upgrade de Alcance no segundo slot (ou vazio). */
    private ItemStack range = ItemStack.EMPTY;

    ItemStack chunk() {
        return chunk;
    }

    @Nullable UUID chunkOwner() {
        return chunkOwner;
    }

    void setChunk(ItemStack stack, @Nullable UUID owner) {
        chunk = stack;
        chunkOwner = stack.isEmpty() ? null : owner;
    }

    ItemStack range() {
        return range;
    }

    void setRange(ItemStack stack) {
        range = stack;
    }

    void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!chunk.isEmpty()) {
            tag.put("upgrade", chunk.save(registries));
            if (chunkOwner != null) {
                tag.putUUID("upgrade_owner", chunkOwner);
            }
        }
        if (!range.isEmpty()) {
            tag.put("range_upgrade", range.save(registries));
        }
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        chunk = tag.contains("upgrade", Tag.TAG_COMPOUND)
                ? ItemStack.parse(registries, tag.getCompound("upgrade")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        chunkOwner = !chunk.isEmpty() && tag.hasUUID("upgrade_owner") ? tag.getUUID("upgrade_owner") : null;
        range = tag.contains("range_upgrade", Tag.TAG_COMPOUND)
                ? ItemStack.parse(registries, tag.getCompound("range_upgrade")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }
}
