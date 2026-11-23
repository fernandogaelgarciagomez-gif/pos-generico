package com.fgsistemas.pos.service;

import com.fgsistemas.pos.dao.ResumenDao;
import com.fgsistemas.pos.util.Dinero;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * No se puede sacar de la caja más efectivo del que debe haber (Fase 9, errores humanos):
 * evita retiros con un cero de más y que la caja quede "en negativo" en el corte.
 */
final class EfectivoEnCaja {

    private EfectivoEnCaja() {
    }

    /** Efectivo que debe haber ahora en la caja del turno (fondo + efectivo + abonos + entradas − retiros). */
    static long disponible(Connection c, long turnoId) throws SQLException {
        return new ResumenDao().calcular(c, ResumenDao.Filtro.turno(turnoId), false).efectivoEsperado();
    }

    /** @throws IllegalArgumentException si {@code monto} es mayor que el efectivo de la caja */
    static void exigir(Connection c, long turnoId, long monto, String que) throws SQLException {
        long hay = disponible(c, turnoId);
        if (monto > hay) {
            throw new IllegalArgumentException("No alcanza el efectivo de la caja para " + que + ": se quieren sacar "
                    + Dinero.formatear(monto) + " y en la caja debe haber " + Dinero.formatear(Math.max(hay, 0)) + ".");
        }
    }
}
