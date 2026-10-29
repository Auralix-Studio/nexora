package pe.edu.sistema.util;

/**
 * Los datos no se pueden leer ni escribir (base de datos sin configurar o inaccesible) y el entorno no admite
 * el respaldo en memoria. Es una {@link IllegalStateException} para que el código existente la trate igual;
 * el filtro de cabeceras la convierte en una respuesta 503.
 */
public class DatosNoDisponiblesException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /** Mensaje estándar que ve quien llama (sin detalles internos ni secretos). */
    public static final String MENSAJE = "El servicio de datos no está disponible en este momento.";

    public DatosNoDisponiblesException() {
        super(MENSAJE);
    }

    public DatosNoDisponiblesException(Throwable causa) {
        super(MENSAJE, causa);
    }
}
