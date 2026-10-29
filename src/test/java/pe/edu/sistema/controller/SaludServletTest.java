package pe.edu.sistema.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.edu.sistema.controller.ServletFalsos.Peticion;
import pe.edu.sistema.controller.ServletFalsos.Respuesta;
import pe.edu.sistema.util.ConfigApp;
import pe.edu.sistema.util.PropiedadesSistema;

/** Pruebas de {@link SaludServlet} sin contenedor: JSON, sin sesión y sin tocar la base de datos. */
class SaludServletTest {

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
    @DisplayName("GET /salud responde 200 con estado, versión y entorno, sin crear sesión")
    void saludBasico() throws Exception {
        System.setProperty(ConfigApp.VERSION, "a1b2c3d");
        System.setProperty(ConfigApp.ENTORNO, "produccion");   // sin DB_USER ni DB_PASSWORD: no debe tocar la base
        Peticion req = Peticion.get("/salud");
        Respuesta resp = new Respuesta();

        new SaludServlet().doGet(req.proxy, resp.proxy);

        assertEquals(200, resp.estado);
        assertEquals("application/json", resp.tipoContenido);
        assertEquals("{\"estado\":\"ok\",\"version\":\"a1b2c3d\",\"entorno\":\"produccion\"}", resp.texto());
        assertEquals("no-store", resp.cabecera("Cache-Control"));
        assertTrue(req.llamadas.isEmpty(), "no debe pedir ni crear la sesión: " + req.llamadas);
    }

    @Test
    @DisplayName("En desarrollo /salud informa entorno desarrollo y la versión por defecto")
    void saludEnDesarrollo() throws Exception {
        assumeTrue(System.getenv(ConfigApp.ENTORNO) == null && System.getenv(ConfigApp.VERSION) == null,
                "el entorno de la prueba define NEXORA_ENTORNO o NEXORA_VERSION");
        Respuesta resp = new Respuesta();

        new SaludServlet().doGet(Peticion.get("/salud").proxy, resp.proxy);

        assertEquals(200, resp.estado);
        assertEquals("{\"estado\":\"ok\",\"version\":\"dev\",\"entorno\":\"desarrollo\"}", resp.texto());
    }

    @Test
    @DisplayName("GET /salud?bd=1 responde 503 si la base no responde (producción sin configurar)")
    void saludConBaseSinConfigurar() throws Exception {
        assumeTrue(System.getenv("DB_USER") == null && System.getenv("DB_PASSWORD") == null,
                "el entorno de la prueba define DB_USER o DB_PASSWORD");
        System.setProperty(ConfigApp.ENTORNO, "produccion");
        Peticion req = Peticion.get("/salud");
        req.parametros.put("bd", "1");
        Respuesta resp = new Respuesta();

        new SaludServlet().doGet(req.proxy, resp.proxy);

        assertEquals(503, resp.estado);
        assertEquals("{\"estado\":\"error\",\"bd\":\"no disponible\"}", resp.texto());
        assertTrue(req.llamadas.isEmpty());
    }

    @Test
    @DisplayName("GET /salud?bd=1 responde 503 si el servidor no está activo (desarrollo, puerto cerrado)")
    void saludConBaseCaida() throws Exception {
        assumeTrue(System.getenv(ConfigApp.ENTORNO) == null, "el entorno de la prueba define NEXORA_ENTORNO");
        Peticion req = Peticion.get("/salud");
        req.parametros.put("bd", "1");
        Respuesta resp = new Respuesta();

        new SaludServlet().doGet(req.proxy, resp.proxy);

        assertEquals(503, resp.estado);
        assertTrue(resp.texto().contains("\"bd\":\"no disponible\""));
    }

    @Test
    @DisplayName("Un valor de bd distinto de 1 no consulta la base")
    void otroValorDeBdNoConsulta() throws Exception {
        System.setProperty(ConfigApp.ENTORNO, "produccion");
        Peticion req = Peticion.get("/salud");
        req.parametros.put("bd", "0");
        Respuesta resp = new Respuesta();

        new SaludServlet().doGet(req.proxy, resp.proxy);

        assertEquals(200, resp.estado);
        assertFalse(resp.texto().contains("\"bd\""));
    }

    @Test
    @DisplayName("La versión se escapa para que el JSON siga siendo válido")
    void escapaLaVersion() {
        assertEquals("a\\\"b\\\\c\\u000a", SaludServlet.escaparJson("a\"b\\c\n"));
        assertEquals("1.2.3-abc", SaludServlet.escaparJson("1.2.3-abc"));
    }
}
