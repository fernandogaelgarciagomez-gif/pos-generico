package com.fgsistemas.pos.service;

import com.fgsistemas.pos.config.Database;
import com.fgsistemas.pos.dao.BitacoraDao;
import com.fgsistemas.pos.dao.CategoriaDao;
import com.fgsistemas.pos.dao.MovimientoInventarioDao;
import com.fgsistemas.pos.dao.MovimientoInventarioDao.Tipo;
import com.fgsistemas.pos.dao.NegocioDao;
import com.fgsistemas.pos.dao.ProductoDao;
import com.fgsistemas.pos.model.MovimientoInventario;
import com.fgsistemas.pos.model.Negocio;
import com.fgsistemas.pos.model.Permiso;
import com.fgsistemas.pos.model.Producto;
import com.fgsistemas.pos.model.TipoVenta;
import com.fgsistemas.pos.util.Cantidad;
import com.fgsistemas.pos.util.Dinero;
import com.fgsistemas.pos.util.Sesion;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reglas de inventario (docs/modulos/15-inventario.md). */
public class InventarioService {

    /** Cómo se aplica un ajuste de stock. */
    public enum TipoAjuste {
        FIJAR("Dejar el stock en"),
        SUMAR("Sumar al stock"),
        RESTAR("Restar del stock");

        private final String etiqueta;

        TipoAjuste(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }

    private final ProductoDao productoDao = new ProductoDao();
    private final CategoriaDao categoriaDao = new CategoriaDao();
    private final MovimientoInventarioDao movimientoDao = new MovimientoInventarioDao();
    private final NegocioDao negocioDao = new NegocioDao();
    private final BitacoraDao bitacoraDao = new BitacoraDao();

    // ---------------------------------------------------------------- consultas

    /**
     * Productos para la vista de inventario. Los inactivos solo para quien edita inventario.
     * A quien no edita inventario (cajero) se le entregan SIN costo.
     */
    public List<Producto> listar(boolean incluirInactivos) throws SQLException {
        Sesion.requiere(Permiso.VER_INVENTARIO);
        boolean editor = Sesion.tiene(Permiso.EDITAR_INVENTARIO);
        try (Connection c = Database.conectar()) {
            List<Producto> lista = productoDao.listar(c, incluirInactivos && editor);
            if (!editor) {
                lista.forEach(p -> p.setCosto(0));
            }
            return lista;
        }
    }

    /** Producto ACTIVO por código exacto (para capturar en entradas y ventas). */
    public Optional<Producto> buscarActivoPorCodigo(String codigo) throws SQLException {
        Sesion.requiere(Permiso.VER_INVENTARIO);
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        try (Connection c = Database.conectar()) {
            Optional<Producto> p = productoDao.buscarPorCodigo(c, codigo.trim()).filter(Producto::isActivo);
            if (!Sesion.tiene(Permiso.EDITAR_INVENTARIO)) {
                p.ifPresent(x -> x.setCosto(0));
            }
            return p;
        }
    }

    public List<String> categorias() throws SQLException {
        Sesion.requiere(Permiso.VER_INVENTARIO);
        try (Connection c = Database.conectar()) {
            return categoriaDao.listarNombres(c);
        }
    }

