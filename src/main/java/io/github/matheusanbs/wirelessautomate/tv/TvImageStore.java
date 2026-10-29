package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * O armazém das imagens enviadas de um mundo: os arquivos como vieram em {@code data/wirelessautomate/images/
 * <resumo>.<ext>} (uma cópia por resumo; o servidor nunca os decodifica) e o índice ({@link TvImageIndex}) neste
 * {@code SavedData} do overworld, com o cabeçalho, o tamanho, o autor, a data e as Telas que usam cada imagem, mais a
 * lista de banidos do envio.
 * <p>
 * <b>O uso sem varrer o mundo:</b> cada Tela que aponta imagens é anotada pela posição quando o layout dela muda
 * ({@link #screenChanged}), quando ela carrega (o {@code onLoad} anota de novo, o que conserta uma anotação perdida) e
 * sai quando o bloco é quebrado ({@link #screenRemoved}, no {@code onRemove} do {@link TvBlock}); a anotação vai ao
 * disco com o índice. As bibliotecas são recontadas da própria {@link TvLibrary} a cada carga do mundo
 * ({@link #attach}) e anotadas a cada mudança. Um índice que não lê (apagado ou corrompido) com arquivos na pasta é
 * refeito deles pelo cabeçalho ({@link #recover}); essas imagens não saem pela limpeza até alguém voltar a usá-las,
 * porque as Telas descarregadas que as apontam só se anotam quando carregam.
 * <p>
 * <b>A limpeza</b> passa a cada {@link #CLEANUP_MS} e apaga, no máximo {@link #CLEANUP_BATCH} por tick, as imagens
 * sem uso há mais de {@link #GRACE_MS} (folga para quebrar e recolocar uma Tela, ou desfazer). Parado, o tick é a
 * leitura do relógio. Gravar, ler e apagar arquivos vai num executor de entrada e saída, nunca na thread do servidor;
 * os bytes lidos para a entrega ficam num cache de até {@link #CACHE_BYTES}.
 */
public final class TvImageStore extends SavedData {
    public static final String DATA_NAME = "wirelessautomate_tv_images";
    public static final SavedData.Factory<TvImageStore> FACTORY = new SavedData.Factory<>(() -> new TvImageStore(false),
            (tag, registries) -> load(tag));
    public static final long GRACE_MS = 10 * 60_000L;
    static final long CLEANUP_MS = 5 * 60_000L;
    private static final int CLEANUP_BATCH = 8;
    private static final long CACHE_BYTES = 64L << 20;

    /** Nos testes: o servidor de GameTest é tratado como dedicado (o teto do mundo vale). */
    public static boolean dedicatedForTests;
    private static LongSupplier clock = System::currentTimeMillis;
    /** O armazém do mundo aberto, para o tick da limpeza não precisar buscá-lo; solto na parada. */
    private static @Nullable TvImageStore active;

    private final TvImageIndex<GlobalPos> index = new TvImageIndex<>();
    private final Set<UUID> banned = new LinkedHashSet<>();
    private final boolean fromDisk;
    private @Nullable MinecraftServer server;
    private @Nullable Path dir;
    private long nextCleanup;
    private final ArrayDeque<String> doomed = new ArrayDeque<>();
    /** Os bytes lidos para a entrega, do menos ao mais usado. */
    private final LinkedHashMap<String, byte[]> cache = new LinkedHashMap<>(16, 0.75f, true);
    private long cacheBytes;
    private final Set<String> loading = new HashSet<>();
    /** As que não leram do disco (o arquivo sumiu ou quebrou): a entrega desiste delas. */
    private final Set<String> unreadable = new HashSet<>();

    private TvImageStore(boolean fromDisk) {
        this.fromDisk = fromDisk;
    }

    /** O armazém do mundo do servidor, ligado a ele na primeira vez (a pasta, a recuperação e as bibliotecas). */
    public static TvImageStore get(MinecraftServer server) {
        TvImageStore store = server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        if (store.server != server) {
            store.attach(server);
        }
        active = store;
        return store;
    }

    private static long now() {
        return clock.getAsLong();
    }

    private void attach(MinecraftServer server) {
        this.server = server;
        dir = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(WirelessAutomate.MODID)
                .resolve("images");
        if (!fromDisk) {
            recover();
        }
        long now = now();
        for (Map.Entry<UUID, List<String>> entry : TvLibrary.get(server).pictureRefs().entrySet()) {
            index.setLibrary(entry.getKey(), entry.getValue(), now);
        }
        nextCleanup = now + CLEANUP_MS;
    }

    /**
     * Sem índice no disco (mundo novo, ou o arquivo apagado ou corrompido): as imagens da pasta voltam ao índice pelo
     * cabeçalho, como recuperadas. Raro, e só na carga do mundo.
     */
    private void recover() {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        int found = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                int dot = name.lastIndexOf('.');
                TvImageFormat format = dot < 0 ? null : TvImageFormat.byExt(name.substring(dot + 1));
                String hash = dot < 0 ? "" : name.substring(0, dot);
                if (format == null || !TvImageHash.valid(hash)) {
                    continue;
                }
                byte[] data = Files.readAllBytes(file);
                TvImageHeader.Result header = TvImageHeader.read(data, TvPicture.HARD_SIZE, TvPicture.HARD_FRAMES);
                if (!header.ok()) {
                    continue;
                }
                TvImageHeader.Info info = header.info();
                index.add(new TvImageIndex.Entry(hash, info.format(), info.width(), info.height(), info.frames(),
                        data.length, null, Files.getLastModifiedTime(file).toMillis()), true, now());
                found++;
            }
        } catch (IOException e) {
            WirelessAutomate.LOGGER.warn("Tela de Sinal: não deu para ler a pasta de imagens {}", dir, e);
        }
        if (found > 0) {
            WirelessAutomate.LOGGER.warn("Tela de Sinal: o índice das imagens não leu; {} imagens recuperadas da pasta "
                    + "(não saem pela limpeza até voltarem a ser usadas)", found);
            setDirty();
        }
    }

    public boolean contains(String hash) {
        return index.contains(hash);
    }

    public @Nullable TvImageIndex.Entry entry(String hash) {
        return index.get(hash);
    }

    public Collection<TvImageIndex.Entry> entries() {
        return index.entries();
    }

    public long totalBytes() {
        return index.totalBytes();
    }

    public long bytesOf(UUID author) {
        return index.bytesOf(author);
    }

    /** A imagem como o layout a aponta, pelo que o armazém sabe dela; {@code null} se não existe. */
    public @Nullable TvPicture picture(String hash) {
        TvImageIndex.Entry entry = index.get(hash);
        return entry == null ? null : new TvPicture(entry.hash, entry.format, entry.width, entry.height, entry.frames,
                Optional.ofNullable(entry.author));
    }

    /**
     * As imagens do layout trocadas pelo que o armazém sabe delas (o cliente não decide tamanho nem autor); as que o
     * armazém não tem saem. Devolve o próprio layout quando nada muda.
     */
    public TvLayout resolve(TvLayout layout) {
        List<TvPicture> pictures = layout.pictures();
        if (pictures.isEmpty()) {
            return layout;
        }
        List<TvPicture> resolved = resolve(pictures);
        return resolved.equals(pictures) ? layout : layout.withPictures(resolved);
    }

    /**
     * As imagens trocadas pelo que o armazém sabe delas (o cliente não decide tamanho nem autor); as que o armazém não
     * tem saem. Para os componentes e layouts salvos da biblioteca.
     */
    public List<TvPicture> resolve(List<TvPicture> pictures) {
        if (pictures.isEmpty()) {
            return pictures;
        }
        List<TvPicture> resolved = new ArrayList<>(pictures.size());
        for (TvPicture picture : pictures) {
            TvPicture stored = picture(picture.hash());
            if (stored != null) {
                resolved.add(stored);
            }
        }
        return resolved;
    }

    /** Se o armazém tem todas as imagens do layout. */
    public boolean hasAll(TvLayout layout) {
        for (TvPicture picture : layout.pictures()) {
            if (!index.contains(picture.hash())) {
                return false;
            }
        }
        return true;
    }

    // As anotações de uso.

    private static GlobalPos key(Level level, BlockPos pos) {
        return GlobalPos.of(level.dimension(), pos.immutable());
    }

    /** O layout da Tela mudou, ou ela carregou: anota as imagens que ela usa agora. */
    public void screenChanged(TvBlockEntity tv) {
        if (tv.getLevel() != null && index.setScreen(key(tv.getLevel(), tv.getBlockPos()), tv.layout().pictureHashes(),
                now())) {
            setDirty();
        }
    }

    /** O bloco da Tela saiu do mundo (quebrado, explodido, trocado). */
    public void screenRemoved(Level level, BlockPos pos) {
        if (index.removeScreen(key(level, pos), now())) {
            setDirty();
        }
    }

    /** A biblioteca do jogador mudou: anota as imagens dela de novo. */
    public void libraryChanged(UUID player, Collection<String> hashes) {
        if (index.setLibrary(player, hashes, now())) {
            setDirty();
        }
    }

    /** O mesmo, lendo a biblioteca do jogador. */
    public static void syncLibrary(MinecraftServer server, UUID player) {
        get(server).libraryChanged(player, TvLibrary.get(server).pictureHashes(player));
    }

    // Os banidos.

    public boolean banned(UUID player) {
        return banned.contains(player);
    }

    public boolean ban(UUID player) {
        boolean added = banned.add(player);
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean unban(UUID player) {
        boolean removed = banned.remove(player);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public Set<UUID> bannedView() {
        return Collections.unmodifiableSet(banned);
    }

    // Os arquivos.

    private Path file(String hash, TvImageFormat format) {
        return dir.resolve(hash + "." + format.ext());
    }

    /**
     * Grava o arquivo de uma imagem já conferida (fora da thread do servidor: chamado pelo executor de entrada e
     * saída) num arquivo temporário trocado de uma vez pelo definitivo. Falso se não gravou.
     */
    boolean writeFile(String hash, TvImageFormat format, byte[] data) {
        try {
            Files.createDirectories(dir);
            Path target = file(hash, format);
            Path temp = dir.resolve(hash + ".tmp");
            Files.write(temp, data);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            WirelessAutomate.LOGGER.warn("Tela de Sinal: não deu para gravar a imagem {}", hash, e);
            return false;
        }
    }

    /** Põe no índice a imagem cujo arquivo já foi gravado (thread do servidor). */
    void added(String hash, TvImageHeader.Info info, int bytes, UUID author) {
        index.add(new TvImageIndex.Entry(hash, info.format(), info.width(), info.height(), info.frames(), bytes, author,
                now()), false, now());
        setDirty();
    }

    /**
     * Tira a imagem do armazém: do índice, das anotações e do cache, e apaga o arquivo (no executor). Falso se não
     * existia. Quem a usa (Telas carregadas e bibliotecas) é tratado por quem chama ({@link TvImageModeration}).
     */
    public boolean remove(String hash) {
        TvImageIndex.Entry entry = index.remove(hash);
        if (entry == null) {
            return false;
        }
        byte[] cached = cache.remove(hash);
        if (cached != null) {
            cacheBytes -= cached.length;
        }
        setDirty();
        Path target = file(hash, entry.format);
        Util.ioPool().execute(() -> {
            try {
                Files.deleteIfExists(target);
            } catch (IOException e) {
                WirelessAutomate.LOGGER.warn("Tela de Sinal: não deu para apagar a imagem {}", target, e);
            }
        });
        return true;
    }

    /**
     * Os bytes da imagem para a entrega, se já estão na memória; senão começa a lê-los no executor (uma vez) e devolve
     * {@code null}: quem pede tenta de novo depois. Um arquivo que sumiu da pasta deixa a imagem sem entrega.
     */
    public byte @Nullable [] loaded(String hash) {
        byte[] data = cache.get(hash);
        if (data != null) {
            return data;
        }
        TvImageIndex.Entry entry = index.get(hash);
        if (entry == null || server == null || unreadable.contains(hash) || !loading.add(hash)) {
            return null;
        }
        Path source = file(hash, entry.format);
        MinecraftServer owner = server;
        Util.ioPool().execute(() -> {
            byte[] read;
            try {
                read = Files.readAllBytes(source);
            } catch (IOException e) {
                WirelessAutomate.LOGGER.warn("Tela de Sinal: não deu para ler a imagem {}", source, e);
                read = null;
            }
            byte[] done = read;
            owner.execute(() -> cached(hash, done));
        });
        return null;
    }

    private void cached(String hash, byte @Nullable [] data) {
        loading.remove(hash);
        if (data == null) {
            unreadable.add(hash);
            return;
        }
        if (!index.contains(hash)) {
            return;
        }
        cache.put(hash, data);
        cacheBytes += data.length;
        Iterator<Map.Entry<String, byte[]>> it = cache.entrySet().iterator();
        while (cacheBytes > CACHE_BYTES && it.hasNext()) {
            Map.Entry<String, byte[]> oldest = it.next();
            if (oldest.getKey().equals(hash)) {
                continue;
            }
            cacheBytes -= oldest.getValue().length;
            it.remove();
        }
    }

    /** Se o arquivo da imagem não leu do disco: a entrega não espera por ele. */
    public boolean unreadable(String hash) {
        return unreadable.contains(hash);
    }

    // A limpeza.

    /** A cada tick do servidor: a limpeza a cada {@link #CLEANUP_MS}, no máximo {@link #CLEANUP_BATCH} por tick. */
    public static void tick() {
        TvImageStore store = active;
        if (store != null) {
            store.cleanup(now());
        }
    }

    private void cleanup(long now) {
        if (doomed.isEmpty()) {
            if (now < nextCleanup) {
                return;
            }
            nextCleanup = now + CLEANUP_MS;
            doomed.addAll(index.expired(now, GRACE_MS, 256));
        }
        for (int i = 0; i < CLEANUP_BATCH && !doomed.isEmpty(); i++) {
            String hash = doomed.poll();
            TvImageIndex.Entry entry = index.get(hash);
            if (entry != null && entry.uses() == 0 && !entry.recovered() && now - entry.unusedSince() >= GRACE_MS) {
                remove(hash);
            }
        }
    }

    /** Parada do servidor: o próximo mundo começa sem armazém ativo e com o relógio de verdade. */
    public static void reset() {
        active = null;
        clock = System::currentTimeMillis;
        dedicatedForTests = false;
    }

    /** O relógio da limpeza e das datas (só os testes; {@code null} volta ao de verdade). */
    public static void setClockForTests(@Nullable LongSupplier testClock) {
        clock = testClock == null ? System::currentTimeMillis : testClock;
    }

    /**
     * Guarda uma imagem direto, sem o envio (o arquivo gravado na hora, na thread de quem chama); falso se o cabeçalho
     * não vale ou o arquivo não gravou.
     */
    public boolean putForTests(byte[] data, UUID author) {
        TvImageHeader.Result header = TvImageHeader.read(data, TvPicture.HARD_SIZE, TvPicture.HARD_FRAMES);
        String hash = TvImageHash.of(data);
        if (!header.ok() || !writeFile(hash, header.info().format(), data)) {
            return false;
        }
        added(hash, header.info(), data.length, author);
        return true;
    }

    /** Roda a limpeza agora, com todas as que venceram de uma vez. */
    public void cleanupNowForTests() {
        nextCleanup = Long.MIN_VALUE;
        doomed.clear();
        cleanup(now());
        while (!doomed.isEmpty()) {
            cleanup(now());
        }
    }

    // O disco do índice.

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", 1);
        ListTag entries = new ListTag();
        for (TvImageIndex.Entry entry : index.entries()) {
            CompoundTag item = new CompoundTag();
            item.putString("hash", entry.hash);
            item.putString("format", entry.format.ext());
            item.putInt("w", entry.width);
            item.putInt("h", entry.height);
            item.putInt("frames", entry.frames);
            item.putLong("bytes", entry.bytes);
            if (entry.author != null) {
                item.putUUID("author", entry.author);
            }
            item.putLong("created", entry.created);
            item.putLong("unused", entry.unusedSince());
            if (entry.recovered()) {
                item.putBoolean("recovered", true);
            }
            entries.add(item);
        }
        tag.put("images", entries);
        ListTag screens = new ListTag();
        for (Map.Entry<GlobalPos, String[]> screen : index.screens().entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("dim", screen.getKey().dimension().location().toString());
            item.putLong("pos", screen.getKey().pos().asLong());
            ListTag hashes = new ListTag();
            for (String hash : screen.getValue()) {
                hashes.add(StringTag.valueOf(hash));
            }
            item.put("hashes", hashes);
            screens.add(item);
        }
        tag.put("screens", screens);
        ListTag bannedList = new ListTag();
        for (UUID player : banned) {
            bannedList.add(NbtUtils.createUUID(player));
        }
        tag.put("banned", bannedList);
        return tag;
    }

    /** Lê cada item à parte: um que não lê é largado, sem derrubar os outros. */
    private static TvImageStore load(CompoundTag tag) {
        TvImageStore store = new TvImageStore(true);
        long now = now();
        ListTag entries = tag.getList("images", Tag.TAG_COMPOUND);
        Map<String, Long> unused = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag item = entries.getCompound(i);
            String hash = item.getString("hash");
            TvImageFormat format = TvImageFormat.byExt(item.getString("format"));
            if (!TvImageHash.valid(hash) || format == null) {
                continue;
            }
            store.index.add(new TvImageIndex.Entry(hash, format, item.getInt("w"), item.getInt("h"),
                    Math.max(1, item.getInt("frames")), item.getLong("bytes"),
                    item.hasUUID("author") ? item.getUUID("author") : null, item.getLong("created")),
                    item.getBoolean("recovered"), now);
            unused.put(hash, item.getLong("unused"));
        }
        ListTag screens = tag.getList("screens", Tag.TAG_COMPOUND);
        for (int i = 0; i < screens.size(); i++) {
            CompoundTag item = screens.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(item.getString("dim"));
            if (dim == null) {
                continue;
            }
            ListTag hashes = item.getList("hashes", Tag.TAG_STRING);
            List<String> list = new ArrayList<>(hashes.size());
            for (int j = 0; j < hashes.size(); j++) {
                list.add(hashes.getString(j));
            }
            store.index.setScreen(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim),
                    BlockPos.of(item.getLong("pos"))), list, now);
        }
        unused.forEach(store.index::restoreUnusedSince);
        ListTag bannedList = tag.getList("banned", Tag.TAG_INT_ARRAY);
        for (Tag item : bannedList) {
            try {
                store.banned.add(NbtUtils.loadUUID(item));
            } catch (IllegalArgumentException e) {
                // Um UUID que não lê fica de fora.
            }
        }
        return store;
    }
}
