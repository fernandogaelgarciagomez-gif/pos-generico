package com.fgsistemas.pos.service;

import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Rol;
import com.fgsistemas.pos.model.Usuario;
import com.fgsistemas.pos.util.Sesion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermisosTest {

    @AfterEach
    void limpiar() {
        Sesion.cerrar();
    }

    @Test
    void adminTieneTodo() {
        Usuario admin = new Usuario(1, "Ana", "ana", Rol.ADMIN);
        for (Permiso p : Permiso.values()) {
            assertTrue(Permisos.tiene(admin, p), p.name());
        }
    }

    @Test
    void cajeroSoloLoSuyo() {
        Usuario cajero = new Usuario(2, "Luis", "luis", Rol.CAJERO);
        assertTrue(Permisos.tiene(cajero, Permiso.VENDER));
        assertTrue(Permisos.tiene(cajero, Permiso.FIADOS_ABONAR));
        assertFalse(Permisos.tiene(cajero, Permiso.EDITAR_INVENTARIO));
        assertFalse(Permisos.tiene(cajero, Permiso.CANCELAR_VENTA));
        assertFalse(Permisos.tiene(cajero, Permiso.DESCUENTOS));
        assertFalse(Permisos.tiene(cajero, Permiso.RETIROS_CAJA));
        assertFalse(Permisos.tiene(cajero, Permiso.USUARIOS));
    }

    @Test
    void usuarioInactivoNoTienePermisos() {
        Usuario admin = new Usuario(1, "Ana", "ana", Rol.ADMIN);
        admin.setActivo(false);
        assertFalse(Permisos.tiene(admin, Permiso.VENDER));
    }

    @Test
    void sesionRequiereLanzaExcepcion() {
        assertThrows(PermisoDenegadoException.class, () -> Sesion.requiere(Permiso.VENDER));
        Sesion.iniciar(new Usuario(2, "Luis", "luis", Rol.CAJERO));
        assertDoesNotThrow(() -> Sesion.requiere(Permiso.VENDER));
        assertThrows(PermisoDenegadoException.class, () -> Sesion.requiere(Permiso.CONFIGURACION));
    }
}
