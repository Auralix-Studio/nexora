package pe.edu.sistema.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.edu.sistema.model.Pago;
import pe.edu.sistema.model.Pedido;
import pe.edu.sistema.model.Producto;
import pe.edu.sistema.model.ResultadoCompra;
import pe.edu.sistema.model.SolicitudPago;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.util.ConexionBD;

/**
 * Datos de entrega del pedido, DNI y celular del cliente y conteo de la cabecera, con el respaldo en memoria.
 * En SQL las mismas reglas se guardan en dbo.entrega y en dbo.usuario (dni, telefono); aquí se verifica el
 * comportamiento observable que debe ser idéntico en los dos almacenes.
 */
class PersistenciaEntregaTest {

    private static final DateTimeFormatter MM_AA = DateTimeFormatter.ofPattern("MM/yy");

    private final ProductoService productos = ProductoService.getInstancia();
    private final UsuarioService usuarios = new UsuarioService();

    /** Evita escribir en una base real si alguien ejecuta las pruebas con SQL Server activo. */
    @BeforeAll
    static void soloRespaldoEnMemoria() {
        assumeFalse(ConexionBD.estaDisponible(),
                "Estas pruebas usan el respaldo en memoria; hay un SQL Server activo y no se modificará");
    }

    private static Usuario cliente() {
        return new Usuario(2100, "entrega_cli", "Cliente Entrega", "entrega_cli@prueba.pe", Usuario.ROL_CLIENTE, true);
    }

    private static Usuario personal() {
        return new Usuario(3100, "entrega_admin", "Personal", "", Usuario.ROL_ADMIN, true);
    }

    private static SolicitudPago solicitud() {
        SolicitudPago s = new SolicitudPago();
        s.setMetodo(Pago.METODO_TARJETA);
        s.setNumeroTarjeta("4111 1111 1111 1111");
        s.setTitular("Cliente Entrega");
        s.setVencimiento(YearMonth.now().plusYears(2).format(MM_AA));
        s.setCvv("123");
        s.setTipoComprobante(Pago.BOLETA);
        s.setDocumento("70451236");
        return s;
    }

    private Pedido comprar(SolicitudPago s) {
        ResultadoCompra r = productos.comprarConPago(cliente(), "P007", 1, s);
        assertTrue(r.isExito(), "La compra debía aprobarse: " + r.getError() + " " + r.getErrores());
        return r.getPedido();
    }

    // ---------------------------------------------------------------- entrega

    @Test
    @DisplayName("La entrega a domicilio se conserva al consultar el pedido y el historial")
    void entregaADomicilio() {
        SolicitudPago s = solicitud();
        s.setTipoEnvio("DOMICILIO");
        s.setDepartamento("Lima");
        s.setDistrito("Surco");
        s.setDireccion("Av. Primavera 123");
        s.setReferencia("Cerca del banco");
        s.setTelefonoContacto("987654321");
        s.setCorreoComprobante("entrega@prueba.pe");
        s.setNotasPedido("Tocar el timbre");
        Pedido comprado = comprar(s);

        Pedido leido = productos.buscarPedido(comprado.getIdPedido(), cliente());
        assertNotNull(leido);
        assertEquals("Envío a Domicilio Express", leido.getTipoEnvio());
        assertEquals("Lima", leido.getDepartamento());
        assertEquals("Surco", leido.getDistrito());
        assertEquals("Av. Primavera 123", leido.getDireccionEnvio());
        assertEquals("Cerca del banco", leido.getReferenciaEnvio());
        assertEquals("987654321", leido.getTelefonoContacto());
        assertEquals("entrega@prueba.pe", leido.getCorreoContacto());
        assertEquals("Tocar el timbre", leido.getNotasPedido());
        assertTrue(leido.getCodigoSeguimiento().startsWith("NEX-TRK-"));

        Pedido enLista = productos.listarPedidos(cliente()).stream()
                .filter(p -> p.getIdPedido() == comprado.getIdPedido()).findFirst().orElseThrow();
        assertEquals("Surco", enLista.getDistrito());
        assertEquals("Av. Primavera 123", enLista.getDireccionEnvio());
        assertEquals("Tocar el timbre", enLista.getNotasPedido());

        Pedido paraPersonal = productos.filtrarPedidos(personal(), null, "entrega_cli", null, null).stream()
                .filter(p -> p.getIdPedido() == comprado.getIdPedido()).findFirst().orElseThrow();
        assertEquals("987654321", paraPersonal.getTelefonoContacto());
    }

    @Test
    @DisplayName("El retiro en tienda guarda la dirección del almacén, no la escrita por el cliente")
    void retiroEnTienda() {
        SolicitudPago s = solicitud();
        s.setTipoEnvio("RETIRO");
        s.setDireccion("Esto no debe usarse");
        s.setTelefonoContacto("912345678");
        Pedido comprado = comprar(s);

        Pedido leido = productos.buscarPedido(comprado.getIdPedido(), cliente());
        assertNotNull(leido);
        assertTrue(leido.getTipoEnvio().startsWith("Retiro en Tienda"));
        assertTrue(leido.getDireccionEnvio().contains("San Isidro"));
        assertEquals("912345678", leido.getTelefonoContacto());
        assertEquals("", leido.getReferenciaEnvio());
        assertEquals("", leido.getCorreoContacto());
    }

