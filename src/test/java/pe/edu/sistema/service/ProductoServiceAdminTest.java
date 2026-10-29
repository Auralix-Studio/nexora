package pe.edu.sistema.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.text.Collator;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.edu.sistema.model.Categoria;
import pe.edu.sistema.model.MovimientoStock;
import pe.edu.sistema.model.Pago;
import pe.edu.sistema.model.Pedido;
import pe.edu.sistema.model.Producto;
import pe.edu.sistema.model.ResultadoCompra;
import pe.edu.sistema.model.SolicitudPago;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.util.ConexionBD;

/**
 * Pruebas de la administración de {@link ProductoService} (estados de pedido, anulación, kardex, filtros y
 * categorías) con el respaldo en memoria.
 *
 * El servicio es un singleton con estado compartido durante todo el JVM: cada prueba registra sus propios
 * productos, clientes y categorías con códigos que no usa ninguna otra, y nunca toca los pedidos semilla.
 */
class ProductoServiceAdminTest {

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
        return new Usuario(2000, username, "Cliente " + username, username + "@prueba.pe", Usuario.ROL_CLIENTE, true);
    }

    private static Usuario clienteConNombre(String username, String nombre) {
        return new Usuario(2000, username, nombre, username + "@prueba.pe", Usuario.ROL_CLIENTE, true);
    }

    private static Usuario admin() {
        return new Usuario(3001, "adm_prueba_prod", "Administrador de Prueba", "", Usuario.ROL_ADMIN, true);
    }

    private static Usuario almacen() {
        return new Usuario(3002, "alm_prueba_prod", "Almacén de Prueba", "", Usuario.ROL_ALMACEN, true);
    }

    private static Usuario consulta() {
        return new Usuario(3003, "con_prueba_prod", "Consulta de Prueba", "", Usuario.ROL_CONSULTA, true);
    }

    /** Tarjeta Visa 4111 1111 1111 1111, vencimiento a 2 años, CVV 123 y boleta al DNI 70451236. */
    private static SolicitudPago tarjeta() {
        SolicitudPago s = new SolicitudPago();
        s.setMetodo(Pago.METODO_TARJETA);
        s.setNumeroTarjeta(VISA);
        s.setTitular("Cliente de Prueba");
        s.setVencimiento(YearMonth.now().plusYears(2).format(MM_AA));
        s.setCvv("123");
        s.setTipoComprobante(Pago.BOLETA);
        s.setDocumento(DNI);
        return s;
    }

    /** Registra un producto propio de la prueba (categoría Accesorios) y lo devuelve. */
    private Producto registrarProducto(String codigo, int stock, double precio) {
        servicio.registrar(new Producto(codigo, "Producto " + codigo, "Accesorios", precio, stock));
        return servicio.buscarPorCodigo(codigo);
    }

    private int stockDe(String codigo) {
        return servicio.buscarPorCodigo(codigo).getStock();
    }

    private Pedido comprar(Usuario cliente, String codigo, int cantidad) {
        ResultadoCompra r = servicio.comprarConPago(cliente, codigo, cantidad, tarjeta());
        assertTrue(r.isExito(), "La compra debía aprobarse: " + r.getError() + " " + r.getErrores());
        return r.getPedido();
    }

    /** Lee el pedido otra vez desde el servicio, para no depender de la instancia devuelta al comprar. */
    private Pedido pedido(int id) {
        Pedido p = servicio.buscarPedido(id, admin());
        assertNotNull(p, "El pedido " + id + " debía existir");
        return p;
    }

    private int idCategoria(String nombre) {
        return servicio.listarCategorias(false).stream()
                .filter(c -> c.getNombre().equals(nombre)).findFirst()
                .orElseThrow(() -> new AssertionError("No existe la categoría " + nombre)).getIdCategoria();
    }

    private Categoria categoria(String nombre) {
        return servicio.listarCategorias(false).stream()
                .filter(c -> c.getNombre().equals(nombre)).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- estados del pedido

    @Test
    @DisplayName("El pedido avanza solo al siguiente estado: Confirmado, En preparación, Enviado y Entregado")
    void flujoDeEstadosSoloAvanzaAlSiguiente() {
        registrarProducto("AFLUJO01", 10, 50.00);
        Pedido creado = comprar(cliente("adm_cli_flujo"), "AFLUJO01", 1);
        int id = creado.getIdPedido();
        assertEquals(Pedido.CONFIRMADO, creado.getEstado(), "Una compra aprobada nace Confirmada");

        // Saltarse pasos o retroceder se rechaza con el siguiente estado permitido
        for (String destino : new String[]{Pedido.ENVIADO, Pedido.ENTREGADO, Pedido.CONFIRMADO, Pedido.ANULADO, "Inventado"}) {
            String error = servicio.cambiarEstadoPedido(almacen(), id, destino);
            assertNotNull(error, "No debía permitir pasar a '" + destino + "'");
            assertTrue(error.contains("solo puede pasar a " + Pedido.EN_PREPARACION), error);
        }
        assertEquals(Pedido.CONFIRMADO, pedido(id).getEstado());

        assertNull(servicio.cambiarEstadoPedido(almacen(), id, Pedido.EN_PREPARACION));
        assertEquals(Pedido.EN_PREPARACION, pedido(id).getEstado());
        assertNotNull(pedido(id).getFechaActualizacion(), "El cambio registra la fecha de actualización");

        assertNull(servicio.cambiarEstadoPedido(admin(), id, Pedido.ENVIADO));
        assertEquals(Pedido.ENVIADO, pedido(id).getEstado());

        assertNull(servicio.cambiarEstadoPedido(almacen(), id, Pedido.ENTREGADO));
        assertEquals(Pedido.ENTREGADO, pedido(id).getEstado());
        assertNull(pedido(id).getSiguienteEstado());
    }

    @Test
    @DisplayName("Entregado es un estado final: no admite más cambios ni anulación")
    void entregadoEsFinal() {
        registrarProducto("AFINAL01", 10, 50.00);
        int id = comprar(cliente("adm_cli_final"), "AFINAL01", 2).getIdPedido();
        int stockVendido = stockDe("AFINAL01");
        for (String estado : new String[]{Pedido.EN_PREPARACION, Pedido.ENVIADO, Pedido.ENTREGADO}) {
            assertNull(servicio.cambiarEstadoPedido(admin(), id, estado));
        }

        for (String destino : new String[]{Pedido.ENTREGADO, Pedido.ENVIADO, Pedido.CONFIRMADO, Pedido.ANULADO}) {
            String error = servicio.cambiarEstadoPedido(admin(), id, destino);
            assertNotNull(error, destino);
            assertTrue(error.contains("ya está Entregado"), error);
        }
        String noAnulable = servicio.anularPedido(admin(), id, "Quiere devolverlo");
        assertNotNull(noAnulable);
        assertTrue(noAnulable.contains("ya no puede anularse"), noAnulable);
        assertEquals(Pedido.ENTREGADO, pedido(id).getEstado());
        assertEquals(stockVendido, stockDe("AFINAL01"), "Anular un pedido entregado no devuelve stock");
    }

    @Test
    @DisplayName("Anulado es un estado final: no admite cambios de estado ni una segunda anulación")
    void anuladoEsFinal() {
        registrarProducto("AFINAL02", 10, 50.00);
        int id = comprar(cliente("adm_cli_final2"), "AFINAL02", 1).getIdPedido();
        assertNull(servicio.anularPedido(admin(), id, "Pedido duplicado"));

        for (String destino : new String[]{Pedido.EN_PREPARACION, Pedido.CONFIRMADO, Pedido.ENVIADO, Pedido.ANULADO}) {
            String error = servicio.cambiarEstadoPedido(admin(), id, destino);
            assertNotNull(error, destino);
            assertTrue(error.contains("está anulado y no admite cambios de estado"), error);
        }
        assertTrue(servicio.anularPedido(admin(), id, "Otra vez").contains("ya está anulado"));
        assertNull(pedido(id).getSiguienteEstado());
    }

    @Test
    @DisplayName("cambiarEstadoPedido exige Administrador o Almacén y valida el pedido y el estado indicado")
    void cambiarEstadoValidaPermisosYDatos() {
        registrarProducto("APERM01", 10, 50.00);
        int id = comprar(cliente("adm_cli_perm"), "APERM01", 1).getIdPedido();
        String sinPermiso = "No tienes permiso para cambiar el estado de los pedidos.";

        // Consulta puede ver los pedidos pero no avanzarlos; el cliente tampoco
        assertEquals(sinPermiso, servicio.cambiarEstadoPedido(consulta(), id, Pedido.EN_PREPARACION));
        assertEquals(sinPermiso, servicio.cambiarEstadoPedido(cliente("adm_cli_perm"), id, Pedido.EN_PREPARACION));
        assertEquals(sinPermiso, servicio.cambiarEstadoPedido(null, id, Pedido.EN_PREPARACION));
        assertEquals(Pedido.CONFIRMADO, pedido(id).getEstado());

        assertEquals("Indica el nuevo estado del pedido.", servicio.cambiarEstadoPedido(almacen(), id, null));
        assertEquals("Indica el nuevo estado del pedido.", servicio.cambiarEstadoPedido(almacen(), id, "   "));
        assertEquals("El pedido solicitado no existe.",
                servicio.cambiarEstadoPedido(almacen(), 987_654, Pedido.EN_PREPARACION));

        assertNull(servicio.cambiarEstadoPedido(almacen(), id, "  " + Pedido.EN_PREPARACION + "  "),
                "El estado se recorta antes de comparar");
        assertEquals(Pedido.EN_PREPARACION, pedido(id).getEstado());
    }

    // ---------------------------------------------------------------- anulación

    @Test
    @DisplayName("anularPedido es solo del Administrador y exige un motivo de hasta 200 caracteres")
    void anularExigeAdministradorYMotivo() {
        registrarProducto("AANUL01", 10, 50.00);
        int id = comprar(cliente("adm_cli_anul1"), "AANUL01", 1).getIdPedido();
        int stock = stockDe("AANUL01");
        String soloAdmin = "Solo el administrador puede anular pedidos.";

        assertEquals(soloAdmin, servicio.anularPedido(almacen(), id, "Motivo"));
        assertEquals(soloAdmin, servicio.anularPedido(consulta(), id, "Motivo"));
        assertEquals(soloAdmin, servicio.anularPedido(cliente("adm_cli_anul1"), id, "Motivo"));
        assertEquals(soloAdmin, servicio.anularPedido(null, id, "Motivo"));

        assertEquals("Indica el motivo de la anulación.", servicio.anularPedido(admin(), id, null));
        assertEquals("Indica el motivo de la anulación.", servicio.anularPedido(admin(), id, "   "));
        assertTrue(servicio.anularPedido(admin(), id, "x".repeat(201)).contains("máximo 200"));
        assertEquals("El pedido solicitado no existe.", servicio.anularPedido(admin(), 987_654, "Motivo"));

        assertEquals(Pedido.CONFIRMADO, pedido(id).getEstado(), "Ningún intento rechazado cambia el pedido");
        assertEquals(stock, stockDe("AANUL01"));
        assertEquals(Pago.APROBADO, pedido(id).getPago().getEstado());

        assertNull(servicio.anularPedido(admin(), id, "x".repeat(200)), "200 caracteres es el máximo permitido");
        MovimientoStock devolucion = servicio.listarMovimientos("AANUL01", 5).get(0);
        assertTrue(devolucion.getMotivo().length() <= 200, "El motivo del kardex se recorta a la columna");
    }

    @Test
    @DisplayName("Anular un pedido Confirmado lo marca Anulado, reembolsa el pago, devuelve el stock y deja una Devolución en el kardex")
    void anularConfirmadoReembolsaYDevuelveStock() {
        registrarProducto("AANUL02", 10, 100.00);
        int id = comprar(cliente("adm_cli_anul2"), "AANUL02", 3).getIdPedido();
        assertEquals(7, stockDe("AANUL02"));

        assertNull(servicio.anularPedido(admin(), id, "Cliente se arrepintió"));

        Pedido anulado = pedido(id);
        assertEquals(Pedido.ANULADO, anulado.getEstado());
        assertTrue(anulado.isAnulado());
        assertFalse(anulado.isVentaValida(), "Un pedido anulado no cuenta como venta");
        assertNotNull(anulado.getFechaActualizacion());
        assertEquals(Pago.REEMBOLSADO, anulado.getPago().getEstado());
        assertTrue(anulado.getPago().isReembolsado());
        assertFalse(anulado.getPago().isAprobado());
        assertEquals("Reembolsado", anulado.getEstadoPago());
        assertEquals(10, stockDe("AANUL02"), "Se devuelven las 3 unidades");

        List<MovimientoStock> kardex = servicio.listarMovimientos("AANUL02", 10);
        assertEquals(3, kardex.size(), "Stock inicial, Salida por la venta y Devolución");
        MovimientoStock devolucion = kardex.get(0);
        assertEquals(MovimientoStock.DEVOLUCION, devolucion.getTipo());
        assertEquals(3, devolucion.getCantidad());
        assertEquals("+3", devolucion.getCantidadTexto());
        assertEquals(10, devolucion.getStockResultante());
        assertEquals("adm_prueba_prod", devolucion.getUsuario());
        assertTrue(devolucion.getMotivo().startsWith("Anulación " + anulado.getCodigoPedido() + ": "), devolucion.getMotivo());
        assertTrue(devolucion.getMotivo().contains("Cliente se arrepintió"), devolucion.getMotivo());
        assertEquals(MovimientoStock.SALIDA, kardex.get(1).getTipo());
        assertEquals("Stock inicial", kardex.get(2).getMotivo());
    }

    @Test
    @DisplayName("Un pedido En preparación también se anula; uno Enviado ya no")
    void anularEnPreparacionSiEnviadoNo() {
        registrarProducto("AANUL03", 10, 50.00);
        Usuario c = cliente("adm_cli_anul3");
        int enPreparacion = comprar(c, "AANUL03", 2).getIdPedido();
        int enviado = comprar(c, "AANUL03", 1).getIdPedido();
        assertEquals(7, stockDe("AANUL03"));

        assertNull(servicio.cambiarEstadoPedido(almacen(), enPreparacion, Pedido.EN_PREPARACION));
        assertNull(servicio.anularPedido(admin(), enPreparacion, "Sin stock real"));
        assertEquals(Pedido.ANULADO, pedido(enPreparacion).getEstado());
        assertEquals(9, stockDe("AANUL03"));

        assertNull(servicio.cambiarEstadoPedido(almacen(), enviado, Pedido.EN_PREPARACION));
        assertNull(servicio.cambiarEstadoPedido(almacen(), enviado, Pedido.ENVIADO));
        String error = servicio.anularPedido(admin(), enviado, "Tarde");
        assertNotNull(error);
        assertTrue(error.contains("Enviado") && error.contains("ya no puede anularse"), error);
        assertEquals(Pedido.ENVIADO, pedido(enviado).getEstado());
        assertEquals(Pago.APROBADO, pedido(enviado).getPago().getEstado());
        assertEquals(9, stockDe("AANUL03"), "El pedido enviado conserva su descuento de stock");
    }

    @Test
    @DisplayName("Anular la compra que agotó el producto lo deja otra vez Disponible")
    void anularReactivaProductoAgotado() {
        registrarProducto("AANUL04", 2, 50.00);
        int id = comprar(cliente("adm_cli_anul4"), "AANUL04", 2).getIdPedido();
        assertEquals(Producto.AGOTADO, servicio.buscarPorCodigo("AANUL04").getEstado());

        assertNull(servicio.anularPedido(admin(), id, "Error de digitación"));
        Producto p = servicio.buscarPorCodigo("AANUL04");
        assertEquals(2, p.getStock());
        assertEquals(Producto.DISPONIBLE, p.getEstado());
    }

    // ---------------------------------------------------------------- kardex

    @Test
    @DisplayName("Cada compra registra una Salida en el kardex con el stock resultante y el motivo 'Venta PED-0000'")
    void compraRegistraSalidaEnKardex() {
        registrarProducto("AKARD01", 10, 20.00);
        Pedido pedido = comprar(cliente("adm_cli_kardex"), "AKARD01", 3);

        List<MovimientoStock> kardex = servicio.listarMovimientos("akard01", 10);
        assertEquals(2, kardex.size(), "El stock inicial y la venta");
        assertEquals("Stock inicial", kardex.get(1).getMotivo());
        MovimientoStock salida = kardex.get(0);
        assertEquals(MovimientoStock.SALIDA, salida.getTipo());
        assertEquals("AKARD01", salida.getCodigoProducto());
        assertEquals("Producto AKARD01", salida.getNombreProducto());
        assertEquals(-3, salida.getCantidad());
        assertEquals("-3", salida.getCantidadTexto());
        assertFalse(salida.isEntrada());
        assertEquals(7, salida.getStockResultante());
        assertEquals("Venta " + pedido.getCodigoPedido(), salida.getMotivo());
        assertEquals("adm_cli_kardex", salida.getUsuario());
        assertNotNull(salida.getFecha());
    }

    @Test
    @DisplayName("listarMovimientos devuelve del más reciente al más antiguo, respeta el límite y filtra por producto")
    void listarMovimientosOrdenLimiteYFiltro() {
        registrarProducto("AKARD02", 10, 20.00);
        registrarProducto("AKARD03", 10, 20.00);
        assertNull(servicio.ajustarStock(almacen(), "AKARD02", "Entrada", 5, "Reposición"));
        assertNull(servicio.ajustarStock(almacen(), "AKARD03", "Entrada", 1, "Otro producto"));
        assertNull(servicio.ajustarStock(admin(), "AKARD02", "Ajuste", -2, "Merma"));

        List<MovimientoStock> deUno = servicio.listarMovimientos("AKARD02", 10);
        assertEquals(3, deUno.size(), "No mezcla movimientos de otros productos (incluye el stock inicial)");
        assertEquals("Merma", deUno.get(0).getMotivo(), "El más reciente va primero");
        assertEquals("Reposición", deUno.get(1).getMotivo());
        assertEquals("Stock inicial", deUno.get(2).getMotivo());

        assertEquals(1, servicio.listarMovimientos("AKARD02", 1).size());
        assertTrue(servicio.listarMovimientos("AKARD02", 0).isEmpty());
        assertTrue(servicio.listarMovimientos("AKARD02", -3).isEmpty());
        assertTrue(servicio.listarMovimientos("NOEXISTE99", 10).isEmpty());

        List<MovimientoStock> todos = servicio.listarMovimientos(null, 1000);
        for (int i = 1; i < todos.size(); i++) {
            assertTrue(todos.get(i - 1).getIdMovimiento() > todos.get(i).getIdMovimiento(),
                    "Los ids deben ir de mayor a menor en la posición " + i);
        }
        assertEquals(2, servicio.listarMovimientos(null, 2).size());
    }

    // ---------------------------------------------------------------- ajustarStock

    @Test
    @DisplayName("ajustarStock solo lo permiten Administrador y Almacén")
    void ajustarStockPermisos() {
        registrarProducto("AAJUS01", 10, 20.00);
        String sinPermiso = "No tienes permiso para registrar movimientos de stock.";

        assertEquals(sinPermiso, servicio.ajustarStock(consulta(), "AAJUS01", "Entrada", 5, "Reposición"));
        assertEquals(sinPermiso, servicio.ajustarStock(cliente("adm_cli_ajus"), "AAJUS01", "Entrada", 5, "Reposición"));
        assertEquals(sinPermiso, servicio.ajustarStock(null, "AAJUS01", "Entrada", 5, "Reposición"));
        assertEquals(10, stockDe("AAJUS01"));
        assertEquals(1, servicio.listarMovimientos("AAJUS01", 10).size(), "Solo el stock inicial: ningún rechazo deja movimiento");

        assertNull(servicio.ajustarStock(almacen(), "AAJUS01", "Entrada", 5, "Reposición"));
        assertNull(servicio.ajustarStock(admin(), "AAJUS01", "Entrada", 5, "Reposición"));
        assertEquals(20, stockDe("AAJUS01"));
    }

    @Test
    @DisplayName("Una Entrada suma al stock y debe ser mayor que cero")
    void entradaSumaYExigeCantidadPositiva() {
        registrarProducto("AAJUS02", 4, 20.00);

        assertEquals("Una entrada debe tener una cantidad mayor que cero.",
                servicio.ajustarStock(almacen(), "AAJUS02", "Entrada", 0, "Reposición"));
        assertEquals("Una entrada debe tener una cantidad mayor que cero.",
                servicio.ajustarStock(almacen(), "AAJUS02", "Entrada", -5, "Reposición"));
        assertEquals(4, stockDe("AAJUS02"));

        assertNull(servicio.ajustarStock(almacen(), "  aajus02 ", "entrada", 6, "Compra a proveedor"));
        assertEquals(10, stockDe("AAJUS02"));

        MovimientoStock m = servicio.listarMovimientos("AAJUS02", 5).get(0);
        assertEquals(MovimientoStock.ENTRADA, m.getTipo(), "El tipo se guarda con su escritura oficial");
        assertEquals(6, m.getCantidad());
        assertEquals("+6", m.getCantidadTexto());
        assertTrue(m.isEntrada());
        assertEquals(10, m.getStockResultante());
        assertEquals("Compra a proveedor", m.getMotivo());
        assertEquals("alm_prueba_prod", m.getUsuario());
    }

    @Test
    @DisplayName("Un Ajuste lleva signo, no puede ser 0 ni dejar el stock negativo, y actualiza el estado del producto")
    void ajusteConSignoSinStockNegativo() {
        registrarProducto("AAJUS03", 5, 20.00);

        assertEquals("El ajuste debe tener una cantidad distinta de cero.",
                servicio.ajustarStock(admin(), "AAJUS03", "Ajuste", 0, "Conteo"));

        String negativo = servicio.ajustarStock(admin(), "AAJUS03", "Ajuste", -6, "Conteo");
        assertNotNull(negativo);
        assertTrue(negativo.contains("negativo") && negativo.contains("stock actual: 5"), negativo);
        assertEquals(5, stockDe("AAJUS03"));
        assertEquals(1, servicio.listarMovimientos("AAJUS03", 10).size(), "Un ajuste rechazado no deja movimiento (solo el inicial)");

        assertNull(servicio.ajustarStock(admin(), "AAJUS03", "Ajuste", -2, "Rotura"));
        assertEquals(3, stockDe("AAJUS03"));
        MovimientoStock resta = servicio.listarMovimientos("AAJUS03", 1).get(0);
        assertEquals(MovimientoStock.AJUSTE, resta.getTipo());
        assertEquals(-2, resta.getCantidad());
        assertEquals(3, resta.getStockResultante());

        assertNull(servicio.ajustarStock(admin(), "AAJUS03", "Ajuste", 4, "Hallazgo en conteo"));
        assertEquals(7, stockDe("AAJUS03"));

        // Llegar exactamente a cero agota el producto; una entrada lo reactiva
        assertNull(servicio.ajustarStock(admin(), "AAJUS03", "Ajuste", -7, "Inventario a cero"));
        assertEquals(0, stockDe("AAJUS03"));
        assertEquals(Producto.AGOTADO, servicio.buscarPorCodigo("AAJUS03").getEstado());
        assertNull(servicio.ajustarStock(admin(), "AAJUS03", "Entrada", 1, "Reposición"));
        assertEquals(Producto.DISPONIBLE, servicio.buscarPorCodigo("AAJUS03").getEstado());
    }

    @Test
    @DisplayName("ajustarStock valida el tipo, el motivo, el producto y el desborde")
    void ajustarStockValidaDatos() {
        registrarProducto("AAJUS04", 10, 20.00);

        for (String tipo : new String[]{"Salida", "Devolución", "Venta", "", "   ", null}) {
            String error = servicio.ajustarStock(admin(), "AAJUS04", tipo, 1, "Motivo");
            assertNotNull(error, "Tipo '" + tipo + "'");
            assertTrue(error.startsWith("Tipo de movimiento no válido"), error);
        }
        assertEquals("Indica el motivo del movimiento.", servicio.ajustarStock(admin(), "AAJUS04", "Entrada", 1, null));
        assertEquals("Indica el motivo del movimiento.", servicio.ajustarStock(admin(), "AAJUS04", "Entrada", 1, "   "));
        assertTrue(servicio.ajustarStock(admin(), "AAJUS04", "Entrada", 1, "x".repeat(201)).contains("máximo 200"));
        assertEquals("Indica el producto.", servicio.ajustarStock(admin(), "  ", "Entrada", 1, "Motivo"));
        assertEquals("Indica el producto.", servicio.ajustarStock(admin(), null, "Entrada", 1, "Motivo"));
        assertEquals("El producto NOEXISTE99 no existe.",
                servicio.ajustarStock(admin(), "noexiste99", "Entrada", 1, "Motivo"));
        assertTrue(servicio.ajustarStock(admin(), "AAJUS04", "Entrada", Integer.MAX_VALUE, "Motivo")
                .contains("demasiado grande"));

        assertEquals(10, stockDe("AAJUS04"));
        assertEquals(1, servicio.listarMovimientos("AAJUS04", 10).size(), "Ningún rechazo deja movimientos (solo el inicial)");
        assertNull(servicio.ajustarStock(admin(), "AAJUS04", "Entrada", 1, "x".repeat(200)));
    }

    // ---------------------------------------------------------------- filtrarPedidos

    @Test
    @DisplayName("filtrarPedidos filtra por estado, por cliente (usuario o nombre) y combina los criterios")
    void filtrarPedidosPorEstadoYCliente() {
        registrarProducto("AFILT01", 20, 10.00);
        Usuario ana = clienteConNombre("zzf_ana", "Zeta Filtro Ana");
        Usuario beto = clienteConNombre("zzf_beto", "Zeta Filtro Beto");
        Pedido a1 = comprar(ana, "AFILT01", 1);
        Pedido a2 = comprar(ana, "AFILT01", 1);
        Pedido b1 = comprar(beto, "AFILT01", 1);
        assertNull(servicio.cambiarEstadoPedido(almacen(), a2.getIdPedido(), Pedido.EN_PREPARACION));

        List<Pedido> todos = servicio.filtrarPedidos(admin(), null, "zzf_", null, null);
        assertEquals(3, todos.size());
        assertEquals(b1.getIdPedido(), todos.get(0).getIdPedido(), "El más reciente va primero");
        assertEquals(a2.getIdPedido(), todos.get(1).getIdPedido());
        assertEquals(a1.getIdPedido(), todos.get(2).getIdPedido());

        // Estado
        List<Pedido> enPreparacion = servicio.filtrarPedidos(admin(), Pedido.EN_PREPARACION, "zzf_", null, null);
        assertEquals(1, enPreparacion.size());
        assertEquals(a2.getIdPedido(), enPreparacion.get(0).getIdPedido());
        assertEquals(2, servicio.filtrarPedidos(admin(), Pedido.CONFIRMADO, "zzf_", null, null).size());
        assertTrue(servicio.filtrarPedidos(admin(), Pedido.ANULADO, "zzf_", null, null).isEmpty());
        assertTrue(servicio.filtrarPedidos(admin(), "Inexistente", "zzf_", null, null).isEmpty());
        assertEquals(3, servicio.filtrarPedidos(admin(), "", "zzf_", null, null).size(), "Estado vacío = sin filtro");

        // Cliente: usuario o nombre, sin distinguir mayúsculas y recortando espacios
        assertEquals(2, servicio.filtrarPedidos(admin(), null, "ZZF_ANA", null, null).size());
        assertEquals(1, servicio.filtrarPedidos(admin(), null, "  zeta filtro beto ", null, null).size());
        assertEquals(2, servicio.filtrarPedidos(admin(), null, "filtro ana", null, null).size());
        assertTrue(servicio.filtrarPedidos(admin(), null, "zzf_nadie", null, null).isEmpty());

        // Combinados
        assertEquals(1, servicio.filtrarPedidos(admin(), Pedido.CONFIRMADO, "zzf_beto", null, null).size());
        assertTrue(servicio.filtrarPedidos(admin(), Pedido.EN_PREPARACION, "zzf_beto", null, null).isEmpty());
    }

    @Test
    @DisplayName("filtrarPedidos filtra por fechas con límites inclusivos y admite uno solo de los extremos")
    void filtrarPedidosPorFechas() {
        registrarProducto("AFILT02", 20, 10.00);
        comprar(clienteConNombre("zzfecha_ana", "Zeta Fecha Ana"), "AFILT02", 1);
        LocalDate hoy = LocalDate.now();

        assertEquals(1, servicio.filtrarPedidos(admin(), null, "zzfecha_", hoy, hoy).size(), "Ambos extremos son inclusivos");
        assertEquals(1, servicio.filtrarPedidos(admin(), null, "zzfecha_", hoy.minusDays(1), hoy.plusDays(1)).size());
        assertEquals(1, servicio.filtrarPedidos(admin(), null, "zzfecha_", hoy, null).size());
        assertEquals(1, servicio.filtrarPedidos(admin(), null, "zzfecha_", null, hoy).size());
        assertTrue(servicio.filtrarPedidos(admin(), null, "zzfecha_", hoy.plusDays(1), null).isEmpty());
        assertTrue(servicio.filtrarPedidos(admin(), null, "zzfecha_", null, hoy.minusDays(1)).isEmpty());
        assertTrue(servicio.filtrarPedidos(admin(), null, "zzfecha_", hoy.plusDays(1), hoy.plusDays(2)).isEmpty());

        // Los pedidos semilla (18/10/2026) caen en su día y fuera de los demás
        LocalDate diaSemilla = LocalDate.of(2026, 10, 18);
        List<Pedido> delDia = servicio.filtrarPedidos(admin(), null, null, diaSemilla, diaSemilla);
        assertTrue(delDia.stream().anyMatch(p -> p.getIdPedido() == 1));
        assertTrue(delDia.stream().anyMatch(p -> p.getIdPedido() == 2));
        for (Pedido p : delDia) {
            assertEquals(diaSemilla, p.getFechaHora().toLocalDate());
        }
        List<Pedido> otroDia = servicio.filtrarPedidos(admin(), null, null, diaSemilla.plusDays(1), diaSemilla.plusDays(1));
        assertTrue(otroDia.stream().noneMatch(p -> p.getIdPedido() == 1 || p.getIdPedido() == 2));
    }

    @Test
    @DisplayName("filtrarPedidos solo responde al personal: un cliente, un rol desconocido o null reciben una lista vacía")
    void filtrarPedidosSoloPersonal() {
        registrarProducto("AFILT03", 20, 10.00);
        Usuario ana = clienteConNombre("zzper_ana", "Zeta Personal Ana");
        comprar(ana, "AFILT03", 1);

        assertTrue(servicio.filtrarPedidos(ana, null, null, null, null).isEmpty(), "Un cliente no usa el filtro del personal");
        assertTrue(servicio.filtrarPedidos(null, null, null, null, null).isEmpty());
        assertTrue(servicio.filtrarPedidos(new Usuario(5000, "zzper_ana", "Rol Raro", "", "Auditor", true),
                null, null, null, null).isEmpty());
        for (Usuario personal : new Usuario[]{admin(), almacen(), consulta()}) {
            assertEquals(1, servicio.filtrarPedidos(personal, null, "zzper_", null, null).size(), personal.getRol());
        }
    }

    // ---------------------------------------------------------------- categorías

    @Test
    @DisplayName("Las categorías solo las gestiona el Administrador")
    void categoriasSoloAdministrador() {
        assertNull(servicio.crearCategoria(admin(), "CatPermisos", ""));
        int id = idCategoria("CatPermisos");
        String soloAdmin = "Solo el administrador puede gestionar las categorías.";

        for (Usuario actor : new Usuario[]{almacen(), consulta(), cliente("adm_cli_cat"), null}) {
            assertEquals(soloAdmin, servicio.crearCategoria(actor, "CatNoCreada", ""));
            assertEquals(soloAdmin, servicio.editarCategoria(actor, id, "CatRenombrada", ""));
            assertEquals(soloAdmin, servicio.cambiarEstadoCategoria(actor, id, false));
            assertEquals(soloAdmin, servicio.eliminarCategoria(actor, id));
        }
        assertNull(categoria("CatNoCreada"));
        assertNull(categoria("CatRenombrada"));
        assertTrue(categoria("CatPermisos").isActivo());
    }

    @Test
    @DisplayName("crearCategoria valida el nombre y la descripción, y el nombre es único sin distinguir mayúsculas")
    void crearCategoriaValidaYEsUnica() {
        assertEquals("El nombre de la categoría es obligatorio.", servicio.crearCategoria(admin(), null, ""));
        assertEquals("El nombre de la categoría es obligatorio.", servicio.crearCategoria(admin(), "   ", ""));
        assertEquals("El nombre de la categoría debe tener entre 2 y 50 caracteres.",
                servicio.crearCategoria(admin(), "A", ""));
        assertEquals("El nombre de la categoría debe tener entre 2 y 50 caracteres.",
                servicio.crearCategoria(admin(), "N".repeat(51), ""));
        assertEquals("La descripción admite como máximo 150 caracteres.",
                servicio.crearCategoria(admin(), "CatDescLarga", "d".repeat(151)));
        assertNull(categoria("CatDescLarga"));

        assertNull(servicio.crearCategoria(admin(), "  CatUnica  ", "Descripción opcional"));
        Categoria creada = categoria("CatUnica");
        assertNotNull(creada, "El nombre se guarda sin espacios sobrantes");
        assertEquals("Descripción opcional", creada.getDescripcion());
        assertTrue(creada.isActivo());
        assertEquals(0, creada.getCantidadProductos());
        assertTrue(creada.isEliminable());
        assertTrue(servicio.nombresCategorias().contains("CatUnica"));

        for (String repetido : new String[]{"CatUnica", "catunica", "CATUNICA", "  catUNICA "}) {
            String error = servicio.crearCategoria(admin(), repetido, "");
            assertNotNull(error, repetido);
            assertTrue(error.startsWith("Ya existe una categoría llamada"), error);
        }
        assertTrue(servicio.crearCategoria(admin(), "accesorios", "").startsWith("Ya existe una categoría"),
                "También choca con las categorías de origen");
        assertEquals(1, servicio.listarCategorias(false).stream().filter(c -> c.getNombre().equalsIgnoreCase("CatUnica")).count());
    }

    @Test
    @DisplayName("Renombrar una categoría actualiza la categoría de sus productos y valida duplicados")
    void editarCategoriaRenombraProductos() {
        assertNull(servicio.crearCategoria(admin(), "CatOriginal", "Antes"));
        assertNull(servicio.crearCategoria(admin(), "CatVecina", ""));
        servicio.registrar(new Producto("AEDIT01", "Producto en categoría", "CatOriginal", 10.00, 5));
        servicio.registrar(new Producto("AEDIT02", "Otro producto en categoría", "catoriginal", 10.00, 5));
        int id = idCategoria("CatOriginal");
        assertEquals(2, categoria("CatOriginal").getCantidadProductos());

        assertNull(servicio.editarCategoria(admin(), id, "  CatRenombrada ", "Después"));

        assertEquals("CatRenombrada", servicio.buscarPorCodigo("AEDIT01").getCategoria());
        assertEquals("CatRenombrada", servicio.buscarPorCodigo("AEDIT02").getCategoria());
        assertNull(categoria("CatOriginal"));
        Categoria renombrada = categoria("CatRenombrada");
        assertNotNull(renombrada);
        assertEquals("Después", renombrada.getDescripcion());
        assertEquals(2, renombrada.getCantidadProductos(), "Los productos siguen asociados");
        assertEquals(id, renombrada.getIdCategoria(), "Renombrar conserva el identificador");
        assertFalse(servicio.nombresCategorias().contains("CatOriginal"));
        assertTrue(servicio.nombresCategorias().contains("CatRenombrada"));
        assertEquals(2, servicio.buscarPorCategoria("CatRenombrada").stream()
                .filter(p -> p.getCodigo().startsWith("AEDIT")).count());

        // Conservar el nombre (aunque cambie la escritura) no es un duplicado; chocar con otra categoría sí
        assertNull(servicio.editarCategoria(admin(), id, "catrenombrada", "Solo otra descripción"));
        assertTrue(servicio.editarCategoria(admin(), id, "catvecina", "").startsWith("Ya existe una categoría"));
        assertEquals("El nombre de la categoría es obligatorio.", servicio.editarCategoria(admin(), id, "", ""));
        assertEquals("La categoría solicitada no existe.", servicio.editarCategoria(admin(), 987_654, "CatFantasma", ""));
        assertEquals("catrenombrada", servicio.buscarPorCodigo("AEDIT01").getCategoria());
    }

    @Test
    @DisplayName("Desactivar una categoría la saca de nombresCategorias() y de validar(); reactivarla la devuelve")
    void desactivarCategoriaLaSacaDeListasYValidacion() {
        assertNull(servicio.crearCategoria(admin(), "CatDesactivable", ""));
        int id = idCategoria("CatDesactivable");
        assertTrue(servicio.nombresCategorias().contains("CatDesactivable"));
        assertFalse(servicio.validar(formulario("AVALCAT01", "CatDesactivable"), true).containsKey("categoria"));

        assertNull(servicio.cambiarEstadoCategoria(admin(), id, false));

        assertFalse(servicio.nombresCategorias().contains("CatDesactivable"));
        assertTrue(servicio.listarCategorias(true).stream().noneMatch(c -> c.getNombre().equals("CatDesactivable")));
        Categoria inactiva = categoria("CatDesactivable");
        assertNotNull(inactiva, "listarCategorias(false) sigue mostrándola");
        assertFalse(inactiva.isActivo());
        assertTrue(servicio.validar(formulario("AVALCAT01", "CatDesactivable"), true).containsKey("categoria"),
                "No se puede registrar un producto en una categoría inactiva");

        assertNull(servicio.cambiarEstadoCategoria(admin(), id, true));
        assertTrue(servicio.nombresCategorias().contains("CatDesactivable"));
        assertFalse(servicio.validar(formulario("AVALCAT01", "CatDesactivable"), true).containsKey("categoria"));

        assertEquals("La categoría solicitada no existe.", servicio.cambiarEstadoCategoria(admin(), 987_654, false));
    }

    @Test
    @DisplayName("Eliminar una categoría con productos falla; sin productos funciona y desaparece de las listas")
    void eliminarCategoria() {
        assertNull(servicio.crearCategoria(admin(), "CatEliminable", ""));
        int id = idCategoria("CatEliminable");
        // Sin stock inicial no hay kardex y los productos se pueden borrar
        servicio.registrar(new Producto("AELIM01", "Producto atado", "CatEliminable", 10.00, 0));
        servicio.registrar(new Producto("AELIM02", "Otro producto atado", "CatEliminable", 10.00, 0));

        String error = servicio.eliminarCategoria(admin(), id);
        assertNotNull(error);
        assertTrue(error.contains("tiene 2 productos") && error.contains("desactívala"), error);
        Categoria sigue = categoria("CatEliminable");
        assertNotNull(sigue, "Sigue existiendo");
        assertEquals(2, sigue.getCantidadProductos());
        assertFalse(sigue.isEliminable());

        assertEquals(ProductoService.ResultadoEliminacion.ELIMINADO, servicio.eliminar("AELIM01"));
        assertTrue(servicio.eliminarCategoria(admin(), id).contains("tiene 1 producto;"));
        assertEquals(ProductoService.ResultadoEliminacion.ELIMINADO, servicio.eliminar("AELIM02"));
        assertTrue(categoria("CatEliminable").isEliminable());

        assertNull(servicio.eliminarCategoria(admin(), id));
        assertNull(categoria("CatEliminable"));
        assertFalse(servicio.nombresCategorias().contains("CatEliminable"));
        assertEquals("La categoría solicitada no existe.", servicio.eliminarCategoria(admin(), id));
        assertEquals("La categoría solicitada no existe.", servicio.eliminarCategoria(admin(), 987_654));
    }

    @Test
    @DisplayName("Las categorías de origen siguen activas, ordenadas por nombre y con su cantidad de productos")
    void categoriasDeOrigen() {
        List<String> nombres = servicio.nombresCategorias();
        for (String origen : ProductoService.CATEGORIAS) {
            assertTrue(nombres.contains(origen), "Falta la categoría de origen " + origen);
        }
        List<Categoria> lista = servicio.listarCategorias(true);
        Collator orden = Collator.getInstance(Locale.forLanguageTag("es-PE"));
        for (int i = 1; i < lista.size(); i++) {
            assertTrue(orden.compare(lista.get(i - 1).getNombre(), lista.get(i).getNombre()) <= 0,
                    "Orden alfabético en la posición " + i);
        }
        Categoria accesorios = lista.stream().filter(c -> c.getNombre().equals("Accesorios")).findFirst().orElseThrow();
        assertTrue(accesorios.getCantidadProductos() > 0);
        assertFalse(accesorios.isEliminable());
    }

    // ---------------------------------------------------------------- registro, edición y baja de productos

    @Test
    @DisplayName("Registrar un producto con stock deja una Entrada 'Stock inicial' en el kardex; sin stock no deja nada")
    void registrarConStockCreaEntradaStockInicial() {
        servicio.registrar(new Producto("AINI01", "Producto inicial", "Accesorios", 10.00, 7), admin());
        List<MovimientoStock> kardex = servicio.listarMovimientos("AINI01", 10);
        assertEquals(1, kardex.size());
        MovimientoStock m = kardex.get(0);
        assertEquals(MovimientoStock.ENTRADA, m.getTipo());
        assertEquals(7, m.getCantidad());
        assertEquals(7, m.getStockResultante());
        assertEquals("Stock inicial", m.getMotivo());
        assertEquals("adm_prueba_prod", m.getUsuario());

        servicio.registrar(new Producto("AINI02", "Producto sin stock", "Accesorios", 10.00, 0), admin());
        assertTrue(servicio.listarMovimientos("AINI02", 10).isEmpty(), "Sin stock inicial no hay movimiento");

        servicio.registrar(new Producto("AINI03", "Producto sin actor", "Accesorios", 10.00, 3));
        assertEquals("sistema", servicio.listarMovimientos("AINI03", 10).get(0).getUsuario());
    }

    @Test
    @DisplayName("actualizar cambia nombre, categoría y precio pero conserva el stock y mantiene el estado coherente")
    void actualizarNoCambiaStock() {
        registrarProducto("AACT01", 5, 20.00);
        Producto datos = new Producto("AACT01", "Nombre nuevo", "Redes", 33.50, 999);
        datos.setEstado(Producto.AGOTADO);

        assertTrue(servicio.actualizar(datos));

        Producto p = servicio.buscarPorCodigo("AACT01");
        assertEquals(5, p.getStock(), "El stock no se edita desde actualizar");
        assertEquals(Producto.DISPONIBLE, p.getEstado());
        assertEquals("Nombre nuevo", p.getNombre());
        assertEquals("Redes", p.getCategoria());
        assertEquals(33.50, p.getPrecio(), 0.001);
        assertEquals(1, servicio.listarMovimientos("AACT01", 10).size(), "Editar no deja movimientos");

        registrarProducto("AACT02", 0, 10.00);
        assertTrue(servicio.actualizar(new Producto("AACT02", "Sigue agotado", "Accesorios", 10.00, 50)));
        assertEquals(0, stockDe("AACT02"));
        assertEquals(Producto.AGOTADO, servicio.buscarPorCodigo("AACT02").getEstado());

        assertFalse(servicio.actualizar(new Producto("NOEXISTE9", "Fantasma", "Accesorios", 10.00, 1)));
    }

    @Test
    @DisplayName("Eliminar un producto con movimientos de inventario falla en memoria; sin kardex se elimina")
    void eliminarProductoConMovimientosFalla() {
        registrarProducto("AELP01", 3, 10.00);
        assertEquals(ProductoService.ResultadoEliminacion.CON_PEDIDOS, servicio.eliminar("AELP01"),
                "Tiene el movimiento de stock inicial");
        assertNotNull(servicio.buscarPorCodigo("AELP01"), "El producto sigue existiendo");

        registrarProducto("AELP02", 0, 10.00);
        assertEquals(ProductoService.ResultadoEliminacion.ELIMINADO, servicio.eliminar("AELP02"));

        registrarProducto("AELP03", 4, 10.00);
        comprar(cliente("adm_cli_elim"), "AELP03", 1);
        assertEquals(ProductoService.ResultadoEliminacion.CON_PEDIDOS, servicio.eliminar("AELP03"), "Tiene pedidos");
        assertEquals(ProductoService.ResultadoEliminacion.NO_ENCONTRADO, servicio.eliminar("NOEXISTE9"));
    }

    @Test
    @DisplayName("buscarPedido, listarPedidos y filtrarPedidos devuelven copias (incluido el pago) en memoria")
    void pedidosSeDevuelvenComoCopias() {
        registrarProducto("ACOP01", 10, 40.00);
        int id = comprar(cliente("adm_cli_copia"), "ACOP01", 1).getIdPedido();

        Pedido a = servicio.buscarPedido(id, admin());
        a.setEstado(Pedido.ENTREGADO);
        a.setNombreCliente("Alterado");
        a.getPago().setEstado(Pago.RECHAZADO);
        Pedido b = servicio.buscarPedido(id, admin());
        assertNotSame(a, b);
        assertNotSame(a.getPago(), b.getPago());
        assertEquals(Pedido.CONFIRMADO, b.getEstado(), "Modificar la copia no altera el pedido guardado");
        assertEquals(Pago.APROBADO, b.getPago().getEstado());
        assertEquals("Cliente adm_cli_copia", b.getNombreCliente());

        Pedido deLista = servicio.listarPedidos(admin()).stream().filter(p -> p.getIdPedido() == id).findFirst().orElseThrow();
        deLista.setEstado(Pedido.ANULADO);
        deLista.getPago().setEstado(Pago.REEMBOLSADO);
        Pedido deFiltro = servicio.filtrarPedidos(admin(), null, "adm_cli_copia", null, null).get(0);
        deFiltro.setEstado(Pedido.ANULADO);
        deFiltro.getPago().setEstado(Pago.REEMBOLSADO);
        assertEquals(Pedido.CONFIRMADO, pedido(id).getEstado());
        assertEquals(Pago.APROBADO, pedido(id).getPago().getEstado());

        // La copia tomada antes de cambiar el estado conserva el estado anterior (lo que audita AdminServlet)
        Pedido antes = servicio.buscarPedido(id, almacen());
        assertNull(servicio.cambiarEstadoPedido(almacen(), id, Pedido.EN_PREPARACION));
        assertEquals(Pedido.CONFIRMADO, antes.getEstado());
        assertEquals(Pedido.EN_PREPARACION, pedido(id).getEstado());
    }

    @Test
    @DisplayName("Al editar, validar ignora el stock y admite conservar la categoría actual aunque esté inactiva")
    void validarEditarIgnoraStockYAdmiteCategoriaActualInactiva() {
        assertNull(servicio.crearCategoria(admin(), "CatInactivaA", ""));
        assertNull(servicio.crearCategoria(admin(), "CatInactivaB", ""));
        servicio.registrar(new Producto("AVAL02", "Producto en categoría", "CatInactivaA", 10.00, 2));
        assertNull(servicio.cambiarEstadoCategoria(admin(), idCategoria("CatInactivaA"), false));
        assertNull(servicio.cambiarEstadoCategoria(admin(), idCategoria("CatInactivaB"), false));

        Map<String, String> form = formulario("AVAL02", "CatInactivaA");
        assertTrue(servicio.validar(form, false).isEmpty(), "Editar conserva la categoría actual aunque esté inactiva");
        assertTrue(servicio.validar(formulario("AVAL03", "CatInactivaA"), true).containsKey("categoria"),
                "Un producto nuevo no puede usar una categoría inactiva");
        assertTrue(servicio.validar(formulario("AVAL02", "CatInactivaB"), false).containsKey("categoria"),
                "Cambiar a otra categoría inactiva no vale");

        form.put("stock", "abc");
        assertFalse(servicio.validar(form, false).containsKey("stock"), "El stock no se valida al editar");
        assertTrue(servicio.validar(form, true).containsKey("stock"), "Al registrar sí");
        form.put("stock", "");
        assertEquals(0, servicio.construir(form).getStock(), "Sin stock en el formulario no falla");

        Map<String, String> largo = formulario("AVAL04", "Accesorios");
        largo.put("nombre", "N".repeat(121));
        assertTrue(servicio.validar(largo, true).containsKey("nombre"));
    }

    /** Formulario de producto válido salvo, quizá, la categoría indicada. */
    private static Map<String, String> formulario(String codigo, String categoria) {
        Map<String, String> form = new HashMap<>();
        form.put("codigo", codigo);
        form.put("nombre", "Producto de formulario");
        form.put("categoria", categoria);
        form.put("precio", "80.00");
        form.put("stock", "2");
        return form;
    }
}
