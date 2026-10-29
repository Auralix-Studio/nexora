package pe.edu.sistema.model;

/**
 * Modelo: representa un usuario autenticado en el sistema NEXORA y su rol de acceso
 * (Administrador, Almacén, Cliente o Consulta).
 */
public class Usuario {

    public static final String ROL_ADMIN = "Administrador";
    public static final String ROL_ALMACEN = "Almacén";
    public static final String ROL_CLIENTE = "Cliente";
    public static final String ROL_CONSULTA = "Consulta";

    private int idUsuario;
    private String username;
    private String nombreCompleto;
    private String correo;
    private String rol;
    private boolean activo;
    /** DNI del cliente (8 dígitos); null si no lo registró. */
    private String dni;
    /** Celular del cliente (9 dígitos, empieza con 9); null si no lo registró. */
    private String telefono;

    public Usuario() {
    }

    public Usuario(int idUsuario, String username, String nombreCompleto, String correo, String rol, boolean activo) {
        this.idUsuario = idUsuario;
        this.username = username;
        this.nombreCompleto = nombreCompleto;
        this.correo = correo;
        this.rol = rol;
        this.activo = activo;
    }

    public Usuario(int idUsuario, String username, String nombreCompleto, String correo, String rol, boolean activo,
            String dni, String telefono) {
        this(idUsuario, username, nombreCompleto, correo, rol, activo);
        this.dni = dni;
        this.telefono = telefono;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    /** Permiso total: Administrador. */
    public boolean isAdministrador() {
        return ROL_ADMIN.equalsIgnoreCase(rol);
    }

    /** Verifica si el usuario tiene el rol Cliente. */
    public boolean isCliente() {
        return ROL_CLIENTE.equalsIgnoreCase(rol);
    }

    /** Verifica si el usuario tiene el rol Almacén. */
    public boolean isAlmacen() {
        return ROL_ALMACEN.equalsIgnoreCase(rol);
    }

    /** Verifica si el usuario tiene el rol Consulta. */
    public boolean isConsulta() {
        return ROL_CONSULTA.equalsIgnoreCase(rol);
    }

    /** Puede registrar nuevos productos: Administrador o Almacén. */
    public boolean isPuedeRegistrar() {
        return isAdministrador() || ROL_ALMACEN.equalsIgnoreCase(rol);
    }

    /** Puede editar productos existentes: Administrador o Almacén. */
    public boolean isPuedeEditar() {
        return isAdministrador() || ROL_ALMACEN.equalsIgnoreCase(rol);
    }

    /** Puede eliminar productos: únicamente Administrador. */
    public boolean isPuedeEliminar() {
        return isAdministrador();
    }

    /**
     * Puede comprar productos del catálogo: únicamente el rol Cliente.
     * El personal (Administrador, Almacén, Consulta) gestiona inventario y reportes, no compra.
     */
    public boolean isPuedeComprar() {
        return isCliente();
    }

    // ---------------------------------------------------------------- módulo de administración

    /** Personal de la tienda: Administrador, Almacén o Consulta. Accede al panel y a los reportes. */
    public boolean isPersonal() {
        return isAdministrador() || isAlmacen() || isConsulta();
    }

    /** Avanza pedidos (En preparación, Enviado, Entregado): Administrador y Almacén. */
    public boolean isPuedeGestionarPedidos() {
        return isAdministrador() || isAlmacen();
    }

    /** Anula pedidos con reembolso y devolución de stock: solo Administrador. */
    public boolean isPuedeAnularPedidos() {
        return isAdministrador();
    }

    /** Registra entradas y ajustes de stock: Administrador y Almacén. */
    public boolean isPuedeAjustarStock() {
        return isAdministrador() || isAlmacen();
    }

    /** Usuarios, categorías y auditoría: solo Administrador. */
    public boolean isPuedeAdministrarSistema() {
        return isAdministrador();
    }

    /** Nivel de acciones para la tabla de productos: todas | editar | comprar | ninguna. */
    public String getAccionesTabla() {
        if (isPuedeEliminar()) {
            return "todas";
        }
        if (isPuedeEditar()) {
            return "editar";
        }
        if (isCliente()) {
            return "comprar";
        }
        return "ninguna";
    }

    /** Descripción breve de los permisos del rol activo. */
    public String getDescripcionPermisos() {
        if (isAdministrador()) {
            return "Control total: registrar, editar, eliminar productos, gestionar clientes y reportes";
        }
        if (ROL_ALMACEN.equalsIgnoreCase(rol)) {
            return "Operación de inventario: registrar, editar productos y consultar reportes";
        }
        if (isCliente()) {
            return "Portal de Cliente: explorar catálogo disponible, comprar productos y ver mis pedidos";
        }
        return "Solo lectura: listar, buscar productos y visualizar reportes";
    }
}
