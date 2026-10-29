package io.github.matheusanbs.wirelessautomate.client.tv.image;

import io.github.matheusanbs.wirelessautomate.ClientConfig;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHash;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHeader;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * O cache em disco das imagens enviadas no cliente: {@code <pasta do jogo>/wirelessautomate/image-cache/<resumo>}, um
 * arquivo por imagem, como veio do servidor, até {@code tv.imageCacheMb} ({@link ClientConfig}); acima disso sai
 * primeiro a usada há mais tempo (a data do arquivo é renovada a cada leitura, então a ordem sobrevive a um reinício).
 * Quem volta a uma Tela não baixa de novo, e a lista do que está aqui vai ao servidor uma vez por conexão
 * ({@link #recent}).
 * <p>
 * Tudo roda numa thread própria ({@link #IO}), em ordem: a thread de render nunca espera o disco. O índice (resumo,
 * tamanho, na ordem de uso) é montado da pasta no primeiro uso e só essa thread o toca. Só nomes que são resumos
 * válidos entram, então nenhum nome vira caminho.
 */
public final class TvImageDisk {
    static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Wireless Automate imagens");
        thread.setDaemon(true);
        return thread;
    });
    private static final long MB = 1024L * 1024;
    /** Resumo → tamanho, do menos ao mais usado. */
    private static final LinkedHashMap<String, Long> FILES = new LinkedHashMap<>(16, 0.75f, true);
    private static long total;
    private static boolean scanned;
    private static volatile @Nullable Path dir;

    private TvImageDisk() {
    }

    private static Path dir() {
        Path path = dir;
        if (path == null) {
            path = Minecraft.getInstance().gameDirectory.toPath().resolve(WirelessAutomate.MODID).resolve("image-cache");
            dir = path;
        }
        return path;
    }

    /** O índice da pasta, montado uma vez (na thread {@link #IO}). */
    private static void scan() {
        if (scanned) {
            return;
        }
        scanned = true;
        Path folder = dir();
        if (!Files.isDirectory(folder)) {
            return;
        }
        List<Object[]> found = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (TvImageHash.valid(name)) {
                    found.add(new Object[] {name, Files.size(file), Files.getLastModifiedTime(file).toMillis()});
                }
            }
        } catch (IOException e) {
            WirelessAutomate.LOGGER.warn("Tela de Sinal: não deu para ler o cache de imagens {}", folder, e);
        }
        found.sort(Comparator.comparingLong(item -> (Long) item[2]));
        for (Object[] item : found) {
            FILES.put((String) item[0], (Long) item[1]);
            total += (Long) item[1];
        }
    }

    /** Os bytes da imagem guardada, ou {@code null} se ela não está aqui; renova a data dela. */
    public static CompletableFuture<byte @Nullable []> read(String hash) {
        return CompletableFuture.supplyAsync(() -> {
            scan();
            if (!TvImageHash.valid(hash) || !FILES.containsKey(hash)) {
                return null;
            }
            Path file = dir().resolve(hash);
            try {
                byte[] data = Files.readAllBytes(file);
                Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
                return data;
            } catch (IOException e) {
                Long size = FILES.remove(hash);
                total -= size == null ? 0 : size;
                return null;
            }
        }, IO);
    }

    /**
     * Confere os bytes que chegaram do servidor (o resumo e o cabeçalho, nos tetos de qualquer imagem) e os guarda;
     * verdadeiro se valem. Quem chama só os usa se valem.
     */
    public static CompletableFuture<Boolean> verifyAndStore(String hash, byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            if (!TvImageHash.valid(hash) || !TvImageHash.of(data).equals(hash)
                    || !TvImageHeader.read(data, TvPicture.HARD_SIZE, TvPicture.HARD_FRAMES).ok()) {
                return false;
            }
            write(hash, data);
            return true;
        }, IO);
    }

    /** Guarda os bytes de uma imagem já conferida (a que o próprio jogador enviou). */
    public static void store(String hash, byte[] data) {
        IO.execute(() -> write(hash, data));
    }

    /** Grava num temporário trocado de uma vez pelo definitivo e solta as mais antigas acima do teto. */
    private static void write(String hash, byte[] data) {
        scan();
        if (!TvImageHash.valid(hash)) {
            return;
        }
        if (FILES.containsKey(hash)) {
            // Já guardada: só passa a ser a mais recente.
            FILES.get(hash);
            return;
        }
        Path folder = dir();
        try {
            Files.createDirectories(folder);
            Path temp = folder.resolve(hash + ".tmp");
            Files.write(temp, data);
            Files.move(temp, folder.resolve(hash), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            WirelessAutomate.LOGGER.warn("Tela de Sinal: não deu para guardar a imagem {} no cache", hash, e);
            return;
        }
        FILES.put(hash, (long) data.length);
        total += data.length;
        long cap = ClientConfig.TV_IMAGE_CACHE_MB.get() * MB;
        Iterator<Map.Entry<String, Long>> it = FILES.entrySet().iterator();
        while (total > cap && it.hasNext()) {
            Map.Entry<String, Long> oldest = it.next();
            if (oldest.getKey().equals(hash)) {
                continue;
            }
            try {
                Files.deleteIfExists(folder.resolve(oldest.getKey()));
            } catch (IOException e) {
                continue;
            }
            total -= oldest.getValue();
            it.remove();
        }
    }

    /** Os resumos guardados, os mais recentes primeiro, até {@code limit}. */
    public static CompletableFuture<List<String>> recent(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            scan();
            List<String> all = new ArrayList<>(FILES.keySet());
            Collections.reverse(all);
            return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
        }, IO);
    }

    /** Se a imagem está no cache (o e2e confere que a enviada ficou guardada). */
    public static CompletableFuture<Boolean> hasForTests(String hash) {
        return CompletableFuture.supplyAsync(() -> {
            scan();
            return FILES.containsKey(hash);
        }, IO);
    }
}
