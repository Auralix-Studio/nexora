package pe.edu.sistema.model;

import pe.edu.sistema.util.Formato;

/**
 * Modelo: pago autorizado por la pasarela simulada y comprobante emitido para un pedido.
 * Nunca guarda el número completo de la tarjeta ni el CVV; solo la marca y los últimos dígitos.
 */
public class Pago {

    public static final String METODO_TARJETA = "TARJETA";
    public static final String METODO_YAPE = "YAPE";

    public static final String APROBADO = "Aprobado";
    public static final String RECHAZADO = "Rechazado";
    /** Pago devuelto al anular el pedido (reembolso simulado). */
    public static final String REEMBOLSADO = "Reembolsado";

    public static final String BOLETA = "Boleta";
    public static final String FACTURA = "Factura";

    /** Tasa del IGV incluida en los precios de venta. */
    public static final double IGV = 0.18;

    private int idPago;
    private int idPedido;
    private String metodo;
    private String marca;
    private String ultimosDigitos;
    private String titular;
    private double monto;
    private String estado;
    private String motivoRechazo;
    private String codigoAutorizacion;
    private String codigoOperacion;
    private String tipoComprobante;
    private String numeroComprobante;
    private String documentoCliente;
    private String razonSocial;
    private String fecha;

    public Pago() {
    }

    public int getIdPago() {
        return idPago;
    }

    public void setIdPago(int idPago) {
        this.idPago = idPago;
    }

    public int getIdPedido() {
        return idPedido;
    }

    public void setIdPedido(int idPedido) {
        this.idPedido = idPedido;
    }

    public String getMetodo() {
        return metodo;
    }

    public void setMetodo(String metodo) {
        this.metodo = metodo;
    }

    /** Visa, Mastercard, American Express o Yape. */
    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    /** Últimos 4 dígitos de la tarjeta o del celular Yape. */
    public String getUltimosDigitos() {
        return ultimosDigitos;
    }

    public void setUltimosDigitos(String ultimosDigitos) {
        this.ultimosDigitos = ultimosDigitos;
    }

    public String getTitular() {
        return titular;
    }

    public void setTitular(String titular) {
        this.titular = titular;
    }

    public double getMonto() {
        return monto;
    }

    public void setMonto(double monto) {
        this.monto = monto;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public String getCodigoAutorizacion() {
        return codigoAutorizacion;
    }

    public void setCodigoAutorizacion(String codigoAutorizacion) {
        this.codigoAutorizacion = codigoAutorizacion;
    }

    public String getCodigoOperacion() {
        return codigoOperacion;
    }

    public void setCodigoOperacion(String codigoOperacion) {
        this.codigoOperacion = codigoOperacion;
    }

    public String getTipoComprobante() {
        return tipoComprobante;
    }

    public void setTipoComprobante(String tipoComprobante) {
        this.tipoComprobante = tipoComprobante;
    }

    /** Serie y correlativo: B001-000001 (boleta) o F001-000001 (factura). */
    public String getNumeroComprobante() {
        return numeroComprobante;
    }

    public void setNumeroComprobante(String numeroComprobante) {
        this.numeroComprobante = numeroComprobante;
    }

    /** DNI (8 dígitos) para boleta o RUC (11 dígitos) para factura. */
    public String getDocumentoCliente() {
        return documentoCliente;
    }

    public void setDocumentoCliente(String documentoCliente) {
        this.documentoCliente = documentoCliente;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    // ---------------------------------------------------------------- cálculo de importes

    /** Redondea un importe en soles a céntimos. */
    public static double redondear(double importe) {
        return Math.round(importe * 100) / 100.0;
    }

    /** Valor de venta sin IGV de un total que ya lo incluye (total ÷ 1.18), a céntimos. */
    public static double subtotalDe(double total) {
        return redondear(total / (1 + IGV));
    }

    /** IGV incluido en un total: total menos su subtotal, a céntimos. */
    public static double igvDe(double total) {
        return redondear(total - subtotalDe(total));
    }

    // ---------------------------------------------------------------- calculados

    public boolean isAprobado() {
        return APROBADO.equals(estado);
    }

    public boolean isReembolsado() {
        return REEMBOLSADO.equals(estado);
    }

    public boolean isFactura() {
        return FACTURA.equals(tipoComprobante);
    }

    /** "Visa •••• 1111" o "Yape •••• 5678". */
    public String getMetodoTexto() {
        return (marca == null ? "" : marca) + " •••• " + (ultimosDigitos == null ? "" : ultimosDigitos);
    }

    /** Valor de venta sin IGV (los precios del catálogo ya incluyen IGV). */
    public double getSubtotal() {
        return subtotalDe(monto);
    }

    public double getIgvMonto() {
        return igvDe(monto);
    }

    public String getMontoTexto() {
        return Formato.soles(monto);
    }

    public String getSubtotalTexto() {
        return Formato.soles(getSubtotal());
    }

    public String getIgvTexto() {
        return Formato.soles(getIgvMonto());
    }
}
