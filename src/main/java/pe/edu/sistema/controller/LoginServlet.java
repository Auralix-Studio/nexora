package pe.edu.sistema.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import pe.edu.sistema.model.RegistroAuditoria;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.service.UsuarioService;
import pe.edu.sistema.util.ConexionBD;

/**
 * Controlador de inicio y cierre de sesión del sistema NEXORA (Ventana de Ingreso · Semana 06).
 */
@WebServlet(name = "LoginServlet", urlPatterns = {"/login", "/logout"})
public class LoginServlet extends HttpServlet {

    private final UsuarioService usuarios = new UsuarioService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if ("/logout".equals(req.getServletPath())) {
            HttpSession sesion = req.getSession(false);
            if (sesion != null) {
                sesion.invalidate();
            }
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        HttpSession sesion = req.getSession(false);
        if (sesion != null && sesion.getAttribute("usuario") != null) {
            resp.sendRedirect(req.getContextPath() + "/inicio");
            return;
        }
        if ("1".equals(req.getParameter("probarBD"))) {
            ConexionBD.probarConexion();
        }
        req.setAttribute("bdConectada", ConexionBD.estaDisponible());
        req.setAttribute("bdEstado", ConexionBD.getEstadoTexto());
        Controlador.vista(req, resp, "login");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String accion = req.getParameter("accion");
        if ("registrarCliente".equals(accion)) {
            registrarNuevoCliente(req, resp);
            return;
        }

        String usuario = req.getParameter("usuario");
        String clave = req.getParameter("clave");
        // Nombre escrito tal como se anota en la auditoría, que lo recorta (puede ser basura: nunca se interpreta)
        String escrito = usuario == null || usuario.isBlank() ? "(vacío)" : usuario.trim();

        // Marca de revocaciones tomada ANTES de autenticar: si el administrador cambia la cuenta durante
        // el ingreso, la sesión nueva ya nace revocada
        long marca = UsuarioService.marcaActual();

        // Un intento contra una cuenta ya bloqueada no se audita: el bloqueo se anotó una sola vez al crearse
        if (usuario != null && usuarios.estaBloqueado(usuario)) {
            mostrarLoginConError(req, resp, usuario, mensajeBloqueo(usuarios.minutosRestantesBloqueo(usuario)));
            return;
        }

        UsuarioService.ResultadoAcceso acceso = usuarios.intentarAcceso(usuario, clave, null, req.getRemoteAddr());
        Usuario u = acceso.usuario();

        if (u == null) {
            Controlador.auditar(req, escrito, null, RegistroAuditoria.ACCESO_FALLIDO, "Usuario " + escrito,
                    "Usuario o contraseña incorrectos");
            if (acceso.bloqueoNuevo()) {
                long minutos = usuarios.minutosRestantesBloqueo(usuario);
                Controlador.auditar(req, escrito, null, RegistroAuditoria.CUENTA_BLOQUEADA, "Usuario " + escrito,
                        "Cuenta bloqueada por " + UsuarioService.MAX_INTENTOS + " intentos fallidos seguidos; faltan "
                        + minutos + " min");
                mostrarLoginConError(req, resp, usuario, mensajeBloqueo(minutos));
                return;
            }
            mostrarLoginConError(req, resp, usuario, "Usuario o contraseña incorrectos.");
            return;
        }

        String saludo;
        if (u.isCliente()) {
            saludo = "¡Hola, " + u.getNombreCompleto() + "! Bienvenido a la tienda NEXORA.";
        } else if (u.isAdministrador()) {
            saludo = "Bienvenido al panel de administración general, " + u.getNombreCompleto() + ".";
        } else if (u.isAlmacen()) {
            saludo = "Bienvenido a la gestión de inventario y almacén, " + u.getNombreCompleto() + ".";
        } else {
            saludo = "Bienvenido a NEXORA, " + u.getNombreCompleto() + " · Modo consulta.";
        }
        iniciarSesionUsuario(req, resp, u, saludo, marca);
    }

    private static String mensajeBloqueo(long minutos) {
        return "Cuenta bloqueada temporalmente por varios intentos fallidos. Intenta de nuevo en "
                + minutos + (minutos == 1 ? " minuto." : " minutos.");
    }

    /** Vuelve al formulario de ingreso con un mensaje de error y el usuario escrito. */
    private void mostrarLoginConError(HttpServletRequest req, HttpServletResponse resp, String usuario, String error)
            throws ServletException, IOException {
        req.setAttribute("error", error);
        req.setAttribute("usuarioIngresado", usuario);
        req.setAttribute("bdConectada", ConexionBD.estaDisponible());
        req.setAttribute("bdEstado", ConexionBD.getEstadoTexto());
        Controlador.vista(req, resp, "login");
    }

