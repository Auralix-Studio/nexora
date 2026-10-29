package pe.edu.sistema.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.WebListener;
import java.util.logging.Logger;
import pe.edu.sistema.util.ConexionBD;
import pe.edu.sistema.util.ConfigApp;

/**
 * Arranque y cierre de la aplicación: endurece la cookie de sesión (HttpOnly siempre; Secure solo en producción
 * para no romper http://localhost ni los scripts con curl), anota en el registro una línea con entorno, versión y
 * estado de la configuración de la base (nunca secretos) y cierra el pool de conexiones al detenerse.
 */
@WebListener
public class ArranqueListener implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(ArranqueListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent evento) {
        ServletContext contexto = evento.getServletContext();
        SessionCookieConfig cookie = contexto.getSessionCookieConfig();
        cookie.setHttpOnly(true);
        if (ConfigApp.cookieSegura()) {
            cookie.setSecure(true);
        }
        LOG.info("NEXORA iniciada: entorno=" + ConfigApp.entorno() + ", versión=" + ConfigApp.version()
                + ", demo=" + ConfigApp.esDemo() + ", base de datos=" + ConexionBD.resumenConfiguracion());
    }

    @Override
    public void contextDestroyed(ServletContextEvent evento) {
        ConexionBD.cerrar();
    }
}
