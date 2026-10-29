package pe.edu.sistema.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.service.AuditoriaService;

/**
 * Utilidades compartidas por los controladores.
 */
final class Controlador {

    private Controlador() {
    }

    /** Envía la petición a una vista JSP protegida dentro de WEB-INF/vistas. */
    static void vista(HttpServletRequest req, HttpServletResponse resp, String nombre)
            throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/vistas/" + nombre + ".jsp").forward(req, resp);
    }

    /** Mensaje que se muestra una sola vez en la siguiente página (tipo: ok | error). */
    static void mensaje(HttpServletRequest req, String tipo, String texto) {
        req.getSession().setAttribute("flashTipo", tipo);
        req.getSession().setAttribute("flashMensaje", texto);
    }

    /**
     * Registra una acción de un usuario con sesión en la auditoría. Una falla de la bitácora nunca debe
     * deshacer ni romper la operación que ya se hizo, así que se ignora. Solo delega: el recorte de los textos
     * a la longitud de las columnas lo hace {@link AuditoriaService}.
     */
    static void auditar(HttpServletRequest req, Usuario actor, String accion, String entidad, String detalle) {
        try {
            AuditoriaService.getInstancia().registrar(actor, accion, entidad, detalle, req.getRemoteAddr());
        } catch (RuntimeException ignorada) {
            // la auditoría es un registro auxiliar
        }
    }

    /** Variante para accesos sin usuario autenticado (intentos fallidos con el nombre escrito). */
    static void auditar(HttpServletRequest req, String username, String rol, String accion,
            String entidad, String detalle) {
        try {
            AuditoriaService.getInstancia().registrar(username, rol, accion, entidad, detalle, req.getRemoteAddr());
        } catch (RuntimeException ignorada) {
            // la auditoría es un registro auxiliar
        }
    }

    /** Recorta el texto a {@code max} caracteres (null pasa tal cual); para campos de formularios y avisos. */
    static String recortar(String texto, int max) {
        if (texto == null || texto.length() <= max) {
            return texto;
        }
        return texto.substring(0, max);
    }
}
