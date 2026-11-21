package com.fgsistemas.pos.dao;

import com.fgsistemas.pos.model.Negocio;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class NegocioDao {

    public Optional<Negocio> obtener(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM negocio WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            Negocio n = new Negocio();
            n.setNombre(rs.getString("nombre"));
            n.setDireccion(rs.getString("direccion"));
            n.setTelefono(rs.getString("telefono"));
            n.setRfc(rs.getString("rfc"));
            n.setMensajeTicket(rs.getString("mensaje_ticket"));
            n.setLogoPath(rs.getString("logo_path"));
            n.setStockMinimoDefault(rs.getLong("stock_minimo_default"));
            n.setPermitirStockNegativo(rs.getInt("permitir_stock_negativo") == 1);
            n.setMetodoTarjeta(rs.getInt("metodo_tarjeta") == 1);
            n.setMetodoTransferencia(rs.getInt("metodo_transferencia") == 1);
            n.setMetodoFiado(rs.getInt("metodo_fiado") == 1);
            n.setDescuentoMaximoPct(rs.getInt("descuento_maximo_pct"));
            n.setRespaldoAutomatico(rs.getInt("respaldo_automatico") == 1);
            n.setDiasConservarRespaldos(rs.getInt("dias_conservar_respaldos"));
            return Optional.of(n);
        }
    }

    /** Crea la fila única del negocio (primera configuración). El resto de columnas toma su default. */
    public void insertar(Connection c, Negocio n) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO negocio (id, nombre, direccion, telefono) VALUES (1, ?, ?, ?)")) {
            ps.setString(1, n.getNombre().trim());
            ps.setString(2, Dao.vacioANulo(n.getDireccion()));
            ps.setString(3, Dao.vacioANulo(n.getTelefono()));
            ps.executeUpdate();
        }
    }
}
