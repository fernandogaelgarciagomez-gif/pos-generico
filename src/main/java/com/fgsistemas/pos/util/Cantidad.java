package com.fgsistemas.pos.util;

import com.fgsistemas.pos.model.TipoVenta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cantidades en milésimas ({@code long}): 1 pieza = 1000, 0.250 kg = 250, 1.5 m = 1500.
 * Aplica a stock, mínimos, cantidades vendidas/recibidas y movimientos de inventario.
 */
public final class Cantidad {

    /** Una unidad completa (1 pza, 1 kg, 1 m) en milésimas. */
    public static final long UNO = 1000;
    /**
     * Máximo que se puede capturar de una vez: 999,999 unidades.
     * DECISION (Fase 9, errores humanos): un código de barras (8–13 dígitos) escaneado por error en un campo
     * de cantidad queda fuera de rango y se rechaza en vez de meter millones de piezas.
     */
    public static final long MAXIMO = 999_999L * UNO;

    private static final Locale MX = Locale.forLanguageTag("es-MX");
    private static final BigDecimal MIL = BigDecimal.valueOf(UNO);
    public static final String MENSAJE_MAXIMO =
            "La cantidad es demasiado grande. ¿Se escaneó un código en el campo de cantidad?";
    private static final Pattern CANTIDAD = Pattern.compile("\\d+(\\.\\d{0,3})?|\\.\\d{1,3}");

    private Cantidad() {
    }

    /**
     * Aviso (no error) cuando una cantidad es rara para una sola captura: más de 500 piezas o más de
     * 100 kg / m / l. El que captura confirma; así se atrapa "50" en vez de "5" o "1000" en vez de "1.000".
     *
     * @return el texto del aviso, o null si la cantidad es normal
     */
    public static String avisoCantidadInusual(long milesimas, TipoVenta tipo, String unidad) {
        long limite = (tipo == null || tipo == TipoVenta.PIEZA ? 500 : 100) * UNO;
        if (milesimas <= limite) {
            return null;
        }
        return "Vas a capturar " + formatear(milesimas, tipo == null ? TipoVenta.PIEZA : tipo, unidad)
                + ". Es una cantidad poco común.\n\n¿Es correcta?";
    }

    public static long dePiezas(long piezas) {
        return Math.multiplyExact(piezas, UNO);
    }

    /**
     * Texto capturado → milésimas. No acepta negativos (los ajustes indican el signo aparte).
     * PIEZA solo acepta enteros; PESO/FRACCION hasta 3 decimales.
     *
     * @throws IllegalArgumentException con mensaje listo para mostrar al usuario
     */
    public static long parsear(String texto, TipoVenta tipo) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Escribe una cantidad.");
        }
        String limpio = texto.trim().replace(",", "").replace(" ", "");
        if (!CANTIDAD.matcher(limpio).matches()) {
            throw new IllegalArgumentException("Cantidad inválida: " + texto.trim());
        }
        long milesimas;
        try {
            milesimas = new BigDecimal(limpio).movePointRight(3).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Cantidad inválida: " + texto.trim());
        }
        if (milesimas > MAXIMO) {
            throw new IllegalArgumentException(MENSAJE_MAXIMO);
        }
        if (!tipo.aceptaDecimales() && milesimas % UNO != 0) {
            throw new IllegalArgumentException("Este producto se vende por pieza.");
        }
        return milesimas;
    }

    /** 3000 PIEZA → "3"; 250 PESO → "0.250"; 12350000 PESO → "12,350.000". */
    public static String formatear(long milesimas, TipoVenta tipo) {
        BigDecimal valor = BigDecimal.valueOf(milesimas, 3);
        if (!tipo.aceptaDecimales() && milesimas % UNO == 0) {
            return formato("#,##0").format(valor);
        }
        return formato("#,##0.000").format(valor);
    }

    /** Con unidad: "0.250 kg", "3 pza". */
    public static String formatear(long milesimas, TipoVenta tipo, String unidad) {
        String u = (unidad == null || unidad.isBlank()) ? tipo.unidadDefault() : unidad;
        return formatear(milesimas, tipo) + " " + u;
    }

    /** Para precargar campos editables (sin separador de miles). */
    public static String paraCampo(long milesimas, TipoVenta tipo) {
        BigDecimal valor = BigDecimal.valueOf(milesimas, 3);
        if (!tipo.aceptaDecimales() && milesimas % UNO == 0) {
            return valor.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
        }
        return valor.toPlainString();
    }

    /** Importe en centavos = precio por unidad × cantidad, redondeado HALF_UP. */
    public static long importe(long precioCentavos, long milesimas) {
        return BigDecimal.valueOf(precioCentavos)
                .multiply(BigDecimal.valueOf(milesimas))
                .divide(MIL, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * Venta por importe ("$50 de jamón"): cantidad en milésimas que corresponde a
     * {@code importeCentavos} al precio dado, redondeada HALF_UP a 3 decimales.
     * El importe real se recalcula con {@link #importe(long, long)} sobre esta cantidad.
     */
    public static long porImporte(long importeCentavos, long precioCentavos) {
        if (precioCentavos <= 0) {
            throw new IllegalArgumentException("El producto no tiene precio.");
        }
        long q = BigDecimal.valueOf(importeCentavos)
                .multiply(MIL)
                .divide(BigDecimal.valueOf(precioCentavos), 0, RoundingMode.HALF_UP)
                .longValueExact();
        if (q > MAXIMO) {
            throw new IllegalArgumentException(MENSAJE_MAXIMO);
        }
        return q;
    }

    private static DecimalFormat formato(String patron) {
        return new DecimalFormat(patron, DecimalFormatSymbols.getInstance(MX));
    }
}
