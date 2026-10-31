package io.github.matheusanbs.wirelessautomate.client.guide;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.TextMeasure;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.TextStyle;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * A medida de texto da paginação com a fonte do jogo (um pixel da fonte = uma unidade de folha) e o tamanho real das
 * imagens das páginas, lido do cabeçalho do PNG. Usada só ao paginar, nunca por quadro.
 */
final class FontMeasure implements TextMeasure {
    private final Font font;
    private final ResourceManager resources;
    private final Map<String, Size> images = new HashMap<>();

    FontMeasure(Font font, ResourceManager resources) {
        this.font = font;
        this.resources = resources;
    }

    @Override
    public int width(String text, TextStyle style) {
        return font.width(FormattedText.of(text, GuideInk.style(style)));
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    @Override
    public Size imageSize(String path) {
        return images.computeIfAbsent(path, this::readSize);
    }

    /** O recurso da imagem: o caminho do texto é relativo a {@code assets/wirelessautomate/guide/}. */
    static ResourceLocation image(String path) {
        return WirelessAutomate.id("guide/" + path);
    }

    /** Largura e altura do PNG pelo cabeçalho IHDR (bytes 16 a 23), sem decodificar a imagem. */
    private Size readSize(String path) {
        Optional<Resource> res = resources.getResource(image(path));
        if (res.isPresent()) {
            try (InputStream in = res.get().open()) {
                byte[] head = in.readNBytes(24);
                if (head.length == 24) {
                    int w = readInt(head, 16);
                    int h = readInt(head, 20);
                    if (w > 0 && h > 0) {
                        return new Size(w, h);
                    }
                }
            } catch (IOException e) {
                WirelessAutomate.LOGGER.warn("Livro: não deu para ler a imagem {}", path, e);
            }
        } else {
            WirelessAutomate.LOGGER.warn("Livro: imagem {} não encontrada", path);
        }
        return new Size(32, 32);
    }

    private static int readInt(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
    }
}
