package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlock;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WirelessAutomate.MODID);

    public static final DeferredBlock<RouterBlock> ROUTER = BLOCKS.registerBlock("router", RouterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion());

    /** Armazenamentos do mod (Baú, Tanque, Bateria, Tanque Químico, Tanque de Source), com tiers. */
    public static final Map<StorageKind, DeferredBlock<StorageBlock>> STORAGE = new EnumMap<>(StorageKind.class);

    static {
        for (StorageKind kind : StorageKind.values()) {
            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    // Os modelos têm elementos e não preenchem o cubo: sem oclusão (só visual).
                    .noOcclusion();
            STORAGE.put(kind, BLOCKS.registerBlock(kind.id,
                    props -> kind == StorageKind.SOURCE_TANK ? new StorageSourceTankBlock(kind, props) : new StorageBlock(kind, props),
                    properties));
        }
    }

    public static final DeferredBlock<StorageBlock> STORAGE_CHEST = STORAGE.get(StorageKind.CHEST);

    /** Minério de Quartzo de Sinal, na pedra e na ardósia: dá Cristais de Sinal (geração em data/.../worldgen). */
    public static final DeferredBlock<DropExperienceBlock> SIGNAL_QUARTZ_ORE = BLOCKS.registerBlock("signal_quartz_ore",
            props -> new DropExperienceBlock(UniformInt.of(2, 5), props),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 3.0F));
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_SIGNAL_QUARTZ_ORE = BLOCKS.registerBlock(
            "deepslate_signal_quartz_ore",
            props -> new DropExperienceBlock(UniformInt.of(2, 5), props),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(4.5F, 3.0F)
                    .sound(SoundType.DEEPSLATE));

    private ModBlocks() {
    }
}
