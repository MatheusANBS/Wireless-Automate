package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.packet.TvLayoutPayload;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Os códigos de texto da Tela de Sinal, para copiar e colar fora do jogo, em duas formas que o colar aceita igual:
 * <ul>
 * <li><b>comprimida:</b> {@code WA-TELA1:} com o NBT de um layout (as artes que ele usa vão junto, como cópia) e
 * {@code WA-ARTE1:} com o NBT de uma pixel art, pelo {@link TvCodeText}, com o mesmo teto do pacote de salvar
 * ({@link TvLayoutPayload#MAX_BYTES}) conferido durante a descompressão;</li>
 * <li><b>legível</b> (a primeira linha só {@code WA-TELA1} ou {@code WA-ARTE1}, sem os dois pontos), para uma pessoa
 * ou uma IA de chat escrever: a arte pelo {@link TvArtText} e o layout em JSON pelo {@link TvLayoutJson}.</li>
 * </ul>
 * Os dois leem com os codecs tolerantes de sempre ({@link TvLayout#CODEC}, {@link TvPixelArt#CODEC}) e o texto tem o
 * teto {@link TvCodeText#MAX_TEXT}. Nada vai ao servidor por aqui: o layout colado é salvo pelo caminho de sempre,
 * que valida tudo.
 *
 * <p>O código do layout leva, além das chaves do layout, a área de origem em pixels de desenho ({@code area_w},
 * {@code area_h}), para as peças escalarem como num layout salvo, e {@code keep_theme} quando vem de um layout salvo
 * (que não guarda tema: quem cola fica com o seu). As imagens enviadas vão como referência (a lista {@code pictures}
 * do layout) e, quando cabem no teto do texto, com os bytes em {@link #PICTURE_DATA} (o resumo e o arquivo de cada
 * uma): quem cola as envia de novo e o layout funciona em outro servidor. Sem os bytes, só onde as imagens já existem.
 * Por causa deles, o código do layout descomprime até {@link #MAX_LAYOUT_CODE_BYTES}; o layout sem eles continua no
 * teto do pacote de salvar, que o colar confere.
 */
public final class TvCodes {
    public static final String LAYOUT_PREFIX = TvLayoutJson.HEADER + ":";
    public static final String ART_PREFIX = TvArtText.HEADER + ":";
    private static final String AREA_W = "area_w";
    private static final String AREA_H = "area_h";
    private static final String KEEP_THEME = "keep_theme";
    /** A chave dos bytes das imagens no código do layout (comprimido e legível). */
    public static final String PICTURE_DATA = "picture_data";
    /** Quanto o código de um layout com imagens pode ter descomprimido (o texto de 512 K não comprime fotos). */
    public static final int MAX_LAYOUT_CODE_BYTES = 1 << 20;
    /** A maior área de origem aceita, em pixels de desenho por lado; fora disso, as peças não escalam. */
    private static final int MAX_AREA = 4096;

    private TvCodes() {
    }

    /** O que um código deu: o valor, ou a chave do aviso ({@code code.*}) e os argumentos dele. */
    public record Read<T>(@Nullable T value, @Nullable String error, Object[] args) {
        public static <T> Read<T> ok(T value) {
            return new Read<>(value, null, new Object[0]);
        }

        public static <T> Read<T> fail(String error, Object... args) {
            return new Read<>(null, error, args);
        }
    }

    /**
     * Um layout lido de um código: a área de origem é 0 quando o código não a tem; {@code dropped} conta as ligações
     * que não leram e saíram (só na forma legível).
     */
    public record Layout(TvLayout layout, int width, int height, boolean keepTheme, int dropped,
            Map<String, byte[]> pictureData) {
        static Layout of(TvLayout layout, int width, int height, boolean keepTheme, int dropped,
                Map<String, byte[]> pictureData) {
            boolean known = width > 0 && height > 0 && width <= MAX_AREA && height <= MAX_AREA;
            return new Layout(layout, known ? width : 0, known ? height : 0, keepTheme, dropped,
                    TvCodes.pictureData(layout, pictureData));
        }

        /** Se as peças vieram de uma área conhecida e diferente de {@code w} × {@code h}. */
        public boolean needsScale(int w, int h) {
            return width > 0 && height > 0 && (width != w || height != h);
        }
    }

    /** O layout sem as ligações que {@code keep} recusa e quantas saíram. */
    public record Pruned(TvLayout layout, int removed) {
    }

    /**
     * O código do layout feito numa área de {@code width} × {@code height} pixels de desenho, ou {@code null} se ele
     * não leria de volta (NBT acima de {@link TvLayoutPayload#MAX_BYTES} ou texto acima de
     * {@link TvCodeText#MAX_TEXT}).
     */
    public static @Nullable String layout(TvLayout layout, int width, int height, boolean keepTheme) {
        return layout(layout, width, height, keepTheme, Map.of());
    }

    /**
     * O mesmo com os bytes das imagens ({@code data}, pelo resumo); {@code null} também se eles não couberem no teto
     * do texto (quem chama tenta de novo sem eles).
     */
    public static @Nullable String layout(TvLayout layout, int width, int height, boolean keepTheme,
            Map<String, byte[]> data) {
        CompoundTag tag = (CompoundTag) TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout).getOrThrow();
        tag.putInt(AREA_W, width);
        tag.putInt(AREA_H, height);
        if (keepTheme) {
            tag.putBoolean(KEEP_THEME, true);
        }
        if (bytes(tag).length > TvLayoutPayload.MAX_BYTES) {
            return null;
        }
        if (data.isEmpty()) {
            return code(LAYOUT_PREFIX, tag);
        }
        ListTag list = new ListTag();
        for (Map.Entry<String, byte[]> entry : data.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("hash", entry.getKey());
            item.put("data", new ByteArrayTag(entry.getValue()));
            list.add(item);
        }
        tag.put(PICTURE_DATA, list);
        byte[] raw = bytes(tag);
        if (raw.length > MAX_LAYOUT_CODE_BYTES) {
            return null;
        }
        String code = TvCodeText.encode(LAYOUT_PREFIX, raw);
        return code.length() > TvCodeText.MAX_TEXT ? null : code;
    }

    /** O código legível do layout (JSON), ou {@code null} acima do teto do texto. */
    public static @Nullable String layoutText(TvLayout layout, int width, int height, boolean keepTheme,
            boolean english) {
        return layoutText(layout, width, height, keepTheme, english, Map.of());
    }

    /** O mesmo com os bytes das imagens em base64; {@code null} acima do teto do texto. */
    public static @Nullable String layoutText(TvLayout layout, int width, int height, boolean keepTheme,
            boolean english, Map<String, byte[]> data) {
        String text = TvLayoutJson.write(layout, width, height, keepTheme, english, data);
        return text.length() > TvCodeText.MAX_TEXT ? null : text;
    }

    /**
     * Os bytes que valem: os das imagens que o layout aponta, com o resumo que bate com o conteúdo e o cabeçalho que
     * lê nos tetos de qualquer imagem. Um arquivo trocado no caminho é largado (e a imagem fica só como referência).
     */
    static Map<String, byte[]> pictureData(TvLayout layout, Map<String, byte[]> data) {
        if (data.isEmpty()) {
            return Map.of();
        }
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (TvPicture picture : layout.pictures()) {
            byte[] bytes = data.get(picture.hash());
            if (bytes != null && TvImageHash.of(bytes).equals(picture.hash())
                    && TvImageHeader.read(bytes, TvPicture.HARD_SIZE, TvPicture.HARD_FRAMES).ok()) {
                out.put(picture.hash(), bytes);
            }
        }
        return Map.copyOf(out);
    }

    /** O código da arte (uma arte no teto de tamanho fica bem abaixo dos tetos). */
    public static @Nullable String art(TvPixelArt art) {
        return code(ART_PREFIX, TvPixelArt.CODEC.encodeStart(NbtOps.INSTANCE, art).getOrThrow());
    }

    /** O código legível da arte, com as linhas de pixels. */
    public static String artText(TvPixelArt art, boolean english) {
        return TvArtText.format(new TvArtText.Art(art.name(), art.width(), art.height(), art.palette(),
                art.pixels()), english);
    }

    private static @Nullable String code(String prefix, Tag tag) {
        byte[] raw = bytes(tag);
        if (raw.length > TvLayoutPayload.MAX_BYTES) {
            return null;
        }
        String code = TvCodeText.encode(prefix, raw);
        return code.length() > TvCodeText.MAX_TEXT ? null : code;
    }

    /** O layout do código, nas duas formas. */
    public static Read<Layout> readLayout(@Nullable String text) {
        int body = readable(TvLayoutJson.HEADER, text);
        if (body >= 0) {
            return TvLayoutJson.read(text.substring(body), lineOf(text, body));
        }
        CompoundTag tag = read(LAYOUT_PREFIX, text, MAX_LAYOUT_CODE_BYTES);
        Map<String, byte[]> data = new LinkedHashMap<>();
        if (tag != null) {
            ListTag list = tag.getList(PICTURE_DATA, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && i < TvLayout.MAX_PICTURES; i++) {
                CompoundTag item = list.getCompound(i);
                data.put(item.getString("hash"), item.getByteArray("data"));
            }
            tag.remove(PICTURE_DATA);
        }
        Optional<TvLayout> layout = tag == null || bytes(tag).length > TvLayoutPayload.MAX_BYTES ? Optional.empty()
                : TvLayout.CODEC.parse(NbtOps.INSTANCE, tag).result();
        if (layout.isEmpty()) {
            return Read.fail("code.invalid");
        }
        return Read.ok(Layout.of(layout.get(), tag.getInt(AREA_W), tag.getInt(AREA_H), tag.getBoolean(KEEP_THEME), 0,
                data));
    }

    /**
     * A arte do código, nas duas formas: a comprimida com o id e o nome de quem a exportou, a legível com um id novo.
     */
    public static Read<TvPixelArt> readArt(@Nullable String text) {
        if (readable(TvArtText.HEADER, text) >= 0) {
            TvArtText.Parsed parsed = TvArtText.parse(text);
            TvArtText.Art art = parsed.art();
            return art == null ? Read.fail(parsed.error(), parsed.args()) : Read.ok(new TvPixelArt(UUID.randomUUID(),
                    art.name(), art.width(), art.height(), art.palette(), art.pixels()));
        }
        CompoundTag tag = read(ART_PREFIX, text, TvLayoutPayload.MAX_BYTES);
        Optional<TvPixelArt> art = tag == null ? Optional.empty()
                : TvPixelArt.CODEC.parse(NbtOps.INSTANCE, tag).result();
        return art.isPresent() ? Read.ok(art.get()) : Read.fail("code.invalid");
    }

    /**
     * Onde começa o corpo de um código legível (o texto depois da linha {@code header}, a primeira que não está em
     * branco), ou -1 se não é um, ou se o texto passa do teto.
     */
    private static int readable(String header, @Nullable String text) {
        if (text == null || text.length() > TvCodeText.MAX_TEXT) {
            return -1;
        }
        int start = 0;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
            end++;
        }
        return text.substring(start, end).strip().equalsIgnoreCase(header) ? end : -1;
    }

    /** O número (a partir de 1) da linha em que fica a posição {@code at} do texto. */
    private static int lineOf(String text, int at) {
        int line = 1;
        for (int i = 0; i < at; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * O layout sem as ligações que {@code keep} recusa (uma rede ou um armazenamento que quem cola não tem): a peça
     * fica sem dado, a regra fica com o dado da peça, para o jogador ligar de novo no editor.
     */
    public static Pruned withoutBindings(TvLayout layout, Predicate<TvBinding> keep) {
        int removed = 0;
        List<TvPiece> pieces = new ArrayList<>(layout.pieces().size());
        for (TvPiece piece : layout.pieces()) {
            if (piece.binding().isPresent() && !keep.test(piece.binding().get())) {
                piece = piece.withBinding(Optional.empty());
                removed++;
            }
            List<TvRule> rules = null;
            for (int i = 0; i < piece.rules().size(); i++) {
                TvRule rule = piece.rules().get(i);
                if (rule.bind().isPresent() && !keep.test(rule.bind().get())) {
                    if (rules == null) {
                        rules = new ArrayList<>(piece.rules());
                    }
                    rules.set(i, new TvRule(rule.op(), rule.value(), Optional.empty(), rule.color(),
                            rule.background(), rule.blink(), rule.hide()));
                    removed++;
                }
            }
            pieces.add(rules == null ? piece : piece.withRules(rules));
        }
        return removed == 0 ? new Pruned(layout, 0) : new Pruned(layout.withPieces(pieces), removed);
    }

    private static @Nullable CompoundTag read(String prefix, @Nullable String text, int maxBytes) {
        byte[] raw = TvCodeText.decode(prefix, text, maxBytes);
        if (raw == null) {
            return null;
        }
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(raw))) {
            Tag tag = NbtIo.readAnyTag(data, NbtAccounter.create(maxBytes * 4L));
            return tag instanceof CompoundTag compound ? compound : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static byte[] bytes(Tag tag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(out)) {
            NbtIo.writeAnyTag(tag, data);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }
}