    private void registrarNuevoCliente(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String nombreCompleto = req.getParameter("nombreCompleto") != null ? req.getParameter("nombreCompleto").trim() : "";
        String dni = req.getParameter("dni") != null ? req.getParameter("dni").replaceAll("\\D", "") : "";
        String correo = req.getParameter("correo") != null ? req.getParameter("correo").trim() : "";
        String telefono = req.getParameter("telefono") != null ? req.getParameter("telefono").replaceAll("\\D", "") : "";
        String nuevoUsuario = req.getParameter("nuevoUsuario") != null ? req.getParameter("nuevoUsuario").trim() : "";
        String nuevaClave = req.getParameter("nuevaClave") != null ? req.getParameter("nuevaClave") : "";
        String confirmarClave = req.getParameter("confirmarClave") != null ? req.getParameter("confirmarClave") : "";

        String error = null;
        if (nombreCompleto.isEmpty() || nombreCompleto.length() < 3) {
            error = "Ingrese sus nombres y apellidos completos (al menos 3 caracteres).";
        } else if (!dni.matches("\\d{8}")) {
            error = "El DNI debe contener exactamente 8 dígitos numéricos.";
        } else if (correo.isEmpty() || !correo.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            error = "Ingrese un correo electrónico válido (ejemplo: cliente@nexora.pe).";
        } else if (!telefono.matches("9\\d{8}")) {
            error = "El celular debe contener 9 dígitos y empezar con 9 (ejemplo: 987654321).";
        } else if (nuevoUsuario.length() < 3) {
            error = "El nombre de usuario debe tener al menos 3 caracteres.";
        } else if (!nuevaClave.equals(confirmarClave)) {
            error = "Las contraseñas ingresadas no coinciden. Por favor, verifíquelas.";
        } else {
            error = usuarios.registrarCliente(nuevoUsuario, nuevaClave, nombreCompleto, correo);
        }

        if (error != null && !error.isEmpty()) {
            req.setAttribute("error", error);
            req.setAttribute("mostrarRegistroCliente", true);
            req.setAttribute("regNombre", nombreCompleto);
            req.setAttribute("regDni", dni);
            req.setAttribute("regCorreo", correo);
            req.setAttribute("regTelefono", telefono);
            req.setAttribute("regUsuario", nuevoUsuario);
            req.setAttribute("bdConectada", ConexionBD.estaDisponible());
            req.setAttribute("bdEstado", ConexionBD.getEstadoTexto());
            Controlador.vista(req, resp, "login");
            return;
        }
        long marca = UsuarioService.marcaActual();
        Usuario u = usuarios.autenticarUsuario(nuevoUsuario, nuevaClave, Usuario.ROL_CLIENTE, req.getRemoteAddr());
        if (u == null) {
            // La cuenta quedó creada pero no se pudo verificar al instante: se pide ingresar con los datos
            req.setAttribute("error", "La cuenta se creó, pero no se pudo iniciar sesión. Intenta ingresar con tus datos.");
            req.setAttribute("usuarioIngresado", nuevoUsuario);
            req.setAttribute("bdConectada", ConexionBD.estaDisponible());
            req.setAttribute("bdEstado", ConexionBD.getEstadoTexto());
            Controlador.vista(req, resp, "login");
            return;
        }
        iniciarSesionUsuario(req, resp, u,
                "Cuenta de Cliente creada correctamente. ¡Bienvenido a NEXORA, " + u.getNombreCompleto() + "!",
                marca, dni, telefono);
    }

    private void iniciarSesionUsuario(HttpServletRequest req, HttpServletResponse resp, Usuario u, String mensajeBienvenida,
                                      long marca)
            throws IOException {
        iniciarSesionUsuario(req, resp, u, mensajeBienvenida, marca, null, null);
    }

    private void iniciarSesionUsuario(HttpServletRequest req, HttpServletResponse resp, Usuario u, String mensajeBienvenida,
                                      long marca, String dni, String telefono)
            throws IOException {
        // Preservar urlRetorno (ej. comprar producto) antes de invalidar la sesión
        HttpSession anterior = req.getSession(false);
        String urlRetorno = null;
        if (anterior != null) {
            Object ret = anterior.getAttribute("urlRetorno");
            if (ret instanceof String s && !s.isBlank()) {
                urlRetorno = s;
            }
            anterior.invalidate();
        }
        HttpSession sesion = req.getSession(true);
        sesion.setAttribute("usuario", u.getUsername());
        sesion.setAttribute("nombreCompleto", u.getNombreCompleto());
        sesion.setAttribute("rol", u.getRol());
        sesion.setAttribute("usuarioObj", u);
        sesion.setAttribute("marcaSesion", marca);
        sesion.setAttribute("modoBD", ConexionBD.getEstadoTexto());
        if (dni != null && !dni.isBlank()) {
            sesion.setAttribute("clienteDni", dni);
        }
        if (telefono != null && !telefono.isBlank()) {
            sesion.setAttribute("clienteTelefono", telefono);
        }
        Controlador.mensaje(req, "ok", mensajeBienvenida);
        Controlador.auditar(req, u, RegistroAuditoria.INICIO_SESION, "Usuario " + u.getUsername(), "Ingreso correcto");
        if (esUrlRetornoSegura(urlRetorno, req.getContextPath())) {
            resp.sendRedirect(urlRetorno);
            return;
        }
        // El personal trabaja desde el panel de administración; el cliente, desde la tienda
        resp.sendRedirect(req.getContextPath() + (u.isPersonal() ? "/admin" : "/inicio"));
    }

    /**
     * Evita redirecciones abiertas: la URL de retorno debe ser una ruta de esta misma aplicación
     * (empieza con el contexto), sin "//" después del contexto ni saltos de línea.
     */
    private static boolean esUrlRetornoSegura(String url, String contextPath) {
        if (url == null || url.isBlank() || !url.startsWith(contextPath + "/")) {
            return false;
        }
        if (url.indexOf('\r') >= 0 || url.indexOf('\n') >= 0) {
            return false;
        }
        return !url.substring(contextPath.length()).contains("//");
    }
}
