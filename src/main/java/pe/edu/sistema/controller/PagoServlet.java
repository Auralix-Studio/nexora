package pe.edu.sistema.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import pe.edu.sistema.model.Pago;
import pe.edu.sistema.model.Pedido;
import pe.edu.sistema.model.Producto;
import pe.edu.sistema.model.RegistroAuditoria;
import pe.edu.sistema.model.ResultadoCompra;
import pe.edu.sistema.model.SolicitudPago;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.service.PasarelaPagoService;
import pe.edu.sistema.service.ProductoService;
import pe.edu.sistema.util.Formato;

/**
 * Controlador de la pasarela de pago simulada de NEXORA (compra directa de un producto).
 *
 * GET  /pago?codigo=P001&cantidad=2        checkout (solo cuentas de cliente); emite un token de un solo uso
 * POST /pago                               procesa el pago; aprobado -> redirige al comprobante
 * GET  /pago?accion=comprobante&pedido=N   comprobante del pedido (dueño o personal)
 *
 * Anti doble envío y precio cambiado: cada checkout lleva un token ({@code tokenCheckout}) guardado en la
 * sesión junto con el producto, la cantidad y el total mostrados. El POST lo consume de forma atómica:
 * un segundo envío con el mismo token no vuelve a cobrar y el pago solo se procesa si el precio actual
 * sigue coincidiendo con el total que vio el cliente.
 */
@WebServlet(name = "PagoServlet", urlPatterns = {"/pago"})
public class PagoServlet extends HttpServlet {

    private static final String MSG_SOLO_CLIENTES = "La compra en línea está disponible para cuentas de cliente.";
    private static final String MSG_CHECKOUT_EXPIRADO =
            "Este pago ya se está procesando o el formulario expiró. Revisa tus pedidos.";
    private static final String MSG_PRECIO_CAMBIO =
            "El precio del producto cambió. Revisa el nuevo total antes de pagar.";

    /** Atributo de sesión con los checkouts recientes: token -> Pendiente (sin pagar) o Integer (id del pedido creado). */
    private static final String ATRIBUTO_CHECKOUTS = "checkouts";
    private static final int MAX_CHECKOUTS = 20;
    private static final Object CANDADO_CHECKOUTS = new Object();

    private final ProductoService service = ProductoService.getInstancia();

    /** Producto y cantidad ya verificados contra el catálogo. */
    private record Compra(Producto producto, int cantidad) {
    }

    /** Checkout emitido y todavía sin pagar: lo que el cliente vio cuando se generó el token. */
    private record Pendiente(String codigo, int cantidad, long totalCentimos) implements Serializable {
    }

