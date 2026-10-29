package pe.edu.sistema.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import pe.edu.sistema.util.ConexionBD;
import pe.edu.sistema.util.ConfigApp;

/**
 * Comprobación de salud para las sondas del contenedor (Azure Container Apps).
 *
 * GET /salud        200 {"estado":"ok","version":"...","entorno":"..."}; no toca la base de datos ni crea sesión
 * GET /salud?bd=1   ejecuta SELECT 1: 200 {"estado":"ok","bd":"ok"} o 503 {"estado":"error","bd":"no disponible"}
 *
 * Es una ruta pública: {@link SesionFilter} la deja pasar sin sesión ni token CSRF.
 */
@WebServlet(name = "SaludServlet", urlPatterns = {"/salud"})
public class SaludServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setHeader("Cache-Control", "no-store");

        if ("1".equals(req.getParameter("bd"))) {
            boolean activa = ConexionBD.probarConexion();
            resp.setStatus(activa ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            resp.getWriter().write(activa
                    ? "{\"estado\":\"ok\",\"bd\":\"ok\"}"
                    : "{\"estado\":\"error\",\"bd\":\"no disponible\"}");
            return;
        }
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(cuerpoBasico());
    }

    /** JSON de estado básico: versión y entorno (sin tocar la base de datos). */
    static String cuerpoBasico() {
        return "{\"estado\":\"ok\",\"version\":\"" + escaparJson(ConfigApp.version())
                + "\",\"entorno\":\"" + ConfigApp.entorno() + "\"}";
    }

    /** Escapa comillas, barras invertidas y caracteres de control para incrustar texto en una cadena JSON. */
    static String escaparJson(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
