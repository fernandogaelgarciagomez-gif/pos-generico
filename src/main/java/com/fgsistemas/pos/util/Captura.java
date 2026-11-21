package com.fgsistemas.pos.util;

/**
 * Lo que el cajero escribe en el campo de código de Vender:
 * <ul>
 *   <li>{@code 750100} → código</li>
 *   <li>{@code 3*750100} / {@code 0.250*JAM01} → cantidad y código</li>
 *   <li>{@code $50*JAM01} → vender por importe (lo que alcance con $50)</li>
 * </ul>
 * La cantidad queda como texto porque se interpreta según el tipo de venta del producto.
 */
public record Captura(String codigo, String cantidad, Long importe) {

    public static Captura parsear(String texto) {
        String t = texto == null ? "" : texto.trim();
        int asterisco = t.indexOf('*');
        if (asterisco < 0) {
            return new Captura(t, null, null);
        }
        String izquierda = t.substring(0, asterisco).trim();
        String codigo = t.substring(asterisco + 1).trim();
        if (codigo.isEmpty()) {
            throw new IllegalArgumentException("Falta el código después del *.");
        }
        if (izquierda.isEmpty()) {
            return new Captura(codigo, null, null);
        }
        if (izquierda.startsWith("$")) {
            long importe = Dinero.parsear(izquierda);
            if (importe <= 0) {
                throw new IllegalArgumentException("El importe debe ser mayor a cero.");
            }
            return new Captura(codigo, null, importe);
        }
        return new Captura(codigo, izquierda, null);
    }

    public boolean tieneCantidad() {
        return cantidad != null || importe != null;
    }
}
