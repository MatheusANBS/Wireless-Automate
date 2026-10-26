package io.github.matheusanbs.wirelessautomate.client.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    /** O perfil pelo nome; o desconhecido é o rápido. */
    static Profile profile(String name) {
        return name != null && name.trim().equalsIgnoreCase("completo") ? FULL : FAST;
    }
}
