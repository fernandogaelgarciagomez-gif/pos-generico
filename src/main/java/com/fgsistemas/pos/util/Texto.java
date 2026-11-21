package com.fgsistemas.pos.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Utilidades de texto para búsquedas (sin acentos, sin mayúsculas). */
public final class Texto {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");

    private Texto() {
    }

    /** "Jamón Pavo" → "jamon pavo". */
    public static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String sinAcentos = MARCAS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        return sinAcentos.toLowerCase(Locale.ROOT).trim();
    }

    /** ¿{@code texto} contiene TODAS las palabras de {@code busqueda} (en cualquier orden)? */
    public static boolean contieneTodas(String texto, String busqueda) {
        String t = normalizar(texto);
        for (String palabra : normalizar(busqueda).split("\\s+")) {
            if (!palabra.isEmpty() && !t.contains(palabra)) {
                return false;
            }
        }
        return true;
    }
}
