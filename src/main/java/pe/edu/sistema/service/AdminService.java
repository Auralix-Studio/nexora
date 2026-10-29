package pe.edu.sistema.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import pe.edu.sistema.model.FilaReporte;
import pe.edu.sistema.model.Pago;
import pe.edu.sistema.model.Pedido;
import pe.edu.sistema.model.Producto;
import pe.edu.sistema.model.ResumenVentas;
import pe.edu.sistema.model.Usuario;

/**
 * Servicio: indicadores del panel de administración y reportes de ventas.
 * Calcula a partir de los pedidos y productos de {@link ProductoService}, así funciona igual con
 * SQL Server o con el respaldo en memoria. Solo cuentan como venta los pedidos con pago aprobado
 * que no están anulados ({@code Pedido.isVentaValida()}). Rangos de fechas inclusivos; null = sin límite.
 */
public final class AdminService {

    /** Stock igual o menor a este valor genera alerta en el panel e inventario. */
    public static final int UMBRAL_STOCK_BAJO = 5;

    private static final int TOP_PANEL = 5;
    private static final int ULTIMOS_PANEL = 8;
    private static final String SIN_CATEGORIA = "Sin categoría";

    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_CSV = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String ENCABEZADO_CSV = "Pedido;Fecha;Cliente;Usuario;Código;Producto;Categoría;Cantidad;"
            + "Precio unitario;Total;Método;Comprobante;Estado del pedido;Estado del pago";

    private static final AdminService INSTANCIA = new AdminService();

    private AdminService() {
    }

    public static AdminService getInstancia() {
        return INSTANCIA;
    }

    // ---------------------------------------------------------------- panel

    /** Indicadores del panel: ingresos de hoy, del mes y totales, pedidos por estado, top 5, stock bajo y últimos 8 pedidos. */
    public ResumenVentas resumen() {
        List<Pedido> todos = pedidos();
        LocalDate hoy = LocalDate.now();
        YearMonth mes = YearMonth.from(hoy);

        Map<String, Integer> porEstado = new LinkedHashMap<>();
        for (String estado : Pedido.ESTADOS) {
            porEstado.put(estado, 0);
        }
        double ingresosHoy = 0;
        double ingresosMes = 0;
        double ingresosTotal = 0;
        int pedidosHoy = 0;
        int pedidosMes = 0;
        int pedidosTotal = 0;
        int unidades = 0;
        List<Pedido> ventas = new ArrayList<>();
        for (Pedido p : todos) {
            if (p.getEstado() != null) {
                porEstado.merge(p.getEstado(), 1, Integer::sum);
            }
            if (!p.isVentaValida()) {
                continue;
            }
            ventas.add(p);
            double monto = montoDe(p);
            ingresosTotal += monto;
            pedidosTotal++;
            unidades += p.getCantidad();
            LocalDateTime fecha = p.getFechaHora();
            if (fecha != null) {
                LocalDate dia = fecha.toLocalDate();
                if (dia.equals(hoy)) {
                    ingresosHoy += monto;
                    pedidosHoy++;
                }
                if (YearMonth.from(dia).equals(mes)) {
                    ingresosMes += monto;
                    pedidosMes++;
                }
            }
        }

        ResumenVentas resumen = new ResumenVentas();
        resumen.setIngresosHoy(Pago.redondear(ingresosHoy));
        resumen.setIngresosMes(Pago.redondear(ingresosMes));
        resumen.setIngresosTotal(Pago.redondear(ingresosTotal));
        resumen.setPedidosHoy(pedidosHoy);
        resumen.setPedidosMes(pedidosMes);
        resumen.setPedidosTotal(pedidosTotal);
        resumen.setUnidadesVendidas(unidades);
        resumen.setPedidosPorEstado(porEstado);
        resumen.setTopProductos(filasPorProducto(ventas, TOP_PANEL));
        resumen.setStockBajo(stockBajo());
        resumen.setUltimosPedidos(new ArrayList<>(todos.subList(0, Math.min(ULTIMOS_PANEL, todos.size()))));
        return resumen;
    }

    /** Productos con stock menor o igual a {@link #UMBRAL_STOCK_BAJO}, de menor a mayor stock. */
    public List<Producto> stockBajo() {
        List<Producto> bajos = new ArrayList<>();
        for (Producto p : ProductoService.getInstancia().listar()) {
            if (p.getStock() <= UMBRAL_STOCK_BAJO) {
                bajos.add(p);
            }
        }
        bajos.sort(Comparator.comparingInt(Producto::getStock).thenComparing(Producto::getCodigo));
        return bajos;
    }

    // ---------------------------------------------------------------- reportes

