package pe.edu.sistema.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import pe.edu.sistema.util.ConexionBD;
import pe.edu.sistema.util.ConfigApp;

/**
 * Comprobación de salud para las sondas del contenedor (Azure Container Apps).
 *
 * GET /salud        200 {"estado":"ok","version":"...","entorno":"..."}; no toca la base de datos ni crea sesión
 * GET /salud?bd=1   ejecuta SELECT 1: 200 {"estado":"ok","bd":"ok"} o 503 {"estado":"error","bd":"no disponible"}
 *
 * Es una ruta pública: {@link SesionFilter} la deja pasar sin sesión ni token CSRF. Como la consulta a la base
 * puede reanudar Azure SQL y gastar su límite gratuito, en producción {@code ?bd=1} exige la cabecera
 * {@code X-Salud-Token} igual a la variable {@code NEXORA_TOKEN_SALUD}; si la variable no existe o el token no
 * coincide responde 403 {"estado":"ok","bd":"no autorizado"} sin tocar la base. En desarrollo sigue abierto.
 */
@WebServlet(name = "SaludServlet", urlPatterns = {"/salud"})
public class SaludServlet extends HttpServlet {

    /** Variable de entorno con el token que autoriza {@code /salud?bd=1} en producción. */
    public static final String VARIABLE_TOKEN = "NEXORA_TOKEN_SALUD";
    /** Cabecera HTTP que lleva el token. */
    public static final String CABECERA_TOKEN = "X-Salud-Token";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setHeader("Cache-Control", "no-store");

        if ("1".equals(req.getParameter("bd"))) {
            if (ConfigApp.esProduccion() && !tokenValido(req.getHeader(CABECERA_TOKEN))) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.getWriter().write("{\"estado\":\"ok\",\"bd\":\"no autorizado\"}");
                return;
            }
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

    /**
     * true si el token recibido coincide con NEXORA_TOKEN_SALUD. Sin la variable definida nunca es válido.
     * La comparación es en tiempo constante (se comparan los SHA-256 de ambos valores con
     * {@link MessageDigest#isEqual}).
     */
    static boolean tokenValido(String recibido) {
        String esperado = ConfigApp.leer(VARIABLE_TOKEN, "");
        if (esperado.isEmpty() || recibido == null) {
            return false;
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] a = sha.digest(esperado.getBytes(StandardCharsets.UTF_8));
            byte[] b = sha.digest(recibido.trim().getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(a, b);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);   // SHA-256 existe en toda JVM
        }
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
