package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.AbonoDao;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.CorteDao;
import com.fgsistemas.pos.dao.MovimientoCajaDao;
import com.fgsistemas.pos.dao.ResumenDao;
import com.fgsistemas.pos.dao.ResumenDao.Filtro;
import com.fgsistemas.pos.dao.TurnoDao;
import com.fgsistemas.pos.dao.VentaDao;
import com.fgsistemas.pos.model.Corte;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.ResumenCaja;
import com.fgsistemas.pos.model.Turno;
import com.fgsistemas.pos.model.Venta;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Fechas;
import com.fgsistemas.pos.util.Sesion;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumen del día (17), corte de caja (18) y cortes pasados (19).
 * Todas las cifras salen de {@link ResumenDao}, así el resumen y el corte siempre coinciden.
 */
public class CorteService {

    /** Ventas, movimientos de caja y abonos de un turno (detalle de un corte pasado). */
    public record DetalleTurno(List<Venta.Resumen> ventas, List<MovimientoCajaDao.Movimiento> movimientos,
                               List<AbonoDao.Abono> abonos) {
    }

    private final ResumenDao resumenDao = new ResumenDao();
    private final CorteDao corteDao = new CorteDao();
    private final TurnoDao turnoDao = new TurnoDao();
    private final VentaDao ventaDao = new VentaDao();
    private final MovimientoCajaDao cajaDao = new MovimientoCajaDao();
    private final AbonoDao abonoDao = new AbonoDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    /**
     * DECISION: la ganancia revela costos, así que la ve quien ve costos (EDITAR_INVENTARIO = admin).
     * El cajero nunca la recibe del servicio (llega null), no solo se oculta en pantalla.
     */
    public static boolean puedeVerGanancia() {
        return Sesion.tiene(Permiso.EDITAR_INVENTARIO);
    }

    // ---------------------------------------------------------------- resumen del día

    /** Cifras del turno actual, sin cerrarlo. */
    public ResumenCaja resumenTurnoActual() throws SQLException {
        Sesion.requiere(Permiso.VER_RESUMEN_DIA);
        if (Sesion.turno() == null) {
            throw new IllegalStateException("No hay turno abierto.");
        }
        try (Connection c = Database.conectar()) {
            return resumenDao.calcular(c, Filtro.turno(Sesion.turno().getId()), puedeVerGanancia());
        }
    }

    /** Cifras de todo un día (todos los turnos). Solo admin. */
    public ResumenCaja resumenDia(LocalDate dia) throws SQLException {
        Sesion.requiere(Permiso.VER_RESUMEN_DIA);
        Sesion.requiere(Permiso.VER_CORTES_PASADOS);
        try (Connection c = Database.conectar()) {
            return resumenDao.calcular(c, Filtro.dia(dia), puedeVerGanancia());
        }
    }

    // ---------------------------------------------------------------- corte

    /** ¿El turno es el de la sesión? Si no, es un turno ajeno (lo abrió otro y no se está trabajando en él). */
    private static boolean esTurnoDeLaSesion(long turnoId) {
        return Sesion.turno() != null && Sesion.turno().getId() == turnoId;
    }

    /** Valida permisos y que el turno siga abierto. @return el turno y el id del admin que autorizó (o null). */
    private Turno validar(Connection c, long turnoId, Autorizacion autorizacion) throws SQLException {
        Sesion.requiere(Permiso.HACER_CORTE);
        if (!esTurnoDeLaSesion(turnoId)) {
            Autorizaciones.verificar(c, Permiso.CORTE_TURNO_AJENO, autorizacion);
        }
        Turno t = turnoDao.obtener(c, turnoId).orElseThrow(() -> new IllegalArgumentException("El turno no existe."));
        if (t.getEstado() != Turno.Estado.ABIERTO) {
            throw new IllegalStateException("El turno ya tiene corte.");
        }
        return t;
    }

    /**
     * Paso 2: cuánto debería haber y la diferencia contra lo contado, sin guardar nada.
     *
     * @param autorizacion solo para el turno de otro usuario cuando no se tiene CORTE_TURNO_AJENO
     */
    public Corte vistaPrevia(long turnoId, long contado, Autorizacion autorizacion) throws SQLException {
        validarContado(contado);
        try (Connection c = Database.conectar()) {
            Turno t = validar(c, turnoId, autorizacion);
            ResumenCaja r = resumenDao.calcular(c, Filtro.turno(turnoId), puedeVerGanancia());
            return Corte.de(t, r, contado, null, Sesion.usuario().getNombre(), Fechas.ahora());
        }
    }

