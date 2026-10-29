package pe.edu.sistema.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import pe.edu.sistema.service.ProductoService;
import pe.edu.sistema.util.Formato;

/**
 * Controlador del módulo de reportes.
 *
 * GET /reportes?accion=disponibles | resumen
 */
@WebServlet(name = "ReporteServlet", urlPatterns = {"/reportes"})
public class ReporteServlet extends HttpServlet {

    private final ProductoService service = ProductoService.getInstancia();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession sesion = req.getSession(false);
        String rol = sesion != null && sesion.getAttribute("rol") != null
                ? String.valueOf(sesion.getAttribute("rol"))
                : "";
        if ("Cliente".equalsIgnoreCase(rol)) {
            Controlador.mensaje(req, "error", "El módulo de reportes internos está reservado para administración y logística.");
            resp.sendRedirect(req.getContextPath() + "/productos?accion=listar");
            return;
        }

        String accion = req.getParameter("accion");
        int total = service.contar();
        int disponibles = service.contarDisponibles();
        int agotados = service.contarAgotados();

        req.setAttribute("total", total);
        req.setAttribute("disponibles", disponibles);
        req.setAttribute("agotados", agotados);
        req.setAttribute("stockTotal", service.stockTotal());
        req.setAttribute("valorTotal", Formato.soles(service.valorTotal()));

        if ("resumen".equals(accion)) {
            int pctDisponibles = total == 0 ? 0 : Math.round(disponibles * 100f / total);
            req.setAttribute("pctDisponibles", pctDisponibles);
            req.setAttribute("pctAgotados", total == 0 ? 0 : 100 - pctDisponibles);
            req.setAttribute("porCategoria", service.valorPorCategoria());
            req.setAttribute("generado",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")));
            Controlador.vista(req, resp, "reportes/resumen");
        } else {
            req.setAttribute("productos", service.listarDisponibles());
            Controlador.vista(req, resp, "reportes/disponibles");
        }
    }
}
