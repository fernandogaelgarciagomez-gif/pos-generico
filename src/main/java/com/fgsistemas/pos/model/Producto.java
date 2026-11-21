package com.fgsistemas.pos.model;

import com.fgsistemas.pos.util.Cantidad;

import java.time.LocalDateTime;

/** Producto. Precio y costo en centavos POR UNIDAD; stock y mínimo en milésimas. */
public class Producto {

    private long id;
    private String codigo;
    private String nombre;
    private Long categoriaId;
    private String categoriaNombre;
    private long precioVenta;
    private long costo;
    private long stock;
    private long stockMinimo = 5000;
    private TipoVenta tipoVenta = TipoVenta.PIEZA;
    private String unidad = "pza";
    private boolean activo = true;
    private LocalDateTime creadoEn;

    public Producto copia() {
        Producto p = new Producto();
        p.id = id;
        p.codigo = codigo;
        p.nombre = nombre;
        p.categoriaId = categoriaId;
        p.categoriaNombre = categoriaNombre;
        p.precioVenta = precioVenta;
        p.costo = costo;
        p.stock = stock;
        p.stockMinimo = stockMinimo;
        p.tipoVenta = tipoVenta;
        p.unidad = unidad;
        p.activo = activo;
        p.creadoEn = creadoEn;
        return p;
    }

    public boolean stockBajo() {
        return activo && stock <= stockMinimo;
    }

    /** "12.350 kg", "3 pza". */
    public String formatear(long milesimas) {
        return Cantidad.formatear(milesimas, tipoVenta, unidad);
    }

    public String stockTexto() {
        return formatear(stock);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Long getCategoriaId() {
        return categoriaId;
    }

    public void setCategoriaId(Long categoriaId) {
        this.categoriaId = categoriaId;
    }

    public String getCategoriaNombre() {
        return categoriaNombre;
    }

    public void setCategoriaNombre(String categoriaNombre) {
        this.categoriaNombre = categoriaNombre;
    }

    public long getPrecioVenta() {
        return precioVenta;
    }

    public void setPrecioVenta(long precioVenta) {
        this.precioVenta = precioVenta;
    }

    public long getCosto() {
        return costo;
    }

    public void setCosto(long costo) {
        this.costo = costo;
    }

    public long getStock() {
        return stock;
    }

    public void setStock(long stock) {
        this.stock = stock;
    }

    public long getStockMinimo() {
        return stockMinimo;
    }

    public void setStockMinimo(long stockMinimo) {
        this.stockMinimo = stockMinimo;
    }

    public TipoVenta getTipoVenta() {
        return tipoVenta;
    }

    public void setTipoVenta(TipoVenta tipoVenta) {
        this.tipoVenta = tipoVenta;
    }

    public String getUnidad() {
        return unidad;
    }

    public void setUnidad(String unidad) {
        this.unidad = unidad;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}
