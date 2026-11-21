package com.fgsistemas.pos.service;

/**
 * Autorización de un administrador para UNA acción hecha desde la sesión de un cajero
 * (cancelar, descuento, retiro de caja). Se obtiene con {@link AuthService#autorizarAdmin}.
 */
public record Autorizacion(long adminId, String adminNombre) {
}
