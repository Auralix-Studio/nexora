package pe.edu.sistema.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Resiliencia de {@link ConexionBD} con la base pausada o caída: cortocircuito de 15 segundos con una sola petición
 * de prueba al vencer, y pool que no se recrea después de {@link ConexionBD#cerrar()}. Usa un reloj simulado.
 */
class CortocircuitoConexionTest {

    private static final long PLAZO_MS = 15_000L;
    private static final long SEGUNDO_NS = TimeUnit.SECONDS.toNanos(1);

    private Map<String, String> guardadas;
    private final AtomicLong reloj = new AtomicLong();

    @BeforeEach
    void iniciar() {
        guardadas = PropiedadesSistema.guardar();
        PropiedadesSistema.limpiarConfiguracion();
        ConexionBD.reiniciarParaPruebas();
        ConexionBD.cortocircuito = new ConexionBD.Cortocircuito(PLAZO_MS, reloj::get);
    }

    @AfterEach
    void restaurar() {
        ConexionBD.reiniciarParaPruebas();
        PropiedadesSistema.restaurar(guardadas);
        ConexionBD.reiniciarParaPruebas();
    }

    /** Producción con la base configurada en un puerto cerrado; espera mínima por conexión. */
    private static void produccionConBaseCaida() {
        System.setProperty(ConfigApp.ENTORNO, "produccion");
        System.setProperty("DB_USER", "usuario_de_prueba");
        System.setProperty("DB_PASSWORD", "clave-de-prueba-sin-valor");
        System.setProperty("DB_CONNECTION_TIMEOUT_MS", "300");
    }

    // ---------------------------------------------------------------- la máquina de estados

    @Test
    @DisplayName("Cerrado deja pasar todo; tras un fallo rechaza durante el plazo y luego deja pasar solo una prueba")
    void unaSolaPruebaAlVencerElPlazo() {
        ConexionBD.Cortocircuito corte = new ConexionBD.Cortocircuito(PLAZO_MS, reloj::get);
        assertTrue(corte.permitir());
        assertTrue(corte.permitir());

        assertTrue(corte.fallo(), "la primera vez estaba cerrado");
        assertFalse(corte.permitir());
        reloj.set(14 * SEGUNDO_NS);
        assertFalse(corte.permitir(), "faltando un segundo sigue rechazando");

        reloj.set(15 * SEGUNDO_NS);
        assertTrue(corte.permitir(), "vencido el plazo pasa la primera petición");
        assertFalse(corte.permitir(), "las demás esperan el resultado de la prueba");
        reloj.set(60 * SEGUNDO_NS);
        assertFalse(corte.permitir(), "aunque la prueba tarde mucho, nadie más entra");

        assertFalse(corte.fallo(), "ya estaba abierto");
        assertFalse(corte.permitir(), "la prueba falló: otro plazo completo");
        reloj.set(74 * SEGUNDO_NS);
        assertFalse(corte.permitir());
        reloj.set(75 * SEGUNDO_NS);
        assertTrue(corte.permitir(), "segunda prueba");

        corte.exito();
        assertFalse(corte.estaAbierto());
        assertTrue(corte.permitir());
        assertTrue(corte.permitir());
    }

    @Test
    @DisplayName("Con muchas peticiones simultáneas tras el plazo, solo una conecta")
    void soloUnaPeticionDeVariasSimultaneas() throws Exception {
        ConexionBD.Cortocircuito corte = new ConexionBD.Cortocircuito(PLAZO_MS, reloj::get);
        corte.fallo();
        reloj.set(16 * SEGUNDO_NS);

        int hilos = 16;
        ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                resultados.add(ejecutor.submit(() -> {
                    salida.await();
                    return corte.permitir();
                }));
            }
            salida.countDown();
            int permitidas = 0;
            for (Future<Boolean> r : resultados) {
                if (r.get(10, TimeUnit.SECONDS)) {
                    permitidas++;
                }
            }
            assertEquals(1, permitidas);
        } finally {
            ejecutor.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- integrado con ConexionBD

    @Test
    @DisplayName("Producción: tras un fallo de conexión, 15 s de rechazo inmediato (DatosNoDisponibles) y luego un reintento")
    void produccionRechazaSinTocarHikari() {
        produccionConBaseCaida();

        assertThrows(SQLTransientConnectionException.class, ConexionBD::obtenerConexion);   // el primer intento llega a Hikari
        assertTrue(ConexionBD.cortocircuito.estaAbierto(), "un fallo de conexión abre el cortocircuito");

        // Rechazo inmediato: DatosNoDisponiblesException no puede venir de Hikari
        assertThrows(DatosNoDisponiblesException.class, ConexionBD::obtenerConexion);
        assertThrows(DatosNoDisponiblesException.class, ConexionBD::obtenerConexion);
        assertFalse(ConexionBD.probarConexion(), "el sondeo informa no disponible sin lanzar");

        reloj.set(14 * SEGUNDO_NS);
        assertThrows(DatosNoDisponiblesException.class, ConexionBD::obtenerConexion);

        reloj.set(16 * SEGUNDO_NS);
        assertThrows(SQLTransientConnectionException.class, ConexionBD::obtenerConexion);   // reintento real
        assertThrows(DatosNoDisponiblesException.class, ConexionBD::obtenerConexion);       // otra vez rechazado
    }

    @Test
    @DisplayName("Producción: un fallo de conexión en una consulta (alFallar) también abre el cortocircuito")
    void alFallarAbreElCortocircuito() {
        produccionConBaseCaida();

        // Un error que no es de conectividad (restricción de datos) no lo abre
        SQLException restriccion = new SQLException("violación de clave", "23000", 2627);
        ConexionBD.alFallar(restriccion);
        assertFalse(ConexionBD.cortocircuito.estaAbierto());

        assertThrows(DatosNoDisponiblesException.class,
                () -> ConexionBD.alFallar(new SQLTransientConnectionException("sin conexión", "08001")));
        assertTrue(ConexionBD.cortocircuito.estaAbierto());
        assertThrows(DatosNoDisponiblesException.class, ConexionBD::obtenerConexion);
    }

    @Test
    @DisplayName("Desarrollo: no hay cortocircuito, cada intento llega al pool")
    void desarrolloNoUsaCortocircuito() {
        assumeTrue(System.getenv(ConfigApp.ENTORNO) == null, "el entorno de la prueba define NEXORA_ENTORNO");
        System.setProperty("DB_CONNECTION_TIMEOUT_MS", "300");

        for (int i = 0; i < 2; i++) {
            assertThrows(SQLException.class, ConexionBD::obtenerConexion);
        }
        assertFalse(ConexionBD.cortocircuito.estaAbierto());
    }

    // ---------------------------------------------------------------- cierre

    private static boolean hayHilosDeHikari() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.isAlive() && t.getName().startsWith("nexora-pool"));
    }

    private static boolean esperarSinHilosDeHikari() throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (hayHilosDeHikari() && System.nanoTime() < limite) {
            Thread.sleep(50);
        }
        return !hayHilosDeHikari();
    }

    @Test
    @DisplayName("Tras cerrar() el pool no se recrea y no quedan hilos de Hikari")
    void cerrarNoPermiteRecrearElPool() throws Exception {
        produccionConBaseCaida();
        assertThrows(SQLException.class, ConexionBD::obtenerConexion);
        assertTrue(ConexionBD.hayPool(), "el primer uso crea el pool");

        ConexionBD.cerrar();   // como ArranqueListener.contextDestroyed

        assertFalse(ConexionBD.hayPool());
        assertThrows(SQLNonTransientConnectionException.class, ConexionBD::obtenerConexion);
        assertThrows(SQLNonTransientConnectionException.class, ConexionBD::obtenerConexion);
        assertFalse(ConexionBD.hayPool(), "una petición tardía no debe recrear el pool");
        assertTrue(esperarSinHilosDeHikari(), "no deben quedar hilos nexora-pool vivos");
    }

    @Test
    @DisplayName("Cerrar sin haber creado el pool también lo deja cerrado")
    void cerrarSinPoolPrevio() {
        produccionConBaseCaida();
        ConexionBD.cerrar();
        assertThrows(SQLNonTransientConnectionException.class, ConexionBD::obtenerConexion);
        assertFalse(ConexionBD.hayPool());
        assertFalse(ConexionBD.cortocircuito.estaAbierto(), "el cierre no cuenta como fallo de la base");
    }
}
