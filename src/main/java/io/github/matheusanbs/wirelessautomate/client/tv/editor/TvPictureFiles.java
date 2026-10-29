package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHeader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * "Enviar arquivo" da aba Imagens: a caixa de abrir arquivo do sistema ({@code TinyFileDialogs} do LWJGL, que o
 * Minecraft já traz), com o filtro png, jpg, jpeg e gif. A caixa é modal (no Windows, a do próprio sistema) e roda numa
 * thread só dela, para o jogo continuar desenhando enquanto ela está aberta; o arquivo escolhido é lido ali mesmo, com o
 * tamanho conferido antes de ler e o cabeçalho depois ({@link TvImageHeader}, nos tetos do servidor), e só então o
 * envio sai, na thread de render ({@link TvPictureSend}). Uma caixa por vez.
 * <p>
 * No Linux a caixa depende de um programa do sistema (zenity, kdialog e parentes); sem nenhum no {@code PATH}, o botão
 * avisa ({@link #available}) e colar o código {@code WA-IMAGEM1} continua valendo.
 */
final class TvPictureFiles {
    private static final String[] LINUX_DIALOGS = {"zenity", "kdialog", "qarma", "matedialog", "yad", "Xdialog"};
    private static volatile boolean opening;
    private static @Nullable Boolean available;

    private TvPictureFiles() {
    }

    /** Se a caixa existe neste sistema: sempre no Windows e no macOS; no Linux, se há um dos programas dela. */
    static boolean available() {
        if (available == null) {
            available = Util.getPlatform() != Util.OS.LINUX || linuxDialog();
        }
        return available;
    }

    private static boolean linuxDialog() {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            for (String program : LINUX_DIALOGS) {
                if (Files.isExecutable(Path.of(dir, program))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Se a caixa está aberta agora (o botão fica desligado). */
    static boolean opening() {
        return opening;
    }

    /** Abre a caixa e, com um arquivo escolhido que vale, envia a imagem com o nome do arquivo. */
    static void open(TvEditorActions actions) {
        if (opening || !TvPictureSend.allowed(actions.images())) {
            return;
        }
        if (!available()) {
            TvCodeNotice.show(TvCodeNotice.Kind.NO_DIALOG);
            return;
        }
        opening = true;
        String title = TvEditorText.key("pictures.dialog").getString();
        TvMenu.ImageRules rules = actions.images();
        Thread thread = new Thread(() -> {
            try {
                String chosen = ask(title);
                if (chosen != null) {
                    read(actions, rules, Path.of(chosen));
                }
            } finally {
                opening = false;
            }
        }, "Wireless Automate: abrir imagem");
        thread.setDaemon(true);
        thread.start();
    }

    private static @Nullable String ask(String title) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(4);
            filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg"))
                    .put(stack.UTF8("*.gif")).flip();
            return TinyFileDialogs.tinyfd_openFileDialog(title, System.getProperty("user.home") + File.separator,
                    filters, "PNG, JPG, GIF", false);
        }
    }

    /** Lê o arquivo (o tamanho antes, o cabeçalho depois) e manda o envio para a thread de render. */
    private static void read(TvEditorActions actions, TvMenu.ImageRules rules, Path file) {
        Component problem = null;
        byte[] data = null;
        try {
            if (Files.size(file) > rules.maxFileBytes()) {
                problem = Component.translatable("message.wirelessautomate.tv.image.file_too_big");
            } else {
                data = Files.readAllBytes(file);
                TvImageHeader.Result header = TvImageHeader.read(data, rules.maxSize(), rules.maxFrames());
                if (!header.ok()) {
                    problem = Component.translatable("message.wirelessautomate.tv.image." + switch (header.problem()) {
                        case TOO_LARGE -> "too_large";
                        case TOO_MANY_FRAMES -> "too_many_frames";
                        default -> "bad_format";
                    });
                }
            }
        } catch (IOException | RuntimeException e) {
            WirelessAutomate.LOGGER.warn("Tela de Sinal: não deu para ler a imagem {}", file, e);
            problem = TvEditorText.key("pictures.file_failed");
        }
        Component refused = problem;
        byte[] bytes = data;
        String name = name(file);
        Minecraft.getInstance().execute(() -> {
            if (refused != null) {
                TvCodeNotice.show(TvCodeNotice.Kind.PICTURE_REFUSED, refused);
            } else {
                TvPictureSend.send(actions, bytes, name);
            }
        });
    }

    /** O nome do arquivo sem a extensão, que vira o nome da imagem na biblioteca. */
    static String name(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return dot > 0 && (ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("gif"))
                ? name.substring(0, dot) : name;
    }
}
