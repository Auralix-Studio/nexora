package pe.edu.sistema.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pe.edu.sistema.model.Producto;
import pe.edu.sistema.model.RegistroAuditoria;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.service.ProductoService;
import pe.edu.sistema.util.Formato;

/**
 * Controlador del módulo de productos del sistema NEXORA con control de roles.
 *
 * GET  /productos?accion=listar | nuevo | buscar | pedidos | comprar[&codigo=] | editar[&codigo=] | eliminar[&codigo=]
 * POST /productos?accion=registrar | actualizar | eliminar
 * La compra con pago se procesa en {@link PagoServlet} (/pago).
 */
@WebServlet(name = "ProductoServlet", urlPatterns = {"/productos"})
public class ProductoServlet extends HttpServlet {

    private static final String[] CAMPOS = {"codigo", "nombre", "categoria", "precio", "stock", "estado"};

    private final ProductoService service = ProductoService.getInstancia();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String accion = parametro(req, "accion", "listar");
        Usuario u = usuarioSesion(req);

        switch (accion) {
            case "nuevo":
                if (!u.isPuedeRegistrar()) {
                    denegarAcceso(req, resp, "Su rol (" + u.getRol() + ") solo tiene permisos de consulta y no puede registrar productos.");
                    return;
                }
                mostrarFormulario(req, resp, "registrar", formularioVacio(), new HashMap<>());
                break;
            case "editar":
                if (!u.isPuedeEditar()) {
                    denegarAcceso(req, resp, "Su rol (" + u.getRol() + ") no tiene permisos para editar productos.");
                    return;
                }
                editar(req, resp);
                break;
            case "eliminar":
                if (!u.isPuedeEliminar()) {
                    denegarAcceso(req, resp, "Acceso restringido: solo el rol Administrador puede eliminar productos del inventario.");
                    return;
                }
                confirmarEliminacion(req, resp);
                break;
            case "comprar":
                if (!u.isPuedeComprar()) {
                    denegarAcceso(req, resp, "La compra en línea está disponible para cuentas de cliente.");
                    return;
                }
                confirmarCompra(req, resp);
                break;
            case "pedidos":
                // Defensa en profundidad: el historial exige un usuario real en sesión, no el invitado por defecto
                if (!hayUsuarioEnSesion(req)) {
                    resp.sendRedirect(req.getContextPath() + "/login");
                    return;
                }
                listar(req, resp, "pedidos");
                break;
            case "buscar":
                buscar(req, resp);
                break;
            default:
                listar(req, resp, "listar");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String accion = parametro(req, "accion", "");
        Usuario u = usuarioSesion(req);

        switch (accion) {
            case "registrar":
                if (!u.isPuedeRegistrar()) {
                    denegarAcceso(req, resp, "No cuenta con permisos para registrar productos.");
                    return;
                }
                guardar(req, resp, true);
                break;
            case "actualizar":
                if (!u.isPuedeEditar()) {
                    denegarAcceso(req, resp, "No cuenta con permisos para actualizar productos.");
                    return;
                }
                guardar(req, resp, false);
                break;
            case "eliminar":
                if (!u.isPuedeEliminar()) {
                    denegarAcceso(req, resp, "Solo el rol Administrador puede eliminar productos.");
                    return;
                }
                eliminar(req, resp);
                break;
            default:
                resp.sendRedirect(req.getContextPath() + "/productos");
        }
    }

    /** true si la sesión tiene un {@link Usuario} autenticado (usuarioObj), no solo atributos sueltos. */
    private static boolean hayUsuarioEnSesion(HttpServletRequest req) {
        HttpSession sesion = req.getSession(false);
        return sesion != null && sesion.getAttribute("usuarioObj") instanceof Usuario;
    }

    private Usuario usuarioSesion(HttpServletRequest req) {
        HttpSession sesion = req.getSession(false);
        if (sesion != null && sesion.getAttribute("usuarioObj") instanceof Usuario u) {
            return u;
        }
        String rol = sesion != null && sesion.getAttribute("rol") != null
                ? String.valueOf(sesion.getAttribute("rol"))
                : Usuario.ROL_CONSULTA;
        String user = sesion != null && sesion.getAttribute("usuario") != null
                ? String.valueOf(sesion.getAttribute("usuario"))
                : "invitado";
        return new Usuario(0, user, user, "", rol, true);
    }

