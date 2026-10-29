package pe.edu.sistema.model;

import java.util.Locale;
import pe.edu.sistema.util.Formato;

/**
 * Modelo de apoyo para los reportes de ventas: una fila agrupada (día, categoría, método o producto).
 */
public class FilaReporte {

    private final String etiqueta;
    private final int cantidad;
    private final double monto;
    private final double porcentaje;
    private final double anchoBarra;

    /**
     * @param cantidad   unidades vendidas o número de pedidos, según el reporte
     * @param porcentaje participación del monto sobre el total (0-100)
     * @param anchoBarra ancho relativo a la fila de mayor monto (0-100)
     */
    public FilaReporte(String etiqueta, int cantidad, double monto, double porcentaje, double anchoBarra) {
        this.etiqueta = etiqueta;
        this.cantidad = cantidad;
        this.monto = monto;
        this.porcentaje = porcentaje;
        this.anchoBarra = anchoBarra;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public int getCantidad() {
        return cantidad;
    }

    public double getMonto() {
        return monto;
    }

    public double getPorcentaje() {
        return porcentaje;
    }

    public String getMontoTexto() {
        return Formato.soles(monto);
    }

    public String getPorcentajeTexto() {
        return String.format(Locale.US, "%.1f%%", porcentaje);
    }

    /** Ancho de la barra en porcentaje, con un mínimo visible. */
    public String getAnchoBarra() {
        return String.format(Locale.US, "%.1f", Math.max(anchoBarra, 1.5));
    }
}
