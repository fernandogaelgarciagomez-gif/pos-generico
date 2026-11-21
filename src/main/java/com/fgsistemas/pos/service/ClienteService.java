package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.AbonoDao;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.ClienteDao;
import com.fgsistemas.pos.dao.NegocioDao;
import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.model.EstadoCuenta;
import com.fgsistemas.pos.model.MetodoPago;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Sesion;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Clientes y fiados (20-fiados-clientes.md). Sin límite de crédito.
 * Saldo = Σ parte fiada de ventas completadas − Σ abonos; negativo = saldo a favor.
 */
public class ClienteService {

    /** Resultado de un abono. */
    public record AbonoRegistrado(long abonoId, long monto, long saldoAnterior, long saldoNuevo) {
    }

    private final ClienteDao clienteDao = new ClienteDao();
    private final AbonoDao abonoDao = new AbonoDao();
    private final NegocioDao negocioDao = new NegocioDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    // ---------------------------------------------------------------- consultas

    /** Para el buscador de Vender (F4). */
    public List<Cliente> listarActivos() throws SQLException {
        requiereVer();
        try (Connection c = Database.conectar()) {
            return clienteDao.listarActivos(c);
        }
    }

    /** Lista de la pantalla Fiados. Los inactivos solo los ve quien administra clientes. */
    public List<Cliente> listar(boolean incluirInactivos) throws SQLException {
        requiereVer();
        boolean inactivos = incluirInactivos && Sesion.tiene(Permiso.FIADOS_ADMINISTRAR);
        try (Connection c = Database.conectar()) {
            return clienteDao.listarConFechas(c, inactivos);
        }
    }

    /** Saldo recalculado desde ventas y abonos (para verificar que coincide con el guardado). */
    public long saldoCalculado(long clienteId) throws SQLException {
        requiereVer();
        try (Connection c = Database.conectar()) {
            return clienteDao.saldoCalculado(c, clienteId);
        }
    }

    public Cliente obtener(long clienteId) throws SQLException {
        requiereVer();
        try (Connection c = Database.conectar()) {
            return clienteDao.buscarPorId(c, clienteId)
                    .orElseThrow(() -> new IllegalArgumentException("El cliente no existe."));
        }
    }

    /**
     * Estado de cuenta: ventas a fiado (+) y abonos (−) por fecha con saldo acumulado.
     * Las ventas canceladas se listan marcadas y no mueven el saldo.
     */
    public EstadoCuenta estadoCuenta(long clienteId) throws SQLException {
        requiereVer();
        try (Connection c = Database.conectar()) {
            Cliente cliente = clienteDao.buscarPorId(c, clienteId)
                    .orElseThrow(() -> new IllegalArgumentException("El cliente no existe."));
            List<EstadoCuenta.Movimiento> crudos = new ArrayList<>();
            for (ClienteDao.VentaFiada v : clienteDao.ventasFiadas(c, clienteId)) {
                crudos.add(new EstadoCuenta.Movimiento(EstadoCuenta.Tipo.VENTA, v.fecha(),
                        "Venta #" + v.folio() + (v.cancelada() ? " (cancelada)" : ""), v.fiado(), 0, 0,
                        v.ventaId(), v.cancelada()));
            }
            for (AbonoDao.Abono a : abonoDao.deCliente(c, clienteId)) {
                crudos.add(new EstadoCuenta.Movimiento(EstadoCuenta.Tipo.ABONO, a.fecha(),
                        "Abono (" + a.metodo().etiqueta().toLowerCase() + ")", 0, a.monto(), 0, null, false));
            }
            // Mismo segundo: primero la venta y luego el abono.
            crudos.sort(Comparator.comparing(EstadoCuenta.Movimiento::fecha)
                    .thenComparing(m -> m.tipo() == EstadoCuenta.Tipo.ABONO));
            List<EstadoCuenta.Movimiento> movimientos = new ArrayList<>();
            long saldo = 0;
            for (EstadoCuenta.Movimiento m : crudos) {
                if (!m.cancelada()) {
                    saldo += m.cargo() - m.abono();
                }
                movimientos.add(new EstadoCuenta.Movimiento(m.tipo(), m.fecha(), m.concepto(), m.cargo(), m.abono(),
                        saldo, m.ventaId(), m.cancelada()));
            }
            return new EstadoCuenta(cliente, movimientos, saldo);
        }
    }

    /** Estado de cuenta en PDF (carpeta de exportaciones). */
    public java.nio.file.Path exportarPdf(EstadoCuenta ec) throws SQLException, java.io.IOException {
        requiereVer();
        Negocio negocio;
        try (Connection c = Database.conectar()) {
            negocio = negocioDao.obtener(c).orElseGet(Negocio::new);
        }
        return new EstadoCuentaExport().pdf(ec, negocio);
    }

    /** Métodos con los que se puede abonar: efectivo siempre; tarjeta/transferencia si están habilitados. */
    public Set<MetodoPago> metodosAbono() throws SQLException {
        try (Connection c = Database.conectar()) {
            return metodosAbono(negocioDao.obtener(c).orElseGet(Negocio::new));
        }
    }

    private static Set<MetodoPago> metodosAbono(Negocio n) {
        Set<MetodoPago> s = EnumSet.of(MetodoPago.EFECTIVO);
        if (n.isMetodoTarjeta()) {
            s.add(MetodoPago.TARJETA);
        }
        if (n.isMetodoTransferencia()) {
            s.add(MetodoPago.TRANSFERENCIA);
        }
        return s;
    }

    // ---------------------------------------------------------------- alta y edición

