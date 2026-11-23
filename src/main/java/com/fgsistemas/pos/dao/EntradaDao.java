package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.EntradaDetalle;
import com.fgsistemas.pos.model.EntradaMercancia;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EntradaDao {

    public long insertar(Connection c, long usuarioId, String proveedor, String factura, String notas,
                         long totalCosto) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO entradas_mercancia (usuario_id, proveedor, factura, notas, total_costo, fecha) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, usuarioId);
            ps.setString(2, Dao.vacioANulo(proveedor));
            ps.setString(3, Dao.vacioANulo(factura));
            ps.setString(4, Dao.vacioANulo(notas));
            ps.setLong(5, totalCosto);
            ps.setString(6, Fechas.ahoraTexto());
            ps.executeUpdate();
        }
        return Dao.ultimoId(c);
    }

    public void insertarDetalle(Connection c, long entradaId, long productoId, long cantidad, long costoUnitario)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO entrada_detalle (entrada_id, producto_id, cantidad, costo_unitario) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, entradaId);
            ps.setLong(2, productoId);
            ps.setLong(3, cantidad);
            ps.setLong(4, costoUnitario);
            ps.executeUpdate();
        }
    }

    /** Proveedores ya usados, para autocompletar. */
    public List<String> proveedores(Connection c) throws SQLException {
        List<String> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT DISTINCT proveedor FROM entradas_mercancia WHERE proveedor IS NOT NULL "
                        + "ORDER BY proveedor COLLATE NOCASE");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(rs.getString(1));
            }
        }
        return lista;
    }

    /** Historial entre dos fechas (inclusive), filtrando por proveedor o factura si {@code texto} no está vacío. */
    public List<EntradaMercancia> listar(Connection c, LocalDate desde, LocalDate hasta, String texto)
            throws SQLException {
        String filtro = (texto == null || texto.isBlank()) ? null : "%" + texto.trim() + "%";
        String sql = "SELECT e.id, e.fecha, u.nombre, e.proveedor, e.factura, e.notas, e.total_costo, "
                + "(SELECT COUNT(*) FROM entrada_detalle d WHERE d.entrada_id = e.id) "
                + "FROM entradas_mercancia e JOIN usuarios u ON u.id = e.usuario_id "
                + "WHERE e.fecha >= ? AND e.fecha < ? "
                + (filtro == null ? "" : "AND (e.proveedor LIKE ? OR e.factura LIKE ?) ")
                + "ORDER BY e.id DESC";
        List<EntradaMercancia> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, Fechas.aTexto(desde.atStartOfDay()));
            ps.setString(2, Fechas.aTexto(hasta.plusDays(1).atStartOfDay()));
            if (filtro != null) {
                ps.setString(3, filtro);
                ps.setString(4, filtro);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new EntradaMercancia(rs.getLong(1), Fechas.deTexto(rs.getString(2)), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getLong(7), rs.getInt(8)));
                }
            }
        }
        return lista;
    }

    public List<EntradaDetalle> detalle(Connection c, long entradaId) throws SQLException {
        List<EntradaDetalle> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT p.codigo, p.nombre, p.tipo_venta, p.unidad, d.cantidad, d.costo_unitario "
                        + "FROM entrada_detalle d JOIN productos p ON p.id = d.producto_id "
                        + "WHERE d.entrada_id = ? ORDER BY d.id")) {
            ps.setLong(1, entradaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new EntradaDetalle(rs.getString(1), rs.getString(2),
                            TipoVenta.valueOf(rs.getString(3)), rs.getString(4), rs.getLong(5), rs.getLong(6)));
                }
            }
        }
        return lista;
    }

    /** Entrada anterior con la misma factura (y el mismo proveedor, si se dio), sin distinguir mayúsculas. */
    public java.util.Optional<String> buscarFactura(Connection c, String proveedor, String factura) throws SQLException {
        boolean conProveedor = proveedor != null && !proveedor.isBlank();
        try (PreparedStatement ps = c.prepareStatement("SELECT id, fecha, proveedor, total_costo FROM entradas_mercancia "
                + "WHERE TRIM(factura) = ? COLLATE NOCASE"
                + (conProveedor ? " AND TRIM(proveedor) = ? COLLATE NOCASE" : "") + " ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, factura.trim());
            if (conProveedor) {
                ps.setString(2, proveedor.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return java.util.Optional.empty();
                }
                return java.util.Optional.of("Entrada #" + rs.getLong(1) + " del "
                        + com.fgsistemas.pos.util.Fechas.FECHA_HORA.format(com.fgsistemas.pos.util.Fechas.deTexto(rs.getString(2)))
                        + (rs.getString(3) == null ? "" : " · " + rs.getString(3))
                        + " · " + com.fgsistemas.pos.util.Dinero.formatear(rs.getLong(4)));
            }
        }
    }
}
