package com.fgsistemas.pos.service;

import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma un ticket para impresoras térmicas genéricas con comandos ESC/POS (Epson y compatibles: 58 y 80 mm).
 * A la vez arma una vista previa en texto plano con el mismo contenido.
 * <p>
 * DECISION: se usa la página de códigos PC850 (ESC t 2), que trae todas las vocales acentuadas, ñ, ¿ y ¡.
 * La tabla de conversión va aquí mismo (no depende del módulo de charsets extra de Java, que el instalador
 * no incluye). Si la impresora no soporta PC850 y salen símbolos raros, la opción "sin acentos" los quita.
 */
public final class EscPos {

    private static final byte ESC = 0x1B;
    private static final byte GS = 0x1D;

    private final int columnas;
    private final boolean sinAcentos;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final StringBuilder vista = new StringBuilder();

    public EscPos(int columnas, boolean sinAcentos) {
        this.columnas = columnas;
        this.sinAcentos = sinAcentos;
        comando(ESC, '@');          // reiniciar
        comando(ESC, 't', 2);       // página de códigos PC850
    }

    public int columnas() {
        return columnas;
    }

    // ------------------------------------------------------------------ texto

    /** Renglón centrado (se parte en varios si no cabe). */
    public EscPos centrado(String texto) {
        return parrafo(texto, 1, false, false);
    }

    public EscPos centradoNegrita(String texto) {
        return parrafo(texto, 1, true, false);
    }

    /** Título a doble alto y doble ancho (si cabe; si no, solo doble alto). */
    public EscPos titulo(String texto) {
        String t = texto == null ? "" : texto.trim();
        boolean dobleAncho = t.length() <= columnas / 2;
        alinear(1);
        negrita(true);
        comando(GS, '!', dobleAncho ? 0x11 : 0x01);
        int ancho = dobleAncho ? columnas / 2 : columnas;
        for (String linea : partir(t, ancho)) {
            escribir(linea);
            salto();
            vista.append(centrar(linea, columnas)).append('\n');
        }
        comando(GS, '!', 0);
        negrita(false);
        alinear(0);
        return this;
    }

    public EscPos izquierda(String texto) {
        return parrafo(texto, 0, false, false);
    }

    /** Texto a la izquierda e importe pegado a la derecha en el mismo renglón. */
    public EscPos dosColumnas(String izquierda, String derecha) {
        return dosColumnas(izquierda, derecha, false, false);
    }

    /** Igual que {@link #dosColumnas(String, String)} pero resaltado (negrita y doble alto: para el TOTAL). */
    public EscPos dosColumnasGrande(String izquierda, String derecha) {
        return dosColumnas(izquierda, derecha, true, true);
    }

    private EscPos dosColumnas(String izquierda, String derecha, boolean resaltar, boolean dobleAlto) {
        String der = derecha == null ? "" : derecha;
        String izq = izquierda == null ? "" : izquierda;
        int espacio = columnas - der.length() - 1;
        if (espacio < 4) {
            parrafo(izq, 0, resaltar, dobleAlto);
            return parrafo(der, 2, resaltar, dobleAlto);
        }
        List<String> lineas = partir(izq, espacio);
        if (resaltar) {
            negrita(true);
        }
        if (dobleAlto) {
            comando(GS, '!', 0x01);
        }
        for (int i = 0; i < lineas.size(); i++) {
            String linea = i < lineas.size() - 1 ? lineas.get(i)
                    : rellenar(lineas.get(i), columnas - der.length()) + der;
            escribir(linea);
            salto();
            vista.append(linea).append('\n');
        }
        if (dobleAlto) {
            comando(GS, '!', 0);
        }
        if (resaltar) {
            negrita(false);
        }
        return this;
    }

    public EscPos separador() {
        String linea = "-".repeat(columnas);
        escribir(linea);
        salto();
        vista.append(linea).append('\n');
        return this;
    }

    public EscPos lineaEnBlanco() {
        salto();
        vista.append('\n');
        return this;
    }

    private EscPos parrafo(String texto, int alineacion, boolean resaltar, boolean dobleAlto) {
        if (texto == null || texto.isBlank()) {
            return this;
        }
        alinear(alineacion);
        if (resaltar) {
            negrita(true);
        }
        if (dobleAlto) {
            comando(GS, '!', 0x01);
        }
        for (String linea : partir(texto.trim(), columnas)) {
            escribir(linea);
            salto();
            vista.append(switch (alineacion) {
                case 1 -> centrar(linea, columnas);
                case 2 -> " ".repeat(Math.max(0, columnas - linea.length())) + linea;
                default -> linea;
            }).append('\n');
        }
        if (dobleAlto) {
            comando(GS, '!', 0);
        }
        if (resaltar) {
            negrita(false);
        }
        alinear(0);
        return this;
    }

