package com.fgsistemas.pos.service;

import com.fgsistemas.pos.util.Dinero;

/**
 * Avisos contra errores humanos (Fase 9): cifras posibles pero sospechosas que la persona debe confirmar
 * antes de seguir ("¿Es correcto?"). No bloquean: los límites imposibles los rechazan los servicios y
 * {@code Dinero}/{@code Cantidad}.
 * <p>
 * Cada método regresa el texto del aviso, o {@code null} si la cifra es normal.
 */
public final class Avisos {

    /** Cambio a partir del cual se pregunta si el recibido es correcto ($1,000.00). */
    static final long CAMBIO_GRANDE = 100_000;
    /** Diferencia de corte a partir de la cual se sugiere volver a contar ($200.00). */
    static final long DIFERENCIA_GRANDE = 20_000;
    /** Fondo de caja que se pregunta siempre que no hay fondos anteriores ($10,000.00). */
    static final long FONDO_GRANDE = 1_000_000;

    private Avisos() {
    }

    /** Precio de venta: menor que el costo, o 5 veces mayor/menor que el anterior (¿un cero de más o de menos?). */
    public static String precio(long nuevo, long anterior, long costo) {
        if (anterior > 0 && nuevo != anterior && (nuevo >= anterior * 5 || nuevo * 5 <= anterior)) {
            return "El precio cambia de " + Dinero.formatear(anterior) + " a " + Dinero.formatear(nuevo)
                    + ". ¿Le sobra o le falta un cero?\n\n¿Es correcto?";
        }
        if (costo > 0 && nuevo < costo) {
            return "El precio (" + Dinero.formatear(nuevo) + ") es menor que el costo (" + Dinero.formatear(costo)
                    + "): cada venta deja pérdida.\n\n¿Es correcto?";
        }
        return null;
    }

    /** Costo de compra: mayor que el precio de venta, o 3 veces mayor/menor que el anterior. */
    public static String costo(long nuevo, long anterior, long precioVenta) {
        if (anterior > 0 && nuevo != anterior && (nuevo >= anterior * 3 || nuevo * 3 <= anterior)) {
            return "El costo cambia de " + Dinero.formatear(anterior) + " a " + Dinero.formatear(nuevo)
                    + ". ¿Escribiste el costo de la caja completa en vez del de una unidad?\n\n¿Es correcto?";
        }
        if (precioVenta > 0 && nuevo > precioVenta) {
            return "El costo (" + Dinero.formatear(nuevo) + ") es mayor que el precio de venta ("
                    + Dinero.formatear(precioVenta) + "): se vendería con pérdida. Después ajusta el precio en "
                    + "Inventario.\n\n¿Es correcto el costo?";
        }
        return null;
    }

    /** Fondo de caja: 5 veces mayor que el último (y $1,000 más), o muy grande si no hay fondos anteriores. */
    public static String fondo(long nuevo, Long ultimo) {
        boolean sospechoso = ultimo != null && ultimo > 0
                ? nuevo >= ultimo * 5 && nuevo - ultimo >= 100_000
                : nuevo >= FONDO_GRANDE;
        if (!sospechoso) {
            return null;
        }
        return "El fondo es de " + Dinero.formatear(nuevo)
                + (ultimo != null && ultimo > 0 ? " (el último fue de " + Dinero.formatear(ultimo) + ")" : "")
                + ".\n\n¿Es correcto?";
    }

    /** Efectivo recibido: el cambio pasa de $1,000 (¿se tecleó un cero de más?). */
    public static String cambio(long total, long recibido) {
        long cambio = recibido - total;
        if (cambio < CAMBIO_GRANDE) {
            return null;
        }
        return "Recibido " + Dinero.formatear(recibido) + " para una venta de " + Dinero.formatear(total)
                + ".\nHay que dar " + Dinero.formatear(cambio) + " de cambio.\n\n¿Es correcto lo recibido?";
    }

    /**
     * Corte: diferencia de $200 o más, o de 5 % o más de lo esperado (nunca por menos de $50).
     * Se sugiere volver a contar.
     */
    public static String diferenciaCorte(long diferencia, long esperado) {
        long abs = Math.abs(diferencia);
        if (abs < 5_000 || (abs < DIFERENCIA_GRANDE && abs * 20 < Math.abs(esperado))) {
            return null;
        }
        // Sin cifras: el conteo es a ciegas y no debe revelar cuánto debería haber.
        return "Lo contado no se parece a lo que debe haber en la caja (" + (diferencia < 0 ? "falta" : "sobra")
                + " dinero).\n\nAntes de cerrar el turno conviene volver a contar "
                + "(revisa billetes pegados, monedas y vales).\n\n¿Volver a contar?";
    }
}
