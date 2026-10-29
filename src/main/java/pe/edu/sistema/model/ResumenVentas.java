package pe.edu.sistema.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pe.edu.sistema.util.Formato;

/**
 * Modelo de apoyo para el panel de administración: indicadores de ventas, pedidos e inventario.
 * Solo cuentan como venta los pedidos con pago aprobado que no están anulados.
 */
public class ResumenVentas {

    private double ingresosHoy;
    private double ingresosMes;
    private double ingresosTotal;
    private int pedidosHoy;
    private int pedidosMes;
    private int pedidosTotal;
    private int unidadesVendidas;
    private Map<String, Integer> pedidosPorEstado = new LinkedHashMap<>();
    private List<FilaReporte> topProductos = new ArrayList<>();
    private List<Producto> stockBajo = new ArrayList<>();
    private List<Pedido> ultimosPedidos = new ArrayList<>();

    public double getIngresosHoy() {
        return ingresosHoy;
    }

    public void setIngresosHoy(double ingresosHoy) {
        this.ingresosHoy = ingresosHoy;
    }

    public double getIngresosMes() {
        return ingresosMes;
    }

    public void setIngresosMes(double ingresosMes) {
        this.ingresosMes = ingresosMes;
    }

    public double getIngresosTotal() {
        return ingresosTotal;
    }

    public void setIngresosTotal(double ingresosTotal) {
        this.ingresosTotal = ingresosTotal;
    }

    public int getPedidosHoy() {
        return pedidosHoy;
    }

    public void setPedidosHoy(int pedidosHoy) {
        this.pedidosHoy = pedidosHoy;
    }

    public int getPedidosMes() {
        return pedidosMes;
    }

    public void setPedidosMes(int pedidosMes) {
        this.pedidosMes = pedidosMes;
    }

    /** Ventas válidas (sin anulados). */
    public int getPedidosTotal() {
        return pedidosTotal;
    }

    public void setPedidosTotal(int pedidosTotal) {
        this.pedidosTotal = pedidosTotal;
    }

    public int getUnidadesVendidas() {
        return unidadesVendidas;
    }

    public void setUnidadesVendidas(int unidadesVendidas) {
        this.unidadesVendidas = unidadesVendidas;
    }

    /** Estado -> número de pedidos, en el orden de {@link Pedido#ESTADOS} (incluye los que tienen 0). */
    public Map<String, Integer> getPedidosPorEstado() {
        return pedidosPorEstado;
    }

    public void setPedidosPorEstado(Map<String, Integer> pedidosPorEstado) {
        this.pedidosPorEstado = pedidosPorEstado;
    }

    /** Los productos más vendidos por monto. */
    public List<FilaReporte> getTopProductos() {
        return topProductos;
    }

    public void setTopProductos(List<FilaReporte> topProductos) {
        this.topProductos = topProductos;
    }

    /** Productos con stock menor o igual al umbral, de menor a mayor stock. */
    public List<Producto> getStockBajo() {
        return stockBajo;
    }

    public void setStockBajo(List<Producto> stockBajo) {
        this.stockBajo = stockBajo;
    }

    /** Pedidos más recientes, de cualquier estado. */
    public List<Pedido> getUltimosPedidos() {
        return ultimosPedidos;
    }

    public void setUltimosPedidos(List<Pedido> ultimosPedidos) {
        this.ultimosPedidos = ultimosPedidos;
    }

    /** Ingreso total entre el número de ventas; 0 si no hay ventas. */
    public double getTicketPromedio() {
        return pedidosTotal == 0 ? 0 : Pago.redondear(ingresosTotal / pedidosTotal);
    }

    public String getIngresosHoyTexto() {
        return Formato.soles(ingresosHoy);
    }

    public String getIngresosMesTexto() {
        return Formato.soles(ingresosMes);
    }

    public String getIngresosTotalTexto() {
        return Formato.soles(ingresosTotal);
    }

    public String getTicketPromedioTexto() {
        return Formato.soles(getTicketPromedio());
    }

    /** Pedidos pagados que esperan atención del almacén (Confirmado + En preparación). */
    public int getPedidosPendientes() {
        return pedidosPorEstado.getOrDefault(Pedido.CONFIRMADO, 0)
                + pedidosPorEstado.getOrDefault(Pedido.EN_PREPARACION, 0);
    }
}
