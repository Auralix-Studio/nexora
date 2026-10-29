package pe.edu.sistema.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.service.ProductoService;
import pe.edu.sistema.service.UsuarioService;
import pe.edu.sistema.util.ConexionBD;

/**
 * Controlador de los módulos Inicio e Información del sistema NEXORA.
 */
@WebServlet(name = "SistemaServlet", urlPatterns = {"/inicio", "/informacion"})
public class SistemaServlet extends HttpServlet {

    private final ProductoService service = ProductoService.getInstancia();
    private final UsuarioService usuarioService = new UsuarioService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession sesion = req.getSession(false);
        Usuario u = (sesion != null && sesion.getAttribute("usuarioObj") instanceof Usuario usr)
                ? usr
                : null;
        req.setAttribute("bdEstado", ConexionBD.getEstadoTexto());
        if ("/informacion".equals(req.getServletPath())) {
            if (u != null && u.isAdministrador()) req.setAttribute("usuariosSistema", usuarioService.listarUsuarios());
            Controlador.vista(req, resp, "informacion");
            return;
        }
        if (u != null && u.isPersonal()) {
            // El personal (Administrador, Almacén, Consulta) trabaja desde el panel de administración
            resp.sendRedirect(req.getContextPath() + "/admin");
            return;
        }
        // Escaparate de la tienda: visitantes y clientes
        req.setAttribute("productosTienda", service.listar());
        req.setAttribute("categoriasTienda", service.nombresCategorias());
        Controlador.vista(req, resp, "inicio");
    }
}
