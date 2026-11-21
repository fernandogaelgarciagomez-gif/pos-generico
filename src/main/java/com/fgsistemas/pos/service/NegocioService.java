package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.NegocioDao;
import com.fgsistemas.pos.model.Negocio;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** Lectura de los datos del negocio. La edición llega en la Fase 8 (Configuración). */
public class NegocioService {

    private final NegocioDao negocioDao = new NegocioDao();

    public Optional<Negocio> obtener() throws SQLException {
        try (Connection c = Database.conectar()) {
            return negocioDao.obtener(c);
        }
    }
}
