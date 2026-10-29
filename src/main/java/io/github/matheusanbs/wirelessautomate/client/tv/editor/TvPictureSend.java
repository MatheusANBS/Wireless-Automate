package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageDisk;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageUploader;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.tv.TvCodes;
import io.github.matheusanbs.wirelessautomate.tv.TvImageCode;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryPicture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * O envio de imagens a partir do editor: o código {@code WA-IMAGEM1} colado no "Importar" ({@link #pasteIfImage}, nas
 * abas Pixel art e Imagens e no colar do layout), o "Copiar código" de uma imagem da biblioteca ({@link #copy}) e o
 * envio comum dos bytes, que também o arquivo aberto pelo {@link TvPictureFiles} usa ({@link #send}). Os tetos do
 * servidor vêm das regras do menu ({@link TvMenu.ImageRules}) e são conferidos antes de qualquer coisa sair; o
 * servidor confere de novo. Os avisos vão pelo {@link TvCodeNotice}. Só no clique.
 */
final class TvPictureSend {
    private TvPictureSend() {
    }

    /**
     * Se o texto é um código de imagem, lê e envia (ou avisa por que não); devolve se era um, para quem chama não
     * tentar lê-lo como arte ou layout.
     */
    static boolean pasteIfImage(TvEditorActions actions, String text) {
        if (!TvImageCode.is(text)) {
            return false;
        }
        TvMenu.ImageRules rules = actions.images();
        if (!allowed(rules)) {
            return true;
        }
        TvCodes.Read<TvImageCode.Image> read = TvImageCode.read(text, rules.maxFileBytes(), rules.maxSize(),
                rules.maxFrames());
        TvImageCode.Image image = read.value();
        if (image == null) {
            TvCodeNotice.failed(read);
            return true;
        }
        send(actions, image.data(), image.name());
        return true;
    }

    /** Se o servidor deixa este jogador enviar; senão avisa o porquê. */
    static boolean allowed(TvMenu.ImageRules rules) {
        if (!rules.enabled()) {
            TvCodeNotice.show(TvCodeNotice.Kind.PICTURE_REFUSED, Component.translatable(
                    "message.wirelessautomate.tv.image.disabled"));
            return false;
        }
        if (!rules.canUpload()) {
            TvCodeNotice.show(TvCodeNotice.Kind.PICTURE_REFUSED, Component.translatable(
                    "message.wirelessautomate.tv.image.not_allowed"));
            return false;
        }
        return true;
    }

    /** Envia os bytes com o nome (vazio: o padrão "Nova imagem"); o aviso acompanha o envio até a resposta. */
    static void send(TvEditorActions actions, byte[] data, String name) {
        String named = name.isBlank() ? TvEditorText.key("pictures.new_name").getString()
                : cut(name.strip());
        TvCodeNotice.show(TvCodeNotice.Kind.PICTURE_SENDING);
        actions.uploadPicture(data, named, TvPictureSend::answered);
    }

    /** O nome em até {@link TvLibraryPicture#MAX_NAME} letras (o servidor corta do mesmo jeito). */
    private static String cut(String name) {
        return name.codePointCount(0, name.length()) <= TvLibraryPicture.MAX_NAME ? name
                : name.substring(0, name.offsetByCodePoints(0, TvLibraryPicture.MAX_NAME)).strip();
    }

    private static void answered(TvImageUploader.Outcome outcome) {
        if (outcome.ok()) {
            TvCodeNotice.show(TvCodeNotice.Kind.PICTURE_SENT);
        } else if (outcome.refusal() != null) {
            TvCodeNotice.show(TvCodeNotice.Kind.PICTURE_REFUSED, Component.translatable(outcome.refusal().key()));
        }
    }

    /**
     * "Copiar código": a imagem da biblioteca no formato {@code WA-IMAGEM1}, com os bytes do cache em disco (lidos
     * fora da thread de render). A imagem que ainda não chegou a este cliente avisa e não copia.
     */
    static void copy(TvEditorActions actions, TvLibraryPicture entry) {
        int maxText = TvImageCode.maxText(actions.images().maxFileBytes());
        TvImageDisk.read(entry.picture().hash()).thenAccept(data -> Minecraft.getInstance().execute(() -> {
            if (data == null) {
                TvCodeNotice.show(TvCodeNotice.Kind.MISSING_PICTURE);
                return;
            }
            String code = TvImageCode.write(entry.name(), data, entry.picture().format(), TvLayoutCode.english());
            if (code.length() > maxText) {
                TvCodeNotice.show(TvCodeNotice.Kind.TOO_BIG);
                return;
            }
            Minecraft.getInstance().keyboardHandler.setClipboard(code);
            TvCodeNotice.show(TvCodeNotice.Kind.COPIED);
        }));
    }
}