    // ------------------------------------------------------------------ papel y cajón

    /** Avanza el papel y lo corta (las impresoras sin cortador ignoran el comando). */
    public EscPos cortar() {
        comando(ESC, 'd', 4);       // avanzar 4 renglones para que el texto pase la cuchilla
        comando(GS, 'V', 66, 0);    // corte parcial
        vista.append("\n- - - - - - corte - - - - - -\n");
        return this;
    }

    /** Solo avanza el papel (para impresoras sin cortador, para poder arrancarlo). */
    public EscPos avanzar() {
        comando(ESC, 'd', 5);
        return this;
    }

    /** Pulso al conector RJ11 de la impresora para abrir el cajón de dinero (pin 2, ~50/500 ms). */
    public EscPos abrirCajon() {
        comando(ESC, 'p', 0, 25, 250);
        return this;
    }

    public byte[] bytes() {
        return bytes.toByteArray();
    }

    public String vistaPrevia() {
        return vista.toString();
    }

    // ------------------------------------------------------------------ bajo nivel

    private void alinear(int n) {
        comando(ESC, 'a', n);
    }

    private void negrita(boolean si) {
        comando(ESC, 'E', si ? 1 : 0);
    }

    private void salto() {
        bytes.write('\n');
    }

    private void comando(int... valores) {
        for (int v : valores) {
            bytes.write(v & 0xFF);
        }
    }

    private void escribir(String texto) {
        for (int i = 0; i < texto.length(); i++) {
            bytes.write(codificar(texto.charAt(i)));
        }
    }

    /** Carácter → byte en PC850 (o ASCII sin acentos). Lo que no existe sale como '?'. */
    int codificar(char ch) {
        if (ch >= 0x20 && ch < 0x7F) {
            return ch;
        }
        if (sinAcentos) {
            return ascii(ch);
        }
        return switch (ch) {
            case 'á' -> 0xA0;
            case 'é' -> 0x82;
            case 'í' -> 0xA1;
            case 'ó' -> 0xA2;
            case 'ú' -> 0xA3;
            case 'ñ' -> 0xA4;
            case 'Ñ' -> 0xA5;
            case 'Á' -> 0xB5;
            case 'É' -> 0x90;
            case 'Í' -> 0xD6;
            case 'Ó' -> 0xE0;
            case 'Ú' -> 0xE9;
            case 'ü' -> 0x81;
            case 'Ü' -> 0x9A;
            case '¿' -> 0xA8;
            case '¡' -> 0xAD;
            case '°' -> 0xF8;
            case 'ª' -> 0xA6;
            case 'º' -> 0xA7;
            default -> ascii(ch);
        };
    }

    private static int ascii(char ch) {
        switch (ch) {
            case ' ', ' ', ' ' -> {
                return ' ';
            }
            case '−', '–', '—' -> {
                return '-';
            }
            case '¿' -> {
                return '?';
            }
            case '¡' -> {
                return '!';
            }
            case '·', '•' -> {
                return '*';
            }
            case '°', 'º' -> {
                return 'o';
            }
            case 'ª' -> {
                return 'a';
            }
            default -> {
            }
        }
        String base = Normalizer.normalize(String.valueOf(ch), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (base.length() == 1 && base.charAt(0) >= 0x20 && base.charAt(0) < 0x7F) {
            return base.charAt(0);
        }
        return '?';
    }

    /** Parte el texto en renglones de hasta {@code ancho} caracteres, cortando en espacios cuando se puede. */
    static List<String> partir(String texto, int ancho) {
        List<String> lineas = new ArrayList<>();
        String resto = texto == null ? "" : texto.replace('\n', ' ').trim();
        while (resto.length() > ancho) {
            int corte = resto.lastIndexOf(' ', ancho);
            if (corte <= 0) {
                corte = ancho;
            }
            lineas.add(resto.substring(0, corte).trim());
            resto = resto.substring(corte).trim();
        }
        lineas.add(resto);
        return lineas;
    }

    private static String rellenar(String s, int ancho) {
        return s.length() >= ancho ? s : s + " ".repeat(ancho - s.length());
    }

    private static String centrar(String s, int ancho) {
        int izq = Math.max(0, (ancho - s.length()) / 2);
        return " ".repeat(izq) + s;
    }
}
