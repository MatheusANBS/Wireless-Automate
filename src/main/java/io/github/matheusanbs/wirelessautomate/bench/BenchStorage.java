package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Que inventário o benchmark usa. Vanilla: barril (27 slots) e baú duplo (54) como inventário
 * grande. Sophisticated Storage: barril de madeira e barril de netherita, se o mod estiver carregado.
 * {@code wa}: o Baú do mod no tier {@link #WA_TIER}, pequeno e grande, com capacidade de sobra.
 *
 * <p>O Baú do mod não tem slots de verdade (um por tipo, mais um vazio), então a cena o enche e o
 * esvazia direto pelo conteúdo ({@link BenchScene}), com o mesmo número de itens dos vanilla:
 * {@link #virtualSlots} pilhas de 64.
 */
public enum BenchStorage {
    VANILLA("vanilla"),
    SOPH("soph"),
    WA("wa");

    public static final String SOPH_MOD = "sophisticatedstorage";
    /** Tier do Baú do mod nos cenários: o Elite guarda 2.097.152 itens. */
    public static final RouterTier WA_TIER = RouterTier.ELITE;

    public final String id;

    BenchStorage(String id) {
        this.id = id;
    }

    public boolean available() {
        return this != SOPH || ModList.get().isLoaded(SOPH_MOD);
    }

    /** O inventário pequeno, como é colocado. */
    public BlockState small() {
        return switch (this) {
            case VANILLA -> Blocks.BARREL.defaultBlockState();
            case SOPH -> soph("barrel").defaultBlockState();
            case WA -> chest();
        };
    }

    /** O inventário grande, como é colocado; no vanilla, a cena monta o baú duplo à parte. */
    public BlockState big() {
        return switch (this) {
            case VANILLA -> Blocks.CHEST.defaultBlockState();
            case SOPH -> soph("netherite_barrel").defaultBlockState();
            case WA -> chest();
        };
    }

    /** O inventário grande ocupa dois blocos (baú duplo). */
    public boolean bigIsDoubleChest() {
        return this == VANILLA;
    }

    /** Pilhas de 64 que a cena põe no Baú do mod, como os slots dos vanilla: 27 no pequeno, 54 no grande. */
    public static int virtualSlots(boolean big) {
        return big ? 54 : 27;
    }

    /** O Baú do mod no {@link #WA_TIER}. */
    public static BlockState chest() {
        return ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, WA_TIER);
    }

    private static Block soph(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(SOPH_MOD, path));
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
