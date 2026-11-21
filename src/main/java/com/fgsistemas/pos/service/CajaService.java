package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.MovimientoCajaDao;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Sesion;

import java.sql.SQLException;

/** Entradas y retiros de efectivo que no son ventas (F8 en Vender). */
public class CajaService {

    private final MovimientoCajaDao cajaDao = new MovimientoCajaDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    /** Gastos (retiros) e ingresos (entradas) del turno actual, del más antiguo al más reciente. */
    public java.util.List<MovimientoCajaDao.Movimiento> movimientosTurno() throws SQLException {
        Sesion.requiere(Permiso.VENDER);
        if (Sesion.turno() == null) {
            return java.util.List.of();
        }
        try (java.sql.Connection c = Database.conectar()) {
            return cajaDao.delTurno(c, Sesion.turno().getId());
        }
    }

    /**
     * Registra el movimiento en el turno actual. Los retiros requieren permiso RETIROS_CAJA
     * o autorización de un admin.
     */
    public void registrar(MovimientoCajaDao.Tipo tipo, long monto, String concepto, Autorizacion autorizacion)
            throws SQLException {
        Sesion.requiere(Permiso.VENDER);
        if (Sesion.turno() == null) {
            throw new IllegalStateException("No hay turno abierto.");
        }
        if (monto <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero.");
        }
        if (concepto == null || concepto.isBlank()) {
            throw new IllegalArgumentException("Escribe el concepto (ej. \"Pago a proveedor de refrescos\").");
        }
        long usuarioId = Sesion.usuario().getId();
        long turnoId = Sesion.turno().getId();
        Database.enTransaccion(c -> {
            Long autorizo = tipo == MovimientoCajaDao.Tipo.RETIRO
                    ? Autorizaciones.verificar(c, Permiso.RETIROS_CAJA, autorizacion) : null;
            cajaDao.registrar(c, turnoId, usuarioId, tipo, monto, concepto.trim());
            bitacoraDao.registrar(c, usuarioId, tipo == MovimientoCajaDao.Tipo.RETIRO ? "RETIRO_CAJA" : "ENTRADA_CAJA",
                    Dinero.formatear(monto) + " · " + concepto.trim()
                            + (autorizo == null ? "" : " · autorizado por admin #" + autorizo));
            return null;
        });
    }
}
