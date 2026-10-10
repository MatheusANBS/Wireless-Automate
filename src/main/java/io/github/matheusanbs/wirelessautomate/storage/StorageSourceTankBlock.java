package io.github.matheusanbs.wirelessautomate.storage;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * O bloco do Tanque de Source: o {@link StorageBlock} com o nível da coluna ({@link #FILL}, de 0 a 10)
 * a forma da jarra vinda do {@link StorageBlock} ({@link StorageShapes}, igual nos 11 níveis). O nível só muda quando o conteúdo muda de nível (ver
 * {@link StorageSourceTankBlockEntity#refreshFill}).
 */
public class StorageSourceTankBlock extends StorageBlock {
    public static final MapCodec<StorageSourceTankBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            StringRepresentable.fromEnum(StorageKind::values).fieldOf("kind").forGetter(StorageBlock::kind),
            propertiesCodec()).apply(instance, StorageSourceTankBlock::new));

    /** Nível do conteúdo: 0 vazio, 10 cheio. */
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 10);

    public StorageSourceTankBlock(StorageKind kind, Properties properties) {
        super(kind, properties);
        registerDefaultState(defaultBlockState().setValue(FILL, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FILL);
    }

    /** O tick agendado no carregamento do block entity: corrige o nível se a capacidade mudou. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof StorageSourceTankBlockEntity tank) {
            tank.refreshFill();
        }
    }
}
