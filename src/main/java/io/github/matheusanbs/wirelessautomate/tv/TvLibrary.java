package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.Config;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * A biblioteca de cada jogador: os componentes, os temas, as pixel arts e as imagens enviadas (só o resumo e o nome,
 * os bytes ficam no {@link TvImageStore}, até {@link #MAX_PICTURES}) que ele salvou, pelo UUID. Fica no data
 * storage do overworld e só é lida ao abrir o editor e nas ações; a Tela não depende dela para ser desenhada. Nome
 * repetido substitui um componente ou um tema; a arte é pelo id dela. Os tetos vêm da config
 * ({@code tv.maxComponents}, {@code tv.maxThemes}, {@code tv.maxArts}; cada arte já vem até 128 × 128, uns 8 KB
 * gravada, e o {@link TvLibraryActions} recusa a que faria a biblioteca passar do pacote) e os componentes de um
 * jogador somam no máximo {@link #MAX_COMPONENT_BYTES_PER_PLAYER}: o arquivo fica todo na memória e é regravado inteiro a cada
 * autosave, então sem esse teto dezenas de jogadores com a biblioteca cheia chegariam a dezenas de MB.
 */
public final class TvLibrary extends SavedData {
    public static final String DATA_NAME = "wirelessautomate_tv_library";
    public static final SavedData.Factory<TvLibrary> FACTORY = new SavedData.Factory<>(TvLibrary::new, TvLibrary::load);

    /** Quanto os componentes de um jogador podem ocupar juntos (a estimativa de memória do NBT de cada um). */
    public static final int MAX_COMPONENT_BYTES_PER_PLAYER = 256 * 1024;

    /** Quantas imagens enviadas cada jogador guarda (cada uma é só o resumo e o nome aqui). */
    public static final int MAX_PICTURES = 64;

    /** Uma imagem enviada na biblioteca: o resumo e o nome. */
    public record PictureEntry(String hash, String name) {
    }

    /** O resultado de salvar, renomear ou remover; {@code TOO_MANY_PICTURES} é o componente acima do maxPerScreen. */
    public enum Result { OK, FULL, INVALID, TOO_BIG, MISSING, EXISTS, TOO_MANY_PICTURES }

    /** Os conjuntos de um jogador, em ordem de criação. */
    private static final class Shelf {
        final Map<String, TvComponent> components = new LinkedHashMap<>();
        /** O tamanho estimado de cada componente, pelo nome (a soma é a cota do jogador). */
        final Map<String, Integer> sizes = new HashMap<>();
        int bytes;
        final Map<String, TvTheme> themes = new LinkedHashMap<>();
        final Map<UUID, TvPixelArt> arts = new LinkedHashMap<>();
        /** Resumo → nome, em ordem de chegada. */
        final Map<String, String> pictures = new LinkedHashMap<>();

        boolean isEmpty() {
            return components.isEmpty() && themes.isEmpty() && arts.isEmpty() && pictures.isEmpty();
        }
    }

    private final Map<UUID, Shelf> shelves = new LinkedHashMap<>();

    public static TvLibrary get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** A estimativa de memória do componente: o que conta na cota do jogador. */
    private static int sizeOf(TvComponent component) {
        return sizeOf(TvComponent.CODEC.encodeStart(NbtOps.INSTANCE, component).getOrThrow());
    }

    private static int sizeOf(Tag tag) {
        return (int) Math.min(tag.sizeInBytes(), Integer.MAX_VALUE);
    }

    /** Guarda o componente já medido, mantendo a soma em dia. */
    private static void putComponent(Shelf shelf, String name, TvComponent component, int size) {
        Integer old = shelf.sizes.put(name, size);
        shelf.bytes += size - (old == null ? 0 : old);
        shelf.components.put(name, component);
    }

    private static void removeComponent(Shelf shelf, String name) {
        Integer old = shelf.sizes.remove(name);
        shelf.bytes -= old == null ? 0 : old;
        shelf.components.remove(name);
    }

    public List<TvComponent> components(UUID player) {
        Shelf shelf = shelves.get(player);
        return shelf == null ? List.of() : List.copyOf(shelf.components.values());
    }

    public List<TvTheme> themes(UUID player) {
        Shelf shelf = shelves.get(player);
        return shelf == null ? List.of() : List.copyOf(shelf.themes.values());
    }

    public List<TvPixelArt> arts(UUID player) {
        Shelf shelf = shelves.get(player);
        return shelf == null ? List.of() : List.copyOf(shelf.arts.values());
    }

    public List<PictureEntry> pictures(UUID player) {
        Shelf shelf = shelves.get(player);
        if (shelf == null || shelf.pictures.isEmpty()) {
            return List.of();
        }
        List<PictureEntry> out = new ArrayList<>(shelf.pictures.size());
        for (Map.Entry<String, String> entry : shelf.pictures.entrySet()) {
            out.add(new PictureEntry(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    /**
     * Os resumos das imagens que a biblioteca do jogador segura: as da aba Imagens, na ordem de chegada, e as dos
     * componentes e layouts salvos (o armazém conta todas como uso).
     */
    public List<String> pictureHashes(UUID player) {
        Shelf shelf = shelves.get(player);
        return shelf == null ? List.of() : held(shelf);
    }

    private static List<String> held(Shelf shelf) {
        List<String> out = new ArrayList<>(shelf.pictures.keySet());
        for (TvComponent component : shelf.components.values()) {
            for (TvPicture picture : component.pictures()) {
                if (!out.contains(picture.hash())) {
                    out.add(picture.hash());
                }
            }
        }
        return out;
    }

    public boolean hasPicture(UUID player, String hash) {
        Shelf shelf = shelves.get(player);
        return shelf != null && shelf.pictures.containsKey(hash);
    }

    /**
     * Guarda a imagem enviada pelo resumo; a que já está fica com o nome dela ({@code EXISTS}, nada muda). Recusa
     * resumo inválido ou biblioteca cheia. O nome vazio vira "Imagem"; acima de {@link TvLibraryPicture#MAX_NAME}
     * letras, é cortado.
     */
    public Result addPicture(UUID player, String hash, String name) {
        if (!TvImageHash.valid(hash)) {
            return Result.INVALID;
        }
        Shelf shelf = shelves.computeIfAbsent(player, id -> new Shelf());
        if (shelf.pictures.containsKey(hash)) {
            return Result.EXISTS;
        }
        if (shelf.pictures.size() >= MAX_PICTURES) {
            drop(player, shelf);
            return Result.FULL;
        }
        shelf.pictures.put(hash, pictureName(name));
        setDirty();
        return Result.OK;
    }

    /** O nome guardado: sem espaços nas pontas, até 32 letras, "Imagem" se vazio. */
    static String pictureName(String name) {
        String cut = TvText.cut(name.strip(), TvLibraryPicture.MAX_NAME);
        return cut.isEmpty() ? "Imagem" : cut;
    }

    /** Renomeia uma imagem; recusa nome vazio. */
    public Result renamePicture(UUID player, String hash, String to) {
        Shelf shelf = shelves.get(player);
        if (shelf == null || !shelf.pictures.containsKey(hash)) {
            return Result.MISSING;
        }
        if (to.strip().isEmpty()) {
            return Result.INVALID;
        }
        shelf.pictures.put(hash, pictureName(to));
        setDirty();
        return Result.OK;
    }

    /** Tira a imagem da biblioteca (a imagem só sai do mundo quando nada mais a usa). */
    public Result removePicture(UUID player, String hash) {
        Shelf shelf = shelves.get(player);
        if (shelf == null || shelf.pictures.remove(hash) == null) {
            return Result.MISSING;
        }
        drop(player, shelf);
        setDirty();
        return Result.OK;
    }

    /**
     * Tira a imagem de todas as bibliotecas (o comando de remover), da aba Imagens e das imagens dos componentes e
     * layouts salvos (as peças que a apontavam ficam sem imagem); devolve de quem saiu.
     */
    public List<UUID> removePictureEverywhere(String hash) {
        List<UUID> affected = new ArrayList<>();
        for (Map.Entry<UUID, Shelf> entry : new ArrayList<>(shelves.entrySet())) {
            Shelf shelf = entry.getValue();
            boolean removed = shelf.pictures.remove(hash) != null;
            for (TvComponent component : new ArrayList<>(shelf.components.values())) {
                List<TvPicture> kept = without(component.pictures(), hash);
                if (kept != null) {
                    TvComponent stored = component.withPictures(kept);
                    putComponent(shelf, stored.name(), stored, sizeOf(stored));
                    removed = true;
                }
            }
            if (removed) {
                affected.add(entry.getKey());
                drop(entry.getKey(), shelf);
            }
        }
        if (!affected.isEmpty()) {
            setDirty();
        }
        return affected;
    }

    /** As imagens sem a do resumo, ou {@code null} se ela não estava. */
    private static @Nullable List<TvPicture> without(List<TvPicture> pictures, String hash) {
        for (int i = 0; i < pictures.size(); i++) {
            if (pictures.get(i).hash().equals(hash)) {
                List<TvPicture> kept = new ArrayList<>(pictures);
                kept.remove(i);
                return kept;
            }
        }
        return null;
    }

    /**
     * Os resumos das imagens de cada jogador que segura alguma (na aba Imagens, num componente ou num layout salvo): o
     * armazém conta o uso das bibliotecas por aqui.
     */
    public Map<UUID, List<String>> pictureRefs() {
        Map<UUID, List<String>> out = new HashMap<>();
        for (Map.Entry<UUID, Shelf> entry : shelves.entrySet()) {
            List<String> held = held(entry.getValue());
            if (!held.isEmpty()) {
                out.put(entry.getKey(), held);
            }
        }
        return out;
    }

    /** Guarda a arte pelo id (troca a de mesmo id, no mesmo lugar). Recusa nome vazio ou biblioteca cheia. */
    public Result saveArt(UUID player, TvPixelArt art) {
        String name = art.name().strip();
        if (name.isEmpty()) {
            return Result.INVALID;
        }
        Shelf shelf = shelves.computeIfAbsent(player, id -> new Shelf());
        if (!shelf.arts.containsKey(art.id()) && shelf.arts.size() >= Config.tvMaxArts()) {
            drop(player, shelf);
            return Result.FULL;
        }
        shelf.arts.put(art.id(), name.equals(art.name()) ? art : art.withName(name));
        setDirty();
        return Result.OK;
    }

    /** Renomeia uma arte; recusa nome vazio. Nomes repetidos valem: a arte é pelo id. */
    public Result renameArt(UUID player, UUID id, String to) {
        Shelf shelf = shelves.get(player);
        if (shelf == null || !shelf.arts.containsKey(id)) {
            return Result.MISSING;
        }
        String name = to.strip();
        if (name.isEmpty()) {
            return Result.INVALID;
        }
        shelf.arts.put(id, shelf.arts.get(id).withName(name));
        setDirty();
        return Result.OK;
    }

    public Result removeArt(UUID player, UUID id) {
        Shelf shelf = shelves.get(player);
        if (shelf == null || shelf.arts.remove(id) == null) {
            return Result.MISSING;
        }
        drop(player, shelf);
        setDirty();
        return Result.OK;
    }

    /** Guarda o componente pelo nome (substitui o de mesmo nome). Recusa nome vazio, peças demais ou biblioteca cheia. */
    public Result saveComponent(UUID player, TvComponent component) {
        String name = component.name().strip();
        if (name.isEmpty() || component.pieces().isEmpty() || component.pieces().size() > Config.tvMaxElements()) {
            return Result.INVALID;
        }
        Shelf shelf = shelves.computeIfAbsent(player, id -> new Shelf());
        if (!shelf.components.containsKey(name) && shelf.components.size() >= Config.tvMaxComponents()) {
            drop(player, shelf);
            return Result.FULL;
        }
        TvComponent stored = new TvComponent(name, component.pieces(), component.layout(), component.pictures());
        int size = sizeOf(stored);
        int others = shelf.bytes - shelf.sizes.getOrDefault(name, 0);
        if (others + size > MAX_COMPONENT_BYTES_PER_PLAYER) {
            drop(player, shelf);
            return Result.FULL;
        }
        putComponent(shelf, name, stored, size);
        setDirty();
        return Result.OK;
    }

    /** Guarda o tema pelo nome (substitui o de mesmo nome). */
    public Result saveTheme(UUID player, TvTheme theme) {
        String name = theme.name().strip();
        if (name.isEmpty()) {
            return Result.INVALID;
        }
        Shelf shelf = shelves.computeIfAbsent(player, id -> new Shelf());
        if (!shelf.themes.containsKey(name) && shelf.themes.size() >= Config.tvMaxThemes()) {
            drop(player, shelf);
            return Result.FULL;
        }
        shelf.themes.put(name, new TvTheme(name, theme.bg(), theme.card(), theme.text(), theme.muted(), theme.high(),
                theme.low()));
        setDirty();
        return Result.OK;
    }

    /** Renomeia um componente; recusa nome vazio ou já usado por outro. */
    public Result renameComponent(UUID player, String from, String to) {
        Shelf shelf = shelves.get(player);
        String name = TvText.cut(to.strip(), TvComponent.MAX_NAME);
        if (shelf == null || !shelf.components.containsKey(from)) {
            return Result.MISSING;
        }
        if (name.isEmpty()) {
            return Result.INVALID;
        }
        if (!name.equals(from) && shelf.components.containsKey(name)) {
            return Result.EXISTS;
        }
        TvComponent old = shelf.components.get(from);
        rebuild(shelf.components, from, name, new TvComponent(name, old.pieces(), old.layout()));
        rebuild(shelf.sizes, from, name, shelf.sizes.getOrDefault(from, 0));
        setDirty();
        return Result.OK;
    }

    /** Renomeia um tema; recusa nome vazio ou já usado por outro. */
    public Result renameTheme(UUID player, String from, String to) {
        Shelf shelf = shelves.get(player);
        String name = TvText.cut(to.strip(), TvTheme.MAX_NAME);
        if (shelf == null || !shelf.themes.containsKey(from)) {
            return Result.MISSING;
        }
        if (name.isEmpty()) {
            return Result.INVALID;
        }
        if (!name.equals(from) && shelf.themes.containsKey(name)) {
            return Result.EXISTS;
        }
        TvTheme old = shelf.themes.get(from);
        rebuild(shelf.themes, from, name, new TvTheme(name, old.bg(), old.card(), old.text(), old.muted(), old.high(),
                old.low()));
        setDirty();
        return Result.OK;
    }

    public Result removeComponent(UUID player, String name) {
        Shelf shelf = shelves.get(player);
        if (shelf == null || !shelf.components.containsKey(name)) {
            return Result.MISSING;
        }
        removeComponent(shelf, name);
        drop(player, shelf);
        setDirty();
        return Result.OK;
    }

    public Result removeTheme(UUID player, String name) {
        Shelf shelf = shelves.get(player);
        if (shelf == null || shelf.themes.remove(name) == null) {
            return Result.MISSING;
        }
        drop(player, shelf);
        setDirty();
        return Result.OK;
    }

    /** Solta a prateleira vazia, para o arquivo não guardar jogadores sem nada. */
    private void drop(UUID player, Shelf shelf) {
        if (shelf.isEmpty()) {
            shelves.remove(player);
        }
    }

    /** Troca a chave mantendo a posição na ordem de criação. */
    private static <V> void rebuild(Map<String, V> map, String from, String to, V value) {
        Map<String, V> copy = new LinkedHashMap<>(map);
        map.clear();
        for (Map.Entry<String, V> entry : copy.entrySet()) {
            if (entry.getKey().equals(from)) {
                map.put(to, value);
            } else {
                map.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Shelf> entry : shelves.entrySet()) {
            CompoundTag player = new CompoundTag();
            player.putUUID("player", entry.getKey());
            ListTag components = new ListTag();
            for (TvComponent component : entry.getValue().components.values()) {
                components.add(TvComponent.CODEC.encodeStart(NbtOps.INSTANCE, component).getOrThrow());
            }
            ListTag themes = new ListTag();
            for (TvTheme theme : entry.getValue().themes.values()) {
                themes.add(TvCodecs.THEME.encodeStart(NbtOps.INSTANCE, theme).getOrThrow());
            }
            player.put("components", components);
            player.put("themes", themes);
            if (!entry.getValue().arts.isEmpty()) {
                ListTag arts = new ListTag();
                for (TvPixelArt art : entry.getValue().arts.values()) {
                    arts.add(TvPixelArt.CODEC.encodeStart(NbtOps.INSTANCE, art).getOrThrow());
                }
                player.put("arts", arts);
            }
            if (!entry.getValue().pictures.isEmpty()) {
                ListTag pictures = new ListTag();
                for (Map.Entry<String, String> picture : entry.getValue().pictures.entrySet()) {
                    CompoundTag item = new CompoundTag();
                    item.putString("hash", picture.getKey());
                    item.putString("name", picture.getValue());
                    pictures.add(item);
                }
                player.put("pictures", pictures);
            }
            players.add(player);
        }
        tag.put("players", players);
        return tag;
    }

    /** Lê cada item à parte: um que não decodifica é largado, sem derrubar os outros. */
    public static TvLibrary load(CompoundTag tag, HolderLookup.Provider registries) {
        TvLibrary library = new TvLibrary();
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag player = players.getCompound(i);
            if (!player.hasUUID("player")) {
                continue;
            }
            Shelf shelf = new Shelf();
            ListTag components = player.getList("components", Tag.TAG_COMPOUND);
            for (int j = 0; j < components.size(); j++) {
                Tag item = components.get(j);
                TvComponent.CODEC.parse(NbtOps.INSTANCE, item).result()
                        .ifPresent(component -> putComponent(shelf, component.name(), component, sizeOf(item)));
            }
            ListTag themes = player.getList("themes", Tag.TAG_COMPOUND);
            for (int j = 0; j < themes.size(); j++) {
                TvCodecs.THEME.parse(NbtOps.INSTANCE, themes.get(j)).result()
                        .ifPresent(theme -> shelf.themes.put(theme.name(), theme));
            }
            ListTag arts = player.getList("arts", Tag.TAG_COMPOUND);
            for (int j = 0; j < arts.size(); j++) {
                TvPixelArt.CODEC.parse(NbtOps.INSTANCE, arts.get(j)).result()
                        .ifPresent(art -> shelf.arts.putIfAbsent(art.id(), art));
            }
            ListTag pictures = player.getList("pictures", Tag.TAG_COMPOUND);
            for (int j = 0; j < pictures.size() && shelf.pictures.size() < MAX_PICTURES; j++) {
                CompoundTag item = pictures.getCompound(j);
                String hash = item.getString("hash");
                if (TvImageHash.valid(hash)) {
                    shelf.pictures.putIfAbsent(hash, pictureName(item.getString("name")));
                }
            }
            if (!shelf.isEmpty()) {
                library.shelves.put(player.getUUID("player"), shelf);
            }
        }
        return library;
    }
}
