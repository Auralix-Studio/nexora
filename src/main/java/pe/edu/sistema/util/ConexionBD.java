package pe.edu.sistema.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Acceso JDBC del Sistema NEXORA a Microsoft SQL Server local y a Azure SQL Database.
 *
 * <p>Las conexiones salen de un pool HikariCP perezoso (se crea en el primer uso, sin conexiones mínimas y sin
 * keepalive, para que Azure SQL serverless pueda autopausarse). Se configura con las variables de entorno o
 * propiedades del sistema {@code DB_URL}, {@code DB_USER} y {@code DB_PASSWORD}.</p>
 *
 * <p>Dos modos, según {@link ConfigApp#esProduccion()}:</p>
 * <ul>
 *   <li><b>Desarrollo</b>: si faltan las variables se usan los valores por defecto de SQL Server local. Si el motor
 *       no responde, los servicios usan su respaldo en memoria; la disponibilidad se comprueba cada 5 segundos.</li>
 *   <li><b>Producción</b>: las tres variables son obligatorias y no hay valores por defecto. Nunca existe respaldo
 *       en memoria: sin configuración o con la base caída, los servicios lanzan
 *       {@link DatosNoDisponiblesException} (503). No se sondea la base fuera de {@link #probarConexion()}.
 *       Tras un fallo de conexión, durante {@link #PLAZO_CORTOCIRCUITO_MS} las solicitudes fallan de inmediato y
 *       luego una sola petición vuelve a intentar (reanudación de Azure SQL).</li>
 * </ul>
 */
public final class ConexionBD {

    private static final Logger LOG = Logger.getLogger(ConexionBD.class.getName());

    private static final String DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

    // Parámetros por defecto para MS SQL Server local (solo desarrollo)
    private static final String DEFAULT_URL =
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=BD_NEXORA;"
            + "encrypt=true;trustServerCertificate=true;loginTimeout=10;";
    private static final String DEFAULT_USER = "sa";
    private static final String DEFAULT_PASSWORD = "TuPasswordSQL123";

    private static final List<String> VARIABLES_OBLIGATORIAS = List.of("DB_URL", "DB_USER", "DB_PASSWORD");

    /** Espera máxima por una conexión (opcional: DB_CONNECTION_TIMEOUT_MS). En producción alcanza para reanudar Azure SQL. */
    private static final long TIMEOUT_PRODUCCION_MS = 60_000L;
    private static final long TIMEOUT_DESARROLLO_MS = 10_000L;
    private static final long TIMEOUT_MINIMO_MS = 250L;

    /** Errores de SQL Server / Azure SQL que indican base no disponible (inicio de sesión, pausada, reanudando, límites). */
    private static final Set<Integer> ERRORES_NO_DISPONIBLE =
            Set.of(18456, 4060, 40613, 40197, 40501, 49918, 49919, 49920, 10928, 10929);

    // Estado del modo desarrollo (en producción no se usa: no hay respaldo que activar)
    private static volatile Boolean bdDisponible = null;
    private static volatile String detalleEstado = "Pendiente de verificación";
    private static volatile long ultimaVerificacion = 0;
    private static final long INTERVALO_REINTENTO_MS = 5000;

    /** Producción: tras un fallo de conexión, plazo en que las solicitudes fallan de inmediato antes de reintentar. */
    static final long PLAZO_CORTOCIRCUITO_MS = 15_000L;

    private static final Object CANDADO_POOL = new Object();
    private static volatile HikariDataSource pool;
    /** true tras {@link #cerrar()}: el pool no se vuelve a crear (la aplicación se está deteniendo). */
    private static volatile boolean cerrado;
    /** Solo se reemplaza en pruebas (reloj simulado). */
    static volatile Cortocircuito cortocircuito = new Cortocircuito(PLAZO_CORTOCIRCUITO_MS, System::nanoTime);
    private static final AtomicBoolean AVISO_CONFIGURACION = new AtomicBoolean(false);

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

    // ---------------------------------------------------------------- conexiones

    /**
     * Toma una conexión del pool; al cerrarla vuelve al pool.
     *
     * @return instancia de {@link Connection} abierta
     * @throws SQLException si la base no está configurada (producción) o no es posible conectar
     */
    public static Connection obtenerConexion() throws SQLException {
        if (!estaConfigurada()) {
            throw new SQLTransientConnectionException("La base de datos no está configurada.");
        }
        if (cerrado) {
            throw new SQLNonTransientConnectionException("La aplicación se está deteniendo: el pool está cerrado.");
        }
        boolean produccion = ConfigApp.esProduccion();
        Cortocircuito corte = cortocircuito;
        if (produccion && !corte.permitir()) {
            // Base caída o pausada: respuesta inmediata (503) sin tocar Hikari ni esperar su tiempo de conexión
            throw new DatosNoDisponiblesException();
        }
        try {
            Connection cn = pool().getConnection();
            if (produccion) {
                corte.exito();
            }
            return cn;
        } catch (SQLException | RuntimeException ex) {
            if (produccion) {
                registrarResultadoFallido(corte, ex);
            }
            throw ex;
        }
    }

    /** Un fallo de conexión abre el cortocircuito; un error que no es de conectividad indica que la base respondió. */
    private static void registrarResultadoFallido(Cortocircuito corte, Exception ex) {
        if (ex instanceof SQLException sql && !esFalloDeConexion(sql)) {
            corte.exito();
        } else {
            abrirCortocircuito(corte);
        }
    }

    private static void abrirCortocircuito(Cortocircuito corte) {
        if (corte.fallo()) {
            LOG.log(Level.WARNING, "Base de datos no disponible: durante {0} s las conexiones se rechazan sin consultarla; "
                    + "luego una sola petición volverá a intentarlo.", PLAZO_CORTOCIRCUITO_MS / 1000);
        }
    }

    /**
     * Cortocircuito de producción: tras un fallo de conexión rechaza de inmediato las solicitudes durante un plazo y,
     * vencido este, deja pasar una sola petición de prueba (las demás siguen rechazadas hasta que esa termine).
     * Así la reanudación de Azure SQL serverless la dispara una única petición y el resto recibe un 503 rápido en
     * lugar de esperar el tiempo de conexión de Hikari.
     */
    static final class Cortocircuito {
        private final long plazoNs;
        private final LongSupplier relojNs;
        private boolean abierto;
        private boolean sondeando;
        private long hastaNs;

        Cortocircuito(long plazoMs, LongSupplier relojNs) {
            this.plazoNs = TimeUnit.MILLISECONDS.toNanos(plazoMs);
            this.relojNs = relojNs;
        }

        /** true si la solicitud puede intentar conectar; false si debe fallar de inmediato. */
        synchronized boolean permitir() {
            if (!abierto) {
                return true;
            }
            if (sondeando || relojNs.getAsLong() - hastaNs < 0) {
                return false;
            }
            sondeando = true;   // medio abierto: pasa esta petición y las demás esperan su resultado
            return true;
        }

        /** La conexión funcionó (o la base respondió): cierra el cortocircuito. */
        synchronized void exito() {
            abierto = false;
            sondeando = false;
        }

        /** La conexión falló: rechaza las nuevas solicitudes durante el plazo. @return true si estaba cerrado */
        synchronized boolean fallo() {
            boolean estabaCerrado = !abierto;
            abierto = true;
            sondeando = false;
            hastaNs = relojNs.getAsLong() + plazoNs;
            return estabaCerrado;
        }

        synchronized boolean estaAbierto() {
            return abierto;
        }
    }

    private static HikariDataSource pool() throws SQLException {
        HikariDataSource actual = pool;
        if (actual != null && !actual.isClosed()) {
            return actual;
        }
        synchronized (CANDADO_POOL) {
            if (cerrado) {
                throw new SQLNonTransientConnectionException("La aplicación se está deteniendo: el pool está cerrado.");
            }
            if (pool == null || pool.isClosed()) {
                try {
                    pool = crearPool();
                } catch (RuntimeException ex) {
                    throw new SQLTransientConnectionException("No se pudo crear el pool de conexiones.", ex);
                }
            }
            return pool;
        }
    }

    private static HikariDataSource crearPool() {
        boolean produccion = ConfigApp.esProduccion();
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("nexora-pool");
        cfg.setDriverClassName(DRIVER);
        cfg.setJdbcUrl(leerConfig("DB_URL", DEFAULT_URL));
        cfg.setUsername(leerConfig("DB_USER", DEFAULT_USER));
        cfg.setPassword(leerConfig("DB_PASSWORD", DEFAULT_PASSWORD));
        cfg.setMinimumIdle(0);
        cfg.setMaximumPoolSize(8);
        cfg.setIdleTimeout(300_000L);
        cfg.setMaxLifetime(1_800_000L);
        cfg.setConnectionTimeout(Math.max(TIMEOUT_MINIMO_MS,
                leerEntero("DB_CONNECTION_TIMEOUT_MS", produccion ? TIMEOUT_PRODUCCION_MS : TIMEOUT_DESARROLLO_MS)));
        // Perezoso: crear el pool no abre ninguna conexión ni falla si la base está caída o pausada
        cfg.setInitializationFailTimeout(-1);
        // keepaliveTime queda en 0 (desactivado): ninguna consulta periódica mantiene despierta la base
        return new HikariDataSource(cfg);
    }

    /**
     * Cierra el pool al detener la aplicación y lo deja cerrado: ninguna petición tardía lo recrea ni deja hilos de
     * Hikari vivos (obtenerConexion falla enseguida con SQLNonTransientConnectionException).
     */
    public static void cerrar() {
        synchronized (CANDADO_POOL) {
            cerrado = true;
            if (pool != null) {
                pool.close();
                pool = null;
            }
        }
    }

    /** Solo para pruebas: true si existe un pool creado y abierto. */
    static boolean hayPool() {
        HikariDataSource actual = pool;
        return actual != null && !actual.isClosed();
    }

    /** Solo para pruebas: cierra el pool y olvida el estado de disponibilidad, el cortocircuito y los avisos. */
    static void reiniciarParaPruebas() {
        cerrar();
        cerrado = false;
        cortocircuito = new Cortocircuito(PLAZO_CORTOCIRCUITO_MS, System::nanoTime);
        bdDisponible = null;
        detalleEstado = "Pendiente de verificación";
        ultimaVerificacion = 0;
        AVISO_CONFIGURACION.set(false);
    }

    // ---------------------------------------------------------------- configuración y disponibilidad

    /**
     * true si hay con qué conectar: en desarrollo siempre (valores por defecto); en producción solo si
     * DB_URL, DB_USER y DB_PASSWORD están definidas. Si faltan, registra un error (una sola vez) con los nombres,
     * nunca con valores.
     */
    public static boolean estaConfigurada() {
        if (!ConfigApp.esProduccion()) {
            return true;
        }
        List<String> faltan = variablesFaltantes();
        if (faltan.isEmpty()) {
            return true;
        }
        if (AVISO_CONFIGURACION.compareAndSet(false, true)) {
            LOG.severe("Producción sin base de datos configurada: faltan las variables " + String.join(", ", faltan)
                    + ". Todo acceso a datos responderá como no disponible (503).");
        }
        return false;
    }

    /** Nombres (nunca valores) de las variables obligatorias de producción que no están definidas. */
    public static List<String> variablesFaltantes() {
        List<String> faltan = new ArrayList<>();
        for (String variable : VARIABLES_OBLIGATORIAS) {
            if (ConfigApp.leer(variable, "").isEmpty()) {
                faltan.add(variable);
            }
        }
        return faltan;
    }

    /**
     * Verifica si la base de datos está activa y accesible.
     * <ul>
     *   <li>Producción: es true cuando hay configuración; no se sondea (la consulta real fallará con
     *       {@link DatosNoDisponiblesException} si la base no responde).</li>
     *   <li>Desarrollo: el resultado se guarda en caché; si estaba desconectada, reintenta tras 5 segundos.</li>
     * </ul>
     */
    public static boolean estaDisponible() {
        if (ConfigApp.esProduccion()) {
            return estaConfigurada();
        }
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
     * Decide dónde operan los servicios de datos.
     *
     * @return true para usar SQL; false para usar el respaldo en memoria (solo desarrollo, con la base caída)
     * @throws DatosNoDisponiblesException en producción si la base no está configurada: allí no hay respaldo
     */
    public static boolean usarSql() {
        if (estaDisponible()) {
            return true;
        }
        if (ConfigApp.esProduccion()) {
            throw new DatosNoDisponiblesException();
        }
        return false;
    }

    /**
     * Una consulta o un comando falló. En desarrollo vuelve a probar la conexión (si cayó, se activa el respaldo en
     * memoria). En producción lo registra y, si el fallo es de conectividad o de inicio de sesión
     * ({@link #esFalloDeConexion}), lanza {@link DatosNoDisponiblesException}; otros errores SQL (restricciones,
     * datos) los resuelve quien llama.
     */
    public static void alFallar(SQLException ex) {
        if (!ConfigApp.esProduccion()) {
            probarConexion();
            return;
        }
        registrarFallo(ex);
        if (esFalloDeConexion(ex)) {
            abrirCortocircuito(cortocircuito);
            throw new DatosNoDisponiblesException(ex);
        }
    }

    /**
     * Igual que {@link #alFallar} para los puntos donde quien llama usaría el respaldo en memoria: en producción
     * lanza siempre {@link DatosNoDisponiblesException}, sea cual sea el error, porque no hay respaldo posible.
     */
    public static void alFallarSinRespaldo(SQLException ex) {
        if (!ConfigApp.esProduccion()) {
            probarConexion();
            return;
        }
        registrarFallo(ex);
        if (esFalloDeConexion(ex)) {
            abrirCortocircuito(cortocircuito);
        }
        throw new DatosNoDisponiblesException(ex);
    }

    /** true si la excepción (o su causa) indica que no hay conexión: red, tiempo de espera, sesión o base no disponible. */
    public static boolean esFalloDeConexion(Throwable ex) {
        int profundidad = 0;
        for (Throwable t = ex; t != null && profundidad < 10; t = t.getCause(), profundidad++) {
            if (t instanceof SQLTransientConnectionException || t instanceof SQLNonTransientConnectionException) {
                return true;
            }
            if (t instanceof SQLException sql) {
                String estado = sql.getSQLState();
                if ((estado != null && estado.startsWith("08")) || ERRORES_NO_DISPONIBLE.contains(sql.getErrorCode())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Anota un fallo de datos en el registro sin detalles sensibles (solo tipo, estado SQL, código y mensaje). */
    private static void registrarFallo(SQLException ex) {
        String mensaje = ex.getMessage() == null ? "" : ex.getMessage();
        int salto = mensaje.indexOf('\n');
        if (salto > 0) {
            mensaje = mensaje.substring(0, salto);
        }
        LOG.log(Level.WARNING, "Fallo de acceso a datos: {0} (SQLState {1}, código {2}): {3}",
                new Object[]{ex.getClass().getSimpleName(), ex.getSQLState(), ex.getErrorCode(), mensaje});
    }

    // ---------------------------------------------------------------- sondeo

    /**
     * Fuerza una prueba real ({@code SELECT 1}) contra SQL Server / Azure SQL. En desarrollo actualiza el estado
     * que decide el respaldo en memoria; en producción solo informa (no hay respaldo que activar).
     */
    public static boolean probarConexion() {
        return ConfigApp.esProduccion() ? probarEnProduccion() : probarEnDesarrollo();
    }

    private static boolean probarEnProduccion() {
        if (!estaConfigurada()) {
            return false;
        }
        try (Connection cn = obtenerConexion()) {
            return seleccionarUno(cn);
        } catch (SQLException ex) {
            registrarFallo(ex);
            return false;
        } catch (DatosNoDisponiblesException ex) {
            return false;   // cortocircuito abierto: la base no responde y no se vuelve a tocar todavía
        }
    }

    /** Sondeo de desarrollo: conexión directa, que falla pronto (loginTimeout) si el motor local no está activo. */
    private static synchronized boolean probarEnDesarrollo() {
        ultimaVerificacion = System.currentTimeMillis();
        try (Connection cn = DriverManager.getConnection(leerConfig("DB_URL", DEFAULT_URL),
                leerConfig("DB_USER", DEFAULT_USER), leerConfig("DB_PASSWORD", DEFAULT_PASSWORD))) {
            boolean ok = seleccionarUno(cn);
            bdDisponible = ok;
            detalleEstado = ok ? "Conectado a MS SQL Server (BD_NEXORA)"
                    : "Respaldo en memoria activo: la base no respondió a SELECT 1";
            return ok;
        } catch (SQLException ex) {
            bdDisponible = Boolean.FALSE;
            detalleEstado = "Respaldo en memoria activo: " + ex.getMessage();
            return false;
        }
    }

    private static boolean seleccionarUno(Connection cn) throws SQLException {
        try (Statement st = cn.createStatement(); ResultSet rs = st.executeQuery("SELECT 1")) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }

    /**
     * Devuelve una descripción legible del estado actual de la persistencia (sin secretos).
     * En producción no sondea la base: solo indica si está configurada.
     */
    public static String getEstadoTexto() {
        if (ConfigApp.esProduccion()) {
            return estaConfigurada() ? "Base de datos SQL configurada" : "Base de datos SQL sin configurar";
        }
        estaDisponible();
        return detalleEstado;
    }

    /** Resumen de la configuración para el registro de arranque (sin valores secretos). */
    public static String resumenConfiguracion() {
        if (ConfigApp.esProduccion()) {
            return estaConfigurada() ? "configurada" : "SIN CONFIGURAR (faltan " + String.join(", ", variablesFaltantes()) + ")";
        }
        return ConfigApp.leer("DB_URL", "").isEmpty() ? "valores por defecto de desarrollo" : "configurada por variables";
    }

    /**
     * Devuelve la cadena JDBC configurada (puede contener el servidor; nunca la contraseña).
     */
    public static String getUrlConfigurada() {
        return leerConfig("DB_URL", DEFAULT_URL);
    }

    /**
     * Valor configurado. En producción no hay valores por defecto (devuelve cadena vacía si falta); en desarrollo
     * se usa el valor por defecto de SQL Server local.
     */
    private static String leerConfig(String clave, String valorPorDefecto) {
        return ConfigApp.leer(clave, ConfigApp.esProduccion() ? "" : valorPorDefecto);
    }

    private static long leerEntero(String clave, long porDefecto) {
        try {
            return Long.parseLong(ConfigApp.leer(clave, String.valueOf(porDefecto)));
        } catch (NumberFormatException ex) {
            return porDefecto;
        }
    }
}
