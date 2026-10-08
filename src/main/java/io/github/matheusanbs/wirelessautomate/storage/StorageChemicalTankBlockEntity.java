package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tanque Químico: químicos do Mekanism por tipo ({@link ChemicalStorage}), em mB, pelo id. A
 * capability {@code mekanism:chemical_handler} (em {@code compat/mekanism}) só existe com o
 * Mekanism; sem ele o bloco guarda o que tinha e não troca nada.
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
