package pe.edu.sistema.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Collator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import pe.edu.sistema.model.Categoria;
import pe.edu.sistema.model.CategoriaResumen;
import pe.edu.sistema.model.MovimientoStock;
import pe.edu.sistema.model.Pago;
import pe.edu.sistema.model.Pedido;
import pe.edu.sistema.model.Producto;
import pe.edu.sistema.model.ResultadoCompra;
import pe.edu.sistema.model.SolicitudPago;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.util.ConexionBD;

/**
 * Servicio: lógica de negocio y persistencia híbrida del sistema NEXORA (Semana 04 - Semana 06).
 * Cuando Microsoft SQL Server / Azure SQL está activo, consulta y persiste en
 * las tablas dbo.producto, dbo.categoria, dbo.pedido y dbo.pago; si no hay conexión disponible,
 * opera automáticamente sobre las colecciones en memoria.
 */
public class ProductoService {

    public static final List<String> CATEGORIAS = List.of(
            "Computadoras", "Accesorios", "Monitores", "Impresoras", "Almacenamiento", "Redes");

    public static final List<String> ESTADOS = List.of(Producto.DISPONIBLE, Producto.AGOTADO);

    private static final String SQL_LISTAR =
            "SELECT p.codigo, p.nombre, c.nombre AS categoria, p.precio, p.stock, p.estado "
            + "FROM dbo.producto p "
            + "INNER JOIN dbo.categoria c ON p.id_categoria = c.id_categoria "
            + "ORDER BY p.codigo ASC";

    private static final String SQL_REGISTRAR =
            "INSERT INTO dbo.producto (codigo, nombre, id_categoria, precio, stock, estado) "
            + "VALUES (?, ?, (SELECT id_categoria FROM dbo.categoria WHERE LOWER(nombre) = LOWER(?)), ?, ?, ?)";

    private static final String SQL_ACTUALIZAR =
            "UPDATE dbo.producto SET nombre = ?, "
            + "id_categoria = (SELECT id_categoria FROM dbo.categoria WHERE LOWER(nombre) = LOWER(?)), "
            + "precio = ?, estado = ? WHERE codigo = ?";

    /** Lee el stock bloqueándolo hasta cerrar la transacción: editar un producto no puede pisar un movimiento concurrente. */
    private static final String SQL_STOCK_BLOQUEADO =
            "SELECT stock FROM dbo.producto WITH (UPDLOCK, HOLDLOCK) WHERE codigo = ?";

    /** Motivo del movimiento que deja el stock inicial al registrar un producto. */
    private static final String MOTIVO_STOCK_INICIAL = "Stock inicial";

    private static final String SQL_ELIMINAR =
            "DELETE FROM dbo.producto WHERE codigo = ?";

    /** Inserta el pedido y devuelve su id y la fecha asignada por la base de datos. */
    private static final String SQL_REGISTRAR_PEDIDO =
            "INSERT INTO dbo.pedido (id_usuario, codigo_producto, cantidad, precio_unitario) "
            + "OUTPUT INSERTED.id_pedido, INSERTED.fecha_pedido "
            + "VALUES ((SELECT TOP 1 id_usuario FROM dbo.usuario WHERE LOWER(username) = LOWER(?)), ?, ?, ?)";

    /** Pago simulado: nunca se almacena el número completo de la tarjeta ni el CVV. */
    private static final String SQL_REGISTRAR_PAGO =
            "INSERT INTO dbo.pago (id_pedido, metodo, marca, ultimos_digitos, titular, monto, estado, "
            + "codigo_autorizacion, codigo_operacion, tipo_comprobante, numero_comprobante, "
            + "documento_cliente, razon_social, fecha_pago) "
            + "OUTPUT INSERTED.id_pago "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** Pedidos con su pago (LEFT JOIN: un pedido sin pago registrado también se lista). */
    private static final String SQL_PEDIDOS =
            "SELECT ped.id_pedido, u.username, u.nombre_completo, ped.codigo_producto, "
            + "p.nombre AS producto, ped.cantidad, ped.precio_unitario, ped.fecha_pedido, "
            + "ped.estado AS estado_pedido, ped.fecha_actualizacion, "
            + "pg.id_pago, pg.metodo, pg.marca, pg.ultimos_digitos, pg.titular, pg.monto, "
            + "pg.estado AS estado_pago, pg.codigo_autorizacion, pg.codigo_operacion, "
            + "pg.tipo_comprobante, pg.numero_comprobante, pg.documento_cliente, pg.razon_social, "
            + "pg.fecha_pago "
            + "FROM dbo.pedido ped JOIN dbo.usuario u ON u.id_usuario = ped.id_usuario "
            + "JOIN dbo.producto p ON p.codigo = ped.codigo_producto "
            + "LEFT JOIN dbo.pago pg ON pg.id_pedido = ped.id_pedido ";

    /** Correlativo siguiente de la serie: el mayor emitido (últimos 6 dígitos) + 1, aunque falten números. */
    private static final String SQL_CORRELATIVO =
            "SELECT ISNULL(MAX(CAST(RIGHT(numero_comprobante, 6) AS INT)), 0) + 1 "
            + "FROM dbo.pago WITH (UPDLOCK, HOLDLOCK) WHERE tipo_comprobante = ?";

    /** Código de error de SQL Server por violación de FOREIGN KEY / REFERENCE. */
    private static final int ERROR_SQL_FK = 547;

    private static final String MSG_SIN_SERVICIO =
            "El servicio de pedidos no está disponible en este momento; no se realizó ningún cargo.";

    private static final String MSG_ERROR_BD =
            "No se pudo completar la operación en la base de datos; no se realizó ningún cambio. Intenta nuevamente.";

    private static final String MSG_PEDIDO_NO_EXISTE = "El pedido solicitado no existe.";

    private static final String MSG_CATEGORIA_NO_EXISTE = "La categoría solicitada no existe.";

    private static final String MSG_SOLO_ADMIN_CATEGORIAS = "Solo el administrador puede gestionar las categorías.";

    /** Longitud máxima del nombre de un producto (columna dbo.producto.nombre). */
    private static final int MAX_NOMBRE_PRODUCTO = 120;

    /** Longitud máxima del motivo de un movimiento (columna dbo.movimiento_stock.motivo). */
    private static final int MAX_MOTIVO = 200;

    /** Códigos de error de SQL Server por violación de restricción o índice UNIQUE. */
    private static final int ERROR_SQL_UNICO = 2627;
    private static final int ERROR_SQL_INDICE_UNICO = 2601;

    /** Pedido con bloqueo hasta cerrar la transacción (cambio de estado y anulación). */
    private static final String SQL_PEDIDO_BLOQUEADO =
            "SELECT estado, codigo_producto, cantidad FROM dbo.pedido WITH (UPDLOCK, HOLDLOCK) WHERE id_pedido = ?";

    /** Movimiento del kardex; el usuario se busca por username y puede quedar nulo ("sistema"). */
    private static final String SQL_MOVIMIENTO =
            "INSERT INTO dbo.movimiento_stock (codigo_producto, tipo, cantidad, stock_resultante, motivo, id_usuario) "
            + "VALUES (?, ?, ?, ?, ?, (SELECT TOP 1 id_usuario FROM dbo.usuario WHERE LOWER(username) = LOWER(?)))";

    private static final String SQL_MOVIMIENTOS =
            "SELECT TOP (?) m.id_movimiento, m.codigo_producto, p.nombre AS producto, m.tipo, m.cantidad, "
            + "m.stock_resultante, m.motivo, u.username, m.fecha "
            + "FROM dbo.movimiento_stock m JOIN dbo.producto p ON p.codigo = m.codigo_producto "
            + "LEFT JOIN dbo.usuario u ON u.id_usuario = m.id_usuario ";

    private static final String SQL_CATEGORIAS =
            "SELECT c.id_categoria, c.nombre, c.descripcion, c.activo, "
            + "(SELECT COUNT(*) FROM dbo.producto p WHERE p.id_categoria = c.id_categoria) AS productos "
            + "FROM dbo.categoria c ";

    /** Operación dentro de una transacción SQL: devuelve null para confirmar o un mensaje de error para revertir. */
    @FunctionalInterface
    private interface OperacionSql {
        String ejecutar(Connection cn) throws SQLException;
    }

    /** Resultado de {@link #eliminar(String)}. */
    public enum ResultadoEliminacion {
        /** El producto se eliminó. */
        ELIMINADO,
        /** No existe un producto con ese código. */
        NO_ENCONTRADO,
        /** Tiene pedidos o movimientos de inventario asociados y no se puede borrar. */
        CON_PEDIDOS,
        /** La base de datos está activa pero la operación falló por otro motivo. */
        ERROR
    }

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm");

    /** Orden alfabético en español (las tildes no alteran la posición). */
    private static final Collator ORDEN_TEXTO = Collator.getInstance(Locale.forLanguageTag("es-PE"));

    private static final ProductoService INSTANCIA = new ProductoService();

    private final List<Producto> productos = new ArrayList<>();
    private final List<Pedido> pedidos = new ArrayList<>();
    private final List<MovimientoStock> movimientos = new ArrayList<>();
    private final List<Categoria> categorias = new ArrayList<>();
    private final PasarelaPagoService pasarela = new PasarelaPagoService();