    /** Alta rápida desde Vender: nombre obligatorio, teléfono opcional. */
    public Cliente crearRapido(String nombre, String telefono) throws SQLException {
        return crear(nombre, telefono, null);
    }

    /** Nuevo cliente (cajero y admin). */
    public Cliente crear(String nombre, String telefono, String notas) throws SQLException {
        requiereVer();
        Validacion.exigir(Validacion.nombre(nombre));
        Cliente c = new Cliente();
        c.setNombre(nombre.trim());
        c.setTelefono(limpiar(telefono));
        c.setNotas(limpiar(notas));
        long usuarioId = Sesion.usuario().getId();
        return Database.enTransaccion(con -> {
            clienteDao.insertar(con, c);
            bitacoraDao.registrar(con, usuarioId, "CLIENTE_CREADO", c.getNombre());
            return c;
        });
    }

    /** Editar nombre, teléfono y notas (solo admin). El saldo no se edita. */
    public void editar(long clienteId, String nombre, String telefono, String notas) throws SQLException {
        Sesion.requiere(Permiso.FIADOS_ADMINISTRAR);
        Validacion.exigir(Validacion.nombre(nombre));
        long usuarioId = Sesion.usuario().getId();
        Database.enTransaccion(con -> {
            Cliente c = clienteDao.buscarPorId(con, clienteId)
                    .orElseThrow(() -> new IllegalArgumentException("El cliente no existe."));
            c.setNombre(nombre.trim());
            c.setTelefono(limpiar(telefono));
            c.setNotas(limpiar(notas));
            clienteDao.actualizarDatos(con, c);
            bitacoraDao.registrar(con, usuarioId, "CLIENTE_EDITADO", c.getNombre());
            return null;
        });
    }

    /** Desactivar (solo si su saldo es exactamente cero) o reactivar. Solo admin. */
    public void cambiarActivo(long clienteId, boolean activo) throws SQLException {
        Sesion.requiere(Permiso.FIADOS_ADMINISTRAR);
        long usuarioId = Sesion.usuario().getId();
        Database.enTransaccion(con -> {
            Cliente c = clienteDao.buscarPorId(con, clienteId)
                    .orElseThrow(() -> new IllegalArgumentException("El cliente no existe."));
            if (!activo && c.getSaldo() != 0) {
                throw new IllegalArgumentException(c.getSaldo() > 0
                        ? c.getNombre() + " todavía debe " + Dinero.formatear(c.getSaldo())
                        + ". Solo se puede desactivar con saldo en cero."
                        : c.getNombre() + " tiene saldo a favor de " + Dinero.formatear(-c.getSaldo())
                        + ". Solo se puede desactivar con saldo en cero.");
            }
            clienteDao.cambiarActivo(con, clienteId, activo);
            bitacoraDao.registrar(con, usuarioId, activo ? "CLIENTE_REACTIVADO" : "CLIENTE_DESACTIVADO",
                    c.getNombre());
            return null;
        });
    }

    // ---------------------------------------------------------------- abonos

    /**
     * Registra un abono en UNA transacción: inserta en {@code abonos} (ligado al turno actual, para el corte)
     * y resta del saldo del cliente. No se permite abonar más de lo que debe.
     *
     * @throws IllegalArgumentException datos inválidos (mensaje para el usuario)
     * @throws IllegalStateException    sin turno abierto
     */
    public AbonoRegistrado abonar(long clienteId, long monto, MetodoPago metodo, String notas) throws SQLException {
        Sesion.requiere(Permiso.FIADOS_ABONAR);
        if (Sesion.turno() == null) {
            throw new IllegalStateException("No hay turno abierto.");
        }
        if (metodo == null || metodo == MetodoPago.FIADO) {
            throw new IllegalArgumentException("Elige cómo paga el abono: efectivo, tarjeta o transferencia.");
        }
        if (monto <= 0) {
            throw new IllegalArgumentException("El abono debe ser mayor a cero.");
        }
        long usuarioId = Sesion.usuario().getId();
        long turnoId = Sesion.turno().getId();
        return Database.enTransaccion(con -> {
            if (!metodosAbono(negocioDao.obtener(con).orElseGet(Negocio::new)).contains(metodo)) {
                throw new IllegalArgumentException("El método " + metodo.etiqueta() + " está deshabilitado.");
            }
            Cliente c = clienteDao.buscarPorId(con, clienteId)
                    .orElseThrow(() -> new IllegalArgumentException("El cliente no existe."));
            long anterior = c.getSaldo();
            if (anterior <= 0) {
                throw new IllegalArgumentException(c.getNombre() + " no tiene adeudo.");
            }
            if (monto > anterior) {
                throw new IllegalArgumentException("El abono (" + Dinero.formatear(monto)
                        + ") no puede ser mayor a lo que debe (" + Dinero.formatear(anterior) + ").");
            }
            long nuevo = clienteDao.sumarSaldo(con, clienteId, -monto);
            long id = abonoDao.insertar(con, clienteId, turnoId, usuarioId, monto, metodo, anterior, nuevo, notas);
            bitacoraDao.registrar(con, usuarioId, "ABONO", c.getNombre() + " · " + Dinero.formatear(monto) + " ("
                    + metodo.etiqueta() + ") · saldo " + Dinero.formatear(nuevo));
            return new AbonoRegistrado(id, monto, anterior, nuevo);
        });
    }

    // ---------------------------------------------------------------- utilidades

    private static void requiereVer() {
        if (!Sesion.tiene(Permiso.FIADOS_VENDER) && !Sesion.tiene(Permiso.FIADOS_ABONAR)) {
            throw new PermisoDenegadoException(Permiso.FIADOS_VENDER);
        }
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
