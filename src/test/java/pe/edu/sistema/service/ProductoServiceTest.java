package pe.edu.sistema.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.lang.reflect.Field;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.sistema.model.Pago;
import pe.edu.sistema.model.Pedido;
import pe.edu.sistema.model.Producto;
import pe.edu.sistema.model.ResultadoCompra;
import pe.edu.sistema.model.SolicitudPago;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.util.ConexionBD;

/**
 * Pruebas de {@link ProductoService} con el respaldo en memoria (sin SQL Server).
 *
 * El servicio es un singleton cuyo estado dura todo el JVM, así que las pruebas no dependen del
 * orden: cada una usa su propio cliente y, si cambia el stock, un producto distinto, y compara
 * deltas contra el stock leído justo antes en lugar de valores absolutos.
 */
class ProductoServiceTest {

    private static final String VISA = "4111 1111 1111 1111";
    private static final String DNI = "70451236";
    private static final DateTimeFormatter MM_AA = DateTimeFormatter.ofPattern("MM/yy");

    private final ProductoService servicio = ProductoService.getInstancia();

    /** Evita escribir en una base real si alguien ejecuta las pruebas con SQL Server activo. */
    @BeforeAll
    static void soloRespaldoEnMemoria() {
        assumeFalse(ConexionBD.estaDisponible(),
                "Estas pruebas usan el respaldo en memoria; hay un SQL Server activo y no se modificará");
    }

    // ---------------------------------------------------------------- auxiliares

    private static Usuario cliente(String username) {
        return new Usuario(2000, username, "Cliente de Prueba", username + "@prueba.pe", Usuario.ROL_CLIENTE, true);
    }

    private static Usuario conRol(String rol) {
        return new Usuario(3000, "personal_" + rol.toLowerCase(), "Personal de Prueba", "", rol, true);
    }

    /** Tarjeta Visa 4111 1111 1111 1111, vencimiento a 2 años, CVV 123 y boleta al DNI 70451236. */
    private static SolicitudPago tarjeta(String numero) {
        SolicitudPago s = new SolicitudPago();
        s.setMetodo(Pago.METODO_TARJETA);
        s.setNumeroTarjeta(numero);
        s.setTitular("Cliente de Prueba");
        s.setVencimiento(YearMonth.now().plusYears(2).format(MM_AA));
        s.setCvv("123");
        s.setTipoComprobante(Pago.BOLETA);
        s.setDocumento(DNI);
        return s;
    }

    private static SolicitudPago tarjetaValida() {
        return tarjeta(VISA);
    }

    /** Yape con factura a un RUC. */
    private static SolicitudPago yapeConFactura(String codigoYape) {
        SolicitudPago s = new SolicitudPago();
        s.setMetodo(Pago.METODO_YAPE);
        s.setCelular("987654321");
        s.setCodigoYape(codigoYape);
        s.setTipoComprobante(Pago.FACTURA);
        s.setDocumento("20123456789");
        s.setRazonSocial("NEXORA S.A.C.");
        return s;
    }

    private int stockDe(String codigo) {
        return servicio.buscarPorCodigo(codigo).getStock();
    }

    /** Compra aprobada que debe terminar en éxito; falla con el motivo si no. */
    private Pedido comprarOk(Usuario cliente, String codigo, int cantidad, SolicitudPago solicitud) {
        ResultadoCompra r = servicio.comprarConPago(cliente, codigo, cantidad, solicitud);
        assertTrue(r.isExito(), "La compra debía aprobarse: " + r.getError() + " " + r.getErrores());
        return r.getPedido();
    }

    /** Correlativo numérico de un comprobante como B001-000007 o F001-000003. */
    private static long correlativo(String numeroComprobante) {
        return Long.parseLong(numeroComprobante.substring(numeroComprobante.indexOf('-') + 1));
    }

    // ---------------------------------------------------------------- producto y validación

    @Test
    @DisplayName("El estado del producto es coherente con el stock aunque se fuerce otro valor")
    void estadoCoherenteConStock() {
        Producto sinStock = new Producto("PX01", "Producto nuevo", "Accesorios", 10, 0);
        sinStock.setEstado(Producto.DISPONIBLE);
        assertEquals(Producto.AGOTADO, sinStock.getEstado());
        assertFalse(sinStock.isDisponible());

        Producto conStock = new Producto("PX02", "Producto nuevo", "Accesorios", 10, 3);
        conStock.setEstado(Producto.AGOTADO);
        assertEquals(Producto.DISPONIBLE, conStock.getEstado());
        assertTrue(conStock.isDisponible());
    }

