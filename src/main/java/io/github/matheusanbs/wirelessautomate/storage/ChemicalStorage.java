package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import it.unimi.dsi.fastutil.Hash;
import java.util.Objects;
import java.util.function.LongSupplier;

import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Conteúdo do Tanque Químico: químicos do Mekanism com quantidade {@code long} em mB, pelo id do
 * químico ({@code mekanism:hydrogen}), sem nenhum tipo do Mekanism (os químicos não têm
 * componentes). Só a visão {@code IChemicalHandler}, em {@code compat/mekanism}, fala com a API dele.
 */
public final class ChemicalStorage extends KeyedStorage<ResourceLocation> {
    /** Ids iguais. */
    public static final Hash.Strategy<ResourceLocation> IDS = new Hash.Strategy<>() {
        @Override
        public int hashCode(@Nullable ResourceLocation id) {
            return Objects.hashCode(id);
        }

        @Override
        public boolean equals(@Nullable ResourceLocation a, @Nullable ResourceLocation b) {
            return Objects.equals(a, b);
        }
    };

    public ChemicalStorage(Runnable onChange, LongSupplier capacity) {
        super(IDS, onChange, capacity, "Tanque Químico");
    }

    @Override
    protected boolean isEmptyKey(ResourceLocation key) {
        return key.equals(Chemicals.EMPTY_ID);
    }

    @Override
    protected ResourceLocation normalize(ResourceLocation key) {
        return key;
    }

    @Override
    protected Tag saveKey(ResourceLocation key) {
        return StringTag.valueOf(key.toString());
    }

    /**
     * O id lido. Sem o Mekanism o químico não pode ser conferido, e fica guardado como está (volta a
     * valer quando ele voltar); com ele, um químico que não existe mais é descartado.
     */
    @Override
    protected @Nullable ResourceLocation loadKey(Tag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getAsString());
        if (id == null || Chemicals.LOADED && !Chemicals.exists(id)) {
            return null;
        }
        return id;
    }
}
