package pe.edu.sistema.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilidad de pruebas: guarda y restaura propiedades del sistema para que una prueba que cambia la configuración
 * (entorno, base de datos) no afecte a las siguientes.
 */
public final class PropiedadesSistema {

    /** Todas las propiedades que leen {@link ConfigApp} y {@link ConexionBD}. */
    public static final String[] CLAVES = {
        ConfigApp.ENTORNO, ConfigApp.DEMO, ConfigApp.VERSION,
        "DB_URL", "DB_USER", "DB_PASSWORD", "DB_CONNECTION_TIMEOUT_MS"
    };

    /** URL a un puerto cerrado: la conexión falla de inmediato (la misma que usa surefire). */
    public static final String URL_PUERTO_CERRADO =
            "jdbc:sqlserver://localhost:1;databaseName=BD_NEXORA;encrypt=true;trustServerCertificate=true;loginTimeout=1;";

    private PropiedadesSistema() {
    }

    /** Valores actuales (null si no estaban definidas), para devolverlos con {@link #restaurar}. */
    public static Map<String, String> guardar() {
        Map<String, String> copia = new HashMap<>();
        for (String clave : CLAVES) {
            copia.put(clave, System.getProperty(clave));
        }
        return copia;
    }

    public static void restaurar(Map<String, String> copia) {
        for (Map.Entry<String, String> e : copia.entrySet()) {
            if (e.getValue() == null) {
                System.clearProperty(e.getKey());
            } else {
                System.setProperty(e.getKey(), e.getValue());
            }
        }
    }

    /** Quita las propiedades de configuración de NEXORA, salvo la URL de la base (sigue en puerto cerrado). */
    public static void limpiarConfiguracion() {
        for (String clave : CLAVES) {
            System.clearProperty(clave);
        }
        System.setProperty("DB_URL", URL_PUERTO_CERRADO);
    }
}
