package pe.edu.sistema.model;

import java.util.List;

/**
 * Modelo: una acción registrada en la auditoría (quién, qué, sobre qué, desde dónde y cuándo).
 */
public class RegistroAuditoria {

    public static final String INICIO_SESION = "Inicio de sesión";
    public static final String ACCESO_FALLIDO = "Acceso fallido";
    public static final String CUENTA_BLOQUEADA = "Cuenta bloqueada";
    public static final String COMPRA = "Compra";
    public static final String PRODUCTO_REGISTRADO = "Producto registrado";
    public static final String PRODUCTO_ACTUALIZADO = "Producto actualizado";
    public static final String PRODUCTO_ELIMINADO = "Producto eliminado";
    public static final String PEDIDO_ESTADO = "Estado de pedido";
    public static final String PEDIDO_ANULADO = "Pedido anulado";
    public static final String STOCK_AJUSTE = "Movimiento de stock";
    public static final String USUARIO_CREADO = "Usuario creado";
    public static final String USUARIO_ESTADO = "Usuario activado/desactivado";
    public static final String USUARIO_ROL = "Cambio de rol";
    public static final String USUARIO_CLAVE = "Contraseña restablecida";
    public static final String CATEGORIA = "Categoría";

    /** Acciones disponibles para filtrar la auditoría. */
    public static final List<String> ACCIONES = List.of(INICIO_SESION, ACCESO_FALLIDO, CUENTA_BLOQUEADA, COMPRA,
            PRODUCTO_REGISTRADO, PRODUCTO_ACTUALIZADO, PRODUCTO_ELIMINADO, PEDIDO_ESTADO, PEDIDO_ANULADO,
            STOCK_AJUSTE, USUARIO_CREADO, USUARIO_ESTADO, USUARIO_ROL, USUARIO_CLAVE, CATEGORIA);

    private int idAuditoria;
    private String usuario;
    private String rol;
    private String accion;
    private String entidad;
    private String detalle;
    private String ip;
    private String fecha;

    public RegistroAuditoria() {
    }

    public int getIdAuditoria() {
        return idAuditoria;
    }

    public void setIdAuditoria(int idAuditoria) {
        this.idAuditoria = idAuditoria;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    /** Sobre qué se actuó, p. ej. "Producto P001" o "Pedido PED-0003". */
    public String getEntidad() {
        return entidad;
    }

    public void setEntidad(String entidad) {
        this.entidad = entidad;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    /** dd/MM/yyyy · HH:mm */
    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    /** Acciones de seguridad que conviene resaltar en la vista. */
    public boolean isAlerta() {
        return ACCESO_FALLIDO.equals(accion) || CUENTA_BLOQUEADA.equals(accion)
                || PEDIDO_ANULADO.equals(accion) || PRODUCTO_ELIMINADO.equals(accion);
    }
}
