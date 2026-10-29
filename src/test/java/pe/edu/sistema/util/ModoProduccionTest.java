package pe.edu.sistema.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import pe.edu.sistema.model.Producto;
import pe.edu.sistema.model.SolicitudPago;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.service.AuditoriaService;
import pe.edu.sistema.service.ProductoService;
import pe.edu.sistema.service.UsuarioService;

/**
 * Modo producción: sin respaldo en memoria. Con la base sin configurar, o configurada pero inaccesible (puerto
 * cerrado), los servicios lanzan {@link IllegalStateException} con el mensaje estándar en lugar de usar o modificar
 * las colecciones en memoria. Cada prueba restaura las propiedades del sistema y el estado de {@link ConexionBD}.
 */
class ModoProduccionTest {

    private static final String SIN_DATOS = "El servicio de datos no está disponible en este momento.";

    private final ProductoService productos = ProductoService.getInstancia();
    private final UsuarioService usuarios = new UsuarioService();
    private final AuditoriaService auditoria = AuditoriaService.getInstancia();

    private Map<String, String> guardadas;

    @BeforeEach
    void iniciar() {
        guardadas = PropiedadesSistema.guardar();
        PropiedadesSistema.limpiarConfiguracion();
        ConexionBD.reiniciarParaPruebas();
    }

    @AfterEach
    void restaurar() {
        ConexionBD.reiniciarParaPruebas();
        PropiedadesSistema.restaurar(guardadas);
        ConexionBD.reiniciarParaPruebas();
    }

    // ---------------------------------------------------------------- escenarios

    /** Producción con DB_URL pero sin usuario ni clave: la base no está configurada. */
    private static void produccionSinBase() {
        assumeTrue(System.getenv("DB_USER") == null && System.getenv("DB_PASSWORD") == null,
                "el entorno de la prueba define DB_USER o DB_PASSWORD");
        System.setProperty(ConfigApp.ENTORNO, "produccion");
    }

    /** Producción con la base configurada pero en un puerto cerrado; espera mínima por conexión. */
    private static void produccionConBaseCaida() {
        System.setProperty(ConfigApp.ENTORNO, "produccion");
        System.setProperty("DB_USER", "usuario_de_prueba");
        System.setProperty("DB_PASSWORD", "clave-de-prueba-sin-valor");
        System.setProperty("DB_CONNECTION_TIMEOUT_MS", "300");
    }

    private static Usuario admin() {
        return new Usuario(1, "admin", "Admin", "admin@nexora.pe", Usuario.ROL_ADMIN, true);
    }

    private static Usuario cliente() {
        return new Usuario(3, "cliente", "Andrea Paredes", "cliente@nexora.pe", Usuario.ROL_CLIENTE, true);
    }

    private static void sinDatos(Executable operacion) {
        IllegalStateException ex = assertThrows(IllegalStateException.class, operacion);
        assertEquals(SIN_DATOS, ex.getMessage());
    }

    private static Producto producto(String codigo) {
        return new Producto(codigo, "Producto de prueba", "Redes", 10.0, 3);
    }

    // ---------------------------------------------------------------- configuración

    @Test
    @DisplayName("Producción sin DB_USER ni DB_PASSWORD: no hay base configurada y se informa solo de los nombres")
    void sinConfiguracionLaBaseNoEstaDisponible() {
        produccionSinBase();
        assertFalse(ConexionBD.estaConfigurada());
        assertFalse(ConexionBD.estaDisponible());
        assertEquals(List.of("DB_USER", "DB_PASSWORD"), ConexionBD.variablesFaltantes());
        assertTrue(ConexionBD.resumenConfiguracion().contains("DB_USER"));
        assertFalse(ConexionBD.resumenConfiguracion().contains("TuPasswordSQL123"));
        assertFalse(ConexionBD.probarConexion());
        assertThrows(SQLException.class, ConexionBD::obtenerConexion);
        sinDatos(ConexionBD::usarSql);
    }

    @Test
    @DisplayName("En producción no hay valores por defecto: sin variables no se usa sa ni la clave de desarrollo")
    void produccionNoUsaValoresPorDefecto() {
        produccionSinBase();
        System.clearProperty("DB_URL");
        assumeTrue(System.getenv("DB_URL") == null, "el entorno de la prueba define DB_URL");
        assertEquals(List.of("DB_URL", "DB_USER", "DB_PASSWORD"), ConexionBD.variablesFaltantes());
        assertFalse(ConexionBD.estaConfigurada());
    }

    @Test
    @DisplayName("En desarrollo la base siempre cuenta como configurada (valores por defecto)")
    void desarrolloUsaValoresPorDefecto() {
        assumeTrue(System.getenv(ConfigApp.ENTORNO) == null, "el entorno de la prueba define NEXORA_ENTORNO");
        assertTrue(ConexionBD.estaConfigurada());
        assertTrue(ConexionBD.variablesFaltantes().size() <= 3);
    }

