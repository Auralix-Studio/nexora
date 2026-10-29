package pe.edu.sistema.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Peticiones y respuestas HTTP falsas (java.lang.reflect.Proxy) para probar servlets y filtros sin contenedor.
 * Registran lo que el código bajo prueba hace con ellas.
 */
final class ServletFalsos {

    private ServletFalsos() {
    }

    /** Valor neutro según el tipo de retorno de un método no simulado. */
    private static Object neutro(Method m) {
        Class<?> tipo = m.getReturnType();
        if (tipo == boolean.class) {
            return false;
        }
        if (tipo == int.class) {
            return 0;
        }
        if (tipo == long.class) {
            return 0L;
        }
        return null;
    }

    /** Petición falsa: devuelve método, ruta y parámetros indicados y anota atributos y llamadas a getSession. */
    static final class Peticion {
        final Map<String, Object> atributos = new HashMap<>();
        final Map<String, String> parametros = new HashMap<>();
        /** Llamadas relevantes en orden, p. ej. "getSession(true)". */
        final List<String> llamadas = new ArrayList<>();
        final HttpServletRequest proxy;

        Peticion(String metodo, String servletPath, String pathInfo, boolean seguro) {
            InvocationHandler h = (p, m, a) -> switch (m.getName()) {
                case "getMethod" -> metodo;
                case "getServletPath" -> servletPath;
                case "getPathInfo" -> pathInfo;
                case "getRequestURI" -> "/Semana04" + servletPath + (pathInfo == null ? "" : pathInfo);
                case "getContextPath" -> "/Semana04";
                case "isSecure" -> seguro;
                case "getParameter" -> parametros.get((String) a[0]);
                case "setAttribute" -> {
                    atributos.put((String) a[0], a[1]);
                    yield null;
                }
                case "getAttribute" -> atributos.get((String) a[0]);
                case "getSession" -> {
                    llamadas.add("getSession(" + (a == null ? "" : a[0]) + ")");
                    yield null;
                }
                default -> neutro(m);
            };
            this.proxy = (HttpServletRequest) Proxy.newProxyInstance(
                    ServletFalsos.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class}, h);
        }

        static Peticion get(String servletPath) {
            return new Peticion("GET", servletPath, null, false);
        }

        boolean creoSesion() {
            return llamadas.contains("getSession(true)") || llamadas.contains("getSession()");
        }
    }

    /** Respuesta falsa: anota estado, cabeceras (sin distinguir mayúsculas), cuerpo y errores enviados. */
    static final class Respuesta {
        int estado = 200;
        int errorEnviado = -1;
        String tipoContenido;
        String redireccion;
        boolean comprometida;
        final Map<String, String> cabeceras = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final StringWriter cuerpo = new StringWriter();
        final HttpServletResponse proxy;

        Respuesta() {
            InvocationHandler h = (p, m, a) -> switch (m.getName()) {
                case "setStatus" -> {
                    estado = (Integer) a[0];
                    yield null;
                }
                case "sendError" -> {
                    errorEnviado = (Integer) a[0];
                    yield null;
                }
                case "sendRedirect" -> {
                    redireccion = (String) a[0];
                    yield null;
                }
                case "setHeader", "addHeader" -> {
                    cabeceras.put((String) a[0], (String) a[1]);
                    yield null;
                }
                case "setContentType" -> {
                    tipoContenido = (String) a[0];
                    yield null;
                }
                case "getWriter" -> new PrintWriter(cuerpo, true);
                case "isCommitted" -> comprometida;
                default -> neutro(m);
            };
            this.proxy = (HttpServletResponse) Proxy.newProxyInstance(
                    ServletFalsos.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class}, h);
        }

        String cabecera(String nombre) {
            return cabeceras.get(nombre);
        }

        String texto() {
            return cuerpo.toString();
        }
    }
}