    @Test
    @DisplayName("Los campos de entrega se recortan a la longitud de las columnas de dbo.entrega")
    void entregaSeRecortaALasColumnas() {
        SolicitudPago s = solicitud();
        s.setTipoEnvio("DOMICILIO");
        s.setDepartamento("D".repeat(80));
        s.setDistrito("S".repeat(120));
        s.setDireccion("A".repeat(300));
        s.setReferencia("R".repeat(300));
        s.setTelefonoContacto("9".repeat(40));
        s.setCorreoComprobante("c".repeat(150));
        s.setNotasPedido("N".repeat(500));
        Pedido leido = productos.buscarPedido(comprar(s).getIdPedido(), cliente());

        assertNotNull(leido);
        assertEquals(50, leido.getDepartamento().length());
        assertEquals(80, leido.getDistrito().length());
        assertEquals(200, leido.getDireccionEnvio().length());
        assertEquals(200, leido.getReferenciaEnvio().length());
        assertEquals(20, leido.getTelefonoContacto().length());
        assertEquals(100, leido.getCorreoContacto().length());
        assertEquals(300, leido.getNotasPedido().length());
    }

    @Test
    @DisplayName("Los pedidos de muestra traen la misma entrega que las semillas de sql/bd_nexora.sql")
    void pedidosSemillaConEntrega() {
        Pedido semilla = productos.listarPedidos(personal()).stream()
                .filter(p -> p.getIdPedido() == 1).findFirst().orElseThrow();
        assertEquals("Envío a Domicilio Express", semilla.getTipoEnvio());
        assertEquals("Lima", semilla.getDepartamento());
        assertEquals("Miraflores", semilla.getDistrito());
        assertEquals("Av. Larco 345, Miraflores", semilla.getDireccionEnvio());
        assertEquals("Frente al parque Kennedy", semilla.getReferenciaEnvio());
        assertEquals("987654321", semilla.getTelefonoContacto());
        assertEquals("cliente@nexora.pe", semilla.getCorreoContacto());
        assertTrue(semilla.getCodigoSeguimiento().startsWith("NEX-TRK-"));
    }

    // ---------------------------------------------------------------- DNI y celular del cliente

    @Test
    @DisplayName("registrarCliente con DNI y celular los guarda en la cuenta")
    void registroGuardaDniYCelular() {
        assertEquals("", usuarios.registrarCliente("dni_cliente_1", "Clave1234", "Cliente Con Dni",
                "dni_cliente_1@prueba.pe", "12345678", "912345678"));

        Usuario u = usuarios.autenticarUsuario("dni_cliente_1", "Clave1234", Usuario.ROL_CLIENTE, "127.0.0.1");
        assertNotNull(u);
        assertEquals("12345678", u.getDni());
        assertEquals("912345678", u.getTelefono());
        assertEquals("12345678", usuarios.buscarPorId(u.getIdUsuario()).getDni());
        assertEquals("912345678", usuarios.listarUsuarios().stream()
                .filter(x -> x.getUsername().equals("dni_cliente_1")).findFirst().orElseThrow().getTelefono());
    }

    @Test
    @DisplayName("Las sobrecargas anteriores de registrarCliente siguen funcionando sin DNI ni celular")
    void registroSinDniNiCelular() {
        assertEquals("", usuarios.registrarCliente("dni_cliente_2", "Clave1234", "Cliente Sin Dni", "dni_cliente_2@prueba.pe"));
        assertEquals("", usuarios.registrarCliente("Cliente Tres", "dni_cliente_3", "Clave1234"));

        Usuario dos = usuarios.autenticarUsuario("dni_cliente_2", "Clave1234", null, "127.0.0.1");
        assertNotNull(dos);
        assertNull(dos.getDni());
        assertNull(dos.getTelefono());
        assertNotNull(usuarios.autenticarUsuario("dni_cliente_3", "Clave1234", null, "127.0.0.1"));
    }

    @Test
    @DisplayName("Un DNI o celular con formato inválido se rechaza y no crea la cuenta")
    void registroValidaDniYCelular() {
        assertEquals("El DNI debe contener exactamente 8 dígitos numéricos.", usuarios.registrarCliente(
                "dni_cliente_4", "Clave1234", "Cliente Malo", "dni_cliente_4@prueba.pe", "1234", "912345678"));
        assertEquals("El celular debe contener 9 dígitos y empezar con 9.", usuarios.registrarCliente(
                "dni_cliente_4", "Clave1234", "Cliente Malo", "dni_cliente_4@prueba.pe", "12345678", "812345678"));
        assertNull(usuarios.autenticarUsuario("dni_cliente_4", "Clave1234", null, "127.0.0.1"));
    }

    @Test
    @DisplayName("La cuenta de muestra del cliente trae el DNI y el celular de las semillas SQL")
    void clienteDeMuestraTieneDniYCelular() {
        Usuario cliente = usuarios.listarUsuarios().stream()
                .filter(x -> x.getUsername().equals("cliente")).findFirst().orElseThrow();
        assertEquals("70451236", cliente.getDni());
        assertEquals("987654321", cliente.getTelefono());
    }

    // ---------------------------------------------------------------- conteo de la cabecera

    @Test
    @DisplayName("El conteo de la cabecera se guarda y se invalida al registrar o eliminar un producto")
    void conteoDeCabeceraSeInvalida() {
        int antes = productos.contarParaCabecera();
        assertEquals(antes, productos.contarParaCabecera(), "segunda lectura: mismo valor guardado");

        assertTrue(productos.registrar(new Producto("PCNT1", "Producto de conteo", "Redes", 10.0, 0)));
        assertEquals(antes + 1, productos.contarParaCabecera(), "registrar invalida el conteo");

        assertEquals(ProductoService.ResultadoEliminacion.ELIMINADO, productos.eliminar("PCNT1"));
        assertEquals(antes, productos.contarParaCabecera(), "eliminar invalida el conteo");
    }
}