    // ---------------------------------------------------------------- sin respaldo en memoria

    @Test
    @DisplayName("Sin base configurada, todos los servicios lanzan el mensaje estándar")
    void sinConfiguracionTodosLosServiciosLanzan() {
        produccionSinBase();
        Usuario admin = admin();
        Usuario cliente = cliente();

        // ProductoService
        sinDatos(productos::listar);
        sinDatos(productos::contar);
        sinDatos(() -> productos.buscarPorCodigo("P001"));
        sinDatos(() -> productos.existeCodigo("P001"));
        sinDatos(() -> productos.buscar("nombre", "mouse"));
        sinDatos(() -> productos.registrar(producto("PZ01")));
        sinDatos(() -> productos.actualizar(producto("P001")));
        sinDatos(() -> productos.eliminar("P001"));
        sinDatos(() -> productos.comprarConPago(cliente, "P001", 1, new SolicitudPago()));
        sinDatos(() -> productos.buscarPedido(1, admin));
        sinDatos(() -> productos.listarPedidos(admin));
        sinDatos(() -> productos.filtrarPedidos(admin, null, null, null, null));
        sinDatos(() -> productos.cambiarEstadoPedido(admin, 1, "En preparación"));
        sinDatos(() -> productos.anularPedido(admin, 1, "prueba"));
        sinDatos(() -> productos.ajustarStock(admin, "P001", "Entrada", 5, "prueba"));
        sinDatos(() -> productos.listarMovimientos(null, 10));
        sinDatos(() -> productos.listarCategorias(false));
        sinDatos(() -> productos.crearCategoria(admin, "Nueva categoría", "prueba"));
        sinDatos(() -> productos.editarCategoria(admin, 1, "Computadoras", "prueba"));
        sinDatos(() -> productos.cambiarEstadoCategoria(admin, 1, false));
        sinDatos(() -> productos.eliminarCategoria(admin, 99));
        sinDatos(productos::valorTotal);
        // La cabecera de las páginas no se cae aunque no haya datos
        assertDoesNotThrow(productos::contarParaCabecera);
        assertTrue(assertDoesNotThrow(productos::nombresCategorias).isEmpty());

        // UsuarioService
        sinDatos(() -> usuarios.autenticarUsuario("prod_login", "Clave1234", null, "127.0.0.1"));
        sinDatos(usuarios::listarUsuarios);
        sinDatos(() -> usuarios.buscarPorId(3));
        sinDatos(() -> usuarios.crearUsuario(admin, "prod_nuevo", "Prod Nuevo", "prod_nuevo@x.pe", Usuario.ROL_CONSULTA, "Clave1234"));
        sinDatos(() -> usuarios.cambiarEstadoUsuario(admin, 99, false));
        sinDatos(() -> usuarios.cambiarRol(admin, 99, Usuario.ROL_CONSULTA));
        sinDatos(() -> usuarios.restablecerClave(admin, 99, "Clave1234"));
        sinDatos(() -> usuarios.registrarCliente("prod_cliente", "Clave1234", "Prod Cliente", "prod_cliente@x.pe", "12345678", "912345678"));
        sinDatos(() -> usuarios.registrarCliente("Prod Cliente", "prod_cliente2", "Clave1234"));

        // AuditoriaService: registrar nunca lanza; listar sí
        assertDoesNotThrow(() -> auditoria.registrar("prod_sin_base", "Administrador", "PRUEBA", "Entidad", "detalle", "127.0.0.1"));
        sinDatos(() -> auditoria.listar(null, null, null, null, 10));
    }