    /** Conserva solo los {@value #MAX_CHECKOUTS} tokens más recientes para que la sesión no crezca sin límite. */
    private static final class CheckoutsRecientes extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
            return size() > MAX_CHECKOUTS;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Usuario u = usuarioSesion(req);
        if (u == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        if ("comprobante".equals(parametro(req, "accion", ""))) {
            comprobante(req, resp, u);
            return;
        }
        Compra compra = validarCompra(req, resp, u);
        if (compra == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        // Datos de despacho y entrega
        form.put("tipoEnvio", "DOMICILIO");
        form.put("departamento", "Lima");
        form.put("distrito", "Miraflores");
        form.put("direccion", "");
        form.put("referencia", "");
        // Datos del cliente: lo último que escribió en esta sesión y, si no hay, lo guardado en su cuenta
        Object sesDni = req.getSession().getAttribute("clienteDni");
        Object sesTel = req.getSession().getAttribute("clienteTelefono");
        String telefono = sesTel instanceof String t && !t.isBlank() ? t : (u.getTelefono() != null ? u.getTelefono() : "");
        String documento = sesDni instanceof String d && !d.isBlank() ? d : (u.getDni() != null ? u.getDni() : "");
        form.put("telefonoContacto", telefono);
        form.put("correoComprobante", u.getCorreo() != null ? u.getCorreo() : "");
        form.put("notasPedido", "");

        // Método de pago y comprobante
        form.put("metodo", Pago.METODO_TARJETA);
        form.put("titular", titularPorDefecto(u));
        form.put("vencimiento", "");
        form.put("celular", telefono);
        form.put("tipoComprobante", Pago.BOLETA);
        form.put("documento", documento);
        form.put("razonSocial", "");
        mostrarCheckout(req, resp, compra, form, new HashMap<>(), null, emitirToken(req, compra));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Usuario u = usuarioSesion(req);
        if (u == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        if (!esCliente(req, resp, u)) {
            return;
        }

        // El token se consume antes de hacer nada más: de dos envíos simultáneos solo uno lo obtiene.
        String token = valor(req, "tokenCheckout");
        Map<String, Object> checkouts = checkouts(req.getSession());
        Object estado = token.isEmpty() ? null : checkouts.remove(token);

        if (estado instanceof Integer idPedido) {
            // Ese checkout ya produjo un pedido: se conserva el token y se muestra su comprobante
            checkouts.put(token, idPedido);
            Controlador.mensaje(req, "ok", "Este pago ya fue procesado.");
            resp.sendRedirect(req.getContextPath() + "/pago?accion=comprobante&pedido=" + idPedido);
            return;
        }
        if (!(estado instanceof Pendiente pendiente)) {
            checkoutExpirado(req, resp);
            return;
        }

        Compra compra = validarCompra(req, resp, u);
        if (compra == null) {
            return;
        }
        if (!pendiente.codigo().equals(compra.producto().getCodigo()) || pendiente.cantidad() != compra.cantidad()) {
            // El formulario no corresponde al checkout que emitió el token
            checkoutExpirado(req, resp);
            return;
        }

        SolicitudPago solicitud = new SolicitudPago();
        solicitud.setTipoEnvio(parametro(req, "tipoEnvio", "DOMICILIO"));
        solicitud.setDepartamento(parametro(req, "departamento", "Lima"));
        solicitud.setDistrito(valor(req, "distrito"));
        solicitud.setDireccion(valor(req, "direccion"));
        solicitud.setReferencia(valor(req, "referencia"));
        solicitud.setTelefonoContacto(valor(req, "telefonoContacto"));
        solicitud.setCorreoComprobante(valor(req, "correoComprobante"));
        solicitud.setNotasPedido(valor(req, "notasPedido"));

        solicitud.setMetodo(parametro(req, "metodo", Pago.METODO_TARJETA));
        solicitud.setNumeroTarjeta(valor(req, "numeroTarjeta"));
        solicitud.setTitular(valor(req, "titular"));
        solicitud.setVencimiento(valor(req, "vencimiento"));
        solicitud.setCvv(valor(req, "cvv"));
        solicitud.setCelular(valor(req, "celular"));
        solicitud.setCodigoYape(valor(req, "codigoYape"));
        solicitud.setTipoComprobante(parametro(req, "tipoComprobante", Pago.BOLETA));
        solicitud.setDocumento(valor(req, "documento"));
        solicitud.setRazonSocial(valor(req, "razonSocial"));

        // El formulario que vuelve a la pantalla nunca incluye número de tarjeta, CVV ni código Yape.
        Map<String, String> form = new LinkedHashMap<>();
        form.put("tipoEnvio", solicitud.getTipoEnvio());
        form.put("departamento", solicitud.getDepartamento());
        form.put("distrito", solicitud.getDistrito());
        form.put("direccion", solicitud.getDireccion());
        form.put("referencia", solicitud.getReferencia());
        form.put("telefonoContacto", solicitud.getTelefonoContacto());
        form.put("correoComprobante", solicitud.getCorreoComprobante());
        form.put("notasPedido", solicitud.getNotasPedido());

        form.put("metodo", solicitud.getMetodo());
        form.put("titular", solicitud.getTitular());
        form.put("vencimiento", solicitud.getVencimiento());
        form.put("celular", solicitud.getCelular());
        form.put("tipoComprobante", solicitud.getTipoComprobante());
        form.put("documento", solicitud.getDocumento());
        form.put("razonSocial", solicitud.getRazonSocial());

        Map<String, String> erroresEnvio = validarEnvio(solicitud);
        if (!erroresEnvio.isEmpty()) {
            checkouts.put(token, pendiente);
            mostrarCheckout(req, resp, compra, form, erroresEnvio, null, token);
            return;
        }

        if (centimos(compra) != pendiente.totalCentimos()) {
            // El precio cambió desde que se mostró el total: se muestra el nuevo con un token nuevo
            mostrarCheckout(req, resp, compra, form, new HashMap<>(), MSG_PRECIO_CAMBIO, emitirToken(req, compra));
            return;
        }

        ResultadoCompra resultado;
        try {
            resultado = service.comprarConPago(u, compra.producto().getCodigo(), compra.cantidad(), solicitud);
        } catch (RuntimeException ex) {
            checkouts.put(token, pendiente);
            throw ex;
        }

        if (resultado.isExito()) {
            Pedido pedido = resultado.getPedido();
            checkouts.put(token, pedido.getIdPedido());
            if (!solicitud.getDocumento().isEmpty()) {
                req.getSession().setAttribute("clienteDni", solicitud.getDocumento());
            }
            if (!solicitud.getTelefonoContacto().isEmpty()) {
                req.getSession().setAttribute("clienteTelefono", solicitud.getTelefonoContacto());
            }
            Controlador.mensaje(req, "ok", "Pago aprobado. Pedido " + pedido.getCodigoPedido() + " registrado.");
            // Solo total y comprobante: nunca datos de la tarjeta
            String comprobante = pedido.getPago() == null ? "" : " · comprobante " + pedido.getPago().getNumeroComprobante();
            Controlador.auditar(req, u, RegistroAuditoria.COMPRA, "Pedido " + pedido.getCodigoPedido(),
                    pedido.getTotalTexto() + comprobante);
            // Patrón POST-Redirect-GET: evita reenviar el pago al recargar el comprobante.
            resp.sendRedirect(req.getContextPath() + "/pago?accion=comprobante&pedido=" + pedido.getIdPedido());
            return;
        }

        // Datos inválidos o pago rechazado: el mismo formulario puede reintentarse con el mismo token
        checkouts.put(token, pendiente);
        if (!resultado.getErrores().isEmpty()) {
            mostrarCheckout(req, resp, compra, form, resultado.getErrores(), null, token);
        } else {
            mostrarCheckout(req, resp, compra, form, new HashMap<>(), resultado.getError(), token);
        }
    }

    /**
     * Valida los datos de logística y entrega para la compra.
     */
    private Map<String, String> validarEnvio(SolicitudPago s) {
        Map<String, String> errores = new LinkedHashMap<>();
        boolean esRetiro = "RETIRO".equalsIgnoreCase(s.getTipoEnvio());
        if (!esRetiro) {
            if (s.getDireccion() == null || s.getDireccion().trim().length() < 5) {
                errores.put("direccion", "Ingresa la dirección exacta de entrega (calle, número o dpto).");
            }
            if (s.getDistrito() == null || s.getDistrito().trim().isEmpty()) {
                errores.put("distrito", "Ingresa el distrito o localidad de destino.");
            }
        }
        // Longitudes de las columnas de dbo.entrega
        if (largo(s.getDireccion()) > 200) {
            errores.put("direccion", "La dirección admite como máximo 200 caracteres.");
        }
        if (largo(s.getDistrito()) > 80) {
            errores.put("distrito", "El distrito admite como máximo 80 caracteres.");
        }
        if (largo(s.getReferencia()) > 200) {
            errores.put("referencia", "La referencia admite como máximo 200 caracteres.");
        }
        if (largo(s.getNotasPedido()) > 300) {
            errores.put("notasPedido", "Las notas admiten como máximo 300 caracteres.");
        }
        String tel = s.getTelefonoContacto() != null ? s.getTelefonoContacto().replaceAll("\\D", "") : "";
        if (tel.isEmpty()) {
            errores.put("telefonoContacto", "Ingresa un celular de contacto para la entrega.");
        } else if (!tel.matches("9\\d{8}") || largo(s.getTelefonoContacto()) > 20) {
            errores.put("telefonoContacto", "El celular de contacto debe tener 9 dígitos y empezar con 9.");
        }
        String correo = s.getCorreoComprobante() != null ? s.getCorreoComprobante().trim() : "";
        if (correo.length() > 100) {
            errores.put("correoComprobante", "El correo admite como máximo 100 caracteres.");
        } else if (!correo.isEmpty() && !correo.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            errores.put("correoComprobante", "Ingresa un correo electrónico válido para el comprobante y tracking.");
        }
        return errores;
    }

    // ------------------------------------------------------------------ acciones

    private void comprobante(HttpServletRequest req, HttpServletResponse resp, Usuario u)
            throws ServletException, IOException {
        Pedido pedido = null;
        try {
            int id = Integer.parseInt(parametro(req, "pedido", ""));
            pedido = service.buscarPedido(id, u);
        } catch (NumberFormatException ex) {
            // id inválido: se trata igual que un pedido inexistente
        } catch (IllegalStateException ex) {
            Controlador.mensaje(req, "error", "No se pudo cargar el comprobante. Intenta nuevamente en unos momentos.");
            resp.sendRedirect(req.getContextPath() + "/productos?accion=pedidos");
            return;
        }
        if (pedido == null) {
            Controlador.mensaje(req, "error", "No se encontró el comprobante solicitado.");
            resp.sendRedirect(req.getContextPath() + "/productos?accion=pedidos");
            return;
        }
        sinCache(resp);
        req.setAttribute("pedido", pedido);
        Controlador.vista(req, resp, "pago/comprobante");
    }

    /** Token vencido, ya consumido por otro envío o ajeno a este formulario: se vuelve al historial. */
    private void checkoutExpirado(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Controlador.mensaje(req, "error", MSG_CHECKOUT_EXPIRADO);
        resp.sendRedirect(req.getContextPath() + "/productos?accion=pedidos");
    }

    /** Solo las cuentas de cliente compran; si no, deja un mensaje, redirige y devuelve false. */
    private boolean esCliente(HttpServletRequest req, HttpServletResponse resp, Usuario u) throws IOException {
        if (u.isPuedeComprar()) {
            return true;
        }
        Controlador.mensaje(req, "error", MSG_SOLO_CLIENTES);
        resp.sendRedirect(req.getContextPath() + "/productos?accion=listar");
        return false;
    }

    /**
     * Verifica permiso, producto y cantidad. Si algo falla, deja un mensaje, redirige y devuelve null.
     */
    private Compra validarCompra(HttpServletRequest req, HttpServletResponse resp, Usuario u) throws IOException {
        String ctx = req.getContextPath();
        if (!esCliente(req, resp, u)) {
            return null;
        }
        String codigo = ProductoService.normalizarCodigo(req.getParameter("codigo"));
        String volver = ctx + "/productos?accion=comprar&codigo=" + URLEncoder.encode(codigo, StandardCharsets.UTF_8);

        Producto p = service.buscarPorCodigo(codigo);
        if (p == null) {
            Controlador.mensaje(req, "error", codigo.isEmpty()
                    ? "Elige un producto del catálogo para comprar."
                    : "No existe un producto con el código " + codigo + ".");
            resp.sendRedirect(volver);
            return null;
        }
        if (!p.isDisponible() || p.getStock() <= 0) {
            Controlador.mensaje(req, "error", "El producto " + p.getNombre() + " se encuentra agotado actualmente.");
            resp.sendRedirect(volver);
            return null;
        }
        int cantidad = leerCantidad(req.getParameter("cantidad"));
        if (cantidad < 1 || cantidad > p.getStock()) {
            Controlador.mensaje(req, "error", "La cantidad debe estar entre 1 y " + p.getStock() + " unidades.");
            resp.sendRedirect(volver);
            return null;
        }
        return new Compra(p, cantidad);
    }

    private void mostrarCheckout(HttpServletRequest req, HttpServletResponse resp, Compra compra,
            Map<String, String> form, Map<String, String> errores, String errorPago, String tokenCheckout)
            throws ServletException, IOException {
        double total = Pago.redondear(compra.producto().getPrecio() * compra.cantidad());

        req.setAttribute("producto", compra.producto());
        req.setAttribute("cantidad", compra.cantidad());
        req.setAttribute("subtotalTexto", Formato.soles(Pago.subtotalDe(total)));
        req.setAttribute("igvTexto", Formato.soles(Pago.igvDe(total)));
        req.setAttribute("totalTexto", Formato.soles(total));
        req.setAttribute("form", form);
        req.setAttribute("errores", errores);
        req.setAttribute("errorPago", errorPago);
        req.setAttribute("tokenCheckout", tokenCheckout);
        req.setAttribute("tarjetasPrueba", PasarelaPagoService.TARJETAS_PRUEBA);
        req.setAttribute("codigoYapePrueba", PasarelaPagoService.CODIGO_YAPE_PRUEBA);
        sinCache(resp);
        Controlador.vista(req, resp, "pago/checkout");
    }

    // ------------------------------------------------------------------ token de checkout

    /** Total de la compra en céntimos al precio actual del catálogo. */
    private static long centimos(Compra compra) {
        return Math.round(compra.producto().getPrecio() * compra.cantidad() * 100);
    }

    /** Genera un token de un solo uso y guarda en la sesión el checkout que autoriza. */
    private static String emitirToken(HttpServletRequest req, Compra compra) {
        String token = SesionFilter.nuevoToken();
        checkouts(req.getSession()).put(token,
                new Pendiente(compra.producto().getCodigo(), compra.cantidad(), centimos(compra)));
        return token;
    }

    /** Mapa de checkouts de la sesión (sincronizado: {@code remove} es atómico); se crea si no existe. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> checkouts(HttpSession sesion) {
        synchronized (CANDADO_CHECKOUTS) {
            if (sesion.getAttribute(ATRIBUTO_CHECKOUTS) instanceof Map<?, ?> existente) {
                return (Map<String, Object>) existente;
            }
            Map<String, Object> nuevo = Collections.synchronizedMap(new CheckoutsRecientes());
            sesion.setAttribute(ATRIBUTO_CHECKOUTS, nuevo);
            return nuevo;
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private Usuario usuarioSesion(HttpServletRequest req) {
        HttpSession sesion = req.getSession(false);
        return sesion != null && sesion.getAttribute("usuarioObj") instanceof Usuario u ? u : null;
    }

    /** Checkout y comprobante llevan datos del pedido: el navegador no debe guardarlos en caché. */
    private static void sinCache(HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Pragma", "no-cache");
    }

    /** Nombre completo del cliente, recortado, como titular propuesto. */
    private static String titularPorDefecto(Usuario u) {
        return u.getNombreCompleto() == null ? "" : u.getNombreCompleto().trim();
    }

    private static int largo(String texto) {
        return texto == null ? 0 : texto.trim().length();
    }

    private static int leerCantidad(String texto) {
        try {
            return Integer.parseInt(texto == null ? "" : texto.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String valor(HttpServletRequest req, String nombre) {
        String v = req.getParameter(nombre);
        return v == null ? "" : v.trim();
    }

    private static String parametro(HttpServletRequest req, String nombre, String porDefecto) {
        String v = req.getParameter(nombre);
        return v == null || v.isBlank() ? porDefecto : v.trim();
    }
}
