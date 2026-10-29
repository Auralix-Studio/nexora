package pe.edu.sistema.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.edu.sistema.controller.ServletFalsos.Peticion;
import pe.edu.sistema.controller.ServletFalsos.Respuesta;
import pe.edu.sistema.util.DatosNoDisponiblesException;

/** Pruebas de {@link CabecerasSeguridadFilter}: cabeceras del contrato y conversión de errores de datos en 503. */
class CabecerasSeguridadFilterTest {

    private final CabecerasSeguridadFilter filtro = new CabecerasSeguridadFilter();
    private static final FilterChain CADENA_VACIA = (rq, rs) -> { };

    @Test
    @DisplayName("Pone todas las cabeceras de seguridad del contrato")
    void cabecerasDelContrato() throws Exception {
        Respuesta resp = new Respuesta();
        filtro.doFilter(Peticion.get("/inicio").proxy, resp.proxy, CADENA_VACIA);

        assertEquals("default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com; "
                + "font-src https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; form-action 'self'; "
                + "frame-ancestors 'none'; base-uri 'self'; object-src 'none'", resp.cabecera("Content-Security-Policy"));
        assertEquals("nosniff", resp.cabecera("X-Content-Type-Options"));
        assertEquals("strict-origin-when-cross-origin", resp.cabecera("Referrer-Policy"));
        assertEquals("camera=(), microphone=(), geolocation=(), payment=()", resp.cabecera("Permissions-Policy"));
        assertEquals("DENY", resp.cabecera("X-Frame-Options"));
        assertEquals("same-origin", resp.cabecera("Cross-Origin-Opener-Policy"));
        assertFalse(resp.cabecera("Content-Security-Policy").contains("unsafe-inline"));
    }

    @Test
    @DisplayName("HSTS solo en peticiones seguras")
    void hstsSoloConHttps() throws Exception {
        Respuesta http = new Respuesta();
        filtro.doFilter(Peticion.get("/inicio").proxy, http.proxy, CADENA_VACIA);
        assertNull(http.cabecera("Strict-Transport-Security"));

        Respuesta https = new Respuesta();
        filtro.doFilter(new Peticion("GET", "/inicio", null, true).proxy, https.proxy, CADENA_VACIA);
        assertEquals("max-age=31536000; includeSubDomains", https.cabecera("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("CSS y JS (versionados con ?v=) en caché una semana, imágenes un día; las páginas no")
    void cacheDeEstaticos() throws Exception {
        for (String ruta : new String[]{"/css/app.css", "/js/app.js"}) {
            Respuesta resp = new Respuesta();
            filtro.doFilter(Peticion.get(ruta).proxy, resp.proxy, CADENA_VACIA);
            assertEquals("public, max-age=604800", resp.cabecera("Cache-Control"), ruta);
        }
        Respuesta imagen = new Respuesta();
        filtro.doFilter(Peticion.get("/img/logo.png").proxy, imagen.proxy, CADENA_VACIA);
        assertEquals("public, max-age=86400", imagen.cabecera("Cache-Control"));
        for (String ruta : new String[]{"/inicio", "/productos", "/csstest/x", "/salud"}) {
            Respuesta resp = new Respuesta();
            filtro.doFilter(Peticion.get(ruta).proxy, resp.proxy, CADENA_VACIA);
            assertNull(resp.cabecera("Cache-Control"), ruta);
        }
    }

    @Test
    @DisplayName("Una excepción de datos no capturada se convierte en 503")
    void datosNoDisponiblesEs503() throws Exception {
        Respuesta resp = new Respuesta();
        FilterChain falla = (rq, rs) -> {
            throw new DatosNoDisponiblesException();
        };
        filtro.doFilter(Peticion.get("/productos").proxy, resp.proxy, falla);

        assertEquals(503, resp.errorEnviado);
        assertEquals("30", resp.cabecera("Retry-After"));
        assertEquals("no-store", resp.cabecera("Cache-Control"));
    }

    @Test
    @DisplayName("Una IllegalStateException causada por SQL también es 503, incluso dentro de una ServletException")
    void errorSqlEnvueltoEs503() throws Exception {
        Respuesta uno = new Respuesta();
        FilterChain causaSql = (rq, rs) -> {
            throw new IllegalStateException("No se pudo consultar el historial de pedidos.", new SQLException("x"));
        };
        filtro.doFilter(Peticion.get("/productos").proxy, uno.proxy, causaSql);
        assertEquals(503, uno.errorEnviado);

        Respuesta dos = new Respuesta();
        FilterChain envuelta = (rq, rs) -> {
            throw new ServletException("fallo en la vista", new DatosNoDisponiblesException());
        };
        filtro.doFilter(Peticion.get("/productos").proxy, dos.proxy, envuelta);
        assertEquals(503, dos.errorEnviado);
    }

    @Test
    @DisplayName("Otras excepciones no se convierten: se propagan al contenedor")
    void otrasExcepcionesSePropagan() {
        IllegalStateException ajena = new IllegalStateException("Cannot create a session after the response has been committed");
        FilterChain falla = (rq, rs) -> {
            throw ajena;
        };
        Respuesta resp = new Respuesta();
        IllegalStateException lanzada = assertThrows(IllegalStateException.class,
                () -> filtro.doFilter(Peticion.get("/productos").proxy, resp.proxy, falla));
        assertSame(ajena, lanzada);
        assertEquals(-1, resp.errorEnviado);

        ServletException servlet = new ServletException("otra falla");
        FilterChain fallaServlet = (rq, rs) -> {
            throw servlet;
        };
        assertSame(servlet, assertThrows(ServletException.class,
                () -> filtro.doFilter(Peticion.get("/productos").proxy, new Respuesta().proxy, fallaServlet)));
    }

    @Test
    @DisplayName("Si la respuesta ya se envió no se intenta cambiar el estado")
    void respuestaComprometidaNoSeToca() throws Exception {
        Respuesta resp = new Respuesta();
        resp.comprometida = true;
        FilterChain falla = (rq, rs) -> {
            throw new DatosNoDisponiblesException();
        };
        filtro.doFilter(Peticion.get("/productos").proxy, resp.proxy, falla);

        assertEquals(-1, resp.errorEnviado);
        assertTrue(resp.cabeceras.get("Retry-After") == null);
    }
}
