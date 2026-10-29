package pe.edu.sistema.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import pe.edu.sistema.model.Categoria;
import pe.edu.sistema.model.MovimientoStock;
import pe.edu.sistema.model.Pedido;
import pe.edu.sistema.model.Producto;
import pe.edu.sistema.model.RegistroAuditoria;
import pe.edu.sistema.model.ResumenVentas;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.service.AdminService;
import pe.edu.sistema.service.AuditoriaService;
import pe.edu.sistema.service.ProductoService;
import pe.edu.sistema.service.UsuarioService;
import pe.edu.sistema.util.Formato;

/**
 * Controlador del módulo de administración de NEXORA (panel, pedidos, inventario, ventas,
 * usuarios, categorías y auditoría).
 *
 * GET  /admin?accion=panel | pedidos | pedido&id= | inventario[&codigo=] | ventas[&formato=csv] | usuarios | categorias | auditoria
 * POST /admin?accion=estadoPedido | anularPedido | ajustarStock | crearUsuario | estadoUsuario | rolUsuario |
 *      claveUsuario | crearCategoria | editarCategoria | estadoCategoria | eliminarCategoria
 *
 * Todo el personal ve el panel; los permisos de cada acción salen de los métodos de {@link Usuario}.
 * Cada POST exitoso queda en la auditoría. Los parámetros numéricos o de fecha inválidos nunca producen un 500.
 */
@WebServlet(name = "AdminServlet", urlPatterns = {"/admin"})
public class AdminServlet extends HttpServlet {

    private static final int MAX_MOVIMIENTOS = 50;
    private static final int MAX_TOP_PRODUCTOS = 10;
    private static final int MAX_AUDITORIA = 300;
    /** Años admitidos en los filtros por fecha: fuera de este rango el filtro se ignora. */
    private static final int ANIO_MIN_FILTRO = 2000;
    private static final int ANIO_MAX_FILTRO = 2100;

    private static final String MSG_SOLO_PERSONAL = "El panel de administración es solo para el personal de la tienda.";
    private static final String MSG_SOLO_ADMIN = "Esta sección es solo para el rol Administrador.";
    private static final String MSG_ID_INVALIDO = "El identificador recibido no es válido.";
    private static final String MSG_FECHA_INVALIDA = "Alguna de las fechas indicadas no es válida.";
    private static final String MSG_FALLO_BD =
            "No se pudo completar la operación. Intenta nuevamente en unos momentos.";

    private final ProductoService productos = ProductoService.getInstancia();
    private final UsuarioService usuarios = new UsuarioService();
    private final AdminService reportes = AdminService.getInstancia();

