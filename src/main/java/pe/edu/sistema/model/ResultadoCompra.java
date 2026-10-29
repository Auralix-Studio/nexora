package pe.edu.sistema.model;

import java.util.Collections;
import java.util.Map;

/**
 * Resultado de una compra con pago: pedido registrado, errores de formulario o pago rechazado.
 */
public final class ResultadoCompra {

    private final Pedido pedido;
    private final Map<String, String> errores;
    private final String error;

    private ResultadoCompra(Pedido pedido, Map<String, String> errores, String error) {
        this.pedido = pedido;
        this.errores = errores == null ? Collections.emptyMap() : errores;
        this.error = error;
    }

    /** Pago aprobado: el pedido ya tiene su {@link Pago} asociado. */
    public static ResultadoCompra exito(Pedido pedido) {
        return new ResultadoCompra(pedido, null, null);
    }

    /** Datos del formulario inválidos (campo -> mensaje). No se contactó a la pasarela. */
    public static ResultadoCompra invalido(Map<String, String> errores) {
        return new ResultadoCompra(null, errores, null);
    }

    /** Pago rechazado o compra imposible (stock, permisos). El stock no cambia. */
    public static ResultadoCompra fallo(String mensaje) {
        return new ResultadoCompra(null, null, mensaje);
    }

    public boolean isExito() {
        return pedido != null;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public Map<String, String> getErrores() {
        return errores;
    }

    public String getError() {
        return error;
    }
}
