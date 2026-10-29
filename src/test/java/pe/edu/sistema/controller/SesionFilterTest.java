package pe.edu.sistema.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.edu.sistema.controller.ServletFalsos.Peticion;
import pe.edu.sistema.controller.ServletFalsos.Respuesta;
import pe.edu.sistema.util.ConfigApp;
import pe.edu.sistema.util.PropiedadesSistema;

/** Pruebas de {@link SesionFilter}: /salud pública sin sesión y atributos de entorno para las vistas. */
class SesionFilterTest {

    private final SesionFilter filtro = new SesionFilter();
    private Map<String, String> guardadas;

    @BeforeEach
    void iniciar() {
        guardadas = PropiedadesSistema.guardar();
        PropiedadesSistema.limpiarConfiguracion();
    }

    @AfterEach
    void restaurar() {
        PropiedadesSistema.restaurar(guardadas);
    }

    @Test
    @DisplayName("/salud pasa sin sesión, sin token CSRF y sin atributos de cabecera (ni consulta de productos)")
    void saludEsPublicaYNoCreaSesion() throws Exception {
        Peticion req = Peticion.get("/salud");
        Respuesta resp = new Respuesta();
        AtomicInteger pasos = new AtomicInteger();
        FilterChain cadena = (rq, rs) -> pasos.incrementAndGet();

        filtro.doFilter(req.proxy, resp.proxy, cadena);

        assertEquals(1, pasos.get(), "la petición debe llegar al servlet");
        assertTrue(req.llamadas.isEmpty(), "no debe tocar la sesión: " + req.llamadas);
        assertNull(resp.redireccion, "no debe redirigir al login");
        assertFalse(req.atributos.containsKey("totalProductos"));
        assertFalse(req.atributos.containsKey("fechaActual"));
    }

    @Test
    @DisplayName("/salud responde aunque sea un POST de otro origen: no exige token CSRF")
    void saludNoExigeCsrf() throws Exception {
        Peticion req = new Peticion("POST", "/salud", null, false);
        Respuesta resp = new Respuesta();
        AtomicInteger pasos = new AtomicInteger();

        filtro.doFilter(req.proxy, resp.proxy, (rq, rs) -> pasos.incrementAndGet());

        assertEquals(1, pasos.get());
        assertNull(resp.redireccion);
    }

    @Test
    @DisplayName("Las vistas reciben appVersion, modoDemo y entornoProduccion")
    void atributosDeEntorno() throws Exception {
        System.setProperty(ConfigApp.VERSION, "abc1234");
        System.setProperty(ConfigApp.DEMO, "true");
        System.setProperty(ConfigApp.ENTORNO, "produccion");
        Peticion req = Peticion.get("/css/nexora.css");
        AtomicInteger pasos = new AtomicInteger();

        filtro.doFilter(req.proxy, new Respuesta().proxy, (rq, rs) -> pasos.incrementAndGet());

        assertEquals(1, pasos.get());
        assertEquals("abc1234", req.atributos.get("appVersion"));
        assertEquals(Boolean.TRUE, req.atributos.get("modoDemo"));
        assertEquals(Boolean.TRUE, req.atributos.get("entornoProduccion"));
        assertFalse(req.creoSesion(), "un recurso estático no crea sesión");
    }

    @Test
    @DisplayName("Fuera de demostración y producción los atributos valen false")
    void atributosPorDefecto() throws Exception {
        System.setProperty(ConfigApp.DEMO, "false");
        System.setProperty(ConfigApp.ENTORNO, "desarrollo");
        Peticion req = Peticion.get("/js/app.js");

        filtro.doFilter(req.proxy, new Respuesta().proxy, (rq, rs) -> { });

        assertEquals(Boolean.FALSE, req.atributos.get("modoDemo"));
        assertEquals(Boolean.FALSE, req.atributos.get("entornoProduccion"));
    }
}