    private void denegarAcceso(HttpServletRequest req, HttpServletResponse resp, String motivo)
            throws IOException {
        Controlador.mensaje(req, "error", motivo);
        resp.sendRedirect(req.getContextPath() + "/productos?accion=listar");
    }

    // ------------------------------------------------------------------ acciones

    private void listar(HttpServletRequest req, HttpServletResponse resp, String modo)
            throws ServletException, IOException {
        req.setAttribute("modo", modo);
        if ("pedidos".equals(modo)) {
            // El historial solo necesita los pedidos del usuario; no se consulta el catálogo
            try {
                req.setAttribute("pedidos", service.listarPedidos(usuarioSesion(req)));
            } catch (IllegalStateException ex) {
                req.setAttribute("errorPedidos", "No se pudo cargar el historial. Intenta nuevamente en unos momentos.");
            }
        } else {
            List<Producto> productos = service.listar();
            req.setAttribute("productos", productos);
            req.setAttribute("disponibles", service.contarDisponibles());
            req.setAttribute("agotados", service.contarAgotados());
        }
        Controlador.vista(req, resp, "productos/lista");
    }

    private void confirmarCompra(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String codigo = req.getParameter("codigo");
        if (codigo != null && !codigo.isBlank()) {
            Producto p = service.buscarPorCodigo(codigo);
            if (p == null) {
                Controlador.mensaje(req, "error", "No existe un producto con el código " + codigo.trim() + ".");
            } else if (!p.isDisponible() || p.getStock() <= 0) {
                Controlador.mensaje(req, "error", "El producto " + p.getNombre() + " se encuentra agotado actualmente.");
            } else {
                req.setAttribute("productoComprar", p);
            }
        }
        listar(req, resp, "comprar");
    }

    private void editar(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String codigo = req.getParameter("codigo");
        if (codigo == null || codigo.isBlank()) {
            listar(req, resp, "editar");
            return;
        }
        Producto p = service.buscarPorCodigo(codigo);
        if (p == null) {
            Controlador.mensaje(req, "error", "No existe un producto con el código " + codigo.trim() + ".");
            resp.sendRedirect(req.getContextPath() + "/productos?accion=editar");
            return;
        }
        Map<String, String> form = new HashMap<>();
        form.put("codigo", p.getCodigo());
        form.put("nombre", p.getNombre());
        form.put("categoria", p.getCategoria());
        form.put("precio", p.getPrecioPlano());
        form.put("stock", String.valueOf(p.getStock()));
        form.put("estado", p.getEstado());
        mostrarFormulario(req, resp, "editar", form, new HashMap<>());
    }

    private void confirmarEliminacion(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String codigo = req.getParameter("codigo");
        if (codigo != null && !codigo.isBlank()) {
            Producto p = service.buscarPorCodigo(codigo);
            if (p == null) {
                Controlador.mensaje(req, "error", "No existe un producto con el código " + codigo.trim() + ".");
            } else {
                req.setAttribute("productoEliminar", p);
            }
        }
        listar(req, resp, "eliminar");
    }

    private void buscar(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String criterio = parametro(req, "criterio", "nombre");
        if (!List.of("codigo", "nombre", "categoria").contains(criterio)) {
            criterio = "nombre";
        }
        String valor = parametro(req, "valor", "");
        req.setAttribute("criterio", criterio);
        req.setAttribute("valor", valor);
        req.setAttribute("categorias", service.nombresCategorias());
        if (!valor.isEmpty()) {
            req.setAttribute("resultados", service.buscar(criterio, valor));
            req.setAttribute("total", service.contar());
        }
        Controlador.vista(req, resp, "productos/buscar");
    }

