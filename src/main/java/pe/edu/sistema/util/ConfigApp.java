package pe.edu.sistema.util;

/**
 * Configuración de NEXORA por variables de entorno (o propiedades del sistema con el mismo nombre, que mandan).
 *
 * <ul>
 *   <li>{@code NEXORA_ENTORNO}: {@code produccion} activa el modo de producción (sin respaldo en memoria);
 *       cualquier otro valor, o ninguno, es desarrollo.</li>
 *   <li>{@code NEXORA_DEMO}: {@code true} muestra la cinta de demostración y las cuentas demo.</li>
 *   <li>{@code NEXORA_VERSION}: versión que se publica en {@code /salud} y en los recursos estáticos (por defecto {@code dev}).</li>
 * </ul>
 *
 * No guarda estado: cada consulta lee la configuración vigente, de modo que las pruebas pueden cambiarla
 * con {@link System#setProperty(String, String)}.
 */
public final class ConfigApp {

    public static final String ENTORNO = "NEXORA_ENTORNO";
    public static final String DEMO = "NEXORA_DEMO";
    public static final String VERSION = "NEXORA_VERSION";
    public static final String COOKIE_SEGURA = "NEXORA_COOKIE_SEGURA";

    private static final String VERSION_POR_DEFECTO = "dev";

    private ConfigApp() {
    }

    /** true si NEXORA_ENTORNO es {@code produccion} (sin distinguir mayúsculas). */
    public static boolean esProduccion() {
        return "produccion".equalsIgnoreCase(leer(ENTORNO, ""));
    }

    /** true si NEXORA_DEMO es {@code true} (sin distinguir mayúsculas). */
    public static boolean esDemo() {
        return "true".equalsIgnoreCase(leer(DEMO, ""));
    }

    /**
     * Fuerza el atributo Secure en la cookie de sesión. Por defecto sí en producción; NEXORA_COOKIE_SEGURA=false
     * lo desactiva para probar el contenedor por http://localhost (Tomcat igual la marca Secure si la petición
     * llega por HTTPS, p. ej. tras el ingress de Azure con RemoteIpValve).
     */
    public static boolean cookieSegura() {
        String valor = leer(COOKIE_SEGURA, "");
        return valor.isEmpty() ? esProduccion() : "true".equalsIgnoreCase(valor);
    }

    /** NEXORA_VERSION o {@code dev} si no está definida. */
    public static String version() {
        return leer(VERSION, VERSION_POR_DEFECTO);
    }

    /** Nombre del entorno para mostrar: {@code produccion} o {@code desarrollo}. */
    public static String entorno() {
        return esProduccion() ? "produccion" : "desarrollo";
    }

    /**
     * Valor de la propiedad del sistema y, si no existe o está vacía, de la variable de entorno del mismo nombre.
     *
     * @return el valor sin espacios en los extremos, o {@code porDefecto} si no hay ninguno
     */
    public static String leer(String clave, String porDefecto) {
        String prop = System.getProperty(clave);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv(clave);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return porDefecto;
    }
}
