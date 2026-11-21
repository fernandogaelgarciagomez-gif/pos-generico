package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.TurnoDao;
import com.fgsistemas.pos.model.Turno;
import com.fgsistemas.pos.model.Usuario;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Sesion;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

/** Una caja, un turno abierto a la vez (docs/modulos/12-turno-fondo-caja.md). */
public class TurnoService {

    private final TurnoDao turnoDao = new TurnoDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    public Optional<Turno> turnoAbierto() throws SQLException {
        try (Connection c = Database.conectar()) {
            return turnoDao.abierto(c);
        }
    }

    /**
     * Abre un turno nuevo con el fondo inicial indicado y lo pone en la sesión.
     *
     * @throws IllegalStateException    si no hay sesión o ya hay un turno abierto
     * @throws IllegalArgumentException si el fondo es negativo
     */
    public Turno abrir(long fondoCentavos) throws SQLException {
        Usuario usuario = requiereSesion();
        if (fondoCentavos < 0) {
            throw new IllegalArgumentException("El fondo no puede ser negativo.");
        }
        Turno turno = Database.enTransaccion(c -> {
            if (turnoDao.abierto(c).isPresent()) {
                throw new IllegalStateException("Ya hay un turno abierto.");
            }
            Turno t = new Turno();
            t.setUsuarioId(usuario.getId());
            t.setUsuarioNombre(usuario.getNombre());
            t.setAbiertoEn(Fechas.ahora());
            t.setFondoInicial(fondoCentavos);
            turnoDao.insertar(c, t);
            bitacoraDao.registrar(c, usuario.getId(), "TURNO_ABIERTO",
                    "Turno #" + t.getId() + " · fondo inicial " + Dinero.formatear(fondoCentavos));
            return t;
        });
        Sesion.setTurno(turno);
        return turno;
    }

    /** Continúa en el turno abierto (propio o de otro usuario). Las ventas quedan con el usuario actual. */
    public void continuar(Turno turno) throws SQLException {
        Usuario usuario = requiereSesion();
        if (turno.getUsuarioId() != usuario.getId()) {
            try (Connection c = Database.conectar()) {
                bitacoraDao.registrar(c, usuario.getId(), "TURNO_CONTINUADO",
                        "Continúa el turno #" + turno.getId() + " de " + turno.getUsuarioNombre());
            }
        }
        Sesion.setTurno(turno);
    }

    public static boolean esDeDiaAnterior(Turno turno, LocalDate hoy) {
        return turno.getAbiertoEn() != null && turno.getAbiertoEn().toLocalDate().isBefore(hoy);
    }

    private static Usuario requiereSesion() {
        if (!Sesion.haySesion()) {
            throw new IllegalStateException("No hay sesión iniciada.");
        }
        return Sesion.usuario();
    }
}