    private ProductoService() {
        productos.add(new Producto("P001", "Laptop Lenovo IdeaPad 3", "Computadoras", 2500.00, 10));
        productos.add(new Producto("P002", "Mouse Logitech M170", "Accesorios", 80.00, 25));
        productos.add(new Producto("P003", "Teclado Redragon Kumara", "Accesorios", 150.00, 0));
        productos.add(new Producto("P004", "Monitor LG 24\" IPS", "Monitores", 650.00, 8));
        productos.add(new Producto("P005", "Impresora Epson L3250", "Impresoras", 799.00, 4));
        productos.add(new Producto("P006", "SSD Kingston A400 480GB", "Almacenamiento", 165.00, 30));
        productos.add(new Producto("P007", "USB SanDisk Ultra 64GB", "Almacenamiento", 35.00, 60));
        productos.add(new Producto("P008", "Audífonos HyperX Cloud II", "Accesorios", 280.00, 0));
        productos.add(new Producto("P009", "Router TP-Link Archer C6", "Redes", 189.00, 12));
        productos.add(new Producto("P010", "Laptop HP 15 Ryzen 5", "Computadoras", 2890.00, 5));
        productos.add(new Producto("P011", "Webcam Logitech C920", "Accesorios", 320.00, 7));
        productos.add(new Producto("P012", "Switch TP-Link 8 puertos", "Redes", 95.00, 0));

        // Pedidos iniciales de muestra del rol Cliente, cada uno con su pago aprobado
        Pedido p1 = new Pedido(1, "cliente", "Andrea Paredes", "P002", "Mouse Logitech M170", 2, 80.00, "18/10/2026 · 18:30");
        p1.setPago(pagoSemilla(1, p1, "482915", "OP-20261018183012-4821", "B001-000001"));
        Pedido p2 = new Pedido(2, "cliente", "Andrea Paredes", "P006", "SSD Kingston A400 480GB", 1, 165.00, "18/10/2026 · 19:15");
        p2.setPago(pagoSemilla(2, p2, "730264", "OP-20261018191547-7305", "B001-000002"));
        p1.setEstado(Pedido.ENTREGADO);
        p1.setFechaActualizacion("20/10/2026 · 11:00");
        p2.setEstado(Pedido.ENVIADO);
        p2.setFechaActualizacion("19/10/2026 · 09:30");
        pedidos.add(p1);
        pedidos.add(p2);

        // Kardex inicial: las salidas por venta de los pedidos de muestra (el stock de arriba ya las descuenta)
        movimientos.add(movimientoSemilla(1, "P002", "Mouse Logitech M170", -2, 25, "Venta PED-0001", "18/10/2026 · 18:30"));
        movimientos.add(movimientoSemilla(2, "P006", "SSD Kingston A400 480GB", -1, 30, "Venta PED-0002", "18/10/2026 · 19:15"));

        // Mismas categorías y orden que sql/bd_nexora.sql (4.4)
        categorias.add(new Categoria(1, "Computadoras", "Laptops, equipos de escritorio y estaciones de trabajo", true));
        categorias.add(new Categoria(2, "Accesorios", "Periféricos: mouse, teclados, audífonos y webcams", true));
        categorias.add(new Categoria(3, "Monitores", "Pantallas LED, IPS y monitores profesionales", true));
        categorias.add(new Categoria(4, "Impresoras", "Impresoras multifuncionales y de tinta continua", true));
        categorias.add(new Categoria(5, "Almacenamiento", "Unidades de estado sólido SSD, discos duros y memorias USB", true));
        categorias.add(new Categoria(6, "Redes", "Routers inalámbricos, switches y equipos de conectividad", true));
    }

    /** Salida por venta del cliente de muestra. */
    private static MovimientoStock movimientoSemilla(int id, String codigo, String nombre, int cantidad,
            int stockResultante, String motivo, String fecha) {
        MovimientoStock m = new MovimientoStock();
        m.setIdMovimiento(id);
        m.setCodigoProducto(codigo);
        m.setNombreProducto(nombre);
        m.setTipo(MovimientoStock.SALIDA);
        m.setCantidad(cantidad);
        m.setStockResultante(stockResultante);
        m.setMotivo(motivo);
        m.setUsuario("cliente");
        m.setFecha(fecha);
        return m;
    }

    /** Pago aprobado de muestra (Visa 1111, boleta a DNI 70451236) para los pedidos semilla. */
    private static Pago pagoSemilla(int id, Pedido pedido, String autorizacion, String operacion, String comprobante) {
        Pago pago = new Pago();
        pago.setIdPago(id);
        pago.setIdPedido(pedido.getIdPedido());
        pago.setMetodo(Pago.METODO_TARJETA);
        pago.setMarca("Visa");
        pago.setUltimosDigitos("1111");
        pago.setTitular("Andrea Paredes");
        pago.setMonto(pedido.getTotal());
        pago.setEstado(Pago.APROBADO);
        pago.setCodigoAutorizacion(autorizacion);
        pago.setCodigoOperacion(operacion);
        pago.setTipoComprobante(Pago.BOLETA);
        pago.setNumeroComprobante(comprobante);
        pago.setDocumentoCliente("70451236");
        pago.setFecha(pedido.getFecha());
        return pago;
    }

    public static ProductoService getInstancia() {
        return INSTANCIA;
    }

    // ---------------------------------------------------------------- consultas

    public synchronized List<Producto> listar() {
        if (ConexionBD.estaDisponible()) {
            try (Connection cn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = cn.prepareStatement(SQL_LISTAR);
                 ResultSet rs = ps.executeQuery()) {
                List<Producto> listaBD = new ArrayList<>();
                while (rs.next()) {
                    Producto p = new Producto(
                            rs.getString("codigo"),
                            rs.getString("nombre"),
                            rs.getString("categoria"),
                            rs.getDouble("precio"),
                            rs.getInt("stock"));
                    p.setEstado(rs.getString("estado"));
                    listaBD.add(p);
                }
                return listaBD;
            } catch (SQLException ex) {
                ConexionBD.probarConexion();
            }
        }
        return new ArrayList<>(productos);
    }

    public synchronized int contar() {
        return listar().size();
    }

    public synchronized Producto buscarPorCodigo(String codigo) {
        String c = normalizarCodigo(codigo);
        for (Producto p : listar()) {
            if (p.getCodigo().equals(c)) {
                return p;
            }
        }
        return null;
    }

    public boolean existeCodigo(String codigo) {
        return buscarPorCodigo(codigo) != null;
    }

    public synchronized List<Producto> buscarPorNombre(String texto) {
        String t = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        List<Producto> resultado = new ArrayList<>();
        for (Producto p : listar()) {
            if (p.getNombre().toLowerCase(Locale.ROOT).contains(t)) {
                resultado.add(p);
            }
        }
        return resultado;
    }

    public synchronized List<Producto> buscarPorCategoria(String categoria) {
        List<Producto> resultado = new ArrayList<>();
        for (Producto p : listar()) {
            if (p.getCategoria().equalsIgnoreCase(categoria == null ? "" : categoria.trim())) {
                resultado.add(p);
            }
        }
        return resultado;
    }

    /** Búsqueda según el criterio elegido: codigo, nombre o categoria. */
    public List<Producto> buscar(String criterio, String valor) {
        if ("codigo".equals(criterio)) {
            Producto p = buscarPorCodigo(valor);
            return p == null ? new ArrayList<>() : new ArrayList<>(List.of(p));
        }
        if ("categoria".equals(criterio)) {
            return buscarPorCategoria(valor);
        }
        return buscarPorNombre(valor);
    }

    // ---------------------------------------------------------------- operaciones

    public synchronized boolean registrar(Producto p) {
        return registrar(p, null);
    }

    /**
     * Registra un producto. Con stock inicial mayor que cero deja en el kardex una Entrada "Stock inicial"
     * a nombre de {@code actor} (en SQL, en la misma transacción que el producto).
     *
     * @return false si SQL Server está activo pero no pudo guardar el producto (no se duplica en memoria)
     */
    public synchronized boolean registrar(Producto p, Usuario actor) {
        String username = actor == null ? null : actor.getUsername();
        if (ConexionBD.estaDisponible()) {
            String error = transaccion(cn -> {
                try (PreparedStatement ps = cn.prepareStatement(SQL_REGISTRAR)) {
                    ps.setString(1, p.getCodigo());
                    ps.setString(2, p.getNombre());
                    ps.setString(3, p.getCategoria());
                    ps.setDouble(4, p.getPrecio());
                    ps.setInt(5, p.getStock());
                    ps.setString(6, p.getEstado());
                    ps.executeUpdate();
                }
                if (p.getStock() > 0) {
                    insertarMovimientoBD(cn, p.getCodigo(), MovimientoStock.ENTRADA, p.getStock(), p.getStock(),
                            MOTIVO_STOCK_INICIAL, username);
                }
                return null;
            });
            if (error == null) {
                return true;
            }
            if (ConexionBD.estaDisponible()) {
                // La base sigue activa: no se toca la copia en memoria
                return false;
            }
        }
        productos.add(p);
        if (p.getStock() > 0) {
            registrarMovimientoMemoria(p, MovimientoStock.ENTRADA, p.getStock(), MOTIVO_STOCK_INICIAL, username, ahora());
        }
        return true;
    }