    /** Ventas por día (etiqueta dd/MM/yyyy, cantidad = pedidos), en orden cronológico; solo días con ventas. */
    public List<FilaReporte> ventasPorDia(LocalDate desde, LocalDate hasta) {
        Map<LocalDate, double[]> porDia = new TreeMap<>();
        for (Pedido p : ventas(desde, hasta)) {
            if (p.getFechaHora() == null) {
                continue;
            }
            acumular(porDia.computeIfAbsent(p.getFechaHora().toLocalDate(), k -> new double[2]), 1, montoDe(p));
        }
        Map<String, double[]> etiquetadas = new LinkedHashMap<>();
        porDia.forEach((dia, datos) -> etiquetadas.put(dia.format(FORMATO_DIA), datos));
        return filas(new ArrayList<>(etiquetadas.entrySet()), Integer.MAX_VALUE);
    }

    /** Ventas por categoría del producto (cantidad = unidades), de mayor a menor monto. */
    public List<FilaReporte> ventasPorCategoria(LocalDate desde, LocalDate hasta) {
        Map<String, String> categorias = categoriasPorCodigo();
        Map<String, double[]> porCategoria = new HashMap<>();
        for (Pedido p : ventas(desde, hasta)) {
            String categoria = categorias.getOrDefault(p.getCodigoProducto(), SIN_CATEGORIA);
            acumular(porCategoria.computeIfAbsent(categoria, k -> new double[2]), p.getCantidad(), montoDe(p));
        }
        return filas(ordenadasPorMonto(porCategoria), Integer.MAX_VALUE);
    }

    /** Ventas por método de pago: etiqueta "Tarjeta Visa", "Tarjeta Mastercard", "Yape"… (cantidad = pedidos). */
    public List<FilaReporte> ventasPorMetodo(LocalDate desde, LocalDate hasta) {
        Map<String, double[]> porMetodo = new HashMap<>();
        for (Pedido p : ventas(desde, hasta)) {
            acumular(porMetodo.computeIfAbsent(etiquetaMetodo(p.getPago()), k -> new double[2]), 1, montoDe(p));
        }
        return filas(ordenadasPorMonto(porMetodo), Integer.MAX_VALUE);
    }

    /** Productos más vendidos por monto (etiqueta "P001 · nombre", cantidad = unidades). */
    public List<FilaReporte> ventasPorProducto(LocalDate desde, LocalDate hasta, int limite) {
        return filasPorProducto(ventas(desde, hasta), limite);
    }

    /** Suma de los montos de las ventas válidas del período. */
    public double totalVentas(LocalDate desde, LocalDate hasta) {
        double total = 0;
        for (Pedido p : ventas(desde, hasta)) {
            total += montoDe(p);
        }
        return Pago.redondear(total);
    }

    /** Número de ventas válidas del período. */
    public int numeroVentas(LocalDate desde, LocalDate hasta) {
        return ventas(desde, hasta).size();
    }

    /**
     * CSV (UTF-8 con BOM, separador ";") con una fila por pedido del período, incluidos los anulados:
     * pedido, fecha, cliente, usuario, código, producto, categoría, cantidad, precio unitario, total,
     * método, comprobante, estado del pedido, estado del pago. Neutraliza la inyección de fórmulas
     * (celdas que empiezan con = + - @ se prefijan con ').
     */
    public String exportarVentasCsv(LocalDate desde, LocalDate hasta) {
        Map<String, String> categorias = categoriasPorCodigo();
        List<Pedido> filas = new ArrayList<>();
        for (Pedido p : pedidos()) {
            if (enPeriodo(p, desde, hasta)) {
                filas.add(p);
            }
        }
        filas.sort(Comparator.comparingInt(Pedido::getIdPedido));

        StringBuilder csv = new StringBuilder("﻿").append(ENCABEZADO_CSV).append('\n');
        for (Pedido p : filas) {
            Pago pago = p.getPago();
            LocalDateTime fecha = p.getFechaHora();
            String[] celdas = {
                p.getCodigoPedido(),
                fecha != null ? fecha.format(FORMATO_CSV) : p.getFecha(),
                p.getNombreCliente(),
                p.getUsernameCliente(),
                p.getCodigoProducto(),
                p.getNombreProducto(),
                categorias.getOrDefault(p.getCodigoProducto(), SIN_CATEGORIA),
                String.valueOf(p.getCantidad()),
                importe(p.getPrecioUnitario()),
                importe(montoDe(p)),
                pago != null ? etiquetaMetodo(pago) : "",
                pago != null ? pago.getNumeroComprobante() : "",
                p.getEstado(),
                p.getEstadoPago()
            };
            for (int i = 0; i < celdas.length; i++) {
                // Cantidad, precio y total son números: no necesitan neutralización ni comillas
                boolean numerica = i == 7 || i == 8 || i == 9;
                csv.append(numerica ? celdas[i] : celdaCsv(celdas[i]));
                csv.append(i < celdas.length - 1 ? ';' : '\n');
            }
        }
        return csv.toString();
    }

    // ---------------------------------------------------------------- auxiliares