    /**
     * Crea una categoría (o regresa la existente con ese nombre, sin distinguir mayúsculas).
     *
     * @return el nombre como quedó guardado
     */
    public String crearCategoria(String nombre) throws SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Escribe el nombre de la categoría.");
        }
        String limpio = nombre.trim();
        if (limpio.length() > 60) {
            throw new IllegalArgumentException("Máximo 60 caracteres.");
        }
        long usuarioId = Sesion.usuario().getId();
        return Database.enTransaccion(c -> {
            for (String existente : categoriaDao.listarNombres(c)) {
                if (existente.equalsIgnoreCase(limpio)) {
                    return existente;
                }
            }
            categoriaDao.buscarOCrear(c, limpio);
            bitacoraDao.registrar(c, usuarioId, "CATEGORIA_CREADA", limpio);
            return limpio;
        });
    }

    /** Categorías con cuántos productos usan cada una (Configuración → Inventario). */
    public List<CategoriaDao.ConConteo> categoriasConConteo() throws SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        try (Connection c = Database.conectar()) {
            return categoriaDao.listarConConteo(c);
        }
    }

    public void renombrarCategoria(long categoriaId, String nombre) throws SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Escribe el nombre de la categoría.");
        }
        String limpio = nombre.trim();
        if (limpio.length() > 60) {
            throw new IllegalArgumentException("Máximo 60 caracteres.");
        }
        long usuarioId = Sesion.usuario().getId();
        Database.enTransaccion(c -> {
            CategoriaDao.ConConteo actual = categoriaDao.buscar(c, categoriaId)
                    .orElseThrow(() -> new IllegalArgumentException("La categoría ya no existe."));
            if (categoriaDao.existeNombre(c, limpio, categoriaId)) {
                throw new IllegalArgumentException("Ya existe una categoría llamada \"" + limpio + "\".");
            }
            if (!actual.nombre().equals(limpio)) {
                categoriaDao.renombrar(c, categoriaId, limpio);
                bitacoraDao.registrar(c, usuarioId, "CATEGORIA_RENOMBRADA", actual.nombre() + " → " + limpio);
            }
            return null;
        });
    }

    /**
     * Quita una categoría que ningún producto usa.
     * DECISION: en vez de "desactivar" (que pediría una columna nueva y filtrar en Vender e Inventario),
     * solo se puede quitar una categoría vacía; con productos, primero hay que cambiarlos de categoría.
     */
    public void quitarCategoria(long categoriaId) throws SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        long usuarioId = Sesion.usuario().getId();
        Database.enTransaccion(c -> {
            CategoriaDao.ConConteo actual = categoriaDao.buscar(c, categoriaId)
                    .orElseThrow(() -> new IllegalArgumentException("La categoría ya no existe."));
            if (actual.productos() > 0) {
                throw new IllegalStateException("\"" + actual.nombre() + "\" tiene " + actual.productos()
                        + (actual.productos() == 1 ? " producto" : " productos")
                        + ". Cámbialos de categoría en Inventario antes de quitarla.");
            }
            categoriaDao.eliminar(c, categoriaId);
            bitacoraDao.registrar(c, usuarioId, "CATEGORIA_QUITADA", actual.nombre());
            return null;
        });
    }

    public int contarStockBajo() throws SQLException {
        Sesion.requiere(Permiso.VER_INVENTARIO);
        try (Connection c = Database.conectar()) {
            return productoDao.contarStockBajo(c);
        }
    }

    public List<Producto> listaDeCompras() throws SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        try (Connection c = Database.conectar()) {
            return productoDao.listarStockBajo(c);
        }
    }

    /** Cantidad sugerida a comprar = mínimo × 2 − stock (en piezas se redondea hacia arriba). */
    public static long cantidadSugerida(Producto p) {
        long sugerida = Math.max(0, p.getStockMinimo() * 2 - p.getStock());
        if (!p.getTipoVenta().aceptaDecimales() && sugerida % Cantidad.UNO != 0) {
            sugerida = (sugerida / Cantidad.UNO + 1) * Cantidad.UNO;
        }
        return sugerida;
    }

    public List<MovimientoInventario> kardex(long productoId) throws SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        try (Connection c = Database.conectar()) {
            return movimientoDao.listarPorProducto(c, productoId);
        }
    }

    public long stockMinimoDefault() throws SQLException {
        try (Connection c = Database.conectar()) {
            return negocioDao.obtener(c).map(Negocio::getStockMinimoDefault).orElse(5000L);
        }
    }

    // ---------------------------------------------------------------- cambios

    /**
     * Alta de producto. Si trae stock inicial, queda en kardex como AJUSTE "Stock inicial".
     *
     * @throws IllegalArgumentException datos inválidos (mensaje para el usuario)
     */
    public Producto crear(Producto datos, String nombreCategoria) throws SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        Producto p = normalizarYValidar(datos);
        if (p.getStock() < 0) {
            throw new IllegalArgumentException("El stock inicial no puede ser negativo.");
        }
        validarCantidadSegunTipo(p.getStock(), p.getTipoVenta(), "El stock inicial");
        long usuarioId = Sesion.usuario().getId();
        return Database.enTransaccion(c -> {
            if (productoDao.existeCodigo(c, p.getCodigo(), -1)) {
                throw new IllegalArgumentException("Ya existe un producto con el código " + p.getCodigo() + ".");
            }
            p.setCategoriaId(categoriaId(c, nombreCategoria));
            p.setActivo(true);
            productoDao.insertar(c, p);
            if (p.getStock() != 0) {
                movimientoDao.registrar(c, p.getId(), Tipo.AJUSTE, p.getStock(), p.getStock(), null, usuarioId,
                        "Stock inicial");
            }
            bitacoraDao.registrar(c, usuarioId, "PRODUCTO_CREADO",
                    p.getCodigo() + " · " + p.getNombre() + " · " + Dinero.formatear(p.getPrecioVenta()));
            return p;
        });
    }

    /** Edita datos generales. El stock NO cambia aquí (usar {@link #ajustarStock}). Cambio de precio → bitácora. */
    public void editar(Producto datos, String nombreCategoria) throws SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        Producto p = normalizarYValidar(datos);
        long usuarioId = Sesion.usuario().getId();
        Database.enTransaccion(c -> {
            Producto actual = productoDao.buscarPorId(c, p.getId())
                    .orElseThrow(() -> new IllegalArgumentException("El producto ya no existe."));
            if (productoDao.existeCodigo(c, p.getCodigo(), p.getId())) {
                throw new IllegalArgumentException("Ya existe otro producto con el código " + p.getCodigo() + ".");
            }
            if (p.getTipoVenta() == TipoVenta.PIEZA && actual.getStock() % Cantidad.UNO != 0) {
                throw new IllegalArgumentException(
                        "El stock actual tiene fracción; ajústalo a un número entero antes de venderlo por pieza.");
            }
            p.setCategoriaId(categoriaId(c, nombreCategoria));
            productoDao.actualizar(c, p);
            if (actual.getPrecioVenta() != p.getPrecioVenta()) {
                bitacoraDao.registrar(c, usuarioId, "PRECIO_CAMBIADO", p.getCodigo() + " · "
                        + Dinero.formatear(actual.getPrecioVenta()) + " → " + Dinero.formatear(p.getPrecioVenta()));
            }
            bitacoraDao.registrar(c, usuarioId, "PRODUCTO_EDITADO", p.getCodigo() + " · " + p.getNombre());
            return null;
        });
    }

    /** Cambia solo el precio de venta (p. ej. después de una entrada con costo nuevo). */
    public void cambiarPrecio(long productoId, long nuevoPrecio) throws SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        if (nuevoPrecio <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a cero.");
        }
        long usuarioId = Sesion.usuario().getId();
        Database.enTransaccion(c -> {
            Producto actual = productoDao.buscarPorId(c, productoId)
                    .orElseThrow(() -> new IllegalArgumentException("El producto ya no existe."));
            if (actual.getPrecioVenta() == nuevoPrecio) {
                return null;
            }
            productoDao.actualizarPrecio(c, productoId, nuevoPrecio);
            bitacoraDao.registrar(c, usuarioId, "PRECIO_CAMBIADO", actual.getCodigo() + " · "
                    + Dinero.formatear(actual.getPrecioVenta()) + " → " + Dinero.formatear(nuevoPrecio));
            return null;
        });
    }

    /**
     * Ajuste de stock con motivo obligatorio → kardex tipo AJUSTE.
     *
     * @return el stock resultante
     */
    public long ajustarStock(long productoId, TipoAjuste tipo, long cantidad, String motivo) throws SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo del ajuste es obligatorio.");
        }
        if (cantidad < 0) {
            throw new IllegalArgumentException("La cantidad no puede ser negativa.");
        }
        long usuarioId = Sesion.usuario().getId();
        return Database.enTransaccion(c -> {
            Producto p = productoDao.buscarPorId(c, productoId)
                    .orElseThrow(() -> new IllegalArgumentException("El producto ya no existe."));
            validarCantidadSegunTipo(cantidad, p.getTipoVenta(), "La cantidad");
            long nuevo = switch (tipo) {
                case FIJAR -> cantidad;
                case SUMAR -> p.getStock() + cantidad;
                case RESTAR -> p.getStock() - cantidad;
            };
            long diferencia = nuevo - p.getStock();
            if (diferencia == 0) {
                throw new IllegalArgumentException("El stock quedaría igual; no hay nada que ajustar.");
            }
            boolean permiteNegativo = negocioDao.obtener(c).map(Negocio::isPermitirStockNegativo).orElse(false);
            if (nuevo < 0 && !permiteNegativo) {
                throw new IllegalArgumentException("El stock no puede quedar negativo (" + p.formatear(nuevo) + ").");
            }
            productoDao.actualizarStock(c, productoId, nuevo);
            movimientoDao.registrar(c, productoId, Tipo.AJUSTE, diferencia, nuevo, null, usuarioId, motivo.trim());
            bitacoraDao.registrar(c, usuarioId, "AJUSTE_STOCK", p.getCodigo() + " · " + p.formatear(p.getStock())
                    + " → " + p.formatear(nuevo) + " · " + motivo.trim());
            return nuevo;
        });
    }

    /** Desactivar (no se borra; deja de aparecer en ventas) o reactivar. */
    public void cambiarActivo(long productoId, boolean activo) throws SQLException {
        Sesion.requiere(Permiso.EDITAR_INVENTARIO);
        long usuarioId = Sesion.usuario().getId();
        Database.enTransaccion(c -> {
            Producto p = productoDao.buscarPorId(c, productoId)
                    .orElseThrow(() -> new IllegalArgumentException("El producto ya no existe."));
            productoDao.actualizarActivo(c, productoId, activo);
            bitacoraDao.registrar(c, usuarioId, activo ? "PRODUCTO_REACTIVADO" : "PRODUCTO_DESACTIVADO",
                    p.getCodigo() + " · " + p.getNombre());
            return null;
        });
    }

    // ---------------------------------------------------------------- validación

    private static Producto normalizarYValidar(Producto datos) {
        Producto p = datos.copia();
        List<String> errores = new ArrayList<>();
        agregar(errores, Validacion.codigoProducto(p.getCodigo()));
        agregar(errores, Validacion.nombreProducto(p.getNombre()));
        if (p.getTipoVenta() == null) {
            errores.add("Elige cómo se vende el producto.");
        }
        if (p.getPrecioVenta() <= 0) {
            errores.add("El precio debe ser mayor a cero.");
        }
        if (p.getCosto() < 0) {
            errores.add("El costo no puede ser negativo.");
        }
        if (p.getStockMinimo() < 0) {
            errores.add("El stock mínimo no puede ser negativo.");
        }
        if (p.getUnidad() == null || p.getUnidad().isBlank()) {
            errores.add("Escribe la unidad (pza, kg, m...).");
        }
        if (!errores.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errores));
        }
        validarCantidadSegunTipo(p.getStockMinimo(), p.getTipoVenta(), "El stock mínimo");
        p.setCodigo(p.getCodigo().trim());
        p.setNombre(p.getNombre().trim());
        p.setUnidad(p.getUnidad().trim());
        return p;
    }

    private static void validarCantidadSegunTipo(long milesimas, TipoVenta tipo, String campo) {
        if (!tipo.aceptaDecimales() && milesimas % Cantidad.UNO != 0) {
            throw new IllegalArgumentException(campo + " debe ser un número entero (se vende por pieza).");
        }
    }

    private static void agregar(List<String> errores, String error) {
        if (error != null) {
            errores.add(error);
        }
    }

    private Long categoriaId(Connection c, String nombre) throws SQLException {
        return (nombre == null || nombre.isBlank()) ? null : categoriaDao.buscarOCrear(c, nombre.trim());
    }
}
