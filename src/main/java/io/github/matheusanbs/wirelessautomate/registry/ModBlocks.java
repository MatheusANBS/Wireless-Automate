package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlock;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorCasingBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorGlassBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
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

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(3.0F, 6.0F)
                .sound(SoundType.STONE).requiresCorrectToolForDrops();
    }

    /**
     * As peças do Ressonador: pedra, e o pistão não as empurra (a peça chegaria ao destino com o estado montado,
     * fora de qualquer estrutura).
     */
    private static BlockBehaviour.Properties resonatorPart() {
        return stone().pushReaction(PushReaction.BLOCK);
    }

    /** O Ressonador de Sinal: a carcaça, o vidro, o Núcleo (com tier) e as três peças das faces. */
    public static final DeferredBlock<ResonatorCasingBlock> RESONATOR_CASING = BLOCKS.registerBlock("resonator_casing",
            ResonatorCasingBlock::new, resonatorPart());
    public static final DeferredBlock<ResonatorGlassBlock> RESONATOR_GLASS = BLOCKS.registerBlock("resonator_glass",
            ResonatorGlassBlock::new, BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.GLASS)
                    .pushReaction(PushReaction.BLOCK).noOcclusion().isValidSpawn(ModBlocks::never)
                    .isRedstoneConductor(ModBlocks::never).isSuffocating(ModBlocks::never)
                    .isViewBlocking(ModBlocks::never));
    public static final DeferredBlock<ResonanceCoreBlock> RESONANCE_CORE = BLOCKS.registerBlock("resonance_core",
            ResonanceCoreBlock::new, resonatorPart());
    public static final DeferredBlock<ResonatorPartBlock> RESONATOR_ENERGY_PORT = BLOCKS.registerBlock(
            "resonator_energy_port", props -> new ResonatorPartBlock(ResonatorPartBlock.Kind.ENERGY_PORT, props),
            resonatorPart());
    public static final DeferredBlock<ResonatorPartBlock> RESONATOR_CRYSTAL_PORT = BLOCKS.registerBlock(
            "resonator_crystal_port", props -> new ResonatorPartBlock(ResonatorPartBlock.Kind.CRYSTAL_PORT, props),
            resonatorPart());
    public static final DeferredBlock<ResonatorPartBlock> RESONATOR_TUNER = BLOCKS.registerBlock("resonator_tuner",
            props -> new ResonatorPartBlock(ResonatorPartBlock.Kind.TUNER, props), resonatorPart());
    public static final DeferredBlock<RepairerBlock> CRYSTAL_REPAIRER = BLOCKS.registerBlock("crystal_repairer",
            RepairerBlock::new, stone().pushReaction(PushReaction.BLOCK).noOcclusion());

    private static boolean never(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    private static Boolean never(BlockState state, BlockGetter level, BlockPos pos, EntityType<?> type) {
        return false;
    }

    private ModBlocks() {
    }
}
