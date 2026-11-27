package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.MovimientoInventarioDao;
import com.fgsistemas.pos.dao.MovimientoInventarioDao.Tipo;
import com.fgsistemas.pos.dao.NegocioDao;
import com.fgsistemas.pos.dao.ProductoDao;
import com.fgsistemas.pos.dao.SalidaDao;
import com.fgsistemas.pos.model.Carrito;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Sesion;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Salida sin cobro: el producto sale del inventario sin venta y sin tocar la caja
 * (uso interno, consumo del personal, cortesías). El cajero/recepcionista la hace sin autorización,
 * pero queda en el kardex, en la bitácora y en el reporte de salidas para el administrador.
 */
public class SalidaService {

    /** Motivos sugeridos (el usuario también puede escribir el suyo). */
    public static final List<String> MOTIVOS =
            List.of("Uso interno", "Consumo del personal", "Cortesía a cliente", "Muestra", "Otro");

    public record SalidaRegistrada(long id, int productos, long costo, long valorVenta) {
    }

    private final ProductoDao productoDao = new ProductoDao();
    private final MovimientoInventarioDao movimientoDao = new MovimientoInventarioDao();
    private final NegocioDao negocioDao = new NegocioDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();
    private final SalidaDao salidaDao = new SalidaDao();

    /**
     * Saca del inventario los productos del carrito sin cobrarlos. Ignora descuento y cliente del carrito.
     *
     * @throws IllegalArgumentException datos inválidos o sin stock (mensaje para el usuario)
     */
    public SalidaRegistrada registrar(Carrito carrito, String motivo) throws SQLException {
        Sesion.requiere(Permiso.SALIDA_SIN_COBRO);
        if (carrito == null || carrito.vacio()) {
            throw new IllegalArgumentException("Agrega los productos que se van a sacar.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Escribe para qué se saca el producto (motivo).");
        }
        String motivoLimpio = motivo.trim();
        if (motivoLimpio.length() > 200) {
            throw new IllegalArgumentException("El motivo es muy largo (máximo 200 caracteres).");
        }
        for (Carrito.Renglon r : carrito.getRenglones()) {
            if (r.getCantidad() <= 0 || r.getCantidad() > Cantidad.MAXIMO) {
                throw new IllegalArgumentException("Revisa la cantidad de \"" + r.getProducto().getNombre() + "\".");
            }
        }
        long usuarioId = Sesion.usuario().getId();
        Long turnoId = Sesion.turno() == null ? null : Sesion.turno().getId();

        return Database.enTransaccion(c -> {
            // Datos frescos de la BD, sumando renglones del mismo producto.
            Map<Long, Producto> productos = new LinkedHashMap<>();
            Map<Long, Long> cantidades = new LinkedHashMap<>();
            for (Carrito.Renglon r : carrito.getRenglones()) {
                long id = r.getProducto().getId();
                if (!productos.containsKey(id)) {
                    productos.put(id, productoDao.buscarPorId(c, id).filter(Producto::isActivo)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "\"" + r.getProducto().getNombre() + "\" ya no está disponible.")));
                }
                cantidades.merge(id, r.getCantidad(), Long::sum);
            }
            boolean permiteNegativo = negocioDao.obtener(c).map(Negocio::isPermitirStockNegativo).orElse(false);
            for (Map.Entry<Long, Long> e : cantidades.entrySet()) {
                Producto p = productos.get(e.getKey());
                if (!permiteNegativo && p.getStock() < e.getValue()) {
                    throw new IllegalArgumentException("No hay suficiente \"" + p.getNombre() + "\": quedan "
                            + p.stockTexto() + " y se quieren sacar " + p.formatear(e.getValue()) + ".");
                }
            }

            long salidaId = salidaDao.insertar(c, turnoId, usuarioId, motivoLimpio);
            long costo = 0;
            long valor = 0;
            StringBuilder resumen = new StringBuilder();
            for (Map.Entry<Long, Long> e : cantidades.entrySet()) {
                Producto p = productos.get(e.getKey());
                long q = e.getValue();
                salidaDao.insertarDetalle(c, salidaId, p.getId(), q, p.getCosto(), p.getPrecioVenta());
                long nuevo = p.getStock() - q;
                productoDao.actualizarStock(c, p.getId(), nuevo);
                movimientoDao.registrar(c, p.getId(), Tipo.SALIDA, -q, nuevo, salidaId, usuarioId, motivoLimpio);
                costo += Cantidad.importe(p.getCosto(), q);
                valor += Cantidad.importe(p.getPrecioVenta(), q);
                if (resumen.length() > 0) {
                    resumen.append(", ");
                }
                resumen.append(p.formatear(q)).append(' ').append(p.getNombre());
            }
            bitacoraDao.registrar(c, usuarioId, "SALIDA_SIN_COBRO", "Salida #" + salidaId + " · " + motivoLimpio
                    + " · " + resumen + " · valor " + Dinero.formatear(valor));
            return new SalidaRegistrada(salidaId, cantidades.size(), costo, valor);
        });
    }

    /** Reporte para el administrador: renglones de salidas con fecha en [desde, hasta). */
    public List<SalidaDao.Renglon> listar(LocalDateTime desde, LocalDateTime hasta) throws SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        try (Connection c = Database.conectar()) {
            return salidaDao.listar(c, desde, hasta);
        }
    }
}
