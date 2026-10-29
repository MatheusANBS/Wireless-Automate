package io.github.matheusanbs.wirelessautomate.tv;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * A moderação das imagens enviadas, para o comando {@code /wa tela imagem}: achar uma imagem pelo começo do resumo e
 * tirá-la do mundo. Remover tira a imagem das Telas carregadas (a peça fica sem imagem, o mosaico volta ao tema), de
 * todas as bibliotecas e do armazém, que apaga o arquivo. As Telas descarregadas a perdem quando carregarem, porque o
 * armazém não acha mais o resumo ({@link TvBlockEntity#dropMissingPictures}).
 */
public final class TvImageModeration {
    /** O começo do resumo que o comando aceita, no mínimo. */
    public static final int MIN_PREFIX = 6;

    private TvImageModeration() {
    }

    /** O que um remover tirou: quantas Telas carregadas e quantas bibliotecas. */
    public record Removed(String hash, int screens, int libraries) {
    }

    /**
     * O resumo inteiro da imagem cujo resumo começa com {@code prefix} (ao menos {@link #MIN_PREFIX} letras), ou
     * {@code null} se nenhuma ou mais de uma começam assim.
     */
    public static @Nullable String find(TvImageStore store, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        if (lower.length() < MIN_PREFIX) {
            return null;
        }
        if (TvImageHash.valid(lower)) {
            return store.contains(lower) ? lower : null;
        }
        String found = null;
        for (TvImageIndex.Entry entry : store.entries()) {
            if (entry.hash.startsWith(lower)) {
                if (found != null) {
                    return null;
                }
                found = entry.hash;
            }
        }
        return found;
    }

    /** Tira a imagem de tudo e a apaga; {@code null} se o armazém não a tem. */
    public static @Nullable Removed remove(MinecraftServer server, String hash) {
        TvImageStore store = TvImageStore.get(server);
        if (!store.contains(hash)) {
            return null;
        }
        UUID id = TvImageHash.idOf(hash);
        List<TvBlockEntity> screens = new ArrayList<>();
        for (TvBlockEntity tv : TvLoaded.serverScreens()) {
            if (tv.layout().picture(id) != null) {
                screens.add(tv);
            }
        }
        store.remove(hash);
        for (TvBlockEntity tv : screens) {
            tv.dropMissingPictures(store);
        }
        int libraries = TvLibrary.get(server).removePictureEverywhere(hash).size();
        return new Removed(hash, screens.size(), libraries);
    }
}
