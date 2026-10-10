package io.github.matheusanbs.wirelessautomate.bench;

import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Que inventário o benchmark usa. Vanilla: barril (27 slots) e baú duplo (54) como inventário
 * grande. Sophisticated Storage: barril de madeira e barril de netherita, se o mod estiver carregado.
 */
public enum BenchStorage {
    VANILLA("vanilla"),
    SOPH("soph");

    public static final String SOPH_MOD = "sophisticatedstorage";

    public final String id;

    BenchStorage(String id) {
        this.id = id;
    }

    public boolean available() {
        return this == VANILLA || ModList.get().isLoaded(SOPH_MOD);
    }

    /** Bloco do inventário pequeno. */
    public Block small() {
        return this == VANILLA ? Blocks.BARREL : soph("barrel");
    }

    /** Bloco do inventário grande; no vanilla é a metade esquerda de um baú duplo. */
    public Block big() {
        return this == VANILLA ? Blocks.CHEST : soph("netherite_barrel");
    }

    /** O inventário grande ocupa dois blocos (baú duplo). */
    public boolean bigIsDoubleChest() {
        return this == VANILLA;
    }

    private static Block soph(String path) {
        return BuiltInRegistries.BLOCK.get(new ResourceLocation(SOPH_MOD, path));
    }

    public static @Nullable BenchStorage byId(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        for (BenchStorage storage : values()) {
            if (storage.id.equals(key)) {
                return storage;
            }
        }
        return null;
    }
}