    /**
     * Actualiza nombre, categoría y precio. El stock no se toca aquí (se ajusta en Inventario): se conserva el
     * actual, leído bajo bloqueo en SQL, y el estado se mantiene coherente con él.
     *
     * @return false si el producto no existe o la base de datos no pudo guardar el cambio
     */
    public synchronized boolean actualizar(Producto datos) {
        String codigo = normalizarCodigo(datos.getCodigo());
        if (ConexionBD.estaDisponible()) {
            boolean[] actualizado = {false};
            String error = transaccion(cn -> {
                int stock;
                try (PreparedStatement ps = cn.prepareStatement(SQL_STOCK_BLOQUEADO)) {
                    ps.setString(1, codigo);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return "El producto " + codigo + " no existe.";
                        }
                        stock = rs.getInt("stock");
                    }
                }
                try (PreparedStatement ps = cn.prepareStatement(SQL_ACTUALIZAR)) {
                    ps.setString(1, datos.getNombre());
                    ps.setString(2, datos.getCategoria());
                    ps.setDouble(3, datos.getPrecio());
                    ps.setString(4, stock > 0 ? Producto.DISPONIBLE : Producto.AGOTADO);
                    ps.setString(5, codigo);
                    actualizado[0] = ps.executeUpdate() > 0;
                }
                return null;
            });
            if (error == null) {
                return actualizado[0];
            }
            if (ConexionBD.estaDisponible()) {
                // La base sigue activa: no se toca la copia en memoria
                return false;
            }
        }
        for (Producto actual : productos) {
            if (actual.getCodigo().equals(codigo)) {
                actual.setNombre(datos.getNombre());
                actual.setCategoria(datos.getCategoria());
                actual.setPrecio(datos.getPrecio());
                actual.setEstado(actual.getStock() > 0 ? Producto.DISPONIBLE : Producto.AGOTADO);
                return true;
            }
        }
        return false;
    }

    /**
     * Elimina un producto. Si tiene pedidos o movimientos de inventario no se borra
     * ({@link ResultadoEliminacion#CON_PEDIDOS}): en SQL lo impide la clave foránea y en memoria se aplica
     * la misma regla. Solo si la conexión se pierde se opera en memoria.
     */
    public synchronized ResultadoEliminacion eliminar(String codigo) {
        String c = normalizarCodigo(codigo);
        if (ConexionBD.estaDisponible()) {
            try (Connection cn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = cn.prepareStatement(SQL_ELIMINAR)) {
                ps.setString(1, c);
                return ps.executeUpdate() > 0 ? ResultadoEliminacion.ELIMINADO : ResultadoEliminacion.NO_ENCONTRADO;
            } catch (SQLException ex) {
                if (ex.getErrorCode() == ERROR_SQL_FK) {
                    return ResultadoEliminacion.CON_PEDIDOS;
                }
                if (ConexionBD.probarConexion()) {
                    // La base sigue activa: no se toca la copia en memoria
                    return ResultadoEliminacion.ERROR;
                }
            }
        }
        Producto existente = productoEnMemoria(c);
        if (existente == null) {
            return ResultadoEliminacion.NO_ENCONTRADO;
        }
        boolean conHistorial = pedidos.stream().anyMatch(ped -> c.equals(ped.getCodigoProducto()))
                || movimientos.stream().anyMatch(m -> c.equals(m.getCodigoProducto()));
        if (conHistorial) {
            return ResultadoEliminacion.CON_PEDIDOS;
        }
        productos.remove(existente);
        return ResultadoEliminacion.ELIMINADO;
    }

    // ---------------------------------------------------------------- compras con pago

    /**
     * Compra con pago: valida stock y formulario, autoriza en la pasarela simulada y, solo si el
     * pago es aprobado, descuenta stock y registra pedido y pago en una misma transacción.
     */
    public synchronized ResultadoCompra comprarConPago(Usuario cliente, String codigo, int cantidad,
            SolicitudPago solicitud) {
        if (cliente == null || !cliente.isActivo() || !cliente.isPuedeComprar()) {
            return ResultadoCompra.fallo("La compra en línea está disponible para cuentas de cliente.");
        }
        if (cantidad < 1) {
            return ResultadoCompra.fallo("La cantidad solicitada debe ser mayor que cero.");
        }
        // Modo de persistencia con el que empieza la compra: no puede cambiar a mitad de camino
        boolean modoSql = ConexionBD.estaDisponible();
        Producto p = buscarPorCodigo(codigo);
        if (ConexionBD.estaDisponible() != modoSql) {
            return ResultadoCompra.fallo(MSG_SIN_SERVICIO);
        }
        if (p == null) {
            return ResultadoCompra.fallo("El producto solicitado no existe.");
        }
        if (p.getStock() < cantidad) {
            return ResultadoCompra.fallo("Stock insuficiente. Disponible: " + p.getStock() + " unidades.");
        }

        SolicitudPago datosPago = solicitud != null ? solicitud : new SolicitudPago();
        Map<String, String> errores = pasarela.validar(datosPago);
        if (!errores.isEmpty()) {
            return ResultadoCompra.invalido(errores);
        }

        double monto = Pago.redondear(p.getPrecio() * cantidad);
        Pago pago = pasarela.autorizar(datosPago, monto);
        if (!pago.isAprobado()) {
            return ResultadoCompra.fallo("Pago rechazado: " + pago.getMotivoRechazo());
        }
        if (ConexionBD.estaDisponible() != modoSql) {
            // Cambió la base de datos durante el pago: no se registra en el otro almacén
            return ResultadoCompra.fallo(MSG_SIN_SERVICIO);
        }
        return modoSql
                ? registrarCompraEnBD(cliente, p.getCodigo(), cantidad, pago, datosPago)
                : registrarCompraEnMemoria(cliente, p.getCodigo(), cantidad, pago, datosPago);
    }

    /** Respaldo en memoria: descuenta stock, crea el pedido y le asocia el pago con su comprobante. */
    private ResultadoCompra registrarCompraEnMemoria(Usuario cliente, String codigo, int cantidad, Pago pago, SolicitudPago solicitud) {
        Producto p = null;
        for (Producto actual : productos) {
            if (actual.getCodigo().equals(codigo)) {
                p = actual;
                break;
            }
        }
        if (p == null) {
            return ResultadoCompra.fallo("El producto solicitado no existe.");
        }
        if (p.getStock() < cantidad) {
            return ResultadoCompra.fallo("El stock cambió durante el pago; el cargo fue anulado");
        }
        p.setStock(p.getStock() - cantidad);
        p.setEstado(p.getStock() > 0 ? Producto.DISPONIBLE : Producto.AGOTADO);

        int idPedido = pedidos.stream().mapToInt(Pedido::getIdPedido).max().orElse(0) + 1;
        int idPago = pedidos.stream().filter(ped -> ped.getPago() != null)
                .mapToInt(ped -> ped.getPago().getIdPago()).max().orElse(0) + 1;
        long ultimoCorrelativo = pedidos.stream()
                .filter(ped -> ped.getPago() != null
                        && pago.getTipoComprobante().equals(ped.getPago().getTipoComprobante()))
                .mapToLong(ped -> correlativoDe(ped.getPago().getNumeroComprobante()))
                .max().orElse(0);

        Pedido pedido = new Pedido(idPedido, cliente.getUsername(), cliente.getNombreCompleto(),
                p.getCodigo(), p.getNombre(), cantidad, p.getPrecio(), pago.getFecha());
        pago.setIdPago(idPago);
        pago.setIdPedido(idPedido);
        pago.setNumeroComprobante(numeroComprobante(pago, ultimoCorrelativo + 1));
        pedido.setPago(pago);
        aplicarInfoEnvio(pedido, solicitud);
        pedidos.add(pedido);
        registrarMovimientoMemoria(p, MovimientoStock.SALIDA, -cantidad, "Venta " + pedido.getCodigoPedido(),
                cliente.getUsername(), pago.getFecha());
        return ResultadoCompra.exito(pedido);
    }

    /**
     * Stock, pedido y pago pertenecen a una sola transacción: si algo falla, no queda ningún
     * rastro parcial (el cargo simulado se considera anulado).
     */
    private ResultadoCompra registrarCompraEnBD(Usuario cliente, String codigo, int cantidad, Pago pago, SolicitudPago solicitud) {
        try (Connection cn = ConexionBD.obtenerConexion()) {
            cn.setAutoCommit(false);
            try {
                String nombre;
                double precio;
                int stockAnterior;
                // Bloquea el producto hasta confirmar o revertir toda la operación.
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT nombre, precio, stock FROM dbo.producto WITH (UPDLOCK, HOLDLOCK) WHERE codigo = ?")) {
                    ps.setString(1, codigo);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            cn.rollback();
                            return ResultadoCompra.fallo("El producto solicitado no existe.");
                        }
                        if (rs.getInt("stock") < cantidad) {
                            cn.rollback();
                            return ResultadoCompra.fallo("El stock cambió durante el pago; el cargo fue anulado");
                        }
                        nombre = rs.getString("nombre");
                        precio = rs.getDouble("precio");
                        stockAnterior = rs.getInt("stock");
                    }
                }
                if (Math.abs(Pago.redondear(precio * cantidad) - pago.getMonto()) > 0.005) {
                    cn.rollback();
                    return ResultadoCompra.fallo("El precio cambió durante el pago; el cargo fue anulado");
                }
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE dbo.producto SET stock = stock - ?, estado = CASE WHEN stock - ? > 0 THEN 'Disponible' ELSE 'Agotado' END WHERE codigo = ?")) {
                    ps.setInt(1, cantidad);
                    ps.setInt(2, cantidad);
                    ps.setString(3, codigo);
                    if (ps.executeUpdate() != 1) throw new SQLException("Producto no actualizado");
                }

                int idPedido;
                Timestamp fecha;
                try (PreparedStatement ps = cn.prepareStatement(SQL_REGISTRAR_PEDIDO)) {
                    ps.setString(1, cliente.getUsername());
                    ps.setString(2, codigo);
                    ps.setInt(3, cantidad);
                    ps.setDouble(4, precio);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Pedido no registrado");
                        idPedido = rs.getInt("id_pedido");
                        fecha = rs.getTimestamp("fecha_pedido");
                    }
                }
                // Kardex: la salida por la venta se confirma o se revierte junto con el pedido
                insertarMovimientoBD(cn, codigo, MovimientoStock.SALIDA, -cantidad, stockAnterior - cantidad,
                        "Venta " + codigoPedido(idPedido), cliente.getUsername());

                long correlativo;
                try (PreparedStatement ps = cn.prepareStatement(SQL_CORRELATIVO)) {
                    ps.setString(1, pago.getTipoComprobante());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        correlativo = rs.getLong(1);
                    }
                }
                String numero = numeroComprobante(pago, correlativo);

                int idPago;
                try (PreparedStatement ps = cn.prepareStatement(SQL_REGISTRAR_PAGO)) {
                    ps.setInt(1, idPedido);
                    ps.setString(2, pago.getMetodo());
                    ps.setString(3, pago.getMarca());
                    ps.setString(4, pago.getUltimosDigitos());
                    ps.setString(5, pago.getTitular());
                    ps.setDouble(6, pago.getMonto());
                    ps.setString(7, pago.getEstado());
                    ps.setString(8, pago.getCodigoAutorizacion());
                    ps.setString(9, pago.getCodigoOperacion());
                    ps.setString(10, pago.getTipoComprobante());
                    ps.setString(11, numero);
                    ps.setString(12, pago.getDocumentoCliente());
                    ps.setString(13, pago.getRazonSocial());
                    ps.setTimestamp(14, fecha);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Pago no registrado");
                        idPago = rs.getInt("id_pago");
                    }
                }
                cn.commit();

                String fechaTexto = fecha.toLocalDateTime().format(FORMATO_FECHA);
                pago.setIdPago(idPago);
                pago.setIdPedido(idPedido);
                pago.setNumeroComprobante(numero);
                pago.setFecha(fechaTexto);
                Pedido pedido = new Pedido(idPedido, cliente.getUsername(), cliente.getNombreCompleto(),
                        codigo, nombre, cantidad, precio, fechaTexto);
                pedido.setFechaHora(fecha.toLocalDateTime());
                pedido.setPago(pago);
                aplicarInfoEnvio(pedido, solicitud);
                return ResultadoCompra.exito(pedido);
            } catch (SQLException ex) {
                cn.rollback();
                return ResultadoCompra.fallo("No se pudo confirmar el pedido; el cargo fue anulado. Intenta nuevamente.");
            }
        } catch (SQLException ex) {
            return ResultadoCompra.fallo("No se pudo confirmar el pedido; el cargo fue anulado. Intenta nuevamente.");
        }
    }

    /** B001-000001 para boletas y F001-000001 para facturas. */
    private static String numeroComprobante(Pago pago, long correlativo) {
        return String.format(pago.isFactura() ? "F001-%06d" : "B001-%06d", correlativo);
    }

    /** Correlativo numérico de un comprobante (últimos 6 dígitos de B001-000007); 0 si no se puede leer. */
    private static long correlativoDe(String numeroComprobante) {
        if (numeroComprobante == null || numeroComprobante.length() < 6) {
            return 0;
        }
        try {
            return Long.parseLong(numeroComprobante.substring(numeroComprobante.length() - 6));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public record InfoEnvio(
            String tipoEnvio,
            String departamento,
            String distrito,
            String direccion,
            String referencia,
            String telefonoContacto,
            String correoContacto,
            String codigoSeguimiento,
            String notasPedido
    ) implements java.io.Serializable {}

    private static final Map<Integer, InfoEnvio> ENVIOS_POR_PEDIDO = new ConcurrentHashMap<>();

    private static void aplicarInfoEnvio(Pedido pedido, SolicitudPago solicitud) {
        if (pedido == null) return;
        boolean retiro = solicitud != null && "RETIRO".equalsIgnoreCase(solicitud.getTipoEnvio());
        String tipo = retiro ? "Retiro en Tienda (Almacén Central NEXORA)" : "Envío a Domicilio Express";
        String depto = solicitud != null && solicitud.getDepartamento() != null && !solicitud.getDepartamento().isBlank()
                ? solicitud.getDepartamento().trim() : "Lima";
        String dist = solicitud != null && solicitud.getDistrito() != null && !solicitud.getDistrito().isBlank()
                ? solicitud.getDistrito().trim() : (retiro ? "San Isidro" : "Lima Cercado");
        String dir = retiro
                ? "Av. República de Panamá 3545, San Isidro (Almacén Central NEXORA)"
                : (solicitud != null && solicitud.getDireccion() != null && !solicitud.getDireccion().isBlank()
                    ? solicitud.getDireccion().trim() : "Dirección registrada");
        String ref = solicitud != null && solicitud.getReferencia() != null ? solicitud.getReferencia().trim() : "";
        String tel = solicitud != null && solicitud.getTelefonoContacto() != null ? solicitud.getTelefonoContacto().trim() : "";
        String correo = solicitud != null && solicitud.getCorreoComprobante() != null ? solicitud.getCorreoComprobante().trim() : "";
        String notas = solicitud != null && solicitud.getNotasPedido() != null ? solicitud.getNotasPedido().trim() : "";
        String tracking = String.format("NEX-TRK-%06d", (pedido.getIdPedido() * 379 + 5421) % 900000 + 100000);

        pedido.setTipoEnvio(tipo);
        pedido.setDepartamento(depto);
        pedido.setDistrito(dist);
        pedido.setDireccionEnvio(dir);
        pedido.setReferenciaEnvio(ref);
        pedido.setTelefonoContacto(tel);
        pedido.setCorreoContacto(correo);
        pedido.setCodigoSeguimiento(tracking);
        pedido.setNotasPedido(notas);

        ENVIOS_POR_PEDIDO.put(pedido.getIdPedido(), new InfoEnvio(tipo, depto, dist, dir, ref, tel, correo, tracking, notas));
    }

    private static void aplicarInfoEnvioGuardada(Pedido pedido) {
        if (pedido == null) return;
        InfoEnvio info = ENVIOS_POR_PEDIDO.get(pedido.getIdPedido());
        if (info != null) {
            pedido.setTipoEnvio(info.tipoEnvio());
            pedido.setDepartamento(info.departamento());
            pedido.setDistrito(info.distrito());
            pedido.setDireccionEnvio(info.direccion());
            pedido.setReferenciaEnvio(info.referencia());
            pedido.setTelefonoContacto(info.telefonoContacto());
            pedido.setCorreoContacto(info.correoContacto());
            pedido.setCodigoSeguimiento(info.codigoSeguimiento());
            pedido.setNotasPedido(info.notasPedido());
        } else {
            String tracking = String.format("NEX-TRK-%06d", (pedido.getIdPedido() * 379 + 5421) % 900000 + 100000);
            pedido.setCodigoSeguimiento(tracking);
        }
    }

    // ---------------------------------------------------------------- pedidos

    /**
     * Pedido con su pago, visible para el cliente que lo realizó o para el personal.
     *
     * @return null si no existe o el usuario no puede verlo
     */
    public synchronized Pedido buscarPedido(int idPedido, Usuario solicitante) {
        if (solicitante == null) return null;
        Pedido encontrado = null;
        if (ConexionBD.estaDisponible()) {
            try (Connection cn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = cn.prepareStatement(SQL_PEDIDOS + "WHERE ped.id_pedido = ?")) {
                ps.setInt(1, idPedido);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) encontrado = pedidoDesde(rs);
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("No se pudo consultar el pedido.", ex);
            }
        } else {
            for (Pedido ped : pedidos) {
                if (ped.getIdPedido() == idPedido) {
                    encontrado = copiar(ped);
                    break;
                }
            }
        }
        if (encontrado != null) {
            aplicarInfoEnvioGuardada(encontrado);
        }
        return encontrado != null && puedeVer(encontrado, solicitante) ? encontrado : null;
    }

    /** Personal con acceso a todos los pedidos: Administrador, Almacén o Consulta (roles reconocidos). */
    private static boolean esPersonal(Usuario usuario) {
        return usuario.isAdministrador() || usuario.isAlmacen() || usuario.isConsulta();
    }

    /**
     * El personal ve cualquier pedido y un Cliente solo los suyos; cualquier otro rol, ninguno.
     */
    private static boolean puedeVer(Pedido pedido, Usuario usuario) {
        if (esPersonal(usuario)) {
            return true;
        }
        return usuario.isCliente() && usuario.getUsername() != null
                && usuario.getUsername().equalsIgnoreCase(pedido.getUsernameCliente());
    }

    /** Lee el historial del mismo origen que el inventario y filtra por cliente (en memoria, devuelve copias). */
    public synchronized List<Pedido> listarPedidos(Usuario u) {
        if (u == null) return new ArrayList<>();
        boolean personal = esPersonal(u);
        if (!personal && !u.isCliente()) return new ArrayList<>();
        if (ConexionBD.estaDisponible()) {
            String sql = SQL_PEDIDOS
                    + (personal ? "" : "WHERE LOWER(u.username) = LOWER(?) ")
                    + "ORDER BY ped.id_pedido DESC";
            try (Connection cn = ConexionBD.obtenerConexion(); PreparedStatement ps = cn.prepareStatement(sql)) {
                if (!personal) ps.setString(1, u.getUsername());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Pedido> resultado = new ArrayList<>();
                    while (rs.next()) resultado.add(pedidoDesde(rs));
                    return resultado;
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("No se pudo consultar el historial de pedidos.", ex);
            }
        }
        List<Pedido> resultado = new ArrayList<>();
        for (Pedido ped : pedidos) {
            if (puedeVer(ped, u)) resultado.add(copiar(ped));
        }
        // Mismo orden que el historial en SQL (ORDER BY id_pedido DESC), sea cual sea el orden de inserción
        resultado.sort(Comparator.comparingInt(Pedido::getIdPedido).reversed());
        return resultado;
    }

    /**
     * Copia independiente del pedido y de su pago: quien la recibe no puede alterar el respaldo en memoria
     * (igual que las listas de categorías y movimientos).
     */
    private static Pedido copiar(Pedido o) {
        Pedido c = new Pedido(o.getIdPedido(), o.getUsernameCliente(), o.getNombreCliente(), o.getCodigoProducto(),
                o.getNombreProducto(), o.getCantidad(), o.getPrecioUnitario(), o.getFecha());
        c.setEstado(o.getEstado());
        c.setFechaActualizacion(o.getFechaActualizacion());
        c.setFechaHora(o.getFechaHora());
        c.setTipoEnvio(o.getTipoEnvio());
        c.setDepartamento(o.getDepartamento());
        c.setDistrito(o.getDistrito());
        c.setDireccionEnvio(o.getDireccionEnvio());
        c.setReferenciaEnvio(o.getReferenciaEnvio());
        c.setTelefonoContacto(o.getTelefonoContacto());
        c.setCorreoContacto(o.getCorreoContacto());
        c.setCodigoSeguimiento(o.getCodigoSeguimiento());
        c.setNotasPedido(o.getNotasPedido());
        Pago pago = o.getPago();
        if (pago != null) {
            Pago cp = new Pago();
            cp.setIdPago(pago.getIdPago());
            cp.setIdPedido(pago.getIdPedido());
            cp.setMetodo(pago.getMetodo());
            cp.setMarca(pago.getMarca());
            cp.setUltimosDigitos(pago.getUltimosDigitos());
            cp.setTitular(pago.getTitular());
            cp.setMonto(pago.getMonto());
            cp.setEstado(pago.getEstado());
            cp.setMotivoRechazo(pago.getMotivoRechazo());
            cp.setCodigoAutorizacion(pago.getCodigoAutorizacion());
            cp.setCodigoOperacion(pago.getCodigoOperacion());
            cp.setTipoComprobante(pago.getTipoComprobante());
            cp.setNumeroComprobante(pago.getNumeroComprobante());
            cp.setDocumentoCliente(pago.getDocumentoCliente());
            cp.setRazonSocial(pago.getRazonSocial());
            cp.setFecha(pago.getFecha());
            c.setPago(cp);
        }
        return c;
    }

    /** Fila de {@link #SQL_PEDIDOS} -> pedido con su pago (null si el pedido no tiene pago). */
    private static Pedido pedidoDesde(ResultSet rs) throws SQLException {
        LocalDateTime fechaPedido = rs.getTimestamp("fecha_pedido").toLocalDateTime();
        Pedido pedido = new Pedido(rs.getInt("id_pedido"), rs.getString("username"),
                rs.getString("nombre_completo"), rs.getString("codigo_producto"), rs.getString("producto"),
                rs.getInt("cantidad"), rs.getDouble("precio_unitario"), fechaPedido.format(FORMATO_FECHA));
        pedido.setFechaHora(fechaPedido);
        pedido.setEstado(rs.getString("estado_pedido"));
        Timestamp actualizacion = rs.getTimestamp("fecha_actualizacion");
        if (actualizacion != null) {
            pedido.setFechaActualizacion(actualizacion.toLocalDateTime().format(FORMATO_FECHA));
        }
        int idPago = rs.getInt("id_pago");
        if (!rs.wasNull()) {
            Pago pago = new Pago();
            pago.setIdPago(idPago);
            pago.setIdPedido(pedido.getIdPedido());
            pago.setMetodo(rs.getString("metodo"));
            pago.setMarca(rs.getString("marca"));
            pago.setUltimosDigitos(rs.getString("ultimos_digitos"));
            pago.setTitular(rs.getString("titular"));
            pago.setMonto(rs.getDouble("monto"));
            pago.setEstado(rs.getString("estado_pago"));
            pago.setCodigoAutorizacion(rs.getString("codigo_autorizacion"));
            pago.setCodigoOperacion(rs.getString("codigo_operacion"));
            pago.setTipoComprobante(rs.getString("tipo_comprobante"));
            pago.setNumeroComprobante(rs.getString("numero_comprobante"));
            pago.setDocumentoCliente(rs.getString("documento_cliente"));
            pago.setRazonSocial(rs.getString("razon_social"));
            pago.setFecha(rs.getTimestamp("fecha_pago").toLocalDateTime().format(FORMATO_FECHA));
            pedido.setPago(pago);
        }
        return pedido;
    }

    // ---------------------------------------------------------------- administración de pedidos
    // Convención de los métodos que modifican: devuelven null si tuvo éxito o el mensaje de error.

    /**
     * Pedidos de todos los clientes para el personal, del más reciente al más antiguo.
     * Filtros opcionales (null o vacío = sin filtro): estado exacto, texto en nombre o usuario del
     * cliente, y rango de fechas inclusivo.
     *
     * @return lista vacía si el solicitante no es personal
     */
    public synchronized List<Pedido> filtrarPedidos(Usuario solicitante, String estado, String cliente,
            LocalDate desde, LocalDate hasta) {
        if (solicitante == null || !esPersonal(solicitante)) {
            return new ArrayList<>();
        }
        String estadoBuscado = texto(estado);
        String clienteBuscado = texto(cliente).toLowerCase(Locale.ROOT);
        List<Pedido> resultado = new ArrayList<>();
        for (Pedido ped : listarPedidos(solicitante)) {
            if (!estadoBuscado.isEmpty() && !estadoBuscado.equals(ped.getEstado())) {
                continue;
            }
            if (!clienteBuscado.isEmpty() && !contiene(ped.getNombreCliente(), clienteBuscado)
                    && !contiene(ped.getUsernameCliente(), clienteBuscado)) {
                continue;
            }
            if ((desde != null || hasta != null) && !enRango(ped.getFechaHora(), desde, hasta)) {
                continue;
            }
            resultado.add(ped);
        }
        return resultado;
    }

    /**
     * Avanza un pedido al siguiente estado del flujo (Confirmado → En preparación → Enviado → Entregado).
     * Solo Administrador y Almacén; el nuevo estado debe ser exactamente {@link Pedido#getSiguienteEstado()}.
     */
    public synchronized String cambiarEstadoPedido(Usuario actor, int idPedido, String nuevoEstado) {
        if (actor == null || !actor.isPuedeGestionarPedidos()) {
            return "No tienes permiso para cambiar el estado de los pedidos.";
        }
        String destino = texto(nuevoEstado);
        if (destino.isEmpty()) {
            return "Indica el nuevo estado del pedido.";
        }
        if (ConexionBD.estaDisponible()) {
            return transaccion(cn -> {
                String actual;
                try (PreparedStatement ps = cn.prepareStatement(SQL_PEDIDO_BLOQUEADO)) {
                    ps.setInt(1, idPedido);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return MSG_PEDIDO_NO_EXISTE;
                        }
                        actual = rs.getString("estado");
                    }
                }
                String error = errorTransicion(idPedido, actual, destino);
                if (error != null) {
                    return error;
                }
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE dbo.pedido SET estado = ?, fecha_actualizacion = SYSDATETIME() WHERE id_pedido = ?")) {
                    ps.setString(1, destino);
                    ps.setInt(2, idPedido);
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Pedido no actualizado");
                    }
                }
                return null;
            });
        }
        Pedido pedido = pedidoEnMemoria(idPedido);
        if (pedido == null) {
            return MSG_PEDIDO_NO_EXISTE;
        }
        String error = errorTransicion(idPedido, pedido.getEstado(), destino);
        if (error != null) {
            return error;
        }
        pedido.setEstado(destino);
        pedido.setFechaActualizacion(ahora());
        return null;
    }

    /**
     * Anula un pedido Confirmado o En preparación: estado Anulado, pago Reembolsado, devuelve el stock y
     * registra un movimiento Devolución, todo en una transacción. Solo Administrador; motivo obligatorio.
     */
    public synchronized String anularPedido(Usuario actor, int idPedido, String motivo) {
        if (actor == null || !actor.isPuedeAnularPedidos()) {
            return "Solo el administrador puede anular pedidos.";
        }
        String razon = texto(motivo);
        if (razon.isEmpty()) {
            return "Indica el motivo de la anulación.";
        }
        if (razon.length() > MAX_MOTIVO) {
            return "El motivo admite como máximo " + MAX_MOTIVO + " caracteres.";
        }
        if (ConexionBD.estaDisponible()) {
            return transaccion(cn -> {
                Pedido ref = new Pedido();
                ref.setIdPedido(idPedido);
                String codigoProducto;
                int cantidad;
                try (PreparedStatement ps = cn.prepareStatement(SQL_PEDIDO_BLOQUEADO)) {
                    ps.setInt(1, idPedido);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return MSG_PEDIDO_NO_EXISTE;
                        }
                        ref.setEstado(rs.getString("estado"));
                        codigoProducto = rs.getString("codigo_producto");
                        cantidad = rs.getInt("cantidad");
                    }
                }
                if (!ref.isAnulable()) {
                    return errorNoAnulable(ref);
                }
                int stock;
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT stock FROM dbo.producto WITH (UPDLOCK, HOLDLOCK) WHERE codigo = ?")) {
                    ps.setString(1, codigoProducto);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Producto del pedido no encontrado");
                        }
                        stock = rs.getInt("stock");
                    }
                }
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE dbo.pedido SET estado = ?, fecha_actualizacion = SYSDATETIME() WHERE id_pedido = ?")) {
                    ps.setString(1, Pedido.ANULADO);
                    ps.setInt(2, idPedido);
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Pedido no actualizado");
                    }
                }
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE dbo.pago SET estado = ? WHERE id_pedido = ? AND estado = ?")) {
                    ps.setString(1, Pago.REEMBOLSADO);
                    ps.setInt(2, idPedido);
                    ps.setString(3, Pago.APROBADO);
                    ps.executeUpdate();
                }
                int nuevoStock = stock + cantidad;
                actualizarStockBD(cn, codigoProducto, nuevoStock);
                insertarMovimientoBD(cn, codigoProducto, MovimientoStock.DEVOLUCION, cantidad, nuevoStock,
                        motivoAnulacion(ref, razon), actor.getUsername());
                return null;
            });
        }
        Pedido pedido = pedidoEnMemoria(idPedido);
        if (pedido == null) {
            return MSG_PEDIDO_NO_EXISTE;
        }
        if (!pedido.isAnulable()) {
            return errorNoAnulable(pedido);
        }
        Producto producto = productoEnMemoria(pedido.getCodigoProducto());
        if (producto == null) {
            return "No se puede anular: el producto " + pedido.getCodigoProducto() + " ya no existe en el inventario.";
        }
        pedido.setEstado(Pedido.ANULADO);
        pedido.setFechaActualizacion(ahora());
        if (pedido.getPago() != null && pedido.getPago().isAprobado()) {
            pedido.getPago().setEstado(Pago.REEMBOLSADO);
        }
        producto.setStock(producto.getStock() + pedido.getCantidad());
        producto.setEstado(producto.getStock() > 0 ? Producto.DISPONIBLE : Producto.AGOTADO);
        registrarMovimientoMemoria(producto, MovimientoStock.DEVOLUCION, pedido.getCantidad(),
                motivoAnulacion(pedido, razon), actor.getUsername(), ahora());
        return null;
    }

    private static String motivoAnulacion(Pedido pedido, String razon) {
        return recortar("Anulación " + pedido.getCodigoPedido() + ": " + razon, MAX_MOTIVO);
    }

    /** null si el pedido puede pasar de {@code actual} a {@code destino}; si no, el motivo. */
    private static String errorTransicion(int idPedido, String actual, String destino) {
        Pedido ref = new Pedido();
        ref.setIdPedido(idPedido);
        ref.setEstado(actual == null ? Pedido.CONFIRMADO : actual);
        String siguiente = ref.getSiguienteEstado();
        if (siguiente == null) {
            return ref.isAnulado()
                    ? "El pedido " + ref.getCodigoPedido() + " está anulado y no admite cambios de estado."
                    : "El pedido " + ref.getCodigoPedido() + " ya está " + ref.getEstado()
                            + " y no tiene un estado siguiente.";
        }
        if (!siguiente.equals(destino)) {
            return "El pedido " + ref.getCodigoPedido() + " está " + ref.getEstado()
                    + " y solo puede pasar a " + siguiente + ".";
        }
        return null;
    }

    private static String errorNoAnulable(Pedido pedido) {
        if (pedido.isAnulado()) {
            return "El pedido " + pedido.getCodigoPedido() + " ya está anulado.";
        }
        return "El pedido " + pedido.getCodigoPedido() + " está " + pedido.getEstado()
                + " y ya no puede anularse: solo se anulan pedidos Confirmados o En preparación.";
    }

    private Pedido pedidoEnMemoria(int idPedido) {
        for (Pedido ped : pedidos) {
            if (ped.getIdPedido() == idPedido) {
                return ped;
            }
        }
        return null;
    }

    private static boolean contiene(String texto, String buscado) {
        return texto != null && texto.toLowerCase(Locale.ROOT).contains(buscado);
    }

    private static boolean enRango(LocalDateTime fecha, LocalDate desde, LocalDate hasta) {
        if (fecha == null) {
            return false;
        }
        LocalDate dia = fecha.toLocalDate();
        return (desde == null || !dia.isBefore(desde)) && (hasta == null || !dia.isAfter(hasta));
    }

    // ---------------------------------------------------------------- inventario (kardex)

    /**
     * Registra un movimiento manual: Entrada (cantidad mayor que 0, suma al stock) o Ajuste (cantidad con
     * signo distinta de 0; el stock resultante no puede quedar negativo). Actualiza stock y estado del
     * producto. Solo Administrador y Almacén; motivo obligatorio (máx. 200).
     */
    public synchronized String ajustarStock(Usuario actor, String codigo, String tipo, int cantidad, String motivo) {
        if (actor == null || !actor.isPuedeAjustarStock()) {
            return "No tienes permiso para registrar movimientos de stock.";
        }
        String tipoMovimiento = null;
        for (String manual : MovimientoStock.TIPOS_MANUALES) {
            if (manual.equalsIgnoreCase(texto(tipo))) {
                tipoMovimiento = manual;
            }
        }
        if (tipoMovimiento == null) {
            return "Tipo de movimiento no válido: usa Entrada o Ajuste.";
        }
        if (MovimientoStock.ENTRADA.equals(tipoMovimiento) && cantidad <= 0) {
            return "Una entrada debe tener una cantidad mayor que cero.";
        }
        if (cantidad == 0) {
            return "El ajuste debe tener una cantidad distinta de cero.";
        }
        String razon = texto(motivo);
        if (razon.isEmpty()) {
            return "Indica el motivo del movimiento.";
        }
        if (razon.length() > MAX_MOTIVO) {
            return "El motivo admite como máximo " + MAX_MOTIVO + " caracteres.";
        }
        String c = normalizarCodigo(codigo);
        if (c.isEmpty()) {
            return "Indica el producto.";
        }
        final String tipoFinal = tipoMovimiento;
        if (ConexionBD.estaDisponible()) {
            return transaccion(cn -> {
                int stock;
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT stock FROM dbo.producto WITH (UPDLOCK, HOLDLOCK) WHERE codigo = ?")) {
                    ps.setString(1, c);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return "El producto " + c + " no existe.";
                        }
                        stock = rs.getInt("stock");
                    }
                }
                String error = errorResultadoStock(stock, cantidad);
                if (error != null) {
                    return error;
                }
                int nuevoStock = stock + cantidad;
                actualizarStockBD(cn, c, nuevoStock);
                insertarMovimientoBD(cn, c, tipoFinal, cantidad, nuevoStock, razon, actor.getUsername());
                return null;
            });
        }
        Producto producto = productoEnMemoria(c);
        if (producto == null) {
            return "El producto " + c + " no existe.";
        }
        String error = errorResultadoStock(producto.getStock(), cantidad);
        if (error != null) {
            return error;
        }
        producto.setStock(producto.getStock() + cantidad);
        producto.setEstado(producto.getStock() > 0 ? Producto.DISPONIBLE : Producto.AGOTADO);
        registrarMovimientoMemoria(producto, tipoFinal, cantidad, razon, actor.getUsername(), ahora());
        return null;
    }

    /**
     * Movimientos del kardex, del más reciente al más antiguo.
     *
     * @param codigo producto a consultar; null para todos
     * @param limite máximo de filas (mayor que 0)
     */
    public synchronized List<MovimientoStock> listarMovimientos(String codigo, int limite) {
        List<MovimientoStock> resultado = new ArrayList<>();
        if (limite < 1) {
            return resultado;
        }
        String c = normalizarCodigo(codigo);
        if (ConexionBD.estaDisponible()) {
            String sql = SQL_MOVIMIENTOS + (c.isEmpty() ? "" : "WHERE m.codigo_producto = ? ")
                    + "ORDER BY m.fecha DESC, m.id_movimiento DESC";
            try (Connection cn = ConexionBD.obtenerConexion(); PreparedStatement ps = cn.prepareStatement(sql)) {
                ps.setInt(1, limite);
                if (!c.isEmpty()) {
                    ps.setString(2, c);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        MovimientoStock m = new MovimientoStock();
                        m.setIdMovimiento(rs.getInt("id_movimiento"));
                        m.setCodigoProducto(rs.getString("codigo_producto"));
                        m.setNombreProducto(rs.getString("producto"));
                        m.setTipo(rs.getString("tipo"));
                        m.setCantidad(rs.getInt("cantidad"));
                        m.setStockResultante(rs.getInt("stock_resultante"));
                        m.setMotivo(rs.getString("motivo"));
                        String username = rs.getString("username");
                        m.setUsuario(username == null ? "sistema" : username);
                        m.setFecha(rs.getTimestamp("fecha").toLocalDateTime().format(FORMATO_FECHA));
                        resultado.add(m);
                    }
                }
                return resultado;
            } catch (SQLException ex) {
                throw new IllegalStateException("No se pudo consultar el kardex de inventario.", ex);
            }
        }
        List<MovimientoStock> copia = new ArrayList<>(movimientos);
        copia.sort(Comparator.comparingInt(MovimientoStock::getIdMovimiento).reversed());
        for (MovimientoStock origen : copia) {
            if (resultado.size() >= limite) {
                break;
            }
            if (c.isEmpty() || c.equals(origen.getCodigoProducto())) {
                MovimientoStock m = new MovimientoStock();
                m.setIdMovimiento(origen.getIdMovimiento());
                m.setCodigoProducto(origen.getCodigoProducto());
                m.setNombreProducto(origen.getNombreProducto());
                m.setTipo(origen.getTipo());
                m.setCantidad(origen.getCantidad());
                m.setStockResultante(origen.getStockResultante());
                m.setMotivo(origen.getMotivo());
                m.setUsuario(origen.getUsuario());
                m.setFecha(origen.getFecha());
                resultado.add(m);
            }
        }
        return resultado;
    }

    /** null si el stock resultante es válido; si no, el motivo. */
    private static String errorResultadoStock(int stockActual, int cantidad) {
        long resultante = (long) stockActual + cantidad;
        if (resultante < 0) {
            return "El ajuste dejaría el stock en negativo (stock actual: " + stockActual + ").";
        }
        if (resultante > Integer.MAX_VALUE) {
            return "La cantidad es demasiado grande.";
        }
        return null;
    }

    private Producto productoEnMemoria(String codigo) {
        for (Producto p : productos) {
            if (p.getCodigo().equals(codigo)) {
                return p;
            }
        }
        return null;
    }

    /** Añade un movimiento al kardex en memoria; el producto ya debe tener su stock final. */
    private void registrarMovimientoMemoria(Producto producto, String tipo, int cantidad, String motivo,
            String usuario, String fecha) {
        MovimientoStock m = new MovimientoStock();
        m.setIdMovimiento(movimientos.stream().mapToInt(MovimientoStock::getIdMovimiento).max().orElse(0) + 1);
        m.setCodigoProducto(producto.getCodigo());
        m.setNombreProducto(producto.getNombre());
        m.setTipo(tipo);
        m.setCantidad(cantidad);
        m.setStockResultante(producto.getStock());
        m.setMotivo(motivo);
        m.setUsuario(usuario == null || usuario.isBlank() ? "sistema" : usuario);
        m.setFecha(fecha);
        movimientos.add(m);
    }

    private static void actualizarStockBD(Connection cn, String codigo, int nuevoStock) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("UPDATE dbo.producto SET stock = ?, estado = ? WHERE codigo = ?")) {
            ps.setInt(1, nuevoStock);
            ps.setString(2, nuevoStock > 0 ? Producto.DISPONIBLE : Producto.AGOTADO);
            ps.setString(3, codigo);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Producto no actualizado");
            }
        }
    }

    private static void insertarMovimientoBD(Connection cn, String codigo, String tipo, int cantidad,
            int stockResultante, String motivo, String username) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(SQL_MOVIMIENTO)) {
            ps.setString(1, codigo);
            ps.setString(2, tipo);
            ps.setInt(3, cantidad);
            ps.setInt(4, stockResultante);
            ps.setString(5, recortar(motivo, MAX_MOTIVO));
            ps.setString(6, username);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- categorías

    /**
     * Categorías ordenadas por nombre, con la cantidad de productos de cada una.
     *
     * @throws IllegalStateException si SQL Server está activo pero la consulta falla (no se mezcla con la memoria)
     */
    public synchronized List<Categoria> listarCategorias(boolean soloActivas) {
        if (ConexionBD.estaDisponible()) {
            String sql = SQL_CATEGORIAS + (soloActivas ? "WHERE c.activo = 1 " : "") + "ORDER BY c.nombre";
            try (Connection cn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = cn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                List<Categoria> listaBD = new ArrayList<>();
                while (rs.next()) {
                    String descripcion = rs.getString("descripcion");
                    Categoria c = new Categoria(rs.getInt("id_categoria"), rs.getString("nombre"),
                            descripcion == null ? "" : descripcion, rs.getBoolean("activo"));
                    c.setCantidadProductos(rs.getInt("productos"));
                    listaBD.add(c);
                }
                return listaBD;
            } catch (SQLException ex) {
                ConexionBD.probarConexion();
                if (ConexionBD.estaDisponible()) {
                    throw new IllegalStateException("No se pudo consultar las categorías.", ex);
                }
            }
        }
        List<Categoria> resultado = new ArrayList<>();
        for (Categoria origen : categorias) {
            if (!soloActivas || origen.isActivo()) {
                Categoria copia = new Categoria(origen.getIdCategoria(), origen.getNombre(),
                        origen.getDescripcion(), origen.isActivo());
                copia.setCantidadProductos(productosDeCategoriaEnMemoria(origen.getNombre()));
                resultado.add(copia);
            }
        }
        resultado.sort(Comparator.comparing(Categoria::getNombre, ORDEN_TEXTO::compare));
        return resultado;
    }

    /**
     * Nombres de las categorías activas (para formularios, búsqueda y la tienda). Si la consulta falla devuelve
     * una lista vacía para que las páginas públicas no caigan; las pantallas de administración usan
     * {@link #listarCategorias(boolean)} y muestran el error.
     */
    public synchronized List<String> nombresCategorias() {
        List<String> nombres = new ArrayList<>();
        try {
            for (Categoria c : listarCategorias(true)) {
                nombres.add(c.getNombre());
            }
        } catch (IllegalStateException ex) {
            return new ArrayList<>();
        }
        return nombres;
    }

    /** Nombre obligatorio (2-50), único sin distinguir mayúsculas; descripción opcional (máx. 150). Solo Administrador. */
    public synchronized String crearCategoria(Usuario actor, String nombre, String descripcion) {
        if (actor == null || !actor.isPuedeAdministrarSistema()) {
            return MSG_SOLO_ADMIN_CATEGORIAS;
        }
        String n = texto(nombre);
        String d = texto(descripcion);
        String error = errorDatosCategoria(n, d);
        if (error != null) {
            return error;
        }
        if (ConexionBD.estaDisponible()) {
            return transaccion(cn -> {
                if (existeNombreCategoriaBD(cn, n, 0)) {
                    return mensajeCategoriaDuplicada(n);
                }
                try (PreparedStatement ps = cn.prepareStatement(
                        "INSERT INTO dbo.categoria (nombre, descripcion, activo) VALUES (?, ?, 1)")) {
                    ps.setString(1, n);
                    ps.setString(2, d.isEmpty() ? null : d);
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    if (esViolacionUnica(ex)) {
                        return mensajeCategoriaDuplicada(n);
                    }
                    throw ex;
                }
                return null;
            });
        }
        if (existeNombreCategoriaEnMemoria(n, 0)) {
            return mensajeCategoriaDuplicada(n);
        }
        int id = categorias.stream().mapToInt(Categoria::getIdCategoria).max().orElse(0) + 1;
        categorias.add(new Categoria(id, n, d, true));
        return null;
    }

    /** Renombra o cambia la descripción; los productos de la categoría conservan la relación. Solo Administrador. */
    public synchronized String editarCategoria(Usuario actor, int idCategoria, String nombre, String descripcion) {
        if (actor == null || !actor.isPuedeAdministrarSistema()) {
            return MSG_SOLO_ADMIN_CATEGORIAS;
        }
        String n = texto(nombre);
        String d = texto(descripcion);
        String error = errorDatosCategoria(n, d);
        if (error != null) {
            return error;
        }
        if (ConexionBD.estaDisponible()) {
            return transaccion(cn -> {
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT 1 FROM dbo.categoria WITH (UPDLOCK, HOLDLOCK) WHERE id_categoria = ?")) {
                    ps.setInt(1, idCategoria);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return MSG_CATEGORIA_NO_EXISTE;
                        }
                    }
                }
                if (existeNombreCategoriaBD(cn, n, idCategoria)) {
                    return mensajeCategoriaDuplicada(n);
                }
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE dbo.categoria SET nombre = ?, descripcion = ? WHERE id_categoria = ?")) {
                    ps.setString(1, n);
                    ps.setString(2, d.isEmpty() ? null : d);
                    ps.setInt(3, idCategoria);
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    if (esViolacionUnica(ex)) {
                        return mensajeCategoriaDuplicada(n);
                    }
                    throw ex;
                }
                return null;
            });
        }
        Categoria categoria = categoriaEnMemoria(idCategoria);
        if (categoria == null) {
            return MSG_CATEGORIA_NO_EXISTE;
        }
        if (existeNombreCategoriaEnMemoria(n, idCategoria)) {
            return mensajeCategoriaDuplicada(n);
        }
        String anterior = categoria.getNombre();
        for (Producto p : productos) {
            if (p.getCategoria() != null && p.getCategoria().equalsIgnoreCase(anterior)) {
                p.setCategoria(n);
            }
        }
        categoria.setNombre(n);
        categoria.setDescripcion(d);
        return null;
    }

    /** Activa o desactiva una categoría. Solo Administrador. */
    public synchronized String cambiarEstadoCategoria(Usuario actor, int idCategoria, boolean activo) {
        if (actor == null || !actor.isPuedeAdministrarSistema()) {
            return MSG_SOLO_ADMIN_CATEGORIAS;
        }
        if (ConexionBD.estaDisponible()) {
            return transaccion(cn -> {
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE dbo.categoria SET activo = ? WHERE id_categoria = ?")) {
                    ps.setBoolean(1, activo);
                    ps.setInt(2, idCategoria);
                    return ps.executeUpdate() > 0 ? null : MSG_CATEGORIA_NO_EXISTE;
                }
            });
        }
        Categoria categoria = categoriaEnMemoria(idCategoria);
        if (categoria == null) {
            return MSG_CATEGORIA_NO_EXISTE;
        }
        categoria.setActivo(activo);
        return null;
    }

    /** Elimina una categoría sin productos. Solo Administrador. */
    public synchronized String eliminarCategoria(Usuario actor, int idCategoria) {
        if (actor == null || !actor.isPuedeAdministrarSistema()) {
            return MSG_SOLO_ADMIN_CATEGORIAS;
        }
        if (ConexionBD.estaDisponible()) {
            return transaccion(cn -> {
                String nombre;
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT nombre FROM dbo.categoria WITH (UPDLOCK, HOLDLOCK) WHERE id_categoria = ?")) {
                    ps.setInt(1, idCategoria);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return MSG_CATEGORIA_NO_EXISTE;
                        }
                        nombre = rs.getString("nombre");
                    }
                }
                int cantidad;
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT COUNT(*) FROM dbo.producto WITH (HOLDLOCK) WHERE id_categoria = ?")) {
                    ps.setInt(1, idCategoria);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        cantidad = rs.getInt(1);
                    }
                }
                if (cantidad > 0) {
                    return mensajeCategoriaConProductos(nombre, cantidad);
                }
                try (PreparedStatement ps = cn.prepareStatement("DELETE FROM dbo.categoria WHERE id_categoria = ?")) {
                    ps.setInt(1, idCategoria);
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    if (ex.getErrorCode() == ERROR_SQL_FK) {
                        return "La categoría " + nombre + " tiene productos; desactívala en su lugar.";
                    }
                    throw ex;
                }
                return null;
            });
        }
        Categoria categoria = categoriaEnMemoria(idCategoria);
        if (categoria == null) {
            return MSG_CATEGORIA_NO_EXISTE;
        }
        int cantidad = productosDeCategoriaEnMemoria(categoria.getNombre());
        if (cantidad > 0) {
            return mensajeCategoriaConProductos(categoria.getNombre(), cantidad);
        }
        categorias.remove(categoria);
        return null;
    }

    private Categoria categoriaEnMemoria(int idCategoria) {
        for (Categoria c : categorias) {
            if (c.getIdCategoria() == idCategoria) {
                return c;
            }
        }
        return null;
    }

    private int productosDeCategoriaEnMemoria(String nombre) {
        int total = 0;
        for (Producto p : productos) {
            if (p.getCategoria() != null && p.getCategoria().equalsIgnoreCase(nombre)) {
                total++;
            }
        }
        return total;
    }

    private boolean existeNombreCategoriaEnMemoria(String nombre, int exceptoId) {
        for (Categoria c : categorias) {
            if (c.getIdCategoria() != exceptoId && c.getNombre().equalsIgnoreCase(nombre)) {
                return true;
            }
        }
        return false;
    }

    /** Busca el nombre con la fila bloqueada hasta cerrar la transacción; exceptoId 0 = ninguna excepción. */
    private static boolean existeNombreCategoriaBD(Connection cn, String nombre, int exceptoId) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT 1 FROM dbo.categoria WITH (UPDLOCK, HOLDLOCK) WHERE LOWER(nombre) = LOWER(?) AND id_categoria <> ?")) {
            ps.setString(1, nombre);
            ps.setInt(2, exceptoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String errorDatosCategoria(String nombre, String descripcion) {
        if (nombre.isEmpty()) {
            return "El nombre de la categoría es obligatorio.";
        }
        if (nombre.length() < 2 || nombre.length() > 50) {
            return "El nombre de la categoría debe tener entre 2 y 50 caracteres.";
        }
        if (descripcion.length() > 150) {
            return "La descripción admite como máximo 150 caracteres.";
        }
        return null;
    }

    private static String mensajeCategoriaDuplicada(String nombre) {
        return "Ya existe una categoría llamada " + nombre + ".";
    }

    private static String mensajeCategoriaConProductos(String nombre, int cantidad) {
        return "La categoría " + nombre + " tiene " + cantidad + (cantidad == 1 ? " producto" : " productos")
                + "; desactívala en su lugar.";
    }

    private static boolean esViolacionUnica(SQLException ex) {
        return ex.getErrorCode() == ERROR_SQL_UNICO || ex.getErrorCode() == ERROR_SQL_INDICE_UNICO;
    }

    /**
     * Ejecuta la operación en una transacción: se confirma si devuelve null y se revierte si devuelve un
     * mensaje o lanza {@link SQLException}. Nunca recurre a la memoria: si la base falla, no queda ningún
     * cambio y se informa del error (y se vuelve a comprobar la conexión para las siguientes peticiones).
     */
    private String transaccion(OperacionSql operacion) {
        try (Connection cn = ConexionBD.obtenerConexion()) {
            cn.setAutoCommit(false);
            try {
                String error = operacion.ejecutar(cn);
                if (error != null) {
                    cn.rollback();
                    return error;
                }
                cn.commit();
                return null;
            } catch (SQLException ex) {
                cn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            ConexionBD.probarConexion();
            return MSG_ERROR_BD;
        }
    }

    private static String ahora() {
        return LocalDateTime.now().format(FORMATO_FECHA);
    }

    private static String codigoPedido(int idPedido) {
        Pedido ref = new Pedido();
        ref.setIdPedido(idPedido);
        return ref.getCodigoPedido();
    }

    /** Recorta el texto a la longitud de la columna. */
    private static String recortar(String texto, int maximo) {
        return texto != null && texto.length() > maximo ? texto.substring(0, maximo) : texto;
    }

    // ---------------------------------------------------------------- validación

    /**
     * Valida los datos del formulario (texto tal como llega del request).
     *
     * @param form  valores del formulario: codigo, nombre, categoria, precio, stock, estado
     * @param nuevo true al registrar (verifica que el código no esté duplicado y valida el stock inicial);
     *              al editar el stock no se valida (no se modifica) y se admite conservar la categoría actual
     *              del producto aunque esté inactiva
     * @return mapa campo -> mensaje de error; vacío si todo es válido
     */
    public Map<String, String> validar(Map<String, String> form, boolean nuevo) {
        Map<String, String> errores = new LinkedHashMap<>();

        String codigo = normalizarCodigo(form.get("codigo"));
        if (codigo.isEmpty()) {
            errores.put("codigo", "El código es obligatorio");
        } else if (codigo.length() > 10) {
            errores.put("codigo", "El código admite como máximo 10 caracteres");
        } else if (nuevo && existeCodigo(codigo)) {
            errores.put("codigo", "El código " + codigo + " ya está registrado");
        }

        if (texto(form.get("nombre")).isEmpty()) {
            errores.put("nombre", "El nombre es obligatorio");
        } else if (texto(form.get("nombre")).length() > MAX_NOMBRE_PRODUCTO) {
            errores.put("nombre", "El nombre admite como máximo " + MAX_NOMBRE_PRODUCTO + " caracteres");
        }

        String categoria = texto(form.get("categoria"));
        if (categoria.isEmpty()) {
            errores.put("categoria", "Seleccione una categoría");
        } else if (!nombresCategorias().contains(categoria) && !esCategoriaActual(codigo, categoria, nuevo)) {
            errores.put("categoria", "Categoría no válida");
        }

        Double precio = leerDecimal(form.get("precio"));
        if (texto(form.get("precio")).isEmpty()) {
            errores.put("precio", "El precio es obligatorio");
        } else if (precio == null) {
            errores.put("precio", "Ingrese un precio válido, por ejemplo 80.00");
        } else if (precio <= 0) {
            errores.put("precio", "El precio debe ser mayor que cero");
        }

        if (nuevo) {
            Integer stock = leerEntero(form.get("stock"));
            if (texto(form.get("stock")).isEmpty()) {
                errores.put("stock", "El stock es obligatorio");
            } else if (stock == null) {
                errores.put("stock", "El stock debe ser un número entero");
            } else if (stock < 0) {
                errores.put("stock", "El stock debe ser mayor o igual a cero");
            }
        }

        return errores;
    }

    /** Al editar, la categoría que el producto ya tiene sigue siendo válida aunque la hayan desactivado. */
    private boolean esCategoriaActual(String codigo, String categoria, boolean nuevo) {
        if (nuevo) {
            return false;
        }
        Producto actual = buscarPorCodigo(codigo);
        return actual != null && categoria.equals(actual.getCategoria());
    }

    /** Construye el producto a partir de un formulario ya validado. */
    public Producto construir(Map<String, String> form) {
        Producto p = new Producto();
        p.setCodigo(normalizarCodigo(form.get("codigo")));
        p.setNombre(texto(form.get("nombre")));
        p.setCategoria(nombreCategoria(texto(form.get("categoria"))));
        p.setPrecio(leerDecimal(form.get("precio")));
        Integer stock = leerEntero(form.get("stock"));
        p.setStock(stock == null ? 0 : stock);
        // El estado debe ser coherente con el stock: sin stock siempre es "Agotado".
        p.setEstado(p.getStock() > 0 ? Producto.DISPONIBLE : Producto.AGOTADO);
        return p;
    }

    /** Nombre oficial de la categoría activa que coincide (sin distinguir mayúsculas); si no hay, el texto recibido. */
    private String nombreCategoria(String escrito) {
        for (String nombre : nombresCategorias()) {
            if (nombre.equalsIgnoreCase(escrito)) {
                return nombre;
            }
        }
        return escrito;
    }

    // ---------------------------------------------------------------- reportes

    public synchronized List<Producto> listarDisponibles() {
        List<Producto> resultado = new ArrayList<>();
        for (Producto p : listar()) {
            if (p.getStock() > 0) {
                resultado.add(p);
            }
        }
        return resultado;
    }

    public synchronized int contarDisponibles() {
        return listarDisponibles().size();
    }

    public synchronized int contarAgotados() {
        return contar() - contarDisponibles();
    }

    public synchronized int stockTotal() {
        int total = 0;
        for (Producto p : listar()) {
            total += p.getStock();
        }
        return total;
    }

    public synchronized double valorTotal() {
        double total = 0;
        for (Producto p : listar()) {
            total += p.getValorInventario();
        }
        return total;
    }

    public synchronized int contarCategorias() {
        return (int) listar().stream().map(Producto::getCategoria).distinct().count();
    }

    /** Últimos productos registrados, del más reciente al más antiguo. */
    public synchronized List<Producto> ultimos(int cantidad) {
        List<Producto> copia = new ArrayList<>(listar());
        Collections.reverse(copia);
        return copia.subList(0, Math.min(cantidad, copia.size()));
    }

    /** Valor del inventario por categoría, de mayor a menor. */
    public synchronized List<CategoriaResumen> valorPorCategoria() {
        Map<String, double[]> acumulado = new TreeMap<>();
        for (Producto p : listar()) {
            double[] datos = acumulado.computeIfAbsent(p.getCategoria(), k -> new double[2]);
            datos[0] += 1;
            datos[1] += p.getValorInventario();
        }
        double total = valorTotal();
        double maximo = acumulado.values().stream().mapToDouble(d -> d[1]).max().orElse(0);

        List<CategoriaResumen> resultado = new ArrayList<>();
        for (Map.Entry<String, double[]> e : acumulado.entrySet()) {
            double valor = e.getValue()[1];
            resultado.add(new CategoriaResumen(e.getKey(), (int) e.getValue()[0], valor,
                    total > 0 ? valor * 100 / total : 0,
                    maximo > 0 ? valor * 100 / maximo : 0));
        }
        resultado.sort((a, b) -> Double.compare(b.getValor(), a.getValor()));
        return resultado;
    }

    // ---------------------------------------------------------------- auxiliares

    public static String normalizarCodigo(String codigo) {
        return texto(codigo).toUpperCase(Locale.ROOT);
    }

    private static String texto(String s) {
        return s == null ? "" : s.trim();
    }

    private static Double leerDecimal(String s) {
        try {
            String t = texto(s);
            if (!t.contains(".")) {
                t = t.replace(',', '.');
            } else {
                t = t.replace(",", "");
            }
            double valor = Double.parseDouble(t);
            return Double.isFinite(valor) ? valor : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer leerEntero(String s) {
        try {
            return Integer.valueOf(texto(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
