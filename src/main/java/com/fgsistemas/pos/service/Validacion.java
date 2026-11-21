package com.fgsistemas.pos.service;

import java.util.regex.Pattern;

/**
 * Reglas de validación compartidas por la UI (mensajes en vivo) y los servicios (validación real).
 * Cada método regresa el mensaje de error, o {@code null} si el valor es válido.
 */
public final class Validacion {

    public static final int PASSWORD_MIN = 6;
    private static final Pattern USUARIO = Pattern.compile("[a-z0-9._]{3,20}");

    private Validacion() {
    }

    public static String nombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "Escribe el nombre.";
        }
        if (nombre.trim().length() > 80) {
            return "Máximo 80 caracteres.";
        }
        return null;
    }

    public static String usuario(String usuario) {
        if (usuario == null || usuario.isEmpty()) {
            return "Escribe un usuario.";
        }
        if (usuario.contains(" ")) {
            return "Sin espacios.";
        }
        if (!usuario.equals(usuario.toLowerCase())) {
            return "Solo minúsculas.";
        }
        if (usuario.length() < 3 || usuario.length() > 20) {
            return "De 3 a 20 caracteres.";
        }
        if (!USUARIO.matcher(usuario).matches()) {
            return "Solo letras sin acento, números, punto o guion bajo.";
        }
        return null;
    }

    public static String password(String password) {
        if (password == null || password.length() < PASSWORD_MIN) {
            return "Mínimo " + PASSWORD_MIN + " caracteres.";
        }
        return null;
    }

    public static String confirmacion(String password, String confirmacion) {
        if (confirmacion == null || confirmacion.isEmpty()) {
            return "Vuelve a escribir la contraseña.";
        }
        if (!confirmacion.equals(password)) {
            return "Las contraseñas no coinciden.";
        }
        return null;
    }

    public static String nombreNegocio(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "El nombre del negocio es obligatorio.";
        }
        if (nombre.trim().length() > 80) {
            return "Máximo 80 caracteres.";
        }
        return null;
    }

    private static final Pattern CODIGO = Pattern.compile("[A-Za-z0-9._\\-]{1,40}");

    public static String codigoProducto(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return "Escribe el código (de barras o clave interna).";
        }
        if (!CODIGO.matcher(codigo.trim()).matches()) {
            return "Código: sin espacios; solo letras, números, punto, guion o guion bajo (máx. 40).";
        }
        return null;
    }

    public static String nombreProducto(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "Escribe el nombre del producto.";
        }
        if (nombre.trim().length() > 120) {
            return "Máximo 120 caracteres.";
        }
        return null;
    }

    /** Lanza IllegalArgumentException con el mensaje si {@code error} no es nulo. */
    static void exigir(String error) {
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }
}
