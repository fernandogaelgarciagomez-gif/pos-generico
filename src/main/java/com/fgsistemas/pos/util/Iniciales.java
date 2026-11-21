package com.fgsistemas.pos.util;

import java.util.Locale;

/** Iniciales y color de los mosaicos de productos en Vender. */
public final class Iniciales {

    public static final int COLORES = 8;

    private Iniciales() {
    }

    /** "Café americano" → "CA"; "Refresco" → "RE"; "Coca-Cola 600 ml" → "CC". */
    public static String de(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "?";
        }
        String[] palabras = nombre.trim().split("[^\\p{L}\\p{N}]+");
        StringBuilder sb = new StringBuilder();
        for (String p : palabras) {
            if (!p.isEmpty() && Character.isLetter(p.charAt(0))) {
                sb.append(p.charAt(0));
                if (sb.length() == 2) {
                    break;
                }
            }
        }
        if (sb.length() < 2) {
            String primera = palabras.length > 0 && !palabras[0].isEmpty() ? palabras[0] : nombre.trim();
            sb.setLength(0);
            sb.append(primera, 0, Math.min(2, primera.length()));
        }
        return sb.toString().toUpperCase(Locale.forLanguageTag("es-MX"));
    }

    /** Índice de color estable (0..7) según el nombre. */
    public static int color(String nombre) {
        return Math.floorMod(Texto.normalizar(nombre).hashCode(), COLORES);
    }
}