    /** Todos los pedidos, del más reciente al más antiguo, vistos con un usuario interno de rol Administrador. */
    private static List<Pedido> pedidos() {
        Usuario interno = new Usuario(0, "sistema", "Sistema", "", Usuario.ROL_ADMIN, true);
        return ProductoService.getInstancia().listarPedidos(interno);
    }

    /** Ventas válidas (pago aprobado, no anuladas) dentro del período. */
    private static List<Pedido> ventas(LocalDate desde, LocalDate hasta) {
        List<Pedido> ventas = new ArrayList<>();
        for (Pedido p : pedidos()) {
            if (p.isVentaValida() && enPeriodo(p, desde, hasta)) {
                ventas.add(p);
            }
        }
        return ventas;
    }

    private static boolean enPeriodo(Pedido p, LocalDate desde, LocalDate hasta) {
        if (desde == null && hasta == null) {
            return true;
        }
        LocalDateTime fecha = p.getFechaHora();
        if (fecha == null) {
            return false;
        }
        LocalDate dia = fecha.toLocalDate();
        return (desde == null || !dia.isBefore(desde)) && (hasta == null || !dia.isAfter(hasta));
    }

    /** Monto cobrado: el del pago; sin pago, el total del pedido. */
    private static double montoDe(Pedido p) {
        return p.getPago() != null ? p.getPago().getMonto() : Pago.redondear(p.getTotal());
    }

    private static Map<String, String> categoriasPorCodigo() {
        Map<String, String> categorias = new HashMap<>();
        for (Producto p : ProductoService.getInstancia().listar()) {
            categorias.put(p.getCodigo(), p.getCategoria());
        }
        return categorias;
    }

    private static String etiquetaMetodo(Pago pago) {
        if (pago == null) {
            return "Sin pago";
        }
        if (Pago.METODO_YAPE.equals(pago.getMetodo())) {
            return "Yape";
        }
        String marca = pago.getMarca() == null ? "" : pago.getMarca().trim();
        return marca.isEmpty() ? "Tarjeta" : "Tarjeta " + marca;
    }

    private static void acumular(double[] datos, double cantidad, double monto) {
        datos[0] += cantidad;
        datos[1] += monto;
    }

    private static List<FilaReporte> filasPorProducto(List<Pedido> ventas, int limite) {
        Map<String, double[]> porProducto = new HashMap<>();
        for (Pedido p : ventas) {
            String etiqueta = p.getCodigoProducto() + " · " + p.getNombreProducto();
            acumular(porProducto.computeIfAbsent(etiqueta, k -> new double[2]), p.getCantidad(), montoDe(p));
        }
        return filas(ordenadasPorMonto(porProducto), limite);
    }

    /** Filas de mayor a menor monto; a igual monto, por etiqueta. */
    private static List<Map.Entry<String, double[]>> ordenadasPorMonto(Map<String, double[]> acumulado) {
        List<Map.Entry<String, double[]>> filas = new ArrayList<>(acumulado.entrySet());
        filas.sort(Comparator.<Map.Entry<String, double[]>>comparingDouble(e -> -e.getValue()[1])
                .thenComparing(Map.Entry::getKey));
        return filas;
    }

    /**
     * Convierte filas acumuladas (cantidad, monto) en {@link FilaReporte}: el porcentaje es sobre el total de
     * todas las filas y el ancho de la barra sobre la fila de mayor monto; se devuelven como máximo {@code limite}.
     */
    private static List<FilaReporte> filas(List<Map.Entry<String, double[]>> acumuladas, int limite) {
        double total = 0;
        double maximo = 0;
        for (Map.Entry<String, double[]> e : acumuladas) {
            total += e.getValue()[1];
            maximo = Math.max(maximo, e.getValue()[1]);
        }
        List<FilaReporte> resultado = new ArrayList<>();
        for (Map.Entry<String, double[]> e : acumuladas) {
            if (resultado.size() >= limite) {
                break;
            }
            double monto = e.getValue()[1];
            resultado.add(new FilaReporte(e.getKey(), (int) Math.round(e.getValue()[0]), Pago.redondear(monto),
                    total > 0 ? monto * 100 / total : 0,
                    maximo > 0 ? monto * 100 / maximo : 0));
        }
        return resultado;
    }

    /** 2500.0 -> "2500.00" (punto decimal, sin separador de miles). */
    private static String importe(double monto) {
        return String.format(Locale.US, "%.2f", monto);
    }

    /** Celda de texto: neutraliza fórmulas (= + - @) y, si hace falta, la entrecomilla duplicando las comillas. */
    private static String celdaCsv(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        String celda = valor;
        if ("=+-@\t\r".indexOf(celda.charAt(0)) >= 0) {
            celda = "'" + celda;
        }
        if (celda.indexOf(';') >= 0 || celda.indexOf('"') >= 0 || celda.indexOf('\n') >= 0
                || celda.indexOf('\r') >= 0) {
            celda = '"' + celda.replace("\"", "\"\"") + '"';
        }
        return celda;
    }
}
