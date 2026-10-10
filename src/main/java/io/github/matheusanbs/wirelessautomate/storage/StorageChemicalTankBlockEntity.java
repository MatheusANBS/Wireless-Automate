package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

/**
 * Tanque Químico: químicos do Mekanism por tipo ({@link ChemicalStorage}), em mB, pelo id. As
 * capabilities de químico (em {@code compat/mekanism}) só existem com o Mekanism; sem ele o bloco
 * guarda o que tinha e não troca nada. Porte 1.20.1 (Mekanism 10.4, D4): guarda os quatro tipos (gás,
 * infusão, pigmento, slurry) e expõe as quatro capabilities, cada uma vendo só os químicos do seu tipo.
 */
public class StorageChemicalTankBlockEntity extends KeyedStorageBlockEntity<ResourceLocation> {
    private final ChemicalStorage storage = new ChemicalStorage(this::setChanged, this::capacity);

    public StorageChemicalTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHEMICAL_TANK.get(), StorageKind.CHEMICAL_TANK, pos, state);
        storage.setAdmission(this::admit);
    }

    public ChemicalStorage storage() {
        return storage;
    }

    @Override
    public KeyedStorage<ResourceLocation> keyed() {
        return storage;
    }

    /** As de químico do Mekanism, uma por subtipo, pela ponte {@link Chemicals} (nada sem ele). */
    @Override
    protected @Nullable Object exposed(Capability<?> capability) {
        return Chemicals.storageHandler(capability, storage);
    }

    @Override
    protected String contentsKey() {
        return "chemicals";
    }

    /** O filtro de entrada: nada se recusa; com estoque, só até o Tanque ter N mB do químico. */
    private long admit(ResourceLocation id, long amount) {
        FilterSet set = filter().asSet();
        if (set.isEmpty()) {
            return amount;
        }
        if (!set.testChemical(id)) {
            return 0;
        }
        Filter rule = set.chemicalStockFilter(id);
        long stock = rule == null ? 0 : rule.chemicalStock(id);
        if (stock > 0) {
            return StockLimit.acceptable(storage.count(id), stock, amount);
        }
        return amount;
    }
}
