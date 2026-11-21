package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.VentaDao;
import com.fgsistemas.pos.dao.VentaDao.Totales;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Sesion;

import java.sql.Connection;
import java.sql.SQLException;

/** Cifras de ventas. Se completa en la Fase 7 (Resumen del día). */
public class ResumenService {

    private final VentaDao ventaDao = new VentaDao();

    /** Vendido en el turno actual (contador del menú). */
    public Totales vendidoTurno() throws SQLException {
        if (!Sesion.haySesion() || Sesion.turno() == null) {
            return new Totales(0, 0);
        }
        try (Connection c = Database.conectar()) {
            return ventaDao.delTurno(c, Sesion.turno().getId());
        }
    }

    /** Vendido hoy en todos los turnos. Solo admin (el cajero solo ve su turno). */
    public Totales vendidoHoy() throws SQLException {
        Sesion.requiere(Permiso.VER_CORTES_PASADOS);
        try (Connection c = Database.conectar()) {
            return ventaDao.delDia(c, Fechas.ahora().toLocalDate());
        }
    }
}
