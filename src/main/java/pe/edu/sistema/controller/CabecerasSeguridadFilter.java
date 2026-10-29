package pe.edu.sistema.controller;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import pe.edu.sistema.util.DatosNoDisponiblesException;

/**
 * Filtro de seguridad HTTP: añade las cabeceras de endurecimiento a todas las respuestas (CSP, nosniff,
 * Referrer-Policy, Permissions-Policy, X-Frame-Options, COOP; HSTS solo en HTTPS) y la caché de los recursos
 * estáticos. Además envuelve la cadena: una {@link IllegalStateException} de datos que nadie capturó (base no
 * disponible) se convierte en una respuesta 503 en lugar de un error 500.
 *
 * <p>Se registra en web.xml antes que {@link SesionFilter}. La CSP no admite estilos ni scripts en línea.</p>
 */
public class CabecerasSeguridadFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(CabecerasSeguridadFilter.class.getName());

    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com; "
            + "font-src https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; form-action 'self'; "
            + "frame-ancestors 'none'; base-uri 'self'; object-src 'none'";
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=()";
    static final String CACHE_ESTATICOS = "public, max-age=604800";
    static final String CACHE_IMAGENES = "public, max-age=86400";
    private static final String HSTS = "max-age=31536000; includeSubDomains";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse resp)) {
            chain.doFilter(request, response);
            return;
        }
        aplicarCabeceras(req, resp);
        try {
            chain.doFilter(request, response);
        } catch (IllegalStateException | ServletException ex) {
            if (!esErrorDeDatos(ex)) {
                throw ex;
            }
            responder503(req, resp, ex);
        }
    }

    /** Pone las cabeceras de seguridad (y la caché de /css, /js e /img) antes de despachar la petición. */
    static void aplicarCabeceras(HttpServletRequest req, HttpServletResponse resp) {
        resp.setHeader("Content-Security-Policy", CSP);
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        resp.setHeader("Permissions-Policy", PERMISSIONS_POLICY);
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        if (req.isSecure()) {
            resp.setHeader("Strict-Transport-Security", HSTS);
        }
        if (esEstatico(req.getServletPath())) {
            // CSS y JS llevan ?v=<versión> y pueden guardarse una semana; las imágenes no se versionan
            resp.setHeader("Cache-Control", req.getServletPath().startsWith("/img/") ? CACHE_IMAGENES : CACHE_ESTATICOS);
        }
    }

    private static boolean esEstatico(String ruta) {
        return ruta != null && (ruta.startsWith("/css/") || ruta.startsWith("/js/") || ruta.startsWith("/img/"));
    }

    /**
     * true si la excepción (o alguna de sus causas) indica que los datos no están disponibles: la excepción
     * propia de producción o una {@link IllegalStateException} causada por un error SQL.
     */
    static boolean esErrorDeDatos(Throwable ex) {
        int profundidad = 0;
        for (Throwable t = ex; t != null && profundidad < 10; t = t.getCause(), profundidad++) {
            if (t instanceof DatosNoDisponiblesException) {
                return true;
            }
            if (t instanceof IllegalStateException && t.getCause() instanceof SQLException) {
                return true;
            }
        }
        return false;
    }

    private static void responder503(HttpServletRequest req, HttpServletResponse resp, Throwable ex)
            throws IOException {
        LOG.log(Level.WARNING, "Datos no disponibles al atender {0} {1}: {2}",
                new Object[]{req.getMethod(), req.getRequestURI(), ex.getMessage()});
        if (resp.isCommitted()) {
            // Ya se envió parte de la respuesta: no se puede cambiar el estado
            return;
        }
        resp.setHeader("Retry-After", "30");
        resp.setHeader("Cache-Control", "no-store");
        resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }
}
