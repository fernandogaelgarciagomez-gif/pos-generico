package com.fgsistemas.pos.model;

/** Permisos del sistema. Qué tiene cada rol: {@link com.fgsistemas.pos.service.Permisos}. */
public enum Permiso {
    VENDER,
    VER_INVENTARIO,
    EDITAR_INVENTARIO,
    ENTRADA_MERCANCIA,
    VER_RESUMEN_DIA,
    HACER_CORTE,
    VER_CORTES_PASADOS,
    FIADOS_VENDER,
    FIADOS_ABONAR,
    FIADOS_ADMINISTRAR,
    CANCELAR_VENTA,
    DESCUENTOS,
    RETIROS_CAJA,
    USUARIOS,
    CONFIGURACION,
    RESPALDOS,
    /** Hacer el corte de un turno abierto por otro usuario (sin autorización: solo admin). */
    CORTE_TURNO_AJENO
}
