package pe.edu.sistema.model;

import pe.edu.sistema.util.Formato;

/**
 * Modelo de apoyo para el reporte: valor del inventario agrupado por categoría.
 */
public class CategoriaResumen {

    private final String categoria;
    private final int cantidad;
    private final double valor;
    private final double porcentaje;
    private final double anchoBarra;

    public CategoriaResumen(String categoria, int cantidad, double valor, double porcentaje, double anchoBarra) {
        this.categoria = categoria;
        this.cantidad = cantidad;
        this.valor = valor;
        this.porcentaje = porcentaje;
        this.anchoBarra = anchoBarra;
    }

    public String getCategoria() {
        return categoria;
    }

    public int getCantidad() {
        return cantidad;
    }

    public double getValor() {
        return valor;
    }

    public String getValorTexto() {
        return Formato.soles(valor);
    }

    public String getPorcentajeTexto() {
        return String.format(java.util.Locale.US, "%.1f%%", porcentaje);
    }

    /** Ancho de la barra en porcentaje (0-100) respecto a la categoría de mayor valor. */
    public String getAnchoBarra() {
        return String.format(java.util.Locale.US, "%.1f", Math.max(anchoBarra, 1.5));
    }
}