    private Map<String, String> formularioValido() {
        Map<String, String> form = new HashMap<>();
        form.put("codigo", "PRUEBA");
        form.put("nombre", "Producto de prueba");
        form.put("categoria", "Accesorios");
        form.put("precio", "80.00");
        form.put("stock", "2");
        return form;
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "1e999", "-5", "0", "-0.01", "abc", ""})
    @DisplayName("validar rechaza precios no finitos, negativos, cero o ilegibles")
    void validarRechazaPreciosInvalidos(String precio) {
        Map<String, String> form = formularioValido();
        form.put("precio", precio);
        assertTrue(servicio.validar(form, false).containsKey("precio"), "Debía rechazar el precio '" + precio + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"80", "80.00", "0.01", "1,250.50", "99.9"})
    @DisplayName("validar acepta precios positivos")
    void validarAceptaPreciosValidos(String precio) {
        Map<String, String> form = formularioValido();
        form.put("precio", precio);
        assertFalse(servicio.validar(form, false).containsKey("precio"), precio);
    }

    @Test
    @DisplayName("validar acepta un formulario correcto y rechaza código duplicado, categoría y stock inválidos")
    void validarFormulario() {
        assertTrue(servicio.validar(formularioValido(), true).isEmpty());

        Map<String, String> duplicado = formularioValido();
        duplicado.put("codigo", "p001");
        assertTrue(servicio.validar(duplicado, true).containsKey("codigo"), "P001 ya existe");
        assertFalse(servicio.validar(duplicado, false).containsKey("codigo"), "Al editar el código existente es válido");

        Map<String, String> categoria = formularioValido();
        categoria.put("categoria", "Muebles");
        assertTrue(servicio.validar(categoria, false).containsKey("categoria"));

        for (String stock : new String[]{"-1", "1.5", "abc", ""}) {
            Map<String, String> form = formularioValido();
            form.put("stock", stock);
            assertTrue(servicio.validar(form, true).containsKey("stock"), "Debía rechazar el stock inicial '" + stock + "'");
            assertFalse(servicio.validar(form, false).containsKey("stock"), "Al editar el stock no se valida");
        }
    }

    // ---------------------------------------------------------------- comprarConPago: rechazos

    @ParameterizedTest
    @ValueSource(strings = {Usuario.ROL_CONSULTA, Usuario.ROL_ADMIN, Usuario.ROL_ALMACEN})
    @DisplayName("comprarConPago rechaza a quien no es Cliente y no toca el stock")
    void rechazaRolesQueNoSonCliente(String rol) {
        int antes = stockDe("P009");
        ResultadoCompra r = servicio.comprarConPago(conRol(rol), "P009", 1, tarjetaValida());
        assertFalse(r.isExito(), rol);
        assertNotNull(r.getError());
        assertTrue(r.getError().contains("cuentas de cliente"), r.getError());
        assertEquals(antes, stockDe("P009"));
    }

    @Test
    @DisplayName("comprarConPago rechaza usuario nulo o cliente inactivo")
    void rechazaUsuarioNuloOInactivo() {
        int antes = stockDe("P009");
        assertFalse(servicio.comprarConPago(null, "P009", 1, tarjetaValida()).isExito());
        Usuario inactivo = new Usuario(2001, "cliente_inactivo", "Inactivo", "", Usuario.ROL_CLIENTE, false);
        assertFalse(servicio.comprarConPago(inactivo, "P009", 1, tarjetaValida()).isExito());
        assertEquals(antes, stockDe("P009"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    @DisplayName("comprarConPago rechaza cantidades menores que 1")
    void rechazaCantidadNoPositiva(int cantidad) {
        int antes = stockDe("P009");
        ResultadoCompra r = servicio.comprarConPago(cliente("cliente_cantidad"), "P009", cantidad, tarjetaValida());
        assertFalse(r.isExito());
        assertTrue(r.getError().contains("mayor que cero"), r.getError());
        assertEquals(antes, stockDe("P009"));
    }

    @Test
    @DisplayName("comprarConPago rechaza la sobreventa y los productos inexistentes")
    void rechazaSobreventaYProductoInexistente() {
        Usuario c = cliente("cliente_sobreventa");
        int antes = stockDe("P005");

        ResultadoCompra sobreventa = servicio.comprarConPago(c, "P005", antes + 1, tarjetaValida());
        assertFalse(sobreventa.isExito());
        assertTrue(sobreventa.getError().contains("Stock insuficiente"), sobreventa.getError());
        assertTrue(sobreventa.getError().contains(String.valueOf(antes)), "Debe indicar el stock disponible");
        assertEquals(antes, stockDe("P005"));

        ResultadoCompra inexistente = servicio.comprarConPago(c, "NOEXISTE", 1, tarjetaValida());
        assertFalse(inexistente.isExito());
        assertTrue(inexistente.getError().contains("no existe"), inexistente.getError());

        ResultadoCompra agotado = servicio.comprarConPago(c, "P003", 1, tarjetaValida());
        assertFalse(agotado.isExito(), "P003 nace sin stock");
        assertTrue(agotado.getError().contains("Stock insuficiente"), agotado.getError());
    }

    @Test
    @DisplayName("comprarConPago con datos de pago inválidos devuelve los errores y no toca el stock")
    void datosDePagoInvalidos() {
        Usuario c = cliente("cliente_datos_invalidos");
        int stockAntes = stockDe("P009");
        int pedidosAntes = servicio.listarPedidos(c).size();

        SolicitudPago numeroMalo = tarjeta("4111 1111 1111 1112");
        ResultadoCompra r = servicio.comprarConPago(c, "P009", 1, numeroMalo);
        assertFalse(r.isExito());
        assertNull(r.getError(), "Un formulario inválido no es un pago rechazado");
        assertTrue(r.getErrores().containsKey("numeroTarjeta"), r.getErrores().toString());

        SolicitudPago vencida = tarjetaValida();
        vencida.setVencimiento(YearMonth.now().minusMonths(1).format(MM_AA));
        assertTrue(servicio.comprarConPago(c, "P009", 1, vencida).getErrores().containsKey("vencimiento"));

        SolicitudPago sinMetodo = tarjetaValida();
        sinMetodo.setMetodo("");
        assertTrue(servicio.comprarConPago(c, "P009", 1, sinMetodo).getErrores().containsKey("metodo"));

        ResultadoCompra sinSolicitud = servicio.comprarConPago(c, "P009", 1, null);
        assertFalse(sinSolicitud.isExito());
        assertFalse(sinSolicitud.getErrores().isEmpty());

        assertEquals(stockAntes, stockDe("P009"));
        assertEquals(pedidosAntes, servicio.listarPedidos(c).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"4000 0000 0000 0002", "4000 0000 0000 0069"})
    @DisplayName("Una tarjeta rechazada por la pasarela no descuenta stock ni crea pedido")
    void tarjetaRechazadaNoTocaStock(String numero) {
        Usuario c = cliente("cliente_rechazo_" + numero.substring(numero.length() - 4));
        int stockAntes = stockDe("P009");

        ResultadoCompra r = servicio.comprarConPago(c, "P009", 2, tarjeta(numero));

        assertFalse(r.isExito());
        assertNull(r.getPedido());
        assertTrue(r.getErrores().isEmpty(), "El formulario era válido; lo rechazó la pasarela");
        assertTrue(r.getError().startsWith("Pago rechazado"), r.getError());
        assertEquals(stockAntes, stockDe("P009"));
        assertTrue(servicio.listarPedidos(c).isEmpty());
    }

    @Test
    @DisplayName("Un código Yape incorrecto se rechaza sin descontar stock")
    void yapeRechazadoNoTocaStock() {
        Usuario c = cliente("cliente_yape_rechazo");
        int stockAntes = stockDe("P009");
        ResultadoCompra r = servicio.comprarConPago(c, "P009", 1, yapeConFactura("654321"));
        assertFalse(r.isExito());
        assertTrue(r.getError().startsWith("Pago rechazado"), r.getError());
        assertEquals(stockAntes, stockDe("P009"));
    }

    // ---------------------------------------------------------------- comprarConPago: aprobadas

    @Test
    @DisplayName("Una compra aprobada descuenta el stock y devuelve un pedido pagado con boleta B001")
    void compraAprobadaConBoleta() {
        Usuario c = cliente("cliente_boleta");
        Producto producto = servicio.buscarPorCodigo("P007");
        int stockAntes = producto.getStock();
        double precio = producto.getPrecio();

        Pedido pedido = comprarOk(c, "P007", 3, tarjetaValida());

        assertEquals(stockAntes - 3, stockDe("P007"));
        assertEquals("P007", pedido.getCodigoProducto());
        assertEquals(3, pedido.getCantidad());
        assertEquals(c.getUsername(), pedido.getUsernameCliente());
        assertEquals(precio * 3, pedido.getTotal(), 0.001);
        assertEquals("Pagado", pedido.getEstadoPago());

        Pago pago = pedido.getPago();
        assertNotNull(pago);
        assertTrue(pago.isAprobado());
        assertEquals(Pago.APROBADO, pago.getEstado());
        assertEquals(precio * 3, pago.getMonto(), 0.001);
        assertEquals(pedido.getIdPedido(), pago.getIdPedido());
        assertEquals(Pago.BOLETA, pago.getTipoComprobante());
        assertFalse(pago.isFactura());
        assertTrue(pago.getNumeroComprobante().matches("B001-\\d{6}"), pago.getNumeroComprobante());
        assertEquals(DNI, pago.getDocumentoCliente());
        assertEquals("Visa", pago.getMarca());
        assertEquals("1111", pago.getUltimosDigitos());
        assertTrue(pago.getCodigoAutorizacion().matches("\\d{6}"));
        assertTrue(pago.getCodigoOperacion().startsWith("OP-"));
        assertFalse(pago.getNumeroComprobante().contains("4111"));
    }

    @Test
    @DisplayName("El IGV del 18 % va incluido: subtotal + IGV = total")
    void igvIncluidoEnElTotal() {
        Pedido pedido = comprarOk(cliente("cliente_igv"), "P007", 2, tarjetaValida());
        Pago pago = pedido.getPago();
        assertEquals(70.00, pago.getMonto(), 0.001);
        assertEquals(59.32, pago.getSubtotal(), 0.001);
        assertEquals(10.68, pago.getIgvMonto(), 0.001);
        assertEquals(pago.getMonto(), pago.getSubtotal() + pago.getIgvMonto(), 0.011);
    }

    @Test
    @DisplayName("Yape con factura emite un comprobante F001 con RUC y razón social")
    void compraAprobadaYapeConFactura() {
        Usuario c = cliente("cliente_factura");
        int stockAntes = stockDe("P011");

        Pedido pedido = comprarOk(c, "P011", 1, yapeConFactura(PasarelaPagoService.CODIGO_YAPE_PRUEBA));

        assertEquals(stockAntes - 1, stockDe("P011"));
        Pago pago = pedido.getPago();
        assertTrue(pago.isFactura());
        assertTrue(pago.getNumeroComprobante().matches("F001-\\d{6}"), pago.getNumeroComprobante());
        assertEquals(Pago.METODO_YAPE, pago.getMetodo());
        assertEquals("Yape", pago.getMarca());
        assertEquals("4321", pago.getUltimosDigitos());
        assertEquals("20123456789", pago.getDocumentoCliente());
        assertEquals("NEXORA S.A.C.", pago.getRazonSocial());
    }

    @Test
    @DisplayName("Los comprobantes son correlativos dentro de cada serie, B001 y F001 por separado")
    void comprobantesCorrelativosPorSerie() {
        Usuario c = cliente("cliente_correlativo");

        long boleta1 = correlativo(comprarOk(c, "P006", 1, tarjetaValida()).getPago().getNumeroComprobante());
        long factura1 = correlativo(comprarOk(c, "P006", 1, yapeConFactura("123456")).getPago().getNumeroComprobante());
        long boleta2 = correlativo(comprarOk(c, "P006", 1, tarjetaValida()).getPago().getNumeroComprobante());
        long factura2 = correlativo(comprarOk(c, "P006", 1, yapeConFactura("123456")).getPago().getNumeroComprobante());

        assertEquals(boleta1 + 1, boleta2, "Las boletas avanzan de uno en uno");
        assertEquals(factura1 + 1, factura2, "Las facturas avanzan de uno en uno");
    }

    @Test
    @DisplayName("Al vender las últimas unidades el producto queda agotado y no admite más ventas")
    void compraQueAgotaElStock() {
        Usuario c = cliente("cliente_agota");
        int stock = stockDe("P010");
        assertTrue(stock > 0, "P010 debe tener existencias al empezar");

        comprarOk(c, "P010", stock, tarjetaValida());

        Producto p = servicio.buscarPorCodigo("P010");
        assertEquals(0, p.getStock());
        assertFalse(p.isDisponible());
        assertEquals(Producto.AGOTADO, p.getEstado());

        ResultadoCompra otra = servicio.comprarConPago(c, "P010", 1, tarjetaValida());
        assertFalse(otra.isExito());
        assertTrue(otra.getError().contains("Stock insuficiente"), otra.getError());
        assertEquals(0, stockDe("P010"));
    }

    // ---------------------------------------------------------------- buscarPedido

    @Test
    @DisplayName("buscarPedido: el dueño y el personal ven el pedido; otro cliente y null no")
    void buscarPedidoSegunPermisos() {
        Usuario dueno = cliente("cliente_dueno");
        Pedido pedido = comprarOk(dueno, "P006", 1, tarjetaValida());
        int id = pedido.getIdPedido();

        Pedido visto = servicio.buscarPedido(id, dueno);
        assertNotNull(visto, "El dueño ve su pedido");
        assertEquals(id, visto.getIdPedido());
        assertEquals(pedido.getTotal(), visto.getTotal(), 0.001);
        assertNotNull(servicio.buscarPedido(id, cliente("CLIENTE_DUENO")), "El usuario no distingue mayúsculas");
        assertNotNull(servicio.buscarPedido(id, conRol(Usuario.ROL_ADMIN)));
        assertNotNull(servicio.buscarPedido(id, conRol(Usuario.ROL_ALMACEN)));
        assertNotNull(servicio.buscarPedido(id, conRol(Usuario.ROL_CONSULTA)));

        assertNull(servicio.buscarPedido(id, cliente("cliente_otro")), "Otro cliente no ve pedidos ajenos");
        assertNull(servicio.buscarPedido(id, null), "Sin usuario no se devuelve nada");
        assertNull(servicio.buscarPedido(-1, conRol(Usuario.ROL_ADMIN)), "Un pedido inexistente da null");
        assertNull(servicio.buscarPedido(Integer.MAX_VALUE, dueno));
    }

    @Test
    @DisplayName("Los pedidos iniciales de muestra pertenecen a 'cliente' y vienen con su pago")
    void pedidosDeMuestra() {
        Usuario cliente = cliente("cliente");
        Pedido semilla = servicio.buscarPedido(1, cliente);
        assertNotNull(semilla);
        assertEquals("Pagado", semilla.getEstadoPago());
        assertEquals("B001-000001", semilla.getPago().getNumeroComprobante());
        assertNull(servicio.buscarPedido(1, cliente("cliente_ajeno_a_la_muestra")));
    }

    // ---------------------------------------------------------------- listarPedidos

    @Test
    @DisplayName("listarPedidos de un cliente no incluye pedidos ajenos y el personal ve todos")
    void listarPedidosNoMezclaClientes() {
        Usuario ana = cliente("cliente_lista_ana");
        Usuario beto = cliente("cliente_lista_beto");
        Pedido deAna = comprarOk(ana, "P006", 1, tarjetaValida());
        Pedido deBeto = comprarOk(beto, "P006", 1, tarjetaValida());

        List<Pedido> deAnaLista = servicio.listarPedidos(ana);
        assertEquals(1, deAnaLista.size());
        assertEquals(deAna.getIdPedido(), deAnaLista.get(0).getIdPedido());
        for (Pedido p : deAnaLista) {
            assertEquals("cliente_lista_ana", p.getUsernameCliente());
        }
        assertTrue(servicio.listarPedidos(beto).stream().noneMatch(p -> p.getIdPedido() == deAna.getIdPedido()));

        List<Pedido> todos = servicio.listarPedidos(conRol(Usuario.ROL_ADMIN));
        assertTrue(todos.stream().anyMatch(p -> p.getIdPedido() == deAna.getIdPedido()));
        assertTrue(todos.stream().anyMatch(p -> p.getIdPedido() == deBeto.getIdPedido()));

        assertTrue(servicio.listarPedidos(cliente("cliente_sin_compras")).isEmpty());
    }

    @Test
    @DisplayName("listarPedidos(null) devuelve una lista vacía")
    void listarPedidosSinUsuario() {
        assertTrue(servicio.listarPedidos(null).isEmpty());
    }

    // ---------------------------------------------------------------- correlativo, orden y roles

    /** Lista interna de pedidos del respaldo en memoria (solo para preparar escenarios). */
    @SuppressWarnings("unchecked")
    private List<Pedido> pedidosInternos() throws ReflectiveOperationException {
        Field campo = ProductoService.class.getDeclaredField("pedidos");
        campo.setAccessible(true);
        return (List<Pedido>) campo.get(servicio);
    }

    @Test
    @DisplayName("El correlativo del comprobante es el mayor emitido de la serie + 1, no la cantidad de comprobantes")
    void correlativoEsElMaximoMasUno() throws ReflectiveOperationException {
        // Una factura F001-000500 ya emitida (con huecos antes): la siguiente debe ser la 501
        Pago previo = new Pago();
        previo.setTipoComprobante(Pago.FACTURA);
        previo.setNumeroComprobante("F001-000500");
        Pedido existente = new Pedido(900_000, "cliente_hueco", "Cliente Hueco", "P007",
                "USB SanDisk Ultra 64GB", 1, 35.00, "01/01/2026 · 10:00");
        existente.setPago(previo);
        synchronized (servicio) {
            pedidosInternos().add(existente);
        }
        try {
            Pedido factura = comprarOk(cliente("cliente_correlativo_max"), "P007", 1, yapeConFactura("123456"));
            assertEquals("F001-000501", factura.getPago().getNumeroComprobante());

            // La otra serie no se ve afectada por el hueco de las facturas
            Pedido boleta = comprarOk(cliente("cliente_correlativo_max"), "P007", 1, tarjetaValida());
            assertTrue(correlativo(boleta.getPago().getNumeroComprobante()) < 500,
                    boleta.getPago().getNumeroComprobante());
        } finally {
            synchronized (servicio) {
                pedidosInternos().remove(existente);
            }
        }
    }

    @Test
    @DisplayName("listarPedidos devuelve el historial por id descendente; las semillas (1 y 2) quedan al final")
    void listarPedidosPorIdDescendente() {
        Usuario c = cliente("cliente_orden");
        Pedido primero = comprarOk(c, "P007", 1, tarjetaValida());
        Pedido segundo = comprarOk(c, "P007", 1, tarjetaValida());

        List<Pedido> propios = servicio.listarPedidos(c);
        assertEquals(2, propios.size());
        assertEquals(segundo.getIdPedido(), propios.get(0).getIdPedido(), "El más reciente va primero");
        assertEquals(primero.getIdPedido(), propios.get(1).getIdPedido());

        List<Pedido> todos = servicio.listarPedidos(conRol(Usuario.ROL_ADMIN));
        for (int i = 1; i < todos.size(); i++) {
            assertTrue(todos.get(i - 1).getIdPedido() > todos.get(i).getIdPedido(),
                    "Los ids deben ir de mayor a menor en la posición " + i);
        }
        assertEquals(2, todos.get(todos.size() - 2).getIdPedido());
        assertEquals(1, todos.get(todos.size() - 1).getIdPedido());
    }

    @Test
    @DisplayName("Un rol desconocido (o null) no ve pedidos, ni con el mismo usuario del dueño")
    void rolDesconocidoNoVePedidos() {
        Usuario dueno = cliente("cliente_rol_raro");
        Pedido pedido = comprarOk(dueno, "P007", 1, tarjetaValida());
        Usuario auditor = new Usuario(4000, "cliente_rol_raro", "Rol Raro", "", "Auditor", true);
        Usuario sinRol = new Usuario(4001, "cliente_rol_raro", "Sin Rol", "", null, true);

        assertNull(servicio.buscarPedido(pedido.getIdPedido(), auditor));
        assertNull(servicio.buscarPedido(1, auditor));
        assertNull(servicio.buscarPedido(pedido.getIdPedido(), sinRol));
        assertTrue(servicio.listarPedidos(auditor).isEmpty());
        assertTrue(servicio.listarPedidos(sinRol).isEmpty());
        assertNotNull(servicio.buscarPedido(pedido.getIdPedido(), dueno), "El dueño sigue viéndolo");
    }

    // ---------------------------------------------------------------- eliminar

    @Test
    @DisplayName("eliminar distingue entre eliminado y no encontrado")
    void eliminarDevuelveResultado() {
        // Sin stock inicial no hay movimientos de inventario y el producto se puede borrar
        servicio.registrar(new Producto("TELIM01", "Producto temporal", "Accesorios", 10.00, 0));

        assertEquals(ProductoService.ResultadoEliminacion.ELIMINADO, servicio.eliminar(" telim01 "));
        assertNull(servicio.buscarPorCodigo("TELIM01"));
        assertEquals(ProductoService.ResultadoEliminacion.NO_ENCONTRADO, servicio.eliminar("TELIM01"));
        assertEquals(ProductoService.ResultadoEliminacion.NO_ENCONTRADO, servicio.eliminar(null));
    }
}
