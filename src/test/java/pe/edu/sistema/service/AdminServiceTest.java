package pe.edu.sistema.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.edu.sistema.model.FilaReporte;
import pe.edu.sistema.model.Pago;
import pe.edu.sistema.model.Pedido;
import pe.edu.sistema.model.Producto;
import pe.edu.sistema.model.ResultadoCompra;
import pe.edu.sistema.model.ResumenVentas;
import pe.edu.sistema.model.SolicitudPago;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.util.ConexionBD;

/**
 * Pruebas de {@link AdminService} (panel, reportes de ventas y CSV) con el respaldo en memoria.
 *
 * El estado de {@link ProductoService} es compartido por todo el JVM, así que las pruebas comparan contra
 * lo que había antes (deltas) y usan productos, clientes y categorías propios cuando necesitan valores exactos.
 */
class AdminServiceTest {

    private static final String VISA = "4111 1111 1111 1111";
    private static final String MASTERCARD = "5555 5555 5555 4444";
    private static final String DNI = "70451236";
    private static final DateTimeFormatter MM_AA = DateTimeFormatter.ofPattern("MM/yy");
    private static final DateTimeFormatter DD_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final double EPSILON = 0.005;

    private final AdminService admin = AdminService.getInstancia();
    private final ProductoService productos = ProductoService.getInstancia();

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

    private static Usuario administrador() {
        return new Usuario(3001, "adm_prueba_rep", "Administrador de Prueba", "", Usuario.ROL_ADMIN, true);
    }

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

    private static SolicitudPago yape() {
        SolicitudPago s = new SolicitudPago();
        s.setMetodo(Pago.METODO_YAPE);
        s.setCelular("987654321");
        s.setCodigoYape(PasarelaPagoService.CODIGO_YAPE_PRUEBA);
        s.setTipoComprobante(Pago.FACTURA);
        s.setDocumento("20123456789");
        s.setRazonSocial("NEXORA S.A.C.");
        return s;
    }

