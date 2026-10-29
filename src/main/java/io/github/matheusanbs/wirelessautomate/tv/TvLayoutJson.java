package io.github.matheusanbs.wirelessautomate.tv;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * A forma legível do código do layout: a linha {@code WA-TELA1} e o JSON do {@link TvLayout} pelos codecs de sempre
 * ({@link JsonOps}), com três ajudas para quem escreve à mão (uma pessoa ou uma IA de chat):
 * <ul>
 * <li>nos campos de cor conhecidos (do estilo, das regras, do tema e do fundo; nunca em {@code text}), a cor em
 * {@code "#RRGGBB"} ou {@code "#RRGGBBAA"}, o papel do tema por nome ({@code "tema:texto"}, {@code "theme:text"}...)
 * e {@code "transparente"}, além do inteiro;</li>
 * <li>em {@code images}, a arte legível ({@code id}, {@code nome}, {@code tamanho}, {@code paleta} e {@code linhas},
 * como no {@link TvArtText}), além da empacotada; os ids (da arte, da Imagem, do fundo e da rede) podem ser texto, e
 * um texto que não é UUID vira sempre o mesmo id, para a Imagem apontar a arte pelo nome que quem escreveu deu;</li>
 * <li>uma ligação que não lê sai da peça (e é contada), em vez de derrubar a peça inteira;</li>
 * <li>o tema com chaves faltando é completado pelo Grafite ({@link TvTheme#GRAPHITE}), em vez de cair inteiro nele.</li>
 * </ul>
 * Uma peça que não lê é erro, com o número dela, e uma arte que não lê também, com o nome dela e a fileira (o texto
 * colado não guarda a linha de cada valor do JSON): à mão é melhor saber do que perder a peça calada. As cores do tema
 * são sempre fixas: um papel do tema nelas é erro. Exportar faz o caminho de volta: JSON indentado com as cores e os
 * papéis por nome, os ids em texto e as artes legíveis.
 */
public final class TvLayoutJson {
    public static final String HEADER = "WA-TELA1";
    /** O teto de aninhamento do JSON (o layout mais fundo tem uns 6 níveis). */
    private static final int MAX_DEPTH = 32;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern LINE = Pattern.compile("line (\\d+)");
    private static final String[] ROLES_PT = {"", "fundo", "cartao", "texto", "apagado", "alta", "baixa"};
    private static final String[] ROLES_EN = {"", "background", "card", "text", "muted", "high", "low"};
    private static final Set<String> STYLE_COLORS = Set.of("color", "background", "border", "track", "upColor",
            "downColor", "flatColor");
    private static final Set<String> RULE_COLORS = Set.of("color", "background");
    private static final Set<String> THEME_COLORS = Set.of("bg", "card", "text", "muted", "high", "low");
    /** As chaves de cor do tema na ordem dos papéis (1 a 6), para completar o tema com as do Grafite. */
    private static final List<String> THEME_KEYS = List.of("bg", "card", "text", "muted", "high", "low");
    /** Os erros da arte que trazem a linha do texto como primeiro argumento; no JSON ela não diz nada e sai. */
    private static final Set<String> ART_LINE_ERRORS = Set.of("code.art.size", "code.art.palette_long",
            "code.art.color");
    private static final Set<String> BACKGROUND_COLORS = Set.of("top", "bottom");
    private static final List<String> PIECE_ORDER = List.of("type", "name", "x", "y", "w", "h", "group",
            "hidden", "bind", "format", "style", "rules");

    private TvLayoutJson() {
    }

    /** Um erro de quem escreveu: a chave do texto e os argumentos. */
    private static final class Bad extends Exception {
        final String key;
        final Object[] args;

        Bad(String key, Object... args) {
            super(key, null, false, false);
            this.key = key;
            this.args = args;
        }
    }

    // ------------------------------------------------------------------ exportar

    /** O código legível do layout feito numa área de {@code width} × {@code height} pixels de desenho. */
    public static String write(TvLayout layout, int width, int height, boolean keepTheme, boolean english) {
        return write(layout, width, height, keepTheme, english, Map.of());
    }

    /** O mesmo com os bytes das imagens enviadas em base64 ({@link TvCodes#PICTURE_DATA}). */
    public static String write(TvLayout layout, int width, int height, boolean keepTheme, boolean english,
            Map<String, byte[]> data) {
        JsonObject encoded = TvLayout.CODEC.encodeStart(JsonOps.INSTANCE, layout).getOrThrow().getAsJsonObject();
        JsonObject root = new JsonObject();
        root.addProperty("area_w", width);
        root.addProperty("area_h", height);
        if (keepTheme) {
            root.addProperty("keep_theme", true);
        }
        for (var entry : encoded.entrySet()) {
            root.add(entry.getKey(), entry.getValue());
        }
        root.add("pieces", pieces(layout.pieces()));
        if (!layout.images().isEmpty()) {
            JsonArray images = new JsonArray();
            for (TvPixelArt art : layout.images()) {
                images.add(readableArt(art, english));
            }
            root.add("images", images);
        }
        if (!data.isEmpty()) {
            JsonArray pictures = new JsonArray();
            for (Map.Entry<String, byte[]> entry : data.entrySet()) {
                JsonObject item = new JsonObject();
                item.addProperty("hash", entry.getKey());
                item.addProperty("data", Base64.getEncoder().encodeToString(entry.getValue()));
                pictures.add(item);
            }
            root.add(TvCodes.PICTURE_DATA, pictures);
        }
        helpersOut(root, english);
        return HEADER + "\n" + GSON.toJson(root) + "\n";
    }

    /**
     * As peças e as regras codificadas direto em JSON: pelo {@link TvLayout#CODEC} elas passam pelo NBT, que guarda
     * o verdadeiro e falso como número, e o JSON lido de volta não os aceitaria.
     */
    private static JsonArray pieces(List<TvPiece> pieces) {
        JsonArray out = new JsonArray(pieces.size());
        for (TvPiece piece : pieces) {
            JsonObject json = TvPiece.CODEC.encodeStart(JsonOps.INSTANCE, piece).getOrThrow().getAsJsonObject();
            if (!piece.rules().isEmpty()) {
                JsonArray rules = new JsonArray(piece.rules().size());
                for (TvRule rule : piece.rules()) {
                    rules.add(TvCodecs.RULE.encodeStart(JsonOps.INSTANCE, rule).getOrThrow());
                }
                json.add("rules", rules);
            }
            out.add(ordered(json));
        }
        return out;
    }

    /** A peça com o tipo e a caixa primeiro, para ler de cima para baixo. */
    private static JsonObject ordered(JsonObject piece) {
        JsonObject out = new JsonObject();
        for (String key : PIECE_ORDER) {
            if (piece.has(key)) {
                out.add(key, piece.get(key));
            }
        }
        for (var entry : piece.entrySet()) {
            if (!out.has(entry.getKey())) {
                out.add(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private static JsonObject readableArt(TvPixelArt art, boolean english) {
        TvArtText.Art plain = new TvArtText.Art(art.name(), art.width(), art.height(), art.palette(), art.pixels());
        JsonObject out = new JsonObject();
        out.addProperty("id", art.id().toString());
        out.addProperty(english ? "name" : "nome", art.name());
        out.addProperty(english ? "size" : "tamanho", art.width() + "x" + art.height());
        out.add(english ? "palette" : "paleta", strings(TvArtText.paletteEntries(art.palette())));
        out.add(english ? "rows" : "linhas", strings(TvArtText.rows(plain)));
        return out;
    }

    private static JsonArray strings(List<String> list) {
        JsonArray out = new JsonArray(list.size());
        list.forEach(out::add);
        return out;
    }

    /** O caminho de volta das ajudas: cores por texto e ids em texto (as artes já saíram legíveis). */
    private static void helpersOut(JsonObject root, boolean english) {
        colorsOut(object(root, "theme"), THEME_COLORS, english);
        JsonObject background = object(root, "background");
        colorsOut(background, BACKGROUND_COLORS, english);
        idOut(background, "image");
        for (JsonObject piece : objects(root, "pieces")) {
            JsonObject style = object(piece, "style");
            colorsOut(style, STYLE_COLORS, english);
            idOut(style, "image");
            idOut(object(piece, "bind"), "network");
            for (JsonObject rule : objects(piece, "rules")) {
                colorsOut(rule, RULE_COLORS, english);
                idOut(object(rule, "bind"), "network");
            }
        }
    }

    private static void colorsOut(@Nullable JsonObject object, Set<String> keys, boolean english) {
        if (object == null) {
            return;
        }
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
                object.addProperty(key, colorText(primitive.getAsInt(), english));
            }
        }
    }

    /** A cor como texto: {@code #RRGGBB}/{@code #RRGGBBAA}, o papel do tema por nome ou o transparente. */
    static String colorText(int color, boolean english) {
        if (color == 0) {
            return english ? "transparent" : "transparente";
        }
        if (TvColor.isTheme(color)) {
            return english ? "theme:" + ROLES_EN[color] : "tema:" + ROLES_PT[color];
        }
        return TvColor.hex(color);
    }

    private static void idOut(@Nullable JsonObject object, String key) {
        if (object != null && object.get(key) instanceof JsonArray array && array.size() == 4) {
            long most = (long) array.get(0).getAsInt() << 32 | array.get(1).getAsInt() & 0xFFFFFFFFL;
            long least = (long) array.get(2).getAsInt() << 32 | array.get(3).getAsInt() & 0xFFFFFFFFL;
            object.addProperty(key, new UUID(most, least).toString());
        }
    }

    // ------------------------------------------------------------------ importar

    /**
     * Lê o JSON depois da linha {@code WA-TELA1} ({@code firstLine} é o número da primeira linha dele no texto, para
     * o erro de sintaxe apontar a linha certa).
     */
    public static TvCodes.Read<TvCodes.Layout> read(String json, int firstLine) {
        if (TvCodeText.jsonDepth(json) > MAX_DEPTH) {
            return TvCodes.Read.fail("code.invalid");
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (JsonParseException | StackOverflowError e) {
            Matcher matcher = LINE.matcher(String.valueOf(e.getMessage()));
            int line = matcher.find() ? Integer.parseInt(matcher.group(1)) + firstLine - 1 : firstLine;
            return TvCodes.Read.fail("code.layout.json", line);
        }
        if (!(parsed instanceof JsonObject root)) {
            return TvCodes.Read.fail("code.invalid");
        }
        Map<String, byte[]> data = pictureData(root);
        int dropped;
        try {
            dropped = fix(root);
        } catch (Bad bad) {
            return TvCodes.Read.fail(bad.key, bad.args);
        }
        Optional<TvLayout> layout = TvLayout.CODEC.parse(JsonOps.INSTANCE, root).result();
        if (layout.isEmpty()) {
            return TvCodes.Read.fail("code.invalid");
        }
        int w = integer(root.get("area_w"));
        int h = integer(root.get("area_h"));
        boolean keepTheme = root.get("keep_theme") instanceof JsonPrimitive keep && keep.isBoolean()
                && keep.getAsBoolean();
        return TvCodes.Read.ok(TvCodes.Layout.of(layout.get(), w, h, keepTheme, dropped, data));
    }

    /** Tira do JSON os bytes das imagens ({@link TvCodes#PICTURE_DATA}); um item que não lê fica de fora. */
    private static Map<String, byte[]> pictureData(JsonObject root) {
        if (!(root.remove(TvCodes.PICTURE_DATA) instanceof JsonArray list)) {
            return Map.of();
        }
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (int i = 0; i < list.size() && out.size() < TvLayout.MAX_PICTURES; i++) {
            if (list.get(i) instanceof JsonObject item && item.get("hash") instanceof JsonPrimitive hash
                    && item.get("data") instanceof JsonPrimitive bytes) {
                try {
                    out.put(hash.getAsString(), Base64.getMimeDecoder().decode(bytes.getAsString()));
                } catch (IllegalArgumentException e) {
                    // Base64 estragado: a imagem fica só como referência.
                }
            }
        }
        return out;
    }

    /** Aplica as ajudas no JSON lido; devolve quantas ligações não leram e saíram. */
    private static int fix(JsonObject root) throws Bad {
        JsonObject theme = object(root, "theme");
        themeIn(theme);
        colorsIn(theme, THEME_COLORS);
        noRoles(theme, THEME_COLORS);
        JsonObject background = object(root, "background");
        colorsIn(background, BACKGROUND_COLORS);
        idIn(background, "image");
        JsonArray images = root.get("images") instanceof JsonArray array ? array : null;
        for (int i = 0; images != null && i < images.size(); i++) {
            if (images.get(i) instanceof JsonObject art) {
                images.set(i, artIn(art, i + 1));
            }
        }
        int dropped = 0;
        List<JsonObject> pieces = objects(root, "pieces");
        for (int i = 0; i < pieces.size(); i++) {
            JsonObject piece = pieces.get(i);
            JsonObject style = object(piece, "style");
            colorsIn(style, STYLE_COLORS);
            idIn(style, "image");
            dropped += bindIn(piece);
            for (JsonObject rule : objects(piece, "rules")) {
                colorsIn(rule, RULE_COLORS);
                dropped += bindIn(rule);
            }
            if (TvPiece.CODEC.parse(JsonOps.INSTANCE, piece).error().isPresent()) {
                throw new Bad("code.layout.piece", i + 1);
            }
        }
        return dropped;
    }

    /** A ligação da peça ou da regra: o id da rede em texto vira UUID; uma ligação que não lê sai (devolve 1). */
    private static int bindIn(JsonObject owner) throws Bad {
        JsonObject bind = object(owner, "bind");
        if (bind == null) {
            return owner.has("bind") ? remove(owner) : 0;
        }
        idIn(bind, "network");
        return TvBinding.CODEC.parse(JsonOps.INSTANCE, bind).error().isPresent() ? remove(owner) : 0;
    }

    private static int remove(JsonObject owner) {
        owner.remove("bind");
        return 1;
    }

    /**
     * O tema com chave faltando: o nome vazio e cada cor que falta vêm do Grafite. Sem isso, o codec (que pede as
     * sete) largaria o tema inteiro e a Tela cairia calada no Grafite.
     */
    private static void themeIn(@Nullable JsonObject theme) {
        if (theme == null) {
            return;
        }
        if (!theme.has("name")) {
            theme.addProperty("name", "");
        }
        for (int i = 0; i < THEME_KEYS.size(); i++) {
            if (!theme.has(THEME_KEYS.get(i))) {
                theme.addProperty(THEME_KEYS.get(i), TvTheme.GRAPHITE.color(i + 1));
            }
        }
    }

    /** As cores do tema são fixas: um papel ({@code "tema:texto"} ou o número de 1 a 6) não vale ali. */
    private static void noRoles(@Nullable JsonObject object, Set<String> keys) throws Bad {
        if (object == null) {
            return;
        }
        for (String key : keys) {
            if (object.get(key) instanceof JsonPrimitive primitive && primitive.isNumber()
                    && TvColor.isTheme(primitive.getAsInt())) {
                throw new Bad("code.layout.theme_role", key);
            }
        }
    }

    private static void colorsIn(@Nullable JsonObject object, Set<String> keys) throws Bad {
        if (object == null) {
            return;
        }
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                object.addProperty(key, color(primitive.getAsString()));
            } else if (value != null && !(value instanceof JsonPrimitive primitive && primitive.isNumber())) {
                throw new Bad("code.layout.color", value.toString());
            }
        }
    }

    /** A cor de um texto: {@code #RRGGBB}, {@code #RRGGBBAA}, {@code tema:…}/{@code theme:…} ou transparente. */
    static int color(String text) throws Bad {
        String value = text.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("transparente") || value.equals("transparent")) {
            return 0;
        }
        int colon = value.indexOf(':');
        if (colon > 0 && (value.startsWith("tema:") || value.startsWith("theme:"))) {
            String role = value.substring(colon + 1).strip().replace('ã', 'a');
            for (int i = 1; i < ROLES_PT.length; i++) {
                if (role.equals(ROLES_PT[i]) || role.equals(ROLES_EN[i]) || i == TvColor.BG && role.equals("bg")) {
                    return TvColor.theme(i);
                }
            }
            throw new Bad("code.layout.color", text);
        }
        Integer hex = TvColor.fromHex(value);
        if (hex == null) {
            throw new Bad("code.layout.color", text);
        }
        return hex;
    }

    /** O id em texto vira o UUID em 4 inteiros, como o codec lê; um texto que não é UUID vira o id do nome. */
    private static void idIn(@Nullable JsonObject object, String key) {
        if (object != null && object.get(key) instanceof JsonPrimitive primitive && primitive.isString()) {
            UUID id = id(primitive.getAsString());
            JsonArray array = new JsonArray(4);
            array.add((int) (id.getMostSignificantBits() >> 32));
            array.add((int) id.getMostSignificantBits());
            array.add((int) (id.getLeastSignificantBits() >> 32));
            array.add((int) id.getLeastSignificantBits());
            object.add(key, array);
        }
    }

    static UUID id(String text) {
        try {
            return UUID.fromString(text.strip());
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(text.strip().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * A arte legível vira a empacotada, que o codec lê; a empacotada só ganha o id em texto convertido. Um erro da
     * arte legível diz qual arte ({@link #artError}); {@code number} é a posição dela em {@code images}, de 1 em diante.
     */
    private static JsonElement artIn(JsonObject art, int number) throws Bad {
        JsonElement rows = first(art, "linhas", "rows");
        if (rows == null) {
            idIn(art, "id");
            return art;
        }
        String name = text(first(art, "nome", "name"));
        TvArtText.Parsed parsed = TvArtText.build(name, nullableText(first(art, "tamanho", "size")), 0,
                list(first(art, "paleta", "palette"), true), 0, rows(rows));
        if (parsed.art() == null) {
            throw artError(parsed, artLabel(art, name, number));
        }
        JsonElement idText = art.get("id");
        UUID id = idText instanceof JsonPrimitive p && p.isString() ? id(p.getAsString()) : id(name);
        TvArtText.Art plain = parsed.art();
        TvPixelArt full = new TvPixelArt(id, plain.name(), plain.width(), plain.height(), plain.palette(),
                plain.pixels());
        return TvPixelArt.CODEC.encodeStart(JsonOps.INSTANCE, full).getOrThrow();
    }

    /** O nome da arte para o erro: o nome, ou o id em texto, ou "#N" pela posição. */
    private static String artLabel(JsonObject art, String name, int number) {
        if (!name.isBlank()) {
            return name.strip();
        }
        String id = nullableText(art.get("id"));
        return id != null && !id.isBlank() ? id.strip() : "#" + number;
    }

    /**
     * O erro da arte dentro do JSON: a chave {@code code.layout.art.*} com o nome da arte primeiro. A linha que o
     * {@link TvArtText} põe no erro do texto solto sai (no JSON ela é 0); a fileira fica, contada nas {@code linhas}
     * da arte.
     */
    private static Bad artError(TvArtText.Parsed parsed, String label) {
        String key = parsed.error() == null ? "code.invalid" : parsed.error();
        if (!key.startsWith("code.art.")) {
            return new Bad(key, parsed.args());
        }
        Object[] args = parsed.args();
        int skip = ART_LINE_ERRORS.contains(key) && args.length > 0 ? 1 : 0;
        Object[] out = new Object[args.length - skip + 1];
        out[0] = label;
        System.arraycopy(args, skip, out, 1, args.length - skip);
        return new Bad("code.layout.art." + key.substring("code.art.".length()), out);
    }

    private static List<TvArtText.Row> rows(JsonElement element) {
        List<String> lines = list(element, false);
        List<TvArtText.Row> rows = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            rows.add(new TvArtText.Row(lines.get(i), i + 1));
        }
        return rows;
    }

    /** Uma lista de textos; a paleta também vale como um texto só, separado por espaços. */
    private static List<String> list(@Nullable JsonElement element, boolean split) {
        List<String> out = new ArrayList<>();
        if (element instanceof JsonArray array) {
            for (JsonElement item : array) {
                out.add(text(item));
            }
        } else if (element != null && split && !text(element).isBlank()) {
            out.addAll(List.of(text(element).strip().split("\\s+")));
        }
        return out;
    }

    private static String text(@Nullable JsonElement element) {
        String text = nullableText(element);
        return text == null ? "" : text;
    }

    private static @Nullable String nullableText(@Nullable JsonElement element) {
        return element instanceof JsonPrimitive primitive ? primitive.getAsString() : null;
    }

    private static @Nullable JsonElement first(JsonObject object, String pt, String en) {
        JsonElement value = object.get(pt);
        return value != null ? value : object.get(en);
    }

    private static int integer(@Nullable JsonElement element) {
        return element instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsInt() : 0;
    }

    private static @Nullable JsonObject object(@Nullable JsonObject parent, String key) {
        return parent != null && parent.get(key) instanceof JsonObject object ? object : null;
    }

    private static List<JsonObject> objects(JsonObject parent, String key) {
        if (!(parent.get(key) instanceof JsonArray array)) {
            return List.of();
        }
        List<JsonObject> out = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element instanceof JsonObject object) {
                out.add(object);
            }
        }
        return out;
    }
}
