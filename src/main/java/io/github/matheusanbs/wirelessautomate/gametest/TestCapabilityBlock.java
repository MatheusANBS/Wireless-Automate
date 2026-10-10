package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Porte 1.20.1 (D3): bloco de teste que expõe capabilities por um block entity mínimo. No {@code main}, as
 * máquinas de teste (GameTests, químicos e benchmark) eram blocos vanilla com a capability presa pelo
 * {@code RegisterCapabilitiesEvent.registerBlock} do NeoForge, sem block entity; no Forge 1.20.1 a capability de
 * bloco só vem de um block entity. Cada bloco copia as propriedades do bloco vanilla que fazia o papel no
 * {@code main} e pergunta ao seu {@link Provider} o que expor, pela posição (o estado continua guardado por
 * posição, como no {@code main}).
 *
 * <p>Só é registrado quando a propriedade de sistema de quem o usa está ligada (veja {@link Group}): sem ela, nada
 * entra no registro.
 */
public final class TestCapabilityBlock extends Block implements EntityBlock {

    /** O que o bloco da posição expõe para a capability e o lado, ou {@code null} se não expõe. */
    @FunctionalInterface
    public interface Provider {
        @Nullable Object get(Capability<?> capability, BlockPos pos, @Nullable Direction side);
    }

    private final Provider provider;
    private final Group owner;

    private TestCapabilityBlock(BlockBehaviour.Properties properties, Provider provider, Group owner) {
        super(properties);
        this.provider = provider;
        this.owner = owner;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(owner.type(), pos, state);
    }

    /**
     * Um conjunto de blocos de teste com o seu tipo de block entity, registrados no {@link RegisterEvent} de quem
     * os usa (só quando a propriedade de sistema dela está ligada).
     */
    public static final class Group {
        private final String prefix;
        private final Map<String, Spec> specs = new LinkedHashMap<>();
        private final Map<String, TestCapabilityBlock> blocks = new HashMap<>();
        private @Nullable BlockEntityType<Entity> type;

        private record Spec(Block copyOf, Provider provider) {
        }

        /** {@code prefix} vira o começo do id de cada bloco ({@code wirelessautomate:<prefix>_<nome>}) e o id do tipo. */
        public Group(String prefix) {
            this.prefix = prefix;
        }

        /** Declara um bloco com as propriedades de {@code copyOf}. Chame antes do registro. */
        public Group add(String name, Block copyOf, Provider provider) {
            specs.put(name, new Spec(copyOf, provider));
            return this;
        }

        /** Registra os blocos e o tipo de block entity (chame do {@code @SubscribeEvent} do {@link RegisterEvent}). */
        public void register(RegisterEvent event) {
            event.register(Registries.BLOCK, helper -> specs.forEach((name, spec) -> {
                TestCapabilityBlock block = new TestCapabilityBlock(BlockBehaviour.Properties.copy(spec.copyOf()),
                        spec.provider(), this);
                blocks.put(name, block);
                helper.register(WirelessAutomate.id(prefix + "_" + name), block);
            }));
            event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
                List<Block> valid = new ArrayList<>(blocks.values());
                type = BlockEntityType.Builder.<Entity>of((pos, state) -> new Entity(type(), pos, state),
                        valid.toArray(Block[]::new)).build(null);
                helper.register(WirelessAutomate.id(prefix), type);
            });
        }

        /** O bloco registrado com esse nome (só depois do registro). */
        public Block block(String name) {
            return Objects.requireNonNull(blocks.get(name), () -> "bloco de teste não registrado: " + prefix + "_" + name);
        }

        BlockEntityType<Entity> type() {
            return Objects.requireNonNull(type, "tipo de block entity de teste não registrado: " + prefix);
        }
    }

    /** O block entity mínimo: expõe o que o {@link Provider} do bloco der, num {@link LazyOptional} por pedido. */
    public static final class Entity extends BlockEntity {
        private record Key(Capability<?> capability, @Nullable Direction side) {
        }

        private record Exposed(Object value, LazyOptional<?> optional) {
        }

        private final Map<Key, Exposed> exposed = new HashMap<>();

        Entity(BlockEntityType<Entity> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            if (!remove && getBlockState().getBlock() instanceof TestCapabilityBlock block) {
                Object value = block.provider.get(capability, worldPosition, side);
                if (value != null) {
                    Key key = new Key(capability, side);
                    Exposed cached = exposed.get(key);
                    // O estado por posição pode ter sido trocado (o teste chamou reset): o objeto velho sai.
                    if (cached == null || cached.value() != value) {
                        if (cached != null) {
                            cached.optional().invalidate();
                        }
                        cached = new Exposed(value, LazyOptional.of(() -> value));
                        exposed.put(key, cached);
                    }
                    return cached.optional().cast();
                }
            }
            return super.getCapability(capability, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            exposed.values().forEach(cached -> cached.optional().invalidate());
            exposed.clear();
        }
    }
}
