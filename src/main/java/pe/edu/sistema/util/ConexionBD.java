package pe.edu.sistema.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Clase de conexión JDBC del Sistema NEXORA para Microsoft SQL Server y Azure SQL Database (Semana 06).
 *
 * Soporta dos modos de configuración:
 * 1. SQL Server Local (por defecto):
 *    jdbc:sqlserver://localhost:1433;databaseName=BD_NEXORA;encrypt=true;trustServerCertificate=true;loginTimeout=2;
 *
 * 2. Microsoft Azure SQL Database (mediante variables de entorno o propiedades):
 *    jdbc:sqlserver://srv-nexora.database.windows.net:1433;database=BD_NEXORA;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=5;
 *
 * Si el motor SQL Server no se encuentra activo en el equipo, el sistema NEXORA activa
 * automáticamente el modo de respaldo en memoria (ArrayList) sin interrumpir la aplicación.
 */
public final class ConexionBD {

    private static final String DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

    // Parámetros por defecto para MS SQL Server local (Base de datos BD_NEXORA)
    private static final String DEFAULT_URL =
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=BD_NEXORA;"
            + "encrypt=true;trustServerCertificate=true;loginTimeout=10;";
    private static final String DEFAULT_USER = "sa";
    private static final String DEFAULT_PASSWORD = "TuPasswordSQL123";

    private static volatile Boolean bdDisponible = null;
    private static volatile String detalleEstado = "Pendiente de verificación";
    private static volatile long ultimaVerificacion = 0;
    private static final long INTERVALO_REINTENTO_MS = 5000;

    static {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            bdDisponible = Boolean.FALSE;
            detalleEstado = "Driver JDBC de SQL Server no encontrado en el classpath";
        }
    }

    private ConexionBD() {
    }

    /**
     * Obtiene una nueva conexión JDBC a MS SQL Server o Azure SQL Database.
     *
     * @return instancia de {@link Connection} abierta
     * @throws SQLException si no es posible establecer la conexión
     */
    public static Connection obtenerConexion() throws SQLException {
        String url = leerConfig("DB_URL", DEFAULT_URL);
        String usuario = leerConfig("DB_USER", DEFAULT_USER);
        String clave = leerConfig("DB_PASSWORD", DEFAULT_PASSWORD);
        return DriverManager.getConnection(url, usuario, clave);
    }

    /**
     * Verifica si la base de datos SQL Server está activa y accesible.
     * El resultado se guarda en caché; si estaba desconectada, reintenta automáticamente
     * tras un breve intervalo (5 segundos) sin necesidad de reiniciar la aplicación.
     */
    public static boolean estaDisponible() {
        Boolean actual = bdDisponible;
        long ahora = System.currentTimeMillis();
        if (actual != null && actual) {
            return true;
        }
        if (actual != null && (ahora - ultimaVerificacion < INTERVALO_REINTENTO_MS)) {
            return false;
        }
        return probarConexion();
    }

    /**
     * Fuerza una nueva prueba de conexión contra SQL Server / Azure SQL.
     */
    public static synchronized boolean probarConexion() {
        ultimaVerificacion = System.currentTimeMillis();
        try (Connection cn = obtenerConexion()) {
            bdDisponible = (cn != null && !cn.isClosed());
            detalleEstado = "Conectado a MS SQL Server (BD_NEXORA)";
            return bdDisponible;
        } catch (SQLException ex) {
            bdDisponible = Boolean.FALSE;
            detalleEstado = "Respaldo en memoria activo: " + ex.getMessage();
            return false;
        }
    }

    /**
     * Devuelve una descripción legible del estado actual de la persistencia.
     */
    public static String getEstadoTexto() {
        estaDisponible();
        return detalleEstado;
    }

    /**
     * Devuelve la cadena JDBC configurada (sin exponer contraseñas).
     */
    public static String getUrlConfigurada() {
        return leerConfig("DB_URL", DEFAULT_URL);
    }

    private static String leerConfig(String clave, String valorPorDefecto) {
        String prop = System.getProperty(clave);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv(clave);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return valorPorDefecto;
    }
}
