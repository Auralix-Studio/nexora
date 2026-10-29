package pe.edu.sistema.model;

import pe.edu.sistema.util.Formato;

/**
 * Modelo: representa un producto del inventario.
 */
public class Producto {

    public static final String DISPONIBLE = "Disponible";
    public static final String AGOTADO = "Agotado";

    private String codigo;
    private String nombre;
    private String categoria;
    private double precio;
    private int stock;
    private String estado;

    public Producto() {
    }

    public Producto(String codigo, String nombre, String categoria, double precio, int stock) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.categoria = categoria;
        this.precio = precio;
        this.stock = stock;
        this.estado = stock > 0 ? DISPONIBLE : AGOTADO;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public double getPrecio() {
        return precio;
    }

    public void setPrecio(double precio) {
        this.precio = precio;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getEstado() {
        return stock > 0 ? DISPONIBLE : AGOTADO;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    /** Un producto está disponible cuando su stock es mayor que cero. */
    public boolean isDisponible() {
        return stock > 0;
    }

    /** Precio x stock. */
    public double getValorInventario() {
        return precio * stock;
    }

    public String getPrecioTexto() {
        return Formato.soles(precio);
    }

    public String getPrecioPlano() {
        return Formato.decimal(precio);
    }

    public String getValorInventarioTexto() {
        return Formato.soles(getValorInventario());
    }
}
