package pe.edu.sistema.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.edu.sistema.util.DatosNoDisponiblesException;

/**
 * El conteo de productos de la cabecera es de un solo vuelo: al vencer la caché una única petición consulta
 * (aunque la base tarde o esté pausada) y las demás reciben el último valor al instante.
 */
class ConteoCabeceraTest {

    private final ProductoService productos = ProductoService.getInstancia();
    private IntSupplier original;

    @BeforeEach
    void iniciar() {
        original = productos.consultaConteo;
        productos.reiniciarConteoParaPruebas();
    }

    @AfterEach
    void restaurar() {
        productos.consultaConteo = original;
        productos.reiniciarConteoParaPruebas();
    }

    @Test
    @DisplayName("Mientras una petición consulta, las demás devuelven el último valor sin consultar ni esperar")
    void soloUnaPeticionConsulta() throws Exception {
        AtomicInteger consultas = new AtomicInteger();
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        productos.consultaConteo = () -> {
            consultas.incrementAndGet();
            dentro.countDown();
            try {
                liberar.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return 42;
        };

        ExecutorService ejecutor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> primera = ejecutor.submit(productos::contarParaCabecera);
            assertTrue(dentro.await(5, TimeUnit.SECONDS), "la primera petición debe estar consultando");

            for (int i = 0; i < 5; i++) {
                assertEquals(0, productos.contarParaCabecera(), "las demás usan el último valor (0 al inicio) al instante");
            }
            assertEquals(1, consultas.get());

            liberar.countDown();
            assertEquals(42, primera.get(5, TimeUnit.SECONDS));
        } finally {
            liberar.countDown();
            ejecutor.shutdownNow();
        }
        assertEquals(42, productos.contarParaCabecera(), "el valor queda guardado");
        assertEquals(1, consultas.get(), "dentro del plazo de la caché no se vuelve a consultar");
    }

    @Test
    @DisplayName("Con 16 peticiones simultáneas y una consulta lenta, la base se consulta una sola vez")
    void variasPeticionesSimultaneasConsultanUnaVez() throws Exception {
        AtomicInteger consultas = new AtomicInteger();
        productos.consultaConteo = () -> {
            consultas.incrementAndGet();
            try {
                Thread.sleep(300);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return 7;
        };

        int hilos = 16;
        ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<Integer>> resultados = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                resultados.add(ejecutor.submit(() -> {
                    salida.await();
                    return productos.contarParaCabecera();
                }));
            }
            salida.countDown();
            for (Future<Integer> r : resultados) {
                int valor = r.get(10, TimeUnit.SECONDS);
                assertTrue(valor == 0 || valor == 7, "valor inesperado: " + valor);
            }
        } finally {
            ejecutor.shutdownNow();
        }
        assertEquals(1, consultas.get());
        assertEquals(7, productos.contarParaCabecera());
    }

    @Test
    @DisplayName("Si la consulta falla (base pausada) devuelve el último valor y no reintenta de inmediato")
    void consultaFallidaConservaElUltimoValor() {
        AtomicInteger consultas = new AtomicInteger();
        productos.consultaConteo = () -> {
            consultas.incrementAndGet();
            throw new DatosNoDisponiblesException();
        };

        assertEquals(0, productos.contarParaCabecera());
        assertEquals(0, productos.contarParaCabecera());
        assertEquals(0, productos.contarParaCabecera());
        assertEquals(1, consultas.get(), "tras el fallo espera antes de volver a consultar");
        // El indicador de vuelo se libera aunque la consulta falle: vencido el plazo, otra petición puede consultar
        productos.consultaConteo = () -> 9;
        productos.vencerConteoParaPruebas();
        assertEquals(9, productos.contarParaCabecera());
    }
}