    @Test
    @DisplayName("Con la base configurada pero caída, las operaciones de datos lanzan en vez de usar la memoria")
    void baseCaidaLanzaYNoTocaLaMemoria() {
        produccionConBaseCaida();
        assertTrue(ConexionBD.estaConfigurada());
        assertTrue(ConexionBD.estaDisponible(), "en producción 'disponible' significa configurada: no se sondea");
        Usuario admin = admin();

        sinDatos(productos::listar);
        sinDatos(() -> productos.buscarPorCodigo("P001"));
        sinDatos(() -> productos.registrar(producto("PZ02")));
        sinDatos(() -> productos.comprarConPago(cliente(), "P001", 1, new SolicitudPago()));
        sinDatos(() -> productos.cambiarEstadoPedido(admin, 1, "En preparación"));
        sinDatos(() -> productos.crearCategoria(admin, "Categoría fantasma", "prueba"));
        sinDatos(() -> productos.listarPedidos(admin));
        sinDatos(() -> usuarios.autenticarUsuario("prod_login", "Clave1234", null, "127.0.0.1"));
        sinDatos(() -> usuarios.listarUsuarios());
        sinDatos(() -> usuarios.crearUsuario(admin, "prod_caida", "Prod Caída", "prod_caida@x.pe", Usuario.ROL_CONSULTA, "Clave1234"));
        sinDatos(() -> usuarios.registrarCliente("prod_caida2", "Clave1234", "Prod Caida", "prod_caida2@x.pe", null, null));
        assertDoesNotThrow(() -> auditoria.registrar("prod_base_caida", "Administrador", "PRUEBA", "Entidad", "detalle", "127.0.0.1"));
        sinDatos(() -> auditoria.listar(null, null, null, null, 10));
        assertFalse(ConexionBD.probarConexion());

        // De vuelta en desarrollo (base caída): nada de lo anterior quedó en la memoria
        ConexionBD.reiniciarParaPruebas();
        PropiedadesSistema.limpiarConfiguracion();
        assumeFalse(ConexionBD.estaDisponible(), "hay una base activa en el puerto cerrado de la prueba");
        assertNull(productos.buscarPorCodigo("PZ02"), "el producto no debe existir en la memoria");
        assertNotNull(productos.buscarPorCodigo("P001"), "los datos semilla siguen intactos");
        assertTrue(productos.listarCategorias(false).stream().noneMatch(c -> c.getNombre().equals("Categoría fantasma")));
        assertNull(usuarios.autenticarUsuario("prod_caida2", "Clave1234", null, "127.0.0.1"),
                "la cuenta no debe haberse creado en la memoria");
        assertTrue(auditoria.listar("prod_base_caida", null, null, null, 10).isEmpty(),
                "la auditoría no debe haberse guardado en la memoria");
        assertTrue(auditoria.listar("prod_sin_base", null, null, null, 10).isEmpty());
    }

    // ---------------------------------------------------------------- manejo de fallos SQL

    @Test
    @DisplayName("alFallar: en producción lanza solo si el fallo es de conexión; alFallarSinRespaldo siempre")
    void fallosSqlEnProduccion() {
        produccionConBaseCaida();
        SQLException conexion = new SQLException("TCP/IP", "08S01");
        SQLException transitoria = new SQLTransientConnectionException("tiempo agotado");
        SQLException restriccion = new SQLException("duplicado", "23000", 2627);

        assertThrows(DatosNoDisponiblesException.class, () -> ConexionBD.alFallar(conexion));
        assertThrows(DatosNoDisponiblesException.class, () -> ConexionBD.alFallar(transitoria));
        assertDoesNotThrow(() -> ConexionBD.alFallar(restriccion));
        assertThrows(DatosNoDisponiblesException.class, () -> ConexionBD.alFallarSinRespaldo(restriccion));
    }

    @Test
    @DisplayName("alFallar y alFallarSinRespaldo no lanzan en desarrollo (activan el respaldo en memoria)")
    void fallosSqlEnDesarrollo() {
        assumeTrue(System.getenv(ConfigApp.ENTORNO) == null, "el entorno de la prueba define NEXORA_ENTORNO");
        SQLException conexion = new SQLException("TCP/IP", "08S01");
        assertDoesNotThrow(() -> ConexionBD.alFallar(conexion));
        assertDoesNotThrow(() -> ConexionBD.alFallarSinRespaldo(conexion));
        assertFalse(ConexionBD.estaDisponible());
    }

    @Test
    @DisplayName("esFalloDeConexion reconoce red, tiempo de espera, inicio de sesión y base pausada")
    void clasificaFallosDeConexion() {
        assertTrue(ConexionBD.esFalloDeConexion(new SQLTransientConnectionException("x")));
        assertTrue(ConexionBD.esFalloDeConexion(new SQLException("x", "08001")));
        assertTrue(ConexionBD.esFalloDeConexion(new SQLException("Login failed", "S0001", 18456)));
        assertTrue(ConexionBD.esFalloDeConexion(new SQLException("Base pausada", "S0001", 40613)));
        assertTrue(ConexionBD.esFalloDeConexion(new RuntimeException("envoltura", new SQLException("x", "08S01"))));
        assertFalse(ConexionBD.esFalloDeConexion(new SQLException("duplicado", "23000", 2627)));
        assertFalse(ConexionBD.esFalloDeConexion(new SQLException("llave foránea", "23000", 547)));
        assertFalse(ConexionBD.esFalloDeConexion(new IllegalStateException("otra cosa")));
        assertFalse(ConexionBD.esFalloDeConexion(null));
    }

    @Test
    @DisplayName("Los mensajes de estado de producción no sondean ni revelan valores")
    void estadoTextoSinSecretos() {
        produccionConBaseCaida();
        String texto = ConexionBD.getEstadoTexto();
        assertFalse(texto.contains("clave-de-prueba-sin-valor"));
        assertFalse(texto.contains("usuario_de_prueba"));
        assertEquals("configurada", ConexionBD.resumenConfiguracion());
    }
}
