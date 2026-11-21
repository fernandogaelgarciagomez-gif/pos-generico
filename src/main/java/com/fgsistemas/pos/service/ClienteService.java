package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.ClienteDao;
import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.util.Sesion;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Clientes. En la Fase 5 solo lo necesario para vender a fiado; la Fase 6 lo completa. */
public class ClienteService {

    private final ClienteDao clienteDao = new ClienteDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    public List<Cliente> listarActivos() throws SQLException {
        if (!Sesion.tiene(Permiso.FIADOS_VENDER) && !Sesion.tiene(Permiso.FIADOS_ABONAR)) {
            throw new PermisoDenegadoException(Permiso.FIADOS_VENDER);
        }
        try (Connection c = Database.conectar()) {
            return clienteDao.listarActivos(c);
        }
    }

    /** Alta rápida desde Vender: nombre obligatorio, teléfono opcional. */
    public Cliente crearRapido(String nombre, String telefono) throws SQLException {
        Sesion.requiere(Permiso.FIADOS_VENDER);
        Validacion.exigir(Validacion.nombre(nombre));
        Cliente c = new Cliente();
        c.setNombre(nombre.trim());
        c.setTelefono(telefono);
        long usuarioId = Sesion.usuario().getId();
        return Database.enTransaccion(con -> {
            clienteDao.insertar(con, c);
            bitacoraDao.registrar(con, usuarioId, "CLIENTE_CREADO", c.getNombre());
            return c;
        });
    }
}
