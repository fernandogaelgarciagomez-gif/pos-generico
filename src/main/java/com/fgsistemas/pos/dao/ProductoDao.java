package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.util.Fechas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductoDao {

    private static final String SELECT =
            "SELECT p.id, p.codigo, p.nombre, p.categoria_id, c.nombre AS categoria_nombre, p.precio_venta, "
                    + "p.costo, p.stock, p.stock_minimo, p.tipo_venta, p.unidad, p.activo, p.creado_en "
                    + "FROM productos p LEFT JOIN categorias c ON c.id = p.categoria_id ";

    public List<Producto> listar(Connection c, boolean incluirInactivos) throws SQLException {
        String sql = SELECT + (incluirInactivos ? "" : "WHERE p.activo = 1 ") + "ORDER BY p.nombre COLLATE NOCASE";
        List<Producto> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public List<Producto> listarStockBajo(Connection c) throws SQLException {
        List<Producto> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                SELECT + "WHERE p.activo = 1 AND p.stock <= p.stock_minimo ORDER BY p.nombre COLLATE NOCASE");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public Optional<Producto> buscarPorCodigo(Connection c, String codigo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE p.codigo = ?")) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Producto> buscarPorId(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE p.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    public long insertar(Connection c, Producto p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO productos (codigo, nombre, categoria_id, precio_venta, costo, stock, stock_minimo, "
                        + "tipo_venta, unidad, activo, creado_en) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, p.getCodigo());
            ps.setString(2, p.getNombre());
            Dao.setLongONulo(ps, 3, p.getCategoriaId());
            ps.setLong(4, p.getPrecioVenta());
            ps.setLong(5, p.getCosto());
            ps.setLong(6, p.getStock());
            ps.setLong(7, p.getStockMinimo());
            ps.setString(8, p.getTipoVenta().name());
            ps.setString(9, p.getUnidad());
            ps.setInt(10, p.isActivo() ? 1 : 0);
            ps.setString(11, Fechas.ahoraTexto());
            ps.executeUpdate();
        }
        long id = Dao.ultimoId(c);
        p.setId(id);
        return id;
    }

    /** Actualiza datos generales. NO toca el stock (eso solo pasa con movimientos). */
    public void actualizar(Connection c, Producto p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE productos SET codigo = ?, nombre = ?, categoria_id = ?, precio_venta = ?, costo = ?, "
                        + "stock_minimo = ?, tipo_venta = ?, unidad = ? WHERE id = ?")) {
            ps.setString(1, p.getCodigo());
            ps.setString(2, p.getNombre());
            Dao.setLongONulo(ps, 3, p.getCategoriaId());
            ps.setLong(4, p.getPrecioVenta());
            ps.setLong(5, p.getCosto());
            ps.setLong(6, p.getStockMinimo());
            ps.setString(7, p.getTipoVenta().name());
            ps.setString(8, p.getUnidad());
            ps.setLong(9, p.getId());
            ps.executeUpdate();
        }
    }

    public void actualizarStock(Connection c, long id, long stock) throws SQLException {
        actualizarCampo(c, "UPDATE productos SET stock = ? WHERE id = ?", stock, id);
    }

    public void actualizarCosto(Connection c, long id, long costo) throws SQLException {
        actualizarCampo(c, "UPDATE productos SET costo = ? WHERE id = ?", costo, id);
    }

    public void actualizarPrecio(Connection c, long id, long precio) throws SQLException {
        actualizarCampo(c, "UPDATE productos SET precio_venta = ? WHERE id = ?", precio, id);
    }

    public void actualizarActivo(Connection c, long id, boolean activo) throws SQLException {
        actualizarCampo(c, "UPDATE productos SET activo = ? WHERE id = ?", activo ? 1 : 0, id);
    }

    public boolean existeCodigo(Connection c, String codigo, long excluirId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM productos WHERE codigo = ? AND id <> ?")) {
            ps.setString(1, codigo);
            ps.setLong(2, excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Productos activos con stock en o por debajo de su mínimo. */
    public int contarStockBajo(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM productos WHERE activo = 1 AND stock <= stock_minimo");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void actualizarCampo(Connection c, String sql, long valor, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, valor);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private static Producto mapear(ResultSet rs) throws SQLException {
        Producto p = new Producto();
        p.setId(rs.getLong("id"));
        p.setCodigo(rs.getString("codigo"));
        p.setNombre(rs.getString("nombre"));
        long cat = rs.getLong("categoria_id");
        p.setCategoriaId(rs.wasNull() ? null : cat);
        p.setCategoriaNombre(rs.getString("categoria_nombre"));
        p.setPrecioVenta(rs.getLong("precio_venta"));
        p.setCosto(rs.getLong("costo"));
        p.setStock(rs.getLong("stock"));
        p.setStockMinimo(rs.getLong("stock_minimo"));
        p.setTipoVenta(TipoVenta.valueOf(rs.getString("tipo_venta")));
        p.setUnidad(rs.getString("unidad"));
        p.setActivo(rs.getInt("activo") == 1);
        p.setCreadoEn(Fechas.deTexto(rs.getString("creado_en")));
        return p;
    }
}
