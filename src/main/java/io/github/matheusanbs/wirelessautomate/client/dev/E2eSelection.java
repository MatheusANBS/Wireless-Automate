package io.github.matheusanbs.wirelessautomate.client.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Seleção do e2e (capturas, variantes e perfil) a partir das variáveis de ambiente; lógica pura. */
final class E2eSelection {
    /** Os padrões de um perfil: o modo das capturas, o das variantes e se as suítes opcionais entram. */
    record Profile(String capture, String variants, boolean optional) {
    }

    private static final Profile FAST = new Profile("off", "off", false);
    private static final Profile FULL = new Profile("todas", "todas", true);

    private E2eSelection() {
    }

    /** Separa por vírgula, tira espaços e vazios e põe em minúsculas. */
    static List<String> parseList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.split(",")) {
            String item = part.trim().toLowerCase(Locale.ROOT);
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }

    /** {@code off} nunca, {@code todas} sempre, senão o nome do arquivo começa por algum prefixo da lista. */
    static boolean captureWanted(String mode, String file) {
        List<String> prefixes = parseList(mode);
        if (prefixes.isEmpty() || prefixes.contains("off")) {
            return false;
        }
        if (prefixes.contains("todas")) {
            return true;
        }
        for (String prefix : prefixes) {
            if (file.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** {@code off} não, {@code todas} sim, senão a variante está na lista. */
    static boolean variantOn(String mode, String variant) {
        List<String> list = parseList(mode);
        if (list.isEmpty() || list.contains("off")) {
            return false;
        }
        return list.contains("todas") || list.contains(variant.toLowerCase(Locale.ROOT));
    }

    /** {@code WA_E2E_ONLY=lista}: só mostra as suítes e não roda nenhuma. */
    static boolean listOnly(String only) {
        return parseList(only).contains("lista");
    }

    /**
     * Os nomes das suítes que rodam, na ordem de {@code available} (a do registro) e não na da lista pedida. Vazio ou
     * {@code todas} pega as que não são opcionais (todas, com o perfil completo); nomes explícitos valem mesmo sendo
     * opcionais; {@code skip} tira. Um nome desconhecido, em {@code only} ou em {@code skip}, é erro.
     */
    static List<String> selectSuites(String only, String skip, boolean full, List<String> available,
            Set<String> optional) {
        List<String> wanted = parseList(only);
        List<String> skipped = parseList(skip);
        for (String name : wanted) {
            if (!name.equals("todas") && !available.contains(name)) {
                throw unknown(name, available);
            }
        }
        for (String name : skipped) {
            if (!available.contains(name)) {
                throw unknown(name, available);
            }
        }
        boolean all = wanted.isEmpty() || wanted.contains("todas");
        List<String> out = new ArrayList<>();
        for (String name : available) {
            boolean chosen = all ? full || !optional.contains(name) : wanted.contains(name);
            if (all && wanted.contains(name)) {
                chosen = true;
            }
            if (chosen && !skipped.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }

    private static IllegalArgumentException unknown(String name, List<String> available) {
        return new IllegalArgumentException("suíte desconhecida: " + name + "; as válidas: " + String.join(", ", available));
    }

    /** O perfil pelo nome; o desconhecido é o rápido. */
    static Profile profile(String name) {
        return name != null && name.trim().equalsIgnoreCase("completo") ? FULL : FAST;
    }
}
