package pe.edu.sistema.model;

/**
 * Datos del formulario de pago tal como llegan del checkout. Vive solo durante la petición:
 * el número de tarjeta y el CVV nunca se guardan en sesión, base de datos ni registros.
 */
public class SolicitudPago {

    private String metodo;
    private String numeroTarjeta;
    private String titular;
    private String vencimiento;
    private String cvv;
    private String celular;
    private String codigoYape;
    private String tipoComprobante;
    private String documento;
    private String razonSocial;

    // Datos de entrega y despacho de pedido
    private String tipoEnvio = "DOMICILIO"; // DOMICILIO o RETIRO
    private String departamento = "Lima";
    private String distrito = "";
    private String direccion = "";
    private String referencia = "";
    private String telefonoContacto = "";
    private String correoComprobante = "";
    private String notasPedido = "";

    public SolicitudPago() {
    }

    /** TARJETA o YAPE (ver constantes de {@link Pago}). */
    public String getMetodo() {
        return metodo;
    }

    public void setMetodo(String metodo) {
        this.metodo = metodo;
    }

    public String getNumeroTarjeta() {
        return numeroTarjeta;
    }

    public void setNumeroTarjeta(String numeroTarjeta) {
        this.numeroTarjeta = numeroTarjeta;
    }

    public String getTitular() {
        return titular;
    }

    public void setTitular(String titular) {
        this.titular = titular;
    }

    /** Formato MM/AA. */
    public String getVencimiento() {
        return vencimiento;
    }

    public void setVencimiento(String vencimiento) {
        this.vencimiento = vencimiento;
    }

    public String getCvv() {
        return cvv;
    }

    public void setCvv(String cvv) {
        this.cvv = cvv;
    }

    /** Celular Yape: 9 dígitos que empiezan con 9. */
    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }

    /** Código de aprobación Yape de 6 dígitos. */
    public String getCodigoYape() {
        return codigoYape;
    }

    public void setCodigoYape(String codigoYape) {
        this.codigoYape = codigoYape;
    }

    /** Boleta o Factura (ver constantes de {@link Pago}). */
    public String getTipoComprobante() {
        return tipoComprobante;
    }

    public void setTipoComprobante(String tipoComprobante) {
        this.tipoComprobante = tipoComprobante;
    }

    /** DNI para boleta, RUC para factura. */
    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
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

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public String getTelefonoContacto() {
        return telefonoContacto;
    }

    public void setTelefonoContacto(String telefonoContacto) {
        this.telefonoContacto = telefonoContacto;
    }

    public String getCorreoComprobante() {
        return correoComprobante;
    }

    public void setCorreoComprobante(String correoComprobante) {
        this.correoComprobante = correoComprobante;
    }

    public String getNotasPedido() {
        return notasPedido;
    }

    public void setNotasPedido(String notasPedido) {
        this.notasPedido = notasPedido;
    }
}
