package pe.edu.sistema.controller;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import pe.edu.sistema.service.ProductoService;
import pe.edu.sistema.service.UsuarioService;
import pe.edu.sistema.util.Formato;

/**
 * Filtro: codificación UTF-8, rechazo de URI con traversal, control de sesión sobre la ruta normalizada,
 * protección CSRF de todos los POST y datos comunes de la cabecera (fecha actual y cantidad de productos).
 */
@WebFilter(filterName = "SesionFilter", urlPatterns = {"/*"})
public class SesionFilter implements Filter {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Pattern JSESSIONID_FINAL = Pattern.compile(";jsessionid=[A-Za-z0-9._-]+$", Pattern.CASE_INSENSITIVE);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        req.setCharacterEncoding("UTF-8");

        // URI con ";", "/..", barra invertida o puntos codificados: intento de traversal; se rechaza sin más
        if (uriSospechosa(req.getRequestURI())) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // Ruta normalizada por el contenedor (sin parámetros de ruta ni segmentos ".."),
        // la misma con la que se despacha la petición al servlet o al recurso.
        String ruta = req.getServletPath() + (req.getPathInfo() != null ? req.getPathInfo() : "");
        String accion;
        try {
            accion = req.getParameter("accion");
        } catch (IllegalStateException ex) {
            // Parámetros que no son UTF-8 válido (Tomcat lanza InvalidParameterException): petición mal formada
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        boolean esEstatica = ruta.startsWith("/css/") || ruta.startsWith("/js/")
                || ruta.startsWith("/img/") || ruta.equals("/favicon.ico");
        boolean esRutaPublica = esEstatica
                || ruta.equals("/login") || ruta.equals("/logout")
                || ruta.isEmpty() || ruta.equals("/") || ruta.equals("/index.jsp")
                || ruta.equals("/inicio") || ruta.equals("/informacion");

        if (ruta.equals("/productos")) {
            // El catálogo y el buscador son 100% públicos para que cualquier cliente vea los productos y precios
            if (accion == null || accion.isBlank() || accion.equals("listar") || accion.equals("buscar")) {
                esRutaPublica = true;
            }
        }

        HttpSession sesion = req.getSession(false);
        boolean conectado = sesion != null && sesion.getAttribute("usuario") != null;

        // Cuenta desactivada, con rol o clave cambiados por un administrador después de abrir esta sesión:
        // la sesión deja de valer (se compara su marca de nacimiento con la secuencia de revocaciones)
        if (conectado && !esEstatica && UsuarioService.sesionRevocada(String.valueOf(sesion.getAttribute("usuario")),
                sesion.getAttribute("marcaSesion") instanceof Long marca ? marca : null)) {
            sesion.invalidate();
            Controlador.mensaje(req, "error", "Tu cuenta fue modificada por un administrador. Inicia sesión nuevamente.");
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        if (!esRutaPublica && !conectado) {
            if ("GET".equals(req.getMethod())) {
                // Tras iniciar sesión, LoginServlet devuelve al usuario a la página que pidió
                String consulta = req.getQueryString();
                req.getSession(true).setAttribute("urlRetorno",
                        req.getRequestURI() + (consulta != null && !consulta.isEmpty() ? "?" + consulta : ""));
                boolean esCompra = ruta.equals("/pago") || "comprar".equals(accion);
                Controlador.mensaje(req, "error", esCompra
                        ? "Para comprar en NEXORA, inicia sesión o crea tu cuenta de cliente."
                        : "Inicia sesión para continuar.");
            }
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        boolean esPost = "POST".equals(req.getMethod());
        if (esEstatica && !esPost) {
            chain.doFilter(request, response);
            return;
        }

        // CSRF: cada sesión tiene un token que todos los formularios POST deben devolver en "_csrf"
        // (también los dirigidos a rutas estáticas: ningún POST se salta la validación)
        HttpSession actual = req.getSession(true);
        String token = (String) actual.getAttribute("csrfToken");
        if (token == null) {
            token = nuevoToken();
            actual.setAttribute("csrfToken", token);
        }
        if (esPost && !tokenValido(token, req.getParameter("_csrf"))) {
            Controlador.mensaje(req, "error",
                    "Tu sesión expiró o el formulario ya no es válido. Vuelve a intentarlo.");
            resp.sendRedirect(req.getContextPath() + (conectado ? "/inicio" : "/login"));
            return;
        }
        if (esEstatica) {
            chain.doFilter(request, response);
            return;
        }

        req.setAttribute("fechaActual", Formato.fechaLarga(LocalDate.now()));
        req.setAttribute("totalProductos", ProductoService.getInstancia().contar());
        chain.doFilter(request, response);
    }

    /**
     * Detecta URI con parámetros de ruta (";"), retrocesos ("/.."), barras invertidas o puntos
     * codificados (%2e / %5c), con los que se intenta saltar el control de acceso por prefijo de ruta.
     * Solo se tolera el ";jsessionid=..." final que el contenedor añade al reescribir enlaces.
     */
    private static boolean uriSospechosa(String uri) {
        String u = uri == null ? "" : JSESSIONID_FINAL.matcher(uri).replaceFirst("");
        String minusculas = u.toLowerCase(Locale.ROOT);
        return u.indexOf(';') >= 0 || u.contains("/..") || u.indexOf('\\') >= 0
                || minusculas.contains("%2e") || minusculas.contains("%5c");
    }

    /** 32 caracteres hexadecimales (128 bits) generados con SecureRandom; también lo usa el checkout. */
    static String nuevoToken() {
        byte[] bytes = new byte[16];
        ALEATORIO.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Comparación en tiempo constante del token de la sesión con el recibido en el formulario. */
    private static boolean tokenValido(String esperado, String recibido) {
        if (esperado == null || recibido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), recibido.getBytes(StandardCharsets.UTF_8));
    }
}
