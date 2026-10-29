package pe.edu.sistema.model;

/**
 * Modelo: categoría del catálogo. Una categoría inactiva no se ofrece al registrar productos
 * ni en la tienda, pero conserva los productos que ya la usan.
 */
public class Categoria {

    private int idCategoria;
    private String nombre;
    private String descripcion;
    private boolean activo = true;
    private int cantidadProductos;

    public Categoria() {
    }

    public Categoria(int idCategoria, String nombre, String descripcion, boolean activo) {
        this.idCategoria = idCategoria;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.activo = activo;
    }

    public int getIdCategoria() {
        return idCategoria;
    }

    public void setIdCategoria(int idCategoria) {
        this.idCategoria = idCategoria;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /** Productos que pertenecen a la categoría; si es mayor que cero no se puede eliminar. */
    public int getCantidadProductos() {
        return cantidadProductos;
    }

    public void setCantidadProductos(int cantidadProductos) {
        this.cantidadProductos = cantidadProductos;
    }

    public boolean isEliminable() {
        return cantidadProductos == 0;
    }
}
