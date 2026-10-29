package pe.edu.sistema.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import pe.edu.sistema.util.Formato;

/**
 * Modelo: representa un pedido o compra realizada por un usuario con rol Cliente en NEXORA.
 */
public class Pedido {

    public static final String CONFIRMADO = "Confirmado";
    public static final String EN_PREPARACION = "En preparación";
    public static final String ENVIADO = "Enviado";
    public static final String ENTREGADO = "Entregado";
    public static final String ANULADO = "Anulado";

    /** Todos los estados, en el orden en que se muestran. */
    public static final List<String> ESTADOS = List.of(CONFIRMADO, EN_PREPARACION, ENVIADO, ENTREGADO, ANULADO);

    /** Avance normal de un pedido pagado; Anulado queda fuera del flujo. */
    private static final List<String> FLUJO = List.of(CONFIRMADO, EN_PREPARACION, ENVIADO, ENTREGADO);

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm");

    private int idPedido;
    private String usernameCliente;
    private String nombreCliente;
    private String codigoProducto;
    private String nombreProducto;
    private int cantidad;
    private double precioUnitario;
    private String fecha;
    private Pago pago;
    private String estado = CONFIRMADO;
    private String fechaActualizacion;
    private LocalDateTime fechaHora;

    // Datos de entrega y logística
    private String tipoEnvio = "Envío a Domicilio Express";
    private String departamento = "Lima";
    private String distrito = "Lima";
    private String direccionEnvio = "Dirección registrada de cliente";
    private String referenciaEnvio = "";
    private String telefonoContacto = "";
    private String correoContacto = "";
    private String codigoSeguimiento = "";
    private String notasPedido = "";

    public Pedido() {
    }

    public Pedido(int idPedido, String usernameCliente, String nombreCliente,
                  String codigoProducto, String nombreProducto, int cantidad,
                  double precioUnitario, String fecha) {
        this.idPedido = idPedido;
        this.usernameCliente = usernameCliente;
        this.nombreCliente = nombreCliente;
        this.codigoProducto = codigoProducto;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.fecha = fecha;
    }

    public int getIdPedido() {
        return idPedido;
    }

    public void setIdPedido(int idPedido) {
        this.idPedido = idPedido;
    }

    public String getUsernameCliente() {
        return usernameCliente;
    }

    public void setUsernameCliente(String usernameCliente) {
        this.usernameCliente = usernameCliente;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
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

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public double getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(double precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public double getTotal() {
        return precioUnitario * cantidad;
    }

    public String getPrecioUnitarioTexto() {
        return Formato.soles(precioUnitario);
    }

    public String getTotalTexto() {
        return Formato.soles(getTotal());
    }

    public String getCodigoPedido() {
        return String.format("PED-%04d", idPedido);
    }

    public String getFechaTexto() {
        return fecha;
    }

    /** Pago aprobado asociado; null en pedidos sin pago registrado. */
    public Pago getPago() {
        return pago;
    }

    public void setPago(Pago pago) {
        this.pago = pago;
    }

    /** "Pagado" con pago aprobado, "Reembolsado" si se anuló; "Pendiente de pago" en otro caso. */
    public String getEstadoPago() {
        if (pago != null && pago.isAprobado()) {
            return "Pagado";
        }
        return pago != null && pago.isReembolsado() ? "Reembolsado" : "Pendiente de pago";
    }

    // ---------------------------------------------------------------- estado del pedido

    /** Confirmado, En preparación, Enviado, Entregado o Anulado (ver {@link #ESTADOS}). */
    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    /** Fecha del último cambio de estado (dd/MM/yyyy · HH:mm); null si nunca cambió. */
    public String getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(String fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    /** Fecha del pedido para filtros y reportes; si no se asignó, se obtiene del texto de {@link #getFecha()}. */
    public LocalDateTime getFechaHora() {
        if (fechaHora == null && fecha != null) {
            try {
                return LocalDateTime.parse(fecha, FORMATO_FECHA);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public boolean isAnulado() {
        return ANULADO.equals(estado);
    }

    /** Estado al que puede avanzar el pedido; null si ya está entregado o anulado. */
    public String getSiguienteEstado() {
        int i = FLUJO.indexOf(estado);
        return i >= 0 && i < FLUJO.size() - 1 ? FLUJO.get(i + 1) : null;
    }

    /** Solo se anula antes del envío: Confirmado o En preparación. */
    public boolean isAnulable() {
        return CONFIRMADO.equals(estado) || EN_PREPARACION.equals(estado);
    }

    /** Cuenta para ingresos: tiene pago aprobado y no está anulado. */
    public boolean isVentaValida() {
        return !isAnulado() && pago != null && pago.isAprobado();
    }

    public String getTipoEnvio() {
        return tipoEnvio;
    }

    public void setTipoEnvio(String tipoEnvio) {
        this.tipoEnvio = tipoEnvio;
    }

    public String getDepartamento() {
        return departamento;
    }

    public void setDepartamento(String departamento) {
        this.departamento = departamento;
    }

    public String getDistrito() {
        return distrito;
    }

    public void setDistrito(String distrito) {
        this.distrito = distrito;
    }

    public String getDireccionEnvio() {
        return direccionEnvio;
    }

    public void setDireccionEnvio(String direccionEnvio) {
        this.direccionEnvio = direccionEnvio;
    }

    public String getReferenciaEnvio() {
        return referenciaEnvio;
    }

    public void setReferenciaEnvio(String referenciaEnvio) {
        this.referenciaEnvio = referenciaEnvio;
    }

    public String getTelefonoContacto() {
        return telefonoContacto;
    }

    public void setTelefonoContacto(String telefonoContacto) {
        this.telefonoContacto = telefonoContacto;
    }

    public String getCorreoContacto() {
        return correoContacto;
    }

    public void setCorreoContacto(String correoContacto) {
        this.correoContacto = correoContacto;
    }

    public String getCodigoSeguimiento() {
        return codigoSeguimiento;
    }

    public void setCodigoSeguimiento(String codigoSeguimiento) {
        this.codigoSeguimiento = codigoSeguimiento;
    }

    public String getNotasPedido() {
        return notasPedido;
    }

    public void setNotasPedido(String notasPedido) {
        this.notasPedido = notasPedido;
    }

    public boolean isRetiroEnTienda() {
        return tipoEnvio != null && tipoEnvio.toUpperCase().contains("RETIRO");
    }
}
