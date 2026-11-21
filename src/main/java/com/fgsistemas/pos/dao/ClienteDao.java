package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.Cliente;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Acceso a clientes (fiados). */
public class ClienteDao {

    private static final String SELECT = "SELECT id, nombre, telefono, notas, saldo, activo FROM clientes ";

    /** Parte fiada de las ventas COMPLETADAS de un cliente (las canceladas no cuentan). */
    private static final String SUMA_FIADO =
            "SELECT COALESCE(SUM(p.monto), 0) FROM venta_pagos p JOIN ventas v ON v.id = p.venta_id "
                    + "WHERE v.cliente_id = ? AND v.estado = 'COMPLETADA' AND p.metodo = 'FIADO'";

    public List<Cliente> listarActivos(Connection c) throws SQLException {
        List<Cliente> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE activo = 1 ORDER BY nombre COLLATE NOCASE");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    /** Lista para la pantalla Fiados: con fecha de la última compra y del último abono. */
    public List<Cliente> listarConFechas(Connection c, boolean incluirInactivos) throws SQLException {
        List<Cliente> lista = new ArrayList<>();
        String sql = "SELECT cl.id, cl.nombre, cl.telefono, cl.notas, cl.saldo, cl.activo, "
                + "(SELECT MAX(v.fecha) FROM ventas v WHERE v.cliente_id = cl.id AND v.estado = 'COMPLETADA'), "
                + "(SELECT MAX(a.fecha) FROM abonos a WHERE a.cliente_id = cl.id) "
                + "FROM clientes cl " + (incluirInactivos ? "" : "WHERE cl.activo = 1 ")
                + "ORDER BY cl.nombre COLLATE NOCASE";
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Cliente cl = mapear(rs);
                cl.setUltimaCompra(fecha(rs.getString(7)));
                cl.setUltimoAbono(fecha(rs.getString(8)));
                lista.add(cl);
            }
        }
        return lista;
    }

    public Optional<Cliente> buscarPorId(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    public long insertar(Connection c, Cliente cl) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO clientes (nombre, telefono, notas, saldo, activo, creado_en) VALUES (?, ?, ?, 0, 1, ?)")) {
            ps.setString(1, cl.getNombre());
            ps.setString(2, Dao.vacioANulo(cl.getTelefono()));
            ps.setString(3, Dao.vacioANulo(cl.getNotas()));
            ps.setString(4, Fechas.ahoraTexto());
            ps.executeUpdate();
        }
        long id = Dao.ultimoId(c);
        cl.setId(id);
        return id;
    }

    /** Actualiza nombre, teléfono y notas (el saldo nunca se edita a mano). */
    public void actualizarDatos(Connection c, Cliente cl) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE clientes SET nombre = ?, telefono = ?, notas = ? WHERE id = ?")) {
            ps.setString(1, cl.getNombre());
            ps.setString(2, Dao.vacioANulo(cl.getTelefono()));
            ps.setString(3, Dao.vacioANulo(cl.getNotas()));
            ps.setLong(4, cl.getId());
            ps.executeUpdate();
        }
    }

    public void cambiarActivo(Connection c, long id, boolean activo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE clientes SET activo = ? WHERE id = ?")) {
            ps.setInt(1, activo ? 1 : 0);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /** Suma {@code delta} al saldo (positivo = debe más) y regresa el saldo nuevo. */
    public long sumarSaldo(Connection c, long clienteId, long delta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE clientes SET saldo = saldo + ? WHERE id = ?")) {
            ps.setLong(1, delta);
            ps.setLong(2, clienteId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT saldo FROM clientes WHERE id = ?")) {
            ps.setLong(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Saldo recalculado desde los movimientos: Σ parte fiada de ventas completadas − Σ abonos. */
    public long saldoCalculado(Connection c, long clienteId) throws SQLException {
        long fiado;
        try (PreparedStatement ps = c.prepareStatement(SUMA_FIADO)) {
            ps.setLong(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                fiado = rs.getLong(1);
            }
        }
        long abonos;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(monto), 0) FROM abonos WHERE cliente_id = ?")) {
            ps.setLong(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                abonos = rs.getLong(1);
            }
        }
        return fiado - abonos;
    }

    /** Venta del cliente con parte fiada (incluye canceladas, para el estado de cuenta). */
    public record VentaFiada(long ventaId, long folio, LocalDateTime fecha, long fiado, boolean cancelada) {
    }

    public List<VentaFiada> ventasFiadas(Connection c, long clienteId) throws SQLException {
        List<VentaFiada> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT v.id, v.folio, v.fecha, SUM(p.monto), v.estado FROM ventas v "
                        + "JOIN venta_pagos p ON p.venta_id = v.id AND p.metodo = 'FIADO' "
                        + "WHERE v.cliente_id = ? GROUP BY v.id ORDER BY v.fecha, v.id")) {
            ps.setLong(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new VentaFiada(rs.getLong(1), rs.getLong(2), Fechas.deTexto(rs.getString(3)),
                            rs.getLong(4), "CANCELADA".equals(rs.getString(5))));
                }
            }
        }
        return lista;
    }

    private static LocalDateTime fecha(String texto) {
        return texto == null ? null : Fechas.deTexto(texto);
    }

    private static Cliente mapear(ResultSet rs) throws SQLException {
        Cliente c = new Cliente();
        c.setId(rs.getLong("id"));
        c.setNombre(rs.getString("nombre"));
        c.setTelefono(rs.getString("telefono"));
        c.setNotas(rs.getString("notas"));
        c.setSaldo(rs.getLong("saldo"));
        c.setActivo(rs.getInt("activo") == 1);
        return c;
    }
}
