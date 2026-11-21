package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Rol;
import com.fgsistemas.pos.model.Usuario;
import com.fgsistemas.pos.service.ConfiguracionInicialService.DatosIniciales;
import com.fgsistemas.pos.util.Sesion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Criterios de la Fase 3: admin crea cajero; el cajero solo ve lo suyo; siempre queda un admin. */
class Fase3Test {

    @TempDir
    Path tmp;

    private final UsuarioService usuarios = new UsuarioService();
    private Usuario admin;

    @BeforeEach
    void preparar() throws Exception {
        Database.inicializar(tmp.resolve("pos.db"));
        admin = new ConfiguracionInicialService().configurar(new DatosIniciales(
                "Ana Admin", "ana", "secreta1", "secreta1", "Mi Tienda", null, null));
        login("ana", "secreta1");
    }

    @AfterEach
    void limpiar() {
        Sesion.cerrar();
    }

    private static void login(String usuario, String password) throws Exception {
        new AuthService(Clock.systemUTC()).login(usuario, password);
    }

    @Test
    void adminCreaCajeroYElCajeroEntraConSusPermisos() throws Exception {
        Usuario cajero = usuarios.crear("Luis Cajero", "luis", "cajero1", "cajero1", Rol.CAJERO);
        assertEquals(2, usuarios.listar().size());

        Sesion.cerrar();
        login("luis", "cajero1");
        assertEquals(cajero.getId(), Sesion.usuario().getId());
        long visibles = List.of(Permiso.VENDER, Permiso.VER_INVENTARIO, Permiso.ENTRADA_MERCANCIA,
                        Permiso.VER_RESUMEN_DIA, Permiso.HACER_CORTE, Permiso.VER_CORTES_PASADOS,
                        Permiso.FIADOS_VENDER, Permiso.USUARIOS, Permiso.CONFIGURACION)
                .stream().filter(Sesion::tiene).count();
        assertEquals(5, visibles, "El cajero ve 5 botones del menú");
    }

    @Test
    void cajeroQueLlamaAlServicioDirectoRecibeErrorDePermisos() throws Exception {
        usuarios.crear("Luis Cajero", "luis", "cajero1", "cajero1", Rol.CAJERO);
        Sesion.cerrar();
        login("luis", "cajero1");

        assertThrows(PermisoDenegadoException.class, usuarios::listar);
        assertThrows(PermisoDenegadoException.class,
                () -> usuarios.crear("Otro", "otro", "secreta1", "secreta1", Rol.ADMIN));
        assertThrows(PermisoDenegadoException.class, () -> usuarios.desactivar(admin.getId()));
        assertThrows(PermisoDenegadoException.class,
                () -> usuarios.restablecerPassword(admin.getId(), "x12345", "x12345"));
    }

    @Test
    void usuarioRepetidoOInvalido() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> usuarios.crear("Ana 2", "ana", "secreta1", "secreta1", Rol.CAJERO));
        assertThrows(IllegalArgumentException.class,
                () -> usuarios.crear("Pepe", "Pepe Lopez", "secreta1", "secreta1", Rol.CAJERO));
        assertThrows(IllegalArgumentException.class,
                () -> usuarios.crear("Pepe", "pepe", "123", "123", Rol.CAJERO));
    }

    @Test
    void siempreQuedaUnAdminActivo() throws Exception {
        // Ana es la única admin: no puede desactivarse ni quitarse el rol.
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> usuarios.desactivar(admin.getId()));
        assertEquals(UsuarioService.MSG_A_SI_MISMO, e1.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> usuarios.editar(admin.getId(), "Ana", Rol.CAJERO, true));

        // Con un segundo admin, ese sí puede desactivar a Ana... pero no al último.
        Usuario beto = usuarios.crear("Beto Admin", "beto", "secreta2", "secreta2", Rol.ADMIN);
        Sesion.cerrar();
        login("beto", "secreta2");
        assertDoesNotThrow(() -> usuarios.desactivar(admin.getId()));
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> usuarios.desactivar(beto.getId()));
        assertEquals(UsuarioService.MSG_A_SI_MISMO, e2.getMessage());

        // Ana desactivada no puede entrar.
        Sesion.cerrar();
        LoginException le = assertThrows(LoginException.class, () -> login("ana", "secreta1"));
        assertEquals(LoginException.Tipo.INACTIVO, le.getTipo());

        try (Connection c = Database.conectar(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM usuarios WHERE rol='ADMIN' AND activo=1")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void ultimoAdminActivoNoPierdeRolAunqueLoEditeOtro() throws Exception {
        // Defensa en profundidad: aunque quien edita no sea el mismo usuario (p. ej. una sesión
        // de un admin que ya no está activo en BD), el último admin activo no se puede degradar.
        Sesion.iniciar(new Usuario(999, "Fantasma", "fantasma", Rol.ADMIN));
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> usuarios.desactivar(admin.getId()));
        assertEquals(UsuarioService.MSG_ULTIMO_ADMIN, e1.getMessage());
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> usuarios.editar(admin.getId(), "Ana", Rol.CAJERO, true));
        assertEquals(UsuarioService.MSG_ULTIMO_ADMIN, e2.getMessage());
        // Cambiar solo el nombre del último admin sí se permite.
        Sesion.iniciar(admin);
        assertDoesNotThrow(() -> usuarios.editar(admin.getId(), "Ana María", Rol.ADMIN, true));
    }

    @Test
    void reactivarYEditarQuedanEnBitacora() throws Exception {
        Usuario luis = usuarios.crear("Luis", "luis", "cajero1", "cajero1", Rol.CAJERO);
        usuarios.desactivar(luis.getId());
        usuarios.editar(luis.getId(), "Luis Pérez", Rol.CAJERO, true);
        usuarios.restablecerPassword(luis.getId(), "nueva12", "nueva12");
        try (Connection c = Database.conectar(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT accion FROM bitacora WHERE accion LIKE 'USUARIO%' "
                     + "OR accion = 'PASSWORD_RESTABLECIDA' ORDER BY id")) {
            StringBuilder acciones = new StringBuilder();
            while (rs.next()) {
                acciones.append(rs.getString(1)).append(',');
            }
            assertEquals("USUARIO_CREADO,USUARIO_DESACTIVADO,USUARIO_EDITADO,PASSWORD_RESTABLECIDA,",
                    acciones.toString());
        }
        Sesion.cerrar();
        login("luis", "nueva12");
        assertEquals("Luis Pérez", Sesion.usuario().getNombre());
    }

    @Test
    void cambiarPropiaPassword() throws Exception {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> usuarios.cambiarPropiaPassword("mala", "nueva12", "nueva12"));
        assertEquals("La contraseña actual no es correcta.", e.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> usuarios.cambiarPropiaPassword("secreta1", "nueva12", "otra12"));

        usuarios.cambiarPropiaPassword("secreta1", "nueva12", "nueva12");
        Sesion.cerrar();
        assertThrows(LoginException.class, () -> login("ana", "secreta1"));
        login("ana", "nueva12");
        assertTrue(Sesion.haySesion());
        assertFalse(Sesion.usuario().getPasswordHash().isEmpty());
    }
}
