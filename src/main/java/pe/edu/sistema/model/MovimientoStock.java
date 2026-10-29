package pe.edu.sistema.model;

import java.util.List;

/**
 * Modelo: movimiento del kardex de un producto. La cantidad lleva signo:
 * positiva entra al almacén y negativa sale.
 */
public class MovimientoStock {

    public static final String ENTRADA = "Entrada";
    public static final String SALIDA = "Salida";
    public static final String AJUSTE = "Ajuste";
    public static final String DEVOLUCION = "Devolución";

    /** Tipos que el personal registra a mano; Salida y Devolución los genera el sistema. */
    public static final List<String> TIPOS_MANUALES = List.of(ENTRADA, AJUSTE);

    private int idMovimiento;
    private String codigoProducto;
    private String nombreProducto;
    private String tipo;
    private int cantidad;
    private int stockResultante;
    private String motivo;
    private String usuario;
    private String fecha;

    public MovimientoStock() {
    }

    public int getIdMovimiento() {
        return idMovimiento;
    }

    public void setIdMovimiento(int idMovimiento) {
        this.idMovimiento = idMovimiento;
    }

    public String getCodigoProducto() {
        return codigoProducto;
    }

    public void setCodigoProducto(String codigoProducto) {
        this.codigoProducto = codigoProducto;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public void setNombreProducto(String nombreProducto) {
        this.nombreProducto = nombreProducto;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public int getStockResultante() {
        return stockResultante;
    }

    public void setStockResultante(int stockResultante) {
        this.stockResultante = stockResultante;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    /** Usuario que lo registró; "sistema" si no hay uno asociado. */
    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    /** dd/MM/yyyy · HH:mm */
    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    /** "+5" o "-2". */
    public String getCantidadTexto() {
        return (cantidad > 0 ? "+" : "") + cantidad;
    }

    public boolean isEntrada() {
        return cantidad > 0;
    }
}
