package com.fgsistemas.pos.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Códigos de barras de productos genéricos (EAN-13, UPC-A, EAN-8, GTIN-14 o códigos internos).
 * <p>
 * Un lector USB genérico "teclea" el código y manda Enter, así que no hace falta controlador. Esta clase
 * limpia lo que llega y ayuda a encontrar el producto aunque el código se haya dado de alta con o sin el
 * cero inicial (un UPC-A de 12 dígitos es el mismo EAN-13 con un 0 adelante).
 */
public final class CodigoBarras {

    private CodigoBarras() {
    }

    /** Quita caracteres de control que algunos lectores mandan como prefijo/sufijo (STX, ETX, Tab…) y espacios. */
    public static String limpiar(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char ch = texto.charAt(i);
            if (!Character.isISOControl(ch)) {
                sb.append(ch);
            }
        }
        return sb.toString().trim();
    }

    public static boolean soloDigitos(String codigo) {
        if (codigo == null || codigo.isEmpty()) {
            return false;
        }
        for (int i = 0; i < codigo.length(); i++) {
            char ch = codigo.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    /** ¿El último dígito cuadra con el dígito verificador GS1? Solo para 8, 12, 13 o 14 dígitos. */
    public static boolean digitoVerificadorValido(String codigo) {
        if (!soloDigitos(codigo)) {
            return false;
        }
        int n = codigo.length();
        if (n != 8 && n != 12 && n != 13 && n != 14) {
            return false;
        }
        int suma = 0;
        // De derecha a izquierda, sin contar el verificador: posiciones impares ×3.
        for (int i = n - 2, pos = 1; i >= 0; i--, pos++) {
            int d = codigo.charAt(i) - '0';
            suma += (pos % 2 == 1) ? d * 3 : d;
        }
        int esperado = (10 - (suma % 10)) % 10;
        return esperado == codigo.charAt(n - 1) - '0';
    }

    /** Nombre del tipo de código para mostrarlo al probar el lector. */
    public static String tipo(String codigo) {
        if (!soloDigitos(codigo)) {
            return "Código interno (letras o símbolos)";
        }
        boolean valido = digitoVerificadorValido(codigo);
        String nombre = switch (codigo.length()) {
            case 8 -> "EAN-8";
            case 12 -> "UPC-A";
            case 13 -> "EAN-13";
            case 14 -> "GTIN-14 (caja)";
            default -> null;
        };
        if (nombre == null) {
            return "Código numérico interno (" + codigo.length() + " dígitos)";
        }
        return valido ? nombre : nombre + "? (el dígito verificador no cuadra: puede ser código interno o mal leído)";
    }

    /**
     * Formas equivalentes del código para buscarlo, empezando por el código tal cual.
     * UPC-A (12) ↔ EAN-13 con 0 inicial (13) ↔ GTIN-14 con 0 inicial (14).
     */
    public static List<String> variantes(String codigo) {
        String c = limpiar(codigo);
        List<String> lista = new ArrayList<>();
        if (c.isEmpty()) {
            return lista;
        }
        lista.add(c);
        if (!soloDigitos(c)) {
            return lista;
        }
        // Sin ceros iniciales sobrantes (hasta llegar a 12 dígitos) y con ellos (hasta 14).
        String corto = c;
        while (corto.length() > 12 && corto.charAt(0) == '0') {
            corto = corto.substring(1);
            agregarSiFalta(lista, corto);
        }
        if (c.length() >= 12) {
            String largo = c;
            while (largo.length() < 14) {
                largo = "0" + largo;
                agregarSiFalta(lista, largo);
            }
        }
        return lista;
    }

    private static void agregarSiFalta(List<String> lista, String s) {
        if (!lista.contains(s)) {
            lista.add(s);
        }
    }
}