    /**
     * Paso 3: guarda el corte y cierra el turno en UNA transacción (las cifras se recalculan aquí,
     * con los datos finales). Si la diferencia no es cero, la nota es obligatoria.
     */
    public Corte cerrarTurno(long turnoId, long contado, String notas, Autorizacion autorizacion) throws SQLException {
        validarContado(contado);
        long usuarioId = Sesion.usuario().getId();
        String usuarioNombre = Sesion.usuario().getNombre();
        String nota = notas == null || notas.isBlank() ? null : notas.trim();
        Corte guardado = Database.enTransaccion(c -> {
            Turno t = validar(c, turnoId, autorizacion);
            Long autorizo = esTurnoDeLaSesion(turnoId) ? null
                    : Autorizaciones.verificar(c, Permiso.CORTE_TURNO_AJENO, autorizacion);
            ResumenCaja r = resumenDao.calcular(c, Filtro.turno(turnoId), true);
            Corte k = Corte.de(t, r, contado, nota, usuarioNombre, Fechas.ahora());
            if (k.diferencia() != 0 && nota == null) {
                throw new IllegalArgumentException("Hay una diferencia de " + Dinero.formatear(k.diferencia())
                        + ": escribe una nota que la explique.");
            }
            long id = corteDao.insertar(c, k, usuarioId, r.ganancia());
            if (!turnoDao.cerrar(c, turnoId)) {
                throw new IllegalStateException("El turno ya tiene corte.");
            }
            bitacoraDao.registrar(c, usuarioId, "CORTE", "Turno #" + turnoId + " de " + t.getUsuarioNombre()
                    + " · esperado " + Dinero.formatear(k.efectivoEsperado()) + " · contado "
                    + Dinero.formatear(contado) + " · diferencia " + Dinero.formatear(k.diferencia())
                    + (autorizo == null ? "" : " · autorizó admin #" + autorizo));
            return corteDao.obtener(c, id, puedeVerGanancia()).orElseThrow();
        });
        if (esTurnoDeLaSesion(turnoId)) {
            Sesion.setTurno(null);
        }
        new RespaldoService().automaticoDespuesDeCorte(); // nunca falla el corte (23-respaldos.md)
        return guardado;
    }

    private static void validarContado(long contado) {
        if (contado < 0) {
            throw new IllegalArgumentException("El efectivo contado no puede ser negativo.");
        }
    }

    /** Corte en PDF (carpeta de exportaciones). */
    public java.nio.file.Path exportarPdf(Corte k) throws SQLException, java.io.IOException {
        Sesion.requiere(Permiso.HACER_CORTE);
        com.fgsistemas.pos.model.Negocio negocio;
        try (Connection c = Database.conectar()) {
            negocio = new com.fgsistemas.pos.dao.NegocioDao().obtener(c)
                    .orElseGet(com.fgsistemas.pos.model.Negocio::new);
        }
        return new CorteExport().pdf(k, negocio);
    }

    // ---------------------------------------------------------------- cortes pasados

    public List<Corte> listar(LocalDate desde, LocalDate hasta, Long usuarioId) throws SQLException {
        Sesion.requiere(Permiso.VER_CORTES_PASADOS);
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new IllegalArgumentException("Revisa el rango de fechas.");
        }
        try (Connection c = Database.conectar()) {
            return corteDao.listar(c, desde, hasta, usuarioId, puedeVerGanancia());
        }
    }

    public Corte obtener(long corteId) throws SQLException {
        Sesion.requiere(Permiso.VER_CORTES_PASADOS);
        try (Connection c = Database.conectar()) {
            return corteDao.obtener(c, corteId, puedeVerGanancia())
                    .orElseThrow(() -> new IllegalArgumentException("El corte no existe."));
        }
    }

    public DetalleTurno detalle(long turnoId) throws SQLException {
        Sesion.requiere(Permiso.VER_CORTES_PASADOS);
        try (Connection c = Database.conectar()) {
            return new DetalleTurno(ventaDao.listarDelTurno(c, turnoId), cajaDao.delTurno(c, turnoId),
                    abonoDao.delTurno(c, turnoId));
        }
    }
}
