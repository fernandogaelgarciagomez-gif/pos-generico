package com.fgsistemas.pos.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Dinero en centavos ({@code long}). Nunca usar double para dinero.
 * Formato de pantalla: {@code $1,234.50} (es-MX).
 */
public final class Dinero {

    private static final Locale MX = Locale.forLanguageTag("es-MX");
    private static final Pattern MONTO = Pattern.compile("-?(\\d+(\\.\\d{0,2})?|\\.\\d{1,2})");

    private Dinero() {
    }

    /** 123450 → "$1,234.50"; -1000 → "-$10.00". */
    public static String formatear(long centavos) {
        String cifra = formato("#,##0.00").format(BigDecimal.valueOf(centavos, 2).abs());
        return (centavos < 0 ? "-$" : "$") + cifra;
    }

    /** 123450 → "1234.50" (para precargar campos editables, sin $ ni comas). */
    public static String paraCampo(long centavos) {
        return BigDecimal.valueOf(centavos, 2).toPlainString();
    }

    /**
     * Texto capturado → centavos. Acepta "$1,234.5", "1234.50", "12", ".5", "-10".
     * Rechaza más de 2 decimales.
     *
     * @throws IllegalArgumentException con mensaje listo para mostrar al usuario
     */
    public static long parsear(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Escribe un monto.");
        }
        String limpio = texto.trim().replace("$", "").replace(",", "").replace(" ", "");
        if (!MONTO.matcher(limpio).matches()) {
            throw new IllegalArgumentException("Monto inválido: " + texto.trim());
        }
        try {
            return new BigDecimal(limpio).movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Monto inválido: " + texto.trim());
        }
    }

    /** Porcentaje (0–100) de un monto, redondeado HALF_UP a centavos. */
    public static long porcentaje(long centavos, BigDecimal porcentaje) {
        return BigDecimal.valueOf(centavos)
                .multiply(porcentaje)
                .divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static DecimalFormat formato(String patron) {
        // DecimalFormat no es thread-safe: una instancia por llamada.
        return new DecimalFormat(patron, DecimalFormatSymbols.getInstance(MX));
    }
}