    private void guardar(HttpServletRequest req, HttpServletResponse resp, boolean nuevo)
            throws ServletException, IOException {
        Map<String, String> form = leerFormulario(req);
        if (!nuevo) {
            // Editar no cambia el stock (se ajusta en Inventario): el formulario muestra el actual, no un valor recibido
            Producto actual = service.buscarPorCodigo(form.get("codigo"));
            form.put("stock", actual == null ? "0" : String.valueOf(actual.getStock()));
        }
        Map<String, String> errores = service.validar(form, nuevo);

        if (!nuevo && errores.isEmpty() && !service.existeCodigo(form.get("codigo"))) {
            Controlador.mensaje(req, "error", "El producto que intenta actualizar ya no existe.");
            resp.sendRedirect(req.getContextPath() + "/productos");
            return;
        }
        if (!errores.isEmpty()) {
            mostrarFormulario(req, resp, nuevo ? "registrar" : "editar", form, errores);
            return;
        }

        Producto p = service.construir(form);
        String detalle = p.getNombre() + " · " + p.getCategoria() + " · " + Formato.soles(p.getPrecio())
                + " · stock " + p.getStock();
        if (nuevo) {
            if (!service.registrar(p, usuarioSesion(req))) {
                Controlador.mensaje(req, "error", "No se pudo registrar el producto " + p.getCodigo()
                        + ". Verifica que el código no exista e intenta nuevamente.");
                resp.sendRedirect(req.getContextPath() + "/productos?accion=listar");
                return;
            }
            Controlador.mensaje(req, "ok", "Producto " + p.getCodigo() + " registrado correctamente.");
            Controlador.auditar(req, usuarioSesion(req), RegistroAuditoria.PRODUCTO_REGISTRADO,
                    "Producto " + p.getCodigo(), detalle);
        } else {
            if (!service.actualizar(p)) {
                Controlador.mensaje(req, "error", "No se pudo actualizar el producto " + p.getCodigo()
                        + ". Intenta nuevamente en unos momentos.");
                resp.sendRedirect(req.getContextPath() + "/productos?accion=listar");
                return;
            }
            Controlador.mensaje(req, "ok", "Producto " + p.getCodigo() + " actualizado correctamente.");
            Controlador.auditar(req, usuarioSesion(req), RegistroAuditoria.PRODUCTO_ACTUALIZADO,
                    "Producto " + p.getCodigo(), detalle);
        }
        // Patrón POST-Redirect-GET: evita reenviar el formulario al recargar.
        resp.sendRedirect(req.getContextPath() + "/productos?accion=listar");
    }

    private void eliminar(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String codigo = ProductoService.normalizarCodigo(req.getParameter("codigo"));
        Producto p = service.buscarPorCodigo(codigo);
        ProductoService.ResultadoEliminacion resultado = p == null
                ? ProductoService.ResultadoEliminacion.NO_ENCONTRADO
                : service.eliminar(codigo);
        switch (resultado) {
            case ELIMINADO -> {
                Controlador.mensaje(req, "ok", "Producto " + codigo + " (" + p.getNombre() + ") eliminado.");
                Controlador.auditar(req, usuarioSesion(req), RegistroAuditoria.PRODUCTO_ELIMINADO,
                        "Producto " + codigo, p.getNombre());
            }
            case CON_PEDIDOS ->
                Controlador.mensaje(req, "error",
                        "No se puede eliminar el producto " + codigo
                        + " porque tiene pedidos o movimientos de inventario.");
            case ERROR ->
                Controlador.mensaje(req, "error",
                        "No se pudo eliminar el producto " + codigo + ". Intenta nuevamente en unos momentos.");
            default ->
                Controlador.mensaje(req, "error", "No se encontró el producto " + codigo + ".");
        }
        resp.sendRedirect(req.getContextPath() + "/productos?accion=eliminar");
    }

    // ------------------------------------------------------------------ auxiliares

    private void mostrarFormulario(HttpServletRequest req, HttpServletResponse resp, String modo,
            Map<String, String> form, Map<String, String> errores) throws ServletException, IOException {
        req.setAttribute("modo", modo);
        req.setAttribute("form", form);
        req.setAttribute("errores", errores);
        req.setAttribute("categorias", service.nombresCategorias());
        req.setAttribute("estados", ProductoService.ESTADOS);
        Controlador.vista(req, resp, "productos/formulario");
    }

    private Map<String, String> formularioVacio() {
        Map<String, String> form = new HashMap<>();
        for (String c : CAMPOS) {
            form.put(c, "");
        }
        form.put("estado", Producto.DISPONIBLE);
        return form;
    }

    private Map<String, String> leerFormulario(HttpServletRequest req) {
        Map<String, String> form = new HashMap<>();
        for (String c : CAMPOS) {
            String v = req.getParameter(c);
            form.put(c, v == null ? "" : v.trim());
        }
        form.put("codigo", ProductoService.normalizarCodigo(form.get("codigo")));
        return form;
    }

    private static String parametro(HttpServletRequest req, String nombre, String porDefecto) {
        String v = req.getParameter(nombre);
        return v == null || v.isBlank() ? porDefecto : v.trim();
    }
}