    /** Quita un producto del catálogo en memoria sin pasar por eliminar (que lo impide si tiene pedidos). */
    @SuppressWarnings("unchecked")
    private void quitarDelCatalogo(String codigo) {
        try {
            java.lang.reflect.Field campo = ProductoService.class.getDeclaredField("productos");
            campo.setAccessible(true);
            synchronized (productos) {
                ((List<Producto>) campo.get(productos)).removeIf(p -> p.getCodigo().equals(codigo));
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private Producto registrarProducto(String codigo, String categoria, int stock, double precio) {
        productos.registrar(new Producto(codigo, "Producto " + codigo, categoria, precio, stock));
        return productos.buscarPorCodigo(codigo);
    }

    private Pedido comprar(Usuario cliente, String codigo, int cantidad, SolicitudPago pago) {
        ResultadoCompra r = productos.comprarConPago(cliente, codigo, cantidad, pago);
        assertTrue(r.isExito(), "La compra debía aprobarse: " + r.getError() + " " + r.getErrores());
        return r.getPedido();
    }

    private Pedido comprar(Usuario cliente, String codigo, int cantidad) {
        return comprar(cliente, codigo, cantidad, tarjeta(VISA));
    }

    /** Fila con esa etiqueta, o null si no está. */
    private static FilaReporte fila(List<FilaReporte> filas, String etiqueta) {
        return filas.stream().filter(f -> f.getEtiqueta().equals(etiqueta)).findFirst().orElse(null);
    }

    private static double monto(List<FilaReporte> filas, String etiqueta) {
        FilaReporte f = fila(filas, etiqueta);
        return f == null ? 0 : f.getMonto();
    }

    private static int cantidad(List<FilaReporte> filas, String etiqueta) {
        FilaReporte f = fila(filas, etiqueta);
        return f == null ? 0 : f.getCantidad();
    }

    private static double sumaPorcentajes(List<FilaReporte> filas) {
        return filas.stream().mapToDouble(FilaReporte::getPorcentaje).sum();
    }

    private static double sumaMontos(List<FilaReporte> filas) {
        return filas.stream().mapToDouble(FilaReporte::getMonto).sum();
    }

    private static int sumaCantidades(List<FilaReporte> filas) {
        return filas.stream().mapToInt(FilaReporte::getCantidad).sum();
    }

    // ---------------------------------------------------------------- resumen del panel

    @Test
    @DisplayName("Los ingresos y los pedidos del resumen cuentan las ventas y descuentan las anuladas")
    void resumenExcluyeAnulados() {
        registrarProducto("RRES01", "Accesorios", 20, 100.00);
        Usuario c = cliente("rep_cli_resumen");
        ResumenVentas antes = admin.resumen();

        Pedido pedido = comprar(c, "RRES01", 3);
        ResumenVentas conVenta = admin.resumen();
        assertEquals(antes.getPedidosTotal() + 1, conVenta.getPedidosTotal());
        assertEquals(antes.getPedidosHoy() + 1, conVenta.getPedidosHoy());
        assertEquals(antes.getPedidosMes() + 1, conVenta.getPedidosMes());
        assertEquals(antes.getIngresosTotal() + 300.00, conVenta.getIngresosTotal(), EPSILON);
        assertEquals(antes.getIngresosHoy() + 300.00, conVenta.getIngresosHoy(), EPSILON);
        assertEquals(antes.getIngresosMes() + 300.00, conVenta.getIngresosMes(), EPSILON);
        assertEquals(antes.getUnidadesVendidas() + 3, conVenta.getUnidadesVendidas());
        assertEquals(antes.getPedidosPorEstado().get(Pedido.CONFIRMADO) + 1,
                conVenta.getPedidosPorEstado().get(Pedido.CONFIRMADO));

        assertNull(productos.anularPedido(administrador(), pedido.getIdPedido(), "Prueba de reportes"));
        ResumenVentas anulada = admin.resumen();
        assertEquals(antes.getPedidosTotal(), anulada.getPedidosTotal(), "El pedido anulado no cuenta como venta");
        assertEquals(antes.getPedidosHoy(), anulada.getPedidosHoy());
        assertEquals(antes.getPedidosMes(), anulada.getPedidosMes());
        assertEquals(antes.getIngresosTotal(), anulada.getIngresosTotal(), EPSILON);
        assertEquals(antes.getIngresosHoy(), anulada.getIngresosHoy(), EPSILON);
        assertEquals(antes.getIngresosMes(), anulada.getIngresosMes(), EPSILON);
        assertEquals(antes.getUnidadesVendidas(), anulada.getUnidadesVendidas());
        // Pero sigue contándose en su estado
        assertEquals(antes.getPedidosPorEstado().get(Pedido.ANULADO) + 1, anulada.getPedidosPorEstado().get(Pedido.ANULADO));
        assertEquals(antes.getPedidosPorEstado().get(Pedido.CONFIRMADO), anulada.getPedidosPorEstado().get(Pedido.CONFIRMADO));
    }

    @Test
    @DisplayName("pedidosPorEstado incluye todos los estados en orden y pedidosPendientes suma Confirmado y En preparación")
    void pedidosPorEstadoIncluyeTodosLosEstados() {
        registrarProducto("RRES02", "Accesorios", 20, 10.00);
        Usuario c = cliente("rep_cli_estados");
        ResumenVentas antes = admin.resumen();
        comprar(c, "RRES02", 1);                                     // queda Confirmado
        int enPreparacion = comprar(c, "RRES02", 1).getIdPedido();
        assertNull(productos.cambiarEstadoPedido(administrador(), enPreparacion, Pedido.EN_PREPARACION));

        ResumenVentas resumen = admin.resumen();
        assertEquals(Pedido.ESTADOS, new ArrayList<>(resumen.getPedidosPorEstado().keySet()),
                "Todos los estados, aunque no tengan pedidos, en el orden de Pedido.ESTADOS");
        for (String estado : Pedido.ESTADOS) {
            assertNotNull(resumen.getPedidosPorEstado().get(estado), estado);
            assertTrue(resumen.getPedidosPorEstado().get(estado) >= 0);
        }
        assertEquals(antes.getPedidosPorEstado().get(Pedido.CONFIRMADO) + 1, resumen.getPedidosPorEstado().get(Pedido.CONFIRMADO));
        assertEquals(antes.getPedidosPorEstado().get(Pedido.EN_PREPARACION) + 1,
                resumen.getPedidosPorEstado().get(Pedido.EN_PREPARACION));
        assertEquals(resumen.getPedidosPorEstado().get(Pedido.CONFIRMADO)
                + resumen.getPedidosPorEstado().get(Pedido.EN_PREPARACION), resumen.getPedidosPendientes());

        // Las semillas aportan al menos un pedido Entregado y uno Enviado
        assertTrue(resumen.getPedidosPorEstado().get(Pedido.ENTREGADO) >= 1);
        assertTrue(resumen.getPedidosPorEstado().get(Pedido.ENVIADO) >= 1);
    }

    @Test
    @DisplayName("El resumen trae el top 5, el stock bajo, los últimos 8 pedidos y el ticket promedio")
    void resumenTopStockBajoUltimosYTicket() {
        registrarProducto("RRES03", "Accesorios", 40, 100_000.00);
        registrarProducto("RBAJO01", "Accesorios", 5, 10.00);       // justo en el umbral
        registrarProducto("RBAJO02", "Accesorios", 6, 10.00);       // por encima del umbral
        Pedido ultimo = comprar(cliente("rep_cli_top"), "RRES03", 4);

        ResumenVentas resumen = admin.resumen();

        List<FilaReporte> top = resumen.getTopProductos();
        assertTrue(top.size() <= 5);
        assertEquals("RRES03 · Producto RRES03", top.get(0).getEtiqueta(), "400.000 soles superan a cualquier otro producto");
        assertEquals(4, top.get(0).getCantidad());
        for (int i = 1; i < top.size(); i++) {
            assertTrue(top.get(i - 1).getMonto() >= top.get(i).getMonto(), "Orden por monto en la posición " + i);
        }

        assertTrue(resumen.getStockBajo().stream().anyMatch(p -> p.getCodigo().equals("RBAJO01")));
        assertTrue(resumen.getStockBajo().stream().noneMatch(p -> p.getCodigo().equals("RBAJO02")));
        List<Producto> bajos = admin.stockBajo();
        assertEquals(AdminService.UMBRAL_STOCK_BAJO, 5);
        for (int i = 0; i < bajos.size(); i++) {
            assertTrue(bajos.get(i).getStock() <= AdminService.UMBRAL_STOCK_BAJO, bajos.get(i).getCodigo());
            if (i > 0) {
                assertTrue(bajos.get(i - 1).getStock() <= bajos.get(i).getStock(), "De menor a mayor stock");
            }
        }
        assertTrue(bajos.stream().anyMatch(p -> p.getCodigo().equals("RBAJO01")));
        assertTrue(bajos.stream().noneMatch(p -> p.getCodigo().equals("RBAJO02")));

        List<Pedido> ultimos = resumen.getUltimosPedidos();
        assertTrue(ultimos.size() <= 8);
        assertEquals(ultimo.getIdPedido(), ultimos.get(0).getIdPedido(), "El pedido más reciente va primero");

        assertTrue(resumen.getPedidosTotal() > 0);
        assertEquals(Pago.redondear(resumen.getIngresosTotal() / resumen.getPedidosTotal()),
                resumen.getTicketPromedio(), EPSILON);
    }

    // ---------------------------------------------------------------- ventas por categoría, método, producto y día

    @Test
    @DisplayName("ventasPorCategoria suma unidades y montos de las ventas válidas, excluye las anuladas y el porcentaje suma 100")
    void ventasPorCategoria() {
        assertNull(productos.crearCategoria(administrador(), "CatRepVentas", ""));
        registrarProducto("RCAT01", "CatRepVentas", 50, 100.00);
        registrarProducto("RCAT02", "CatRepVentas", 50, 40.00);
        Usuario c = cliente("rep_cli_cat");
        LocalDate hoy = LocalDate.now();

        comprar(c, "RCAT01", 2);
        comprar(c, "RCAT02", 1);
        Pedido anulado = comprar(c, "RCAT01", 5);
        assertNull(productos.anularPedido(administrador(), anulado.getIdPedido(), "No cuenta"));

        List<FilaReporte> porCategoria = admin.ventasPorCategoria(hoy, hoy);
        FilaReporte fila = fila(porCategoria, "CatRepVentas");
        assertNotNull(fila, "La categoría propia debe aparecer");
        assertEquals(3, fila.getCantidad(), "2 + 1 unidades; las 5 del pedido anulado no cuentan");
        assertEquals(240.00, fila.getMonto(), EPSILON);
        assertEquals(100.0, sumaPorcentajes(porCategoria), 0.01);
        assertOrdenadoPorMonto(porCategoria);

        // Sin rango también la incluye, con los mismos valores
        assertEquals(240.00, monto(admin.ventasPorCategoria(null, null), "CatRepVentas"), EPSILON);
        // Fuera de su día no aparece
        assertNull(fila(admin.ventasPorCategoria(hoy.plusDays(1), hoy.plusDays(2)), "CatRepVentas"));
        assertNull(fila(admin.ventasPorCategoria(null, hoy.minusDays(1)), "CatRepVentas"));
    }

    @Test
    @DisplayName("Las ventas de un producto que ya no existe aparecen en 'Sin categoría'")
    void ventasDeProductoEliminadoVanASinCategoria() {
        registrarProducto("RCAT03", "Accesorios", 10, 25.00);
        comprar(cliente("rep_cli_sincat"), "RCAT03", 1);
        double antes = monto(admin.ventasPorCategoria(null, null), "Sin categoría");
        int unidadesAntes = cantidad(admin.ventasPorCategoria(null, null), "Sin categoría");

        // Un producto con pedidos ya no se puede eliminar; se simula su baja directa del catálogo en memoria
        assertEquals(ProductoService.ResultadoEliminacion.CON_PEDIDOS, productos.eliminar("RCAT03"));
        quitarDelCatalogo("RCAT03");

        List<FilaReporte> despues = admin.ventasPorCategoria(null, null);
        assertEquals(antes + 25.00, monto(despues, "Sin categoría"), EPSILON);
        assertEquals(unidadesAntes + 1, cantidad(despues, "Sin categoría"));
        assertEquals(100.0, sumaPorcentajes(despues), 0.01);
    }

    @Test
    @DisplayName("ventasPorMetodo agrupa por 'Tarjeta Visa', 'Tarjeta Mastercard' y 'Yape' y el porcentaje suma 100")
    void ventasPorMetodo() {
        registrarProducto("RMET01", "Accesorios", 30, 50.00);
        Usuario c = cliente("rep_cli_metodo");
        List<FilaReporte> antes = admin.ventasPorMetodo(null, null);

        comprar(c, "RMET01", 2, tarjeta(VISA));                   // 100
        comprar(c, "RMET01", 1, tarjeta(MASTERCARD));             // 50
        comprar(c, "RMET01", 3, yape());                          // 150
        Pedido anulado = comprar(c, "RMET01", 4, tarjeta(VISA));  // no cuenta
        assertNull(productos.anularPedido(administrador(), anulado.getIdPedido(), "No cuenta"));

        List<FilaReporte> despues = admin.ventasPorMetodo(null, null);
        assertEquals(monto(antes, "Tarjeta Visa") + 100.00, monto(despues, "Tarjeta Visa"), EPSILON);
        assertEquals(cantidad(antes, "Tarjeta Visa") + 1, cantidad(despues, "Tarjeta Visa"), "Cuenta pedidos, no unidades");
        assertEquals(monto(antes, "Tarjeta Mastercard") + 50.00, monto(despues, "Tarjeta Mastercard"), EPSILON);
        assertEquals(cantidad(antes, "Tarjeta Mastercard") + 1, cantidad(despues, "Tarjeta Mastercard"));
        assertEquals(monto(antes, "Yape") + 150.00, monto(despues, "Yape"), EPSILON);
        assertEquals(cantidad(antes, "Yape") + 1, cantidad(despues, "Yape"));
        assertEquals(100.0, sumaPorcentajes(despues), 0.01);
        assertOrdenadoPorMonto(despues);
        assertEquals(admin.numeroVentas(null, null), sumaCantidades(despues));
    }

    @Test
    @DisplayName("ventasPorProducto ordena por monto, respeta el límite y la barra del primero ocupa el 100 %")
    void ventasPorProducto() {
        registrarProducto("RPROD01", "Accesorios", 30, 300.00);
        registrarProducto("RPROD02", "Accesorios", 30, 10.00);
        Usuario c = cliente("rep_cli_producto");
        LocalDate hoy = LocalDate.now();
        comprar(c, "RPROD01", 2);                                  // 600
        comprar(c, "RPROD01", 1);                                  // +300 = 900
        comprar(c, "RPROD02", 3);                                  // 30

        List<FilaReporte> todas = admin.ventasPorProducto(hoy, hoy, 1_000);
        FilaReporte caro = fila(todas, "RPROD01 · Producto RPROD01");
        FilaReporte barato = fila(todas, "RPROD02 · Producto RPROD02");
        assertNotNull(caro);
        assertNotNull(barato);
        assertEquals(3, caro.getCantidad(), "Unidades vendidas");
        assertEquals(900.00, caro.getMonto(), EPSILON);
        assertEquals(3, barato.getCantidad());
        assertEquals(30.00, barato.getMonto(), EPSILON);
        assertTrue(todas.indexOf(caro) < todas.indexOf(barato), "Mayor monto primero");
        assertOrdenadoPorMonto(todas);
        assertEquals(100.0, sumaPorcentajes(todas), 0.01);
        assertEquals("100.0", todas.get(0).getAnchoBarra(), "La fila de mayor monto llena la barra");
        for (FilaReporte f : todas) {
            assertTrue(Double.parseDouble(f.getAnchoBarra()) <= 100.0);
        }

        List<FilaReporte> top2 = admin.ventasPorProducto(hoy, hoy, 2);
        assertEquals(2, top2.size());
        assertEquals(todas.get(0).getEtiqueta(), top2.get(0).getEtiqueta());
        assertEquals(1, admin.ventasPorProducto(hoy, hoy, 1).size());
        assertTrue(admin.ventasPorProducto(hoy, hoy, 0).isEmpty());
        assertTrue(admin.ventasPorProducto(hoy.plusDays(1), hoy.plusDays(5), 10).isEmpty());
    }

    @Test
    @DisplayName("ventasPorDia agrupa por día en orden cronológico, cuenta pedidos y el porcentaje suma 100")
    void ventasPorDia() {
        registrarProducto("RDIA01", "Accesorios", 30, 60.00);
        Usuario c = cliente("rep_cli_dia");
        LocalDate hoy = LocalDate.now();
        String etiquetaHoy = hoy.format(DD_MM_AAAA);
        List<FilaReporte> antes = admin.ventasPorDia(null, null);

        comprar(c, "RDIA01", 2);                                   // 120
        comprar(c, "RDIA01", 1);                                   // 60
        Pedido anulado = comprar(c, "RDIA01", 3);
        assertNull(productos.anularPedido(administrador(), anulado.getIdPedido(), "No cuenta"));

        List<FilaReporte> despues = admin.ventasPorDia(null, null);
        assertEquals(cantidad(antes, etiquetaHoy) + 2, cantidad(despues, etiquetaHoy), "Cuenta pedidos del día");
        assertEquals(monto(antes, etiquetaHoy) + 180.00, monto(despues, etiquetaHoy), EPSILON);
        assertEquals(100.0, sumaPorcentajes(despues), 0.01);

        LocalDate anterior = null;
        for (FilaReporte f : despues) {
            LocalDate dia = LocalDate.parse(f.getEtiqueta(), DD_MM_AAAA);
            if (anterior != null) {
                assertTrue(dia.isAfter(anterior), "Orden cronológico: " + f.getEtiqueta());
            }
            anterior = dia;
        }

        List<FilaReporte> soloHoy = admin.ventasPorDia(hoy, hoy);
        assertEquals(1, soloHoy.size());
        assertEquals(etiquetaHoy, soloHoy.get(0).getEtiqueta());
        assertEquals(100.0, soloHoy.get(0).getPorcentaje(), 0.01);
        assertTrue(admin.ventasPorDia(hoy.plusDays(1), null).isEmpty());
    }

    // ---------------------------------------------------------------- totales

    @Test
    @DisplayName("totalVentas y numeroVentas coinciden con los reportes y con el resumen, y los rangos son inclusivos")
    void totalesCoherentes() {
        registrarProducto("RTOT01", "Accesorios", 30, 80.00);
        Usuario c = cliente("rep_cli_totales");
        LocalDate hoy = LocalDate.now();
        double totalAntes = admin.totalVentas(hoy, hoy);
        int ventasAntes = admin.numeroVentas(hoy, hoy);

        comprar(c, "RTOT01", 2);                                   // 160
        Pedido anulado = comprar(c, "RTOT01", 1);
        assertNull(productos.anularPedido(administrador(), anulado.getIdPedido(), "No cuenta"));

        assertEquals(totalAntes + 160.00, admin.totalVentas(hoy, hoy), EPSILON);
        assertEquals(ventasAntes + 1, admin.numeroVentas(hoy, hoy));

        // Los reportes por día, categoría, método y producto suman lo mismo que los totales
        int ventas = admin.numeroVentas(null, null);
        double total = admin.totalVentas(null, null);
        List<FilaReporte> porDia = admin.ventasPorDia(null, null);
        List<FilaReporte> porMetodo = admin.ventasPorMetodo(null, null);
        List<FilaReporte> porProducto = admin.ventasPorProducto(null, null, Integer.MAX_VALUE);
        List<FilaReporte> porCategoria = admin.ventasPorCategoria(null, null);
        assertEquals(ventas, sumaCantidades(porDia));
        assertEquals(ventas, sumaCantidades(porMetodo));
        assertEquals(total, sumaMontos(porDia), 0.01 * porDia.size() + EPSILON);
        assertEquals(total, sumaMontos(porMetodo), 0.01 * porMetodo.size() + EPSILON);
        assertEquals(total, sumaMontos(porProducto), 0.01 * porProducto.size() + EPSILON);
        assertEquals(total, sumaMontos(porCategoria), 0.01 * porCategoria.size() + EPSILON);
        assertEquals(sumaCantidades(porProducto), sumaCantidades(porCategoria), "Ambos cuentan unidades");

        ResumenVentas resumen = admin.resumen();
        assertEquals(ventas, resumen.getPedidosTotal());
        assertEquals(total, resumen.getIngresosTotal(), EPSILON);

        // Los rangos particionan las ventas: antes de hoy + desde hoy = todas
        assertEquals(ventas, admin.numeroVentas(null, hoy.minusDays(1)) + admin.numeroVentas(hoy, null));
        assertEquals(total, admin.totalVentas(null, hoy.minusDays(1)) + admin.totalVentas(hoy, null), 0.011);

        // Sin ventas en el rango
        assertEquals(0, admin.numeroVentas(hoy.plusDays(1), hoy.plusDays(30)));
        assertEquals(0.0, admin.totalVentas(hoy.plusDays(1), hoy.plusDays(30)), 0.0);
        assertEquals(0, admin.numeroVentas(LocalDate.of(2000, 1, 1), LocalDate.of(2000, 12, 31)));
        assertTrue(admin.ventasPorCategoria(hoy.plusDays(1), hoy.plusDays(30)).isEmpty());
        assertTrue(admin.ventasPorMetodo(hoy.plusDays(1), hoy.plusDays(30)).isEmpty());
        assertTrue(admin.ventasPorDia(hoy.plusDays(1), hoy.plusDays(30)).isEmpty());
    }

    @Test
    @DisplayName("Los pedidos semilla (PED-0001 y PED-0002, del 18/10/2026) cuentan como ventas de su día")
    void pedidosSemillaCuentanEnSuDia() {
        LocalDate dia = LocalDate.of(2026, 10, 18);

        List<FilaReporte> porDia = admin.ventasPorDia(dia, dia);
        assertEquals(1, porDia.size());
        assertEquals("18/10/2026", porDia.get(0).getEtiqueta());
        assertTrue(porDia.get(0).getCantidad() >= 2);
        assertTrue(admin.totalVentas(dia, dia) >= 80.00 * 2 + 165.00);
    }

    // ---------------------------------------------------------------- CSV

    @Test
    @DisplayName("El CSV empieza con BOM y encabezado, separa con ';' y trae una fila con 14 columnas por pedido")
    void csvBomEncabezadoYColumnas() {
        registrarProducto("RCSV01", "Accesorios", 30, 1234.50);
        Pedido pedido = comprar(cliente("rep_cli_csv"), "RCSV01", 2);
        LocalDate hoy = LocalDate.now();

        String csv = admin.exportarVentasCsv(hoy, hoy);

        assertEquals('﻿', csv.charAt(0), "UTF-8 con BOM para que Excel lo abra bien");
        assertTrue(csv.endsWith("\n"));
        assertFalse(csv.contains("\r"));
        String[] lineas = csv.substring(1).split("\n");
        assertEquals("Pedido;Fecha;Cliente;Usuario;Código;Producto;Categoría;Cantidad;Precio unitario;Total;Método;"
                + "Comprobante;Estado del pedido;Estado del pago", lineas[0]);

        String fila = lineaDe(csv, pedido.getCodigoPedido());
        String[] celdas = fila.split(";", -1);
        assertEquals(14, celdas.length, fila);
        assertEquals(pedido.getCodigoPedido(), celdas[0]);
        assertTrue(celdas[1].matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}"), celdas[1]);
        assertEquals("Cliente rep_cli_csv", celdas[2]);
        assertEquals("rep_cli_csv", celdas[3]);
        assertEquals("RCSV01", celdas[4]);
        assertEquals("Producto RCSV01", celdas[5]);
        assertEquals("Accesorios", celdas[6]);
        assertEquals("2", celdas[7]);
        assertEquals("1234.50", celdas[8], "Punto decimal y sin separador de miles");
        assertEquals("2469.00", celdas[9]);
        assertEquals("Tarjeta Visa", celdas[10]);
        assertTrue(celdas[11].matches("B001-\\d{6}"), celdas[11]);
        assertEquals("Confirmado", celdas[12]);
        assertEquals("Pagado", celdas[13]);
    }

    @Test
    @DisplayName("El CSV incluye los pedidos anulados con su estado y pago reembolsado, y respeta el rango de fechas")
    void csvIncluyeAnuladosYRespetaRango() {
        registrarProducto("RCSV02", "Accesorios", 30, 50.00);
        Pedido pedido = comprar(cliente("rep_cli_csv_anulado"), "RCSV02", 1);
        assertNull(productos.anularPedido(administrador(), pedido.getIdPedido(), "Para el CSV"));
        LocalDate hoy = LocalDate.now();

        String fila = lineaDe(admin.exportarVentasCsv(hoy, hoy), pedido.getCodigoPedido());
        String[] celdas = fila.split(";", -1);
        assertEquals("Anulado", celdas[12]);
        assertEquals("Reembolsado", celdas[13]);

        String sinRango = admin.exportarVentasCsv(null, null);
        assertTrue(sinRango.contains("\nPED-0001;"), "Sin rango incluye también las semillas");
        assertTrue(sinRango.contains("\n" + pedido.getCodigoPedido() + ";"));

        String futuro = admin.exportarVentasCsv(hoy.plusDays(1), hoy.plusDays(2));
        assertEquals(1, futuro.substring(1).split("\n").length, "Fuera de rango solo queda el encabezado");

        // Los pedidos semilla son del 18/10/2026: están en su día y no en el de la prueba (salvo que coincidan)
        LocalDate diaSemilla = LocalDate.of(2026, 10, 18);
        String delDiaSemilla = admin.exportarVentasCsv(diaSemilla, diaSemilla);
        assertTrue(delDiaSemilla.contains("\nPED-0001;"));
        assertTrue(delDiaSemilla.contains("\nPED-0002;"));
        if (!hoy.equals(diaSemilla)) {
            assertFalse(delDiaSemilla.contains("\n" + pedido.getCodigoPedido() + ";"));
            assertFalse(admin.exportarVentasCsv(hoy, hoy).contains("\nPED-0001;"));
        }
    }

    @Test
    @DisplayName("El CSV neutraliza las celdas de texto que empiezan con = + - @ para evitar inyección de fórmulas")
    void csvNeutralizaFormulas() {
        registrarProducto("RCSV03", "Accesorios", 30, 10.00);
        productos.registrar(new Producto("RCSV04", "=CALC()", "Accesorios", 10.00, 30));
        Pedido igual = comprar(clienteConNombre("rep_csv_igual", "=1+1"), "RCSV03", 1);
        Pedido mas = comprar(clienteConNombre("rep_csv_mas", "+cmd|' /C calc'!A0"), "RCSV03", 1);
        Pedido menos = comprar(clienteConNombre("-rep_csv_menos", "-2+3"), "RCSV03", 1);
        Pedido arroba = comprar(clienteConNombre("rep_csv_arroba", "@SUM(1+1)"), "RCSV03", 1);
        Pedido producto = comprar(clienteConNombre("rep_csv_producto", "Cliente Normal"), "RCSV04", 1);
        LocalDate hoy = LocalDate.now();

        String csv = admin.exportarVentasCsv(hoy, hoy);

        assertEquals("'=1+1", celdasDe(csv, igual)[2]);
        assertEquals("'+cmd|' /C calc'!A0", celdasDe(csv, mas)[2]);
        assertEquals("'-2+3", celdasDe(csv, menos)[2]);
        assertEquals("'-rep_csv_menos", celdasDe(csv, menos)[3], "También se neutraliza la columna Usuario");
        assertEquals("'@SUM(1+1)", celdasDe(csv, arroba)[2]);
        assertEquals("'=CALC()", celdasDe(csv, producto)[5], "También se neutraliza la columna Producto");

        // Las celdas normales y las numéricas quedan intactas
        assertEquals("Cliente Normal", celdasDe(csv, producto)[2]);
        assertEquals("10.00", celdasDe(csv, producto)[8]);
        for (String linea : csv.substring(1).split("\n")) {
            for (String celda : linea.split(";", -1)) {
                assertFalse(celda.startsWith("=") || celda.startsWith("+") || celda.startsWith("@"),
                        "Celda sin neutralizar en: " + linea);
            }
        }
    }

    @Test
    @DisplayName("El CSV entrecomilla las celdas con ';' o comillas, duplicando las comillas")
    void csvEntrecomillaCeldasConSeparador() {
        registrarProducto("RCSV05", "Accesorios", 30, 10.00);
        Pedido pedido = comprar(clienteConNombre("rep_csv_comillas", "Pérez; \"Juan\" Carlos"), "RCSV05", 1);
        LocalDate hoy = LocalDate.now();

        String fila = lineaDe(admin.exportarVentasCsv(hoy, hoy), pedido.getCodigoPedido());

        assertTrue(fila.contains(";\"Pérez; \"\"Juan\"\" Carlos\";rep_csv_comillas;"), fila);
    }

    // ---------------------------------------------------------------- auxiliares del CSV

    /** Línea del CSV que empieza con el código del pedido. */
    private static String lineaDe(String csv, String codigoPedido) {
        for (String linea : csv.substring(1).split("\n")) {
            if (linea.startsWith(codigoPedido + ";")) {
                return linea;
            }
        }
        throw new AssertionError("El CSV no tiene una fila para " + codigoPedido);
    }

    private static String[] celdasDe(String csv, Pedido pedido) {
        String[] celdas = lineaDe(csv, pedido.getCodigoPedido()).split(";", -1);
        assertEquals(14, celdas.length, String.join(";", celdas));
        return celdas;
    }

    private static void assertOrdenadoPorMonto(List<FilaReporte> filas) {
        for (int i = 1; i < filas.size(); i++) {
            assertTrue(filas.get(i - 1).getMonto() >= filas.get(i).getMonto() - EPSILON,
                    "De mayor a menor monto en la posición " + i);
        }
    }
}