    // ------------------------------------------------------------------ GET

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Usuario u = usuarioSesion(req);
        if (!esPersonal(req, resp, u)) {
            return;
        }
        sinCache(resp);
        String accion = parametro(req, "accion", "panel");
        try {
            switch (accion) {
                case "pedidos" -> pedidos(req, resp, u);
                case "pedido" -> pedido(req, resp, u);
                case "inventario" -> inventario(req, resp);
                case "ventas" -> ventas(req, resp);
                case "usuarios" -> {
                    if (exigir(req, resp, u.isPuedeAdministrarSistema(), MSG_SOLO_ADMIN)) {
                        mostrarUsuarios(req, resp, formularioUsuarioVacio(), null);
                    }
                }
                case "categorias" -> {
                    if (exigir(req, resp, u.isPuedeAdministrarSistema(), MSG_SOLO_ADMIN)) {
                        categorias(req, resp);
                    }
                }
                case "auditoria" -> {
                    if (exigir(req, resp, u.isPuedeAdministrarSistema(), MSG_SOLO_ADMIN)) {
                        auditoria(req, resp);
                    }
                }
                default -> panel(req, resp);
            }
        } catch (IllegalStateException ex) {
            Controlador.mensaje(req, "error", "No se pudo cargar la información solicitada. Intenta nuevamente en unos momentos.");
            resp.sendRedirect(req.getContextPath() + "/admin");
        } catch (DateTimeException ex) {
            Controlador.mensaje(req, "error", MSG_FECHA_INVALIDA);
            resp.sendRedirect(req.getContextPath() + "/admin");
        }
    }

    private void panel(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        ResumenVentas resumen;
        try {
            resumen = reportes.resumen();
        } catch (IllegalStateException ex) {
            // Se muestra el panel vacío con el aviso: redirigir a /admin repetiría el fallo
            Controlador.mensaje(req, "error", "No se pudo calcular el resumen. Intenta nuevamente en unos momentos.");
            resumen = new ResumenVentas();
        }
        req.setAttribute("resumen", resumen);
        req.setAttribute("umbralStock", AdminService.UMBRAL_STOCK_BAJO);
        Controlador.vista(req, resp, "admin/panel");
    }

    private void pedidos(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        String estado = parametro(req, "estado", "");
        if (!Pedido.ESTADOS.contains(estado)) {
            estado = "";
        }
        String cliente = Controlador.recortar(parametro(req, "cliente", ""), 60);
        LocalDate[] rango = ordenar(fecha(req, "desde"), fecha(req, "hasta"));

        List<Pedido> lista = productos.filtrarPedidos(u, estado.isEmpty() ? null : estado,
                cliente.isEmpty() ? null : cliente, rango[0], rango[1]);
        // El total del filtro solo suma ventas válidas: los pedidos anulados se listan pero no cuentan
        double total = lista.stream().filter(Pedido::isVentaValida).mapToDouble(Pedido::getTotal).sum();

        req.setAttribute("pedidos", lista);
        req.setAttribute("estados", Pedido.ESTADOS);
        req.setAttribute("fEstado", estado);
        req.setAttribute("fCliente", cliente);
        req.setAttribute("fDesde", texto(rango[0]));
        req.setAttribute("fHasta", texto(rango[1]));
        req.setAttribute("totalFiltradoTexto", Formato.soles(total));
        Controlador.vista(req, resp, "admin/pedidos");
    }

    private void pedido(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        Integer id = entero(req.getParameter("id"));
        Pedido p = id == null ? null : productos.buscarPedido(id, u);
        if (p == null) {
            Controlador.mensaje(req, "error", "No se encontró el pedido solicitado.");
            resp.sendRedirect(req.getContextPath() + "/admin?accion=pedidos");
            return;
        }
        req.setAttribute("pedido", p);
        req.setAttribute("siguienteEstado", p.getSiguienteEstado());
        Controlador.vista(req, resp, "admin/pedido");
    }

    private void inventario(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String codigo = ProductoService.normalizarCodigo(req.getParameter("codigo"));
        Producto seleccionado = codigo.isEmpty() ? null : productos.buscarPorCodigo(codigo);
        if (!codigo.isEmpty() && seleccionado == null) {
            Controlador.mensaje(req, "error", "No existe un producto con el código " + Controlador.recortar(codigo, 20) + ".");
        }
        mostrarInventario(req, resp, seleccionado,
                formularioMovimiento(seleccionado == null ? "" : seleccionado.getCodigo(), MovimientoStock.ENTRADA, "", ""),
                null);
    }

    /** Pinta el inventario con el formulario de ajuste (valores previos y error, si los hay). */
    private void mostrarInventario(HttpServletRequest req, HttpServletResponse resp, Producto seleccionado,
            Map<String, String> form, String errorFormulario) throws ServletException, IOException {
        req.setAttribute("productos", productos.listar());
        req.setAttribute("movimientos",
                productos.listarMovimientos(seleccionado == null ? null : seleccionado.getCodigo(), MAX_MOVIMIENTOS));
        req.setAttribute("productoSel", seleccionado);
        req.setAttribute("tiposMovimiento", MovimientoStock.TIPOS_MANUALES);
        req.setAttribute("umbralStock", AdminService.UMBRAL_STOCK_BAJO);
        req.setAttribute("form", form);
        req.setAttribute("errorFormulario", errorFormulario);
        Controlador.vista(req, resp, "admin/inventario");
    }

    private void ventas(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        LocalDate hoy = LocalDate.now();
        LocalDate desde = fecha(req, "desde");
        LocalDate hasta = fecha(req, "hasta");
        if (desde == null) {
            desde = hoy.withDayOfMonth(1);
        }
        if (hasta == null) {
            hasta = hoy;
        }
        LocalDate[] rango = ordenar(desde, hasta);
        desde = rango[0];
        hasta = rango[1];

        if ("csv".equalsIgnoreCase(parametro(req, "formato", ""))) {
            descargarVentasCsv(resp, desde, hasta);
            return;
        }

        double total = reportes.totalVentas(desde, hasta);
        int numero = reportes.numeroVentas(desde, hasta);
        req.setAttribute("porDia", reportes.ventasPorDia(desde, hasta));
        req.setAttribute("porCategoria", reportes.ventasPorCategoria(desde, hasta));
        req.setAttribute("porMetodo", reportes.ventasPorMetodo(desde, hasta));
        req.setAttribute("porProducto", reportes.ventasPorProducto(desde, hasta, MAX_TOP_PRODUCTOS));
        req.setAttribute("totalVentasTexto", Formato.soles(total));
        req.setAttribute("numeroVentas", numero);
        req.setAttribute("ticketPromedioTexto", Formato.soles(numero > 0 ? total / numero : 0));
        req.setAttribute("fDesde", texto(desde));
        req.setAttribute("fHasta", texto(hasta));
        Controlador.vista(req, resp, "admin/ventas");
    }

    /** Descarga el CSV del período (UTF-8; el BOM lo incluye el servicio para que Excel lo abra bien). */
    private void descargarVentasCsv(HttpServletResponse resp, LocalDate desde, LocalDate hasta) throws IOException {
        byte[] contenido = reportes.exportarVentasCsv(desde, hasta).getBytes(StandardCharsets.UTF_8);
        String nombre = "ventas_" + desde.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "_" + hasta.format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        resp.setContentType("text/csv; charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + nombre + "\"");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Pragma", "no-cache");
        resp.setContentLength(contenido.length);
        resp.getOutputStream().write(contenido);
    }

    private void categorias(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Categoria editar = null;
        Integer id = entero(req.getParameter("editar"));
        if (id != null) {
            editar = buscarCategoria(id);
            if (editar == null) {
                Controlador.mensaje(req, "error", "No se encontró la categoría que intentas editar.");
            }
        } else if (!parametro(req, "editar", "").isEmpty()) {
            Controlador.mensaje(req, "error", MSG_ID_INVALIDO);
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("nombre", editar == null ? "" : editar.getNombre());
        form.put("descripcion", editar == null || editar.getDescripcion() == null ? "" : editar.getDescripcion());
        mostrarCategorias(req, resp, editar, form, null);
    }

    private void auditoria(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String usuario = Controlador.recortar(parametro(req, "usuario", ""), 40);
        String tipo = parametro(req, "tipo", "");
        if (!RegistroAuditoria.ACCIONES.contains(tipo)) {
            tipo = "";
        }
        LocalDate[] rango = ordenar(fecha(req, "desde"), fecha(req, "hasta"));

        req.setAttribute("registros", AuditoriaService.getInstancia().listar(
                usuario.isEmpty() ? null : usuario, tipo.isEmpty() ? null : tipo, rango[0], rango[1], MAX_AUDITORIA));
        req.setAttribute("acciones", RegistroAuditoria.ACCIONES);
        req.setAttribute("fUsuario", usuario);
        req.setAttribute("fAccion", tipo);
        req.setAttribute("fDesde", texto(rango[0]));
        req.setAttribute("fHasta", texto(rango[1]));
        Controlador.vista(req, resp, "admin/auditoria");
    }

    // ------------------------------------------------------------------ POST

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Usuario u = usuarioSesion(req);
        if (!esPersonal(req, resp, u)) {
            return;
        }
        String accion = parametro(req, "accion", "");
        try {
            switch (accion) {
                case "estadoPedido" -> estadoPedido(req, resp, u);
                case "anularPedido" -> anularPedido(req, resp, u);
                case "ajustarStock" -> ajustarStock(req, resp, u);
                case "crearUsuario" -> crearUsuario(req, resp, u);
                case "estadoUsuario" -> estadoUsuario(req, resp, u);
                case "rolUsuario" -> rolUsuario(req, resp, u);
                case "claveUsuario" -> claveUsuario(req, resp, u);
                case "crearCategoria" -> crearCategoria(req, resp, u);
                case "editarCategoria" -> editarCategoria(req, resp, u);
                case "estadoCategoria" -> estadoCategoria(req, resp, u);
                case "eliminarCategoria" -> eliminarCategoria(req, resp, u);
                default -> resp.sendRedirect(req.getContextPath() + "/admin");
            }
        } catch (IllegalStateException ex) {
            Controlador.mensaje(req, "error", MSG_FALLO_BD);
            resp.sendRedirect(req.getContextPath() + "/admin");
        } catch (DateTimeException ex) {
            Controlador.mensaje(req, "error", MSG_FECHA_INVALIDA);
            resp.sendRedirect(req.getContextPath() + "/admin");
        }
    }

    private void estadoPedido(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        if (!exigir(req, resp, u.isPuedeGestionarPedidos(), "Tu rol no puede cambiar el estado de los pedidos.")) {
            return;
        }
        Integer id = entero(req.getParameter("id"));
        if (id == null) {
            idInvalido(req, resp, "pedidos");
            return;
        }
        String estado = parametro(req, "estado", "");
        Pedido antes = productos.buscarPedido(id, u);
        // El estado anterior se guarda antes de llamar al servicio: la auditoría muestra el cambio real
        String estadoAnterior = antes == null ? null : antes.getEstado();
        String error = productos.cambiarEstadoPedido(u, id, estado);
        String codigo = antes == null ? "#" + id : antes.getCodigoPedido();
        if (error != null) {
            Controlador.mensaje(req, "error", error);
        } else {
            Controlador.mensaje(req, "ok", "Pedido " + codigo + " actualizado a \"" + estado + "\".");
            Controlador.auditar(req, u, RegistroAuditoria.PEDIDO_ESTADO, "Pedido " + codigo,
                    (estadoAnterior == null ? "" : estadoAnterior + " → ") + estado);
        }
        resp.sendRedirect(req.getContextPath() + "/admin?accion=pedido&id=" + id);
    }

    private void anularPedido(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        if (!exigir(req, resp, u.isPuedeAnularPedidos(), "Solo el Administrador puede anular pedidos.")) {
            return;
        }
        Integer id = entero(req.getParameter("id"));
        if (id == null) {
            idInvalido(req, resp, "pedidos");
            return;
        }
        String motivo = parametro(req, "motivo", "");
        Pedido antes = productos.buscarPedido(id, u);
        String error = productos.anularPedido(u, id, motivo);
        String codigo = antes == null ? "#" + id : antes.getCodigoPedido();
        if (error != null) {
            Controlador.mensaje(req, "error", error);
        } else {
            Controlador.mensaje(req, "ok", "Pedido " + codigo + " anulado. El pago quedó reembolsado y el stock se devolvió.");
            Controlador.auditar(req, u, RegistroAuditoria.PEDIDO_ANULADO, "Pedido " + codigo, "Motivo: " + motivo);
        }
        resp.sendRedirect(req.getContextPath() + "/admin?accion=pedido&id=" + id);
    }

    private void ajustarStock(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        if (!exigir(req, resp, u.isPuedeAjustarStock(), "Tu rol no puede registrar movimientos de stock.")) {
            return;
        }
        String codigo = ProductoService.normalizarCodigo(req.getParameter("codigo"));
        String volver = req.getContextPath() + "/admin?accion=inventario"
                + (codigo.isEmpty() ? "" : "&codigo=" + URLEncoder.encode(codigo, StandardCharsets.UTF_8));
        String cantidadTexto = parametro(req, "cantidad", "");
        String tipo = tipoOficial(parametro(req, "tipo", ""));
        String motivo = parametro(req, "motivo", "");
        Integer cantidad = entero(cantidadTexto);
        String error = cantidad == null
                ? "La cantidad debe ser un número entero."
                : productos.ajustarStock(u, codigo, tipo, cantidad, motivo);
        if (error != null) {
            // Se vuelve a pintar el inventario con lo escrito, en vez de perder los datos con una redirección
            Producto seleccionado = codigo.isEmpty() ? null : productos.buscarPorCodigo(codigo);
            mostrarInventario(req, resp, seleccionado,
                    formularioMovimiento(codigo, tipo, cantidadTexto, motivo), error);
            return;
        }
        String signo = cantidad > 0 ? "+" : "";
        Controlador.mensaje(req, "ok", "Movimiento registrado en " + codigo + ": " + tipo + " " + signo + cantidad + ".");
        Controlador.auditar(req, u, RegistroAuditoria.STOCK_AJUSTE, "Producto " + codigo,
                tipo + " " + signo + cantidad + " · " + motivo);
        resp.sendRedirect(volver);
    }

    private void crearUsuario(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        if (!exigir(req, resp, u.isPuedeAdministrarSistema(), MSG_SOLO_ADMIN)) {
            return;
        }
        String username = parametro(req, "username", "");
        String nombre = parametro(req, "nombreCompleto", "");
        String correo = parametro(req, "correo", "");
        String rol = parametro(req, "rol", "");
        String clave = req.getParameter("clave");   // sin recortar: el servicio rechaza espacios en los extremos

        String error = usuarios.crearUsuario(u, username, nombre, correo, rol, clave == null ? "" : clave);
        if (error != null) {
            // La clave nunca vuelve al formulario
            Map<String, String> form = new LinkedHashMap<>();
            form.put("username", username);
            form.put("nombreCompleto", nombre);
            form.put("correo", correo);
            form.put("rol", rol);
            mostrarUsuarios(req, resp, form, error);
            return;
        }
        String creado = username.toLowerCase(Locale.ROOT);
        Controlador.mensaje(req, "ok", "Usuario " + creado + " creado con el rol " + rol + ".");
        Controlador.auditar(req, u, RegistroAuditoria.USUARIO_CREADO, "Usuario " + creado, "Rol: " + rol);
        resp.sendRedirect(req.getContextPath() + "/admin?accion=usuarios");
    }

    private void estadoUsuario(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        if (!exigir(req, resp, u.isPuedeAdministrarSistema(), MSG_SOLO_ADMIN)) {
            return;
        }
        Integer id = entero(req.getParameter("id"));
        Boolean activo = booleano(req.getParameter("activo"));
        if (id == null || activo == null) {
            idInvalido(req, resp, "usuarios");
            return;
        }
        Usuario objetivo = usuarios.buscarPorId(id);
        String nombre = objetivo == null ? "#" + id : objetivo.getUsername();
        String error = usuarios.cambiarEstadoUsuario(u, id, activo);
        if (error != null) {
            Controlador.mensaje(req, "error", error);
        } else {
            Controlador.mensaje(req, "ok", "Usuario " + nombre + (activo ? " activado." : " desactivado."));
            Controlador.auditar(req, u, RegistroAuditoria.USUARIO_ESTADO, "Usuario " + nombre,
                    activo ? "Activado" : "Desactivado");
        }
        resp.sendRedirect(req.getContextPath() + "/admin?accion=usuarios");
    }

    private void rolUsuario(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        if (!exigir(req, resp, u.isPuedeAdministrarSistema(), MSG_SOLO_ADMIN)) {
            return;
        }
        Integer id = entero(req.getParameter("id"));
        if (id == null) {
            idInvalido(req, resp, "usuarios");
            return;
        }
        String rol = parametro(req, "rol", "");
        Usuario objetivo = usuarios.buscarPorId(id);
        String nombre = objetivo == null ? "#" + id : objetivo.getUsername();
        String error = usuarios.cambiarRol(u, id, rol);
        if (error != null) {
            Controlador.mensaje(req, "error", error);
        } else {
            Controlador.mensaje(req, "ok", "Usuario " + nombre + " ahora tiene el rol " + rol + ".");
            Controlador.auditar(req, u, RegistroAuditoria.USUARIO_ROL, "Usuario " + nombre,
                    (objetivo == null ? "" : objetivo.getRol() + " → ") + rol);
        }
        resp.sendRedirect(req.getContextPath() + "/admin?accion=usuarios");
    }

    private void claveUsuario(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        if (!exigir(req, resp, u.isPuedeAdministrarSistema(), MSG_SOLO_ADMIN)) {
            return;
        }
        Integer id = entero(req.getParameter("id"));
        if (id == null) {
            idInvalido(req, resp, "usuarios");
            return;
        }
        String clave = req.getParameter("clave");
        Usuario objetivo = usuarios.buscarPorId(id);
        String nombre = objetivo == null ? "#" + id : objetivo.getUsername();
        String error = usuarios.restablecerClave(u, id, clave == null ? "" : clave);
        if (error != null) {
            Controlador.mensaje(req, "error", error);
        } else {
            Controlador.mensaje(req, "ok", "Contraseña de " + nombre + " restablecida. La cuenta quedó desbloqueada.");
            // La contraseña nunca se registra
            Controlador.auditar(req, u, RegistroAuditoria.USUARIO_CLAVE, "Usuario " + nombre, "Contraseña restablecida por el administrador");
        }
        resp.sendRedirect(req.getContextPath() + "/admin?accion=usuarios");
    }

    private void crearCategoria(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        if (!exigir(req, resp, u.isPuedeAdministrarSistema(), MSG_SOLO_ADMIN)) {
            return;
        }
        String nombre = parametro(req, "nombre", "");
        String descripcion = parametro(req, "descripcion", "");
        String error = productos.crearCategoria(u, nombre, descripcion);
        if (error != null) {
            mostrarCategorias(req, resp, null, formularioCategoria(nombre, descripcion), error);
            return;
        }
        Controlador.mensaje(req, "ok", "Categoría " + nombre + " creada.");
        Controlador.auditar(req, u, RegistroAuditoria.CATEGORIA, "Categoría " + nombre, "Creada");
        resp.sendRedirect(req.getContextPath() + "/admin?accion=categorias");
    }

    private void editarCategoria(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        if (!exigir(req, resp, u.isPuedeAdministrarSistema(), MSG_SOLO_ADMIN)) {
            return;
        }
        Integer id = entero(req.getParameter("id"));
        if (id == null) {
            idInvalido(req, resp, "categorias");
            return;
        }
        String nombre = parametro(req, "nombre", "");
        String descripcion = parametro(req, "descripcion", "");
        Categoria antes = buscarCategoria(id);
        String error = productos.editarCategoria(u, id, nombre, descripcion);
        if (error != null) {
            if (antes == null) {
                Controlador.mensaje(req, "error", error);
                resp.sendRedirect(req.getContextPath() + "/admin?accion=categorias");
            } else {
                mostrarCategorias(req, resp, antes, formularioCategoria(nombre, descripcion), error);
            }
            return;
        }
        Controlador.mensaje(req, "ok", "Categoría " + nombre + " actualizada.");
        boolean renombrada = antes != null && !antes.getNombre().equals(nombre);
        Controlador.auditar(req, u, RegistroAuditoria.CATEGORIA, "Categoría " + nombre,
                renombrada ? "Editada (antes: " + antes.getNombre() + ")" : "Editada");
        resp.sendRedirect(req.getContextPath() + "/admin?accion=categorias");
    }

    private void estadoCategoria(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        if (!exigir(req, resp, u.isPuedeAdministrarSistema(), MSG_SOLO_ADMIN)) {
            return;
        }
        Integer id = entero(req.getParameter("id"));
        Boolean activo = booleano(req.getParameter("activo"));
        if (id == null || activo == null) {
            idInvalido(req, resp, "categorias");
            return;
        }
        Categoria antes = buscarCategoria(id);
        String nombre = antes == null ? "#" + id : antes.getNombre();
        String error = productos.cambiarEstadoCategoria(u, id, activo);
        if (error != null) {
            Controlador.mensaje(req, "error", error);
        } else {
            Controlador.mensaje(req, "ok", "Categoría " + nombre + (activo ? " activada." : " desactivada."));
            Controlador.auditar(req, u, RegistroAuditoria.CATEGORIA, "Categoría " + nombre,
                    activo ? "Activada" : "Desactivada");
        }
        resp.sendRedirect(req.getContextPath() + "/admin?accion=categorias");
    }

    private void eliminarCategoria(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        if (!exigir(req, resp, u.isPuedeAdministrarSistema(), MSG_SOLO_ADMIN)) {
            return;
        }
        Integer id = entero(req.getParameter("id"));
        if (id == null) {
            idInvalido(req, resp, "categorias");
            return;
        }
        Categoria antes = buscarCategoria(id);
        String nombre = antes == null ? "#" + id : antes.getNombre();
        String error = productos.eliminarCategoria(u, id);
        if (error != null) {
            Controlador.mensaje(req, "error", error);
        } else {
            Controlador.mensaje(req, "ok", "Categoría " + nombre + " eliminada.");
            Controlador.auditar(req, u, RegistroAuditoria.CATEGORIA, "Categoría " + nombre, "Eliminada");
        }
        resp.sendRedirect(req.getContextPath() + "/admin?accion=categorias");
    }

    // ------------------------------------------------------------------ vistas con formulario

    private void mostrarUsuarios(HttpServletRequest req, HttpServletResponse resp,
            Map<String, String> form, String errorFormulario) throws ServletException, IOException {
        req.setAttribute("usuarios", usuarios.listarUsuarios());
        req.setAttribute("roles", UsuarioService.ROLES);
        req.setAttribute("form", form);
        req.setAttribute("errorFormulario", errorFormulario);
        Controlador.vista(req, resp, "admin/usuarios");
    }

    private void mostrarCategorias(HttpServletRequest req, HttpServletResponse resp, Categoria editar,
            Map<String, String> form, String errorFormulario) throws ServletException, IOException {
        req.setAttribute("categorias", productos.listarCategorias(false));
        req.setAttribute("editar", editar);
        req.setAttribute("form", form);
        req.setAttribute("errorFormulario", errorFormulario);
        Controlador.vista(req, resp, "admin/categorias");
    }

    private static Map<String, String> formularioUsuarioVacio() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("username", "");
        form.put("nombreCompleto", "");
        form.put("correo", "");
        form.put("rol", Usuario.ROL_CLIENTE);
        return form;
    }

    private static Map<String, String> formularioMovimiento(String codigo, String tipo, String cantidad, String motivo) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("codigo", codigo);
        form.put("tipo", tipo);
        form.put("cantidad", cantidad);
        form.put("motivo", motivo);
        return form;
    }

    /** Nombre oficial del tipo de movimiento manual (sin distinguir mayúsculas); si no coincide, el texto recibido. */
    private static String tipoOficial(String tipo) {
        for (String manual : MovimientoStock.TIPOS_MANUALES) {
            if (manual.equalsIgnoreCase(tipo)) {
                return manual;
            }
        }
        return tipo;
    }

    private static Map<String, String> formularioCategoria(String nombre, String descripcion) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("nombre", nombre);
        form.put("descripcion", descripcion);
        return form;
    }

    // ------------------------------------------------------------------ permisos y auxiliares

    private static Usuario usuarioSesion(HttpServletRequest req) {
        HttpSession sesion = req.getSession(false);
        return sesion != null && sesion.getAttribute("usuarioObj") instanceof Usuario u ? u : null;
    }

    /** Solo el personal entra; sin sesión va al login y un cliente vuelve a la tienda con un aviso. */
    private static boolean esPersonal(HttpServletRequest req, HttpServletResponse resp, Usuario u) throws IOException {
        if (u == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return false;
        }
        if (!u.isPersonal()) {
            Controlador.mensaje(req, "error", MSG_SOLO_PERSONAL);
            resp.sendRedirect(req.getContextPath() + "/inicio");
            return false;
        }
        return true;
    }

    /** Si el rol no tiene el permiso, deja un aviso, vuelve al panel y devuelve false. */
    private static boolean exigir(HttpServletRequest req, HttpServletResponse resp, boolean permitido, String mensaje)
            throws IOException {
        if (permitido) {
            return true;
        }
        Controlador.mensaje(req, "error", mensaje);
        resp.sendRedirect(req.getContextPath() + "/admin");
        return false;
    }

    private static void idInvalido(HttpServletRequest req, HttpServletResponse resp, String seccion) throws IOException {
        Controlador.mensaje(req, "error", MSG_ID_INVALIDO);
        resp.sendRedirect(req.getContextPath() + "/admin?accion=" + seccion);
    }

    private Categoria buscarCategoria(int id) {
        for (Categoria c : productos.listarCategorias(false)) {
            if (c.getIdCategoria() == id) {
                return c;
            }
        }
        return null;
    }

    /** Las páginas del módulo muestran datos de clientes y cuentas: el navegador no debe guardarlas en caché. */
    private static void sinCache(HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Pragma", "no-cache");
    }

    /** Entero del parámetro, o null si falta o no es un número válido. */
    private static Integer entero(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return Integer.valueOf(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** true/1/on/si → TRUE; false/0/off/no → FALSE; cualquier otra cosa → null. */
    private static Boolean booleano(String texto) {
        if (texto == null) {
            return null;
        }
        return switch (texto.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "on", "si", "sí" -> Boolean.TRUE;
            case "false", "0", "off", "no" -> Boolean.FALSE;
            default -> null;
        };
    }

    /** Fecha yyyy-MM-dd del parámetro; null si falta, es inválida o su año no está entre 2000 y 2100 (se ignora el filtro). */
    private static LocalDate fecha(HttpServletRequest req, String nombre) {
        String v = req.getParameter(nombre);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            LocalDate fecha = LocalDate.parse(v.trim());
            return fecha.getYear() >= ANIO_MIN_FILTRO && fecha.getYear() <= ANIO_MAX_FILTRO ? fecha : null;
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Devuelve {desde, hasta} con las fechas en orden cronológico cuando ambas existen. */
    private static LocalDate[] ordenar(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            return new LocalDate[]{hasta, desde};
        }
        return new LocalDate[]{desde, hasta};
    }

    /** Fecha en formato yyyy-MM-dd para los campos de fecha; cadena vacía si no hay filtro. */
    private static String texto(LocalDate fecha) {
        return fecha == null ? "" : fecha.toString();
    }

    private static String parametro(HttpServletRequest req, String nombre, String porDefecto) {
        String v = req.getParameter(nombre);
        return v == null || v.isBlank() ? porDefecto : v.trim();
    }
}
