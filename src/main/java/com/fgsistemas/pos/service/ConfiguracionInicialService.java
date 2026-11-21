package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.NegocioDao;
import com.fgsistemas.pos.dao.UsuarioDao;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Rol;
import com.fgsistemas.pos.model.Usuario;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.SQLException;

/** Primera ejecución: crear el admin y los datos del negocio (docs/modulos/10-bienvenida-primer-admin.md). */
public class ConfiguracionInicialService {

    private final UsuarioDao usuarioDao = new UsuarioDao();
    private final NegocioDao negocioDao = new NegocioDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    /** Datos capturados en los pasos 2 y 3 de la bienvenida. */
    public record DatosIniciales(String nombre, String usuario, String password, String confirmacion,
                                 String nombreNegocio, String telefono, String direccion) {
    }

    public boolean requiereConfiguracion() throws SQLException {
        try (Connection c = Database.conectar()) {
            return !usuarioDao.existeAdmin(c);
        }
    }

    /**
     * Crea admin + negocio en una sola transacción y registra en bitácora.
     *
     * @return el admin creado
     * @throws IllegalArgumentException si algún dato es inválido (mensaje para el usuario)
     * @throws IllegalStateException    si el sistema ya estaba configurado
     */
    public Usuario configurar(DatosIniciales d) throws SQLException {
        Validacion.exigir(Validacion.nombre(d.nombre()));
        Validacion.exigir(Validacion.usuario(d.usuario()));
        Validacion.exigir(Validacion.password(d.password()));
        Validacion.exigir(Validacion.confirmacion(d.password(), d.confirmacion()));
        Validacion.exigir(Validacion.nombreNegocio(d.nombreNegocio()));

        Usuario admin = new Usuario();
        admin.setNombre(d.nombre().trim());
        admin.setUsuario(d.usuario());
        admin.setPasswordHash(BCrypt.hashpw(d.password(), BCrypt.gensalt()));
        admin.setRol(Rol.ADMIN);

        Negocio negocio = new Negocio();
        negocio.setNombre(d.nombreNegocio().trim());
        negocio.setTelefono(d.telefono());
        negocio.setDireccion(d.direccion());

        return Database.enTransaccion(c -> {
            if (usuarioDao.existeAdmin(c)) {
                throw new IllegalStateException("El sistema ya está configurado.");
            }
            usuarioDao.insertar(c, admin);
            negocioDao.insertar(c, negocio);
            bitacoraDao.registrar(c, admin.getId(), "SISTEMA_CONFIGURADO",
                    "Sistema configurado por primera vez");
            return admin;
        });
    }
}
